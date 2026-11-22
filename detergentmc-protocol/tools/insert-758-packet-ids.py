#!/usr/bin/env python3
"""PacketIds.java に 758 列を差し込む。

/tmp/mcdata/packet-ids-758.json（packet-ids-758.py の出力）を読み、
各エントリの 17 個の引数（759〜775）の先頭に 758 の ID を挿入する。
758 にだけあるパケットは 5 件（legacy_server_list_ping（0xfe）を除く。
あれはハンドシェイク／ステータス系のためこの表の対象外）：
  - spawn_entity_living（プレイ・クライアント行き 0x02）
  - spawn_entity_painting（プレイ・クライアント行き 0x03）
  - sculk_vibration_signal（プレイ・クライアント行き 0x05）
  - chat（プレイ・クライアント行き 0x0f。署名なしの旧式チャット）
  - chat（プレイ・サーバー行き 0x03。署名なしの旧式チャット）
これらは新規エントリとして追加する（760のときの PLAY_MESSAGE_HEADER
などの前例と同じ）。

使い方: python3 tools/insert-758-packet-ids.py [--data FILE]
--data を省略すると /tmp/mcdata/packet-ids-758.json を使う。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path("/tmp/mcdata/packet-ids-758.json")

NEW_ENTRIES = """        /** Spawn Entity Living。758 にだけある（生き物の出現）。
            759 以降は Spawn Entity に統合。 */
        PLAY_SPAWN_ENTITY_LIVING(State.PLAY, Direction.CLIENTBOUND, 0x02, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Spawn Entity Painting。758 にだけある（絵画の出現）。
            759 以降は Spawn Entity に統合。 */
        PLAY_SPAWN_ENTITY_PAINTING(State.PLAY, Direction.CLIENTBOUND, 0x03, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Sculk Vibration Signal。758 にだけある（スカルク振動の信号）。
            759 で廃止され、振動は Game Event などで送られる。 */
        PLAY_SCULK_VIBRATION_SIGNAL(State.PLAY, Direction.CLIENTBOUND, 0x05, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
"""


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=DATA)
    args = parser.parse_args()

    data = json.loads(args.data.read_text(encoding="utf-8"))
    text = P.read_text(encoding="utf-8")
    if "int id758" in text:
        print("skip（既存）: 758 列はすでにある")
        return 0

    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 17) + r"\)([,;])")

    def repl(m):
        name = m.group(1)
        if name not in data:
            print(f"JSON に無いエントリ: {name}", file=sys.stderr)
            raise SystemExit(1)
        id758 = fmt(data[name]["id758"])
        args = ", ".join(m.group(i) for i in range(4, 21))
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{id758}, {args}){m.group(21)}")

    text, n = pattern.subn(repl, text)
    print(f"758 列を挿入: {n} 件")
    if n != len(data):
        print(f"件数が合わない: 挿入 {n} 件 / JSON {len(data)} 件", file=sys.stderr)
        raise SystemExit(1)

    # 758 にだけあるパケットの新規エントリ。
    # Spawn Entity のエントリの直後に挿入する（758 列の挿入後、行は
    # 18 引数で 0x00 が 4 個続く）。
    spawn_re = re.compile(
        r"(        PLAY_SPAWN_ENTITY\(State\.PLAY, Direction\.CLIENTBOUND, "
        r"0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, "
        r"0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01\),\n)")
    m = spawn_re.search(text)
    if not m:
        print("PLAY_SPAWN_ENTITY の行が見つからない", file=sys.stderr)
        raise SystemExit(1)
    text = text[:m.end(1)] + NEW_ENTRIES + text[m.end(1):]
    print("758 限定の出現系パケット 3 件を追加")

    # 758 の旧式チャット（クライアント行き）は Player Chat の直前に置く。
    chat_anchor = ("        /** Player Chat Message。 */\n"
                   "        PLAY_PLAYER_CHAT(State.PLAY, Direction.CLIENTBOUND, ")
    idx = text.find(chat_anchor)
    if idx < 0:
        print("PLAY_PLAYER_CHAT の行が見つからない", file=sys.stderr)
        raise SystemExit(1)
    chat_entry = ("        /** Chat Message。758 にだけある（署名なしの旧式チャット）。\n"
                  "            759 以降は Player Chat／System Chat に分かれた。 */\n"
                  "        PLAY_CHAT(State.PLAY, Direction.CLIENTBOUND, 0x0f, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),\n")
    text = text[:idx] + chat_entry + text[idx:]
    print("758 限定のクライアント行きチャット 1 件を追加")

    # 758 の旧式チャット（サーバー行き）は Chat Message の直前に置く。
    chat_srv_anchor = "        PLAY_CHAT_MESSAGE(State.PLAY, Direction.SERVERBOUND, "
    idx = text.find(chat_srv_anchor)
    if idx < 0:
        print("PLAY_CHAT_MESSAGE の行が見つからない", file=sys.stderr)
        raise SystemExit(1)
    # 直前の javadoc 行の先頭まで戻る。
    javadoc_start = text.rfind("        /**", 0, idx)
    chat_srv_entry = ("        /** Chat Message。758 にだけある（署名なしの旧式チャット）。\n"
                      "            759 以降は Chat Message（署名付き）／Chat Command に分かれた。 */\n"
                      "        PLAY_CHAT_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x03, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),\n")
    text = text[:javadoc_start] + chat_srv_entry + text[javadoc_start:]
    print("758 限定のサーバー行きチャット 1 件を追加")

    # Named Entity Spawn のコメントは 758 も含むよう直す。
    text = text.replace(
        "        /** Named Entity Spawn。761〜763 にだけある（プレイヤーの出現）。762 以降の Spawn Entity とは別パケット。 */",
        "        /** Named Entity Spawn。758〜763 にある（プレイヤーの出現）。764 以降の Spawn Entity とは別パケット。 */",
        1)

    # フィールド・コンストラクタ・アクセサ・idFor
    text = text.replace("        private final int id759;\n",
                        "        private final int id758;\n"
                        "        private final int id759;\n", 1)
    text = text.replace(
        "        PacketName(State state, Direction direction, int id759, int id760, int id761, int id762, int id763, int id764, int id765, int id766,\n",
        "        PacketName(State state, Direction direction, int id758, int id759, int id760, int id761, int id762, int id763, int id764, int id765, int id766,\n",
        1)
    text = text.replace("            this.id759 = id759;\n",
                        "            this.id758 = id758;\n"
                        "            this.id759 = id759;\n", 1)
    text = text.replace(
        '        /** 759（1.19）でのID。存在しない場合は -1。 */\n'
        "        public int id759() {\n"
        "            return id759;\n"
        "        }\n",
        '        /** 758（1.18.2）でのID。存在しない場合は -1。 */\n'
        "        public int id758() {\n"
        "            return id758;\n"
        "        }\n"
        "\n"
        '        /** 759（1.19）でのID。存在しない場合は -1。 */\n'
        "        public int id759() {\n"
        "            return id759;\n"
        "        }\n",
        1)
    text = text.replace(
        "            if (protocol == ProtocolVersion.V1_19) {\n"
        "                return id759;\n"
        "            }\n",
        "            if (protocol == ProtocolVersion.V1_18_2) {\n"
        "                return id758;\n"
        "            }\n"
        "            if (protocol == ProtocolVersion.V1_19) {\n"
        "                return id759;\n"
        "            }\n",
        1)
    # supportedProtocols() と isSupported() に V1_18_2 を入れる。
    text = text.replace(
        "        return List.of(ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,\n",
        "        return List.of(ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,\n",
        1)
    text = text.replace(
        "        return protocol == ProtocolVersion.V1_19\n",
        "        return protocol == ProtocolVersion.V1_18_2\n"
        "                || protocol == ProtocolVersion.V1_19\n",
        1)
    for marker in ("private final int id758;", "int id758, int id759",
                   "this.id758 = id758;", "public int id758()",
                   "ProtocolVersion.V1_18_2)", "ProtocolVersion.V1_18_2, ",
                   "PLAY_SPAWN_ENTITY_LIVING", "PLAY_SPAWN_ENTITY_PAINTING",
                   "PLAY_SCULK_VIBRATION_SIGNAL", "PLAY_CHAT(",
                   "PLAY_CHAT_TO_SERVER"):
        if marker not in text:
            print(f"挿入できなかった: {marker}", file=sys.stderr)
            raise SystemExit(1)
    P.write_text(text, encoding="utf-8")
    print("PacketIds.java に 758 列と 758 限定パケット 5 件を差し込んだ。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
