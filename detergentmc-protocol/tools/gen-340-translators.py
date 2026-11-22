#!/usr/bin/env python3
"""ChunkTranslator.java に 340（1.12.2）の節を 393 の節から生成する。

393 と 340 は同じ「旧世代」の骨格（チャンクの旧式 crossing 詰め・
セクション内蔵の光・座標 x26|y12|z26・Spawn Entity の object 分離）を
共有するため、393 の節を写して識別子を付け替える。ただし 340 は
Flattening 前で、以下の点は生成後に手で直してある（本スクリプトの
機械置換で対応するものは置換に含めた）:

- チャンクのバイオームが u8×256（393 は i32×256）→ 機械置換済み。
- 直接パレットが 13 ビット（(数値ID<<4)|メタデータは 12 ビット幅、
  バニラは 13 で書く。393 は 14 ビット）→ 機械置換済み。
- World Particles の粒子 ID が i32（393 は varint）→ 機械置換済み。
- Spawn Entity の object 表: trident（758 のみ）→ 矢で代用 → 機械置換済み。
- スロット: itemDamage: i16 があり、758 で分かれたアイテム
  （魚・旗・ベッド・頭・染料）は (基本アイテム, damage) に畳む
  → translateSlot340 を手で書き換え。
- Spawn Painting: 340 はタイトル文字列 → 手で書き換え。
- エンティティメタデータ: 型フィールドが i8、型番号も違う
  （optblockid=14・nbt=15・particle=16）→ 手で書き換え。
- パーティクルデータの ID（340 番号: reddust=30, iconcrack=36,
  blockcrack=37, blockdust=38, fallingdust=46）→ 手で書き換え。

使い方：
  python3 tools/gen-340-translators.py
  冪等（340 の節があれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ChunkTranslator.java"
MARKER = "// 393（1.13）対応"
DONE_MARKER = "// 340（1.12.2）対応"

HEADER_340 = """    // ------------------------------------------------------------------
    // 340（1.12.2）対応
    //
    // 骨格は 393 と同じ旧世代（チャンクの旧式 crossing 詰め・
    // セクション内蔵の光・座標 x26|y12|z26・Spawn Entity の
    // object 分離）なので、この節は 393 の節を写して
    // RegistryRemapper の 340 表に差し替えたもの。340 固有の差分：
    //
    // ・ブロック状態は状態 ID ではなく (数値ID<<4)|メタデータ。
    //   チャンクの直接パレットは 13 ビット（393 は 14）。
    // ・チャンク末尾のバイオームは u8×256（393 は i32×256）。
    // ・スロットに itemDamage: i16 があり、758 で色・種別ごとに
    //   分かれたアイテムは (基本アイテム, damage) で表す
    //   （translateSlot340 のコメント参照）。
    // ・Spawn Painting はモチーフ番号ではなくタイトル文字列。
    // ・エンティティメタデータの型は i8 で、型番号も違う
    //   （optblockid=14、nbt=15、particle=16。758 の villagerdata/
    //   optvarint/pose は落とす）。Burger の protocol.json の
    //   メタデータ表は旧版の使い回しで誤りのため使っていない
    //   （Stage E で実ワイヤ確認予定）。
    // ・World Particles の粒子 ID は i32（393 は varint）。
    // ・Open Window のタイトルは平文文字列（JSON ではない）。
    // ・Trade List／Entity Sound Effect／レシピ系は 340 に無い
    //   （呼び出し側で落とす）。
    // ------------------------------------------------------------------
"""


def main():
    text = P.read_text(encoding="utf-8")
    if DONE_MARKER in text:
        print("skip（既存）: 340 の節はすでにある")
        return 0
    lines = text.split("\n")
    marker_idx = next(i for i, ln in enumerate(lines) if MARKER in ln)
    start = marker_idx - 1  # 直前の // ---- 行から
    assert lines[start].strip().startswith("// ---"), lines[start]
    # 393 の節はファイル末尾（クラスを閉じる "}" の直前）まで。
    assert lines[-1] == "}" or (lines[-1] == "" and lines[-2] == "}"), lines[-3:]
    end = len(lines) - 1 if lines[-1] == "}" else len(lines) - 2
    section = "\n".join(lines[start:end])

    # ヘッダー差し替え（393 のヘッダーブロックは最初の空行まで）。
    head_end = section.index("\n\n")
    section = HEADER_340.rstrip("\n") + section[head_end:]

    # 付け替え。
    section = section.replace("393", "340")
    section = section.replace("1.13", "1.12.2")

    # 機械置換（340 固有の形式差分）。
    def rep(old, new, cnt=1):
        nonlocal section
        assert section.count(old) >= cnt, f"置換対象が足りない: {old[:60]}"
        section = section.replace(old, new, cnt)

    # バイオーム u8（340→758 の読み）。
    rep("biomes[i] = buffer.readInt();",
        "biomes[i] = buffer.readUnsignedByte(); // 340 のバイオームは u8")
    # バイオーム u8（758→340 の書き）。
    rep("dataOut.writeInt(RegistryRemapper.mapBiome758To340(biomes758[4][src]));",
        "dataOut.writeByte(RegistryRemapper.mapBiome758To340(biomes758[4][src]));")
    # 直接パレット 13 ビット。
    rep("writer.writeUnsignedByte(14); // 直接パレット（340 のブロック状態は 14 ビット）",
        "writer.writeUnsignedByte(13); // 直接パレット（340 の値は (数値ID<<4)|メタデータ＝13 ビット）")
    rep("packCrossing(states340, 14)", "packCrossing(states340, 13)")
    # World Particles の粒子 ID は i32（340 側）。
    rep("int id = reader.readVarInt();\n"
        "            writer.writeVarInt(RegistryRemapper.mapParticle340To758(id));",
        "int id = reader.readInt(); // 340 の粒子 ID は i32\n"
        "            writer.writeVarInt(RegistryRemapper.mapParticle340To758(id));")
    rep("int id = reader.readVarInt();\n"
        "            writer.writeVarInt(RegistryRemapper.mapParticle758To340(id));",
        "int id = reader.readVarInt();\n"
        "            writer.writeInt(RegistryRemapper.mapParticle758To340(id)); // 340 の粒子 ID は i32")
    # trident は 340 に無い（矢で代用）。逆向きの 94 は落とす。
    rep("case 93 -> 94; // trident",
        "case 93 -> 60; // trident（340 に無いため矢で代用）")
    rep("            case 94 -> 93; // trident\n", "")

    assert DONE_MARKER in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    P.write_text(new_text, encoding="utf-8")
    print(f"ChunkTranslator.java に 340 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
