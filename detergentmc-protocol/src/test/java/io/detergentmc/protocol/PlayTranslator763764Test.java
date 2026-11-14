package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame763;
import io.detergentmc.protocol.PlayTranslator.JoinGame765;
import io.detergentmc.protocol.PlayTranslator.JoinGame766;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 763↔764のプレイ・ペイロード変換と、766の Join Game／Respawn／
 * Login Success／Registry Data の変換。
 *
 * <p>764のスロットは NBT が名前なし、763は名前付き（ルート名は空文字）。
 * テキストは両方とも JSON 文字列。エフェクトは764が0ベース・763が1ベース。
 */
class PlayTranslator763764Test {

    // ------------------------------------------------------------------
    // スロット
    // ------------------------------------------------------------------

    /** 764のスロット1個（NBTなし）。 */
    private static byte[] slot764(int itemId, int count) {
        Writer writer = new Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0);
        return writer.bytes();
    }

    /** 763のスロット1個（NBTなし）。 */
    private static byte[] slot763(int itemId, int count) {
        Writer writer = new Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0);
        return writer.bytes();
    }

    /** 変換後の763スロットを読む（NBTなし。末尾の 0x00 を1バイト読む）。 */
    private record Slot(boolean present, int item, int count, Tag nbt) {
    }

    private static Slot readSlot763NoNbt(Reader reader) {
        boolean present = reader.readBoolean();
        if (!present) {
            return new Slot(false, -1, -1, null);
        }
        int item = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readUnsignedByte());
        return new Slot(true, item, count, null);
    }

    /** 変換後の763スロットを読む（NBTあり。名前付き NBT として読む）。 */
    private static Slot readSlot763WithNbt(Reader reader) {
        boolean present = reader.readBoolean();
        assertTrue(present);
        int item = reader.readVarInt();
        int count = reader.readByte();
        Tag nbt = reader.readNamedNbt().tag();
        return new Slot(true, item, count, nbt);
    }

    /** 変換後の764スロットを読む（NBTなし。末尾の 0x00 を1バイト読む）。 */
    private static Slot readSlot764NoNbt(Reader reader) {
        boolean present = reader.readBoolean();
        if (!present) {
            return new Slot(false, -1, -1, null);
        }
        int item = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readUnsignedByte());
        return new Slot(true, item, count, null);
    }

    /** 変換後の764スロットを読む（NBTあり。名前なし NBT として読む）。 */
    private static Slot readSlot764WithNbt(Reader reader) {
        boolean present = reader.readBoolean();
        assertTrue(present);
        int item = reader.readVarInt();
        int count = reader.readByte();
        Tag nbt = reader.readAnonymousNbt().tag();
        return new Slot(true, item, count, nbt);
    }

    @Test
    void slot764To763EmptyIsPassthrough() {
        Writer writer = new Writer();
        PlayTranslator.translateSlot764To763(new Reader(new byte[]{0}), writer);
        assertArrayEquals(new byte[]{0}, writer.bytes());
    }

    @Test
    void slot764To763ConvertsNbtNaming() {
        Tag.CompoundTag nbt = new Tag.CompoundTag(Map.of(
                "CustomName", new Tag.StringTag("{\"text\":\"x\"}")));
        Writer in = new Writer();
        in.writeBoolean(true);
        in.writeVarInt(1);
        in.writeByte(3);
        in.writeAnonymousNbt(nbt);
        Writer out = new Writer();
        PlayTranslator.translateSlot764To763(new Reader(in.bytes()), out);
        Slot slot = readSlot763WithNbt(new Reader(out.bytes()));
        assertTrue(slot.present());
        assertEquals(1, slot.item());
        assertEquals(3, slot.count());
        assertEquals(nbt, slot.nbt());
    }

    @Test
    void slot763To764RoundTrip() {
        Tag.CompoundTag nbt = new Tag.CompoundTag(Map.of(
                "Damage", new Tag.IntTag(5)));
        Writer in = new Writer();
        in.writeBoolean(true);
        in.writeVarInt(1);
        in.writeByte(2);
        in.writeNamedNbt("", nbt);
        Writer mid = new Writer();
        PlayTranslator.translateSlot763To764(new Reader(in.bytes()), mid);
        Slot back764 = readSlot764WithNbt(new Reader(mid.bytes()));
        assertEquals(1, back764.item());
        assertEquals(nbt, back764.nbt());
        Writer out = new Writer();
        PlayTranslator.translateSlot764To763(new Reader(mid.bytes()), out);
        Slot back763 = readSlot763WithNbt(new Reader(out.bytes()));
        assertEquals(1, back763.item());
        assertEquals(2, back763.count());
        assertEquals(nbt, back763.nbt());
    }

    // ------------------------------------------------------------------
    // Block Entity Data／Set Slot／Window Items／Entity Equipment／Trade List
    // ------------------------------------------------------------------

    @Test
    void blockEntityData764To763ConvertsTypeAndNbt() {
        Writer writer = new Writer();
        writer.writePositionRaw(0L);
        writer.writeVarInt(1);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        byte[] out = PlayTranslator.translateBlockEntityData764To763(writer.bytes());
        assertNotNull(out);
        Reader reader = new Reader(out);
        assertEquals(0L, reader.readPositionRaw());
        assertEquals(1, reader.readVarInt());
        assertEquals(new Tag.CompoundTag(Map.of()), reader.readNamedNbt().tag());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockEntityDataUnknownTypeReturnsNull() {
        Writer writer = new Writer();
        writer.writePositionRaw(0L);
        writer.writeVarInt(999);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        assertNull(PlayTranslator.translateBlockEntityData764To763(writer.bytes()));
    }

    @Test
    void setSlot764To763() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(5);
        writer.writeShort(3);
        writer.writeBytes(slot764(1, 2));
        byte[] out = PlayTranslator.translateSetSlot764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        Slot slot = readSlot763NoNbt(reader);
        assertEquals(1, slot.item());
        assertEquals(2, slot.count());
        assertEquals(0, reader.remaining());
    }

    @Test
    void windowItems764To763() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0);
        writer.writeVarInt(1);
        writer.writeVarInt(2);
        writer.writeBytes(slot764(1, 1));
        writer.writeBytes(slot764(1, 64));
        writer.writeBytes(new byte[]{0}); // 手持ちは空
        byte[] out = PlayTranslator.translateWindowItems764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(1, readSlot763NoNbt(reader).item());
        assertEquals(64, readSlot763NoNbt(reader).count());
        assertFalse(readSlot763NoNbt(reader).present());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityEquipment764To763() {
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeUnsignedByte(0x00); // 最後のエントリ
        writer.writeBytes(slot764(1, 1));
        byte[] out = PlayTranslator.translateEntityEquipment764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0x00, reader.readUnsignedByte());
        assertEquals(1, readSlot763NoNbt(reader).item());
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeList764To763() {
        Writer writer = new Writer();
        writer.writeVarInt(1); // ウィンドウ
        writer.writeVarInt(1); // 取引1件
        writer.writeBytes(slot764(1, 1));
        writer.writeBytes(new byte[]{0});
        writer.writeBytes(slot764(1, 1));
        writer.writeBoolean(false);
        writer.writeInt(10); // maxUses
        writer.writeInt(0); // uses
        writer.writeInt(0); // xp
        writer.writeInt(0); // specialPrice
        writer.writeFloat(0.05f);
        writer.writeInt(0); // demand
        writer.writeVarInt(1); // villagerLevel
        writer.writeVarInt(0); // villagerXp
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        byte[] out = PlayTranslator.translateTradeList764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, readSlot763NoNbt(reader).item());
        assertFalse(readSlot763NoNbt(reader).present());
        assertEquals(1, readSlot763NoNbt(reader).item());
        assertFalse(reader.readBoolean());
        assertEquals(10, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(0.05f, reader.readFloat());
        assertEquals(0, reader.readInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Entity Metadata
    // ------------------------------------------------------------------

    @Test
    void entityMetadata764To763DropsWolfAndPaintingVariants() {
        Writer writer = new Writer();
        writer.writeVarInt(42);
        // byte エントリ（type 0）はそのまま。
        writer.writeUnsignedByte(0);
        writer.writeVarInt(0);
        writer.writeByte(5);
        // 猫の variant（type 22）はそのまま。
        writer.writeUnsignedByte(1);
        writer.writeVarInt(22);
        writer.writeVarInt(3);
        // 狼の variant（type 23、直書き NBT）は落とす。
        writer.writeUnsignedByte(2);
        writer.writeVarInt(23);
        writer.writeVarInt(0);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        // 絵画の variant（type 26、レジストリ参照）は落とす。
        writer.writeUnsignedByte(3);
        writer.writeVarInt(26);
        writer.writeVarInt(1);
        // カエルの variant（type 24）はそのまま。
        writer.writeUnsignedByte(4);
        writer.writeVarInt(24);
        writer.writeVarInt(1);
        // スロット（type 7）は変換する。
        writer.writeUnsignedByte(5);
        writer.writeVarInt(7);
        writer.writeBytes(slot764(1, 1));
        writer.writeUnsignedByte(0xFF);
        byte[] out = PlayTranslator.translateEntityMetadata764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(42, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(5, reader.readByte());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(22, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(4, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(5, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        assertEquals(1, readSlot763NoNbt(reader).item());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Set Cooldown／Block Action／Open Window／Spawn Entity
    // ------------------------------------------------------------------

    @Test
    void setCooldown764To763() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(10);
        byte[] out = PlayTranslator.translateSetCooldown764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(10, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockAction764To763() {
        Writer writer = new Writer();
        writer.writePositionRaw(0L);
        writer.writeUnsignedByte(1);
        writer.writeUnsignedByte(2);
        writer.writeVarInt(1);
        byte[] out = PlayTranslator.translateBlockAction764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0L, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void openWindow764To763() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        writer.writeString("{\"text\":\"x\"}");
        byte[] out = PlayTranslator.translateOpenWindow764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals("{\"text\":\"x\"}", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity764To763FallingBlockRemapsBlockState() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeBytes(new byte[16]);
        writer.writeVarInt(36); // falling_block（764）
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeVarInt(1); // ブロック状態
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        byte[] out = PlayTranslator.translateSpawnEntity764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(16, reader.readBytes(16).length);
        assertEquals(36, reader.readVarInt());
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(ClientAdapter.mapBlockState764To763(1), reader.readVarInt());
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity764To763NonFallingBlockKeepsObjectData() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeBytes(new byte[16]);
        writer.writeVarInt(1);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeVarInt(99);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        byte[] out = PlayTranslator.translateSpawnEntity764To763(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readBytes(16);
        assertEquals(1, reader.readVarInt());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte();
        reader.readByte();
        reader.readByte();
        assertEquals(99, reader.readVarInt());
    }

    // ------------------------------------------------------------------
    // 音・パーティクル・エフェクト
    // ------------------------------------------------------------------

    @Test
    void soundEffect764To763DirectName() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // 直書き
        writer.writeString("minecraft:block.stone.break");
        writer.writeBoolean(false);
        writer.writeVarInt(0); // category
        writer.writeInt(8);
        writer.writeInt(64);
        writer.writeInt(8);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(0L);
        byte[] out = PlayTranslator.translateSoundEffect764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt());
        assertEquals("minecraft:block.stone.break", reader.readString());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(8, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(8, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void stopSound764To763MapsName() {
        Writer writer = new Writer();
        writer.writeByte(0x02);
        writer.writeString("minecraft:block.stone.break");
        byte[] out = PlayTranslator.translateStopSound764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x02, reader.readByte());
        assertEquals("minecraft:block.stone.break", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles764To763Dust() {
        Writer writer = new Writer();
        writer.writeVarInt(14); // dust
        writer.writeFloat(1.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(1.0f);
        writer.writeBoolean(false);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeInt(1);
        byte[] out = PlayTranslator.translateWorldParticles764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(ClientAdapter.mapParticle764To763(14), reader.readVarInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertFalse(reader.readBoolean());
        assertEquals(0.0, reader.readDouble());
        assertEquals(0.0, reader.readDouble());
        assertEquals(0.0, reader.readDouble());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(1, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityEffect764To763AddsOne() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(0); // speed（764は0ベース）
        writer.writeByte(1);
        writer.writeVarInt(100);
        writer.writeByte(0);
        writer.writeBoolean(false);
        byte[] out = PlayTranslator.translateEntityEffect764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // 763は1ベース
        assertEquals(1, reader.readByte());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void removeEntityEffect764To763AddsOne() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        byte[] out = PlayTranslator.translateRemoveEntityEffect764To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // サーバー行き（Window Click／Set Creative Slot）
    // ------------------------------------------------------------------

    @Test
    void windowClick763To764AndBack() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(1);
        writer.writeVarInt(2);
        writer.writeShort(5);
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeVarInt(1);
        writer.writeShort(5);
        writer.writeBytes(slot763(1, 1));
        writer.writeBytes(new byte[]{0}); // カーソルは空
        byte[] to764 = PlayTranslator.translateWindowClick763To764(writer.bytes());
        Reader reader = new Reader(to764);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertEquals(1, readSlot764NoNbt(reader).item());
        assertFalse(readSlot764NoNbt(reader).present());
        assertEquals(0, reader.remaining());
        byte[] back = PlayTranslator.translateWindowClick764To763(to764);
        assertArrayEquals(writer.bytes(), back);
    }

    @Test
    void setCreativeSlot763To764() {
        Writer writer = new Writer();
        writer.writeShort(5);
        writer.writeBytes(slot763(1, 1));
        byte[] out = PlayTranslator.translateSetCreativeSlot763To764(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, readSlot764NoNbt(reader).item());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Login Start（763 ⇄ 765／766）
    // ------------------------------------------------------------------

    @Test
    void loginStart765To763AddsBool() {
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        byte[] out = PlayTranslator.translateLoginStart765To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("Steve", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart763To765AndBackRoundTrip() {
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        byte[] to765 = PlayTranslator.translateLoginStart763To765(writer.bytes());
        byte[] back = PlayTranslator.translateLoginStart765To763(to765);
        assertArrayEquals(writer.bytes(), back);
    }

    // ------------------------------------------------------------------
    // Login Success（766の strictErrorHandling）
    // ------------------------------------------------------------------

    private static byte[] loginSuccess765Payload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    @Test
    void loginSuccess765To766AppendsStrictBool() {
        byte[] out = PlayTranslator.translateLoginSuccess765To766(loginSuccess765Payload());
        assertEquals(loginSuccess765Payload().length + 1, out.length);
        assertEquals(0, out[out.length - 1]);
        assertArrayEquals(loginSuccess765Payload(),
                java.util.Arrays.copyOf(out, loginSuccess765Payload().length));
    }

    @Test
    void loginSuccess766To765StripsStrictBool() {
        byte[] withBool = PlayTranslator.translateLoginSuccess765To766(loginSuccess765Payload());
        assertArrayEquals(loginSuccess765Payload(),
                PlayTranslator.translateLoginSuccess766To765(withBool));
    }

    @Test
    void loginSuccess766To765WithoutBoolThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayTranslator.translateLoginSuccess766To765(loginSuccess765Payload()));
    }

    // ------------------------------------------------------------------
    // Registry Data（765／764 ⇄ 766）
    // ------------------------------------------------------------------

    /** dimension_type（overworld=0）＋biome（plains=0）の codec。 */
    private static Tag.CompoundTag testCodec() {
        Map<String, Tag> root = new java.util.LinkedHashMap<>();
        root.put("minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:dimension_type"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of(
                                "name", new Tag.StringTag("minecraft:overworld"),
                                "id", new Tag.IntTag(0),
                                "element", new Tag.CompoundTag(Map.of()))))))));
        root.put("minecraft:worldgen/biome", new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:worldgen/biome"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of(
                                "name", new Tag.StringTag("minecraft:plains"),
                                "id", new Tag.IntTag(0),
                                "element", new Tag.CompoundTag(Map.of()))))))));
        return new Tag.CompoundTag(root);
    }

    @Test
    void registryData765To766ProducesOnePacketPerRegistry() {
        byte[] codec = Nbt.writeAnonymous(testCodec());
        List<byte[]> packets = PlayTranslator.translateRegistryData765To766(codec);
        assertEquals(2, packets.size());
        // 各パケットは {id: string, entries: [{key, value}]}。
        for (byte[] packet : packets) {
            Reader reader = new Reader(packet);
            String id = reader.readString();
            assertTrue(id.equals("minecraft:dimension_type")
                    || id.equals("minecraft:worldgen/biome"), id);
            int count = reader.readVarInt();
            assertEquals(1, count);
            String key = reader.readString();
            assertTrue(key.equals("minecraft:overworld")
                    || key.equals("minecraft:plains"), key);
            assertTrue(reader.readBoolean());
            assertNotNull(reader.readAnonymousNbt());
            assertEquals(0, reader.remaining());
        }
    }

    @Test
    void registryData766To765RoundTrip() {
        byte[] codec = Nbt.writeAnonymous(testCodec());
        List<byte[]> packets = PlayTranslator.translateRegistryData765To766(codec);
        for (byte[] packet : packets) {
            byte[] back = PlayTranslator.translateRegistryData766To765(packet);
            Map<String, Tag.CompoundTag> registries = PlayTranslator.parseRegistryData(back);
            assertEquals(1, registries.size());
            String id = registries.keySet().iterator().next();
            Tag.CompoundTag registry = registries.get(id);
            List<Tag> entries = registry.getList("value")
                    .map(Tag.ListTag::value).orElse(List.of());
            assertEquals(1, entries.size());
            assertTrue(entries.get(0) instanceof Tag.CompoundTag compound
                    && compound.getString("name").isPresent());
        }
    }

    // ------------------------------------------------------------------
    // Join Game（763／765 ⇄ 766、763 ⇄ 764）
    // ------------------------------------------------------------------

    /** 763の Join Game（codec は名前付き NBT）。 */
    private static byte[] joinGame763Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        writer.writeByte(0);
        writer.writeByte(-1);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", testCodec());
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 764／765の Join Game。 */
    private static byte[] joinGame765Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false); // doLimitedCrafting
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 766の Join Game。 */
    private static byte[] joinGame766Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false); // doLimitedCrafting
        writer.writeVarInt(0); // dimension（overworld）
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeBoolean(false); // enforcesSecureChat
        return writer.bytes();
    }

    private static Map<String, Tag.CompoundTag> testRegistries() {
        return PlayTranslator.parseRegistryData(Nbt.writeAnonymous(testCodec()));
    }

    @Test
    void joinGame763To766AndBack() {
        JoinGame763 join763 = PlayTranslator.parseJoinGame763(joinGame763Payload());
        assertEquals("minecraft:overworld", join763.worldType());
        byte[] to766 = PlayTranslator.translateJoinGame763To766(join763, testRegistries());
        JoinGame766 join766 = PlayTranslator.parseJoinGame766(to766);
        assertEquals(42, join766.entityId());
        assertEquals(0, join766.dimensionId());
        assertEquals("minecraft:overworld", join766.worldName());
        assertEquals(99L, join766.hashedSeed());
        assertFalse(join766.doLimitedCrafting());
        assertFalse(join766.enforcesSecureChat());
        byte[] back = PlayTranslator.translateJoinGame766To763(join766, testRegistries());
        JoinGame763 join763Back = PlayTranslator.parseJoinGame763(back);
        assertEquals(42, join763Back.entityId());
        assertEquals("minecraft:overworld", join763Back.worldType());
        assertEquals("minecraft:overworld", join763Back.worldName());
        assertEquals(99L, join763Back.hashedSeed());
        assertNotNull(join763Back.codec());
    }

    @Test
    void joinGame765To766AndBack() {
        JoinGame765 join765 = PlayTranslator.parseJoinGame765(joinGame765Payload());
        byte[] to766 = PlayTranslator.translateJoinGame765To766(join765, testRegistries());
        JoinGame766 join766 = PlayTranslator.parseJoinGame766(to766);
        assertEquals(42, join766.entityId());
        assertEquals(0, join766.dimensionId());
        assertFalse(join766.enforcesSecureChat());
        byte[] back = PlayTranslator.translateJoinGame766To765(join766, testRegistries());
        JoinGame765 join765Back = PlayTranslator.parseJoinGame765(back);
        assertEquals(42, join765Back.entityId());
        assertEquals("minecraft:overworld", join765Back.worldType());
        assertEquals(99L, join765Back.hashedSeed());
    }

    @Test
    void joinGame763To764() {
        JoinGame763 join763 = PlayTranslator.parseJoinGame763(joinGame763Payload());
        byte[] to764 = PlayTranslator.translateJoinGame763To764(join763);
        JoinGame765 join764 = PlayTranslator.parseJoinGame765(to764);
        assertEquals(42, join764.entityId());
        assertEquals("minecraft:overworld", join764.worldType());
        assertEquals("minecraft:overworld", join764.worldName());
        assertEquals(99L, join764.hashedSeed());
        assertFalse(join764.doLimitedCrafting());
    }

    // ------------------------------------------------------------------
    // Respawn（766 ⇄ 763／765）
    // ------------------------------------------------------------------

    /** 766の Respawn（SpawnInfo＋copyMetadata u8）。 */
    private static byte[] respawn766Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // dimension（overworld）
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false); // hasDeath
        writer.writeVarInt(0); // portalCooldown
        writer.writeByte(1); // copyMetadata
        return writer.bytes();
    }

    @Test
    void respawn766To763MovesCopyMetadata() {
        byte[] out = PlayTranslator.translateRespawn766To763(
                respawn766Payload(), testRegistries());
        Reader reader = new Reader(out);
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(99L, reader.readLong());
        assertEquals(0, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean()); // copyMetadata（isFlat の直後）
        assertFalse(reader.readBoolean()); // hasDeath
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void respawn765To766AndBack() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false); // hasDeath
        writer.writeVarInt(0);
        writer.writeBoolean(true); // copyMetadata
        byte[] to766 = PlayTranslator.translateRespawn765To766(
                writer.bytes(), testRegistries());
        assertArrayEquals(respawn766Payload(), to766);
        byte[] back = PlayTranslator.translateRespawn766To765(to766, testRegistries());
        assertArrayEquals(writer.bytes(), back);
    }

    // ------------------------------------------------------------------
    // チャットコマンド（766 ⇄ 765）
    // ------------------------------------------------------------------

    @Test
    void chatCommand766To765SynthesizesSignedForm() {
        Writer writer = new Writer();
        writer.writeString("hello");
        byte[] out = PlayTranslator.translateChatCommand766To765(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("hello", reader.readString());
        assertTrue(reader.readLong() > 0); // タイムスタンプ
        assertEquals(0L, reader.readLong()); // salt
        assertEquals(0, reader.readVarInt()); // argumentSignatures
        assertEquals(0, reader.readVarInt()); // messageCount
        assertEquals(0, reader.readUnsignedByte()); // acknowledged（3バイト）
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }
}
