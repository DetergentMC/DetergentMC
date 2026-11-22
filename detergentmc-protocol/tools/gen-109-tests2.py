"""ProxySession110Test / ChunkTranslator110Test を 109 用に複製する。

機械的な改名のみ。チャンクのブロックエンティティ末尾の除去と
109 固有テストの追加は生成後に手で編集する。
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


clone("ProxySession110Test.java", "ProxySession109Test.java", [
    ("ProxySession110Test", "ProxySession109Test"),
    ("JoinGame110", "JoinGame109"),
    ("joined110Against758", "joined109Against758"),
    ("loginTo(110,", "loginTo(109,"),
    ("loginTo(404, 110)", "loginTo(404, 109)"),
    ("loginTo(763, 110)", "loginTo(763, 109)"),
    ("handshake(110,", "handshake(109,"),
    ("handshake110IsAccepted", "handshake109IsAccepted"),
    ("assertEquals(110, result.session().clientProtocol());",
     "assertEquals(109, result.session().clientProtocol());"),
    ("join110Against758", "join109Against758"),
    ("join110Against110", "join109Against109"),
    ("join404ClientAgainst110Server", "join404ClientAgainst109Server"),
    ("creativeSlotFrom110Client", "creativeSlotFrom109Client"),
    ("keepAliveFrom110Server", "keepAliveFrom109Server"),
    ("advancementsFrom758ServerNeverReach110Client",
     "advancementsFrom758ServerNeverReach109Client"),
    ("stripResourcePackHash110", "stripResourcePackHash109"),
    ("prependResourcePackHash110", "prependResourcePackHash109"),
    ("resourcePackHashIsStrippedAndPrepended", "resourcePackHashIsStrippedAndPrepended"),
])

clone("ChunkTranslator110Test.java", "ChunkTranslator109Test.java", [
    ("ChunkTranslator110Test", "ChunkTranslator109Test"),
    ("chunk110()", "chunk109()"),
    ("ChunkData110To758", "ChunkData109To758"),
    ("ChunkData758To110", "ChunkData758To109"),
    ("repackPosition758To110", "repackPosition758To109"),
    ("repackPosition110To758", "repackPosition109To758"),
    ("translateSlot758To110", "translateSlot758To109"),
    ("translateSlot110To758", "translateSlot109To758"),
    ("translateSpawnPainting758To110", "translateSpawnPainting758To109"),
    ("translateSpawnPainting110To758", "translateSpawnPainting109To758"),
    ("translateEntityEffect758To110", "translateEntityEffect758To109"),
    ("translateEntityEffect110To758", "translateEntityEffect109To758"),
    ("translateScoreboardObjective758To110", "translateScoreboardObjective758To109"),
    ("translateScoreboardObjective110To758", "translateScoreboardObjective109To758"),
    ("translateEntityMetadata758To110", "translateEntityMetadata758To109"),
    ("translateEntityMetadata110To758", "translateEntityMetadata109To758"),
    ("translateSpawnEntity758To110", "translateSpawnEntity758To109"),
    ("isObjectEntity110", "isObjectEntity109"),
    ("translateSpawnLiving758To110", "translateSpawnLiving758To109"),
    ("spawnLiving110To758", "spawnLiving109To758"),
    ("translateOpenWindow758To110", "translateOpenWindow758To109"),
    ("translateBlockPlace758To110", "translateBlockPlace758To109"),
    ("translateBlockPlace110To758", "translateBlockPlace109To758"),
    ("translateCollect758To110", "translateCollect758To109"),
    ("translateCollect110To758", "translateCollect109To758"),
    ("titleText758To110", "titleText758To109"),
    ("titleAnimation758To110", "titleAnimation758To109"),
    ("titleClear758To110", "titleClear758To109"),
    ("titleAction110", "titleAction109"),
    ("translateTitle110To758", "translateTitle109To758"),
    ("actionBar758ToChat110", "actionBar758ToChat109"),
    ("chatPosition110", "chatPosition109"),
    ("chat110ToActionBar758", "chat109ToActionBar758"),
    ("translateSoundEffect758To110", "translateSoundEffect758To109"),
    ("translateSoundEffect110To758", "translateSoundEffect109To758"),
    ("translateNamedSoundEffect758To110", "translateNamedSoundEffect758To109"),
    ("translateNamedSoundEffect110To758", "translateNamedSoundEffect109To758"),
    ("mapSound110To758", "mapSound109To758"),
    ("chunk110RoundTripsThrough758", "chunk109RoundTripsThrough758"),
    ("chunkSection110UsesCrossingPacking", "chunkSection109UsesCrossingPacking"),
    ("spawnLiving110UsesMobId90ForPig", "spawnLiving109UsesMobId90ForPig"),
    ("openWindow110UsesStringTypeAndSlotCount", "openWindow109UsesStringTypeAndSlotCount"),
    ("spawnLiving110TypeIsUnsignedByte", "spawnLiving109TypeIsUnsignedByte"),
])

print("完了")
