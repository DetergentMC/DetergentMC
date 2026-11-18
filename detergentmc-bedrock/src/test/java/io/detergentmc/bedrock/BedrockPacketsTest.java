package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Bedrock ゲームパケット（Text / MovePlayer / StartGame 等）のテスト。 */
class BedrockPacketsTest {

    @Test
    void textChatRoundTrip() {
        byte[] encoded = BedrockPackets.encodeTextChat("Alex", "hello bedrock");
        assertEquals((byte) BedrockProtocol.TEXT_TYPE_CHAT, encoded[0]);
        BedrockPackets.TextMessage decoded = BedrockPackets.decodeText(encoded);
        assertEquals(BedrockProtocol.TEXT_TYPE_CHAT, decoded.type());
        assertEquals("Alex", decoded.sourceName());
        assertEquals("hello bedrock", decoded.message());
    }

    @Test
    void textDecodeJapanese() {
        byte[] encoded = BedrockPackets.encodeTextChat("ああ", "こんにちは");
        BedrockPackets.TextMessage decoded = BedrockPackets.decodeText(encoded);
        assertEquals("ああ", decoded.sourceName());
        assertEquals("こんにちは", decoded.message());
    }

    @Test
    void movePlayerRoundTrip() {
        byte[] encoded = BedrockPackets.encodeMovePlayerTeleport(1L, 10.5f, 64.0f, -3.25f,
                12.0f, 90.0f);
        BedrockPackets.MovePlayer decoded = BedrockPackets.decodeMovePlayer(encoded);
        assertEquals(1L, decoded.runtimeId());
        assertEquals(10.5f, decoded.x());
        assertEquals(64.0f, decoded.y());
        assertEquals(-3.25f, decoded.z());
        assertEquals(12.0f, decoded.pitch());
        assertEquals(90.0f, decoded.yaw());
        assertEquals(2, decoded.mode());
        assertTrue(decoded.onGround());
    }

    @Test
    void playerAuthInputReadsPosition() {
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        out.writeLittleFloat(5.0f); // pitch
        out.writeLittleFloat(180.0f); // yaw
        out.writeLittleFloat(100.0f); // x
        out.writeLittleFloat(65.0f); // y
        out.writeLittleFloat(-50.0f); // z
        BedrockPackets.PlayerAuthInput input =
                BedrockPackets.decodePlayerAuthInput(out.bytes());
        assertEquals(5.0f, input.pitch());
        assertEquals(180.0f, input.yaw());
        assertEquals(100.0f, input.x());
        assertEquals(65.0f, input.y());
        assertEquals(-50.0f, input.z());
    }

    @Test
    void playStatusEncoding() {
        byte[] loginSuccess = BedrockPackets.encodePlayStatus(0);
        assertEquals(4, loginSuccess.length);
        assertEquals(0, new ByteReader(loginSuccess).readBigInt());
        byte[] spawn = BedrockPackets.encodePlayStatus(3);
        assertEquals(3, new ByteReader(spawn).readBigInt());
    }

    @Test
    void startGameFieldOrderMatchesSpec() {
        // minecraft-data Bedrock 1.20.40 の項目順どおりに読めること。
        byte[] encoded = BedrockPackets.encodeStartGame(
                new BedrockPackets.StartGameInfo(7, 8.5f, 64.0f, 8.5f,
                        "DetergentMC", "1.20.50"));
        ByteReader in = new ByteReader(encoded);
        assertEquals(7, BedrockVarInt.readZigzag64(in)); // entity id
        assertEquals(7, BedrockVarInt.readUnsignedVarInt(in)); // runtime entity id
        assertEquals(0, BedrockVarInt.readZigzag32(in)); // gamemode: survival
        assertEquals(8.5f, in.readLittleFloat()); // spawn x
        assertEquals(64.0f, in.readLittleFloat()); // spawn y
        assertEquals(8.5f, in.readLittleFloat()); // spawn z
        assertEquals(0f, in.readLittleFloat()); // pitch
        assertEquals(0f, in.readLittleFloat()); // yaw
        assertEquals(0L, in.readLittleLong()); // seed
        assertEquals(1, in.readLittleShort()); // biome type: plains
        assertEquals("minecraft:plains", BedrockVarInt.readString(in)); // biome name
        assertEquals(0, BedrockVarInt.readZigzag32(in)); // dimension: overworld
        assertEquals(1, BedrockVarInt.readZigzag32(in)); // generator: infinite
        assertEquals(0, BedrockVarInt.readZigzag32(in)); // world gamemode
        assertEquals(2, BedrockVarInt.readZigzag32(in)); // difficulty: normal
        // spawn position（BlockCoordinates：x zigzag32、y 符号なし VarInt、z zigzag32）
        assertEquals(0, BedrockVarInt.readZigzag32(in));
        assertEquals(64, BedrockVarInt.readUnsignedVarInt(in));
        assertEquals(0, BedrockVarInt.readZigzag32(in));
        assertEquals(false, in.readBoolean()); // achievements disabled
        assertEquals(0, BedrockVarInt.readZigzag32(in)); // editor world type
        assertEquals(false, in.readBoolean()); // created in editor
        assertEquals(false, in.readBoolean()); // exported from editor
        assertEquals(0, BedrockVarInt.readZigzag32(in)); // day cycle stop time
        assertEquals(0, BedrockVarInt.readZigzag32(in)); // edu offer
        assertEquals(false, in.readBoolean()); // edu features enabled
        assertEquals("", BedrockVarInt.readString(in)); // edu product uuid
        assertEquals(0f, in.readLittleFloat()); // rain
        assertEquals(0f, in.readLittleFloat()); // lightning
        assertEquals(false, in.readBoolean()); // confirmed platform locked content
        assertEquals(true, in.readBoolean()); // is multiplayer
        assertEquals(true, in.readBoolean()); // broadcast to lan
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // xbox broadcast mode
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // platform broadcast mode
        assertEquals(true, in.readBoolean()); // enable commands
        assertEquals(false, in.readBoolean()); // texturepacks required
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // gamerules: なし
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // experiments: なし
        assertEquals(false, in.readBoolean()); // experiments previously used
        assertEquals(false, in.readBoolean()); // bonus chest
        assertEquals(false, in.readBoolean()); // map enabled
        assertEquals(1, in.readUnsignedByte()); // permission level (u8): member
        assertEquals(4, in.readLittleInt()); // server chunk tick range
        // 末尾まで読めること（残りの bool／文字列群はここでは中身を見ない）。
        assertTrue(in.remaining() > 0);
        // game version・world name が含まれていること。
        String rest = new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(rest.contains("1.20.50"), rest);
        assertTrue(rest.contains("DetergentMC"), rest);
        // multiplayer correlation id はランダムな UUID 文字列が入っていること。
        assertTrue(rest.matches("(?s).*"
                + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*"),
                "multiplayer correlation id が UUID 形式であること");
    }

    @Test
    void resourcePackPacketsAreEmpty() {
        byte[] info = BedrockPackets.encodeResourcePacksInfo();
        assertEquals((byte) 0, info[0]); // must accept: false
        byte[] stack = BedrockPackets.encodeResourcePackStack();
        assertEquals((byte) 0, stack[0]); // must accept: false
        assertTrue(new String(stack, java.nio.charset.StandardCharsets.UTF_8)
                .contains("1.20.50"));
    }

    @Test
    void disconnectEncoding() {
        byte[] encoded = BedrockPackets.encodeDisconnect("Bye");
        ByteReader in = new ByteReader(encoded);
        assertEquals(0, BedrockVarInt.readVarInt(in));
        assertEquals(false, in.readBoolean());
        assertEquals("Bye", BedrockVarInt.readString(in));
        assertEquals(0, in.remaining());
    }

    @Test
    void uuidUnusedHere() {
        // UUID 形式の確認（StartGame の correlation id で使う）。
        assertEquals(36, UUID.randomUUID().toString().length());
    }

    @Test
    void startGameWithPaletteContainsAllEntries() throws Exception {
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] encoded = BedrockPackets.encodeStartGame(
                new BedrockPackets.StartGameInfo(7, 8.5f, 64.0f, 8.5f,
                        "DetergentMC", "1.20.50"),
                palette);
        // パレット全体（約 2MB）が入っていること。
        assertTrue(encoded.length > 1_900_000, "パレット付き StartGame は約 2MB: " + encoded.length);
        String asText = new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(asText.contains("minecraft:cyan_terracotta"), "先頭のエントリが入っていること");
        assertTrue(asText.contains("minecraft:air"), "空気のエントリが入っていること");
        assertTrue(asText.contains(palette.nameAt(palette.count() - 1)), "末尾のエントリが入っていること");

        // パレット無しの StartGame は小さい。
        byte[] empty = BedrockPackets.encodeStartGame(
                new BedrockPackets.StartGameInfo(7, 8.5f, 64.0f, 8.5f,
                        "DetergentMC", "1.20.50"));
        assertTrue(empty.length < 10_000, "パレット無しは小さい: " + empty.length);
    }

    @Test
    void levelChunkEncoding() {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        byte[] encoded = BedrockPackets.encodeLevelChunk(-3, 7, 24, payload);
        ByteReader in = new ByteReader(encoded);
        assertEquals(-3, BedrockVarInt.readZigzag32(in));
        assertEquals(7, BedrockVarInt.readZigzag32(in));
        assertEquals(24, BedrockVarInt.readUnsignedVarInt(in));
        assertEquals(false, in.readBoolean()); // cache disabled
        assertEquals(5, BedrockVarInt.readUnsignedVarInt(in)); // ByteArray 長
        for (byte b : payload) {
            assertEquals(b, in.readUnsignedByte());
        }
        assertEquals(0, in.remaining());
    }

    @Test
    void levelChunk2225HasClientBiomeUpdate() {
        // 2225 の LevelChunk は末尾に client_biome_update（bool）が付く
        // （2225 の protocol.json で実測。BE-NOTES.md §24）。2193 形より
        // 1 バイト長く、末尾は false。
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        byte[] encoded2193 = BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                BedrockVersion.V1_26_52);
        byte[] encoded2225 = BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                BedrockVersion.V1_26_60);
        assertEquals(encoded2193.length + 1, encoded2225.length);
        assertEquals(0, encoded2225[encoded2225.length - 1],
                "client_biome_update は false");
        // 旧 4 引数版は従来通り（末尾フィールドなし＝2193 形と同じ長さ）。
        assertEquals(encoded2193.length,
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload).length);
    }

    @Test
    void chunkRadiusPackets() {
        BedrockVarInt.ByteWriter request = new BedrockVarInt.ByteWriter();
        BedrockVarInt.writeZigzag32(request, 6);
        request.writeByte(8); // max radius
        BedrockPackets.ChunkRadiusRequest decoded =
                BedrockPackets.decodeRequestChunkRadius(request.bytes());
        assertEquals(6, decoded.radius());
        assertEquals(8, decoded.maxRadius());

        byte[] updated = BedrockPackets.encodeChunkRadiusUpdated(6);
        assertEquals(6, BedrockVarInt.readZigzag32(new ByteReader(updated)));

        byte[] publisher = BedrockPackets.encodeNetworkChunkPublisherUpdate(16, 64, -32, 96);
        ByteReader in = new ByteReader(publisher);
        assertEquals(16, BedrockVarInt.readZigzag32(in)); // block x
        assertEquals(64, BedrockVarInt.readUnsignedVarInt(in)); // block y
        assertEquals(-32, BedrockVarInt.readZigzag32(in)); // block z
        assertEquals(96, BedrockVarInt.readUnsignedVarInt(in)); // radius（ブロック単位）
        assertEquals(0, in.readLittleInt()); // saved chunks: なし
        assertEquals(0, in.remaining());
    }

    @Test
    void updateBlockEncoding() {
        byte[] encoded = BedrockPackets.encodeUpdateBlock(10, 65, -4, 12183, 0);
        ByteReader in = new ByteReader(encoded);
        assertEquals(10, BedrockVarInt.readZigzag32(in)); // x
        assertEquals(65, BedrockVarInt.readUnsignedVarInt(in)); // y
        assertEquals(-4, BedrockVarInt.readZigzag32(in)); // z
        assertEquals(12183, BedrockVarInt.readUnsignedVarInt(in)); // runtime id
        assertEquals(BedrockProtocol.UPDATE_BLOCK_FLAG_NETWORK,
                BedrockVarInt.readUnsignedVarInt(in)); // flags
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // layer
        assertEquals(0, in.remaining());

        byte[] water = BedrockPackets.encodeUpdateBlock(10, 65, -4, 9304, 1);
        ByteReader waterIn = new ByteReader(water);
        BedrockVarInt.readZigzag32(waterIn);
        BedrockVarInt.readUnsignedVarInt(waterIn);
        BedrockVarInt.readZigzag32(waterIn);
        assertEquals(9304, BedrockVarInt.readUnsignedVarInt(waterIn));
        BedrockVarInt.readUnsignedVarInt(waterIn);
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(waterIn));
    }
}
