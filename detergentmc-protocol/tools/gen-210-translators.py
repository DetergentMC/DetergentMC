"""ChunkTranslator / SlotTranslator / PlayTranslator に 210 の節を作る。

210（1.10／1.10.1／1.10.2）は 315 とパケット ID が完全同一で、
ワイヤ形式の差分は4件だけ（Stage A 実測）：
  1. spawn_entity_living の type が u8（315 は varint）
  2. title の action 番号（210: 2=時間、3=消去、4=リセット。
     315: 2=アクションバー、3=時間、4=消去、5=リセット。
     アクションバー自体は 210 の Title に無く、Chat の
     position=2 で代替する）
  3. collect に pickupItemCount が無い（315 で追加）
  4. block_place のカーソルが i8（0〜15。315 は f32 0.0〜1.0）
あわせてブロックエンティティの NBT id が旧式名（"Furnace" 等、
名前空間なし）で、名前表も 210 専用（RegistryTables210）になる。

そのため各変換器は 315 の節を写して RegistryRemapper の 210 表へ
差し替え、上記4件とブロックエンティティ名を 210 用に直したもの
にする。スロット形式は 315 と完全同一（itemId i16＋count i8＋
itemDamage i16＋NBT、メタデータ型は i8 で 0〜12 のみ）。

使い方：
  python3 tools/gen-210-translators.py
  冪等（210 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DONE = "// 210（1.10.2）対応"

CHUNK_HEADER_210 = """    // ------------------------------------------------------------------
    // 210（1.10.2）対応
    //
    // 210 は 1.10／1.10.1／1.10.2 の3リリース共通プロトコル。
    // 315 とパケット ID は完全同一で、ワイヤ形式の差分は4件だけ
    // （spawn_entity_living の type が u8、title の action 番号、
    // collect の pickupItemCount 欠落、block_place のカーソルが
    // i8）。この節は 315 の節を写して RegistryRemapper の 210 表
    // に差し替え、上記4件とブロックエンティティ名（210 は旧式名
    // "Furnace" 等を名前空間なしで読み書きし、名前表も
    // RegistryTables210 の 210 専用表）を直したもの。
    // object バイト表からは 1.11 追加の evoker_fangs／llama_spit
    // を除いてある（210 に無いため落とす）。
    // ------------------------------------------------------------------
"""

SLOT_HEADER_210 = """    // ------------------------------------------------------------------
    // 210（1.10.2）対応
    //
    // 210 のスロット形式は 315 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 315 の節を写して番号だけ 210 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 315 と同じ
    // （writeMetadataEntries210）。
    // ------------------------------------------------------------------
"""

PLAY_210 = """    // ------------------------------------------------------------------
    // 210（1.10.2）対応
    //
    // Join Game / Respawn のワイヤ形式は 315 と同一で、393 とも
    // 完全一致する（実バニラ 1.10.2 の Join Game 採取で 20/20
    // バイト消費を確認済み。docs/PROXY-1.10.2.md）。393 の実装へ
    // 委譲する。
    // ------------------------------------------------------------------

    /** 210 の Join Game の1件（形式は 393 と同一）。 */
    public record JoinGame210(int entityId, int gameMode, int dimension,
            int difficulty, int maxPlayers, String levelType,
            boolean reducedDebug) {
    }

    /** 210 の Join Game を読む（形式は 393 と同一のため 393 の解析を流用する）。 */
    public static JoinGame210 parseJoinGame210(byte[] payload) {
        JoinGame393 join = parseJoinGame393(payload);
        if (join == null) {
            return null;
        }
        return new JoinGame210(join.entityId(), join.gameMode(), join.dimension(),
                join.difficulty(), join.maxPlayers(), join.levelType(),
                join.reducedDebug());
    }

    /** Join Game（758 → 210）。形式は 393 と同一。 */
    public static byte[] translateJoinGame758To210(byte[] payload) {
        return translateJoinGame758To393(payload);
    }

    /** Join Game（210 → 758）。形式は 393 と同一。 */
    public static byte[] translateJoinGame210To758(byte[] payload) {
        return translateJoinGame393To758(payload);
    }

    /** Respawn（758 → 210）。形式は 393 と同一。 */
    public static byte[] translateRespawn758To210(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn758To393(payload, registries);
    }

    /** Respawn（210 → 758）。形式は 393 と同一。 */
    public static byte[] translateRespawn210To758(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn393To758(payload, registries);
    }

"""

EXTRA_210 = """
    // ---- 210 固有の形式差分（315 との差分のうち変換器側） ----

    /** block_place のカーソル：758 の f32（0.0〜1.0）→ 210 の i8（0〜15）。 */
    private static int cursor210FromF32(float value) {
        int v = Math.round(value * 15.0f);
        return v < 0 ? 0 : Math.min(v, 15);
    }

    /** block_place のカーソル：210 の i8（0〜15）→ 758 の f32（0.0〜1.0）。 */
    private static float cursor210ToF32(int value) {
        int v = value < 0 ? 0 : Math.min(value, 15);
        return v / 15.0f;
    }

    /** Collect（758 → 210）。末尾の pickupItemCount（varint）は 210 に無いため落とす。 */
    public static byte[] translateCollect758To210(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt()); // collectedEntityId
            writer.writeVarInt(reader.readVarInt()); // collectorEntityId
            reader.readVarInt(); // pickupItemCount（210 には無い）
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Collect を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Collect（210 → 758）。pickupItemCount は 1 を補う（210 には個数の情報が無い）。 */
    public static byte[] translateCollect210To758(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt()); // collectedEntityId
            writer.writeVarInt(reader.readVarInt()); // collectorEntityId
            requireEnd(reader);
            writer.writeVarInt(1); // pickupItemCount（補う）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Collect を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ---- タイトル（210 の action 番号：0=題、1=副題、2=時間、3=消去、4=リセット） ----
    // 315 系（498 と同じ合成）とは action 番号だけが違う。アクションバーは
    // 210 の Title に無いため、758 の PLAY_ACTION_BAR は Chat（position=2）
    // へ、210 の Chat（position=2）は PLAY_ACTION_BAR へ振り替える
    // （振り替えは ProxySession が担い、ここはペイロード変換だけを行う）。

    /** 758 の Set Title Text → 210 の Title（action 0＋文字列）。 */
    public static byte[] titleText758To210(byte[] payload) {
        return titleSimple758To210(payload, 0);
    }

    /** 758 の Set Title Subtitle → 210 の Title（action 1＋文字列）。 */
    public static byte[] titleSubtitle758To210(byte[] payload) {
        return titleSimple758To210(payload, 1);
    }

    private static byte[] titleSimple758To210(byte[] payload, int action) {
        try {
            Reader reader = new Reader(payload);
            String text = reader.readString();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(action);
            writer.writeString(text);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 758 の Set Title Time → 210 の Title（action 2＋fadeIn/stay/fadeOut）。 */
    public static byte[] titleAnimation758To210(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int fadeIn = reader.readInt();
            int stay = reader.readInt();
            int fadeOut = reader.readInt();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(2);
            writer.writeInt(fadeIn);
            writer.writeInt(stay);
            writer.writeInt(fadeOut);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 758 の Clear Titles → 210 の Title（action 3=消去、4=リセット）。 */
    public static byte[] titleClear758To210(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean reset = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(reset ? 4 : 3);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 210 の Title → 758 の分割パケットのペイロード（振り分けは ProxySession）。 */
    public static byte[] translateTitle210To758(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readVarInt();
            Writer writer = new Writer();
            switch (action) {
                case 0, 1 -> writer.writeString(reader.readString());
                case 2 -> {
                    writer.writeInt(reader.readInt());
                    writer.writeInt(reader.readInt());
                    writer.writeInt(reader.readInt());
                }
                case 3 -> writer.writeBoolean(false);
                case 4 -> writer.writeBoolean(true);
                default -> throw new IllegalArgumentException(
                        "未知の Title action: " + action);
            }
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Title を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** 210 の Title の action 番号を読む。読めなければ -1。 */
    public static int titleAction210(byte[] payload) {
        try {
            return new Reader(payload).readVarInt();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /** 758 の Action Bar → 210 の Chat ペイロード（JSON＋position=2）。 */
    public static byte[] actionBar758ToChat210(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String text = reader.readString();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(text);
            writer.writeByte(2); // position: アクションバー
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Action Bar を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /** 210 の Chat ペイロードの position（0=チャット、1=システム、2=アクションバー）。読めなければ -1。 */
    public static int chatPosition210(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            reader.readString();
            return reader.readByte();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /** 210 の Chat（position=2）→ 758 の Action Bar ペイロード（JSON のみ）。 */
    public static byte[] chat210ToActionBar758(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String text = reader.readString();
            reader.readByte(); // position（2 のはず）
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(text);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
"""

PROTECT = [
    ("RegistryTables316.BLOCK_ENTITY_NAMES_316", "RTBEN"),
    ("RegistryTables316", "RT"),
]


def rename_315_to_210(section):
    for orig, ph in PROTECT:
        section = section.replace(orig, ph)
    section = section.replace("V1_11", "V1_10_2")
    section = section.replace("1.11", "1.10.2")
    section = section.replace("315", "210")
    for orig, ph in PROTECT:
        section = section.replace(ph, orig)
    return section


def sub_once(section, old, new, where):
    assert section.count(old) == 1, \
        f"{where}: 一意でない({section.count(old)}): {old[:60]}"
    return section.replace(old, new)


def fix_chunk_section(section):
    # ブロックエンティティ名：210 専用表＋旧式名で読み書きする
    section = sub_once(
        section,
        """    private static String blockEntityName210(int type758) {
        int mapped = RegistryRemapper.mapBlockEntityType758To210(type758);
        if (mapped == RegistryRemapper.DROP || mapped < 0
                || mapped >= RegistryTables316.BLOCK_ENTITY_NAMES_316.length) {
            return null;
        }
        return RegistryTables316.BLOCK_ENTITY_NAMES_316[mapped];
    }""",
        """    private static String blockEntityName210(int type758) {
        int mapped = RegistryRemapper.mapBlockEntityType758To210(type758);
        if (mapped == RegistryRemapper.DROP || mapped < 0
                || mapped >= RegistryTables210.BLOCK_ENTITY_LEGACY_NAMES_210.length) {
            return null;
        }
        return RegistryTables210.BLOCK_ENTITY_LEGACY_NAMES_210[mapped];
    }""",
        "blockEntityName210")
    section = sub_once(
        section,
        """    private static int indexOfBlockEntityName210(String name) {
        for (int i = 0; i < RegistryTables316.BLOCK_ENTITY_NAMES_316.length; i++) {
            if (RegistryTables316.BLOCK_ENTITY_NAMES_316[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }""",
        """    private static int indexOfBlockEntityName210(String name) {
        // 210 の実ワイヤは旧式名（"Furnace" 等、名前空間なし）。
        // 内部で合成された NBT が正規化名を持つ場合に備え、
        // 正規化名の表でも引けるようにする。
        for (int i = 0; i < RegistryTables210.BLOCK_ENTITY_LEGACY_NAMES_210.length; i++) {
            if (RegistryTables210.BLOCK_ENTITY_LEGACY_NAMES_210[i].equals(name)) {
                return i;
            }
        }
        for (int i = 0; i < RegistryTables210.BLOCK_ENTITY_NAMES_210.length; i++) {
            if (RegistryTables210.BLOCK_ENTITY_NAMES_210[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }""",
        "indexOfBlockEntityName210")
    # NBT の id は旧式名をそのまま書く（"minecraft:" を付けない）
    n = section.count('data.put("id", new Tag.StringTag("minecraft:" + name));')
    assert n == 2, f"BE id 書き込みが2件でない: {n}"
    section = section.replace(
        'data.put("id", new Tag.StringTag("minecraft:" + name));',
        'data.put("id", new Tag.StringTag(name));')
    # spawn_entity_living の type 幅（u8）
    section = sub_once(
        section,
        "writer.writeVarInt(RegistryRemapper.mapEntityType758To210(reader.readVarInt()));",
        "writer.writeUnsignedByte(RegistryRemapper.mapEntityType758To210(reader.readVarInt()));",
        "spawn living fwd")
    section = sub_once(
        section,
        "writer.writeVarInt(RegistryRemapper.mapEntityType210To758(reader.readVarInt()));",
        "writer.writeVarInt(RegistryRemapper.mapEntityType210To758(reader.readUnsignedByte()));",
        "spawn living rev")
    # block_place のカーソル（i8 ⇔ f32 換算）
    section = sub_once(
        section,
        """            writer.writeVarInt(hand);
            writer.writeFloat(cursorX);
            writer.writeFloat(cursorY);
            writer.writeFloat(cursorZ);""",
        """            writer.writeVarInt(hand);
            writer.writeByte(cursor210FromF32(cursorX));
            writer.writeByte(cursor210FromF32(cursorY));
            writer.writeByte(cursor210FromF32(cursorZ));""",
        "block place fwd")
    section = sub_once(
        section,
        """            float cursorX = reader.readFloat();
            float cursorY = reader.readFloat();
            float cursorZ = reader.readFloat();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(hand);""",
        """            float cursorX = cursor210ToF32(reader.readByte());
            float cursorY = cursor210ToF32(reader.readByte());
            float cursorZ = cursor210ToF32(reader.readByte());
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(hand);""",
        "block place rev")
    # object バイト表から 1.11 追加分を除く
    for line in ("            case 24 -> 79; // evoker_fangs\n",
                 "            case 47 -> 68; // llama_spit\n",
                 "            case 68 -> 47; // llama_spit\n",
                 "            case 79 -> 24; // evoker_fangs\n"):
        assert section.count(line) == 1, f"object 表の行が一意でない: {line}"
        section = section.replace(line, "")
    return section + EXTRA_210


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
    section = rename_315_to_210(section)
    if fix is not None:
        section = fix(section)
    assert DONE in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    path.write_text(new_text, encoding="utf-8")
    print(f"{path.name} に 210 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")


def main():
    base = ROOT / "src/main/java/io/detergentmc/protocol"
    copy_tail_section(base / "ChunkTranslator.java",
                      "// 315（1.11）対応", CHUNK_HEADER_210,
                      fix=fix_chunk_section)
    copy_tail_section(base / "SlotTranslator.java",
                      "// 315（1.11）", SLOT_HEADER_210)
    play = base / "PlayTranslator.java"
    text = play.read_text(encoding="utf-8")
    if DONE in text:
        print("skip（既存）: PlayTranslator.java")
    else:
        anchor = ("    /**\n"
                  "     * Player Info（759 0x34 → 758 0x36）。")
        assert text.count(anchor) == 1
        play.write_text(text.replace(anchor, PLAY_210 + anchor),
                        encoding="utf-8")
        print("PlayTranslator.java に 210 の節を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
