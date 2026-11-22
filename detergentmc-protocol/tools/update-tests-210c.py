"""ClientAdapter 系テストの位置固定を実リストで正規化する。

update-tests-210.py（値の書き換え）と update-tests-210b.py
（インデックスの +1）が重なったファイルでは、先頭5件の固定が
二重にずれている。assertEquals(<proto>, supported.get(<n>) の
n を、実際の supportedClientProtocols() の並びから決まる
正しい位置に書き直すことで全ファイルを同じ状態に揃える。
"""

import re
from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")

ORDER = [210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485,
         490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764,
         765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776]
INDEX = {p: i for i, p in enumerate(ORDER)}

PIN = re.compile(
    r"assertEquals\((\d+), supported\.get\((\d+)\)")

for p in sorted(TEST.glob("ClientAdapter*Test.java")):
    if p.name == "ClientAdapter210Test.java":
        continue
    t = p.read_text(encoding="utf-8")

    def fix(m):
        proto = int(m.group(1))
        assert proto in INDEX, f"{p.name}: 未知のプロトコル {proto}"
        return f"assertEquals({proto}, supported.get({INDEX[proto]})"

    t2 = PIN.sub(fix, t)
    if t2 != t:
        p.write_text(t2, encoding="utf-8")
        print(f"{p.name} の位置固定を正規化した")
