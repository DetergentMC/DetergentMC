#!/usr/bin/env python3
"""775 受け入れに伴い、テスト側の「775 は新しすぎる」前提を 776 へ移す。

対象：ClientAdapter*Test.java（decide(775 → decide(776、コメントの 775より新しい→776より新しい、
766/767Test の陳腐化した「768より新しい」コメントも同じ行のため 776 に直す）、
ClientProxyTest.java の unsupportedProtocolLoginIsRejected（775→776）。

触らないもの：ClientProxy767/768/769Test の internalProtocol775IsStillRejected
（旧ビッグコンストラクタが 775 を拒否するテスト。旧コンストラクタは 775 スコープ外のため現状維持）、
ResourcePackBuilder*Test の packFormatForProtocol(775)（Stage D で扱う）。
"""
import sys
from pathlib import Path

BASE = Path("detergentmc-protocol/src/test/java/io/detergentmc/protocol")
failures = []


def edit(path, pairs, note):
    text = path.read_text(encoding="utf-8")
    orig = text
    for old, new in pairs:
        n = text.count(old)
        if n == 0:
            failures.append(f"{path.name}: {note}: {old[:60]!r} が見つからない")
            continue
        text = text.replace(old, new)
    if text != orig:
        path.write_text(text, encoding="utf-8")
        print(f"  {path.name}: {note} を更新")


def main():
    names = ["ClientAdapter759Test.java", "ClientAdapter760Test.java",
             "ClientAdapter761Test.java", "ClientAdapter762Test.java",
             "ClientAdapter764Test.java", "ClientAdapter766Test.java",
             "ClientAdapter767Test.java", "ClientAdapter768Test.java",
             "ClientAdapter769Test.java", "ClientAdapter770Test.java",
             "ClientAdapter771Test.java", "ClientAdapter772Test.java",
             "ClientAdapter773Test.java", "ClientAdapter774Test.java",
             "ClientAdapterTest.java", "ClientAdapterCrossVersionTest.java"]
    for name in names:
        p = BASE / name
        pairs = [("decide(775", "decide(776"),
                 ("775より新しい", "776より新しい")]
        if name in ("ClientAdapter766Test.java", "ClientAdapter767Test.java"):
            pairs.append(("768より新しい", "776より新しい"))
        edit(p, pairs, "too-new 775→776")
    # ClientProxyTest：未対応プロトコルの拒否テスト（775→776）
    edit(BASE / "ClientProxyTest.java", [
        ("// 774は対応済みのため、拒否されるのは775（26.1以降の未対応番号）。",
         "// 775は対応済みのため、拒否されるのは776（26.2以降の未対応番号）。"),
        ("handshakePayload(775, 2)", "handshakePayload(776, 2)"),
        ('reason.contains("775")', 'reason.contains("776")'),
    ], "unsupportedProtocol 775→776")
    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        return 1
    print("完了。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
