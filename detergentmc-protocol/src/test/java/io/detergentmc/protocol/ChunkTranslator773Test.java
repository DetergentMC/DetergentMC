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
 * 1.21.9／1.21.10（プロトコル773）のチャンク・スロット・パーティクル・メタデータ変換のテスト。
 *
 * <p>772→773の変更（minecraft-data・ViaVersion mapping-1.21.9 の表に準拠。
 * 実バニラ1.21.9／1.21.10の Registry Data キャプチャで絵画・ジュークボックスの
 * 位置を確認する）：
 * heightmap は772・773とも直接の配列（NBT ではない）、パレット付きコンテナの
 * Data Array に長さ varint は無い、データコンポーネント種別は96件のまま・
 * 同じ番号、エンティティメタデータの種別番号は同一、バイオームは65件・同じ並び、
 * エンチャントは42件・同じ並び、属性は35件・同じ並び、メニューは25件・同じ並び、
 * ダメージ種別は49件・同じ並び、ジュークボックスの歌は21件・同じ並び、
 * 絵画は51件・同じ並び。Window Click は HashedSlot のため変換できず
 * 落とす（ProxySession 側で落とす）。
 * 変わるのは番号の付け替えだけ：ブロック状態（27,946→29,671。棚・銅の松明・
 * 銅の棒・銅の鎖・銅のランタン・銅のチェスト・銅ゴーレム像・避雷針の露出等）、
 * アイテム（1,416→1,488。棚12件を305番に挿入、chain→iron_chain の改名で
 * 378→399へ移動。772→773では bookshelf 305→317）、エンティティ
 * （151→153。copper_golem を27に、mannequin を82に挿入。pig 95→97、
 * happy_ghast 56→57）、音（1,728→1,771。銅のチェストの音を356番に挿入等。
 * 773にしかない音は block.stone.break（772=1442）で代替）、パーティクル
 * （114→115。copper_fire_flame を5番に挿入）、ブロックエンティティ
 * （47→49。shelf を40番に挿入、copper_golem_statue を48番に追加。
 * 773にしかない種別はパケットごと落とす）、絵画は同一のため
 * メタデータも素通しする。
 */
class ChunkTranslator773Test {

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

    /** 間接パレットのセクション1個。772／773形式（Data Array に長さ varint が無い）。 */
    private static byte[] indirectSection772(int[] blockPalette, int[] biomePalette) {
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

    /** セクションのバイオームパレットを読む。772／773形式（Data Array に長さ varint が無い）。 */
    private static int[] readBiomePalette773(byte[] sections) {
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

    /** World Particles ペイロード（772／773の形：longDistance＋alwaysShow）。 */
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

    /** チャンク Data ペイロード（772／773共通の形）。セクションは1個。 */
    private static byte[] chunkDataPayload(int chunkX, int chunkZ, int[] blockPalette) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        writeHeightmapArray(payload);
        payload.writeByteArray(indirectSection772(blockPalette, new int[]{38, 39}));
        payload.writeVarInt(0); // ブロックエンティティは0件
        return payload.bytes();
    }

    // ---- チャンク（ブロック状態・バイオームは772・773とも同一） ----

    @Test
    void chunkData772To773BlockStatesShift() {
        // 772→773：ブロック状態は付ける（trial_spawner 27730→29455・vault 27742→29467）。
        // バイオーム（38・39）は772・773とも同一。
        byte[] translated = ChunkTranslator.translateChunkData772To773(
                chunkDataPayload(3, -2, new int[]{0, 1, 27730, 27742}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]); // WORLD_SURFACE
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]); // MOTION_BLOCKING
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 29455, 29467}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette773(sections));
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
    }

    @Test
    void chunkData773To772BlockStatesShift() {
        // 逆向き：trial_spawner 29455→27730・vault 29467→27742。
        byte[] translated = ChunkTranslator.translateChunkData773To772(
                chunkDataPayload(0, 0, new int[]{0, 1, 29455, 29467}));
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]);
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27730, 27742}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette773(sections));
        assertEquals(0, reader.readVarInt());
    }

    // ---- ブロック変更・ブロックエンティティ ----

    @Test
    void blockChange772To773TrialSpawnerShifts() {
        // trial_spawner：772の 27730 → 773の 29455。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27730); // trial_spawner（772）
        byte[] out = ChunkTranslator.translateBlockChange772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(29455, reader.readVarInt());
    }

    @Test
    void blockChange773To772TrialSpawnerShiftsBack() {
        // 逆向き：trial_spawner 29455 → 27730。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(29455); // trial_spawner（773）
        byte[] out = ChunkTranslator.translateBlockChange773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27730, reader.readVarInt());
    }

    @Test
    void blockEntityData772To773TestBlockShifts() {
        // test_block：772の 45 → 773の 46（shelf を40番に挿入したため+1）。
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(45); // test_block（772）
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(46, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    @Test
    void blockEntityData773To772TestBlockShiftsBack() {
        // 逆向き：test_block 46 → 45。
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(46); // test_block（773）
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(45, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    @Test
    void blockEntityData773To772ShelfDropped() {
        // shelf（773の 40）・copper_golem_statue（773の 48）は772に無いため
        // パケットごと落とす（null）。
        Writer shelf = new Writer();
        shelf.writePositionRaw(0x100L);
        shelf.writeVarInt(40); // shelf（773）
        shelf.writeByte(0); // NBT 無し
        assertNull(ChunkTranslator.translateBlockEntityData773To772(shelf.bytes()));
        Writer statue = new Writer();
        statue.writePositionRaw(0x100L);
        statue.writeVarInt(48); // copper_golem_statue（773）
        statue.writeByte(0); // NBT 無し
        assertNull(ChunkTranslator.translateBlockEntityData773To772(statue.bytes()));
    }

    // ---- スロット（データコンポーネント） ----

    @Test
    void slot772To773ItemShiftsAtShelves() {
        // 棚12件（773=305〜316）の挿入で bookshelf 305→317 に+12ずれる。
        // 354→367（+13）、chain 378→iron_chain 399（改名で移動）も確かめる。
        byte[] out = ChunkTranslator.translateSetSlot772To773(setSlotPacket(304));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(304, readSlotHead(reader)[1]); // bricks（挿入前）

        byte[] out2 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(305));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(317, readSlotHead(reader2)[1]); // bookshelf

        byte[] out3 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(354));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(367, readSlotHead(reader3)[1]);

        byte[] out4 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(378));
        Reader reader4 = new Reader(out4);
        reader4.readVarInt();
        reader4.readVarInt();
        reader4.readShort();
        assertEquals(399, readSlotHead(reader4)[1]); // chain → iron_chain

        byte[] out5 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(1415));
        Reader reader5 = new Reader(out5);
        reader5.readVarInt();
        reader5.readVarInt();
        reader5.readShort();
        assertEquals(1487, readSlotHead(reader5)[1]); // 末尾（+72）
    }

    @Test
    void slot773To772ShelfFallsBackToStone() {
        // acacia_shelf（773=305）は772に無いため石（1）に落とす。
        // bookshelf 317→305、iron_chain 399→chain 378、末尾 1487→1415。
        byte[] out = ChunkTranslator.translateSetSlot773To772(setSlotPacket(305));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(317));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(305, readSlotHead(reader2)[1]); // bookshelf

        byte[] out3 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(399));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(378, readSlotHead(reader3)[1]); // iron_chain → chain

        byte[] out4 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(1487));
        Reader reader4 = new Reader(out4);
        reader4.readVarInt();
        reader4.readVarInt();
        reader4.readShort();
        assertEquals(1415, readSlotHead(reader4)[1]); // 末尾
    }

    @Test
    void slot772To773EnchantmentIdentity() {
        // enchantments は772・773とも種別 10、showTooltip は両方とも無い。
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
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
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
    void slot772To773JukeboxPlayableIdentity() {
        // jukebox_playable（種別 55）。曲は772・773とも同じ並びのため素通し。
        // mall=10（holder 11）、lava_chicken=9（holder 10）のまま。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(11); // holder：曲ID 10（mall）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
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
    void slot773To772JukeboxPlayableIdentity() {
        // 逆向きも素通しする（lava_chicken=9 は772にもある）。
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
        byte[] out = ChunkTranslator.translateSetSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(10, reader.readVarInt()); // holder：曲ID 9（lava_chicken）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot772To773BreakSoundShiftsAtLavaChicken() {
        // break_sound（種別 71）の音 Holder：参照 939（772の music_disc.lava_chicken）
        // → 970（773。銅のチェスト等の音の挿入で+31）。
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
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(971, reader.readVarInt()); // holder：音ID 970 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot773To772BreakSoundCopperChestBecomesStoneBreak() {
        // 逆向き：773にしかない block.copper_chest.close（356）は772では
        // block.stone.break（1442）で代替する。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(357); // holder：音ID 356 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(1443, reader.readVarInt()); // holder：音ID 1442 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot772To773TooltipDisplayIdentity() {
        // tooltip_display（種別 15）は772・773とも {hideTooltip, hiddenComponents}。
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
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
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
    void setCreativeSlot772To773ItemShifts() {
        // UntrustedSlot のままアイテムIDだけ付け替える（bookshelf 305→317）。
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(305); // 772の bookshelf
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(317, reader.readVarInt()); // 773の bookshelf
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void setCreativeSlot773To772ShelfBecomesStone() {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(305); // 773の acacia_shelf
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // 石
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void windowClick772To773Throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick772To773(new byte[0]));
    }

    @Test
    void windowClick773To772Throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> ChunkTranslator.translateWindowClick773To772(new byte[0]));
    }

    // ---- パーティクル ----

    @Test
    void worldParticles772To773PaleOakLeavesShifts() {
        // pale_oak_leaves：772の 34 → 773の 35（copper_fire_flame を5番に挿入）。
        // pale_oak_leaves にデータは無い（772テストで書いていた i32 は
        // 翻訳器が消費せず、requireEnd の失敗でペイロードが素通しされる
        // フォールバックで通っていただけ。773では空データで確かめる）。
        byte[] out = ChunkTranslator.translateWorldParticles772To773(
                worldParticlesPacket(false, 34, new Writer()));
        Reader reader = new Reader(out);
        assertEquals(35, readWorldParticlesId(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles773To772TrailShiftsBack() {
        // trail：773の 49 → 772の 48。データ（3×f64＋i32＋varint）はそのまま。
        Writer data = new Writer();
        data.writeDouble(1.0);
        data.writeDouble(2.0);
        data.writeDouble(3.0);
        data.writeInt(0x11223344);
        data.writeVarInt(7);
        byte[] out = ChunkTranslator.translateWorldParticles773To772(
                worldParticlesPacket(false, 49, data));
        Reader reader = new Reader(out);
        assertEquals(48, readWorldParticlesId(reader));
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0x11223344, reader.readInt());
        assertEquals(7, reader.readVarInt());
    }

    @Test
    void worldParticles772To773BlockCrumbleShifts() {
        // block_crumble：772の 112 → 773の 113。データのブロック状態も
        // trial_spawner 27730→29455 に付ける。
        Writer data = new Writer();
        data.writeVarInt(27730); // trial_spawner（772）
        byte[] out = ChunkTranslator.translateWorldParticles772To773(
                worldParticlesPacket(false, 112, data));
        Reader reader = new Reader(out);
        assertEquals(113, readWorldParticlesId(reader));
        assertEquals(29455, reader.readVarInt());
    }

    // ---- エンティティメタデータ ----

    @Test
    void entityMetadata772To773PaintingIdentity() {
        // 絵画は772・773とも51件・同じ並びのため素通しする。dennis は両方とも
        // 絵画ID 14（holder 15）のまま。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（絵画ID 14 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata772To773(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(15, reader.readVarInt()); // holder（絵画ID 14 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata773To772DennisIdentity() {
        // 逆向きも素通しする（dennis は772にも絵画ID 14 である）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（dennis +1）
        byte[] out = ChunkTranslator.translateEntityMetadata773To772(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(15, reader.readVarInt()); // holder（dennis +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata773To772PaintingIdentity() {
        // kebab（両方とも絵画ID 24、holder 25）も素通しする。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(25); // holder（絵画ID 24 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata773To772(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(25, reader.readVarInt()); // holder（絵画ID 24 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- Spawn Entity ----

    @Test
    void spawnEntity772To773HappyGhastShifts() {
        // ハッピーガスト：772の 56 → 773の 57（copper_golem を27に挿入）。
        byte[] out = ChunkTranslator.translateSpawnEntity772To773(spawnEntityPacket(56, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(57, reader.readVarInt()); // happy_ghast（773）
    }

    @Test
    void spawnEntity773To772CopperGolemBecomesPig() {
        // 銅ゴーレム（773の 27）・マネキン（773の 82）は772に無いためブタ（95）にする。
        byte[] out = ChunkTranslator.translateSpawnEntity773To772(spawnEntityPacket(27, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(95, reader.readVarInt()); // pig（772）
        byte[] out2 = ChunkTranslator.translateSpawnEntity773To772(spawnEntityPacket(82, 0));
        Reader reader2 = new Reader(out2);
        assertEquals(7, reader2.readVarInt()); // entityId
        reader2.readBytes(16); // uuid
        assertEquals(95, reader2.readVarInt()); // pig（772）
    }

    @Test
    void spawnEntity773To772PigShiftsBack() {
        // ブタ：773の 97 → 772の 95。
        byte[] out = ChunkTranslator.translateSpawnEntity773To772(spawnEntityPacket(97, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(95, reader.readVarInt()); // pig（772）
    }

    // ---- Explosion / Update Attributes / Damage Event / Set Cooldown ----

    @Test
    void explosion772To773SoundShifts() {
        // 音は参照 Holder：939（772の music_disc.lava_chicken）→970（773）。
        // 粒子は entity_effect 20→21（copper_fire_flame の挿入で+1）。
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
        byte[] out = ChunkTranslator.translateExplosion772To773(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertTrue(reader.readBoolean());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0.2, reader.readDouble());
        assertEquals(0.3, reader.readDouble());
        assertEquals(21, reader.readVarInt()); // 粒子
        assertEquals(0xFF0000, reader.readInt()); // 粒子の色
        assertEquals(971, reader.readVarInt()); // holder：音ID 970 +1
        assertEquals(0, reader.remaining());
    }

    @Test
    void explosion773To772CopperChestSoundBecomesStoneBreak() {
        // 逆向き：773にしかない block.copper_chest.close（356）は772では
        // block.stone.break（1442）で代替する。粒子は 21→20 に戻る。
        Writer payload = new Writer();
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeBoolean(false); // hasKnockback 無し
        payload.writeVarInt(21); // entity_effect（773）
        payload.writeInt(0x00FF00);
        payload.writeVarInt(357); // holder：音ID 356 +1
        byte[] out = ChunkTranslator.translateExplosion773To772(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(false, reader.readBoolean());
        assertEquals(20, reader.readVarInt());
        assertEquals(0x00FF00, reader.readInt()); // 粒子の色
        assertEquals(1443, reader.readVarInt()); // holder：音ID 1442 +1（block.stone.break）
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateAttributes772To773Identity() {
        // 属性は772・773とも同じ並び（movement_speed 22、camera_distance 8）。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(2); // count 2
        payload.writeVarInt(8); // camera_distance
        payload.writeDouble(50.0);
        payload.writeVarInt(0); // modifiers 0
        payload.writeVarInt(22); // generic.movement_speed
        payload.writeDouble(0.1);
        payload.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes772To773(payload.bytes());
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
    void updateAttributes773To772Identity() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(1); // count 1
        payload.writeVarInt(22); // generic.movement_speed
        payload.writeDouble(0.1);
        payload.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes773To772(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void damageEvent772To773Identity() {
        // ダメージ種別は772・773とも49種・同じ並び。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(0); // sourceType
        payload.writeVarInt(0); // sourceCauseId
        payload.writeVarInt(0); // sourceDirectId
        payload.writeBoolean(true); // hasPosition
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        byte[] out = ChunkTranslator.translateDamageEvent772To773(payload.bytes());
        assertArrayEquals(payload.bytes(), out);
    }

    @Test
    void damageEvent773To772OutOfRangeDropped() {
        // 772・773とも範囲外のダメージ種別（49）は落とす（null）。
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(49); // 範囲外
        payload.writeVarInt(0);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        assertNull(ChunkTranslator.translateDamageEvent773To772(payload.bytes()));
    }

    @Test
    void setCooldown772To773Identity() {
        // Set Cooldown は形式が同一（group 文字列＋ticks）。
        Writer payload = new Writer();
        payload.writeString("test_group");
        payload.writeVarInt(40);
        byte[] out = ChunkTranslator.translateSetCooldown772To773(payload.bytes());
        assertArrayEquals(payload.bytes(), out);
        assertArrayEquals(payload.bytes(),
                ChunkTranslator.translateSetCooldown773To772(payload.bytes()));
    }
}
