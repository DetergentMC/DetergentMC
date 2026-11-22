"""315 の Stage D コミット (15f7589) の diff を 210 用に変換して出す。

番号対応：315（新規）→210、316（前版）→315。pack_format の
PACK_MCMETA_210 と PACK_FORMAT_1_10_2 は 2 に直す。
出力は unified diff。そのまま git apply に渡せる形にするが、
ファイル単位で対象を選べるよう引数でパスの一部を指定できる。

使い方：
  python3 tools/apply-315-stage-d-diff.py ResourcePackMode > /tmp/mode.diff
"""

import subprocess
import sys

P1 = chr(1)  # 316 の退避
P2 = chr(2)  # 1.11.2 の退避
P3 = chr(3)  # V1_11_2 の退避
P4 = chr(4)  # 1_11_2 の退避


def transform(text):
    # hunk ヘッダ（@@ -a,b +c,d @@）の行番号は変換しない
    lines = text.split("\n")
    out = []
    for line in lines:
        if line.startswith("@@"):
            head, sep, tail = line.partition("@@", )
            parts = line.split("@@")
            # parts[0]='', parts[1]=' -a,b +c,d ', parts[2]=見出し
            if len(parts) >= 3:
                line = "@@" + parts[1] + "@@" + transform_body("@@".join(parts[2:]))
                out.append(line)
                continue
        out.append(transform_body(line))
    return "\n".join(out)


def transform_body(text):
    text = text.replace("1.11.2", P2)
    text = text.replace("V1_11_2", P3)
    text = text.replace("1_11_2", P4)
    text = text.replace("316", P1)
    text = text.replace("315", "210")
    text = text.replace(P1, "315")
    text = text.replace("1.11", "1.10.2")
    text = text.replace(P2, "1.11")
    text = text.replace("V1_11", "V1_10_2")
    text = text.replace("1_11", "1_10_2")
    text = text.replace(P4, "1_11")
    # P3 の復元は最後（復元した V1_11 を 1_11→1_10_2 が食わないため）
    text = text.replace(P3, "V1_11")
    # pack_format: 210 は 2
    text = text.replace('PACK_MCMETA_210 = \\"{\\"pack\\":{\\"pack_format\\":3,\\"',
                        'PACK_MCMETA_210 = \\"{\\"pack\\":{\\"pack_format\\":2,\\"')
    text = text.replace("PACK_FORMAT_1_10_2 = 3;", "PACK_FORMAT_1_10_2 = 2;")
    return text


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else ""
    diff = subprocess.run(
        ["git", "show", "15f7589", "--format=", "--"],
        capture_output=True, text=True, check=True,
        cwd="/home/hatch/workspace/detergentmc-210").stdout
    # ファイル単位に分割して対象だけ変換して出す
    chunks = []
    cur = []
    for line in diff.split("\n"):
        if line.startswith("diff --git") and cur:
            chunks.append("\n".join(cur))
            cur = []
        cur.append(line)
    if cur:
        chunks.append("\n".join(cur))
    out = []
    for c in chunks:
        if target and target not in c.split("\n")[0]:
            continue
        out.append(transform(c))
    sys.stdout.write("\n".join(out))


if __name__ == "__main__":
    main()
