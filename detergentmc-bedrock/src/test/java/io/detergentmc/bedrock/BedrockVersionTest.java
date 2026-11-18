package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bedrock バージョン表（プロトコル番号・名称・形式ゲート）のテスト。 */
class BedrockVersionTest {

    @Test
    void allSixtyFourSupportedProtocolsResolve() {
        int[] protocols = {291, 313, 332, 340, 354, 361, 388, 389, 390, 407, 408, 419, 422, 428, 431, 440, 448, 465, 471, 475, 486, 503, 527, 534, 544, 545, 554, 557, 560, 567, 575, 582, 589, 594, 618, 622, 630, 649, 662, 671, 685, 686,
                712, 729, 748, 766, 776, 786, 800, 818, 819, 827,
                844, 859, 860, 898, 924, 944, 975, 1001, 2168, 2169, 2193, 2225};
        String[] names = {"1.7.1", "1.8.1", "1.9.0", "1.10.1", "1.11.4", "1.12.1", "1.13.3", "1.14.41", "1.14.60", "1.16.10", "1.16.61", "1.16.101", "1.16.201", "1.16.210", "1.16.221", "1.17.2", "1.17.11", "1.17.34", "1.17.41", "1.18.2", "1.18.12", "1.18.33", "1.19.2", "1.19.11", "1.19.20", "1.19.22", "1.19.31", "1.19.41", "1.19.51", "1.19.62", "1.19.70", "1.19.83", "1.20.0", "1.20.10", "1.20.30", "1.20.40", "1.20.50",
                "1.20.60", "1.20.70", "1.20.80", "1.21.0", "1.21.2",
                "1.21.20", "1.21.30", "1.21.40", "1.21.50", "1.21.60", "1.21.70",
                "1.21.80", "1.21.90", "1.21.93", "1.21.100",
                "1.21.111", "1.21.120", "1.21.124", "1.21.130", "1.26.0", "1.26.10", "1.26.20",
                "1.26.30", "1.26.40", "1.26.45", "1.26.50", "1.26.60"};
        assertEquals(64, protocols.length, "対応プロトコルは 64 個");
        assertEquals(64, names.length);
        for (int i = 0; i < protocols.length; i++) {
            BedrockVersion version = BedrockVersion.fromProtocol(protocols[i]);
            assertEquals(names[i], version.displayName(),
                    "プロトコル " + protocols[i] + " の表示名");
            assertTrue(BedrockVersion.isSupportedProtocol(protocols[i]));
            assertEquals(protocols[i], version.protocol());
        }
    }

    @Test
    void startGameEduResourceUriGate() {
        // 448 の StartGame は 60 フィールドで edu_resource_uri が無い。
        // 465 は 61 フィールドで edu_resource_uri（EducationSharedResourceURI）
        // が is_new_nether の次にある。
        assertFalse(BedrockVersion.V1_17_11.hasStartGameEduResourceUri());
        assertTrue(BedrockVersion.V1_17_34.hasStartGameEduResourceUri());
        assertTrue(BedrockVersion.V1_17_41.hasStartGameEduResourceUri());
        assertTrue(BedrockVersion.V1_19_2.hasStartGameEduResourceUri());
        assertTrue(BedrockVersion.V1_26_52.hasStartGameEduResourceUri());
    }

    @Test
    void startGameBlockPaletteChecksumGate() {
        // 471 の StartGame は 61 フィールドで block_pallete_checksum が無い。
        // 475 は 62 フィールドで末尾が block_pallete_checksum（lu64）。
        // 527 は block_pallette_checksum（二重 l）だが型・位置は同じ。
        assertFalse(BedrockVersion.V1_17_34.hasStartGameBlockPaletteChecksum());
        assertFalse(BedrockVersion.V1_17_41.hasStartGameBlockPaletteChecksum());
        assertTrue(BedrockVersion.V1_18_2.hasStartGameBlockPaletteChecksum());
        assertTrue(BedrockVersion.V1_18_12.hasStartGameBlockPaletteChecksum());
        assertTrue(BedrockVersion.V1_18_33.hasStartGameBlockPaletteChecksum());
        assertTrue(BedrockVersion.V1_19_2.hasStartGameBlockPaletteChecksum());
        assertTrue(BedrockVersion.V1_26_52.hasStartGameBlockPaletteChecksum());
    }

    @Test
    void startGameRewindFieldsGate() {
        // 422 の StartGame は 57 フィールドで rewind_history_size・
        // server_authoritative_block_breaking が無い（movement_authority の
        // 直後が current_tick）。428 は 59 フィールドでこの 2 フィールドが
        // movement_authority の直後にある。431 以降も同じくある。
        assertFalse(BedrockVersion.V1_16_101.hasStartGameRewindFields());
        assertFalse(BedrockVersion.V1_16_201.hasStartGameRewindFields());
        assertTrue(BedrockVersion.V1_16_210.hasStartGameRewindFields());
        assertTrue(BedrockVersion.V1_16_221.hasStartGameRewindFields());
        assertTrue(BedrockVersion.V1_17_2.hasStartGameRewindFields());
        assertTrue(BedrockVersion.V1_19_2.hasStartGameRewindFields());
        assertTrue(BedrockVersion.V1_26_52.hasStartGameRewindFields());
        // 419 は AddPainting が vec3f で、PlayerList remove に verified は
        // 無い（v390 系シリアライザが v419 まで未上書き。§28 で述語に
        // 下限 422 を追加した）。422 はどちらも protocol.json 通り有り。
        assertFalse(BedrockVersion.V1_16_101.addPaintingUsesBlockCoordinates());
        assertFalse(BedrockVersion.V1_16_101.playerListRemoveHasVerified());
        assertTrue(BedrockVersion.V1_16_201.addPaintingUsesBlockCoordinates());
        assertTrue(BedrockVersion.V1_16_201.playerListRemoveHasVerified());
        assertFalse(BedrockVersion.V1_16_221.addPaintingUsesBlockCoordinates());
        assertFalse(BedrockVersion.V1_16_221.playerListRemoveHasVerified());
        // 408 も rewind 2 フィールドは無い。AddPainting／PlayerList の
        // ゲートは §28 でコーデック実読に基づき改めた：408 の実効
        // シリアライザ（v361／v390 系）では AddPainting は vec3f、
        // PlayerList remove に verified は無い（BE-NOTES.md §28）。
        assertFalse(BedrockVersion.V1_16_61.hasStartGameRewindFields());
        assertFalse(BedrockVersion.V1_16_61.addPaintingUsesBlockCoordinates());
        assertFalse(BedrockVersion.V1_16_61.playerListRemoveHasVerified());
        // 407 も同じ（実効定義が v407 コーデックで 408 と同一。
        // BE-NOTES.md §27・§28）。
        assertFalse(BedrockVersion.V1_16_10.hasStartGameRewindFields());
        assertFalse(BedrockVersion.V1_16_10.addPaintingUsesBlockCoordinates());
        assertFalse(BedrockVersion.V1_16_10.playerListRemoveHasVerified());
        // 390 も rewind 2 フィールドは無く、AddPainting は vec3f、
        // PlayerList remove に verified は無い（v390 コーデック。
        // BE-NOTES.md §28）。
        assertFalse(BedrockVersion.V1_14_60.hasStartGameRewindFields());
        assertFalse(BedrockVersion.V1_14_60.addPaintingUsesBlockCoordinates());
        assertFalse(BedrockVersion.V1_14_60.playerListRemoveHasVerified());
    }

    @Test
    void protocol419BoundaryGates() {
        // 419（1.16.100）で入った形式差分は 7 つ：StartGame の experiments、
        // StartGame の blockProperties 形式、ResourcePackStack の
        // experiments、MovePlayer／SetEntityData／UpdateAttributes の
        // 末尾 tick、MoveEntityDelta の FloatLE 絶対座標形式。
        // 408 にはすべて無い（CloudburstMC Protocol の v408／v419
        // コーデックの差分。BE-NOTES.md §26）。
        assertFalse(BedrockVersion.V1_16_61.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_16_61.hasStartGameBlockProperties());
        assertFalse(BedrockVersion.V1_16_61.hasResourcePackStackExperiments());
        assertFalse(BedrockVersion.V1_16_61.hasMovePlayerTick());
        assertFalse(BedrockVersion.V1_16_61.hasSetEntityDataTick());
        assertFalse(BedrockVersion.V1_16_61.hasUpdateAttributesTick());
        assertFalse(BedrockVersion.V1_16_61.moveEntityDeltaUsesAbsolutePosition());
        assertTrue(BedrockVersion.V1_16_101.hasStartGameExperiments());
        assertTrue(BedrockVersion.V1_16_101.hasStartGameBlockProperties());
        assertTrue(BedrockVersion.V1_16_101.hasResourcePackStackExperiments());
        assertTrue(BedrockVersion.V1_16_101.hasMovePlayerTick());
        assertTrue(BedrockVersion.V1_16_101.hasSetEntityDataTick());
        assertTrue(BedrockVersion.V1_16_101.hasUpdateAttributesTick());
        assertTrue(BedrockVersion.V1_16_101.moveEntityDeltaUsesAbsolutePosition());
        assertTrue(BedrockVersion.V1_16_201.hasStartGameExperiments());
        assertTrue(BedrockVersion.V1_26_52.moveEntityDeltaUsesAbsolutePosition());
    }

    @Test
    void protocol407Matches408Gates() {
        // 407 の実効定義は CloudburstMC Protocol の v407 コーデック
        // そのもので、408 はその CODEC を流用していたため、419 境界の
        // 7 ゲートは 407 もすべて偽で 408 と一致する（BE-NOTES.md §27）。
        assertFalse(BedrockVersion.V1_16_10.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_16_10.hasStartGameBlockProperties());
        assertFalse(BedrockVersion.V1_16_10.hasResourcePackStackExperiments());
        assertFalse(BedrockVersion.V1_16_10.hasMovePlayerTick());
        assertFalse(BedrockVersion.V1_16_10.hasSetEntityDataTick());
        assertFalse(BedrockVersion.V1_16_10.hasUpdateAttributesTick());
        assertFalse(BedrockVersion.V1_16_10.moveEntityDeltaUsesAbsolutePosition());
        // アイテム表は 407 も 408 と同じ 1_16_210 表を共用する（代用。
        // 407 自体のランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_16_10.itemTableTag());
        assertEquals("1_16_210", BedrockVersion.V1_16_61.itemTableTag());
    }

    @Test
    void protocol390BoundaryGates() {
        // 390 の実効定義は CloudburstMC Protocol の v390 コーデック。
        // 419 境界の 7 ゲートは 407 と同じくすべて偽（BE-NOTES.md §28）。
        assertFalse(BedrockVersion.V1_14_60.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_14_60.hasStartGameBlockProperties());
        assertFalse(BedrockVersion.V1_14_60.hasResourcePackStackExperiments());
        assertFalse(BedrockVersion.V1_14_60.hasMovePlayerTick());
        assertFalse(BedrockVersion.V1_14_60.hasSetEntityDataTick());
        assertFalse(BedrockVersion.V1_14_60.hasUpdateAttributesTick());
        assertFalse(BedrockVersion.V1_14_60.moveEntityDeltaUsesAbsolutePosition());
        // 407 境界の新ゲート 2 つ：StartGame の v407 追加分と
        // InventoryTransaction の legacy_request_id は 390 には無く、
        // 407・408 にはある（StartGameSerializer_v388／_v407、
        // InventoryTransactionSerializer_v291／_v407）。
        assertFalse(BedrockVersion.V1_14_60.hasStartGame407Fields());
        assertTrue(BedrockVersion.V1_16_10.hasStartGame407Fields());
        assertTrue(BedrockVersion.V1_16_61.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_14_60.transactionHasLegacyRequestId());
        assertTrue(BedrockVersion.V1_16_10.transactionHasLegacyRequestId());
        assertTrue(BedrockVersion.V1_16_61.transactionHasLegacyRequestId());
        // アイテム表は 390 も 1_16_210 表を共用する（代用。1.14 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_14_60.itemTableTag());
    }

    @Test
    void protocol388BoundaryGates() {
        // 388 の実効定義は CloudburstMC Protocol の v388 コーデック。
        // 389 との差分は PARTICLE_TYPES／LEVEL_EVENTS／ENTITY_DATA の
        // トランスフォーマ／Event 系シリアライザだけで、この実装の
        // 送受信範囲にワイヤ差は無く、全ゲートが 389 と完全一致する
        // （BE-NOTES.md §30）。
        assertFalse(BedrockVersion.V1_13_3.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_13_3.hasStartGameBlockProperties());
        assertFalse(BedrockVersion.V1_13_3.hasResourcePackStackExperiments());
        assertFalse(BedrockVersion.V1_13_3.hasMovePlayerTick());
        assertFalse(BedrockVersion.V1_13_3.hasSetEntityDataTick());
        assertFalse(BedrockVersion.V1_13_3.hasUpdateAttributesTick());
        assertFalse(BedrockVersion.V1_13_3.moveEntityDeltaUsesAbsolutePosition());
        assertFalse(BedrockVersion.V1_13_3.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_13_3.transactionHasLegacyRequestId());
        // PlayerList add の verified は 390 で追加されたため、388 は
        // 389 と同じく無し（388 自身が PlayerListSerializer_v388 を
        // 使う。trustedSkin は v390 の BedrockCodecHelper_v390 が追加）。
        assertFalse(BedrockVersion.V1_13_3.playerListAddHasVerified());
        assertFalse(BedrockVersion.V1_14_41.playerListAddHasVerified());
        assertTrue(BedrockVersion.V1_14_60.playerListAddHasVerified());
        // remove の verified と AddPainting の境界は §28 のまま（388 でも偽）。
        assertFalse(BedrockVersion.V1_13_3.playerListRemoveHasVerified());
        assertFalse(BedrockVersion.V1_13_3.addPaintingUsesBlockCoordinates());
        // アイテム表は 388 も 1_16_210 表を共用する（代用。1.13 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_13_3.itemTableTag());
    }

    @Test
    void protocol361BoundaryGates() {
        // 361 の実効定義は CloudburstMC Protocol の v361 コーデック。
        // 388 境界のゲートが新たに生まれ、最小の >= 閾値は 388 になった
        // （BE-NOTES.md §31）。
        // StartGame：vanillaVersion 文字列と movement_authority は
        // 388 で新規。361 のパレットはレガシー配列形式。
        assertFalse(BedrockVersion.V1_12_1.hasStartGameVanillaVersion());
        assertTrue(BedrockVersion.V1_13_3.hasStartGameVanillaVersion());
        assertFalse(BedrockVersion.V1_12_1.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_13_3.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_12_1.hasStartGameLegacyPaletteArray());
        assertFalse(BedrockVersion.V1_13_3.hasStartGameLegacyPaletteArray());
        // 419・407 境界のゲートは 388 と同じくすべて偽。
        assertFalse(BedrockVersion.V1_12_1.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_12_1.hasStartGameBlockProperties());
        assertFalse(BedrockVersion.V1_12_1.hasResourcePackStackExperiments());
        assertFalse(BedrockVersion.V1_12_1.hasMovePlayerTick());
        assertFalse(BedrockVersion.V1_12_1.hasSetEntityDataTick());
        assertFalse(BedrockVersion.V1_12_1.hasUpdateAttributesTick());
        assertFalse(BedrockVersion.V1_12_1.moveEntityDeltaUsesAbsolutePosition());
        assertFalse(BedrockVersion.V1_12_1.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_12_1.transactionHasLegacyRequestId());
        // PlayerList：361 は旧直書きエントリ。verified は両世代とも無し。
        assertTrue(BedrockVersion.V1_12_1.playerListUsesLegacyEntry());
        assertFalse(BedrockVersion.V1_13_3.playerListUsesLegacyEntry());
        assertFalse(BedrockVersion.V1_12_1.playerListAddHasVerified());
        assertFalse(BedrockVersion.V1_12_1.playerListRemoveHasVerified());
        assertFalse(BedrockVersion.V1_12_1.addPaintingUsesBlockCoordinates());
        // AddPlayer の末尾 buildPlatform と ResourcePackStack の末尾
        // gameVersion は 388 で新規。
        assertFalse(BedrockVersion.V1_12_1.addPlayerHasBuildPlatform());
        assertTrue(BedrockVersion.V1_13_3.addPlayerHasBuildPlatform());
        assertFalse(BedrockVersion.V1_12_1.resourcePackStackHasGameVersion());
        assertTrue(BedrockVersion.V1_13_3.resourcePackStackHasGameVersion());
        // アイテム表は 361 も 1_16_210 表を共用する（代用。1.12 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_12_1.itemTableTag());
    }

    @Test
    void protocol332BoundaryGates() {
        // 332 の実効定義は CloudburstMC Protocol の v332 コーデック。
        // 送受信範囲のワイヤ形式は 340 と全て同一で、新しい境界は
        // 生まれない（最小の >= 閾値は 361 のまま。BE-NOTES.md §34）。
        // 361 境界のゲートは 340 と同じ値になる。
        assertFalse(BedrockVersion.V1_9_0.hasStartGameOnlySpawningV1Villagers());
        assertFalse(BedrockVersion.V1_9_0.hasItemstates());
        assertTrue(BedrockVersion.V1_9_0.hasStartGameLegacyPaletteArray());
        assertFalse(BedrockVersion.V1_9_0.hasStartGameLegacyPaletteEntryId());
        assertFalse(BedrockVersion.V1_9_0.hasLevelChunkSubChunkCount());
        // AddPainting は 332 もブロック座標（_v332 自身。<= 354 の範囲）。
        assertTrue(BedrockVersion.V1_9_0.addPaintingUsesBlockCoordinates());
        // 388 境界のゲートは 340 と同じくすべて偽（PlayerList は
        // 旧エントリのまま）。
        assertFalse(BedrockVersion.V1_9_0.hasStartGameVanillaVersion());
        assertFalse(BedrockVersion.V1_9_0.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_9_0.playerListUsesLegacyEntry());
        assertFalse(BedrockVersion.V1_9_0.addPlayerHasBuildPlatform());
        assertFalse(BedrockVersion.V1_9_0.resourcePackStackHasGameVersion());
        // 419・407 境界のゲートも 340 と同じくすべて偽。
        assertFalse(BedrockVersion.V1_9_0.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_9_0.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_9_0.transactionHasLegacyRequestId());
        // アイテム表は 332 も 1_16_210 表を共用する（代用。1.9 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_9_0.itemTableTag());
    }

    @Test
    void protocol313BoundaryGates() {
        // 313 の実効定義は CloudburstMC Protocol の v313 コーデック。
        // 332 とのワイヤ差は 2 点だけで、新しい境界が 2 つ生まれた
        // （BE-NOTES.md §35）。
        // StartGame の LevelSettings は _v291 レイアウト
        // （StartGameSerializer_v313）で、313 のみが該当する。
        assertTrue(BedrockVersion.V1_8_1.startGameUsesV291LevelSettings());
        assertFalse(BedrockVersion.V1_9_0.startGameUsesV291LevelSettings());
        assertFalse(BedrockVersion.V1_20_50.startGameUsesV291LevelSettings());
        // ResourcePacksInfo の scriptingEnabled は 332 で新規。
        assertFalse(BedrockVersion.V1_8_1.resourcePacksInfoHasScripting());
        assertTrue(BedrockVersion.V1_9_0.resourcePacksInfoHasScripting());
        assertTrue(BedrockVersion.V1_26_52.resourcePacksInfoHasScripting());
        // 361 境界のゲートは 332 と同じ値になる。
        assertFalse(BedrockVersion.V1_8_1.hasStartGameOnlySpawningV1Villagers());
        assertFalse(BedrockVersion.V1_8_1.hasItemstates());
        assertTrue(BedrockVersion.V1_8_1.hasStartGameLegacyPaletteArray());
        assertFalse(BedrockVersion.V1_8_1.hasStartGameLegacyPaletteEntryId());
        assertFalse(BedrockVersion.V1_8_1.hasLevelChunkSubChunkCount());
        // AddPainting は 313 もブロック座標（_v291。_v332 と同一実装で、
        // <= 354 の範囲）。
        assertTrue(BedrockVersion.V1_8_1.addPaintingUsesBlockCoordinates());
        // 388 境界のゲートは 332 と同じくすべて偽（PlayerList は
        // 旧エントリのまま）。
        assertFalse(BedrockVersion.V1_8_1.hasStartGameVanillaVersion());
        assertFalse(BedrockVersion.V1_8_1.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_8_1.playerListUsesLegacyEntry());
        assertFalse(BedrockVersion.V1_8_1.addPlayerHasBuildPlatform());
        assertFalse(BedrockVersion.V1_8_1.resourcePackStackHasGameVersion());
        // 419・407 境界のゲートも 332 と同じくすべて偽。
        assertFalse(BedrockVersion.V1_8_1.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_8_1.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_8_1.transactionHasLegacyRequestId());
        // アイテム表は 313 も 1_16_210 表を共用する（代用。1.8 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_8_1.itemTableTag());
    }

    @Test
    void protocol291BoundaryGates() {
        // 291 の実効定義は CloudburstMC Protocol の v291 コーデック
        // （継承チェーンの最古）。313 とのワイヤ差は 3 点で、新しい
        // 境界が 3 つ生まれた（BE-NOTES.md §36）。
        // StartGame の LevelSettings は 291 が _v291 の純粋な
        // 31 フィールドで、313 の _v313 レイアウト（末尾 2 bool 追加）
        // は 313 のみ。
        assertTrue(BedrockVersion.V1_7_1.startGameUsesV291LevelSettings());
        assertFalse(BedrockVersion.V1_7_1.startGameUsesV313LevelSettings());
        assertTrue(BedrockVersion.V1_8_1.startGameUsesV313LevelSettings());
        assertFalse(BedrockVersion.V1_9_0.startGameUsesV313LevelSettings());
        // ResourcePackStack は 291 が _v291 形式（末尾の legacy
        // experiment bool 無し）で、313 以降は bool あり。
        assertTrue(BedrockVersion.V1_7_1.resourcePackStackUsesV291Format());
        assertFalse(BedrockVersion.V1_8_1.resourcePackStackUsesV291Format());
        assertFalse(BedrockVersion.V1_20_50.resourcePackStackUsesV291Format());
        // AddEntity のタイプは 291 のみ旧来の数値 ID。
        assertTrue(BedrockVersion.V1_7_1.addEntityUsesLegacyTypeId());
        assertFalse(BedrockVersion.V1_8_1.addEntityUsesLegacyTypeId());
        assertFalse(BedrockVersion.V1_26_52.addEntityUsesLegacyTypeId());
        // NetworkChunkPublisherUpdate は 313 で新規登録（291 には無い）。
        assertFalse(BedrockVersion.V1_7_1.hasNetworkChunkPublisherUpdate());
        assertTrue(BedrockVersion.V1_8_1.hasNetworkChunkPublisherUpdate());
        assertTrue(BedrockVersion.V1_26_52.hasNetworkChunkPublisherUpdate());
        // 313 と同じ値になる既存ゲート（パレット配列・PlayerList
        // 旧エントリ・AddPainting ブロック座標・scripting 無し）。
        assertTrue(BedrockVersion.V1_7_1.hasStartGameLegacyPaletteArray());
        assertFalse(BedrockVersion.V1_7_1.hasStartGameLegacyPaletteEntryId());
        assertFalse(BedrockVersion.V1_7_1.hasLevelChunkSubChunkCount());
        assertTrue(BedrockVersion.V1_7_1.playerListUsesLegacyEntry());
        assertTrue(BedrockVersion.V1_7_1.addPaintingUsesBlockCoordinates());
        assertFalse(BedrockVersion.V1_7_1.resourcePacksInfoHasScripting());
        assertFalse(BedrockVersion.V1_7_1.resourcePackStackHasGameVersion());
        assertFalse(BedrockVersion.V1_7_1.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_7_1.transactionHasLegacyRequestId());
        // 数値タイプ ID の対応表（PocketMine-MP 3.4.0 の EntityIds）。
        assertEquals(32, BedrockLegacyEntityTypeIds.legacyIdFor("minecraft:zombie"));
        assertEquals(10, BedrockLegacyEntityTypeIds.legacyIdFor("minecraft:chicken"));
        assertEquals(80, BedrockLegacyEntityTypeIds.legacyIdFor("minecraft:arrow"));
        assertEquals(15, BedrockLegacyEntityTypeIds.legacyIdFor("minecraft:villager_v2"));
        assertEquals(112, BedrockLegacyEntityTypeIds.legacyIdFor("minecraft:cod"));
        assertEquals(-1, BedrockLegacyEntityTypeIds.legacyIdFor("minecraft:phantom"));
        assertEquals(-1, BedrockLegacyEntityTypeIds.legacyIdFor("minecraft:bee"));
        // アイテム表は 291 も 1_16_210 表を共用する（代用。PMMP
        // BedrockData に bedrock-1.7.0 タグ自体が存在せず、1.7 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_7_1.itemTableTag());
    }

    @Test
    void protocol340BoundaryGates() {
        // 340 の実効定義は CloudburstMC Protocol の v340 コーデック。
        // 送受信範囲のワイヤ形式は 354 と全て同一で、新しい境界は
        // 生まれない（最小の >= 閾値は 361 のまま。BE-NOTES.md §33）。
        // 361 境界のゲートは 354 と同じ値になる。
        assertFalse(BedrockVersion.V1_10_1.hasStartGameOnlySpawningV1Villagers());
        assertFalse(BedrockVersion.V1_10_1.hasItemstates());
        assertTrue(BedrockVersion.V1_10_1.hasStartGameLegacyPaletteArray());
        assertFalse(BedrockVersion.V1_10_1.hasStartGameLegacyPaletteEntryId());
        assertFalse(BedrockVersion.V1_10_1.hasLevelChunkSubChunkCount());
        // AddPainting は 340 もブロック座標（_v332。<= 354 の範囲）。
        assertTrue(BedrockVersion.V1_10_1.addPaintingUsesBlockCoordinates());
        // 388 境界のゲートは 354 と同じくすべて偽（PlayerList は
        // 旧エントリのまま）。
        assertFalse(BedrockVersion.V1_10_1.hasStartGameVanillaVersion());
        assertFalse(BedrockVersion.V1_10_1.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_10_1.playerListUsesLegacyEntry());
        assertFalse(BedrockVersion.V1_10_1.addPlayerHasBuildPlatform());
        assertFalse(BedrockVersion.V1_10_1.resourcePackStackHasGameVersion());
        // 419・407 境界のゲートも 354 と同じくすべて偽。
        assertFalse(BedrockVersion.V1_10_1.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_10_1.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_10_1.transactionHasLegacyRequestId());
        // アイテム表は 340 も 1_16_210 表を共用する（代用。1.10 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_10_1.itemTableTag());
    }

    @Test
    void protocol354BoundaryGates() {
        // 354 の実効定義は CloudburstMC Protocol の v354 コーデック。
        // 361 境界のゲートが新たに生まれ、最小の >= 閾値は 361 になった
        // （BE-NOTES.md §32）。
        // StartGame：onlySpawningV1Villagers と itemstates は 361 で
        // 新規。パレット配列の要素 id も 361 のみ。
        assertFalse(BedrockVersion.V1_11_4.hasStartGameOnlySpawningV1Villagers());
        assertTrue(BedrockVersion.V1_12_1.hasStartGameOnlySpawningV1Villagers());
        assertFalse(BedrockVersion.V1_11_4.hasItemstates());
        assertTrue(BedrockVersion.V1_12_1.hasItemstates());
        assertTrue(BedrockVersion.V1_11_4.hasStartGameLegacyPaletteArray());
        assertFalse(BedrockVersion.V1_11_4.hasStartGameLegacyPaletteEntryId());
        assertTrue(BedrockVersion.V1_12_1.hasStartGameLegacyPaletteEntryId());
        // LevelChunk の subChunkCount・キャッシュ可否は 361 で新規。
        assertFalse(BedrockVersion.V1_11_4.hasLevelChunkSubChunkCount());
        assertTrue(BedrockVersion.V1_12_1.hasLevelChunkSubChunkCount());
        // AddPainting は 354 がブロック座標、361 は vec3f。
        assertTrue(BedrockVersion.V1_11_4.addPaintingUsesBlockCoordinates());
        assertFalse(BedrockVersion.V1_12_1.addPaintingUsesBlockCoordinates());
        // 388 境界のゲートは 361 と同じくすべて偽（PlayerList は
        // 旧エントリのまま）。
        assertFalse(BedrockVersion.V1_11_4.hasStartGameVanillaVersion());
        assertFalse(BedrockVersion.V1_11_4.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_11_4.playerListUsesLegacyEntry());
        assertFalse(BedrockVersion.V1_11_4.addPlayerHasBuildPlatform());
        assertFalse(BedrockVersion.V1_11_4.resourcePackStackHasGameVersion());
        // 419・407 境界のゲートもすべて偽。
        assertFalse(BedrockVersion.V1_11_4.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_11_4.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_11_4.transactionHasLegacyRequestId());
        // アイテム表は 354 も 1_16_210 表を共用する（代用。1.11 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_11_4.itemTableTag());
    }

    @Test
    void protocol389BoundaryGates() {
        // 389 の実効定義は CloudburstMC Protocol の v389 コーデック。
        // 419 境界の 7 ゲートは 390・407 と同じくすべて偽（BE-NOTES.md §29）。
        assertFalse(BedrockVersion.V1_14_41.hasStartGameExperiments());
        assertFalse(BedrockVersion.V1_14_41.hasStartGameBlockProperties());
        assertFalse(BedrockVersion.V1_14_41.hasResourcePackStackExperiments());
        assertFalse(BedrockVersion.V1_14_41.hasMovePlayerTick());
        assertFalse(BedrockVersion.V1_14_41.hasSetEntityDataTick());
        assertFalse(BedrockVersion.V1_14_41.hasUpdateAttributesTick());
        assertFalse(BedrockVersion.V1_14_41.moveEntityDeltaUsesAbsolutePosition());
        // 407 境界のゲート 2 つも 390 と同じく偽（StartGame は
        // StartGameSerializer_v388、Transaction は v291 世代の基本形式）。
        assertFalse(BedrockVersion.V1_14_41.hasStartGame407Fields());
        assertFalse(BedrockVersion.V1_14_41.transactionHasLegacyRequestId());
        // 390 境界の新ゲート：PlayerList add の verified（trustedSkin）は
        // 390 で追加され、389 の PlayerListSerializer_v388 には無い。
        assertFalse(BedrockVersion.V1_14_41.playerListAddHasVerified());
        assertTrue(BedrockVersion.V1_14_60.playerListAddHasVerified());
        assertTrue(BedrockVersion.V1_16_10.playerListAddHasVerified());
        assertTrue(BedrockVersion.V1_26_52.playerListAddHasVerified());
        // remove の verified と AddPainting の境界は §28 のまま（389 でも偽）。
        assertFalse(BedrockVersion.V1_14_41.playerListRemoveHasVerified());
        assertFalse(BedrockVersion.V1_14_41.addPaintingUsesBlockCoordinates());
        // アイテム表は 389 も 1_16_210 表を共用する（代用。1.14 系の
        // ランタイム ID 実データは存在しない）。
        assertEquals("1_16_210", BedrockVersion.V1_14_41.itemTableTag());
    }

    @Test
    void unknownProtocolsAreNotSupportedAnd2225IsNetherNetGeneration() {
        assertNull(BedrockVersion.fromProtocol(0));
        assertNull(BedrockVersion.fromProtocol(588));
        assertNull(BedrockVersion.fromProtocol(631));
        assertNull(BedrockVersion.fromProtocol(2194));
        assertFalse(BedrockVersion.isSupportedProtocol(0));
        // 2225 はパケット形式としては対応する（V1_26_60 が引ける）が、
        // NetherNet 世代であることに変わりはない。RakNet 側のセッションは
        // isNetherNetProtocol を先に見るため、引き続き切断される
        // （BedrockSessionVersionTest を参照。BE-NOTES.md §24）。
        assertTrue(BedrockVersion.isSupportedProtocol(2225),
                "26.60（2225）はパケット形式として対応する");
        assertEquals(BedrockVersion.V1_26_60, BedrockVersion.fromProtocol(2225),
                "fromProtocol(2225) は最初に宣言された V1_26_60 を返す");
        assertTrue(BedrockVersion.isNetherNetProtocol(2225));
        assertFalse(BedrockVersion.isNetherNetProtocol(2193));
        assertEquals(2225, BedrockVersion.NETHERNET_PROTOCOL);
    }

    @Test
    void protocol2225Gates() {
        // 2225 で増える形式差分は 4 つ（StartGame の
        // editor_level_migration_version、AddPlayer／AddEntity の
        // passenger_of_block、LevelChunk の client_biome_update、
        // Transaction の hand）。2193 にはすべて無い
        // （1.26.60 の protocol.json で実測。BE-NOTES.md §24）。
        assertFalse(BedrockVersion.V1_26_52.hasEditorLevelMigrationVersion());
        assertTrue(BedrockVersion.V1_26_60.hasEditorLevelMigrationVersion());
        assertTrue(BedrockVersion.V1_26_65.hasEditorLevelMigrationVersion());
        assertFalse(BedrockVersion.V1_26_52.hasPassengerOfBlock());
        assertTrue(BedrockVersion.V1_26_60.hasPassengerOfBlock());
        assertFalse(BedrockVersion.V1_26_52.hasClientBiomeUpdate());
        assertTrue(BedrockVersion.V1_26_60.hasClientBiomeUpdate());
        assertFalse(BedrockVersion.V1_26_52.transactionHasHand());
        assertTrue(BedrockVersion.V1_26_60.transactionHasHand());
        // アイテム表は 2193 と同じ 1_26_30 を共用する。
        assertEquals("1_26_30", BedrockVersion.V1_26_60.itemTableTag());
        assertEquals("1_26_30", BedrockVersion.V1_26_65.itemTableTag());
    }

    @Test
    void startGameGatesMatchVerifiedShapes() {
        // Shape A0（1.17.34）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（465 の protocol.json で実測。
        // StartGame は 61 フィールドで 471 と JSON 完全一致。471 との
        // 差分は subchunk・subchunk_request の有無と packet_animate・
        // ItemStackRequest・level_event の型のみで、StartGame・
        // add_player・add_entity・set_entity_data は 465＝471 で JSON 完全一致）。
        assertFalse(BedrockVersion.V1_17_34.hasHardcore());
        assertFalse(BedrockVersion.V1_17_34.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_17_34.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_17_34.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_17_34.hasItemstates());
        assertFalse(BedrockVersion.V1_17_34.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_17_34.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_17_34.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_17_34.hasChatLogging());
        assertFalse(BedrockVersion.V1_17_34.hasEntityProperties());
        assertFalse(BedrockVersion.V1_17_34.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_17_34.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_17_34.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_17_34.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_17_34.hasAttributeModifiers());
        assertFalse(BedrockVersion.V1_17_34.hasSavedChunks());
        assertFalse(BedrockVersion.V1_17_34.hasEditorWorld());
        assertFalse(BedrockVersion.V1_17_34.hasPermissionLevelMapper());
        assertFalse(BedrockVersion.V1_17_34.hasAddPlayerAbilitiesArray());
        assertFalse(BedrockVersion.V1_17_34.hasAddEntityBodyYaw());
        assertFalse(BedrockVersion.V1_17_34.hasStartGame527Fields());
        assertFalse(BedrockVersion.V1_17_34.hasStartGameLu64Seed());
        assertFalse(BedrockVersion.V1_17_34.hasAddPlayerGamemode());
        assertFalse(BedrockVersion.V1_17_34.hasStartGameBlockPaletteChecksum());
        assertEquals("1_17_40", BedrockVersion.V1_17_34.itemTableTag());
        // Shape A0（1.17.41）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（471 の protocol.json で実測。
        // StartGame は 61 フィールド。475 との差分は末尾の
        // block_pallete_checksum の 1 フィールドのみ（471 には無い）。
        // add_player・add_entity・set_entity_data は 471＝475 で JSON 完全一致）。
        assertFalse(BedrockVersion.V1_17_41.hasHardcore());
        assertFalse(BedrockVersion.V1_17_41.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_17_41.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_17_41.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_17_41.hasItemstates());
        assertFalse(BedrockVersion.V1_17_41.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_17_41.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_17_41.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_17_41.hasChatLogging());
        assertFalse(BedrockVersion.V1_17_41.hasEntityProperties());
        assertFalse(BedrockVersion.V1_17_41.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_17_41.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_17_41.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_17_41.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_17_41.hasAttributeModifiers());
        assertFalse(BedrockVersion.V1_17_41.hasSavedChunks());
        assertFalse(BedrockVersion.V1_17_41.hasEditorWorld());
        assertFalse(BedrockVersion.V1_17_41.hasPermissionLevelMapper());
        assertFalse(BedrockVersion.V1_17_41.hasAddPlayerAbilitiesArray());
        assertFalse(BedrockVersion.V1_17_41.hasAddEntityBodyYaw());
        assertFalse(BedrockVersion.V1_17_41.hasStartGame527Fields());
        assertFalse(BedrockVersion.V1_17_41.hasStartGameLu64Seed());
        assertFalse(BedrockVersion.V1_17_41.hasAddPlayerGamemode());
        assertFalse(BedrockVersion.V1_17_41.hasStartGameBlockPaletteChecksum());
        assertEquals("1_17_40", BedrockVersion.V1_17_41.itemTableTag());
        // Shape A0（1.18.12）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（486 の protocol.json で実測。
        // StartGame は 62 フィールド。503 との差分は seed の型（486 は
        // zigzag32、503 は lu64）と dimension の型（486 は zigzag32、503 は
        // mapper。overworld＝0 はワイヤ同一）。add_player は 21 フィールドで
        // entity_id_self・gamemode 無し、add_entity に body_yaw は無い。
        // UpdateAttributes に modifiers、NetworkChunkPublisherUpdate に
        // saved_chunks は無い）。
        assertFalse(BedrockVersion.V1_18_2.hasHardcore());
        assertFalse(BedrockVersion.V1_18_2.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_18_2.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_18_2.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_18_2.hasItemstates());
        assertFalse(BedrockVersion.V1_18_2.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_18_2.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_18_2.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_18_2.hasChatLogging());
        assertFalse(BedrockVersion.V1_18_2.hasEntityProperties());
        assertFalse(BedrockVersion.V1_18_2.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_18_2.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_18_2.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_18_2.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_18_2.hasAttributeModifiers());
        assertFalse(BedrockVersion.V1_18_2.hasSavedChunks());
        assertFalse(BedrockVersion.V1_18_2.hasEditorWorld());
        assertFalse(BedrockVersion.V1_18_2.hasPermissionLevelMapper());
        assertFalse(BedrockVersion.V1_18_2.hasAddPlayerAbilitiesArray());
        assertFalse(BedrockVersion.V1_18_2.hasAddEntityBodyYaw());
        assertFalse(BedrockVersion.V1_18_2.hasStartGame527Fields());
        assertFalse(BedrockVersion.V1_18_2.hasStartGameLu64Seed());
        assertFalse(BedrockVersion.V1_18_2.hasAddPlayerGamemode());
        assertEquals("1_18_0", BedrockVersion.V1_18_2.itemTableTag());
        assertFalse(BedrockVersion.V1_18_12.hasHardcore());
        assertFalse(BedrockVersion.V1_18_12.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_18_12.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_18_12.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_18_12.hasItemstates());
        assertFalse(BedrockVersion.V1_18_12.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_18_12.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_18_12.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_18_12.hasChatLogging());
        assertFalse(BedrockVersion.V1_18_12.hasEntityProperties());
        assertFalse(BedrockVersion.V1_18_12.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_18_12.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_18_12.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_18_12.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_18_12.hasAttributeModifiers());
        assertFalse(BedrockVersion.V1_18_12.hasSavedChunks());
        assertFalse(BedrockVersion.V1_18_12.hasEditorWorld());
        assertFalse(BedrockVersion.V1_18_12.hasPermissionLevelMapper());
        assertFalse(BedrockVersion.V1_18_12.hasAddPlayerAbilitiesArray());
        assertFalse(BedrockVersion.V1_18_12.hasAddEntityBodyYaw());
        assertFalse(BedrockVersion.V1_18_12.hasStartGame527Fields());
        assertFalse(BedrockVersion.V1_18_12.hasStartGameLu64Seed());
        assertFalse(BedrockVersion.V1_18_12.hasAddPlayerGamemode());
        assertEquals("1_18_10", BedrockVersion.V1_18_12.itemTableTag());
        assertTrue(BedrockVersion.V1_18_33.hasStartGameLu64Seed());
        assertTrue(BedrockVersion.V1_18_33.hasAddPlayerGamemode());
        // Shape A0（1.18.33）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（503 の protocol.json で実測。
        // StartGame は 62 フィールド。527 との差分は property_data・
        // world_template_id の 2 フィールドとチェックサム欄の綴り
        // （block_pallete_checksum（単 l））。add_player は旧形式
        // （能力系を直書き）、add_entity に body_yaw は無い。
        // UpdateAttributes に modifiers、NetworkChunkPublisherUpdate に
        // saved_chunks は無い）。
        assertFalse(BedrockVersion.V1_18_33.hasHardcore());
        assertFalse(BedrockVersion.V1_18_33.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_18_33.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_18_33.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_18_33.hasItemstates());
        assertFalse(BedrockVersion.V1_18_33.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_18_33.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_18_33.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_18_33.hasChatLogging());
        assertFalse(BedrockVersion.V1_18_33.hasEntityProperties());
        assertFalse(BedrockVersion.V1_18_33.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_18_33.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_18_33.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_18_33.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_18_33.hasAttributeModifiers());
        assertFalse(BedrockVersion.V1_18_33.hasSavedChunks());
        assertFalse(BedrockVersion.V1_18_33.hasEditorWorld());
        assertFalse(BedrockVersion.V1_18_33.hasPermissionLevelMapper());
        assertFalse(BedrockVersion.V1_18_33.hasAddPlayerAbilitiesArray());
        assertFalse(BedrockVersion.V1_18_33.hasAddEntityBodyYaw());
        assertFalse(BedrockVersion.V1_18_33.hasStartGame527Fields());
        assertTrue(BedrockVersion.V1_19_2.hasStartGame527Fields());
        // Shape A0（1.19.2）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（527 の protocol.json で実測。
        // StartGame は 64 フィールド。534 との差分は editor_world の
        // 1 フィールドのみ（permission_level の型も違う）。add_player は
        // 旧形式（能力系を直書き）、add_entity に body_yaw は無い。
        // UpdateAttributes に modifiers、NetworkChunkPublisherUpdate に
        // saved_chunks は無い）。
        assertFalse(BedrockVersion.V1_19_2.hasHardcore());
        assertFalse(BedrockVersion.V1_19_2.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_2.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_2.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_2.hasItemstates());
        assertFalse(BedrockVersion.V1_19_2.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_2.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_2.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_2.hasChatLogging());
        assertFalse(BedrockVersion.V1_19_2.hasEntityProperties());
        assertFalse(BedrockVersion.V1_19_2.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_19_2.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_19_2.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_19_2.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_19_2.hasAttributeModifiers());
        assertFalse(BedrockVersion.V1_19_2.hasSavedChunks());
        assertFalse(BedrockVersion.V1_19_2.hasEditorWorld());
        assertFalse(BedrockVersion.V1_19_2.hasPermissionLevelMapper());
        assertFalse(BedrockVersion.V1_19_2.hasAddPlayerAbilitiesArray());
        assertFalse(BedrockVersion.V1_19_2.hasAddEntityBodyYaw());
        // Shape A0（1.19.11）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（534 の protocol.json で実測。
        // StartGame は 65 フィールド。544 との差分は 544 追加分 5 フィールド
        // のみ。add_player・add_entity・set_entity_data に properties は無い。
        // UpdateAttributes に modifiers、NetworkChunkPublisherUpdate に
        // saved_chunks は無い）。
        assertFalse(BedrockVersion.V1_19_11.hasHardcore());
        assertFalse(BedrockVersion.V1_19_11.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_11.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_11.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_11.hasItemstates());
        assertFalse(BedrockVersion.V1_19_11.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_11.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_11.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_11.hasChatLogging());
        assertFalse(BedrockVersion.V1_19_11.hasEntityProperties());
        assertFalse(BedrockVersion.V1_19_11.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_19_11.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_19_11.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_19_11.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_19_11.hasAttributeModifiers());
        assertFalse(BedrockVersion.V1_19_11.hasSavedChunks());
        assertTrue(BedrockVersion.V1_19_11.hasEditorWorld());
        assertTrue(BedrockVersion.V1_19_11.hasPermissionLevelMapper());
        assertTrue(BedrockVersion.V1_19_11.hasAddPlayerAbilitiesArray());
        assertTrue(BedrockVersion.V1_19_11.hasAddEntityBodyYaw());
        // Shape A0（1.19.20）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（544 の protocol.json は
        // 545 のものとバイト同一で、StartGame は 545 と JSON 完全一致）。
        // add_player・add_entity・set_entity_data に properties は無い。
        assertFalse(BedrockVersion.V1_19_20.hasHardcore());
        assertFalse(BedrockVersion.V1_19_20.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_20.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_20.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_20.hasItemstates());
        assertFalse(BedrockVersion.V1_19_20.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_20.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_20.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_20.hasChatLogging());
        assertFalse(BedrockVersion.V1_19_20.hasEntityProperties());
        // 544 追加分あり（534 との差分）。emote_chat_muted・582 追加分・
        // server_controlled_sound はまだ無い。modifiers・saved_chunks あり。
        assertTrue(BedrockVersion.V1_19_20.hasStartGame544Fields());
        assertFalse(BedrockVersion.V1_19_20.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_19_20.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_19_20.hasServerControlledSound());
        assertTrue(BedrockVersion.V1_19_20.hasAttributeModifiers());
        assertTrue(BedrockVersion.V1_19_20.hasSavedChunks());
        // Shape A0（1.19.22）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（545 の protocol.json で実測。
        // StartGame は 554・557・560 と JSON 完全一致で 70 フィールド）。
        // add_player・add_entity・set_entity_data に properties は無い。
        assertFalse(BedrockVersion.V1_19_22.hasHardcore());
        assertFalse(BedrockVersion.V1_19_22.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_22.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_22.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_22.hasItemstates());
        assertFalse(BedrockVersion.V1_19_22.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_22.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_22.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_22.hasChatLogging());
        assertFalse(BedrockVersion.V1_19_22.hasEntityProperties());
        // Shape A0（1.19.31）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（554 の protocol.json で実測。
        // StartGame は 557・560 と JSON 完全一致で 70 フィールド。567 との
        // 差分は emote_chat_muted の 1 フィールドのみ）。
        // add_player・add_entity・set_entity_data に properties は無い。
        assertFalse(BedrockVersion.V1_19_31.hasHardcore());
        assertFalse(BedrockVersion.V1_19_31.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_31.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_31.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_31.hasItemstates());
        assertFalse(BedrockVersion.V1_19_31.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_31.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_31.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_31.hasChatLogging());
        assertFalse(BedrockVersion.V1_19_31.hasEntityProperties());
        // Shape A0（1.19.41）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（557 の protocol.json で実測。
        // StartGame は 560 と JSON 完全一致で 70 フィールド。567 との
        // 差分は emote_chat_muted の 1 フィールドのみ）。
        assertFalse(BedrockVersion.V1_19_41.hasHardcore());
        assertFalse(BedrockVersion.V1_19_41.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_41.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_41.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_41.hasItemstates());
        assertFalse(BedrockVersion.V1_19_41.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_41.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_41.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_41.hasChatLogging());
        assertTrue(BedrockVersion.V1_19_41.hasEntityProperties());
        // Shape A0（1.19.51）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（560 の protocol.json で実測。
        // StartGame は 557 と JSON 完全一致で 70 フィールド。567 との
        // 差分は emote_chat_muted の 1 フィールドのみ）。
        assertFalse(BedrockVersion.V1_19_51.hasHardcore());
        assertFalse(BedrockVersion.V1_19_51.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_51.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_51.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_51.hasItemstates());
        assertFalse(BedrockVersion.V1_19_51.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_51.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_51.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_51.hasChatLogging());
        // 560 までは emote_chat_muted が無い（567 で追加）。
        assertFalse(BedrockVersion.V1_19_51.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_19_51.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_19_51.hasServerControlledSound());
        // Shape A0（1.19.62）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（567 の protocol.json で実測。
        // StartGame は 575 と JSON 完全一致で 71 フィールド）。
        assertFalse(BedrockVersion.V1_19_62.hasHardcore());
        assertFalse(BedrockVersion.V1_19_62.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_62.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_62.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_62.hasItemstates());
        assertFalse(BedrockVersion.V1_19_62.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_62.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_62.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_62.hasChatLogging());
        // 567 で emote_chat_muted が追加。582 追加分・server_controlled_sound はまだ無い。
        assertTrue(BedrockVersion.V1_19_62.hasEmoteChatMuted());
        assertFalse(BedrockVersion.V1_19_62.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_19_62.hasServerControlledSound());
        // Shape A0（1.19.70）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（575 の protocol.json で実測。
        // 582 との StartGame の差分は 3 フィールドのみ）。
        assertFalse(BedrockVersion.V1_19_70.hasHardcore());
        assertFalse(BedrockVersion.V1_19_70.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_70.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_70.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_70.hasItemstates());
        assertFalse(BedrockVersion.V1_19_70.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_70.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_70.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_70.hasChatLogging());
        // 575 までは 582 追加分・server_controlled_sound が無い。
        assertFalse(BedrockVersion.V1_19_70.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_19_70.hasServerControlledSound());
        // Shape A0（1.19.83）：hardcore・識別子・編集者項目なし。
        // movement_authority・itemstates あり（582 の protocol.json で実測）。
        assertFalse(BedrockVersion.V1_19_83.hasHardcore());
        assertFalse(BedrockVersion.V1_19_83.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_19_83.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_19_83.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_19_83.hasItemstates());
        assertFalse(BedrockVersion.V1_19_83.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_19_83.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_19_83.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_19_83.hasChatLogging());
        // 582 で created_in_editor・exported_from_editor・
        // block_network_ids_are_hashes が追加。server_controlled_sound はまだ無い。
        assertTrue(BedrockVersion.V1_19_83.hasStartGame582Fields());
        assertFalse(BedrockVersion.V1_19_83.hasServerControlledSound());
        // Shape A（1.20.0〜1.20.70）：hardcore・識別子・編集者項目なし。
        assertFalse(BedrockVersion.V1_20_0.hasHardcore());
        // 589（1.20.0）で server_controlled_sound が追加。
        assertTrue(BedrockVersion.V1_20_0.hasServerControlledSound());
        assertFalse(BedrockVersion.V1_20_0.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_20_0.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_20_0.hasMovementAuthority());
        assertTrue(BedrockVersion.V1_20_0.hasItemstates());
        // Shape A2（1.20.80）：hardcore が追加。
        assertTrue(BedrockVersion.V1_20_80.hasHardcore());
        assertFalse(BedrockVersion.V1_20_80.hasPreLevelIdentifiers());
        // Shape B（1.21.0〜1.21.40）：level_id 前に識別子 3 つ。
        assertTrue(BedrockVersion.V1_21_0.hasPreLevelIdentifiers());
        assertFalse(BedrockVersion.V1_21_0.hasOwnerIdentifier());
        assertTrue(BedrockVersion.V1_21_0.hasItemstates());
        // Shape B2（1.21.50〜1.21.80）：itemstates が無くなる。
        assertFalse(BedrockVersion.V1_21_50.hasItemstates());
        assertTrue(BedrockVersion.V1_21_50.hasMovementAuthority());
        // Shape C（1.21.90〜1.21.130）：owner 識別子が追加、movement_authority が無くなる。
        assertTrue(BedrockVersion.V1_21_90.hasOwnerIdentifier());
        assertFalse(BedrockVersion.V1_21_90.hasMovementAuthority());
        assertFalse(BedrockVersion.V1_21_90.hasTickDeathSystems());
        // Shape C2（1.21.100〜1.21.124）：tick_death_systems だけ追加。
        assertTrue(BedrockVersion.V1_21_100.hasTickDeathSystems());
        // 860（1.21.124）は 859 と protocol.json がバイト同一で、
        // tick_death_systems がある。消えるのは 898（1.21.130）。
        assertTrue(BedrockVersion.V1_21_124.hasTickDeathSystems());
        assertFalse(BedrockVersion.V1_21_130.hasTickDeathSystems());
        // 860 は 4 識別子形式（hasPreLevelIdentifiers=false、
        // hasOwnerIdentifier=true）。3 識別子形式ではない。
        assertFalse(BedrockVersion.V1_21_124.hasPreLevelIdentifiers());
        assertTrue(BedrockVersion.V1_21_124.hasOwnerIdentifier());
        // Shape D（26.0 以降）：識別子は末尾、has_server_join_info が付く。
        assertFalse(BedrockVersion.V1_26_0.hasPreLevelIdentifiers());
        assertTrue(BedrockVersion.V1_26_0.hasJoinInfoTail());
        assertFalse(BedrockVersion.V1_26_0.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_26_0.hasChatLogging());
        // Shape D2（26.30）：編集者項目と is_chat_logging が付く。
        assertTrue(BedrockVersion.V1_26_30.hasEditorPolicy());
        assertTrue(BedrockVersion.V1_26_30.hasChatLogging());
        // Shape D3（26.40 以降）：is_chat_logging が無くなる。
        assertFalse(BedrockVersion.V1_26_40.hasChatLogging());
        assertTrue(BedrockVersion.V1_26_40.hasEditorPolicy());
    }

    @Test
    void resourcePackGatesMatchVerifiedShapes() {
        // 431（1.16.221）：force_server_packs なし（448 で新規）。440 と同じ形状。
        // behaviour_packs あり。has_addons・resource_pack_links・
        // world_template・disable_vibrant_visuals・has_editor_packs なし。
        // 件数は li16（431 の protocol.json で実測。
        // ResourcePacksInfo は must_accept・has_scripts・behaviour_packs・
        // texture_packs の 4 フィールドで 440 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_16_221.hasAddons());
        assertFalse(BedrockVersion.V1_16_221.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_16_221.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_16_221.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_16_221.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_16_221.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_16_221.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_16_221.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_16_221.resourcePacksCountIsVarint());
        // 440（1.17.2）：force_server_packs なし（448 で新規）。
        // behaviour_packs あり。has_addons・resource_pack_links・
        // world_template・disable_vibrant_visuals・has_editor_packs なし。
        // 件数は li16（440 の protocol.json で実測。
        // ResourcePacksInfo は must_accept・has_scripts・behaviour_packs・
        // texture_packs の 4 フィールド）。
        assertFalse(BedrockVersion.V1_17_2.hasAddons());
        assertFalse(BedrockVersion.V1_17_2.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_17_2.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_17_2.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_17_2.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_17_2.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_17_2.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_17_2.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_17_2.resourcePacksCountIsVarint());
        // 544（1.19.20）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        // 545・554・557・560・567 と同じ形状（544 の protocol.json は
        // 545 のものとバイト同一で、ResourcePacksInfo／ResourcePackStack は
        // 545 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_20.hasAddons());
        assertTrue(BedrockVersion.V1_19_20.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_20.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_20.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_20.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_20.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_20.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_20.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_20.resourcePacksCountIsVarint());
        // 545（1.19.22）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        // 554・557・560・567 と同じ形状（545 の protocol.json で実測。
        // ResourcePacksInfo／ResourcePackStack は 554 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_22.hasAddons());
        assertTrue(BedrockVersion.V1_19_22.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_22.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_22.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_22.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_22.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_22.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_22.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_22.resourcePacksCountIsVarint());
        // 554（1.19.31）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        // 557・560・567 と同じ形状（554 の protocol.json で実測。ResourcePacksInfo／
        // ResourcePackStack は 557 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_31.hasAddons());
        assertTrue(BedrockVersion.V1_19_31.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_31.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_31.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_31.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_31.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_31.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_31.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_31.resourcePacksCountIsVarint());
        // 557（1.19.41）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        // 560・567 と同じ形状（557 の protocol.json で実測。ResourcePacksInfo／
        // ResourcePackStack は 560・567 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_41.hasAddons());
        assertTrue(BedrockVersion.V1_19_41.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_41.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_41.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_41.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_41.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_41.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_41.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_41.resourcePacksCountIsVarint());
        // 560（1.19.51）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        // 567 と同じ形状（560 の protocol.json で実測。ResourcePacksInfo／
        // ResourcePackStack は 567 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_51.hasAddons());
        assertTrue(BedrockVersion.V1_19_51.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_51.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_51.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_51.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_51.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_51.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_51.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_51.resourcePacksCountIsVarint());
        // 567（1.19.62）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        // 575 と同じ形状（567 の protocol.json で実測。ResourcePacksInfo／
        // ResourcePackStack は 575 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_62.hasAddons());
        assertTrue(BedrockVersion.V1_19_62.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_62.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_62.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_62.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_62.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_62.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_62.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_62.resourcePacksCountIsVarint());
        // 575（1.19.70）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        // 582 と同じ形状（575 の protocol.json で実測）。
        assertFalse(BedrockVersion.V1_19_70.hasAddons());
        assertTrue(BedrockVersion.V1_19_70.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_70.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_70.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_70.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_70.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_70.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_70.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_70.resourcePacksCountIsVarint());
        // 582（1.19.83）：force_server_packs・behaviour_packs あり。
        // has_addons・resource_pack_links・world_template・
        // disable_vibrant_visuals・has_editor_packs なし。件数は li16。
        assertFalse(BedrockVersion.V1_19_83.hasAddons());
        assertTrue(BedrockVersion.V1_19_83.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_19_83.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_19_83.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_19_83.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_19_83.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_19_83.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_19_83.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_19_83.resourcePacksCountIsVarint());
        assertFalse(BedrockVersion.V1_20_0.hasAddons());
        assertFalse(BedrockVersion.V1_20_0.hasResourcePackLinks());
        assertTrue(BedrockVersion.V1_20_30.hasResourcePackLinks());
        assertTrue(BedrockVersion.V1_20_70.hasAddons());
        assertTrue(BedrockVersion.V1_20_70.hasForceServerPacks());
        assertTrue(BedrockVersion.V1_21_20.hasBehaviourPacks());
        assertFalse(BedrockVersion.V1_21_30.hasBehaviourPacks());
        assertTrue(BedrockVersion.V1_21_30.hasResourcePackLinks());
        assertFalse(BedrockVersion.V1_21_40.hasResourcePackLinks());
        assertTrue(BedrockVersion.V1_21_50.hasWorldTemplate());
        assertFalse(BedrockVersion.V1_21_50.hasBehaviourPacks());
        assertTrue(BedrockVersion.V1_21_90.hasDisableVibrantVisuals());
        assertTrue(BedrockVersion.V1_21_120.hasBehaviorPacksInStack());
        // 860（1.21.124）は 859 と protocol.json がバイト同一で、
        // behavior_packs がある。消えるのは 898（1.21.130）。
        assertTrue(BedrockVersion.V1_21_124.hasBehaviorPacksInStack());
        assertFalse(BedrockVersion.V1_21_130.hasBehaviorPacksInStack());
        assertTrue(BedrockVersion.V1_20_70.hasEditorPacksInStack());
        assertFalse(BedrockVersion.V1_20_60.hasEditorPacksInStack());
        // ResourcePacksInfo の件数は 26.40 以降だけ varint。
        assertFalse(BedrockVersion.V1_26_30.resourcePacksCountIsVarint());
        assertTrue(BedrockVersion.V1_26_40.resourcePacksCountIsVarint());
        // 860（1.21.124）の件数は li16（2193 の varint とは異なる）。
        assertFalse(BedrockVersion.V1_21_124.resourcePacksCountIsVarint());
    }

    @Test
    void itemGatesMatchVerifiedShapes() {
        // 554（1.19.31）：すべて旧型 Item、mob_armor は 4 スロット、
        // Transaction は V1 形式。557・560・567 と同じ形状（554 の protocol.json で実測。
        // mob_equipment／add_item_entity／mob_armor_equipment／
        // inventory_transaction は 557 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_31.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_19_31.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_19_31.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_19_31.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_19_31.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_19_31.transactionUsesItemV4());
        assertFalse(BedrockVersion.V1_19_31.transactionFieldsAreOptions());
        // 557（1.19.41）：すべて旧型 Item、mob_armor は 4 スロット、
        // Transaction は V1 形式。560・567 と同じ形状（557 の protocol.json で実測。
        // mob_equipment／add_item_entity／mob_armor_equipment／
        // inventory_transaction は 560・567 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_41.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_19_41.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_19_41.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_19_41.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_19_41.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_19_41.transactionUsesItemV4());
        assertFalse(BedrockVersion.V1_19_41.transactionFieldsAreOptions());
        // 560（1.19.51）：すべて旧型 Item、mob_armor は 4 スロット、
        // Transaction は V1 形式。567 と同じ形状（560 の protocol.json で実測。
        // mob_equipment／add_item_entity／mob_armor_equipment／
        // inventory_transaction は 567 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_51.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_19_51.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_19_51.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_19_51.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_19_51.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_19_51.transactionUsesItemV4());
        assertFalse(BedrockVersion.V1_19_51.transactionFieldsAreOptions());
        // 567（1.19.62）：すべて旧型 Item、mob_armor は 4 スロット、
        // Transaction は V1 形式。575 と同じ形状（567 の protocol.json で実測。
        // mob_equipment／add_item_entity／mob_armor_equipment／
        // inventory_transaction は 575 と JSON 完全一致）。
        assertFalse(BedrockVersion.V1_19_62.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_19_62.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_19_62.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_19_62.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_19_62.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_19_62.transactionUsesItemV4());
        assertFalse(BedrockVersion.V1_19_62.transactionFieldsAreOptions());
        // 575（1.19.70）：すべて旧型 Item、mob_armor は 4 スロット、
        // Transaction は V1 形式。582 と同じ形状（575 の protocol.json で実測）。
        assertFalse(BedrockVersion.V1_19_70.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_19_70.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_19_70.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_19_70.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_19_70.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_19_70.transactionUsesItemV4());
        assertFalse(BedrockVersion.V1_19_70.transactionFieldsAreOptions());
        // 582（1.19.83）：すべて旧型 Item、mob_armor は 4 スロット、
        // Transaction は V1 形式。
        assertFalse(BedrockVersion.V1_19_83.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_19_83.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_19_83.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_19_83.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_19_83.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_19_83.transactionUsesItemV4());
        assertFalse(BedrockVersion.V1_19_83.transactionFieldsAreOptions());
        assertFalse(BedrockVersion.V1_20_50.mobEquipmentUsesItemV4());
        assertTrue(BedrockVersion.V1_26_20.mobEquipmentUsesItemV4());
        assertFalse(BedrockVersion.V1_26_20.mobArmorUsesItemV4());
        assertTrue(BedrockVersion.V1_26_30.mobArmorUsesItemV4());
        assertFalse(BedrockVersion.V1_26_30.addItemUsesItemV4());
        assertTrue(BedrockVersion.V1_26_40.addItemUsesItemV4());
        assertFalse(BedrockVersion.V1_21_2.mobArmorHasBodySlot());
        assertTrue(BedrockVersion.V1_21_20.mobArmorHasBodySlot());
        assertFalse(BedrockVersion.V1_20_50.transactionLegacyIsOption());
        assertTrue(BedrockVersion.V1_26_30.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_26_30.transactionFieldsAreOptions());
        assertTrue(BedrockVersion.V1_26_40.transactionFieldsAreOptions());
    }

    @Test
    void itemTableTagsAreKnown() {
        // 419（1.16.101）は 422・428・431 と同じ bedrock_item_ids_1_16_210.bin
        // を共用（PMMP bedrock-1.16.100 タグの required_item_list.json から
        // 再生成した候補表が既存の表とバイト同一。bedrock-1.16.100 タグは
        // 419 を共有するリリース版 1.16.100 自体のデータなので他バージョン
        // からの代用ではない。ただし 1.16.101 固有のアイテム差分が無いことの
        // 直接の証拠は無い（未確認）。exact=624／shared=236／legacy=33／
        // fallback=419）。
        assertEquals("1_16_210", BedrockVersion.V1_16_101.itemTableTag());
        // 422（1.16.201）は 428 と同じ bedrock_item_ids_1_16_210.bin を共用
        // （bedrock-1.16.200 タグの required_item_list.json から再生成した
        // 候補表が既存の表とバイト同一。1.16.200 は 422 を共有するリリース版
        // そのものなので他バージョンからの代用ではない。ただし 1.16.201 固有の
        // アイテム差分が無いことの直接の証拠は無い（未確認）。
        // exact=624／shared=236／legacy=33／fallback=419）。
        assertEquals("1_16_210", BedrockVersion.V1_16_201.itemTableTag());
        // 465（1.17.34）は 471 と同じ bedrock_item_ids_1_17_40.bin を共用
        // （bedrock-1.17.30 タグの required_item_list.json から同じ手順で
        // 構築した候補表が 1_17_40 の表とバイト同一。1.17.30 自体の実測で
        // 代用ではない。860 が 859 の表を共用するのと同じ扱い）。
        assertEquals("1_17_40", BedrockVersion.V1_17_34.itemTableTag());
        // 527（1.19.2）は bedrock-1.19.0 タグから生成した専用表を使う
        // （1.19.40 の表とは 3/1312 行が違うため共用できない。
        // trader_llama_spawn_egg・end_crystal・glow_berries の 3 行）。
        assertEquals("1_19_0", BedrockVersion.V1_19_2.itemTableTag());
        // 534（1.19.11）は 557 と同じ bedrock_item_ids_1_19_40.bin を共用
        // （bedrock-1.19.10 タグの required_item_list.json が 1.19.20／
        // 1.19.30／1.19.40 のものとバイト同一で、候補表もバイト同一。今回は
        // 1.19.10 自体の実測。860 が 859 の表を共用するのと同じ扱い）。
        assertEquals("1_19_40", BedrockVersion.V1_19_11.itemTableTag());
        // 544（1.19.20）は 557 と同じ bedrock_item_ids_1_19_40.bin を共用
        // （bedrock-1.19.20 タグの required_item_list.json が 1.19.30／
        // 1.19.40 のものとバイト同一で、候補表もバイト同一。今回は
        // 1.19.20 自体の実測。860 が 859 の表を共用するのと同じ扱い）。
        assertEquals("1_19_40", BedrockVersion.V1_19_20.itemTableTag());
        // 545（1.19.22）は 557 と同じ bedrock_item_ids_1_19_40.bin を共用
        // （1.19.21 自体の PMMP データは無いが、1.19.20 の
        // required_item_list.json が 1.19.30／1.19.40 のものとバイト同一で、
        // 候補表もバイト同一。860 が 859 の表を共用するのと同じ扱い）。
        assertEquals("1_19_40", BedrockVersion.V1_19_22.itemTableTag());
        // 554（1.19.31）は 557 と同じ bedrock_item_ids_1_19_40.bin を共用
        // （1.19.30 と 1.19.40 の required_item_list.json がバイト同一で、
        // 候補表もバイト同一。860 が 859 の表を共用するのと同じ扱い）。
        assertEquals("1_19_40", BedrockVersion.V1_19_31.itemTableTag());
        // 557（1.19.41）は bedrock-1.19.40 タグから生成した専用表を使う
        // （1.19.50 の表とは 44/1312 行が違うため共用できない。
        // 末尾側の ID が +1〜+4 シフトしており、共用すると誤表示になる）。
        assertEquals("1_19_40", BedrockVersion.V1_19_41.itemTableTag());
        // 560（1.19.51）は bedrock-1.19.50 タグから生成した専用表を使う
        // （1.19.60 の表とは 164/1312 行が違うため共用できない。
        // ほぼ全アイテムが +5 シフトしており、共用すると誤表示になる）。
        assertEquals("1_19_50", BedrockVersion.V1_19_51.itemTableTag());
        // 567（1.19.62）は bedrock-1.19.60 タグから生成した専用表を使う
        // （1.19.70 の表とは 401/1312 行が違うため共用できない。
        // ほぼ全アイテムが +1 シフトしており、共用すると誤表示になる）。
        assertEquals("1_19_60", BedrockVersion.V1_19_62.itemTableTag());
        // 575（1.19.70）は bedrock-1.19.70 タグから生成した専用表を使う
        // （1.19.80 の表とは 64/1312 行が違うため共用できない。
        // ネザライト装備の ID ずれ等、共用すると誤表示になる）。
        assertEquals("1_19_70", BedrockVersion.V1_19_70.itemTableTag());
        // 582（1.19.83）は bedrock-1.19.80 タグから生成した専用表を使う
        // （1.20.0 の表とは 450/1312 行が違うため共用できない）。
        assertEquals("1_19_80", BedrockVersion.V1_19_83.itemTableTag());
        assertEquals("1_20_0", BedrockVersion.V1_20_0.itemTableTag());
        assertEquals("1_20_50", BedrockVersion.V1_20_50.itemTableTag());
        assertEquals("1_21_0", BedrockVersion.V1_21_0.itemTableTag());
        assertEquals("1_21_0", BedrockVersion.V1_21_2.itemTableTag());
        assertEquals("1_21_93", BedrockVersion.V1_21_93.itemTableTag());
        assertEquals("1_21_93", BedrockVersion.V1_21_100.itemTableTag());
        assertEquals("1_26_30", BedrockVersion.V1_26_30.itemTableTag());
        assertEquals("1_26_30", BedrockVersion.V1_26_50.itemTableTag());
        assertEquals("1_26_30", BedrockVersion.V1_26_52.itemTableTag());
        // 860（1.21.124）は 859 の表を共用（protocol.json がバイト同一で
        // アイテム ID に差分なし。再生成のバイト一致で確認）。
        assertEquals("1_21_120", BedrockVersion.V1_21_124.itemTableTag());
    }

    @Test
    void duplicateProtocol2193IsSharedBy1_26_50And1_26_52() {
        // 26.52（2026-09-25、プロトコル 2193）は 1.26.50 / 1.26.51 と
        // 同じプロトコルを共有するホットフィックス。Login が運ぶのは
        // プロトコル番号だけなので、クライアントの区別はできない。
        assertEquals(2193, BedrockVersion.V1_26_52.protocol());
        assertEquals("1.26.52", BedrockVersion.V1_26_52.displayName());
        assertTrue(BedrockVersion.isSupportedProtocol(2193));
        // fromProtocol は最初に一致した V1_26_50 を返す（仕様。2193 の
        // セッションは V1_26_50 として扱われ、StartGame の game version
        // も "1.26.50" になる。26.51 のときと同じ扱い）。
        assertEquals(BedrockVersion.V1_26_50, BedrockVersion.fromProtocol(2193));
        // 述語はすべてプロトコル番号で判定するため、V1_26_52 の判定は
        // V1_26_50 と全項目で一致する（ワイヤ形式は同一）。
        assertArrayEquals(predicates(BedrockVersion.V1_26_50),
                predicates(BedrockVersion.V1_26_52),
                "2193 を共有するため全述語が一致すること");
        // 2193 の絶対値（protocol-1.26.51.json で実測した形式と対応）。
        assertTrue(BedrockVersion.V1_26_52.hasHardcore());
        assertTrue(BedrockVersion.V1_26_52.hasJoinInfoTail());
        assertTrue(BedrockVersion.V1_26_52.hasEditorPolicy());
        assertFalse(BedrockVersion.V1_26_52.hasChatLogging());
        assertTrue(BedrockVersion.V1_26_52.resourcePacksCountIsVarint());
        assertTrue(BedrockVersion.V1_26_52.addItemUsesItemV4());
        assertTrue(BedrockVersion.V1_26_52.transactionFieldsAreOptions());
        assertTrue(BedrockVersion.V1_26_52.transactionLegacyIsOption());
        assertFalse(BedrockVersion.V1_26_52.hasBehaviorPacksInStack());
    }

    /** 全 has*() 述語の値を並べたもの。重複プロトコルの一致確認用。 */
    private static boolean[] predicates(BedrockVersion v) {
        return new boolean[]{
                v.hasHardcore(), v.hasPreLevelIdentifiers(), v.hasOwnerIdentifier(),
                v.hasMovementAuthority(), v.hasItemstates(), v.hasTickDeathSystems(),
                v.hasJoinInfoTail(), v.hasEditorPolicy(), v.hasChatLogging(),
                v.hasAddons(), v.hasForceServerPacks(), v.hasBehaviourPacks(),
                v.hasResourcePackLinks(), v.hasWorldTemplate(),
                v.hasDisableVibrantVisuals(), v.resourcePacksCountIsVarint(),
                v.hasBehaviorPacksInStack(), v.hasEditorPacksInStack(),
                v.hasStartGame544Fields(), v.hasEmoteChatMuted(),
                v.hasStartGame582Fields(), v.hasServerControlledSound(),
                v.hasAttributeModifiers(), v.hasSavedChunks(),
                v.mobEquipmentUsesItemV4(), v.mobArmorUsesItemV4(),
                v.addItemUsesItemV4(), v.mobArmorHasBodySlot(),
                v.transactionLegacyIsOption(), v.transactionUsesItemV4(),
                v.transactionFieldsAreOptions(),
        };
    }
}
