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
 * 754（1.16.4／1.16.5）の ProxySession 配線テスト。ChunkTranslator の
 * 754⇔755変換器（Set Slot／Window Items／Window Click／Entity Destroy／
 * Chunk Data／スロット）を ProxySession の754ハンドラから呼ぶことを、
 * セッション経由で確かめる。{@link ProxySession755Test} と同じ段取り
 * （ハンドシェイク→ログイン→プレイ。754／755とも設定フェーズは無い）。
 * Join Game 0x24⇔0x26、Respawn 0x39⇔0x3D、Spawn Position 0x42⇔0x4B、
 * chat 0x03⇔chat_message 0x04、Block Dig 0x1B⇔0x1C、
 * Window Click 0x09⇔0x0A、Set Creative Slot 0x28⇔0x2A、
 * Client Information 0x05⇔0x07、Pong 0x07⇔0x1D。
 * 注意：サーバー→クライアントは754サーバー→754／755／756／757クライアント方向の
 * 汎用パケット（Set Slot／Window Items／chat 等）が handleServerPlayFrom763 の汎用経路で
 * 落とされる（763→754の Join Game／Respawn／汎用パケット変換が未実装のため。
 * 755の ProxySession755Test と同じ非対称性）。その落としもこのテストで確かめる。
 * 754⇔755でフィールド構成が違うもの：
 * Set Slot／Window Items／Window Click／Entity Destroy は ChunkTranslator の
 * 754⇔755変換器を使う（担当3が着地済み）。Window Click は 754→755 で
 * changedSlots を空にし、単一スロットを cursorItem にする（逆向きは
 * cursorItem を単一スロットにし、action に 0 を補う）。
 * Edit Book は 754⇔755 で形式が同一（{new_book slot, signing bool, hand varint}）のため
 * 素通し＋ID付け替えする（0x0c⇔0x0b）。755⇔756のときのような全面的な違いは無い。
 * ただし 755→756の段では Edit Book が落とされるため、754クライアント→759サーバーの
 * Edit Book は落とす（754⇔755の段ではなく、755⇔756の段での落とし）。
 * Ping／Pong は transaction 形式と {id i32} 形式で互換性が無く、両方向とも落とす。
 * Update Light は754と755でマスクの形式が違うため落とす（変換器なし。ChunkTranslator 754節の申し送り）。
 * Chunk Data の変換器自体は ChunkTranslator754Test で確かめる（実バニラ生キャプチャ使用）。
 * ここでは754サーバー→759クライアントへの到達（0x20→0x1F）だけ確かめる。
 * 763〜775→754のJoin Game／Respawnは落とすままになる（755の§14.9と同じ残課題）。
 * 埋め込みのレジストリID（Sound Effect の音ID、World Particles のパーティクルID、
 * Entity Metadata の値中のアイテム・ブロック状態など）は付け替えていない
 * （ChunkTranslator 754節の申し送り。親の判断待ち）。
 */
class ProxySession754Test {

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

    /** 754の Login Start：名前だけ。 */
    private static byte[] loginStart754(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 755の Login Start：名前だけ。 */
    private static byte[] loginStart755(String name) {
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

    /** 774の Login Start：名前＋UUID（766以降の形式）。 */
    private static byte[] loginStart774(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 754／755／756／757／759／774の Login Success（UUID＋名前＋プロパティ0件）。 */
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

    /** 754／755の dimension の値（最小：effects＋has_ceiling で overworld と読めるもの）。 */
    private static Tag.CompoundTag overworldDimensionValue() {
        return new Tag.CompoundTag(Map.of(
                "effects", new Tag.StringTag("minecraft:overworld"),
                "has_ceiling", new Tag.ByteTag((byte) 0)));
    }

    /** 754の Join Game（755と同一。15フィールド。previousGameMode は -1 を1バイトで書く）。 */
    private static byte[] joinGame754Payload() {
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

    /** 754の Respawn（755と同一。copyMetadata=true）。 */
    private static byte[] respawn754Payload() {
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

    /** 754の Spawn Position。{location} のみ（angle は無い）。 */
    private static byte[] spawnPosition754Payload() {
        Writer writer = new Writer();
        writer.writeLong(0L); // location (0, 0, 0)
        return writer.bytes();
    }

    /** 754の chat（サーバー→クライアント）。{message, position, sender}。 */
    private static byte[] chat754Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeByte(0); // position chat
        writer.writeUuid(PLAYER_UUID);
        return writer.bytes();
    }

    /** 754の chat（クライアント→サーバー）。{message}。 */
    private static byte[] chatToServer754Payload(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 754の Block Dig。{status, location, face}。 */
    private static byte[] blockDig754Payload() {
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

    /** 754の Client Information。6フィールド（disableTextFiltering 無し）。 */
    private static byte[] clientInformation754Payload() {
        Writer writer = new Writer();
        writer.writeString("en_us");
        writer.writeByte(10); // viewDistance
        writer.writeVarInt(0); // chatFlags
        writer.writeBoolean(true); // chatColors
        writer.writeUnsignedByte(127); // skinParts
        writer.writeVarInt(1); // mainHand left
        return writer.bytes();
    }

    /** 754の Window Click。{windowId u8, slot i16, button i8, action i16, mode varint, item slot}。 */
    private static byte[] windowClick754Payload() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeShort(0); // action（uid）
        writer.writeVarInt(0); // mode
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（754:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 754の Edit Book。{new_book slot, signing bool, hand varint}。 */
    private static byte[] editBook754Payload() {
        Writer writer = new Writer();
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（本の代わりのスロット内容。形式確認用）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        writer.writeBoolean(true); // signing
        writer.writeVarInt(0); // hand main hand
        return writer.bytes();
    }

    /** 754の Set Slot。{windowId i8, slot i16, item slot}（stateId 無し）。 */
    private static byte[] setSlot754Payload() {
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeShort(5); // slot
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（754:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 754の Window Items。{windowId u8, count i16, items}（stateId・carriedItem 無し）。 */
    private static byte[] windowItems754Payload() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(1); // count i16
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（754:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 754の Entity Destroy。[count varint][entityIds]。 */
    private static byte[] entityDestroy754Payload(int... entityIds) {
        Writer writer = new Writer();
        writer.writeVarInt(entityIds.length);
        for (int id : entityIds) {
            writer.writeVarInt(id);
        }
        return writer.bytes();
    }

    /** 754の Set Creative Slot。{slot i16, item slot}。 */
    private static byte[] setCreativeSlot754Payload() {
        Writer writer = new Writer();
        writer.writeShort(5); // slot
        writer.writeBoolean(true); // スロットあり
        writer.writeVarInt(1); // stone（754:1）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT無し
        return writer.bytes();
    }

    /** 実バニラ 1.16.5 の生キャプチャ Chunk Data（チャンク -15,-7、10,633バイト）。 */
    private static byte[] capturedChunk754() throws Exception {
        try (InputStream in = ProxySession754Test.class.getResourceAsStream(
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
        if (clientProtocol == 754) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart754("Steve")));
        } else if (clientProtocol == 755) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart755("Steve")));
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

    /** stone（754:1）の759でのID。754→755→756（同一）→757→759の付け替え。 */
    private static int stone759() {
        return RegistryRemapper.mapItem757To759(
                RegistryRemapper.mapItem756To757(
                        RegistryRemapper.mapItem754To755(1)));
    }

    // ------------------------------------------------------------------
    // 754クライアント＋754サーバー（ネイティブ）
    // ------------------------------------------------------------------

    @Test
    void join754Against754IsNative() {
        ProxySession session = loginTo(754, 754);
        loginSuccess(session);

        // Join Game（0x24）はそのまま。
        byte[] joinPayload = joinGame754Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x24, join.toClient().get(0).id());
        assertArrayEquals(joinPayload, join.toClient().get(0).payload());

        // Respawn（0x39）はそのまま。
        byte[] respawnPayload = respawn754Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x39, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x39, respawn.toClient().get(0).id());
        assertArrayEquals(respawnPayload, respawn.toClient().get(0).payload());

        // Spawn Position（0x42）はそのまま（angle は無い）。
        byte[] spawnPayload = spawnPosition754Payload();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x42, spawnPayload));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x42, spawn.toClient().get(0).id());
        assertArrayEquals(spawnPayload, spawn.toClient().get(0).payload());

        // chat（0x03）はそのまま。
        byte[] chatPayload = chatToServer754Payload("hello");
        Outcome chat = session.handleClientPacket(
                new MinecraftPacket(0x03, chatPayload));
        assertEquals(1, chat.toServer().size());
        assertEquals(0x03, chat.toServer().get(0).id());
        assertArrayEquals(chatPayload, chat.toServer().get(0).payload());
    }

    // ------------------------------------------------------------------
    // 754サーバー＋755クライアント（754→755方向。Join Game／Respawn／Spawn は通る）
    // ------------------------------------------------------------------

    @Test
    void joinAndRespawn754Against755Convert() {
        ProxySession session = loginTo(755, 754);
        loginSuccess(session);

        // Join Game（754：0x24）→755（0x26）。dimensionCodecを755形式に書き換える
        // （dimension elementにmin_y／heightを足し、バイオームにdripstone_caves・
        // lush_cavesを補う。PlayTranslator.translateJoinGame754To755の出力と一致する。
        // 359バイト→1,474バイトに膨らむのはcodec書き換えのため）。
        byte[] joinPayload = joinGame754Payload();
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x24, joinPayload));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateJoinGame754To755(joinPayload),
                join.toClient().get(0).payload());
        assertFalse(java.util.Arrays.equals(joinPayload,
                join.toClient().get(0).payload()));

        // Respawn（754：0x39）→755（0x3D）。dimensionのelementだけ755既定値に直す
        // （min_y／heightを足す。translateRespawn754To755の出力と一致する）。
        byte[] respawnPayload = respawn754Payload();
        Outcome respawn = session.handleServerPacket(
                new MinecraftPacket(0x39, respawnPayload));
        assertEquals(1, respawn.toClient().size());
        assertEquals(0x3D, respawn.toClient().get(0).id());
        assertArrayEquals(PlayTranslator.translateRespawn754To755(respawnPayload),
                respawn.toClient().get(0).payload());
        assertFalse(java.util.Arrays.equals(respawnPayload,
                respawn.toClient().get(0).payload()));

        // Spawn Position（754：0x42）→755（0x4B）。angle 0.0f が補われる。
        byte[] spawnPayload = spawnPosition754Payload();
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x42, spawnPayload));
        assertEquals(1, spawn.toClient().size());
        assertEquals(0x4B, spawn.toClient().get(0).id());
        assertEquals(12, spawn.toClient().get(0).payload().length);
        assertArrayEquals(spawnPayload,
                java.util.Arrays.copyOf(spawn.toClient().get(0).payload(), 8));
        assertArrayEquals(new byte[]{0, 0, 0, 0},
                java.util.Arrays.copyOfRange(spawn.toClient().get(0).payload(), 8, 12));

        // chat（754：0x0E）は755クライアントには落とす
        // （handleServerPlayFrom763 の汎用経路の落としリストに755がある。763→755の汎用変換が未実装のため）。
        Outcome chat = session.handleServerPacket(
                new MinecraftPacket(0x0E, chat754Payload("{\"text\":\"hi\"}")));
        assertEquals(0, chat.toClient().size());

        // Set Slot（754：0x15）→755（0x16）。スロットのアイテムIDが付け替わる。
        // セッション経由では handleServerPlayFrom763 の汎用経路で落とされるため、
        // ここでは ProxySession が呼ぶ変換器の出力だけ確かめる。
        byte[] setSlotConverted =
                ChunkTranslator.translateSetSlot754To755(setSlot754Payload());
        Reader setSlotReader = new Reader(setSlotConverted);
        assertEquals(0, setSlotReader.readByte()); // windowId
        assertEquals(5, setSlotReader.readShort()); // slot
        assertTrue(setSlotReader.readBoolean()); // スロットあり
        assertEquals(RegistryRemapper.mapItem754To755(1),
                setSlotReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, setSlotReader.readByte()); // count
        assertEquals(0, setSlotReader.readByte()); // NBT無し
        assertEquals(0, setSlotReader.remaining());
        Outcome setSlot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlot754Payload()));
        assertEquals(0, setSlot.toClient().size());

        // Window Items（754：0x13）→755（0x14）。スロットのアイテムIDが付け替わる。
        byte[] windowItemsConverted =
                ChunkTranslator.translateWindowItems754To755(windowItems754Payload());
        Reader windowItemsReader = new Reader(windowItemsConverted);
        assertEquals(0, windowItemsReader.readUnsignedByte()); // windowId
        assertEquals(1, windowItemsReader.readShort()); // count i16
        assertTrue(windowItemsReader.readBoolean()); // スロットあり
        assertEquals(RegistryRemapper.mapItem754To755(1),
                windowItemsReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, windowItemsReader.readByte()); // count
        assertEquals(0, windowItemsReader.readByte()); // NBT無し
        assertEquals(0, windowItemsReader.remaining());
        Outcome windowItems = session.handleServerPacket(
                new MinecraftPacket(0x13, windowItems754Payload()));
        assertEquals(0, windowItems.toClient().size());

        // Entity Destroy（754：[count=1][42]）→755（[42]）。count 1 だけ単体に直せる。
        assertArrayEquals(new byte[]{0x2A},
                ChunkTranslator.translateEntityDestroy754To755(entityDestroy754Payload(42)));
        assertNull(ChunkTranslator.translateEntityDestroy754To755(entityDestroy754Payload()));
        assertNull(ChunkTranslator.translateEntityDestroy754To755(
                entityDestroy754Payload(42, 43)));
        Outcome entityDestroy = session.handleServerPacket(
                new MinecraftPacket(0x36, entityDestroy754Payload(42)));
        assertEquals(0, entityDestroy.toClient().size());

        // Ping（754：0x11）は落とす（transaction 形式で755の {id i32} と互換性が無い）。
        Outcome ping = session.handleServerPacket(
                new MinecraftPacket(0x11, new byte[0]));
        assertEquals(0, ping.toClient().size());

        // Update Light（754：0x23）は落とす（マスクの形式が違う。変換器なし）。
        Outcome light = session.handleServerPacket(
                new MinecraftPacket(0x23, new byte[0]));
        assertEquals(0, light.toClient().size());
    }

    // ------------------------------------------------------------------
    // 754サーバー＋759クライアント（Set Slot／Window Items／Chunk Data／Entity Destroy は届く）
    // ------------------------------------------------------------------

    @Test
    void serverPackets754Against759Reach() throws Exception {
        ProxySession session = loginTo(759, 754);
        loginSuccess(session);

        // Set Slot（754：0x15）→759（0x13）。stateId が付き、アイテムIDが付け替わる。
        Outcome setSlot = session.handleServerPacket(
                new MinecraftPacket(0x15, setSlot754Payload()));
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

        // Window Items（754：0x13）→759（0x11）。stateId と carriedItem が付く。
        Outcome windowItems = session.handleServerPacket(
                new MinecraftPacket(0x13, windowItems754Payload()));
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

        // Entity Destroy（754：[count=1][42]）→759（0x38、[count=1][42]）。
        // フォローアップで757→759の段の隙間（PLAY_ENTITY_DESTROYのcase無し）を直したため届く。
        // 754→755で単体化→756で配列化→757で単体化→759で配列化される。
        Outcome entityDestroy = session.handleServerPacket(
                new MinecraftPacket(0x36, entityDestroy754Payload(42)));
        assertEquals(1, entityDestroy.toClient().size());
        assertEquals(0x38, entityDestroy.toClient().get(0).id());
        assertArrayEquals(new byte[]{0x01, 0x2A},
                entityDestroy.toClient().get(0).payload());

        // Entity Destroy count 2 は754→755の段で落とす（変換器が null を返す）。
        Outcome entityDestroy2 = session.handleServerPacket(
                new MinecraftPacket(0x36, entityDestroy754Payload(42, 43)));
        assertEquals(0, entityDestroy2.toClient().size());

        // Chunk Data（754：0x20）→759（0x1F）。実バニラ生キャプチャが届く。
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
    // 754クライアント＋759サーバー（クライアント→サーバー方向）
    // ------------------------------------------------------------------

    @Test
    void clientPackets754Against759Convert() {
        ProxySession session = loginTo(754, 759);
        loginSuccess(session);

        // chat（754：0x03）→Chat Message（759：0x04）。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x03, chatToServer754Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x04, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（754：0x1B）→759（0x1C）。sequence が補われる。
        Outcome dig = session.handleClientPacket(
                new MinecraftPacket(0x1B, blockDig754Payload()));
        assertEquals(1, dig.toServer().size());
        assertEquals(0x1C, dig.toServer().get(0).id());
        Reader digReader = new Reader(dig.toServer().get(0).payload());
        assertEquals(0, digReader.readVarInt());
        assertEquals(0L, digReader.readLong());
        assertEquals(1, digReader.readByte());
        assertEquals(0, digReader.readVarInt()); // sequence（754には無いため 0 を補う）
        assertEquals(0, digReader.remaining());

        // Window Click（754：0x09）→759（0x0A）。action が捨てられ、
        // 単一スロットが cursorItem になり、changedSlots は空になる。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x09, windowClick754Payload()));
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

        // Client Information（754：0x05）→759（0x07）。disableTextFiltering=false が補われ、
        // 末尾が757形式になる。
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x05, clientInformation754Payload()));
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

        // Set Creative Slot（754：0x28）→759（0x2A）。スロットのアイテムIDが付け替わる。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x28, setCreativeSlot754Payload()));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x2A, creative.toServer().get(0).id());
        Reader creativeReader = new Reader(creative.toServer().get(0).payload());
        assertEquals(5, creativeReader.readShort()); // slot
        assertTrue(creativeReader.readBoolean()); // スロットあり
        assertEquals(stone759(), creativeReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, creativeReader.readByte()); // count
        assertEquals(0, creativeReader.readByte()); // NBT無し
        assertEquals(0, creativeReader.remaining());

        // Edit Book（754：0x0c）は落とす（755→756の段で落とされる。
        // 754⇔755の段では素通しだが、755⇔756の段に変換器が無いため）。
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0c, editBook754Payload()));
        assertEquals(0, editBook.toServer().size());

        // Pong（754：0x07）は落とす（transaction 形式で755の {id i32} と互換性が無い）。
        Outcome pong = session.handleClientPacket(
                new MinecraftPacket(0x07, new byte[]{0, 0, 0, 0}));
        assertEquals(0, pong.toServer().size());
    }

    // ------------------------------------------------------------------
    // 754クライアント＋755サーバー（754⇔755の直接変換）
    // ------------------------------------------------------------------

    @Test
    void directPackets754Against755Convert() {
        ProxySession session = loginTo(754, 755);
        loginSuccess(session);

        // Edit Book（754：0x0c）→755（0x0b）。形式が同一のため素通しする。
        byte[] editBookPayload = editBook754Payload();
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0c, editBookPayload));
        assertEquals(1, editBook.toServer().size());
        assertEquals(0x0b, editBook.toServer().get(0).id());
        assertArrayEquals(editBookPayload, editBook.toServer().get(0).payload());

        // Set Creative Slot（754：0x28）→755（0x28）。スロットのアイテムIDが付け替わる。
        Outcome creative = session.handleClientPacket(
                new MinecraftPacket(0x28, setCreativeSlot754Payload()));
        assertEquals(1, creative.toServer().size());
        assertEquals(0x28, creative.toServer().get(0).id());
        Reader creativeReader = new Reader(creative.toServer().get(0).payload());
        assertEquals(5, creativeReader.readShort()); // slot
        assertTrue(creativeReader.readBoolean()); // スロットあり
        assertEquals(RegistryRemapper.mapItem754To755(1),
                creativeReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, creativeReader.readByte()); // count
        assertEquals(0, creativeReader.readByte()); // NBT無し
        assertEquals(0, creativeReader.remaining());

        // Window Click（754：0x09）→755（0x08）。changedSlots は空、cursorItem は石。
        Outcome click = session.handleClientPacket(
                new MinecraftPacket(0x09, windowClick754Payload()));
        assertEquals(1, click.toServer().size());
        assertEquals(0x08, click.toServer().get(0).id());
        Reader clickReader = new Reader(click.toServer().get(0).payload());
        assertEquals(0, clickReader.readUnsignedByte()); // windowId
        assertEquals(5, clickReader.readShort()); // slot
        assertEquals(0, clickReader.readByte()); // button
        assertEquals(0, clickReader.readVarInt()); // mode
        assertEquals(0, clickReader.readVarInt()); // 変更スロット0件
        assertTrue(clickReader.readBoolean()); // cursorItem あり
        assertEquals(RegistryRemapper.mapItem754To755(1),
                clickReader.readVarInt()); // stone の付け替え後ID
        assertEquals(1, clickReader.readByte()); // count
        assertEquals(0, clickReader.readByte()); // NBT無し
        assertEquals(0, clickReader.remaining());

        // Client Information（754：0x05）→755（0x05）。末尾に disableTextFiltering=false が付く。
        byte[] settingsPayload = clientInformation754Payload();
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x05, settingsPayload));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x05, settings.toServer().get(0).id());
        assertEquals(settingsPayload.length + 1,
                settings.toServer().get(0).payload().length);
        assertEquals(0, settings.toServer().get(0).payload()[
                settings.toServer().get(0).payload().length - 1]); // disableTextFiltering=false
    }

    // ------------------------------------------------------------------
    // 763クライアント＋754サーバー（handleClientPlayTo754）
    // ------------------------------------------------------------------

    @Test
    void clientPackets763Against754Convert() {
        ProxySession session = loginTo(763, 754);
        loginSuccess(session);

        // Chat Message（763：0x05）→chat（754：0x03）。署名は捨てられる。
        Outcome chatOut = session.handleClientPacket(
                new MinecraftPacket(0x05, chatMessage759Payload("hello")));
        assertEquals(1, chatOut.toServer().size());
        assertEquals(0x03, chatOut.toServer().get(0).id());
        assertEquals("hello", new Reader(chatOut.toServer().get(0).payload()).readString());

        // Block Dig（763：0x1D）→754（0x1B）。sequence が剥がれる。
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
    // 754クライアント＋775サーバー（Join Game は落とす）
    // ------------------------------------------------------------------

    @Test
    void join775Against754Drops() {
        ProxySession session = loginTo(754, 775);
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

        // Join Game（775：0x31）は754クライアントには落とす
        // （handleServerPlayFrom775 の Join Game 分岐の落としリストに754がある。
        // 763→754の Join Game 変換が未実装のため。755と同じ扱い）。
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x31, joinGame754Payload()));
        assertEquals(0, join.toClient().size());
    }

    // ------------------------------------------------------------------
    // 754クライアント＋765／764サーバー（&&／|| の優先順位の回帰）
    // ------------------------------------------------------------------

    /**
     * 755の keepAlive765Against755IsNotDroppedByRespawnGuard と同じ段取り。
     * handleServerPlayUp／handleServerPlay765From764 の Respawn／Disconnect ガードに
     * {@code || is754()} を追加したことの回帰テスト。Keep Alive が通り、
     * Respawn が落ちることを確かめる。
     */
    @Test
    void keepAlive765Against754IsNotDroppedByRespawnGuard() {
        ProxySession session = loginTo(754, 765);
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

        // Keep Alive（765：0x24）は落とされず、754の 0x1F でクライアントへ届く。
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
    void keepAlive764Against754IsNotClosedByDisconnectGuard() {
        ProxySession session = loginTo(754, 764);
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

        // Keep Alive（764：0x24）は Disconnect 扱いで切断されず、754の 0x1F で届く。
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
    // pack754 のフォールバック（pack755）
    // ------------------------------------------------------------------

    @Test
    void pack754FallsBackToPack755() {
        ProxySession session = loginTo(754, 756);
        loginSuccess(session);

        ResourcePack pack755 = new ResourcePack(
                "https://example.invalid/pack755.zip", "aa".repeat(20), false, "");
        ResourcePack pack754 = new ResourcePack(
                "https://example.invalid/pack754.zip", "bb".repeat(20), false, "");

        // pack754 未設定：pack755 に落とす。
        assertNull(session.pack754());
        session.setPack755(pack755);
        assertEquals(pack755, session.packOverrideForClient());
        assertEquals(pack755, session.packFor763LikeClient());

        // pack754 設定済み：pack754 を出す。
        session.setPack754(pack754);
        assertEquals(pack754, session.pack754());
        assertEquals(pack754, session.packOverrideForClient());
        assertEquals(pack754, session.packFor763LikeClient());

        // どちらも未設定：null（バニラのパックをそのまま出す）。
        ProxySession fresh = loginTo(754, 756);
        loginSuccess(fresh);
        assertNull(fresh.packOverrideForClient());
        assertNull(fresh.packFor763LikeClient());
        assertNull(fresh.pack754());
    }

    // ------------------------------------------------------------------
    // 754クライアント＋775サーバー（ログイン合成。サーバー側の設定フェーズを消費）
    // ------------------------------------------------------------------

    @Test
    void login754Against775SynthesizesConfig() {
        ProxySession session = loginTo(754, 775);
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
        // 754クライアントに送るものは無い（設定フェーズが無いため）。
        Outcome finish = session.handleServerPacket(
                new MinecraftPacket(0x03, new byte[0]));
        assertEquals(ProxySession.ServerState.PLAY, session.serverState());
        assertEquals(0, finish.toClient().size());
        assertEquals(1, finish.toServer().size());
        assertEquals(0x03, finish.toServer().get(0).id());
    }
}
