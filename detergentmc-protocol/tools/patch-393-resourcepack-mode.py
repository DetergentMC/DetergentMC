#!/usr/bin/env python3
"""ResourcePackMode.java に 393（1.13）パックを追加する。

401 の追加と同じ形：Decision レコードに pack393 コンポーネントを追加
（互換コンストラクタの委譲には null を 1 つ追加）、decide() の最長
オーバーロードに configuredUrl393/configuredSha1393 を追加（他の
オーバーロードの委譲には null, null を追加）、393 用の検証ブロック、
packForProtocol の 393 分岐（代替は 401→404→… の順）。

使い方：
  python3 tools/patch-393-resourcepack-mode.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ResourcePackMode.java"


def rep(text, old, new, count):
    n = text.count(old)
    if n != count:
        raise SystemExit(f"置換回数が合いません（{n} != {count}）: {old[:70]}...")
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "pack393" in text:
        print("skip（既存）")
        return 0

    # レコードのコンポーネントに pack393 を追加。
    text = rep(text,
        "ResourcePack pack404, ResourcePack pack401) {",
        "ResourcePack pack404, ResourcePack pack401, ResourcePack pack393) {", 1)
    # 互換コンストラクタの委譲に末尾 null を 1 つ追加（13 件）。
    lines = text.split("\n")
    n_deleg = 0
    for i, line in enumerate(lines):
        if "this(kind, pack, problem" in line:
            assert line.rstrip().endswith(");"), line[:80]
            lines[i] = line.rstrip()[:-2] + ", null);"
            n_deleg += 1
    if n_deleg != 13:
        raise SystemExit(f"レコード委譲の件数が合いません: {n_deleg}")
    text = "\n".join(lines)
    # decide() の委譲（18 件）に null, null を追加。
    text = rep(text, "required, promptKey);", "null, null, required, promptKey);", 18)
    # 最長オーバーロードのシグネチャに 393 引数を追加。
    text = rep(text,
        """            String configuredUrl401, String configuredSha1401,
            boolean required, String promptKey) {""",
        """            String configuredUrl401, String configuredSha1401,
            String configuredUrl393, String configuredSha1393,
            boolean required, String promptKey) {""", 1)
    # pack393 の宣言。
    text = rep(text,
        """            ResourcePack pack401 = null;
""",
        """            ResourcePack pack401 = null;
            ResourcePack pack393 = null;
""", 1)
    # 401 の検証ブロックの直後に 393 の検証ブロックを追加。
    text = rep(text,
        """            String url575 = configuredUrl575 == null ? "" : configuredUrl575.trim();
""",
        """            String url393 = configuredUrl393 == null ? "" : configuredUrl393.trim();
            if (!url393.isEmpty()) {
                if (!url393.startsWith("https://")) {
                    problems.add("resource-pack.url-393 must start with https:// (got '"
                            + url393 + "'); 393 clients will be offered the 1.13.1 pack instead");
                } else if (!isValidSha1(configuredSha1393)) {
                    problems.add("resource-pack.sha1-393 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1393
                            + "'; 393 clients will be offered the 1.13.1 pack instead");
                } else {
                    pack393 = new ResourcePack(url393,
                            configuredSha1393.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url575 = configuredUrl575 == null ? "" : configuredUrl575.trim();
""", 1)
    # 最終 return に pack393 を追加。
    text = rep(text,
        "pack477, pack404, pack401);",
        "pack477, pack404, pack401, pack393);", 1)
    # packForProtocol に 393 分岐を追加（401 分岐の複製＋先頭に pack393）。
    text = rep(text,
        """            if (protocol == ProtocolVersion.V1_13_1) {
                return pack401 != null ? pack401
""",
        """            if (protocol == ProtocolVersion.V1_13) {
                return pack393 != null ? pack393
                        : pack401 != null ? pack401
                        : pack404 != null ? pack404
                        : pack477 != null ? pack477
                        : pack480 != null ? pack480
                        : pack485 != null ? pack485
                        : pack490 != null ? pack490
                        : pack498 != null ? pack498
                        : pack573 != null ? pack573
                        : pack575 != null ? pack575
                        : pack578 != null ? pack578
                        : pack758 != null ? pack758
                        : pack759 != null ? pack759
                        : pack760 != null ? pack760
                        : pack761 != null ? pack761
                        : pack762 != null ? pack762
                        : pack763 != null ? pack763 : pack;
            }
            if (protocol == ProtocolVersion.V1_13_1) {
                return pack401 != null ? pack401
""", 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java に 393 パックを追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
