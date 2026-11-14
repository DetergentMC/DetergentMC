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
 * 761（1.19.3）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession762Test} と同じ段取りで、761の番号・形式で確かめる。
 * 761と762のパケットIDは132件変更（Join Game 0x28→0x24、Chunk Data 0x24→0x20、
 * Keep Alive 0x23→0x1f、Resource Pack 0x40→0x3c、Window Click 0x0B→0x0a、
 * Set Creative Slot 0x2B→0x2b、Update Sign 0x2E→0x2e）。
 * 761には設定フェーズが無いため、761クライアントは762クライアントと同じく
 * ログイン成功のあとすぐプレイ状態へ進む。内部763サーバーとの組み合わせでは
 * 両方とも設定フェーズが無いため、Login Acknowledged を送らない。
 * ペイロードでは Join Game（portalCooldown 無し）・チャンクの trustEdges・
 * 位置の dismountVehicle・Server Data の motd／icon 形式と、761↔762の
 * レジストリ付け替えを確かめる。762にだけある Damage Event／Bundle Delimiter／
 * Chunk Biomes／Hurt Animation は761クライアントに落とす。
 * Spawn Entity（プレイヤー種別）は Named Entity Spawn（0x02）に変換する。
 */
class ProxySession761Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

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

    /** 761の Join Game（portalCooldown 無し。762と同形式）。 */
    private static byte[] joinGame761Payload() {
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

    /** 761の Chunk Data（trustEdges あり）。セクションは1個、ブロックエンティティは0件。 */
    private static byte[] chunkData761Payload() {
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
        section.writeVarInt(8337); // flower_pot（761）
        section.writeVarInt(256);
        for (int i = 0; i < 256; i++) {
            section.writeLong(0L);
        }
        section.writeUnsignedByte(2); // バイオーム BPE（間接）
        section.writeVarInt(1);
        section.writeVarInt(38); // plains（761）
        section.writeVarInt(2);
        for (int i = 0; i < 2; i++) {
            section.writeLong(0L);
        }
        payload.writeByteArray(section.bytes());
        payload.writeVarInt(0); // ブロックエンティティは0件
        payload.writeBoolean(true); // trustEdges（761／762のみ）
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ
        return payload.bytes();
    }

    /** スロット1個（761／762／763形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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
        session.handleClientPacket(new MinecraftPacket(0x00,
                loginStart761("Steve")));
        return session;
    }

    /** 761クライアントが内部763サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined761Against763() {
        ProxySession session = loginTo(761, 763);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        return session;
    }

    /** 763クライアントが内部761サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined763Against761() {
        ProxySession session = loginTo(763, 761);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinGame761Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        return session;
    }

    /** 761クライアントが内部761サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined761Against761() {
        ProxySession session = loginTo(761, 761);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinGame761Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        return session;
    }

    // ------------------------------------------------------------------
    // ハンドシェイク
    // ------------------------------------------------------------------

    @Test
    void handshake761IsAcceptedAnd759IsAccepted() {
        ProxySession.HandshakeResult result = handshake(
                761, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(761, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());

        ProxySession.HandshakeResult old = handshake(
                759, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(old.disconnect());
        assertNotNull(old.session());
        assertEquals(759, old.session().clientProtocol());
        assertEquals(763, old.session().serverProtocol());
    }

    // ------------------------------------------------------------------
    // 761クライアント → 内部761（素通し）
    // ------------------------------------------------------------------

    @Test
    void join761Against761IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                761, 2, false, ProxyAuthConfig.defaults(), 761);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(761, session.clientProtocol());
        assertEquals(761, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart761("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart761("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccessPayload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // プレイ状態は素通し（Join Game は 0x24、portalCooldown 無しのまま）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinGame761Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame762(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
    }

    // ------------------------------------------------------------------
    // 761クライアント → 内部763（両方とも設定フェーズなし）
    // ------------------------------------------------------------------

    @Test
    void join761Against763GoesStraightToPlay() {
        ProxySession.HandshakeResult result = handshake(
                761, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(761, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は761形式のままバニラへ送る（763と同じ形）。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart761("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart761("Steve"), login.toServer().get(0).payload());

        // Login Success（763形式）→ 両方ともすぐプレイ状態へ。
        // Login Acknowledged は送らない（内部763には設定フェーズが無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccessPayload(), success.toClient().get(0).payload());
        assertTrue(success.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（763：0x28）→ 761（0x24）。portalCooldown を落とす。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame762(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    @Test
    void playResourcePackOfferUsesPack761For761() {
        ProxySession session = joined761Against763();
        session.setPack761(new ResourcePack(
                "https://127.0.0.1:25567/detergentmc-pack-1.19.3.zip",
                "0123456789abcdef0123456789abcdef01234567", false, null));
        // 内部763サーバーが送る旧式 Resource Pack（0x40：URL＋SHA-1＋required＋prompt）。
        Writer pack = new Writer();
        pack.writeString("https://example.invalid/vanilla.zip");
        pack.writeString("ffffffffffffffffffffffffffffffffffffffff");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome offer = session.handleServerPacket(new MinecraftPacket(0x40, pack.bytes()));
        // 761クライアントには 0x3c、761用パックの URL／SHA-1 で送る。
        assertEquals(1, offer.toClient().size());
        assertEquals(0x3c, offer.toClient().get(0).id());
        Reader packReader = new Reader(offer.toClient().get(0).payload());
        assertEquals("https://127.0.0.1:25567/detergentmc-pack-1.19.3.zip",
                packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertEquals(false, packReader.readBoolean()); // required
        assertEquals(false, packReader.readBoolean()); // prompt 無し
        assertEquals(0, packReader.remaining());
    }

    @Test
    void playResourcePackOfferFallsBackToPack762For761() {
        ProxySession session = joined761Against763();
        session.setPack762(new ResourcePack(
                "https://127.0.0.1:25567/detergentmc-pack-1.19.4.zip",
                "0123456789abcdef0123456789abcdef01234567", false, null));
        Writer pack = new Writer();
        pack.writeString("https://example.invalid/vanilla.zip");
        pack.writeString("ffffffffffffffffffffffffffffffffffffffff");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome offer = session.handleServerPacket(new MinecraftPacket(0x40, pack.bytes()));
        // pack761 が無ければ pack762 を 0x3c で送る。
        assertEquals(1, offer.toClient().size());
        assertEquals(0x3c, offer.toClient().get(0).id());
        Reader packReader = new Reader(offer.toClient().get(0).payload());
        assertEquals("https://127.0.0.1:25567/detergentmc-pack-1.19.4.zip",
                packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertEquals(false, packReader.readBoolean()); // required
        assertEquals(false, packReader.readBoolean()); // prompt 無し
        assertEquals(0, packReader.remaining());
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部761（両方とも設定フェーズなし）
    // ------------------------------------------------------------------

    @Test
    void join763Against761GoesStraightToPlay() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 761);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart761("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart761("Steve"), login.toServer().get(0).payload());

        // Login Success → 両方ともすぐプレイ状態へ（Login Acknowledged は送らない）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertTrue(success.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（761：0x24）→ 763（0x28）。portalCooldown=0 を足す。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinGame761Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
    }

    // ------------------------------------------------------------------
    // プレイ状態の変換（761クライアント → 内部763）
    // ------------------------------------------------------------------

    @Test
    void chunkData763To761AddsTrustEdgesAndRemapsPalette() {
        ProxySession session = joined761Against763();
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x24, chunkData763Payload()));
        assertEquals(1, chunk.toClient().size());
        assertEquals(0x20, chunk.toClient().get(0).id());
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
        assertEquals(8337, section.readVarInt()); // flower_pot（761）
        // ブロックエンティティは0件、trustEdges（true）が光データの前に挿入される。
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void windowClick761To763RemapsItemAndId() {
        ProxySession session = joined761Against763();
        Writer writer = new Writer();
        writer.writeUnsignedByte(1); // windowId
        writer.writeVarInt(5); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット数
        writer.writeShort(3);
        writeSlot(writer, 821, 1); // oak_sign（761）
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
    void setCreativeSlot761To763RemapsItem() {
        ProxySession session = joined761Against763();
        Writer writer = new Writer();
        writer.writeShort(3); // slot
        writeSlot(writer, 821, 1); // oak_sign（761）
        Outcome slot = session.handleClientPacket(
                new MinecraftPacket(0x2b, writer.bytes()));
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
    void position763To761AppendsDismountVehicle() {
        ProxySession session = joined761Against763();
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7);
        Outcome position = session.handleServerPacket(
                new MinecraftPacket(0x3c, writer.bytes()));
        assertEquals(1, position.toClient().size());
        assertEquals(0x38, position.toClient().get(0).id());
        Reader reader = new Reader(position.toClient().get(0).payload());
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(90.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0, reader.readByte());
        assertEquals(7, reader.readVarInt());
        assertEquals(false, reader.readBoolean()); // dismountVehicle=false を足す
        assertEquals(0, reader.remaining());
    }

    @Test
    void serverData763To761RewritesMotdAndIcon() {
        ProxySession session = joined761Against763();
        Writer writer = new Writer();
        writer.writeString("hello");
        writer.writeBoolean(false); // icon 無し
        writer.writeBoolean(false); // enforcesSecureChat
        Outcome serverData = session.handleServerPacket(
                new MinecraftPacket(0x45, writer.bytes()));
        assertEquals(1, serverData.toClient().size());
        assertEquals(0x41, serverData.toClient().get(0).id());
        Reader reader = new Reader(serverData.toClient().get(0).payload());
        assertEquals(true, reader.readBoolean()); // motd あり
        assertEquals("hello", reader.readString());
        assertEquals(false, reader.readBoolean()); // icon 無し
        assertEquals(false, reader.readBoolean()); // enforcesSecureChat
        assertEquals(0, reader.remaining());
    }

    @Test
    void respawn763To761DropsPortalCooldown() {
        ProxySession session = joined761Against763();
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeVarInt(0); // portalCooldown
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x41, writer.bytes()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        Reader reader = new Reader(respawn.toClient().get(0).payload());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(99L, reader.readLong());
        assertEquals(0, reader.readByte());
        assertEquals(255, reader.readUnsignedByte());
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining()); // portalCooldown は落とす
    }

    @Test
    void keepAlive763To761RemapsId() {
        ProxySession session = joined761Against763();
        Writer writer = new Writer();
        writer.writeLong(123456789L);
        Outcome keepAlive = session.handleServerPacket(
                new MinecraftPacket(0x23, writer.bytes()));
        assertEquals(1, keepAlive.toClient().size());
        assertEquals(0x1F, keepAlive.toClient().get(0).id());
        Reader reader = new Reader(keepAlive.toClient().get(0).payload());
        assertEquals(123456789L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void damageEvent763IsDroppedFor761() {
        ProxySession session = joined761Against763();
        Writer writer = new Writer();
        writer.writeVarInt(42); // entityId
        writer.writeVarInt(5); // damageType
        writer.writeVarInt(0); // sourceEntityId
        writer.writeVarInt(0); // sourceDirectEntityId
        writer.writeDouble(1.0);
        writer.writeDouble(64.0);
        writer.writeDouble(2.0);
        Outcome damage = session.handleServerPacket(
                new MinecraftPacket(0x18, writer.bytes()));
        assertTrue(damage.toClient().isEmpty());
    }

    @Test
    void spawnEntityPlayer763ToNamedEntitySpawn761() {
        ProxySession session = joined761Against763();
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
    void spawnEntityPlayer761ServerTo761Client() {
        // 761＋761のネイティブ経路は素通しのため、Spawn Entity（player）は
        // Named Entity Spawn には変換されず、0x00・type=117のまま届く。
        // Named Entity Spawn への変換は、クライアントが761でサーバーが
        // 762／763のときにだけ行う（spawnEntityPlayer763ToNamedEntitySpawn761）。
        ProxySession session = joined761Against761();
        Writer writer = new Writer();
        writer.writeVarInt(43); // entityId
        writer.writeUuid(PLAYER_UUID);
        writer.writeVarInt(117); // player（761）
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
        assertEquals(117, reader.readVarInt()); // player（761）
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
    void namedEntitySpawn761ServerTo761Client() {
        ProxySession session = joined761Against761();
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

    // ------------------------------------------------------------------
    // プレイ状態の変換（763クライアント → 内部761）
    // ------------------------------------------------------------------

    @Test
    void chunkData761To763RemapsPalette() {
        ProxySession session = joined763Against761();
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x20, chunkData761Payload()));
        assertEquals(1, chunk.toClient().size());
        assertEquals(0x24, chunk.toClient().get(0).id());
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
        assertEquals(8567, section.readVarInt()); // flower_pot（763。761=8337→762=8563→763=8567）
        // trustEdges は761→763で落とされ、光データが直後に来る。
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void windowClick763To761RemapsItemAndId() {
        ProxySession session = joined763Against761();
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
        assertEquals(821, reader.readVarInt()); // oak_sign（761）
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void position761To763StripsDismountVehicle() {
        ProxySession session = joined763Against761();
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7);
        writer.writeBoolean(true); // dismountVehicle（761のみ）
        Outcome position = session.handleServerPacket(
                new MinecraftPacket(0x38, writer.bytes()));
        assertEquals(1, position.toClient().size());
        assertEquals(0x3c, position.toClient().get(0).id());
        Reader reader = new Reader(position.toClient().get(0).payload());
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(90.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0, reader.readByte());
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.remaining()); // dismountVehicle は落とす
    }
}
