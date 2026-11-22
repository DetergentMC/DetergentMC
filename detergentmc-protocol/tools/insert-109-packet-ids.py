"""PacketIds.java に 109（1.9.2）のID列を追加する。

109 と 110 のパケット ID 表の差分は Stage A 実測で確定した次の
2点だけ（packet-ids-109.py で照合）：
  1. play／クライアント行きに update_sign（0x46）が 109 にだけ
     ある。110 では削除されたため、それより後ろの6件
     （sound_effect 0x46→0x47、playerlist_header 0x47→0x48、
     collect 0x48→0x49、entity_teleport 0x49→0x4a、
     entity_update_attributes 0x4a→0x4b、entity_effect
     0x4b→0x4c）が 109 では +1 ずれる。play／クライアント行きの
     最大 ID が 0x4b（110）／0x4c（109）なので、ずれるのは
     この6件で全部。
  2. それ以外のステート・方向は ID 差分 0 件。

そのため各エントリの先頭引数（id110）の直前に id109 を挿入し、
play／クライアント行きで id110 が 0x46〜0x4b の6件だけ +1 する。
update_sign（クライアント行き）は新規エントリ
PLAY_UPDATE_SIGN_CLIENTBOUND として PLAY_UPDATE_SIGN の直後に
追加する（id109=0x46、他は全て -1。758 を含め 110 以後の全版に
クライアント行き update_sign は無い）。

フィールド・コンストラクタ・アクセサ・idFor・supportedProtocols・
isSupported・クラス javadoc は手で直す前提で、このスクリプトは
既存エントリ行と新規エントリの追加だけを対象にする。

使い方：
  python3 tools/insert-109-packet-ids.py
  冪等ではない（2回実行すると列が二重になる）。git diff で確認すること。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

ARG = r"(-1|0x[0-9a-fA-F]+)"
ENTRY = re.compile(
    r"(\w+)\(State\.(\w+), Direction\.(\w+), "
    + ", ".join([ARG] * 37) + r"\)[,;]")

SHIFTED = {  # play／クライアント行きで +1 ずれる6件（enum名 → id110）
    "PLAY_SOUND_EFFECT": 0x46,
    "PLAY_PLAYERLIST_HEADER": 0x47,
    "PLAY_COLLECT": 0x48,
    "PLAY_ENTITY_TELEPORT": 0x49,
    "PLAY_ENTITY_UPDATE_ATTRIBUTES": 0x4a,
    "PLAY_ENTITY_EFFECT": 0x4b,
}


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    text = JAVA_FILE.read_text(encoding="utf-8")
    count = 0
    shifted_seen = set()

    def repl(m):
        nonlocal count
        count += 1
        name, state, direction = m.group(1), m.group(2), m.group(3)
        id110 = int(m.group(4), 16) if m.group(4).startswith("0x") else -1
        id109 = id110
        if state == "PLAY" and direction == "CLIENTBOUND" and id110 >= 0x46:
            assert name in SHIFTED and SHIFTED[name] == id110, (
                f"想定外のずれ対象: {name} id110={id110:#x}")
            shifted_seen.add(name)
            id109 = id110 + 1
        whole = m.group(0)
        head = f"{name}(State.{state}, Direction.{direction}, "
        return head + fmt(id109) + ", " + whole[len(head):]

    new_text = ENTRY.sub(repl, text)
    print(f"エントリ {count} 件に id109 列を挿入した。")
    if count != 283:
        print("件数が 283 でないため書き込まない。", file=sys.stderr)
        return 1
    if shifted_seen != set(SHIFTED):
        print(f"ずれた6件が揃わない: {shifted_seen}", file=sys.stderr)
        return 1

    # 新規エントリ PLAY_UPDATE_SIGN_CLIENTBOUND を PLAY_UPDATE_SIGN の直後へ
    anchor = re.compile(
        r"(        PLAY_UPDATE_SIGN\(State\.PLAY, Direction\.SERVERBOUND, "
        + ", ".join([ARG] * 38) + r"\),\n)")
    m = anchor.search(new_text)
    assert m, "PLAY_UPDATE_SIGN 行が見つからない"
    new_entry = ("        PLAY_UPDATE_SIGN_CLIENTBOUND(State.PLAY, "
                 "Direction.CLIENTBOUND, 0x46"
                 + ", -1" * 37 + "),\n")
    new_text = new_text[:m.end()] + new_entry + new_text[m.end():]
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print(f"書き込み: {JAVA_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
