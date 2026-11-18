// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 735⇔736の ProxySession 配線テスト（Stage B 統合時の残作業分）。
 * PlayTranslator の Join Game／Respawn／Client Information／Position 変換器が
 * ProxySession の735経路で呼ばれること、735サーバー→759クライアントに
 * フィールド差分パケットが届くこと、763以上サーバー→735クライアントの
 * Ping が落とされることを確かめる。
 * 735⇔736は恒等写像である：Join Game は dimensionCodec（735も736も
 * {"dimension": [...]} のみ）と dimension（文字列）と maxPlayers（u8）が同一、
 * Respawn は dimension（文字列）が同一。Position／Client Information も
 * 恒等写像。753サーバー→735クライアントの Join Game／Respawn は754形式を経由するが、
 * 753⇔754は恒等写像のため元の735形式に戻る。Position だけ755形式を経由するが、
 * dismountVehicle の付加と除去が往復するため元の735形式に戻る。
 */
class ProxySession735bTest {

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

    private static byte[] loginStart735(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    private static byte[] loginStart736(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    private static byte[] loginStart751(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    private static byte[] loginStart753(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    private static byte[] loginStart755(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    private static byte[] loginStart759(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        if (clientProtocol == 735) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart735("Steve")));
        } else if (clientProtocol == 736) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart736("Steve")));
        } else if (clientProtocol == 751) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart751("Steve")));
        } else if (clientProtocol == 753) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart753("Steve")));
        } else if (clientProtocol == 755) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart755("Steve")));
        } else if (clientProtocol == 759) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart759("Steve")));
        } else {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart763("Steve")));
        }
        return session;
    }

    private static void loginSuccess(ProxySession session) {
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
    }

    /** 最小の dimensionCodec（dimension_type 1件＋biome 1件）。751／753用。 */
    private static Tag.CompoundTag minimalCodec() {
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
        return new Tag.CompoundTag(root);
    }

    /** 751／753の dimension の値（最小：effects＋has_ceiling）。 */
    private static Tag.CompoundTag overworldDimensionValue() {
        return new Tag.CompoundTag(Map.of(
                "effects", new Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Tag.ByteTag((byte) 0)));
    }

    /** 751の Join Game（15フィールド。753と同一）。 */
    private static byte[] joinGame751Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        writer.writeByte(0);
        writer.writeByte(-1);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", minimalCodec());
        writer.writeNamedNbt("", overworldDimensionValue());
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 751の Respawn（copyMetadata=true。753と同一）。 */
    private static byte[] respawn751Payload() {
        Writer writer = new Writer();
        writer.writeNamedNbt("", overworldDimensionValue());
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 735の Respawn。dimension は文字列（736と同じ）。copyMetadata=true。 */
    private static byte[] respawn735Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 735の Synchronize Player Position（teleportId 付き。736と同一）。 */
    private static byte[] position735Payload() {
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7); // teleportId（735にもある）
        return writer.bytes();
    }

    /** 735の Client Information（6フィールド。736と同一）。 */
    private static byte[] clientInformation735Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(127);
        writer.writeVarInt(1);
        return writer.bytes();
    }

    /** 763の Client Information（8フィールド。enableServerListing 付き）。 */
    private static byte[] clientInformation763Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(127);
        writer.writeVarInt(1);
        writer.writeBoolean(true); // enableTextFiltering
        writer.writeBoolean(true); // enableServerListing
        return writer.bytes();
    }

    /** 実バニラ 1.16 の生キャプチャ Join Game（1,159バイト）。 */
    private static byte[] capturedJoinGame735() throws Exception {
        try (InputStream in = ProxySession735bTest.class.getResourceAsStream(
                "/cap735-joingame-1.16.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            return in.readAllBytes();
        }
    }

    /** 実バニラ 1.16.1 の生キャプチャ Join Game（1,159バイト）。 */
    private static byte[] capturedJoinGame736() throws Exception {
        try (InputStream in = ProxySession735bTest.class.getResourceAsStream(
                "/cap736-joingame-1-16-1.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            return in.readAllBytes();
        }
    }

    /** 759クライアント行きのパケットID（PacketIds の表から取る）。 */
    private static int id759(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 759).orElseThrow();
    }

    /** 735サーバー発のパケットID（PacketIds の表から取る）。 */
    private static int id735(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 735).orElseThrow();
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（735サーバー→736クライアント。恒等写像）
    // ------------------------------------------------------------------

    @Test
    void joinGame735To736IsIdentity() throws Exception {
        ProxySession session = loginTo(736, 735);
        loginSuccess(session);

        byte[] input = capturedJoinGame735();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x25, input));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        // 735⇔736は恒等写像のためペイロードは変わらない。
        assertArrayEquals(PlayTranslator.translateJoinGame735To736(input),
                join.toClient().get(0).payload());
        assertArrayEquals(input, join.toClient().get(0).payload());
    }

    @Test
    void respawn735To736IsIdentity() {
        ProxySession session = loginTo(736, 735);
        loginSuccess(session);

        byte[] input = respawn735Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x3a, input));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3a, respawn.toClient().get(0).id());
        // dimension は文字列のまま（恒等写像）。
        assertArrayEquals(PlayTranslator.translateRespawn735To736(input),
                respawn.toClient().get(0).payload());
        assertArrayEquals(input, respawn.toClient().get(0).payload());
    }

    @Test
    void position735To736IsIdentity() {
        ProxySession session = loginTo(736, 735);
        loginSuccess(session);

        byte[] input = position735Payload();
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x35, input));
        assertEquals(1, position.toClient().size());
        assertEquals(0x35, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition735To736(input),
                position.toClient().get(0).payload());
        assertArrayEquals(input, position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（736サーバー→735クライアント。恒等写像）
    // ------------------------------------------------------------------

    @Test
    void joinGame736To735IsIdentity() throws Exception {
        ProxySession session = loginTo(735, 736);
        loginSuccess(session);

        byte[] input = capturedJoinGame736();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x25, input));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        // 736→751→736の往復で元の736形式に戻る（735⇔736は同一形式）。
        assertArrayEquals(
                PlayTranslator.translateJoinGame751To736(
                        PlayTranslator.translateJoinGame736To751(input)),
                join.toClient().get(0).payload());
        assertArrayEquals(input, join.toClient().get(0).payload());
    }

    @Test
    void respawn736To735IsIdentity() {
        ProxySession session = loginTo(735, 736);
        loginSuccess(session);

        byte[] input = respawn735Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x3a, input));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3a, respawn.toClient().get(0).id());
        // 736→751→736の往復で元の形式に戻る（dimension は文字列）。
        assertArrayEquals(
                PlayTranslator.translateRespawn751To736(
                        PlayTranslator.translateRespawn736To751(input)),
                respawn.toClient().get(0).payload());
        assertArrayEquals(input, respawn.toClient().get(0).payload());
    }

    @Test
    void position736To735IsIdentity() {
        ProxySession session = loginTo(735, 736);
        loginSuccess(session);

        byte[] input = position735Payload();
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x35, input));
        assertEquals(1, position.toClient().size());
        assertEquals(0x35, position.toClient().get(0).id());
        assertArrayEquals(
                PlayTranslator.translatePosition751To736(
                        PlayTranslator.translatePosition736To751(input)),
                position.toClient().get(0).payload());
        assertArrayEquals(input, position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（753サーバー→735クライアント。連鎖）
    // ------------------------------------------------------------------

    @Test
    void joinGame753To735Chain() {
        ProxySession session = loginTo(735, 753);
        loginSuccess(session);

        // 753サーバーの Join Game（0x24）は754形式を経由して735（0x25）に直る
        // （753⇔754は恒等写像のため、実質751→736の書き換えと同じ。735⇔736は同一形式）。
        byte[] input753 = joinGame751Payload();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x24, input753));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        assertArrayEquals(
                PlayTranslator.translateJoinGame751To736(
                        PlayTranslator.translateJoinGame753To751(input753)),
                join.toClient().get(0).payload());
        assertNotEquals(input753.length, join.toClient().get(0).payload().length);
    }

    @Test
    void respawn753To735Chain() {
        ProxySession session = loginTo(735, 753);
        loginSuccess(session);

        // 753サーバーの Respawn（0x39）は754形式を経由して735（0x3a）に直る。
        byte[] input753 = respawn751Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x39, input753));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3a, respawn.toClient().get(0).id());
        assertArrayEquals(
                PlayTranslator.translateRespawn751To736(
                        PlayTranslator.translateRespawn753To751(input753)),
                respawn.toClient().get(0).payload());
        assertNotEquals(input753.length, respawn.toClient().get(0).payload().length);
    }

    @Test
    void position753To735RoundTrips() {
        ProxySession session = loginTo(735, 753);
        loginSuccess(session);

        // 753サーバーの Synchronize Player Position（0x34）は755形式を経由するが、
        // dismountVehicle の付加と除去が往復するため元の735形式に戻る。
        byte[] input753 = position735Payload();
        byte[] input755 = PlayTranslator.translatePosition754To755(input753);
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x34, input753));
        assertEquals(1, position.toClient().size());
        assertEquals(0x35, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition755To754(input755),
                position.toClient().get(0).payload());
        assertArrayEquals(position735Payload(), position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Client Information（両方向＋763形式のフォールバック）
    // ------------------------------------------------------------------

    @Test
    void clientInformation735To736IsIdentity() {
        ProxySession session = loginTo(735, 736);
        loginSuccess(session);

        byte[] input = clientInformation735Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(PlayTranslator.translateClientInformation735To736(input),
                settings.toServer().get(0).payload());
        // 恒等写像のため末尾は付かない（754→755のときのような +1バイトは無い）。
        assertEquals(input.length, settings.toServer().get(0).payload().length);
        assertArrayEquals(input, settings.toServer().get(0).payload());
    }

    @Test
    void clientInformation736To735IsIdentity() {
        ProxySession session = loginTo(736, 735);
        loginSuccess(session);

        byte[] input736 = clientInformation735Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input736));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        // 736→735は恒等写像のため元の6フィールドに戻る
        // （handleClientPlay736 が751形式にして handleClientPlay751 に渡し、
        // さらに753形式にして handleClientPlay753 に渡し、そこの isServer735
        // ショートカットで753→751→736→735へ変換するため、755は経由しない）。
        assertArrayEquals(PlayTranslator.translateClientInformation736To735(input736),
                settings.toServer().get(0).payload());
        assertArrayEquals(clientInformation735Payload(),
                settings.toServer().get(0).payload());
    }

    @Test
    void clientInformation763To735FallsBackToPassthrough() {
        ProxySession session = loginTo(763, 735);
        loginSuccess(session);

        // 763形式（8フィールド）は変換器が読めないため素通しするが、
        // 735サーバーは先頭6フィールドだけ読むため余った末尾バイトは無害。
        // 763の Client Information は 0x08（実バニラ1.20.1 jarの登録順＋
        // PacketIds で確認。0x00 は Teleport Confirm で別パケット）。
        byte[] input763 = clientInformation763Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x08, input763));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(input763, settings.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 735サーバー→759クライアント（到達するもの）
    // ------------------------------------------------------------------

    @Test
    void fieldDiffPackets735To759Arrive() {
        ProxySession session = loginTo(759, 735);
        loginSuccess(session);

        // Explosion（735：0x1B）→759（0x19）。count i32→varint。
        Writer explosion = new Writer();
        explosion.writeFloat(1.0f);
        explosion.writeFloat(2.0f);
        explosion.writeFloat(3.0f);
        explosion.writeFloat(4.0f);
        explosion.writeInt(1);
        explosion.writeByte(1);
        explosion.writeByte(2);
        explosion.writeByte(3);
        explosion.writeFloat(0.0f);
        explosion.writeFloat(0.0f);
        explosion.writeFloat(0.0f);
        Outcome explosionOut = session.handleServerPacket(new MinecraftPacket(
                id735(PacketIds.PacketName.PLAY_EXPLOSION), explosion.bytes()));
        assertEquals(1, explosionOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_EXPLOSION),
                explosionOut.toClient().get(0).id());
        Reader explosionReader = new Reader(explosionOut.toClient().get(0).payload());
        assertEquals(1.0f, explosionReader.readFloat());
        assertEquals(2.0f, explosionReader.readFloat());
        assertEquals(3.0f, explosionReader.readFloat());
        assertEquals(4.0f, explosionReader.readFloat());
        assertEquals(1, explosionReader.readVarInt());
        assertEquals(1, explosionReader.readByte());
        assertEquals(2, explosionReader.readByte());
        assertEquals(3, explosionReader.readByte());
        assertEquals(0, explosionReader.remaining() - 12); // motion 3 float

        // Entity Update Attributes（735：0x58）→759（0x65）。count i32→varint。
        Writer attributes = new Writer();
        attributes.writeVarInt(7);
        attributes.writeInt(1);
        attributes.writeString("generic.max_health");
        attributes.writeDouble(20.0);
        attributes.writeVarInt(0);
        Outcome attributesOut = session.handleServerPacket(new MinecraftPacket(
                id735(PacketIds.PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES),
                attributes.bytes()));
        assertEquals(1, attributesOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES),
                attributesOut.toClient().get(0).id());
        Reader attributesReader = new Reader(attributesOut.toClient().get(0).payload());
        assertEquals(7, attributesReader.readVarInt());
        assertEquals(1, attributesReader.readVarInt());
        assertEquals("generic.max_health", attributesReader.readString());
        assertEquals(20.0, attributesReader.readDouble());
        assertEquals(0, attributesReader.readVarInt());
        assertEquals(0, attributesReader.remaining());

        // Synchronize Player Position（735：0x35）→759（0x36）。
        // teleportId が通り、末尾に dismountVehicle=false が付く。
        Outcome positionOut = session.handleServerPacket(new MinecraftPacket(
                id735(PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION),
                position735Payload()));
        assertEquals(1, positionOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION),
                positionOut.toClient().get(0).id());
        Reader positionReader = new Reader(positionOut.toClient().get(0).payload());
        assertEquals(1.0, positionReader.readDouble());
        assertEquals(2.0, positionReader.readDouble());
        assertEquals(3.0, positionReader.readDouble());
        assertEquals(0.0f, positionReader.readFloat());
        assertEquals(0.0f, positionReader.readFloat());
        assertEquals(0, positionReader.readByte());
        assertEquals(7, positionReader.readVarInt()); // teleportId
        assertFalse(positionReader.readBoolean()); // dismountVehicle
        assertEquals(0, positionReader.remaining());

        // Entity Destroy（735：[count=1][42]）→759（[count=1][42]）。
        // 735→736は恒等写像で通り、736→751→753→754→755→756→757→759の全段を通る。
        Writer destroy = new Writer();
        destroy.writeVarInt(1);
        destroy.writeVarInt(42);
        Outcome destroyOut = session.handleServerPacket(new MinecraftPacket(
                id735(PacketIds.PacketName.PLAY_ENTITY_DESTROY), destroy.bytes()));
        assertEquals(1, destroyOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_DESTROY),
                destroyOut.toClient().get(0).id());
        Reader destroyReader = new Reader(destroyOut.toClient().get(0).payload());
        assertEquals(1, destroyReader.readVarInt());
        assertEquals(42, destroyReader.readVarInt());
        assertEquals(0, destroyReader.remaining());
    }

    // ------------------------------------------------------------------
    // 735サーバー→759クライアント（落とすもの）
    // ------------------------------------------------------------------

    @Test
    void updateLight735To759Drops() {
        ProxySession session = loginTo(759, 735);
        loginSuccess(session);

        // Update Light は735⇔736の段では素通しだが、754→755の段で落とされる
        // （マスクの形式が違う。変換器なし）。
        Outcome light = session.handleServerPacket(new MinecraftPacket(
                id735(PacketIds.PacketName.PLAY_UPDATE_LIGHT), new byte[0]));
        assertEquals(0, light.toClient().size());
    }

    // ------------------------------------------------------------------
    // 763以上サーバー→735クライアントの Ping（落とす）
    // ------------------------------------------------------------------

    @Test
    void ping763To735Drops() {
        ProxySession session = loginTo(735, 763);
        loginSuccess(session);

        // Ping（763：0x32）は735クライアントには落とす（763形式 {id i32} を
        // 735の transaction 形式として誤読させないため）。
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x32, new byte[]{0, 0, 0, 42}));
        assertEquals(0, ping.toClient().size());
    }

    @Test
    void ping763To755StillArrives() {
        ProxySession session = loginTo(755, 763);
        loginSuccess(session);

        // 比較：755クライアントには Ping が届く（0x30）。
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x32, new byte[]{0, 0, 0, 42}));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x30, ping.toClient().get(0).id());
        assertArrayEquals(new byte[]{0, 0, 0, 42}, ping.toClient().get(0).payload());
    }
}
