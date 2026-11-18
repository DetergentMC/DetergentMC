package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockEntityTranslator.BedrockItem;
import io.detergentmc.bedrock.BedrockPackets.InventoryTransactionUse;
import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.util.UUID;
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
        if (version.hasStartGameLu64Seed()) {
            in.readLittleLong(); // seed（503 以降は lu64）
        } else {
            BedrockVarInt.readZigzag32(in); // seed（486 は zigzag32）
        }
        if (version.hasStartGame407Fields()) {
            in.readLittleShort(); // biome type（407 で新規。390 には無い）
            BedrockVarInt.readString(in); // biome name（407 で新規。390 には無い）
        }
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
        if (version.hasEditorWorld()) {
            BedrockVarInt.readZigzag32(in); // editor world type
        }
        if (version.hasStartGame582Fields()) {
            in.readBoolean(); // created in editor
            in.readBoolean(); // exported from editor
        }
        if (version.hasEditorLevelMigrationVersion()) {
            assertEquals(0, in.readUnsignedByte(),
                    "editor level migration version は legacy（2225 で新規）");
        }
        BedrockVarInt.readZigzag32(in); // day cycle stop time
        BedrockVarInt.readZigzag32(in); // edu offer
        in.readBoolean(); // edu features enabled
        if (version.hasStartGame407Fields()) {
            BedrockVarInt.readString(in); // edu product uuid（407 で新規。390 には無い）
        }
        in.readLittleFloat(); // rain level
        in.readLittleFloat(); // lightning level
        if (version.startGameUsesV291LevelSettings()) {
            // 313 の LevelSettings（_v291＋_v313 の末尾 2 bool）：
            // 放送モードは bool（NO_MULTI_PLAY のとき false）と
            // zigzag32 の組で、trustingPlayers がある
            // （BE-NOTES.md §35）。291 は末尾 2 bool が無い純粋な
            // _v291（startGameUsesV313LevelSettings()。§36）。
            assertTrue(in.readBoolean(), "multiplayer は true");
            assertTrue(in.readBoolean(), "broadcast to lan は true");
            assertFalse(in.readBoolean(), "xbl broadcast bool は false");
            assertTrue(in.readBoolean(), "commands enabled は true");
            assertFalse(in.readBoolean(), "texturepacks required は false");
            assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "gamerules は 0");
            assertFalse(in.readBoolean(), "bonus chest は false");
            assertFalse(in.readBoolean(), "map enabled は false");
            assertFalse(in.readBoolean(), "trusting players は false");
            assertEquals(1, BedrockVarInt.readZigzag32(in), "permission level は member");
            assertEquals(0, BedrockVarInt.readZigzag32(in), "xbl broadcast mode は 0");
            assertEquals(4, in.readLittleInt(), "server chunk tick range は 4");
            assertFalse(in.readBoolean(), "platform broadcast bool は false");
            assertEquals(0, BedrockVarInt.readZigzag32(in), "platform broadcast mode は 0");
            assertFalse(in.readBoolean(), "xbl intent bool は false");
            assertFalse(in.readBoolean(), "locked behavior pack は false");
            assertFalse(in.readBoolean(), "locked resource pack は false");
            assertFalse(in.readBoolean(), "from locked world template は false");
            assertFalse(in.readBoolean(), "msa gamertags only は false");
            if (version.startGameUsesV313LevelSettings()) {
                assertFalse(in.readBoolean(), "from world template（v313 追加分）は false");
                assertFalse(in.readBoolean(), "from locked world template（v313 追加分）は false");
            }
        } else {
        in.readBoolean(); // confirmed platform locked content
        in.readBoolean(); // multiplayer
        in.readBoolean(); // broadcast to lan
        BedrockVarInt.readUnsignedVarInt(in); // xbox live broadcast mode
        BedrockVarInt.readUnsignedVarInt(in); // platform broadcast mode
        in.readBoolean(); // enable commands
        in.readBoolean(); // texturepacks required
        BedrockVarInt.readUnsignedVarInt(in); // gamerules
        if (version.hasStartGameExperiments()) {
            BedrockVarInt.readUnsignedVarInt(in); // experiments（419 で新規。408 には無い）
            in.readBoolean(); // experiments previously used（419 で新規。408 には無い）
        }
        in.readBoolean(); // bonus chest
        in.readBoolean(); // map enabled
        if (version.hasPermissionLevelMapper()) {
            assertEquals(1, in.readUnsignedByte(), "permission level は member");
        } else {
            assertEquals(1, BedrockVarInt.readZigzag32(in), "permission level は member（527 は zigzag32）");
        }
        in.readLittleInt(); // server chunk tick range
        in.readBoolean(); // locked behavior pack
        in.readBoolean(); // locked resource pack
        in.readBoolean(); // from locked world template
        in.readBoolean(); // msa gamertags only
        in.readBoolean(); // from world template
        in.readBoolean(); // world template option locked
        if (version.hasStartGameOnlySpawningV1Villagers()) {
            in.readBoolean(); // only spawn v1 villagers（361 で新規。354 には無い）
        }
        }
        if (version.hasStartGame544Fields()) {
            in.readBoolean(); // persona disabled
            in.readBoolean(); // custom skins disabled
        }
        if (version.hasEmoteChatMuted()) {
            in.readBoolean(); // emote chat muted
        }
        String gameVersion = version.hasStartGameVanillaVersion()
                ? BedrockVarInt.readString(in) : null;
        if (version.hasStartGame407Fields()) {
            in.readLittleInt(); // limited world width（407 で新規。390 には無い）
            in.readLittleInt(); // limited world length（407 で新規。390 には無い）
            in.readBoolean(); // is new nether（407 で新規。390 には無い）
        }
        if (version.hasStartGameEduResourceUri()) {
            BedrockVarInt.readString(in); // edu resource uri button（465 以降。448 には無い）
            BedrockVarInt.readString(in); // edu resource uri link（465 以降。448 には無い）
        }
        if (version.hasStartGame407Fields()) {
            in.readBoolean(); // experimental gameplay override（407 で新規。390 には無い）
        }
        if (version.hasStartGame544Fields()) {
            in.readUnsignedByte(); // chat restriction level
            in.readBoolean(); // disable player interactions
        }
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
        if (version.hasStartGameRewindFields()) {
            BedrockVarInt.readZigzag32(in); // rewind history size（428 で新規。422 には無い）
            in.readBoolean(); // server authoritative block breaking（428 で新規。422 には無い）
        }
        in.readLittleLong(); // current tick
        BedrockVarInt.readZigzag32(in); // enchantment seed
        if (version.hasStartGameBlockProperties()) {
            long blockProperties = BedrockVarInt.readUnsignedVarInt(in);
            if (palette != null) {
                assertEquals(palette.count(), blockProperties, "パレットの件数");
                in.readBytes(palette.rawPaletteBytes().length);
            } else {
                assertEquals(0, blockProperties, "パレットなし");
            }
        } else if (version.hasStartGameLegacyPaletteArray()) {
            if (palette != null) {
                // 配列形式：件数＋要素の並びそのまま（361 は name／meta／id、
                // 354 は name／meta のみで中身も 1.11.0 世代）
                byte[] expected = version.hasStartGameLegacyPaletteEntryId()
                        ? BedrockLegacyBlockPalette.load().rawArrayBytes()
                        : BedrockLegacyBlockPalette.load1_11_0().rawArrayBytes();
                assertArrayEquals(expected, in.readBytes(expected.length),
                        "レガシーブロックパレットは配列形式");
            } else {
                assertEquals(0, BedrockVarInt.readUnsignedVarInt(in),
                        "空パレットの件数（361 の配列形式）");
            }
        } else if (palette != null) {
            // 408 の旧形式：パレット全体が単一の NBT リストタグ
            byte[] expected = palette.rawPaletteListTagBytes();
            assertArrayEquals(expected, in.readBytes(expected.length),
                    "408 のブロックパレットは単一の NBT リストタグ");
        } else {
            // 408 の旧形式でパレットなし：空のリストタグ（0x09＋0x0A＋件数 0）
            assertEquals(0x09, in.readUnsignedByte(), "リストタグの型バイト");
            assertEquals(0x0A, in.readUnsignedByte(), "リストタグの要素型");
            assertEquals(0, BedrockVarInt.readZigzag32(in), "空パレットの件数");
        }
        int itemstatesCount = -1;
        if (version.hasItemstates()) {
            itemstatesCount = (int) BedrockVarInt.readUnsignedVarInt(in);
        }
        BedrockVarInt.readString(in); // multiplayer correlation id
        if (version.hasStartGame407Fields()) {
            in.readBoolean(); // server authoritative inventory（407 で新規。390 には無い）
        }
        if (version.hasStartGameEngine()) {
            BedrockVarInt.readString(in); // engine（440 で新規。431 には無い）
        }
        if (version.hasStartGame527Fields()) {
            assertEquals(0x0A, in.readUnsignedByte()); // property data: compound
            assertEquals(0, in.readUnsignedByte()); // 名前長 0
            assertEquals(0x00, in.readUnsignedByte()); // TAG_End
        }
        if (version.hasStartGameBlockPaletteChecksum()) {
            in.readLittleLong(); // block palette checksum
        }
        if (version.hasStartGame527Fields()) {
            in.readBytes(16); // world template id
        }
        if (version.hasStartGame544Fields()) {
            in.readBoolean(); // client side generation
        }
        if (version.hasStartGame582Fields()) {
            in.readBoolean(); // block network ids are hashes
        }
        boolean hasTickDeathSystemsField = version.hasTickDeathSystems();
        boolean tickDeathSystemsValue = false;
        if (hasTickDeathSystemsField) {
            tickDeathSystemsValue = in.readBoolean();
        }
        if (version.hasServerControlledSound()) {
            in.readBoolean(); // server controlled sound
        }
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
        BedrockVersion[] shapes = {BedrockVersion.V1_14_60, BedrockVersion.V1_16_61, BedrockVersion.V1_16_201, BedrockVersion.V1_16_210, BedrockVersion.V1_16_221, BedrockVersion.V1_17_11, BedrockVersion.V1_17_41, BedrockVersion.V1_18_2, BedrockVersion.V1_18_12, BedrockVersion.V1_18_33, BedrockVersion.V1_19_2, BedrockVersion.V1_19_11, BedrockVersion.V1_19_20, BedrockVersion.V1_19_22, BedrockVersion.V1_19_31, BedrockVersion.V1_19_41, BedrockVersion.V1_19_51, BedrockVersion.V1_19_62, BedrockVersion.V1_19_70, BedrockVersion.V1_19_83, BedrockVersion.V1_20_0,
                BedrockVersion.V1_20_80,
                BedrockVersion.V1_21_0, BedrockVersion.V1_21_50,
                BedrockVersion.V1_21_90, BedrockVersion.V1_21_100,
                BedrockVersion.V1_21_124, BedrockVersion.V1_21_130, BedrockVersion.V1_26_0,
                BedrockVersion.V1_26_30, BedrockVersion.V1_26_50,
                BedrockVersion.V1_26_52, BedrockVersion.V1_26_60,
                BedrockVersion.V1_26_65};
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
    void startGame422OmitsRewindFields() {
        // 422 の StartGame は 57 フィールドで rewind_history_size・
        // server_authoritative_block_breaking が無い。428 は 59 フィールドで
        // この 2 フィールドがある。値はどちらも 0／false で、rewind_history_size
        // は zigzag32 の 0＝1 バイト、server_authoritative_block_breaking は
        // bool＝1 バイトのため、game version を同じ文字列にして比較すると
        // 422 は 428 よりちょうど 2 バイト短い。
        BedrockPackets.StartGameInfo info422 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        BedrockPackets.StartGameInfo info428 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload422 = BedrockPackets.encodeStartGame(info422, null, BedrockVersion.V1_16_201);
        byte[] payload428 = BedrockPackets.encodeStartGame(info428, null, BedrockVersion.V1_16_210);
        assertEquals(payload428.length - 2, payload422.length,
                "422 の StartGame は 428 より 2 バイト短い（rewind 2 フィールド分）");
        // 422 のペイロードも最後まで読めること（rewind 欄を読まない）。
        ParsedStartGame parsed = parseStartGame(payload422, BedrockVersion.V1_16_201, null);
        assertEquals("x", parsed.gameVersion());
        assertEquals("world", parsed.worldName());
    }

    @Test
    void startGame419Equals422() {
        // 419 の StartGame は 422 と同じ StartGameSerializer_v419
        // （CloudburstMC Protocol の v419 コーデック）で、フィールド順・型が
        // 422 の protocol.json の 57 フィールドと一致する。419 と 422 で
        // 述語の値がすべて同じため、game version を同じ文字列にして比較すると
        // ペイロードはバイト同一になる（BE-NOTES.md §25）。
        // ただし multiplayer correlation id は encode のたびに
        // UUID.randomUUID() で生成されるため、その欄（末尾の
        // server_authoritative_inventory の直前にある li32 長＋36 バイトの
        // 文字列）だけはマスクして比較する。
        BedrockPackets.StartGameInfo info419 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        BedrockPackets.StartGameInfo info422 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload419 = BedrockPackets.encodeStartGame(info419, null, BedrockVersion.V1_16_101);
        byte[] payload422 = BedrockPackets.encodeStartGame(info422, null, BedrockVersion.V1_16_201);
        assertEquals(payload422.length, payload419.length,
                "419 の StartGame は 422 と同じ長さ");
        int tail = 4 + 36 + 1; // correlation id（li32 長＋UUID 文字列）＋最終 bool
        for (int i = payload419.length - tail; i < payload419.length - 1; i++) {
            payload419[i] = 0;
            payload422[i] = 0;
        }
        assertArrayEquals(payload422, payload419,
                "419 の StartGame は 422 とバイト同一（correlation id を除く）");
        // 419 のペイロードが 419 の形式で最後まで読めること。
        ParsedStartGame parsed = parseStartGame(
                BedrockPackets.encodeStartGame(info419, null, BedrockVersion.V1_16_101),
                BedrockVersion.V1_16_101, null);
        assertEquals("x", parsed.gameVersion());
        assertEquals("world", parsed.worldName());
    }

    @Test
    void resourcePacks419Equals422() {
        // ResourcePacksInfo は v422 でシリアライザが差し替わったが、差分は
        // リソースパックエントリ末尾の raytracing フラグだけで、この実装は
        // 空の一覧しか書かないため 419 と 422 でバイト同一になる
        // （CloudburstMC Protocol の ResourcePacksInfoSerializer_v332／
        // _v422。BE-NOTES.md §25）。
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_201),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_101),
                "419 の ResourcePacksInfo は 422 とバイト同一（空の一覧のため）");
        parseResourcePacksInfo(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_101),
                BedrockVersion.V1_16_101);
        // ResourcePackStack は game version の文字列だけが違う
        // （"1.16.101" と "1.16.201"）。どちらの形式でも最後まで読めること。
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_16_101),
                BedrockVersion.V1_16_101);
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_16_201),
                BedrockVersion.V1_16_201);
    }

    @Test
    void startGame408OmitsExperiments() {
        // 408 の StartGame には experiments の 2 フィールドが無い
        // （StartGameSerializer_v407。419 の _v419 が挿入した）。
        // パレットなしで比べると、408 は experiments 分の 2 バイトが減る
        // 一方、ブロックパレット欄が「件数 varint 0＝1 バイト」から
        // 「空の NBT リストタグ＝3 バイト」に変わって 2 バイト増えるため、
        // game version を同じ文字列にすると全長は 419 と同じになる。
        BedrockPackets.StartGameInfo info408 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        BedrockPackets.StartGameInfo info419 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload408 = BedrockPackets.encodeStartGame(info408, null, BedrockVersion.V1_16_61);
        byte[] payload419 = BedrockPackets.encodeStartGame(info419, null, BedrockVersion.V1_16_101);
        assertEquals(payload419.length, payload408.length,
                "408 の StartGame は experiments −2 とパレット欄 +2 で 419 と同じ長さ");
        // 408 のペイロードが 408 の形式（experiments なし・空リストタグ）
        // で最後まで読めること。
        ParsedStartGame parsed = parseStartGame(payload408, BedrockVersion.V1_16_61, null);
        assertEquals("x", parsed.gameVersion());
        assertEquals("world", parsed.worldName());
    }

    @Test
    void startGame408BlockPaletteIsListTag() throws Exception {
        // 408 のブロックパレットは blockProperties 形式ではなく、
        // パレット全体を単一の NBT リストタグとして送る
        // （StartGameSerializer_v388 の helper.writeTag。BE-NOTES.md §26）。
        // パーサがタグのバイト列そのものを rawPaletteListTagBytes() と
        // 照合して最後まで読めることで検証する。長さの差は、パレット
        // 14,127 件で 419 形式＝件数 varint（2 バイト）＋生バイト列 R、
        // 408 形式＝5 バイト（型・要素型・件数の zigzag varint 3 バイト）＋
        // （R − 2×14,127）で、差は 28,251 バイト。experiments の
        // 2 バイトを合わせて 419 は 408 より 28,253 バイト長い。
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        BedrockPackets.StartGameInfo info408 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        BedrockPackets.StartGameInfo info419 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload408 = BedrockPackets.encodeStartGame(info408, palette, BedrockVersion.V1_16_61);
        byte[] payload419 = BedrockPackets.encodeStartGame(info419, palette, BedrockVersion.V1_16_101);
        assertEquals(28253, payload419.length - payload408.length,
                "419 と 408 の StartGame の長さの差（パレット形式＋experiments）");
        ParsedStartGame parsed = parseStartGame(payload408, BedrockVersion.V1_16_61, palette);
        assertEquals("x", parsed.gameVersion());
    }

    @Test
    void resourcePacks408Equals419() {
        // ResourcePacksInfo の実効シリアライザは 408 も 419 も v332 で
        // 同一のため、空の一覧ではバイト同一になる（BE-NOTES.md §26）。
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_101),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_61),
                "408 の ResourcePacksInfo は 419 とバイト同一（空の一覧のため）");
        parseResourcePacksInfo(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_61),
                BedrockVersion.V1_16_61);
        // ResourcePackStack は 408 が旧形式（legacy experiment フラグ＋
        // gameVersion）。どちらの形式でも最後まで読めること。
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_16_61),
                BedrockVersion.V1_16_61);
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_16_101),
                BedrockVersion.V1_16_101);
    }

    @Test
    void tickFields408Omitted() {
        // MovePlayer・SetEntityData・UpdateAttributes の末尾 tick は
        // 419 で追加された（各 _v419 シリアライザ）。408 では書かないため、
        // 同じ引数では 408 のペイロードがちょうど 1 バイト短い。
        byte[] move408 = BedrockPackets.encodeMovePlayerTeleport(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, BedrockVersion.V1_16_61);
        byte[] move419 = BedrockPackets.encodeMovePlayerTeleport(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, BedrockVersion.V1_16_101);
        assertEquals(move419.length - 1, move408.length,
                "408 の MovePlayer（テレポート）には末尾 tick が無い");
        byte[] moveEntity408 = BedrockPackets.encodeMovePlayerEntity(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, true, BedrockVersion.V1_16_61);
        byte[] moveEntity419 = BedrockPackets.encodeMovePlayerEntity(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, true, BedrockVersion.V1_16_101);
        assertEquals(moveEntity419.length - 1, moveEntity408.length,
                "408 の MovePlayer（他プレイヤー）には末尾 tick が無い");
        byte[] data408 = BedrockPackets.encodeSetEntityData(7, null, BedrockVersion.V1_16_61);
        byte[] data419 = BedrockPackets.encodeSetEntityData(7, null, BedrockVersion.V1_16_101);
        assertEquals(data419.length - 1, data408.length,
                "408 の SetEntityData には末尾 tick が無い");
        byte[] attr408 = BedrockPackets.encodeUpdateAttributes(7, 20.0f, 20.0f,
                BedrockVersion.V1_16_61);
        byte[] attr419 = BedrockPackets.encodeUpdateAttributes(7, 20.0f, 20.0f,
                BedrockVersion.V1_16_101);
        assertEquals(attr419.length - 1, attr408.length,
                "408 の UpdateAttributes には末尾 tick が無い");
    }

    @Test
    void startGame407Equals408() throws Exception {
        // 407 の実効定義は CloudburstMC Protocol の v407 コーデック
        // そのもので、408 はその CODEC を流用していたため、StartGame の
        // 形式は 408 と同一（BE-NOTES.md §27）。StartGame には呼び出し
        // ごとに変わるランダムな UUID が含まれるためバイト列そのものは
        // 比較できず、長さの一致と、407 のペイロードが 407 の形式で
        // 最後まで読めることで確認する。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload407 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_16_10);
        byte[] payload408 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_16_61);
        assertEquals(payload408.length, payload407.length,
                "407 の StartGame は 408 と同じ長さ（パレットなし）");
        ParsedStartGame parsed = parseStartGame(payload407, BedrockVersion.V1_16_10, null);
        assertEquals("x", parsed.gameVersion());
        assertEquals("world", parsed.worldName());
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] pal407 = BedrockPackets.encodeStartGame(info, palette, BedrockVersion.V1_16_10);
        byte[] pal408 = BedrockPackets.encodeStartGame(info, palette, BedrockVersion.V1_16_61);
        assertEquals(pal408.length, pal407.length,
                "407 の StartGame は 408 と同じ長さ（実パレット）");
        ParsedStartGame parsedPal = parseStartGame(pal407, BedrockVersion.V1_16_10, palette);
        assertEquals("x", parsedPal.gameVersion());
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void resourcePacks407Equals408() {
        // ResourcePacksInfo の実効シリアライザは 407 も 408 も v332 で
        // 同一のため、空の一覧ではバイト同一になる（BE-NOTES.md §27）。
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_61),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_10),
                "407 の ResourcePacksInfo は 408 とバイト同一（空の一覧のため）");
        parseResourcePacksInfo(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_10),
                BedrockVersion.V1_16_10);
        // ResourcePackStack は 407 も 408 と同じ旧形式（legacy
        // experiment フラグ＋gameVersion）。どちらの形式でも最後まで
        // 読めること。
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_16_10),
                BedrockVersion.V1_16_10);
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_16_61),
                BedrockVersion.V1_16_61);
    }

    @Test
    void tickFields407Omitted() {
        // MovePlayer・SetEntityData・UpdateAttributes の末尾 tick は
        // 419 で追加された。407 では書かないため、同じ引数では 407 の
        // ペイロードが 419 よりちょうど 1 バイト短い（408 と同じ。
        // BE-NOTES.md §27）。
        byte[] move407 = BedrockPackets.encodeMovePlayerTeleport(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, BedrockVersion.V1_16_10);
        byte[] move419 = BedrockPackets.encodeMovePlayerTeleport(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, BedrockVersion.V1_16_101);
        assertEquals(move419.length - 1, move407.length,
                "407 の MovePlayer（テレポート）には末尾 tick が無い");
        byte[] moveEntity407 = BedrockPackets.encodeMovePlayerEntity(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, true, BedrockVersion.V1_16_10);
        byte[] moveEntity419 = BedrockPackets.encodeMovePlayerEntity(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, true, BedrockVersion.V1_16_101);
        assertEquals(moveEntity419.length - 1, moveEntity407.length,
                "407 の MovePlayer（他プレイヤー）には末尾 tick が無い");
        byte[] data407 = BedrockPackets.encodeSetEntityData(7, null, BedrockVersion.V1_16_10);
        byte[] data419 = BedrockPackets.encodeSetEntityData(7, null, BedrockVersion.V1_16_101);
        assertEquals(data419.length - 1, data407.length,
                "407 の SetEntityData には末尾 tick が無い");
        byte[] attr407 = BedrockPackets.encodeUpdateAttributes(7, 20.0f, 20.0f,
                BedrockVersion.V1_16_10);
        byte[] attr419 = BedrockPackets.encodeUpdateAttributes(7, 20.0f, 20.0f,
                BedrockVersion.V1_16_101);
        assertEquals(attr419.length - 1, attr407.length,
                "407 の UpdateAttributes には末尾 tick が無い");
    }

    @Test
    void startGame390Omits407Fields() throws Exception {
        // 390 の StartGame（StartGameSerializer_v388）には v407 追加分が
        // 無い：spawn_biome_type（li16＝2 バイト）・custom_biome_name
        // （文字列＝li32 長 4＋"minecraft:plains" 16 バイト）・
        // education_production_id（空文字列＝4 バイト）・
        // limited_world_width／height（li32 ×2＝8 バイト）・
        // is_new_nether（1 バイト）・force_experimental_gameplay
        // （1 バイト）・server_authoritative_inventory（1 バイト）の
        // 合計 37 バイト分、390 は 407 より短い（BE-NOTES.md §28）。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload390 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_14_60);
        byte[] payload407 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_16_10);
        assertEquals(payload407.length - 37, payload390.length,
                "390 の StartGame は 407 より v407 追加分（37 バイト）だけ短い");
        ParsedStartGame parsed = parseStartGame(payload390, BedrockVersion.V1_14_60, null);
        assertEquals("x", parsed.gameVersion());
        assertEquals("world", parsed.worldName());
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] pal390 = BedrockPackets.encodeStartGame(info, palette, BedrockVersion.V1_14_60);
        byte[] pal407 = BedrockPackets.encodeStartGame(info, palette, BedrockVersion.V1_16_10);
        assertEquals(pal407.length - 37, pal390.length,
                "390 の StartGame は 407 より 37 バイト短い（実パレット）");
        ParsedStartGame parsedPal = parseStartGame(pal390, BedrockVersion.V1_14_60, palette);
        assertEquals("x", parsedPal.gameVersion());
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void startGame389Equals390() throws Exception {
        // 389 の StartGame も 390 と同じ StartGameSerializer_v388 が
        // 実効で、v390 は StartGame に触っていないため、同じ情報では
        // 全長が一致し、389 形式で最後まで読める（BE-NOTES.md §29）。
        // ペイロード内の UUID は呼び出しごとに乱数生成されるため、
        // バイト単位の同一性ではなく長さとパースで確認する
        // （startGame390Omits407Fields と同じ事情）。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload389 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_14_41);
        byte[] payload390 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_14_60);
        assertEquals(payload390.length, payload389.length,
                "389 の StartGame は 390 と全長が一致する");
        ParsedStartGame parsed = parseStartGame(payload389, BedrockVersion.V1_14_41, null);
        assertEquals("x", parsed.gameVersion());
        assertEquals("world", parsed.worldName());
    }

    @Test
    void startGame388Equals389() throws Exception {
        // 388 の StartGame は StartGameSerializer_v388 自身が実効で、
        // 389 も同じシリアライザを使うため、同じ情報では全長が一致し、
        // 388 形式で最後まで読める（BE-NOTES.md §30）。ペイロード内の
        // UUID は乱数のため、バイト単位ではなく長さとパースで確認する。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload388 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_13_3);
        byte[] payload389 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_14_41);
        assertEquals(payload389.length, payload388.length,
                "388 の StartGame は 389 と全長が一致する");
        ParsedStartGame parsed = parseStartGame(payload388, BedrockVersion.V1_13_3, null);
        assertEquals("x", parsed.gameVersion());
        assertEquals("world", parsed.worldName());
    }

    @Test
    void resourcePacks390Equals407() {
        // ResourcePacksInfo の実効シリアライザは 390 も 407 も v332 で
        // 同一のため、空の一覧ではバイト同一になる（BE-NOTES.md §28）。
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_16_10),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_14_60),
                "390 の ResourcePacksInfo は 407 とバイト同一（空の一覧のため）");
        parseResourcePacksInfo(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_14_60),
                BedrockVersion.V1_14_60);
        // ResourcePackStack は 390 も 407 と同じ旧形式（legacy
        // experiment フラグ＋gameVersion）。どちらの形式でも最後まで
        // 読めること。
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_14_60),
                BedrockVersion.V1_14_60);
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_16_10),
                BedrockVersion.V1_16_10);
    }

    @Test
    void resourcePacks389Equals390() {
        // ResourcePacksInfo の実効シリアライザは 389 も 390 も v332 世代、
        // ResourcePackStack はどちらも v388 で同一のため、389 の
        // ペイロードは 390 とバイト同一で、389 形式で最後まで読める
        // （BE-NOTES.md §29）。
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_14_60),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_14_41),
                "389 の ResourcePacksInfo は 390 とバイト同一（空の一覧のため）");
        // ResourcePackStack は gameVersion にバージョン自身の表示名が
        // 入るためバイト同一にはならない（resourcePacks390Equals407 と
        // 同じ事情）。389 形式で最後まで読めることだけ確認する。
        parseResourcePacksInfo(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_14_41),
                BedrockVersion.V1_14_41);
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_14_41),
                BedrockVersion.V1_14_41);
    }

    @Test
    void resourcePacks388Equals389() {
        // ResourcePacksInfo の実効シリアライザは 388 も 389 も v332 世代、
        // ResourcePackStack はどちらも v388 で同一のため、388 の
        // ペイロードは 389 とバイト同一で、388 形式で最後まで読める
        // （BE-NOTES.md §30）。
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_14_41),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_13_3),
                "388 の ResourcePacksInfo は 389 とバイト同一（空の一覧のため）");
        parseResourcePacksInfo(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_13_3),
                BedrockVersion.V1_13_3);
        parseResourcePackStack(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_13_3),
                BedrockVersion.V1_13_3);
    }

    @Test
    void startGame361DiffersFrom388() throws Exception {
        // 361 の StartGame は StartGameSerializer_v361 が実効で、388 と
        // 3 点違う：movement_authority 自体が無い、LevelSettings が
        // 31 フィールドで末尾の game version 文字列が無い、ブロック
        // パレットが配列形式（BE-NOTES.md §31）。同じ情報・パレット
        // なしでは、361 は 388 より movement（1 バイト）＋game version
        // 文字列（"x" で長さ 4 バイト＋1 バイト＝5 バイト）＋パレット
        // （空リストタグ 3 バイトに対し件数 0 の 1 バイトで 2 バイト）
        // ＝8 バイト短い。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload361 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_12_1);
        byte[] payload388 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_13_3);
        assertEquals(payload388.length - 8, payload361.length,
                "361 の StartGame は 388 より 8 バイト短い（movement・game version・パレット形式）");
        ParsedStartGame parsed = parseStartGame(payload361, BedrockVersion.V1_12_1, null);
        assertFalse(parsed.hasMovementAuthority(), "361 に movement_authority は無い");
        assertNull(parsed.gameVersion(), "361 の LevelSettings に game version 文字列は無い");
        assertEquals("world", parsed.worldName());
        // パレットありでは 361 の配列形式バイト列がそのまま入り、
        // 361 形式で最後まで読めること。
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] withPalette = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_12_1);
        ParsedStartGame parsedPal = parseStartGame(withPalette, BedrockVersion.V1_12_1, palette);
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void legacyBlockPalette361Contents() {
        // 361 の StartGame パレットは PMMP BedrockData bedrock-1.12.0
        // タグの実測（required_block_states.json＋block_id_map.json）
        // から生成した 3,311 件（BE-NOTES.md §31）。
        BedrockLegacyBlockPalette palette = BedrockLegacyBlockPalette.load();
        assertEquals(3311, palette.count(), "1.12.0 の (名前, メタ) 件数");
        assertEquals(new BedrockLegacyBlockPalette.Entry("minecraft:acacia_button", 0, 395),
                palette.entryAt(0), "先頭エントリ");
        assertEquals(new BedrockLegacyBlockPalette.Entry(
                "minecraft:yellow_glazed_terracotta", 7, 224),
                palette.entryAt(3310), "末尾エントリ");
    }

    @Test
    void startGame354DiffersFrom361() throws Exception {
        // 354 の StartGame は StartGameSerializer_v332＋トップレベル
        // _v291 が実効で、361 と 2 点違う：LevelSettings が 30 フィールド
        // で only spawn v1 villagers が無い、itemstates 自体が無い。
        // パレットはどちらも配列形式（要素と中身は違うが、パレットなし
        // では件数 0 の 1 バイトで同一）。同じ情報・パレットなしでは、
        // 354 は 361 より villagers（1 バイト）＋itemstates 件数
        // （1 バイト）＝2 バイト短い（BE-NOTES.md §32）。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload354 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_11_4);
        byte[] payload361 = BedrockPackets.encodeStartGame(info, null, BedrockVersion.V1_12_1);
        assertEquals(payload361.length - 2, payload354.length,
                "354 の StartGame は 361 より 2 バイト短い（villagers・itemstates）");
        ParsedStartGame parsed = parseStartGame(payload354, BedrockVersion.V1_11_4, null);
        assertNull(parsed.gameVersion(), "354 の LevelSettings に game version 文字列は無い");
        assertEquals(-1, parsed.itemstatesCount(), "354 に itemstates は無い");
        assertEquals("world", parsed.worldName());
        // パレットありでは 354 の配列形式バイト列（1.11.0 世代・
        // 要素に id 無し）がそのまま入り、354 形式で最後まで読めること。
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] withPalette = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_11_4);
        ParsedStartGame parsedPal = parseStartGame(withPalette, BedrockVersion.V1_11_4, palette);
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void legacyBlockPalette354Contents() {
        // 354 の StartGame パレットは PMMP BedrockData bedrock-1.11.0
        // タグの実測（required_block_states.json＋block_id_map.json）
        // から生成した 3,183 件で、要素に id は無い（BE-NOTES.md §32）。
        BedrockLegacyBlockPalette palette = BedrockLegacyBlockPalette.load1_11_0();
        assertEquals(3183, palette.count(), "1.11.0 の (名前, メタ) 件数");
        assertEquals(new BedrockLegacyBlockPalette.Entry("minecraft:acacia_button", 0, -1),
                palette.entryAt(0), "先頭エントリ");
        assertEquals(new BedrockLegacyBlockPalette.Entry(
                "minecraft:yellow_glazed_terracotta", 7, -1),
                palette.entryAt(3182), "末尾エントリ");
    }

    @Test
    void levelChunk354HasNoSubChunkCount() {
        // 354 の LevelChunk は FullChunkDataSerializer_v291 が実効で、
        // chunkX／chunkZ と payload のみ。subChunkCount とキャッシュ
        // 可否は 361 で追加された（BE-NOTES.md §32）。
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        byte[] chunk354 = BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                BedrockVersion.V1_11_4);
        ByteReader in = new ByteReader(chunk354);
        assertEquals(-3, BedrockVarInt.readZigzag32(in));
        assertEquals(7, BedrockVarInt.readZigzag32(in));
        assertEquals(5, BedrockVarInt.readUnsignedVarInt(in), "ByteArray 長");
        for (byte b : payload) {
            assertEquals(b, in.readUnsignedByte());
        }
        assertEquals(0, in.remaining(), "354 の LevelChunk は最後まで読み切れること");
        byte[] chunk361 = BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                BedrockVersion.V1_12_1);
        assertEquals(chunk354.length + 2, chunk361.length,
                "361 は subChunkCount（varint 24＝1 バイト）＋キャッシュ可否（1 バイト）が付く");
    }

    @Test
    void addPainting354UsesBlockCoordinates() {
        // 354 の AddPainting は AddPaintingSerializer_v332 が実効で、
        // coordinates は BlockPosition（x zigzag32・y varint・
        // z zigzag32）。vec3f 化は 361（BE-NOTES.md §32）。
        byte[] painting354 = BedrockPackets.encodeAddPainting(108,
                10.0f, 65.0f, 10.0f, 2, "Kebab", BedrockVersion.V1_11_4);
        ByteReader pin = new ByteReader(painting354);
        assertEquals(108, BedrockVarInt.readZigzag64(pin));
        assertEquals(108, BedrockVarInt.readVarLong(pin));
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "x は zigzag32");
        assertEquals(65, BedrockVarInt.readUnsignedVarInt(pin), "y は varint");
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "z は zigzag32");
        assertEquals(2, BedrockVarInt.readZigzag32(pin));
        assertEquals("Kebab", BedrockVarInt.readString(pin));
        assertEquals(0, pin.remaining());
        // 361 は vec3f（lf32 × 3）のため、同じ引数では 354 より長い。
        byte[] painting361 = BedrockPackets.encodeAddPainting(108,
                10.0f, 65.0f, 10.0f, 2, "Kebab", BedrockVersion.V1_12_1);
        assertEquals(painting354.length + 9, painting361.length,
                "361 の vec3f は 12 バイト、354 のブロック座標は 3 バイト（x／z 各 1 バイト＋y 1 バイト）");
    }

    @Test
    void playerListAndResourcePacks354Equal361() {
        // PlayerList（_v291）・ResourcePacksInfo（_v332）・
        // ResourcePackStack（_v313）の実効シリアライザは 354 も 361 も
        // 同じため、この実装が書く範囲ではバイト同一になる
        // （BE-NOTES.md §32）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertArrayEquals(
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_12_1),
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_11_4),
                "354 の PlayerList add は 361 とバイト同一（旧直書きエントリ）");
        assertArrayEquals(
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_12_1),
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_11_4),
                "354 の PlayerList remove は 361 とバイト同一");
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_12_1),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_11_4),
                "354 の ResourcePacksInfo は 361 とバイト同一（空の一覧のため）");
        assertArrayEquals(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_12_1),
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_11_4),
                "354 の ResourcePackStack は 361 とバイト同一（gameVersion 無し）");
    }

    @Test
    void decodeInventoryTransactionV0As354() {
        // 354 の取引も v291 世代の基本形式（V0）で、legacy_request_id
        // は無い。361 と同じバイト列が 354 でも同じ結果に読めること
        // （BE-NOTES.md §32）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_11_4);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_11_4);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void startGame332Equals340() throws Exception {
        // 332 の StartGame は StartGameSerializer_v332＋トップレベル
        // _v291 が実効で、340 と完全に同一形式（LevelSettings
        // 30 フィールド・パレットは name／meta のみの配列・
        // itemstates 無し）。パレットの中身も 332 は 1.11.0 世代を
        // 代用するため、同じ情報では 340 と同じ長さになる
        // （multiplayerCorrelationId が呼び出しごとのランダム UUID
        // のため、StartGame は長さとパース結果で比べる。既存の
        // 340／354 テストと同じ流儀。BE-NOTES.md §34）。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload332 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_9_0);
        byte[] payload340 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_10_1);
        assertEquals(payload340.length, payload332.length,
                "332 の StartGame（パレットなし）は 340 と同じ長さ");
        ParsedStartGame parsed = parseStartGame(payload332,
                BedrockVersion.V1_9_0, null);
        assertEquals("world", parsed.worldName());
        assertNull(parsed.gameVersion(), "332 の LevelSettings に game version 文字列は無い");
        assertEquals(-1, parsed.itemstatesCount(), "332 に itemstates は無い");
        // パレットありでも 340 と同じ長さで、332 形式で最後まで
        // 読めること（中身は 1.11.0 世代パレットの代用）。
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] withPalette332 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_9_0);
        byte[] withPalette340 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_10_1);
        assertEquals(withPalette340.length, withPalette332.length,
                "332 の StartGame（パレットあり）は 340 と同じ長さ");
        ParsedStartGame parsedPal = parseStartGame(withPalette332,
                BedrockVersion.V1_9_0, palette);
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void levelChunk332Equals340() {
        // 332 の LevelChunk も FullChunkDataSerializer_v291 が実効で、
        // 340 と同一形式（subChunkCount・キャッシュ可否は無い。
        // BE-NOTES.md §34）。
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        assertArrayEquals(
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_10_1),
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_9_0),
                "332 の LevelChunk は 340 とバイト同一");
    }

    @Test
    void addPainting332UsesBlockCoordinates() {
        // 332 の AddPainting は AddPaintingSerializer_v332 自身が
        // 実効で、coordinates は BlockPosition。340 とバイト同一に
        // なる（BE-NOTES.md §34）。
        byte[] painting332 = BedrockPackets.encodeAddPainting(108,
                10.0f, 65.0f, 10.0f, 2, "Kebab", BedrockVersion.V1_9_0);
        assertArrayEquals(
                BedrockPackets.encodeAddPainting(108, 10.0f, 65.0f, 10.0f, 2,
                        "Kebab", BedrockVersion.V1_10_1),
                painting332,
                "332 の AddPainting は 340 とバイト同一（ブロック座標）");
        ByteReader pin = new ByteReader(painting332);
        assertEquals(108, BedrockVarInt.readZigzag64(pin));
        assertEquals(108, BedrockVarInt.readVarLong(pin));
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "x は zigzag32");
        assertEquals(65, BedrockVarInt.readUnsignedVarInt(pin), "y は varint");
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "z は zigzag32");
        assertEquals(2, BedrockVarInt.readZigzag32(pin));
        assertEquals("Kebab", BedrockVarInt.readString(pin));
        assertEquals(0, pin.remaining());
    }

    @Test
    void playerListAndResourcePacks332Equal340() {
        // PlayerList（_v291）・ResourcePacksInfo（_v332）・
        // ResourcePackStack（_v313）の実効シリアライザは 332 も 340 も
        // 同じため、この実装が書く範囲ではバイト同一になる
        // （BE-NOTES.md §34）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertArrayEquals(
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_10_1),
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_9_0),
                "332 の PlayerList add は 340 とバイト同一（旧直書きエントリ）");
        assertArrayEquals(
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_10_1),
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_9_0),
                "332 の PlayerList remove は 340 とバイト同一");
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_10_1),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_9_0),
                "332 の ResourcePacksInfo は 340 とバイト同一（空の一覧のため）");
        assertArrayEquals(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_10_1),
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_9_0),
                "332 の ResourcePackStack は 340 とバイト同一（gameVersion 無し）");
    }

    @Test
    void decodeInventoryTransactionV0As332() {
        // 332 の取引も v291 世代の基本形式（V0）で、legacy_request_id
        // は無い。340 と同じバイト列が 332 でも同じ結果に読めること
        // （BE-NOTES.md §34）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_9_0);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_9_0);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void startGame313UsesV291LevelSettings() throws Exception {
        // 313 の StartGame は StartGameSerializer_v313 が実効で、
        // LevelSettings は _v291 のレイアウト＋末尾 2 bool
        // （33 フィールド）。トップレベルは _v291 で 332 と同一、
        // パレットは name／meta のみの配列で中身も 332 と同じ
        // 1.11.0 世代の代用のため、313 は 332 より LevelSettings
        // 区間が 3 バイトだけ長い（multiplayerCorrelationId が
        // 呼び出しごとのランダム UUID のため、長さとパース結果で
        // 比べる。既存の 332／340 テストと同じ流儀。BE-NOTES.md §35）。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload313 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_8_1);
        byte[] payload332 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_9_0);
        assertEquals(payload332.length + 3, payload313.length,
                "313 の StartGame（パレットなし）は 332 より 3 バイト長い");
        ParsedStartGame parsed = parseStartGame(payload313,
                BedrockVersion.V1_8_1, null);
        assertEquals("world", parsed.worldName());
        assertNull(parsed.gameVersion(), "313 の LevelSettings に game version 文字列は無い");
        assertEquals(-1, parsed.itemstatesCount(), "313 に itemstates は無い");
        // パレットありでも差は 3 バイトのままで、313 形式で
        // 最後まで読めること（中身は 1.11.0 世代パレットの代用）。
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] withPalette313 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_8_1);
        byte[] withPalette332 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_9_0);
        assertEquals(withPalette332.length + 3, withPalette313.length,
                "313 の StartGame（パレットあり）は 332 より 3 バイト長い");
        ParsedStartGame parsedPal = parseStartGame(withPalette313,
                BedrockVersion.V1_8_1, palette);
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void levelChunk313Equals332() {
        // 313 の LevelChunk も FullChunkDataSerializer_v291 が実効で、
        // 332 と同一形式（subChunkCount・キャッシュ可否は無い。
        // BE-NOTES.md §35）。
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        assertArrayEquals(
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_9_0),
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_8_1),
                "313 の LevelChunk は 332 とバイト同一");
    }

    @Test
    void addPainting313UsesBlockCoordinates() {
        // 313 の AddPainting は AddPaintingSerializer_v291 が実効で、
        // _v332 とバイト単位で同一実装（coordinates は
        // BlockPosition）。332 とバイト同一になる（BE-NOTES.md §35）。
        byte[] painting313 = BedrockPackets.encodeAddPainting(108,
                10.0f, 65.0f, 10.0f, 2, "Kebab", BedrockVersion.V1_8_1);
        assertArrayEquals(
                BedrockPackets.encodeAddPainting(108, 10.0f, 65.0f, 10.0f, 2,
                        "Kebab", BedrockVersion.V1_9_0),
                painting313,
                "313 の AddPainting は 332 とバイト同一（ブロック座標）");
        ByteReader pin = new ByteReader(painting313);
        assertEquals(108, BedrockVarInt.readZigzag64(pin));
        assertEquals(108, BedrockVarInt.readVarLong(pin));
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "x は zigzag32");
        assertEquals(65, BedrockVarInt.readUnsignedVarInt(pin), "y は varint");
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "z は zigzag32");
        assertEquals(2, BedrockVarInt.readZigzag32(pin));
        assertEquals("Kebab", BedrockVarInt.readString(pin));
        assertEquals(0, pin.remaining());
    }

    @Test
    void playerListAndResourcePacks313Vs332() {
        // PlayerList（_v291）・ResourcePackStack（_v313）の実効
        // シリアライザは 313 も 332 も同じため、この実装が書く
        // 範囲ではバイト同一になる。ResourcePacksInfo は 313 が
        // _v291 で scriptingEnabled が無いため、空の一覧では
        // 332 より 1 バイト短い（BE-NOTES.md §35）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertArrayEquals(
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_9_0),
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_8_1),
                "313 の PlayerList add は 332 とバイト同一（旧直書きエントリ）");
        assertArrayEquals(
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_9_0),
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_8_1),
                "313 の PlayerList remove は 332 とバイト同一");
        byte[] info313 = BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_8_1);
        byte[] info332 = BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_9_0);
        assertEquals(info332.length - 1, info313.length,
                "313 の ResourcePacksInfo は scriptingEnabled が無いため 332 より 1 バイト短い");
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0}, info313,
                "313 の ResourcePacksInfo は must_accept＋2 件の空一覧（li16）で 5 バイト");
        parseResourcePacksInfo(info313, BedrockVersion.V1_8_1);
        assertArrayEquals(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_9_0),
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_8_1),
                "313 の ResourcePackStack は 332 とバイト同一（gameVersion 無し）");
    }

    @Test
    void decodeInventoryTransactionV0As313() {
        // 313 の取引も v291 世代の基本形式（V0）で、legacy_request_id
        // は無い。332 と同じバイト列が 313 でも同じ結果に読めること
        // （BE-NOTES.md §35）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_8_1);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_8_1);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void startGame291UsesPureV291LevelSettings() throws Exception {
        // 291 の StartGame は StartGameSerializer_v291 が実効で、
        // LevelSettings は純粋な _v291 の 31 フィールド（313 が
        // 末尾に追加した 2 bool が無い）。トップレベルも _v291 で
        // 313 と同一、パレットは name／meta のみの配列で中身も
        // 313 と同じ 1.11.0 世代の代用のため、291 は 313 より
        // 2 バイトだけ短い（multiplayerCorrelationId が呼び出し
        // ごとのランダム UUID のため、長さとパース結果で比べる。
        // BE-NOTES.md §36）。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload291 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_7_1);
        byte[] payload313 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_8_1);
        assertEquals(payload313.length - 2, payload291.length,
                "291 の StartGame（パレットなし）は 313 より 2 バイト短い");
        ParsedStartGame parsed = parseStartGame(payload291,
                BedrockVersion.V1_7_1, null);
        assertEquals("world", parsed.worldName());
        assertNull(parsed.gameVersion(), "291 の LevelSettings に game version 文字列は無い");
        assertEquals(-1, parsed.itemstatesCount(), "291 に itemstates は無い");
        // パレットありでも差は 2 バイトのままで、291 形式で
        // 最後まで読めること（中身は 1.11.0 世代パレットの代用）。
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] withPalette291 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_7_1);
        byte[] withPalette313 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_8_1);
        assertEquals(withPalette313.length - 2, withPalette291.length,
                "291 の StartGame（パレットあり）は 313 より 2 バイト短い");
        ParsedStartGame parsedPal = parseStartGame(withPalette291,
                BedrockVersion.V1_7_1, palette);
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void levelChunk291Equals313() {
        // 291 の LevelChunk も FullChunkDataSerializer_v291 が実効で、
        // 313 と同一形式（subChunkCount・キャッシュ可否は無い。
        // BE-NOTES.md §36）。
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        assertArrayEquals(
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_8_1),
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_7_1),
                "291 の LevelChunk は 313 とバイト同一");
    }

    @Test
    void addPainting291UsesBlockCoordinates() {
        // 291 の AddPainting は AddPaintingSerializer_v291 が実効で、
        // coordinates は BlockPosition。313 とバイト同一になる
        // （BE-NOTES.md §36）。
        byte[] painting291 = BedrockPackets.encodeAddPainting(108,
                10.0f, 65.0f, 10.0f, 2, "Kebab", BedrockVersion.V1_7_1);
        assertArrayEquals(
                BedrockPackets.encodeAddPainting(108, 10.0f, 65.0f, 10.0f, 2,
                        "Kebab", BedrockVersion.V1_8_1),
                painting291,
                "291 の AddPainting は 313 とバイト同一（ブロック座標）");
    }

    @Test
    void playerListAndResourcePacks291Vs313() {
        // PlayerList（_v291）・ResourcePacksInfo（_v291）は 291 も
        // 313 も同じ実効シリアライザのためバイト同一になる。
        // ResourcePackStack は 291 が _v291 形式で、313 が末尾に
        // 追加した legacy experiment bool が無いため 1 バイト短い
        // （BE-NOTES.md §36）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertArrayEquals(
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_8_1),
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_7_1),
                "291 の PlayerList add は 313 とバイト同一（旧直書きエントリ）");
        assertArrayEquals(
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_8_1),
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_7_1),
                "291 の PlayerList remove は 313 とバイト同一");
        byte[] info291 = BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_7_1);
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_8_1),
                info291,
                "291 の ResourcePacksInfo は 313 とバイト同一（scripting 無し）");
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0}, info291,
                "291 の ResourcePacksInfo は must_accept＋2 件の空一覧（li16）で 5 バイト");
        parseResourcePacksInfo(info291, BedrockVersion.V1_7_1);
        byte[] stack291 = BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_7_1);
        byte[] stack313 = BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_8_1);
        assertEquals(stack313.length - 1, stack291.length,
                "291 の ResourcePackStack は legacy experiment bool が無いため 313 より 1 バイト短い");
        assertArrayEquals(new byte[]{0, 0, 0}, stack291,
                "291 の ResourcePackStack は must_accept＋2 件の空一覧で 3 バイト");
        parseResourcePackStack(stack291, BedrockVersion.V1_7_1);
    }

    @Test
    void addEntity291UsesLegacyTypeId() {
        // 291 の AddEntity は AddEntitySerializer_v291 が実効で、
        // 第 3 フィールドは identifier 文字列ではなく旧来の数値
        // タイプ ID（VarUnsignedInt。ゾンビは PocketMine-MP 3.4.0 の
        // EntityIds で 32）。313 は文字列（この実装の文字列形式は
        // u32 LE 長 4 バイト＋16 バイト＝20 バイト）のため、
        // 291 は 19 バイト短い（BE-NOTES.md §36）。
        byte[] entity291 = BedrockPackets.encodeAddEntity(42,
                "minecraft:zombie", 1.0f, 64.0f, 2.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 20.0f, 20.0f, null, BedrockVersion.V1_7_1);
        byte[] entity313 = BedrockPackets.encodeAddEntity(42,
                "minecraft:zombie", 1.0f, 64.0f, 2.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 20.0f, 20.0f, null, BedrockVersion.V1_8_1);
        assertEquals(entity313.length - 19, entity291.length,
                "291 の AddEntity はタイプが数値 ID（1 バイト）のため 313 より 19 バイト短い");
        ByteReader in = new ByteReader(entity291);
        assertEquals(42, BedrockVarInt.readZigzag64(in), "unique id");
        assertEquals(42, BedrockVarInt.readVarLong(in), "runtime id");
        assertEquals(32, BedrockVarInt.readUnsignedVarInt(in),
                "entity type は数値 ID 32（ZOMBIE）");
    }

    @Test
    void decodeInventoryTransactionV0As291() {
        // 291 の取引も v291 世代の基本形式（V0）で、legacy_request_id
        // は無い。313 と同じバイト列が 291 でも同じ結果に読めること
        // （BE-NOTES.md §36）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_7_1);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_7_1);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void startGame340Equals354() throws Exception {
        // 340 の StartGame は StartGameSerializer_v332＋トップレベル
        // _v291 が実効で、354 と完全に同一形式（LevelSettings
        // 30 フィールド・パレットは name／meta のみの配列・
        // itemstates 無し）。パレットの中身も 340 は 1.11.0 世代を
        // 代用するため、同じ情報では 354 と同じ長さになる
        // （multiplayerCorrelationId が呼び出しごとのランダム UUID
        // のため、StartGame は長さとパース結果で比べる。既存の
        // 354／361 テストと同じ流儀。BE-NOTES.md §33）。
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload340 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_10_1);
        byte[] payload354 = BedrockPackets.encodeStartGame(info, null,
                BedrockVersion.V1_11_4);
        assertEquals(payload354.length, payload340.length,
                "340 の StartGame（パレットなし）は 354 と同じ長さ");
        ParsedStartGame parsed = parseStartGame(payload340,
                BedrockVersion.V1_10_1, null);
        assertEquals("world", parsed.worldName());
        assertNull(parsed.gameVersion(), "340 の LevelSettings に game version 文字列は無い");
        assertEquals(-1, parsed.itemstatesCount(), "340 に itemstates は無い");
        // パレットありでも 354 と同じ長さで、340 形式で最後まで
        // 読めること（中身は 1.11.0 世代パレットの代用）。
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] withPalette340 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_10_1);
        byte[] withPalette354 = BedrockPackets.encodeStartGame(info, palette,
                BedrockVersion.V1_11_4);
        assertEquals(withPalette354.length, withPalette340.length,
                "340 の StartGame（パレットあり）は 354 と同じ長さ");
        ParsedStartGame parsedPal = parseStartGame(withPalette340,
                BedrockVersion.V1_10_1, palette);
        assertEquals("world", parsedPal.worldName());
    }

    @Test
    void levelChunk340Equals354() {
        // 340 の LevelChunk も FullChunkDataSerializer_v291 が実効で、
        // 354 と同一形式（subChunkCount・キャッシュ可否は無い。
        // BE-NOTES.md §33）。
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        assertArrayEquals(
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_11_4),
                BedrockPackets.encodeLevelChunk(-3, 7, 24, payload,
                        BedrockVersion.V1_10_1),
                "340 の LevelChunk は 354 とバイト同一");
    }

    @Test
    void addPainting340UsesBlockCoordinates() {
        // 340 の AddPainting も AddPaintingSerializer_v332 が実効で、
        // coordinates は BlockPosition。354 とバイト同一になる
        // （BE-NOTES.md §33）。
        byte[] painting340 = BedrockPackets.encodeAddPainting(108,
                10.0f, 65.0f, 10.0f, 2, "Kebab", BedrockVersion.V1_10_1);
        assertArrayEquals(
                BedrockPackets.encodeAddPainting(108, 10.0f, 65.0f, 10.0f, 2,
                        "Kebab", BedrockVersion.V1_11_4),
                painting340,
                "340 の AddPainting は 354 とバイト同一（ブロック座標）");
        ByteReader pin = new ByteReader(painting340);
        assertEquals(108, BedrockVarInt.readZigzag64(pin));
        assertEquals(108, BedrockVarInt.readVarLong(pin));
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "x は zigzag32");
        assertEquals(65, BedrockVarInt.readUnsignedVarInt(pin), "y は varint");
        assertEquals(10, BedrockVarInt.readZigzag32(pin), "z は zigzag32");
        assertEquals(2, BedrockVarInt.readZigzag32(pin));
        assertEquals("Kebab", BedrockVarInt.readString(pin));
        assertEquals(0, pin.remaining());
    }

    @Test
    void playerListAndResourcePacks340Equal354() {
        // PlayerList（_v291）・ResourcePacksInfo（_v332）・
        // ResourcePackStack（_v313）の実効シリアライザは 340 も 354 も
        // 同じため、この実装が書く範囲ではバイト同一になる
        // （BE-NOTES.md §33）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertArrayEquals(
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_11_4),
                BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve", BedrockVersion.V1_10_1),
                "340 の PlayerList add は 354 とバイト同一（旧直書きエントリ）");
        assertArrayEquals(
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_11_4),
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_10_1),
                "340 の PlayerList remove は 354 とバイト同一");
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_11_4),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_10_1),
                "340 の ResourcePacksInfo は 354 とバイト同一（空の一覧のため）");
        assertArrayEquals(
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_11_4),
                BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_10_1),
                "340 の ResourcePackStack は 354 とバイト同一（gameVersion 無し）");
    }

    @Test
    void decodeInventoryTransactionV0As340() {
        // 340 の取引も v291 世代の基本形式（V0）で、legacy_request_id
        // は無い。354 と同じバイト列が 340 でも同じ結果に読めること
        // （BE-NOTES.md §33）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_10_1);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_10_1);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void resourcePacks361HasNoGameVersion() {
        // ResourcePacksInfo の実効シリアライザは 361 も 388 も v332 で
        // 同一のため、空の一覧ではバイト同一になる。ResourcePackStack は
        // 361 が _v313 で末尾の gameVersion が無く、legacy experiment
        // フラグで終わる（BE-NOTES.md §31）。
        assertArrayEquals(
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_13_3),
                BedrockPackets.encodeResourcePacksInfo(BedrockVersion.V1_12_1),
                "361 の ResourcePacksInfo は 388 とバイト同一（空の一覧のため）");
        byte[] stack361 = BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_12_1);
        assertArrayEquals(new byte[]{0, 0, 0, 0}, stack361,
                "361 の ResourcePackStack は must_accept＋packs 0 件＋legacy フラグの 4 バイト");
        byte[] stack388 = BedrockPackets.encodeResourcePackStack(BedrockVersion.V1_13_3);
        assertEquals(stack361.length + 10, stack388.length,
                "388 は末尾に gameVersion 文字列（\"1.13.3\" で長さ 4 バイト＋6 バイト）が付く");
        parseResourcePackStack(stack361, BedrockVersion.V1_12_1);
        parseResourcePackStack(stack388, BedrockVersion.V1_13_3);
    }

    @Test
    void playerListAdd361UsesLegacyEntry() {
        // 361 の PlayerList add は PlayerListSerializer_v291 の旧直書き
        // エントリ（skinId／skinData／capeData／geometryName／
        // geometryData を埋め込み、buildPlatform・Skin・teacher・host
        // は無い。BE-NOTES.md §31）。フィールド順を 1 つずつ読んで
        // 確認する。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        byte[] payload = BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve",
                BedrockVersion.V1_12_1);
        assertEquals(50, payload.length, "361 の PlayerList add の長さ");
        ByteReader in = new ByteReader(payload);
        assertEquals(0, in.readUnsignedByte(), "type は add");
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(in), "records は 1 件");
        assertEquals(uuid, new UUID(in.readBigLong(), in.readBigLong()), "uuid");
        assertEquals(42, BedrockVarInt.readZigzag64(in), "entity id");
        assertEquals("Steve", BedrockVarInt.readString(in), "name");
        assertEquals("", BedrockVarInt.readString(in), "skin id");
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "skin data は空");
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "cape data は空");
        assertEquals("", BedrockVarInt.readString(in), "geometry name");
        assertEquals("", BedrockVarInt.readString(in), "geometry data");
        assertEquals("", BedrockVarInt.readString(in), "xuid");
        assertEquals("", BedrockVarInt.readString(in), "platform chat id");
        assertEquals(0, in.remaining(), "最後まで読み切れること");
        int len388 = BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve",
                BedrockVersion.V1_13_3).length;
        assertEquals(117, len388, "388 の PlayerList add の長さ（Skin 形式）");
    }

    @Test
    void playerListRemove361Equals388() {
        // PlayerList remove は 361 も 388 も uuid のみで同一
        // （verified は両世代とも無し。BE-NOTES.md §31）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertArrayEquals(
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_13_3),
                BedrockPackets.encodePlayerListRemove(uuid, BedrockVersion.V1_12_1),
                "361 の PlayerList remove は 388 とバイト同一");
        assertEquals(18, BedrockPackets.encodePlayerListRemove(uuid,
                BedrockVersion.V1_12_1).length,
                "361 の PlayerList remove は verified 無し");
    }

    @Test
    void addPlayer361HasNoBuildPlatform() {
        // 361 の AddPlayer は AddPlayerSerializer_v291 が実効で、388 で
        // 末尾に追加された buildPlatform（IntLE）が無い。それ以外は
        // 同一形式のため、同じ引数では 361 がちょうど 4 バイト短い
        // （BE-NOTES.md §31）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        byte[] payload361 = BedrockPackets.encodeAddPlayer(uuid, "Steve", 42,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, null, BedrockVersion.V1_12_1);
        byte[] payload388 = BedrockPackets.encodeAddPlayer(uuid, "Steve", 42,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, null, BedrockVersion.V1_13_3);
        assertEquals(payload388.length - 4, payload361.length,
                "361 の AddPlayer には末尾 buildPlatform が無い");
    }

    @Test
    void decodeInventoryTransactionV0As361() {
        // 361 の取引も v291 世代の基本形式（V0）で、legacy_request_id
        // は無い。388 と同じバイト列が 361 でも同じ結果に読めること
        // （BE-NOTES.md §31）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_12_1);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_12_1);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void tickFields390Omitted() {
        // MovePlayer・SetEntityData・UpdateAttributes の末尾 tick は
        // 419 で追加された。390 では書かないため、同じ引数では 390 の
        // ペイロードが 419 よりちょうど 1 バイト短い（407・408 と同じ。
        // BE-NOTES.md §28）。
        byte[] move390 = BedrockPackets.encodeMovePlayerTeleport(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, BedrockVersion.V1_14_60);
        byte[] move419 = BedrockPackets.encodeMovePlayerTeleport(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, BedrockVersion.V1_16_101);
        assertEquals(move419.length - 1, move390.length,
                "390 の MovePlayer（テレポート）には末尾 tick が無い");
        byte[] moveEntity390 = BedrockPackets.encodeMovePlayerEntity(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, true, BedrockVersion.V1_14_60);
        byte[] moveEntity419 = BedrockPackets.encodeMovePlayerEntity(7,
                1.0f, 64.0f, 2.0f, 0.0f, 90.0f, 90.0f, 0, true, BedrockVersion.V1_16_101);
        assertEquals(moveEntity419.length - 1, moveEntity390.length,
                "390 の MovePlayer（他プレイヤー）には末尾 tick が無い");
        byte[] data390 = BedrockPackets.encodeSetEntityData(7, null, BedrockVersion.V1_14_60);
        byte[] data419 = BedrockPackets.encodeSetEntityData(7, null, BedrockVersion.V1_16_101);
        assertEquals(data419.length - 1, data390.length,
                "390 の SetEntityData には末尾 tick が無い");
        byte[] attr390 = BedrockPackets.encodeUpdateAttributes(7, 20.0f, 20.0f,
                BedrockVersion.V1_14_60);
        byte[] attr419 = BedrockPackets.encodeUpdateAttributes(7, 20.0f, 20.0f,
                BedrockVersion.V1_16_101);
        assertEquals(attr419.length - 1, attr390.length,
                "390 の UpdateAttributes には末尾 tick が無い");
    }

    @Test
    void playerListRemove390HasNoVerified() {
        // PlayerList remove に verified 配列があるのは 422・428 だけ。
        // 390（と 407・408・419）は uuid のみで、type＋件数＋uuid の
        // 18 バイト（PlayerListSerializer_v390。BE-NOTES.md §28）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertEquals(18, BedrockPackets.encodePlayerListRemove(uuid,
                BedrockVersion.V1_14_60).length,
                "390 の PlayerList remove は verified 無し");
        assertEquals(18, BedrockPackets.encodePlayerListRemove(uuid,
                BedrockVersion.V1_16_10).length,
                "407 の PlayerList remove も verified 無し");
        assertEquals(19, BedrockPackets.encodePlayerListRemove(uuid,
                BedrockVersion.V1_16_201).length,
                "422 の PlayerList remove は verified 1 バイトが付く");
    }

    @Test
    void playerListAdd389HasNoVerified() {
        // PlayerList add の verified（trustedSkin）は 390 の
        // PlayerListSerializer_v390 で追加された末尾ブロックで、
        // 389 の実効シリアライザ（PlayerListSerializer_v388）には無い。
        // そのため同じ引数では 389 のペイロードが 390 よりちょうど
        // 1 バイト短い（BE-NOTES.md §29）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        int len389 = BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve",
                BedrockVersion.V1_14_41).length;
        int len390 = BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve",
                BedrockVersion.V1_14_60).length;
        assertEquals(len390 - 1, len389,
                "389 の PlayerList add には verified が無い");
        // バージョン無し版は従来通り verified を書く（390 以降の形式）。
        assertEquals(len390, BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve").length,
                "バージョン無し版の PlayerList add は verified を書く");
    }

    @Test
    void playerListRemove389HasNoVerified() {
        // 389 の PlayerList remove も uuid のみ（v388 serializer。
        // 390 と同じ 18 バイト。BE-NOTES.md §29）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertEquals(18, BedrockPackets.encodePlayerListRemove(uuid,
                BedrockVersion.V1_14_41).length,
                "389 の PlayerList remove は verified 無し");
    }

    @Test
    void playerListAdd388HasNoVerified() {
        // 388 の PlayerList add も verified 無し（PlayerListSerializer_v388
        // 自身が実効。389 と同じ長さで、390 より 1 バイト短い。
        // BE-NOTES.md §30）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        int len388 = BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve",
                BedrockVersion.V1_13_3).length;
        int len389 = BedrockPackets.encodePlayerListAdd(uuid, 42, "Steve",
                BedrockVersion.V1_14_41).length;
        assertEquals(len389, len388,
                "388 の PlayerList add は 389 と同じ長さ（verified 無し）");
    }

    @Test
    void playerListRemove388HasNoVerified() {
        // 388 の PlayerList remove も uuid のみ（v388 serializer。
        // 389・390 と同じ 18 バイト。BE-NOTES.md §30）。
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertEquals(18, BedrockPackets.encodePlayerListRemove(uuid,
                BedrockVersion.V1_13_3).length,
                "388 の PlayerList remove は verified 無し");
    }

    @Test
    void startGame431OmitsEngine() {
        // 431 の StartGame は 59 フィールドで engine が無い。440 は 60 フィールドで
        // 末尾が engine（空文字列）。Bedrock の文字列は u32 LE 長＋UTF-8 のため
        // 空文字列でも 4 バイトある。game version を同じ文字列にして比較すると、
        // 431 は 440 よりちょうど 4 バイト短い。
        BedrockPackets.StartGameInfo info431 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        BedrockPackets.StartGameInfo info440 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", "x");
        byte[] payload431 = BedrockPackets.encodeStartGame(info431, null, BedrockVersion.V1_16_221);
        byte[] payload440 = BedrockPackets.encodeStartGame(info440, null, BedrockVersion.V1_17_2);
        assertEquals(payload440.length - 4, payload431.length,
                "431 の StartGame は 440 より engine（空文字列・u32 長 4 バイト）分だけ短い");
        assertFalse(BedrockVersion.V1_16_221.hasStartGameEngine(), "431 に engine は無い");
        assertTrue(BedrockVersion.V1_17_2.hasStartGameEngine(), "440 に engine はある");
        ParsedStartGame parsed431 = parseStartGame(payload431, BedrockVersion.V1_16_221, null);
        ParsedStartGame parsed440 = parseStartGame(payload440, BedrockVersion.V1_17_2, null);
        assertEquals("x", parsed431.gameVersion());
        assertEquals("x", parsed440.gameVersion());
        assertEquals("world", parsed431.worldName());
        assertEquals("world", parsed440.worldName());
    }

    @Test
    void startGame471Is7BytesShorterThan475() {
        // 471 は 475 より block_pallete_checksum（lu64、8 バイト）が無い。
        // game version は "1.17.41"（7 バイト）と "1.18.2"（6 バイト）で
        // 1 バイト違うため、実測の差は 8 - 1 = 7 バイトになる。
        BedrockPackets.StartGameInfo info471 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_17_41.displayName());
        BedrockPackets.StartGameInfo info475 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_18_2.displayName());
        byte[] payload471 = BedrockPackets.encodeStartGame(info471, null, BedrockVersion.V1_17_41);
        byte[] payload475 = BedrockPackets.encodeStartGame(info475, null, BedrockVersion.V1_18_2);
        assertEquals(payload475.length - 7, payload471.length,
                "471 の StartGame は 475 より 7 バイト短い");
        ParsedStartGame parsed471 = parseStartGame(payload471, BedrockVersion.V1_17_41, null);
        assertEquals("1.17.41", parsed471.gameVersion());
        assertEquals("world", parsed471.worldName());
    }

    @Test
    void startGame448Is8BytesShorterThan465() {
        // 448 の StartGame は 465 より edu_resource_uri（空文字列 2 つ）だけ短い。
        // writeString は長さを u32 LE（4 バイト）で書くため空文字列 1 つが
        // 4 バイト、2 つで 8 バイト。game version は "1.17.11"（7 バイト）と
        // "1.17.34"（7 バイト）で同じ長さのため、差は 8 バイトちょうど。
        // （実測：448＝191 バイト、465＝199 バイト。）
        BedrockPackets.StartGameInfo info448 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_17_11.displayName());
        BedrockPackets.StartGameInfo info465 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_17_34.displayName());
        byte[] payload448 = BedrockPackets.encodeStartGame(info448, null, BedrockVersion.V1_17_11);
        byte[] payload465 = BedrockPackets.encodeStartGame(info465, null, BedrockVersion.V1_17_34);
        assertEquals(payload465.length - 8, payload448.length,
                "448 の StartGame は 465 より 8 バイト短い");
        ParsedStartGame parsed448 = parseStartGame(payload448, BedrockVersion.V1_17_11, null);
        assertEquals("1.17.11", parsed448.gameVersion());
        assertEquals("world", parsed448.worldName());
    }

    @Test
    void startGame465Equals471Length() {
        // 465 と 471 の StartGame は JSON 完全一致（61 フィールド）。
        // game version は "1.17.34"（7 バイト）と "1.17.41"（7 バイト）で
        // 同じ長さのため、エンコード結果も同じ長さになる。
        BedrockPackets.StartGameInfo info465 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_17_34.displayName());
        BedrockPackets.StartGameInfo info471 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_17_41.displayName());
        byte[] payload465 = BedrockPackets.encodeStartGame(info465, null, BedrockVersion.V1_17_34);
        byte[] payload471 = BedrockPackets.encodeStartGame(info471, null, BedrockVersion.V1_17_41);
        assertEquals(payload471.length, payload465.length,
                "465 の StartGame は 471 と同じ長さ");
        ParsedStartGame parsed465 = parseStartGame(payload465, BedrockVersion.V1_17_34, null);
        assertEquals("1.17.34", parsed465.gameVersion());
        assertEquals("world", parsed465.worldName());
    }

    @Test
    void startGame2225Is1ByteLongerThan2193() {
        // 2225 の StartGame は 83 フィールドで、2193（82 フィールド）との
        // 差分は editor_level_migration_version（u8、1 バイト）の挿入のみ
        // （exported_from_editor の直後）。game version は "1.26.52" と
        // "1.26.60" でどちらも 7 バイトのため、差は 1 バイトちょうど。
        BedrockPackets.StartGameInfo info2193 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_26_52.displayName());
        BedrockPackets.StartGameInfo info2225 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_26_60.displayName());
        byte[] payload2193 = BedrockPackets.encodeStartGame(info2193, null, BedrockVersion.V1_26_52);
        byte[] payload2225 = BedrockPackets.encodeStartGame(info2225, null, BedrockVersion.V1_26_60);
        assertEquals(payload2193.length + 1, payload2225.length,
                "2225 の StartGame は 2193 より 1 バイト長い");
        ParsedStartGame parsed2225 = parseStartGame(payload2225, BedrockVersion.V1_26_60, null);
        assertEquals("1.26.60", parsed2225.gameVersion());
        assertEquals("world", parsed2225.worldName());
    }

    @Test
    void startGame503Is18BytesShorterThan527() {
        // 503 は 527 より property_data（3 バイト）＋world_template_id
        // （16 バイト）の 19 バイト分短い。チェックサム（lu64）は両方にある。
        // ただし game version は "1.18.33"（7 バイト）と "1.19.2"（6 バイト）で
        // 1 バイト違うため、実測の差は 19 - 1 = 18 バイトになる。
        BedrockPackets.StartGameInfo info503 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_18_33.displayName());
        BedrockPackets.StartGameInfo info527 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_19_2.displayName());
        byte[] payload503 = BedrockPackets.encodeStartGame(info503, null, BedrockVersion.V1_18_33);
        byte[] payload527 = BedrockPackets.encodeStartGame(info527, null, BedrockVersion.V1_19_2);
        assertEquals(payload527.length - 18, payload503.length,
                "503 の StartGame は 527 より 18 バイト短い");
        ParsedStartGame parsed503 = parseStartGame(payload503, BedrockVersion.V1_18_33, null);
        assertEquals("1.18.33", parsed503.gameVersion());
        assertEquals("world", parsed503.worldName());
    }

    @Test
    void startGame486Is7BytesShorterThan503() {
        // 486 は 503 より seed が zigzag32（1 バイト）対 lu64（8 バイト）で
        // 7 バイト短い。dimension は 486 が zigzag32、503 が mapper だが
        // overworld＝0 はワイヤ同一で差は無い。game version は "1.18.12" と
        // "1.18.33" で同じ 7 バイトのため、実測の差はちょうど 7 バイトになる。
        BedrockPackets.StartGameInfo info486 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_18_12.displayName());
        BedrockPackets.StartGameInfo info503 = new BedrockPackets.StartGameInfo(
                1, 0.5f, 64.0f, 0.5f, "world", BedrockVersion.V1_18_33.displayName());
        byte[] payload486 = BedrockPackets.encodeStartGame(info486, null, BedrockVersion.V1_18_12);
        byte[] payload503 = BedrockPackets.encodeStartGame(info503, null, BedrockVersion.V1_18_33);
        assertEquals(payload503.length - 7, payload486.length,
                "486 の StartGame は 503 より 7 バイト短い");
        ParsedStartGame parsed486 = parseStartGame(payload486, BedrockVersion.V1_18_12, null);
        assertEquals("1.18.12", parsed486.gameVersion());
        assertEquals("world", parsed486.worldName());
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
        if (version.resourcePacksInfoHasScripting()) {
            assertFalse(in.readBoolean(), "has_scripts は false");
        }
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
        BedrockVersion[] shapes = {BedrockVersion.V1_19_2, BedrockVersion.V1_19_11, BedrockVersion.V1_19_22, BedrockVersion.V1_19_31, BedrockVersion.V1_19_41, BedrockVersion.V1_19_51, BedrockVersion.V1_19_62, BedrockVersion.V1_19_70, BedrockVersion.V1_19_83, BedrockVersion.V1_20_0,
                BedrockVersion.V1_20_30,
                BedrockVersion.V1_20_70, BedrockVersion.V1_21_0,
                BedrockVersion.V1_21_30, BedrockVersion.V1_21_50,
                BedrockVersion.V1_21_90, BedrockVersion.V1_21_124, BedrockVersion.V1_26_0,
                BedrockVersion.V1_26_30, BedrockVersion.V1_26_50,
                BedrockVersion.V1_26_52};
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
        if (version.hasResourcePackStackExperiments()) {
            assertEquals(version.displayName(), BedrockVarInt.readString(in),
                    "game_version はクライアントの表示名");
            assertEquals(0, BedrockVarInt.readUnsignedVarInt(in), "experiments は 0");
            assertFalse(in.readBoolean(), "experiments previously used は false");
        } else {
            // 408 の旧形式：gameVersion の前に legacy experiment フラグ。
            // 291 は _v291 形式でフラグ自体が無い（§36）。
            if (!version.resourcePackStackUsesV291Format()) {
                assertFalse(in.readBoolean(), "legacy experiment は false");
            }
            if (version.resourcePackStackHasGameVersion()) {
                assertEquals(version.displayName(), BedrockVarInt.readString(in),
                        "game_version はクライアントの表示名");
            }
        }
        if (version.hasEditorPacksInStack()) {
            assertFalse(in.readBoolean(), "has_editor_packs は false");
        }
        assertEquals(0, in.remaining(),
                "ResourcePackStack は最後まで読み切れること（" + version.displayName() + "）");
    }

    @Test
    void resourcePackStackParsesForEveryShape() {
        BedrockVersion[] shapes = {BedrockVersion.V1_19_2, BedrockVersion.V1_19_11, BedrockVersion.V1_19_22, BedrockVersion.V1_19_31, BedrockVersion.V1_19_41, BedrockVersion.V1_19_51, BedrockVersion.V1_19_62, BedrockVersion.V1_19_70, BedrockVersion.V1_19_83, BedrockVersion.V1_20_0,
                BedrockVersion.V1_20_70,
                BedrockVersion.V1_21_0, BedrockVersion.V1_21_90,
                BedrockVersion.V1_21_124, BedrockVersion.V1_21_130, BedrockVersion.V1_26_0,
                BedrockVersion.V1_26_50, BedrockVersion.V1_26_52};
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

    /**
     * V0 形式（390）の取引ペイロードを作る。legacy_request_id は無く、
     * transaction_type から始まる（CloudburstMC Protocol の
     * InventoryTransactionSerializer_v291。BE-NOTES.md §28）。
     */
    private static byte[] buildV0Transaction(boolean withAction) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeVarInt(out, BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY);
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
        BedrockVarInt.writeVarLong(out, 7); // entity runtime id
        BedrockVarInt.writeVarInt(out, 1); // action type: attack
        BedrockVarInt.writeZigzag32(out, 0); // hotbar slot
        BedrockPackets.writeAirItem(out); // held item
        writePositions(out);
        return out.bytes();
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

    /** V4 形式（26.60 以降）の取引ペイロードを作る。V3 との差分は
     * item_use_on_entity の hotbar_slot と held_item の間に hand（u8）が
     * 入ることだけ（BE-NOTES.md §24）。 */
    private static byte[] buildV4Transaction() {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, 0); // legacy request id
        out.writeBoolean(true); // legacy_transactions あり
        BedrockVarInt.writeUnsignedVarInt(out, 0); // legacy 0 件
        out.writeBoolean(true); // transaction_type あり
        BedrockVarInt.writeVarInt(out,
                BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY);
        out.writeBoolean(true); // actions あり
        BedrockVarInt.writeUnsignedVarInt(out, 1); // actions 1 件
        writeV2Action(out);
        BedrockVarInt.writeVarLong(out, 7); // entity runtime id
        BedrockVarInt.writeZigzag32(out, 1); // action type: attack
        BedrockVarInt.writeZigzag32(out, 0); // hotbar slot
        out.writeByte(0); // hand: main_hand（2225 で新規）
        BedrockPackets.writeAirItem(out, true); // held item (ItemV4)
        writePositions(out);
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
    void decodeInventoryTransactionV1With1_21_124() {
        // 860（1.21.124）は旧式トランザクション（legacy は option ではなく
        // legacy_request_id の switch、アイテムは旧型 Item）。V1 形式で読めること。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV1Transaction(0, BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY,
                        false, true),
                BedrockVersion.V1_21_124);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV0() {
        // 390（1.14.60）の取引は legacy_request_id が無い V0 形式
        // （transaction_type から始まる）。V0 形式で読めること。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_14_60);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV0WithAction() {
        // container アクション付きでも、v291 世代の基本形式の
        // 読み飛ばしで item_use_on_entity まで辿り着けること。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_14_60);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV0As407IsNull() {
        // 同じバイト列を 407 形式で読むと、先頭が legacy_request_id
        // （zigzag32 で -2）として解釈されて legacy 側へ流れ、
        // item_use_on_entity にはならない（形式が違うことの確認）。
        assertNull(BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_16_10));
    }

    @Test
    void decodeInventoryTransactionV0As389() {
        // 389 の取引も 390 と同じ v291 世代の基本形式（V0）で、
        // legacy_request_id は無い。390 と同じバイト列が 389 でも
        // 同じ結果に読めること（BE-NOTES.md §29）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_14_41);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_14_41);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void decodeInventoryTransactionV0As388() {
        // 388 の取引も 389・390 と同じ v291 世代の基本形式（V0）で、
        // legacy_request_id は無い。390 と同じバイト列が 388 でも
        // 同じ結果に読めること（BE-NOTES.md §30）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(false), BedrockVersion.V1_13_3);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        InventoryTransactionUse useWithAction = BedrockPackets.decodeInventoryTransaction(
                buildV0Transaction(true), BedrockVersion.V1_13_3);
        assertEquals(7, useWithAction.entityRuntimeId());
        assertEquals(1, useWithAction.actionType());
    }

    @Test
    void decodeInventoryTransactionV3() {
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV3Transaction(true), BedrockVersion.V1_26_50);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
    }

    @Test
    void decodeInventoryTransactionV3With1_26_52() {
        // 2193 を共有するため、V1_26_52 でも V3 形式で読めること。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV3Transaction(true), BedrockVersion.V1_26_52);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        assertNull(BedrockPackets.decodeInventoryTransaction(
                buildV3Transaction(false), BedrockVersion.V1_26_52));
    }

    @Test
    void decodeInventoryTransactionV4With1_26_60() {
        // 2225 は V3 形式に hand（u8）が増えた V4 形式。hand を読み飛ばして
        // held_item 以降が正しく読めること（BE-NOTES.md §24）。
        InventoryTransactionUse use = BedrockPackets.decodeInventoryTransaction(
                buildV4Transaction(), BedrockVersion.V1_26_60);
        assertEquals(7, use.entityRuntimeId());
        assertEquals(1, use.actionType());
        // V3 形式（hand 無し）を 2225 として読むと held_item の位置が
        // ずれるため、少なくとも同じ結果にはならない（air アイテムの
        // 読みがずれて例外→null になる）。
        assertNull(BedrockPackets.decodeInventoryTransaction(
                buildV3Transaction(true), BedrockVersion.V1_26_60));
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

    @Test
    void updateAttributesAndChunkPublisherOmit544FieldsFor1_19_11() {
        // UpdateAttributes：534 は modifiers が無いため 544 より 1 バイト短い。
        byte[] old = BedrockPackets.encodeUpdateAttributes(101, 15.0f, 30.0f,
                BedrockVersion.V1_19_11);
        byte[] next = BedrockPackets.encodeUpdateAttributes(101, 15.0f, 30.0f,
                BedrockVersion.V1_19_20);
        assertEquals(next.length - 1, old.length, "modifiers の varint 0 が 1 バイト分");
        // 534 形を最後まで読めること（modifiers なしで tick を読む）。
        ByteReader in = new ByteReader(old);
        assertEquals(101, BedrockVarInt.readVarLong(in));
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(in));
        assertEquals(0.0f, in.readLittleFloat(), 0.001f); // min
        assertEquals(30.0f, in.readLittleFloat(), 0.001f); // max
        assertEquals(15.0f, in.readLittleFloat(), 0.001f); // current
        assertEquals(30.0f, in.readLittleFloat(), 0.001f); // default
        assertEquals("minecraft:health", BedrockVarInt.readString(in));
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // tick
        assertEquals(0, in.remaining());
        // 544 形は modifiers 0 を挟んで tick を読む。
        ByteReader in2 = new ByteReader(next);
        assertEquals(101, BedrockVarInt.readVarLong(in2));
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(in2));
        assertEquals(0.0f, in2.readLittleFloat(), 0.001f);
        assertEquals(30.0f, in2.readLittleFloat(), 0.001f);
        assertEquals(15.0f, in2.readLittleFloat(), 0.001f);
        assertEquals(30.0f, in2.readLittleFloat(), 0.001f);
        assertEquals("minecraft:health", BedrockVarInt.readString(in2));
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in2)); // modifiers
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in2)); // tick
        assertEquals(0, in2.remaining());
        // NetworkChunkPublisherUpdate：534 は saved_chunks が無いため 544 より 4 バイト短い。
        byte[] pubOld = BedrockPackets.encodeNetworkChunkPublisherUpdate(16, 64, -32, 96,
                BedrockVersion.V1_19_11);
        byte[] pubNext = BedrockPackets.encodeNetworkChunkPublisherUpdate(16, 64, -32, 96,
                BedrockVersion.V1_19_20);
        assertEquals(pubNext.length - 4, pubOld.length, "saved chunks の lu32 0 が 4 バイト分");
        ByteReader pin = new ByteReader(pubOld);
        assertEquals(16, BedrockVarInt.readZigzag32(pin)); // block x
        assertEquals(64, BedrockVarInt.readUnsignedVarInt(pin)); // block y
        assertEquals(-32, BedrockVarInt.readZigzag32(pin)); // block z
        assertEquals(96, BedrockVarInt.readUnsignedVarInt(pin)); // radius（ブロック単位）
        assertEquals(0, pin.remaining());
        ByteReader pin2 = new ByteReader(pubNext);
        assertEquals(16, BedrockVarInt.readZigzag32(pin2));
        assertEquals(64, BedrockVarInt.readUnsignedVarInt(pin2));
        assertEquals(-32, BedrockVarInt.readZigzag32(pin2));
        assertEquals(96, BedrockVarInt.readUnsignedVarInt(pin2));
        assertEquals(0, pin2.readLittleInt()); // saved chunks: なし
        assertEquals(0, pin2.remaining());
    }
}
