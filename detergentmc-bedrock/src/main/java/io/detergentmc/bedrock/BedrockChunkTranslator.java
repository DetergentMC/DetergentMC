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
     * 771 は 771→765 表、772 は 772→765 表、773 は 773→765 表、774 は 774→765 表、775 は 775→765 表、776 は 776→765 表、763 は
     * 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。
     */
    public static int normalizeBlockState(int stateId, int serverProtocol) {
        return switch (serverProtocol) {
            case RegistryRemapper.PROTOCOL_47 -> {
                // 47 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。47 の値は (数値ID<<4)|
                // メタデータで、意味対応表（mapBlockState47To758）が担う。
                int s = RegistryRemapper.mapBlockState47To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_5, RegistryRemapper.PROTOCOL_4 -> {
                // 5 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。5 の値は (数値ID<<4)|
                // メタデータで、意味対応表（mapBlockState5To758）が担う。
                int s = RegistryRemapper.mapBlockState5To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_107, RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_110, RegistryRemapper.PROTOCOL_210, RegistryRemapper.PROTOCOL_315, RegistryRemapper.PROTOCOL_316, RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_338, RegistryRemapper.PROTOCOL_340 -> {
                // 340 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。340 の値は (数値ID<<4)|
                // メタデータで、意味対応表（mapBlockState340To758）が担う。
                int s = RegistryRemapper.mapBlockState340To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_393 -> {
                // 393 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。393 のレジストリは 404 と
                // 異なる（サンゴまわり）ため、専用表を使う。
                int s = RegistryRemapper.mapBlockState393To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_404, 401 -> {
                // 404 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState404To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_477 -> {
                // 477 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState477To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_480 -> {
                // 480 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState480To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_485 -> {
                // 485 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState485To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_498 -> {
                // 498 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState498To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_490 -> {
                // 490 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState490To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_573 -> {
                // 573 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState573To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_575 -> {
                // 575 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState575To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
            case RegistryRemapper.PROTOCOL_578 -> {
                // 578 → 758 → 759 → 760 → 761 → 762 → 763 と段階表を合成し、
                // 最後に 763 の逆引きで 765 へ。
                int s = RegistryRemapper.mapBlockState578To758(stateId);
                s = RegistryRemapper.mapBlockState758To759(s);
                s = RegistryRemapper.mapBlockState759To760(s);
                s = RegistryRemapper.mapBlockState760To761(s);
                s = RegistryRemapper.mapBlockState761To762(s);
                s = RegistryRemapper.mapBlockState762To763(s);
                if (s < 0 || s >= BLOCK_STATE_763_TO_765.length) {
                    yield 1;
                }
                int mapped = BLOCK_STATE_763_TO_765[s];
                yield mapped < 0 ? 1 : mapped;
            }
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
            case RegistryRemapper.PROTOCOL_776 -> RegistryRemapper.mapBlockState776To765(stateId);
            default -> stateId;
        };
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
     */
    public static TranslatedChunk translate(ChunkInput input) {
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_5
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_4) {
            // 5 のチャンクは zlib 圧縮＋種類別グループ化レイアウトで
            // 47 とも別形式。47 形式へ詰め替えてから 47 の経路へ流す
            // （値は (数値ID<<4)|メタデータで 47 と同じ番号空間）。
            return translate5(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_47) {
            // 47 のチャンクはパレット無しの固定詰め（2バイトLE の
            // (数値ID<<4)|メタデータ ×4096＋光 2048×2）、bitMap は
            // u16、バイオームは末尾の u8×256。パースは専用にする。
            return translate47(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_340
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_338
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_335
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_316
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_315
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_110
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_109
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_108
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_107) {
            // 340／338 のチャンクは骨格が 404 と同じだが、パレット値は
            // (数値ID<<4)|メタデータ、直接パレットは13ビット、
            // バイオームは末尾の u8×256。パースは専用にする
            // （338 のチャンク形式は 340 と完全同一）。
            return translate340(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_393) {
            // 393 のチャンク形式は 404 と同一（パースは共用できる）。
            // ブロック状態の正規化は writeSubChunk が
            // input.serverProtocol() で振り分けるため、393 専用表が使われる。
            return translate404(input);
        }
        if (input.serverProtocol() == 401) {
            // 401 のチャンク形式は 404 と同一（スロット形式だけが違う
            // 世代で、チャンクにスロットは含まれない）。
            return translate404(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_404) {
            // 404 のチャンク形式は 498 と別世代（heightmap 無し、光は
            // セクション内蔵、詰め方はロング境界をまたがない旧方式、
            // バイオームはバッファ末尾の 256 件 i32）。
            return translate404(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_477) {
            // チャンク形式は 480 と同一（バイオームは末尾の256件）。
            return translate498(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_480) {
            // チャンク形式は 485 と同一（バイオームは末尾の256件）。
            return translate498(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_485) {
            // チャンク形式は 490 と同一（バイオームは末尾の256件）。
            return translate498(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_490) {
            // チャンク形式は 498 と同一（バイオームは末尾の256件）。
            return translate498(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_498) {
            return translate498(input);
        }
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_578
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_575
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_573) {
            return translate578(input);
        }
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_763) {
            reader.readNamedNbt();
        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_770
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_771
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_772
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_773
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_774
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_775
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_776) {
            // 770／771／772／773／774／775／776 の heightmap は NBT ではなく配列（varint 個数 ×
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
        for (int i = 0; i < sectionCount && sections.remaining() > 0; i++) {
            int blockCount = sections.readShort(); // blockCount（使わない）
            if (input.serverProtocol() == RegistryRemapper.PROTOCOL_775
                    || input.serverProtocol() == RegistryRemapper.PROTOCOL_776) {
                // 26.1（775）／26.2（776）では blockCount の後に 2 バイトの追加
                // フィールドがある（実バニラ 26.1／26.2 のチャンクで確認。値は
                // 変わる：26.2 のセクション 0 で 14。内容は未確認のため読み飛ばす。
                // 770〜774 には無い）。
                sections.readShort();
            }
            blockSections.add(readPalettedContainer(sections, true,
                    input.serverProtocol()));
            biomeSections.add(readPalettedContainer(sections, false,
                    input.serverProtocol()));
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

    /**
     * 578（1.15.2）のチャンクを読む。形式は 758 以降と別世代：x, z, groundUp,
     * セクションビットマスク（varint）, heightmaps（名前付き NBT）, バイオーム
     * 1024 個の int 配列（groundUp のとき）, データ長, セクション列（マスクの
     * ビット順）, ブロックエンティティ NBT 列。セクションのビット詰めは
     * ロング境界をまたぐ旧方式（実バニラ 1.15.2 の heightmap 36 ロングで確認）。
     * セクションは常に 16 個（高さ 256、minY 0）。ブロック状態は 578 の ID の
     * まま保持し、書き出し時に {@link #normalizeBlockState} で 765 へ寄せる。
     */
    /**
     * 498 の Chunk Data を読む。578 との違い：バイオームは別フィールド
     * （1024件）ではなく、データバッファの末尾に列ごと256件が入る。
     * セクションの読み方は 578 と同じ（境界またぎ詰め）。
     */
    /**
     * 404（1.13.2）のチャンクを読む。498 との違い：heightmaps NBT が無く、
     * セクションがブロックライト 2048 バイト（＋スカイライト 2048 バイト、
     * 空光のある次元のみ）を内蔵し、ビット詰めはロング境界をまたがない
     * 旧方式。バイオームはデータバッファ末尾の 256 件 i32（498 と同じ位置）。
     * スカイライトの有無は次元で決まるが入力に無いため、あり→なしの順に
     * 試し、末尾の残量がバイオーム分と一致した方を採用する。
     */
    private static TranslatedChunk translate404(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        boolean groundUp = reader.readBoolean();
        int mask = reader.readVarInt();
        int size = reader.readVarInt();
        if (size < 0 || size > reader.remaining()) {
            throw new IllegalArgumentException("404 チャンクのデータ長が不正です: " + size);
        }
        byte[] buffer = reader.readBytes(size);
        List<int[]> blockSections = null;
        int[] biomes = null;
        for (boolean sky : new boolean[]{true, false}) {
            try {
                PacketIO.Reader sections = new PacketIO.Reader(buffer);
                List<int[]> parsed = new ArrayList<>(16);
                for (int s = 0; s < 16; s++) {
                    if ((mask & (1 << s)) == 0) {
                        parsed.add(airBlocks());
                        continue;
                    }
                    // 404 のセクションに blockCount は無い（1.14 から追加。実バニラで確認済み）。
                    int bits = sections.readUnsignedByte();
                    int[] palette = null;
                    if (bits <= 8) {
                        int paletteLength = sections.readVarInt();
                        palette = new int[paletteLength];
                        for (int i = 0; i < paletteLength; i++) {
                            palette[i] = sections.readVarInt();
                        }
                    }
                    int dataLength = sections.readVarInt();
                    long[] longs = new long[dataLength];
                    for (int i = 0; i < dataLength; i++) {
                        longs[i] = sections.readLong();
                    }
                    sections.readBytes(2048); // ブロックライト（Bedrock 変換では使わない）
                    if (sky) {
                        sections.readBytes(2048); // スカイライト
                    }
                    int[] states = new int[SECTION_BLOCKS];
                    if (bits == 0) {
                        int only = palette != null && palette.length > 0 ? palette[0] : 0;
                        java.util.Arrays.fill(states, only);
                    } else {
                        // 詰め方は 1.15 以前の旧式 BitArray（long 境界をまたぐ
                        // crossing）。実バニラ 1.13.2 の bits=5 セクション
                        // （320 long＝4096×5/64）で確認した。
                        int valueBits = palette != null ? bits : 14;
                        long valueMask = (1L << valueBits) - 1;
                        int bitPos = 0;
                        for (int i = 0; i < SECTION_BLOCKS; i++) {
                            int li = bitPos >> 6;
                            int off = bitPos & 63;
                            long raw = (longs[li] >>> off) & valueMask;
                            if (off + valueBits > 64 && li + 1 < longs.length) {
                                raw |= (longs[li + 1] << (64 - off)) & valueMask;
                            }
                            int rawInt = (int) (raw & valueMask);
                            states[i] = palette != null
                                    ? (rawInt < palette.length ? palette[rawInt] : 0) : rawInt;
                            bitPos += valueBits;
                        }
                    }
                    parsed.add(states);
                }
                int[] parsedBiomes = null;
                if (groundUp) {
                    if (sections.remaining() != 1024) {
                        throw new IllegalArgumentException("404 チャンクの末尾が合いません");
                    }
                    parsedBiomes = new int[256];
                    for (int i = 0; i < 256; i++) {
                        parsedBiomes[i] = sections.readInt();
                    }
                } else if (sections.remaining() != 0) {
                    throw new IllegalArgumentException("404 チャンクの末尾が合いません");
                }
                blockSections = parsed;
                biomes = parsedBiomes;
                break;
            } catch (RuntimeException e) {
                if (!sky) {
                    throw e;
                }
            }
        }
        List<int[]> biomeSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            int[] biomeSection = new int[SECTION_BIOMES];
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        int value = biomes == null ? 1
                                : biomes[(qz * 4) * 16 + qx * 4];
                        biomeSection[(qy << 4) | (qz << 2) | qx] = value;
                    }
                }
            }
            biomeSections.add(biomeSection);
        }
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        for (int i = 0; i < 16; i++) {
            writeSubChunk(out, blockSections.get(i), i, input.serverProtocol());
        }
        for (int i = 0; i < 16; i++) {
            writeBiomeStorage(out, biomeSections.get(i), input.biomeNames());
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        return new TranslatedChunk(chunkX, chunkZ, out.bytes(), 16);
    }

    /**
     * 47（1.8.x）のチャンクを読む。セクションは固定長 12,288 バイト
     * （ブロック 4096×u16 LE＋ブロックライト 2048＋スカイライト 2048）。
     * スカイライトの有無は次元依存でペイロードからは分からないため、
     * 340 と同じく有り→無しの2回試行で確定させる。
     */
    /**
     * 5（1.7.x）のチャンクを読む。ペイロードは
     * {chunkX, chunkZ, groundUp, primaryBitMap u16, addBitMap u16,
     * compressedSize varint, zlib データ} で、展開後は種類別に
     * グループ化されている（全セクションの ID 配列→メタ nibble→
     * ブロックライト→スカイライト→add nibble→バイオーム）。
     * 47 の固定詰めレイアウトへ詰め替えた合成ペイロードを作り、
     * translate47 へ委譲する（ブロック値は 47 と同じ番号空間）。
     * スカイライトの有無は展開後の長さから厳密に決める。
     */
    private static TranslatedChunk translate5(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        boolean groundUp = reader.readBoolean();
        int mask = reader.readUnsignedShort();
        reader.readUnsignedShort(); // addBitMap（ID は最大175で実質0）
        int compressedSize = reader.readVarInt();
        if (compressedSize < 0 || compressedSize > reader.remaining()) {
            throw new IllegalArgumentException("5 チャンクの圧縮長が不正です: " + compressedSize);
        }
        byte[] compressed = reader.readBytes(compressedSize);
        byte[] data;
        try {
            java.util.zip.Inflater inflater = new java.util.zip.Inflater();
            inflater.setInput(compressed);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(tmp);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("5 チャンクの zlib が途中で終わりました");
                }
                bos.write(tmp, 0, n);
            }
            inflater.end();
            data = bos.toByteArray();
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalArgumentException("5 チャンクの zlib 展開に失敗しました", e);
        }
        int sections = Integer.bitCount(mask);
        int biomeBytes = groundUp ? 256 : 0;
        boolean sky;
        if (data.length == sections * 12288 + biomeBytes) {
            sky = true;
        } else if (data.length == sections * 10240 + biomeBytes) {
            sky = false;
        } else {
            throw new IllegalArgumentException("5 チャンクの展開長が合いません: " + data.length);
        }
        int idsOff = 0;
        int metaOff = sections * 4096;
        int blockLightOff = metaOff + sections * 2048;
        int skyLightOff = blockLightOff + sections * 2048;
        int addOff = skyLightOff + (sky ? sections * 2048 : 0);
        PacketIO.Writer out = new PacketIO.Writer();
        int sectionIndex = 0;
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        for (int sec = 0; sec < 16; sec++) {
            if ((mask & (1 << sec)) == 0) {
                continue;
            }
            int s = sectionIndex++;
            for (int i = 0; i < 4096; i++) {
                int id = data[idsOff + s * 4096 + i] & 0xFF;
                int metaByte = data[metaOff + s * 2048 + (i >> 1)] & 0xFF;
                int meta = (i & 1) == 0 ? metaByte & 0xF : (metaByte >> 4) & 0xF;
                int addByte = data[addOff + s * 2048 + (i >> 1)] & 0xFF;
                int add = (i & 1) == 0 ? addByte & 0xF : (addByte >> 4) & 0xF;
                int value = ((id | (add << 8)) << 4) | meta;
                body.write(value & 0xFF);
                body.write((value >> 8) & 0xFF);
            }
            body.write(data, blockLightOff + s * 2048, 2048);
            if (sky) {
                body.write(data, skyLightOff + s * 2048, 2048);
            }
        }
        if (groundUp) {
            body.write(data, addOff + sections * 2048, 256);
        }
        byte[] bodyBytes = body.toByteArray();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        out.writeBoolean(groundUp);
        out.writeShort((short) mask);
        out.writeVarInt(bodyBytes.length);
        out.writeBytes(bodyBytes);
        return translate47(new ChunkInput(chunkX, chunkZ, out.bytes(),
                RegistryRemapper.PROTOCOL_47, input.minY(), input.height(),
                input.biomeNames()));
    }

    private static TranslatedChunk translate47(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        boolean groundUp = reader.readBoolean();
        int mask = reader.readUnsignedShort();
        int size = reader.readVarInt();
        if (size < 0 || size > reader.remaining()) {
            throw new IllegalArgumentException("47 チャンクのデータ長が不正です: " + size);
        }
        byte[] buffer = reader.readBytes(size);
        List<int[]> blockSections = null;
        int[] biomes = null;
        for (boolean sky : new boolean[]{true, false}) {
            try {
                PacketIO.Reader sections = new PacketIO.Reader(buffer);
                List<int[]> parsed = new ArrayList<>(16);
                for (int s = 0; s < 16; s++) {
                    if ((mask & (1 << s)) == 0) {
                        parsed.add(airBlocks());
                        continue;
                    }
                    int[] states = new int[SECTION_BLOCKS];
                    for (int i = 0; i < SECTION_BLOCKS; i++) {
                        int lo = sections.readUnsignedByte();
                        int hi = sections.readUnsignedByte();
                        states[i] = lo | (hi << 8);
                    }
                    sections.readBytes(2048); // ブロックライト（使わない）
                    if (sky) {
                        sections.readBytes(2048); // スカイライト
                    }
                    parsed.add(states);
                }
                int[] parsedBiomes = null;
                if (groundUp) {
                    if (sections.remaining() < 256) {
                        throw new IllegalArgumentException("47 チャンクの末尾が合いません");
                    }
                    parsedBiomes = new int[256];
                    for (int i = 0; i < 256; i++) {
                        parsedBiomes[i] = sections.readUnsignedByte();
                    }
                }
                if (sections.remaining() != 0) {
                    throw new IllegalArgumentException("47 チャンクの末尾が合いません");
                }
                blockSections = parsed;
                biomes = parsedBiomes;
                break;
            } catch (RuntimeException e) {
                if (!sky) {
                    throw e;
                }
            }
        }
        List<int[]> biomeSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            int[] biomeSection = new int[SECTION_BIOMES];
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        int value = biomes == null ? 1
                                : biomes[(qz * 4) * 16 + qx * 4];
                        biomeSection[(qy << 4) | (qz << 2) | qx] = value;
                    }
                }
            }
            biomeSections.add(biomeSection);
        }
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        for (int i = 0; i < 16; i++) {
            writeSubChunk(out, blockSections.get(i), i, input.serverProtocol());
        }
        for (int i = 0; i < 16; i++) {
            writeBiomeStorage(out, biomeSections.get(i), input.biomeNames());
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        return new TranslatedChunk(chunkX, chunkZ, out.bytes(), 16);
    }

    /**
     * セクション本体の後ろに残ったバイトを検証する。通常は0バイトで
     * なければならないが、107（1.9）のチャンクは dataLen の内側に
     * 大きなゼロ埋めを含む癖があるため（Stage A 実測：153,600バイト
     * 等が全部ゼロ）、107 の場合だけ「残りが全部ゼロ」を条件に
     * 読み捨てる。他プロトコルの末尾検証は従来どおり厳密に行う。
     */
    private static void requireZeroPaddedTail(PacketIO.Reader sections, ChunkInput input) {
        if (sections.remaining() == 0) {
            return;
        }
        if (input.serverProtocol() != RegistryRemapper.PROTOCOL_107) {
            throw new IllegalArgumentException("340 チャンクの末尾が合いません");
        }
        while (sections.remaining() > 0) {
            if (sections.readUnsignedByte() != 0) {
                throw new IllegalArgumentException("107 チャンクの末尾のゼロ埋めが崩れています");
            }
        }
    }

    private static TranslatedChunk translate340(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        boolean groundUp = reader.readBoolean();
        int mask = reader.readVarInt();
        int size = reader.readVarInt();
        if (size < 0 || size > reader.remaining()) {
            throw new IllegalArgumentException("340 チャンクのデータ長が不正です: " + size);
        }
        byte[] buffer = reader.readBytes(size);
        List<int[]> blockSections = null;
        int[] biomes = null;
        for (boolean sky : new boolean[]{true, false}) {
            try {
                PacketIO.Reader sections = new PacketIO.Reader(buffer);
                List<int[]> parsed = new ArrayList<>(16);
                for (int s = 0; s < 16; s++) {
                    if ((mask & (1 << s)) == 0) {
                        parsed.add(airBlocks());
                        continue;
                    }
                    // 340 のセクションに blockCount は無い（404 と同じ）。
                    int bits = sections.readUnsignedByte();
                    int[] palette = null;
                    if (bits <= 8) {
                        int paletteLength = sections.readVarInt();
                        palette = new int[paletteLength];
                        for (int i = 0; i < paletteLength; i++) {
                            palette[i] = sections.readVarInt();
                        }
                    }
                    int dataLength = sections.readVarInt();
                    long[] longs = new long[dataLength];
                    for (int i = 0; i < dataLength; i++) {
                        longs[i] = sections.readLong();
                    }
                    sections.readBytes(2048); // ブロックライト（Bedrock 変換では使わない）
                    if (sky) {
                        sections.readBytes(2048); // スカイライト
                    }
                    int[] states = new int[SECTION_BLOCKS];
                    if (bits == 0) {
                        int only = palette != null && palette.length > 0 ? palette[0] : 0;
                        java.util.Arrays.fill(states, only);
                    } else {
                        // 詰め方は 1.15 以前の旧式 BitArray（long 境界をまたぐ
                        // crossing）。実バニラ 1.13.2 の bits=5 セクション
                        // （320 long＝4096×5/64）で確認した。
                        int valueBits = palette != null ? bits : 13;
                        long valueMask = (1L << valueBits) - 1;
                        int bitPos = 0;
                        for (int i = 0; i < SECTION_BLOCKS; i++) {
                            int li = bitPos >> 6;
                            int off = bitPos & 63;
                            long raw = (longs[li] >>> off) & valueMask;
                            if (off + valueBits > 64 && li + 1 < longs.length) {
                                raw |= (longs[li + 1] << (64 - off)) & valueMask;
                            }
                            int rawInt = (int) (raw & valueMask);
                            states[i] = palette != null
                                    ? (rawInt < palette.length ? palette[rawInt] : 0) : rawInt;
                            bitPos += valueBits;
                        }
                    }
                    parsed.add(states);
                }
                int[] parsedBiomes = null;
                if (groundUp) {
                    if (sections.remaining() < 256) {
                        throw new IllegalArgumentException("340 チャンクの末尾が合いません");
                    }
                    parsedBiomes = new int[256];
                    for (int i = 0; i < 256; i++) {
                        parsedBiomes[i] = sections.readUnsignedByte();
                    }
                    requireZeroPaddedTail(sections, input);
                } else {
                    requireZeroPaddedTail(sections, input);
                }
                blockSections = parsed;
                biomes = parsedBiomes;
                break;
            } catch (RuntimeException e) {
                if (!sky) {
                    throw e;
                }
            }
        }
        List<int[]> biomeSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            int[] biomeSection = new int[SECTION_BIOMES];
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        int value = biomes == null ? 1
                                : biomes[(qz * 4) * 16 + qx * 4];
                        biomeSection[(qy << 4) | (qz << 2) | qx] = value;
                    }
                }
            }
            biomeSections.add(biomeSection);
        }
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        for (int i = 0; i < 16; i++) {
            writeSubChunk(out, blockSections.get(i), i, input.serverProtocol());
        }
        for (int i = 0; i < 16; i++) {
            writeBiomeStorage(out, biomeSections.get(i), input.biomeNames());
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        return new TranslatedChunk(chunkX, chunkZ, out.bytes(), 16);
    }

    private static TranslatedChunk translate498(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        boolean groundUp = reader.readBoolean();
        int mask = reader.readVarInt();
        reader.readNamedNbt(); // heightmaps（Bedrock 変換では使わない）
        int size = reader.readVarInt();
        if (size < 0 || size > reader.remaining()) {
            throw new IllegalArgumentException("498 チャンクのデータ長が不正です: " + size);
        }
        PacketIO.Reader sections = new PacketIO.Reader(reader.readBytes(size));
        List<int[]> blockSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            if ((mask & (1 << s)) == 0) {
                blockSections.add(airBlocks());
            } else {
                sections.readShort(); // blockCount（使わない）
                int bits = sections.readUnsignedByte();
                int[] palette = null;
                if (bits <= 8) {
                    int paletteLength = sections.readVarInt();
                    palette = new int[paletteLength];
                    for (int i = 0; i < paletteLength; i++) {
                        palette[i] = sections.readVarInt();
                    }
                }
                int dataLength = sections.readVarInt();
                long[] longs = new long[dataLength];
                for (int i = 0; i < dataLength; i++) {
                    longs[i] = sections.readLong();
                }
                int[] states = new int[SECTION_BLOCKS];
                if (bits == 0) {
                    int only = palette != null && palette.length > 0 ? palette[0] : 0;
                    java.util.Arrays.fill(states, only);
                } else {
                    int valueBits = palette != null ? bits : 14;
                    for (int i = 0; i < SECTION_BLOCKS; i++) {
                        long bitPos = (long) i * valueBits;
                        int li = (int) (bitPos >>> 6);
                        int off = (int) (bitPos & 63);
                        long v;
                        if (off + valueBits <= 64) {
                            v = (longs[li] >>> off) & ((1L << valueBits) - 1);
                        } else {
                            int rem = 64 - off;
                            v = ((longs[li] >>> off) | (longs[li + 1] << rem))
                                    & ((1L << valueBits) - 1);
                        }
                        int raw = (int) v;
                        states[i] = palette != null
                                ? (raw < palette.length ? palette[raw] : 0) : raw;
                    }
                }
                blockSections.add(states);
            }
        }
        int[] biomes = null;
        if (groundUp) {
            biomes = new int[256];
            for (int i = 0; i < 256; i++) {
                biomes[i] = sections.readInt();
            }
        }
        List<int[]> biomeSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            int[] biomeSection = new int[SECTION_BIOMES];
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        int value = biomes == null ? 1
                                : biomes[(qz * 4) * 16 + qx * 4];
                        biomeSection[(qy << 4) | (qz << 2) | qx] = value;
                    }
                }
            }
            biomeSections.add(biomeSection);
        }
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        for (int i = 0; i < 16; i++) {
            writeSubChunk(out, blockSections.get(i), i, input.serverProtocol());
        }
        for (int i = 0; i < 16; i++) {
            writeBiomeStorage(out, biomeSections.get(i), input.biomeNames());
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        return new TranslatedChunk(chunkX, chunkZ, out.bytes(), 16);
    }

    private static TranslatedChunk translate578(ChunkInput input) {
        PacketIO.Reader reader = new PacketIO.Reader(input.javaChunkPayload());
        int chunkX = reader.readInt();
        int chunkZ = reader.readInt();
        boolean groundUp = reader.readBoolean();
        int mask = reader.readVarInt();
        reader.readNamedNbt(); // heightmaps（Bedrock 変換では使わない）
        int[] biomes = null;
        if (groundUp) {
            biomes = new int[1024];
            for (int i = 0; i < 1024; i++) {
                biomes[i] = reader.readInt();
            }
        }
        int size = reader.readVarInt();
        if (size < 0 || size > reader.remaining()) {
            throw new IllegalArgumentException("578 チャンクのデータ長が不正です: " + size);
        }
        PacketIO.Reader sections = new PacketIO.Reader(reader.readBytes(size));
        List<int[]> blockSections = new ArrayList<>(16);
        List<int[]> biomeSections = new ArrayList<>(16);
        for (int s = 0; s < 16; s++) {
            if ((mask & (1 << s)) == 0) {
                blockSections.add(airBlocks());
            } else {
                sections.readShort(); // blockCount（使わない）
                int bits = sections.readUnsignedByte();
                int[] palette = null;
                if (bits <= 8) {
                    int paletteLength = sections.readVarInt();
                    palette = new int[paletteLength];
                    for (int i = 0; i < paletteLength; i++) {
                        palette[i] = sections.readVarInt();
                    }
                }
                int dataLength = sections.readVarInt();
                long[] longs = new long[dataLength];
                for (int i = 0; i < dataLength; i++) {
                    longs[i] = sections.readLong();
                }
                int[] states = new int[SECTION_BLOCKS];
                if (bits == 0) {
                    int only = palette != null && palette.length > 0 ? palette[0] : 0;
                    java.util.Arrays.fill(states, only);
                } else {
                    int valueBits = palette != null ? bits : 14;
                    for (int i = 0; i < SECTION_BLOCKS; i++) {
                        long bitPos = (long) i * valueBits;
                        int li = (int) (bitPos >>> 6);
                        int off = (int) (bitPos & 63);
                        long v;
                        if (off + valueBits <= 64) {
                            v = (longs[li] >>> off) & ((1L << valueBits) - 1);
                        } else {
                            int rem = 64 - off;
                            v = ((longs[li] >>> off) | (longs[li + 1] << rem))
                                    & ((1L << valueBits) - 1);
                        }
                        int raw = (int) v;
                        states[i] = palette != null
                                ? (raw < palette.length ? palette[raw] : 0) : raw;
                    }
                }
                blockSections.add(states);
            }
            int[] biomeSection = new int[SECTION_BIOMES];
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        int value = biomes == null ? 1
                                : biomes[((s * 4 + qy) << 4) | (qz << 2) | qx];
                        biomeSection[(qy << 4) | (qz << 2) | qx] = value;
                    }
                }
            }
            biomeSections.add(biomeSection);
        }
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        for (int i = 0; i < 16; i++) {
            writeSubChunk(out, blockSections.get(i), i, input.serverProtocol());
        }
        for (int i = 0; i < 16; i++) {
            writeBiomeStorage(out, biomeSections.get(i), input.biomeNames());
        }
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        return new TranslatedChunk(chunkX, chunkZ, out.bytes(), 16);
    }

    private static int[] airBlocks() {
        int[] blocks = new int[SECTION_BLOCKS];
        Arrays.fill(blocks, BedrockBlockRuntimeIds.AIR_RUNTIME_ID);
        return blocks;
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
                    && serverProtocol != RegistryRemapper.PROTOCOL_775
                    && serverProtocol != RegistryRemapper.PROTOCOL_776) {
                int dataLength = reader.readVarInt();
                if (dataLength != 0) {
                    throw new IllegalArgumentException("シングルパレットの Data Array 長が 0 ではありません。");
                }
            }
            // 770／771／772／773／774／775／776では Data Array の長さ接頭辞が無いため、シングルパレットは
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
        // バニラの BitStorage は 1 long あたり 64 / bits 個のエントリを詰め、
        // long の境界をまたがない。long 数は
        //   oldExpected = (entryCount + perLong - 1) / perLong
        // （perLong = 64 / bitsPerEntry）である。
        // 2026-11-14 に「連続詰め（(entryCount * bits + 63) / 64）」に変えたが、
        // それは誤診だった。実バニラ 1.20.1（763）のワイヤを測るとブロック
        // コンテナ b=5 で dataLength=342（=旧式。連続詰めなら 320）であり、
        // 26.1 のバイオーム観測（3 ビットで 4 long 読んだ）は、ブロック
        // コンテナを 320 long しか読まず 22 long 足りなかったために
        // バイオームがずれて見えただけだった（2026-11-15、Bedrock マトリクス
        // 15 件中 9 件失敗の調査で判明。770〜775 も旧式でなければ通らない）。
        // 接頭辞あり（758〜769）ではワイヤの長さを見て両方式に対応する。
        int perLong = 64 / bitsPerEntry;
        int oldExpected = (entryCount + perLong - 1) / perLong;
        int newExpected = (entryCount * bitsPerEntry + 63) / 64;
        int dataLength;
        boolean crossBoundary;
        if (serverProtocol == RegistryRemapper.PROTOCOL_770
                || serverProtocol == RegistryRemapper.PROTOCOL_771
                || serverProtocol == RegistryRemapper.PROTOCOL_772
                || serverProtocol == RegistryRemapper.PROTOCOL_773
                || serverProtocol == RegistryRemapper.PROTOCOL_774
                || serverProtocol == RegistryRemapper.PROTOCOL_775
                || serverProtocol == RegistryRemapper.PROTOCOL_776) {
            // 1.21.5（770）／1.21.6（771）／1.21.7／1.21.8（772）／1.21.9／1.21.10（773）／1.21.11（774）／26.1（775）／26.2（776）ではパレット付きコンテナの Data Array に長さの
            // varint 接頭辞が無い。長さは bitsPerEntry とエントリ数から計算する。
            // （実バニラ1.21.5のチャンクで確認：長さ varint を読むと 0x33=51 が
            //  dataLength になってしまい、その後のバイオームが壊れる。）
            dataLength = oldExpected;
            crossBoundary = false;
        } else {
            dataLength = reader.readVarInt();
            if (dataLength == oldExpected) {
                crossBoundary = false;
            } else if (dataLength == newExpected) {
                crossBoundary = true;
            } else {
                throw new IllegalArgumentException("Data Array 長が想定と違います: "
                        + dataLength + "（旧式=" + oldExpected
                        + "、連続詰め=" + newExpected + "）。");
            }
        }
        long[] longs = new long[dataLength];
        for (int i = 0; i < dataLength; i++) {
            longs[i] = reader.readLong();
        }
        int[] values = new int[entryCount];
        long mask = (1L << bitsPerEntry) - 1;
        if (crossBoundary) {
            // 連続詰め（long の境界をまたぐ）で読む。接頭辞ありでワイヤが
            // 新式の長さだった場合のみ。
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
        } else {
            // 旧式（1 long あたり perLong 個を先頭から読む）。実バニラの詰め方。
            for (int index = 0; index < entryCount; index++) {
                int cellIndex = index / perLong;
                int bitIndex = (index % perLong) * bitsPerEntry;
                int entry = (int) ((longs[cellIndex] >>> bitIndex) & mask);
                values[index] = indirect ? palette[entry] : entry;
            }
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

    private static boolean isNonStraddling(int serverProtocol) {
        return serverProtocol == RegistryRemapper.PROTOCOL_735
                || serverProtocol == RegistryRemapper.PROTOCOL_736
                || serverProtocol == RegistryRemapper.PROTOCOL_751
                || serverProtocol == RegistryRemapper.PROTOCOL_753
                || serverProtocol == RegistryRemapper.PROTOCOL_754
                || serverProtocol == RegistryRemapper.PROTOCOL_755
                || serverProtocol == RegistryRemapper.PROTOCOL_756
                || serverProtocol == RegistryRemapper.PROTOCOL_757;
    }

}
