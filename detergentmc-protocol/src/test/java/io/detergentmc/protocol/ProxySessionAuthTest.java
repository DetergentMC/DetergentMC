package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * プロキシ認証（online-mode）のセッション単位の検証（担当1）。
 * Netty は使わず、セッションの入出力と hasJoined 照会の差し替えだけを見る。
 * 偽の照会器は認証済みプロフィールを返す。
 */
class ProxySessionAuthTest {

    private static final UUID AUTH_UUID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
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

    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    private static byte[] loginStart765(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
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

    /** Encryption Request から Encryption Response を作る（フェイククライアント役）。 */
    private static byte[] encryptionResponseFor(MinecraftPacket request, byte[] secret) {
        MojangAuth.EncryptionRequest parsed =
                MojangAuth.parseEncryptionRequest(request.payload());
        return MojangAuth.buildEncryptionResponsePayload(
                secret, parsed.verifyToken(),
                MojangAuth.decodePublicKey(parsed.publicKey()));
    }

    /** Login Success のプロパティを読み取る（検証用）。 */
    private static List<AuthenticatedProfile.Property> readLoginSuccessProperties(
            MinecraftPacket success) {
        Reader reader = new Reader(success.payload());
        reader.readUuid();
        reader.readString();
        int count = reader.readVarInt();
        List<AuthenticatedProfile.Property> properties = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = reader.readString();
            String value = reader.readString();
            String signature = reader.readBoolean() ? reader.readString() : null;
            properties.add(new AuthenticatedProfile.Property(name, value, signature));
        }
        assertEquals(0, reader.remaining());
        return properties;
    }

    // ------------------------------------------------------------------
    // 763 online-mode
    // ------------------------------------------------------------------

    @Test
    void handshake763OnlineIsDeferredUntilAuthCompletes() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(763, 2), true, null, FAKE_AUTH);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        // ハンドシェイクは認証が終わるまでバニラへ送らない。
        assertNull(result.toServer());
        assertEquals(ProxySession.ClientState.LOGIN, result.session().clientState());
    }

    @Test
    void login763OnlineAuthFlowForwardsAuthenticatedLoginStart() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), true, null, FAKE_AUTH).session();
        // Login Start → Encryption Request がクライアントへ返る。バニラへは何も行かない。
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
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
        MojangAuth.EncryptionRequest parsed = MojangAuth.parseEncryptionRequest(
                request.toClient().get(0).payload());
        assertEquals(MojangAuth.serverIdHash("", secret, parsed.publicKey()),
                pending.serverIdHash());
        assertArrayEquals(secret, session.clientSharedSecret());

        // 認証成功 → 預かったハンドシェイクと認証済み Login Start がバニラへ。
        Outcome authed = session.completeAuthentication(Optional.of(PROFILE), null);
        assertEquals(2, authed.toServer().size());
        assertEquals(765, PlayTranslator.parseHandshake(
                authed.toServer().get(0).payload()).protocol());
        Reader loginReader = new Reader(authed.toServer().get(1).payload());
        assertEquals("Steve", loginReader.readString());
        // クライアントが送ってきた UUID ではなく、認証済み UUID を使う。
        assertEquals(AUTH_UUID, loginReader.readUuid());
        assertEquals(0, loginReader.remaining());
        assertEquals(AUTH_UUID, session.authenticatedProfile().uuid());

        // Login Success には認証済みプロパティ（textures）が入る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        List<AuthenticatedProfile.Property> properties =
                readLoginSuccessProperties(success.toClient().get(0));
        assertEquals(1, properties.size());
        assertEquals("textures", properties.get(0).name());
        assertEquals("dGV4dHVyZXNWYWx1ZQ==", properties.get(0).value());
        // 763は設定フェーズへ（Login Acknowledged＋Client Information）。
        assertEquals(2, success.toServer().size());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());

        // 設定フェーズを消費して Join Game まで進める。
        session.handleServerPacket(new MinecraftPacket(0x05, registryDataPayload()));
        session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(0x28, join.toClient().get(0).id());
    }

    @Test
    void authFailureDisconnectsWithReason() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), true, null, FAKE_AUTH).session();
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Mallory")));
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
    void wrongVerifyTokenDisconnects() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), true, null, FAKE_AUTH).session();
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
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

    @Test
    void encryptionResponseBeforeLoginStartIsDropped() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), true, null, FAKE_AUTH).session();
        Outcome outcome = session.handleClientPacket(
                new MinecraftPacket(0x01, new byte[]{0x00, 0x00}));
        assertTrue(outcome.toServer().isEmpty());
        assertTrue(outcome.toClient().isEmpty());
        assertFalse(outcome.closeAfterSend());
    }

    // ------------------------------------------------------------------
    // 765 online-mode（プロキシ認証→リレー）
    // ------------------------------------------------------------------

    @Test
    void login765OnlineAuthFlowSwitchesToRelayAfterLoginSuccess() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(765, 2), true, null, FAKE_AUTH).session();
        // online-mode の765はハンドシェイクでリレーに切り替わらない。
        assertFalse(session.isRelayMode());
        Outcome request = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart765("Steve")));
        assertEquals(0x01, request.toClient().get(0).id());
        session.handleClientPacket(new MinecraftPacket(0x01,
                encryptionResponseFor(request.toClient().get(0),
                        MojangAuth.generateSharedSecret())));
        assertTrue(session.isAuthPending());
        Outcome authed = session.completeAuthentication(Optional.of(PROFILE), null);
        assertEquals(2, authed.toServer().size());
        assertEquals(765, PlayTranslator.parseHandshake(
                authed.toServer().get(0).payload()).protocol());

        // Login Success（プロパティ付き）の後にリレーへ切り替わる。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertTrue(success.relay());
        assertTrue(session.isRelayMode());
        assertEquals(1, readLoginSuccessProperties(success.toClient().get(0)).size());
    }

    @Test
    void login765OfflineStillRelaysAtHandshake() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(765, 2), false);
        assertTrue(result.relay());
        assertEquals(765, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
    }

    @Test
    void login763OfflineIsUnchanged() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false).session();
        Outcome outcome = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, outcome.toServer().size());
        assertEquals(0x00, outcome.toServer().get(0).id());
        assertNull(session.authenticatedProfile());
        assertFalse(session.isAuthPending());
    }
}
