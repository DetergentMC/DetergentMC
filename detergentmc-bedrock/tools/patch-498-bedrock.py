#!/usr/bin/env python3
"""Bedrock 側に 498（1.14.4）バックエンドの配線を足す。

573 のときと違い、498 は 578 と形式が完全一致ではないため、
形式が同じ分岐には 498 を足し、違う箇所は専用に分ける：
- Join Game：hashedSeed が無いため parseJoinGame498 で読む。
- Spawn Entity Living：末尾メタデータがあるため parseSpawnEntityLiving498
  （読み飛ばす）で読む。
- チャンク：バイオームがバッファ末尾の256件のため translate498 を新設。

対象: JavaBackendConnection / BedrockChunkTranslator /
BedrockEntityTranslator。使い方:
    python3 tools/patch-498-bedrock.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "src/main/java/io/detergentmc/bedrock"


def sub(text, old, new, count, where):
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def patch_backend():
    p = BASE / "JavaBackendConnection.java"
    text = p.read_text(encoding="utf-8")
    if "serverProtocol == 498" in text:
        print("skip（既存）: JavaBackendConnection")
        return
    # Chat 送信・コマンド送信・Login Start（すべて 498 も同じ旧式形式）。
    text = sub(text,
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573) {",
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498) {",
               3, "backend")
    # Login Success（設定フェーズ無し）。
    text = sub(text,
               "if (serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               "if (serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               1, "backend")
    # 受信チャット（旧式 {JSON, position} は 498 も同じ）。
    text = sub(text,
               "            PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573\n                    ? extractOldChat578(packet.payload())",
               "            PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498\n                    ? extractOldChat578(packet.payload())",
               1, "backend")
    # Player Info（旧式 action 形式。crypto は 498 にも無い）。
    text = sub(text,
               "if (serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               "if (serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               1, "backend")
    text = sub(text,
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573);",
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573 && serverProtocol != 498);",
               1, "backend")
    # Respawn の次元（先頭 i32 は 498 も同じ）。
    text = sub(text,
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573) {",
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498) {",
               1, "backend")
    # Spawn Entity Living（498 は末尾メタデータ付きのため専用パース）。
    text = sub(text,
               "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573\n                        ? BedrockEntityTranslator.parseSpawnEntityLiving578(packet.payload())",
               "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 498\n                        ? BedrockEntityTranslator.parseSpawnEntityLiving498(packet.payload())\n                        : serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573\n                        ? BedrockEntityTranslator.parseSpawnEntityLiving578(packet.payload())",
               1, "backend")
    # Named Entity Spawn（498 も末尾メタデータ付き。578 用パースは読み飛ばすため共用できる）。
    text = sub(text,
               "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573\n                        ? BedrockEntityTranslator.parseNamedEntitySpawn578(packet.payload())",
               "                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498\n                        ? BedrockEntityTranslator.parseNamedEntitySpawn578(packet.payload())",
               1, "backend")
    # Join Game（498 専用分岐を 578 分岐の前に追加）。
    text = sub(text,
               "    private void parseJoinGame(byte[] payload) {\n"
               "        if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573) {",
               "    private void parseJoinGame(byte[] payload) {\n"
               "        if (serverProtocol == 498) {\n"
               "            // 498 の Join Game は次元が i32 の ID で、codec は無い\n"
               "            // （parseJoinGame498。578 と違い hashedSeed が無い）。\n"
               "            // 次元は minY 0・高さ 256、バイオームは 498 の組み込み\n"
               "            // レジストリ順を使う。\n"
               "            PlayTranslator.JoinGame498 join = PlayTranslator.parseJoinGame498(payload);\n"
               "            dimensionTypes.put(\"minecraft:overworld\", new int[]{0, 256});\n"
               "            dimensionTypes.put(\"minecraft:the_nether\", new int[]{0, 256});\n"
               "            dimensionTypes.put(\"minecraft:the_end\", new int[]{0, 256});\n"
               "            biomeNames = RegistryRemapper.biomeNames498();\n"
               "            setDimension(join.dimension() == -1 ? \"minecraft:the_nether\"\n"
               "                    : join.dimension() == 1 ? \"minecraft:the_end\"\n"
               "                    : \"minecraft:overworld\");\n"
               "            return;\n"
               "        }\n"
               "        if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573) {",
               1, "backend")
    p.write_text(text, encoding="utf-8")
    print("JavaBackendConnection.java を更新した")


def patch_chunk():
    p = BASE / "BedrockChunkTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_498" in text:
        print("skip（既存）: BedrockChunkTranslator")
        return
    # normalizeBlockState の 573 ケースを読み、498 ケースを前に足す。
    start = text.index("            case RegistryRemapper.PROTOCOL_573 -> {")
    end = text.index("            case RegistryRemapper.PROTOCOL_575 -> {")
    block573 = text[start:end]
    block498 = (block573
                .replace("PROTOCOL_573", "PROTOCOL_498")
                .replace("mapBlockState573To758", "mapBlockState498To758")
                .replace("573 → 758", "498 → 758"))
    text = text[:start] + block498 + text[start:]
    # translate() の振り分けに 498 を足す。
    text = sub(text,
               "    public static TranslatedChunk translate(ChunkInput input) {\n"
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_578",
               "    public static TranslatedChunk translate(ChunkInput input) {\n"
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_498) {\n"
               "            return translate498(input);\n"
               "        }\n"
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_578",
               1, "chunk")
    # translate498 本体を translate578 の直前に追加する。
    anchor = "    private static TranslatedChunk translate578(ChunkInput input) {"
    body = '''    /**
     * 498 の Chunk Data を読む。578 との違い：バイオームは別フィールド
     * （1024件）ではなく、データバッファの末尾に列ごと256件が入る。
     * セクションの読み方は 578 と同じ（境界またぎ詰め）。
     */
    private static TranslatedChunk translate498(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        boolean groundUp = reader.readBoolean();
        int mask = reader.readVarInt();
        reader.readNamedNbt(); // heightmaps（Bedrock 変換では使わない）
        int size = reader.readVarInt();
        if (size < 0 || size > reader.remaining()) {
            throw new IllegalArgumentException("498 チャンクのデータ長が不正です: " + size);
        }
        PacketIO.Reader sections = new PacketIO.Reader(reader.readBytes(size));
        List<int[]> blockSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            if ((mask & (1 << s)) == 0) {
                blockSections.add(airBlocks());
            } else {
                sections.readShort(); // blockCount（使わない）
                int bits = sections.readUnsignedByte();
                int[] palette = null;
                if (bits <= 8) {
                    int paletteLength = sections.readVarInt();
                    palette = new int[paletteLength];
                    for (int i = 0; i < paletteLength; i++) {
                        palette[i] = sections.readVarInt();
                    }
                }
                int dataLength = sections.readVarInt();
                long[] longs = new long[dataLength];
                for (int i = 0; i < dataLength; i++) {
                    longs[i] = sections.readLong();
                }
                int[] states = new int[SECTION_BLOCKS];
                if (bits == 0) {
                    int only = palette != null && palette.length > 0 ? palette[0] : 0;
                    java.util.Arrays.fill(states, only);
                } else {
                    int valueBits = palette != null ? bits : 14;
                    for (int i = 0; i < SECTION_BLOCKS; i++) {
                        long bitPos = (long) i * valueBits;
                        int li = (int) (bitPos >>> 6);
                        int off = (int) (bitPos & 63);
                        long v;
                        if (off + valueBits <= 64) {
                            v = (longs[li] >>> off) & ((1L << valueBits) - 1);
                        } else {
                            int rem = 64 - off;
                            v = ((longs[li] >>> off) | (longs[li + 1] << rem))
                                    & ((1L << valueBits) - 1);
                        }
                        int raw = (int) v;
                        states[i] = palette != null
                                ? (raw < palette.length ? palette[raw] : 0) : raw;
                    }
                }
                blockSections.add(states);
            }
        }
        int[] biomes = null;
        if (groundUp) {
            biomes = new int[256];
            for (int i = 0; i < 256; i++) {
                biomes[i] = sections.readInt();
            }
        }
        List<int[]> biomeSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            int[] biomeSection = new int[SECTION_BIOMES];
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        int value = biomes == null ? 1
                                : biomes[(qz * 4) * 16 + qx * 4];
                        biomeSection[(qy << 4) | (qz << 2) | qx] = value;
                    }
                }
            }
            biomeSections.add(biomeSection);
        }
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        for (int i = 0; i < 16; i++) {
            writeSubChunk(out, blockSections.get(i), i, input.serverProtocol());
        }
        for (int i = 0; i < 16; i++) {
            writeBiomeStorage(out, biomeSections.get(i), input.biomeNames());
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        return new TranslatedChunk(chunkX, chunkZ, out.bytes(), 16);
    }

'''
    text = sub(text, anchor, body + anchor, 1, "chunk")
    p.write_text(text, encoding="utf-8")
    print("BedrockChunkTranslator.java を更新した")


def patch_entity():
    p = BASE / "BedrockEntityTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_498" in text:
        print("skip（既存）: BedrockEntityTranslator")
        return
    # normalizeEntityType の 573 ケースを読み、498 ケースを前に足す。
    start = text.index("            case RegistryRemapper.PROTOCOL_573 -> {")
    end = text.index("            case RegistryRemapper.PROTOCOL_575 -> {", start)
    block573 = text[start:end]
    block498 = (block573
                .replace("PROTOCOL_573", "PROTOCOL_498")
                .replace("mapEntityType573To758", "mapEntityType498To758")
                .replace("573 → 758", "498 → 758"))
    text = text[:start] + block498 + text[start:]
    # normalizeItem の 573 ケース（2 件目）。
    first = text.index("            case RegistryRemapper.PROTOCOL_573 -> {")
    start = text.index("            case RegistryRemapper.PROTOCOL_573 -> {", first + 1)
    end = text.index("            case RegistryRemapper.PROTOCOL_575 -> {", start)
    block573 = text[start:end]
    block498 = (block573
                .replace("PROTOCOL_573", "PROTOCOL_498")
                .replace("mapItem573To758", "mapItem498To758")
                .replace("573 → 758", "498 → 758"))
    text = text[:start] + block498 + text[start:]
    # Spawn Entity の headPitch 無し分岐（498 も同じ形式）。
    text = sub(text,
               "                || serverProtocol == RegistryRemapper.PROTOCOL_575\n"
               "                || serverProtocol == RegistryRemapper.PROTOCOL_573) {",
               "                || serverProtocol == RegistryRemapper.PROTOCOL_575\n"
               "                || serverProtocol == RegistryRemapper.PROTOCOL_573\n"
               "                || serverProtocol == RegistryRemapper.PROTOCOL_498) {",
               1, "entity")
    # parseSpawnEntityLiving498（末尾メタデータを読み飛ばす）。
    text = sub(text,
               "    /** Spawn Entity Living（578、0x03）を読む。形式は 758 と同一。 */\n"
               "    public static SpawnEntity parseSpawnEntityLiving578(byte[] payload) {",
               "    /**\n"
               "     * Spawn Entity Living（498）を読む。形式は 578 と同じだが、\n"
               "     * 末尾にエンティティメタデータが付く（ここでは読み飛ばす）。\n"
               "     */\n"
               "    public static SpawnEntity parseSpawnEntityLiving498(byte[] payload) {\n"
               "        PacketIO.Reader reader = new PacketIO.Reader(payload);\n"
               "        int entityId = reader.readVarInt();\n"
               "        byte[] uuidBytes = reader.readBytes(16);\n"
               "        int type = normalizeEntityType(reader.readVarInt(), RegistryRemapper.PROTOCOL_498);\n"
               "        double x = reader.readDouble();\n"
               "        double y = reader.readDouble();\n"
               "        double z = reader.readDouble();\n"
               "        int yaw = reader.readByte();\n"
               "        int pitch = reader.readByte();\n"
               "        int headPitch = reader.readByte();\n"
               "        int velX = reader.readShort();\n"
               "        int velY = reader.readShort();\n"
               "        int velZ = reader.readShort();\n"
               "        // 末尾のメタデータは読み飛ばす（Bedrock 変換では使わない）。\n"
               "        return new SpawnEntity(entityId,\n"
               "                new UUID(bytesToLong(uuidBytes, 0), bytesToLong(uuidBytes, 8)),\n"
               "                type, x, y, z, byteToAngle(pitch), byteToAngle(yaw), byteToAngle(headPitch),\n"
               "                0, shortVelocityToFloat(velX), shortVelocityToFloat(velY),\n"
               "                shortVelocityToFloat(velZ));\n"
               "    }\n"
               "\n"
               "    /** Spawn Entity Living（578、0x03）を読む。形式は 758 と同一。 */\n"
               "    public static SpawnEntity parseSpawnEntityLiving578(byte[] payload) {",
               1, "entity")
    p.write_text(text, encoding="utf-8")
    print("BedrockEntityTranslator.java を更新した")


def main():
    patch_backend()
    patch_chunk()
    patch_entity()
    return 0


if __name__ == "__main__":
    sys.exit(main())
