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
 * 770（1.21.5）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession769Test} と同じ段取りで、770の番号・形式で確かめる。
 * 770のクライアント行きパケットIDは769から1つずつ小さくなる（experience_orb
 * の削除による繰り上がり）。サーバー行きは update_sign 等5件＋新規2件が
 * 変わる。ペイロードでは Player Chat の globalIndex、チャットの checksum、
 * Set Creative Slot の UntrustedSlot、Window Click の破棄を確かめる。
 */
class ProxySession770Test {

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

    /** 766／767／768／769／770の Login Start：名前＋UUID必須。 */
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

    /** 765／768／769／770の Login Success（strict bool なし）。 */
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

    /** 768／769／770の Client Information（設定フェーズ。particleStatus=0 付き9フィールド）。 */
    private static byte[] clientInformation770Payload() {
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

    /** 766／767／768／769／770の Registry Data 1件（dimension_type、エントリ配列形式）。 */
    private static byte[] registryData766Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:dimension_type");
        writer.writeVarInt(1);
        writer.writeString("minecraft:overworld");
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        return writer.bytes();
    }

    /** 768／769／770の Join Game（SpawnInfo の末尾に seaLevel=63）。 */
    private static byte[] joinGame770Payload() {
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

    /** 768／769／770の Respawn（SpawnInfo の末尾に seaLevel=63、copyMetadata は末尾のバイト）。 */
    private static byte[] respawn770Payload() {
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

    /** 766／767／768／769／770の Add Resource Pack（UUID付き、プロンプトなし）。 */
    private static byte[] addResourcePackPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        writer.writeString("https://127.0.0.1:25567/detergentmc-pack-1.21.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 769／770のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes770() {
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

    /** 766／767／768／769／770形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767／768／769／770形式の Registry Data パケットを読み、名前列を返す。 */
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

    /** Set Slot ペイロード（768／769／770：windowId は varint）。 */
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

    /** Set Slot ペイロード（768／769／770形式）のアイテムIDを読む。 */
    private static int readSetSlotItemId768(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // windowId（varint）
        reader.readVarInt(); // stateId
        reader.readShort(); // slotIndex
        reader.readByte(); // count
        return reader.readVarInt();
    }

    /** Set Creative Slot ペイロード（769形式。Slot）。 */
    private static byte[] setCreativeSlot769Packet(int itemId) {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        return writer.bytes();
    }

    /** Set Creative Slot ペイロード（770形式。UntrustedSlot）。 */
    private static byte[] setCreativeSlot770Packet(int itemId) {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        return writer.bytes();
    }

    /** Set Creative Slot ペイロード（769／770共通）のアイテムIDを読む。 */
    private static int readSetCreativeSlotItemId(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readShort(); // slotId
        reader.readVarInt(); // count
        return reader.readVarInt();
    }

    /** Window Click ペイロード（770形式。HashedSlot は空）。 */
    private static byte[] windowClick770Packet() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(1); // stateId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(0); // changed 0件
        writer.writeVarInt(0); // cursorItem 無し
        return writer.bytes();
    }

    /** Window Click ペイロード（769形式）。 */
    private static byte[] windowClick769Packet() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(1); // stateId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(0); // changed 0件
        writer.writeByte(0); // carried（空き）
        return writer.bytes();
    }

    /** Player Chat ペイロード（769形式。先頭に globalIndex は無い）。 */
    private static byte[] playerChat769Payload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("hello");
        writer.writeLong(123456789L);
        return writer.bytes();
    }

    /** Chat Message ペイロード（769形式。末尾に checksum は無い）。 */
    private static byte[] chatMessage769Payload() {
        Writer writer = new Writer();
        writer.writeString("hello");
        writer.writeLong(123456789L);
        writer.writeVarInt(0); // salt
        writer.writeVarInt(0); // signature 長さ0
        writer.writeVarInt(0); // offset
        writer.writeVarInt(0); // acknowledged
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
     * （766／767／768／769／770は 0x03、765／764は 0x02）。 */
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
    // 770クライアント → 内部770（素通し）
    // ------------------------------------------------------------------

    @Test
    void join770Against770IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                770, 2, false, ProxyAuthConfig.defaults(), 770);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(770, session.clientProtocol());
        assertEquals(770, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        // 770の Login Success は strict bool が無い（765と同じ形式）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // プレイ状態は素通し（Set Slot の blue_egg は 970 のまま）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(970)));
        assertEquals(1, slot.toClient().size());
        assertEquals(970, readSetSlotItemId768(slot.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 770クライアント → 内部769（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join770Against769TranslatesSlots() {
        ProxySession session = loginTo(770, 769);
        assertEquals(770, session.clientProtocol());
        assertEquals(769, session.serverProtocol());
        // Login Success（769形式）→ 770にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（770：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（770：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation770Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation770Payload(), info.toServer().get(0).payload());

        // Registry Data（769：0x07、バイオーム65件）→ 770（0x07、65件。
        // バイオームは769→770で変わらないためそのまま）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes770())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(65, names.size());
        assertEquals(39, names.indexOf("pale_garden"));
        assertEquals(40, names.indexOf("plains"));

        // Finish Configuration（769：0x03）→ 770（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（770：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（769：0x2C）→ 770（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame770Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame770Payload(), join.toClient().get(0).payload());

        // Spawn Position（769：0x5B）→ 770（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5B, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Keep Alive（769：0x27）→ 770（0x26）。Ping（769：0x37）→ 770（0x36）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x27, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x37, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x36, ping.toClient().get(0).id());

        // Respawn（769：0x4C）→ 770（0x4B、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn770Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4B, respawn.toClient().get(0).id());
        assertArrayEquals(respawn770Payload(), respawn.toClient().get(0).payload());

        // Set Slot（769：0x15、oak_boat 803）→ 770（0x14、810）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(803)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(810, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（769：0x3B）→ 770（0x3A、先頭に globalIndex 0 を付ける）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3B, playerChat769Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        Reader chatReader = new Reader(chat.toClient().get(0).payload());
        assertEquals(0, chatReader.readVarInt()); // globalIndex（最初の1件）
        assertArrayEquals(playerChat769Payload(), chatReader.rest());

        // Set Creative Slot（770：0x36、UntrustedSlot、oak_boat 810）
        // → 769（0x36、Slot、oak_boat 803）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x36, setCreativeSlot770Packet(810)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x36, creative.toServer().get(0).id());
        assertEquals(803, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（770：0x10）は HashedSlot のため落とす（バニラへ送らない）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick770Packet()));
        assertTrue(click.toServer().isEmpty());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部770（上向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join769Against770TranslatesSlots() {
        ProxySession session = loginTo(769, 770);
        assertEquals(769, session.clientProtocol());
        assertEquals(770, session.serverProtocol());
        // Login Success（770形式）→ 769にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // Login Acknowledged（769：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());

        // Client Information（769：0x00、9フィールド）はそのままバニラへ。
        // 770も同じ9フィールドのため、particleStatus を落としてはいけない。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation770Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation770Payload(), info.toServer().get(0).payload());

        // Registry Data（770：0x07、バイオーム65件）→ 769（0x07、65件）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes770())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());

        // Finish Configuration（770：0x03）→ 769（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（769：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（770：0x2B）→ 769（0x2C、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame770Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());
        assertArrayEquals(joinGame770Payload(), join.toClient().get(0).payload());

        // Spawn Position（770：0x5A）→ 769（0x5B）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5B, spawn.toClient().get(0).id());

        // Set Slot（770：0x14、blue_egg 970）→ 769（0x15、stone 1 に落とす）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(970)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(1, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（770：0x3A、globalIndex 3 付き）→ 769（0x3B、剥がす）。
        Writer chatWriter = new Writer();
        chatWriter.writeVarInt(3); // globalIndex
        chatWriter.writeBytes(playerChat769Payload());
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, chatWriter.bytes()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3B, chat.toClient().get(0).id());
        assertArrayEquals(playerChat769Payload(), chat.toClient().get(0).payload());

        // Chat Message（769：0x07）→ 770（0x07、末尾に checksum 0x00 を付ける。
        // 値が 0x00 で受け入れられるかは本物のバニラでの未検証）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x07, chatMessage769Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x07, chatMsg.toServer().get(0).id());
        byte[] chatPayload = chatMsg.toServer().get(0).payload();
        assertEquals(chatMessage769Payload().length + 1, chatPayload.length);
        assertEquals(0, chatPayload[chatPayload.length - 1]);
        assertArrayEquals(chatMessage769Payload(),
                java.util.Arrays.copyOf(chatPayload, chatPayload.length - 1));

        // Set Creative Slot（769：0x36、Slot、oak_boat 803）
        // → 770（0x36、UntrustedSlot、oak_boat 810）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x36, setCreativeSlot769Packet(803)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x36, creative.toServer().get(0).id());
        assertEquals(810, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（769：0x10）は770の HashedSlot にならないため落とす。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick769Packet()));
        assertTrue(click.toServer().isEmpty());
    }

    // ------------------------------------------------------------------
    // 770クライアント → 内部763（設定フェーズを合成）
    // ------------------------------------------------------------------

    @Test
    void join770Against763SynthesizesConfig() {
        ProxySession.HandshakeResult result = handshake(
                770, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(770, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は763形式（bool 付き）に直してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PLAYER_UUID, loginReader.readUuid());

        // Login Success（763形式）→ 770にはそのまま送る（strict bool は無い）。
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

        // Login Acknowledged（770：0x03）で合成した設定フェーズを送る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertTrue(ack.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(3, ack.toClient().size());
        assertEquals(0x07, ack.toClient().get(0).id());
        assertEquals(0x07, ack.toClient().get(1).id());
        assertEquals(0x03, ack.toClient().get(2).id());

        // Finish Configuration の応答（770：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x2B, finishAck.toClient().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Reader joinReader = new Reader(finishAck.toClient().get(0).payload());
        assertEquals(42, joinReader.readInt()); // entityId
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部770（設定フェーズを770クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join763Against770ConsumesConfigAs770Client() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 770);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(770, session.serverProtocol());

        // Login Start は763形式から770形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（770形式）→ 763にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        // 763クライアント向けにプロキシが合成する Client Information は
        // 770形式（9フィールド。particleStatus=0 付き）。8フィールドのまま
        // 送ると770のバニラが読み取り途中で切断する。
        byte[] synthInfo = success.toServer().get(1).payload();
        assertEquals(PlayTranslator.clientInformationPayload().length + 1, synthInfo.length);
        assertEquals(0, synthInfo[synthInfo.length - 1]);
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（770：0x07）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x07, registryData766Payload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Add Resource Pack（770：0x09）は保持する。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x09, addResourcePackPayload()));
        assertTrue(pack.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());

        // Finish Configuration（770：0x03）→ 応答（0x03）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（770：0x2B）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame770Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    // ------------------------------------------------------------------
    // Client Information の particleStatus
    // ------------------------------------------------------------------

    @Test
    void clientInformation770KeepsParticleStatusToward769Server() {
        ProxySession session = loginTo(770, 769);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        // Client Information（770：0x00、9フィールド）は769へそのまま送る。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation770Payload()));
        assertEquals(1, info.toServer().size());
        assertArrayEquals(clientInformation770Payload(), info.toServer().get(0).payload());
    }

    @Test
    void clientInformationLosesParticleStatusToward767Server() {
        // 770クライアント → 内部767：設定フェーズとプレイ中の Settings から
        // particleStatus を落として送る。
        ProxySession session = loginTo(770, 767);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // Client Information（770：0x00、9フィールド）は767へ送るとき
        // particleStatus（末尾の varint）を落として8フィールドにする。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation770Payload()));
        assertEquals(1, info.toServer().size());
        assertArrayEquals(clientInformation766Payload(), info.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 設定フェーズのレジストリ並べ替え
    // ------------------------------------------------------------------

    @Test
    void registryData770To769KeepsBiomesInSession() {
        ProxySession session = loginTo(769, 770);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes770())));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(65, names.size());
        assertEquals(39, names.indexOf("pale_garden"));
        assertEquals(40, names.indexOf("plains"));
    }

    @Test
    void registryData770To768DropsPaleGardenInSession() {
        ProxySession session = loginTo(768, 770);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes770())));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(64, names.size());
        assertEquals(39, names.indexOf("plains"));
        assertFalse(names.contains("pale_garden"));
    }

    // ------------------------------------------------------------------
    // 770クライアント → 内部765（Join Game 相当だけを中継）
    // ------------------------------------------------------------------

    @Test
    void join770Against765RelaysJoinLevel() {
        ProxySession session = loginTo(770, 765);
        assertEquals(770, session.clientProtocol());
        assertEquals(765, session.serverProtocol());
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // 765サーバーは設定フェーズを持たないため、プロキシは
        // Join Game（765：0x29）を受け取るまでプレイ状態に入らない。
        finishConfig(session, 770, 765, registryDataPayload(), 0x05, 0x02, 0x03);
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Join Game（765：0x29）→ 770（0x2B）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());

        // Spawn Position（765：0x54）→ 770（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Keep Alive（765：0x24）→ 770（0x26）。Ping（765：0x33）→ 770（0x36）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x36, ping.toClient().get(0).id());

        // Respawn（765：0x45）→ 770（0x4B、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4B, respawn.toClient().get(0).id());
        assertArrayEquals(respawn770Payload(), respawn.toClient().get(0).payload());

        // プレイ状態のその他パケットは770クライアントには落とす（join レベルのみ）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(799)));
        assertTrue(slot.toClient().isEmpty());
    }
}
