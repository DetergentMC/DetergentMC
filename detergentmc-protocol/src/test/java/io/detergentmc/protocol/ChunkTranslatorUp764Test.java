package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の逆向き（764→765）変換テスト。
 * 764形式のペイロードを合成して765形式に変換し、読み直して中身を確認する。
 * 下向き（765→764）と対称になること、往復で764に存在するIDが
 * 元に戻ることも確認する。
 */
class ChunkTranslatorUp764Test {

    // ---- ヘルパー ----

    /** 高さマップ（名前なし NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** スロット（764形式：bool＋アイテムID＋個数＋名前なし NBT）。 */
    private static void writeSlot764(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        if (nbt == null) {
            writer.writeByte(0);
        } else {
            writer.writeAnonymousNbt(nbt);
        }
    }

    /** スロット（765形式：bool＋アイテムID＋個数＋名前なし NBT）。 */
    private static void writeSlot765(Writer writer, int itemId, int count, Tag nbt) {
        writeSlot764(writer, itemId, count, nbt);
    }

    /** 間接パレットのセクション1個（ブロック＋バイオーム）。 */
    private static byte[] indirectSection(int[] blockPalette, int biomeId) {
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
        writer.writeUnsignedByte(0); // バイオームはシングル
        writer.writeVarInt(biomeId);
        writer.writeVarInt(0); // Data Array 長 0
        return writer.bytes();
    }

    /** シングルパレットのセクション1個（ブロック＋バイオーム）。 */
    private static byte[] singleSection(int blockState, int biomeId) {
        Writer writer = new Writer();
        writer.writeShort(4096); // ブロック数
        writer.writeUnsignedByte(0); // ブロックはシングル
        writer.writeVarInt(blockState);
        writer.writeVarInt(0); // Data Array 長 0
        writer.writeUnsignedByte(0); // バイオームはシングル
        writer.writeVarInt(biomeId);
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 1 long 分の直接パレットを詰める（テスト用）。 */
    private static long packDirect(int[] entries, int bpe) {
        long value = 0;
        for (int i = 0; i < entries.length; i++) {
            value |= ((long) entries[i] & ((1L << bpe) - 1)) << (i * bpe);
        }
        return value;
    }

    /** 1 long 分の直接パレットをほどく（テスト用）。 */
    private static int[] unpackDirect(long value, int bpe, int count) {
        int[] entries = new int[count];
        long mask = (1L << bpe) - 1;
        for (int i = 0; i < count; i++) {
            entries[i] = (int) ((value >>> (i * bpe)) & mask);
        }
        return entries;
    }

    // ---- Chunk Data ----

    @Test
    void chunkDataRemapsPaletteUpAndKeepsAnonymousNbt() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1, 5850, 5849}, 5));
        payload.writeVarInt(1);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(1); // chest
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("id", new Tag.StringTag("minecraft:chest"))));
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ

        byte[] translated = ChunkTranslator.translateChunkData764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        Nbt.ReadResult height = reader.readAnonymousNbt();
        assertTrue(height.tag() instanceof Tag.CompoundTag);
        int dataLength = reader.readVarInt();
        byte[] sections = reader.readBytes(dataLength);
        Reader section = new Reader(sections);
        assertEquals(1, section.readShort());
        assertEquals(4, section.readUnsignedByte());
        assertEquals(4, section.readVarInt());
        assertEquals(0, section.readVarInt());
        assertEquals(1, section.readVarInt());
        assertEquals(5849, section.readVarInt()); // 764=5850 → 765=5849
        assertEquals(6811, section.readVarInt()); // 764=5849 → 765=6811（pumpkin）
        assertEquals(1, reader.readVarInt()); // ブロックエンティティ1件
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt()); // chest（そのまま）
        assertEquals("minecraft:chest",
                ((Tag.CompoundTag) reader.readAnonymousNbt().tag()).getString("id").orElseThrow());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkDataSinglePaletteUp() {
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(singleSection(5850, 5));
        payload.writeVarInt(0);

        byte[] translated = ChunkTranslator.translateChunkData764To765(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        int dataLength = reader.readVarInt();
        Reader section = new Reader(reader.readBytes(dataLength));
        assertEquals(4096, section.readShort());
        assertEquals(0, section.readUnsignedByte());
        assertEquals(5849, section.readVarInt()); // 764=5850 → 765=5849
    }

    @Test
    void chunkDataDirectPaletteUp() {
        // BPE 15 の直接パレット：先頭の long に 5850／5849 を詰める。
        Writer sections = new Writer();
        sections.writeShort(4096);
        sections.writeUnsignedByte(15);
        sections.writeVarInt(960); // 4096エントリ×15bit＝960 long（2026-11-14に修正）
        sections.writeLong(packDirect(new int[]{5850, 5849, 1, 0}, 15));
        for (int i = 1; i < 960; i++) {
            sections.writeLong(0L);
        }
        sections.writeUnsignedByte(0);
        sections.writeVarInt(5);
        sections.writeVarInt(0);

        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(sections.bytes());
        payload.writeVarInt(0);

        byte[] translated = ChunkTranslator.translateChunkData764To765(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        int dataLength = reader.readVarInt();
        Reader section = new Reader(reader.readBytes(dataLength));
        assertEquals(4096, section.readShort());
        assertEquals(15, section.readUnsignedByte());
        assertEquals(960, section.readVarInt());
        int[] entries = unpackDirect(section.readLong(), 15, 4);
        assertArrayEquals(new int[]{5849, 6811, 1, 0}, entries);
    }

    @Test
    void chunkDataRoundTrip() {
        // 764→765→764 の往復で、764のパレットが元に戻る。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1, 5850, 5849, 2005}, 5));
        payload.writeVarInt(0);
        byte[] up = ChunkTranslator.translateChunkData764To765(payload.bytes());
        byte[] down = ChunkTranslator.translateChunkData765To764(up);
        assertArrayEquals(payload.bytes(), down);
    }

    // ---- Block Change / Block Entity ----

    @Test
    void blockChangeUp() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789ABCDEFL);
        payload.writeVarInt(5850);
        byte[] translated = ChunkTranslator.translateBlockChange764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789ABCDEFL, reader.readPositionRaw());
        assertEquals(5849, reader.readVarInt());
    }

    @Test
    void multiBlockChangeUp() {
        Writer payload = new Writer();
        payload.writeLong(0x1122334455667788L);
        payload.writeVarInt(2);
        payload.writeVarInt((5850 << 12) | 0x123);
        payload.writeVarInt((5849 << 12) | 0x456);
        byte[] translated = ChunkTranslator.translateMultiBlockChange764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x1122334455667788L, reader.readLong());
        assertEquals(2, reader.readVarInt());
        assertEquals((5849 << 12) | 0x123, reader.readVarInt());
        assertEquals((6811 << 12) | 0x456, reader.readVarInt());
    }

    @Test
    void blockEntityDataUp() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x10L);
        payload.writeVarInt(40); // decorated_pot
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("id", new Tag.StringTag("minecraft:decorated_pot"))));
        byte[] translated = ChunkTranslator.translateBlockEntityData764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x10L, reader.readPositionRaw());
        assertEquals(40, reader.readVarInt());
        assertEquals("minecraft:decorated_pot",
                ((Tag.CompoundTag) reader.readAnonymousNbt().tag()).getString("id").orElseThrow());
    }

    @Test
    void blockEntityDataUpDropsUnknownType() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x10L);
        payload.writeVarInt(41); // 764に無い種別
        payload.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData764To765(payload.bytes()));
    }

    // ---- スロット ----

    @Test
    void setSlotUp() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(5);
        payload.writeShort(3);
        writeSlot764(payload, 173, 2, null); // grass → short_grass
        byte[] translated = ChunkTranslator.translateSetSlot764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(194, reader.readVarInt()); // short_grass
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readByte()); // NBT 無し
    }

    @Test
    void windowItemsUp() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(1);
        payload.writeVarInt(2);
        payload.writeVarInt(1);
        writeSlot764(payload, 318, 1, null); // stone_bricks → 765=339
        writeSlot764(payload, 0, 0, null); // 空スロット（present=false は別途）
        byte[] translated = ChunkTranslator.translateWindowItems764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(339, reader.readVarInt());
        reader.readByte();
        reader.readByte();
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void setCooldownUp() {
        Writer payload = new Writer();
        payload.writeVarInt(173);
        payload.writeVarInt(20);
        byte[] translated = ChunkTranslator.translateSetCooldown764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(194, reader.readVarInt());
        assertEquals(20, reader.readVarInt());
    }

    @Test
    void openWindowUp() {
        Writer payload = new Writer();
        payload.writeVarInt(3);
        payload.writeVarInt(11); // crafting → 765=12
        payload.writeString("{\"text\":\"Chest\"}");
        byte[] translated = ChunkTranslator.translateOpenWindow764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readVarInt());
        assertEquals(12, reader.readVarInt());
        assertEquals("{\"text\":\"Chest\"}",
                Nbt.toJsonString(reader.readAnonymousNbt().tag()));
    }

    @Test
    void blockActionUp() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x20L);
        payload.writeUnsignedByte(1);
        payload.writeUnsignedByte(2);
        payload.writeVarInt(182);
        byte[] translated = ChunkTranslator.translateBlockAction764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x20L, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(182, reader.readVarInt());
    }

    @Test
    void tradeListUp() {
        Writer payload = new Writer();
        payload.writeVarInt(7);
        payload.writeVarInt(1);
        writeSlot764(payload, 173, 1, null);
        writeSlot764(payload, 318, 1, null);
        writeSlot764(payload, 1, 1, null);
        payload.writeBoolean(true);
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
        byte[] translated = ChunkTranslator.translateTradeList764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(194, reader.readVarInt());
        reader.readByte();
        reader.readByte();
        assertTrue(reader.readBoolean());
        assertEquals(339, reader.readVarInt());
    }

    // ---- エンティティ・音・パーティクル ----

    @Test
    void spawnEntityUp() {
        Writer payload = new Writer();
        payload.writeVarInt(10);
        payload.writeBytes(new byte[16]);
        payload.writeVarInt(72); // pig（764）→ 765=73
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeShort(0);
        payload.writeShort(0);
        payload.writeShort(0);
        byte[] translated = ChunkTranslator.translateSpawnEntity764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(10, reader.readVarInt());
        reader.readBytes(16);
        assertEquals(73, reader.readVarInt());
    }

    @Test
    void spawnFallingBlockUp() {
        Writer payload = new Writer();
        payload.writeVarInt(11);
        payload.writeBytes(new byte[16]);
        payload.writeVarInt(36); // falling_block（764）→ 765=37
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeVarInt(5850); // objectData：ブロック状態
        payload.writeShort(0);
        payload.writeShort(0);
        payload.writeShort(0);
        byte[] translated = ChunkTranslator.translateSpawnEntity764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(11, reader.readVarInt());
        reader.readBytes(16);
        assertEquals(37, reader.readVarInt());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte();
        reader.readByte();
        reader.readByte();
        assertEquals(5849, reader.readVarInt()); // 764=5850 → 765=5849
    }

    @Test
    void soundEffectUp() {
        Writer payload = new Writer();
        payload.writeVarInt(1271); // 764 音 1270（stone.break）＋1
        payload.writeVarInt(1); // category
        payload.writeInt(8);
        payload.writeInt(64);
        payload.writeInt(-8);
        payload.writeFloat(1.0f);
        payload.writeFloat(1.0f);
        payload.writeLong(123L);
        byte[] translated = ChunkTranslator.translateSoundEffect764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1313, reader.readVarInt()); // 765 音 1312＋1
        assertEquals(1, reader.readVarInt());
    }

    @Test
    void soundEffectUpInlineName() {
        Writer payload = new Writer();
        payload.writeVarInt(0); // 直書き
        payload.writeString("minecraft:block.stone.break");
        payload.writeBoolean(false);
        payload.writeVarInt(1);
        payload.writeInt(8);
        payload.writeInt(64);
        payload.writeInt(-8);
        payload.writeFloat(1.0f);
        payload.writeFloat(1.0f);
        payload.writeLong(123L);
        byte[] translated = ChunkTranslator.translateSoundEffect764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readVarInt());
        assertEquals("minecraft:block.stone.break", reader.readString());
    }

    @Test
    void worldParticlesUp() {
        // falling_dust（764=25→765=27）、ブロック状態 5850→5849。
        Writer payload = new Writer();
        payload.writeVarInt(25);
        payload.writeVarInt(5850);
        payload.writeBoolean(true);
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeFloat(0.1f);
        payload.writeFloat(0.2f);
        payload.writeFloat(0.3f);
        payload.writeFloat(0.4f);
        payload.writeInt(5);
        byte[] translated = ChunkTranslator.translateWorldParticles764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(27, reader.readVarInt());
        assertEquals(5849, reader.readVarInt());
        assertTrue(reader.readBoolean());
    }

    @Test
    void worldParticlesUpItem() {
        // item 粒子（764=40→765=42）、スロットの grass→short_grass。
        Writer payload = new Writer();
        payload.writeVarInt(40);
        writeSlot764(payload, 173, 1, null);
        payload.writeBoolean(false);
        payload.writeDouble(0.0);
        payload.writeDouble(0.0);
        payload.writeDouble(0.0);
        payload.writeFloat(0.0f);
        payload.writeFloat(0.0f);
        payload.writeFloat(0.0f);
        payload.writeFloat(0.0f);
        payload.writeInt(1);
        byte[] translated = ChunkTranslator.translateWorldParticles764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(42, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(194, reader.readVarInt());
    }

    @Test
    void entityEffectUpIsIdentity() {
        Writer payload = new Writer();
        payload.writeVarInt(9);
        payload.writeVarInt(1); // Speed（0ベースのまま）
        payload.writeByte(2);
        payload.writeVarInt(100);
        payload.writeByte(0);
        payload.writeBoolean(false);
        byte[] translated = ChunkTranslator.translateEntityEffect764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(9, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readByte());
        assertEquals(100, reader.readVarInt());
    }

    @Test
    void entityMetadataUpTextAndSlot() {
        Writer payload = new Writer();
        payload.writeVarInt(5);
        // index 2：テキスト（type 5、JSON 文字列→NBT）
        payload.writeUnsignedByte(2);
        payload.writeVarInt(5);
        payload.writeString("{\"text\":\"Name\"}");
        // index 3：スロット（type 7）
        payload.writeUnsignedByte(3);
        payload.writeVarInt(7);
        writeSlot764(payload, 173, 1, null);
        // index 4：ブロック状態（type 14）
        payload.writeUnsignedByte(4);
        payload.writeVarInt(14);
        payload.writeVarInt(5850);
        payload.writeUnsignedByte(0xFF);
        byte[] translated = ChunkTranslator.translateEntityMetadata764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(5, reader.readVarInt());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals("{\"text\":\"Name\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(194, reader.readVarInt());
        reader.readByte();
        reader.readByte();
        assertEquals(4, reader.readUnsignedByte());
        assertEquals(14, reader.readVarInt());
        assertEquals(5849, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- サーバー行き（765→764） ----

    @Test
    void windowClickDown765To764() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(1);
        payload.writeVarInt(2);
        payload.writeShort(3);
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeVarInt(1);
        payload.writeShort(0);
        writeSlot765(payload, 194, 1, null); // short_grass → grass
        writeSlot765(payload, 339, 1, null); // stone_bricks（765=339→764=318）
        byte[] translated = ChunkTranslator.translateWindowClick765To764(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(173, reader.readVarInt()); // grass
        reader.readByte();
        reader.readByte();
        assertTrue(reader.readBoolean());
        assertEquals(318, reader.readVarInt()); // stone_bricks
    }

    @Test
    void setCreativeSlotDown765To764() {
        Writer payload = new Writer();
        payload.writeShort(5);
        writeSlot765(payload, 194, 3, null);
        byte[] translated = ChunkTranslator.translateSetCreativeSlot765To764(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(5, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(173, reader.readVarInt());
        assertEquals(3, reader.readByte());
    }
}
