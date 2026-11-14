# Copyright (c) 2026 DetergentMC contributors.
# SPDX-License-Identifier: Apache-2.0

"""ProxySession の773パケットIDの誤りを直す。

背景：773パッチの生成器は 772 の分岐を写すとき、版の条件（is773()）だけを
変えてパケットIDを 772 のまま残した箇所が多数あった。プレイ・クライアント行きの
ID は 772→773 で変わる（Join Game 0x2B→0x30、Respawn 0x4B→0x50、
Spawn Position 0x5A→0x5F、Keep Alive 0x26→0x2B、Ping 0x36→0x3B、
Disconnect 0x1C→0x20、Start Configuration 0x6F→0x74、Add Resource Pack
0x4A→0x4F）ため、773クライアントへ送る番号は 773 の値に直す必要がある。
ProxySession773Test の10件の失敗（2026-11-12）の原因。

使い方：python3 fix-773-proxysession-ids.py（ProxySession.java を直接書き換える）
"""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE.parent / "src/main/java/io/detergentmc/protocol/ProxySession.java"

failures = []


def replace(src, old, new, expect=1, label=""):
    n = src.count(old)
    if n != expect:
        failures.append(f"{label}: {old[:60]!r} が {n} 回（{expect} 回のはず）")
        return src
    print(f"OK: {label}")
    return src.replace(old, new, 1) if expect == 1 else src.replace(old, new)


def replace_in_method(src, method_sig, old, new, expect=1, label=""):
    start = src.index(method_sig)
    end = src.index("\n    private ", start + len(method_sig))
    span = src[start:end]
    n = span.count(old)
    if n != expect:
        failures.append(f"{label}: {old[:60]!r} が {n} 回（{expect} 回のはず）")
        return src
    print(f"OK: {label}")
    return src[:start] + span.replace(old, new, 1) + src[end:]


def main():
    src = SRC.read_text(encoding="utf-8")

    # ------------------------------------------------------------------
    # A. handleServerPlayFrom773 のディスパッチ（773サーバーからの番号）
    # ------------------------------------------------------------------
    src = replace(src,
        """        // Start Configuration（773：0x6F）。763には設定フェーズもこのパケットも無い。
        if (packet.id() == 0x6F) {""",
        """        // Start Configuration（773：0x74）。763には設定フェーズもこのパケットも無い。
        if (packet.id() == 0x74) {""",
        label="From773 Start Configuration 0x74")
    src = replace(src,
        """        // Join Game（773：0x2B）。
        if (packet.id() == 0x2B) {""",
        """        // Join Game（773：0x30）。
        if (packet.id() == 0x30) {""",
        label="From773 Join Game 0x30")
    src = replace(src,
        """        // Spawn Position（773：0x5A）。
        if (packet.id() == 0x5A) {""",
        """        // Spawn Position（773：0x5F）。
        if (packet.id() == 0x5F) {""",
        label="From773 Spawn Position 0x5F")
    src = replace(src,
        """        // Keep Alive（773：0x26）／Ping（773：0x36）は接続維持のため中継する。
        if (packet.id() == 0x26) {""",
        """        // Keep Alive（773：0x2B）／Ping（773：0x3B）は接続維持のため中継する。
        if (packet.id() == 0x2B) {""",
        label="From773 Keep Alive 0x2B")
    src = replace_in_method(src,
        "private Outcome handleServerPlayFrom773(MinecraftPacket packet) {",
        """        if (packet.id() == 0x36) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,""",
        """        if (packet.id() == 0x3B) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,""",
        label="From773 Ping 0x3B")
    src = replace(src,
        """        // Disconnect（773：0x1C、NBT）。
        if (packet.id() == 0x1C) {""",
        """        // Disconnect（773：0x20、NBT）。
        if (packet.id() == 0x20) {""",
        label="From773 Disconnect 0x20")
    # From773 と From772 の両方にある合体分岐（計2回）を分ける。
    src = replace(src,
        """            if (is771() || is772() || is773()) {
                // 771／772／773クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }""",
        """            if (is773()) {
                // 773クライアント：Disconnect は 0x20（NBT）。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);
            }
            if (is771() || is772()) {
                // 771／772クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }""",
        expect=2, label="From773/772 Disconnect 773->0x20")
    src = replace_in_method(src,
        "private Outcome handleServerPlayFrom773(MinecraftPacket packet) {",
        """        // Respawn（773：0x4B。772（0x4B）と同形式のためそのまま送る）。
        if (packet.id() == 0x4B) {
            if (is772()) {""",
        """        // Respawn（773：0x50。772（0x4B）と同形式のためそのまま送る）。
        if (packet.id() == 0x50) {
            if (is773()) {
                // 773クライアント：Respawn は 0x50（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));
            }
            if (is772()) {""",
        label="From773 Respawn 0x50")
    src = replace(src,
        """     * 内部773→クライアントのプレイ状態（サーバー→クライアント）。
     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／
     * Start Configuration は772と同形式（ID も同じ）のため、772の処理を使い回す。""",
        """     * 内部773→クライアントのプレイ状態（サーバー→クライアント）。
     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／
     * Start Configuration は772と同形式だが ID が変わる（Join Game 0x2B→0x30、
     * Respawn 0x4B→0x50、Spawn Position 0x5A→0x5F、Keep Alive 0x26→0x2B、
     * Ping 0x36→0x3B、Disconnect 0x1C→0x20、Start Configuration 0x6F→0x74）
     * ため、773の番号で振り分ける。""",
        label="From773 javadoc")
    src = replace(src,
        """    /**
     * Join Game（内部773：0x2B）のクライアント向け変換。""",
        """    /**
     * Join Game（内部773：0x30）のクライアント向け変換。""",
        label="JoinGame773 javadoc")

    # ------------------------------------------------------------------
    # B. Join Game の is773() 分岐（768／769／770／771／772）：番号を直す
    # ------------------------------------------------------------------
    src = replace(src,
        """        if (is773()) {
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x2B, packet.payload()));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4A,""",
        """        if (is773()) {
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x30, packet.payload()));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,""",
        expect=4, label="JoinGame is773 0x30/0x4F x4")
    src = replace(src,
        """            // 773クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。""",
        """            // 773クライアント：Join Game は 0x30、Add Resource Pack は 0x4F。""",
        label="JoinGame768 is773 comment")

    # ------------------------------------------------------------------
    # C. Join Game に無かった is773() 分岐を足す（763／766／汎用）
    # ------------------------------------------------------------------
    src = replace_in_method(src,
        "private Outcome handleServerJoinGame763(MinecraftPacket packet) {",
        """        if (is770() || is771() || is772()) {""",
        """        if (is773()) {
            // 773の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
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
        }
        if (is770() || is771() || is772()) {""",
        label="JoinGame763 is773 branch")
    src = replace_in_method(src,
        "private Outcome handleServerJoinGame766(MinecraftPacket packet) {",
        """        if (is770() || is771() || is772()) {""",
        """        if (is773()) {
            // 773クライアント：766の Join Game は767と同一形式のため、
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
        }
        if (is770() || is771() || is772()) {""",
        label="JoinGame766 is773 branch")
    src = replace_in_method(src,
        "private Outcome handleServerJoinGame(MinecraftPacket packet) {",
        """        if (is770() || is771() || is772()) {""",
        """        if (is773()) {
            // 773クライアント：dimension を文字列からレジストリ番号にし、
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
        }
        if (is770() || is771() || is772()) {""",
        label="JoinGame generic is773 branch")

    # ------------------------------------------------------------------
    # D. Disconnect の is773() 分岐（763サーバー＋From771／770／769／768）
    # ------------------------------------------------------------------
    src = replace(src,
        """            if (is773()) {
                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, payload)), false, true);
            }""",
        """            if (is773()) {
                // 773クライアント：Disconnect は 0x20（NBT）。
                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x20, payload)), false, true);
            }""",
        label="763 Disconnect is773 0x20")
    src = replace(src,
        """            if (is773()) {
                // 773クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }""",
        """            if (is773()) {
                // 773クライアント：Disconnect は 0x20（NBT）。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);
            }""",
        expect=4, label="From771/770/769/768 Disconnect is773 0x20")

    # ------------------------------------------------------------------
    # E. Respawn の is773() 分岐（From772／771／770／769／768）
    # ------------------------------------------------------------------
    src = replace(src,
        """            if (is773()) {
                // 773クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }""",
        """            if (is773()) {
                // 773クライアント：Respawn は 0x50（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));
            }""",
        expect=5, label="Respawn is773 0x50 x5")

    # ------------------------------------------------------------------
    # F. Client Information：773サーバー向けは particleStatus を落とさない
    # ------------------------------------------------------------------
    src = replace(src,
        """        if (packet.id() == 0x00 && !isServer768() && !isServer769() && !isServer770() && !isServer771()
                && !isServer772()) {
            // Settings：768 は末尾に particleStatus がある。旧サーバー向けに落とす。
            // 769も770も771も772も768と同じ9フィールドのため、769／770／771／772サーバー向けはそのまま通す。""",
        """        if (packet.id() == 0x00 && !isServer768() && !isServer769() && !isServer770() && !isServer771()
                && !isServer772() && !isServer773()) {
            // Settings：768 は末尾に particleStatus がある。旧サーバー向けに落とす。
            // 769も770も771も772も773も768と同じ9フィールドのため、769／770／771／772／773サーバー向けはそのまま通す。""",
        label="ClientInformation keep particleStatus for 773")
    src = replace(src,
        """        if (isServer768() || isServer769() || isServer770() || isServer771() || isServer772()
                || isServer767() || isServer766()) {""",
        """        if (isServer768() || isServer769() || isServer770() || isServer771() || isServer772() || isServer773()
                || isServer767() || isServer766()) {""",
        label="ClientInformation passthrough includes 773")

    # ------------------------------------------------------------------
    # G. Custom Click Action：773サーバーへ送る（重複した isServer772() を直す）
    # ------------------------------------------------------------------
    src = replace(src,
        """            if (isServer772() || isServer771() || isServer772()) {
                return Outcome.toServer(packet);
            }""",
        """            if (isServer773() || isServer772() || isServer771()) {
                return Outcome.toServer(packet);
            }""",
        label="CustomClickAction to 773 server")

    # ------------------------------------------------------------------
    # H. 設定フェーズのダイアログ：773クライアント＋772サーバーで素通し
    # ------------------------------------------------------------------
    src = replace_in_method(src,
        "private Outcome handleServerConfiguration773From772(MinecraftPacket packet) {",
        """            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {""",
        """            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> Outcome.toClient(packet); // Clear Dialog／Show Dialog（772・773とも同ID・同形式のため素通し）
            default -> {""",
        label="Config773From772 dialog passthrough")

    # ------------------------------------------------------------------
    # I. プレイ状態の Add Resource Pack（合成・763サーバー）：773は 0x4F
    # ------------------------------------------------------------------
    src = replace(src,
        """                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。773は 0x4A。
                toClient.add(new MinecraftPacket(is770() || is771() || is772() || is773() ? 0x4A : 0x4B,""",
        """                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。773は 0x4F。
                toClient.add(new MinecraftPacket(is773() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x4B,""",
        label="Synthesized Add Resource Pack 773 0x4F")
    src = replace(src,
        """                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is770() || is771() || is772() || is773() ? 0x4A : 0x46,""",
        """                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is773() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x46,""",
        label="763 Resource Pack 773 0x4F")

    # ------------------------------------------------------------------
    # J. clientId のフォールバック（翻訳器が空を返したときだけ使う番号）
    # ------------------------------------------------------------------
    src = replace(src,
        """.orElse(is768() || is769() ? 0x70 : is770() || is771() || is772() || is773() ? 0x6F : 0x69);""",
        """.orElse(is768() || is769() ? 0x70 : is773() ? 0x74 : is770() || is771() || is772() ? 0x6F : 0x69);""",
        label="startConfigurationClientId fallback 0x74")
    src = replace(src,
        """.orElse(is768() || is769() ? 0x5B : is770() || is771() || is772() || is773() ? 0x5A : 0x56);""",
        """.orElse(is768() || is769() ? 0x5B : is773() ? 0x5F : is770() || is771() || is772() ? 0x5A : 0x56);""",
        label="spawnPositionClientId fallback 0x5F")
    src = replace(src,
        """                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x27 : 0x26);""",
        """                    serverProtocol, clientProtocol).orElse(is773() ? 0x2B : is768() || is769() ? 0x27 : 0x26);""",
        label="keepAliveClientId fallback 0x2B")
    src = replace(src,
        """.orElse(is768() || is769() ? 0x37 : is770() || is771() || is772() || is773() ? 0x36 : 0x35);""",
        """.orElse(is768() || is769() ? 0x37 : is773() ? 0x3B : is770() || is771() || is772() ? 0x36 : 0x35);""",
        label="pingClientId fallback 0x3B")

    # ------------------------------------------------------------------
    # K. From766 の Disconnect／Respawn：773クライアント向け分岐を足す
    # ------------------------------------------------------------------
    src = replace_in_method(src,
        "private Outcome handleServerPlayFrom766(MinecraftPacket packet) {",
        """            if (is766() || is767() || is768() || is769()) {
                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            return new Outcome(List.of(),
                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);""",
        """            if (is766() || is767() || is768() || is769()) {
                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            if (is773()) {
                // 773クライアント：Disconnect は 0x20（NBT）。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);
            }
            return new Outcome(List.of(),
                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);""",
        label="From766 Disconnect is773 0x20")
    src = replace_in_method(src,
        "private Outcome handleServerPlayFrom766(MinecraftPacket packet) {",
        """            byte[] payload;
            int outId;
            if (is763()) {
                payload = PlayTranslator.translateRespawn766To763(packet.payload(), registries);
                outId = 0x41;""",
        """            if (is773()) {
                // 773クライアント：766の Respawn は767と同一形式のため、
                // 767→768へ直して 0x50 で送る。
                return Outcome.toClient(new MinecraftPacket(0x50,
                        PlayTranslator.translateRespawn767To768(
                                packet.payload(), registries)));
            }
            byte[] payload;
            int outId;
            if (is763()) {
                payload = PlayTranslator.translateRespawn766To763(packet.payload(), registries);
                outId = 0x41;""",
        label="From766 Respawn is773 0x50")

    # ------------------------------------------------------------------
    # L. handleServerConfiguration772From773 の javadoc（lava_chicken は落とさない）
    # ------------------------------------------------------------------
    src = replace(src,
        """     * Registry Data だけ773→772へ並べ替え（minecraft:dialog は772にもあるため通し、
     * jukebox_song の lava_chicken は落とす）、Tags はレジストリ番号が""",
        """     * Registry Data だけ773→772へ並べ替え（minecraft:dialog は772にもあるため通し、
     * jukebox_song は772・773とも同じ21件のため素通し）、Tags はレジストリ番号が""",
        label="772From773 javadoc jukebox")

    if failures:
        print("NG:")
        for f in failures:
            print("  " + f)
        sys.exit(1)
    SRC.write_text(src, encoding="utf-8")
    print("ProxySession.java を書き換えた")


if __name__ == "__main__":
    main()
