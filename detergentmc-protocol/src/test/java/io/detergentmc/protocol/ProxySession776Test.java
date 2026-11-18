// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * 776（26.2）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession773Test} と同じ段取りで、775↔776の4件のペイロード差分
 * （Join Game の onlineMode、Login Success の sessionId、Teams の並べ替え、
 * SpectatorAction の entityId±1）を確かめる。775→776のパケットID変更は
 * 0件（play toServer 0x3e が spectate_entity から spectator_action へ
 * 改名しただけ）なので、Join Game・Teams・Respawn のIDは両方とも同じ
 * （Join Game 0x31、Teams 0x6D、Respawn 0x52）。
 */
class ProxySession776Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** 776の Login Success に付く sessionId（テスト用に固定）。 */
    private static final UUID SESSION_UUID =
            UUID.fromString("33333333-4444-5555-6666-777777777777");

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

    /** 766／767／768／769／772／773／775／776の Login Start：名前＋UUID必須。 */
    private static byte[] loginStart766(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 765／768／769／772／773／775の Login Success（strict bool なし）。 */
    private static byte[] loginSuccess765Payload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 776の Login Success：765形式＋末尾に sessionId（UUID）16バイト。 */
    private static byte[] loginSuccess776Payload() {
        Writer writer = new Writer();
        writer.writeBytes(loginSuccess765Payload());
        writer.writeUuid(SESSION_UUID);
        return writer.bytes();
    }

    /** 775の Join Game（773と同じ形式。774／775でペイロード変更なし）。 */
    private static byte[] joinGame775Payload() {
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
        writer.writeBoolean(false); // deathLocation なし
        writer.writeVarInt(0); // portalCooldown
        writer.writeVarInt(63); // seaLevel
        writer.writeBoolean(false); // enforcesSecureChat
        return writer.bytes();
    }

    /** 776の Join Game：775形式＋末尾に onlineMode（boolean）1バイト。 */
    private static byte[] joinGame776Payload(boolean onlineMode) {
        Writer writer = new Writer();
        writer.writeBytes(joinGame775Payload());
        writer.writeBoolean(onlineMode);
        return writer.bytes();
    }

    /** 775／776の Client Information（設定フェーズ。particleStatus=0 付き9フィールド）。 */
    private static byte[] clientInformation775Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7F);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeBoolean(true);
        writer.writeVarInt(0); // particleStatus
        return writer.bytes();
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

    /** Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** Registry Data パケットを読み、名前列を返す。 */
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

    /** 772／773／775／776のジュークボックスの歌21件（lava_chicken は9番）。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /**
     * 775形式の Teams ペイロード（mode 0＝作成）。
     * 775のワイヤ順：displayName, options(1バイト), nametagVisibility,
     * collisionRule, color(ChatFormatting の varint), playerPrefix, playerSuffix。
     */
    private static byte[] teams775Payload(String team, int color, List<String> players) {
        Writer writer = new Writer();
        writer.writeString(team);
        writer.writeByte(0); // mode 0＝作成
        writer.writeString("{\"text\":\"Red\"}"); // displayName
        writer.writeByte(0x01); // options（friendlyFire 許可）
        writer.writeString("always"); // nametagVisibility
        writer.writeString("always"); // collisionRule
        writer.writeVarInt(color); // color（ChatFormatting の序数）
        writer.writeString("{\"text\":\"[R]\"}"); // playerPrefix
        writer.writeString("{\"text\":\"\"}"); // playerSuffix
        writer.writeVarInt(players.size());
        for (String player : players) {
            writer.writeString(player);
        }
        return writer.bytes();
    }

    /**
     * 776形式の Teams ペイロード（mode 0＝作成）。
     * 776のワイヤ順：displayName, playerPrefix, playerSuffix, nametagVisibility,
     * collisionRule, color(Optional&lt;TeamColor&gt; の varint、0=無し・それ以外は序数+1),
     * options(1バイト)。
     */
    private static byte[] teams776Payload(String team, int color, List<String> players) {
        Writer writer = new Writer();
        writer.writeString(team);
        writer.writeByte(0); // mode 0＝作成
        writer.writeString("{\"text\":\"Red\"}"); // displayName
        writer.writeString("{\"text\":\"[R]\"}"); // playerPrefix
        writer.writeString("{\"text\":\"\"}"); // playerSuffix
        writer.writeString("always"); // nametagVisibility
        writer.writeString("always"); // collisionRule
        writer.writeVarInt(color); // color（0=無し・それ以外は序数+1）
        writer.writeByte(0x01); // options（friendlyFire 許可）
        writer.writeVarInt(players.size());
        for (String player : players) {
            writer.writeString(player);
        }
        return writer.bytes();
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(
            int clientProtocol, int serverProtocol, boolean onlineMode) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, onlineMode, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        session.handleClientPacket(new MinecraftPacket(0x00, loginStart766("Steve")));
        return session;
    }

    /**
     * ログイン→設定フェーズを終え、プレイ状態まで進めたセッションを返す。
     * Login Success はサーバーの形式で送る（776サーバーなら sessionId 付き）。
     */
    private static ProxySession playSession(
            int clientProtocol, int serverProtocol, boolean onlineMode) {
        ProxySession session = loginTo(clientProtocol, serverProtocol, onlineMode);
        byte[] loginSuccess = serverProtocol == 776
                ? loginSuccess776Payload() : loginSuccess765Payload();
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（0x03）で両方とも設定フェーズに入る。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, ack.toServer().size());
        assertEquals(0x03, ack.toServer().get(0).id());
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());

        // Client Information（0x00、9フィールド）はそのままバニラへ。
        Outcome info = session.handleClientPacket(
                new MinecraftPacket(0x00, clientInformation775Payload()));
        assertEquals(1, info.toServer().size());
        assertEquals(0x00, info.toServer().get(0).id());

        // Registry Data（0x07、ジュークボックス21件）。
        // 775サーバー→776クライアントは bounce を挿入して22件（bounce は4番、
        // lava_chicken は9番→10番）。776サーバー→775クライアントは
        // bounce が無いため21件のまま（lava_chicken は9番）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        if (serverProtocol == 775) {
            assertEquals(22, names.size());
            assertEquals(4, names.indexOf("bounce"));
            assertEquals(10, names.indexOf("lava_chicken"));
        } else {
            assertEquals(21, names.size());
            assertEquals(9, names.indexOf("lava_chicken"));
        }

        // Finish Configuration（0x03）→ クライアントへ。
        Outcome finish = session.handleServerPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finish.toClient().size());
        assertEquals(0x03, finish.toClient().get(0).id());

        // クライアントの応答（0x03）で両方ともプレイ状態に入る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toServer().size());
        assertEquals(0x03, finishAck.toServer().get(0).id());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        return session;
    }

    // ------------------------------------------------------------------
    // Login Success：776の sessionId の落とし／付け足し
    // ------------------------------------------------------------------

    @Test
    void loginSuccess776Against775DropsSessionId() {
        ProxySession session = loginTo(775, 776, false);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess776Payload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        assertArrayEquals(loginSuccess765Payload(), success.toClient().get(0).payload());
    }

    @Test
    void loginSuccess775Against776AppendsSessionId() {
        ProxySession session = loginTo(776, 775, false);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        byte[] out = success.toClient().get(0).payload();
        byte[] base = loginSuccess765Payload();
        assertEquals(base.length + 16, out.length);
        for (int i = 0; i < base.length; i++) {
            assertEquals(base[i], out[i]);
        }
    }

    // ------------------------------------------------------------------
    // Join Game：776の onlineMode の落とし／付け足し
    // ------------------------------------------------------------------

    @Test
    void join776Against775DropsOnlineMode() {
        ProxySession session = playSession(775, 776, true);
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame776Payload(true)));
        assertEquals(1, join.toClient().size());
        assertEquals(0x31, join.toClient().get(0).id());
        assertArrayEquals(joinGame775Payload(), join.toClient().get(0).payload());
    }

    @Test
    void join776Against775DropsOnlineModeFalse() {
        ProxySession session = playSession(775, 776, false);
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame776Payload(false)));
        assertEquals(1, join.toClient().size());
        assertEquals(0x31, join.toClient().get(0).id());
        assertArrayEquals(joinGame775Payload(), join.toClient().get(0).payload());
    }

    @Test
    void join775Against776AppendsOnlineMode() {
        ProxySession session = playSession(776, 775, true);
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame775Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x31, join.toClient().get(0).id());
        assertArrayEquals(joinGame776Payload(true), join.toClient().get(0).payload());
    }

    @Test
    void join775Against776AppendsOnlineModeFalse() {
        ProxySession session = playSession(776, 775, false);
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame775Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x31, join.toClient().get(0).id());
        assertArrayEquals(joinGame776Payload(false), join.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // SpectatorAction：0の破棄、entityId±1
    // ------------------------------------------------------------------

    @Test
    void spectatorAction776To775DropsZero() {
        ProxySession session = playSession(776, 775, false);
        Writer writer = new Writer();
        writer.writeVarInt(0); // 観戦停止
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x3e, writer.bytes()));
        assertTrue(out.toServer().isEmpty());
    }

    @Test
    void spectatorAction776To775TranslatesEntityId() {
        ProxySession session = playSession(776, 775, false);
        Writer writer = new Writer();
        writer.writeVarInt(6); // entityId 5 + 1
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x3e, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x3e, out.toServer().get(0).id());
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spectatorAction775To776TranslatesEntityId() {
        ProxySession session = playSession(775, 776, false);
        Writer writer = new Writer();
        writer.writeVarInt(5); // entityId 5
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x3e, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x3e, out.toServer().get(0).id());
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(6, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Teams：775↔776の並べ替えと color
    // ------------------------------------------------------------------

    @Test
    void teams775To776ReordersAndMapsColor() {
        ProxySession session = playSession(776, 775, false);
        // 775の RED（ChatFormatting の序数 12）→ 776は 13（序数+1）。
        Outcome teams = session.handleServerPacket(new MinecraftPacket(0x6D,
                teams775Payload("red", 12, List.of("Steve"))));
        assertEquals(1, teams.toClient().size());
        assertEquals(0x6D, teams.toClient().get(0).id());
        assertArrayEquals(
                teams776Payload("red", 13, List.of("Steve")),
                teams.toClient().get(0).payload());
    }

    @Test
    void teams776To775ReordersAndMapsColor() {
        ProxySession session = playSession(775, 776, false);
        // 776の 13（序数+1）→ 775の RED（序数 12）。
        Outcome teams = session.handleServerPacket(new MinecraftPacket(0x6D,
                teams776Payload("red", 13, List.of("Steve"))));
        assertEquals(1, teams.toClient().size());
        assertEquals(0x6D, teams.toClient().get(0).id());
        assertArrayEquals(
                teams775Payload("red", 12, List.of("Steve")),
                teams.toClient().get(0).payload());
    }

    @Test
    void teams776To775ColorNoneBecomesReset() {
        ProxySession session = playSession(775, 776, false);
        // 776の 0（色なし）→ 775の RESET（序数 21）。
        Outcome teams = session.handleServerPacket(new MinecraftPacket(0x6D,
                teams776Payload("red", 0, List.of("Steve"))));
        assertEquals(1, teams.toClient().size());
        assertEquals(0x6D, teams.toClient().get(0).id());
        assertArrayEquals(
                teams775Payload("red", 21, List.of("Steve")),
                teams.toClient().get(0).payload());
    }

    @Test
    void teams775To776ColorResetBecomesNone() {
        ProxySession session = playSession(776, 775, false);
        // 775の RESET（序数 21）→ 776の 0（色なし）。
        Outcome teams = session.handleServerPacket(new MinecraftPacket(0x6D,
                teams775Payload("red", 21, List.of("Steve"))));
        assertEquals(1, teams.toClient().size());
        assertEquals(0x6D, teams.toClient().get(0).id());
        assertArrayEquals(
                teams776Payload("red", 0, List.of("Steve")),
                teams.toClient().get(0).payload());
    }
}
