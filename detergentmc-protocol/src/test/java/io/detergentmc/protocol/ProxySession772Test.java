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
 * 772（1.21.7／1.21.8）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession771Test} と同じ段取りで、772の番号・形式で確かめる。
 * 772のパケットIDは771と変わらない（247件すべて同一）。ペイロードでは
 * Player Chat の globalIndex、チャットの checksum（771・772ともあるため
 * 剥がさない）、Set Creative Slot の UntrustedSlot、Window Click の破棄、
 * ロケーターバー／ダイアログ／Change Gamemode／Custom Click Action の中継
 * （771にもあるため）、ジュークボックス lava_chicken・絵画 dennis・
 * アイテム music_disc_lava_chicken の付け替えを確かめる。
 */
class ProxySession772Test {

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

    /** 766／767／768／769／771／772の Login Start：名前＋UUID必須。 */
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

    /** 765／768／769／771／772の Login Success（strict bool なし）。 */
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

    /** 768／769／771／772の Client Information（設定フェーズ。particleStatus=0 付き9フィールド）。 */
    private static byte[] clientInformation772Payload() {
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

    /** 766／767／768／769／771／772の Registry Data 1件（dimension_type、エントリ配列形式）。 */
    private static byte[] registryData766Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:dimension_type");
        writer.writeVarInt(1);
        writer.writeString("minecraft:overworld");
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        return writer.bytes();
    }

    /** 768／769／771／772の Join Game（SpawnInfo の末尾に seaLevel=63）。 */
    private static byte[] joinGame772Payload() {
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

    /** 766／767の Join Game（SpawnInfo の末尾に seaLevel は無い）。 */
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
        writer.writeVarInt(0); // portalCooldown
        writer.writeBoolean(false); // enforcesSecureChat
        return writer.bytes();
    }

    /** 765の Join Game（dimension は文字列、dimension 番号は無い）。 */
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

    /**
     * 設定フェーズの合成が要るサーバー（763／765）との参加を、設定フェーズの
     * 終了まで進める。{@link ProxySession771Test} と同じ段取り。
     */
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

    /** 768／769／771／772の Respawn（SpawnInfo の末尾に seaLevel=63、copyMetadata は末尾のバイト）。 */
    private static byte[] respawn772Payload() {
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

    /** 766／767／768／769／771／772の Add Resource Pack（UUID付き、プロンプトなし）。 */
    private static byte[] addResourcePackPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        writer.writeString("https://127.0.0.1:25567/detergentmc-pack-1.21.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 769／771／772のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes772() {
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

    /** 771のジュークボックスの歌20件（tears を17番に挿入）。 */
    private static List<String> jukeboxSongs771() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "mall", "mellohi", "otherside", "pigstep",
                "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /** 772のジュークボックスの歌21件（lava_chicken を9番に挿入）。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
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

    /** 766／767／768／769／771／772形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767／768／769／771／772形式の Registry Data パケットを読み、名前列を返す。 */
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

    /** Set Slot ペイロード（768／769／771／772：windowId は varint）。 */
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

    /** Set Slot ペイロード（768／769／771／772形式）のアイテムIDを読む。 */
    private static int readSetSlotItemId768(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // windowId（varint）
        reader.readVarInt(); // stateId
        reader.readShort(); // slotIndex
        reader.readByte(); // count
        return reader.readVarInt();
    }

    /** Set Creative Slot ペイロード（771／772形式。UntrustedSlot）。 */
    private static byte[] setCreativeSlot772Packet(int itemId) {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        return writer.bytes();
    }

    /** Set Creative Slot ペイロード（771／772共通）のアイテムIDを読む。 */
    private static int readSetCreativeSlotItemId(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readShort(); // slotId
        reader.readVarInt(); // count
        return reader.readVarInt();
    }

    /** Window Click ペイロード（771／772形式。HashedSlot は空）。 */
    private static byte[] windowClick772Packet() {
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

    /** Player Chat ペイロード（771／772形式。先頭に globalIndex 付き）。 */
    private static byte[] playerChat772Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(3); // globalIndex
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("hello");
        writer.writeLong(123456789L);
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

    /** Chat Message ペイロード（771／772形式。末尾に checksum 0x00 付き）。 */
    private static byte[] chatMessage771Payload() {
        Writer writer = new Writer();
        writer.writeString("hello");
        writer.writeLong(123456789L);
        writer.writeLong(0L); // salt
        writer.writeBoolean(false); // 署名なし
        writer.writeVarInt(0); // offset
        writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
        writer.writeByte(0); // checksum
        return writer.bytes();
    }

    /** Spawn Entity ペイロード（entityId＋UUID＋種別＋座標＋向き＋objectData＋速度）。 */
    private static byte[] spawnEntityPacket(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(0); // pitch
        writer.writeByte(0); // yaw
        writer.writeByte(0); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(0); // velocityX
        writer.writeShort(0); // velocityY
        writer.writeShort(0); // velocityZ
        return writer.bytes();
    }

    /** Spawn Entity ペイロードの種別を読む。 */
    private static int readSpawnEntityType(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // entityId
        reader.readBytes(16); // uuid
        return reader.readVarInt();
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

    /** Client Information ペイロードの末尾に particleStatus を付ける。 */
    private static byte[] appendParticleStatus(byte[] base, int particleStatus) {
        Writer writer = new Writer();
        writer.writeBytes(base);
        writer.writeVarInt(particleStatus);
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // 772クライアント → 内部772（素通し）
    // ------------------------------------------------------------------

    @Test
    void join772Against772IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                772, 2, false, ProxyAuthConfig.defaults(), 772);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(772, session.clientProtocol());
        assertEquals(772, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // プレイ状態は素通し（Set Slot のアイテム 619 は 619 のまま）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(619)));
        assertEquals(1, slot.toClient().size());
        assertEquals(619, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // 771・772両方にあるパケットも素通し（Tracked Waypoint 0x83）。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x83, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x83, waypoint.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // 772クライアント → 内部771（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join772Against771TranslatesSlots() {
        ProxySession session = loginTo(772, 771);
        assertEquals(772, session.clientProtocol());
        assertEquals(771, session.serverProtocol());
        // Login Success（771形式）→ 772にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（772：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（772：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation772Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation772Payload(), info.toServer().get(0).payload());

        // Registry Data（771：0x07、ジュークボックス20件）→ 772（0x07、21件。
        // lava_chicken を9番に挿入）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs771())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(21, names.size());
        assertEquals(9, names.indexOf("lava_chicken"));
        assertEquals(10, names.indexOf("mall"));
        assertEquals(18, names.indexOf("tears"));
        assertEquals(19, names.indexOf("wait"));
        assertEquals(20, names.indexOf("ward"));

        // Finish Configuration（771：0x03）→ 772（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（772：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（771：0x2B）→ 772（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame772Payload(), join.toClient().get(0).payload());

        // Spawn Position（771：0x5A）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Keep Alive（771：0x26）→ 772（0x26）。Ping（771：0x36）→ 772（0x36）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x36, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x36, ping.toClient().get(0).id());

        // Respawn（771：0x4B）→ 772（0x4B、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn772Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4B, respawn.toClient().get(0).id());
        assertArrayEquals(respawn772Payload(), respawn.toClient().get(0).payload());

        // Set Slot（771：0x14、アイテム 1256）→ 772（0x14、1257。
        // music_disc_lava_chicken は772で増えたため +1 ずれる）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1256)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1257, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（771：0x3A、globalIndex 3 付き）→ 772（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat772Payload(), chat.toClient().get(0).payload());

        // Chat Message（772：0x08）→ 771（0x08、checksum は両方にあるため剥がさない）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage771Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x08, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage771Payload(), chatMsg.toServer().get(0).payload());

        // Set Creative Slot（772：0x37、UntrustedSlot、アイテム 1257）
        // → 771（0x37、アイテム 1256）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot772Packet(1257)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1256, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（772：0x11）は HashedSlot のため落とす（バニラへ送らない）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x11, windowClick772Packet()));
        assertTrue(click.toServer().isEmpty());

        // Change Gamemode（772：0x04）は771にも同ID・同形式があるためそのまま通す。
        Outcome gamemode = session.handleClientPacket(
                new MinecraftPacket(0x04, new byte[]{0x01}));
        assertEquals(1, gamemode.toServer().size());
        assertEquals(0x04, gamemode.toServer().get(0).id());
        assertArrayEquals(new byte[]{0x01}, gamemode.toServer().get(0).payload());

        // Custom Click Action（772：0x41）は771にも同ID・同形式があるためそのまま通す。
        Writer clickAction = new Writer();
        clickAction.writeString("minecraft:test");
        clickAction.writeByte(0);
        Outcome customClick = session.handleClientPacket(
                new MinecraftPacket(0x41, clickAction.bytes()));
        assertEquals(1, customClick.toServer().size());
        assertEquals(0x41, customClick.toServer().get(0).id());
        assertArrayEquals(clickAction.bytes(), customClick.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 771クライアント → 内部772（上向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join771Against772TranslatesSlots() {
        ProxySession session = loginTo(771, 772);
        assertEquals(771, session.clientProtocol());
        assertEquals(772, session.serverProtocol());
        // Login Success（772形式）→ 771にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // Login Acknowledged（771：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());

        // Client Information（771：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation772Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation772Payload(), info.toServer().get(0).payload());

        // Registry Data（772：0x07、ジュークボックス21件）→ 771（0x07、20件。
        // lava_chicken を落とす）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(20, names.size());
        assertEquals(9, names.indexOf("mall"));
        assertEquals(17, names.indexOf("tears"));
        assertEquals(18, names.indexOf("wait"));
        assertEquals(19, names.indexOf("ward"));
        assertFalse(names.contains("lava_chicken"));

        // minecraft:dialog（772：0x07）は771にもあるためそのまま通す。
        Outcome dialog = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:dialog",
                        List.of("custom_options", "quick_actions", "server_links"))));
        assertEquals(1, dialog.toClient().size());
        assertEquals(0x07, dialog.toClient().get(0).id());

        // Finish Configuration（772：0x03）→ 771（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（771：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（772：0x2B）→ 771（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame772Payload(), join.toClient().get(0).payload());

        // Spawn Position（772：0x5A）→ 771（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Set Slot（772：0x14、アイテム 1256）→ 771（0x14、石 1。
        // music_disc_lava_chicken は771に無いため、772→771の付け替えは石になる）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1256)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（772：0x3A、globalIndex 3 付き）→ 771（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat772Payload(), chat.toClient().get(0).payload());

        // Chat Message（771：0x08）→ 772（0x08、checksum はそのまま）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage771Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x08, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage771Payload(), chatMsg.toServer().get(0).payload());

        // Set Creative Slot（771：0x37、UntrustedSlot、アイテム 1256）
        // → 772（0x37、アイテム 1257）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot772Packet(1256)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1257, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（771：0x11）は HashedSlot のため落とす。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x11, windowClick772Packet()));
        assertTrue(click.toServer().isEmpty());

        // Tracked Waypoint（772：0x83）は771にも同ID・同形式があるためそのまま通す。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x83, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x83, waypoint.toClient().get(0).id());
        assertArrayEquals(new byte[]{0x00}, waypoint.toClient().get(0).payload());

        // Show Dialog（772：0x85）／Clear Dialog（772：0x84）は
        // 771にも同ID・同形式があるためそのまま通す。
        Outcome showDialog = session.handleServerPacket(
                new MinecraftPacket(0x85, new byte[]{0x00}));
        assertEquals(1, showDialog.toClient().size());
        assertEquals(0x85, showDialog.toClient().get(0).id());
        Outcome clearDialog = session.handleServerPacket(
                new MinecraftPacket(0x84, new byte[0]));
        assertEquals(1, clearDialog.toClient().size());
        assertEquals(0x84, clearDialog.toClient().get(0).id());

        // Spawn Entity（772：0x01、ハッピーガスト 56）→ 771（56、同型）。
        Outcome spawnEntity = session.handleServerPacket(
                new MinecraftPacket(0x01, spawnEntityPacket(56, 0)));
        assertEquals(1, spawnEntity.toClient().size());
        assertEquals(0x01, spawnEntity.toClient().get(0).id());
        assertEquals(56, readSpawnEntityType(spawnEntity.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 772クライアント → 内部766（Join Game の変換）
    // ------------------------------------------------------------------

    @Test
    void join772Against766TranslatesJoinGame() {
        ProxySession session = loginTo(772, 766);
        assertEquals(772, session.clientProtocol());
        assertEquals(766, session.serverProtocol());
        // Login Success（766形式）→ 772にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());

        // Login Acknowledged（772：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（772：0x00、9フィールド）は766へ送るとき
        // particleStatus（末尾の varint）を落として8フィールドにする。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation772Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation766Payload(), info.toServer().get(0).payload());

        // Registry Data（766：0x07、dimension_type）→ 772（0x07）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryData766Payload()));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());

        // Finish Configuration（766：0x03）→ 772（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（772：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（766：0x2B、seaLevel 無し）→ 772（0x2B、seaLevel=63 を補う）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame767To768(joinGame766Payload()),
                join.toClient().get(0).payload());

        // Spawn Position（766：0x56）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x56, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Keep Alive（766：0x26）→ 772（0x26）。Ping（766：0x35）→ 772（0x36）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x35, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x36, ping.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // 772クライアント → 内部765（Join Game 相当だけを中継）
    // ------------------------------------------------------------------

    @Test
    void join772Against765RelaysJoinLevel() {
        ProxySession session = loginTo(772, 765);
        assertEquals(772, session.clientProtocol());
        assertEquals(765, session.serverProtocol());
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // 765サーバーは設定フェーズを持たないため、プロキシは
        // Join Game（765：0x29）を受け取るまでプレイ状態に入らない。
        finishConfig(session, 772, 765, registryDataPayload(), 0x05, 0x02, 0x03);
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Join Game（765：0x29）→ 772（0x2B）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());

        // Spawn Position（765：0x54）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Keep Alive（765：0x24）→ 772（0x26）。Ping（765：0x33）→ 772（0x36）。
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

        // Respawn（765：0x45）→ 772（0x4B、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4B, respawn.toClient().get(0).id());
        assertArrayEquals(respawn772Payload(), respawn.toClient().get(0).payload());

        // プレイ状態のその他パケットは772クライアントには落とす（join レベルのみ）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(799)));
        assertTrue(slot.toClient().isEmpty());

        // Add Resource Pack（765：0x44）→ 772（0x4A）。
        Writer pack = new Writer();
        pack.writeUuid(PLAYER_UUID);
        pack.writeString("https://example.invalid/pack.zip");
        pack.writeString("0123456789abcdef0123456789abcdef01234567");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome addPack = session.handleServerPacket(
                new MinecraftPacket(0x44, pack.bytes()));
        assertEquals(1, addPack.toClient().size());
        assertEquals(0x4A, addPack.toClient().get(0).id());

        // Disconnect（765：0x1B）→ 772（0x1C）。セッションを閉じるため最後に送る。
        Outcome disconnect = session.handleServerPacket(
                new MinecraftPacket(0x1B, new byte[]{0x00}));
        assertEquals(1, disconnect.toClient().size());
        assertEquals(0x1C, disconnect.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部772（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join769Against772TranslatesSlots() {
        ProxySession session = loginTo(769, 772);
        assertEquals(769, session.clientProtocol());
        assertEquals(772, session.serverProtocol());
        // Login Success（772形式）→ 769にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // Login Acknowledged（769：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());

        // Client Information（769：0x00、9フィールド）は772へそのまま送る。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation772Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation772Payload(), info.toServer().get(0).payload());

        // Registry Data（772：0x07、ジュークボックス21件）→ 769（0x07、19件。
        // lava_chicken と tears を落とす）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(19, names.size());
        assertEquals(17, names.indexOf("wait"));
        assertEquals(18, names.indexOf("ward"));
        assertFalse(names.contains("tears"));
        assertFalse(names.contains("lava_chicken"));

        // Finish Configuration（772：0x03）→ 769（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（769：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（772：0x2B）→ 769（0x2C、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());
        assertArrayEquals(joinGame772Payload(), join.toClient().get(0).payload());

        // Respawn（772：0x4B）→ 769（0x4C、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn772Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn772Payload(), respawn.toClient().get(0).payload());

        // Set Slot（772：0x14、アイテム 620）→ 769（0x15、612。772の 620 は
        // 771でも 620 で、771の 620 は 769では 612）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(620)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(612, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（772：0x3A、globalIndex 3 付き）→ 769（0x3B、剥がす）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3B, chat.toClient().get(0).id());
        assertArrayEquals(playerChat769Payload(), chat.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 772クライアント → 内部763（設定フェーズを合成）
    // ------------------------------------------------------------------

    @Test
    void join772Against763SynthesizesConfig() {
        ProxySession.HandshakeResult result = handshake(
                772, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(772, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は763形式（bool 付き）に直してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PLAYER_UUID, loginReader.readUuid());

        // Login Success（763形式）→ 772にはそのまま送る（strict bool は無い）。
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

        // Login Acknowledged（772：0x03）で合成した設定フェーズを送る。
        // minecraft:dialog は合成しないため、送るのは dimension_type・
        // biome の Registry Data 2件＋Finish Configuration の3件。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertTrue(ack.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(3, ack.toClient().size());
        assertEquals(0x07, ack.toClient().get(0).id());
        assertEquals(0x07, ack.toClient().get(1).id());
        assertEquals(0x03, ack.toClient().get(2).id());

        // Finish Configuration の応答（772：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x2B, finishAck.toClient().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Reader joinReader = new Reader(finishAck.toClient().get(0).payload());
        assertEquals(42, joinReader.readInt()); // entityId
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部772（設定フェーズを772クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join763Against772ConsumesConfigAs772Client() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 772);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(772, session.serverProtocol());

        // Login Start は763形式から772形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（772形式）→ 763にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        // 763クライアント向けにプロキシが合成する Client Information は
        // 772形式（9フィールド。particleStatus=0 付き）。8フィールドのまま
        // 送ると772のバニラが読み取り途中で切断する。
        byte[] synthInfo = success.toServer().get(1).payload();
        assertEquals(PlayTranslator.clientInformationPayload().length + 1, synthInfo.length);
        assertEquals(0, synthInfo[synthInfo.length - 1]);
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（772：0x07）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x07, registryData766Payload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Add Resource Pack（772：0x09）は保持する。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x09, addResourcePackPayload()));
        assertTrue(pack.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());

        // Finish Configuration（772：0x03）→ 応答（0x03）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（772：0x2B）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    // ------------------------------------------------------------------
    // 設定フェーズの Custom Click Action（772・771）／ダイアログ（772）
    // ------------------------------------------------------------------

    @Test
    void configCustomClickAction772RelayedTo771Server() {
        ProxySession session = loginTo(772, 771);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // Custom Click Action（設定 0x08）は771・772とも同ID・同形式のためそのまま通す。
        Writer clickAction = new Writer();
        clickAction.writeString("minecraft:test");
        clickAction.writeByte(0);
        Outcome out = session.handleClientPacket(
                new MinecraftPacket(0x08, clickAction.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x08, out.toServer().get(0).id());
        assertArrayEquals(clickAction.bytes(), out.toServer().get(0).payload());
    }

    @Test
    void configCustomClickAction772RelayedNativelyTo772Server() {
        // ネイティブ772同士は Login Success の後にプレイ状態へ入る。
        // ネイティブのセッションは再設定を追跡しない（771のネイティブ同士と同じ）：
        // サーバーからの Start Configuration（0x6F）はそのまま中継し、
        // セッションはプレイ状態のまま、isReconfiguring() も false のままである。
        // その後のクライアントの設定フェーズのパケットも、プレイ状態の番号で
        // そのまま中継される。Custom Click Action（設定 0x08）はプレイ状態の
        // Chat Message（0x08）と番号が同じため、バイト列としてはそのまま
        // サーバーへ届く（サーバーは設定状態で読むため正しく解釈する）。
        ProxySession session = loginTo(772, 772);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome start = session.handleServerPacket(
                new MinecraftPacket(0x6F, new byte[0]));
        assertEquals(1, start.toClient().size());
        assertEquals(0x6F, start.toClient().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertFalse(session.isReconfiguring());
        // クライアントの Configuration Acknowledged（プレイ 0x0F）はそのまま送る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x0F, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x0F, ack.toServer().get(0).id());
        // Custom Click Action（設定 0x08）はバイト列のままサーバーへ届く。
        Writer clickAction = new Writer();
        clickAction.writeString("minecraft:test");
        clickAction.writeByte(0);
        Outcome out = session.handleClientPacket(
                new MinecraftPacket(0x08, clickAction.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x08, out.toServer().get(0).id());
        assertArrayEquals(clickAction.bytes(), out.toServer().get(0).payload());
    }

    @Test
    void configCustomClickAction771RelayedTo772Server() {
        ProxySession session = loginTo(771, 772);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // Custom Click Action（設定 0x08）は771・772とも同ID・同形式の
        // ためそのまま通す。770までの Custom Report Details（0x08）／
        // Server Links（0x09）は771・772には無いが、771クライアントは
        // もともと送らないため、771→772で落とす設定パケットは無い。
        Writer clickAction = new Writer();
        clickAction.writeString("minecraft:test");
        clickAction.writeByte(0);
        Outcome out = session.handleClientPacket(
                new MinecraftPacket(0x08, clickAction.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x08, out.toServer().get(0).id());
        assertArrayEquals(clickAction.bytes(), out.toServer().get(0).payload());
    }

    @Test
    void configDialog772RelayedTo771Server() {
        ProxySession session = loginTo(772, 771);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // Show Dialog（設定 0x12）／Clear Dialog（設定 0x11）は771・772とも
        // 同ID・同形式のためそのまま通す。
        Outcome show = session.handleServerPacket(
                new MinecraftPacket(0x12, new byte[]{0x00}));
        assertEquals(1, show.toClient().size());
        assertEquals(0x12, show.toClient().get(0).id());
        assertArrayEquals(new byte[]{0x00}, show.toClient().get(0).payload());
        Outcome clear = session.handleServerPacket(
                new MinecraftPacket(0x11, new byte[0]));
        assertEquals(1, clear.toClient().size());
        assertEquals(0x11, clear.toClient().get(0).id());
    }

    @Test
    void clientInformation768KeptFor772Server() {
        // 768クライアント→772サーバーの設定フェーズ：Client Information は
        // 768・772とも9フィールド（末尾の particleStatus 付き）のため、
        // particleStatus を落とさずそのまま通す（771段階の漏れ修正 8fa47ab の
        // 772版。落とすと772サーバーが8フィールドで切断する）。
        ProxySession session = loginTo(768, 772);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation772Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation772Payload(), info.toServer().get(0).payload());
        // Finish Configuration（768：0x03）はそのまま送られ、プレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
    }

    // ------------------------------------------------------------------
    // 設定フェーズのレジストリ並べ替え（772）
    // ------------------------------------------------------------------

    @Test
    void registryData772To771KeepsBiomesInSession() {
        ProxySession session = loginTo(771, 772);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes772())));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(65, names.size());
        assertEquals(39, names.indexOf("pale_garden"));
        assertEquals(40, names.indexOf("plains"));
    }
}
