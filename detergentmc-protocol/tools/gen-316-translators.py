"""ChunkTranslator / SlotTranslator / PlayTranslator に 316 の節を作る。

316 は 340 と同じ世代（Flattening 前・(数値ID<<4)|メタデータ・
チャンク旧式 crossing 詰め・直接パレット 13 ビット・バイオーム
u8×256・スロットの itemDamage 付け替え・粒子 ID i32）で、ワイヤ
形式の差分は 340 との間で次の2点だけ（Stage A 実測）：

・パケット ID が違う（境界で名前引き直し。ProxySession 側）。
・316 のエンティティメタデータ型は 0〜12 のみ。1.12 で増えた
  型 13（NBT、minecraft-data の表記。実ワイヤの世代内番号では
  blockid=13・optblockid=14・nbt=15・particle=16）は 316 に無い。
  ハイブリッド形式（404符号化＋316番号）から実ワイヤへ付け替える
  SlotTranslator の writeMetadataEntries316 で、型 13〜16 の
  エントリは値だけ読み捨てて落とす。

そのため ChunkTranslator と SlotTranslator は 340 の節を写して
RegistryRemapper の 316 表へ差し替えたものにし、メタデータの
落としだけ SlotTranslator 側で足す。PlayTranslator は Join Game／
Respawn の委譲（形式は 393 と同一、316 も実測で同一）だけを足す。

使い方：
  python3 tools/gen-316-translators.py
  冪等（316 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DONE = "// 316（1.11.2）対応"

CHUNK_HEADER_316 = """    // ------------------------------------------------------------------
    // 316（1.11.2）対応
    //
    // 骨格は 340 と同じ旧世代（チャンクの旧式 crossing 詰め・
    // セクション内蔵の光・座標 x26|y12|z26・Spawn Entity の
    // object 分離）なので、この節は 340 の節を写して
    // RegistryRemapper の 316 表に差し替えたもの。316 固有の差分：
    //
    // ・ブロック状態は (数値ID<<4)|メタデータ（340 と同じ）。
    //   316 に無い 1.12 のブロックは RegistryTables316 の近似で
    //   寄せる（コンクリート系→羊毛、彩釉テラコッタ→色付き
    //   堅焼き粘土）。
    // ・エンティティメタデータの型番号はハイブリッド内では
    //   340 世代と同じ（blockid=13・optblockid=14・nbt=15・
    //   particle=16）。316 の実ワイヤに無い型 13〜16 の落としは
    //   SlotTranslator の writeMetadataEntries316 が担う。
    // ・進捗（Advancements）とレシピブックは 316 に無い。
    //   この節の該当メソッドは届かず、ProxySession の名前引き
    //   直し（316 に無いパケットは落とす）で落ちる。
    // ------------------------------------------------------------------
"""

SLOT_HEADER_316 = """    // ------------------------------------------------------------------
    // 316（1.11.2）対応
    //
    // 316 の実ワイヤ形式は 340 と同じ（スロット：itemId i16＋
    // count i8＋itemDamage i16＋NBT、メタデータの型は i8、
    // World Particles の粒子 ID は i32 でデータ形も 340 と同じ）
    // ため、この節は 340 の節を写して番号だけ 316 に差し替えた
    // もの。316 固有の差分は1点だけ：
    //   ・エンティティメタデータの型は 316 では 0〜12 のみ。
    //     ハイブリッド側の型 13（blockid）・14（optblockid）・
    //     15（nbt）・16（particle）は 316 に無いため、
    //     writeMetadataEntries316 が値だけ読み捨てて落とす
    //     （逆向きはワイヤに 0〜12 しか来ないため素通し）。
    // ------------------------------------------------------------------
"""

METADATA_316 = """    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 316 のまま）。 */
    private static void writeMetadataEntries316(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to316) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to316) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to316 && type >= 13 && type <= 16) {
                // 316 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue316(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to316) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue316(reader, writer, source, type, to316);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue316(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to316) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeVarInt(reader.readVarInt());
            case 2 -> writer.writeFloat(reader.readFloat());
            case 3, 4 -> writer.writeString(reader.readString());
            case 5 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                }
            }
            case 6 -> writeSlot316(reader, writer, source, to316);
            case 7 -> writer.writeBoolean(reader.readBoolean());
            case 8 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 9 -> writer.writeLong(reader.readLong()); // position
            case 10 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeLong(reader.readLong());
                }
            }
            case 11 -> writer.writeVarInt(reader.readVarInt()); // direction
            case 12 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeBytes(reader.readBytes(16)); // uuid
                }
            }
            case 13 -> writer.writeVarInt(reader.readVarInt()); // blockid
            case 14 -> { // optblockid
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeVarInt(reader.readVarInt());
                }
            }
            case 15 -> writer.writeBytes(readRawNbt(reader, source)); // nbt
            case 16 -> { // particle
                int particle = reader.readVarInt();
                writer.writeVarInt(particle);
                writeParticleData316(reader, writer, source, particle, to316);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
"""

PLAY_316 = """    // ------------------------------------------------------------------
    // 316（1.11.2）対応
    //
    // Join Game / Respawn のワイヤ形式は 393 と完全一致する
    // （実バニラ 1.11.2 の Join Game 採取で 20/20 バイト消費を
    // 確認済み。docs/PROXY-1.11.2.md）。393 の実装へ委譲する。
    // ------------------------------------------------------------------

    /** 316 の Join Game の1件（形式は 393 と同一）。 */
    public record JoinGame316(int entityId, int gameMode, int dimension,
            int difficulty, int maxPlayers, String levelType,
            boolean reducedDebug) {
    }

    /** 316 の Join Game を読む（形式は 393 と同一のため 393 の解析を流用する）。 */
    public static JoinGame316 parseJoinGame316(byte[] payload) {
        JoinGame393 join = parseJoinGame393(payload);
        if (join == null) {
            return null;
        }
        return new JoinGame316(join.entityId(), join.gameMode(), join.dimension(),
                join.difficulty(), join.maxPlayers(), join.levelType(),
                join.reducedDebug());
    }

    /** Join Game（758 → 316）。形式は 393 と同一。 */
    public static byte[] translateJoinGame758To316(byte[] payload) {
        return translateJoinGame758To393(payload);
    }

    /** Join Game（316 → 758）。形式は 393 と同一。 */
    public static byte[] translateJoinGame316To758(byte[] payload) {
        return translateJoinGame393To758(payload);
    }

    /** Respawn（758 → 316）。形式は 393 と同一。 */
    public static byte[] translateRespawn758To316(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn758To393(payload, registries);
    }

    /** Respawn（316 → 758）。形式は 393 と同一。 */
    public static byte[] translateRespawn316To758(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn393To758(payload, registries);
    }

"""


def copy_tail_section(path, marker, header, extra=None):
    text = path.read_text(encoding="utf-8")
    if DONE in text:
        print(f"skip（既存）: {path.name}")
        return
    lines = text.split("\n")
    marker_idx = next(i for i, ln in enumerate(lines) if marker in ln)
    start = marker_idx - 1
    assert lines[start].strip().startswith("// ---"), lines[start]
    assert lines[-1] == "}" or (lines[-1] == "" and lines[-2] == "}"), lines[-3:]
    end = len(lines) - 1 if lines[-1] == "}" else len(lines) - 2
    section = "\n".join(lines[start:end])
    head_end = section.index("\n\n")
    section = header.rstrip("\n") + section[head_end:]
    section = section.replace("340", "316")
    section = section.replace("1.12.2", "1.11.2")
    section = section.replace("V1_12_2", "V1_11_2")
    if extra is not None:
        section = extra(section)
    assert DONE in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    path.write_text(new_text, encoding="utf-8")
    print(f"{path.name} に 316 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")


def chunk_extra(section):
    # 340 の節には共有ヘルパー（スロットの Damage タグ付け替え）の
    # 定義が含まれる。クラスに既にあるため 316 の節からは除く。
    import re
    for name in ("slotDamageTag", "withSlotDamageTag",
                 "withoutSlotDamageTag"):
        pat = re.compile(r"\n    /\*\*[^*]*\*/\n    static [^\n]*"
                         + name + r"\([^\n]*\n(?:.*?\n)*?    \}\n", re.S)
        section, n = pat.subn("\n", section, count=1)
        assert n == 1, name
    return section


def slot_extra(section):
    # writeMetadataEntries316 を、型 13〜16 を落とす形に差し替える。
    start = section.index("    /** メタデータのエントリ列を付け替える")
    end = section.index("\n    }\n", start) + len("\n    }\n")
    return section[:start] + METADATA_316.rstrip("\n") + section[end:]


def main():
    base = ROOT / "src/main/java/io/detergentmc/protocol"
    copy_tail_section(base / "ChunkTranslator.java",
                      "// 340（1.12.2）対応", CHUNK_HEADER_316, chunk_extra)
    copy_tail_section(base / "SlotTranslator.java",
                      "// 340（1.12.2）", SLOT_HEADER_316, slot_extra)
    play = base / "PlayTranslator.java"
    text = play.read_text(encoding="utf-8")
    if DONE in text:
        print("skip（既存）: PlayTranslator.java")
    else:
        anchor = ("    /**\n"
                  "     * Player Info（759 0x34 → 758 0x36）。")
        assert text.count(anchor) == 1
        play.write_text(text.replace(anchor, PLAY_316 + anchor),
                        encoding="utf-8")
        print("PlayTranslator.java に 316 の節を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
