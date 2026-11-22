package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame404;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 404（1.13.2）クライアント・サーバーのプロキシ統合テスト。
 * 404 は 477 と形式が大きく違う（Join Game に difficulty、座標の
 * 詰め方が旧式、Trade List が無い）ため、477 の経路とは別に
 * 404 専用経路を通ることを固定する。
 */
class ProxySession404Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static ProxySession.HandshakeResult handshake(
            int clientProtocol, int nextState, int serverProtocol) {
        Writer writer = new Writer();
        writer.writeVarInt(clientProtocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return ProxySession.resolveHandshake(writer.bytes(), false,
                null, null, ProxyAuthConfig.defaults(), null, serverProtocol);
    }

    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(clientProtocol, 2, serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        Writer writer = new Writer();
        writer.writeString("Steve");
        session.handleClientPacket(new MinecraftPacket(0x00, writer.bytes()));
        return session;
    }

    /** 758 形式の Login Success（UUID＋名前だけ。実バニラ 1.18.2 には properties 自体が無い）。 */
    private static byte[] loginSuccess758() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        return writer.bytes();
    }

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

    /** 404 の Join Game ペイロード（difficulty あり・viewDistance 無し）。 */
    private static byte[] joinGame404Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(1);
        writer.writeByte(20);
        writer.writeString("flat");
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 477 の Join Game ペイロード（difficulty 無し・viewDistance あり）。 */
    private static byte[] joinGame477Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(20);
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 旧式サーバーの Login Success（{UUID文字列, 名前}）。 */
    private static byte[] loginSuccessOld() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        return writer.bytes();
    }

    @Test
    void handshake404IsAccepted() {
        ProxySession.HandshakeResult result = handshake(404, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(404, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake403IsRejected() {
        ProxySession.HandshakeResult result = handshake(403, 2, 763);
        assertTrue(result.disconnect());
    }

    @Test
    void join404Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(404, 758);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess758()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        Reader reader = new Reader(success.toClient().get(0).payload());
        assertEquals(PLAYER_UUID.toString(), reader.readString());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void join404Against758JoinGameIs404Form() {
        ProxySession session = loginTo(404, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame758Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        // 758 には difficulty が無いため normal（2）が合成される。
        assertEquals(2, parsed.difficulty());
    }

    @Test
    void join404Against404JoinGameSurvives() {
        ProxySession session = loginTo(404, 404);
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessOld()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame404Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(20, parsed.maxPlayers());
    }

    @Test
    void join404ClientAgainst477Server() {
        // 477 の Join Game（difficulty 無し）→ 404 形式（difficulty 合成）。
        ProxySession session = loginTo(404, 477);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame477Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(2, parsed.difficulty());
    }

    @Test
    void join477ClientAgainst404Server() {
        // 404 の Join Game（difficulty あり）→ 477 形式（difficulty は落ちる）。
        ProxySession session = loginTo(477, 404);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame404Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        PlayTranslator.JoinGame498 parsed =
                PlayTranslator.parseJoinGame498(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
    }

    @Test
    void real1132FlowAgainst477Client() throws Exception {
        // 実バニラ 1.13.2 が参加直後に送るパケット列（プローブで採取）を
        // 477 クライアント向けに流し、例外なく変換できることを確認する。
        java.nio.file.Path dir = java.nio.file.Path.of(
                "/home/hatch/workspace/mcdata404/flow");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.isDirectory(dir), "採取データが無いためスキップ");
        ProxySession session = loginTo(477, 404);
        java.util.List<java.nio.file.Path> files;
        try (var stream = java.nio.file.Files.list(dir)) {
            files = stream.filter(f -> f.getFileName().toString().endsWith(".bin"))
                    .sorted().toList();
        }
        for (java.nio.file.Path file : files) {
            String name = file.getFileName().toString();
            int id = Integer.parseInt(name.substring(4, 6), 16);
            byte[] payload = java.nio.file.Files.readAllBytes(file);
            try {
                session.handleServerPacket(new MinecraftPacket(id, payload));
            } catch (RuntimeException e) {
                org.junit.jupiter.api.Assertions.fail(
                        "パケット " + name + " で例外: " + e, e);
            }
        }
    }

    @Test
    void chat477To404ServerBecomes404Chat() {
        // 477 クライアントの Chat Message（0x03、文字列のみ）→ 404 の
        // Chat Message（0x02、文字列のみ）になること。
        ProxySession session = loginTo(477, 404);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGame404Payload()));
        Writer writer = new Writer();
        writer.writeString("hello from 477 to 1.13.2");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x02, out.toServer().get(0).id()); // 404 の Chat Message
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("hello from 477 to 1.13.2", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat404To758ServerStaysLegacyChat() {
        ProxySession session = loginTo(404, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        Writer writer = new Writer();
        writer.writeString("hello");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x02, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x03, out.toServer().get(0).id()); // 758 の Chat Message
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("hello", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat758To404ClientDropsSenderUuid() {
        ProxySession session = loginTo(404, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeByte(0);
        writer.writeUuid(PLAYER_UUID);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x0f, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x0e, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeListIsDroppedFor404Client() {
        // Trade List は 1.14 で追加されたため 404 には届かない。
        ProxySession session = loginTo(404, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        int tradeList758 = PacketIds.id(
                PacketIds.PacketName.PLAY_TRADE_LIST, 758).orElseThrow();
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(tradeList758, new byte[0]));
        assertEquals(0, out.toClient().size(),
                "404 に無いパケットはクライアントへ送らない");
    }

    @Test
    void acknowledgePlayerDiggingIsDroppedFor404Client() {
        // 掘削 ack は 1.14.4 で追加。404 クライアントには届かない。
        ProxySession session = loginTo(404, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        Writer dig = new Writer();
        dig.writeVarInt(0); // status（started digging、404 では varint）
        dig.writePositionRaw(0L); // location
        dig.writeByte(0); // face
        session.handleClientPacket(new MinecraftPacket(0x18, dig.bytes()));
        Writer ack = new Writer();
        ack.writePositionRaw(0L);
        ack.writeVarInt(1);
        ack.writeVarInt(0);
        ack.writeBoolean(true);
        Outcome dropped = session.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(0, dropped.toClient().size(),
                "404 に無いパケットはクライアントへ送らない");
    }
}
