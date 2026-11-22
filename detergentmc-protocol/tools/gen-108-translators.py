"""ChunkTranslator / SlotTranslator / PlayTranslator に 108 の節を作る。

108（1.9.1）は 109 とレジストリ・ワイヤ形式とも完全同一
（Stage A 実測：両 jar の全面比較で非クラス差分ゼロ、Join Game・
Chunk Data・Keep Alive のワイヤ採取が同形。packet-ids-108.py）。
そのため各変換器は 109 の節を写して RegistryRemapper の 108 写像
（109 へ全面委譲）へ差し替えたものにする。チャンクの
blockEntities 欠落も 109 と同じ扱いがそのまま正しい。
ブロックエンティティ名の表は RegistryTables110 をそのまま
参照する（109 の節と同じ）。

使い方：
  python3 tools/gen-108-translators.py
  冪等（108 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DONE = "// 108（1.9.1）対応"

CHUNK_HEADER_108 = """    // ------------------------------------------------------------------
    // 108（1.9.1）対応
    //
    // 108 は 1.9.1 のプロトコル。109 とレジストリ・ワイヤ形式とも
    // 完全同一（Stage A 実測）ため、この節は 109 の節を写して
    // RegistryRemapper の 108 写像（109 へ全面委譲）に差し替えた
    // もの。チャンク末尾に blockEntities が無い扱いも 109 と
    // 同じ。ブロックエンティティ名は旧式名（"Furnace" 等、
    // 名前空間なし）で、名前表は RegistryTables110（108／109 と
    // 同一内容）をそのまま参照する。
    // ------------------------------------------------------------------
"""

SLOT_HEADER_108 = """    // ------------------------------------------------------------------
    // 108（1.9.1）対応
    //
    // 108 のスロット形式は 109 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 109 の節を写して番号だけ 108 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 109 と同じ
    // （writeMetadataEntries108）。パケット名の引き直しは
    // PacketIds の 108 列（109 列と同値）を使う。
    // ------------------------------------------------------------------
"""

PLAY_108 = """    // ------------------------------------------------------------------
    // 108（1.9.1）対応
    //
    // Join Game / Respawn のワイヤ形式は 109 と同一で、393 とも
    // 完全一致する（実バニラ 1.9.1 の Join Game 採取で dimension
    // i32 を 20/20 バイト消費を確認済み。docs/PROXY-1.9.1.md）。
    // 109 の実装へ委譲する。
    // ------------------------------------------------------------------

    /** 108 の Join Game の1件（形式は 393 と同一）。 */
    public record JoinGame108(int entityId, int gameMode, int dimension,
            int difficulty, int maxPlayers, String levelType,
            boolean reducedDebug) {
    }

    /** 108 の Join Game を読む（形式は 109 と同一のため 109 の解析を流用する）。 */
    public static JoinGame108 parseJoinGame108(byte[] payload) {
        JoinGame109 join = parseJoinGame109(payload);
        if (join == null) {
            return null;
        }
        return new JoinGame108(join.entityId(), join.gameMode(), join.dimension(),
                join.difficulty(), join.maxPlayers(), join.levelType(),
                join.reducedDebug());
    }

    /** Join Game（758 → 108）。形式は 393 と同一。 */
    public static byte[] translateJoinGame758To108(byte[] payload) {
        return translateJoinGame758To109(payload);
    }

    /** Join Game（108 → 758）。形式は 393 と同一。 */
    public static byte[] translateJoinGame108To758(byte[] payload) {
        return translateJoinGame109To758(payload);
    }

    /** Respawn（758 → 108）。形式は 393 と同一。 */
    public static byte[] translateRespawn758To108(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn758To109(payload, registries);
    }

    /** Respawn（108 → 758）。形式は 393 と同一。 */
    public static byte[] translateRespawn108To758(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn109To758(payload, registries);
    }

"""


def rename_109_to_108(section):
    section = section.replace("V1_9_2", "V1_9_1")
    section = section.replace("1.9.2", "1.9.1")
    section = section.replace("109", "108")
    return section


def copy_tail_section(path, marker, header):
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
    section = rename_109_to_108(section)
    assert DONE in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    path.write_text(new_text, encoding="utf-8")
    print(f"{path.name} に 108 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")


def main():
    base = ROOT / "src/main/java/io/detergentmc/protocol"
    copy_tail_section(base / "ChunkTranslator.java",
                      "// 109（1.9.2）対応", CHUNK_HEADER_108)
    copy_tail_section(base / "SlotTranslator.java",
                      "// 109（1.9.2）対応", SLOT_HEADER_108)
    play = base / "PlayTranslator.java"
    text = play.read_text(encoding="utf-8")
    if DONE in text:
        print("skip（既存）: PlayTranslator.java")
    else:
        anchor = ("    /**\n"
                  "     * Player Info（759 0x34 → 758 0x36）。")
        assert text.count(anchor) == 1
        play.write_text(text.replace(anchor, PLAY_108 + anchor),
                        encoding="utf-8")
        print("PlayTranslator.java に 108 の節を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
