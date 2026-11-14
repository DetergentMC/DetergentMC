package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * 1.21.7／1.21.8（プロトコル772）のチャンク・スロット・パーティクル・メタデータ変換のテスト。
 *
 * <p>771→772の変更（minecraft-data の表に準拠。実バニラ1.21.7／1.21.8の
 * Registry Data キャプチャで絵画・ジュークボックスの位置を確認する）：
 * heightmap は771・772とも直接の配列（NBT ではない）、パレット付きコンテナの
 * Data Array に長さ varint は無い、データコンポーネント種別は96件のまま・
 * 同じ番号、エンティティメタデータの種別番号は同一、パーティクルは同一、
 * 属性は同一、ブロック状態・エンティティ・バイオーム・エンチャント・
 * メニュー・ダメージ種別は同一。Window Click は HashedSlot のため変換できず
 * 落とす（ProxySession 側で落とす）。
 * 変わるのは番号の付け替えだけ：アイテム（music_disc_lava_chicken を1256に挿入）、
 * 音（music_disc.lava_chicken を939に挿入、772→771では block.stone.break（1441）で代替）、
 * 絵画（dennis を14に挿入、772→771ではメタデータの項目ごと落とし成分では kebab で代用）、
 * ジュークボックスの歌（lava_chicken を9に挿入、772→771では "11"（0）で代用）。
 */
class ChunkTranslator772Test {

    /** heightmap の直接配列（type 1=WORLD_SURFACE、4=MOTION_BLOCKING、各2要素）。 */
    private static void writeHeightmapArray(Writer writer) {
        writer.writeVarInt(2); // 2件
        writer.writeVarInt(1); // WORLD_SURFACE
        writer.writeVarInt(2);
        writer.writeLong(44L);
        writer.writeLong(55L);
        writer.writeVarInt(4); // MOTION_BLOCKING
        writer.writeVarInt(2);
        writer.writeLong(11L);
        writer.writeLong(22L);
    }

    /** heightmap の直接配列を読み、(type, longs) を返す（2件分）。 */
    private static long[][] readHeightmapArray(Reader reader) {
        assertEquals(2, reader.readVarInt());
        long[][] out = new long[2][];
        for (int i = 0; i < 2; i++) {
            int type = reader.readVarInt();
            int len = reader.readVarInt();
            long[] values = new long[len];
            for (int j = 0; j < len; j++) {
                values[j] = reader.readLong();
            }
            out[type == 1 ? 0 : 1] = values;
        }
        return out;
    }

    /** 間接パレットのセクション1個。771／772形式（Data Array に長さ varint が無い）。 */
    private static byte[] indirectSection771(int[] blockPalette, int[] biomePalette) {
        Writer writer = new Writer();
        writer.writeShort(1); // ブロック数
        writer.writeUnsignedByte(4); // BPE（間接）
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        // Data Array の長さ varint は無い。256 long をそのまま書く。
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        writer.writeUnsignedByte(2); // バイオーム BPE（間接）
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        // Data Array の長さ varint は無い。2 long（64エントリ×2bit）をそのまま書く。
        for (int i = 0; i < 2; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** セクションのブロックパレットだけ読み、ブロック状態IDの配列を返す。 */
    private static int[] readIndirectPalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** セクションのバイオームパレットを読む。771／772形式（Data Array に長さ varint が無い）。 */
    private static int[] readBiomePalette772(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        int blockBits = section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int perLong = 64 / blockBits;
        int blockDataLength = (4096 + perLong - 1) / perLong;
        for (int i = 0; i < blockDataLength; i++) {
            section.readLong();
        }
        int bits = section.readUnsignedByte(); // バイオーム BPE
        if (bits == 0) {
            return new int[]{section.readVarInt()};
        }
        int biomeSize = section.readVarInt();
        int[] palette = new int[biomeSize];
        for (int i = 0; i < biomeSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** エンティティメタデータのパケット（entityId＋エントリ＋0xFF）。 */
    private static byte[] metadataPacket(int entityId, Writer entries) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeBytes(entries.bytes());
        writer.writeUnsignedByte(0xFF);
        return writer.bytes();
    }

    /** World Particles ペイロード（771／772の形：longDistance＋alwaysShow）。 */
    private static byte[] worldParticlesPacket(boolean alwaysShow, int particleId, Writer particleData) {
        Writer writer = new Writer();
        writer.writeBoolean(false); // longDistance
        writer.writeBoolean(alwaysShow); // alwaysShow
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f); // offsetX
        writer.writeFloat(0.5f); // offsetY
        writer.writeFloat(0.5f); // offsetZ
        writer.writeFloat(1.0f); // speed
        writer.writeInt(4); // count
        writer.writeVarInt(particleId);
        writer.writeBytes(particleData.bytes());
        return writer.bytes();
    }

    /** World Particles ペイロードのヘッダを読み飛ばし、粒子IDを返す。 */
    private static int readWorldParticlesId(Reader reader) {
        reader.readBoolean(); // longDistance
        reader.readBoolean(); // alwaysShow
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readInt();
        return reader.readVarInt();
    }

    /** Spawn Entity ペイロード（entityId＋UUID＋種別＋座標＋向き＋objectData＋速度）。 */
    private static byte[] spawnEntityPacket(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(0); // pitch
        writer.writeByte(0); // yaw
        writer.writeByte(0); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(0); // velocityX
        writer.writeShort(0); // velocityY
        writer.writeShort(0); // velocityZ
        return writer.bytes();
    }

    /** Set Slot ペイロード（windowId＋stateId＋slot＋アイテムID、成分なし）。 */
    private static byte[] setSlotPacket(int itemId) {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        return writer.bytes();
    }

    /** スロットの先頭（個数＋アイテムID）を読み、追加コンポーネント数を返す。削除数も読み飛ばす。 */
    private static int[] readSlotHead(Reader reader) {
        int count = reader.readVarInt();
        int itemId = reader.readVarInt();
        int added = reader.readVarInt();
        int removed = reader.readVarInt();
        return new int[]{count, itemId, added, removed};
    }

    /** チャンク Data ペイロード（771／772共通の形）。セクションは1個。 */
    private static byte[] chunkDataPayload(int chunkX, int chunkZ, int[] blockPalette) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        writeHeightmapArray(payload);
        payload.writeByteArray(indirectSection771(blockPalette, new int[]{38, 39}));
        payload.writeVarInt(0); // ブロックエンティティは0件
        return payload.bytes();
    }

    // ---- チャンク（ブロック状態・バイオームは771・772とも同一） ----

    @Test
    void chunkData771To772BlockStatesIdentity() {
        // 771→772：ブロック状態は変わらない（trial_spawner 27730・vault 27742 のまま）。
        byte[] translated = ChunkTranslator.translateChunkData771To772(
                chunkDataPayload(3, -2, new int[]{0, 1, 27730, 27742}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]); // WORLD_SURFACE
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]); // MOTION_BLOCKING
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27730, 27742}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette772(sections));
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
    }

    @Test
    void chunkData772To771BlockStatesIdentity() {
        byte[] translated = ChunkTranslator.translateChunkData772To771(
                chunkDataPayload(0, 0, new int[]{0, 1, 27730, 27742}));
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]);
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27730, 27742}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette772(sections));
        assertEquals(0, reader.readVarInt());
    }

    // ---- ブロック変更・ブロックエンティティ ----

    @Test
    void blockChange771To772Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27730); // trial_spawner（771・772とも）
        byte[] out = ChunkTranslator.translateBlockChange771To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27730, reader.readVarInt());
    }

    @Test
    void blockChange772To771Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27730); // trial_spawner（772）
        byte[] out = ChunkTranslator.translateBlockChange772To771(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27730, reader.readVarInt());
    }

    @Test
    void blockEntityData771To772Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(45); // test_block
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData771To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(45, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    @Test
    void blockEntityData772To771Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(45); // test_block
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData772To771(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(45, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    // ---- スロット（データコンポーネント） ----

    @Test
    void slot771To772ItemShiftsAtLavaChicken() {
        // music_disc_lava_chicken（772=1256）の挿入で1256以降が+1ずれる。
        byte[] out = ChunkTranslator.translateSetSlot771To772(setSlotPacket(1255));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1255, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot771To772(setSlotPacket(1256));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1257, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot771To772(setSlotPacket(1414));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1415, readSlotHead(reader3)[1]);
    }

    @Test
    void slot772To771LavaChickenDiscFallsBackToStone() {
        // music_disc_lava_chicken（772=1256）は771に無いため石（1）に落とす。
        byte[] out = ChunkTranslator.translateSetSlot772To771(setSlotPacket(1256));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot772To771(setSlotPacket(1257));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1256, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot772To771(setSlotPacket(1415));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1414, readSlotHead(reader3)[1]);
    }

    @Test
    void slot771To772EnchantmentIdentity() {
        // enchantments は771・772とも種別 10、showTooltip は両方とも無い。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(10); // enchantments
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(22); // mending
        writer.writeVarInt(3); // level
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot771To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(10, reader.readVarInt()); // enchantments
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt()); // mending
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot771To772JukeboxPlayableShiftsAtLavaChicken() {
        // jukebox_playable（種別 55）。参照の曲ID 9（mall）が772では 10 になる。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(10); // holder：曲ID 9（mall）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot771To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(11, reader.readVarInt()); // holder：曲ID 10（mall）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot772To771JukeboxPlayableLavaChickenBecomesEleven() {
        // lava_chicken（772の 9）は771に無いため "11"（0）で代用する。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(10); // holder：曲ID 9（lava_chicken）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot772To771(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(1, reader.readVarInt()); // holder：曲ID 0（"11"）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot771To772BreakSoundShiftsAtLavaChicken() {
        // break_sound（種別 71）の音 Holder：参照 939（771の music_disc.ward 相当）
        // → 940（772）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(940); // holder：音ID 939 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot771To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(941, reader.readVarInt()); // holder：音ID 940 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot772To771BreakSoundLavaChickenBecomesStoneBreak() {
        // 逆向き：772の music_disc.lava_chicken（939）は771では
        // block.stone.break（1441）で代替する。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(940); // holder：音ID 939 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot772To771(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(1442, reader.readVarInt()); // holder：音ID 1441 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot771To772TooltipDisplayIdentity() {
        // tooltip_display（種別 15）は771・772とも {hideTooltip, hiddenComponents}。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(15); // tooltip_display
        writer.writeBoolean(true); // hideTooltip
        writer.writeVarInt(0); // hiddenComponents 0件
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot771To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(15, reader.readVarInt()); // tooltip_display
        assertTrue(reader.readBoolean()); // hideTooltip
        assertEquals(0, reader.readVarInt()); // hiddenComponents 0件
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    // ---- Set Creative Slot（UntrustedSlot） / Window Click ----

    @Test
    void setCreativeSlot771To772ItemShifts() {
        // UntrustedSlot のままアイテムIDだけ付け替える（1256→1257）。
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(1256); // 771のアイテム
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot771To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1257, reader.readVarInt()); // 772のアイテム
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void setCreativeSlot772To771LavaChickenBecomesStone() {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(1256); // 772の music_disc_lava_chicken
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot772To771(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // 石
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void windowClick771To772Throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick771To772(new byte[0]));
    }

    @Test
    void windowClick772To771Throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick772To771(new byte[0]));
    }

    // ---- パーティクル ----

    @Test
    void worldParticles771To772PaleOakLeavesIdentity() {
        // pale_oak_leaves（34）は771・772とも同じ番号。データ（i32）はそのまま。
        Writer data = new Writer();
        data.writeInt(12345);
        byte[] out = ChunkTranslator.translateWorldParticles771To772(
                worldParticlesPacket(false, 34, data));
        Reader reader = new Reader(out);
        assertEquals(34, readWorldParticlesId(reader));
        assertEquals(12345, reader.readInt());
    }

    @Test
    void worldParticles772To771TrailIdentity() {
        // trail 48。データ（3×f64＋i32＋varint）はそのまま。
        Writer data = new Writer();
        data.writeDouble(1.0);
        data.writeDouble(2.0);
        data.writeDouble(3.0);
        data.writeInt(0x11223344);
        data.writeVarInt(7);
        byte[] out = ChunkTranslator.translateWorldParticles772To771(
                worldParticlesPacket(false, 48, data));
        Reader reader = new Reader(out);
        assertEquals(48, readWorldParticlesId(reader));
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0x11223344, reader.readInt());
        assertEquals(7, reader.readVarInt());
    }

    @Test
    void worldParticles771To772BlockCrumbleIdentity() {
        // block_crumble 112。データはブロック状態（trial_spawner 27730 のまま）。
        Writer data = new Writer();
        data.writeVarInt(27730); // trial_spawner（771・772とも）
        byte[] out = ChunkTranslator.translateWorldParticles771To772(
                worldParticlesPacket(false, 112, data));
        Reader reader = new Reader(out);
        assertEquals(112, readWorldParticlesId(reader));
        assertEquals(27730, reader.readVarInt());
    }

    // ---- エンティティメタデータ ----

    @Test
    void entityMetadata771To772PaintingShifts() {
        // painting_variant（種別 30、holder）：771の holder 15（絵画ID 14）
        // → 772では holder 16（絵画ID 15）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（絵画ID 14 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata771To772(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(16, reader.readVarInt()); // holder（絵画ID 15 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata772To771DennisDropped() {
        // dennis（772の絵画ID 14、holder 15）は771に無いため項目ごと落とす。
        // wolf_variant の項目だけが残る。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（dennis +1）
        byte[] out = ChunkTranslator.translateEntityMetadata772To771(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata772To771PaintingShiftsBack() {
        // 772の holder 16（絵画ID 15）→ 771では holder 15（絵画ID 14）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(16); // holder（絵画ID 15 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata772To771(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(15, reader.readVarInt()); // holder（絵画ID 14 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- Spawn Entity ----

    @Test
    void spawnEntity771To772HappyGhastIdentity() {
        // ハッピーガスト（771・772とも 56）はそのまま。
        byte[] out = ChunkTranslator.translateSpawnEntity771To772(spawnEntityPacket(56, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(56, reader.readVarInt()); // happy_ghast（772）
    }

    @Test
    void spawnEntity772To771PigIdentity() {
        // ブタ（771・772とも 95）はそのまま。
        byte[] out = ChunkTranslator.translateSpawnEntity772To771(spawnEntityPacket(95, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(95, reader.readVarInt()); // pig（771）
    }

    // ---- Explosion / Update Attributes / Damage Event / Set Cooldown ----

    @Test
    void explosion771To772SoundShifts() {
        // 音は参照 Holder：939（771）→940（772）。粒子（entity_effect 20）はそのまま。
        Writer payload = new Writer();
        payload.writeDouble(1.0); // x
        payload.writeDouble(2.0); // y
        payload.writeDouble(3.0); // z
        payload.writeBoolean(true); // hasKnockback
        payload.writeDouble(0.1);
        payload.writeDouble(0.2);
        payload.writeDouble(0.3);
        payload.writeVarInt(20); // entity_effect（粒子、データは色 i32）
        payload.writeInt(0xFF0000);
        payload.writeVarInt(940); // holder：音ID 939 +1
        byte[] out = ChunkTranslator.translateExplosion771To772(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertTrue(reader.readBoolean());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0.2, reader.readDouble());
        assertEquals(0.3, reader.readDouble());
        assertEquals(20, reader.readVarInt()); // 粒子
        assertEquals(0xFF0000, reader.readInt()); // 粒子の色
        assertEquals(941, reader.readVarInt()); // holder：音ID 940 +1
        assertEquals(0, reader.remaining());
    }

    @Test
    void explosion772To771LavaChickenSoundBecomesStoneBreak() {
        Writer payload = new Writer();
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeBoolean(false); // hasKnockback 無し
        payload.writeVarInt(20); // entity_effect
        payload.writeInt(0x00FF00);
        payload.writeVarInt(940); // holder：音ID 939 +1
        byte[] out = ChunkTranslator.translateExplosion772To771(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(false, reader.readBoolean());
        assertEquals(20, reader.readVarInt());
        assertEquals(0x00FF00, reader.readInt()); // 粒子の色
        assertEquals(1442, reader.readVarInt()); // holder：音ID 1441 +1（block.stone.break）
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateAttributes771To772Identity() {
        // 属性は771・772とも同じ並び（movement_speed 22、camera_distance 8）。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(2); // count 2
        payload.writeVarInt(8); // camera_distance
        payload.writeDouble(50.0);
        payload.writeVarInt(0); // modifiers 0
        payload.writeVarInt(22); // generic.movement_speed
        payload.writeDouble(0.1);
        payload.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes771To772(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(8, reader.readVarInt());
        assertEquals(50.0, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(22, reader.readVarInt());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateAttributes772To771Identity() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(1); // count 1
        payload.writeVarInt(22); // generic.movement_speed
        payload.writeDouble(0.1);
        payload.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes772To771(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void damageEvent771To772Identity() {
        // ダメージ種別は771・772とも49種・同じ並び。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(0); // sourceType
        payload.writeVarInt(0); // sourceCauseId
        payload.writeVarInt(0); // sourceDirectId
        payload.writeBoolean(true); // hasPosition
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        byte[] out = ChunkTranslator.translateDamageEvent771To772(payload.bytes());
        assertArrayEquals(payload.bytes(), out);
    }

    @Test
    void damageEvent772To771OutOfRangeDropped() {
        // 771・772とも範囲外のダメージ種別（49）は落とす（null）。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(49); // 範囲外
        payload.writeVarInt(0);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        assertNull(ChunkTranslator.translateDamageEvent772To771(payload.bytes()));
    }

    @Test
    void setCooldown771To772Identity() {
        // Set Cooldown は形式が同一（group 文字列＋ticks）。
        Writer payload = new Writer();
        payload.writeString("test_group");
        payload.writeVarInt(40);
        byte[] out = ChunkTranslator.translateSetCooldown771To772(payload.bytes());
        assertArrayEquals(payload.bytes(), out);
        assertArrayEquals(payload.bytes(),
                ChunkTranslator.translateSetCooldown772To771(payload.bytes()));
    }
}
