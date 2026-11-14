package io.detergentmc.protocol;

/**
 * 759（1.19）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>759 と 760（1.19.1／1.19.2）のレジストリは全カテゴリで同一
 * （ViaVersion mapping-1.19.json と minecraft-data pc/1.19／
 * pc/1.19.2 の名前リスト突き合わせで検証済み）のため、すべての表は
 * 恒等写像。差分数は全カテゴリ 0。唯一の既知の違いは blocks.json
 * の drops メタデータ 3 件（fern, attached_pumpkin_stem,
 * attached_melon_stem）で、ID には影響しない。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-759-tables.py}。
 * ダメージ種別は 759・760 どちらの codec にも存在しない
 * （1.19.4 で追加）。対応表は 762 の 42 件の恒等写像として作るが
 * ワイヤ上の呼び出し元は無い（760 と同じ扱い）。
 */
final class RegistryTables759 {

    private RegistryTables759() {
    }

    static final int[][] BLOCK_STATE_759_RANGES = {
            {0, 21447, 0}
    };

    static final int[][] BLOCK_STATE_759_OVERRIDES = {
    };

    static final int[][] BLOCK_STATE_759_TO_760_RANGES = {
            {0, 21447, 0}
    };

    static final int[][] BLOCK_STATE_759_TO_760_OVERRIDES = {
    };

    static final int[][] ITEM_759_RANGES = {
            {0, 1151, 0}
    };

    static final int[][] ITEM_759_OVERRIDES = {
    };

    static final int[][] ITEM_759_TO_760_RANGES = {
            {0, 1151, 0}
    };

    static final int[][] ITEM_759_TO_760_OVERRIDES = {
    };

    static final int[][] ENTITY_TYPE_759_RANGES = {
            {0, 117, 0}
    };

    static final int[][] ENTITY_TYPE_759_OVERRIDES = {
    };

    static final int[][] ENTITY_TYPE_759_TO_760_RANGES = {
            {0, 117, 0}
    };

    static final int[][] ENTITY_TYPE_759_TO_760_OVERRIDES = {
    };

    static final int[][] SOUND_759_RANGES = {
            {0, 1320, 0}
    };

    static final int[][] SOUND_759_OVERRIDES = {
    };

    static final int[][] SOUND_759_TO_760_RANGES = {
            {0, 1320, 0}
    };

    static final int[][] SOUND_759_TO_760_OVERRIDES = {
    };

    static final int[][] PARTICLE_759_RANGES = {
            {0, 92, 0}
    };

    static final int[][] PARTICLE_759_OVERRIDES = {
    };

    static final int[][] PARTICLE_759_TO_760_RANGES = {
            {0, 92, 0}
    };

    static final int[][] PARTICLE_759_TO_760_OVERRIDES = {
    };

    static final int[][] BLOCK_759_RANGES = {
            {0, 932, 0}
    };

    static final int[][] BLOCK_759_OVERRIDES = {
    };

    static final int[][] BLOCK_759_TO_760_RANGES = {
            {0, 932, 0}
    };

    static final int[][] BLOCK_759_TO_760_OVERRIDES = {
    };

    static final int[][] MENU_759_RANGES = {
            {0, 23, 0}
    };

    static final int[][] MENU_759_OVERRIDES = {
    };

    static final int[][] MENU_759_TO_760_RANGES = {
            {0, 23, 0}
    };

    static final int[][] MENU_759_TO_760_OVERRIDES = {
    };

    static final int[][] BLOCK_ENTITY_759_RANGES = {
            {0, 35, 0}
    };

    static final int[][] BLOCK_ENTITY_759_OVERRIDES = {
    };

    static final int[][] BLOCK_ENTITY_759_TO_760_RANGES = {
            {0, 35, 0}
    };

    static final int[][] BLOCK_ENTITY_759_TO_760_OVERRIDES = {
    };

    static final int[][] BIOME_759_RANGES = {
            {0, 62, 0}
    };

    static final int[][] BIOME_759_OVERRIDES = {
    };

    static final int[][] BIOME_759_TO_760_RANGES = {
            {0, 62, 0}
    };

    static final int[][] BIOME_759_TO_760_OVERRIDES = {
    };

    static final int[][] DAMAGE_TYPE_759_RANGES = {
            {0, 41, 0}
    };

    static final int[][] DAMAGE_TYPE_759_OVERRIDES = {
    };

    static final int[][] DAMAGE_TYPE_759_TO_760_RANGES = {
            {0, 41, 0}
    };

    static final int[][] DAMAGE_TYPE_759_TO_760_OVERRIDES = {
    };

    /** 760→759 の音の名前の代替（恒等写像のため空）。 */
    static final String[][] SOUND_NAME_FALLBACKS_760_TO_759 = {
    };

    /** 759→760 の音の名前の代替（恒等写像のため空）。 */
    static final String[][] SOUND_NAME_FALLBACKS_759_TO_760 = {
    };
}
