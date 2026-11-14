package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の760（1.19.1／1.19.2）⇔761（1.19.3）向け変換テスト。
 *
 * <p>760と761で構造が違うプレイ状態のパケット（minecraft-data protocol.json で確認）：
 * Player Chat（760 は Message Header 分離＋previousSignature＋formattedMessage＋
 * UUID 付き previousMessages、761 は index＋256バイト署名＋id 付き previousMessages）、
 * Player Info（760 は action varint の旧式、761 は bitflags）、
 * Chat Message／Chat Command（760 は signedPreview＋lastRejectedMessage、
 * 761 は messageCount＋acknowledged 3バイト）、Message Acknowledgement、
 * Server Data（760 にだけ previewsChat）、Explosion（760 は f32、761 は f64）、
 * Hide Message（760 は署名バッファ、761 は id＋256バイト署名）。
 * 760 にだけある Message Header／Chat Preview／Should Display Chat Preview は
 * 761 へは出さない。Chat Session Update（761）は 760 サーバーへは出さない。
 * Join Game／Synchronize Player Position は両方とも同じ構造のため変換しない。
 * Declare Commands の引数種別は翻訳しない（763↔764 でも未対応。素通し）。
 *
 * <p>既知の制限：761→760 の Player Chat は previousMessages を空で送り、
 * Message Header は合成しない（本物の760クライアントでは未検証のメッセージと
 * 表示される可能性がある）。Chat Command／Chat Message の acknowledged は
 * {0,0,0}（未確認）で合成する。署名チェーンの検証は境界を越えて維持できない。
 */
class PlayTranslator760Test {

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
    void chatMessage760To761Keeps256ByteSignatureAndCountsPrevious() {
        Writer payload = new Writer();
        payload.writeString("hello");
        payload.writeLong(123456789L); // timestamp
        payload.writeLong(987654321L); // salt
        payload.writeByteArray(SIG_256); // 署名
        payload.writeBoolean(true); // signedPreview（761 には無い）
        payload.writeVarInt(1); // previousMessages 1件
        payload.writeUuid(OTHER);
        payload.writeByteArray(new byte[]{0x01, 0x02}); // 256バイトではない署名
        payload.writeBoolean(false); // lastRejectedMessage 無し
        byte[] translated = PlayTranslator.translateChatMessage760To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("hello", reader.readString());
        assertEquals(123456789L, reader.readLong());
        assertEquals(987654321L, reader.readLong());
        assertTrue(reader.readBoolean()); // 署名あり
        assertArrayEquals(SIG_256, reader.readBytes(256));
        assertEquals(1, reader.readVarInt()); // offset = previousMessages の件数
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3)); // acknowledged（合成）
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatMessage760To761WithoutSignature() {
        Writer payload = new Writer();
        payload.writeString("hi");
        payload.writeLong(1L);
        payload.writeLong(2L);
        payload.writeByteArray(new byte[0]); // 署名無し
        payload.writeBoolean(false);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        byte[] translated = PlayTranslator.translateChatMessage760To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("hi", reader.readString());
        assertEquals(1L, reader.readLong());
        assertEquals(2L, reader.readLong());
        assertFalse(reader.readBoolean()); // 署名無し
        assertEquals(0, reader.readVarInt()); // offset
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3));
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatMessage761To760DropsOffsetAndAcknowledged() {
        Writer payload = new Writer();
        payload.writeString("hello");
        payload.writeLong(123456789L);
        payload.writeLong(987654321L);
        payload.writeBoolean(true); // 署名あり
        payload.writeBytes(SIG_256);
        payload.writeVarInt(3); // offset（760 には無い）
        payload.writeBytes(new byte[]{1, 2, 3}); // acknowledged（760 には無い）
        byte[] translated = PlayTranslator.translateChatMessage761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("hello", reader.readString());
        assertEquals(123456789L, reader.readLong());
        assertEquals(987654321L, reader.readLong());
        assertArrayEquals(SIG_256, reader.readByteArray());
        assertFalse(reader.readBoolean()); // signedPreview（合成・false）
        assertEquals(0, reader.readVarInt()); // previousMessages（空）
        assertFalse(reader.readBoolean()); // lastRejectedMessage 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatMessageBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translateChatMessage760To761(broken));
        assertArrayEquals(broken, PlayTranslator.translateChatMessage761To760(broken));
    }

    // ---- Chat Command（サーバー行き） ----

    @Test
    void chatCommand760To761KeepsOnly256ByteSignatures() {
        Writer payload = new Writer();
        payload.writeString("/say hi");
        payload.writeLong(10L);
        payload.writeLong(20L);
        payload.writeVarInt(2); // 引数署名 2件
        payload.writeString("msg");
        payload.writeByteArray(SIG_256); // 256バイト（残す）
        payload.writeString("other");
        payload.writeByteArray(new byte[]{0x01}); // 256バイトではない（落とす）
        payload.writeBoolean(true); // signedPreview（761 には無い）
        payload.writeVarInt(1); // previousMessages 1件
        payload.writeUuid(OTHER);
        payload.writeByteArray(SIG_256);
        payload.writeBoolean(false); // lastRejectedMessage 無し
        byte[] translated = PlayTranslator.translateChatCommand760To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("/say hi", reader.readString());
        assertEquals(10L, reader.readLong());
        assertEquals(20L, reader.readLong());
        assertEquals(1, reader.readVarInt()); // 残った引数署名は1件
        assertEquals("msg", reader.readString());
        assertArrayEquals(SIG_256, reader.readBytes(256));
        assertEquals(1, reader.readVarInt()); // messageCount = previousMessages の件数
        assertArrayEquals(new byte[]{0, 0, 0}, reader.readBytes(3)); // acknowledged（合成）
        assertEquals(0, reader.remaining());
    }

    @Test
    void chatCommand761To760() {
        Writer payload = new Writer();
        payload.writeString("/say hi");
        payload.writeLong(10L);
        payload.writeLong(20L);
        payload.writeVarInt(1);
        payload.writeString("msg");
        payload.writeBytes(SIG_256);
        payload.writeVarInt(2); // messageCount（760 には無い）
        payload.writeBytes(new byte[]{1, 2, 3}); // acknowledged（760 には無い）
        byte[] translated = PlayTranslator.translateChatCommand761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("/say hi", reader.readString());
        assertEquals(10L, reader.readLong());
        assertEquals(20L, reader.readLong());
        assertEquals(1, reader.readVarInt());
        assertEquals("msg", reader.readString());
        assertArrayEquals(SIG_256, reader.readByteArray());
        assertFalse(reader.readBoolean()); // signedPreview（合成・false）
        assertEquals(0, reader.readVarInt()); // previousMessages（空）
        assertFalse(reader.readBoolean()); // lastRejectedMessage 無し
        assertEquals(0, reader.remaining());
    }

    // ---- Message Acknowledgement（サーバー行き） ----

    @Test
    void messageAcknowledgement760To761KeepsCountOnly() {
        Writer payload = new Writer();
        payload.writeVarInt(2); // previousMessages 2件
        payload.writeUuid(OTHER);
        payload.writeByteArray(SIG_256);
        payload.writeUuid(SENDER);
        payload.writeByteArray(new byte[]{0x01});
        payload.writeBoolean(true); // lastRejectedMessage あり
        payload.writeUuid(OTHER);
        payload.writeByteArray(SIG_256);
        byte[] translated = PlayTranslator.translateMessageAcknowledgement760To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(2, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void messageAcknowledgement761To760SendsEmpty() {
        Writer payload = new Writer();
        payload.writeVarInt(5); // count（760 では中身は復元できない）
        byte[] translated = PlayTranslator.translateMessageAcknowledgement761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readVarInt()); // previousMessages（空）
        assertFalse(reader.readBoolean()); // lastRejectedMessage 無し
        assertEquals(0, reader.remaining());
    }

    // ---- Player Chat（クライアント行き） ----

    /** 761形式の Player Chat ペイロード（署名あり・previousMessages 1件・unsigned なし）。 */
    private static byte[] playerChat761() {
        Writer payload = new Writer();
        payload.writeUuid(SENDER);
        payload.writeVarInt(7); // index（760 には無い）
        payload.writeBoolean(true); // 署名あり
        payload.writeBytes(SIG_256);
        payload.writeString("hello");
        payload.writeLong(100L); // timestamp
        payload.writeLong(200L); // salt
        payload.writeVarInt(1); // previousMessages 1件
        payload.writeVarInt(0); // id=0（署名付き）
        payload.writeBytes(SIG_256);
        payload.writeBoolean(false); // unsigned 無し
        payload.writeVarInt(0); // filterType（無し）
        payload.writeVarInt(0); // chatType
        payload.writeString("Steve"); // networkName
        payload.writeBoolean(false); // target 無し
        return payload.bytes();
    }

    /** 760形式の Player Chat ペイロード（previousSignature あり・formattedMessage あり・previous 1件）。 */
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
        payload.writeBoolean(false); // unsigned 無し
        payload.writeVarInt(0); // filterType（無し）
        payload.writeVarInt(0); // chatType
        payload.writeString("Steve"); // networkName
        payload.writeBoolean(false); // target 無し
        return payload.bytes();
    }

    @Test
    void playerChat761To760SendsEmptyPreviousAndNoMessageHeader() {
        byte[] translated = PlayTranslator.translatePlayerChat761To760(playerChat761());
        Reader reader = new Reader(translated);
        assertFalse(reader.readBoolean()); // previousSignature 無し
        assertEquals(SENDER, reader.readUuid());
        assertArrayEquals(SIG_256, reader.readByteArray()); // 署名
        assertEquals("hello", reader.readString());
        assertFalse(reader.readBoolean()); // formattedMessage 無し
        assertEquals(100L, reader.readLong());
        assertEquals(200L, reader.readLong());
        assertEquals(0, reader.readVarInt()); // previousMessages（空。761 の id 付き形式からは復元できない）
        assertFalse(reader.readBoolean()); // unsigned 無し
        assertEquals(0, reader.readVarInt()); // filterType
        assertEquals(0, reader.readVarInt()); // chatType
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // target 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChat761To760WithoutSignatureWritesEmptyBuffer() {
        Writer payload = new Writer();
        payload.writeUuid(SENDER);
        payload.writeVarInt(0); // index
        payload.writeBoolean(false); // 署名無し
        payload.writeString("hi");
        payload.writeLong(1L);
        payload.writeLong(2L);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        payload.writeVarInt(0);
        payload.writeVarInt(0);
        payload.writeString("Steve");
        payload.writeBoolean(false);
        byte[] translated = PlayTranslator.translatePlayerChat761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertFalse(reader.readBoolean());
        assertEquals(SENDER, reader.readUuid());
        assertArrayEquals(new byte[0], reader.readByteArray()); // 署名は空のバッファ（760 では必須項目）
        assertEquals("hi", reader.readString());
        assertFalse(reader.readBoolean()); // formattedMessage 無し
        assertEquals(1L, reader.readLong());
        assertEquals(2L, reader.readLong());
        assertEquals(0, reader.readVarInt()); // previousMessages（空）
        assertFalse(reader.readBoolean()); // unsigned 無し
        assertEquals(0, reader.readVarInt()); // filterType
        assertEquals(0, reader.readVarInt()); // chatType
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // target 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerChat760To761DropsPreviousSignatureAndFormattedMessage() {
        byte[] translated = PlayTranslator.translatePlayerChat760To761(playerChat760(), 3);
        Reader reader = new Reader(translated);
        assertEquals(SENDER, reader.readUuid());
        assertEquals(3, reader.readVarInt()); // index（呼び出し側が渡したもの）
        assertTrue(reader.readBoolean()); // 署名あり
        assertArrayEquals(SIG_256, reader.readBytes(256));
        assertEquals("hello", reader.readString());
        assertEquals(100L, reader.readLong());
        assertEquals(200L, reader.readLong());
        assertEquals(1, reader.readVarInt()); // previousMessages 1件（256バイト署名のため残る）
        assertEquals(0, reader.readVarInt()); // id=0
        assertArrayEquals(SIG_256, reader.readBytes(256));
        assertFalse(reader.readBoolean()); // unsigned 無し
        assertEquals(0, reader.readVarInt()); // filterType
        assertEquals(0, reader.readVarInt()); // chatType
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // target 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void extractChatSender760() {
        assertEquals(SENDER, PlayTranslator.extractChatSender760(playerChat760()));
        // previousSignature 無しのペイロードからも読める。
        Writer payload = new Writer();
        payload.writeBoolean(false);
        payload.writeUuid(OTHER);
        payload.writeByteArray(new byte[0]);
        payload.writeString("x");
        payload.writeBoolean(false);
        payload.writeLong(0L);
        payload.writeLong(0L);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        payload.writeVarInt(0);
        payload.writeVarInt(0);
        payload.writeString("n");
        payload.writeBoolean(false);
        assertEquals(OTHER, PlayTranslator.extractChatSender760(payload.bytes()));
        // 読めないペイロードは nil UUID。
        assertEquals(new UUID(0, 0), PlayTranslator.extractChatSender760(new byte[]{0x00}));
    }

    @Test
    void playerChatBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, PlayTranslator.translatePlayerChat761To760(broken));
        assertArrayEquals(broken, PlayTranslator.translatePlayerChat760To761(broken, 0));
    }

    // ---- Player Info ----

    /** 761形式の Player Info（add_player＋initialize_chat、1人）。 */
    private static byte[] playerInfo761AddPlayer() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(0x03); // add_player＋initialize_chat
        payload.writeVarInt(1);
        payload.writeUuid(SENDER);
        payload.writeString("Steve");
        payload.writeVarInt(1); // プロパティ 1件
        payload.writeString("textures");
        payload.writeString("value");
        payload.writeBoolean(true);
        payload.writeString("sig");
        payload.writeBoolean(true); // chatSession あり
        payload.writeUuid(SENDER);
        payload.writeLong(999L); // expireTime
        payload.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        payload.writeByteArray(new byte[]{0x03, 0x04}); // 署名
        return payload.bytes();
    }

    /** 760形式の Player Info（action 0 add_player、1人・crypto あり）。 */
    private static byte[] playerInfo760AddPlayer() {
        Writer payload = new Writer();
        payload.writeVarInt(0); // add_player
        payload.writeVarInt(1);
        payload.writeUuid(SENDER);
        payload.writeString("Steve");
        payload.writeVarInt(1); // プロパティ 1件
        payload.writeString("textures");
        payload.writeString("value");
        payload.writeBoolean(true);
        payload.writeString("sig");
        payload.writeVarInt(1); // gamemode
        payload.writeVarInt(50); // ping
        payload.writeBoolean(true); // displayName あり
        payload.writeString("Steve");
        payload.writeBoolean(true); // crypto あり
        payload.writeLong(999L); // expireTime
        payload.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        payload.writeByteArray(new byte[]{0x03, 0x04}); // 署名
        return payload.bytes();
    }

    @Test
    void playerInfo761To760SplitsAddPlayerWithCrypto() {
        List<byte[]> translated = PlayTranslator.translatePlayerInfo761To760(playerInfo761AddPlayer());
        // add_player だけ（update_game_mode／latency／display_name は
        // 761 の flags に無いため出ない）。
        assertEquals(1, translated.size());
        Reader reader = new Reader(translated.get(0));
        assertEquals(0, reader.readVarInt()); // action 0 add_player
        assertEquals(1, reader.readVarInt());
        assertEquals(SENDER, reader.readUuid());
        assertEquals("Steve", reader.readString());
        assertEquals(1, reader.readVarInt());
        assertEquals("textures", reader.readString());
        assertEquals("value", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("sig", reader.readString());
        assertEquals(0, reader.readVarInt()); // gamemode（761 に無いため 0）
        assertEquals(0, reader.readVarInt()); // latency（761 に無いため 0）
        assertFalse(reader.readBoolean()); // displayName 無し
        assertTrue(reader.readBoolean()); // crypto あり
        assertEquals(999L, reader.readLong());
        assertArrayEquals(new byte[]{0x01, 0x02}, reader.readByteArray());
        assertArrayEquals(new byte[]{0x03, 0x04}, reader.readByteArray());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerInfo761To760SplitsAllActions() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(0x35); // add_player＋update_game_mode＋update_latency＋update_display_name
        payload.writeVarInt(1);
        payload.writeUuid(SENDER);
        payload.writeString("Steve");
        payload.writeVarInt(0); // プロパティ無し
        payload.writeVarInt(1); // gamemode
        payload.writeVarInt(50); // latency
        payload.writeBoolean(true); // displayName あり
        payload.writeString("Steve");
        List<byte[]> translated = PlayTranslator.translatePlayerInfo761To760(payload.bytes());
        assertEquals(4, translated.size());
        // 1つ目：add_player。
        Reader add = new Reader(translated.get(0));
        assertEquals(0, add.readVarInt());
        assertEquals(1, add.readVarInt());
        assertEquals(SENDER, add.readUuid());
        assertEquals("Steve", add.readString());
        // 2つ目：update_game_mode。
        Reader mode = new Reader(translated.get(1));
        assertEquals(1, mode.readVarInt());
        assertEquals(1, mode.readVarInt());
        assertEquals(SENDER, mode.readUuid());
        assertEquals(1, mode.readVarInt());
        // 3つ目：update_latency。
        Reader latency = new Reader(translated.get(2));
        assertEquals(2, latency.readVarInt());
        assertEquals(1, latency.readVarInt());
        assertEquals(SENDER, latency.readUuid());
        assertEquals(50, latency.readVarInt());
        // 4つ目：update_display_name（update_listed は 761 の flags に無いため出ない）。
        Reader display = new Reader(translated.get(3));
        assertEquals(3, display.readVarInt());
        assertEquals(1, display.readVarInt());
        assertEquals(SENDER, display.readUuid());
        assertTrue(display.readBoolean());
        assertEquals("Steve", display.readString());
        assertEquals(0, display.remaining());
    }

    @Test
    void playerInfo761To760DropsListedAndInitializeChatOnly() {
        // update_listed だけ（0x08）は 760 では表せないため 0件になる。
        Writer listed = new Writer();
        listed.writeUnsignedByte(0x08);
        listed.writeVarInt(1);
        listed.writeUuid(SENDER);
        listed.writeVarInt(0);
        assertEquals(0, PlayTranslator.translatePlayerInfo761To760(listed.bytes()).size());
        // initialize_chat だけ（0x02）は 760 では表せないため 0件になる。
        Writer chat = new Writer();
        chat.writeUnsignedByte(0x02);
        chat.writeVarInt(1);
        chat.writeUuid(SENDER);
        chat.writeBoolean(false);
        assertEquals(0, PlayTranslator.translatePlayerInfo761To760(chat.bytes()).size());
    }

    @Test
    void playerInfo760To761SplitsAddPlayerIntoTwoPackets() {
        List<byte[]> translated = PlayTranslator.translatePlayerInfo760To761(playerInfo760AddPlayer());
        assertEquals(2, translated.size());
        // 1つ目：add_player＋initialize_chat（crypto ありのため flags 0x03）。
        Reader add = new Reader(translated.get(0));
        assertEquals(0x03, add.readUnsignedByte());
        assertEquals(1, add.readVarInt());
        assertEquals(SENDER, add.readUuid());
        assertEquals("Steve", add.readString());
        assertEquals(1, add.readVarInt());
        assertEquals("textures", add.readString());
        assertEquals("value", add.readString());
        assertTrue(add.readBoolean());
        assertEquals("sig", add.readString());
        assertTrue(add.readBoolean()); // chatSession あり
        assertEquals(SENDER, add.readUuid()); // session UUID はプレイヤーの UUID で代用
        assertEquals(999L, add.readLong());
        assertArrayEquals(new byte[]{0x01, 0x02}, add.readByteArray());
        assertArrayEquals(new byte[]{0x03, 0x04}, add.readByteArray());
        assertEquals(0, add.remaining());
        // 2つ目：update_game_mode＋update_latency＋update_display_name（flags 0x34）。
        Reader updates = new Reader(translated.get(1));
        assertEquals(0x34, updates.readUnsignedByte());
        assertEquals(1, updates.readVarInt());
        assertEquals(SENDER, updates.readUuid());
        assertEquals(1, updates.readVarInt()); // gamemode
        assertEquals(50, updates.readVarInt()); // latency
        assertTrue(updates.readBoolean()); // displayName あり
        assertEquals("Steve", updates.readString());
        assertEquals(0, updates.remaining());
    }

    @Test
    void playerInfo760To761SingleActions() {
        // update_game_mode（action 1）。
        Writer mode = new Writer();
        mode.writeVarInt(1);
        mode.writeVarInt(1);
        mode.writeUuid(SENDER);
        mode.writeVarInt(2);
        List<byte[]> modeOut = PlayTranslator.translatePlayerInfo760To761(mode.bytes());
        assertEquals(1, modeOut.size());
        Reader modeReader = new Reader(modeOut.get(0));
        assertEquals(0x04, modeReader.readUnsignedByte());
        assertEquals(1, modeReader.readVarInt());
        assertEquals(SENDER, modeReader.readUuid());
        assertEquals(2, modeReader.readVarInt());
        // update_latency（action 2）。
        Writer latency = new Writer();
        latency.writeVarInt(2);
        latency.writeVarInt(1);
        latency.writeUuid(SENDER);
        latency.writeVarInt(33);
        Reader latencyReader = new Reader(
                PlayTranslator.translatePlayerInfo760To761(latency.bytes()).get(0));
        assertEquals(0x10, latencyReader.readUnsignedByte());
        assertEquals(1, latencyReader.readVarInt()); // count
        assertEquals(SENDER, latencyReader.readUuid());
        assertEquals(33, latencyReader.readVarInt());
        // update_display_name（action 3）。
        Writer name = new Writer();
        name.writeVarInt(3);
        name.writeVarInt(1);
        name.writeUuid(SENDER);
        name.writeBoolean(true);
        name.writeString("Alex");
        Reader nameReader = new Reader(
                PlayTranslator.translatePlayerInfo760To761(name.bytes()).get(0));
        assertEquals(0x20, nameReader.readUnsignedByte());
        assertEquals(1, nameReader.readVarInt()); // count
        assertEquals(SENDER, nameReader.readUuid());
        assertTrue(nameReader.readBoolean());
        assertEquals("Alex", nameReader.readString());
    }

    @Test
    void playerInfoRemoveBothWays() {
        // 760 action 4 → 761 Player Info Remove。
        Writer remove760 = new Writer();
        remove760.writeVarInt(4);
        remove760.writeVarInt(2);
        remove760.writeUuid(SENDER);
        remove760.writeUuid(OTHER);
        byte[] to761 = PlayTranslator.translatePlayerInfoRemove760To761(remove760.bytes());
        Reader reader761 = new Reader(to761);
        assertEquals(2, reader761.readVarInt());
        assertEquals(SENDER, reader761.readUuid());
        assertEquals(OTHER, reader761.readUuid());
        assertEquals(0, reader761.remaining());
        // 761 Player Info Remove → 760 action 4。
        Writer remove761 = new Writer();
        remove761.writeVarInt(2);
        remove761.writeUuid(SENDER);
        remove761.writeUuid(OTHER);
        byte[] to760 = PlayTranslator.translatePlayerInfoRemove761To760(remove761.bytes());
        Reader reader760 = new Reader(to760);
        assertEquals(4, reader760.readVarInt());
        assertEquals(2, reader760.readVarInt());
        assertEquals(SENDER, reader760.readUuid());
        assertEquals(OTHER, reader760.readUuid());
        assertEquals(0, reader760.remaining());
    }

    @Test
    void playerInfoBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertEquals(1, PlayTranslator.translatePlayerInfo761To760(broken).size());
        assertArrayEquals(broken, PlayTranslator.translatePlayerInfo761To760(broken).get(0));
        assertEquals(1, PlayTranslator.translatePlayerInfo760To761(broken).size());
        assertArrayEquals(broken, PlayTranslator.translatePlayerInfo760To761(broken).get(0));
    }

    // ---- Server Data ----

    @Test
    void serverData761To760AddsPreviewsChatFalse() {
        Writer payload = new Writer();
        payload.writeBoolean(true); // motd あり
        payload.writeString("{\"text\":\"A Minecraft Server\"}");
        payload.writeBoolean(false); // icon 無し
        payload.writeBoolean(true); // enforcesSecureChat
        byte[] translated = PlayTranslator.translateServerData761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"A Minecraft Server\"}", reader.readString());
        assertFalse(reader.readBoolean()); // icon 無し
        assertFalse(reader.readBoolean()); // previewsChat（合成・false）
        assertTrue(reader.readBoolean()); // enforcesSecureChat
        assertEquals(0, reader.remaining());
    }

    @Test
    void serverData760To761DropsPreviewsChat() {
        Writer payload = new Writer();
        payload.writeBoolean(true);
        payload.writeString("{\"text\":\"A Minecraft Server\"}");
        payload.writeBoolean(true);
        payload.writeString("data:image/png;base64,AQIDBA==");
        payload.writeBoolean(true); // previewsChat（761 には無い）
        payload.writeBoolean(false); // enforcesSecureChat
        byte[] translated = PlayTranslator.translateServerData760To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"A Minecraft Server\"}", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("data:image/png;base64,AQIDBA==", reader.readString());
        assertFalse(reader.readBoolean()); // enforcesSecureChat
        assertEquals(0, reader.remaining());
    }

    // ---- Login Start ----

    @Test
    void loginStart760To765DropsSignatureAndKeepsUuid() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(true); // 署名データあり
        payload.writeLong(123L); // timestamp
        payload.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        payload.writeByteArray(new byte[]{0x03, 0x04}); // 署名
        payload.writeBoolean(true); // UUID あり
        payload.writeUuid(SENDER);
        byte[] translated = PlayTranslator.translateLoginStart760To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertEquals(SENDER, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart760To765WithoutUuidSynthesizesOfflineUuid() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(false); // 署名データ無し
        payload.writeBoolean(false); // UUID 無し
        byte[] translated = PlayTranslator.translateLoginStart760To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertEquals(PlayTranslator.offlineUuid("Steve"), reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart765To760() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeUuid(SENDER);
        byte[] translated = PlayTranslator.translateLoginStart765To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("Steve", reader.readString());
        assertFalse(reader.readBoolean()); // 署名データ無し
        assertTrue(reader.readBoolean()); // UUID あり
        assertEquals(SENDER, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    @Test
    void loginStart760To763RoundTrip() {
        Writer payload = new Writer();
        payload.writeString("Steve");
        payload.writeBoolean(false);
        payload.writeBoolean(true);
        payload.writeUuid(SENDER);
        byte[] to763 = PlayTranslator.translateLoginStart760To763(payload.bytes());
        Reader reader = new Reader(to763);
        assertEquals("Steve", reader.readString());
        assertTrue(reader.readBoolean()); // 763形式は bool＋UUID
        assertEquals(SENDER, reader.readUuid());
        assertEquals(0, reader.remaining());
    }

    // ---- Explosion ----

    @Test
    void explosion761To760ConvertsF64ToF32() {
        Writer payload = new Writer();
        payload.writeDouble(1.5);
        payload.writeDouble(64.25);
        payload.writeDouble(-2.75);
        payload.writeFloat(3.0f); // radius
        payload.writeVarInt(2);
        payload.writeByte(1);
        payload.writeByte(2);
        payload.writeByte(3);
        payload.writeByte(-1);
        payload.writeByte(-2);
        payload.writeByte(-3);
        payload.writeFloat(0.1f);
        payload.writeFloat(0.2f);
        payload.writeFloat(0.3f);
        byte[] translated = PlayTranslator.translateExplosion761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1.5f, reader.readFloat());
        assertEquals(64.25f, reader.readFloat());
        assertEquals(-2.75f, reader.readFloat());
        assertEquals(3.0f, reader.readFloat());
        assertEquals(2, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(2, reader.readByte());
        assertEquals(3, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-2, reader.readByte());
        assertEquals(-3, reader.readByte());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }

    @Test
    void explosion760To761ConvertsF32ToF64() {
        Writer payload = new Writer();
        payload.writeFloat(1.5f);
        payload.writeFloat(64.25f);
        payload.writeFloat(-2.75f);
        payload.writeFloat(3.0f);
        payload.writeVarInt(0);
        payload.writeFloat(0.1f);
        payload.writeFloat(0.2f);
        payload.writeFloat(0.3f);
        byte[] translated = PlayTranslator.translateExplosion760To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.25, reader.readDouble());
        assertEquals(-2.75, reader.readDouble());
        assertEquals(3.0f, reader.readFloat());
        assertEquals(0, reader.readVarInt());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }

    // ---- Hide Message ----

    @Test
    void hideMessage761To760OnlyWhenIdIsZero() {
        Writer payload = new Writer();
        payload.writeVarInt(0); // id=0（署名付き）
        payload.writeBytes(SIG_256);
        byte[] translated = PlayTranslator.translateHideMessage761To760(payload.bytes());
        Reader reader = new Reader(translated);
        assertArrayEquals(SIG_256, reader.readByteArray());
        assertEquals(0, reader.remaining());

        // id が 0 以外は 760 では表せないため null（呼び出し側は落とす）。
        Writer other = new Writer();
        other.writeVarInt(5);
        assertNull(PlayTranslator.translateHideMessage761To760(other.bytes()));
    }

    @Test
    void hideMessage760To761OnlyWhenSignatureIs256Bytes() {
        Writer payload = new Writer();
        payload.writeByteArray(SIG_256);
        byte[] translated = PlayTranslator.translateHideMessage760To761(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readVarInt());
        assertArrayEquals(SIG_256, reader.readBytes(256));
        assertEquals(0, reader.remaining());

        // 256バイトではない署名は null（呼び出し側は落とす）。
        Writer shortSig = new Writer();
        shortSig.writeByteArray(new byte[]{0x01, 0x02});
        assertNull(PlayTranslator.translateHideMessage760To761(shortSig.bytes()));
    }
}
