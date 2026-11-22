#!/usr/bin/env python3
"""ResourcePackMode.java に 573（1.15）のパック配線を足す。

575 のときと同じ流儀：Decision レコードの末尾に pack573 を足し、
既存の委譲コンストラクタには null を1つ追加する。decide() には
url-573／sha1-573 の引数と検証ブロックを 575 の直後に足し、
packForProtocol() に 573 の分岐を足す。

使い方: python3 tools/patch-573-resourcepackmode.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
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
    if "pack573" in text:
        print("skip（既存）: 573 配線はすでにある")
        return 0

    # 1. Decision レコードに pack573 を追加（pack575 の後）。
    text = sub(text,
               "ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,\n"
               "            ResourcePack pack575) {",
               "ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,\n"
               "            ResourcePack pack575, ResourcePack pack573) {", 1)

    # 2. 委譲コンストラクタ（this(kind, pack, problem, ...);）に null を追加。
    deleg = re.compile(r"this\(kind, pack, problem,(.*?)\);", re.S)
    matches = deleg.findall(text)
    if len(matches) != 13:
        print(f"NG: 委譲コンストラクタが {len(matches)} 件（期待 13）")
        return 1

    def add_null(m):
        args = m.group(1).rstrip()
        return "this(kind, pack, problem," + args + ", null);"

    text = deleg.sub(add_null, text)
    print("委譲コンストラクタ 13 件に null を追加した")

    # 3. packForProtocol() に 573 の分岐（575 の直前）。
    text = sub(text,
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_15_1) {",
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_15) {\n"
               "                return pack573 != null ? pack573\n"
               "                        : pack575 != null ? pack575\n"
               "                        : pack578 != null ? pack578\n"
               "                        : pack758 != null ? pack758\n"
               "                        : pack759 != null ? pack759\n"
               "                        : pack760 != null ? pack760\n"
               "                        : pack761 != null ? pack761\n"
               "                        : pack762 != null ? pack762\n"
               "                        : pack763 != null ? pack763 : pack;\n"
               "            }\n"
               "            if (protocol == ProtocolVersion.V1_15_1) {", 1)

    # 4. decide() の引数に url573／sha1573 を追加（575 の直後）。
    text = sub(text,
               "            String configuredUrl575, String configuredSha1575,\n"
               "            boolean required, String promptKey) {",
               "            String configuredUrl575, String configuredSha1575,\n"
               "            String configuredUrl573, String configuredSha1573,\n"
               "            boolean required, String promptKey) {", 1)

    # 5. pack573 ローカル変数。
    text = sub(text,
               "            ResourcePack pack575 = null;\n",
               "            ResourcePack pack575 = null;\n"
               "            ResourcePack pack573 = null;\n", 1)

    # 6. url573 の検証ブロック（575 の検証ブロックの直前。どちらも
    #    problem 確定より前に problems へ積む）。
    text = sub(text,
               "            String url575 = configuredUrl575 == null ? \"\" : configuredUrl575.trim();",
               "            String url573 = configuredUrl573 == null ? \"\" : configuredUrl573.trim();\n"
               "            if (!url573.isEmpty()) {\n"
               "                if (!url573.startsWith(\"https://\")) {\n"
               "                    problems.add(\"resource-pack.url-573 must start with https:// (got '\"\n"
               "                            + url573 + \"'); 573 clients will be offered the 1.15.1 pack instead\");\n"
               "                } else if (!isValidSha1(configuredSha1573)) {\n"
               "                    problems.add(\"resource-pack.sha1-573 is missing or not a 40-digit hex \"\n"
               "                            + \"SHA-1: '\" + configuredSha1573\n"
               "                            + \"'; 573 clients will be offered the 1.15.1 pack instead\");\n"
               "                } else {\n"
               "                    pack573 = new ResourcePack(url573,\n"
               "                            configuredSha1573.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n"
               "            String url575 = configuredUrl575 == null ? \"\" : configuredUrl575.trim();", 1)

    # 7. EXTERNAL の Decision 生成に pack573 を渡す。
    text = sub(text,
               "pack774, pack775, pack776, pack578, pack575);",
               "pack774, pack775, pack776, pack578, pack575, pack573);", 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java を更新した（Decision の生成側は呼び出し箇所で pack573 を渡すこと）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
