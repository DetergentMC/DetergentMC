#!/usr/bin/env python3
"""PacketIds.java に 756 列を挿入する（18引数 → 19引数）。

packet-ids-756.py が作った packet-ids-756.json（272件の写像）を使い、
コンストラクタ呼び出しの先頭に `0xXX, `（無い場合は `-1, `）を足す。
756で新規のパケットは 0件のため、新規エントリの追加は無い。
コンストラクタ・id756() アクセサ・idFor／supportedProtocols／isSupported・
javadoc も756対応にする。

※ 777版（insert-777-java.py）との違い：777は末尾への追加だったが、
   756は先頭への挿入になる。引数の並びは 756, 757, 759, 760, ..., 775。
   終端子のグループ番号は 19引数版でも group(23) になる。
   757版の作りは tools/patch-packet-ids-775.py にならったもの
   （insert-775-java.py は RegistryRemapper 用で PacketIds とは関係ない）。

775版の罠（group番号の数え直し）：
  18引数の正規表現ではグループは name=1・state=2・direction=3・
  ID（757〜775）=4〜21、終端子（`,`/`;`）=22 である。
  このスクリプトではグループ番号を数え直し、m.group(22) を終端子と
  して使う。pattern.groups == 22 であることも実行時に確認する。
  19引数版ではグループは name=1・state=2・direction=3・
  ID（756〜775）=4〜22、終端子=23 になる。

新規エントリは 0件（packet-ids-756.py の報告：1.17.1にあって表に無い
パケット 0件、757で -1・756で値ありのエントリ 0件）。

削除1件（PLAY_SIMULATION_DISTANCE）は写像の id756=-1 を入れて
エントリ自体は残す。757ですでに -1 の古いエントリも写像通り -1 のままにする。

使い方：
  python3 tools/insert-756-java.py
  終了コード 0 = 272件の既存エントリ全件に756列を挿入し、既存の18個のIDが
  変わっていないことを確認した。
  1 = 件数不一致／写像に無いエントリ／18引数版の残存／アンカー無し。

注意：このスクリプトは ProtocolVersion.V1_17_1 を参照する idFor／
supportedProtocols／isSupported の分岐も追加する。ProtocolVersion.java
に V1_17_1 がまだ無いため、追加されるまでこのファイルはコンパイル
できない。V1_17_1 の追加は Stage A の次の作業（ProtocolVersion.java
への 756 追加）で行うこと。Gradleはまだ無いためコンパイル・テストは
この作業では行わない。
"""

import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

ID_PAT = r"(-1|0x[0-9a-fA-F]+)"
PROTOCOLS_18 = (757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                770, 771, 772, 773, 774, 775)

# 756で削除される1件。id756=-1 であることを実行時に確認する。
# Set Simulation Distance は 757（1.18）で追加されたため 756 には無い。
REMOVED_IN_756 = ["PLAY_SIMULATION_DISTANCE"]


def fmt(value):
    return "-1" if value < 0 else f"0x{value:02x}"


def entry_pattern(n_ids):
    """n引数版のエントリ正規表現。グループ番号：
    name=1・state=2・direction=3・ID=4〜(3+n)・終端子=(4+n)。
    18引数版は終端子が group(22)、19引数版は group(23)。"""
    return re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([ID_PAT] * n_ids) + r"\)([,;])")


def parse_entries(text, n_ids):
    """エントリを {名前: (状態, 方向, [ID...], 終端子)} で返す。"""
    pattern = entry_pattern(n_ids)
    assert pattern.groups == 4 + n_ids, \
        f"グループ数の数え間違い：{pattern.groups}（期待値 {4 + n_ids}）"
    out = {}
    for m in pattern.finditer(text):
        ids = [int(g, 0) for g in m.groups()[3:3 + n_ids]]
        assert len(ids) == n_ids
        out[m.group(1)] = (m.group(2), m.group(3), ids, m.group(4 + n_ids))
    return out


def insert_after(text, anchor, block, label):
    """anchor の直後に block を挿入する。anchor が無ければ None を返す。"""
    if anchor not in text:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return text.replace(anchor, anchor + block, 1)


def replace_once(text, old, new, label):
    """old を new に1回だけ置き換える。old が無ければ None を返す。"""
    if old not in text:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return text.replace(old, new, 1)


JAVADOC_756 = """ * <p>756の要点（757との違い）：
 *   <ul>
 *     <li>パケットIDは757からほとんどずれない。tools/packet-ids-756.py で確認。
 *       272エントリすべてが minecraft-data の内部名で一意に写像でき、
 *       757とIDが違うものは16件（クライアント行きのみ。サーバー行きは0件）、
 *       756で削除されたものは1件（Set Simulation Distance 0x57。
 *       1.18で追加された描画距離の通知で、756には無い）、
 *       756で新規のパケットは0件（1.17.1にあって表に無いパケットは無い。
 *       757で -1・756で値ありのエントリも0件）。Join Game 0x26・
 *       Chunk Data 0x22 は757と変わらない。変更はすべて削除によるずれで、
 *       IDの入れ替えや並び替えは無い。クライアント行きは 0x57 の削除で
 *       0x58 以降が -1 にずれる（Update Time 0x59 → 0x58、Set Title Text
 *       0x5a → 0x59、Entity Teleport 0x62 → 0x61、Tags 0x67 → 0x66）。
 *       0x00〜0x56（87件）は変わらない。</li>
 *     <li>ペイロードの形式は757とほぼ同じ見込み。違うものがあれば
 *       Stage B/C で PlayTranslator／ChunkTranslator の756対応とともに確認する。
 *       構造の違いとして、756には設定フェーズが無い（ログインの直後が
 *       プレイ。Registry Data はプレイ状態で送られる）、チャットは
 *       署名なしの1パケット、エンティティのスポーンは種類別
 *       （living／painting／player）に分かれている（いずれも757と同じ）。
 *       1.17.1 は Java 16 を要求する（version.json 実測）。</li>
 *   </ul>
"""


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-756.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")

    # 冪等性の確認：756列はまだ無いこと。
    if "int id756" in text or "ProtocolVersion.V1_17_1" in text:
        print("756列は既にあるため何もしない", file=sys.stderr)
        return 1

    before = parse_entries(text, 18)
    print(f"挿入前のエントリ: {len(before)} 件（写像: {len(mapping)} 件）")
    if len(before) != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    if set(before) != set(mapping):
        print(f"名前が合わない：javaにあって写像に無い {sorted(set(before) - set(mapping))}、"
              f"写像にあってjavaに無い {sorted(set(mapping) - set(before))}", file=sys.stderr)
        return 1

    # --- 756列を先頭に挿入（18引数 → 19引数） ---
    # 終端子は group(22)。数え直し済み（docstring の「775版の罠」参照）。
    pattern = entry_pattern(18)
    assert pattern.groups == 22, f"終端子のグループ番号が 22 ではない：{pattern.groups}"

    def replace(m):
        name = m.group(1)
        if name in mapping:
            id756 = mapping[name]["id756"]
        else:
            raise SystemExit(f"写像に無いエントリ: {name}")
        ids = list(m.groups()[3:21])
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                + f"{fmt(id756)}, " + ", ".join(ids)
                + f"){m.group(22)}")

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件")
    if count != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1

    # --- 検証：既存の18個のIDが変わっていないこと、19引数版になっていること ---
    # 19引数版では ID（756〜775）= group(4)〜(22)、終端子=group(23)。
    after = parse_entries(new_text, 19)
    if len(after) != len(before):
        print(f"19引数版の件数が合わない：{len(after)} 件", file=sys.stderr)
        return 1
    for name, (state, direction, ids18, _) in before.items():
        state2, direction2, ids19, _ = after[name]
        if (state, direction) != (state2, direction2) or ids18 != ids19[1:]:
            print(f"既存のIDが変わっている：{name}", file=sys.stderr)
            return 1
        if ids19[0] != mapping[name]["id756"]:
            print(f"756列の値が写像と違う：{name}（{ids19[0]} != {mapping[name]['id756']}）",
                  file=sys.stderr)
            return 1
    for name in REMOVED_IN_756:
        if after[name][2][0] != -1:
            print(f"削除エントリが 756 で -1 ではない：{name}", file=sys.stderr)
            return 1
    # 18引数版の残存が無いこと。
    left = parse_entries(new_text, 18)
    if left:
        print(f"18引数にできていないエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    print("検証OK：既存18列は不変、756列は写像と一致、削除1件は -1 のまま")

    # --- コンストラクタ・フィールド・アクセサ（756を先頭に） ---
    new_text = insert_after(new_text,
                            "        private final State state;\n"
                            "        private final Direction direction;\n",
                            "        private final int id756;\n", "id756 フィールド")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "PacketName(State state, Direction direction, int id757,",
                            "PacketName(State state, Direction direction, int id756, int id757,",
                            "コンストラクタ引数")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "            this.direction = direction;\n",
                            "            this.id756 = id756;\n", "id756 の代入")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "        /** このパケットの向き。 */\n"
                            "        public Direction direction() {\n"
                            "            return direction;\n"
                            "        }\n",
                            "\n"
                            "        /** 756（1.17.1）でのID。存在しない場合は -1。 */\n"
                            "        public int id756() {\n"
                            "            return id756;\n"
                            "        }\n",
                            "id756() アクセサ")
    if new_text is None:
        return 1

    # --- idFor／supportedProtocols／isSupported ---
    # ProtocolVersion.V1_17_1 はまだ無い（次の作業で追加する）。参照だけ先に入れる。
    new_text = insert_after(new_text,
                            "        public int idFor(int protocol) {\n",
                            "            if (protocol == ProtocolVersion.V1_17_1) {\n"
                            "                return id756;\n"
                            "            }\n",
                            "idFor の 756 分岐")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return List.of(ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "        return List.of(ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "supportedProtocols")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return protocol == ProtocolVersion.V1_18\n",
                            "        return protocol == ProtocolVersion.V1_17_1\n"
                            "                || protocol == ProtocolVersion.V1_18\n",
                            "isSupported")
    if new_text is None:
        return 1

    # --- javadoc ---
    new_text = replace_once(new_text,
                            "パケットID表：状態×方向×パケット名×プロトコル（757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "パケットID表：状態×方向×パケット名×プロトコル（756 / 757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "javadoc のプロトコル一覧")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "data/pc/26.1/protocol.json を使った。1.18／1.18.1（757）は",
                            "data/pc/26.1/protocol.json を使った。1.17.1（756）は\n"
                            " *       data/pc/1.17.1/protocol.json、1.18／1.18.1（757）は",
                            "javadoc の情報源（756）")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * <p>対応するプロトコルは 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            " * <p>対応するプロトコルは 756（1.17.1）と 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            "javadoc の対応プロトコル")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            " *       （living／painting／player）に分かれている。</li>\n"
                            " *   </ul>\n",
                            JAVADOC_756, "javadoc の 756 の要点")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "クライアント発の ping 要求。757・759・763にも無い。プロキシは落とす。",
                            "クライアント発の ping 要求。756・757・759・763にも無い。プロキシは落とす。",
                            "javadoc の Ping Request 注記")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * Chat の行き来）も -1 である。",
                            " * Chat の行き来）も -1 である。756にだけあるものは無い\n"
                            " * （1.17.1にあって表に無いパケットは0件。756で削除された\n"
                            " * Set Simulation Distance は757で追加されたもの）。",
                            "javadoc の -1 の説明（756）")
    if new_text is None:
        return 1

    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 756 列を挿入した（既存 272 件、新規 0 件）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
