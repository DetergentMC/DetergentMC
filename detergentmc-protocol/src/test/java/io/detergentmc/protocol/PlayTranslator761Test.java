package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の761（1.19.3）⇔762（1.19.4）向け変換テスト。
 *
 * <p>761と762で構造が違うプレイ状態のパケットは2つだけ
 * （minecraft-data protocol.json で確認）：
 * Synchronize Player Position（761 にだけ末尾の dismountVehicle がある）と
 * Server Data（761 は motd／icon が [option, string]、762 は motd が string・
 * icon が [option, buffer]）。Join Game は両方とも同じ構造（末尾の
 * portalCooldown が無い）のため変換しない。Respawn／Advancements／
 * Death／End Combat も構造が同一のため変換しない。
 */
class PlayTranslator761Test {

    @Test
    void position762To761AppendsDismountVehicleFalse() {
        Writer payload = new Writer();
        payload.writeDouble(1.5);
        payload.writeDouble(64.0);
        payload.writeDouble(-2.5);
        payload.writeFloat(90.0f);
        payload.writeFloat(0.0f);
        payload.writeByte(0x01);
        payload.writeVarInt(42);
        byte[] translated = PlayTranslator.translatePosition762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(90.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0x01, reader.readByte());
        assertEquals(42, reader.readVarInt());
        assertFalse(reader.readBoolean()); // dismountVehicle（761のみ）
        assertEquals(0, reader.remaining());
    }

    @Test
    void position761To762StripsDismountVehicle() {
        Writer payload = new Writer();
        payload.writeDouble(1.5);
        payload.writeDouble(64.0);
        payload.writeDouble(-2.5);
        payload.writeFloat(90.0f);
        payload.writeFloat(0.0f);
        payload.writeByte(0x01);
        payload.writeVarInt(42);
        payload.writeBoolean(true); // dismountVehicle（761のみ）
        byte[] translated = PlayTranslator.translatePosition761To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(90.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0x01, reader.readByte());
        assertEquals(42, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void positionBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translatePosition762To761(broken));
        assertArrayEquals(broken, PlayTranslator.translatePosition761To762(broken));
    }

    @Test
    void serverData762To761() {
        Writer payload = new Writer();
        payload.writeString("{\"text\":\"A Minecraft Server\"}");
        payload.writeBoolean(true); // icon あり
        payload.writeByteArray(new byte[]{0x01, 0x02, 0x03, 0x04}); // PNG バイト
        payload.writeBoolean(true); // enforcesSecureChat
        byte[] translated = PlayTranslator.translateServerData762To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertTrue(reader.readBoolean()); // motd あり
        assertEquals("{\"text\":\"A Minecraft Server\"}", reader.readString());
        assertTrue(reader.readBoolean()); // icon あり
        assertEquals("data:image/png;base64,AQIDBA==", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.remaining());

        // icon 無し。
        Writer noIcon = new Writer();
        noIcon.writeString("{\"text\":\"No Icon\"}");
        noIcon.writeBoolean(false);
        noIcon.writeBoolean(false);
        byte[] noIconOut = PlayTranslator.translateServerData762To761(noIcon.bytes());
        Reader noIconReader = new Reader(noIconOut);
        assertTrue(noIconReader.readBoolean());
        assertEquals("{\"text\":\"No Icon\"}", noIconReader.readString());
        assertFalse(noIconReader.readBoolean());
        assertFalse(noIconReader.readBoolean());
        assertEquals(0, noIconReader.remaining());
    }

    @Test
    void serverData761To762() {
        Writer payload = new Writer();
        payload.writeBoolean(true); // motd あり
        payload.writeString("{\"text\":\"A Minecraft Server\"}");
        payload.writeBoolean(true); // icon あり
        payload.writeString("data:image/png;base64,AQIDBA==");
        payload.writeBoolean(true); // enforcesSecureChat
        byte[] translated = PlayTranslator.translateServerData761To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"A Minecraft Server\"}", reader.readString());
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, reader.readByteArray());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.remaining());

        // motd 無し・icon 無し。
        Writer empty = new Writer();
        empty.writeBoolean(false);
        empty.writeBoolean(false);
        empty.writeBoolean(true);
        byte[] emptyOut = PlayTranslator.translateServerData761To762(empty.bytes());
        Reader emptyReader = new Reader(emptyOut);
        assertEquals("", emptyReader.readString()); // motd は空文字になる
        assertFalse(emptyReader.readBoolean());
        assertTrue(emptyReader.readBoolean());
        assertEquals(0, emptyReader.remaining());

        // data URL 接頭辞の無い Base64 文字列もデコードする。
        Writer bare = new Writer();
        bare.writeBoolean(true);
        bare.writeString("{\"text\":\"Bare\"}");
        bare.writeBoolean(true);
        bare.writeString("AQIDBA==");
        bare.writeBoolean(false);
        byte[] bareOut = PlayTranslator.translateServerData761To762(bare.bytes());
        Reader bareReader = new Reader(bareOut);
        assertEquals("{\"text\":\"Bare\"}", bareReader.readString());
        assertTrue(bareReader.readBoolean());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, bareReader.readByteArray());
        assertFalse(bareReader.readBoolean());

        // デコードできない icon は無しとして扱う。
        Writer bad = new Writer();
        bad.writeBoolean(true);
        bad.writeString("{\"text\":\"Bad\"}");
        bad.writeBoolean(true);
        bad.writeString("!!!not-base64!!!");
        bad.writeBoolean(false);
        byte[] badOut = PlayTranslator.translateServerData761To762(bad.bytes());
        Reader badReader = new Reader(badOut);
        assertEquals("{\"text\":\"Bad\"}", badReader.readString());
        assertFalse(badReader.readBoolean());
        assertFalse(badReader.readBoolean());
    }

    @Test
    void serverDataBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translateServerData762To761(broken));
        assertArrayEquals(broken, PlayTranslator.translateServerData761To762(broken));
    }
}
