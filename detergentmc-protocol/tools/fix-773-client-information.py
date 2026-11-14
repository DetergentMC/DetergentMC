#!/usr/bin/env python3
"""ProxySession.java のプレイ状態 Settings（Client Information）の
particleStatus 付け足し条件を修正する（2026-11-12発見）。

背景：handleClientPlayTo773／To772／To771 の switch は
「768以下の 8 フィールド形式に particleStatus=0 を付ける。
769は 9 フィールド形式のためそのまま送る」という条件だったが、
これは誤り。minecraft-data の protocol.json で確認したところ、
particleStatus があるのは 768（1.21.3）からで、767（1.21.1）には無い。
packet_common_settings（768）は locale／viewDistance／chatFlags／
chatColors／skinParts／mainHand／enableTextFiltering／
enableServerListing／particleStatus の9フィールド。
設定フェーズ側のコード（handleClientConfiguration768 等）は
「768 は末尾に particleStatus がある」と正しく書いており、
プレイ側だけが誤っていた。

実害：768／770クライアントがプレイ中に設定を変更して Settings を
送り直すと、既に particleStatus がある9フィールドにさらにもう1個
付け足され、10フィールドの不正なパケットになる。バニラサーバーは
余分なバイトで切断する。設定フェーズの初回 Settings は設定フェーズ側の
正しい処理を通るため問題にならない。

修正：
- To773／To772：is769() → is768() || is769() || is770()
  （switch に到達するのは763〜770クライアント。771／772は先に振り分け済み）
- To771：is769() → is768() || is769()
  （switch に到達するのは763〜769クライアント。770は先に振り分け済み）
- To770：無条件の付け足し → is768() ? そのまま : 付け足し
  （switch に到達するのは763〜768クライアント。769は先に振り分け済み）
- To769／To768：switch に到達するのは767以下（8フィールド）だけのため
  無条件の付け足しで正しい。直さない。

使い方：リポジトリ直下で python3 detergentmc-protocol/tools/fix-773-client-information.py
各置換は件数を確認してから書き戻す（失敗時は書き戻さない）。
"""

import re
import sys

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"


def method_body(src, name):
    """name メソッドの本体（private 行〜自身の閉じ括弧まで）を返す。"""
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


def fix_to_nnn(src, name, server_ver, versions):
    """To773／To772／To771 の is769() 条件を直す。
    versions は素通しにするクライアント版のリスト（例：[768, 769, 770]）。
    """
    label = "／".join(str(v) for v in versions)
    cond = " || ".join(f"is{v}()" for v in versions)
    old = (
        "                // Settings：768以下の 8 フィールド形式に particleStatus=0 を付けて\n"
        f"                // {server_ver} 形式にする。付けないと {server_ver} サーバーが読み取り途中で切断する。\n"
        "                // 769は 9 フィールド形式（particleStatus 付き）のためそのまま送る。\n"
        "                byte[] payload = is769() ? packet.payload()\n"
        "                        : PlayTranslator.translateClientInformation767To768(\n"
        "                                packet.payload());\n"
    )
    new = (
        "                // Settings：767以下の 8 フィールド形式に particleStatus=0 を付けて\n"
        f"                // {server_ver} 形式にする。付けないと {server_ver} サーバーが読み取り途中で切断する。\n"
        f"                // {label}は 9 フィールド形式（particleStatus 付き）のためそのまま送る。\n"
        "                // （particleStatus があるのは768から。minecraft-data 1.21.3 の\n"
        "                // packet_common_settings で確認。従来の「768以下は8フィールド」は誤り。）\n"
        f"                byte[] payload = {cond} ? packet.payload()\n"
        "                        : PlayTranslator.translateClientInformation767To768(\n"
        "                                packet.payload());\n"
    )
    return replace_in_method(src, name, old, new)


def main():
    with open(PATH, encoding="utf-8") as f:
        src = f.read()
    orig = src

    # To773／To772：switch 到達は763〜770。768／769／770は素通し。
    src = fix_to_nnn(src, "handleClientPlayTo773", "773", [768, 769, 770])
    print("OK To773 Settings passthrough 768/769/770")
    src = fix_to_nnn(src, "handleClientPlayTo772", "772", [768, 769, 770])
    print("OK To772 Settings passthrough 768/769/770")
    # To771：switch 到達は763〜769。768／769は素通し。
    src = fix_to_nnn(src, "handleClientPlayTo771", "771", [768, 769])
    print("OK To771 Settings passthrough 768/769")

    # To770：switch 到達は763〜768。768は素通しへ。
    src = replace_in_method(
        src, "handleClientPlayTo770",
        "                // Settings：旧クライアントの 8 フィールド形式に particleStatus=0 を付けて\n"
        "                // 770 形式にする。付けないと 770 サーバーが読み取り途中で切断する。\n"
        "                byte[] payload = PlayTranslator.translateClientInformation767To768(\n"
        "                        packet.payload());\n",
        "                // Settings：767以下の 8 フィールド形式に particleStatus=0 を付けて\n"
        "                // 770 形式にする。付けないと 770 サーバーが読み取り途中で切断する。\n"
        "                // 768は 9 フィールド形式（particleStatus 付き）のためそのまま送る。\n"
        "                byte[] payload = is768() ? packet.payload()\n"
        "                        : PlayTranslator.translateClientInformation767To768(\n"
        "                                packet.payload());\n")
    print("OK To770 Settings passthrough 768")

    if src == orig:
        raise SystemExit("no changes made")
    with open(PATH, "w", encoding="utf-8") as f:
        f.write(src)
    print("DONE: wrote " + PATH)


if __name__ == "__main__":
    main()
