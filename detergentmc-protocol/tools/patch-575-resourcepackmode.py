#!/usr/bin/env python3
"""ResourcePackMode.java に 575（1.15.1）のパック配線を足す。

578 のときと同じ流儀：Decision レコードの末尾に pack575 を足し、
既存の委譲コンストラクタには null を1つ追加する。decide() には
url-575／sha1-575 の引数と検証ブロックを 578 の直後に足し、
packForProtocol() に 575 の分岐を足す。

使い方: python3 tools/patch-575-resourcepackmode.py
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
    if "pack575" in text:
        print("skip（既存）: 575 配線はすでにある")
        return 0

    # 1. Decision レコードに pack575 を追加。
    text = sub(text,
               "ResourcePack pack775, ResourcePack pack776, ResourcePack pack578) {",
               "ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,\n"
               "            ResourcePack pack575) {", 1)

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

    # 3. packForProtocol() に 575 の分岐（578 の直前）。
    text = sub(text,
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_15_2) {",
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_15_1) {\n"
               "                return pack575 != null ? pack575\n"
               "                        : pack578 != null ? pack578\n"
               "                        : pack758 != null ? pack758\n"
               "                        : pack759 != null ? pack759\n"
               "                        : pack760 != null ? pack760\n"
               "                        : pack761 != null ? pack761\n"
               "                        : pack762 != null ? pack762\n"
               "                        : pack763 != null ? pack763 : pack;\n"
               "            }\n"
               "            if (protocol == ProtocolVersion.V1_15_2) {", 1)

    # 4. decide() の引数に url575／sha1575 を追加（578 の直後）。
    text = sub(text,
               "            String configuredUrl578, String configuredSha1578,\n"
               "            boolean required, String promptKey) {",
               "            String configuredUrl578, String configuredSha1578,\n"
               "            String configuredUrl575, String configuredSha1575,\n"
               "            boolean required, String promptKey) {", 1)

    # 5. pack575 ローカル変数。
    text = sub(text,
               "            ResourcePack pack578 = null;\n",
               "            ResourcePack pack578 = null;\n"
               "            ResourcePack pack575 = null;\n", 1)

    # 6. url575 の検証ブロック（578 の検証ブロックの直後、problem 確定の直前）。
    text = sub(text,
               "            String problem = problems.isEmpty() ? null : String.join(\"; \", problems);",
               "            String url575 = configuredUrl575 == null ? \"\" : configuredUrl575.trim();\n"
               "            if (!url575.isEmpty()) {\n"
               "                if (!url575.startsWith(\"https://\")) {\n"
               "                    problems.add(\"resource-pack.url-575 must start with https:// (got '\"\n"
               "                            + url575 + \"'); 575 clients will be offered the 1.15.2 pack instead\");\n"
               "                } else if (!isValidSha1(configuredSha1575)) {\n"
               "                    problems.add(\"resource-pack.sha1-575 is missing or not a 40-digit hex \"\n"
               "                            + \"SHA-1: '\" + configuredSha1575\n"
               "                            + \"'; 575 clients will be offered the 1.15.2 pack instead\");\n"
               "                } else {\n"
               "                    pack575 = new ResourcePack(url575,\n"
               "                            configuredSha1575.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n"
               "            String problem = problems.isEmpty() ? null : String.join(\"; \", problems);", 1)

    # 7. EXTERNAL の Decision 生成に pack575 を渡す。
    text = sub(text,
               "pack774, pack775, pack776, pack578);",
               "pack774, pack775, pack776, pack578, pack575);", 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java を更新した（Decision の生成側は呼び出し箇所で pack575 を渡すこと）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
