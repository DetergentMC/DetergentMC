package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 1.21.9／1.21.10（プロトコル773）の PlayTranslator 変換テスト。
 *
 * <p>772→773の変更（minecraft-data の表に準拠。実バニラ1.21.9／1.21.10の
 * Registry Data キャプチャで絵画・ジュークボックスの位置を確認する）：
 * Player Info／Set Held Item／Vehicle Move／Player Chat は形式が同一のため
 * 素通し。Chat Message／Chat Command Signed の末尾 checksum u8 は772・773とも
 * あるため、772⇔773では剥がしも付け足しもしない（strip／append は
 * 769⇔771／771⇔772／772⇔773の連鎖で使う道具として残る）。
 * Registry Data は jukebox_song・絵画・バイオーム・エンチャント・属性・
 * ダメージ種別が772・773とも同一のため素通しする（771→772で lava_chicken
 * を挿入したのとは違い、772→773では新しい曲は無い）。
 * minecraft:dialog は772・773ともにあるため、772⇔773では落とさず素通しする
 * （771段階で770向けに落としていたのとは違う）。
 * エンティティ・パーティクル・ブロックエンティティの Registry Data は
 * 並べ替えず素通しする（既存の全ペアと同じ設計）。772→773では銅ゴーレム・
 * マネキン・copper_fire_flame・棚・銅ゴーレム像が増えるが、Registry Data
 * には反映されない。Spawn Entity 等の種別ID自体は翻訳される。
 */
class PlayTranslator773Test {

    // ------------------------------------------------------------------
    // ヘルパー（PlayTranslator772Test と同形）
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

    /** 766／767／768／769／771／772／773形式の Registry Data 1パケット（レジストリ1個分）。 */
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

    /** 772／773のバイオーム65件（pale_garden は39、plains は40）。 */
    private static List<String> biomes772() {
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

    /** 772／773形式の Player Info（update_hat＋update_list_order 付き、プレイヤー2人）。 */
    private static byte[] playerInfo772Payload() {
        Writer writer = new Writer();
        writer.writeByte(0xFF); // 全アクション（hat＋list_order 含む）
        writer.writeVarInt(2); // count 2
        // 1人目：全アクションのデータあり。listPriority の後に showHat。
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
        writer.writeVarInt(5); // listPriority（bit 7）
        writer.writeBoolean(true); // showHat（bit 6）
        // 2人目：add_player と latency だけ。
        writer.writeUuid(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        writer.writeString("Bob");
        writer.writeVarInt(0); // properties 0件
        writer.writeBoolean(false); // initialize_chat：セッション無し
        writer.writeVarInt(0); // update_game_mode（survival）
        writer.writeVarInt(1); // listed
        writer.writeVarInt(7); // latency
        writer.writeBoolean(false); // displayName 無し
        writer.writeVarInt(9); // listPriority（bit 7）
        writer.writeBoolean(false); // showHat（bit 6）
        return writer.bytes();
    }

    /** 772／773形式の Player Chat（先頭に globalIndex varint 付き）。 */
    private static byte[] playerChat772Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(3); // globalIndex
        writer.writeUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        writer.writeString("hello");
        writer.writeLong(123456789L);
        return writer.bytes();
    }

    /** 772のジュークボックスの歌21件（実バニラ1.21.7／1.21.8の並び。lava_chicken は9番）。773も同じ21件。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    /** 773のジュークボックスの歌21件（lava_chicken を9番に挿入）。 */
    private static List<String> jukeboxSongs773() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }

    // ------------------------------------------------------------------
    // Player Info／Set Held Item／Vehicle Move／Player Chat（素通し）
    // ------------------------------------------------------------------

    @Test
    void playerInfo772To773Identity() {
        byte[] payload = playerInfo772Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo772To773(payload));
    }

    @Test
    void playerInfo773To772Identity() {
        byte[] payload = playerInfo772Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerInfo773To772(payload));
    }

    @Test
    void setHeldItem772To773Identity() {
        byte[] payload = new byte[]{0x05};
        assertArrayEquals(payload, PlayTranslator.translateSetHeldItem772To773(payload));
    }

    @Test
    void vehicleMove772To773IdentityWithOnGround() {
        // Vehicle Move は772と773で形式が同一（x/y/z＋yaw＋pitch＋onGround）。
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f);
        writer.writeFloat(1.0f);
        writer.writeBoolean(true); // onGround
        byte[] payload = writer.bytes();
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove772To773(payload));
        assertArrayEquals(payload, PlayTranslator.translateVehicleMove773To772(payload));
    }

    @Test
    void playerChat772To773Identity() {
        // Player Chat は772・773とも先頭に globalIndex があるため素通し。
        byte[] payload = playerChat772Payload();
        assertArrayEquals(payload, PlayTranslator.translatePlayerChat772To773(payload));
        assertArrayEquals(payload, PlayTranslator.translatePlayerChat773To772(payload));
    }

    // ------------------------------------------------------------------
    // チャットチェックサム（772⇔773では使わない道具）
    // ------------------------------------------------------------------

    @Test
    void stripChatChecksum773() {
        byte[] payload = new byte[]{0x01, 0x02, (byte) 0xAB};
        assertArrayEquals(new byte[]{0x01, 0x02},
                PlayTranslator.stripChatChecksum773(payload));
    }

    @Test
    void stripChatChecksum773Empty() {
        assertArrayEquals(new byte[0], PlayTranslator.stripChatChecksum773(new byte[0]));
    }

    @Test
    void appendChatChecksum773() {
        // 末尾に checksum 0x00 を書き足す。0x00 が実バニラ1.21.9／1.21.10で
        // 受け入れられることは実バニラでの統合テストで確認する（プローブ未実施）。
        byte[] payload = new byte[]{0x01, 0x02};
        assertArrayEquals(new byte[]{0x01, 0x02, 0x00},
                PlayTranslator.appendChatChecksum773(payload));
    }

    // ------------------------------------------------------------------
    // Registry Data
    // ------------------------------------------------------------------

    @Test
    void registryData772To773EnchantmentsIdentity() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData773To772EnchantmentsIdentity() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            names.add("enchantment_" + i);
        }
        names.set(22, "mending");
        byte[] packet = registryPacket("minecraft:enchantment", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To772(packet));
        assertEquals(42, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
    }

    @Test
    void registryData772To773BiomesIdentity() {
        byte[] packet = registryPacket("minecraft:worldgen/biome", biomes772());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(65, out.names().size());
        assertEquals("minecraft:pale_garden", out.names().get(39));
        assertEquals("minecraft:plains", out.names().get(40));
        assertEquals(shortNames(biomes772()), shortNames(out.names()));
    }

    @Test
    void registryData772To773JukeboxSongIdentity() {
        // 772→773：ジュークボックスの歌は21件・同じ並び（lava_chicken=9）。
        // 新しい曲は無いため素通しする。
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs772());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(21, out.names().size());
        assertEquals(shortNames(jukeboxSongs773()), shortNames(out.names()));
        assertEquals("lava_chicken", out.names().get(9));
        assertEquals("mall", out.names().get(10));
        assertEquals("tears", out.names().get(18));
        assertEquals("wait", out.names().get(19));
        assertEquals("ward", out.names().get(20));
    }

    @Test
    void registryData773To772JukeboxSongIdentity() {
        // 逆向きも素通しする（772・773とも21件・同じ並び）。
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs773());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To772(packet));
        assertEquals(21, out.names().size());
        assertEquals(shortNames(jukeboxSongs772()), shortNames(out.names()));
        assertEquals("lava_chicken", out.names().get(9));
        assertEquals("mall", out.names().get(10));
        assertEquals("tears", out.names().get(18));
        assertEquals("wait", out.names().get(19));
        assertEquals("ward", out.names().get(20));
    }

    @Test
    void registryData772To773DialogPassesThrough() {
        // minecraft:dialog は772・773ともにあるため素通しする（落とさない）。
        byte[] packet = registryPacket("minecraft:dialog",
                List.of("custom_options", "quick_actions", "server_links"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(3, out.names().size());
        assertEquals(List.of("custom_options", "quick_actions", "server_links"),
                shortNames(out.names()));
    }

    @Test
    void registryData773To772DialogPassesThrough() {
        // 逆向きも素通しする（771段階で770向けに落としていたのとは違う）。
        byte[] packet = registryPacket("minecraft:dialog",
                List.of("custom_options", "quick_actions", "server_links"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To772(packet));
        assertEquals(3, out.names().size());
        assertEquals(List.of("custom_options", "quick_actions", "server_links"),
                shortNames(out.names()));
    }

    @Test
    void registryData772To773NonBiomeUnchanged() {
        byte[] packet = registryPacket("minecraft:dimension_type",
                List.of("overworld", "overworld_caves", "the_nether", "the_end"));
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(List.of("overworld", "overworld_caves", "the_nether", "the_end"),
                shortNames(out.names()));
    }

    // ------------------------------------------------------------------
    // 773の Registry Data（絵画は同一、エンティティ等は素通し）
    // ------------------------------------------------------------------

    @Test
    void registryData772To773PaintingsIdentity() {
        // 絵画は772・773とも51件・同じ並び（実バニラ1.21.8／1.21.9の
        // Registry Data キャプチャで確認：dennis=14、kebab=24、pointer=33、
        // pond=34、unpacked=44、wither=50。767の kebab=23 は dennis の
        // 挿入で+1ずれる）。
        List<String> names = List.of("alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet", "burning_skull",
                "bust", "cavebird", "changing", "cotan", "courbet", "creebet", "dennis", "donkey_kong",
                "earth", "endboss", "fern", "fighters", "finding", "fire", "graham", "humble",
                "kebab", "lowmist", "match", "meditative", "orb", "owlemons", "passage", "pigscene",
                "plant", "pointer", "pond", "pool", "prairie_ride", "sea", "skeleton", "skull_and_roses",
                "stage", "sunflowers", "sunset", "tides", "unpacked", "void", "wanderer", "wasteland",
                "water", "wind", "wither");
        byte[] packet = registryPacket("minecraft:painting_variant", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(51, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:dennis", out.names().get(14));
        assertEquals("minecraft:kebab", out.names().get(24));
        assertEquals("minecraft:pointer", out.names().get(33));
        assertEquals("minecraft:pond", out.names().get(34));
        assertEquals("minecraft:unpacked", out.names().get(44));
        assertEquals("minecraft:wither", out.names().get(50));
    }

    @Test
    void registryData773To772PaintingsIdentity() {
        List<String> names = List.of("alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet", "burning_skull",
                "bust", "cavebird", "changing", "cotan", "courbet", "creebet", "dennis", "donkey_kong",
                "earth", "endboss", "fern", "fighters", "finding", "fire", "graham", "humble",
                "kebab", "lowmist", "match", "meditative", "orb", "owlemons", "passage", "pigscene",
                "plant", "pointer", "pond", "pool", "prairie_ride", "sea", "skeleton", "skull_and_roses",
                "stage", "sunflowers", "sunset", "tides", "unpacked", "void", "wanderer", "wasteland",
                "water", "wind", "wither");
        byte[] packet = registryPacket("minecraft:painting_variant", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To772(packet));
        assertEquals(51, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:dennis", out.names().get(14));
    }

    @Test
    void registryData772To773EntitiesPassThrough() {
        // エンティティの Registry Data は並べ替えず素通しする（既存の全ペアと
        // 同じ設計。並べ替えるのはエンチャント・絵画・バナー・トリム・
        // ジュークボックス・ダメージ種別の6レジストリだけ）。
        // 772→773では銅ゴーレム（27）・マネキン（82）が増えるため、素通しの
        // ままでは773クライアントのエンティティ登録は772順（151件、pig=95）
        // のままになる。Spawn Entity の種別ID自体は773へ翻訳される（pig=97）。
        // 実機クライアントでの扱いは未確認。残る制限として報告する。
        List<String> names = List.of("acacia_boat", "acacia_chest_boat", "allay", "area_effect_cloud", "armadillo", "armor_stand", "arrow", "axolotl",
                "bamboo_chest_raft", "bamboo_raft", "bat", "bee", "birch_boat", "birch_chest_boat", "blaze", "block_display",
                "bogged", "breeze", "breeze_wind_charge", "camel", "cat", "cave_spider", "cherry_boat", "cherry_chest_boat",
                "chest_minecart", "chicken", "cod", "command_block_minecart", "cow", "creaking", "creeper", "dark_oak_boat",
                "dark_oak_chest_boat", "dolphin", "donkey", "dragon_fireball", "drowned", "egg", "elder_guardian", "enderman",
                "endermite", "ender_dragon", "ender_pearl", "end_crystal", "evoker", "evoker_fangs", "experience_bottle", "experience_orb",
                "eye_of_ender", "falling_block", "fireball", "firework_rocket", "fox", "frog", "furnace_minecart", "ghast",
                "happy_ghast", "giant", "glow_item_frame", "glow_squid", "goat", "guardian", "hoglin", "hopper_minecart",
                "horse", "husk", "illusioner", "interaction", "iron_golem", "item", "item_display", "item_frame",
                "jungle_boat", "jungle_chest_boat", "leash_knot", "lightning_bolt", "llama", "llama_spit", "magma_cube", "mangrove_boat",
                "mangrove_chest_boat", "marker", "minecart", "mooshroom", "mule", "oak_boat", "oak_chest_boat", "ocelot",
                "ominous_item_spawner", "painting", "pale_oak_boat", "pale_oak_chest_boat", "panda", "parrot", "phantom", "pig",
                "piglin", "piglin_brute", "pillager", "polar_bear", "splash_potion", "lingering_potion", "pufferfish", "rabbit",
                "ravager", "salmon", "sheep", "shulker", "shulker_bullet", "silverfish", "skeleton", "skeleton_horse",
                "slime", "small_fireball", "sniffer", "snowball", "snow_golem", "spawner_minecart", "spectral_arrow", "spider",
                "spruce_boat", "spruce_chest_boat", "squid", "stray", "strider", "tadpole", "text_display", "tnt",
                "tnt_minecart", "trader_llama", "trident", "tropical_fish", "turtle", "vex", "villager", "vindicator",
                "wandering_trader", "warden", "wind_charge", "witch", "wither", "wither_skeleton", "wither_skull", "wolf",
                "zoglin", "zombie", "zombie_horse", "zombie_villager", "zombified_piglin", "player", "fishing_bobber");
        byte[] packet = registryPacket("minecraft:entity_type", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(151, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:pig", out.names().get(95));
        assertEquals("minecraft:happy_ghast", out.names().get(56));
    }

    @Test
    void registryData772To773ParticlesPassThrough() {
        // パーティクルも素通しする（copper_fire_flame（773=5）は増えない）。
        List<String> names = List.of("angry_villager", "block", "block_marker", "bubble", "cloud", "crit", "damage_indicator", "dragon_breath",
                "dripping_lava", "falling_lava", "landing_lava", "dripping_water", "falling_water", "dust", "dust_color_transition", "effect",
                "elder_guardian", "enchanted_hit", "enchant", "end_rod", "entity_effect", "explosion_emitter", "explosion", "gust",
                "small_gust", "gust_emitter_large", "gust_emitter_small", "sonic_boom", "falling_dust", "firework", "fishing", "flame",
                "infested", "cherry_leaves", "pale_oak_leaves", "tinted_leaves", "sculk_soul", "sculk_charge", "sculk_charge_pop", "soul_fire_flame",
                "soul", "flash", "happy_villager", "composter", "heart", "instant_effect", "item", "vibration",
                "trail", "item_slime", "item_cobweb", "item_snowball", "large_smoke", "lava", "mycelium", "note",
                "poof", "portal", "rain", "smoke", "white_smoke", "sneeze", "spit", "squid_ink",
                "sweep_attack", "totem_of_undying", "underwater", "splash", "witch", "bubble_pop", "current_down", "bubble_column_up",
                "nautilus", "dolphin", "campfire_cosy_smoke", "campfire_signal_smoke", "dripping_honey", "falling_honey", "landing_honey", "falling_nectar",
                "falling_spore_blossom", "ash", "crimson_spore", "warped_spore", "spore_blossom_air", "dripping_obsidian_tear", "falling_obsidian_tear", "landing_obsidian_tear",
                "reverse_portal", "white_ash", "small_flame", "snowflake", "dripping_dripstone_lava", "falling_dripstone_lava", "dripping_dripstone_water", "falling_dripstone_water",
                "glow_squid_ink", "glow", "wax_on", "wax_off", "electric_spark", "scrape", "shriek", "egg_crack",
                "dust_plume", "trial_spawner_detection", "trial_spawner_detection_ominous", "vault_connection", "dust_pillar", "ominous_spawning", "raid_omen", "trial_omen",
                "block_crumble", "firefly");
        byte[] packet = registryPacket("minecraft:particle_type", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(114, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:pale_oak_leaves", out.names().get(34));
    }

    @Test
    void registryData772To773BlockEntitiesPassThrough() {
        // ブロックエンティティも素通しする（shelf（773=40）・
        // copper_golem_statue（773=48）は増えない）。
        List<String> names = List.of("furnace", "chest", "trapped_chest", "ender_chest", "jukebox", "dispenser", "dropper", "sign",
                "hanging_sign", "mob_spawner", "creaking_heart", "piston", "brewing_stand", "enchanting_table", "end_portal", "beacon",
                "skull", "daylight_detector", "hopper", "comparator", "banner", "structure_block", "end_gateway", "command_block",
                "shulker_box", "bed", "conduit", "barrel", "smoker", "blast_furnace", "lectern", "bell",
                "jigsaw", "campfire", "beehive", "sculk_sensor", "calibrated_sculk_sensor", "sculk_catalyst", "sculk_shrieker", "chiseled_bookshelf",
                "brushable_block", "decorated_pot", "crafter", "trial_spawner", "vault", "test_block", "test_instance_block");
        byte[] packet = registryPacket("minecraft:block_entity_type", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(47, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:test_block", out.names().get(45));
    }
}
