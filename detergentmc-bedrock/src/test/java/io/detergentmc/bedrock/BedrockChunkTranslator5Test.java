package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

/**
 * 5（1.7.10）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 5 のチャンクは zlib 圧縮＋種類別グループ化レイアウトで、47 の
 * 固定詰めとは別形式。同じ内容の 47 チャンクと翻訳結果が一致する
 * ことを固定する（詰め替えの正しさの担保）。
 */
class BedrockChunkTranslator5Test {

    /** セクション0: index0=石（1,0）、index1=赤羊毛（35,14）。バイオームは平原（1）。 */
    private static byte[] sectionContent5(boolean sky) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] ids = new byte[4096];
        ids[0] = 1;
        ids[1] = 35;
        out.write(ids, 0, ids.length);
        byte[] meta = new byte[2048];
        meta[0] = (byte) 0xE0; // index1 のメタは上位 nibble → 14
        out.write(meta, 0, meta.length);
        out.write(new byte[2048], 0, 2048); // ブロックライト
        if (sky) {
            out.write(new byte[2048], 0, 2048); // スカイライト
        }
        out.write(new byte[2048], 0, 2048); // add 配列
        byte[] biomes = new byte[256];
        java.util.Arrays.fill(biomes, (byte) 1);
        out.write(biomes, 0, biomes.length);
        return out.toByteArray();
    }

    private static byte[] chunk5() {
        byte[] raw = sectionContent5(true);
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] compressed = new byte[raw.length + 64];
        int length = deflater.deflate(compressed);
        deflater.end();
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(3);
        writer.writeInt(-2);
        writer.writeBoolean(true);
        writer.writeShort((short) 0x0001); // primaryBitMap: section 0
        writer.writeShort((short) 0); // addBitMap
        writer.writeVarInt(length);
        writer.writeBytes(java.util.Arrays.copyOf(compressed, length));
        return writer.bytes();
    }

    /** 同じ内容の 47 チャンク（u16 LE 固定詰め、スカイライトあり）。 */
    private static byte[] chunk47() {
        PacketIO.Writer data = new PacketIO.Writer();
        for (int i = 0; i < 4096; i++) {
            int value = i == 0 ? (1 << 4) : i == 1 ? ((35 << 4) | 14) : 0;
            data.writeByte(value & 0xFF);
            data.writeByte((value >> 8) & 0xFF);
        }
        data.writeBytes(new byte[2048]); // ブロックライト
        data.writeBytes(new byte[2048]); // スカイライト
        byte[] biomes = new byte[256];
        java.util.Arrays.fill(biomes, (byte) 1);
        data.writeBytes(biomes);
        byte[] dataBytes = data.bytes();
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(3);
        writer.writeInt(-2);
        writer.writeBoolean(true);
        writer.writeShort((short) 0x0001);
        writer.writeVarInt(dataBytes.length);
        writer.writeBytes(dataBytes);
        return writer.bytes();
    }

    @Test
    void chunk5MatchesEquivalent47Chunk() {
        TranslatedChunk five = BedrockChunkTranslator.translate(new ChunkInput(
                3, -2, chunk5(), RegistryRemapper.PROTOCOL_5, 0, 256, List.of()));
        TranslatedChunk fortySeven = BedrockChunkTranslator.translate(new ChunkInput(
                3, -2, chunk47(), RegistryRemapper.PROTOCOL_47, 0, 256, List.of()));
        assertEquals(3, five.chunkX());
        assertEquals(-2, five.chunkZ());
        assertEquals(16, five.subChunkCount());
        assertArrayEquals(fortySeven.payload(), five.payload());
    }
}
