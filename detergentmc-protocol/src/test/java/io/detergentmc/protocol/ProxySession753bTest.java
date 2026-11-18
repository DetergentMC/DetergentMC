// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 753⇔754の ProxySession 配線テスト（Stage B 統合時の残作業分）。
 * PlayTranslator の Join Game／Respawn／Client Information／Position 変換器が
 * ProxySession の753経路で呼ばれること、753サーバー→759クライアントに
 * フィールド差分パケットが届くこと、763以上サーバー→753クライアントの
 * Ping が落とされることを確かめる。
 * 753⇔754は恒等写像のため、753→754方向の Join Game／Respawn／Position／
 * Client Information はペイロードが変わらない。一方で753→755方向では
 * 754→755の段で書き換わる（dimensionCodec／dimension／dismountVehicle／
 * disableTextFiltering）。両方を確かめる。
 * 754サーバー→753クライアントの Join Game／Respawn は754の処理の直接返信分岐で
 * そのまま送る（755形式を経由しない）。Position だけ755形式を経由するが、
 * dismountVehicle の付加と除去が往復するため元の753形式に戻る。
 */
class ProxySession753bTest {

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

    private static byte[] loginStart753(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    private static byte[] loginStart754(String name) {
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
        if (clientProtocol == 753) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart753("Steve")));
        } else if (clientProtocol == 754) {
            session.handleClientPacket(new MinecraftPacket(0x00, loginStart754("Steve")));
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

    /** 最小の dimensionCodec（dimension_type 1件＋biome 1件）。 */
    private static Nbt.Tag.CompoundTag minimalCodec() {
        Map<String, Nbt.Tag> root = new java.util.LinkedHashMap<>();
        root.put("minecraft:dimension_type", new Nbt.Tag.CompoundTag(Map.of(
                "type", new Nbt.Tag.StringTag("minecraft:dimension_type"),
                "value", new Nbt.Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Nbt.Tag.CompoundTag(Map.of(
                                "name", new Nbt.Tag.StringTag("minecraft:overworld"),
                                "id", new Nbt.Tag.IntTag(0),
                                "element", new Nbt.Tag.CompoundTag(Map.of()))))))));
        root.put("minecraft:worldgen/biome", new Nbt.Tag.CompoundTag(Map.of(
                "type", new Nbt.Tag.StringTag("minecraft:worldgen/biome"),
                "value", new Nbt.Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Nbt.Tag.CompoundTag(Map.of(
                                "name", new Nbt.Tag.StringTag("minecraft:plains"),
                                "id", new Nbt.Tag.IntTag(0),
                                "element", new Nbt.Tag.CompoundTag(Map.of()))))))));
        return new Nbt.Tag.CompoundTag(root);
    }

    /** 753／754の dimension の値（最小：effects＋has_ceiling）。 */
    private static Nbt.Tag.CompoundTag overworldDimensionValue() {
        return new Nbt.Tag.CompoundTag(Map.of(
                "effects", new Nbt.Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Nbt.Tag.ByteTag((byte) 0)));
    }

    /** 753の Join Game（15フィールド。754と同一）。 */
    private static byte[] joinGame753Payload() {
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

    /** 753の Respawn（copyMetadata=true。754と同一）。 */
    private static byte[] respawn753Payload() {
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

    /** 753の Synchronize Player Position（teleportId 付き。754と同一）。 */
    private static byte[] position753Payload() {
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7); // teleportId（753にもある）
        return writer.bytes();
    }

    /** 753の Client Information（6フィールド。754と同一）。 */
    private static byte[] clientInformation753Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(127);
        writer.writeVarInt(1);
        return writer.bytes();
    }

    /** 754の Client Information（6フィールド。753と同一）。 */
    private static byte[] clientInformation754Payload() {
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

    /** 759クライアント行きのパケットID（PacketIds の表から取る）。 */
    private static int id759(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 759).orElseThrow();
    }

    /** 753サーバー発のパケットID（PacketIds の表から取る）。 */
    private static int id753(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 753).orElseThrow();
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（753サーバー→754クライアント。恒等写像）
    // ------------------------------------------------------------------

    @Test
    void joinGame753To754IsIdentity() {
        ProxySession session = loginTo(754, 753);
        loginSuccess(session);

        byte[] input = joinGame753Payload();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x24, input));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        // dimensionCodec は書き換わらない（恒等写像）。
        assertArrayEquals(PlayTranslator.translateJoinGame753To754(input),
                join.toClient().get(0).payload());
        assertArrayEquals(input, join.toClient().get(0).payload());
    }

    @Test
    void respawn753To754IsIdentity() {
        ProxySession session = loginTo(754, 753);
        loginSuccess(session);

        byte[] input = respawn753Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x39, input));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x39, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn753To754(input),
                respawn.toClient().get(0).payload());
        assertArrayEquals(input, respawn.toClient().get(0).payload());
    }

    @Test
    void position753To754IsIdentity() {
        ProxySession session = loginTo(754, 753);
        loginSuccess(session);

        byte[] input = position753Payload();
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x34, input));
        assertEquals(1, position.toClient().size());
        assertEquals(0x34, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition753To754(input),
                position.toClient().get(0).payload());
        assertArrayEquals(input, position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（753サーバー→755クライアント。754→755段で書き換わる）
    // ------------------------------------------------------------------

    @Test
    void joinGame753To755RewritesCodec() {
        ProxySession session = loginTo(755, 753);
        loginSuccess(session);

        byte[] input = joinGame753Payload();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x24, input));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        // 753→754は恒等写像だが、754→755の段で dimensionCodec が755形式に書き換わる。
        assertArrayEquals(PlayTranslator.translateJoinGame754To755(input),
                join.toClient().get(0).payload());
        assertNotEquals(input.length, join.toClient().get(0).payload().length);
    }

    @Test
    void respawn753To755RewritesDimension() {
        ProxySession session = loginTo(755, 753);
        loginSuccess(session);

        byte[] input = respawn753Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x39, input));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn754To755(input),
                respawn.toClient().get(0).payload());
        assertNotEquals(input.length, respawn.toClient().get(0).payload().length);
    }

    @Test
    void position753To755AddsDismountVehicle() {
        ProxySession session = loginTo(755, 753);
        loginSuccess(session);

        byte[] input = position753Payload();
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x34, input));
        assertEquals(1, position.toClient().size());
        assertEquals(0x38, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition754To755(input),
                position.toClient().get(0).payload());
        // 末尾に dismountVehicle=false が1バイト付く。
        assertEquals(input.length + 1, position.toClient().get(0).payload().length);
        assertEquals(0, position.toClient().get(0).payload()[
                position.toClient().get(0).payload().length - 1]);
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（754サーバー→753クライアント。恒等写像）
    // ------------------------------------------------------------------

    @Test
    void joinGame754To753IsIdentity() {
        ProxySession session = loginTo(753, 754);
        loginSuccess(session);

        // 754サーバーの Join Game（0x24）は753クライアントにそのまま送る
        // （handleServerJoinGame754 の直接返信分岐に is753() があるため。
        // 755形式を経由しない）。
        byte[] input754 = joinGame753Payload();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x24, input754));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame754To753(input754),
                join.toClient().get(0).payload());
        assertArrayEquals(input754, join.toClient().get(0).payload());
    }

    @Test
    void respawn754To753IsIdentity() {
        ProxySession session = loginTo(753, 754);
        loginSuccess(session);

        // 754サーバーの Respawn（0x39）は753クライアントにそのまま送る
        // （handleServerPlayFrom754 の直接返信分岐に is753() があるため）。
        byte[] input754 = respawn753Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x39, input754));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x39, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn754To753(input754),
                respawn.toClient().get(0).payload());
        assertArrayEquals(input754, respawn.toClient().get(0).payload());
    }

    @Test
    void position754To753RoundTrips() {
        ProxySession session = loginTo(753, 754);
        loginSuccess(session);

        // 754サーバーの Synchronize Player Position（0x34）は755形式を経由するが、
        // dismountVehicle の付加と除去が往復するため元の753形式に戻る。
        byte[] input754 = position753Payload();
        byte[] input755 = PlayTranslator.translatePosition754To755(input754);
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x34, input754));
        assertEquals(1, position.toClient().size());
        assertEquals(0x34, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition755To754(input755),
                position.toClient().get(0).payload());
        assertArrayEquals(position753Payload(), position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Client Information（両方向＋763形式のフォールバック）
    // ------------------------------------------------------------------

    @Test
    void clientInformation753To754IsIdentity() {
        ProxySession session = loginTo(753, 754);
        loginSuccess(session);

        byte[] input = clientInformation753Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(PlayTranslator.translateClientInformation753To754(input),
                settings.toServer().get(0).payload());
        // 恒等写像のため末尾は付かない（754→755のときのような +1バイトは無い）。
        assertEquals(input.length, settings.toServer().get(0).payload().length);
        assertArrayEquals(input, settings.toServer().get(0).payload());
    }

    @Test
    void clientInformation754To753IsIdentity() {
        ProxySession session = loginTo(754, 753);
        loginSuccess(session);

        byte[] input754 = clientInformation754Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input754));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        // 754→755で末尾に disableTextFiltering=false が付くが、
        // 755→754の段で剥がれ、754→753は恒等写像のため元の6フィールドに戻る。
        assertArrayEquals(PlayTranslator.translateClientInformation754To753(input754),
                settings.toServer().get(0).payload());
        assertArrayEquals(clientInformation753Payload(),
                settings.toServer().get(0).payload());
    }

    @Test
    void clientInformation763To753FallsBackToPassthrough() {
        ProxySession session = loginTo(763, 753);
        loginSuccess(session);

        // 763形式（8フィールド）は変換器が読めないため素通しするが、
        // 753サーバーは先頭6フィールドだけ読むため余った末尾バイトは無害。
        // 763の Client Information は 0x08（実バニラ1.20.1 jarの登録順＋
        // PacketIds で確認。0x00 は Teleport Confirm で別パケット）。
        byte[] input763 = clientInformation763Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x08, input763));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(input763, settings.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 753サーバー→759クライアント（到達するもの）
    // ------------------------------------------------------------------

    @Test
    void fieldDiffPackets753To759Arrive() {
        ProxySession session = loginTo(759, 753);
        loginSuccess(session);

        // Explosion（753：0x1B）→759（0x19）。count i32→varint。
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
                id753(PacketIds.PacketName.PLAY_EXPLOSION), explosion.bytes()));
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

        // Entity Update Attributes（753：0x58）→759（0x65）。count i32→varint。
        Writer attributes = new Writer();
        attributes.writeVarInt(7);
        attributes.writeInt(1);
        attributes.writeString("generic.max_health");
        attributes.writeDouble(20.0);
        attributes.writeVarInt(0);
        Outcome attributesOut = session.handleServerPacket(new MinecraftPacket(
                id753(PacketIds.PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES),
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

        // Synchronize Player Position（753：0x34）→759（0x36）。
        // teleportId が通り、末尾に dismountVehicle=false が付く。
        Outcome positionOut = session.handleServerPacket(new MinecraftPacket(
                id753(PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION),
                position753Payload()));
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

        // Entity Destroy（753：[count=1][42]）→759（[count=1][42]）。
        // 753→754は恒等写像で通り、754→755→756→757→759の全段を通る。
        Writer destroy = new Writer();
        destroy.writeVarInt(1);
        destroy.writeVarInt(42);
        Outcome destroyOut = session.handleServerPacket(new MinecraftPacket(
                id753(PacketIds.PacketName.PLAY_ENTITY_DESTROY), destroy.bytes()));
        assertEquals(1, destroyOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_DESTROY),
                destroyOut.toClient().get(0).id());
        Reader destroyReader = new Reader(destroyOut.toClient().get(0).payload());
        assertEquals(1, destroyReader.readVarInt());
        assertEquals(42, destroyReader.readVarInt());
        assertEquals(0, destroyReader.remaining());
    }

    // ------------------------------------------------------------------
    // 753サーバー→759クライアント（落とすもの）
    // ------------------------------------------------------------------

    @Test
    void updateLight753To759Drops() {
        ProxySession session = loginTo(759, 753);
        loginSuccess(session);

        // Update Light は753⇔754の段では素通しだが、754→755の段で落とされる
        // （マスクの形式が違う。変換器なし）。
        Outcome light = session.handleServerPacket(new MinecraftPacket(
                id753(PacketIds.PacketName.PLAY_UPDATE_LIGHT), new byte[0]));
        assertEquals(0, light.toClient().size());
    }

    // ------------------------------------------------------------------
    // 763以上サーバー→753クライアントの Ping（落とす）
    // ------------------------------------------------------------------

    @Test
    void ping763To753Drops() {
        ProxySession session = loginTo(753, 763);
        loginSuccess(session);

        // Ping（763：0x32）は753クライアントには落とす（763形式 {id i32} を
        // 753の transaction 形式として誤読させないため）。
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
