package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} と {@link PacketTranslator} の逆向き（764→765）
 * 変換テスト。764形式のペイロードを合成して765形式に変換し、
 * 読み直して中身を確認する。
 */
class PlayTranslatorUp764Test {

    // ---- PacketTranslator（IDの付け替えの逆向き） ----

    @Test
    void packetIdsReverse() {
        // 764→765：Respawn は 0x43→0x45、Join Game は 0x29→0x29（同じ）。
        assertEquals(0x45, PacketTranslator.toClientId(PacketName.PLAY_RESPAWN, 764, 765).orElseThrow());
        assertEquals(0x29, PacketTranslator.toClientId(PacketName.PLAY_JOIN_GAME, 764, 765).orElseThrow());
        // 765→764：Keep Alive（サーバー行き）は 0x15→0x14。
        assertEquals(0x14, PacketTranslator.toServerId(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, 765, 764).orElseThrow());
        // 764に無いパケット（Reset Score）は 765→764 で空。
        assertTrue(PacketTranslator.translateId(PacketName.PLAY_RESET_SCORE, 765, 764).isEmpty());
        // 765に無いパケット（旧式リソースパック）は 764→765 で空。
        assertTrue(PacketTranslator.translateId(PacketName.PLAY_RESOURCE_PACK, 764, 765).isEmpty());
    }

    @Test
    void needsTranslationWithServerProtocol() {
        assertTrue(PacketTranslator.needsTranslation(765, 764));
        assertTrue(PacketTranslator.needsTranslation(764, 765));
        assertFalse(PacketTranslator.needsTranslation(765, 765));
        assertFalse(PacketTranslator.needsTranslation(764, 764));
        assertEquals(764, ProtocolVersion.serverProtocolFor("1.20.2"));
        assertEquals(765, ProtocolVersion.serverProtocolFor("1.20.4"));
        assertEquals(765, ProtocolVersion.serverProtocolFor("1.20.3"));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
    }

    @Test
    void existingToServerAndToClientStillAssume765() {
        // 既存の2引数版はサーバー765のまま（後方互換）。
        assertEquals(0x15, PacketTranslator.toServerId(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, 763).orElseThrow());
        assertEquals(0x28, PacketTranslator.toClientId(PacketName.PLAY_JOIN_GAME, 763).orElseThrow());
    }

    // ---- ステータス JSON ----

    @Test
    void statusJsonUp() {
        String json = "{\"version\":{\"name\":\"1.20.2\",\"protocol\":764},\"players\":{\"max\":20,\"online\":1}}";
        String rewritten = PlayTranslator.rewriteStatusJsonFor765(json);
        assertTrue(rewritten.contains("\"protocol\":765"));
        assertTrue(rewritten.contains("\"name\":\"1.20.4\""));
        assertTrue(rewritten.contains("\"max\":20"));
        // 下向きも壊れていない。
        String down = PlayTranslator.rewriteStatusJsonFor764(
                "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765}}");
        assertTrue(down.contains("\"protocol\":764"));
        assertTrue(down.contains("\"name\":\"1.20.2\""));
    }

    // ---- Join Game / Chat（サーバー行き） ----

    @Test
    void joinGameAndChatServerboundAreIdentity() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};
        assertArrayEquals(payload, PlayTranslator.translateJoinGame764To765(payload));
        assertArrayEquals(payload, PlayTranslator.translateChatMessage765To764(payload));
        assertArrayEquals(payload, PlayTranslator.translateChatCommand765To764(payload));
    }

    // ---- チャット（クライアント行き） ----

    @Test
    void systemChatUp() {
        Writer payload = new Writer();
        payload.writeString("{\"text\":\"Hello\"}");
        payload.writeBoolean(true);
        byte[] translated = PlayTranslator.translateSystemChat764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"Hello\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertTrue(reader.readBoolean());
    }

    @Test
    void playerChatUp() {
        UUID sender = UUID.randomUUID();
        Writer payload = new Writer();
        payload.writeUuid(sender);
        payload.writeVarInt(3);
        payload.writeBoolean(false); // 署名なし
        payload.writeString("hello");
        payload.writeLong(1000L);
        payload.writeLong(0L);
        payload.writeVarInt(0); // previous 無し
        payload.writeBoolean(true);
        payload.writeString("{\"text\":\"hello\"}"); // unsigned
        payload.writeVarInt(0); // filterType
        payload.writeVarInt(0); // chatType
        payload.writeString("{\"text\":\"Alice\"}"); // networkName
        payload.writeBoolean(false); // target 無し
        byte[] translated = PlayTranslator.translatePlayerChat764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(sender, reader.readUuid());
        assertEquals(3, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals("hello", reader.readString());
        assertEquals(1000L, reader.readLong());
        assertEquals(0L, reader.readLong());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"hello\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals("{\"text\":\"Alice\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void profilelessChatUp() {
        Writer payload = new Writer();
        payload.writeString("{\"text\":\"msg\"}");
        payload.writeVarInt(1);
        payload.writeString("{\"text\":\"Bob\"}");
        payload.writeBoolean(true);
        payload.writeString("{\"text\":\"Alice\"}");
        byte[] translated = PlayTranslator.translateProfilelessChat764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"msg\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(1, reader.readVarInt());
        assertEquals("{\"text\":\"Bob\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Alice\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
    }

    @Test
    void deathCombatEventUp() {
        Writer payload = new Writer();
        payload.writeVarInt(9);
        payload.writeString("{\"text\":\"died\"}");
        byte[] translated = PlayTranslator.translateDeathCombatEvent764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(9, reader.readVarInt());
        assertEquals("{\"text\":\"died\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
    }

    // ---- スコアボード ----

    @Test
    void scoreboardObjectiveUp() {
        Writer payload = new Writer();
        payload.writeString("obj");
        payload.writeByte(0);
        payload.writeString("{\"text\":\"Obj\"}");
        payload.writeVarInt(1);
        byte[] translated = PlayTranslator.translateScoreboardObjective764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("obj", reader.readString());
        assertEquals(0, reader.readByte());
        assertEquals("{\"text\":\"Obj\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(1, reader.readVarInt());
        assertEquals(false, reader.readBoolean()); // numberFormat 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void scoreboardScoreUpSet() {
        Writer payload = new Writer();
        payload.writeString("Alice");
        payload.writeVarInt(0); // 設定
        payload.writeString("obj");
        payload.writeVarInt(42);
        Optional<byte[]> translated = PlayTranslator.translateScoreboardScore764To765(payload.bytes());
        assertTrue(translated.isPresent());
        Reader reader = new Reader(translated.orElseThrow());
        assertEquals("Alice", reader.readString());
        assertEquals("obj", reader.readString());
        assertEquals(42, reader.readVarInt());
        assertEquals(false, reader.readBoolean()); // displayName 無し
        assertEquals(false, reader.readBoolean()); // numberFormat 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void scoreboardScoreUpRemoveBecomesResetScore() {
        Writer payload = new Writer();
        payload.writeString("Alice");
        payload.writeVarInt(1); // 削除
        payload.writeString("obj");
        Optional<byte[]> translated = PlayTranslator.translateScoreboardScore764To765(payload.bytes());
        assertTrue(translated.isEmpty());
        // 代わりに Reset Score を作る。
        byte[] reset = PlayTranslator.buildResetScore765ForRemoval(payload.bytes());
        Reader reader = new Reader(reset);
        assertEquals("Alice", reader.readString());
        assertTrue(reader.readBoolean());
        assertEquals("obj", reader.readString());
        assertEquals(0, reader.remaining());
    }

    // ---- その他のクライアント行き ----

    @Test
    void disconnectUp() {
        Writer payload = new Writer();
        payload.writeString("{\"text\":\"bye\"}");
        byte[] translated = PlayTranslator.translateDisconnect764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"bye\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
    }

    @Test
    void serverDataUp() {
        Writer payload = new Writer();
        payload.writeString("{\"text\":\"A Minecraft Server\"}");
        payload.writeBoolean(false);
        payload.writeBoolean(true);
        byte[] translated = PlayTranslator.translateServerData764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"A Minecraft Server\"}",
                Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(false, reader.readBoolean());
        assertTrue(reader.readBoolean());
    }

    @Test
    void actionBarAndTitleUp() {
        Writer payload = new Writer();
        payload.writeString("\"Action\"");
        byte[] translated = PlayTranslator.translateActionBar764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("\"Action\"", Nbt.toJsonString(reader.readAnonymousNbt().tag()));

        Writer title = new Writer();
        title.writeString("{\"text\":\"Title\"}");
        byte[] titleTranslated = PlayTranslator.translateTitleText764To765(title.bytes());
        Reader titleReader = new Reader(titleTranslated);
        assertEquals("{\"text\":\"Title\"}", Nbt.toJsonString(titleReader.readAnonymousNbt().tag()));
    }

    @Test
    void playerlistHeaderUp() {
        Writer payload = new Writer();
        payload.writeString("{\"text\":\"H\"}");
        payload.writeString("{\"text\":\"F\"}");
        byte[] translated = PlayTranslator.translatePlayerlistHeader764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals("{\"text\":\"H\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals("{\"text\":\"F\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
    }

    @Test
    void playerInfoUpWithDisplayName() {
        UUID uuid = UUID.randomUUID();
        Writer payload = new Writer();
        payload.writeUnsignedByte(0x21); // add_player(0x01)＋update_display_name(0x20)
        payload.writeVarInt(1);
        payload.writeUuid(uuid);
        payload.writeString("Alice");
        payload.writeVarInt(0); // properties 無し
        payload.writeBoolean(true);
        payload.writeString("{\"text\":\"Alice\"}");
        byte[] translated = PlayTranslator.translatePlayerInfo764To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x21, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(uuid, reader.readUuid());
        assertEquals("Alice", reader.readString());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals("{\"text\":\"Alice\"}", Nbt.toJsonString(reader.readAnonymousNbt().tag()));
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerInfoUpWithoutDisplayNameIsIdentity() {
        UUID uuid = UUID.randomUUID();
        Writer payload = new Writer();
        payload.writeUnsignedByte(0x10); // update_latency のみ
        payload.writeVarInt(1);
        payload.writeUuid(uuid);
        payload.writeVarInt(50);
        assertArrayEquals(payload.bytes(), PlayTranslator.translatePlayerInfo764To765(payload.bytes()));
    }
}
