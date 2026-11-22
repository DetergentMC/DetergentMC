#!/usr/bin/env python3
"""patch-107-proxysession.py 手順13で誤った位置（handleServerPlayRouted の
分岐の間）に挿入された 107 の大スイッチ5本を、routeServerPlay108 の
定義の直後（108 の大スイッチがある領域）へ移動する。"""
import pathlib

PATH = (pathlib.Path(__file__).resolve().parent.parent
        / "src/main/java/io/detergentmc/protocol/ProxySession.java")
src = PATH.read_text(encoding="utf-8")

# 誤配置ブロックを切り出す
start_marker = "\n    private List<MinecraftPacket> translateServerPlayPacket758To107"
i = src.index(start_marker)
k = src.index("    private MinecraftPacket to107ClientPacket758(MinecraftPacket packet) {", i)
j = src.index("\n        };\n    }\n", k) + len("\n        };\n    }\n")
block = src[i + 1:j]  # 先頭の改行は含めない
src = src[:i] + src[j:]
assert "translateServerPlayPacket758To107" not in src.split("routeServerPlay108")[0][-2000:], \
    "切り出し位置が想定と違う"
print("ブロックを切り出し（", block.count("\n"), "行）")

# routeServerPlay108 の定義の直後へ挿入する
sig = "    private Outcome routeServerPlay108(MinecraftPacket packet) {"
i = src.index(sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + "\n" + src[end:]

PATH.write_text(src, encoding="utf-8")
print("routeServerPlay108 の直後へ移動した")
