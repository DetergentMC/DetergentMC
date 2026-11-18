package io.detergentmc.bedrock;

/**
 * プロトコル 291（1.7.0／1.7.1）の AddEntity が使う旧来の数値
 * エンティティタイプ ID（identifier 文字列の代わりに VarUnsignedInt で
 * 書く形式。CloudburstMC Protocol の AddEntitySerializer_v291 が
 * entityType を VarUnsignedInt で書くのに対し、v313 は identifier
 * 文字列。BE-NOTES.md §36）の対応表。
 *
 * <p>数値の出典は PocketMine-MP 3.4.0（291 と同時代のリリース。
 * 同タグの src/pocketmine/network/mcpe/protocol/ProtocolInfo.php は
 * CURRENT_PROTOCOL = 291／MINECRAFT_VERSION_NETWORK = '1.7.0'）の
 * src/pocketmine/entity/EntityIds.php（SHA-1
 * 5585c6a7cbb18f0b8e87b765e5f35686a07a310b）。各値のコメントは
 * EntityIds.php の定数名。identifier との対応付けはエンティティの
 * 標準名の同一性による。次の 13 件は PocketMine-MP 3.4.0 の
 * Entity.php の registerEntity 行で identifier が直接確認できる：
 * arrow／egg／ender_pearl／xp_bottle／xp_orb／falling_block／item／
 * painting／tnt／snowball／squid／villager／zombie。
 * splash_potion は PocketMine-MP 3.4.0 では SplashPotion クラスが
 * 'minecraft:potion' という identifier で登録されている（当時の
 * identifier が現在と違う）が、クラス自体が投げたスプラッシュ
 * ポーションであるため SPLASH_POTION（86）に対応付ける。
 * villager_v2／zombie_villager_v2 は 1.16 以降の identifier で、
 * 291 では villager／zombie_villager と同じエンティティのため
 * 同じ番号に対応付ける。fireball はガストの火球で、PocketMine-MP の
 * LARGE_FIREBALL に対応する。tropicalfish は PocketMine-MP の
 * TROPICAL_FISH、cod は FISH、thrown_trident は TRIDENT に対応する。
 *
 * <p>対応付けが無い identifier（{@link #legacyIdFor} が -1 を返す）は
 * 291 に送らない。内訳：1.7 より後に追加されたエンティティ
 * （allay、axolotl、bee、camel、cat、fox、frog、glow_squid、goat、
 * hoglin、panda、piglin、piglin_brute、pillager、ravager、sniffer、
 * strider、tadpole、trader_llama、warden、zoglin、chest_boat、
 * glow_item_frame 等）と、PocketMine-MP 3.4.0 の EntityIds.php に
 * 定数が無いもの（phantom、turtle、item_frame。phantom と turtle は
 * 1.7 世代にエンティティ自体は存在するが、この出典では番号が
 * 確認できないため未対応とする）。
 */
final class BedrockLegacyEntityTypeIds {

    private BedrockLegacyEntityTypeIds() {
    }

    /**
     * identifier に対応する 291 の数値タイプ ID。対応付けが無ければ
     * -1（呼び出し側はそのエンティティを 291 に送らないこと）。
     */
    static int legacyIdFor(String identifier) {
        return switch (identifier) {
            case "minecraft:chicken" -> 10; // CHICKEN
            case "minecraft:cow" -> 11; // COW
            case "minecraft:pig" -> 12; // PIG
            case "minecraft:sheep" -> 13; // SHEEP
            case "minecraft:wolf" -> 14; // WOLF
            case "minecraft:villager", "minecraft:villager_v2" -> 15; // VILLAGER
            case "minecraft:mooshroom" -> 16; // MOOSHROOM
            case "minecraft:squid" -> 17; // SQUID
            case "minecraft:rabbit" -> 18; // RABBIT
            case "minecraft:bat" -> 19; // BAT
            case "minecraft:iron_golem" -> 20; // IRON_GOLEM
            case "minecraft:snow_golem" -> 21; // SNOW_GOLEM
            case "minecraft:ocelot" -> 22; // OCELOT
            case "minecraft:horse" -> 23; // HORSE
            case "minecraft:donkey" -> 24; // DONKEY
            case "minecraft:mule" -> 25; // MULE
            case "minecraft:skeleton_horse" -> 26; // SKELETON_HORSE
            case "minecraft:zombie_horse" -> 27; // ZOMBIE_HORSE
            case "minecraft:polar_bear" -> 28; // POLAR_BEAR
            case "minecraft:llama" -> 29; // LLAMA
            case "minecraft:parrot" -> 30; // PARROT
            case "minecraft:dolphin" -> 31; // DOLPHIN
            case "minecraft:zombie" -> 32; // ZOMBIE
            case "minecraft:creeper" -> 33; // CREEPER
            case "minecraft:skeleton" -> 34; // SKELETON
            case "minecraft:spider" -> 35; // SPIDER
            case "minecraft:zombie_pigman" -> 36; // ZOMBIE_PIGMAN
            case "minecraft:slime" -> 37; // SLIME
            case "minecraft:enderman" -> 38; // ENDERMAN
            case "minecraft:silverfish" -> 39; // SILVERFISH
            case "minecraft:cave_spider" -> 40; // CAVE_SPIDER
            case "minecraft:ghast" -> 41; // GHAST
            case "minecraft:magma_cube" -> 42; // MAGMA_CUBE
            case "minecraft:blaze" -> 43; // BLAZE
            case "minecraft:zombie_villager", "minecraft:zombie_villager_v2" -> 44; // ZOMBIE_VILLAGER
            case "minecraft:witch" -> 45; // WITCH
            case "minecraft:stray" -> 46; // STRAY
            case "minecraft:husk" -> 47; // HUSK
            case "minecraft:wither_skeleton" -> 48; // WITHER_SKELETON
            case "minecraft:guardian" -> 49; // GUARDIAN
            case "minecraft:elder_guardian" -> 50; // ELDER_GUARDIAN
            case "minecraft:npc" -> 51; // NPC
            case "minecraft:wither" -> 52; // WITHER
            case "minecraft:ender_dragon" -> 53; // ENDER_DRAGON
            case "minecraft:shulker" -> 54; // SHULKER
            case "minecraft:endermite" -> 55; // ENDERMITE
            case "minecraft:vindicator" -> 57; // VINDICATOR
            case "minecraft:armor_stand" -> 61; // ARMOR_STAND
            case "minecraft:item" -> 64; // ITEM
            case "minecraft:tnt" -> 65; // TNT
            case "minecraft:falling_block" -> 66; // FALLING_BLOCK
            case "minecraft:xp_bottle" -> 68; // XP_BOTTLE
            case "minecraft:xp_orb" -> 69; // XP_ORB
            case "minecraft:eye_of_ender_signal" -> 70; // EYE_OF_ENDER_SIGNAL
            case "minecraft:ender_crystal" -> 71; // ENDER_CRYSTAL
            case "minecraft:fireworks_rocket" -> 72; // FIREWORKS_ROCKET
            case "minecraft:thrown_trident" -> 73; // TRIDENT
            case "minecraft:shulker_bullet" -> 76; // SHULKER_BULLET
            case "minecraft:fishing_hook" -> 77; // FISHING_HOOK
            case "minecraft:dragon_fireball" -> 79; // DRAGON_FIREBALL
            case "minecraft:arrow" -> 80; // ARROW
            case "minecraft:snowball" -> 81; // SNOWBALL
            case "minecraft:egg" -> 82; // EGG
            case "minecraft:painting" -> 83; // PAINTING
            case "minecraft:minecart" -> 84; // MINECART
            case "minecraft:fireball" -> 85; // LARGE_FIREBALL
            case "minecraft:splash_potion" -> 86; // SPLASH_POTION
            case "minecraft:ender_pearl" -> 87; // ENDER_PEARL
            case "minecraft:leash_knot" -> 88; // LEASH_KNOT
            case "minecraft:wither_skull" -> 89; // WITHER_SKULL
            case "minecraft:boat" -> 90; // BOAT
            case "minecraft:lightning_bolt" -> 93; // LIGHTNING_BOLT
            case "minecraft:small_fireball" -> 94; // SMALL_FIREBALL
            case "minecraft:area_effect_cloud" -> 95; // AREA_EFFECT_CLOUD
            case "minecraft:hopper_minecart" -> 96; // HOPPER_MINECART
            case "minecraft:tnt_minecart" -> 97; // TNT_MINECART
            case "minecraft:chest_minecart" -> 98; // CHEST_MINECART
            case "minecraft:command_block_minecart" -> 100; // COMMAND_BLOCK_MINECART
            case "minecraft:lingering_potion" -> 101; // LINGERING_POTION
            case "minecraft:llama_spit" -> 102; // LLAMA_SPIT
            case "minecraft:evocation_fang" -> 103; // EVOCATION_FANG
            case "minecraft:evocation_illager" -> 104; // EVOCATION_ILLAGER
            case "minecraft:vex" -> 105; // VEX
            case "minecraft:pufferfish" -> 108; // PUFFERFISH
            case "minecraft:salmon" -> 109; // SALMON
            case "minecraft:drowned" -> 110; // DROWNED
            case "minecraft:tropicalfish" -> 111; // TROPICAL_FISH
            case "minecraft:cod" -> 112; // FISH
            default -> -1;
        };
    }
}
