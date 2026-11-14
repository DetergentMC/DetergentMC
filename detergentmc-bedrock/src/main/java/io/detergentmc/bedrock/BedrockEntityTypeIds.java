package io.detergentmc.bedrock;

/**
 * Java 1.20.4（プロトコル 765）のエンティティ種別IDから
 * Bedrock のエンティティ識別子への対応表。
 *
 * <p>Java 側の順序は ViaVersion Mappings の mapping-1.20.3.json
 * （entities リスト。index がレジストリ ID）による。
 * バニラ 1.20.4 は設定フェーズで minecraft:entity_type を
 * 送らないため（実機プローブで確認済み）、この静的表が唯一の
 * 典拠である。763／764／766 サーバーの種別番号は
 * RegistryRemapper で 765 に正規化してから引く。
 *
 * <p>Bedrock 側の識別子は Microsoft Learn「Default Minecraft
 * Entity Listings」による。Bedrock 1.20.50 に無いものは
 * 近い種別へ代替する（breeze→blaze、wind_charge→
 * small_fireball、表示エンティティ→armor_stand、
 * giant→zombie、illusioner→evocation_illager、
 * spectral_arrow→arrow、furnace／spawner トロッコ→
 * minecart）。interaction／marker は不可視のため送らない。
 * 他プレイヤー（player）は AddPlayer で別扱いのため null。
 *
 * <p>網羅率（126種中）：直接対応 99、改名 14、代替 10、
 * 送らない 2、他プレイヤー 1。
 */
public final class BedrockEntityTypeIds {

    private BedrockEntityTypeIds() {
    }

    /** Java 765 のエンティティ種別数。 */
    public static final int COUNT = 126;

    /** 直接対応できた種別数。 */
    public static final int DIRECT_COUNT = 99;

    /** 同概念の改名で対応した種別数。 */
    public static final int RENAMED_COUNT = 14;

    /** 別種で代替した種別数。 */
    public static final int SUBSTITUTE_COUNT = 10;

    /** 送らない種別数（不可視のため）。 */
    public static final int NOT_SPAWNED_COUNT = 2;

    /** 765 の種別ID順。送らないものは null。 */
    private static final String[] TYPES_765 = {
            "minecraft:allay", // 0 allay
            "minecraft:area_effect_cloud", // 1 area_effect_cloud
            "minecraft:armor_stand", // 2 armor_stand
            "minecraft:arrow", // 3 arrow
            "minecraft:axolotl", // 4 axolotl
            "minecraft:bat", // 5 bat
            "minecraft:bee", // 6 bee
            "minecraft:blaze", // 7 blaze
            "minecraft:armor_stand", // 8 block_display
            "minecraft:boat", // 9 boat
            "minecraft:blaze", // 10 breeze
            "minecraft:camel", // 11 camel
            "minecraft:cat", // 12 cat
            "minecraft:cave_spider", // 13 cave_spider
            "minecraft:chest_boat", // 14 chest_boat
            "minecraft:chest_minecart", // 15 chest_minecart
            "minecraft:chicken", // 16 chicken
            "minecraft:cod", // 17 cod
            "minecraft:command_block_minecart", // 18 command_block_minecart
            "minecraft:cow", // 19 cow
            "minecraft:creeper", // 20 creeper
            "minecraft:dolphin", // 21 dolphin
            "minecraft:donkey", // 22 donkey
            "minecraft:dragon_fireball", // 23 dragon_fireball
            "minecraft:drowned", // 24 drowned
            "minecraft:egg", // 25 egg
            "minecraft:elder_guardian", // 26 elder_guardian
            "minecraft:ender_crystal", // 27 end_crystal
            "minecraft:ender_dragon", // 28 ender_dragon
            "minecraft:ender_pearl", // 29 ender_pearl
            "minecraft:enderman", // 30 enderman
            "minecraft:endermite", // 31 endermite
            "minecraft:evocation_illager", // 32 evoker
            "minecraft:evocation_fang", // 33 evoker_fangs
            "minecraft:xp_bottle", // 34 experience_bottle
            "minecraft:xp_orb", // 35 experience_orb
            "minecraft:eye_of_ender_signal", // 36 eye_of_ender
            "minecraft:falling_block", // 37 falling_block
            "minecraft:fireworks_rocket", // 38 firework_rocket
            "minecraft:fox", // 39 fox
            "minecraft:frog", // 40 frog
            "minecraft:minecart", // 41 furnace_minecart
            "minecraft:ghast", // 42 ghast
            "minecraft:zombie", // 43 giant
            "minecraft:glow_item_frame", // 44 glow_item_frame
            "minecraft:glow_squid", // 45 glow_squid
            "minecraft:goat", // 46 goat
            "minecraft:guardian", // 47 guardian
            "minecraft:hoglin", // 48 hoglin
            "minecraft:hopper_minecart", // 49 hopper_minecart
            "minecraft:horse", // 50 horse
            "minecraft:husk", // 51 husk
            "minecraft:evocation_illager", // 52 illusioner
            null, // 53 interaction
            "minecraft:iron_golem", // 54 iron_golem
            "minecraft:item", // 55 item
            "minecraft:armor_stand", // 56 item_display
            "minecraft:item_frame", // 57 item_frame
            "minecraft:fireball", // 58 fireball
            "minecraft:leash_knot", // 59 leash_knot
            "minecraft:lightning_bolt", // 60 lightning_bolt
            "minecraft:llama", // 61 llama
            "minecraft:llama_spit", // 62 llama_spit
            "minecraft:magma_cube", // 63 magma_cube
            null, // 64 marker
            "minecraft:minecart", // 65 minecart
            "minecraft:mooshroom", // 66 mooshroom
            "minecraft:mule", // 67 mule
            "minecraft:ocelot", // 68 ocelot
            "minecraft:painting", // 69 painting
            "minecraft:panda", // 70 panda
            "minecraft:parrot", // 71 parrot
            "minecraft:phantom", // 72 phantom
            "minecraft:pig", // 73 pig
            "minecraft:piglin", // 74 piglin
            "minecraft:piglin_brute", // 75 piglin_brute
            "minecraft:pillager", // 76 pillager
            "minecraft:polar_bear", // 77 polar_bear
            "minecraft:splash_potion", // 78 potion
            "minecraft:pufferfish", // 79 pufferfish
            "minecraft:rabbit", // 80 rabbit
            "minecraft:ravager", // 81 ravager
            "minecraft:salmon", // 82 salmon
            "minecraft:sheep", // 83 sheep
            "minecraft:shulker", // 84 shulker
            "minecraft:shulker_bullet", // 85 shulker_bullet
            "minecraft:silverfish", // 86 silverfish
            "minecraft:skeleton", // 87 skeleton
            "minecraft:skeleton_horse", // 88 skeleton_horse
            "minecraft:slime", // 89 slime
            "minecraft:small_fireball", // 90 small_fireball
            "minecraft:sniffer", // 91 sniffer
            "minecraft:snow_golem", // 92 snow_golem
            "minecraft:snowball", // 93 snowball
            "minecraft:minecart", // 94 spawner_minecart
            "minecraft:arrow", // 95 spectral_arrow
            "minecraft:spider", // 96 spider
            "minecraft:squid", // 97 squid
            "minecraft:stray", // 98 stray
            "minecraft:strider", // 99 strider
            "minecraft:tadpole", // 100 tadpole
            "minecraft:armor_stand", // 101 text_display
            "minecraft:tnt", // 102 tnt
            "minecraft:tnt_minecart", // 103 tnt_minecart
            "minecraft:trader_llama", // 104 trader_llama
            "minecraft:thrown_trident", // 105 trident
            "minecraft:tropicalfish", // 106 tropical_fish
            "minecraft:turtle", // 107 turtle
            "minecraft:vex", // 108 vex
            "minecraft:villager_v2", // 109 villager
            "minecraft:vindicator", // 110 vindicator
            "minecraft:wandering_trader", // 111 wandering_trader
            "minecraft:warden", // 112 warden
            "minecraft:small_fireball", // 113 wind_charge
            "minecraft:witch", // 114 witch
            "minecraft:wither", // 115 wither
            "minecraft:wither_skeleton", // 116 wither_skeleton
            "minecraft:wither_skull", // 117 wither_skull
            "minecraft:wolf", // 118 wolf
            "minecraft:zoglin", // 119 zoglin
            "minecraft:zombie", // 120 zombie
            "minecraft:zombie_horse", // 121 zombie_horse
            "minecraft:zombie_villager_v2", // 122 zombie_villager
            "minecraft:zombie_pigman", // 123 zombified_piglin
            null, // 124 player
            "minecraft:fishing_hook", // 125 fishing_bobber
    };

    /**
     * Java 765 のエンティティ種別IDから Bedrock の識別子を引く。
     * 送らない種別（interaction／marker、他プレイヤー）は null。
     * 範囲外は null。
     */
    public static String bedrockType(int type765) {
        if (type765 < 0 || type765 >= TYPES_765.length) {
            return null;
        }
        return TYPES_765[type765];
    }

    /**
     * サーバーのプロトコル番号の種別IDを 765 に正規化してから引く。
     * 763 は 765→763 表の逆引き、764／766 は上向き表を使う。
     * 765 以外で範囲外のものはブタ（765 の 73）になる。
     */
    public static String bedrockTypeFor(int typeId, int serverProtocol) {
        return bedrockType(
                BedrockEntityTranslator.normalizeEntityType(typeId, serverProtocol));
    }
}
