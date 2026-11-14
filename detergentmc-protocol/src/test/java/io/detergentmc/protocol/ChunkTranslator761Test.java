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
 * {@link ChunkTranslator} の761（1.19.3）⇔762（1.19.4）向け変換テスト。
 * 762形式のペイロードを合成して761形式に変換し、読み直して中身を確認する。
 *
 * <p>761と762のペイロード差（minecraft-data protocol.json で確認）：構造差は無し。
 * Chunk Data／Update Light の trustEdges、Multi Block Change の
 * suppressLightUpdates、Open Sign／Update Sign の isFrontText は両方に同じ
 * 位置にあるため、そのまま写す。パケットIDは762→761で132件変更。
 * NBT は両方とも名前付きのためそのまま写す。エンティティメタデータの
 * variant 系は 761・762 で形式が同一のため落とさない（765→763 では落としている）。
 * スロットは両方とも bool＋アイテムID＋個数＋名前付き NBT。
 * 762にしか無いもの：ブロック状態493件・アイテム42件・エンティティ5種・
 * 音62件・パーティクル3種（cherry_leaves 系）・ブロックエンティティ2種は
 * 代替（oak／ブタ／white_ash 等）へ写すか、ブロックエンティティは落とす。
 */
class ChunkTranslator761Test {

    // ---- ヘルパー ----

    /** 高さマップ（名前付き NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** スロット1個（761／762形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** 間接パレットのセクション1個（ブロック＋バイオーム）。761／762形式（Data Array に長さ varint あり）。 */
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

    /** チャンク Data ペイロード（762形式）。trustEdges は引数通り。 */
    private static byte[] chunkDataPayload762(int chunkX, int chunkZ,
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
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ
        return payload.bytes();
    }

    // ---- Chunk Data ----

    @Test
    void chunkData762To761RemapsBlockStatesAndDropsBlockEntity() {
        byte[] translated = ChunkTranslator.translateChunkData762To761(
                chunkDataPayload762(3, -2,
                        new int[]{0, 1, 236, 373}, new int[]{38, 31},
                        new int[][]{{0x11, 64, 1}, {0x22, 65, 38}}, true));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        // oak_leaves 236→217、cherry_leaves 373→oak_leaves 241。
        assertArrayEquals(new int[]{0, 1, 217, 241}, readBlockPalette(sections));
        // バイオームは 761・762 ともに 63種で同一のため変わらない。
        assertArrayEquals(new int[]{38, 31}, readBiomePalette(sections));
        // ブロックエンティティはチェスト（762 type 1→761 type 1）だけ残り、
        // suspicious_sand（762 type 38）は 761 に無いため落とされる。
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
    void chunkData762To761TrustEdgesFalsePassesThrough() {
        byte[] translated = ChunkTranslator.translateChunkData762To761(
                chunkDataPayload762(0, 0,
                        new int[]{0}, new int[]{38},
                        new int[][]{}, false));
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        reader.readByteArray();
        assertEquals(0, reader.readVarInt());
        // trustEdges（false）もそのまま写る（763→762 では true を挿入していた）。
        assertEquals(false, reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData761To762RemapsBack() {
        byte[] translated = ChunkTranslator.translateChunkData761To762(
                chunkDataPayload761(3, -2,
                        new int[]{0, 1, 217, 241}, new int[]{38, 31},
                        new int[][]{{0x11, 64, 1}}, true));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        // oak_leaves 217→236。241 は 761 の oak_leaves 状態のため 762 の
        // oak_leaves 状態 260 へ戻る（cherry_leaves 373 には戻らない。多対1）。
        assertArrayEquals(new int[]{0, 1, 236, 260}, readBlockPalette(sections));
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
        assertArrayEquals(broken, ChunkTranslator.translateChunkData762To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateChunkData761To762(broken));
    }

    // ---- Block Change / Multi Block Change / Block Entity Data ----

    @Test
    void blockChange() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(23718); // decorated_pot（762）
        byte[] translated = ChunkTranslator.translateBlockChange762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(8337, reader.readVarInt()); // flower_pot（761）
        assertEquals(0, reader.remaining());

        Writer payload2 = new Writer();
        payload2.writePositionRaw(0x123456789AL);
        payload2.writeVarInt(236); // oak_leaves（762）
        byte[] translated2 = ChunkTranslator.translateBlockChange762To761(payload2.bytes());
        Reader reader2 = new Reader(translated2);
        assertEquals(0x123456789AL, reader2.readPositionRaw());
        assertEquals(217, reader2.readVarInt()); // oak_leaves（761）
        assertEquals(0, reader2.remaining());

        // 逆向き：761 の flower_pot 状態は 762 の flower_pot 状態へ（decorated_pot には戻らない）。
        Writer payload761 = new Writer();
        payload761.writePositionRaw(0x123456789AL);
        payload761.writeVarInt(8337);
        byte[] back = ChunkTranslator.translateBlockChange761To762(payload761.bytes());
        Reader reader761 = new Reader(back);
        assertEquals(0x123456789AL, reader761.readPositionRaw());
        assertEquals(8563, reader761.readVarInt()); // flower_pot（762）
        assertEquals(0, reader761.remaining());
    }

    @Test
    void multiBlockChangePassesThroughSuppressLightUpdates() {
        Writer payload = new Writer();
        payload.writeLong(0x1122334455667788L);
        payload.writeBoolean(true); // suppressLightUpdates（761・762 ともにある）
        payload.writeVarInt(2);
        payload.writeVarInt((23718 << 12) | 0x123); // decorated_pot
        payload.writeVarInt((236 << 12) | 0x456); // oak_leaves
        byte[] translated = ChunkTranslator.translateMultiBlockChange762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x1122334455667788L, reader.readLong());
        assertEquals(true, reader.readBoolean()); // そのまま写る
        assertEquals(2, reader.readVarInt());
        assertEquals((8337 << 12) | 0x123, reader.readVarInt());
        assertEquals((217 << 12) | 0x456, reader.readVarInt());
        assertEquals(0, reader.remaining());

        Writer payload761 = new Writer();
        payload761.writeLong(0x1122334455667788L);
        payload761.writeBoolean(false);
        payload761.writeVarInt(1);
        payload761.writeVarInt((8337 << 12) | 0x123);
        byte[] back = ChunkTranslator.translateMultiBlockChange761To762(payload761.bytes());
        Reader reader761 = new Reader(back);
        assertEquals(0x1122334455667788L, reader761.readLong());
        assertEquals(false, reader761.readBoolean());
        assertEquals(1, reader761.readVarInt());
        assertEquals((8563 << 12) | 0x123, reader761.readVarInt());
        assertEquals(0, reader761.remaining());
    }

    @Test
    void blockEntityData() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(35); // sculk_catalyst（762）
        payload.writeByte(0); // NBT 無し
        byte[] translated = ChunkTranslator.translateBlockEntityData762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(35, reader.readVarInt()); // sculk_catalyst（761、同一）
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());

        // suspicious_sand（762=38）と decorated_pot（762=39）は 761 に無いため落とす。
        Writer suspicious = new Writer();
        suspicious.writePositionRaw(0x123456789AL);
        suspicious.writeVarInt(38);
        suspicious.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData762To761(suspicious.bytes()));

        Writer pot = new Writer();
        pot.writePositionRaw(0x123456789AL);
        pot.writeVarInt(39);
        pot.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData762To761(pot.bytes()));

        // 範囲外の種別も null（パケットごと落とす）。
        Writer unknown = new Writer();
        unknown.writePositionRaw(0x123456789AL);
        unknown.writeVarInt(99999);
        unknown.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData762To761(unknown.bytes()));

        // 逆向き：sculk_catalyst（761=35）は 762 でも 35。
        Writer payload761 = new Writer();
        payload761.writePositionRaw(0x123456789AL);
        payload761.writeVarInt(35);
        payload761.writeByte(0);
        byte[] back = ChunkTranslator.translateBlockEntityData761To762(payload761.bytes());
        Reader reader761 = new Reader(back);
        assertEquals(0x123456789AL, reader761.readPositionRaw());
        assertEquals(35, reader761.readVarInt());
        assertEquals(0, reader761.readByte());
        assertEquals(0, reader761.remaining());
    }

    // ---- スロット・ウィンドウ ----

    @Test
    void setSlot() {
        Writer payload = new Writer();
        payload.writeByte(0); // windowId
        payload.writeVarInt(0); // stateId
        payload.writeShort(3); // slot
        writeSlot(payload, 847, 1); // cherry_sign（762）
        byte[] translated = ChunkTranslator.translateSetSlot762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{821, 1}, readSlotHead(reader)); // oak_sign（761）
        assertEquals(0, reader.remaining());

        // 逆向き：761 の oak_sign は 762 の oak_sign へ（cherry_sign には戻らない）。
        Writer payload761 = new Writer();
        payload761.writeByte(0);
        payload761.writeVarInt(0);
        payload761.writeShort(3);
        writeSlot(payload761, 821, 1);
        byte[] back = ChunkTranslator.translateSetSlot761To762(payload761.bytes());
        Reader reader761 = new Reader(back);
        assertEquals(0, reader761.readByte());
        assertEquals(0, reader761.readVarInt());
        assertEquals(3, reader761.readShort());
        assertArrayEquals(new int[]{842, 1}, readSlotHead(reader761)); // oak_sign（762）
        assertEquals(0, reader761.remaining());

        // brush（762=1211）は stick（761=782）へ。
        Writer brush = new Writer();
        brush.writeByte(0);
        brush.writeVarInt(0);
        brush.writeShort(0);
        writeSlot(brush, 1211, 1);
        byte[] brushOut = ChunkTranslator.translateSetSlot762To761(brush.bytes());
        Reader brushReader = new Reader(brushOut);
        brushReader.readByte();
        brushReader.readVarInt();
        brushReader.readShort();
        assertArrayEquals(new int[]{782, 1}, readSlotHead(brushReader));
    }

    @Test
    void windowItems() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(2); // windowId
        payload.writeVarInt(5); // stateId
        payload.writeVarInt(2); // count
        writeSlot(payload, 847, 1); // cherry_sign
        writeSlot(payload, 1211, 2); // brush
        writeSlot(payload, 1016, 1); // sniffer_spawn_egg（カーソル）
        byte[] translated = ChunkTranslator.translateWindowItems762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertArrayEquals(new int[]{821, 1}, readSlotHead(reader)); // oak_sign
        assertArrayEquals(new int[]{782, 2}, readSlotHead(reader)); // stick
        assertArrayEquals(new int[]{1001, 1}, readSlotHead(reader)); // turtle_spawn_egg
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCooldown() {
        Writer payload = new Writer();
        payload.writeVarInt(847); // cherry_sign（762）
        payload.writeVarInt(20); // ticks
        byte[] translated = ChunkTranslator.translateSetCooldown762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(821, reader.readVarInt()); // oak_sign（761）
        assertEquals(20, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityEquipment() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeUnsignedByte(0); // slot 0（最後）
        writeSlot(payload, 847, 1); // cherry_sign
        byte[] translated = ChunkTranslator.translateEntityEquipment762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertArrayEquals(new int[]{821, 1}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeList() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(1); // count
        writeSlot(payload, 847, 1); // cherry_sign
        writeSlot(payload, 1211, 1); // brush
        writeSlot(payload, 1016, 1); // sniffer_spawn_egg
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
        byte[] translated = ChunkTranslator.translateTradeList762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertArrayEquals(new int[]{821, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{782, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{1001, 1}, readSlotHead(reader));
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
        // 762 の smithing（21、新3スロット）は 761 の smithing（20、旧2スロット）へ。
        Writer payload = new Writer();
        payload.writeVarInt(1); // windowId
        payload.writeVarInt(21); // smithing（762）
        payload.writeString("{\"text\":\"Smithing\"}");
        byte[] translated = ChunkTranslator.translateOpenWindow762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readVarInt());
        assertEquals(20, reader.readVarInt()); // smithing（761）
        assertEquals("{\"text\":\"Smithing\"}", reader.readString());
        assertEquals(0, reader.remaining());

        // 762 の legacy_smithing（20）も 761 の smithing（20）へ。
        Writer legacy = new Writer();
        legacy.writeVarInt(1);
        legacy.writeVarInt(20);
        legacy.writeString("{\"text\":\"Smithing\"}");
        byte[] legacyOut = ChunkTranslator.translateOpenWindow762To761(legacy.bytes());
        Reader legacyReader = new Reader(legacyOut);
        assertEquals(1, legacyReader.readVarInt());
        assertEquals(20, legacyReader.readVarInt());
        assertEquals("{\"text\":\"Smithing\"}", legacyReader.readString());

        // 逆向き：761 の smithing（20）は 762 の legacy_smithing（20）へ。
        Writer payload761 = new Writer();
        payload761.writeVarInt(1);
        payload761.writeVarInt(20); // smithing（761）
        payload761.writeString("{\"text\":\"Smithing\"}");
        byte[] back = ChunkTranslator.translateOpenWindow761To762(payload761.bytes());
        Reader reader761 = new Reader(back);
        assertEquals(1, reader761.readVarInt());
        assertEquals(20, reader761.readVarInt()); // legacy_smithing（762）
        assertEquals("{\"text\":\"Smithing\"}", reader761.readString());

        // 逆向き：761 の smoker（21）は 762 の smoker（22）へ。
        Writer smoker = new Writer();
        smoker.writeVarInt(1);
        smoker.writeVarInt(21);
        smoker.writeString("{\"text\":\"Smoker\"}");
        byte[] smokerOut = ChunkTranslator.translateOpenWindow761To762(smoker.bytes());
        Reader smokerReader = new Reader(smokerOut);
        assertEquals(1, smokerReader.readVarInt());
        assertEquals(22, smokerReader.readVarInt());
        assertEquals("{\"text\":\"Smoker\"}", smokerReader.readString());
    }

    @Test
    void blockAction() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeUnsignedByte(1);
        payload.writeUnsignedByte(2);
        payload.writeVarInt(50); // cherry_log（762）
        byte[] translated = ChunkTranslator.translateBlockAction762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(42, reader.readVarInt()); // oak_log（761）
        assertEquals(0, reader.remaining());

        // decorated_pot（762=997）は flower_pot（761=339）へ。
        Writer pot = new Writer();
        pot.writePositionRaw(0x123456789AL);
        pot.writeUnsignedByte(1);
        pot.writeUnsignedByte(2);
        pot.writeVarInt(997);
        byte[] potOut = ChunkTranslator.translateBlockAction762To761(pot.bytes());
        Reader potReader = new Reader(potOut);
        potReader.readPositionRaw();
        potReader.readUnsignedByte();
        potReader.readUnsignedByte();
        assertEquals(339, potReader.readVarInt());

        // 逆向き：761 の oak_log（42）は 762 の oak_log（45）へ。
        Writer payload761 = new Writer();
        payload761.writePositionRaw(0x123456789AL);
        payload761.writeUnsignedByte(1);
        payload761.writeUnsignedByte(2);
        payload761.writeVarInt(42);
        byte[] back = ChunkTranslator.translateBlockAction761To762(payload761.bytes());
        Reader reader761 = new Reader(back);
        assertEquals(0x123456789AL, reader761.readPositionRaw());
        assertEquals(1, reader761.readUnsignedByte());
        assertEquals(2, reader761.readUnsignedByte());
        assertEquals(45, reader761.readVarInt());
        assertEquals(0, reader761.remaining());
    }

    // ---- エンティティ・音・パーティクル・エフェクト ----

    @Test
    void spawnEntity() {
        // sniffer（762=90）は 761 ではブタ（68）になる。
        assertArrayEquals(new int[]{68, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity762To761(spawnEntityPacket(90, 0))));
        // falling_block の objectData はブロック状態IDなので一緒に変換する。
        assertArrayEquals(new int[]{30, 217},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity762To761(
                                spawnEntityPacket(36, 236))));
        // 逆向き：ブタ（761=68）は 762 のブタ（72）へ。
        assertArrayEquals(new int[]{72, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity761To762(spawnEntityPacket(68, 0))));
        assertArrayEquals(new int[]{36, 236},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity761To762(
                                spawnEntityPacket(30, 217))));
        // ゾンビ（762=118）は 761 では 113。
        assertArrayEquals(new int[]{113, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity762To761(spawnEntityPacket(118, 0))));
    }

    @Test
    void entityMetadataSlotAndBlockStateAndVariantPassThrough() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(7); // item_stack（スロット）
        writeSlot(writer, 847, 1); // cherry_sign（762）
        writer.writeUnsignedByte(1); // index 1
        writer.writeVarInt(14); // block_state
        writer.writeVarInt(23718); // decorated_pot（762）
        writer.writeUnsignedByte(2); // index 2
        writer.writeVarInt(22); // frog_variant（761・762とも同じ番号）
        writer.writeVarInt(3);
        writer.writeUnsignedByte(3); // index 3
        writer.writeVarInt(24); // painting_variant
        writer.writeVarInt(5);
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata762To761(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        assertArrayEquals(new int[]{821, 1}, readSlotHead(reader)); // oak_sign（761）
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(14, reader.readVarInt());
        assertEquals(8337, reader.readVarInt()); // flower_pot（761）
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
        writer.writeVarInt(33); // sculk_charge（762）
        writer.writeFloat(1.5f); // 回転
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata762To761(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(17, reader.readVarInt());
        assertEquals(30, reader.readVarInt()); // sculk_charge（761）
        assertEquals(1.5f, reader.readFloat());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void soundEffect() {
        // 数値 holder：762 の block.cherry_wood.break（233）は holder 234 として来る。
        Writer writer = new Writer();
        writer.writeVarInt(234); // holder = id + 1
        writer.writeVarInt(1); // source
        writer.writeInt(8);
        writer.writeInt(64);
        writer.writeInt(8);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L);
        byte[] out = ChunkTranslator.translateSoundEffect762To761(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1350, reader.readVarInt()); // 761 の block.wood.break（1349）+ 1
        assertEquals(1, reader.readVarInt());
        assertEquals(8, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(8, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());

        // 文字列 holder：music.overworld.cherry_grove は jungle_and_forest に代替。
        Writer named = new Writer();
        named.writeVarInt(0); // holder = 文字列
        named.writeString("music.overworld.cherry_grove");
        named.writeBoolean(false); // hasRange なし
        named.writeVarInt(1);
        named.writeInt(8);
        named.writeInt(64);
        named.writeInt(8);
        named.writeFloat(1.0f);
        named.writeFloat(1.0f);
        named.writeLong(12345L);
        byte[] out2 = ChunkTranslator.translateSoundEffect762To761(named.bytes());
        Reader reader2 = new Reader(out2);
        assertEquals(0, reader2.readVarInt());
        assertEquals("music.overworld.jungle_and_forest", reader2.readString());
        assertEquals(false, reader2.readBoolean());

        // 逆向き：761 の block.wood.break（1349）は 762 の block.wood.break（1415）へ。
        Writer back = new Writer();
        back.writeVarInt(1350); // holder = id + 1
        back.writeVarInt(1);
        back.writeInt(8);
        back.writeInt(64);
        back.writeInt(8);
        back.writeFloat(1.0f);
        back.writeFloat(1.0f);
        back.writeLong(12345L);
        byte[] out3 = ChunkTranslator.translateSoundEffect761To762(back.bytes());
        Reader reader3 = new Reader(out3);
        assertEquals(1416, reader3.readVarInt());
        assertEquals(1, reader3.readVarInt());
    }

    @Test
    void entitySoundEffect() {
        Writer writer = new Writer();
        writer.writeVarInt(234); // holder = id + 1（block.cherry_wood.break 762）
        writer.writeVarInt(1); // source
        writer.writeVarInt(7); // entityId
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L);
        byte[] out = ChunkTranslator.translateEntitySoundEffect762To761(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1350, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void stopSound() {
        Writer writer = new Writer();
        writer.writeByte(0x02); // 名前だけ
        writer.writeString("music.overworld.cherry_grove");
        byte[] out = ChunkTranslator.translateStopSound762To761(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x02, reader.readByte());
        assertEquals("music.overworld.jungle_and_forest", reader.readString());
        assertEquals(0, reader.remaining());

        // 逆向き：761 の名前は 762 にも同じ名前であるため変わらない。
        Writer writer761 = new Writer();
        writer761.writeByte(0x02);
        writer761.writeString("music.overworld.jungle_and_forest");
        byte[] back = ChunkTranslator.translateStopSound761To762(writer761.bytes());
        Reader reader761 = new Reader(back);
        assertEquals(0x02, reader761.readByte());
        assertEquals("music.overworld.jungle_and_forest", reader761.readString());
        assertEquals(0, reader761.remaining());
    }

    @Test
    void worldParticles() {
        // dripping_cherry_leaves：762=29 → 761 は white_ash（79）。データ無し。
        Writer cherry = new Writer();
        cherry.writeVarInt(29);
        cherry.writeBoolean(false);
        cherry.writeDouble(0.0);
        cherry.writeDouble(0.0);
        cherry.writeDouble(0.0);
        cherry.writeFloat(0.0f);
        cherry.writeFloat(0.0f);
        cherry.writeFloat(0.0f);
        cherry.writeFloat(0.0f);
        cherry.writeInt(1);
        byte[] cherryOut = ChunkTranslator.translateWorldParticles762To761(cherry.bytes());
        Reader cherryReader = new Reader(cherryOut);
        assertEquals(79, cherryReader.readVarInt()); // white_ash（761）
        assertEquals(false, cherryReader.readBoolean());

        // sculk_charge：762=33 → 761=30。回転 float 付き。
        Writer sculk = new Writer();
        sculk.writeVarInt(33);
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
        byte[] out = ChunkTranslator.translateWorldParticles762To761(sculk.bytes());
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

        // block：ブロック状態IDを変換する。
        Writer block = new Writer();
        block.writeVarInt(2);
        block.writeVarInt(236); // oak_leaves（762）
        block.writeBoolean(false);
        block.writeDouble(0.0);
        block.writeDouble(0.0);
        block.writeDouble(0.0);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeInt(1);
        byte[] out2 = ChunkTranslator.translateWorldParticles762To761(block.bytes());
        Reader reader2 = new Reader(out2);
        assertEquals(2, reader2.readVarInt());
        assertEquals(217, reader2.readVarInt()); // oak_leaves（761）

        // item：762=42 → 761=39。スロットを変換する。
        Writer item = new Writer();
        item.writeVarInt(42);
        writeSlot(item, 847, 1); // cherry_sign
        item.writeBoolean(false);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeInt(1);
        byte[] out3 = ChunkTranslator.translateWorldParticles762To761(item.bytes());
        Reader reader3 = new Reader(out3);
        assertEquals(39, reader3.readVarInt());
        assertArrayEquals(new int[]{821, 1}, readSlotHead(reader3));

        // 逆向き：shriek 761=92 → 762=95。遅延 tick 数付き。
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
        byte[] out4 = ChunkTranslator.translateWorldParticles761To762(shriek.bytes());
        Reader reader4 = new Reader(out4);
        assertEquals(95, reader4.readVarInt());
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
        byte[] out = ChunkTranslator.translateEntityEffect762To761(writer.bytes());
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
        byte[] back = ChunkTranslator.translateEntityEffect761To762(writer.bytes());
        assertArrayEquals(out, back);
    }

    // ---- サーバー行き ----

    @Test
    void windowClick761To762() {
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
        byte[] translated = ChunkTranslator.translateWindowClick761To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{842, 1}, readSlotHead(reader)); // oak_sign（762）
        assertArrayEquals(new int[]{1025, 1}, readSlotHead(reader)); // turtle_spawn_egg（762）
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCreativeSlot761To762() {
        Writer payload = new Writer();
        payload.writeShort(5);
        writeSlot(payload, 821, 1); // oak_sign（761）
        byte[] translated = ChunkTranslator.translateSetCreativeSlot761To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(5, reader.readShort());
        assertArrayEquals(new int[]{842, 1}, readSlotHead(reader)); // oak_sign（762）
        assertEquals(0, reader.remaining());
    }

    @Test
    void brokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02}; // 短すぎて読めない
        assertArrayEquals(broken, ChunkTranslator.translateBlockChange762To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateBlockChange761To762(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSetSlot762To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSetSlot761To762(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSpawnEntity762To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSpawnEntity761To762(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSoundEffect762To761(broken));
        assertArrayEquals(broken, ChunkTranslator.translateWorldParticles762To761(broken));
    }

    // ---- Named Entity Spawn（player だけ 762 の Spawn Entity と 761 の Named Entity Spawn で違う） ----

    @Test
    void readSpawnEntityType762() {
        assertEquals(122, ChunkTranslator.readSpawnEntityType762(spawnEntityPacket(122, 0)));
        assertEquals(118, ChunkTranslator.readSpawnEntityType762(spawnEntityPacket(118, 0)));
        assertEquals(-1, ChunkTranslator.readSpawnEntityType762(new byte[]{0x00, 0x01, 0x02}));
    }

    @Test
    void spawnEntityPlayerBecomesNamedEntitySpawn() {
        // 762 の Spawn Entity（player=122）は 761 の Named Entity Spawn になる。
        // pitch・yaw の順番が入れ替わる（Spawn Entity は pitch・yaw・headPitch、
        // Named Entity Spawn は yaw・pitch）。
        Writer spawn = new Writer();
        spawn.writeVarInt(7); // entityId
        spawn.writeBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        spawn.writeVarInt(122); // player（762）
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
        byte[] named = ChunkTranslator.spawnEntity762ToNamedEntitySpawn761(spawn.bytes());
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
        assertNull(ChunkTranslator.spawnEntity762ToNamedEntitySpawn761(spawnEntityPacket(118, 0)));
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
        byte[] spawn = ChunkTranslator.namedEntitySpawn761ToSpawnEntity762(named.bytes());
        Reader reader = new Reader(spawn);
        assertEquals(7, reader.readVarInt());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                reader.readBytes(16));
        assertEquals(122, reader.readVarInt()); // player（762）
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
