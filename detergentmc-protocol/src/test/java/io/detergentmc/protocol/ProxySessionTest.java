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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * プロキシの状態機械の検証。Netty は使わず、セッションの入出力だけを見る。
 */
class ProxySessionTest {

    private static byte[] handshakePayload(int protocol, int nextState) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    private static byte[] loginStart763(String name, boolean withUuid) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(withUuid);
        if (withUuid) {
            writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        }
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

    /** 763・offline の参加フローを最後まで進めたセッションを作る。 */
    private static ProxySession joined763Session() {
        ProxySession.HandshakeResult handshake = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false);
        assertFalse(handshake.disconnect());
        ProxySession session = handshake.session();
        assertEquals(763, session.clientProtocol());
        // ハンドシェイクは 765 に書き換えられてバニラへ。
        assertEquals(765, PlayTranslator.parseHandshake(
                handshake.toServer().payload()).protocol());
        assertFalse(handshake.relay());
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve", true)));
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, login.toClient().size());
        assertEquals(0x02, login.toClient().get(0).id());
        // プロキシが Login Acknowledged と Client Information をバニラへ送る。
        assertEquals(2, login.toServer().size());
        assertEquals(0x03, login.toServer().get(0).id());
        assertEquals(0x00, login.toServer().get(1).id());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        session.handleServerPacket(new MinecraftPacket(0x05, registryDataPayload()));
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(List.of(0x02), finish.toServer().stream().map(MinecraftPacket::id).toList());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        return session;
    }

    // ------------------------------------------------------------------
    // ハンドシェイク
    // ------------------------------------------------------------------

    @Test
    void handshake763LoginIsAcceptedWithRewrite() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(763, result.session().clientProtocol());
        assertFalse(result.relay());
        assertEquals(765, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
    }

    @Test
    void handshake765LoginSwitchesToRelay() {
        // 【担当1】online-mode の765はハンドシェイクではリレーに切り替わらない。
        // プロキシが先に認証し、Login Success の後にリレーへ切り替わる
        // （ProxySessionAuthTest 参照）。offline-mode の765だけが
        // ハンドシェイク直後にリレーする。
        ProxySession.HandshakeResult online = ProxySession.resolveHandshake(
                handshakePayload(765, 2), true);
        assertFalse(online.disconnect());
        assertFalse(online.relay());
        assertFalse(online.session().isRelayMode());
        assertNull(online.toServer());
        ProxySession.HandshakeResult offline = ProxySession.resolveHandshake(
                handshakePayload(765, 2), false);
        assertFalse(offline.disconnect());
        assertTrue(offline.relay());
        assertTrue(offline.session().isRelayMode());
        assertArrayEquals(handshakePayload(765, 2), offline.toServer().payload());
    }

    @Test
    void handshake764LoginIsAcceptedWithRewrite() {
        // 764は変換して受け入れる（旧：拒否）。ハンドシェイクは765に書き換えられる。
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(764, 2), false);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(764, result.session().clientProtocol());
        assertFalse(result.relay());
        assertEquals(765, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
    }

    @Test
    void handshake765StatusIsNotRelay() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(765, 1), true);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        assertEquals(ProxySession.ClientState.STATUS, result.session().clientState());
    }

    @Test
    void handshake763StatusForwardsToVanilla() {
        ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                handshakePayload(763, 1), false);
        assertFalse(result.disconnect());
        assertEquals(765, PlayTranslator.parseHandshake(
                result.toServer().payload()).protocol());
    }

    // ------------------------------------------------------------------
    // ステータス
    // ------------------------------------------------------------------

    @Test
    void statusResponseIsRewrittenFor763() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 1), false).session();
        String json = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0},\"description\":{\"text\":\"hi\"}}";
        Writer writer = new Writer();
        writer.writeString(json);
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x00, writer.bytes()));
        assertEquals(1, outcome.toClient().size());
        String rewritten = new Reader(outcome.toClient().get(0).payload()).readString();
        assertTrue(rewritten.contains("\"protocol\":763"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.20.1\""), rewritten);
    }

    @Test
    void statusResponseIsUnchangedFor765() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(765, 1), true).session();
        Writer writer = new Writer();
        writer.writeString("{\"version\":{\"name\":\"1.20.4\",\"protocol\":765}}");
        MinecraftPacket response = new MinecraftPacket(0x00, writer.bytes());
        Outcome outcome = session.handleServerPacket(response);
        assertEquals(response, outcome.toClient().get(0));
    }

    // ------------------------------------------------------------------
    // ログイン（763・offline）
    // ------------------------------------------------------------------

    @Test
    void loginStart763IsConvertedTo765() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false).session();
        Outcome outcome = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve", true)));
        assertEquals(1, outcome.toServer().size());
        MinecraftPacket forwarded = outcome.toServer().get(0);
        assertEquals(0x00, forwarded.id());
        Reader reader = new Reader(forwarded.payload());
        assertEquals("Steve", reader.readString());
        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                reader.readUuid());
        assertEquals("Steve", session.playerName());
    }

    @Test
    void login763WithOnlineModeStartsProxyAuth() {
        // 【担当1】763＋online-mode はもう切断しない。プロキシが認証する。
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), true).session();
        Outcome outcome = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve", true)));
        assertFalse(outcome.closeAfterSend());
        assertEquals(0, outcome.toServer().size());
        assertEquals(1, outcome.toClient().size());
        assertEquals(0x01, outcome.toClient().get(0).id()); // Encryption Request
        assertFalse(session.isClosed());
    }

    @Test
    void encryptionRequestFor763Disconnects() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false).session();
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve", true)));
        Outcome outcome = session.handleServerPacket(new MinecraftPacket(0x01, new byte[0]));
        assertTrue(outcome.closeAfterSend());
        String reason = new Reader(outcome.toClient().get(0).payload()).readString();
        assertTrue(reason.contains("online-mode"), reason);
    }

    // ------------------------------------------------------------------
    // 設定フェーズ → Join Game
    // ------------------------------------------------------------------

    @Test
    void configurationIsConsumedAndJoinGameTranslated() {
        ProxySession session = joined763Session();
        assertEquals(2, session.registries().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
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
    }

    @Test
    void configKeepAliveAndPingAreAnswered() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false).session();
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve", true)));
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        Writer keepAlive = new Writer();
        keepAlive.writeLong(1234L);
        Outcome alive = session.handleServerPacket(
                new MinecraftPacket(0x03, keepAlive.bytes()));
        assertEquals(1, alive.toServer().size());
        assertEquals(0x03, alive.toServer().get(0).id());
        assertEquals(1234L, new Reader(alive.toServer().get(0).payload()).readLong());
        Writer ping = new Writer();
        ping.writeInt(77);
        Outcome pong = session.handleServerPacket(new MinecraftPacket(0x04, ping.bytes()));
        assertEquals(0x04, pong.toServer().get(0).id());
        assertEquals(77, new Reader(pong.toServer().get(0).payload()).readInt());
    }

    @Test
    void configAddResourcePackIsSentAfterJoinGame() {
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false).session();
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve", true)));
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        session.handleServerPacket(new MinecraftPacket(0x05, registryDataPayload()));
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer pack = new Writer();
        pack.writeUuid(uuid);
        pack.writeString("http://127.0.0.1:25567/detergentmc-pack.zip");
        pack.writeString("0123456789abcdef0123456789abcdef01234567");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome dropped = session.handleServerPacket(new MinecraftPacket(0x07, pack.bytes()));
        assertTrue(dropped.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());
        session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        assertEquals(0, session.pendingPackCount());
        // クライアントの応答は UUID 付きでバニラへ。
        Writer response = new Writer();
        response.writeVarInt(0);
        Outcome toServer = session.handleClientPacket(
                new MinecraftPacket(0x24, response.bytes()));
        assertEquals(0x28, toServer.toServer().get(0).id());
        assertEquals(uuid, new Reader(toServer.toServer().get(0).payload()).readUuid());
    }

    @Test
    void configAddResourcePackIsSentAs763PackAfterJoinGame() {
        ResourcePack pack763 = new ResourcePack(
                "http://127.0.0.1:25567/detergentmc-pack-1.20.1.zip",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                false, "resource-pack.prompt");
        ProxySession session = ProxySession.resolveHandshake(
                handshakePayload(763, 2), false, pack763).session();
        assertEquals(pack763, session.pack763());
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve", true)));
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        session.handleServerPacket(new MinecraftPacket(0x05, registryDataPayload()));
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer pack = new Writer();
        pack.writeUuid(uuid);
        pack.writeString("http://127.0.0.1:25567/detergentmc-pack.zip");
        pack.writeString("0123456789abcdef0123456789abcdef01234567");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        session.handleServerPacket(new MinecraftPacket(0x07, pack.bytes()));
        session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x40, join.toClient().get(1).id());
        // 763クライアントには763用パックのURLとSHA-1が出る。
        Reader reader = new Reader(join.toClient().get(1).payload());
        assertEquals("http://127.0.0.1:25567/detergentmc-pack-1.20.1.zip",
                reader.readString());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", reader.readString());
        assertFalse(reader.readBoolean());
        // 応答はバニラのパックのUUIDで返す（URL／SHA-1だけ置き換えたため）。
        Writer response = new Writer();
        response.writeVarInt(3);
        Outcome toServer = session.handleClientPacket(
                new MinecraftPacket(0x24, response.bytes()));
        assertEquals(0x28, toServer.toServer().get(0).id());
        Reader serverReader = new Reader(toServer.toServer().get(0).payload());
        assertEquals(uuid, serverReader.readUuid());
        assertEquals(3, serverReader.readVarInt());
    }

    // ------------------------------------------------------------------
    // プレイ中の翻訳
    // ------------------------------------------------------------------

    @Test
    void keepAliveAndMovementAreRemappedByIdOnly() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        // サーバー→クライアントの Keep Alive：0x24→0x23、ペイロードはそのまま。
        Writer alive = new Writer();
        alive.writeLong(99L);
        Outcome toClient = session.handleServerPacket(
                new MinecraftPacket(0x24, alive.bytes()));
        assertEquals(0x23, toClient.toClient().get(0).id());
        assertEquals(99L, new Reader(toClient.toClient().get(0).payload()).readLong());
        // クライアント→サーバーの Keep Alive：0x12→0x15。
        Outcome toServer = session.handleClientPacket(
                new MinecraftPacket(0x12, alive.bytes()));
        assertEquals(0x15, toServer.toServer().get(0).id());
        // 移動：0x14→0x17、ペイロードはそのまま。
        Writer move = new Writer();
        move.writeDouble(1.5);
        move.writeDouble(64.0);
        move.writeDouble(-2.5);
        move.writeBoolean(true);
        Outcome moved = session.handleClientPacket(new MinecraftPacket(0x14, move.bytes()));
        assertEquals(0x17, moved.toServer().get(0).id());
        assertArrayEquals(move.bytes(), moved.toServer().get(0).payload());
        // 位置と向き：0x15→0x18。
        Outcome moved2 = session.handleClientPacket(new MinecraftPacket(0x15, move.bytes()));
        assertEquals(0x18, moved2.toServer().get(0).id());
    }

    @Test
    void chatMessageStringOnlyIsSynthesized() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        Writer chat = new Writer();
        chat.writeString("hello");
        Outcome outcome = session.handleClientPacket(new MinecraftPacket(0x05, chat.bytes()));
        assertEquals(0x05, outcome.toServer().get(0).id());
        Reader reader = new Reader(outcome.toServer().get(0).payload());
        assertEquals("hello", reader.readString());
        reader.readLong(); // タイムスタンプ
        reader.readLong(); // salt
        assertFalse(reader.readBoolean()); // 署名なし
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining() - 3);
    }

    @Test
    void windowItemsAreRemapped() {
        ProxySession session = joined763Session();
        Writer items = new Writer();
        items.writeByte(0); // window id
        items.writeVarInt(0); // state id
        items.writeVarInt(0); // スロットなし
        items.writeBoolean(false); // carried item なし
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x13, items.bytes()));
        assertEquals(1, outcome.toClient().size());
        assertEquals(0x12, outcome.toClient().get(0).id());
        assertArrayEquals(items.bytes(), outcome.toClient().get(0).payload());
    }

    @Test
    void bossBarAndExplosionAreTranslated() {
        // 担当3：Boss Bar と Explosion は落とさず、763形式に変換して送る。
        ProxySession session = joined763Session();
        Writer bossBar = new Writer();
        bossBar.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        bossBar.writeVarInt(1); // 削除
        Outcome bossOutcome = session.handleServerPacket(
                new MinecraftPacket(0x0A, bossBar.bytes()));
        assertEquals(1, bossOutcome.toClient().size());
        assertEquals(0x0B, bossOutcome.toClient().get(0).id());
        Writer explosion = new Writer();
        explosion.writeDouble(1.0);
        explosion.writeDouble(2.0);
        explosion.writeDouble(3.0);
        explosion.writeFloat(4.0f);
        explosion.writeVarInt(0); // 影響ブロック無し
        explosion.writeFloat(0.0f);
        explosion.writeFloat(0.0f);
        explosion.writeFloat(0.0f);
        explosion.writeVarInt(0); // block_interaction_type
        explosion.writeVarInt(22); // small：explosion
        explosion.writeVarInt(21); // large：explosion_emitter
        explosion.writeVarInt(5); // sound：レジストリID
        Outcome explosionOutcome = session.handleServerPacket(
                new MinecraftPacket(0x1E, explosion.bytes()));
        assertEquals(1, explosionOutcome.toClient().size());
        assertEquals(0x1D, explosionOutcome.toClient().get(0).id());
    }

    @Test
    void resetScoreBecomesRemoveScoreboardScore() {
        ProxySession session = joined763Session();
        Writer reset = new Writer();
        reset.writeString("Steve");
        reset.writeBoolean(true);
        reset.writeString("obj");
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x42, reset.bytes()));
        assertEquals(1, outcome.toClient().size());
        assertEquals(0x5B, outcome.toClient().get(0).id());
        Reader reader = new Reader(outcome.toClient().get(0).payload());
        assertEquals("Steve", reader.readString());
        assertEquals(1, reader.readVarInt()); // action 1＝削除
        assertEquals("obj", reader.readString());
    }

    @Test
    void profilelessChatIsTranslated() {
        ProxySession session = joined763Session();
        Writer chat = new Writer();
        chat.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("hi"))));
        chat.writeVarInt(0);
        chat.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Steve"))));
        chat.writeBoolean(false);
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x1C, chat.bytes()));
        assertEquals(0x1B, outcome.toClient().get(0).id());
        Reader reader = new Reader(outcome.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertEquals("{\"text\":\"Steve\"}", reader.readString());
    }

    @Test
    void systemChatIsTranslated() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        Writer chat = new Writer();
        chat.writeAnonymousNbt(new Tag.CompoundTag(
                Map.of("text", new Tag.StringTag("Welcome!"))));
        chat.writeBoolean(false);
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x69, chat.bytes()));
        assertEquals(0x64, outcome.toClient().get(0).id());
        assertEquals("{\"text\":\"Welcome!\"}",
                new Reader(outcome.toClient().get(0).payload()).readString());
    }

    @Test
    void gameEvent13IsDroppedAndSpawnPositionIsForwarded() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        // Game Event 13：落とす。
        Outcome dropped = session.handleServerPacket(
                new MinecraftPacket(0x20, new byte[]{13, 0, 0, 0, 0}));
        assertTrue(dropped.toClient().isEmpty());
        // Game Event 1（雨開始）：0x1F に付け替えて送る。
        Outcome rain = session.handleServerPacket(
                new MinecraftPacket(0x20, new byte[]{1, 0, 0, 0, 0}));
        assertEquals(0x1F, rain.toClient().get(0).id());
        // Spawn Position：0x50 に付け替えて送る。
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x54, new byte[]{0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals(0x50, spawn.toClient().get(0).id());
    }

    @Test
    void unknownPlayPacketIsDroppedFor763() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        // 0x67 Start Configuration は763に無い。
        Outcome dropped = session.handleServerPacket(
                new MinecraftPacket(0x67, new byte[0]));
        assertTrue(dropped.toClient().isEmpty());
    }

    @Test
    void playDisconnectIsTranslatedAndCloses() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        Writer reason = new Writer();
        reason.writeAnonymousNbt(new Tag.StringTag("Bye"));
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x1B, reason.bytes()));
        assertEquals(0x1A, outcome.toClient().get(0).id());
        assertEquals("\"Bye\"", new Reader(outcome.toClient().get(0).payload()).readString());
        assertTrue(outcome.closeAfterSend());
    }
}
