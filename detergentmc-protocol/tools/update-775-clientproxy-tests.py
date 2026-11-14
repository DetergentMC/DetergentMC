#!/usr/bin/env python3
"""ClientProxy767/768/769Test の「775 は未対応で拒否」前提を 776 へ移す。

775 の受け入れは新ビッグコンストラクタの検証を通るため、これらのテストは
そのままでは失敗する。拒否の境界を 776（26.2）へ移す。
775 の受け入れテストは新設の ClientProxy775Test.java で行う。
"""
import sys
from pathlib import Path

BASE = Path("detergentmc-protocol/src/test/java/io/detergentmc/protocol")
failures = []


def main():
    for name in ("ClientProxy767Test.java", "ClientProxy768Test.java", "ClientProxy769Test.java"):
        p = BASE / name
        src = p.read_text(encoding="utf-8")
        pairs = [
            ("internalProtocol775IsStillRejected", "internalProtocol776IsStillRejected"),
            ("expected IllegalArgumentException for 775", "expected IllegalArgumentException for 776"),
            ('expected.getMessage().contains("774")', 'expected.getMessage().contains("775")'),
        ]
        for old, new in pairs:
            n = src.count(old)
            if n == 0:
                failures.append(f"{name}: {old[:50]!r} が見つからない")
            src = src.replace(old, new)
        # 拒否テストのコンストラクタ呼び出しと assertThrows の 775→776
        # （, 775) の形で現れる2か所。759〜774 の受け入れテストの , 769) 等には触れない）
        n = src.count(", 775)")
        if n != 2:
            failures.append(f"{name}: ', 775)' が {n} 回（期待2回）")
        src = src.replace(", 775)", ", 776)")
        p.write_text(src, encoding="utf-8")
        print(f"  {name} を更新")
    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        return 1
    print("完了。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
