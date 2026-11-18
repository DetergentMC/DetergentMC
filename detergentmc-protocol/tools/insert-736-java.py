#!/usr/bin/env python3
"""PacketIds.java に 736 列を挿入する（23引数 → 24引数）。

packet-ids-736.py が作った packet-ids-736.json（272件の写像）を使い、
コンストラクタ呼び出しの先頭に `0xXX, `（無い場合は `-1, `）を足す。
736で新規のパケットは 0件のため、新規エントリの追加は無い。
コンストラクタ・id736() アクセサ・idFor／supportedProtocols／isSupported・
javadoc も736対応にする。

※ 751版（insert-751-java.py）との違い：751は先頭への挿入だったが、
   736はさらにその前に入る。引数の並びは 736, 751, 753, 754, 755, 756, 757, 759, 760, ..., 775。
   終端子のグループ番号は 24引数版では group(28) になる。

23引数の正規表現ではグループは name=1・state=2・direction=3・
ID（751〜775）=4〜26、終端子（`,`/`;`）=27 である。
このスクリプトではグループ番号を数え直し、m.group(27) を終端子と
して使う。pattern.groups == 27 であることも実行時に確認する。
24引数版ではグループは name=1・state=2・direction=3・
ID（736〜775）=4〜27、終端子=28 になる。

新規エントリは 0件（packet-ids-736.py の報告：1.16.1にあって表に無い
パケット 0件、751で -1・736で値ありのエントリ 0件）。

削除は 1件（PLAY_DISPLAYED_RECIPE。751で追加されたため 736 では -1）、
変更は 62件（751→736 の差分は変更 62件・新規 0件・削除 1件。
PLAY／クライアント行き 46件・PLAY／サーバー行き 16件。
Join Game 0x24→0x25、Chunk Data 0x20→0x21、Update Time 0x4e は
変わらず。multi_block_change は 751 の 0x3b から 736 の 0x0f へ、
tab_complete は 751 の 0x0f から 736 の 0x10 へ）。
751 の minecraft-data 入力（mc-1.16.2-protocol.json）と 736 の入力
（mc-1.16.1-protocol.json）はバイト同一ではない（実測で差分あり）。
ViaVersion の Protocol1_16To1_16_1 は両側とも同じ
ClientboundPackets1_16／ServerboundPackets1_16 を使っており、
1.16／1.16.1 でパケットIDが同一であることを裏付ける。
Protocol1_16_1To1_16_2 は旧側が 1.16 enum・新側が 1.16.2 enum で、
736→751 で変わることの証拠になる。

使い方：
  python3 tools/insert-736-java.py
  終了コード 0 = 272件の既存エントリ全件に736列を挿入し、既存の23個のIDが
  変わっていないことを確認した。
  1 = 件数不一致／写像に無いエントリ／23引数版の残存／アンカー無し。

注意：このスクリプトは ProtocolVersion.V1_16_1 を参照する idFor／
supportedProtocols／isSupported の分岐も追加する。ProtocolVersion.java
に V1_16_1 はまだ無いため、javac はこのスクリプトの実行後に
ProtocolVersion.java へ V1_16_1 を追加してから行うこと（Stage A の
次の作業。serverProtocolFor() への "1.16.1" の追加も同じ作業で行う）。
Gradleはまだ無いためフルビルドはこの作業では行わない。
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
PROTOCOLS_23 = (751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                770, 771, 772, 773, 774, 775)


def fmt(value):
    return "-1" if value < 0 else f"0x{value:02x}"


def entry_pattern(n_ids):
    """n引数版のエントリ正規表現。グループ番号：
    name=1・state=2・direction=3・ID=4〜(3+n)・終端子=(4+n)。
    21引数版は終端子が group(25)、22引数版は group(26)。"""
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


JAVADOC_736 = """ * <p>736の要点（751との違い）：
 *   <ul>
 *     <li>パケットIDは751から62件変わる。tools/packet-ids-736.py で確認。
 *       272エントリすべてが minecraft-data の内部名で一意に写像でき、
 *       751とIDが違うものは62件（PLAY／クライアント行き46件・
 *       PLAY／サーバー行き16件）、736で削除されたものは1件
 *       （PLAY_DISPLAYED_RECIPE。751で追加された displayed_recipe
 *       は 736 には無い）、736で新規のパケットは0件（1.16.1にあって
 *       表に無いパケットは無い。751で -1・736で値ありのエントリも0件）。
 *       Join Game 0x25（751は 0x24）・Chunk Data 0x21（751は 0x20）・
 *       Update Time 0x4e（751と同じ）。multi_block_change は 751 の
 *       0x3b から 736 の 0x0f へ移動し、tab_complete は 751 の 0x0f
 *       から 736 の 0x10 へ（1.16.2 で tab_complete が 0x0f に入り、
 *       それ以降が1つずつ後ろへずれた）。PLAY／サーバー行きは
 *       displayed_recipe（0x1f）の新設で 0x1f 以降が1つずつ後ろへ
 *       ずれたため、736 では 1つ小さい。内部名の改名・分割は751と
 *       ほぼ同じ7系統に recipe_book→crafting_book_data（0x1e）が
 *       加わる：destroy_entity→entity_destroy（0x37）、
 *       combat_event（0x32）→ enter／end／death_combat_event に3分割、
 *       title（0x4f）→ set_title_text／set_title_subtitle／
 *       set_title_time／clear_titles／action_bar に5分割、
 *       world_border（0x3d）→ initialize_world_border／
 *       world_border_center／world_border_lerp_size／world_border_size／
 *       world_border_warning_delay／world_border_warning_reach に6分割、
 *       transaction→ping（クライアント行き 0x12）／pong（サーバー行き
 *       0x07）に置き換え。entity（クライアント行き 0x2b）は1.17で
 *       削除され PacketIds の対象外（ViaVersion でも未登録）。IDの
 *       入れ替えは上記の62件のみ。ViaVersion の Protocol1_16To1_16_1
 *       は両側とも同じ ClientboundPackets1_16／
 *       ServerboundPackets1_16 を使っており、1.16／1.16.1 で
 *       パケットIDが同一であることを裏付ける。Minecraft Wiki の
 *       Packets ページには 1.16.1 の改訂版が無い（1.16.1 リリースから
 *       1.16.2 リリースの間にページは 1.15.2 のままだった）ため、
 *       1.16.3 の改訂版 2772553（2020-10-25、「1.16.3, protocol 753」）
 *       との差分照合（149件・不一致 0件）と、1.15.2 表との差分の裏付けで
 *       代用した（tools/verify-736-wiki.py）。</li>
 *     <li>ペイロードの形式は751とほぼ同じ見込み。違うものがあれば
 *       Stage B/C で PlayTranslator／ChunkTranslator の736対応とともに確認する。
 *       構造の違いとして、736には設定フェーズが無い（ログインの直後が
 *       プレイ。Registry Data はプレイ状態で送られる）、チャットは
 *       署名なしの1パケット、エンティティのスポーンは種類別
 *       （living／painting／player）に分かれている（いずれも751と同じ）。
 *       1.16.1 は Java 8 を要求する（piston-meta majorVersion 8 実測）。
 *       ただし DetergentMC の javaBinaryFor は Java 21／25 の最低
 *       バージョン分岐しか無く、Java 8 向けバイトコードは新しい JVM
 *       でも動くため 1.16.1 用の分岐は追加していない。</li>
 *   </ul>
"""


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-736.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")

    # 冪等性の確認：736列はまだ無いこと。
    if "int id736" in text or "ProtocolVersion.V1_16_1)" in text:
        print("736列は既にあるため何もしない", file=sys.stderr)
        return 1

    before = parse_entries(text, 23)
    print(f"挿入前のエントリ: {len(before)} 件（写像: {len(mapping)} 件）")
    if len(before) != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    if set(before) != set(mapping):
        print(f"名前が合わない：javaにあって写像に無い {sorted(set(before) - set(mapping))}、"
              f"写像にあってjavaに無い {sorted(set(mapping) - set(before))}", file=sys.stderr)
        return 1

    # --- 736列を先頭に挿入（23引数 → 24引数） ---
    # 終端子は group(27)。数え直し済み。
    pattern = entry_pattern(23)
    assert pattern.groups == 27, f"終端子のグループ番号が 27 ではない：{pattern.groups}"

    def replace(m):
        name = m.group(1)
        if name in mapping:
            id736 = mapping[name]["id736"]
        else:
            raise SystemExit(f"写像に無いエントリ: {name}")
        ids = list(m.groups()[3:26])
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                + f"{fmt(id736)}, " + ", ".join(ids)
                + f"){m.group(27)}")

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件")
    if count != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1

    # --- 検証：既存の23個のIDが変わっていないこと、24引数版になっていること ---
    # 24引数版では ID（736〜775）= group(4)〜(27)、終端子=group(28)。
    after = parse_entries(new_text, 24)
    if len(after) != len(before):
        print(f"22引数版の件数が合わない：{len(after)} 件", file=sys.stderr)
        return 1
    for name, (state, direction, ids23, _) in before.items():
        state2, direction2, ids24, _ = after[name]
        if (state, direction) != (state2, direction2) or ids23 != ids24[1:]:
            print(f"既存のIDが変わっている：{name}", file=sys.stderr)
            return 1
        if ids24[0] != mapping[name]["id736"]:
            print(f"736列の値が写像と違う：{name}（{ids24[0]} != {mapping[name]['id736']}）",
                  file=sys.stderr)
            return 1
    # 23引数版の残存が無いこと。
    left = parse_entries(new_text, 23)
    if left:
        print(f"23引数にできていないエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    print("検証OK：既存23列は不変、736列は写像と一致")

    # --- コンストラクタ・フィールド・アクセサ（736を先頭に） ---
    new_text = insert_after(new_text,
                            "        private final State state;\n"
                            "        private final Direction direction;\n",
                            "        private final int id736;\n", "id736 フィールド")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "PacketName(State state, Direction direction, int id751,",
                            "PacketName(State state, Direction direction, int id736, int id751,",
                            "コンストラクタ引数")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "            this.direction = direction;\n",
                            "            this.id736 = id736;\n", "id736 の代入")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "        /** このパケットの向き。 */\n"
                            "        public Direction direction() {\n"
                            "            return direction;\n"
                            "        }\n",
                            "\n"
                            "        /** 736（1.16.1）でのID。存在しない場合は -1。 */\n"
                            "        public int id736() {\n"
                            "            return id736;\n"
                            "        }\n",
                            "id736() アクセサ")
    if new_text is None:
        return 1

    # --- idFor／supportedProtocols／isSupported ---
    # ProtocolVersion.V1_16_3 はこの時点ではまだ無い（次の作業で追加する）。
    new_text = insert_after(new_text,
                            "        public int idFor(int protocol) {\n",
                            "            if (protocol == ProtocolVersion.V1_16_1) {\n"
                            "                return id736;\n"
                            "            }\n",
                            "idFor の 736 分岐")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return List.of(ProtocolVersion.V1_16_2, ProtocolVersion.V1_16_3, ProtocolVersion.V1_16_5, ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "        return List.of(ProtocolVersion.V1_16_1, ProtocolVersion.V1_16_2, ProtocolVersion.V1_16_3, ProtocolVersion.V1_16_5, ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "supportedProtocols")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return protocol == ProtocolVersion.V1_16_2\n",
                            "        return protocol == ProtocolVersion.V1_16_1\n"
                            "                || protocol == ProtocolVersion.V1_16_2\n",
                            "isSupported")
    if new_text is None:
        return 1

    # --- javadoc ---
    new_text = replace_once(new_text,
                            "パケットID表：状態×方向×パケット名×プロトコル（751 / 753 / 754 / 755 / 756 / 757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "パケットID表：状態×方向×パケット名×プロトコル（736 / 751 / 753 / 754 / 755 / 756 / 757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "javadoc のプロトコル一覧")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "data/pc/26.1/protocol.json を使った。1.16.2（751）は\n"
                            " *       data/pc/1.16.2/protocol.json、1.16.3（753）は\n"
                            " *       data/pc/1.16.2/protocol.json、1.16.5（754）は\n"
                            " *       data/pc/1.16.2/protocol.json、1.17（755）は\n"
                            " *       data/pc/1.17/protocol.json、1.17.1（756）は\n"
                            " *       data/pc/1.17.1/protocol.json、1.18／1.18.1（757）は",
                            "data/pc/26.1/protocol.json を使った。1.16.1（736）は\n"
                            " *       data/pc/1.16.1/protocol.json、1.16.2（751）は\n"
                            " *       data/pc/1.16.2/protocol.json、1.16.3（753）は\n"
                            " *       data/pc/1.16.2/protocol.json、1.16.5（754）は\n"
                            " *       data/pc/1.16.2/protocol.json、1.17（755）は\n"
                            " *       data/pc/1.17/protocol.json、1.17.1（756）は\n"
                            " *       data/pc/1.17.1/protocol.json、1.18／1.18.1（757）は",
                            "javadoc の情報源（736）")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * <p>対応するプロトコルは 751（1.16.2）と 753（1.16.3）と 754（1.16.4／1.16.5）と 755（1.17）と 756（1.17.1）と 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            " * <p>対応するプロトコルは 736（1.16.1）と 751（1.16.2）と 753（1.16.3）と 754（1.16.4／1.16.5）と 755（1.17）と 756（1.17.1）と 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            "javadoc の対応プロトコル")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            " *       でも動くため 1.16.2 用の分岐は追加していない。</li>\n"
                            " *   </ul>\n",
                            JAVADOC_736, "javadoc の 736 の要点")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "クライアント発の ping 要求。751・753・754・755・756・757・759・763にも無い。プロキシは落とす。",
                            "クライアント発の ping 要求。736・751・753・754・755・756・757・759・763にも無い。プロキシは落とす。",
                            "javadoc の Ping Request 注記")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * ものは無い（1.17にあって表に無いパケットは0件。756と755の\n"
                            " * パケットIDは全件同一）。754にだけあるものは無い\n"
                            " * （1.16.5にあって表に無いパケットは0件。entity 0x2a は\n"
                            " * 1.17で削除され PacketIds の対象外。755→754 の差分は\n"
                            " * 変更120件・削除1件）。753にだけあるものは無い\n"
                            " * （1.16.3にあって表に無いパケットは0件。entity 0x2a は\n"
                            " * 1.17で削除され PacketIds の対象外。754→753 の差分は\n"
                            " * 変更0件・削除0件・新規0件）。",
                            " * ものは無い（1.17にあって表に無いパケットは0件。756と755の\n"
                            " * パケットIDは全件同一）。754にだけあるものは無い\n"
                            " * （1.16.5にあって表に無いパケットは0件。entity 0x2a は\n"
                            " * 1.17で削除され PacketIds の対象外。755→754 の差分は\n"
                            " * 変更120件・削除1件）。753にだけあるものは無い\n"
                            " * （1.16.3にあって表に無いパケットは0件。entity 0x2a は\n"
                            " * 1.17で削除され PacketIds の対象外。754→753 の差分は\n"
                            " * 変更0件・削除0件・新規0件）。751にだけあるものは無い\n"
                            " * （1.16.2にあって表に無いパケットは0件。entity 0x2a は\n"
                            " * 1.17で削除され PacketIds の対象外。753→751 の差分は\n"
                            " * 変更0件・削除0件・新規0件）。736にだけあるものは無い\n"
                            " * （1.16.1にあって表に無いパケットは0件。entity 0x2b は\n"
                            " * 1.17で削除され PacketIds の対象外。751→736 の差分は\n"
                            " * 変更62件・削除1件・新規0件）。",
                            "javadoc の -1 の説明（736）")
    if new_text is None:
        return 1

    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 736 列を挿入した（既存 272 件、新規 0 件）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
