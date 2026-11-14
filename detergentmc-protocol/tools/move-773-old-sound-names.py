#!/usr/bin/env python3
"""RegistryRemapper.java の古い音の名前表（SOUND_NAME_FALLBACKS_* の
String[][] 5 件）を RegistryTablesSoundNames.java へ移す（1回だけの作業用）。

理由：RegistryRemapper.java の静的初期化子（<clinit>）が 64KB を超えて
"code too large" でコンパイルできなくなる。773 の追加分は小さい
（音の名前の Map 18 件とダメージ種別の int[] 2 件で 1KB 未満）が、
772 時点で既に 63KB 近くまで膨らんでいた。767 のときも同じ理由で
767 の表を RegistryTables767.java へ分離している。
古い音の名前表は 390 対・約 10KB あり、ここを移せば <clinit> は
50KB 台前半まで戻る。表そのものは中身を変えず、置き場所だけ別クラスへ
移す。参照は static import で解決するため、メソッド本体は一切変わらない。

対象は 763／766 時代の 5 件（SOUND_NAME_FALLBACKS、
SOUND_NAME_FALLBACKS_766、SOUND_NAME_FALLBACKS_766_TO_763／764／765）。
verify-766／767 はこれらを読まない（RANGES／OVERRIDES だけ読む）ため、
verify スクリプトの変更は要らない。773 の音の名前表は別クラス
RegistryTables773SoundNames.java にある（こちらは 773 のもの）。
"""
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
PROTO = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
         / "io" / "detergentmc" / "protocol")
REMAPPER = PROTO / "RegistryRemapper.java"
OUT = PROTO / "RegistryTablesSoundNames.java"

text = REMAPPER.read_text(encoding="utf-8")
pattern = re.compile(
    r"    private static final String\[\]\[\] (SOUND_NAME_FALLBACKS\w*) = \{.*?\n    \};"
    r"(?:\n    // [^\n]*)?", re.DOTALL)
moves = [(m.group(1), m.group(0)) for m in pattern.finditer(text)]
assert len(moves) == 5, f"音の名前表が {len(moves)} 件（期待 5 件）"
print("移動する音の名前表:", len(moves), "件")
for n, _ in moves:
    print(" -", n)

new_text = pattern.sub("", text)
new_text = re.sub(r"\n{3,}", "\n\n", new_text)
assert "private static final String[][] SOUND_NAME_FALLBACKS" not in new_text
REMAPPER.write_text(new_text, encoding="utf-8")

header = """package io.detergentmc.protocol;

/**
 * 763／766 時代の音の名前の代替表（SOUND_NAME_FALLBACKS_*）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * RegistryRemapper の静的初期化子（{@code <clinit>}）が 64KB を超えて
 * コンパイルできなくなった（\"code too large\"）ため。767 のときも同じ
 * 理由で 767 の表を分離している。このクラスの表は {@link RegistryRemapper}
 * から static import で同じ名前のまま参照する。773 の音の名前表は別クラス
 * {@code RegistryTables773SoundNames} にある。
 */
final class RegistryTablesSoundNames {

    private RegistryTablesSoundNames() {
    }

"""
body = "\n\n".join(block.replace("    private static final ", "    static final ", 1)
                    for _, block in moves)
OUT.write_text(header + body + "\n\n}\n", encoding="utf-8")

java = REMAPPER.read_text(encoding="utf-8")
anchor = "import static io.detergentmc.protocol.RegistryTables772Down770.*;\n"
assert java.count(anchor) == 1
assert "RegistryTablesSoundNames.*" not in java
java = java.replace(
    anchor,
    anchor + "import static io.detergentmc.protocol.RegistryTablesSoundNames.*;\n",
    1)
REMAPPER.write_text(java, encoding="utf-8")
print("RegistryTablesSoundNames.java を書き出した")
