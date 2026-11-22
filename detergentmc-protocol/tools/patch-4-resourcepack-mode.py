#!/usr/bin/env python3
"""ResourcePackMode に pack4（1.7.2）を配線する（5 の配線の鏡）。

- Decision レコードの正規コンストラクタ末尾に pack4 を追加し、
  委譲コンストラクタ全ての this(...) 末尾に null を1つ足す。
- 「1.7.2 パック無し」の後方互換コンストラクタを追加する。
- packForProtocol に V1_7_2 分岐（pack4→pack5→pack47→…）を追加。
- 最長 decide() に configuredUrl4／configuredSha14 を追加し、
  pack4 の構築と Decision への受け渡しを行う。その手前の
  オーバーロードの委譲呼び出しに "", "" を足す。
冪等ではない（実行は1回だけ）。件数が合わなければ中止する。
"""
import re
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "ResourcePackMode.java"
src = PATH.read_text(encoding="utf-8")


def rep(old, new, expect):
    global src
    n = src.count(old)
    assert n == expect, f"{n} != {expect}: {old[:70]}"
    src = src.replace(old, new)
    print(f"ok {expect} x {old[:60]}")


# 1. レコードヘッダ
rep("ResourcePack pack108, ResourcePack pack107, ResourcePack pack47,\n"
    "            ResourcePack pack5) {",
    "ResourcePack pack108, ResourcePack pack107, ResourcePack pack47,\n"
    "            ResourcePack pack5, ResourcePack pack4) {", 1)

# 2. 委譲コンストラクタの this(...) 末尾に null を追加
lines = src.split("\n")
count = 0
for i, line in enumerate(lines):
    if line.lstrip().startswith("this(kind, pack, problem,") and line.rstrip().endswith(");"):
        lines[i] = line.rstrip()[:-2] + ", null);"
        count += 1
src = "\n".join(lines)
assert count == 23, f"this(...) delegations: {count} != 23"
print(f"ok 23 x this(...) + null")

# 3. 「1.7.2 パック無し」互換コンストラクタを「1.7.10 パック無し」から生成
m = re.search(
    r"        /\*\* Backwards-compatible constructor without a 1\.7\.10 pack\. \*/\n"
    r"        public Decision\(Kind kind, ResourcePack pack, String problem,\n"
    r"                ResourcePack pack758[^;]*?\) \{\n"
    r"            this\(kind, pack, problem,[^;]*?\);\n"
    r"        \}\n", src)
assert m, "without-5 constructor not found"
block = m.group(0)
new_block = block.replace("without a 1.7.10 pack", "without a 1.7.2 pack")
new_block = new_block.replace(
    "ResourcePack pack107, ResourcePack pack47) {",
    "ResourcePack pack107, ResourcePack pack47, ResourcePack pack5) {")
new_block = new_block.replace("pack107, pack47, null, null);",
                              "pack107, pack47, pack5, null);")
assert new_block != block and "pack5, null);" in new_block
src = src.replace(block, block + "\n" + new_block)
print("ok compat constructor without pack4")

# 4. packForProtocol に V1_7_2 分岐
m = re.search(
    r"            if \(protocol == ProtocolVersion\.V1_7_10\) \{\n"
    r"                return pack5 != null \? pack5\n"
    r"(.*?)"
    r"            \}\n", src, re.S)
assert m, "packForProtocol V1_7_10 branch not found"
branch5 = m.group(0)
branch4 = branch5.replace("ProtocolVersion.V1_7_10", "ProtocolVersion.V1_7_2")
branch4 = branch4.replace(
    "return pack5 != null ? pack5\n",
    "return pack4 != null ? pack4\n"
    "                        : pack5 != null ? pack5\n")
src = src.replace(branch5, branch4 + branch5)
print("ok packForProtocol V1_7_2 branch")

# 5. 最長 decide()
rep("            String configuredUrl5, String configuredSha15,\n"
    "            boolean required, String promptKey) {",
    "            String configuredUrl5, String configuredSha15,\n"
    "            String configuredUrl4, String configuredSha14,\n"
    "            boolean required, String promptKey) {", 1)
rep("            ResourcePack pack5 = null;",
    "            ResourcePack pack5 = null;\n"
    "            ResourcePack pack4 = null;", 1)
m = re.search(
    r"            String url5 = configuredUrl5 == null \? \"\" : configuredUrl5\.trim\(\);\n"
    r"            if \(!url5\.isEmpty\(\)\) \{\n"
    r".*?"
    r"            \}\n", src, re.S)
assert m, "url5 block not found"
url5_block = m.group(0)
url4_block = (url5_block
              .replace("url5", "url4")
              .replace("configuredUrl5", "configuredUrl4")
              .replace("configuredSha15", "configuredSha14")
              .replace("resource-pack.url-5", "resource-pack.url-4")
              .replace("resource-pack.sha1-5", "resource-pack.sha1-4")
              .replace("5 clients will be offered the 1.8 pack instead",
                       "4 clients will be offered the 1.7.10 pack instead")
              .replace("pack5 = new ResourcePack(url4,",
                       "pack4 = new ResourcePack(url4,"))
assert "pack4 = new ResourcePack" in url4_block
src = src.replace(url5_block, url5_block + url4_block)
print("ok url4 block")
rep("pack107, pack47, pack5);", "pack107, pack47, pack5, pack4);", 1)

# 6. 手前のオーバーロードの委譲に "", "" を追加
rep('configuredUrl47, configuredSha147, "", "", required, promptKey);',
    'configuredUrl47, configuredSha147, "", "", "", "", required, promptKey);', 1)

PATH.write_text(src, encoding="utf-8")
print("written", PATH)
