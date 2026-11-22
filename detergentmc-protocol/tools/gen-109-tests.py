"""110 の単体テストを 109 用に複製する（機械的な置換のみ）。

構造が変わるファイル（PacketIds・RegistryRemapper・ProxySession・
ChunkTranslator・ClientAdapter・ProtocolVersion）は別途手書きする。
冪等ではない（109 ファイルを上書きする）。
"""

from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")


def clone(src_name, dst_name, pairs):
    src = (TEST / src_name).read_text(encoding="utf-8")
    for old, new in pairs:
        assert old in src, f"{src_name}: 置換対象が無い: {old[:60]}"
        src = src.replace(old, new)
    (TEST / dst_name).write_text(src, encoding="utf-8")
    print(f"{dst_name} を生成した")


# ClientJoinFlow109Test
clone("ClientJoinFlow110Test.java", "ClientJoinFlow109Test.java", [
    ("ClientJoinFlow110Test", "ClientJoinFlow109Test"),
    ("/** 110（1.9.4）クライアントの参加フロー。設定フェーズは無い（335／340／393と同じ）。 */",
     "/** 109（1.9.2）クライアントの参加フロー。設定フェーズは無い（335／340／393と同じ）。 */"),
    ("void stagesFor110HaveNoConfiguration() {", "void stagesFor109HaveNoConfiguration() {"),
    ("new ClientJoinFlow(110)", "new ClientJoinFlow(109)"),
    ("// 314 と 317 はこのツリーでは未対応（110 は 1.9.4 として対応済み）。",
     "// 314 と 317 はこのツリーでは未対応（109 は 1.9.2 として対応済み）。"),
])

# PlayTranslator109Test
clone("PlayTranslator110Test.java", "PlayTranslator109Test.java", [
    ("PlayTranslator110Test", "PlayTranslator109Test"),
    ("JoinGame110", "JoinGame109"),
    ("translateRespawn110To758", "translateRespawn109To758"),
    ("translateRespawn758To110", "translateRespawn758To109"),
    ("joinGame110Payload", "joinGame109Payload"),
    ("joinGame110Parses", "joinGame109Parses"),
    ("joinGame110To758DropsDifficultyAndSynthesizesViewDistance",
     "joinGame109To758DropsDifficultyAndSynthesizesViewDistance"),
    ("joinGame758To110SynthesizesNormalDifficulty",
     "joinGame758To109SynthesizesNormalDifficulty"),
    ("respawn110To758DropsDifficulty", "respawn109To758DropsDifficulty"),
    (""" * 110（1.9.4）の Join Game／Respawn 変換。ワイヤ形式は 393 と完全一致
 * （実バニラ 1.9.4 の Join Game 採取で 20/20 バイト消費を確認）
 * するため 393 の実装へ委譲していることを、110 の入口で固定する。
 * 110 の Join Game は difficulty（u8）を持ち viewDistance を持たない。""",
     """ * 109（1.9.2）の Join Game／Respawn 変換。ワイヤ形式は 110・393 と
 * 完全一致（実バニラ 1.9.2 の Join Game 採取でも 20/20 バイト消費を
 * 確認）するため 393 の実装へ委譲していることを、109 の入口で固定する。
 * 109 の Join Game は difficulty（u8）を持ち viewDistance を持たない。"""),
    ("/** 110 の Join Game ペイロード（実 1.9.4 のレイアウト＝393 と同一）。 */",
     "/** 109 の Join Game ペイロード（実 1.9.2 のレイアウト＝393 と同一）。 */"),
    ("// 758 側に difficulty は無いため、110 へ戻すときは normal（2）を合成する。",
     "// 758 側に difficulty は無いため、109 へ戻すときは normal（2）を合成する。"),
])

# SlotTranslator109Test
clone("SlotTranslator110Test.java", "SlotTranslator109Test.java", [
    ("SlotTranslator110Test", "SlotTranslator109Test"),
    ("slotTo110", "slotTo109"),
    ("slotFrom110", "slotFrom109"),
    ("clientboundTo110", "clientboundTo109"),
    ("clientboundFrom110", "clientboundFrom109"),
    ("serverboundFrom110", "serverboundFrom109"),
    ("ProtocolVersion.V1_9_4", "ProtocolVersion.V1_9_2"),
    ("110（1.9.4）", "109（1.9.2）"),
    ("110 層", "109 層"),
    ("110 に無い", "109 に無い"),
    ("110 は i8", "109 は i8"),
    ("110 ワイヤ形式", "109 ワイヤ形式"),
])

print("完了")
