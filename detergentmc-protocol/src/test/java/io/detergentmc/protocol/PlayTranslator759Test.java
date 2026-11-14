package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の759（1.19）⇔760（1.19.1／1.19.2）向け変換テスト。
 *
 * <p>759と760で構造が違うプレイ状態のパケット（minecraft-data protocol.json で確認）：
 * Player Chat（759 は初代のインライン署名形式 {signedChatContent・unsigned・
 * type・senderUuid・senderName・senderTeam・timestamp・salt・signature}、
 * 760 は Message Header 分離＋previousSignature＋formattedMessage＋
 * UUID 付き previousMessages）、Chat Message／Chat Command（759 には
 * previousMessages・lastRejectedMessage が無い）、System Chat（759 は
 * type varint、760 は isActionBar bool）、Server Data（759 には
 * enforcesSecureChat が無い）、Trade List（件数が759は u8・760は varint。
 * {@link ChunkTranslator} 側で変換する）。Player Info は759・760ともに
 * 旧式の action varint のため変換しない。Multi Block Change の bool
 * （notTrustEdges⇔suppressLightUpdates）と Respawn の gamemode
 * （u8⇔i8）はワイヤ上同一のため変換しない。
 * 署名付きチャットは759（1.19）で導入された。760は連鎖ハッシュ＋
 * Message Header 付きの別形式。依頼文の「署名付きチャットは760で
 * 導入された」は誤り。759が初出。
 *
 * <p>既知の制限：759→760 の Player Chat は previousMessages を空で送り、
 * Message Header は合成しない（本物の760クライアントでは未検証のメッセージと
 * 表示される可能性がある）。署名チェーンの検証は境界を越えて維持できない。
 * System Chat の type 0（chat）と type 1（system）は760では区別できない
 * （どちらも isActionBar=false になる）。
 */
class PlayTranslator759Test {

    private static final UUID SENDER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final byte[] SIG_256 = new byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            SIG_256[i] = (byte) i;
        }
    }

    // ---- Chat Message（サーバー行き） ----

    @Test
    void chatMessage759To760AddsEmptyPreviousAndNoLastRejected() {
        Writer payload = new Writer();
        payload.writeString("hello");
        payload.writeLong(123456789L); // timestamp
        payload.writeLong(987654321L); // salt
        payload.writeByteArray(SIG_256); // 署名
        payload.writeBoolean(true); // signedPreview
        byte[] translated = PlayTranslator.translateChatMessage759To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("hello", reader.readString());
        assertEquals(123456789L, reader.readLong());
        assertEquals(987654321L, reader.readLong());
        assertArrayEquals(SIG_256, reader.readByteArray());
        assertTrue(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.readVarInt()); // previousMessages（空。759 には無い）
        assertFalse(reader.readBoolean()); // lastRejectedMessage 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatMessage760To759DropsPreviousAndLastRejected() {
        Writer payload = new Writer();
        payload.writeString("hello");
        payload.writeLong(123456789L);
        payload.writeLong(987654321L);
        payload.writeByteArray(SIG_256); // 署名
        payload.writeBoolean(true); // signedPreview
        payload.writeVarInt(1); // previousMessages 1件
        payload.writeUuid(OTHER);
        payload.writeByteArray(new byte[]{0x01, 0x02});
        payload.writeBoolean(true); // lastRejectedMessage あり
        payload.writeUuid(OTHER);
        payload.writeByteArray(SIG_256);
        byte[] translated = PlayTranslator.translateChatMessage760To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("hello", reader.readString());
        assertEquals(123456789L, reader.readLong());
        assertEquals(987654321L, reader.readLong());
        assertArrayEquals(SIG_256, reader.readByteArray());
        assertTrue(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatMessageBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translateChatMessage759To760(broken));
        assertArrayEquals(broken, PlayTranslator.translateChatMessage760To759(broken));
    }

    // ---- Chat Command（サーバー行き） ----

    @Test
    void chatCommand759To760KeepsArgSignaturesAndAddsEmptyPrevious() {
        Writer payload = new Writer();
        payload.writeString("/say hi");
        payload.writeLong(10L);
        payload.writeLong(20L);
        payload.writeVarInt(1); // 引数署名 1件
        payload.writeString("msg");
        payload.writeByteArray(SIG_256);
        payload.writeBoolean(true); // signedPreview
        byte[] translated = PlayTranslator.translateChatCommand759To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("/say hi", reader.readString());
        assertEquals(10L, reader.readLong());
        assertEquals(20L, reader.readLong());
        assertEquals(1, reader.readVarInt());
        assertEquals("msg", reader.readString());
        assertArrayEquals(SIG_256, reader.readByteArray()); // 引数署名はそのまま通る
        assertTrue(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.readVarInt()); // previousMessages（空）
        assertFalse(reader.readBoolean()); // lastRejectedMessage 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatCommand760To759DropsPreviousAndLastRejected() {
        Writer payload = new Writer();
        payload.writeString("/say hi");
        payload.writeLong(10L);
        payload.writeLong(20L);
        payload.writeVarInt(1);
        payload.writeString("msg");
        payload.writeByteArray(SIG_256);
        payload.writeBoolean(false); // signedPreview
        payload.writeVarInt(1); // previousMessages 1件
        payload.writeUuid(OTHER);
        payload.writeByteArray(new byte[]{0x01, 0x02});
        payload.writeBoolean(false); // lastRejectedMessage 無し
        byte[] translated = PlayTranslator.translateChatCommand760To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("/say hi", reader.readString());
        assertEquals(10L, reader.readLong());
        assertEquals(20L, reader.readLong());
        assertEquals(1, reader.readVarInt());
        assertEquals("msg", reader.readString());
        assertArrayEquals(SIG_256, reader.readByteArray());
        assertFalse(reader.readBoolean()); // signedPreview
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatCommandBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translateChatCommand759To760(broken));
        assertArrayEquals(broken, PlayTranslator.translateChatCommand760To759(broken));
    }

    // ---- Player Chat（クライアント行き） ----

    /** 759形式の Player Chat ペイロード（unsigned・senderTeam あり）。 */
    private static byte[] playerChat759() {
        Writer payload = new Writer();
        payload.writeString("hello"); // signedChatContent
        payload.writeBoolean(true); // unsigned あり
        payload.writeString("unsigned hello");
        payload.writeVarInt(1); // type（system）
        payload.writeUuid(SENDER);
        payload.writeString("Steve"); // senderName
        payload.writeBoolean(true); // senderTeam あり
        payload.writeString("TeamA");
        payload.writeLong(100L); // timestamp
        payload.writeLong(200L); // salt
        payload.writeByteArray(SIG_256); // 署名
        return payload.bytes();
    }

    /** 760形式の Player Chat ペイロード（previousSignature・formattedMessage・previous 1件・unsigned・target あり）。 */
    private static byte[] playerChat760() {
        Writer payload = new Writer();
        payload.writeBoolean(true); // previousSignature あり
        payload.writeByteArray(SIG_256);
        payload.writeUuid(SENDER);
        payload.writeByteArray(SIG_256); // 署名
        payload.writeString("hello");
        payload.writeBoolean(true); // formattedMessage あり
        payload.writeString("{\"text\":\"hello\"}");
        payload.writeLong(100L); // timestamp
        payload.writeLong(200L); // salt
        payload.writeVarInt(1); // previousMessages 1件
        payload.writeUuid(OTHER);
        payload.writeByteArray(SIG_256);
        payload.writeBoolean(true); // unsigned あり
        payload.writeString("unsigned hello");
        payload.writeVarInt(0); // filterType（無し）
        payload.writeVarInt(1); // chatType
        payload.writeString("Steve"); // networkName
        payload.writeBoolean(true); // target あり
        payload.writeString("TeamA");
        return payload.bytes();
    }

    @Test
    void playerChat759To760SendsEmptyPreviousAndNoMessageHeader() {
        byte[] translated = PlayTranslator.translatePlayerChat759To760(playerChat759());
        Reader reader = new Reader(translated);
        assertFalse(reader.readBoolean()); // previousSignature 無し
        assertEquals(SENDER, reader.readUuid());
        assertArrayEquals(SIG_256, reader.readByteArray()); // 署名
        assertEquals("hello", reader.readString());
        assertFalse(reader.readBoolean()); // formattedMessage 無し
        assertEquals(100L, reader.readLong());
        assertEquals(200L, reader.readLong());
        assertEquals(0, reader.readVarInt()); // previousMessages（空。759 には無い）
        assertTrue(reader.readBoolean()); // unsigned あり
        assertEquals("unsigned hello", reader.readString());
        assertEquals(0, reader.readVarInt()); // filterType
        assertEquals(1, reader.readVarInt()); // chatType
        assertEquals("Steve", reader.readString()); // networkName
        assertTrue(reader.readBoolean()); // target あり
        assertEquals("TeamA", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChat760To759DropsPreviousSignatureAndFormattedMessage() {
        byte[] translated = PlayTranslator.translatePlayerChat760To759(playerChat760());
        Reader reader = new Reader(translated);
        assertEquals("hello", reader.readString());
        assertTrue(reader.readBoolean()); // unsigned あり
        assertEquals("unsigned hello", reader.readString());
        assertEquals(1, reader.readVarInt()); // type
        assertEquals(SENDER, reader.readUuid());
        assertEquals("Steve", reader.readString()); // senderName
        assertTrue(reader.readBoolean()); // senderTeam あり
        assertEquals("TeamA", reader.readString());
        assertEquals(100L, reader.readLong());
        assertEquals(200L, reader.readLong());
        assertArrayEquals(SIG_256, reader.readByteArray()); // 署名
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChat760To759DropsMaskedFilter() {
        // filterType=2（マスク付き）は759では表せないため落とす。
        Writer payload = new Writer();
        payload.writeBoolean(false); // previousSignature 無し
        payload.writeUuid(SENDER);
        payload.writeByteArray(SIG_256);
        payload.writeString("hi");
        payload.writeBoolean(false); // formattedMessage 無し
        payload.writeLong(1L);
        payload.writeLong(2L);
        payload.writeVarInt(0); // previousMessages 0件
        payload.writeBoolean(false); // unsigned 無し
        payload.writeVarInt(2); // filterType（マスク付き）
        payload.writeVarInt(2); // マスク 2件
        payload.writeLong(0x1122334455667788L);
        payload.writeLong(0x99AABBCCDDEEFF00L);
        payload.writeVarInt(0); // chatType
        payload.writeString("Steve");
        payload.writeBoolean(false); // target 無し
        byte[] translated = PlayTranslator.translatePlayerChat760To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("hi", reader.readString());
        assertFalse(reader.readBoolean()); // unsigned 無し
        assertEquals(0, reader.readVarInt()); // type
        assertEquals(SENDER, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // senderTeam 無し
        assertEquals(1L, reader.readLong());
        assertEquals(2L, reader.readLong());
        assertArrayEquals(SIG_256, reader.readByteArray());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChatBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translatePlayerChat759To760(broken));
        assertArrayEquals(broken, PlayTranslator.translatePlayerChat760To759(broken));
    }

    // ---- System Chat（クライアント行き） ----

    @Test
    void systemChat759To760MapsType2ToActionBar() {
        Writer gameInfo = new Writer();
        gameInfo.writeString("{\"text\":\"hi\"}");
        gameInfo.writeVarInt(2); // game_info
        Reader gameInfoReader = new Reader(
                PlayTranslator.translateSystemChat759To760(gameInfo.bytes()));
        assertEquals("{\"text\":\"hi\"}", gameInfoReader.readString());
        assertTrue(gameInfoReader.readBoolean()); // isActionBar
        assertEquals(0, gameInfoReader.remaining());

        // type 0（chat）と type 1（system）は760では区別できない（どちらも false）。
        Writer chat = new Writer();
        chat.writeString("{\"text\":\"hi\"}");
        chat.writeVarInt(0);
        Reader chatReader = new Reader(
                PlayTranslator.translateSystemChat759To760(chat.bytes()));
        assertEquals("{\"text\":\"hi\"}", chatReader.readString());
        assertFalse(chatReader.readBoolean()); // isActionBar
        assertEquals(0, chatReader.remaining());
        Writer system = new Writer();
        system.writeString("{\"text\":\"hi\"}");
        system.writeVarInt(1);
        Reader systemReader = new Reader(
                PlayTranslator.translateSystemChat759To760(system.bytes()));
        assertEquals("{\"text\":\"hi\"}", systemReader.readString());
        assertFalse(systemReader.readBoolean()); // isActionBar
        assertEquals(0, systemReader.remaining());
    }

    @Test
    void systemChat760To759MapsActionBarToType2() {
        Writer actionBar = new Writer();
        actionBar.writeString("{\"text\":\"hi\"}");
        actionBar.writeBoolean(true); // isActionBar
        Reader actionBarReader = new Reader(
                PlayTranslator.translateSystemChat760To759(actionBar.bytes()));
        assertEquals("{\"text\":\"hi\"}", actionBarReader.readString());
        assertEquals(2, actionBarReader.readVarInt()); // game_info
        assertEquals(0, actionBarReader.remaining());

        Writer normal = new Writer();
        normal.writeString("{\"text\":\"hi\"}");
        normal.writeBoolean(false); // isActionBar
        Reader normalReader = new Reader(
                PlayTranslator.translateSystemChat760To759(normal.bytes()));
        assertEquals("{\"text\":\"hi\"}", normalReader.readString());
        assertEquals(1, normalReader.readVarInt()); // system
        assertEquals(0, normalReader.remaining());
    }

    @Test
    void systemChatBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translateSystemChat759To760(broken));
        assertArrayEquals(broken, PlayTranslator.translateSystemChat760To759(broken));
    }

    // ---- Server Data（クライアント行き） ----

    @Test
    void serverData759To760AddsEnforcesSecureChatFalse() {
        Writer payload = new Writer();
        payload.writeBoolean(true); // motd あり
        payload.writeString("{\"text\":\"A Minecraft Server\"}");
        payload.writeBoolean(false); // icon 無し
        payload.writeBoolean(true); // previewsChat
        byte[] translated = PlayTranslator.translateServerData759To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"A Minecraft Server\"}", reader.readString());
        assertFalse(reader.readBoolean()); // icon 無し
        assertTrue(reader.readBoolean()); // previewsChat
        assertFalse(reader.readBoolean()); // enforcesSecureChat（合成・false）
        assertEquals(0, reader.remaining());
    }

    @Test
    void serverData760To759DropsEnforcesSecureChat() {
        Writer payload = new Writer();
        payload.writeBoolean(true);
        payload.writeString("{\"text\":\"A Minecraft Server\"}");
        payload.writeBoolean(true);
        payload.writeString("data:image/png;base64,AQIDBA==");
        payload.writeBoolean(true); // previewsChat
        payload.writeBoolean(true); // enforcesSecureChat
        byte[] translated = PlayTranslator.translateServerData760To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"A Minecraft Server\"}", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("data:image/png;base64,AQIDBA==", reader.readString());
        assertTrue(reader.readBoolean()); // previewsChat
        assertEquals(0, reader.remaining());
    }

    // ---- Login Start ----

    @Test
    void loginStart759To760AddsNoUuidOption() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(true); // 署名データあり
        payload.writeLong(123L); // timestamp
        payload.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        payload.writeByteArray(new byte[]{0x03, 0x04}); // 署名
        byte[] translated = PlayTranslator.translateLoginStart759To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertTrue(reader.readBoolean()); // 署名データあり
        assertEquals(123L, reader.readLong());
        assertArrayEquals(new byte[]{0x01, 0x02}, reader.readByteArray());
        assertArrayEquals(new byte[]{0x03, 0x04}, reader.readByteArray());
        assertFalse(reader.readBoolean()); // UUID 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart760To759DropsUuidOption() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(false); // 署名データ無し
        payload.writeBoolean(true); // UUID あり
        payload.writeUuid(SENDER);
        byte[] translated = PlayTranslator.translateLoginStart760To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // 署名データ無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart759To765SynthesizesOfflineUuid() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(false); // 署名データ無し
        byte[] translated = PlayTranslator.translateLoginStart759To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertEquals(PlayTranslator.offlineUuid("Steve"), reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart759To763() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(false); // 署名データ無し
        byte[] translated = PlayTranslator.translateLoginStart759To763(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertTrue(reader.readBoolean()); // 763形式は bool＋UUID
        assertEquals(PlayTranslator.offlineUuid("Steve"), reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart765To759() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeUuid(SENDER);
        byte[] translated = PlayTranslator.translateLoginStart765To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // 署名データ無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart759WithTrailingBytesThrows() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(false);
        payload.writeByte(0x01); // 余分なバイト
        assertThrows(IllegalArgumentException.class,
                () -> PlayTranslator.translateLoginStart759To760(payload.bytes()));
    }
}
