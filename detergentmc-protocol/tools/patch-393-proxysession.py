#!/usr/bin/env python3
"""ProxySession.java に 393（1.13）の配線を追加する。

やること：
 1. 旧クライアント／旧サーバーの列挙に is393()／isServer393() を足す
    （形式が 404 と同一の分岐。Login Start・Login Success・リソース
    パック提示・Join Game 763 経路など）。
 2. 専用分岐を足す（クライアント行きチェーン、内部サーバーの経路、
    サーバー行きの付け替え、内部サーバーへの下りチェーン）。
 3. 404 のディスパッチ群（translateServerPlayPacket758To404＋
    readDimension 2件、routeServerPlay404＋to758ServerPackets404、
    to758ClientPacket404、to404ClientPacket758）を写して 393 版を
    作る。付け替えは V1_13_2→V1_13、1.13.2→1.13、404→393。
    393 のパケットIDは 401 と同一で 404 とも数値が一致するものが
    多いが、名前引きは必ず 393 で行う（正しさのため）。
    レジストリ番号は RegistryRemapper の 393 表が担当する。

使い方：
  python3 tools/patch-393-proxysession.py
  冪等ではない（各置換の出現回数を検証し、合わなければ中断する）。
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


def method_span(text, signature, next_signature):
    """signature のメソッドについて、直前の /** から next_signature の
    直前の /** までの範囲を返す（開始・終了インデックス）。"""
    sig = text.index(signature)
    start = text.rfind("    /**", 0, sig)
    nsig = text.index(next_signature)
    end = text.rfind("    /**", 0, nsig)
    return start, end


def transform(block):
    if "0x" in block and re.search(r"0x[0-9a-fA-F]*404", block):
        raise SystemExit("16進リテラルに 404 が含まれています。手作業が必要です")
    return (block.replace("V1_13_2", "V1_13")
                 .replace("1.13.2", "1.13")
                 .replace("404", "393"))


def main():
    text = P.read_text(encoding="utf-8")
    if "isServer393()" in text:
        print("skip（既存）: 393 の配線はすでにある")
        return 0

    # ---- 1. 列挙の拡張 -------------------------------------------------
    text = replace(text,
        "(is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759())",
        "(is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759())", 1)
    text = replace(text,
        "return is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
        "return is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();", 1)
    text = replace(text,
        "if (is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401()) {",
        "if (is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393()) {", 2)
    text = replace(text,
        "(isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401())",
        "(isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393())", 6)
    text = replace(text,
        "isServer404() || isServer401() || isServer477() || isServer480() || isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573()",
        "isServer404() || isServer401() || isServer477() || isServer480() || isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573() || isServer393()", 1)
    text = replace(text,
        "(isServer763Like() || isServer760() || isServer759() || isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401())",
        "(isServer763Like() || isServer760() || isServer759() || isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393())", 1)
    text = replace(text,
        "!(is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401())",
        "!(is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393())", 1)
    text = replace(text,
        "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480() || is477() || is404() || is401()) {",
        "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480() || is477() || is404() || is401() || is393()) {", 2)
    text = replace(text,
        "if (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759() || is404() || is401()) {",
        "if (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759() || is404() || is401() || is393()) {", 1)
    text = replace(text,
        "if (is404() || is401() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {",
        "if (is393() || is404() || is401() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {", 1)
    text = replace(text,
        "// 401／404／477／480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。",
        "// 393／401／404／477／480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。", 1)

    # ---- 2. フィールド --------------------------------------------------
    text = insert_after(text,
        "    private int dimension404Server;\n",
        """
    /** 393クライアント向け変換で使う現在のディメンションID（393形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension393Client;

    /** 393サーバー向け変換で使う現在のディメンションID（393形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension393Server;
""")

    # ---- 3. クライアント行きチェーンに 393 ------------------------------
    text = insert_before(text,
        "        if (is578() || is575() || is573()) {\n            // 578クライアント：",
        """        if (is393()) {
            // 393クライアント：763→762→761→760→759→758→393へ六段で変換する。
            // その後スロットだけ旧形式（401 と同一の符号化）へ付け替える。
            Outcome to762 = convertServerPlayOutcomeTo762(outcome);
            Outcome to761 = convertServerPlayOutcomeTo761(to762);
            Outcome to760 = convertServerPlayOutcomeTo760(to761);
            Outcome to759 = convertServerPlayOutcomeTo759(to760);
            Outcome to758 = convertServerPlayOutcomeTo758(to759);
            Outcome to393 = convertServerPlayOutcomeTo393(to758);
            return convertServerPlayOutcomeTo393Slots(to393);
        }
""")

    # ---- 4. 内部サーバーの経路に 393 ------------------------------------
    text = insert_after(text,
        """        if (isServer404() || isServer401()) {
            if (isServer401()) {
                packet = SlotTranslator.clientboundFrom401(packet);
            }
            return routeServerPlay404(packet);
        }
""",
        """        // 内部393：先に758形式へ直してから、758サーバーと同じ経路に乗せる。
        // 光はチャンクに内蔵される。Player Info は393・758で同一形式。
        // スロットだけ旧形式のため、先に現行形式へ付け替えてから扱う
        // （アイテム番号は393のまま。404 とは番号が違うため経路は分ける）。
        if (isServer393()) {
            packet = SlotTranslator.clientboundFrom393(packet);
            return routeServerPlay393(packet);
        }
""")

    # ---- 5. サーバー行き（クライアント発）に 393 ------------------------
    text = insert_after(text,
        """                packet = to758ClientPacket404(packet);
                if (packet == null) {
                    return Outcome.drop();
                }
""",
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
""")

    # ---- 6. 内部サーバーへの下りチェーンに 393 --------------------------
    text = insert_after(text,
        """                if (isServer401()) {
                    p404 = SlotTranslator.clientboundTo401(p404);
                }
                toServer.add(p404);
            }
            return new Outcome(toServer, to763.toClient(), to763.relay(),
                    to763.closeAfterSend());
        }
""",
        """        // 内部393：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から393形式へ変換して送る。その後スロットだけ
        // 旧形式へ付け替える。
        if (isServer393()) {
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
                MinecraftPacket p393 = to393ClientPacket758(p758);
                if (p393 == null) {
                    continue;
                }
                p393 = SlotTranslator.clientboundTo393(p393);
                toServer.add(p393);
            }
            return new Outcome(toServer, to763.toClient(), to763.relay(),
                    to763.closeAfterSend());
        }
""")

    # ---- 7. ヘルパー ------------------------------------------------------
    text = insert_after(text,
        """    /** 内部が 1.13.1（401）か。 */
    private boolean isServer401() {
        return serverProtocol == ProtocolVersion.V1_13_1;
    }
""",
        """
    /**
     * クライアントが 1.13（393）か。ワイヤ形式はスロットの符号化を
     * 除いて 404 と同一世代だが、レジストリ番号が 404 と違う
     * （dead coral 5種が無く、プロパティ追加も無い）ため、変換経路は
     * 404 と分け、スロットだけ {@link SlotTranslator} で付け替える。
     */
    private boolean is393() {
        return clientProtocol == ProtocolVersion.V1_13;
    }

    /** 内部が 1.13（393）か。 */
    private boolean isServer393() {
        return serverProtocol == ProtocolVersion.V1_13;
    }
""")
    text = insert_after(text,
        """    /** 404 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */
    private static boolean hasSkyLight404(int dimensionId404) {
        return dimensionId404 != -1;
    }
""",
        """
    /** 393 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */
    private static boolean hasSkyLight393(int dimensionId393) {
        return dimensionId393 != -1;
    }
""")
    text = insert_after(text,
        """    private Outcome convertServerPlayOutcomeTo401(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.add(SlotTranslator.clientboundTo401(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }
""",
        """
    private Outcome convertServerPlayOutcomeTo393(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.addAll(translateServerPlayPacket758To393(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }

    /**
     * 393形式まで変換した結果を、393クライアント向けにスロットだけ
     * 旧形式へ付け替える（393 の slot 型は 401 と同一の旧形式）。
     */
    private Outcome convertServerPlayOutcomeTo393Slots(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.add(SlotTranslator.clientboundTo393(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }
""")

    # ---- 8. メソッド複写 ---------------------------------------------------
    # C1: translateServerPlayPacket758To404 ＋ readDimension×2
    s, e = method_span(text,
        "private List<MinecraftPacket> translateServerPlayPacket758To404(MinecraftPacket packet)",
        "private Outcome routeServerPlay404(MinecraftPacket packet)")
    text = text[:e] + "\n" + transform(text[s:e]) + text[e:]
    # C2: routeServerPlay404 ＋ to758ServerPackets404
    s, e = method_span(text,
        "private Outcome routeServerPlay404(MinecraftPacket packet)",
        "private MinecraftPacket to758ClientPacket404(MinecraftPacket packet)")
    text = text[:e] + "\n" + transform(text[s:e]) + text[e:]
    # C3: to758ClientPacket404
    s, e = method_span(text,
        "private MinecraftPacket to758ClientPacket404(MinecraftPacket packet)",
        "private MinecraftPacket to404ClientPacket758(MinecraftPacket packet)")
    text = text[:e] + "\n" + transform(text[s:e]) + text[e:]
    # C4: to404ClientPacket758
    s, e = method_span(text,
        "private MinecraftPacket to404ClientPacket758(MinecraftPacket packet)",
        "private List<MinecraftPacket> translateServerPlayPacket759To758(MinecraftPacket packet)")
    text = text[:e] + "\n" + transform(text[s:e]) + text[e:]

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java に 393 の配線を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
