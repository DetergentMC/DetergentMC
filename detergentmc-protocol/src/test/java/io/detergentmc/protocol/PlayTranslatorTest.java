package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ペイロード変換の検証。765／763のフィールド順は minecraft-data の
 * protocol.json（1.20.3／1.20）から取った。
 */
class PlayTranslatorTest {

    // ------------------------------------------------------------------
    // ハンドシェイク
    // ------------------------------------------------------------------

    private static byte[] handshakePayload(int protocol, int nextState) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    @Test
    void handshakeParsesAndRewrites763To765() {
        byte[] payload = handshakePayload(763, 2);
        PlayTranslator.Handshake handshake = PlayTranslator.parseHandshake(payload);
        assertEquals(763, handshake.protocol());
        assertEquals("localhost", handshake.address());
        assertEquals(25565, handshake.port());
        assertEquals(2, handshake.nextState());

        byte[] rewritten = PlayTranslator.rewriteHandshakeProtocol(payload, 765);
        assertEquals(765, PlayTranslator.parseHandshake(rewritten).protocol());
        assertEquals(2, PlayTranslator.parseHandshake(rewritten).nextState());
    }

    @Test
    void handshake765IsUnchangedByRewriteTo765() {
        byte[] payload = handshakePayload(765, 2);
        assertArrayEquals(payload, PlayTranslator.rewriteHandshakeProtocol(payload, 765));
    }

    @Test
    void handshakeWithTrailingBytesThrows() {
        Writer writer = new Writer();
        writer.writeVarInt(763);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(2);
        writer.writeByte(0x7F);
        assertThrows(IllegalArgumentException.class,
                () -> PlayTranslator.parseHandshake(writer.bytes()));
    }

    // ------------------------------------------------------------------
    // ステータス JSON
    // ------------------------------------------------------------------

    @Test
    void statusJsonRewritesVersionOnly() {
        String json = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0},"
                + "\"description\":{\"text\":\"1.20.4 server\"}}";
        String rewritten = PlayTranslator.rewriteStatusJsonFor763(json);
        assertTrue(rewritten.contains("\"protocol\":763"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.20.1\""), rewritten);
        // 説明文の中の 1.20.4 は触らない。
        assertTrue(rewritten.contains("1.20.4 server"), rewritten);
        assertFalse(rewritten.contains("\"protocol\":765"));
    }

    @Test
    void statusJsonWithoutVersionIsUnchanged() {
        assertEquals("{}", PlayTranslator.rewriteStatusJsonFor763("{}"));
        assertNull(PlayTranslator.rewriteStatusJsonFor763(null));
    }

    // ------------------------------------------------------------------
    // ログイン
    // ------------------------------------------------------------------

    @Test
    void offlineUuidIsDeterministicAndVersion3() {
        UUID first = PlayTranslator.offlineUuid("Steve");
        UUID second = PlayTranslator.offlineUuid("Steve");
        assertEquals(first, second);
        assertEquals(3, first.version());
        assertEquals(2, first.variant());
        assertFalse(first.equals(PlayTranslator.offlineUuid("Alex")));
    }

    @Test
    void loginStart763WithUuidStripsTheBoolean() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeBoolean(true);
        writer.writeUuid(uuid);
        byte[] converted = PlayTranslator.translateLoginStart763To765(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("Steve", reader.readString());
        assertEquals(uuid, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart763WithoutUuidSynthesizesOfflineUuid() {
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeBoolean(false);
        byte[] converted = PlayTranslator.translateLoginStart763To765(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("Steve", reader.readString());
        assertEquals(PlayTranslator.offlineUuid("Steve"), reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginDisconnectPayloadIsJsonText() {
        byte[] payload = PlayTranslator.loginDisconnectPayload("あいう");
        assertEquals("あいう", new Reader(payload).readString().replace("\"", ""));
        assertEquals("\"あいう\"", new Reader(payload).readString());
    }

    // ------------------------------------------------------------------
    // Registry Data / Join Game
    // ------------------------------------------------------------------

    static Tag.CompoundTag registryEntry(String name, int id, String key, String value) {
        return new Tag.CompoundTag(Map.of(
                "name", new Tag.StringTag(name),
                "id", new Tag.IntTag(id),
                "element", new Tag.CompoundTag(Map.of(key, new Tag.StringTag(value)))));
    }

    static Map<String, Tag.CompoundTag> sampleRegistries() {
        Tag.CompoundTag dimensionType = new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:dimension_type"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        registryEntry("minecraft:overworld", 0, "ultrawarm", "false"),
                        registryEntry("minecraft:the_nether", 1, "ultrawarm", "true")))));
        Tag.CompoundTag biome = new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:worldgen/biome"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        registryEntry("minecraft:plains", 0, "precipitation", "rain")))));
        return Map.of("minecraft:dimension_type", dimensionType,
                "minecraft:worldgen/biome", biome);
    }

    @Test
    void registryDataNewFormatParsesAllRegistries() {
        Map<String, Tag> root = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Tag.CompoundTag> entry : sampleRegistries().entrySet()) {
            root.put(entry.getKey(), entry.getValue());
        }
        byte[] payload = Nbt.writeAnonymous(new Tag.CompoundTag(root));
        Map<String, Tag.CompoundTag> parsed = PlayTranslator.parseRegistryData(payload);
        assertEquals(sampleRegistries(), parsed);
    }

    @Test
    void registryDataLegacyFormatBuildsCodec() {
        Writer writer = new Writer();
        writer.writeString("minecraft:dimension_type");
        writer.writeVarInt(1);
        writer.writeString("minecraft:overworld");
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("ultrawarm", new Tag.StringTag("false"))));
        Map<String, Tag.CompoundTag> parsed = PlayTranslator.parseRegistryData(writer.bytes());
        Tag.CompoundTag codec = parsed.get("minecraft:dimension_type");
        assertEquals("minecraft:dimension_type",
                codec.getString("type").orElseThrow());
        Tag.ListTag values = codec.getList("value").orElseThrow();
        assertEquals(1, values.value().size());
        Tag.CompoundTag entry = (Tag.CompoundTag) values.value().get(0);
        assertEquals("minecraft:overworld", entry.getString("name").orElseThrow());
        assertEquals(0, ((Tag.IntTag) entry.get("id").orElseThrow()).value());
    }

    @Test
    void joinGameCodecKeepsOnlyDimensionTypeAndBiome() {
        Map<String, Tag> root = new java.util.LinkedHashMap<>(sampleRegistries());
        root.put("minecraft:chat_type", new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:chat_type"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of()))));
        Tag.CompoundTag codec = PlayTranslator.buildJoinGameCodec(
                PlayTranslator.parseRegistryData(Nbt.writeAnonymous(new Tag.CompoundTag(root))));
        assertEquals(2, codec.value().size());
        assertTrue(codec.value().containsKey("minecraft:dimension_type"));
        assertTrue(codec.value().containsKey("minecraft:worldgen/biome"));
        assertFalse(codec.value().containsKey("minecraft:chat_type"));
    }

    private static byte[] joinGame765Payload() {
        Writer writer = new Writer();
        writer.writeInt(42); // entityId
        writer.writeBoolean(false); // hardcore
        PacketIO.writeStringList(writer, List.of("minecraft:overworld", "minecraft:the_nether"));
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebug
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // doLimitedCrafting（763には無い）
        writer.writeString("minecraft:overworld"); // worldType
        writer.writeString("minecraft:overworld"); // worldName
        writer.writeLong(12345L); // hashedSeed
        writer.writeByte(0); // gameMode
        writer.writeByte(-1); // previousGameMode
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // death なし
        writer.writeVarInt(0); // portalCooldown
        return writer.bytes();
    }

    @Test
    void joinGame765To763ReordersAndInsertsCodec() {
        byte[] converted = PlayTranslator.translateJoinGame765To763(
                joinGame765Payload(), sampleRegistries());
        Reader reader = new Reader(converted);
        assertEquals(42, reader.readInt());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(-1, reader.readByte());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"),
                PacketIO.readStringList(reader));
        Nbt.NamedResult codec = reader.readNamedNbt();
        assertEquals("", codec.name());
        Tag.CompoundTag codecTag = (Tag.CompoundTag) codec.tag();
        assertEquals(2, codecTag.value().size());
        assertTrue(codecTag.value().containsKey("minecraft:dimension_type"));
        assertTrue(codecTag.value().containsKey("minecraft:worldgen/biome"));
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(12345L, reader.readLong());
        assertEquals(20, reader.readVarInt());
        assertEquals(10, reader.readVarInt());
        assertEquals(10, reader.readVarInt());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void joinGame765WithDeathLocationRoundTrips() {
        Writer writer = new Writer();
        writer.writeInt(1);
        writer.writeBoolean(true);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(1);
        writer.writeVarInt(2);
        writer.writeVarInt(2);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(7L);
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(true); // death あり
        writer.writeString("minecraft:the_nether");
        writer.writeLong(0x123456789ABCDEFL); // Position の生 i64
        writer.writeVarInt(5);
        byte[] converted = PlayTranslator.translateJoinGame765To763(
                writer.bytes(), sampleRegistries());
        Reader reader = new Reader(converted);
        reader.readInt();
        reader.readBoolean();
        reader.readUnsignedByte();
        reader.readByte();
        PacketIO.readStringList(reader);
        reader.readNamedNbt();
        reader.readString();
        reader.readString();
        reader.readLong();
        reader.readVarInt();
        reader.readVarInt();
        reader.readVarInt();
        reader.readBoolean();
        reader.readBoolean();
        reader.readBoolean();
        reader.readBoolean();
        assertTrue(reader.readBoolean());
        assertEquals("minecraft:the_nether", reader.readString());
        assertEquals(0x123456789ABCDEFL, reader.readPositionRaw());
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // チャット
    // ------------------------------------------------------------------

    @Test
    void chatMessageStringOnlyIsSynthesizedTo765() {
        Writer writer = new Writer();
        writer.writeString("hello world");
        byte[] converted = PlayTranslator.translateChatMessage763To765(
                writer.bytes(), 1_700_000_000_000L);
        Reader reader = new Reader(converted);
        assertEquals("hello world", reader.readString());
        assertEquals(1_700_000_000_000L, reader.readLong());
        assertEquals(0L, reader.readLong());
        assertFalse(reader.readBoolean()); // 署名なし
        assertEquals(0, reader.readVarInt());
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3));
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatMessageFullFormPassesThrough() {
        Writer writer = new Writer();
        writer.writeString("hello");
        writer.writeLong(111L);
        writer.writeLong(222L);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeBytes(new byte[]{0, 0, 0});
        assertArrayEquals(writer.bytes(),
                PlayTranslator.translateChatMessage763To765(writer.bytes()));
    }

    @Test
    void systemChatNbtToJsonString() {
        Map<String, Tag> fields = new java.util.LinkedHashMap<>();
        fields.put("text", new Tag.StringTag("Server restarted"));
        fields.put("color", new Tag.StringTag("yellow"));
        Writer writer = new Writer();
        writer.writeAnonymousNbt(new Tag.CompoundTag(fields));
        writer.writeBoolean(false);
        byte[] converted = PlayTranslator.translateSystemChat765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("{\"text\":\"Server restarted\",\"color\":\"yellow\"}",
                reader.readString());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChatNbtFieldsToJsonStrings() {
        UUID sender = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer writer = new Writer();
        writer.writeUuid(sender);
        writer.writeVarInt(3);
        writer.writeBoolean(false); // 署名なし
        writer.writeString("hello");
        writer.writeLong(1000L);
        writer.writeLong(0L);
        writer.writeVarInt(0); // previousMessages なし
        writer.writeBoolean(true); // unsigned あり
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of("text", new Tag.StringTag("hi"))));
        writer.writeVarInt(0); // filterType
        writer.writeVarInt(1); // chat type
        writer.writeAnonymousNbt(new Tag.StringTag("Steve"));
        writer.writeBoolean(false); // target なし
        byte[] converted = PlayTranslator.translatePlayerChat765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals(sender, reader.readUuid());
        assertEquals(3, reader.readVarInt());
        assertFalse(reader.readBoolean());
        assertEquals("hello", reader.readString());
        assertEquals(1000L, reader.readLong());
        assertEquals(0L, reader.readLong());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals("\"Steve\"", reader.readString());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void disconnectNbtToJsonString() {
        Writer writer = new Writer();
        writer.writeAnonymousNbt(new Tag.StringTag("Kicked!"));
        byte[] converted = PlayTranslator.translateDisconnect765To763(writer.bytes());
        assertEquals("\"Kicked!\"", new Reader(converted).readString());
    }

    @Test
    void serverDataMotdNbtToJsonString() {
        Writer writer = new Writer();
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("DetergentMC"))));
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        byte[] converted = PlayTranslator.translateServerData765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("{\"text\":\"DetergentMC\"}", reader.readString());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
    }

    @Test
    void playerInfoWithoutDisplayNamePassesThrough() {
        Writer writer = new Writer();
        writer.writeByte(0x01 | 0x08 | 0x10); // add_player + listed + latency
        writer.writeVarInt(1);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        writer.writeString("Steve");
        writer.writeVarInt(0); // properties なし
        writer.writeVarInt(1); // listed
        writer.writeVarInt(50); // latency
        byte[] payload = writer.bytes();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo765To763(payload));
    }

    @Test
    void playerInfoChatSessionOptionIsParsed() {
        // chat_session は option 型：先頭の bool を読まないと後続がずれる。
        // セッションあり（bool true＋UUID＋有効期限＋鍵＋署名）の場合。
        Writer writer = new Writer();
        writer.writeByte(0x02 | 0x10); // initialize_chat + latency
        writer.writeVarInt(1);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        writer.writeLong(1_700_000_000_000L);
        writer.writeByteArray(new byte[]{1, 2, 3});
        writer.writeByteArray(new byte[]{4, 5});
        writer.writeVarInt(42);
        byte[] payload = writer.bytes();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo765To763(payload));

        // セッションなし（bool false だけ）の場合。
        Writer absent = new Writer();
        absent.writeByte(0x02 | 0x10);
        absent.writeVarInt(1);
        absent.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        absent.writeBoolean(false);
        absent.writeVarInt(7);
        byte[] absentPayload = absent.bytes();
        assertArrayEquals(absentPayload,
                PlayTranslator.translatePlayerInfo765To763(absentPayload));
    }

    @Test
    void profilelessChatNbtToJsonStrings() {
        Writer writer = new Writer();
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("hello"))));
        writer.writeVarInt(1);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Steve"))));
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Alex"))));
        byte[] converted = PlayTranslator.translateProfilelessChat765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("{\"text\":\"hello\"}", reader.readString());
        assertEquals(1, reader.readVarInt());
        assertEquals("{\"text\":\"Steve\"}", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Alex\"}", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void deathCombatEventNbtToJsonString() {
        Writer writer = new Writer();
        writer.writeVarInt(42);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Steve was slain"))));
        byte[] converted = PlayTranslator.translateDeathCombatEvent765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals(42, reader.readVarInt());
        assertEquals("{\"text\":\"Steve was slain\"}", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void scoreboardObjectiveNbtToJsonStringAndDropsNumberFormat() {
        Writer writer = new Writer();
        writer.writeString("obj");
        writer.writeByte(0);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Points"))));
        writer.writeVarInt(1);
        writer.writeBoolean(true); // numberFormat あり
        writer.writeVarInt(1); // 固定値
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("styling"))));
        byte[] converted = PlayTranslator.translateScoreboardObjective765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("obj", reader.readString());
        assertEquals(0, reader.readByte());
        assertEquals("{\"text\":\"Points\"}", reader.readString());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.remaining());

        // action 1（削除）は名前と action だけ。
        Writer remove = new Writer();
        remove.writeString("obj");
        remove.writeByte(1);
        byte[] removed = PlayTranslator.translateScoreboardObjective765To763(remove.bytes());
        Reader removedReader = new Reader(removed);
        assertEquals("obj", removedReader.readString());
        assertEquals(1, removedReader.readByte());
        assertEquals(0, removedReader.remaining());
    }

    @Test
    void scoreboardScoreDropsDisplayNameAndNumberFormat() {
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeString("obj");
        writer.writeVarInt(5);
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Steve!"))));
        writer.writeBoolean(true);
        writer.writeVarInt(2); // 空白
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("styling"))));
        byte[] converted = PlayTranslator.translateScoreboardScore765To763(writer.bytes());
        Reader reader = new Reader(converted);
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.readVarInt()); // action 0＝設定
        assertEquals("obj", reader.readString());
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void resetScoreBecomesRemoveScoreboardScore() {
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeBoolean(true);
        writer.writeString("obj");
        assertTrue(PlayTranslator.translateResetScore765To763(writer.bytes()).isPresent());
        byte[] converted = PlayTranslator.translateResetScore765To763(writer.bytes()).orElseThrow();
        Reader reader = new Reader(converted);
        assertEquals("Steve", reader.readString());
        assertEquals(1, reader.readVarInt()); // action 1＝削除
        assertEquals("obj", reader.readString());
        assertEquals(0, reader.remaining());

        // objective 指定なしは763では表せない。
        Writer all = new Writer();
        all.writeString("Steve");
        all.writeBoolean(false);
        assertTrue(PlayTranslator.translateResetScore765To763(all.bytes()).isEmpty());
    }

    @Test
    void playerInfoDisplayNameNbtToJsonString() {
        Writer writer = new Writer();
        writer.writeByte(0x01 | 0x20); // add_player + display_name
        writer.writeVarInt(1);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        writer.writeString("Steve");
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Steve!"))));
        byte[] converted = PlayTranslator.translatePlayerInfo765To763(writer.bytes());
        Reader reader = new Reader(converted);
        reader.readByte();
        reader.readVarInt();
        reader.readUuid();
        reader.readString();
        reader.readVarInt();
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Steve!\"}", reader.readString());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // リソースパック
    // ------------------------------------------------------------------

    @Test
    void configAddResourcePackRoundTripsTo763PlayOffer() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer writer = new Writer();
        writer.writeUuid(uuid);
        writer.writeString("http://127.0.0.1:25567/detergentmc-pack.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Pack?"))));
        PlayTranslator.PendingPack pack =
                PlayTranslator.parseConfigAddResourcePack(writer.bytes());
        assertEquals(uuid, pack.uuid());
        assertEquals("{\"text\":\"Pack?\"}", pack.promptJson());
        byte[] play = PlayTranslator.buildPlayResourcePack763(pack);
        Reader reader = new Reader(play);
        assertEquals("http://127.0.0.1:25567/detergentmc-pack.zip", reader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", reader.readString());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Pack?\"}", reader.readString());
        assertEquals(0, reader.remaining());

        Writer response = new Writer();
        response.writeVarInt(3); // accepted
        byte[] toServer = PlayTranslator.translateResourcePackResponse763To765(
                response.bytes(), pack);
        Reader serverReader = new Reader(toServer);
        assertEquals(uuid, serverReader.readUuid());
        assertEquals(3, serverReader.readVarInt());
    }

    @Test
    void buildPlayResourcePack763UsesOverrideUrlAndSha1() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlayTranslator.PendingPack pack = new PlayTranslator.PendingPack(
                uuid,
                "http://127.0.0.1:25567/detergentmc-pack.zip",
                "0123456789abcdef0123456789abcdef01234567",
                false, "{\\\"text\\\":\\\"Pack?\\\"}");
        // With a 763 override (pack_format 15), the URL and SHA-1 are
        // swapped but forced/prompt still come from the vanilla pack.
        ResourcePack override = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.20.1.zip",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                false, "resource-pack.prompt");
        byte[] play = PlayTranslator.buildPlayResourcePack763(pack, override);
        Reader reader = new Reader(play);
        assertEquals("http://127.0.0.1:25567/detergentmc-pack-1.20.1.zip",
                reader.readString());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", reader.readString());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals("{\\\"text\\\":\\\"Pack?\\\"}", reader.readString());
        assertEquals(0, reader.remaining());

        // Without an override, the vanilla pack is offered as before.
        byte[] plain = PlayTranslator.buildPlayResourcePack763(pack, null);
        Reader plainReader = new Reader(plain);
        assertEquals("http://127.0.0.1:25567/detergentmc-pack.zip", plainReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", plainReader.readString());
        assertFalse(plainReader.readBoolean());
        assertTrue(plainReader.readBoolean());
        assertEquals("{\\\"text\\\":\\\"Pack?\\\"}", plainReader.readString());
        assertEquals(0, plainReader.remaining());
    }

    @Test
    void clientInformationPayloadParsesAs765Settings() {
        byte[] payload = PlayTranslator.clientInformationPayload();
        Reader reader = new Reader(payload);
        assertEquals("ja_JP", reader.readString());
        assertEquals(8, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(0x7F, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }
}
