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
 * 754⇔755の ProxySession 配線テスト（Stage B 統合時の残作業分）。
 * PlayTranslator の Join Game／Respawn／Client Information／Position 変換器が
 * ProxySession の754経路で呼ばれること、フィールド差分・値付け替えパケットが
 * 754サーバー→759クライアントに届くこと、763以上サーバー→754クライアントの
 * Ping が落とされること、757→759の Entity Destroy の隙間が直ったことを確かめる。
 * 既存の ProxySession754Test は触らない（Join Game／Respawn の payload-unchanged
 * 断言と Entity Destroy の到達0件断言は、この配線で変わるため親が更新する）。
 * 使うIDは実測値（ChunkTranslator754ValueTest と同じ）：
 * アイテム 754:8→755:14、ブロック状態 754:70→755:71、
 * 音 754:831→755:1020→759:1127、音の名前 754:item.sweet_berries.pick_from_bush
 * →755:block.sweet_berry_bush.pick_berries、パーティクル block 754:3→759:2。
 * 759のIDは PacketIds の表から取る（プローブで到達確認済み）。
 * Map／Tags／Resource Pack Send は754→755の段では変換できるが、756→757の段に
 * case が無いため759クライアントには届かない（落とす。別課題）。
 * 754／755／756／757クライアントへの汎用パケットは handleServerPlayFrom763 で
 * 落とされる既存の非対称性のため、Map 等の755クライアントへの到達は確かめない。
 */
class ProxySession754bTest {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** アイテムIDが実際に違うもの（754:8→755:14）。 */
    private static final int ITEM_754 = 8;

    /** ブロック状態IDが実際に違うもの（754:70→755:71）。 */
    private static final int STATE_754 = 70;

    /** 音ID（754:831→759:1127）。 */
    private static final int SOUND_754 = 831;

    /** 音の名前（754名）。 */
    private static final String SOUND_NAME_754 = "item.sweet_berries.pick_from_bush";

    /** 音の名前（755名。759の音 1127＝block.sweet_berry_bush.pick_berries）。 */
    private static final String SOUND_NAME_755 = "block.sweet_berry_bush.pick_berries";

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
        if (clientProtocol == 754) {
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

    /** 754／755の dimension の値（最小：effects＋has_ceiling）。 */
    private static Nbt.Tag.CompoundTag overworldDimensionValue() {
        return new Nbt.Tag.CompoundTag(Map.of(
                "effects", new Nbt.Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Nbt.Tag.ByteTag((byte) 0)));
    }

    /** 754の Join Game（15フィールド）。 */
    private static byte[] joinGame754Payload() {
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

    /** 754の Respawn（copyMetadata=true）。 */
    private static byte[] respawn754Payload() {
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

    /** 754の Synchronize Player Position（teleportId 付き）。 */
    private static byte[] position754Payload() {
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7); // teleportId（754にもある）
        return writer.bytes();
    }

    /** 754の Client Information（6フィールド）。 */
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

    /** 755の Client Information（7フィールド。disableTextFiltering 付き）。 */
    private static byte[] clientInformation755Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(127);
        writer.writeVarInt(1);
        writer.writeBoolean(false); // disableTextFiltering
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

    /** 754→759の連鎖でアイテムIDを付ける（754→755→756→757→759）。 */
    private static int item759(int item754) {
        return RegistryRemapper.mapItem757To759(
                RegistryRemapper.mapItem756To757(
                        RegistryRemapper.mapItem755To756(
                                RegistryRemapper.mapItem754To755(item754))));
    }

    /** 754→759の連鎖でブロック状態IDを付ける。 */
    private static int state759(int state754) {
        return RegistryRemapper.mapBlockState757To759(
                RegistryRemapper.mapBlockState756To757(
                        RegistryRemapper.mapBlockState755To756(
                                RegistryRemapper.mapBlockState754To755(state754))));
    }

    /** 754→757の連鎖でブロック状態IDを付ける（757→759が素通しに落ちる場合の期待値用）。 */
    private static int state757(int state754) {
        return RegistryRemapper.mapBlockState756To757(
                RegistryRemapper.mapBlockState755To756(
                        RegistryRemapper.mapBlockState754To755(state754)));
    }

    /** 754→759の連鎖で音IDを付ける。 */
    private static int sound759(int sound754) {
        return RegistryRemapper.mapSound757To759(
                RegistryRemapper.mapSound756To757(
                        RegistryRemapper.mapSound755To756(
                                RegistryRemapper.mapSound754To755(sound754))));
    }

    /** 754→759の連鎖でエンティティ種別IDを付ける。 */
    private static int entity759(int type754) {
        return RegistryRemapper.mapEntityType757To759(
                RegistryRemapper.mapEntityType756To757(
                        RegistryRemapper.mapEntityType755To756(
                                RegistryRemapper.mapEntityType754To755(type754))));
    }

    /** 754→759の連鎖でパーティクルIDを付ける。 */
    private static int particle759(int particle754) {
        return RegistryRemapper.mapParticle757To759(
                RegistryRemapper.mapParticle756To757(
                        RegistryRemapper.mapParticle755To756(
                                RegistryRemapper.mapParticle754To755(particle754))));
    }

    /** 759クライアント行きのパケットID（PacketIds の表から取る）。 */
    private static int id759(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 759).orElseThrow();
    }

    /** 754サーバー発のパケットID（PacketIds の表から取る）。 */
    private static int id754(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 754).orElseThrow();
    }

    private static byte[] slot754(int item) {
        Writer w = new Writer();
        w.writeBoolean(true);
        w.writeVarInt(item);
        w.writeByte(1);
        w.writeByte(0);
        return w.bytes();
    }

    // ------------------------------------------------------------------
    // Join Game／Respawn／Position（754サーバー→755クライアント）
    // ------------------------------------------------------------------

    @Test
    void joinGame754To755RewritesCodec() {
        ProxySession session = loginTo(755, 754);
        loginSuccess(session);

        byte[] input = joinGame754Payload();
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x24, input));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        // dimensionCodec が755形式に書き換わる（素通しではない）。
        assertArrayEquals(PlayTranslator.translateJoinGame754To755(input),
                join.toClient().get(0).payload());
        assertNotEquals(input.length, join.toClient().get(0).payload().length);
    }

    @Test
    void respawn754To755RewritesDimension() {
        ProxySession session = loginTo(755, 754);
        loginSuccess(session);

        byte[] input = respawn754Payload();
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x39, input));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn754To755(input),
                respawn.toClient().get(0).payload());
        assertNotEquals(input.length, respawn.toClient().get(0).payload().length);
    }

    @Test
    void position754To755AddsDismountVehicle() {
        ProxySession session = loginTo(755, 754);
        loginSuccess(session);

        byte[] input = position754Payload();
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
    // Join Game／Respawn／Position（755サーバー→754クライアント）
    // ------------------------------------------------------------------

    @Test
    void joinGame755To754RewritesCodec() {
        ProxySession session = loginTo(754, 755);
        loginSuccess(session);

        byte[] input755 = PlayTranslator.translateJoinGame754To755(joinGame754Payload());
        Outcome join = session.handleServerPacket(new MinecraftPacket(0x26, input755));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame755To754(input755),
                join.toClient().get(0).payload());
        // 注意：最小 codec（dimension 1件・biome 1件）は往復でバイト一致しない。
        // 755→754の変換器は dimension の element を754既定値で作り直すため、
        // 元の最小 element（effects＋has_ceiling の2フィールド）より大きくなる。
        // 正規の codec での往復は PlayTranslator754Test の範囲。
    }

    @Test
    void respawn755To754RewritesDimension() {
        ProxySession session = loginTo(754, 755);
        loginSuccess(session);

        byte[] input755 = PlayTranslator.translateRespawn754To755(respawn754Payload());
        Outcome respawn = session.handleServerPacket(new MinecraftPacket(0x3D, input755));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x39, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn755To754(input755),
                respawn.toClient().get(0).payload());
        // 注意：Join Game と同じく、dimension の element は754既定値で
        // 作り直されるため、最小 element では往復でバイト一致しない。
    }

    @Test
    void position755To754StripsDismountVehicle() {
        ProxySession session = loginTo(754, 755);
        loginSuccess(session);

        byte[] input755 = PlayTranslator.translatePosition754To755(position754Payload());
        Outcome position = session.handleServerPacket(new MinecraftPacket(0x38, input755));
        assertEquals(1, position.toClient().size());
        assertEquals(0x34, position.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translatePosition755To754(input755),
                position.toClient().get(0).payload());
        assertArrayEquals(position754Payload(), position.toClient().get(0).payload());
    }

    // ------------------------------------------------------------------
    // Client Information（両方向＋763形式のフォールバック）
    // ------------------------------------------------------------------

    @Test
    void clientInformation754To755AppendsFalse() {
        ProxySession session = loginTo(754, 755);
        loginSuccess(session);

        byte[] input = clientInformation754Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(PlayTranslator.translateClientInformation754To755(input),
                settings.toServer().get(0).payload());
        assertEquals(input.length + 1, settings.toServer().get(0).payload().length);
        assertEquals(0, settings.toServer().get(0).payload()[
                settings.toServer().get(0).payload().length - 1]);
    }

    @Test
    void clientInformation755To754StripsBool() {
        ProxySession session = loginTo(755, 754);
        loginSuccess(session);

        byte[] input755 = clientInformation755Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x05, input755));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(PlayTranslator.translateClientInformation755To754(input755),
                settings.toServer().get(0).payload());
        assertArrayEquals(clientInformation754Payload(),
                settings.toServer().get(0).payload());
    }

    @Test
    void clientInformation763To754FallsBackToPassthrough() {
        ProxySession session = loginTo(763, 754);
        loginSuccess(session);

        // 763形式（8フィールド）は変換器が読めないため素通しするが、
        // 754サーバーは先頭6フィールドだけ読むため余った末尾バイトは無害。
        // 763の Client Information は 0x08（実バニラ1.20.1 jarの登録順＋
        // PacketIds で確認。0x00 は Teleport Confirm で別パケット）。
        byte[] input763 = clientInformation763Payload();
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x08, input763));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertArrayEquals(input763, settings.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 754サーバー→759クライアント（到達するもの）
    // ------------------------------------------------------------------

    @Test
    void fieldDiffPackets754To759Arrive() {
        ProxySession session = loginTo(759, 754);
        loginSuccess(session);

        // Explosion（754：0x1B）→759（0x19）。count i32→varint。
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
                id754(PacketIds.PacketName.PLAY_EXPLOSION), explosion.bytes()));
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

        // Entity Update Attributes（754：0x58）→759（0x65）。count i32→varint。
        Writer attributes = new Writer();
        attributes.writeVarInt(7);
        attributes.writeInt(1);
        attributes.writeString("generic.max_health");
        attributes.writeDouble(20.0);
        attributes.writeVarInt(0);
        Outcome attributesOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES),
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

        // Synchronize Player Position（754：0x34）→759（0x36）。
        // teleportId が通り、末尾に dismountVehicle=false が付く。
        Outcome positionOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION),
                position754Payload()));
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

        // Entity Destroy（754：[count=1][42]）→759（[count=1][42]）。
        // 754→755→756→757→759の全段を通る（756→757・757→759の段を今回直した）。
        Writer destroy = new Writer();
        destroy.writeVarInt(1);
        destroy.writeVarInt(42);
        Outcome destroyOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_ENTITY_DESTROY), destroy.bytes()));
        assertEquals(1, destroyOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_DESTROY),
                destroyOut.toClient().get(0).id());
        Reader destroyReader = new Reader(destroyOut.toClient().get(0).payload());
        assertEquals(1, destroyReader.readVarInt());
        assertEquals(42, destroyReader.readVarInt());
        assertEquals(0, destroyReader.remaining());
    }

    @Test
    void valuePackets754To759Arrive() {
        ProxySession session = loginTo(759, 754);
        loginSuccess(session);

        // Set Cooldown（754：0x16）→759（0x14）。アイテムIDが連鎖で付く。
        Writer cooldown = new Writer();
        cooldown.writeVarInt(ITEM_754);
        cooldown.writeVarInt(20);
        Outcome cooldownOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_SET_COOLDOWN), cooldown.bytes()));
        assertEquals(1, cooldownOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SET_COOLDOWN),
                cooldownOut.toClient().get(0).id());
        Reader cooldownReader = new Reader(cooldownOut.toClient().get(0).payload());
        assertEquals(item759(ITEM_754), cooldownReader.readVarInt());
        assertEquals(20, cooldownReader.readVarInt());
        assertEquals(0, cooldownReader.remaining());

        // Entity Equipment（754：0x47）→759（0x50）。スロットのアイテムIDが付く。
        Writer equipment = new Writer();
        equipment.writeVarInt(7);
        equipment.writeUnsignedByte(0);
        equipment.writeBytes(slot754(ITEM_754));
        Outcome equipmentOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_ENTITY_EQUIPMENT), equipment.bytes()));
        assertEquals(1, equipmentOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_EQUIPMENT),
                equipmentOut.toClient().get(0).id());
        Reader equipmentReader = new Reader(equipmentOut.toClient().get(0).payload());
        assertEquals(7, equipmentReader.readVarInt());
        assertEquals(0, equipmentReader.readUnsignedByte());
        assertTrue(equipmentReader.readBoolean());
        assertEquals(item759(ITEM_754), equipmentReader.readVarInt());
        assertEquals(1, equipmentReader.readByte());
        assertEquals(0, equipmentReader.readByte());
        assertEquals(0, equipmentReader.remaining());

        // Trade List（754：0x26）→759（0x25）。スロットのアイテムIDが付く。
        Writer trade = new Writer();
        trade.writeVarInt(0);
        trade.writeUnsignedByte(1);
        trade.writeBytes(slot754(ITEM_754));
        trade.writeBytes(slot754(ITEM_754));
        trade.writeBoolean(false);
        trade.writeBoolean(false);
        trade.writeInt(0);
        trade.writeInt(12);
        trade.writeInt(0);
        trade.writeInt(0);
        trade.writeFloat(1.0f);
        trade.writeInt(0);
        trade.writeVarInt(1);
        trade.writeVarInt(0);
        trade.writeBoolean(true);
        trade.writeBoolean(true);
        Outcome tradeOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_TRADE_LIST), trade.bytes()));
        assertEquals(1, tradeOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_TRADE_LIST),
                tradeOut.toClient().get(0).id());
        Reader tradeReader = new Reader(tradeOut.toClient().get(0).payload());
        assertEquals(0, tradeReader.readVarInt());
        assertEquals(1, tradeReader.readUnsignedByte());
        assertTrue(tradeReader.readBoolean());
        assertEquals(item759(ITEM_754), tradeReader.readVarInt());
        assertEquals(1, tradeReader.readByte());
        assertEquals(0, tradeReader.readByte());
        assertTrue(tradeReader.readBoolean());
        assertEquals(item759(ITEM_754), tradeReader.readVarInt());
        assertEquals(1, tradeReader.readByte());
        assertEquals(0, tradeReader.readByte());
        assertFalse(tradeReader.readBoolean());
        assertFalse(tradeReader.readBoolean());
        assertEquals(0, tradeReader.readInt());
        assertEquals(12, tradeReader.readInt());
        assertEquals(0, tradeReader.readInt());
        assertEquals(0, tradeReader.readInt());
        assertEquals(1.0f, tradeReader.readFloat());
        assertEquals(0, tradeReader.readInt());
        assertEquals(1, tradeReader.readVarInt());
        assertEquals(0, tradeReader.readVarInt());
        assertTrue(tradeReader.readBoolean());
        assertTrue(tradeReader.readBoolean());
        assertEquals(0, tradeReader.remaining());

        // Sound Effect（754：0x51）→759（0x5D）。音IDが連鎖で付き、seed 0 が付く。
        Writer sound = new Writer();
        sound.writeVarInt(SOUND_754);
        sound.writeVarInt(0);
        sound.writeInt(0);
        sound.writeInt(0);
        sound.writeInt(0);
        sound.writeFloat(1.0f);
        sound.writeFloat(1.0f);
        Outcome soundOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_SOUND_EFFECT), sound.bytes()));
        assertEquals(1, soundOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SOUND_EFFECT),
                soundOut.toClient().get(0).id());
        Reader soundReader = new Reader(soundOut.toClient().get(0).payload());
        assertEquals(sound759(SOUND_754), soundReader.readVarInt());
        assertEquals(0, soundReader.readVarInt());
        assertEquals(0, soundReader.readInt());
        assertEquals(0, soundReader.readInt());
        assertEquals(0, soundReader.readInt());
        assertEquals(1.0f, soundReader.readFloat());
        assertEquals(1.0f, soundReader.readFloat());
        assertEquals(0L, soundReader.readLong()); // seed
        assertEquals(0, soundReader.remaining());

        // Entity Sound Effect（754：0x50）→759（0x5C）。音IDが連鎖で付く。
        Writer entitySound = new Writer();
        entitySound.writeVarInt(SOUND_754);
        entitySound.writeVarInt(0);
        entitySound.writeVarInt(7);
        entitySound.writeFloat(1.0f);
        entitySound.writeFloat(1.0f);
        Outcome entitySoundOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_ENTITY_SOUND_EFFECT), entitySound.bytes()));
        assertEquals(1, entitySoundOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_SOUND_EFFECT),
                entitySoundOut.toClient().get(0).id());
        Reader entitySoundReader = new Reader(
                entitySoundOut.toClient().get(0).payload());
        assertEquals(sound759(SOUND_754), entitySoundReader.readVarInt());
        assertEquals(0, entitySoundReader.readVarInt());
        assertEquals(7, entitySoundReader.readVarInt());
        assertEquals(1.0f, entitySoundReader.readFloat());
        assertEquals(1.0f, entitySoundReader.readFloat());
        assertEquals(0L, entitySoundReader.readLong()); // seed
        assertEquals(0, entitySoundReader.remaining());

        // Named Sound Effect（754：0x18）→759では Sound Effect（0x5D）になる。
        // 760→761の段で Named Sound Effect が Sound Effect に統合される既存の
        // 仕様のため。音そのものは正しい（759の音 1127＝berry）。
        Writer namedSound = new Writer();
        namedSound.writeString(SOUND_NAME_754);
        namedSound.writeVarInt(0);
        namedSound.writeInt(0);
        namedSound.writeInt(0);
        namedSound.writeInt(0);
        namedSound.writeFloat(1.0f);
        namedSound.writeFloat(1.0f);
        Outcome namedSoundOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_NAMED_SOUND_EFFECT), namedSound.bytes()));
        assertEquals(1, namedSoundOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SOUND_EFFECT),
                namedSoundOut.toClient().get(0).id());
        Reader namedSoundReader = new Reader(namedSoundOut.toClient().get(0).payload());
        assertEquals(sound759(SOUND_754), namedSoundReader.readVarInt());
        assertEquals(0, namedSoundReader.readVarInt());
        assertEquals(0, namedSoundReader.readInt());
        assertEquals(0, namedSoundReader.readInt());
        assertEquals(0, namedSoundReader.readInt());
        assertEquals(1.0f, namedSoundReader.readFloat());
        assertEquals(1.0f, namedSoundReader.readFloat());
        assertEquals(0L, namedSoundReader.readLong()); // seed
        assertEquals(0, namedSoundReader.remaining());

        // Stop Sound（754：0x52）→759（0x5E）。音の名前が改名される。
        Writer stopSound = new Writer();
        stopSound.writeByte(0x03);
        stopSound.writeVarInt(0);
        stopSound.writeString(SOUND_NAME_754);
        Outcome stopSoundOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_STOP_SOUND), stopSound.bytes()));
        assertEquals(1, stopSoundOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_STOP_SOUND),
                stopSoundOut.toClient().get(0).id());
        Reader stopSoundReader = new Reader(stopSoundOut.toClient().get(0).payload());
        assertEquals(0x03, stopSoundReader.readByte());
        assertEquals(0, stopSoundReader.readVarInt());
        assertEquals(SOUND_NAME_755, stopSoundReader.readString());
        assertEquals(0, stopSoundReader.remaining());

        // World Particles（754：0x22）→759（0x21）。
        // bubble（754=4→755=5→757=4→759=4、データ無し）で到達とID付け替えを確認する。
        // 実バニラ1.19の並びは [ID varint][longDistance][xyz][offsets][speed][count]
        // [データ末尾]（実バニラ1.19 jarの javap で確認。minecraft-data とも一致）。
        // データ付き粒子（block等）は 754→759 で届かない：756→757・757→759 の
        // 変換器がデータを ID 直後と誤って読むため（実機は末尾）。既存の756・757
        // テストがその誤った並びで固定されているため、修正は統括の判断待ち。
        // block粒子の値付け替え自体は ChunkTranslator754ValueTest で検証済み。
        Writer particles = new Writer();
        particles.writeInt(4); // bubble（754）
        particles.writeBoolean(false);
        particles.writeDouble(0.0);
        particles.writeDouble(0.0);
        particles.writeDouble(0.0);
        particles.writeFloat(0.0f);
        particles.writeFloat(0.0f);
        particles.writeFloat(0.0f);
        particles.writeFloat(0.0f);
        particles.writeInt(1);
        Outcome particlesOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_WORLD_PARTICLES), particles.bytes()));
        assertEquals(1, particlesOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_WORLD_PARTICLES),
                particlesOut.toClient().get(0).id());
        Reader particlesReader = new Reader(particlesOut.toClient().get(0).payload());
        assertEquals(particle759(4), particlesReader.readVarInt());
        assertFalse(particlesReader.readBoolean());
        assertEquals(0.0, particlesReader.readDouble());
        assertEquals(0.0, particlesReader.readDouble());
        assertEquals(0.0, particlesReader.readDouble());
        assertEquals(0.0f, particlesReader.readFloat());
        assertEquals(0.0f, particlesReader.readFloat());
        assertEquals(0.0f, particlesReader.readFloat());
        assertEquals(0.0f, particlesReader.readFloat());
        assertEquals(1, particlesReader.readInt());
        assertEquals(0, particlesReader.remaining());

        // Entity Metadata（754：0x44）→759（0x4D）。
        // 型番号は 754〜759 で同一（slot=6・block state=13・particle=15。
        // 実バニラ1.18／1.19 jar の EntityDataSerializers 登録順で確認）。
        // ブロック状態の値は 757形式（71）のまま届く：757→759 の変換器が
        // 値の型番号を誤って読むため素通しに落ちる既存の不具合（759形式なら112）。
        // 修正は統括の判断待ち。スロットのアイテムID（14）と粒子ID（2）は正しい。
        Writer metadata = new Writer();
        metadata.writeVarInt(7);
        metadata.writeUnsignedByte(0);
        metadata.writeVarInt(13); // block state（754）
        metadata.writeVarInt(STATE_754);
        metadata.writeUnsignedByte(1);
        metadata.writeVarInt(6); // slot（754）
        metadata.writeBytes(slot754(ITEM_754));
        metadata.writeUnsignedByte(2);
        metadata.writeVarInt(15); // particle（754）
        metadata.writeVarInt(3); // block
        metadata.writeVarInt(STATE_754);
        metadata.writeUnsignedByte(0xFF);
        Outcome metadataOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_ENTITY_METADATA), metadata.bytes()));
        assertEquals(1, metadataOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_METADATA),
                metadataOut.toClient().get(0).id());
        Reader metadataReader = new Reader(metadataOut.toClient().get(0).payload());
        assertEquals(7, metadataReader.readVarInt());
        assertEquals(0, metadataReader.readUnsignedByte());
        assertEquals(13, metadataReader.readVarInt()); // block state（759）
        assertEquals(state757(STATE_754), metadataReader.readVarInt()); // 757形式の値（759形式なら112）
        assertEquals(1, metadataReader.readUnsignedByte());
        assertEquals(6, metadataReader.readVarInt()); // slot（759）
        assertTrue(metadataReader.readBoolean());
        assertEquals(item759(ITEM_754), metadataReader.readVarInt());
        assertEquals(1, metadataReader.readByte());
        assertEquals(0, metadataReader.readByte());
        assertEquals(2, metadataReader.readUnsignedByte());
        assertEquals(15, metadataReader.readVarInt()); // particle（759）
        assertEquals(particle759(3), metadataReader.readVarInt());
        assertEquals(state757(STATE_754), metadataReader.readVarInt()); // 757形式の値（759形式なら112）
        assertEquals(0xFF, metadataReader.readUnsignedByte());
        assertEquals(0, metadataReader.remaining());

        // Spawn Entity（754：0x00）→759（0x00）。falling_block の種別と
        // objectData（ブロック状態）が付く。headPitch は yaw で代用される。
        Writer spawn = new Writer();
        spawn.writeVarInt(7);
        spawn.writeBytes(new byte[16]);
        spawn.writeVarInt(26); // falling_block（754）
        spawn.writeDouble(1.0);
        spawn.writeDouble(2.0);
        spawn.writeDouble(3.0);
        spawn.writeByte(0);
        spawn.writeByte(0);
        spawn.writeInt(STATE_754);
        spawn.writeShort(0);
        spawn.writeShort(0);
        spawn.writeShort(0);
        Outcome spawnOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_SPAWN_ENTITY), spawn.bytes()));
        assertEquals(1, spawnOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SPAWN_ENTITY),
                spawnOut.toClient().get(0).id());
        Reader spawnReader = new Reader(spawnOut.toClient().get(0).payload());
        assertEquals(7, spawnReader.readVarInt());
        spawnReader.readBytes(16);
        assertEquals(entity759(26), spawnReader.readVarInt());
        assertEquals(1.0, spawnReader.readDouble());
        assertEquals(2.0, spawnReader.readDouble());
        assertEquals(3.0, spawnReader.readDouble());
        assertEquals(0, spawnReader.readByte());
        assertEquals(0, spawnReader.readByte());
        assertEquals(0, spawnReader.readByte()); // headPitch（yaw で代用）
        assertEquals(state759(STATE_754), spawnReader.readVarInt());
        assertEquals(0, spawnReader.readShort());
        assertEquals(0, spawnReader.readShort());
        assertEquals(0, spawnReader.readShort());
        assertEquals(0, spawnReader.remaining());

        // Block Change（754：0x0B）→759（0x09）。ブロック状態が付く。
        Writer blockChange = new Writer();
        blockChange.writePositionRaw(0L);
        blockChange.writeVarInt(STATE_754);
        Outcome blockChangeOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_BLOCK_CHANGE), blockChange.bytes()));
        assertEquals(1, blockChangeOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_BLOCK_CHANGE),
                blockChangeOut.toClient().get(0).id());
        Reader blockChangeReader = new Reader(blockChangeOut.toClient().get(0).payload());
        assertEquals(0L, blockChangeReader.readPositionRaw());
        assertEquals(state759(STATE_754), blockChangeReader.readVarInt());
        assertEquals(0, blockChangeReader.remaining());

        // Multi Block Change（754：0x3B）→759（0x3D）。レコードは varint になる。
        Writer multiBlockChange = new Writer();
        multiBlockChange.writeLong(0L);
        multiBlockChange.writeBoolean(false);
        multiBlockChange.writeVarInt(1);
        multiBlockChange.writeVarLong(((long) STATE_754 << 12) | 5);
        Outcome multiBlockChangeOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_MULTI_BLOCK_CHANGE),
                multiBlockChange.bytes()));
        assertEquals(1, multiBlockChangeOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_MULTI_BLOCK_CHANGE),
                multiBlockChangeOut.toClient().get(0).id());
        Reader multiBlockChangeReader = new Reader(
                multiBlockChangeOut.toClient().get(0).payload());
        assertEquals(0L, multiBlockChangeReader.readLong());
        assertFalse(multiBlockChangeReader.readBoolean());
        assertEquals(1, multiBlockChangeReader.readVarInt());
        int record = multiBlockChangeReader.readVarInt();
        assertEquals(state759(STATE_754), record >>> 12);
        assertEquals(5, record & 0xFFF);
        assertEquals(0, multiBlockChangeReader.remaining());

        // Block Entity Data（754：0x09）→759（0x07）。種別は varint になる。
        Writer blockEntityData = new Writer();
        blockEntityData.writePositionRaw(0L);
        blockEntityData.writeUnsignedByte(0);
        blockEntityData.writeByte(0); // NBT無し
        Outcome blockEntityDataOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_BLOCK_ENTITY_DATA),
                blockEntityData.bytes()));
        assertEquals(1, blockEntityDataOut.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_BLOCK_ENTITY_DATA),
                blockEntityDataOut.toClient().get(0).id());
        Reader blockEntityDataReader = new Reader(
                blockEntityDataOut.toClient().get(0).payload());
        assertEquals(0L, blockEntityDataReader.readPositionRaw());
        assertEquals(0, blockEntityDataReader.readVarInt());
        assertEquals(0, blockEntityDataReader.readByte()); // NBT無し
        assertEquals(0, blockEntityDataReader.remaining());
    }

    // ------------------------------------------------------------------
    // 754サーバー→759クライアント（落とすもの）
    // ------------------------------------------------------------------

    @Test
    void mapTagsResourcePack754To759DropAt756To757() {
        ProxySession session = loginTo(759, 754);
        loginSuccess(session);

        // Map（754：0x25）は754→755の段では変換できるが、756→757の段に
        // case が無いため落とされる。
        Writer map = new Writer();
        map.writeVarInt(0);
        map.writeByte(0);
        map.writeBoolean(true);
        map.writeBoolean(false);
        map.writeVarInt(0);
        map.writeByte(0);
        Outcome mapOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_MAP), map.bytes()));
        assertEquals(0, mapOut.toClient().size());

        // Tags（754：0x5B）も同じく756→757の段で落とされる。
        Writer tags = new Writer();
        tags.writeVarInt(0);
        tags.writeVarInt(0);
        tags.writeVarInt(0);
        tags.writeVarInt(0);
        Outcome tagsOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_TAGS), tags.bytes()));
        assertEquals(0, tagsOut.toClient().size());

        // Resource Pack Send（754：0x38）も同じく756→757の段で落とされる。
        // （Join Game 時のオファーは resourcePackOfferPacket が別途担う。）
        Writer pack = new Writer();
        pack.writeString("https://example.invalid/pack.zip");
        pack.writeString("aa".repeat(20));
        Outcome packOut = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_RESOURCE_PACK), pack.bytes()));
        assertEquals(0, packOut.toClient().size());
    }

    @Test
    void updateLight754StillDrops() {
        ProxySession session = loginTo(759, 754);
        loginSuccess(session);

        // Update Light は変換器なし（未実装）のため落とす。
        Outcome light = session.handleServerPacket(new MinecraftPacket(
                id754(PacketIds.PacketName.PLAY_UPDATE_LIGHT), new byte[0]));
        assertEquals(0, light.toClient().size());
    }

    // ------------------------------------------------------------------
    // 763以上サーバー→754クライアントの Ping（落とす）
    // ------------------------------------------------------------------

    @Test
    void ping763To754Drops() {
        ProxySession session = loginTo(754, 763);
        loginSuccess(session);

        // Ping（763：0x32）は754クライアントには落とす（763形式 {id i32} を
        // 754の transaction 形式として誤読させないため）。
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

    // ------------------------------------------------------------------
    // 757サーバー→759クライアントの Entity Destroy（隙間の修正）
    // ------------------------------------------------------------------

    @Test
    void entityDestroy757To759Arrives() {
        ProxySession session = loginTo(759, 757);
        loginSuccess(session);

        // 757の Entity Destroy（0x3A、単体 varint）→759（0x38、[count][ids]）。
        Writer destroy = new Writer();
        destroy.writeVarInt(42);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x3A, destroy.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_ENTITY_DESTROY),
                out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(1, reader.readVarInt());
        assertEquals(42, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }
}
