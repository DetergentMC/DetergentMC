#!/usr/bin/env python3
"""live の ProxySession.java の 774 関連の鎖・ID を修正する。

gen-774-proxysession.py（950e0f8）が入れた INSERTS に残っていた
誤りを直す。根拠は PacketIds.java（minecraft-data の protocol.json から
生成）と、live の 773 版メソッド（handleServerPlayFrom773／
handleServerConfiguration770From773／769From774 等）。
fix-774-proxysession-ids.py／chains.py はパッチファイル生成時のもので、
live の handleServerPlayFrom774 の陳腐IDは対象外だったためここで直す。

直すもの：
- 手書きメソッド 7 件の追加：handleServerPlay772To774／771To774、
  handleServerConfiguration774From772／774From771、
  handleServerConfiguration772From774／771From774／770From774。
  （タスクの6件に加え、is772 分岐が誤って 773From774 を呼んでいたため
  772From774 も追加する。）
- handleServerConfiguration770From772 の追加（is770 分岐が一段短い
  771From772 を呼んでいた既存不具合の修正）。
- ディスパッチの欠け：is774 設定分岐に isServer773、is773 設定分岐に
  isServer774、is772 設定分岐の重複 isServer773 の削除。
- handleServerPlayFrom774 の陳腐なパケットID（772時代のまま）：
  Start Configuration 0x6F→0x74、Join Game 0x2B→0x30、
  Spawn Position 0x5A→0x5F、Keep Alive 0x26→0x2B、Ping 0x36→0x3B、
  Disconnect 0x1C→0x20、Respawn 0x4B→0x50。
  あわせて Disconnect／Respawn の内側の分岐（773／774→0x20／0x50、
  770／771／772→0x1C／0x4B、768／769→0x4C）を直し、
  欠けていた 772／771／770／769／768 クライアント向け分岐を追加する。
- handleServerJoinGame774 の Join Game 0x2B→0x30、
  Add Resource Pack 0x4A→0x4F。
- 既存不具合（同じ箇所のためついでに直す）：
  From773／772／771 の Disconnect で 770 を 0x1D 組に入れていた
  （770 は 0x1C）と、Respawn で 770 に 0x4C を送っていた
  （770 は 0x4B）。PacketIds／protocol.json で確認済み。
  なお 773 報告の「From769／768 の Respawn で is770()→0x4B は誤り」
  という指摘自体が誤りで、0x4B が正しいためそこは直さない。
  From770 の is772 分岐が一段短い（770→771 を落として 771To772 を
  直接呼んでいた）のも直す。From766 の Disconnect／Respawn で
  770／771／772／774 クライアントがフォールバックに落ちていたのも直す。
  769From772／769From773／769From774 の registries 蓄積で
  reorderRegistries の target が 770／771／772 になっていた
  （正しくは 769＝V1_21_4）のも直す。
- handleClientConfiguration774 の重複 isServer772() の除去。

全置き換えは一致件数を検証する（fix は正確に1件、fix_all は指定件数）。
"""
from pathlib import Path

P = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java")


def fix(text, old, new, label, count=1):
    n = text.count(old)
    if n != count:
        raise SystemExit(f"[{label}] 一致 {n} 件（期待 {count} 件）:\n---OLD---\n{old}\n---")
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")

    # -- 1. Disconnect の 766-770 組（4か所：From774／773／772／771）--
    # 770 は 0x1C のため切り出す。
    text = fix(text,
               """            if (is766() || is767() || is768() || is769() || is770()) {
                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }""",
               """            if (is766() || is767() || is768() || is769()) {
                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            if (is770()) {
                // 770クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }""",
               "disconnect-group", count=4)

    # -- 2. Respawn の is770 → 0x4C（3か所：From773／772／771）。770 は 0x4B。 --
    text = fix(text,
               """            if (is770()) {
                // 770クライアント：Respawn は 0x4C（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4C, packet.payload()));
            }""",
               """            if (is770()) {
                // 770クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }""",
               "respawn-770", count=3)

    # -- 3. From771 の Respawn 見出しの誤記（770 は 0x4B）。 --
    text = fix(text,
               "        // Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。",
               "        // Respawn（771：0x4B。770（0x4B）と同形式のため770の変換を使い回す）。",
               "respawn-771-header")

    # -- 4. handleServerPlay772To774／771To774 を追加 --
    text = fix(text,
               """    private Outcome handleServerPlay773To774(MinecraftPacket packet) {
        MinecraftPacket as774 = to774ServerPacket(packet);
        if (as774 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as774);
    }""",
               """    private Outcome handleServerPlay773To774(MinecraftPacket packet) {
        MinecraftPacket as774 = to774ServerPacket(packet);
        if (as774 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as774);
    }

    /**
     * 内部772→774クライアントのプレイ状態（サーバー→クライアント）。
     * 772→773→774へ二段で変換する。
     */
    private Outcome handleServerPlay772To774(MinecraftPacket packet) {
        Outcome to773 = handleServerPlay772To773(packet);
        if (to773.toClient().isEmpty()) {
            return to773;
        }
        return handleServerPlay773To774(to773.toClient().get(0));
    }

    /**
     * 内部771→774クライアントのプレイ状態（サーバー→クライアント）。
     * 771→772→773→774へ三段で変換する。
     * From771 の is774() 分岐は生成器が一段目（771→772）を落としていたため、
     * このメソッド経由で呼ぶ（fix-774-proxysession-live.py）。
     */
    private Outcome handleServerPlay771To774(MinecraftPacket packet) {
        Outcome to773 = handleServerPlay771To773(packet);
        if (to773.toClient().isEmpty()) {
            return to773;
        }
        return handleServerPlay773To774(to773.toClient().get(0));
    }""",
               "play-772-771-to-774")

    # -- 5. handleServerConfiguration774From772／774From771 を追加 --
    text = fix(text,
               """            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 774クライアント＋内部768：設定フェーズを中継する。""",
               """            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 774クライアント＋内部772：設定フェーズを中継する。
     * 772と774の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ772→773→774へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration774From772(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（772は NBT。774も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：772のエントリ配列を774の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData773To774(
                                PlayTranslator.translateRegistryData772To773(packet.payload()))));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（772：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を774向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 774クライアント＋内部771：設定フェーズを中継する。
     * 771と774の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→772→773→774へ三段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration774From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。774も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を774の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData773To774(
                                PlayTranslator.translateRegistryData772To773(
                                        PlayTranslator.translateRegistryData771To772(packet.payload())))));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を774向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 774クライアント＋内部768：設定フェーズを中継する。""",
               "config-774-from-772-771")

    # -- 6. handleServerConfiguration772From774／771From774／770From774 を追加 --
    text = fix(text,
               """            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("774の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部774：設定フェーズを中継する。""",
               """            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("774の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 772クライアント＋内部774：設定フェーズを中継する。
     * 772と774の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ774→773→772へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は772にも
     * あるため素通しする。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration772From774(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（774は NBT。772も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：774のエントリ配列を772の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 772向けの辞書は772形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_7));
                byte[] payload774to773 =
                        PlayTranslator.translateRegistryData774To773(packet.payload());
                if (payload774to773 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData773To772(payload774to773);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（774：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を772向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("774の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 771クライアント＋内部774：設定フェーズを中継する。
     * 771と774の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ774→773→772→771へ三段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771にも
     * あるため素通しする。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From774(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（774は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：774のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 771向けの辞書は771形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_6));
                byte[] payload774to773 =
                        PlayTranslator.translateRegistryData774To773(packet.payload());
                if (payload774to773 == null) {
                    yield Outcome.drop();
                }
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(payload774to773);
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（774：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("774の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 770クライアント＋内部774：設定フェーズを中継する。
     * 770と774の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ774→773→772→771→770へ四段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は770に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration770From774(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（774は NBT。770も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：774のエントリ配列を770の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 770向けの辞書は770形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_5));
                byte[] payload774to773 =
                        PlayTranslator.translateRegistryData774To773(packet.payload());
                if (payload774to773 == null) {
                    yield Outcome.drop();
                }
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(payload774to773);
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData771To770(payload772to771);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（774：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を770向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（770に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("774の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部774：設定フェーズを中継する。""",
               "config-772-771-770-from-774")

    # -- 7. handleServerConfiguration770From772 を追加 --
    text = fix(text,
               """            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（770に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部773：設定フェーズを中継する。""",
               """            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（770に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 770クライアント＋内部772：設定フェーズを中継する。
     * 770と772の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ772→771→770へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は770に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration770From772(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（772は NBT。770も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：772のエントリ配列を770の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 770向けの辞書は770形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_5));
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(packet.payload());
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData771To770(payload772to771);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（772：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を770向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（770に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部773：設定フェーズを中継する。""",
               "config-770-from-772")

    # -- 8. is774 設定分岐に isServer773 を追加 --
    text = fix(text,
               """        if (is774()) {
            if (isServer774()) {
                // 内部774＋774クライアントはネイティブのためここには来ない。
                // 念のためそのまま通す。
                return Outcome.toClient(packet);
            }
            if (isServer772()) {
                return handleServerConfiguration774From772(packet);
            }""",
               """        if (is774()) {
            if (isServer774()) {
                // 内部774＋774クライアントはネイティブのためここには来ない。
                // 念のためそのまま通す。
                return Outcome.toClient(packet);
            }
            if (isServer773()) {
                return handleServerConfiguration774From773(packet);
            }
            if (isServer772()) {
                return handleServerConfiguration774From772(packet);
            }""",
               "dispatch-774-config-773")

    # -- 9. is773 設定分岐に isServer774 を追加 --
    text = fix(text,
               """        if (is773()) {
            if (isServer773()) {
                // 内部773＋773クライアントはネイティブのためここには来ない。
                // 念のためそのまま通す。
                return Outcome.toClient(packet);
            }
            if (isServer772()) {
                return handleServerConfiguration773From772(packet);
            }""",
               """        if (is773()) {
            if (isServer773()) {
                // 内部773＋773クライアントはネイティブのためここには来ない。
                // 念のためそのまま通す。
                return Outcome.toClient(packet);
            }
            if (isServer774()) {
                return handleServerConfiguration773From774(packet);
            }
            if (isServer772()) {
                return handleServerConfiguration773From772(packet);
            }""",
               "dispatch-773-config-774")

    # -- 10. is772 設定分岐：773From774 → 772From774、重複 isServer773 を削除 --
    text = fix(text,
               """            if (isServer774()) {
                return handleServerConfiguration773From774(packet);
            }
            if (isServer773()) {
                return handleServerConfiguration772From773(packet);
            }
            if (isServer773()) {
                return handleServerConfiguration772From773(packet);
            }""",
               """            if (isServer774()) {
                return handleServerConfiguration772From774(packet);
            }
            if (isServer773()) {
                return handleServerConfiguration772From773(packet);
            }""",
               "dispatch-772-config-774")

    # -- 11. is770 設定分岐：isServer772 → 770From772 --
    text = fix(text,
               """            if (isServer772()) {
                return handleServerConfiguration771From772(packet);
            }
            if (isServer771()) {
                return handleServerConfiguration770From771(packet);
            }""",
               """            if (isServer772()) {
                return handleServerConfiguration770From772(packet);
            }
            if (isServer771()) {
                return handleServerConfiguration770From771(packet);
            }""",
               "dispatch-770-config-772")
    # -- 12. handleServerPlayFrom774：Start Configuration 0x6F→0x74 --
    text = fix(text,
               "        // Start Configuration（774：0x6F）。763には設定フェーズもこのパケットも無い。\n        if (packet.id() == 0x6F) {",
               "        // Start Configuration（774：0x74）。763には設定フェーズもこのパケットも無い。\n        if (packet.id() == 0x74) {",
               "from774-start-configuration")

    # -- 13. handleServerPlayFrom774：Join Game 0x2B→0x30 --
    text = fix(text,
               "        // Join Game（774：0x2B）。\n        if (packet.id() == 0x2B) {\n            return handleServerJoinGame774(packet);",
               "        // Join Game（774：0x30）。\n        if (packet.id() == 0x30) {\n            return handleServerJoinGame774(packet);",
               "from774-join-game")

    # -- 14. handleServerPlayFrom774：Spawn Position 0x5A→0x5F --
    text = fix(text,
               "        // Spawn Position（774：0x5A）。\n        if (packet.id() == 0x5A) {",
               "        // Spawn Position（774：0x5F）。\n        if (packet.id() == 0x5F) {",
               "from774-spawn-position")

    # -- 15. handleServerPlayFrom774：Keep Alive 0x26→0x2B、Ping 0x36→0x3B --
    text = fix(text,
               """        // Keep Alive（774：0x26）／Ping（774：0x36）は接続維持のため中継する。
        if (packet.id() == 0x26) {""",
               """        // Keep Alive（774：0x2B）／Ping（774：0x3B）は接続維持のため中継する。
        if (packet.id() == 0x2B) {""",
               "from774-keep-alive")
    text = fix(text,
               """        if (packet.id() == 0x36) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,
                    serverProtocol, clientProtocol)
                    .orElse(is763() ? 0x32 : 0x33);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Disconnect（774：0x1C、NBT）。""",
               """        if (packet.id() == 0x3B) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,
                    serverProtocol, clientProtocol)
                    .orElse(is763() ? 0x32 : 0x33);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Disconnect（774：0x20、NBT）。""",
               "from774-ping")

    # -- 16. handleServerPlayFrom774：Disconnect ブロック全体を書き直す --
    # （手順 1 で 766-769／770 の組は既に切り出し済みのテキストになっている。）
    text = fix(text,
               """        // Disconnect（774：0x20、NBT）。
        if (packet.id() == 0x1C) {
            closed = true;
            if (is763() || is764()) {
                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());
                int outId = is763() ? 0x1A : 0x1B;
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(outId, payload)), false, true);
            }
            if (is771() || is772() || is773() || is774()) {
                // 771／772／773／774クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }
            if (is766() || is767() || is768() || is769()) {
                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            if (is770()) {
                // 770クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }""",
               """        // Disconnect（774：0x20、NBT）。
        if (packet.id() == 0x20) {
            closed = true;
            if (is763() || is764()) {
                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());
                int outId = is763() ? 0x1A : 0x1B;
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(outId, payload)), false, true);
            }
            if (is773() || is774()) {
                // 773／774クライアント：Disconnect は 0x20（NBT）のまま。
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
            }""",
               "from774-disconnect")

    # -- 17. handleServerPlayFrom774：Respawn ブロック全体を書き直す --
    text = fix(text,
               """        // Respawn（774：0x4B。773（0x4B）と同形式のためそのまま送る）。
        if (packet.id() == 0x4B) {
            if (is773()) {
                // 773クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }""",
               """        // Respawn（774：0x50。773（0x50）と同形式のためそのまま送る）。
        if (packet.id() == 0x50) {
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
            }""",
               "from774-respawn")

    # -- 18. handleServerPlayFrom774：欠けていた 772〜768 分岐を追加 --
    text = fix(text,
               """        // 767クライアント：774→773→772→771→770→769→768へ変換してから769→768へ。
        if (is767()) {
            MinecraftPacket as773 = to773ServerPacketFrom774(packet);
            if (as773 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as772 = to772ServerPacketFrom773(as773);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as770 = to770ServerPacketFrom771(as771);
            if (as770 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as769 = to769ServerPacketFrom770(as770);
            if (as769 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as768 = to768ServerPacket(as769);
            if (as768 == null) {
                return Outcome.drop();
            }
            return handleServerPlay768To767(as768);
        }""",
               """        // 772クライアント：774→773へ変換してから773→772へ。
        if (is772()) {
            MinecraftPacket as773 = to773ServerPacketFrom774(packet);
            if (as773 == null) {
                return Outcome.drop();
            }
            return handleServerPlay773To772(as773);
        }
        // 771クライアント：774→773→772へ変換してから772→771へ。
        if (is771()) {
            MinecraftPacket as773 = to773ServerPacketFrom774(packet);
            if (as773 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as772 = to772ServerPacketFrom773(as773);
            if (as772 == null) {
                return Outcome.drop();
            }
            return handleServerPlay772To771(as772);
        }
        // 770クライアント：774→773→772→771へ変換してから771→770へ。
        if (is770()) {
            MinecraftPacket as773 = to773ServerPacketFrom774(packet);
            if (as773 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as772 = to772ServerPacketFrom773(as773);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            return handleServerPlay771To770(as771);
        }
        // 769クライアント：774→773→772→771へ変換してから771→769へ
        // （771→770→769は handleServerPlay771To769 の内側で変換）。
        if (is769()) {
            MinecraftPacket as773 = to773ServerPacketFrom774(packet);
            if (as773 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as772 = to772ServerPacketFrom773(as773);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            return handleServerPlay771To769(as771);
        }
        // 768クライアント：774→773→772→771へ変換してから771→768へ
        // （771→770→769→768は handleServerPlay771To768 の内側で変換）。
        if (is768()) {
            MinecraftPacket as773 = to773ServerPacketFrom774(packet);
            if (as773 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as772 = to772ServerPacketFrom773(as773);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            return handleServerPlay771To768(as771);
        }
        // 767クライアント：774→773→772→771→770→769→768へ変換してから769→768へ。
        if (is767()) {
            MinecraftPacket as773 = to773ServerPacketFrom774(packet);
            if (as773 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as772 = to772ServerPacketFrom773(as773);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as770 = to770ServerPacketFrom771(as771);
            if (as770 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as769 = to769ServerPacketFrom770(as770);
            if (as769 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as768 = to768ServerPacket(as769);
            if (as768 == null) {
                return Outcome.drop();
            }
            return handleServerPlay768To767(as768);
        }""",
               "from774-tail-branches")

    # -- 19. handleServerJoinGame774：Join Game 0x2B→0x30、Add Resource Pack 0x4A→0x4F --
    text = fix(text,
               """    /**
     * Join Game（内部774：0x2B）のクライアント向け変換。
     * 773と同形式のため、773クライアントにはそのまま送る。
     * それ以外は {@link #handleServerJoinGame773} と同じ方針で各クライアントへ送る。
     */
    private Outcome handleServerJoinGame774(MinecraftPacket packet) {
        if (is773()) {
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x2B, packet.payload()));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4A,""",
               """    /**
     * Join Game（内部774：0x30）のクライアント向け変換。
     * 773と同形式のため、773クライアントにはそのまま送る。
     * それ以外は {@link #handleServerJoinGame773} と同じ方針で各クライアントへ送る。
     */
    private Outcome handleServerJoinGame774(MinecraftPacket packet) {
        if (is773()) {
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x30, packet.payload()));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,""",
               "joingame774-ids")

    # -- 20. handleServerPlayFrom770：is772 分岐が一段短い（770→771 を落としていた） --
    text = fix(text,
               """        // 773クライアント：770→771→772→773へ三段で変換する。
        if (is773()) {
            return handleServerPlay770To773(packet);
        }
        // 772クライアント：771→772へ変換する。
        if (is772()) {
            return handleServerPlay771To772(packet);
        }""",
               """        // 773クライアント：770→771→772→773へ三段で変換する。
        if (is773()) {
            return handleServerPlay770To773(packet);
        }
        // 772クライアント：770→771へ変換してから771→772へ。
        if (is772()) {
            Outcome to771 = handleServerPlay770To771(packet);
            if (to771.toClient().isEmpty()) {
                return to771;
            }
            return handleServerPlay771To772(to771.toClient().get(0));
        }""",
               "from770-is772-chain")

    # -- 21. handleServerPlayFrom766：Disconnect に 770／771／772／774 分岐を追加 --
    text = fix(text,
               """            if (is773()) {
                // 773クライアント：Disconnect は 0x20（NBT）。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);
            }
            return new Outcome(List.of(),
                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);
        }
        // Respawn（766：0x47）。""",
               """            if (is773() || is774()) {
                // 773／774クライアント：Disconnect は 0x20（NBT）。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);
            }
            if (is770() || is771() || is772()) {
                // 770／771／772クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }
            return new Outcome(List.of(),
                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);
        }
        // Respawn（766：0x47）。""",
               "from766-disconnect")

    # -- 22. handleServerPlayFrom766：Respawn に 770／771／772／774 分岐を追加 --
    text = fix(text,
               """            if (is773()) {
                // 773クライアント：766の Respawn は767と同一形式のため、
                // 767→768へ直して 0x50 で送る。
                return Outcome.toClient(new MinecraftPacket(0x50,
                        PlayTranslator.translateRespawn767To768(
                                packet.payload(), registries)));
            }""",
               """            if (is773() || is774()) {
                // 773／774クライアント：766の Respawn は767と同一形式のため、
                // 767→768へ直して 0x50 で送る。
                return Outcome.toClient(new MinecraftPacket(0x50,
                        PlayTranslator.translateRespawn767To768(
                                packet.payload(), registries)));
            }
            if (is770() || is771() || is772()) {
                // 770／771／772クライアント：766の Respawn は767と同一形式のため、
                // 767→768へ直して 0x4B で送る。
                return Outcome.toClient(new MinecraftPacket(0x4B,
                        PlayTranslator.translateRespawn767To768(
                                packet.payload(), registries)));
            }""",
               "from766-respawn")

    # -- 23. registries 蓄積の reorder target（769向けなのに 770／771／772） --
    text = fix(text,
               """                // 769向けの辞書は769形式で蓄える（772と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_5));""",
               """                // 769向けの辞書は769形式で蓄える（772と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_4));""",
               "reorder-769-from-772")
    text = fix(text,
               """                // 769向けの辞書は769形式で蓄える（773と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6));""",
               """                // 769向けの辞書は769形式で蓄える（773と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_4));""",
               "reorder-769-from-773")
    text = fix(text,
               """                // 769向けの辞書は769形式で蓄える（774と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_7));""",
               """                // 769向けの辞書は769形式で蓄える（774と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_4));""",
               "reorder-769-from-774")

    # -- 24. handleClientConfiguration774：重複 isServer772() の除去 --
    text = fix(text,
               "            if (isServer772() || isServer771() || isServer772() || isServer773()) {",
               "            if (isServer771() || isServer772() || isServer773()) {",
               "client-config-774-dup")
    print("後半（メソッド・ディスパッチ） OK")
    P.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
