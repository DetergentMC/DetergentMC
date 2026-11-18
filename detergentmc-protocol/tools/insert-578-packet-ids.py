#!/usr/bin/env python3
"""PacketIds.java に 578 列を差し込む。

/tmp/mcdata/packet-ids-578.json（packet-ids-578.py の出力）を読み、
各エントリの 19 個の引数（758〜776）の先頭に 578 の ID を挿入する。
578 にだけあるパケットは 8 件（legacy_server_list_ping（0xfe）を除く。
あれはハンドシェイク／ステータス系のためこの表の対象外）：
  - spawn_entity_weather（プレイ・クライアント行き 0x02。落雷の出現）
  - transaction（プレイ・クライアント行き 0x13。旧式の確認応答）
  - entity（プレイ・クライアント行き 0x2c。entityId だけのパケット）
  - combat_event（プレイ・クライアント行き 0x33。戦闘イベント統合）
  - world_border（プレイ・クライアント行き 0x3e。単一パケット版）
  - title（プレイ・クライアント行き 0x50。単一パケット版）
  - transaction（プレイ・サーバー行き 0x07。旧式の確認応答）
  - crafting_book_data（プレイ・サーバー行き 0x1d。レシピ本の統合版）
これらは新規エントリとして追加する（758のときの前例と同じ）。
PLAY_SPECTATOR_ACTION は packet-ids-578.py では識別できない
（758〜775 の全てで -1。776 でだけ追加されたパケット）ため、
578 では -1 として扱う。

使い方: python3 tools/insert-578-packet-ids.py [--data FILE]
--data を省略すると /tmp/mcdata/packet-ids-578.json を使う。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path("/tmp/mcdata/packet-ids-578.json")

NEW_ENTRIES = """        /** Spawn Entity Weather。578 にだけある（落雷の出現）。
            758 以降は Spawn Entity に統合。 */
        PLAY_SPAWN_ENTITY_WEATHER(State.PLAY, Direction.CLIENTBOUND, 0x02, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Transaction（クライアント行き）。578 にだけある（旧式の
            Window Confirmation。windowId＋action＋accepted）。
            758 以降はプレイ状態では使われない。 */
        PLAY_TRANSACTION(State.PLAY, Direction.CLIENTBOUND, 0x13, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Entity。578 にだけある（entityId だけを持つパケット）。 */
        PLAY_ENTITY(State.PLAY, Direction.CLIENTBOUND, 0x2c, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Combat Event。578 にだけある（戦闘の開始・終了・死亡を
            1パケットに統合）。758 以降は Enter／End／Death に分割。 */
        PLAY_COMBAT_EVENT(State.PLAY, Direction.CLIENTBOUND, 0x33, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** World Border。578 にだけある（アクションで分岐する単一
            パケット版）。758 以降は Initialize と個別 setter に分割。 */
        PLAY_WORLD_BORDER(State.PLAY, Direction.CLIENTBOUND, 0x3e, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Title。578 にだけある（アクションで分岐する単一パケット版）。
            758 以降は Set Title Text／Subtitle／Time に分割。 */
        PLAY_TITLE(State.PLAY, Direction.CLIENTBOUND, 0x50, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Transaction（サーバー行き）。578 にだけある（旧式の
            Window Confirmation への応答）。 */
        PLAY_TRANSACTION_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x07, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Crafting Book Data。578 にだけある（レシピ本の表示レシピと
            開閉状態を1パケットに統合）。758 以降は分割された。 */
        PLAY_CRAFTING_BOOK_DATA(State.PLAY, Direction.SERVERBOUND, 0x1d, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1);
"""

# packet-ids-578.py で識別できないエントリ（776 でのみ追加）の 578 ID。
SPECIAL = {"PLAY_SPECTATOR_ACTION": -1}


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=DATA)
    args = parser.parse_args()

    data = json.loads(args.data.read_text(encoding="utf-8"))
    text = P.read_text(encoding="utf-8")
    if "int id578" in text:
        print("skip（既存）: 578 列はすでにある")
        return 0

    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 19) + r"\)([,;])")

    def repl(m):
        name = m.group(1)
        if name in data:
            id578 = fmt(data[name]["id578"])
        elif name in SPECIAL:
            id578 = fmt(SPECIAL[name])
        else:
            print(f"JSON に無いエントリ: {name}", file=sys.stderr)
            raise SystemExit(1)
        args19 = ", ".join(m.group(i) for i in range(4, 23))
        tail = m.group(23)
        if tail == ";":
            # 最後のエントリ：新しいエントリ群を続ける。
            return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                    f"{id578}, {args19}),\n" + NEW_ENTRIES.rstrip())
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{id578}, {args19}){tail}")

    text, n = pattern.subn(repl, text)
    print(f"エントリ書き換え: {n} 件")
    if n < 200:
        print("エントリ数が想定より少ない。中止。", file=sys.stderr)
        return 1

    # フィールド・コンストラクタ・アクセサ・idFor・supportedProtocols・isSupported。
    text = text.replace(
        "        private final int id758;",
        "        private final int id578;\n        private final int id758;", 1)
    text = text.replace(
        "PacketName(State state, Direction direction, int id758,",
        "PacketName(State state, Direction direction, int id578, int id758,", 1)
    text = text.replace(
        "            this.id758 = id758;",
        "            this.id578 = id578;\n            this.id758 = id758;", 1)
    text = text.replace(
        """        /** 758（1.18.2）でのID。存在しない場合は -1。 */
        public int id758() {""",
        """        /** 578（1.15.2）でのID。存在しない場合は -1。 */
        public int id578() {
            return id578;
        }

        /** 758（1.18.2）でのID。存在しない場合は -1。 */
        public int id758() {""", 1)
    text = text.replace(
        """            if (protocol == ProtocolVersion.V1_18_2) {
                return id758;
            }""",
        """            if (protocol == ProtocolVersion.V1_15_2) {
                return id578;
            }
            if (protocol == ProtocolVersion.V1_18_2) {
                return id758;
            }""", 1)
    text = text.replace(
        "return List.of(ProtocolVersion.V1_18_2,",
        "return List.of(ProtocolVersion.V1_15_2, ProtocolVersion.V1_18_2,", 1)
    text = text.replace(
        """        return protocol == ProtocolVersion.V1_18_2""",
        """        return protocol == ProtocolVersion.V1_15_2
                || protocol == ProtocolVersion.V1_18_2""", 1)
    # 先頭のプロトコル一覧コメントに 578 を追加する。
    text = text.replace(
        "パケットID表：状態×方向×パケット名×プロトコル（758 / 759 /",
        "パケットID表：状態×方向×パケット名×プロトコル（578 / 758 / 759 /", 1)

    P.write_text(text, encoding="utf-8")
    print("PacketIds.java を書き換えた。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
