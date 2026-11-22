package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame578;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 575（1.15.1）の Play 変換。形式は 578 と同一のため、変換関数は
 * 578 と共用される（{@link PlayTranslator} の 578 系関数）。
 * ここでは 575 の実レイアウトで Join Game と Login Success を固定する。
 */
class PlayTranslator575Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** 575 の Join Game ペイロード（実 1.15.1 のレイアウト＝578 と同一）。 */
    private static byte[] joinGame575Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeLong(12345L);
        writer.writeByte(20);
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    @Test
    void joinGame575ParsesRealLayout() {
        JoinGame578 parsed = PlayTranslator.parseJoinGame578(joinGame575Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(12345L, parsed.hashedSeed());
    }

    @Test
    void loginSuccess765To575UsesStringUuid() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0); // properties 0件（765 形式）
        byte[] out = PlayTranslator.translateLoginSuccess765To578(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(PLAYER_UUID.toString(), reader.readString());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginSuccess575To765UsesBinaryUuid() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        byte[] out = PlayTranslator.translateLoginSuccess578To765(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(PLAYER_UUID, reader.readUuid());
        assertEquals("Steve", reader.readString());
    }
}
