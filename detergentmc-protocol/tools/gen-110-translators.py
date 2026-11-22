"""ChunkTranslator / SlotTranslator / PlayTranslator に 110 の節を作る。

110（1.9.4／1.9.3）は 210 とパケット ID が完全同一で、ワイヤ形式の
差分は3件だけ（Stage A 実測）：
  1. sound_effect の pitch が u8（210 は f32）。値の換算が必要
  2. named_sound_effect の pitch が u8（同上）
  3. resource_pack_receive の先頭に hash（string）がある
     （これは ProxySession 側で扱う。変換器の節には現れない）
レジストリ差分（ブロック 5 種・アイテム 6 件・polar_bear・音 19 件・
fallingdust）は RegistryRemapper の 110 表で吸収する。

そのため各変換器は 210 の節を写して RegistryRemapper の 110 表へ
差し替え、音の pitch 換算だけを 110 用に直したものにする。
210 で必要だった形式差分（spawn_entity_living の type u8、title の
action 番号、collect の pickupItemCount 欠落、block_place の
カーソル i8）は 110 でもそのまま同じ（protocol.json の types 節と
play のフィールド構造が 210 と完全一致することを Stage A で照合
済み）。スロット形式も 210 と完全同一（itemId i16＋count i8＋
itemDamage i16＋NBT、メタデータ型は i8 で 0〜12 のみ）。

使い方：
  python3 tools/gen-110-translators.py
  冪等（110 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DONE = "// 110（1.9.4）対応"

CHUNK_HEADER_110 = """    // ------------------------------------------------------------------
    // 110（1.9.4）対応
    //
    // 110 は 1.9.4／1.9.3 の2リリース共通プロトコル。
    // 210 とパケット ID は完全同一で、ワイヤ形式の差分は音の
    // pitch（u8⇔f32 換算）と resource_pack_receive の hash
    // （ProxySession 側で処理）だけ。この節は 210 の節を写して
    // RegistryRemapper の 110 表に差し替え、音の pitch 換算を
    // 110 用に直したもの。210 の形式差分（spawn_entity_living
    // の type が u8、title の action 番号、collect の
    // pickupItemCount 欠落、block_place のカーソルが i8）は
    // 110 でも同一のため、そのまま引き継いでいる。
    // ブロックエンティティ名は 110 も旧式名（"Furnace" 等、
    // 名前空間なし）で、名前表は RegistryTables110（210 と
    // 同一内容）。
    // ------------------------------------------------------------------
"""

SLOT_HEADER_110 = """    // ------------------------------------------------------------------
    // 110（1.9.4）対応
    //
    // 110 のスロット形式は 210 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 210 の節を写して番号だけ 110 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 210 と同じ
    // （writeMetadataEntries110）。
    // ------------------------------------------------------------------
"""

PLAY_110 = """    // ------------------------------------------------------------------
    // 110（1.9.4）対応
    //
    // Join Game / Respawn のワイヤ形式は 210 と同一で、393 とも
    // 完全一致する（実バニラ 1.9.4 の Join Game 採取で 20/20
    // バイト消費を確認済み。docs/PROXY-1.9.4.md）。393 の実装へ
    // 委譲する。
    // ------------------------------------------------------------------

    /** 110 の Join Game の1件（形式は 393 と同一）。 */
    public record JoinGame110(int entityId, int gameMode, int dimension,
            int difficulty, int maxPlayers, String levelType,
            boolean reducedDebug) {
    }

    /** 110 の Join Game を読む（形式は 393 と同一のため 393 の解析を流用する）。 */
    public static JoinGame110 parseJoinGame110(byte[] payload) {
        JoinGame393 join = parseJoinGame393(payload);
        if (join == null) {
            return null;
        }
        return new JoinGame110(join.entityId(), join.gameMode(), join.dimension(),
                join.difficulty(), join.maxPlayers(), join.levelType(),
                join.reducedDebug());
    }

    /** Join Game（758 → 110）。形式は 393 と同一。 */
    public static byte[] translateJoinGame758To110(byte[] payload) {
        return translateJoinGame758To393(payload);
    }

    /** Join Game（110 → 758）。形式は 393 と同一。 */
    public static byte[] translateJoinGame110To758(byte[] payload) {
        return translateJoinGame393To758(payload);
    }

    /** Respawn（758 → 110）。形式は 393 と同一。 */
    public static byte[] translateRespawn758To110(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn758To393(payload, registries);
    }

    /** Respawn（110 → 758）。形式は 393 と同一。 */
    public static byte[] translateRespawn110To758(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn393To758(payload, registries);
    }

"""

EXTRA_110 = """
    // ---- 110 固有の形式差分（210 との差分のうち変換器側） ----

    /** 音の pitch：758 の f32 → 110 の u8（pitch × 63.5 を四捨五入し 0〜255 に丸める）。 */
    private static int pitch110FromF32(float value) {
        int v = Math.round(value * 63.5f);
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** 音の pitch：110 の u8 → 758 の f32（u8 / 63.5）。 */
    private static float pitch110ToF32(int value) {
        int v = value < 0 ? 0 : Math.min(value, 255);
        return v / 63.5f;
    }
"""

PITCH_OLD = """            writer.writeFloat(reader.readFloat()); // volume
            writer.writeFloat(reader.readFloat()); // pitch"""
PITCH_NEW = """            writer.writeFloat(reader.readFloat()); // volume
            if (to110) {
                writer.writeUnsignedByte(pitch110FromF32(reader.readFloat())); // pitch（110 は u8）
            } else {
                writer.writeFloat(pitch110ToF32(reader.readUnsignedByte())); // pitch（110 は u8）
            }"""


def rename_210_to_110(section):
    section = section.replace("V1_10_2", "V1_9_4")
    section = section.replace("1.10.2", "1.9.4")
    section = section.replace("210", "110")
    return section


def fix_chunk_section(section):
    # 音の pitch（u8 ⇔ f32 換算）：sound_effect と named_sound_effect の2件
    n = section.count(PITCH_OLD)
    assert n == 2, f"pitch の行が2件でない: {n}"
    section = section.replace(PITCH_OLD, PITCH_NEW)
    return section + EXTRA_110


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
    section = rename_210_to_110(section)
    if fix is not None:
        section = fix(section)
    assert DONE in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    path.write_text(new_text, encoding="utf-8")
    print(f"{path.name} に 110 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")


def main():
    base = ROOT / "src/main/java/io/detergentmc/protocol"
    copy_tail_section(base / "ChunkTranslator.java",
                      "// 210（1.10.2）対応", CHUNK_HEADER_110,
                      fix=fix_chunk_section)
    copy_tail_section(base / "SlotTranslator.java",
                      "// 210（1.10.2）対応", SLOT_HEADER_110)
    play = base / "PlayTranslator.java"
    text = play.read_text(encoding="utf-8")
    if DONE in text:
        print("skip（既存）: PlayTranslator.java")
    else:
        anchor = ("    /**\n"
                  "     * Player Info（759 0x34 → 758 0x36）。")
        assert text.count(anchor) == 1
        play.write_text(text.replace(anchor, PLAY_110 + anchor),
                        encoding="utf-8")
        print("PlayTranslator.java に 110 の節を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
