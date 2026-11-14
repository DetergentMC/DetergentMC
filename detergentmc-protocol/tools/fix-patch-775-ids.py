#!/usr/bin/env python3
"""patch-proxysession-775.py へ監査で見つけたパケットID付け替えを入れる。

根拠は /tmp/mc775/data の mc-26.1-protocol.json / mc-1.21.11-protocol.json
および packet-ids-775.json。子エージェントの報告の A・B に加え、監査で
見つけた2件（Set Creative Slot 0x37→0x38、Respawn の中間分岐落ち、
Disconnect の is770 の誤グループ化）も直す。

- A: handleServerPlayFrom775 のチェックID（772時代のまま）
- B: handleClientPlayTo775 の構築ID（774時代のまま）
- Respawn: チェック 0x52＋is774→0x50＋is773→0x50＋is770/771/772→0x4B＋is768/769→0x4C
  （生成物は is774 分岐しかなく、しかも 0x4B を送っていた）
- Disconnect: is773/774/775→0x20、is770/771/772→0x1C、is766〜769→0x1D
  （生成物は is771〜775→0x1C、is766〜770→0x1D と誤グループ化）
"""
import sys

PATH = "/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-775.py"

src = open(PATH, encoding="utf-8").read()

REPLACEMENTS = [
    # ---- A: handleServerPlayFrom775 のチェックID ----
    ("A1 Start Configuration",
     "// Start Configuration（775：0x6F）。763には設定フェーズもこのパケットも無い。\n        if (packet.id() == 0x6F) {",
     "// Start Configuration（775：0x76）。763には設定フェーズもこのパケットも無い。\n        if (packet.id() == 0x76) {"),
    ("A2 Join Game",
     "// Join Game（775：0x2B）。\n        if (packet.id() == 0x2B) {",
     "// Join Game（775：0x31）。\n        if (packet.id() == 0x31) {"),
    ("A3 Spawn Position",
     "// Spawn Position（775：0x5A）。\n        if (packet.id() == 0x5A) {",
     "// Spawn Position（775：0x61）。\n        if (packet.id() == 0x61) {"),
    ("A4 Keep Alive/Ping",
     "// Keep Alive（775：0x26）／Ping（775：0x36）は接続維持のため中継する。\n        if (packet.id() == 0x26) {",
     "// Keep Alive（775：0x2C）／Ping（775：0x3D）は接続維持のため中継する。\n        if (packet.id() == 0x2C) {"),
    ("A5 Ping check",
     "        if (packet.id() == 0x36) {\n            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,",
     "        if (packet.id() == 0x3D) {\n            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,"),
    ("A6 Disconnect check",
     "// Disconnect（775：0x1C、NBT）。\n        if (packet.id() == 0x1C) {",
     "// Disconnect（775：0x20、NBT）。\n        if (packet.id() == 0x20) {"),
    ("A7 Disconnect groups",
     """            if (is771() || is772() || is773() || is774() || is775()) {
                // 771／772／773／774／775クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }
            if (is766() || is767() || is768() || is769() || is770()) {
                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }""",
     """            if (is773() || is774() || is775()) {
                // 773／774／775クライアント：Disconnect は 0x20（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);
            }
            if (is770() || is771() || is772()) {
                // 770／771／772クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }
            if (is766() || is767() || is768() || is769()) {
                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }"""),
    ("A8 Respawn rewrite",
     """        // Respawn（775：0x4B。774（0x4B）と同形式のためそのまま送る）。
        if (packet.id() == 0x4B) {
            if (is774()) {
                // 774クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }""",
     """        // Respawn（775：0x52。774（0x50）と同形式のためそのまま送る）。
        if (packet.id() == 0x52) {
            if (is774()) {
                // 774クライアント：Respawn は 0x50（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));
            }
            if (is773()) {
                // 773クライアント：Respawn は 0x50（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));
            }
            if (is770() || is771() || is772()) {
                // 770／771／772クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }
            if (is768() || is769()) {
                // 768／769クライアント：Respawn は 0x4C（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4C, packet.payload()));
            }"""),
    # ---- B: handleClientPlayTo775 の構築ID ----
    ("B1 Set Creative Slot",
     """                    return Outcome.toServer(new MinecraftPacket(0x37, payload));
                }
                logUntranslatedOnce("PLAY_SET_CREATIVE_SLOT");""",
     """                    return Outcome.toServer(new MinecraftPacket(0x38, payload));
                }
                logUntranslatedOnce("PLAY_SET_CREATIVE_SLOT");"""),
    ("B2 Vehicle Move",
     """                return Outcome.toServer(new MinecraftPacket(0x21, payload));
            }
            case PLAY_CRAFT_RECIPE_REQUEST, PLAY_DISPLAYED_RECIPE -> {""",
     """                return Outcome.toServer(new MinecraftPacket(0x22, payload));
            }
            case PLAY_CRAFT_RECIPE_REQUEST, PLAY_DISPLAYED_RECIPE -> {"""),
    ("B3 Chat Command Signed",
     """                    // 775の Chat Command Signed（0x07）に送る。
                    return Outcome.toServer(new MinecraftPacket(0x07, packet.payload()));""",
     """                    // 775の Chat Command Signed（0x08）に送る。
                    return Outcome.toServer(new MinecraftPacket(0x08, packet.payload()));"""),
    ("B4 Chat Command unsigned",
     """                // 766／767の符号なし Chat Command は775の 0x06（同形式）。
                return Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));""",
     """                // 766／767の符号なし Chat Command は775の 0x07（同形式）。
                return Outcome.toServer(new MinecraftPacket(0x07, packet.payload()));"""),
    ("B5 Resource Pack Response 763",
     """                    byte[] payload = PlayTranslator.translateResourcePackResponse763To765(
                            packet.payload(), lastSentPack);
                    return Outcome.toServer(new MinecraftPacket(0x30, payload));""",
     """                    byte[] payload = PlayTranslator.translateResourcePackResponse763To765(
                            packet.payload(), lastSentPack);
                    return Outcome.toServer(new MinecraftPacket(0x31, payload));"""),
    ("B6 Resource Pack Response 764",
     """                    byte[] payload = PlayTranslator.translateResourcePackResponse764To765(
                            packet.payload(), lastSentPack);
                    return Outcome.toServer(new MinecraftPacket(0x30, payload));""",
     """                    byte[] payload = PlayTranslator.translateResourcePackResponse764To765(
                            packet.payload(), lastSentPack);
                    return Outcome.toServer(new MinecraftPacket(0x31, payload));"""),
    ("B7 Resource Pack Response default",
     """                return Outcome.toServer(new MinecraftPacket(0x30, packet.payload()));
            }
            case PLAY_CONFIGURATION_ACKNOWLEDGED -> {""",
     """                return Outcome.toServer(new MinecraftPacket(0x31, packet.payload()));
            }
            case PLAY_CONFIGURATION_ACKNOWLEDGED -> {"""),
    ("B8 Configuration Acknowledged",
     """                return Outcome.toServer(new MinecraftPacket(0x0f, packet.payload()));
            }
            case PLAY_CHUNK_BATCH_RECEIVED -> {""",
     """                return Outcome.toServer(new MinecraftPacket(0x10, packet.payload()));
            }
            case PLAY_CHUNK_BATCH_RECEIVED -> {"""),
    ("B9 Chunk Batch Received",
     """                return Outcome.toServer(new MinecraftPacket(0x0a, packet.payload()));
            }
            case PLAY_COOKIE_RESPONSE -> {""",
     """                return Outcome.toServer(new MinecraftPacket(0x0b, packet.payload()));
            }
            case PLAY_COOKIE_RESPONSE -> {"""),
    ("B10 Enchant Item",
     """                byte[] payload = PlayTranslator.translateEnchantItem767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x10, payload));""",
     """                byte[] payload = PlayTranslator.translateEnchantItem767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x11, payload));"""),
    ("B11 Close Window",
     """                byte[] payload = PlayTranslator.translateCloseWindow767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x12, payload));""",
     """                byte[] payload = PlayTranslator.translateCloseWindow767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x13, payload));"""),
    ("B12 Client Information",
     """                return Outcome.toServer(new MinecraftPacket(0x0d, payload));
            }
            default -> {""",
     """                return Outcome.toServer(new MinecraftPacket(0x0e, payload));
            }
            default -> {"""),
    ("B13 Player Input (Steer Vehicle)",
     """                byte[] payload = PlayTranslator.translateSteerVehicleToPlayerInput768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x2a, payload));""",
     """                byte[] payload = PlayTranslator.translateSteerVehicleToPlayerInput768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x2b, payload));"""),
]

failed = []
for name, old, new in REPLACEMENTS:
    n = src.count(old)
    if n != 1:
        print(f"NG: {name}: anchor が {n} 回（1回のはず）", file=sys.stderr)
        failed.append(name)
        continue
    src = src.replace(old, new, 1)
    print(f"OK: {name}")

if failed:
    print(f"失敗 {len(failed)} 件: {failed}", file=sys.stderr)
    sys.exit(1)

open(PATH, "w", encoding="utf-8").write(src)
print(f"patch-proxysession-775.py を更新した（{len(REPLACEMENTS)} 件）")
