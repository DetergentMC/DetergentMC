package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 477（1.14）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 477 のチャンク形式は 480 と同一（バイオームはデータバッファ末尾の
 * 256件）のため、480 と同じ経路（translate498）を共用することと、
 * 正規化が全範囲で 480 と一致することを固定する。
 */
class BedrockChunkTranslator477Test {

    /** 477 のチャンク（セクション0に石と草、バイオームはバッファ末尾）。 */
    private static byte[] chunk477() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(1); // section 0
        Map<String, Nbt.Tag> heightmaps = new LinkedHashMap<>();
        heightmaps.put("MOTION_BLOCKING", new Nbt.Tag.LongArrayTag(new long[36]));
        writer.writeNamedNbt("", new Nbt.Tag.CompoundTag(heightmaps));
        PacketIO.Writer buffer = new PacketIO.Writer();
        PacketIO.Writer section = new PacketIO.Writer();
        section.writeShort(2);
        section.writeUnsignedByte(4);
        section.writeVarInt(3);
        section.writeVarInt(0);
        section.writeVarInt(1); // stone
        section.writeVarInt(9); // grass_block
        long[] data = new long[256];
        data[0] = 1;
        data[16] = 2;
        section.writeVarInt(256);
        for (long l : data) {
            section.writeLong(l);
        }
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
    void chunk477TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk477(), 477, 0, 256, RegistryRemapper.biomeNames477()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void normalizeBlockState477Equals480OverFullRange() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 477)); // air
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 477)); // stone
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_477; i++) {
            assertEquals(BedrockChunkTranslator.normalizeBlockState(i, 480),
                    BedrockChunkTranslator.normalizeBlockState(i, 477), "state " + i);
        }
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 765),
                BedrockChunkTranslator.runtimeIdFor(1, 477));
    }
}
