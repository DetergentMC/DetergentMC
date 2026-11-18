#!/usr/bin/env python3
"""既存テストの「最古＝393／一覧30件」前提を 340／31件へ一括更新する。

対象はテストツリーだけ。ルール：
 1. List.of(393, 401, 404, ...) の全プロトコル一覧に 340 を先頭追加。
 2. OLDEST_SUPPORTED_CLIENT_PROTOCOL の 393／V1_13 断言を 340／V1_12_2 へ。
 3. supported.size() の 30 を 31 へ。
 4. ClientAdapter の一覧の supported.get(i) は添字を +1 し、
    get(0)=340 の断言を一覧宣言の直後に挿入する。

393 のときに 340 を「未対応」として断言している箇所（ClientAdapter
393Test の decide(340) 拒否、PacketIds393Test の PLAY_BED の
存在範囲）は、このスクリプトでは直らないため手で直す。

使い方：
  python3 tools/update-tests-340.py
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
OLD_LIST = ("List.of(393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, "
            "758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, "
            "770, 771, 772, 773, 774, 775, 776)")
NEW_LIST = ("List.of(340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, "
            "578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, "
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
                "assertEquals(393, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                "assertEquals(340, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
            text = text.replace(
                "assertEquals(ProtocolVersion.V1_13, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                "assertEquals(ProtocolVersion.V1_12_2, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
            text = text.replace(
                "assertEquals(30, supported.size(), supported.toString());",
                "assertEquals(31, supported.size(), supported.toString());")
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
                        out.append(indent + "assertEquals(340, supported.get(0),"
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
