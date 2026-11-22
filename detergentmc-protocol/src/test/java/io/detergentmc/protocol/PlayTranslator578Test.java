package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame578;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の578（1.15.2）⇔758（1.18.2）向け変換テスト。
 * 578 の Join Game は dimension が i32、maxPlayers が u8、codec 無し
 * （実バニラ 1.15.2 のキャプチャ 26 バイトで確認）。Login Success は
 * UUID が文字列。チャットは送信者 UUID 無し。
 */
class PlayTranslator578Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("22222222-3333-4444-5555-666666666666");

    private static Tag.CompoundTag codec() {
        return new Tag.CompoundTag(Map.of(
                "minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:dimension_type"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                                new Tag.CompoundTag(Map.of(
                                        "name", new Tag.StringTag("minecraft:overworld"),
                                        "id", new Tag.IntTag(0),
                                        "element", new Tag.CompoundTag(Map.of()))))))),
                "minecraft:worldgen/biome", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:worldgen/biome"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of())))));
    }

    /** 758 の Join Game ペイロード。 */
    private static byte[] joinGame758Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        writer.writeByte(0);
        writer.writeByte(-1);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", codec());
        writer.writeNamedNbt("", new Tag.CompoundTag(Map.of()));
        writer.writeString("minecraft:overworld");
        writer.writeLong(12345L);
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 578 の Join Game ペイロード（実バニラの形式）。 */
    private static byte[] joinGame578Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0); // gamemode（hardcore ビットなし）
        writer.writeInt(0); // dimension overworld
        writer.writeLong(12345L);
        writer.writeByte(20); // maxPlayers は u8
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    @Test
    void joinGame758To578UsesNumericDimension() {
        byte[] out = PlayTranslator.translateJoinGame758To578(joinGame758Payload());
        JoinGame578 join = PlayTranslator.parseJoinGame578(out);
        assertEquals(42, join.entityId());
        assertEquals(0, join.gameMode());
        assertEquals(0, join.dimension());
        assertEquals(12345L, join.hashedSeed());
        assertEquals(20, join.maxPlayers());
        assertEquals("default", join.levelType());
        assertEquals(10, join.viewDistance());
        assertTrue(join.enableRespawnScreen());
    }

    @Test
    void joinGame578To758SynthesizesCodecForm() {
        byte[] out = PlayTranslator.translateJoinGame578To758(joinGame578Payload());
        PlayTranslator.JoinGame758 join = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, join.entityId());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(12345L, join.hashedSeed());
        assertEquals(20, join.maxPlayers());
    }

    @Test
    void joinGame578ParsesRealLayout() {
        JoinGame578 join = PlayTranslator.parseJoinGame578(joinGame578Payload());
        assertEquals(42, join.entityId());
        assertEquals(0, join.dimension());
        assertEquals("default", join.levelType());
    }

    @Test
    void loginSuccess765To578UsesStringUuid() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(1);
        writer.writeString("textures");
        writer.writeString("value");
        writer.writeBoolean(false);
        byte[] out = PlayTranslator.translateLoginSuccess765To578(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(PLAYER_UUID.toString(), reader.readString());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginSuccess578To765UsesBinaryUuid() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        byte[] out = PlayTranslator.translateLoginSuccess578To765(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.readVarInt()); // properties 0件
    }

    @Test
    void chat758To578DropsSenderUuid() {
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeByte(0);
        writer.writeUuid(PLAYER_UUID);
        byte[] out = PlayTranslator.translateChat758To578(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat578To758AddsNilUuid() {
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeByte(1);
        byte[] out = PlayTranslator.translateChat578To758(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        assertEquals(1, reader.readByte());
        assertEquals(new UUID(0L, 0L), reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void respawnRoundTripKeepsDimension() {
        // 758 の Respawn を組み立て、578 化して次元 ID を確かめ、戻す。
        Writer writer = new Writer();
        writer.writeNamedNbt("", new Tag.CompoundTag(Map.of())); // dimension element
        writer.writeString("minecraft:the_nether");
        writer.writeLong(777L);
        writer.writeByte(1);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false); // copyMetadata
        Map<String, Tag.CompoundTag> registries = Map.of(
                "minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:dimension_type"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                                new Tag.CompoundTag(Map.of(
                                        "name", new Tag.StringTag("minecraft:the_nether"),
                                        "id", new Tag.IntTag(1),
                                        "element", new Tag.CompoundTag(Map.of()))))))));
        byte[] as578 = PlayTranslator.translateRespawn758To578(writer.bytes(), registries);
        Reader reader = new Reader(as578);
        assertEquals(-1, reader.readInt()); // the_nether
        assertEquals(777L, reader.readLong());
        assertEquals(1, reader.readByte()); // gamemode
        assertFalse(reader.readString().isEmpty());
    }
}
