package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の755（1.17）⇔756（1.17.1）向け変換テスト。
 *
 * <p>755と756は PlayTranslator の担当範囲では構造が同一（実測）：
 * Join Game（0x26両方。simulationDistance は両方無し）、Respawn（0x3d両方。8フィールド同一）、
 * dimensionCodec（dimension_type 4件・バイオーム81件とも同一順序・同一内容。レジストリ差分0件）、
 * Client Information（0x05両方。disableTextFiltering の7フィールド同一）。
 * 根拠：mc-1.17-protocol.json と mc-1.17.1-protocol.json の全173パケットの突き合わせ
 * （packet_login／packet_respawn／packet_settings はフィールド構成まで完全同一）と
 * STAGE-A-755-NOTES.md §11.6（実バニラ1.17と1.17.1の Join Game は両方31,555バイトで、
 * 差分は entityId 1バイト＋hashedSeed 8バイトのみ）。
 * そのため本節の変換器はすべて素通し（ペイロード unchanged）である。
 *
 * <p>実バニラ1.17の Join Game 生キャプチャはテストリソースに無い
 * （src/test/resources には cap756-chunk-21-17.bin のみ。.tmp-755 にも1.17の Join Game
 * キャプチャは無い）。そのため Join Game のテストデータは {@link PlayTranslator#writeJoinGame755}
 * で組み立てた合成ペイロードで、往復のバイト完全一致を確認する。生キャプチャとの照合は
 * Stage A §11.6 のバイト比較（両方31,555バイト）がすでに行われている。
 * 実ゲームクライアントでの確認は未実施。
 *
 * <p>protocol.json の突き合わせで実測の違いが見つかったのは play の4パケットだけ
 * （Set Slot／Window Items／Window Click／Edit Book。いずれも stateId の有無などの窓系
 * フィールド差分で、IDは同一）。これらは PlayTranslator の担当外（Set Slot／Window Items／
 * Window Click は ChunkTranslator、Edit Book は未対応）で、本テストでは扱わない。
 */
class PlayTranslator755Test {

    /** dimension_type の element 1件を作る（overworld。1.17／1.17.1は height 256／min_y 0）。 */
    private static Tag.CompoundTag dimensionTypeElement(int logicalHeight, int minY, int height) {
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
        element.put("min_y", new Tag.IntTag(minY));
        element.put("has_ceiling", new Tag.ByteTag((byte) 0));
        element.put("ultrawarm", new Tag.ByteTag((byte) 0));
        element.put("height", new Tag.IntTag(height));
        return new Tag.CompoundTag(element);
    }

    /** バイオーム element 1件を作る。755形式は756と同じく depth・scale を含む。 */
    private static Tag.CompoundTag biomeElement(float depth, float scale, float temperature, int skyColor) {
        Map<String, Tag> effects = new LinkedHashMap<>();
        effects.put("sky_color", new Tag.IntTag(skyColor));
        effects.put("water_fog_color", new Tag.IntTag(0x050533));
        effects.put("fog_color", new Tag.IntTag(0xC0D8FF));
        effects.put("water_color", new Tag.IntTag(0x3F76E4));
        Map<String, Tag> element = new LinkedHashMap<>();
        element.put("precipitation", new Tag.StringTag("rain"));
        element.put("effects", new Tag.CompoundTag(effects));
        element.put("depth", new Tag.FloatTag(depth));
        element.put("temperature", new Tag.FloatTag(temperature));
        element.put("scale", new Tag.FloatTag(scale));
        element.put("downfall", new Tag.FloatTag(0.5f));
        element.put("category", new Tag.StringTag("plains"));
        return new Tag.CompoundTag(element);
    }

    private static Tag.CompoundTag registryEntry(String name, int id, Tag element) {
        Map<String, Tag> entry = new LinkedHashMap<>();
        entry.put("name", new Tag.StringTag(name));
        entry.put("id", new Tag.IntTag(id));
        if (element != null) {
            entry.put("element", element);
        }
        return new Tag.CompoundTag(entry);
    }

    private static Tag.CompoundTag registry(String type, List<Tag> entries) {
        Map<String, Tag> registry = new LinkedHashMap<>();
        registry.put("type", new Tag.StringTag(type));
        registry.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
        return new Tag.CompoundTag(registry);
    }

    /** 合成の755 codec：dimension_type は overworld 1件、biome は plains／desert／mountains の3件。 */
    private static Tag.CompoundTag smallCodec755() {
        Map<String, Tag> codec = new LinkedHashMap<>();
        codec.put("minecraft:dimension_type", registry("minecraft:dimension_type",
                List.of(registryEntry("minecraft:overworld", 0, dimensionTypeElement(256, 0, 256)))));
        codec.put("minecraft:worldgen/biome", registry("minecraft:worldgen/biome", List.of(
                registryEntry("minecraft:plains", 1, biomeElement(0.125f, 0.05f, 0.8f, 0x78A7FF)),
                registryEntry("minecraft:desert", 2, biomeElement(0.125f, 0.05f, 2.0f, 0x78A7FF)),
                registryEntry("minecraft:mountains", 3, biomeElement(1.0f, 0.5f, 0.2f, 0x78A7FF)))));
        return new Tag.CompoundTag(codec);
    }

    private static Tag.CompoundTag overworldElement() {
        return dimensionTypeElement(256, 0, 256);
    }

    /** writeJoinGame755 で組み立てた合成の755 Join Game ペイロード。 */
    private static byte[] syntheticJoinGame755() {
        PlayTranslator.JoinGame755 join = new PlayTranslator.JoinGame755(
                349, false, 0, -1,
                List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                smallCodec755(), overworldElement(), "minecraft:overworld",
                123456789L, 5, 10, false, true, false, false);
        return PlayTranslator.writeJoinGame755(join);
    }

    private static int intField(Tag.CompoundTag compound, String name) {
        return compound.get(name)
                .filter(t -> t instanceof Tag.IntTag)
                .map(t -> ((Tag.IntTag) t).value())
                .orElseThrow(() -> new AssertionError("int フィールドがありません: " + name));
    }

    private static String stringField(Tag.CompoundTag compound, String name) {
        return compound.getString(name)
                .orElseThrow(() -> new AssertionError("string フィールドがありません: " + name));
    }

    private static List<Tag> registryEntries(Tag.CompoundTag codec, String registryId) {
        return codec.getCompound(registryId)
                .flatMap(r -> r.getList("value"))
                .map(Tag.ListTag::value)
                .orElseThrow(() -> new AssertionError("レジストリがありません: " + registryId));
    }

    @Test
    void joinGame755SyntheticParses() {
        PlayTranslator.JoinGame755 join = PlayTranslator.parseJoinGame755(syntheticJoinGame755());
        assertEquals(349, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                join.worldNames());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(123456789L, join.hashedSeed());
        assertEquals(5, join.maxPlayers());
        assertEquals(10, join.viewDistance());
        assertFalse(join.reducedDebug());
        assertTrue(join.enableRespawnScreen());
        assertFalse(join.isDebug());
        assertFalse(join.isFlat());
        List<Tag> dimensionTypes = registryEntries(join.dimensionCodec(), "minecraft:dimension_type");
        assertEquals(1, dimensionTypes.size());
        assertEquals("minecraft:overworld", stringField((Tag.CompoundTag) dimensionTypes.get(0), "name"));
        assertEquals(0, intField((Tag.CompoundTag) dimensionTypes.get(0), "id"));
        List<Tag> biomes = registryEntries(join.dimensionCodec(), "minecraft:worldgen/biome");
        assertEquals(3, biomes.size());
        // Join Game の dimension は overworld の element と同一。
        assertEquals(overworldElement(), join.dimension());
        // write→parse の往復でもバイトが変わらない。
        assertArrayEquals(syntheticJoinGame755(), PlayTranslator.writeJoinGame755(join));
    }

    @Test
    void joinGame755To756RoundTripBytesIdentical() {
        byte[] payload755 = syntheticJoinGame755();
        byte[] to756 = PlayTranslator.translateJoinGame755To756(payload755);
        assertArrayEquals(payload755, to756);
        byte[] back = PlayTranslator.translateJoinGame756To755(to756);
        assertArrayEquals(payload755, back);
    }

    @Test
    void joinGame756To755RoundTripBytesIdentical() {
        // 756側の合成ペイロード（755と同一の codec・dimension）も往復でバイト完全一致する。
        PlayTranslator.JoinGame756 join756 = new PlayTranslator.JoinGame756(
                374, false, 0, -1,
                List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                smallCodec755(), overworldElement(), "minecraft:overworld",
                987654321L, 5, 10, false, true, false, false);
        byte[] payload756 = PlayTranslator.writeJoinGame756(join756);
        byte[] to755 = PlayTranslator.translateJoinGame756To755(payload756);
        assertArrayEquals(payload756, to755);
        byte[] back = PlayTranslator.translateJoinGame755To756(to755);
        assertArrayEquals(payload756, back);
        // 756側でパースした中身が755側のパース結果と entityId・seed 以外同一であること。
        PlayTranslator.JoinGame755 join755 = PlayTranslator.parseJoinGame755(to755);
        assertEquals(374, join755.entityId());
        assertEquals(987654321L, join755.hashedSeed());
        assertEquals("minecraft:overworld", join755.worldName());
    }

    @Test
    void dimensionCodec755To756Identical() {
        Tag.CompoundTag codec = smallCodec755();
        assertSame(codec, PlayTranslator.translateDimensionCodec755To756(codec));
        assertSame(codec, PlayTranslator.translateDimensionCodec756To755(codec));
    }

    @Test
    void dimensionCodecRoundTripSmall() {
        Tag.CompoundTag codec755 = smallCodec755();
        Tag.CompoundTag to756 = PlayTranslator.translateDimensionCodec755To756(codec755);
        Tag.CompoundTag back = PlayTranslator.translateDimensionCodec756To755(to756);
        assertEquals(codec755, back);
        assertEquals(1, registryEntries(back, "minecraft:dimension_type").size());
        assertEquals(3, registryEntries(back, "minecraft:worldgen/biome").size());
        Tag.CompoundTag overworld = (Tag.CompoundTag) ((Tag.CompoundTag)
                registryEntries(back, "minecraft:dimension_type").get(0)).get("element")
                .orElseThrow(() -> new AssertionError("element がありません"));
        assertEquals(256, intField(overworld, "height"));
        assertEquals(0, intField(overworld, "min_y"));
        assertEquals(256, intField(overworld, "logical_height"));
    }

    @Test
    void respawn755To756AndBack() {
        PlayTranslator.SpawnInfo755 info = new PlayTranslator.SpawnInfo755(
                overworldElement(), "minecraft:overworld", 987654321L, 0, 255, false, false, true);
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo755(writer, info);
        byte[] payload755 = writer.bytes();
        byte[] to756 = PlayTranslator.translateRespawn755To756(payload755);
        assertArrayEquals(payload755, to756);
        byte[] back = PlayTranslator.translateRespawn756To755(to756);
        assertArrayEquals(payload755, back);
        // 756側で読んだ中身が同じであること（previousGameMode は u8 のまま255が通る）。
        Reader reader756 = new Reader(to756);
        PlayTranslator.SpawnInfo756 info756 = PlayTranslator.readSpawnInfo756(reader756);
        assertEquals(overworldElement(), info756.dimension());
        assertEquals("minecraft:overworld", info756.worldName());
        assertEquals(255, info756.previousGameMode());
        assertTrue(info756.copyMetadata());
    }

    @Test
    void respawn756To755AndBack() {
        PlayTranslator.SpawnInfo756 info = new PlayTranslator.SpawnInfo756(
                overworldElement(), "minecraft:the_nether", 111L, 0, 0, false, false, false);
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo756(writer, info);
        byte[] payload756 = writer.bytes();
        byte[] to755 = PlayTranslator.translateRespawn756To755(payload756);
        assertArrayEquals(payload756, to755);
        byte[] back = PlayTranslator.translateRespawn755To756(to755);
        assertArrayEquals(payload756, back);
        Reader reader755 = new Reader(to755);
        PlayTranslator.SpawnInfo755 info755 = PlayTranslator.readSpawnInfo755(reader755);
        assertEquals("minecraft:the_nether", info755.worldName());
        assertFalse(info755.copyMetadata());
    }

    @Test
    void clientInformation755To756RoundTrip() {
        Writer writer = new Writer();
        writer.writeString("en_US");
        writer.writeByte(12);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0xFF);
        writer.writeVarInt(1);
        writer.writeBoolean(true); // disableTextFiltering = true
        byte[] payload755 = writer.bytes();
        byte[] to756 = PlayTranslator.translateClientInformation755To756(payload755);
        assertArrayEquals(payload755, to756);
        byte[] back = PlayTranslator.translateClientInformation756To755(to756);
        assertArrayEquals(payload755, back);
        // 756側でも disableTextFiltering がそのまま読める（反転されない）。
        Reader reader = new Reader(to756);
        assertEquals("en_US", reader.readString());
        assertEquals(12, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertTrue(reader.readBoolean()); // disableTextFiltering = true のまま
        assertEquals(0, reader.remaining());
    }

    @Test
    void invalidPayloadsFallThrough() {
        byte[] truncated = {0x02, 0x65, 0x6E}; // locale の途中で切れたもの
        assertArrayEquals(truncated, PlayTranslator.translateClientInformation755To756(truncated));
        assertArrayEquals(truncated, PlayTranslator.translateClientInformation756To755(truncated));
        byte[] garbage = {0x00, 0x00, 0x00}; // Join Game／Respawn として読めないもの
        assertArrayEquals(garbage, PlayTranslator.translateJoinGame755To756(garbage));
        assertArrayEquals(garbage, PlayTranslator.translateJoinGame756To755(garbage));
        assertArrayEquals(garbage, PlayTranslator.translateRespawn755To756(garbage));
        assertArrayEquals(garbage, PlayTranslator.translateRespawn756To755(garbage));
    }

    @Test
    void packetIds755Vs756() {
        // ID もペイロードも同一のため変換器が要らないもの（protocol.json で確認）。
        assertEquals(0x26, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(755));
        assertEquals(0x26, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(756));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_RESPAWN.idFor(755));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_RESPAWN.idFor(756));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(755));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(756));
        assertEquals(0x58, PacketIds.PacketName.PLAY_UPDATE_TIME.idFor(755));
        assertEquals(0x58, PacketIds.PacketName.PLAY_UPDATE_TIME.idFor(756));
        assertEquals(0x0f, PacketIds.PacketName.PLAY_CHAT.idFor(755));
        assertEquals(0x0f, PacketIds.PacketName.PLAY_CHAT.idFor(756));
        assertEquals(0x36, PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE.idFor(755));
        assertEquals(0x36, PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE.idFor(756));
        assertEquals(0x48, PacketIds.PacketName.PLAY_HELD_ITEM_SLOT.idFor(755));
        assertEquals(0x48, PacketIds.PacketName.PLAY_HELD_ITEM_SLOT.idFor(756));
        assertEquals(0x22, PacketIds.PacketName.PLAY_CHUNK_DATA.idFor(755));
        assertEquals(0x22, PacketIds.PacketName.PLAY_CHUNK_DATA.idFor(756));
    }
}
