package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 764向け Advancements 変換：765と同じく criteria が無いため、
 * 763向けの空 criteria 書き足しをしてはいけない。
 */
class ExtraPlayTranslator764Test {

    /** 765形式の Advancements を1件だけ作る（表示あり、アイコンは空スロット）。 */
    private static byte[] advancements765Payload() {
        Writer writer = new Writer();
        writer.writeBoolean(false); // reset
        writer.writeVarInt(1); // mappingCount
        writer.writeString("minecraft:story/root"); // key
        writer.writeBoolean(false); // parentId なし
        writer.writeBoolean(true); // displayData あり
        writer.writeBytes(Nbt.writeAnonymous(new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("Root"))))); // title
        writer.writeBytes(Nbt.writeAnonymous(new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("Desc"))))); // description
        writer.writeBoolean(false); // icon：空スロット
        writer.writeVarInt(0); // frameType
        writer.writeInt(0); // flags（背景テクスチャなし）
        writer.writeFloat(0.0f); // xCord
        writer.writeFloat(0.0f); // yCord
        writer.writeVarInt(0); // requirements
        writer.writeBoolean(false); // sendsTelemetryData
        writer.writeVarInt(0); // identifiers
        writer.writeVarInt(0); // progressMapping
        return writer.bytes();
    }

    /** 764形式として読む：title・description は文字列で、criteria は無い。 */
    private static void assertParsesAs764(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(false, reader.readBoolean()); // reset
        assertEquals(1, reader.readVarInt()); // mappingCount
        assertEquals("minecraft:story/root", reader.readString()); // key
        assertEquals(false, reader.readBoolean()); // parentId なし
        assertEquals(true, reader.readBoolean()); // displayData あり
        String title = reader.readString();
        assertTrue(title.contains("Root"), title);
        String description = reader.readString();
        assertTrue(description.contains("Desc"), description);
        assertEquals(false, reader.readBoolean()); // icon：空スロット
        assertEquals(0, reader.readVarInt()); // frameType
        assertEquals(0, reader.readInt()); // flags
        assertEquals(0.0f, reader.readFloat()); // xCord
        assertEquals(0.0f, reader.readFloat()); // yCord
        // ここに criteria は無い。次は requirements。
        assertEquals(0, reader.readVarInt()); // requirements
        assertEquals(false, reader.readBoolean()); // sendsTelemetryData
        assertEquals(0, reader.readVarInt()); // identifiers
        assertEquals(0, reader.readVarInt()); // progressMapping
        assertEquals(0, reader.remaining());
    }

    /** 763形式として読む：criteria（空配列）が requirements の前にある。 */
    private static void assertParsesAs763(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertTrue(reader.readString().contains("Root"));
        assertTrue(reader.readString().contains("Desc"));
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readInt());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0, reader.readVarInt()); // criteria（空配列）
        assertEquals(0, reader.readVarInt()); // requirements
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void advancements765To764HasNoCriteria() {
        byte[] converted = ExtraPlayTranslator.translateAdvancements765To764(
                advancements765Payload());
        assertParsesAs764(converted);
    }

    @Test
    void advancements765To763StillHasEmptyCriteria() {
        byte[] converted = ExtraPlayTranslator.translateAdvancements765To763(
                advancements765Payload());
        assertParsesAs763(converted);
    }
}
