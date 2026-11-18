package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame498;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 498（1.14.4）の Play 変換。573 との構造差（Stage A で実データ確定）：
 * Join Game に hashedSeed と enableRespawnScreen が無い、Respawn に
 * hashedSeed が無い、Spawn 系に末尾メタデータがある、World Particles の
 * 座標が f32。ここではその差分を固定する。
 */
class PlayTranslator498Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** 498 の Join Game ペイロード（実 1.14.4 のレイアウト）。 */
    private static byte[] joinGame498Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(20);
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void joinGame498ParsesRealLayout() {
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(joinGame498Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(0, parsed.dimension());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("default", parsed.levelType());
        assertEquals(10, parsed.viewDistance());
    }

    @Test
    void joinGame498To758SynthesizesSeedAndRespawnScreen() {
        byte[] out = PlayTranslator.translateJoinGame498To758(joinGame498Payload());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, parsed.entityId());
        assertEquals(0L, parsed.hashedSeed());
        assertTrue(parsed.enableRespawnScreen());
    }

    @Test
    void joinGame758To498DropsSeedAndRespawnScreen() {
        byte[] as758 = PlayTranslator.translateJoinGame498To758(joinGame498Payload());
        byte[] back = PlayTranslator.translateJoinGame758To498(as758);
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(back);
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(10, parsed.viewDistance());
        assertArrayEquals(joinGame498Payload(), back);
    }

    @Test
    void respawn498RoundTripsThrough758() {
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeByte(0);
        writer.writeString("default");
        byte[] original = writer.bytes();
        byte[] as758 = PlayTranslator.translateRespawn498To758(original, null);
        byte[] back = PlayTranslator.translateRespawn758To498(as758, null);
        Reader reader = new Reader(back);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals("default", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginSuccessSharedWith578Form() {
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
}
