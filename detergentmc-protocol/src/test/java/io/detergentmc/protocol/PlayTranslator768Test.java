// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 767（1.21／1.21.1）⇔768（1.21.2／1.21.3）の変換テスト。
 * 期待値は minecraft-data の proto-1.21.1.yml／proto-1.21.3.yml と
 * ViaVersion のレジストリから計算したもの（推測ではない）。
 */
class PlayTranslator768Test {

    // ------------------------------------------------------------------
    // 名称表（検証用の独立したコピー）
    // ------------------------------------------------------------------

    private static final List<String> ENCHANTMENT_767 = List.of(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
            "breach", "channeling", "density", "depth_strider", "efficiency",
            "feather_falling", "fire_aspect", "fire_protection", "flame", "fortune",
            "frost_walker", "impaling", "infinity", "knockback", "looting", "loyalty",
            "luck_of_the_sea", "lure", "mending", "multishot", "piercing", "power",
            "projectile_protection", "protection", "punch", "quick_charge", "respiration",
            "riptide", "sharpness", "silk_touch", "smite", "soul_speed", "sweeping_edge",
            "swift_sneak", "thorns", "unbreaking", "vanishing_curse", "wind_burst");

    /** 768のエンチャントは767と同一の42件・同じ並び。 */
    private static final List<String> ENCHANTMENT_768 = ENCHANTMENT_767;

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

    /** 768のダメージ種別は767の47件に ender_pearl（8）と mace_smash（26）を加えた49件。 */
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

    private static final List<String> PAINTING_767 = List.of(
            "alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet",
            "burning_skull", "bust", "cavebird", "changing", "cotan", "courbet", "creebet",
            "donkey_kong", "earth", "endboss", "fern", "fighters", "finding", "fire",
            "graham", "humble", "kebab", "lowmist", "match", "meditative", "orb",
            "owlemons", "passage", "pigscene", "plant", "pointer", "pond", "pool",
            "prairie_ride", "sea", "skeleton", "skull_and_roses", "stage", "sunflowers",
            "sunset", "tides", "unpacked", "void", "wanderer", "wasteland", "water",
            "wind", "wither");

    // ------------------------------------------------------------------
    // ヘルパー
    // ------------------------------------------------------------------

    /** 767形式の Login Success（UUID・名前・プロパティ1件・strictErrorHandling）。 */
    private static byte[] loginSuccess767Payload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        writer.writeString("Steve");
        writer.writeVarInt(1);
        writer.writeString("textures");
        writer.writeString("abc123");
        writer.writeBoolean(true); // signature 有り
        writer.writeString("sig456");
        writer.writeBoolean(false); // strictErrorHandling（768→767 は false を補う）
        return writer.bytes();
    }

    /** 768形式の Login Success（strictErrorHandling 無し）。 */
    private static byte[] loginSuccess768Payload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        writer.writeString("Steve");
        writer.writeVarInt(1);
        writer.writeString("textures");
        writer.writeString("abc123");
        writer.writeBoolean(true); // signature 有り
        writer.writeString("sig456");
        return writer.bytes();
    }

    /** 767形式の Join Game。SpawnInfo の末尾は portalCooldown。 */
    private static byte[] joinGame767Payload() {
        Writer writer = new Writer();
        writer.writeInt(42); // entityId
        writer.writeBoolean(false); // isHardcore
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebugInfo
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // doLimitedCrafting
        writeSpawnInfo767(writer);
        writer.writeBoolean(true); // enforcesSecureChat
        return writer.bytes();
    }

    /** 768形式の Join Game。SpawnInfo の末尾は seaLevel。 */
    private static byte[] joinGame768Payload() {
        Writer writer = new Writer();
        writer.writeInt(42); // entityId
        writer.writeBoolean(false); // isHardcore
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebugInfo
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // doLimitedCrafting
        writeSpawnInfo768(writer);
        writer.writeBoolean(true); // enforcesSecureChat
        return writer.bytes();
    }

    /** 767形式の SpawnInfo（death 無し）。 */
    private static void writeSpawnInfo767(Writer writer) {
        writer.writeVarInt(0); // dimension
        writer.writeString("minecraft:overworld"); // name
        writer.writeLong(99L); // hashedSeed
        writer.writeByte(0); // gamemode（survival）
        writer.writeByte(-1); // previousGamemode（無し）
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // death 無し
        writer.writeVarInt(0); // portalCooldown
    }

    /** 768形式の SpawnInfo（death 無し、seaLevel=63）。 */
    private static void writeSpawnInfo768(Writer writer) {
        writer.writeVarInt(0); // dimension
        writer.writeString("minecraft:overworld"); // name
        writer.writeLong(99L); // hashedSeed
        writer.writeByte(0); // gamemode（survival）
        writer.writeByte(-1); // previousGamemode（無し）
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // death 無し
        writer.writeVarInt(0); // portalCooldown
        writer.writeVarInt(63); // seaLevel
    }

    /** 767形式の Respawn（SpawnInfo＋copyMetadata）。 */
    private static byte[] respawn767Payload() {
        Writer writer = new Writer();
        writeSpawnInfo767(writer);
        writer.writeByte(3); // copyMetadata
        return writer.bytes();
    }

    /** 768形式の Respawn（SpawnInfo＋copyMetadata）。 */
    private static byte[] respawn768Payload() {
        Writer writer = new Writer();
        writeSpawnInfo768(writer);
        writer.writeByte(3); // copyMetadata
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

    /** 766／767／768形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767／768形式の Registry Data パケットを読み、名前列と hasData 列を返す。 */
    private record RegistryPacket(List<String> names, List<Boolean> hasData) {
    }

    private static RegistryPacket readRegistryPacket(byte[] payload) {
        Reader reader = new Reader(payload);
        String registryId = reader.readString();
        assertTrue(registryId.startsWith("minecraft:"), registryId);
        int count = reader.readVarInt();
        List<String> names = new ArrayList<>(count);
        List<Boolean> hasData = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(reader.readString());
            boolean has = reader.readBoolean();
            hasData.add(has);
            if (has) {
                reader.readAnonymousNbt();
            }
        }
        assertEquals(0, reader.remaining());
        return new RegistryPacket(names, hasData);
    }

    private static List<String> shortNames(List<String> fullNames) {
        return fullNames.stream()
                .map(n -> n.startsWith("minecraft:") ? n.substring("minecraft:".length()) : n)
                .toList();
    }

    // ------------------------------------------------------------------
    // Login Success
    // ------------------------------------------------------------------

    @Test
    void loginSuccess767To768StripsStrictBool() {
        byte[] out = PlayTranslator.translateLoginSuccess767To768(loginSuccess767Payload());
        assertArrayEquals(loginSuccess768Payload(), out);
    }

    @Test
    void loginSuccess768To767AppendsFalse() {
        byte[] out = PlayTranslator.translateLoginSuccess768To767(loginSuccess768Payload());
        assertEquals(loginSuccess768Payload().length + 1, out.length);
        assertEquals(0, out[out.length - 1]);
        assertArrayEquals(loginSuccess767Payload(), out);
    }

    // ------------------------------------------------------------------
    // Join Game / Respawn
    // ------------------------------------------------------------------

    @Test
    void joinGame767To768AddsSeaLevel() {
        byte[] out = PlayTranslator.translateJoinGame767To768(joinGame767Payload());
        assertArrayEquals(joinGame768Payload(), out);
    }

    @Test
    void joinGame768To767DropsSeaLevel() {
        byte[] out = PlayTranslator.translateJoinGame768To767(joinGame768Payload());
        assertArrayEquals(joinGame767Payload(), out);
    }

    @Test
    void joinGameRoundTrip() {
        byte[] original = joinGame767Payload();
        assertArrayEquals(original, PlayTranslator.translateJoinGame768To767(
                PlayTranslator.translateJoinGame767To768(original)));
    }

    @Test
    void respawn767To768AddsSeaLevel() {
        byte[] out = PlayTranslator.translateRespawn767To768(respawn767Payload(), Map.of());
        assertArrayEquals(respawn768Payload(), out);
    }

    @Test
    void respawn768To767DropsSeaLevel() {
        byte[] out = PlayTranslator.translateRespawn768To767(respawn768Payload(), Map.of());
        assertArrayEquals(respawn767Payload(), out);
    }

    // ------------------------------------------------------------------
    // Synchronize Player Position / 移動パケット
    // ------------------------------------------------------------------

    @Test
    void position767To768() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0x03); // flags：x／y 相対
        writer.writeVarInt(42); // teleportId
        byte[] out = PlayTranslator.translatePosition767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(42, reader.readVarInt()); // teleportId（先頭へ）
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(64.0, reader.readDouble(), 0.0001);
        assertEquals(2.5, reader.readDouble(), 0.0001);
        assertEquals(0.0, reader.readDouble(), 0.0001); // dx
        assertEquals(0.0, reader.readDouble(), 0.0001); // dy
        assertEquals(0.0, reader.readDouble(), 0.0001); // dz
        assertEquals(90.0f, reader.readFloat(), 0.0001f);
        assertEquals(0.0f, reader.readFloat(), 0.0001f);
        assertEquals(0x03, reader.readInt()); // flags（u32）
        assertEquals(0, reader.remaining());
    }

    @Test
    void position768To767() {
        Writer writer = new Writer();
        writer.writeVarInt(42); // teleportId
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeDouble(0.1); // dx
        writer.writeDouble(0.2); // dy
        writer.writeDouble(0.3); // dz
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeInt(0x123); // flags（u32。dx／dy／dz ビット付き）
        byte[] out = PlayTranslator.translatePosition768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(64.0, reader.readDouble(), 0.0001);
        assertEquals(2.5, reader.readDouble(), 0.0001);
        assertEquals(90.0f, reader.readFloat(), 0.0001f);
        assertEquals(0.0f, reader.readFloat(), 0.0001f);
        assertEquals(0x03, reader.readUnsignedByte()); // flags（u8。下位5ビットのみ）
        assertEquals(42, reader.readVarInt()); // teleportId（末尾へ）
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerPosition767To768() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeBoolean(true); // onGround
        byte[] out = PlayTranslator.translatePlayerPosition767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(64.0, reader.readDouble(), 0.0001);
        assertEquals(2.5, reader.readDouble(), 0.0001);
        assertEquals(0x01, reader.readUnsignedByte()); // flags（onGround）
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerPosition768To767() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeByte(0x03); // flags（onGround＋hasHorizontalCollision）
        byte[] out = PlayTranslator.translatePlayerPosition768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(64.0, reader.readDouble(), 0.0001);
        assertEquals(2.5, reader.readDouble(), 0.0001);
        assertTrue(reader.readBoolean()); // onGround
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerPosition768To767NotOnGround() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeByte(0x02); // flags（hasHorizontalCollision のみ）
        byte[] out = PlayTranslator.translatePlayerPosition768To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        assertFalse(reader.readBoolean()); // onGround
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerPositionLook767To768() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeBoolean(false); // onGround
        byte[] out = PlayTranslator.translatePlayerPositionLook767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(64.0, reader.readDouble(), 0.0001);
        assertEquals(2.5, reader.readDouble(), 0.0001);
        assertEquals(90.0f, reader.readFloat(), 0.0001f);
        assertEquals(0.0f, reader.readFloat(), 0.0001f);
        assertEquals(0x00, reader.readUnsignedByte()); // flags
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerLook767To768() {
        Writer writer = new Writer();
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeBoolean(true); // onGround
        byte[] out = PlayTranslator.translatePlayerLook767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(90.0f, reader.readFloat(), 0.0001f);
        assertEquals(0.0f, reader.readFloat(), 0.0001f);
        assertEquals(0x01, reader.readUnsignedByte()); // flags（onGround）
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerFlying768To767() {
        Writer writer = new Writer();
        writer.writeByte(0x01); // flags（onGround）
        byte[] out = PlayTranslator.translatePlayerFlying768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertTrue(reader.readBoolean()); // onGround
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Update Time / Set Slot State
    // ------------------------------------------------------------------

    @Test
    void updateTime767To768() {
        Writer writer = new Writer();
        writer.writeLong(1000L);
        writer.writeLong(6000L);
        byte[] out = PlayTranslator.translateUpdateTime767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1000L, reader.readLong());
        assertEquals(6000L, reader.readLong());
        assertFalse(reader.readBoolean()); // tickDayTime
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateTime768To767() {
        Writer writer = new Writer();
        writer.writeLong(1000L);
        writer.writeLong(6000L);
        writer.writeBoolean(true); // tickDayTime
        byte[] out = PlayTranslator.translateUpdateTime768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1000L, reader.readLong());
        assertEquals(6000L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void setSlotState767To768IsIdentity() {
        // window_id は両バージョンとも varint のため同一形式。200（2バイトの varint）で確かめる。
        Writer writer = new Writer();
        writer.writeVarInt(5); // slotId
        writer.writeVarInt(200); // windowId（varint）
        writer.writeBoolean(true); // state
        byte[] payload = writer.bytes();
        assertArrayEquals(payload, PlayTranslator.translateSetSlotState767To768(payload));
        assertArrayEquals(payload, PlayTranslator.translateSetSlotState768To767(payload));
    }

    // ------------------------------------------------------------------
    // Enchant Item / Close Window（ContainerID の型が u8→varint に変わる）
    // ------------------------------------------------------------------

    @Test
    void enchantItem767To768() {
        Writer writer = new Writer();
        writer.writeByte(200); // windowId（u8。-56 として読む）
        writer.writeVarInt(3); // enchantment
        byte[] out = PlayTranslator.translateEnchantItem767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(-56, reader.readVarInt()); // windowId（varint。u8 の 200 と同じビット）
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void enchantItem768To767() {
        Writer writer = new Writer();
        writer.writeVarInt(200); // windowId（varint）
        writer.writeVarInt(3); // enchantment
        byte[] out = PlayTranslator.translateEnchantItem768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(-56, reader.readByte()); // windowId（u8。200 と同じビット）
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void closeWindow767To768() {
        byte[] out = PlayTranslator.translateCloseWindow767To768(new byte[]{(byte) 200});
        Reader reader = new Reader(out);
        assertEquals(-56, reader.readVarInt()); // windowId（varint）
        assertEquals(0, reader.remaining());
    }

    @Test
    void closeWindow768To767() {
        Writer writer = new Writer();
        writer.writeVarInt(-1); // windowId（varint。カーソル）
        byte[] out = PlayTranslator.translateCloseWindow768To767(writer.bytes());
        assertArrayEquals(new byte[]{(byte) 0xFF}, out); // windowId（u8。-1）
    }

    // ------------------------------------------------------------------
    // Player Input / Steer Vehicle
    // ------------------------------------------------------------------

    @Test
    void playerInput768ToSteerVehicle() {
        // forward＋right＋jump＋shift（sprint 付き）。sprint は落とす。
        byte[] out = PlayTranslator.translatePlayerInput768ToSteerVehicle(
                new byte[]{(byte) 0x79});
        Reader reader = new Reader(out);
        assertEquals(1.0f, reader.readFloat(), 0.0001f); // sideways（right）
        assertEquals(1.0f, reader.readFloat(), 0.0001f); // forward
        assertEquals(0x03, reader.readUnsignedByte()); // jump＋sneak
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerInput768ToSteerVehicleBackwardLeft() {
        byte[] out = PlayTranslator.translatePlayerInput768ToSteerVehicle(
                new byte[]{(byte) 0x06}); // backward＋left
        Reader reader = new Reader(out);
        assertEquals(-1.0f, reader.readFloat(), 0.0001f); // sideways（left）
        assertEquals(-1.0f, reader.readFloat(), 0.0001f); // forward（backward）
        assertEquals(0x00, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void steerVehicleToPlayerInput768() {
        Writer writer = new Writer();
        writer.writeFloat(-1.0f); // sideways（left）
        writer.writeFloat(1.0f); // forward
        writer.writeByte(0x03); // jump＋sneak
        byte[] out = PlayTranslator.translateSteerVehicleToPlayerInput768(writer.bytes());
        assertArrayEquals(new byte[]{(byte) 0x35}, out); // forward＋left＋jump＋shift
    }

    @Test
    void playerInputRoundTrip() {
        // sprint ビット以外は往復する。
        byte[] input = new byte[]{(byte) 0x39}; // forward＋left＋jump＋shift
        byte[] steer = PlayTranslator.translatePlayerInput768ToSteerVehicle(input);
        assertArrayEquals(input, PlayTranslator.translateSteerVehicleToPlayerInput768(steer));
    }

    // ------------------------------------------------------------------
    // Player Info
    // ------------------------------------------------------------------

    /** 768形式の Player Info（update_list_order 付き、プレイヤー2人）。 */
    private static byte[] playerInfo768Payload() {
        Writer writer = new Writer();
        writer.writeByte(0x7F); // 全アクション（list_order 含む）
        writer.writeVarInt(2); // count 2
        // 1人目：全アクションのデータあり。
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("Alice"); // add_player：名前
        writer.writeVarInt(1); // properties 1件
        writer.writeString("textures");
        writer.writeString("texvalue");
        writer.writeBoolean(true); // signature 有り
        writer.writeString("sigvalue");
        writer.writeBoolean(true); // initialize_chat：セッション有り
        writer.writeUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        writer.writeLong(123456789L); // 有効期限
        writer.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        writer.writeByteArray(new byte[]{0x03}); // 署名
        writer.writeVarInt(1); // update_game_mode（creative）
        writer.writeVarInt(1); // listed（varint）
        writer.writeVarInt(42); // latency
        writer.writeBoolean(true); // displayName 有り
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of())); // 空の NBT
        writer.writeVarInt(5); // listPriority
        // 2人目：add_player と latency だけ。
        writer.writeUuid(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        writer.writeString("Bob");
        writer.writeVarInt(0); // properties 0件
        writer.writeBoolean(false); // initialize_chat：セッション無し
        writer.writeVarInt(0); // update_game_mode（survival）
        writer.writeVarInt(1); // listed
        writer.writeVarInt(7); // latency
        writer.writeBoolean(false); // displayName 無し
        writer.writeVarInt(9); // listPriority
        return writer.bytes();
    }

    /** 767形式の Player Info（update_list_order 無し、プレイヤー2人）。 */
    private static byte[] playerInfo767Payload() {
        Writer writer = new Writer();
        writer.writeByte(0x3F); // list_order 無し
        writer.writeVarInt(2); // count 2
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("Alice");
        writer.writeVarInt(1);
        writer.writeString("textures");
        writer.writeString("texvalue");
        writer.writeBoolean(true);
        writer.writeString("sigvalue");
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        writer.writeLong(123456789L);
        writer.writeByteArray(new byte[]{0x01, 0x02});
        writer.writeByteArray(new byte[]{0x03});
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(42);
        writer.writeBoolean(true);
        writer.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        writer.writeUuid(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        writer.writeString("Bob");
        writer.writeVarInt(0);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeVarInt(1);
        writer.writeVarInt(7);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void playerInfo768To767DropsListOrder() {
        byte[] out = PlayTranslator.translatePlayerInfo768To767(playerInfo768Payload());
        assertArrayEquals(playerInfo767Payload(), out);
    }

    @Test
    void playerInfo767To768IsIdentity() {
        assertArrayEquals(playerInfo767Payload(),
                PlayTranslator.translatePlayerInfo767To768(playerInfo767Payload()));
    }

    // ------------------------------------------------------------------
    // Registry Data
    // ------------------------------------------------------------------

    @Test
    void registryData767To768DamageTypeReordered() {
        byte[] packet = registryPacket("minecraft:damage_type", DAMAGE_767);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData767To768(packet));
        assertEquals(DAMAGE_768, shortNames(out.names()));
        assertEquals(49, out.names().size());
        assertEquals(8, out.names().indexOf("minecraft:ender_pearl"));
        assertEquals(26, out.names().indexOf("minecraft:mace_smash"));
        assertEquals(27, out.names().indexOf("minecraft:magic"));
        assertEquals(47, out.names().indexOf("minecraft:wither"));
        assertEquals(48, out.names().indexOf("minecraft:wither_skull"));
        // 768だけの種別は hasData=false で埋める。
        assertFalse(out.hasData().get(8)); // ender_pearl
        assertFalse(out.hasData().get(26)); // mace_smash
        assertTrue(out.hasData().get(27)); // magic
        assertTrue(out.hasData().get(0)); // arrow
    }

    @Test
    void registryData768To767DamageTypeReordered() {
        byte[] packet = registryPacket("minecraft:damage_type", DAMAGE_768);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData768To767(packet));
        assertEquals(DAMAGE_767, shortNames(out.names()));
        assertEquals(47, out.names().size());
        assertEquals(25, out.names().indexOf("minecraft:magic"));
        assertEquals(45, out.names().indexOf("minecraft:wither"));
        assertEquals(46, out.names().indexOf("minecraft:wither_skull"));
        assertFalse(out.names().contains("minecraft:ender_pearl"));
        assertFalse(out.names().contains("minecraft:mace_smash"));
        assertTrue(out.hasData().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void registryData767To768EnchantmentsIdentity() {
        byte[] packet = registryPacket("minecraft:enchantment", ENCHANTMENT_767);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData767To768(packet));
        assertEquals(ENCHANTMENT_768, shortNames(out.names()));
        assertEquals(42, out.names().size());
        assertEquals(22, out.names().indexOf("minecraft:mending"));
        assertTrue(out.hasData().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void registryData768To767EnchantmentsIdentity() {
        byte[] packet = registryPacket("minecraft:enchantment", ENCHANTMENT_768);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData768To767(packet));
        assertEquals(ENCHANTMENT_767, shortNames(out.names()));
        assertEquals(42, out.names().size());
        assertEquals(22, out.names().indexOf("minecraft:mending"));
        assertTrue(out.hasData().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void registryData767To768PaintingsIdentity() {
        byte[] packet = registryPacket("minecraft:painting_variant", PAINTING_767);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData767To768(packet));
        assertEquals(PAINTING_767, shortNames(out.names()));
        assertEquals(50, out.names().size());
        assertEquals(23, out.names().indexOf("minecraft:kebab"));
        assertTrue(out.hasData().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void registryData768JukeboxSongKeptFor767() {
        // jukebox_song は767にも存在するため落とさない。
        byte[] packet = registryPacket("minecraft:jukebox_song", List.of("13", "cat"));
        assertNotNull(PlayTranslator.translateRegistryData768To767(packet));
    }

    @Test
    void registryData767JukeboxSongKeptFor768() {
        // jukebox_song は768にも存在するため落とさない。
        byte[] packet = registryPacket("minecraft:jukebox_song", List.of("13", "cat"));
        assertNotNull(PlayTranslator.translateRegistryData767To768(packet));
    }

    @Test
    void reorderRegistries767To768DamageType() {
        Map<String, Tag.CompoundTag> reordered = PlayTranslator.reorderRegistries(
                Map.of("minecraft:damage_type",
                        registryCodec("minecraft:damage_type", DAMAGE_767)),
                767, 768);
        Tag.CompoundTag registry = reordered.get("minecraft:damage_type");
        assertNotNull(registry);
        List<Tag> entries = registry.getList("value").map(Tag.ListTag::value).orElse(List.of());
        assertEquals(49, entries.size());
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (Tag entry : entries) {
            Tag.CompoundTag compound = (Tag.CompoundTag) entry;
            String name = compound.getString("name").orElseThrow();
            int id = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value()).orElseThrow();
            ids.put(name, id);
        }
        assertEquals(8, ids.get("minecraft:ender_pearl"));
        assertEquals(26, ids.get("minecraft:mace_smash"));
        assertEquals(27, ids.get("minecraft:magic"));
        assertEquals(48, ids.get("minecraft:wither_skull"));
        // 768だけの種別は element 無しで埋める。
        Tag.CompoundTag enderPearl = (Tag.CompoundTag) entries.get(8);
        assertFalse(enderPearl.get("element").isPresent());
        Tag.CompoundTag magic = (Tag.CompoundTag) entries.get(27);
        assertTrue(magic.get("element").isPresent());
    }

    // ---- Client Information（Settings）の 767<->768 ----

    /** 767 形式の Client Information ペイロード（8 フィールド）。 */
    private static byte[] clientInformation767() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("ja_JP");
        writer.writeByte(8); // viewDistance
        writer.writeVarInt(0); // chatFlags
        writer.writeBoolean(true); // chatColors
        writer.writeUnsignedByte(0x7F); // skinParts
        writer.writeVarInt(1); // mainHand
        writer.writeBoolean(false); // enableTextFiltering
        writer.writeBoolean(false); // enableServerListing
        return writer.bytes();
    }

    /** 768 形式の Client Information ペイロード（8 フィールド＋particleStatus）。 */
    private static byte[] clientInformation768(int particleStatus) {
        byte[] base = clientInformation767();
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBytes(base);
        writer.writeVarInt(particleStatus);
        return writer.bytes();
    }

    @Test
    void clientInformation767To768AppendsParticleStatus() {
        byte[] out = PlayTranslator.translateClientInformation767To768(clientInformation767());
        byte[] expected = clientInformation768(0);
        assertArrayEquals(expected, out);
    }

    @Test
    void clientInformation768To767StripsParticleStatus() {
        byte[] out = PlayTranslator.translateClientInformation768To767(clientInformation768(2));
        assertArrayEquals(clientInformation767(), out);
    }

    @Test
    void clientInformation768To767RoundTrip() {
        byte[] out = PlayTranslator.translateClientInformation768To767(
                PlayTranslator.translateClientInformation767To768(clientInformation767()));
        assertArrayEquals(clientInformation767(), out);
    }

    @Test
    void clientInformation768To767MalformedReturnsInput() {
        // particleStatus が無い 767 形式を 768 として読むと読めないため、そのまま返す。
        byte[] in = clientInformation767();
        assertArrayEquals(in, PlayTranslator.translateClientInformation768To767(in));
    }
}
