#!/usr/bin/env python3
"""767サーバー→766クライアントのプレイ変換（handleServerPlay767To766）を追加する。
handleServerPlayFrom766 のフォールバックで、766クライアント＋内部767の
場合に呼ぶ（766クライアント＋内部766はネイティブのためここには来ない）。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PATH = ROOT / "src/main/java/io/detergentmc/protocol/ProxySession.java"


def replace_once(text, old, new):
    if text.count(old) != 1:
        print(f"REPLACE 失敗（{text.count(old)}件）: {old[:90]!r}")
        sys.exit(1)
    return text.replace(old, new, 1)


def insert_once(text, anchor, block):
    if text.count(anchor) != 1:
        print(f"ANCHOR 失敗（{text.count(anchor)}件）: {anchor[:90]!r}")
        sys.exit(1)
    return text.replace(anchor, block + "\n" + anchor, 1)


def main():
    text = PATH.read_text(encoding="utf-8")
    if "handleServerPlay767To766" in text:
        print("既に挿入済みのため何もしません。")
        return

    # フォールバックの振り分け：766クライアント＋内部767は767→766へ変換する。
    text = replace_once(text,
        "        // 767クライアント：スロット等は766→767へ変換する。\n"
        "        if (is767()) {\n"
        "            return handleServerPlay766To767(packet);\n"
        "        }",
        "        // 767クライアント：スロット等は766→767へ変換する。\n"
        "        if (is767()) {\n"
        "            return handleServerPlay766To767(packet);\n"
        "        }\n"
        "        // 766クライアント＋内部767：スロット等は767→766へ変換する。\n"
        "        if (is766() && isServer767()) {\n"
        "            return handleServerPlay767To766(packet);\n"
        "        }")

    method = """
    /**
     * 内部767→766クライアントのプレイ状態（サーバー→クライアント）。
     * スロットを含むパケットは767→766へ変換する（同ID）。
     * チャンク・ブロック変更・ブロックエンティティはブロック状態・
     * バイオーム・ブロックエンティティ種別が766と767で同一のため素通しする。
     * Declare Recipes／Statistics／Tags／World Event ID／Advancements などは
     * レジストリ番号・スロット変換が要るため落とす（766向けと同じ方針）。
     * Join Game／Spawn Position／Keep Alive／Ping／Disconnect／Respawn／
     * Start Configuration はここに来る前に処理済みである。
     */
    private Outcome handleServerPlay767To766(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, 767).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return Outcome.drop();
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA -> ChunkTranslator.translateEntityMetadata767To766(
                    packet.payload());
            case PLAY_SET_SLOT -> ChunkTranslator.translateSetSlot767To766(packet.payload());
            case PLAY_WINDOW_ITEMS -> ChunkTranslator.translateWindowItems767To766(
                    packet.payload());
            case PLAY_SET_COOLDOWN -> ChunkTranslator.translateSetCooldown767To766(
                    packet.payload());
            case PLAY_ENTITY_EQUIPMENT -> ChunkTranslator.translateEntityEquipment767To766(
                    packet.payload());
            case PLAY_TRADE_LIST -> ChunkTranslator.translateTradeList767To766(
                    packet.payload());
            case PLAY_OPEN_WINDOW -> ChunkTranslator.translateOpenWindow767To766(
                    packet.payload());
            case PLAY_BLOCK_ACTION -> ChunkTranslator.translateBlockAction767To766(
                    packet.payload());
            case PLAY_SPAWN_ENTITY -> ChunkTranslator.translateSpawnEntity767To766(
                    packet.payload());
            case PLAY_SOUND_EFFECT -> ChunkTranslator.translateSoundEffect767To766(
                    packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT -> ChunkTranslator.translateEntitySoundEffect767To766(
                    packet.payload());
            case PLAY_STOP_SOUND -> ChunkTranslator.translateStopSound767To766(
                    packet.payload());
            case PLAY_WORLD_PARTICLES -> ChunkTranslator.translateWorldParticles767To766(
                    packet.payload());
            case PLAY_ENTITY_EFFECT -> ChunkTranslator.translateEntityEffect767To766(
                    packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT -> ChunkTranslator.translateRemoveEntityEffect767To766(
                    packet.payload());
            case PLAY_DAMAGE_EVENT -> ChunkTranslator.translateDamageEvent767To766(
                    packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES -> ChunkTranslator
                    .translateUpdateAttributes767To766(packet.payload());
            case PLAY_CHUNK_DATA, PLAY_BLOCK_CHANGE, PLAY_MULTI_BLOCK_CHANGE,
                    PLAY_BLOCK_ENTITY_DATA -> packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return Outcome.drop();
        }
        return Outcome.toClient(new MinecraftPacket(packet.id(), translated));
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 内部764→767クライアントのプレイ状態（サーバー→クライアント）。",
        method)

    PATH.write_text(text, encoding="utf-8")
    print("handleServerPlay767To766 を追加しました。")


if __name__ == "__main__":
    main()
