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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 767クライアント／767サーバーの参加フロー（ハンドシェイク→ログイン→
 * 設定フェーズ→プレイ）を、内部763／764／765／766／767のそれぞれで確認する。
 * 767は766とプレイID・スロット形式が同じため、スロットを含むパケットは
 * 内部766／767向けは変換し、内部763／764／765向けは落とす。
 */
class ProxySession767Test {

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

    /** 766／767の Login Start：名前＋UUID必須。 */
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

    /** 766／767の Login Success（末尾に strictErrorHandling=false）。 */
    private static byte[] loginSuccess766Payload() {
        return PlayTranslator.translateLoginSuccess765To766(loginSuccess765Payload());
    }

    /** 767の Client Information（設定フェーズ。766と同じ）。 */
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

    /** dimension_type（overworld=0）＋biome（plains=0）の codec（765／764形式）。 */
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

    /** 766／767の Registry Data 1件（dimension_type、エントリ配列形式）。 */
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

    /** 766／767の Join Game。 */
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

    /** 764／765の Respawn（dimension は文字列、copyMetadata は末尾）。 */
    private static byte[] respawn765Payload() {
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
        return writer.bytes();
    }

    /** 766／767の Respawn（dimension は番号、copyMetadata は末尾のバイト）。 */
    private static byte[] respawn766Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // dimension（overworld）
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeByte(1); // copyMetadata
        return writer.bytes();
    }

    /** 766／767の Add Resource Pack（UUID付き、プロンプトなし）。 */
    private static byte[] addResourcePackPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        writer.writeString("https://127.0.0.1:25567/detergentmc-pack-1.21.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    // ---- エンチャントの名称表（並べ替えの検証用） ----

    private static final List<String> ENCHANTMENT_765 = List.of(
            "protection", "fire_protection", "feather_falling", "blast_protection",
            "projectile_protection", "respiration", "aqua_affinity", "thorns",
            "depth_strider", "frost_walker", "binding_curse", "soul_speed", "swift_sneak",
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping", "efficiency", "silk_touch", "unbreaking", "fortune",
            "power", "punch", "flame", "infinity", "luck_of_the_sea", "lure", "loyalty",
            "impaling", "riptide", "channeling", "multishot", "quick_charge", "piercing",
            "mending", "vanishing_curse");

    private static final List<String> ENCHANTMENT_766 = List.of(
            "protection", "fire_protection", "feather_falling", "blast_protection",
            "projectile_protection", "respiration", "aqua_affinity", "thorns",
            "depth_strider", "frost_walker", "binding_curse", "soul_speed", "swift_sneak",
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping_edge", "efficiency", "silk_touch", "unbreaking",
            "fortune", "power", "punch", "flame", "infinity", "luck_of_the_sea", "lure",
            "loyalty", "impaling", "riptide", "channeling", "multishot", "quick_charge",
            "piercing", "density", "breach", "wind_burst", "mending", "vanishing_curse");

    private static final List<String> ENCHANTMENT_767 = List.of(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
            "breach", "channeling", "density", "depth_strider", "efficiency",
            "feather_falling", "fire_aspect", "fire_protection", "flame", "fortune",
            "frost_walker", "impaling", "infinity", "knockback", "looting", "loyalty",
            "luck_of_the_sea", "lure", "mending", "multishot", "piercing", "power",
            "projectile_protection", "protection", "punch", "quick_charge", "respiration",
            "riptide", "sharpness", "silk_touch", "smite", "soul_speed", "sweeping_edge",
            "swift_sneak", "thorns", "unbreaking", "vanishing_curse", "wind_burst");

    /** レジストリ1個分の codec（エントリは名前順のリスト、element は空）。 */
    private static Tag.CompoundTag registryCodec(String registryId, List<String> names) {
        List<Tag> entries = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            entries.add(new Tag.CompoundTag(Map.of(
                    "name", new Tag.StringTag("minecraft:" + names.get(i)),
                    "id", new Tag.IntTag(i),
                    "element", new Tag.CompoundTag(Map.of()))));
        }
        return new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag(registryId),
                "value", new Tag.ListTag(Nbt.COMPOUND, entries)));
    }

    /** 766／767形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767形式の Registry Data パケットを読み、名前列を返す。 */
    private static List<String> readRegistryPacketNames(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readString(); // registry id
        int count = reader.readVarInt();
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(reader.readString());
            if (reader.readBoolean()) {
                reader.readAnonymousNbt();
            }
        }
        assertEquals(0, reader.remaining());
        return names;
    }

    private static List<String> shortNames(List<String> fullNames) {
        return fullNames.stream()
                .map(n -> n.startsWith("minecraft:") ? n.substring("minecraft:".length()) : n)
                .toList();
    }

    /** Set Slot ペイロード（766／767共通）。 */
    private static byte[] setSlotPacket(int itemId) {
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3); // slotIndex
        writer.writeByte(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        return writer.bytes();
    }

    /** Set Slot ペイロードのアイテムIDを読む。 */
    private static int readSetSlotItemId(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        return reader.readVarInt();
    }

    /** Window Click ペイロード（767形式）。 */
    private static byte[] windowClick767Packet(int itemId) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeVarInt(1); // stateId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // changed 1件
        writer.writeShort(5);
        writer.writeByte(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        writer.writeByte(0); // carried（空き）
        return writer.bytes();
    }

    /** Window Click ペイロードの changed スロットのアイテムIDを読む。 */
    private static int readWindowClickItemId(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readUnsignedByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        return reader.readVarInt();
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo767(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        session.handleClientPacket(new MinecraftPacket(0x00,
                clientProtocol == 763 ? loginStart763("Steve") : loginStart766("Steve")));
        return session;
    }

    /** 設定フェーズを終わらせ、プレイ状態まで進める。finishAckId は
     * クライアントの Acknowledge Finish Configuration のID
     * （766／767は 0x03、765／764は 0x02）。 */
    private static void finishConfig(ProxySession session, int clientProtocol,
            int serverProtocol, byte[] registryPayload, int registryServerId,
            int finishServerId, int finishAckId) {
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(registryServerId, registryPayload));
        assertFalse(registry.toClient().isEmpty());
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(finishServerId, new byte[0]));
        assertEquals(1, finish.toClient().size());
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(finishAckId, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
    }

    // ------------------------------------------------------------------
    // 767クライアント → 内部767（素通し）
    // ------------------------------------------------------------------

    @Test
    void join767Against767IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                767, 2, false, ProxyAuthConfig.defaults(), 767);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(767, session.clientProtocol());
        assertEquals(767, session.serverProtocol());

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

        // プレイ状態は素通し（Set Slot の bowl は 799 のまま）。
        Outcome slot = session.handleServerPacket(new MinecraftPacket(0x15, setSlotPacket(799)));
        assertEquals(1, slot.toClient().size());
        assertEquals(799, readSetSlotItemId(slot.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 767クライアント → 内部765（下向き）
    // ------------------------------------------------------------------

    @Test
    void join767Against765RelaysConfigWithTranslation() {
        ProxySession session = loginTo767(767, 765);
        assertEquals(767, session.clientProtocol());
        assertEquals(765, session.serverProtocol());
        // Login Success（765形式）→ 767には strict bool を足して送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（767：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（767：0x00）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation766Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());

        // Registry Data（765：0x05、codec 形式）→ 767（0x07、エントリ配列形式）。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x05, registryDataPayload()));
        assertEquals(2, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        assertEquals(0x07, registry.toClient().get(1).id());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Finish Configuration（765：0x02）→ 767（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（767：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x02, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（765：0x29）→ 767（0x2B）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        PlayTranslator.JoinGame766 parsed =
                PlayTranslator.parseJoinGame766(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimensionId());

        // Spawn Position（765：0x54）→ 767（0x56）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x56, spawn.toClient().get(0).id());

        // Keep Alive（765：0x24）→ 767（0x26）。Ping（765：0x33）→ 767（0x35）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x35, ping.toClient().get(0).id());

        // Respawn（765：0x45）→ 767（0x47、dimension は番号）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
        Reader respawnReader = new Reader(respawn.toClient().get(0).payload());
        assertEquals(0, respawnReader.readVarInt()); // dimension（overworld）
        assertEquals("minecraft:overworld", respawnReader.readString());

        // Window Click（767：0x0E）は内部765向けは落とす（データコンポーネント）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0E, windowClick767Packet(799)));
        assertTrue(click.toServer().isEmpty());
        assertTrue(click.toClient().isEmpty());
    }

    @Test
    void registryData765To767EnchantmentsReorderedInSession() {
        ProxySession session = loginTo767(767, 765);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Map<String, Tag> root = new java.util.LinkedHashMap<>();
        root.put("minecraft:enchantment",
                registryCodec("minecraft:enchantment", ENCHANTMENT_765));
        byte[] codec = Nbt.writeAnonymous(new Tag.CompoundTag(root));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x05, codec));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(ENCHANTMENT_767, names);
        assertEquals(22, names.indexOf("mending"));
        assertEquals(36, names.indexOf("sweeping_edge"));
    }

    @Test
    void client767ConfigDropsNewPacketsForOldServer() {
        ProxySession session = loginTo767(767, 765);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        // Custom Report Details（設定 0x08）／Server Links（設定 0x09）は
        // 765サーバーに無いため落とす。
        Outcome report = session.handleClientPacket(
                new MinecraftPacket(0x08, new byte[0]));
        assertTrue(report.toServer().isEmpty());
        Outcome links = session.handleClientPacket(
                new MinecraftPacket(0x09, new byte[0]));
        assertTrue(links.toServer().isEmpty());
    }

    // ------------------------------------------------------------------
    // 767クライアント → 内部766（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join767Against766TranslatesSlots() {
        ProxySession session = loginTo767(767, 766);
        // Login Success（766形式）→ 767も同じ形式のためそのまま。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        finishConfig(session, 767, 766, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（766：0x2B）→ 767（0x2B、素通し）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        PlayTranslator.JoinGame766 parsed =
                PlayTranslator.parseJoinGame766(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());

        // Set Slot（766：0x15、bowl 848）→ 767（0x15、bowl 799）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(848)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(799, readSetSlotItemId(slot.toClient().get(0).payload()));

        // Window Click（767：0x0E、bowl 799）→ 766（0x0E、bowl 848）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0E, windowClick767Packet(799)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0E, click.toServer().get(0).id());
        assertEquals(848, readWindowClickItemId(click.toServer().get(0).payload()));

        // Respawn（766：0x47）→ 767（0x47、素通し）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x47, respawn766Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
        Reader respawnReader = new Reader(respawn.toClient().get(0).payload());
        assertEquals(0, respawnReader.readVarInt());
    }

    @Test
    void registryData766To767EnchantmentsReorderedInSession() {
        ProxySession session = loginTo767(767, 766);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:enchantment", ENCHANTMENT_766)));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(ENCHANTMENT_767, names);
        assertEquals(22, names.indexOf("mending"));
    }

    // ------------------------------------------------------------------
    // 766クライアント → 内部767（上向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join766Against767TranslatesSlots() {
        ProxySession session = loginTo767(766, 767);
        // Login Success（767形式）→ 766には strict bool を落として送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        finishConfig(session, 766, 767,
                registryPacket("minecraft:enchantment", ENCHANTMENT_767), 0x07, 0x03, 0x03);

        // Join Game（767：0x2B）→ 766（0x2B、素通し）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());

        // Set Slot（767：0x15、bowl 799）→ 766（0x15、bowl 848）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(799)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(848, readSetSlotItemId(slot.toClient().get(0).payload()));

        // Window Click（766：0x0E、bowl 848）→ 767（0x0E、bowl 799）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0E, windowClick767Packet(848)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0E, click.toServer().get(0).id());
        assertEquals(799, readWindowClickItemId(click.toServer().get(0).payload()));

        // Respawn（767：0x47）→ 766（0x47、素通し）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x47, respawn766Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
    }

    @Test
    void registryData767To766EnchantmentsReorderedInSession() {
        ProxySession session = loginTo767(766, 767);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:enchantment", ENCHANTMENT_767)));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(ENCHANTMENT_766, names);
        assertEquals(40, names.indexOf("mending"));
    }

    @Test
    void config767DropsNewPacketsAndJukeboxSongFor766Client() {
        ProxySession session = loginTo767(766, 767);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        // Custom Report Details（0x0F）／Server Links（0x10）は766に無いため落とす。
        Outcome report = session.handleServerPacket(
                new MinecraftPacket(0x0F, new byte[0]));
        assertTrue(report.toClient().isEmpty());
        Outcome links = session.handleServerPacket(
                new MinecraftPacket(0x10, new byte[0]));
        assertTrue(links.toClient().isEmpty());
        // jukebox_song の Registry Data も766に無いため落とす。
        Outcome jukebox = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", List.of("13", "cat"))));
        assertTrue(jukebox.toClient().isEmpty());
        // プレイ状態の新規パケット（0x7A／0x7B）も落とす（参加後に確認）。
        session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        session.handleServerPacket(new MinecraftPacket(0x2B, joinGame766Payload()));
        Outcome playReport = session.handleServerPacket(
                new MinecraftPacket(0x7A, new byte[0]));
        assertTrue(playReport.toClient().isEmpty());
        Outcome playLinks = session.handleServerPacket(
                new MinecraftPacket(0x7B, new byte[0]));
        assertTrue(playLinks.toClient().isEmpty());
    }

    // ------------------------------------------------------------------
    // 767クライアント → 内部764（下向き、join レベル）
    // ------------------------------------------------------------------

    @Test
    void join767Against764RelaysJoinLevel() {
        ProxySession session = loginTo767(767, 764);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        finishConfig(session, 767, 764, registryDataPayload(), 0x05, 0x02, 0x03);

        // Join Game（764：0x29）→ 767（0x2B）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());

        // Spawn Position（764：0x52）→ 767（0x56）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x52, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x56, spawn.toClient().get(0).id());

        // Keep Alive（764：0x24）→ 767（0x26）。Ping（764：0x33）→ 767（0x35）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x35, ping.toClient().get(0).id());

        // Respawn（764：0x43）→ 767（0x47、dimension は番号）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x43, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
        Reader respawnReader = new Reader(respawn.toClient().get(0).payload());
        assertEquals(0, respawnReader.readVarInt());
    }

    // ------------------------------------------------------------------
    // 767クライアント → 内部763（設定フェーズを合成）
    // ------------------------------------------------------------------

    @Test
    void join767Against763SynthesizesConfig() {
        ProxySession.HandshakeResult result = handshake(
                767, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(767, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は763形式（bool 付き）に直してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PLAYER_UUID, loginReader.readUuid());

        // Login Success（763形式）→ 767には strict bool を足して送る。
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

        // Login Acknowledged（767：0x03）で合成した設定フェーズを送る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertTrue(ack.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(3, ack.toClient().size());
        assertEquals(0x07, ack.toClient().get(0).id());
        assertEquals(0x07, ack.toClient().get(1).id());
        assertEquals(0x03, ack.toClient().get(2).id());

        // Finish Configuration の応答（767：0x03）で預かった Join Game を送る。
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
    // 763クライアント → 内部767（設定フェーズを767クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join763Against767ConsumesConfigAs767Client() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 767);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(767, session.serverProtocol());

        // Login Start は763形式から767形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（767形式）→ 763には strict bool を落として送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（767：0x07）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x07, registryData766Payload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Add Resource Pack（767：0x09）は保持する。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x09, addResourcePackPayload()));
        assertTrue(pack.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());

        // Finish Configuration（767：0x03）→ 応答（0x03）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（767：0x2B）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());

        // Custom Report Details（0x0F）／Server Links（0x10）は763に無いため落とす。
        // （参加後の設定フェーズで来た場合の確認。ここでは消費側の動きを直接見る。）
        ProxySession.HandshakeResult result2 = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 767);
        ProxySession session2 = result2.session();
        session2.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve")));
        session2.handleServerPacket(new MinecraftPacket(0x02, loginSuccess766Payload()));
        Outcome report = session2.handleServerPacket(
                new MinecraftPacket(0x0F, new byte[0]));
        assertTrue(report.toClient().isEmpty());
        Outcome links = session2.handleServerPacket(
                new MinecraftPacket(0x10, new byte[0]));
        assertTrue(links.toClient().isEmpty());
    }

    // ------------------------------------------------------------------
    // 765クライアント → 内部767（上向き）
    // ------------------------------------------------------------------

    @Test
    void join765Against767RelaysConfigWithTranslation() {
        ProxySession session = loginTo767(765, 767);
        // Login Success（767形式）→ 765には strict bool を落として送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 765, 767, registryData766Payload(), 0x07, 0x03, 0x02);

        // Join Game（767：0x2B）→ 765（0x29）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x29, join.toClient().get(0).id());
        PlayTranslator.JoinGame765 parsed =
                PlayTranslator.parseJoinGame765(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());

        // Respawn（767：0x47）→ 765（0x45、dimension は文字列）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x47, respawn766Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x45, respawn.toClient().get(0).id());
        Reader respawnReader = new Reader(respawn.toClient().get(0).payload());
        assertEquals("minecraft:overworld", respawnReader.readString());

        // プレイ状態の新規パケット（0x7A／0x7B）は765に無いため落とす。
        Outcome playReport = session.handleServerPacket(
                new MinecraftPacket(0x7A, new byte[0]));
        assertTrue(playReport.toClient().isEmpty());
        Outcome playLinks = session.handleServerPacket(
                new MinecraftPacket(0x7B, new byte[0]));
        assertTrue(playLinks.toClient().isEmpty());
    }

    @Test
    void registryData767To765EnchantmentsReorderedInSession() {
        ProxySession session = loginTo767(765, 767);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:enchantment", ENCHANTMENT_767)));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x05, registry.toClient().get(0).id());
        Map<String, Tag.CompoundTag> registries =
                PlayTranslator.parseRegistryData(registry.toClient().get(0).payload());
        Tag.CompoundTag enchantment = registries.get("minecraft:enchantment");
        assertNotNull(enchantment);
        List<Tag> entries = enchantment.getList("value").map(Tag.ListTag::value).orElse(List.of());
        assertEquals(39, entries.size());
        Map<String, Integer> ids = new java.util.LinkedHashMap<>();
        for (Tag entry : entries) {
            Tag.CompoundTag compound = (Tag.CompoundTag) entry;
            String name = compound.getString("name").orElseThrow();
            int id = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value()).orElseThrow();
            ids.put(name, id);
        }
        assertEquals(37, ids.get("minecraft:mending"));
        assertEquals(0, ids.get("minecraft:protection"));
        assertEquals(19, ids.get("minecraft:sweeping"));
    }
}
