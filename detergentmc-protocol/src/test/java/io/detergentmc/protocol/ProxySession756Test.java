// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * 756（1.17.1）の ProxySession 配線テスト。PlayTranslator／ChunkTranslator の
 * 756⇔757変換器（Join Game／Respawn／Client Information／Window Click／
 * Chunk Data／Update Light 等）を ProxySession の756ハンドラから呼ぶことを、
 * セッション経由で確かめる。{@link ProxySession757Test} と同じ段取り
 * （ハンドシェイク→ログイン→プレイ。756／757とも設定フェーズは無い）。
 * Join Game 0x26⇔0x26、Respawn 0x3D⇔0x3D、chat 0x03⇔chat_message 0x04／chat_command 0x03、
 * Block Dig 0x1A⇔0x1C、Window Click 0x08⇔0x0A、Enchant Item 0x07⇔0x09、
 * Client Information 0x05⇔0x07。
 * 注意：サーバー→クライアントは756サーバー→757／759以上クライアント方向の
 * 汎用パケット（Chunk Data／chat 等）が handleServerPlayFrom763 の汎用経路で
 * 落とされる（763→757の Join Game／Respawn／汎用パケット変換が未実装のため。
 * 757の ProxySession757Test と同じ非対称性）。その落としもこのテストで確かめる。
 * 光の扱い：757→756の Chunk Data の光抽出は
 * {@link ChunkTranslator#extractUpdateLightFromChunkData757} ができているが、
 * 呼び出し口（763→757のサーバー側変換）が無いため ProxySession には未配線。
 * ここでは抽出器そのものが756形式の Update Light を返すことだけ確かめる。
 */
class ProxySession756Test {

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

    /** 756の Login Start：名前だけ。 */
    private static byte[] loginStart756(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 757の Login Start：名前だけ。 */
    private static byte[] loginStart757(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 763の Login Start：名前＋bool＋UUID。 */
    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true); // UUIDあり
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 759の Login Start：名前＋署名データ option のみ（UUID は無い）。 */
    private static byte[] loginStart759(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
        return writer.bytes();
    }

    /** 774／775の Login Start：名前＋UUID（766以降の形式）。 */
    private static byte[] loginStart774(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 756／757／759／774の Login Success（UUID＋名前＋プロパティ0件）。 */
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

    /** 756／757の dimension の値（最小：effects＋has_ceiling で overworld と読めるもの）。 */
    private static Tag.CompoundTag overworldDimensionValue() {
        return new Tag.CompoundTag(Map.of(
                "effects", new Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Tag.ByteTag((byte) 0)));
    }

    /** 756の Join Game（simulationDistance 無し。757との差分）。 */
    private static byte[] joinGame756Payload() {
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
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 756の Respawn（757と同一。copyMetadata=true）。 */
    private static byte[] respawn756Payload() {
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

    /** 756／757の chat（サーバー→クライアント）。{message, position, sender}。 */
    private static byte[] chat756Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeByte(0); // position chat
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 756／757の chat（クライアント→サーバー）。{message}。 */
    private static byte[] chatToServer756Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 756／757の Block Dig。{status, location, face}。 */
    private static byte[] blockDig756Payload() {
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

    /** 759の Enchant Item。{windowId i8, enchantment i8}。 */
    private static byte[] enchantItem759Payload(int enchantment) {
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeByte(enchantment);
        return writer.bytes();
    }

    /** 756の Client Information。{locale, viewDistance i8, chatFlags, chatColors, skinParts, mainHand, disableTextFiltering}。 */
    private static byte[] clientInformation756Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10); // viewDistance
        writer.writeVarInt(0); // chatFlags
        writer.writeBoolean(true); // chatColors
        writer.writeUnsignedByte(127); // skinParts
        writer.writeVarInt(1); // mainHand left
        writer.writeBoolean(false); // disableTextFiltering=false（フィルタ有効）
        return writer.bytes();
    }

    /** 756の Window Click（石1個のスロット変更＋空のカーソル）。 */
    private static byte[] windowClick756Payload() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeVarInt(1); // stateId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット1件
        writer.writeShort(5); // slotNo
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（756:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        writer.writeBoolean(false); // カーソルは空
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

    /** 774の Block Dig。{status, location, face, sequence}（763形式と同一）。 */
    private static byte[] blockDig774Payload() {
        return blockDig759Payload();
    }

    /** 757の Chunk Data（セクション1個、ブロックエンティティ0件、trustEdges=true、光は空）。 */
    private static byte[] chunkData757PayloadWithEmptyLight() {
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
        // 光セクション（空）：マスク4組＋sky／block配列はいずれも0件。
        for (int i = 0; i < 6; i++) {
            payload.writeVarInt(0);
        }
        return payload.bytes();
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        if (clientProtocol == 756) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart756("Steve")));
        } else if (clientProtocol == 757) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart757("Steve")));
        } else if (clientProtocol == 759) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart759("Steve")));
        } else if (clientProtocol == 763) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart763("Steve")));
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
    // 756クライアント＋756サーバー（ネイティブ）
    // ------------------------------------------------------------------

    @Test
    void join756Against756IsNative() {
        ProxySession session = loginTo(756, 756);
        loginSuccess(session);

        // Join Game（0x26）はそのまま。
        byte[] joinPayload = joinGame756Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        assertArrayEquals(joinPayload, join.toClient().get(0).payload());

        // Respawn（0x3D）はそのまま。
        byte[] respawnPayload = respawn756Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(respawnPayload, respawn.toClient().get(0).payload());

        // chat（0x03）はそのまま。
        byte[] chatPayload = chatToServer756Payload("hello");
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x03, chatPayload));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x03, chat.toServer().get(0).id());
        assertArrayEquals(chatPayload, chat.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 756サーバー＋757クライアント（756→757方向。Join Game／Respawn は通る）
    // ------------------------------------------------------------------

    @Test
    void joinAndRespawn756Against757Convert() {
        ProxySession session = loginTo(757, 756);
        loginSuccess(session);

        // Join Game（756：0x26）→757（0x26）。変換器の出力と一致する。
        byte[] joinPayload = joinGame756Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame756To757(joinPayload),
                join.toClient().get(0).payload());
        PlayTranslator.JoinGame757 parsedJoin =
                PlayTranslator.parseJoinGame757(join.toClient().get(0).payload());
        assertEquals("minecraft:overworld", parsedJoin.worldName());
        assertEquals(20, parsedJoin.maxPlayers());
        assertEquals(10, parsedJoin.viewDistance());
        assertEquals(10, parsedJoin.simulationDistance());

        // Respawn（756：0x3D）→757（0x3D）。変換器の出力と一致する。
        byte[] respawnPayload = respawn756Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn756To757(respawnPayload),
                respawn.toClient().get(0).payload());
        PlayTranslator.SpawnInfo757 parsedRespawn =
                PlayTranslator.readSpawnInfo757(new Reader(respawn.toClient().get(0).payload()));
        assertEquals("minecraft:overworld", parsedRespawn.worldName());
        assertTrue(parsedRespawn.copyMetadata());

        // chat（756：0x0F）は757クライアントには落とす
        // （handleServerPlayFrom763 の汎用経路の落としリストに757がある。763→757の汎用変換が未実装のため）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x0F, chat756Payload("{\"text\":\"hi\"}")));
        assertEquals(0, chat.toClient().size());
    }

    // ------------------------------------------------------------------
    // 774クライアント＋756サーバー（二段変換。ログイン合成あり）
    // ------------------------------------------------------------------

    @Test
    void join774Against756ConvertsJoinGameButDropsRespawn() {
        ProxySession session = loginTo(774, 756);
        // Login Success（0x02）：サーバーはプレイ状態、クライアントは LOGIN のまま（合成設定フェーズ）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（0x03）：プロキシが消費する（756サーバーへは送らない）。
        // クライアントは設定フェーズに入る。Join Game はまだ無いため何も送られない。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(0, ack.toServer().size());
        assertEquals(0, ack.toClient().size());

        // Join Game（756：0x26）：合成設定フェーズで保持され、Registry Data＋Finish Configuration が送られる。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame756Payload()));
        assertTrue(join.toClient().size() >= 2);
        assertEquals(0x07, join.toClient().get(0).id()); // Registry Data
        assertEquals(0x03, join.toClient().get(join.toClient().size() - 1).id()); // Finish Configuration

        // Finish Configuration の応答（0x03）：保持した Join Game（774：0x30）が送られ、プレイ状態に入る。
        Outcome finish = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(1, finish.toClient().size());
        assertEquals(0x30, finish.toClient().get(0).id());

        // Respawn（756：0x3D）は774クライアントには落とす
        // （handleServerPlayFrom763 の Respawn 分岐の落としリストに774がある）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawn756Payload()));
        assertEquals(0, respawn.toClient().size());

        // Chat Message（774：0x08、checksum 付き）→chat（756：0x03）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage774Payload("hi")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hi", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（774：0x28）→756（0x1A）。sequence が剥がれる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x28, blockDig774Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1A, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.remaining());
    }

    // ------------------------------------------------------------------
    // 756クライアント＋775サーバー（ログイン合成。サーバー側の設定フェーズを消費）
    // ------------------------------------------------------------------

    @Test
    void login756Against775SynthesizesConfig() {
        ProxySession session = loginTo(756, 775);
        // Login Success（0x02）：クライアントはプレイ状態、サーバーは設定フェーズ。
        // プロキシが内部クライアント役として Login Acknowledged＋Client Information を送る。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id()); // Login Acknowledged
        assertEquals(0x00, success.toServer().get(1).id()); // Client Information

        // Finish Configuration（775設定：0x03）：サーバーはプレイ状態に入る。
        // 756クライアントに送るものは無い（設定フェーズが無いため）。
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(0, finish.toClient().size());
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());

        // Join Game（775：0x31）は756クライアントには落とす
        // （handleServerPlayFrom775 の Join Game 分岐の落としリストに756がある。
        // 763→756の Join Game 変換が未実装のため。757と同じ扱い）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame756Payload()));
        assertEquals(0, join.toClient().size());
    }

    // ------------------------------------------------------------------
    // 756クライアント＋759サーバー（クライアント→サーバー方向）
    // ------------------------------------------------------------------

    @Test
    void clientPackets756Against759Convert() {
        ProxySession session = loginTo(756, 759);
        loginSuccess(session);

        // chat（756：0x03）→Chat Message（759：0x04）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x03, chatToServer756Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x04, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（756：0x1A）→759（0x1C）。sequence が補われる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1A, blockDig756Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1C, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.readVarInt()); // sequence（757には無いため 0 を補う）
        assertEquals(0, digReader.remaining());

        // Window Click（756：0x08）→759（0x0A）。スロットのアイテムIDが付け替わる。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x08, windowClick756Payload()));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0A, click.toServer().get(0).id());
        Reader clickReader = new Reader(click.toServer().get(0).payload());
        assertEquals(0, clickReader.readUnsignedByte()); // windowId
        assertEquals(1, clickReader.readVarInt()); // stateId
        assertEquals(5, clickReader.readShort()); // slot
        assertEquals(0, clickReader.readByte()); // button
        assertEquals(0, clickReader.readVarInt()); // mode
        assertEquals(1, clickReader.readVarInt()); // 変更スロット1件
        assertEquals(5, clickReader.readShort()); // slotNo
        assertTrue(clickReader.readBoolean()); // スロットあり
        assertEquals(
                RegistryRemapper.mapItem757To759(RegistryRemapper.mapItem756To757(1)),
                clickReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, clickReader.readByte()); // count
        assertFalse(clickReader.readBoolean()); // カーソルは空

        // Client Information（756：0x05）→759（0x07）。末尾が757形式になる。
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x05, clientInformation756Payload()));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x07, settings.toServer().get(0).id());
        Reader settingsReader = new Reader(settings.toServer().get(0).payload());
        assertEquals("en_us", settingsReader.readString());
        assertEquals(10, settingsReader.readByte());
        assertEquals(0, settingsReader.readVarInt());
        assertTrue(settingsReader.readBoolean());
        assertEquals(127, settingsReader.readUnsignedByte());
        assertEquals(1, settingsReader.readVarInt());
        assertTrue(settingsReader.readBoolean()); // enableTextFiltering（反転済み）
        assertTrue(settingsReader.readBoolean()); // enableServerListing（補完）
        assertEquals(0, settingsReader.remaining());

        // Enchant Item（756：0x07、protection=0）→759（0x09、protection=0）。
        Outcome enchant = session.handleClientPacket(
                new MinecraftPacket(0x07, enchantItem759Payload(0)));
        assertEquals(1, enchant.toServer().size());
        assertEquals(0x09, enchant.toServer().get(0).id());
        Reader enchantReader = new Reader(enchant.toServer().get(0).payload());
        assertEquals(0, enchantReader.readByte()); // windowId
        assertEquals(0, enchantReader.readByte()); // protection
    }

    // ------------------------------------------------------------------
    // 759クライアント＋756サーバー（handleClientPlayTo756）
    // ------------------------------------------------------------------

    @Test
    void clientPackets759Against756Convert() {
        ProxySession session = loginTo(759, 756);
        loginSuccess(session);

        // Chat Message（759：0x04）→chat（756：0x03）。署名は捨てられる。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x04, chatMessage759Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（759：0x1C）→756（0x1A）。sequence が剥がれる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1C, blockDig759Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1A, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.remaining());

        // Enchant Item（759：0x09、protection=0）→756（0x07、protection=0）。
        Outcome enchant = session.handleClientPacket(
                new MinecraftPacket(0x09, enchantItem759Payload(0)));
        assertEquals(1, enchant.toServer().size());
        assertEquals(0x07, enchant.toServer().get(0).id());
        Reader enchantReader = new Reader(enchant.toServer().get(0).payload());
        assertEquals(0, enchantReader.readByte()); // windowId
        assertEquals(0, enchantReader.readByte()); // protection
    }

    // ------------------------------------------------------------------
    // 763クライアント＋756サーバー（Window Click／Set Creative Slot は落とす）
    // ------------------------------------------------------------------

    @Test
    void windowClick763Against756Drops() {
        ProxySession session = loginTo(763, 756);
        loginSuccess(session);

        // Window Click（763：0x0B）は落とす（translateWindowClick763To756 が無い。
        // 757経由も 763→757の変換器が無いため不可）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0B, new byte[0]));
        assertEquals(0, click.toServer().size());

        // Set Creative Slot（763：0x2B）も落とす（同上）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x2B, new byte[0]));
        assertEquals(0, creative.toServer().size());
    }

    // ------------------------------------------------------------------
    // 光の抽出器（ChunkTranslator。ProxySession への配線は未実装）
    // ------------------------------------------------------------------

    @Test
    void extractUpdateLightFromChunkData757Produces756Light() {
        byte[] chunk757 = chunkData757PayloadWithEmptyLight();
        byte[] light756 = ChunkTranslator.extractUpdateLightFromChunkData757(chunk757);
        assertNotNull(light756);
        // 756形式の Update Light：{chunkX varint, chunkZ varint, trustEdges, 光セクション}。
        Reader reader = new Reader(light756);
        assertEquals(3, reader.readVarInt());
        assertEquals(-2, reader.readVarInt());
        assertTrue(reader.readBoolean());
        // 光セクションは空（マスク4組＋sky／block配列はいずれも0件）。
        for (int i = 0; i < 6; i++) {
            assertEquals(0, reader.readVarInt());
        }
        assertEquals(0, reader.remaining());
    }
}
