#!/usr/bin/env python3
"""gen-776-translators.py の ProxySession 設定フェーズ 8 ハンドラだけを適用する。

gen-776-translators.py の main() は初回生成用で、ChunkTranslator／
PlayTranslator に 776 節が入った後は再実行できない（ガードで止まる）。
ProxySession is free の許可が出た後で 8 ハンドラだけを追加するために、
main() の proxysession ステップと同じ insert_before_marker 呼び出しを行う。
冪等ではない（2回目は guard で止まる）。
"""
import importlib.util
from pathlib import Path

TOOLS = "/home/hatch/workspace/detergentmc/detergentmc-protocol/tools"
PS = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java")

spec = importlib.util.spec_from_file_location("gen776", f"{TOOLS}/gen-776-translators.py")
gen776 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen776)

gen776.insert_before_marker(
    PS, gen776.PROXYSESSION_CONFIG_SECTION_776, "proxysession-config",
    "private Outcome handleServerConfiguration775From776(MinecraftPacket packet)",
    "private Outcome handleServerConfiguration776From774(MinecraftPacket packet)")
print("完了。8 ハンドラを追加した。")
