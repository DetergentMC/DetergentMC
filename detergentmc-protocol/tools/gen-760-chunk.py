#!/usr/bin/env python3
"""ChunkTranslator.java に 761<->760（1.19.3 <-> 1.19.1/1.19.2）の翻訳を
機械生成で差し込む。

762<->761 の節（Chunk Data から Set Creative Slot まで）を丸ごと写し、
番号を 762->761、761->760 へずらす。構造が同一のパケット（チャンク・
ブロック・スロット・エンティティ・パーティクル等）はこの機械変換だけで
済む。構造が違う音（Sound Effect／Entity Sound Effect の Holder）と
パーティクルのデータ番号だけは後から手で直す（このスクリプト内の
FIXUPS で行う）。

使い方: python3 tools/gen-760-chunk.py
冪等：translateChunkData761To760 が既にあれば何もしない。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHUNK = ROOT / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "ChunkTranslator.java"
TABLES = ROOT / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "RegistryTables760.java"
REMAPPER = ROOT / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java"
DATA = Path.home() / "workspace" / "mcdata760"
IDS_JSON = DATA / "packet-ids-760.json"

# メソッド名の断片 -> PacketIds の enum 名（コメントの ID 付け替え用）。
METHOD_TO_PACKET = {
    "ChunkData": "PLAY_CHUNK_DATA",
    "BlockChange": "PLAY_BLOCK_CHANGE",
    "MultiBlockChange": "PLAY_MULTI_BLOCK_CHANGE",
    "BlockEntityData": "PLAY_BLOCK_ENTITY_DATA",
    "SetSlot": "PLAY_SET_SLOT",
    "WindowItems": "PLAY_WINDOW_ITEMS",
    "SetCooldown": "PLAY_SET_COOLDOWN",
    "EntityEquipment": "PLAY_ENTITY_EQUIPMENT",
    "TradeList": "PLAY_TRADE_LIST",
    "OpenWindow": "PLAY_OPEN_WINDOW",
    "BlockAction": "PLAY_BLOCK_ACTION",
    "NamedEntitySpawn": "PLAY_NAMED_ENTITY_SPAWN",
    "SpawnEntity": "PLAY_SPAWN_ENTITY",
    "EntityMetadata": "PLAY_ENTITY_METADATA",
    "NamedSoundEffect": "PLAY_NAMED_SOUND_EFFECT",
    "EntitySoundEffect": "PLAY_ENTITY_SOUND_EFFECT",
    "SoundEffect": "PLAY_SOUND_EFFECT",
    "StopSound": "PLAY_STOP_SOUND",
    "WorldParticles": "PLAY_WORLD_PARTICLES",
    "EntityEffect": "PLAY_ENTITY_EFFECT",
    "WindowClick": "PLAY_WINDOW_CLICK",
    "SetCreativeSlot": "PLAY_SET_CREATIVE_SLOT",
}

HEADER_760 = """    // ------------------------------------------------------------------
    // 761 ⇔ 760（1.19.3 ⇔ 1.19.1／1.19.2）
    // パケットIDが違うため、呼び出し側（ProxySession）が ID を付け替える。
    // ペイロードの構造差は無い。Chunk Data／Update Light の trustEdges、
    // Multi Block Change の suppressLightUpdates は 760・761 ともに同じ
    // 位置にある。Open Sign／Update Sign の isFrontText は 760・761 ともに
    // 無く、763 にだけある。レジストリは RegistryRemapper の 761⇔760 の表で
    // 直接付け替える。NBT は 760・761 ともに名前付きのため、スロットや
    // ブロックエンティティの NBT はそのまま写す。エンティティメタデータの
    // variant 系は 760・761 で形式が同一のため落とさない（765→763 では
    // 落としている）。パーティクルIDは 760・761 で同一順序（93件）のため
    // 番号は付け替えない。
    // 761 にしか無い Feature Flags／Player Info Remove／Add／Remove
    // Resource Pack／Chunk Biomes／Bundle Delimiter／Damage Event／
    // Hurt Animation／Profileless Chat は 760 には送らない（呼び出し側で
    // 落とす）。761 の Spawn Entity（player）は 760 では Named Entity
    // Spawn（0x02）へ変換する（呼び出し側で行う）。音だけ構造が違う
    // （761 は Holder、760 は音IDの VarInt）ため Sound Effect／Entity
    // Sound Effect は専用の変換を使う。
    // ------------------------------------------------------------------

    /** 760 の falling_block のエンティティ種別ID（29）。761 は 30。 */
    static final int FALLING_BLOCK_760 = 29;

    /** 760 の player のエンティティ種別ID（116）。761 は 117。 */
    static final int PLAYER_ENTITY_TYPE_760 = 116;

"""

SOUND_HOLDER_760 = """
    /** 音の Holder（761）→ 音ID（760）に読んで書き直す。inline（名前）の場合は 760 のレジストリで名前を引き、無ければ GENERIC_SOUND_760 にする。range は 760 には無いため読んで落とす。 */
    private static void translateSoundHolder761To760(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            String name = RegistryRemapper.mapSoundName761To760(reader.readString());
            boolean hasRange = reader.readBoolean();
            if (hasRange) {
                reader.readFloat();
            }
            int id = RegistryRemapper.soundIdForName760(name);
            writer.writeVarInt(id < 0 ? RegistryRemapper.GENERIC_SOUND_760 : id);
            return;
        }
        writer.writeVarInt(RegistryRemapper.mapSound761To760(holder - 1));
    }

    /** 音ID（760）→ 音の Holder（761）に読んで書き直す。 */
    private static void translateSoundHolder760To761(Reader reader, Writer writer) {
        int soundId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapSound760To761(soundId) + 1);
    }

    /** Entity Sound Effect の音の Holder（761）→ 音ID（760）。inline（resource）の場合は名前で引く。range は 760 には無いため読んで落とす。 */
    private static void translateEntitySoundHolder761To760(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            String name = RegistryRemapper.mapSoundName761To760(reader.readString());
            if (reader.readBoolean()) { // range（option f32）
                reader.readFloat();
            }
            int id = RegistryRemapper.soundIdForName760(name);
            writer.writeVarInt(id < 0 ? RegistryRemapper.GENERIC_SOUND_760 : id);
            return;
        }
        writer.writeVarInt(RegistryRemapper.mapSound761To760(holder - 1));
    }

    /** Entity Sound Effect の音ID（760）→ 音の Holder（761）。 */
    private static void translateEntitySoundHolder760To761(Reader reader, Writer writer) {
        int soundId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapSound760To761(soundId) + 1);
    }

    /** Named Sound Effect（760 0x17）→ Sound Effect（761）。名前を Holder の inline 形式にする（range 無し）。 */
    public static byte[] translateNamedSoundEffect760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String name = reader.readString();
            int category = reader.readVarInt();
            int x = reader.readInt();
            int y = reader.readInt();
            int z = reader.readInt();
            float volume = reader.readFloat();
            float pitch = reader.readFloat();
            long seed = reader.readLong();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(0); // inline
            writer.writeString(RegistryRemapper.mapSoundName760To761(name));
            writer.writeBoolean(false); // range 無し
            writer.writeVarInt(category);
            writer.writeInt(x);
            writer.writeInt(y);
            writer.writeInt(z);
            writer.writeFloat(volume);
            writer.writeFloat(pitch);
            writer.writeLong(seed);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Named Sound Effect を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
"""


def dance(text):
    """762 -> 761、761 -> 760 へずらす（プレースホルダ経由）。"""
    text = text.replace("762", "\x00762\x00")
    text = text.replace("761", "760")
    return text.replace("\x00762\x00", "761")


def fix_particle(text):
    """translateParticle760 のデータ番号を直す。760・761 はパーティクルIDが
    同一順序のため、両方向とも 761 の番号（sculk_charge=30、item=39、
    vibration=40、shriek=92）を使う。機械変換では to760 側に 762 の番号
    （33／42／43／95）が残ってしまうため置き換える。"""
    start = text.index("private static void translateParticle760(")
    end = text.index("\n    }\n", start) + len("\n    }\n")
    old = text[start:end]
    new = old.replace(
        "     * データを持つ粒子の番号は 760 と 761 で違うため、読み側の番号で分岐する。\n"
        "     * 761：block=2、block_marker=3、falling_dust=25、dust=14、\n"
        "     * dust_color_transition=15、sculk_charge=33、item=42、vibration=43、shriek=95。\n"
        "     * 760：sculk_charge=30、item=39、vibration=40、shriek=92（他は同じ）。\n"
        "     * 761 にしか無い cherry_leaves 系3種は white_ash へ写す（RegistryRemapper）。",
        "     * データを持つ粒子の番号は 760・761 で同一のため、両方向とも同じ番号で分岐する。\n"
        "     * block=2、block_marker=3、falling_dust=25、dust=14、\n"
        "     * dust_color_transition=15、sculk_charge=30、item=39、vibration=40、shriek=92。")
    # to760 側（if (to760) ブロック）の case 番号を 762 のものから 761 のものへ。
    new = new.replace("case 33 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転",
                      "case 30 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転")
    new = new.replace("case 42 -> translateSlot761To760(reader, writer); // item",
                      "case 39 -> translateSlot761To760(reader, writer); // item")
    new = new.replace("case 43 -> { // vibration：位置種別＋行き先＋tick 数",
                      "case 40 -> { // vibration：位置種別＋行き先＋tick 数")
    new = new.replace("case 95 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数",
                      "case 92 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数")
    assert new != old, "translateParticle760 の置き換えが効いていない"
    return text[:start] + new + text[end:]


def fix_sound_holders(text):
    """機械変換された translateSoundHolder761To760／760To761 を ID 変換版に
    置き換え、Entity Sound Effect 側は専用メソッドを使うようにする。
    旧 translateSoundHolder760To761（761->762 をずらしたもの）は削除する
    （SOUND_HOLDER_760 の中に新しい定義があるため）。"""
    start1 = text.index("    private static void translateSoundHolder761To760(")
    end1 = text.index("\n    }\n", start1) + len("\n    }\n")
    start2 = text.index("    private static void translateSoundHolder760To761(", end1)
    end2 = text.index("\n    }\n", start2) + len("\n    }\n")
    # 2つ目のメソッドの javadoc ごと削除する。
    javadoc2 = text.rindex("    /**", 0, start2)
    text = text[:start1] + SOUND_HOLDER_760.strip("\n") + text[end1:javadoc2] + text[end2:]
    # Entity Sound Effect の2メソッドだけ、呼び出しを専用版に変える。
    for method in ("translateEntitySoundEffect761To760", "translateEntitySoundEffect760To761"):
        mstart = text.index(f"public static byte[] {method}(")
        mend = text.index("\n    }\n", mstart) + len("\n    }\n")
        body = text[mstart:mend]
        body = body.replace("translateSoundHolder761To760(reader, writer);",
                            "translateEntitySoundHolder761To760(reader, writer);")
        body = body.replace("translateSoundHolder760To761(reader, writer);",
                            "translateEntitySoundHolder760To761(reader, writer);")
        text = text[:mstart] + body + text[mend:]
    return text


def fix_comment_ids(text, ids):
    """生成された節の javadoc 内の「761 0x..」「760 0x..」を正しい ID に直す。
    各 javadoc の直後のメソッド名からパケットを引き、packet-ids-760.json
    の id761／id760 を使う。"""
    lines = text.split("\n")
    out = []
    # 各行について「次に出てくるメソッド名」を先読みして決める。
    method_for_line = [None] * len(lines)
    current = None
    for i in range(len(lines) - 1, -1, -1):
        m = re.search(r"public static byte\[\] (translate\w+?)(761To760|760To761)\(", lines[i])
        if m:
            current = m.group(1)
        method_for_line[i] = current
    for i, line in enumerate(lines):
        method = method_for_line[i]
        if method is None:
            out.append(line)
            continue
        packet = None
        for frag, pname in METHOD_TO_PACKET.items():
            if frag in method:
                packet = pname
                break
        if packet is None or packet not in ids:
            out.append(line)
            continue
        id761, id760 = ids[packet]["id761"], ids[packet]["id760"]
        if id761 is None or id760 is None:
            out.append(line)
            continue
        line = re.sub(r"(?<=761 )0x[0-9A-Fa-f]+", f"0x{id761:02X}", line)
        line = re.sub(r"(?<=760 )0x[0-9A-Fa-f]+", f"0x{id760:02X}", line)
        out.append(line)
    return "\n".join(out)


def main():
    src = CHUNK.read_text(encoding="utf-8")
    if "translateChunkData761To760" in src:
        print("skip（既存）: ChunkTranslator の 761<->760 翻訳は既にある")
    else:
        marker = "    // 762 ⇔ 761（1.19.4 ⇔ 1.19.3）"
        start = src.index(marker)
        # 節の開始は直前の区切り線。
        sep = src.rindex("    // ------------------------------------------------------------------", 0, start)
        # 節の終わりはファイル末尾のクラスの閉じ括弧。
        end = src.rindex("\n}\n")
        section = src[sep:end]
        gen = dance(section)
        # ヘッダーコメントと定数を正しいものに置き換える。
        # dance 後のヘッダー末尾を探す（定数定義の直後の javadoc）。
        const_end = gen.index("    /**\n     * Spawn Entity（761形式）の種別IDだけ読む。")
        gen = HEADER_760 + gen[const_end:]
        gen = fix_particle(gen)
        gen = fix_sound_holders(gen)
        ids = json.loads(IDS_JSON.read_text(encoding="utf-8"))
        gen = fix_comment_ids(gen, ids)
        # 挿入：旧節の直後（ファイル末尾の閉じ括弧の直前）。
        new_src = src[:end] + "\n" + gen + src[end:]
        CHUNK.write_text(new_src, encoding="utf-8")
        print("ChunkTranslator.java に 761<->760 の翻訳を差し込んだ。")

    # SOUND_NAMES_760 を RegistryTables760.java に追加し、lookup を
    # RegistryRemapper.java に追加する。
    tables = TABLES.read_text(encoding="utf-8")
    if "SOUND_NAMES_760" not in tables:
        sounds = json.loads((DATA / "mapping-1.19.json").read_text(encoding="utf-8"))["sounds"]
        entries = ",\n            ".join(f'"{s}"' for s in sounds)
        addition = (
            "\n    /** 760 の音の名前一覧（ViaVersion mapping-1.19.json の順序＝レジストリ番号順）。\n"
            "     * inline の音名を 760 の音IDに引くために使う。 */\n"
            "    public static final String[] SOUND_NAMES_760 = {\n            "
            + entries
            + ",\n    };\n"
        )
        idx = tables.rindex("\n}")
        tables = tables[:idx] + addition + tables[idx:]
        TABLES.write_text(tables, encoding="utf-8")
        print(f"RegistryTables760.java に SOUND_NAMES_760（{len(sounds)} 件）を追加した。")
    remapper = REMAPPER.read_text(encoding="utf-8")
    if "soundIdForName760" not in remapper:
        anchor = "    /** 761のブロック状態ID → 760のブロック状態ID。"
        method = (
            "    /**\n"
            "     * 音の名前から 760 の音IDを引く。inline の音名（Sound Effect／Entity Sound\n"
            "     * Effect の Holder が 0 の場合）を 760 の音IDに変換するために使う。\n"
            "     * 無ければ -1 を返す（呼び出し側は GENERIC_SOUND_760 にする）。\n"
            "     */\n"
            "    public static int soundIdForName760(String name) {\n"
            "        for (int i = 0; i < RegistryTables760.SOUND_NAMES_760.length; i++) {\n"
            "            if (RegistryTables760.SOUND_NAMES_760[i].equals(name)) {\n"
            "                return i;\n"
            "            }\n"
            "        }\n"
            "        return -1;\n"
            "    }\n\n"
        )
        idx = remapper.index(anchor)
        remapper = remapper[:idx] + method + remapper[idx:]
        REMAPPER.write_text(remapper, encoding="utf-8")
        print("RegistryRemapper.java に soundIdForName760 を追加した。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
