package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 763↔764の Respawn／Advancements 変換（{@link ExtraPlayTranslator}）。
 *
 * <p>Respawn は763では copyMetadata が isFlat の直後、764／765では末尾。
 * Advancements は764には criteria が無く、763では空配列を書いて無害化する。
 */
class ExtraPlayTranslator763764Test {

    /** 764／765の Respawn（copyMetadata は末尾）。 */
    private static byte[] respawn764Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false); // hasDeath
        writer.writeVarInt(0); // portalCooldown
        writer.writeBoolean(true); // copyMetadata
        return writer.bytes();
    }

    /** 763の Respawn（copyMetadata は isFlat の直後）。 */
    private static byte[] respawn763Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true); // copyMetadata
        writer.writeBoolean(false); // hasDeath
        writer.writeVarInt(0); // portalCooldown
        return writer.bytes();
    }

    @Test
    void respawn764To763MovesCopyMetadata() {
        byte[] out = ExtraPlayTranslator.translateRespawn764To763(respawn764Payload());
        assertArrayEquals(respawn763Payload(), out);
    }

    @Test
    void respawn763To764MovesCopyMetadataToEnd() {
        byte[] out = ExtraPlayTranslator.translateRespawn763To764(respawn763Payload());
        assertArrayEquals(respawn764Payload(), out);
    }

    @Test
    void respawn764To763AndBackRoundTrip() {
        Writer writer = new Writer();
        writer.writeString("minecraft:the_nether");
        writer.writeString("minecraft:the_nether");
        writer.writeLong(7L);
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeBoolean(true);
        writer.writeBoolean(true);
        writer.writeBoolean(true); // hasDeath
        writer.writeString("minecraft:overworld");
        writer.writePositionRaw(0L);
        writer.writeVarInt(5);
        writer.writeBoolean(false); // copyMetadata
        byte[] out = ExtraPlayTranslator.translateRespawn763To764(
                ExtraPlayTranslator.translateRespawn764To763(writer.bytes()));
        assertArrayEquals(writer.bytes(), out);
    }

    /** 764の Advancements 1件（表示あり、criteria なし）。 */
    private static byte[] advancements764Payload() {
        Writer writer = new Writer();
        writer.writeBoolean(false); // reset
        writer.writeVarInt(1); // mappingCount
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(false); // hasParent
        writer.writeBoolean(true); // hasDisplay
        writer.writeString("{\"text\":\"title\"}");
        writer.writeString("{\"text\":\"desc\"}");
        // icon（764スロット：石1個、NBTなし）
        writer.writeBoolean(true);
        writer.writeVarInt(1);
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeVarInt(0); // frameType
        writer.writeInt(0); // flags（背景テクスチャなし）
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeVarInt(0); // requirementCount
        writer.writeBoolean(false); // sendsTelemetryData
        PacketIO.writeStringList(writer, List.of()); // 削除対象
        writer.writeVarInt(0); // progressCount
        return writer.bytes();
    }

    @Test
    void advancements764To763WritesEmptyCriteria() {
        byte[] out = ExtraPlayTranslator.translateAdvancements764To763(
                advancements764Payload());
        Reader reader = new Reader(out);
        assertFalse(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"title\"}", reader.readString());
        assertEquals("{\"text\":\"desc\"}", reader.readString());
        assertTrue(reader.readBoolean()); // icon present
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readUnsignedByte()); // 名前付き NBT なし
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readInt());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        // 763では criteria（空配列）が入る。
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // 削除対象の件数
        assertEquals(0, reader.readVarInt()); // progressCount
        assertEquals(0, reader.remaining());
    }

    /** 763の Advancements 1件（criteria あり）。 */
    private static byte[] advancements763Payload() {
        Writer writer = new Writer();
        writer.writeBoolean(false); // reset
        writer.writeVarInt(1); // mappingCount
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(false); // hasParent
        writer.writeBoolean(true); // hasDisplay
        writer.writeString("{\"text\":\"title\"}");
        writer.writeString("{\"text\":\"desc\"}");
        // icon（763スロット：石1個、NBTなし）
        writer.writeBoolean(true);
        writer.writeVarInt(1);
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeVarInt(0); // frameType
        writer.writeInt(0); // flags
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        PacketIO.writeStringList(writer, List.of("has_stone")); // criteria
        writer.writeVarInt(0); // requirementCount
        writer.writeBoolean(false); // sendsTelemetryData
        PacketIO.writeStringList(writer, List.of()); // 削除対象
        writer.writeVarInt(0); // progressCount
        return writer.bytes();
    }

    @Test
    void advancements763To764DropsCriteriaAndConvertsTitleToNbt() {
        byte[] out = ExtraPlayTranslator.translateAdvancements763To764(
                advancements763Payload());
        Reader reader = new Reader(out);
        assertFalse(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        // title・description は JSON 文字列から NBT へ。
        Tag title = reader.readAnonymousNbt().tag();
        assertEquals("{\"text\":\"title\"}", Nbt.toJsonString(title));
        Tag description = reader.readAnonymousNbt().tag();
        assertEquals("{\"text\":\"desc\"}", Nbt.toJsonString(description));
        assertTrue(reader.readBoolean()); // icon present
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readUnsignedByte()); // 名前なし NBT なし
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readInt());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        // criteria は無いため requirementCount がすぐ来る。
        assertEquals(0, reader.readVarInt());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }
}
