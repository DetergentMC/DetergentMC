#!/usr/bin/env python3
"""patch-clientadapter-772.py から patch-clientadapter-773.py を生成する。

772の edit の new（＝773の old）をそのまま流用し、772→773の差分だけを
shift() で写す。shift() は版文字列の鎖を崩すため、以下は手書きで補う。
- class javadoc clients：末尾に「と 1.21.9／1.21.10（773）」を足すだけ。
- class javadoc 773 play：末尾に773の行を足すだけ。
- decide javadoc 773 entry：772行のあとに773行を足すだけ。
- differences(int) 773 block：772→773の数値・内容を手書きする。
- differences(int,int) pack_format：V1_21_9 の pack_format は "69"。
- decide condition／differences(int,int) server condition／client check／
  766/767/… client branch の鎖：末尾に V1_21_9 行を足すだけ。
  difflib の replace は末尾の2行を置き換えて V1_21_6 を落とすため手書き。
- differences(int,int) client check 2：第2の除外リストにも V1_21_9 を足す。
- supportedClientProtocols：末尾に V1_21_9 を追加する。
"""
import ast, difflib, importlib.util
from pathlib import Path
TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gen773ps", TOOLS / "gen-773-proxysession.py")
g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
shift = g.shift

def load_edits(fn):
    src = open(TOOLS / fn, encoding="utf-8").read()
    tree = ast.parse(src)
    assigns = {n.targets[0].id: n.value for n in tree.body
               if isinstance(n, ast.Assign) and len(n.targets)==1 and isinstance(n.targets[0], ast.Name)}
    return ast.literal_eval(assigns["EDITS"])

e772 = {name: (old, new) for name, old, new in load_edits("patch-clientadapter-772.py")}
live = open(TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "ClientAdapter.java", encoding="utf-8").read()

# 773 differences block（手書き：772→773の正しい数値と内容）
BLOCK773 = '''        if (clientProtocol == ProtocolVersion.V1_21_9) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が773と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.7／1.21.8と1.21.9／1.21.10で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は772と同じ形。",
                            Handling.UNAFFECTED,
                            "772と773の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは773にもあり、772と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（ブロック状態・アイテム・エンティティ・音・パーティクル）。"
                                    + "Code of Conduct が新規。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は772と同じ。Registry Data の形式も"
                                    + "772と同じエントリ配列だが、ブロック状態・アイテム・"
                                    + "エンティティ・音・パーティクルの順序が変わる。"
                                    + "minecraft:dialog レジストリは772と同じ。"
                                    + "Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）が新規。Show Dialog（0x12）・"
                                    + "Clear Dialog（0x11）・Custom Click Action（設定 0x08）は"
                                    + "772と同ID・同形のため素通し。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が772から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・パーティクル・ブロックエンティティ）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,946→29,671件（銅系ブロック・棚の追加）、"
                                    + "アイテムは 1,416→1,488件（銅の道具・防具・銅ゴーレムの"
                                    + "スポーンエッグなど）、エンティティは 151→153件"
                                    + "（銅ゴーレム・マネキンの追加）、音は 1,728→1,771件"
                                    + "（銅系の音＋weather.end_flash）、パーティクルは 114→115件"
                                    + "（copper_fire_flame を5番に挿入）、ブロックエンティティは"
                                    + " 47→49件（shelf を40番に挿入、copper_golem_statue を48番に"
                                    + "追加）。ブロック chain は iron_chain に改名。"
                                    + "バイオーム・エンチャント・メニュー・属性・"
                                    + "データコンポーネント種別・ダメージ種別・絵画・"
                                    + "ジュークボックスの歌・旗の模様・トリム・楽器は変わらない。"
                                    + "（絵画・ジュークボックスの歌は772と同一。実バニラ1.21.9／"
                                    + "1.21.10のキャプチャで確認する。）"),
                    new Difference("PLAY",
                            "773で新規のパケットが8件あり、削除が1件、クライアント行きの"
                                    + "パケットIDも108件変わる。デバッグ系のパケット"
                                    + "（debug_block_value など）が新規。",
                            Handling.TRANSLATED,
                            "新規は Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）・Debug Block Value（0x1A）・"
                                    + "Debug Chunk Value（0x1B）・Debug Entity Value（0x1C）・"
                                    + "Debug Event（0x1D）・Game Test Highlight Pos（0x27）・"
                                    + "Debug Subscription Request（送信側 0x16）。"
                                    + "削除は Debug Sample Subscription（送信側 0x16）。"
                                    + "デバッグ用パケットの挿入でプレイ送信側 0x1A 以降が+4、"
                                    + "0x27 以降が+5ずれる（Join Game 0x2B→0x30、"
                                    + "Chunk Data 0x27→0x2C、Keep Alive 0x26→0x2B、"
                                    + "Ping 0x36→0x3B）。送信側のパケットIDは772と変わらない"
                                    + "（Debug Sample Subscription の削除を除く）。"
                                    + "Chat Message の checksum・Vehicle Move の onGround・"
                                    + "Window Click の HashedSlot・Set Creative Slot の UntrustedSlot・"
                                    + "Player Chat の globalIndex は772・773とも同じため、"
                                    + "番号だけ付け替える。"
                                    + "プレイ状態の773変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の773対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは772⇔773で変換する。残る制限：デバッグ用"
                                    + "パケットは772に相当が無いため旧クライアント向けに落とす。"
                                    + "銅ゴーレム・マネキンは772ではブタに代替する。銅系アイテムは"
                                    + "石に代替する。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }'''

# difflib が似た行を replace と誤認して旧版の行を壊す edit は手書きする。
MANUAL_NEW = {
    # 末尾に「と 1.21.9／1.21.10（773）」を足すだけ（shift は版文字列を全部ずらして壊す）
    "class javadoc clients": None,
    # 末尾に773の行を足すだけ
    "class javadoc 773 play": None,
    # 772行のあとに773行を足すだけ（difflib が772行と773行の位置を入れ替える）
    "decide javadoc 773 entry": None,
    # old_773 の末尾に V1_21_9 を追加する
    "supportedClientProtocols":
        '''        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21, ProtocolVersion.V1_21_2,
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6,
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9);''',
    # 鎖の末尾に773行を足すだけ（difflib の replace は V1_21_6 を落とすため手書き）
    "decide condition":
        '''                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5
                || clientProtocol == ProtocolVersion.V1_21_6
                || clientProtocol == ProtocolVersion.V1_21_7
                || clientProtocol == ProtocolVersion.V1_21_9) {
            Direction dir = direction(clientProtocol, serverProtocol);''',
    "differences(int,int) server condition":
        '''        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2
                || serverProtocol == ProtocolVersion.V1_21_4
                || serverProtocol == ProtocolVersion.V1_21_5
                || serverProtocol == ProtocolVersion.V1_21_6
                || serverProtocol == ProtocolVersion.V1_21_7
                || serverProtocol == ProtocolVersion.V1_21_9) {
            // 内部763・766・767・768・769・770・771・772・773の差の一覧は、対応表・レジストリ表の766／767／768／769／770／771／772／773対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770・771・772・773は消費・中継）。''',
    "differences(int,int) client check":
        '''            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5
                    && clientProtocol != ProtocolVersion.V1_21_6
                    && clientProtocol != ProtocolVersion.V1_21_7
                    && clientProtocol != ProtocolVersion.V1_21_9) {''',
    "differences(int,int) 766/767/768/769/770/772 client branch":
        '''        if (clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5
                || clientProtocol == ProtocolVersion.V1_21_6
                || clientProtocol == ProtocolVersion.V1_21_7
                || clientProtocol == ProtocolVersion.V1_21_9) {''',
    "differences(int,int) pack_format":
        '''                                    + (clientProtocol == ProtocolVersion.V1_21_9 ? "69"
                                            : clientProtocol == ProtocolVersion.V1_21_7 ? "64"
                                            : clientProtocol == ProtocolVersion.V1_21_6 ? "63"
                                            : clientProtocol == ProtocolVersion.V1_21_5 ? "55"
                                            : clientProtocol == ProtocolVersion.V1_21_4 ? "46"
                                            : clientProtocol == ProtocolVersion.V1_21_2 ? "42"
                                            : clientProtocol == ProtocolVersion.V1_21 ? "34" : "32")''',
}

def apply_delta(old773, old772, new772):
    """old772→new772 の差分を old773（= new772）に shift して適用する。"""
    a = old772.split("\n")
    b = new772.split("\n")
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    out = old773.split("\n")
    # 末尾から適用して位置ずれを防ぐ
    for tag, i1, i2, j1, j2 in reversed(sm.get_opcodes()):
        if tag == "equal":
            continue
        if tag == "delete":
            continue  # 772で消えた行は old773 に無い
        new_lines = [shift(x) for x in b[j1:j2]]
        if tag == "insert":
            out[j1:j1] = new_lines
        elif tag == "replace":
            out[j1:j2] = new_lines
    return "\n".join(out)

# 772パッチに edit が無い第2の除外リスト（766/767/…分岐の直前の unsupported 判定）にも
# V1_21_9 を足す。772では 1155adf で別途更新されていた。
EXTRA_EDITS = [
    ("differences(int,int) client check 2",
     '''        if (clientProtocol != ProtocolVersion.V1_20_1
                && clientProtocol != ProtocolVersion.V1_20_2
                && clientProtocol != ProtocolVersion.V1_20_4
                && clientProtocol != ProtocolVersion.V1_20_5
                && clientProtocol != ProtocolVersion.V1_21
                && clientProtocol != ProtocolVersion.V1_21_2
                && clientProtocol != ProtocolVersion.V1_21_4
                && clientProtocol != ProtocolVersion.V1_21_5
                && clientProtocol != ProtocolVersion.V1_21_6
                && clientProtocol != ProtocolVersion.V1_21_7) {''',
     '''        if (clientProtocol != ProtocolVersion.V1_20_1
                && clientProtocol != ProtocolVersion.V1_20_2
                && clientProtocol != ProtocolVersion.V1_20_4
                && clientProtocol != ProtocolVersion.V1_20_5
                && clientProtocol != ProtocolVersion.V1_21
                && clientProtocol != ProtocolVersion.V1_21_2
                && clientProtocol != ProtocolVersion.V1_21_4
                && clientProtocol != ProtocolVersion.V1_21_5
                && clientProtocol != ProtocolVersion.V1_21_6
                && clientProtocol != ProtocolVersion.V1_21_7
                && clientProtocol != ProtocolVersion.V1_21_9) {'''),
]

new_edits = []
for name772, (old772, new772) in e772.items():
    name773 = name772.replace("772", "773").replace("770/771 client", "770/772 client")
    old773 = new772
    assert live.count(old773) == 1, f"{name773}: old_773 が live に {live.count(old773)} 回"
    if name773 in MANUAL_NEW:
        if MANUAL_NEW[name773] is None:
            if name773 == "class javadoc clients":
                new773 = old773.replace(
                    "1.21.7／1.21.8（772）。それより古いクライアントは「古すぎる」、",
                    "1.21.7／1.21.8（772）と 1.21.9／1.21.10（773）。"
                    "それより古いクライアントは「古すぎる」、")
            elif name773 == "class javadoc 773 play":
                new773 = old773.replace(
                    " * 変換も同じく、対応表・レジストリ表の772対応で実装する。",
                    " * 変換も同じく、対応表・レジストリ表の772対応で実装する。773のプレイ状態の\n"
                    " * 変換も同じく、対応表・レジストリ表の773対応で実装する。")
            elif name773 == "decide javadoc 773 entry":
                new773 = old773 + "\n     *   <li>773（1.21.9／1.21.10）：変換して受け入れる。" \
                    "設定フェーズは772と同形で中継する。</li>"
            else:
                raise AssertionError(name773)
            assert new773 != old773, name773
        else:
            new773 = MANUAL_NEW[name773]
    elif name773 == "differences(int) 773 block":
        # delta は挿入のみ（772ブロックの追加）のはず。手書きブロックを使う。
        new773 = old773 + "\n" + BLOCK773
    else:
        new773 = apply_delta(old773, old772, new772)
    new_edits.append((name773, old773, new773))

for name773, old773, new773 in EXTRA_EDITS:
    assert live.count(old773) == 1, f"{name773}: old_773 が live に {live.count(old773)} 回"
    new_edits.append((name773, old773, new773))

# docstring を 773 用に書き換える
doc = '"""ClientAdapter.java へ Java 1.21.9／1.21.10（プロトコル773）の受け入れを入れる。\n\ndecide／supportedClientProtocols／versionName／differences を773対応にする。\n各アンカーはファイル中にちょうど1回だけ現れること。\n"""'
lines = open(TOOLS / "patch-clientadapter-772.py", encoding="utf-8").read().split("\n")
# 772 ファイルの先頭 docstring（3行）を 773 版に置き換えて使う
out_lines = [doc, "import sys", "from pathlib import Path", ""]
out_lines.append("PATH = Path(__file__).resolve().parent.parent / \"src\" / \"main\" / \"java\" \\")
out_lines.append("    / \"io\" / \"detergentmc\" / \"protocol\" / \"ClientAdapter.java\"")
out_lines.append("")
out_lines.append("EDITS = [")
for name, old, new in new_edits:
    out_lines.append(f"    ({name!r}, {old!r},")
    out_lines.append(f"     {new!r}),")
out_lines.append("]")
out_lines.append("")
out_lines.append("")
out_lines.append("def main():")
out_lines.append("    src = PATH.read_text(encoding=\"utf-8\")")
out_lines.append("    for name, old, new in EDITS:")
out_lines.append("        n = src.count(old)")
out_lines.append("        if n != 1:")
out_lines.append("            print(f\"NG: {name}: anchor が {n} 回見つかった（1回のはず）\", file=sys.stderr)")
out_lines.append("            sys.exit(1)")
out_lines.append("        src = src.replace(old, new, 1)")
out_lines.append("        print(f\"OK: {name}\")")
out_lines.append("    PATH.write_text(src, encoding=\"utf-8\")")
out_lines.append("    print(\"ClientAdapter.java に 773 の対応を挿入した\")")
out_lines.append("")
out_lines.append("")
out_lines.append("if __name__ == \"__main__\":")
out_lines.append("    main()")
open(TOOLS / "patch-clientadapter-773.py", "w", encoding="utf-8").write("\n".join(out_lines) + "\n")
print(f"patch-clientadapter-773.py を生成した（{len(new_edits)} edits）")
