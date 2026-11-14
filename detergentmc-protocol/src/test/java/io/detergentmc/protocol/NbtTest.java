package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NbtTest {

    @Test
    void roundTripsNamedCompound() {
        Map<String, Tag> inner = Map.of(
                "name", new Tag.StringTag("minecraft:overworld"),
                "id", new Tag.IntTag(0),
                "height", new Tag.LongTag(384L),
                "ratio", new Tag.FloatTag(1.5f),
                "flag", new Tag.ByteTag((byte) 1));
        Tag.CompoundTag compound = new Tag.CompoundTag(inner);
        byte[] bytes = Nbt.writeNamed("", compound);
        Nbt.NamedResult read = Nbt.readNamed(bytes, 0);
        assertEquals("", read.name());
        assertEquals(compound, read.tag());
        assertEquals(bytes.length, read.bytesRead());
    }

    @Test
    void roundTripsAnonymousTags() {
        Tag string = new Tag.StringTag("こんにちは");
        byte[] bytes = Nbt.writeAnonymous(string);
        Nbt.ReadResult read = Nbt.readAnonymous(bytes, 0);
        assertEquals(string, read.tag());
        assertEquals(bytes.length, read.bytesRead());

        Tag list = new Tag.ListTag(Nbt.STRING, List.of(
                new Tag.StringTag("a"), new Tag.StringTag("b")));
        byte[] listBytes = Nbt.writeAnonymous(list);
        assertEquals(list, Nbt.readAnonymous(listBytes, 0).tag());

        Tag arrays = new Tag.CompoundTag(Map.of(
                "bytes", new Tag.ByteArrayTag(new byte[]{1, 2, 3}),
                "ints", new Tag.IntArrayTag(new int[]{4, 5}),
                "longs", new Tag.LongArrayTag(new long[]{6L}),
                "short", new Tag.ShortTag((short) 7),
                "double", new Tag.DoubleTag(2.5)));
        byte[] arrayBytes = Nbt.writeAnonymous(arrays);
        assertEquals(arrays, Nbt.readAnonymous(arrayBytes, 0).tag());
    }

    @Test
    void truncatedNbtThrows() {
        byte[] bytes = Nbt.writeAnonymous(new Tag.StringTag("hello"));
        byte[] cut = new byte[bytes.length - 2];
        System.arraycopy(bytes, 0, cut, 0, cut.length);
        assertThrows(IllegalArgumentException.class, () -> Nbt.readAnonymous(cut, 0));
    }

    @Test
    void toJsonStringConvertsComponent() {
        Map<String, Tag> fields = new java.util.LinkedHashMap<>();
        fields.put("text", new Tag.StringTag("hello"));
        fields.put("color", new Tag.StringTag("red"));
        fields.put("bold", new Tag.ByteTag((byte) 1));
        fields.put("extra", new Tag.ListTag(Nbt.COMPOUND, List.of(
                new Tag.CompoundTag(Map.of("text", new Tag.StringTag("!"))))));
        Tag component = new Tag.CompoundTag(fields);
        assertEquals("{\"text\":\"hello\",\"color\":\"red\",\"bold\":true,\"extra\":[{\"text\":\"!\"}]}",
                Nbt.toJsonString(component));
        assertEquals("\"plain\"", Nbt.toJsonString(new Tag.StringTag("plain")));
        assertEquals("42", Nbt.toJsonString(new Tag.IntTag(42)));
    }

    @Test
    void plainTextExtractsTextAndExtra() {
        Tag component = new Tag.CompoundTag(Map.of(
                "text", new Tag.StringTag("hi "),
                "extra", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of("text", new Tag.StringTag("there")))))));
        assertEquals("hi there", Nbt.plainText(component));
        assertEquals("solo", Nbt.plainText(new Tag.StringTag("solo")));
        assertTrue(Nbt.plainText(new Tag.IntTag(3)).isEmpty());
    }

    @Test
    void jsonStringEscapes() {
        assertEquals("\"a\\\"b\\\\c\\n\"",
                Nbt.jsonString("a\"b\\c\n"));
    }
}
