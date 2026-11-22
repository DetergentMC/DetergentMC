package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import org.junit.jupiter.api.Test;

/**
 * 338（1.12.1）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 340 のチャンクは骨格が 404 と同じ（heightmap 無し、光はセクション
 * 内蔵、旧式 crossing 詰め）だが、パレット値は (数値ID&lt;&lt;4)|
 * メタデータ、直接パレットは13ビット、バイオームは末尾の u8×256。
 * パースは 340 と共用（338 のチャンク形式は 340 と完全同一）、
 * 正規化は意味対応表で振り分けることを固定する。
 */
class BedrockChunkTranslator338Test {

    /** 338 のチャンク（セクション0に石・草・白羊毛、光は内蔵、バイオームは末尾 u8）。形式は 340 と同一。 */
    private static byte[] chunk338() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(1); // section 0
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
        byte[] bufferBytes = buffer.bytes();
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
        writer.writeVarInt(0); // ブロックエンティティなし
        return writer.bytes();
    }

    @Test
    void chunk338TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk338(), 338, 0, 256, RegistryRemapper.biomeNames340()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void normalizeBlockState338Anchors() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 338)); // air
        // stone：338 の (1<<4)|0=16 と 393 の状態 1 は同じ 765 状態になる。
        assertEquals(BedrockChunkTranslator.normalizeBlockState(1, 393),
                BedrockChunkTranslator.normalizeBlockState(16, 338));
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 393),
                BedrockChunkTranslator.runtimeIdFor(16, 338));
        // 338 の正規化は全範囲で 340 と一致する（同じ意味対応表）。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_340; i++) {
            assertEquals(BedrockChunkTranslator.normalizeBlockState(i, 340),
                    BedrockChunkTranslator.normalizeBlockState(i, 338), "state " + i);
        }
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 765),
                BedrockChunkTranslator.runtimeIdFor(16, 338));
    }
}
