package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import org.junit.jupiter.api.Test;

/**
 * 107（1.9）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * チャンクの骨格は 108 と同一（heightmap 無し、光はセクション内蔵、
 * 旧式 crossing 詰め、パレット値は (数値ID&lt;&lt;4)|メタデータ、
 * 直接パレットは13ビット、バイオームは末尾の u8×256、末尾の
 * ブロックエンティティ列は無い）。107 固有の癖として、dataLen の
 * 内側に大きなゼロ埋めが含まれていても（Stage A 実測）変換できる
 * ことを固定する。
 */
class BedrockChunkTranslator107Test {

    /** 107 のチャンク本体（セクション0に石・草・白羊毛、光は内蔵、バイオームは末尾 u8）。 */
    private static byte[] chunkBuffer107(boolean padded) {
        PacketIO.Writer buffer = new PacketIO.Writer();
        PacketIO.Writer section = new PacketIO.Writer();
        section.writeUnsignedByte(4);
        section.writeVarInt(4);
        section.writeVarInt(0); // air
        section.writeVarInt(16); // stone (1<<4)|0
        section.writeVarInt(32); // grass_block (2<<4)|0
        section.writeVarInt(560); // white wool (35<<4)|0
        long[] data = new long[256];
        data[0] = 1;
        data[1] = 2;
        data[2] = 3; // white wool
        section.writeVarInt(256);
        for (long l : data) {
            section.writeLong(l);
        }
        section.writeBytes(new byte[2048]); // ブロックライト
        section.writeBytes(new byte[2048]); // スカイライト
        buffer.writeBytes(section.bytes());
        for (int i = 0; i < 256; i++) {
            buffer.writeByte(1); // plains（バッファ末尾の u8 バイオーム）
        }
        if (padded) {
            // 107 の癖：データ領域の末尾に大きなゼロ埋めが付く。
            buffer.writeBytes(new byte[1536]);
        }
        return buffer.bytes();
    }

    private static byte[] chunk107(boolean padded) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(1); // section 0
        byte[] bufferBytes = chunkBuffer107(padded);
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
        // 107 には末尾のブロックエンティティ列が無い。
        return writer.bytes();
    }

    @Test
    void chunk107TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk107(false), 107, 0, 256, RegistryRemapper.biomeNames107()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void chunk107WithZeroPaddingTranslatesTheSame() {
        TranslatedChunk plain = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk107(false), 107, 0, 256, RegistryRemapper.biomeNames107()));
        TranslatedChunk padded = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk107(true), 107, 0, 256, RegistryRemapper.biomeNames107()));
        assertArrayEquals(plain.payload(), padded.payload());
    }

    @Test
    void normalizeBlockState107Matches108() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 107)); // air
        assertEquals(BedrockChunkTranslator.normalizeBlockState(1, 393),
                BedrockChunkTranslator.normalizeBlockState(16, 107));
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 393),
                BedrockChunkTranslator.runtimeIdFor(16, 107));
        // 107 の正規化は全範囲で 108 と一致する（同じ意味対応表）。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_110; i++) {
            assertEquals(BedrockChunkTranslator.normalizeBlockState(i, 108),
                    BedrockChunkTranslator.normalizeBlockState(i, 107), "state " + i);
        }
    }
}
