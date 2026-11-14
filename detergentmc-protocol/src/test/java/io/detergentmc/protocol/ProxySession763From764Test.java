package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
 * 内部764（1.20.2サーバー）→763クライアントのプレイ・ペイロード変換を
 * セッション単位で確認する。NBT付きパケットが落とされず、763向けに
 * 変換されて届くこと（以前は TODO として落とされていた）。
 */
class ProxySession763From764Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

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

    /**
     * 763・offline の参加フローを Join Game まで進めたセッションを作る
     * （内部764、上向き）。この後のプレイ状態のパケットは
     * {@code handleServerPlay763From764} が扱う。
     */
    private static ProxySession joined763Session() {
        ProxySession.HandshakeResult handshake = handshake764(763, 2, false);
        assertFalse(handshake.disconnect());
        assertFalse(handshake.relay());
        ProxySession session = handshake.session();
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertTrue(registry.toClient().isEmpty());
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame764Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        return session;
    }

    /** 764のスロット1個（NBTなし）。 */
    private static byte[] slot764(int itemId, int count) {
        Writer writer = new Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0);
        return writer.bytes();
    }

    /** 763のスロット1個（NBTなし）。 */
    private static byte[] slot763(int itemId, int count) {
        Writer writer = new Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0);
        return writer.bytes();
    }

    @Test
    void setSlot764To763IsDelivered() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(5);
        writer.writeShort(3);
        writer.writeBytes(slot764(1, 2));
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x15, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x14, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void windowItems764To763IsDelivered() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeUnsignedByte(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeBytes(slot764(1, 1));
        writer.writeBytes(new byte[]{0});
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x13, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x12, out.toClient().get(0).id());
    }

    @Test
    void entityEquipment764To763IsDelivered() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeUnsignedByte(0x00);
        writer.writeBytes(slot764(1, 1));
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x57, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x55, out.toClient().get(0).id());
    }

    @Test
    void entityMetadata764To763DropsWolfVariantEntryButKeepsPacket() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeVarInt(42);
        // byte エントリ。
        writer.writeUnsignedByte(0);
        writer.writeVarInt(0);
        writer.writeByte(5);
        // 狼の variant（type 23）は落とす。
        writer.writeUnsignedByte(1);
        writer.writeVarInt(23);
        writer.writeVarInt(1);
        writer.writeUnsignedByte(0xFF);
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x54, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x52, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(42, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(5, reader.readByte());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockEntityDataUnknownTypeIsDropped() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writePositionRaw(0L);
        writer.writeVarInt(999);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x07, writer.bytes()));
        assertTrue(out.toClient().isEmpty());
    }

    @Test
    void blockEntityDataKnownTypeIsDelivered() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writePositionRaw(0L);
        writer.writeVarInt(1);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x07, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x08, out.toClient().get(0).id());
    }

    @Test
    void entityEffect764To763AddsOne() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        writer.writeByte(1);
        writer.writeVarInt(100);
        writer.writeByte(0);
        writer.writeBoolean(false);
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x6E, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x6C, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void respawn764To763IsDelivered() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        Outcome out = session.handleServerPacket(
                new MinecraftPacket(0x43, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x41, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(99L, reader.readLong());
        assertEquals(0, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean()); // copyMetadata
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void windowClick763To764IsConverted() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeUnsignedByte(1);
        writer.writeVarInt(2);
        writer.writeShort(5);
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeVarInt(1);
        writer.writeShort(5);
        writer.writeBytes(slot763(1, 1));
        writer.writeBytes(new byte[]{0});
        Outcome out = session.handleClientPacket(
                new MinecraftPacket(0x0B, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x0D, out.toServer().get(0).id());
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readUnsignedByte());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCreativeSlot763To764IsConverted() {
        ProxySession session = joined763Session();
        Writer writer = new Writer();
        writer.writeShort(5);
        writer.writeBytes(slot763(1, 1));
        Outcome out = session.handleClientPacket(
                new MinecraftPacket(0x2B, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x2E, out.toServer().get(0).id());
    }
}
