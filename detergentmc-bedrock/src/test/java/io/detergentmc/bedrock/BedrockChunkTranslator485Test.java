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
 * 485（1.14.2）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 485 のチャンク形式は 490 と同一（バイオームはデータバッファ末尾の
 * 256件）のため、translate490 のパスを共用することと、正規化が
 * 全範囲で 490 と一致することを固定する。
 */
class BedrockChunkTranslator485Test {

    /** 485 のチャンク（セクション0に石と草、バイオームはバッファ末尾）。 */
    private static byte[] chunk485() {
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
    void chunk485TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk485(), 485, 0, 256, RegistryRemapper.biomeNames485()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void normalizeBlockState485Equals490OverFullRange() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 485)); // air
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 485)); // stone
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_485; i++) {
            assertEquals(BedrockChunkTranslator.normalizeBlockState(i, 490),
                    BedrockChunkTranslator.normalizeBlockState(i, 485), "state " + i);
        }
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 765),
                BedrockChunkTranslator.runtimeIdFor(1, 485));
    }
}
