package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 1.21.11（プロトコル774）の PlayTranslator 変換テスト。
 *
 * <p>773→774の変更（minecraft-data の表と実バニラ1.21.11の Registry Data
 * キャプチャに準拠。パケットIDは773と同一で0変更）：
 * エンチャントは lunge を21番に挿入（42→43件。lure は22、mending は23、
 * wind_burst は42になる）。ダメージ種別は spear を37番に挿入（49→50件。
 * spit は38、wither_skull は49になる）。アイテムは17件増え（スポーンエッグ
 * 4件・ネザライトの馬鎧1件・槍7件・ノーチラスの防具5件。music_disc.lava_chicken
 * は1304→1316）、エンティティは4件増える（camel_husk 20・nautilus 88・
 * parched 97・zombie_nautilus 152。pig は97→100）。
 * ブロック状態・バイオーム・属性・メニュー・パーティクル・ブロックエンティティ
 * 種別・絵画・ジュークボックスは同一。Player Info／Set Held Item／Vehicle
 * Move／Player Chat は形式が同一のため素通し。Chat Message の末尾 checksum
 * u8 は773・774ともあるため剥がしも付け足しもしない。
 * Registry Data の entity_type・sound・item・particle は並べ替えず素通し
 * する（既存の全ペアと同じ設計）。773→774では新エンティティ4件が増えるが
 * Registry Data には反映されない。Spawn Entity 等の種別ID自体は翻訳される。
 * 実機クライアントでの扱いは未確認。残る制限として報告する。
 */
class PlayTranslator774Test {

    // ------------------------------------------------------------------
    // ヘルパー（PlayTranslator773Test と同形）
    // ------------------------------------------------------------------

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

    /** 766／767／768／769／771／772／773／774形式の Registry Data 1パケット（レジストリ1個分）。 */
    private static byte[] registryPacket(String registryId, List<String> names) {
        return PlayTranslator.buildRegistryData766(
                Map.of(registryId, registryCodec(registryId, names))).get(0);
    }

    /** Registry Data パケットを読み、名前列と hasData 列を返す。 */
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

    /** 773／774のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes773() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            if (i == 38) {
                names.add("old_growth_spruce_taiga");
            } else if (i == 39) {
                names.add("pale_garden");
            } else if (i == 40) {
                names.add("plains");
            } else if (i < 38) {
                names.add("biome_" + i);
            } else {
                names.add("biome_" + (i - 1));
            }
        }
        return names;
    }

    /** 774のエンチャント43件（minecraft-data enchantments-1.21.11.json の並び。lunge は21番）。 */
    private static List<String> enchantments774() {
        return List.of("aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
                "breach", "channeling", "density", "depth_strider", "efficiency", "feather_falling",
                "fire_aspect", "fire_protection", "flame", "fortune", "frost_walker", "impaling",
                "infinity", "knockback", "looting", "loyalty", "luck_of_the_sea", "lunge",
                "lure", "mending", "multishot", "piercing", "power", "projectile_protection",
                "protection", "punch", "quick_charge", "respiration", "riptide", "sharpness",
                "silk_touch", "smite", "soul_speed", "sweeping_edge", "swift_sneak", "thorns",
                "unbreaking", "vanishing_curse", "wind_burst");
    }

    /** 773のエンチャント42件（774から lunge を除いた並び）。 */
    private static List<String> enchantments773() {
        List<String> names = new ArrayList<>(enchantments774());
        names.remove("lunge");
        return names;
    }

    /** 774のダメージ種別50件（spear は37番）。 */
    private static List<String> damages774() {
        return List.of("arrow", "bad_respawn_point", "cactus", "campfire", "cramming",
                "dragon_breath", "drown", "dry_out", "ender_pearl", "explosion", "fall",
                "falling_anvil", "falling_block", "falling_stalactite", "fireball", "fireworks",
                "fly_into_wall", "freeze", "generic", "generic_kill", "hot_floor", "in_fire",
                "in_wall", "indirect_magic", "lava", "lightning_bolt", "mace_smash", "magic",
                "mob_attack", "mob_attack_no_aggro", "mob_projectile", "on_fire", "out_of_world",
                "outside_border", "player_attack", "player_explosion", "sonic_boom", "spear",
                "spit", "stalagmite", "starve", "sting", "sweet_berry_bush", "thorns", "thrown",
                "trident", "unattributed_fireball", "wind_charge", "wither", "wither_skull");
    }

    /** 773のダメージ種別49件（774から spear を除いた並び）。 */
    private static List<String> damages773() {
        List<String> names = new ArrayList<>(damages774());
        names.remove("spear");
        return names;
    }

    /** 773／774のジュークボックスの歌21件（lava_chicken は9番）。 */
    private static List<String> jukeboxSongs774() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /** 773／774の絵画51件（dennis=14、kebab=24、wither=50）。 */
    private static List<String> paintings774() {
        return List.of("alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet", "burning_skull",
                "bust", "cavebird", "changing", "cotan", "courbet", "creebet", "dennis", "donkey_kong",
                "earth", "endboss", "fern", "fighters", "finding", "fire", "graham", "humble",
                "kebab", "lowmist", "match", "meditative", "orb", "owlemons", "passage", "pigscene",
                "plant", "pointer", "pond", "pool", "prairie_ride", "sea", "skeleton", "skull_and_roses",
                "stage", "sunflowers", "sunset", "tides", "unpacked", "void", "wanderer", "wasteland",
                "water", "wind", "wither");
    }

    /** 773のエンティティ153件（pig=97、ghast=56、happy_ghast=57、falling_block=50）。 */
    private static List<String> entities773() {
        return List.of("acacia_boat", "acacia_chest_boat", "allay", "area_effect_cloud", "armadillo", "armor_stand", "arrow", "axolotl",
                "bamboo_chest_raft", "bamboo_raft", "bat", "bee", "birch_boat", "birch_chest_boat", "blaze", "block_display",
                "bogged", "breeze", "breeze_wind_charge", "camel", "cat", "cave_spider", "cherry_boat", "cherry_chest_boat",
                "chest_minecart", "chicken", "cod", "copper_golem", "command_block_minecart", "cow", "creaking", "creeper",
                "dark_oak_boat", "dark_oak_chest_boat", "dolphin", "donkey", "dragon_fireball", "drowned", "egg", "elder_guardian",
                "enderman", "endermite", "ender_dragon", "ender_pearl", "end_crystal", "evoker", "evoker_fangs", "experience_bottle",
                "experience_orb", "eye_of_ender", "falling_block", "fireball", "firework_rocket", "fox", "frog", "furnace_minecart",
                "ghast", "happy_ghast", "giant", "glow_item_frame", "glow_squid", "goat", "guardian", "hoglin",
                "hopper_minecart", "horse", "husk", "illusioner", "interaction", "iron_golem", "item", "item_display",
                "item_frame", "jungle_boat", "jungle_chest_boat", "leash_knot", "lightning_bolt", "llama", "llama_spit", "magma_cube",
                "mangrove_boat", "mangrove_chest_boat", "mannequin", "marker", "minecart", "mooshroom", "mule", "oak_boat",
                "oak_chest_boat", "ocelot", "ominous_item_spawner", "painting", "pale_oak_boat", "pale_oak_chest_boat", "panda", "parrot",
                "phantom", "pig", "piglin", "piglin_brute", "pillager", "polar_bear", "splash_potion", "lingering_potion",
                "pufferfish", "rabbit", "ravager", "salmon", "sheep", "shulker", "shulker_bullet", "silverfish",
                "skeleton", "skeleton_horse", "slime", "small_fireball", "sniffer", "snowball", "snow_golem", "spawner_minecart",
                "spectral_arrow", "spider", "spruce_boat", "spruce_chest_boat", "squid", "stray", "strider", "tadpole",
                "text_display", "tnt", "tnt_minecart", "trader_llama", "trident", "tropical_fish", "turtle", "vex",
                "villager", "vindicator", "wandering_trader", "warden", "wind_charge", "witch", "wither", "wither_skeleton",
                "wither_skull", "wolf", "zoglin", "zombie", "zombie_horse", "zombie_villager", "zombified_piglin", "player",
                "fishing_bobber");
    }

    /** 774のエンティティ157件（camel_husk=20、nautilus=88、parched=97、zombie_nautilus=152、pig=100）。 */
    private static List<String> entities774() {
        return List.of("acacia_boat", "acacia_chest_boat", "allay", "area_effect_cloud", "armadillo", "armor_stand", "arrow", "axolotl",
                "bamboo_chest_raft", "bamboo_raft", "bat", "bee", "birch_boat", "birch_chest_boat", "blaze", "block_display",
                "bogged", "breeze", "breeze_wind_charge", "camel", "camel_husk", "cat", "cave_spider", "cherry_boat",
                "cherry_chest_boat", "chest_minecart", "chicken", "cod", "copper_golem", "command_block_minecart", "cow", "creaking",
                "creeper", "dark_oak_boat", "dark_oak_chest_boat", "dolphin", "donkey", "dragon_fireball", "drowned", "egg",
                "elder_guardian", "enderman", "endermite", "ender_dragon", "ender_pearl", "end_crystal", "evoker", "evoker_fangs",
                "experience_bottle", "experience_orb", "eye_of_ender", "falling_block", "fireball", "firework_rocket", "fox", "frog",
                "furnace_minecart", "ghast", "happy_ghast", "giant", "glow_item_frame", "glow_squid", "goat", "guardian",
                "hoglin", "hopper_minecart", "horse", "husk", "illusioner", "interaction", "iron_golem", "item",
                "item_display", "item_frame", "jungle_boat", "jungle_chest_boat", "leash_knot", "lightning_bolt", "llama", "llama_spit",
                "magma_cube", "mangrove_boat", "mangrove_chest_boat", "mannequin", "marker", "minecart", "mooshroom", "mule",
                "nautilus", "oak_boat", "oak_chest_boat", "ocelot", "ominous_item_spawner", "painting", "pale_oak_boat", "pale_oak_chest_boat",
                "panda", "parched", "parrot", "phantom", "pig", "piglin", "piglin_brute", "pillager",
                "polar_bear", "splash_potion", "lingering_potion", "pufferfish", "rabbit", "ravager", "salmon", "sheep",
                "shulker", "shulker_bullet", "silverfish", "skeleton", "skeleton_horse", "slime", "small_fireball", "sniffer",
                "snowball", "snow_golem", "spawner_minecart", "spectral_arrow", "spider", "spruce_boat", "spruce_chest_boat", "squid",
                "stray", "strider", "tadpole", "text_display", "tnt", "tnt_minecart", "trader_llama", "trident",
                "tropical_fish", "turtle", "vex", "villager", "vindicator", "wandering_trader", "warden", "wind_charge",
                "witch", "wither", "wither_skeleton", "wither_skull", "wolf", "zoglin", "zombie", "zombie_horse",
                "zombie_nautilus", "zombie_villager", "zombified_piglin", "player", "fishing_bobber");
    }

    // ------------------------------------------------------------------
    // Registry Data：エンチャント・ダメージ種別（挿入でずれる）
    // ------------------------------------------------------------------

    @Test
    void registryData773To774EnchantmentsShiftLunge() {
        byte[] packet = registryPacket("minecraft:enchantment", enchantments773());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To774(packet));
        assertEquals(43, out.names().size());
        assertEquals(shortNames(enchantments774()), shortNames(out.names()));
        assertEquals("minecraft:lunge", out.names().get(21));
        assertEquals("minecraft:lure", out.names().get(22));
        assertEquals("minecraft:mending", out.names().get(23));
        assertEquals("minecraft:wind_burst", out.names().get(42));
    }

    @Test
    void registryData774To773EnchantmentsDropLunge() {
        byte[] packet = registryPacket("minecraft:enchantment", enchantments774());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData774To773(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(enchantments773()), shortNames(out.names()));
        assertEquals("minecraft:lure", out.names().get(21));
        assertEquals("minecraft:mending", out.names().get(22));
        assertEquals("minecraft:wind_burst", out.names().get(41));
    }

    @Test
    void registryData773To774DamageTypeShiftSpear() {
        byte[] packet = registryPacket("minecraft:damage_type", damages773());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To774(packet));
        assertEquals(50, out.names().size());
        assertEquals(shortNames(damages774()), shortNames(out.names()));
        assertEquals("minecraft:spear", out.names().get(37));
        assertEquals("minecraft:spit", out.names().get(38));
        assertEquals("minecraft:wither_skull", out.names().get(49));
    }

    @Test
    void registryData774To773DamageTypeDropSpear() {
        byte[] packet = registryPacket("minecraft:damage_type", damages774());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData774To773(packet));
        assertEquals(49, out.names().size());
        assertEquals(shortNames(damages773()), shortNames(out.names()));
        assertEquals("minecraft:spit", out.names().get(37));
        assertEquals("minecraft:wither_skull", out.names().get(48));
    }

    // ------------------------------------------------------------------
    // Registry Data：同一のもの（素通し）
    // ------------------------------------------------------------------

    @Test
    void registryData773To774PaintingsIdentity() {
        byte[] packet = registryPacket("minecraft:painting_variant", paintings774());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To774(packet));
        assertEquals(51, out.names().size());
        assertEquals(shortNames(paintings774()), shortNames(out.names()));
        assertEquals("minecraft:dennis", out.names().get(14));
        assertEquals("minecraft:kebab", out.names().get(24));
        assertEquals("minecraft:wither", out.names().get(50));
    }

    @Test
    void registryData773To774JukeboxSongIdentity() {
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs774());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To774(packet));
        assertEquals(21, out.names().size());
        assertEquals(shortNames(jukeboxSongs774()), shortNames(out.names()));
        assertEquals("lava_chicken", out.names().get(9));
        assertEquals("mall", out.names().get(10));
        assertEquals("tears", out.names().get(18));
        assertEquals("wait", out.names().get(19));
        assertEquals("ward", out.names().get(20));
    }

    @Test
    void registryData773To774BiomesIdentity() {
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes773());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To774(packet));
        assertEquals(65, out.names().size());
        assertEquals("minecraft:pale_garden", out.names().get(39));
        assertEquals("minecraft:plains", out.names().get(40));
        assertEquals(shortNames(biomes773()), shortNames(out.names()));
    }

    @Test
    void registryData773To774DialogPassesThrough() {
        byte[] packet = registryPacket("minecraft:dialog",
                List.of("custom_options", "quick_actions", "server_links"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To774(packet));
        assertEquals(3, out.names().size());
        assertEquals(List.of("custom_options", "quick_actions", "server_links"),
                shortNames(out.names()));
    }

    @Test
    void registryData773To774EntitiesPassThrough() {
        // エンティティの Registry Data は並べ替えず素通しする（既存の全ペアと
        // 同じ設計）。773→774では camel_husk・nautilus・parched・
        // zombie_nautilus が増えるため、素通しのままでは774クライアントの
        // エンティティ登録は773順（153件、pig=97）のままになる。Spawn Entity
        // の種別ID自体は774へ翻訳される（pig=100）。実機クライアントでの
        // 扱いは未確認。残る制限として報告する。
        byte[] packet = registryPacket("minecraft:entity_type", entities773());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To774(packet));
        assertEquals(153, out.names().size());
        assertEquals(shortNames(entities773()), shortNames(out.names()));
        assertEquals("minecraft:pig", out.names().get(97));
        assertEquals("minecraft:happy_ghast", out.names().get(57));
    }

    @Test
    void registryData774To773EntitiesPassThrough() {
        byte[] packet = registryPacket("minecraft:entity_type", entities774());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData774To773(packet));
        assertEquals(157, out.names().size());
        assertEquals(shortNames(entities774()), shortNames(out.names()));
        assertEquals("minecraft:pig", out.names().get(100));
        assertEquals("minecraft:nautilus", out.names().get(88));
    }

    // ------------------------------------------------------------------
    // RegistryRemapper の抜き打ち
    // ------------------------------------------------------------------

    @Test
    void remapperEnchantmentSpotChecks() {
        assertEquals(0, RegistryRemapper.mapEnchantment773To774(0)); // aqua_affinity
        assertEquals(20, RegistryRemapper.mapEnchantment773To774(20)); // luck_of_the_sea
        assertEquals(22, RegistryRemapper.mapEnchantment773To774(21)); // lure
        assertEquals(23, RegistryRemapper.mapEnchantment773To774(22)); // mending
        assertEquals(42, RegistryRemapper.mapEnchantment773To774(41)); // wind_burst
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment774To773(21)); // lunge
        assertEquals(21, RegistryRemapper.mapEnchantment774To773(22)); // lure
        assertEquals(22, RegistryRemapper.mapEnchantment774To773(23)); // mending
        assertEquals(41, RegistryRemapper.mapEnchantment774To773(42)); // wind_burst
    }

    @Test
    void remapperDamageTypeSpotChecks() {
        assertEquals(8, RegistryRemapper.mapDamageType773To774(8)); // ender_pearl
        assertEquals(27, RegistryRemapper.mapDamageType773To774(27)); // magic
        assertEquals(38, RegistryRemapper.mapDamageType773To774(37)); // spit
        assertEquals(49, RegistryRemapper.mapDamageType773To774(48)); // wither_skull
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType774To773(37)); // spear
        assertEquals(37, RegistryRemapper.mapDamageType774To773(38)); // spit
        assertEquals(48, RegistryRemapper.mapDamageType774To773(49)); // wither_skull
    }

    @Test
    void remapperEntityAndItemSpotChecks() {
        assertEquals(100, RegistryRemapper.mapEntityType773To774(97)); // pig
        assertEquals(57, RegistryRemapper.mapEntityType773To774(56)); // ghast
        assertEquals(58, RegistryRemapper.mapEntityType773To774(57)); // happy_ghast
        assertEquals(51, RegistryRemapper.mapEntityType773To774(50)); // falling_block
        assertEquals(1316, RegistryRemapper.mapItem773To774(1304)); // music_disc.lava_chicken
        assertEquals(863, RegistryRemapper.mapItem773To774(863)); // oak_boat（同一）
        assertEquals(1304, RegistryRemapper.mapItemUp774(1316)); // lava_chicken 逆向き
        assertEquals(1, RegistryRemapper.mapItemUp774(1302)); // netherite_spear は石へ
        assertEquals(1, RegistryRemapper.mapItemUp774(1156)); // nautilus_spawn_egg は石へ
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(88)); // nautilus は豚へ
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(152)); // zombie_nautilus は豚へ
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(100)); // pig 100→97
    }
}
