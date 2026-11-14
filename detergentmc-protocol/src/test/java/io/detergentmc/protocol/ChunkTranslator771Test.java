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
 * 1.21.6（プロトコル771）のチャンク・スロット・パーティクル・メタデータ変換のテスト。
 *
 * <p>770→771の変更（minecraft-data の表・実バニラ1.21.6のプローブに準拠）：
 * heightmap は770・771とも直接の配列（NBT ではない）、パレット付きコンテナの
 * Data Array に長さ varint は無い、データコンポーネント種別は96件のまま・
 * 同じ番号、エンティティメタデータの種別番号は同一、パーティクルは同一、
 * Window Click は HashedSlot のため変換できず落とす（ProxySession 側で落とす）。
 * 変わるのは番号の付け替えだけ：ブロック状態（13826番に乾燥ガスト32件が挿入）、
 * アイテム（+19）、エンティティ（ハッピーガスト 56 の挿入）、音
 * （puffer_fish.ambient／blow_out の衝突あり）、属性（camera_distance の挿入と
 * waypoint 系2件の追加）、ジュークボックスの歌（tears の挿入）。
 */
class ChunkTranslator771Test {

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

    /** 間接パレットのセクション1個。770／771形式（Data Array に長さ varint が無い）。 */
    private static byte[] indirectSection770(int[] blockPalette, int[] biomePalette) {
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

    /** セクションのバイオームパレットを読む。770／771形式（Data Array に長さ varint が無い）。 */
    private static int[] readBiomePalette771(byte[] sections) {
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

    /** World Particles ペイロード（770／771の形：longDistance＋alwaysShow）。 */
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

    /** チャンク Data ペイロード（770／771共通の形）。セクションは1個。 */
    private static byte[] chunkDataPayload(int chunkX, int chunkZ, int[] blockPalette) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        writeHeightmapArray(payload);
        payload.writeByteArray(indirectSection770(blockPalette, new int[]{38, 39}));
        payload.writeVarInt(0); // ブロックエンティティは0件
        return payload.bytes();
    }

    // ---- チャンク（heightmap は直接配列のまま） ----

    @Test
    void chunkData770To771HeightmapStaysArray() {
        // 770→771：heightmap は直接配列のまま（NBT には戻らない）。
        // ブロックパレットだけ付け替える（trial_spawner 27698→27730、vault 27710→27742）。
        byte[] translated = ChunkTranslator.translateChunkData770To771(
                chunkDataPayload(3, -2, new int[]{0, 1, 27698, 27710}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]); // WORLD_SURFACE
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]); // MOTION_BLOCKING
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27730, 27742}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette771(sections));
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
    }

    @Test
    void chunkData771To770HeightmapStaysArray() {
        byte[] translated = ChunkTranslator.translateChunkData771To770(
                chunkDataPayload(0, 0, new int[]{0, 1, 27730, 27742}));
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]);
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27698, 27710}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette771(sections));
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void chunkData771To770DriedGhastStateBecomesStone() {
        // 乾燥ガストの新規ブロック状態（771の 13826）は770では石（1）になる。
        byte[] translated = ChunkTranslator.translateChunkData771To770(
                chunkDataPayload(0, 0, new int[]{0, 13826}));
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        readHeightmapArray(reader);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1}, readIndirectPalette(sections));
    }

    // ---- ブロック変更・ブロックエンティティ ----

    @Test
    void blockChange770To771RemapsState() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27698); // trial_spawner（770）
        byte[] out = ChunkTranslator.translateBlockChange770To771(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27730, reader.readVarInt());
    }

    @Test
    void blockChange771To770RemapsState() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27730); // trial_spawner（771）
        byte[] out = ChunkTranslator.translateBlockChange771To770(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27698, reader.readVarInt());
    }

    @Test
    void blockEntityData770To771Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(43); // trial_spawner
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData770To771(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(43, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    @Test
    void blockEntityData771To770DriedGhastKept() {
        // ブロックエンティティ種別は770・771とも47件・同じ並びのため素通し。
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(45); // test_block
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData771To770(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(45, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    // ---- スロット（データコンポーネント） ----

    @Test
    void slot770To771ItemRemap() {
        byte[] out = ChunkTranslator.translateSetSlot770To771(setSlotPacket(619));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(620, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot770To771(setSlotPacket(801));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(818, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot770To771(setSlotPacket(1395));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1414, readSlotHead(reader3)[1]);
    }

    @Test
    void slot771To770NewItemsFallBackToStone() {
        // 771の新アイテム（619／802／1111／1268）は770に無いため石（1）に落とす。
        for (int item771 : new int[]{619, 802, 1111, 1268}) {
            byte[] out = ChunkTranslator.translateSetSlot771To770(setSlotPacket(item771));
            Reader reader = new Reader(out);
            reader.readVarInt();
            reader.readVarInt();
            reader.readShort();
            assertEquals(1, readSlotHead(reader)[1], "item " + item771);
        }
        byte[] out = ChunkTranslator.translateSetSlot771To770(setSlotPacket(1414));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1395, readSlotHead(reader)[1]);
    }

    @Test
    void slot770To771EnchantmentIdentity() {
        // enchantments は770・771とも種別 10、showTooltip は両方とも無い。
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
        byte[] out = ChunkTranslator.translateSetSlot770To771(writer.bytes());
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
    void slot770To771JukeboxPlayableWaitShifts() {
        // jukebox_playable（種別 55）。参照の曲ID 17（wait）が771では 18 になる。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(18); // holder：曲ID 17（wait）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot770To771(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(19, reader.readVarInt()); // holder：曲ID 18（wait）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot771To770JukeboxPlayableTearsBecomesEleven() {
        // tears（771の 17）は770に無いため "11"（0）で代用する。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(18); // holder：曲ID 17（tears）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot771To770(writer.bytes());
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
    void slot770To771BreakSoundRemapsSound() {
        // break_sound（種別 71）の音 Holder：参照 1180（770の blow_out）→ 1204（771）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(1181); // holder：音ID 1180 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot770To771(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(1205, reader.readVarInt()); // holder：音ID 1204 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot771To770BreakSoundBlowOutBecomesAmbient() {
        // 逆向き：771の blow_out（1204）は770では ambient（1179）になる（先行勝ち）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(1205); // holder：音ID 1204 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot771To770(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(1180, reader.readVarInt()); // holder：音ID 1179 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot770To771TooltipDisplayIdentity() {
        // tooltip_display（種別 15）は770・771とも {hideTooltip, hiddenComponents}。
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
        byte[] out = ChunkTranslator.translateSetSlot770To771(writer.bytes());
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
    void setCreativeSlot770To771ItemRemap() {
        // UntrustedSlot のままアイテムIDだけ付け替える（619→620）。
        Writer component = new Writer();
        component.writeVarInt(1); // count 1
        component.writeVarInt(22); // mending
        component.writeVarInt(3); // level
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(619); // 770のアイテム
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(0); // removed 0
        writer.writeVarInt(10); // enchantments
        writer.writeByteArray(component.bytes());
        byte[] out = ChunkTranslator.translateSetCreativeSlot770To771(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(620, reader.readVarInt()); // 771のアイテム
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(0, reader.readVarInt()); // removed 0
        assertEquals(10, reader.readVarInt()); // enchantments
        byte[] componentBytes = reader.readByteArray();
        Reader componentReader = new Reader(componentBytes);
        assertEquals(1, componentReader.readVarInt());
        assertEquals(22, componentReader.readVarInt()); // mending
        assertEquals(3, componentReader.readVarInt());
    }

    @Test
    void setCreativeSlot771To770NewItemBecomesStone() {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(619); // 771の新アイテム
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot771To770(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // 石
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void windowClick770To771Throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick770To771(new byte[0]));
    }

    @Test
    void windowClick771To770Throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick771To770(new byte[0]));
    }

    // ---- パーティクル ----

    @Test
    void worldParticles770To771TintedLeavesIdentity() {
        // tinted_leaves（35）は770・771とも同じ番号。データ（i32）はそのまま。
        Writer data = new Writer();
        data.writeInt(12345);
        byte[] out = ChunkTranslator.translateWorldParticles770To771(
                worldParticlesPacket(false, 35, data));
        Reader reader = new Reader(out);
        assertEquals(35, readWorldParticlesId(reader));
        assertEquals(12345, reader.readInt());
    }

    @Test
    void worldParticles771To770FireflyIdentity() {
        // firefly（113）は770・771とも同じ番号。データ無し。
        byte[] out = ChunkTranslator.translateWorldParticles771To770(
                worldParticlesPacket(false, 113, new Writer()));
        Reader reader = new Reader(out);
        assertEquals(113, readWorldParticlesId(reader));
    }

    @Test
    void worldParticles770To771BlockCrumble() {
        // block_crumble 112。データはブロック状態（trial_spawner 27698→27730）。
        Writer data = new Writer();
        data.writeVarInt(27698); // trial_spawner（770）
        byte[] out = ChunkTranslator.translateWorldParticles770To771(
                worldParticlesPacket(false, 112, data));
        Reader reader = new Reader(out);
        assertEquals(112, readWorldParticlesId(reader));
        assertEquals(27730, reader.readVarInt());
    }

    @Test
    void worldParticles771To770TrailIdentity() {
        // trail 48。データ（3×f64＋i32＋varint）はそのまま。
        Writer data = new Writer();
        data.writeDouble(1.0);
        data.writeDouble(2.0);
        data.writeDouble(3.0);
        data.writeInt(0x11223344);
        data.writeVarInt(7);
        byte[] out = ChunkTranslator.translateWorldParticles771To770(
                worldParticlesPacket(false, 48, data));
        Reader reader = new Reader(out);
        assertEquals(48, readWorldParticlesId(reader));
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0x11223344, reader.readInt());
        assertEquals(7, reader.readVarInt());
    }

    // ---- エンティティメタデータ ----

    @Test
    void entityMetadata770To771Identity() {
        // wolf_variant（種別 24、varint）・cow_variant（種別 23、varint）・
        // painting_variant（種別 30、holder）は770・771とも同じ番号。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(23); // cow_variant
        entries.writeVarInt(2); // 参照 id
        entries.writeUnsignedByte(2); // index 2
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(7); // holder
        byte[] out = ChunkTranslator.translateEntityMetadata770To771(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(23, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata771To770Identity() {
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        byte[] out = ChunkTranslator.translateEntityMetadata771To770(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- Spawn Entity ----

    @Test
    void spawnEntity770To771PigShifts() {
        // pig（770: 94）→771では 95。
        byte[] out = ChunkTranslator.translateSpawnEntity770To771(spawnEntityPacket(94, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(95, reader.readVarInt()); // pig（771）
    }

    @Test
    void spawnEntity770To771GhastIdentity() {
        // ghast（770: 55）は771でも 55。
        byte[] out = ChunkTranslator.translateSpawnEntity770To771(spawnEntityPacket(55, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(55, reader.readVarInt()); // ghast（771）
    }

    @Test
    void spawnEntity771To770HappyGhastBecomesGhast() {
        // ハッピーガスト（771: 56）は770ではガスト（55）に代替する
        // （RegistryRemapper の逆向きはブタ（94）だが、Spawn Entity の
        // 翻訳子はガストを選ぶ。メタデータの個体差は残る）。
        byte[] out = ChunkTranslator.translateSpawnEntity771To770(spawnEntityPacket(56, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(55, reader.readVarInt()); // ghast（770）
    }

    @Test
    void spawnEntity771To770PigShiftsBack() {
        byte[] out = ChunkTranslator.translateSpawnEntity771To770(spawnEntityPacket(95, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(94, reader.readVarInt()); // pig（770）
    }

    // ---- Explosion / Update Attributes / Damage Event / Set Cooldown ----

    @Test
    void explosion770To771SoundRemap() {
        // knockback は770・771とも f64×3。粒子（entity_effect 20）はそのまま。
        // 音は参照 Holder：1180（770の blow_out）→1204（771）。
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
        payload.writeVarInt(1181); // holder：音ID 1180 +1
        byte[] out = ChunkTranslator.translateExplosion770To771(payload.bytes());
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
        assertEquals(1205, reader.readVarInt()); // holder：音ID 1204 +1
        assertEquals(0, reader.remaining());
    }

    @Test
    void explosion771To770SoundRemap() {
        Writer payload = new Writer();
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeBoolean(false); // hasKnockback 無し
        payload.writeVarInt(20); // entity_effect
        payload.writeInt(0x00FF00);
        payload.writeVarInt(1205); // holder：音ID 1204 +1
        byte[] out = ChunkTranslator.translateExplosion771To770(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(false, reader.readBoolean());
        assertEquals(20, reader.readVarInt());
        assertEquals(0x00FF00, reader.readInt()); // 粒子の色
        assertEquals(1180, reader.readVarInt()); // holder：音ID 1179 +1（ambient）
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateAttributes770To771CameraDistanceShifts() {
        // 属性 8 が771では 9（camera_distance の挿入）、31 は 32。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(2); // count 2
        payload.writeVarInt(8);
        payload.writeDouble(10.0);
        payload.writeVarInt(0); // modifiers 0
        payload.writeVarInt(31);
        payload.writeDouble(0.5);
        payload.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes770To771(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(9, reader.readVarInt());
        assertEquals(10.0, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(32, reader.readVarInt());
        assertEquals(0.5, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateAttributes771To770WaypointDropped() {
        // waypoint_transmit_range（33）と waypoint_receive_range（34）は
        // 770に無いためエントリごと落とす。camera_distance（8）も落とす。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(3); // count 3
        payload.writeVarInt(33);
        payload.writeDouble(100.0);
        payload.writeVarInt(0);
        payload.writeVarInt(8);
        payload.writeDouble(50.0);
        payload.writeVarInt(0);
        payload.writeVarInt(0);
        payload.writeDouble(20.0);
        payload.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes771To770(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // 残るのは 1件だけ
        assertEquals(0, reader.readVarInt());
        assertEquals(20.0, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void damageEvent770To771Identity() {
        // ダメージ種別は770・771とも49種・同じ並び。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(0); // sourceType
        payload.writeVarInt(0); // sourceCauseId
        payload.writeVarInt(0); // sourceDirectId
        payload.writeBoolean(true); // hasPosition
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        byte[] out = ChunkTranslator.translateDamageEvent770To771(payload.bytes());
        assertArrayEquals(payload.bytes(), out);
    }

    @Test
    void damageEvent771To770OutOfRangeDropped() {
        // 771にしか無いダメージ種別（49）は落とす（null）。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(49); // 範囲外
        payload.writeVarInt(0);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        assertNull(ChunkTranslator.translateDamageEvent771To770(payload.bytes()));
    }

    @Test
    void setCooldown770To771Identity() {
        // Set Cooldown は形式が同一（group 文字列＋ticks）。
        Writer payload = new Writer();
        payload.writeString("test_group");
        payload.writeVarInt(40);
        byte[] out = ChunkTranslator.translateSetCooldown770To771(payload.bytes());
        assertArrayEquals(payload.bytes(), out);
        assertArrayEquals(payload.bytes(),
                ChunkTranslator.translateSetCooldown771To770(payload.bytes()));
    }
}
