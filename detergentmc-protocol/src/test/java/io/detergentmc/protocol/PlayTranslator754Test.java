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
 * {@link PlayTranslator} の754（1.16.5／1.16.4）⇔755（1.17）向け変換テスト。
 *
 * <p>根拠：mc-1.16-protocol.json と mc-1.17-protocol.json の全パケット・全方向の突き合わせ
 * （detergentmc-protocol/tools/compare-protocol-754-755.py）と STAGE-A-754-NOTES.md §8・§11.6。
 * Join Game／Respawn のフィールド構成は同一（15／8フィールド）で、違いは dimensionCodec の中身
 * （バイオーム79 vs 81、dimension element に min_y／height 無し）と Client Information
 * （1.16.5は6フィールド）だけ。
 * 生キャプチャはテストリソースにある（cap754-joingame-1-16-5.bin は実バニラ1.16.5の
 * Join Game 30,847バイト、entityId 270。cap755-joingame-1-17.bin は実バニラ1.17の
 * Join Game 31,555バイト、entityId 267。entityId はサーバー起動ごとに変わる
 * （1.16.5の3回の起動で 224・253・270）。
 * 実ゲームクライアントでの確認は未実施。
 */
class PlayTranslator754Test {

    private static byte[] readResource(String name) {
        try (InputStream in = PlayTranslator754Test.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new AssertionError("テストリソースがありません: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("テストリソースが読めません: " + name, e);
        }
    }

    /** dimension_type の element 1件を作る（754形式。min_y／height は無い）。 */
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

    /** dimension_type の element 1件を作る（755形式。min_y=0・height=256 付き）。 */
    private static Tag.CompoundTag dimensionTypeElement755(int logicalHeight) {
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
        element.put("min_y", new Tag.IntTag(0));
        element.put("has_ceiling", new Tag.ByteTag((byte) 0));
        element.put("ultrawarm", new Tag.ByteTag((byte) 0));
        element.put("height", new Tag.IntTag(256));
        return new Tag.CompoundTag(element);
    }

    /** バイオーム element 1件を作る。754形式は depth・scale を含む（755も同じ）。 */
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

    /** 合成の754 codec：dimension_type は overworld 1件、biome は plains／desert／mountains の3件。 */
    private static Tag.CompoundTag smallCodec754() {
        Map<String, Tag> codec = new LinkedHashMap<>();
        codec.put("minecraft:dimension_type", registry("minecraft:dimension_type",
                List.of(registryEntry("minecraft:overworld", 0, dimensionTypeElement754(256)))));
        codec.put("minecraft:worldgen/biome", registry("minecraft:worldgen/biome", List.of(
                registryEntry("minecraft:plains", 1, biomeElement(0.125f, 0.05f, 0.8f, 0x78A7FF)),
                registryEntry("minecraft:desert", 2, biomeElement(0.125f, 0.05f, 2.0f, 0x78A7FF)),
                registryEntry("minecraft:mountains", 3, biomeElement(1.0f, 0.5f, 0.2f, 0x78A7FF)))));
        return new Tag.CompoundTag(codec);
    }

    /** writeJoinGame754 で組み立てた合成の754 Join Game ペイロード。 */
    private static byte[] syntheticJoinGame754() {
        PlayTranslator.JoinGame754 join = new PlayTranslator.JoinGame754(
                270, false, 0, -1,
                List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                smallCodec754(), dimensionTypeElement754(256), "minecraft:overworld",
                123456789L, 5, 10, false, true, false, false);
        return PlayTranslator.writeJoinGame754(join);
    }

    /** 合成の754 Respawn ペイロード。 */
    private static byte[] syntheticRespawn754() {
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo754(writer, new PlayTranslator.SpawnInfo754(
                dimensionTypeElement754(256), "minecraft:overworld",
                123456789L, 0, -1, false, false, false));
        return writer.bytes();
    }

    /** 合成の755 Respawn ペイロード。 */
    private static byte[] syntheticRespawn755() {
        Writer writer = new Writer();
        PlayTranslator.writeSpawnInfo755(writer, new PlayTranslator.SpawnInfo755(
                dimensionTypeElement755(256), "minecraft:overworld",
                123456789L, 0, -1, false, false, false));
        return writer.bytes();
    }

    /** 合成の754 Client Information ペイロード（6フィールド）。 */
    private static byte[] clientInformation754() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7f);
        writer.writeVarInt(1);
        return writer.bytes();
    }

    /** 合成の755 Client Information ペイロード（7フィールド。disableTextFiltering=true）。 */
    private static byte[] clientInformation755() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7f);
        writer.writeVarInt(1);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 合成の754 Synchronize Player Position ペイロード。 */
    private static byte[] position754() {
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

    /** 合成の755 Synchronize Player Position ペイロード（dismountVehicle=true）。 */
    private static byte[] position755() {
        Writer writer = new Writer();
        writer.writeDouble(100.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-200.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0);
        writer.writeVarInt(7);
        writer.writeBoolean(true);
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
    void joinGame754SyntheticParses() {
        PlayTranslator.JoinGame754 join = PlayTranslator.parseJoinGame754(syntheticJoinGame754());
        assertEquals(270, join.entityId());
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
        assertEquals(dimensionTypeElement754(256), join.dimension());
        // write→parse の往復でもバイトが変わらない。
        assertArrayEquals(syntheticJoinGame754(), PlayTranslator.writeJoinGame754(join));
    }

    @Test
    void joinGame754LiveCaptureParses() {
        byte[] capture = readResource("cap754-joingame-1-16-5.bin");
        assertEquals(30_847, capture.length);
        PlayTranslator.JoinGame754 join = PlayTranslator.parseJoinGame754(capture);
        assertEquals(270, join.entityId());
        assertFalse(join.hardcore());
        assertEquals(0, join.gameMode());
        assertEquals(-1, join.previousGameMode());
        assertEquals("minecraft:overworld", join.worldName());
        assertEquals(5, join.maxPlayers());
        assertEquals(10, join.viewDistance());
        assertTrue(join.enableRespawnScreen());
        List<Tag> dimensionTypes = registryEntries(join.dimensionCodec(), "minecraft:dimension_type");
        assertEquals(4, dimensionTypes.size());
        assertEquals(List.of("minecraft:overworld", "minecraft:overworld_caves",
                "minecraft:the_nether", "minecraft:the_end"), registryNames(dimensionTypes));
        List<Tag> biomes = registryEntries(join.dimensionCodec(), "minecraft:worldgen/biome");
        assertEquals(79, biomes.size());
        // dimension element に min_y／height は無い（754の実測）。
        Tag.CompoundTag dimension = (Tag.CompoundTag) join.dimension();
        assertFalse(dimension.get("min_y").isPresent());
        assertFalse(dimension.get("height").isPresent());
        // 生キャプチャを write してもバイトが変わらない（パーサの検証）。
        assertArrayEquals(capture, PlayTranslator.writeJoinGame754(join));
    }

    @Test
    void joinGame755LiveCaptureParses() {
        byte[] capture = readResource("cap755-joingame-1-17.bin");
        assertEquals(31_555, capture.length);
        PlayTranslator.JoinGame755 join = PlayTranslator.parseJoinGame755(capture);
        assertEquals(267, join.entityId());
        assertEquals("minecraft:overworld", join.worldName());
        List<Tag> dimensionTypes = registryEntries(join.dimensionCodec(), "minecraft:dimension_type");
        assertEquals(4, dimensionTypes.size());
        List<Tag> biomes = registryEntries(join.dimensionCodec(), "minecraft:worldgen/biome");
        assertEquals(81, biomes.size());
        assertTrue(registryNames(biomes).contains("minecraft:dripstone_caves"));
        assertTrue(registryNames(biomes).contains("minecraft:lush_caves"));
        // dimension element に min_y=0・height=256 がある（755の実測）。
        Tag.CompoundTag dimension = (Tag.CompoundTag) join.dimension();
        assertEquals(0, intField(dimension, "min_y"));
        assertEquals(256, intField(dimension, "height"));
        assertArrayEquals(capture, PlayTranslator.writeJoinGame755(join));
    }

    @Test
    void joinGame754To755To754RoundTripBytesIdentical() {
        byte[] capture = readResource("cap754-joingame-1-16-5.bin");
        byte[] via755 = PlayTranslator.translateJoinGame754To755(capture);
        assertFalse(java.util.Arrays.equals(capture, via755));
        assertArrayEquals(capture, PlayTranslator.translateJoinGame755To754(via755));
    }

    @Test
    void joinGame755To754To755RoundTripBytesIdentical() {
        byte[] capture = readResource("cap755-joingame-1-17.bin");
        byte[] via754 = PlayTranslator.translateJoinGame755To754(capture);
        assertFalse(java.util.Arrays.equals(capture, via754));
        assertArrayEquals(capture, PlayTranslator.translateJoinGame754To755(via754));
    }

    @Test
    void joinGame754To755CodecGainsBiomesAndHeight() {
        byte[] via755 = PlayTranslator.translateJoinGame754To755(
                readResource("cap754-joingame-1-16-5.bin"));
        PlayTranslator.JoinGame755 join = PlayTranslator.parseJoinGame755(via755);
        List<Tag> biomes = registryEntries(join.dimensionCodec(), "minecraft:worldgen/biome");
        assertEquals(81, biomes.size());
        assertEquals("minecraft:dripstone_caves", stringField((Tag.CompoundTag) biomes.get(79), "name"));
        assertEquals(174, intField((Tag.CompoundTag) biomes.get(79), "id"));
        assertEquals("minecraft:lush_caves", stringField((Tag.CompoundTag) biomes.get(80), "name"));
        assertEquals(175, intField((Tag.CompoundTag) biomes.get(80), "id"));
        // dimension_type の element に min_y=0・height=256 が足される。
        for (Tag entry : registryEntries(join.dimensionCodec(), "minecraft:dimension_type")) {
            Tag.CompoundTag element = (Tag.CompoundTag) ((Tag.CompoundTag) entry).get("element")
                    .orElseThrow(() -> new AssertionError("element がありません"));
            assertEquals(0, intField(element, "min_y"));
            assertEquals(256, intField(element, "height"));
        }
        // dimension そのものにも min_y・height が付く。
        Tag.CompoundTag dimension = (Tag.CompoundTag) join.dimension();
        assertEquals(0, intField(dimension, "min_y"));
        assertEquals(256, intField(dimension, "height"));
    }

    @Test
    void joinGame755To754CodecLosesBiomesAndHeight() {
        byte[] via754 = PlayTranslator.translateJoinGame755To754(
                readResource("cap755-joingame-1-17.bin"));
        PlayTranslator.JoinGame754 join = PlayTranslator.parseJoinGame754(via754);
        List<Tag> biomes = registryEntries(join.dimensionCodec(), "minecraft:worldgen/biome");
        assertEquals(79, biomes.size());
        assertFalse(registryNames(biomes).contains("minecraft:dripstone_caves"));
        assertFalse(registryNames(biomes).contains("minecraft:lush_caves"));
        for (Tag entry : registryEntries(join.dimensionCodec(), "minecraft:dimension_type")) {
            Tag.CompoundTag element = (Tag.CompoundTag) ((Tag.CompoundTag) entry).get("element")
                    .orElseThrow(() -> new AssertionError("element がありません"));
            assertFalse(element.get("min_y").isPresent());
            assertFalse(element.get("height").isPresent());
        }
        Tag.CompoundTag dimension = (Tag.CompoundTag) join.dimension();
        assertFalse(dimension.get("min_y").isPresent());
        assertFalse(dimension.get("height").isPresent());
    }

    @Test
    void respawn754To755To754RoundTripBytesIdentical() {
        byte[] original = syntheticRespawn754();
        byte[] via755 = PlayTranslator.translateRespawn754To755(original);
        // 755側で読むと dimension に min_y=0・height=256 が付いている。
        PlayTranslator.SpawnInfo755 info755 =
                PlayTranslator.readSpawnInfo755(new Reader(via755));
        Tag.CompoundTag dimension755 = (Tag.CompoundTag) info755.dimension();
        assertEquals(0, intField(dimension755, "min_y"));
        assertEquals(256, intField(dimension755, "height"));
        assertEquals("minecraft:overworld", info755.worldName());
        assertEquals(123456789L, info755.hashedSeed());
        // Respawn の previousGameMode は u8 で読むため、0xFF は 255 になる
        // （Join Game の i8 とは違う。ワイヤ上の1バイトは同じ）。
        assertEquals(255, info755.previousGameMode());
        assertArrayEquals(original, PlayTranslator.translateRespawn755To754(via755));
    }

    @Test
    void respawn755To754DimensionLosesMinYHeight() {
        byte[] via754 = PlayTranslator.translateRespawn755To754(syntheticRespawn755());
        PlayTranslator.SpawnInfo754 info =
                PlayTranslator.readSpawnInfo754(new Reader(via754));
        Tag.CompoundTag dimension = (Tag.CompoundTag) info.dimension();
        assertFalse(dimension.get("min_y").isPresent());
        assertFalse(dimension.get("height").isPresent());
        assertEquals("minecraft:overworld", info.worldName());
        assertArrayEquals(syntheticRespawn754(), via754);
    }

    @Test
    void clientInformation754To755AppendsFalse() {
        byte[] via755 = PlayTranslator.translateClientInformation754To755(clientInformation754());
        assertEquals(clientInformation754().length + 1, via755.length);
        // 末尾の1バイトが disableTextFiltering=false。
        assertEquals((byte) 0, via755[via755.length - 1]);
        // 先頭6フィールドはそのまま。
        assertArrayEquals(clientInformation754(),
                java.util.Arrays.copyOf(via755, clientInformation754().length));
    }

    @Test
    void clientInformation755To754DropsBool() {
        assertArrayEquals(clientInformation754(),
                PlayTranslator.translateClientInformation755To754(clientInformation755()));
    }

    @Test
    void clientInformationInvalidPayloadFallsThrough() {
        byte[] truncated = java.util.Arrays.copyOf(clientInformation754(), 3);
        assertArrayEquals(truncated,
                PlayTranslator.translateClientInformation754To755(truncated));
        byte[] truncated755 = java.util.Arrays.copyOf(clientInformation755(), 3);
        assertArrayEquals(truncated755,
                PlayTranslator.translateClientInformation755To754(truncated755));
    }

    @Test
    void combatEvent754To755Splits() {
        // event=0（enter）。ペイロードは空。
        Writer enter = new Writer();
        enter.writeVarInt(0);
        MinecraftPacket enterPacket = PlayTranslator.translateCombatEvent754To755(enter.bytes());
        assertEquals(PacketIds.PacketName.PLAY_ENTER_COMBAT_EVENT.idFor(755), enterPacket.id());
        assertEquals(0, enterPacket.payload().length);
        // event=1（end）。duration＋entityId。
        Writer end = new Writer();
        end.writeVarInt(1);
        end.writeVarInt(600);
        end.writeInt(42);
        MinecraftPacket endPacket = PlayTranslator.translateCombatEvent754To755(end.bytes());
        assertEquals(PacketIds.PacketName.PLAY_END_COMBAT_EVENT.idFor(755), endPacket.id());
        Reader endReader = new Reader(endPacket.payload());
        assertEquals(600, endReader.readVarInt());
        assertEquals(42, endReader.readInt());
        // event=2（death）。playerId＋entityId＋message。
        Writer death = new Writer();
        death.writeVarInt(2);
        death.writeVarInt(7);
        death.writeInt(42);
        death.writeString("{\"text\":\"died\"}");
        MinecraftPacket deathPacket = PlayTranslator.translateCombatEvent754To755(death.bytes());
        assertEquals(PacketIds.PacketName.PLAY_DEATH_COMBAT_EVENT.idFor(755), deathPacket.id());
        Reader deathReader = new Reader(deathPacket.payload());
        assertEquals(7, deathReader.readVarInt());
        assertEquals(42, deathReader.readInt());
        assertEquals("{\"text\":\"died\"}", deathReader.readString());
    }

    @Test
    void combatEvent755To754Reassembles() {
        // enter（空）→ {event=0}。
        assertArrayEquals(new byte[]{0},
                PlayTranslator.translateEnterCombatEvent755To754(new byte[0]));
        // end → {event=1, duration, entityId}。
        Writer end = new Writer();
        end.writeVarInt(600);
        end.writeInt(42);
        Writer expectedEnd = new Writer();
        expectedEnd.writeVarInt(1);
        expectedEnd.writeVarInt(600);
        expectedEnd.writeInt(42);
        assertArrayEquals(expectedEnd.bytes(),
                PlayTranslator.translateEndCombatEvent755To754(end.bytes()));
        // death → {event=2, playerId, entityId, message}。
        Writer death = new Writer();
        death.writeVarInt(7);
        death.writeInt(42);
        death.writeString("{\"text\":\"died\"}");
        Writer expectedDeath = new Writer();
        expectedDeath.writeVarInt(2);
        expectedDeath.writeVarInt(7);
        expectedDeath.writeInt(42);
        expectedDeath.writeString("{\"text\":\"died\"}");
        assertArrayEquals(expectedDeath.bytes(),
                PlayTranslator.translateDeathCombatEvent755To754(death.bytes()));
        // 両方向の往復。
        Writer original = new Writer();
        original.writeVarInt(2);
        original.writeVarInt(7);
        original.writeInt(42);
        original.writeString("{\"text\":\"died\"}");
        MinecraftPacket split = PlayTranslator.translateCombatEvent754To755(original.bytes());
        assertArrayEquals(original.bytes(),
                PlayTranslator.translateDeathCombatEvent755To754(split.payload()));
    }

    @Test
    void combatEventUnknownEventDrops() {
        Writer unknown = new Writer();
        unknown.writeVarInt(3);
        assertNull(PlayTranslator.translateCombatEvent754To755(unknown.bytes()));
        assertNull(PlayTranslator.translateCombatEvent754To755(new byte[0]));
        assertNull(PlayTranslator.translateEnterCombatEvent755To754(new byte[]{1}));
        assertNull(PlayTranslator.translateEndCombatEvent755To754(new byte[]{1}));
        assertNull(PlayTranslator.translateDeathCombatEvent755To754(new byte[]{1}));
    }

    @Test
    void title754To755Splits() {
        // action=0（set_title_text）。
        Writer text = new Writer();
        text.writeVarInt(0);
        text.writeString("{\"text\":\"Hello\"}");
        MinecraftPacket textPacket = PlayTranslator.translateTitle754To755(text.bytes());
        assertEquals(PacketIds.PacketName.PLAY_SET_TITLE_TEXT.idFor(755), textPacket.id());
        assertEquals("{\"text\":\"Hello\"}", new Reader(textPacket.payload()).readString());
        // action=1（set_title_subtitle）。
        Writer subtitle = new Writer();
        subtitle.writeVarInt(1);
        subtitle.writeString("{\"text\":\"Sub\"}");
        MinecraftPacket subtitlePacket = PlayTranslator.translateTitle754To755(subtitle.bytes());
        assertEquals(PacketIds.PacketName.PLAY_SET_TITLE_SUBTITLE.idFor(755), subtitlePacket.id());
        assertEquals("{\"text\":\"Sub\"}", new Reader(subtitlePacket.payload()).readString());
        // action=2（action_bar）。
        Writer bar = new Writer();
        bar.writeVarInt(2);
        bar.writeString("{\"text\":\"Bar\"}");
        MinecraftPacket barPacket = PlayTranslator.translateTitle754To755(bar.bytes());
        assertEquals(PacketIds.PacketName.PLAY_ACTION_BAR.idFor(755), barPacket.id());
        assertEquals("{\"text\":\"Bar\"}", new Reader(barPacket.payload()).readString());
        // action=3（set_title_time）。
        Writer time = new Writer();
        time.writeVarInt(3);
        time.writeInt(10);
        time.writeInt(70);
        time.writeInt(20);
        MinecraftPacket timePacket = PlayTranslator.translateTitle754To755(time.bytes());
        assertEquals(PacketIds.PacketName.PLAY_SET_TITLE_TIME.idFor(755), timePacket.id());
        Reader timeReader = new Reader(timePacket.payload());
        assertEquals(10, timeReader.readInt());
        assertEquals(70, timeReader.readInt());
        assertEquals(20, timeReader.readInt());
        // action=4（clear_titles、reset=false）。
        Writer clear = new Writer();
        clear.writeVarInt(4);
        MinecraftPacket clearPacket = PlayTranslator.translateTitle754To755(clear.bytes());
        assertEquals(PacketIds.PacketName.PLAY_CLEAR_TITLES.idFor(755), clearPacket.id());
        assertFalse(new Reader(clearPacket.payload()).readBoolean());
        // action=5（clear_titles、reset=true）。
        Writer reset = new Writer();
        reset.writeVarInt(5);
        MinecraftPacket resetPacket = PlayTranslator.translateTitle754To755(reset.bytes());
        assertEquals(PacketIds.PacketName.PLAY_CLEAR_TITLES.idFor(755), resetPacket.id());
        assertTrue(new Reader(resetPacket.payload()).readBoolean());
    }

    @Test
    void title755To754Reassembles() {
        // set_title_text → {action=0, text}。
        Writer text = new Writer();
        text.writeString("{\"text\":\"Hello\"}");
        Writer expectedText = new Writer();
        expectedText.writeVarInt(0);
        expectedText.writeString("{\"text\":\"Hello\"}");
        assertArrayEquals(expectedText.bytes(),
                PlayTranslator.translateSetTitleText755To754(text.bytes()));
        // set_title_subtitle → {action=1, text}。
        Writer subtitle = new Writer();
        subtitle.writeString("{\"text\":\"Sub\"}");
        Writer expectedSubtitle = new Writer();
        expectedSubtitle.writeVarInt(1);
        expectedSubtitle.writeString("{\"text\":\"Sub\"}");
        assertArrayEquals(expectedSubtitle.bytes(),
                PlayTranslator.translateSetTitleSubtitle755To754(subtitle.bytes()));
        // set_title_time → {action=3, fadeIn, stay, fadeOut}。
        Writer time = new Writer();
        time.writeInt(10);
        time.writeInt(70);
        time.writeInt(20);
        Writer expectedTime = new Writer();
        expectedTime.writeVarInt(3);
        expectedTime.writeInt(10);
        expectedTime.writeInt(70);
        expectedTime.writeInt(20);
        assertArrayEquals(expectedTime.bytes(),
                PlayTranslator.translateSetTitleTime755To754(time.bytes()));
        // clear_titles reset=false → {action=4}。reset=true → {action=5}。
        Writer clearFalse = new Writer();
        clearFalse.writeBoolean(false);
        assertArrayEquals(new byte[]{4},
                PlayTranslator.translateClearTitles755To754(clearFalse.bytes()));
        Writer clearTrue = new Writer();
        clearTrue.writeBoolean(true);
        assertArrayEquals(new byte[]{5},
                PlayTranslator.translateClearTitles755To754(clearTrue.bytes()));
        // action_bar → {action=2, text}。
        Writer bar = new Writer();
        bar.writeString("{\"text\":\"Bar\"}");
        Writer expectedBar = new Writer();
        expectedBar.writeVarInt(2);
        expectedBar.writeString("{\"text\":\"Bar\"}");
        assertArrayEquals(expectedBar.bytes(),
                PlayTranslator.translateActionBar755To754(bar.bytes()));
        // 両方向の往復（action=3）。
        Writer original = new Writer();
        original.writeVarInt(3);
        original.writeInt(10);
        original.writeInt(70);
        original.writeInt(20);
        MinecraftPacket split = PlayTranslator.translateTitle754To755(original.bytes());
        assertArrayEquals(original.bytes(),
                PlayTranslator.translateSetTitleTime755To754(split.payload()));
    }

    @Test
    void titleUnknownActionDrops() {
        Writer unknown = new Writer();
        unknown.writeVarInt(6);
        assertNull(PlayTranslator.translateTitle754To755(unknown.bytes()));
        assertNull(PlayTranslator.translateTitle754To755(new byte[0]));
        assertNull(PlayTranslator.translateSetTitleText755To754(new byte[0]));
        assertNull(PlayTranslator.translateClearTitles755To754(new byte[0]));
    }

    @Test
    void worldBorder754To755Splits() {
        // action=0（size）。radius は直径の値そのまま。
        Writer size = new Writer();
        size.writeVarInt(0);
        size.writeDouble(60_000_000.0);
        MinecraftPacket sizePacket = PlayTranslator.translateWorldBorder754To755(size.bytes());
        assertEquals(PacketIds.PacketName.PLAY_WORLD_BORDER_SIZE.idFor(755), sizePacket.id());
        assertEquals(60_000_000.0, new Reader(sizePacket.payload()).readDouble());
        // action=1（lerp_size）。
        Writer lerp = new Writer();
        lerp.writeVarInt(1);
        lerp.writeDouble(100.0);
        lerp.writeDouble(200.0);
        lerp.writeVarLong(5000L);
        MinecraftPacket lerpPacket = PlayTranslator.translateWorldBorder754To755(lerp.bytes());
        assertEquals(PacketIds.PacketName.PLAY_WORLD_BORDER_LERP_SIZE.idFor(755), lerpPacket.id());
        Reader lerpReader = new Reader(lerpPacket.payload());
        assertEquals(100.0, lerpReader.readDouble());
        assertEquals(200.0, lerpReader.readDouble());
        assertEquals(5000L, lerpReader.readVarLong());
        // action=2（center）。
        Writer center = new Writer();
        center.writeVarInt(2);
        center.writeDouble(10.5);
        center.writeDouble(-20.5);
        MinecraftPacket centerPacket = PlayTranslator.translateWorldBorder754To755(center.bytes());
        assertEquals(PacketIds.PacketName.PLAY_WORLD_BORDER_CENTER.idFor(755), centerPacket.id());
        Reader centerReader = new Reader(centerPacket.payload());
        assertEquals(10.5, centerReader.readDouble());
        assertEquals(-20.5, centerReader.readDouble());
        // action=3（initialize）。warning は warningBlocks（5）→warningTime（15）の順。
        Writer init = new Writer();
        init.writeVarInt(3);
        init.writeDouble(0.0);
        init.writeDouble(0.0);
        init.writeDouble(60_000_000.0);
        init.writeDouble(60_000_000.0);
        init.writeVarLong(0L);
        init.writeVarInt(29_999_984);
        init.writeVarInt(5);
        init.writeVarInt(15);
        MinecraftPacket initPacket = PlayTranslator.translateWorldBorder754To755(init.bytes());
        assertEquals(PacketIds.PacketName.PLAY_INITIALIZE_WORLD_BORDER.idFor(755), initPacket.id());
        Reader initReader = new Reader(initPacket.payload());
        assertEquals(0.0, initReader.readDouble());
        assertEquals(0.0, initReader.readDouble());
        assertEquals(60_000_000.0, initReader.readDouble());
        assertEquals(60_000_000.0, initReader.readDouble());
        assertEquals(0L, initReader.readVarLong());
        assertEquals(29_999_984, initReader.readVarInt());
        assertEquals(5, initReader.readVarInt());
        assertEquals(15, initReader.readVarInt());
        // action=4（warning_delay）。
        Writer delay = new Writer();
        delay.writeVarInt(4);
        delay.writeVarInt(15);
        MinecraftPacket delayPacket = PlayTranslator.translateWorldBorder754To755(delay.bytes());
        assertEquals(PacketIds.PacketName.PLAY_WORLD_BORDER_WARNING_DELAY.idFor(755), delayPacket.id());
        assertEquals(15, new Reader(delayPacket.payload()).readVarInt());
        // action=5（warning_reach）。
        Writer reach = new Writer();
        reach.writeVarInt(5);
        reach.writeVarInt(5);
        MinecraftPacket reachPacket = PlayTranslator.translateWorldBorder754To755(reach.bytes());
        assertEquals(PacketIds.PacketName.PLAY_WORLD_BORDER_WARNING_REACH.idFor(755), reachPacket.id());
        assertEquals(5, new Reader(reachPacket.payload()).readVarInt());
    }

    @Test
    void worldBorder755To754Reassembles() {
        // initialize → {action=3, ...}。warning の順序は warningBlocks→warningTime。
        Writer init = new Writer();
        init.writeDouble(0.0);
        init.writeDouble(0.0);
        init.writeDouble(59_999_968.0);
        init.writeDouble(59_999_968.0);
        init.writeVarLong(0L);
        init.writeVarInt(29_999_984);
        init.writeVarInt(5);
        init.writeVarInt(15);
        Writer expectedInit = new Writer();
        expectedInit.writeVarInt(3);
        expectedInit.writeDouble(0.0);
        expectedInit.writeDouble(0.0);
        expectedInit.writeDouble(59_999_968.0);
        expectedInit.writeDouble(59_999_968.0);
        expectedInit.writeVarLong(0L);
        expectedInit.writeVarInt(29_999_984);
        expectedInit.writeVarInt(5);
        expectedInit.writeVarInt(15);
        assertArrayEquals(expectedInit.bytes(),
                PlayTranslator.translateInitializeWorldBorder755To754(init.bytes()));
        // center → {action=2, x, z}。
        Writer center = new Writer();
        center.writeDouble(10.5);
        center.writeDouble(-20.5);
        Writer expectedCenter = new Writer();
        expectedCenter.writeVarInt(2);
        expectedCenter.writeDouble(10.5);
        expectedCenter.writeDouble(-20.5);
        assertArrayEquals(expectedCenter.bytes(),
                PlayTranslator.translateWorldBorderCenter755To754(center.bytes()));
        // lerp_size → {action=1, ...}。
        Writer lerp = new Writer();
        lerp.writeDouble(100.0);
        lerp.writeDouble(200.0);
        lerp.writeVarLong(5000L);
        Writer expectedLerp = new Writer();
        expectedLerp.writeVarInt(1);
        expectedLerp.writeDouble(100.0);
        expectedLerp.writeDouble(200.0);
        expectedLerp.writeVarLong(5000L);
        assertArrayEquals(expectedLerp.bytes(),
                PlayTranslator.translateWorldBorderLerpSize755To754(lerp.bytes()));
        // size → {action=0, diameter}。
        Writer size = new Writer();
        size.writeDouble(59_999_968.0);
        Writer expectedSize = new Writer();
        expectedSize.writeVarInt(0);
        expectedSize.writeDouble(59_999_968.0);
        assertArrayEquals(expectedSize.bytes(),
                PlayTranslator.translateWorldBorderSize755To754(size.bytes()));
        // warning_delay → {action=4, warningTime}。
        Writer delay = new Writer();
        delay.writeVarInt(15);
        Writer expectedDelay = new Writer();
        expectedDelay.writeVarInt(4);
        expectedDelay.writeVarInt(15);
        assertArrayEquals(expectedDelay.bytes(),
                PlayTranslator.translateWorldBorderWarningDelay755To754(delay.bytes()));
        // warning_reach → {action=5, warningBlocks}。
        Writer reach = new Writer();
        reach.writeVarInt(5);
        Writer expectedReach = new Writer();
        expectedReach.writeVarInt(5);
        expectedReach.writeVarInt(5);
        assertArrayEquals(expectedReach.bytes(),
                PlayTranslator.translateWorldBorderWarningReach755To754(reach.bytes()));
        // 両方向の往復（action=3）。
        Writer original = new Writer();
        original.writeVarInt(3);
        original.writeDouble(0.0);
        original.writeDouble(0.0);
        original.writeDouble(60_000_000.0);
        original.writeDouble(60_000_000.0);
        original.writeVarLong(0L);
        original.writeVarInt(29_999_984);
        original.writeVarInt(5);
        original.writeVarInt(15);
        MinecraftPacket split = PlayTranslator.translateWorldBorder754To755(original.bytes());
        assertArrayEquals(original.bytes(),
                PlayTranslator.translateInitializeWorldBorder755To754(split.payload()));
    }

    @Test
    void worldBorderUnknownActionDrops() {
        Writer unknown = new Writer();
        unknown.writeVarInt(6);
        assertNull(PlayTranslator.translateWorldBorder754To755(unknown.bytes()));
        assertNull(PlayTranslator.translateWorldBorder754To755(new byte[0]));
        assertNull(PlayTranslator.translateInitializeWorldBorder755To754(new byte[0]));
        assertNull(PlayTranslator.translateWorldBorderSize755To754(new byte[0]));
    }

    @Test
    void pingPongTransactionDrops() {
        // 755 ping {id i32} → 落とす。
        Writer ping = new Writer();
        ping.writeInt(12345);
        assertNull(PlayTranslator.translatePing755To754(ping.bytes()));
        assertNull(PlayTranslator.translatePing755To754(new byte[0]));
        // 754 transaction {windowId i8, actionNumber i16, accepted bool} → 落とす。
        Writer transaction = new Writer();
        transaction.writeByte(0);
        transaction.writeShort((short) 12345);
        transaction.writeBoolean(true);
        assertNull(PlayTranslator.translateTransaction754ToPing755(transaction.bytes()));
        // 755 pong {id i32} → 落とす。
        Writer pong = new Writer();
        pong.writeInt(12345);
        assertNull(PlayTranslator.translatePong755To754(pong.bytes()));
        // 754 transaction → 落とす。
        assertNull(PlayTranslator.translateTransaction754ToPong755(transaction.bytes()));
    }

    @Test
    void sculkVibrationSignalDrops() {
        Writer sculk = new Writer();
        sculk.writeVarInt(1);
        sculk.writeVarInt(2);
        sculk.writeVarInt(3);
        assertNull(PlayTranslator.translateSculkVibrationSignal755To754(sculk.bytes()));
        assertNull(PlayTranslator.translateSculkVibrationSignal755To754(new byte[0]));
    }

    @Test
    void position754To755AppendsFalse() {
        byte[] via755 = PlayTranslator.translatePosition754To755(position754());
        assertEquals(position754().length + 1, via755.length);
        assertEquals((byte) 0, via755[via755.length - 1]);
        assertArrayEquals(position754(),
                java.util.Arrays.copyOf(via755, position754().length));
    }

    @Test
    void position755To754DropsBool() {
        assertArrayEquals(position754(),
                PlayTranslator.translatePosition755To754(position755()));
        assertArrayEquals(position754(), PlayTranslator.translatePosition755To754(
                PlayTranslator.translatePosition754To755(position754())));
    }

    @Test
    void packetIds754SpotChecks() {
        assertEquals(0x24, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(754));
        assertEquals(0x26, PacketIds.PacketName.PLAY_JOIN_GAME.idFor(755));
        assertEquals(0x39, PacketIds.PacketName.PLAY_RESPAWN.idFor(754));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_RESPAWN.idFor(755));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(754));
        assertEquals(0x05, PacketIds.PacketName.PLAY_CLIENT_INFORMATION.idFor(755));
        assertEquals(0x31, PacketIds.PacketName.PLAY_ENTER_COMBAT_EVENT.idFor(754));
        assertEquals(0x34, PacketIds.PacketName.PLAY_ENTER_COMBAT_EVENT.idFor(755));
        assertEquals(0x4f, PacketIds.PacketName.PLAY_SET_TITLE_TEXT.idFor(754));
        assertEquals(0x59, PacketIds.PacketName.PLAY_SET_TITLE_TEXT.idFor(755));
        assertEquals(0x3d, PacketIds.PacketName.PLAY_INITIALIZE_WORLD_BORDER.idFor(754));
        assertEquals(0x20, PacketIds.PacketName.PLAY_INITIALIZE_WORLD_BORDER.idFor(755));
        assertEquals(0x11, PacketIds.PacketName.PLAY_PING.idFor(754));
        assertEquals(0x30, PacketIds.PacketName.PLAY_PING.idFor(755));
        assertEquals(0x07, PacketIds.PacketName.PLAY_PONG.idFor(754));
        assertEquals(0x1d, PacketIds.PacketName.PLAY_PONG.idFor(755));
        assertEquals(-1, PacketIds.PacketName.PLAY_SCULK_VIBRATION_SIGNAL.idFor(754));
        assertEquals(0x05, PacketIds.PacketName.PLAY_SCULK_VIBRATION_SIGNAL.idFor(755));
    }

    @Test
    void invalidJoinGameAndRespawnFallThrough() {
        byte[] truncatedJoin = java.util.Arrays.copyOf(syntheticJoinGame754(), 10);
        assertArrayEquals(truncatedJoin,
                PlayTranslator.translateJoinGame754To755(truncatedJoin));
        byte[] truncatedRespawn = java.util.Arrays.copyOf(syntheticRespawn754(), 4);
        assertArrayEquals(truncatedRespawn,
                PlayTranslator.translateRespawn754To755(truncatedRespawn));
        byte[] truncatedPosition = java.util.Arrays.copyOf(position754(), 5);
        assertArrayEquals(truncatedPosition,
                PlayTranslator.translatePosition754To755(truncatedPosition));
    }
}
