#!/usr/bin/env python3
"""316（1.11.2）のレジストリ ground truth を実バニラ jar から抽出する。

extract-340-registries.py の 316 版。1.11.2 には data-generator が
無く、minecraft-data 1.11（Burger 抽出）にも音一覧が無いため、音と
エンティティ登録順はサーバー jar のバイトコードから直接取る。

- 音（493 件）: SoundEvents クラス（難読化名 nn）の <clinit> で
  register("...") に渡される文字列の出現順 = 登録順 = 数値 ID 順。
  先頭の "Accessed Sounds before Bootstrap" はガード用エラー文なので
  除く。340 版との違い：1.11.2 は static final が 493 件しか無いため、
  クラス特定の閾値を 500 から 400 に下げている。
- エンティティ（81 件）: EntityList クラスの登録メソッド
  a(int, String, Class, String) 呼び出しの引数 (id, 名前) の組。
  340（1.12.2）の抽出結果と照合し、共有 ID は id・名前とも完全一致、
  340 のみに illusion_illager（37）と parrot（105）があることを
  確認済み（pig=90 は両方とも 90）。

対象 jar は piston-meta 照合済みの server-1.11.2.jar
（SHA-1 f00c294a1576e03fddcac777c3cf4c7d404c4ba4）。

使い方:
  python3 extract-316-registries.py /path/to/server-1.11.2.jar

出力: sounds-316.json（名前の配列、添字=ID）、entities-316.json（[id, 名前] の配列）。
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
        if text.count("static final") < 400:
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
    tmpdir = Path("/tmp/extract-316")
    tmpdir.mkdir(exist_ok=True)
    sounds = extract_sounds(jar, tmpdir)
    print(f"音: {len(sounds)} 件（先頭 {sounds[0]}）")
    Path("sounds-316.json").write_text(
        json.dumps(sounds, indent=1) + "\n", encoding="utf-8")
    entities = extract_entities(jar, tmpdir)
    print(f"エンティティ: {len(entities)} 件（pig={dict((n, i) for i, n in entities).get('pig')}）")
    Path("entities-316.json").write_text(
        json.dumps(entities, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
