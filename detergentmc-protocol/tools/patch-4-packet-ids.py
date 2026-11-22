"""PacketIds.java に 4 列を追加し、ProtocolVersion に 1.7.2〜1.7.5 の
名前対応を足す（4 Stage A）。

4 列の値は tools/packet-ids-4.py が出した JSON（5 列の複写。
4⇔5 の ID 同一は実ワイヤ採取で裏付け済み）を使う。先に
packet-ids-4.py を --out で実行して JSON を用意すること。

使い方：
  python3 tools/packet-ids-4.py --out /tmp/packet-ids-4.json
  python3 tools/patch-4-packet-ids.py /tmp/packet-ids-4.json
冪等ではない。git diff で確認すること。
"""

import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PROTO = SCRIPT_DIR.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
PACKET_IDS = PROTO / "PacketIds.java"
PROTOCOL_VERSION = PROTO / "ProtocolVersion.java"


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    id4 = {name: r["id4"] for name, r in data.items()}

    src = PACKET_IDS.read_text(encoding="utf-8")
    assert "int id4;" not in src, "既に 4 列がある"

    # 1) 既存エントリの先頭引数として id4 を差し込む（現在は42引数）
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 42) + r"\)([,;])")
    count = 0

    def repl(m):
        nonlocal count
        name = m.group(1)
        assert name in id4, f"JSON に無いエントリ: {name}"
        count += 1
        head = (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{fmt(id4[name])}, ")
        vals = [m.group(4 + i) for i in range(42)]
        return head + ", ".join(vals) + ")" + m.group(46)

    src = pattern.sub(repl, src)
    assert count == 287, f"エントリ数が想定と違う: {count}"

    # 2) フィールド・コンストラクタ・アクセサ・振り分け
    src = src.replace(
        "        private final int id5;",
        "        private final int id4;\n        private final int id5;", 1)
    src = src.replace(
        "PacketName(State state, Direction direction, int id5,",
        "PacketName(State state, Direction direction, int id4, int id5,", 1)
    src = src.replace(
        "            this.id5 = id5;",
        "            this.id4 = id4;\n            this.id5 = id5;", 1)
    doc4 = """        /** 4（1.7.2〜1.7.5 の全4リリース共通）でのID。存在しない場合は -1。**4 の ID は 5 と完全同一**（packet-ids-4.py 参照：minecraft-data pc/1.7 は 1.7.10＝プロトコル 5 専用で 4 用の個別データは無く、Stage A の実ワイヤ採取で観測した ID 集合が 5 の列の部分集合であること、二人採取で named_entity_spawn の ID（0x0c）一致を確認済み）。4⇔5 の差分はワイヤ形式の2件だけ：Login Success と named_entity_spawn の UUID 文字列がダッシュ無し32文字（5 はダッシュ付き36文字）、named_entity_spawn のプロパティ配列が 4 には無い。 */
        public int id4() {
            return id4;
        }

"""
    idx = src.index("        /** 5（1.7.6〜1.7.10 の全5リリース共通）でのID。")
    src = src[:idx] + doc4 + src[idx:]
    src = src.replace(
        """            if (protocol == ProtocolVersion.V1_7_10) {
                return id5;
            }""",
        """            if (protocol == ProtocolVersion.V1_7_2) {
                return id4;
            }
            if (protocol == ProtocolVersion.V1_7_10) {
                return id5;
            }""", 1)
    src = src.replace(
        "return List.of(ProtocolVersion.V1_7_10, ProtocolVersion.V1_8,",
        "return List.of(ProtocolVersion.V1_7_2, ProtocolVersion.V1_7_10, ProtocolVersion.V1_8,", 1)
    src = src.replace(
        """        return protocol == ProtocolVersion.V1_7_10
                || protocol == ProtocolVersion.V1_8""",
        """        return protocol == ProtocolVersion.V1_7_2
                || protocol == ProtocolVersion.V1_7_10
                || protocol == ProtocolVersion.V1_8""", 1)
    PACKET_IDS.write_text(src, encoding="utf-8")

    # 3) ProtocolVersion：定数と名前対応
    pv = PROTOCOL_VERSION.read_text(encoding="utf-8")
    assert "V1_7_2" not in pv, "ProtocolVersion に既に V1_7_2 がある"
    pv = pv.replace(
        """    /** Minecraft 1.7.6 - 1.7.10（5版ともプロトコル5） */
    public static final int V1_7_10 = 5;""",
        """    /** Minecraft 1.7.2 - 1.7.5（4版ともプロトコル4） */
    public static final int V1_7_2 = 4;
    /** Minecraft 1.7.6 - 1.7.10（5版ともプロトコル5） */
    public static final int V1_7_10 = 5;""", 1)
    pv = pv.replace(
        """     *   <li>{@code "1.7.6"} 〜 {@code "1.7.10"} → {@link #V1_7_10} (5);""",
        """     *   <li>{@code "1.7.2"} 〜 {@code "1.7.5"} → {@link #V1_7_2} (4);
     *       1.7.2〜1.7.5 の全4リリースがプロトコル 4 を共有する
     *       （全4版の実バニラの status 応答で確認済み。1.7.3 も
     *       実在する製品版で 4 に属す。代表は 1.7.2 の jar を
     *       内部サーバーに使う。1.7.6〜1.7.10 はプロトコル 5）</li>
     *   <li>{@code "1.7.6"} 〜 {@code "1.7.10"} → {@link #V1_7_10} (5);""", 1)
    pv = pv.replace(
        """     *       1.7.10 の jar を内部サーバーに使う。1.7.2〜1.7.5 は
     *       プロトコル 4 で別対応）</li>""",
        """     *       1.7.10 の jar を内部サーバーに使う）</li>""", 1)
    pv = pv.replace(
        "     * @return the internal server protocol number (5, 47,",
        "     * @return the internal server protocol number (4, 5, 47,", 1)
    pv = pv.replace(
        """        if ("1.7.6".equals(minecraftVersion)""",
        """        if ("1.7.2".equals(minecraftVersion) || "1.7.3".equals(minecraftVersion) || "1.7.4".equals(minecraftVersion) || "1.7.5".equals(minecraftVersion)) {
            return V1_7_2;
        }
        if ("1.7.6".equals(minecraftVersion)""", 1)
    PROTOCOL_VERSION.write_text(pv, encoding="utf-8")
    print("PacketIds に 4 列（287エントリ）と ProtocolVersion の 1.7.2〜1.7.5 対応を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
