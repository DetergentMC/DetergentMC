"""ChunkTranslator / SlotTranslator / PlayTranslator に 109 の節を作る。

109（1.9.2）は 110 とレジストリが完全同一で、ワイヤ形式の差分は
map_chunk の blockEntities（110 で追加、109 には無い）の1件だけ
（Stage A 実測。パケット ID の差分は PacketIds 側で吸収する）。
そのため各変換器は 110 の節を写して RegistryRemapper の 109 写像
（110 へ全面委譲）へ差し替え、チャンクの blockEntities だけを
109 用に直したものにする：
  - 758 → 109：758 側のブロックエンティティは読む（消費と検証）
    が、109 のワイヤには書かない（落とす）。
  - 109 → 758：109 のワイヤに blockEntities は無いため、758 側は
    常に空（個数 0）。
ブロックエンティティ名の表は 109 でも同一のため RegistryTables110
をそのまま参照する（315 の節が RegistryTables316 を参照する流儀）。

使い方：
  python3 tools/gen-109-translators.py
  冪等（109 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DONE = "// 109（1.9.2）対応"

CHUNK_HEADER_109 = """    // ------------------------------------------------------------------
    // 109（1.9.2）対応
    //
    // 109 は 1.9.2 のプロトコル。110 とレジストリは完全同一で、
    // ワイヤ形式の差分は map_chunk の blockEntities（110 で
    // 追加、109 には無い）だけ。この節は 110 の節を写して
    // RegistryRemapper の 109 写像（110 へ全面委譲）に差し替え、
    // チャンク末尾の blockEntities を 109 用に直したもの。
    // パケット ID の差分（update_sign の有無＋後続6件のずれ）は
    // PacketIds 側で吸収する。ブロックエンティティ名は 109 も
    // 旧式名（"Furnace" 等、名前空間なし）で、名前表は
    // RegistryTables110（109 と同一内容）をそのまま参照する。
    // ------------------------------------------------------------------
"""

SLOT_HEADER_109 = """    // ------------------------------------------------------------------
    // 109（1.9.2）対応
    //
    // 109 のスロット形式は 110 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 110 の節を写して番号だけ 109 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 110 と同じ
    // （writeMetadataEntries109）。パケット名の引き直しは
    // PacketIds の 109 列（update_sign 分のずれ込み）を使う。
    // ------------------------------------------------------------------
"""

PLAY_109 = """    // ------------------------------------------------------------------
    // 109（1.9.2）対応
    //
    // Join Game / Respawn のワイヤ形式は 110 と同一で、393 とも
    // 完全一致する（実バニラ 1.9.2 の Join Game 採取で dimension
    // i32 を 20/20 バイト消費を確認済み。docs/PROXY-1.9.2.md）。
    // 393 の実装へ委譲する。
    // ------------------------------------------------------------------

    /** 109 の Join Game の1件（形式は 393 と同一）。 */
    public record JoinGame109(int entityId, int gameMode, int dimension,
            int difficulty, int maxPlayers, String levelType,
            boolean reducedDebug) {
    }

    /** 109 の Join Game を読む（形式は 393 と同一のため 393 の解析を流用する）。 */
    public static JoinGame109 parseJoinGame109(byte[] payload) {
        JoinGame393 join = parseJoinGame393(payload);
        if (join == null) {
            return null;
        }
        return new JoinGame109(join.entityId(), join.gameMode(), join.dimension(),
                join.difficulty(), join.maxPlayers(), join.levelType(),
                join.reducedDebug());
    }

    /** Join Game（758 → 109）。形式は 393 と同一。 */
    public static byte[] translateJoinGame758To109(byte[] payload) {
        return translateJoinGame758To393(payload);
    }

    /** Join Game（109 → 758）。形式は 393 と同一。 */
    public static byte[] translateJoinGame109To758(byte[] payload) {
        return translateJoinGame393To758(payload);
    }

    /** Respawn（758 → 109）。形式は 393 と同一。 */
    public static byte[] translateRespawn758To109(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn758To393(payload, registries);
    }

    /** Respawn（109 → 758）。形式は 393 と同一。 */
    public static byte[] translateRespawn109To758(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn393To758(payload, registries);
    }

"""


def rename_110_to_109(section):
    section = section.replace("V1_9_4", "V1_9_2")
    section = section.replace("1.9.4", "1.9.2")
    section = section.replace("110", "109")
    # ブロックエンティティ名の表は 110 と同一内容のため参照を戻す
    # （クラス名だけでなく、表のフィールド名の接尾辞も 110 のまま）。
    section = section.replace("RegistryTables109", "RegistryTables110")
    section = section.replace("BLOCK_ENTITY_LEGACY_NAMES_109",
                              "BLOCK_ENTITY_LEGACY_NAMES_110")
    section = section.replace("BLOCK_ENTITY_NAMES_109",
                              "BLOCK_ENTITY_NAMES_110")
    return section


def fix_chunk_section(section):
    # 109 → 758：109 のワイヤには blockEntities が無いため、末尾の
    # 読み取り（個数 varint＋NBT 列）を丸ごと落とし、758 側は空にする。
    start = section.index("            int beCount = reader.readVarInt();")
    end_marker = "            requireEnd(reader);"
    end = section.index(end_marker, start) + len(end_marker)
    section = section[:start] + (
        "            // 109 の Chunk Data には blockEntities フィールドが\n"
        "            // 無い（1.9.3／110 で追加）ため、758 側のブロック\n"
        "            // エンティティは常に空（kept は空のまま）。\n"
        "            List<BlockEntityEntry> kept = new ArrayList<>();\n"
        + end_marker) + section[end:]
    # 758 → 109：758 側のブロックエンティティは読むが書かない。
    old_write = """            writer.writeVarInt(kept.size());
            for (Tag.CompoundTag compound : kept) {
                writer.writeNamedNbt("", compound);
            }
"""
    assert section.count(old_write) == 1
    section = section.replace(old_write, """            // 109 の Chunk Data には blockEntities フィールドが無い
            // （1.9.3／110 で追加）ため、758 側のブロックエンティティは
            // ここで落とす（kept は読み取りの消費と検証のためだけに使う）。
""")
    return section


def copy_tail_section(path, marker, header, fix=None):
    text = path.read_text(encoding="utf-8")
    if DONE in text:
        print(f"skip（既存）: {path.name}")
        return
    lines = text.split("\n")
    marker_idx = next(i for i, ln in enumerate(lines) if marker in ln)
    start = marker_idx
    while not lines[start].strip().startswith("// ---"):
        start -= 1
    assert lines[-1] == "}" or (lines[-1] == "" and lines[-2] == "}"), lines[-3:]
    end = len(lines) - 1 if lines[-1] == "}" else len(lines) - 2
    section = "\n".join(lines[start:end])
    head_end = section.index("\n\n")
    section = header.rstrip("\n") + section[head_end:]
    section = rename_110_to_109(section)
    if fix is not None:
        section = fix(section)
    assert DONE in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    path.write_text(new_text, encoding="utf-8")
    print(f"{path.name} に 109 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")


def main():
    base = ROOT / "src/main/java/io/detergentmc/protocol"
    copy_tail_section(base / "ChunkTranslator.java",
                      "// 110（1.9.4）対応", CHUNK_HEADER_109,
                      fix=fix_chunk_section)
    copy_tail_section(base / "SlotTranslator.java",
                      "// 110（1.9.4）対応", SLOT_HEADER_109)
    play = base / "PlayTranslator.java"
    text = play.read_text(encoding="utf-8")
    if DONE in text:
        print("skip（既存）: PlayTranslator.java")
    else:
        anchor = ("    /**\n"
                  "     * Player Info（759 0x34 → 758 0x36）。")
        assert text.count(anchor) == 1
        play.write_text(text.replace(anchor, PLAY_109 + anchor),
                        encoding="utf-8")
        print("PlayTranslator.java に 109 の節を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
