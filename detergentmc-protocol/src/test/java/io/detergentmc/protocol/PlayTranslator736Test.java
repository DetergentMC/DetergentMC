package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * {@link PlayTranslator} の736（1.16.1）⇔751（1.16.2）向け変換テスト。
 *
 * <p>根拠：mc-1.16.1-protocol.json と mc-1.16.2-protocol.json の全パケット・全方向の突き合わせと
 * STAGE-A-736-NOTES.md §8・§11.6、実バニラ1.16.1 jar の javap 実測。
 * Join Game は14フィールドで hardcore が gameMode バイトの bit 3、dimension は文字列、
 * maxPlayers は u8、codec は {"dimension": [...]} だけ。Respawn は dimension だけ文字列⇔NBT。
 * Client Information・Position・combat_event・title・world_border はフィールド同一で、
 * 751は分割されていない（分割は755での変更）ため恒等写像。
 * 生キャプチャはテストリソースにある（cap736-joingame-1-16-1.bin は実バニラ1.16.1の
 * Join Game 1,159バイト、entityId 138。cap751-joingame-1-16-2.bin は実バニラ1.16.2の
 * Join Game 30,847バイト、entityId 306）。
 * 実ゲームクライアントでの確認は未実施。
 */
class PlayTranslator736Test {

    private static byte[] readResource(String name) {
        try (InputStream in = PlayTranslator736Test.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new AssertionError("テストリソースがありません: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("テストリソースが読めません: " + name, e);
        }
    }

    /** 751の dimension_type element 1件を作る（nether。min_y／height は無い）。 */
    private static Tag.CompoundTag dimensionTypeElement751Nether() {
        Map<String, Tag> element = new LinkedHashMap<>();
        element.put("piglin_safe", new Tag.ByteTag((byte) 1));
        element.put("natural", new Tag.ByteTag((byte) 0));
        element.put("ambient_light", new Tag.FloatTag(0.1f));
        element.put("infiniburn", new Tag.StringTag("minecraft:infiniburn_nether"));
        element.put("respawn_anchor_works", new Tag.ByteTag((byte) 1));
        element.put("has_skylight", new Tag.ByteTag((byte) 0));
        element.put("bed_works", new Tag.ByteTag((byte) 0));
        element.put("effects", new Tag.StringTag("minecraft:the_nether"));
        element.put("fixed_time", new Tag.LongTag(18000L));
        element.put("has_raids", new Tag.ByteTag((byte) 0));
        element.put("logical_height", new Tag.IntTag(128));
        element.put("coordinate_scale", new Tag.DoubleTag(8.0));
        element.put("ultrawarm", new Tag.ByteTag((byte) 1));
        element.put("has_ceiling", new Tag.ByteTag((byte) 1));
        return new Tag.CompoundTag(element);
    }

    /** 合成の751 Respawn ペイロード（nether）。 */
    private static byte[] syntheticRespawn751() {
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo754(writer, new PlayTranslator.SpawnInfo754(
                dimensionTypeElement751Nether(), "minecraft:the_nether",
                123456789L, 0, 255, false, false, true));
        return writer.bytes();
    }

    /** 合成の736 Respawn ペイロード（nether）。 */
    private static byte[] syntheticRespawn736() {
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo736(writer, new PlayTranslator.SpawnInfo736(
                "minecraft:the_nether", "minecraft:the_nether",
                123456789L, 0, 255, false, false, true));
        return writer.bytes();
    }

    /** 合成の736 Client Information ペイロード（6フィールド。751と同一）。 */
    private static byte[] clientInformation736() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7f);
        writer.writeVarInt(1);
        return writer.bytes();
    }

    /** 合成の736 Synchronize Player Position ペイロード（7フィールド。751と同一）。 */
    private static byte[] position736() {
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

    /** 合成の Combat Event ペイロード（event 0〜2）。 */
    private static byte[] combatEvent736(int event) {
        Writer writer = new Writer();
        writer.writeVarInt(event);
        if (event == 1) {
            writer.writeVarInt(100);
            writer.writeInt(5);
        } else if (event == 2) {
            writer.writeVarInt(7);
            writer.writeInt(9);
            writer.writeString("{\"text\":\"x\"}");
        }
        return writer.bytes();
    }

    /** 合成の Title ペイロード（action 0＝text／3＝times）。 */
    private static byte[] title736(int action) {
        Writer writer = new Writer();
        writer.writeVarInt(action);
        if (action == 0) {
            writer.writeString("{\"text\":\"hi\"}");
        } else if (action == 3) {
            writer.writeInt(10);
            writer.writeInt(70);
            writer.writeInt(20);
        }
        return writer.bytes();
    }

    /** 合成の World Border ペイロード（action 0＝size／3＝initialize）。 */
    private static byte[] worldBorder736(int action) {
        Writer writer = new Writer();
        writer.writeVarInt(action);
        if (action == 0) {
            writer.writeDouble(60000000.0);
        } else if (action == 3) {
            writer.writeDouble(0.0);
            writer.writeDouble(0.0);
            writer.writeDouble(60000000.0);
            writer.writeDouble(60000000.0);
            writer.writeVarLong(0L);
            writer.writeVarInt(29999984);
            writer.writeVarInt(5);
            writer.writeVarInt(15);
        }
        return writer.bytes();
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

    private static List<String> registryNames(List<Tag> entries) {
        List<String> names = new ArrayList<>(entries.size());
        for (Tag entry : entries) {
            names.add(stringField((Tag.CompoundTag) entry, "name"));
        }
        return names;
    }

    @Test
    void joinGame736LiveCaptureParses() {
        byte[] capture = readResource("cap736-joingame-1-16-1.bin");
        assertEquals(1_159, capture.length);
        PlayTranslator.JoinGame736 join = PlayTranslator.parseJoinGame736(capture);
        assertEquals(138, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                join.worldNames());
        assertEquals("minecraft:overworld", join.dimension());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(4_709_209_815_302_206_686L, join.hashedSeed());
        assertEquals(20, join.maxPlayers());
        assertEquals(10, join.viewDistance());
        assertFalse(join.reducedDebug());
        assertTrue(join.enableRespawnScreen());
        assertFalse(join.isDebug());
        assertFalse(join.isFlat());
        // codec は {"dimension": [...]} だけ。
        assertEquals(List.of("dimension"), List.copyOf(join.dimensionCodec().value().keySet()));
        List<Tag> dimensions = join.dimensionCodec().getList("dimension")
                .map(Tag.ListTag::value)
                .orElseThrow(() -> new AssertionError("dimension がありません"));
        assertEquals(4, dimensions.size());
        List<String> names = new ArrayList<>(dimensions.size());
        for (Tag d : dimensions) {
            names.add(stringField((Tag.CompoundTag) d, "name"));
        }
        assertEquals(List.of("minecraft:overworld", "minecraft:overworld_caves",
                "minecraft:the_nether", "minecraft:the_end"), names);
        // 736の element は "name"／"shrunk" を持ち、"effects"／"coordinate_scale" は無い。
        Tag.CompoundTag overworld = (Tag.CompoundTag) dimensions.get(0);
        assertEquals("minecraft:overworld", stringField(overworld, "name"));
        assertTrue(overworld.get("shrunk").isPresent());
        assertFalse(overworld.get("effects").isPresent());
        assertFalse(overworld.get("coordinate_scale").isPresent());
        assertFalse(overworld.get("min_y").isPresent());
        assertFalse(overworld.get("height").isPresent());
        // 生キャプチャを write してもバイトが変わらない（パーサの検証）。
        assertArrayEquals(capture, PlayTranslator.writeJoinGame736(join));
    }

    @Test
    void joinGame736To751CodecGainsBiomes() {
        byte[] via751 = PlayTranslator.translateJoinGame736To751(
                readResource("cap736-joingame-1-16-1.bin"));
        PlayTranslator.JoinGame754 join = PlayTranslator.parseJoinGame751(via751);
        assertEquals(138, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(20, join.maxPlayers());
        assertEquals(10, join.viewDistance());
        List<Tag> dimensionTypes = registryEntries(join.dimensionCodec(), "minecraft:dimension_type");
        assertEquals(4, dimensionTypes.size());
        assertEquals(List.of("minecraft:overworld", "minecraft:overworld_caves",
                "minecraft:the_nether", "minecraft:the_end"), registryNames(dimensionTypes));
        List<Tag> biomes = registryEntries(join.dimensionCodec(), "minecraft:worldgen/biome");
        assertEquals(79, biomes.size());
        assertEquals("minecraft:plains", stringField((Tag.CompoundTag) biomes.get(1), "name"));
        assertEquals(1, intField((Tag.CompoundTag) biomes.get(1), "id"));
        assertEquals("minecraft:basalt_deltas",
                stringField((Tag.CompoundTag) biomes.get(78), "name"));
        assertEquals(173, intField((Tag.CompoundTag) biomes.get(78), "id"));
        // dimension_type の element は751形式（effects／coordinate_scale 付き、min_y／height 無し）。
        for (Tag entry : dimensionTypes) {
            Tag.CompoundTag element = (Tag.CompoundTag) ((Tag.CompoundTag) entry).get("element")
                    .orElseThrow(() -> new AssertionError("element がありません"));
            assertTrue(element.get("effects").isPresent());
            assertTrue(element.get("coordinate_scale").isPresent());
            assertFalse(element.get("name").isPresent());
            assertFalse(element.get("shrunk").isPresent());
            assertFalse(element.get("min_y").isPresent());
            assertFalse(element.get("height").isPresent());
        }
        // dimension そのものは実バニラ1.16.2の overworld element と同一。
        assertEquals(PlayTranslator.parseJoinGame751(
                readResource("cap751-joingame-1-16-2.bin")).dimension(), join.dimension());
    }

    @Test
    void joinGame751To736To751RoundTripBytesIdentical() {
        byte[] capture = readResource("cap751-joingame-1-16-2.bin");
        assertEquals(30_847, capture.length);
        byte[] via736 = PlayTranslator.translateJoinGame751To736(capture);
        assertEquals(1_159, via736.length);
        assertArrayEquals(capture, PlayTranslator.translateJoinGame736To751(via736));
    }

    @Test
    void joinGame736To751To736RoundTripBytesIdentical() {
        byte[] capture = readResource("cap736-joingame-1-16-1.bin");
        byte[] via751 = PlayTranslator.translateJoinGame736To751(capture);
        assertArrayEquals(capture, PlayTranslator.translateJoinGame751To736(via751));
    }

    @Test
    void joinGame736HardcoreBit() {
        PlayTranslator.JoinGame736 parsed =
                PlayTranslator.parseJoinGame736(readResource("cap736-joingame-1-16-1.bin"));
        PlayTranslator.JoinGame736 hardcore = new PlayTranslator.JoinGame736(
                parsed.entityId(), true, 1, parsed.previousGameMode(), parsed.worldNames(),
                parsed.dimensionCodec(), parsed.dimension(), parsed.worldName(),
                parsed.hashedSeed(), parsed.maxPlayers(), parsed.viewDistance(),
                parsed.reducedDebug(), parsed.enableRespawnScreen(),
                parsed.isDebug(), parsed.isFlat());
        byte[] bytes = PlayTranslator.writeJoinGame736(hardcore);
        // gameMode バイトは entityId（4バイト）の直後で、1 | 8 == 9。
        assertEquals(9, bytes[4]);
        PlayTranslator.JoinGame754 via751 = PlayTranslator.parseJoinGame751(
                PlayTranslator.translateJoinGame736To751(bytes));
        assertTrue(via751.hardcore());
        assertEquals(1, via751.gameMode());
    }

    @Test
    void joinGame736MaxPlayers200U8VsVarint() {
        PlayTranslator.JoinGame736 parsed =
                PlayTranslator.parseJoinGame736(readResource("cap736-joingame-1-16-1.bin"));
        PlayTranslator.JoinGame736 many = new PlayTranslator.JoinGame736(
                parsed.entityId(), false, 0, parsed.previousGameMode(), parsed.worldNames(),
                parsed.dimensionCodec(), parsed.dimension(), parsed.worldName(),
                parsed.hashedSeed(), 200, parsed.viewDistance(),
                parsed.reducedDebug(), parsed.enableRespawnScreen(),
                parsed.isDebug(), parsed.isFlat());
        byte[] bytes736 = PlayTranslator.writeJoinGame736(many);
        // u8 は1バイトのため、maxPlayers 20 のときと同じ長さ。
        assertEquals(1_159, bytes736.length);
        byte[] via751 = PlayTranslator.translateJoinGame736To751(bytes736);
        // varint 200 は2バイトのため、maxPlayers 20 の翻訳より1バイトだけ長い。
        // （736→751はバイオーム79件も付くため全体は 30,848バイトになる。）
        byte[] via751Base = PlayTranslator.translateJoinGame736To751(
                readResource("cap736-joingame-1-16-1.bin"));
        assertEquals(30_847, via751Base.length);
        assertEquals(30_848, via751.length);
        assertEquals(via751Base.length + 1, via751.length);
        assertEquals(200, PlayTranslator.parseJoinGame751(via751).maxPlayers());
        assertArrayEquals(bytes736, PlayTranslator.translateJoinGame751To736(via751));
    }

    @Test
    void respawn736To751To736RoundTripBytesIdentical() {
        byte[] original = syntheticRespawn736();
        byte[] via751 = PlayTranslator.translateRespawn736To751(original);
        // 751側で読むと dimension は NBT（effects 付き）。
        PlayTranslator.SpawnInfo754 info751 =
                PlayTranslator.readSpawnInfo754(new Reader(via751));
        Tag.CompoundTag dimension751 = (Tag.CompoundTag) info751.dimension();
        assertEquals("minecraft:the_nether", stringField(dimension751, "effects"));
        assertEquals(8.0, dimension751.get("coordinate_scale")
                .filter(t -> t instanceof Tag.DoubleTag)
                .map(t -> ((Tag.DoubleTag) t).value())
                .orElseThrow(() -> new AssertionError("coordinate_scale がありません")));
        assertEquals("minecraft:the_nether", info751.worldName());
        assertEquals(123456789L, info751.hashedSeed());
        // Respawn の previousGameMode は u8 で読むため、0xFF は 255 になる。
        assertEquals(255, info751.previousGameMode());
        assertTrue(info751.copyMetadata());
        assertArrayEquals(original, PlayTranslator.translateRespawn751To736(via751));
    }

    @Test
    void respawn751To736DimensionIsString() {
        byte[] via736 = PlayTranslator.translateRespawn751To736(syntheticRespawn751());
        PlayTranslator.SpawnInfo736 info =
                PlayTranslator.readSpawnInfo736(new Reader(via736));
        assertEquals("minecraft:the_nether", info.dimension());
        assertEquals("minecraft:the_nether", info.worldName());
        assertEquals(123456789L, info.hashedSeed());
        assertEquals(255, info.previousGameMode());
        assertTrue(info.copyMetadata());
        assertArrayEquals(syntheticRespawn751(),
                PlayTranslator.translateRespawn736To751(via736));
    }

    @Test
    void clientInformation736To751Identity() {
        assertArrayEquals(clientInformation736(),
                PlayTranslator.translateClientInformation736To751(clientInformation736()));
        assertArrayEquals(clientInformation736(),
                PlayTranslator.translateClientInformation751To736(clientInformation736()));
    }

    @Test
    void position736To751Identity() {
        assertArrayEquals(position736(),
                PlayTranslator.translatePosition736To751(position736()));
        assertArrayEquals(position736(),
                PlayTranslator.translatePosition751To736(position736()));
    }

    @Test
    void combatEvent736To751Identity() {
        for (int event = 0; event <= 2; event++) {
            assertArrayEquals(combatEvent736(event),
                    PlayTranslator.translateCombatEvent736To751(combatEvent736(event)),
                    "event=" + event);
            assertArrayEquals(combatEvent736(event),
                    PlayTranslator.translateCombatEvent751To736(combatEvent736(event)),
                    "event=" + event);
        }
    }

    @Test
    void title736To751Identity() {
        assertArrayEquals(title736(0),
                PlayTranslator.translateTitle736To751(title736(0)));
        assertArrayEquals(title736(0),
                PlayTranslator.translateTitle751To736(title736(0)));
        assertArrayEquals(title736(3),
                PlayTranslator.translateTitle736To751(title736(3)));
        assertArrayEquals(title736(3),
                PlayTranslator.translateTitle751To736(title736(3)));
    }

    @Test
    void worldBorder736To751Identity() {
        assertArrayEquals(worldBorder736(0),
                PlayTranslator.translateWorldBorder736To751(worldBorder736(0)));
        assertArrayEquals(worldBorder736(0),
                PlayTranslator.translateWorldBorder751To736(worldBorder736(0)));
        assertArrayEquals(worldBorder736(3),
                PlayTranslator.translateWorldBorder736To751(worldBorder736(3)));
        assertArrayEquals(worldBorder736(3),
                PlayTranslator.translateWorldBorder751To736(worldBorder736(3)));
    }

    @Test
    void pingPong751To736Drop() {
        assertNull(PlayTranslator.translatePing751To736(new byte[]{0, 0, 0, 1}));
        assertNull(PlayTranslator.translatePong751To736(new byte[]{0, 0, 0, 1}));
        assertNull(PlayTranslator.translateTransaction736ToPing751(new byte[]{0, 0, 1, 1}));
        assertNull(PlayTranslator.translateTransaction736ToPong751(new byte[]{0, 0, 1, 1}));
    }

    @Test
    void displayedRecipe751To736Converts() {
        // Stage B フォローアップで落としから変換に変更した。ViaVersion の
        // Protocol1_16_1To1_16_2 は displayed_recipe を type=0 の
        // crafting_book_data に写す登録があるため、736でも復元できる。
        Writer writer = new Writer();
        writer.writeString("minecraft:oak_planks");
        byte[] out = PlayTranslator.translateDisplayedRecipe751To736(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt()); // type=SHOWN
        assertEquals("minecraft:oak_planks", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void dimensionCodec736To751AndBack() {
        Tag.CompoundTag codec736 = PlayTranslator.parseJoinGame736(
                readResource("cap736-joingame-1-16-1.bin")).dimensionCodec();
        Tag.CompoundTag codec751 = PlayTranslator.translateDimensionCodec736To751(codec736);
        assertEquals(List.of("minecraft:dimension_type", "minecraft:worldgen/biome"),
                List.copyOf(codec751.value().keySet()));
        assertEquals(79, registryEntries(codec751, "minecraft:worldgen/biome").size());
        assertEquals(4, registryEntries(codec751, "minecraft:dimension_type").size());
        // 逆向き：バイオームは落ち、dimension の生 element に戻る。
        Tag.CompoundTag back = PlayTranslator.translateDimensionCodec751To736(
                PlayTranslator.parseJoinGame751(
                        readResource("cap751-joingame-1-16-2.bin")).dimensionCodec());
        assertEquals(List.of("dimension"), List.copyOf(back.value().keySet()));
        List<Tag> dimensions = back.getList("dimension")
                .map(Tag.ListTag::value)
                .orElseThrow(() -> new AssertionError("dimension がありません"));
        assertEquals(4, dimensions.size());
        for (Tag d : dimensions) {
            Tag.CompoundTag element = (Tag.CompoundTag) d;
            assertTrue(element.getString("name").isPresent());
            assertTrue(element.get("shrunk").isPresent());
            assertFalse(element.get("effects").isPresent());
            assertFalse(element.get("coordinate_scale").isPresent());
        }
        assertNull(PlayTranslator.translateDimensionCodec736To751(null));
        assertNull(PlayTranslator.translateDimensionCodec751To736(null));
    }
}
