#!/usr/bin/env python3
"""gen-770-translators.py のドライラン：置き換え対象が1件ずつ存在するか確認する。
ファイルを書き換えない。"""
import importlib.util
import sys
from pathlib import Path

from pathlib import Path as _Path
_spec = importlib.util.spec_from_file_location("gen770tr", _Path(__file__).resolve().parent / "gen-770-translators.py")
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

ok = True

chunk_sec, cs, ce = mod.extract_section(
    mod.CT, "// 768 ⇔ 769（1.21.4）", "// NBT の読み書き")
play_sec, ps, pe = mod.extract_section_to_eof(
    mod.PT, "// 768 ⇔ 769（1.21.4）")
print(f"chunk section lines {cs+1}-{ce+1}, play section lines {ps+1}-{pe+1}")
chunk_shifted = mod.shift(chunk_sec)
play_shifted = mod.shift(play_sec)
chunk770 = mod.apply_fixes(chunk_shifted, mod.CHUNK_STRING_FIXES, "chunk-string")
print("string fixes: OK")

# メソッド置き換えのシグネチャが shifted テキストに1件ずつあるか。
for sig in mod.CHUNK_METHOD_REPLACEMENTS:
    n = chunk770.count(sig)
    if n != 1:
        print(f"NG: {sig!r} が {n} 件")
        ok = False
print(f"method replacements: {len(mod.CHUNK_METHOD_REPLACEMENTS)} 件を確認"
      + (" OK" if ok else " NGあり"))

# 769修正・jukebox修正のアンカーが実ファイルに1件ずつあるか。
ct = mod.CT.read_text(encoding="utf-8")
pt = mod.PT.read_text(encoding="utf-8")
for label, text, old in [
    ("fix769-attr-fwd", ct, mod.FIX_769_ATTR_768_TO_769_OLD),
    ("fix769-attr-rev", ct, mod.FIX_769_ATTR_769_TO_768_OLD),
    ("fix769-itemcost-fwd", ct, mod.FIX_769_ITEMCOST_768_TO_769_OLD),
    ("fix769-itemcost-rev", ct, mod.FIX_769_ITEMCOST_769_TO_768_OLD),
    ("play-jukebox", pt,
     "            if (registryId.equals(\"minecraft:jukebox_song\")\n"
     "                    && toProtocol != ProtocolVersion.V1_21\n"
     "                    && toProtocol != ProtocolVersion.V1_21_2\n"
     "                    && toProtocol != ProtocolVersion.V1_21_4) {"),
]:
    n = text.count(old)
    print(f"{label}: {n} 件" + (" OK" if n == 1 else " NG"))
    if n != 1:
        ok = False

# ガード：770節がまだ無いこと。
for label, text, guard in [
    ("chunk-guard", ct, "translateSlot769To770"),
    ("play-guard", pt, "translatePlayerChat769To770"),
]:
    print(f"{label}: {'既に存在する NG' if guard in text else '未挿入 OK'}")
    if guard in text:
        ok = False

# 置き換え後の構文（Javaのメソッド数が合うか）を簡易確認：
# replace_method を全て適用し、{ } の数を数える。
tmp = chunk770
for sig, new_text in mod.CHUNK_METHOD_REPLACEMENTS.items():
    tmp = mod.replace_method(tmp, sig, new_text, "chunk-dry")
print(f"chunk770 after replacements: {len(tmp)} chars, "
      f"braces open={tmp.count('{')} close={tmp.count('}')}")
if tmp.count("{") != tmp.count("}"):
    print("NG: 波括弧の数が合わない")
    ok = False
play = mod.PLAY_770_SECTION
print(f"play770 section: {len(play)} chars, "
      f"braces open={play.count('{')} close={play.count('}')}")
if play.count("{") != play.count("}"):
    print("NG: Play の波括弧の数が合わない")
    ok = False

# 削除の検証：shift した狼ホルダー（void 版）と IDSet は消え、正しい版だけ残る。
for label, needle, expect in [
    ("wolf-fwd-bool", "private static boolean translateWolfVariantHolder769To770", 1),
    ("wolf-fwd-void", "private static void translateWolfVariantHolder769To770", 0),
    ("wolf-rev", "private static void translateWolfVariantHolder770To769", 1),
    ("idset-fwd", "translateIdSetBiomes769To770", 0),
    ("idset-rev", "translateIdSetBiomes770To769", 0),
    ("ench-up770", "mapEnchantmentUp770", 0),
    ("attr-up770", "mapAttributeUp770", 0),
    ("trim-up770", "mapTrimPatternUp770", 0),
    ("ench-770to769", "mapEnchantment770To769", 1),
    ("attr-770to769", "mapAttribute770To769", 2),  # 属性修飾子＋Update Attributes の2箇所
    ("trim-770to769", "mapTrimPattern770To769", 1),
]:
    n = tmp.count(needle)
    status = "OK" if n == expect else "NG"
    print(f"{label}: {n} 件（期待 {expect}）{status}")
    if n != expect:
        ok = False
# imports のアンカー。
n = ct.count("import java.util.ArrayList;\nimport java.util.List;\nimport java.util.Set;")
print(f"chunk-imports anchor: {n} 件" + (" OK" if n == 1 else " NG"))
if n != 1:
    ok = False

print("DRY RUN " + ("OK" if ok else "NG"))
sys.exit(0 if ok else 1)
