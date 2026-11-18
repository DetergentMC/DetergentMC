#!/usr/bin/env python3
"""ResourcePackMode.java に 498（1.14.4）のパック配線を足す。

573 のときの流儀＋その後始末（fix-573）の教訓込み：
- Decision レコードの末尾に pack498 を足す。
- 委譲コンストラクタのうち、正規コンストラクタへ直接委譲する
  （引数総数 24）ものだけに null を1つ足す。それ以外は触らない。
- decide() 本体には url-498／sha1-498 の引数と検証ブロックを
  573 の直後に足し、packForProtocol() に 498 の分岐を足す。

使い方: python3 tools/patch-498-resourcepackmode.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ResourcePackMode.java"


def sub(text, old, new, count):
    found = text.count(old)
    if found != count:
        print(f"NG: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "pack498" in text:
        print("skip（既存）: 498 配線はすでにある")
        return 0

    # 1. Decision レコードに pack498 を追加（末尾）。
    text = sub(text,
               "ResourcePack pack575, ResourcePack pack573) {",
               "ResourcePack pack575, ResourcePack pack573, ResourcePack pack498) {", 1)

    # 2. 正規コンストラクタへ直接委譲する呼び出し（引数総数 24）だけに null を追加。
    deleg = re.compile(r"this\(kind, pack, problem,(.*?)\);", re.S)
    matches = deleg.findall(text)
    if len(matches) != 13:
        print(f"NG: 委譲コンストラクタが {len(matches)} 件（期待 13）")
        return 1

    added = []

    def add_null(m):
        args = m.group(1)
        total = 3 + args.count(",") + 1
        if total != 24:
            return m.group(0)
        added.append(total)
        return "this(kind, pack, problem," + args.rstrip() + ", null);"

    text = deleg.sub(add_null, text)
    if len(added) != 4:
        print(f"NG: null を足した委譲が {len(added)} 件（期待 4）")
        return 1
    print("正規コンストラクタ行きの委譲 4 件に null を追加した")

    # 3. packForProtocol() に 498 の分岐（573 の直前）。
    text = sub(text,
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_15) {",
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_14_4) {\n"
               "                return pack498 != null ? pack498\n"
               "                        : pack573 != null ? pack573\n"
               "                        : pack575 != null ? pack575\n"
               "                        : pack578 != null ? pack578\n"
               "                        : pack758 != null ? pack758\n"
               "                        : pack759 != null ? pack759\n"
               "                        : pack760 != null ? pack760\n"
               "                        : pack761 != null ? pack761\n"
               "                        : pack762 != null ? pack762\n"
               "                        : pack763 != null ? pack763 : pack;\n"
               "            }\n"
               "            if (protocol == ProtocolVersion.V1_15) {", 1)

    # 4. decide() 本体の引数に url498／sha1498 を追加（573 の直後）。
    text = sub(text,
               "            String configuredUrl573, String configuredSha1573,\n"
               "            boolean required, String promptKey) {",
               "            String configuredUrl573, String configuredSha1573,\n"
               "            String configuredUrl498, String configuredSha1498,\n"
               "            boolean required, String promptKey) {", 1)

    # 5. pack498 ローカル変数。
    text = sub(text,
               "            ResourcePack pack573 = null;\n",
               "            ResourcePack pack573 = null;\n"
               "            ResourcePack pack498 = null;\n", 1)

    # 6. url498 の検証ブロック（573 の検証ブロックの直後。
    #    フォールバック先は 573 のパック）。
    text = sub(text,
               "                    pack573 = new ResourcePack(url573,\n"
               "                            configuredSha1573.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n",
               "                    pack573 = new ResourcePack(url573,\n"
               "                            configuredSha1573.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n"
               "            String url498 = configuredUrl498 == null ? \"\" : configuredUrl498.trim();\n"
               "            if (!url498.isEmpty()) {\n"
               "                if (!url498.startsWith(\"https://\")) {\n"
               "                    problems.add(\"resource-pack.url-498 must start with https:// (got '\"\n"
               "                            + url498 + \"'); 498 clients will be offered the 1.15 pack instead\");\n"
               "                } else if (!isValidSha1(configuredSha1498)) {\n"
               "                    problems.add(\"resource-pack.sha1-498 is missing or not a 40-digit hex \"\n"
               "                            + \"SHA-1: '\" + configuredSha1498\n"
               "                            + \"'; 498 clients will be offered the 1.15 pack instead\");\n"
               "                } else {\n"
               "                    pack498 = new ResourcePack(url498,\n"
               "                            configuredSha1498.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n", 1)

    # 7. EXTERNAL の Decision 生成に pack498 を渡す。
    text = sub(text,
               "pack774, pack775, pack776, pack578, pack575, pack573);",
               "pack774, pack775, pack776, pack578, pack575, pack573, pack498);", 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java を更新した（decide() の呼び出し側は別途直すこと）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
