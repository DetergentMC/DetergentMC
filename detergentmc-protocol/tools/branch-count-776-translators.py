#!/usr/bin/env python3
"""776 翻訳子の分岐数チェック（shift() の if-chain 末尾落ちの番兵）。

ChunkTranslator.java の 775 節（774 ⇔ 775）と 776 節（775 ⇔ 776）の
対応するメソッド同士で、case ラベル数と if 文数を比べる。
776 の方が少ないメソッドは shift() や置き換えで分岐が落ちた疑いがあるため
一覧に出し、意図した削減（例：drop への統合）かどうかを目で確認する。
コメント・文字列リテラルを除いて数える。

対応付け：メソッド名の 775To776→774To775、776To775→775To774。
776 にしか無いメソッド（新規）は「新規」として報告する。
"""
import re
import sys

CT = "/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java"


def strip_comments_and_strings(text):
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


def extract_methods(text, start_marker, end_marker=None):
    start = text.find(start_marker)
    assert start >= 0, start_marker
    end = text.find(end_marker, start) if end_marker else len(text)
    section = text[start:end]
    methods = {}
    # メソッド定義：private static ... name(...) {
    pattern = re.compile(
        r'(?:private|public|protected)\s+(?:static\s+)?[\w<>\[\]]+\s+(\w+)\s*\([^)]*\)\s*(?:throws\s+[\w, ]+)?\s*\{',
        re.S)
    for m in pattern.finditer(section):
        name = m.group(1)
        body_start = m.end() - 1
        depth = 0
        i = body_start
        while i < len(section):
            c = section[i]
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    break
            i += 1
        body = section[body_start:i + 1]
        clean = strip_comments_and_strings(body)
        cases = len(re.findall(r'\bcase\b', clean))
        ifs = len(re.findall(r'\bif\s*\(', clean))
        methods[name] = (cases, ifs)
    return methods


def main():
    text = open(CT, encoding="utf-8").read()
    m775 = extract_methods(text, "// 774 ⇔ 775（26.1）", "// 775 ⇔ 776（26.2）")
    m776 = extract_methods(text, "// 775 ⇔ 776（26.2）")
    print(f"775 節: {len(m775)} メソッド、776 節: {len(m776)} メソッド")

    def orig_name(name776):
        if "775To776" in name776:
            return name776.replace("775To776", "774To775")
        if "776To775" in name776:
            return name776.replace("776To775", "775To774")
        if "Up776" in name776:
            return name776.replace("Up776", "Up775")
        if name776.endswith("776"):
            return name776[:-3] + "775"
        return None

    problems = []
    new_methods = []
    checked = 0
    # 意図した削減（目視で確認済み）：
    # mapMetadataType775To776／776To775 は 775・776 で型番号が同一（26.1／26.2
    # 両 jar の EntityDataSerializers 登録順で確認）のため恒等写像で if-chain が無い。
    # 775 の元メソッド（774→775）は挿入分の if-chain（5／6 分岐）を持っていた。
    expected_reductions = {
        "mapMetadataType775To776": "型番号は同一のため恒等写像（if-chain 不要）",
        "mapMetadataType776To775": "型番号は同一のため恒等写像（if-chain 不要）",
    }
    for name776, (cases776, ifs776) in sorted(m776.items()):
        orig = orig_name(name776)
        if orig is None or orig not in m775:
            new_methods.append(name776)
            continue
        cases775, ifs775 = m775[orig]
        checked += 1
        # 776 の分岐が 775 より少ない場合は警告（意図した削減もあるため目視確認）。
        if cases776 < cases775 or ifs776 < ifs775:
            if name776 in expected_reductions:
                print(f"  意図した削減: {name776}（元 {orig}）: "
                      f"case {cases775}→{cases776}、if {ifs775}→{ifs776}"
                      f" — {expected_reductions[name776]}")
                continue
            problems.append((name776, orig, cases775, cases776, ifs775, ifs776))
    print(f"対応付けできたメソッド: {checked} 件、新規（775 に対応無し）: {len(new_methods)} 件")
    for name in new_methods:
        print(f"  新規: {name}")
    if problems:
        print(f"\n分岐が減っているメソッド: {len(problems)} 件（目視で意図した削減か確認すること）")
        for name776, orig, c775, c776, i775, i776 in problems:
            print(f"  {name776}（元 {orig}）: case {c775}→{c776}、if {i775}→{i776}")
        return 1
    print("\n分岐が減っているメソッドは無い。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
