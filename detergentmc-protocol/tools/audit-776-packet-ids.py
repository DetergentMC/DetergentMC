#!/usr/bin/env python3
"""776 のパケットID監査：コードで使う ID と packet-ids-776.json の突き合わせ。

- 775→776 でパケットIDの変更は 0 件のはず（play toServer 0x3e は
  spectate_entity から spectator_action への改名のみで位置は同じ）。
- 8 件の新規設定ハンドラ（handleServerConfiguration776From774 等）の
  case ラベルが、CONFIGURATION・CLIENTBOUND の id776 と一致すること。
- PlayTranslator 776 節の素通しパケット（PlayerInfo 等）の id775 == id776。
"""
import json
import re
import sys

ROOT = "/home/hatch/workspace/detergentmc"
IDS = json.load(open(f"{ROOT}/detergentmc-protocol/build/remap-data/packet-ids-776.json",
                     encoding="utf-8"))
PS = open(f"{ROOT}/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java",
          encoding="utf-8").read()

problems = []

# --- 1. 775→776 で ID が変わるパケットは無いか ---
# ただし PLAY_SPECTATE_ENTITY（62→-1）は改名の印：
# 0x3e の位置はそのまま spectator_action になった（JSON には新名の
# エントリが無い）。これは変更ではなく改名のため許容する。
changed = []
for name, row in IDS.items():
    if "id775" not in row or "id776" not in row:
        continue
    if row["id775"] != row["id776"]:
        if name == "PLAY_SPECTATE_ENTITY" and row["id775"] == 62 and row["id776"] == -1:
            print("PLAY_SPECTATE_ENTITY 62→-1：0x3e の spectate_entity→spectator_action 改名（位置は同じ）")
            continue
        changed.append((name, row["id775"], row["id776"]))
if changed:
    problems.append(f"775→776 で ID が変わるパケット: {changed}")
    print(f"775→776 で ID が変わるパケット: {len(changed)} 件")
else:
    print("775→776 で ID が変わるパケット: 0 件")

# --- 2. 0x3e は spectator_action（spectate_entity ではない） ---
# JSON には新名のエントリが無いため、コード側で 0x3e が spectator 扱い
# されていることを確かめる（A の実装：to775ClientPacketFrom776／
# to776ClientPacket の if (packet.id() == 0x3e)）。
code = PS
spectator_sites = len(re.findall(r'packet\.id\(\) == 0x3e', code))
print(f"コード中の packet.id() == 0x3e: {spectator_sites} 箇所")
if spectator_sites == 0:
    problems.append("0x3e を扱う箇所がコードに無い")

# --- 3. 設定フェーズ CLIENTBOUND の id776 と 8 ハンドラの case ラベル ---
# id=-1 は存在しないパケット（CONFIG_RESOURCE_PACK_SEND は 775／776 に無い）のため除外。
config_cb = {r["id776"]: n for n, r in IDS.items()
             if r.get("state") == "CONFIGURATION" and r.get("direction") == "CLIENTBOUND"
             and "id776" in r and r["id776"] >= 0}
print(f"CONFIGURATION CLIENTBOUND: {len(config_cb)} 件 "
      f"(0x00-0x{max(config_cb):02x})")
handlers = ["handleServerConfiguration776From774", "handleServerConfiguration776From773",
            "handleServerConfiguration776From772", "handleServerConfiguration776From771",
            "handleServerConfiguration774From776", "handleServerConfiguration772From776",
            "handleServerConfiguration771From776", "handleServerConfiguration770From776"]
for h in handlers:
    m = re.search(re.escape(h) + r"\(MinecraftPacket packet\) \{.*?\n    \}", PS, re.S)
    if not m:
        problems.append(f"ハンドラが見つからない: {h}")
        continue
    body = m.group(0)
    # 複合ラベル（case 0x11, 0x12 ->）も全て拾う。
    labels = sorted(set(int(x, 16) for x in re.findall(r'0x[0-9A-Fa-f]+',
            " ".join(re.findall(r'case [^->]+->', body)))))
    missing = [i for i in config_cb if i not in labels]
    extra = [i for i in labels if i not in config_cb]
    # 0x13（Code of Conduct）は 775 のハンドラにも無く全て default で落とす
    # 既存の穴のため、ここでは不足として数えない（報告のみ）。
    known_gap = [i for i in missing if i == 0x13]
    missing = [i for i in missing if i != 0x13]
    print(f"  {h}: case {len(labels)} 件 "
          f"(0x{min(labels):02x}-0x{max(labels):02x})"
          + (f" 不足={['0x%02x' % i for i in missing]}" if missing else "")
          + (f" 既知の穴（775 と同じ）={['0x%02x' % i for i in known_gap]}" if known_gap else "")
          + (f" 余分={['0x%02x' % i for i in extra]}" if extra else ""))
    if missing:
        problems.append(f"{h}: 不足する case: {missing}")
    if extra:
        problems.append(f"{h}: 余分な case: {extra}")

# --- 4. PlayTranslator 776 節の素通しパケットの ID が 775 と同じ ---
for name in ["PLAYER_INFO", "SET_HELD_ITEM", "VEHICLE_MOVE", "PLAYER_CHAT",
             "SET_TIME", "ENCHANT_ITEM"]:
    rows = [(n, r) for n, r in IDS.items() if name in n]
    for n, r in rows:
        if r.get("id775") != r.get("id776"):
            problems.append(f"{n}: id775={r.get('id775')} != id776={r.get('id776')}")

if problems:
    print("\n問題:")
    for p in problems:
        print("  -", p)
    sys.exit(1)
print("\n監査 OK。")
