package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の757（1.18／1.18.1）⇔759（1.19）向け変換テスト。
 * 757・759 ともデータコンポーネントは無い（NBT 時代）のため、スロットは
 * bool＋アイテムID＋個数＋名前付き NBT で、NBT は素通しする。
 * レジストリ差分（Stage A §11.1 で実測）：ブロック状態 20,342→21,448、
 * アイテム 1,101→1,152、エンティティ 113→118、音 1,203→1,321、
 * パーティクル 88→93、バイオーム 61→63、エンチャント 38→39
 * （swift_sneak が 759 の 12 番に挿入）、エフェクト 32→33
 * （darkness が 759 の 33 番）。パケットのフィールド差は protocol.json
 * （mc-1.18-protocol.json／mc-1.19-protocol.json）で実測。
 * 雛形は ChunkTranslator761Test／ChunkTranslator777Test。
 */
class ChunkTranslator757Test {

    /** 高さマップ（名前付き NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** スロット1個（757／759形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** スロットの先頭（個数＋アイテムID）を読む。 */
    private static int[] readSlotHead(Reader reader) {
        assertTrue(reader.readBoolean());
        int itemId = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readByte()); // NBT 無し
        return new int[]{itemId, count};
    }

    /** 間接パレットのセクション1個（ブロック＋バイオーム）。757／759形式。 */
    private static byte[] indirectSection(int[] blockPalette, int[] biomePalette) {
        Writer writer = new Writer();
        writer.writeShort(1); // ブロック数
        writer.writeUnsignedByte(4); // BPE（間接）
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(256); // Data Array：4096エントリ×4bit＝256 long
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        writer.writeUnsignedByte(2); // バイオーム BPE（間接）
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(2); // Data Array：64エントリ×2bit＝2 long
        for (int i = 0; i < 2; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** セクションのブロックパレットだけ読み、ブロック状態IDの配列を返す。 */
    private static int[] readBlockPalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** セクションのバイオームパレットを読む。 */
    private static int[] readBiomePalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte(); // ブロック BPE
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int blockDataLength = section.readVarInt();
        for (int i = 0; i < blockDataLength; i++) {
            section.readLong();
        }
        section.readUnsignedByte();
        int biomePaletteSize = section.readVarInt();
        int[] palette = new int[biomePaletteSize];
        for (int i = 0; i < biomePaletteSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** チャンク Data ペイロード。blockEntities は {packedXZ, y, type} の列。 */
    private static byte[] chunkDataPayload(int chunkX, int chunkZ,
            int[] blockPalette, int[] biomePalette, int[][] blockEntities, boolean trustEdges) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        payload.writeNamedNbt("", heightmaps());
        payload.writeByteArray(indirectSection(blockPalette, biomePalette));
        payload.writeVarInt(blockEntities.length);
        for (int[] be : blockEntities) {
            payload.writeUnsignedByte(be[0]);
            payload.writeShort(be[1]);
            payload.writeVarInt(be[2]);
            payload.writeByte(0); // NBT 無し
        }
        payload.writeBoolean(trustEdges);
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ（そのまま写る想定）
        return payload.bytes();
    }

    /** 変換後の Chunk Data を読み、セクション部分とブロックエンティティ種別を返す。 */
    private static class ParsedChunk {
        byte[] sections;
        int[] blockEntityTypes;
        boolean trustEdges;
        byte[] light;
    }

    private static ParsedChunk readChunkData(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        ParsedChunk out = new ParsedChunk();
        out.sections = reader.readByteArray();
        int count = reader.readVarInt();
        out.blockEntityTypes = new int[count];
        for (int i = 0; i < count; i++) {
            reader.readUnsignedByte();
            reader.readShort();
            out.blockEntityTypes[i] = reader.readVarInt();
            assertEquals(0, reader.readByte()); // NBT 無し
        }
        out.trustEdges = reader.readBoolean();
        out.light = reader.rest();
        assertEquals(0, reader.remaining());
        return out;
    }

    /** 759形式の Spawn Entity ペイロード（headPitch あり・objectData は varint）。 */
    private static byte[] spawnEntityPacket759(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(10); // pitch
        writer.writeByte(20); // yaw
        writer.writeByte(30); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    /** 757形式の Spawn Entity ペイロード（headPitch 無し・objectData は i32）。 */
    private static byte[] spawnEntityPacket757(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(10); // pitch
        writer.writeByte(20); // yaw
        writer.writeInt(objectData);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    /** 759形式の Spawn Entity ペイロードの種別と objectData を読む。 */
    private static int[] readSpawnEntity759(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // entityId
        reader.readBytes(16); // uuid
        int type = reader.readVarInt();
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte(); // pitch
        int yaw = reader.readByte();
        int headPitch = reader.readByte();
        int objectData = reader.readVarInt();
        reader.readShort(); // velocityX
        reader.readShort(); // velocityY
        reader.readShort(); // velocityZ
        assertEquals(0, reader.remaining());
        return new int[]{type, objectData, yaw, headPitch};
    }

    /** 757形式の Spawn Entity ペイロードの種別と objectData を読む。 */
    private static int[] readSpawnEntity757(byte[] payload) {
        Reader reader = new Reader(payload);
        reader.readVarInt(); // entityId
        reader.readBytes(16); // uuid
        int type = reader.readVarInt();
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte(); // pitch
        int yaw = reader.readByte();
        int objectData = reader.readInt();
        reader.readShort(); // velocityX
        reader.readShort(); // velocityY
        reader.readShort(); // velocityZ
        assertEquals(0, reader.remaining());
        return new int[]{type, objectData, yaw};
    }

    /** 759形式の World Particles ペイロード（粒子IDは varint）。data は既に書いたバイト列。 */
    private static byte[] worldParticlesPacket759(int particleId, byte[] data) {
        Writer writer = new Writer();
        writer.writeVarInt(particleId);
        writer.writeBytes(data);
        writer.writeBoolean(true); // longDistance
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.4f); // speed
        writer.writeInt(5); // count
        return writer.bytes();
    }

    /** 757形式の World Particles ペイロードの粒子ID（i32）と後続を読む。 */
    private static int readWorldParticlesId757(byte[] payload) {
        Reader reader = new Reader(payload);
        return reader.readInt();
    }

    // ---- スロット ----

    @Test
    void slotRoundTrip757To759To757() {
        Writer writer = new Writer();
        writeSlot(writer, 1, 64); // stone（757=1・759=1）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot757To759(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot759To757(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotNewItem759FallsBackTo757() {
        // mud（759=19）→ dirt（757=15）、mangrove_planks（759=29）→ oak_planks（757=22）。
        Writer writer = new Writer();
        writeSlot(writer, 19, 1);
        Reader reader = new Reader(writer.bytes());
        Writer out = new Writer();
        ChunkTranslator.translateSlot759To757(reader, out);
        assertArrayEquals(new int[]{15, 1}, readSlotHead(new Reader(out.bytes())));

        Writer writer2 = new Writer();
        writeSlot(writer2, 29, 3);
        Reader reader2 = new Reader(writer2.bytes());
        Writer out2 = new Writer();
        ChunkTranslator.translateSlot759To757(reader2, out2);
        assertArrayEquals(new int[]{22, 3}, readSlotHead(new Reader(out2.bytes())));
    }

    // ---- Chunk Data ----

    @Test
    void chunkData759To757RemapsBlockStateBiomeAndDropsBlockEntity() {
        // ブロック状態 759=21 → 757=15（0〜5 は恒等）。バイオーム mangrove_swamp
        // （759=7）→ swamp（757=6。Stage A の代替表）。sculk_catalyst（759=34）は落とす。
        byte[] translated = ChunkTranslator.translateChunkData759To757(
                chunkDataPayload(3, -2,
                        new int[]{0, 1, 21}, new int[]{7},
                        new int[][]{{0x11, 64, 1}, {0x22, 65, 34}}, true));
        ParsedChunk parsed = readChunkData(translated);
        assertArrayEquals(new int[]{0, 1, 15}, readBlockPalette(parsed.sections));
        assertArrayEquals(new int[]{6}, readBiomePalette(parsed.sections));
        assertArrayEquals(new int[]{1}, parsed.blockEntityTypes);
        assertTrue(parsed.trustEdges);
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, parsed.light);
    }

    @Test
    void chunkDataRoundTrip757To759To757() {
        byte[] original = chunkDataPayload(3, -2,
                new int[]{0, 1, 2}, new int[]{1},
                new int[][]{{0x11, 64, 1}}, true);
        byte[] up = ChunkTranslator.translateChunkData757To759(original);
        ParsedChunk parsed = readChunkData(up);
        assertArrayEquals(new int[]{0, 1, 2}, readBlockPalette(parsed.sections));
        assertArrayEquals(new int[]{1}, readBiomePalette(parsed.sections));
        assertArrayEquals(new int[]{1}, parsed.blockEntityTypes);
        byte[] back = ChunkTranslator.translateChunkData759To757(up);
        assertArrayEquals(original, back);
    }

    // ---- Spawn Entity ----

    @Test
    void spawnEntityNewTypes759FallBackTo757() {
        // warden（759=105）→ ravager（757=72）、frog（32）→ axolotl（3）、
        // allay（0）→ vex（97）。
        assertEquals(72, readSpawnEntity757(
                ChunkTranslator.translateSpawnEntity759To757(spawnEntityPacket759(105, 0)))[0]);
        assertEquals(3, readSpawnEntity757(
                ChunkTranslator.translateSpawnEntity759To757(spawnEntityPacket759(32, 0)))[0]);
        assertEquals(97, readSpawnEntity757(
                ChunkTranslator.translateSpawnEntity759To757(spawnEntityPacket759(0, 0)))[0]);
        // falling_block（759=29）の objectData はブロック状態ID（21→15）。
        int[] falling = readSpawnEntity757(
                ChunkTranslator.translateSpawnEntity759To757(spawnEntityPacket759(29, 21)));
        assertEquals(27, falling[0]);
        assertEquals(15, falling[1]);
        // 逆向き：falling_block（757=27）の objectData（21→22。挿入によるずれ）。
        // headPitch は yaw で代用。
        int[] up = readSpawnEntity759(
                ChunkTranslator.translateSpawnEntity757To759(spawnEntityPacket757(27, 21)));
        assertEquals(29, up[0]);
        assertEquals(22, up[1]);
        assertEquals(20, up[2]); // yaw
        assertEquals(20, up[3]); // headPitch（yaw で代用）
    }

    // ---- パーティクル ----

    @Test
    void worldParticlesNewTypes759FallBackTo757() {
        // sonic_boom（759=24）→ explosion（757=23、データ無し）。
        byte[] translated = ChunkTranslator.translateWorldParticles759To757(
                worldParticlesPacket759(24, new byte[0]));
        assertEquals(23, readWorldParticlesId757(translated));
        // sculk_charge（759=30、回転 float 付き）→ soul（757=29、データ無し）。
        Writer rotation = new Writer();
        rotation.writeFloat(1.5f);
        byte[] translated2 = ChunkTranslator.translateWorldParticles759To757(
                worldParticlesPacket759(30, rotation.bytes()));
        assertEquals(29, readWorldParticlesId757(translated2));
        // shriek（759=92、遅延 varint 付き）→ sweep_attack（757=50、データ無し）。
        Writer delay = new Writer();
        delay.writeVarInt(7);
        byte[] translated3 = ChunkTranslator.translateWorldParticles759To757(
                worldParticlesPacket759(92, delay.bytes()));
        assertEquals(50, readWorldParticlesId757(translated3));
        // falling_dust（759=25、ブロック状態 21）→ falling_dust（757=24、ブロック状態 15）。
        Writer dust = new Writer();
        dust.writeVarInt(21);
        byte[] translated4 = ChunkTranslator.translateWorldParticles759To757(
                worldParticlesPacket759(25, dust.bytes()));
        Reader reader4 = new Reader(translated4);
        assertEquals(24, reader4.readInt());
        assertEquals(15, reader4.readVarInt());
        // vibration（759=40、block 行き先）→ vibration（757=36）。origin は
        // パケットの x／y／z（1.5／2.5／3.5→1／2／3）になる。
        Writer vibration = new Writer();
        vibration.writeString("minecraft:block");
        vibration.writePositionRaw((3L << 38) | (5L << 12) | 4L); // 行き先 (3, 4, 5)
        vibration.writeVarInt(9); // ticks
        byte[] translated5 = ChunkTranslator.translateWorldParticles759To757(
                worldParticlesPacket759(40, vibration.bytes()));
        Reader reader5 = new Reader(translated5);
        assertEquals(36, reader5.readInt());
        long origin = reader5.readPositionRaw();
        assertEquals(1, (int) (origin >> 38));
        assertEquals(2, (int) (origin & 0xFFF));
        assertEquals(3, (int) ((origin >> 12) & 0x3FFFFFF));
        assertEquals("minecraft:block", reader5.readString());
        assertEquals((3L << 38) | (5L << 12) | 4L, reader5.readPositionRaw());
        assertEquals(9, reader5.readVarInt());
    }

    // ---- 音 ----

    @Test
    void soundEffectNewSound759FallsBackTo757() {
        // entity.warden.ambient（759=1222）→ entity.ravager.ambient（757=855）。
        // seed（i64）は落とす。
        Writer writer = new Writer();
        writer.writeVarInt(1222);
        writer.writeVarInt(0); // category
        writer.writeInt(10);
        writer.writeInt(20);
        writer.writeInt(30);
        writer.writeFloat(1.0f);
        writer.writeFloat(0.5f);
        writer.writeLong(12345L); // seed
        byte[] translated = ChunkTranslator.translateSoundEffect759To757(writer.bytes());
        Reader reader = new Reader(translated);
        assertEquals(855, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(10, reader.readInt());
        assertEquals(20, reader.readInt());
        assertEquals(30, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
        // Named Sound Effect：名前で指定する形式。entity.warden.ambient →
        // entity.ravager.ambient。seed を落とす。
        Writer named = new Writer();
        named.writeString("entity.warden.ambient");
        named.writeVarInt(0);
        named.writeInt(10);
        named.writeInt(20);
        named.writeInt(30);
        named.writeFloat(1.0f);
        named.writeFloat(0.5f);
        named.writeLong(999L);
        byte[] translatedNamed = ChunkTranslator.translateNamedSoundEffect759To757(named.bytes());
        Reader readerNamed = new Reader(translatedNamed);
        assertEquals("entity.ravager.ambient", readerNamed.readString());
        assertEquals(0, readerNamed.readVarInt()); // category
        assertEquals(10, readerNamed.readInt());
        assertEquals(20, readerNamed.readInt());
        assertEquals(30, readerNamed.readInt());
        assertEquals(1.0f, readerNamed.readFloat());
        assertEquals(0.5f, readerNamed.readFloat());
        assertEquals(0, readerNamed.remaining());
    }

    // ---- エンチャント・エフェクト・メタデータ ----

    @Test
    void enchantItemSwiftSneak759IsDropped() {
        // swift_sneak（759=12）は 757 に無いためパケットごと落とす。
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeByte(12); // swift_sneak
        assertNull(ChunkTranslator.translateEnchantItem759To757(writer.bytes()));
        // 759=11 は 757=11 のまま通る。
        Writer writer2 = new Writer();
        writer2.writeByte(0);
        writer2.writeByte(11);
        byte[] translated = ChunkTranslator.translateEnchantItem759To757(writer2.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(11, reader.readByte());
        assertEquals(0, reader.remaining());
        // 逆向き：757=12 は 759=13（swift_sneak の挿入で +1）。
        Writer writer3 = new Writer();
        writer3.writeByte(0);
        writer3.writeByte(12);
        byte[] translated3 = ChunkTranslator.translateEnchantItem757To759(writer3.bytes());
        Reader reader3 = new Reader(translated3);
        assertEquals(0, reader3.readByte());
        assertEquals(13, reader3.readByte());
    }

    @Test
    void entityEffectDarkness759IsDroppedAndMetadataFrogVariantDropped() {
        // darkness（759=33）は 757 に無いため Entity Effect／Remove Entity Effect
        // ともにパケットごと落とす。
        Writer effect = new Writer();
        effect.writeVarInt(5); // entityId
        effect.writeVarInt(33); // darkness
        effect.writeByte(0); // amplifier
        effect.writeVarInt(100); // duration
        effect.writeByte(0); // hideParticles
        effect.writeBoolean(false); // factorCodec 無し
        assertNull(ChunkTranslator.translateEntityEffect759To757(effect.bytes()));
        Writer remove = new Writer();
        remove.writeVarInt(5);
        remove.writeVarInt(33);
        assertNull(ChunkTranslator.translateRemoveEntityEffect759To757(remove.bytes()));
        // speed（1）は通る（effectId は varint→i8）。
        Writer speed = new Writer();
        speed.writeVarInt(5);
        speed.writeVarInt(1);
        speed.writeByte(2);
        speed.writeVarInt(100);
        speed.writeByte(1);
        speed.writeBoolean(false);
        byte[] translatedSpeed = ChunkTranslator.translateEntityEffect759To757(speed.bytes());
        Reader readerSpeed = new Reader(translatedSpeed);
        assertEquals(5, readerSpeed.readVarInt());
        assertEquals(1, readerSpeed.readByte());
        assertEquals(2, readerSpeed.readByte());
        assertEquals(100, readerSpeed.readVarInt());
        assertEquals(1, readerSpeed.readByte());
        assertEquals(0, readerSpeed.remaining());
        // メタデータ：frog variant（型 22）のエントリは落とし、型 1 のエントリは残す。
        Writer meta = new Writer();
        meta.writeVarInt(5); // entityId
        meta.writeUnsignedByte(0);
        meta.writeVarInt(22); // frog variant
        meta.writeVarInt(1);
        meta.writeUnsignedByte(1);
        meta.writeVarInt(1); // varint
        meta.writeVarInt(7);
        meta.writeUnsignedByte(0xFF);
        byte[] translatedMeta = ChunkTranslator.translateEntityMetadata759To757(meta.bytes());
        Reader readerMeta = new Reader(translatedMeta);
        assertEquals(5, readerMeta.readVarInt());
        assertEquals(1, readerMeta.readUnsignedByte());
        assertEquals(1, readerMeta.readVarInt());
        assertEquals(7, readerMeta.readVarInt());
        assertEquals(0xFF, readerMeta.readUnsignedByte());
        assertEquals(0, readerMeta.remaining());
    }
}
