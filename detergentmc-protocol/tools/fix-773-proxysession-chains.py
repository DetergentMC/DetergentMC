#!/usr/bin/env python3
"""ProxySession.java の773連鎖飛ばし（2026-11-12発見）を修正する。

背景：gen-773-proxysession.py が772の分岐を写すとき、版番号だけを
ずらしたため、一段目の変換が丸ごと落ちた箇所がある。具体的には：

1. handleServerPlayFrom771 の is773() 分岐が
   handleServerPlay772To773(packet) を直接呼んでいた。
   771形式のパケットを772→773の翻訳器に渡すため、レジストリIDが
   一段ずれる（例：Set Slot の music_disc_mall が771:1256→773:1304
   になる。正しくは771:1256→772:1257→773:1305）。
   同じ誤りが handleServerPlayFrom770 の is773() 分岐にもある
   （770形式を直接772→773へ）。
2. handleClientPlayTo773 に is771() の振り分けが無い。
   772段階の handleClientPlayTo772 は先頭で is771() を
   handleClientPlay771To772 へ振っていたが、773の生成器が
   is771()→is772() にずらしたため振り分け自体が消えた。
   771クライアントの Set Creative Slot は switch の creative case
   （is767() しか扱わない）に落ちて落とされていた。

修正：二段／三段の連鎖メソッドを新設し、分岐をそれらへ向ける。
- handleServerPlay771To773（771→772→773）
- handleServerPlay770To773（770→771→772→773）
- handleClientPlay771To773（771→772→773）

注意：handleServerPlayFrom770 の is772() 分岐
（handleServerPlay771To772(packet) を770形式に直接当てる）は
HEAD（1155adf）からある既存の不具合で、772クライアント向けの
経路であるためここでは直さない（最終報告に記載する）。
本スクリプトが直すのは773クライアント／773サーバー向けの経路だけ。

使い方：リポジトリ直下で python3 detergentmc-protocol/tools/fix-773-proxysession-chains.py
各置換は件数を確認してから書き戻す（失敗時は書き戻さない）。
"""

import re
import sys

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"


def method_body(src, name):
    """name メソッドの本体（private 行〜自身の閉じ括弧まで）を返す。
    終端は行頭4スペースの閉じ括弧。次のメソッドの javadoc は含めない。
    """
    m = re.search(r"    private Outcome " + name + r"\(MinecraftPacket packet\) \{", src)
    if not m:
        raise SystemExit("method not found: " + name)
    start = m.start()
    end = src.find("\n    }\n", start)
    if end < 0:
        raise SystemExit("method end not found: " + name)
    end += len("\n    }\n")
    return src[start:end]


def replace_in_method(src, name, old, new, expect=1):
    """name メソッドの範囲内だけで old を new に置き換える。"""
    m = re.search(r"    private Outcome " + name + r"\(MinecraftPacket packet\) \{", src)
    if not m:
        raise SystemExit("method not found: " + name)
    start = m.start()
    end = src.find("\n    }\n", start)
    if end < 0:
        raise SystemExit("method end not found: " + name)
    end += len("\n    }\n")
    body = src[start:end]
    count = body.count(old)
    if count != expect:
        raise SystemExit(
            f"{name}: expected {expect} occurrence(s) of old text, found {count}")
    return src[:start] + body.replace(old, new) + src[end:]


def main():
    with open(PATH, encoding="utf-8") as f:
        src = f.read()
    orig = src

    # --- 1. サーバー→クライアント：771→772→773 の連鎖メソッドを新設 ---
    anchor = method_body(src, "handleServerPlay769To773")
    new_methods = '''

    /**
     * 内部771→773クライアントのプレイ状態（サーバー→クライアント）。
     * 771→772→773へ二段で変換する。
     * From771 の is773() 分岐は生成器が一段目（771→772）を落としていたため、
     * このメソッド経由で呼ぶ（fix-773-proxysession-chains.py）。
     */
    private Outcome handleServerPlay771To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay771To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }

    /**
     * 内部770→773クライアントのプレイ状態（サーバー→クライアント）。
     * 770→771→772→773へ三段で変換する。
     * From770 の is773() 分岐は生成器が二段目まで（770→771→772）を落としていたため、
     * このメソッド経由で呼ぶ（fix-773-proxysession-chains.py）。
     */
    private Outcome handleServerPlay770To773(MinecraftPacket packet) {
        Outcome to771 = handleServerPlay770To771(packet);
        if (to771.toClient().isEmpty()) {
            return to771;
        }
        Outcome to772 = handleServerPlay771To772(to771.toClient().get(0));
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }
'''
    if "handleServerPlay771To773" in src or "handleServerPlay770To773" in src:
        raise SystemExit("chain methods already exist; refusing to insert twice")
    src = src.replace(anchor, anchor + new_methods, 1)
    print("OK insert handleServerPlay771To773 + handleServerPlay770To773")

    # --- 2. From771 の is773() 分岐を連鎖メソッドへ ---
    src = replace_in_method(
        src, "handleServerPlayFrom771",
        "        // 773クライアント：772→773へ変換する。\n"
        "        if (is773()) {\n"
        "            return handleServerPlay772To773(packet);\n"
        "        }",
        "        // 773クライアント：771→772→773へ二段で変換する。\n"
        "        if (is773()) {\n"
        "            return handleServerPlay771To773(packet);\n"
        "        }")
    print("OK From771 is773 -> handleServerPlay771To773")

    # --- 3. From770 の is773() 分岐を連鎖メソッドへ ---
    src = replace_in_method(
        src, "handleServerPlayFrom770",
        "        // 773クライアント：772→773へ変換する。\n"
        "        if (is773()) {\n"
        "            return handleServerPlay772To773(packet);\n"
        "        }",
        "        // 773クライアント：770→771→772→773へ三段で変換する。\n"
        "        if (is773()) {\n"
        "            return handleServerPlay770To773(packet);\n"
        "        }")
    print("OK From770 is773 -> handleServerPlay770To773")

    # --- 4. クライアント→サーバー：771→772→773 の連鎖メソッドを新設 ---
    anchor = method_body(src, "handleClientPlay772To773")
    new_method = '''

    /**
     * 771クライアント→内部773のプレイ状態（クライアント→サーバー）。
     * 771→772→773へ二段で変換する。
     * handleClientPlayTo773 は生成器が is771() の振り分けを落としていたため、
     * このメソッド経由で呼ぶ（fix-773-proxysession-chains.py）。
     */
    private Outcome handleClientPlay771To773(MinecraftPacket packet) {
        Outcome to772 = handleClientPlay771To772(packet);
        if (to772.toServer().isEmpty()) {
            return to772;
        }
        return handleClientPlay772To773(to772.toServer().get(0));
    }
'''
    if "handleClientPlay771To773" in src:
        raise SystemExit("handleClientPlay771To773 already exists")
    src = src.replace(anchor, anchor + new_method, 1)
    print("OK insert handleClientPlay771To773")

    # --- 5. handleClientPlayTo773 の先頭に is771() の振り分けを追加 ---
    src = replace_in_method(
        src, "handleClientPlayTo773",
        "    private Outcome handleClientPlayTo773(MinecraftPacket packet) {\n"
        "        if (is772()) {\n"
        "            return handleClientPlay772To773(packet);\n"
        "        }\n",
        "    private Outcome handleClientPlayTo773(MinecraftPacket packet) {\n"
        "        if (is772()) {\n"
        "            return handleClientPlay772To773(packet);\n"
        "        }\n"
        "        if (is771()) {\n"
        "            return handleClientPlay771To773(packet);\n"
        "        }\n")
    print("OK handleClientPlayTo773 dispatches is771()")

    if src == orig:
        raise SystemExit("no changes made")
    with open(PATH, "w", encoding="utf-8") as f:
        f.write(src)
    print("DONE: wrote " + PATH)


if __name__ == "__main__":
    main()
