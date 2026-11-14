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
 * 766クライアントの参加フロー（ハンドシェイク→ログイン→設定フェーズ→
 * プレイ）を、内部763／764／765／766のそれぞれで確認する。
 * online-mode は既存のプロキシ認証を流用し、偽セッションサーバーで
 * 確認する（本物の Mojang 認証サーバーには触らない）。
 */
class ProxySession766Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID AUTH_UUID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final AuthenticatedProfile PROFILE = new AuthenticatedProfile(
            AUTH_UUID, "Steve", List.of(new AuthenticatedProfile.Property(
                    "textures", "dGV4dHVyZXNWYWx1ZQ==", "c2lnbmF0dXJl")));
    private static final ProxyAuthConfig FAKE_AUTH = ProxyAuthConfig.withVerifier(
            (username, serverIdHash) -> Optional.of(PROFILE));

    /** 内部プロトコルを指定する7引数版 resolveHandshake。 */
    private static ProxySession.HandshakeResult handshake(
            int clientProtocol, int nextState, boolean onlineMode,
            ProxyAuthConfig authConfig, int serverProtocol) {
        return ProxySession.resolveHandshake(
                handshakePayload(clientProtocol, nextState), onlineMode,
                null, null, authConfig, null, serverProtocol);
    }

    private static byte[] handshakePayload(int protocol, int nextState) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    /** 764／765／766の Login Start：名前＋UUID必須。 */
    private static byte[] loginStart766(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 763の Login Start：名前＋bool＋UUID。 */
    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 765の Login Success（strict bool なし）。 */
    private static byte[] loginSuccess765Payload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 766の Login Success（末尾に strictErrorHandling=false）。 */
    private static byte[] loginSuccess766Payload() {
        return PlayTranslator.translateLoginSuccess765To766(loginSuccess765Payload());
    }

    /** 766の Client Information（設定フェーズ）。 */
    private static byte[] clientInformation766Payload() {
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

    /** 766の Registry Data 1件（dimension_type、エントリ配列形式）。 */
    private static byte[] registryData766Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:dimension_type");
        writer.writeVarInt(1);
        writer.writeString("minecraft:overworld");
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
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

    /** 763の Join Game（codec は名前付き NBT）。 */
    private static byte[] joinGame763Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        writer.writeByte(0);
        writer.writeByte(-1);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
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
        writer.writeNamedNbt("", new Tag.CompoundTag(root));
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

    /** Encryption Request から Encryption Response を作る（フェイククライアント役）。 */
    private static byte[] encryptionResponseFor(MinecraftPacket request, byte[] secret) {
        MojangAuth.EncryptionRequest parsed =
                MojangAuth.parseEncryptionRequest(request.payload());
        return MojangAuth.buildEncryptionResponsePayload(
                secret, parsed.verifyToken(),
                MojangAuth.decodePublicKey(parsed.publicKey()));
    }

    // ------------------------------------------------------------------
    // 766クライアント → 内部765（下向き）
    // ------------------------------------------------------------------

    @Test
    void join766Against765RelaysConfigWithTranslation() {
        ProxySession.HandshakeResult result = handshake(
                766, 2, false, ProxyAuthConfig.defaults(), 765);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(766, session.clientProtocol());
        assertEquals(765, session.serverProtocol());
        assertEquals(765, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（765形式）→ 766には strict bool を足して送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（766：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());

        // Client Information（766：0x00）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation766Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());

        // Registry Data（765：0x05、codec 形式）→ 766（0x07、エントリ配列形式）。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertEquals(2, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        assertEquals(0x07, registry.toClient().get(1).id());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Finish Configuration（765：0x02）→ 766（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());

        // クライアントの応答（766：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x02, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Join Game（765：0x29）→ 766（0x2B）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        PlayTranslator.JoinGame766 parsed =
                PlayTranslator.parseJoinGame766(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimensionId());
        assertFalse(parsed.enforcesSecureChat());
    }

    // ------------------------------------------------------------------
    // 766クライアント → 内部764（下向き）
    // ------------------------------------------------------------------

    @Test
    void join766Against764RelaysConfigWithTranslation() {
        ProxySession.HandshakeResult result = handshake(
                766, 2, false, ProxyAuthConfig.defaults(), 764);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(764, session.serverProtocol());

        session.handleClientPacket(new MinecraftPacket(0x00, loginStart766("Steve")));
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Registry Data（764：0x05）→ 766（0x07）。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertEquals(2, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());

        // Finish Configuration（764：0x02）→ 766（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x02, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Join Game（764：0x29）→ 766（0x2B）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // 766クライアント → 内部766（素通し）
    // ------------------------------------------------------------------

    @Test
    void join766Against766IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                766, 2, false, ProxyAuthConfig.defaults(), 766);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(766, session.clientProtocol());
        assertEquals(766, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
    }

    // ------------------------------------------------------------------
    // 766クライアント → 内部763（設定フェーズを合成）
    // ------------------------------------------------------------------

    @Test
    void join766Against763SynthesizesConfig() {
        ProxySession.HandshakeResult result = handshake(
                766, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(766, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は763形式（bool 付き）に直してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        assertEquals(0x00, login.toServer().get(0).id());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PLAYER_UUID, loginReader.readUuid());
        assertEquals(0, loginReader.remaining());

        // Login Success（763形式）→ 766には strict bool を足して送る。
        // 内部763のためサーバーは既にプレイ状態へ進む。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Join Game（763：0x28）はプロキシが預かり、まだクライアントへ送らない。
        Outcome joinHeld = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertTrue(joinHeld.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Login Acknowledged（766：0x03）で合成した設定フェーズを送る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertTrue(ack.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(3, ack.toClient().size());
        // Registry Data（766：0x07）×2＋Finish Configuration（766：0x03）。
        assertEquals(0x07, ack.toClient().get(0).id());
        assertEquals(0x07, ack.toClient().get(1).id());
        assertEquals(0x03, ack.toClient().get(2).id());

        // Finish Configuration の応答（766：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x2B, finishAck.toClient().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        PlayTranslator.JoinGame766 parsed =
                PlayTranslator.parseJoinGame766(finishAck.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimensionId());
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部766（設定フェーズを766クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join763Against766ConsumesConfigAs766Client() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 766);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(766, session.serverProtocol());

        // Login Start は763形式から766形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（766形式）→ 763には strict bool を落として送る。
        // プロキシが Login Acknowledged＋Client Information をバニラへ送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（766：0x07）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x07, registryData766Payload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Finish Configuration（766：0x03）→ 応答（0x03）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（766：0x2B）→ 763（0x28）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    // ------------------------------------------------------------------
    // 766クライアントの Keep Alive／Ping は中継する
    // ------------------------------------------------------------------

    @Test
    void keepAliveAndPingAreRelayedFor766Client() {
        ProxySession.HandshakeResult result = handshake(
                766, 2, false, ProxyAuthConfig.defaults(), 765);
        ProxySession session = result.session();
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart766("Steve")));
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        session.handleServerPacket(new MinecraftPacket(0x05, registryDataPayload()));
        session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Keep Alive（765：0x24）→ 766（0x26）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        assertArrayEquals(keepAlive.bytes(), ka.toClient().get(0).payload());

        // Ping（765：0x33）→ 766（0x35）。
        Writer ping = new Writer();
        ping.writeInt(99);
        Outcome pg = session.handleServerPacket(
                new MinecraftPacket(0x33, ping.bytes()));
        assertEquals(1, pg.toClient().size());
        assertEquals(0x35, pg.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // online-mode（偽セッションサーバー）
    // ------------------------------------------------------------------

    @Test
    void join766OnlineThroughProxyAuth() {
        ProxySession session = handshake(
                766, 2, true, FAKE_AUTH, 765).session();
        // Login Start → Encryption Request がクライアントへ返る。
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
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
        assertEquals(765, PlayTranslator.parseHandshake(
                authed.toServer().get(0).payload()).protocol());
        Reader loginReader = new Reader(authed.toServer().get(1).payload());
        assertEquals("Steve", loginReader.readString());
        assertEquals(AUTH_UUID, loginReader.readUuid());
        assertEquals(0, loginReader.remaining());

        // Login Success には認証済みプロパティが入り、766向けに strict bool が付く。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        Reader successReader = new Reader(success.toClient().get(0).payload());
        assertEquals(PLAYER_UUID, successReader.readUuid());
        assertEquals("Steve", successReader.readString());
        assertEquals(1, successReader.readVarInt()); // 認証済みプロパティ
        assertEquals("textures", successReader.readString());
        assertEquals("dGV4dHVyZXNWYWx1ZQ==", successReader.readString());
        assertTrue(successReader.readBoolean());
        assertEquals("c2lnbmF0dXJl", successReader.readString());
        assertFalse(successReader.readBoolean()); // strictErrorHandling
        assertEquals(0, successReader.remaining());
    }

    @Test
    void join766OnlineAgainst766ServerKeepsStrictBool() {
        ProxySession session = handshake(
                766, 2, true, FAKE_AUTH, 766).session();
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, request.toClient().size());
        byte[] secret = new byte[16];
        Arrays.fill(secret, (byte) 0x2A);
        session.handleClientPacket(new MinecraftPacket(0x01,
                encryptionResponseFor(request.toClient().get(0), secret)));
        assertTrue(session.isAuthPending());
        Outcome authed = session.completeAuthentication(Optional.of(PROFILE), null);
        assertEquals(2, authed.toServer().size());

        // 766のサーバーが送る Login Success（末尾に strict bool）でも、
        // 認証済みプロパティの書き換えで落とさず、クライアントへ届く。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertFalse(success.closeAfterSend());
        // ネイティブ＋online-mode：Login Success の後にリレーへ切り替わる。
        assertTrue(success.relay());
        Reader successReader = new Reader(success.toClient().get(0).payload());
        assertEquals(PLAYER_UUID, successReader.readUuid());
        assertEquals("Steve", successReader.readString());
        assertEquals(1, successReader.readVarInt());
        assertEquals("textures", successReader.readString());
        assertEquals("dGV4dHVyZXNWYWx1ZQ==", successReader.readString());
        assertTrue(successReader.readBoolean());
        assertEquals("c2lnbmF0dXJl", successReader.readString());
        assertFalse(successReader.readBoolean()); // strictErrorHandling
        assertEquals(0, successReader.remaining());
    }

    @Test
    void join766OnlineAgainst763Builds763LoginStart() {
        ProxySession session = handshake(
                766, 2, true, FAKE_AUTH, 763).session();
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, request.toClient().size());
        byte[] secret = new byte[16];
        Arrays.fill(secret, (byte) 0x2A);
        session.handleClientPacket(new MinecraftPacket(0x01,
                encryptionResponseFor(request.toClient().get(0), secret)));
        assertTrue(session.isAuthPending());

        // 認証済み Login Start は内部763向けに763形式（bool 付き）になる。
        Outcome authed = session.completeAuthentication(Optional.of(PROFILE), null);
        assertEquals(2, authed.toServer().size());
        assertEquals(763, PlayTranslator.parseHandshake(
                authed.toServer().get(0).payload()).protocol());
        Reader loginReader = new Reader(authed.toServer().get(1).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(AUTH_UUID, loginReader.readUuid());
        assertEquals(0, loginReader.remaining());
    }
}
