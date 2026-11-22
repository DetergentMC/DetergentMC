package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 4（1.7.2〜1.7.5）の Login Success と UUID 文字列の形。
 * 4 の UUID 文字列はダッシュ無し32文字（Stage A で実バニラ 1.7.2 の
 * ワイヤ採取により確定）。値は 5 と同じ nameUUIDFromBytes 由来。
 */
class PlayTranslator4Test {

    private static final UUID UUID_VALUE =
            UUID.fromString("cf72409d-f7b8-35ae-b9c2-e0373fd7c562");
    private static final String UUID_DASHLESS = "cf72409df7b835aeb9c2e0373fd7c562";

    @Test
    void uuidStringForms() {
        assertEquals(UUID_DASHLESS, PlayTranslator.uuidToString4(UUID_VALUE));
        assertEquals(32, PlayTranslator.uuidToString4(UUID_VALUE).length());
        assertEquals(UUID_VALUE, PlayTranslator.uuidFromString4(UUID_DASHLESS));
        // ダッシュ付きも防御的に受け付ける。
        assertEquals(UUID_VALUE,
                PlayTranslator.uuidFromString4(UUID_VALUE.toString()));
    }

    @Test
    void loginSuccess765To4IsDashless() {
        Writer writer = new Writer();
        writer.writeUuid(UUID_VALUE);
        writer.writeString("StageABot");
        writer.writeVarInt(0); // properties
        byte[] payload4 = PlayTranslator.translateLoginSuccess765To4(writer.bytes());
        Reader reader = new Reader(payload4);
        assertEquals(UUID_DASHLESS, reader.readString());
        assertEquals("StageABot", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginSuccess4To765ParsesDashless() {
        Writer writer = new Writer();
        writer.writeString(UUID_DASHLESS);
        writer.writeString("StageABot");
        byte[] payload765 = PlayTranslator.translateLoginSuccess4To765(writer.bytes());
        Reader reader = new Reader(payload765);
        assertEquals(UUID_VALUE, reader.readUuid());
        assertEquals("StageABot", reader.readString());
        assertEquals(0, reader.readVarInt()); // properties 0件
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginSuccessRoundTrip4() {
        Writer writer = new Writer();
        writer.writeUuid(UUID_VALUE);
        writer.writeString("Steve");
        writer.writeVarInt(1); // properties 1件（4 では落ちる）
        writer.writeString("textures");
        writer.writeString("abc");
        writer.writeBoolean(false);
        byte[] original = writer.bytes();
        byte[] payload4 = PlayTranslator.translateLoginSuccess765To4(original);
        byte[] back = PlayTranslator.translateLoginSuccess4To765(payload4);
        Reader reader = new Reader(back);
        assertEquals(UUID_VALUE, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }
}
