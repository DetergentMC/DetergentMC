#!/usr/bin/env python3
"""PacketIds.java に 754 列を挿入する（20引数 → 21引数）。

packet-ids-754.py が作った packet-ids-754.json（272件の写像）を使い、
コンストラクタ呼び出しの先頭に `0xXX, `（無い場合は `-1, `）を足す。
754で新規のパケットは 0件のため、新規エントリの追加は無い。
コンストラクタ・id754() アクセサ・idFor／supportedProtocols／isSupported・
javadoc も754対応にする。

※ 755版（insert-755-java.py）との違い：755は先頭への挿入だったが、
   754はさらにその前に入る。引数の並びは 754, 755, 756, 757, 759, 760, ..., 775。
   終端子のグループ番号は 21引数版でも group(25) になる。

20引数の正規表現ではグループは name=1・state=2・direction=3・
ID（755〜775）=4〜23、終端子（`,`/`;`）=24 である。
このスクリプトではグループ番号を数え直し、m.group(24) を終端子と
して使う。pattern.groups == 24 であることも実行時に確認する。
21引数版ではグループは name=1・state=2・direction=3・
ID（754〜775）=4〜24、終端子=25 になる。

新規エントリは 0件（packet-ids-754.py の報告：1.16.5にあって表に無い
パケット 0件、755で -1・754で値ありのエントリ 0件）。

削除は 1件（755→754 の差分は変更 120件・新規 0件・削除 1件。
PLAY_SCULK_VIBRATION_SIGNAL が 754 では -1 になる。
755ですでに -1 の古いエントリも写像通り -1 のままにする）。

使い方：
  python3 tools/insert-754-java.py
  終了コード 0 = 272件の既存エントリ全件に754列を挿入し、既存の20個のIDが
  変わっていないことを確認した。
  1 = 件数不一致／写像に無いエントリ／20引数版の残存／アンカー無し。

注意：このスクリプトは ProtocolVersion.V1_16_5 を参照する idFor／
supportedProtocols／isSupported の分岐も追加する。ProtocolVersion.java
に V1_16_5 は既にある（24行目。`public static final int V1_16_5 = 754;`）。
serverProtocolFor() への "1.16.5"／"1.16.4" の追加は Stage A の次の作業
（ProtocolVersion.java への 754 追加）で行うこと。Gradleはまだ無いため
コンパイル・テストはこの作業では行わない。
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
PROTOCOLS_20 = (755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                770, 771, 772, 773, 774, 775)


def fmt(value):
    return "-1" if value < 0 else f"0x{value:02x}"


def entry_pattern(n_ids):
    """n引数版のエントリ正規表現。グループ番号：
    name=1・state=2・direction=3・ID=4〜(3+n)・終端子=(4+n)。
    20引数版は終端子が group(24)、21引数版は group(25)。"""
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


JAVADOC_754 = """ * <p>754の要点（755との違い）：
 *   <ul>
 *     <li>パケットIDは755から120件変わる。tools/packet-ids-754.py で確認。
 *       272エントリすべてが minecraft-data の内部名で一意に写像でき、
 *       755とIDが違うものは120件（クライアント行き97件・サーバー行き23件）、
 *       754で削除されたものは1件（Sculk Vibration Signal 0x05。
 *       1.17で追加されたスカルク振動の信号で、1.16.5には無い）、
 *       754で新規のパケットは0件（1.16.5にあって表に無いパケットは無い。
 *       755で -1・754で値ありのエントリも0件）。Join Game 0x26→0x24・
 *       Chunk Data 0x22→0x20・Update Time 0x58→0x4e。内部名の改名・
 *       分割が7系統ある：destroy_entity→entity_destroy（0x36）、
 *       combat_event（0x31）→ enter／end／death_combat_event に3分割、
 *       title（0x4f）→ set_title_text／set_title_subtitle／
 *       set_title_time／clear_titles／action_bar に5分割、
 *       world_border（0x3d）→ initialize_world_border／
 *       world_border_center／world_border_lerp_size／world_border_size／
 *       world_border_warning_delay／world_border_warning_reach に6分割、
 *       transaction→ping（クライアント行き 0x11）／pong（サーバー行き
 *       0x07）に置き換え。entity（クライアント行き 0x2a）は1.17で
 *       削除され PacketIds の対象外（ViaVersion でも未登録）。IDの
 *       入れ替えや並び替えは無い。</li>
 *     <li>ペイロードの形式は755とほぼ同じ見込み。違うものがあれば
 *       Stage B/C で PlayTranslator／ChunkTranslator の754対応とともに確認する。
 *       構造の違いとして、754には設定フェーズが無い（ログインの直後が
 *       プレイ。Registry Data はプレイ状態で送られる）、チャットは
 *       署名なしの1パケット、エンティティのスポーンは種類別
 *       （living／painting／player）に分かれている（いずれも755と同じ）。
 *       1.16.5 は Java 8 を要求する（piston-meta majorVersion 8 実測）。
 *       ただし DetergentMC の javaBinaryFor は Java 21／25 の最低
 *       バージョン分岐しか無く、Java 8 向けバイトコードは新しい JVM
 *       でも動くため 1.16.5 用の分岐は追加していない。</li>
 *   </ul>
"""


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-754.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")

    # 冪等性の確認：754列はまだ無いこと。
    if "int id754" in text or "ProtocolVersion.V1_16_5)" in text:
        print("754列は既にあるため何もしない", file=sys.stderr)
        return 1

    before = parse_entries(text, 20)
    print(f"挿入前のエントリ: {len(before)} 件（写像: {len(mapping)} 件）")
    if len(before) != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    if set(before) != set(mapping):
        print(f"名前が合わない：javaにあって写像に無い {sorted(set(before) - set(mapping))}、"
              f"写像にあってjavaに無い {sorted(set(mapping) - set(before))}", file=sys.stderr)
        return 1

    # --- 754列を先頭に挿入（20引数 → 21引数） ---
    # 終端子は group(24)。数え直し済み（docstring の「775版の罠」参照）。
    pattern = entry_pattern(20)
    assert pattern.groups == 24, f"終端子のグループ番号が 24 ではない：{pattern.groups}"

    def replace(m):
        name = m.group(1)
        if name in mapping:
            id754 = mapping[name]["id754"]
        else:
            raise SystemExit(f"写像に無いエントリ: {name}")
        ids = list(m.groups()[3:23])
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                + f"{fmt(id754)}, " + ", ".join(ids)
                + f"){m.group(24)}")

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件")
    if count != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1

    # --- 検証：既存の20個のIDが変わっていないこと、21引数版になっていること ---
    # 21引数版では ID（754〜775）= group(4)〜(24)、終端子=group(25)。
    after = parse_entries(new_text, 21)
    if len(after) != len(before):
        print(f"21引数版の件数が合わない：{len(after)} 件", file=sys.stderr)
        return 1
    for name, (state, direction, ids20, _) in before.items():
        state2, direction2, ids21, _ = after[name]
        if (state, direction) != (state2, direction2) or ids20 != ids21[1:]:
            print(f"既存のIDが変わっている：{name}", file=sys.stderr)
            return 1
        if ids21[0] != mapping[name]["id754"]:
            print(f"754列の値が写像と違う：{name}（{ids21[0]} != {mapping[name]['id754']}）",
                  file=sys.stderr)
            return 1
    # 20引数版の残存が無いこと。
    left = parse_entries(new_text, 20)
    if left:
        print(f"20引数にできていないエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    print("検証OK：既存20列は不変、754列は写像と一致")

    # --- コンストラクタ・フィールド・アクセサ（754を先頭に） ---
    new_text = insert_after(new_text,
                            "        private final State state;\n"
                            "        private final Direction direction;\n",
                            "        private final int id754;\n", "id754 フィールド")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "PacketName(State state, Direction direction, int id755,",
                            "PacketName(State state, Direction direction, int id754, int id755,",
                            "コンストラクタ引数")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "            this.direction = direction;\n",
                            "            this.id754 = id754;\n", "id754 の代入")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "        /** このパケットの向き。 */\n"
                            "        public Direction direction() {\n"
                            "            return direction;\n"
                            "        }\n",
                            "\n"
                            "        /** 754（1.16.4／1.16.5）でのID。存在しない場合は -1。 */\n"
                            "        public int id754() {\n"
                            "            return id754;\n"
                            "        }\n",
                            "id754() アクセサ")
    if new_text is None:
        return 1

    # --- idFor／supportedProtocols／isSupported ---
    # ProtocolVersion.V1_16_5 は既にある（24行目）。
    new_text = insert_after(new_text,
                            "        public int idFor(int protocol) {\n",
                            "            if (protocol == ProtocolVersion.V1_16_5) {\n"
                            "                return id754;\n"
                            "            }\n",
                            "idFor の 754 分岐")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return List.of(ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "        return List.of(ProtocolVersion.V1_16_5, ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "supportedProtocols")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return protocol == ProtocolVersion.V1_17\n",
                            "        return protocol == ProtocolVersion.V1_16_5\n"
                            "                || protocol == ProtocolVersion.V1_17\n",
                            "isSupported")
    if new_text is None:
        return 1

    # --- javadoc ---
    new_text = replace_once(new_text,
                            "パケットID表：状態×方向×パケット名×プロトコル（755 / 756 / 757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "パケットID表：状態×方向×パケット名×プロトコル（754 / 755 / 756 / 757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "javadoc のプロトコル一覧")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "data/pc/26.1/protocol.json を使った。1.17（755）は\n"
                            " *       data/pc/1.17/protocol.json、1.17.1（756）は\n"
                            " *       data/pc/1.17.1/protocol.json、1.18／1.18.1（757）は",
                            "data/pc/26.1/protocol.json を使った。1.16.5（754）は\n"
                            " *       data/pc/1.16.2/protocol.json、1.17（755）は\n"
                            " *       data/pc/1.17/protocol.json、1.17.1（756）は\n"
                            " *       data/pc/1.17.1/protocol.json、1.18／1.18.1（757）は",
                            "javadoc の情報源（754）")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * <p>対応するプロトコルは 755（1.17）と 756（1.17.1）と 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            " * <p>対応するプロトコルは 754（1.16.4／1.16.5）と 755（1.17）と 756（1.17.1）と 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            "javadoc の対応プロトコル")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            " *       1.17 は Java 16 を要求する（version.json 実測）。</li>\n"
                            " *   </ul>\n",
                            JAVADOC_754, "javadoc の 754 の要点")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "クライアント発の ping 要求。755・756・757・759・763にも無い。プロキシは落とす。",
                            "クライアント発の ping 要求。754・755・756・757・759・763にも無い。プロキシは落とす。",
                            "javadoc の Ping Request 注記")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * ものは無い（1.17にあって表に無いパケットは0件。756と755の\n"
                            " * パケットIDは全件同一）。",
                            " * ものは無い（1.17にあって表に無いパケットは0件。756と755の\n"
                            " * パケットIDは全件同一）。754にだけあるものは無い\n"
                            " * （1.16.5にあって表に無いパケットは0件。entity 0x2a は\n"
                            " * 1.17で削除され PacketIds の対象外。755→754 の差分は\n"
                            " * 変更120件・削除1件）。",
                            "javadoc の -1 の説明（754）")
    if new_text is None:
        return 1

    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 754 列を挿入した（既存 272 件、新規 0 件）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
