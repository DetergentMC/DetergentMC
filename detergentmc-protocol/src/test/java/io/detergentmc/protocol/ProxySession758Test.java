package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 758（1.18.2）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession759Test} と同じ段取りで、758の番号・形式で確かめる。
 * 758は最古の対応バージョンで、759を基準にする。758クライアントは
 * 758→759→760→761→762→763 へ段階変換して送り、サーバーからは
 * 763→762→761→760→759→758 へ五段で変換して返す。758には設定フェーズが
 * 無く、チャット署名も無い（旧式 chat 0x0f／0x03）。署名付きチャットは
 * 758に送らない。Join Game は 0x26、Resource Pack は 0x3c。
 * Block Dig／Block Place／Use Item には sequence を振り、掘削のものは
 * 記録してサーバーからの ack を758の旧式 ack に合成する（block=0、
 * successful は完了した掘削だけ true）。絵画の Spawn Entity は
 * バリアントが Entity Metadata で後から届くため保留し、バリアントが
 * 分かった時点で Spawn Entity Painting（0x03）を送る。758に無い
 * バリアント（earth／wind／water／fire）は落とす。
 */
class ProxySession758Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final byte[] SIG_256 = new byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            SIG_256[i] = (byte) i;
        }
    }

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

    /** 758の Login Start：名前だけ。 */
    private static byte[] loginStart758(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 758の Login Success（UUID＋名前だけ。実バニラ 1.18.2 には properties 自体が無い）。 */
    private static byte[] loginSuccess758Payload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        return writer.bytes();
    }

    /** 759／762／763／765の Login Success（properties 1件付き。strict bool なし）。 */
    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(1); // properties 1件
        writer.writeString("textures");
        writer.writeString("value");
        writer.writeBoolean(false); // signature 無し
        return writer.bytes();
    }

    /** 763の Join Game（codec は名前付き NBT、末尾に portalCooldown）。 */
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

    /** 759の Join Game（portalCooldown 無し。759と760は同形式）。 */
    private static byte[] joinGame759Payload() {
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
        return writer.bytes();
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        session.handleClientPacket(new MinecraftPacket(0x00,
                loginStart758("Steve")));
        return session;
    }

    /** 758クライアントが内部759サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined758Against759() {
        ProxySession session = loginTo(758, 759);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame759Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        return session;
    }

    /** 758クライアントが内部763サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined758Against763() {
        ProxySession session = loginTo(758, 763);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        return session;
    }

    /** 759 の Spawn Entity ペイロード（type＋objectData を引数で変える）。 */
    private static byte[] spawnEntity759Packet(int entityId, int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeUuid(PLAYER_UUID);
        writer.writeVarInt(type);
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeByte(64); // pitch
        writer.writeByte(32); // yaw
        writer.writeByte(64); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    /** 759 の Entity Metadata ペイロード（entityId＋index 8 の varint バリアント）。 */
    private static byte[] entityMetadata759Packet(int entityId, int variant) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeUnsignedByte(8); // index
        writer.writeVarInt(1); // type varint
        writer.writeVarInt(variant);
        writer.writeUnsignedByte(0xFF); // 終端
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // ハンドシェイク
    // ------------------------------------------------------------------

    @Test
    void handshake758IsAccepted() {
        ProxySession.HandshakeResult result = handshake(
                758, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(758, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake757IsRejected() {
        // 758 が最古。757 以下は拒否される。
        ProxySession.HandshakeResult result = handshake(
                757, 2, false, ProxyAuthConfig.defaults(), 763);
        assertTrue(result.disconnect());
    }

    // ------------------------------------------------------------------
    // 758クライアント → 内部759（Join Game・Login Success）
    // ------------------------------------------------------------------

    @Test
    void join758Against759DropsPropertiesFromLoginSuccess() {
        ProxySession session = loginTo(758, 759);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        Reader reader = new Reader(success.toClient().get(0).payload());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining()); // properties 配列は落とされる
    }

    @Test
    void join758Against759JoinGameIs026WithNbtDimension() {
        ProxySession session = loginTo(758, 759);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame759Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldName());
        assertEquals(new Tag.CompoundTag(Map.of()), parsed.dimension());
    }

    @Test
    void join758Against763JoinGameIs026WithNbtDimension() {
        ProxySession session = loginTo(758, 763);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldName());
        assertEquals(new Tag.CompoundTag(Map.of()), parsed.dimension());
    }

    // ------------------------------------------------------------------
    // 758クライアント → 内部759（チャット）
    // ------------------------------------------------------------------

    @Test
    void chat758ToServerBecomesChatMessage759() {
        ProxySession session = joined758Against759();
        Writer writer = new Writer();
        writer.writeString("hello");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x04, out.toServer().get(0).id()); // Chat Message（759）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("hello", reader.readString());
        assertTrue(reader.readLong() > 0L); // timestamp
        assertEquals(0L, reader.readLong()); // salt
        assertArrayEquals(new byte[0], reader.readByteArray()); // 署名は空
        assertFalse(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat758SlashToServerBecomesChatCommand759() {
        ProxySession session = joined758Against759();
        Writer writer = new Writer();
        writer.writeString("/say hi");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x03, out.toServer().get(0).id()); // Chat Command（759）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("say hi", reader.readString());
        assertTrue(reader.readLong() > 0L); // timestamp
        assertEquals(0L, reader.readLong()); // salt
        assertEquals(0, reader.readVarInt()); // argumentSignatures（空）
        assertFalse(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChat759To758BecomesOldChat() {
        ProxySession session = joined758Against759();
        Writer writer = new Writer();
        writer.writeString("hello"); // signedChatContent
        writer.writeBoolean(true); // unsigned あり
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeVarInt(0); // type
        writer.writeUuid(PLAYER_UUID); // sender
        writer.writeString("Steve"); // senderName
        writer.writeBoolean(false); // senderTeam 無し
        writer.writeLong(100L); // timestamp
        writer.writeLong(200L); // salt
        writer.writeByteArray(SIG_256); // signature
        Outcome chat = session.handleServerPacket(new MinecraftPacket(0x30, writer.bytes()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x0f, chat.toClient().get(0).id()); // 旧式 chat（758）
        Reader reader = new Reader(chat.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        assertEquals(0, reader.readByte()); // position: chat
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChat763To758BecomesOldChatThroughChain() {
        ProxySession session = joined758Against763();
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID); // sender
        writer.writeVarInt(7); // index（759 には無い）
        writer.writeBoolean(true); // 署名あり
        writer.writeBytes(SIG_256);
        writer.writeString("hello");
        writer.writeLong(100L);
        writer.writeLong(200L);
        writer.writeVarInt(0); // previousMessages 0件
        writer.writeBoolean(false); // unsigned 無し
        writer.writeVarInt(0); // filterType
        writer.writeVarInt(0); // chatType
        writer.writeString("Steve");
        writer.writeBoolean(false); // target 無し
        Outcome chat = session.handleServerPacket(new MinecraftPacket(0x35, writer.bytes()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x0f, chat.toClient().get(0).id()); // 旧式 chat（758）
        Reader reader = new Reader(chat.toClient().get(0).payload());
        assertEquals("{\"text\":\"hello\"}", reader.readString());
        assertEquals(0, reader.readByte()); // position: chat
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void systemChat759To758KeepsContentAndType() {
        ProxySession session = joined758Against759();
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"sys\"}");
        writer.writeVarInt(1); // type: system
        Outcome chat = session.handleServerPacket(new MinecraftPacket(0x5f, writer.bytes()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x0f, chat.toClient().get(0).id());
        Reader reader = new Reader(chat.toClient().get(0).payload());
        assertEquals("{\"text\":\"sys\"}", reader.readString());
        assertEquals(1, reader.readByte()); // position = type
        assertEquals(new UUID(0L, 0L), reader.readUuid()); // sender は nil UUID
        assertEquals(0, reader.remaining());
    }

    @Test
    void serverData759IsDroppedFor758() {
        ProxySession session = joined758Against759();
        Writer writer = new Writer();
        writer.writeBoolean(true); // motd あり
        writer.writeString("{\"text\":\"motd\"}");
        writer.writeBoolean(false); // icon 無し
        writer.writeBoolean(true); // previewsChat
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x3f, writer.bytes()));
        assertEquals(0, out.toClient().size()); // Server Data は758に無いため落とす
    }

    @Test
    void chatPreview759IsDroppedFor758() {
        ProxySession session = joined758Against759();
        Writer writer = new Writer();
        writer.writeVarInt(7); // queryId
        writer.writeBoolean(true); // message あり
        writer.writeString("hello");
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x0c, writer.bytes()));
        assertEquals(0, out.toClient().size()); // Chat Preview は758に無いため落とす
    }

    // ------------------------------------------------------------------
    // 758クライアント → 内部759（出現・絵画）
    // ------------------------------------------------------------------

    @Test
    void spawnEntityPlayer759ToNamedEntitySpawn758() {
        ProxySession session = joined758Against759();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, spawnEntity759Packet(42, 116, 0)));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x04, spawn.toClient().get(0).id()); // Named Entity Spawn（758）
        Reader reader = new Reader(spawn.toClient().get(0).payload());
        assertEquals(42, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(32, reader.readByte()); // yaw
        assertEquals(64, reader.readByte()); // pitch
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntityLiving759ToSpawnLiving758() {
        ProxySession session = joined758Against759();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, spawnEntity759Packet(42, 2, 0))); // armor_stand
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x02, spawn.toClient().get(0).id()); // Spawn Entity Living（758）
        Reader reader = new Reader(spawn.toClient().get(0).payload());
        assertEquals(42, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals(1, reader.readVarInt()); // 758 の armor_stand
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(32, reader.readByte()); // yaw
        assertEquals(64, reader.readByte()); // pitch
        assertEquals(64, reader.readByte()); // headPitch
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntityPainting759HeldUntilMetadata758() {
        ProxySession session = joined758Against759();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, spawnEntity759Packet(42, 63, 2))); // painting、方向 2
        assertEquals(0, spawn.toClient().size()); // バリアント待ちのため保留
        Outcome meta = session.handleServerPacket(
                new MinecraftPacket(0x4d, entityMetadata759Packet(42, 29))); // donkey_kong
        assertEquals(1, meta.toClient().size());
        assertEquals(0x03, meta.toClient().get(0).id()); // Spawn Entity Painting（758）
        Reader reader = new Reader(meta.toClient().get(0).payload());
        assertEquals(42, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals(25, reader.readVarInt()); // 759 の 29→758 の 25
        assertEquals(ChunkTranslator.packBlockPos(1, 64, -2), reader.readPositionRaw()); // 位置
        assertEquals(2, reader.readByte()); // direction
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntityPainting759UnknownVariantIsDropped() {
        ProxySession session = joined758Against759();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, spawnEntity759Packet(42, 63, 2))); // painting
        assertEquals(0, spawn.toClient().size());
        Outcome meta = session.handleServerPacket(
                new MinecraftPacket(0x4d, entityMetadata759Packet(42, 25))); // earth（758に無い）
        assertEquals(0, meta.toClient().size()); // DROP。絵画は出ない
    }

    @Test
    void destroyEntities759ClearsHeldPainting() {
        ProxySession session = joined758Against759();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, spawnEntity759Packet(42, 63, 2))); // painting
        assertEquals(0, spawn.toClient().size());
        Writer destroy = new Writer();
        destroy.writeVarInt(1);
        destroy.writeVarInt(42);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x38, destroy.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x3a, out.toClient().get(0).id()); // Destroy Entities（758）
        // 保留が掃除されたため、後から来たメタデータは絵画を出さない。
        Outcome meta = session.handleServerPacket(
                new MinecraftPacket(0x4d, entityMetadata759Packet(42, 29)));
        assertEquals(1, meta.toClient().size());
        assertEquals(0x4d, meta.toClient().get(0).id()); // 通常の Entity Metadata として通る
    }

    // ------------------------------------------------------------------
    // 758クライアント → 内部759（掘削・ビーコン・リソースパック）
    // ------------------------------------------------------------------

    @Test
    void blockDig758AddsSequenceAndAckIsSynthesized() {
        ProxySession session = joined758Against759();
        Writer dig = new Writer();
        dig.writeVarInt(2); // status: finished digging
        dig.writePositionRaw(0x123456789L); // location
        dig.writeByte(1); // face: top
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x1a, dig.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x1c, out.toServer().get(0).id()); // Block Dig（759）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(2, reader.readVarInt()); // status
        assertEquals(0x123456789L, reader.readPositionRaw()); // location
        assertEquals(1, reader.readByte()); // face
        int sequence = reader.readVarInt(); // 末尾の sequence
        assertEquals(0, reader.remaining());
        // サーバーからの ack（sequence だけ）→758の旧式 ack を合成する。
        Writer ack = new Writer();
        ack.writeVarInt(sequence);
        Outcome ackOut = session.handleServerPacket(new MinecraftPacket(0x05, ack.bytes()));
        assertEquals(1, ackOut.toClient().size());
        assertEquals(0x08, ackOut.toClient().get(0).id()); // Acknowledge Player Digging（758）
        Reader ackReader = new Reader(ackOut.toClient().get(0).payload());
        assertEquals(0x123456789L, ackReader.readPositionRaw()); // location
        assertEquals(0, ackReader.readVarInt()); // block（空気で補完）
        assertEquals(2, ackReader.readVarInt()); // status
        assertTrue(ackReader.readBoolean()); // successful（完了した掘削だけ true）
        assertEquals(0, ackReader.remaining());
    }

    @Test
    void blockDig758StartedAckIsNotSuccessful() {
        ProxySession session = joined758Against759();
        Writer dig = new Writer();
        dig.writeVarInt(0); // status: started digging
        dig.writePositionRaw(0x123456789L);
        dig.writeByte(1); // face: top
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x1a, dig.bytes()));
        Reader reader = new Reader(out.toServer().get(0).payload());
        reader.readVarInt();
        reader.readPositionRaw();
        reader.readByte();
        int sequence = reader.readVarInt();
        Writer ack = new Writer();
        ack.writeVarInt(sequence);
        Outcome ackOut = session.handleServerPacket(new MinecraftPacket(0x05, ack.bytes()));
        assertEquals(1, ackOut.toClient().size());
        Reader ackReader = new Reader(ackOut.toClient().get(0).payload());
        assertEquals(0x123456789L, ackReader.readPositionRaw());
        assertEquals(0, ackReader.readVarInt());
        assertEquals(0, ackReader.readVarInt()); // status
        assertFalse(ackReader.readBoolean()); // successful は false（開始した掘削）
        assertEquals(0, ackReader.remaining());
    }

    @Test
    void setBeaconEffect758MinusOneBecomesAbsent() {
        ProxySession session = joined758Against759();
        Writer writer = new Writer();
        writer.writeVarInt(-1); // primary 無し
        writer.writeVarInt(5); // secondary あり
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x24, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x26, out.toServer().get(0).id()); // Set Beacon Effect（759）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertFalse(reader.readBoolean()); // primary: absent
        assertTrue(reader.readBoolean()); // secondary: present
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playResourcePackOfferFallsBackToPack759For758() {
        ProxySession session = joined758Against763();
        session.setPack759(new ResourcePack(
                "https://127.0.0.1:25567/detergentmc-pack-1.19.zip",
                "0123456789abcdef0123456789abcdef01234567", false, null));
        // 内部763サーバーが送る旧式 Resource Pack（0x40：URL＋SHA-1＋required＋prompt）。
        Writer pack = new Writer();
        pack.writeString("https://example.invalid/vanilla.zip");
        pack.writeString("ffffffffffffffffffffffffffffffffffffffff");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome offer = session.handleServerPacket(new MinecraftPacket(0x40, pack.bytes()));
        // 758クライアントには 0x3c、759用パック（pack758 は Stage D まで無いため代替）の
        // URL／SHA-1 で送る。
        assertEquals(1, offer.toClient().size());
        assertEquals(0x3c, offer.toClient().get(0).id());
        Reader packReader = new Reader(offer.toClient().get(0).payload());
        assertEquals("https://127.0.0.1:25567/detergentmc-pack-1.19.zip",
                packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertEquals(false, packReader.readBoolean()); // required
        assertEquals(false, packReader.readBoolean()); // prompt 無し
        assertEquals(0, packReader.remaining());
    }

    // ------------------------------------------------------------------
    // 758／759クライアント → 内部774（チャット）
    // 2026-11-15の不具合：758／759クライアントのチャットが774サーバーに
    // 届かなかった。handleClientPlayTo768〜To775 が、既に763形式に直した
    // パケットの名前を clientProtocol（758／759）の表で引いていたため、
    // 763の Chat Message 0x05 を758では Client Information、759では
    // Client Status と取り違え、チャットが別パケットとして送られて消えた。
    // 修正：763-likeクライアントは763で名前を引く（packetProtocol）。
    // ------------------------------------------------------------------

    /** 774設定フェーズの Registry Data（dimension_type＋biome、エントリ配列形式）。 */
    private static List<byte[]> registryData774Packets() {
        Map<String, Tag.CompoundTag> registries = new java.util.LinkedHashMap<>();
        registries.put("minecraft:dimension_type", registryCodec(
                "minecraft:dimension_type", List.of("minecraft:overworld")));
        registries.put("minecraft:worldgen/biome", registryCodec(
                "minecraft:worldgen/biome", List.of("minecraft:plains")));
        return PlayTranslator.buildRegistryData766(registries);
    }

    private static Tag.CompoundTag registryCodec(String registryId, List<String> names) {
        List<Tag> entries = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            entries.add(new Tag.CompoundTag(Map.of(
                    "name", new Tag.StringTag(names.get(i)),
                    "id", new Tag.IntTag(i),
                    "element", new Tag.CompoundTag(Map.of()))));
        }
        return new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag(registryId),
                "value", new Tag.ListTag(Nbt.COMPOUND, entries)));
    }

    /** 759の Login Start：名前＋署名データ無し。 */
    private static byte[] loginStart759(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
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

    /** 763の Chat Message（完全な形式、署名なし）。 */
    private static byte[] chatMessage763(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeBoolean(false); // 署名なし
        writer.writeVarInt(0); // offset
        writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
        return writer.bytes();
    }

    /** 指定クライアントが内部774サーバーに参加し、設定フェーズを終えたセッションを返す。 */
    private static ProxySession joinedAgainst774(int clientProtocol, byte[] loginStart) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), 774);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        assertEquals(clientProtocol, session.clientProtocol());
        assertEquals(774, session.serverProtocol());
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart));
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        for (byte[] registry : registryData774Packets()) {
            Outcome outcome = session.handleServerPacket(new MinecraftPacket(0x07, registry));
            assertTrue(outcome.toClient().isEmpty());
        }
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        return session;
    }

    /** 774の Chat Message（サーバー行き 0x08）のペイロードを読む。 */
    private static void assertChatMessage774(byte[] payload, String message) {
        Reader reader = new Reader(payload);
        assertEquals(message, reader.readString());
        assertTrue(reader.readLong() > 0L); // timestamp
        assertEquals(0L, reader.readLong()); // salt
        assertFalse(reader.readBoolean()); // 署名なし
        assertEquals(0, reader.readVarInt()); // offset
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3)); // acknowledged
        assertEquals(0, reader.readByte()); // checksum（770以降で末尾に付く）
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat758To774ServerBecomesChatMessage774() {
        ProxySession session = joinedAgainst774(758, loginStart758("Steve"));
        Writer writer = new Writer();
        writer.writeString("hello from 1.18.2 to 1.21.11");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x08, out.toServer().get(0).id()); // Chat Message（774）
        assertChatMessage774(out.toServer().get(0).payload(),
                "hello from 1.18.2 to 1.21.11");
    }

    @Test
    void chat758SlashTo774ServerBecomesChatCommandSigned774() {
        ProxySession session = joinedAgainst774(758, loginStart758("Steve"));
        Writer writer = new Writer();
        writer.writeString("/say hi");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x07, out.toServer().get(0).id()); // Chat Command Signed（774）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("say hi", reader.readString());
        assertTrue(reader.readLong() > 0L); // timestamp
        assertEquals(0L, reader.readLong()); // salt
        assertEquals(0, reader.readVarInt()); // argumentSignatures（空）
        assertEquals(0, reader.readVarInt()); // messageCount
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3)); // acknowledged
        assertEquals(0, reader.readByte()); // checksum（770以降で末尾に付く）
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat759To774ServerBecomesChatMessage774() {
        ProxySession session = joinedAgainst774(759, loginStart759("Steve"));
        Writer writer = new Writer();
        writer.writeString("hello from 1.19 to 1.21.11");
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeByteArray(new byte[0]); // 署名は空
        writer.writeBoolean(false); // signedPreview
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x04, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x08, out.toServer().get(0).id()); // Chat Message（774）
        assertChatMessage774(out.toServer().get(0).payload(), "hello from 1.19 to 1.21.11");
    }

    @Test
    void chat763To774ServerBecomesChatMessage774() {
        ProxySession session = joinedAgainst774(763, loginStart763("Steve"));
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x05,
                chatMessage763("hello from 1.20.1 to 1.21.11")));
        assertEquals(1, out.toServer().size());
        assertEquals(0x08, out.toServer().get(0).id()); // Chat Message（774）
        assertChatMessage774(out.toServer().get(0).payload(),
                "hello from 1.20.1 to 1.21.11");
    }

    @Test
    void move758To774ServerBecomesSetPlayerPosition774() {
        ProxySession session = joinedAgainst774(758, loginStart758("Steve"));
        // 758の Set Player Position（0x11：x・y・z・onGround）。
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeBoolean(true);
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x11, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x1d, out.toServer().get(0).id()); // Set Player Position（774）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(0x01, reader.readByte()); // flags（onGround=true）
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // 内部758サーバーへの参加（Join Game が届かない不具合の再現）
    // ------------------------------------------------------------------

    /** 指定クライアントが内部758サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joinedAgainst758(int clientProtocol, byte[] loginStart) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), 758);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        assertEquals(clientProtocol, session.clientProtocol());
        assertEquals(758, session.serverProtocol());
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart));
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess758Payload()));
        assertEquals(1, success.toClient().size());
        // 758には設定フェーズが無い。Login Success の後、両方ともプレイ状態になること。
        // Login Acknowledged や Client Information をサーバーへ送ってはいけない。
        assertTrue(success.toServer().isEmpty(),
                "内部758へ Login Acknowledged／Client Information を送ってはいけない");
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        byte[] join758 = PlayTranslator.translateJoinGame759To758(joinGame759Payload());
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x26, join758));
        assertEquals(1, join.toClient().size());
        int expected = clientProtocol == 758 ? 0x26 : clientProtocol == 759 ? 0x23 : 0x28;
        assertEquals(expected, join.toClient().get(0).id());
        return session;
    }

    @Test
    void join758Against758ReachesJoinGame() {
        joinedAgainst758(758, loginStart758("Steve"));
    }

    @Test
    void join759Against758ReachesJoinGame() {
        joinedAgainst758(759, loginStart759("Steve"));
    }

    @Test
    void join763Against758ReachesJoinGame() {
        joinedAgainst758(763, loginStart763("Steve"));
    }

    // ------------------------------------------------------------------
    // 内部758サーバーへのクライアント→サーバー方向
    // 2026-11-15の不具合：内部758サーバーへの経路が ProxySession に
    // 繋がっていなかった（コンストラクタの検証・handleServerLogin・
    // handleServerPlayRouted・handleClientPlay に758分岐が無い）。
    // 修正：内部758分岐を追加し、クライアント→サーバーは
    // 763→762→761→760→759→758 へ段階変換して送る。
    // ------------------------------------------------------------------

    /** ハンドシェイク後、Login Start を送った結果（サーバーへ転送される分）を返す。 */
    private static Outcome loginStartOutcome(
            int clientProtocol, int serverProtocol, byte[] loginStart) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        return session.handleClientPacket(new MinecraftPacket(0x00, loginStart));
    }

    /** 内部758サーバーへ転送される Login Start は名前だけであること。 */
    private static void assertLoginStartTo758IsNameOnly(Outcome out) {
        assertEquals(1, out.toServer().size());
        assertEquals(0x00, out.toServer().get(0).id()); // Login Start（758）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart758ToInternal758IsNameOnly() {
        assertLoginStartTo758IsNameOnly(
                loginStartOutcome(758, 758, loginStart758("Steve")));
    }

    @Test
    void loginStart759ToInternal758IsNameOnly() {
        assertLoginStartTo758IsNameOnly(
                loginStartOutcome(759, 758, loginStart759("Steve")));
    }

    @Test
    void loginStart763ToInternal758IsNameOnly() {
        assertLoginStartTo758IsNameOnly(
                loginStartOutcome(763, 758, loginStart763("Steve")));
    }

    @Test
    void chat758ToInternal758IsOldChat() {
        ProxySession session = joinedAgainst758(758, loginStart758("Steve"));
        Writer writer = new Writer();
        writer.writeString("hello to 1.18.2");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x03, out.toServer().get(0).id()); // chat（758）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("hello to 1.18.2", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat763ToInternal758IsOldChat() {
        ProxySession session = joinedAgainst758(763, loginStart763("Steve"));
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x05,
                chatMessage763("hello from 1.20.1 to 1.18.2")));
        assertEquals(1, out.toServer().size());
        assertEquals(0x03, out.toServer().get(0).id()); // chat（758）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("hello from 1.20.1 to 1.18.2", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockDig763ToInternal758StripsSequenceAndAckReturns() {
        ProxySession session = joinedAgainst758(763, loginStart763("Steve"));
        int dig763 = PacketIds.id(PacketIds.PacketName.PLAY_BLOCK_DIG,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND, 763).orElseThrow();
        int dig758 = PacketIds.id(PacketIds.PacketName.PLAY_BLOCK_DIG,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND, 758).orElseThrow();
        Writer dig = new Writer();
        dig.writeVarInt(2); // status: finished digging
        dig.writePositionRaw(0x123456789L);
        dig.writeByte(1); // face: top
        dig.writeVarInt(7); // sequence
        Outcome out = session.handleClientPacket(new MinecraftPacket(dig763, dig.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(dig758, out.toServer().get(0).id()); // Block Dig（758）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(2, reader.readVarInt()); // status
        assertEquals(0x123456789L, reader.readPositionRaw());
        assertEquals(1, reader.readByte()); // face
        assertEquals(0, reader.remaining()); // sequence は外れていること
        // サーバーからの旧式 ack（position＋block＋status＋successful）
        // →763クライアントには sequence だけの ack が返る。
        int ack758 = PacketIds.id(PacketIds.PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND, 758).orElseThrow();
        int ack763 = PacketIds.id(PacketIds.PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND, 763).orElseThrow();
        Writer ack = new Writer();
        ack.writePositionRaw(0x123456789L);
        ack.writeVarInt(0); // block（空気）
        ack.writeVarInt(2); // status
        ack.writeBoolean(true); // successful
        Outcome ackOut = session.handleServerPacket(new MinecraftPacket(ack758, ack.bytes()));
        assertEquals(1, ackOut.toClient().size());
        assertEquals(ack763, ackOut.toClient().get(0).id());
        Reader ackReader = new Reader(ackOut.toClient().get(0).payload());
        assertEquals(7, ackReader.readVarInt()); // sequence
        assertEquals(0, ackReader.remaining());
    }

    @Test
    void blockDig758ToInternal758AckRoundTrip() {
        ProxySession session = joinedAgainst758(758, loginStart758("Steve"));
        Writer dig = new Writer();
        dig.writeVarInt(2); // status: finished digging
        dig.writePositionRaw(0x123456789L);
        dig.writeByte(1); // face: top
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x1a, dig.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x1a, out.toServer().get(0).id()); // Block Dig（758）
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(2, reader.readVarInt()); // status
        assertEquals(0x123456789L, reader.readPositionRaw());
        assertEquals(1, reader.readByte()); // face
        assertEquals(0, reader.remaining()); // sequence は無いこと
        // サーバーからの旧式 ack →758クライアントには旧式 ack が合成されて返る。
        // 記録は 759→758合成が使うため、758→759変換では取り除かない。
        Writer ack = new Writer();
        ack.writePositionRaw(0x123456789L);
        ack.writeVarInt(0); // block（空気）
        ack.writeVarInt(2); // status
        ack.writeBoolean(true); // successful
        Outcome ackOut = session.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(1, ackOut.toClient().size());
        assertEquals(0x08, ackOut.toClient().get(0).id()); // Acknowledge Player Digging（758）
        Reader ackReader = new Reader(ackOut.toClient().get(0).payload());
        assertEquals(0x123456789L, ackReader.readPositionRaw());
        assertEquals(0, ackReader.readVarInt()); // block（空気で補完）
        assertEquals(2, ackReader.readVarInt()); // status
        assertTrue(ackReader.readBoolean()); // successful（完了した掘削だけ true）
        assertEquals(0, ackReader.remaining());
    }
}
