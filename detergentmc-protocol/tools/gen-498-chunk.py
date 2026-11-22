#!/usr/bin/env python3
"""ChunkTranslator の 578 メソッド群から 498 版を生成し、498 セクションとして挿入する。

機械コピーできるメソッド（レジストリ付け替えだけのもの）は 578 のソースを
抽出して 578→498 に置換する。形式が違うもの（チャンクのバイオーム配置、
Spawn Living の末尾メタデータ、World Particles の f32 座標と末尾データ）は
このスクリプト内に手書きの Java として持つ。
"""
import re
import sys

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java"
src = open(PATH).read()
lines = src.split("\n")


def extract(signature):
    """signature を含むメソッド（直前の javadoc／コメント込み）を抽出する。"""
    for i, line in enumerate(lines):
        if signature in line and ("static" in line or "record" in line):
            start = i
            # 直前の javadoc ブロックを取り込む。
            j = i - 1
            if j >= 0 and lines[j].strip() == "*/":
                k = j
                while k >= 0 and not lines[k].strip().startswith("/**"):
                    k -= 1
                if k >= 0:
                    start = k
            # 中括弧の対応で終端を探す。
            depth = 0
            end = None
            for m in range(i, len(lines)):
                depth += lines[m].count("{") - lines[m].count("}")
                if m > i and depth <= 0:
                    end = m
                    break
                if m == i and depth == 0 and lines[m].rstrip().endswith(";"):
                    end = m
                    break
            if end is None:
                raise SystemExit("終端が見つかりません: " + signature)
            return "\n".join(lines[start:end + 1]), end
    raise SystemExit("メソッドが見つかりません: " + signature)


COPY_METHODS = [
    "translateSlot578To758(Reader reader, Writer writer)",
    "translateSlot758To578(Reader reader, Writer writer)",
    "blockEntityName578(int type758)",
    "indexOfBlockEntityName578(String name)",
    "translateSpawnEntity578To758(byte[] payload)",
    "translateSpawnEntity758To578(byte[] payload)",
    "readSpawnEntityType578(byte[] payload)",
    "spawnEntity758ToWeather578(byte[] payload)",
    "spawnWeather578ToEntity758(byte[] payload)",
    "namedEntitySpawn578To758(byte[] payload)",
    "translateEntityMetadata578To758(byte[] payload)",
    "translateEntityMetadata758To578(byte[] payload)",
    "translateEntityMetadata578(byte[] payload, boolean to578)",
    "translateMetadataValue578(Reader reader, Writer writer, int type,",
    "translateParticle578(Reader reader, Writer writer, boolean to578)",
    "translateSoundEffect578To758(byte[] payload)",
    "translateSoundEffect758To578(byte[] payload)",
    "translateSoundEffect578(byte[] payload, boolean to578)",
    "translateEntitySoundEffect578To758(byte[] payload)",
    "translateEntitySoundEffect758To578(byte[] payload)",
    "translateEntitySoundEffect578(byte[] payload, boolean to578)",
    "translateNamedSoundEffect578To758(byte[] payload)",
    "translateNamedSoundEffect758To578(byte[] payload)",
    "translateNamedSoundEffect578(byte[] payload, boolean to578)",
    "translateStopSound578To758(byte[] payload)",
    "translateStopSound758To578(byte[] payload)",
    "translateStopSound578(byte[] payload, boolean to578)",
    "translateBlockChange578To758(byte[] payload)",
    "translateBlockChange758To578(byte[] payload)",
    "translateMultiBlockChange578To758(byte[] payload)",
    "translateMultiBlockChange758To578(byte[] payload)",
    "translateBlockEntityData578To758(byte[] payload)",
    "translateBlockEntityData758To578(byte[] payload)",
    "translateSetSlot578To758(byte[] payload)",
    "translateSetSlot758To578(byte[] payload)",
    "translateWindowItems578To758(byte[] payload)",
    "translateWindowItems758To578(byte[] payload)",
    "translateEntityEquipment758To578(byte[] payload)",
    "translateEntityEquipment578To758(byte[] payload)",
    "translateTradeList578To758(byte[] payload)",
    "translateTradeList758To578(byte[] payload)",
    "translateOpenWindow578To758(byte[] payload)",
    "translateOpenWindow758To578(byte[] payload)",
    "translateSetCooldown578To758(byte[] payload)",
    "translateSetCooldown758To578(byte[] payload)",
    "translateBlockAction578To758(byte[] payload)",
    "translateBlockAction758To578(byte[] payload)",
    "translateUpdateLight578To758(byte[] payload)",
    "translateUpdateLight758To578(byte[] payload)",
    "translateWindowClick578To758(byte[] payload)",
    "translateWindowClick758To578(byte[] payload)",
    "translateSetCreativeSlot578To758(byte[] payload)",
    "translateSetCreativeSlot758To578(byte[] payload)",
    "translateUnlockRecipes578To758(byte[] payload)",
    "translateUnlockRecipes758To578(byte[] payload)",
    "translateMapData578To758(byte[] payload)",
    "translateMapData758To578(byte[] payload)",
    "translateAdvancements578To758(byte[] payload)",
    "translateAdvancements758To578(byte[] payload)",
    "translateWorldBorder578To758(byte[] payload)",
    "borderInitialize758To578(byte[] payload)",
    "borderSize758To578(byte[] payload)",
    "borderCenter758To578(byte[] payload)",
    "borderLerp758To578(byte[] payload)",
    "borderWarningDelay758To578(byte[] payload)",
    "borderWarningDistance758To578(byte[] payload)",
    "borderSimple758To578(byte[] payload, int action, boolean isDouble)",
    "worldBorderAction578(byte[] payload)",
    "translateTitle578To758(byte[] payload)",
    "titleText758To578(byte[] payload)",
    "titleSubtitle758To578(byte[] payload)",
    "titleActionBar758To578(byte[] payload)",
    "titleSimple758To578(byte[] payload, int action)",
    "titleAnimation758To578(byte[] payload)",
    "titleClear758To578(byte[] payload)",
    "titleAction578(byte[] payload)",
    "translateCombatEvent578To758(byte[] payload)",
    "combatEnter758To578(byte[] payload)",
    "combatEnd758To578(byte[] payload)",
    "combatKill758To578(byte[] payload)",
    "combatEventType578(byte[] payload)",
    "translateResourcePack578To758(byte[] payload)",
    "translateResourcePack758To578(byte[] payload)",
    "translateEditBook578To758(byte[] payload)",
    "translateUpdateJigsaw578To758(byte[] payload)",
    "translateUpdateJigsaw758To578(byte[] payload)",
]

# 共有ヘルパー（形式だけのもの）は 578 の名前のまま使うため、置換後に戻す。
RESTORE = [
    ("translateHeightmaps498", "translateHeightmaps578"),
    ("readSection498", "readSection578"),
    ("writeSection498", "writeSection578"),
    ("Section498", "Section578"),
    ("updateLight498FromChunk758", "updateLight498FromChunk758"),  # 手書き側で定義
]


def transform(text):
    text = text.replace("1.15.2", "1.14.4")
    text = text.replace("578", "498")
    for new, old in RESTORE:
        text = text.replace(new, old)
    return text


parts = []
last_end = None
for sig in COPY_METHODS:
    block, end = extract(sig)
    parts.append(transform(block))
    if "translateUpdateJigsaw758To578" in sig:
        last_end = end

HAND_WRITTEN = r'''
    /**
     * Chunk Data（498 → 758）。578 との違い：バイオームは別フィールド
     * （1024件）ではなく、データバッファの末尾に 256 件（列ごと）が
     * 入る。758 向けには各セクションのバイオームコンテナ（64件）を
     * 列の値から合成する（4×4×4 セルは同じ列の値を共有する）。
     * 光は 578 と同じく別途 Update Light で受け取る。
     */
    public static byte[] translateChunkData498To758(byte[] payload, int heightOffset,
            byte[] updateLight498) {
        try {
            Reader reader = new Reader(payload);
            int chunkX = reader.readInt();
            int chunkZ = reader.readInt();
            boolean groundUp = reader.readBoolean();
            int sectionMask = reader.readVarInt();
            Nbt.NamedResult heightmaps = reader.readNamedNbt();
            int dataLength = reader.readVarInt();
            Reader buffer = new Reader(reader.readBytes(dataLength));
            Section578[] parsed = new Section578[16];
            for (int s = 0; s < 16; s++) {
                if ((sectionMask & (1 << s)) != 0) {
                    parsed[s] = readSection578(buffer);
                }
            }
            int[] biomes = null;
            if (groundUp) {
                biomes = new int[256];
                for (int i = 0; i < 256; i++) {
                    biomes[i] = buffer.readInt();
                }
            }
            int beCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < beCount; i++) {
                Tag tag = reader.readNamedNbt().tag();
                if (!(tag instanceof Tag.CompoundTag compound)) {
                    continue;
                }
                int bx = nbtInt(compound, "x");
                int by = nbtInt(compound, "y");
                int bz = nbtInt(compound, "z");
                String id = compound.getString("id").orElse("");
                String name = id.startsWith("minecraft:") ? id.substring(10) : id;
                int type498 = indexOfBlockEntityName498(name);
                if (type498 < 0) {
                    LOG.warning("758に無いブロックエンティティ（498 id=" + id
                            + "）のエントリをチャンクから落とします。");
                    continue;
                }
                int type758 = RegistryRemapper.mapBlockEntityType498To758(type498);
                if (type758 == RegistryRemapper.DROP) {
                    continue;
                }
                Map<String, Tag> data = new LinkedHashMap<>(compound.value());
                data.remove("x");
                data.remove("y");
                data.remove("z");
                data.remove("id");
                int packedXZ = ((bx & 15) << 4) | (bz & 15);
                kept.add(new BlockEntityEntry(packedXZ, by, type758,
                        data.isEmpty() ? null : new Tag.CompoundTag(data)));
            }
            requireEnd(reader);

            Writer sectionsOut = new Writer();
            for (int s758 = 0; s758 < 24; s758++) {
                int s498 = s758 - 4;
                int[] states758 = new int[4096];
                int blockCount = 0;
                if (s498 >= 0 && s498 < 16 && parsed[s498] != null) {
                    for (int i = 0; i < 4096; i++) {
                        states758[i] = RegistryRemapper.mapBlockState498To758(
                                parsed[s498].states()[i]);
                        if (states758[i] != 0) {
                            blockCount++;
                        }
                    }
                }
                sectionsOut.writeShort(blockCount);
                writeBlockContainer758(sectionsOut, states758);
                int[] biomes758 = new int[64];
                for (int i = 0; i < 64; i++) {
                    int cx = i & 3;
                    int cz = (i >> 2) & 3;
                    int b498 = 1; // plains
                    if (biomes != null) {
                        b498 = biomes[(cz * 4) * 16 + cx * 4];
                    }
                    biomes758[i] = RegistryRemapper.mapBiome498To758(b498);
                }
                writeBiomeContainer758(sectionsOut, biomes758);
            }

            Writer writer = new Writer();
            writer.writeInt(chunkX);
            writer.writeInt(chunkZ);
            Tag.CompoundTag hm = translateHeightmaps578(heightmaps.tag(), true, heightOffset);
            if (hm == null) {
                writer.writeByte(0);
            } else {
                writer.writeNamedNbt("", hm);
            }
            writer.writeByteArray(sectionsOut.bytes());
            writer.writeVarInt(kept.size());
            for (BlockEntityEntry entry : kept) {
                writer.writeUnsignedByte(entry.packedXZ());
                writer.writeShort(entry.y());
                writer.writeVarInt(entry.type());
                writeOptionalNamedNbt(writer, entry.nbt());
            }
            writeLight758(writer, updateLight498);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chunk Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Chunk Data（758 → 498）。バイオームは 758 の最下セクションの
     * コンテナから列ごとに拾い、データバッファの末尾に 256 件並べる。
     * 光データはチャンクから外し、別途 {@link #updateLight498FromChunk758(byte[])}
     * で Update Light（498形式）として取り出すこと。
     */
    public static byte[] translateChunkData758To498(byte[] payload, int heightOffset) {
        try {
            Reader reader = new Reader(payload);
            int chunkX = reader.readInt();
            int chunkZ = reader.readInt();
            Nbt.NamedResult heightmaps = reader.readNamedNbt();
            int dataLength = reader.readVarInt();
            Reader sections = new Reader(reader.readBytes(dataLength));
            int[][] states758 = new int[24][];
            int[][] biomes758 = new int[24][];
            int[] blockCounts = new int[24];
            for (int s = 0; s < 24; s++) {
                blockCounts[s] = sections.readShort();
                states758[s] = readBlockContainer758(sections);
                biomes758[s] = readBiomeContainer758(sections);
            }
            int beCount = reader.readVarInt();
            List<Tag.CompoundTag> kept = new ArrayList<>();
            for (int i = 0; i < beCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalNamedNbt(reader);
                String name = blockEntityName498(type);
                if (name == null) {
                    LOG.warning("498に無いブロックエンティティ種別（758 type=" + type
                            + "）のエントリをチャンクから落とします。");
                    continue;
                }
                Map<String, Tag> data = new LinkedHashMap<>();
                data.put("x", new Tag.IntTag(chunkX * 16 + (packedXZ >> 4)));
                data.put("y", new Tag.IntTag(y));
                data.put("z", new Tag.IntTag(chunkZ * 16 + (packedXZ & 15)));
                data.put("id", new Tag.StringTag("minecraft:" + name));
                if (nbt instanceof Tag.CompoundTag compound) {
                    data.putAll(compound.value());
                }
                kept.add(new Tag.CompoundTag(data));
            }
            // 光データは読み飛ばす（呼び出し側が別途取り出す）。
            reader.readBoolean();
            skipBitset758(reader);
            skipBitset758(reader);
            skipBitset758(reader);
            skipBitset758(reader);

            int sectionMask = 0;
            Writer dataOut = new Writer();
            for (int s498 = 0; s498 < 16; s498++) {
                int s758 = s498 + 4;
                if (blockCounts[s758] == 0) {
                    continue;
                }
                sectionMask |= 1 << s498;
                int[] states498 = new int[4096];
                for (int i = 0; i < 4096; i++) {
                    states498[i] = RegistryRemapper.mapBlockState758To498(states758[s758][i]);
                }
                writeSection578(dataOut, states498);
            }
            // バイオーム（列ごと256件）はバッファの末尾に入る。
            for (int bz = 0; bz < 16; bz++) {
                for (int bx = 0; bx < 16; bx++) {
                    int src = (bz / 4) * 4 + (bx / 4);
                    dataOut.writeInt(RegistryRemapper.mapBiome758To498(biomes758[4][src]));
                }
            }

            Writer writer = new Writer();
            writer.writeInt(chunkX);
            writer.writeInt(chunkZ);
            writer.writeBoolean(true); // groundUp
            writer.writeVarInt(sectionMask);
            Tag.CompoundTag hm = translateHeightmaps578(heightmaps.tag(), false, heightOffset);
            if (hm == null) {
                writer.writeByte(0);
            } else {
                writer.writeNamedNbt("", hm);
            }
            writer.writeByteArray(dataOut.bytes());
            writer.writeVarInt(kept.size());
            for (Tag.CompoundTag compound : kept) {
                writer.writeNamedNbt("", compound);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chunk Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * 758 の Chunk Data の末尾にある光データを、498 の Update Light
     * ペイロードに変換する。498 の Update Light は 578 と同一形式のため、
     * 578 向けの取り出しをそのまま使う。
     */
    public static byte[] updateLight498FromChunk758(byte[] payload) {
        return updateLight578FromChunk758(payload);
    }

    /**
     * Spawn Entity Living（498 → 758）。498 は末尾にメタデータを持つが
     * 758 には無いため、メタデータは分離して Entity Metadata として
     * 返せる形にする。戻り値は {spawn のペイロード, メタデータの
     * ペイロード（無ければ null）}。
     */
    public static byte[][] spawnLiving498To758(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            int entityId = reader.readVarInt();
            writer.writeVarInt(entityId);
            writer.writeBytes(reader.readBytes(16));
            writer.writeVarInt(RegistryRemapper.mapEntityType498To758(reader.readVarInt()));
            writer.writeDouble(reader.readDouble());
            writer.writeDouble(reader.readDouble());
            writer.writeDouble(reader.readDouble());
            writer.writeByte(reader.readByte()); // yaw
            writer.writeByte(reader.readByte()); // pitch
            writer.writeByte(reader.readByte()); // headPitch
            writer.writeShort(reader.readShort());
            writer.writeShort(reader.readShort());
            writer.writeShort(reader.readShort());
            byte[] metadata = reader.rest();
            byte[] metadata758 = null;
            if (metadata.length > 0) {
                Writer mw = new Writer();
                mw.writeVarInt(entityId);
                mw.writeBytes(metadata);
                metadata758 = translateEntityMetadata498(mw.bytes(), false);
            }
            return new byte[][]{writer.bytes(), metadata758};
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity Living を変換できなかったため素通しします: "
                    + e.getMessage());
            return new byte[][]{payload, null};
        }
    }

    /**
     * Spawn Entity Living（758 → 498）。種別を付け替え、498 にある
     * 末尾メタデータは空（終端 0xFF）を補う。
     */
    public static byte[] translateSpawnLiving758To498(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt());
            writer.writeBytes(reader.readBytes(16));
            writer.writeVarInt(RegistryRemapper.mapEntityType758To498(reader.readVarInt()));
            writer.writeDouble(reader.readDouble());
            writer.writeDouble(reader.readDouble());
            writer.writeDouble(reader.readDouble());
            writer.writeByte(reader.readByte()); // yaw
            writer.writeByte(reader.readByte()); // pitch
            writer.writeByte(reader.readByte()); // headPitch
            writer.writeShort(reader.readShort());
            writer.writeShort(reader.readShort());
            writer.writeShort(reader.readShort());
            requireEnd(reader);
            writer.writeUnsignedByte(0xFF); // メタデータ（空）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity Living を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /**
     * パーティクルのデータ部だけを 498⇔758 向けに変換する（ID は
     * 呼び出し側で付け替え済み）。読み方は元の ID で決める。
     * 758：block=2、block_marker=3、dust=14、dust_color_transition=15、
     * falling_dust=24、item=35、vibration=36。
     * 498：block=3、dust=14、falling_dust=23、item=32。
     */
    private static void translateParticleData498(Reader reader, Writer writer, int sourceId,
            boolean to498) {
        if (to498) {
            switch (sourceId) {
                case 2 -> writer.writeVarInt(
                        RegistryRemapper.mapBlockState758To498(reader.readVarInt()));
                case 3 -> reader.readVarInt(); // block_marker の状態は barrier には無い
                case 14 -> {
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                }
                case 15 -> { // dust_color_transition → dust（前半4つだけ使う）
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    reader.readFloat();
                    reader.readFloat();
                    reader.readFloat();
                }
                case 24 -> writer.writeVarInt(
                        RegistryRemapper.mapBlockState758To498(reader.readVarInt()));
                case 35 -> translateSlot758To498(reader, writer);
                case 36 -> { // vibration → enchant（データ無し）
                    reader.readPositionRaw(); // origin
                    String positionType = reader.readString();
                    if ("minecraft:entity".equals(positionType)) {
                        reader.readVarInt(); // entityId
                    } else {
                        reader.readPositionRaw(); // destination
                    }
                    reader.readVarInt(); // ticks
                }
                default -> {
                }
            }
            return;
        }
        switch (sourceId) {
            case 3 -> writer.writeVarInt(
                    RegistryRemapper.mapBlockState498To758(reader.readVarInt()));
            case 14 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 23 -> writer.writeVarInt(
                    RegistryRemapper.mapBlockState498To758(reader.readVarInt()));
            case 32 -> translateSlot498To758(reader, writer);
            default -> {
            }
        }
    }

    /**
     * World Particles（498 → 758）。498 は座標が f32、758 は f64。
     * 粒子データは両形式とも末尾（個数の後）に付くため、ID で決まる
     * データ部は最後に変換する。
     */
    public static byte[] translateWorldParticles498To758(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            int id = reader.readVarInt();
            writer.writeVarInt(RegistryRemapper.mapParticle498To758(id));
            writer.writeBoolean(reader.readBoolean()); // longDistance
            writer.writeDouble(reader.readFloat());
            writer.writeDouble(reader.readFloat());
            writer.writeDouble(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat()); // particleData（速度）
            writer.writeInt(reader.readInt()); // particleCount
            translateParticleData498(reader, writer, id, false);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("World Particles を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /** World Particles（758 → 498）。座標は f64 → f32 に落とす。 */
    public static byte[] translateWorldParticles758To498(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            int id = reader.readVarInt();
            writer.writeVarInt(RegistryRemapper.mapParticle758To498(id));
            writer.writeBoolean(reader.readBoolean()); // longDistance
            writer.writeFloat((float) reader.readDouble());
            writer.writeFloat((float) reader.readDouble());
            writer.writeFloat((float) reader.readDouble());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat()); // particleData（速度）
            writer.writeInt(reader.readInt()); // particleCount
            translateParticleData498(reader, writer, id, true);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("World Particles を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }
'''

header = """
    // ------------------------------------------------------------------
    // 498（1.14.4）対応
    // 578 と形式が同じ変換は 578 版の写し（レジストリ表だけ 498 用）。
    // 形式が違うもの（チャンクのバイオーム配置・Spawn Living の末尾
    // メタデータ・World Particles の f32 座標）は専用に書いてある。
    // ------------------------------------------------------------------
"""

block = header + "\n" + "\n\n".join(parts) + "\n" + HAND_WRITTEN
if last_end is None:
    raise SystemExit("挿入位置（translateUpdateJigsaw758To578）が見つかりません")
lines.insert(last_end + 1, block)
open(PATH, "w").write("\n".join(lines))
print("ChunkTranslator に 498 セクションを挿入しました（メソッド",
      len(parts), "件＋手書き）")
