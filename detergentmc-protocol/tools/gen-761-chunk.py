#!/usr/bin/env python3
"""ChunkTranslator の 763<->762 節を元に 762<->761 節を生成する。

762<->761 は 763<->762 と違い、パケットIDが違う（呼び出し側で付け替える）一方で
ペイロードの構造差はほとんど無い（trustEdges／suppressLightUpdates／
isFrontText は両方に同じ形である）。そのため機械的な置換で大半が作れるが、
以下の点は生成後に手で直す（スクリプトは警告を出すだけ）：
  - Chunk Data：trustEdges を読んで書き戻す（763->762 は挿入だった）
  - Multi Block Change：suppressLightUpdates を読んで書き戻す
  - Update Light／Damage Event／Open Sign／Update Sign：761 には無いか
    形式が同一のため、生成されたメソッドは削除する
  - Particle：データを持つ粒子の番号（sculk_charge／item／vibration／shriek）
  - Spawn Entity：falling_block の種別IDは発生元（762=36／761=30）で判定する

使い方：python3 tools/gen-761-chunk.py
  ChunkTranslator.java の末尾（クラスの閉じ括弧の直前）に 762<->761 節を挿入する。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHUNK = ROOT / "src/main/java/io/detergentmc/protocol/ChunkTranslator.java"
PACKET_IDS = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"

# メソッド基底名 -> PacketName（javadoc のパケットIDを付け直すため）
METHOD_TO_PACKET = {
    "ChunkData": "PLAY_CHUNK_DATA",
    "BlockChange": "PLAY_BLOCK_CHANGE",
    "MultiBlockChange": "PLAY_MULTI_BLOCK_CHANGE",
    "BlockEntityData": "PLAY_BLOCK_ENTITY_DATA",
    "UpdateLight": "PLAY_UPDATE_LIGHT",
    "SetSlot": "PLAY_SET_SLOT",
    "WindowItems": "PLAY_WINDOW_ITEMS",
    "SetCooldown": "PLAY_SET_COOLDOWN",
    "EntityEquipment": "PLAY_ENTITY_EQUIPMENT",
    "TradeList": "PLAY_TRADE_LIST",
    "OpenWindow": "PLAY_OPEN_WINDOW",
    "BlockAction": "PLAY_BLOCK_ACTION",
    "SpawnEntity": "PLAY_SPAWN_ENTITY",
    "EntityMetadata": "PLAY_ENTITY_METADATA",
    "SoundEffect": "PLAY_SOUND_EFFECT",
    "EntitySoundEffect": "PLAY_ENTITY_SOUND_EFFECT",
    "StopSound": "PLAY_STOP_SOUND",
    "WorldParticles": "PLAY_WORLD_PARTICLES",
    "EntityEffect": "PLAY_ENTITY_EFFECT",
    "DamageEvent": "PLAY_DAMAGE_EVENT",
    "OpenSign": "PLAY_OPEN_SIGN_EDITOR",
    "WindowClick": "PLAY_WINDOW_CLICK",
    "SetCreativeSlot": "PLAY_SET_CREATIVE_SLOT",
    "UpdateSign": "PLAY_UPDATE_SIGN",
}


def load_packet_ids():
    """PacketIds.java の enum 項目から {PacketName: (id761, id762)} を作る。"""
    text = PACKET_IDS.read_text(encoding="utf-8")
    ids = {}
    for m in re.finditer(
        r"^\s+(\w+)\(State\.\w+, Direction\.\w+, ((?:-?\d+|0x[0-9A-Fa-f]+)"
        r"(?:, (?:-?\d+|0x[0-9A-Fa-f]+))*)\),",
        text, re.MULTILINE,
    ):
        nums = [int(x, 0) for x in m.group(2).split(", ")]
        if len(nums) >= 2:
            ids[m.group(1)] = (nums[0], nums[1])
    return ids


def main():
    lines = CHUNK.read_text(encoding="utf-8").splitlines(keepends=True)
    # 762 節：行 32779（1始まり）のヘッダーから translateUpdateSign763To762 の
    # 末尾（行 34335）まで。0 始まりのスライスは [32778:34335]。
    start = next(i for i, l in enumerate(lines) if "763 ⇔ 762" in l) - 1
    end = next(
        i for i, l in enumerate(lines)
        if "translateUpdateSign763To762" in l and "byte[]" in l
    )
    # メソッドの閉じ括弧まで進める
    depth = 0
    j = end
    while True:
        depth += lines[j].count("{") - lines[j].count("}")
        if depth == 0 and "}" in lines[j]:
            break
        j += 1
    section = lines[start:j + 1]
    assert "763 ⇔ 762" in section[1], section[:3]
    assert any("translateUpdateSign763To762" in l for l in section)

    packet_ids = load_packet_ids()
    for name in METHOD_TO_PACKET.values():
        assert name in packet_ids, f"PacketIds に {name} が無い"

    text = "".join(section)

    # --- 機械置換（プレースホルダで二重置換を防ぐ） ---
    text = text.replace("762To763", "\x00A")
    text = text.replace("763To762", "\x00B")
    text = text.replace("\x00A", "761To762")
    text = text.replace("\x00B", "762To761")
    text = text.replace("763⇔762", "762⇔761")
    for old, new in [
        ("translateChunkSections762(", "translateChunkSections761("),
        ("translatePalettedContainer762(", "translatePalettedContainer761("),
        ("remapDirectEntries762(", "remapDirectEntries761("),
        ("translateEntityMetadata762(", "translateEntityMetadata761("),
        ("translateMetadataValue762(", "translateMetadataValue761("),
        ("translateParticle762(", "translateParticle761("),
    ]:
        text = text.replace(old, new)
    text = re.sub(r"\bto762\b", "to761", text)

    # falling_block 定数：762=36 の定義を 761=30 の定義に置き換える。
    text = text.replace(
        "    /** 762／763 の falling_block のエンティティ種別ID（両方 36）。 */\n"
        "    static final int FALLING_BLOCK_762 = 36;\n",
        "    /** 761 の falling_block のエンティティ種別ID（30）。762 は 36。 */\n"
        "    static final int FALLING_BLOCK_761 = 30;\n",
    )
    assert "FALLING_BLOCK_761 = 30" in text

    # --- javadoc のパケットIDを付け直す ---
    out_lines = text.splitlines(keepends=True)
    fixed = 0
    for i, line in enumerate(out_lines):
        m = re.search(r"(763|762) 0x([0-9A-Fa-f]+) → (762|763) 0x([0-9A-Fa-f]+)", line)
        if not m:
            continue
        # 直後のメソッド定義からパケット名を特定する
        pkt = None
        direction = None
        for k in range(i + 1, min(i + 12, len(out_lines))):
            mm = re.search(r"translate(\w+?)(762To761|761To762)\(", out_lines[k])
            if mm:
                pkt = METHOD_TO_PACKET.get(mm.group(1))
                direction = mm.group(2)
                break
        if pkt is None:
            print(f"警告: パケット名を特定できない javadoc（行 {i}）: {line.strip()}", file=sys.stderr)
            continue
        id761, id762 = packet_ids[pkt]
        if direction == "762To761":
            new = f"762 0x{id762:02X} → 761 0x{id761:02X}"
        else:
            new = f"761 0x{id761:02X} → 762 0x{id762:02X}"
        out_lines[i] = line[:m.start()] + new + line[m.end():]
        fixed += 1
    # 方向だけ書かれた javadoc（Window Click など）
    for i, line in enumerate(out_lines):
        if "（762 → 763）" in line:
            out_lines[i] = line.replace("（762 → 763）", "（761 → 762）")
        elif "（763 → 762）" in line:
            out_lines[i] = line.replace("（763 → 762）", "（762 → 761）")
    text = "".join(out_lines)

    # --- 節ヘッダーを書き換える ---
    old_header = (
        "    // 763 ⇔ 762（1.20／1.20.1 ⇔ 1.19.4）\n"
        "    // パケットIDは同一、ペイロードの差は Chunk Data／Update Light の\n"
        "    // trustEdges、Multi Block Change の suppressLightUpdates、\n"
        "    // Open Sign／Update Sign の isFrontText のみ。レジストリは\n"
        "    // RegistryRemapper の 762⇔761 の表で直接付け替える。\n"
        "    // NBT は 762・763 ともに名前付きのため、スロットやブロックエンティティの\n"
        "    // NBT はそのまま写す。エンティティメタデータの variant 系は 762・763\n"
        "    // で形式が同一のため落とさない（765→763 では落としている）。\n"
        "    // チャットは 762・763 とも署名付きで同一形式のため変換しない。\n"
    )
    new_header = (
        "    // 762 ⇔ 761（1.19.4 ⇔ 1.19.3）\n"
        "    // パケットIDが違うため、呼び出し側（ProxySession）が ID を付け替える。\n"
        "    // ペイロードの構造差は無い。Chunk Data／Update Light の trustEdges、\n"
        "    // Multi Block Change の suppressLightUpdates は 761・762 ともに同じ\n"
        "    // 位置にある。Open Sign／Update Sign の isFrontText は 761・762 ともに\n"
        "    // 無く、763 にだけある。レジストリは RegistryRemapper の 762⇔761 の表で\n"
        "    // 直接付け替える。NBT は 761・762 ともに名前付きのため、スロットや\n"
        "    // ブロックエンティティの NBT はそのまま写す。エンティティメタデータの\n"
        "    // variant 系は 761・762 で形式が同一のため落とさない（765→763 では\n"
        "    // 落としている）。チャットは 761・762 とも署名付きで同一形式のため変換しない。\n"
        "    // 762 にしか無い Damage Event／Bundle Delimiter／Chunk Biomes／\n"
        "    // Hurt Animation は 761 には送らない（呼び出し側で落とす）。\n"
        "    // 762 の Spawn Entity（player）は 761 では Named Entity Spawn（0x02）へ\n"
        "    // 変換する（呼び出し側で行う）。\n"
    )
    assert old_header in text, "節ヘッダーが想定と違う"
    text = text.replace(old_header, new_header)

    # 末尾に挿入する（クラスの閉じ括弧の直前）
    full = CHUNK.read_text(encoding="utf-8")
    assert full.rstrip().endswith("}\n") or full.rstrip().endswith("}")
    idx = full.rfind("\n}\n")
    assert idx > 0
    new_full = full[:idx] + "\n" + text + full[idx:]
    CHUNK.write_text(new_full, encoding="utf-8")
    print(f"762<->761 節を挿入した（{len(text.splitlines())} 行、javadoc ID 修正 {fixed} 件）")
    print("手直しが必要な箇所：")
    print("  - Chunk Data：trustEdges を読んで書き戻す（両方向）")
    print("  - Multi Block Change：suppressLightUpdates を読んで書き戻す（両方向）")
    print("  - 削除：Update Light／Damage Event／Open Sign／Update Sign（両方向）")
    print("  - Particle：データを持つ粒子の番号（761: sculk_charge=30,item=39,")
    print("    vibration=40,shriek=92／762: sculk_charge=33,item=42,vibration=43,shriek=95）")
    print("  - Spawn Entity：falling_block の判定は発生元（762=36／761=30）で行う")


if __name__ == "__main__":
    main()
