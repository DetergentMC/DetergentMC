// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

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
 * 769（1.21.4）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession768Test} と同じ段取りで、769の番号・形式で確かめる。
 * 769のクライアント行きパケットIDは768と全て同一（変わるのはサーバー行きの
 * 24件＋新規3件＋削除1件）のため、サーバー→クライアントは主に
 * ペイロード（スロット・Player Info・バイオーム）の変換を確かめる。
 */
class ProxySession769Test {

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

    /** 766／767／768／769の Login Start：名前＋UUID必須。 */
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

    /** 765／768／769の Login Success（strict bool なし）。 */
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

    /** 767の Client Information（設定フェーズ。766と同じ8フィールド）。 */
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

    /** 768／769の Client Information（設定フェーズ。particleStatus=0 付き9フィールド）。 */
    private static byte[] clientInformation769Payload() {
        return appendParticleStatus(clientInformation766Payload(), 0);
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

    /** 766／767／768／769の Registry Data 1件（dimension_type、エントリ配列形式）。 */
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

    /** 768／769の Join Game（SpawnInfo の末尾に seaLevel=63）。 */
    private static byte[] joinGame769Payload() {
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
        writer.writeVarInt(0); // portalCooldown
        writer.writeVarInt(63); // seaLevel
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

    /** 768／769の Respawn（SpawnInfo の末尾に seaLevel=63、copyMetadata は末尾のバイト）。 */
    private static byte[] respawn769Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // dimension（overworld）
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0); // portalCooldown
        writer.writeVarInt(63); // seaLevel
        writer.writeByte(1); // copyMetadata
        return writer.bytes();
    }

    /** 765の Respawn（dimension は文字列、copyMetadata は末尾の bool）。 */
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

    /** 766／767／768／769の Add Resource Pack（UUID付き、プロンプトなし）。 */
    private static byte[] addResourcePackPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        writer.writeString("https://127.0.0.1:25567/detergentmc-pack-1.21.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 768のバイオーム64件（plains は39）。 */
    private static List<String> biomes768() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (i == 38) {
                names.add("old_growth_spruce_taiga");
            } else if (i == 39) {
                names.add("plains");
            } else {
                names.add("biome_" + i);
            }
        }
        return names;
    }

    /** 769のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes769() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            if (i == 38) {
                names.add("old_growth_spruce_taiga");
            } else if (i == 39) {
                names.add("pale_garden");
            } else if (i == 40) {
                names.add("plains");
            } else if (i < 38) {
                names.add("biome_" + i);
            } else {
                names.add("biome_" + (i - 1));
            }
        }
        return names;
    }

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

    /** 766／767／768／769形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767／768／769形式の Registry Data パケットを読み、名前列を返す。 */
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

    /** Set Slot ペイロード（766／767共通：windowId は u8）。 */
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

    /** Set Slot ペイロード（768／769：windowId は varint）。 */
    private static byte[] setSlotPacket768(int itemId) {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId（varint）
        writer.writeVarInt(0); // stateId
        writer.writeShort(3); // slotIndex
        writer.writeByte(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        return writer.bytes();
    }

    /** Set Slot ペイロード（766／767形式）のアイテムIDを読む。 */
    private static int readSetSlotItemId(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readByte(); // windowId
        reader.readVarInt(); // stateId
        reader.readShort(); // slotIndex
        reader.readByte(); // count
        return reader.readVarInt();
    }

    /** Set Slot ペイロード（768／769形式）のアイテムIDを読む。 */
    private static int readSetSlotItemId768(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // windowId（varint）
        reader.readVarInt(); // stateId
        reader.readShort(); // slotIndex
        reader.readByte(); // count
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

    /** Window Click ペイロード（768／769形式。windowId は varint）。 */
    private static byte[] windowClick768Packet(int itemId) {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId（varint）
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

    /** Window Click ペイロード（767形式）の changed スロットのアイテムIDを読む。 */
    private static int readWindowClickItemId(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readUnsignedByte(); // windowId
        reader.readVarInt(); // stateId
        reader.readShort(); // slot
        reader.readByte(); // button
        reader.readVarInt(); // mode
        reader.readVarInt(); // changed
        reader.readShort(); // slot
        reader.readByte(); // count
        return reader.readVarInt();
    }

    /** Window Click ペイロード（768／769形式）の changed スロットのアイテムIDを読む。 */
    private static int readWindowClickItemId768(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // windowId（varint）
        reader.readVarInt(); // stateId
        reader.readShort(); // slot
        reader.readByte(); // button
        reader.readVarInt(); // mode
        reader.readVarInt(); // changed
        reader.readShort(); // slot
        reader.readByte(); // count
        return reader.readVarInt();
    }

    /** スロット1個だけのペイロード（Set Cursor Item 用）。 */
    private static byte[] slotOnlyPacket(int itemId) {
        Writer writer = new Writer();
        writer.writeByte(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        return writer.bytes();
    }

    /** 768形式の Player Info（update_list_order 付き、プレイヤー1人）。 */
    private static byte[] playerInfo768Payload() {
        Writer writer = new Writer();
        writer.writeByte(0x7F); // 全アクション（list_order 含む）
        writer.writeVarInt(1); // count 1
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Alice");
        writer.writeVarInt(0); // properties 0件
        writer.writeBoolean(false); // initialize_chat：セッション無し
        writer.writeVarInt(0); // update_game_mode（survival）
        writer.writeVarInt(1); // listed
        writer.writeVarInt(5); // latency
        writer.writeBoolean(false); // displayName 無し
        writer.writeVarInt(9); // listPriority
        return writer.bytes();
    }

    /** 769形式の Player Info（update_hat＋update_list_order 付き、プレイヤー1人）。 */
    private static byte[] playerInfo769Payload() {
        Writer writer = new Writer();
        writer.writeByte(0xFF); // 全アクション（hat＋list_order 含む）
        writer.writeVarInt(1); // count 1
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Alice");
        writer.writeVarInt(0); // properties 0件
        writer.writeBoolean(false); // initialize_chat：セッション無し
        writer.writeVarInt(0); // update_game_mode（survival）
        writer.writeVarInt(1); // listed
        writer.writeVarInt(5); // latency
        writer.writeBoolean(false); // displayName 無し
        writer.writeVarInt(9); // listPriority（bit 7）
        writer.writeBoolean(true); // showHat（bit 6）
        return writer.bytes();
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
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
     * （766／767／768／769は 0x03、765／764は 0x02）。 */
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

    /** Client Information ペイロードの末尾に particleStatus を付ける。 */
    private static byte[] appendParticleStatus(byte[] base, int particleStatus) {
        Writer writer = new Writer();
        writer.writeBytes(base);
        writer.writeVarInt(particleStatus);
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部769（素通し）
    // ------------------------------------------------------------------

    @Test
    void join769Against769IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                769, 2, false, ProxyAuthConfig.defaults(), 769);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(769, session.clientProtocol());
        assertEquals(769, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        // 769の Login Success は strict bool が無い（765と同じ形式）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // プレイ状態は素通し（Set Slot の turtle_helmet は 825 のまま）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(825)));
        assertEquals(1, slot.toClient().size());
        assertEquals(825, readSetSlotItemId768(slot.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部768（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join769Against768TranslatesSlots() {
        ProxySession session = loginTo(769, 768);
        assertEquals(769, session.clientProtocol());
        assertEquals(768, session.serverProtocol());
        // Login Success（768形式）→ 769にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（769：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（769：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation769Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation769Payload(), info.toServer().get(0).payload());

        // Registry Data（768：0x07、バイオーム64件）→ 769（0x07、65件。
        // pale_garden を39番に挿入し、plains は40に移る）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes768())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(65, names.size());
        assertEquals(39, names.indexOf("pale_garden"));
        assertEquals(40, names.indexOf("plains"));
        assertEquals(38, names.indexOf("old_growth_spruce_taiga"));

        // Finish Configuration（768：0x03）→ 769（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（769：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（768：0x2C）→ 769（0x2C、同形式のため素通し）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame769Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());
        assertArrayEquals(joinGame769Payload(), join.toClient().get(0).payload());

        // Spawn Position（768：0x5B）→ 769（0x5B）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5B, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5B, spawn.toClient().get(0).id());

        // Keep Alive（768：0x27）→ 769（0x27）。Ping（768：0x37）→ 769（0x37）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x27, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x27, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x37, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x37, ping.toClient().get(0).id());

        // Respawn（768：0x4C）→ 769（0x4C、同形式のため素通し）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn769Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn769Payload(), respawn.toClient().get(0).payload());

        // Set Slot（768：0x15、turtle_helmet 816）→ 769（0x15、825）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(816)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(825, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Window Click（769：0x10、turtle_helmet 825）→ 768（0x10、816）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick768Packet(825)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x10, click.toServer().get(0).id());
        assertEquals(816, readWindowClickItemId768(click.toServer().get(0).payload()));

        // Set Creative Slot（769：0x36、breeze_rod 1143）→ 768（0x34、1134）。
        Writer creative = new Writer();
        creative.writeShort(3);
        creative.writeByte(1);
        creative.writeVarInt(1143); // breeze_rod（769）
        creative.writeVarInt(0);
        creative.writeVarInt(0);
        Outcome creativeSlot = session.handleClientPacket(
                new MinecraftPacket(0x36, creative.bytes()));
        assertEquals(1, creativeSlot.toServer().size());
        assertEquals(0x34, creativeSlot.toServer().get(0).id());
        Reader creativeReader = new Reader(creativeSlot.toServer().get(0).payload());
        assertEquals(3, creativeReader.readShort());
        creativeReader.readByte(); // count
        assertEquals(1134, creativeReader.readVarInt()); // breeze_rod（768）

        // Player Info（768：0x40、list_order 付き）→ 769（0x40、bit 6→bit 7）。
        Outcome playerInfo = session.handleServerPacket(
                new MinecraftPacket(0x40, playerInfo768Payload()));
        assertEquals(1, playerInfo.toClient().size());
        assertEquals(0x40, playerInfo.toClient().get(0).id());
        Reader infoReader = new Reader(playerInfo.toClient().get(0).payload());
        assertEquals(0xBF, infoReader.readUnsignedByte()); // list_order は bit 7 へ
        assertEquals(1, infoReader.readVarInt());
        assertEquals(PLAYER_UUID, infoReader.readUuid());
        assertEquals("Alice", infoReader.readString());
        assertEquals(0, infoReader.readVarInt());
        assertEquals(false, infoReader.readBoolean());
        assertEquals(0, infoReader.readVarInt());
        assertEquals(1, infoReader.readVarInt());
        assertEquals(5, infoReader.readVarInt());
        assertEquals(false, infoReader.readBoolean());
        assertEquals(9, infoReader.readVarInt()); // listPriority

        // Player Input（769：0x29）→ 768（0x28、同形式）。
        Outcome input = session.handleClientPacket(
                new MinecraftPacket(0x29, new byte[]{(byte) 0x35}));
        assertEquals(1, input.toServer().size());
        assertEquals(0x28, input.toServer().get(0).id());
        assertArrayEquals(new byte[]{(byte) 0x35}, input.toServer().get(0).payload());

        // Pong（769：0x2B）→ 768（0x29）。
        Outcome pong = session.handleClientPacket(
                new MinecraftPacket(0x2B, keepAlive.bytes()));
        assertEquals(1, pong.toServer().size());
        assertEquals(0x29, pong.toServer().get(0).id());

        // Resource Pack Response（769：0x2F）→ 768（0x2D）。
        Outcome packResponse = session.handleClientPacket(
                new MinecraftPacket(0x2F, new byte[]{0x03}));
        assertEquals(1, packResponse.toServer().size());
        assertEquals(0x2D, packResponse.toServer().get(0).id());

        // Vehicle Move（769：0x20、onGround 付き）→ 768（0x20、onGround を落とす）。
        Writer vehicle = new Writer();
        vehicle.writeDouble(1.5);
        vehicle.writeDouble(64.0);
        vehicle.writeDouble(2.5);
        vehicle.writeFloat(90.0f);
        vehicle.writeFloat(0.0f);
        vehicle.writeBoolean(true); // onGround
        Outcome vehicleMove = session.handleClientPacket(
                new MinecraftPacket(0x20, vehicle.bytes()));
        assertEquals(1, vehicleMove.toServer().size());
        assertEquals(0x20, vehicleMove.toServer().get(0).id());
        Reader vehicleReader = new Reader(vehicleMove.toServer().get(0).payload());
        assertEquals(1.5, vehicleReader.readDouble(), 0.0001);
        assertEquals(64.0, vehicleReader.readDouble(), 0.0001);
        assertEquals(2.5, vehicleReader.readDouble(), 0.0001);
        assertEquals(90.0f, vehicleReader.readFloat(), 0.0001f);
        assertEquals(0.0f, vehicleReader.readFloat(), 0.0001f);
        assertEquals(0, vehicleReader.remaining()); // onGround は落とした

        // Chat Message（769：0x07）→ 768（0x07）。
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x07, new byte[]{0x00}));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x07, chat.toServer().get(0).id());

        // Craft Recipe Request（769：0x25）→ 768サーバーへは 0x24 でそのまま送る。
        Outcome craft = session.handleClientPacket(
                new MinecraftPacket(0x25, new byte[]{0x00, 0x00}));
        assertEquals(1, craft.toServer().size());
        assertEquals(0x24, craft.toServer().get(0).id());

        // Displayed Recipe（769：0x2D）→ 768サーバーへは 0x2B でそのまま送る。
        Outcome displayed = session.handleClientPacket(
                new MinecraftPacket(0x2D, new byte[]{0x00}));
        assertEquals(1, displayed.toServer().size());
        assertEquals(0x2B, displayed.toServer().get(0).id());

        // 769だけのクライアント→サーバーパケットは落とす。
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x22, new byte[]{0x00, 0x00})).toServer().isEmpty()); // Pick Item From Block
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x23, new byte[]{0x00})).toServer().isEmpty()); // Pick Item From Entity
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x2A, new byte[0])).toServer().isEmpty()); // Player Loaded
    }

    // ------------------------------------------------------------------
    // 768クライアント → 内部769（上向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join768Against769TranslatesSlots() {
        ProxySession session = loginTo(768, 769);
        // Login Success（769形式）→ 768にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 768, 769, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（769：0x2C）→ 768（0x2C、同形式のため素通し）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame769Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());
        assertArrayEquals(joinGame769Payload(), join.toClient().get(0).payload());

        // Spawn Position（769：0x5B）→ 768（0x5B）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5B, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5B, spawn.toClient().get(0).id());

        // Keep Alive（769：0x27）→ 768（0x27）。Ping（769：0x37）→ 768（0x37）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x27, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x27, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x37, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x37, ping.toClient().get(0).id());

        // Respawn（769：0x4C）→ 768（0x4C、同形式のため素通し）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn769Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn769Payload(), respawn.toClient().get(0).payload());

        // Set Slot（769：0x15、turtle_helmet 825）→ 768（0x15、816）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(825)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(816, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Set Slot（769：0x15、resin_brick 1167）→ 768（0x15、石 1）。
        Outcome resin = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(1167)));
        assertEquals(1, readSetSlotItemId768(resin.toClient().get(0).payload()));

        // Window Click（768：0x10、turtle_helmet 816）→ 769（0x10、825）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick768Packet(816)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x10, click.toServer().get(0).id());
        assertEquals(825, readWindowClickItemId768(click.toServer().get(0).payload()));

        // Set Creative Slot（768：0x34、breeze_rod 1134）→ 769（0x36、1143）。
        Writer creative = new Writer();
        creative.writeShort(3);
        creative.writeByte(1);
        creative.writeVarInt(1134); // breeze_rod（768）
        creative.writeVarInt(0);
        creative.writeVarInt(0);
        Outcome creativeSlot = session.handleClientPacket(
                new MinecraftPacket(0x34, creative.bytes()));
        assertEquals(1, creativeSlot.toServer().size());
        assertEquals(0x36, creativeSlot.toServer().get(0).id());
        Reader creativeReader = new Reader(creativeSlot.toServer().get(0).payload());
        assertEquals(3, creativeReader.readShort());
        creativeReader.readByte(); // count
        assertEquals(1143, creativeReader.readVarInt()); // breeze_rod（769）

        // Player Input（768：0x28）→ 769（0x29、同形式）。
        Outcome input = session.handleClientPacket(
                new MinecraftPacket(0x28, new byte[]{(byte) 0x35}));
        assertEquals(1, input.toServer().size());
        assertEquals(0x29, input.toServer().get(0).id());
        assertArrayEquals(new byte[]{(byte) 0x35}, input.toServer().get(0).payload());

        // Pong（768：0x29）→ 769（0x2B）。
        Outcome pong = session.handleClientPacket(
                new MinecraftPacket(0x29, keepAlive.bytes()));
        assertEquals(1, pong.toServer().size());
        assertEquals(0x2B, pong.toServer().get(0).id());

        // Vehicle Move（768：0x20、onGround 無し）→ 769（0x20、onGround=false を付ける）。
        Writer vehicle = new Writer();
        vehicle.writeDouble(1.5);
        vehicle.writeDouble(64.0);
        vehicle.writeDouble(2.5);
        vehicle.writeFloat(90.0f);
        vehicle.writeFloat(0.0f);
        Outcome vehicleMove = session.handleClientPacket(
                new MinecraftPacket(0x20, vehicle.bytes()));
        assertEquals(1, vehicleMove.toServer().size());
        assertEquals(0x20, vehicleMove.toServer().get(0).id());
        Reader vehicleReader = new Reader(vehicleMove.toServer().get(0).payload());
        assertEquals(1.5, vehicleReader.readDouble(), 0.0001);
        assertEquals(64.0, vehicleReader.readDouble(), 0.0001);
        assertEquals(2.5, vehicleReader.readDouble(), 0.0001);
        assertEquals(90.0f, vehicleReader.readFloat(), 0.0001f);
        assertEquals(0.0f, vehicleReader.readFloat(), 0.0001f);
        assertEquals(false, vehicleReader.readBoolean()); // onGround（補った既定値）

        // Player Info（769：0x40、hat＋list_order 付き）→ 768（0x40、hat を落とし list_order は bit 6 へ）。
        Outcome playerInfo = session.handleServerPacket(
                new MinecraftPacket(0x40, playerInfo769Payload()));
        assertEquals(1, playerInfo.toClient().size());
        assertEquals(0x40, playerInfo.toClient().get(0).id());
        assertArrayEquals(playerInfo768Payload(), playerInfo.toClient().get(0).payload());

        // Set Cursor Item（769：0x5A、breeze_rod 1143）→ 768（0x5A、1134）。
        Outcome cursor = session.handleServerPacket(
                new MinecraftPacket(0x5A, slotOnlyPacket(1143)));
        assertEquals(1, cursor.toClient().size());
        assertEquals(0x5A, cursor.toClient().get(0).id());
        Reader cursorReader = new Reader(cursor.toClient().get(0).payload());
        assertEquals(1, cursorReader.readByte()); // count
        assertEquals(1134, cursorReader.readVarInt()); // breeze_rod（768）

        // Set Player Inventory（769：0x66、turtle_helmet 825）→ 768（0x66、816）。
        Writer inventory = new Writer();
        inventory.writeVarInt(5); // slotId
        inventory.writeByte(1); // count
        inventory.writeVarInt(825); // turtle_helmet（769）
        inventory.writeVarInt(0); // added
        inventory.writeVarInt(0); // removed
        Outcome playerInventory = session.handleServerPacket(
                new MinecraftPacket(0x66, inventory.bytes()));
        assertEquals(1, playerInventory.toClient().size());
        assertEquals(0x66, playerInventory.toClient().get(0).id());
        Reader invReader = new Reader(playerInventory.toClient().get(0).payload());
        assertEquals(5, invReader.readVarInt()); // slotId
        assertEquals(1, invReader.readByte()); // count
        assertEquals(816, invReader.readVarInt()); // turtle_helmet（768）

        // Set Held Item（769：0x63、varint 3）→ 768（0x63、i8 3）。
        Outcome heldItem = session.handleServerPacket(
                new MinecraftPacket(0x63, new byte[]{3}));
        assertEquals(1, heldItem.toClient().size());
        assertEquals(0x63, heldItem.toClient().get(0).id());
        assertArrayEquals(new byte[]{3}, heldItem.toClient().get(0).payload());

        // Pick Item（768：0x22）は769に相当が無いため落とす。
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x22, new byte[]{0x00})).toServer().isEmpty());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部767（合成変換あり）
    // ------------------------------------------------------------------

    @Test
    void join769Against767TranslatesSlots() {
        ProxySession session = loginTo(769, 767);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 769, 767, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（767：0x2B）→ 769（0x2C、seaLevel 付き）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());

        // Set Slot（767：0x15、turtle_helmet 794）→ 769（0x15、825）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(794)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(825, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Window Click（769：0x10、turtle_helmet 825）→ 767（0x0E、794）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick768Packet(825)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0E, click.toServer().get(0).id());
        assertEquals(794, readWindowClickItemId(click.toServer().get(0).payload()));

        // Player Info（767：0x3E）→ 769（0x40、素通し。list_order・hat は無い）。
        Writer info767 = new Writer();
        info767.writeByte(0x3F);
        info767.writeVarInt(1);
        info767.writeUuid(PLAYER_UUID);
        info767.writeString("Alice");
        info767.writeVarInt(0);
        info767.writeBoolean(false);
        info767.writeVarInt(0);
        info767.writeVarInt(1);
        info767.writeVarInt(5);
        info767.writeBoolean(false);
        Outcome playerInfo = session.handleServerPacket(
                new MinecraftPacket(0x3E, info767.bytes()));
        assertEquals(1, playerInfo.toClient().size());
        assertEquals(0x40, playerInfo.toClient().get(0).id());
        assertArrayEquals(info767.bytes(), playerInfo.toClient().get(0).payload());

        // Respawn（767：0x47）→ 769（0x4C、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x47, respawn766Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn769Payload(), respawn.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 767クライアント → 内部769（合成変換あり）
    // ------------------------------------------------------------------

    @Test
    void join767Against769TranslatesSlots() {
        ProxySession session = loginTo(767, 769);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        finishConfig(session, 767, 769, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（769：0x2C）→ 767（0x2B、seaLevel を落とす）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame769Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());

        // Set Slot（769：0x15、turtle_helmet 825）→ 767（0x15、794）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(825)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(794, readSetSlotItemId(slot.toClient().get(0).payload()));

        // Player Info（769：0x40、hat＋list_order 付き）→ 767（0x3E、両方落とす）。
        Outcome playerInfo = session.handleServerPacket(
                new MinecraftPacket(0x40, playerInfo769Payload()));
        assertEquals(1, playerInfo.toClient().size());
        assertEquals(0x3E, playerInfo.toClient().get(0).id());
        Reader infoReader = new Reader(playerInfo.toClient().get(0).payload());
        assertEquals(0x3F, infoReader.readUnsignedByte()); // hat・list_order 無し
        assertEquals(1, infoReader.readVarInt());
        assertEquals(PLAYER_UUID, infoReader.readUuid());
        assertEquals("Alice", infoReader.readString());

        // Window Click（767：0x0E、turtle_helmet 794）→ 769（0x10、825）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0E, windowClick767Packet(794)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x10, click.toServer().get(0).id());
        assertEquals(825, readWindowClickItemId768(click.toServer().get(0).payload()));

        // Set Creative Slot（767：0x32、breeze_rod 1332）→ 769（0x36、1143）。
        Writer creative = new Writer();
        creative.writeShort(3);
        creative.writeByte(1);
        creative.writeVarInt(1332); // breeze_rod（767）
        creative.writeVarInt(0);
        creative.writeVarInt(0);
        Outcome creativeSlot = session.handleClientPacket(
                new MinecraftPacket(0x32, creative.bytes()));
        assertEquals(1, creativeSlot.toServer().size());
        assertEquals(0x36, creativeSlot.toServer().get(0).id());
        Reader creativeReader = new Reader(creativeSlot.toServer().get(0).payload());
        assertEquals(3, creativeReader.readShort());
        creativeReader.readByte(); // count
        assertEquals(1143, creativeReader.readVarInt()); // breeze_rod（769）

        // Respawn（769：0x4C）→ 767（0x47、seaLevel を落とす）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn769Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
        assertArrayEquals(respawn766Payload(), respawn.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部766（合成変換あり）
    // ------------------------------------------------------------------

    @Test
    void join769Against766TranslatesSlots() {
        ProxySession session = loginTo(769, 766);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 769, 766, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（766：0x2B）→ 769（0x2C、seaLevel 付き）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());

        // Set Slot（766：0x15、turtle_helmet 794）→ 769（0x15、825）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(794)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(825, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Set Slot（766：0x15、bowl 848）→ 769（0x15、830）。
        Outcome bowl = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(848)));
        assertEquals(830, readSetSlotItemId768(bowl.toClient().get(0).payload()));

        // Window Click（769：0x10、turtle_helmet 825）→ 766（0x0E、794）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick768Packet(825)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0E, click.toServer().get(0).id());
        assertEquals(794, readWindowClickItemId(click.toServer().get(0).payload()));

        // Respawn（766：0x47）→ 769（0x4C、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x47, respawn766Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn769Payload(), respawn.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 766クライアント → 内部769（合成変換あり）
    // ------------------------------------------------------------------

    @Test
    void join766Against769TranslatesSlots() {
        ProxySession session = loginTo(766, 769);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        finishConfig(session, 766, 769, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（769：0x2C）→ 766（0x2B、seaLevel を落とす）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame769Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());

        // Set Slot（769：0x15、turtle_helmet 825）→ 766（0x15、794）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(825)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(794, readSetSlotItemId(slot.toClient().get(0).payload()));

        // Set Slot（769：0x15、bowl 830）→ 766（0x15、848）。
        Outcome bowl = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(830)));
        assertEquals(848, readSetSlotItemId(bowl.toClient().get(0).payload()));

        // Window Click（766：0x0E）は769向けに変換できないため落とす。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0E, windowClick767Packet(794)));
        assertTrue(click.toServer().isEmpty());

        // Respawn（769：0x4C）→ 766（0x47、seaLevel を落とす）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn769Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
        assertArrayEquals(respawn766Payload(), respawn.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部763（設定フェーズを合成）
    // ------------------------------------------------------------------

    @Test
    void join769Against763SynthesizesConfig() {
        ProxySession.HandshakeResult result = handshake(
                769, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(769, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は763形式（bool 付き）に直してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PLAYER_UUID, loginReader.readUuid());

        // Login Success（763形式）→ 769にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Join Game（763：0x28）はプロキシが預かり、まだクライアントへ送らない。
        Outcome joinHeld = session.handleServerPacket(
                new MinecraftPacket(0x28, joinGame763Payload()));
        assertTrue(joinHeld.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Login Acknowledged（769：0x03）で合成した設定フェーズを送る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertTrue(ack.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(3, ack.toClient().size());
        assertEquals(0x07, ack.toClient().get(0).id());
        assertEquals(0x07, ack.toClient().get(1).id());
        assertEquals(0x03, ack.toClient().get(2).id());

        // Finish Configuration の応答（769：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x2C, finishAck.toClient().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Reader joinReader = new Reader(finishAck.toClient().get(0).payload());
        assertEquals(42, joinReader.readInt()); // entityId
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部769（設定フェーズを769クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join763Against769ConsumesConfigAs769Client() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 769);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(769, session.serverProtocol());

        // Login Start は763形式から769形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（769形式）→ 763にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        // 763クライアント向けにプロキシが合成する Client Information は
        // 769形式（9フィールド。particleStatus=0 付き）。8フィールドのまま
        // 送ると769のバニラが読み取り途中で切断する。
        byte[] synthInfo = success.toServer().get(1).payload();
        assertEquals(PlayTranslator.clientInformationPayload().length + 1, synthInfo.length);
        assertEquals(0, synthInfo[synthInfo.length - 1]);
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（769：0x07）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x07, registryData766Payload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Add Resource Pack（769：0x09）は保持する。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x09, addResourcePackPayload()));
        assertTrue(pack.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());

        // Finish Configuration（769：0x03）→ 応答（0x03）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（769：0x2C）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame769Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());

        // Custom Report Details（0x0F）／Server Links（0x10）は763に無いため落とす。
        ProxySession.HandshakeResult result2 = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 769);
        ProxySession session2 = result2.session();
        session2.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve")));
        session2.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome report = session2.handleServerPacket(
                new MinecraftPacket(0x0F, new byte[0]));
        assertTrue(report.toClient().isEmpty());
        Outcome links = session2.handleServerPacket(
                new MinecraftPacket(0x10, new byte[0]));
        assertTrue(links.toClient().isEmpty());
    }

    // ------------------------------------------------------------------
    // 設定フェーズのレジストリ並べ替え
    // ------------------------------------------------------------------

    @Test
    void registryData768To769InsertsPaleGardenInSession() {
        ProxySession session = loginTo(769, 768);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes768())));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(65, names.size());
        assertEquals(39, names.indexOf("pale_garden"));
        assertEquals(40, names.indexOf("plains"));
    }

    @Test
    void registryData769To768DropsPaleGardenInSession() {
        ProxySession session = loginTo(768, 769);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes769())));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(64, names.size());
        assertEquals(39, names.indexOf("plains"));
        assertFalse(names.contains("pale_garden"));
    }

    @Test
    void registryData769To767DropsPaleGardenInSession() {
        ProxySession session = loginTo(767, 769);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes769())));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(64, names.size());
        assertEquals(39, names.indexOf("plains"));
        assertFalse(names.contains("pale_garden"));
    }

    // ------------------------------------------------------------------
    // Client Information（Settings）の particleStatus 変換
    // ------------------------------------------------------------------

    @Test
    void clientInformationGainsParticleStatusToward769Server() {
        // 767クライアント → 内部769：設定フェーズの Settings（0x00）に
        // particleStatus=0（all）を付けて送る。付けないと 769 サーバーが
        // 読み取り途中で切断する。
        ProxySession session = loginTo(767, 769);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        // Finish Ack は 769 でも 0x03 のまま（764／765向けの付け替え 0x02 ではない）。
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation766Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        byte[] forwarded = info.toServer().get(0).payload();
        assertEquals(clientInformation766Payload().length + 1, forwarded.length);
        assertEquals(0, forwarded[forwarded.length - 1]);
        // 先頭は元の 8 フィールドと同じ。
        assertArrayEquals(clientInformation766Payload(),
                java.util.Arrays.copyOf(forwarded, clientInformation766Payload().length));
    }

    @Test
    void clientInformation768KeepsParticleStatusToward769Server() {
        // 768クライアント → 内部769：設定フェーズの Settings（0x00）は
        // 768も769も9フィールド（particleStatus 付き）のため、そのまま通す。
        // 8フィールドに落とすと 769 サーバーが読み取り途中で切断し、
        // Join Game が届かなくなる（統合テスト
        // fake768ClientJoinsRealVanilla1214ThroughProxy で発覚した実バグ）。
        ProxySession session = loginTo(768, 769);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation769Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation769Payload(), info.toServer().get(0).payload());
    }

    @Test
    void clientInformationLosesParticleStatusToward767Server() {
        // 769クライアント → 内部767：設定フェーズとプレイ中の Settings から
        // particleStatus を落として送る。
        ProxySession session = loginTo(769, 767);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // 設定フェーズの Settings（0x00、particleStatus=2 付き）。
        byte[] info769 = appendParticleStatus(clientInformation766Payload(), 2);
        Outcome info = session.handleClientPacket(new MinecraftPacket(0x00, info769));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation766Payload(), info.toServer().get(0).payload());

        // プレイ状態まで進める（ack は既に送ったためここでは送らない）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryData766Payload()));
        assertFalse(registry.toClient().isEmpty());
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // プレイ中の Settings（769：0x0C）→ 767（0x0A）。particleStatus を落とす。
        Outcome playInfo = session.handleClientPacket(new MinecraftPacket(0x0C, info769));
        assertEquals(1, playInfo.toServer().size());
        assertEquals(0x0A, playInfo.toServer().get(0).id());
        assertArrayEquals(clientInformation766Payload(), playInfo.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部765（join レベル）
    // ------------------------------------------------------------------

    @Test
    void join769Against765RelaysJoinLevel() {
        ProxySession session = loginTo(769, 765);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 769, 765, registryDataPayload(), 0x05, 0x02, 0x03);

        // Join Game（765：0x29）→ 769（0x2C）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());

        // Spawn Position（765：0x54）→ 769（0x5B）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5B, spawn.toClient().get(0).id());

        // Keep Alive（765：0x24）→ 769（0x27）。Ping（765：0x33）→ 769（0x37）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x27, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x37, ping.toClient().get(0).id());

        // Respawn（765：0x45）→ 769（0x4C、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn769Payload(), respawn.toClient().get(0).payload());

        // プレイ状態のその他パケットは769クライアントには落とす（join レベルのみ）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(799)));
        assertTrue(slot.toClient().isEmpty());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部764（join レベル）
    // ------------------------------------------------------------------

    @Test
    void join769Against764RelaysJoinLevel() {
        ProxySession session = loginTo(769, 764);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // 764も設定フェーズがある（Registry Data 0x05／Finish 0x02）。
        finishConfig(session, 769, 764, registryDataPayload(), 0x05, 0x02, 0x03);

        // Join Game（764：0x29）→ 769（0x2C）。764／765の Join Game は同形式。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());

        // Spawn Position（764：0x52）→ 769（0x5B）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x52, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5B, spawn.toClient().get(0).id());

        // Keep Alive（764：0x24）→ 769（0x27）。Ping（764：0x33）→ 769（0x37）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x27, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x37, ping.toClient().get(0).id());

        // Respawn（764：0x43）→ 769（0x4C、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x43, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn769Payload(), respawn.toClient().get(0).payload());

        // プレイ状態のその他パケットは769クライアントには落とす（join レベルのみ）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(799)));
        assertTrue(slot.toClient().isEmpty());
    }

    // ------------------------------------------------------------------
    // 765クライアント → 内部769（join レベル）
    // ------------------------------------------------------------------

    @Test
    void join765Against769RelaysJoinLevel() {
        ProxySession session = loginTo(765, 769);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 765, 769, registryData766Payload(), 0x07, 0x03, 0x02);

        // Join Game（769：0x2C）→ 765（0x29）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame769Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x29, join.toClient().get(0).id());

        // Spawn Position（769：0x5B）→ 765（0x54）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5B, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x54, spawn.toClient().get(0).id());

        // Respawn（769：0x4C）→ 765（0x45）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn769Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x45, respawn.toClient().get(0).id());

        // プレイ状態のその他パケットは765クライアントには落とす（join レベルのみ）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(825)));
        assertTrue(slot.toClient().isEmpty());
    }
}
