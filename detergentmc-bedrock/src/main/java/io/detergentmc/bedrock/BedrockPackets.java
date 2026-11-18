package io.detergentmc.bedrock;

import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.util.UUID;

/**
 * Bedrock ゲームパケット（バッチの中身）の組み立てと分解。
 *
 * <p>形式は PrismarineJS minecraft-data の Bedrock 1.20.40 プロトコル記述
 * による。ここで扱うのは MVP に必要なものだけ：
 * Text（0x09）、Move Player（0x13）、Player Auth Input（0x90、先頭だけ）、
 * Command Request（0x4d、コマンド文字列だけ）、Play Status（0x02）、
 * Disconnect（0x05）、Resource Packs Info（0x06）、Resource Pack Stack
 * （0x07）、Start Game（0x0b）、LevelChunk（0x3a）、RequestChunkRadius
 * （0x45）、ChunkRadiusUpdated（0x46）、NetworkChunkPublisherUpdate（0x79）、
 * Update Block（0x15）。
 *
 * <p>Start Game はフィールド数が多い（minecraft-data の記述で全項目を
 * 確認済み）。送る値は既定値（サバイバル、オーバーワールド、プレーンズ、
 * スポーン (0.5, 64, 0.5)、ゲームルール・実験なし、ブロック・アイテム
 * 一覧は空）で、<b>本物の Bedrock クライアントでの検証はまだできていない</b>
 * ため「ベストエフォートの既定値」として扱う。特に Itemstates（全
 * アイテムの一覧）が空だとモバイル・クライアントがクラッシュする
 * 可能性があると minecraft-data に注記があるため、実機確認が取れるまで
 * 制限一覧に載せてある。
 *
 * <p>暗号化：Server To Client Handshake（0x03）は送らない。PocketMine など
 * と同じく、ハンドシェイクを送らなければクライアントは暗号化なしで
 * 進む（暗号化は任意）。したがって Bedrock 側の通信は平文のままである。
 * くわしくは docs/BEDROCK.md を参照。
 */
public final class BedrockPackets {

    private BedrockPackets() {
    }

    // ---- Text (0x09) ----

    /** チャット文（TextPacket）の内容。 */
    public record TextMessage(int type, String sourceName, String message, String xuid) {
    }

    /** サーバー→クライアントのチャット文（type=chat）を符号化する。 */
    public static byte[] encodeTextChat(String sourceName, String message) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.TEXT_TYPE_CHAT);
        out.writeBoolean(false); // needs translation
        BedrockVarInt.writeString(out, sourceName);
        BedrockVarInt.writeString(out, message);
        BedrockVarInt.writeString(out, ""); // xuid
        BedrockVarInt.writeString(out, ""); // platform chat id
        return out.bytes();
    }

    /** TextPacket を読む（ペイロードは ID の後ろから）。 */
    public static TextMessage decodeText(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        int type = in.readUnsignedByte();
        in.readBoolean(); // needs translation
        String sourceName = "";
        String message;
        switch (type) {
            case 1, 7, 8 -> { // chat / whisper / announcement
                sourceName = BedrockVarInt.readString(in);
                message = BedrockVarInt.readString(in);
            }
            case 2, 3, 4 -> { // translation / popup / jukebox popup
                message = BedrockVarInt.readString(in);
                long count = BedrockVarInt.readUnsignedVarInt(in);
                for (long i = 0; i < count; i++) {
                    BedrockVarInt.readString(in);
                }
            }
            default -> message = BedrockVarInt.readString(in); // raw / tip / system / json 系
        }
        String xuid = "";
        if (in.hasRemaining()) {
            xuid = BedrockVarInt.readString(in);
        }
        return new TextMessage(type, sourceName, message, xuid);
    }

    // ---- Move Player (0x13) ----

    /** MovePlayer の内容（クライアント権威移動のときのもの）。 */
    public record MovePlayer(long runtimeId, float x, float y, float z,
            float pitch, float yaw, float headYaw, int mode, boolean onGround) {
    }

    /** MovePlayer を読む（teleport モードの cause 等も読み飛ばす）。 */
    public static MovePlayer decodeMovePlayer(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        long runtimeId = BedrockVarInt.readUnsignedVarInt(in);
        float x = in.readLittleFloat();
        float y = in.readLittleFloat();
        float z = in.readLittleFloat();
        float pitch = in.readLittleFloat();
        float yaw = in.readLittleFloat();
        float headYaw = in.readLittleFloat();
        int mode = in.readUnsignedByte();
        boolean onGround = in.readBoolean();
        BedrockVarInt.readUnsignedVarInt(in); // ridden runtime id
        if (mode == 2) { // teleport
            in.readLittleInt(); // cause（li32）
            BedrockVarInt.readZigzag32(in); // source entity type（LegacyEntityType）
        }
        return new MovePlayer(runtimeId, x, y, z, pitch, yaw, headYaw, mode, onGround);
    }

    /** サーバー→クライアントの MovePlayer（テレポート）を作る。 */
    public static byte[] encodeMovePlayerTeleport(long runtimeId,
            float x, float y, float z, float pitch, float yaw) {
        return encodeMovePlayerTeleport(runtimeId, x, y, z, pitch, yaw,
                BedrockVersion.V1_20_50);
    }

    /**
     * サーバー→クライアントの MovePlayer（テレポート）を作る。
     * 末尾の tick は 1.16.100（419）以降のみ（MovePlayerSerializer_v419 が
     * 追加。408 には無い。BE-NOTES.md §26）。
     */
    public static byte[] encodeMovePlayerTeleport(long runtimeId,
            float x, float y, float z, float pitch, float yaw,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        out.writeLittleFloat(x);
        out.writeLittleFloat(y);
        out.writeLittleFloat(z);
        out.writeLittleFloat(pitch);
        out.writeLittleFloat(yaw);
        out.writeLittleFloat(yaw); // head yaw
        out.writeByte(2); // teleport
        out.writeBoolean(true);
        BedrockVarInt.writeUnsignedVarInt(out, 0); // ridden runtime id
        out.writeLittleInt(0); // cause: unknown
        BedrockVarInt.writeZigzag32(out, 0); // source entity type
        if (version.hasMovePlayerTick()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // tick
        }
        return out.bytes();
    }

    // ---- Player Auth Input (0x90) ----

    /** PlayerAuthInput の先頭だけ（移動に必要なもの）を読んだ結果。 */
    public record PlayerAuthInput(float pitch, float yaw, float x, float y, float z) {
    }

    /** PlayerAuthInput の先頭（pitch / yaw / position）だけを読む。 */
    public static PlayerAuthInput decodePlayerAuthInput(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        float pitch = in.readLittleFloat();
        float yaw = in.readLittleFloat();
        float x = in.readLittleFloat();
        float y = in.readLittleFloat();
        float z = in.readLittleFloat();
        return new PlayerAuthInput(pitch, yaw, x, y, z);
    }

    // ---- Command Request (0x4d) ----

    /** Command Request のコマンド文字列だけを読む（残りは無視する）。 */
    public static String decodeCommandRequest(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        return BedrockVarInt.readString(in);
    }

    // ---- Play Status (0x02) ----

    /** Play Status を作る（status は i32 ビッグエンディアン）。 */
    public static byte[] encodePlayStatus(int status) {
        ByteWriter out = new ByteWriter();
        out.writeBigInt(status);
        return out.bytes();
    }

    // ---- Disconnect (0x05) ----

    /** Disconnect を作る（reason 0＝切断、メッセージ付き）。 */
    public static byte[] encodeDisconnect(String message) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeVarInt(out, 0); // reason
        out.writeBoolean(false); // hide disconnect reason
        BedrockVarInt.writeString(out, message == null ? "" : message);
        return out.bytes();
    }

    // ---- Resource Packs Info (0x06) / Stack (0x07) ----

    /** 空の Resource Packs Info（パック要求なし）を作る。 */
    public static byte[] encodeResourcePacksInfo() {
        return encodeResourcePacksInfo(BedrockVersion.V1_20_50);
    }

    /**
     * 空の Resource Packs Info（パック要求なし）を作る。
     * 形式はバージョンごとに違う（minecraft-data の protocol.json で確認）：
     * has_addons は 1.20.70 以降、resource_pack_links は 1.20.30〜1.21.30、
     * behaviour_packs と force_server_packs は 1.21.20 まで、
     * world_template は 1.21.50 以降、disable_vibrant_visuals は 1.21.90 以降。
     * texture_packs の件数は 26.40 以降 varint、それ以前は li16。
     * has_scripts（scriptingEnabled）は 332 以降で、313 の実効
     * シリアライザ（CloudburstMC Protocol の
     * ResourcePacksInfoSerializer_v291）には無い
     * （BE-NOTES.md §35）。
     */
    public static byte[] encodeResourcePacksInfo(BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        out.writeBoolean(false); // must accept
        if (version.hasAddons()) {
            out.writeBoolean(false); // has addons
        }
        if (version.resourcePacksInfoHasScripting()) {
            out.writeBoolean(false); // has scripts
        }
        if (version.hasDisableVibrantVisuals()) {
            out.writeBoolean(false); // disable vibrant visuals
        }
        if (version.hasWorldTemplate()) {
            out.writeBytes(new byte[16]); // world template uuid（空）
            BedrockVarInt.writeString(out, ""); // world template version
        }
        if (version.hasForceServerPacks()) {
            out.writeBoolean(false); // force server packs
        }
        if (version.hasBehaviourPacks()) {
            out.writeLittleShort(0); // behaviour packs（li16 長）
        }
        if (version.resourcePacksCountIsVarint()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // texture packs（varint 長）
        } else {
            out.writeLittleShort(0); // texture packs（li16 長）
        }
        if (version.hasResourcePackLinks()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // resource pack links
        }
        return out.bytes();
    }

    /** 空の Resource Pack Stack（パックなし）を作る。 */
    public static byte[] encodeResourcePackStack() {
        return encodeResourcePackStack(BedrockVersion.V1_20_50);
    }

    /**
     * 空の Resource Pack Stack（パックなし）を作る。
     * behavior_packs は 1.21.120 まで、has_editor_packs は 1.20.70 以降。
     * ResourcePackIdVersions の件数はいずれのバージョンでも varint。
     * 419 未満は旧形式で、experiments 配列が無く、gameVersion の前に
     * legacy experiment フラグ（bool）が 1 つある
     * （ResourcePackStackSerializer_v313／_v388。BE-NOTES.md §26）。
     * 361 はさらに gameVersion 自体が無く、legacy experiment フラグが
     * 末尾になる（_v313 が 361 の実効シリアライザ。BE-NOTES.md §31）。
     * 291 は legacy experiment フラグ自体が無く、2 つの配列で終わる
     * （ResourcePackStackSerializer_v291 がフラグ無しで、_v313 が
     * 末尾に 1 bool を追加した。resourcePackStackUsesV291Format()。
     * BE-NOTES.md §36）。
     */
    public static byte[] encodeResourcePackStack(BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        out.writeBoolean(false); // must accept
        if (version.hasBehaviorPacksInStack()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // behaviour packs
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0); // resource packs
        if (version.hasResourcePackStackExperiments()) {
            BedrockVarInt.writeString(out, version.displayName());
            BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments
            out.writeBoolean(false); // experiments previously used
        } else {
            if (!version.resourcePackStackUsesV291Format()) {
                out.writeBoolean(false); // legacy experiment（旧形式）
            }
            if (version.resourcePackStackHasGameVersion()) {
                BedrockVarInt.writeString(out, version.displayName());
            }
        }
        if (version.hasEditorPacksInStack()) {
            out.writeBoolean(false); // has editor packs
        }
        return out.bytes();
    }

    // ---- Level Chunk (0x3a) / Chunk Radius (0x45 / 0x46) /
    //      Network Chunk Publisher Update (0x79) / Update Block (0x15) ----

    /** RequestChunkRadius の内容（chunk_radius zigzag32、max_radius u8）。 */
    public record ChunkRadiusRequest(int radius, int maxRadius) {
    }

    /** RequestChunkRadius を読む。 */
    public static ChunkRadiusRequest decodeRequestChunkRadius(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        int radius = BedrockVarInt.readZigzag32(in);
        int maxRadius = in.hasRemaining() ? in.readUnsignedByte() : radius;
        return new ChunkRadiusRequest(radius, maxRadius);
    }

    /** LevelChunk を作る（cache 無効、payload は翻訳済みのサブチャンク列）。 */
    public static byte[] encodeLevelChunk(int chunkX, int chunkZ, int subChunkCount, byte[] payload) {
        return encodeLevelChunk(chunkX, chunkZ, subChunkCount, payload,
                BedrockVersion.V1_20_50);
    }

    /**
     * LevelChunk を作る（バージョン別）。2225 では末尾に
     * client_biome_update（bool）が付く（BE-NOTES.md §24）。
     * 354 では subChunkCount とキャッシュ可否自体が無い
     * （hasLevelChunkSubChunkCount()。FullChunkDataSerializer_v291 が
     * 実効で、_v361 がこの 2 つを追加した。BE-NOTES.md §32）。
     */
    public static byte[] encodeLevelChunk(int chunkX, int chunkZ, int subChunkCount, byte[] payload,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, chunkX);
        BedrockVarInt.writeZigzag32(out, chunkZ);
        if (version.hasLevelChunkSubChunkCount()) {
            BedrockVarInt.writeUnsignedVarInt(out, subChunkCount);
            out.writeBoolean(false); // cache disabled
        }
        BedrockVarInt.writeUnsignedVarInt(out, payload.length); // ByteArray
        out.writeBytes(payload);
        if (version.hasClientBiomeUpdate()) {
            out.writeBoolean(false); // client biome update（2225 で新規。末尾）
        }
        return out.bytes();
    }

    /** ChunkRadiusUpdated を作る（応答する半径を返す）。 */
    public static byte[] encodeChunkRadiusUpdated(int radius) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, radius);
        return out.bytes();
    }

    /**
     * NetworkChunkPublisherUpdate を作る。クライアントはこのパケットが無いと
     * 送られたチャンクを表示しない（Mojang の sub-chunk request ガイド）。
     * radius はブロック単位（チャンク半径 × 16）。1.20.50 形。
     */
    public static byte[] encodeNetworkChunkPublisherUpdate(int blockX, int blockY, int blockZ,
                                                           int radiusBlocks) {
        return encodeNetworkChunkPublisherUpdate(blockX, blockY, blockZ, radiusBlocks,
                BedrockVersion.V1_20_50);
    }

    /**
     * NetworkChunkPublisherUpdate を作る（バージョン別）。saved_chunks 配列は
     * 1.19.20 以降のみ（534 には無い。BE-NOTES.md §11）。
     */
    public static byte[] encodeNetworkChunkPublisherUpdate(int blockX, int blockY, int blockZ,
                                                           int radiusBlocks, BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        writeBlockCoordinates(out, blockX, blockY, blockZ);
        BedrockVarInt.writeUnsignedVarInt(out, radiusBlocks);
        if (version.hasSavedChunks()) {
            out.writeLittleInt(0); // saved chunks: なし
        }
        return out.bytes();
    }

    /**
     * UpdateBlock を作る。flags は network（2）のみ。
     * 水没したブロックは層 0（ブロック）と層 1（水）の 2 回送る。
     */
    public static byte[] encodeUpdateBlock(int x, int y, int z, int runtimeId, int layer) {
        ByteWriter out = new ByteWriter();
        writeBlockCoordinates(out, x, y, z);
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        BedrockVarInt.writeUnsignedVarInt(out,
                BedrockProtocol.UPDATE_BLOCK_FLAG_NETWORK);
        BedrockVarInt.writeUnsignedVarInt(out, layer);
        return out.bytes();
    }

    // ---- Start Game (0x0b) ----

    /** StartGame のスポーン位置などの既定値。 */
    public record StartGameInfo(int entityId, float spawnX, float spawnY, float spawnZ,
            String worldName, String gameVersion) {
        public static StartGameInfo defaults(int entityId, String worldName) {
            return new StartGameInfo(entityId, 0.5f, 64.0f, 0.5f, worldName,
                    BedrockProtocol.TARGET_VERSION);
        }
    }

    /**
     * Start Game を作る（minecraft-data Bedrock 1.20.40 の項目順）。
     * 値は既定値。ブロック・アイテム一覧は空（実機での検証は未実施のため、
     * 制限一覧に載せてある）。
     */
    public static byte[] encodeStartGame(StartGameInfo info) {
        return encodeStartGame(info, null, BedrockVersion.V1_20_50);
    }

    /**
     * Start Game を作る。{@code palette} が null でない場合は正規パレット
     * 全エントリを block properties として送る（ランタイムIDの一致のため）。
     * itemstates は引き続き空（モバイル・クライアントがクラッシュする
     * 可能性があると minecraft-data に注記があるため、制限一覧に載せてある）。
     */
    public static byte[] encodeStartGame(StartGameInfo info, BedrockBlockPalette palette) {
        return encodeStartGame(info, palette, BedrockVersion.V1_20_50);
    }

    /**
     * Start Game を作る（バージョン別）。形式の境界は minecraft-data の
     * protocol.json で確認した：editor_world は 1.19.10 以降（527 には
     * 無い）、permission_level は 1.19.10 以降が PermissionLevel 型
     * （u8 mapper）で 527 は zigzag32、544 追加分（persona_disabled・
     * custom_skins_disabled・chat_restriction_level・
     * disable_player_interactions・client_side_generation）は 1.19.20 以降、
     * emote_chat_muted は 1.19.60 以降、582 追加分（created_in_editor・
     * exported_from_editor・block_network_ids_are_hashes）は 1.19.80 以降、
     * server_controlled_sound は 1.20.0 以降、hardcore は 1.20.80 以降、level_id 前の
     * 識別子は 1.21.0〜1.21.80 で 3 つ・1.21.90〜1.21.130 で 4 つ、
     * itemstates は 1.12.0〜1.21.40、movement_authority は 1.21.80 まで、
     * tick_death_systems は 1.21.100〜1.21.120 のみ、末尾の
     * has_server_join_info 等は 26.0 以降、編集者向け項目は 26.30 以降、
     * is_chat_logging は 26.30 のみ。
     * （534 回で 544／567／582／589 の分岐を追加するまで、544 追加分・
     * emote_chat_muted・582 追加分・server_controlled_sound を無条件に
     * 書いていたため、534〜582 の実ワイヤは余分なバイトを含んでいた。
     * BE-NOTES.md §11 参照。engine も 431 には無いため 440 以降のみ書く
     * （hasStartGameEngine()。BE-NOTES.md §20 参照）。rewind_history_size・
     * server_authoritative_block_breaking も 428 で新規のため 428 以降のみ書く
     * （hasStartGameRewindFields()。422 には無い。BE-NOTES.md §22 参照）。
     * 分岐なしでは 422 の StartGame が 2 フィールド分（zigzag32 1 バイト＋
     * bool 1 バイト）多くなるところだった。）
     * （361 は CloudburstMC Protocol の v361 コーデックで確認した：
     * LevelSettings は 31 フィールドで末尾の game version 文字列が無く
     * （hasStartGameVanillaVersion()。388 で新規）、movement_authority
     * 自体が無い（hasMovementAuthority() の下限は 388）、ブロック
     * パレットは配列形式（hasStartGameLegacyPaletteArray()。
     * BedrockLegacyBlockPalette を参照）。BE-NOTES.md §31 参照。）
     * （354 は CloudburstMC Protocol の v354 コーデックで確認した：
     * LevelSettings は 30 フィールドで only spawn v1 villagers 自体が
     * 無く（hasStartGameOnlySpawningV1Villagers()。361 で新規）、
     * パレットは配列形式だが要素に id が無く中身も 1.11.0 世代、
     * itemstates 自体が無い（hasItemstates() の下限は 361）。
     * BE-NOTES.md §32 参照。）
     * （340 は CloudburstMC Protocol の v340 コーデックで確認した：
     * 実効シリアライザは 354 と全て同一で、StartGame の形式も
     * 354 と同一。パレットの中身は 1.10 系の実データが
     * 無いため 1.11.0 世代を代用する（340 自体の実測ではない）。
     * BE-NOTES.md §33 参照。）
     * （332 は CloudburstMC Protocol の v332 コーデックで確認した：
     * 実効シリアライザは 340 と全て同一で、StartGame の形式も
     * 340 と同一。パレットの中身は 1.9 系の実データが
     * 無いため 1.11.0 世代を代用する（332 自体の実測ではない）。
     * BE-NOTES.md §34 参照。）
     * （313 は CloudburstMC Protocol の v313 コーデックで確認した：
     * LevelSettings は StartGameSerializer_v313（_v291 の
     * writeLevelSettings 31 フィールド＋fromWorldTemplate／
     * fromLockedWorldTemplate の 2 bool＝33 フィールド）で、
     * 放送モードは bool と zigzag32 の組になり trustingPlayers が
     * ある（startGameUsesV291LevelSettings()。332 より 3 バイト
     * 長い）。トップレベルは _v291 で 332 と同一、パレットは
     * 配列形式で中身は 1.8 系の実データが無いため 1.11.0 世代を
     * 代用する（313 自体の実測ではない）。BE-NOTES.md §35 参照。）
     * （291 は CloudburstMC Protocol の v291 コーデックで確認した：
     * LevelSettings は StartGameSerializer_v291 の純粋な
     * 31 フィールドで、313 が末尾に追加した 2 bool が無い
     * （startGameUsesV313LevelSettings() は 313 のみ。313 より
     * 2 バイト短い）。トップレベルは _v291 で 313 と同一、
     * パレットは配列形式で中身は 1.7 系の実データが無いため
     * 1.11.0 世代を代用する（291 自体の実測ではない）。
     * BE-NOTES.md §36 参照。）
     */
    public static byte[] encodeStartGame(StartGameInfo info, BedrockBlockPalette palette,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, info.entityId()); // entity id
        BedrockVarInt.writeUnsignedVarInt(out, info.entityId()); // runtime entity id
        BedrockVarInt.writeZigzag32(out, 0); // player gamemode: survival
        out.writeLittleFloat(info.spawnX()); // player position
        out.writeLittleFloat(info.spawnY());
        out.writeLittleFloat(info.spawnZ());
        out.writeLittleFloat(0); // rotation: pitch
        out.writeLittleFloat(0); // rotation: yaw
        if (version.hasStartGameLu64Seed()) {
            out.writeLittleLong(0); // seed（503 以降は lu64）
        } else {
            BedrockVarInt.writeZigzag32(out, 0); // seed（486 は zigzag32）
        }
        if (version.hasStartGame407Fields()) {
            out.writeLittleInt16(1); // biome type: plains（407 で新規。390 には無い）
            BedrockVarInt.writeString(out, "minecraft:plains"); // biome name（407 で新規。390 には無い）
        }
        BedrockVarInt.writeZigzag32(out, 0); // dimension: overworld
        BedrockVarInt.writeZigzag32(out, 1); // generator: infinite
        BedrockVarInt.writeZigzag32(out, 0); // world gamemode: survival
        if (version.hasHardcore()) {
            out.writeBoolean(false); // hardcore
        }
        BedrockVarInt.writeZigzag32(out, 2); // difficulty: normal
        writeBlockCoordinates(out, 0, 64, 0); // spawn position
        out.writeBoolean(false); // achievements disabled
        if (version.hasEditorWorld()) {
            BedrockVarInt.writeZigzag32(out, 0); // editor world type: not editor（527 には無い）
        }
        if (version.hasStartGame582Fields()) {
            out.writeBoolean(false); // created in editor
            out.writeBoolean(false); // exported from editor
        }
        if (version.hasEditorLevelMigrationVersion()) {
            out.writeByte(0); // editor level migration version: legacy（2225 で新規。2193 には無い）
        }
        BedrockVarInt.writeZigzag32(out, 0); // day cycle stop time
        BedrockVarInt.writeZigzag32(out, 0); // edu offer: none（390 は Boolean 形式だが、値 0 は 0x00 で VarInt とバイト同一のため分岐しない。§28）
        out.writeBoolean(false); // edu features enabled
        if (version.hasStartGame407Fields()) {
            BedrockVarInt.writeString(out, ""); // edu product uuid（407 で新規。390 には無い）
        }
        out.writeLittleFloat(0); // rain level
        out.writeLittleFloat(0); // lightning level
        if (version.startGameUsesV291LevelSettings()) {
            // 313 の LevelSettings は StartGameSerializer_v313
            // （_v291 の writeLevelSettings＋末尾 2 bool。33 フィールド）。
            // 291 は _v291 の純粋な 31 フィールドで、末尾 2 bool が
            // 無い分だけ 313 より 2 バイト短い
            // （startGameUsesV313LevelSettings()。BE-NOTES.md §36）。
            // 放送モードは bool（値が NO_MULTI_PLAY のとき false）と
            // zigzag32 の組で、trustingPlayers がある
            // （BE-NOTES.md §35）。
            out.writeBoolean(true); // is multiplayer
            out.writeBoolean(true); // broadcast to lan
            out.writeBoolean(false); // xbox live broadcast（broadcast mode 0＝NO_MULTI_PLAY のため false）
            out.writeBoolean(true); // enable commands
            out.writeBoolean(false); // is texturepacks required
            BedrockVarInt.writeUnsignedVarInt(out, 0); // gamerules: なし
            out.writeBoolean(false); // bonus chest
            out.writeBoolean(false); // map enabled
            out.writeBoolean(false); // trusting players
            BedrockVarInt.writeZigzag32(out, 1); // default player permission: member
            BedrockVarInt.writeZigzag32(out, 0); // xbox live broadcast mode: NO_MULTI_PLAY
            out.writeLittleInt(4); // server chunk tick range
            out.writeBoolean(false); // platform broadcast（broadcast mode 0＝NO_MULTI_PLAY のため false）
            BedrockVarInt.writeZigzag32(out, 0); // platform broadcast mode: NO_MULTI_PLAY
            out.writeBoolean(false); // xbl intent（broadcast mode 0＝NO_MULTI_PLAY のため false）
            out.writeBoolean(false); // has locked behavior pack
            out.writeBoolean(false); // has locked resource pack
            out.writeBoolean(false); // is from locked world template
            out.writeBoolean(false); // msa gamertags only
            if (version.startGameUsesV313LevelSettings()) {
                out.writeBoolean(false); // is from world template（313 で新規。_v313 が追加）
                out.writeBoolean(false); // is from locked world template（313 で新規。_v313 が追加）
            }
        } else {
        out.writeBoolean(false); // has confirmed platform locked content
        out.writeBoolean(true); // is multiplayer
        out.writeBoolean(true); // broadcast to lan
        BedrockVarInt.writeUnsignedVarInt(out, 0); // xbox live broadcast mode
        BedrockVarInt.writeUnsignedVarInt(out, 0); // platform broadcast mode
        out.writeBoolean(true); // enable commands
        out.writeBoolean(false); // is texturepacks required
        BedrockVarInt.writeUnsignedVarInt(out, 0); // gamerules: なし
        if (version.hasStartGameExperiments()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）
            out.writeBoolean(false); // experiments previously used（419 で新規。408 には無い）
        }
        out.writeBoolean(false); // bonus chest
        out.writeBoolean(false); // map enabled
        if (version.hasPermissionLevelMapper()) {
            out.writeByte(1); // permission level (u8 mapper): member
        } else {
            BedrockVarInt.writeZigzag32(out, 1); // permission level (527 は zigzag32): member
        }
        out.writeLittleInt(4); // server chunk tick range
        out.writeBoolean(false); // has locked behavior pack
        out.writeBoolean(false); // has locked resource pack
        out.writeBoolean(false); // is from locked world template
        out.writeBoolean(false); // msa gamertags only
        out.writeBoolean(false); // is from world template
        out.writeBoolean(false); // is world template option locked
        if (version.hasStartGameOnlySpawningV1Villagers()) {
            out.writeBoolean(false); // only spawn v1 villagers（361 で新規。354 の LevelSettings は 30 フィールドでこの項目が無い）
        }
        }
        if (version.hasStartGame544Fields()) {
            out.writeBoolean(false); // persona disabled
            out.writeBoolean(false); // custom skins disabled
        }
        if (version.hasEmoteChatMuted()) {
            out.writeBoolean(false); // emote chat muted
        }
        if (version.hasStartGameVanillaVersion()) {
            BedrockVarInt.writeString(out, info.gameVersion()); // game version（388 で新規。361 の LevelSettings は 31 フィールドでこの文字列が無い）
        }
        if (version.hasStartGame407Fields()) {
            out.writeLittleInt(0); // limited world width（407 で新規。390 には無い）
            out.writeLittleInt(0); // limited world length（407 で新規。390 には無い）
            out.writeBoolean(false); // is new nether（407 で新規。390 には無い）
        }
        if (version.hasStartGameEduResourceUri()) {
            BedrockVarInt.writeString(out, ""); // edu resource uri: button name（465 以降。448 には無い）
            BedrockVarInt.writeString(out, ""); // edu resource uri: link uri（465 以降。448 には無い）
        }
        if (version.hasStartGame407Fields()) {
            out.writeBoolean(false); // experimental gameplay override（407 で新規。390 には無い）
        }
        if (version.hasStartGame544Fields()) {
            out.writeByte(0); // chat restriction level (u8): none
            out.writeBoolean(false); // disable player interactions
        }
        if (version.hasPreLevelIdentifiers()) {
            BedrockVarInt.writeString(out, ""); // server identifier
            BedrockVarInt.writeString(out, ""); // world identifier
            BedrockVarInt.writeString(out, ""); // scenario identifier
        } else if (version.hasOwnerIdentifier()) {
            BedrockVarInt.writeString(out, ""); // server identifier
            BedrockVarInt.writeString(out, ""); // world identifier
            BedrockVarInt.writeString(out, ""); // scenario identifier
            BedrockVarInt.writeString(out, ""); // owner identifier
        }
        if (version.hasEditorPolicy()) {
            BedrockVarInt.writeZigzag32(out, 0); // server editor connection policy
            out.writeBoolean(false); // allow anonymous block drops
        }
        BedrockVarInt.writeString(out, ""); // level id
        BedrockVarInt.writeString(out, info.worldName()); // world name
        BedrockVarInt.writeString(out, ""); // premium world template id
        out.writeBoolean(false); // is trial
        if (version.hasMovementAuthority()) {
            // 419 以降は VarInt の ordinal、390・407・408 は Boolean 形式だが、
            // 書く値は 0（client）だけで、zigzag32 の 0 は 1 バイトの
            // 0x00＝Boolean false とバイト同一のため分岐しない
            // （StartGameSerializer_v388／_v419。BE-NOTES.md §26）。
            BedrockVarInt.writeZigzag32(out, 0); // movement authority: client
        }
        if (version.hasStartGameRewindFields()) {
            BedrockVarInt.writeZigzag32(out, 0); // rewind history size（428 で新規。422 には無い）
            out.writeBoolean(false); // server authoritative block breaking（428 で新規。422 には無い）
        }
        out.writeLittleLong(0); // current tick
        BedrockVarInt.writeZigzag32(out, 0); // enchantment seed
        if (version.hasStartGameBlockProperties()) {
            if (palette == null) {
                BedrockVarInt.writeUnsignedVarInt(out, 0); // block properties: なし
            } else {
                BedrockVarInt.writeUnsignedVarInt(out, palette.count());
                out.writeBytes(palette.rawPaletteBytes());
            }
        } else if (version.hasStartGameLegacyPaletteArray()) {
            if (palette == null) {
                BedrockVarInt.writeUnsignedVarInt(out, 0); // block palette: なし（361・354・340・332・313・291 の配列形式）
            } else if (version.hasStartGameLegacyPaletteEntryId()) {
                // 361 の配列形式：中身は 1.12.0 世代の実測レガシーパレット
                // （件数の VarInt を含む。要素は name／meta／id。
                // BedrockLegacyBlockPalette を参照）
                out.writeBytes(BedrockLegacyBlockPalette.load().rawArrayBytes());
            } else {
                // 354・340・332・313・291 の配列形式：中身は 1.11.0 世代の実測
                // レガシーパレット（件数の VarInt を含む。要素は
                // name／meta のみで id が無い。340・332・313・291 については
                // 1.10 系・1.9 系・1.8 系・1.7 系のパレット実データが存在しないための
                // 代用で、340・332・313・291 自体の実測ではない。
                // BedrockLegacyBlockPalette を参照。BE-NOTES.md §32・§33・§34・§35・§36）
                out.writeBytes(BedrockLegacyBlockPalette.load1_11_0().rawArrayBytes());
            }
        } else if (palette == null) {
            // 408 の旧形式：空の NBT リストタグ（0x09＋要素型 0x0A＋件数 0）
            out.writeBytes(new byte[]{0x09, 0x0A, 0x00});
        } else {
            // 408 の旧形式：パレット全体を単一の NBT リストタグで送る
            out.writeBytes(palette.rawPaletteListTagBytes());
        }
        if (version.hasItemstates()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // itemstates: なし
        }
        BedrockVarInt.writeString(out,
                UUID.randomUUID().toString()); // multiplayer correlation id
        if (version.hasStartGame407Fields()) {
            out.writeBoolean(false); // server authoritative inventory（407 で新規。390 には無い）
        }
        if (version.hasStartGameEngine()) {
            BedrockVarInt.writeString(out, ""); // engine（440 で新規。431 には無い）
        }
        // property data（空の NBT compound）。varint NBT なので
        // 0x0A（compound）＋名前長 0（varint 1 バイト）＋0x00（TAG_End）の 3 バイト。
        // 527 以降のみ（503 には無い。hasStartGame527Fields() を参照）。
        if (version.hasStartGame527Fields()) {
            out.writeByte(0x0A); // TAG_Compound
            out.writeByte(0); // 名前長 0（varint）
            out.writeByte(0x00); // TAG_End
        }
        // block palette checksum（lu64）。471 には無く、475 以降にある
        // （hasStartGameBlockPaletteChecksum() を参照）。503 は
        // block_pallete_checksum（単 l）、527 は block_pallette_checksum
        // （二重 l）だが型・位置は同じ。
        if (version.hasStartGameBlockPaletteChecksum()) {
            out.writeLittleLong(0); // block palette checksum
        }
        if (version.hasStartGame527Fields()) {
            out.writeBytes(new byte[16]); // world template id（空 UUID。503 には無い）
        }
        if (version.hasStartGame544Fields()) {
            out.writeBoolean(false); // client side generation
        }
        if (version.hasStartGame582Fields()) {
            out.writeBoolean(false); // block network ids are hashes
        }
        if (version.hasTickDeathSystems()) {
            out.writeBoolean(false); // tick death systems
        }
        if (version.hasServerControlledSound()) {
            out.writeBoolean(false); // server controlled sound
        }
        if (version.hasChatLogging()) {
            out.writeBoolean(false); // is chat logging
        }
        if (version.hasJoinInfoTail()) {
            out.writeBoolean(false); // has server join info
            out.writeBoolean(false); // server join info: なし（option）
            BedrockVarInt.writeString(out, ""); // server identifier
            BedrockVarInt.writeString(out, ""); // scenario identifier
            BedrockVarInt.writeString(out, ""); // world identifier
            BedrockVarInt.writeString(out, ""); // owner identifier
        }
        return out.bytes();
    }

    /** BlockCoordinates（vec3i）：x zigzag32、y 符号なし VarInt、z zigzag32。 */
    static void writeBlockCoordinates(ByteWriter out, int x, int y, int z) {
        BedrockVarInt.writeZigzag32(out, x);
        BedrockVarInt.writeUnsignedVarInt(out, y);
        BedrockVarInt.writeZigzag32(out, z);
    }

    /** vec3f（lf32 × 3）を書く。 */
    static void writeVec3f(ByteWriter out, float x, float y, float z) {
        out.writeLittleFloat(x);
        out.writeLittleFloat(y);
        out.writeLittleFloat(z);
    }

    /** UUID を 16 バイト（MSB→LSB、ビッグエンディアン）で書く。 */
    static void writeUuid(ByteWriter out, UUID uuid) {
        out.writeBigLong(uuid.getMostSignificantBits());
        out.writeBigLong(uuid.getLeastSignificantBits());
    }

    /** EntityProperties（空：int 0 件・float 0 件）を書く。 */
    static void writeEmptyEntityProperties(ByteWriter out) {
        BedrockVarInt.writeUnsignedVarInt(out, 0); // ints
        BedrockVarInt.writeUnsignedVarInt(out, 0); // floats
    }

    /** Links（空：0 件）を書く。 */
    static void writeEmptyLinks(ByteWriter out) {
        BedrockVarInt.writeUnsignedVarInt(out, 0);
    }

    // ---- Item（Bedrock）----

    /**
     * Bedrock の Item 1 個を書く（従来の Item 形式）。
     * network_id が 0（air）ならそれだけ。それ以外は count（lu16）・
     * metadata（varint）・has_stack_id（u8 0）・block_runtime_id・
     * extra（varint 長＋中身）で、extra は NBT 無し・can_place_on／
     * can_destroy 無し。盾（358）は blocking_tick（li64）付きの
     * ItemExtraDataWithBlockingTick になる。
     */
    public static void writeItem(ByteWriter out,
            BedrockEntityTranslator.BedrockItem item) {
        writeItem(out, item, false);
    }

    /**
     * Bedrock の Item 1 個を書く。{@code itemV4} が true の場合は
     * ItemV4 形式（26.40 以降の一部パケット）：network_id は li16、
     * block_runtime_id は varint、has_stack_id は bool。
     * ItemV4 には network_id==0 の省略分岐が無いため、air も全項目を書く。
     */
    public static void writeItem(ByteWriter out,
            BedrockEntityTranslator.BedrockItem item, boolean itemV4) {
        if (itemV4) {
            writeItemV4(out, item);
            return;
        }
        if (item == null || item.networkId() == 0) {
            BedrockVarInt.writeZigzag32(out, 0);
            return;
        }
        BedrockVarInt.writeZigzag32(out, item.networkId());
        out.writeLittleShort(item.count()); // count（lu16）
        BedrockVarInt.writeUnsignedVarInt(out, item.metadata()); // metadata
        out.writeByte(0); // has_stack_id
        BedrockVarInt.writeZigzag32(out, item.blockRuntimeId());
        writeItemExtra(out, item.networkId());
    }

    /** ItemV4 形式で Item 1 個を書く（後方互換のため {@code writeItem} 経由で使う）。 */
    static void writeItemV4(ByteWriter out,
            BedrockEntityTranslator.BedrockItem item) {
        int networkId = item == null ? 0 : item.networkId();
        int count = item == null ? 0 : item.count();
        int metadata = item == null ? 0 : item.metadata();
        int blockRuntimeId = item == null ? 0 : item.blockRuntimeId();
        out.writeLittleShort(networkId); // network id（li16）
        out.writeLittleShort(count); // count（lu16）
        BedrockVarInt.writeUnsignedVarInt(out, metadata); // metadata
        out.writeBoolean(false); // has_stack_id
        BedrockVarInt.writeUnsignedVarInt(out, blockRuntimeId); // block runtime id
        if (networkId == 0) {
            // air：extra は長さ 0（中身なし）。ItemV4 には network_id==0 の
            // 省略分岐が無いため、全項目を書いたうえで extra だけ空にする。
            BedrockVarInt.writeUnsignedVarInt(out, 0);
            return;
        }
        writeItemExtra(out, networkId);
    }

    /** Item の extra（NBT 無し・can_place_on／can_destroy 無し）を書く。 */
    private static void writeItemExtra(ByteWriter out, int networkId) {
        if (networkId == 358) { // shield
            BedrockVarInt.writeUnsignedVarInt(out, 18);
            out.writeLittleShort(0); // has_nbt
            out.writeLittleInt(0); // can_place_on
            out.writeLittleInt(0); // can_destroy
            out.writeLittleLong(0); // blocking_tick
        } else {
            BedrockVarInt.writeUnsignedVarInt(out, 10);
            out.writeLittleShort(0); // has_nbt
            out.writeLittleInt(0); // can_place_on
            out.writeLittleInt(0); // can_destroy
        }
    }

    /** 空のアイテム（air）を書く。 */
    public static void writeAirItem(ByteWriter out) {
        writeAirItem(out, false);
    }

    /** 空のアイテム（air）を書く。ItemV4 形式では全項目を書く。 */
    public static void writeAirItem(ByteWriter out, boolean itemV4) {
        if (itemV4) {
            writeItemV4(out, null);
            return;
        }
        BedrockVarInt.writeZigzag32(out, 0);
    }

    /** Bedrock の Item 1 個を読み飛ばす（InventoryTransaction の復号用）。 */
    static void skipItem(ByteReader in) {
        skipItem(in, false);
    }

    /** Bedrock の Item 1 個を読み飛ばす（ItemV4 形式対応）。 */
    static void skipItem(ByteReader in, boolean itemV4) {
        if (itemV4) {
            in.readLittleShort(); // network id（li16）
            in.readLittleUnsignedShort(); // count
            BedrockVarInt.readUnsignedVarInt(in); // metadata
            boolean hasStackId = in.readBoolean();
            if (hasStackId) {
                BedrockVarInt.readZigzag32(in); // stack id
            }
            BedrockVarInt.readUnsignedVarInt(in); // block runtime id
            long extraLength = BedrockVarInt.readUnsignedVarInt(in);
            in.readBytes((int) extraLength);
            return;
        }
        int networkId = BedrockVarInt.readZigzag32(in);
        if (networkId == 0) {
            return;
        }
        in.readLittleUnsignedShort(); // count
        BedrockVarInt.readUnsignedVarInt(in); // metadata
        int hasStackId = in.readUnsignedByte();
        if (hasStackId != 0) {
            BedrockVarInt.readZigzag32(in); // stack id
        }
        BedrockVarInt.readZigzag32(in); // block runtime id
        long extraLength = BedrockVarInt.readUnsignedVarInt(in);
        in.readBytes((int) extraLength);
    }

    // ---- Add Entity (0x0d) / Add Player (0x0c) / Add Item Entity (0x0f) ----

    /**
     * AddEntity を作る。attributes は体力 1 件（min 0・value=health・
     * max=maxHealth）。properties・links は空。
     * 旧 12 引数版は 1.20.50 形（properties あり）。
     */
    public static byte[] encodeAddEntity(long runtimeId, String entityType,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            float velX, float velY, float velZ,
            float health, float maxHealth,
            BedrockEntityTranslator.BedrockEntityData data) {
        return encodeAddEntity(runtimeId, entityType, x, y, z, pitch, yaw, headYaw,
                velX, velY, velZ, health, maxHealth, data, BedrockVersion.V1_20_50);
    }

    /**
     * AddEntity を作る（バージョン別。properties は 557 以降のみ、body_yaw は 534 以降のみ）。
     * 291 は第 3 フィールドが identifier 文字列ではなく旧来の数値タイプ ID
     * （VarUnsignedInt。AddEntitySerializer_v291。v313 が文字列に変えた。
     * addEntityUsesLegacyTypeId()。対応表は BedrockLegacyEntityTypeIds で、
     * 対応付けの無い identifier は 0 を書くが、セッション側が送信自体を
     * 省くため実運用では到達しない。BE-NOTES.md §36）。
     */
    public static byte[] encodeAddEntity(long runtimeId, String entityType,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            float velX, float velY, float velZ,
            float health, float maxHealth,
            BedrockEntityTranslator.BedrockEntityData data,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, runtimeId); // unique id
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId); // runtime id
        if (version.addEntityUsesLegacyTypeId()) {
            int legacyId = BedrockLegacyEntityTypeIds.legacyIdFor(entityType);
            BedrockVarInt.writeUnsignedVarInt(out, Math.max(legacyId, 0)); // entity type（291 は数値 ID）
        } else {
            BedrockVarInt.writeString(out, entityType);
        }
        writeVec3f(out, x, y, z);
        writeVec3f(out, velX, velY, velZ);
        out.writeLittleFloat(pitch);
        out.writeLittleFloat(yaw);
        out.writeLittleFloat(headYaw);
        if (version.hasAddEntityBodyYaw()) {
            out.writeLittleFloat(yaw); // body yaw（527 には無い）
        }
        BedrockVarInt.writeUnsignedVarInt(out, 1); // attributes: 1 件
        BedrockVarInt.writeString(out, "minecraft:health");
        out.writeLittleFloat(0); // min
        out.writeLittleFloat(health); // value
        out.writeLittleFloat(maxHealth); // max
        out.writeBytes(data == null
                ? new BedrockEntityTranslator.BedrockEntityData().bytes() : data.bytes());
        if (version.hasEntityProperties()) {
            writeEmptyEntityProperties(out);
        }
        writeEmptyLinks(out);
        if (version.hasPassengerOfBlock()) {
            out.writeBoolean(false); // passenger of block: なし（2225 で新規。末尾）
        }
        return out.bytes();
    }

    /**
     * AddPlayer を作る。held_item は空、skin は PlayerList の空スキンと
     * 同じ既定、abilities・links は空、device は未定。
     */
    public static byte[] encodeAddPlayer(UUID uuid, String username, long runtimeId,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            int gamemode, BedrockEntityTranslator.BedrockEntityData data) {
        return encodeAddPlayer(uuid, username, runtimeId, x, y, z, pitch, yaw, headYaw,
                gamemode, data, BedrockVersion.V1_20_50);
    }

    /** AddPlayer を作る（バージョン別。held_item は 26.40 以降 ItemV4。527 は旧形式で能力系を直書きし、unique_entity_id／runtime_entity_id を使う。486 は gamemode が無く entity_id_self を使う（型は unique_entity_id と同じ zigzag64）。 */
    public static byte[] encodeAddPlayer(UUID uuid, String username, long runtimeId,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            int gamemode, BedrockEntityTranslator.BedrockEntityData data,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        writeUuid(out, uuid);
        BedrockVarInt.writeString(out, username);
        if (version.hasAddPlayerAbilitiesArray()) {
            BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        } else {
            BedrockVarInt.writeZigzag64(out, runtimeId); // unique entity id（527）
            BedrockVarInt.writeUnsignedVarInt(out, runtimeId); // runtime entity id（527）
        }
        BedrockVarInt.writeString(out, ""); // platform chat id
        writeVec3f(out, x, y, z);
        writeVec3f(out, 0, 0, 0); // velocity
        out.writeLittleFloat(pitch);
        out.writeLittleFloat(yaw);
        out.writeLittleFloat(headYaw);
        writeAirItem(out, version.addItemUsesItemV4()); // held item
        if (version.hasAddPlayerGamemode()) {
            BedrockVarInt.writeZigzag32(out, gamemode);
        }
        out.writeBytes(data == null
                ? new BedrockEntityTranslator.BedrockEntityData().bytes() : data.bytes());
        if (version.hasEntityProperties()) {
            writeEmptyEntityProperties(out);
        }
        if (version.hasAddPlayerAbilitiesArray()) {
            out.writeLittleLong(runtimeId); // unique id
            out.writeByte(1); // permission level: member
            out.writeByte(0); // command permission: normal
            out.writeByte(0); // abilities: 0 層
        } else {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // flags（527：能力系を直書き）
            BedrockVarInt.writeUnsignedVarInt(out, 0); // command permission: normal
            BedrockVarInt.writeUnsignedVarInt(out, 0); // action permissions
            BedrockVarInt.writeUnsignedVarInt(out, 1); // permission level: member
            BedrockVarInt.writeUnsignedVarInt(out, 0); // custom stored permissions
            out.writeLittleLong(0); // user id
        }
        writeEmptyLinks(out);
        if (version.hasPassengerOfBlock()) {
            out.writeBoolean(false); // passenger of block: なし（2225 で新規。device_id の前）
        }
        BedrockVarInt.writeString(out, ""); // device id
        if (version.addPlayerHasBuildPlatform()) {
            out.writeLittleInt(0); // device os: Undefined（388 で新規。361 には無い）
        }
        return out.bytes();
    }

    /** AddItemEntity を作る。 */
    public static byte[] encodeAddItemEntity(long runtimeId,
            BedrockEntityTranslator.BedrockItem item,
            float x, float y, float z, float velX, float velY, float velZ,
            BedrockEntityTranslator.BedrockEntityData data) {
        return encodeAddItemEntity(runtimeId, item, x, y, z, velX, velY, velZ, data,
                BedrockVersion.V1_20_50);
    }

    /** AddItemEntity を作る（バージョン別。アイテムは 26.40 以降 ItemV4）。 */
    public static byte[] encodeAddItemEntity(long runtimeId,
            BedrockEntityTranslator.BedrockItem item,
            float x, float y, float z, float velX, float velY, float velZ,
            BedrockEntityTranslator.BedrockEntityData data, BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, runtimeId); // entity id self
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId); // runtime id
        writeItem(out, item, version.addItemUsesItemV4());
        writeVec3f(out, x, y, z);
        writeVec3f(out, velX, velY, velZ);
        out.writeBytes(data == null
                ? new BedrockEntityTranslator.BedrockEntityData().bytes() : data.bytes());
        out.writeBoolean(false); // is from fishing
        return out.bytes();
    }

    // ---- Remove Entity (0x0e) ----

    /** RemoveEntity を作る（unique id＝Java エンティティID）。 */
    public static byte[] encodeRemoveEntity(long runtimeId) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, runtimeId);
        return out.bytes();
    }

    // ---- Move Entity (0x12) / Move Entity Delta (0x6f) / Move Player (0x13) ----

    /** MoveEntity を作る。rotation は角度バイト（256＝360°）で渡す。 */
    public static byte[] encodeMoveEntity(long runtimeId,
            float x, float y, float z, int yawByte, int pitchByte, int headYawByte,
            boolean onGround, boolean teleport) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        int flags = 0;
        if (onGround) {
            flags |= BedrockProtocol.MOVE_ENTITY_FLAG_ON_GROUND;
        }
        if (teleport) {
            flags |= BedrockProtocol.MOVE_ENTITY_FLAG_TELEPORT;
        }
        out.writeByte(flags);
        writeVec3f(out, x, y, z);
        out.writeByte(yawByte); // yaw
        out.writeByte(pitchByte); // pitch
        out.writeByte(headYawByte); // head yaw
        return out.bytes();
    }

    /**
     * MoveEntityDelta を作る。位置・向きのうち null でないものだけ送る。
     * flags は {@link BedrockProtocol} の MOVE_DELTA_* を組み合わせる。
     * この FloatLE 絶対座標形式は 419 以降のもの。419 未満は VarInt 差分
     * 形式だが差分のスケールが実測ソースで確認できないため、セッションは
     * 419 未満ではこのメソッドを使わず絶対座標の MoveEntity で代替する
     * （BE-NOTES.md §26）。
     */
    public static byte[] encodeMoveEntityDelta(long runtimeId, int flags,
            Float x, Float y, Float z, Integer rotX, Integer rotY, Integer rotZ) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        out.writeLittleShort(flags);
        if ((flags & BedrockProtocol.MOVE_DELTA_HAS_X) != 0) {
            out.writeLittleFloat(x == null ? 0 : x);
        }
        if ((flags & BedrockProtocol.MOVE_DELTA_HAS_Y) != 0) {
            out.writeLittleFloat(y == null ? 0 : y);
        }
        if ((flags & BedrockProtocol.MOVE_DELTA_HAS_Z) != 0) {
            out.writeLittleFloat(z == null ? 0 : z);
        }
        if ((flags & BedrockProtocol.MOVE_DELTA_HAS_ROT_X) != 0) {
            out.writeByte(rotX == null ? 0 : rotX);
        }
        if ((flags & BedrockProtocol.MOVE_DELTA_HAS_ROT_Y) != 0) {
            out.writeByte(rotY == null ? 0 : rotY);
        }
        if ((flags & BedrockProtocol.MOVE_DELTA_HAS_ROT_Z) != 0) {
            out.writeByte(rotZ == null ? 0 : rotZ);
        }
        return out.bytes();
    }

    /** 他プレイヤー向けの MovePlayer（mode 0＝通常、2＝テレポート）を作る。 */
    public static byte[] encodeMovePlayerEntity(long runtimeId,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            int mode, boolean onGround) {
        return encodeMovePlayerEntity(runtimeId, x, y, z, pitch, yaw, headYaw,
                mode, onGround, BedrockVersion.V1_20_50);
    }

    /**
     * 他プレイヤー向けの MovePlayer（mode 0＝通常、2＝テレポート）を作る。
     * 末尾の tick は 1.16.100（419）以降のみ（408 には無い。BE-NOTES.md §26）。
     */
    public static byte[] encodeMovePlayerEntity(long runtimeId,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            int mode, boolean onGround, BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        writeVec3f(out, x, y, z);
        out.writeLittleFloat(pitch);
        out.writeLittleFloat(yaw);
        out.writeLittleFloat(headYaw);
        out.writeByte(mode);
        out.writeBoolean(onGround);
        BedrockVarInt.writeUnsignedVarInt(out, 0); // ridden runtime id
        if (mode == 2) {
            out.writeLittleInt(0); // cause: unknown
            BedrockVarInt.writeZigzag32(out, 0); // source entity type
        }
        if (version.hasMovePlayerTick()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // tick
        }
        return out.bytes();
    }

    // ---- Set Entity Data (0x27) / Set Entity Motion (0x28) / Set Entity Link (0x29) ----

    /** SetEntityData を作る（properties は空、tick は 0）。旧 2 引数版は 1.20.50 形。 */
    public static byte[] encodeSetEntityData(long runtimeId,
            BedrockEntityTranslator.BedrockEntityData data) {
        return encodeSetEntityData(runtimeId, data, BedrockVersion.V1_20_50);
    }

    /** SetEntityData を作る（properties は 557 以降のみ、tick は 419 以降のみ）。 */
    public static byte[] encodeSetEntityData(long runtimeId,
            BedrockEntityTranslator.BedrockEntityData data,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        out.writeBytes(data == null
                ? new BedrockEntityTranslator.BedrockEntityData().bytes() : data.bytes());
        if (version.hasEntityProperties()) {
            writeEmptyEntityProperties(out);
        }
        if (version.hasSetEntityDataTick()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // tick（419 で新規。408 には無い）
        }
        return out.bytes();
    }

    /** SetEntityMotion を作る。 */
    public static byte[] encodeSetEntityMotion(long runtimeId,
            float velX, float velY, float velZ) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        writeVec3f(out, velX, velY, velZ);
        return out.bytes();
    }

    /** SetEntityLink を作る（type 0＝解除、1＝騎乗）。 */
    public static byte[] encodeSetEntityLink(long riddenId, long riderId, int type) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, riddenId);
        BedrockVarInt.writeZigzag64(out, riderId);
        out.writeByte(type);
        out.writeBoolean(true); // immediate
        out.writeBoolean(false); // rider initiated
        return out.bytes();
    }

    // ---- Mob Equipment (0x1f) / Mob Armor Equipment (0x20) ----

    /** MobEquipment を作る（window_id は 0＝inventory）。 */
    public static byte[] encodeMobEquipment(long runtimeId,
            BedrockEntityTranslator.BedrockItem item, int slot, int selectedSlot) {
        return encodeMobEquipment(runtimeId, item, slot, selectedSlot,
                BedrockVersion.V1_20_50);
    }

    /** MobEquipment を作る（バージョン別。アイテムは 26.20 以降 ItemV4）。 */
    public static byte[] encodeMobEquipment(long runtimeId,
            BedrockEntityTranslator.BedrockItem item, int slot, int selectedSlot,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        writeItem(out, item, version.mobEquipmentUsesItemV4());
        out.writeByte(slot);
        out.writeByte(selectedSlot);
        out.writeByte(0); // window id: inventory
        return out.bytes();
    }

    /** MobArmorEquipment を作る（helmet・chestplate・leggings・boots）。 */
    public static byte[] encodeMobArmorEquipment(long runtimeId,
            BedrockEntityTranslator.BedrockItem helmet,
            BedrockEntityTranslator.BedrockItem chestplate,
            BedrockEntityTranslator.BedrockItem leggings,
            BedrockEntityTranslator.BedrockItem boots) {
        return encodeMobArmorEquipment(runtimeId, helmet, chestplate, leggings, boots,
                BedrockEntityTranslator.BedrockItem.air(), BedrockVersion.V1_20_50);
    }

    /**
     * MobArmorEquipment を作る（バージョン別。1.21.20 以降は body（5 個目）が
     * 付き、アイテムは 26.30 以降 ItemV4）。
     */
    public static byte[] encodeMobArmorEquipment(long runtimeId,
            BedrockEntityTranslator.BedrockItem helmet,
            BedrockEntityTranslator.BedrockItem chestplate,
            BedrockEntityTranslator.BedrockItem leggings,
            BedrockEntityTranslator.BedrockItem boots,
            BedrockEntityTranslator.BedrockItem body, BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        boolean itemV4 = version.mobArmorUsesItemV4();
        writeItem(out, helmet, itemV4);
        writeItem(out, chestplate, itemV4);
        writeItem(out, leggings, itemV4);
        writeItem(out, boots, itemV4);
        if (version.mobArmorHasBodySlot()) {
            writeItem(out, body, itemV4);
        }
        return out.bytes();
    }

    // ---- Mob Effect (0x1c) / Entity Event (0x1b) / Update Attributes (0x1d) ----

    /** MobEffect を作る（event 1＝追加、2＝更新、3＝除去）。 */
    public static byte[] encodeMobEffect(long runtimeId, int eventId,
            int effectId, int amplifier, boolean particles, int duration) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        out.writeByte(eventId);
        BedrockVarInt.writeZigzag32(out, effectId);
        BedrockVarInt.writeZigzag32(out, amplifier);
        out.writeBoolean(particles);
        BedrockVarInt.writeZigzag32(out, duration);
        return out.bytes();
    }

    /** EntityEvent を作る（event 2＝hurt、3＝death 等）。 */
    public static byte[] encodeEntityEvent(long runtimeId, int eventId, int data) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        out.writeByte(eventId);
        BedrockVarInt.writeZigzag32(out, data);
        return out.bytes();
    }

    /** UpdateAttributes（体力 1 件）を作る。1.20.50 形。 */
    public static byte[] encodeUpdateAttributes(long runtimeId,
            float health, float maxHealth) {
        return encodeUpdateAttributes(runtimeId, health, maxHealth,
                BedrockVersion.V1_20_50);
    }

    /**
     * UpdateAttributes（体力 1 件）を作る（バージョン別）。属性の modifiers
     * 配列は 1.19.20 以降のみ（534 には無い。BE-NOTES.md §11）。末尾の tick は
     * 1.16.100（419）以降のみ（408 には無い。BE-NOTES.md §26）。
     */
    public static byte[] encodeUpdateAttributes(long runtimeId,
            float health, float maxHealth, BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        BedrockVarInt.writeUnsignedVarInt(out, 1); // attributes: 1 件
        out.writeLittleFloat(0); // min
        out.writeLittleFloat(maxHealth); // max
        out.writeLittleFloat(health); // current
        out.writeLittleFloat(maxHealth); // default
        BedrockVarInt.writeString(out, "minecraft:health");
        if (version.hasAttributeModifiers()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // modifiers: なし
        }
        if (version.hasUpdateAttributesTick()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // tick
        }
        return out.bytes();
    }

    // ---- Spawn Experience Orb (0x42) / Add Painting (0x16) ----

    /** SpawnExperienceOrb を作る。 */
    public static byte[] encodeSpawnExperienceOrb(float x, float y, float z, int count) {
        ByteWriter out = new ByteWriter();
        writeVec3f(out, x, y, z);
        BedrockVarInt.writeZigzag32(out, count);
        return out.bytes();
    }

    /** AddPainting を作る（direction は Java の objectData をそのまま使う）。 */
    public static byte[] encodeAddPainting(long runtimeId,
            float x, float y, float z, int direction, String title) {
        return encodeAddPainting(runtimeId, x, y, z, direction, title,
                BedrockVersion.V1_20_50);
    }

    /** AddPainting を作る（バージョン別。coordinates は 428 が BlockCoordinates、431 以降が vec3f）。 */
    public static byte[] encodeAddPainting(long runtimeId,
            float x, float y, float z, int direction, String title,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, runtimeId); // entity id self
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId); // runtime id
        if (version.addPaintingUsesBlockCoordinates()) {
            // BlockCoordinates：x zigzag32、y 符号なし VarInt、z zigzag32
            // （428。絵画の位置はブロック座標なので int に丸める）。
            BedrockVarInt.writeZigzag32(out, (int) x);
            BedrockVarInt.writeUnsignedVarInt(out, (int) y);
            BedrockVarInt.writeZigzag32(out, (int) z);
        } else {
            writeVec3f(out, x, y, z);
        }
        BedrockVarInt.writeZigzag32(out, direction);
        BedrockVarInt.writeString(out, title == null ? "Kebab" : title);
        return out.bytes();
    }

    // ---- Player List (0x3f) ----

    /** 空のスキン（PlayerList add 用。Java 側のスキンは送らない）。 */
    static void writeEmptySkin(ByteWriter out) {
        BedrockVarInt.writeString(out, ""); // skin id
        BedrockVarInt.writeString(out, ""); // play fab id
        BedrockVarInt.writeString(out, ""); // skin resource pack
        // skin_data（SkinImage）
        out.writeLittleInt(0); // width
        out.writeLittleInt(0); // height
        BedrockVarInt.writeUnsignedVarInt(out, 0); // data
        out.writeLittleInt(0); // animations
        // cape_data（SkinImage）
        out.writeLittleInt(0); // width
        out.writeLittleInt(0); // height
        BedrockVarInt.writeUnsignedVarInt(out, 0); // data
        BedrockVarInt.writeString(out, ""); // geometry data
        BedrockVarInt.writeString(out, ""); // geometry data version
        BedrockVarInt.writeString(out, ""); // animation data
        BedrockVarInt.writeString(out, ""); // cape id
        BedrockVarInt.writeString(out, ""); // full skin id
        BedrockVarInt.writeString(out, ""); // arm size
        BedrockVarInt.writeString(out, ""); // skin color
        out.writeLittleInt(0); // personal pieces
        out.writeLittleInt(0); // piece tint colors
        out.writeBoolean(false); // premium
        out.writeBoolean(false); // persona
        out.writeBoolean(false); // cape on classic
        out.writeBoolean(false); // primary user
        out.writeBoolean(false); // overriding player appearance
    }

    /** PlayerList（add 1 件）を作る。 */
    public static byte[] encodePlayerListAdd(UUID uuid, long entityUniqueId, String username) {
        return encodePlayerListAdd(uuid, entityUniqueId, username, BedrockVersion.V1_20_50);
    }

    /** PlayerList（add 1 件）を作る（バージョン別。verified は 390 以降のみ存在し、389 には無い。361 は旧直書きエントリ形式）。 */
    public static byte[] encodePlayerListAdd(UUID uuid, long entityUniqueId, String username,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        out.writeByte(0); // type: add
        BedrockVarInt.writeUnsignedVarInt(out, 1); // records: 1 件
        writeUuid(out, uuid);
        BedrockVarInt.writeZigzag64(out, entityUniqueId);
        BedrockVarInt.writeString(out, username);
        if (version.playerListUsesLegacyEntry()) {
            // 361 の旧直書き形式（PlayerListSerializer_v291）：スキンの
            // 5 要素をエントリに埋め込む。値は writeEmptySkin と同じ
            // 「空」方針（BE-NOTES.md §31）。
            BedrockVarInt.writeString(out, ""); // skin id
            BedrockVarInt.writeUnsignedVarInt(out, 0); // skin data: なし
            BedrockVarInt.writeUnsignedVarInt(out, 0); // cape data: なし
            BedrockVarInt.writeString(out, ""); // geometry name
            BedrockVarInt.writeString(out, ""); // geometry data
            BedrockVarInt.writeString(out, ""); // xbox user id
            BedrockVarInt.writeString(out, ""); // platform chat id
            return out.bytes();
        }
        BedrockVarInt.writeString(out, ""); // xbox user id
        BedrockVarInt.writeString(out, ""); // platform chat id
        out.writeLittleInt(0); // build platform: Undefined
        writeEmptySkin(out);
        out.writeBoolean(false); // is teacher
        out.writeBoolean(false); // is host
        if (version.playerListAddHasVerified()) {
            out.writeBoolean(false); // verified（390 以降：本体の後に records_count 個の bool 配列）
        }
        return out.bytes();
    }

    /** PlayerList（remove 1 件）を作る。 */
    public static byte[] encodePlayerListRemove(UUID uuid) {
        return encodePlayerListRemove(uuid, BedrockVersion.V1_20_50);
    }

    /** PlayerList（remove 1 件）を作る（バージョン別。verified は 422・428 では remove でも存在し、それ以外は add のみ）。 */
    public static byte[] encodePlayerListRemove(UUID uuid, BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        out.writeByte(1); // type: remove
        BedrockVarInt.writeUnsignedVarInt(out, 1); // records: 1 件
        writeUuid(out, uuid);
        if (version.playerListRemoveHasVerified()) {
            out.writeBoolean(false); // verified（422・428：remove でも records_count 個の bool 配列）
        }
        return out.bytes();
    }

    // ---- Inventory Transaction (0x1e) の復号（Bedrock → Java） ----

    /** item use on entity の内容（それ以外は null）。 */
    public record InventoryTransactionUse(long entityRuntimeId, int actionType) {
    }

    /**
     * InventoryTransaction を読む。transaction_type が 3（item use on
     * entity）のときだけ、その対象エンティティと action（0＝インタラクト、
     * 1＝攻撃）を返す。それ以外・読めない場合は null。
     */
    public static InventoryTransactionUse decodeInventoryTransaction(byte[] payload) {
        return decodeInventoryTransaction(payload, BedrockVersion.V1_20_50);
    }

    /**
     * InventoryTransaction を読む（バージョン別）。
     * 取引の形式は 26.30 で変わり（legacy_transactions が option 化、
     * アイテムが ItemV4 化、action_type が zigzag32 化）、26.40 で
     * transaction_type・actions も option 化した。390 には
     * legacy_request_id 自体が無く、transaction_type から始まる
     * （legacy_request_id は v407 で追加。BE-NOTES.md §28）。
     */
    public static InventoryTransactionUse decodeInventoryTransaction(byte[] payload,
            BedrockVersion version) {
        try {
            ByteReader in = new ByteReader(payload);
            boolean itemV4 = version.transactionUsesItemV4();
            // 390 には legacy_request_id が無いため、先頭は
            // transaction_type から始まる（v407 で追加された
            // フィールド。transactionHasLegacyRequestId() を参照）。
            int legacyRequestId = 0;
            if (version.transactionHasLegacyRequestId()) {
                legacyRequestId = BedrockVarInt.readZigzag32(in);
                if (version.transactionLegacyIsOption()) {
                    if (in.readBoolean()) { // legacy_transactions あり
                        skipLegacyTransactions(in);
                    }
                } else if (legacyRequestId != 0) {
                    skipLegacyTransactions(in);
                }
            }
            int transactionType;
            if (version.transactionFieldsAreOptions()) {
                if (!in.readBoolean()) { // transaction_type なし
                    return null;
                }
                transactionType = BedrockVarInt.readVarInt(in);
                if (!in.readBoolean()) { // actions なし
                    return null;
                }
                long actionCount = BedrockVarInt.readUnsignedVarInt(in);
                for (long i = 0; i < actionCount; i++) {
                    skipTransactionActionV2(in);
                }
            } else {
                transactionType = BedrockVarInt.readVarInt(in);
                long actionCount = BedrockVarInt.readUnsignedVarInt(in);
                for (long i = 0; i < actionCount; i++) {
                    if (itemV4) {
                        skipTransactionActionV2(in);
                    } else if (!version.transactionHasLegacyRequestId()) {
                        skipTransactionActionV0(in);
                    } else {
                        skipTransactionActionV1(in);
                    }
                }
            }
            if (transactionType != BedrockProtocol.TRANSACTION_ITEM_USE_ON_ENTITY) {
                return null;
            }
            long entityRuntimeId = BedrockVarInt.readVarLong(in);
            int actionType = itemV4
                    ? BedrockVarInt.readZigzag32(in)
                    : BedrockVarInt.readVarInt(in);
            BedrockVarInt.readZigzag32(in); // hotbar slot
            if (version.transactionHasHand()) {
                in.readUnsignedByte(); // hand（2225 で新規。hotbar_slot と held_item の間）
            }
            skipItem(in, itemV4); // held item
            in.readLittleFloat(); // player pos x
            in.readLittleFloat(); // player pos y
            in.readLittleFloat(); // player pos z
            in.readLittleFloat(); // click pos x
            in.readLittleFloat(); // click pos y
            in.readLittleFloat(); // click pos z
            return new InventoryTransactionUse(entityRuntimeId, actionType);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** legacy_transactions の配列（container_id＋changed_slots）を読み飛ばす。 */
    private static void skipLegacyTransactions(ByteReader in) {
        long legacyCount = BedrockVarInt.readUnsignedVarInt(in);
        for (long i = 0; i < legacyCount; i++) {
            in.readUnsignedByte(); // container id
            long slots = BedrockVarInt.readUnsignedVarInt(in);
            for (long j = 0; j < slots; j++) {
                in.readUnsignedByte(); // slot id
            }
        }
    }

    /** 取引 action 1 件を読み飛ばす（26.30 より前の形式）。 */
    private static void skipTransactionActionV1(ByteReader in) {
        int sourceType = BedrockVarInt.readVarInt(in);
        switch (sourceType) {
            case 0 -> BedrockVarInt.readZigzag32(in); // container: window id
            case 99999, 100 -> BedrockVarInt.readVarInt(in); // craft / craft slot
            case 2 -> BedrockVarInt.readVarInt(in); // world interaction: flags
            default -> {
                // global / 不明：何も無い。
            }
        }
        BedrockVarInt.readUnsignedVarInt(in); // slot
        skipItem(in); // old item
        skipItem(in); // new item
    }

    /**
     * 取引 action 1 件を読み飛ばす（390 の v291 世代の基本形式。
     * CloudburstMC Protocol の Base ヘルパーの readInventoryAction／
     * readSource：source は container（0）と non-implemented（99999）が
     * zigzag32 の window id、world interaction（2）が varint の flags、
     * それ以外（global／creative／untracked 100 等）は追加フィールド
     * 無し。先行するネットワーク ID の有無フラグ（bool）や stack_network_id は無い。
     * BE-NOTES.md §28）。
     */
    private static void skipTransactionActionV0(ByteReader in) {
        int sourceType = BedrockVarInt.readVarInt(in);
        switch (sourceType) {
            case 0, 99999 -> BedrockVarInt.readZigzag32(in); // container / non-implemented: window id
            case 2 -> BedrockVarInt.readVarInt(in); // world interaction: flags
            default -> {
                // global / creative / untracked 等：何も無い。
            }
        }
        BedrockVarInt.readUnsignedVarInt(in); // slot
        skipItem(in); // old item
        skipItem(in); // new item
    }

    /** 取引 action 1 件を読み飛ばす（26.30 以降の形式。アイテムは ItemV4）。 */
    private static void skipTransactionActionV2(ByteReader in) {
        BedrockVarInt.readVarInt(in); // source type
        if (in.readBoolean()) { // container presence
            if (in.readBoolean()) { // window id あり（option）
                in.readUnsignedByte(); // window id (i8)
            }
        }
        if (in.readBoolean()) { // flag presence
            if (in.readBoolean()) { // flags あり（option）
                BedrockVarInt.readVarInt(in); // flags
            }
        }
        BedrockVarInt.readUnsignedVarInt(in); // slot
        skipItem(in, true); // old item (ItemV4)
        skipItem(in, true); // new item (ItemV4)
    }

    // ---- Player Action (0x24) / Interact (0x21) の復号（Bedrock → Java） ----

    /** Player Action の内容（runtime id と action 番号だけ）。 */
    public record PlayerAction(long runtimeId, int action) {
    }

    /** PlayerAction を読む（position 等は読み飛ばす）。 */
    public static PlayerAction decodePlayerAction(byte[] payload) {
        try {
            ByteReader in = new ByteReader(payload);
            long runtimeId = BedrockVarInt.readVarLong(in);
            int action = BedrockVarInt.readZigzag32(in);
            // position（vec3i）
            BedrockVarInt.readZigzag32(in);
            BedrockVarInt.readUnsignedVarInt(in);
            BedrockVarInt.readZigzag32(in);
            // result position（vec3i）
            BedrockVarInt.readZigzag32(in);
            BedrockVarInt.readUnsignedVarInt(in);
            BedrockVarInt.readZigzag32(in);
            BedrockVarInt.readZigzag32(in); // face
            return new PlayerAction(runtimeId, action);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Interact の内容（action と対象エンティティだけ）。 */
    public record Interact(int actionId, long targetEntityId) {
    }

    /** Interact を読む。 */
    public static Interact decodeInteract(byte[] payload) {
        try {
            ByteReader in = new ByteReader(payload);
            int actionId = in.readUnsignedByte();
            long targetEntityId = BedrockVarInt.readVarLong(in);
            if (actionId == 3 || actionId == 4) {
                // leave_vehicle / mouse_over_entity：position（vec3f）が付く。
                in.readLittleFloat();
                in.readLittleFloat();
                in.readLittleFloat();
            }
            return new Interact(actionId, targetEntityId);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
