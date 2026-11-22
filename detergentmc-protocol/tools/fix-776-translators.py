#!/usr/bin/env python3
"""gen-776-translators.py の chunk 3 修正を生成済み ChunkTranslator.java に反映する。

背景：2026-11-15、スタンドアロンでのコンパイルで 2 件の実バグが発覚した。
(1) translateMetadataValue776To775 が 774 派生で、型番号がずれ
    （24=wolf holder になるべき 25、32=global_pos になるべき 33、
    33=painting になるべき 34、22・25・31・34 が欠落）うえ
    case 22, 24, 29, 31 の重複でコンパイルエラー（ChunkTranslator.java:45040）。
    原因は 774→775 の型番号表（<22 同一、<24 +1、<29 +2、<31 +3、他 +4）が
    774 型 23・27・28・29・30 で誤った番号を返すこと
    （775 の mapMetadataType774To775 の潜在バグ。26.1 jar の
    EntityDataSerializers 登録順で確認。775 は対象外のため直さない）。
(2) translateComponent776To775 が 774 派生で、775 の新規 6 種
    （776 番号で 41, 43, 95, 97, 99, 107）が欠落し、default の
    copyComponentOpaque776 に落ちて例外になる（パケットが素通しになる）。
    原因は 774 には無い種別を 774 派生では拾えないこと。

修正：両メソッドとも 775 派生に書き換えた（gen-776-translators.py の
build_component_down／build_metadata_value_down を参照）。
このスクリプトは修正後の 2 メソッドを生成し、ChunkTranslator.java の
誤った 2 メソッドと置き換える。冪等ではない（2回目は old が無いため失敗する）。
"""
import importlib.util
import sys
from pathlib import Path

TOOLS = "/home/hatch/workspace/detergentmc/detergentmc-protocol/tools"
CT = "/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java"

spec = importlib.util.spec_from_file_location("gen776", f"{TOOLS}/gen-776-translators.py")
gen776 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen776)

orig775, _, _ = gen776.extract_section_to_eof(Path(CT), "// 774 ⇔ 775（26.1）")
# 775 節の末尾には 776 節も含まれるため、776 節の手前で切る。
cut = orig775.find("// 775 ⇔ 776（26.2）")
assert cut >= 0, "776 節の区切りが見つからない"
orig775 = orig775[:cut]

new_component = gen776.build_component_down(orig775)
new_metadata = gen776.build_metadata_value_down(orig775)

text = open(CT, encoding="utf-8").read()
for name, new_text in [
    ("translateComponent776To775", new_component),
    ("translateMetadataValue776To775", new_metadata),
]:
    old_sig = f"private static boolean {name}(Reader reader, Writer writer, int "
    assert text.count(old_sig) == 1, f"{name}: シグネチャが1件でない"
    text = gen776.replace_method(text, old_sig, new_text, name)
    print(f"置き換えた: {name}")

open(CT, "w", encoding="utf-8").write(text)
print("完了。")
