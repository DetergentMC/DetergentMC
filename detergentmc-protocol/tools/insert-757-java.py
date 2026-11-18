#!/usr/bin/env python3
"""PacketIds.java に 757 列を挿入する（17引数 → 18引数）。

packet-ids-757.py が作った packet-ids-757.json（267件の写像）を使い、
コンストラクタ呼び出しの先頭に `0xXX, `（無い場合は `-1, `）を足す。
さらに新規パケット5件のエントリを enum の末尾に追加し、コンストラクタ・
id757() アクセサ・idFor／supportedProtocols／isSupported・javadoc も
757対応にする。

※ 777版（insert-777-java.py）との違い：777は末尾への追加だったが、
   757は先頭への挿入になる。引数の並びは 757, 759, 760, ..., 775。
   終端子のグループ番号は 18引数版でも group(22)（777のときと同じ）だが、
   IDの group は 757=4、759=5、...、775=21 になる。
   777版の作りは tools/patch-packet-ids-775.py にならったもの
   （insert-775-java.py は RegistryRemapper 用で PacketIds とは関係ない）。

775版の罠（group番号の数え直し）：
  17引数の正規表現ではグループは name=1・state=2・direction=3・
  ID（759〜775）=4〜20、終端子（`,`/`;`）=21 である。
  このスクリプトではグループ番号を数え直し、m.group(21) を終端子と
  して使う。pattern.groups == 21 であることも実行時に確認する。
  18引数版ではグループは name=1・state=2・direction=3・
  ID（757〜775）=4〜21、終端子=22 になる。

新規エントリ5件（757で追加。759〜775はすべて -1）：
  PLAY_SPAWN_ENTITY_LIVING（PLAY/CLIENTBOUND 0x02）
  PLAY_SPAWN_ENTITY_PAINTING（PLAY/CLIENTBOUND 0x03）
  PLAY_SCULK_VIBRATION_SIGNAL（PLAY/CLIENTBOUND 0x05）
  PLAY_CHAT（PLAY/CLIENTBOUND 0x0F）
  PLAY_CHAT_TO_SERVER（PLAY/SERVERBOUND 0x03）
  777の前例（6件）と同じく enum の末尾に「757で追加」のコメント付きで
  追加する。名前は 759 の命名規則にならう（PLAY_＋大文字スネークケース。
  サーバー行きの chat は PLAY_CHAT_MESSAGE／PLAY_CHAT_COMMAND と紛らわしい
  ため PLAY_CHAT_TO_SERVER とする）。

削除8件（PLAY_SYSTEM_CHAT、PLAY_PLAYER_CHAT、
PLAY_SHOULD_DISPLAY_CHAT_PREVIEW、PLAY_SERVER_DATA、PLAY_CHAT_PREVIEW、
PLAY_CHAT_MESSAGE、PLAY_CHAT_COMMAND、PLAY_CHAT_PREVIEW_TO_SERVER）は
写像の id757=-1 を入れてエントリ自体は残す。759ですでに -1 の古い
エントリも写像通り -1 のままにする。

使い方：
  python3 tools/insert-757-java.py
  終了コード 0 = 267件の既存エントリ全件に757列を挿入し、新規5件を
  追加し、既存の17個のIDが変わっていないことを確認した。
  1 = 件数不一致／写像に無いエントリ／17引数版の残存／アンカー無し。

注意：このスクリプトは ProtocolVersion.V1_18 を参照する idFor／
supportedProtocols／isSupported の分岐も追加する。ProtocolVersion.java
に V1_18 がまだ無いため、追加されるまでこのファイルはコンパイル
できない。V1_18 の追加は Stage A の次の作業（ProtocolVersion.java
への 757 追加）で行うこと。Gradleはまだ無いためコンパイル・テストは
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
PROTOCOLS_17 = (759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                770, 771, 772, 773, 774, 775)

# 新規エントリ5件：(名前, 状態, 方向, id757, コメント)。
# 説明は Minecraft Wiki の Packets ページ（1.18、protocol 757、
# 2021-12-09 の改訂版 2772742）の記述による。
NEW_ENTRIES = [
    ("PLAY_SPAWN_ENTITY_LIVING", "PLAY", "CLIENTBOUND", 0x02,
     "Spawn Living Entity。757で追加（モブのスポーン。1.19で Spawn Entity に統合された）。"),
    ("PLAY_SPAWN_ENTITY_PAINTING", "PLAY", "CLIENTBOUND", 0x03,
     "Spawn Painting。757で追加（絵画のスポーン。1.19で Spawn Entity に統合された）。"),
    ("PLAY_SCULK_VIBRATION_SIGNAL", "PLAY", "CLIENTBOUND", 0x05,
     "Sculk Vibration Signal。757で追加（スカルク振動の信号。1.19で削除された）。"),
    ("PLAY_CHAT", "PLAY", "CLIENTBOUND", 0x0F,
     "Chat。757で追加（署名なしのチャット。1.19の System Chat／Player Chat に置き換わった）。"),
    ("PLAY_CHAT_TO_SERVER", "PLAY", "SERVERBOUND", 0x03,
     "Chat。757で追加（署名なしのチャット。1.19の Chat Message／Chat Command に置き換わった）。"),
]

# 757で削除される8件。id757=-1 であることを実行時に確認する。
REMOVED_IN_757 = ["PLAY_SYSTEM_CHAT", "PLAY_PLAYER_CHAT",
                  "PLAY_SHOULD_DISPLAY_CHAT_PREVIEW", "PLAY_SERVER_DATA",
                  "PLAY_CHAT_PREVIEW", "PLAY_CHAT_MESSAGE",
                  "PLAY_CHAT_COMMAND", "PLAY_CHAT_PREVIEW_TO_SERVER"]


def fmt(value):
    return "-1" if value < 0 else f"0x{value:02x}"


def entry_pattern(n_ids):
    """n引数版のエントリ正規表現。グループ番号：
    name=1・state=2・direction=3・ID=4〜(3+n)・終端子=(4+n)。
    17引数版は終端子が group(21)、18引数版は group(22)。"""
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


JAVADOC_757 = """ * <p>757の要点（759との違い）：
 *   <ul>
 *     <li>パケットIDは759から大きくずれた。tools/packet-ids-757.py で確認。
 *       267エントリすべてが minecraft-data の内部名で一意に写像でき
 *       （760だけの5件は内部名を既知のものとして757での不在を確認し -1）、
 *       759とIDが違うものは123件（クライアント行き79・サーバー行き44）、
 *       757で削除されたものは8件（チャット関係：System Chat 0x5f／
 *       Player Chat 0x30／Should Display Chat Preview 0x4b／Server Data
 *       0x3f／Chat Preview 0x0c のクライアント行きと、Chat Message 0x04／
 *       Chat Command 0x03／Chat Preview 0x05 のサーバー行き。1.19で追加
 *       された署名付きチャット／チャットプレビューが757には無い）、
 *       757で新規のパケットは5件（プレイの Spawn Living Entity 0x02／
 *       Spawn Painting 0x03／Sculk Vibration Signal 0x05／Chat 0x0f の
 *       クライアント行きと、Chat 0x03 のサーバー行き。表に載せない
 *       ping_request は757・759には無い、legacy_server_list_ping は
 *       0xfe）。変更はすべて挿入・削除によるずれで、IDの入れ替えや
 *       並び替えは無い。クライアント行きは 0x02・0x03・0x05 の挿入で
 *       +2／+3 にずれ、0x30・0x3f・0x4b・0x5f の削除で +2／+1／0／-1 に
 *       戻る。サーバー行きは chat 3件が 1件（0x03）にまとまったため
 *       0x06 以降が -2 にずれる。</li>
 *     <li>ペイロードの形式は759とほぼ同じ見込み。違うものがあれば
 *       Stage B/C で PlayTranslator／ChunkTranslator の757対応とともに確認する。
 *       構造の違いとして、757には設定フェーズが無い（ログインの直後が
 *       プレイ。Registry Data はプレイ状態で送られる）、チャットは
 *       署名なしの1パケット、エンティティのスポーンは種類別
 *       （living／painting／player）に分かれている。</li>
 *   </ul>
"""


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-757.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")

    # 冪等性の確認：757列はまだ無いこと。
    if "int id757" in text or "ProtocolVersion.V1_18" in text:
        print("757列は既にあるため何もしない", file=sys.stderr)
        return 1

    before = parse_entries(text, 17)
    print(f"挿入前のエントリ: {len(before)} 件（写像: {len(mapping)} 件）")
    if len(before) != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    if set(before) != set(mapping):
        print(f"名前が合わない：javaにあって写像に無い {sorted(set(before) - set(mapping))}、"
              f"写像にあってjavaに無い {sorted(set(mapping) - set(before))}", file=sys.stderr)
        return 1

    # --- 新規エントリ5件を enum の末尾に追加（17引数版、759〜775は -1） ---
    # 末尾は PLAY_SPECTATE_ENTITY（`;` で終わる）。`;` を `,` に変え、
    # その後ろに5件を追加する（777の前例と同じ段取り）。
    last_name = "PLAY_SPECTATE_ENTITY"
    last = before[last_name]
    if last[3] != ";":
        print(f"末尾のエントリが {last_name} ではない（終端子 {last[3]}）", file=sys.stderr)
        return 1
    old_tail = (f"{last_name}(State.{last[0]}, Direction.{last[1]}, "
                + ", ".join(fmt(v) for v in last[2]) + ");")
    new_lines = []
    for name, state, direction, id757, comment in NEW_ENTRIES:
        new_lines.append(f"        /** {comment} */")
        # 17引数版（759〜775は -1）。757列は後ろの列挿入で足す。
        new_lines.append(
            f"        {name}(State.{state}, Direction.{direction}, "
            + ", ".join(["-1"] * 17) + "),")
    # 最後の1件だけ `,` を `;` にする。
    new_lines[-1] = new_lines[-1][:-1] + ";"
    new_tail = old_tail[:-1] + ",\n" + "\n".join(new_lines)
    if old_tail not in text:
        print("末尾エントリのアンカーが見つからない", file=sys.stderr)
        return 1
    text = text.replace(old_tail, new_tail, 1)
    print(f"新規エントリを追加: {len(NEW_ENTRIES)} 件")

    # --- 757列を先頭に挿入（17引数 → 18引数） ---
    # 終端子は group(21)。数え直し済み（docstring の「775版の罠」参照）。
    pattern = entry_pattern(17)
    assert pattern.groups == 21, f"終端子のグループ番号が 21 ではない：{pattern.groups}"
    new_by_name = {n: v for n, _, _, v, _ in NEW_ENTRIES}

    def replace(m):
        name = m.group(1)
        if name in mapping:
            id757 = mapping[name]["id757"]
        elif name in new_by_name:
            id757 = new_by_name[name]
        else:
            raise SystemExit(f"写像に無いエントリ: {name}")
        ids = list(m.groups()[3:20])
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                + f"{fmt(id757)}, " + ", ".join(ids)
                + f"){m.group(21)}")

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件（写像 {len(mapping)} 件＋新規 {len(NEW_ENTRIES)} 件）")
    if count != len(mapping) + len(NEW_ENTRIES):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1

    # --- 検証：既存の17個のIDが変わっていないこと、18引数版になっていること ---
    # 18引数版では ID（757〜775）= group(4)〜(21)、終端子=group(22)。
    after = parse_entries(new_text, 18)
    if len(after) != len(before) + len(NEW_ENTRIES):
        print(f"18引数版の件数が合わない：{len(after)} 件", file=sys.stderr)
        return 1
    for name, (state, direction, ids17, _) in before.items():
        state2, direction2, ids18, _ = after[name]
        if (state, direction) != (state2, direction2) or ids17 != ids18[1:]:
            print(f"既存のIDが変わっている：{name}", file=sys.stderr)
            return 1
        if ids18[0] != mapping[name]["id757"]:
            print(f"757列の値が写像と違う：{name}（{ids18[0]} != {mapping[name]['id757']}）",
                  file=sys.stderr)
            return 1
    for name, _, _, id757, _ in NEW_ENTRIES:
        _, _, ids18, _ = after[name]
        if ids18[1:] != [-1] * 17 or ids18[0] != id757:
            print(f"新規エントリの値が違う：{name}", file=sys.stderr)
            return 1
    for name in REMOVED_IN_757:
        if after[name][2][0] != -1:
            print(f"削除エントリが 757 で -1 ではない：{name}", file=sys.stderr)
            return 1
    # 17引数版の残存が無いこと。
    left = parse_entries(new_text, 17)
    if left:
        print(f"17引数にできていないエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    print("検証OK：既存17列は不変、757列は写像と一致、削除8件は -1 のまま")

    # --- コンストラクタ・フィールド・アクセサ（757を先頭に） ---
    new_text = insert_after(new_text,
                            "        private final State state;\n"
                            "        private final Direction direction;\n",
                            "        private final int id757;\n", "id757 フィールド")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "PacketName(State state, Direction direction, int id759,",
                            "PacketName(State state, Direction direction, int id757, int id759,",
                            "コンストラクタ引数")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "            this.direction = direction;\n",
                            "            this.id757 = id757;\n", "id757 の代入")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            "        /** このパケットの向き。 */\n"
                            "        public Direction direction() {\n"
                            "            return direction;\n"
                            "        }\n",
                            "\n"
                            "        /** 757（1.18／1.18.1）でのID。存在しない場合は -1。 */\n"
                            "        public int id757() {\n"
                            "            return id757;\n"
                            "        }\n",
                            "id757() アクセサ")
    if new_text is None:
        return 1

    # --- idFor／supportedProtocols／isSupported ---
    # ProtocolVersion.V1_18 はまだ無い（次の作業で追加する）。参照だけ先に入れる。
    new_text = insert_after(new_text,
                            "        public int idFor(int protocol) {\n",
                            "            if (protocol == ProtocolVersion.V1_18) {\n"
                            "                return id757;\n"
                            "            }\n",
                            "idFor の 757 分岐")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return List.of(ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "        return List.of(ProtocolVersion.V1_18, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,",
                            "supportedProtocols")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "        return protocol == ProtocolVersion.V1_19\n",
                            "        return protocol == ProtocolVersion.V1_18\n"
                            "                || protocol == ProtocolVersion.V1_19\n",
                            "isSupported")
    if new_text is None:
        return 1

    # --- javadoc ---
    new_text = replace_once(new_text,
                            "パケットID表：状態×方向×パケット名×プロトコル（760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "パケットID表：状態×方向×パケット名×プロトコル（757 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775）。",
                            "javadoc のプロトコル一覧")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "data/pc/26.1/protocol.json を使った。minecraft-data には",
                            "data/pc/26.1/protocol.json を使った。1.18／1.18.1（757）は\n"
                            " *       data/pc/1.18/protocol.json、1.19（759）は\n"
                            " *       data/pc/1.19/protocol.json を使った（tools/packet-ids-757.py\n"
                            " *       参照。1.18.1 は dataPaths.json で pc/1.18 と同じ protocol.json\n"
                            " *       を使うことが確認済み）。minecraft-data には",
                            "javadoc の情報源（757・759）")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * <p>対応するプロトコルは 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            " * <p>対応するプロトコルは 757（1.18／1.18.1）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と",
                            "javadoc の対応プロトコル")
    if new_text is None:
        return 1
    new_text = insert_after(new_text,
                            " *       （Game Rule Values／Set Game Rule の配列要素）が増えた。</li>\n"
                            " *   </ul>\n",
                            JAVADOC_757, "javadoc の 757 の要点")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            "クライアント発の ping 要求。763にも無い。プロキシは落とす。",
                            "クライアント発の ping 要求。757・759・763にも無い。プロキシは落とす。",
                            "javadoc の Ping Request 注記")
    if new_text is None:
        return 1
    new_text = replace_once(new_text,
                            " * （設定・プレイ）は 766 だけ -1 である。",
                            " * （設定・プレイ）は 766 だけ -1 である。757にだけあるもの\n"
                            " * （Spawn Living Entity、Spawn Painting、Sculk Vibration Signal、\n"
                            " * Chat の行き来）も -1 である。",
                            "javadoc の -1 の説明（757）")
    if new_text is None:
        return 1

    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 757 列を挿入した（既存 267 件＋新規 5 件＝272 件）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
