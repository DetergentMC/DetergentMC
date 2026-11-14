package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockEntityTranslator.BedrockItem;
import io.detergentmc.bedrock.BedrockPackets.InventoryTransactionUse;
import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import org.junit.jupiter.api.Test;

/**
 * バージョン別パケット形式のテスト。StartGame・ResourcePacksInfo・
 * ResourcePackStack・Item（ItemV4 含む）・InventoryTransaction の
 * 形式差分を、minecraft-data の protocol.json で確認した形ごとに検証する。
 */
class BedrockPacketsVersionTest {

    /** StartGame を最後まで読む。読み切れなければ形式が合っていない。 */
    record ParsedStartGame(boolean hasHardcoreField, boolean hardcoreValue,
            int preLevelIdentifierCount,
            int itemstatesCount, boolean hasTickDeathSystemsField,
            boolean tickDeathSystemsValue, boolean hasChatLoggingField,
            boolean chatLoggingValue,
            boolean joinInfoTail, boolean hasMovementAuthority, String gameVersion,
            String worldName) {
    }

    static ParsedStartGame parseStartGame(byte[] payload, BedrockVersion version,
            BedrockBlockPalette palette) {
        ByteReader in = new ByteReader(payload);
        BedrockVarInt.readZigzag64(in); // entity id
        BedrockVarInt.readUnsignedVarInt(in); // runtime entity id
        BedrockVarInt.readZigzag32(in); // player gamemode
        in.readLittleFloat(); // pos x
        in.readLittleFloat(); // pos y
        in.readLittleFloat(); // pos z
        in.readLittleFloat(); // pitch
        in.readLittleFloat(); // yaw
        in.readLittleLong(); // seed
        in.readLittleShort(); // biome type
        BedrockVarInt.readString(in); // biome name
        BedrockVarInt.readZigzag32(in); // dimension
        BedrockVarInt.readZigzag32(in); // generator
        BedrockVarInt.readZigzag32(in); // world gamemode
        boolean hasHardcoreField = version.hasHardcore();
        boolean hardcoreValue = false;
        if (hasHardcoreField) {
            hardcoreValue = in.readBoolean();
        }
        BedrockVarInt.readZigzag32(in); // difficulty
        BedrockVarInt.readZigzag32(in); // spawn x
        BedrockVarInt.readUnsignedVarInt(in); // spawn y
        BedrockVarInt.readZigzag32(in); // spawn z
        in.readBoolean(); // achievements disabled
        BedrockVarInt.readZigzag32(in); // editor world type
        in.readBoolean(); // created in editor
        in.readBoolean(); // exported from editor
        BedrockVarInt.readZigzag32(in); // day cycle stop time
        BedrockVarInt.readZigzag32(in); // edu offer
        in.readBoolean(); // edu features enabled
        BedrockVarInt.readString(in); // edu product uuid
        in.readLittleFloat(); // rain level
        in.readLittleFloat(); // lightning level
        in.readBoolean(); // confirmed platform locked content
        in.readBoolean(); // multiplayer
        in.readBoolean(); // broadcast to lan
        BedrockVarInt.readUnsignedVarInt(in); // xbox live broadcast mode
        BedrockVarInt.readUnsignedVarInt(in); // platform broadcast mode
        in.readBoolean(); // enable commands
        in.readBoolean(); // texturepacks required
        BedrockVarInt.readUnsignedVarInt(in); // gamerules
        BedrockVarInt.readUnsignedVarInt(in); // experiments
        in.readBoolean(); // experiments previously used
        in.readBoolean(); // bonus chest
        in.readBoolean(); // map enabled
        in.readUnsignedByte(); // permission level
        in.readLittleInt(); // server chunk tick range
        in.readBoolean(); // locked behavior pack
        in.readBoolean(); // locked resource pack
        in.readBoolean(); // from locked world template
        in.readBoolean(); // msa gamertags only
        in.readBoolean(); // from world template
        in.readBoolean(); // world template option locked
        in.readBoolean(); // only spawn v1 villagers
        in.readBoolean(); // persona disabled
        in.readBoolean(); // custom skins disabled
        in.readBoolean(); // emote chat muted
        String gameVersion = BedrockVarInt.readString(in);
        in.readLittleInt(); // limited world width
        in.readLittleInt(); // limited world length
        in.readBoolean(); // is new nether
        BedrockVarInt.readString(in); // edu resource uri button
        BedrockVarInt.readString(in); // edu resource uri link
        in.readBoolean(); // experimental gameplay override
        in.readUnsignedByte(); // chat restriction level
        in.readBoolean(); // disable player interactions
        int preLevelIdentifierCount = 0;
        if (version.hasPreLevelIdentifiers()) {
            for (int i = 0; i < 3; i++) {
                assertEquals("", BedrockVarInt.readString(in), "識別子は空");
            }
            preLevelIdentifierCount = 3;
        } else if (version.hasOwnerIdentifier()) {
            for (int i = 0; i < 4; i++) {
                assertEquals("", BedrockVarInt.readString(in), "識別子は空");
            }
            preLevelIdentifierCount = 4;
        }
        if (version.hasEditorPolicy()) {
            BedrockVarInt.readZigzag32(in); // editor connection policy
            in.readBoolean(); // allow anonymous block drops
        }
        BedrockVarInt.readString(in); // level id
        String worldName = BedrockVarInt.readString(in);
        BedrockVarInt.readString(in); // premium world template id
        in.readBoolean(); // is trial
        boolean hasMovementAuthority = version.hasMovementAuthority();
        if (hasMovementAuthority) {
            BedrockVarInt.readZigzag32(in); // movement authority
        }
        BedrockVarInt.readZigzag32(in); // rewind history size
        in.readBoolean(); // server authoritative block breaking
        in.readLittleLong(); // current tick
        BedrockVarInt.readZigzag32(in); // enchantment seed
        long blockProperties = BedrockVarInt.readUnsignedVarInt(in);
        if (palette != null) {
            assertEquals(palette.count(), blockProperties, "パレットの件数");
            in.readBytes(palette.rawPaletteBytes().length);
        } else {
            assertEquals(0, blockProperties, "パレットなし");
        }
        int itemstatesCount = -1;
        if (version.hasItemstates()) {
            itemstatesCount = (int) BedrockVarInt.readUnsignedVarInt(in);
        }
        BedrockVarInt.readString(in); // multiplayer correlation id
        in.readBoolean(); // server authoritative inventory
        BedrockVarInt.readString(in); // engine
        assertEquals(0x0A, in.readUnsignedByte()); // property data: compound
        assertEquals(0, in.readUnsignedByte()); // 名前長 0
        assertEquals(0x00, in.readUnsignedByte()); // TAG_End
        in.readLittleLong(); // block palette checksum
        in.readBytes(16); // world template id
        in.readBoolean(); // client side generation
        in.readBoolean(); // block network ids are hashes
        boolean hasTickDeathSystemsField = version.hasTickDeathSystems();
        boolean tickDeathSystemsValue = false;
        if (hasTickDeathSystemsField) {
            tickDeathSystemsValue = in.readBoolean();
        }
        in.readBoolean(); // server controlled sound
        boolean hasChatLoggingField = version.hasChatLogging();
        boolean chatLoggingValue = false;
        if (hasChatLoggingField) {
            chatLoggingValue = in.readBoolean();
        }
        boolean joinInfoTail = version.hasJoinInfoTail();
        if (joinInfoTail) {
            assertFalse(in.readBoolean(), "has_server_join_info は false");
            assertFalse(in.readBoolean(), "server_join_info は無し");
            for (int i = 0; i < 4; i++) {
                assertEquals("", BedrockVarInt.readString(in), "末尾の識別子は空");
            }
        }
        assertEquals(0, in.remaining(),
                "StartGame は最後まで読み切れること（" + version.displayName() + "）");
        return new ParsedStartGame(hasHardcoreField, hardcoreValue,
                preLevelIdentifierCount, itemstatesCount,
                hasTickDeathSystemsField, tickDeathSystemsValue,
                hasChatLoggingField, chatLoggingValue,
                joinInfoTail, hasMovementAuthority,
                gameVersion, worldName);
    }

    @Test
    void startGameParsesForEveryShape() {
        BedrockVersion[] shapes = {BedrockVersion.V1_20_0, BedrockVersion.V1_20_80,
                BedrockVersion.V1_21_0, BedrockVersion.V1_21_50,
                BedrockVersion.V1_21_90, BedrockVersion.V1_21_100,
                BedrockVersion.V1_21_130, BedrockVersion.V1_26_0,
                BedrockVersion.V1_26_30, BedrockVersion.V1_26_50};
        for (BedrockVersion version : shapes) {
            BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                    1, 0.5f, 64.0f, 0.5f, "world", version.displayName());
            byte[] payload = BedrockPackets.encodeStartGame(info, null, version);
            ParsedStartGame parsed = parseStartGame(payload, version, null);
            assertEquals(version.displayName(), parsed.gameVersion(),
                    "game version はクライアントの表示名（" + version.displayName() + "）");
            assertEquals("world", parsed.worldName());
            assertEquals(version.hasHardcore(), parsed.hasHardcoreField(),
                    "hardcore 欄の有無（" + version.displayName() + "）");
            assertFalse(parsed.hardcoreValue(), "hardcore の値は false");
            int expectedIdentifiers = version.hasPreLevelIdentifiers() ? 3
                    : version.hasOwnerIdentifier() ? 4 : 0;
            assertEquals(expectedIdentifiers, parsed.preLevelIdentifierCount());
            assertEquals(version.hasItemstates() ? 0 : -1, parsed.itemstatesCount());
            assertEquals(version.hasTickDeathSystems(),
                    parsed.hasTickDeathSystemsField(), "tick_death_systems 欄の有無");
            assertFalse(parsed.tickDeathSystemsValue(), "tick_death_systems の値は false");
            assertEquals(version.hasChatLogging(), parsed.hasChatLoggingField(),
                    "is_chat_logging 欄の有無");
            assertFalse(parsed.chatLoggingValue(), "is_chat_logging の値は false");
            assertEquals(version.hasJoinInfoTail(), parsed.joinInfoTail());
            assertEquals(version.hasMovementAuthority(), parsed.hasMovementAuthority());
        }
    }

    @Test
    void startGameWithPaletteParses() throws Exception {
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "1.20.50");
        byte[] payload = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_20_50);
        ParsedStartGame parsed = parseStartGame(payload, BedrockVersion.V1_20_50, palette);
        assertEquals("1.20.50", parsed.gameVersion());
        assertEquals(0, parsed.itemstatesCount(), "1.20.50 は itemstates 付き");
    }

    @Test
    void legacyStartGameOverloadStillMatches1_20_50() {
        // 旧 2 引数版は 1.20.50 形。既存テストとの互換性のため残している。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "1.20.50");
        byte[] legacy = BedrockPackets.encodeStartGame(info, null);
        byte[] versioned = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_20_50);
        // correlation id はランダムなため長さだけ比較する。
        assertEquals(versioned.length, legacy.length);
    }

    /** ResourcePacksInfo を読む。空パックの既定値を確認する。 */
    static void parseResourcePacksInfo(byte[] payload, BedrockVersion version) {
        ByteReader in = new ByteReader(payload);
        assertFalse(in.readBoolean(), "must_accept は false");
        if (version.hasAddons()) {
            assertFalse(in.readBoolean(), "has_addons は false");
        }
        assertFalse(in.readBoolean(), "has_scripts は false");
        if (version.hasDisableVibrantVisuals()) {
            assertFalse(in.readBoolean(), "disable_vibrant_visuals は false");
        }
        if (version.hasWorldTemplate()) {
            in.readBytes(16); // world template uuid（空）
            assertEquals("", BedrockVarInt.readString(in), "world template version は空");
        }
        if (version.hasForceServerPacks()) {
            assertFalse(in.readBoolean(), "force_server_packs は false");
        }
        if (version.hasBehaviourPacks()) {
            assertEquals(0, in.readLittleUnsignedShort(), "behaviour packs は 0");
        }
        if (version.resourcePacksCountIsVarint()) {
            assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "texture packs は 0");
        } else {
            assertEquals(0, in.readLittleUnsignedShort(), "texture packs は 0");
        }
        if (version.hasResourcePackLinks()) {
            assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "resource pack links は 0");
        }
        assertEquals(0, in.remaining(),
                "ResourcePacksInfo は最後まで読み切れること（" + version.displayName() + "）");
    }

    @Test
    void resourcePacksInfoParsesForEveryShape() {
        BedrockVersion[] shapes = {BedrockVersion.V1_20_0, BedrockVersion.V1_20_30,
                BedrockVersion.V1_20_70, BedrockVersion.V1_21_0,
                BedrockVersion.V1_21_30, BedrockVersion.V1_21_50,
                BedrockVersion.V1_21_90, BedrockVersion.V1_26_0,
                BedrockVersion.V1_26_30, BedrockVersion.V1_26_50};
        for (BedrockVersion version : shapes) {
            parseResourcePacksInfo(BedrockPackets.encodeResourcePacksInfo(version),
                    version);
        }
    }

    /** ResourcePackStack を読む。空パックの既定値を確認する。 */
    static void parseResourcePackStack(byte[] payload, BedrockVersion version) {
        ByteReader in = new ByteReader(payload);
        assertFalse(in.readBoolean(), "must_accept は false");
        if (version.hasBehaviorPacksInStack()) {
            assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "behaviour packs は 0");
        }
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "resource packs は 0");
        assertEquals(version.displayName(), BedrockVarInt.readString(in),
                "game_version はクライアントの表示名");
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "experiments は 0");
        assertFalse(in.readBoolean(), "experiments previously used は false");
        if (version.hasEditorPacksInStack()) {
            assertFalse(in.readBoolean(), "has_editor_packs は false");
        }
        assertEquals(0, in.remaining(),
                "ResourcePackStack は最後まで読み切れること（" + version.displayName() + "）");
    }

    @Test
    void resourcePackStackParsesForEveryShape() {
        BedrockVersion[] shapes = {BedrockVersion.V1_20_0, BedrockVersion.V1_20_70,
                BedrockVersion.V1_21_0, BedrockVersion.V1_21_90,
                BedrockVersion.V1_21_130, BedrockVersion.V1_26_0,
                BedrockVersion.V1_26_50};
        for (BedrockVersion version : shapes) {
            parseResourcePackStack(BedrockPackets.encodeResourcePackStack(version),
                    version);
        }
    }

    @Test
    void legacyResourcePackOverloadsStillMatch1_20_50() {
        assertEquals(BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_20_50).length,
                BedrockPackets.encodeResourcePackStack().length);
        assertEquals(BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_20_50).length,
                BedrockPackets.encodeResourcePacksInfo().length);
    }

    // ---- Item ----

    /** Item 1 個（従来形式）を読む。 */
    private static void assertLegacyItem(ByteReader in, int networkId, int count,
            int metadata, int blockRuntimeId) {
        assertEquals(networkId, BedrockVarInt.readZigzag32(in), "network_id");
        assertEquals(count, in.readLittleUnsignedShort(), "count");
        assertEquals(metadata, BedrockVarInt.readUnsignedVarInt(in), "metadata");
        assertEquals(0, in.readUnsignedByte(), "has_stack_id は 0");
        assertEquals(blockRuntimeId, BedrockVarInt.readZigzag32(in), "block_runtime_id");
        long extraLength = BedrockVarInt.readUnsignedVarInt(in);
        assertEquals(10, extraLength, "extra の長さ（盾以外）");
        in.readBytes(10);
    }

    /** Item 1 個（ItemV4 形式）を読む。 */
    private static void assertItemV4Item(ByteReader in, int networkId, int count,
            int metadata, int blockRuntimeId) {
        assertEquals(networkId, in.readLittleShort(), "network_id（li16）");
        assertEquals(count, in.readLittleUnsignedShort(), "count");
        assertEquals(metadata, BedrockVarInt.readUnsignedVarInt(in), "metadata");
        assertFalse(in.readBoolean(), "has_stack_id は false");
        assertEquals(blockRuntimeId, BedrockVarInt.readUnsignedVarInt(in),
                "block_runtime_id（varint）");
        long extraLength = BedrockVarInt.readUnsignedVarInt(in);
        if (networkId == 0) {
            assertEquals(0, extraLength, "air の extra は長さ 0");
        } else {
            assertEquals(10, extraLength, "extra の長さ（盾以外）");
            in.readBytes(10);
        }
    }

    @Test
    void itemV4RoundTrip() {
        BedrockItem item = new BedrockItem(319, 5, 123, 1);
        ByteWriter out = new ByteWriter();
        BedrockPackets.writeItem(out, item, true);
        ByteReader in = new ByteReader(out.bytes());
        assertItemV4Item(in, 319, 5, 1, 123);
        assertEquals(0, in.remaining());

        // legacy 形式も同じ中身で書ける。
        out = new ByteWriter();
        BedrockPackets.writeItem(out, item);
        in = new ByteReader(out.bytes());
        assertLegacyItem(in, 319, 5, 1, 123);
        assertEquals(0, in.remaining());
    }

    @Test
    void airItemShapes() {
        // ItemV4 の air は 8 バイト（extra 長 0 を含む全項目）。
        ByteWriter airV4Out = new ByteWriter();
        BedrockPackets.writeAirItem(airV4Out, true);
        byte[] v4 = airV4Out.bytes();
        assertEquals(8, v4.length, "ItemV4 の air は 8 バイト");
        ByteReader in = new ByteReader(v4);
        assertItemV4Item(in, 0, 0, 0, 0);
        assertEquals(0, in.remaining());

        // 従来形式の air は zigzag32 0 の 1 バイト。
        ByteWriter airLegacyOut = new ByteWriter();
        BedrockPackets.writeAirItem(airLegacyOut);
        byte[] legacy = airLegacyOut.bytes();
        assertEquals(1, legacy.length, "従来形式の air は 1 バイト");
        assertEquals(0, BedrockVarInt.readZigzag32(new ByteReader(legacy)));
    }

    @Test
    void mobEquipmentUsesItemV4From1_26_20() {
        BedrockItem item = new BedrockItem(319, 1, 0, 0);
        byte[] old = BedrockPackets.encodeMobEquipment(42, item, 0, 0,
                BedrockVersion.V1_20_50);
        ByteReader in = new ByteReader(old);
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in));
        assertLegacyItem(in, 319, 1, 0, 0);
        assertEquals(0, in.readUnsignedByte(), "slot");
        assertEquals(0, in.readUnsignedByte(), "selected slot");
        assertEquals(0, in.readUnsignedByte(), "window id");
        assertEquals(0, in.remaining());

        byte[] v4 = BedrockPackets.encodeMobEquipment(42, item, 0, 0,
                BedrockVersion.V1_26_20);
        in = new ByteReader(v4);
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in));
        assertItemV4Item(in, 319, 1, 0, 0);
        assertEquals(3, in.remaining(), "slot・selected slot・window id");
    }

    @Test
    void mobArmorEquipmentBodySlotAndItemV4() {
        BedrockItem air = BedrockItem.air();
        BedrockItem helmet = new BedrockItem(318, 1, 0, 0);
        // 1.20.50：4 個・従来形式。
        byte[] old = BedrockPackets.encodeMobArmorEquipment(42, helmet, air, air, air,
                air, BedrockVersion.V1_20_50);
        ByteReader in = new ByteReader(old);
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in));
        assertLegacyItem(in, 318, 1, 0, 0);
        for (int i = 0; i < 3; i++) {
            assertEquals(0, BedrockVarInt.readZigzag32(in), "air は 1 バイト");
        }
        assertEquals(0, in.remaining(), "1.20.50 は body 枠なし");

        // 1.21.20：5 個（body 付き）・従来形式。
        BedrockItem body = new BedrockItem(359, 1, 0, 0);
        byte[] withBody = BedrockPackets.encodeMobArmorEquipment(42, helmet, air, air,
                air, body, BedrockVersion.V1_21_20);
        in = new ByteReader(withBody);
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in));
        assertLegacyItem(in, 318, 1, 0, 0);
        for (int i = 0; i < 3; i++) {
            assertEquals(0, BedrockVarInt.readZigzag32(in));
        }
        assertLegacyItem(in, 359, 1, 0, 0);
        assertEquals(0, in.remaining(), "1.21.20 は body 枠付き");

        // 26.30：5 個（body 付き）・ItemV4。
        byte[] v4 = BedrockPackets.encodeMobArmorEquipment(42, helmet, air, air, air,
                air, BedrockVersion.V1_26_30);
        in = new ByteReader(v4);
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in));
        assertItemV4Item(in, 318, 1, 0, 0);
        for (int i = 0; i < 4; i++) {
            assertItemV4Item(in, 0, 0, 0, 0);
        }
        assertEquals(0, in.remaining(), "26.30 は body 枠付き・ItemV4");
    }

    @Test
    void addItemEntityUsesItemV4From1_26_40() {
        BedrockItem item = new BedrockItem(319, 3, 0, 0);
        // エンティティデータ（中身はこのテストでは形状のみ確認する）。
        BedrockEntityTranslator.BedrockEntityData data =
                BedrockEntityTranslator.defaultData(2);
        byte[] old = BedrockPackets.encodeAddItemEntity(42, item,
                0, 64, 0, 0, 0, 0, data, BedrockVersion.V1_20_50);
        ByteReader in = new ByteReader(old);
        assertEquals(42, BedrockVarInt.readZigzag64(in));
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in));
        assertLegacyItem(in, 319, 3, 0, 0);

        byte[] v4 = BedrockPackets.encodeAddItemEntity(42, item,
                0, 64, 0, 0, 0, 0, data, BedrockVersion.V1_26_40);
        in = new ByteReader(v4);
        assertEquals(42, BedrockVarInt.readZigzag64(in));
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in));
        assertItemV4Item(in, 319, 3, 0, 0);
    }

    // ---- InventoryTransaction ----

    /** 座標 6 個分（player pos・click pos）を書く。 */
    private static void writePositions(ByteWriter out) {
        for (int i = 0; i < 6; i++) {
            out.writeLittleFloat(0);
        }
    }

    /** V1 形式（1.26.20 まで）の取引ペイロードを作る。 */
    private static byte[] buildV1Transaction(int legacyRequestId, int transactionType,
            boolean withLegacyEntries, boolean withAction) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, legacyRequestId);
        if (legacyRequestId != 0 && withLegacyEntries) {
            BedrockVarInt.writeUnsignedVarInt(out, 1); // legacy 1 件
            out.writeByte(0); // container id
            BedrockVarInt.writeUnsignedVarInt(out, 1); // changed slots 1 件
            out.writeByte(3); // slot id
        }
        BedrockVarInt.writeVarInt(out, transactionType);
        if (withAction) {
            BedrockVarInt.writeUnsignedVarInt(out, 1); // actions 1 件
            BedrockVarInt.writeVarInt(out, 0); // source type: container
            BedrockVarInt.writeZigzag32(out, 9); // window id
            BedrockVarInt.writeUnsignedVarInt(out, 2); // slot
            BedrockPackets.writeAirItem(out); // old item
            BedrockPackets.writeAirItem(out); // new item
        } else {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // actions なし
        }
        if (transactionType == BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY) {
            BedrockVarInt.writeVarLong(out, 7); // entity runtime id
            BedrockVarInt.writeVarInt(out, 1); // action type: attack
            BedrockVarInt.writeZigzag32(out, 0); // hotbar slot
            BedrockPackets.writeAirItem(out); // held item
            writePositions(out);
        }
        return out.bytes();
    }

    /** V2 形式（26.30）の取引 action 1 件を書く。 */
    private static void writeV2Action(ByteWriter out) {
        BedrockVarInt.writeVarInt(out, 0); // source type: container
        out.writeBoolean(true); // container presence
        out.writeBoolean(true); // window id あり
        out.writeByte(5); // window id (i8)
        out.writeBoolean(false); // flag presence なし
        BedrockVarInt.writeUnsignedVarInt(out, 2); // slot
        BedrockPackets.writeAirItem(out, true); // old item (ItemV4)
        BedrockPackets.writeAirItem(out, true); // new item (ItemV4)
    }

    /** V2 形式（26.30）の取引ペイロードを作る。 */
    private static byte[] buildV2Transaction(int transactionType) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, 0); // legacy request id
        out.writeBoolean(true); // legacy_transactions あり
        BedrockVarInt.writeUnsignedVarInt(out, 0); // legacy 0 件
        BedrockVarInt.writeVarInt(out, transactionType);
        BedrockVarInt.writeUnsignedVarInt(out, 1); // actions 1 件
        writeV2Action(out);
        if (transactionType == BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY) {
            BedrockVarInt.writeVarLong(out, 7); // entity runtime id
            BedrockVarInt.writeZigzag32(out, 1); // action type: attack（zigzag32）
            BedrockVarInt.writeZigzag32(out, 0); // hotbar slot
            BedrockPackets.writeAirItem(out, true); // held item (ItemV4)
            writePositions(out);
        }
        return out.bytes();
    }

    /** V3 形式（26.40 以降）の取引ペイロードを作る。 */
    private static byte[] buildV3Transaction(boolean withType) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, 0); // legacy request id
        out.writeBoolean(true); // legacy_transactions あり
        BedrockVarInt.writeUnsignedVarInt(out, 0); // legacy 0 件
        out.writeBoolean(withType); // transaction_type あり
        if (withType) {
            BedrockVarInt.writeVarInt(out,
                    BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY);
        }
        out.writeBoolean(withType); // actions あり
        if (withType) {
            BedrockVarInt.writeUnsignedVarInt(out, 1); // actions 1 件
            writeV2Action(out);
            BedrockVarInt.writeVarLong(out, 7); // entity runtime id
            BedrockVarInt.writeZigzag32(out, 1); // action type: attack
            BedrockVarInt.writeZigzag32(out, 0); // hotbar slot
            BedrockPackets.writeAirItem(out, true); // held item (ItemV4)
            writePositions(out);
        }
        return out.bytes();
    }

    @Test
    void decodeInventoryTransactionV1() {
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV1Transaction(0, BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY,
                        false, false),
                BedrockVersion.V1_20_50);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV1WithLegacyAndAction() {
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV1Transaction(5, BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY,
                        true, true),
                BedrockVersion.V1_20_50);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV1OtherTypeIsNull() {
        assertNull(BedrockPackets.decodeInventoryTransaction(
                buildV1Transaction(0, 0, false, false), BedrockVersion.V1_20_50));
    }

    @Test
    void decodeInventoryTransactionV2() {
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV2Transaction(BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY),
                BedrockVersion.V1_26_30);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV3() {
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV3Transaction(true), BedrockVersion.V1_26_50);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV3WithoutTypeIsNull() {
        assertNull(BedrockPackets.decodeInventoryTransaction(
                buildV3Transaction(false), BedrockVersion.V1_26_50));
    }

    @Test
    void decodeInventoryTransactionLegacyOverloadIsV1() {
        // 旧 1 引数版は V1（1.20.50）として読む。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV1Transaction(0, BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY,
                        false, false));
        assertEquals(7, use.entityRuntimeId());
    }
}
