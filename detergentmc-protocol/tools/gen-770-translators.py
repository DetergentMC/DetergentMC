#!/usr/bin/env python3
"""ChunkTranslator / PlayTranslator の 769 節から 770 節を生成する。

方針: 769 節を抜き出して shift()（gen-770-proxysession.py のもの）で
1段ずらし、FIXES（手書きの置き換え）を当て、ファイル末尾の最終 `}` の
直前に挿入する。PlayTranslator の 770 節は手書きブロックで作る
（Player Info 等は769と770で同一形式のため素通しにする）。
"""
import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen770ps", _HERE / "gen-770-proxysession.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift = _mod.shift

ROOT = Path("/home/hatch/workspace/detergentmc")
CT = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java"
PT = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/PlayTranslator.java"


def extract_section(path, start_marker, end_marker):
    lines = path.read_text(encoding="utf-8").split("\n")
    start = None
    end = None
    for i, line in enumerate(lines):
        if start is None and start_marker in line:
            # 1行上の区切り線から開始する。
            start = i - 1
            assert "---" in lines[start], f"区切り線が見つからない: {lines[start]!r}"
        if start is not None and end_marker in line:
            end = i - 1  # 区切り線の直前の空行まで
            break
    assert start is not None and end is not None, (start_marker, end_marker)
    return "\n".join(lines[start:end]), start, end


def extract_section_to_eof(path, start_marker):
    """開始マーカーからファイル末尾の最終 `}` の直前まで。"""
    lines = path.read_text(encoding="utf-8").split("\n")
    start = None
    for i, line in enumerate(lines):
        if start_marker in line:
            start = i - 1
            assert "---" in lines[start], f"区切り線が見つからない: {lines[start]!r}"
            break
    assert start is not None, start_marker
    # 末尾の `}`（最終行）の直前の空行まで。
    end = len(lines) - 1
    assert lines[end] == "}" or lines[end] == "", lines[end]
    while lines[end - 1].strip() == "":
        end -= 1
    end -= 1  # 最後のメソッドの直後の空行を除く
    return "\n".join(lines[start:end]), start, end


def apply_fixes(text, fixes, label):
    for old, new in fixes:
        n = text.count(old)
        if n != 1:
            print(f"[{label}] FIX 失敗（一致 {n} 件）:\n---OLD---\n{old}\n---", file=sys.stderr)
            raise SystemExit(1)
        text = text.replace(old, new)
    return text


# （main はファイル末尾に定義する）

# ----------------------------------------------------------------------
# 置き換えヘルパー
# ----------------------------------------------------------------------

def replace_method(text, signature, new_text, label):
    """signature を含むメソッド1個を、直前の javadoc ごと new_text に置き換える。
    signature はメソッド名を含む一意の文字列（例: "translateSlot769To770(Reader"）。"""
    idx = text.find(signature)
    if idx < 0:
        raise SystemExit(f"[{label}] メソッドが見つからない: {signature}")
    if text.find(signature, idx + 1) >= 0:
        raise SystemExit(f"[{label}] メソッドが2件以上ある: {signature}")
    # メソッド開始行の先頭を探す。
    line_start = text.rfind("\n", 0, idx) + 1
    # 直前の javadoc（/** ... */）・行コメント・空行をさかのぼって含める。
    # 空行の前で止める（空行自体は text[:start] に残し、new_text 側の
    # 先頭の改行は除去する）。これで置き換え後のメソッド間に空白行が残る。
    # `//` 行は javadoc に直結しているもの（空行なし）だけ含める。
    start = line_start
    while True:
        prev_end = start - 1
        if prev_end <= 0:
            break
        prev_start = text.rfind("\n", 0, prev_end) + 1
        prev_line = text[prev_start:prev_end]
        stripped = prev_line.strip()
        if stripped == "":
            break
        if stripped.startswith("*") or stripped.startswith("/**") \
                or stripped.startswith("*/"):
            start = prev_start
            continue
        if stripped.startswith("//"):
            start = prev_start
            continue
        break
    if text[:start].endswith("\n\n"):
        new_text = new_text.lstrip("\n")
    # シグネチャ以後の最初の `{` から対応する `}` までを数える。
    brace = text.find("{", idx)
    assert brace > 0, signature
    depth = 0
    i = brace
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                break
        i += 1
    assert depth == 0, signature
    end = i + 1
    # 末尾の改行は残す。
    return text[:start] + new_text + text[end:]


def fix(text, old, new, label):
    n = text.count(old)
    if n != 1:
        print(f"[{label}] FIX 失敗（一致 {n} 件）:\n---OLD---\n{old}\n---", file=sys.stderr)
        raise SystemExit(1)
    return text.replace(old, new)


# 成分種別の対応表（769 kind -> 770 kind。-1 は 770 に無く落とす）。
KIND_769_TO_770 = {
    **{i: i for i in range(0, 15)},
    15: -1, 16: 15,
    17: 16, 18: 17, 19: 18, 20: 19, 21: 20, 22: 21, 23: 22, 24: 23, 25: 24, 26: 25,
    27: 27, 28: 28, 29: 29, 30: 30, 31: 31, 32: 32,
    33: 34, 34: 35, 35: 36, 36: 37, 37: 38, 38: 39, 39: 40, 40: 41, 41: 42,
    42: 44, 43: 45, 44: 46, 45: 47, 46: 48, 47: 49, 48: 50, 49: 51, 50: 52,
    51: 54, 52: 55, 53: 57, 54: 58, 55: 59, 56: 60, 57: 61, 58: 62, 59: 63,
    60: 64, 61: 65, 62: 66, 63: 67, 64: 68, 65: 69, 66: 70,
}
assert len(KIND_769_TO_770) == 67
KIND_770_TO_769 = {v: k for k, v in KIND_769_TO_770.items() if v >= 0}
assert len(KIND_770_TO_769) == 66  # 15（hide_additional_tooltip）が落ちる分


def java_int_array(name, values, per_line=16):
    lines = []
    for i in range(0, len(values), per_line):
        lines.append("            " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
    body = "\n".join(lines)
    return f"    private static final int[] {name} = {{\n{body}\n    }};\n"


KIND_769_TO_770_JAVA = java_int_array(
    "KIND_769_TO_770", [KIND_769_TO_770[i] for i in range(67)])
KIND_770_TO_769_JAVA = java_int_array(
    "KIND_770_TO_769",
    [KIND_770_TO_769.get(i, -1) for i in range(96)])


# ----------------------------------------------------------------------
# ChunkTranslator 770 節の文字列 FIXES（シフト済みテキストに適用）
# ----------------------------------------------------------------------

CHUNK_STRING_FIXES = [
    # ヘッダーコメント。
    ("""    // 769 ⇔ 770（1.21.5）
    // 成分種別（67件）は769と770で同一の番号・形式のため KIND 表は要らない。
    // 変わるのは中身のレジストリIDだけ（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）。バイオームは pale_garden が
    // 39番に挿入され、40番以降が1つずれる。エンティティは
    // creaking_transient（769=30）の削除で31番以降が1つずれる。
    // trail（769=46→770=47）は770で末尾に duration varint が増えた。
    // pale_oak_leaves（770=34）は769に無いため770→769では落とす。""",
     """    // 769 ⇔ 770（1.21.5）
    // 成分種別は769が67件、770が96件。番号が大きくずれるため KIND 表
    // （KIND_769_TO_770／KIND_770_TO_769）で付け替える。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の形式（tooltip_display への
    // 統合、showTooltip／showInTooltip の削除、tool の
    // canDestroyBlocksInCreative 追加等）。バイオーム・エンチャント・属性・
    // メニュー・ダメージ種別は同一のため付け替えない。エンティティは
    // potion（769=99）が splash_potion（770=99）と lingering_potion
    // （770=100）に分かれる。tinted_leaves（770=35）と firefly
    // （770=113）は769に無いため770→769では落とす。
    // チャンクの heightmap は NBT から直接の配列に変わった。"""),
    # 成分種別の件数＋KIND 表の挿入。
    ("""    /** 成分種別の件数（768・769・770 とも 67 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_770 = 67;""",
     """    /** 成分種別の件数（770 は 96 件。769 は 67 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_770 = 96;

    /**
     * 成分種別の対応表（769 kind → 770 kind）。-1 は 770 に無いため落とす
     * （15＝hide_additional_tooltip）。16（hide_tooltip）は 15
     * （tooltip_display）になる。
     */
""" + KIND_769_TO_770_JAVA + """
    /**
     * 成分種別の対応表（770 kind → 769 kind）。-1 は 769 に無いため落とす。
     * 15（tooltip_display）は内容次第で hide_tooltip（16）か
     * hide_additional_tooltip（15）になるためここでは -1 にしてある。
     */
""" + KIND_770_TO_769_JAVA),
    # パーティクル定数の値。
    ("""    /** 770の trail 粒子のID（vv-1.21.5.json の particles 順）。 */
    private static final int PARTICLE_770_TRAIL = 47;
    /** 770の block_crumble 粒子のID。 */
    private static final int PARTICLE_770_BLOCK_CRUMBLE = 111;
    /** 770の pale_oak_leaves 粒子のID（769には無い）。 */
    private static final int PARTICLE_770_PALE_OAK_LEAVES = 34;""",
     """    /** 770の trail 粒子のID（vv-1.21.5.json の particles 順）。 */
    private static final int PARTICLE_770_TRAIL = 48;
    /** 770の block_crumble 粒子のID。 */
    private static final int PARTICLE_770_BLOCK_CRUMBLE = 112;
    /** 770の pale_oak_leaves 粒子のID（769・770とも 34）。 */
    private static final int PARTICLE_770_PALE_OAK_LEAVES = 34;
    /** 770の tinted_leaves 粒子のID（769には無い）。 */
    private static final int PARTICLE_770_TINTED_LEAVES = 35;
    /** 770の firefly 粒子のID（769には無い）。 */
    private static final int PARTICLE_770_FIREFLY = 113;"""),
    # trail の既定 duration は不要（769の trail にも duration がある）。
    ("""    /**
     * 769→770の trail に補う duration（tick）。769の trail には
     * duration が無いため 20 tick（1秒）で補う。近似値であり実機未確認。
     */
    private static final int TRAIL_DURATION_DEFAULT_TICKS = 20;

""", ""),
    # Spawn Entity：falling_block は 769・770とも 49。
    ("""            int mappedObjectData = type == 50
                    ? RegistryRemapper.mapBlockState769To770(objectData)
                    : objectData;""",
     """            int mappedObjectData = type == 49
                    ? RegistryRemapper.mapBlockState769To770(objectData)
                    : objectData;"""),
    # パーティクル（769→770）の case 番号：769側のIDに合わせる。
    ("""            case 1, 2, 28, 106 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockState769To770(reader.readVarInt()));""",
     """            case 1, 2, 28, 107 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockState769To770(reader.readVarInt()));"""),
    ("""            case 35 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転（1個の float）
            case 44 -> translateSlot769To770(reader, writer); // item
            case 45 -> translateVibration(reader, writer); // vibration（同一形式）
            case 100 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 46 -> { // trail：target vec3f64＋color i32＋duration（769には無いため既定値で補う）
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeInt(reader.readInt());
                writer.writeVarInt(TRAIL_DURATION_DEFAULT_TICKS);
            }
            case 110 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockState769To770(reader.readVarInt()));""",
     """            case 36 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転（1個の float）
            case 45 -> translateSlot769To770(reader, writer); // item
            case 46 -> translateVibration(reader, writer); // vibration（同一形式）
            case 101 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 47 -> { // trail：target vec3f64＋color i32＋duration（769・770ともある）
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeInt(reader.readInt());
                writer.writeVarInt(reader.readVarInt()); // duration
            }
            case 111 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockState769To770(reader.readVarInt()));"""),
    # パーティクル（770→769）の case 番号：770側のIDに合わせる。
    ("""            case 1, 2, 28, 107 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp770(reader.readVarInt()));""",
     """            case 1, 2, 28, 108 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp770(reader.readVarInt()));"""),
    ("""            case 36 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転（1個の float）
            case 45 -> translateSlot770To769(reader, writer); // item
            case 46 -> translateVibration(reader, writer); // vibration（同一形式）
            case 101 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 47 -> { // trail：target vec3f64＋color i32＋duration（duration を落とす）
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeInt(reader.readInt());
                reader.readVarInt(); // duration
            }
            case 111 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp770(reader.readVarInt()));
            case 34 -> throw new IllegalArgumentException("769に無い粒子です（770 id=" + particleId + "）。");""",
     """            case 37 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転（1個の float）
            case 46 -> translateSlot770To769(reader, writer); // item
            case 47 -> translateVibration(reader, writer); // vibration（同一形式）
            case 102 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 48 -> { // trail：target vec3f64＋color i32＋duration（769・770ともある）
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeInt(reader.readInt());
                writer.writeVarInt(reader.readVarInt()); // duration
            }
            case 112 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp770(reader.readVarInt()));"""),
    # 狼バリアントの Holder（shift したもの）は 770 の形式（ただの varint）と
    # 合わないため削除する。正しい版は translateMetadataValue769To770／
    # 770To769 の置き換えで定義する（直書きは落とす）。IDSet の2メソッドは
    # この2メソッドからしか呼ばれないため一緒に削除する。
    ("""    /**
     * 狼バリアントの Holder を769→770向けに変換する。直書きはテクスチャ3文字列＋
     * バイオームの IDSet。770では pale_garden 挿入でバイオームIDがずれるため
     * IDSet の中身を付け替える。参照IDは同一のためそのまま写す。
     */
    private static void translateWolfVariantHolder769To770(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return;
        }
        writer.writeString(reader.readString()); // wildTexture
        writer.writeString(reader.readString()); // tameTexture
        writer.writeString(reader.readString()); // angryTexture
        translateIdSetBiomes769To770(reader, writer); // biome
    }

    /** 狼バリアントの Holder を770→769向けに変換する。直書きのバイオーム IDSet を付け替える。 */
    private static void translateWolfVariantHolder770To769(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return;
        }
        writer.writeString(reader.readString()); // wildTexture
        writer.writeString(reader.readString()); // tameTexture
        writer.writeString(reader.readString()); // angryTexture
        translateIdSetBiomes770To769(reader, writer); // biome
    }

    /** バイオームの IDSet を769→770向けに変換する（0 はタグ名の文字列）。 */
    private static void translateIdSetBiomes769To770(Reader reader, Writer writer) {
        int value = reader.readVarInt();
        writer.writeVarInt(value);
        if (value == 0) {
            writer.writeString(reader.readString());
            return;
        }
        for (int i = 0; i < value - 1; i++) {
            writer.writeVarInt(mapBiome769To770(reader.readVarInt()));
        }
    }

    /** バイオームの IDSet を770→769向けに変換する（0 はタグ名の文字列）。 */
    private static void translateIdSetBiomes770To769(Reader reader, Writer writer) {
        int value = reader.readVarInt();
        writer.writeVarInt(value);
        if (value == 0) {
            writer.writeString(reader.readString());
            return;
        }
        for (int i = 0; i < value - 1; i++) {
            writer.writeVarInt(mapBiomeUp770(reader.readVarInt()));
        }
    }

""", ""),
]


# ----------------------------------------------------------------------
# ChunkTranslator 770 節のメソッド置き換え
# ----------------------------------------------------------------------

CHUNK_METHOD_REPLACEMENTS = {
"translateWorldParticles770To769(byte[] payload)": """    /**
     * World Particles（770 → 769）。alwaysShow は770・769ともにあるため
     * そのまま通す。770にしかない粒子（tinted_leaves・firefly）の
     * ときはパケットごと落とす（{@code null}）。
     */
    public static byte[] translateWorldParticles770To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean longDistance = reader.readBoolean();
            boolean alwaysShow = reader.readBoolean(); // alwaysShow（770・769ともにある）
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float offsetX = reader.readFloat();
            float offsetY = reader.readFloat();
            float offsetZ = reader.readFloat();
            float speed = reader.readFloat();
            int count = reader.readInt();
            Writer particleWriter = new Writer();
            if (!translateParticle770To769(reader, particleWriter)) {
                LOG.warning("World Particles の粒子が769に無いため落とします。");
                return null;
            }
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(longDistance);
            writer.writeBoolean(alwaysShow); // alwaysShow（769にもある。そのまま通す）
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(offsetX);
            writer.writeFloat(offsetY);
            writer.writeFloat(offsetZ);
            writer.writeFloat(speed);
            writer.writeInt(count);
            writer.writeBytes(particleWriter.bytes());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("World Particles を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateWorldParticles769To770(byte[] payload)": """    /**
     * World Particles（769 → 770）。alwaysShow は769・770ともにあるため
     * そのまま通す。粒子は769→770に変換する。
     */
    public static byte[] translateWorldParticles769To770(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean longDistance = reader.readBoolean();
            boolean alwaysShow = reader.readBoolean(); // alwaysShow（769・770ともにある）
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float offsetX = reader.readFloat();
            float offsetY = reader.readFloat();
            float offsetZ = reader.readFloat();
            float speed = reader.readFloat();
            int count = reader.readInt();
            Writer particleWriter = new Writer();
            translateParticle769To770(reader, particleWriter);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(longDistance);
            writer.writeBoolean(alwaysShow); // alwaysShow（770にもある。そのまま通す）
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(offsetX);
            writer.writeFloat(offsetY);
            writer.writeFloat(offsetZ);
            writer.writeFloat(speed);
            writer.writeInt(count);
            writer.writeBytes(particleWriter.bytes());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("World Particles を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"mapBiome769To770(int id)": """    /**
     * 769のバイオームID→770。769と770でバイオームは65種・同じ並びのため
     * そのまま返す。
     */
    private static int mapBiome769To770(int id) {
        return id;
    }
""",
"mapBiomeUp770(int id)": """    /**
     * 770のバイオームID→769。769と770でバイオームは65種・同じ並びのため
     * そのまま返す。
     */
    private static int mapBiomeUp770(int id) {
        return id;
    }
""",
"translateSlot769To770(Reader reader, Writer writer)": """    /** スロット（count＋itemId＋added／removed の成分1個ずつ）を769→770向けに変換する。成分種別は KIND 表で付け替える。 */
    public static void translateSlot769To770(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        if (count <= 0) {
            writer.writeVarInt(count);
            return;
        }
        writer.writeVarInt(count);
        int itemId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapItem769To770(itemId));
        translateSlotComponents769To770(reader, writer);
    }

    /** スロットの added／removed 成分部分だけを769→770向けに変換する。 */
    private static void translateSlotComponents769To770(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind769 = reader.readVarInt();
            int kind770 = mapComponentKind769To770(kind769);
            if (kind770 < 0) {
                skipComponentData769(reader, kind769);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent769To770(reader, component, kind769)) {
                continue;
            }
            kept.writeVarInt(kind770);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int removed = reader.readVarInt();
        Writer keptRemoved = new Writer();
        int keptRemovedCount = 0;
        for (int i = 0; i < removed; i++) {
            int kind770 = mapComponentKind769To770(reader.readVarInt());
            if (kind770 < 0) {
                continue;
            }
            keptRemoved.writeVarInt(kind770);
            keptRemovedCount++;
        }
        writer.writeVarInt(keptRemovedCount);
        writer.writeBytes(keptRemoved.bytes());
    }

    /** 769の成分種別を770の種別にする。770に無いものは -1（落とす）。 */
    private static int mapComponentKind769To770(int kind769) {
        if (kind769 < 0 || kind769 >= KIND_769_TO_770.length) {
            throw new IllegalArgumentException("未知の成分種別です（769）: " + kind769);
        }
        return KIND_769_TO_770[kind769];
    }

    /** 770の成分種別を769の種別にする。769に無いものは -1（落とす）。 */
    private static int mapComponentKind770To769(int kind770) {
        if (kind770 < 0 || kind770 >= KIND_770_TO_769.length) {
            throw new IllegalArgumentException("未知の成分種別です（770）: " + kind770);
        }
        return KIND_770_TO_769[kind770];
    }

    /** hide_tooltip を770の tooltip_display に変換する（hideTooltip=true、隠す成分なし）。 */
    private static void translateHideTooltip769To770(Reader reader, Writer writer) {
        // 769の hide_tooltip は void（中身なし）。
        writer.writeBoolean(true); // hideTooltip
        writer.writeVarInt(0); // hiddenComponents（空）
    }

    /**
     * 770の tooltip_display を読み、対応する769の種別を返す。
     * hideTooltip=true なら hide_tooltip（16）、hiddenComponents が
     * 1個以上あるなら hide_additional_tooltip（15）、どちらでも無ければ
     * -1（落とす）。769の両種別は void のため中身は書かない。
     */
    private static int translateTooltipDisplay770To769(Reader reader) {
        boolean hideTooltip = reader.readBoolean();
        int hidden = reader.readVarInt();
        for (int i = 0; i < hidden; i++) {
            reader.readVarInt();
        }
        if (hideTooltip) {
            return 16;
        }
        if (hidden > 0) {
            return 15;
        }
        return -1;
    }

    /** unbreakable を769→770向けに変換する。769の bool を読み、770は void のため何も書かない。 */
    private static void translateUnbreakable769To770(Reader reader, Writer writer) {
        reader.readBoolean(); // 769の unbreakable（bool）
    }

    /** unbreakable を770→769向けに変換する。770は void のため true を書く。 */
    private static void translateUnbreakable770To769(Reader reader, Writer writer) {
        writer.writeBoolean(true);
    }

    /** dyed_color を769→770向けに変換する。769は {色 i32, showTooltip bool}、770は色 i32 だけ。 */
    private static void translateDyedColor769To770(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
        reader.readBoolean(); // showTooltip（770には無い）
    }

    /** dyed_color を770→769向けに変換する。色 i32 の後に showTooltip=true を書く。 */
    private static void translateDyedColor770To769(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
        writer.writeBoolean(true); // showTooltip
    }
""",
"translateSlot770To769(Reader reader, Writer writer)": """    /** スロット（count＋itemId＋added／removed の成分1個ずつ）を770→769向けに変換する。成分種別は KIND 表で付け替える。 */
    public static void translateSlot770To769(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        if (count <= 0) {
            writer.writeVarInt(count);
            return;
        }
        writer.writeVarInt(count);
        int itemId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapItemUp770(itemId));
        translateSlotComponents770To769(reader, writer);
    }

    /** スロットの added／removed 成分部分だけを770→769向けに変換する。 */
    private static void translateSlotComponents770To769(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind770 = reader.readVarInt();
            if (kind770 == 15) {
                // tooltip_display は内容に応じて 769 の種別を決める。
                int kind769 = translateTooltipDisplay770To769(reader);
                if (kind769 < 0) {
                    continue;
                }
                kept.writeVarInt(kind769);
                keptCount++;
                continue;
            }
            int kind769 = mapComponentKind770To769(kind770);
            if (kind769 < 0) {
                skipComponentData770(reader, kind770);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent770To769(reader, component, kind770)) {
                continue;
            }
            kept.writeVarInt(kind769);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int removed = reader.readVarInt();
        Writer keptRemoved = new Writer();
        int keptRemovedCount = 0;
        for (int i = 0; i < removed; i++) {
            int kind769 = mapComponentKind770To769(reader.readVarInt());
            if (kind769 < 0) {
                continue;
            }
            keptRemoved.writeVarInt(kind769);
            keptRemovedCount++;
        }
        writer.writeVarInt(keptRemovedCount);
        writer.writeBytes(keptRemoved.bytes());
    }
""",
"skipSlot770(Reader reader)": """    /** スロット1個を読み飛ばす（770形式。変換しない成分のスキップ用）。 */
    private static void skipSlot770(Reader reader) {
        int count = reader.readVarInt();
        if (count <= 0) {
            return;
        }
        reader.readVarInt(); // itemId
        int added = reader.readVarInt();
        for (int i = 0; i < added; i++) {
            int kind = reader.readVarInt();
            skipComponentData770(reader, kind);
        }
        int removed = reader.readVarInt();
        for (int i = 0; i < removed; i++) {
            reader.readVarInt();
        }
    }
""",
"skipComponentData770(Reader reader, int kind770)": """    /**
     * 成分データ1個分を読み飛ばす（770形式）。770で形式が変わったものと
     * 新規の種別はここで読み飛ばし、それ以外は種別を769に直して
     * 769の読み飛ばしに委譲する。
     */
    private static void skipComponentData770(Reader reader, int kind770) {
        switch (kind770) {
            case 4 -> { } // unbreakable（void）
            case 15 -> { // tooltip_display
                reader.readBoolean(); // hideTooltip
                int hidden = reader.readVarInt();
                for (int i = 0; i < hidden; i++) {
                    reader.readVarInt();
                }
            }
            case 26 -> { // weapon
                reader.readVarInt(); // itemDamagePerAttack
                reader.readFloat(); // disableBlockingForSeconds
            }
            case 33 -> skipBlocksAttacks770(reader);
            case 43 -> reader.readFloat(); // potion_duration_scale
            case 53 -> skipProvidesTrimMaterial770(reader);
            case 56 -> reader.readString(); // provides_banner_patterns
            case 71 -> ExtraPlayTranslator.skipSoundHolder(reader); // break_sound
            case 86 -> { // chicken/variant
                if (reader.readBoolean()) {
                    reader.readVarInt();
                } else {
                    reader.readString();
                }
            }
            case 89 -> skipPaintingVariantHolder770(reader);
            case 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85,
                    87, 88, 90, 91, 92, 93, 94, 95 -> reader.readVarInt(); // 各種 variant／collar／color
            default -> {
                int kind769 = mapComponentKind770To769(kind770);
                if (kind769 < 0) {
                    throw new IllegalArgumentException("未知の成分種別です（770）: " + kind770);
                }
                skipComponentData769(reader, kind769);
            }
        }
    }

    /** blocks_attacks（770の新規種別 33）を読み飛ばす。 */
    private static void skipBlocksAttacks770(Reader reader) {
        reader.readFloat(); // blockDelaySeconds
        reader.readFloat(); // disableCooldownScale
        int reductions = reader.readVarInt();
        for (int i = 0; i < reductions; i++) {
            reader.readFloat(); // horizontalBlockingAngle
            if (reader.readBoolean()) { // type（option IDSet）
                skipIdSet(reader);
            }
            reader.readFloat(); // base
            reader.readFloat(); // factor
        }
        reader.readFloat(); // itemDamage.threshold
        reader.readFloat(); // itemDamage.base
        reader.readFloat(); // itemDamage.factor
        if (reader.readBoolean()) { // bypassedBy（option string）
            reader.readString();
        }
        if (reader.readBoolean()) { // blockSound（option ItemSoundHolder）
            ExtraPlayTranslator.skipSoundHolder(reader);
        }
        if (reader.readBoolean()) { // disableSound（option ItemSoundHolder）
            ExtraPlayTranslator.skipSoundHolder(reader);
        }
    }

    /** provides_trim_material（770の新規種別 53）を読み飛ばす。 */
    private static void skipProvidesTrimMaterial770(Reader reader) {
        boolean hasHolder = reader.readBoolean();
        if (!hasHolder) {
            reader.readString(); // 材料名
            return;
        }
        int holder = reader.readVarInt();
        if (holder != 0) {
            return;
        }
        // 直書きの ArmorTrimMaterial（770形式）。
        reader.readString(); // assetBase
        int count = reader.readVarInt();
        for (int i = 0; i < count; i++) {
            reader.readString(); // key
            reader.readString(); // value
        }
        reader.readAnonymousNbt(); // description
    }

    /** painting/variant（770の新規種別 89）の Holder を読み飛ばす。 */
    private static void skipPaintingVariantHolder770(Reader reader) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            return;
        }
        reader.readInt(); // width
        reader.readInt(); // height
        reader.readString(); // assetId
        readOptionalAnonymousNbt(reader); // title（option）
        readOptionalAnonymousNbt(reader); // author（option）
    }
""",
"copyComponentOpaque770(Reader reader, Writer writer, int kind770)": """    /** 成分1個の中身だけを写す（770形式。翻訳不要な成分用。種別を769に直して769の写しに委譲する）。 */
    private static void copyComponentOpaque770(Reader reader, Writer writer, int kind770) {
        copyComponentOpaque769(reader, writer, mapComponentKind770To769(kind770));
    }
""",
}

CHUNK_METHOD_REPLACEMENTS.update({
"translateItemCost769To770(Reader reader, Writer writer)": """    /**
     * 取引の入力（ItemCost）1個を769→770向けに変換する。
     * 769の形式は [itemId VarInt][個数 VarInt][追加コンポーネント数][成分]、
     * 770の形式は [itemId][個数][ExactComponentMatcher（成分の配列）] で、
     * 線上では同じ「個数＋成分の列」になる。成分は KIND 表で付け替える。
     * （768→769の翻訳器は誤って「個数＋スロット列」と読んでいたため、
     * 770では正しい形式に直す。768→769も別途修正する。）
     */
    public static void translateItemCost769To770(Reader reader, Writer writer) {
        writer.writeVarInt(RegistryRemapper.mapItem769To770(reader.readVarInt()));
        writer.writeVarInt(reader.readVarInt()); // count
        translateComponentList769To770(reader, writer);
    }

    /** 成分の列（個数 VarInt＋成分1個ずつ）を769→770向けに変換する。 */
    private static void translateComponentList769To770(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind769 = reader.readVarInt();
            int kind770 = mapComponentKind769To770(kind769);
            if (kind770 < 0) {
                skipComponentData769(reader, kind769);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent769To770(reader, component, kind769)) {
                continue;
            }
            kept.writeVarInt(kind770);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateItemCost770To769(Reader reader, Writer writer)": """    /** 取引の入力（ItemCost）1個を770→769向けに変換する。 */
    public static void translateItemCost770To769(Reader reader, Writer writer) {
        writer.writeVarInt(RegistryRemapper.mapItemUp770(reader.readVarInt()));
        writer.writeVarInt(reader.readVarInt()); // count
        translateComponentList770To769(reader, writer);
    }

    /** 成分の列（個数 VarInt＋成分1個ずつ）を770→769向けに変換する。 */
    private static void translateComponentList770To769(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind770 = reader.readVarInt();
            if (kind770 == 15) {
                int kind769 = translateTooltipDisplay770To769(reader);
                if (kind769 < 0) {
                    continue;
                }
                kept.writeVarInt(kind769);
                keptCount++;
                continue;
            }
            int kind769 = mapComponentKind770To769(kind770);
            if (kind769 < 0) {
                skipComponentData770(reader, kind770);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent770To769(reader, component, kind770)) {
                continue;
            }
            kept.writeVarInt(kind769);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateComponent769To770(Reader reader, Writer writer, int kind769)": """    /**
     * 成分1個を769→770向けに変換する。成分種別（kind）は呼び出し側が
     * 付け替えて書くため、ここでは中身だけを書く。落とす成分は {@code false} を返す。
     */
    private static boolean translateComponent769To770(Reader reader, Writer writer, int kind769) {
        switch (kind769) {
            case 4 -> translateUnbreakable769To770(reader, writer);
            case 10, 33 -> translateEnchantments769To770(reader, writer);
            case 11, 12 -> translateItemBlockPredicates769To770(reader, writer);
            case 13 -> translateAttributeModifiers769To770(reader, writer);
            case 14 -> translateCustomModelData769To770(reader, writer);
            case 16 -> translateHideTooltip769To770(reader, writer);
            case 21 -> translateFood769To770(reader, writer);
            case 22 -> translateConsumable769To770(reader, writer);
            case 23 -> translateUseRemainder769To770(reader, writer);
            case 26 -> translateTool769To770(reader, writer);
            case 28 -> translateEquippable769To770(reader, writer);
            case 29 -> translateRepairable769To770(reader, writer);
            case 32 -> translateDeathProtection769To770(reader, writer);
            case 34 -> translateDyedColor769To770(reader, writer);
            case 39, 40, 62 -> translateSlotArray769To770(reader, writer);
            case 45 -> {
                return translateTrim769To770(reader, writer);
            }
            case 50 -> translateInstrument769To770(reader, writer);
            case 52 -> translateJukeboxPlayable769To770(reader, writer);
            case 58 -> translateNoteBlockSound769To770(reader, writer);
            case 59 -> translateBannerPatterns769To770(reader, writer);
            case 61 -> translatePotDecorations769To770(reader, writer);
            default -> copyComponentOpaque769(reader, writer, kind769);
        }
        return true;
    }
""",
"translateComponent770To769(Reader reader, Writer writer, int kind770)": """    /**
     * 成分1個を770→769向けに変換する。成分種別（kind）は呼び出し側が
     * 付け替えて書くため、ここでは中身だけを書く。落とす成分は {@code false} を返す。
     * tooltip_display（15）は呼び出し側で種別を決めるためここには来ない。
     */
    private static boolean translateComponent770To769(Reader reader, Writer writer, int kind770) {
        switch (kind770) {
            case 4 -> translateUnbreakable770To769(reader, writer);
            case 10, 34 -> translateEnchantments770To769(reader, writer);
            case 11, 12 -> translateItemBlockPredicates770To769(reader, writer);
            case 13 -> translateAttributeModifiers770To769(reader, writer);
            case 14 -> translateCustomModelData770To769(reader, writer);
            case 20 -> translateFood770To769(reader, writer);
            case 21 -> translateConsumable770To769(reader, writer);
            case 22 -> translateUseRemainder770To769(reader, writer);
            case 25 -> translateTool770To769(reader, writer);
            case 28 -> translateEquippable770To769(reader, writer);
            case 29 -> translateRepairable770To769(reader, writer);
            case 32 -> translateDeathProtection770To769(reader, writer);
            case 35 -> translateDyedColor770To769(reader, writer);
            case 40, 41, 66 -> translateSlotArray770To769(reader, writer);
            case 47 -> {
                return translateTrim770To769(reader, writer);
            }
            case 52 -> {
                return translateInstrument770To769(reader, writer);
            }
            case 55 -> translateJukeboxPlayable770To769(reader, writer);
            case 62 -> translateNoteBlockSound770To769(reader, writer);
            case 63 -> translateBannerPatterns770To769(reader, writer);
            case 65 -> translatePotDecorations770To769(reader, writer);
            case 26, 33, 43, 53, 56, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81,
                    82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95 -> {
                skipComponentData770(reader, kind770);
                return false;
            }
            default -> copyComponentOpaque770(reader, writer, kind770);
        }
        return true;
    }
""",
"translateEnchantments769To770(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を769→770向けに変換する。IDは同一、末尾の showTooltip は770に無いため読んで落とす。 */
    private static void translateEnchantments769To770(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment769To770(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-770",
                        "770に無いエンチャントを落とします（id=" + id + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeVarInt(level);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        reader.readBoolean(); // showTooltip（770には無い）
    }
""",
"translateEnchantments770To769(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を770→769向けに変換する。IDは同一、末尾に showTooltip=true を書く。 */
    private static void translateEnchantments770To769(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment770To769(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-769-from-770",
                        "769に無いエンチャントを落とします（id=" + id + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeVarInt(level);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        writer.writeBoolean(true); // showTooltip
    }
""",
"translateAttributeModifiers769To770(Reader reader, Writer writer)": """    /** attribute_modifiers を769→770向けに変換する。769は {属性の配列, showTooltip}、770は属性の配列だけ。 */
    private static void translateAttributeModifiers769To770(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute769To770(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-770",
                        "770に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        reader.readBoolean(); // showTooltip（770には無い）
    }
""",
"translateAttributeModifiers770To769(Reader reader, Writer writer)": """    /** attribute_modifiers を770→769向けに変換する。属性の配列の後に showTooltip=true を書く。 */
    private static void translateAttributeModifiers770To769(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute770To769(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-769-from-770",
                        "769に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        writer.writeBoolean(true); // showTooltip
    }
""",
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateTool769To770(Reader reader, Writer writer)": """    /** tool を769→770向けに変換する。ルールの中のブロックIDだけ付け替え、末尾に canDestroyBlocksInCreative=false を補う。 */
    private static void translateTool769To770(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks769To770(reader, writer);
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeFloat(reader.readFloat());
            } else {
                writer.writeBoolean(false);
            }
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeBoolean(reader.readBoolean());
            } else {
                writer.writeBoolean(false);
            }
        }
        writer.writeFloat(reader.readFloat()); // defaultMiningSpeed
        writer.writeVarInt(reader.readVarInt()); // damagePerBlock
        writer.writeBoolean(false); // canDestroyBlocksInCreative（769には無い）
    }
""",
"translateTool770To769(Reader reader, Writer writer)": """    /** tool を770→769向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative を落とす。 */
    private static void translateTool770To769(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks770To769(reader, writer);
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeFloat(reader.readFloat());
            } else {
                writer.writeBoolean(false);
            }
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeBoolean(reader.readBoolean());
            } else {
                writer.writeBoolean(false);
            }
        }
        writer.writeFloat(reader.readFloat()); // defaultMiningSpeed
        writer.writeVarInt(reader.readVarInt()); // damagePerBlock
        reader.readBoolean(); // canDestroyBlocksInCreative（769には無い）
    }
""",
"translateItemBlockPredicates769To770(Reader reader, Writer writer)": """    /** can_place_on／can_break を769→770向けに変換する。述語の中のブロックIDだけ付け替え、末尾の showTooltip を落とす。 */
    private static void translateItemBlockPredicates769To770(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate769To770(reader, writer);
        }
        reader.readBoolean(); // showTooltip（770には無い）
    }
""",
"translateItemBlockPredicates770To769(Reader reader, Writer writer)": """    /** can_place_on／can_break を770→769向けに変換する。述語の中のブロックIDだけ付け替え、末尾に showTooltip=true を書く。 */
    private static void translateItemBlockPredicates770To769(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate770To769(reader, writer);
        }
        writer.writeBoolean(true); // showTooltip
    }
""",
"translateItemBlockPredicate769To770(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を769→770向けに変換する。述語の中のブロックIDだけ付け替え、末尾に空の DataComponentMatchers を補う。 */
    private static void translateItemBlockPredicate769To770(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks769To770(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeString(reader.readString()); // name
                boolean exact = reader.readBoolean();
                writer.writeBoolean(exact);
                if (exact) {
                    writer.writeString(reader.readString());
                } else {
                    writer.writeString(reader.readString());
                    writer.writeString(reader.readString());
                }
            }
        } else {
            writer.writeBoolean(false);
        }
        writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // nbt
        // DataComponentMatchers（770で追加）：空の exact／partial を書く。
        writer.writeVarInt(0); // exactMatchers（0件）
        writer.writeVarInt(0); // partialMatchers（0件）
    }
""",
"translateItemBlockPredicate770To769(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を770→769向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers を読み飛ばす。 */
    private static void translateItemBlockPredicate770To769(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks770To769(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeString(reader.readString()); // name
                boolean exact = reader.readBoolean();
                writer.writeBoolean(exact);
                if (exact) {
                    writer.writeString(reader.readString());
                } else {
                    writer.writeString(reader.readString());
                    writer.writeString(reader.readString());
                }
            }
        } else {
            writer.writeBoolean(false);
        }
        writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // nbt
        skipDataComponentMatchers770(reader);
    }

    /** DataComponentMatchers（770）を読み飛ばす。exact は成分の列、partial は種別の列。 */
    private static void skipDataComponentMatchers770(Reader reader) {
        int exact = reader.readVarInt();
        for (int i = 0; i < exact; i++) {
            int kind770 = reader.readVarInt();
            skipComponentData770(reader, kind770);
        }
        int partial = reader.readVarInt();
        for (int i = 0; i < partial; i++) {
            reader.readVarInt();
        }
    }
""",
"translateTrim769To770(Reader reader, Writer writer)": """    /** trim を769→770向けに変換する。材料・型の Holder と表示フラグ。770に showInTooltip は無いため読んで落とす。 */
    private static boolean translateTrim769To770(Reader reader, Writer writer) {
        if (!translateTrimMaterial769To770(reader, writer)) {
            return false;
        }
        boolean keep = translateTrimPattern769To770(reader, writer);
        reader.readBoolean(); // showInTooltip（770には無い）
        if (!keep) {
            return false;
        }
        return true;
    }
""",
"translateTrim770To769(Reader reader, Writer writer)": """    /** trim を770→769向けに変換する。材料・型の Holder と表示フラグ。末尾に showInTooltip=true を書く。 */
    private static boolean translateTrim770To769(Reader reader, Writer writer) {
        if (!translateTrimMaterial770To769(reader, writer)) {
            return false;
        }
        boolean keep = translateTrimPattern770To769(reader, writer);
        if (!keep) {
            return false;
        }
        writer.writeBoolean(true); // showInTooltip
        return true;
    }
""",
"translateTrimMaterial769To770(Reader reader, Writer writer)": """    /** trim の材料の Holder を769→770向けに変換する。770で ingredientId が無くなったため読んで落とし、assetName は assetBase としてそのまま書く。 */
    private static boolean translateTrimMaterial769To770(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return true;
        }
        writer.writeString(reader.readString()); // assetName → assetBase
        reader.readVarInt(); // ingredientId（770には無い）
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.writeString(reader.readString());
            writer.writeString(reader.readString());
        }
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateTrimMaterial770To769(Reader reader, Writer writer)": """    /** trim の材料の Holder を770→769向けに変換する。直書きのとき ingredientId は assetBase から補う（既知の材料10種。未知のものは鉄インゴット）。 */
    private static boolean translateTrimMaterial770To769(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return true;
        }
        String assetBase = reader.readString();
        writer.writeString(assetBase); // assetBase → assetName
        writer.writeVarInt(trimMaterialIngredient769(assetBase));
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.writeString(reader.readString());
            writer.writeString(reader.readString());
        }
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }

    /** trim 材料の assetBase（例：iron）から769の材料アイテムIDを返す（vv-1.21.4.json の items 順）。 */
    private static int trimMaterialIngredient769(String assetBase) {
        return switch (assetBase) {
            case "quartz" -> 839;
            case "iron" -> 842;
            case "netherite" -> 847;
            case "redstone" -> 680;
            case "copper" -> 844;
            case "gold" -> 846;
            case "emerald" -> 837;
            case "diamond" -> 836;
            case "lapis" -> 838;
            case "amethyst" -> 840;
            default -> {
                logOnce("trim-material-unknown-769",
                        "未知の trim 材料のため鉄インゴットで代用します（assetBase=" + assetBase + "）。");
                yield 842;
            }
        };
    }
""",
"translateTrimPattern769To770(Reader reader, Writer writer)": """    /** trim の型の Holder を769→770向けに変換する。770で templateItemId が無くなったため読んで落とす。 */
    private static boolean translateTrimPattern769To770(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern769To770(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-770",
                        "770に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
                return false;
            }
            writer.writeVarInt(mapped + 1);
            return true;
        }
        writer.writeVarInt(0);
        writer.writeString(reader.readString()); // assetId
        reader.readVarInt(); // templateItemId（770には無い）
        copyOptionalAnonymousNbt(reader, writer);
        writer.writeBoolean(reader.readBoolean()); // decal
        return true;
    }
""",
"translateTrimPattern770To769(Reader reader, Writer writer)": """    /** trim の型の Holder を770→769向けに変換する。直書きのとき templateItemId は assetId から補う（既知の型18種。未知のものは sentry）。 */
    private static boolean translateTrimPattern770To769(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern770To769(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-769-from-770",
                        "769に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
                return false;
            }
            writer.writeVarInt(mapped + 1);
            return true;
        }
        writer.writeVarInt(0);
        String assetId = reader.readString();
        writer.writeString(assetId); // assetId
        writer.writeVarInt(trimPatternTemplate769(assetId));
        copyOptionalAnonymousNbt(reader, writer);
        writer.writeBoolean(reader.readBoolean()); // decal
        return true;
    }

    /** trim 型の assetId（例：sentry）から769の型紙アイテムIDを返す（vv-1.21.4.json の items 順）。 */
    private static int trimPatternTemplate769(String assetId) {
        return switch (assetId) {
            case "sentry" -> 1323;
            case "dune" -> 1324;
            case "coast" -> 1325;
            case "wild" -> 1326;
            case "ward" -> 1327;
            case "eye" -> 1328;
            case "vex" -> 1329;
            case "tide" -> 1330;
            case "snout" -> 1331;
            case "rib" -> 1332;
            case "spire" -> 1333;
            case "wayfinder" -> 1334;
            case "shaper" -> 1335;
            case "silence" -> 1336;
            case "raiser" -> 1337;
            case "host" -> 1338;
            case "flow" -> 1339;
            case "bolt" -> 1340;
            default -> {
                logOnce("trim-pattern-unknown-769",
                        "未知の trim 型のため sentry の型紙で代用します（assetId=" + assetId + "）。");
                yield 1323;
            }
        };
    }
""",
"translateInstrument769To770(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを769→770向けに変換する。769は Holder
     * （varint：0＝直書き／それ以外＝参照ID＋1）そのものだが、770は
     * {hasHolder bool, data：true なら Holder／false なら楽器名} の
     * コンテナになる。769の Holder はそのまま内側の Holder として書く。
     */
    private static void translateInstrument769To770(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeBoolean(true); // hasHolder（769の Holder をそのまま内側に書く）
        writer.writeVarInt(holder);
        if (holder != 0) {
            return;
        }
        translateSoundHolder769To770(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
    }
""",
"translateInstrument770To769(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを770→769向けに変換する。770は
     * {hasHolder bool, data} のコンテナ。hasHolder=true なら内側の
     * Holder を769の Holder として書き、false（楽器名だけ）は
     * 769に表現が無いため成分ごと落とす（{@code false} を返す）。
     */
    private static boolean translateInstrument770To769(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        if (!hasHolder) {
            reader.readString(); // 楽器名（769に表現が無い）
            logOnce("instrument-name-drop-769",
                    "楽器名だけの instrument を769向けに落とします。");
            return false;
        }
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return true;
        }
        translateSoundHolder770To769(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateJukeboxPlayable769To770(Reader reader, Writer writer)": """    /** jukebox_playable を769→770向けに変換する。曲IDは同一、直書きの音の Holder だけ付け替える。末尾の showInTooltip は770に無いため読んで落とす。 */
    private static void translateJukeboxPlayable769To770(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            writer.writeVarInt(holder);
            if (holder == 0) {
                translateSoundHolder769To770(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            }
        } else {
            writer.writeString(reader.readString()); // song name
        }
        reader.readBoolean(); // showInTooltip（770には無い）
    }
""",
"translateJukeboxPlayable770To769(Reader reader, Writer writer)": """    /** jukebox_playable を770→769向けに変換する。曲IDは同一、直書きの音の Holder だけ付け替える。末尾に showInTooltip=true を書く。 */
    private static void translateJukeboxPlayable770To769(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            writer.writeVarInt(holder);
            if (holder == 0) {
                translateSoundHolder770To769(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            }
        } else {
            writer.writeString(reader.readString()); // song name
        }
        writer.writeBoolean(true); // showInTooltip
    }
""",
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateParticle770To769(Reader reader, Writer writer)": """    /**
     * パーティクル1個（ID VarInt＋データ）を770→769向けに変換する。
     * tinted_leaves（35）と firefly（113）は769に無いためデータを
     * 読み飛ばして {@code false} を返す（呼び出し側はエントリ／パケットごと落とす）。
     */
    private static boolean translateParticle770To769(Reader reader, Writer writer) {
        int particleId = reader.readVarInt();
        if (particleId == PARTICLE_770_TINTED_LEAVES) {
            reader.readInt(); // tinted_leaves：色の i32 を読み飛ばす。
            return false;
        }
        if (particleId == PARTICLE_770_FIREFLY) {
            // firefly にデータは無い。
            return false;
        }
        writer.writeVarInt(RegistryRemapper.mapParticleUp770(particleId));
        translateParticleData770To769(reader, writer, particleId);
        return true;
    }
""",
"translateMetadataValue769To770(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を769→770向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は769側の番号で dispatch する（呼び出し側が770の型番号に付け替える）。
     * 23（狼バリアント）は Holder からただの varint になり、直書きは落とす。
     * 24（カエル）→26、25（任意の全体位置）→29、26（絵画）→30、
     * 27（スニッファー）→31、28（アルマジロ）→32、29→33、30→34。
     */
    private static boolean translateMetadataValue769To770(Reader reader, Writer writer, int type) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeVarInt(reader.readVarInt());
            case 2 -> writer.writeVarLong(reader.readVarLong());
            case 3 -> writer.writeFloat(reader.readFloat());
            case 4 -> writer.writeString(reader.readString());
            case 5 -> {
                Tag tag = reader.readAnonymousNbt().tag();
                if (tag == null) {
                    throw new IllegalArgumentException("メタデータのテキストが空です。");
                }
                writer.writeAnonymousNbt(tag);
            }
            case 6 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    Tag tag = reader.readAnonymousNbt().tag();
                    if (tag == null) {
                        throw new IllegalArgumentException("メタデータのテキストが空です。");
                    }
                    writer.writeAnonymousNbt(tag);
                }
            }
            case 7 -> translateSlot769To770(reader, writer);
            case 8 -> writer.writeBoolean(reader.readBoolean());
            case 9 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 10 -> writer.writePositionRaw(reader.readPositionRaw());
            case 11 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 12 -> writer.writeVarInt(reader.readVarInt());
            case 13 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeBytes(reader.readBytes(16));
                }
            }
            case 14 -> writer.writeVarInt(RegistryRemapper.mapBlockState769To770(reader.readVarInt()));
            case 15 -> writer.writeVarInt(RegistryRemapper.mapBlockState769To770(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> translateParticle769To770(reader, writer);
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle769To770(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20 -> writer.writeVarInt(reader.readVarInt());
            case 21 -> writer.writeVarInt(reader.readVarInt());
            case 22, 24 -> writer.writeVarInt(reader.readVarInt());
            case 23 -> {
                return translateWolfVariantHolder769To770(reader, writer);
            }
            case 25 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 26 -> {
                return translatePaintingVariantHolder769To770(reader, writer);
            }
            case 27, 28 -> writer.writeVarInt(reader.readVarInt());
            case 29 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 30 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }

    /**
     * 狼バリアントの Holder を769→770向けに変換する。770の狼バリアントは
     * ただの varint（レジストリID）のため、参照（holder != 0）は ID そのまま
     * （769・770とも9種・同じ並び）、直書き（holder == 0）は落とす
     * （{@code false} を返す）。
     */
    private static boolean translateWolfVariantHolder769To770(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            // 直書き（テクスチャ3文字列＋バイオームの IDSet）は770に表現が無い。
            reader.readString(); // wildTexture
            reader.readString(); // tameTexture
            reader.readString(); // angryTexture
            skipIdSet(reader); // biome
            logOnce("wolf-variant-inline-drop-770",
                    "直書きの狼バリアントを770向けに落とします。");
            return false;
        }
        writer.writeVarInt(holder - 1); // 参照ID（769・770とも同じ並び）
        return true;
    }
""",
"translateMetadataValue770To769(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を770→769向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は770側の番号で dispatch する（呼び出し側が769の型番号に付け替える）。
     * 23（ウシ）・25（狼の音）・27（ブタ）・28（ニワトリ）は769に無いため落とす。
     * 24（狼バリアント）は varint から Holder（参照ID＋1）に戻す。
     * 26（カエル）→24、29（任意の全体位置）→25、30（絵画）→26、
     * 31（スニッファー）→27、32（アルマジロ）→28、33→29、34→30。
     */
    private static boolean translateMetadataValue770To769(Reader reader, Writer writer, int type) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeVarInt(reader.readVarInt());
            case 2 -> writer.writeVarLong(reader.readVarLong());
            case 3 -> writer.writeFloat(reader.readFloat());
            case 4 -> writer.writeString(reader.readString());
            case 5 -> {
                Tag tag = reader.readAnonymousNbt().tag();
                if (tag == null) {
                    throw new IllegalArgumentException("メタデータのテキストが空です。");
                }
                writer.writeAnonymousNbt(tag);
            }
            case 6 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    Tag tag = reader.readAnonymousNbt().tag();
                    if (tag == null) {
                        throw new IllegalArgumentException("メタデータのテキストが空です。");
                    }
                    writer.writeAnonymousNbt(tag);
                }
            }
            case 7 -> translateSlot770To769(reader, writer);
            case 8 -> writer.writeBoolean(reader.readBoolean());
            case 9 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 10 -> writer.writePositionRaw(reader.readPositionRaw());
            case 11 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 12 -> writer.writeVarInt(reader.readVarInt());
            case 13 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeBytes(reader.readBytes(16));
                }
            }
            case 14 -> writer.writeVarInt(RegistryRemapper.mapBlockStateUp770(reader.readVarInt()));
            case 15 -> writer.writeVarInt(RegistryRemapper.mapBlockStateUp770(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> {
                return translateParticle770To769(reader, writer);
            }
            case 18 -> {
                int count = reader.readVarInt();
                Writer kept = new Writer();
                int keptCount = 0;
                for (int i = 0; i < count; i++) {
                    Writer single = new Writer();
                    if (translateParticle770To769(reader, single)) {
                        kept.writeBytes(single.bytes());
                        keptCount++;
                    }
                }
                writer.writeVarInt(keptCount);
                writer.writeBytes(kept.bytes());
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20 -> writer.writeVarInt(reader.readVarInt());
            case 21 -> writer.writeVarInt(reader.readVarInt());
            case 22 -> writer.writeVarInt(reader.readVarInt());
            case 23, 25, 27, 28 -> {
                // ウシ／狼の音／ブタ／ニワトリのバリアントは769に無いため落とす。
                reader.readVarInt();
                return false;
            }
            case 24 -> translateWolfVariantHolder770To769(reader, writer);
            case 26 -> writer.writeVarInt(reader.readVarInt()); // frog_variant
            case 29 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder770To769(reader, writer);
            }
            case 31, 32 -> writer.writeVarInt(reader.readVarInt());
            case 33 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 34 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }

    /** 狼バリアント（770の varint）を769の Holder（参照ID＋1）に戻す。 */
    private static void translateWolfVariantHolder770To769(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt() + 1);
    }

    /** メタデータの型番号を769→770に付け替える。769に無い型は -1（落とす）。 */
    private static int mapMetadataType769To770(int type769) {
        return switch (type769) {
            case 23 -> 24; // wolf_variant
            case 24 -> 26; // frog_variant
            case 25 -> 29; // optional_global_pos
            case 26 -> 30; // painting_variant
            case 27 -> 31; // sniffer_state
            case 28 -> 32; // armadillo_state
            case 29 -> 33; // vector3
            case 30 -> 34; // quaternion
            default -> type769; // 0〜22 は同一
        };
    }

    /** メタデータの型番号を770→769に付け替える。769に無い型は -1（落とす）。 */
    private static int mapMetadataType770To769(int type770) {
        return switch (type770) {
            case 23, 25, 27, 28 -> -1; // cow／wolf_sound／pig／chicken
            case 24 -> 23; // wolf_variant
            case 26 -> 24; // frog_variant
            case 29 -> 25; // optional_global_pos
            case 30 -> 26; // painting_variant
            case 31 -> 27; // sniffer_state
            case 32 -> 28; // armadillo_state
            case 33 -> 29; // vector3
            case 34 -> 30; // quaternion
            default -> type770; // 0〜22 は同一
        };
    }

    /** メタデータの値1個を読み飛ばす（770形式。落とすエントリ用）。 */
    private static void skipMetadataValue770(Reader reader, int type770) {
        switch (type770) {
            case 23, 24, 25, 26, 27, 28 -> reader.readVarInt();
            default -> throw new IllegalArgumentException("読み飛ばせないメタデータ型です: " + type770);
        }
    }
""",
"translateEntityMetadata769To770(byte[] payload)": """    /** Entity Metadata（769 → 770）。型番号を付け替える。 */
    public static byte[] translateEntityMetadata769To770(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt());
            while (true) {
                int index = reader.readUnsignedByte();
                if (index == 0xFF) {
                    writer.writeUnsignedByte(0xFF);
                    break;
                }
                int type = reader.readVarInt();
                Writer entry = new Writer();
                if (translateMetadataValue769To770(reader, entry, type)) {
                    writer.writeUnsignedByte(index);
                    writer.writeVarInt(mapMetadataType769To770(type));
                    writer.writeBytes(entry.bytes());
                }
            }
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Entity Metadata を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateEntityMetadata770To769(byte[] payload)": """    /** Entity Metadata（770 → 769）。769に無いバリアントのエントリは落とす。 */
    public static byte[] translateEntityMetadata770To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt());
            while (true) {
                int index = reader.readUnsignedByte();
                if (index == 0xFF) {
                    writer.writeUnsignedByte(0xFF);
                    break;
                }
                int type = reader.readVarInt();
                int mappedType = mapMetadataType770To769(type);
                if (mappedType < 0) {
                    skipMetadataValue770(reader, type);
                    continue;
                }
                Writer entry = new Writer();
                if (translateMetadataValue770To769(reader, entry, type)) {
                    writer.writeUnsignedByte(index);
                    writer.writeVarInt(mappedType);
                    writer.writeBytes(entry.bytes());
                }
            }
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Entity Metadata を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateChunkData769To770(byte[] payload)": """    /** チャンクデータを769→770向けに変換する。heightmap は NBT から直接の配列に変わった。 */
    public static byte[] translateChunkData769To770(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int chunkX = reader.readInt();
            int chunkZ = reader.readInt();
            Nbt.ReadResult heightmaps = reader.readAnonymousNbt();
            int dataLength = reader.readVarInt();
            byte[] chunkData = reader.readBytes(dataLength);
            byte[] translatedSections = translateChunkSections769To770(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityType769To770(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("770に無いブロックエンティティ種別（769 type=" + type
                            + "）のエントリをチャンクから落とします。");
                    continue;
                }
                kept.add(new BlockEntityEntry(packedXZ, y, mapped, nbt));
            }
            byte[] lightData = reader.rest();
            Writer writer = new Writer();
            writer.writeInt(chunkX);
            writer.writeInt(chunkZ);
            writeHeightmaps770(writer, heightmaps.tag());
            writer.writeByteArray(translatedSections);
            writer.writeVarInt(kept.size());
            for (BlockEntityEntry entry : kept) {
                writer.writeUnsignedByte(entry.packedXZ());
                writer.writeShort(entry.y());
                writer.writeVarInt(entry.type());
                writeOptionalAnonymousNbt(writer, entry.nbt());
            }
            writer.writeBytes(lightData);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chunk Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Heightmap の種別名の並び（770の type id に対応する）。
     * 順序はバニラの Heightmap.Types の宣言順（WORLD_SURFACE_WG、
     * WORLD_SURFACE、OCEAN_FLOOR_WG、OCEAN_FLOOR、MOTION_BLOCKING、
     * MOTION_BLOCKING_NO_LEAVES）で、実バニラキャプチャでの確認は未実施。
     */
    private static final String[] HEIGHTMAP_TYPE_NAMES_770 = {
        "WORLD_SURFACE_WG", "WORLD_SURFACE", "OCEAN_FLOOR_WG", "OCEAN_FLOOR",
        "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES",
    };

    /** 769の Heightmap NBT（compound。キーは種別名、値は long[]）を770の配列形式で書く。 */
    private static void writeHeightmaps770(Writer writer, Tag tag) {
        if (!(tag instanceof Tag.CompoundTag compound)) {
            writer.writeVarInt(0);
            return;
        }
        Map<String, Tag> entries = compound.value();
        Writer body = new Writer();
        int count = 0;
        for (int type = 0; type < HEIGHTMAP_TYPE_NAMES_770.length; type++) {
            Tag value = entries.get(HEIGHTMAP_TYPE_NAMES_770[type]);
            if (!(value instanceof Tag.LongArrayTag longs)) {
                continue;
            }
            body.writeVarInt(type);
            body.writeVarInt(longs.value().length);
            for (long v : longs.value()) {
                body.writeLong(v);
            }
            count++;
        }
        writer.writeVarInt(count);
        writer.writeBytes(body.bytes());
    }

    /** 770の Heightmap 配列を読み、769の NBT（compound）にして返す。無ければ null。 */
    private static Tag readHeightmaps770ToNbt(Reader reader) {
        int count = reader.readVarInt();
        if (count <= 0) {
            return null;
        }
        Map<String, Tag> entries = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int type = reader.readVarInt();
            int length = reader.readVarInt();
            long[] data = new long[length];
            for (int j = 0; j < length; j++) {
                data[j] = reader.readLong();
            }
            if (type < 0 || type >= HEIGHTMAP_TYPE_NAMES_770.length) {
                LOG.warning("未知の Heightmap 種別です（770 type=" + type + "）。");
                continue;
            }
            entries.put(HEIGHTMAP_TYPE_NAMES_770[type], new Tag.LongArrayTag(data));
        }
        return new Tag.CompoundTag(entries);
    }
""",
"translateChunkData770To769(byte[] payload)": """    /** チャンクデータを770→769向けに変換する。heightmap は直接の配列から NBT に戻す。 */
    public static byte[] translateChunkData770To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int chunkX = reader.readInt();
            int chunkZ = reader.readInt();
            Tag heightmaps = readHeightmaps770ToNbt(reader);
            int dataLength = reader.readVarInt();
            byte[] chunkData = reader.readBytes(dataLength);
            byte[] translatedSections = translateChunkSections770To769(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityTypeUp770(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("769に無いブロックエンティティ種別（770 type=" + type
                            + "）のエントリをチャンクから落とします。");
                    continue;
                }
                kept.add(new BlockEntityEntry(packedXZ, y, mapped, nbt));
            }
            byte[] lightData = reader.rest();
            Writer writer = new Writer();
            writer.writeInt(chunkX);
            writer.writeInt(chunkZ);
            if (heightmaps == null) {
                writer.writeByte(0);
            } else {
                writer.writeAnonymousNbt(heightmaps);
            }
            writer.writeByteArray(translatedSections);
            writer.writeVarInt(kept.size());
            for (BlockEntityEntry entry : kept) {
                writer.writeUnsignedByte(entry.packedXZ());
                writer.writeShort(entry.y());
                writer.writeVarInt(entry.type());
                writeOptionalAnonymousNbt(writer, entry.nbt());
            }
            writer.writeBytes(lightData);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chunk Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateWindowClick769To770(byte[] payload)": """    /**
     * Window Click（769 → 770）は変換しない。770の Window Click は HashedSlot
     * （成分はハッシュ値だけ）を使うため、769の完全なスロットに変換できない
     * （ハッシュの計算式が未確認）。ProxySession でパケットごと落とすため、
     * このメソッドは呼ばれない想定。呼ばれた場合は失敗させる。
     */
    public static byte[] translateWindowClick769To770(byte[] payload) {
        throw new UnsupportedOperationException("770の Window Click は変換できません（HashedSlot のため）。");
    }
""",
"translateWindowClick770To769(byte[] payload)": """    /**
     * Window Click（770 → 769）は変換しない。770の Window Click は HashedSlot
     * （成分はハッシュ値だけ）を使うため、769の完全なスロットに復元できない
     * （ハッシュは成分の中身を表さない）。ProxySession でパケットごと落とすため、
     * このメソッドは呼ばれない想定。呼ばれた場合は失敗させる。
     */
    public static byte[] translateWindowClick770To769(byte[] payload) {
        throw new UnsupportedOperationException("770の Window Click は変換できません（HashedSlot のため）。");
    }
""",
"translateSetCreativeSlot769To770(byte[] payload)": """    /** Set Creative Slot（769 → 770）。770のスロットは UntrustedSlot（成分は ByteArray の生バイト）。 */
    public static byte[] translateSetCreativeSlot769To770(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeShort(reader.readShort()); // slotId
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            if (count <= 0) {
                requireEnd(reader);
                return writer.bytes();
            }
            writer.writeVarInt(RegistryRemapper.mapItem769To770(reader.readVarInt()));
            int added = reader.readVarInt();
            Writer addedBytes = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind769 = reader.readVarInt();
                int kind770 = mapComponentKind769To770(kind769);
                if (kind770 < 0) {
                    skipComponentData769(reader, kind769);
                    continue;
                }
                Writer component = new Writer();
                if (!translateComponent769To770(reader, component, kind769)) {
                    continue;
                }
                addedBytes.writeVarInt(kind770);
                addedBytes.writeByteArray(component.bytes());
                keptAdded++;
            }
            int removed = reader.readVarInt();
            Writer removedBytes = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind770 = mapComponentKind769To770(reader.readVarInt());
                if (kind770 < 0) {
                    continue;
                }
                removedBytes.writeVarInt(kind770);
                keptRemoved++;
            }
            requireEnd(reader);
            writer.writeVarInt(keptAdded);
            writer.writeVarInt(keptRemoved);
            writer.writeBytes(addedBytes.bytes());
            writer.writeBytes(removedBytes.bytes());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Creative Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateSetCreativeSlot770To769(byte[] payload)": """    /** Set Creative Slot（770 → 769）。770の UntrustedSlot（成分は ByteArray の生バイト）を769のスロット形式に変換する。 */
    public static byte[] translateSetCreativeSlot770To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeShort(reader.readShort()); // slotId
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            if (count <= 0) {
                requireEnd(reader);
                return writer.bytes();
            }
            writer.writeVarInt(RegistryRemapper.mapItemUp770(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind770 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                if (kind770 == 15) {
                    int kind769 = translateTooltipDisplay770To769(new Reader(data));
                    if (kind769 < 0) {
                        continue;
                    }
                    addedOut.writeVarInt(kind769);
                    keptAdded++;
                    continue;
                }
                int kind769 = mapComponentKind770To769(kind770);
                if (kind769 < 0) {
                    continue;
                }
                Writer component = new Writer();
                if (!translateComponent770To769(new Reader(data), component, kind770)) {
                    continue;
                }
                addedOut.writeVarInt(kind769);
                addedOut.writeBytes(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind769 = mapComponentKind770To769(reader.readVarInt());
                if (kind769 < 0) {
                    continue;
                }
                removedOut.writeVarInt(kind769);
                keptRemoved++;
            }
            requireEnd(reader);
            writer.writeVarInt(keptAdded);
            writer.writeBytes(addedOut.bytes());
            writer.writeVarInt(keptRemoved);
            writer.writeBytes(removedOut.bytes());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Creative Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Spawn Entity のペイロードからエンティティ種別を読む（770形式。entityId＋UUID の後）。 */
    public static int spawnEntityType770(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // entityId
        reader.readBytes(16); // uuid
        return reader.readVarInt(); // type
    }

    /**
     * Spawn Entity（770 → 769）。experience_orb（770=47）のときだけ使い、
     * 769の Spawn Experience Orb にする。data（varint）は経験値と仮定する
     * （実バニラでの確認は未実施）。769の count は i16 のため 32767 で抑える。
     */
    public static byte[] translateSpawnEntityExperienceOrb770To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            reader.readBytes(16); // uuid（769の orb には無い）
            int type = reader.readVarInt();
            if (type != 47) {
                throw new IllegalArgumentException("experience_orb ではありません（770 type=" + type + "）。");
            }
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            reader.readByte(); // pitch（orb には無い）
            reader.readByte(); // yaw
            reader.readByte(); // headYaw
            int count = reader.readVarInt(); // data（経験値と仮定）
            reader.readShort(); // velX
            reader.readShort(); // velY
            reader.readShort(); // velZ
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeShort(Math.min(Math.max(count, 0), 32767));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity Experience Orb を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Spawn Experience Orb（769 → 770）。770は通常の Spawn Entity
     * （type=47）にし、UUID は新規に作る。pitch／yaw／速度は 0。
     */
    public static byte[] translateSpawnEntityExperienceOrb769To770(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            int count = reader.readShort();
            requireEnd(reader);
            java.util.UUID uuid = java.util.UUID.randomUUID();
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeLong(uuid.getMostSignificantBits());
            writer.writeLong(uuid.getLeastSignificantBits());
            writer.writeVarInt(47); // experience_orb
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(0); // pitch
            writer.writeByte(0); // yaw
            writer.writeByte(0); // headYaw
            writer.writeVarInt(count); // data（経験値）
            writer.writeShort(0); // velX
            writer.writeShort(0); // velY
            writer.writeShort(0); // velZ
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity Experience Orb を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translatePalettedContainer769To770(Reader reader, Writer writer, boolean blocks)": """private static void translatePalettedContainer769To770(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockState769To770(value)
                    : mapBiome769To770(value));
            // 769側は長さ varint（0）を読むが、770側には長さ接頭辞が無いため書かない。
            int dataLength = reader.readVarInt();
            if (dataLength != 0) {
                throw new IllegalArgumentException(
                        "シングルパレットの Data Array 長が 0 ではありません: " + dataLength);
            }
            return;
        }
        boolean indirect = blocks ? bitsPerEntry <= 8 : bitsPerEntry <= 3;
        if (indirect) {
            int paletteLength = reader.readVarInt();
            if (paletteLength <= 0 || paletteLength > (1 << bitsPerEntry)) {
                throw new IllegalArgumentException("パレット長が不正です: " + paletteLength);
            }
            writer.writeVarInt(paletteLength);
            for (int i = 0; i < paletteLength; i++) {
                int value = reader.readVarInt();
                writer.writeVarInt(blocks
                        ? RegistryRemapper.mapBlockState769To770(value)
                        : mapBiome769To770(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 769側の長さ varint を読むが、770側には書かない。
            int length = reader.readVarInt();
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            if (length != expected) {
                throw new IllegalArgumentException("Data Array 長が想定と違います: " + length
                        + "（想定=" + expected + "）。");
            }
            for (int i = 0; i < length; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        int dataLength = reader.readVarInt();
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        if (dataLength != expected) {
            throw new IllegalArgumentException("直接パレットの Data Array 長が想定と違います: "
                    + dataLength + "（想定=" + expected + "）。");
        }
        // 770側には長さ接頭辞が無いため書かない。
        long[] longs = new long[dataLength];
        for (int i = 0; i < dataLength; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries769To770(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }""",
"translatePalettedContainer770To769(Reader reader, Writer writer, boolean blocks)": """private static void translatePalettedContainer770To769(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockStateUp770(value)
                    : mapBiomeUp770(value));
            // 770側には長さ接頭辞が無いため読まない。769側には長さ 0 を書く。
            writer.writeVarInt(0);
            return;
        }
        boolean indirect = blocks ? bitsPerEntry <= 8 : bitsPerEntry <= 3;
        if (indirect) {
            int paletteLength = reader.readVarInt();
            if (paletteLength <= 0 || paletteLength > (1 << bitsPerEntry)) {
                throw new IllegalArgumentException("パレット長が不正です: " + paletteLength);
            }
            writer.writeVarInt(paletteLength);
            for (int i = 0; i < paletteLength; i++) {
                int value = reader.readVarInt();
                writer.writeVarInt(blocks
                        ? RegistryRemapper.mapBlockStateUp770(value)
                        : mapBiomeUp770(value));
            }
            // 770側には長さ接頭辞が無い。長さは bpe から計算し、769側に書く。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            writer.writeVarInt(expected);
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：770側には長さ接頭辞が無い。長さは bpe から計算し、769側に書く。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        writer.writeVarInt(expected);
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries770To769(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }""",
})


# ----------------------------------------------------------------------
# PlayTranslator 770 節（手書き。769→770の変更は少ないため）
# ----------------------------------------------------------------------

PLAY_770_SECTION = """    // ------------------------------------------------------------------
    // 769 ⇔ 770（1.21.5）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move は
    // 769と770で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Player Chat（先頭に globalIndex varint 追加）、
    // Chat Message／Chat Command Signed（末尾に checksum u8 追加）、
    // Registry Data（レジストリの順序。1.21.5で追加されたレジストリは無い）
    // だけである。Explosion は粒子・音の変換器が ChunkTranslator にあるため
    // あちらに置く（ChunkTranslator.translateExplosion769To770 参照）。
    // ------------------------------------------------------------------

    /**
     * Player Info（769 → 770）。形式は同一のため素通しする。
     * （update_hat・update_list_order とも769・770で同じ。）
     */
    public static byte[] translatePlayerInfo769To770(byte[] payload) {
        return payload;
    }

    /** Player Info（770 → 769）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo770To769(byte[] payload) {
        return payload;
    }

    /** Set Held Item（769 → 770）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem769To770(byte[] payload) {
        return payload;
    }

    /** Set Held Item（770 → 769）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem770To769(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（769 → 770）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove769To770(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（770 → 769）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove770To769(byte[] payload) {
        return payload;
    }

    /**
     * Player Chat（769 → 770）。先頭に globalIndex（varint）を追加する。
     * globalIndex はセッションごとの通し番号（ProxySession が 0 から数える）。
     */
    public static byte[] translatePlayerChat769To770(byte[] payload, int globalIndex) {
        Writer writer = new Writer();
        writer.writeVarInt(globalIndex);
        writer.writeBytes(payload);
        return writer.bytes();
    }

    /**
     * Player Chat（770 → 769）。先頭の globalIndex（varint）を取り除く。
     */
    public static byte[] translatePlayerChat770To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            reader.readVarInt(); // globalIndex
            return reader.rest();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Chat Message／Chat Command Signed（770 → 769）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum770(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（769 → 770）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実バニラ 1.21.5 での確認は未実施。サーバーが拒否する可能性がある）。
     */
    public static byte[] appendChatChecksum770(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 769の Registry Data を770形式にする。1.21.5で追加されたレジストリは
     * 無いため順序の付け替え（reorderRegistries）だけでよい。
     * {@code minecraft:jukebox_song} は769にも770にも存在するため落とさない。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData769To770(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 770の Registry Data を769形式にする。1.21.5で追加されたレジストリは
     * 無いため順序の付け替え（reorderRegistries）だけでよい。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData770To769(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_4);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }
"""


# ----------------------------------------------------------------------
# 769 節の修正（770の土台として直す。別途テストも直す）
# ----------------------------------------------------------------------

# attribute_modifiers：769・768とも末尾に showTooltip bool があるのに
# 769の翻訳器は読んでいなかった（次の成分の kind がずれる実バグ）。
# 両方向とも末尾に bool を写す。
FIX_769_ATTR_768_TO_769_OLD = """            int mapped = RegistryRemapper.mapAttribute768To769(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-769",
                        "769に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }"""
FIX_769_ATTR_768_TO_769_NEW = """            int mapped = RegistryRemapper.mapAttribute768To769(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-769",
                        "769に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        writer.writeBoolean(reader.readBoolean()); // showTooltip（768・769とも末尾にある）
    }"""

FIX_769_ATTR_769_TO_768_OLD = """            int mapped = RegistryRemapper.mapAttribute769To768(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-768-from-769",
                        "768に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }"""
FIX_769_ATTR_769_TO_768_NEW = """            int mapped = RegistryRemapper.mapAttribute769To768(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-768-from-769",
                        "768に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        writer.writeBoolean(reader.readBoolean()); // showTooltip（768・769とも末尾にある）
    }"""

# ItemCost：768・769とも形式は {itemId, itemCount, addedComponentCount,
# components[]}（スロットの羅列ではない）。769の翻訳器は誤って
# 「個数＋スロット列」と読んでいたため直す。767→768の対は別作業
# （前からある不具合で、770の経路には影響しない）。
FIX_769_ITEMCOST_768_TO_769_OLD = """    /** ItemCost（コスト1個）を768→769向けに変換する。 */
    public static void translateItemCost768To769(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateSlot768To769(reader, writer);
        }
    }"""
FIX_769_ITEMCOST_768_TO_769_NEW = """    /**
     * ItemCost（コスト1個）を768→769向けに変換する。形式は
     * {itemId, itemCount, addedComponentCount, components[]}（スロットの
     * 羅列ではない）。768・769とも成分種別の番号は同じ。
     */
    public static void translateItemCost768To769(Reader reader, Writer writer) {
        writer.writeVarInt(RegistryRemapper.mapItem768To769(reader.readVarInt()));
        writer.writeVarInt(reader.readVarInt()); // count
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind = reader.readVarInt();
            Writer component = new Writer();
            if (translateComponent768To769(reader, component, kind)) {
                kept.writeVarInt(kind);
                kept.writeBytes(component.bytes());
                keptCount++;
            }
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }"""

FIX_769_ITEMCOST_769_TO_768_OLD = """    /** ItemCost（コスト1個）を769→768向けに変換する。 */
    public static void translateItemCost769To768(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateSlot769To768(reader, writer);
        }
    }"""
FIX_769_ITEMCOST_769_TO_768_NEW = """    /** ItemCost（コスト1個）を769→768向けに変換する。形式は {itemId, itemCount, addedComponentCount, components[]}。 */
    public static void translateItemCost769To768(Reader reader, Writer writer) {
        writer.writeVarInt(RegistryRemapper.mapItemUp769(reader.readVarInt()));
        writer.writeVarInt(reader.readVarInt()); // count
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind = reader.readVarInt();
            Writer component = new Writer();
            if (translateComponent769To768(reader, component, kind)) {
                kept.writeVarInt(kind);
                kept.writeBytes(component.bytes());
                keptCount++;
            }
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }"""


def insert_section_before_class_close(path, section, label, guard):
    """section をファイル末尾の最終 `}`（クラスの閉じ）の直前に挿入する。"""
    text = path.read_text(encoding="utf-8")
    assert guard not in text, f"[{label}] {guard} が既に存在する（2回目の実行？）"
    stripped = text.rstrip()
    assert stripped.endswith("}"), f"[{label}] ファイルの末尾が }} ではない"
    body = stripped[:-1].rstrip()
    new_text = body + "\n\n" + section + "\n}\n"
    path.write_text(new_text, encoding="utf-8")
    print(f"[{label}] 挿入した（{len(new_text)} chars）")


def main():
    chunk_sec, cs, ce = extract_section(
        CT, "// 768 ⇔ 769（1.21.4）", "// NBT の読み書き")
    play_sec, ps, pe = extract_section_to_eof(
        PT, "// 768 ⇔ 769（1.21.4）")
    print(f"chunk section: lines {cs+1}-{ce+1} ({len(chunk_sec)} chars)")
    print(f"play section: lines {ps+1}-{pe+1} ({len(play_sec)} chars)")
    Path("/tmp/chunk769_section.txt").write_text(chunk_sec, encoding="utf-8")
    Path("/tmp/play769_section.txt").write_text(play_sec, encoding="utf-8")
    chunk_shifted = shift(chunk_sec)
    play_shifted = shift(play_sec)
    Path("/tmp/chunk770_shifted.txt").write_text(chunk_shifted, encoding="utf-8")
    Path("/tmp/play770_shifted.txt").write_text(play_shifted, encoding="utf-8")
    print(f"chunk shifted: {len(chunk_shifted)} chars, play shifted: {len(play_shifted)} chars")

    # --- ChunkTranslator 770 節 ---
    chunk770 = apply_fixes(chunk_shifted, CHUNK_STRING_FIXES, "chunk-string")
    for sig, new_text in CHUNK_METHOD_REPLACEMENTS.items():
        chunk770 = replace_method(chunk770, sig, new_text, "chunk")
    print(f"chunk770: {len(chunk770)} chars, "
          f"{chunk770.count('translate')} translate occurrences")
    insert_section_before_class_close(
        CT, chunk770, "chunk", "translateSlot769To770")

    # --- PlayTranslator 770 節（手書き） ---
    insert_section_before_class_close(
        PT, PLAY_770_SECTION, "play", "translatePlayerChat769To770")

    # --- reorderRegistries の jukebox_song フィルタ：770 も残す ---
    pt_text = PT.read_text(encoding="utf-8")
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4) {""",
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5) {""",
                  "play-jukebox")
    PT.write_text(pt_text, encoding="utf-8")
    print("[play-jukebox] OK")

    # --- 769 節の修正（attribute_modifiers の showTooltip、ItemCost の形式） ---
    ct_text = CT.read_text(encoding="utf-8")
    ct_text = fix(ct_text, FIX_769_ATTR_768_TO_769_OLD, FIX_769_ATTR_768_TO_769_NEW, "fix769-attr-fwd")
    ct_text = fix(ct_text, FIX_769_ATTR_769_TO_768_OLD, FIX_769_ATTR_769_TO_768_NEW, "fix769-attr-rev")
    ct_text = fix(ct_text, FIX_769_ITEMCOST_768_TO_769_OLD, FIX_769_ITEMCOST_768_TO_769_NEW, "fix769-itemcost-fwd")
    ct_text = fix(ct_text, FIX_769_ITEMCOST_769_TO_768_OLD, FIX_769_ITEMCOST_769_TO_768_NEW, "fix769-itemcost-rev")
    # --- ChunkTranslator の imports：heightmap 変換で Map／LinkedHashMap を使う ---
    ct_text = fix(ct_text,
                  "import java.util.ArrayList;\nimport java.util.List;\nimport java.util.Set;",
                  "import java.util.ArrayList;\nimport java.util.LinkedHashMap;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.Set;",
                  "chunk-imports")
    CT.write_text(ct_text, encoding="utf-8")
    print("[fix769] OK（attribute_modifiers ×2、ItemCost ×2）")
    print("[chunk-imports] OK（Map／LinkedHashMap）")
    print("完了。次は :detergentmc-protocol:compileJava で確認すること。")

if __name__ == "__main__":
    main()
