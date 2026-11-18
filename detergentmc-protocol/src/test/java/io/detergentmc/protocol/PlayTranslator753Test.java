package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の753（1.16.3）⇔754（1.16.4／1.16.5）向け変換テスト。
 *
 * <p>753⇔754の変換はすべて恒等写像である。根拠は STAGE-A-753-NOTES.md §8.1・§11.1・§11.6：
 * パケットID差分0件（packet-ids-753.json、272件全て id753 == id754）、レジストリ差分0件
 * （実バニラ1.16.3の registries.json・blocks レポートは1.16.5版とバイト同一）、
 * Login 15フィールド・Respawn 8フィールド同一（公式 server_mappings.txt）、
 * 実バニラ1.16.3の Join Game 生キャプチャ30,847バイトは実バニラ1.16.5のものと同一。
 * 変換器はパースして書き直さず、入力ペイロードをそのまま返す。
 * 754のパーサ（parseJoinGame754／readSpawnInfo754）は753のバイトも読めることを
 * 本テストで証明する。実ゲームクライアントでの確認は未実施。
 */
class PlayTranslator753Test {

    private static byte[] readResource(String name) {
        try (InputStream in = PlayTranslator753Test.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new AssertionError("テストリソースがありません: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("テストリソースが読めません: " + name, e);
        }
    }

    /** dimension_type の element 1件を作る（754形式。min_y／height は無い。753も同じ）。 */
    private static Tag.CompoundTag dimensionTypeElement754(int logicalHeight) {
        Map<String, Tag> element = new LinkedHashMap<>();
        element.put("piglin_safe", new Tag.ByteTag((byte) 0));
        element.put("natural", new Tag.ByteTag((byte) 1));
        element.put("ambient_light", new Tag.FloatTag(0.0f));
        element.put("infiniburn", new Tag.StringTag("minecraft:infiniburn_overworld"));
        element.put("respawn_anchor_works", new Tag.ByteTag((byte) 0));
        element.put("has_skylight", new Tag.ByteTag((byte) 1));
        element.put("bed_works", new Tag.ByteTag((byte) 1));
        element.put("effects", new Tag.StringTag("minecraft:overworld"));
        element.put("has_raids", new Tag.ByteTag((byte) 1));
        element.put("logical_height", new Tag.IntTag(logicalHeight));
        element.put("coordinate_scale", new Tag.DoubleTag(1.0));
        element.put("ultrawarm", new Tag.ByteTag((byte) 0));
        element.put("has_ceiling", new Tag.ByteTag((byte) 0));
        return new Tag.CompoundTag(element);
    }

    /** 合成の754 Respawn ペイロード（753も同じワイヤ。writeSpawnInfo754 で組み立てる）。 */
    private static byte[] syntheticRespawn754() {
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo754(writer, new PlayTranslator.SpawnInfo754(
                dimensionTypeElement754(256), "minecraft:overworld",
                123456789L, 0, -1, false, false, false));
        return writer.bytes();
    }

    /** 合成の Client Information ペイロード（753・754とも6フィールド）。 */
    private static byte[] clientInformation753() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7f);
        writer.writeVarInt(1);
        return writer.bytes();
    }

    /** 合成の Synchronize Player Position ペイロード（753・754とも dismountVehicle 無し）。 */
    private static byte[] position753() {
        Writer writer = new Writer();
        writer.writeDouble(100.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-200.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7);
        return writer.bytes();
    }

    private static List<Tag> registryEntries(Tag.CompoundTag codec, String registryId) {
        return codec.getCompound(registryId)
                .flatMap(r -> r.getList("value"))
                .map(Tag.ListTag::value)
                .orElseThrow(() -> new AssertionError("レジストリがありません: " + registryId));
    }

    private static List<String> registryNames(List<Tag> entries) {
        List<String> names = new ArrayList<>(entries.size());
        for (Tag entry : entries) {
            names.add(((Tag.CompoundTag) entry).getString("name")
                    .orElseThrow(() -> new AssertionError("name がありません")));
        }
        return names;
    }

    @Test
    void joinGame753LiveCaptureParses() {
        byte[] capture = readResource("cap753-joingame-1-16-3.bin");
        assertEquals(30_847, capture.length);
        // 754のパーサが753の生キャプチャを30,847バイトちょうど消費してパースできる。
        // entityId はサーバー起動ごとに変わる（このファイルは204、もう一方の
        // joingame-1.16.3a.bin は109。754のキャプチャも起動ごとに224・253・270）。
        PlayTranslator.JoinGame754 join = PlayTranslator.parseJoinGame754(capture);
        assertEquals(204, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                join.worldNames());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(-8426507314629099014L, join.hashedSeed());
        assertEquals(5, join.maxPlayers());
        assertEquals(10, join.viewDistance());
        assertFalse(join.reducedDebug());
        assertTrue(join.enableRespawnScreen());
        assertFalse(join.isDebug());
        assertFalse(join.isFlat());
        List<Tag> dimensionTypes = registryEntries(join.dimensionCodec(), "minecraft:dimension_type");
        assertEquals(4, dimensionTypes.size());
        assertEquals(List.of("minecraft:overworld", "minecraft:overworld_caves",
                "minecraft:the_nether", "minecraft:the_end"), registryNames(dimensionTypes));
        List<Tag> biomes = registryEntries(join.dimensionCodec(), "minecraft:worldgen/biome");
        assertEquals(79, biomes.size());
        // dimension element に min_y／height は無い（753も754も同じ実測）。
        Tag.CompoundTag dimension = (Tag.CompoundTag) join.dimension();
        assertFalse(dimension.get("min_y").isPresent());
        assertFalse(dimension.get("height").isPresent());
        // 生キャプチャを write してもバイトが変わらない（パーサの検証）。
        assertArrayEquals(capture, PlayTranslator.writeJoinGame754(join));
    }

    @Test
    void joinGame753To754Identity() {
        byte[] capture = readResource("cap753-joingame-1-16-3.bin");
        assertArrayEquals(capture, PlayTranslator.translateJoinGame753To754(capture));
        assertSame(capture, PlayTranslator.translateJoinGame753To754(capture));
    }

    @Test
    void joinGame754To753Identity() {
        byte[] capture754 = readResource("cap754-joingame-1-16-5.bin");
        assertEquals(30_847, capture754.length);
        assertArrayEquals(capture754, PlayTranslator.translateJoinGame754To753(capture754));
        assertSame(capture754, PlayTranslator.translateJoinGame754To753(capture754));
        // 753のキャプチャを逆向きに通しても同じ。
        byte[] capture753 = readResource("cap753-joingame-1-16-3.bin");
        assertArrayEquals(capture753, PlayTranslator.translateJoinGame754To753(capture753));
    }

    @Test
    void joinGame753RoundTripBytesIdentical() {
        byte[] capture = readResource("cap753-joingame-1-16-3.bin");
        byte[] via754 = PlayTranslator.translateJoinGame753To754(capture);
        assertArrayEquals(capture, PlayTranslator.translateJoinGame754To753(via754));
    }

    @Test
    void respawn753SyntheticParses() {
        byte[] payload = syntheticRespawn754();
        PlayTranslator.SpawnInfo754 info = PlayTranslator.readSpawnInfo754(new Reader(payload));
        assertEquals("minecraft:overworld", info.worldName());
        assertEquals(123456789L, info.hashedSeed());
        assertEquals(0, info.gameMode());
        // Respawn の previousGameMode は u8 で読むため、0xFF は 255 になる
        // （Join Game の i8 とは違う。ワイヤ上の1バイトは同じ）。
        assertEquals(255, info.previousGameMode());
        assertFalse(info.isDebug());
        assertFalse(info.isFlat());
        assertFalse(info.copyMetadata());
        assertEquals(dimensionTypeElement754(256), info.dimension());
        // write→parse の往復でもバイトが変わらない。
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo754(writer, info);
        assertArrayEquals(payload, writer.bytes());
    }

    @Test
    void respawn753To754Identity() {
        byte[] payload = syntheticRespawn754();
        assertArrayEquals(payload, PlayTranslator.translateRespawn753To754(payload));
        assertSame(payload, PlayTranslator.translateRespawn753To754(payload));
    }

    @Test
    void respawn754To753IdentityAndRoundTrip() {
        byte[] payload = syntheticRespawn754();
        assertArrayEquals(payload, PlayTranslator.translateRespawn754To753(payload));
        assertSame(payload, PlayTranslator.translateRespawn754To753(payload));
        byte[] via754 = PlayTranslator.translateRespawn753To754(payload);
        assertArrayEquals(payload, PlayTranslator.translateRespawn754To753(via754));
    }

    @Test
    void clientInformation753To754Identity() {
        byte[] payload = clientInformation753();
        assertArrayEquals(payload, PlayTranslator.translateClientInformation753To754(payload));
        assertSame(payload, PlayTranslator.translateClientInformation753To754(payload));
    }

    @Test
    void clientInformation754To753IdentityAndRoundTrip() {
        byte[] payload = clientInformation753();
        assertArrayEquals(payload, PlayTranslator.translateClientInformation754To753(payload));
        assertSame(payload, PlayTranslator.translateClientInformation754To753(payload));
        byte[] via754 = PlayTranslator.translateClientInformation753To754(payload);
        assertArrayEquals(payload, PlayTranslator.translateClientInformation754To753(via754));
    }

    @Test
    void position753To754Identity() {
        byte[] payload = position753();
        assertArrayEquals(payload, PlayTranslator.translatePosition753To754(payload));
        assertSame(payload, PlayTranslator.translatePosition753To754(payload));
    }

    @Test
    void position754To753IdentityAndRoundTrip() {
        byte[] payload = position753();
        assertArrayEquals(payload, PlayTranslator.translatePosition754To753(payload));
        assertSame(payload, PlayTranslator.translatePosition754To753(payload));
        byte[] via754 = PlayTranslator.translatePosition753To754(payload);
        assertArrayEquals(payload, PlayTranslator.translatePosition754To753(via754));
    }

    @Test
    void packetIds753SpotChecks() {
        assertEquals(0x24, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(753));
        assertEquals(0x24, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(754));
        assertEquals(0x39, PacketIds.PacketName.PLAY_RESPAWN.idFor(753));
        assertEquals(0x39, PacketIds.PacketName.PLAY_RESPAWN.idFor(754));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(753));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(754));
        assertEquals(0x34, PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION.idFor(753));
        assertEquals(0x34, PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION.idFor(754));
        // combat_event／title／world_border は753・754とも単一IDを共有する。
        assertEquals(0x31, PacketIds.PacketName.PLAY_ENTER_COMBAT_EVENT.idFor(753));
        assertEquals(0x31, PacketIds.PacketName.PLAY_ENTER_COMBAT_EVENT.idFor(754));
        assertEquals(0x4f, PacketIds.PacketName.PLAY_SET_TITLE_TEXT.idFor(753));
        assertEquals(0x4f, PacketIds.PacketName.PLAY_SET_TITLE_TEXT.idFor(754));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_INITIALIZE_WORLD_BORDER.idFor(753));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_INITIALIZE_WORLD_BORDER.idFor(754));
        // ping／pong は両版とも transaction。
        assertEquals(0x11, PacketIds.PacketName.PLAY_PING.idFor(753));
        assertEquals(0x11, PacketIds.PacketName.PLAY_PING.idFor(754));
        assertEquals(0x07, PacketIds.PacketName.PLAY_PONG.idFor(753));
        assertEquals(0x07, PacketIds.PacketName.PLAY_PONG.idFor(754));
        // sculk は両版とも無し。declare_recipes は両版とも 0x5a。
        assertEquals(-1, PacketIds.PacketName.PLAY_SCULK_VIBRATION_SIGNAL.idFor(753));
        assertEquals(-1, PacketIds.PacketName.PLAY_SCULK_VIBRATION_SIGNAL.idFor(754));
        assertEquals(0x5a, PacketIds.PacketName.PLAY_DECLARE_RECIPES.idFor(753));
        assertEquals(0x5a, PacketIds.PacketName.PLAY_DECLARE_RECIPES.idFor(754));
    }
}
