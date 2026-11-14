#!/usr/bin/env python3
"""DatapackGenerator.java へ Java 1.21.7／1.21.8（プロトコル772）の対応を入れる。

データパックの pack_format 81 は Minecraft Wiki の pack_format 表で
確認した値（リソースパックの 64 とは別の番号）。771の流儀をなぞる：
PACK_FORMAT_1_21_6（80）の直後に PACK_FORMAT_1_21_7（81）を増やし、
packFormatFor に "1.21.7"／"1.21.8" の分岐を増やす。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent.parent / "detergentmc-core" / "src" / "main" / "java" \
    / "io" / "detergentmc" / "core" / "vanilla" / "DatapackGenerator.java"


def edit(path, pairs):
    src = path.read_text(encoding="utf-8")
    for label, old, new in pairs:
        n = src.count(old)
        if n != 1:
            print(f"NG: {label}: anchor が {n} 回見つかった（1回のはず）", file=sys.stderr)
            sys.exit(1)
        src = src.replace(old, new, 1)
        print(f"OK: {label}")
    path.write_text(src, encoding="utf-8")


edit(PATH, [
    ("PACK_FORMAT_1_21_7", """\
    /** Minecraft 1.21.6 datapack format (Minecraft Wiki: 80 = 1.21.6). */
    public static final int PACK_FORMAT_1_21_6 = 80;""",
     """\
    /** Minecraft 1.21.6 datapack format (Minecraft Wiki: 80 = 1.21.6). */
    public static final int PACK_FORMAT_1_21_6 = 80;

    /** Minecraft 1.21.7 / 1.21.8 datapack format (Minecraft Wiki: 81 = 1.21.7 / 1.21.8). */
    public static final int PACK_FORMAT_1_21_7 = 81;"""),
    ("packFormatFor 772", """\
    /** Datapack format for a Minecraft version ("1.20.1" -&gt; 15, "1.20.2" -&gt; 18, "1.20.4" -&gt; 26, "1.20.5" / "1.20.6" -&gt; 41, "1.21" / "1.21.1" -&gt; 48, "1.21.2" / "1.21.3" -&gt; 57, "1.21.4" -&gt; 61, "1.21.5" -&gt; 71, "1.21.6" -&gt; 80). */
    public static int packFormatFor(String minecraftVersion) {""",
     """\
    /** Datapack format for a Minecraft version ("1.20.1" -&gt; 15, "1.20.2" -&gt; 18, "1.20.4" -&gt; 26, "1.20.5" / "1.20.6" -&gt; 41, "1.21" / "1.21.1" -&gt; 48, "1.21.2" / "1.21.3" -&gt; 57, "1.21.4" -&gt; 61, "1.21.5" -&gt; 71, "1.21.6" -&gt; 80, "1.21.7" / "1.21.8" -&gt; 81). */
    public static int packFormatFor(String minecraftVersion) {"""),
    ("packFormatFor branch 772", """\
        if ("1.21.6".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_6;
        }
        return PACK_FORMAT_1_20_4;""",
     """\
        if ("1.21.6".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_6;
        }
        if ("1.21.7".equals(minecraftVersion) || "1.21.8".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_7;
        }
        return PACK_FORMAT_1_20_4;"""),
])

print("DatapackGenerator.java に 772 の対応を挿入した")
