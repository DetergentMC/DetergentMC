package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 210（1.10.2）のチャンク・座標・スポーン・スロット変換。チャンクの
 * 骨格は 393 と同じ（heightmap 無し・光は内蔵・crossing 詰め）だが、
 * パレット値は (数値ID&lt;&lt;4)|メタデータ、バイオームは u8×256、
 * 直接パレットは13ビット。ブロック値の意味付け替えは意味対応表
 * （RegistryRemapper の 210 表）が担う。
 */
class ChunkTranslator210Test {

    /** 210 のチャンク1個（セクション0のみ、石・草・白羊毛、光は全ゼロ、バイオームは plains）。 */
    private static byte[] chunk210() {
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true); // groundUp
        writer.writeVarInt(1); // mask: section 0
        Writer buffer = new Writer();
        Writer section = new Writer();
        section.writeUnsignedByte(4); // bits per block（パレット）
        section.writeVarInt(4);
        section.writeVarInt(0); // air
        section.writeVarInt(16); // stone (1<<4)|0
        section.writeVarInt(32); // grass_block (2<<4)|0
        section.writeVarInt(560); // white wool (35<<4)|0
        long[] data = new long[256];
        data[0] = 1; // (0,0,0) = パレット1（stone）
        data[1] = 2; // (0,1,0) = パレット2（grass）
        data[2] = 3; // (0,2,0) = パレット3（white wool）
        section.writeVarInt(256);
        for (long l : data) {
            section.writeLong(l);
        }
        section.writeBytes(new byte[2048]); // ブロックライト
        section.writeBytes(new byte[2048]); // スカイライト
        buffer.writeBytes(section.bytes());
        for (int i = 0; i < 256; i++) {
            buffer.writeByte(1); // バイオーム列（plains、210 は u8）
        }
        byte[] bufferBytes = buffer.bytes();
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
        writer.writeVarInt(0); // ブロックエンティティなし
        return writer.bytes();
    }

    @Test
    void chunk210RoundTripsThrough758() {
        byte[] out = ChunkTranslator.translateChunkData210To758(chunk210(), true);
        assertNotNull(out);
        byte[] back = ChunkTranslator.translateChunkData758To210(out, true);
        Reader reader = new Reader(back);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readVarInt()); // mask はセクション0のみ
        int size = reader.readVarInt();
        Reader buffer = new Reader(reader.readBytes(size));
        assertEquals(4, buffer.readUnsignedByte()); // bits per block
        int palLen = buffer.readVarInt();
        int[] palette = new int[palLen];
        for (int i = 0; i < palLen; i++) {
            palette[i] = buffer.readVarInt();
        }
        assertEquals(256, buffer.readVarInt());
        long[] data = new long[256];
        for (int i = 0; i < 256; i++) {
            data[i] = buffer.readLong();
        }
        assertEquals(16, palette[(int) (data[0] & 0xF)]); // stone
        assertEquals(32, palette[(int) (data[1] & 0xF)]); // grass
        assertEquals(560, palette[(int) (data[2] & 0xF)]); // white wool
        assertEquals(0, palette[(int) ((data[0] >>> 4) & 0xF)]); // air
        buffer.readBytes(2048);
        buffer.readBytes(2048);
        for (int i = 0; i < 256; i++) {
            assertEquals(1, buffer.readUnsignedByte(), "biome " + i); // u8 で戻る
        }
        assertEquals(0, buffer.remaining());
        assertEquals(0, reader.readVarInt()); // ブロックエンティティなし
        assertEquals(0, reader.remaining());
    }

    /** テスト側で独立に実装した crossing（境界またぎ）詰め。 */
    private static long[] packCrossingLocal(int[] values, int bits) {
        long[] longs = new long[(values.length * bits + 63) / 64];
        int bitPos = 0;
        for (int v : values) {
            for (int b = 0; b < bits; b++) {
                if (((v >>> b) & 1) != 0) {
                    longs[bitPos >> 6] |= 1L << (bitPos & 63);
                }
                bitPos++;
            }
        }
        return longs;
    }

    /** テスト側で独立に実装した crossing 解き。 */
    private static int[] unpackCrossingLocal(long[] longs, int bits, int count) {
        int[] out = new int[count];
        int bitPos = 0;
        for (int i = 0; i < count; i++) {
            int v = 0;
            for (int b = 0; b < bits; b++) {
                if (((longs[bitPos >> 6] >>> (bitPos & 63)) & 1) != 0) {
                    v |= 1 << b;
                }
                bitPos++;
            }
            out[i] = v;
        }
        return out;
    }

    @Test
    void chunkSection210UsesCrossingPacking() {
        // パレットは (id<<4)|0 で id 0..19（全て実在ブロック、758 側の
        // 既定状態が互いに異なる）。
        int[] paletteIds = {0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};
        int[] palette = new int[20];
        for (int i = 0; i < 20; i++) {
            palette[i] = paletteIds[i] << 4;
        }
        int[] entries = new int[4096];
        for (int i = 0; i < 4096; i++) {
            entries[i] = (i * 7 + 3) % 20;
        }
        long[] data = packCrossingLocal(entries, 5);
        assertEquals(320, data.length);
        Writer section = new Writer();
        section.writeUnsignedByte(5);
        section.writeVarInt(20);
        for (int state : palette) {
            section.writeVarInt(state);
        }
        section.writeVarInt(data.length);
        for (long l : data) {
            section.writeLong(l);
        }
        section.writeBytes(new byte[2048]);
        section.writeBytes(new byte[2048]);
        Writer buffer = new Writer();
        buffer.writeBytes(section.bytes());
        for (int i = 0; i < 256; i++) {
            buffer.writeByte(1);
        }
        Writer chunk = new Writer();
        chunk.writeInt(0);
        chunk.writeInt(0);
        chunk.writeBoolean(true);
        chunk.writeVarInt(1);
        byte[] bufferBytes = buffer.bytes();
        chunk.writeVarInt(bufferBytes.length);
        chunk.writeBytes(bufferBytes);
        chunk.writeVarInt(0);
        byte[] as758 = ChunkTranslator.translateChunkData210To758(chunk.bytes(), true);
        assertNotNull(as758);
        byte[] back = ChunkTranslator.translateChunkData758To210(as758, true);
        Reader reader = new Reader(back);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        int size = reader.readVarInt();
        Reader buf = new Reader(reader.readBytes(size));
        int bits = buf.readUnsignedByte();
        int palLen = buf.readVarInt();
        int[] palBack = new int[palLen];
        for (int i = 0; i < palLen; i++) {
            palBack[i] = buf.readVarInt();
        }
        int dataLen = buf.readVarInt();
        long[] dataBack = new long[dataLen];
        for (int i = 0; i < dataLen; i++) {
            dataBack[i] = buf.readLong();
        }
        int[] entriesBack = unpackCrossingLocal(dataBack, bits, 4096);
        for (int idx = 0; idx < 4096; idx++) {
            assertEquals(palette[entries[idx]], palBack[entriesBack[idx]],
                    "state at " + idx);
        }
    }

    @Test
    void positionPackingDiffersBetween210And758() {
        long raw758 = (1L << 38) | (3L << 12) | 2L;
        long raw210 = (1L << 38) | (2L << 26) | 3L;
        assertEquals(raw210, ChunkTranslator.repackPosition758To210(raw758));
        assertEquals(raw758, ChunkTranslator.repackPosition210To758(raw210));
        long negative758 = (-5L << 38) | (300L << 12) | 64L;
        assertEquals(negative758, ChunkTranslator.repackPosition210To758(
                ChunkTranslator.repackPosition758To210(negative758)));
    }

    @Test
    void slotSplitsUseDamageTag() {
        // salmon（758 の 802）→ ハイブリッド210：fish(349)＋Damage 1。
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeBoolean(true);
        w.writeVarInt(802);
        w.writeByte(3);
        w.writeByte(0);
        byte[] hybrid = ChunkTranslator.translateSlot758To210(w.bytes());
        PacketIO.Reader reader = new PacketIO.Reader(hybrid);
        assertTrue(reader.readBoolean());
        assertEquals(349, reader.readVarInt());
        assertEquals(3, reader.readUnsignedByte());
        assertArrayEquals(w.bytes(), ChunkTranslator.translateSlot210To758(hybrid));
        // 白バナー（982）→ banner(425)＋Damage 15（染料順は逆順）。
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeBoolean(true);
        w2.writeVarInt(982);
        w2.writeByte(1);
        w2.writeByte(0);
        byte[] hybrid2 = ChunkTranslator.translateSlot758To210(w2.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(hybrid2);
        assertTrue(r2.readBoolean());
        assertEquals(425, r2.readVarInt());
        assertArrayEquals(w2.bytes(), ChunkTranslator.translateSlot210To758(hybrid2));
        // 通常アイテム（ダイヤ剣 719）は番号だけ付け替わり Damage は付かない。
        PacketIO.Writer w3 = new PacketIO.Writer();
        w3.writeBoolean(true);
        w3.writeVarInt(719);
        w3.writeByte(1);
        w3.writeByte(0);
        byte[] hybrid3 = ChunkTranslator.translateSlot758To210(w3.bytes());
        PacketIO.Reader r3 = new PacketIO.Reader(hybrid3);
        assertTrue(r3.readBoolean());
        assertEquals(276, r3.readVarInt());
        assertEquals(1, r3.readUnsignedByte()); // count
        assertEquals(0, r3.readByte()); // NBT 無し
    }

    @Test
    void spawnPaintingUsesTitleString() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(10);
        w.writeUuid(uuid);
        w.writeVarInt(25); // 758 の donkey_kong
        w.writeLong((1L << 38) | (64L << 26) | 3L); // 210 形式の座標で入れる前の値は758形式
        w.writeByte(2);
        // 758 形式の座標で作り直す（x=1,y=64,z=3 の 758 詰め）。
        PacketIO.Writer w758 = new PacketIO.Writer();
        w758.writeVarInt(10);
        w758.writeUuid(uuid);
        w758.writeVarInt(25);
        w758.writeLong((1L << 38) | (3L << 12) | 64L);
        w758.writeByte(2);
        byte[] as210 = ChunkTranslator.translateSpawnPainting758To210(w758.bytes());
        PacketIO.Reader reader = new PacketIO.Reader(as210);
        assertEquals(10, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals("DonkeyKong", reader.readString()); // タイトル文字列
        byte[] back = ChunkTranslator.translateSpawnPainting210To758(as210);
        PacketIO.Reader backReader = new PacketIO.Reader(back);
        backReader.readVarInt();
        backReader.readUuid();
        assertEquals(25, backReader.readVarInt());
    }

    @Test
    void entityEffectShiftsByOne() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(42); // entityId
        w.writeVarInt(0); // speed（758 は 0 始まり）
        w.writeByte(1); // amplifier
        w.writeVarInt(200); // duration
        w.writeByte(0); // flags
        byte[] as210 = ChunkTranslator.translateEntityEffect758To210(w.bytes());
        PacketIO.Reader reader = new PacketIO.Reader(as210);
        assertEquals(42, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // 210 は 1 始まり
        assertArrayEquals(w.bytes(), ChunkTranslator.translateEntityEffect210To758(as210));
        // bad_omen（758 の 27）は 210 に無いため null。
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeVarInt(42);
        w2.writeVarInt(27);
        w2.writeByte(0);
        w2.writeVarInt(200);
        w2.writeByte(0);
        assertNull(ChunkTranslator.translateEntityEffect758To210(w2.bytes()));
    }

    @Test
    void scoreboardObjectiveTypeBecomesString() {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeString("obj");
        w.writeByte(0); // mode 0（作成）
        w.writeString("表示");
        w.writeVarInt(1); // hearts
        byte[] as210 = ChunkTranslator.translateScoreboardObjective758To210(w.bytes());
        PacketIO.Reader reader = new PacketIO.Reader(as210);
        assertEquals("obj", reader.readString());
        assertEquals(0, reader.readByte());
        assertEquals("表示", reader.readString());
        assertEquals("hearts", reader.readString());
        assertArrayEquals(w.bytes(),
                ChunkTranslator.translateScoreboardObjective210To758(as210));
    }

    @Test
    void metadataTypeNumbersShift() {
        // 758 の type 14（nbt）は 210 の 15、type 15（particle）は 16。
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeVarInt(9); // entityId
        w.writeUnsignedByte(0);
        w.writeVarInt(14); // nbt
        w.writeByte(0); // 空 NBT
        w.writeUnsignedByte(0xFF);
        byte[] as210 = ChunkTranslator.translateEntityMetadata758To210(w.bytes());
        PacketIO.Reader reader = new PacketIO.Reader(as210);
        assertEquals(9, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(15, reader.readVarInt());
        // 逆向き：210 の optblockid（14）で値が無い場合は落とされる。
        PacketIO.Writer w2 = new PacketIO.Writer();
        w2.writeVarInt(9);
        w2.writeUnsignedByte(3);
        w2.writeVarInt(14); // optblockid
        w2.writeBoolean(false); // 値無し
        w2.writeUnsignedByte(0xFF);
        byte[] as758 = ChunkTranslator.translateEntityMetadata210To758(w2.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(as758);
        assertEquals(9, r2.readVarInt());
        assertEquals(0xFF, r2.readUnsignedByte()); // エントリが落ちて終端のみ
    }

    @Test
    void spawnEntitySplitsObjectAndLiving() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PacketIO.Writer boat758 = new PacketIO.Writer();
        boat758.writeVarInt(10);
        boat758.writeUuid(uuid);
        boat758.writeVarInt(7); // 758 の boat
        boat758.writeDouble(1.0);
        boat758.writeDouble(64.0);
        boat758.writeDouble(2.0);
        boat758.writeByte(0);
        boat758.writeByte(0);
        boat758.writeInt(0);
        boat758.writeShort(0);
        boat758.writeShort(0);
        boat758.writeShort(0);
        byte[] as210 = ChunkTranslator.translateSpawnEntity758To210(boat758.bytes());
        assertNotNull(as210);
        PacketIO.Reader reader = new PacketIO.Reader(as210);
        assertEquals(10, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals(1, reader.readByte()); // オブジェクト表の boat = 1
        assertTrue(ChunkTranslator.isObjectEntity210(104)); // wither_skull
    }

    @Test
    void spawnLiving210UsesMobId90ForPig() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PacketIO.Writer living758 = new PacketIO.Writer();
        living758.writeVarInt(20);
        living758.writeUuid(uuid);
        living758.writeVarInt(64); // 758 の pig
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeShort(0);
        living758.writeShort(0);
        living758.writeShort(0);
        byte[] as210 = ChunkTranslator.translateSpawnLiving758To210(living758.bytes());
        assertNotNull(as210);
        PacketIO.Reader reader = new PacketIO.Reader(as210);
        assertEquals(20, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals(90, reader.readVarInt()); // 210 の pig はモブ ID 90
    }

    @Test
    void openWindow210UsesStringTypeAndSlotCount() {
        PacketIO.Writer window758 = new PacketIO.Writer();
        window758.writeVarInt(5); // windowId
        window758.writeVarInt(2); // generic_9x3
        window758.writeString("{\"text\":\"Chest\"}");
        byte[] as210 = ChunkTranslator.translateOpenWindow758To210(window758.bytes());
        assertNotNull(as210);
        PacketIO.Reader reader = new PacketIO.Reader(as210);
        assertEquals(5, reader.readUnsignedByte());
        assertEquals("minecraft:chest", reader.readString());
        assertEquals("Chest", reader.readString());
        assertEquals(27, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    // ---- 210 固有の形式差分4件の検証 ----

    @Test
    void spawnLiving210TypeIsUnsignedByte() {
        // 210 側の type は u8。0xFF は varint として読むと後続を
        // 食って壊れるため、u8 で読めていることの固定になる。
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PacketIO.Writer living210 = new PacketIO.Writer();
        living210.writeVarInt(20);
        living210.writeUuid(uuid);
        living210.writeByte(0xFF); // 範囲外の種別（u8 で 255）
        living210.writeDouble(0);
        living210.writeDouble(0);
        living210.writeDouble(0);
        living210.writeByte(0);
        living210.writeByte(0);
        living210.writeByte(0);
        living210.writeShort(0);
        living210.writeShort(0);
        living210.writeShort(0);
        byte[] as758 = ChunkTranslator.spawnLiving210To758(living210.bytes())[0];
        assertNotNull(as758);
        assertTrue(!java.util.Arrays.equals(living210.bytes(), as758));
        PacketIO.Reader reader = new PacketIO.Reader(as758);
        assertEquals(20, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals(RegistryRemapper.mapEntityType210To758(255),
                reader.readVarInt());
        // 逆向き：758 の pig (64) → 210 では 1 バイトの 90。
        PacketIO.Writer living758 = new PacketIO.Writer();
        living758.writeVarInt(20);
        living758.writeUuid(uuid);
        living758.writeVarInt(64);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeShort(0);
        living758.writeShort(0);
        living758.writeShort(0);
        byte[] as210 = ChunkTranslator.translateSpawnLiving758To210(living758.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(as210);
        assertEquals(20, r2.readVarInt());
        assertEquals(uuid, r2.readUuid());
        assertEquals(90, r2.readUnsignedByte());
    }

    @Test
    void blockPlaceCursorConvertsBetweenI8AndF32() {
        // 758 → 210：カーソル f32 (0.0, 0.5, 1.0) → i8 (0, 8, 15)。
        PacketIO.Writer place758 = new PacketIO.Writer();
        place758.writeVarInt(0); // hand
        place758.writeLong((0L << 38) | (0L << 12) | 64L); // 758 位置 (0,64,0)
        place758.writeVarInt(1); // face
        place758.writeFloat(0.0f);
        place758.writeFloat(0.5f);
        place758.writeFloat(1.0f);
        place758.writeBoolean(false); // insideBlock
        byte[] as210 = ChunkTranslator.translateBlockPlace758To210(place758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals(64L << 26, r.readLong()); // 210 位置 (0,64,0)
        assertEquals(1, r.readVarInt()); // face
        assertEquals(0, r.readVarInt()); // hand
        assertEquals(0, r.readByte());
        assertEquals(8, r.readByte());
        assertEquals(15, r.readByte());
        assertEquals(0, r.remaining());
        // 210 → 758：i8 (0, 8, 15) → f32 (0.0, 8/15, 1.0)。
        PacketIO.Writer place210 = new PacketIO.Writer();
        place210.writeLong(64L << 26);
        place210.writeVarInt(1);
        place210.writeVarInt(0);
        place210.writeByte(0);
        place210.writeByte(8);
        place210.writeByte(15);
        byte[] back758 = ChunkTranslator.translateBlockPlace210To758(place210.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(back758);
        assertEquals(0, r2.readVarInt()); // hand
        assertEquals(64L, r2.readLong()); // 758 位置 (0,64,0)
        assertEquals(1, r2.readVarInt()); // face
        assertEquals(0.0f, r2.readFloat());
        assertEquals(8 / 15.0f, r2.readFloat());
        assertEquals(1.0f, r2.readFloat());
        assertEquals(false, r2.readBoolean()); // insideBlock（補われる）
        assertEquals(0, r2.remaining());
    }

    @Test
    void collectCountFieldOnlyExistsOn758Side() {
        PacketIO.Writer collect758 = new PacketIO.Writer();
        collect758.writeVarInt(5);
        collect758.writeVarInt(7);
        collect758.writeVarInt(3); // pickupItemCount
        byte[] as210 = ChunkTranslator.translateCollect758To210(collect758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals(5, r.readVarInt());
        assertEquals(7, r.readVarInt());
        assertEquals(0, r.remaining());
        PacketIO.Writer collect210 = new PacketIO.Writer();
        collect210.writeVarInt(5);
        collect210.writeVarInt(7);
        byte[] back758 = ChunkTranslator.translateCollect210To758(collect210.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(back758);
        assertEquals(5, r2.readVarInt());
        assertEquals(7, r2.readVarInt());
        assertEquals(1, r2.readVarInt()); // 補われる個数
        assertEquals(0, r2.remaining());
    }

    @Test
    void titleActionsUse210Numbering() {
        // 758 の Set Title Text → 210 の action 0。
        PacketIO.Writer text758 = new PacketIO.Writer();
        text758.writeString("{\"text\":\"Hi\"}");
        byte[] as210 = ChunkTranslator.titleText758To210(text758.bytes());
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals(0, r.readVarInt());
        assertEquals("{\"text\":\"Hi\"}", r.readString());
        // 758 の Set Title Time → 210 の action 2（315 では 3）。
        PacketIO.Writer time758 = new PacketIO.Writer();
        time758.writeInt(10);
        time758.writeInt(70);
        time758.writeInt(20);
        byte[] time210 = ChunkTranslator.titleAnimation758To210(time758.bytes());
        PacketIO.Reader r2 = new PacketIO.Reader(time210);
        assertEquals(2, r2.readVarInt());
        assertEquals(10, r2.readInt());
        assertEquals(70, r2.readInt());
        assertEquals(20, r2.readInt());
        // 758 の Clear Titles → 210 の action 3（消去）／4（リセット）。
        PacketIO.Writer clear758 = new PacketIO.Writer();
        clear758.writeBoolean(false);
        PacketIO.Reader r3 = new PacketIO.Reader(
                ChunkTranslator.titleClear758To210(clear758.bytes()));
        assertEquals(3, r3.readVarInt());
        PacketIO.Writer reset758 = new PacketIO.Writer();
        reset758.writeBoolean(true);
        PacketIO.Reader r4 = new PacketIO.Reader(
                ChunkTranslator.titleClear758To210(reset758.bytes()));
        assertEquals(4, r4.readVarInt());
        // 逆向き：210 の action 2 は時間、3 は消去 (false)、4 はリセット (true)。
        assertEquals(2, ChunkTranslator.titleAction210(time210));
        byte[] timeBack = ChunkTranslator.translateTitle210To758(time210);
        PacketIO.Reader r5 = new PacketIO.Reader(timeBack);
        assertEquals(10, r5.readInt());
        assertEquals(70, r5.readInt());
        assertEquals(20, r5.readInt());
        PacketIO.Writer clear210 = new PacketIO.Writer();
        clear210.writeVarInt(3);
        PacketIO.Reader r6 = new PacketIO.Reader(
                ChunkTranslator.translateTitle210To758(clear210.bytes()));
        assertEquals(false, r6.readBoolean());
        PacketIO.Writer reset210 = new PacketIO.Writer();
        reset210.writeVarInt(4);
        PacketIO.Reader r7 = new PacketIO.Reader(
                ChunkTranslator.translateTitle210To758(reset210.bytes()));
        assertEquals(true, r7.readBoolean());
    }

    @Test
    void actionBarTravelsAsChatPosition2() {
        PacketIO.Writer bar758 = new PacketIO.Writer();
        bar758.writeString("{\"text\":\"Bar\"}");
        byte[] as210 = ChunkTranslator.actionBar758ToChat210(bar758.bytes());
        assertEquals(2, ChunkTranslator.chatPosition210(as210));
        PacketIO.Reader r = new PacketIO.Reader(as210);
        assertEquals("{\"text\":\"Bar\"}", r.readString());
        assertEquals(2, r.readByte());
        assertEquals(0, r.remaining());
        byte[] back = ChunkTranslator.chat210ToActionBar758(as210);
        PacketIO.Reader r2 = new PacketIO.Reader(back);
        assertEquals("{\"text\":\"Bar\"}", r2.readString());
        assertEquals(0, r2.remaining());
        // 通常のチャット（position 0）はアクションバーではない。
        PacketIO.Writer chat210 = new PacketIO.Writer();
        chat210.writeString("{\"text\":\"Chat\"}");
        chat210.writeByte(0);
        assertEquals(0, ChunkTranslator.chatPosition210(chat210.bytes()));
    }

}
