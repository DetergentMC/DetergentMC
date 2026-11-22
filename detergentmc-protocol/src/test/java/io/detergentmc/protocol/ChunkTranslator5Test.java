package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

/**
 * 5（1.7.6〜1.7.10）のチャンク変換器。1.7 のチャンクは種類別に
 * まとまった配置（ブロック ID 配列→メタ nibble→ブロック光→
 * スカイライト→add 配列）で、47 の u16 LE 固定詰めとは別形式。
 * bulk のヘッダ配置（個数 i16・長さ i32 が先頭、メタは末尾）も含め、
 * Stage A の実ワイヤ採取で確定したレイアウトを固定する。
 */
class ChunkTranslator5Test {

    private static byte[] zlib(byte[] raw) {
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] compressed) {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                out.write(buffer, 0, n);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalArgumentException(e);
        }
        inflater.end();
        return out.toByteArray();
    }

    /** 1セクション分のチャンクデータ（石・赤羊毛・草、バイオームは plains=1）。 */
    private static byte[] sectionData() {
        Writer data = new Writer();
        for (int i = 0; i < 4096; i++) {
            int id = i == 0 ? 1 : i == 1 ? 35 : i == 2 ? 2 : 0;
            data.writeByte(id);
        }
        // メタ nibble：index1（赤羊毛）に 14
        for (int i = 0; i < 2048; i++) {
            data.writeByte(i == 0 ? 0xE0 : 0);
        }
        data.writeBytes(new byte[2048]); // ブロック光
        data.writeBytes(new byte[2048]); // スカイライト
        for (int i = 0; i < 256; i++) {
            data.writeByte(1); // バイオーム（plains）
        }
        return data.bytes();
    }

    /** 5 の単体チャンク（x=3, z=-2、1セクション、スカイライトあり）。 */
    private static byte[] chunk5() {
        byte[] compressed = zlib(sectionData());
        Writer writer = new Writer();
        writer.writeInt(3);
        writer.writeInt(-2);
        writer.writeBoolean(true);
        writer.writeShort(1); // primaryBitMap
        writer.writeShort(0); // addBitMap
        writer.writeInt(compressed.length);
        writer.writeBytes(compressed);
        return writer.bytes();
    }

    @Test
    void chunk5RoundTripsThrough758() {
        byte[] out = ChunkTranslator.translateChunkData5To758(chunk5(), true);
        assertNotNull(out);
        byte[] back = ChunkTranslator.translateChunkData758To5(out, true);
        Reader reader = new Reader(back);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readUnsignedShort());
        assertEquals(0, reader.readUnsignedShort());
        int size = reader.readInt();
        byte[] raw = inflate(reader.readBytes(size));
        // 種類別配置：ID 配列→メタ→光→光→バイオーム
        assertEquals(1, raw[0] & 0xFF); // 石
        assertEquals(35, raw[1] & 0xFF); // 羊毛
        assertEquals(2, raw[2] & 0xFF); // 草
        assertEquals(0xE0, raw[4096] & 0xFF); // メタ nibble（index1 = 14）
        for (int i = 0; i < 256; i++) {
            assertEquals(1, raw[10240 + i] & 0xFF, "biome " + i);
        }
        assertEquals(10496, raw.length);
        assertEquals(0, reader.remaining());
    }

    @Test
    void chunkBulk5SplitsIntoSingleChunks() {
        byte[] dataA = sectionData();
        byte[] dataB = sectionData();
        Writer blob = new Writer();
        blob.writeBytes(dataA);
        blob.writeBytes(dataB);
        byte[] compressed = zlib(blob.bytes());
        Writer writer = new Writer();
        writer.writeShort(2); // 個数（i16）
        writer.writeInt(compressed.length); // データ長（i32）
        writer.writeBoolean(true); // skyLightSent
        writer.writeBytes(compressed);
        // メタ配列は末尾：x, z, primaryBitMap, addBitMap
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeShort(1);
        writer.writeShort(0);
        writer.writeInt(1);
        writer.writeInt(0);
        writer.writeShort(1);
        writer.writeShort(0);
        List<byte[]> chunks = ChunkTranslator.splitChunkBulk5(writer.bytes());
        assertEquals(2, chunks.size());
        Reader ra = new Reader(chunks.get(0));
        assertEquals(0, ra.readInt());
        assertEquals(0, ra.readInt());
        assertEquals(true, ra.readBoolean());
        assertEquals(1, ra.readUnsignedShort());
        Reader rb = new Reader(chunks.get(1));
        assertEquals(1, rb.readInt());
        assertEquals(0, rb.readInt());
        // 分割した単体チャンクはそのまま 758 形式へ変換できる。
        assertNotNull(ChunkTranslator.translateChunkData5To758(
                chunks.get(0), true));
    }

    @Test
    void multiBlockChangeRoundTrips() {
        // 内部正準形式：セクション位置 i64＋suppressLight＋件数 varint
        // ＋レコード varlong（状態<<12 | ローカル位置）。
        Writer writer = new Writer();
        writer.writeLong(4); // セクション位置（x=0, z=0, y=4）
        writer.writeBoolean(false); // suppressLight
        writer.writeVarInt(1); // records
        writer.writeVarLong((1L << 12) | (1 << 8) | (2 << 4) | 3); // 石, x=1,z=2,y=3
        byte[] five = ChunkTranslator.translateMultiBlockChange758To5(
                writer.bytes());
        // 5 の形式：chunkX/Z i32、個数 i16、データ長 i32、レコード4バイト
        assertEquals(4 + 4 + 2 + 4 + 4, five.length);
        Reader fr = new Reader(five);
        assertEquals(0, fr.readInt());
        assertEquals(0, fr.readInt());
        assertEquals(1, fr.readShort());
        assertEquals(4, fr.readInt());
        assertEquals(0x00, fr.readUnsignedByte()); // (meta<<4)|(blockId>>8)：石は 0
        assertEquals(0x01, fr.readUnsignedByte()); // blockId 下位
        assertEquals(67, fr.readUnsignedByte()); // y = 4*16+3
        assertEquals(0x21, fr.readUnsignedByte()); // (z<<4)|x
        assertEquals(0, fr.remaining());
        List<byte[]> back = ChunkTranslator.translateMultiBlockChange5To758(five);
        assertEquals(1, back.size());
        Reader reader = new Reader(back.get(0));
        assertEquals(4, reader.readLong());
        assertEquals(true, reader.readBoolean()); // suppressLight は true で合成
        assertEquals(1, reader.readVarInt());
        assertEquals((1L << 12) | (1 << 8) | (2 << 4) | 3, reader.readVarLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntityArmorStandDropsAndArrowPasses() {
        // 防具立て（758 の type 1）は 1.7 に無いため落とす。
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeBytes(new byte[16]);
        writer.writeVarInt(1); // armor_stand
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeInt(0); // data
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        assertNull(ChunkTranslator.translateSpawnEntity758To5(writer.bytes()));
        // 矢（758 の type 2）は 47 経由で変換される（null でない）。
        Writer arrow = new Writer();
        arrow.writeVarInt(8);
        arrow.writeBytes(new byte[16]);
        arrow.writeVarInt(2); // arrow
        arrow.writeDouble(0.5);
        arrow.writeDouble(64.0);
        arrow.writeDouble(0.5);
        arrow.writeByte(0);
        arrow.writeByte(0);
        arrow.writeInt(99); // data（射手の entityId）→ 速度あり
        arrow.writeShort(10);
        arrow.writeShort(0);
        arrow.writeShort(0);
        assertNotNull(ChunkTranslator.translateSpawnEntity758To5(arrow.bytes()));
    }
}
