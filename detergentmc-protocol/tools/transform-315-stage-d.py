"""315 の Stage D スクリプトから 210 の Stage D スクリプトを作る。

番号の対応：新しい版 315→210、前の版 316→315。335 以降は不変。
pack_format は 315 の mcmeta が 3 のところ、210 は 2 のため、
生成後に PACK_MCMETA_210 の行だけ 2 へ直す（このスクリプト内で
対象文字列を置換する）。
"""

from pathlib import Path

TOOLS = Path(__file__).parent

FILES = [
    "patch-315-stage-d1b.py",
    "patch-315-stage-d2.py",
    "patch-315-stage-d2b.py",
    "patch-315-stage-d2c.py",
    "patch-315-stage-d2d.py",
    "patch-315-stage-d3.py",
]


def transform(text):
    # バージョン文字列・定数名（長い方を先に退避する）
    text = text.replace("1.11.2", "\x01")
    text = text.replace("V1_11_2", "\x02")
    text = text.replace("1_11_2", "\x03")
    text = text.replace("316", "\x04")
    text = text.replace("315", "210")
    text = text.replace("\x04", "315")
    text = text.replace("1.11", "1.10.2")
    text = text.replace("\x01", "1.11")
    text = text.replace("V1_11", "V1_10_2")
    text = text.replace("\x02", "V1_11")
    text = text.replace("1_11", "1_10_2")
    text = text.replace("\x03", "1_11")
    # 210 の pack_format は 2（315 は 3）
    text = text.replace(
        'PACK_MCMETA_210 = \\"{\\\\"pack\\":{\\\\"pack_format\\":3,\\"',
        'PACK_MCMETA_210 = \\"{\\\\"pack\\":{\\\\"pack_format\\":2,\\"')
    return text


for name in FILES:
    src = TOOLS / name
    dst = TOOLS / name.replace("315", "210")
    out = transform(src.read_text(encoding="utf-8"))
    out = out.replace("315 Stage D", "210 Stage D").replace(
        "315 の", "210 の")
    dst.write_text(out, encoding="utf-8")
    print(f"{dst.name} を生成した")
