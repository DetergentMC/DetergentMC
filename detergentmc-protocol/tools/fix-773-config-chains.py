#!/usr/bin/env python3
"""ProxySession.java の設定フェーズの773連鎖飛ばしを修正する（2026-11-12発見）。

背景：gen-773-proxysession.py が772の分岐を名前だけで写したため、
771／770クライアントの isServer773() 分岐が一段短い
handleServerConfiguration772From773 を呼んでいた。
773形式の Registry Data を772形式に直しただけで770／771クライアントへ
送るため、レジストリの順序が一段ずれる。例えば771クライアントは
jukebox_song を21件（lava_chicken 付き）で受け取るが、771本来の
レジストリは20件（lava_chicken 無し）で、lava_chicken 以降の歌の
holder 番号が1つずれる。769／768／767／766クライアントには
handleServerConfiguration769From773 等の正しい多段ハンドラがあるのに、
771／770だけ欠けていた（同名のメソッドが存在しない）。

修正：
- handleServerConfiguration771From773 を新設（773→772→771の二段。
  dialog は771にもあるため素通し）
- handleServerConfiguration770From773 を新設（773→772→771→770の三段。
  dialog は770に無いため落とす）
- ディスパッチの771／770クライアントの isServer773() 分岐をそれらへ向ける。

注意：handleServerConfiguration769From773 の辞書並べ替えは
reorderRegistries(parsed, V1_21_9, V1_21_6)（771形式）になっているが、
バイオームの順序は769〜771で同じため実害は無い。ここでは直さない。

使い方：リポジトリ直下で python3 detergentmc-protocol/tools/fix-773-config-chains.py
各置換は件数を確認してから書き戻す（失敗時は書き戻さない）。
"""

import sys

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"

METHOD_771 = '''    /**
     * 771クライアント＋内部773：設定フェーズを中継する。
     * 771と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771へ二段で並べ替え（jukebox_song の
     * lava_chicken は落とす）、Tags はレジストリ番号がずれるため
     * 落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771にもあるため
     * 素通し。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     * 771クライアントの isServer773() 分岐は生成器が一段目（773→772の次段）
     * を落としていたため、このメソッド経由で呼ぶ
     * （fix-773-config-chains.py）。
     */
    private Outcome handleServerConfiguration771From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 771向けの辞書は771形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6));
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
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
            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

'''

METHOD_770 = '''    /**
     * 770クライアント＋内部773：設定フェーズを中継する。
     * 770と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771→770へ三段で並べ替え（minecraft:dialog と
     * jukebox_song の lava_chicken／tears は落とす）、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は770に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     * 770クライアントの isServer773() 分岐は生成器が二段目まで（773→772の次段）
     * を落としていたため、このメソッド経由で呼ぶ
     * （fix-773-config-chains.py）。
     */
    private Outcome handleServerConfiguration770From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。770も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を770の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 770向けの辞書は770形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_5));
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
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
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

'''


def replace_once(src, old, new, what):
    count = src.count(old)
    if count != 1:
        raise SystemExit(f"{what}: expected 1 occurrence, found {count}")
    return src.replace(old, new, 1)


def main():
    with open(PATH, encoding="utf-8") as f:
        src = f.read()
    orig = src

    if "handleServerConfiguration771From773" in src:
        raise SystemExit("handleServerConfiguration771From773 already exists")
    if "handleServerConfiguration770From773" in src:
        raise SystemExit("handleServerConfiguration770From773 already exists")

    # 新しい2メソッドを 769From773 の javadoc の直前に挿入する。
    anchor = (
        "    /**\n"
        "     * 769クライアント＋内部773：設定フェーズを中継する。\n"
    )
    src = replace_once(src, anchor, METHOD_771 + METHOD_770 + anchor,
                       "insert config chain methods")
    print("OK insert handleServerConfiguration771From773 + 770From773")

    # 771クライアントの isServer773() 分岐（直前は isServer770() 分岐で区別）。
    src = replace_once(
        src,
        "            if (isServer770()) {\n"
        "                return handleServerConfiguration771From770(packet);\n"
        "            }\n"
        "            if (isServer773()) {\n"
        "                return handleServerConfiguration772From773(packet);\n"
        "            }",
        "            if (isServer770()) {\n"
        "                return handleServerConfiguration771From770(packet);\n"
        "            }\n"
        "            if (isServer773()) {\n"
        "                return handleServerConfiguration771From773(packet);\n"
        "            }",
        "dispatch 771-client isServer773")
    print("OK dispatch 771-client isServer773 -> 771From773")

    # 770クライアントの isServer773() 分岐（直前はネイティブ分岐で区別）。
    src = replace_once(
        src,
        "            if (isServer770()) {\n"
        "                // 内部770＋770クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            if (isServer773()) {\n"
        "                return handleServerConfiguration772From773(packet);\n"
        "            }",
        "            if (isServer770()) {\n"
        "                // 内部770＋770クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            if (isServer773()) {\n"
        "                return handleServerConfiguration770From773(packet);\n"
        "            }",
        "dispatch 770-client isServer773")
    print("OK dispatch 770-client isServer773 -> 770From773")

    if src == orig:
        raise SystemExit("no changes made")
    with open(PATH, "w", encoding="utf-8") as f:
        f.write(src)
    print("DONE: wrote " + PATH)


if __name__ == "__main__":
    main()
