#!/usr/bin/env python3
"""RegistryRemapper.java に 573（1.15）の定数と 573⇔758 委譲を追加する。

573 のレジストリは 575／578 と全カテゴリ完全一致する（実バニラ 1.15／
1.15.1 両 jar の data-generator ダンプ reports/registries.json・
reports/blocks.json・reports/commands.json を全件照合して確認済み。
docs/PROXY-1.15.md 参照）。そのため 575 の定数・委譲メソッドを雛形に
573 分を生成する（写像先の 578 の表はそのまま指す）。

使い方: python3 tools/patch-573-remapper.py
冪等：573 が既にあれば何もしない。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"


def fail(msg):
    print(f"NG: {msg}", file=sys.stderr)
    sys.exit(1)


def main():
    text = P.read_text(encoding="utf-8")
    if "PROTOCOL_573" in text:
        print("skip（既存）: 573 はすでにある")
        return 0

    # 1. PROTOCOL_573 定数。
    old = ("    /** 575（1.15.1）のプロトコル番号。 */\n"
           "    public static final int PROTOCOL_575 = 575;")
    if old not in text:
        fail("PROTOCOL_575 の定義が見つからない")
    text = text.replace(old,
                        "    /** 573（1.15）のプロトコル番号。 */\n"
                        "    public static final int PROTOCOL_573 = 573;\n" + old, 1)

    # 2. MAX_*_573 定数群（575 の定数群を雛形に生成し、直後に差し込む）。
    const_re = re.compile(
        r"^    public static final int (\w+)_575 = (\w+)_578;$", re.M)
    consts = const_re.findall(text)
    if len(consts) < 20:
        fail(f"575 の定数が少なすぎる（{len(consts)}件）")
    block = [
        "",
        "    /**",
        "     * 573（1.15）側の最大の番号と代替先。573 のレジストリは 575／578 と",
        "     * 全カテゴリ完全一致する（実バニラ 1.15／1.15.1 両 jar の",
        "     * data-generator ダンプを全件照合して確認済み。docs/PROXY-1.15.md",
        "     * 参照）。そのため 573 の定数は 578 と同値で、573⇔758 の写像も",
        "     * 578⇔758 の表をそのまま使う。",
        "     */",
    ]
    for name, _ in consts:
        block.append(f"    public static final int {name}_573 = {name}_578;")
    last = f"    public static final int {consts[-1][0]}_575 = {consts[-1][0]}_578;"
    if last not in text:
        fail("575 定数群の末尾が見つからない")
    text = text.replace(last, last + "\n" + "\n".join(block), 1)
    print(f"MAX_*_573 定数 {len(consts)} 件を追加した")

    # 3. 573⇔758 委譲メソッド群（575 のメソッド群を雛形に生成）。
    start_marker = "    /** 758のブロック状態ID → 575のブロック状態ID（578 と同一の表）。 */"
    end_marker = ("    public static String mapSoundName575To758(String name) {\n"
                  "        return mapSoundName578To758(name);\n"
                  "    }")
    si = text.find(start_marker)
    ei = text.find(end_marker)
    if si < 0 or ei < 0:
        fail("575 委譲メソッド群の範囲が見つからない")
    ei_end = ei + len(end_marker)
    template = text[si:ei_end]
    delegated = template.replace("575", "573")
    header = (
        "\n\n    // 573 ⇔ 758（1.15 ⇔ 1.18.2）\n"
        "    // 573 のレジストリは 575／578 と全カテゴリ完全一致するため（両 jar の\n"
        "    // data-generator ダンプで全件照合済み）、573⇔758 の写像は\n"
        "    // 578⇔758 の表へ委譲する。将来 573 と 575 の差が見つかった場合は\n"
        "    // ここに専用の表を持たせる。\n\n")
    text = text[:ei_end] + header + delegated + text[ei_end:]
    n_methods = delegated.count("public static")
    print(f"573⇔758 委譲メソッド {n_methods} 件を追加した")

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
