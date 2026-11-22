"""PacketIds.java に 5 列を追加し、ProtocolVersion に 1.7.6〜1.7.10 の
名前対応を足す（5 Stage A）。

5 列の値は tools/packet-ids-5.py が minecraft-data pc/1.7 から
引いた結果（packet-ids-5.json）を使う。先に packet-ids-5.py を
--out で実行して JSON を用意すること。

使い方：
  python3 tools/packet-ids-5.py --out /tmp/packet-ids-5.json
  python3 tools/patch-5-packet-ids.py /tmp/packet-ids-5.json
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
    id5 = {name: r["id5"] for name, r in data.items()}

    src = PACKET_IDS.read_text(encoding="utf-8")
    assert "int id5;" not in src, "既に 5 列がある"

    # 1) 既存エントリの先頭引数として id5 を差し込む（現在は41引数）
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 41) + r"\)([,;])")
    count = 0

    def repl(m):
        nonlocal count
        name = m.group(1)
        assert name in id5, f"JSON に無いエントリ: {name}"
        count += 1
        head = (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{fmt(id5[name])}, ")
        vals = [m.group(4 + i) for i in range(41)]
        return head + ", ".join(vals) + ")" + m.group(45)

    src = pattern.sub(repl, src)
    assert count == 287, f"エントリ数が想定と違う: {count}"

    # 2) フィールド・コンストラクタ・アクセサ・振り分け
    src = src.replace(
        "        private final int id47;",
        "        private final int id5;\n        private final int id47;", 1)
    src = src.replace(
        "PacketName(State state, Direction direction, int id47,",
        "PacketName(State state, Direction direction, int id5, int id47,", 1)
    src = src.replace(
        "            this.id47 = id47;",
        "            this.id5 = id5;\n            this.id47 = id47;", 1)
    doc5 = """        /** 5（1.7.6〜1.7.10 の全5リリース共通）でのID。存在しない場合は -1。packet-ids-5.py で、5 自身の protocol.json（minecraft-data pc/1.7、抽出元は 1.7.10）から引き直し、全287エントリが既存列からの名前同定で一意に定まることを照合済み。**5 と 47 の両方に存在するパケットは表の範囲（status を除く）で95件、そのうち ID が違うのは0件**（1.8 は ID 配置を変えずに12件を追加しただけ）。47 にだけあるパケットは12件（login compress、camera、combat_event、difficulty、playerlist_header、resource_pack_send、play の set_compression、title、update_entity_nbt、world_border、resource_pack_receive、spectate）で、5 ではすべて -1。5 にだけあるパケットは0件。map_chunk_bulk は 5 にも 0x26 で存在する。5 で名前が違うパケットは47と同じ2件（teams → scoreboard_team 0x3e、entity_update_attributes → update_attributes 0x20）。ワイヤ形式の差分は60パケットに及ぶが大半は系統的な型差分（entityId i32⇔varint、ブロック座標のバラ数値⇔64bit詰め、Keep Alive i32⇔varint）で、チャンクの中身（u8 ID配列＋nibble群、bulk のヘッダ配置）と player_info／named_entity_spawn の形が別物（{@link io.detergentmc.protocol.ChunkTranslator} 参照）。**4／5 にはパケット圧縮の仕組み自体が無い**。Keep Alive は i32 で、ID は 47 と同じ 0x00。 */
        public int id5() {
            return id5;
        }

"""
    idx = src.index("        /** 47（1.8〜1.8.9 の全10リリース共通）でのID。")
    src = src[:idx] + doc5 + src[idx:]
    src = src.replace(
        """            if (protocol == ProtocolVersion.V1_8) {
                return id47;
            }""",
        """            if (protocol == ProtocolVersion.V1_7_10) {
                return id5;
            }
            if (protocol == ProtocolVersion.V1_8) {
                return id47;
            }""", 1)
    src = src.replace(
        "return List.of(ProtocolVersion.V1_8,",
        "return List.of(ProtocolVersion.V1_7_10, ProtocolVersion.V1_8,", 1)
    src = src.replace(
        """        return protocol == ProtocolVersion.V1_8
""",
        """        return protocol == ProtocolVersion.V1_7_10
                || protocol == ProtocolVersion.V1_8
""", 1)
    # ヘッダーのプロトコル列挙に 5 を足す
    src = src.replace(
        "状態×方向×パケット名×プロトコル（47 /",
        "状態×方向×パケット名×プロトコル（5 / 47 /", 1)
    src = src.replace(
        "<p>対応するプロトコルは 47（1.8〜1.8.9）と",
        "<p>対応するプロトコルは 5（1.7.6〜1.7.10）と 47（1.8〜1.8.9）と", 1)
    PACKET_IDS.write_text(src, encoding="utf-8")
    print(f"PacketIds.java: 5 列を追加（既存 {count} 件、新規エントリなし）")

    # 3) ProtocolVersion: 定数と 1.7.6〜1.7.10 の名前対応
    pv = PROTOCOL_VERSION.read_text(encoding="utf-8")
    assert "V1_7_10" not in pv
    pv = pv.replace(
        """    /** Minecraft 1.8 - 1.8.9 */
    public static final int V1_8 = 47;""",
        """    /** Minecraft 1.7.6 - 1.7.10（5版ともプロトコル5） */
    public static final int V1_7_10 = 5;
    /** Minecraft 1.8 - 1.8.9 */
    public static final int V1_8 = 47;""", 1)
    pv = pv.replace(
        """     *   <li>{@code "1.8"} 〜 {@code "1.8.9"} → {@link #V1_8} (47);""",
        """     *   <li>{@code "1.7.6"} 〜 {@code "1.7.10"} → {@link #V1_7_10} (5);
     *       1.7.6〜1.7.10 の全5リリースがプロトコル 5 を共有する
     *       （全5版の実バニラの status 応答で確認済み。代表は
     *       1.7.10 の jar を内部サーバーに使う。1.7.2〜1.7.5 は
     *       プロトコル 4 で別対応）</li>
     *   <li>{@code "1.8"} 〜 {@code "1.8.9"} → {@link #V1_8} (47);""", 1)
    pv = pv.replace(
        "@return the internal server protocol number (47,",
        "@return the internal server protocol number (5, 47,", 1)
    names = " || ".join(f'"{v}".equals(minecraftVersion)'
                        for v in ["1.7.6", "1.7.7", "1.7.8", "1.7.9",
                                  "1.7.10"])
    pv = pv.replace(
        """    public static int serverProtocolFor(String minecraftVersion) {
        if ("1.8".equals(minecraftVersion)""",
        f"""    public static int serverProtocolFor(String minecraftVersion) {{
        if ({names}) {{
            return V1_7_10;
        }}
        if ("1.8".equals(minecraftVersion)""", 1)
    PROTOCOL_VERSION.write_text(pv, encoding="utf-8")
    print("ProtocolVersion.java: V1_7_10 定数と 1.7.6〜1.7.10 → 5 の対応を追加")
    return 0


if __name__ == "__main__":
    sys.exit(main())
