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
        BedrockVarInt.writeUnsignedVarInt(out, 0); // tick
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
     */
    public static byte[] encodeResourcePacksInfo(BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        out.writeBoolean(false); // must accept
        if (version.hasAddons()) {
            out.writeBoolean(false); // has addons
        }
        out.writeBoolean(false); // has scripts
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
     */
    public static byte[] encodeResourcePackStack(BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        out.writeBoolean(false); // must accept
        if (version.hasBehaviorPacksInStack()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // behaviour packs
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0); // resource packs
        BedrockVarInt.writeString(out, version.displayName());
        BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments
        out.writeBoolean(false); // experiments previously used
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
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, chunkX);
        BedrockVarInt.writeZigzag32(out, chunkZ);
        BedrockVarInt.writeUnsignedVarInt(out, subChunkCount);
        out.writeBoolean(false); // cache disabled
        BedrockVarInt.writeUnsignedVarInt(out, payload.length); // ByteArray
        out.writeBytes(payload);
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
     * radius はブロック単位（チャンク半径 × 16）。
     */
    public static byte[] encodeNetworkChunkPublisherUpdate(int blockX, int blockY, int blockZ,
                                                           int radiusBlocks) {
        ByteWriter out = new ByteWriter();
        writeBlockCoordinates(out, blockX, blockY, blockZ);
        BedrockVarInt.writeUnsignedVarInt(out, radiusBlocks);
        out.writeLittleInt(0); // saved chunks: なし
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
     * protocol.json で確認した：hardcore は 1.20.80 以降、level_id 前の
     * 識別子は 1.21.0〜1.21.80 で 3 つ・1.21.90〜1.21.130 で 4 つ、
     * itemstates は 1.21.40 まで、movement_authority は 1.21.80 まで、
     * tick_death_systems は 1.21.100〜1.21.120 のみ、末尾の
     * has_server_join_info 等は 26.0 以降、編集者向け項目は 26.30 以降、
     * is_chat_logging は 26.30 のみ。
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
        out.writeLittleLong(0); // seed
        out.writeLittleInt16(1); // biome type: plains
        BedrockVarInt.writeString(out, "minecraft:plains"); // biome name
        BedrockVarInt.writeZigzag32(out, 0); // dimension: overworld
        BedrockVarInt.writeZigzag32(out, 1); // generator: infinite
        BedrockVarInt.writeZigzag32(out, 0); // world gamemode: survival
        if (version.hasHardcore()) {
            out.writeBoolean(false); // hardcore
        }
        BedrockVarInt.writeZigzag32(out, 2); // difficulty: normal
        writeBlockCoordinates(out, 0, 64, 0); // spawn position
        out.writeBoolean(false); // achievements disabled
        BedrockVarInt.writeZigzag32(out, 0); // editor world type: not editor
        out.writeBoolean(false); // created in editor
        out.writeBoolean(false); // exported from editor
        BedrockVarInt.writeZigzag32(out, 0); // day cycle stop time
        BedrockVarInt.writeZigzag32(out, 0); // edu offer: none
        out.writeBoolean(false); // edu features enabled
        BedrockVarInt.writeString(out, ""); // edu product uuid
        out.writeLittleFloat(0); // rain level
        out.writeLittleFloat(0); // lightning level
        out.writeBoolean(false); // has confirmed platform locked content
        out.writeBoolean(true); // is multiplayer
        out.writeBoolean(true); // broadcast to lan
        BedrockVarInt.writeUnsignedVarInt(out, 0); // xbox live broadcast mode
        BedrockVarInt.writeUnsignedVarInt(out, 0); // platform broadcast mode
        out.writeBoolean(true); // enable commands
        out.writeBoolean(false); // is texturepacks required
        BedrockVarInt.writeUnsignedVarInt(out, 0); // gamerules: なし
        BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし
        out.writeBoolean(false); // experiments previously used
        out.writeBoolean(false); // bonus chest
        out.writeBoolean(false); // map enabled
        out.writeByte(1); // permission level (u8): member
        out.writeLittleInt(4); // server chunk tick range
        out.writeBoolean(false); // has locked behavior pack
        out.writeBoolean(false); // has locked resource pack
        out.writeBoolean(false); // is from locked world template
        out.writeBoolean(false); // msa gamertags only
        out.writeBoolean(false); // is from world template
        out.writeBoolean(false); // is world template option locked
        out.writeBoolean(false); // only spawn v1 villagers
        out.writeBoolean(false); // persona disabled
        out.writeBoolean(false); // custom skins disabled
        out.writeBoolean(false); // emote chat muted
        BedrockVarInt.writeString(out, info.gameVersion()); // game version
        out.writeLittleInt(0); // limited world width
        out.writeLittleInt(0); // limited world length
        out.writeBoolean(false); // is new nether
        BedrockVarInt.writeString(out, ""); // edu resource uri: button name
        BedrockVarInt.writeString(out, ""); // edu resource uri: link uri
        out.writeBoolean(false); // experimental gameplay override
        out.writeByte(0); // chat restriction level (u8): none
        out.writeBoolean(false); // disable player interactions
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
            BedrockVarInt.writeZigzag32(out, 0); // movement authority: client
        }
        BedrockVarInt.writeZigzag32(out, 0); // rewind history size
        out.writeBoolean(false); // server authoritative block breaking
        out.writeLittleLong(0); // current tick
        BedrockVarInt.writeZigzag32(out, 0); // enchantment seed
        if (palette == null) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // block properties: なし
        } else {
            BedrockVarInt.writeUnsignedVarInt(out, palette.count());
            out.writeBytes(palette.rawPaletteBytes());
        }
        if (version.hasItemstates()) {
            BedrockVarInt.writeUnsignedVarInt(out, 0); // itemstates: なし
        }
        BedrockVarInt.writeString(out,
                UUID.randomUUID().toString()); // multiplayer correlation id
        out.writeBoolean(false); // server authoritative inventory
        BedrockVarInt.writeString(out, ""); // engine
        // property data（空の NBT compound）。varint NBT なので
        // 0x0A（compound）＋名前長 0（varint 1 バイト）＋0x00（TAG_End）の 3 バイト。
        out.writeByte(0x0A); // TAG_Compound
        out.writeByte(0); // 名前長 0（varint）
        out.writeByte(0x00); // TAG_End
        out.writeLittleLong(0); // block palette checksum
        out.writeBytes(new byte[16]); // world template id（空 UUID）
        out.writeBoolean(false); // client side generation
        out.writeBoolean(false); // block network ids are hashes
        if (version.hasTickDeathSystems()) {
            out.writeBoolean(false); // tick death systems
        }
        out.writeBoolean(false); // server controlled sound
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
     */
    public static byte[] encodeAddEntity(long runtimeId, String entityType,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            float velX, float velY, float velZ,
            float health, float maxHealth,
            BedrockEntityTranslator.BedrockEntityData data) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, runtimeId); // unique id
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId); // runtime id
        BedrockVarInt.writeString(out, entityType);
        writeVec3f(out, x, y, z);
        writeVec3f(out, velX, velY, velZ);
        out.writeLittleFloat(pitch);
        out.writeLittleFloat(yaw);
        out.writeLittleFloat(headYaw);
        out.writeLittleFloat(yaw); // body yaw
        BedrockVarInt.writeUnsignedVarInt(out, 1); // attributes: 1 件
        BedrockVarInt.writeString(out, "minecraft:health");
        out.writeLittleFloat(0); // min
        out.writeLittleFloat(health); // value
        out.writeLittleFloat(maxHealth); // max
        out.writeBytes(data == null
                ? new BedrockEntityTranslator.BedrockEntityData().bytes() : data.bytes());
        writeEmptyEntityProperties(out);
        writeEmptyLinks(out);
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

    /** AddPlayer を作る（バージョン別。held_item は 26.40 以降 ItemV4）。 */
    public static byte[] encodeAddPlayer(UUID uuid, String username, long runtimeId,
            float x, float y, float z, float pitch, float yaw, float headYaw,
            int gamemode, BedrockEntityTranslator.BedrockEntityData data,
            BedrockVersion version) {
        ByteWriter out = new ByteWriter();
        writeUuid(out, uuid);
        BedrockVarInt.writeString(out, username);
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        BedrockVarInt.writeString(out, ""); // platform chat id
        writeVec3f(out, x, y, z);
        writeVec3f(out, 0, 0, 0); // velocity
        out.writeLittleFloat(pitch);
        out.writeLittleFloat(yaw);
        out.writeLittleFloat(headYaw);
        writeAirItem(out, version.addItemUsesItemV4()); // held item
        BedrockVarInt.writeZigzag32(out, gamemode);
        out.writeBytes(data == null
                ? new BedrockEntityTranslator.BedrockEntityData().bytes() : data.bytes());
        writeEmptyEntityProperties(out);
        out.writeLittleLong(runtimeId); // unique id
        out.writeByte(1); // permission level: member
        out.writeByte(0); // command permission: normal
        out.writeByte(0); // abilities: 0 層
        writeEmptyLinks(out);
        BedrockVarInt.writeString(out, ""); // device id
        out.writeLittleInt(0); // device os: Undefined
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
        BedrockVarInt.writeUnsignedVarInt(out, 0); // tick
        return out.bytes();
    }

    // ---- Set Entity Data (0x27) / Set Entity Motion (0x28) / Set Entity Link (0x29) ----

    /** SetEntityData を作る（properties は空、tick は 0）。 */
    public static byte[] encodeSetEntityData(long runtimeId,
            BedrockEntityTranslator.BedrockEntityData data) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        out.writeBytes(data == null
                ? new BedrockEntityTranslator.BedrockEntityData().bytes() : data.bytes());
        writeEmptyEntityProperties(out);
        BedrockVarInt.writeUnsignedVarInt(out, 0); // tick
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

    /** UpdateAttributes（体力 1 件）を作る。 */
    public static byte[] encodeUpdateAttributes(long runtimeId,
            float health, float maxHealth) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId);
        BedrockVarInt.writeUnsignedVarInt(out, 1); // attributes: 1 件
        out.writeLittleFloat(0); // min
        out.writeLittleFloat(maxHealth); // max
        out.writeLittleFloat(health); // current
        out.writeLittleFloat(maxHealth); // default
        BedrockVarInt.writeString(out, "minecraft:health");
        BedrockVarInt.writeUnsignedVarInt(out, 0); // modifiers: なし
        BedrockVarInt.writeUnsignedVarInt(out, 0); // tick
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
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag64(out, runtimeId); // entity id self
        BedrockVarInt.writeUnsignedVarInt(out, runtimeId); // runtime id
        writeVec3f(out, x, y, z);
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
        ByteWriter out = new ByteWriter();
        out.writeByte(0); // type: add
        BedrockVarInt.writeUnsignedVarInt(out, 1); // records: 1 件
        writeUuid(out, uuid);
        BedrockVarInt.writeZigzag64(out, entityUniqueId);
        BedrockVarInt.writeString(out, username);
        BedrockVarInt.writeString(out, ""); // xbox user id
        BedrockVarInt.writeString(out, ""); // platform chat id
        out.writeLittleInt(0); // build platform: Undefined
        writeEmptySkin(out);
        out.writeBoolean(false); // is teacher
        out.writeBoolean(false); // is host
        out.writeBoolean(false); // verified
        return out.bytes();
    }

    /** PlayerList（remove 1 件）を作る。 */
    public static byte[] encodePlayerListRemove(UUID uuid) {
        ByteWriter out = new ByteWriter();
        out.writeByte(1); // type: remove
        BedrockVarInt.writeUnsignedVarInt(out, 1); // records: 1 件
        writeUuid(out, uuid);
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
     * transaction_type・actions も option 化した。
     */
    public static InventoryTransactionUse decodeInventoryTransaction(byte[] payload,
            BedrockVersion version) {
        try {
            ByteReader in = new ByteReader(payload);
            boolean itemV4 = version.transactionUsesItemV4();
            int legacyRequestId = BedrockVarInt.readZigzag32(in);
            if (version.transactionLegacyIsOption()) {
                if (in.readBoolean()) { // legacy_transactions あり
                    skipLegacyTransactions(in);
                }
            } else if (legacyRequestId != 0) {
                skipLegacyTransactions(in);
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
