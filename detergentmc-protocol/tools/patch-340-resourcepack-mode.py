#!/usr/bin/env python3
"""ResourcePackMode.java に 340（1.12.2）パックを追加する。

393 の追加と同じ形：Decision レコードに pack340 コンポーネントを追加
（互換コンストラクタの委譲には null を 1 つ追加）、decide() の最長
オーバーロードに configuredUrl340/configuredSha1340 を追加（他の
オーバーロードの委譲には null, null を追加）、340 用の検証ブロック、
packForProtocol の 340 分岐（代替は 393→401→… の順）。

使い方：
  python3 tools/patch-340-resourcepack-mode.py
"""

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
    if "pack340" in text:
        print("skip（既存）")
        return 0

    # レコードのコンポーネントに pack340 を追加。
    text = rep(text,
        "ResourcePack pack401, ResourcePack pack393) {",
        "ResourcePack pack401, ResourcePack pack393, ResourcePack pack340) {", 1)
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
    # 最長オーバーロードのシグネチャに 340 引数を追加。
    text = rep(text,
        """            String configuredUrl393, String configuredSha1393,
            boolean required, String promptKey) {""",
        """            String configuredUrl393, String configuredSha1393,
            String configuredUrl340, String configuredSha1340,
            boolean required, String promptKey) {""", 1)
    # pack340 の宣言。
    text = rep(text,
        """            ResourcePack pack393 = null;
""",
        """            ResourcePack pack393 = null;
            ResourcePack pack340 = null;
""", 1)
    # 393 の検証ブロックの直前に 340 の検証ブロックを追加。
    text = rep(text,
        """            String url393 = configuredUrl393 == null ? "" : configuredUrl393.trim();
""",
        """            String url340 = configuredUrl340 == null ? "" : configuredUrl340.trim();
            if (!url340.isEmpty()) {
                if (!url340.startsWith("https://")) {
                    problems.add("resource-pack.url-340 must start with https:// (got '"
                            + url340 + "'); 340 clients will be offered the 1.13 pack instead");
                } else if (!isValidSha1(configuredSha1340)) {
                    problems.add("resource-pack.sha1-340 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1340
                            + "'; 340 clients will be offered the 1.13 pack instead");
                } else {
                    pack340 = new ResourcePack(url340,
                            configuredSha1340.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url393 = configuredUrl393 == null ? "" : configuredUrl393.trim();
""", 1)
    # 最終 return に pack340 を追加。
    text = rep(text,
        "pack477, pack404, pack401, pack393);",
        "pack477, pack404, pack401, pack393, pack340);", 1)
    # packForProtocol に 340 分岐を追加（393 分岐の複製＋先頭に pack340）。
    text = rep(text,
        """            if (protocol == ProtocolVersion.V1_13) {
                return pack393 != null ? pack393
""",
        """            if (protocol == ProtocolVersion.V1_12_2) {
                return pack340 != null ? pack340
                        : pack393 != null ? pack393
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
            if (protocol == ProtocolVersion.V1_13) {
                return pack393 != null ? pack393
""", 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java に 340 パックを追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
