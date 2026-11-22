#!/usr/bin/env python3
"""ProxySession／ClientProxy に pack4（1.7.2）の保持と伝播を配線する。

- ProxySession: pack4 フィールド、setPack4／pack4、
  packFor763LikeClient の外側条件に is4() を追加し、
  フォールバック先頭に pack4→pack5→pack47 の順序を作る。
- ClientProxy: pack4 フィールド、setPack4／pack4、
  セッション生成時の session.setPack4(proxy.pack4) 伝播。
冪等ではない（実行は1回だけ）。件数が合わなければ中止する。
"""
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"


def rep(src, old, new, expect=1):
    n = src.count(old)
    assert n == expect, f"{n} != {expect}: {old[:70]}"
    print(f"ok {expect} x {old[:60]}")
    return src.replace(old, new)


# --- ProxySession ---
p = BASE / "ProxySession.java"
src = p.read_text(encoding="utf-8")
src = rep(src, "    private ResourcePack pack5;",
          "    private ResourcePack pack5;\n    private ResourcePack pack4;")
src = rep(src, """    /** 5クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack5() {
        return pack5;
    }""",
          """    /** 5クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack5() {
        return pack5;
    }

    /** 4クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack4(ResourcePack pack4) {
        this.pack4 = pack4;
    }

    /** 4クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack4() {
        return pack4;
    }""")
src = rep(src, "if (is108() || is107() || is47() || is5() || is109() || is110() || is210() || is315() || is316() || is335() || is338() || is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {",
          "if (is108() || is107() || is47() || is5() || is4() || is109() || is110() || is210() || is315() || is316() || is335() || is338() || is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {")
src = rep(src, """            if (is5()) {
                if (pack5 != null) {
                    return pack5;
                }
            }
            if (is47() || is5()) {""",
          """            if (is4()) {
                if (pack4 != null) {
                    return pack4;
                }
            }
            if (is5() || is4()) {
                if (pack5 != null) {
                    return pack5;
                }
            }
            if (is47() || is5() || is4()) {""")
p.write_text(src, encoding="utf-8")
print("written ProxySession")

# --- ClientProxy ---
p = BASE / "ClientProxy.java"
src = p.read_text(encoding="utf-8")
src = rep(src, "    private ResourcePack pack5;",
          "    private ResourcePack pack5;\n    private ResourcePack pack4;")
src = rep(src, """    public void setPack5(ResourcePack pack5) {
        this.pack5 = pack5;
    }

    public ResourcePack pack5() {
        return pack5;
    }""",
          """    public void setPack5(ResourcePack pack5) {
        this.pack5 = pack5;
    }

    public ResourcePack pack5() {
        return pack5;
    }

    public void setPack4(ResourcePack pack4) {
        this.pack4 = pack4;
    }

    public ResourcePack pack4() {
        return pack4;
    }""")
src = rep(src, "                session.setPack5(proxy.pack5);",
          "                session.setPack5(proxy.pack5);\n"
          "                session.setPack4(proxy.pack4);")
p.write_text(src, encoding="utf-8")
print("written ClientProxy")
