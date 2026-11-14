#!/usr/bin/env python3
"""detergentmc-adapter-v1_21_5 を detergentmc-adapter-v1_21_6 へ複写し、771へずらす。

V1215Adapter → V1216Adapter、1.21.5 → 1.21.6、770 → 771、
SHA-1 → 1.21.6 のもの。javadoc の「1.21.5で追加されたブロック」の
説明も 1.21.6（乾燥ガスト）へ更新する。
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SRC = ROOT / "detergentmc-adapter-v1_21_5" / "src"
DST = ROOT / "detergentmc-adapter-v1_21_6" / "src"
BUILD_SRC = ROOT / "detergentmc-adapter-v1_21_5" / "build.gradle"
BUILD_DST = ROOT / "detergentmc-adapter-v1_21_6" / "build.gradle"

if DST.exists():
    print(f"{DST} が既にある。何もしない", file=sys.stderr)
    sys.exit(1)

shutil.copytree(SRC, DST)
shutil.copyfile(BUILD_SRC, BUILD_DST)

# ファイル名の変更
(DST / "main" / "java" / "io" / "detergentmc" / "adapter" / "v1_21_5").rename(
    DST / "main" / "java" / "io" / "detergentmc" / "adapter" / "v1_21_6")
(DST / "test" / "java" / "io" / "detergentmc" / "adapter" / "v1_21_5").rename(
    DST / "test" / "java" / "io" / "detergentmc" / "adapter" / "v1_21_6")
main_java = DST / "main" / "java" / "io" / "detergentmc" / "adapter" / "v1_21_6"
test_java = DST / "test" / "java" / "io" / "detergentmc" / "adapter" / "v1_21_6"
(main_java / "V1215Adapter.java").rename(main_java / "V1216Adapter.java")
(test_java / "V1215AdapterTest.java").rename(test_java / "V1216AdapterTest.java")

files = [
    main_java / "V1216Adapter.java",
    test_java / "V1216AdapterTest.java",
    DST / "main" / "resources" / "META-INF" / "services" / "io.detergentmc.api.VersionAdapter",
    BUILD_DST,
]
failed = []


def rep(path, old, new, label):
    src = path.read_text(encoding="utf-8")
    n = src.count(old)
    if n < 1:
        failed.append(f"{path.name} {label}: 一致 0 件")
        return
    path.write_text(src.replace(old, new), encoding="utf-8")


def rep_opt(path, old, new, label):
    """0件でもよい置換（全ファイル共通のずれで、ファイルによっては無いもの）。"""
    src = path.read_text(encoding="utf-8")
    n = src.count(old)
    if n < 1:
        return
    path.write_text(src.replace(old, new), encoding="utf-8")


# 機械的なずれ（全ファイル共通。無いものは飛ばす）
for path in files:
    rep_opt(path, "v1_21_5", "v1_21_6", "package")
    rep_opt(path, "V1215Adapter", "V1216Adapter", "class")
    rep_opt(path, "1.21.5", "1.21.6", "version")
    rep_opt(path, "1215", "1216", "1215")
    rep_opt(path, "770", "771", "protocol")
    rep_opt(path, "e6ec2f64e6080b9b5d9b471b291c33cc7f509733",
            "6e64dcabba3c01a7271b4fa6bd898483b794c59b", "sha1")

# V1216Adapter.java の javadoc：1.21.6 で追加されたブロック
adapter = main_java / "V1216Adapter.java"
rep(adapter,
    " * <p>Which blocks exist in 1.21.6: every {@link BlockType} whose\n"
    " * {@code since} version is 1.21.6 or older. 1.21.6 added blocks over\n"
    " * 1.21.4 (the bush, short and tall dry grass, the cactus flower,\n"
    " * wildflowers, leaf litter, the firefly bush, the test block and the\n"
    " * test instance block - the Spring to Life content), but the unified\n"
    " * {@link BlockType} enum does not list them yet, so every unified\n"
    " * block known today is present here, including the 1.20 additions\n"
    " * (cherry wood, bamboo blocks, suspicious sand, calibrated sculk\n"
    " * sensor), the 1.20.3 additions (crafter, trial spawner), the 1.21.2\n"
    " * additions (the pale oak woodset, the creaking heart, pale moss)\n"
    " * and the 1.21.4 additions (resin blocks, the eyeblossom). Blocks\n"
    " * added in 1.21.6 or later do not exist here until they are added to\n"
    " * {@link BlockType}, and {@link #toMinecraftId(BlockType)} answers\n"
    " * {@link Optional#empty()} for them. The check is done against\n"
    " * {@code BlockType.since()}, so a future unified block introduced\n"
    " * after 1.21.6 is automatically reported as unsupported instead of\n"
    " * silently mapping to a wrong id.",
    " * <p>Which blocks exist in 1.21.6: every {@link BlockType} whose\n"
    " * {@code since} version is 1.21.6 or older. 1.21.6 added blocks over\n"
    " * 1.21.5 (the dried ghast - the Chase the Skies content), but the\n"
    " * unified {@link BlockType} enum does not list it yet, so every\n"
    " * unified block known today is present here, including the 1.20\n"
    " * additions (cherry wood, bamboo blocks, suspicious sand, calibrated\n"
    " * sculk sensor), the 1.20.3 additions (crafter, trial spawner), the\n"
    " * 1.21.2 additions (the pale oak woodset, the creaking heart, pale\n"
    " * moss), the 1.21.4 additions (resin blocks, the eyeblossom) and the\n"
    " * 1.21.5 additions (the bush, short and tall dry grass, the cactus\n"
    " * flower, wildflowers, leaf litter, the firefly bush, the test block\n"
    " * and the test instance block). Blocks added in 1.21.6 or later do\n"
    " * not exist here until they are added to {@link BlockType}, and\n"
    " * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}\n"
    " * for them. The check is done against {@code BlockType.since()}, so\n"
    " * a future unified block introduced after 1.21.6 is automatically\n"
    " * reported as unsupported instead of silently mapping to a wrong id.",
    "javadoc blocks")
rep(adapter,
    " * <p>1.21.6 alone has protocol 771, so this module has only this one\n"
    " * adapter class (unlike 1.21.2 and 1.21.3, which share protocol 768\n"
    " * in the v1_21_2 module).",
    " * <p>1.21.6 alone has protocol 771, so this module has only this one\n"
    " * adapter class (unlike 1.21.2 and 1.21.3, which share protocol 768\n"
    " * in the v1_21_2 module).",
    "javadoc protocol (no-op check)")
rep(adapter,
    " * and no NMS calls - the real binding via the official Mojang mappings\n"
    " * is TODO, the same as for the 1.20.4, 1.20.1, 1.20.2, 1.20.5, 1.21,\n"
    " * 1.21.2 and 1.21.4 adapters (see docs/VERSIONS.md). The vanilla 1.21.6 server jar (SHA-1",
    " * and no NMS calls - the real binding via the official Mojang mappings\n"
    " * is TODO, the same as for the 1.20.4, 1.20.1, 1.20.2, 1.20.5, 1.21,\n"
    " * 1.21.2, 1.21.4 and 1.21.5 adapters (see docs/VERSIONS.md). The vanilla 1.21.6 server jar (SHA-1",
    "javadoc adapters list")

# V1216AdapterTest.java のコメント
test = test_java / "V1216AdapterTest.java"
rep(test,
    "        // Every unified block known today was introduced in 1.21.4 or\n"
    "        // earlier, and the 1.21.6 additions (bush, dry grass, cactus\n"
    "        // flower, wildflowers, leaf litter, firefly bush, test block)\n"
    "        // are not in the unified BlockType enum yet, so all of them\n"
    "        // exist in 1.21.6 - including the 1.20 additions (cherry wood,\n"
    "        // bamboo, suspicious sand, calibrated sculk sensor), the 1.20.3\n"
    "        // additions (crafter, trial spawner), the 1.21.2 additions\n"
    "        // (pale oak woodset, creaking heart, pale moss) and the 1.21.4\n"
    "        // additions (resin blocks, eyeblossom).",
    "        // Every unified block known today was introduced in 1.21.5 or\n"
    "        // earlier, and the 1.21.6 addition (dried ghast) is not in the\n"
    "        // unified BlockType enum yet, so all of them exist in 1.21.6 -\n"
    "        // including the 1.20 additions (cherry wood, bamboo, suspicious\n"
    "        // sand, calibrated sculk sensor), the 1.20.3 additions (crafter,\n"
    "        // trial spawner), the 1.21.2 additions (pale oak woodset,\n"
    "        // creaking heart, pale moss), the 1.21.4 additions (resin\n"
    "        // blocks, eyeblossom) and the 1.21.5 additions (bush, dry\n"
    "        // grass, cactus flower, wildflowers, leaf litter, firefly\n"
    "        // bush, test block, test instance block).",
    "test comment blocks")
rep(test,
    'assertTrue(V1216Adapter.compareVersions("1.21.6", "1.21.6") > 0);',
    'assertTrue(V1216Adapter.compareVersions("1.21.7", "1.21.6") > 0);',
    "test compare future")
rep(test,
    '        assertTrue(V1216Adapter.availableIn1216(BlockType.CHERRY_LOG),\n'
    '                "a 1.20 block exists in 1.21.6");',
    '        assertTrue(V1216Adapter.availableIn1216(BlockType.CHERRY_LOG),\n'
    '                "a 1.20 block exists in 1.21.6");',
    "test available (no-op check)")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("detergentmc-adapter-v1_21_6 を作成した")
for p in sorted(DST.rglob("*")):
    if p.is_file():
        print(" ", p.relative_to(ROOT))
print(" ", BUILD_DST.relative_to(ROOT))
