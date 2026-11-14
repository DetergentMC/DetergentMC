package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonToNbt} のテスト。テキストコンポーネントとして現れる
 * JSON の形（文字列・オブジェクト・配列・真偽値・数値・null）と、
 * {@link Nbt#toJsonString(Tag)} との往復を確認する。
 */
class JsonToNbtTest {

    @Test
    void plainString() {
        Tag tag = JsonToNbt.fromJson("\"hello\"");
        assertEquals(new Tag.StringTag("hello"), tag);
        assertEquals("\"hello\"", Nbt.toJsonString(tag));
    }

    @Test
    void simpleComponentObject() {
        Tag tag = JsonToNbt.fromJson("{\"text\":\"hi\",\"color\":\"red\",\"bold\":true}");
        assertTrue(tag instanceof Tag.CompoundTag);
        Tag.CompoundTag compound = (Tag.CompoundTag) tag;
        assertEquals("hi", compound.getString("text").orElseThrow());
        assertEquals("red", compound.getString("color").orElseThrow());
        assertEquals(new Tag.ByteTag((byte) 1), compound.get("bold").orElseThrow());
        // 往復：NBT→JSON に戻すと元の JSON と同じ内容になる。
        assertEquals("{\"text\":\"hi\",\"color\":\"red\",\"bold\":true}", Nbt.toJsonString(tag));
    }

    @Test
    void extraArrayWithMixedElements() {
        Tag tag = JsonToNbt.fromJson(
                "{\"text\":\"a\",\"extra\":[\"b\",{\"text\":\"c\",\"italic\":false}]}");
        Tag.CompoundTag compound = (Tag.CompoundTag) tag;
        Tag.ListTag extra = compound.getList("extra").orElseThrow();
        assertEquals(Nbt.COMPOUND, extra.elementType());
        assertEquals(2, extra.value().size());
        // 文字列要素は {"text": ...} に包まれる。
        assertEquals(new Tag.CompoundTag(java.util.Map.of("text", new Tag.StringTag("b"))),
                extra.value().get(0));
        assertEquals("{\"text\":\"a\",\"extra\":[{\"text\":\"b\"},{\"text\":\"c\",\"italic\":false}]}",
                Nbt.toJsonString(tag));
    }

    @Test
    void numbersAndNull() {
        Tag tag = JsonToNbt.fromJson("{\"a\":1,\"b\":2.5,\"c\":null,\"d\":1234567890123}");
        Tag.CompoundTag compound = (Tag.CompoundTag) tag;
        assertEquals(new Tag.IntTag(1), compound.get("a").orElseThrow());
        assertEquals(new Tag.DoubleTag(2.5), compound.get("b").orElseThrow());
        assertTrue(compound.get("c").isEmpty()); // null はキーを落とす
        assertEquals(new Tag.LongTag(1234567890123L), compound.get("d").orElseThrow());
    }

    @Test
    void escapesAndUnicode() {
        Tag tag = JsonToNbt.fromJson("\"a\\n\\u3042\\u3044\"");
        assertEquals(new Tag.StringTag("a\nあい"), tag);
    }

    @Test
    void emptyAndNullInput() {
        assertEquals(new Tag.StringTag(""), JsonToNbt.fromJson(null));
        assertEquals(new Tag.StringTag(""), JsonToNbt.fromJson(""));
        assertEquals(new Tag.StringTag(""), JsonToNbt.fromJson("   "));
    }

    @Test
    void brokenJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonToNbt.fromJson("{\"text\":"));
        assertThrows(IllegalArgumentException.class, () -> JsonToNbt.fromJson("{\"text\":\"a\""));
        assertThrows(IllegalArgumentException.class, () -> JsonToNbt.fromJson("{\"text\":\"a\"} junk"));
        assertThrows(IllegalArgumentException.class, () -> JsonToNbt.fromJson("[1,2"));
    }

    @Test
    void emptyArrayAndObject() {
        assertEquals(new Tag.ListTag(Nbt.STRING, java.util.List.of()),
                JsonToNbt.fromJson("[]"));
        assertEquals(new Tag.CompoundTag(java.util.Map.of()),
                JsonToNbt.fromJson("{}"));
    }
}
