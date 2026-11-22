"""109 の単体テストを 108 用に複製する。

108 は 109 とレジストリ・ワイヤ形式・パケット ID とも完全同一
（Stage A 実測）ため、109 用テストの機械的な改名で 108 用が
得られる。ファイル内の "109" はすべてプロトコル参照・識別子・
プロトコル 109 についてのコメントであることを生成前に確認済み
（データ値としての 109 は無い）。

構造が変わるファイル（PacketIds・RegistryRemapper・
ClientAdapter・ProtocolVersion）は別途手書きする。
冪等ではない（108 ファイルを上書きする）。
"""

from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")


def rename_109_to_108(text):
    text = text.replace("V1_9_2", "V1_9_1")
    text = text.replace("1.9.2", "1.9.1")
    text = text.replace("109", "108")
    return text


def clone(src_name, dst_name):
    src = (TEST / src_name).read_text(encoding="utf-8")
    (TEST / dst_name).write_text(rename_109_to_108(src), encoding="utf-8")
    print(f"{dst_name} を生成した")


clone("ClientJoinFlow109Test.java", "ClientJoinFlow108Test.java")
clone("PlayTranslator109Test.java", "PlayTranslator108Test.java")
clone("SlotTranslator109Test.java", "SlotTranslator108Test.java")
clone("ProxySession109Test.java", "ProxySession108Test.java")
clone("ChunkTranslator109Test.java", "ChunkTranslator108Test.java")

print("完了")
