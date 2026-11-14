package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の760（1.19.1／1.19.2）⇔761（1.19.3）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.19.json／mapping-1.19.3.json と
 * minecraft-data（pc/1.19.2・pc/1.19.3 の blocks.json／items.json／
 * entities.json／biomes.json）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-760-tables.py）の
 * tables-760.json と同じ値。検証は tools/verify-760-tables.py を参照。
 *
 * <p>761→760 の差分：ブロック状態 23,232→21,448（−1,784。竹系・
 * hanging sign・chiseled_bookshelf・ラクダ・スポーンエッグ類の追加分が
 * 760 には無い）、ブロック 972→933（−39）、アイテム 1,186→1,152（−34）、
 * エンティティ 119→118（−1。camel の追加）、音 1,392→1,321（−71）、
 * パーティクル 93件同士で並び同一、エンチャント 39件同士で
 * 並び同一、エフェクト 33件同士で並び同一（1ベース）、
 * ブロックエンティティ 38→36（chiseled_bookshelf・hanging_sign の追加）、
 * メニュー 24件同士で並び同一、バイオーム 63件同士で同一名だが
 * 並びが違う（plains は 760=1・761=38）、ダメージ種別 42件同士で
 * 並び同一（761 の codec には damage_type が無いが対応表は恒等写像
 * として残す。ワイヤ上の呼び出し元は無い）、絵画 30件同士で並び同一。
 *
 * <p>既知の注意点：バイオームの代替先は plains（760=1、761=38）。
 * 1.19.1／1.19.2 にはレジストリの追加が無く、mapping-1.19.json（759）が
 * 760 のレジストリ源として有効（minecraft-data にも pc/1.19.1 は無い）。
 */
class RegistryRemapper760Test {

    // ---- 761 → 760 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState761To760(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState761To760(1)); // stone
        // 761にしかないブロック状態は近い既存ブロックの既定状態に代替
        // （gen-760-tables.py の BLOCK_FALLBACK_761_TO_760）。
        assertEquals(118, RegistryRemapper.mapBlockState761To760(146)); // bamboo_block→oak_log
        assertEquals(15, RegistryRemapper.mapBlockState761To760(23)); // bamboo_mosaic→oak_planks
        assertEquals(3637, RegistryRemapper.mapBlockState761To760(4775)); // oak_hanging_sign→oak_sign
        assertEquals(1686, RegistryRemapper.mapBlockState761To760(2108)); // chiseled_bookshelf→bookshelf
        // note_block の新楽器（piglin）は harp に落とす（同じ note・powered）。
        assertEquals(527, RegistryRemapper.mapBlockState761To760(1585)); // piglin note=24→harp note=24
        assertEquals(21447, RegistryRemapper.mapBlockState761To760(23231)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState761To760(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState760To761(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState760To761(1)); // stone
        // 760 の状態はすべて 761 に同文字列で存在する（恒等）。
        assertEquals(120, RegistryRemapper.mapBlockState760To761(118)); // oak_log
        assertEquals(23231, RegistryRemapper.mapBlockState760To761(21447)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState760To761(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem761To760(0)); // air
        assertEquals(1, RegistryRemapper.mapItem761To760(1)); // stone
        // 761にしかないアイテムは近い既存アイテムに代替
        // （gen-760-tables.py の ITEM_FALLBACK_761_TO_760）。
        assertEquals(937, RegistryRemapper.mapItem761To760(946)); // camel_spawn_egg→horse_spawn_egg
        assertEquals(334, RegistryRemapper.mapItem761To760(956)); // ender_dragon_spawn_egg→dragon_egg
        assertEquals(728, RegistryRemapper.mapItem761To760(969)); // iron_golem_spawn_egg→iron_ingot
        assertEquals(817, RegistryRemapper.mapItem761To760(993)); // snow_golem_spawn_egg→snowball
        assertEquals(1002, RegistryRemapper.mapItem761To760(1008)); // wither_spawn_egg→nether_star
        assertEquals(999, RegistryRemapper.mapItem761To760(1035)); // piglin_head→zombie_head
        assertEquals(698, RegistryRemapper.mapItem761To760(727)); // bamboo_raft→oak_boat
        assertEquals(699, RegistryRemapper.mapItem761To760(728)); // bamboo_chest_raft→oak_chest_boat
        assertEquals(1151, RegistryRemapper.mapItem761To760(1185)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem761To760(99999)); // 範囲外は石
    }

    @Test
    void itemsReverse() {
        assertEquals(0, RegistryRemapper.mapItem760To761(0)); // air
        assertEquals(1, RegistryRemapper.mapItem760To761(1)); // stone
        // 760 のアイテムはすべて 761 に同名で存在する（恒等）。
        // horse_spawn_egg は 760=937・761=967（camel_spawn_egg が 946 に
        // 入ったため番号がずれる。camel→horse の代替で潰れた分）。
        assertEquals(967, RegistryRemapper.mapItem760To761(937)); // horse_spawn_egg
        assertEquals(1185, RegistryRemapper.mapItem760To761(1151)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem760To761(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        // camel は 761 にしかないので horse に代替。
        assertEquals(40, RegistryRemapper.mapEntityType761To760(11)); // camel→horse
        assertEquals(67, RegistryRemapper.mapEntityType761To760(68)); // pig
        assertEquals(68, RegistryRemapper.mapEntityType760To761(67)); // pig
        assertEquals(41, RegistryRemapper.mapEntityType760To761(40)); // horse
        assertEquals(67, RegistryRemapper.mapEntityType761To760(99999)); // 範囲外はブタ
        assertEquals(68, RegistryRemapper.mapEntityType760To761(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(1278, RegistryRemapper.mapSound761To760(97)); // bamboo_wood.break→wood.break
        assertEquals(532, RegistryRemapper.mapSound761To760(192)); // camel.ambient→horse.ambient
        assertEquals(538, RegistryRemapper.mapSound761To760(193)); // camel.dash→horse.gallop
        assertEquals(1112, RegistryRemapper.mapSound761To760(1183)); // stone.break
        assertEquals(1349, RegistryRemapper.mapSound760To761(1278)); // wood.break
        assertEquals("block.wood.break",
                RegistryRemapper.mapSoundName761To760("block.bamboo_wood.break"));
        assertEquals("entity.horse.ambient",
                RegistryRemapper.mapSoundName761To760("entity.camel.ambient"));
        assertEquals("entity.horse.gallop",
                RegistryRemapper.mapSoundName761To760("entity.camel.dash"));
        assertEquals("block.wood.break",
                RegistryRemapper.mapSoundName760To761("block.wood.break"));
    }

    @Test
    void particlesAndMenusAreIdentity() {
        assertEquals(30, RegistryRemapper.mapParticle761To760(30)); // sculk_charge
        assertEquals(39, RegistryRemapper.mapParticle761To760(39)); // item
        assertEquals(40, RegistryRemapper.mapParticle761To760(40)); // vibration
        assertEquals(92, RegistryRemapper.mapParticle761To760(92)); // shriek
        assertEquals(30, RegistryRemapper.mapParticle760To761(30));
        assertEquals(11, RegistryRemapper.mapMenu761To760(11)); // crafting
        assertEquals(11, RegistryRemapper.mapMenu760To761(11));
    }

    @Test
    void blockEntities() {
        // 761 にしかないブロックエンティティは落とす（-1）。
        assertEquals(-1, RegistryRemapper.mapBlockEntityType761To760(37)); // chiseled_bookshelf
        assertEquals(-1, RegistryRemapper.mapBlockEntityType761To760(8)); // hanging_sign
        assertEquals(0, RegistryRemapper.mapBlockEntityType761To760(0)); // furnace
        assertEquals(0, RegistryRemapper.mapBlockEntityType760To761(0)); // furnace
    }

    @Test
    void biomes() {
        // plains は 760=1・761=38（並びが違う）。
        assertEquals(1, RegistryRemapper.mapBiome761To760(38)); // plains
        assertEquals(38, RegistryRemapper.mapBiome760To761(1)); // plains
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType761To760(0));
        assertEquals(41, RegistryRemapper.mapDamageType761To760(41));
        assertEquals(0, RegistryRemapper.mapDamageType760To761(0));
        assertEquals(41, RegistryRemapper.mapDamageType760To761(41));
    }

    @Test
    void blocks() {
        assertEquals(1, RegistryRemapper.mapBlock761To760(1)); // stone
        assertEquals(1, RegistryRemapper.mapBlock760To761(1)); // stone
    }

    @Test
    void roundTripFor760Elements() {
        // 760 にある要素は 760→761→760 で自分に戻る。
        assertEquals(118, RegistryRemapper.mapBlockState761To760(
                RegistryRemapper.mapBlockState760To761(118)));
        assertEquals(937, RegistryRemapper.mapItem761To760(
                RegistryRemapper.mapItem760To761(937)));
        assertEquals(67, RegistryRemapper.mapEntityType761To760(
                RegistryRemapper.mapEntityType760To761(67)));
        assertEquals(1, RegistryRemapper.mapBiome761To760(
                RegistryRemapper.mapBiome760To761(1)));
        // 761 にだけある要素は 761→760→761 で自分に戻らない（代替先へ潰れる）。
        assertEquals(967, RegistryRemapper.mapItem760To761(
                RegistryRemapper.mapItem761To760(946))); // camel_spawn_egg→horse_spawn_egg
        assertEquals(41, RegistryRemapper.mapEntityType760To761(
                RegistryRemapper.mapEntityType761To760(11))); // camel→horse
    }
}
