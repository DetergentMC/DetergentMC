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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 768（1.21.2／1.21.3）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession767Test} と同じ段取りで、768の番号・形式で確かめる。
 */
class ProxySession768Test {

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

    /** 766／767の Login Start：名前＋UUID必須。768も同じ。 */
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

    /** 765／768の Login Success（strict bool なし）。 */
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

    /** 767の Client Information（設定フェーズ。766／768と同じ）。 */
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

    /** 766／767／768の Registry Data 1件（dimension_type、エントリ配列形式）。 */
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

    /** 768の Join Game（SpawnInfo の末尾に seaLevel=63）。 */
    private static byte[] joinGame768Payload() {
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

    /** 768の Respawn（SpawnInfo の末尾に seaLevel=63、copyMetadata は末尾のバイト）。 */
    private static byte[] respawn768Payload() {
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

    /** 766／767／768の Add Resource Pack（UUID付き、プロンプトなし）。 */
    private static byte[] addResourcePackPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        writer.writeString("https://127.0.0.1:25567/detergentmc-pack-1.21.zip");
        writer.writeString("0123456789abcdef0123456789abcdef01234567");
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    // ---- ダメージ種別の名称表（並べ替えの検証用） ----

    private static final List<String> DAMAGE_767 = List.of(
            "arrow", "bad_respawn_point", "cactus", "campfire", "cramming", "dragon_breath",
            "drown", "dry_out", "explosion", "fall", "falling_anvil", "falling_block",
            "falling_stalactite", "fireball", "fireworks", "fly_into_wall", "freeze",
            "generic", "generic_kill", "hot_floor", "in_fire", "in_wall", "indirect_magic",
            "lava", "lightning_bolt", "magic", "mob_attack", "mob_attack_no_aggro",
            "mob_projectile", "on_fire", "out_of_world", "outside_border", "player_attack",
            "player_explosion", "sonic_boom", "spit", "stalagmite", "starve", "sting",
            "sweet_berry_bush", "thorns", "thrown", "trident", "unattributed_fireball",
            "wind_charge", "wither", "wither_skull");

    private static final List<String> DAMAGE_768 = List.of(
            "arrow", "bad_respawn_point", "cactus", "campfire", "cramming", "dragon_breath",
            "drown", "dry_out", "ender_pearl", "explosion", "fall", "falling_anvil",
            "falling_block", "falling_stalactite", "fireball", "fireworks", "fly_into_wall",
            "freeze", "generic", "generic_kill", "hot_floor", "in_fire", "in_wall",
            "indirect_magic", "lava", "lightning_bolt", "mace_smash", "magic", "mob_attack",
            "mob_attack_no_aggro", "mob_projectile", "on_fire", "out_of_world",
            "outside_border", "player_attack", "player_explosion", "sonic_boom", "spit",
            "stalagmite", "starve", "sting", "sweet_berry_bush", "thorns", "thrown",
            "trident", "unattributed_fireball", "wind_charge", "wither", "wither_skull");

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

    /** 766／767／768形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767／768形式の Registry Data パケットを読み、名前列を返す。 */
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

    /** Set Slot ペイロード（768：windowId は varint）。 */
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

    /** Set Slot ペイロード（768形式）のアイテムIDを読む。 */
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

    /** Window Click ペイロード（768形式。windowId は varint）。 */
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

    /** Window Click ペイロード（768形式）の changed スロットのアイテムIDを読む。 */
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
     * （766／767／768は 0x03、765／764は 0x02）。 */
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
    // 768クライアント → 内部768（素通し）
    // ------------------------------------------------------------------

    @Test
    void join768Against768IsRelayedOffline() {
        ProxySession.HandshakeResult result = handshake(
                768, 2, false, ProxyAuthConfig.defaults(), 768);
        assertFalse(result.disconnect());
        assertTrue(result.relay());
        ProxySession session = result.session();
        assertEquals(768, session.clientProtocol());
        assertEquals(768, session.serverProtocol());

        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // ネイティブ＋offline は Login Success の後にリレーへ切り替わる。
        // 768の Login Success は strict bool が無い（765と同じ形式）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // プレイ状態は素通し（Set Slot の turtle_helmet は 816 のまま）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(816)));
        assertEquals(1, slot.toClient().size());
        assertEquals(816, readSetSlotItemId768(slot.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 768クライアント → 内部767（下向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join768Against767TranslatesSlots() {
        ProxySession session = loginTo(768, 767);
        assertEquals(768, session.clientProtocol());
        assertEquals(767, session.serverProtocol());
        // Login Success（767形式）→ 768には strict bool を落として送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（768：0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（768：0x00）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation766Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());

        // Registry Data（767：0x07、damage_type）→ 768（0x07。ender_pearl／mace_smash 付き）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:damage_type", DAMAGE_767)));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(DAMAGE_768, names);
        assertEquals(8, names.indexOf("ender_pearl"));
        assertEquals(26, names.indexOf("mace_smash"));
        assertEquals(27, names.indexOf("magic"));

        // Finish Configuration（767：0x03）→ 768（0x03）。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（768：0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（767：0x2B）→ 768（0x2C、seaLevel 付き）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());
        Reader joinReader = new Reader(join.toClient().get(0).payload());
        assertEquals(42, joinReader.readInt());
        joinReader.readBoolean();
        PacketIO.readStringList(joinReader);
        joinReader.readVarInt();
        joinReader.readVarInt();
        joinReader.readVarInt();
        joinReader.readBoolean();
        joinReader.readBoolean();
        joinReader.readBoolean();
        assertEquals(0, joinReader.readVarInt()); // dimension（overworld）
        assertEquals("minecraft:overworld", joinReader.readString());
        joinReader.readLong();
        joinReader.readByte();
        joinReader.readByte();
        joinReader.readBoolean();
        joinReader.readBoolean();
        joinReader.readBoolean();
        assertEquals(0, joinReader.readVarInt()); // portalCooldown
        assertEquals(63, joinReader.readVarInt()); // seaLevel
        assertFalse(joinReader.readBoolean()); // enforcesSecureChat
        assertEquals(0, joinReader.remaining());

        // Spawn Position（767：0x56）→ 768（0x5B）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x56, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5B, spawn.toClient().get(0).id());

        // Keep Alive（767：0x26）→ 768（0x27）。Ping（767：0x35）→ 768（0x37）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x27, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x35, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x37, ping.toClient().get(0).id());

        // Respawn（767：0x47）→ 768（0x4C、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x47, respawn766Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn768Payload(), respawn.toClient().get(0).payload());

        // Set Slot（767：0x15、turtle_helmet 794）→ 768（0x15、816）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(794)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(816, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Window Click（768：0x10、turtle_helmet 816）→ 767（0x0E、794）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick768Packet(816)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0E, click.toServer().get(0).id());
        assertEquals(794, readWindowClickItemId(click.toServer().get(0).payload()));

        // Synchronize Player Position（767：0x40）→ 768（0x42）。
        Writer position = new Writer();
        position.writeDouble(1.5);
        position.writeDouble(64.0);
        position.writeDouble(2.5);
        position.writeFloat(90.0f);
        position.writeFloat(0.0f);
        position.writeByte(0x03); // flags（x／y 相対）
        position.writeVarInt(42); // teleportId
        Outcome pos = session.handleServerPacket(
                new MinecraftPacket(0x40, position.bytes()));
        assertEquals(1, pos.toClient().size());
        assertEquals(0x42, pos.toClient().get(0).id());
        Reader posReader = new Reader(pos.toClient().get(0).payload());
        assertEquals(42, posReader.readVarInt()); // teleportId（先頭へ）
        assertEquals(1.5, posReader.readDouble(), 0.0001); // x
        assertEquals(64.0, posReader.readDouble(), 0.0001); // y
        assertEquals(2.5, posReader.readDouble(), 0.0001); // z
        assertEquals(0.0, posReader.readDouble(), 0.0001); // dx
        assertEquals(0.0, posReader.readDouble(), 0.0001); // dy
        assertEquals(0.0, posReader.readDouble(), 0.0001); // dz

        // Player Info（767：0x3E）→ 768（0x40、素通し）。
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

        // Update Time（767：0x64）→ 768（0x6B、tickDayTime 付き）。
        Writer time = new Writer();
        time.writeLong(1000L);
        time.writeLong(6000L);
        Outcome updateTime = session.handleServerPacket(
                new MinecraftPacket(0x64, time.bytes()));
        assertEquals(1, updateTime.toClient().size());
        assertEquals(0x6B, updateTime.toClient().get(0).id());
        Reader timeReader = new Reader(updateTime.toClient().get(0).payload());
        assertEquals(1000L, timeReader.readLong());
        assertEquals(6000L, timeReader.readLong());
        assertFalse(timeReader.readBoolean()); // tickDayTime

        // クライアント→サーバー：Player Input（768：0x35、forward＋left＋jump＋sneak）
        // → Steer Vehicle（767：0x26）。
        Outcome input = session.handleClientPacket(
                new MinecraftPacket(0x28, new byte[]{(byte) 0x35}));
        assertEquals(1, input.toServer().size());
        assertEquals(0x26, input.toServer().get(0).id());
        Reader inputReader = new Reader(input.toServer().get(0).payload());
        assertEquals(-1.0f, inputReader.readFloat(), 0.0001f); // sideways（left）
        assertEquals(1.0f, inputReader.readFloat(), 0.0001f); // forward
        assertEquals(0x03, inputReader.readUnsignedByte()); // jump＋sneak

        // Set Slot State（768：0x12）→ 767（0x10、同形式）。
        Writer slotState = new Writer();
        slotState.writeVarInt(5);
        slotState.writeVarInt(2);
        slotState.writeBoolean(true);
        Outcome state = session.handleClientPacket(
                new MinecraftPacket(0x12, slotState.bytes()));
        assertEquals(1, state.toServer().size());
        assertEquals(0x10, state.toServer().get(0).id());
        assertArrayEquals(slotState.bytes(), state.toServer().get(0).payload());

        // Chat Message（768：0x07）→ 767（0x06）。
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x07, new byte[]{0x00}));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x06, chat.toServer().get(0).id());

        // 768だけのクライアント→サーバーパケットは落とす。
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x02, new byte[]{0x00, 0x00})).toServer().isEmpty()); // Select Bundle Item
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x0B, new byte[0])).toServer().isEmpty()); // Tick End
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x24, new byte[]{0x00, 0x00})).toServer().isEmpty()); // Craft Recipe Request
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x2B, new byte[]{0x00})).toServer().isEmpty()); // Displayed Recipe
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

    // ------------------------------------------------------------------
    // 767クライアント → 内部768（上向き、スロット変換あり）
    // ------------------------------------------------------------------

    @Test
    void join767Against768TranslatesSlots() {
        ProxySession session = loginTo(767, 768);
        // Login Success（768形式）→ 767には strict bool を足して送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        finishConfig(session, 767, 768, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（768：0x2C）→ 767（0x2B、seaLevel を落とす）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame768Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        PlayTranslator.JoinGame766 parsed =
                PlayTranslator.parseJoinGame766(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimensionId());

        // Spawn Position（768：0x5B）→ 767（0x56）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5B, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x56, spawn.toClient().get(0).id());

        // Keep Alive（768：0x27）→ 767（0x26）。Ping（768：0x37）→ 767（0x35）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x27, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x37, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x35, ping.toClient().get(0).id());

        // Respawn（768：0x4C）→ 767（0x47、seaLevel を落とす）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn768Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
        assertArrayEquals(respawn766Payload(), respawn.toClient().get(0).payload());

        // Set Slot（768：0x15、turtle_helmet 816）→ 767（0x15、794）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(816)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(794, readSetSlotItemId(slot.toClient().get(0).payload()));

        // Window Click（767：0x0E、turtle_helmet 794）→ 768（0x10、816）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0E, windowClick767Packet(794)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x10, click.toServer().get(0).id());
        assertEquals(816, readWindowClickItemId768(click.toServer().get(0).payload()));

        // Player Position（767：0x1A）→ 768（0x1C、MovementFlags）。
        Writer position = new Writer();
        position.writeDouble(1.5);
        position.writeDouble(64.0);
        position.writeDouble(2.5);
        position.writeBoolean(true); // onGround
        Outcome pos = session.handleClientPacket(
                new MinecraftPacket(0x1A, position.bytes()));
        assertEquals(1, pos.toServer().size());
        assertEquals(0x1C, pos.toServer().get(0).id());
        Reader posReader = new Reader(pos.toServer().get(0).payload());
        assertEquals(1.5, posReader.readDouble(), 0.0001);
        assertEquals(64.0, posReader.readDouble(), 0.0001);
        assertEquals(2.5, posReader.readDouble(), 0.0001);
        assertEquals(0x01, posReader.readUnsignedByte()); // flags（onGround）

        // Steer Vehicle（767：0x26）→ Player Input（768：0x28）。
        Writer steer = new Writer();
        steer.writeFloat(-1.0f); // sideways（left）
        steer.writeFloat(1.0f); // forward
        steer.writeByte(0x03); // jump＋sneak
        Outcome input = session.handleClientPacket(
                new MinecraftPacket(0x26, steer.bytes()));
        assertEquals(1, input.toServer().size());
        assertEquals(0x28, input.toServer().get(0).id());
        assertArrayEquals(new byte[]{(byte) 0x35}, input.toServer().get(0).payload());

        // Set Slot State（767：0x10）→ 768（0x12、同形式）。
        Writer slotState = new Writer();
        slotState.writeVarInt(5);
        slotState.writeVarInt(2);
        slotState.writeBoolean(true);
        Outcome state = session.handleClientPacket(
                new MinecraftPacket(0x10, slotState.bytes()));
        assertEquals(1, state.toServer().size());
        assertEquals(0x12, state.toServer().get(0).id());
        assertArrayEquals(slotState.bytes(), state.toServer().get(0).payload());

        // Chat Command Signed（767：0x05）→ 768（0x06）。
        Outcome command = session.handleClientPacket(
                new MinecraftPacket(0x05, new byte[]{0x00}));
        assertEquals(1, command.toServer().size());
        assertEquals(0x06, command.toServer().get(0).id());

        // Craft Recipe Request（767：0x22）／Displayed Recipe（767：0x29）は落とす。
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x22, new byte[]{0x00, 0x00})).toServer().isEmpty());
        assertTrue(session.handleClientPacket(
                new MinecraftPacket(0x29, new byte[]{0x00})).toServer().isEmpty());

        // Player Info（768：0x40、list_order 付き）→ 767（0x3E、list_order を落とす）。
        Writer info768 = new Writer();
        info768.writeByte(0x7F);
        info768.writeVarInt(1);
        info768.writeUuid(PLAYER_UUID);
        info768.writeString("Alice");
        info768.writeVarInt(0);
        info768.writeBoolean(false);
        info768.writeVarInt(0);
        info768.writeVarInt(1);
        info768.writeVarInt(5);
        info768.writeBoolean(false);
        info768.writeVarInt(9); // listPriority
        Outcome playerInfo = session.handleServerPacket(
                new MinecraftPacket(0x40, info768.bytes()));
        assertEquals(1, playerInfo.toClient().size());
        assertEquals(0x3E, playerInfo.toClient().get(0).id());
        Reader infoReader = new Reader(playerInfo.toClient().get(0).payload());
        assertEquals(0x3F, infoReader.readUnsignedByte()); // list_order 無し
        assertEquals(1, infoReader.readVarInt());
        assertEquals(PLAYER_UUID, infoReader.readUuid());
        assertEquals("Alice", infoReader.readString());

        // Synchronize Player Position（768：0x42）→ 767（0x40）。
        Writer pos768 = new Writer();
        pos768.writeVarInt(42); // teleportId
        pos768.writeDouble(1.5);
        pos768.writeDouble(64.0);
        pos768.writeDouble(2.5);
        pos768.writeDouble(0.1); // dx
        pos768.writeDouble(0.2); // dy
        pos768.writeDouble(0.3); // dz
        pos768.writeFloat(90.0f);
        pos768.writeFloat(0.0f);
        pos768.writeInt(0x123); // flags（u32）
        Outcome syncPos = session.handleServerPacket(
                new MinecraftPacket(0x42, pos768.bytes()));
        assertEquals(1, syncPos.toClient().size());
        assertEquals(0x40, syncPos.toClient().get(0).id());
        Reader syncReader = new Reader(syncPos.toClient().get(0).payload());
        assertEquals(1.5, syncReader.readDouble(), 0.0001); // x
        assertEquals(64.0, syncReader.readDouble(), 0.0001); // y
        assertEquals(2.5, syncReader.readDouble(), 0.0001); // z
        // dx／dy／dz は767には無いため落とされる（出力に現れない）。
        assertEquals(90.0f, syncReader.readFloat(), 0.0001f);
        assertEquals(0.0f, syncReader.readFloat(), 0.0001f);
        assertEquals(0x03, syncReader.readUnsignedByte()); // flags（下位5ビットのみ）
        assertEquals(42, syncReader.readVarInt()); // teleportId（末尾へ）

        // Update Time（768：0x6B）→ 767（0x64、tickDayTime を落とす）。
        Writer time = new Writer();
        time.writeLong(1000L);
        time.writeLong(6000L);
        time.writeBoolean(true);
        Outcome updateTime = session.handleServerPacket(
                new MinecraftPacket(0x6B, time.bytes()));
        assertEquals(1, updateTime.toClient().size());
        assertEquals(0x64, updateTime.toClient().get(0).id());
        Reader timeReader = new Reader(updateTime.toClient().get(0).payload());
        assertEquals(1000L, timeReader.readLong());
        assertEquals(6000L, timeReader.readLong());
        assertEquals(0, timeReader.remaining());

        // Set Cursor Item（768：0x5A）→ 767の Set Slot（0x15、window -1）。
        Outcome cursor = session.handleServerPacket(
                new MinecraftPacket(0x5A, slotOnlyPacket(816)));
        assertEquals(1, cursor.toClient().size());
        assertEquals(0x15, cursor.toClient().get(0).id());
        Reader cursorReader = new Reader(cursor.toClient().get(0).payload());
        assertEquals(-1, cursorReader.readByte()); // windowId -1
        assertEquals(0, cursorReader.readVarInt()); // stateId
        assertEquals(-1, cursorReader.readShort()); // slot -1
        assertEquals(1, cursorReader.readByte()); // count
        assertEquals(794, cursorReader.readVarInt()); // turtle_helmet（767）

        // Set Player Inventory（768：0x66）→ 767の Set Slot（0x15、window 0）。
        Writer inventory = new Writer();
        inventory.writeVarInt(5); // slotId
        inventory.writeByte(1); // count
        inventory.writeVarInt(816); // turtle_helmet（768）
        inventory.writeVarInt(0); // added
        inventory.writeVarInt(0); // removed
        Outcome playerInventory = session.handleServerPacket(
                new MinecraftPacket(0x66, inventory.bytes()));
        assertEquals(1, playerInventory.toClient().size());
        assertEquals(0x15, playerInventory.toClient().get(0).id());
        Reader invReader = new Reader(playerInventory.toClient().get(0).payload());
        assertEquals(0, invReader.readByte()); // windowId 0
        assertEquals(0, invReader.readVarInt()); // stateId
        assertEquals(5, invReader.readShort()); // slot 5
        assertEquals(1, invReader.readByte()); // count
        assertEquals(794, invReader.readVarInt()); // turtle_helmet（767）

        // 768だけのプレイ状態パケットは767に無いため落とす。
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x44, new byte[0])).toClient().isEmpty()); // Recipe Book Add
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x45, new byte[0])).toClient().isEmpty()); // Recipe Book Remove
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x46, new byte[0])).toClient().isEmpty()); // Recipe Book Settings
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x20, new byte[0])).toClient().isEmpty()); // Sync Entity Position
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x31, new byte[0])).toClient().isEmpty()); // Move Minecart
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x43, new byte[0])).toClient().isEmpty()); // Player Rotation
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x81, new byte[0])).toClient().isEmpty()); // Custom Report Details
        assertTrue(session.handleServerPacket(
                new MinecraftPacket(0x82, new byte[0])).toClient().isEmpty()); // Server Links
    }

    // ------------------------------------------------------------------
    // 768クライアント → 内部766（合成変換あり）
    // ------------------------------------------------------------------

    @Test
    void join768Against766TranslatesSlots() {
        ProxySession session = loginTo(768, 766);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 768, 766, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（766：0x2B）→ 768（0x2C、seaLevel 付き）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());

        // Set Slot（766：0x15、turtle_helmet 794）→ 768（0x15、816）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(794)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(816, readSetSlotItemId768(slot.toClient().get(0).payload()));

        // Set Slot（766：0x15、bowl 848）→ 768（0x15、821）。
        Outcome bowl = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(848)));
        assertEquals(821, readSetSlotItemId768(bowl.toClient().get(0).payload()));

        // Window Click（768：0x10、turtle_helmet 816）→ 766（0x0E、794）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x10, windowClick768Packet(816)));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0E, click.toServer().get(0).id());
        assertEquals(794, readWindowClickItemId(click.toServer().get(0).payload()));

        // Respawn（766：0x47）→ 768（0x4C、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x47, respawn766Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn768Payload(), respawn.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 766クライアント → 内部768（合成変換あり）
    // ------------------------------------------------------------------

    @Test
    void join766Against768TranslatesSlots() {
        ProxySession session = loginTo(766, 768);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());

        finishConfig(session, 766, 768, registryData766Payload(), 0x07, 0x03, 0x03);

        // Join Game（768：0x2C）→ 766（0x2B、seaLevel を落とす）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame768Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());

        // Set Slot（768：0x15、turtle_helmet 816）→ 766（0x15、794）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(816)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(794, readSetSlotItemId(slot.toClient().get(0).payload()));

        // Set Slot（768：0x15、bowl 821）→ 766（0x15、848）。
        Outcome bowl = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket768(821)));
        assertEquals(848, readSetSlotItemId(bowl.toClient().get(0).payload()));

        // Window Click（766：0x0E）は768向けに変換できないため落とす。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0E, windowClick767Packet(794)));
        assertTrue(click.toServer().isEmpty());

        // Respawn（768：0x4C）→ 766（0x47、seaLevel を落とす）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4C, respawn768Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x47, respawn.toClient().get(0).id());
        assertArrayEquals(respawn766Payload(), respawn.toClient().get(0).payload());
    }

    @Test
    void registryData768To766DropsJukeboxSongInSession() {
        // jukebox_song の Registry Data は766に無いため、設定フェーズで落とす。
        ProxySession session = loginTo(766, 768);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess766Payload(), success.toClient().get(0).payload());
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome jukebox = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", List.of("13", "cat"))));
        assertTrue(jukebox.toClient().isEmpty());
    }

    // ------------------------------------------------------------------
    // 768クライアント → 内部765（join レベル）
    // ------------------------------------------------------------------

    @Test
    void join768Against765RelaysJoinLevel() {
        ProxySession session = loginTo(768, 765);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());

        finishConfig(session, 768, 765, registryDataPayload(), 0x05, 0x02, 0x03);

        // Join Game（765：0x29）→ 768（0x2C）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());

        // Spawn Position（765：0x54）→ 768（0x5B）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5B, spawn.toClient().get(0).id());

        // Keep Alive（765：0x24）→ 768（0x27）。Ping（765：0x33）→ 768（0x37）。
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

        // Respawn（765：0x45）→ 768（0x4C、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());
        assertArrayEquals(respawn768Payload(), respawn.toClient().get(0).payload());

        // プレイ状態のその他パケットは768クライアントには落とす（join レベルのみ）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlotPacket(799)));
        assertTrue(slot.toClient().isEmpty());
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

    // ------------------------------------------------------------------
    // 768クライアント → 内部763（設定フェーズを合成）
    // ------------------------------------------------------------------

    @Test
    void join768Against763SynthesizesConfig() {
        ProxySession.HandshakeResult result = handshake(
                768, 2, false, ProxyAuthConfig.defaults(), 763);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(768, session.clientProtocol());
        assertEquals(763, session.serverProtocol());

        // Login Start は763形式（bool 付き）に直してバニラへ送る。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart766("Steve")));
        assertEquals(1, login.toServer().size());
        Reader loginReader = new Reader(login.toServer().get(0).payload());
        assertEquals("Steve", loginReader.readString());
        assertTrue(loginReader.readBoolean());
        assertEquals(PLAYER_UUID, loginReader.readUuid());

        // Login Success（763形式）→ 768にはそのまま送る（strict bool は無い）。
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

        // Login Acknowledged（768：0x03）で合成した設定フェーズを送る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertTrue(ack.toServer().isEmpty());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(3, ack.toClient().size());
        assertEquals(0x07, ack.toClient().get(0).id());
        assertEquals(0x07, ack.toClient().get(1).id());
        assertEquals(0x03, ack.toClient().get(2).id());

        // Finish Configuration の応答（768：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x2C, finishAck.toClient().get(0).id());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Reader joinReader = new Reader(finishAck.toClient().get(0).payload());
        assertEquals(42, joinReader.readInt()); // entityId
    }

    // ------------------------------------------------------------------
    // 763クライアント → 内部768（設定フェーズを768クライアント役で消費）
    // ------------------------------------------------------------------

    @Test
    void join763Against768ConsumesConfigAs768Client() {
        ProxySession.HandshakeResult result = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 768);
        assertFalse(result.disconnect());
        assertFalse(result.relay());
        ProxySession session = result.session();
        assertEquals(763, session.clientProtocol());
        assertEquals(768, session.serverProtocol());

        // Login Start は763形式から768形式（bool を落とす）に直す。
        Outcome login = session.handleClientPacket(
                new MinecraftPacket(0x00, loginStart763("Steve")));
        assertEquals(1, login.toServer().size());
        assertArrayEquals(loginStart766("Steve"), login.toServer().get(0).payload());

        // Login Success（768形式）→ 763にはそのまま送る（strict bool は無い）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id());
        assertEquals(0x00, success.toServer().get(1).id());
        // 763クライアント向けにプロキシが合成する Client Information は
        // 768形式（9フィールド。particleStatus=0 付き）。8フィールドのまま
        // 送ると768のバニラが読み取り途中で切断する。
        byte[] synthInfo = success.toServer().get(1).payload();
        assertEquals(PlayTranslator.clientInformationPayload().length + 1, synthInfo.length);
        assertEquals(0, synthInfo[synthInfo.length - 1]);
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // Registry Data（768：0x07）はプロキシが消費し、内容を保持する。
        Outcome registry = session.handleServerPacket(
                new MinecraftPacket(0x07, registryData766Payload()));
        assertTrue(registry.toClient().isEmpty());
        assertTrue(session.registries().containsKey("minecraft:dimension_type"));

        // Add Resource Pack（768：0x09）は保持する。
        Outcome pack = session.handleServerPacket(
                new MinecraftPacket(0x09, addResourcePackPayload()));
        assertTrue(pack.toClient().isEmpty());
        assertEquals(1, session.pendingPackCount());

        // Finish Configuration（768：0x03）→ 応答（0x03）をバニラへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());

        // Join Game（768：0x2C）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2C, joinGame768Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());
        assertEquals(0x40, join.toClient().get(1).id());
        PlayTranslator.JoinGame763 parsed =
                PlayTranslator.parseJoinGame763(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals("minecraft:overworld", parsed.worldType());

        // Custom Report Details（0x0F）／Server Links（0x10）は763に無いため落とす。
        ProxySession.HandshakeResult result2 = handshake(
                763, 2, false, ProxyAuthConfig.defaults(), 768);
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
    void registryData767To768DamageTypeReorderedInSession() {
        ProxySession session = loginTo(768, 767);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:damage_type", DAMAGE_767)));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(DAMAGE_768, names);
        assertEquals(26, names.indexOf("mace_smash"));
        assertEquals(27, names.indexOf("magic"));
    }

    @Test
    void registryData768To767DamageTypeReorderedInSession() {
        ProxySession session = loginTo(767, 768);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:damage_type", DAMAGE_768)));
        assertEquals(1, registry.toClient().size());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(DAMAGE_767, names);
        assertEquals(25, names.indexOf("magic"));
        assertFalse(names.contains("ender_pearl"));
        assertFalse(names.contains("mace_smash"));
    }

    @Test
    void client768ConfigDropsNewPacketsForOldServer() {
        ProxySession session = loginTo(768, 765);
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

    @Test
    void config768KeepsCustomReportDetailsFor767Client() {
        ProxySession session = loginTo(767, 768);
        session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        // Custom Report Details（0x0F）／Server Links（0x10）は767にもあるため通す。
        Outcome report = session.handleServerPacket(
                new MinecraftPacket(0x0F, new byte[0]));
        assertEquals(1, report.toClient().size());
        assertEquals(0x0F, report.toClient().get(0).id());
        Outcome links = session.handleServerPacket(
                new MinecraftPacket(0x10, new byte[0]));
        assertEquals(1, links.toClient().size());
        assertEquals(0x10, links.toClient().get(0).id());
        // jukebox_song の Registry Data は767にもあるため落とさない。
        Outcome jukebox = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", List.of("13", "cat"))));
        assertEquals(1, jukebox.toClient().size());
        assertEquals(0x07, jukebox.toClient().get(0).id());
    }

    // ------------------------------------------------------------------
    // Client Information（Settings）の particleStatus 変換
    // ------------------------------------------------------------------

    @Test
    void clientInformationGainsParticleStatusToward768Server() {
        // 767クライアント → 内部768：設定フェーズの Settings（0x00）に
        // particleStatus=0（all）を付けて送る。付けないと 768 サーバーが
        // 読み取り途中で切断する。
        ProxySession session = loginTo(767, 768);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        // Finish Ack は 768 でも 0x03 のまま（764／765向けの付け替え 0x02 ではない）。
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
    void clientInformationLosesParticleStatusToward767Server() {
        // 768クライアント → 内部767：設定フェーズとプレイ中の Settings から
        // particleStatus を落として送る。
        ProxySession session = loginTo(768, 767);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess766Payload()));
        assertEquals(1, success.toClient().size());
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // 設定フェーズの Settings（0x00、particleStatus=2 付き）。
        byte[] info768 = appendParticleStatus(clientInformation766Payload(), 2);
        Outcome info = session.handleClientPacket(new MinecraftPacket(0x00, info768));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());
        assertArrayEquals(clientInformation766Payload(), info.toServer().get(0).payload());

        // プレイ状態まで進める（ack は既に送ったためここでは送らない）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:damage_type", DAMAGE_767)));
        assertFalse(registry.toClient().isEmpty());
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());

        // プレイ中の Settings（768：0x0C）→ 767（0x0A）。particleStatus を落とす。
        Outcome playInfo = session.handleClientPacket(new MinecraftPacket(0x0C, info768));
        assertEquals(1, playInfo.toServer().size());
        assertEquals(0x0A, playInfo.toServer().get(0).id());
        assertArrayEquals(clientInformation766Payload(), playInfo.toServer().get(0).payload());
    }

    /** Client Information ペイロードの末尾に particleStatus を付ける。 */
    private static byte[] appendParticleStatus(byte[] base, int particleStatus) {
        Writer writer = new Writer();
        writer.writeBytes(base);
        writer.writeVarInt(particleStatus);
        return writer.bytes();
    }
}
