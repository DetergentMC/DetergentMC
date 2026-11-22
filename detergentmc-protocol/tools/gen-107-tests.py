"""108 の単体テストを 107 用に複製する。

107 は 108 とレジストリ・パケット ID とも完全同一で、ワイヤ形式の
差分は Join Game の dimension（107 は i8、108 は i32）の1件だけ
（Stage A 実測）。そのため 108 用テストの機械的な改名で 107 用が
得られるが、Join Game ペイロードの dimension 書き込みだけは
writeInt → writeByte に直す（PlayTranslator107Test と
ProxySession107Test に各1箇所）。ファイル内の "108" はすべて
プロトコル参照・識別子・コメントであることを生成前に確認済み
（データ値としての 108 は無い）。

構造が変わるファイル（PacketIds・RegistryRemapper・
ClientAdapter・ProtocolVersion）は別途手書きする。
冪等ではない（107 ファイルを上書きする）。
"""

from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")


def rename_108_to_107(text):
    text = text.replace("V1_9_1", "V1_9")
    text = text.replace("1.9.1", "1.9")
    text = text.replace("108", "107")
    return text


def clone(src_name, dst_name, fixes=()):
    src = (TEST / src_name).read_text(encoding="utf-8")
    out = rename_108_to_107(src)
    for old, new in fixes:
        assert out.count(old) == 1, f"{dst_name}: 修正アンカーが一意でない: {old[:50]}"
        out = out.replace(old, new)
    (TEST / dst_name).write_text(out, encoding="utf-8")
    print(f"{dst_name} を生成した")


# Join Game の dimension は 107 では i8。ペイロード生成側を直す。
JOINGAME_FIX = (
    "        writer.writeInt(0);\n",
    "        writer.writeByte(0); // dimension（107 は i8。108 以降は i32）\n",
)

# 404 クライアントが受け取る Join Game は 404 形式（dimension i32）。
# 複製元の parseJoinGame108 はそのまま正しかったが、機械改名で
# parseJoinGame107（i8 読み）になると読めなくなるため、404 形式の
# parse へ直す（107⇔108 の唯一の形式差分がここに現れる）。
JOINGAME_404_FIX = (
    """        assertEquals(0x25, join.toClient().get(0).id()); // 404 の Join Game
        JoinGame107 parsed = PlayTranslator.parseJoinGame107(join.toClient().get(0).payload());""",
    """        assertEquals(0x25, join.toClient().get(0).id()); // 404 の Join Game
        // 届くペイロードは 404 形式（dimension i32）。107 形式（i8）では
        // 読めないことが、107⇔108 の唯一の形式差分をここでも示す。
        PlayTranslator.JoinGame404 parsed = PlayTranslator.parseJoinGame404(join.toClient().get(0).payload());""",
)

clone("ClientJoinFlow108Test.java", "ClientJoinFlow107Test.java")
clone("PlayTranslator108Test.java", "PlayTranslator107Test.java",
      fixes=(JOINGAME_FIX,))
clone("SlotTranslator108Test.java", "SlotTranslator107Test.java")
clone("ProxySession108Test.java", "ProxySession107Test.java",
      fixes=(JOINGAME_FIX, JOINGAME_404_FIX))
clone("ChunkTranslator108Test.java", "ChunkTranslator107Test.java")

print("完了")
