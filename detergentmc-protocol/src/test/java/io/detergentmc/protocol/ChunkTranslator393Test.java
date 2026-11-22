package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 393（1.13）のチャンク・座標・スポーン変換。形式は 404 と同一世代：
 * チャンクに heightmap が無く光はセクション内蔵、ビット詰めは
 * ロング境界をまたぐ旧式（crossing）、座標の詰め方は x26|y12|z26、
 * Spawn Entity はオブジェクト表の i8 種別で生物系は Spawn Entity
 * Living に分かれる。404 と違うのはレジストリ番号だけで、サンゴで
 * 固定する（393 の tube_coral は状態 8459・プロパティ無し、758 では
 * waterlogged=true の 9780）。
 */
class ChunkTranslator393Test {

    /** 393 のチャンク1個（セクション0のみ、石・草・サンゴ、光は全ゼロ、バイオームは plains）。 */
    private static byte[] chunk393() {
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true); // groundUp
        writer.writeVarInt(1); // mask: section 0
        Writer buffer = new Writer();
        Writer section = new Writer();
        // 393 のセクションに blockCount は無い（1.14 から）。bits から始まる。
        section.writeUnsignedByte(4); // bits per block（パレット）
        section.writeVarInt(4);
        section.writeVarInt(0); // air
        section.writeVarInt(1); // stone
        section.writeVarInt(9); // grass_block
        section.writeVarInt(8459); // tube_coral（393 の状態番号）
        long[] data = new long[256];
        data[0] = 1; // (0,0,0) = パレット1（stone）
        data[1] = 2; // (0,1,0) = パレット2（grass_block）
        data[2] = 3; // (0,2,0) = パレット3（tube_coral）
        section.writeVarInt(256);
        for (long l : data) {
            section.writeLong(l);
        }
        section.writeBytes(new byte[2048]); // ブロックライト
        section.writeBytes(new byte[2048]); // スカイライト
        buffer.writeBytes(section.bytes());
        for (int i = 0; i < 256; i++) {
            buffer.writeInt(1); // バイオーム列（plains）＝バッファ末尾
        }
        byte[] bufferBytes = buffer.bytes();
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
        writer.writeVarInt(0); // ブロックエンティティなし
        return writer.bytes();
    }

    @Test
    void chunk393RoundTripsThrough758() {
        byte[] out = ChunkTranslator.translateChunkData393To758(chunk393(), true);
        assertNotNull(out);
        byte[] back = ChunkTranslator.translateChunkData758To393(out, true);
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
        assertEquals(1, palette[(int) (data[0] & 0xF)]); // (0,0,0) = stone
        assertEquals(9, palette[(int) (data[1] & 0xF)]); // ブロック16 = grass_block
        // ブロック32 = tube_coral。758 を往復しても 393 の状態 8459 に戻る
        // （758 側では waterlogged=true の 9780 になっている）。
        assertEquals(8459, palette[(int) (data[2] & 0xF)]);
        assertEquals(0, palette[(int) ((data[0] >>> 4) & 0xF)]); // ブロック1 = air
        // 光（2048×2）が戻り、バイオーム 256 件が plains のまま残る。
        buffer.readBytes(2048);
        buffer.readBytes(2048);
        for (int i = 0; i < 256; i++) {
            assertEquals(1, buffer.readInt(), "biome " + i);
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
    void chunkSection393UsesCrossingPacking() {
        // 404 のテストと同じ構成（bits=5・パレット20件、状態 0..19 は
        // サンゴより前の並びで 393・404 同一）。
        int[] palette = new int[20];
        for (int i = 0; i < 20; i++) {
            palette[i] = i;
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
            buffer.writeInt(1);
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
        byte[] as758 = ChunkTranslator.translateChunkData393To758(chunk.bytes(), true);
        assertNotNull(as758);
        byte[] back = ChunkTranslator.translateChunkData758To393(as758, true);
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
    void positionPackingDiffersBetween393And758() {
        // 758 形式：x=1, z=3, y=2 → (1<<38)|(3<<12)|2
        long raw758 = (1L << 38) | (3L << 12) | 2L;
        // 393 形式：x=1, y=2, z=3 → (1<<38)|(2<<26)|3
        long raw393 = (1L << 38) | (2L << 26) | 3L;
        assertEquals(raw393, ChunkTranslator.repackPosition758To393(raw758));
        assertEquals(raw758, ChunkTranslator.repackPosition393To758(raw393));
        // 負座標のビットパターンも往復で保存される。
        long negative758 = (-5L << 38) | (300L << 12) | 64L;
        assertEquals(negative758, ChunkTranslator.repackPosition393To758(
                ChunkTranslator.repackPosition758To393(negative758)));
    }

    @Test
    void spawnEntitySplitsObjectAndLiving() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        // 758 の Spawn Entity（ボート＝オブジェクト系）→ 393 は i8 のオブジェクト ID。
        Writer boat758 = new Writer();
        boat758.writeVarInt(10);
        boat758.writeUuid(uuid);
        boat758.writeVarInt(7); // 758 の boat
        boat758.writeDouble(1.0);
        boat758.writeDouble(64.0);
        boat758.writeDouble(2.0);
        boat758.writeByte(0); // pitch
        boat758.writeByte(0); // yaw
        boat758.writeInt(0); // data（758 は i32）
        boat758.writeShort(0);
        boat758.writeShort(0);
        boat758.writeShort(0);
        byte[] as393 = ChunkTranslator.translateSpawnEntity758To393(boat758.bytes());
        assertNotNull(as393);
        Reader reader = new Reader(as393);
        assertEquals(10, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals(1, reader.readByte()); // オブジェクト表の boat = 1
        // 758 の type 104 は wither_skull（オブジェクト系）なので変換できる。
        Writer skull758 = new Writer();
        skull758.writeVarInt(11);
        skull758.writeUuid(uuid);
        skull758.writeVarInt(104); // wither_skull
        skull758.writeDouble(0);
        skull758.writeDouble(0);
        skull758.writeDouble(0);
        skull758.writeByte(0);
        skull758.writeByte(0);
        skull758.writeInt(0); // data（758 は i32）
        skull758.writeShort(0);
        skull758.writeShort(0);
        skull758.writeShort(0);
        assertNotNull(ChunkTranslator.translateSpawnEntity758To393(skull758.bytes()));
        assertTrue(ChunkTranslator.isObjectEntity393(104));
        // ゾンビ（758 の 107、生物系）は Spawn Entity では送れず null。
        Writer zombie758 = new Writer();
        zombie758.writeVarInt(12);
        zombie758.writeUuid(uuid);
        zombie758.writeVarInt(107); // zombie
        zombie758.writeDouble(0);
        zombie758.writeDouble(0);
        zombie758.writeDouble(0);
        zombie758.writeByte(0);
        zombie758.writeByte(0);
        zombie758.writeInt(0); // data（758 は i32）
        zombie758.writeShort(0);
        zombie758.writeShort(0);
        zombie758.writeShort(0);
        assertNull(ChunkTranslator.translateSpawnEntity758To393(zombie758.bytes()));
    }

    @Test
    void spawnLiving393AppendsTerminatorAndSplitsMetadata() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer living758 = new Writer();
        living758.writeVarInt(20);
        living758.writeUuid(uuid);
        living758.writeVarInt(2);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeShort(0);
        living758.writeShort(0);
        living758.writeShort(0);
        byte[] as393 = ChunkTranslator.translateSpawnLiving758To393(
                replaceType(living758.bytes(), pig758Type()));
        assertNotNull(as393);
        Reader reader = new Reader(as393);
        assertEquals(20, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals(51, reader.readVarInt()); // 393 の pig は 51（404 と同じ並び）
        byte[] rest = reader.rest();
        assertEquals((byte) 0xFF, rest[rest.length - 1]);
    }

    /** 758 の pig のレジストリ ID（minecraft-data 1.18 の entities 順）。 */
    private static int pig758Type() {
        return 64;
    }

    private static byte[] replaceType(byte[] payload, int type758) {
        Reader reader = new Reader(payload);
        int entityId = reader.readVarInt();
        UUID uuid = reader.readUuid();
        reader.readVarInt();
        byte[] rest = reader.rest();
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeUuid(uuid);
        writer.writeVarInt(type758);
        writer.writeBytes(rest);
        return writer.bytes();
    }

    @Test
    void openWindow393UsesStringTypeAndSlotCount() {
        // 758 の Open Window（generic_9x3 = menu 2、タイトル JSON）→ 393 形式。
        Writer window758 = new Writer();
        window758.writeVarInt(5); // windowId
        window758.writeVarInt(2); // generic_9x3
        window758.writeString("{\"text\":\"Chest\"}");
        byte[] as393 = ChunkTranslator.translateOpenWindow758To393(window758.bytes());
        assertNotNull(as393);
        Reader reader = new Reader(as393);
        assertEquals(5, reader.readUnsignedByte()); // windowId は u8
        assertEquals("minecraft:chest", reader.readString());
        assertEquals("Chest", reader.readString()); // JSON から平文へ
        assertEquals(27, reader.readUnsignedByte()); // slotCount
        assertEquals(0, reader.remaining());
        // 逆向き：393 → 758（menu 番号に戻る）。
        byte[] back = ChunkTranslator.translateOpenWindow393To758(as393);
        assertNotNull(back);
        Reader backReader = new Reader(back);
        assertEquals(5, backReader.readVarInt());
        assertEquals(2, backReader.readVarInt());
    }

    @Test
    void tradeListHasNo393Form() {
        // Trade List は 393 に存在しないため、変換メソッド自体が無い
        // （ProxySession のガードで落とされる）。PacketIds で固定する。
        assertEquals(-1, PacketIds.PacketName.PLAY_TRADE_LIST.id393());
    }
}
