package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の変換テスト。765形式のペイロードを合成して
 * 763形式に変換し、読み直して中身を確認する。
 */
class ChunkTranslatorTest {

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

    // ---- Chunk Data ----

    @Test
    void chunkDataRemapsPaletteAndBlockEntities() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1, 26638}, 5));
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

        byte[] translated = ChunkTranslator.translateChunkData765To763(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        // 高さマップは名前付き NBT（ルート名は空文字）になっている。
        Nbt.NamedResult named = reader.readNamedNbt();
        assertEquals("", named.name());
        assertTrue(named.tag() instanceof Tag.CompoundTag);
        int dataLength = reader.readVarInt();
        byte[] sections = reader.readBytes(dataLength);
        Reader section = new Reader(sections);
        assertEquals(1, section.readShort());
        assertEquals(4, section.readUnsignedByte());
        assertEquals(3, section.readVarInt());
        assertEquals(0, section.readVarInt());
        assertEquals(1, section.readVarInt());
        assertEquals(2873, section.readVarInt()); // trial_spawner → spawner
        assertEquals(256, section.readVarInt());
        for (int i = 0; i < 256; i++) {
            assertEquals(0L, section.readLong());
        }
        assertEquals(0, section.readUnsignedByte());
        assertEquals(5, section.readVarInt()); // バイオームはそのまま
        assertEquals(0, section.readVarInt());
        assertEquals(0, section.remaining());
        // ブロックエンティティはチェストだけ残る。
        assertEquals(1, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        Nbt.NamedResult beNbt = reader.readNamedNbt();
        assertEquals("", beNbt.name());
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

        byte[] translated = ChunkTranslator.translateChunkData765To763(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readNamedNbt();
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
        // 4096エントリ×15bit＝(4096*15+63)/64＝960 long になる（2026-11-14に修正。
        // 旧式の 1024 long は long の境界で区切る誤った数え方）。
        long[] longs = new long[960];
        longs[0] = packDirect(new int[]{26638, 1, 0, 0}, 15);
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

        byte[] translated = ChunkTranslator.translateChunkData765To763(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readNamedNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        Reader s = new Reader(sections);
        assertEquals(2, s.readShort());
        assertEquals(15, s.readUnsignedByte());
        assertEquals(960, s.readVarInt());
        long first = s.readLong();
        int[] entries = unpackDirect(first, 15, 4);
        assertEquals(2873, entries[0]); // trial_spawner → spawner
        assertEquals(1, entries[1]); // stone は同じ
        assertEquals(0, entries[2]);
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

    @Test
    void chunkDataBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02}; // 短すぎて読めない
        assertArrayEquals(broken, ChunkTranslator.translateChunkData765To763(broken));
    }

    // ---- Block Change / Multi Block Change / Block Entity Data ----

    @Test
    void blockChange() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(26638);
        byte[] translated = ChunkTranslator.translateBlockChange765To763(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(2873, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockChangeUnknownStateFallsBackToStone() {
        Writer payload = new Writer();
        payload.writePositionRaw(0L);
        payload.writeVarInt(99999);
        Reader reader = new Reader(ChunkTranslator.translateBlockChange765To763(payload.bytes()));
        reader.readPositionRaw();
        assertEquals(1, reader.readVarInt());
    }

    @Test
    void multiBlockChange() {
        Writer payload = new Writer();
        payload.writeLong(0x0102030405060708L);
        payload.writeVarInt(2);
        payload.writeVarInt((26638 << 12) | 0x123);
        payload.writeVarInt((1 << 12) | 0x456);
        Reader reader = new Reader(ChunkTranslator.translateMultiBlockChange765To763(payload.bytes()));
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
        assertNull(ChunkTranslator.translateBlockEntityData765To763(payload.bytes()));
    }

    @Test
    void blockEntityDataKeepsKnownType() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x77L);
        payload.writeVarInt(1); // chest
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("x", new Tag.IntTag(3))));
        byte[] translated = ChunkTranslator.translateBlockEntityData765To763(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x77L, reader.readPositionRaw());
        assertEquals(1, reader.readVarInt());
        Nbt.NamedResult nbt = reader.readNamedNbt();
        assertEquals("", nbt.name());
        assertEquals(3, ((Tag.IntTag) ((Tag.CompoundTag) nbt.tag()).value().get("x")).value());
        assertEquals(0, reader.remaining());
    }

    // ---- スロット ----

    @Test
    void setSlotRemapsItemAndNbt() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(3);
        payload.writeShort(5);
        writeSlot765(payload, 978, 2, // crafter → crafting_table
                new Tag.CompoundTag(Map.of("display", new Tag.CompoundTag(
                        Map.of("Name", new Tag.StringTag("test"))))));
        Reader reader = new Reader(ChunkTranslator.translateSetSlot765To763(payload.bytes()));
        assertEquals(0, reader.readByte());
        assertEquals(3, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(278, reader.readVarInt());
        assertEquals(2, reader.readByte());
        Nbt.NamedResult nbt = reader.readNamedNbt();
        assertEquals("", nbt.name());
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
        Reader reader = new Reader(ChunkTranslator.translateWindowItems765To763(payload.bytes()));
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
        Reader r = new Reader(ChunkTranslator.translateSetCooldown765To763(cooldown.bytes()));
        assertEquals(655, r.readVarInt());
        assertEquals(20, r.readVarInt());

        Writer action = new Writer();
        action.writePositionRaw(0x55L);
        action.writeUnsignedByte(1);
        action.writeUnsignedByte(2);
        action.writeVarInt(1056); // crafter → crafting_table
        r = new Reader(ChunkTranslator.translateBlockAction765To763(action.bytes()));
        assertEquals(0x55L, r.readPositionRaw());
        assertEquals(1, r.readUnsignedByte());
        assertEquals(2, r.readUnsignedByte());
        assertEquals(182, r.readVarInt());

        Writer window = new Writer();
        window.writeVarInt(0);
        window.writeVarInt(7); // crafter_3x3 → crafting
        window.writeAnonymousNbt(new Tag.CompoundTag(Map.of("text", new Tag.StringTag("Crafter"))));
        r = new Reader(ChunkTranslator.translateOpenWindow765To763(window.bytes()));
        assertEquals(0, r.readVarInt());
        assertEquals(11, r.readVarInt());
        assertTrue(r.readString().contains("Crafter"));
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
        Reader r = new Reader(ChunkTranslator.translateTradeList765To763(trades.bytes()));
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
        r = new Reader(ChunkTranslator.translateEntityEquipment765To763(equipment.bytes()));
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
        Reader r = new Reader(ChunkTranslator.translateSpawnEntity765To763(payload.bytes()));
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
        r = new Reader(ChunkTranslator.translateSpawnEntity765To763(falling.bytes()));
        r.readVarInt();
        r.readBytes(16);
        assertEquals(36, r.readVarInt()); // falling_block（765=37 → 763=36）
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
        Reader r = new Reader(ChunkTranslator.translateSoundEffect765To763(registry.bytes()));
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
        r = new Reader(ChunkTranslator.translateSoundEffect765To763(inline.bytes()));
        assertEquals(0, r.readVarInt());
        assertEquals("minecraft:entity.blaze.hurt", r.readString());
        assertTrue(r.readBoolean());
        assertEquals(16.0f, r.readFloat());
    }

    @Test
    void stopSoundRenamesNewSound() {
        Writer payload = new Writer();
        payload.writeByte(3);
        payload.writeVarInt(0);
        payload.writeString("minecraft:block.trial_spawner.break");
        Reader r = new Reader(ChunkTranslator.translateStopSound765To763(payload.bytes()));
        assertEquals(3, r.readByte());
        assertEquals(0, r.readVarInt());
        assertEquals("minecraft:block.stone.break", r.readString());
    }

    @Test
    void worldParticlesRemapIdAndData() {
        // falling_dust（765=27 → 763=25）。データのブロック状態は
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
        Reader r = new Reader(ChunkTranslator.translateWorldParticles765To763(payload.bytes()));
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

        // trial_spawner_detection（765=100 → 763=30、データ無し）。
        Writer plain = new Writer();
        plain.writeVarInt(100);
        plain.writeBoolean(true);
        plain.writeDouble(1.0);
        plain.writeDouble(2.0);
        plain.writeDouble(3.0);
        plain.writeFloat(0.5f);
        plain.writeFloat(0.5f);
        plain.writeFloat(0.5f);
        plain.writeFloat(0.0f);
        plain.writeInt(5);
        r = new Reader(ChunkTranslator.translateWorldParticles765To763(plain.bytes()));
        assertEquals(30, r.readVarInt());
    }

    @Test
    void entityEffectShiftsId() {
        Writer payload = new Writer();
        payload.writeVarInt(4);
        payload.writeVarInt(0); // Speed（765=0 → 763=1）
        payload.writeByte(1);
        payload.writeVarInt(100);
        payload.writeByte(0);
        payload.writeBoolean(false); // factor 無し
        Reader r = new Reader(ChunkTranslator.translateEntityEffect765To763(payload.bytes()));
        assertEquals(4, r.readVarInt());
        assertEquals(1, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(100, r.readVarInt());
        assertEquals(0, r.readByte());
        assertTrue(!r.readBoolean());

        Writer remove = new Writer();
        remove.writeVarInt(4);
        remove.writeVarInt(5); // 765=5 → 763=6
        r = new Reader(ChunkTranslator.translateRemoveEntityEffect765To763(remove.bytes()));
        assertEquals(4, r.readVarInt());
        assertEquals(6, r.readVarInt());
    }

    @Test
    void entityMetadataRemapsSlotStateAndChat() {
        Writer payload = new Writer();
        payload.writeVarInt(9);
        // index 0：Byte
        payload.writeUnsignedByte(0);
        payload.writeVarInt(0);
        payload.writeByte(1);
        // index 2：Optional Text Component（名前）
        payload.writeUnsignedByte(2);
        payload.writeVarInt(6);
        payload.writeBoolean(true);
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of("text", new Tag.StringTag("Bob"))));
        // index 8：Slot（額縁の中身など）
        payload.writeUnsignedByte(8);
        payload.writeVarInt(7);
        writeSlot765(payload, 978, 1, null);
        // index 9：Block State
        payload.writeUnsignedByte(9);
        payload.writeVarInt(14);
        payload.writeVarInt(26638);
        // index 10：Cat Variant（落とす）
        payload.writeUnsignedByte(10);
        payload.writeVarInt(22);
        payload.writeVarInt(3);
        // 終端
        payload.writeUnsignedByte(0xFF);
        Reader r = new Reader(ChunkTranslator.translateEntityMetadata765To763(payload.bytes()));
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
        assertEquals(0xFF, r.readUnsignedByte()); // cat variant は落ちている
        assertEquals(0, r.remaining());
    }

    // ---- サーバー行き ----

    @Test
    void windowClickReverseMapsItems() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(0);
        payload.writeVarInt(1);
        payload.writeShort(3);
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeVarInt(1);
        payload.writeShort(10);
        writeSlot763(payload, 278, 1, null); // crafting_table → crafter
        writeSlot763(payload, 173, 1, null); // grass → short_grass
        Reader r = new Reader(ChunkTranslator.translateWindowClick763To765(payload.bytes()));
        assertEquals(0, r.readUnsignedByte());
        assertEquals(1, r.readVarInt());
        assertEquals(3, r.readShort());
        assertEquals(0, r.readByte());
        assertEquals(0, r.readVarInt());
        assertEquals(1, r.readVarInt());
        assertEquals(10, r.readShort());
        assertTrue(r.readBoolean());
        assertEquals(299, r.readVarInt()); // crafting_table（765にもある）
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte()); // NBT 無し
        assertTrue(r.readBoolean());
        assertEquals(194, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte());
        assertEquals(0, r.remaining());
    }

    /** スロット（763形式：bool＋アイテムID＋個数＋名前付き NBT）。 */
    private static void writeSlot763(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        if (nbt == null) {
            writer.writeByte(0);
        } else {
            writer.writeNamedNbt("", nbt);
        }
    }

    @Test
    void setCreativeSlotReverse() {
        Writer payload = new Writer();
        payload.writeShort(36);
        writeSlot763(payload, 278, 1, null);
        Reader r = new Reader(ChunkTranslator.translateSetCreativeSlot763To765(payload.bytes()));
        assertEquals(36, r.readShort());
        assertTrue(r.readBoolean());
        assertEquals(299, r.readVarInt()); // crafting_table（765にもある）
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte()); // NBT 無し
        assertEquals(0, r.remaining());
    }
}
