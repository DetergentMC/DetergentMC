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
 * 773（1.21.9／1.21.10）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession772Test} と同じ段取りで、773の番号・形式で確かめる。
 * 773のパケットIDは772から変わる（247件のうち108件が変わる。デバッグ系の
 * 挿入で0x1A以降が+4、0x27以降が+5。Join Game 0x2B→0x30、Chunk Data
 * 0x27→0x2C、Keep Alive 0x26→0x2B、Ping 0x36→0x3B）。ペイロードでは
 * Player Chat の globalIndex、チャットの checksum（772・773ともあるため
 * 剥がさない）、Set Creative Slot の UntrustedSlot、Window Click の破棄、
 * ロケーターバー／ダイアログ／Change Gamemode／Custom Click Action の中継
 * （772にもあるため）、棚の挿入（bookshelf 305→317）・銅ゴーレム／マネキン
 * （Spawn Entity はブタへ）・銅のチェストの音（block.stone.break で代替）の
 * 付け替えを確かめる。ジュークボックスの歌・絵画は772・773とも同一のため
 * 素通しする。
 */
class ProxySession773Test {

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

    /** 766／767／768／769／772／773の Login Start：名前＋UUID必須。 */
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

    /** 765／768／769／772／773の Login Success（strict bool なし）。 */
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

    /** 768／769／772／773の Client Information（設定フェーズ。particleStatus=0 付き9フィールド）。 */
    private static byte[] clientInformation773Payload() {
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

    /** 766／767／768／769／772／773の Registry Data 1件（dimension_type、エントリ配列形式）。 */
    private static byte[] registryData766Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:dimension_type");
        writer.writeVarInt(1);
        writer.writeString("minecraft:overworld");
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        return writer.bytes();
    }

    /** 768／769／772／773の Join Game（SpawnInfo の末尾に seaLevel=63）。 */
    private static byte[] joinGame773Payload() {
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
     * 終了まで進める。{@link ProxySession772Test} と同じ段取り。
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

    /** 768／769／772／773の Respawn（SpawnInfo の末尾に seaLevel=63、copyMetadata は末尾のバイト）。 */
    private static byte[] respawn773Payload() {
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

    /** 766／767／768／769／772／773の Add Resource Pack（UUID付き、プロンプトなし）。 */
    private static byte[] addResourcePackPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        writer.writeString("https://127.0.0.1:25567/detergentmc-pack-1.21.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 769／772／773のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes773() {
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

    /** 772のジュークボックスの歌21件（実バニラ1.21.7／1.21.8の並び。lava_chicken は9番）。773も同じ21件。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /** 773のジュークボックスの歌21件（lava_chicken を9番に挿入）。 */
    private static List<String> jukeboxSongs773() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /** 770のジュークボックスの歌19件（tears は771、lava_chicken は772で追加）。 */
    private static List<String> jukeboxSongs770() {
        return jukeboxSongs772().stream()
                .filter(n -> !n.equals("lava_chicken") && !n.equals("tears"))
                .toList();
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

    /** 766／767／768／769／772／773形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767／768／769／772／773形式の Registry Data パケットを読み、名前列を返す。 */
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

    /** Set Slot ペイロード（768／769／772／773：windowId は varint）。 */
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

    /** Set Slot ペイロード（768／769／772／773形式）のアイテムIDを読む。 */
    private static int readSetSlotItemId768(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // windowId（varint）
        reader.readVarInt(); // stateId
        reader.readShort(); // slotIndex
        reader.readByte(); // count
        return reader.readVarInt();
    }

    /** Set Creative Slot ペイロード（772／773形式。UntrustedSlot）。 */
    private static byte[] setCreativeSlot773Packet(int itemId) {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        return writer.bytes();
    }

    /** Set Creative Slot ペイロード（772／773共通）のアイテムIDを読む。 */
    private static int readSetCreativeSlotItemId(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readShort(); // slotId
        reader.readVarInt(); // count
        return reader.readVarInt();
    }

    /** Window Click ペイロード（772／773形式。HashedSlot は空）。 */
    private static byte[] windowClick773Packet() {
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

    /** Player Chat ペイロード（772／773形式。先頭に globalIndex 付き）。 */
    private static byte[] playerChat773Payload() {
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

    /** Chat Message ペイロード（772／773形式。末尾に checksum 0x00 付き）。 */
    private static byte[] chatMessage772Payload() {
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
    // 773クライアント → 内部773（素通し）
    // ------------------------------------------------------------------

    @Test
    void join773Against773IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                773, 2, false, ProxyAuthConfig.defaults(), 773);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(773, session.clientProtocol());
        assertEquals(773, session.serverProtocol());

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

        // 772・773両方にあるパケットも素通し（Tracked Waypoint 0x88）。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x88, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x88, waypoint.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // 773クライアント → 内部772（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join773Against772TranslatesSlots() {
        ProxySession session = loginTo(773, 772);
        assertEquals(773, session.clientProtocol());
        assertEquals(772, session.serverProtocol());
        // Login Success（772形式）→ 773にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（773：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（773：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());

        // Registry Data（772：0x07、ジュークボックス21件）→ 773（0x07、21件・同じ並び）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
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

        // Finish Configuration（772：0x03）→ 773（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（773：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（772：0x2B）→ 773（0x30、同形式。IDだけ変わる）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame773Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());
        assertArrayEquals(joinGame773Payload(), join.toClient().get(0).payload());

        // Spawn Position（772：0x5A）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());

        // Keep Alive（772：0x26）→ 773（0x2B）。Ping（772：0x36）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x36, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());

        // Respawn（772：0x4B）→ 773（0x50、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn773Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x50, respawn.toClient().get(0).id());
        assertArrayEquals(respawn773Payload(), respawn.toClient().get(0).payload());

        // Set Slot（772：0x14、アイテム 1256）→ 773（0x14、1304。
        // music_disc_lava_chicken は772で1256、773で1304（+48）。
        // 棚の挿入などで 1247〜1299 が+48ずれる）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1256)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1304, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（772：0x3A、globalIndex 3 付き）→ 773（0x3F、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat773Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3F, chat.toClient().get(0).id());
        assertArrayEquals(playerChat773Payload(), chat.toClient().get(0).payload());

        // Chat Message（773：0x08）→ 772（0x08、checksum は両方にあるため剥がさない）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage772Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x08, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage772Payload(), chatMsg.toServer().get(0).payload());

        // Set Creative Slot（773：0x37、UntrustedSlot、アイテム 1304）
        // → 772（0x37、アイテム 1256）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot773Packet(1304)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1256, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（773：0x11）は HashedSlot のため落とす（バニラへ送らない）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x11, windowClick773Packet()));
        assertTrue(click.toServer().isEmpty());

        // Change Gamemode（773：0x04）は772にも同ID・同形式があるためそのまま通す。
        Outcome gamemode = session.handleClientPacket(
                new MinecraftPacket(0x04, new byte[]{0x01}));
        assertEquals(1, gamemode.toServer().size());
        assertEquals(0x04, gamemode.toServer().get(0).id());
        assertArrayEquals(new byte[]{0x01}, gamemode.toServer().get(0).payload());

        // Custom Click Action（773：0x41）は772にも同ID・同形式があるためそのまま通す。
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
    // 773クライアント → 内部771（Join Game の変換）
    // ------------------------------------------------------------------
    // 2026-11-12に手書きで追加。773パッチで handleServerConfiguration773From771 が
    // 欠けていた（771サーバーが764／765用の汎用ハンドラに落ち、Finish Configuration が
    // Keep Alive と誤読された）。このテストはその欠落を捕まえる
    // （Finish Configuration 0x03 が 0x03 のまま届くことを主張する）。

    @Test
    void join773Against771TranslatesSlots() {
        ProxySession session = loginTo(773, 771);
        assertEquals(773, session.clientProtocol());
        assertEquals(771, session.serverProtocol());
        // Login Success（771形式）→ 773にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（773：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（773：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());

        // Registry Data（771：0x07、ジュークボックス21件）→ 773（0x07、21件・同じ並び）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
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

        // minecraft:dialog（771：0x07）は773にもあるためそのまま通す。
        Outcome dialog = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:dialog",
                        List.of("custom_options", "quick_actions", "server_links"))));
        assertEquals(1, dialog.toClient().size());
        assertEquals(0x07, dialog.toClient().get(0).id());

        // Finish Configuration（771：0x03）→ 773（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（773：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（771：0x2B）→ 773（0x30、同形式。IDだけ変わる）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame773Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());
        assertArrayEquals(joinGame773Payload(), join.toClient().get(0).payload());

        // Spawn Position（771：0x5A）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());

        // Keep Alive（771：0x26）→ 773（0x2B）。Ping（771：0x36）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x36, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());

        // Respawn（771：0x4B）→ 773（0x50、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn773Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x50, respawn.toClient().get(0).id());
        assertArrayEquals(respawn773Payload(), respawn.toClient().get(0).payload());

        // Set Slot（771：0x14、music_disc_mall 1256）→ 773（0x14、1305。
        // mall は771で1256、772で1257（lava_chicken が1256に挿入されたため+1）、
        // 773で1305（棚12件の挿入でさらに+48）。lava_chicken を使うと
        // 771には無いため石へ落ちるので、ここでは mall を使う）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1256)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1305, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（771：0x3A、globalIndex 3 付き）→ 773（0x3F、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat773Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3F, chat.toClient().get(0).id());
        assertArrayEquals(playerChat773Payload(), chat.toClient().get(0).payload());

        // Chat Message（773：0x08）→ 771（0x08、checksum は両方にあるため剥がさない）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage772Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x08, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage772Payload(), chatMsg.toServer().get(0).payload());

        // Set Creative Slot（773：0x37、UntrustedSlot、music_disc_mall 1305）
        // → 771（0x37、アイテム 1256）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot773Packet(1305)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1256, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Set Creative Slot（773：music_disc_lava_chicken 1304）は
        // 771に無いため石（1）へ落ちる。
        Outcome lavaChicken = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot773Packet(1304)));
        assertEquals(1, lavaChicken.toServer().size());
        assertEquals(0x37, lavaChicken.toServer().get(0).id());
        assertEquals(1, readSetCreativeSlotItemId(lavaChicken.toServer().get(0).payload()));

        // Window Click（773：0x11）は HashedSlot のため落とす（バニラへ送らない）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x11, windowClick773Packet()));
        assertTrue(click.toServer().isEmpty());

        // Change Gamemode（773：0x04）は771にも同ID・同形式があるためそのまま通す。
        Outcome gamemode = session.handleClientPacket(
                new MinecraftPacket(0x04, new byte[]{0x01}));
        assertEquals(1, gamemode.toServer().size());
        assertEquals(0x04, gamemode.toServer().get(0).id());
        assertArrayEquals(new byte[]{0x01}, gamemode.toServer().get(0).payload());

        // Custom Click Action（773：0x41）は771にも同ID・同形式があるためそのまま通す。
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
    // 772クライアント → 内部773（上向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join772Against773TranslatesSlots() {
        ProxySession session = loginTo(772, 773);
        assertEquals(772, session.clientProtocol());
        assertEquals(773, session.serverProtocol());
        // Login Success（773形式）→ 772にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // Login Acknowledged（772：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());

        // Client Information（772：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());

        // Registry Data（773：0x07、ジュークボックス21件）→ 772（0x07、21件・同じ並び）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
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

        // minecraft:dialog（773：0x07）は772にもあるためそのまま通す。
        Outcome dialog = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:dialog",
                        List.of("custom_options", "quick_actions", "server_links"))));
        assertEquals(1, dialog.toClient().size());
        assertEquals(0x07, dialog.toClient().get(0).id());

        // Finish Configuration（773：0x03）→ 772（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（772：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（773：0x30）→ 772（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame773Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame773Payload(), join.toClient().get(0).payload());

        // Spawn Position（773：0x5F）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5F, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Set Slot（773：0x14、アイテム 1304）→ 772（0x14、1256。
        // music_disc_lava_chicken は773で1304、772で1256）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1304)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1256, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（773：0x3F、globalIndex 3 付き）→ 772（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3F, playerChat773Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat773Payload(), chat.toClient().get(0).payload());

        // Chat Message（772：0x08）→ 773（0x08、checksum はそのまま）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage772Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x08, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage772Payload(), chatMsg.toServer().get(0).payload());

        // Set Creative Slot（772：0x37、UntrustedSlot、アイテム 1256）
        // → 773（0x37、アイテム 1304）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot773Packet(1256)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1304, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（772：0x11）は HashedSlot のため落とす。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x11, windowClick773Packet()));
        assertTrue(click.toServer().isEmpty());

        // Tracked Waypoint（773：0x88）は772（0x83）へ番号だけ付け替えて通す。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x88, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x83, waypoint.toClient().get(0).id());
        assertArrayEquals(new byte[]{0x00}, waypoint.toClient().get(0).payload());

        // Show Dialog（773：0x8A）／Clear Dialog（773：0x89）は
        // 772（0x85／0x84）へ番号だけ付け替えて通す。
        Outcome showDialog = session.handleServerPacket(
                new MinecraftPacket(0x8A, new byte[]{0x00}));
        assertEquals(1, showDialog.toClient().size());
        assertEquals(0x85, showDialog.toClient().get(0).id());
        Outcome clearDialog = session.handleServerPacket(
                new MinecraftPacket(0x89, new byte[0]));
        assertEquals(1, clearDialog.toClient().size());
        assertEquals(0x84, clearDialog.toClient().get(0).id());

        // Spawn Entity（773：0x01、ハッピーガスト 57）→ 772（56）。
        Outcome spawnEntity = session.handleServerPacket(
                new MinecraftPacket(0x01, spawnEntityPacket(57, 0)));
        assertEquals(1, spawnEntity.toClient().size());
        assertEquals(0x01, spawnEntity.toClient().get(0).id());
        assertEquals(56, readSpawnEntityType(spawnEntity.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 771クライアント → 内部773（上向き、スロット変換あり）
    // ------------------------------------------------------------------
    // 2026-11-12に手書きで追加。773クライアント→771サーバーの対になる向き。
    // 771クライアント＋773サーバーの設定フェーズは handleServerConfiguration772From773
    // を使い回す（771・772の設定IDは同じ）。

    @Test
    void join771Against773TranslatesSlots() {
        ProxySession session = loginTo(771, 773);
        assertEquals(771, session.clientProtocol());
        assertEquals(773, session.serverProtocol());
        // Login Success（773形式）→ 771にはそのまま送る。
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
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());

        // Registry Data（773：0x07、ジュークボックス21件）→ 771（0x07、20件。
        // lava_chicken は772で追加されたため771には無く、773→772→771の
        // 並べ替えで落とされる。従来は handleServerConfiguration772From773 を
        // 直接呼んで21件のまま送っていた（連鎖飛ばし）。
        // fix-773-config-chains.py で修正。）
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(20, names.size());
        assertEquals(-1, names.indexOf("lava_chicken"));
        assertEquals(9, names.indexOf("mall"));
        assertEquals(17, names.indexOf("tears"));
        assertEquals(18, names.indexOf("wait"));
        assertEquals(19, names.indexOf("ward"));

        // minecraft:dialog（773：0x07）は771にもあるためそのまま通す。
        Outcome dialog = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:dialog",
                        List.of("custom_options", "quick_actions", "server_links"))));
        assertEquals(1, dialog.toClient().size());
        assertEquals(0x07, dialog.toClient().get(0).id());

        // Finish Configuration（773：0x03）→ 771（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（771：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（773：0x30）→ 771（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame773Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame773Payload(), join.toClient().get(0).payload());

        // Spawn Position（773：0x5F）→ 771（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5F, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Set Slot（773：0x14、music_disc_mall 1305）→ 771（0x14、1256。
        // mall は773で1305、772で1257、771で1256。lava_chicken を使うと
        // 771には無いため石へ落ちるので、ここでは mall を使う）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1305)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1256, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Set Slot（773：music_disc_lava_chicken 1304）は771に無いため石（1）へ落ちる。
        Outcome lavaChickenSlot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1304)));
        assertEquals(1, lavaChickenSlot.toClient().size());
        assertEquals(0x14, lavaChickenSlot.toClient().get(0).id());
        assertEquals(1, readSetSlotItemId768(lavaChickenSlot.toClient().get(0).payload()));

        // Player Chat（773：0x3F、globalIndex 3 付き）→ 771（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3F, playerChat773Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat773Payload(), chat.toClient().get(0).payload());

        // Chat Message（771：0x08）→ 773（0x08、checksum はそのまま）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage772Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x08, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage772Payload(), chatMsg.toServer().get(0).payload());

        // Set Creative Slot（771：0x37、UntrustedSlot、music_disc_mall 1256）
        // → 773（0x37、アイテム 1305）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot773Packet(1256)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1305, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（771：0x11）は HashedSlot のため落とす。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x11, windowClick773Packet()));
        assertTrue(click.toServer().isEmpty());

        // Tracked Waypoint（773：0x88）は771（0x83）へ番号だけ付け替えて通す。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x88, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x83, waypoint.toClient().get(0).id());
        assertArrayEquals(new byte[]{0x00}, waypoint.toClient().get(0).payload());

        // Show Dialog（773：0x8A）／Clear Dialog（773：0x89）は
        // 771（0x85／0x84）へ番号だけ付け替えて通す。
        Outcome showDialog = session.handleServerPacket(
                new MinecraftPacket(0x8A, new byte[]{0x00}));
        assertEquals(1, showDialog.toClient().size());
        assertEquals(0x85, showDialog.toClient().get(0).id());
        Outcome clearDialog = session.handleServerPacket(
                new MinecraftPacket(0x89, new byte[0]));
        assertEquals(1, clearDialog.toClient().size());
        assertEquals(0x84, clearDialog.toClient().get(0).id());

        // Spawn Entity（773：0x01、ハッピーガスト 57）→ 771（56）。
        Outcome spawnEntity = session.handleServerPacket(
                new MinecraftPacket(0x01, spawnEntityPacket(57, 0)));
        assertEquals(1, spawnEntity.toClient().size());
        assertEquals(0x01, spawnEntity.toClient().get(0).id());
        assertEquals(56, readSpawnEntityType(spawnEntity.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 773クライアント → 内部770（Join Game／スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join773Against770TranslatesSlots() {
        ProxySession session = loginTo(773, 770);
        assertEquals(773, session.clientProtocol());
        assertEquals(770, session.serverProtocol());
        // Login Success（770形式）→ 773にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // Login Acknowledged（773：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());

        // Client Information（773：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());

        // Registry Data（770：0x07、ジュークボックス19件）→ 773（0x07、21件。
        // 770→771で tears が17番に、771→772で lava_chicken が9番に挿入される）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs770())));
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

        // minecraft:dialog は770には無いため送らない（1.21.6で追加）。

        // Finish Configuration（770：0x03）→ 773（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（773：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（770：0x2B）→ 773（0x30、同形式。IDだけ変わる）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame773Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());
        assertArrayEquals(joinGame773Payload(), join.toClient().get(0).payload());

        // Spawn Position（770：0x5A）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());

        // Keep Alive（770：0x26）→ 773（0x2B）。Ping（770：0x36）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x36, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());

        // Respawn（770：0x4B）→ 773（0x50、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn773Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x50, respawn.toClient().get(0).id());
        assertArrayEquals(respawn773Payload(), respawn.toClient().get(0).payload());

        // Set Slot（770：0x14、music_disc_mall 1238）→ 773（0x14、1305。
        // mall は770で1238、771で1256、772で1257、773で1305）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1238)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1305, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（770：0x3A、globalIndex 3 付き）→ 773（0x3F、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat773Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3F, chat.toClient().get(0).id());
        assertArrayEquals(playerChat773Payload(), chat.toClient().get(0).payload());

        // Chat Message（773：0x08）→ 770（0x07、checksum は両方にあるため剥がさない）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage772Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x07, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage772Payload(), chatMsg.toServer().get(0).payload());

        // Set Creative Slot（773：0x37、UntrustedSlot、music_disc_mall 1305）
        // → 770（0x36、アイテム 1238）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot773Packet(1305)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x36, creative.toServer().get(0).id());
        assertEquals(1238, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));

        // Window Click（773：0x11）は HashedSlot のため落とす（バニラへ送らない）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x11, windowClick773Packet()));
        assertTrue(click.toServer().isEmpty());

        // Spawn Entity（770：0x01、ブタ 94）→ 773（97）。
        Outcome spawnEntity = session.handleServerPacket(
                new MinecraftPacket(0x01, spawnEntityPacket(94, 0)));
        assertEquals(1, spawnEntity.toClient().size());
        assertEquals(0x01, spawnEntity.toClient().get(0).id());
        assertEquals(97, readSpawnEntityType(spawnEntity.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 770クライアント → 内部773（上向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join770Against773TranslatesSlots() {
        ProxySession session = loginTo(770, 773);
        assertEquals(770, session.clientProtocol());
        assertEquals(773, session.serverProtocol());
        // Login Success（773形式）→ 770にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // Login Acknowledged（770：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());

        // Client Information（770：0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());

        // Registry Data（773：0x07、ジュークボックス21件）→ 770（0x07、19件。
        // lava_chicken と tears は770に無いため並べ替えで落とされる）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(19, names.size());
        assertEquals(-1, names.indexOf("lava_chicken"));
        assertEquals(-1, names.indexOf("tears"));
        assertEquals(9, names.indexOf("mall"));
        assertEquals(17, names.indexOf("wait"));
        assertEquals(18, names.indexOf("ward"));

        // Finish Configuration（773：0x03）→ 770（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（770：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（773：0x30）→ 770（0x2B、同形式。IDだけ変わる）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame773Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame773Payload(), join.toClient().get(0).payload());

        // Spawn Position（773：0x5F）→ 770（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5F, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());

        // Set Slot（773：0x14、music_disc_mall 1305）→ 770（0x14、1238）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1305)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1238, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Set Slot（773：music_disc_lava_chicken 1304）は770に無いため石（1）へ落ちる。
        Outcome lavaChickenSlot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1304)));
        assertEquals(1, lavaChickenSlot.toClient().size());
        assertEquals(0x14, lavaChickenSlot.toClient().get(0).id());
        assertEquals(1, readSetSlotItemId768(lavaChickenSlot.toClient().get(0).payload()));

        // Player Chat（773：0x3F、globalIndex 3 付き）→ 770（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3F, playerChat773Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat773Payload(), chat.toClient().get(0).payload());

        // Chat Message（770：0x07）→ 773（0x08、checksum はそのまま）。
        Outcome chatMsg = session.handleClientPacket(
                new MinecraftPacket(0x07, chatMessage772Payload()));
        assertEquals(1, chatMsg.toServer().size());
        assertEquals(0x08, chatMsg.toServer().get(0).id());
        assertArrayEquals(chatMessage772Payload(), chatMsg.toServer().get(0).payload());

        // プレイ中の Settings（770：0x0c、9フィールド）はそのまま773（0x0d）へ送る。
        // 従来は handleClientPlayTo773 が particleStatus をもう1個付け足して
        // 10フィールドにしていた（fix-773-client-information.py で修正）。
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x0c, clientInformation773Payload()));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x0d, settings.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), settings.toServer().get(0).payload());

        // Set Creative Slot（770：0x36）は773への変換の鎖が無いため落とす
        // （770→772でも同じ扱い。771／772クライアントは専用ハンドラがある）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x36, setCreativeSlot773Packet(1238)));
        assertTrue(creative.toServer().isEmpty());

        // Window Click（770：0x10）は HashedSlot のため落とす。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick773Packet()));
        assertTrue(click.toServer().isEmpty());

        // Spawn Entity（773：0x01、ハッピーガスト 57）→ 770（ガスト 55。
        // ハッピーガストは771で追加されたため770には無く、ブタではなく
        // ガストで代用する。translateSpawnEntity771To770 に明記あり）。
        Outcome spawnEntity = session.handleServerPacket(
                new MinecraftPacket(0x01, spawnEntityPacket(57, 0)));
        assertEquals(1, spawnEntity.toClient().size());
        assertEquals(0x01, spawnEntity.toClient().get(0).id());
        assertEquals(55, readSpawnEntityType(spawnEntity.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 773クライアント → 内部766（Join Game の変換）
    // ------------------------------------------------------------------

    @Test
    void join773Against766TranslatesJoinGame() {
        ProxySession session = loginTo(773, 766);
        assertEquals(773, session.clientProtocol());
        assertEquals(766, session.serverProtocol());
        // Login Success（766形式）→ 773にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());

        // Login Acknowledged（773：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（773：0x00、9フィールド）は766へ送るとき
        // particleStatus（末尾の varint）を落として8フィールドにする。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation766Payload(), info.toServer().get(0).payload());

        // Registry Data（766：0x07、dimension_type）→ 773（0x07）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryData766Payload()));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());

        // Finish Configuration（766：0x03）→ 773（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（773：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（766：0x2B、seaLevel 無し）→ 773（0x30、seaLevel=63 を補う）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame767To768(joinGame766Payload()),
                join.toClient().get(0).payload());

        // Spawn Position（766：0x56）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x56, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());

        // Keep Alive（766：0x26）→ 773（0x2B）。Ping（766：0x35）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x35, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // 773クライアント → 内部765（Join Game 相当だけを中継）
    // ------------------------------------------------------------------

    @Test
    void join773Against765RelaysJoinLevel() {
        ProxySession session = loginTo(773, 765);
        assertEquals(773, session.clientProtocol());
        assertEquals(765, session.serverProtocol());
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // 765サーバーは設定フェーズを持たないため、プロキシは
        // Join Game（765：0x29）を受け取るまでプレイ状態に入らない。
        finishConfig(session, 773, 765, registryDataPayload(), 0x05, 0x02, 0x03);
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Join Game（765：0x29）→ 773（0x30）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());

        // Spawn Position（765：0x54）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());

        // Keep Alive（765：0x24）→ 773（0x2B）。Ping（765：0x33）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());

        // Respawn（765：0x45）→ 773（0x50、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x50, respawn.toClient().get(0).id());
        assertArrayEquals(respawn773Payload(), respawn.toClient().get(0).payload());

        // プレイ状態のその他パケットは773クライアントには落とす（join レベルのみ）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(799)));
        assertTrue(slot.toClient().isEmpty());

        // Add Resource Pack（765：0x44）→ 773（0x4F）。
        Writer pack = new Writer();
        pack.writeUuid(PLAYER_UUID);
        pack.writeString("https://example.invalid/pack.zip");
        pack.writeString("0123456789abcdef0123456789abcdef01234567");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome addPack = session.handleServerPacket(
                new MinecraftPacket(0x44, pack.bytes()));
        assertEquals(1, addPack.toClient().size());
        assertEquals(0x4F, addPack.toClient().get(0).id());

        // Disconnect（765：0x1B）→ 773（0x20）。セッションを閉じるため最後に送る。
        Outcome disconnect = session.handleServerPacket(
                new MinecraftPacket(0x1B, new byte[]{0x00}));
        assertEquals(1, disconnect.toClient().size());
        assertEquals(0x20, disconnect.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // 769クライアント → 内部773（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join769Against773TranslatesSlots() {
        ProxySession session = loginTo(769, 773);
        assertEquals(769, session.clientProtocol());
        assertEquals(773, session.serverProtocol());
        // Login Success（773形式）→ 769にはそのまま送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        // Login Acknowledged（769：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());

        // Client Information（769：0x00、9フィールド）は773へそのまま送る。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());

        // Registry Data（773：0x07、ジュークボックス21件）→ 769（0x07、19件。
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

        // Finish Configuration（773：0x03）→ 769（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（769：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（773：0x30）→ 769（0x2C、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame773Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());
        assertArrayEquals(joinGame773Payload(), join.toClient().get(0).payload());

        // Respawn（773：0x50）→ 769（0x4C、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x50, respawn773Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn773Payload(), respawn.toClient().get(0).payload());

        // Set Slot（773：0x14、アイテム 649）→ 769（0x15、612。773の 649 は
        // 772で 620 で、772の 620 は 769では 612）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(649)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(612, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Player Chat（773：0x3F、globalIndex 3 付き）→ 769（0x3B、剥がす）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3F, playerChat773Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3B, chat.toClient().get(0).id());
        assertArrayEquals(playerChat769Payload(), chat.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 773クライアント → 内部763（設定フェーズを合成）
    // ------------------------------------------------------------------

    @Test
    void join773Against763SynthesizesConfig() {
        ProxySession.HandshakeResult result = handshake(
                773, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(773, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は763形式（bool 付き）に直してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PLAYER_UUID, loginReader.readUuid());

        // Login Success（763形式）→ 773にはそのまま送る（strict bool は無い）。
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

        // Login Acknowledged（773：0x03）で合成した設定フェーズを送る。
        // minecraft:dialog は合成しないため、送るのは dimension_type・
        // biome の Registry Data 2件＋Finish Configuration の3件。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertTrue(ack.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(3, ack.toClient().size());
        assertEquals(0x07, ack.toClient().get(0).id());
        assertEquals(0x07, ack.toClient().get(1).id());
        assertEquals(0x03, ack.toClient().get(2).id());

        // Finish Configuration の応答（773：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x30, finishAck.toClient().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Reader joinReader = new Reader(finishAck.toClient().get(0).payload());
        assertEquals(42, joinReader.readInt()); // entityId
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部773（設定フェーズを773クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join763Against773ConsumesConfigAs773Client() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 773);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(773, session.serverProtocol());

        // Login Start は763形式から773形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（773形式）→ 763にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        // 763クライアント向けにプロキシが合成する Client Information は
        // 773形式（9フィールド。particleStatus=0 付き）。8フィールドのまま
        // 送ると773のバニラが読み取り途中で切断する。
        byte[] synthInfo = success.toServer().get(1).payload();
        assertEquals(PlayTranslator.clientInformationPayload().length + 1, synthInfo.length);
        assertEquals(0, synthInfo[synthInfo.length - 1]);
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（773：0x07）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x07, registryData766Payload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Add Resource Pack（773：0x09）は保持する。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x09, addResourcePackPayload()));
        assertTrue(pack.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());

        // Finish Configuration（773：0x03）→ 応答（0x03）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（773：0x30）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame773Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());
    }

    // ------------------------------------------------------------------
    // 設定フェーズの Custom Click Action（773・772）／ダイアログ（773）
    // ------------------------------------------------------------------

    @Test
    void configCustomClickAction773RelayedTo772Server() {
        ProxySession session = loginTo(773, 772);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // Custom Click Action（設定 0x08）は772・773とも同ID・同形式のためそのまま通す。
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
    void configCustomClickAction773RelayedNativelyTo773Server() {
        // ネイティブ773同士は Login Success の後にプレイ状態へ入る。
        // ネイティブのセッションは再設定を追跡しない（772のネイティブ同士と同じ）：
        // サーバーからの Start Configuration（0x74）はそのまま中継し、
        // セッションはプレイ状態のまま、isReconfiguring() も false のままである。
        // その後のクライアントの設定フェーズのパケットも、プレイ状態の番号で
        // そのまま中継される。Custom Click Action（設定 0x08）はプレイ状態の
        // Chat Message（0x08）と番号が同じため、バイト列としてはそのまま
        // サーバーへ届く（サーバーは設定状態で読むため正しく解釈する）。
        ProxySession session = loginTo(773, 773);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome start = session.handleServerPacket(
                new MinecraftPacket(0x74, new byte[0]));
        assertEquals(1, start.toClient().size());
        assertEquals(0x74, start.toClient().get(0).id());
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
    void configCustomClickAction772RelayedTo773Server() {
        ProxySession session = loginTo(772, 773);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // Custom Click Action（設定 0x08）は772・773とも同ID・同形式の
        // ためそのまま通す。771までの Custom Report Details（0x08）／
        // Server Links（0x09）は772・773には無いが、772クライアントは
        // もともと送らないため、772→773で落とす設定パケットは無い。
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
    void configDialog773RelayedTo772Server() {
        ProxySession session = loginTo(773, 772);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        // Show Dialog（設定 0x12）／Clear Dialog（設定 0x11）は772・773とも
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
    void clientInformation768KeptFor773Server() {
        // 768クライアント→773サーバーの設定フェーズ：Client Information は
        // 768・773とも9フィールド（末尾の particleStatus 付き）のため、
        // particleStatus を落とさずそのまま通す（771段階の漏れ修正 8fa47ab の
        // 773版。落とすと773サーバーが8フィールドで切断する）。
        ProxySession session = loginTo(768, 773);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation773Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation773Payload(), info.toServer().get(0).payload());
        // Finish Configuration（768：0x03）はそのまま送られ、プレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
    }

    // ------------------------------------------------------------------
    // 設定フェーズのレジストリ並べ替え（773）
    // ------------------------------------------------------------------

    @Test
    void registryData773To772KeepsBiomesInSession() {
        ProxySession session = loginTo(772, 773);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:worldgen/biome", biomes773())));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(65, names.size());
        assertEquals(39, names.indexOf("pale_garden"));
        assertEquals(40, names.indexOf("plains"));
    }
}
