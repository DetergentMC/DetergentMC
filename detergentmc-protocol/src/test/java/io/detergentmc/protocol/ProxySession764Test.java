package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 764（1.20.2）のプロキシ参加フロー。
 * 763との最大の違い：設定フェーズはプロキシが消費せず中継する。
 * Join Game は765と同形式のため変換なしで通す。
 */
class ProxySession764Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PACK_UUID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID AUTH_UUID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final AuthenticatedProfile PROFILE = new AuthenticatedProfile(
            AUTH_UUID, "Steve", List.of(new AuthenticatedProfile.Property(
                    "textures", "dGV4dHVyZXNWYWx1ZQ==", "c2lnbmF0dXJl")));
    private static final ProxyAuthConfig FAKE_AUTH = ProxyAuthConfig.withVerifier(
            (username, serverIdHash) -> Optional.of(PROFILE));

    private static byte[] handshakePayload(int protocol, int nextState) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    /** 764の Login Start：名前＋UUID必須（763の bool 付きではない）。 */
    private static byte[] loginStart764(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    private static byte[] registryDataPayload() {
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
        return Nbt.writeAnonymous(new Tag.CompoundTag(root));
    }

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
        writer.writeBoolean(false);
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

    /** 765の設定フェーズの Add Resource Pack（UUID付き、プロンプトなし）。 */
    private static byte[] configAddPack765Payload() {
        Writer writer = new Writer();
        writer.writeUuid(PACK_UUID);
        writer.writeString("https://example.invalid/pack.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 765の System Chat（NBT＋bool）。 */
    private static byte[] systemChat765Payload() {
        Writer writer = new Writer();
        writer.writeBytes(Nbt.writeAnonymous(new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("hello")))));
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 765の Respawn（copyMetadata は末尾）。 */
    private static byte[] respawn765Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:the_nether");
        writer.writeString("minecraft:the_nether");
        writer.writeLong(7L);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false); // death なし
        writer.writeVarInt(0); // portalCooldown
        writer.writeBoolean(true); // copyMetadata（末尾）
        return writer.bytes();
    }

    /** 765の Entity Effect（エフェクトID 0＝Speed、factor なし）。 */
    private static byte[] entityEffect765Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(42);
        writer.writeVarInt(0);
        writer.writeByte(1);
        writer.writeVarInt(100);
        writer.writeByte(0);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** Encryption Request から Encryption Response を作る（フェイククライアント役）。 */
    private static byte[] encryptionResponseFor(MinecraftPacket request, byte[] secret) {
        MojangAuth.EncryptionRequest parsed =
                MojangAuth.parseEncryptionRequest(request.payload());
        return MojangAuth.buildEncryptionResponsePayload(
                secret, parsed.verifyToken(),
                MojangAuth.decodePublicKey(parsed.publicKey()));
    }

    /** 764・offline の参加フローを最後まで進めたセッションを作る。 */
    private static ProxySession joined764Session() {
        ProxySession.HandshakeResult handshake = ProxySession.resolveHandshake(
                handshakePayload(764, 2), false);
        assertFalse(handshake.disconnect());
        ProxySession session = handshake.session();
        assertEquals(764, session.clientProtocol());
        assertEquals(765, PlayTranslator.parseHandshake(
                handshake.toServer().payload()).protocol());
        assertFalse(handshake.relay());
        // Login Start は764形式のままバニラへ（変換なし）。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Steve")));
        assertEquals(1, login.toServer().size());
        assertEquals(0x00, login.toServer().get(0).id());
        assertArrayEquals(loginStart764("Steve"), login.toServer().get(0).payload());
        assertEquals("Steve", session.playerName());
        // Login Success：まだログイン状態のまま（Login Acknowledged 待ち）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        assertTrue(success.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());
        assertEquals(ProxySession.ServerState.LOGIN, session.serverState());
        // Login Acknowledged で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        // Registry Data はそのまま中継され、内容も保持される。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x05, registry.toClient().get(0).id());
        assertArrayEquals(registryDataPayload(), registry.toClient().get(0).payload());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));
        // Add Resource Pack は764の単一パケット（0x06、UUIDなし）に変換される。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x07, configAddPack765Payload()));
        assertEquals(1, pack.toClient().size());
        assertEquals(0x06, pack.toClient().get(0).id());
        Reader packReader = new Reader(pack.toClient().get(0).payload());
        assertEquals("https://example.invalid/pack.zip", packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertEquals(false, packReader.readBoolean());
        assertEquals(false, packReader.readBoolean());
        assertEquals(0, packReader.remaining());
        // Resource Pack Response（設定 0x05、結果のみ）→ UUID を付けて765形式に。
        Outcome packResponse = session.handleClientPacket(
                new MinecraftPacket(0x05, new byte[]{0x00}));
        assertEquals(1, packResponse.toServer().size());
        assertEquals(0x05, packResponse.toServer().get(0).id());
        Reader responseReader = new Reader(packResponse.toServer().get(0).payload());
        assertEquals(PACK_UUID, responseReader.readUuid());
        assertEquals(0, responseReader.readVarInt());
        // Finish Configuration：まだ設定フェーズのまま。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x02, finish.toClient().get(0).id());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        // クライアントの応答で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x02, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        return session;
    }

    // ------------------------------------------------------------------
    // ハンドシェイク・ステータス
    // ------------------------------------------------------------------

    @Test
    void handshake764StatusRewritesVersionFor764() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(764, 1), false);
        assertFalse(result.disconnect());
        assertEquals(ProxySession.ClientState.STATUS, result.session().clientState());
        assertEquals(765, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
        String json = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0},\"description\":{\"text\":\"hi\"}}";
        Writer writer = new Writer();
        writer.writeString(json);
        Outcome response = result.session().handleServerPacket(
                new MinecraftPacket(0x00, writer.bytes()));
        assertEquals(1, response.toClient().size());
        String rewritten = new Reader(response.toClient().get(0).payload()).readString();
        assertTrue(rewritten.contains("\"protocol\":764"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.20.2\""), rewritten);
    }

    @Test
    void handshake759IsAccepted() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(759, 2), false);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(759, result.session().clientProtocol());
    }

    // ------------------------------------------------------------------
    // 参加フロー（offline）
    // ------------------------------------------------------------------

    @Test
    void joinGamePassesThroughUnchangedFor764() {
        ProxySession session = joined764Session();
        byte[] payload = joinGame765Payload();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x29, payload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x29, join.toClient().get(0).id());
        assertArrayEquals(payload, join.toClient().get(0).payload());
    }

    @Test
    void spawnPositionIsSpawnSignalFor764() {
        ProxySession session = joined764Session();
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        assertFalse(session.joinFlow().isInGame());
        Writer writer = new Writer();
        writer.writeLong(0L); // spawn position（生の位置）
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, writer.bytes()));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x52, spawn.toClient().get(0).id());
        assertTrue(session.joinFlow().isInGame());
    }

    @Test
    void gameEvent13IsDroppedFor764() {
        ProxySession session = joined764Session();
        // Game Event 13（チャンク待ち開始）は764に無いので落とす。
        Outcome dropped = session.handleServerPacket(
                new MinecraftPacket(0x20, new byte[]{13, 0, 0, 0, 0}));
        assertTrue(dropped.toClient().isEmpty());
        assertTrue(dropped.toServer().isEmpty());
        // それ以外の Game Event は同IDで通す。
        Outcome other = session.handleServerPacket(
                new MinecraftPacket(0x20, new byte[]{0, 0, 0, 0, 0}));
        assertEquals(1, other.toClient().size());
        assertEquals(0x20, other.toClient().get(0).id());
    }

    @Test
    void systemChatIsTranslatedNbtToJsonFor764() {
        ProxySession session = joined764Session();
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x69, systemChat765Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x67, chat.toClient().get(0).id());
        Reader reader = new Reader(chat.toClient().get(0).payload());
        assertTrue(reader.readString().contains("hello"));
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void clientChatPassesThroughUnchangedFor764() {
        ProxySession session = joined764Session();
        Writer writer = new Writer();
        writer.writeString("hello");
        writer.writeLong(123L);
        writer.writeLong(0L);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeBytes(new byte[]{0, 0, 0});
        byte[] payload = writer.bytes();
        Outcome chat = session.handleClientPacket(new MinecraftPacket(0x05, payload));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x05, chat.toServer().get(0).id());
        assertArrayEquals(payload, chat.toServer().get(0).payload());
    }

    @Test
    void clientKeepAliveAndMovementAreRemappedFor764() {
        ProxySession session = joined764Session();
        Outcome keepAlive = session.handleClientPacket(
                new MinecraftPacket(0x14, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}));
        assertEquals(0x15, keepAlive.toServer().get(0).id());
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeBoolean(false);
        Outcome position = session.handleClientPacket(
                new MinecraftPacket(0x16, writer.bytes()));
        assertEquals(0x17, position.toServer().get(0).id());
    }

    @Test
    void respawnAndEntityEffectPassThroughUnchangedFor764() {
        ProxySession session = joined764Session();
        byte[] respawn = respawn765Payload();
        Outcome respawned = session.handleServerPacket(new MinecraftPacket(0x45, respawn));
        assertEquals(0x43, respawned.toClient().get(0).id());
        assertArrayEquals(respawn, respawned.toClient().get(0).payload());
        byte[] effect = entityEffect765Payload();
        Outcome effected = session.handleServerPacket(new MinecraftPacket(0x72, effect));
        assertEquals(0x6E, effected.toClient().get(0).id());
        assertArrayEquals(effect, effected.toClient().get(0).payload());
        // Remove Entity Effect も同IDでそのまま（エフェクトIDは764も0ベース）。
        Writer writer = new Writer();
        writer.writeVarInt(42);
        writer.writeVarInt(0);
        Outcome removed = session.handleServerPacket(
                new MinecraftPacket(0x41, writer.bytes()));
        assertEquals(0x41, removed.toClient().get(0).id());
    }

    @Test
    void playDisconnectIsTranslatedToStringFor764() {
        ProxySession session = joined764Session();
        Writer writer = new Writer();
        writer.writeBytes(Nbt.writeAnonymous(new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("bye")))));
        byte[] payload = writer.bytes();
        Outcome disconnect = session.handleServerPacket(new MinecraftPacket(0x1B, payload));
        assertEquals(0x1B, disconnect.toClient().get(0).id());
        // 764の Disconnect は JSON 文字列のため、765の NBT が文字列に変換される。
        // 素通しすると764クライアントが切断理由を誤読する。
        assertEquals("{\"text\":\"bye\"}",
                new Reader(disconnect.toClient().get(0).payload()).readString());
        assertTrue(disconnect.closeAfterSend());
    }

    @Test
    void configDisconnectIsTranslatedToStringFor764() {
        ProxySession session = joined764Session();
        // 設定フェーズに戻す：Start Configuration を中継して応答する。
        session.handleServerPacket(new MinecraftPacket(0x67, new byte[0]));
        session.handleClientPacket(new MinecraftPacket(0x0B, new byte[0]));
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        Writer writer = new Writer();
        writer.writeBytes(Nbt.writeAnonymous(new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("nope")))));
        Outcome disconnect = session.handleServerPacket(
                new MinecraftPacket(0x01, writer.bytes()));
        assertEquals(0x01, disconnect.toClient().get(0).id());
        assertTrue(new Reader(disconnect.toClient().get(0).payload())
                .readString().contains("nope"));
        assertTrue(disconnect.closeAfterSend());
    }

    @Test
    void resetScoreBecomesScoreboardScoreRemovalFor764() {
        ProxySession session = joined764Session();
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeBoolean(true);
        writer.writeString("objective");
        Outcome reset = session.handleServerPacket(
                new MinecraftPacket(0x42, writer.bytes()));
        assertEquals(1, reset.toClient().size());
        assertEquals(0x5D, reset.toClient().get(0).id());
        Reader reader = new Reader(reset.toClient().get(0).payload());
        assertEquals("Steve", reader.readString());
        assertEquals(1, reader.readVarInt()); // action 1＝削除
        assertEquals("objective", reader.readString());
        // objective 指定なしは落とす。
        Writer noObjective = new Writer();
        noObjective.writeString("Steve");
        noObjective.writeBoolean(false);
        Outcome dropped = session.handleServerPacket(
                new MinecraftPacket(0x42, noObjective.bytes()));
        assertTrue(dropped.toClient().isEmpty());
    }

    @Test
    void declareRecipesAndTagsAreDroppedFor764() {
        ProxySession session = joined764Session();
        Outcome recipes = session.handleServerPacket(
                new MinecraftPacket(0x73, new byte[]{0x00}));
        assertTrue(recipes.toClient().isEmpty());
        Outcome tags = session.handleServerPacket(
                new MinecraftPacket(0x74, new byte[]{0x00}));
        assertTrue(tags.toClient().isEmpty());
        // 設定フェーズの Tags も落とす。
        session.handleServerPacket(new MinecraftPacket(0x67, new byte[0]));
        session.handleClientPacket(new MinecraftPacket(0x0B, new byte[0]));
        Outcome configTags = session.handleServerPacket(
                new MinecraftPacket(0x09, new byte[]{0x00}));
        assertTrue(configTags.toClient().isEmpty());
    }

    @Test
    void playResourcePackIsConvertedAndRemoveIsDroppedFor764() {
        ProxySession session = joined764Session();
        Outcome add = session.handleServerPacket(
                new MinecraftPacket(0x44, configAddPack765Payload()));
        assertEquals(1, add.toClient().size());
        assertEquals(0x42, add.toClient().get(0).id());
        Reader reader = new Reader(add.toClient().get(0).payload());
        assertEquals("https://example.invalid/pack.zip", reader.readString());
        // Remove Resource Pack は764に相当が無いので落とす。
        Writer writer = new Writer();
        writer.writeUuid(PACK_UUID);
        Outcome remove = session.handleServerPacket(new MinecraftPacket(0x43, writer.bytes()));
        assertTrue(remove.toClient().isEmpty());
        // 765だけのパケットも落とす。
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x6E, new byte[]{0x01})).toClient().isEmpty());
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x6F, new byte[]{0x00})).toClient().isEmpty());
        // プレイ状態の Resource Pack Response（764は 0x27、結果のみ）。
        Outcome response = session.handleClientPacket(
                new MinecraftPacket(0x27, new byte[]{0x03}));
        assertEquals(1, response.toServer().size());
        assertEquals(0x28, response.toServer().get(0).id());
        Reader responseReader = new Reader(response.toServer().get(0).payload());
        assertEquals(PACK_UUID, responseReader.readUuid());
        assertEquals(3, responseReader.readVarInt());
    }

    @Test
    void chunkBatchIsRelayedWithSameIdsFor764() {
        ProxySession session = joined764Session();
        assertEquals(0x0D, session.handleServerPacket(
                new MinecraftPacket(0x0D, new byte[]{0x05})).toClient().get(0).id());
        assertEquals(0x0C, session.handleServerPacket(
                new MinecraftPacket(0x0C, new byte[0])).toClient().get(0).id());
        Outcome received = session.handleClientPacket(new MinecraftPacket(0x07, new byte[]{0x02}));
        assertEquals(0x07, received.toServer().get(0).id());
    }

    @Test
    void windowClickIsTranslatedFor764() {
        ProxySession session = joined764Session();
        // 空スロットだけの Window Click（アイテムIDの変換は起きない）。
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeVarInt(1); // stateId
        writer.writeShort((short) 5); // clickedSlot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(0); // count
        writer.writeBoolean(false); // carried：空スロット
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0D, writer.bytes()));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0D, click.toServer().get(0).id());
        assertArrayEquals(writer.bytes(), click.toServer().get(0).payload());
    }

    @Test
    void startConfigurationIsRelayedFor764() {
        ProxySession session = joined764Session();
        // プレイ中の Start Configuration はそのまま中継する（763のような肩代わりはしない）。
        Outcome start = session.handleServerPacket(new MinecraftPacket(0x67, new byte[0]));
        assertEquals(1, start.toClient().size());
        assertEquals(0x65, start.toClient().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        // クライアントの応答で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x0B, new byte[0]));
        assertEquals(0x0B, ack.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // 設定フェーズを往復してプレイに戻る。
        session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x02, new byte[0]));
        assertEquals(0x02, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
    }

    @Test
    void advancementsAreConvertedWithoutCriteriaFor764() {
        ProxySession session = joined764Session();
        Writer writer = new Writer();
        writer.writeBoolean(false);
        writer.writeVarInt(1);
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBytes(Nbt.writeAnonymous(new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("Root")))));
        writer.writeBytes(Nbt.writeAnonymous(new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("Desc")))));
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeInt(0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeVarInt(0);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        Outcome advancements = session.handleServerPacket(
                new MinecraftPacket(0x70, writer.bytes()));
        assertEquals(1, advancements.toClient().size());
        assertEquals(0x6C, advancements.toClient().get(0).id());
        // 764形式として読める（criteria が無い）。
        Reader reader = new Reader(advancements.toClient().get(0).payload());
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertTrue(reader.readString().contains("Root"));
        assertTrue(reader.readString().contains("Desc"));
        assertEquals(false, reader.readBoolean()); // icon：空スロット
        assertEquals(0, reader.readVarInt()); // frameType
        assertEquals(0, reader.readInt()); // flags
        assertEquals(0.0f, reader.readFloat()); // xCord
        assertEquals(0.0f, reader.readFloat()); // yCord
        assertEquals(0, reader.readVarInt()); // requirements（criteria は無い）
        assertEquals(false, reader.readBoolean()); // sendsTelemetryData
        assertEquals(0, reader.readVarInt()); // identifiers
        assertEquals(0, reader.readVarInt()); // progressMapping
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // online-mode（764）
    // ------------------------------------------------------------------

    @Test
    void login764OnlineAuthFlowUsesSameAuthAs763() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(764, 2), true, null, FAKE_AUTH).session();
        assertNotNull(session);
        assertFalse(session.isRelayMode());
        // ハンドシェイクは認証が終わるまでバニラへ送らない。
        // Login Start → Encryption Request がクライアントへ返る。バニラへは何も行かない。
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Steve")));
        assertEquals(1, request.toClient().size());
        assertEquals(0x01, request.toClient().get(0).id());
        assertTrue(request.toServer().isEmpty());
        assertFalse(request.closeAfterSend());

        // Encryption Response → hasJoined 照会の待ち受けになる。
        byte[] secret = new byte[16];
        Arrays.fill(secret, (byte) 0x2A);
        Outcome response = session.handleClientPacket(new MinecraftPacket(0x01,
                encryptionResponseFor(request.toClient().get(0), secret)));
        assertTrue(response.toServer().isEmpty());
        assertTrue(response.toClient().isEmpty());
        assertTrue(session.isAuthPending());
        ProxySession.PendingAuthVerification pending = session.pendingAuthVerification();
        assertEquals("Steve", pending.username());
        assertArrayEquals(secret, session.clientSharedSecret());

        // 認証成功 → 預かったハンドシェイクと認証済み Login Start がバニラへ。
        Outcome authed = session.completeAuthentication(Optional.of(PROFILE), null);
        assertEquals(2, authed.toServer().size());
        assertEquals(765, PlayTranslator.parseHandshake(
                authed.toServer().get(0).payload()).protocol());
        Reader loginReader = new Reader(authed.toServer().get(1).payload());
        assertEquals("Steve", loginReader.readString());
        assertEquals(AUTH_UUID, loginReader.readUuid());
        assertEquals(0, loginReader.remaining());

        // Login Success の後は764の設定フェーズに入る（リレーには切り替わらない）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertFalse(success.relay());
        assertFalse(session.isRelayMode());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());
        assertEquals(ProxySession.ServerState.LOGIN, session.serverState());
        // Login Acknowledged で設定フェーズに入る。
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
    }

    @Test
    void login764OnlineAuthFailureDisconnectsWithReason() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(764, 2), true, null, FAKE_AUTH).session();
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Mallory")));
        session.handleClientPacket(new MinecraftPacket(0x01,
                encryptionResponseFor(request.toClient().get(0),
                        MojangAuth.generateSharedSecret())));
        assertTrue(session.isAuthPending());
        Outcome failed = session.completeAuthentication(Optional.empty(), null);
        assertTrue(failed.closeAfterSend());
        assertTrue(failed.toServer().isEmpty());
        assertEquals(0x00, failed.toClient().get(0).id());
        String reason = new Reader(failed.toClient().get(0).payload()).readString();
        assertTrue(reason.contains("Mojang認証"), reason);
        assertTrue(session.isClosed());
    }

    @Test
    void login764OnlineWrongVerifyTokenDisconnects() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(764, 2), true, null, FAKE_AUTH).session();
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Steve")));
        MojangAuth.EncryptionRequest parsed = MojangAuth.parseEncryptionRequest(
                request.toClient().get(0).payload());
        byte[] bad = MojangAuth.buildEncryptionResponsePayload(
                MojangAuth.generateSharedSecret(),
                new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF},
                MojangAuth.decodePublicKey(parsed.publicKey()));
        Outcome outcome = session.handleClientPacket(new MinecraftPacket(0x01, bad));
        assertTrue(outcome.closeAfterSend());
        String reason = new Reader(outcome.toClient().get(0).payload()).readString();
        assertTrue(reason.contains("verify token"), reason);
        assertFalse(session.isAuthPending());
    }

    // ------------------------------------------------------------------
    // 担当4：ネイティブ764リマップ表の配線（763流用からの差し替え）
    // ブロック状態 8843 と音 638 は763表と764表で写像が違うため、
    // 763向け変換を流用したままでは誤変換になる。ここで764表が
    // 使われることを、プロキシの経路を通して確認する。
    // ------------------------------------------------------------------

    @Test
    void blockChangeUsesNative764TableFor764() {
        ProxySession session = joined764Session();
        Writer writer = new Writer();
        writer.writePositionRaw(0L);
        writer.writeVarInt(8843); // 763表なら 8827、764表なら 8843
        Outcome changed = session.handleServerPacket(new MinecraftPacket(0x09, writer.bytes()));
        assertEquals(1, changed.toClient().size());
        assertEquals(0x09, changed.toClient().get(0).id());
        Reader reader = new Reader(changed.toClient().get(0).payload());
        assertEquals(0L, reader.readPositionRaw());
        assertEquals(8843, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void soundEffectUsesNative764TableFor764() {
        ProxySession session = joined764Session();
        Writer writer = new Writer();
        writer.writeVarInt(639); // 音ID 638＋1（holder 形式）
        writer.writeVarInt(0); // カテゴリ
        writer.writeInt(8);
        writer.writeInt(64);
        writer.writeInt(8);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(0L);
        Outcome sounded = session.handleServerPacket(
                new MinecraftPacket(0x66, writer.bytes()));
        assertEquals(1, sounded.toClient().size());
        assertEquals(0x64, sounded.toClient().get(0).id());
        Reader reader = new Reader(sounded.toClient().get(0).payload());
        // 764表なら音 638 → 1270（holder 1271）。763表なら 1264（holder 1265）。
        assertEquals(1271, reader.readVarInt());
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
    void blockEntityDataKeepsAnonymousNbtFor764() {
        ProxySession session = joined764Session();
        Writer writer = new Writer();
        writer.writePositionRaw(0L);
        writer.writeVarInt(0); // チェスト（763・764とも 0）
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of(
                "id", new Tag.StringTag("minecraft:chest"))));
        byte[] payload = writer.bytes();
        Outcome data = session.handleServerPacket(new MinecraftPacket(0x07, payload));
        assertEquals(1, data.toClient().size());
        assertEquals(0x07, data.toClient().get(0).id());
        // 764向けは NBT を名前なし形式のまま通すため、入力と同一バイト列になる。
        // 763向け変換（名前付き NBT への書き直し）を使っていたら変わってしまう。
        assertArrayEquals(payload, data.toClient().get(0).payload());
        // crafter（765 type 41）は764にも無いのでパケットごと落とす。
        Writer crafter = new Writer();
        crafter.writePositionRaw(0L);
        crafter.writeVarInt(41);
        crafter.writeAnonymousNbt(new Tag.CompoundTag(Map.of(
                "id", new Tag.StringTag("minecraft:crafter"))));
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x07, crafter.bytes())).toClient().isEmpty());
    }

    @Test
    void playResourcePackOfferUsesPack764For764() {
        ProxySession session = joined764Session();
        session.setPack764(new ResourcePack(
                "https://example.invalid/pack-1.20.2.zip",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", false, ""));
        session.setPack763(new ResourcePack(
                "https://example.invalid/pack-1.20.1.zip",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", false, ""));
        Outcome add = session.handleServerPacket(
                new MinecraftPacket(0x44, configAddPack765Payload()));
        assertEquals(1, add.toClient().size());
        assertEquals(0x42, add.toClient().get(0).id());
        Reader reader = new Reader(add.toClient().get(0).payload());
        // 764用パック（format 18）が出る。763用パックは764には流用しない。
        assertEquals("https://example.invalid/pack-1.20.2.zip", reader.readString());
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", reader.readString());
    }

    @Test
    void configResourcePackOfferUsesPack764For764() {
        ProxySession.HandshakeResult handshake = ProxySession.resolveHandshake(
                handshakePayload(764, 2), false);
        ProxySession session = handshake.session();
        session.setPack764(new ResourcePack(
                "https://example.invalid/pack-1.20.2.zip",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", false, ""));
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart764("Steve")));
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x07, configAddPack765Payload()));
        assertEquals(1, pack.toClient().size());
        assertEquals(0x06, pack.toClient().get(0).id());
        Reader reader = new Reader(pack.toClient().get(0).payload());
        assertEquals("https://example.invalid/pack-1.20.2.zip", reader.readString());
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", reader.readString());
    }
}
