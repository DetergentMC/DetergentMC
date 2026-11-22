package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 26.2（プロトコル776）の PlayTranslator 変換テスト。
 *
 * <p>775→776の変更（vv-26.2.json／vv-26.1.json と親のレジストリ差分に準拠）：
 * ジュークボックスは bounce を 4 番に挿入（21→22曲。lava_chicken は 9→10）。
 * ダメージ種別は sulfur_cube_hot を 42 番に挿入（50→51件。wither_skull は
 * 49→50）。エンチャント 43 件・絵画 51 種・ダイアログ 3 件は同一。
 * バイオームは sulfur_caves を 53 番に挿入（65→66件）するが、Registry Data
 * は entity_type と同じく並べ替えず素通しする（既存の全ペアと同じ設計。
 * チャンクデータのバイオーム ID 自体は ChunkTranslator が付け替えるため、
 * Registry Data のバイオーム順とチャンクのバイオーム ID が 53 番以降で
 * ずれる制限が残る。実機クライアントでの扱いは未確認。残る制限として報告する）。
 * エンティティも Registry Data は素通し（sulfur_cube=130 は反映されない。
 * Spawn Entity の種別 ID 自体は翻訳される）。
 * Player Info／Set Held Item／Vehicle Move／Player Chat／Update Time／
 * Enchant Item／Use Entity は形式が同一のため素通し。
 */
class PlayTranslator776Test {

    // ------------------------------------------------------------------
    // ヘルパー（PlayTranslator774Test と同形）
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

    /** 766／767／768／769／771／772／773／774／775／776形式の Registry Data 1パケット（レジストリ1個分）。 */
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

    /** 775／774のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes775() {
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

    /** 776のバイオーム66件（sulfur_caves を 53 番に挿入）。 */
    private static List<String> biomes776() {
        List<String> names = new ArrayList<>(biomes775());
        names.add(53, "sulfur_caves");
        return names;
    }

    /** 775のエンチャント43件（774と同一）。 */
    private static List<String> enchantments775() {
        return List.of("aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
                "breach", "channeling", "density", "depth_strider", "efficiency", "feather_falling",
                "fire_aspect", "fire_protection", "flame", "fortune", "frost_walker", "impaling",
                "infinity", "knockback", "looting", "loyalty", "luck_of_the_sea", "lunge",
                "lure", "mending", "multishot", "piercing", "power", "projectile_protection",
                "protection", "punch", "quick_charge", "respiration", "riptide", "sharpness",
                "silk_touch", "smite", "soul_speed", "sweeping_edge", "swift_sneak", "thorns",
                "unbreaking", "vanishing_curse", "wind_burst");
    }

    /** 775のダメージ種別50件（spear は37番）。 */
    private static List<String> damages775() {
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

    /** 776のダメージ種別51件（sulfur_cube_hot を 42 番に挿入）。 */
    private static List<String> damages776() {
        List<String> names = new ArrayList<>(damages775());
        names.add(42, "sulfur_cube_hot");
        return names;
    }

    /** 775／774のジュークボックスの歌21件（lava_chicken は9番）。 */
    private static List<String> jukeboxSongs775() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /** 776のジュークボックスの歌22件（bounce を 4 番に挿入）。 */
    private static List<String> jukeboxSongs776() {
        List<String> names = new ArrayList<>(jukeboxSongs775());
        names.add(4, "bounce");
        return names;
    }

    /** 775／774の絵画51件（dennis=14、kebab=24、wither=50）。 */
    private static List<String> paintings775() {
        return List.of("alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet", "burning_skull",
                "bust", "cavebird", "changing", "cotan", "courbet", "creebet", "dennis", "donkey_kong",
                "earth", "endboss", "fern", "fighters", "finding", "fire", "graham", "humble",
                "kebab", "lowmist", "match", "meditative", "orb", "owlemons", "passage", "pigscene",
                "plant", "pointer", "pond", "pool", "prairie_ride", "sea", "skeleton", "skull_and_roses",
                "stage", "sunflowers", "sunset", "tides", "unpacked", "void", "wanderer", "wasteland",
                "water", "wind", "wither");
    }

    /** 775のエンティティ157件（pig=100、tadpole=130、strider=129）。 */
    private static List<String> entities775() {
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

    /** 776のエンティティ158件（sulfur_cube を 130 番に挿入。tadpole は 131）。 */
    private static List<String> entities776() {
        List<String> names = new ArrayList<>(entities775());
        names.add(130, "sulfur_cube");
        return names;
    }

    // ------------------------------------------------------------------
    // Registry Data：ジュークボックス・ダメージ種別（挿入でずれる）
    // ------------------------------------------------------------------

    @Test
    void registryData775To776JukeboxSongInsertBounce() {
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs775());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData775To776(packet));
        assertEquals(22, out.names().size());
        assertEquals(shortNames(jukeboxSongs776()), shortNames(out.names()));
        assertEquals("bounce", out.names().get(4));
        assertEquals("lava_chicken", out.names().get(10));
        assertEquals("ward", out.names().get(21));
        // bounce は 775 側に無いため hasData=false で埋められる。
        assertFalse(out.hasData().get(4));
        assertTrue(out.hasData().get(10));
    }

    @Test
    void registryData776To775JukeboxSongDropBounce() {
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs776());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData776To775(packet));
        assertEquals(21, out.names().size());
        assertEquals(shortNames(jukeboxSongs775()), shortNames(out.names()));
        assertEquals("lava_chicken", out.names().get(9));
        assertEquals("ward", out.names().get(20));
        assertTrue(out.hasData().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void registryData775To776DamageTypeInsertSulfurCubeHot() {
        byte[] packet = registryPacket("minecraft:damage_type", damages775());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData775To776(packet));
        assertEquals(51, out.names().size());
        assertEquals(shortNames(damages776()), shortNames(out.names()));
        assertEquals("minecraft:sulfur_cube_hot", out.names().get(42));
        assertEquals("minecraft:wither_skull", out.names().get(50));
        assertFalse(out.hasData().get(42));
        assertTrue(out.hasData().get(50));
    }

    @Test
    void registryData776To775DamageTypeDropSulfurCubeHot() {
        byte[] packet = registryPacket("minecraft:damage_type", damages776());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData776To775(packet));
        assertEquals(50, out.names().size());
        assertEquals(shortNames(damages775()), shortNames(out.names()));
        assertEquals("minecraft:wither_skull", out.names().get(49));
        assertTrue(out.hasData().stream().allMatch(Boolean::booleanValue));
    }

    // ------------------------------------------------------------------
    // Registry Data：同一のもの（素通し）
    // ------------------------------------------------------------------

    @Test
    void registryData775To776EnchantmentsIdentity() {
        byte[] packet = registryPacket("minecraft:enchantment", enchantments775());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData775To776(packet));
        assertEquals(43, out.names().size());
        assertEquals(shortNames(enchantments775()), shortNames(out.names()));
        assertEquals("minecraft:lunge", out.names().get(21));
        assertEquals("minecraft:wind_burst", out.names().get(42));
    }

    @Test
    void registryData775To776PaintingsIdentity() {
        byte[] packet = registryPacket("minecraft:painting_variant", paintings775());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData775To776(packet));
        assertEquals(51, out.names().size());
        assertEquals(shortNames(paintings775()), shortNames(out.names()));
        assertEquals("minecraft:dennis", out.names().get(14));
        assertEquals("minecraft:kebab", out.names().get(24));
        assertEquals("minecraft:wither", out.names().get(50));
    }

    @Test
    void registryData775To776DialogPassesThrough() {
        byte[] packet = registryPacket("minecraft:dialog",
                List.of("custom_options", "quick_actions", "server_links"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData775To776(packet));
        assertEquals(3, out.names().size());
        assertEquals(List.of("custom_options", "quick_actions", "server_links"),
                shortNames(out.names()));
    }

    @Test
    void registryData775To776BiomesPassThrough() {
        // バイオームは Registry Data では並べ替えず素通しする（既存の全ペアと
        // 同じ設計）。775→776 では sulfur_caves が増えるため、素通しのままでは
        // 776 クライアントのバイオーム登録は 775 順（65件）のままになる。
        // チャンクデータのバイオーム ID 自体は 776 へ翻訳される（53 番以降+1）。
        // 実機クライアントでの扱いは未確認。残る制限として報告する。
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes775());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData775To776(packet));
        assertEquals(65, out.names().size());
        assertEquals(shortNames(biomes775()), shortNames(out.names()));
        assertEquals("minecraft:pale_garden", out.names().get(39));
        assertEquals("minecraft:plains", out.names().get(40));
    }

    @Test
    void registryData776To775BiomesPassThrough() {
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes776());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData776To775(packet));
        assertEquals(66, out.names().size());
        assertEquals(shortNames(biomes776()), shortNames(out.names()));
        assertEquals("minecraft:sulfur_caves", out.names().get(53));
    }

    @Test
    void registryData775To776EntitiesPassThrough() {
        // エンティティの Registry Data は並べ替えず素通しする（既存の全ペアと
        // 同じ設計）。775→776 では sulfur_cube が増えるため、素通しのままでは
        // 776 クライアントのエンティティ登録は 775 順（157件、tadpole=130）の
        // ままになる。Spawn Entity の種別 ID 自体は 776 へ翻訳される
        // （tadpole=131）。実機クライアントでの扱いは未確認。残る制限として報告する。
        byte[] packet = registryPacket("minecraft:entity_type", entities775());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData775To776(packet));
        assertEquals(157, out.names().size());
        assertEquals(shortNames(entities775()), shortNames(out.names()));
        assertEquals("minecraft:tadpole", out.names().get(130));
        assertEquals("minecraft:pig", out.names().get(100));
    }

    @Test
    void registryData776To775EntitiesPassThrough() {
        byte[] packet = registryPacket("minecraft:entity_type", entities776());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData776To775(packet));
        assertEquals(158, out.names().size());
        assertEquals(shortNames(entities776()), shortNames(out.names()));
        assertEquals("minecraft:sulfur_cube", out.names().get(130));
        assertEquals("minecraft:tadpole", out.names().get(131));
    }

    // ------------------------------------------------------------------
    // 素通しのパケット（形式が同一）
    // ------------------------------------------------------------------

    @Test
    void passthroughPacketsAreIdentity() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03, (byte) 0x80, 0x00, 0x7F};
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo775To776(payload));
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo776To775(payload));
        assertArrayEquals(payload, PlayTranslator.translateSetHeldItem775To776(payload));
        assertArrayEquals(payload, PlayTranslator.translateSetHeldItem776To775(payload));
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove775To776(payload));
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove776To775(payload));
        assertArrayEquals(payload, PlayTranslator.translatePlayerChat775To776(payload));
        assertArrayEquals(payload, PlayTranslator.translatePlayerChat776To775(payload));
        assertArrayEquals(payload, PlayTranslator.translateUpdateTime775To776(payload));
        assertArrayEquals(payload, PlayTranslator.translateUpdateTime776To775(payload));
        assertArrayEquals(payload, PlayTranslator.translateEnchantItem775To776(payload));
        assertArrayEquals(payload, PlayTranslator.translateEnchantItem776To775(payload));
        assertArrayEquals(payload, PlayTranslator.translateUseEntity775To776(payload));
        assertArrayEquals(payload, PlayTranslator.translateUseEntity776To775(payload));
    }

    @Test
    void chatChecksumStripAndAppendRoundTrip() {
        byte[] payload = new byte[]{0x10, 0x20, 0x30};
        byte[] withChecksum = PlayTranslator.appendChatChecksum776(payload);
        assertEquals(4, withChecksum.length);
        assertEquals(0, withChecksum[3]);
        assertArrayEquals(payload, PlayTranslator.stripChatChecksum776(withChecksum));
        assertEquals(0, PlayTranslator.stripChatChecksum776(new byte[0]).length);
    }

    // ------------------------------------------------------------------
    // RegistryRemapper の抜き打ち
    // ------------------------------------------------------------------

    @Test
    void remapperJukeboxSongSpotChecks() {
        assertEquals(5, RegistryRemapper.mapJukeboxSong775To776(4)); // cat
        assertEquals(10, RegistryRemapper.mapJukeboxSong775To776(9)); // lava_chicken
        assertEquals(21, RegistryRemapper.mapJukeboxSong775To776(20)); // ward
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapJukeboxSong776To775(4)); // bounce
        assertEquals(4, RegistryRemapper.mapJukeboxSong776To775(5)); // cat
        assertEquals(9, RegistryRemapper.mapJukeboxSong776To775(10)); // lava_chicken
        assertEquals(20, RegistryRemapper.mapJukeboxSong776To775(21)); // ward
    }

    @Test
    void remapperDamageTypeSpotChecks() {
        assertEquals(41, RegistryRemapper.mapDamageType775To776(41)); // sting
        assertEquals(43, RegistryRemapper.mapDamageType775To776(42)); // sweet_berry_bush
        assertEquals(48, RegistryRemapper.mapDamageType775To776(47)); // wind_charge
        assertEquals(49, RegistryRemapper.mapDamageType775To776(48)); // wither
        assertEquals(50, RegistryRemapper.mapDamageType775To776(49)); // wither_skull
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType776To775(42)); // sulfur_cube_hot
        assertEquals(42, RegistryRemapper.mapDamageType776To775(43)); // sweet_berry_bush
        assertEquals(47, RegistryRemapper.mapDamageType776To775(48)); // wind_charge
        assertEquals(48, RegistryRemapper.mapDamageType776To775(49)); // wither
        assertEquals(49, RegistryRemapper.mapDamageType776To775(50)); // wither_skull
    }

    @Test
    void remapperEnchantmentIdentitySpotChecks() {
        assertEquals(0, RegistryRemapper.mapEnchantment775To776(0)); // aqua_affinity
        assertEquals(21, RegistryRemapper.mapEnchantment775To776(21)); // lunge
        assertEquals(42, RegistryRemapper.mapEnchantment775To776(42)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment776To775(0));
        assertEquals(21, RegistryRemapper.mapEnchantment776To775(21));
        assertEquals(42, RegistryRemapper.mapEnchantment776To775(42));
    }
}
