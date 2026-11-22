#!/usr/bin/env python3
"""Bedrock → 4（1.7.2）バックエンドの配線。

4 のワイヤ形式は 5 と同一（チャンク・メタデータ・スポーン系）だが、
named_entity_spawn は UUID がダッシュ無し文字列でプロパティ配列が
無い。よって大半は 5 の分岐へ合流させ、named だけ専用パースを足す。
498 用関数へは流さない。
冪等ではない（実行は1回だけ）。件数が合わなければ中止する。
"""
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "bedrock"


def rep(src, old, new, expect=1):
    n = src.count(old)
    assert n == expect, f"{n} != {expect}: {old[:70]}"
    print(f"ok {expect} x {old[:60]}")
    return src.replace(old, new)


# --- BedrockChunkTranslator ---
p = BASE / "BedrockChunkTranslator.java"
src = p.read_text(encoding="utf-8")
src = rep(src, "            case RegistryRemapper.PROTOCOL_5 -> {",
          "            case RegistryRemapper.PROTOCOL_5, RegistryRemapper.PROTOCOL_4 -> {")
src = rep(src, "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_5) {",
          "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_5\n"
          "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_4) {")
p.write_text(src, encoding="utf-8")
print("written BedrockChunkTranslator")

# --- BedrockEntityTranslator ---
p = BASE / "BedrockEntityTranslator.java"
src = p.read_text(encoding="utf-8")
src = rep(src, "            case RegistryRemapper.PROTOCOL_5 -> {",
          "            case RegistryRemapper.PROTOCOL_5, RegistryRemapper.PROTOCOL_4 -> {",
          expect=2)
# parseSpawnEntity4: parseSpawnEntity5 の直前に委譲関数を足す
src = rep(src, "    public static SpawnEntity parseSpawnEntity5(byte[] payload) {",
          """    /**
     * 4（1.7.2〜1.7.5）の Spawn Entity（オブジェクト系）を読む。
     * ワイヤ形式は 5 と完全同一のため 5 用へ委譲する。
     */
    public static SpawnEntity parseSpawnEntity4(byte[] payload) {
        return parseSpawnEntity5(payload);
    }

    public static SpawnEntity parseSpawnEntity5(byte[] payload) {""")
src = rep(src, "    public static SpawnEntity parseSpawnEntityLiving5(byte[] payload) {",
          """    /**
     * 4（1.7.2〜1.7.5）の Spawn Entity Living を読む。ワイヤ形式は
     * 5 と完全同一（UUID 無し・種別 u8・i32 固定小数点）で、種別表も
     * 5 と同一のため 5 用へ委譲する（498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving4(byte[] payload) {
        return parseSpawnEntityLiving5(payload);
    }

    public static SpawnEntity parseSpawnEntityLiving5(byte[] payload) {""")
src = rep(src, "    public static SpawnEntity parseNamedEntitySpawn5(byte[] payload) {",
          """    /**
     * Named Entity Spawn（4）を読む。5 と違い UUID はダッシュ無し
     * 32 文字の文字列で、プロパティ配列が無い（名前の直後が座標）。
     * 座標は i32 固定小数点（×32）で、角度の後に currentItem（i16）
     * とメタデータが付く（どちらも読み飛ばす）。
     */
    public static SpawnEntity parseNamedEntitySpawn4(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        UUID uuid = io.detergentmc.protocol.PlayTranslator.uuidFromString4(reader.readString());
        reader.readString(); // 名前（SpawnEntity には載せない）
        double x = reader.readInt() / 32.0;
        double y = reader.readInt() / 32.0;
        double z = reader.readInt() / 32.0;
        int yaw = reader.readByte();
        int pitch = reader.readByte();
        reader.readShort(); // currentItem（使わない）
        // 末尾のメタデータは読み飛ばす。
        return new SpawnEntity(entityId, uuid,
                TYPE_PLAYER, x, y, z, byteToAngle(pitch), byteToAngle(yaw), byteToAngle(yaw),
                0, 0.0, 0.0, 0.0);
    }

    public static SpawnEntity parseNamedEntitySpawn5(byte[] payload) {""")
src = rep(src, "    public static SpawnExperienceOrb parseSpawnExperienceOrb5(byte[] payload) {",
          """    /** 4 の Spawn Experience Orb を読む（形式は 5 と同一）。 */
    public static SpawnExperienceOrb parseSpawnExperienceOrb4(byte[] payload) {
        return parseSpawnExperienceOrb5(payload);
    }

    public static SpawnExperienceOrb parseSpawnExperienceOrb5(byte[] payload) {""")
p.write_text(src, encoding="utf-8")
print("written BedrockEntityTranslator")

# --- JavaBackendConnection ---
p = BASE / "JavaBackendConnection.java"
src = p.read_text(encoding="utf-8")
src = rep(src, "serverProtocol == 47 || serverProtocol == 5)",
          "serverProtocol == 47 || serverProtocol == 5 || serverProtocol == 4)",
          expect=3)
src = rep(src, "serverProtocol == 47 || serverProtocol == 5 || serverProtocol == 393",
          "serverProtocol == 47 || serverProtocol == 5 || serverProtocol == 4 || serverProtocol == 393")
src = rep(src, "serverProtocol == 47 || serverProtocol == 5\n",
          "serverProtocol == 47 || serverProtocol == 5 || serverProtocol == 4\n")
src = rep(src, "            if (serverProtocol == 5) {\n                // 5 の Player Info は名前ベースの旧形式（UUID を持たない）。",
          "            if (serverProtocol == 5 || serverProtocol == 4) {\n                // 5／4 の Player Info は名前ベースの旧形式（UUID を持たない）。")
src = rep(src, "                } else if (serverProtocol == 5) {",
          "                } else if (serverProtocol == 5 || serverProtocol == 4) {")
src = rep(src, "                for (byte[] chunk : serverProtocol == 5\n",
          "                for (byte[] chunk : serverProtocol == 5 || serverProtocol == 4\n")
src = rep(src, "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 5\n"
               "                        ? BedrockEntityTranslator.parseSpawnEntity5(packet.payload())",
          "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 4\n"
          "                        ? BedrockEntityTranslator.parseSpawnEntity4(packet.payload())\n"
          "                        : serverProtocol == 5\n"
          "                        ? BedrockEntityTranslator.parseSpawnEntity5(packet.payload())")
src = rep(src, "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 5\n"
               "                        ? BedrockEntityTranslator.parseSpawnEntityLiving5(packet.payload())",
          "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 4\n"
          "                        ? BedrockEntityTranslator.parseSpawnEntityLiving4(packet.payload())\n"
          "                        : serverProtocol == 5\n"
          "                        ? BedrockEntityTranslator.parseSpawnEntityLiving5(packet.payload())")
src = rep(src, "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 5\n"
               "                        ? BedrockEntityTranslator.parseNamedEntitySpawn5(packet.payload())",
          "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 4\n"
          "                        ? BedrockEntityTranslator.parseNamedEntitySpawn4(packet.payload())\n"
          "                        : serverProtocol == 5\n"
          "                        ? BedrockEntityTranslator.parseNamedEntitySpawn5(packet.payload())")
src = rep(src, "                listener.onSpawnExperienceOrb(serverProtocol == 5\n"
               "                        ? BedrockEntityTranslator.parseSpawnExperienceOrb5(packet.payload())",
          "                listener.onSpawnExperienceOrb(serverProtocol == 4\n"
          "                        ? BedrockEntityTranslator.parseSpawnExperienceOrb4(packet.payload())\n"
          "                        : serverProtocol == 5\n"
          "                        ? BedrockEntityTranslator.parseSpawnExperienceOrb5(packet.payload())")
src = rep(src, "        if (serverProtocol == 5) {\n            // 5 の Join Game は末尾の reducedDebugInfo が無い 16 バイト",
          "        if (serverProtocol == 5 || serverProtocol == 4) {\n            // 5／4 の Join Game は末尾の reducedDebugInfo が無い 16 バイト")
p.write_text(src, encoding="utf-8")
print("written JavaBackendConnection")
