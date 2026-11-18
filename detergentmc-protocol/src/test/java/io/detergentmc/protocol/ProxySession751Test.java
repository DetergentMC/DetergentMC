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
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 751（1.16.2）の ProxySession 配線テスト。ChunkTranslator の
 * 751⇔753変換器（Set Slot／Window Items／Window Click／Entity Destroy／
 * Chunk Data／スロット）を ProxySession の751ハンドラから呼ぶことを、
 * セッション経由で確かめる。{@link ProxySession753Test} と同じ段取り
 * （ハンドシェイク→ログイン→プレイ。751／753とも設定フェーズは無い）。
 * 751⇔753はパケットID・フィールド・レジストリがすべて同一のため、
 * 751⇔753の変換は恒等写像である（Stage A §8.1・§11.1・§11.6）。
 * Join Game 0x24⇔0x24、Respawn 0x39⇔0x39、Spawn Position 0x42⇔0x42、
 * chat 0x03⇔0x03、Block Dig 0x1B⇔0x1B、
 * Window Click 0x09⇔0x09、Set Creative Slot 0x28⇔0x28、
 * Client Information 0x05⇔0x05、Pong 0x07⇔0x07。
 * 注意：サーバー→クライアントは751サーバー→753／754／755／756／757クライアント方向の
 * 汎用パケット（Set Slot／Window Items／chat 等）が handleServerPlayFrom763 の汎用経路で
 * 落とされる（763→754の Join Game／Respawn／汎用パケット変換が未実装のため。
 * 753の ProxySession753Test と同じ非対称性）。その落としもこのテストで確かめる。
 * 751⇔753でフィールド構成が違うものは無い：
 * Set Slot／Window Items／Window Click／Entity Destroy は ChunkTranslator の
 * 751⇔753変換器を使う（担当Cが着地済み。すべて恒等写像）。
 * Edit Book は 751⇔753 で形式が同一のため素通しする（0x0c⇔0x0c）。
 * Ping／Pong は両版とも transaction 形式で同一のため751⇔753の段では素通しするが、
 * 754⇔755の段で落とされるため、751サーバー→755クライアントでは落とす。
 * Update Light は751と753でマスクの形式が同一のため751⇔753の段では素通しするが、
 * 754⇔755の段で落とされるため、751サーバー→755クライアントでは落とす。
 * Chunk Data の変換器自体は ChunkTranslator751Test で確かめる。
 * ここでは751サーバー→759クライアントへの到達（0x20→0x1F）だけ確かめる。
 * 763〜775→751のJoin Game／Respawnは落とすままになる（753の§14.9と同じ残課題）。
 */
class ProxySession751Test {

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

    /** 751の Login Start：名前だけ（753と同じ）。 */
    private static byte[] loginStart751(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 753の Login Start：名前だけ。 */
    private static byte[] loginStart753(String name) {
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

    /** 763の Login Start：名前＋bool＋UUID。 */
    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true); // UUIDあり
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 751／753／759／763の Login Success（UUID＋名前＋プロパティ0件）。 */
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

    /** 751／753の dimension の値（最小：effects＋has_ceiling で overworld と読めるもの）。 */
    private static Tag.CompoundTag overworldDimensionValue() {
        return new Tag.CompoundTag(Map.of(
                "effects", new Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Tag.ByteTag((byte) 0)));
    }

    /** 751の Join Game（753と同一。15フィールド。previousGameMode は -1 を1バイトで書く）。 */
    private static byte[] joinGame751Payload() {
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

    /** 751の Respawn（753と同一。copyMetadata=true）。 */
    private static byte[] respawn751Payload() {
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

    /** 751の Spawn Position。{location} のみ（angle は無い。753と同じ）。 */
    private static byte[] spawnPosition751Payload() {
        Writer writer = new Writer();
        writer.writeLong(0L); // location (0, 0, 0)
        return writer.bytes();
    }

    /** 751の chat（サーバー→クライアント）。{message, position, sender}。 */
    private static byte[] chat751Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeByte(0); // position chat
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 751の chat（クライアント→サーバー）。{message}。 */
    private static byte[] chatToServer751Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 751の Block Dig。{status, location, face}。 */
    private static byte[] blockDig751Payload() {
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

    /** 759／763の Chat Message（クライアント→サーバー）。署名なし。 */
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

    /** 751の Client Information。6フィールド（disableTextFiltering 無し。753と同じ）。 */
    private static byte[] clientInformation751Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10); // viewDistance
        writer.writeVarInt(0); // chatFlags
        writer.writeBoolean(true); // chatColors
        writer.writeUnsignedByte(127); // skinParts
        writer.writeVarInt(1); // mainHand left
        return writer.bytes();
    }

    /** 751の Window Click。{windowId u8, slot i16, button i8, action i16, mode varint, item slot}。 */
    private static byte[] windowClick751Payload() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeShort(0); // action（uid）
        writer.writeVarInt(0); // mode
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（751:1。753と同じ）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 751の Edit Book。{new_book slot, signing bool, hand varint}。 */
    private static byte[] editBook751Payload() {
        Writer writer = new Writer();
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（本の代わりのスロット内容。形式確認用）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        writer.writeBoolean(true); // signing
        writer.writeVarInt(0); // hand main hand
        return writer.bytes();
    }

    /** 751の Set Slot。{windowId i8, slot i16, item slot}（stateId 無し）。 */
    private static byte[] setSlot751Payload() {
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeShort(5); // slot
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（751:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 751の Window Items。{windowId u8, count i16, items}（stateId・carriedItem 無し）。 */
    private static byte[] windowItems751Payload() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(1); // count i16
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（751:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 751の Entity Destroy。[count varint][entityIds]（753と同じ配列形式）。 */
    private static byte[] entityDestroy751Payload(int... entityIds) {
        Writer writer = new Writer();
        writer.writeVarInt(entityIds.length);
        for (int id : entityIds) {
            writer.writeVarInt(id);
        }
        return writer.bytes();
    }

    /** 751の Set Creative Slot。{slot i16, item slot}。 */
    private static byte[] setCreativeSlot751Payload() {
        Writer writer = new Writer();
        writer.writeShort(5); // slot
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（751:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 実バニラ 1.16.5 の生キャプチャ Chunk Data（チャンク -15,-7、10,633バイト。751と同形式）。 */
    private static byte[] capturedChunk754() throws Exception {
        try (InputStream in = ProxySession751Test.class.getResourceAsStream(
                "/cap754-chunk-x-15-z-7.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            return in.readAllBytes();
        }
    }

    /** ハンドシェイクと Login Start まで進めたセッションを返す。 */
    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        if (clientProtocol == 751) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart751("Steve")));
        } else if (clientProtocol == 753) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart753("Steve")));
        } else if (clientProtocol == 759) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart759("Steve")));
        } else {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart763("Steve")));
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

    /** stone（751:1。753:1と同一）の759でのID。751→753は恒等写像のため753と同じ連鎖。 */
    private static int stone759() {
        return RegistryRemapper.mapItem757To759(
                RegistryRemapper.mapItem756To757(
                        RegistryRemapper.mapItem754To755(
                                RegistryRemapper.mapItem753To754(
                                        RegistryRemapper.mapItem751To753(1)))));
    }

    // ------------------------------------------------------------------
    // 751クライアント＋751サーバー（ネイティブ）
    // ------------------------------------------------------------------

    @Test
    void join751Against751IsNative() {
        ProxySession session = loginTo(751, 751);
        loginSuccess(session);

        // Join Game（0x24）はそのまま。
        byte[] joinPayload = joinGame751Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        assertArrayEquals(joinPayload, join.toClient().get(0).payload());

        // Respawn（0x39）はそのまま。
        byte[] respawnPayload = respawn751Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x39, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x39, respawn.toClient().get(0).id());
        assertArrayEquals(respawnPayload, respawn.toClient().get(0).payload());

        // Spawn Position（0x42）はそのまま（angle は無い）。
        byte[] spawnPayload = spawnPosition751Payload();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x42, spawnPayload));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x42, spawn.toClient().get(0).id());
        assertArrayEquals(spawnPayload, spawn.toClient().get(0).payload());

        // chat（0x03）はそのまま。
        byte[] chatPayload = chatToServer751Payload("hello");
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x03, chatPayload));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x03, chat.toServer().get(0).id());
        assertArrayEquals(chatPayload, chat.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 751サーバー＋753クライアント（751→753方向。Join Game／Respawn／Spawn は恒等写像）
    // ------------------------------------------------------------------

    @Test
    void joinAndRespawn751Against753Convert() {
        ProxySession session = loginTo(753, 751);
        loginSuccess(session);

        // Join Game（751：0x24）→753（0x24）。恒等写像のためペイロードは変わらない
        // （PlayTranslator.translateJoinGame751To753の出力と一致する）。
        byte[] joinPayload = joinGame751Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame751To753(joinPayload),
                join.toClient().get(0).payload());
        assertArrayEquals(joinPayload, join.toClient().get(0).payload());

        // Respawn（751：0x39）→753（0x39）。恒等写像のためペイロードは変わらない。
        byte[] respawnPayload = respawn751Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x39, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x39, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn751To753(respawnPayload),
                respawn.toClient().get(0).payload());
        assertArrayEquals(respawnPayload, respawn.toClient().get(0).payload());

        // Spawn Position（751：0x42）→753（0x42）。angle は無いまま（8バイト）。
        byte[] spawnPayload = spawnPosition751Payload();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x42, spawnPayload));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x42, spawn.toClient().get(0).id());
        assertEquals(8, spawn.toClient().get(0).payload().length);
        assertArrayEquals(spawnPayload, spawn.toClient().get(0).payload());

        // chat（751：0x0E）は753クライアントには落とす
        // （handleServerPlayFrom763 の汎用経路の落としリストに753がある。763→753の汎用変換が未実装のため。754のときと同じ非対称性）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x0E, chat751Payload("{\"text\":\"hi\"}")));
        assertEquals(0, chat.toClient().size());

        // Set Slot（751：0x15）→753（0x15）。恒等写像のためアイテムIDは変わらない。
        // セッション経由では handleServerPlayFrom763 の汎用経路で落とされるため、
        // ここでは ProxySession が呼ぶ変換器の出力だけ確かめる。
        byte[] setSlotConverted =
                ChunkTranslator.translateSetSlot751To753(setSlot751Payload());
        assertArrayEquals(setSlot751Payload(), setSlotConverted);
        Reader setSlotReader = new Reader(setSlotConverted);
        assertEquals(0, setSlotReader.readByte()); // windowId
        assertEquals(5, setSlotReader.readShort()); // slot
        assertTrue(setSlotReader.readBoolean()); // スロットあり
        assertEquals(1, setSlotReader.readVarInt()); // stone（751:1のまま）
        assertEquals(1, setSlotReader.readByte()); // count
        assertEquals(0, setSlotReader.readByte()); // NBT無し
        assertEquals(0, setSlotReader.remaining());
        Outcome setSlot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlot751Payload()));
        assertEquals(0, setSlot.toClient().size());

        // Window Items（751：0x13）→753（0x13）。恒等写像のためアイテムIDは変わらない。
        byte[] windowItemsConverted =
                ChunkTranslator.translateWindowItems751To753(windowItems751Payload());
        assertArrayEquals(windowItems751Payload(), windowItemsConverted);
        Reader windowItemsReader = new Reader(windowItemsConverted);
        assertEquals(0, windowItemsReader.readUnsignedByte()); // windowId
        assertEquals(1, windowItemsReader.readShort()); // count i16
        assertTrue(windowItemsReader.readBoolean()); // スロットあり
        assertEquals(1, windowItemsReader.readVarInt()); // stone（751:1のまま）
        assertEquals(1, windowItemsReader.readByte()); // count
        assertEquals(0, windowItemsReader.readByte()); // NBT無し
        assertEquals(0, windowItemsReader.remaining());
        Outcome windowItems = session.handleServerPacket(
                new MinecraftPacket(0x13, windowItems751Payload()));
        assertEquals(0, windowItems.toClient().size());

        // Entity Destroy（751：[count=1][42]）→753（[count=1][42]）。恒等写像。
        // count 0／2以上もそのまま通す（754→755のような単体化は無い）。
        assertArrayEquals(new byte[]{0x01, 0x2A},
                ChunkTranslator.translateEntityDestroy751To753(entityDestroy751Payload(42)));
        assertArrayEquals(new byte[]{0x00},
                ChunkTranslator.translateEntityDestroy751To753(entityDestroy751Payload()));
        assertArrayEquals(new byte[]{0x02, 0x2A, 0x2B},
                ChunkTranslator.translateEntityDestroy751To753(entityDestroy751Payload(42, 43)));
        Outcome entityDestroy = session.handleServerPacket(
                new MinecraftPacket(0x36, entityDestroy751Payload(42)));
        assertEquals(0, entityDestroy.toClient().size());

        // Ping（751：0x11）は753クライアントには落とす
        // （751⇔753の段では素通しだが、754⇔755の段で落とされる。
        // transaction 形式で755の {id i32} と互換性が無いため）。
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x11, new byte[0]));
        assertEquals(0, ping.toClient().size());

        // Update Light（751：0x23）は753クライアントには落とす
        // （751⇔753の段では素通しだが、754⇔755の段で落とされる。マスクの形式が違うため）。
        Outcome light = session.handleServerPacket(
                new MinecraftPacket(0x23, new byte[0]));
        assertEquals(0, light.toClient().size());
    }

    // ------------------------------------------------------------------
    // 751サーバー＋759クライアント（Set Slot／Window Items／Chunk Data／Entity Destroy は届く）
    // ------------------------------------------------------------------

    @Test
    void serverPackets751Against759Reach() throws Exception {
        ProxySession session = loginTo(759, 751);
        loginSuccess(session);

        // Set Slot（751：0x15）→759（0x13）。stateId が付き、アイテムIDが付け替わる。
        Outcome setSlot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlot751Payload()));
        assertEquals(1, setSlot.toClient().size());
        assertEquals(0x13, setSlot.toClient().get(0).id());
        Reader setSlotReader = new Reader(setSlot.toClient().get(0).payload());
        assertEquals(0, setSlotReader.readByte()); // windowId
        assertEquals(0, setSlotReader.readVarInt()); // stateId（755→756で 0 を補完）
        assertEquals(5, setSlotReader.readShort()); // slot
        assertTrue(setSlotReader.readBoolean()); // スロットあり
        assertEquals(stone759(), setSlotReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, setSlotReader.readByte()); // count
        assertEquals(0, setSlotReader.readByte()); // NBT無し
        assertEquals(0, setSlotReader.remaining());

        // Window Items（751：0x13）→759（0x11）。stateId と carriedItem が付く。
        Outcome windowItems = session.handleServerPacket(
                new MinecraftPacket(0x13, windowItems751Payload()));
        assertEquals(1, windowItems.toClient().size());
        assertEquals(0x11, windowItems.toClient().get(0).id());
        Reader windowItemsReader = new Reader(windowItems.toClient().get(0).payload());
        assertEquals(0, windowItemsReader.readUnsignedByte()); // windowId
        assertEquals(0, windowItemsReader.readVarInt()); // stateId（補完値は 0）
        assertEquals(1, windowItemsReader.readVarInt()); // count（i16→varint）
        assertTrue(windowItemsReader.readBoolean()); // スロットあり
        assertEquals(stone759(), windowItemsReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, windowItemsReader.readByte()); // count
        assertEquals(0, windowItemsReader.readByte()); // NBT無し
        assertFalse(windowItemsReader.readBoolean()); // carriedItem 無し
        assertEquals(0, windowItemsReader.remaining());

        // Entity Destroy（751：[count=1][42]）→759（0x38、[count=1][42]）。
        // 751→753は恒等写像で通り、753→754も恒等写像で通り、754→755で単体化→756で配列化→757で単体化→759で配列化される。
        Outcome entityDestroy = session.handleServerPacket(
                new MinecraftPacket(0x36, entityDestroy751Payload(42)));
        assertEquals(1, entityDestroy.toClient().size());
        assertEquals(0x38, entityDestroy.toClient().get(0).id());
        assertArrayEquals(new byte[]{0x01, 0x2A},
                entityDestroy.toClient().get(0).payload());

        // Entity Destroy count 2 は754→755の段で落とす（変換器が null を返す）。
        Outcome entityDestroy2 = session.handleServerPacket(
                new MinecraftPacket(0x36, entityDestroy751Payload(42, 43)));
        assertEquals(0, entityDestroy2.toClient().size());

        // Chunk Data（751：0x20）→759（0x1F）。実バニラ生キャプチャが届く。
        byte[] captured = capturedChunk754();
        Outcome chunk = session.handleServerPacket(
                new MinecraftPacket(0x20, captured));
        assertEquals(1, chunk.toClient().size());
        assertEquals(0x1F, chunk.toClient().get(0).id());
        assertTrue(chunk.toClient().get(0).payload().length > 0);
        assertFalse(java.util.Arrays.equals(captured,
                chunk.toClient().get(0).payload()));
    }

    // ------------------------------------------------------------------
    // 751クライアント＋759サーバー（クライアント→サーバー方向）
    // ------------------------------------------------------------------

    @Test
    void clientPackets751Against759Convert() {
        ProxySession session = loginTo(751, 759);
        loginSuccess(session);

        // chat（751：0x03）→Chat Message（759：0x04）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x03, chatToServer751Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x04, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（751：0x1B）→759（0x1C）。sequence が補われる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1B, blockDig751Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1C, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.readVarInt()); // sequence（753には無いため 0 を補う）
        assertEquals(0, digReader.remaining());

        // Window Click（751：0x09）→759（0x0A）。action が捨てられ、
        // 単一スロットが cursorItem になり、changedSlots は空になる。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x09, windowClick751Payload()));
        assertEquals(1, click.toServer().size());
        assertEquals(0x0A, click.toServer().get(0).id());
        Reader clickReader = new Reader(click.toServer().get(0).payload());
        assertEquals(0, clickReader.readUnsignedByte()); // windowId
        assertEquals(0, clickReader.readVarInt()); // stateId（755→756で 0 を補完）
        assertEquals(5, clickReader.readShort()); // slot
        assertEquals(0, clickReader.readByte()); // button
        assertEquals(0, clickReader.readVarInt()); // mode
        assertEquals(0, clickReader.readVarInt()); // 変更スロット0件
        assertTrue(clickReader.readBoolean()); // cursorItem あり
        assertEquals(stone759(), clickReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, clickReader.readByte()); // count
        assertEquals(0, clickReader.readByte()); // NBT無し
        assertEquals(0, clickReader.remaining());

        // Client Information（751：0x05）→759（0x07）。disableTextFiltering=false が補われ、
        // 末尾が757形式になる。
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x05, clientInformation751Payload()));
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

        // Set Creative Slot（751：0x28）→759（0x2A）。スロットのアイテムIDが付け替わる。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x28, setCreativeSlot751Payload()));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x2A, creative.toServer().get(0).id());
        Reader creativeReader = new Reader(creative.toServer().get(0).payload());
        assertEquals(5, creativeReader.readShort()); // slot
        assertTrue(creativeReader.readBoolean()); // スロットあり
        assertEquals(stone759(), creativeReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, creativeReader.readByte()); // count
        assertEquals(0, creativeReader.readByte()); // NBT無し
        assertEquals(0, creativeReader.remaining());

        // Edit Book（751：0x0c）は落とす（755→756の段で落とされる。
        // 751⇔753の段では素通しだが、755⇔756の段に変換器が無いため）。
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0c, editBook751Payload()));
        assertEquals(0, editBook.toServer().size());

        // Pong（751：0x07）は落とす（751⇔753の段では素通しだが、754⇔755の段で落とされる。
        // transaction 形式で755の {id i32} と互換性が無いため）。
        Outcome pong = session.handleClientPacket(
                new MinecraftPacket(0x07, new byte[]{0, 0, 0, 0}));
        assertEquals(0, pong.toServer().size());
    }

    // ------------------------------------------------------------------
    // 751クライアント＋753サーバー（751⇔753の直接変換。すべて恒等写像）
    // ------------------------------------------------------------------

    @Test
    void directPackets751Against753Convert() {
        ProxySession session = loginTo(751, 753);
        loginSuccess(session);

        // Edit Book（751：0x0c）→753（0x0c）。形式が同一のため素通しする。
        byte[] editBookPayload = editBook751Payload();
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0c, editBookPayload));
        assertEquals(1, editBook.toServer().size());
        assertEquals(0x0c, editBook.toServer().get(0).id());
        assertArrayEquals(editBookPayload, editBook.toServer().get(0).payload());

        // Set Creative Slot（751：0x28）→753（0x28）。アイテムIDは変わらない（恒等写像）。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x28, setCreativeSlot751Payload()));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x28, creative.toServer().get(0).id());
        Reader creativeReader = new Reader(creative.toServer().get(0).payload());
        assertEquals(5, creativeReader.readShort()); // slot
        assertTrue(creativeReader.readBoolean()); // スロットあり
        assertEquals(1, creativeReader.readVarInt()); // stone（751:1のまま）
        assertEquals(1, creativeReader.readByte()); // count
        assertEquals(0, creativeReader.readByte()); // NBT無し
        assertEquals(0, creativeReader.remaining());

        // Window Click（751：0x09）→753（0x09）。恒等写像のためペイロードは変わらない。
        byte[] clickPayload = windowClick751Payload();
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x09, clickPayload));
        assertEquals(1, click.toServer().size());
        assertEquals(0x09, click.toServer().get(0).id());
        assertArrayEquals(clickPayload, click.toServer().get(0).payload());
        Reader clickReader = new Reader(click.toServer().get(0).payload());
        assertEquals(0, clickReader.readUnsignedByte()); // windowId
        assertEquals(5, clickReader.readShort()); // slot
        assertEquals(0, clickReader.readByte()); // button
        assertEquals(0, clickReader.readShort()); // action（uid）
        assertEquals(0, clickReader.readVarInt()); // mode
        assertTrue(clickReader.readBoolean()); // スロットあり
        assertEquals(1, clickReader.readVarInt()); // stone（751:1のまま）
        assertEquals(1, clickReader.readByte()); // count
        assertEquals(0, clickReader.readByte()); // NBT無し
        assertEquals(0, clickReader.remaining());

        // Client Information（751：0x05）→753（0x05）。恒等写像のため末尾は付かない。
        byte[] settingsPayload = clientInformation751Payload();
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x05, settingsPayload));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertEquals(settingsPayload.length,
                settings.toServer().get(0).payload().length);
        assertArrayEquals(settingsPayload, settings.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 763クライアント＋751サーバー（handleClientPlayTo751）
    // ------------------------------------------------------------------

    @Test
    void clientPackets763Against751Convert() {
        ProxySession session = loginTo(763, 751);
        loginSuccess(session);

        // Chat Message（763：0x05）→chat（751：0x03）。署名は捨てられる。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x05, chatMessage759Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（763：0x1D）→751（0x1B）。sequence が剥がれる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1D, blockDig759Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1B, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.remaining());

        // Window Click（763：0x0B）は落とす（763→755の段で落とされる。
        // translateWindowClick763To755 が無く、757経由も不可）。
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
    // 751クライアント＋775サーバー（Join Game は落とす）
    // ------------------------------------------------------------------

    @Test
    void join775Against751Drops() {
        ProxySession session = loginTo(751, 775);
        // Login Success（0x02）：クライアントはプレイ状態、サーバーは設定フェーズ。
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessPayload()));
        assertEquals(1, success.toClient().size());
        assertEquals(ProxySession.ClientState.PLAY, session.clientState());
        assertEquals(ProxySession.ServerState.CONFIGURATION, session.serverState());
        assertEquals(2, success.toServer().size());
        assertEquals(0x03, success.toServer().get(0).id()); // Login Acknowledged
        assertEquals(0x00, success.toServer().get(1).id()); // Client Information

        // Finish Configuration（775設定：0x03）：サーバーはプレイ状態に入る。
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(0, finish.toClient().size());
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());

        // Join Game（775：0x31）は751クライアントには落とす
        // （handleServerPlayFrom775 の Join Game 分岐の落としリストに751がある。
        // 763→751の Join Game 変換が未実装のため。753と同じ扱い）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame751Payload()));
        assertEquals(0, join.toClient().size());
    }

    // ------------------------------------------------------------------
    // 751クライアント＋765／764サーバー（&&／|| の優先順位の回帰）
    // ------------------------------------------------------------------

    /**
     * 753の keepAlive765Against753IsNotDroppedByRespawnGuard と同じ段取り。
     * handleServerPlayUp／handleServerPlay765From764 の Respawn／Disconnect ガードに
     * {@code || is751()} を追加したことの回帰テスト。Keep Alive が通り、
     * Respawn が落ちることを確かめる。
     */
    @Test
    void keepAlive765Against751IsNotDroppedByRespawnGuard() {
        ProxySession session = loginTo(751, 765);
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

        // Keep Alive（765：0x24）は落とされず、751の 0x1F でクライアントへ届く。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome alive = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, alive.toClient().size());
        assertEquals(0x1F, alive.toClient().get(0).id());
        assertArrayEquals(keepAlive.bytes(), alive.toClient().get(0).payload());
        assertFalse(alive.closeAfterSend());

        // Respawn（765：0x45）は落とすまま（ガードはペイロードを見る前に落とす）。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x45, new byte[0]));
        assertEquals(0, respawn.toClient().size());
        assertFalse(respawn.closeAfterSend());
    }

    @Test
    void keepAlive764Against751IsNotClosedByDisconnectGuard() {
        ProxySession session = loginTo(751, 764);
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

        // Keep Alive（764：0x24）は Disconnect 扱いで切断されず、751の 0x1F で届く。
        Writer keepAlive = new Writer();
        keepAlive.writeLong(12345L);
        Outcome alive = session.handleServerPacket(
                new MinecraftPacket(0x24, keepAlive.bytes()));
        assertEquals(1, alive.toClient().size());
        assertEquals(0x1F, alive.toClient().get(0).id());
        assertArrayEquals(keepAlive.bytes(), alive.toClient().get(0).payload());
        assertFalse(alive.closeAfterSend());

        // Respawn（764：0x43）は落とすまま。
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x43, new byte[0]));
        assertEquals(0, respawn.toClient().size());
        assertFalse(respawn.closeAfterSend());
    }

    // ------------------------------------------------------------------
    // pack751 のフォールバック（pack753）
    // ------------------------------------------------------------------

    @Test
    void pack751FallsBackToPack753() {
        ProxySession session = loginTo(751, 756);
        loginSuccess(session);

        ResourcePack pack753 = new ResourcePack(
                "https://example.invalid/pack753.zip", "aa".repeat(20), false, "");
        ResourcePack pack751 = new ResourcePack(
                "https://example.invalid/pack751.zip", "bb".repeat(20), false, "");

        // pack751 未設定：pack753 に落とす。
        assertNull(session.pack751());
        session.setPack753(pack753);
        assertEquals(pack753, session.packOverrideForClient());
        assertEquals(pack753, session.packFor763LikeClient());

        // pack751 設定済み：pack751 を出す。
        session.setPack751(pack751);
        assertEquals(pack751, session.pack751());
        assertEquals(pack751, session.packOverrideForClient());
        assertEquals(pack751, session.packFor763LikeClient());

        // どちらも未設定：null（バニラのパックをそのまま出す）。
        ProxySession fresh = loginTo(751, 756);
        loginSuccess(fresh);
        assertNull(fresh.packOverrideForClient());
        assertNull(fresh.packFor763LikeClient());
        assertNull(fresh.pack751());
    }

    // ------------------------------------------------------------------
    // 751クライアント＋775サーバー（ログイン合成。サーバー側の設定フェーズを消費）
    // ------------------------------------------------------------------

    @Test
    void login751Against775SynthesizesConfig() {
        ProxySession session = loginTo(751, 775);
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
        // 751クライアントに送るものは無い（設定フェーズが無いため）。
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(0, finish.toClient().size());
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
    }
}
