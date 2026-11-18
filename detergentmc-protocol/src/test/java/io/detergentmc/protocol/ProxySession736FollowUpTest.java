// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 736（1.16.1）の ProxySession 配線テスト（Stage B フォローアップ）。
 * {@link ProxySession736Test} と同じ段取り（ハンドシェイク→ログイン→プレイ）。
 * このテストで確かめるもの：
 * - 736クライアント→751サーバー：Recipe Book（crafting_book_data）の振り分け
 *   （type=0→displayed_recipe 0x1f、type=1→recipe_book 0x1e）と
 *   Edit Book のスロット付け替え。
 * - 751クライアント→736サーバー：recipe_book／displayed_recipe の
 *   crafting_book_data への変換と Edit Book のスロット付け替え。
 * - 736サーバー→759クライアント：値付け替え変換器の到達
 *   （Block Change／Set Cooldown／Sound Effect／Spawn Entity／
 *   Multi Block Change（単一・複数セクション）／World Particles）。
 *   Unlock Recipes は759には届かない（756→757段と757→759段に
 *   PLAY_UNLOCK_RECIPES の case が無い既存のギャップのため落とす。
 *   下記のテストを参照）。World Particles は759には届くがペイロードは
 *   755形式のまま（755→756段に case が無い既存のギャップのため。
 *   下記のテストを参照）。期待値は stone759() と同じ段取りで連鎖マッパーから計算する
 *   （736→751→751→753→753→754→754→755→756→757→757→759。
 *   755→756段は既存の素通しギャップのため飛ばす）。
 * 751サーバー→736クライアントの汎用パケットは handleServerPlayFrom763 で
 * 落とされる既存の制限のため、751→736方向のサーバー→クライアント変換器は
 * セッション経由では確かめない（変換器レベルのテストでカバーする）。
 * Update Light は736サーバー→751クライアントでは754⇔755の段で落とされる
 * （既存の ProxySession736Test でカバー済み）のため重複させない。
 */
class ProxySession736FollowUpTest {

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

    /** 736の Login Start：名前だけ（751と同じ）。 */
    private static byte[] loginStart736(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 751の Login Start：名前だけ。 */
    private static byte[] loginStart751(String name) {
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

    /** 736／751／753／759／763の Login Success（UUID＋名前＋プロパティ0件）。 */
    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(
                clientProtocol, 2, false, ProxyAuthConfig.defaults(), serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        if (clientProtocol == 736) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart736("Steve")));
        } else if (clientProtocol == 751) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart751("Steve")));
        } else if (clientProtocol == 759) {
            session.handleClientPacket(new MinecraftPacket(0x00,
                    loginStart759("Steve")));
        } else {
            throw new IllegalArgumentException("未対応のクライアント: " + clientProtocol);
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
    // 連鎖マッパー（736→759の期待値。stone759() と同じ段取り）
    // ------------------------------------------------------------------

    /** ブロック状態IDの736→759（cauldron 5145 の行き先）。 */
    private static int blockState759(int state736) {
        return RegistryRemapper.mapBlockState757To759(
                RegistryRemapper.mapBlockState756To757(
                        RegistryRemapper.mapBlockState755To756(
                                RegistryRemapper.mapBlockState754To755(
                                        RegistryRemapper.mapBlockState753To754(
                                                RegistryRemapper.mapBlockState751To753(
                                                        RegistryRemapper.mapBlockState736To751(state736)))))));
    }

    /** アイテムIDの736→759（791 の行き先）。 */
    private static int item759(int item736) {
        return RegistryRemapper.mapItem757To759(
                RegistryRemapper.mapItem756To757(
                        RegistryRemapper.mapItem755To756(
                                RegistryRemapper.mapItem754To755(
                                        RegistryRemapper.mapItem753To754(
                                                RegistryRemapper.mapItem751To753(
                                                        RegistryRemapper.mapItem736To751(item736)))))));
    }

    /** 音IDの736→759。 */
    private static int sound759(int sound736) {
        return RegistryRemapper.mapSound757To759(
                RegistryRemapper.mapSound756To757(
                        RegistryRemapper.mapSound755To756(
                                RegistryRemapper.mapSound754To755(
                                        RegistryRemapper.mapSound753To754(
                                                RegistryRemapper.mapSound751To753(
                                                        RegistryRemapper.mapSound736To751(sound736)))))));
    }

    /** エンティティ種別IDの736→759（falling_block 26 の行き先）。 */
    private static int entity759(int entity736) {
        return RegistryRemapper.mapEntityType757To759(
                RegistryRemapper.mapEntityType756To757(
                        RegistryRemapper.mapEntityType755To756(
                                RegistryRemapper.mapEntityType754To755(
                                        RegistryRemapper.mapEntityType753To754(
                                                RegistryRemapper.mapEntityType751To753(
                                                        RegistryRemapper.mapEntityType736To751(entity736)))))));
    }

    /** 759のクライアント行きパケットID（PacketIds.java の表から引く）。 */
    private static int id759(PacketIds.PacketName name) {
        return PacketIds.id(name, PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, 759).orElseThrow();
    }

    // ------------------------------------------------------------------
    // 736形式のペイロード作成用ヘルパー
    // ------------------------------------------------------------------

    /** スロット1個（736／751形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** 736の Edit Book：[slot][signing bool][hand varint]。 */
    private static byte[] editBook736Payload(int item) {
        Writer writer = new Writer();
        writeSlot(writer, item, 1);
        writer.writeBoolean(true); // signing
        writer.writeVarInt(0); // hand main hand
        return writer.bytes();
    }

    /** 736の crafting_book_data type=1：[type=1][8 bool]。 */
    private static byte[] craftingBookDataSettings736Payload(boolean[] values) {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        for (boolean value : values) {
            writer.writeBoolean(value);
        }
        return writer.bytes();
    }

    /** 736の crafting_book_data type=0：[type=0][displayedRecipe]。 */
    private static byte[] craftingBookDataShown736Payload(String recipeId) {
        Writer writer = new Writer();
        writer.writeVarInt(0);
        writer.writeString(recipeId);
        return writer.bytes();
    }

    /** 751の recipe_book：[bookId][bookOpen][filterActive]。 */
    private static byte[] recipeBook751Payload(int bookId, boolean open, boolean filter) {
        Writer writer = new Writer();
        writer.writeVarInt(bookId);
        writer.writeBoolean(open);
        writer.writeBoolean(filter);
        return writer.bytes();
    }

    /** 751の displayed_recipe：[recipeId]。 */
    private static byte[] displayedRecipe751Payload(String recipeId) {
        Writer writer = new Writer();
        writer.writeString(recipeId);
        return writer.bytes();
    }

    /** 751の chunkCoordinates（SectionPos.asLong）。 */
    private static long sectionPos(int chunkX, int sectionY, int chunkZ) {
        return ((long) (chunkX & 0x3FFFFF) << 42)
                | ((long) (chunkZ & 0x3FFFFF) << 20)
                | (long) (sectionY & 0xFFFFF);
    }

    /** 粒子IDの736→755（block 3 の755での値。759到達テストの実態確認用）。 */
    private static int particle755(int particle736) {
        return RegistryRemapper.mapParticle754To755(
                RegistryRemapper.mapParticle753To754(
                        RegistryRemapper.mapParticle751To753(
                                RegistryRemapper.mapParticle736To751(particle736))));
    }

    /** ブロック状態IDの736→755（cauldron 5145 の755での値。759到達テストの実態確認用）。 */
    private static int blockState755(int state736) {
        return RegistryRemapper.mapBlockState754To755(
                RegistryRemapper.mapBlockState753To754(
                        RegistryRemapper.mapBlockState751To753(
                                RegistryRemapper.mapBlockState736To751(state736))));
    }

    // ------------------------------------------------------------------
    // 736クライアント＋751サーバー（クライアント→サーバー方向）
    // ------------------------------------------------------------------

    @Test
    void recipeBook736Against751Converts() {
        ProxySession session = loginTo(736, 751);
        loginSuccess(session);

        // type=1（smelting 開・フィルタ無効）→751の recipe_book 0x1e（bookId 1）。
        Outcome settings = session.handleClientPacket(new MinecraftPacket(0x1e,
                craftingBookDataSettings736Payload(
                        new boolean[]{false, false, true, false, false, false, false, false})));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x1e, settings.toServer().get(0).id());
        Reader settingsReader = new Reader(settings.toServer().get(0).payload());
        assertEquals(1, settingsReader.readVarInt());
        assertTrue(settingsReader.readBoolean());
        assertFalse(settingsReader.readBoolean());
        assertEquals(0, settingsReader.remaining());

        // type=0（displayedRecipe）→751の displayed_recipe 0x1f。
        Outcome shown = session.handleClientPacket(new MinecraftPacket(0x1e,
                craftingBookDataShown736Payload("minecraft:stone")));
        assertEquals(1, shown.toServer().size());
        assertEquals(0x1f, shown.toServer().get(0).id());
        assertEquals("minecraft:stone",
                new Reader(shown.toServer().get(0).payload()).readString());
    }

    @Test
    void editBook736Against751RemapsSlot() {
        ProxySession session = loginTo(736, 751);
        loginSuccess(session);

        // Edit Book（736：0x0c）→751（0x0c）。スロットのアイテムIDが付け替わる。
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0c, editBook736Payload(791)));
        assertEquals(1, editBook.toServer().size());
        assertEquals(0x0c, editBook.toServer().get(0).id());
        Reader reader = new Reader(editBook.toServer().get(0).payload());
        assertTrue(reader.readBoolean());
        assertEquals(792, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte()); // NBT無し
        assertTrue(reader.readBoolean()); // signing
        assertEquals(0, reader.readVarInt()); // hand
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // 751クライアント＋736サーバー（クライアント→サーバー方向）
    // ------------------------------------------------------------------

    @Test
    void recipeBook751Against736Converts() {
        ProxySession session = loginTo(751, 736);
        loginSuccess(session);

        // recipe_book（bookId 2・開・フィルタ無効）→736の crafting_book_data type=1。
        Outcome settings = session.handleClientPacket(
                new MinecraftPacket(0x1e, recipeBook751Payload(2, true, false)));
        assertEquals(1, settings.toServer().size());
        assertEquals(0x1e, settings.toServer().get(0).id());
        Reader settingsReader = new Reader(settings.toServer().get(0).payload());
        assertEquals(1, settingsReader.readVarInt()); // type=SETTINGS
        assertFalse(settingsReader.readBoolean()); // craftingBookOpen
        assertFalse(settingsReader.readBoolean()); // craftingFilter
        assertFalse(settingsReader.readBoolean()); // smeltingBookOpen
        assertFalse(settingsReader.readBoolean()); // smeltingFilter
        assertTrue(settingsReader.readBoolean()); // blastingBookOpen
        assertFalse(settingsReader.readBoolean()); // blastingFilter
        assertFalse(settingsReader.readBoolean()); // smokingBookOpen
        assertFalse(settingsReader.readBoolean()); // smokingFilter
        assertEquals(0, settingsReader.remaining());

        // displayed_recipe→736の crafting_book_data type=0。
        Outcome shown = session.handleClientPacket(
                new MinecraftPacket(0x1f, displayedRecipe751Payload("minecraft:stone")));
        assertEquals(1, shown.toServer().size());
        assertEquals(0x1e, shown.toServer().get(0).id());
        Reader shownReader = new Reader(shown.toServer().get(0).payload());
        assertEquals(0, shownReader.readVarInt()); // type=SHOWN
        assertEquals("minecraft:stone", shownReader.readString());
        assertEquals(0, shownReader.remaining());
    }

    @Test
    void editBook751Against736RemapsSlot() {
        ProxySession session = loginTo(751, 736);
        loginSuccess(session);

        // Edit Book（751：0x0c）→736（0x0c）。スロットのアイテムIDが付け替わる。
        Outcome editBook = session.handleClientPacket(
                new MinecraftPacket(0x0c, editBook736Payload(792)));
        assertEquals(1, editBook.toServer().size());
        assertEquals(0x0c, editBook.toServer().get(0).id());
        Reader reader = new Reader(editBook.toServer().get(0).payload());
        assertTrue(reader.readBoolean());
        assertEquals(791, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.readByte()); // NBT無し
        assertTrue(reader.readBoolean()); // signing
        assertEquals(0, reader.readVarInt()); // hand
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // 736サーバー＋759クライアント（サーバー→クライアント方向）
    // ------------------------------------------------------------------

    /** 736の Block Change：[位置][ブロック状態ID]。 */
    private static byte[] blockChange736Payload() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789L);
        writer.writeVarInt(5145); // cauldron
        return writer.bytes();
    }

    /** 736の Set Cooldown：[アイテムID][tick数]。 */
    private static byte[] setCooldown736Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(791);
        writer.writeVarInt(20);
        return writer.bytes();
    }

    /** 736の Sound Effect：[音ID][カテゴリ][x/y/z][音量][ピッチ]。 */
    private static byte[] soundEffect736Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(626);
        writer.writeVarInt(2);
        writer.writeInt(100);
        writer.writeInt(64);
        writer.writeInt(-200);
        writer.writeFloat(1.0f);
        writer.writeFloat(0.5f);
        return writer.bytes();
    }

    /** 736の Spawn Entity（falling_block）：[entityId][UUID][種別][x/y/z][pitch][yaw][objectData][vel]。 */
    private static byte[] spawnEntity736Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        writer.writeVarInt(26); // falling_block
        writer.writeDouble(1.0);
        writer.writeDouble(64.0);
        writer.writeDouble(2.0);
        writer.writeByte(10); // pitch
        writer.writeByte(20); // yaw
        writer.writeInt(5145); // objectData（ブロック状態ID）
        writer.writeShort(100);
        writer.writeShort(-200);
        writer.writeShort(300);
        return writer.bytes();
    }

    /** 736の Unlock Recipes（action=0）：[action][4 bool][recipes1][recipes2]。 */
    private static byte[] unlockRecipes736Payload() {
        Writer writer = new Writer();
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(true);
        writer.writeVarInt(2);
        writer.writeString("a");
        writer.writeString("b");
        writer.writeVarInt(1);
        writer.writeString("c");
        return writer.bytes();
    }

    /** 736の Multi Block Change：[chunkX i32][chunkZ i32][count][{hPos, y, state}]。 */
    private static byte[] multiBlockChange736Payload(int[][] records) {
        Writer writer = new Writer();
        writer.writeInt(-2);
        writer.writeInt(3);
        writer.writeVarInt(records.length);
        for (int[] record : records) {
            writer.writeUnsignedByte((record[0] << 4) | record[1]);
            writer.writeUnsignedByte(record[2]);
            writer.writeVarInt(record[3]);
        }
        return writer.bytes();
    }

    /** 736の World Particles（block 粒子。データは末尾のブロック状態ID）。 */
    private static byte[] worldParticles736Payload() {
        Writer writer = new Writer();
        writer.writeInt(3); // 粒子 block
        writer.writeBoolean(true);
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.5f);
        writer.writeInt(4);
        writer.writeVarInt(5145);
        return writer.bytes();
    }

    @Test
    void serverPackets736Against759ReachFollowUp() {
        ProxySession session = loginTo(759, 736);
        loginSuccess(session);

        // Block Change（736：0x0b）→759（0x09）。位置はそのまま、状態IDが付け替わる。
        Outcome blockChange = session.handleServerPacket(
                new MinecraftPacket(0x0b, blockChange736Payload()));
        assertEquals(1, blockChange.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_BLOCK_CHANGE),
                blockChange.toClient().get(0).id());
        Reader blockChangeReader = new Reader(blockChange.toClient().get(0).payload());
        assertEquals(0x123456789L, blockChangeReader.readPositionRaw());
        assertEquals(blockState759(5145), blockChangeReader.readVarInt());
        assertEquals(0, blockChangeReader.remaining());

        // Set Cooldown（736：0x17）→759（0x14）。アイテムIDが付け替わる。
        Outcome cooldown = session.handleServerPacket(
                new MinecraftPacket(0x17, setCooldown736Payload()));
        assertEquals(1, cooldown.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SET_COOLDOWN),
                cooldown.toClient().get(0).id());
        Reader cooldownReader = new Reader(cooldown.toClient().get(0).payload());
        assertEquals(item759(791), cooldownReader.readVarInt());
        assertEquals(20, cooldownReader.readVarInt());
        assertEquals(0, cooldownReader.remaining());

        // Sound Effect（736：0x51）→759（0x5d）。音IDが付け替わり、seed 0 が付く。
        Outcome sound = session.handleServerPacket(
                new MinecraftPacket(0x51, soundEffect736Payload()));
        assertEquals(1, sound.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SOUND_EFFECT),
                sound.toClient().get(0).id());
        Reader soundReader = new Reader(sound.toClient().get(0).payload());
        assertEquals(sound759(626), soundReader.readVarInt());
        assertEquals(2, soundReader.readVarInt());
        assertEquals(100, soundReader.readInt());
        assertEquals(64, soundReader.readInt());
        assertEquals(-200, soundReader.readInt());
        assertEquals(1.0f, soundReader.readFloat());
        assertEquals(0.5f, soundReader.readFloat());
        assertEquals(0L, soundReader.readLong()); // seed（757→759で 0 を補完）
        assertEquals(0, soundReader.remaining());

        // Spawn Entity（736：0x00）→759（0x00）。種別IDと falling_block の
        // objectData が付け替わり、headPitch（=yaw）が付き、objectData は varint になる。
        Outcome spawn = session.handleServerPacket(
                new MinecraftPacket(0x00, spawnEntity736Payload()));
        assertEquals(1, spawn.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_SPAWN_ENTITY),
                spawn.toClient().get(0).id());
        Reader spawnReader = new Reader(spawn.toClient().get(0).payload());
        assertEquals(7, spawnReader.readVarInt());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                spawnReader.readBytes(16));
        assertEquals(entity759(26), spawnReader.readVarInt());
        assertEquals(1.0, spawnReader.readDouble());
        assertEquals(64.0, spawnReader.readDouble());
        assertEquals(2.0, spawnReader.readDouble());
        assertEquals(10, spawnReader.readByte());
        assertEquals(20, spawnReader.readByte());
        assertEquals(20, spawnReader.readByte()); // headPitch（yaw で代用）
        assertEquals(blockState759(5145), spawnReader.readVarInt());
        assertEquals(100, spawnReader.readShort());
        assertEquals(-200, spawnReader.readShort());
        assertEquals(300, spawnReader.readShort());
        assertEquals(0, spawnReader.remaining());

        // Unlock Recipes（736：0x36）は759クライアントには届かない。
        // 736→751の変換自体は PlayTranslator736FollowUpTest で確かめてあるが、
        // 751→753→754→755→756は素通し（各段の default が passthrough）のあと、
        // 756→757段（to757ServerPacketFrom756）と757→759段
        // （to759ServerPacketFrom757）に PLAY_UNLOCK_RECIPES の case が無く、
        // 両段とも default -> null（落とす）のため、757以上のクライアントには
        // 届かない。既存のギャップ（736の段の問題ではない）で、756／757担当の
        // 範囲のためここでは落とすことを確かめる。統括への判断待ち。
        Outcome unlock = session.handleServerPacket(
                new MinecraftPacket(0x36, unlockRecipes736Payload()));
        assertEquals(0, unlock.toClient().size());

        // Multi Block Change（736：0x0f、単一セクション）→759（0x3d）。
        // 759のレコードは varint（751の varlong とは違う）。
        Outcome multiSingle = session.handleServerPacket(new MinecraftPacket(0x0f,
                multiBlockChange736Payload(new int[][]{{1, 2, 65, 5145}, {15, 0, 66, 5145}})));
        assertEquals(1, multiSingle.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_MULTI_BLOCK_CHANGE),
                multiSingle.toClient().get(0).id());
        Reader multiSingleReader = new Reader(multiSingle.toClient().get(0).payload());
        assertEquals(sectionPos(-2, 4, 3),
                multiSingleReader.readLong());
        assertFalse(multiSingleReader.readBoolean());
        assertEquals(2, multiSingleReader.readVarInt());
        assertEquals((blockState759(5145) << 12) | 0x121, multiSingleReader.readVarInt());
        assertEquals((blockState759(5145) << 12) | 0xF02, multiSingleReader.readVarInt());
        assertEquals(0, multiSingleReader.remaining());

        // Multi Block Change（736：0x0f、複数セクション）→759（0x3d）2件。
        // セクション昇順（1・4）に分かれる。
        Outcome multiSplit = session.handleServerPacket(new MinecraftPacket(0x0f,
                multiBlockChange736Payload(new int[][]{{1, 2, 65, 5145}, {3, 4, 16, 5145}})));
        assertEquals(2, multiSplit.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_MULTI_BLOCK_CHANGE),
                multiSplit.toClient().get(0).id());
        Reader multiFirst = new Reader(multiSplit.toClient().get(0).payload());
        assertEquals(sectionPos(-2, 1, 3),
                multiFirst.readLong());
        assertFalse(multiFirst.readBoolean());
        assertEquals(1, multiFirst.readVarInt());
        assertEquals((blockState759(5145) << 12) | 0x340, multiFirst.readVarInt());
        assertEquals(0, multiFirst.remaining());
        assertEquals(id759(PacketIds.PacketName.PLAY_MULTI_BLOCK_CHANGE),
                multiSplit.toClient().get(1).id());
        Reader multiSecond = new Reader(multiSplit.toClient().get(1).payload());
        assertEquals(sectionPos(-2, 4, 3),
                multiSecond.readLong());
        assertFalse(multiSecond.readBoolean());
        assertEquals(1, multiSecond.readVarInt());
        assertEquals((blockState759(5145) << 12) | 0x121, multiSecond.readVarInt());
        assertEquals(0, multiSecond.remaining());

        // World Particles（736：0x23、block 粒子）→759（0x21）には届くが、
        // ペイロードは759形式ではない。736→751の変換自体は
        // ChunkTranslator736FollowUpTest で確かめてあり、751→753→754→755も
        // 正しく変換されるが、755→756段（to756ServerPacketFrom755）に
        // PLAY_WORLD_PARTICLES の case が無く素通しされるため、755形式
        // （[粒子ID i32][longDistance][x/y/z][offset][speed][count][データ]）の
        // まま756に渡る。756→757段の translateWorldParticles756To757 は
        // データがID直後にある757形式を期待するため誤読して素通しし、
        // 757→759段以降も同じく素通しするため、759クライアントには755形式・
        // 755の値（粒子ID 4・ブロック状態は755の値）のまま届く。
        // 実バニラ759クライアントは [粒子ID varint][データ][longDistance]… を
        // 期待するため、このままでは誤読される。既存のギャップ（736の段の
        // 問題ではない）で、755／756担当の範囲のためここでは届くことと
        // 755形式のままであることを確かめる。統括への判断待ち。
        Outcome particles = session.handleServerPacket(
                new MinecraftPacket(0x23, worldParticles736Payload()));
        assertEquals(1, particles.toClient().size());
        assertEquals(id759(PacketIds.PacketName.PLAY_WORLD_PARTICLES),
                particles.toClient().get(0).id());
        Reader particlesReader = new Reader(particles.toClient().get(0).payload());
        assertEquals(particle755(3), particlesReader.readInt()); // 755形式は ID が i32
        assertTrue(particlesReader.readBoolean());
        assertEquals(1.5, particlesReader.readDouble());
        assertEquals(64.0, particlesReader.readDouble());
        assertEquals(-2.5, particlesReader.readDouble());
        assertEquals(0.1f, particlesReader.readFloat());
        assertEquals(0.2f, particlesReader.readFloat());
        assertEquals(0.3f, particlesReader.readFloat());
        assertEquals(0.5f, particlesReader.readFloat());
        assertEquals(4, particlesReader.readInt());
        assertEquals(blockState755(5145), particlesReader.readVarInt()); // データは末尾
        assertEquals(0, particlesReader.remaining());
    }
}
