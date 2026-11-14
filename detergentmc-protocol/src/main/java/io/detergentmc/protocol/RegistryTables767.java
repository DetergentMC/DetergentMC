package io.detergentmc.protocol;

/**
 * 767（1.21／1.21.1）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 767 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_767_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 767}、
 * 検証は {@code verify-767-tables.py}。
 */
final class RegistryTables767 {

    private RegistryTables767() {
    }


    static final int[][] BLOCK_STATE_767_RANGES = {
            {0, 26683, 0},
    };

    static final int[][] BLOCK_STATE_767_OVERRIDES = {
    };
    // BLOCK_STATE_767: 765 entries=26684 ranges=1 overrides=0

    static final int[][] ITEM_767_RANGES = {
            {0, 798, 0}, {799, 847, 1}, {849, 1171, 0}, {1172, 1183, 2}, {1184, 1329, 3},
    };

    static final int[][] ITEM_767_OVERRIDES = {
            {848, 799},
    };
    // ITEM_767: 765 entries=1330 ranges=5 overrides=1

    static final int[][] ENTITY_TYPE_767_RANGES = {
            {0, 129, 0},
    };

    static final int[][] ENTITY_TYPE_767_OVERRIDES = {
    };
    // ENTITY_TYPE_767: 765 entries=130 ranges=1 overrides=0

    static final int[][] SOUND_767_RANGES = {
            {0, 885, 0}, {886, 1438, 3}, {1439, 1606, 4},
    };

    static final int[][] SOUND_767_OVERRIDES = {
    };
    // SOUND_767: 765 entries=1607 ranges=3 overrides=0

    static final int[][] PARTICLE_767_RANGES = {
            {0, 108, 0},
    };

    static final int[][] PARTICLE_767_OVERRIDES = {
    };
    // PARTICLE_767: 765 entries=109 ranges=1 overrides=0

    static final int[][] BLOCK_767_RANGES = {
            {0, 1059, 0},
    };

    static final int[][] BLOCK_767_OVERRIDES = {
    };
    // BLOCK_767: 765 entries=1060 ranges=1 overrides=0

    static final int[][] MENU_767_RANGES = {
            {0, 24, 0},
    };

    static final int[][] MENU_767_OVERRIDES = {
    };
    // MENU_767: 765 entries=25 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_767_RANGES = {
            {0, 798, 0}, {800, 848, -1}, {849, 1171, 0}, {1174, 1185, -2}, {1187, 1332, -3},
    };

    static final int[][] ITEM_REVERSE_767_OVERRIDES = {
            {799, 848}, {1172, 1}, {1173, 1}, {1186, 1},
    };
    // ITEM_REVERSE_767: 767 entries=1333 ranges=5 overrides=4

    static final String[][] SOUND_NAME_FALLBACKS_767 = {
            {"block.trial_spawner.ambient_charged", "block.trial_spawner.ambient_ominous"},
            {"block.trial_spawner.charge_activate", "block.trial_spawner.ominous_activate"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_766_TO_767 = {
            {"block.trial_spawner.ambient_charged", "block.trial_spawner.ambient_ominous"},
            {"block.trial_spawner.charge_activate", "block.trial_spawner.ominous_activate"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_767_TO_766 = {
            {"block.trial_spawner.ambient_ominous", "block.trial_spawner.ambient_charged"},
            {"block.trial_spawner.ominous_activate", "block.trial_spawner.charge_activate"},
            {"block.vault.reject_rewarded_player", "block.stone.break"},
            {"music_disc.creator", "block.stone.break"},
            {"music_disc.creator_music_box", "block.stone.break"},
            {"music_disc.precipice", "block.stone.break"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_767_TO_765 = {
            {"block.cobweb.break", "block.stone.break"},
            {"block.cobweb.fall", "block.stone.break"},
            {"block.cobweb.hit", "block.stone.break"},
            {"block.cobweb.place", "block.stone.break"},
            {"block.cobweb.step", "block.stone.break"},
            {"block.heavy_core.break", "block.stone.break"},
            {"block.heavy_core.fall", "block.stone.break"},
            {"block.heavy_core.hit", "block.stone.break"},
            {"block.heavy_core.place", "block.stone.break"},
            {"block.heavy_core.step", "block.stone.break"},
            {"block.trial_spawner.about_to_spawn_item", "block.stone.break"},
            {"block.trial_spawner.ambient_ominous", "block.stone.break"},
            {"block.trial_spawner.ominous_activate", "block.stone.break"},
            {"block.trial_spawner.spawn_item", "block.stone.break"},
            {"block.trial_spawner.spawn_item_begin", "block.stone.break"},
            {"block.vault.activate", "block.stone.break"},
            {"block.vault.ambient", "block.stone.break"},
            {"block.vault.break", "block.stone.break"},
            {"block.vault.close_shutter", "block.stone.break"},
            {"block.vault.deactivate", "block.stone.break"},
            {"block.vault.eject_item", "block.stone.break"},
            {"block.vault.fall", "block.stone.break"},
            {"block.vault.hit", "block.stone.break"},
            {"block.vault.insert_item", "block.stone.break"},
            {"block.vault.insert_item_fail", "block.stone.break"},
            {"block.vault.open_shutter", "block.stone.break"},
            {"block.vault.place", "block.stone.break"},
            {"block.vault.reject_rewarded_player", "block.stone.break"},
            {"block.vault.step", "block.stone.break"},
            {"block.wet_sponge.dries", "block.stone.break"},
            {"entity.armadillo.ambient", "block.stone.break"},
            {"entity.armadillo.brush", "block.stone.break"},
            {"entity.armadillo.death", "block.stone.break"},
            {"entity.armadillo.eat", "block.stone.break"},
            {"entity.armadillo.hurt", "block.stone.break"},
            {"entity.armadillo.hurt_reduced", "block.stone.break"},
            {"entity.armadillo.land", "block.stone.break"},
            {"entity.armadillo.peek", "block.stone.break"},
            {"entity.armadillo.roll", "block.stone.break"},
            {"entity.armadillo.scute_drop", "block.stone.break"},
            {"entity.armadillo.step", "block.stone.break"},
            {"entity.armadillo.unroll_finish", "block.stone.break"},
            {"entity.armadillo.unroll_start", "block.stone.break"},
            {"entity.bogged.ambient", "block.stone.break"},
            {"entity.bogged.death", "block.stone.break"},
            {"entity.bogged.hurt", "block.stone.break"},
            {"entity.bogged.shear", "block.stone.break"},
            {"entity.bogged.step", "block.stone.break"},
            {"entity.breeze.charge", "entity.breeze.inhale"},
            {"entity.breeze.deflect", "block.stone.break"},
            {"entity.breeze.whirl", "block.stone.break"},
            {"entity.breeze.wind_burst", "entity.generic.wind_burst"},
            {"entity.donkey.jump", "block.stone.break"},
            {"entity.mule.jump", "block.stone.break"},
            {"entity.parrot.imitate.bogged", "block.stone.break"},
            {"entity.wind_charge.throw", "entity.generic.wind_burst"},
            {"entity.wind_charge.wind_burst", "entity.generic.wind_burst"},
            {"event.mob_effect.bad_omen", "block.stone.break"},
            {"event.mob_effect.raid_omen", "block.stone.break"},
            {"event.mob_effect.trial_omen", "block.stone.break"},
            {"item.armor.equip_wolf", "block.stone.break"},
            {"item.armor.unequip_wolf", "block.stone.break"},
            {"item.mace.smash_air", "block.stone.break"},
            {"item.mace.smash_ground", "block.stone.break"},
            {"item.mace.smash_ground_heavy", "block.stone.break"},
            {"item.ominous_bottle.dispose", "block.stone.break"},
            {"item.wolf_armor.break", "block.stone.break"},
            {"item.wolf_armor.crack", "block.stone.break"},
            {"item.wolf_armor.damage", "block.stone.break"},
            {"item.wolf_armor.repair", "block.stone.break"},
            {"music_disc.creator", "block.stone.break"},
            {"music_disc.creator_music_box", "block.stone.break"},
            {"music_disc.precipice", "block.stone.break"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_767_TO_763 = {
            {"block.cobweb.break", "block.stone.break"},
            {"block.cobweb.fall", "block.stone.break"},
            {"block.cobweb.hit", "block.stone.break"},
            {"block.cobweb.place", "block.stone.break"},
            {"block.cobweb.step", "block.stone.break"},
            {"block.copper_bulb.break", "block.glass.break"},
            {"block.copper_bulb.fall", "block.glass.fall"},
            {"block.copper_bulb.hit", "block.glass.hit"},
            {"block.copper_bulb.place", "block.glass.place"},
            {"block.copper_bulb.step", "block.glass.step"},
            {"block.copper_bulb.turn_off", "block.lever.click"},
            {"block.copper_bulb.turn_on", "block.lever.click"},
            {"block.copper_door.close", "block.iron_door.close"},
            {"block.copper_door.open", "block.iron_door.open"},
            {"block.copper_grate.break", "block.metal.break"},
            {"block.copper_grate.fall", "block.metal.fall"},
            {"block.copper_grate.hit", "block.metal.hit"},
            {"block.copper_grate.place", "block.metal.place"},
            {"block.copper_grate.step", "block.metal.step"},
            {"block.copper_trapdoor.close", "block.iron_trapdoor.close"},
            {"block.copper_trapdoor.open", "block.iron_trapdoor.open"},
            {"block.crafter.craft", "block.wooden_button.click_on"},
            {"block.crafter.fail", "block.wooden_button.click_off"},
            {"block.decorated_pot.insert", "block.glass.place"},
            {"block.decorated_pot.insert_fail", "block.glass.break"},
            {"block.hanging_sign.waxed_interact_fail", "block.wooden_button.click_off"},
            {"block.heavy_core.break", "block.stone.break"},
            {"block.heavy_core.fall", "block.stone.break"},
            {"block.heavy_core.hit", "block.stone.break"},
            {"block.heavy_core.place", "block.stone.break"},
            {"block.heavy_core.step", "block.stone.break"},
            {"block.polished_tuff.break", "block.stone.break"},
            {"block.polished_tuff.fall", "block.stone.fall"},
            {"block.polished_tuff.hit", "block.stone.hit"},
            {"block.polished_tuff.place", "block.stone.place"},
            {"block.polished_tuff.step", "block.stone.step"},
            {"block.sponge.absorb", "block.water.ambient"},
            {"block.sponge.break", "block.wool.break"},
            {"block.sponge.fall", "block.wool.fall"},
            {"block.sponge.hit", "block.wool.hit"},
            {"block.sponge.place", "block.wool.place"},
            {"block.sponge.step", "block.wool.step"},
            {"block.trial_spawner.about_to_spawn_item", "block.stone.break"},
            {"block.trial_spawner.ambient", "block.bubble_column.upwards_ambient"},
            {"block.trial_spawner.ambient_ominous", "block.stone.break"},
            {"block.trial_spawner.break", "block.stone.break"},
            {"block.trial_spawner.close_shutter", "block.iron_door.close"},
            {"block.trial_spawner.detect_player", "block.note_block.pling"},
            {"block.trial_spawner.eject_item", "block.dispenser.dispense"},
            {"block.trial_spawner.fall", "block.stone.fall"},
            {"block.trial_spawner.hit", "block.stone.hit"},
            {"block.trial_spawner.ominous_activate", "block.stone.break"},
            {"block.trial_spawner.open_shutter", "block.iron_door.open"},
            {"block.trial_spawner.place", "block.stone.place"},
            {"block.trial_spawner.spawn_item", "block.stone.break"},
            {"block.trial_spawner.spawn_item_begin", "block.stone.break"},
            {"block.trial_spawner.spawn_mob", "block.beacon.activate"},
            {"block.trial_spawner.step", "block.stone.step"},
            {"block.tuff_bricks.break", "block.stone.break"},
            {"block.tuff_bricks.fall", "block.stone.fall"},
            {"block.tuff_bricks.hit", "block.stone.hit"},
            {"block.tuff_bricks.place", "block.stone.place"},
            {"block.tuff_bricks.step", "block.stone.step"},
            {"block.vault.activate", "block.stone.break"},
            {"block.vault.ambient", "block.stone.break"},
            {"block.vault.break", "block.stone.break"},
            {"block.vault.close_shutter", "block.stone.break"},
            {"block.vault.deactivate", "block.stone.break"},
            {"block.vault.eject_item", "block.stone.break"},
            {"block.vault.fall", "block.stone.break"},
            {"block.vault.hit", "block.stone.break"},
            {"block.vault.insert_item", "block.stone.break"},
            {"block.vault.insert_item_fail", "block.stone.break"},
            {"block.vault.open_shutter", "block.stone.break"},
            {"block.vault.place", "block.stone.break"},
            {"block.vault.reject_rewarded_player", "block.stone.break"},
            {"block.vault.step", "block.stone.break"},
            {"block.wet_sponge.break", "block.wool.break"},
            {"block.wet_sponge.dries", "block.stone.break"},
            {"block.wet_sponge.fall", "block.wool.fall"},
            {"block.wet_sponge.hit", "block.wool.hit"},
            {"block.wet_sponge.place", "block.wool.place"},
            {"block.wet_sponge.step", "block.wool.step"},
            {"entity.armadillo.ambient", "block.stone.break"},
            {"entity.armadillo.brush", "block.stone.break"},
            {"entity.armadillo.death", "block.stone.break"},
            {"entity.armadillo.eat", "block.stone.break"},
            {"entity.armadillo.hurt", "block.stone.break"},
            {"entity.armadillo.hurt_reduced", "block.stone.break"},
            {"entity.armadillo.land", "block.stone.break"},
            {"entity.armadillo.peek", "block.stone.break"},
            {"entity.armadillo.roll", "block.stone.break"},
            {"entity.armadillo.scute_drop", "block.stone.break"},
            {"entity.armadillo.step", "block.stone.break"},
            {"entity.armadillo.unroll_finish", "block.stone.break"},
            {"entity.armadillo.unroll_start", "block.stone.break"},
            {"entity.bogged.ambient", "block.stone.break"},
            {"entity.bogged.death", "block.stone.break"},
            {"entity.bogged.hurt", "block.stone.break"},
            {"entity.bogged.shear", "block.stone.break"},
            {"entity.bogged.step", "block.stone.break"},
            {"entity.breeze.charge", "entity.blaze.ambient"},
            {"entity.breeze.death", "entity.blaze.death"},
            {"entity.breeze.deflect", "block.stone.break"},
            {"entity.breeze.hurt", "entity.blaze.hurt"},
            {"entity.breeze.idle_air", "entity.blaze.ambient"},
            {"entity.breeze.idle_ground", "entity.blaze.ambient"},
            {"entity.breeze.inhale", "entity.blaze.ambient"},
            {"entity.breeze.jump", "entity.blaze.ambient"},
            {"entity.breeze.land", "entity.blaze.ambient"},
            {"entity.breeze.shoot", "entity.blaze.shoot"},
            {"entity.breeze.slide", "entity.blaze.ambient"},
            {"entity.breeze.whirl", "block.stone.break"},
            {"entity.breeze.wind_burst", "entity.generic.explode"},
            {"entity.donkey.jump", "block.stone.break"},
            {"entity.mule.jump", "block.stone.break"},
            {"entity.parrot.imitate.bogged", "block.stone.break"},
            {"entity.parrot.imitate.breeze", "entity.parrot.imitate.blaze"},
            {"entity.player.teleport", "entity.enderman.teleport"},
            {"entity.wind_charge.throw", "entity.generic.explode"},
            {"entity.wind_charge.wind_burst", "entity.generic.explode"},
            {"event.mob_effect.bad_omen", "block.stone.break"},
            {"event.mob_effect.raid_omen", "block.stone.break"},
            {"event.mob_effect.trial_omen", "block.stone.break"},
            {"item.armor.equip_wolf", "block.stone.break"},
            {"item.armor.unequip_wolf", "block.stone.break"},
            {"item.mace.smash_air", "block.stone.break"},
            {"item.mace.smash_ground", "block.stone.break"},
            {"item.mace.smash_ground_heavy", "block.stone.break"},
            {"item.ominous_bottle.dispose", "block.stone.break"},
            {"item.wolf_armor.break", "block.stone.break"},
            {"item.wolf_armor.crack", "block.stone.break"},
            {"item.wolf_armor.damage", "block.stone.break"},
            {"item.wolf_armor.repair", "block.stone.break"},
            {"music_disc.creator", "block.stone.break"},
            {"music_disc.creator_music_box", "block.stone.break"},
            {"music_disc.precipice", "block.stone.break"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_767_TO_764 = {
            {"block.cobweb.break", "block.stone.break"},
            {"block.cobweb.fall", "block.stone.break"},
            {"block.cobweb.hit", "block.stone.break"},
            {"block.cobweb.place", "block.stone.break"},
            {"block.cobweb.step", "block.stone.break"},
            {"block.copper_bulb.break", "block.glass.break"},
            {"block.copper_bulb.fall", "block.glass.fall"},
            {"block.copper_bulb.hit", "block.glass.hit"},
            {"block.copper_bulb.place", "block.glass.place"},
            {"block.copper_bulb.step", "block.glass.step"},
            {"block.copper_bulb.turn_off", "block.lever.click"},
            {"block.copper_bulb.turn_on", "block.lever.click"},
            {"block.copper_door.close", "block.iron_door.close"},
            {"block.copper_door.open", "block.iron_door.open"},
            {"block.copper_grate.break", "block.metal.break"},
            {"block.copper_grate.fall", "block.metal.fall"},
            {"block.copper_grate.hit", "block.metal.hit"},
            {"block.copper_grate.place", "block.metal.place"},
            {"block.copper_grate.step", "block.metal.step"},
            {"block.copper_trapdoor.close", "block.iron_trapdoor.close"},
            {"block.copper_trapdoor.open", "block.iron_trapdoor.open"},
            {"block.crafter.craft", "block.wooden_button.click_on"},
            {"block.crafter.fail", "block.wooden_button.click_off"},
            {"block.decorated_pot.insert", "block.glass.place"},
            {"block.decorated_pot.insert_fail", "block.glass.break"},
            {"block.hanging_sign.waxed_interact_fail", "block.wooden_button.click_off"},
            {"block.heavy_core.break", "block.stone.break"},
            {"block.heavy_core.fall", "block.stone.break"},
            {"block.heavy_core.hit", "block.stone.break"},
            {"block.heavy_core.place", "block.stone.break"},
            {"block.heavy_core.step", "block.stone.break"},
            {"block.polished_tuff.break", "block.stone.break"},
            {"block.polished_tuff.fall", "block.stone.fall"},
            {"block.polished_tuff.hit", "block.stone.hit"},
            {"block.polished_tuff.place", "block.stone.place"},
            {"block.polished_tuff.step", "block.stone.step"},
            {"block.trial_spawner.about_to_spawn_item", "block.stone.break"},
            {"block.trial_spawner.ambient", "block.bubble_column.upwards_ambient"},
            {"block.trial_spawner.ambient_ominous", "block.stone.break"},
            {"block.trial_spawner.break", "block.stone.break"},
            {"block.trial_spawner.close_shutter", "block.iron_door.close"},
            {"block.trial_spawner.detect_player", "block.note_block.pling"},
            {"block.trial_spawner.eject_item", "block.dispenser.dispense"},
            {"block.trial_spawner.fall", "block.stone.fall"},
            {"block.trial_spawner.hit", "block.stone.hit"},
            {"block.trial_spawner.ominous_activate", "block.stone.break"},
            {"block.trial_spawner.open_shutter", "block.iron_door.open"},
            {"block.trial_spawner.place", "block.stone.place"},
            {"block.trial_spawner.spawn_item", "block.stone.break"},
            {"block.trial_spawner.spawn_item_begin", "block.stone.break"},
            {"block.trial_spawner.spawn_mob", "block.beacon.activate"},
            {"block.trial_spawner.step", "block.stone.step"},
            {"block.tuff_bricks.break", "block.stone.break"},
            {"block.tuff_bricks.fall", "block.stone.fall"},
            {"block.tuff_bricks.hit", "block.stone.hit"},
            {"block.tuff_bricks.place", "block.stone.place"},
            {"block.tuff_bricks.step", "block.stone.step"},
            {"block.vault.activate", "block.stone.break"},
            {"block.vault.ambient", "block.stone.break"},
            {"block.vault.break", "block.stone.break"},
            {"block.vault.close_shutter", "block.stone.break"},
            {"block.vault.deactivate", "block.stone.break"},
            {"block.vault.eject_item", "block.stone.break"},
            {"block.vault.fall", "block.stone.break"},
            {"block.vault.hit", "block.stone.break"},
            {"block.vault.insert_item", "block.stone.break"},
            {"block.vault.insert_item_fail", "block.stone.break"},
            {"block.vault.open_shutter", "block.stone.break"},
            {"block.vault.place", "block.stone.break"},
            {"block.vault.reject_rewarded_player", "block.stone.break"},
            {"block.vault.step", "block.stone.break"},
            {"block.wet_sponge.dries", "block.stone.break"},
            {"entity.armadillo.ambient", "block.stone.break"},
            {"entity.armadillo.brush", "block.stone.break"},
            {"entity.armadillo.death", "block.stone.break"},
            {"entity.armadillo.eat", "block.stone.break"},
            {"entity.armadillo.hurt", "block.stone.break"},
            {"entity.armadillo.hurt_reduced", "block.stone.break"},
            {"entity.armadillo.land", "block.stone.break"},
            {"entity.armadillo.peek", "block.stone.break"},
            {"entity.armadillo.roll", "block.stone.break"},
            {"entity.armadillo.scute_drop", "block.stone.break"},
            {"entity.armadillo.step", "block.stone.break"},
            {"entity.armadillo.unroll_finish", "block.stone.break"},
            {"entity.armadillo.unroll_start", "block.stone.break"},
            {"entity.bogged.ambient", "block.stone.break"},
            {"entity.bogged.death", "block.stone.break"},
            {"entity.bogged.hurt", "block.stone.break"},
            {"entity.bogged.shear", "block.stone.break"},
            {"entity.bogged.step", "block.stone.break"},
            {"entity.breeze.charge", "entity.blaze.ambient"},
            {"entity.breeze.death", "entity.blaze.death"},
            {"entity.breeze.deflect", "block.stone.break"},
            {"entity.breeze.hurt", "entity.blaze.hurt"},
            {"entity.breeze.idle_air", "entity.blaze.ambient"},
            {"entity.breeze.idle_ground", "entity.blaze.ambient"},
            {"entity.breeze.inhale", "entity.blaze.ambient"},
            {"entity.breeze.jump", "entity.blaze.ambient"},
            {"entity.breeze.land", "entity.blaze.ambient"},
            {"entity.breeze.shoot", "entity.blaze.shoot"},
            {"entity.breeze.slide", "entity.blaze.ambient"},
            {"entity.breeze.whirl", "block.stone.break"},
            {"entity.breeze.wind_burst", "entity.generic.explode"},
            {"entity.donkey.jump", "block.stone.break"},
            {"entity.mule.jump", "block.stone.break"},
            {"entity.parrot.imitate.bogged", "block.stone.break"},
            {"entity.parrot.imitate.breeze", "entity.parrot.imitate.blaze"},
            {"entity.player.teleport", "entity.enderman.teleport"},
            {"entity.wind_charge.throw", "entity.generic.explode"},
            {"entity.wind_charge.wind_burst", "entity.generic.explode"},
            {"event.mob_effect.bad_omen", "block.stone.break"},
            {"event.mob_effect.raid_omen", "block.stone.break"},
            {"event.mob_effect.trial_omen", "block.stone.break"},
            {"item.armor.equip_wolf", "block.stone.break"},
            {"item.armor.unequip_wolf", "block.stone.break"},
            {"item.mace.smash_air", "block.stone.break"},
            {"item.mace.smash_ground", "block.stone.break"},
            {"item.mace.smash_ground_heavy", "block.stone.break"},
            {"item.ominous_bottle.dispose", "block.stone.break"},
            {"item.wolf_armor.break", "block.stone.break"},
            {"item.wolf_armor.crack", "block.stone.break"},
            {"item.wolf_armor.damage", "block.stone.break"},
            {"item.wolf_armor.repair", "block.stone.break"},
            {"music_disc.creator", "block.stone.break"},
            {"music_disc.creator_music_box", "block.stone.break"},
            {"music_disc.precipice", "block.stone.break"},
    };

    static final int[][] BLOCK_STATE_763_TO_767_RANGES = {
            {0, 5848, 0}, {5850, 6811, -1}, {6812, 8826, 0}, {8827, 8842, 16}, {8847, 8862, 36}, {8867, 8882, 56},
            {8887, 8902, 76}, {8907, 8922, 96}, {8927, 8942, 116}, {8947, 8962, 136}, {8966, 10224, 140}, {10225, 20940, 141},
            {20941, 21562, 1375}, {21567, 21572, 1375}, {21573, 22268, 1383}, {22269, 24134, 2455},
    };

    static final int[][] BLOCK_STATE_763_TO_767_OVERRIDES = {
            {5849, 6811}, {8843, 8860}, {8844, 8862}, {8845, 8864}, {8846, 8866}, {8863, 8900}, {8864, 8902}, {8865, 8904},
            {8866, 8906}, {8883, 8940}, {8884, 8942}, {8885, 8944}, {8886, 8946}, {8903, 8980}, {8904, 8982}, {8905, 8984},
            {8906, 8986}, {8923, 9020}, {8924, 9022}, {8925, 9024}, {8926, 9026}, {8943, 9060}, {8944, 9062}, {8945, 9064},
            {8946, 9066}, {8963, 9100}, {8964, 9102}, {8965, 9104}, {21563, 22941}, {21564, 22940}, {21565, 22939}, {21566, 22938},
    };
    // BLOCK_STATE_763_TO_767: entries=24135 ranges=16 overrides=32

    static final int[][] ITEM_763_TO_767_RANGES = {
            {0, 12, 0}, {13, 71, 13}, {72, 81, 14}, {82, 97, 18}, {98, 699, 22}, {700, 711, 30},
            {712, 757, 38}, {759, 807, 41}, {809, 940, 40}, {941, 966, 41}, {967, 971, 42}, {972, 1045, 44},
            {1046, 1047, 45}, {1048, 1125, 46}, {1126, 1137, 48}, {1138, 1151, 49}, {1152, 1234, 51}, {1235, 1242, 53},
            {1244, 1250, 55}, {1251, 1254, 56},
    };

    static final int[][] ITEM_763_TO_767_OVERRIDES = {
            {758, 798}, {808, 799}, {1243, 1297},
    };
    // ITEM_763_TO_767: entries=1255 ranges=20 overrides=3

    static final int[][] ENTITY_TYPE_763_TO_767_RANGES = {
            {0, 1, 0}, {2, 9, 1}, {10, 56, 4}, {57, 111, 5}, {112, 123, 6},
    };

    static final int[][] ENTITY_TYPE_763_TO_767_OVERRIDES = {
    };
    // ENTITY_TYPE_763_TO_767: entries=124 ranges=5 overrides=0

    static final int[][] SOUND_763_TO_767_RANGES = {
            {0, 53, 0}, {54, 62, 13}, {63, 153, 15}, {154, 165, 20}, {166, 286, 33}, {287, 300, 38},
            {301, 305, 45}, {306, 315, 54}, {316, 331, 56}, {332, 368, 58}, {369, 598, 59}, {599, 608, 64},
            {609, 719, 80}, {720, 783, 83}, {784, 801, 84}, {802, 923, 87}, {924, 942, 88}, {943, 1028, 90},
            {1029, 1257, 91}, {1258, 1307, 97}, {1308, 1328, 107}, {1329, 1392, 121}, {1393, 1401, 122}, {1402, 1417, 130},
            {1418, 1473, 134},
    };

    static final int[][] SOUND_763_TO_767_OVERRIDES = {
    };
    // SOUND_763_TO_767: entries=1474 ranges=25 overrides=0

    static final int[][] PARTICLE_763_TO_767_RANGES = {
            {1, 23, -1}, {24, 28, 3}, {29, 42, 4}, {43, 51, 5}, {52, 94, 6},
    };

    static final int[][] PARTICLE_763_TO_767_OVERRIDES = {
            {0, 20},
    };
    // PARTICLE_763_TO_767: entries=95 ranges=5 overrides=1

    static final int[][] BLOCK_763_TO_767_RANGES = {
            {0, 254, 0}, {256, 311, -1}, {312, 909, 0}, {910, 918, 13}, {923, 928, 13}, {929, 952, 21},
            {953, 1002, 53},
    };

    static final int[][] BLOCK_763_TO_767_OVERRIDES = {
            {255, 311}, {919, 935}, {920, 934}, {921, 933}, {922, 932},
    };
    // BLOCK_763_TO_767: entries=1003 ranges=7 overrides=5

    static final int[][] MENU_763_TO_767_RANGES = {
            {0, 6, 0}, {7, 23, 1},
    };

    static final int[][] MENU_763_TO_767_OVERRIDES = {
    };
    // MENU_763_TO_767: entries=24 ranges=2 overrides=0

    static final int[][] BLOCK_STATE_767_TO_763_RANGES = {
            {0, 5848, 0}, {5849, 6810, 1}, {6812, 8842, 0}, {8843, 8859, -16}, {8860, 8861, -17}, {8862, 8863, -18},
            {8864, 8865, -19}, {8866, 8882, -20}, {8883, 8899, -36}, {8900, 8901, -37}, {8902, 8903, -38}, {8904, 8905, -39},
            {8906, 8922, -40}, {8923, 8939, -56}, {8940, 8941, -57}, {8942, 8943, -58}, {8944, 8945, -59}, {8946, 8962, -60},
            {8963, 8979, -76}, {8980, 8981, -77}, {8982, 8983, -78}, {8984, 8985, -79}, {8986, 9002, -80}, {9003, 9019, -96},
            {9020, 9021, -97}, {9022, 9023, -98}, {9024, 9025, -99}, {9026, 9042, -100}, {9043, 9059, -116}, {9060, 9061, -117},
            {9062, 9063, -118}, {9064, 9065, -119}, {9066, 9082, -120}, {9083, 9099, -136}, {9100, 9101, -137}, {9102, 9103, -138},
            {9104, 9105, -139}, {9106, 10365, -140}, {10366, 21081, -141}, {21082, 21087, -10001}, {21088, 21167, -7787}, {21168, 21491, -5529},
            {21493, 21498, -10412}, {21499, 21578, -8198}, {21579, 21902, -5940}, {21905, 21910, -10782}, {21911, 21990, -14802}, {21991, 22314, -6352},
            {22316, 22937, -1375}, {22942, 22947, -1375}, {22956, 23651, -1383}, {23652, 23715, -18000}, {23716, 23779, -18064}, {23780, 23843, -18128},
            {23844, 23907, -18192}, {23908, 23971, -18256}, {23972, 24035, -18320}, {24036, 24099, -18384}, {24100, 24163, -18448}, {24164, 24227, -13906},
            {24228, 24291, -13970}, {24292, 24355, -14034}, {24356, 24419, -14098}, {24420, 24483, -14162}, {24484, 24547, -14226}, {24548, 24611, -14290},
            {24612, 24675, -14354}, {24724, 26589, -2455},
    };

    static final int[][] BLOCK_STATE_767_TO_763_OVERRIDES = {
            {6811, 5849}, {21492, 20940}, {21903, 6541}, {21904, 6538}, {22315, 6541}, {22938, 21566}, {22939, 21565}, {22940, 21564},
            {22941, 21563}, {22948, 21566}, {22949, 21566}, {22950, 21566}, {22951, 21566}, {22952, 21566}, {22953, 21566}, {22954, 21566},
            {22955, 21566}, {24676, 6771}, {24677, 6773}, {24678, 6771}, {24679, 6773}, {24680, 6771}, {24681, 6773}, {24682, 6771},
            {24683, 6773}, {24684, 6771}, {24685, 6773}, {24686, 6771}, {24687, 6773}, {24688, 6771}, {24689, 6773}, {24690, 6771},
            {24691, 6773}, {24692, 5864}, {24693, 5864}, {24694, 5864}, {24695, 5864}, {24696, 5864}, {24697, 5864}, {24698, 5864},
            {24699, 5864}, {24700, 5864}, {24701, 5864}, {24702, 5864}, {24703, 5864}, {24704, 5864}, {24705, 5864}, {24706, 5864},
            {24707, 5864}, {24708, 5864}, {24709, 5864}, {24710, 5864}, {24711, 5864}, {24712, 5864}, {24713, 5864}, {24714, 5864},
            {24715, 5864}, {24716, 5864}, {24717, 5864}, {24718, 5864}, {24719, 5864}, {24720, 5864}, {24721, 5864}, {24722, 5864},
            {24723, 5864}, {26590, 4277}, {26591, 4277}, {26592, 4277}, {26593, 4277}, {26594, 4277}, {26595, 4277}, {26596, 4277},
            {26597, 4277}, {26598, 4277}, {26599, 4277}, {26600, 4277}, {26601, 4277}, {26602, 4277}, {26603, 4277}, {26604, 4277},
            {26605, 4277}, {26606, 4277}, {26607, 4277}, {26608, 4277}, {26609, 4277}, {26610, 4277}, {26611, 4277}, {26612, 4277},
            {26613, 4277}, {26614, 4277}, {26615, 4277}, {26616, 4277}, {26617, 4277}, {26618, 4277}, {26619, 4277}, {26620, 4277},
            {26621, 4277}, {26622, 4277}, {26623, 4277}, {26624, 4277}, {26625, 4277}, {26626, 4277}, {26627, 4277}, {26628, 4277},
            {26629, 4277}, {26630, 4277}, {26631, 4277}, {26632, 4277}, {26633, 4277}, {26634, 4277}, {26635, 4277}, {26636, 4277},
            {26637, 4277}, {26638, 2873}, {26639, 2873}, {26640, 2873}, {26641, 2873}, {26642, 2873}, {26643, 2873}, {26644, 2873},
            {26645, 2873}, {26646, 2873}, {26647, 2873}, {26648, 2873}, {26649, 2873}, {26650, 2873}, {26651, 2873}, {26652, 2873},
            {26653, 2873}, {26654, 2873}, {26655, 2873}, {26656, 2873}, {26657, 2873}, {26658, 2873}, {26659, 2873}, {26660, 2873},
            {26661, 2873}, {26662, 2873}, {26663, 2873}, {26664, 2873}, {26665, 2873}, {26666, 2873}, {26667, 2873}, {26668, 2873},
            {26669, 2873}, {26670, 2873}, {26671, 2873}, {26672, 2873}, {26673, 2873}, {26674, 2873}, {26675, 2873}, {26676, 2873},
            {26677, 2873}, {26678, 2873}, {26679, 2873}, {26680, 2873}, {26681, 2873}, {26682, 1}, {26683, 1},
    };
    // BLOCK_STATE_767_TO_763: entries=26684 ranges=68 overrides=159

    static final int[][] ITEM_767_TO_763_RANGES = {
            {0, 12, 0}, {26, 84, -13}, {86, 95, -14}, {100, 115, -18}, {120, 721, -22}, {730, 741, -30},
            {750, 795, -38}, {800, 848, -41}, {849, 980, -40}, {982, 1007, -41}, {1009, 1013, -42}, {1015, 1089, -44},
            {1091, 1092, -45}, {1094, 1171, -46}, {1174, 1185, -48}, {1187, 1200, -49}, {1203, 1285, -51}, {1288, 1295, -53},
            {1299, 1305, -55}, {1307, 1310, -56},
    };

    static final int[][] ITEM_767_TO_763_OVERRIDES = {
            {13, 12}, {14, 12}, {15, 12}, {16, 12}, {17, 12}, {18, 12}, {19, 12}, {20, 12},
            {21, 318}, {22, 318}, {23, 318}, {24, 318}, {25, 321}, {85, 1}, {96, 75}, {97, 75},
            {98, 75}, {99, 75}, {116, 75}, {117, 75}, {118, 75}, {119, 75}, {722, 688}, {723, 688},
            {724, 688}, {725, 688}, {726, 688}, {727, 688}, {728, 688}, {729, 688}, {742, 700}, {743, 700},
            {744, 700}, {745, 700}, {746, 700}, {747, 700}, {748, 700}, {749, 700}, {796, 1}, {797, 1},
            {798, 758}, {799, 808}, {981, 278}, {1008, 1}, {1014, 1}, {1090, 1}, {1093, 1}, {1172, 1},
            {1173, 1}, {1186, 1}, {1201, 1}, {1202, 1}, {1286, 1}, {1287, 1}, {1296, 1}, {1297, 1243},
            {1298, 1}, {1306, 1}, {1311, 333}, {1312, 333}, {1313, 333}, {1314, 333}, {1315, 333}, {1316, 333},
            {1317, 333}, {1318, 333}, {1319, 310}, {1320, 310}, {1321, 310}, {1322, 310}, {1323, 310}, {1324, 310},
            {1325, 310}, {1326, 310}, {1327, 276}, {1328, 655}, {1329, 1}, {1330, 1}, {1331, 1}, {1332, 1},
    };
    // ITEM_767_TO_763: entries=1333 ranges=20 overrides=80

    static final int[][] ENTITY_TYPE_767_TO_763_RANGES = {
            {0, 1, 0}, {3, 10, -1}, {14, 60, -4}, {62, 116, -5}, {118, 129, -6},
    };

    static final int[][] ENTITY_TYPE_767_TO_763_OVERRIDES = {
            {2, 72}, {11, 72}, {12, 7}, {13, 72}, {61, 72}, {117, 89},
    };
    // ENTITY_TYPE_767_TO_763: entries=130 ranges=5 overrides=6

    static final int[][] SOUND_767_TO_763_RANGES = {
            {0, 53, 0}, {67, 75, -13}, {78, 168, -15}, {174, 185, -20}, {195, 196, -46}, {199, 319, -33},
            {325, 338, -38}, {346, 350, -45}, {351, 352, 315}, {358, 359, 316}, {360, 369, -54}, {372, 387, -56},
            {390, 426, -58}, {428, 657, -59}, {663, 672, -64}, {689, 799, -80}, {803, 866, -83}, {868, 885, -84},
            {889, 1010, -87}, {1012, 1030, -88}, {1032, 1118, -90}, {1120, 1348, -91}, {1349, 1353, 91}, {1355, 1404, -97},
            {1406, 1408, -139}, {1411, 1413, -144}, {1415, 1435, -107}, {1450, 1513, -121}, {1515, 1523, -122}, {1526, 1529, -85},
            {1532, 1547, -130}, {1552, 1607, -134},
    };

    static final int[][] SOUND_767_TO_763_OVERRIDES = {
            {54, 1264}, {55, 1264}, {56, 1264}, {57, 1264}, {58, 1264}, {59, 1264}, {60, 1264}, {61, 1264},
            {62, 1264}, {63, 1264}, {64, 1264}, {65, 1264}, {66, 1264}, {76, 1264}, {77, 1264}, {169, 1264},
            {170, 1264}, {171, 1264}, {172, 1264}, {173, 1264}, {186, 147}, {187, 1264}, {188, 147}, {189, 147},
            {190, 147}, {191, 151}, {192, 147}, {193, 147}, {194, 147}, {197, 1264}, {198, 517}, {320, 1264},
            {321, 1264}, {322, 1264}, {323, 1264}, {324, 1264}, {339, 534}, {340, 538}, {341, 537}, {342, 536},
            {343, 535}, {344, 700}, {345, 700}, {353, 733}, {354, 739}, {355, 736}, {356, 735}, {357, 734},
            {370, 1432}, {371, 1431}, {388, 537}, {389, 534}, {427, 1264}, {658, 1264}, {659, 1264}, {660, 1264},
            {661, 1264}, {662, 1264}, {673, 1264}, {674, 1272}, {675, 1269}, {676, 1268}, {677, 1267}, {678, 125},
            {679, 1264}, {680, 1264}, {681, 1264}, {682, 907}, {683, 1264}, {684, 173}, {685, 1264}, {686, 667},
            {687, 666}, {688, 350}, {800, 1264}, {801, 1264}, {802, 1264}, {867, 1264}, {886, 1264}, {887, 1264},
            {888, 1264}, {1011, 1264}, {1031, 1264}, {1119, 423}, {1354, 1394}, {1405, 1264}, {1409, 1272}, {1410, 1264},
            {1414, 1272}, {1436, 1264}, {1437, 1264}, {1438, 1264}, {1439, 1264}, {1440, 1264}, {1441, 1264}, {1442, 1264},
            {1443, 1264}, {1444, 1264}, {1445, 1264}, {1446, 1264}, {1447, 1264}, {1448, 1264}, {1449, 1264}, {1514, 1431},
            {1524, 1440}, {1525, 1264}, {1530, 517}, {1531, 517}, {1548, 1264}, {1549, 1264}, {1550, 1264}, {1551, 1264},
            {1608, 1264}, {1609, 1264}, {1610, 1264},
    };
    // SOUND_767_TO_763: entries=1611 ranges=32 overrides=115

    static final int[][] PARTICLE_767_TO_763_RANGES = {
            {0, 22, 1}, {27, 31, -3}, {33, 46, -4}, {48, 56, -5}, {57, 100, -6},
    };

    static final int[][] PARTICLE_767_TO_763_OVERRIDES = {
            {23, 5}, {24, 14}, {25, 5}, {26, 5}, {32, 0}, {47, 0}, {101, 14}, {102, 30},
            {103, 30}, {104, 0}, {105, 0}, {106, 0}, {107, 0}, {108, 0},
    };
    // PARTICLE_767_TO_763: entries=109 ranges=5 overrides=14

    static final int[][] BLOCK_767_TO_763_RANGES = {
            {0, 254, 0}, {255, 310, 1}, {312, 909, 0}, {923, 931, -13}, {936, 941, -13}, {950, 973, -21},
            {1006, 1055, -53},
    };

    static final int[][] BLOCK_767_TO_763_OVERRIDES = {
            {311, 255}, {910, 909}, {911, 909}, {912, 909}, {913, 909}, {914, 909}, {915, 909}, {916, 909},
            {917, 909}, {918, 294}, {919, 294}, {920, 294}, {921, 294}, {922, 297}, {932, 922}, {933, 921},
            {934, 920}, {935, 919}, {942, 922}, {943, 922}, {944, 922}, {945, 922}, {946, 922}, {947, 922},
            {948, 922}, {949, 922}, {974, 232}, {975, 232}, {976, 232}, {977, 232}, {978, 232}, {979, 232},
            {980, 232}, {981, 232}, {982, 466}, {983, 466}, {984, 466}, {985, 466}, {986, 466}, {987, 466},
            {988, 466}, {989, 466}, {990, 309}, {991, 309}, {992, 309}, {993, 309}, {994, 309}, {995, 309},
            {996, 309}, {997, 309}, {998, 263}, {999, 263}, {1000, 263}, {1001, 263}, {1002, 263}, {1003, 263},
            {1004, 263}, {1005, 263}, {1056, 182}, {1057, 175}, {1058, 1}, {1059, 1},
    };
    // BLOCK_767_TO_763: entries=1060 ranges=7 overrides=62

    static final int[][] MENU_767_TO_763_RANGES = {
            {0, 6, 0}, {8, 24, -1},
    };

    static final int[][] MENU_767_TO_763_OVERRIDES = {
            {7, 11},
    };
    // MENU_767_TO_763: entries=25 ranges=2 overrides=1

    static final int[][] BLOCK_STATE_764_TO_767_RANGES = {
            {0, 5848, 0}, {5850, 6811, -1}, {6812, 21081, 0}, {21082, 21703, 1234}, {21708, 21713, 1234}, {21714, 22409, 1242},
            {22410, 24275, 2314},
    };

    static final int[][] BLOCK_STATE_764_TO_767_OVERRIDES = {
            {5849, 6811}, {21704, 22941}, {21705, 22940}, {21706, 22939}, {21707, 22938},
    };
    // BLOCK_STATE_764_TO_767: entries=24276 ranges=7 overrides=5

    static final int[][] ITEM_764_TO_767_RANGES = {
            {0, 12, 0}, {13, 71, 13}, {72, 81, 14}, {82, 97, 18}, {98, 699, 22}, {700, 711, 30},
            {712, 757, 38}, {759, 807, 41}, {809, 940, 40}, {941, 966, 41}, {967, 971, 42}, {972, 1045, 44},
            {1046, 1047, 45}, {1048, 1125, 46}, {1126, 1137, 48}, {1138, 1151, 49}, {1152, 1234, 51}, {1235, 1242, 53},
            {1244, 1250, 55}, {1251, 1254, 56},
    };

    static final int[][] ITEM_764_TO_767_OVERRIDES = {
            {758, 798}, {808, 799}, {1243, 1297},
    };
    // ITEM_764_TO_767: entries=1255 ranges=20 overrides=3

    static final int[][] ENTITY_TYPE_764_TO_767_RANGES = {
            {0, 1, 0}, {2, 9, 1}, {10, 56, 4}, {57, 111, 5}, {112, 123, 6},
    };

    static final int[][] ENTITY_TYPE_764_TO_767_OVERRIDES = {
    };
    // ENTITY_TYPE_764_TO_767: entries=124 ranges=5 overrides=0

    static final int[][] SOUND_764_TO_767_RANGES = {
            {0, 53, 0}, {54, 62, 13}, {63, 153, 15}, {154, 165, 20}, {166, 286, 33}, {287, 300, 38},
            {301, 305, 45}, {306, 315, 54}, {316, 331, 56}, {332, 368, 58}, {369, 598, 59}, {599, 608, 64},
            {609, 719, 80}, {720, 783, 83}, {784, 801, 84}, {802, 923, 87}, {924, 942, 88}, {943, 1028, 90},
            {1029, 1313, 91}, {1314, 1334, 101}, {1335, 1398, 115}, {1399, 1408, 116}, {1409, 1412, 117}, {1413, 1428, 119},
            {1429, 1484, 123},
    };

    static final int[][] SOUND_764_TO_767_OVERRIDES = {
    };
    // SOUND_764_TO_767: entries=1485 ranges=25 overrides=0

    static final int[][] PARTICLE_764_TO_767_RANGES = {
            {1, 23, -1}, {24, 28, 3}, {29, 42, 4}, {43, 51, 5}, {52, 94, 6},
    };

    static final int[][] PARTICLE_764_TO_767_OVERRIDES = {
            {0, 20},
    };
    // PARTICLE_764_TO_767: entries=95 ranges=5 overrides=1

    static final int[][] BLOCK_764_TO_767_RANGES = {
            {0, 254, 0}, {256, 311, -1}, {312, 909, 0}, {910, 918, 13}, {923, 928, 13}, {929, 952, 21},
            {953, 1002, 53},
    };

    static final int[][] BLOCK_764_TO_767_OVERRIDES = {
            {255, 311}, {919, 935}, {920, 934}, {921, 933}, {922, 932},
    };
    // BLOCK_764_TO_767: entries=1003 ranges=7 overrides=5

    static final int[][] MENU_764_TO_767_RANGES = {
            {0, 6, 0}, {7, 23, 1},
    };

    static final int[][] MENU_764_TO_767_OVERRIDES = {
    };
    // MENU_764_TO_767: entries=24 ranges=2 overrides=0

    static final int[][] BLOCK_STATE_767_TO_764_RANGES = {
            {0, 5848, 0}, {5849, 6810, 1}, {6812, 21081, 0}, {21082, 21087, -9860}, {21088, 21167, -7646}, {21168, 21491, -5388},
            {21493, 21498, -10271}, {21499, 21578, -8057}, {21579, 21902, -5799}, {21905, 21910, -10641}, {21911, 21990, -14802}, {21991, 22314, -6211},
            {22316, 22937, -1234}, {22942, 22947, -1234}, {22956, 23651, -1242}, {23652, 23715, -18000}, {23716, 23779, -18064}, {23780, 23843, -18128},
            {23844, 23907, -18192}, {23908, 23971, -18256}, {23972, 24035, -18320}, {24036, 24099, -18384}, {24100, 24163, -18448}, {24164, 24227, -13765},
            {24228, 24291, -13829}, {24292, 24355, -13893}, {24356, 24419, -13957}, {24420, 24483, -14021}, {24484, 24547, -14085}, {24548, 24611, -14149},
            {24612, 24675, -14213}, {24724, 26589, -2314},
    };

    static final int[][] BLOCK_STATE_767_TO_764_OVERRIDES = {
            {6811, 5849}, {21492, 21081}, {21903, 6541}, {21904, 6538}, {22315, 6541}, {22938, 21707}, {22939, 21706}, {22940, 21705},
            {22941, 21704}, {22948, 21707}, {22949, 21707}, {22950, 21707}, {22951, 21707}, {22952, 21707}, {22953, 21707}, {22954, 21707},
            {22955, 21707}, {24676, 6771}, {24677, 6773}, {24678, 6771}, {24679, 6773}, {24680, 6771}, {24681, 6773}, {24682, 6771},
            {24683, 6773}, {24684, 6771}, {24685, 6773}, {24686, 6771}, {24687, 6773}, {24688, 6771}, {24689, 6773}, {24690, 6771},
            {24691, 6773}, {24692, 5864}, {24693, 5864}, {24694, 5864}, {24695, 5864}, {24696, 5864}, {24697, 5864}, {24698, 5864},
            {24699, 5864}, {24700, 5864}, {24701, 5864}, {24702, 5864}, {24703, 5864}, {24704, 5864}, {24705, 5864}, {24706, 5864},
            {24707, 5864}, {24708, 5864}, {24709, 5864}, {24710, 5864}, {24711, 5864}, {24712, 5864}, {24713, 5864}, {24714, 5864},
            {24715, 5864}, {24716, 5864}, {24717, 5864}, {24718, 5864}, {24719, 5864}, {24720, 5864}, {24721, 5864}, {24722, 5864},
            {24723, 5864}, {26590, 4277}, {26591, 4277}, {26592, 4277}, {26593, 4277}, {26594, 4277}, {26595, 4277}, {26596, 4277},
            {26597, 4277}, {26598, 4277}, {26599, 4277}, {26600, 4277}, {26601, 4277}, {26602, 4277}, {26603, 4277}, {26604, 4277},
            {26605, 4277}, {26606, 4277}, {26607, 4277}, {26608, 4277}, {26609, 4277}, {26610, 4277}, {26611, 4277}, {26612, 4277},
            {26613, 4277}, {26614, 4277}, {26615, 4277}, {26616, 4277}, {26617, 4277}, {26618, 4277}, {26619, 4277}, {26620, 4277},
            {26621, 4277}, {26622, 4277}, {26623, 4277}, {26624, 4277}, {26625, 4277}, {26626, 4277}, {26627, 4277}, {26628, 4277},
            {26629, 4277}, {26630, 4277}, {26631, 4277}, {26632, 4277}, {26633, 4277}, {26634, 4277}, {26635, 4277}, {26636, 4277},
            {26637, 4277}, {26638, 2873}, {26639, 2873}, {26640, 2873}, {26641, 2873}, {26642, 2873}, {26643, 2873}, {26644, 2873},
            {26645, 2873}, {26646, 2873}, {26647, 2873}, {26648, 2873}, {26649, 2873}, {26650, 2873}, {26651, 2873}, {26652, 2873},
            {26653, 2873}, {26654, 2873}, {26655, 2873}, {26656, 2873}, {26657, 2873}, {26658, 2873}, {26659, 2873}, {26660, 2873},
            {26661, 2873}, {26662, 2873}, {26663, 2873}, {26664, 2873}, {26665, 2873}, {26666, 2873}, {26667, 2873}, {26668, 2873},
            {26669, 2873}, {26670, 2873}, {26671, 2873}, {26672, 2873}, {26673, 2873}, {26674, 2873}, {26675, 2873}, {26676, 2873},
            {26677, 2873}, {26678, 2873}, {26679, 2873}, {26680, 2873}, {26681, 2873}, {26682, 1}, {26683, 1},
    };
    // BLOCK_STATE_767_TO_764: entries=26684 ranges=32 overrides=159

    static final int[][] ITEM_767_TO_764_RANGES = {
            {0, 12, 0}, {26, 84, -13}, {86, 95, -14}, {100, 115, -18}, {120, 721, -22}, {730, 741, -30},
            {750, 795, -38}, {800, 848, -41}, {849, 980, -40}, {982, 1007, -41}, {1009, 1013, -42}, {1015, 1089, -44},
            {1091, 1092, -45}, {1094, 1171, -46}, {1174, 1185, -48}, {1187, 1200, -49}, {1203, 1285, -51}, {1288, 1295, -53},
            {1299, 1305, -55}, {1307, 1310, -56},
    };

    static final int[][] ITEM_767_TO_764_OVERRIDES = {
            {13, 12}, {14, 12}, {15, 12}, {16, 12}, {17, 12}, {18, 12}, {19, 12}, {20, 12},
            {21, 318}, {22, 318}, {23, 318}, {24, 318}, {25, 321}, {85, 1}, {96, 75}, {97, 75},
            {98, 75}, {99, 75}, {116, 75}, {117, 75}, {118, 75}, {119, 75}, {722, 688}, {723, 688},
            {724, 688}, {725, 688}, {726, 688}, {727, 688}, {728, 688}, {729, 688}, {742, 700}, {743, 700},
            {744, 700}, {745, 700}, {746, 700}, {747, 700}, {748, 700}, {749, 700}, {796, 1}, {797, 1},
            {798, 758}, {799, 808}, {981, 278}, {1008, 1}, {1014, 1}, {1090, 1}, {1093, 1}, {1172, 1},
            {1173, 1}, {1186, 1}, {1201, 1}, {1202, 1}, {1286, 1}, {1287, 1}, {1296, 1}, {1297, 1243},
            {1298, 1}, {1306, 1}, {1311, 333}, {1312, 333}, {1313, 333}, {1314, 333}, {1315, 333}, {1316, 333},
            {1317, 333}, {1318, 333}, {1319, 310}, {1320, 310}, {1321, 310}, {1322, 310}, {1323, 310}, {1324, 310},
            {1325, 310}, {1326, 310}, {1327, 276}, {1328, 655}, {1329, 1}, {1330, 1}, {1331, 1}, {1332, 1},
    };
    // ITEM_767_TO_764: entries=1333 ranges=20 overrides=80

    static final int[][] ENTITY_TYPE_767_TO_764_RANGES = {
            {0, 1, 0}, {3, 10, -1}, {14, 60, -4}, {62, 116, -5}, {118, 129, -6},
    };

    static final int[][] ENTITY_TYPE_767_TO_764_OVERRIDES = {
            {2, 72}, {11, 72}, {12, 7}, {13, 72}, {61, 72}, {117, 89},
    };
    // ENTITY_TYPE_767_TO_764: entries=130 ranges=5 overrides=6

    static final int[][] SOUND_767_TO_764_RANGES = {
            {0, 53, 0}, {67, 75, -13}, {78, 168, -15}, {174, 185, -20}, {195, 196, -46}, {199, 319, -33},
            {325, 338, -38}, {346, 350, -45}, {351, 352, 315}, {358, 359, 316}, {360, 369, -54}, {372, 387, -56},
            {390, 426, -58}, {428, 657, -59}, {663, 672, -64}, {689, 799, -80}, {803, 866, -83}, {868, 885, -84},
            {889, 1010, -87}, {1012, 1030, -88}, {1032, 1118, -90}, {1120, 1404, -91}, {1406, 1408, -133}, {1411, 1413, -138},
            {1415, 1435, -101}, {1450, 1513, -115}, {1515, 1524, -116}, {1526, 1529, -117}, {1532, 1547, -119}, {1552, 1607, -123},
    };

    static final int[][] SOUND_767_TO_764_OVERRIDES = {
            {54, 1270}, {55, 1270}, {56, 1270}, {57, 1270}, {58, 1270}, {59, 1270}, {60, 1270}, {61, 1270},
            {62, 1270}, {63, 1270}, {64, 1270}, {65, 1270}, {66, 1270}, {76, 1270}, {77, 1270}, {169, 1270},
            {170, 1270}, {171, 1270}, {172, 1270}, {173, 1270}, {186, 147}, {187, 1270}, {188, 147}, {189, 147},
            {190, 147}, {191, 151}, {192, 147}, {193, 147}, {194, 147}, {197, 1270}, {198, 517}, {320, 1270},
            {321, 1270}, {322, 1270}, {323, 1270}, {324, 1270}, {339, 534}, {340, 538}, {341, 537}, {342, 536},
            {343, 535}, {344, 700}, {345, 700}, {353, 733}, {354, 739}, {355, 736}, {356, 735}, {357, 734},
            {370, 1443}, {371, 1442}, {388, 537}, {389, 534}, {427, 1270}, {658, 1270}, {659, 1270}, {660, 1270},
            {661, 1270}, {662, 1270}, {673, 1270}, {674, 1278}, {675, 1275}, {676, 1274}, {677, 1273}, {678, 125},
            {679, 1270}, {680, 1270}, {681, 1270}, {682, 907}, {683, 1270}, {684, 173}, {685, 1270}, {686, 667},
            {687, 666}, {688, 350}, {800, 1270}, {801, 1270}, {802, 1270}, {867, 1270}, {886, 1270}, {887, 1270},
            {888, 1270}, {1011, 1270}, {1031, 1270}, {1119, 423}, {1405, 1270}, {1409, 1278}, {1410, 1270}, {1414, 1278},
            {1436, 1270}, {1437, 1270}, {1438, 1270}, {1439, 1270}, {1440, 1270}, {1441, 1270}, {1442, 1270}, {1443, 1270},
            {1444, 1270}, {1445, 1270}, {1446, 1270}, {1447, 1270}, {1448, 1270}, {1449, 1270}, {1514, 1442}, {1525, 1270},
            {1530, 517}, {1531, 517}, {1548, 1270}, {1549, 1270}, {1550, 1270}, {1551, 1270}, {1608, 1270}, {1609, 1270},
            {1610, 1270},
    };
    // SOUND_767_TO_764: entries=1611 ranges=30 overrides=113

    static final int[][] PARTICLE_767_TO_764_RANGES = {
            {0, 22, 1}, {27, 31, -3}, {33, 46, -4}, {48, 56, -5}, {57, 100, -6},
    };

    static final int[][] PARTICLE_767_TO_764_OVERRIDES = {
            {23, 5}, {24, 14}, {25, 5}, {26, 5}, {32, 0}, {47, 0}, {101, 14}, {102, 30},
            {103, 30}, {104, 0}, {105, 0}, {106, 0}, {107, 0}, {108, 0},
    };
    // PARTICLE_767_TO_764: entries=109 ranges=5 overrides=14

    static final int[][] BLOCK_767_TO_764_RANGES = {
            {0, 254, 0}, {255, 310, 1}, {312, 909, 0}, {923, 931, -13}, {936, 941, -13}, {950, 973, -21},
            {1006, 1055, -53},
    };

    static final int[][] BLOCK_767_TO_764_OVERRIDES = {
            {311, 255}, {910, 909}, {911, 909}, {912, 909}, {913, 909}, {914, 909}, {915, 909}, {916, 909},
            {917, 909}, {918, 294}, {919, 294}, {920, 294}, {921, 294}, {922, 297}, {932, 922}, {933, 921},
            {934, 920}, {935, 919}, {942, 922}, {943, 922}, {944, 922}, {945, 922}, {946, 922}, {947, 922},
            {948, 922}, {949, 922}, {974, 232}, {975, 232}, {976, 232}, {977, 232}, {978, 232}, {979, 232},
            {980, 232}, {981, 232}, {982, 466}, {983, 466}, {984, 466}, {985, 466}, {986, 466}, {987, 466},
            {988, 466}, {989, 466}, {990, 309}, {991, 309}, {992, 309}, {993, 309}, {994, 309}, {995, 309},
            {996, 309}, {997, 309}, {998, 263}, {999, 263}, {1000, 263}, {1001, 263}, {1002, 263}, {1003, 263},
            {1004, 263}, {1005, 263}, {1056, 182}, {1057, 175}, {1058, 1}, {1059, 1},
    };
    // BLOCK_767_TO_764: entries=1060 ranges=7 overrides=62

    static final int[][] MENU_767_TO_764_RANGES = {
            {0, 6, 0}, {8, 24, -1},
    };

    static final int[][] MENU_767_TO_764_OVERRIDES = {
            {7, 11},
    };
    // MENU_767_TO_764: entries=25 ranges=2 overrides=1

    static final int[][] BLOCK_STATE_765_TO_767_RANGES = {
            {0, 26637, 0}, {26638, 26643, 6},
    };

    static final int[][] BLOCK_STATE_765_TO_767_OVERRIDES = {
    };
    // BLOCK_STATE_765_TO_767: entries=26644 ranges=2 overrides=0

    static final int[][] ITEM_765_TO_767_RANGES = {
            {0, 84, 0}, {85, 794, 1}, {796, 844, 4}, {846, 1004, 3}, {1005, 1009, 4}, {1010, 1084, 5},
            {1085, 1086, 6}, {1087, 1164, 7}, {1165, 1176, 9}, {1177, 1190, 10}, {1191, 1273, 12}, {1274, 1281, 14},
            {1283, 1289, 16}, {1290, 1311, 17},
    };

    static final int[][] ITEM_765_TO_767_OVERRIDES = {
            {795, 798}, {845, 799}, {1282, 1297},
    };
    // ITEM_765_TO_767: entries=1312 ranges=14 overrides=3

    static final int[][] ENTITY_TYPE_765_TO_767_RANGES = {
            {0, 1, 0}, {2, 9, 1}, {11, 57, 3}, {58, 125, 4},
    };

    static final int[][] ENTITY_TYPE_765_TO_767_OVERRIDES = {
            {10, 12},
    };
    // ENTITY_TYPE_765_TO_767: entries=126 ranges=4 overrides=1

    static final int[][] SOUND_765_TO_767_RANGES = {
            {0, 53, 0}, {54, 62, 13}, {63, 153, 15}, {154, 165, 20}, {166, 174, 22}, {175, 295, 24},
            {296, 397, 29}, {398, 627, 30}, {628, 643, 35}, {646, 759, 40}, {760, 823, 43}, {824, 841, 44},
            {842, 963, 47}, {964, 982, 48}, {983, 1386, 49}, {1387, 1461, 63}, {1462, 1466, 64}, {1467, 1482, 65},
            {1483, 1538, 69},
    };

    static final int[][] SOUND_765_TO_767_OVERRIDES = {
            {644, 682}, {645, 684},
    };
    // SOUND_765_TO_767: entries=1539 ranges=19 overrides=2

    static final int[][] PARTICLE_765_TO_767_RANGES = {
            {1, 24, -1}, {26, 30, 1}, {31, 44, 2}, {45, 98, 3},
    };

    static final int[][] PARTICLE_765_TO_767_OVERRIDES = {
            {0, 20}, {25, 25}, {99, 24}, {100, 102},
    };
    // PARTICLE_765_TO_767: entries=101 ranges=4 overrides=4

    static final int[][] BLOCK_765_TO_767_RANGES = {
            {0, 1057, 0},
    };

    static final int[][] BLOCK_765_TO_767_OVERRIDES = {
    };
    // BLOCK_765_TO_767: entries=1058 ranges=1 overrides=0

    static final int[][] MENU_765_TO_767_RANGES = {
            {0, 24, 0},
    };

    static final int[][] MENU_765_TO_767_OVERRIDES = {
    };
    // MENU_765_TO_767: entries=25 ranges=1 overrides=0

    static final int[][] BLOCK_STATE_767_TO_765_RANGES = {
            {0, 26643, 0}, {26644, 26649, -6},
    };

    static final int[][] BLOCK_STATE_767_TO_765_OVERRIDES = {
            {26650, 26638}, {26651, 26638}, {26652, 26638}, {26653, 26638}, {26654, 26638}, {26655, 26638}, {26656, 26638}, {26657, 26638},
            {26658, 26638}, {26659, 26638}, {26660, 26638}, {26661, 26638}, {26662, 26638}, {26663, 26638}, {26664, 26638}, {26665, 26638},
            {26666, 26638}, {26667, 26638}, {26668, 26638}, {26669, 26638}, {26670, 26638}, {26671, 26638}, {26672, 26638}, {26673, 26638},
            {26674, 26638}, {26675, 26638}, {26676, 26638}, {26677, 26638}, {26678, 26638}, {26679, 26638}, {26680, 26638}, {26681, 26638},
            {26682, 1}, {26683, 1},
    };
    // BLOCK_STATE_767_TO_765: entries=26684 ranges=2 overrides=34

    static final int[][] ITEM_767_TO_765_RANGES = {
            {0, 84, 0}, {86, 795, -1}, {800, 848, -4}, {849, 1007, -3}, {1009, 1013, -4}, {1015, 1089, -5},
            {1091, 1092, -6}, {1094, 1171, -7}, {1174, 1185, -9}, {1187, 1200, -10}, {1203, 1285, -12}, {1288, 1295, -14},
            {1299, 1305, -16}, {1307, 1328, -17},
    };

    static final int[][] ITEM_767_TO_765_OVERRIDES = {
            {85, 1}, {796, 1}, {797, 1}, {798, 795}, {799, 845}, {1008, 1}, {1014, 1}, {1090, 1},
            {1093, 1}, {1172, 1}, {1173, 1}, {1186, 1}, {1201, 1}, {1202, 1}, {1286, 1}, {1287, 1},
            {1296, 1}, {1297, 1282}, {1298, 1}, {1306, 1}, {1329, 1}, {1330, 1}, {1331, 1}, {1332, 1},
    };
    // ITEM_767_TO_765: entries=1333 ranges=14 overrides=24

    static final int[][] ENTITY_TYPE_767_TO_765_RANGES = {
            {0, 1, 0}, {3, 10, -1}, {14, 60, -3}, {62, 129, -4},
    };

    static final int[][] ENTITY_TYPE_767_TO_765_OVERRIDES = {
            {2, 73}, {11, 73}, {12, 10}, {13, 73}, {61, 73},
    };
    // ENTITY_TYPE_767_TO_765: entries=130 ranges=4 overrides=5

    static final int[][] SOUND_767_TO_765_RANGES = {
            {0, 53, 0}, {67, 75, -13}, {78, 168, -15}, {174, 186, -20}, {188, 196, -22}, {199, 319, -24},
            {325, 426, -29}, {428, 657, -30}, {663, 678, -35}, {686, 799, -40}, {803, 866, -43}, {868, 885, -44},
            {889, 1010, -47}, {1012, 1030, -48}, {1032, 1435, -49}, {1450, 1524, -63}, {1526, 1530, -64}, {1531, 1547, -65},
            {1552, 1607, -69},
    };

    static final int[][] SOUND_767_TO_765_OVERRIDES = {
            {54, 1312}, {55, 1312}, {56, 1312}, {57, 1312}, {58, 1312}, {59, 1312}, {60, 1312}, {61, 1312},
            {62, 1312}, {63, 1312}, {64, 1312}, {65, 1312}, {66, 1312}, {76, 1312}, {77, 1312}, {169, 1312},
            {170, 1312}, {171, 1312}, {172, 1312}, {173, 1312}, {187, 1312}, {197, 1312}, {198, 1466}, {320, 1312},
            {321, 1312}, {322, 1312}, {323, 1312}, {324, 1312}, {427, 1312}, {658, 1312}, {659, 1312}, {660, 1312},
            {661, 1312}, {662, 1312}, {679, 1312}, {680, 1312}, {681, 1312}, {682, 644}, {683, 1312}, {684, 645},
            {685, 1312}, {800, 1312}, {801, 1312}, {802, 1312}, {867, 1312}, {886, 1312}, {887, 1312}, {888, 1312},
            {1011, 1312}, {1031, 1312}, {1436, 1312}, {1437, 1312}, {1438, 1312}, {1439, 1312}, {1440, 1312}, {1441, 1312},
            {1442, 1312}, {1443, 1312}, {1444, 1312}, {1445, 1312}, {1446, 1312}, {1447, 1312}, {1448, 1312}, {1449, 1312},
            {1525, 1312}, {1548, 1312}, {1549, 1312}, {1550, 1312}, {1551, 1312}, {1608, 1312}, {1609, 1312}, {1610, 1312},
    };
    // SOUND_767_TO_765: entries=1611 ranges=19 overrides=72

    static final int[][] PARTICLE_767_TO_765_RANGES = {
            {0, 23, 1}, {26, 31, -1}, {33, 46, -2}, {48, 101, -3},
    };

    static final int[][] PARTICLE_767_TO_765_OVERRIDES = {
            {24, 99}, {25, 25}, {32, 0}, {47, 0}, {102, 100}, {103, 100}, {104, 0}, {105, 0},
            {106, 0}, {107, 0}, {108, 0},
    };
    // PARTICLE_767_TO_765: entries=109 ranges=4 overrides=11

    static final int[][] BLOCK_767_TO_765_RANGES = {
            {0, 1057, 0},
    };

    static final int[][] BLOCK_767_TO_765_OVERRIDES = {
            {1058, 1}, {1059, 1},
    };
    // BLOCK_767_TO_765: entries=1060 ranges=1 overrides=2

    static final int[][] MENU_767_TO_765_RANGES = {
            {0, 24, 0},
    };

    static final int[][] MENU_767_TO_765_OVERRIDES = {
    };
    // MENU_767_TO_765: entries=25 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_767_RANGES = {
            {0, 6, 0}, {7, 16, 2}, {18, 19, 5}, {20, 21, 6},
    };

    static final int[][] ATTRIBUTE_767_OVERRIDES = {
            {17, 21},
    };
    // ATTRIBUTE_767: 766 entries=22 ranges=4 overrides=1

    static final int[][] ATTRIBUTE_REVERSE_767_RANGES = {
            {0, 6, 0}, {9, 18, -2}, {23, 24, -5}, {26, 27, -6},
    };

    static final int[][] ATTRIBUTE_REVERSE_767_OVERRIDES = {
            {7, -1}, {8, -1}, {19, -1}, {20, -1}, {21, 17}, {22, -1}, {25, -1}, {28, -1},
            {29, -1}, {30, -1},
    };
    // ATTRIBUTE_REVERSE_767: 767 entries=31 ranges=4 overrides=10

    static final String[] ATTRIBUTE_766_NAMES = {
            "generic.armor", "generic.armor_toughness", "generic.attack_damage", "generic.attack_knockback",
            "generic.attack_speed", "player.block_break_speed", "player.block_interaction_range", "player.entity_interaction_range",
            "generic.fall_damage_multiplier", "generic.flying_speed", "generic.follow_range", "generic.gravity",
            "generic.jump_strength", "generic.knockback_resistance", "generic.luck", "generic.max_absorption",
            "generic.max_health", "generic.movement_speed", "generic.safe_fall_distance", "generic.scale",
            "zombie.spawn_reinforcements", "generic.step_height",
    };

    static final String[] ATTRIBUTE_767_NAMES = {
            "generic.armor", "generic.armor_toughness", "generic.attack_damage", "generic.attack_knockback",
            "generic.attack_speed", "player.block_break_speed", "player.block_interaction_range", "generic.burning_time",
            "generic.explosion_knockback_resistance", "player.entity_interaction_range", "generic.fall_damage_multiplier", "generic.flying_speed",
            "generic.follow_range", "generic.gravity", "generic.jump_strength", "generic.knockback_resistance",
            "generic.luck", "generic.max_absorption", "generic.max_health", "player.mining_efficiency",
            "generic.movement_efficiency", "generic.movement_speed", "generic.oxygen_bonus", "generic.safe_fall_distance",
            "generic.scale", "player.sneaking_speed", "zombie.spawn_reinforcements", "generic.step_height",
            "player.submerged_mining_speed", "player.sweeping_damage_ratio", "generic.water_movement_efficiency",
    };

    static final int[][] ENCHANTMENT_767_RANGES = {
            {28, 29, -8},
    };

    static final int[][] ENCHANTMENT_767_OVERRIDES = {
            {0, 27}, {1, 11}, {2, 9}, {3, 3}, {4, 26}, {5, 30}, {6, 0}, {7, 38},
            {8, 7}, {9, 14}, {10, 2}, {11, 35}, {12, 37}, {13, 32}, {14, 34}, {15, 1},
            {16, 17}, {17, 10}, {18, 18}, {19, 36}, {20, 8}, {21, 33}, {22, 39}, {23, 13},
            {24, 25}, {25, 28}, {26, 12}, {27, 16}, {30, 19}, {31, 15}, {32, 31}, {33, 5},
            {34, 23}, {35, 29}, {36, 24}, {37, 6}, {38, 4}, {39, 41}, {40, 22}, {41, 40},
    };
    // ENCHANTMENT_767: 766 entries=42 ranges=1 overrides=40

    static final int[][] ENCHANTMENT_REVERSE_767_RANGES = {
            {20, 21, 8},
    };

    static final int[][] ENCHANTMENT_REVERSE_767_OVERRIDES = {
            {0, 6}, {1, 15}, {2, 10}, {3, 3}, {4, 38}, {5, 33}, {6, 37}, {7, 8},
            {8, 20}, {9, 2}, {10, 17}, {11, 1}, {12, 26}, {13, 23}, {14, 9}, {15, 31},
            {16, 27}, {17, 16}, {18, 18}, {19, 30}, {22, 40}, {23, 34}, {24, 36}, {25, 24},
            {26, 4}, {27, 0}, {28, 25}, {29, 35}, {30, 5}, {31, 32}, {32, 13}, {33, 21},
            {34, 14}, {35, 11}, {36, 19}, {37, 12}, {38, 7}, {39, 22}, {40, 41}, {41, 39},
    };
    // ENCHANTMENT_REVERSE_767: 767 entries=42 ranges=1 overrides=40

}
