package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import io.detergentmc.protocol.PlayTranslator.JoinGame763;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の759（1.19）⇔758（1.18.2）向け変換テスト。
 *
 * <p>758にだけある旧式チャット（クライアント行き 0x0f／サーバー行き 0x03）は
 * message 文字列＋position＋sender UUID の形式。759の Player Chat は初代の
 * インライン署名形式（signedChatContent・unsigned・type・senderUuid・
 * senderName・senderTeam・timestamp・salt・signature）、System Chat は
 * content＋type varint。758→759 のサーバー行きチャットは「/」で始まれば
 * Chat Command、それ以外は Chat Message（timestamp は現在時刻、salt=0、
 * 署名は空、signedPreview=false）になる。Join Game は 758 が dimension を
 * NBT で送り、759 が worldType 文字列＋末尾の death option で送る。
 * portalCooldown は758・759とも無い。Login Start は 758 が名前だけ、
 * 759 が名前＋署名データ option、760 がさらに任意UUID、763 が名前＋bool＋
 * UUID、765 が名前＋UUID。Login Success は 758 が UUID＋名前だけ。
 * Player Info は 759 の add_player に crypto option が付く。
 */
class PlayTranslator758Test {

    private static final UUID SENDER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PLAYER_UUID = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final byte[] SIG_256 = new byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            SIG_256[i] = (byte) i;
        }
    }

    /** dimension_type レジストリ（overworld だけ。element は空の compound）。 */
    private static Tag.CompoundTag dimensionTypeRegistry() {
        return new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:dimension_type"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of(
                                "name", new Tag.StringTag("minecraft:overworld"),
                                "id", new Tag.IntTag(0),
                                "element", new Tag.CompoundTag(Map.of())))))));
    }

    /** codec（dimension_type＋worldgen/biome）。 */
    private static Tag.CompoundTag codec() {
        return new Tag.CompoundTag(Map.of(
                "minecraft:dimension_type", dimensionTypeRegistry(),
                "minecraft:worldgen/biome", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:worldgen/biome"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of())))));
    }

    /** Respawn 用の registries（dimension_type だけ）。 */
    private static Map<String, Tag.CompoundTag> registries() {
        return Map.of("minecraft:dimension_type", dimensionTypeRegistry());
    }

    /** 759 の Join Game ペイロード（parseJoinGame762 のレイアウト）。 */
    private static byte[] joinGame759Payload(boolean hasDeath) {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false); // hardcore
        writer.writeByte(0); // gamemode
        writer.writeByte(-1); // previousGamemode
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", codec());
        writer.writeString("minecraft:overworld"); // worldType
        writer.writeString("minecraft:overworld"); // worldName
        writer.writeLong(12345L); // hashedSeed
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebug
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(hasDeath); // death
        if (hasDeath) {
            writer.writeString("minecraft:the_nether");
            writer.writePositionRaw(0x123456789L);
        }
        return writer.bytes();
    }

    /** 758 の Join Game ペイロード（parseJoinGame758 のレイアウト）。 */
    private static byte[] joinGame758Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false); // hardcore
        writer.writeByte(0); // gamemode
        writer.writeByte(-1); // previousGamemode
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", codec());
        writer.writeNamedNbt("", new Tag.CompoundTag(Map.of())); // dimension（overworld の element）
        writer.writeString("minecraft:overworld"); // worldName
        writer.writeLong(12345L); // hashedSeed
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebug
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        return writer.bytes();
    }

    /** 759 の Player Chat ペイロード。 */
    private static byte[] playerChat759Payload(String signed, String unsigned) {
        Writer writer = new Writer();
        writer.writeString(signed); // signedChatContent
        writer.writeBoolean(unsigned != null); // unsigned あり
        if (unsigned != null) {
            writer.writeString(unsigned);
        }
        writer.writeVarInt(0); // type（chat_type）
        writer.writeUuid(SENDER);
        writer.writeString("Steve"); // senderName
        writer.writeBoolean(false); // senderTeam 無し
        writer.writeLong(123456789L); // timestamp
        writer.writeLong(987654321L); // salt
        writer.writeByteArray(SIG_256); // signature
        return writer.bytes();
    }

    // ---- チャット（クライアント行き） ----

    @Test
    void playerChat759To758UsesUnsignedWhenPresent() {
        byte[] translated = PlayTranslator.translatePlayerChat759To758(
                playerChat759Payload("hello", "{\"text\":\"hi\"}"));
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"hi\"}", reader.readString()); // unsigned を使う
        assertEquals(0, reader.readByte()); // position: chat
        assertEquals(SENDER, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChat759To758WrapsSignedWhenUnsignedAbsent() {
        byte[] translated = PlayTranslator.translatePlayerChat759To758(
                playerChat759Payload("hello", null));
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"hello\"}", reader.readString()); // 平文を JSON に包む
        assertEquals(0, reader.readByte()); // position: chat
        assertEquals(SENDER, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void systemChat759To758KeepsTypeAsPosition() {
        Writer payload = new Writer();
        payload.writeString("{\"text\":\"sys\"}"); // content（JSON）
        payload.writeVarInt(1); // type: system
        byte[] translated = PlayTranslator.translateSystemChat759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"sys\"}", reader.readString());
        assertEquals(1, reader.readByte()); // position = type（0/1/2 同一対応）
        assertEquals(new UUID(0L, 0L), reader.readUuid()); // sender は nil UUID
        assertEquals(0, reader.remaining());
    }

    // ---- チャット（サーバー行き） ----

    @Test
    void chat758ToChatMessage759FillsTimestampSaltAndEmptySignature() {
        Writer payload = new Writer();
        payload.writeString("hello");
        byte[] translated = PlayTranslator.translateChat758ToChatMessage759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("hello", reader.readString());
        assertTrue(reader.readLong() > 0L); // timestamp（現在時刻）
        assertEquals(0L, reader.readLong()); // salt
        assertArrayEquals(new byte[0], reader.readByteArray()); // 署名は空
        assertFalse(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat758ToChatCommand759StripsSlash() {
        Writer payload = new Writer();
        payload.writeString("/say hi");
        byte[] translated = PlayTranslator.translateChat758ToChatCommand759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("say hi", reader.readString()); // 先頭の "/" を除く
        assertTrue(reader.readLong() > 0L); // timestamp
        assertEquals(0L, reader.readLong()); // salt
        assertEquals(0, reader.readVarInt()); // argumentSignatures（空）
        assertFalse(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.remaining());
    }

    // ---- Join Game ----

    @Test
    void joinGame759To758ConvertsDimensionToNbtAndDropsDeath() {
        byte[] translated = PlayTranslator.translateJoinGame759To758(joinGame759Payload(true));
        JoinGame758 join = PlayTranslator.parseJoinGame758(translated);
        assertEquals(42, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals(List.of("minecraft:overworld"), join.worldNames());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(12345L, join.hashedSeed());
        assertEquals(20, join.maxPlayers());
        assertEquals(10, join.viewDistance());
        assertEquals(10, join.simulationDistance());
        assertFalse(join.reducedDebug());
        assertTrue(join.enableRespawnScreen());
        assertFalse(join.isDebug());
        assertFalse(join.isFlat());
        // dimension は codec 内の overworld の element（空の compound）。
        assertEquals(new Tag.CompoundTag(Map.of()), join.dimension());
    }

    @Test
    void joinGame758To759ConvertsDimensionToNameAndAddsAbsentDeath() {
        byte[] translated = PlayTranslator.translateJoinGame758To759(joinGame758Payload());
        JoinGame763 join = PlayTranslator.parseJoinGame762(translated);
        assertEquals(42, join.entityId());
        assertEquals("minecraft:overworld", join.worldType());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(12345L, join.hashedSeed());
        assertEquals(20, join.maxPlayers());
        assertFalse(join.hasDeath()); // death は absent を補う
        assertEquals(0, join.portalCooldown());
    }

    @Test
    void joinGame759To758BrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translateJoinGame759To758(broken));
        assertArrayEquals(broken, PlayTranslator.translateJoinGame758To759(broken));
    }

    // ---- Respawn ----

    @Test
    void respawn759To758ConvertsDimensionToNbtAndDropsDeath() {
        Writer payload = new Writer();
        payload.writeString("minecraft:overworld"); // worldType
        payload.writeString("minecraft:overworld"); // worldName
        payload.writeLong(999L); // hashedSeed
        payload.writeByte(0); // gamemode
        payload.writeByte(-1); // previousGamemode
        payload.writeBoolean(false); // isDebug
        payload.writeBoolean(false); // isFlat
        payload.writeBoolean(true); // copyMetadata
        payload.writeBoolean(true); // death あり
        payload.writeString("minecraft:the_nether");
        payload.writePositionRaw(0x123456789L);
        byte[] translated = PlayTranslator.translateRespawn759To758(payload.bytes(), registries());
        Reader reader = new Reader(translated);
        assertEquals(new Tag.CompoundTag(Map.of()), reader.readNamedNbt().tag()); // dimension（NBT）
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(999L, reader.readLong());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(-1, reader.readByte());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.remaining()); // death は落とされる
    }

    @Test
    void respawn758To759ConvertsDimensionToNameAndAddsAbsentDeath() {
        Writer payload = new Writer();
        payload.writeNamedNbt("", new Tag.CompoundTag(Map.of())); // dimension（overworld の element）
        payload.writeString("minecraft:overworld"); // worldName
        payload.writeLong(999L); // hashedSeed
        payload.writeByte(0); // gamemode
        payload.writeByte(-1); // previousGamemode
        payload.writeBoolean(false); // isDebug
        payload.writeBoolean(false); // isFlat
        payload.writeBoolean(true); // copyMetadata
        byte[] translated = PlayTranslator.translateRespawn758To759(payload.bytes(), registries());
        Reader reader = new Reader(translated);
        assertEquals("minecraft:overworld", reader.readString()); // worldType
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(999L, reader.readLong());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(-1, reader.readByte());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean()); // death: absent
        assertEquals(0, reader.remaining());
    }

    // ---- Login Start ----

    @Test
    void loginStart758To759AddsFalseSignatureOption() {
        Writer payload = new Writer();
        payload.writeString("Steve"); // 758 は名前だけ
        byte[] translated = PlayTranslator.translateLoginStart758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // 署名データ無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart759To758KeepsNameOnly() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(false); // 署名データ無し
        byte[] translated = PlayTranslator.translateLoginStart759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart758To763AddsTrueAndUuid() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        byte[] translated = PlayTranslator.translateLoginStart758To763(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertTrue(reader.readBoolean()); // UUID あり
        // 758には UUID が無いため、オフラインUUID（"OfflinePlayer:Steve" の v3）を補う。
        assertEquals(PlayTranslator.offlineUuid("Steve"), reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart763To758KeepsNameOnly() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(true);
        payload.writeUuid(PLAYER_UUID);
        byte[] translated = PlayTranslator.translateLoginStart763To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart758To765AddsUuid() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        byte[] translated = PlayTranslator.translateLoginStart758To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        // 758には UUID が無いため、オフラインUUID（"OfflinePlayer:Steve" の v3）を補う。
        assertEquals(PlayTranslator.offlineUuid("Steve"), reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    // ---- Login Success ----

    @Test
    void loginSuccess765To758DropsProperties() {
        Writer payload = new Writer();
        payload.writeUuid(PLAYER_UUID);
        payload.writeString("Steve");
        payload.writeVarInt(1); // properties 1件
        payload.writeString("textures");
        payload.writeString("value");
        payload.writeBoolean(true); // signature あり
        payload.writeString("sig");
        byte[] translated = PlayTranslator.translateLoginSuccess765To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining()); // properties 配列は落とされる
    }

    // ---- Player Info ----

    @Test
    void playerInfo759To758DropsCrypto() {
        Writer payload = new Writer();
        payload.writeVarInt(0); // action: add_player
        payload.writeVarInt(1); // 1件
        payload.writeUuid(PLAYER_UUID);
        payload.writeString("Steve");
        payload.writeVarInt(0); // properties 0件
        payload.writeVarInt(0); // gamemode
        payload.writeVarInt(50); // ping
        payload.writeBoolean(true); // displayName あり
        payload.writeString("{\"text\":\"Steve\"}");
        payload.writeBoolean(true); // crypto あり（759 にだけある）
        payload.writeLong(123456789L); // timestamp
        payload.writeByteArray(new byte[]{0x01, 0x02}); // publicKey
        payload.writeByteArray(SIG_256); // signature
        byte[] translated = PlayTranslator.translatePlayerInfo759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(50, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Steve\"}", reader.readString());
        assertEquals(0, reader.remaining()); // crypto は落とされる
    }

    @Test
    void playerInfo758To759AddsAbsentCrypto() {
        Writer payload = new Writer();
        payload.writeVarInt(0); // action: add_player
        payload.writeVarInt(1); // 1件
        payload.writeUuid(PLAYER_UUID);
        payload.writeString("Steve");
        payload.writeVarInt(0); // properties 0件
        payload.writeVarInt(0); // gamemode
        payload.writeVarInt(50); // ping
        payload.writeBoolean(false); // displayName 無し
        byte[] translated = PlayTranslator.translatePlayerInfo758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(50, reader.readVarInt());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean()); // crypto: absent
        assertEquals(0, reader.remaining());
    }
}
