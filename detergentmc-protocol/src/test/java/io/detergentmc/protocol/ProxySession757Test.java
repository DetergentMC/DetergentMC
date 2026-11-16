// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 757（1.18／1.18.1）の ProxySession 配線テスト。PlayTranslator／ChunkTranslator の
 * 757⇔759変換器（Join Game／Respawn／chat／Spawn Entity／Chunk Data／
 * Block Dig／Enchant Item 等）を ProxySession の757ハンドラから呼ぶことを、
 * セッション経由で確かめる。{@link ProxySession759Test} と同じ段取り
 * （ハンドシェイク→ログイン→プレイ。757／759とも設定フェーズは無い）。
 * Join Game 0x26⇔0x23、Respawn 0x3D⇔0x3B、Chunk Data 0x22⇔0x1F、
 * chat 0x03⇔chat_message 0x04／chat_command 0x03、
 * Spawn Entity Living 0x02／Painting 0x03→Spawn Entity 0x00、
 * System Chat 0x5F→chat 0x0F。
 * 注意：サーバー→クライアントは757サーバー→759クライアント方向だけが配線済みで、
 * 759サーバー→757クライアント方向は handleServerPlayFrom763 の汎用経路で
 * 落とされる（763→757の Join Game／Respawn／汎用パケット変換が未実装のため）。
 * その非対称性もこのテストで確かめる。
 */
class ProxySession757Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static ProxySession.HandshakeResult handshake(
            int clientProtocol, int nextState, boolean onlineMode,
            ProxyAuthConfig authConfig, int serverProtocol) {
        return ProxySession.resolveHandshake(
                handshakePayload(clientProtocol, nextState), onlineMode,
                null, null, authConfig, null, serverProtocol);
    }

    private static byte[] handshakePayload(int protocol, int nextState) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    /** 757の Login Start：名前だけ。 */
    private static byte[] loginStart757(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 759の Login Start：名前＋署名データ option のみ（UUID は無い）。 */
    private static byte[] loginStart759(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
        return writer.bytes();
    }

    /** 774の Login Start：名前＋UUID（766以降の形式）。 */
    private static byte[] loginStart774(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 757／759／774の Login Success（UUID＋名前＋プロパティ0件）。 */
    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 最小の dimensionCodec（dimension_type 1件＋biome 1件）。 */
    private static Tag.CompoundTag minimalCodec() {
        Map<String, Tag> root = new java.util.LinkedHashMap<>();
        root.put("minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:dimension_type"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of(
                                "name", new Tag.StringTag("minecraft:overworld"),
                                "id", new Tag.IntTag(0),
                                "element", new Tag.CompoundTag(Map.of()))))))));
        root.put("minecraft:worldgen/biome", new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:worldgen/biome"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of(
                                "name", new Tag.StringTag("minecraft:plains"),
                                "id", new Tag.IntTag(0),
                                "element", new Tag.CompoundTag(Map.of()))))))));
        return new Tag.CompoundTag(root);
    }

    /** 757の dimension の値（最小：effects＋has_ceiling で overworld と読めるもの）。 */
    private static Tag.CompoundTag overworldDimensionValue() {
        return new Tag.CompoundTag(Map.of(
                "effects", new Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Tag.ByteTag((byte) 0)));
    }

    /** 757の Join Game（death 無し）。 */
    private static byte[] joinGame757Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        writer.writeByte(0); // gameMode survival
        writer.writeByte(-1); // previousGameMode なし
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", minimalCodec());
        writer.writeNamedNbt("", overworldDimensionValue());
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 759の Join Game（death 無し。portalCooldown 無し）。 */
    private static byte[] joinGame759Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        writer.writeByte(0);
        writer.writeByte(-1);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", minimalCodec());
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false); // hasDeath
        return writer.bytes();
    }

    /** 757の Respawn（death 無し、copyMetadata=true）。末尾は copyMetadata（1.18の packet_respawn で確認）。 */
    private static byte[] respawn757Payload() {
        Writer writer = new Writer();
        writer.writeNamedNbt("", overworldDimensionValue());
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true); // copyMetadata
        return writer.bytes();
    }

    /** 759の Respawn（death 無し、copyMetadata=true）。copyMetadata は isFlat の後・death の前（1.19の packet_respawn で確認）。 */
    private static byte[] respawn759Payload() {
        Writer writer = new Writer();
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(true); // copyMetadata
        writer.writeBoolean(false); // hasDeath
        return writer.bytes();
    }

    /** 757の chat（サーバー→クライアント）。{message, position, sender}。 */
    private static byte[] chat757Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeByte(0); // position chat
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 759の System Chat（サーバー→クライアント）。{content, type varint}。 */
    private static byte[] systemChat759Payload(String content) {
        Writer writer = new Writer();
        writer.writeString(content);
        writer.writeVarInt(0); // type chat
        return writer.bytes();
    }

    /** 757の chat（クライアント→サーバー）。{message}。 */
    private static byte[] chatToServer757Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 759の Chat Message（クライアント→サーバー）。署名なし。 */
    private static byte[] chatMessage759Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(123456789L);
        writer.writeLong(0L);
        writer.writeBoolean(false); // 署名なし
        writer.writeVarInt(0); // offset
        writer.writeBytes(new byte[3]); // acknowledged
        return writer.bytes();
    }

    /** 759の Chat Command（クライアント→サーバー）。{command, timestamp, salt, 署名, messageCount, acknowledged}。 */
    private static byte[] chatCommand759Payload(String command) {
        Writer writer = new Writer();
        writer.writeString(command);
        writer.writeLong(123456789L);
        writer.writeLong(0L);
        writer.writeVarInt(0); // argumentSignatures 0件
        writer.writeVarInt(0); // messageCount
        writer.writeBytes(new byte[3]); // acknowledged
        return writer.bytes();
    }

    /** 774の Chat Message（クライアント→サーバー）。署名なし、checksum 付き。 */
    private static byte[] chatMessage774Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(123456789L);
        writer.writeLong(0L);
        writer.writeBoolean(false); // 署名なし
        writer.writeVarInt(0); // offset
        writer.writeBytes(new byte[3]); // acknowledged
        writer.writeByte(0); // checksum
        return writer.bytes();
    }

    /** 757の Block Dig。{status, location, face}。 */
    private static byte[] blockDig757Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // status start
        writer.writeLong(0L); // location (0, 0, 0)
        writer.writeByte(1); // face up
        return writer.bytes();
    }

    /** 759の Block Dig。{status, location, face, sequence}。 */
    private static byte[] blockDig759Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(0);
        writer.writeLong(0L);
        writer.writeByte(1);
        writer.writeVarInt(5); // sequence
        return writer.bytes();
    }

    /** 774の Block Dig。{status, location, face, sequence}（763形式と同一）。 */
    private static byte[] blockDig774Payload() {
        return blockDig759Payload();
    }

    /** 774の Enchant Item。{windowId varint, enchantment varint}。 */
    private static byte[] enchantItem774Payload(int enchantment) {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(enchantment);
        return writer.bytes();
    }

    /** 757の Spawn Entity Living（zombie）。{entityId, uuid, type, x, y, z, yaw, pitch, headPitch, velocity}。 */
    private static byte[] spawnEntityLiving757Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeUuid(PLAYER_UUID);
        writer.writeVarInt(107); // zombie（757:107。registries-1.18.json で確認）
        writer.writeDouble(10.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-3.5);
        writer.writeByte(0); // yaw
        writer.writeByte(0); // pitch
        writer.writeByte(0); // headPitch
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    /** 759の Spawn Entity。{entityId, uuid, type, x, y, z, pitch, yaw, headPitch, objectData, velocity}。 */
    private static byte[] spawnEntity759Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeUuid(PLAYER_UUID);
        writer.writeVarInt(107); // zombie（759の種別は変換器の表で付け替える。ここでは落とすことだけ確かめる）
        writer.writeDouble(10.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-3.5);
        writer.writeByte(0); // pitch
        writer.writeByte(0); // yaw
        writer.writeByte(0); // headPitch
        writer.writeVarInt(0); // objectData
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    /** 757の Chunk Data（セクション1個、ブロックエンティティ0件、trustEdges=true）。 */
    private static byte[] chunkData757Payload() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeNamedNbt("", new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37]))));
        Writer section = new Writer();
        section.writeShort(1); // ブロック数
        section.writeUnsignedByte(4); // BPE（間接）
        section.writeVarInt(2);
        section.writeVarInt(0); // air
        section.writeVarInt(1); // stone
        section.writeVarInt(256);
        for (int i = 0; i < 256; i++) {
            section.writeLong(0L);
        }
        section.writeUnsignedByte(2); // バイオーム BPE（間接）
        section.writeVarInt(1);
        section.writeVarInt(1); // plains
        section.writeVarInt(2);
        for (int i = 0; i < 2; i++) {
            section.writeLong(0L);
        }
        payload.writeByteArray(section.bytes());
        payload.writeVarInt(0); // ブロックエンティティは0件
        payload.writeBoolean(true); // trustEdges
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ
        return payload.bytes();
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        if (clientProtocol == 757) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart757("Steve")));
        } else if (clientProtocol == 759) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart759("Steve")));
        } else {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart774("Steve")));
        }
        return session;
    }

    /** ログイン成功まで進め、プレイ状態であることを確かめる。 */
    private static void loginSuccess(ProxySession session) {
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
    }

    // ------------------------------------------------------------------
    // 759クライアント＋757サーバー（757→759方向。配線済み）
    // ------------------------------------------------------------------

    @Test
    void join759Against757ConvertsJoinGameRespawnChatSpawnAndChunk() {
        ProxySession session = loginTo(759, 757);
        loginSuccess(session);

        // Join Game（757：0x26）→759（0x23）。dimension は名前になる。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame757Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id());
        PlayTranslator.JoinGame759 parsedJoin =
                PlayTranslator.parseJoinGame759(join.toClient().get(0).payload());
        assertEquals("minecraft:overworld", parsedJoin.dimension());
        assertEquals("minecraft:overworld", parsedJoin.worldName());
        assertEquals(20, parsedJoin.maxPlayers());
        assertFalse(parsedJoin.hasDeath());

        // Respawn（757：0x3D）→759（0x3B）。copyMetadata=true が写る。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawn757Payload()));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3B, respawn.toClient().get(0).id());
        PlayTranslator.SpawnInfo759 parsedRespawn =
                PlayTranslator.readSpawnInfo759(new Reader(respawn.toClient().get(0).payload()));
        assertEquals("minecraft:overworld", parsedRespawn.dimension());
        assertEquals("minecraft:overworld", parsedRespawn.worldName());
        assertTrue(parsedRespawn.copyMetadata());
        assertFalse(parsedRespawn.hasDeath());

        // chat（757：0x0F）→System Chat（759：0x5F）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x0F, chat757Payload("{\"text\":\"hi\"}")));
        assertEquals(1, chat.toClient().size());
        assertEquals(0x5F, chat.toClient().get(0).id());
        Reader chatReader = new Reader(chat.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", chatReader.readString());

        // Spawn Entity Living（757：0x02）→Spawn Entity（759：0x00）。
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x02, spawnEntityLiving757Payload()));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x00, spawn.toClient().get(0).id());
        Reader spawnReader = new Reader(spawn.toClient().get(0).payload());
        assertEquals(7, spawnReader.readVarInt());

        // Chunk Data（757：0x22）→759（0x1F）。変換器の出力と一致する。
        byte[] chunkPayload = chunkData757Payload();
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x22, chunkPayload));
        assertEquals(1, chunk.toClient().size());
        assertEquals(0x1F, chunk.toClient().get(0).id());
        assertArrayEquals(ChunkTranslator.translateChunkData757To759(chunkPayload),
                chunk.toClient().get(0).payload());

        // Chat Message（759：0x04）→chat（757：0x03）。署名は捨てられる。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x04, chatMessage759Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Chat Command（759：0x03）→chat（757：0x03）。"/" が付く。
        Outcome commandOut = session.handleClientPacket(
                new MinecraftPacket(0x03, chatCommand759Payload("help")));
        assertEquals(1, commandOut.toServer().size());
        assertEquals(0x03, commandOut.toServer().get(0).id());
        assertEquals("/help", new Reader(commandOut.toServer().get(0).payload()).readString());

        // Block Dig（759：0x1C）→757（0x1A）。sequence が剥がれる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1C, blockDig759Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1A, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.remaining());
    }

    // ------------------------------------------------------------------
    // 757クライアント＋759サーバー（759→757方向。サーバー→クライアントは落とす）
    // ------------------------------------------------------------------

    @Test
    void join757Against759DropsServerPacketsButConvertsClientPackets() {
        ProxySession session = loginTo(757, 759);
        loginSuccess(session);

        // Join Game（759：0x23）は757クライアントには落とす
        // （handleServerJoinGame763 の is757() 分岐。763→757の Join Game は未実装）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGame759Payload()));
        assertEquals(0, join.toClient().size());

        // Respawn（759：0x3B）は757クライアントには落とす
        // （handleServerPlayFrom763 の Respawn 分岐の落としリストに757がある）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3B, respawn759Payload()));
        assertEquals(0, respawn.toClient().size());

        // System Chat（759：0x5F）は757クライアントには落とす
        // （handleServerPlayFrom763 の汎用経路の落としリストに757がある）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x5F, systemChat759Payload("{\"text\":\"hi\"}")));
        assertEquals(0, chat.toClient().size());

        // Chunk Data（759：0x1F）は757クライアントには落とす（同上）。
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x1F, chunkData757Payload()));
        assertEquals(0, chunk.toClient().size());

        // Spawn Entity（759：0x00）は757クライアントには落とす（同上）。
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, spawnEntity759Payload()));
        assertEquals(0, spawn.toClient().size());

        // chat（757クライアント→サーバー 0x03）→Chat Message（759：0x04）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x03, chatToServer757Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x04, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（757：0x1A）→759（0x1C）。sequence が補われる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1A, blockDig757Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1C, dig.toServer().get(0).id());
    }

    // ------------------------------------------------------------------
    // 757クライアント＋757サーバー（ネイティブ）
    // ------------------------------------------------------------------

    @Test
    void join757Against757IsNative() {
        ProxySession session = loginTo(757, 757);
        loginSuccess(session);

        // Join Game（0x26）はそのまま。
        byte[] joinPayload = joinGame757Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        assertArrayEquals(joinPayload, join.toClient().get(0).payload());

        // Respawn（0x3D）はそのまま。
        byte[] respawnPayload = respawn757Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(respawnPayload, respawn.toClient().get(0).payload());

        // chat（0x03）はそのまま。
        byte[] chatPayload = chatToServer757Payload("hello");
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x03, chatPayload));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x03, chat.toServer().get(0).id());
        assertArrayEquals(chatPayload, chat.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 774クライアント＋757サーバー（二段変換）
    // ------------------------------------------------------------------

    @Test
    void join774Against757ConvertsJoinGameButDropsRespawn() {
        ProxySession session = loginTo(774, 757);
        // Login Success（0x02）：サーバーはプレイ状態、クライアントは LOGIN のまま（合成設定フェーズ）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（0x03）：クライアントは設定フェーズに入る。Join Game はまだ無いため何も送られない。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(0, ack.toClient().size());

        // Join Game（757：0x26）：合成設定フェーズで保持され、Registry Data＋Finish Configuration が送られる。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame757Payload()));
        assertTrue(join.toClient().size() >= 2);
        assertEquals(0x07, join.toClient().get(0).id()); // Registry Data
        assertEquals(0x03, join.toClient().get(join.toClient().size() - 1).id()); // Finish Configuration

        // Finish Configuration の応答（0x03）：保持した Join Game（774：0x30）が送られ、プレイ状態に入る。
        Outcome finish = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(1, finish.toClient().size());
        assertEquals(0x30, finish.toClient().get(0).id());

        // Respawn（757：0x3D）は774クライアントには落とす
        // （handleServerPlayFrom763 の Respawn 分岐の落としリストに774がある）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawn757Payload()));
        assertEquals(0, respawn.toClient().size());

        // Chat Message（774：0x08、checksum 付き）→chat（757：0x03）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage774Payload("hi")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hi", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（774：0x28）→757（0x1A）。sequence が剥がれる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x28, blockDig774Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1A, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.remaining());

        // Enchant Item（774：0x10、protection=27）→757（0x07、protection=0）。
        // 774のエンチャント順は1.21.1からのアルファベット順（minecraft-data 1.21.8で確認）。
        Outcome enchant = session.handleClientPacket(
                new MinecraftPacket(0x10, enchantItem774Payload(27)));
        assertEquals(1, enchant.toServer().size());
        assertEquals(0x07, enchant.toServer().get(0).id());
        Reader enchantReader = new Reader(enchant.toServer().get(0).payload());
        assertEquals(0, enchantReader.readByte()); // windowId
        assertEquals(0, enchantReader.readByte()); // protection

        // Enchant Item（swift_sneak=37）は757に無いため落とす。
        Outcome enchantDrop = session.handleClientPacket(
                new MinecraftPacket(0x10, enchantItem774Payload(37)));
        assertEquals(0, enchantDrop.toServer().size());
    }
}
