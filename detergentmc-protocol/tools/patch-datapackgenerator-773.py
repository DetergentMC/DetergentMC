"""DatapackGenerator.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。

データパックの pack_format 88 は Minecraft Wiki の pack_format 表で
確認した値（リソースパックの 69 とは別の番号）。772の流儀をなぞる：
PACK_FORMAT_1_21_7（81）の直後に PACK_FORMAT_1_21_9（88）を増やし、
packFormatFor に "1.21.9"／"1.21.10" の分岐を増やす。
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
    ('PACK_FORMAT_1_21_9', '    /** Minecraft 1.21.6 datapack format (Minecraft Wiki: 80 = 1.21.6). */\n    public static final int PACK_FORMAT_1_21_6 = 80;\n\n    /** Minecraft 1.21.7 / 1.21.8 datapack format (Minecraft Wiki: 81 = 1.21.7 / 1.21.8). */\n    public static final int PACK_FORMAT_1_21_7 = 81;',
     '    /** Minecraft 1.21.6 datapack format (Minecraft Wiki: 80 = 1.21.6). */\n    public static final int PACK_FORMAT_1_21_6 = 80;\n\n    /** Minecraft 1.21.7 / 1.21.8 datapack format (Minecraft Wiki: 81 = 1.21.7 / 1.21.8). */\n    public static final int PACK_FORMAT_1_21_7 = 81;\n\n    /** Minecraft 1.21.9 / 1.21.10 datapack format (Minecraft Wiki: 88 = 1.21.9 / 1.21.10). */\n    public static final int PACK_FORMAT_1_21_9 = 88;'),
    ('packFormatFor 773', '    /** Datapack format for a Minecraft version ("1.20.1" -&gt; 15, "1.20.2" -&gt; 18, "1.20.4" -&gt; 26, "1.20.5" / "1.20.6" -&gt; 41, "1.21" / "1.21.1" -&gt; 48, "1.21.2" / "1.21.3" -&gt; 57, "1.21.4" -&gt; 61, "1.21.5" -&gt; 71, "1.21.6" -&gt; 80, "1.21.7" / "1.21.8" -&gt; 81). */\n    public static int packFormatFor(String minecraftVersion) {',
     '    /** Datapack format for a Minecraft version ("1.20.1" -&gt; 15, "1.20.2" -&gt; 18, "1.20.4" -&gt; 26, "1.20.5" / "1.20.6" -&gt; 41, "1.21" / "1.21.1" -&gt; 48, "1.21.2" / "1.21.3" -&gt; 57, "1.21.4" -&gt; 61, "1.21.5" -&gt; 71, "1.21.6" -&gt; 80, "1.21.7" / "1.21.8" -&gt; 81, "1.21.9" / "1.21.10" -&gt; 88). */\n    public static int packFormatFor(String minecraftVersion) {'),
    ('packFormatFor branch 773', '        if ("1.21.6".equals(minecraftVersion)) {\n            return PACK_FORMAT_1_21_6;\n        }\n        if ("1.21.7".equals(minecraftVersion) || "1.21.8".equals(minecraftVersion)) {\n            return PACK_FORMAT_1_21_7;\n        }\n        return PACK_FORMAT_1_20_4;',
     '        if ("1.21.6".equals(minecraftVersion)) {\n            return PACK_FORMAT_1_21_6;\n        }\n        if ("1.21.7".equals(minecraftVersion) || "1.21.8".equals(minecraftVersion)) {\n            return PACK_FORMAT_1_21_7;\n        }\n        if ("1.21.9".equals(minecraftVersion) || "1.21.10".equals(minecraftVersion)) {\n            return PACK_FORMAT_1_21_9;\n        }\n        return PACK_FORMAT_1_20_4;'),
])

print("773 の対応を挿入した")

