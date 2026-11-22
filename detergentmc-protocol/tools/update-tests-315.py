"""既存テストの「最古＝316／一覧34件」前提を 315／35件へ一括更新する。

対象はテストツリーだけ。ルール：
 1. List.of(316, 335, ...) の全プロトコル一覧に 315 を先頭追加。
 2. OLDEST_SUPPORTED_CLIENT_PROTOCOL の 316／V1_11_2 断言を 315／V1_11 へ。
 3. supported.size() の 34 を 35 へ。
 4. ClientAdapter の一覧の supported.get(i) は添字を +1 し、
    get(0)=315 の断言を一覧宣言の直後に挿入する（316 のときに
    挿入された get(0)=316 の断言は、添字+1で get(1)=316 になる）。

316 のときに 315 を「未対応」として断言している箇所は、この
スクリプトでは直らないため手で直す（テスト実行で洗い出す）。

使い方：
  python3 tools/update-tests-315.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
TEST_DIRS = [
    ROOT / "detergentmc-protocol/src/test",
    ROOT / "detergentmc-core/src/test",
    ROOT / "detergentmc-bedrock/src/test",
    ROOT / "detergentmc-content/src/test",
]
OLD_LIST = ("List.of(316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, "
            "758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, "
            "770, 771, 772, 773, 774, 775, 776)")
NEW_LIST = ("List.of(315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, "
            "758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, "
            "770, 771, 772, 773, 774, 775, 776)")
GET_RE = re.compile(r"supported\.get\((\d+)\)")


def main():
    changed = []
    for base in TEST_DIRS:
        if not base.exists():
            continue
        for path in sorted(base.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            original = text
            text = text.replace(OLD_LIST, NEW_LIST)
            text = text.replace(
                "assertEquals(316, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                "assertEquals(315, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
            text = text.replace(
                "assertEquals(ProtocolVersion.V1_11_2, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                "assertEquals(ProtocolVersion.V1_11, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
            text = text.replace(
                "assertEquals(34, supported.size(), supported.toString());",
                "assertEquals(35, supported.size(), supported.toString());")
            if "supported.get(" in text:
                text = GET_RE.sub(
                    lambda m: f"supported.get({int(m.group(1)) + 1})", text)
                lines = text.split("\n")
                out = []
                for i, line in enumerate(lines):
                    out.append(line)
                    if ("List<Integer> supported = ClientAdapter"
                            ".supportedClientProtocols();" in line):
                        indent = line[:len(line) - len(line.lstrip())]
                        out.append(indent + "assertEquals(315, supported.get(0),"
                                   " supported.toString());")
                text = "\n".join(out)
            if text != original:
                path.write_text(text, encoding="utf-8")
                changed.append(str(path.relative_to(ROOT)))
    print(f"{len(changed)} ファイルを更新した")
    for c in changed:
        print(" ", c)
    return 0


if __name__ == "__main__":
    sys.exit(main())
