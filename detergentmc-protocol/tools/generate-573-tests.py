#!/usr/bin/env python3
"""575 のテスト群から 573 のテスト群を生成する。

573 のワイヤ形式は 575 と完全一致する（Stage A で実データ証明済み）ため、
575 用テストを雛形に 573 用へ機械変換する。変換はトークン単位：
  - V1_15_1 → V1_15、1.15.1 → 1.15、v1_15_1 → v1_15、1_15_1 → 1_15
  - 数字に隣接しない単独の 575 → 573（識別子中の 575 や単独のリテラル。
    5754 のようなデータ値は数字隣接のため変換しない）
  - 1.15.1 jar の SHA-1 → 1.15 jar の SHA-1
生成後に意味の固定が必要な箇所（対応プロトコル一覧の期待値など）は
手で直し、テスト実行で確認する。

使い方: python3 tools/generate-573-tests.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SHA_1151 = "4d1826eebac84847c71a77f9349cc22afd0cf0a1"
SHA_115 = "e9f105b3c5c7e85c7b445249a93362a22f62442d"
NUM = re.compile(r"(?<![0-9])575(?![0-9])")

FILES = [
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ChunkTranslator575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ClientAdapter575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ClientJoinFlow575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/PacketIds575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/PlayTranslator575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ProtocolVersion575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ProxySession575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/RealVanilla758Join575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/RegistryRemapper575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ResourcePackBuilder575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ResourcePackMode575Test.java",
    "detergentmc-protocol/src/test/java/io/detergentmc/protocol/ResourcePackServer575Test.java",
    "detergentmc-bedrock/src/test/java/io/detergentmc/bedrock/BedrockChunkTranslator575Test.java",
    "detergentmc-bedrock/src/test/java/io/detergentmc/bedrock/JavaBackendConnection575Test.java",
]


def convert(text):
    text = text.replace(SHA_1151, SHA_115)
    text = text.replace("V1151Adapter", "V115Adapter")
    text = text.replace("V1_15_1", "V1_15")
    text = text.replace("v1_15_1", "v1_15")
    text = text.replace("1_15_1", "1_15")
    text = text.replace("1.15.1", "1.15")
    text = NUM.sub("573", text)
    return text


def main():
    for rel in FILES:
        src = ROOT / rel
        dst = ROOT / rel.replace("575Test.java", "573Test.java")
        out = convert(src.read_text(encoding="utf-8"))
        dst.write_text(out, encoding="utf-8")
        print(f"生成: {dst.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
