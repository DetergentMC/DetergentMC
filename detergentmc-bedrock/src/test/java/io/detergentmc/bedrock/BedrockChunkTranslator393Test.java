package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import org.junit.jupiter.api.Test;

/**
 * 393（1.13）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 393 のチャンク形式は 404 と同一（heightmap 無し、光はセクション
 * 内蔵、旧式 crossing 詰め、バイオームは末尾 256 件 i32）だが、
 * ブロック状態の番号は 393 専用（サンゴまわりで 404 と異なる）。
 * パースは 404 と共用し、正規化は 393 の表で振り分けることを固定する。
 */
class BedrockChunkTranslator393Test {

    /** 393 のチャンク（セクション0に石・草・tube_coral、光は内蔵、バイオームは末尾）。 */
    private static byte[] chunk393() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(1); // section 0
        PacketIO.Writer buffer = new PacketIO.Writer();
        PacketIO.Writer section = new PacketIO.Writer();
        section.writeUnsignedByte(4);
        section.writeVarInt(4);
        section.writeVarInt(0);
        section.writeVarInt(1); // stone
        section.writeVarInt(9); // grass_block
        section.writeVarInt(8459); // tube_coral（393 の単一状態）
        long[] data = new long[256];
        data[0] = 1;
        data[1] = 2;
        data[2] = 3; // tube_coral
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
    void chunk393TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk393(), 393, 0, 256, RegistryRemapper.biomeNames393()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void normalizeBlockState393Anchors() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 393)); // air
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 393)); // stone
        // tube_coral：393 の 8459 と 404 の 8470 はどちらも
        // 758 の tube_coral[waterlogged=true] に対応し、同じ 765 状態になる。
        assertEquals(BedrockChunkTranslator.normalizeBlockState(8470, 404),
                BedrockChunkTranslator.normalizeBlockState(8459, 393));
        assertEquals(BedrockChunkTranslator.runtimeIdFor(8470, 404),
                BedrockChunkTranslator.runtimeIdFor(8459, 393));
        // 全範囲で例外なく 765 の範囲に収まる。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_393; i++) {
            int mapped = BedrockChunkTranslator.normalizeBlockState(i, 393);
            assertTrue(mapped >= 0, "state " + i);
        }
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 765),
                BedrockChunkTranslator.runtimeIdFor(1, 393));
    }
}
