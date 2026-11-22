package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import org.junit.jupiter.api.Test;

/**
 * 404（1.13.2）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 404 のチャンク形式は 498 と別世代：heightmap NBT が無く、光は
 * セクション内蔵（2048＋2048 バイト）、ビット詰めはロング境界を
 * またぐ旧式（crossing）、バイオームはバッファ末尾の 256 件 i32。
 */
class BedrockChunkTranslator404Test {

    /** 404 のチャンク（セクション0に石と草、光は内蔵、バイオームはバッファ末尾）。 */
    private static byte[] chunk404() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(1); // section 0
        PacketIO.Writer buffer = new PacketIO.Writer();
        PacketIO.Writer section = new PacketIO.Writer();
        // 404 のセクションに blockCount は無い（1.14 から）。bits から始まる。
        section.writeUnsignedByte(4);
        section.writeVarInt(3);
        section.writeVarInt(0);
        section.writeVarInt(1); // stone
        section.writeVarInt(9); // grass_block
        long[] data = new long[256];
        // crossing 詰め（bits=4 では non-crossing と一致）。
        data[0] = 1;
        data[1] = 2;
        section.writeVarInt(256);
        for (long l : data) {
            section.writeLong(l);
        }
        section.writeBytes(new byte[2048]); // ブロックライト
        section.writeBytes(new byte[2048]); // スカイライト
        buffer.writeBytes(section.bytes());
        for (int i = 0; i < 256; i++) {
            buffer.writeInt(1); // plains（バッファ末尾の列バイオーム）
        }
        byte[] bufferBytes = buffer.bytes();
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
        writer.writeVarInt(0); // ブロックエンティティなし
        return writer.bytes();
    }

    @Test
    void chunk404TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk404(), 404, 0, 256, RegistryRemapper.biomeNames404()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void normalizeBlockState404Anchors() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 404)); // air
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 404)); // stone
        // 全範囲で例外なく 765 の範囲に収まる。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_404; i++) {
            int mapped = BedrockChunkTranslator.normalizeBlockState(i, 404);
            assertTrue(mapped >= 0, "state " + i);
        }
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 765),
                BedrockChunkTranslator.runtimeIdFor(1, 404));
    }
}
