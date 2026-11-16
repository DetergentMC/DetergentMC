package io.detergentmc.bedrock;

import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java の Chunk Data パケットを Bedrock の LevelChunk ペイロードへ翻訳する。
 *
 * <p>形式の出典：Bedrock 1.20.40 の LevelChunk は PrismarineJS minecraft-data の
 * {@code protocol.json}（{@code packet_level_chunk}：x/z zigzag32、sub_chunk_count varint、
 * cache_enabled bool、payload ByteArray）で、ペイロードの中身は Minecraft Wiki
 * 「Pocket Edition level format」と wiki.vg「Bedrock Edition level format」、
 * および Go の chunk パッケージ（dragonfly 由来、pkg.go.dev）の公開ドキュメントによる：
 * <ul>
 *   <li>各サブチャンクはバージョン 9（1.18 以降）：バージョン 1 バイト、層数 1 バイト、
 *       サブチャンク Y インデックス（符号付き 1 バイト）。</li>
 *   <li>各層はパレット付きストレージ：先頭 1 バイトは上位 7 ビットが
 *       パレットあたりのビット数、最下位ビットが 0＝ランタイムパレット
 *       （varint のランタイムID）／1＝永続パレット（NBT）。</li>
 *   <li>ランタイムパレット：パレット長（varint）＋ランタイムID（varint）の列。
 *       ビット数が 0 より大きい場合、続くデータ配列は 32 ビットワード数（varint）
 *       ＋リトルエンディアンの uint32 列。各ワードは floor(32 / ビット数) 個の
 *       インデックスを下位ビットから詰める。インデックスは XZY 順（Y が最も速い）。</li>
 *   <li>ペイロードはサブチャンク列の後に、サブチャンクごとのバイオームの
 *       パレット付きストレージ（4×4×4＝64 エントリ）が同じ順で並び、最後に
 *       ボーダーブロック数（varint 0）とブロックエンティティ数（varint 0）が付く。
 *       （ブロックエンティティは送らない。チェスト等は見た目だけの普通のブロックになる。）</li>
 * </ul>
 *
 * <p>Java 側の表は二重に作らない。ブロック状態IDは
 * {@link RegistryRemapper} の 765↔763／764／766 表で 765 に正規化してから
 * {@link BedrockBlockRuntimeIds} に渡す。763→765 は 765→763 表の逆引き
 * （ClientAdapter と同じ初出採用の規則）で作る。バイオームは
 * {@link BedrockBiomeIds} に名前で引く。
 *
 * <p>制限：光データは Bedrock のチャンクに無い（クライアントが計算する）ため送らない。
 * ブロックエンティティは送らない。水没したブロックは層 1 に水を置く。水・溶岩そのものは
 * 対応表のランタイムIDで送る。葉の透過などは送ったブロック状態の描画に任せる。
 */
public final class BedrockChunkTranslator {

    /** Bedrock サブチャンクのバージョン（1.18 以降の形式）。 */
    public static final int SUB_CHUNK_VERSION = 9;

    /** オーバーワールドのサブチャンク数（Y -64〜319）。 */
    public static final int OVERWORLD_SUB_CHUNK_COUNT = 24;

    /** ネザーのサブチャンク数（Y 0〜127）。 */
    public static final int NETHER_SUB_CHUNK_COUNT = 8;

    /** ジ・エンドのサブチャンク数（Y 0〜255）。 */
    public static final int END_SUB_CHUNK_COUNT = 16;

    /** 水の Bedrock ランタイムID（深さ 0、水没層用）。 */
    public static final int WATER_RUNTIME_ID = 9304;

    private static final int SECTION_BLOCKS = 4096;
    private static final int SECTION_BIOMES = 64;

    private static final int[] BLOCK_STATE_763_TO_765 = buildInverse763();

    private BedrockChunkTranslator() {
    }

    /** 翻訳への入力。 */
    public record ChunkInput(int chunkX, int chunkZ, byte[] javaChunkPayload, int serverProtocol,
                             int minY, int height, List<String> biomeNames) {
    }

    /** 翻訳済みの Bedrock チャンク（LevelChunk のペイロード部分）。 */
    public record TranslatedChunk(int chunkX, int chunkZ, byte[] payload, int subChunkCount) {
    }

    /** 1 ブロックの変更（Update Block 用）。 */
    public record BlockChange(int runtimeId, boolean waterlogged) {
    }

    /**
     * Java のブロック状態IDを 765 に正規化する（Bedrock 変換の共通の入口）。
     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は
     * 767→765 表、768 は 768→765 表、769 は 769→765 表、770 は 770→765 表、
     * 771 は 771→765 表、772 は 772→765 表、773 は 773→765 表、774 は 774→765 表、775 は 775→765 表、757 は
     * 757→759→760→761→762→763→765 の連鎖（mapBlockState757To765 は
     * RegistryRemapper に無いため、このクラスで既存の隣接表と 763→765 の
     * 逆引き表を繋いで作る。RegistryRemapper には追加しない）、756 は
     * 756→757→759→760→761→762→763→765 の連鎖（mapBlockState756To765。
     * 756・757 のブロック状態は 20,342 件・同一順序のため 756→757 は恒等写像。
     * Stage A §11.1 実測）、755 は
     * 755→756→757→759→760→761→762→763→765 の連鎖（mapBlockState755To765。
     * 755・756 のブロック状態は 20,342 件・同一順序のため 755→756 は恒等写像。
     * Stage A §11.1 実測）、754 は
     * 754→755→756→757→759→760→761→762→763→765 の連鎖（mapBlockState754To765。
     * 754・755 のブロック状態は 17,112 件／20,342 件で、754→755 は
     * RegistryTables754 の表（Stage A §11.1 実測）。オークの丸太は
     * 754 の axis=y が 74、755 では 77）、753 は
     * 753→754→755→756→757→759→760→761→762→763→765 の連鎖（mapBlockState753To765。
     * 753→754 は恒等写像（RegistryTables753。17,112 件。Stage A §11.1・Stage B §14.4 実測）
     * のため 754 の連鎖にそのまま繋ぐ）、763 は
     * 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。757 の 20,342 状態は 759 で全件カバーされる
     * （Stage A §11.1 実測）ため、連鎖で石に落ちるのは範囲外の番号だけ。
     */
    public static int normalizeBlockState(int stateId, int serverProtocol) {
        return switch (serverProtocol) {
            case RegistryRemapper.PROTOCOL_763 -> {
                if (stateId < 0 || stateId >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[stateId];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_764 -> RegistryRemapper.mapBlockStateUp764(stateId);
            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapBlockStateUp766(stateId);
            case RegistryRemapper.PROTOCOL_767 -> RegistryRemapper.mapBlockState767To765(stateId);
            case RegistryRemapper.PROTOCOL_768 -> RegistryRemapper.mapBlockState768To765(stateId);
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapBlockState769To765(stateId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapBlockState770To765(stateId);
            case RegistryRemapper.PROTOCOL_771 -> RegistryRemapper.mapBlockState771To765(stateId);
            case RegistryRemapper.PROTOCOL_772 -> RegistryRemapper.mapBlockState772To765(stateId);
            case RegistryRemapper.PROTOCOL_773 -> RegistryRemapper.mapBlockState773To765(stateId);
            case RegistryRemapper.PROTOCOL_774 -> RegistryRemapper.mapBlockState774To765(stateId);
            case RegistryRemapper.PROTOCOL_775 -> RegistryRemapper.mapBlockState775To765(stateId);
            case RegistryRemapper.PROTOCOL_757 -> mapBlockState757To765(stateId);
            case RegistryRemapper.PROTOCOL_756 -> mapBlockState756To765(stateId);
            case RegistryRemapper.PROTOCOL_755 -> mapBlockState755To765(stateId);
            case RegistryRemapper.PROTOCOL_753 -> mapBlockState753To765(stateId);
            case RegistryRemapper.PROTOCOL_754 -> mapBlockState754To765(stateId);
            default -> stateId;
        };
    }

    /**
     * 757 のブロック状態IDを 765 に正規化する。RegistryRemapper に
     * mapBlockState757To765 は無いため、既存の隣接表の連鎖
     * 757→759→760→761→762→763 と、763→765 の逆引き表
     * （{@link #BLOCK_STATE_763_TO_765}）を繋ぐ。各段で範囲外は石に
     * 落ちる（隣接表の代替）。757 の 20,342 状態は 759 で全件カバーされる
     * （Stage A §11.1 実測）ため、連鎖で石に落ちるのは範囲外の番号だけ。
     */
    static int mapBlockState757To765(int stateId) {
        int id759 = RegistryRemapper.mapBlockState757To759(stateId);
        int id760 = RegistryRemapper.mapBlockState759To760(id759);
        int id761 = RegistryRemapper.mapBlockState760To761(id760);
        int id762 = RegistryRemapper.mapBlockState761To762(id761);
        int id763 = RegistryRemapper.mapBlockState762To763(id762);
        if (id763 < 0 || id763 >= BLOCK_STATE_763_TO_765.length) {
            return 1;
        }
        int mapped = BLOCK_STATE_763_TO_765[id763];
        return mapped < 0 ? 1 : mapped;
    }

    /**
     * 756 のブロック状態IDを 765 に正規化する。756→757 は恒等写像
     * （20,342 件・同一順序。Stage A §11.1 実測）のため、757 の連鎖
     * （{@link #mapBlockState757To765}）にそのまま繋ぐ。
     * RegistryRemapper には追加しない。
     */
    static int mapBlockState756To765(int stateId) {
        return mapBlockState757To765(RegistryRemapper.mapBlockState756To757(stateId));
    }

    /**
     * 755 のブロック状態IDを 765 に正規化する。755→756 は恒等写像
     * （20,342 件・同一順序。Stage A §11.1 実測）のため、756 の連鎖
     * （{@link #mapBlockState756To765}）にそのまま繋ぐ。
     * RegistryRemapper には追加しない。
     */
    static int mapBlockState755To765(int stateId) {
        return mapBlockState756To765(RegistryRemapper.mapBlockState755To756(stateId));
    }

    /**
     * 754 のブロック状態IDを 765 に正規化する。754→755 は RegistryTables754 の
     * 表（Stage A §11.1 実測。17,112 件。オークの丸太 axis=y は 754 が 74、
     * 755 が 77）のため、755 の連鎖（{@link #mapBlockState755To765}）に
     * そのまま繋ぐ。RegistryRemapper には追加しない。
     */
    static int mapBlockState754To765(int stateId) {
        return mapBlockState755To765(RegistryRemapper.mapBlockState754To755(stateId));
    }

    /**
     * 753 のブロック状態IDを 765 に正規化する。753→754 は恒等写像
     * （RegistryTables753。17,112 件・全件同一順序。Stage A §11.1・Stage B §14.4 実測。
     * 実バニラ 1.16.3 の blocks レポート（3,477,609 バイト）は 1.16.5 版とバイト同一）のため、
     * 754 の連鎖（{@link #mapBlockState754To765}）にそのまま繋ぐ。
     * RegistryRemapper には追加しない。
     */
    static int mapBlockState753To765(int stateId) {
        return mapBlockState754To765(RegistryRemapper.mapBlockState753To754(stateId));
    }


    /** Java のブロック状態ID（そのままのプロトコル番号）-> Bedrock のランタイムID。 */
    public static int runtimeIdFor(int stateId, int serverProtocol) {
        return BedrockBlockRuntimeIds.runtimeId(normalizeBlockState(stateId, serverProtocol));
    }

    /** Block Change 用：Java のブロック状態ID -> Bedrock の Update Block の内容。 */
    public static BlockChange translateBlockChange(int stateId, int serverProtocol) {
        int state765 = normalizeBlockState(stateId, serverProtocol);
        return new BlockChange(
                BedrockBlockRuntimeIds.runtimeId(state765),
                BedrockBlockRuntimeIds.isWaterlogged(state765));
    }

    /**
     * Java の Chunk Data ペイロードを Bedrock の LevelChunk ペイロードへ翻訳する。
     * Java 側のチャンクは必ず全列（full chunk）であることが前提。読めない場合は
     * IllegalArgumentException を投げる（呼び出し側で落とすか空気で代用する）。
     *
     * <p>756 の形式（Stage B §14.4 の実測。protocol.json＋実バニラ 1.17.1
     * 生キャプチャ＋両 jar の javap）：
     * {x i32, z i32, bitMap varint[i64], heightmaps 名前付き NBT,
     * biomes varint[1024], chunkData buffer, blockEntities varint[NBT]}。
     * セクションは 16 個（y 0〜15）で、chunkData には bitMap に立っている
     * セクションだけが入る（無いセクションは空気として補完するが、バイオームは
     * int[1024] の値をそのまま使う）。セクション内部は [blockCount i16]
     * ＋ブロックのパレット付きコンテナだけで、バイオームコンテナは無い。
     * バイオームは配列の [64s, 64s+64) がセクション s に対応する。
     * BitStorage は non-straddling（BPE 5 の Data Array 長は 342。
     * 実バニラ 1.17.1 の生キャプチャ cap756-chunk-21-17.bin で確認。
     * straddling 式だと 320）。光は 756 の Chunk Data には無く、別パケットの
     * Update Light で送られる。Bedrock 側は光をクライアントが計算するため、
     * ここでは光を送らない（757 のチャンク内包の光も同じく読まずに捨てている）。
     * ブロックエンティティは読まない（757 と同じく送らない）。
     *
     * <p>755 の形式は 756 と完全同一（Stage B §14.4 の実測。packet_map_chunk は
     * protocol.json で完全一致し、実バニラ 1.17／1.17.1 の Chunk Data クラス
     * qw はフィールド同一で javap -c の差分は難読化クラス名の付け替えのみ。
     * レジストリ差分も 0 件）。そのため 755 は下の 756 分岐をそのまま使う。
     * 1.17.1 の生キャプチャ cap756-chunk-21-17.bin は 755 形式のチャンク
     * でもある（Stage B §14.4）。
     *
     * <p>754 の形式（Stage B §14.4 の実測。mc-1.16-protocol.json（pc/1.16.2）の
     * packet_map_chunk＋実バニラ 1.16.5 の生キャプチャ
     * cap754-chunk-x-15-z-7.bin（チャンク -15,-7、10,633 バイト、groundUp=true、
     * bitMap 0x000f、biomes 1024 件全て 50＝deep_frozen_ocean）＋両 jar の javap）：
     * {x i32, z i32, groundUp bool, bitMap varint, heightmaps 名前付き NBT,
     * biomes varint[1024]（groundUp のときだけ）, chunkData buffer,
     * blockEntities varint[NBT]}。16 セクション（y 0〜15）で、chunkData には
     * bitMap に立っているセクションだけが入る（無いセクションは空気として補完するが、
     * バイオームは int[1024] の値をそのまま使う。755／756 と同じ扱い）。
     * groundUp=false のときバイオーム配列は無く、平原（1）で補う
     * （ChunkTranslator.translateChunkData754To755 と同じ補完）。
     * セクション内部は [blockCount i16]＋ブロックのパレット付きコンテナだけで、
     * バイオームコンテナは無い。バイオームは配列の [64s, 64s+64) がセクション s
     * に対応する。BitStorage は non-straddling（BPE 5 の Data Array 長は 342。
     * 実バニラ 1.16.5 の生キャプチャで確認。straddling 式だと 320）。光は
     * 754 の Chunk Data には無く、別パケットの Update Light で送られる。
     * ブロックエンティティは読まない（757 と同じく送らない）。
     *
     * <p>753 の形式は 754 と完全同一（Stage A §11.1・Stage B §14.4 の実測。
     * mc-1.16.3-protocol.json は mc-1.16-protocol.json とバイト同一（152,123 バイト）で
     * packet_map_chunk のフィールドも同一。レジストリ差分も 0 件（実バニラ 1.16.3 の
     * blocks レポート・registries.json は 1.16.5 版とバイト同一。ブロック状態 17,112・
     * ブロック 763・アイテム 976・バイオーム 79）。Join Game も実バニラ生キャプチャで
     * 30,847 バイト・1.16.5 と同一。そのため 753 は下の 754 分岐をそのまま使う。
     * 実バニラ 1.16.5 の生キャプチャ cap754-chunk-x-15-z-7.bin（10,633 バイト）は
     * 753 形式のバイトでもある（Stage B §14.4）。

     */
    public static TranslatedChunk translate(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        long[] bitmask756 = null;
        int[] biomes756 = null;
            if (input.serverProtocol() == RegistryRemapper.PROTOCOL_753
                    || input.serverProtocol() == RegistryRemapper.PROTOCOL_754) {
            // 753／754 だけの頭部：groundUp＋bitMap（varint の 16 ビット）＋
            // heightmaps（名前付き）＋groundUp のときだけバイオーム int[1024]。
            boolean groundUp = reader.readBoolean();
            int bitMap = reader.readVarInt();
            reader.readNamedNbt(); // heightmaps（名前付き。ルート名は空文字）
            if (groundUp) {
                int biomeCount = reader.readVarInt();
                if (biomeCount != 1024) {
                    throw new IllegalArgumentException(
                    "753／754 のバイオーム配列の件数が 1024 ではありません: " + biomeCount);
                }
                biomes756 = new int[1024];
                for (int i = 0; i < 1024; i++) {
                    biomes756[i] = reader.readVarInt();
                }
            } else {
                // groundUp=false のときバイオーム配列は無い。平原（1）で補う。
                biomes756 = new int[1024];
                Arrays.fill(biomes756, 1);
            }
            bitmask756 = new long[]{bitMap & 0xFFFFL};
        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_756
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_755) {
            // 755／756 だけの頭部：bitMap＋heightmaps（名前付き）＋バイオーム int[1024]。
            int maskCount = reader.readVarInt();
            if (maskCount < 0 || maskCount > 4) {
                throw new IllegalArgumentException("755／756 の bitMap の long 数が不正です: "
                        + maskCount);
            }
            bitmask756 = new long[maskCount];
            for (int i = 0; i < maskCount; i++) {
                bitmask756[i] = reader.readLong();
            }
            reader.readNamedNbt(); // heightmaps（名前付き。ルート名は空文字）
            int biomeCount = reader.readVarInt();
            if (biomeCount != 1024) {
                throw new IllegalArgumentException(
                        "755／756 のバイオーム配列の件数が 1024 ではありません: " + biomeCount);
            }
            biomes756 = new int[1024];
            for (int i = 0; i < 1024; i++) {
                biomes756[i] = reader.readVarInt();
            }
        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_763) {
            reader.readNamedNbt();
        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_770
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_771
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_772
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_773
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_774
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_775) {
            // 770／771／772／773／774／775 の heightmap は NBT ではなく配列（varint 個数 ×
            // {type varint, data long[] }）。NBT として読むと壊れるため
            // 配列として読み飛ばす（高さの情報自体は Bedrock 変換では使わない）。
            int heightmapCount = reader.readVarInt();
            if (heightmapCount < 0 || heightmapCount > 16) {
                throw new IllegalArgumentException("Java チャンクの heightmap 数が不正です: "
                        + heightmapCount);
            }
            for (int i = 0; i < heightmapCount; i++) {
                int type = reader.readVarInt(); // type
                int dataLength = reader.readVarInt();
                if (dataLength < 0 || dataLength > reader.remaining() / 8) {
                    throw new IllegalArgumentException("Java チャンクの heightmap 長が不正です: "
                            + dataLength);
                }
                reader.readBytes(dataLength * 8);
            }
        } else {
            // 757／759／764／765／766／767／768／769 の heightmap は NBT。
            // 757 は実バニラ 1.18 の ClientboundLevelChunkPacketData.write の
            // バイトコードで確認（2026-11-15：heightmaps を NBT で書く）。
            reader.readAnonymousNbt();
        }
        int dataLength = reader.readVarInt();
        if (dataLength < 0 || dataLength > reader.remaining()) {
            throw new IllegalArgumentException(
                    "Java チャンクのセクション長が不正です: " + dataLength);
        }
        byte[] sectionBytes = reader.readBytes(dataLength);

        int sectionCount = Math.max(1, input.height() / 16);
        List<int[]> blockSections = new ArrayList<>(sectionCount);
        List<int[]> biomeSections = new ArrayList<>(sectionCount);
        PacketIO.Reader sections = new PacketIO.Reader(sectionBytes);
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_753
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_754
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_756
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_755) {
            // 753／754／755／756：16 セクション（y 0〜15）。chunkData には bitMap に立っている
            // セクションだけが入る。無いセクションはブロックだけ空気で補完し、
            // バイオームは int[1024] の値をそのまま使う（Stage B §14.4 の
            // ChunkTranslator.translateChunkData756To757 と同じ扱い）。
            // セクション内部は [blockCount i16]＋ブロックコンテナだけで、
            // バイオームコンテナは無い。バイオームは配列の [64s, 64s+64)。
            int sectionsToRead = Math.min(sectionCount, 16);
            for (int s = 0; s < sectionsToRead; s++) {
                if (bitmaskHas(bitmask756, s) && sections.remaining() > 0) {
                    sections.readShort(); // blockCount（使わない）
                    blockSections.add(readPalettedContainer(sections, true,
                            input.serverProtocol()));
                } else {
                    blockSections.add(airBlocks());
                }
                int[] sectionBiomes = new int[SECTION_BIOMES];
                for (int i = 0; i < SECTION_BIOMES; i++) {
                    sectionBiomes[i] = biomes756[s * 64 + i];
                }
                biomeSections.add(sectionBiomes);
            }
        } else {
            for (int i = 0; i < sectionCount && sections.remaining() > 0; i++) {
                // 757 はセクション先頭の short が 1 個（blockCount）で正しい。
                // 実バニラ 1.18 の LevelChunkSection.write のバイトコードで確認
                // （2026-11-15：nonEmptyBlockCount の次に 2 つのコンテナが続く。
                // fluidCount は 1.18 には無い）。770〜775 もここでは 1 short
                // しか読まないが、実バニラ 26.1 では fluidCount があるため疑わしい。
                // 770〜775 はここでは触らない（判断待ち）。
                int blockCount = sections.readShort(); // blockCount（使わない）
                blockSections.add(readPalettedContainer(sections, true,
                        input.serverProtocol()));
                biomeSections.add(readPalettedContainer(sections, false,
                        input.serverProtocol()));
            }
        }
        while (blockSections.size() < sectionCount) {
            blockSections.add(airBlocks());
            biomeSections.add(plainsBiomes());
        }

        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        // サブチャンク列。
        for (int i = 0; i < sectionCount; i++) {
            int subChunkY = (input.minY() + i * 16) / 16;
            writeSubChunk(out, blockSections.get(i), subChunkY, input.serverProtocol());
        }
        // バイオーム列（サブチャンクと同じ順）。
        for (int i = 0; i < sectionCount; i++) {
            writeBiomeStorage(out, biomeSections.get(i), input.biomeNames());
        }
        // ボーダーブロック 0 件、ブロックエンティティ 0 件。
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        return new TranslatedChunk(chunkX, chunkZ, out.bytes(), sectionCount);
    }

    private static int[] airBlocks() {
        // Java の空気（状態 0）で埋める。writeSubChunk 側が 765 化＋ランタイム ID 化
        // するため、ここで AIR_RUNTIME_ID（11881）を入れると二重変換になる
        // （11881 を Java 状態として読むと 4229 になり、waterlogged 扱いで 2 層になる）。
        int[] blocks = new int[SECTION_BLOCKS];
        Arrays.fill(blocks, 0);
        return blocks;
    }

/** 755／756 のビットマスク（long 列）のビットが立っているか。753／754 も bitMap を long[1] に直して同じ判定を使う。 */
    private static boolean bitmaskHas(long[] bitmask, int section) {
        if (bitmask == null) {
            return false;
        }
        int index = section / 64;
        if (index < 0 || index >= bitmask.length) {
            return false;
        }
        return (bitmask[index] & (1L << (section % 64))) != 0;
    }

    /** BitStorage が long の境界をまたがない詰め（non-straddling）か。753・754・755・756・757 が該当。 */
    private static boolean isNonStraddling(int serverProtocol) {
        return serverProtocol == RegistryRemapper.PROTOCOL_753
                || serverProtocol == RegistryRemapper.PROTOCOL_754
                || serverProtocol == RegistryRemapper.PROTOCOL_755
                || serverProtocol == RegistryRemapper.PROTOCOL_756
                || serverProtocol == RegistryRemapper.PROTOCOL_757;
    }

    private static int[] plainsBiomes() {
        int[] biomes = new int[SECTION_BIOMES];
        Arrays.fill(biomes, BedrockBiomeIds.FALLBACK_ID);
        return biomes;
    }

    /**
     * Java のパレット付きコンテナ 1 個を読む。ChunkTranslator と同じ
     * BPE の解釈（0＝シングル、ブロック 1〜8／バイオーム 1〜3＝間接、
     * それ以上＝直接）。戻り値は Java のインデックス順（ブロックは
     * {@code x | z<<4 | y<<8}、バイオームは {@code x | z<<2 | y<<4}）の
     * レジストリ番号列。ブロックはサーバーのプロトコル番号のまま
     * （765 への正規化は writeSubChunk 側で行う）、バイオームは
     * レジストリ番号のまま。
     */
    private static int[] readPalettedContainer(PacketIO.Reader reader, boolean blocks,
            int serverProtocol) {
        int entryCount = blocks ? SECTION_BLOCKS : SECTION_BIOMES;
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            if (serverProtocol != RegistryRemapper.PROTOCOL_770
                    && serverProtocol != RegistryRemapper.PROTOCOL_771
                    && serverProtocol != RegistryRemapper.PROTOCOL_772
                    && serverProtocol != RegistryRemapper.PROTOCOL_773
                    && serverProtocol != RegistryRemapper.PROTOCOL_774
                    && serverProtocol != RegistryRemapper.PROTOCOL_775) {
                int dataLength = reader.readVarInt();
                if (dataLength != 0) {
                    throw new IllegalArgumentException("シングルパレットの Data Array 長が 0 ではありません。");
                }
            }
            // 770／771／772／773／774／775では Data Array の長さ接頭辞が無いため、シングルパレットは
            // 値の直後が次のフィールドになる。
            int[] values = new int[entryCount];
            Arrays.fill(values, value);
            return values;
        }
        boolean indirect = blocks ? bitsPerEntry <= 8 : bitsPerEntry <= 3;
        int[] palette;
        if (indirect) {
            int paletteLength = reader.readVarInt();
            if (paletteLength <= 0 || paletteLength > (1 << bitsPerEntry)) {
                throw new IllegalArgumentException("パレット長が不正です: " + paletteLength);
            }
            palette = new int[paletteLength];
            for (int i = 0; i < paletteLength; i++) {
                palette[i] = reader.readVarInt();
            }
        } else {
            palette = null;
        }
        // 753・754・755・756・757 のみ例外：実バニラ 1.16.3／1.16.5／1.17／1.17.1／1.18 の SimpleBitStorage の
        // バイトコードで確認したところ、753・754・755・756・757 の BitStorage は long の境界を
        // またがない（1 long あたり floor(64 / bits) 個。long 数は
        // (entryCount + valuesPerLong - 1) / valuesPerLong）。
        // Data Array には長さの varint 接頭辞がある（FriendlyByteBuf.writeLongArray
        // が長さを varint で書く。PalettedContainer$Data.write のバイトコードで確認）。
        // 756 は実バニラ 1.17.1 の生キャプチャでも裏付ける（2026-11-16：
        // cap756-chunk-21-17.bin で BPE 5 の dataLen は 342。straddling 式だと 320）。
        // 755 は 756 と Chunk Data が完全同一のため同じ詰め（Stage B §14.4）。
        // 754 も実バニラ 1.16.5 の生キャプチャで裏付ける（2026-11-16：
        // cap754-chunk-x-15-z-7.bin で BPE 5 の dataLen は 342。Stage B §14.4）。
        // 753 は 754 と形式・レジストリとも同一（Stage A §11.1・Stage B §14.4）のため同じ詰め。
        // cap754-chunk-x-15-z-7.bin は 753 形式のバイトでもある。
        // （2026-11-15、server-1.18.jar の javap で確認。生キャプチャでも裏付ける。）
        int expected;
        if (isNonStraddling(serverProtocol)) {
            int valuesPerLong = 64 / bitsPerEntry;
            expected = (entryCount + valuesPerLong - 1) / valuesPerLong;
        } else {
            // 770〜775 以外の既存の前提：エントリを long の境界をまたいで連続して
            // 詰めるため、long 数は (entryCount * bitsPerEntry + 63) / 64。
            // （2026-11-14 のコメントの通り。ただし実バニラ 1.18／26.1 の
            // SimpleBitStorage はまたがないため、この前提は 757 とは別に
            // 770〜775 についても疑わしい。770〜775 はここでは触らない。）
            expected = (entryCount * bitsPerEntry + 63) / 64;
        }
        int dataLength;
        if (serverProtocol == RegistryRemapper.PROTOCOL_770
                || serverProtocol == RegistryRemapper.PROTOCOL_771
                || serverProtocol == RegistryRemapper.PROTOCOL_772
                || serverProtocol == RegistryRemapper.PROTOCOL_773
                || serverProtocol == RegistryRemapper.PROTOCOL_774
                || serverProtocol == RegistryRemapper.PROTOCOL_775) {
            // 1.21.5（770）／1.21.6（771）／1.21.7／1.21.8（772）／1.21.9／1.21.10（773）／1.21.11（774）／26.1（775）ではパレット付きコンテナの Data Array に長さの
            // varint 接頭辞が無い。長さは bitsPerEntry とエントリ数から計算する。
            // （実バニラ1.21.5のチャンクで確認：長さ varint を読むと 0x33=51 が
            //  dataLength になってしまい、その後のバイオームが壊れる。）
            dataLength = expected;
        } else {
            dataLength = reader.readVarInt();
            if (dataLength != expected) {
                throw new IllegalArgumentException("Data Array 長が想定と違います: "
                        + dataLength + "（想定=" + expected + "）。");
            }
        }
        long[] longs = new long[dataLength];
        for (int i = 0; i < dataLength; i++) {
            longs[i] = reader.readLong();
        }
        int[] values = new int[entryCount];
        long mask = (1L << bitsPerEntry) - 1;
        if (isNonStraddling(serverProtocol)) {
            // 753・754・755・756・757 の BitStorage は long の境界をまたがない（SimpleBitStorage.get：
            // cellIndex = index / valuesPerLong、bitIndex = (index % valuesPerLong) * bits）。
            // 実バニラ 1.16.5／1.17.1／1.18 のバイトコードで確認（2026-11-15／2026-11-16）。
            // 755 は 756 と Chunk Data が完全同一のため同じ詰め（Stage B §14.4）。
            // 754 は実バニラ 1.16.5 の生キャプチャで確認（Stage B §14.4）。
            // 753 は 754 と同一形式のため同じ詰め。
            int valuesPerLong = 64 / bitsPerEntry;
            for (int index = 0; index < entryCount; index++) {
                int cellIndex = index / valuesPerLong;
                int bitIndex = (index % valuesPerLong) * bitsPerEntry;
                int entry = (int) ((longs[cellIndex] >>> bitIndex) & mask);
                values[index] = indirect ? palette[entry] : entry;
            }
            return values;
        }
        // エントリは long の境界をまたぐことがある（bits が 64 を割り切らないとき）。
        // BitStorage.get と同じく、またぐ分は次の long の下位ビットから取る。
        // 旧式の「1 long あたり perLong 個を先頭から読む」は、またぐエントリの
        // 上位ビットを落として誤った値を返していた。
        int bitPos = 0;
        for (int index = 0; index < entryCount; index++) {
            int cellIndex = bitPos >> 6;
            int bitIndex = bitPos & 63;
            long low = longs[cellIndex];
            int entry;
            if (bitIndex + bitsPerEntry <= 64) {
                entry = (int) ((low >>> bitIndex) & mask);
            } else {
                int lowBits = 64 - bitIndex;
                long high = cellIndex + 1 < longs.length ? longs[cellIndex + 1] : 0L;
                entry = (int) (((low >>> bitIndex) | (high << lowBits)) & mask);
            }
            values[index] = indirect ? palette[entry] : entry;
            bitPos += bitsPerEntry;
        }
        return values;
    }

    /** 1 セクションを Bedrock のサブチャンク（バージョン 9）として書く。 */
    private static void writeSubChunk(BedrockVarInt.ByteWriter out, int[] javaBlocks, int subChunkY,
                                      int serverProtocol) {
        int[] layer0 = new int[SECTION_BLOCKS];
        int[] layer1 = new int[SECTION_BLOCKS];
        boolean hasWaterlogged = false;
        for (int j = 0; j < SECTION_BLOCKS; j++) {
            int state765 = normalizeBlockState(javaBlocks[j], serverProtocol);
            layer0[j] = BedrockBlockRuntimeIds.runtimeId(state765);
            if (BedrockBlockRuntimeIds.isWaterlogged(state765)) {
                layer1[j] = WATER_RUNTIME_ID;
                hasWaterlogged = true;
            } else {
                layer1[j] = BedrockBlockRuntimeIds.AIR_RUNTIME_ID;
            }
        }
        out.writeByte(SUB_CHUNK_VERSION);
        out.writeByte(hasWaterlogged ? 2 : 1);
        out.writeByte(subChunkY);
        writeBlockStorage(out, layer0, true);
        if (hasWaterlogged) {
            writeBlockStorage(out, layer1, true);
        }
    }

    /** 1 層（4096 ブロック）のパレット付きストレージを書く。 */
    private static void writeBlockStorage(BedrockVarInt.ByteWriter out, int[] javaOrderValues, boolean blocks) {
        writeStorage(out, javaOrderValues, blocks ? SECTION_BLOCKS : SECTION_BIOMES);
    }

    /** 1 サブチャンク分のバイオーム（64 エントリ）のパレット付きストレージを書く。 */
    private static void writeBiomeStorage(BedrockVarInt.ByteWriter out, int[] javaBiomes,
                                          List<String> biomeNames) {
        int[] bedrockBiomes = new int[SECTION_BIOMES];
        for (int j = 0; j < SECTION_BIOMES; j++) {
            int registryIndex = javaBiomes[j];
            int id = BedrockBiomeIds.FALLBACK_ID;
            if (biomeNames != null && registryIndex >= 0 && registryIndex < biomeNames.size()) {
                id = BedrockBiomeIds.idForName(biomeNames.get(registryIndex));
            } else {
                id = BedrockBiomeIds.idForJavaId(registryIndex);
            }
            bedrockBiomes[j] = id;
        }
        writeStorage(out, bedrockBiomes, SECTION_BIOMES);
    }

    /**
     * パレット付きストレージ 1 個を書く。{@code javaOrderValues} は Java の
     * インデックス順（ブロックは {@code x | z<<4 | y<<8}、バイオームは
     * {@code x | z<<2 | y<<4}）の値列で、Bedrock の XZY 順（Y が最も速い）に
     * 並べ替えて出す。
     */
    private static void writeStorage(BedrockVarInt.ByteWriter out, int[] javaOrderValues, int entryCount) {
        // パレット（初出順）。
        Map<Integer, Integer> paletteIndex = new LinkedHashMap<>();
        for (int value : javaOrderValues) {
            paletteIndex.putIfAbsent(value, paletteIndex.size());
        }
        int paletteSize = paletteIndex.size();
        int bits = bitsForPaletteSize(paletteSize);
        out.writeByte((bits << 1) | 0);
        BedrockVarInt.writeUnsignedVarInt(out, paletteSize);
        for (int value : paletteIndex.keySet()) {
            BedrockVarInt.writeUnsignedVarInt(out, value);
        }
        if (bits == 0) {
            return;
        }
        int perWord = 32 / bits;
        int wordCount = (entryCount + perWord - 1) / perWord;
        BedrockVarInt.writeUnsignedVarInt(out, wordCount);
        long mask = (1L << bits) - 1;
        for (int word = 0; word < wordCount; word++) {
            long packed = 0;
            for (int slot = 0; slot < perWord; slot++) {
                int bedrockIndex = word * perWord + slot;
                if (bedrockIndex >= entryCount) {
                    break;
                }
                int javaIndex = bedrockToJavaIndex(bedrockIndex, entryCount);
                int value = javaOrderValues[javaIndex];
                int paletteEntry = paletteIndex.get(value);
                packed |= ((long) paletteEntry & mask) << (slot * bits);
            }
            out.writeLittleInt((int) packed);
        }
    }

    /**
     * Bedrock のインデックス（XZY 順：Y が最も速い）-> Java のインデックス。
     * ブロック（4096）: Bedrock {@code y | z<<4 | x<<8}、Java {@code x | z<<4 | y<<8}。
     * バイオーム（64）: Bedrock {@code y | z<<2 | x<<4}、Java {@code x | z<<2 | y<<4}。
     */
    static int bedrockToJavaIndex(int bedrockIndex, int entryCount) {
        if (entryCount == SECTION_BLOCKS) {
            int y = bedrockIndex & 15;
            int z = (bedrockIndex >> 4) & 15;
            int x = (bedrockIndex >> 8) & 15;
            return x | (z << 4) | (y << 8);
        }
        int y = bedrockIndex & 3;
        int z = (bedrockIndex >> 2) & 3;
        int x = (bedrockIndex >> 4) & 3;
        return x | (z << 2) | (y << 4);
    }

    /** パレットの大きさに必要なビット数（0／1／2／3／4／5／6／8／16 のいずれか）。 */
    static int bitsForPaletteSize(int paletteSize) {
        if (paletteSize <= 1) {
            return 0;
        }
        if (paletteSize <= 2) {
            return 1;
        }
        if (paletteSize <= 4) {
            return 2;
        }
        if (paletteSize <= 8) {
            return 3;
        }
        if (paletteSize <= 16) {
            return 4;
        }
        if (paletteSize <= 32) {
            return 5;
        }
        if (paletteSize <= 64) {
            return 6;
        }
        if (paletteSize <= 256) {
            return 8;
        }
        return 16;
    }

    /** 763→765 の逆引き表（765→763 表の初出を採用）。 */
    private static int[] buildInverse763() {
        int max765 = RegistryRemapper.MAX_BLOCK_STATE_765;
        int[] inverse = new int[max765 + 1];
        Arrays.fill(inverse, -1);
        for (int id765 = 0; id765 <= max765; id765++) {
            int id763 = RegistryRemapper.mapBlockState(id765);
            if (id763 >= 0 && id763 <= max765 && inverse[id763] < 0) {
                inverse[id763] = id765;
            }
        }
        return inverse;
    }
}
