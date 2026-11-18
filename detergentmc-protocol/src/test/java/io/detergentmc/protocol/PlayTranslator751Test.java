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
 * {@link PlayTranslator} の751（1.16.2）⇔753（1.16.3）向け変換テスト。
 *
 * <p>751⇔753の変換はすべて恒等写像である。根拠は STAGE-A-751-NOTES.md §8.1・§11.1・§11.6：
 * パケットID差分0件（packet-ids-751.json、272件全て id751 == id753）、レジストリ差分0件
 * （実バニラ1.16.2の registries.json・blocks レポートは1.16.3版とバイト同一）、
 * Login 15フィールド・Respawn 8フィールド同一（公式 server_mappings.txt）、
 * 実バニラ1.16.2の Join Game 生キャプチャ30,847バイトは実バニラ1.16.3のものと同一。
 * 変換器はパースして書き直さず、入力ペイロードをそのまま返す。
 * 753のパーサ（parseJoinGame753／readSpawnInfo754）は751のバイトも読めることを
 * 本テストで証明する。実ゲームクライアントでの確認は未実施。
 */
class PlayTranslator751Test {

    private static byte[] readResource(String name) {
        try (InputStream in = PlayTranslator751Test.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new AssertionError("テストリソースがありません: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("テストリソースが読めません: " + name, e);
        }
    }

    /** dimension_type の element 1件を作る（753形式。min_y／height は無い。751も同じ）。 */
    private static Tag.CompoundTag dimensionTypeElement753(int logicalHeight) {
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

    /** 合成の753 Respawn ペイロード（751も同じワイヤ。writeSpawnInfo754 で組み立てる）。 */
    private static byte[] syntheticRespawn753() {
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo754(writer, new PlayTranslator.SpawnInfo754(
                dimensionTypeElement753(256), "minecraft:overworld",
                123456789L, 0, -1, false, false, false));
        return writer.bytes();
    }

    /** 合成の Client Information ペイロード（751・753とも6フィールド）。 */
    private static byte[] clientInformation751() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7f);
        writer.writeVarInt(1);
        return writer.bytes();
    }

    /** 合成の Synchronize Player Position ペイロード（751・753とも dismountVehicle 無し）。 */
    private static byte[] position751() {
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
    void joinGame751LiveCaptureParses() {
        byte[] capture = readResource("cap751-joingame-1-16-2.bin");
        assertEquals(30_847, capture.length);
        // 753のパーサが751の生キャプチャを30,847バイトちょうど消費してパースできる。
        // parseJoinGame753 は parseJoinGame754 に委譲し、末尾まで読み切る
        // （requireEnd）ため、パース成功自体がちょうどの消費の証明になる。
        // entityId はサーバー起動ごとに変わる（このファイルは306、もう一方の
        // joingame-1.16.2a.bin は131。753のキャプチャも起動ごとに204・109）。
        // maxPlayers が753のときの5ではなく20なのは server.properties の
        // 設定値の違い（既定20）で、プロトコルの違いではない（751ノート §11.6）。
        PlayTranslator.JoinGame754 join = PlayTranslator.parseJoinGame753(capture);
        assertEquals(306, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                join.worldNames());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(7036270111817450553L, join.hashedSeed());
        assertEquals(20, join.maxPlayers());
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
        // dimension element に min_y／height は無い（751も753も同じ実測）。
        Tag.CompoundTag dimension = (Tag.CompoundTag) join.dimension();
        assertFalse(dimension.get("min_y").isPresent());
        assertFalse(dimension.get("height").isPresent());
        // 生キャプチャを write してもバイトが変わらない（パーサの検証）。
        assertArrayEquals(capture, PlayTranslator.writeJoinGame754(join));
    }

    @Test
    void joinGame751To753Identity() {
        byte[] capture = readResource("cap751-joingame-1-16-2.bin");
        assertArrayEquals(capture, PlayTranslator.translateJoinGame751To753(capture));
        assertSame(capture, PlayTranslator.translateJoinGame751To753(capture));
    }

    @Test
    void joinGame753To751Identity() {
        byte[] capture753 = readResource("cap753-joingame-1-16-3.bin");
        assertEquals(30_847, capture753.length);
        assertArrayEquals(capture753, PlayTranslator.translateJoinGame753To751(capture753));
        assertSame(capture753, PlayTranslator.translateJoinGame753To751(capture753));
        // 751のキャプチャを逆向きに通しても同じ。
        byte[] capture751 = readResource("cap751-joingame-1-16-2.bin");
        assertArrayEquals(capture751, PlayTranslator.translateJoinGame753To751(capture751));
    }

    @Test
    void joinGame751RoundTripBytesIdentical() {
        byte[] capture = readResource("cap751-joingame-1-16-2.bin");
        byte[] via753 = PlayTranslator.translateJoinGame751To753(capture);
        assertArrayEquals(capture, PlayTranslator.translateJoinGame753To751(via753));
    }

    @Test
    void respawn751SyntheticParses() {
        byte[] payload = syntheticRespawn753();
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
        assertEquals(dimensionTypeElement753(256), info.dimension());
        // write→parse の往復でもバイトが変わらない。
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo754(writer, info);
        assertArrayEquals(payload, writer.bytes());
    }

    @Test
    void respawn751To753Identity() {
        byte[] payload = syntheticRespawn753();
        assertArrayEquals(payload, PlayTranslator.translateRespawn751To753(payload));
        assertSame(payload, PlayTranslator.translateRespawn751To753(payload));
    }

    @Test
    void respawn753To751IdentityAndRoundTrip() {
        byte[] payload = syntheticRespawn753();
        assertArrayEquals(payload, PlayTranslator.translateRespawn753To751(payload));
        assertSame(payload, PlayTranslator.translateRespawn753To751(payload));
        byte[] via753 = PlayTranslator.translateRespawn751To753(payload);
        assertArrayEquals(payload, PlayTranslator.translateRespawn753To751(via753));
    }

    @Test
    void clientInformation751To753Identity() {
        byte[] payload = clientInformation751();
        assertArrayEquals(payload, PlayTranslator.translateClientInformation751To753(payload));
        assertSame(payload, PlayTranslator.translateClientInformation751To753(payload));
    }

    @Test
    void clientInformation753To751IdentityAndRoundTrip() {
        byte[] payload = clientInformation751();
        assertArrayEquals(payload, PlayTranslator.translateClientInformation753To751(payload));
        assertSame(payload, PlayTranslator.translateClientInformation753To751(payload));
        byte[] via753 = PlayTranslator.translateClientInformation751To753(payload);
        assertArrayEquals(payload, PlayTranslator.translateClientInformation753To751(via753));
    }

    @Test
    void position751To753Identity() {
        byte[] payload = position751();
        assertArrayEquals(payload, PlayTranslator.translatePosition751To753(payload));
        assertSame(payload, PlayTranslator.translatePosition751To753(payload));
    }

    @Test
    void position753To751IdentityAndRoundTrip() {
        byte[] payload = position751();
        assertArrayEquals(payload, PlayTranslator.translatePosition753To751(payload));
        assertSame(payload, PlayTranslator.translatePosition753To751(payload));
        byte[] via753 = PlayTranslator.translatePosition751To753(payload);
        assertArrayEquals(payload, PlayTranslator.translatePosition753To751(via753));
    }

    @Test
    void packetIds751SpotChecks() {
        assertEquals(0x24, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(751));
        assertEquals(0x24, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(753));
        assertEquals(0x39, PacketIds.PacketName.PLAY_RESPAWN.idFor(751));
        assertEquals(0x39, PacketIds.PacketName.PLAY_RESPAWN.idFor(753));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(751));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(753));
        assertEquals(0x34, PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION.idFor(751));
        assertEquals(0x34, PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION.idFor(753));
        // combat_event／title／world_border は751・753とも単一IDを共有する。
        assertEquals(0x31, PacketIds.PacketName.PLAY_ENTER_COMBAT_EVENT.idFor(751));
        assertEquals(0x31, PacketIds.PacketName.PLAY_ENTER_COMBAT_EVENT.idFor(753));
        assertEquals(0x4f, PacketIds.PacketName.PLAY_SET_TITLE_TEXT.idFor(751));
        assertEquals(0x4f, PacketIds.PacketName.PLAY_SET_TITLE_TEXT.idFor(753));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_INITIALIZE_WORLD_BORDER.idFor(751));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_INITIALIZE_WORLD_BORDER.idFor(753));
        // ping／pong は両版とも transaction。
        assertEquals(0x11, PacketIds.PacketName.PLAY_PING.idFor(751));
        assertEquals(0x11, PacketIds.PacketName.PLAY_PING.idFor(753));
        assertEquals(0x07, PacketIds.PacketName.PLAY_PONG.idFor(751));
        assertEquals(0x07, PacketIds.PacketName.PLAY_PONG.idFor(753));
        // sculk は両版とも無し。declare_recipes／tags／resource_pack_send は両版とも同一。
        assertEquals(-1, PacketIds.PacketName.PLAY_SCULK_VIBRATION_SIGNAL.idFor(751));
        assertEquals(-1, PacketIds.PacketName.PLAY_SCULK_VIBRATION_SIGNAL.idFor(753));
        assertEquals(0x5a, PacketIds.PacketName.PLAY_DECLARE_RECIPES.idFor(751));
        assertEquals(0x5a, PacketIds.PacketName.PLAY_DECLARE_RECIPES.idFor(753));
        assertEquals(0x5b, PacketIds.PacketName.PLAY_TAGS.idFor(751));
        assertEquals(0x5b, PacketIds.PacketName.PLAY_TAGS.idFor(753));
        assertEquals(0x38, PacketIds.PacketName.PLAY_RESOURCE_PACK.idFor(751));
        assertEquals(0x38, PacketIds.PacketName.PLAY_RESOURCE_PACK.idFor(753));
    }
}
