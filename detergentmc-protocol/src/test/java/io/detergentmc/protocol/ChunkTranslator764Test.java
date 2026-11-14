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
 * {@link ChunkTranslator} の764（1.20.2）向け変換テスト。
 * 765形式のペイロードを合成して764形式に変換し、読み直して中身を確認する。
 * 763向けとの違い：NBT は名前なしのまま（名前付きに書き直さない）、
 * エフェクトIDはそのまま、メタデータの variant 系は落とさない。
 */
class ChunkTranslator764Test {

    // ---- ヘルパー ----

    /** 高さマップ（名前なし NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** スロット（765形式：bool＋アイテムID＋個数＋名前なし NBT）。 */
    private static void writeSlot765(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        if (nbt == null) {
            writer.writeByte(0);
        } else {
            writer.writeAnonymousNbt(nbt);
        }
    }

    /** スロット（764形式：bool＋アイテムID＋個数＋名前なし NBT）。 */
    private static void writeSlot764(Writer writer, int itemId, int count, Tag nbt) {
        writeSlot765(writer, itemId, count, nbt);
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

    /** BPE 15 の直接パレットの1 long 分を詰める（テスト用）。 */
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
    void chunkDataRemapsPaletteAndKeepsAnonymousNbt() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1, 26638, 21085}, 5));
        // ブロックエンティティ：チェスト（残る）と trial_spawner（落とす）。
        payload.writeVarInt(2);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(1); // chest
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("id", new Tag.StringTag("minecraft:chest"))));
        payload.writeUnsignedByte(0x22);
        payload.writeShort(65);
        payload.writeVarInt(42); // trial_spawner
        payload.writeByte(0); // NBT 無し
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ（そのまま写る想定）

        byte[] translated = ChunkTranslator.translateChunkData765To764(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        // 高さマップは名前なし NBT のまま（763向けと違い名前付きにしない）。
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
        assertEquals(2873, section.readVarInt()); // trial_spawner → spawner
        assertEquals(11225, section.readVarInt()); // tuff_slab → stone_slab
        assertEquals(256, section.readVarInt());
        for (int i = 0; i < 256; i++) {
            assertEquals(0L, section.readLong());
        }
        assertEquals(0, section.readUnsignedByte());
        assertEquals(5, section.readVarInt()); // バイオームはそのまま
        assertEquals(0, section.readVarInt());
        assertEquals(0, section.remaining());
        // ブロックエンティティはチェストだけ残る。NBT は名前なしのまま。
        assertEquals(1, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        Nbt.ReadResult beNbt = reader.readAnonymousNbt();
        assertEquals("minecraft:chest",
                ((Tag.CompoundTag) beNbt.tag()).value().get("id") instanceof Tag.StringTag s
                        ? s.value() : null);
        // 光データはそのまま。
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkDataSinglePalette() {
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        Writer section = new Writer();
        section.writeShort(0);
        section.writeUnsignedByte(0); // シングル
        section.writeVarInt(26638); // trial_spawner
        section.writeVarInt(0); // Data Array 長 0
        section.writeUnsignedByte(0);
        section.writeVarInt(1);
        section.writeVarInt(0);
        payload.writeByteArray(section.bytes());
        payload.writeVarInt(0); // ブロックエンティティ無し

        byte[] translated = ChunkTranslator.translateChunkData765To764(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        Reader s = new Reader(sections);
        assertEquals(0, s.readShort());
        assertEquals(0, s.readUnsignedByte());
        assertEquals(2873, s.readVarInt()); // trial_spawner → spawner
        assertEquals(0, s.readVarInt());
    }

    @Test
    void chunkDataDirectPalette() {
        // BPE 15 の直接パレット。BitStorage は long の境界をまたいで連続して詰めるため、
        // 4096エントリ×15bit＝960 long になる（2026-11-14に修正）。
        long[] longs = new long[960];
        longs[0] = packDirect(new int[]{26638, 1, 6811, 0}, 15);
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        Writer section = new Writer();
        section.writeShort(2);
        section.writeUnsignedByte(15); // 直接
        section.writeVarInt(960);
        for (long value : longs) {
            section.writeLong(value);
        }
        section.writeUnsignedByte(0);
        section.writeVarInt(1);
        section.writeVarInt(0);
        payload.writeByteArray(section.bytes());
        payload.writeVarInt(0);

        byte[] translated = ChunkTranslator.translateChunkData765To764(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        Reader s = new Reader(sections);
        assertEquals(2, s.readShort());
        assertEquals(15, s.readUnsignedByte());
        assertEquals(960, s.readVarInt());
        long first = s.readLong();
        int[] entries = unpackDirect(first, 15, 4);
        assertEquals(2873, entries[0]); // trial_spawner → spawner
        assertEquals(1, entries[1]); // stone は同じ
        assertEquals(5849, entries[2]); // pumpkin（765=6811 → 764=5849）
        assertEquals(0, entries[3]);
    }

    @Test
    void chunkDataBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02}; // 短すぎて読めない
        assertArrayEquals(broken, ChunkTranslator.translateChunkData765To764(broken));
    }

    // ---- Block Change / Multi Block Change / Block Entity Data ----

    @Test
    void blockChange() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(26638);
        byte[] translated = ChunkTranslator.translateBlockChange765To764(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(2873, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockChangePumpkinAndUnknown() {
        Writer payload = new Writer();
        payload.writePositionRaw(0L);
        payload.writeVarInt(6811); // pumpkin
        Reader reader = new Reader(ChunkTranslator.translateBlockChange765To764(payload.bytes()));
        reader.readPositionRaw();
        assertEquals(5849, reader.readVarInt());

        Writer unknown = new Writer();
        unknown.writePositionRaw(0L);
        unknown.writeVarInt(99999);
        reader = new Reader(ChunkTranslator.translateBlockChange765To764(unknown.bytes()));
        reader.readPositionRaw();
        assertEquals(1, reader.readVarInt()); // 範囲外は石
    }

    @Test
    void multiBlockChange() {
        Writer payload = new Writer();
        payload.writeLong(0x0102030405060708L);
        payload.writeVarInt(2);
        payload.writeVarInt((26638 << 12) | 0x123);
        payload.writeVarInt((1 << 12) | 0x456);
        Reader reader = new Reader(ChunkTranslator.translateMultiBlockChange765To764(payload.bytes()));
        assertEquals(0x0102030405060708L, reader.readLong());
        assertEquals(2, reader.readVarInt());
        assertEquals((2873 << 12) | 0x123, reader.readVarInt());
        assertEquals((1 << 12) | 0x456, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockEntityDataDropsNewType() {
        Writer payload = new Writer();
        payload.writePositionRaw(0L);
        payload.writeVarInt(42); // trial_spawner
        payload.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData765To764(payload.bytes()));

        Writer crafter = new Writer();
        crafter.writePositionRaw(0L);
        crafter.writeVarInt(41); // crafter
        crafter.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData765To764(crafter.bytes()));
    }

    @Test
    void blockEntityDataKeepsKnownTypeAnonymousNbt() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x77L);
        payload.writeVarInt(1); // chest
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("x", new Tag.IntTag(3))));
        byte[] translated = ChunkTranslator.translateBlockEntityData765To764(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x77L, reader.readPositionRaw());
        assertEquals(1, reader.readVarInt());
        // NBT は名前なしのまま。
        Nbt.ReadResult nbt = reader.readAnonymousNbt();
        assertEquals(3, ((Tag.IntTag) ((Tag.CompoundTag) nbt.tag()).value().get("x")).value());
        assertEquals(0, reader.remaining());
    }

    // ---- スロット ----

    @Test
    void setSlotRemapsItemAndKeepsAnonymousNbt() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(3);
        payload.writeShort(5);
        writeSlot765(payload, 194, 2, // short_grass → grass
                new Tag.CompoundTag(Map.of("display", new Tag.CompoundTag(
                        Map.of("Name", new Tag.StringTag("test"))))));
        Reader reader = new Reader(ChunkTranslator.translateSetSlot765To764(payload.bytes()));
        assertEquals(0, reader.readByte());
        assertEquals(3, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(173, reader.readVarInt());
        assertEquals(2, reader.readByte());
        Nbt.ReadResult nbt = reader.readAnonymousNbt();
        assertTrue(nbt.tag() instanceof Tag.CompoundTag);
        assertEquals(0, reader.remaining());
    }

    @Test
    void windowItemsRemapsAllSlots() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(0);
        payload.writeVarInt(1);
        payload.writeVarInt(2);
        writeSlot765(payload, 1311, 1, null); // trial_key → tripwire_hook
        payload.writeBoolean(false); // 空スロット
        writeSlot765(payload, 1, 64, null); // carried：stone は同じ
        Reader reader = new Reader(ChunkTranslator.translateWindowItems765To764(payload.bytes()));
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(655, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte()); // NBT 無し
        assertTrue(!reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(64, reader.readByte()); // 個数
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCooldownAndBlockActionAndOpenWindow() {
        Writer cooldown = new Writer();
        cooldown.writeVarInt(1311);
        cooldown.writeVarInt(20);
        Reader r = new Reader(ChunkTranslator.translateSetCooldown765To764(cooldown.bytes()));
        assertEquals(655, r.readVarInt());
        assertEquals(20, r.readVarInt());

        Writer action = new Writer();
        action.writePositionRaw(0x55L);
        action.writeUnsignedByte(1);
        action.writeUnsignedByte(2);
        action.writeVarInt(1056); // crafter → crafting_table
        r = new Reader(ChunkTranslator.translateBlockAction765To764(action.bytes()));
        assertEquals(0x55L, r.readPositionRaw());
        assertEquals(1, r.readUnsignedByte());
        assertEquals(2, r.readUnsignedByte());
        assertEquals(182, r.readVarInt());

        Writer window = new Writer();
        window.writeVarInt(0);
        window.writeVarInt(7); // crafter_3x3 → crafting
        window.writeAnonymousNbt(new Tag.CompoundTag(Map.of("text", new Tag.StringTag("Crafter"))));
        r = new Reader(ChunkTranslator.translateOpenWindow765To764(window.bytes()));
        assertEquals(0, r.readVarInt());
        assertEquals(11, r.readVarInt());
        assertTrue(r.readString().contains("Crafter")); // NBT → JSON 文字列
    }

    @Test
    void tradeListAndEntityEquipment() {
        Writer trades = new Writer();
        trades.writeVarInt(1);
        trades.writeVarInt(1);
        writeSlot765(trades, 978, 1, null); // input：crafter → crafting_table
        writeSlot765(trades, 1, 1, null); // output：stone
        trades.writeBoolean(false); // 2番目の入力は無し
        trades.writeBoolean(true);
        trades.writeInt(2);
        trades.writeInt(10);
        trades.writeInt(5);
        trades.writeInt(0);
        trades.writeFloat(1.5f);
        trades.writeInt(0);
        trades.writeVarInt(2);
        trades.writeVarInt(3);
        trades.writeBoolean(true);
        trades.writeBoolean(false);
        Reader r = new Reader(ChunkTranslator.translateTradeList765To764(trades.bytes()));
        assertEquals(1, r.readVarInt());
        assertEquals(1, r.readVarInt());
        assertTrue(r.readBoolean());
        assertEquals(278, r.readVarInt()); // crafter → crafting_table
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte()); // NBT 無し
        assertTrue(r.readBoolean());
        assertEquals(1, r.readVarInt()); // stone
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte());
        assertTrue(!r.readBoolean()); // 2番目の入力は無し
        assertTrue(r.readBoolean());
        assertEquals(2, r.readInt());
        assertEquals(10, r.readInt());
        assertEquals(5, r.readInt());
        assertEquals(0, r.readInt());
        assertEquals(1.5f, r.readFloat());
        assertEquals(0, r.readInt());
        assertEquals(2, r.readVarInt());
        assertEquals(3, r.readVarInt());
        assertTrue(r.readBoolean());
        assertTrue(!r.readBoolean());
        assertEquals(0, r.remaining());

        Writer equipment = new Writer();
        equipment.writeVarInt(7);
        equipment.writeUnsignedByte(0x80 | 2);
        writeSlot765(equipment, 978, 1, null);
        equipment.writeUnsignedByte(1);
        writeSlot765(equipment, 741, 1, null); // copper_trapdoor → iron_trapdoor
        r = new Reader(ChunkTranslator.translateEntityEquipment765To764(equipment.bytes()));
        assertEquals(7, r.readVarInt());
        assertEquals(0x80 | 2, r.readUnsignedByte());
        assertTrue(r.readBoolean());
        assertEquals(278, r.readVarInt());
        assertEquals(1, r.readByte()); // 個数
        assertEquals(0, r.readByte()); // NBT 無し
        assertEquals(1, r.readUnsignedByte());
        assertTrue(r.readBoolean());
        assertEquals(700, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte());
        assertEquals(0, r.remaining());
    }

    // ---- エンティティ・音・パーティクル・エフェクト ----

    @Test
    void spawnEntityRemapsTypeAndFallingBlock() {
        Writer payload = new Writer();
        payload.writeVarInt(5);
        payload.writeBytes(new byte[16]); // UUID
        payload.writeVarInt(10); // breeze → blaze
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
        Reader r = new Reader(ChunkTranslator.translateSpawnEntity765To764(payload.bytes()));
        assertEquals(5, r.readVarInt());
        r.readBytes(16);
        assertEquals(7, r.readVarInt());

        Writer falling = new Writer();
        falling.writeVarInt(6);
        falling.writeBytes(new byte[16]);
        falling.writeVarInt(37); // falling_block
        falling.writeDouble(0.0);
        falling.writeDouble(0.0);
        falling.writeDouble(0.0);
        falling.writeByte(0);
        falling.writeByte(0);
        falling.writeByte(0);
        falling.writeVarInt(26638); // trial_spawner の状態 → spawner
        falling.writeShort(0);
        falling.writeShort(0);
        falling.writeShort(0);
        r = new Reader(ChunkTranslator.translateSpawnEntity765To764(falling.bytes()));
        r.readVarInt();
        r.readBytes(16);
        assertEquals(36, r.readVarInt()); // falling_block（765=37 → 764=36）
        r.readDouble();
        r.readDouble();
        r.readDouble();
        r.readByte();
        r.readByte();
        r.readByte();
        assertEquals(2873, r.readVarInt());
    }

    @Test
    void soundEffectRegistryAndInline() {
        Writer registry = new Writer();
        registry.writeVarInt(167); // holder：166＋1（breeze.inhale）
        registry.writeVarInt(0);
        registry.writeInt(8);
        registry.writeInt(64);
        registry.writeInt(8);
        registry.writeFloat(1.0f);
        registry.writeFloat(1.0f);
        registry.writeLong(123L);
        Reader r = new Reader(ChunkTranslator.translateSoundEffect765To764(registry.bytes()));
        assertEquals(148, r.readVarInt()); // blaze.ambient（147）＋1

        Writer inline = new Writer();
        inline.writeVarInt(0);
        inline.writeString("minecraft:entity.breeze.hurt");
        inline.writeBoolean(true);
        inline.writeFloat(16.0f);
        inline.writeVarInt(0);
        inline.writeInt(0);
        inline.writeInt(0);
        inline.writeInt(0);
        inline.writeFloat(1.0f);
        inline.writeFloat(1.0f);
        inline.writeLong(0L);
        r = new Reader(ChunkTranslator.translateSoundEffect765To764(inline.bytes()));
        assertEquals(0, r.readVarInt());
        assertEquals("minecraft:entity.blaze.hurt", r.readString());
        assertTrue(r.readBoolean());
        assertEquals(16.0f, r.readFloat());
    }

    @Test
    void entitySoundEffectAndStopSound() {
        Writer sound = new Writer();
        sound.writeVarInt(639); // holder：638＋1（trial_spawner.break → stone.break 1270）
        sound.writeVarInt(3);
        sound.writeVarInt(4);
        sound.writeFloat(1.0f);
        sound.writeFloat(1.0f);
        sound.writeLong(7L);
        Reader r = new Reader(ChunkTranslator.translateEntitySoundEffect765To764(sound.bytes()));
        assertEquals(1271, r.readVarInt()); // 1270＋1
        assertEquals(3, r.readVarInt());
        assertEquals(4, r.readVarInt());

        Writer payload = new Writer();
        payload.writeByte(3);
        payload.writeVarInt(0);
        payload.writeString("minecraft:block.trial_spawner.break");
        r = new Reader(ChunkTranslator.translateStopSound765To764(payload.bytes()));
        assertEquals(3, r.readByte());
        assertEquals(0, r.readVarInt());
        assertEquals("minecraft:block.stone.break", r.readString());
    }

    @Test
    void worldParticlesRemapIdAndData() {
        // falling_dust（765=27 → 764=25）。データのブロック状態は
        // パーティクルIDの直後に来る。
        Writer payload = new Writer();
        payload.writeVarInt(27);
        payload.writeVarInt(26638);
        payload.writeBoolean(false);
        payload.writeDouble(0.0);
        payload.writeDouble(0.0);
        payload.writeDouble(0.0);
        payload.writeFloat(0.0f);
        payload.writeFloat(0.0f);
        payload.writeFloat(0.0f);
        payload.writeFloat(0.0f);
        payload.writeInt(10);
        Reader r = new Reader(ChunkTranslator.translateWorldParticles765To764(payload.bytes()));
        assertEquals(25, r.readVarInt());
        assertEquals(2873, r.readVarInt()); // trial_spawner → spawner
        assertTrue(!r.readBoolean());
        r.readDouble();
        r.readDouble();
        r.readDouble();
        r.readFloat();
        r.readFloat();
        r.readFloat();
        r.readFloat();
        assertEquals(10, r.readInt());
        assertEquals(0, r.remaining());

        // gust（765=24 → 764=5 cloud、データ無し）。
        Writer gust = new Writer();
        gust.writeVarInt(24);
        gust.writeBoolean(true);
        gust.writeDouble(1.0);
        gust.writeDouble(2.0);
        gust.writeDouble(3.0);
        gust.writeFloat(0.5f);
        gust.writeFloat(0.5f);
        gust.writeFloat(0.5f);
        gust.writeFloat(0.0f);
        gust.writeInt(5);
        r = new Reader(ChunkTranslator.translateWorldParticles765To764(gust.bytes()));
        assertEquals(5, r.readVarInt());

        // item 粒子（765=42 → 764=40）。スロットの中身も変換する。
        Writer item = new Writer();
        item.writeVarInt(42);
        writeSlot765(item, 978, 1, null); // crafter → crafting_table
        item.writeBoolean(false);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeInt(1);
        r = new Reader(ChunkTranslator.translateWorldParticles765To764(item.bytes()));
        assertEquals(40, r.readVarInt());
        assertTrue(r.readBoolean());
        assertEquals(278, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte());
    }

    @Test
    void entityEffectIsIdentityIn764() {
        Writer payload = new Writer();
        payload.writeVarInt(4);
        payload.writeVarInt(0); // Speed（765=0 → 764=0。そのまま）
        payload.writeByte(1);
        payload.writeVarInt(100);
        payload.writeByte(0);
        payload.writeBoolean(true); // factor 有り（名前なし NBT）
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("factor", new Tag.FloatTag(1.5f))));
        Reader r = new Reader(ChunkTranslator.translateEntityEffect765To764(payload.bytes()));
        assertEquals(4, r.readVarInt());
        assertEquals(0, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(100, r.readVarInt());
        assertEquals(0, r.readByte());
        assertTrue(r.readBoolean());
        // factorCodec は名前なし NBT のまま。
        Nbt.ReadResult factor = r.readAnonymousNbt();
        assertEquals(1.5f, ((Tag.FloatTag) ((Tag.CompoundTag) factor.tag()).value().get("factor")).value());
        assertEquals(0, r.remaining());

        Writer remove = new Writer();
        remove.writeVarInt(4);
        remove.writeVarInt(5); // 765=5 → 764=5（そのまま）
        r = new Reader(ChunkTranslator.translateRemoveEntityEffect765To764(remove.bytes()));
        assertEquals(4, r.readVarInt());
        assertEquals(5, r.readVarInt());
    }

    @Test
    void entityMetadataKeepsVariantsAndRemaps() {
        Writer payload = new Writer();
        payload.writeVarInt(9);
        // index 0：Byte
        payload.writeUnsignedByte(0);
        payload.writeVarInt(0);
        payload.writeByte(1);
        // index 2：Optional Text Component（名前）。NBT → JSON 文字列。
        payload.writeUnsignedByte(2);
        payload.writeVarInt(6);
        payload.writeBoolean(true);
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("text", new Tag.StringTag("Bob"))));
        // index 8：Slot
        payload.writeUnsignedByte(8);
        payload.writeVarInt(7);
        writeSlot765(payload, 978, 1, null);
        // index 9：Block State
        payload.writeUnsignedByte(9);
        payload.writeVarInt(14);
        payload.writeVarInt(26638);
        // index 10：Cat Variant（764では落とさず通す）
        payload.writeUnsignedByte(10);
        payload.writeVarInt(22);
        payload.writeVarInt(3);
        // index 11：Painting Variant（holder 直書き 0＋NBT。通す）
        payload.writeUnsignedByte(11);
        payload.writeVarInt(26);
        payload.writeVarInt(0);
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("asset_id", new Tag.StringTag("minecraft:aztec"))));
        // index 12：Wolf Variant（holder 参照。通す）
        payload.writeUnsignedByte(12);
        payload.writeVarInt(23);
        payload.writeVarInt(2);
        // index 13：Compound Tag（名前なしのまま）
        payload.writeUnsignedByte(13);
        payload.writeVarInt(16);
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("x", new Tag.IntTag(9))));
        // 終端
        payload.writeUnsignedByte(0xFF);
        Reader r = new Reader(ChunkTranslator.translateEntityMetadata765To764(payload.bytes()));
        assertEquals(9, r.readVarInt());
        assertEquals(0, r.readUnsignedByte());
        assertEquals(0, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(2, r.readUnsignedByte());
        assertEquals(6, r.readVarInt());
        assertTrue(r.readBoolean());
        assertTrue(r.readString().contains("Bob"));
        assertEquals(8, r.readUnsignedByte());
        assertEquals(7, r.readVarInt());
        assertTrue(r.readBoolean());
        assertEquals(278, r.readVarInt());
        assertEquals(1, r.readByte()); // 個数
        assertEquals(0, r.readByte()); // NBT 無し
        assertEquals(9, r.readUnsignedByte());
        assertEquals(14, r.readVarInt());
        assertEquals(2873, r.readVarInt());
        // cat variant は残っている（763向けでは落ちる）。
        assertEquals(10, r.readUnsignedByte());
        assertEquals(22, r.readVarInt());
        assertEquals(3, r.readVarInt());
        // painting variant（holder 0＋NBT）も残っている。
        assertEquals(11, r.readUnsignedByte());
        assertEquals(26, r.readVarInt());
        assertEquals(0, r.readVarInt());
        Nbt.ReadResult painting = r.readAnonymousNbt();
        assertEquals("minecraft:aztec",
                ((Tag.StringTag) ((Tag.CompoundTag) painting.tag()).value().get("asset_id")).value());
        assertEquals(12, r.readUnsignedByte());
        assertEquals(23, r.readVarInt());
        assertEquals(2, r.readVarInt());
        // compound_tag は名前なしのまま。
        assertEquals(13, r.readUnsignedByte());
        assertEquals(16, r.readVarInt());
        Nbt.ReadResult compound = r.readAnonymousNbt();
        assertEquals(9, ((Tag.IntTag) ((Tag.CompoundTag) compound.tag()).value().get("x")).value());
        assertEquals(0xFF, r.readUnsignedByte());
        assertEquals(0, r.remaining());
    }

    @Test
    void entityMetadataParticleRemaps() {
        Writer payload = new Writer();
        payload.writeVarInt(1);
        // index 0：Particle（falling_dust 27 → 25、状態も変換）
        payload.writeUnsignedByte(0);
        payload.writeVarInt(17);
        payload.writeVarInt(27);
        payload.writeVarInt(26638);
        payload.writeUnsignedByte(0xFF);
        Reader r = new Reader(ChunkTranslator.translateEntityMetadata765To764(payload.bytes()));
        assertEquals(1, r.readVarInt());
        assertEquals(0, r.readUnsignedByte());
        assertEquals(17, r.readVarInt());
        assertEquals(25, r.readVarInt());
        assertEquals(2873, r.readVarInt());
        assertEquals(0xFF, r.readUnsignedByte());
        assertEquals(0, r.remaining());
    }

    // ---- サーバー行き ----

    @Test
    void windowClickReverseMapsItems764() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(0);
        payload.writeVarInt(1);
        payload.writeShort(3);
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeVarInt(1);
        payload.writeShort(10);
        writeSlot764(payload, 278, 1, null); // crafting_table → 299（765にもある）
        writeSlot764(payload, 173, 1, null); // grass → short_grass
        Reader r = new Reader(ChunkTranslator.translateWindowClick764To765(payload.bytes()));
        assertEquals(0, r.readUnsignedByte());
        assertEquals(1, r.readVarInt());
        assertEquals(3, r.readShort());
        assertEquals(0, r.readByte());
        assertEquals(0, r.readVarInt());
        assertEquals(1, r.readVarInt());
        assertEquals(10, r.readShort());
        assertTrue(r.readBoolean());
        assertEquals(299, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte()); // NBT 無し
        assertTrue(r.readBoolean());
        assertEquals(194, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte());
        assertEquals(0, r.remaining());
    }

    @Test
    void setCreativeSlotReverse764() {
        Writer payload = new Writer();
        payload.writeShort(36);
        writeSlot764(payload, 278, 1, null);
        Reader r = new Reader(ChunkTranslator.translateSetCreativeSlot764To765(payload.bytes()));
        assertEquals(36, r.readShort());
        assertTrue(r.readBoolean());
        assertEquals(299, r.readVarInt()); // crafting_table（765にもある）
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte()); // NBT 無し
        assertEquals(0, r.remaining());
    }
}
