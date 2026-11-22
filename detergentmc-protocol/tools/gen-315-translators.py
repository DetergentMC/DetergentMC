"""ChunkTranslator / SlotTranslator / PlayTranslator に 315 の節を作る。

315（1.11）は 316（1.11.1／1.11.2）とワイヤ形式が完全同一（Stage A
実測：パケットID差分0件、参加経路の形式差分なし、Keep Alive は
両者 varint、メタデータ型も 0〜12 のみ）。レジストリも同一で、
差分はアイテム1件だけ（iron_nugget は 316 で追加。315 向けの
写像は RegistryRemapper の 315 専用表が担う）。

そのため各変換器は 316 の節を写して RegistryRemapper の 315 表へ
差し替えたものにする。ブロックエンティティ名の表
（RegistryTables316.BLOCK_ENTITY_NAMES_316）は 315 でも同一
（23種・bed 無し）ため、315 の節からもそのまま参照する。

使い方：
  python3 tools/gen-315-translators.py
  冪等（315 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DONE = "// 315（1.11）対応"

CHUNK_HEADER_315 = """    // ------------------------------------------------------------------
    // 315（1.11）対応
    //
    // 315 は 316 とワイヤ形式が完全同一（Stage A 実測）で、
    // レジストリも同一。差分はアイテム1件だけ（iron_nugget は
    // 316 で追加され 315 には無い。RegistryRemapper の 315 専用表
    // が金塊へ寄せる）。この節は 316 の節を写して RegistryRemapper
    // の 315 表に差し替えたもの。ブロックエンティティ名の表は
    // 315 でも同一のため RegistryTables316 をそのまま参照する。
    // ------------------------------------------------------------------
"""

SLOT_HEADER_315 = """    // ------------------------------------------------------------------
    // 315（1.11）対応
    //
    // 315 の実ワイヤ形式は 316 と完全同一（スロット：itemId i16＋
    // count i8＋itemDamage i16＋NBT、メタデータの型は i8 で
    // 0〜12 のみ）のため、この節は 316 の節を写して番号だけ
    // 315 に差し替えたもの。メタデータ型 13〜16 の落としも
    // 316 と同じ（writeMetadataEntries315）。
    // ------------------------------------------------------------------
"""

PLAY_315 = """    // ------------------------------------------------------------------
    // 315（1.11）対応
    //
    // Join Game / Respawn のワイヤ形式は 316 と同一で、393 とも
    // 完全一致する（実バニラ 1.11 の Join Game 採取で 17/17
    // バイト消費を確認済み。docs/PROXY-1.11.md）。393 の実装へ
    // 委譲する。
    // ------------------------------------------------------------------

    /** 315 の Join Game の1件（形式は 393 と同一）。 */
    public record JoinGame315(int entityId, int gameMode, int dimension,
            int difficulty, int maxPlayers, String levelType,
            boolean reducedDebug) {
    }

    /** 315 の Join Game を読む（形式は 393 と同一のため 393 の解析を流用する）。 */
    public static JoinGame315 parseJoinGame315(byte[] payload) {
        JoinGame393 join = parseJoinGame393(payload);
        if (join == null) {
            return null;
        }
        return new JoinGame315(join.entityId(), join.gameMode(), join.dimension(),
                join.difficulty(), join.maxPlayers(), join.levelType(),
                join.reducedDebug());
    }

    /** Join Game（758 → 315）。形式は 393 と同一。 */
    public static byte[] translateJoinGame758To315(byte[] payload) {
        return translateJoinGame758To393(payload);
    }

    /** Join Game（315 → 758）。形式は 393 と同一。 */
    public static byte[] translateJoinGame315To758(byte[] payload) {
        return translateJoinGame393To758(payload);
    }

    /** Respawn（758 → 315）。形式は 393 と同一。 */
    public static byte[] translateRespawn758To315(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn758To393(payload, registries);
    }

    /** Respawn（315 → 758）。形式は 393 と同一。 */
    public static byte[] translateRespawn315To758(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn393To758(payload, registries);
    }

"""

PROTECT = [
    ("RegistryTables316", "\u0001RT\u0001"),
    ("BLOCK_ENTITY_NAMES_316", "\u0001BEN\u0001"),
]


def rename_316_to_315(section):
    for orig, ph in PROTECT:
        section = section.replace(orig, ph)
    section = section.replace("V1_11_2", "V1_11")
    section = section.replace("1.11.2", "1.11")
    section = section.replace("316", "315")
    for orig, ph in PROTECT:
        section = section.replace(ph, orig)
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
    section = rename_316_to_315(section)
    assert DONE in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    path.write_text(new_text, encoding="utf-8")
    print(f"{path.name} に 315 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")


def main():
    base = ROOT / "src/main/java/io/detergentmc/protocol"
    copy_tail_section(base / "ChunkTranslator.java",
                      "// 316（1.11.2）対応", CHUNK_HEADER_315)
    copy_tail_section(base / "SlotTranslator.java",
                      "// 316（1.11.2）", SLOT_HEADER_315)
    play = base / "PlayTranslator.java"
    text = play.read_text(encoding="utf-8")
    if DONE in text:
        print("skip（既存）: PlayTranslator.java")
    else:
        anchor = ("    /**\n"
                  "     * Player Info（759 0x34 → 758 0x36）。")
        assert text.count(anchor) == 1
        play.write_text(text.replace(anchor, PLAY_315 + anchor),
                        encoding="utf-8")
        print("PlayTranslator.java に 315 の節を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
