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
 * 762（1.19.4）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession773Test} と同じ段取りで、762の番号・形式で確かめる。
 * 762と763のパケットIDは0件変更（Join Game 0x28、Chunk Data 0x24、
 * Keep Alive 0x23、Ping 0x32、Resource Pack 0x40、Window Click 0x0B、
 * Set Creative Slot 0x2B、Update Sign 0x2E）。762には設定フェーズが無い
 * ため、762クライアントは763クライアントと同じくログイン成功のあと
 * すぐプレイ状態へ進む。内部763サーバーとの組み合わせでは両方とも
 * 設定フェーズが無いため、Login Acknowledged を送らない。
 * ペイロードでは Join Game・Respawn の portalCooldown、看板の isFrontText、
 * 戦闘イベントの entityId、チャンクの trustEdges、Advancements の
 * sendsTelemetryData の付け外しと、762↔763のレジストリ付け替えを確かめる。
 */
class ProxySession762Test {

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

    /** 762／763の Login Start：名前＋bool＋UUID。 */
    private static byte[] loginStart762(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 765／766／767の Login Start：名前＋UUID必須。 */
    private static byte[] loginStart766(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
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

    /** dimension_type（overworld=0）＋biome（plains=0）の codec（765／764形式・名前なし NBT）。 */
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

    /** 762の Join Game（portalCooldown 無し）。 */
    private static byte[] joinGame762Payload() {
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

    /** 765の Join Game（dimension は文字列、doLimitedCrafting あり、portalCooldown あり）。 */
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

    /** 765の設定フェーズの Add Resource Pack（UUID＋URL＋SHA-1＋required＋prompt無し）。 */
    private static byte[] configAddPack765Payload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        writer.writeString("https://example.invalid/pack.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 763の Respawn（末尾に portalCooldown）。 */
    private static byte[] respawn763Payload() {
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

    /** 762の Chunk Data（trustEdges あり）。セクションは1個、ブロックエンティティは0件。 */
    private static byte[] chunkData762Payload() {
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
        section.writeVarInt(23718); // decorated_pot（762）
        section.writeVarInt(256);
        for (int i = 0; i < 256; i++) {
            section.writeLong(0L);
        }
        section.writeUnsignedByte(2); // バイオーム BPE（間接）
        section.writeVarInt(1);
        section.writeVarInt(38); // plains（762）
        section.writeVarInt(2);
        for (int i = 0; i < 2; i++) {
            section.writeLong(0L);
        }
        payload.writeByteArray(section.bytes());
        payload.writeVarInt(0); // ブロックエンティティは0件
        payload.writeBoolean(true); // trustEdges（762のみ）
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ
        return payload.bytes();
    }

    /** スロット1個（762／763形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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
                clientProtocol == 762 || clientProtocol == 763
                        ? loginStart762("Steve") : loginStart766("Steve")));
        return session;
    }

    /** 762クライアントが内部763サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined762Against763() {
        ProxySession session = loginTo(762, 763);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        return session;
    }

    // ------------------------------------------------------------------
    // ハンドシェイク
    // ------------------------------------------------------------------

    @Test
    void handshake762IsAcceptedAnd759IsAccepted() {
        ProxySession.HandshakeResult result = handshake(
                762, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(762, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());

        ProxySession.HandshakeResult old = handshake(
                759, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(old.disconnect());
        assertNotNull(old.session());
        assertEquals(759, old.session().clientProtocol());
        assertEquals(763, old.session().serverProtocol());
    }

    // ------------------------------------------------------------------
    // 762クライアント → 内部762（素通し）
    // ------------------------------------------------------------------

    @Test
    void join762Against762IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                762, 2, false, ProxyAuthConfig.defaults(), 762);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(762, session.clientProtocol());
        assertEquals(762, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart762("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart762("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccessPayload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // プレイ状態は素通し（Join Game は portalCooldown 無しのまま）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame762Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame762(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
    }

    // ------------------------------------------------------------------
    // 762クライアント → 内部763（両方とも設定フェーズなし）
    // ------------------------------------------------------------------

    @Test
    void join762Against763GoesStraightToPlay() {
        ProxySession.HandshakeResult result = handshake(
                762, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(762, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は762形式のままバニラへ送る（763と同じ形）。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart762("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart762("Steve"), login.toServer().get(0).payload());

        // Login Success（763形式）→ 両方ともすぐプレイ状態へ。
        // Login Acknowledged は送らない（内部763には設定フェーズが無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccessPayload(), success.toClient().get(0).payload());
        assertTrue(success.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（763：0x28）→ 762（0x28）。portalCooldown を落とす。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame762(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    @Test
    void playResourcePackOfferUsesPack762For762() {
        ProxySession session = joined762Against763();
        session.setPack762(new ResourcePack(
                "https://127.0.0.1:25567/detergentmc-pack-1.19.4.zip",
                "0123456789abcdef0123456789abcdef01234567", false, null));
        // 内部763サーバーが送る旧式 Resource Pack（0x40：URL＋SHA-1＋required＋prompt）。
        Writer pack = new Writer();
        pack.writeString("https://example.invalid/vanilla.zip");
        pack.writeString("ffffffffffffffffffffffffffffffffffffffff");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome offer = session.handleServerPacket(new MinecraftPacket(0x40, pack.bytes()));
        // 762クライアントには 0x40 のまま、762用パックの URL／SHA-1 で送る。
        assertEquals(1, offer.toClient().size());
        assertEquals(0x40, offer.toClient().get(0).id());
        Reader packReader = new Reader(offer.toClient().get(0).payload());
        assertEquals("https://127.0.0.1:25567/detergentmc-pack-1.19.4.zip",
                packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567", packReader.readString());
        assertEquals(false, packReader.readBoolean()); // required
        assertEquals(false, packReader.readBoolean()); // prompt 無し
        assertEquals(0, packReader.remaining());
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部762（両方とも設定フェーズなし）
    // ------------------------------------------------------------------

    @Test
    void join763Against762GoesStraightToPlay() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 762);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart762("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart762("Steve"), login.toServer().get(0).payload());

        // Login Success → 両方ともすぐプレイ状態へ（Login Acknowledged は送らない）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertTrue(success.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（762：0x28）→ 763（0x28）。portalCooldown=0 を足す。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame762Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
    }

    /** 763クライアントが内部762サーバーに参加し、Join Game まで進めたセッションを返す。 */
    private static ProxySession joined763Against762() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 762);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart762("Steve")));
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame762Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        return session;
    }

    @Test
    void chunkData762To763RemapsPalette() {
        ProxySession session = joined763Against762();
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x24, chunkData762Payload()));
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
        assertEquals(24128, section.readVarInt()); // decorated_pot（763）
        // trustEdges は762→763で落とされ、光データが直後に来る。
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void windowClick763To762RemapsItem() {
        ProxySession session = joined763Against762();
        Writer writer = new Writer();
        writer.writeUnsignedByte(1); // windowId
        writer.writeVarInt(5); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット数
        writer.writeShort(3);
        writeSlot(writer, 1236, 1); // archer_pottery_sherd（763）
        writeSlot(writer, 1, 2); // carried
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0B, writer.bytes()));
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
        assertEquals(1224, reader.readVarInt()); // pottery_shard_archer（762）
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateSign763To762DropsIsFrontText() {
        ProxySession session = joined763Against762();
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeBoolean(true); // isFrontText（763のみ）
        writer.writeString("one");
        writer.writeString("two");
        writer.writeString("three");
        writer.writeString("four");
        Outcome sign = session.handleClientPacket(
                new MinecraftPacket(0x2E, writer.bytes()));
        assertEquals(1, sign.toServer().size());
        assertEquals(0x2E, sign.toServer().get(0).id());
        Reader reader = new Reader(sign.toServer().get(0).payload());
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals("one", reader.readString());
        assertEquals("two", reader.readString());
        assertEquals("three", reader.readString());
        assertEquals("four", reader.readString());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // 762クライアント → 内部765（設定フェーズを765クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join762Against765ConsumesConfigAs765Client() {
        ProxySession.HandshakeResult result = handshake(
                762, 2, false, ProxyAuthConfig.defaults(), 765);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(762, session.clientProtocol());
        assertEquals(765, session.serverProtocol());

        // Login Start は762形式から765形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart762("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（765形式）→ 762にはそのまま送る。
        // プロキシは Login Acknowledged（0x03）と Client Information（0x00）を
        // 765サーバーへ送り、設定フェーズに入る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccessPayload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        assertEquals(PlayTranslator.clientInformationPayload().length,
                success.toServer().get(1).payload().length);
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（765：0x05）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Add Resource Pack（765：0x07）は Join Game の後まで保持する。
        session.setPack762(new ResourcePack(
                "https://127.0.0.1:25567/detergentmc-pack-1.19.4.zip",
                "0123456789abcdef0123456789abcdef01234567", false, null));
        Outcome configPack = session.handleServerPacket(
                new MinecraftPacket(0x07, configAddPack765Payload()));
        assertTrue(configPack.toClient().isEmpty());

        // Finish Configuration（765：0x02）→ 応答（0x02）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x02, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（765：0x29）→ 762（0x28）。portalCooldown を落とす。
        // 保持しておいたリソースパックも 0x40（762用）で一緒に出す。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        Reader packReader = new Reader(join.toClient().get(1).payload());
        assertEquals("https://127.0.0.1:25567/detergentmc-pack-1.19.4.zip",
                packReader.readString());
        assertEquals("0123456789abcdef0123456789abcdef01234567",
                packReader.readString());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame762(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.portalCooldown());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    // ------------------------------------------------------------------
    // プレイ状態の変換（762クライアント → 内部763）
    // ------------------------------------------------------------------

    @Test
    void chunkData763To762AddsTrustEdgesAndRemapsPalette() {
        ProxySession session = joined762Against763();
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x24, chunkData763Payload()));
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
        assertEquals(23718, section.readVarInt()); // decorated_pot（762）
        // ブロックエンティティは0件、trustEdges（true）が光データの前に挿入される。
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void respawn763To762DropsPortalCooldown() {
        ProxySession session = joined762Against763();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x41, respawn763Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x41, respawn.toClient().get(0).id());
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
    void windowClick762To763RemapsItem() {
        ProxySession session = joined762Against763();
        Writer writer = new Writer();
        writer.writeUnsignedByte(1); // windowId
        writer.writeVarInt(5); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット数
        writer.writeShort(3);
        writeSlot(writer, 1224, 1); // pottery_shard_archer（762）
        writeSlot(writer, 1, 2); // carried
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0B, writer.bytes()));
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
        assertEquals(1236, reader.readVarInt()); // archer_pottery_sherd（763）
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCreativeSlot762To763RemapsItem() {
        ProxySession session = joined762Against763();
        Writer writer = new Writer();
        writer.writeShort(3); // slot
        writeSlot(writer, 1224, 1);
        Outcome slot = session.handleClientPacket(
                new MinecraftPacket(0x2B, writer.bytes()));
        assertEquals(1, slot.toServer().size());
        assertEquals(0x2B, slot.toServer().get(0).id());
        Reader reader = new Reader(slot.toServer().get(0).payload());
        assertEquals(3, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(1236, reader.readVarInt());
        assertEquals(1, reader.readByte()); // 個数
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateSign762To763AddsIsFrontText() {
        ProxySession session = joined762Against763();
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeString("one");
        writer.writeString("two");
        writer.writeString("three");
        writer.writeString("four");
        Outcome sign = session.handleClientPacket(
                new MinecraftPacket(0x2E, writer.bytes()));
        assertEquals(1, sign.toServer().size());
        assertEquals(0x2E, sign.toServer().get(0).id());
        Reader reader = new Reader(sign.toServer().get(0).payload());
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(true, reader.readBoolean()); // isFrontText=true を挿す
        assertEquals("one", reader.readString());
        assertEquals("two", reader.readString());
        assertEquals("three", reader.readString());
        assertEquals("four", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void advancements763To762DropsSendsTelemetryData() {
        ProxySession session = joined762Against763();
        Writer writer = new Writer();
        writer.writeBoolean(false); // reset
        writer.writeVarInt(1); // mappingCount
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(false); // hasParent
        writer.writeBoolean(false); // hasDisplay
        writer.writeVarInt(0); // criteriaCount
        writer.writeVarInt(0); // requirementCount
        writer.writeBoolean(true); // sendsTelemetryData（763のみ）
        PacketIO.writeStringList(writer, List.of()); // 削除一覧
        writer.writeVarInt(0); // progressCount
        Outcome advancements = session.handleServerPacket(
                new MinecraftPacket(0x69, writer.bytes()));
        assertEquals(1, advancements.toClient().size());
        assertEquals(0x69, advancements.toClient().get(0).id());
        Reader reader = new Reader(advancements.toClient().get(0).payload());
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        // sendsTelemetryData は落とされ、次は削除一覧。
        assertEquals(List.of(), PacketIO.readStringList(reader));
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }
}
