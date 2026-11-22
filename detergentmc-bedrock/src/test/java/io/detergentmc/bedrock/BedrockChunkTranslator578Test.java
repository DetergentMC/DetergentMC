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
 * 578（1.15.2）の Java チャンク → Bedrock LevelChunk 変換テスト。
 * 578 のチャンクはビットマスク方式（16節）・バイオーム配列・またぎ
 * ビット詰めで、758 以降とは別形式のため専用パスで読む。
 */
class BedrockChunkTranslator578Test {

    /** 578 のチャンク（セクション0に石と草、残りは空気）。 */
    private static byte[] chunk578() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(1); // section 0
        Map<String, Nbt.Tag> heightmaps = new LinkedHashMap<>();
        heightmaps.put("MOTION_BLOCKING", new Nbt.Tag.LongArrayTag(new long[36]));
        writer.writeNamedNbt("", new Nbt.Tag.CompoundTag(heightmaps));
        for (int i = 0; i < 1024; i++) {
            writer.writeInt(1); // plains
        }
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
        byte[] sectionBytes = section.bytes();
        writer.writeVarInt(sectionBytes.length);
        writer.writeBytes(sectionBytes);
        writer.writeVarInt(0); // ブロックエンティティなし
        return writer.bytes();
    }

    @Test
    void chunk578TranslatesTo16SubChunks() {
        TranslatedChunk out = BedrockChunkTranslator.translate(new ChunkInput(
                0, 0, chunk578(), 578, 0, 256, RegistryRemapper.biomeNames578()));
        assertEquals(16, out.subChunkCount());
        assertEquals(0, out.chunkX());
        assertTrue(out.payload().length > 0);
    }

    @Test
    void normalizeBlockState578() {
        assertEquals(0, BedrockChunkTranslator.normalizeBlockState(0, 578)); // air
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 578)); // stone
        assertEquals(BedrockChunkTranslator.runtimeIdFor(1, 765),
                BedrockChunkTranslator.runtimeIdFor(1, 578));
        // grass_path（578のみ）→ dirt_path 系へ寄る（石にはならない）。
        int mapped = BedrockChunkTranslator.normalizeBlockState(8687, 578);
        assertTrue(mapped > 1, "grass_path の 765 換算: " + mapped);
    }
}
