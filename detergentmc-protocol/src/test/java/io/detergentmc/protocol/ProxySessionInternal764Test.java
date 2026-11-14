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
 * 内部764（1.20.2サーバー）への参加パターン。
 * 振り分けはクライアントのプロトコルと内部プロトコル（764）の比較で決まる：
 * 764は素通し、765は下向き変換（765→764）、763は763→764への変換。
 * 内部765のときの動きは {@link ProxySessionTest}／
 * {@link ProxySession764Test} が保証する。
 */
class ProxySessionInternal764Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID AUTH_UUID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final AuthenticatedProfile PROFILE = new AuthenticatedProfile(
            AUTH_UUID, "Steve", List.of(new AuthenticatedProfile.Property(
                    "textures", "dGV4dHVyZXNWYWx1ZQ==", "c2lnbmF0dXJl")));
    private static final ProxyAuthConfig FAKE_AUTH = ProxyAuthConfig.withVerifier(
            (username, serverIdHash) -> Optional.of(PROFILE));

    /** 内部764の7引数版 resolveHandshake（pack765 付き）。 */
    private static ProxySession.HandshakeResult handshake764(
            int clientProtocol, int nextState, boolean onlineMode) {
        return ProxySession.resolveHandshake(
                handshakePayload(clientProtocol, nextState), onlineMode,
                null, null, ProxyAuthConfig.defaults(), null, 764);
    }

    private static byte[] handshakePayload(int protocol, int nextState) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    /** 763の Login Start：名前＋bool＋UUID（任意）。 */
    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 764／765の Login Start：名前＋UUID必須。 */
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

    /** 764の Join Game（765と同形式。doLimitedCrafting 付き）。 */
    private static byte[] joinGame764Payload() {
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

    /** 764の設定フェーズの Add Resource Pack（UUIDなし、プロンプトは JSON 文字列）。 */
    private static byte[] configAddPack764Payload() {
        Writer writer = new Writer();
        writer.writeString("https://example.invalid/pack.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeString("{\"text\":\"pack?\"}");
        return writer.bytes();
    }

    /** 765の Client Information（設定フェーズ）。 */
    private static byte[] clientInformation765Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7F);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeBoolean(true);
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

    /** 764・offline の参加フローを最後まで進めたセッションを作る（内部764、ネイティブ）。 */
    private static ProxySession joined764NativeSession() {
        ProxySession.HandshakeResult handshake = handshake764(764, 2, false);
        assertFalse(handshake.disconnect());
        assertTrue(handshake.relay());
        ProxySession session = handshake.session();
        assertEquals(764, session.clientProtocol());
        assertEquals(764, session.serverProtocol());
        assertEquals(764, PlayTranslator.parseHandshake(
                handshake.toServer().payload()).protocol());
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Steve")));
        assertEquals(1, login.toServer().size());
        assertEquals(0x00, login.toServer().get(0).id());
        assertArrayEquals(loginStart764("Steve"), login.toServer().get(0).payload());
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        return session;
    }

    /** 765・offline の参加フローを最後まで進めたセッションを作る（内部764、下向き）。 */
    private static ProxySession joined765DownSession() {
        ProxySession.HandshakeResult handshake = handshake764(765, 2, false);
        assertFalse(handshake.disconnect());
        assertFalse(handshake.relay());
        ProxySession session = handshake.session();
        assertEquals(765, session.clientProtocol());
        assertEquals(764, session.serverProtocol());
        assertEquals(764, PlayTranslator.parseHandshake(
                handshake.toServer().payload()).protocol());
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Steve")));
        assertEquals(1, login.toServer().size());
        assertEquals(0x00, login.toServer().get(0).id());
        assertArrayEquals(loginStart764("Steve"), login.toServer().get(0).payload());
        // Login Success：まだログイン状態のまま（Login Acknowledged 待ち）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());
        assertEquals(ProxySession.ServerState.LOGIN, session.serverState());
        // Login Acknowledged で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        // Client Information はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation765Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        // Registry Data はそのまま通し、内容も保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x05, registry.toClient().get(0).id());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));
        // Add Resource Pack（764：0x06、UUIDなし）→ 765の Add（0x07、UUID付き）。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x06, configAddPack764Payload()));
        assertEquals(1, pack.toClient().size());
        assertEquals(0x07, pack.toClient().get(0).id());
        Reader packReader = new Reader(pack.toClient().get(0).payload());
        assertNotNull(packReader.readUuid());
        assertEquals("https://example.invalid/pack.zip", packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertFalse(packReader.readBoolean());
        assertTrue(packReader.readBoolean());
        assertEquals("{\"text\":\"pack?\"}",
                Nbt.toJsonString(packReader.readAnonymousNbt().tag()));
        // Tags（764：0x08）はレジストリ番号がずれるため落とす。
        Outcome tags = session.handleServerPacket(new MinecraftPacket(0x08, new byte[0]));
        assertTrue(tags.toClient().isEmpty());
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
        // Join Game は764と765で同形式のため変換なしで通す。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame764Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x29, join.toClient().get(0).id());
        assertArrayEquals(joinGame764Payload(), join.toClient().get(0).payload());
        return session;
    }

    /** 763・offline の参加フローを最後まで進めたセッションを作る（内部764、上向き）。 */
    private static ProxySession joined763UpSession() {
        ProxySession.HandshakeResult handshake = handshake764(763, 2, false);
        assertFalse(handshake.disconnect());
        assertFalse(handshake.relay());
        ProxySession session = handshake.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(764, session.serverProtocol());
        assertEquals(764, PlayTranslator.parseHandshake(
                handshake.toServer().payload()).protocol());
        // Login Start は763形式（bool 付き）から764形式（名前＋UUID）に変換される。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertEquals(0x00, login.toServer().get(0).id());
        assertArrayEquals(loginStart764("Steve"), login.toServer().get(0).payload());
        assertEquals("Steve", session.playerName());
        // Login Success：プロキシが Login Acknowledged＋Client Information をバニラへ送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        // Registry Data（764：0x05）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));
        // Add Resource Pack（764：0x06）は保持して Join Game の後に送る。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x06, configAddPack764Payload()));
        assertTrue(pack.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());
        // Finish Configuration：そのまま終わる。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x02, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        return session;
    }

    // ------------------------------------------------------------------
    // ハンドシェイク・ステータス
    // ------------------------------------------------------------------

    @Test
    void handshake764NativeIsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake764(764, 2, false);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        assertEquals(764, result.session().clientProtocol());
        assertEquals(764, result.session().serverProtocol());
        assertEquals(764, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
    }

    @Test
    void handshake765DownIsNotRelayed() {
        ProxySession.HandshakeResult result = handshake764(765, 2, false);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        assertEquals(764, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
    }

    @Test
    void handshake763UpTo764IsNotRelayed() {
        ProxySession.HandshakeResult result = handshake764(763, 2, false);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        assertEquals(764, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
    }

    @Test
    void statusRewrittenFor765ClientAgainst764() {
        ProxySession.HandshakeResult result = handshake764(765, 1, false);
        assertFalse(result.disconnect());
        String json = "{\"version\":{\"name\":\"1.20.2\",\"protocol\":764},"
                + "\"players\":{\"max\":20,\"online\":0},\"description\":{\"text\":\"hi\"}}";
        Writer writer = new Writer();
        writer.writeString(json);
        Outcome response = result.session().handleServerPacket(
                new MinecraftPacket(0x00, writer.bytes()));
        assertEquals(1, response.toClient().size());
        String rewritten = new Reader(response.toClient().get(0).payload()).readString();
        assertTrue(rewritten.contains("\"protocol\":765"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.20.4\""), rewritten);
    }

    @Test
    void statusRewrittenFor763ClientAgainst764() {
        ProxySession.HandshakeResult result = handshake764(763, 1, false);
        assertFalse(result.disconnect());
        String json = "{\"version\":{\"name\":\"1.20.2\",\"protocol\":764},"
                + "\"players\":{\"max\":20,\"online\":0},\"description\":{\"text\":\"hi\"}}";
        Writer writer = new Writer();
        writer.writeString(json);
        Outcome response = result.session().handleServerPacket(
                new MinecraftPacket(0x00, writer.bytes()));
        assertEquals(1, response.toClient().size());
        String rewritten = new Reader(response.toClient().get(0).payload()).readString();
        assertTrue(rewritten.contains("\"protocol\":763"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.20.1\""), rewritten);
    }

    @Test
    void statusUnchangedFor764ClientAgainst764() {
        ProxySession.HandshakeResult result = handshake764(764, 1, false);
        assertFalse(result.disconnect());
        String json = "{\"version\":{\"name\":\"1.20.2\",\"protocol\":764},"
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

    // ------------------------------------------------------------------
    // 764ネイティブ（素通し）
    // ------------------------------------------------------------------

    @Test
    void native764JoinGamePassesThroughUnchanged() {
        ProxySession session = joined764NativeSession();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame764Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x29, join.toClient().get(0).id());
        assertArrayEquals(joinGame764Payload(), join.toClient().get(0).payload());
    }

    @Test
    void native764StartConfigurationIsRelayed() {
        ProxySession session = joined764NativeSession();
        Outcome start = session.handleServerPacket(new MinecraftPacket(0x65, new byte[0]));
        assertEquals(1, start.toClient().size());
        assertEquals(0x65, start.toClient().get(0).id());
        // クライアントの応答もそのままバニラへ。リレーモードではプロキシは
        // 状態を追わない（クライアントとバニラが自分で追う）ため、
        // セッションの状態は PLAY のままである。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x0B, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x0B, ack.toServer().get(0).id());
    }

    // ------------------------------------------------------------------
    // 765クライアント（下向き変換）
    // ------------------------------------------------------------------

    @Test
    void down765SystemChatConvertsJsonToNbt() {
        ProxySession session = joined765DownSession();
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeBoolean(false);
        int systemChat764 = PacketTranslator.toClientId(
                PacketIds.PacketName.PLAY_SYSTEM_CHAT, 764).orElseThrow();
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(systemChat764, writer.bytes()));
        assertEquals(1, chat.toClient().size());
        int systemChat765 = PacketTranslator.toClientId(
                PacketIds.PacketName.PLAY_SYSTEM_CHAT, 765).orElseThrow();
        assertEquals(systemChat765, chat.toClient().get(0).id());
        Reader reader = new Reader(chat.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}",
                Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertFalse(reader.readBoolean());
    }

    @Test
    void down765DisconnectConvertsJsonToNbtAndCloses() {
        ProxySession session = joined765DownSession();
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"bye\"}");
        Outcome disconnect = session.handleServerPacket(
                new MinecraftPacket(0x1B, writer.bytes()));
        assertEquals(1, disconnect.toClient().size());
        assertEquals(0x1B, disconnect.toClient().get(0).id());
        Reader reader = new Reader(disconnect.toClient().get(0).payload());
        assertEquals("{\"text\":\"bye\"}",
                Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertTrue(disconnect.closeAfterSend());
    }

    @Test
    void down765ScoreboardScoreRemovalBecomesResetScore() {
        ProxySession session = joined765DownSession();
        Writer writer = new Writer();
        writer.writeString("Steve");
        writer.writeVarInt(1); // 削除
        writer.writeString("objective");
        int score764 = PacketTranslator.toClientId(
                PacketIds.PacketName.PLAY_SCOREBOARD_SCORE, 764).orElseThrow();
        Outcome reset = session.handleServerPacket(
                new MinecraftPacket(score764, writer.bytes()));
        assertEquals(1, reset.toClient().size());
        assertEquals(0x42, reset.toClient().get(0).id()); // Reset Score（765）
        Reader reader = new Reader(reset.toClient().get(0).payload());
        assertEquals("Steve", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("objective", reader.readString());
    }

    @Test
    void down765ConfigResourcePackResponseStripsUuid() {
        ProxySession session = joined765DownSession();
        // 設定フェーズに戻す：Start Configuration を中継して応答する。
        session.handleServerPacket(new MinecraftPacket(0x65, new byte[0]));
        session.handleClientPacket(new MinecraftPacket(0x0B, new byte[0]));
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        // Add Pack を送ってから、その応答（UUID＋結果）を返す。
        session.handleServerPacket(new MinecraftPacket(0x06, configAddPack764Payload()));
        Writer response = new Writer();
        response.writeUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        response.writeVarInt(0);
        Outcome toServer = session.handleClientPacket(
                new MinecraftPacket(0x05, response.bytes()));
        assertEquals(1, toServer.toServer().size());
        assertEquals(0x05, toServer.toServer().get(0).id());
        assertEquals(0, new Reader(toServer.toServer().get(0).payload()).readVarInt());
    }

    @Test
    void down765StartConfigurationIsRelayedWithRemap() {
        ProxySession session = joined765DownSession();
        Outcome start = session.handleServerPacket(new MinecraftPacket(0x65, new byte[0]));
        assertEquals(1, start.toClient().size());
        assertEquals(0x67, start.toClient().get(0).id()); // 765の Start Configuration
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x0B, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x0B, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
    }

    // ------------------------------------------------------------------
    // 763クライアント（763→764への変換）
    // ------------------------------------------------------------------

    @Test
    void up763JoinGameFrom764EmbedsCodec() {
        ProxySession session = joined763UpSession();
        assertEquals(2, session.registries().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame764Payload()));
        assertEquals(2, join.toClient().size());
        MinecraftPacket joinGame = join.toClient().get(0);
        assertEquals(0x28, joinGame.id());
        Reader reader = new Reader(joinGame.payload());
        assertEquals(42, reader.readInt());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readUnsignedByte());
        // codec に dimension_type が入っている。
        reader.readByte();
        PacketIO.readStringList(reader);
        Nbt.NamedResult codec = reader.readNamedNbt();
        assertTrue(((Tag.CompoundTag) codec.tag()).value()
                .containsKey("minecraft:dimension_type"));
        // 保持したリソースパックが Join Game の後に送られる。
        assertEquals(0x40, join.toClient().get(1).id());
        Reader packReader = new Reader(join.toClient().get(1).payload());
        assertEquals("https://example.invalid/pack.zip", packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertFalse(packReader.readBoolean());
        assertEquals(0, session.pendingPackCount());
    }

    @Test
    void up763ChatMessageIsConvertedTo764() {
        ProxySession session = joined763UpSession();
        Writer writer = new Writer();
        writer.writeString("hello");
        writer.writeLong(123456L);
        writer.writeLong(0L);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        Outcome chat = session.handleClientPacket(new MinecraftPacket(0x05, writer.bytes()));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x05, chat.toServer().get(0).id());
        Reader reader = new Reader(chat.toServer().get(0).payload());
        assertEquals("hello", reader.readString());
        assertEquals(123456L, reader.readLong());
    }

    @Test
    void up763PlayDisconnectPassesThroughAsJsonString() {
        ProxySession session = joined763UpSession();
        // 764の Disconnect（プレイ）は JSON 文字列。763も JSON 文字列のため
        // そのまま通す。NBT 読みを掛けると先頭バイトをタグ種別と誤読して
        // 例外になる（修正前のバグ）。
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"bye\"}");
        Outcome disconnect = session.handleServerPacket(
                new MinecraftPacket(0x1B, writer.bytes()));
        assertEquals(1, disconnect.toClient().size());
        int disconnect763 = PacketTranslator.toClientId(
                PacketIds.PacketName.PLAY_DISCONNECT, 763).orElseThrow();
        assertEquals(disconnect763, disconnect.toClient().get(0).id());
        assertEquals("{\"text\":\"bye\"}",
                new Reader(disconnect.toClient().get(0).payload()).readString());
        assertTrue(disconnect.closeAfterSend());
    }

    @Test
    void up763StartConfigurationIsConsumedByProxy() {
        ProxySession session = joined763UpSession();
        Outcome start = session.handleServerPacket(new MinecraftPacket(0x65, new byte[0]));
        // 763には設定フェーズが無いため、クライアントには何も送らない。
        assertTrue(start.toClient().isEmpty());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        // Finish Configuration でプレイに戻る。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x02, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
    }

    // ------------------------------------------------------------------
    // online-mode
    // ------------------------------------------------------------------

    @Test
    void online764HandshakeIsDeferredUntilAuthCompletes() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(764, 2), true, null, null, FAKE_AUTH, null, 764);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        // ハンドシェイクは認証が終わるまでバニラへ送らない。
        assertNull(result.toServer());
        assertEquals(ProxySession.ClientState.LOGIN, result.session().clientState());
    }

    @Test
    void online764AuthFlowForwardsAuthenticatedLoginStart() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(764, 2), true, null, null, FAKE_AUTH, null, 764).session();
        // Login Start → Encryption Request がクライアントへ返る。バニラへは何も行かない。
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Steve")));
        assertEquals(1, request.toClient().size());
        assertEquals(0x01, request.toClient().get(0).id());
        assertTrue(request.toServer().isEmpty());

        // Encryption Response → hasJoined 照会の待ち受けになる。
        byte[] secret = new byte[16];
        Arrays.fill(secret, (byte) 0x2A);
        Outcome response = session.handleClientPacket(new MinecraftPacket(0x01,
                encryptionResponseFor(request.toClient().get(0), secret)));
        assertTrue(response.toServer().isEmpty());
        assertTrue(response.toClient().isEmpty());
        assertTrue(session.isAuthPending());

        // 認証成功 → 預かったハンドシェイクと認証済み Login Start がバニラへ。
        Outcome authed = session.completeAuthentication(Optional.of(PROFILE), null);
        assertEquals(2, authed.toServer().size());
        assertEquals(764, PlayTranslator.parseHandshake(
                authed.toServer().get(0).payload()).protocol());
        Reader loginReader = new Reader(authed.toServer().get(1).payload());
        assertEquals("Steve", loginReader.readString());
        assertEquals(AUTH_UUID, loginReader.readUuid());
        assertEquals(0, loginReader.remaining());

        // Login Success はクライアントへ届く。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
    }

    @Test
    void online764AuthFailureDisconnectsWithReason() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(764, 2), true, null, null, FAKE_AUTH, null, 764).session();
        // Login Start → Encryption Request がクライアントへ返る。
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart764("Steve")));
        assertEquals(1, request.toClient().size());
        assertEquals(0x01, request.toClient().get(0).id());
        byte[] secret = new byte[16];
        Arrays.fill(secret, (byte) 0x2A);
        session.handleClientPacket(new MinecraftPacket(0x01,
                encryptionResponseFor(request.toClient().get(0), secret)));
        assertTrue(session.isAuthPending());
        Outcome failed = session.completeAuthentication(Optional.empty(), "認証に失敗しました。");
        assertTrue(failed.closeAfterSend());
        assertEquals(1, failed.toClient().size());
    }

    // ------------------------------------------------------------------
    // リソースパックの出し分け（クライアントのプロトコルが基準）
    // ------------------------------------------------------------------

    @Test
    void packOverrideForClientIsKeyedByClientProtocol() {
        ResourcePack pack763 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.20.1.zip",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", false, "");
        ResourcePack pack764 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.20.2.zip",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", false, "");
        ResourcePack pack765 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack.zip",
                "cccccccccccccccccccccccccccccccccccccccc", false, "");
        ProxySession session763 = new ProxySession(763, false, 764);
        session763.setPack763(pack763);
        session763.setPack764(pack764);
        session763.setPack765(pack765);
        assertEquals(pack763, session763.packOverrideForClient());
        ProxySession session764 = new ProxySession(764, false, 764);
        session764.setPack763(pack763);
        session764.setPack764(pack764);
        session764.setPack765(pack765);
        assertEquals(pack764, session764.packOverrideForClient());
        // 内部が764でも、765クライアントには22のパック（pack765）を出す。
        ProxySession session765 = new ProxySession(765, false, 764);
        session765.setPack763(pack763);
        session765.setPack764(pack764);
        session765.setPack765(pack765);
        assertEquals(pack765, session765.packOverrideForClient());
    }
}
