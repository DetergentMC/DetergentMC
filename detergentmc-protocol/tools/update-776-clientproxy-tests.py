#!/usr/bin/env python3
"""ClientProxy767/768/769/775Test の「776 は未対応で拒否」前提を 777 へ移す。

776 の受け入れは新ビッグコンストラクタの検証を通るため、これらのテストは
そのままでは失敗する。拒否の境界を 777（26.3）へ移す。
776 の受け入れテストは新設の ClientProxy776Test.java で行う。
"""
import sys
from pathlib import Path

BASE = Path("detergentmc-protocol/src/test/java/io/detergentmc/protocol")
failures = []


def main():
    for name in ("ClientProxy767Test.java", "ClientProxy768Test.java", "ClientProxy769Test.java",
                 "ClientProxy775Test.java"):
        p = BASE / name
        src = p.read_text(encoding="utf-8")
        pairs = [
            ("internalProtocol776IsStillRejected", "internalProtocol777IsStillRejected"),
            ("expected IllegalArgumentException for 776", "expected IllegalArgumentException for 777"),
            ('expected.getMessage().contains("775")', 'expected.getMessage().contains("776")'),
            ("中間コンストラクタ経由でも 776 は拒否される。", "中間コンストラクタ経由でも 777 は拒否される。"),
        ]
        for old, new in pairs:
            n = src.count(old)
            if n == 0:
                # 「中間コンストラクタ経由でも」は 775Test にしか無い。
                if "中間コンストラクタ経由でも" in old:
                    continue
                failures.append(f"{name}: {old[:50]!r} が見つからない")
            src = src.replace(old, new)
        # 拒否テストのコンストラクタ呼び出しと assertThrows の 776→777
        # （, 776) の形で現れる2か所。759〜775 の受け入れテストの , 769) 等には触れない）
        n = src.count(", 776)")
        if n != 2:
            failures.append(f"{name}: ', 776)' が {n} 回（期待2回）")
        src = src.replace(", 776)", ", 777)")
        p.write_text(src, encoding="utf-8")
        print(f"  {name} を更新")

    # ClientProxyTest：未対応プロトコルのログイン拒否は 776→777 へ移す。
    p = BASE / "ClientProxyTest.java"
    src = p.read_text(encoding="utf-8")
    pairs = [
        ("// 775は対応済みのため、拒否されるのは776（26.2以降の未対応番号）。",
         "// 776は対応済みのため、拒否されるのは777（26.3以降の未対応番号）。"),
        ("client.sendPacket(new MinecraftPacket(0x00, handshakePayload(776, 2)));",
         "client.sendPacket(new MinecraftPacket(0x00, handshakePayload(777, 2)));"),
        ('assertTrue(reason.contains("776"), reason);',
         'assertTrue(reason.contains("777"), reason);'),
    ]
    for old, new in pairs:
        n = src.count(old)
        if n != 1:
            failures.append(f"ClientProxyTest.java: {old[:50]!r} が {n} 回（期待1回）")
        src = src.replace(old, new)
    p.write_text(src, encoding="utf-8")
    print("  ClientProxyTest.java を更新")

    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        return 1
    print("完了。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
