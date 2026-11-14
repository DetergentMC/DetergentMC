#!/usr/bin/env python3
"""gen-773-proxysession.py から gen-774-proxysession.py を生成する。

方針：773 生成器を写し、shift() の「最新版」を 772→773 に進める。
shift() 内の版数の写像は「1段ずらす」のではなく、新しい版の写像を
追加し、古い写像は残す（例：V1_21_7→V1_21_9 は残し、V1_21_9→V1_21_11 を
追加する）。FIX_EDITS／FIX_EDITS_FULL の Java ブロックは版数だけを
ずらした 774 用になる（pack_format 数値と削除版の注記は手で直す）。

774 固有の注意（773 と違う点）：
- パケットIDは 773→774 で変わらない（0件変更）。773 の FIX_EDITS のうち
  パケットIDを 772→773 に直すものは 774 では不要（既に 773 の ID が
  入っている）。pack_format の数値だけが変わる（69→75）。
- FIX_EDITS のうち、既に 773 パッチへ取り込まれて shift 後の new に
  そのまま残るもの（pack769 の復旧など）は、shift 後の old が合わず
  apply_subs が失敗するため、774 版では落とすか書き直す。
  （下の「FIX_EDITS の手直し」を参照。）
"""
import re
import sys
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/gen-773-proxysession.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/gen-774-proxysession.py")

text = SRC.read_text(encoding="utf-8")
failed = []


def rep(old, new, label="", count=1):
    global text
    n = text.count(old)
    if n != count:
        failed.append(f"{label}: 一致 {n} 件 (期待 {count} 件): {old[:60]!r}")
        return
    text = text.replace(old, new, count)


# --- SRC/OUT ---
rep('SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-772.py")',
    'SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-773.py")')
rep('OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-773.py")',
    'OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-774.py")')

# --- docstring の版数 ---
rep('"""patch-proxysession-772.py から patch-proxysession-773.py を生成する。',
    '"""patch-proxysession-773.py から patch-proxysession-774.py を生成する。')
# （docstring 全体は生成後に目視で直す。ここでは先頭だけ。）

# --- shift_chain：772 を 773 に、773 を 774 に ---
rep('''    if len(nums) >= 3 and nums[0] == "772":
        # 772から始まる多段チェーンは段が1つ増える（772→771→770 は
        # 773→772→771→770 になる）。末端（765/766/767）は固定。
        return "773" + sep + m.group(0)
    if nums[-1] == "772":
        return m.group(0) + sep + "773"''',
    '''    if len(nums) >= 3 and nums[0] == "773":
        # 773から始まる多段チェーンは段が1つ増える（773→772→771 は
        # 774→773→772→771 になる）。末端（765/766/767）は固定。
        return "774" + sep + m.group(0)
    if nums[-1] == "773":
        return m.group(0) + sep + "774"''')
rep('''    if len(nums) == 2:
        # 2要素のペアは1段ずらす（771→772 は 772→773 になる）。
        return sep.join(str(int(n) + 1) for n in nums)''',
    '''    if len(nums) == 2:
        # 2要素のペアは1段ずらす（772→773 は 773→774 になる）。
        return sep.join(str(int(n) + 1) for n in nums)''')

# --- shift_list：772→773、771→772 ---
rep('''    if nums[-1] == "772":
        return m.group(0) + sep + "773"
    if nums[-1] == "771":
        # 新バージョンの一つ下まで列挙しているリストは 772 を追加する。
        return m.group(0) + sep + "772"''',
    '''    if nums[-1] == "773":
        return m.group(0) + sep + "774"
    if nums[-1] == "772":
        # 新バージョンの一つ下まで列挙しているリストは 773 を追加する。
        return m.group(0) + sep + "773"''')

# --- IS_CHAIN / IS_CHAIN_770：772→773、771→772 ---
rep('IS_CHAIN = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?772\\(\\))")',
    'IS_CHAIN = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?773\\(\\))")')
rep('IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?771\\(\\))")',
    'IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?772\\(\\))")')
rep('''    # 1b) isチェーン（is764() || ... || is772()）に is773() を追加し、''',
    '''    # 1b) isチェーン（is764() || ... || is773()）に is774() を追加し、''')
rep('''        last = re.search(r"is(Server)?772\\(\\)$", chain)
        suffix = "isServer773()" if last.group(1) else "is773()"''',
    '''        last = re.search(r"is(Server)?773\\(\\)$", chain)
        suffix = "isServer774()" if last.group(1) else "is774()"''')
rep('''        last = re.search(r"is(Server)?771\\(\\)$", chain)
        suffix = "isServer772()" if last.group(1) else "is772()"''',
    '''        last = re.search(r"is(Server)?772\\(\\)$", chain)
        suffix = "isServer773()" if last.group(1) else "is773()"''')

# --- バージョン文字列：新しい写像を追加（古い写像は残す）。順序が重要：
# 「1.21.9／1.21.10」→「1.21.11」を先に、次に「1.21.10」→「1.21.11」、
# 「1.21.9」→「1.21.11」を済ませてから、1.21.8→1.21.10、1.21.7→1.21.9
# を行う（後から作る 1.21.10／1.21.9 を二重にずらさないため）。
rep('''    text = text.replace("1.21.7／1.21.8", "1.21.9／1.21.10")
    text = text.replace("1.21.7", "1.21.9")
    text = text.replace("1.21.8", "1.21.10")
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")''',
    '''    text = text.replace("1.21.9／1.21.10", "1.21.11")
    text = text.replace("1.21.10", "1.21.11")
    text = text.replace("1.21.9", "1.21.11")
    text = text.replace("1.21.8", "1.21.10")
    text = text.replace("1.21.7", "1.21.9")
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")''')
rep('''    text = text.replace("V1_21_7", "V1_21_9")
    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")''',
    '''    text = text.replace("V1_21_9", "V1_21_11")
    text = text.replace("V1_21_7", "V1_21_9")
    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")''')
rep('''    text = text.replace("PROTOCOL_772", "PROTOCOL_773")
    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")''',
    '''    text = text.replace("PROTOCOL_773", "PROTOCOL_774")
    text = text.replace("PROTOCOL_772", "PROTOCOL_773")
    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")''')
rep('''    text = text.replace("pack772", "pack773")
    text = text.replace("pack771", "pack772")
    text = text.replace("pack770", "pack771")''',
    '''    text = text.replace("pack773", "pack774")
    text = text.replace("pack772", "pack773")
    text = text.replace("pack771", "pack772")
    text = text.replace("pack770", "pack771")''')
rep('''    text = re.sub(r"pack(?:770|771|772|773)", pack_sub, text)''',
    '''    text = re.sub(r"pack(?:770|771|772|773|774)", pack_sub, text)''')

# --- 単独の数字：773→774、772→773（771→772 は既存の行で対応） ---
rep('''    text = re.sub(r"(?<!\\d)772(?!\\d)", "\\x01772", text)
    text = re.sub(r"(?<!\\d)771(?!\\d)", "772", text)
    text = text.replace("\\x01772", "773")''',
    '''    text = re.sub(r"(?<!\\d)773(?!\\d)", "\\x01773", text)
    text = re.sub(r"(?<!\\d)772(?!\\d)", "773", text)
    text = text.replace("\\x01773", "774")''')

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("part 1 OK (SRC/OUT / shift() の写像）")


# --- FIX_EDITS の手直し（shift 後のテキストに対するもの） ---
# 以下の FIX_EDITS は 773 で pack_format 数値や版の一覧を直すものだったが、
# shift すると old が合わない（数値が既に 773 の値になっている）ため、
# 774 用に書き直す。pack770→pack771 のように shift で正しくなるものは残す。

# "constructor javadoc"：版の一覧に 774＝1.21.11 を足す。
rep(
    '    "constructor javadoc": [\n'
    '        ("769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）"),\n'
    '    ],',
    '    "constructor javadoc": [\n'
    '        ("769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10、774＝1.21.11）"),\n'
    '    ],',
    "constructor javadoc を 774 用に")

# "pack773 field"：キーは EDITS 名に合わせたまま、pack_format 69→75 に直す。
rep(
    '    "pack773 field": [\n'
    '        # 773の pack_format は 69（772の 64 のままになっている）。\n'
    '        ("773クライアントに送るリソースパック（pack_format 64）",\n'
    '         "773クライアントに送るリソースパック（pack_format 69）"),\n'
    '    ],',
    '    "pack773 field": [\n'
    '        # 774の pack_format は 75（773の 69 のままになっている）。\n'
    '        ("774クライアントに送るリソースパック（pack_format 69）",\n'
    '         "774クライアントに送るリソースパック（pack_format 75）"),\n'
    '    ],',
    "pack773 field を 75 に")



# SKIP_EDITS に「handleServerJoinGame773 773 branch」を追加する。
# JoinGame772 と重複する編集で、JoinGame773 は JoinGame772 へ委譲するため
# 774 分岐は要らない（shift 版は JoinGame772 の先頭を指す重複編集になる）。
rep(
    '    "handleServerPlay767To766 system_chat passthrough",    "synthesized add-pack 773 id",\n',
    '    "handleServerPlay767To766 system_chat passthrough",    "synthesized add-pack 773 id",\n'
    '    "handleServerJoinGame773 773 branch",\n',
    "SKIP_EDITS への handleServerJoinGame773 773 branch の追加")


# --- part 2：手書き定数ブロックと main() の版名 ---
q = chr(39) * 3  # r-string の三連引用符
def replace_section(start, end, new, label):
    """text 中の start から end まで（end を含む）を new で置き換える（1回だけ）。"""
    global text
    i = text.find(start)
    j = text.find(end, i + len(start)) if i >= 0 else -1
    if i < 0 or j < 0:
        failed.append(f"{label}: 開始または終了が見つからない: {start[:50]!r}")
        return
    j += len(end)
    if text.count(start) != 1:
        failed.append(f"{label}: 開始が {text.count(start)} 件")
        return
    text = text[:i] + new + text[j:]


# SERVER_PLAY_774_TO：773版を1段ずらし、770ハンドラを追加したもの。
# （コメントの連鎖 769→771→773 は 773版の誤記で、769→773→774 に直した。）
SERVER_PLAY_774_TO = 'SERVER_PLAY_774_TO = r\'\'\'\n    /**\n     * 773サーバーのプレイ状態パケットを774形式にする。774に相当が無い／\n     * 変換できないものは null を返す（ログ済み）。\n     * 774クライアントへの送出と、770／769／768／767／766クライアントへの\n     * 多段変換の最終段で共用する。経験値オーブは770・771・772・773・774とも通常の\n     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。\n     */\n    private MinecraftPacket to774ServerPacket(MinecraftPacket packet) {\n        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n                Direction.CLIENTBOUND, ProtocolVersion.V1_21_9).orElse(null);\n        if (name == null) {\n            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));\n            return null;\n        }\n        byte[] translated = switch (name) {\n            case PLAY_ENTITY_METADATA ->\n                    ChunkTranslator.translateEntityMetadata773To774(packet.payload());\n            case PLAY_SET_SLOT ->\n                    ChunkTranslator.translateSetSlot773To774(packet.payload());\n            case PLAY_SET_CURSOR_ITEM ->\n                    ChunkTranslator.translateSetCursorItem773To774(packet.payload());\n            case PLAY_SET_PLAYER_INVENTORY ->\n                    ChunkTranslator.translateSetPlayerInventory773To774(packet.payload());\n            case PLAY_WINDOW_ITEMS ->\n                    ChunkTranslator.translateWindowItems773To774(packet.payload());\n            case PLAY_SET_COOLDOWN ->\n                    ChunkTranslator.translateSetCooldown773To774(packet.payload());\n            case PLAY_ENTITY_EQUIPMENT ->\n                    ChunkTranslator.translateEntityEquipment773To774(packet.payload());\n            case PLAY_TRADE_LIST ->\n                    ChunkTranslator.translateTradeList773To774(packet.payload());\n            case PLAY_OPEN_WINDOW ->\n                    ChunkTranslator.translateOpenWindow773To774(packet.payload());\n            case PLAY_BLOCK_ACTION ->\n                    ChunkTranslator.translateBlockAction773To774(packet.payload());\n            case PLAY_SPAWN_ENTITY ->\n                    ChunkTranslator.translateSpawnEntity773To774(packet.payload());\n            case PLAY_SOUND_EFFECT ->\n                    ChunkTranslator.translateSoundEffect773To774(packet.payload());\n            case PLAY_ENTITY_SOUND_EFFECT ->\n                    ChunkTranslator.translateEntitySoundEffect773To774(packet.payload());\n            case PLAY_STOP_SOUND ->\n                    ChunkTranslator.translateStopSound773To774(packet.payload());\n            case PLAY_WORLD_PARTICLES ->\n                    ChunkTranslator.translateWorldParticles773To774(packet.payload());\n            case PLAY_ENTITY_EFFECT ->\n                    ChunkTranslator.translateEntityEffect773To774(packet.payload());\n            case PLAY_REMOVE_ENTITY_EFFECT ->\n                    ChunkTranslator.translateRemoveEntityEffect773To774(packet.payload());\n            case PLAY_DAMAGE_EVENT ->\n                    ChunkTranslator.translateDamageEvent773To774(packet.payload());\n            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->\n                    ChunkTranslator.translateUpdateAttributes773To774(packet.payload());\n            case PLAY_CHUNK_DATA ->\n                    ChunkTranslator.translateChunkData773To774(packet.payload());\n            case PLAY_BLOCK_CHANGE ->\n                    ChunkTranslator.translateBlockChange773To774(packet.payload());\n            case PLAY_MULTI_BLOCK_CHANGE ->\n                    ChunkTranslator.translateMultiBlockChange773To774(packet.payload());\n            case PLAY_BLOCK_ENTITY_DATA ->\n                    ChunkTranslator.translateBlockEntityData773To774(packet.payload());\n            case PLAY_PLAYER_INFO_UPDATE ->\n                    PlayTranslator.translatePlayerInfo773To774(packet.payload());\n            case PLAY_PLAYER_CHAT ->\n                    PlayTranslator.translatePlayerChat773To774(packet.payload());\n            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->\n                    packet.payload(); // 同形式\n            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->\n                    packet.payload(); // 同形式\n            case PLAY_HELD_ITEM_SLOT ->\n                    PlayTranslator.translateSetHeldItem773To774(packet.payload());\n            case PLAY_EXPLOSION ->\n                    ChunkTranslator.translateExplosion773To774(packet.payload());\n            // ロケーターバー／ダイアログは773にもあるためそのまま通す（同形式）。\n            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->\n                    packet.payload();\n            default -> null;\n        };\n        if (translated == null) {\n            logUntranslatedOnce(name);\n            return null;\n        }\n        OptionalInt id774 = PacketTranslator.translateId(name,\n                ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11);\n        if (id774.isEmpty()) {\n            logUntranslatedOnce(name);\n            return null;\n        }\n        return new MinecraftPacket(id774.getAsInt(), translated);\n    }\n\n    /**\n     * 内部773→774クライアントのプレイ状態（サーバー→クライアント）。\n     * 773→774へ変換してそのまま送る。\n     */\n    private Outcome handleServerPlay773To774(MinecraftPacket packet) {\n        MinecraftPacket as774 = to774ServerPacket(packet);\n        if (as774 == null) {\n            return Outcome.drop();\n        }\n        return Outcome.toClient(as774);\n    }\n\n    /**\n     * 内部770→774クライアントのプレイ状態（サーバー→クライアント）。\n     * 769→773→774へ二段で変換する。\n     */\n    private Outcome handleServerPlay769To774(MinecraftPacket packet) {\n        Outcome to773 = handleServerPlay769To773(packet);\n        if (to773.toClient().isEmpty()) {\n            return to773;\n        }\n        return handleServerPlay773To774(to773.toClient().get(0));\n    }\n\n    /**\n     * 内部770→774クライアントのプレイ状態（サーバー→クライアント）。\n     * 770→773→774へ二段で変換する。\n     */\n    private Outcome handleServerPlay770To774(MinecraftPacket packet) {\n        Outcome to773 = handleServerPlay770To773(packet);\n        if (to773.toClient().isEmpty()) {\n            return to773;\n        }\n        return handleServerPlay773To774(to773.toClient().get(0));\n    }\n\n    /**\n     * 内部769→774クライアントのプレイ状態（サーバー→クライアント）。\n     * 768→773→774へ二段で変換する。\n     */\n    private Outcome handleServerPlay768To774(MinecraftPacket packet) {\n        Outcome to773 = handleServerPlay768To773(packet);\n        if (to773.toClient().isEmpty()) {\n            return to773;\n        }\n        return handleServerPlay773To774(to773.toClient().get(0));\n    }\n\n    /**\n     * 内部768→774クライアントのプレイ状態（サーバー→クライアント）。\n     * 767→773→774へ二段で変換する。\n     */\n    private Outcome handleServerPlay767To774(MinecraftPacket packet) {\n        Outcome to773 = handleServerPlay767To773(packet);\n        if (to773.toClient().isEmpty()) {\n            return to773;\n        }\n        return handleServerPlay773To774(to773.toClient().get(0));\n    }\n\n    /**\n     * 内部767→774クライアントのプレイ状態（サーバー→クライアント）。\n     * 766→773→774へ二段で変換する。\n     */\n    private Outcome handleServerPlay766To774(MinecraftPacket packet) {\n        Outcome to773 = handleServerPlay766To773(packet);\n        if (to773.toClient().isEmpty()) {\n            return to773;\n        }\n        return handleServerPlay773To774(to773.toClient().get(0));\n    }\n\'\'\''

replace_section(
    "# 772版の SERVER_PLAY_772_TO（to772ServerPacket 等）を1段ずらしたもの。\n"
    "# 772→773でパケットIDは変わらないため、ロケーターバー／ダイアログは\n"
    "# 772にもあるため通す（770向けの落としはしない）。\n"
    "SERVER_PLAY_773_TO = r'''",
    "'''",
    SERVER_PLAY_774_TO,
    "SERVER_PLAY_774_TO の置き換え")

# CONFIG_773_EXTRA：デッドコード（patch には出ない）のため、774生成器からは
# 削除する。参照は gen-773-proxysession.py に残る。
replace_section(
    "# 773→772→771→770→769→768へ段々に並べ替え、Show Dialog／Clear Dialog\n"
    "# （773だけの config パケット）は旧クライアント向けに落とす。\n"
    "CONFIG_773_EXTRA = r'''",
    "'''",
    "",
    "CONFIG_773_EXTRA の削除")

# main() のブロック名：772→773（読む側）、773→774（書く側）。
rep('    for name in ("CLIENT_PLAY_772", "SERVER_PLAY_772", "CONFIG_772"):',
    '    for name in ("CLIENT_PLAY_773", "SERVER_PLAY_773", "CONFIG_773"):',
    "main() の読み側ブロック名", count=2)
rep('        new_name = name.replace("772", "773")',
    '        new_name = name.replace("773", "774")',
    "main() の new_name")
rep('    for name in ("CLIENT_PLAY_773", "SERVER_PLAY_773", "CONFIG_773"):\n'
    '        out_lines.append(f"{name} = r' + q + '{new_blocks[name]}' + q + '")',
    '    for name in ("CLIENT_PLAY_774", "SERVER_PLAY_774", "CONFIG_774"):\n'
    '        out_lines.append(f"{name} = r' + q + '{new_blocks[name]}' + q + '")',
    "main() の書き側ブロック名")
rep('    out_lines.append(f"SERVER_PLAY_773_TO = r' + q + '{SERVER_PLAY_773_TO}' + q + '")',
    '    out_lines.append(f"SERVER_PLAY_774_TO = r' + q + '{SERVER_PLAY_774_TO}' + q + '")',
    "main() の SERVER_PLAY_774_TO 出力")


# --- part 3：PROTO_VER の半角ペア保護と constructor validation の書き直し ---
# PROTO_VER の第2分岐（"NNN (X.Y.Z)"）は "/X.Y.Z" 接尾辞に対応していないため、
# 検証メッセージの "773 (1.21.9/1.21.10)" が保護されず 1.21.11/1.21.11 に
# 壊れていた。773版の第1分岐は既に "／X.Y.Z" に対応している。
# （PROTO_VER は773版で既に半角ペア "NNN (1.X.Y/1.X.Z)" に対応済みのため、
# 追加の修正は要らない。確認のみ。）

# "packOverrideForClient javadoc" も同じ罠に加え、pack の写像が版数の写像より
# 1段多いため連鎖が1つ欠ける（772→pack772 が消える）。shift 結果を実際に
# 計算して確認した old（774→pack774 (69)、773→pack773 (64)、771→pack772
# (63)、770→pack771 (55)、769→pack770 (46)、768→pack768）と、正しい new に
# 書き直す。
rep(
    '    "packOverrideForClient javadoc": [\n'
    '        # shift は pack_format の数字をずらさない（773=69、772=64、771=63、770=55、769=46）。\n'
    '        # 770の行も shift で 770→pack771 に潰れているため戻す（769→pack770 も）。\n'
    '        ("773→pack773\\n     * （pack_format 64）、772→pack772\\n"\n'
    '         "     * （pack_format 63）、770→pack771\\n"\n'
    '         "     * （pack_format 55）、769→pack770\\n"\n'
    '         "     * （pack_format 46）、768→pack768",\n'
    '         "773→pack773\\n     * （pack_format 69）、772→pack772\\n"\n'
    '         "     * （pack_format 64）、771→pack771\\n"\n'
    '         "     * （pack_format 63）、770→pack770\\n"\n'
    '         "     * （pack_format 55）、769→pack769\\n"\n'
    '         "     * （pack_format 46）、768→pack768"),\n'
    '    ],',
    '    "packOverrideForClient javadoc": [\n'
    '        # shift は pack_format の数字をずらさない（774=75、773=69、772=64、771=63、770=55、769=46）。\n'
    '        # pack の写像が版数の写像より1段多いため連鎖が1つ欠ける（772→pack772 が消える）。\n'
    '        ("774→pack774\\n     * （pack_format 69）、773→pack773\\n"\n'
    '         "     * （pack_format 64）、771→pack772\\n"\n'
    '         "     * （pack_format 63）、770→pack771\\n"\n'
    '         "     * （pack_format 55）、769→pack770\\n"\n'
    '         "     * （pack_format 46）、768→pack768",\n'
    '         "774→pack774\\n     * （pack_format 75）、773→pack773\\n"\n'
    '         "     * （pack_format 69）、772→pack772\\n"\n'
    '         "     * （pack_format 64）、771→pack771\\n"\n'
    '         "     * （pack_format 63）、770→pack770\\n"\n'
    '         "     * （pack_format 55）、769→pack769\\n"\n'
    '         "     * （pack_format 46）、768→pack768"),\n'
    '    ],',
    "packOverrideForClient javadoc の書き直し")

# FIX_EDITS_FULL「chatGlobalIndex field」を 774 用に書き直す。
# （pack774 ブロックを挟んだ形に直す。）
replace_section(
    '    # フィールドだが、' + chr(39) + 'pack773 field' + chr(39) + ' 編集が先に pack773 ブロックを挟むため',
    "    ),",
    '    # 「chatGlobalIndex field」：\'pack773 field\' 編集が先に pack774\n    # ブロックを pack773 の直後に挟むため、old は pack774 ブロックを\n    # 含む形（pack_format 75）にする。\n    "chatGlobalIndex field": (\n        \'    /**\\n     * 773クライアントに送るリソースパック（pack_format 69）。\\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\\n     * 送ってきたパックをそのまま出すため null のままでよい。\\n     * 出し分けの基準はクライアントのプロトコルである\\n     * （{@link #packOverrideForClient()} 参照）。\\n     */\\n    private ResourcePack pack773;\\n\\n    /**\\n     * 774クライアントに送るリソースパック（pack_format 75）。\\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\\n     * 送ってきたパックをそのまま出すため null のままでよい。\\n     * 出し分けの基準はクライアントのプロトコルである\\n     * （{@link #packOverrideForClient()} 参照）。\\n     */\\n    private ResourcePack pack774;\\n\\n    /**\\n     * 770／771／772／773クライアントへ送る player_chat に付ける globalIndex。\\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\\n     * セッションごとに 0 から数える。\\n     */\\n    private int chatGlobalIndex = 0;\',\n        \'    /**\\n     * 773クライアントに送るリソースパック（pack_format 69）。\\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\\n     * 送ってきたパックをそのまま出すため null のままでよい。\\n     * 出し分けの基準はクライアントのプロトコルである\\n     * （{@link #packOverrideForClient()} 参照）。\\n     */\\n    private ResourcePack pack773;\\n\\n    /**\\n     * 774クライアントに送るリソースパック（pack_format 75）。\\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\\n     * 送ってきたパックをそのまま出すため null のままでよい。\\n     * 出し分けの基準はクライアントのプロトコルである\\n     * （{@link #packOverrideForClient()} 参照）。\\n     */\\n    private ResourcePack pack774;\\n\\n    /**\\n     * 770／771／772／773／774クライアントへ送る player_chat に付ける globalIndex。\\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\\n     * セッションごとに 0 から数える。\\n     */\\n    private int chatGlobalIndex = 0;\'),\n',
    "chatGlobalIndex field FULL の書き直し")

# FIX_EDITS_FULL「synthesized add-pack」を 774 用に書き直す（0x4F）。
replace_section(
    '    "synthesized add-pack": (',
    '        "                toClient.add(new MinecraftPacket(is770() || is771() || is772() || is773() ? 0x4A : 0x4B,"),',
    '    # \'synthesized add-pack\'：773／774の Add Resource Pack ID は 0x4F\n    # （PacketIds：773=0x4f、774=0x4f。772までは 0x4A）。\n    # shift は 0x4A のままにしてしまうため、live（773は 0x4F）に合わせて\n    # 丸ごと書き直す。三項は is773() || is774() ? 0x4F : ... にする。\n    "synthesized add-pack": (\n        "            if (is768() || is769() || is770() || is771() || is772() || is773()) {\\n"\n        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。773は 0x4F。\\n"\n        "                toClient.add(new MinecraftPacket(is773() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x4B,",\n        "            if (is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\\n"\n        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。774は 0x4F。\\n"\n        "                toClient.add(new MinecraftPacket(is773() || is774() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x4B,"),\n',
    "synthesized add-pack FULL の書き直し")

# FIX_EDITS_FULL「handleServerConfiguration 763 branch」を 774 用に書き直す。
# （既存エントリがあるため replace_section で置き換える。追加では後の方が勝つ。）
replace_section(
    '    "handleServerConfiguration 763 branch": (',
    "'),",
    '    # 「handleServerConfiguration 763 branch」：live は773分岐を既に\n    # 含むため、shift した版（773分岐なし）と合わない。774分岐を\n    # 前に足すだけにする。\n    "handleServerConfiguration 763 branch": (\n        \'        if (is763()) {\\n            if (isServer773()) {\\n                return handleServerConfiguration763From773(packet);\\n            }\\n            if (isServer772()) {\\n                return handleServerConfiguration763From772(packet);\\n            }\\n            if (isServer771()) {\\n                return handleServerConfiguration763From771(packet);\\n            }\\n            if (isServer770()) {\\n                return handleServerConfiguration763From770(packet);\\n            }\\n            if (isServer769()) {\\n                return handleServerConfiguration763From769(packet);\\n            }\\n            if (isServer768()) {\\n                return handleServerConfiguration763From768(packet);\\n            }\',\n        \'        if (is763()) {\\n            if (isServer774()) {\\n                return handleServerConfiguration763From774(packet);\\n            }\\n            if (isServer773()) {\\n                return handleServerConfiguration763From773(packet);\\n            }\\n            if (isServer772()) {\\n                return handleServerConfiguration763From772(packet);\\n            }\\n            if (isServer771()) {\\n                return handleServerConfiguration763From771(packet);\\n            }\\n            if (isServer770()) {\\n                return handleServerConfiguration763From770(packet);\\n            }\\n            if (isServer769()) {\\n                return handleServerConfiguration763From769(packet);\\n            }\\n            if (isServer768()) {\\n                return handleServerConfiguration763From768(packet);\\n            }\'),\n',
    "handleServerConfiguration 763 branch FULL の書き直し")

# FIX_BLOCKS 全体を 774 用に書き換える（shift 結果を計算して確認した）。
replace_section(
    "FIX_BLOCKS = {",
    "    ],\n\n}",
    '# FIX_BLOCKS：773版の修正は shift 後のブロックに既に含まれているため、\n# 774では段数・連鎖の潰れ（772段の欠落）だけを直す。キーは読む側の版名\n#（CLIENT_PLAY_774 等）のまま。\nFIX_BLOCKS = {\n    "CLIENT_PLAY_774": [\n        # Set Creative Slot：767→774 は七段。\n        (\'     * Set Creative Slot は767→768→769→770→771→772→773→774へ六段で変換する\', \'     * Set Creative Slot は767→768→769→770→771→772→773→774へ七段で変換する\'),\n        # Set Creative Slot の連鎖：771→773 の潰れを 771→772→773 に展開する。\n        (\'                    byte[] payload = ChunkTranslator.translateSetCreativeSlot773To774(\\n                            ChunkTranslator.translateSetCreativeSlot771To773(\\n                                    ChunkTranslator.translateSetCreativeSlot770To771(\\n                                            ChunkTranslator.translateSetCreativeSlot769To770(\\n                                                    ChunkTranslator.translateSetCreativeSlot768To769(\\n                                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()))))));\',\n         \'                    byte[] payload = ChunkTranslator.translateSetCreativeSlot773To774(\\n                            ChunkTranslator.translateSetCreativeSlot772To773(\\n                                    ChunkTranslator.translateSetCreativeSlot771To772(\\n                                            ChunkTranslator.translateSetCreativeSlot770To771(\\n                                            ChunkTranslator.translateSetCreativeSlot769To770(\\n                                                    ChunkTranslator.translateSetCreativeSlot768To769(\\n                                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload())))))));\'),\n    ],\n    "CONFIG_774": [\n        # Registry Data（770のエントリ配列）：771→773 の潰れを 771→772→773 に展開する。\n        (\'                    byte[] payload770to771 =\\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\\n                    byte[] payload771to773 =\\n                            PlayTranslator.translateRegistryData771To773(payload770to771);\\n                    toClient.add(new MinecraftPacket(0x07,\\n                            PlayTranslator.translateRegistryData773To774(payload771to773)));\',\n         \'                    byte[] payload770to771 =\\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\\n                    byte[] payload771to772 =\\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\\n                    byte[] payload772to773 =\\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\\n                    toClient.add(new MinecraftPacket(0x07,\\n                            PlayTranslator.translateRegistryData773To774(payload772to773)));\'),\n    ],\n    "SERVER_PLAY_774": [\n        # 767クライアント：コメントの末尾を 769→768 に戻し、to771ServerPacketFrom773\n        # の潰れを to772ServerPacketFrom773＋to771ServerPacketFrom772 に展開する。\n        (\'        // 767クライアント：774→773→772→771→770→769→768へ変換してから770→769へ。\\n        if (is767()) {\\n            MinecraftPacket as773 = to773ServerPacketFrom774(packet);\\n            if (as773 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as771 = to771ServerPacketFrom773(as773);\\n            if (as771 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\\n            if (as770 == null) {\\n                return Outcome.drop();\\n            }\\n\',\n         \'        // 767クライアント：774→773→772→771→770→769→768へ変換してから769→768へ。\\n        if (is767()) {\\n            MinecraftPacket as773 = to773ServerPacketFrom774(packet);\\n            if (as773 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as772 = to772ServerPacketFrom773(as773);\\n            if (as772 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\\n            if (as771 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\\n            if (as770 == null) {\\n                return Outcome.drop();\\n            }\\n\'),\n        # 766クライアント：同上（コメントの末尾は 769→767）。\n        (\'        // 766クライアント：774→773→772→771→770→769→768へ変換してから770→768へ。\\n        if (is766()) {\\n            MinecraftPacket as773 = to773ServerPacketFrom774(packet);\\n            if (as773 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as771 = to771ServerPacketFrom773(as773);\\n            if (as771 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\\n            if (as770 == null) {\\n                return Outcome.drop();\\n            }\\n\',\n         \'        // 766クライアント：774→773→772→771→770→769→768へ変換してから769→767へ。\\n        if (is766()) {\\n            MinecraftPacket as773 = to773ServerPacketFrom774(packet);\\n            if (as773 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as772 = to772ServerPacketFrom773(as773);\\n            if (as772 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\\n            if (as771 == null) {\\n                return Outcome.drop();\\n            }\\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\\n            if (as770 == null) {\\n                return Outcome.drop();\\n            }\\n\'),\n    ],\n}',
    "FIX_BLOCKS の書き換え")

# "handleServerPlayFrom766 773 branch"：名前と段数を 774 用に書き直す。
# （shift 後のテキストは 773→774 の連鎖で七段／六段のため、八段／七段に直す。）
replace_section(
    '    "handleServerPlayFrom766 773 branch": [',
    "    ],",
    '    "handleServerPlayFrom766 773 branch": [\n        # 段数の数え間違い：766からは八段、767からは七段。\n        ("// 内部766は 766→767→768→769→770→771→772→773→774 の七段、内部767は 767→768→769→770→771→772→773→774 の六段で送る。",\n         "// 内部766は 766→767→768→769→770→771→772→773→774 の八段、内部767は 767→768→769→770→771→772→773→774 の七段で送る。"),\n    ],',
    "handleServerPlayFrom766 773 branch の書き直し")

# "synthesized config registry loop"：shift 結果を実際に計算して確認した。
# 774分岐は 771→773 に潰れ、773分岐は 771→773 に潰れる（いずれも772段が落ちる）。
# さらに 773分岐のコメントは shift で先頭の 766 が落ちる（"767→…" になる）。
# 3つの sub をすべて 774 用に書き直す。
replace_section(
    '    "synthesized config registry loop": [',
    "    ],",
    '    "synthesized config registry loop": [\n        # shift は 771→772→773 を 771→773 に潰す（772段が落ちる）。\n        # 773段を挟んだ二段（771→772→773）に展開する。\n        (\'                    byte[] payload770to771 =\\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\\n                    byte[] payload771to773 =\\n                            PlayTranslator.translateRegistryData771To773(payload770to771);\\n                    registryPayload =\\n                            PlayTranslator.translateRegistryData773To774(payload771to773);\',\n         \'                    byte[] payload770to771 =\\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\\n                    byte[] payload771to772 =\\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\\n                    byte[] payload772to773 =\\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\\n                    registryPayload =\\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\'),\n        (\'// 774：766→767→768→769→770→771→772→773→774へ七段で並べ替える。\', \'// 774：766→767→768→769→770→771→772→773→774へ八段で並べ替える。\'),\n        (\'                if (is773()) {\\n                    // 773：767→768→769→770→771→772→773へ六段で並べ替える。\\n                    byte[] payload766to767 =\\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\\n                    byte[] payload767to768 =\\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\\n                    byte[] payload768to769 =\\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\\n                    byte[] payload769to770 =\\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\\n                    byte[] payload770to771 =\\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\\n                    registryPayload =\\n                            PlayTranslator.translateRegistryData771To773(payload770to771);\',\n         \'                if (is773()) {\\n                    // 773：766→767→768→769→770→771→772→773へ七段で並べ替える。\\n                    byte[] payload766to767 =\\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\\n                    byte[] payload767to768 =\\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\\n                    byte[] payload768to769 =\\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\\n                    byte[] payload769to770 =\\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\\n                    byte[] payload770to771 =\\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\\n                    byte[] payload771to772 =\\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\\n                    registryPayload =\\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\'),\n    ],',
    "synthesized config registry loop の書き直し")

# "serverProtocol() javadoc" も同じ罠：shift 後の old は末尾が 772 の一覧だが、
# 実際の shift 済みテキストは 773 のエントリを既に含む。774 用に書き直す。
rep(
    '    "serverProtocol() javadoc": [\n'
    '        ("769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）。", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）。"),\n'
    '    ],',
    '    "serverProtocol() javadoc": [\n'
    '        ("769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）。", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10、774＝1.21.11）。"),\n'
    '    ],',
    "serverProtocol() javadoc の書き直し")

# "constructor validation" のメッセージ部分：shift 後の old は
# "769 (1.21.4), 770 (1.21.5), 771 (1.21.6) or 772 (1.21.7): " だが、
# 実際の shift 済みテキストは "771 (1.21.6), 772 (1.21.7) or 773 (1.21.9/1.21.10): "
# なので合わない。774 用に書き直す。
rep(
    '        ("769 (1.21.4), 770 (1.21.5), 771 (1.21.6) or 772 (1.21.7): ",\n'
    '         "769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7) or 773 (1.21.9/1.21.10): "),',
    '        ("771 (1.21.6), 772 (1.21.7) or 773 (1.21.9/1.21.10): ",\n'
    '         "771 (1.21.6), 772 (1.21.7), 773 (1.21.9/1.21.10) or 774 (1.21.11): "),',
    "constructor validation のメッセージ書き直し")

# FIX_EDITS_FULL に「*ClientId」4件を追加する（773の手直し ID を live に合わせる）。
rep(
    "FIX_EDITS_FULL = {\n",
    "FIX_EDITS_FULL = {\n" + '    # 「*ClientId」4件：773でパケットIDが変わったため live は手直し済み\n    #（Start Configuration 0x74、Spawn Position 0x5F、Keep Alive 0x2B、\n    # Ping 0x3B）。774の ID は773と同じため、is773() の分岐に is774() を\n    # 足すだけにする。shift した版は live と合わないため FULL で置き換える。\n    "startConfigurationClientId": (\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is773() ? 0x74 : is770() || is771() || is772() ? 0x6F : 0x69);",\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is773() || is774() ? 0x74 : is770() || is771() || is772() ? 0x6F : 0x69);"),\n    "spawnPositionClientId": (\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is773() ? 0x5F : is770() || is771() || is772() ? 0x5A : 0x56);",\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is773() || is774() ? 0x5F : is770() || is771() || is772() ? 0x5A : 0x56);"),\n    "keepAliveClientId": (\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is773() ? 0x2B : is768() || is769() ? 0x27 : 0x26);",\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is773() || is774() ? 0x2B : is768() || is769() ? 0x27 : 0x26);"),\n    "pingClientId": (\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_PING,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is773() ? 0x3B : is770() || is771() || is772() ? 0x36 : 0x35);",\n        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\\n"\n        "            return PacketTranslator.toClientId(PacketName.PLAY_PING,\\n"\n        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is773() || is774() ? 0x3B : is770() || is771() || is772() ? 0x36 : 0x35);"),\n',
    "FIX_EDITS_FULL への ClientId 4件の追加")

# FIX_EDITS_FULL に「handleServerPlay dispatch」を追加する。
rep(
    "FIX_EDITS_FULL = {\n",
    "FIX_EDITS_FULL = {\n" + '    # 「handleServerPlay dispatch」：773で Join Game／Spawn Position／\n    # Start Configuration の ID が変わったため live は手直し済み\n    #（773は 0x30／0x5F／0x74）。774の ID は773と同じため、774分岐を\n    # 同じ ID で前に足すだけにする。\n    "handleServerPlay dispatch": (\n        \'        // 内部773：Join Game は 0x30、Spawn Position は 0x5F、\\n        // Start Configuration は 0x74。\\n        if (isServer773()) {\\n            return handleServerPlayFrom773(packet);\\n        }\\n        // 内部772：Join Game は 0x2B、Spawn Position は 0x5A、\\n        // Start Configuration は 0x6F（771と同じ）。\\n        if (isServer772()) {\\n            return handleServerPlayFrom772(packet);\\n        }\\n        // 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\\n        // Start Configuration は 0x6F（770と同じ）。\\n        if (isServer771()) {\\n            return handleServerPlayFrom771(packet);\\n        }\\n        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\\n        // Start Configuration は 0x6F（769から -1）。\\n        if (isServer770()) {\\n            return handleServerPlayFrom770(packet);\\n        }\\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\\n        // Start Configuration は 0x70（768と同じ番号）。\\n        if (isServer769()) {\\n            return handleServerPlayFrom769(packet);\\n        }\\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\\n        // Start Configuration は 0x70。\\n        if (isServer768()) {\\n            return handleServerPlayFrom768(packet);\\n        }\',\n        \'        // 内部774：Join Game は 0x30、Spawn Position は 0x5F、\\n        // Start Configuration は 0x74（773と同じ）。\\n        if (isServer774()) {\\n            return handleServerPlayFrom774(packet);\\n        }\\n        // 内部773：Join Game は 0x30、Spawn Position は 0x5F、\\n        // Start Configuration は 0x74。\\n        if (isServer773()) {\\n            return handleServerPlayFrom773(packet);\\n        }\\n        // 内部772：Join Game は 0x2B、Spawn Position は 0x5A、\\n        // Start Configuration は 0x6F（771と同じ）。\\n        if (isServer772()) {\\n            return handleServerPlayFrom772(packet);\\n        }\\n        // 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\\n        // Start Configuration は 0x6F（770と同じ）。\\n        if (isServer771()) {\\n            return handleServerPlayFrom771(packet);\\n        }\\n        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\\n        // Start Configuration は 0x6F（769から -1）。\\n        if (isServer770()) {\\n            return handleServerPlayFrom770(packet);\\n        }\\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\\n        // Start Configuration は 0x70（768と同じ番号）。\\n        if (isServer769()) {\\n            return handleServerPlayFrom769(packet);\\n        }\\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\\n        // Start Configuration は 0x70。\\n        if (isServer768()) {\\n            return handleServerPlayFrom768(packet);\\n        }\'),\n',
    "FIX_EDITS_FULL への handleServerPlay dispatch の追加")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)

OUT.write_text(text, encoding="utf-8")
print("gen-774-proxysession.py を書き出した（shift() と SRC/OUT と FIX_EDITS の一部）。")
print("FIX_EDITS／FIX_EDITS_FULL の残りは生成後に目視で直すこと。")
print("特に packOverrideForClient javadoc／branch、constructor validation、")
print("packOverrideForClient branch 以外の FIX_EDITS が shift 後に合うか確認すること。")
