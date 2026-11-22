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
 * 4（1.7.2〜1.7.5）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 4 のチャンク形式は 5 と完全同一（zlib 圧縮＋種類別グループ化
 * レイアウト）のため、同じペイロードを 5 として翻訳した結果と
 * 一致することを固定する。
 */
class BedrockChunkTranslator4Test {

    /** セクション0: index0=石（1,0）、index1=赤羊毛（35,14）。バイオームは平原（1）。 */
    private static byte[] sectionContent(boolean sky) {
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

    private static byte[] chunk17() {
        byte[] raw = sectionContent(true);
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

    @Test
    void chunk4Matches5Translation() {
        TranslatedChunk four = BedrockChunkTranslator.translate(new ChunkInput(
                3, -2, chunk17(), RegistryRemapper.PROTOCOL_4, 0, 256, List.of()));
        TranslatedChunk five = BedrockChunkTranslator.translate(new ChunkInput(
                3, -2, chunk17(), RegistryRemapper.PROTOCOL_5, 0, 256, List.of()));
        assertEquals(3, four.chunkX());
        assertEquals(-2, four.chunkZ());
        assertEquals(16, four.subChunkCount());
        assertArrayEquals(five.payload(), four.payload());
    }
}
