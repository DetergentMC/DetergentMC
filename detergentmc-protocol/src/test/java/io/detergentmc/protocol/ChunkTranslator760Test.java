package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の760（1.19.1／1.19.2）⇔761（1.19.3）向け変換テスト。
 * 761形式のペイロードを合成して760形式に変換し、読み直して中身を確認する。
 *
 * <p>760と761のペイロード差（minecraft-data protocol.json で確認）：構造差は
 * block_change・multi_block_change・set_slot・window_items・set_cooldown・
 * entity_equipment・trade_list・open_window・block_action・spawn_entity・
 * named_entity_spawn・entity_metadata・stop_sound・world_particles・
 * entity_effect・respawn・advancements・death_combat_event・end_combat_event・
 * update_light・window_click・set_creative_slot・update_sign・settings・
 * map_chunk・tile_entity_data・login・position・open_sign_entity には無し。
 * 違うのは sound_effect（760 は音ID varint、761 は holder）、
 * entity_sound_effect（同）、server_data（previewsChat）、player_chat、
 * player_info、chat_message、chat_command、message_acknowledgement、
 * explosion（760 は f32、761 は f64）、hide_message。音は
 * {@link ChunkTranslator#translateSoundEffect761To760} などが holder⇔ID で
 * 変換する。760 にだけある named_sound_effect は 761 の sound_effect へ。
 * パケットIDは761→760で118件変更。NBT は両方とも名前付きのためそのまま写す。
 * エンティティメタデータの variant 系は 760・761 で形式が同一のため落とさない。
 * スロットは両方とも bool＋アイテムID＋個数＋名前付き NBT。
 * 761にしか無いもの：ブロック状態1,784件・アイテム34件・エンティティ1種
 * （camel）・音71件・ブロックエンティティ2種は代替（oak／馬／harp 等）へ
 * 写すか、ブロックエンティティは落とす。パーティクルは93件同士で並び同一。
 */
class ChunkTranslator760Test {

    // ---- ヘルパー ----

    /** 高さマップ（名前付き NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** スロット1個（760／761形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** スロットの先頭（個数＋アイテムID）を読む。 */
    private static int[] readSlotHead(Reader reader) {
        assertTrue(reader.readBoolean());
        int itemId = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readByte()); // NBT 無し
        return new int[]{itemId, count};
    }

    /** 間接パレットのセクション1個（ブロック＋バイオーム）。760／761形式（Data Array に長さ varint あり）。 */
    private static byte[] indirectSection(int[] blockPalette, int[] biomePalette) {
        Writer writer = new Writer();
        writer.writeShort(1); // ブロック数
        writer.writeUnsignedByte(4); // BPE（間接）
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(256); // Data Array：4096エントリ×4bit＝256 long
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        writer.writeUnsignedByte(2); // バイオーム BPE（間接）
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(2); // Data Array：64エントリ×2bit＝2 long
        for (int i = 0; i < 2; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** セクションのブロックパレットだけ読み、ブロック状態IDの配列を返す。 */
    private static int[] readBlockPalette(byte[] sections) {
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

    /** セクションのバイオームパレットを読む。 */
    private static int[] readBiomePalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte(); // ブロック BPE
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int blockDataLength = section.readVarInt();
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

    /** Spawn Entity ペイロードの種別と objectData を読む。 */
    private static int[] readSpawnEntityTypeAndData(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // entityId
        reader.readBytes(16); // uuid
        int type = reader.readVarInt();
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte();
        reader.readByte();
        reader.readByte();
        int objectData = reader.readVarInt();
        return new int[]{type, objectData};
    }

    /** チャンク Data ペイロード（761形式）。trustEdges は引数通り。 */
    private static byte[] chunkDataPayload761(int chunkX, int chunkZ,
            int[] blockPalette, int[] biomePalette, int[][] blockEntities, boolean trustEdges) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        payload.writeNamedNbt("", heightmaps());
        payload.writeByteArray(indirectSection(blockPalette, biomePalette));
        payload.writeVarInt(blockEntities.length);
        for (int[] be : blockEntities) {
            payload.writeUnsignedByte(be[0]);
            payload.writeShort(be[1]);
            payload.writeVarInt(be[2]);
            payload.writeByte(0); // NBT 無し
        }
        payload.writeBoolean(trustEdges);
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ（そのまま写る想定）
        return payload.bytes();
    }

    /** チャンク Data ペイロード（760形式）。trustEdges は引数通り。 */
    private static byte[] chunkDataPayload760(int chunkX, int chunkZ,
            int[] blockPalette, int[] biomePalette, int[][] blockEntities, boolean trustEdges) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        payload.writeNamedNbt("", heightmaps());
        payload.writeByteArray(indirectSection(blockPalette, biomePalette));
        payload.writeVarInt(blockEntities.length);
        for (int[] be : blockEntities) {
            payload.writeUnsignedByte(be[0]);
            payload.writeShort(be[1]);
            payload.writeVarInt(be[2]);
            payload.writeByte(0); // NBT 無し
        }
        payload.writeBoolean(trustEdges);
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ
        return payload.bytes();
    }

    // ---- Chunk Data ----

    @Test
    void chunkData761To760RemapsBlockStatesAndBiomesAndDropsBlockEntity() {
        byte[] translated = ChunkTranslator.translateChunkData761To760(
                chunkDataPayload761(3, -2,
                        new int[]{0, 1, 120, 146}, new int[]{38, 31},
                        new int[][]{{0x11, 64, 1}, {0x22, 65, 37}}, true));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        // oak_log 120→118、bamboo_block 146→oak_log 118。
        assertArrayEquals(new int[]{0, 1, 118, 118}, readBlockPalette(sections));
        // バイオームは並びが違う：plains 38→1、meadow 31→29。
        assertArrayEquals(new int[]{1, 29}, readBiomePalette(sections));
        // ブロックエンティティはチェスト（761 type 1→760 type 1）だけ残り、
        // chiseled_bookshelf（761 type 37）は 760 に無いため落とされる。
        assertEquals(1, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readByte()); // NBT 無し
        // trustEdges（true）はそのまま写る。
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData761To760TrustEdgesFalsePassesThrough() {
        byte[] translated = ChunkTranslator.translateChunkData761To760(
                chunkDataPayload761(0, 0,
                        new int[]{0}, new int[]{38},
                        new int[][]{}, false));
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        reader.readByteArray();
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData760To761RemapsBack() {
        byte[] translated = ChunkTranslator.translateChunkData760To761(
                chunkDataPayload760(3, -2,
                        new int[]{0, 1, 118, 15}, new int[]{1, 29},
                        new int[][]{{0x11, 64, 1}}, true));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        // oak_log 118→120、oak_planks 15→15（同番号）。
        assertArrayEquals(new int[]{0, 1, 120, 15}, readBlockPalette(sections));
        // plains 1→38、meadow 29→31。
        assertArrayEquals(new int[]{38, 31}, readBiomePalette(sections));
        assertEquals(1, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean()); // trustEdges
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkDataBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02}; // 短すぎて読めない
        assertArrayEquals(broken, ChunkTranslator.translateChunkData761To760(broken));
        assertArrayEquals(broken, ChunkTranslator.translateChunkData760To761(broken));
    }

    // ---- Block Change / Multi Block Change / Block Entity Data ----

    @Test
    void blockChange() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(146); // bamboo_block（761）
        byte[] translated = ChunkTranslator.translateBlockChange761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(118, reader.readVarInt()); // oak_log（760）
        assertEquals(0, reader.remaining());

        Writer payload2 = new Writer();
        payload2.writePositionRaw(0x123456789AL);
        payload2.writeVarInt(120); // oak_log（761）
        byte[] translated2 = ChunkTranslator.translateBlockChange761To760(payload2.bytes());
        Reader reader2 = new Reader(translated2);
        assertEquals(0x123456789AL, reader2.readPositionRaw());
        assertEquals(118, reader2.readVarInt()); // oak_log（760）
        assertEquals(0, reader2.remaining());

        // 逆向き：760 の oak_log 状態は 761 の oak_log 状態へ（bamboo_block には戻らない）。
        Writer payload760 = new Writer();
        payload760.writePositionRaw(0x123456789AL);
        payload760.writeVarInt(118);
        byte[] back = ChunkTranslator.translateBlockChange760To761(payload760.bytes());
        Reader reader760 = new Reader(back);
        assertEquals(0x123456789AL, reader760.readPositionRaw());
        assertEquals(120, reader760.readVarInt()); // oak_log（761）
        assertEquals(0, reader760.remaining());
    }

    @Test
    void multiBlockChangePassesThroughSuppressLightUpdates() {
        Writer payload = new Writer();
        payload.writeLong(0x1122334455667788L);
        payload.writeBoolean(true); // suppressLightUpdates（760・761 ともにある）
        payload.writeVarInt(2);
        payload.writeVarInt((146 << 12) | 0x123); // bamboo_block
        payload.writeVarInt((120 << 12) | 0x456); // oak_log
        byte[] translated = ChunkTranslator.translateMultiBlockChange761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x1122334455667788L, reader.readLong());
        assertEquals(true, reader.readBoolean()); // そのまま写る
        assertEquals(2, reader.readVarInt());
        assertEquals((118 << 12) | 0x123, reader.readVarInt());
        assertEquals((118 << 12) | 0x456, reader.readVarInt());
        assertEquals(0, reader.remaining());

        Writer payload760 = new Writer();
        payload760.writeLong(0x1122334455667788L);
        payload760.writeBoolean(false);
        payload760.writeVarInt(1);
        payload760.writeVarInt((118 << 12) | 0x123);
        byte[] back = ChunkTranslator.translateMultiBlockChange760To761(payload760.bytes());
        Reader reader760 = new Reader(back);
        assertEquals(0x1122334455667788L, reader760.readLong());
        assertEquals(false, reader760.readBoolean());
        assertEquals(1, reader760.readVarInt());
        assertEquals((120 << 12) | 0x123, reader760.readVarInt());
        assertEquals(0, reader760.remaining());
    }

    @Test
    void blockEntityData() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(0); // furnace（761）
        payload.writeByte(0); // NBT 無し
        byte[] translated = ChunkTranslator.translateBlockEntityData761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(0, reader.readVarInt()); // furnace（760、同一）
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());

        // chiseled_bookshelf（761=37）と hanging_sign（761=8）は 760 に無いため落とす。
        Writer shelf = new Writer();
        shelf.writePositionRaw(0x123456789AL);
        shelf.writeVarInt(37);
        shelf.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData761To760(shelf.bytes()));

        Writer sign = new Writer();
        sign.writePositionRaw(0x123456789AL);
        sign.writeVarInt(8);
        sign.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData761To760(sign.bytes()));

        // 範囲外の種別も null（パケットごと落とす）。
        Writer unknown = new Writer();
        unknown.writePositionRaw(0x123456789AL);
        unknown.writeVarInt(99999);
        unknown.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData761To760(unknown.bytes()));

        // 逆向き：furnace（760=0）は 761 でも 0。
        Writer payload760 = new Writer();
        payload760.writePositionRaw(0x123456789AL);
        payload760.writeVarInt(0);
        payload760.writeByte(0);
        byte[] back = ChunkTranslator.translateBlockEntityData760To761(payload760.bytes());
        Reader reader760 = new Reader(back);
        assertEquals(0x123456789AL, reader760.readPositionRaw());
        assertEquals(0, reader760.readVarInt());
        assertEquals(0, reader760.readByte());
        assertEquals(0, reader760.remaining());
    }

    // ---- スロット・ウィンドウ ----

    @Test
    void setSlot() {
        Writer payload = new Writer();
        payload.writeByte(0); // windowId
        payload.writeVarInt(0); // stateId
        payload.writeShort(3); // slot
        writeSlot(payload, 946, 1); // camel_spawn_egg（761）
        byte[] translated = ChunkTranslator.translateSetSlot761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{937, 1}, readSlotHead(reader)); // horse_spawn_egg（760）
        assertEquals(0, reader.remaining());

        // 逆向き：760 の horse_spawn_egg は 761 の horse_spawn_egg へ（camel には戻らない）。
        Writer payload760 = new Writer();
        payload760.writeByte(0);
        payload760.writeVarInt(0);
        payload760.writeShort(3);
        writeSlot(payload760, 937, 1);
        byte[] back = ChunkTranslator.translateSetSlot760To761(payload760.bytes());
        Reader reader760 = new Reader(back);
        assertEquals(0, reader760.readByte());
        assertEquals(0, reader760.readVarInt());
        assertEquals(3, reader760.readShort());
        assertArrayEquals(new int[]{967, 1}, readSlotHead(reader760)); // horse_spawn_egg（761）
        assertEquals(0, reader760.remaining());

        // bamboo_raft（761=727）は oak_boat（760=698）へ。
        Writer raft = new Writer();
        raft.writeByte(0);
        raft.writeVarInt(0);
        raft.writeShort(0);
        writeSlot(raft, 727, 1);
        byte[] raftOut = ChunkTranslator.translateSetSlot761To760(raft.bytes());
        Reader raftReader = new Reader(raftOut);
        raftReader.readByte();
        raftReader.readVarInt();
        raftReader.readShort();
        assertArrayEquals(new int[]{698, 1}, readSlotHead(raftReader));
    }

    @Test
    void windowItems() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(2); // windowId
        payload.writeVarInt(5); // stateId
        payload.writeVarInt(2); // count
        writeSlot(payload, 946, 1); // camel_spawn_egg
        writeSlot(payload, 727, 2); // bamboo_raft
        writeSlot(payload, 1035, 1); // piglin_head（カーソル）
        byte[] translated = ChunkTranslator.translateWindowItems761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertArrayEquals(new int[]{937, 1}, readSlotHead(reader)); // horse_spawn_egg
        assertArrayEquals(new int[]{698, 2}, readSlotHead(reader)); // oak_boat
        assertArrayEquals(new int[]{999, 1}, readSlotHead(reader)); // zombie_head
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCooldown() {
        Writer payload = new Writer();
        payload.writeVarInt(946); // camel_spawn_egg（761）
        payload.writeVarInt(20); // ticks
        byte[] translated = ChunkTranslator.translateSetCooldown761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(937, reader.readVarInt()); // horse_spawn_egg（760）
        assertEquals(20, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityEquipment() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeUnsignedByte(0); // slot 0（最後）
        writeSlot(payload, 946, 1); // camel_spawn_egg
        byte[] translated = ChunkTranslator.translateEntityEquipment761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertArrayEquals(new int[]{937, 1}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeList() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(1); // count
        writeSlot(payload, 946, 1); // camel_spawn_egg
        writeSlot(payload, 727, 1); // bamboo_raft
        writeSlot(payload, 1035, 1); // piglin_head
        payload.writeBoolean(false);
        payload.writeInt(1);
        payload.writeInt(2);
        payload.writeInt(3);
        payload.writeInt(4);
        payload.writeFloat(0.5f);
        payload.writeInt(5);
        payload.writeVarInt(0);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        payload.writeBoolean(true);
        byte[] translated = ChunkTranslator.translateTradeList761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertArrayEquals(new int[]{937, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{698, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{999, 1}, readSlotHead(reader));
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(3, reader.readInt());
        assertEquals(4, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void openWindow() {
        // メニューは 760・761 ともに 24種で並び同一のためそのまま通る。
        Writer payload = new Writer();
        payload.writeVarInt(1); // windowId
        payload.writeVarInt(2); // generic_9x3（761）
        payload.writeString("{\"text\":\"Chest\"}");
        byte[] translated = ChunkTranslator.translateOpenWindow761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt()); // generic_9x3（760）
        assertEquals("{\"text\":\"Chest\"}", reader.readString());
        assertEquals(0, reader.remaining());

        // 逆向き：760 の crafting（11）は 761 の crafting（11）へ。
        Writer payload760 = new Writer();
        payload760.writeVarInt(1);
        payload760.writeVarInt(11); // crafting（760）
        payload760.writeString("{\"text\":\"Crafting\"}");
        byte[] back = ChunkTranslator.translateOpenWindow760To761(payload760.bytes());
        Reader reader760 = new Reader(back);
        assertEquals(1, reader760.readVarInt());
        assertEquals(11, reader760.readVarInt()); // crafting（761）
        assertEquals("{\"text\":\"Crafting\"}", reader760.readString());
    }

    @Test
    void blockAction() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeUnsignedByte(1);
        payload.writeUnsignedByte(2);
        payload.writeVarInt(51); // bamboo_block（761）
        byte[] translated = ChunkTranslator.translateBlockAction761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(40, reader.readVarInt()); // oak_log（760）
        assertEquals(0, reader.remaining());

        // note_block（761=93）は 760 の note_block（89）へ。
        Writer note = new Writer();
        note.writePositionRaw(0x123456789AL);
        note.writeUnsignedByte(1);
        note.writeUnsignedByte(2);
        note.writeVarInt(93);
        byte[] noteOut = ChunkTranslator.translateBlockAction761To760(note.bytes());
        Reader noteReader = new Reader(noteOut);
        noteReader.readPositionRaw();
        noteReader.readUnsignedByte();
        noteReader.readUnsignedByte();
        assertEquals(89, noteReader.readVarInt());

        // 逆向き：760 の oak_log（40）は 761 の oak_log（42）へ。
        Writer payload760 = new Writer();
        payload760.writePositionRaw(0x123456789AL);
        payload760.writeUnsignedByte(1);
        payload760.writeUnsignedByte(2);
        payload760.writeVarInt(40);
        byte[] back = ChunkTranslator.translateBlockAction760To761(payload760.bytes());
        Reader reader760 = new Reader(back);
        assertEquals(0x123456789AL, reader760.readPositionRaw());
        assertEquals(1, reader760.readUnsignedByte());
        assertEquals(2, reader760.readUnsignedByte());
        assertEquals(42, reader760.readVarInt());
        assertEquals(0, reader760.remaining());
    }

    // ---- エンティティ・音・パーティクル・エフェクト ----

    @Test
    void spawnEntity() {
        // camel（761=11）は 760 では馬（40）になる。
        assertArrayEquals(new int[]{40, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity761To760(spawnEntityPacket(11, 0))));
        // falling_block の objectData はブロック状態IDなので一緒に変換する。
        assertArrayEquals(new int[]{29, 118},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity761To760(
                                spawnEntityPacket(30, 146))));
        // 逆向き：馬（760=40）は 761 の馬（41）へ。
        assertArrayEquals(new int[]{41, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity760To761(spawnEntityPacket(40, 0))));
        assertArrayEquals(new int[]{30, 120},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity760To761(
                                spawnEntityPacket(29, 118))));
        // ゾンビ（761=113）は 760 では 112。
        assertArrayEquals(new int[]{112, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity761To760(spawnEntityPacket(113, 0))));
    }

    @Test
    void entityMetadataSlotAndBlockStateAndVariantPassThrough() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(7); // item_stack（スロット）
        writeSlot(writer, 946, 1); // camel_spawn_egg（761）
        writer.writeUnsignedByte(1); // index 1
        writer.writeVarInt(14); // block_state
        writer.writeVarInt(146); // bamboo_block（761）
        writer.writeUnsignedByte(2); // index 2
        writer.writeVarInt(22); // frog_variant（760・761とも同じ番号）
        writer.writeVarInt(3);
        writer.writeUnsignedByte(3); // index 3
        writer.writeVarInt(24); // painting_variant
        writer.writeVarInt(5);
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata761To760(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        assertArrayEquals(new int[]{937, 1}, readSlotHead(reader)); // horse_spawn_egg（760）
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(14, reader.readVarInt());
        assertEquals(118, reader.readVarInt()); // oak_log（760）
        // variant 系は落とさずそのまま通る（765→763 との違い）。
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(22, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadataParticle() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(17); // particle
        writer.writeVarInt(30); // sculk_charge（761）
        writer.writeFloat(1.5f); // 回転
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata761To760(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(17, reader.readVarInt());
        assertEquals(30, reader.readVarInt()); // sculk_charge（760、同一）
        assertEquals(1.5f, reader.readFloat());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void soundEffect() {
        // 数値 holder：761 の block.wood.break（1349）は holder 1350 として来る。
        // 760 では音ID varint（1278）になる。
        Writer writer = new Writer();
        writer.writeVarInt(1350); // holder = id + 1
        writer.writeVarInt(1); // source
        writer.writeInt(8);
        writer.writeInt(64);
        writer.writeInt(8);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L);
        byte[] out = ChunkTranslator.translateSoundEffect761To760(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1278, reader.readVarInt()); // block.wood.break（760）
        assertEquals(1, reader.readVarInt());
        assertEquals(8, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(8, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());

        // 文字列 holder：block.bamboo_wood.break は block.wood.break に代替。
        Writer named = new Writer();
        named.writeVarInt(0); // holder = 文字列
        named.writeString("block.bamboo_wood.break");
        named.writeBoolean(false); // hasRange なし
        named.writeVarInt(1);
        named.writeInt(8);
        named.writeInt(64);
        named.writeInt(8);
        named.writeFloat(1.0f);
        named.writeFloat(1.0f);
        named.writeLong(12345L);
        byte[] out2 = ChunkTranslator.translateSoundEffect761To760(named.bytes());
        Reader reader2 = new Reader(out2);
        assertEquals(1278, reader2.readVarInt()); // block.wood.break（760）
        assertEquals(1, reader2.readVarInt());

        // 逆向き：760 の block.wood.break（1278）は 761 の holder 1350 へ。
        Writer back = new Writer();
        back.writeVarInt(1278); // 音ID（760）
        back.writeVarInt(1);
        back.writeInt(8);
        back.writeInt(64);
        back.writeInt(8);
        back.writeFloat(1.0f);
        back.writeFloat(1.0f);
        back.writeLong(12345L);
        byte[] out3 = ChunkTranslator.translateSoundEffect760To761(back.bytes());
        Reader reader3 = new Reader(out3);
        assertEquals(1350, reader3.readVarInt());
        assertEquals(1, reader3.readVarInt());
    }

    @Test
    void namedSoundEffect760To761() {
        // 760 にだけある named_sound_effect は 761 の sound_effect
        //（holder 0＋名前・hasRange なし）になる。
        Writer writer = new Writer();
        writer.writeString("block.wood.break");
        writer.writeVarInt(1); // source
        writer.writeInt(8);
        writer.writeInt(64);
        writer.writeInt(8);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L);
        byte[] out = ChunkTranslator.translateNamedSoundEffect760To761(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt()); // holder = 文字列
        assertEquals("block.wood.break", reader.readString());
        assertEquals(false, reader.readBoolean()); // hasRange なし
        assertEquals(1, reader.readVarInt());
        assertEquals(8, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(8, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entitySoundEffect() {
        // 数値 holder：761 の block.wood.break（1349）は holder 1350 として来る。
        // 760 では音ID varint（1278）になる。
        Writer writer = new Writer();
        writer.writeVarInt(1350); // holder = id + 1
        writer.writeVarInt(1); // source
        writer.writeVarInt(7); // entityId
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L);
        byte[] out = ChunkTranslator.translateEntitySoundEffect761To760(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1278, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());

        // 逆向き：760 の block.wood.break（1278）は 761 の holder 1350 へ。
        Writer back = new Writer();
        back.writeVarInt(1278);
        back.writeVarInt(1);
        back.writeVarInt(7);
        back.writeFloat(1.0f);
        back.writeFloat(1.0f);
        back.writeLong(12345L);
        byte[] out2 = ChunkTranslator.translateEntitySoundEffect760To761(back.bytes());
        Reader reader2 = new Reader(out2);
        assertEquals(1350, reader2.readVarInt());
        assertEquals(1, reader2.readVarInt());
        assertEquals(7, reader2.readVarInt());
    }

    @Test
    void stopSound() {
        Writer writer = new Writer();
        writer.writeByte(0x02); // 名前だけ
        writer.writeString("block.bamboo_wood.break");
        byte[] out = ChunkTranslator.translateStopSound761To760(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x02, reader.readByte());
        assertEquals("block.wood.break", reader.readString());
        assertEquals(0, reader.remaining());

        // 逆向き：760 の名前は 761 にも同じ名前であるため変わらない。
        Writer writer760 = new Writer();
        writer760.writeByte(0x02);
        writer760.writeString("block.wood.break");
        byte[] back = ChunkTranslator.translateStopSound760To761(writer760.bytes());
        Reader reader760 = new Reader(back);
        assertEquals(0x02, reader760.readByte());
        assertEquals("block.wood.break", reader760.readString());
        assertEquals(0, reader760.remaining());
    }

    @Test
    void worldParticles() {
        // sculk_charge：761=30 → 760=30。回転 float 付き。
        Writer sculk = new Writer();
        sculk.writeVarInt(30);
        sculk.writeFloat(2.5f);
        sculk.writeBoolean(true);
        sculk.writeDouble(1.0);
        sculk.writeDouble(2.0);
        sculk.writeDouble(3.0);
        sculk.writeFloat(0.1f);
        sculk.writeFloat(0.2f);
        sculk.writeFloat(0.3f);
        sculk.writeFloat(0.4f);
        sculk.writeInt(5);
        byte[] out = ChunkTranslator.translateWorldParticles761To760(sculk.bytes());
        Reader reader = new Reader(out);
        assertEquals(30, reader.readVarInt());
        assertEquals(2.5f, reader.readFloat());
        assertEquals(true, reader.readBoolean());
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.4f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.remaining());

        // block：ブロック状態IDを変換する（bamboo_block 146→oak_log 118）。
        Writer block = new Writer();
        block.writeVarInt(2);
        block.writeVarInt(146); // bamboo_block（761）
        block.writeBoolean(false);
        block.writeDouble(0.0);
        block.writeDouble(0.0);
        block.writeDouble(0.0);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeInt(1);
        byte[] out2 = ChunkTranslator.translateWorldParticles761To760(block.bytes());
        Reader reader2 = new Reader(out2);
        assertEquals(2, reader2.readVarInt());
        assertEquals(118, reader2.readVarInt()); // oak_log（760）

        // item：761=39 → 760=39。スロットを変換する。
        Writer item = new Writer();
        item.writeVarInt(39);
        writeSlot(item, 946, 1); // camel_spawn_egg
        item.writeBoolean(false);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeInt(1);
        byte[] out3 = ChunkTranslator.translateWorldParticles761To760(item.bytes());
        Reader reader3 = new Reader(out3);
        assertEquals(39, reader3.readVarInt());
        assertArrayEquals(new int[]{937, 1}, readSlotHead(reader3));

        // 逆向き：shriek 760=92 → 761=92。遅延 tick 数付き。
        Writer shriek = new Writer();
        shriek.writeVarInt(92);
        shriek.writeVarInt(7);
        shriek.writeBoolean(false);
        shriek.writeDouble(0.0);
        shriek.writeDouble(0.0);
        shriek.writeDouble(0.0);
        shriek.writeFloat(0.0f);
        shriek.writeFloat(0.0f);
        shriek.writeFloat(0.0f);
        shriek.writeFloat(0.0f);
        shriek.writeInt(1);
        byte[] out4 = ChunkTranslator.translateWorldParticles760To761(shriek.bytes());
        Reader reader4 = new Reader(out4);
        assertEquals(92, reader4.readVarInt());
        assertEquals(7, reader4.readVarInt());
        assertEquals(false, reader4.readBoolean());
        assertEquals(0.0, reader4.readDouble());
        assertEquals(0.0, reader4.readDouble());
        assertEquals(0.0, reader4.readDouble());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(1, reader4.readInt());
        assertEquals(0, reader4.remaining());
    }

    @Test
    void entityEffectIdentity() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(5); // effectId（1ベースで同一）
        writer.writeByte(1); // amplifier
        writer.writeVarInt(100); // duration
        writer.writeByte(0); // flags
        writer.writeBoolean(true); // factorCodec あり
        writer.writeNamedNbt("", new Tag.CompoundTag(Map.of()));
        byte[] out = ChunkTranslator.translateEntityEffect761To760(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(0, reader.remaining());

        // 逆向きも同じ。
        byte[] back = ChunkTranslator.translateEntityEffect760To761(writer.bytes());
        assertArrayEquals(out, back);
    }

    // ---- サーバー行き ----

    @Test
    void windowClick761To760() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(2); // windowId
        payload.writeVarInt(5); // stateId
        payload.writeShort(3); // clicked slot
        payload.writeByte(0); // button
        payload.writeVarInt(0); // mode
        payload.writeVarInt(1); // count
        payload.writeShort(3);
        writeSlot(payload, 821, 1); // oak_sign（761）
        writeSlot(payload, 1001, 1); // turtle_spawn_egg（761、カーソル）
        byte[] translated = ChunkTranslator.translateWindowClick761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{804, 1}, readSlotHead(reader)); // oak_sign（760）
        assertArrayEquals(new int[]{969, 1}, readSlotHead(reader)); // turtle_spawn_egg（760）
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCreativeSlot761To760() {
        Writer payload = new Writer();
        payload.writeShort(5);
        writeSlot(payload, 821, 1); // oak_sign（761）
        byte[] translated = ChunkTranslator.translateSetCreativeSlot761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(5, reader.readShort());
        assertArrayEquals(new int[]{804, 1}, readSlotHead(reader)); // oak_sign（760）
        assertEquals(0, reader.remaining());
    }

    @Test
    void brokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02}; // 短すぎて読めない
        assertArrayEquals(broken, ChunkTranslator.translateBlockChange761To760(broken));
        assertArrayEquals(broken, ChunkTranslator.translateBlockChange760To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSetSlot761To760(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSetSlot760To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSpawnEntity761To760(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSpawnEntity760To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSoundEffect761To760(broken));
        assertArrayEquals(broken, ChunkTranslator.translateWorldParticles761To760(broken));
    }

    // ---- Named Entity Spawn（player だけ 761 の Spawn Entity と 760 の Named Entity Spawn で違う） ----

    @Test
    void readSpawnEntityType760() {
        assertEquals(116, ChunkTranslator.readSpawnEntityType760(spawnEntityPacket(116, 0)));
        assertEquals(112, ChunkTranslator.readSpawnEntityType760(spawnEntityPacket(112, 0)));
        assertEquals(-1, ChunkTranslator.readSpawnEntityType760(new byte[]{0x00, 0x01, 0x02}));
    }

    @Test
    void spawnEntityPlayerBecomesNamedEntitySpawn() {
        // 761 の Spawn Entity（player=117）は 760 の Named Entity Spawn になる。
        // pitch・yaw の順番が入れ替わる（Spawn Entity は pitch・yaw・headPitch、
        // Named Entity Spawn は yaw・pitch）。
        Writer spawn = new Writer();
        spawn.writeVarInt(7); // entityId
        spawn.writeBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        spawn.writeVarInt(117); // player（761）
        spawn.writeDouble(1.0);
        spawn.writeDouble(2.0);
        spawn.writeDouble(3.0);
        spawn.writeByte(10); // pitch
        spawn.writeByte(20); // yaw
        spawn.writeByte(30); // headPitch（落とす）
        spawn.writeVarInt(0); // objectData（落とす）
        spawn.writeShort(100); // velocityX（落とす）
        spawn.writeShort(200);
        spawn.writeShort(300);
        byte[] named = ChunkTranslator.spawnEntity761ToNamedEntitySpawn760(spawn.bytes());
        Reader reader = new Reader(named);
        assertEquals(7, reader.readVarInt());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                reader.readBytes(16));
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(20, reader.readByte()); // yaw
        assertEquals(10, reader.readByte()); // pitch
        assertEquals(0, reader.remaining());

        // player 以外は null（呼び出し側は通常の Spawn Entity 変換を使う）。
        assertNull(ChunkTranslator.spawnEntity761ToNamedEntitySpawn760(spawnEntityPacket(113, 0)));
    }

    @Test
    void namedEntitySpawnBecomesSpawnEntityPlayer() {
        Writer named = new Writer();
        named.writeVarInt(7); // entityId
        named.writeBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        named.writeDouble(1.0);
        named.writeDouble(2.0);
        named.writeDouble(3.0);
        named.writeByte(20); // yaw
        named.writeByte(10); // pitch
        byte[] spawn = ChunkTranslator.namedEntitySpawn760ToSpawnEntity761(named.bytes());
        Reader reader = new Reader(spawn);
        assertEquals(7, reader.readVarInt());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                reader.readBytes(16));
        assertEquals(117, reader.readVarInt()); // player（761）
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(10, reader.readByte()); // pitch
        assertEquals(20, reader.readByte()); // yaw
        assertEquals(10, reader.readByte()); // headPitch（pitch と同じ値）
        assertEquals(0, reader.readVarInt()); // objectData
        assertEquals(0, reader.readShort()); // velocityX
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.remaining());
    }
}
