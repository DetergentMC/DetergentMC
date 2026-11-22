"""PacketIds.java に 47 列を追加し、ProtocolVersion に 1.8 系の
名前対応を足す（47 Stage A）。

47 列の値は tools/packet-ids-47.py が minecraft-data pc/1.8 から
引いた結果（packet-ids-47.json）を使う。先に packet-ids-47.py を
--out で実行して JSON を用意すること。

使い方：
  python3 tools/packet-ids-47.py --out /tmp/packet-ids-47.json
  python3 tools/patch-47-packet-ids.py /tmp/packet-ids-47.json
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
    id47 = {name: r["id47"] for name, r in data.items()}

    src = PACKET_IDS.read_text(encoding="utf-8")
    assert "int id47;" not in src, "既に 47 列がある"

    # 1) 既存エントリの先頭引数として id47 を差し込む
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 40) + r"\)([,;])")
    count = 0

    def repl(m):
        nonlocal count
        name = m.group(1)
        assert name in id47, f"JSON に無いエントリ: {name}"
        count += 1
        head = (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{fmt(id47[name])}, ")
        vals = [m.group(4 + i) for i in range(40)]
        return head + ", ".join(vals) + ")" + m.group(44)

    src = pattern.sub(repl, src)
    assert count == 284, f"エントリ数が想定と違う: {count}"

    # 2) 47 にだけあるパケットの新規エントリ
    minus = ", ".join(["-1"] * 40)
    bulk = ("""        /** Chunk Data Bulk（クライアント行き）。47 にだけある
            （1.9 で廃止され、初期チャンクは個別の Chunk Data で届く
            ようになった）。47 バックエンドからはこれを個別チャンクへ
            分解して正準形に直す。 */
        PLAY_MAP_CHUNK_BULK(State.PLAY, Direction.CLIENTBOUND, 0x26, """
            + minus + "),\n")
    anchor = None
    for m in re.finditer(r"        PLAY_CHUNK_DATA\(State[^\n]*\n", src):
        anchor = m
    assert anchor, "PLAY_CHUNK_DATA が見つからない"
    src = src[:anchor.end()] + bulk + src[anchor.end():]

    nb = ("""        /** Set Compression（プレイ状態・クライアント行き）。47 に
            だけある（ログイン状態の LOGIN_SET_COMPRESSION とは別）。
            47 ではプレイ中にも圧縮しきい値の変更が来うる。 */
        PLAY_SET_COMPRESSION(State.PLAY, Direction.CLIENTBOUND, 0x46, """
          + minus + "),\n")
    anchor = None
    for m in re.finditer(r"        PLAY_MAP_CHUNK_BULK\(State[^\n]*\n", src):
        anchor = m
    src = src[:anchor.end()] + nb + src[anchor.end():]

    nbt = ("""        /** Update Entity NBT（クライアント行き）。47 にだけある
            （1.9 で廃止）。エンティティの NBT を丸ごと差し替える
            パケットで、正準形（758）に対応物が無いため変換では
            落とす。 */
        PLAY_UPDATE_ENTITY_NBT(State.PLAY, Direction.CLIENTBOUND, 0x49, """
           + minus + ");\n")
    # PLAY_ENTITY_METADATA のエントリ末尾（エントリは複数行にわたる
    # 場合があるため、エントリ開始から次の '),' までを探す）
    m = re.search(r"        PLAY_ENTITY_METADATA\(State\.", src)
    assert m, "PLAY_ENTITY_METADATA が見つからない"
    end = src.index("),\n", m.start()) + len("),\n")
    # 最後のエントリでなければ区切りは '),' のまま、新規は ';' でなく
    # ',' で足す必要がある。ここでは末尾挿入ではないため ',' にする。
    nbt = nbt.replace(");\n", "),\n")
    src = src[:end] + nbt + src[end:]

    # 3) フィールド・コンストラクタ・アクセサ・振り分け
    src = src.replace(
        "        private final int id107;",
        "        private final int id47;\n        private final int id107;", 1)
    src = src.replace(
        "PacketName(State state, Direction direction, int id107,",
        "PacketName(State state, Direction direction, int id47, int id107,", 1)
    src = src.replace(
        "            this.id107 = id107;",
        "            this.id47 = id47;\n            this.id107 = id107;", 1)
    doc47 = """        /** 47（1.8〜1.8.9 の全10リリース共通）でのID。存在しない場合は -1。packet-ids-47.py で、47 自身の protocol.json（minecraft-data pc/1.8、抽出元は 1.8.8。1.8.8 と 1.8.9 はクラス263件のバイトコード差のみでプロトコル同一）から引き直し、既存284エントリすべてが既存列からの名前同定で一意に定まることを照合済み。47 と 107 の両方に存在するパケットは表の範囲（status を除く）で104件、そのうち ID が違うのは96件（1.9 で play の ID 配置がほぼ全面組み替えになったため）。47 にだけあるパケットは3件（map_chunk_bulk 0x26、プレイ状態の set_compression 0x46、update_entity_nbt 0x49）で、本表に新規エントリとして追加した。47 で名前が違うパケットは2件（teams → scoreboard_team 0x3e、entity_update_attributes → update_attributes 0x20）。ワイヤ形式の差分は22件あり、特にチャンク形式（パレット無し・2バイトLE固定詰め・map_chunk_bulk）、entityMetadata 形式（bitfield ヘッダ・終端 0x7f・型8種）、エンティティ座標（i32 固定小数点）が別物（{@link io.detergentmc.protocol.ChunkTranslator} 参照）。Keep Alive は 107 と同じく varint で、応答 ID は 0x00。 */
        public int id47() {
            return id47;
        }

"""
    idx = src.index("        /** 107（1.9）でのID。")
    src = src[:idx] + doc47 + src[idx:]
    src = src.replace(
        """            if (protocol == ProtocolVersion.V1_9) {
                return id107;
            }""",
        """            if (protocol == ProtocolVersion.V1_8) {
                return id47;
            }
            if (protocol == ProtocolVersion.V1_9) {
                return id107;
            }""", 1)
    src = src.replace(
        "return List.of(ProtocolVersion.V1_9,",
        "return List.of(ProtocolVersion.V1_8, ProtocolVersion.V1_9,", 1)
    src = src.replace(
        """        return protocol == ProtocolVersion.V1_9
""",
        """        return protocol == ProtocolVersion.V1_8
                || protocol == ProtocolVersion.V1_9
""", 1)
    # ヘッダーのプロトコル列挙に 47 を足す
    src = src.replace(
        "状態×方向×パケット名×プロトコル（107 /",
        "状態×方向×パケット名×プロトコル（47 / 107 /", 1)
    src = src.replace(
        "<p>対応するプロトコルは 107（1.9）と",
        "<p>対応するプロトコルは 47（1.8〜1.8.9）と 107（1.9）と", 1)
    PACKET_IDS.write_text(src, encoding="utf-8")
    print(f"PacketIds.java: 47 列を追加（既存 {count} 件＋新規 3 件）")

    # 4) ProtocolVersion: 1.8 系の名前対応
    pv = PROTOCOL_VERSION.read_text(encoding="utf-8")
    assert '"1.8"' not in pv
    pv = pv.replace(
        """     *   <li>{@code "1.9"} → {@link #V1_9} (107);""",
        """     *   <li>{@code "1.8"} 〜 {@code "1.8.9"} → {@link #V1_8} (47);
     *       1.8 系の全10リリースがプロトコル 47 を共有する
     *       （全10版の実バニラの status 応答で確認済み。代表は
     *       1.8.9 の jar を内部サーバーに使う）</li>
     *   <li>{@code "1.9"} → {@link #V1_9} (107);""", 1)
    pv = pv.replace(
        "@return the internal server protocol number (107,",
        "@return the internal server protocol number (47, 107,", 1)
    names = " || ".join(f'"{v}".equals(minecraftVersion)'
                        for v in ["1.8", "1.8.1", "1.8.2", "1.8.3", "1.8.4",
                                  "1.8.5", "1.8.6", "1.8.7", "1.8.8", "1.8.9"])
    pv = pv.replace(
        """    public static int serverProtocolFor(String minecraftVersion) {
        if ("1.9".equals(minecraftVersion)) {""",
        f"""    public static int serverProtocolFor(String minecraftVersion) {{
        if ({names}) {{
            return V1_8;
        }}
        if ("1.9".equals(minecraftVersion)) {{""", 1)
    PROTOCOL_VERSION.write_text(pv, encoding="utf-8")
    print("ProtocolVersion.java: 1.8〜1.8.9 → 47 の対応を追加")
    return 0


if __name__ == "__main__":
    sys.exit(main())
