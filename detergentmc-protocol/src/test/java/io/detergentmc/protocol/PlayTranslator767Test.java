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
 * 767（1.21）の PlayTranslator：Login Success／Join Game／Respawn／
 * Chat Command のエイリアスと、Registry Data の順序並べ替え。
 *
 * <p>エンチャントの順序は minecraft-data から取った実データ
 * （765: 39件・766: 42件・767: 42件のアルファベット順）。
 * 絵画の766の順序は旧来のコード登録順（仮説。実サーバーの
 * Registry Data 取得テストで確認する）とする。
 */
class PlayTranslator767Test {

    // ------------------------------------------------------------------
    // 名称表（検証用の独立したコピー）
    // ------------------------------------------------------------------

    private static final List<String> ENCHANTMENT_765 = List.of(
            "protection", "fire_protection", "feather_falling", "blast_protection",
            "projectile_protection", "respiration", "aqua_affinity", "thorns",
            "depth_strider", "frost_walker", "binding_curse", "soul_speed", "swift_sneak",
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping", "efficiency", "silk_touch", "unbreaking", "fortune",
            "power", "punch", "flame", "infinity", "luck_of_the_sea", "lure", "loyalty",
            "impaling", "riptide", "channeling", "multishot", "quick_charge", "piercing",
            "mending", "vanishing_curse");

    private static final List<String> ENCHANTMENT_766 = List.of(
            "protection", "fire_protection", "feather_falling", "blast_protection",
            "projectile_protection", "respiration", "aqua_affinity", "thorns",
            "depth_strider", "frost_walker", "binding_curse", "soul_speed", "swift_sneak",
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping_edge", "efficiency", "silk_touch", "unbreaking",
            "fortune", "power", "punch", "flame", "infinity", "luck_of_the_sea", "lure",
            "loyalty", "impaling", "riptide", "channeling", "multishot", "quick_charge",
            "piercing", "density", "breach", "wind_burst", "mending", "vanishing_curse");

    private static final List<String> ENCHANTMENT_767 = List.of(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
            "breach", "channeling", "density", "depth_strider", "efficiency",
            "feather_falling", "fire_aspect", "fire_protection", "flame", "fortune",
            "frost_walker", "impaling", "infinity", "knockback", "looting", "loyalty",
            "luck_of_the_sea", "lure", "mending", "multishot", "piercing", "power",
            "projectile_protection", "protection", "punch", "quick_charge", "respiration",
            "riptide", "sharpness", "silk_touch", "smite", "soul_speed", "sweeping_edge",
            "swift_sneak", "thorns", "unbreaking", "vanishing_curse", "wind_burst");

    // 766の絵画は30件のコード登録順。配置可能な26件に加え、コマンド限定の
    // 4件（earth・wind・water・fire、1.19から存在）を含む
    // （ViaVersion mapping-1.20.5.json の paintings で確認）。
    private static final List<String> PAINTING_766 = List.of(
            "kebab", "aztec", "alban", "aztec2", "bomb", "plant", "wasteland", "pool",
            "courbet", "sea", "sunset", "creebet", "wanderer", "graham", "match", "bust",
            "stage", "void", "skull_and_roses", "wither", "fighters", "pointer", "pigscene",
            "burning_skull", "skeleton", "earth", "wind", "water", "fire", "donkey_kong");

    private static final List<String> PAINTING_767 = List.of(
            "alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet",
            "burning_skull", "bust", "cavebird", "changing", "cotan", "courbet", "creebet",
            "donkey_kong", "earth", "endboss", "fern", "fighters", "finding", "fire",
            "graham", "humble", "kebab", "lowmist", "match", "meditative", "orb",
            "owlemons", "passage", "pigscene", "plant", "pointer", "pond", "pool",
            "prairie_ride", "sea", "skeleton", "skull_and_roses", "stage", "sunflowers",
            "sunset", "tides", "unpacked", "void", "wanderer", "wasteland", "water",
            "wind", "wither");

    private static final List<String> BANNER_766 = List.of(
            "base", "border", "bricks", "circle", "creeper", "cross", "curly_border",
            "diagonal_left", "diagonal_right", "diagonal_up_left", "diagonal_up_right",
            "flower", "globe", "gradient", "gradient_up", "half_horizontal",
            "half_horizontal_bottom", "half_vertical", "half_vertical_right", "mojang",
            "piglin", "rhombus", "skull", "small_stripes", "square_bottom_left",
            "square_bottom_right", "square_top_left", "square_top_right", "straight_cross",
            "stripe_bottom", "stripe_center", "stripe_downleft", "stripe_downright",
            "stripe_left", "stripe_middle", "stripe_right", "stripe_top",
            "triangle_bottom", "triangle_top", "triangles_bottom", "triangles_top");

    private static final List<String> TRIM_766 = List.of(
            "coast", "dune", "eye", "host", "raiser", "rib", "sentry", "shaper", "silence",
            "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");

    private static final List<String> DAMAGE_766 = List.of(
            "arrow", "bad_respawn_point", "cactus", "cramming", "dragon_breath", "drown",
            "dry_out", "explosion", "fall", "falling_anvil", "falling_block",
            "falling_stalactite", "fireball", "fireworks", "fly_into_wall", "freeze",
            "generic", "generic_kill", "hot_floor", "in_fire", "in_wall", "indirect_magic",
            "lava", "lightning_bolt", "magic", "mob_attack", "mob_attack_no_aggro",
            "mob_projectile", "on_fire", "out_of_world", "outside_border", "player_attack",
            "player_explosion", "sonic_boom", "spit", "stalagmite", "starve", "sting",
            "sweet_berry_bush", "thorns", "thrown", "trident", "unattributed_fireball",
            "wither", "wither_skull");

    // ------------------------------------------------------------------
    // ヘルパー
    // ------------------------------------------------------------------

    /** 765形式の Login Success（UUID・名前・プロパティ0件）。 */
    private static byte[] loginSuccess765Payload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 765形式の Join Game（dimension は文字列）。 */
    private static byte[] joinGame765Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 765形式の Respawn（dimension は文字列、copyMetadata は末尾）。 */
    private static byte[] respawn765Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** dimension_type（overworld=0）＋biome（plains=0）の codec。 */
    private static Tag.CompoundTag testRegistries() {
        Map<String, Tag> root = new LinkedHashMap<>();
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

    /** 766／767形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** 766／767形式の Registry Data パケットを読み、名前列と hasData 列を返す。 */
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
    void loginSuccess765To767AppendsStrictBool() {
        byte[] out = PlayTranslator.translateLoginSuccess765To767(loginSuccess765Payload());
        assertEquals(loginSuccess765Payload().length + 1, out.length);
        assertEquals(0, out[out.length - 1]);
        assertArrayEquals(PlayTranslator.translateLoginSuccess765To766(loginSuccess765Payload()), out);
    }

    @Test
    void loginSuccess767To765StripsStrictBool() {
        byte[] payload767 = PlayTranslator.translateLoginSuccess765To767(loginSuccess765Payload());
        assertArrayEquals(loginSuccess765Payload(),
                PlayTranslator.translateLoginSuccess767To765(payload767));
    }

    @Test
    void loginSuccess766And767AreIdentity() {
        byte[] payload767 = PlayTranslator.translateLoginSuccess765To767(loginSuccess765Payload());
        assertArrayEquals(payload767, PlayTranslator.translateLoginSuccess766To767(payload767));
        assertArrayEquals(payload767, PlayTranslator.translateLoginSuccess767To766(payload767));
    }

    // ------------------------------------------------------------------
    // Chat Command
    // ------------------------------------------------------------------

    @Test
    void chatCommand767To765ProducesSignedForm() {
        Writer writer = new Writer();
        writer.writeString("say hi");
        byte[] out = PlayTranslator.translateChatCommand767To765(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("say hi", reader.readString());
        assertTrue(reader.remaining() > 0); // 署名欄などが付く
    }

    // ------------------------------------------------------------------
    // Join Game / Respawn
    // ------------------------------------------------------------------

    @Test
    void joinGame765To767Equals765To766() {
        Map<String, Tag.CompoundTag> registries = Map.of(
                "minecraft:dimension_type",
                testRegistries().getCompound("minecraft:dimension_type").orElseThrow(),
                "minecraft:worldgen/biome",
                testRegistries().getCompound("minecraft:worldgen/biome").orElseThrow());
        byte[] expected = PlayTranslator.translateJoinGame765To766(
                PlayTranslator.parseJoinGame765(joinGame765Payload()), registries);
        assertArrayEquals(expected, PlayTranslator.translateJoinGame765To767(
                PlayTranslator.parseJoinGame765(joinGame765Payload()), registries));
    }

    @Test
    void joinGame766And767AreIdentity() {
        byte[] payload = joinGame765Payload();
        assertArrayEquals(payload, PlayTranslator.translateJoinGame766To767(payload));
        assertArrayEquals(payload, PlayTranslator.translateJoinGame767To766(payload));
    }

    @Test
    void respawn765To767Equals765To766() {
        Map<String, Tag.CompoundTag> registries = Map.of(
                "minecraft:dimension_type",
                testRegistries().getCompound("minecraft:dimension_type").orElseThrow(),
                "minecraft:worldgen/biome",
                testRegistries().getCompound("minecraft:worldgen/biome").orElseThrow());
        byte[] expected = PlayTranslator.translateRespawn765To766(respawn765Payload(), registries);
        assertArrayEquals(expected,
                PlayTranslator.translateRespawn765To767(respawn765Payload(), registries));
    }

    @Test
    void respawn766And767AreIdentity() {
        byte[] payload = respawn765Payload();
        Map<String, Tag.CompoundTag> registries = Map.of();
        assertArrayEquals(payload,
                PlayTranslator.translateRespawn766To767(payload, registries));
        assertArrayEquals(payload,
                PlayTranslator.translateRespawn767To766(payload, registries));
    }

    // ------------------------------------------------------------------
    // Registry Data：enchantment
    // ------------------------------------------------------------------

    @Test
    void registryData766To767EnchantmentsReordered() {
        byte[] packet = registryPacket("minecraft:enchantment", ENCHANTMENT_766);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData766To767(packet));
        assertEquals(ENCHANTMENT_767, shortNames(out.names()));
        assertEquals(22, out.names().indexOf("minecraft:mending"));
        assertEquals(27, out.names().indexOf("minecraft:protection"));
        assertTrue(out.hasData().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void registryData767To766EnchantmentsReordered() {
        byte[] packet = registryPacket("minecraft:enchantment", ENCHANTMENT_767);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData767To766(packet));
        assertNotNull(out);
        assertEquals(ENCHANTMENT_766, shortNames(out.names()));
        assertEquals(40, out.names().indexOf("minecraft:mending"));
    }

    @Test
    void registryData765To767EnchantmentsAndPaintings() {
        Map<String, Tag> root = new LinkedHashMap<>();
        root.put("minecraft:enchantment",
                registryCodec("minecraft:enchantment", ENCHANTMENT_765));
        root.put("minecraft:painting_variant",
                registryCodec("minecraft:painting_variant", PAINTING_766));
        byte[] codec = Nbt.writeAnonymous(new Tag.CompoundTag(root));
        List<byte[]> packets = PlayTranslator.translateRegistryData765To767(codec);
        assertEquals(2, packets.size());
        for (byte[] packet : packets) {
            RegistryPacket out = readRegistryPacket(packet);
            if (out.names().contains("minecraft:mending")) {
                assertEquals(ENCHANTMENT_767, shortNames(out.names()));
                // 765の sweeping は767では sweeping_edge になる。
                assertEquals(36, out.names().indexOf("minecraft:sweeping_edge"));
                assertFalse(out.names().contains("minecraft:sweeping"));
            } else {
                assertEquals(PAINTING_767, shortNames(out.names()));
                assertEquals(50, out.names().size());
                assertEquals(23, out.names().indexOf("minecraft:kebab"));
                // 767だけの絵画は hasData=false で埋める。
                assertFalse(out.hasData().get(3)); // backyard
                assertTrue(out.hasData().get(23)); // kebab
            }
        }
    }

    @Test
    void registryData767To765Enchantments() {
        byte[] packet = registryPacket("minecraft:enchantment", ENCHANTMENT_767);
        byte[] codec = PlayTranslator.translateRegistryData767To765(packet);
        assertNotNull(codec);
        Map<String, Tag.CompoundTag> registries = PlayTranslator.parseRegistryData(codec);
        Tag.CompoundTag registry = registries.get("minecraft:enchantment");
        assertNotNull(registry);
        List<Tag> entries = registry.getList("value").map(Tag.ListTag::value).orElse(List.of());
        // 767の42件のうち density・breach・wind_burst は765に無いため落とされ、39件になる。
        assertEquals(39, entries.size());
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (Tag entry : entries) {
            Tag.CompoundTag compound = (Tag.CompoundTag) entry;
            String name = compound.getString("name").orElseThrow();
            int id = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value()).orElseThrow();
            ids.put(name, id);
        }
        assertEquals(37, ids.get("minecraft:mending"));
        assertEquals(0, ids.get("minecraft:protection"));
        // 767の sweeping_edge は765では sweeping（id 19）。
        assertEquals(19, ids.get("minecraft:sweeping"));
        assertFalse(ids.containsKey("minecraft:sweeping_edge"));
        // 765に無い3件は落とされる。
        assertFalse(ids.containsKey("minecraft:density"));
        assertFalse(ids.containsKey("minecraft:breach"));
        assertFalse(ids.containsKey("minecraft:wind_burst"));
    }

    // ------------------------------------------------------------------
    // Registry Data：painting / banner / trim / damage / jukebox_song
    // ------------------------------------------------------------------

    @Test
    void registryData766To767PaintingsPadded() {
        byte[] packet = registryPacket("minecraft:painting_variant", PAINTING_766);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData766To767(packet));
        assertEquals(PAINTING_767, shortNames(out.names()));
        assertEquals(50, out.names().size());
        assertEquals(23, out.names().indexOf("minecraft:kebab"));
        assertFalse(out.hasData().get(3)); // backyard（767だけ）
        assertTrue(out.hasData().get(23)); // kebab（766にもある）
    }

    @Test
    void registryData767To766PaintingsDropped() {
        byte[] packet = registryPacket("minecraft:painting_variant", PAINTING_767);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData767To766(packet));
        assertNotNull(out);
        assertEquals(PAINTING_766, shortNames(out.names()));
        assertEquals(0, out.names().indexOf("minecraft:kebab"));
    }

    @Test
    void registryData766To767BannerTrimDamageSpotChecks() {
        RegistryPacket banner = readRegistryPacket(PlayTranslator.translateRegistryData766To767(
                registryPacket("minecraft:banner_pattern", BANNER_766)));
        assertEquals(43, banner.names().size());
        assertEquals(11, banner.names().indexOf("minecraft:flow"));
        assertEquals(16, banner.names().indexOf("minecraft:guster"));
        assertFalse(banner.hasData().get(11));
        assertFalse(banner.hasData().get(16));
        assertEquals(12, banner.names().indexOf("minecraft:flower"));

        RegistryPacket trim = readRegistryPacket(PlayTranslator.translateRegistryData766To767(
                registryPacket("minecraft:trim_pattern", TRIM_766)));
        assertEquals(18, trim.names().size());
        assertEquals(0, trim.names().indexOf("minecraft:bolt"));
        assertEquals(4, trim.names().indexOf("minecraft:flow"));
        assertFalse(trim.hasData().get(0));
        assertFalse(trim.hasData().get(4));
        assertEquals(8, trim.names().indexOf("minecraft:sentry"));

        RegistryPacket damage = readRegistryPacket(PlayTranslator.translateRegistryData766To767(
                registryPacket("minecraft:damage_type", DAMAGE_766)));
        assertEquals(47, damage.names().size());
        assertEquals(3, damage.names().indexOf("minecraft:campfire"));
        assertEquals(44, damage.names().indexOf("minecraft:wind_charge"));
        assertFalse(damage.hasData().get(3));
        assertFalse(damage.hasData().get(44));
        assertEquals(0, damage.names().indexOf("minecraft:arrow"));
    }

    @Test
    void registryData767JukeboxSongDroppedForOldClients() {
        byte[] packet = registryPacket("minecraft:jukebox_song", List.of("13", "cat"));
        assertNull(PlayTranslator.translateRegistryData767To766(packet));
        assertNull(PlayTranslator.translateRegistryData767To765(packet));
    }

    @Test
    void reorderRegistriesLeavesDimensionTypeUnchanged() {
        Map<String, Tag.CompoundTag> reordered = PlayTranslator.reorderRegistries(
                Map.of("minecraft:dimension_type",
                        testRegistries().getCompound("minecraft:dimension_type").orElseThrow(),
                        "minecraft:worldgen/biome",
                        testRegistries().getCompound("minecraft:worldgen/biome").orElseThrow()),
                765, 767);
        assertEquals(2, reordered.size());
        assertEquals("minecraft:overworld", reordered.get("minecraft:dimension_type")
                .getList("value").orElseThrow().value().get(0) instanceof Tag.CompoundTag c
                        ? c.getString("name").orElse("") : "");
        assertEquals("minecraft:plains", reordered.get("minecraft:worldgen/biome")
                .getList("value").orElseThrow().value().get(0) instanceof Tag.CompoundTag c
                        ? c.getString("name").orElse("") : "");
    }
}
