#!/usr/bin/env python3
"""210（1.10.2）のレジストリ ground truth を実バニラ jar から抽出する。

extract-316-registries.py の 210 版。1.10.2 には data-generator が
無く、minecraft-data の pc/1.10 は blocks.json（213〜217 欠落）と
entities.json（polar_bear 等欠落）が古いため、レジストリはすべて
サーバー jar のバイトコードから直接取る。

- 音: SoundEvents クラスの <clinit> で register に渡される文字列の
  出現順 = 登録順 = 数値 ID 順（316 版と同じ方式）。
- エンティティ: EntityList クラスの登録メソッド
  a(Class, String, int)／a(Class, String, int, int, int) の引数
  (名前, id) の組。1.11 の a(int, String, Class, String) と引数順が
  違い、名前も旧式（"PolarBear" 等の PascalCase）な点が 316 と違う。
- ブロックエンティティ: TileEntity 基底クラスの <clinit> の
  a(Class, String) 呼び出し順 = 登録順。名前は旧式（"Furnace" 等）。
- パーティクル: EnumParticleTypes の <clinit> のコンストラクタ引数
  (名前, id) の組。
- ブロック: Block クラスの登録メソッド a(int, String, Block) の
  引数 (id, 名前) の組。

対象 jar は piston-meta 照合済みの server-1.10.2.jar
（SHA-1 3d501b23df53c548254f5e3f66492d178a48db63）。

使い方:
  python3 extract-210-registries.py /path/to/server-1.10.2.jar

出力: sounds-210.json（名前の配列、添字=ID）、entities-210.json
（[id, 名前] の配列）、block-entities-210.json（名前の配列、
添字=登録順）、particles-210.json（[id, 名前] の配列）、
blocks-210.json（[id, 名前] の配列）。
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


INT_RE = re.compile(r"(?:bipush|sipush)\s+(-?\d+)|// int (-?\d+)$|iconst_(\d)")
STR_RE = re.compile(r"// String ([A-Za-z0-9_. ]+)$")


def ints_of(line):
    m = INT_RE.search(line)
    if not m:
        return None
    for g in m.groups():
        if g is not None:
            return int(g)
    return None


def extract_sounds(jar, tmpdir):
    hits = find_class_with_strings(jar, ["ambient.cave", "block.stone.break"],
                                    tmpdir)
    best = None
    for h in hits:
        text = javap(h)
        body = text[text.index("static {"):]
        names = []
        for ln in body.splitlines():
            m = re.search(r"// String ([a-z0-9_.]+)$", ln)
            if m:
                names.append(m.group(1))
        names = [n for n in names if n != "Accessed Sounds before Bootstrap"]
        if best is None or len(names) > len(best):
            best = names
    if not best or len(best) < 300:
        raise SystemExit("SoundEvents クラスが見つからない")
    return best


def extract_entities(jar, tmpdir):
    hits = find_class_with_strings(jar, ["PolarBear", "ArmorStand"], tmpdir)
    for h in hits:
        text = javap(h)
        pairs = []
        last_str = None
        pending_ints = []
        for ln in text.splitlines():
            ms = STR_RE.search(ln)
            if ms and ("ldc" in ln):
                last_str = ms.group(1)
                pending_ints = []
                continue
            iv = ints_of(ln)
            if iv is not None and last_str is not None:
                pending_ints.append(iv)
                continue
            if ("Method a:(Ljava/lang/Class;Ljava/lang/String;I)V" in ln
                    or "Method a:(Ljava/lang/Class;Ljava/lang/String;III)V" in ln):
                if last_str is not None and pending_ints:
                    pairs.append([pending_ints[0], last_str])
                last_str = None
                pending_ints = []
        if len(pairs) >= 60:
            return sorted(pairs)
    raise SystemExit("EntityList クラスが見つからない")


def extract_block_entities(jar, tmpdir):
    hits = find_class_with_strings(jar, ["Furnace", "MobSpawner", "Control"],
                                    tmpdir)
    for h in hits:
        text = javap(h)
        if "static {" not in text:
            continue
        body = text[text.index("static {"):]
        names = []
        last_str = None
        for ln in body.splitlines():
            ms = STR_RE.search(ln)
            if ms and "ldc" in ln:
                last_str = ms.group(1)
                continue
            if "Method a:(Ljava/lang/Class;Ljava/lang/String;)V" in ln:
                if last_str is not None:
                    names.append(last_str)
                last_str = None
        if len(names) >= 20:
            return names
    raise SystemExit("TileEntity クラスが見つからない")


def extract_particles(jar, tmpdir):
    hits = find_class_with_strings(jar, ["hugeexplosion", "footstep"], tmpdir)
    for h in hits:
        text = javap(h)
        if "static {" not in text:
            continue
        body = text[text.index("static {"):]
        pairs = []
        pending = []  # (文字列 or 数値) の並び
        for ln in body.splitlines():
            ms = re.search(r"// String ([A-Za-z0-9_]+)$", ln)
            if ms and "ldc" in ln:
                pending.append(("s", ms.group(1)))
                continue
            iv = ints_of(ln)
            if iv is not None:
                pending.append(("i", iv))
                continue
            if ('Method "<init>":(Ljava/lang/String;ILjava/lang/String;' in ln
                    and ")V" in ln):
                # コンストラクタは (名前, 通し番号, パーティクル名, id,
                # bool[, 追加int]) 。追加引数を持つ4件（blockcrack 等）
                # も拾うためシグネチャの末尾は見ない。
                strs = [v for k, v in pending if k == "s"]
                nums = [v for k, v in pending if k == "i"]
                if len(strs) >= 2 and len(nums) >= 2:
                    pairs.append([nums[1], strs[1]])
                pending = []
        if len(pairs) >= 40:
            return sorted(pairs)
    raise SystemExit("EnumParticleTypes クラスが見つからない")


def extract_blocks(jar, tmpdir):
    hits = find_class_with_strings(jar, ["nether_wart_block", "frosted_ice"],
                                    tmpdir)
    for h in hits:
        text = javap(h)
        pairs = []
        pending_int = None
        pending_str = None
        for ln in text.splitlines():
            # メソッド境界で状態をリセットする（他メソッドの定数が
            # 漏れると (0, 'tripwire') のような偽ペアができる）。
            if re.match(r"^  \S.*\(.*\);\s*$", ln):
                pending_int = None
                pending_str = None
                continue
            if pending_int is None:
                iv = ints_of(ln)
                if iv is not None:
                    pending_int = iv
                    continue
            if pending_int is not None and pending_str is None:
                ms = STR_RE.search(ln)
                if ms and "ldc" in ln:
                    pending_str = ms.group(1)
                    continue
            # 登録呼び出しは2形式: a(int, String, Block) と
            # a(int, ResourceLocation, Block)（air のみ後者。名前は
            # 直前の setUnlocalizedName の文字列で拾える）。
            if ("Method a:(ILjava/lang/String;Lakf;)V" in ln
                    or "Method a:(ILkn;Lakf;)V" in ln):
                if (pending_int is not None and pending_str is not None
                        and re.fullmatch(r"[a-z][a-z0-9_]*", pending_str)):
                    pairs.append([pending_int, pending_str])
                pending_int = None
                pending_str = None
        if len(pairs) >= 200:
            return sorted(pairs)
    raise SystemExit("Block クラスが見つからない")


def main():
    jar = sys.argv[1]
    tmpdir = Path("/tmp/extract-210")
    tmpdir.mkdir(exist_ok=True)
    out = Path(__file__).parent

    sounds = extract_sounds(jar, tmpdir)
    print(f"音: {len(sounds)} 件（先頭 {sounds[0]}）")
    (out / "sounds-210.json").write_text(
        json.dumps(sounds, indent=1) + "\n", encoding="utf-8")

    entities = extract_entities(jar, tmpdir)
    by_name = {n: i for i, n in entities}
    print(f"エンティティ: {len(entities)} 件（pig={by_name.get('Pig')}）")
    (out / "entities-210.json").write_text(
        json.dumps(entities, indent=1) + "\n", encoding="utf-8")

    bes = extract_block_entities(jar, tmpdir)
    print(f"ブロックエンティティ: {len(bes)} 件: {bes}")
    (out / "block-entities-210.json").write_text(
        json.dumps(bes, indent=1) + "\n", encoding="utf-8")

    particles = extract_particles(jar, tmpdir)
    print(f"パーティクル: {len(particles)} 件（最大ID {particles[-1][0]}）")
    (out / "particles-210.json").write_text(
        json.dumps(particles, indent=1) + "\n", encoding="utf-8")

    blocks = extract_blocks(jar, tmpdir)
    print(f"ブロック: {len(blocks)} 件（最大ID {blocks[-1][0]}）")
    (out / "blocks-210.json").write_text(
        json.dumps(blocks, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
