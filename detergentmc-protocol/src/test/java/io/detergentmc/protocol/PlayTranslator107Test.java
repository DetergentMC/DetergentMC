package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame107;
import io.detergentmc.protocol.PlayTranslator.JoinGame758;
import org.junit.jupiter.api.Test;

/**
 * 107（1.9）の Join Game／Respawn 変換。Join Game は 108 との唯一の
 * 形式差分として dimension が i8（108 以降は i32）であり、実バニラ
 * 1.9 の Join Game 採取（17 バイトを i8 で読み切れること）で確定した。
 * Respawn は 108 と完全同一（dimension も i32）のため 108 の実装へ
 * 委譲する。107 の Join Game は difficulty（u8）を持ち
 * viewDistance を持たない。
 */
class PlayTranslator107Test {

    /** 107 の Join Game ペイロード（実 1.9 のレイアウト。dimension だけ i8）。 */
    private static byte[] joinGame107Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeByte(0); // dimension（107 は i8。108 以降は i32）
        writer.writeByte(1); // difficulty
        writer.writeByte(20);
        writer.writeString("flat");
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void joinGame107Parses() {
        JoinGame107 parsed = PlayTranslator.parseJoinGame107(joinGame107Payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(0, parsed.dimension());
        assertEquals(1, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
        assertEquals(false, parsed.reducedDebug());
    }

    @Test
    void joinGame107To758DropsDifficultyAndSynthesizesViewDistance() {
        byte[] out = PlayTranslator.translateJoinGame107To758(joinGame107Payload());
        JoinGame758 parsed = PlayTranslator.parseJoinGame758(out);
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.gameMode());
        assertEquals(10, parsed.viewDistance());
    }

    @Test
    void joinGame758To107SynthesizesNormalDifficulty() {
        byte[] as758 = PlayTranslator.translateJoinGame107To758(joinGame107Payload());
        byte[] back = PlayTranslator.translateJoinGame758To107(as758);
        JoinGame107 parsed = PlayTranslator.parseJoinGame107(back);
        assertEquals(42, parsed.entityId());
        // 758 側に difficulty は無いため、107 へ戻すときは normal（2）を合成する。
        assertEquals(2, parsed.difficulty());
        assertEquals(20, parsed.maxPlayers());
        assertEquals("flat", parsed.levelType());
    }

    @Test
    void joinGame107NetherDimensionSurvivesRoundTrip() {
        // dimension は符号付きバイト：-1（ネザー）が i8⇔i32 の
        // 付け替えで符号拡張され、正の値に化けないことを固定する。
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeByte(-1); // dimension（nether、107 は i8）
        writer.writeByte(1);
        writer.writeByte(20);
        writer.writeString("flat");
        writer.writeBoolean(false);
        JoinGame107 parsed = PlayTranslator.parseJoinGame107(writer.bytes());
        assertEquals(-1, parsed.dimension());
        byte[] as758 = PlayTranslator.translateJoinGame107To758(writer.bytes());
        byte[] back = PlayTranslator.translateJoinGame758To107(as758);
        assertEquals(-1, PlayTranslator.parseJoinGame107(back).dimension());
    }

    @Test
    void joinGame107IsThreeBytesShorterThan108() {
        // 同じ内容でも 107 は dimension が i8 のため 108 より3バイト短い。
        Writer as108 = new Writer();
        as108.writeInt(42);
        as108.writeByte(0);
        as108.writeInt(0); // dimension（108 は i32）
        as108.writeByte(1);
        as108.writeByte(20);
        as108.writeString("flat");
        as108.writeBoolean(false);
        assertEquals(as108.bytes().length - 3, joinGame107Payload().length);
    }

    @Test
    void respawn107To758DropsDifficulty() {
        Writer writer = new Writer();
        writer.writeInt(-1); // dimension（nether）
        writer.writeByte(3); // difficulty（hard、758 では落ちる）
        writer.writeByte(1); // gamemode（creative）
        writer.writeString("default");
        byte[] as758 = PlayTranslator.translateRespawn107To758(writer.bytes(), null);
        byte[] back = PlayTranslator.translateRespawn758To107(as758, null);
        Writer expected = new Writer();
        expected.writeInt(-1);
        expected.writeByte(2);
        expected.writeByte(1);
        expected.writeString("default");
        assertArrayEquals(expected.bytes(), back);
    }
}
