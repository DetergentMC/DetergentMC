#!/usr/bin/env python3
"""ProxySession.java へ patch-proxysession-775.py では拾えなかった
旧ハンドラの is775 分岐を手で入れる（774 の be6978d 相当）。

対象は翻訳系ハンドラの is773()||is774() 分岐 7 箇所。
- Join Game 3箇所（JoinGame763 保持＋本体、JoinGame766、汎用 JoinGame）:
  773/774 と同形式の翻訳済みペイロードを 0x31＋AddPack 0x51 で送る。
- From766 Disconnect / From773 Disconnect:
  775 の Disconnect は 0x20 のため分岐に || is775() を足す。
- From766 Respawn: 775 は 0x52（同翻訳ペイロード）。

チェーン側の JoinGame774/773/772/771/770/769/768 は patch 側で
is775 分岐済み（JoinGame774 の is775 分岐が JoinGame775 からの
委譲も受ける）のため、ここでは触らない。
"""
import sys

PATH = "/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"

src = open(PATH, encoding="utf-8").read()

REPLACEMENTS = [
    ("1 JoinGame763 heldJoinGame",
     """        } else if (is773() || is774()) {
            // 773／774の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            heldJoinGame = PlayTranslator.translateJoinGame767To768(
                    PlayTranslator.translateJoinGame763To766(join, registries));
            heldJoinGameId = 0x30;""",
     """        } else if (is775()) {
            // 775の Join Game は770／771／772と同形式だが、番号だけ 0x31（770／771／772は 0x2B）。
            heldJoinGame = PlayTranslator.translateJoinGame767To768(
                    PlayTranslator.translateJoinGame763To766(join, registries));
            heldJoinGameId = 0x31;
        } else if (is773() || is774()) {
            // 773／774の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            heldJoinGame = PlayTranslator.translateJoinGame767To768(
                    PlayTranslator.translateJoinGame763To766(join, registries));
            heldJoinGameId = 0x30;"""),
    ("2 JoinGame763 main",
     """        if (is773() || is774()) {
            // 773／774の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            // プレイ状態の Add Resource Pack は 0x4F（770／771／772は 0x4A）。
            toClient.add(new MinecraftPacket(0x30,
                    PlayTranslator.translateJoinGame767To768(
                            PlayTranslator.translateJoinGame763To766(join, registries))));
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }""",
     """        if (is775()) {
            // 775の Join Game は770／771／772と同形式だが、番号だけ 0x31（770／771／772は 0x2B）。
            // プレイ状態の Add Resource Pack は 0x51（770／771／772は 0x4A）。
            toClient.add(new MinecraftPacket(0x31,
                    PlayTranslator.translateJoinGame767To768(
                            PlayTranslator.translateJoinGame763To766(join, registries))));
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x51,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }
        if (is773() || is774()) {
            // 773／774の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            // プレイ状態の Add Resource Pack は 0x4F（770／771／772は 0x4A）。
            toClient.add(new MinecraftPacket(0x30,
                    PlayTranslator.translateJoinGame767To768(
                            PlayTranslator.translateJoinGame763To766(join, registries))));
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }"""),
    ("3 From766 Disconnect",
     """                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            if (is773() || is774()) {
                // 773／774クライアント：Disconnect は 0x20（NBT）。""",
     """                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            if (is773() || is774() || is775()) {
                // 773／774／775クライアント：Disconnect は 0x20（NBT）。"""),
    ("4 From766 Respawn",
     """            if (is773() || is774()) {
                // 773／774クライアント：766の Respawn は767と同一形式のため、
                // 767→768へ直して 0x50 で送る。""",
     """            if (is775()) {
                // 775クライアント：766の Respawn は767と同一形式のため、
                // 767→768へ直して 0x52 で送る。
                return Outcome.toClient(new MinecraftPacket(0x52,
                        PlayTranslator.translateRespawn767To768(
                                packet.payload(), registries)));
            }
            if (is773() || is774()) {
                // 773／774クライアント：766の Respawn は767と同一形式のため、
                // 767→768へ直して 0x50 で送る。"""),
    ("5 JoinGame766",
     """        if (is773() || is774()) {
            // 773／774クライアント：766の Join Game は767と同一形式のため、
            // 767→768へ直して 0x30 で送る。Add Resource Pack は 0x4F。
            toClient.add(new MinecraftPacket(0x30,
                    PlayTranslator.translateJoinGame767To768(packet.payload())));
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }""",
     """        if (is775()) {
            // 775クライアント：766の Join Game は767と同一形式のため、
            // 767→768へ直して 0x31 で送る。Add Resource Pack は 0x51。
            toClient.add(new MinecraftPacket(0x31,
                    PlayTranslator.translateJoinGame767To768(packet.payload())));
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x51,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }
        if (is773() || is774()) {
            // 773／774クライアント：766の Join Game は767と同一形式のため、
            // 767→768へ直して 0x30 で送る。Add Resource Pack は 0x4F。
            toClient.add(new MinecraftPacket(0x30,
                    PlayTranslator.translateJoinGame767To768(packet.payload())));
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }"""),
    ("6 generic JoinGame",
     """        if (is773() || is774()) {
            // 773／774クライアント：dimension を文字列からレジストリ番号にし、
            // SpawnInfo の末尾に seaLevel を補う（770・771・772は769と同形式だが、
            // Join Game の番号だけ 0x30、Add Resource Pack は 0x4F）。
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x30,
                    PlayTranslator.translateJoinGame767To768(
                            PlayTranslator.translateJoinGame765To767(
                                    PlayTranslator.parseJoinGame765(packet.payload()),
                                    registries))));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }""",
     """        if (is775()) {
            // 775クライアント：dimension を文字列からレジストリ番号にし、
            // SpawnInfo の末尾に seaLevel を補う（770・771・772は769と同形式だが、
            // Join Game の番号だけ 0x31、Add Resource Pack は 0x51）。
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x31,
                    PlayTranslator.translateJoinGame767To768(
                            PlayTranslator.translateJoinGame765To767(
                                    PlayTranslator.parseJoinGame765(packet.payload()),
                                    registries))));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x51,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }
        if (is773() || is774()) {
            // 773／774クライアント：dimension を文字列からレジストリ番号にし、
            // SpawnInfo の末尾に seaLevel を補う（770・771・772は769と同形式だが、
            // Join Game の番号だけ 0x30、Add Resource Pack は 0x4F）。
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x30,
                    PlayTranslator.translateJoinGame767To768(
                            PlayTranslator.translateJoinGame765To767(
                                    PlayTranslator.parseJoinGame765(packet.payload()),
                                    registries))));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }"""),
    ("7 From773 Disconnect",
     """                int outId = is763Like() ? 0x1A : 0x1B;
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(outId, payload)), false, true);
            }
            if (is773() || is774()) {
                // 773／774クライアント：Disconnect は 0x20（NBT）。""",
     """                int outId = is763Like() ? 0x1A : 0x1B;
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(outId, payload)), false, true);
            }
            if (is773() || is774() || is775()) {
                // 773／774／775クライアント：Disconnect は 0x20（NBT）。"""),
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
print(f"ProxySession.java を更新した（{len(REPLACEMENTS)} 件）")
