package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 404（1.13.2）のチャンク・座標・スポーン変換。404 は 477 と別世代：
 * チャンクに heightmap が無く光はセクション内蔵、ビット詰めは
 * ロング境界をまたぐ旧式（crossing）、座標の詰め方は x26|y12|z26
 * （1.14 から x26|z26|y12 に変更）、Spawn Entity はオブジェクト表の
 * i8 種別で生物系は Spawn Entity Living に分かれる。
 */
class ChunkTranslator404Test {

    /** 404 のチャンク1個（セクション0のみ、石と草、光は全ゼロ、バイオームは plains）。 */
    private static byte[] chunk404() {
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true); // groundUp
        writer.writeVarInt(1); // mask: section 0
        Writer buffer = new Writer();
        Writer section = new Writer();
        // 404 のセクションに blockCount は無い（1.14 から）。bits から始まる。
        section.writeUnsignedByte(4); // bits per block（パレット）
        section.writeVarInt(3);
        section.writeVarInt(0); // air
        section.writeVarInt(1); // stone
        section.writeVarInt(9); // grass_block
        long[] data = new long[256];
        // crossing 詰め（bits=4 では non-crossing と一致する）：index 0 は long0 の offset 0、
        // index 16 は long1 の offset 0。
        data[0] = 1; // (0,0,0) = パレット1（stone）
        data[1] = 2; // (0,1,0) = パレット2（grass_block）
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
    void chunk404RoundTripsThrough758() {
        byte[] out = ChunkTranslator.translateChunkData404To758(chunk404(), true);
        assertNotNull(out);
        byte[] back = ChunkTranslator.translateChunkData758To404(out, true);
        Reader reader = new Reader(back);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readVarInt()); // mask はセクション0のみ
        int size = reader.readVarInt();
        Reader buffer = new Reader(reader.readBytes(size));
        assertEquals(4, buffer.readUnsignedByte()); // bits per block
        assertEquals(3, buffer.readVarInt()); // パレット長
        // パレットの並びは出現順で決まるため、表を引き直して状態を検証する。
        int[] palette = {buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()};
        assertEquals(256, buffer.readVarInt());
        long[] data = new long[256];
        for (int i = 0; i < 256; i++) {
            data[i] = buffer.readLong();
        }
        // crossing 詰め（bits=4 では non-crossing と一致）：ブロック0 は long0 の
        // 下位4ビット、ブロック16 は long1 の下位4ビット。
        assertEquals(1, palette[(int) (data[0] & 0xF)]); // (0,0,0) = stone
        assertEquals(9, palette[(int) (data[1] & 0xF)]); // ブロック16 = grass_block
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
    void chunkSection404UsesCrossingPacking() {
        // bits=5・パレット20件のセクション。インデックス12の値は
        // ビット60〜64に乗り、long 境界をまたぐ（non-crossing では
        // 342 long 必要なところ、crossing では 320 long ちょうど）。
        // 実バニラ 1.13.2 の自然地形チャンクで確認した形式。
        int[] palette = new int[20];
        for (int i = 0; i < 20; i++) {
            palette[i] = i; // 404 のブロック状態 0..19
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
        // 404→758：パレット参照が範囲外にならず、状態が写ること。
        byte[] as758 = ChunkTranslator.translateChunkData404To758(chunk.bytes(), true);
        assertNotNull(as758);
        // 758→404 と戻し、テスト側の crossing 解きで状態を検証する。
        byte[] back = ChunkTranslator.translateChunkData758To404(as758, true);
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
        // 戻りのパレットは出現順に作り直されるため、インデックスではなく
        // パレット経由の状態で全ブロックを比較する。
        for (int idx = 0; idx < 4096; idx++) {
            assertEquals(palette[entries[idx]], palBack[entriesBack[idx]],
                    "state at " + idx);
        }
    }

    @Test
    void positionPackingDiffersBetween404And758() {
        // 758 形式：x=1, z=3, y=2 → (1<<38)|(3<<12)|2
        long raw758 = (1L << 38) | (3L << 12) | 2L;
        // 404 形式：x=1, y=2, z=3 → (1<<38)|(2<<26)|3
        long raw404 = (1L << 38) | (2L << 26) | 3L;
        assertEquals(raw404, ChunkTranslator.repackPosition758To404(raw758));
        assertEquals(raw758, ChunkTranslator.repackPosition404To758(raw404));
        // 負座標のビットパターンも往復で保存される。
        long negative758 = (-5L << 38) | (300L << 12) | 64L;
        assertEquals(negative758, ChunkTranslator.repackPosition404To758(
                ChunkTranslator.repackPosition758To404(negative758)));
    }

    @Test
    void spawnEntitySplitsObjectAndLiving() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        // 758 の Spawn Entity（ボート＝オブジェクト系）→ 404 は i8 のオブジェクト ID。
        Writer boat758 = new Writer();
        boat758.writeVarInt(10);
        boat758.writeUuid(uuid);
        boat758.writeVarInt(7); // 758 の boat
        boat758.writeDouble(1.0);
        boat758.writeDouble(64.0);
        boat758.writeDouble(2.0);
        boat758.writeByte(0); // pitch
        boat758.writeByte(0); // yaw
        boat758.writeInt(0); // data（758 は i32。headPitch は 759 から）
        boat758.writeShort(0);
        boat758.writeShort(0);
        boat758.writeShort(0);
        byte[] as404 = ChunkTranslator.translateSpawnEntity758To404(boat758.bytes());
        assertNotNull(as404);
        Reader reader = new Reader(as404);
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
        assertNotNull(ChunkTranslator.translateSpawnEntity758To404(skull758.bytes()));
        assertTrue(ChunkTranslator.isObjectEntity404(104));
        // ゾンビ（758 の 107、生物系）は Spawn Entity では送れず null
        // （404 では Spawn Entity Living 側で送る）。
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
        assertNull(ChunkTranslator.translateSpawnEntity758To404(zombie758.bytes()));
    }

    @Test
    void spawnLiving404AppendsTerminatorAndSplitsMetadata() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer living758 = new Writer();
        living758.writeVarInt(20);
        living758.writeUuid(uuid);
        living758.writeVarInt(2); // 758 の arrow ではなく生物として扱うため entityId は別途
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeDouble(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeByte(0);
        living758.writeShort(0);
        living758.writeShort(0);
        living758.writeShort(0);
        byte[] as404 = ChunkTranslator.translateSpawnLiving758To404(
                replaceType(living758.bytes(), pig758Type()));
        assertNotNull(as404);
        Reader reader = new Reader(as404);
        assertEquals(20, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals(51, reader.readVarInt()); // 404 の pig は 51（実サーバーで確認済み）
        // 座標・角度・速度の後にメタデータ終端 0xFF が付く。
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
    void openWindow404UsesStringTypeAndSlotCount() {
        // 758 の Open Window（generic_9x3 = menu 2、タイトル JSON）→ 404 形式。
        Writer window758 = new Writer();
        window758.writeVarInt(5); // windowId
        window758.writeVarInt(2); // generic_9x3
        window758.writeString("{\"text\":\"Chest\"}");
        byte[] as404 = ChunkTranslator.translateOpenWindow758To404(window758.bytes());
        assertNotNull(as404);
        Reader reader = new Reader(as404);
        assertEquals(5, reader.readUnsignedByte()); // windowId は u8
        assertEquals("minecraft:chest", reader.readString());
        assertEquals("Chest", reader.readString()); // JSON から平文へ
        assertEquals(27, reader.readUnsignedByte()); // slotCount
        assertEquals(0, reader.remaining());
        // 逆向き：404 → 758（menu 番号に戻る）。
        byte[] back = ChunkTranslator.translateOpenWindow404To758(as404);
        assertNotNull(back);
        Reader backReader = new Reader(back);
        assertEquals(5, backReader.readVarInt());
        assertEquals(2, backReader.readVarInt());
    }

    @Test
    void tradeListHasNo404Form() {
        // Trade List は 404 に存在しないため、変換メソッド自体が無い
        // （ProxySession のガードで落とされる）。ここでは PacketIds で固定する。
        assertEquals(-1, PacketIds.PacketName.PLAY_TRADE_LIST.id404());
    }
}
