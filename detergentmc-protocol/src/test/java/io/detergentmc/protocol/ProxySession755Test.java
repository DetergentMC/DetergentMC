// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 755（1.17）の ProxySession 配線テスト。PlayTranslator／ChunkTranslator の
 * 755⇔756変換器（Join Game／Respawn／Client Information／Window Click／
 * Set Slot／Window Items／Entity Destroy／Chunk Data 等）を ProxySession の755ハンドラから呼ぶことを、
 * セッション経由で確かめる。{@link ProxySession756Test} と同じ段取り
 * （ハンドシェイク→ログイン→プレイ。755／756とも設定フェーズは無い）。
 * Join Game 0x26⇔0x26、Respawn 0x3D⇔0x3D、chat 0x03⇔chat_message 0x04／chat_command 0x03、
 * Block Dig 0x1A⇔0x1C、Window Click 0x08⇔0x0A、Enchant Item 0x07⇔0x09、
 * Client Information 0x05⇔0x07。
 * 注意：サーバー→クライアントは755サーバー→756／757／759以上クライアント方向の
 * 汎用パケット（Set Slot／Window Items／chat 等）が handleServerPlayFrom763 の汎用経路で
 * 落とされる（763→756の Join Game／Respawn／汎用パケット変換が未実装のため。
 * 756の ProxySession756Test と同じ非対称性）。その落としもこのテストで確かめる。
 * 755⇔756でフィールド構成が違う4件（Set Slot／Window Items／Window Click／Edit Book）のうち、
 * Edit Book だけは変換器がどこにも無く、755⇔756の直接変換でも763→755でも落とす。
 * Window Click の mode は両バージョンとも varint（minecraft-data の「1.17でi8」は誤りで、
 * javap -c により確認済み）。stateId の補完値は ViaVersion と同じ 0。
 * 763〜775→755のJoin Game／Respawnは落とすままになる見込み（756の§14.9と同じ残課題）。
 * Entity Destroy の756→755は count 0／2以上のとき変換器が null を返す
 * （755の1パケットでは表せないため。分割送信にはProxySession側の対応が要る）が、
 * ProxySession には756サーバー→755クライアントの直接の呼び出し口が無いため、
 * ここでは変換器の null 契約だけ確かめる。
 */
class ProxySession755Test {

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

    /** 755の Login Start：名前だけ。 */
    private static byte[] loginStart755(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
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

    /** 755／756／757／759／774の Login Success（UUID＋名前＋プロパティ0件）。 */
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

    /** 755／756の dimension の値（最小：effects＋has_ceiling で overworld と読めるもの）。 */
    private static Tag.CompoundTag overworldDimensionValue() {
        return new Tag.CompoundTag(Map.of(
                "effects", new Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Tag.ByteTag((byte) 0)));
    }

    /** 755の Join Game（756と同一。simulationDistance 無し）。 */
    private static byte[] joinGame755Payload() {
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

    /** 755の Respawn（756と同一。copyMetadata=true）。 */
    private static byte[] respawn755Payload() {
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

    /** 755／756の chat（サーバー→クライアント）。{message, position, sender}。 */
    private static byte[] chat755Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeByte(0); // position chat
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 755／756の chat（クライアント→サーバー）。{message}。 */
    private static byte[] chatToServer755Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 755／756の Block Dig。{status, location, face}。 */
    private static byte[] blockDig755Payload() {
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

    /** 755の Client Information。{locale, viewDistance i8, chatFlags, chatColors, skinParts, mainHand, disableTextFiltering}。 */
    private static byte[] clientInformation755Payload() {
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

    /** 755の Window Click（stateId 無し。石1個のスロット変更＋空のカーソル）。 */
    private static byte[] windowClick755Payload() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット1件
        writer.writeShort(5); // slotNo
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（755:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        writer.writeBoolean(false); // カーソルは空
        return writer.bytes();
    }

    /** 755の Edit Book。{new_book slot, signing bool, hand varint}。 */
    private static byte[] editBook755Payload() {
        Writer writer = new Writer();
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（本の代わりのスロット内容。形式確認用）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        writer.writeBoolean(true); // signing
        writer.writeVarInt(0); // hand main hand
        return writer.bytes();
    }

    /** 755の Set Slot。{windowId i8, slot i16, item slot}（stateId 無し）。 */
    private static byte[] setSlot755Payload() {
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeShort(5); // slot
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（755:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 755の Window Items。{windowId u8, count i16, items}（stateId・carriedItem 無し）。 */
    private static byte[] windowItems755Payload() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(1); // count i16
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（755:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 755の Entity Destroy。[entityId varint] 1体。 */
    private static byte[] entityDestroy755Payload(int entityId) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        return writer.bytes();
    }

    /** 756の Entity Destroy。[count varint][entityIds]。 */
    private static byte[] entityDestroy756Payload(int... entityIds) {
        Writer writer = new Writer();
        writer.writeVarInt(entityIds.length);
        for (int id : entityIds) {
            writer.writeVarInt(id);
        }
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

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        if (clientProtocol == 755) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart755("Steve")));
        } else if (clientProtocol == 756) {
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
    // 755クライアント＋755サーバー（ネイティブ）
    // ------------------------------------------------------------------

    @Test
    void join755Against755IsNative() {
        ProxySession session = loginTo(755, 755);
        loginSuccess(session);

        // Join Game（0x26）はそのまま。
        byte[] joinPayload = joinGame755Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        assertArrayEquals(joinPayload, join.toClient().get(0).payload());

        // Respawn（0x3D）はそのまま。
        byte[] respawnPayload = respawn755Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(respawnPayload, respawn.toClient().get(0).payload());

        // chat（0x03）はそのまま。
        byte[] chatPayload = chatToServer755Payload("hello");
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x03, chatPayload));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x03, chat.toServer().get(0).id());
        assertArrayEquals(chatPayload, chat.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 755サーバー＋756クライアント（755→756方向。Join Game／Respawn は通る）
    // ------------------------------------------------------------------

    @Test
    void joinAndRespawn755Against756Convert() {
        ProxySession session = loginTo(756, 755);
        loginSuccess(session);

        // Join Game（755：0x26）→756（0x26）。変換器の出力と一致する。
        byte[] joinPayload = joinGame755Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame755To756(joinPayload),
                join.toClient().get(0).payload());
        PlayTranslator.JoinGame756 parsedJoin =
                PlayTranslator.parseJoinGame756(join.toClient().get(0).payload());
        assertEquals("minecraft:overworld", parsedJoin.worldName());
        assertEquals(20, parsedJoin.maxPlayers());
        assertEquals(10, parsedJoin.viewDistance());

        // Respawn（755：0x3D）→756（0x3D）。変換器の出力と一致する。
        byte[] respawnPayload = respawn755Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn755To756(respawnPayload),
                respawn.toClient().get(0).payload());
        PlayTranslator.SpawnInfo756 parsedRespawn =
                PlayTranslator.readSpawnInfo756(new Reader(respawn.toClient().get(0).payload()));
        assertEquals("minecraft:overworld", parsedRespawn.worldName());
        assertTrue(parsedRespawn.copyMetadata());

        // chat（755：0x0F）は756クライアントには落とす
        // （handleServerPlayFrom763 の汎用経路の落としリストに756がある。763→756の汎用変換が未実装のため）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x0F, chat755Payload("{\"text\":\"hi\"}")));
        assertEquals(0, chat.toClient().size());

        // Set Slot（755：0x16）→756（0x16）。stateId 0 が補われる。
        // セッション経由では handleServerPlayFrom763 の汎用経路で落とされるため、
        // ここでは ProxySession が呼ぶ変換器の出力だけ確かめる。
        byte[] setSlotConverted =
                ChunkTranslator.translateSetSlot755To756(setSlot755Payload());
        Reader setSlotReader = new Reader(setSlotConverted);
        assertEquals(0, setSlotReader.readByte()); // windowId
        assertEquals(0, setSlotReader.readVarInt()); // stateId（補完値は 0）
        assertEquals(5, setSlotReader.readShort()); // slot
        assertTrue(setSlotReader.readBoolean()); // スロットあり
        assertEquals(1, setSlotReader.readVarInt()); // stone
        assertEquals(1, setSlotReader.readByte()); // count
        assertEquals(0, setSlotReader.readByte()); // NBT無し
        assertEquals(0, setSlotReader.remaining());
        Outcome setSlot = session.handleServerPacket(
                new MinecraftPacket(0x16, setSlot755Payload()));
        assertEquals(0, setSlot.toClient().size());

        // Window Items（755：0x14）→756（0x14）。stateId 0 と carriedItem が補われる。
        byte[] windowItemsConverted =
                ChunkTranslator.translateWindowItems755To756(windowItems755Payload());
        Reader windowItemsReader = new Reader(windowItemsConverted);
        assertEquals(0, windowItemsReader.readUnsignedByte()); // windowId
        assertEquals(0, windowItemsReader.readVarInt()); // stateId（補完値は 0）
        assertEquals(1, windowItemsReader.readVarInt()); // count（i16→varint）
        assertTrue(windowItemsReader.readBoolean()); // スロットあり
        assertEquals(1, windowItemsReader.readVarInt()); // stone
        assertEquals(1, windowItemsReader.readByte()); // count
        assertEquals(0, windowItemsReader.readByte()); // NBT無し
        assertFalse(windowItemsReader.readBoolean()); // carriedItem 無し
        assertEquals(0, windowItemsReader.remaining());
        Outcome windowItems = session.handleServerPacket(
                new MinecraftPacket(0x14, windowItems755Payload()));
        assertEquals(0, windowItems.toClient().size());

        // Entity Destroy（755：[42]）→756（[count=1][42]）。
        assertArrayEquals(new byte[]{0x01, 0x2A},
                ChunkTranslator.translateEntityDestroy755To756(entityDestroy755Payload(42)));
        Outcome entityDestroy = session.handleServerPacket(
                new MinecraftPacket(0x3A, entityDestroy755Payload(42)));
        assertEquals(0, entityDestroy.toClient().size());
    }

    // ------------------------------------------------------------------
    // 774クライアント＋755サーバー（二段変換。ログイン合成あり）
    // ------------------------------------------------------------------

    @Test
    void join774Against755ConvertsJoinGameButDropsRespawn() {
        ProxySession session = loginTo(774, 755);
        // Login Success（0x02）：サーバーはプレイ状態、クライアントは LOGIN のまま（合成設定フェーズ）。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.LOGIN, session.clientState());

        // Login Acknowledged（0x03）：プロキシが消費する（755サーバーへは送らない）。
        // クライアントは設定フェーズに入る。Join Game はまだ無いため何も送られない。
        Outcome ack = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.CONFIGURATION, session.clientState());
        assertEquals(0, ack.toServer().size());
        assertEquals(0, ack.toClient().size());

        // Join Game（755：0x26）：合成設定フェーズで保持され、Registry Data＋Finish Configuration が送られる。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame755Payload()));
        assertTrue(join.toClient().size() >= 2);
        assertEquals(0x07, join.toClient().get(0).id()); // Registry Data
        assertEquals(0x03, join.toClient().get(join.toClient().size() - 1).id()); // Finish Configuration

        // Finish Configuration の応答（0x03）：保持した Join Game（774：0x30）が送られ、プレイ状態に入る。
        Outcome finish = session.handleClientPacket(new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(1, finish.toClient().size());
        assertEquals(0x30, finish.toClient().get(0).id());

        // Respawn（755：0x3D）は774クライアントには落とす
        // （handleServerPlayFrom763 の Respawn 分岐の落としリストに774がある）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x3D, respawn755Payload()));
        assertEquals(0, respawn.toClient().size());

        // Chat Message（774：0x08、checksum 付き）→chat（755：0x03）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x08, chatMessage774Payload("hi")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hi", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（774：0x28）→755（0x1A）。sequence が剥がれる。
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
    // 755クライアント＋765／764サーバー（&&／|| の優先順位の回帰）
    // ------------------------------------------------------------------

    /**
     * 755 Stage B統合時の修正の回帰テスト。handleServerPlayUp／
     * handleServerPlay765From764 の Respawn／Disconnect ガードは
     * {@code name == PLAY_RESPAWN && is757() || is756() || is755()} と
     * 書かれていた。{@code &&} は {@code ||} より強いため、756／755クライアントでは
     * Respawn 以外の全パケットが落とされ（765側）、764側では全パケットが
     * Disconnect 扱いで切断されていた。括弧で囲み Respawn／Disconnect だけを
     * 落とす／切断するようにした。Keep Alive が通ることと、Respawn が
     * 落ちることを確かめる。
     */
    @Test
    void keepAlive765Against755IsNotDroppedByRespawnGuard() {
        // 765サーバー＋755クライアント：Login Success の後はサーバーが設定フェーズ、
        // クライアントはプレイ状態。プロキシが内部クライアント役として
        // Login Acknowledged＋Client Information をサーバーへ送る。
        // サーバーの Finish Configuration（設定 0x02）でサーバーもプレイ状態になる。
        // Join Game は要らない（Keep Alive の到達だけ確かめるため）。
        ProxySession session = loginTo(755, 765);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(2, success.toServer().size());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Outcome finCfg = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(1, finCfg.toServer().size());

        // Keep Alive（765：0x24）は落とされず、755の 0x21 でクライアントへ届く。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome alive = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, alive.toClient().size());
        assertEquals(0x21, alive.toClient().get(0).id());
        assertArrayEquals(keepAlive.bytes(), alive.toClient().get(0).payload());
        assertFalse(alive.closeAfterSend());

        // Respawn（765：0x45）は落とすまま（ガードはペイロードを見る前に落とす）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, new byte[0]));
        assertEquals(0, respawn.toClient().size());
        assertFalse(respawn.closeAfterSend());
    }

    @Test
    void keepAlive764Against755IsNotClosedByDisconnectGuard() {
        // 764サーバー＋755クライアント：765と同じ段取り。
        ProxySession session = loginTo(755, 764);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(2, success.toServer().size());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        Outcome finCfg = session.handleServerPacket(new MinecraftPacket(0x02, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(1, finCfg.toServer().size());

        // Keep Alive（764：0x24）は Disconnect 扱いで切断されず、755の 0x21 で届く。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome alive = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, alive.toClient().size());
        assertEquals(0x21, alive.toClient().get(0).id());
        assertArrayEquals(keepAlive.bytes(), alive.toClient().get(0).payload());
        assertFalse(alive.closeAfterSend());

        // Respawn（764：0x43）は落とすまま。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x43, new byte[0]));
        assertEquals(0, respawn.toClient().size());
        assertFalse(respawn.closeAfterSend());
    }

    // ------------------------------------------------------------------
    // 755クライアント＋775サーバー（ログイン合成。サーバー側の設定フェーズを消費）
    // ------------------------------------------------------------------

    @Test
    void login755Against775SynthesizesConfig() {
        ProxySession session = loginTo(755, 775);
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
        // 755クライアントに送るものは無い（設定フェーズが無いため）。
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(0, finish.toClient().size());
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());

        // Join Game（775：0x31）は755クライアントには落とす
        // （handleServerPlayFrom775 の Join Game 分岐の落としリストに755がある。
        // 763→755の Join Game 変換が未実装のため。756と同じ扱い）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame755Payload()));
        assertEquals(0, join.toClient().size());
    }

    // ------------------------------------------------------------------
    // 755クライアント＋759サーバー（クライアント→サーバー方向）
    // ------------------------------------------------------------------

    @Test
    void clientPackets755Against759Convert() {
        ProxySession session = loginTo(755, 759);
        loginSuccess(session);

        // chat（755：0x03）→Chat Message（759：0x04）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x03, chatToServer755Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x04, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（755：0x1A）→759（0x1C）。sequence が補われる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1A, blockDig755Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1C, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.readVarInt()); // sequence（756には無いため 0 を補う）
        assertEquals(0, digReader.remaining());

        // Window Click（755：0x08）→759（0x0A）。stateId 0 が補われ、スロットのアイテムIDが付け替わる。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x08, windowClick755Payload()));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0A, click.toServer().get(0).id());
        Reader clickReader = new Reader(click.toServer().get(0).payload());
        assertEquals(0, clickReader.readUnsignedByte()); // windowId
        assertEquals(0, clickReader.readVarInt()); // stateId（755→756で 0 を補完）
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

        // Client Information（755：0x05）→759（0x07）。末尾が757形式になる。
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x05, clientInformation755Payload()));
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

        // Enchant Item（755：0x07、protection=0）→759（0x09、protection=0）。
        Outcome enchant = session.handleClientPacket(
                new MinecraftPacket(0x07, enchantItem759Payload(0)));
        assertEquals(1, enchant.toServer().size());
        assertEquals(0x09, enchant.toServer().get(0).id());
        Reader enchantReader = new Reader(enchant.toServer().get(0).payload());
        assertEquals(0, enchantReader.readByte()); // windowId
        assertEquals(0, enchantReader.readByte()); // protection

        // Edit Book（755：0x0B）は落とす（変換器がどこにも無い）。
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0B, editBook755Payload()));
        assertEquals(0, editBook.toServer().size());
    }

    // ------------------------------------------------------------------
    // 759クライアント＋755サーバー（handleClientPlayTo755）
    // ------------------------------------------------------------------

    @Test
    void clientPackets759Against755Convert() {
        ProxySession session = loginTo(759, 755);
        loginSuccess(session);

        // Chat Message（759：0x04）→chat（755：0x03）。署名は捨てられる。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x04, chatMessage759Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（759：0x1C）→755（0x1A）。sequence が剥がれる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1C, blockDig759Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1A, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.remaining());

        // Enchant Item（759：0x09、protection=0）→755（0x07、protection=0）。
        Outcome enchant = session.handleClientPacket(
                new MinecraftPacket(0x09, enchantItem759Payload(0)));
        assertEquals(1, enchant.toServer().size());
        assertEquals(0x07, enchant.toServer().get(0).id());
        Reader enchantReader = new Reader(enchant.toServer().get(0).payload());
        assertEquals(0, enchantReader.readByte()); // windowId
        assertEquals(0, enchantReader.readByte()); // protection

        // Window Click（759：0x0A）は落とす（translateWindowClick763To755 が無い。
        // 757経由も 763→757の変換器が無いため不可）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0A, new byte[0]));
        assertEquals(0, click.toServer().size());
    }

    // ------------------------------------------------------------------
    // 763クライアント＋755サーバー（Window Click／Set Creative Slot／Edit Book は落とす）
    // ------------------------------------------------------------------

    @Test
    void windowClick763Against755Drops() {
        ProxySession session = loginTo(763, 755);
        loginSuccess(session);

        // Window Click（763：0x0B）は落とす（translateWindowClick763To755 が無い。
        // 757経由も 763→757の変換器が無いため不可）。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x0B, new byte[0]));
        assertEquals(0, click.toServer().size());

        // Set Creative Slot（763：0x2B）も落とす（同上）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x2B, new byte[0]));
        assertEquals(0, creative.toServer().size());

        // Edit Book（763：0x0E）も落とす（755の {new_book, signing, hand} とは
        // 全面的に違う。変換器がどこにも無い）。
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0E, new byte[0]));
        assertEquals(0, editBook.toServer().size());
    }

    // ------------------------------------------------------------------
    // 756サーバー＋755クライアント（Entity Destroy は落とす）
    // ------------------------------------------------------------------

    @Test
    void entityDestroy756Against755Drops() {
        ProxySession session = loginTo(755, 756);
        loginSuccess(session);

        // Entity Destroy（756：[count=1][42]）は755クライアントには落とす
        // （handleServerPlayFrom763 の汎用経路の落としリストに755がある。
        // 763→755の汎用パケット変換が未実装のため）。
        Outcome entityDestroy = session.handleServerPacket(
                new MinecraftPacket(0x3A, entityDestroy756Payload(42)));
        assertEquals(0, entityDestroy.toClient().size());

        // 変換器自体の契約：count 1 だけ単体に直せ、count 0／2以上は null を返す。
        assertArrayEquals(new byte[]{0x2A},
                ChunkTranslator.translateEntityDestroy756To755(entityDestroy756Payload(42)));
        assertNull(ChunkTranslator.translateEntityDestroy756To755(entityDestroy756Payload()));
        assertNull(ChunkTranslator.translateEntityDestroy756To755(entityDestroy756Payload(42, 43)));
    }

    // ------------------------------------------------------------------
    // pack755 のフォールバック（pack756）
    // ------------------------------------------------------------------

    @Test
    void pack755FallsBackToPack756() {
        ProxySession session = loginTo(755, 756);
        loginSuccess(session);

        ResourcePack pack756 = new ResourcePack(
                "https://example.invalid/pack756.zip", "aa".repeat(20), false, "");
        ResourcePack pack755 = new ResourcePack(
                "https://example.invalid/pack755.zip", "bb".repeat(20), false, "");

        // pack755 未設定：pack756 に落とす。
        assertNull(session.pack755());
        session.setPack756(pack756);
        assertEquals(pack756, session.packOverrideForClient());
        assertEquals(pack756, session.packFor763LikeClient());

        // pack755 設定済み：pack755 を出す。
        session.setPack755(pack755);
        assertEquals(pack755, session.pack755());
        assertEquals(pack755, session.packOverrideForClient());
        assertEquals(pack755, session.packFor763LikeClient());

        // どちらも未設定：null（バニラのパックをそのまま出す）。
        ProxySession fresh = loginTo(755, 756);
        loginSuccess(fresh);
        assertNull(fresh.packOverrideForClient());
        assertNull(fresh.packFor763LikeClient());
        assertNull(fresh.pack755());
    }
}
