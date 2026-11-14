package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * 759（1.19）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession760Test} と同じ段取りで、759の番号・形式で確かめる。
 * 759と760のパケットIDは131件変更（Join Game 0x25→0x23、Chunk Data 0x21→0x1f、
 * Keep Alive 0x20→0x1e、Resource Pack 0x3d→0x3a、Window Click 0x0b→0x0a、
 * Set Creative Slot 0x2b→0x2a、Update Sign 0x2e→0x2d、Chat Message 0x05→0x04、
 * Chat Command 0x04→0x03、Player Chat 0x33→0x30、System Chat 0x62→0x5f、
 * Server Data 0x42→0x3f、Trade List 0x27→0x25）。
 * 759には設定フェーズが無いため、759クライアントは760クライアントと同じく
 * ログイン成功のあとすぐプレイ状態へ進む。内部763サーバーとの組み合わせでは
 * 両方とも設定フェーズが無いため、Login Acknowledged を送らない。
 * ペイロードでは Join Game（portalCooldown 無し）・チャンクの trustEdges・
 * Server Data の previewsChat・Player Chat の初代インライン署名形式と、
 * 759↔760のレジストリ同一（番号はそのまま）を確かめる。763にだけある
 * Feature Flags は759クライアントに落とす。Spawn Entity（プレイヤー種別）は
 * Named Entity Spawn（0x02）に変換する。760にだけある Message Header・
 * Chat Suggestions・Hide Message・Message Acknowledgement は759へは出さない。
 * 署名付きチャットは759（1.19）で導入された初代の形式で、760は連鎖ハッシュ＋
 * Message Header 付きの別形式。
 */
class ProxySession759Test {

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

    /** 759の Login Start：名前＋署名データ option のみ（UUID は無い）。 */
    private static byte[] loginStart759(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
        return writer.bytes();
    }

    /** 760の Login Start：名前＋署名データ option＋任意UUID。 */
    private static byte[] loginStart760(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
        writer.writeBoolean(true); // UUID あり
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 761／762／763の Login Start：名前＋bool＋UUID。 */
    private static byte[] loginStart761(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 762／763／765の Login Success（strict bool なし）。 */
    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
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

    /** 759／760の Join Game（portalCooldown 無し。759と760は同形式）。 */
    private static byte[] joinGame760Payload() {
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

    /** 763の Chunk Data（trustEdges 無し）。セクションは1個、ブロックエンティティは0件。 */
    private static byte[] chunkData763Payload() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeNamedNbt("", new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37]))));
        Writer section = new Writer();
        section.writeShort(1);
        section.writeUnsignedByte(4); // BPE（間接）
        section.writeVarInt(2);
        section.writeVarInt(0);
        section.writeVarInt(24128); // decorated_pot（763）
        section.writeVarInt(256);
        for (int i = 0; i < 256; i++) {
            section.writeLong(0L);
        }
        section.writeUnsignedByte(2); // バイオーム BPE（間接）
        section.writeVarInt(1);
        section.writeVarInt(39); // plains（763）
        section.writeVarInt(2);
        for (int i = 0; i < 2; i++) {
            section.writeLong(0L);
        }
        payload.writeByteArray(section.bytes());
        payload.writeVarInt(0); // ブロックエンティティは0件
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ
        return payload.bytes();
    }

    /** スロット1個（759／760／761／762／763形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        if (clientProtocol == 759) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart759("Steve")));
        } else if (clientProtocol == 760) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart760("Steve")));
        } else {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart761("Steve")));
        }
        return session;
    }

    /** 759クライアントが内部763サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined759Against763() {
        ProxySession session = loginTo(759, 763);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id());
        return session;
    }

    /** 763クライアントが内部759サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined763Against759() {
        ProxySession session = loginTo(763, 759);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame760Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        return session;
    }

    /** 759クライアントが内部760サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined759Against760() {
        ProxySession session = loginTo(759, 760);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame760Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id());
        return session;
    }

    /** 760クライアントが内部759サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined760Against759() {
        ProxySession session = loginTo(760, 759);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame760Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        return session;
    }

    /** 759クライアントが内部759サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined759Against759() {
        ProxySession session = loginTo(759, 759);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame760Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id());
        return session;
    }

    // ------------------------------------------------------------------
    // ハンドシェイク
    // ------------------------------------------------------------------

    @Test
    void handshake759IsAccepted() {
        ProxySession.HandshakeResult result = handshake(
                759, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(759, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    // ------------------------------------------------------------------
    // 759クライアント → 内部759（素通し）
    // ------------------------------------------------------------------

    @Test
    void join759Against759IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                759, 2, false, ProxyAuthConfig.defaults(), 759);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(759, session.clientProtocol());
        assertEquals(759, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart759("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart759("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccessPayload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // プレイ状態は素通し（Join Game は 0x23、portalCooldown 無しのまま）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame760Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame762(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
    }

    // ------------------------------------------------------------------
    // 759クライアント → 内部763（両方とも設定フェーズなし）
    // ------------------------------------------------------------------

    @Test
    void join759Against763GoesStraightToPlay() {
        ProxySession.HandshakeResult result = handshake(
                759, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(759, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は759形式から763形式（名前＋bool＋UUID）へ変換してバニラへ送る。
        // 759にはUUIDが無いため、名前から合成したオフラインUUIDになる。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart759("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PlayTranslator.offlineUuid("Steve"), loginReader.readUuid());
        assertEquals(0, loginReader.remaining());

        // Login Success（763形式）→ 両方ともすぐプレイ状態へ。
        // Login Acknowledged は送らない（内部763には設定フェーズが無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccessPayload(), success.toClient().get(0).payload());
        assertTrue(success.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（763：0x28）→ 759（0x23）。portalCooldown を落とす。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame762(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    @Test
    void playResourcePackOfferUsesPack759For759() {
        ProxySession session = joined759Against763();
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
        // 759クライアントには 0x3a、759用パックの URL／SHA-1 で送る。
        assertEquals(1, offer.toClient().size());
        assertEquals(0x3a, offer.toClient().get(0).id());
        Reader packReader = new Reader(offer.toClient().get(0).payload());
        assertEquals("https://127.0.0.1:25567/detergentmc-pack-1.19.zip",
                packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertEquals(false, packReader.readBoolean()); // required
        assertEquals(false, packReader.readBoolean()); // prompt 無し
        assertEquals(0, packReader.remaining());
    }

    @Test
    void playResourcePackOfferFallsBackToPack760For759() {
        ProxySession session = joined759Against763();
        session.setPack760(new ResourcePack(
                "https://127.0.0.1:25567/detergentmc-pack-1.19.1.zip",
                "0123456789abcdef0123456789abcdef01234567", false, null));
        Writer pack = new Writer();
        pack.writeString("https://example.invalid/vanilla.zip");
        pack.writeString("ffffffffffffffffffffffffffffffffffffffff");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome offer = session.handleServerPacket(new MinecraftPacket(0x40, pack.bytes()));
        // pack759 が無ければ pack760 を 0x3a で送る。
        assertEquals(1, offer.toClient().size());
        assertEquals(0x3a, offer.toClient().get(0).id());
        Reader packReader = new Reader(offer.toClient().get(0).payload());
        assertEquals("https://127.0.0.1:25567/detergentmc-pack-1.19.1.zip",
                packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertEquals(false, packReader.readBoolean()); // required
        assertEquals(false, packReader.readBoolean()); // prompt 無し
        assertEquals(0, packReader.remaining());
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部759（両方とも設定フェーズなし）
    // ------------------------------------------------------------------

    @Test
    void join763Against759GoesStraightToPlay() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 759);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();

        // Login Start は763形式から759形式（名前＋署名データ無し）へ変換してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart761("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertFalse(loginReader.readBoolean()); // 署名データ無し
        assertEquals(0, loginReader.remaining());

        // Login Success → 両方ともすぐプレイ状態へ（Login Acknowledged は送らない）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertTrue(success.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（759：0x23）→ 763（0x28）。portalCooldown=0 を足す。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame760Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
    }

    // ------------------------------------------------------------------
    // プレイ状態の変換（759クライアント → 内部763）
    // ------------------------------------------------------------------

    @Test
    void chunkData763To759RemapsPalette() {
        ProxySession session = joined759Against763();
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x24, chunkData763Payload()));
        assertEquals(1, chunk.toClient().size());
        assertEquals(0x1f, chunk.toClient().get(0).id());
        Reader reader = new Reader(chunk.toClient().get(0).payload());
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        Reader section = new Reader(sections);
        assertEquals(1, section.readShort());
        assertEquals(4, section.readUnsignedByte());
        assertEquals(2, section.readVarInt());
        assertEquals(0, section.readVarInt());
        assertEquals(6897, section.readVarInt()); // flower_pot（759。763=24128→761=8337→760=6897→759=6897）
        // ブロックエンティティは0件、trustEdges（true）が光データの前に挿入される。
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void windowClick759To763RemapsItemAndId() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeUnsignedByte(1); // windowId
        writer.writeVarInt(5); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット数
        writer.writeShort(3);
        writeSlot(writer, 804, 1); // oak_sign（759）
        writeSlot(writer, 1, 2); // carried
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0a, writer.bytes()));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0B, click.toServer().get(0).id());
        Reader reader = new Reader(click.toServer().get(0).payload());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(846, reader.readVarInt()); // oak_sign（763）
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCreativeSlot759To763RemapsItem() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeShort(3); // slot
        writeSlot(writer, 804, 1); // oak_sign（759）
        Outcome slot = session.handleClientPacket(
                new MinecraftPacket(0x2a, writer.bytes()));
        assertEquals(1, slot.toServer().size());
        assertEquals(0x2B, slot.toServer().get(0).id());
        Reader reader = new Reader(slot.toServer().get(0).payload());
        assertEquals(3, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(846, reader.readVarInt()); // oak_sign（763）
        assertEquals(1, reader.readByte()); // 個数
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatMessage759To763KeepsSignature() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeString("hello");
        writer.writeLong(123456789L);
        writer.writeLong(987654321L);
        writer.writeByteArray(SIG_256); // 署名
        writer.writeBoolean(true); // signedPreview（763 には無い）
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x04, writer.bytes()));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x05, chat.toServer().get(0).id());
        Reader reader = new Reader(chat.toServer().get(0).payload());
        assertEquals("hello", reader.readString());
        assertEquals(123456789L, reader.readLong());
        assertEquals(987654321L, reader.readLong());
        assertTrue(reader.readBoolean()); // 署名あり
        assertArrayEquals(SIG_256, reader.readBytes(256));
        assertEquals(0, reader.readVarInt()); // offset = previousMessages の件数（759 には無いため 0）
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3)); // acknowledged（合成）
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatCommand759To763KeepsArgSignature() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeString("/say hi");
        writer.writeLong(10L);
        writer.writeLong(20L);
        writer.writeVarInt(1); // 引数署名 1件
        writer.writeString("msg");
        writer.writeByteArray(SIG_256);
        writer.writeBoolean(true); // signedPreview（763 には無い）
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x04, chat.toServer().get(0).id());
        Reader reader = new Reader(chat.toServer().get(0).payload());
        assertEquals("/say hi", reader.readString());
        assertEquals(10L, reader.readLong());
        assertEquals(20L, reader.readLong());
        assertEquals(1, reader.readVarInt());
        assertEquals("msg", reader.readString());
        assertArrayEquals(SIG_256, reader.readBytes(256)); // 256バイト署名は残る
        assertEquals(0, reader.readVarInt()); // messageCount（759 には無いため 0）
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3)); // acknowledged（合成）
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatPreview759IsDroppedFor763() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeInt(7); // query
        writer.writeString("hello");
        Outcome preview = session.handleClientPacket(
                new MinecraftPacket(0x05, writer.bytes()));
        assertTrue(preview.toServer().isEmpty());
    }

    @Test
    void keepAlive763To759RemapsId() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeLong(123456789L);
        Outcome keepAlive = session.handleServerPacket(
                new MinecraftPacket(0x23, writer.bytes()));
        assertEquals(1, keepAlive.toClient().size());
        assertEquals(0x1e, keepAlive.toClient().get(0).id());
        Reader reader = new Reader(keepAlive.toClient().get(0).payload());
        assertEquals(123456789L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void featureFlags763IsDroppedFor759() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeVarInt(1); // feature flags 1件
        writer.writeString("minecraft:vanilla");
        Outcome flags = session.handleServerPacket(
                new MinecraftPacket(0x6b, writer.bytes()));
        assertTrue(flags.toClient().isEmpty());
    }

    @Test
    void spawnEntityPlayer763ToNamedEntitySpawn759() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeVarInt(42); // entityId
        writer.writeUuid(PLAYER_UUID);
        writer.writeVarInt(122); // player（763）
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeByte(64); // pitch
        writer.writeByte(32); // yaw
        writer.writeByte(64); // headPitch
        writer.writeVarInt(0); // objectData
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x01, writer.bytes()));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x02, spawn.toClient().get(0).id());
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
    void playerChat763To759UsesInlineSignatureFormat() {
        ProxySession session = joined759Against763();
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
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x35, writer.bytes()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x30, chat.toClient().get(0).id());
        Reader reader = new Reader(chat.toClient().get(0).payload());
        assertEquals("hello", reader.readString()); // signedChatContent
        assertFalse(reader.readBoolean()); // unsigned 無し
        assertEquals(0, reader.readVarInt()); // type
        assertEquals(PLAYER_UUID, reader.readUuid()); // sender
        assertEquals("Steve", reader.readString()); // senderName
        assertFalse(reader.readBoolean()); // senderTeam 無し
        assertEquals(100L, reader.readLong());
        assertEquals(200L, reader.readLong());
        assertArrayEquals(SIG_256, reader.readByteArray()); // 署名
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerInfo763To759SplitsAddPlayer() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeUnsignedByte(0x01); // add_player
        writer.writeVarInt(1);
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0); // プロパティ無し
        Outcome info = session.handleServerPacket(
                new MinecraftPacket(0x3a, writer.bytes()));
        // 761 の add_player だけの更新は 759 の add_player 1パケットになる。
        assertEquals(1, info.toClient().size());
        assertEquals(0x34, info.toClient().get(0).id());
        Reader reader = new Reader(info.toClient().get(0).payload());
        assertEquals(0, reader.readVarInt()); // action 0 add_player
        assertEquals(1, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.readVarInt()); // プロパティ無し
        assertEquals(0, reader.readVarInt()); // gamemode（無いため 0）
        assertEquals(0, reader.readVarInt()); // latency（無いため 0）
        assertFalse(reader.readBoolean()); // displayName 無し
        assertFalse(reader.readBoolean()); // crypto 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void systemChat763To759MapsActionBarToType2() {
        ProxySession session = joined759Against763();
        // isActionBar=true → type 2（game_info）。
        Writer actionBar = new Writer();
        actionBar.writeString("{\"text\":\"hi\"}");
        actionBar.writeBoolean(true);
        Outcome actionBarOut = session.handleServerPacket(
                new MinecraftPacket(0x64, actionBar.bytes()));
        assertEquals(1, actionBarOut.toClient().size());
        assertEquals(0x5f, actionBarOut.toClient().get(0).id());
        Reader actionBarReader = new Reader(actionBarOut.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", actionBarReader.readString());
        assertEquals(2, actionBarReader.readVarInt());
        assertEquals(0, actionBarReader.remaining());
        // isActionBar=false → type 1（system）。
        Writer normal = new Writer();
        normal.writeString("{\"text\":\"hi\"}");
        normal.writeBoolean(false);
        Outcome normalOut = session.handleServerPacket(
                new MinecraftPacket(0x64, normal.bytes()));
        assertEquals(1, normalOut.toClient().size());
        assertEquals(0x5f, normalOut.toClient().get(0).id());
        Reader normalReader = new Reader(normalOut.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", normalReader.readString());
        assertEquals(1, normalReader.readVarInt());
        assertEquals(0, normalReader.remaining());
    }

    @Test
    void serverData763To759DropsEnforcesSecureChat() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeBoolean(true); // motd あり
        writer.writeString("{\"text\":\"A Minecraft Server\"}");
        writer.writeBoolean(false); // icon 無し
        writer.writeBoolean(true); // enforcesSecureChat（759 には無い）
        Outcome data = session.handleServerPacket(
                new MinecraftPacket(0x45, writer.bytes()));
        assertEquals(1, data.toClient().size());
        assertEquals(0x3f, data.toClient().get(0).id());
        Reader reader = new Reader(data.toClient().get(0).payload());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"A Minecraft Server\"}", reader.readString());
        assertFalse(reader.readBoolean()); // icon 無し
        assertFalse(reader.readBoolean()); // previewsChat（合成・false）
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeList763To759ConvertsCountToUnsignedByte() {
        ProxySession session = joined759Against763();
        Writer writer = new Writer();
        writer.writeVarInt(7); // windowId
        writer.writeVarInt(1); // count（varint）
        writeSlot(writer, 846, 1); // oak_sign（763）
        writeSlot(writer, 1, 2); // stone
        writeSlot(writer, 846, 1); // oak_sign（763）
        writer.writeBoolean(false); // tradeDisabled
        writer.writeInt(1); // numToolUses
        writer.writeInt(2); // maxUses
        writer.writeInt(3); // xp
        writer.writeInt(4); // specialPrice
        writer.writeFloat(0.5f); // priceMultiplier
        writer.writeInt(5); // demand
        writer.writeVarInt(0); // villagerLevel
        writer.writeVarInt(0); // experience
        writer.writeBoolean(false); // isRegularVillager
        writer.writeBoolean(true); // canRestock
        Outcome trades = session.handleServerPacket(
                new MinecraftPacket(0x2a, writer.bytes()));
        assertEquals(1, trades.toClient().size());
        assertEquals(0x25, trades.toClient().get(0).id());
        Reader reader = new Reader(trades.toClient().get(0).payload());
        assertEquals(7, reader.readVarInt()); // windowId
        assertEquals(1, reader.readUnsignedByte()); // count（u8 に直る）
        assertTrue(reader.readBoolean());
        assertEquals(804, reader.readVarInt()); // oak_sign（759）
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt()); // stone
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertEquals(804, reader.readVarInt()); // oak_sign（759）
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(false, reader.readBoolean()); // tradeDisabled
        assertEquals(1, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(3, reader.readInt());
        assertEquals(4, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readVarInt()); // villagerLevel
        assertEquals(0, reader.readVarInt()); // experience
        assertEquals(false, reader.readBoolean()); // isRegularVillager
        assertEquals(true, reader.readBoolean()); // canRestock
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // 760サーバー → 759クライアント（760にだけあるパケットは落とす）
    // ------------------------------------------------------------------

    @Test
    void messageHeader760ServerIsDroppedFor759() {
        ProxySession session = joined759Against760();
        Writer writer = new Writer();
        writer.writeBoolean(false); // previousSignature 無し
        writer.writeUuid(PLAYER_UUID);
        writer.writeByteArray(SIG_256); // 署名
        writer.writeByteArray(new byte[32]); // messageHash
        Outcome header = session.handleServerPacket(
                new MinecraftPacket(0x32, writer.bytes()));
        assertTrue(header.toClient().isEmpty());
    }

    @Test
    void chatSuggestions760ServerIsDroppedFor759() {
        ProxySession session = joined759Against760();
        Writer writer = new Writer();
        writer.writeVarInt(7); // queryId
        writer.writeVarInt(1); // suggestions 1件
        writer.writeString("hello");
        Outcome suggestions = session.handleServerPacket(
                new MinecraftPacket(0x15, writer.bytes()));
        assertTrue(suggestions.toClient().isEmpty());
    }

    @Test
    void hideMessage760ServerIsDroppedFor759() {
        ProxySession session = joined759Against760();
        Writer writer = new Writer();
        writer.writeByteArray(SIG_256); // 署名
        Outcome hide = session.handleServerPacket(
                new MinecraftPacket(0x18, writer.bytes()));
        assertTrue(hide.toClient().isEmpty());
    }

    // ------------------------------------------------------------------
    // 760クライアント → 内部759（760にだけあるパケットは落とす）
    // ------------------------------------------------------------------

    @Test
    void messageAcknowledgement760ClientIsDroppedFor759Server() {
        ProxySession session = joined760Against759();
        Writer writer = new Writer();
        writer.writeVarInt(0); // previousMessages 0件
        writer.writeBoolean(false); // lastRejectedMessage 無し
        Outcome ack = session.handleClientPacket(
                new MinecraftPacket(0x03, writer.bytes()));
        assertTrue(ack.toServer().isEmpty());
    }

    // ------------------------------------------------------------------
    // プレイ状態の変換（763クライアント → 内部759）
    // ------------------------------------------------------------------

    @Test
    void windowClick763To759RemapsItemAndId() {
        ProxySession session = joined763Against759();
        Writer writer = new Writer();
        writer.writeUnsignedByte(1); // windowId
        writer.writeVarInt(5); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット数
        writer.writeShort(3);
        writeSlot(writer, 846, 1); // oak_sign（763）
        writeSlot(writer, 1, 2); // carried
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0B, writer.bytes()));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0a, click.toServer().get(0).id());
        Reader reader = new Reader(click.toServer().get(0).payload());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(804, reader.readVarInt()); // oak_sign（759）
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // ネイティブ経路（759＋759）は素通し
    // ------------------------------------------------------------------

    @Test
    void spawnEntityPlayer759ServerTo759Client() {
        // 759＋759のネイティブ経路は素通しのため、Spawn Entity（player）は
        // Named Entity Spawn には変換されず、0x00・type=116のまま届く。
        ProxySession session = joined759Against759();
        Writer writer = new Writer();
        writer.writeVarInt(43); // entityId
        writer.writeUuid(PLAYER_UUID);
        writer.writeVarInt(116); // player（759）
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeByte(64); // pitch
        writer.writeByte(32); // yaw
        writer.writeByte(64); // headPitch
        writer.writeVarInt(0); // objectData
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, writer.bytes()));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x00, spawn.toClient().get(0).id());
        Reader reader = new Reader(spawn.toClient().get(0).payload());
        assertEquals(43, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals(116, reader.readVarInt()); // player（759）
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(64, reader.readByte()); // pitch
        assertEquals(32, reader.readByte()); // yaw
        assertEquals(64, reader.readByte()); // headPitch
        assertEquals(0, reader.readVarInt()); // objectData
        assertEquals(0, reader.readShort()); // velocityX
        assertEquals(0, reader.readShort()); // velocityY
        assertEquals(0, reader.readShort()); // velocityZ
        assertEquals(0, reader.remaining());
    }

    @Test
    void namedEntitySpawn759ServerTo759Client() {
        ProxySession session = joined759Against759();
        Writer writer = new Writer();
        writer.writeVarInt(43); // entityId
        writer.writeUuid(PLAYER_UUID);
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeByte(32); // yaw
        writer.writeByte(64); // pitch
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x02, writer.bytes()));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x02, spawn.toClient().get(0).id());
        Reader reader = new Reader(spawn.toClient().get(0).payload());
        assertEquals(43, reader.readVarInt());
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(32, reader.readByte()); // yaw
        assertEquals(64, reader.readByte()); // pitch
        assertEquals(0, reader.remaining());
    }
}
