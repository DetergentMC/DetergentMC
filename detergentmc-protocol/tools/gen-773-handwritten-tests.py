#!/usr/bin/env python3
"""772の手書きテスト（PlayTranslator／ChunkTranslator／ProxySession）を
773の手書きテストへ写す。機械シフト＋ファイルごとの FIXES。

機械シフトは gen-773-tests.py と同じ（773→774、772→773、771→772、
770→771、1.21.7／1.21.8→1.21.9／1.21.10、1.21.7→1.21.9、1.21.6→1.21.7、
1_21_7→1_21_9、1_21_6→1_21_7、1217→1219、1216→1217）。

772→773で変わるのは番号の付け替えだけ（パケット形式は同じ）。ただし
ジュークボックスの歌・絵画・バイオーム・エンチャント・属性・ダメージ種別は
772・773とも同一のため、771→772の「挿入／削除」テストは773では「同一」
テストになる。エンティティ・パーティクル・ブロックエンティティの
Registry Data は並べ替えず素通しする（既存の全ペアと同じ設計。6レジストリ
（エンチャント・絵画・バナー・トリム・ジュークボックス・ダメージ種別）だけ
並べ替える）。この点はテストにも明記する。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"
DATA = ROOT / "build" / "remap-data"


def shift(text):
    # 版番号の置換は「数字列の一部でない」ときだけ行う。素の replace だと
    # ブロック状態ID 27730 の中の "773" を "774" にしてしまう（2026-11-12に
    # ChunkTranslator773Test で発見）。識別子（translateX771To772 等）の
    # 中の版番号は前後が数字でないため置換される。
    for a, b in (("773", "774"), ("772", "773"), ("771", "772"), ("770", "771")):
        text = re.sub(rf"(?<!\d){a}(?!\d)", b, text)
    text = text.replace("1.21.7／1.21.8", "1.21.9／1.21.10")
    text = text.replace("1.21.7", "1.21.9")
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")
    text = text.replace("1_21_7", "1_21_9")
    text = text.replace("1_21_6", "1_21_7")
    text = re.sub(r"(?<!\d)1217(?!\d)", "1219", text)
    text = re.sub(r"(?<!\d)1216(?!\d)", "1217", text)
    return text


def apply_fixes(dst_name, out, fixes):
    for label, old, new in fixes:
        if label.startswith("ALL:"):
            n = out.count(old)
            if n < 1:
                print(f"NG: {dst_name} {label}: {old[:60]!r} が 0 回",
                      file=sys.stderr)
                sys.exit(1)
            out = out.replace(old, new)
            print(f"OK: {dst_name} {label} ({n} 回)")
            continue
        n = out.count(old)
        if n != 1:
            print(f"NG: {dst_name} {label}: {old[:60]!r} が {n} 回（1回のはず）",
                  file=sys.stderr)
            sys.exit(1)
        out = out.replace(old, new)
        print(f"OK: {dst_name} {label}")
    return out


def write_shifted(src_name, dst_name, fixes, append=""):
    src = (TEST / src_name).read_text(encoding="utf-8")
    out = shift(src)
    out = apply_fixes(dst_name, out, fixes)
    if append:
        assert out.endswith("}\n"), out[-40:]
        out = out[:-2] + append + "}\n"
    (TEST / dst_name).write_text(out, encoding="utf-8")


def java_list(names):
    """名前の列を Java の List.of(...) 1行にする（8件ずつ改行）。"""
    parts = []
    for i in range(0, len(names), 8):
        parts.append(", ".join(f'"{n}"' for n in names[i:i + 8]))
    return ",\n                ".join(parts)


# ------------------------------------------------------------------
# PlayTranslator773Test
# ------------------------------------------------------------------
paintings767 = None
_src = (ROOT / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
        / "PlayTranslator.java").read_text(encoding="utf-8")
_m = re.search(r"PAINTING_767_NAMES = \{([^}]*)\}", _src, re.S)
paintings767 = re.findall(r'"minecraft:([^"]+)"', _m.group(1))
assert len(paintings767) == 50, len(paintings767)
assert paintings767[23] == "kebab" and paintings767[32] == "pointer"
# 772・773の絵画は767の50件に dennis を14番へ挿入した51件（実バニラの
# Registry Data キャプチャで確認。mapPaintingVariant771To772 は
# 「14より後は+1」）。767の kebab=23 は772・773では 24 になる。
paintings773 = paintings767[:14] + ["dennis"] + paintings767[14:]
assert len(paintings773) == 51, len(paintings773)
assert paintings773[14] == "dennis", paintings773[14]
assert paintings773[24] == "kebab"
assert paintings773[33] == "pointer"
assert paintings773[34] == "pond"
assert paintings773[44] == "unpacked"
assert paintings773[50] == "wither"
vv772 = json.loads((DATA / "vv-1.21.7.json").read_text(encoding="utf-8"))
entities772 = vv772["entities"]
particles772 = vv772["particles"]
blockentities772 = vv772["blockentities"]
assert len(entities772) == 151 and entities772[95] == "pig" and entities772[56] == "happy_ghast"
assert len(particles772) == 114 and particles772[34] == "pale_oak_leaves"
assert len(blockentities772) == 47 and blockentities772[45] == "test_block"
assert "copper_golem" not in entities772 and "copper_fire_flame" not in particles772
assert "shelf" not in blockentities772

PLAY_APPEND = f"""
    // ------------------------------------------------------------------
    // 773の Registry Data（絵画は同一、エンティティ等は素通し）
    // ------------------------------------------------------------------

    @Test
    void registryData772To773PaintingsIdentity() {{
        // 絵画は772・773とも51件・同じ並び（実バニラ1.21.8／1.21.9の
        // Registry Data キャプチャで確認：dennis=14、kebab=24、pointer=33、
        // pond=34、unpacked=44、wither=50。767の kebab=23 は dennis の
        // 挿入で+1ずれる）。
        List<String> names = List.of({java_list(paintings773)});
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
    }}

    @Test
    void registryData773To772PaintingsIdentity() {{
        List<String> names = List.of({java_list(paintings773)});
        byte[] packet = registryPacket("minecraft:painting_variant", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To772(packet));
        assertEquals(51, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:dennis", out.names().get(14));
    }}

    @Test
    void registryData772To773EntitiesPassThrough() {{
        // エンティティの Registry Data は並べ替えず素通しする（既存の全ペアと
        // 同じ設計。並べ替えるのはエンチャント・絵画・バナー・トリム・
        // ジュークボックス・ダメージ種別の6レジストリだけ）。
        // 772→773では銅ゴーレム（27）・マネキン（82）が増えるため、素通しの
        // ままでは773クライアントのエンティティ登録は772順（151件、pig=95）
        // のままになる。Spawn Entity の種別ID自体は773へ翻訳される（pig=97）。
        // 実機クライアントでの扱いは未確認。残る制限として報告する。
        List<String> names = List.of({java_list(entities772)});
        byte[] packet = registryPacket("minecraft:entity_type", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(151, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:pig", out.names().get(95));
        assertEquals("minecraft:happy_ghast", out.names().get(56));
    }}

    @Test
    void registryData772To773ParticlesPassThrough() {{
        // パーティクルも素通しする（copper_fire_flame（773=5）は増えない）。
        List<String> names = List.of({java_list(particles772)});
        byte[] packet = registryPacket("minecraft:particle_type", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(114, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:pale_oak_leaves", out.names().get(34));
    }}

    @Test
    void registryData772To773BlockEntitiesPassThrough() {{
        // ブロックエンティティも素通しする（shelf（773=40）・
        // copper_golem_statue（773=48）は増えない）。
        List<String> names = List.of({java_list(blockentities772)});
        byte[] packet = registryPacket("minecraft:block_entity_type", names);
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData772To773(packet));
        assertEquals(47, out.names().size());
        assertEquals(shortNames(names), shortNames(out.names()));
        assertEquals("minecraft:test_block", out.names().get(45));
    }}
"""

write_shifted("PlayTranslator772Test.java", "PlayTranslator773Test.java", [
    ("javadoc jukebox identity", """ * Registry Data は jukebox_song が lava_chicken の挿入で 9番以降が+1ずれる。
 * minecraft:dialog は772・773ともにあるため、772⇔773では落とさず素通しする
 * （772段階で771向けに落としていたのとは違い、773では772向けに落とさない）。""",
     """ * Registry Data は jukebox_song・絵画・バイオーム・エンチャント・属性・
 * ダメージ種別が772・773とも同一のため素通しする（771→772で lava_chicken
 * を挿入したのとは違い、772→773では新しい曲は無い）。
 * minecraft:dialog は772・773ともにあるため、772⇔773では落とさず素通しする
 * （771段階で770向けに落としていたのとは違う）。
 * エンティティ・パーティクル・ブロックエンティティの Registry Data は
 * 並べ替えず素通しする（既存の全ペアと同じ設計）。772→773では銅ゴーレム・
 * マネキン・copper_fire_flame・棚・銅ゴーレム像が増えるが、Registry Data
 * には反映されない。Spawn Entity 等の種別ID自体は翻訳される。"""),
    ("jukebox helpers 772 is 21 songs", '''    /** 772のジュークボックスの歌20件（実バニラ1.21.7の並び）。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "mall", "mellohi", "otherside", "pigstep",
                "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }''',
     '''    /** 772のジュークボックスの歌21件（実バニラ1.21.7／1.21.8の並び。lava_chicken は9番）。773も同じ21件。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }'''),
    ("jukebox forward identity", """    @Test
    void registryData772To773JukeboxSongInsertsLavaChicken() {
        // 772→773：lava_chicken を9番に挿入し、mall 以降が+1ずれる。
        // 挿入された lava_chicken は名前だけのエントリ（hasData=false）。
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
        assertFalse(out.hasData().get(9));
    }""",
     """    @Test
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
    }"""),
    ("jukebox reverse identity", """    @Test
    void registryData773To772JukeboxSongDropsLavaChicken() {
        // 773→772：lava_chicken を落とし、mall 以降が-1ずれる。
        byte[] packet = registryPacket("minecraft:jukebox_song", jukeboxSongs773());
        RegistryPacket out = readRegistryPacket(
                PlayTranslator.translateRegistryData773To772(packet));
        assertEquals(20, out.names().size());
        assertEquals(shortNames(jukeboxSongs772()), shortNames(out.names()));
        assertEquals("mall", out.names().get(9));
        assertEquals("tears", out.names().get(17));
        assertEquals("wait", out.names().get(18));
        assertEquals("ward", out.names().get(19));
    }""",
     """    @Test
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
    }"""),
    ("dialog reverse comment", "        // 逆向きも素通しする（772段階で771向けに落としていたのとは違う）。",
     "        // 逆向きも素通しする（771段階で770向けに落としていたのとは違う）。"),
], append=PLAY_APPEND)

print("PlayTranslator773Test を生成した")


# ------------------------------------------------------------------
# ChunkTranslator773Test
# ------------------------------------------------------------------
write_shifted("ChunkTranslator772Test.java", "ChunkTranslator773Test.java", [
    ("class javadoc", """ * <p>772→773の変更（minecraft-data の表に準拠。実バニラ1.21.9／1.21.10の
 * Registry Data キャプチャで絵画・ジュークボックスの位置を確認する）：
 * heightmap は772・773とも直接の配列（NBT ではない）、パレット付きコンテナの
 * Data Array に長さ varint は無い、データコンポーネント種別は96件のまま・
 * 同じ番号、エンティティメタデータの種別番号は同一、パーティクルは同一、
 * 属性は同一、ブロック状態・エンティティ・バイオーム・エンチャント・
 * メニュー・ダメージ種別は同一。Window Click は HashedSlot のため変換できず
 * 落とす（ProxySession 側で落とす）。
 * 変わるのは番号の付け替えだけ：アイテム（music_disc_lava_chicken を1256に挿入）、
 * 音（music_disc.lava_chicken を939に挿入、773→772では block.stone.break（1441）で代替）、
 * 絵画（dennis を14に挿入、773→772ではメタデータの項目ごと落とし成分では kebab で代用）、
 * ジュークボックスの歌（lava_chicken を9に挿入、773→772では "11"（0）で代用）。""",
     """ * <p>772→773の変更（minecraft-data・ViaVersion mapping-1.21.9 の表に準拠。
 * 実バニラ1.21.9／1.21.10の Registry Data キャプチャで絵画・ジュークボックスの
 * 位置を確認する）：
 * heightmap は772・773とも直接の配列（NBT ではない）、パレット付きコンテナの
 * Data Array に長さ varint は無い、データコンポーネント種別は96件のまま・
 * 同じ番号、エンティティメタデータの種別番号は同一、バイオームは65件・同じ並び、
 * エンチャントは42件・同じ並び、属性は35件・同じ並び、メニューは25件・同じ並び、
 * ダメージ種別は49件・同じ並び、ジュークボックスの歌は21件・同じ並び、
 * 絵画は51件・同じ並び。Window Click は HashedSlot のため変換できず
 * 落とす（ProxySession 側で落とす）。
 * 変わるのは番号の付け替えだけ：ブロック状態（27,946→29,671。棚・銅の松明・
 * 銅の棒・銅の鎖・銅のランタン・銅のチェスト・銅ゴーレム像・避雷針の露出等）、
 * アイテム（1,416→1,488。棚12件を305番に挿入、chain→iron_chain の改名で
 * 378→399へ移動。772→773では bookshelf 305→317）、エンティティ
 * （151→153。copper_golem を27に、mannequin を82に挿入。pig 95→97、
 * happy_ghast 56→57）、音（1,728→1,771。銅のチェストの音を356番に挿入等。
 * 773にしかない音は block.stone.break（772=1442）で代替）、パーティクル
 * （114→115。copper_fire_flame を5番に挿入）、ブロックエンティティ
 * （47→49。shelf を40番に挿入、copper_golem_statue を48番に追加。
 * 773にしかない種別はパケットごと落とす）、絵画は同一のため
 * メタデータも素通しする。"""),
    ("chunkData forward", """    @Test
    void chunkData772To773BlockStatesIdentity() {
        // 772→773：ブロック状態は変わらない（trial_spawner 27730・vault 27742 のまま）。
        byte[] translated = ChunkTranslator.translateChunkData772To773(
                chunkDataPayload(3, -2, new int[]{0, 1, 27730, 27742}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]); // WORLD_SURFACE
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]); // MOTION_BLOCKING
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27730, 27742}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette773(sections));
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
    }""",
     """    @Test
    void chunkData772To773BlockStatesShift() {
        // 772→773：ブロック状態は付ける（trial_spawner 27730→29455・vault 27742→29467）。
        // バイオーム（38・39）は772・773とも同一。
        byte[] translated = ChunkTranslator.translateChunkData772To773(
                chunkDataPayload(3, -2, new int[]{0, 1, 27730, 27742}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]); // WORLD_SURFACE
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]); // MOTION_BLOCKING
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 29455, 29467}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette773(sections));
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
    }"""),
    ("chunkData reverse", """    @Test
    void chunkData773To772BlockStatesIdentity() {
        byte[] translated = ChunkTranslator.translateChunkData773To772(
                chunkDataPayload(0, 0, new int[]{0, 1, 27730, 27742}));
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]);
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27730, 27742}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette773(sections));
        assertEquals(0, reader.readVarInt());
    }""",
     """    @Test
    void chunkData773To772BlockStatesShift() {
        // 逆向き：trial_spawner 29455→27730・vault 29467→27742。
        byte[] translated = ChunkTranslator.translateChunkData773To772(
                chunkDataPayload(0, 0, new int[]{0, 1, 29455, 29467}));
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        long[][] heightmaps = readHeightmapArray(reader);
        assertArrayEquals(new long[]{44L, 55L}, heightmaps[0]);
        assertArrayEquals(new long[]{11L, 22L}, heightmaps[1]);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27730, 27742}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette773(sections));
        assertEquals(0, reader.readVarInt());
    }"""),
    ("blockChange forward", """    @Test
    void blockChange772To773Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27730); // trial_spawner（772・773とも）
        byte[] out = ChunkTranslator.translateBlockChange772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27730, reader.readVarInt());
    }""",
     """    @Test
    void blockChange772To773TrialSpawnerShifts() {
        // trial_spawner：772の 27730 → 773の 29455。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27730); // trial_spawner（772）
        byte[] out = ChunkTranslator.translateBlockChange772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(29455, reader.readVarInt());
    }"""),
    ("blockChange reverse", """    @Test
    void blockChange773To772Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27730); // trial_spawner（773）
        byte[] out = ChunkTranslator.translateBlockChange773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27730, reader.readVarInt());
    }""",
     """    @Test
    void blockChange773To772TrialSpawnerShiftsBack() {
        // 逆向き：trial_spawner 29455 → 27730。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(29455); // trial_spawner（773）
        byte[] out = ChunkTranslator.translateBlockChange773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27730, reader.readVarInt());
    }"""),
    ("blockEntityData forward", """    @Test
    void blockEntityData772To773Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(45); // test_block
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(45, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }""",
     """    @Test
    void blockEntityData772To773TestBlockShifts() {
        // test_block：772の 45 → 773の 46（shelf を40番に挿入したため+1）。
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(45); // test_block（772）
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(46, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }"""),
    ("blockEntityData reverse", """    @Test
    void blockEntityData773To772Identity() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(45); // test_block
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(45, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }""",
     """    @Test
    void blockEntityData773To772TestBlockShiftsBack() {
        // 逆向き：test_block 46 → 45。
        Writer writer = new Writer();
        writer.writePositionRaw(0x100L);
        writer.writeVarInt(46); // test_block（773）
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x100L, reader.readPositionRaw());
        assertEquals(45, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    @Test
    void blockEntityData773To772ShelfDropped() {
        // shelf（773の 40）・copper_golem_statue（773の 48）は772に無いため
        // パケットごと落とす（null）。
        Writer shelf = new Writer();
        shelf.writePositionRaw(0x100L);
        shelf.writeVarInt(40); // shelf（773）
        shelf.writeByte(0); // NBT 無し
        assertNull(ChunkTranslator.translateBlockEntityData773To772(shelf.bytes()));
        Writer statue = new Writer();
        statue.writePositionRaw(0x100L);
        statue.writeVarInt(48); // copper_golem_statue（773）
        statue.writeByte(0); // NBT 無し
        assertNull(ChunkTranslator.translateBlockEntityData773To772(statue.bytes()));
    }"""),
    ("slot forward bookshelf", """    @Test
    void slot772To773ItemShiftsAtLavaChicken() {
        // music_disc_lava_chicken（773=1256）の挿入で1256以降が+1ずれる。
        byte[] out = ChunkTranslator.translateSetSlot772To773(setSlotPacket(1255));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1255, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(1256));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1257, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(1414));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1415, readSlotHead(reader3)[1]);
    }""",
     """    @Test
    void slot772To773ItemShiftsAtShelves() {
        // 棚12件（773=305〜316）の挿入で bookshelf 305→317 に+12ずれる。
        // 354→367（+13）、chain 378→iron_chain 399（改名で移動）も確かめる。
        byte[] out = ChunkTranslator.translateSetSlot772To773(setSlotPacket(304));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(304, readSlotHead(reader)[1]); // bricks（挿入前）

        byte[] out2 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(305));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(317, readSlotHead(reader2)[1]); // bookshelf

        byte[] out3 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(354));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(367, readSlotHead(reader3)[1]);

        byte[] out4 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(378));
        Reader reader4 = new Reader(out4);
        reader4.readVarInt();
        reader4.readVarInt();
        reader4.readShort();
        assertEquals(399, readSlotHead(reader4)[1]); // chain → iron_chain

        byte[] out5 = ChunkTranslator.translateSetSlot772To773(setSlotPacket(1415));
        Reader reader5 = new Reader(out5);
        reader5.readVarInt();
        reader5.readVarInt();
        reader5.readShort();
        assertEquals(1487, readSlotHead(reader5)[1]); // 末尾（+72）
    }"""),
    ("slot reverse shelf", """    @Test
    void slot773To772LavaChickenDiscFallsBackToStone() {
        // music_disc_lava_chicken（773=1256）は772に無いため石（1）に落とす。
        byte[] out = ChunkTranslator.translateSetSlot773To772(setSlotPacket(1256));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(1257));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1256, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(1415));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1414, readSlotHead(reader3)[1]);
    }""",
     """    @Test
    void slot773To772ShelfFallsBackToStone() {
        // acacia_shelf（773=305）は772に無いため石（1）に落とす。
        // bookshelf 317→305、iron_chain 399→chain 378、末尾 1487→1415。
        byte[] out = ChunkTranslator.translateSetSlot773To772(setSlotPacket(305));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(317));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(305, readSlotHead(reader2)[1]); // bookshelf

        byte[] out3 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(399));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(378, readSlotHead(reader3)[1]); // iron_chain → chain

        byte[] out4 = ChunkTranslator.translateSetSlot773To772(setSlotPacket(1487));
        Reader reader4 = new Reader(out4);
        reader4.readVarInt();
        reader4.readVarInt();
        reader4.readShort();
        assertEquals(1415, readSlotHead(reader4)[1]); // 末尾
    }"""),
    ("jukebox playable forward identity", """    @Test
    void slot772To773JukeboxPlayableShiftsAtLavaChicken() {
        // jukebox_playable（種別 55）。参照の曲ID 9（mall）が773では 10 になる。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(10); // holder：曲ID 9（mall）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(11, reader.readVarInt()); // holder：曲ID 10（mall）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }""",
     """    @Test
    void slot772To773JukeboxPlayableIdentity() {
        // jukebox_playable（種別 55）。曲は772・773とも同じ並びのため素通し。
        // mall=10（holder 11）、lava_chicken=9（holder 10）のまま。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(11); // holder：曲ID 10（mall）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(11, reader.readVarInt()); // holder：曲ID 10（mall）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }"""),
    ("jukebox playable reverse identity", """    @Test
    void slot773To772JukeboxPlayableLavaChickenBecomesEleven() {
        // lava_chicken（773の 9）は772に無いため "11"（0）で代用する。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(10); // holder：曲ID 9（lava_chicken）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(1, reader.readVarInt()); // holder：曲ID 0（"11"）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }""",
     """    @Test
    void slot773To772JukeboxPlayableIdentity() {
        // 逆向きも素通しする（lava_chicken=9 は772にもある）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(55); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(10); // holder：曲ID 9（lava_chicken）+1
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(55, reader.readVarInt()); // jukebox_playable
        assertTrue(reader.readBoolean()); // hasHolder
        assertEquals(10, reader.readVarInt()); // holder：曲ID 9（lava_chicken）+1
        assertTrue(reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }"""),
    ("break sound forward", """    @Test
    void slot772To773BreakSoundShiftsAtLavaChicken() {
        // break_sound（種別 71）の音 Holder：参照 939（772の music_disc.ward 相当）
        // → 940（773）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(940); // holder：音ID 939 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(941, reader.readVarInt()); // holder：音ID 940 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }""",
     """    @Test
    void slot772To773BreakSoundShiftsAtLavaChicken() {
        // break_sound（種別 71）の音 Holder：参照 939（772の music_disc.lava_chicken）
        // → 970（773。銅のチェスト等の音の挿入で+31）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(940); // holder：音ID 939 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(971, reader.readVarInt()); // holder：音ID 970 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }"""),
    ("break sound reverse", """    @Test
    void slot773To772BreakSoundLavaChickenBecomesStoneBreak() {
        // 逆向き：773の music_disc.lava_chicken（939）は772では
        // block.stone.break（1441）で代替する。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(940); // holder：音ID 939 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(1442, reader.readVarInt()); // holder：音ID 1441 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }""",
     """    @Test
    void slot773To772BreakSoundCopperChestBecomesStoneBreak() {
        // 逆向き：773にしかない block.copper_chest.close（356）は772では
        // block.stone.break（1442）で代替する。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(71); // break_sound
        writer.writeVarInt(357); // holder：音ID 356 +1
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(71, reader.readVarInt()); // break_sound
        assertEquals(1443, reader.readVarInt()); // holder：音ID 1442 +1
        assertEquals(0, reader.readVarInt()); // removed 0
    }"""),
    ("setCreativeSlot forward", """    @Test
    void setCreativeSlot772To773ItemShifts() {
        // UntrustedSlot のままアイテムIDだけ付け替える（1256→1257）。
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(1256); // 772のアイテム
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1257, reader.readVarInt()); // 773のアイテム
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }""",
     """    @Test
    void setCreativeSlot772To773ItemShifts() {
        // UntrustedSlot のままアイテムIDだけ付け替える（bookshelf 305→317）。
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(305); // 772の bookshelf
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot772To773(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(317, reader.readVarInt()); // 773の bookshelf
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }"""),
    ("setCreativeSlot reverse", """    @Test
    void setCreativeSlot773To772LavaChickenBecomesStone() {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(1256); // 773の music_disc_lava_chicken
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // 石
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }""",
     """    @Test
    void setCreativeSlot773To772ShelfBecomesStone() {
        Writer writer = new Writer();
        writer.writeShort(5); // slotId
        writer.writeVarInt(1); // count
        writer.writeVarInt(305); // 773の acacia_shelf
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetCreativeSlot773To772(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // 石
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }"""),
    ("particles pale_oak_leaves", """    @Test
    void worldParticles772To773PaleOakLeavesIdentity() {
        // pale_oak_leaves（34）は772・773とも同じ番号。データ（i32）はそのまま。
        Writer data = new Writer();
        data.writeInt(12345);
        byte[] out = ChunkTranslator.translateWorldParticles772To773(
                worldParticlesPacket(false, 34, data));
        Reader reader = new Reader(out);
        assertEquals(34, readWorldParticlesId(reader));
        assertEquals(12345, reader.readInt());
    }""",
     """    @Test
    void worldParticles772To773PaleOakLeavesShifts() {
        // pale_oak_leaves：772の 34 → 773の 35（copper_fire_flame を5番に挿入）。
        // pale_oak_leaves にデータは無い（772テストで書いていた i32 は
        // 翻訳器が消費せず、requireEnd の失敗でペイロードが素通しされる
        // フォールバックで通っていただけ。773では空データで確かめる）。
        byte[] out = ChunkTranslator.translateWorldParticles772To773(
                worldParticlesPacket(false, 34, new Writer()));
        Reader reader = new Reader(out);
        assertEquals(35, readWorldParticlesId(reader));
        assertEquals(0, reader.remaining());
    }"""),
    ("particles trail reverse", """    @Test
    void worldParticles773To772TrailIdentity() {
        // trail 48。データ（3×f64＋i32＋varint）はそのまま。
        Writer data = new Writer();
        data.writeDouble(1.0);
        data.writeDouble(2.0);
        data.writeDouble(3.0);
        data.writeInt(0x11223344);
        data.writeVarInt(7);
        byte[] out = ChunkTranslator.translateWorldParticles773To772(
                worldParticlesPacket(false, 48, data));
        Reader reader = new Reader(out);
        assertEquals(48, readWorldParticlesId(reader));""",
     """    @Test
    void worldParticles773To772TrailShiftsBack() {
        // trail：773の 49 → 772の 48。データ（3×f64＋i32＋varint）はそのまま。
        Writer data = new Writer();
        data.writeDouble(1.0);
        data.writeDouble(2.0);
        data.writeDouble(3.0);
        data.writeInt(0x11223344);
        data.writeVarInt(7);
        byte[] out = ChunkTranslator.translateWorldParticles773To772(
                worldParticlesPacket(false, 49, data));
        Reader reader = new Reader(out);
        assertEquals(48, readWorldParticlesId(reader));"""),
    ("particles block_crumble", """    @Test
    void worldParticles772To773BlockCrumbleIdentity() {
        // block_crumble 112。データはブロック状態（trial_spawner 27730 のまま）。
        Writer data = new Writer();
        data.writeVarInt(27730); // trial_spawner（772・773とも）
        byte[] out = ChunkTranslator.translateWorldParticles772To773(
                worldParticlesPacket(false, 112, data));
        Reader reader = new Reader(out);
        assertEquals(112, readWorldParticlesId(reader));
        assertEquals(27730, reader.readVarInt());
    }""",
     """    @Test
    void worldParticles772To773BlockCrumbleShifts() {
        // block_crumble：772の 112 → 773の 113。データのブロック状態も
        // trial_spawner 27730→29455 に付ける。
        Writer data = new Writer();
        data.writeVarInt(27730); // trial_spawner（772）
        byte[] out = ChunkTranslator.translateWorldParticles772To773(
                worldParticlesPacket(false, 112, data));
        Reader reader = new Reader(out);
        assertEquals(113, readWorldParticlesId(reader));
        assertEquals(29455, reader.readVarInt());
    }"""),
    ("entityMetadata painting forward", """    @Test
    void entityMetadata772To773PaintingShifts() {
        // painting_variant（種別 30、holder）：772の holder 15（絵画ID 14）
        // → 773では holder 16（絵画ID 15）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（絵画ID 14 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata772To773(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(16, reader.readVarInt()); // holder（絵画ID 15 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }""",
     """    @Test
    void entityMetadata772To773PaintingIdentity() {
        // 絵画は772・773とも51件・同じ並びのため素通しする。dennis は両方とも
        // 絵画ID 14（holder 15）のまま。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（絵画ID 14 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata772To773(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(15, reader.readVarInt()); // holder（絵画ID 14 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }"""),
    ("entityMetadata dennis dropped", """    @Test
    void entityMetadata773To772DennisDropped() {
        // dennis（773の絵画ID 14、holder 15）は772に無いため項目ごと落とす。
        // wolf_variant の項目だけが残る。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（dennis +1）
        byte[] out = ChunkTranslator.translateEntityMetadata773To772(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }""",
     """    @Test
    void entityMetadata773To772DennisIdentity() {
        // 逆向きも素通しする（dennis は772にも絵画ID 14 である）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(24); // wolf_variant
        entries.writeVarInt(3); // 参照 id
        entries.writeUnsignedByte(1); // index 1
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(15); // holder（dennis +1）
        byte[] out = ChunkTranslator.translateEntityMetadata773To772(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(15, reader.readVarInt()); // holder（dennis +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }"""),
    ("entityMetadata painting back", """    @Test
    void entityMetadata773To772PaintingShiftsBack() {
        // 773の holder 16（絵画ID 15）→ 772では holder 15（絵画ID 14）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(16); // holder（絵画ID 15 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata773To772(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(15, reader.readVarInt()); // holder（絵画ID 14 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }""",
     """    @Test
    void entityMetadata773To772PaintingIdentity() {
        // kebab（両方とも絵画ID 24、holder 25）も素通しする。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0); // index 0
        entries.writeVarInt(30); // painting_variant
        entries.writeVarInt(25); // holder（絵画ID 24 +1）
        byte[] out = ChunkTranslator.translateEntityMetadata773To772(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(30, reader.readVarInt());
        assertEquals(25, reader.readVarInt()); // holder（絵画ID 24 +1）
        assertEquals(0xFF, reader.readUnsignedByte());
    }"""),
    ("spawnEntity happy_ghast", """    @Test
    void spawnEntity772To773HappyGhastIdentity() {
        // ハッピーガスト（772・773とも 56）はそのまま。
        byte[] out = ChunkTranslator.translateSpawnEntity772To773(spawnEntityPacket(56, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(56, reader.readVarInt()); // happy_ghast（773）
    }""",
     """    @Test
    void spawnEntity772To773HappyGhastShifts() {
        // ハッピーガスト：772の 56 → 773の 57（copper_golem を27に挿入）。
        byte[] out = ChunkTranslator.translateSpawnEntity772To773(spawnEntityPacket(56, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(57, reader.readVarInt()); // happy_ghast（773）
    }

    @Test
    void spawnEntity773To772CopperGolemBecomesPig() {
        // 銅ゴーレム（773の 27）・マネキン（773の 82）は772に無いためブタ（95）にする。
        byte[] out = ChunkTranslator.translateSpawnEntity773To772(spawnEntityPacket(27, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(95, reader.readVarInt()); // pig（772）
        byte[] out2 = ChunkTranslator.translateSpawnEntity773To772(spawnEntityPacket(82, 0));
        Reader reader2 = new Reader(out2);
        assertEquals(7, reader2.readVarInt()); // entityId
        reader2.readBytes(16); // uuid
        assertEquals(95, reader2.readVarInt()); // pig（772）
    }"""),
    ("spawnEntity pig", """    @Test
    void spawnEntity773To772PigIdentity() {
        // ブタ（772・773とも 95）はそのまま。
        byte[] out = ChunkTranslator.translateSpawnEntity773To772(spawnEntityPacket(95, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(95, reader.readVarInt()); // pig（772）
    }""",
     """    @Test
    void spawnEntity773To772PigShiftsBack() {
        // ブタ：773の 97 → 772の 95。
        byte[] out = ChunkTranslator.translateSpawnEntity773To772(spawnEntityPacket(97, 0));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(95, reader.readVarInt()); // pig（772）
    }"""),
    ("explosion forward", """    @Test
    void explosion772To773SoundShifts() {
        // 音は参照 Holder：939（772）→940（773）。粒子（entity_effect 20）はそのまま。
        Writer payload = new Writer();
        payload.writeDouble(1.0); // x
        payload.writeDouble(2.0); // y
        payload.writeDouble(3.0); // z
        payload.writeBoolean(true); // hasKnockback
        payload.writeDouble(0.1);
        payload.writeDouble(0.2);
        payload.writeDouble(0.3);
        payload.writeVarInt(20); // entity_effect（粒子、データは色 i32）
        payload.writeInt(0xFF0000);
        payload.writeVarInt(940); // holder：音ID 939 +1
        byte[] out = ChunkTranslator.translateExplosion772To773(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertTrue(reader.readBoolean());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0.2, reader.readDouble());
        assertEquals(0.3, reader.readDouble());
        assertEquals(20, reader.readVarInt()); // 粒子
        assertEquals(0xFF0000, reader.readInt()); // 粒子の色
        assertEquals(941, reader.readVarInt()); // holder：音ID 940 +1
        assertEquals(0, reader.remaining());
    }""",
     """    @Test
    void explosion772To773SoundShifts() {
        // 音は参照 Holder：939（772の music_disc.lava_chicken）→970（773）。
        // 粒子は entity_effect 20→21（copper_fire_flame の挿入で+1）。
        Writer payload = new Writer();
        payload.writeDouble(1.0); // x
        payload.writeDouble(2.0); // y
        payload.writeDouble(3.0); // z
        payload.writeBoolean(true); // hasKnockback
        payload.writeDouble(0.1);
        payload.writeDouble(0.2);
        payload.writeDouble(0.3);
        payload.writeVarInt(20); // entity_effect（粒子、データは色 i32）
        payload.writeInt(0xFF0000);
        payload.writeVarInt(940); // holder：音ID 939 +1
        byte[] out = ChunkTranslator.translateExplosion772To773(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertTrue(reader.readBoolean());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0.2, reader.readDouble());
        assertEquals(0.3, reader.readDouble());
        assertEquals(21, reader.readVarInt()); // 粒子
        assertEquals(0xFF0000, reader.readInt()); // 粒子の色
        assertEquals(971, reader.readVarInt()); // holder：音ID 970 +1
        assertEquals(0, reader.remaining());
    }"""),
    ("explosion reverse", """    @Test
    void explosion773To772LavaChickenSoundBecomesStoneBreak() {
        Writer payload = new Writer();
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeBoolean(false); // hasKnockback 無し
        payload.writeVarInt(20); // entity_effect
        payload.writeInt(0x00FF00);
        payload.writeVarInt(940); // holder：音ID 939 +1
        byte[] out = ChunkTranslator.translateExplosion773To772(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(false, reader.readBoolean());
        assertEquals(20, reader.readVarInt());
        assertEquals(0x00FF00, reader.readInt()); // 粒子の色
        assertEquals(1442, reader.readVarInt()); // holder：音ID 1441 +1（block.stone.break）
        assertEquals(0, reader.remaining());
    }""",
     """    @Test
    void explosion773To772CopperChestSoundBecomesStoneBreak() {
        // 逆向き：773にしかない block.copper_chest.close（356）は772では
        // block.stone.break（1442）で代替する。粒子は 21→20 に戻る。
        Writer payload = new Writer();
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeBoolean(false); // hasKnockback 無し
        payload.writeVarInt(21); // entity_effect（773）
        payload.writeInt(0x00FF00);
        payload.writeVarInt(357); // holder：音ID 356 +1
        byte[] out = ChunkTranslator.translateExplosion773To772(payload.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(false, reader.readBoolean());
        assertEquals(20, reader.readVarInt());
        assertEquals(0x00FF00, reader.readInt()); // 粒子の色
        assertEquals(1443, reader.readVarInt()); // holder：音ID 1442 +1（block.stone.break）
        assertEquals(0, reader.remaining());
    }"""),
])

def write_shifted_src(src_name, dst_name, fixes):
    """fixes の old はシフト前（772の文面）で書く。new は773の文面。
    手動でシフトした old は間違いやすい（<p> 落ち・27730 の罠）ため、
    ここでは old も shift() で写す。
    new 文面の中のヘルパー呼び出しは、生成物のヘルパー名（773付き）に
    合わせる。旧名のままではコンパイルエラーになる（2026-11-12に確認）。"""
    helper_renames = [
        ("joinGame772Payload", "joinGame773Payload"),
        ("respawn772Payload", "respawn773Payload"),
        ("playerChat772Payload", "playerChat773Payload"),
        ("setCreativeSlot772Packet", "setCreativeSlot773Packet"),
    ]
    src = (TEST / src_name).read_text(encoding="utf-8")
    out = shift(src)
    shifted_fixes = []
    for label, old, new in fixes:
        for old_id, new_id in helper_renames:
            new = new.replace(old_id, new_id)
        shifted_fixes.append((label, shift(old), new))
    out = apply_fixes(dst_name, out, shifted_fixes)
    (TEST / dst_name).write_text(out, encoding="utf-8")


BEDROCK_TEST = Path(__file__).resolve().parent.parent.parent \
    / "detergentmc-bedrock/src/test/java/io/detergentmc/bedrock"


def write_shifted_bedrock(src_name, dst_name, fixes):
    """write_shifted_src の Bedrock 版。helper_renames は使わない。"""
    src = (BEDROCK_TEST / src_name).read_text(encoding="utf-8")
    out = shift(src)
    shifted_fixes = [(label, shift(old), new) for label, old, new in fixes]
    out = apply_fixes(dst_name, out, shifted_fixes)
    (BEDROCK_TEST / dst_name).write_text(out, encoding="utf-8")


print("ChunkTranslator773Test を生成した")


# ------------------------------------------------------------------
# ProxySession773Test
# ------------------------------------------------------------------
# old はシフト前（772の文面）で書く。new は773の文面。
write_shifted_src("ProxySession772Test.java", "ProxySession773Test.java", [
    ("class javadoc", """/**
 * 772（1.21.7／1.21.8）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession771Test} と同じ段取りで、772の番号・形式で確かめる。
 * 772のパケットIDは771と変わらない（247件すべて同一）。ペイロードでは
 * Player Chat の globalIndex、チャットの checksum（771・772ともあるため
 * 剥がさない）、Set Creative Slot の UntrustedSlot、Window Click の破棄、
 * ロケーターバー／ダイアログ／Change Gamemode／Custom Click Action の中継
 * （771にもあるため）、ジュークボックス lava_chicken・絵画 dennis・
 * アイテム music_disc_lava_chicken の付け替えを確かめる。
 */""",
     """/**
 * 773（1.21.9／1.21.10）クライアント・サーバーのプロキシ統合テスト。
 * {@link ProxySession772Test} と同じ段取りで、773の番号・形式で確かめる。
 * 773のパケットIDは772から変わる（247件のうち108件が変わる。デバッグ系の
 * 挿入で0x1A以降が+4、0x27以降が+5。Join Game 0x2B→0x30、Chunk Data
 * 0x27→0x2C、Keep Alive 0x26→0x2B、Ping 0x36→0x3B）。ペイロードでは
 * Player Chat の globalIndex、チャットの checksum（772・773ともあるため
 * 剥がさない）、Set Creative Slot の UntrustedSlot、Window Click の破棄、
 * ロケーターバー／ダイアログ／Change Gamemode／Custom Click Action の中継
 * （772にもあるため）、棚の挿入（bookshelf 305→317）・銅ゴーレム／マネキン
 * （Spawn Entity はブタへ）・銅のチェストの音（block.stone.break で代替）の
 * 付け替えを確かめる。ジュークボックスの歌・絵画は772・773とも同一のため
 * 素通しする。
 */"""),
    ("jukeboxSongs772 helper 21 songs", """    /** 771のジュークボックスの歌20件（tears を17番に挿入）。 */
    private static List<String> jukeboxSongs771() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "mall", "mellohi", "otherside", "pigstep",
                "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }""",
     """    /** 772のジュークボックスの歌21件（実バニラ1.21.7／1.21.8の並び。lava_chicken は9番）。773も同じ21件。 */
    private static List<String> jukeboxSongs772() {
        return List.of("11", "13", "5", "blocks", "cat", "chirp", "creator",
                "creator_music_box", "far", "lava_chicken", "mall", "mellohi", "otherside",
                "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward");
    }"""),
    ("native waypoint 0x88", """        // 771・772両方にあるパケットも素通し（Tracked Waypoint 0x83）。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x83, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x83, waypoint.toClient().get(0).id());""",
     """        // 772・773両方にあるパケットも素通し（Tracked Waypoint 0x88）。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x88, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x88, waypoint.toClient().get(0).id());"""),
    ("773vs772 registry jukebox identity", """        // Registry Data（771：0x07、ジュークボックス20件）→ 772（0x07、21件。
        // lava_chicken を9番に挿入）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs771())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(21, names.size());
        assertEquals(9, names.indexOf("lava_chicken"));
        assertEquals(10, names.indexOf("mall"));
        assertEquals(18, names.indexOf("tears"));
        assertEquals(19, names.indexOf("wait"));
        assertEquals(20, names.indexOf("ward"));""",
     """        // Registry Data（772：0x07、ジュークボックス21件）→ 773（0x07、21件・同じ並び）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(21, names.size());
        assertEquals(9, names.indexOf("lava_chicken"));
        assertEquals(10, names.indexOf("mall"));
        assertEquals(18, names.indexOf("tears"));
        assertEquals(19, names.indexOf("wait"));
        assertEquals(20, names.indexOf("ward"));"""),
    ("773vs772 joinGame 0x30", """        // Join Game（771：0x2B）→ 772（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame772Payload(), join.toClient().get(0).payload());""",
     """        // Join Game（772：0x2B）→ 773（0x30、同形式。IDだけ変わる）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());
        assertArrayEquals(joinGame772Payload(), join.toClient().get(0).payload());"""),
    ("773vs772 spawnPosition 0x5F", """        // Spawn Position（771：0x5A）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());""",
     """        // Spawn Position（772：0x5A）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());"""),
    ("773vs772 keepAlive ping", """        // Keep Alive（771：0x26）→ 772（0x26）。Ping（771：0x36）→ 772（0x36）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x36, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x36, ping.toClient().get(0).id());""",
     """        // Keep Alive（772：0x26）→ 773（0x2B）。Ping（772：0x36）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x36, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());"""),
    ("773vs772 respawn 0x50", """        // Respawn（771：0x4B）→ 772（0x4B、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn772Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4B, respawn.toClient().get(0).id());
        assertArrayEquals(respawn772Payload(), respawn.toClient().get(0).payload());""",
     """        // Respawn（772：0x4B）→ 773（0x50、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn772Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x50, respawn.toClient().get(0).id());
        assertArrayEquals(respawn772Payload(), respawn.toClient().get(0).payload());"""),
    ("773vs772 setSlot bookshelf", """        // Set Slot（771：0x14、アイテム 1256）→ 772（0x14、1257。
        // music_disc_lava_chicken は772で増えたため +1 ずれる）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1256)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1257, readSetSlotItemId768(slot.toClient().get(0).payload()));""",
     """        // Set Slot（772：0x14、アイテム 1256）→ 773（0x14、1304。
        // music_disc_lava_chicken は772で1256、773で1304（+48）。
        // 棚の挿入などで 1247〜1299 が+48ずれる）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1256)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1304, readSetSlotItemId768(slot.toClient().get(0).payload()));"""),
    ("773vs772 playerChat 0x3F", """        // Player Chat（771：0x3A、globalIndex 3 付き）→ 772（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat772Payload(), chat.toClient().get(0).payload());""",
     """        // Player Chat（772：0x3A、globalIndex 3 付き）→ 773（0x3F、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3F, chat.toClient().get(0).id());
        assertArrayEquals(playerChat772Payload(), chat.toClient().get(0).payload());"""),
    ("773vs772 setCreativeSlot 1304", """        // Set Creative Slot（772：0x37、UntrustedSlot、アイテム 1257）
        // → 771（0x37、アイテム 1256）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot772Packet(1257)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1256, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));""",
     """        // Set Creative Slot（773：0x37、UntrustedSlot、アイテム 1304）
        // → 772（0x37、アイテム 1256）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot772Packet(1304)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1256, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));"""),
    ("772vs773 registry jukebox identity", """        // Registry Data（772：0x07、ジュークボックス21件）→ 771（0x07、20件。
        // lava_chicken を落とす）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(20, names.size());
        assertEquals(9, names.indexOf("mall"));
        assertEquals(17, names.indexOf("tears"));
        assertEquals(18, names.indexOf("wait"));
        assertEquals(19, names.indexOf("ward"));
        assertFalse(names.contains("lava_chicken"));""",
     """        // Registry Data（773：0x07、ジュークボックス21件）→ 772（0x07、21件・同じ並び）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));
        assertEquals(1, registry.toClient().size());
        assertEquals(0x07, registry.toClient().get(0).id());
        List<String> names = shortNames(readRegistryPacketNames(
                registry.toClient().get(0).payload()));
        assertEquals(21, names.size());
        assertEquals(9, names.indexOf("lava_chicken"));
        assertEquals(10, names.indexOf("mall"));
        assertEquals(18, names.indexOf("tears"));
        assertEquals(19, names.indexOf("wait"));
        assertEquals(20, names.indexOf("ward"));"""),
    ("772vs773 joinGame incoming 0x30", """        // Join Game（772：0x2B）→ 771（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame772Payload(), join.toClient().get(0).payload());""",
     """        // Join Game（773：0x30）→ 772（0x2B、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());
        assertArrayEquals(joinGame772Payload(), join.toClient().get(0).payload());"""),
    ("772vs773 spawnPosition incoming 0x5F", """        // Spawn Position（772：0x5A）→ 771（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5A, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());""",
     """        // Spawn Position（773：0x5F）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x5F, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());"""),
    ("772vs773 setSlot 1304 to 1256", """        // Set Slot（772：0x14、アイテム 1256）→ 771（0x14、石 1。
        // music_disc_lava_chicken は771に無いため、772→771の付け替えは石になる）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1256)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1, readSetSlotItemId768(slot.toClient().get(0).payload()));""",
     """        // Set Slot（773：0x14、アイテム 1304）→ 772（0x14、1256。
        // music_disc_lava_chicken は773で1304、772で1256）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(1304)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x14, slot.toClient().get(0).id());
        assertEquals(1256, readSetSlotItemId768(slot.toClient().get(0).payload()));"""),
    ("772vs773 playerChat incoming 0x3F", """        // Player Chat（772：0x3A、globalIndex 3 付き）→ 771（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat772Payload(), chat.toClient().get(0).payload());""",
     """        // Player Chat（773：0x3F、globalIndex 3 付き）→ 772（0x3A、そのまま）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3F, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3A, chat.toClient().get(0).id());
        assertArrayEquals(playerChat772Payload(), chat.toClient().get(0).payload());"""),
    ("772vs773 setCreativeSlot expect 1304", """        // Set Creative Slot（771：0x37、UntrustedSlot、アイテム 1256）
        // → 772（0x37、アイテム 1257）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot772Packet(1256)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1257, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));""",
     """        // Set Creative Slot（772：0x37、UntrustedSlot、アイテム 1256）
        // → 773（0x37、アイテム 1304）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x37, setCreativeSlot772Packet(1256)));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x37, creative.toServer().get(0).id());
        assertEquals(1304, readSetCreativeSlotItemId(creative.toServer().get(0).payload()));"""),
    ("772vs773 waypoint incoming 0x88", """        // Tracked Waypoint（772：0x83）は771にも同ID・同形式があるためそのまま通す。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x83, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x83, waypoint.toClient().get(0).id());""",
     """        // Tracked Waypoint（773：0x88）は772（0x83）へ番号だけ付け替えて通す。
        Outcome waypoint = session.handleServerPacket(
                new MinecraftPacket(0x88, new byte[]{0x00}));
        assertEquals(1, waypoint.toClient().size());
        assertEquals(0x83, waypoint.toClient().get(0).id());"""),
    ("772vs773 dialog incoming 0x8A 0x89", """        // Show Dialog（772：0x85）／Clear Dialog（772：0x84）は
        // 771にも同ID・同形式があるためそのまま通す。
        Outcome showDialog = session.handleServerPacket(
                new MinecraftPacket(0x85, new byte[]{0x00}));
        assertEquals(1, showDialog.toClient().size());
        assertEquals(0x85, showDialog.toClient().get(0).id());
        Outcome clearDialog = session.handleServerPacket(
                new MinecraftPacket(0x84, new byte[0]));
        assertEquals(1, clearDialog.toClient().size());
        assertEquals(0x84, clearDialog.toClient().get(0).id());""",
     """        // Show Dialog（773：0x8A）／Clear Dialog（773：0x89）は
        // 772（0x85／0x84）へ番号だけ付け替えて通す。
        Outcome showDialog = session.handleServerPacket(
                new MinecraftPacket(0x8A, new byte[]{0x00}));
        assertEquals(1, showDialog.toClient().size());
        assertEquals(0x85, showDialog.toClient().get(0).id());
        Outcome clearDialog = session.handleServerPacket(
                new MinecraftPacket(0x89, new byte[0]));
        assertEquals(1, clearDialog.toClient().size());
        assertEquals(0x84, clearDialog.toClient().get(0).id());"""),
    ("772vs773 spawnEntity happy_ghast 57", """        // Spawn Entity（772：0x01、ハッピーガスト 56）→ 771（56、同型）。
        Outcome spawnEntity = session.handleServerPacket(
                new MinecraftPacket(0x01, spawnEntityPacket(56, 0)));
        assertEquals(1, spawnEntity.toClient().size());
        assertEquals(0x01, spawnEntity.toClient().get(0).id());
        assertEquals(56, readSpawnEntityType(spawnEntity.toClient().get(0).payload()));""",
     """        // Spawn Entity（773：0x01、ハッピーガスト 57）→ 772（56）。
        Outcome spawnEntity = session.handleServerPacket(
                new MinecraftPacket(0x01, spawnEntityPacket(57, 0)));
        assertEquals(1, spawnEntity.toClient().size());
        assertEquals(0x01, spawnEntity.toClient().get(0).id());
        assertEquals(56, readSpawnEntityType(spawnEntity.toClient().get(0).payload()));"""),
    ("773vs766 joinGame outgoing 0x30", """        // Join Game（766：0x2B、seaLevel 無し）→ 772（0x2B、seaLevel=63 を補う）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());""",
     """        // Join Game（766：0x2B、seaLevel 無し）→ 773（0x30、seaLevel=63 を補う）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame766Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());"""),
    ("773vs766 spawnPosition outgoing 0x5F", """        // Spawn Position（766：0x56）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x56, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());""",
     """        // Spawn Position（766：0x56）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x56, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());"""),
    ("773vs766 keepAlive ping outgoing", """        // Keep Alive（766：0x26）→ 772（0x26）。Ping（766：0x35）→ 772（0x36）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x35, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x36, ping.toClient().get(0).id());""",
     """        // Keep Alive（766：0x26）→ 773（0x2B）。Ping（766：0x35）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x26, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x35, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());"""),
    ("773vs765 joinGame outgoing 0x30", """        // Join Game（765：0x29）→ 772（0x2B）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2B, join.toClient().get(0).id());""",
     """        // Join Game（765：0x29）→ 773（0x30）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x29, joinGame765Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x30, join.toClient().get(0).id());"""),
    ("773vs765 spawnPosition outgoing 0x5F", """        // Spawn Position（765：0x54）→ 772（0x5A）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5A, spawn.toClient().get(0).id());""",
     """        // Spawn Position（765：0x54）→ 773（0x5F）。
        Outcome spawn = session.handleServerPacket(new MinecraftPacket(0x54, new byte[0]));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x5F, spawn.toClient().get(0).id());"""),
    ("773vs765 keepAlive ping outgoing", """        // Keep Alive（765：0x24）→ 772（0x26）。Ping（765：0x33）→ 772（0x36）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x26, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x36, ping.toClient().get(0).id());""",
     """        // Keep Alive（765：0x24）→ 773（0x2B）。Ping（765：0x33）→ 773（0x3B）。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome ka = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, ka.toClient().size());
        assertEquals(0x2B, ka.toClient().get(0).id());
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x33, keepAlive.bytes()));
        assertEquals(1, ping.toClient().size());
        assertEquals(0x3B, ping.toClient().get(0).id());"""),
    ("773vs765 respawn outgoing 0x50", """        // Respawn（765：0x45）→ 772（0x4B、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4B, respawn.toClient().get(0).id());""",
     """        // Respawn（765：0x45）→ 773（0x50、seaLevel 付き）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, respawn765Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x50, respawn.toClient().get(0).id());"""),
    ("773vs765 addResourcePack 0x4F", """        // Add Resource Pack（765：0x44）→ 772（0x4A）。
        Writer pack = new Writer();
        pack.writeUuid(PLAYER_UUID);
        pack.writeString("https://example.invalid/pack.zip");
        pack.writeString("0123456789abcdef0123456789abcdef01234567");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome addPack = session.handleServerPacket(
                new MinecraftPacket(0x44, pack.bytes()));
        assertEquals(1, addPack.toClient().size());
        assertEquals(0x4A, addPack.toClient().get(0).id());""",
     """        // Add Resource Pack（765：0x44）→ 773（0x4F）。
        Writer pack = new Writer();
        pack.writeUuid(PLAYER_UUID);
        pack.writeString("https://example.invalid/pack.zip");
        pack.writeString("0123456789abcdef0123456789abcdef01234567");
        pack.writeBoolean(false);
        pack.writeBoolean(false);
        Outcome addPack = session.handleServerPacket(
                new MinecraftPacket(0x44, pack.bytes()));
        assertEquals(1, addPack.toClient().size());
        assertEquals(0x4F, addPack.toClient().get(0).id());"""),
    ("773vs765 disconnect 0x20", """        // Disconnect（765：0x1B）→ 772（0x1C）。セッションを閉じるため最後に送る。
        Outcome disconnect = session.handleServerPacket(
                new MinecraftPacket(0x1B, new byte[]{0x00}));
        assertEquals(1, disconnect.toClient().size());
        assertEquals(0x1C, disconnect.toClient().get(0).id());""",
     """        // Disconnect（765：0x1B）→ 773（0x20）。セッションを閉じるため最後に送る。
        Outcome disconnect = session.handleServerPacket(
                new MinecraftPacket(0x1B, new byte[]{0x00}));
        assertEquals(1, disconnect.toClient().size());
        assertEquals(0x20, disconnect.toClient().get(0).id());"""),
    ("769vs773 registry input 21 songs", """        // Registry Data（772：0x07、ジュークボックス21件）→ 769（0x07、19件。
        // lava_chicken と tears を落とす）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));""",
     """        // Registry Data（773：0x07、ジュークボックス21件）→ 769（0x07、19件。
        // lava_chicken と tears を落とす）。
        Outcome registry = session.handleServerPacket(new MinecraftPacket(0x07,
                registryPacket("minecraft:jukebox_song", jukeboxSongs772())));"""),
    ("769vs773 joinGame incoming 0x30", """        // Join Game（772：0x2B）→ 769（0x2C、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());""",
     """        // Join Game（773：0x30）→ 769（0x2C、同形式）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame772Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x2C, join.toClient().get(0).id());"""),
    ("769vs773 respawn incoming 0x50", """        // Respawn（772：0x4B）→ 769（0x4C、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x4B, respawn772Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());""",
     """        // Respawn（773：0x50）→ 769（0x4C、同形式）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x50, respawn772Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x4C, respawn.toClient().get(0).id());"""),
    ("769vs773 setSlot 649 to 612", """        // Set Slot（772：0x14、アイテム 620）→ 769（0x15、612。772の 620 は
        // 771でも 620 で、771の 620 は 769では 612）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(620)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(612, readSetSlotItemId768(slot.toClient().get(0).payload()));""",
     """        // Set Slot（773：0x14、アイテム 649）→ 769（0x15、612。773の 649 は
        // 772で 620 で、772の 620 は 769では 612）。
        Outcome slot = session.handleServerPacket(
                new MinecraftPacket(0x14, setSlotPacket768(649)));
        assertEquals(1, slot.toClient().size());
        assertEquals(0x15, slot.toClient().get(0).id());
        assertEquals(612, readSetSlotItemId768(slot.toClient().get(0).payload()));"""),
    ("769vs773 playerChat incoming 0x3F", """        // Player Chat（772：0x3A、globalIndex 3 付き）→ 769（0x3B、剥がす）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3A, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3B, chat.toClient().get(0).id());""",
     """        // Player Chat（773：0x3F、globalIndex 3 付き）→ 769（0x3B、剥がす）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x3F, playerChat772Payload()));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x3B, chat.toClient().get(0).id());"""),
    ("773vs763 held joinGame 0x30", """        // Finish Configuration の応答（772：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x2B, finishAck.toClient().get(0).id());""",
     """        // Finish Configuration の応答（773：0x03）で預かった Join Game を送る。
        Outcome finishAck = session.handleClientPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(1, finishAck.toClient().size());
        assertEquals(0x30, finishAck.toClient().get(0).id());"""),
    ("763vs773 joinGame incoming 0x30", """        // Join Game（772：0x2B）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x2B, joinGame772Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());""",
     """        // Join Game（773：0x30）→ 763（0x28）＋保持したリソースパック（0x40）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x30, joinGame772Payload()));
        assertEquals(2, join.toClient().size());
        assertEquals(0x28, join.toClient().get(0).id());"""),
    ("native startConfiguration 0x74", """        // ネイティブのセッションは再設定を追跡しない（771のネイティブ同士と同じ）：
        // サーバーからの Start Configuration（0x6F）はそのまま中継し、
        // セッションはプレイ状態のまま、isReconfiguring() も false のままである。
        // その後のクライアントの設定フェーズのパケットも、プレイ状態の番号で
        // そのまま中継される。Custom Click Action（設定 0x08）はプレイ状態の
        // Chat Message（0x08）と番号が同じため、バイト列としてはそのまま
        // サーバーへ届く（サーバーは設定状態で読むため正しく解釈する）。
        ProxySession session = loginTo(772, 772);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome start = session.handleServerPacket(
                new MinecraftPacket(0x6F, new byte[0]));
        assertEquals(1, start.toClient().size());
        assertEquals(0x6F, start.toClient().get(0).id());""",
     """        // ネイティブのセッションは再設定を追跡しない（772のネイティブ同士と同じ）：
        // サーバーからの Start Configuration（0x74）はそのまま中継し、
        // セッションはプレイ状態のまま、isReconfiguring() も false のままである。
        // その後のクライアントの設定フェーズのパケットも、プレイ状態の番号で
        // そのまま中継される。Custom Click Action（設定 0x08）はプレイ状態の
        // Chat Message（0x08）と番号が同じため、バイト列としてはそのまま
        // サーバーへ届く（サーバーは設定状態で読むため正しく解釈する）。
        ProxySession session = loginTo(773, 773);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess765Payload()));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        Outcome start = session.handleServerPacket(
                new MinecraftPacket(0x74, new byte[0]));
        assertEquals(1, start.toClient().size());
        assertEquals(0x74, start.toClient().get(0).id());"""),
    ("clientInformation768 comment", """        // 768クライアント→772サーバーの設定フェーズ：Client Information は
        // 768・772とも9フィールド（末尾の particleStatus 付き）のため、
        // particleStatus を落とさずそのまま通す（771段階の漏れ修正 8fa47ab の
        // 772版。落とすと772サーバーが8フィールドで切断する）。""",
     """        // 768クライアント→773サーバーの設定フェーズ：Client Information は
        // 768・773とも9フィールド（末尾の particleStatus 付き）のため、
        // particleStatus を落とさずそのまま通す（771段階の漏れ修正 8fa47ab の
        // 773版。落とすと773サーバーが8フィールドで切断する）。"""),
])

print("ProxySession773Test を生成した")


# ------------------------------------------------------------------
# JavaBackendConnection773Test（Bedrock → 内部773）
# ------------------------------------------------------------------
# 数値は maps773.json（772→773）で確認済み：oak_boat 827→863、
# music_disc_lava_chicken 1256→1304、trial_spawner 27730→29455、
# 乾燥ガスト 13826→14903、ブタ 95→97、ガスト 55→56、ハッピーガスト 56→57。
# Player Info Update は 773 で 0x44（772 は 0x3F）。Join Game は 0x30。
write_shifted_bedrock("JavaBackendConnection772Test.java",
        "JavaBackendConnection773Test.java", [
    ("class javadoc", """/**
 * Bedrock → 内部 772（Java 1.21.7／1.21.8）サーバーのテスト。
 * 偽 Java サーバー（772 役）とのログイン〜プレイの流れを確認する。
 * 772 は Login Success に strictErrorHandling が無く、Join Game／Respawn の
 * SpawnInfo の末尾に seaLevel が付く（771 と同形）。Client Information には
 * 末尾の particleStatus が付く（771 と同じ）。パケットIDは771と変わらない
 * （Chat Message は771・772とも 0x08）。変わるのは772で増えたレジストリ
 * （music_disc_lava_chicken・dennis・lava_chicken）の正規化だけで、
 * 属性表は771と同じ（camera_distance は 8番、generic.movement_speed は 22）。
 */""",
     """/**
 * Bedrock → 内部 773（Java 1.21.9／1.21.10）サーバーのテスト。
 * 偽 Java サーバー（773 役）とのログイン〜プレイの流れを確認する。
 * 773 は Login Success に strictErrorHandling が無く、Join Game／Respawn の
 * SpawnInfo の末尾に seaLevel が付く（772 と同形）。Client Information には
 * 末尾の particleStatus が付く（772 と同じ）。プレイ・サーバー行きの番号は
 * 772と変わらない（Chat Message は772・773とも 0x08）。プレイ・クライアント行きは
 * 変わる（Join Game 0x2B→0x30、Player Info Update 0x3F→0x44）。
 * 変わるのは773で増えたレジストリ（棚・銅の道具／防具・銅ゴーレム・マネキン）の
 * 正規化だけで、属性表は772と同じ（camera_distance は 8番、
 * generic.movement_speed は 22）。
 */"""),
    ("ALL:joinGame send 0x30", """                    fake.send(new MinecraftPacket(0x2b, joinGame772(42, 0)));""",
     """                    fake.send(new MinecraftPacket(0x30, joinGame773(42, 0)));"""),
    ("joinGame comment 0x30", """                    // Join Game は772では 0x2B（771と同じ）。""",
     """                    // Join Game は773では 0x30（772は 0x2B）。"""),
    ("playerInfoUpdate send 0x44", """                    fake.send(new MinecraftPacket(0x3f, info.bytes()));""",
     """                    fake.send(new MinecraftPacket(0x44, info.bytes()));"""),
    ("playerInfoUpdate comment 0x44", """                    // Player Info Update（0x3F）: add player + update_hat + update_list_order。""",
     """                    // Player Info Update（0x44）: add player + update_hat + update_list_order。"""),
    ("playerInfoUpdate gamemode 0x44", """                    fake.send(new MinecraftPacket(0x3f, gamemode.bytes()));""",
     """                    fake.send(new MinecraftPacket(0x44, gamemode.bytes()));"""),
    ("slot music_disc_lava_chicken 1304", """    /** 772 の新アイテム（music_disc_lava_chicken、1256）は 765 には無いので石（1）に落とす。 */
    @Test
    void slot772NormalizesNewItemToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(1256); // music_disc_lava_chicken（772で増えたアイテム）""",
     """    /** 773 の music_disc_lava_chicken（1304）は 765 には無いので石（1）に落とす。 */
    @Test
    void slot773NormalizesNewItemToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(1304); // music_disc_lava_chicken（773。772では 1256）"""),
    ("slot oak_boat 863", """    /** 772 の oak_boat（827）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot772NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(827); // oak_boat（772。771でも 827）""",
     """    /** 773 の oak_boat（863）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot773NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(863); // oak_boat（773。772では 827）"""),
    ("oak_boat assertion stays 773", """        assertEquals(773, slot.item765());""",
     """        assertEquals(773, slot.item765());"""),
    ("blockState 29455 and 14903", """    /** 772 のブロック状態は 765 に正規化される（石 1→1、trial_spawner 27730→26638、乾燥ガスト 13826→石 1）。 */
    @Test
    void blockState772NormalizesTo765() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 772));
        assertEquals(26638, BedrockChunkTranslator.normalizeBlockState(27730, 772));
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(13826, 772));
    }""",
     """    /** 773 のブロック状態は 765 に正規化される（石 1→1、trial_spawner 29455→26638、乾燥ガスト 14903→石 1）。 */
    @Test
    void blockState773NormalizesTo765() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 773));
        assertEquals(26638, BedrockChunkTranslator.normalizeBlockState(29455, 773));
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(14903, 773));
    }"""),
    ("entityType pig 97", """    /** 772 のブタ（95）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType772NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(95, 772));
    }""",
     """    /** 773 のブタ（97）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType773NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(97, 773));
    }"""),
    ("entityType ghast 56", """    /** 772 のガスト（55）は 765 のガスト（42）に正規化される。 */
    @Test
    void entityType772NormalizesGhastTo765() {
        assertEquals(42, BedrockEntityTranslator.normalizeEntityType(55, 772));
    }""",
     """    /** 773 のガスト（56）は 765 のガスト（42）に正規化される。 */
    @Test
    void entityType773NormalizesGhastTo765() {
        assertEquals(42, BedrockEntityTranslator.normalizeEntityType(56, 773));
    }"""),
    ("entityType happy_ghast 57", """    /**
     * 772 のハッピーガスト（56）は 765 には無いのでブタ（73）に落とす。
     * Bedrock の正規化表は合成表（772→771ではそのまま、771→765でブタ）のため、
     * Java プロキシの Spawn Entity（ガストに代替）とは違う代替になる。
     */
    @Test
    void entityType772NormalizesHappyGhastToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(56, 772));
    }""",
     """    /**
     * 773 のハッピーガスト（57）は 765 には無いのでブタ（73）に落とす。
     * Bedrock の正規化表は合成表（773→772ではそのまま、772→765でブタ）のため、
     * Java プロキシの Spawn Entity（ガストに代替）とは違う代替になる。
     */
    @Test
    void entityType773NormalizesHappyGhastToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(57, 773));
    }"""),
])

print("JavaBackendConnection773Test を生成した")


