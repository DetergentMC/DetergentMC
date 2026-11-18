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
 * 736⇔751の ProxySession 配線テスト（Stage B 統合時の残作業分）。
 * PlayTranslator の Join Game／Respawn／Client Information／Position 変換器が
 * ProxySession の736経路で呼ばれること、736サーバー→759クライアントに
 * フィールド差分パケットが届くこと、763以上サーバー→736クライアントの
 * Ping が落とされることを確かめる。
 * 736⇔751は恒等写像ではない：Join Game は dimensionCodec（736は
 * {"dimension": [...]} のみ）と dimension（文字列⇔NBT）と maxPlayers（u8⇔varint）、
 * Respawn は dimension（文字列⇔NBT）が書き換わる。Position／Client Information は
 * 恒等写像。753サーバー→736クライアントの Join Game／Respawn は754形式を経由するが、
 * 753⇔754は恒等写像のため元の736形式に戻る。Position だけ755形式を経由するが、
 * dismountVehicle の付加と除去が往復するため元の736形式に戻る。
 */
class ProxySession736bTest {

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
        if (clientProtocol == 736) {
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

    /** 736の Respawn。dimension は文字列（751は NBT）。copyMetadata=true。 */
    private static byte[] respawn736Payload() {
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

    /** 736の Synchronize Player Position（teleportId 付き。751と同一）。 */
    private static byte[] position736Payload() {
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7); // teleportId（736にもある）
        return writer.bytes();
    }

    /** 736の Client Information（6フィールド。751と同一）。 */
    private static byte[] clientInformation736Payload() {
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

    /** 実バニラ 1.16.1 の生キャプチャ Join Game（1,159バイト）。 */
    private static byte[] capturedJoinGame736() throws Exception {
        try (InputStream in = ProxySession736bTest.class.getResourceAsStream(
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

    /** 736サーバー発のパケットID（PacketIds の表から取る）。 */
    private static int id736(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 736).orElseThrow();
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（736サーバー→751クライアント。書き換わる）
    // ------------------------------------------------------------------

    @Test
    void joinGame736To751RewritesCodec() throws Exception {
        ProxySession session = loginTo(751, 736);
        loginSuccess(session);

        byte[] input = capturedJoinGame736();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x25, input));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        // dimensionCodec は751形式に書き換わる（dimension_type 4件＋バイオーム79件を補う）。
        assertArrayEquals(PlayTranslator.translateJoinGame736To751(input),
                join.toClient().get(0).payload());
        assertNotEquals(input.length, join.toClient().get(0).payload().length);
    }

    @Test
    void respawn736To751RewritesDimension() {
        ProxySession session = loginTo(751, 736);
        loginSuccess(session);

        byte[] input = respawn736Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x3a, input));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x39, respawn.toClient().get(0).id());
        // dimension が文字列から NBT の element に直る。
        assertArrayEquals(PlayTranslator.translateRespawn736To751(input),
                respawn.toClient().get(0).payload());
        assertNotEquals(input.length, respawn.toClient().get(0).payload().length);
    }

    @Test
    void position736To751IsIdentity() {
        ProxySession session = loginTo(751, 736);
        loginSuccess(session);

        byte[] input = position736Payload();
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x35, input));
        assertEquals(1, position.toClient().size());
        assertEquals(0x34, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition736To751(input),
                position.toClient().get(0).payload());
        assertArrayEquals(input, position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（751サーバー→736クライアント。書き換わる）
    // ------------------------------------------------------------------

    @Test
    void joinGame751To736RewritesCodec() {
        ProxySession session = loginTo(736, 751);
        loginSuccess(session);

        byte[] input = joinGame751Payload();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x24, input));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        // 751→736は dimensionCodec が736形式に書き換わる
        // （バイオームレジストリを落とし、dimension_type を {"dimension": [...]} に直す）。
        assertArrayEquals(PlayTranslator.translateJoinGame751To736(input),
                join.toClient().get(0).payload());
        assertNotEquals(input.length, join.toClient().get(0).payload().length);
    }

    @Test
    void respawn751To736RewritesDimension() {
        ProxySession session = loginTo(736, 751);
        loginSuccess(session);

        byte[] input = respawn751Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x39, input));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3a, respawn.toClient().get(0).id());
        // dimension が NBT の element から文字列に直る。
        assertArrayEquals(PlayTranslator.translateRespawn751To736(input),
                respawn.toClient().get(0).payload());
        assertNotEquals(input.length, respawn.toClient().get(0).payload().length);
    }

    @Test
    void position751To736IsIdentity() {
        ProxySession session = loginTo(736, 751);
        loginSuccess(session);

        byte[] input = position736Payload();
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x34, input));
        assertEquals(1, position.toClient().size());
        assertEquals(0x35, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition751To736(input),
                position.toClient().get(0).payload());
        assertArrayEquals(input, position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（753サーバー→736クライアント。連鎖）
    // ------------------------------------------------------------------

    @Test
    void joinGame753To736Chain() {
        ProxySession session = loginTo(736, 753);
        loginSuccess(session);

        // 753サーバーの Join Game（0x24）は754形式を経由して736（0x25）に直る
        // （753⇔754は恒等写像のため、実質751→736の書き換えと同じ）。
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
    void respawn753To736Chain() {
        ProxySession session = loginTo(736, 753);
        loginSuccess(session);

        // 753サーバーの Respawn（0x39）は754形式を経由して736（0x3a）に直る。
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
    void position753To736RoundTrips() {
        ProxySession session = loginTo(736, 753);
        loginSuccess(session);

        // 753サーバーの Synchronize Player Position（0x34）は755形式を経由するが、
        // dismountVehicle の付加と除去が往復するため元の736形式に戻る。
        byte[] input753 = position736Payload();
        byte[] input755 = PlayTranslator.translatePosition754To755(input753);
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x34, input753));
        assertEquals(1, position.toClient().size());
        assertEquals(0x35, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition755To754(input755),
                position.toClient().get(0).payload());
        assertArrayEquals(position736Payload(), position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Client Information（両方向＋763形式のフォールバック）
    // ------------------------------------------------------------------

    @Test
    void clientInformation736To751IsIdentity() {
        ProxySession session = loginTo(736, 751);
        loginSuccess(session);

        byte[] input = clientInformation736Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(PlayTranslator.translateClientInformation736To751(input),
                settings.toServer().get(0).payload());
        // 恒等写像のため末尾は付かない（754→755のときのような +1バイトは無い）。
        assertEquals(input.length, settings.toServer().get(0).payload().length);
        assertArrayEquals(input, settings.toServer().get(0).payload());
    }

    @Test
    void clientInformation751To736IsIdentity() {
        ProxySession session = loginTo(751, 736);
        loginSuccess(session);

        byte[] input751 = clientInformation736Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input751));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        // 751→736は恒等写像のため元の6フィールドに戻る
        // （handleClientPlay751 が753形式にして handleClientPlay753 に渡し、
        // そこの isServer736 ショートカットで753→751→736へ変換するため、755は経由しない）。
        assertArrayEquals(PlayTranslator.translateClientInformation751To736(input751),
                settings.toServer().get(0).payload());
        assertArrayEquals(clientInformation736Payload(),
                settings.toServer().get(0).payload());
    }

    @Test
    void clientInformation763To736FallsBackToPassthrough() {
        ProxySession session = loginTo(763, 736);
        loginSuccess(session);

        // 763形式（8フィールド）は変換器が読めないため素通しするが、
        // 736サーバーは先頭6フィールドだけ読むため余った末尾バイトは無害。
        // 763の Client Information は 0x08（実バニラ1.20.1 jarの登録順＋
        // PacketIds で確認。0x00 は Teleport Confirm で別パケット）。
        byte[] input763 = clientInformation763Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x08, input763));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(input763, settings.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 736サーバー→759クライアント（到達するもの）
    // ------------------------------------------------------------------

    @Test
    void fieldDiffPackets736To759Arrive() {
        ProxySession session = loginTo(759, 736);
        loginSuccess(session);

        // Explosion（736：0x1B）→759（0x19）。count i32→varint。
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
                id736(PacketIds.PacketName.PLAY_EXPLOSION), explosion.bytes()));
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

        // Entity Update Attributes（736：0x58）→759（0x65）。count i32→varint。
        Writer attributes = new Writer();
        attributes.writeVarInt(7);
        attributes.writeInt(1);
        attributes.writeString("generic.max_health");
        attributes.writeDouble(20.0);
        attributes.writeVarInt(0);
        Outcome attributesOut = session.handleServerPacket(new MinecraftPacket(
                id736(PacketIds.PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES),
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

        // Synchronize Player Position（736：0x35）→759（0x36）。
        // teleportId が通り、末尾に dismountVehicle=false が付く。
        Outcome positionOut = session.handleServerPacket(new MinecraftPacket(
                id736(PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION),
                position736Payload()));
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

        // Entity Destroy（736：[count=1][42]）→759（[count=1][42]）。
        // 736→751は恒等写像で通り、751→753→754→755→756→757→759の全段を通る。
        Writer destroy = new Writer();
        destroy.writeVarInt(1);
        destroy.writeVarInt(42);
        Outcome destroyOut = session.handleServerPacket(new MinecraftPacket(
                id736(PacketIds.PacketName.PLAY_ENTITY_DESTROY), destroy.bytes()));
        assertEquals(1, destroyOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_DESTROY),
                destroyOut.toClient().get(0).id());
        Reader destroyReader = new Reader(destroyOut.toClient().get(0).payload());
        assertEquals(1, destroyReader.readVarInt());
        assertEquals(42, destroyReader.readVarInt());
        assertEquals(0, destroyReader.remaining());
    }

    // ------------------------------------------------------------------
    // 736サーバー→759クライアント（落とすもの）
    // ------------------------------------------------------------------

    @Test
    void updateLight736To759Drops() {
        ProxySession session = loginTo(759, 736);
        loginSuccess(session);

        // Update Light は736⇔751の段では素通しだが、754→755の段で落とされる
        // （マスクの形式が違う。変換器なし）。
        Outcome light = session.handleServerPacket(new MinecraftPacket(
                id736(PacketIds.PacketName.PLAY_UPDATE_LIGHT), new byte[0]));
        assertEquals(0, light.toClient().size());
    }

    // ------------------------------------------------------------------
    // 763以上サーバー→736クライアントの Ping（落とす）
    // ------------------------------------------------------------------

    @Test
    void ping763To736Drops() {
        ProxySession session = loginTo(736, 763);
        loginSuccess(session);

        // Ping（763：0x32）は736クライアントには落とす（763形式 {id i32} を
        // 736の transaction 形式として誤読させないため）。
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
