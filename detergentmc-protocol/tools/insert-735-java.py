#!/usr/bin/env python3
"""PacketIds.java に 735 列を挿入し、ProtocolVersion／ClientAdapter／
VanillaServerHostTest に 1.16（735）を追加する（24引数 → 25引数）。

packet-ids-735.py が作った packet-ids-735.json（272件の写像）を使い、
コンストラクタ呼び出しの先頭に `0xXX, `（無い場合は `-1, `）を足す。
735で新規のパケットは 0件のため、新規エントリの追加は無い。
コンストラクタ・id735() アクセサ・idFor／supportedProtocols／isSupported・
javadoc も735対応にする。ProtocolVersion.java には `V1_16 = 735` と
serverProtocolFor("1.16") を、ClientAdapter.java には versionName と
pack_format 三項演算子の "5"（735用）を、VanillaServerHostTest.java には
javaBinaryFor("1.16") のアサーションを追加する。

※ 736版（insert-736-java.py）との違い：736は23→24引数だったが、
   735は24→25引数。終端子のグループ番号は 25引数版では group(29) になる。
   24引数の正規表現ではグループは name=1・state=2・direction=3・
   ID（736〜775）=4〜27、終端子（`,`/`;`）=28 である。
   25引数版ではグループは name=1・state=2・direction=3・
   ID（735〜775）=4〜28、終端子=29 になる。

【重要：735 と 736 のパケットIDは全件同一】
packet-ids-735.py の実測（実バニラ jar の ConnectionProtocol 登録順）で、
736→735 は変更0件・新規0件・削除0件。id735 は全272件で id736 と一致する。
minecraft-data の pc/1.16/protocol.json は pc/1.16.2 とバイト同一の誤写で、
実 jar とは食い違うため使っていない（packet-ids-735.py の docstring 参照）。
Join Game 0x25・Chunk Data 0x21・Update Time 0x4e・Respawn 0x3a・
Keep Alive 0x20・Entity Destroy 0x37・Ping 0x12／Pong 0x07・
Multi Block Change 0x0f・Tab Complete 0x10・Displayed Recipe -1。
736→751 の差分（変更62件・削除1件）は 751（1.16.2）で起きたもの。

使い方：
  python3 tools/insert-735-java.py
  終了コード 0 = 272件の既存エントリ全件に735列を挿入し、既存の24個のIDが
  変わっていないこと、ProtocolVersion／ClientAdapter／テストの追加を
  確認した。
  1 = 件数不一致／写像に無いエントリ／24引数版の残存／アンカー無し。
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
PV_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
           / "io" / "detergentmc" / "protocol" / "ProtocolVersion.java")
CA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
           / "io" / "detergentmc" / "protocol" / "ClientAdapter.java")
VSH_TEST_FILE = (REPO_ROOT / "detergentmc-core" / "src" / "test" / "java"
                 / "io" / "detergentmc" / "core" / "vanilla"
                 / "VanillaServerHostTest.java")

ID_PAT = r"(-1|0x[0-9a-fA-F]+)"
PROTOCOLS_24 = (736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                770, 771, 772, 773, 774, 775)


def fmt(value):
    return "-1" if value < 0 else f"0x{value:02x}"


def entry_pattern(n_ids):
    """n引数版のエントリ正規表現。グループ番号：
    name=1・state=2・direction=3・ID=4〜(3+n)・終端子=(4+n)。
    24引数版は終端子が group(28)、25引数版は group(29)。"""
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


JAVADOC_735 = """ * <p>735の要点（736との違い）：
 *   <ul>
 *     <li>パケットIDは736から0件変わる。tools/packet-ids-735.py で確認。
 *       272エントリすべてが実バニラ jar の ConnectionProtocol 登録順で
 *       写像でき、736とIDが違うものは0件、735で削除されたものは0件、
 *       735で新規のパケットは0件（1.16 jar にあって表に無いクラスは無い）。
 *       Join Game 0x25・Chunk Data 0x21・Update Time 0x4e・Respawn 0x3a・
 *       Keep Alive 0x20・Entity Destroy 0x37・Ping 0x12／Pong 0x07・
 *       Multi Block Change 0x0f・Tab Complete 0x10（いずれも736と同じ）。
 *       minecraft-data の pc/1.16/protocol.json は pc/1.16.2 とバイト同一の
 *       誤写で、実 jar とは食い違うため使っていない（Join Game は pc/1.16
 *       では 0x24 だが実 jar では 0x25、multi_block_change は pc/1.16
 *       では 0x3b だが実 jar では 0x0f、toServer は pc/1.16 では 48 件だが
 *       実 jar では 47 件で displayed_recipe が無い）。ViaVersion の
 *       Protocol1_16To1_16_1 は両側とも同じ ClientboundPackets1_16／
 *       ServerboundPackets1_16 を使っており、付け替えは無い。
 *       736→751 の差分（変更62件・削除1件）は 751（1.16.2）で起きたもの
 *       （1.16.2 jar の登録順で確認。ChunkBlocksUpdate→SectionBlocksUpdate
 *       の移動と RecipeBookUpdate の2分割）。Minecraft Wiki の Packets
 *       ページには 1.16 の改訂版も無い（1.16 リリースから 1.16.1 リリースの
 *       間に改訂版は無く、1.16 リリース直前の 2772533 も 1.15.2 のまま）。
 *       そのため 1.16.3 の改訂版 2772553（2020-10-25）との差分照合
 *       （149件・不一致 0件）と、1.15.2 表との差分の裏付けで代用した
 *       （tools/verify-735-wiki.py）。</li>
 *     <li>ペイロードの形式は736とほぼ同じ見込み。違うものがあれば
 *       Stage B/C で PlayTranslator／ChunkTranslator の735対応とともに確認する。
 *       構造の違いとして、735には設定フェーズが無い（ログインの直後が
 *       プレイ。Registry Data はプレイ状態で送られる）、チャットは
 *       署名なしの1パケット、エンティティのスポーンは種類別
 *       （living／painting／player）に分かれている（いずれも736と同じ）。
 *       1.16 は Java 8 を要求する（piston-meta majorVersion 8 実測）。
 *       ただし DetergentMC の javaBinaryFor は Java 21／25 の最低
 *       バージョン分岐しか無く、Java 8 向けバイトコードは新しい JVM
 *       でも動くため 1.16 用の分岐は追加していない。</li>
 *   </ul>
"""


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-735.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")

    # 冪等性の確認：735列はまだ無いこと。
    if "int id735" in text or "ProtocolVersion.V1_16)" in text:
        print("735列は既にあるため何もしない", file=sys.stderr)
        return 1

    before = parse_entries(text, 24)
    print(f"挿入前のエントリ: {len(before)} 件（写像: {len(mapping)} 件）")
    if len(before) != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    if set(before) != set(mapping):
        print(f"名前が合わない：javaにあって写像に無い {sorted(set(before) - set(mapping))}、"
              f"写像にあってjavaに無い {sorted(set(mapping) - set(before))}", file=sys.stderr)
        return 1

    # --- 735列を先頭に挿入（24引数 → 25引数） ---
    # 終端子は group(28)。数え直し済み。
    pattern = entry_pattern(24)
    assert pattern.groups == 28, f"終端子のグループ番号が 28 ではない：{pattern.groups}"

    def replace(m):
        name = m.group(1)
        if name in mapping:
            id735 = mapping[name]["id735"]
        else:
            raise SystemExit(f"写像に無いエントリ: {name}")
        ids = list(m.groups()[3:27])
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                + f"{fmt(id735)}, " + ", ".join(ids)
                + f"){m.group(28)}")

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件")
    if count != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1

    # --- 検証：既存の24個のIDが変わっていないこと、25引数版になっていること ---
    # 25引数版では ID（735〜775）= group(4)〜(28)、終端子=group(29)。
    after = parse_entries(new_text, 25)
    if len(after) != len(before):
        print(f"25引数版の件数が合わない：{len(after)} 件", file=sys.stderr)
        return 1
    for name, (state, direction, ids24, _) in before.items():
        state2, direction2, ids25, _ = after[name]
        if (state, direction) != (state2, direction2) or ids24 != ids25[1:]:
            print(f"既存のIDが変わっている：{name}", file=sys.stderr)
            return 1
        if ids25[0] != mapping[name]["id735"]:
            print(f"735列の値が写像と違う：{name}（{ids25[0]} != {mapping[name]['id735']}）",
                  file=sys.stderr)
            return 1
    # 24引数版の残存が無いこと。
    left = parse_entries(new_text, 24)
    if left:
        print(f"24引数にできていないエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    print("検証OK：既存24列は不変、735列は写像と一致")

    # --- コンストラクタ・フィールド・アクセサ（735を先頭に） ---
    new_text = insert_after(new_text,
                            "        private final State state;\n"
                            "        private final Direction direction;\n",
                            "        private final int id735;\n", "id735 フィールド")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "PacketName(State state, Direction direction, int id736,",
                            "PacketName(State state, Direction direction, int id735, int id736,",
                            "コンストラクタ引数")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "            this.direction = direction;\n",
                            "            this.id735 = id735;\n", "id735 の代入")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "        /** このパケットの向き。 */\n"
                            "        public Direction direction() {\n"
                            "            return direction;\n"
                            "        }\n",
                            "\n"
                            "        /** 735（1.16）でのID。存在しない場合は -1。 */\n"
                            "        public int id735() {\n"
                            "            return id735;\n"
                            "        }\n",
                            "id735() アクセサ")
    if new_text is None:
        return 1

    # --- idFor／supportedProtocols／isSupported ---
    new_text = insert_after(new_text,
                            "        public int idFor(int protocol) {\n",
                            "            if (protocol == ProtocolVersion.V1_16) {\n"
                            "                return id735;\n"
                            "            }\n",
                            "idFor の 735 分岐")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return List.of(ProtocolVersion.V1_16_1, ProtocolVersion.V1_16_2, ProtocolVersion.V1_16_3, ProtocolVersion.V1_16_5, ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "        return List.of(ProtocolVersion.V1_16, ProtocolVersion.V1_16_1, ProtocolVersion.V1_16_2, ProtocolVersion.V1_16_3, ProtocolVersion.V1_16_5, ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "supportedProtocols")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return protocol == ProtocolVersion.V1_16_1\n",
                            "        return protocol == ProtocolVersion.V1_16\n"
                            "                || protocol == ProtocolVersion.V1_16_1\n",
                            "isSupported")
    if new_text is None:
        return 1

    # --- javadoc ---
    new_text = replace_once(new_text,
                            "パケットID表：状態×方向×パケット名×プロトコル（736 / 751 / 753 / 754 / 755 / 756 / 757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "パケットID表：状態×方向×パケット名×プロトコル（735 / 736 / 751 / 753 / 754 / 755 / 756 / 757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "javadoc のプロトコル一覧")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "data/pc/26.1/protocol.json を使った。1.16.1（736）は",
                            "data/pc/26.1/protocol.json を使った。1.16（735）は\n"
                            " *       data/pc/1.16.1/protocol.json と同じパケットIDのため\n"
                            " *       （実バニラ jar の登録順で確認。minecraft-data の\n"
                            " *       pc/1.16/protocol.json は pc/1.16.2 とバイト同一の誤写のため\n"
                            " *       使わない）、1.16.1（736）は",
                            "javadoc の情報源（735）")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * <p>対応するプロトコルは 736（1.16.1）と 751（1.16.2）と 753（1.16.3）と 754（1.16.4／1.16.5）と 755（1.17）と 756（1.17.1）と 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            " * <p>対応するプロトコルは 735（1.16）と 736（1.16.1）と 751（1.16.2）と 753（1.16.3）と 754（1.16.4／1.16.5）と 755（1.17）と 756（1.17.1）と 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            "javadoc の対応プロトコル")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            " *       でも動くため 1.16.1 用の分岐は追加していない。</li>\n"
                            " *   </ul>\n",
                            JAVADOC_735, "javadoc の 735 の要点")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "クライアント発の ping 要求。736・751・753・754・755・756・757・759・763にも無い。プロキシは落とす。",
                            "クライアント発の ping 要求。735・736・751・753・754・755・756・757・759・763にも無い。プロキシは落とす。",
                            "javadoc の Ping Request 注記")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * 変更62件・削除1件・新規0件）。751にだけあるものは無い",
                            " * 変更62件・削除1件・新規0件）。735にだけあるものは無い\n"
                            " * （1.16にあって表に無いパケットは0件。entity 0x2b は\n"
                            " * 1.17で削除され PacketIds の対象外。736→735 の差分は\n"
                            " * 変更0件・削除0件・新規0件）。751にだけあるものは無い",
                            "javadoc の -1 の説明（735）")
    if new_text is None:
        return 1

    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 735 列を挿入した（既存 272 件、新規 0 件）。")

    # --- ProtocolVersion.java: V1_16 = 735 と serverProtocolFor("1.16") ---
    pv_text = PV_FILE.read_text(encoding="utf-8")
    if "V1_16 = 735" in pv_text:
        print("V1_16 は既にあるため何もしない", file=sys.stderr)
        return 1
    pv_text = replace_once(pv_text,
                           "    /** Minecraft 1.16.1 */\n",
                           "    /** Minecraft 1.16 */\n"
                           "    public static final int V1_16 = 735;\n"
                           "    /** Minecraft 1.16.1 */\n",
                           "V1_16 定数")
    if pv_text is None:
        return 1
    pv_text = replace_once(pv_text,
                           "     *   <li>{@code \"1.16.1\"} → {@link #V1_16_1} (736)</li>\n",
                           "     *   <li>{@code \"1.16\"} → {@link #V1_16} (735)</li>\n"
                           "     *   <li>{@code \"1.16.1\"} → {@link #V1_16_1} (736)</li>\n",
                           "javadoc の 1.16 対応表")
    if pv_text is None:
        return 1
    pv_text = replace_once(pv_text,
                           "     * @return the internal server protocol number (736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774 or 775)",
                           "     * @return the internal server protocol number (735, 736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774 or 775)",
                           "@return の一覧")
    if pv_text is None:
        return 1
    pv_text = replace_once(pv_text,
                           "        if (\"1.16.1\".equals(minecraftVersion)) {\n"
                           "            return V1_16_1;\n"
                           "        }\n",
                           "        if (\"1.16\".equals(minecraftVersion)) {\n"
                           "            return V1_16;\n"
                           "        }\n"
                           "        if (\"1.16.1\".equals(minecraftVersion)) {\n"
                           "            return V1_16_1;\n"
                           "        }\n",
                           "serverProtocolFor の 1.16 分岐")
    if pv_text is None:
        return 1
    pv_text = replace_once(pv_text,
                           "Supported server versions: 1.16.1, 1.16.2,",
                           "Supported server versions: 1.16, 1.16.1, 1.16.2,",
                           "未知バージョン警告の一覧")
    if pv_text is None:
        return 1
    PV_FILE.write_text(pv_text, encoding="utf-8")
    print("ProtocolVersion.java に V1_16 = 735 と serverProtocolFor(\"1.16\") を追加した。")

    # --- ClientAdapter.java: versionName と pack_format 三項演算子 ---
    ca_text = CA_FILE.read_text(encoding="utf-8")
    if "ProtocolVersion.V1_16)" in ca_text and "\"1.16\"" in ca_text:
        print("ClientAdapter の 1.16 対応は既にあるため何もしない", file=sys.stderr)
        return 1
    ca_text = replace_once(ca_text,
                           "        if (protocol == ProtocolVersion.V1_16_1) {\n"
                           "            return \"1.16.1\";\n"
                           "        }\n",
                           "        if (protocol == ProtocolVersion.V1_16) {\n"
                           "            return \"1.16\";\n"
                           "        }\n"
                           "        if (protocol == ProtocolVersion.V1_16_1) {\n"
                           "            return \"1.16.1\";\n"
                           "        }\n",
                           "versionName の 1.16 分岐")
    if ca_text is None:
        return 1
    ca_text = replace_once(ca_text,
                           "                                    + (clientProtocol == ProtocolVersion.V1_16_1 ? \"5\"",
                           "                                    + (clientProtocol == ProtocolVersion.V1_16 ? \"5\"\n"
                           "                                            : clientProtocol == ProtocolVersion.V1_16_1 ? \"5\"",
                           "pack_format 三項演算子（735 → \"5\"）")
    if ca_text is None:
        return 1
    ca_text = insert_after(ca_text,
                           "        // V1_16_1 → \"5\" も同じく休眠エントリ：Stage A の時点では",
                           "        // V1_16 → \"5\" も同じく休眠エントリ：Stage A の時点では\n"
                           "        // decide() が735を断るため differences(735, 764) は\n"
                           "        // NOT_SUPPORTED で、専用の735分岐もまだ無い。Stage B で735の\n"
                           "        // 受け入れを配線しても「5」は休眠のまま（736の「5」と同じ段取り）。\n"
                           "        // 「5」の根拠：1.16 の version.json 実測で pack_version 5。\n",
                           "pack_format 休眠コメント（735）")
    if ca_text is None:
        return 1
    CA_FILE.write_text(ca_text, encoding="utf-8")
    print("ClientAdapter.java に versionName \"1.16\" と pack_format \"5\"（735用）を追加した。")

    # --- VanillaServerHostTest.java: javaBinaryFor("1.16") のアサーション ---
    vsh_text = VSH_TEST_FILE.read_text(encoding="utf-8")
    if "javaBinaryFor(\"1.16\")" in vsh_text:
        print("javaBinaryFor(\"1.16\") のアサーションは既にあるため何もしない",
              file=sys.stderr)
        return 1
    vsh_text = insert_after(vsh_text,
                            "        assertEquals(expected, VanillaServerHost.javaBinaryFor(\"1.16.1\"));\n",
                            "        // 1.16 も Java 8 を要求する（piston-meta majorVersion 8 実測）。\n"
                            "        // 1.16.1 と同じ判断で、現在の Java を使う。実起動の確認は Stage D で行う。\n"
                            "        assertEquals(expected, VanillaServerHost.javaBinaryFor(\"1.16\"));\n",
                            "javaBinaryFor(\"1.16\") のアサーション")
    if vsh_text is None:
        return 1
    VSH_TEST_FILE.write_text(vsh_text, encoding="utf-8")
    print("VanillaServerHostTest.java に javaBinaryFor(\"1.16\") のアサーションを追加した。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
