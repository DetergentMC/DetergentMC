#!/usr/bin/env python3
"""PacketIds.java に 760 列を差し込む。

~/workspace/mcdata760/packet-ids-760.json（packet-ids-760.py の出力）を読み、
各エントリの 14 個の引数（761〜774）の先頭に 760 の ID を挿入する。
さらに 760 にだけある 5 パケット（チャットプレビュー系・
Message Header・Named Sound Effect）を新規エントリとして追加する。
legacy_server_list_ping（0xfe）はハンドシェイク／ステータス系で
この表の対象外のため入れない。

使い方: python3 tools/insert-760-packet-ids.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path.home() / "workspace" / "mcdata760" / "packet-ids-760.json"

# 760 にだけあるパケット（minecraft-data の protocol.json で確認）。
# 761 でチャットプレビューが廃止され、Message Header は Player Chat へ
# 統合され、Named Sound Effect は Sound Effect（名前付きホルダー）へ
# 統合された。feature_flags・player_remove・profileless_chat・
# chat_session_update は 761 で追加されたため、こちら側には無い。
NEW_ENTRIES = [
    # (挿入先の直前のエントリ名, 新エントリの行)
    ("PLAY_CLEAR_TITLES",
     "        /** Chat Preview。760 にだけある（1.19.3 でチャットプレビュー廃止）。\n"
     "            クライアント行き。サーバーはプレビューの内容を送る。 */\n"
     "        PLAY_CHAT_PREVIEW(State.PLAY, Direction.CLIENTBOUND, "
     "0x0c, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),"),
    ("PLAY_SOUND_EFFECT",
     "        /** Named Sound Effect。760 にだけある（名前で音を鳴らす）。\n"
     "            761 以降は Sound Effect の名前付きホルダーへ統合。 */\n"
     "        PLAY_NAMED_SOUND_EFFECT(State.PLAY, Direction.CLIENTBOUND, "
     "0x17, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),"),
    ("PLAY_PLAYER_CHAT",
     "        /** Message Header。760 にだけある（署名付きチャットの署名部）。\n"
     "            761 以降は Player Chat に統合。Player Chat の直前に送る。 */\n"
     "        PLAY_MESSAGE_HEADER(State.PLAY, Direction.CLIENTBOUND, "
     "0x32, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),"),
    ("PLAY_PLAYER_INFO_UPDATE",
     "        /** Should Display Chat Preview。760 にだけある（1.19.3 で廃止）。\n"
     "            クライアント行き。プレビュー表示の可否を送る。 */\n"
     "        PLAY_SHOULD_DISPLAY_CHAT_PREVIEW(State.PLAY, Direction.CLIENTBOUND, "
     "0x4e, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),"),
    ("PLAY_TAB_COMPLETE_TO_SERVER",
     "        /** Chat Preview。760 にだけある（1.19.3 でチャットプレビュー廃止）。\n"
     "            サーバー行き。クライアントがプレビューを要求する。 */\n"
     "        PLAY_CHAT_PREVIEW_TO_SERVER(State.PLAY, Direction.SERVERBOUND, "
     "0x06, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),"),
]


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    text = P.read_text(encoding="utf-8")
    if "int id760" in text:
        print("skip（既存）: 760 列はすでにある")
        return 0

    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+)\)([,;])")

    def repl(m):
        name = m.group(1)
        if name not in data:
            print(f"JSON に無いエントリ: {name}", file=sys.stderr)
            raise SystemExit(1)
        id760 = fmt(data[name]["id760"])
        args = ", ".join(m.group(i) for i in range(4, 18))
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{id760}, {args}){m.group(18)}")

    text, n = pattern.subn(repl, text)
    print(f"760 列を挿入: {n} 件")
    if n != len(data):
        print(f"件数が合わない: 挿入 {n} 件 / JSON {len(data)} 件", file=sys.stderr)
        raise SystemExit(1)

    # 新規エントリ 5 件
    for anchor_name, entry in NEW_ENTRIES:
        anchor_pat = re.compile(
            r"(        " + anchor_name + r"\(State\.\w+, Direction\.\w+, "
            r"(?:-1|0x[0-9a-fA-F]+)(?:, (?:-1|0x[0-9a-fA-F]+)){14}\)[,;]\n)")
        m = anchor_pat.search(text)
        if not m:
            print(f"アンカーが見つからない: {anchor_name}", file=sys.stderr)
            raise SystemExit(1)
        text = text[:m.end()] + entry + "\n" + text[m.end():]
        print(f"新規エントリを追加: {anchor_name} の次")

    # フィールド・コンストラクタ・アクセサ・idFor
    text = text.replace("        private final int id761;\n",
                        "        private final int id760;\n"
                        "        private final int id761;\n", 1)
    text = text.replace(
        "        PacketName(State state, Direction direction, int id761, int id762, int id763, int id764, int id765, int id766,\n",
        "        PacketName(State state, Direction direction, int id760, int id761, int id762, int id763, int id764, int id765, int id766,\n",
        1)
    text = text.replace("            this.id761 = id761;\n",
                        "            this.id760 = id760;\n"
                        "            this.id761 = id761;\n", 1)
    text = text.replace(
        '        /** 761（1.19.3）でのID。存在しない場合は -1。 */\n'
        "        public int id761() {\n"
        "            return id761;\n"
        "        }\n",
        '        /** 760（1.19.1／1.19.2）でのID。存在しない場合は -1。 */\n'
        "        public int id760() {\n"
        "            return id760;\n"
        "        }\n"
        "\n"
        '        /** 761（1.19.3）でのID。存在しない場合は -1。 */\n'
        "        public int id761() {\n"
        "            return id761;\n"
        "        }\n",
        1)
    text = text.replace(
        "            if (protocol == ProtocolVersion.V1_19_3) {\n"
        "                return id761;\n"
        "            }\n",
        "            if (protocol == ProtocolVersion.V1_19_1) {\n"
        "                return id760;\n"
        "            }\n"
        "            if (protocol == ProtocolVersion.V1_19_3) {\n"
        "                return id761;\n"
        "            }\n",
        1)
    for marker in ("private final int id760;", "int id760, int id761",
                   "this.id760 = id760;", "public int id760()",
                   "ProtocolVersion.V1_19_1"):
        if marker not in text:
            print(f"挿入できなかった: {marker}", file=sys.stderr)
            raise SystemExit(1)
    P.write_text(text, encoding="utf-8")
    print("PacketIds.java に 760 列を差し込んだ。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
