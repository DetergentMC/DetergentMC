#!/usr/bin/env python3
"""340（1.12.2）のレジストリ ground truth を実バニラ jar から抽出する。

1.12.2 には data-generator が無く、minecraft-data 1.12（Burger 抽出）にも
音一覧が無いため、音とエンティティ登録順はサーバー jar のバイトコードから
直接取る（393 の絵画順で使った「実 jar の定数プール／初期化順」方式の発展）。

- 音（549 件）: SoundEvents クラス（難読化名 qf）の <clinit> で
  register("...") に渡される文字列の出現順 = 登録順 = 数値 ID 順。
  先頭の "Accessed Sounds before Bootstrap" はガード用エラー文なので除く。
- エンティティ（83 件）: EntityList クラス（難読化名 vi）の登録メソッド
  a(int, String, Class, String) 呼び出しの引数 (id, 名前) の組。
  minecraft-data 1.12 の entities.json のうち type=mob の 83 件と
  id・名前とも完全一致することを確認済み（pig=90, creeper=50,
  armor_stand=30, ender_crystal=200 など）。

使い方:
  python3 extract-340-registries.py /path/to/server-1.12.2.jar

出力: sounds-340.json（名前の配列、添字=ID）、entities-340.json（[id, 名前] の配列）。
javap が必要（JDK 17 で確認）。
"""
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path


def class_bytes(jar, suffix_pred):
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith(".class") and suffix_pred(name):
                yield name, z.read(name)


def find_class_with_strings(jar, markers, tmpdir):
    """markers の文字列をすべて含むクラスファイルを探して書き出す。"""
    hits = []
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if not name.endswith(".class"):
                continue
            data = z.read(name)
            if all(m.encode() in data for m in markers):
                p = Path(tmpdir) / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(data)
                hits.append(p)
    return hits


def javap(path):
    out = subprocess.run(["javap", "-p", "-c", str(path)],
                         capture_output=True, text=True, check=True)
    return out.stdout


def extract_sounds(jar, tmpdir):
    hits = find_class_with_strings(jar, ["ambient.cave", "block.stone.break"],
                                    tmpdir)
    for h in hits:
        text = javap(h)
        if text.count("static final") < 500:
            continue
        body = text[text.index("static {"):]
        names = []
        for ln in body.splitlines():
            m = re.search(r"// String ([a-z0-9_.]+)$", ln)
            if m:
                names.append(m.group(1))
        names = [n for n in names if n != "Accessed Sounds before Bootstrap"]
        return names
    raise SystemExit("SoundEvents クラスが見つからない")


def extract_entities(jar, tmpdir):
    hits = find_class_with_strings(jar, ["evocation_illager", "armor_stand"],
                                    tmpdir)
    for h in hits:
        text = javap(h)
        pairs = []
        last_int = None
        last_name = None
        for ln in text.splitlines():
            mp = re.search(r"(?:bipush|sipush)\s+(-?\d+)", ln)
            if mp:
                last_int = int(mp.group(1))
                last_name = None
                continue
            mi = re.search(r"// int (-?\d+)$", ln)
            if mi:
                last_int = int(mi.group(1))
                last_name = None
                continue
            mc = re.search(r"iconst_(\d)", ln)
            if mc:
                last_int = int(mc.group(1))
                last_name = None
                continue
            ms = re.search(r"// String ([A-Za-z0-9_. ]+)$", ln)
            if ms and last_name is None:
                last_name = ms.group(1)
                continue
            if "Method a:(ILjava/lang/String;Ljava/lang/Class;Ljava/lang/String;)V" in ln:
                pairs.append([last_int, last_name])
                last_int = None
                last_name = None
        if len(pairs) >= 80:
            return sorted(pairs)
    raise SystemExit("EntityList クラスが見つからない")


def main():
    jar = sys.argv[1]
    tmpdir = Path("/tmp/extract-340")
    tmpdir.mkdir(exist_ok=True)
    sounds = extract_sounds(jar, tmpdir)
    print(f"音: {len(sounds)} 件（先頭 {sounds[0]}）")
    Path("sounds-340.json").write_text(
        json.dumps(sounds, indent=1) + "\n", encoding="utf-8")
    entities = extract_entities(jar, tmpdir)
    print(f"エンティティ: {len(entities)} 件（pig={dict((n, i) for i, n in entities).get('pig')}）")
    Path("entities-340.json").write_text(
        json.dumps(entities, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
