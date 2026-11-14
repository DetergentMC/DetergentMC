# Copyright (c) 2026 DetergentMC contributors.
# SPDX-License-Identifier: Apache-2.0

"""ProxySession の773パケットIDの誤りを直す（第2弾）。

fix-773-proxysession-ids.py の適用後に残った ProxySession773Test の
4件の失敗（2026-11-12）の原因：
- handleServerPlayUp の gate に is773() が無く、765サーバーの
  Keep Alive／Ping／Disconnect／Respawn／Add Resource Pack が
  765→763の経路に落ちていた（Respawn は 55バイトの765形式のまま）。
- handleServerPlayFrom773 の Respawn に is770()／is768()／is769() 分岐が無い。
- handleSynthesizedJoinGame に is773() 分岐が無く、預かった Join Game の
  番号が 0x29（765形式）に落ちていた。
- handleClientConfiguration772／771 の Custom Click Action の条件に
  isServer773() が無い。

使い方：python3 fix-773-proxysession-ids-2.py（ProxySession.java を直接書き換える）
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
    # 1. handleSynthesizedJoinGame：773クライアント向け分岐を足す
    # ------------------------------------------------------------------
    src = replace_in_method(src,
        "private Outcome handleSynthesizedJoinGame(MinecraftPacket packet) {",
        """        if (is770() || is771() || is772()) {""",
        """        if (is773()) {
            // 773の Join Game は770／771／772と同形式だが、番号だけ 0x30（770／771／772は 0x2B）。
            heldJoinGame = PlayTranslator.translateJoinGame767To768(
                    PlayTranslator.translateJoinGame763To766(join, registries));
            heldJoinGameId = 0x30;
        } else if (is770() || is771() || is772()) {""",
        label="SynthesizedJoinGame is773 0x30")

    # ------------------------------------------------------------------
    # 2. handleServerPlayFrom773 の Respawn：is770／is768／is769 分岐を足す
    # ------------------------------------------------------------------
    src = replace_in_method(src,
        "private Outcome handleServerPlayFrom773(MinecraftPacket packet) {",
        """            if (is772()) {
                // 772クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }
            byte[] payload767 = PlayTranslator.translateRespawn768To767(""",
        """            if (is772()) {
                // 772クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }
            if (is770()) {
                // 770クライアント：Respawn は 0x4C（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4C, packet.payload()));
            }
            if (is768() || is769()) {
                // 768／769クライアント：Respawn は 0x4C（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4C, packet.payload()));
            }
            byte[] payload767 = PlayTranslator.translateRespawn768To767(""",
        label="From773 Respawn 770/768/769 0x4C")

    # ------------------------------------------------------------------
    # 3. handleServerPlayUp：gate と各パケットの773番号を足す
    # ------------------------------------------------------------------
    src = replace(src,
        """        // 766／767／768／769／770／771クライアント：Keep Alive／Ping／Disconnect／Respawn／
        // Add Resource Pack だけ変換する。それ以外のプレイ状態のパケットは
        // 766の対応表・レジストリ表（別担当の作業中）が要るため落とす。
        // （Keep Alive／Ping を落とすとサーバーがタイムアウトで切断する
        // ため、接続維持のためだけは中継する。）
        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {""",
        """        // 766／767／768／769／770／771／772／773クライアント：Keep Alive／Ping／Disconnect／Respawn／
        // Add Resource Pack だけ変換する。それ以外のプレイ状態のパケットは
        // 766の対応表・レジストリ表（別担当の作業中）が要るため落とす。
        // （Keep Alive／Ping を落とすとサーバーがタイムアウトで切断する
        // ため、接続維持のためだけは中継する。）
        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {""",
        label="PlayUp gate includes 773")
    src = replace(src,
        """            if (name766 == PacketName.PLAY_KEEP_ALIVE) {
                // 770／771／772の Keep Alive は 0x26（768／769は 0x27、766／767は 0x26）。
                return Outcome.toClient(new MinecraftPacket(
                        is768() || is769() ? 0x27 : 0x26, packet.payload()));
            }""",
        """            if (name766 == PacketName.PLAY_KEEP_ALIVE) {
                // 770／771／772の Keep Alive は 0x26（773は 0x2B、768／769は 0x27、766／767は 0x26）。
                return Outcome.toClient(new MinecraftPacket(
                        is773() ? 0x2B : is768() || is769() ? 0x27 : 0x26, packet.payload()));
            }""",
        label="PlayUp Keep Alive 773 0x2B")
    src = replace(src,
        """            if (name766 == PacketName.PLAY_PING) {
                // 770／771／772の Ping は 0x36（768／769は 0x37、766／767は 0x35）。
                return Outcome.toClient(new MinecraftPacket(
                        is768() || is769() ? 0x37 : is770() || is771() || is772() ? 0x36 : 0x35, packet.payload()));
            }""",
        """            if (name766 == PacketName.PLAY_PING) {
                // 770／771／772の Ping は 0x36（773は 0x3B、768／769は 0x37、766／767は 0x35）。
                return Outcome.toClient(new MinecraftPacket(
                        is773() ? 0x3B : is768() || is769() ? 0x37 : is770() || is771() || is772() ? 0x36 : 0x35, packet.payload()));
            }""",
        label="PlayUp Ping 773 0x3B")
    src = replace(src,
        """            if (name766 == PacketName.PLAY_DISCONNECT) {
                closed = true;
                // 770／771／772の Disconnect は 0x1C（768／769は 0x1D）。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(is770() || is771() || is772() ? 0x1C : 0x1D, packet.payload())),
                        false, true);
            }""",
        """            if (name766 == PacketName.PLAY_DISCONNECT) {
                closed = true;
                // 770／771／772の Disconnect は 0x1C（773は 0x20、768／769は 0x1D）。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(is773() ? 0x20 : is770() || is771() || is772() ? 0x1C : 0x1D, packet.payload())),
                        false, true);
            }""",
        label="PlayUp Disconnect 773 0x20")
    src = replace(src,
        """            if (name766 == PacketName.PLAY_RESPAWN) {
                // 770／771／772の Respawn は769と同形式（seaLevel 付き）のため二段で変換する。
                byte[] payload = is768() || is769() || is770() || is771() || is772()
                        ? PlayTranslator.translateRespawn767To768(
                                PlayTranslator.translateRespawn765To767(
                                        packet.payload(), registries), registries)
                        : is767()
                                ? PlayTranslator.translateRespawn765To767(
                                        packet.payload(), registries)
                                : PlayTranslator.translateRespawn765To766(
                                        packet.payload(), registries);
                return Outcome.toClient(new MinecraftPacket(
                        is768() || is769() ? 0x4C : is770() || is771() || is772() ? 0x4B : 0x47, payload));
            }""",
        """            if (name766 == PacketName.PLAY_RESPAWN) {
                // 770／771／772／773の Respawn は769と同形式（seaLevel 付き）のため二段で変換する。
                byte[] payload = is768() || is769() || is770() || is771() || is772() || is773()
                        ? PlayTranslator.translateRespawn767To768(
                                PlayTranslator.translateRespawn765To767(
                                        packet.payload(), registries), registries)
                        : is767()
                                ? PlayTranslator.translateRespawn765To767(
                                        packet.payload(), registries)
                                : PlayTranslator.translateRespawn765To766(
                                        packet.payload(), registries);
                return Outcome.toClient(new MinecraftPacket(
                        is773() ? 0x50 : is768() || is769() ? 0x4C : is770() || is771() || is772() ? 0x4B : 0x47, payload));
            }""",
        label="PlayUp Respawn 773 0x50")
    src = replace(src,
        """            if (name766 == PacketName.PLAY_ADD_RESOURCE_PACK) {
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                // 770／771／772の Add Resource Pack は 0x4A（768／769は 0x4B、766／767は 0x46）。
                return Outcome.toClient(new MinecraftPacket(
                        is768() || is769() ? 0x4B : is770() || is771() || is772() ? 0x4A : 0x46,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }""",
        """            if (name766 == PacketName.PLAY_ADD_RESOURCE_PACK) {
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                // 770／771／772の Add Resource Pack は 0x4A（773は 0x4F、768／769は 0x4B、766／767は 0x46）。
                return Outcome.toClient(new MinecraftPacket(
                        is773() ? 0x4F : is768() || is769() ? 0x4B : is770() || is771() || is772() ? 0x4A : 0x46,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }""",
        label="PlayUp Add Resource Pack 773 0x4F")

    # ------------------------------------------------------------------
    # 4. Custom Click Action：772／771クライアントから773サーバーへ送る
    # ------------------------------------------------------------------
    src = replace_in_method(src,
        "private Outcome handleClientConfiguration772(MinecraftPacket packet) {",
        """        if (packet.id() == 0x08) {
            // Custom Click Action（771・772のみ。770では Custom Report Details と同ID）。
            // 771・772サーバーへはそのまま送り、旧サーバー向けは落とす。
            if (isServer772() || isServer771()) {""",
        """        if (packet.id() == 0x08) {
            // Custom Click Action（771・772・773のみ。770では Custom Report Details と同ID）。
            // 771・772・773サーバーへはそのまま送り、旧サーバー向けは落とす。
            if (isServer773() || isServer772() || isServer771()) {""",
        label="CustomClickAction 772 to 773 server")
    src = replace_in_method(src,
        "private Outcome handleClientConfiguration771(MinecraftPacket packet) {",
        """        if (packet.id() == 0x08) {
            // Custom Click Action（771・772にあり。770では Custom Report Details と同ID）。
            // 771・772サーバーへはそのまま送り、旧サーバー向けは落とす。
            if (isServer771() || isServer772()) {""",
        """        if (packet.id() == 0x08) {
            // Custom Click Action（771・772・773にあり。770では Custom Report Details と同ID）。
            // 771・772・773サーバーへはそのまま送り、旧サーバー向けは落とす。
            if (isServer773() || isServer771() || isServer772()) {""",
        label="CustomClickAction 771 to 773 server")

    # ------------------------------------------------------------------
    # 5. handleServerPlay のディスパッチのコメント（773の番号）
    # ------------------------------------------------------------------
    src = replace(src,
        """        // 内部773：Join Game は 0x2B、Spawn Position は 0x5A、
        // Start Configuration は 0x6F（772と同じ）。""",
        """        // 内部773：Join Game は 0x30、Spawn Position は 0x5F、
        // Start Configuration は 0x74。""",
        label="handleServerPlay dispatch comment 773")

    if failures:
        print("NG:")
        for f in failures:
            print("  " + f)
        sys.exit(1)
    SRC.write_text(src, encoding="utf-8")
    print("ProxySession.java を書き換えた（第2弾）")


if __name__ == "__main__":
    main()
