package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * 担当3：Respawn のセッション経由の変換と、プレイ中の Start Configuration
 * （再設定）の肩代わりの検証。Netty は使わず、セッションの入出力だけを見る。
 */
class ProxySessionReconfigTest {

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
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve")));
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        session.handleServerPacket(new MinecraftPacket(0x05, registryDataPayload()));
        session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        session.handleServerPacket(new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        return session;
    }

    /** 765の Respawn ペイロード（ネザーへ移動、死亡位置あり）。 */
    private static byte[] respawn765Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:the_nether");
        writer.writeString("minecraft:the_nether");
        writer.writeLong(12345L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeString("minecraft:overworld");
        writer.writePositionRaw(987654321L);
        writer.writeVarInt(40);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Respawn（セッション経由）
    // ------------------------------------------------------------------

    @Test
    void respawnIsTranslatedThroughSession() {
        ProxySession session = joined763Session();
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, outcome.toClient().size());
        assertEquals(0x41, outcome.toClient().get(0).id());
        // 763形式：copyMetadata が death の前に来る。
        Reader reader = new Reader(outcome.toClient().get(0).payload());
        assertEquals("minecraft:the_nether", reader.readString());
        assertEquals("minecraft:the_nether", reader.readString());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.readByte());
        assertEquals(255, reader.readUnsignedByte());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean()); // copyMetadata
        assertTrue(reader.readBoolean()); // hasDeath
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(987654321L, reader.readPositionRaw());
        assertEquals(40, reader.readVarInt());
        assertEquals(0, reader.remaining());
        // Respawn の後もセッションはプレイ状態のまま。
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
    }

    @Test
    void otherTranslatedPacketsAreForwardedThroughSession() {
        ProxySession session = joined763Session();
        // Boss Bar（削除）：0x0A → 0x0B。
        Writer bossBar = new Writer();
        bossBar.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        bossBar.writeVarInt(1);
        assertEquals(0x0B, session.handleServerPacket(
                new MinecraftPacket(0x0A, bossBar.bytes())).toClient().get(0).id());
        // Teams（削除）：0x5E → 0x5A。
        Writer teams = new Writer();
        teams.writeString("red");
        teams.writeByte(1);
        assertEquals(0x5A, session.handleServerPacket(
                new MinecraftPacket(0x5E, teams.bytes())).toClient().get(0).id());
        // Tab Complete（候補なし）：0x10 → 0x0F。
        Writer tab = new Writer();
        tab.writeVarInt(1);
        tab.writeVarInt(0);
        tab.writeVarInt(0);
        tab.writeVarInt(0);
        assertEquals(0x0F, session.handleServerPacket(
                new MinecraftPacket(0x10, tab.bytes())).toClient().get(0).id());
        // Advancements（空）：0x70 → 0x69。
        Writer advancements = new Writer();
        advancements.writeBoolean(false);
        advancements.writeVarInt(0);
        advancements.writeVarInt(0);
        advancements.writeVarInt(0);
        assertEquals(0x69, session.handleServerPacket(
                new MinecraftPacket(0x70, advancements.bytes())).toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // Start Configuration（再設定の肩代わり）
    // ------------------------------------------------------------------

    @Test
    void startConfigurationEntersReconfigurationWithoutClientPacket() {
        ProxySession session = joined763Session();
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x67, new byte[0]));
        // クライアントには何も送らない（763に設定フェーズは無い）。
        assertTrue(outcome.toClient().isEmpty());
        assertTrue(outcome.toServer().isEmpty());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertTrue(session.isReconfiguring());
    }

    @Test
    void clientPacketsAreQueuedDuringReconfigurationAndReplayed() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x67, new byte[0]));
        // 再設定中にクライアントが移動パケットを送る：サーバーへは送らず溜める。
        Writer position = new Writer();
        position.writeDouble(10.0);
        position.writeDouble(64.0);
        position.writeDouble(20.0);
        position.writeBoolean(true);
        Outcome queued = session.handleClientPacket(
                new MinecraftPacket(0x14, position.bytes()));
        assertTrue(queued.toServer().isEmpty());
        assertEquals(1, session.queuedClientPacketCount());
        // Finish Configuration：Acknowledge（0x02）の後に溜めた移動を送る。
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(0x02, new byte[0]));
        assertEquals(List.of(0x02, 0x17),
                finish.toServer().stream().map(MinecraftPacket::id).toList());
        assertEquals(0, session.queuedClientPacketCount());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertFalse(session.isReconfiguring());
        // 再設定が終われば、移動はそのまま送られる。
        Outcome after = session.handleClientPacket(
                new MinecraftPacket(0x14, position.bytes()));
        assertEquals(List.of(0x17),
                after.toServer().stream().map(MinecraftPacket::id).toList());
    }

    @Test
    void registryDataDuringReconfigurationIsKept() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x67, new byte[0]));
        Outcome outcome = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertTrue(outcome.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));
        assertTrue(session.registries().containsKey("minecraft:worldgen/biome"));
        session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
    }

    @Test
    void resourcePackDuringReconfigurationIsSentAfterFinish() {
        ProxySession session = joined763Session();
        session.handleServerPacket(new MinecraftPacket(0x67, new byte[0]));
        // 再設定中に Add Resource Pack（設定フェーズ 0x07）が来る。
        Writer pack = new Writer();
        pack.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        pack.writeString("http://127.0.0.1:25567/pack.zip");
        pack.writeString("abc");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome add = session.handleServerPacket(new MinecraftPacket(0x07, pack.bytes()));
        assertTrue(add.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());
        // Finish Configuration：クライアントへ763の Resource Pack Send（0x40）。
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(0x02, new byte[0]));
        assertEquals(List.of(0x02),
                finish.toServer().stream().map(MinecraftPacket::id).toList());
        assertEquals(1, finish.toClient().size());
        assertEquals(0x40, finish.toClient().get(0).id());
        assertEquals("http://127.0.0.1:25567/pack.zip",
                new Reader(finish.toClient().get(0).payload()).readString());
        assertEquals(0, session.pendingPackCount());
    }
}
