package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * 1.21.11（プロトコル774）のチャンク・スロット・メタデータ変換のテスト。
 *
 * <p>773→774の変更（minecraft-data・ViaVersion mapping-1.21.11 の表に準拠）：
 * ブロック状態は29,671件・同一、バイオームは65件・同じ並び、属性は35件・
 * 同じ並び、メニューは25件・同じ並び、パーティクルは115件・同じ並び、
 * ブロックエンティティ種別は49件・同じ並び、絵画は51件・同じ並び、
 * ジュークボックスの歌は21件・同じ並び、heightmap は直接の配列。
 * データコンポーネント種別は96→104件で、8件の新規挿入（use_effects 5、
 * minimum_attack_charge 7、damage_type 8、attack_range 30、piercing_weapon 38、
 * kinetic_weapon 39、swing_animation 40、zombie_nautilus/variant 94）のため
 * KIND 表で付け替える（enchantments は773=10→774=13、
 * jukebox_playable は773=55→774=62）。774の新規8種は773向けに落とす。
 * アイテムは1,488→1,505件（music_disc.lava_chicken 1304→1316、槍7件・
 * ノーチラスの防具5件・スポーンエッグ4件・ネザライトの馬鎧1件は774にしか
 * 無いため773向けは石に落とす）。エンティティは153→157件（pig 97→100、
 * ghast 56→57、happy_ghast 57→58、falling_block 50→51。774にしか無い
 * 4件は773向けは豚に落とす）。エンティティメタデータの型番号は28番以降が
 * +1ずれる（zombie_nautilus_variant を28に挿入、humanoid_arm を38に追加。
 * 773に無い2種はエントリごと落とす）。エフェクトは39→40件
 * （BreathOfTheNautilus が39に追加）が、Entity Effect のエフェクトIDは
 * 773⇔774で付け替えず素通しするため、774→773で39がそのまま773クライア
 * ントに届く（773クライアントは39を知らない）。残る制限として報告する。
 * Window Click は HashedSlot のため変換できず落とす（ProxySession 側で落とす）。
 */
class ChunkTranslator774Test {

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

    /** 間接パレットのセクション1個。773／774形式（Data Array に長さ varint が無い）。 */
    private static byte[] indirectSection773(int[] blockPalette, int[] biomePalette) {
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

    /** セクションのバイオームパレットを読む。773／774形式（Data Array に長さ varint が無い）。 */
    private static int[] readBiomePalette774(byte[] sections) {
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
        section.readUnsignedByte();
        int biomePaletteSize = section.readVarInt();
        int[] palette = new int[biomePaletteSize];
        for (int i = 0; i < biomePaletteSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
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

    /** Spawn Entity ペイロードの種別だけを読む。 */
    private static int readSpawnEntityType(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // entityId
        reader.readBytes(16); // uuid
        return reader.readVarInt();
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

    /** チャンク Data ペイロード（773／774共通の形）。セクションは1個。 */
    private static byte[] chunkDataPayload(int chunkX, int chunkZ, int[] blockPalette) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        writeHeightmapArray(payload);
        payload.writeByteArray(indirectSection773(blockPalette, new int[]{38, 39}));
        payload.writeVarInt(0); // ブロックエンティティは0件
        return payload.bytes();
    }

    // ---- チャンク（ブロック状態・バイオームは773・774とも同一） ----

    @Test
    void chunkData773To774BlockStatesIdentity() {
        byte[] translated = ChunkTranslator.translateChunkData773To774(
                chunkDataPayload(3, -2, new int[]{0, 1, 12345, 29670}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]); // WORLD_SURFACE
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]); // MOTION_BLOCKING
        byte[] sections = reader.readByteArray();
        assertArrayEquals(new int[]{0, 1, 12345, 29670}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette774(sections));
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
        assertEquals(0, reader.remaining());
    }

    // ---- スロット（アイテム） ----

    @Test
    void slot773To774ItemShiftsAtSpears() {
        // 1304（music_disc.lava_chicken）→1316（+12）。槍7件とスポーンエッグ
        // 等の挿入で後ろがずれる。oak_boat 863 は同一。末尾 1487→1504（+17）。
        byte[] out = ChunkTranslator.translateSetSlot773To774(setSlotPacket(1304));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1316, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot773To774(setSlotPacket(863));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(863, readSlotHead(reader2)[1]); // oak_boat

        byte[] out3 = ChunkTranslator.translateSetSlot773To774(setSlotPacket(1487));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1504, readSlotHead(reader3)[1]); // 末尾
    }

    @Test
    void slot774To773ItemShiftsBack() {
        byte[] out = ChunkTranslator.translateSetSlot774To773(setSlotPacket(1316));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1304, readSlotHead(reader)[1]); // lava_chicken

        byte[] out2 = ChunkTranslator.translateSetSlot774To773(setSlotPacket(863));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(863, readSlotHead(reader2)[1]); // oak_boat

        byte[] out3 = ChunkTranslator.translateSetSlot774To773(setSlotPacket(1504));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1487, readSlotHead(reader3)[1]); // 末尾
    }

    @Test
    void slot774To773NewItemFallsBackToStone() {
        // netherite_spear（774=1302）・nautilus_spawn_egg（774=1156）・
        // wooden_spear（774=1296）は773に無いため石（1）に落とす。
        byte[] out = ChunkTranslator.translateSetSlot774To773(setSlotPacket(1302));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot774To773(setSlotPacket(1156));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot774To773(setSlotPacket(1296));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1, readSlotHead(reader3)[1]);
    }

    // ---- スロット（データコンポーネント） ----

    @Test
    void slot773To774EnchantmentsKindAndIdShift() {
        // enchantments は773=10→774=13、mending は22→23（lunge の挿入）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(10); // enchantments（773）
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(22); // mending（773）
        writer.writeVarInt(3); // level
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot773To774(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(13, reader.readVarInt()); // enchantments（774）
        assertEquals(1, reader.readVarInt());
        assertEquals(23, reader.readVarInt()); // mending（774）
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot774To773EnchantmentsKindAndIdShiftBack() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(13); // enchantments（774）
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(23); // mending（774）
        writer.writeVarInt(3); // level
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot774To773(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(10, reader.readVarInt()); // enchantments（773）
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt()); // mending（773）
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void slot774To773NewComponentDropped() {
        // use_effects（774=5、773に無い）は落とし、enchantments だけ残る。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(2); // added 2
        writer.writeVarInt(5); // use_effects（774）
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeFloat(1.5f);
        writer.writeVarInt(13); // enchantments（774）
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(23); // mending（774）
        writer.writeVarInt(3); // level
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot774To773(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1（use_effects は落とした）
        assertEquals(10, reader.readVarInt()); // enchantments（773）
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt()); // mending（773）
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
        assertEquals(0, reader.remaining());
    }

    @Test
    void slot773To774JukeboxPlayableKindShifts() {
        // jukebox_playable は773=55→774=62。曲は773・774とも同じ並びのため
        // holder はそのまま（lava_chicken=9 は holder 10）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable（773）
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(10); // holder：曲ID 9（lava_chicken）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot773To774(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(62, reader.readVarInt()); // jukebox_playable（774）
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(10, reader.readVarInt()); // holder：曲ID 9（lava_chicken）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    // ---- エンティティメタデータ（型番号は28番以降が+1） ----

    @Test
    void entityMetadata773To774PaintingTypeShifts() {
        // painting は773=29→774=30。holder（絵画ID 5＋1＝6）は同一。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(29); // painting（773）
        writer.writeVarInt(6); // holder：絵画ID 5 +1
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata773To774(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt()); // painting（774）
        assertEquals(6, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata774To773ZombieNautilusVariantDropped() {
        // zombie_nautilus_variant（774=28）はエントリごと落とし、type 0 の
        // エントリは残る。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(28); // zombie_nautilus_variant（774）
        writer.writeVarInt(2); // variant
        writer.writeUnsignedByte(1); // index 1
        writer.writeVarInt(0); // byte
        writer.writeByte(7);
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata774To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(7, reader.readByte());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata774To773HumanoidArmDropped() {
        // humanoid_arm（774=38）はエントリごと落とし、type 1（varint）の
        // エントリは残る。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(38); // humanoid_arm（774）
        writer.writeVarInt(1); // arm
        writer.writeUnsignedByte(1); // index 1
        writer.writeVarInt(1); // varint
        writer.writeVarInt(42);
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata774To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(42, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    // ---- Spawn Entity ----

    @Test
    void spawnEntity773To774PigShifts() {
        assertEquals(100, readSpawnEntityType(
                ChunkTranslator.translateSpawnEntity773To774(spawnEntityPacket(97, 0))));
        assertEquals(57, readSpawnEntityType(
                ChunkTranslator.translateSpawnEntity773To774(spawnEntityPacket(56, 0))));
        assertEquals(58, readSpawnEntityType(
                ChunkTranslator.translateSpawnEntity773To774(spawnEntityPacket(57, 0))));
        assertEquals(51, readSpawnEntityType(
                ChunkTranslator.translateSpawnEntity773To774(spawnEntityPacket(50, 0))));
    }

    @Test
    void spawnEntity774To773NautilusBecomesPig() {
        // nautilus（774=88）・zombie_nautilus（774=152）は773に無いため豚になる。
        assertEquals(97, readSpawnEntityType(
                ChunkTranslator.translateSpawnEntity774To773(spawnEntityPacket(88, 0))));
        assertEquals(97, readSpawnEntityType(
                ChunkTranslator.translateSpawnEntity774To773(spawnEntityPacket(152, 0))));
        assertEquals(97, readSpawnEntityType(
                ChunkTranslator.translateSpawnEntity774To773(spawnEntityPacket(100, 0))));
    }

    // ---- Entity Effect（エフェクトIDは付け替えず素通し） ----

    @Test
    void entityEffect773To774Identity() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(5); // effectId
        writer.writeVarInt(1); // amplifier
        writer.writeVarInt(100); // duration
        writer.writeUnsignedByte(0); // flags
        byte[] out = ChunkTranslator.translateEntityEffect773To774(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }
}
