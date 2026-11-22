package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 47（1.8〜1.8.9）の変換器。3つの山場を固定する：
 * チャンクはパレット無しの固定詰め（(数値ID&lt;&lt;4)|メタデータを
 * 2バイトLE、bitMap は u16、初期チャンクは Map Chunk Bulk）、
 * メタデータは型の再番号付け（758 の型 → 47 の型 0〜7）と
 * 実ワイヤの bitfield 化、座標は f64 ⇔ i32 固定小数点（×32）。
 */
class ChunkTranslator47Test {

    /** 47 のチャンク1個（セクション0のみ、石・草・白羊毛、光は全ゼロ、バイオームは plains）。 */
    private static byte[] chunk47() {
        Writer writer = new Writer();
        writer.writeInt(3);
        writer.writeInt(-2);
        writer.writeBoolean(true); // groundUp
        writer.writeShort(1); // bitMap: section 0
        Writer buffer = new Writer();
        for (int i = 0; i < 4096; i++) {
            int v = i == 0 ? 16 : i == 1 ? 32 : i == 2 ? 560 : 0;
            buffer.writeByte(v & 0xFF);
            buffer.writeByte((v >> 8) & 0xFF);
        }
        buffer.writeBytes(new byte[2048]); // ブロックライト
        buffer.writeBytes(new byte[2048]); // スカイライト
        for (int i = 0; i < 256; i++) {
            buffer.writeByte(1); // バイオーム列（plains、47 は u8）
        }
        byte[] bufferBytes = buffer.bytes();
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
        return writer.bytes();
    }

    @Test
    void chunk47RoundTripsThrough758() {
        byte[] out = ChunkTranslator.translateChunkData47To758(chunk47(), true);
        assertNotNull(out);
        byte[] back = ChunkTranslator.translateChunkData758To47(out, true);
        Reader reader = new Reader(back);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readUnsignedShort()); // bitMap は u16
        int size = reader.readVarInt();
        Reader buffer = new Reader(reader.readBytes(size));
        int[] expected = {16, 32, 560};
        for (int i = 0; i < 4096; i++) {
            int lo = buffer.readUnsignedByte();
            int hi = buffer.readUnsignedByte();
            int v = lo | (hi << 8);
            if (i < 3) {
                assertEquals(expected[i], v, "state " + i);
            } else {
                assertEquals(0, v, "state " + i);
            }
        }
        buffer.readBytes(2048);
        buffer.readBytes(2048);
        for (int i = 0; i < 256; i++) {
            assertEquals(1, buffer.readUnsignedByte(), "biome " + i);
        }
        assertEquals(0, buffer.remaining());
        assertEquals(0, reader.remaining());
    }

    @Test
    void chunkBulkSplitsIntoSingleChunks() {
        Writer writer = new Writer();
        writer.writeBoolean(true); // skyLightSent
        writer.writeVarInt(2);
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeShort(1); // chunk A: 1 section
        writer.writeInt(1);
        writer.writeInt(0);
        writer.writeShort(3); // chunk B: 2 sections
        // chunk A のデータ（1セクション＋バイオーム）
        Writer a = new Writer();
        a.writeByte(16);
        a.writeByte(0);
        a.writeBytes(new byte[12288 - 2]);
        for (int i = 0; i < 256; i++) {
            a.writeByte(1);
        }
        writer.writeBytes(a.bytes());
        // chunk B のデータ（2セクション＋バイオーム）
        writer.writeBytes(new byte[2 * 12288]);
        for (int i = 0; i < 256; i++) {
            writer.writeByte(1);
        }
        List<byte[]> chunks = ChunkTranslator.splitChunkBulk47(writer.bytes());
        assertEquals(2, chunks.size());
        Reader ra = new Reader(chunks.get(0));
        assertEquals(0, ra.readInt());
        assertEquals(0, ra.readInt());
        assertEquals(true, ra.readBoolean());
        assertEquals(1, ra.readUnsignedShort());
        assertEquals(12288 + 256, ra.readVarInt());
        Reader rb = new Reader(chunks.get(1));
        assertEquals(1, rb.readInt());
        assertEquals(0, rb.readInt());
        assertEquals(true, rb.readBoolean());
        assertEquals(3, rb.readUnsignedShort());
        assertEquals(2 * 12288 + 256, rb.readVarInt());
        // 分けたチャンクは単体チャンクとして 758 へ変換できる。
        assertNotNull(ChunkTranslator.translateChunkData47To758(chunks.get(0), true));
    }

    @Test
    void metadataTypesConvert758To47() {
        Writer writer = new Writer();
        writer.writeVarInt(5); // entityId
        writer.writeUnsignedByte(0); // index 0: byte
        writer.writeVarInt(0);
        writer.writeByte(7);
        writer.writeUnsignedByte(1); // index 1: varint → 47 の int(2)
        writer.writeVarInt(1);
        writer.writeVarInt(123456);
        writer.writeUnsignedByte(2); // index 2: boolean → 47 の byte(0)
        writer.writeVarInt(7);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(3); // index 3: blockid → 47 の int(2)
        writer.writeVarInt(13);
        writer.writeVarInt(1); // 758 の stone
        writer.writeUnsignedByte(4); // index 4: villagerdata（47 に無いため落ちる）
        writer.writeVarInt(16);
        writer.writeVarInt(1);
        writer.writeVarInt(2);
        writer.writeVarInt(3);
        writer.writeUnsignedByte(5); // index 5: position → 47 の position(6)
        writer.writeVarInt(9);
        writer.writePositionRaw(ChunkTranslator.packBlockPos(10, 64, 20));
        writer.writeUnsignedByte(0xFF);
        byte[] hybrid = ChunkTranslator.translateEntityMetadata758To47(writer.bytes());
        Reader reader = new Reader(hybrid);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readVarInt()); // type 0 (byte)
        assertEquals(7, reader.readByte());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt()); // type 2 (int)
        assertEquals(123456, reader.readInt());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(0, reader.readVarInt()); // type 0 (byte)
        assertEquals(1, reader.readByte());
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt()); // type 2 (int)
        assertEquals(16, reader.readInt()); // stone は (1<<4)|0
        // index 4 は落ちているため、次は index 5 が来る。
        assertEquals(5, reader.readUnsignedByte());
        assertEquals(6, reader.readVarInt()); // type 6 (position)
        assertEquals(10, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(20, reader.readInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void metadataBitfieldWireRoundTrip() {
        // ハイブリッド：entityId 9、index 2・型 4（文字列）"hi"、終端 0xFF。
        Writer writer = new Writer();
        writer.writeVarInt(9);
        writer.writeUnsignedByte(2);
        writer.writeVarInt(4);
        writer.writeString("hi");
        writer.writeUnsignedByte(0xFF);
        int id47 = PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_METADATA,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                ProtocolVersion.V1_8).orElseThrow();
        MinecraftPacket wire = SlotTranslator.clientboundTo47(
                new MinecraftPacket(id47, writer.bytes()));
        Reader reader = new Reader(wire.payload());
        assertEquals(9, reader.readVarInt());
        assertEquals((4 << 5) | 2, reader.readUnsignedByte()); // 型3bit＋キー5bit
        assertEquals("hi", reader.readString());
        assertEquals(0x7F, reader.readUnsignedByte()); // 47 の終端
        assertEquals(0, reader.remaining());
        // 逆向きで元のハイブリッドに戻る。
        MinecraftPacket back = SlotTranslator.clientboundFrom47(wire);
        assertArrayEquals(writer.bytes(), back.payload());
    }

    @Test
    void spawnLivingFixedPointRoundTrip() {
        int pig758 = RegistryRemapper.mapEntityType47To758(90);
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(pig758);
        writer.writeDouble(10.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-3.25);
        writer.writeByte(10); // yaw
        writer.writeByte(20); // pitch
        writer.writeByte(30); // headPitch
        writer.writeShort(1);
        writer.writeShort(2);
        writer.writeShort(3);
        byte[] hybrid = ChunkTranslator.translateSpawnLiving758To47(writer.bytes());
        int id47 = PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                ProtocolVersion.V1_8).orElseThrow();
        MinecraftPacket wire = SlotTranslator.clientboundTo47(
                new MinecraftPacket(id47, hybrid));
        Reader reader = new Reader(wire.payload());
        assertEquals(7, reader.readVarInt());
        assertEquals(90, reader.readUnsignedByte()); // type は 47 の番号（ブタ）
        assertEquals(336, reader.readInt()); // 10.5 × 32
        assertEquals(2048, reader.readInt()); // 64 × 32
        assertEquals(-104, reader.readInt()); // -3.25 × 32
        reader.readByte();
        reader.readByte();
        reader.readByte();
        reader.readShort();
        reader.readShort();
        reader.readShort();
        assertEquals(0x7F, reader.readUnsignedByte()); // メタデータ終端
        assertEquals(0, reader.remaining());
        // 逆向き：ワイヤ → ハイブリッド → 758（UUID は零で補われる）。
        MinecraftPacket backHybrid = SlotTranslator.clientboundFrom47(wire);
        byte[][] parts = ChunkTranslator.spawnLiving47To758(backHybrid.payload());
        Reader spawn = new Reader(parts[0]);
        assertEquals(7, spawn.readVarInt());
        assertArrayEquals(new byte[16], spawn.readBytes(16));
        assertEquals(pig758, spawn.readVarInt());
        assertEquals(10.5, spawn.readDouble());
        assertEquals(64.0, spawn.readDouble());
        assertEquals(-3.25, spawn.readDouble());
    }

    @Test
    void positionTeleportIdIsStripped() {
        Writer writer = new Writer();
        writer.writeDouble(1.5);
        writer.writeDouble(70.0);
        writer.writeDouble(-2.5);
        writer.writeFloat(90.0f);
        writer.writeFloat(0.0f);
        writer.writeByte(0); // flags
        writer.writeVarInt(42); // teleportId
        ChunkTranslator.PositionStrip47 stripped =
                ChunkTranslator.translatePosition758To47(writer.bytes());
        assertEquals(42, stripped.teleportId());
        assertEquals(8 * 3 + 4 * 2 + 1, stripped.payload().length);
        // 逆向きは teleportId 0 を補う。
        byte[] back = ChunkTranslator.translatePosition47To758(stripped.payload());
        Reader reader = new Reader(back);
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readByte();
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityEffectBoundsAndHideParticles() {
        Writer writer = new Writer();
        writer.writeVarInt(3); // entityId
        writer.writeVarInt(22); // 758 の最終有効効果
        writer.writeByte(1); // amplifier
        writer.writeVarInt(200); // duration
        writer.writeByte(0); // flags（粒子表示なし → hideParticles=true）
        byte[] out = ChunkTranslator.translateEntityEffect758To47(writer.bytes());
        assertNotNull(out);
        Reader reader = new Reader(out);
        assertEquals(3, reader.readVarInt());
        assertEquals(23, reader.readVarInt()); // +1
        assertEquals(1, reader.readByte());
        assertEquals(200, reader.readVarInt());
        assertEquals(true, reader.readBoolean());
        // 758 の 23（glowing）は 47 に無いため落とす。
        Writer w2 = new Writer();
        w2.writeVarInt(3);
        w2.writeVarInt(23);
        w2.writeByte(0);
        w2.writeVarInt(10);
        w2.writeByte(2);
        assertNull(ChunkTranslator.translateEntityEffect758To47(w2.bytes()));
        // 逆向き：hideParticles=false は flags の bit1（粒子表示）になる。
        byte[] back = ChunkTranslator.translateEntityEffect47To758(out);
        assertNotNull(back);
        Reader rb = new Reader(back);
        assertEquals(3, rb.readVarInt());
        assertEquals(22, rb.readVarInt());
        rb.readByte();
        rb.readVarInt();
        assertEquals(0, rb.readByte()); // hideParticles=true → flags 0
    }

    @Test
    void slotItemConversion() {
        Writer writer = new Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(719); // 758 のダイヤ剣
        writer.writeUnsignedByte(1);
        writer.writeByte(0); // NBT 無し
        byte[] slot47 = ChunkTranslator.translateSlot758To47(writer.bytes());
        Reader reader = new Reader(slot47);
        assertEquals(true, reader.readBoolean());
        assertEquals(276, reader.readVarInt()); // 47 のダイヤ剣
        byte[] back = ChunkTranslator.translateSlot47To758(slot47);
        Reader rb = new Reader(back);
        assertEquals(true, rb.readBoolean());
        assertEquals(719, rb.readVarInt());
    }

    @Test
    void soundEffectBecomesNamedOrDrops() {
        int stone758 = -1;
        int unmapped758 = -1;
        for (int i = 0; i < RegistryTables47Snd.SOUND_NAMES_758.length; i++) {
            if ("block.stone.break".equals(RegistryTables47Snd.SOUND_NAMES_758[i])) {
                stone758 = i;
            }
            if (unmapped758 < 0 && RegistryRemapper.mapSound758To47Name(i) == null) {
                unmapped758 = i;
            }
        }
        assertTrue(stone758 >= 0 && unmapped758 >= 0);
        Writer writer = new Writer();
        writer.writeVarInt(stone758);
        writer.writeVarInt(0); // category
        writer.writeInt(160); // x（×8 固定）
        writer.writeInt(512);
        writer.writeInt(-80);
        writer.writeFloat(1.0f); // volume
        writer.writeFloat(1.0f); // pitch
        byte[] named = ChunkTranslator.translateSoundEffect758To47Named(writer.bytes());
        assertNotNull(named);
        Reader reader = new Reader(named);
        assertEquals("dig.stone", reader.readString());
        assertEquals(160, reader.readInt());
        assertEquals(512, reader.readInt());
        assertEquals(-80, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(64, reader.readUnsignedByte()); // pitch は u8（1.0 × 63.5 → 64）
        assertEquals(0, reader.remaining());
        // 47 に無い音は落とす（null）。
        Writer w2 = new Writer();
        w2.writeVarInt(unmapped758);
        w2.writeVarInt(0);
        w2.writeInt(0);
        w2.writeInt(0);
        w2.writeInt(0);
        w2.writeFloat(1.0f);
        w2.writeFloat(1.0f);
        assertNull(ChunkTranslator.translateSoundEffect758To47Named(w2.bytes()));
    }
}
