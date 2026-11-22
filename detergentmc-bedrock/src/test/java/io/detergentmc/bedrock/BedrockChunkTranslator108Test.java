package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import org.junit.jupiter.api.Test;

/**
 * 108（1.9.1）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * チャンクの骨格は 109／210／315 と同一（heightmap 無し、光は
 * セクション内蔵、旧式 crossing 詰め、パレット値は
 * (数値ID&lt;&lt;4)|メタデータ、直接パレットは13ビット、バイオームは
 * 末尾の u8×256）だが、末尾のブロックエンティティ列は 108 には
 * 無い（1.9.3 で追加）。パース・正規化とも 109 と一致することを
 * 固定する。
 */
class BedrockChunkTranslator108Test {

    /** 108 のチャンク（セクション0に石・草・白羊毛、光は内蔵、バイオームは末尾 u8）。末尾のブロックエンティティ列は無い。 */
    private static byte[] chunk108() {
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
        // 108 には末尾のブロックエンティティ列が無い。
        return writer.bytes();
    }

    @Test
    void chunk108TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk108(), 108, 0, 256, RegistryRemapper.biomeNames108()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void normalizeBlockState108Matches109() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 108)); // air
        assertEquals(BedrockChunkTranslator.normalizeBlockState(1, 393),
                BedrockChunkTranslator.normalizeBlockState(16, 108));
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 393),
                BedrockChunkTranslator.runtimeIdFor(16, 108));
        // 108 の正規化は全範囲で 109 と一致する（同じ意味対応表）。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_110; i++) {
            assertEquals(BedrockChunkTranslator.normalizeBlockState(i, 109),
                    BedrockChunkTranslator.normalizeBlockState(i, 108), "state " + i);
        }
    }
}
