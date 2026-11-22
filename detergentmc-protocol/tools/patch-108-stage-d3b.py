#!/usr/bin/env python3
"""108 Stage D3 の追補：Bedrock 側の列挙への 108 追加。

patch-108-stage-d3.py の列挙アンカーは 110 時代の並びを前提に
変換したため現在の実物（109 が末尾に入った並び）と合わず失敗した。
本スクリプトは実物のアンカーで列挙だけを追随する。パース関数の
新設とスポーン振り分けは d3 で適用済み。
各置換は期待件数を検証し、合わなければ失敗として報告する。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
BEDROCK = ROOT / "detergentmc-bedrock/src/main/java/io/detergentmc/bedrock"
failures = []


def patch(path, replacements):
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            failures.append(f"{path.name}: 期待 {expected} 件のところ {count} 件: {old[:70]!r}")
            continue
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    print(f"{path.name}: 適用した")


patch(BEDROCK / "BedrockChunkTranslator.java", [
    ("case RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_110,",
     "case RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_110,", 1),
    ("""                || input.serverProtocol() == RegistryRemapper.PROTOCOL_109) {""",
     """                || input.serverProtocol() == RegistryRemapper.PROTOCOL_109
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_108) {""", 1),
])

patch(BEDROCK / "BedrockEntityTranslator.java", [
    ("case RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_110,",
     "case RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_110,", 2),
])

patch(BEDROCK / "JavaBackendConnection.java", [
    ("|| serverProtocol == 110 || serverProtocol == 109) {",
     "|| serverProtocol == 110 || serverProtocol == 109 || serverProtocol == 108) {", 5),
    ("serverProtocol == 110 || serverProtocol == 109 || serverProtocol == 393",
     "serverProtocol == 110 || serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 393", 2),
    ("|| serverProtocol == 110 || serverProtocol == 109\n",
     "|| serverProtocol == 110 || serverProtocol == 109 || serverProtocol == 108\n", 4),
    ("&& serverProtocol != 110 && serverProtocol != 109);",
     "&& serverProtocol != 110 && serverProtocol != 109 && serverProtocol != 108);", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
