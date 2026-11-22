#!/usr/bin/env python3
"""ProxySession.java に 340（1.12.2）の配線を追加する。

やること：
 1. 旧クライアント／旧サーバーの列挙に is340()／isServer340() を足す
    （形式が 393 と同一の分岐。Login Start・Login Success・リソース
    パック提示・Join Game 763 経路など）。最古を 340 に下げても
    393・401・404 が列挙から落ちないよう、既存の列挙へ追加だけする。
 2. 専用分岐を足す（クライアント行きチェーン、内部サーバーの経路、
    サーバー行きの付け替え、内部サーバーへの下りチェーン）。
 3. 393 のディスパッチ群（translateServerPlayPacket758To393＋
    readEffectId／readDimension×2、routeServerPlay393＋
    to758ServerPackets393、to758ClientPacket393、to393ClientPacket758）
    を写して 340 版を作る。付け替えは V1_13→V1_12_2、1.13→1.12.2、
    393→340（いずれも後続の .1／.2 や桁続きを除く）。
    パケットIDは名前引きを必ず 340 で行う。レジストリ番号は
    RegistryRemapper の 340 表が担当する。

使い方：
  python3 tools/patch-340-proxysession.py
  冪等ではない（各置換の出現回数を検証し、合わなければ中断する）。
  適用後の手直し（エフェクト ±1、レシピ系の除去、340 に無い
  パケットの除去、形式差分12件のうち未対応分の調整）は別途行う。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ProxySession.java"


def replace(text, old, new, count):
    n = text.count(old)
    if n != count:
        raise SystemExit(f"置換回数が合いません（{n} != {count}）: {old[:70]}...")
    return text.replace(old, new)


def insert_after(text, anchor, addition):
    if anchor not in text:
        raise SystemExit(f"アンカーが見つかりません: {anchor[:70]}...")
    return text.replace(anchor, anchor + addition, 1)


def insert_before(text, anchor, addition):
    if anchor not in text:
        raise SystemExit(f"アンカーが見つかりません: {anchor[:70]}...")
    return text.replace(anchor, addition + anchor, 1)


def method_span(text, signature):
    """signature のメソッド1個の範囲（直前の /** から波括弧の対応が
    閉じるまで）を返す。ProxySession はバージョンごとのメソッドが
    混在するレイアウトのため、範囲はメソッド単位で取る。"""
    sig = text.index(signature)
    start = sig
    doc = text.rfind("    /**", 0, sig)
    if doc >= 0:
        doc_end = text.index("*/", doc) + 2
        between = text[doc_end:sig]
        if ";" not in between and "}" not in between and "{" not in between:
            start = doc
    brace = text.index("{", sig)
    depth = 0
    i = brace
    while True:
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return start, i + 1
        i += 1


def transform(block):
    if re.search(r"0x[0-9a-fA-F]*393", block):
        raise SystemExit("16進リテラルに 393 が含まれています。手作業が必要です")
    block = re.sub(r"V1_13(?![_0-9])", "V1_12_2", block)
    block = re.sub(r"1\.13(?!\.\d)", "1.12.2", block)
    block = re.sub(r"(?<![0-9])393(?![0-9])", "340", block)
    return block


def main():
    text = P.read_text(encoding="utf-8")
    if "isServer340()" in text:
        print("skip（既存）: 340 の配線はすでにある")
        return 0

    # ---- 1. 列挙の拡張 -------------------------------------------------
    text = replace(text,
        "(is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759())",
        "(is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759())", 1)
    text = replace(text,
        "return is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
        "return is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();", 1)
    text = replace(text,
        "if (is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393()) {",
        "if (is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393() || is340()) {", 2)
    text = replace(text,
        "(isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393())",
        "(isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393() || isServer340())", 6)
    text = replace(text,
        "isServer404() || isServer401() || isServer477() || isServer480() || isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573() || isServer393()",
        "isServer404() || isServer401() || isServer477() || isServer480() || isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573() || isServer393() || isServer340()", 1)
    text = replace(text,
        "(isServer763Like() || isServer760() || isServer759() || isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393())",
        "(isServer763Like() || isServer760() || isServer759() || isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393() || isServer340())", 1)
    text = replace(text,
        "!(is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393())",
        "!(is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393() || is340())", 1)
    text = replace(text,
        "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480() || is477() || is404() || is401() || is393()) {",
        "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480() || is477() || is404() || is401() || is393() || is340()) {", 2)
    text = replace(text,
        "if (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759() || is404() || is401() || is393()) {",
        "if (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759() || is404() || is401() || is393() || is340()) {", 1)
    text = replace(text,
        "if (is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {",
        "if (is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {", 1)
    text = replace(text,
        "// 393／401／404／477／480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。",
        "// 340／393／401／404／477／480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。", 1)
    # コンストラクタの serverProtocol 検証
    text = replace(text,
        "        if (serverProtocol != ProtocolVersion.V1_13\n                && serverProtocol != ProtocolVersion.V1_13_1",
        "        if (serverProtocol != ProtocolVersion.V1_12_2\n                && serverProtocol != ProtocolVersion.V1_13\n                && serverProtocol != ProtocolVersion.V1_13_1", 1)
    text = replace(text,
        '"serverProtocol must be 393 (1.13), 401 (1.13.1), 404 (1.13.2), 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), ',
        '"serverProtocol must be 340 (1.12.2), 393 (1.13), 401 (1.13.1), 404 (1.13.2), 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), ', 1)

    # ---- 2. フィールド --------------------------------------------------
    text = insert_after(text,
        "    private int dimension393Server;\n",
        """
    /** 340クライアント向け変換で使う現在のディメンションID（340形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension340Client;

    /** 340サーバー向け変換で使う現在のディメンションID（340形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension340Server;
""")

    # ---- 3. クライアント行きチェーンに 340 ------------------------------
    text = insert_before(text,
        "        if (is393()) {\n            // 393クライアント：",
        """        if (is340()) {
            // 340クライアント：763→762→761→760→759→758→340へ六段で変換する。
            // その後スロット等の実ワイヤ差分（itemDamage・型 i8・粒子 ID i32）
            // を SlotTranslator の 340 層で付け替える。
            Outcome to762 = convertServerPlayOutcomeTo762(outcome);
            Outcome to761 = convertServerPlayOutcomeTo761(to762);
            Outcome to760 = convertServerPlayOutcomeTo760(to761);
            Outcome to759 = convertServerPlayOutcomeTo759(to760);
            Outcome to758 = convertServerPlayOutcomeTo758(to759);
            Outcome to340 = convertServerPlayOutcomeTo340(to758);
            return convertServerPlayOutcomeTo340Slots(to340);
        }
""")

    # ---- 4. 内部サーバーの経路に 340 ------------------------------------
    text = insert_after(text,
        """        if (isServer393()) {
            packet = SlotTranslator.clientboundFrom393(packet);
            return routeServerPlay393(packet);
        }
""",
        """        // 内部340：先にハイブリッド形式（404符号化＋340番号）へ直してから、
        // 758サーバーと同じ経路に乗せる。光はチャンクに内蔵される。
        // Player Info は340・758で同一形式。
        if (isServer340()) {
            packet = SlotTranslator.clientboundFrom340(packet);
            return routeServerPlay340(packet);
        }
""")

    # ---- 5. サーバー行き（クライアント発）に 340 ------------------------
    text = insert_after(text,
        """            } else if (is393()) {
                // 393クライアント：パケットIDが758と違うものがあるため、
                // 先に758形式へ直してから、758クライアントと同じ経路に乗せる。
                // 758形式に変換できないものは落とす。
                // スロットだけ旧形式のため、先に現行形式へ付け替える。
                packet = SlotTranslator.serverboundFrom393(packet);
                packet = to758ClientPacket393(packet);
                if (packet == null) {
                    return Outcome.drop();
                }
""",
        """            } else if (is340()) {
                // 340クライアント：パケットIDが758と違うものが多いため、
                // 先に758形式へ直してから、758クライアントと同じ経路に乗せる。
                // 758形式に変換できないものは落とす。
                // スロット等の実ワイヤ差分は先にハイブリッド形式へ付け替える。
                packet = SlotTranslator.serverboundFrom340(packet);
                packet = to758ClientPacket340(packet);
                if (packet == null) {
                    return Outcome.drop();
                }
""")

    # ---- 6. 内部サーバーへの下りチェーンに 340 --------------------------
    text = insert_after(text,
        """                p393 = SlotTranslator.clientboundTo393(p393);
                toServer.add(p393);
            }
            return new Outcome(toServer, to763.toClient(), to763.relay(),
                    to763.closeAfterSend());
        }
""",
        """        // 内部340：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から340形式（ハイブリッド）へ変換して送る。その後
        // スロット等の実ワイヤ差分を SlotTranslator の 340 層で付け替える。
        if (isServer340()) {
            Outcome to763 = is763Like()
                    ? Outcome.toServer(packet)
                    : handleClientPlayTo763(packet);
            List<MinecraftPacket> toServer = new ArrayList<>();
            for (MinecraftPacket p : to763.toServer()) {
                MinecraftPacket p762 = to762ClientPacket(p);
                if (p762 == null) {
                    continue;
                }
                MinecraftPacket p761 = to761ClientPacket762(p762);
                if (p761 == null) {
                    continue;
                }
                MinecraftPacket p760 = to760ClientPacket761(p761);
                if (p760 == null) {
                    continue;
                }
                MinecraftPacket p759 = to759ClientPacket760(p760);
                if (p759 == null) {
                    continue;
                }
                MinecraftPacket p758 = to758ClientPacket759(p759);
                if (p758 == null) {
                    continue;
                }
                MinecraftPacket p340 = to340ClientPacket758(p758);
                if (p340 == null) {
                    continue;
                }
                p340 = SlotTranslator.clientboundTo340(p340);
                toServer.add(p340);
            }
            return new Outcome(toServer, to763.toClient(), to763.relay(),
                    to763.closeAfterSend());
        }
""")

    # ---- 7. ヘルパー ------------------------------------------------------
    text = insert_after(text,
        """    /** 内部が 1.13（393）か。 */
    private boolean isServer393() {
        return serverProtocol == ProtocolVersion.V1_13;
    }
""",
        """
    /**
     * クライアントが 1.12.2（340）か。ブロックに状態が無く
     * (数値ID&lt;&lt;4)|メタデータで表す世代で、レジストリは全面に
     * 393 と違う。変換経路は 393 と分け、実ワイヤ差分（スロットの
     * itemDamage、メタデータ型 i8、粒子 ID i32）は {@link SlotTranslator}
     * の 340 層で付け替える。
     */
    private boolean is340() {
        return clientProtocol == ProtocolVersion.V1_12_2;
    }

    /** 内部が 1.12.2（340）か。 */
    private boolean isServer340() {
        return serverProtocol == ProtocolVersion.V1_12_2;
    }
""")
    text = insert_after(text,
        """    /** 393 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */
    private static boolean hasSkyLight393(int dimensionId393) {
        return dimensionId393 != -1;
    }
""",
        """
    /** 340 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */
    private static boolean hasSkyLight340(int dimensionId340) {
        return dimensionId340 != -1;
    }
""")
    text = insert_after(text,
        """    private Outcome convertServerPlayOutcomeTo393Slots(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.add(SlotTranslator.clientboundTo393(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }
""",
        """
    private Outcome convertServerPlayOutcomeTo340(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.addAll(translateServerPlayPacket758To340(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }

    /**
     * 340形式（ハイブリッド）まで変換した結果を、340クライアント向けに
     * 実ワイヤ差分（スロットの itemDamage 等）だけ付け替える。
     */
    private Outcome convertServerPlayOutcomeTo340Slots(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.add(SlotTranslator.clientboundTo340(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }
""")

    # ---- 8. メソッド複写（メソッド単位。後ろの位置から処理する） --------
    copy_targets = [
        "private MinecraftPacket to393ClientPacket758(MinecraftPacket packet)",
        "private MinecraftPacket to758ClientPacket393(MinecraftPacket packet)",
        "private List<MinecraftPacket> to758ServerPackets393(MinecraftPacket packet)",
        "private Outcome routeServerPlay393(MinecraftPacket packet)",
        "private static int readDimension393FromRespawn(byte[] payload393)",
        "private static int readDimension393FromJoinGame(byte[] payload393)",
        "private static int readEffectId393(byte[] payload, boolean addEffect)",
        "private List<MinecraftPacket> translateServerPlayPacket758To393(MinecraftPacket packet)",
    ]
    spans = []
    for sig in copy_targets:
        s, e = method_span(text, sig)
        spans.append((s, e))
    spans.sort(key=lambda se: se[0], reverse=True)
    for s, e in spans:
        text = text[:e] + "\n\n" + transform(text[s:e]) + text[e:]

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java に 340 の配線を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
