package io.detergentmc.bedrock;

import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * テスト用の Java チャンクビルダー。既知の地形パターンを持つ
 * Chunk Data ペイロード（765 形式、763 は heightmaps が名前付き）を作る。
 * Bedrock 側のデコーダー（{@link BedrockLevelChunkDecoder}）もここに置く。
 */
final class TestJavaChunk {

    private TestJavaChunk() {
    }

    /** ブロック位置 (x, y, z) -> Java のブロック状態ID。 */
    @FunctionalInterface
    interface BlockAt {
        int state(int x, int y, int z);
    }

    /** バイオーム位置 (x, y, z) -> Java のバイオーム登録番号。 */
    @FunctionalInterface
    interface BiomeAt {
        int biome(int x, int y, int z);
    }

    /** 765 形式の Chunk Data を作る（オーバーワールド、minY -64、高さ 384）。 */
    static byte[] build765(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build765(chunkX, chunkZ, blocks, biomes, -64, 384, -1);
    }

    /**
     * 765 形式の Chunk Data を作る。{@code directSection} が 0 以上なら
     * そのセクションだけ直接パレット（BPE 15）にする（それ以外は間接）。
     */
    static byte[] build765(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int minY, int height, int directSection) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // heightmaps：名前なし NBT の空 compound（0x0A＋0x00）。
        out.writeByte(0x0A);
        out.writeByte(0x00);
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        int sectionCount = height / 16;
        for (int s = 0; s < sectionCount; s++) {
            int yBase = minY + s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir);
            boolean direct = s == directSection;
            writePalettedContainer(sections, blockValues, true, direct);

            int[] biomeValues = new int[64];
            for (int j = 0; j < 64; j++) {
                int x = j & 3;
                int z = (j >> 2) & 3;
                int yLocal = (j >> 4) & 3;
                biomeValues[j] = biomes.biome(x * 4, yBase + yLocal * 4, z * 4);
            }
            writePalettedContainer(sections, biomeValues, false, false);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    /** 757 形式の Chunk Data を作る（オーバーワールド、minY -64、高さ 384）。 */
    static byte[] build757(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build757(chunkX, chunkZ, blocks, biomes, -64, 384);
    }

    /**
     * 757 形式の Chunk Data を作る。765 との違いはパレット付きコンテナの詰め方だけ：
     * heightmap は NBT、セクションは short 1 個（blockCount）、Data Array には
     * 長さの varint 接頭辞がある（765 と同じ）が、BitStorage は long の境界を
     * またがない詰め（packPerLong。実バニラ 1.18 の SimpleBitStorage の
     * バイトコードで確認。2026-11-15）。BPE 5 のブロックでは 320 long ではなく
     * 342 long、バイオーム BPE 3 では 3 long ではなく 4 long になる。
     */
    static byte[] build757(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int minY, int height) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // heightmaps：名前なし NBT の空 compound（0x0A＋0x00）。757 は NBT 形式。
        out.writeByte(0x0A);
        out.writeByte(0x00);
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        int sectionCount = height / 16;
        for (int s = 0; s < sectionCount; s++) {
            int yBase = minY + s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir); // blockCount のみ（fluidCount は 1.18 には無い）
            writePalettedContainer757(sections, blockValues, true);

            int[] biomeValues = new int[64];
            for (int j = 0; j < 64; j++) {
                int x = j & 3;
                int z = (j >> 2) & 3;
                int yLocal = (j >> 4) & 3;
                biomeValues[j] = biomes.biome(x * 4, yBase + yLocal * 4, z * 4);
            }
            writePalettedContainer757(sections, biomeValues, false);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    /** 756 形式の Chunk Data を作る（オーバーワールド、minY 0、高さ 256）。全 16 セクションあり。 */
    static byte[] build756(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build756(chunkX, chunkZ, blocks, biomes, 0xFFFF);
    }

    /**
     * 756 形式の Chunk Data を作る。757 との違いは頭部とセクション列だけ：
     * 先頭に bitMap（varint 長＋long 列）、heightmaps は名前付き NBT、
     * バイオームはチャンク全体の int[1024]（index = (y&gt;&gt;2)*16 + (z&gt;&gt;2)*4 + (x&gt;&gt;2)）、
     * セクションは [blockCount i16]＋ブロックコンテナだけでバイオームコンテナ無し、
     * chunkData には sectionMask に立っているセクションだけが入る。
     * BitStorage は 757 と同じ non-straddling（packPerLong）＋長さ接頭辞あり。
     * 実バニラ 1.17.1 の生キャプチャ cap756-chunk-21-17.bin で確認（2026-11-16：
     * bitMap 1 long、heightmaps は名前付き、バイオーム 1024 件、BPE 5 の dataLen 342）。
     */
    static byte[] build756(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int sectionMask) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // bitMap：long 1 個。
        out.writeVarInt(1);
        out.writeLong(sectionMask & 0xFFFFL);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 0＋0x00）。
        out.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        // biomes：varint[1024]。index = yCell*16 + zCell*4 + xCell。
        out.writeVarInt(1024);
        for (int i = 0; i < 1024; i++) {
            int xCell = i & 3;
            int zCell = (i >> 2) & 3;
            int yCell = i >> 4;
            out.writeVarInt(biomes.biome(xCell * 4, yCell * 4, zCell * 4));
        }
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 16; s++) {
            if ((sectionMask & (1 << s)) == 0) {
                continue;
            }
            int yBase = s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir); // blockCount のみ（fluidCount は 1.17.1 には無い）
            writePalettedContainer757(sections, blockValues, true);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    /** 754 形式の Chunk Data を作る（オーバーワールド、minY 0、高さ 256）。全 16 セクションあり。 */
    static byte[] build754(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build754(chunkX, chunkZ, blocks, biomes, 0xFFFF);
    }

    /**
     * 754 形式の Chunk Data を作る。755／756 との違いは頭部だけ：
     * groundUp bool＋bitMap（varint の 16 ビット）＋名前付き heightmaps＋
     * groundUp のときだけバイオーム int[1024]＋masked 節のみ。
     * BitStorage は 755／756 と同じ non-straddling（packPerLong）＋長さ接頭辞あり。
     * 実バニラ 1.16.5 の生キャプチャ cap754-chunk-x-15-z-7.bin で確認（2026-11-16：
     * groundUp=true、bitMap 0x000f、biomes 1024 件全て 50、BPE 5 の dataLen 342）。
     */
    static byte[] build754(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int sectionMask) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // groundUp：全チャンク。
        out.writeBoolean(true);
        // bitMap：varint の 16 ビット。
        out.writeVarInt(sectionMask & 0xFFFF);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 0＋0x00）。
        out.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        // biomes：varint[1024]。index = yCell*16 + zCell*4 + xCell。
        out.writeVarInt(1024);
        for (int i = 0; i < 1024; i++) {
            int xCell = i & 3;
            int zCell = (i >> 2) & 3;
            int yCell = i >> 4;
            out.writeVarInt(biomes.biome(xCell * 4, yCell * 4, zCell * 4));
        }
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 16; s++) {
            if ((sectionMask & (1 << s)) == 0) {
                continue;
            }
            int yBase = s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir); // blockCount のみ（fluidCount は 1.16.5 には無い）
            writePalettedContainer757(sections, blockValues, true);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    /** 753 形式の Chunk Data を作る（オーバーワールド、minY 0、高さ 256）。全 16 セクションあり。 */
    static byte[] build753(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build753(chunkX, chunkZ, blocks, biomes, 0xFFFF);
    }

    /**
     * 753 形式の Chunk Data を作る。754 と完全同一の書き方（頭部は groundUp bool＋
     * bitMap（varint の 16 ビット）＋名前付き heightmaps＋groundUp のときだけ
     * バイオーム int[1024]＋masked 節のみ。BitStorage は non-straddling＋長さ接頭辞あり）。
     * 根拠は実測：mc-1.16.3-protocol.json は mc-1.16-protocol.json とバイト同一
     * （152,123 バイト）で packet_map_chunk も同一。レジストリ差分も 0 件
     * （Stage A §11.1・Stage B §14.4）。実バニラ 1.16.5 の生キャプチャ
     * cap754-chunk-x-15-z-7.bin は 753 形式のバイトでもある。
     */
    static byte[] build753(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int sectionMask) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // groundUp：全チャンク。
        out.writeBoolean(true);
        // bitMap：varint の 16 ビット。
        out.writeVarInt(sectionMask & 0xFFFF);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 0＋0x00）。
        out.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        // biomes：varint[1024]。index = yCell*16 + zCell*4 + xCell。
        out.writeVarInt(1024);
        for (int i = 0; i < 1024; i++) {
            int xCell = i & 3;
            int zCell = (i >> 2) & 3;
            int yCell = i >> 4;
            out.writeVarInt(biomes.biome(xCell * 4, yCell * 4, zCell * 4));
        }
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 16; s++) {
            if ((sectionMask & (1 << s)) == 0) {
                continue;
            }
            int yBase = s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir); // blockCount のみ（fluidCount は 1.16.3 には無い）
            writePalettedContainer757(sections, blockValues, true);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }


    /** 751 形式の Chunk Data を作る（オーバーワールド、minY 0、高さ 256）。全 16 セクションあり。 */
    static byte[] build751(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build751(chunkX, chunkZ, blocks, biomes, 0xFFFF);
    }

    /**
     * 751 形式の Chunk Data を作る。753 と完全同一の書き方（頭部は groundUp bool＋
     * bitMap（varint の 16 ビット）＋名前付き heightmaps＋groundUp のときだけ
     * バイオーム int[1024]＋masked 節のみ。BitStorage は non-straddling＋長さ接頭辞あり）。
     * 根拠は実測：mc-1.16.2-protocol.json は mc-1.16.3-protocol.json とバイト同一
     * （152,123 バイト）で packet_map_chunk も同一。レジストリ差分も 0 件
     * （Stage A §11.1・Stage B §14.4）。実バニラ 1.16.5 の生キャプチャ
     * cap754-chunk-x-15-z-7.bin は 751 形式のバイトでもある。
     */
    static byte[] build751(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int sectionMask) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // groundUp：全チャンク。
        out.writeBoolean(true);
        // bitMap：varint の 16 ビット。
        out.writeVarInt(sectionMask & 0xFFFF);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 0＋0x00）。
        out.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        // biomes：varint[1024]。index = yCell*16 + zCell*4 + xCell。
        out.writeVarInt(1024);
        for (int i = 0; i < 1024; i++) {
            int xCell = i & 3;
            int zCell = (i >> 2) & 3;
            int yCell = i >> 4;
            out.writeVarInt(biomes.biome(xCell * 4, yCell * 4, zCell * 4));
        }
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 16; s++) {
            if ((sectionMask & (1 << s)) == 0) {
                continue;
            }
            int yBase = s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir); // blockCount のみ（fluidCount は 1.16.2 には無い）
            writePalettedContainer757(sections, blockValues, true);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }


    /**
     * 736 形式の Chunk Data を作る。751 と頭部が違う（groundUp bool＋ignoreOldData bool＋
     * bitMap（varint の 16 ビット）＋名前付き heightmaps＋groundUp のときだけ
     * バイオーム i32[1024]（件数接頭辞なし）＋masked 節のみ。BitStorage は non-straddling＋長さ接頭辞あり）。
     * 根拠は実測：mc-1.16.1-protocol.json の packet_map_chunk（Stage B §14.4。
     * biomes は [count 1024][i32] の固定長で件数 varint は無い）、実バニラ 1.16.1 の
     * 生キャプチャ cap736-chunk-x--17-z--23.bin（ignoreOldData=true、bitMap 0x001f、
     * biomes 1024 件全て 1＝平原）、ChunkTranslator.translateChunkData736To751。
     * セクション内部は 751 と同じ [blockCount i16]＋ブロックコンテナ。
     */
    static byte[] build736(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build736(chunkX, chunkZ, blocks, biomes, 0xFFFF);
    }

    static byte[] build736(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int sectionMask) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // groundUp：全チャンク。
        out.writeBoolean(true);
        // ignoreOldData：全チャンク送出で true（751 には無い）。
        out.writeBoolean(true);
        // bitMap：varint の 16 ビット。
        out.writeVarInt(sectionMask & 0xFFFF);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 0＋0x00）。
        out.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        // biomes：i32[1024]（件数接頭辞なし。751 の [count varint][varint×1024] と違う）。
        // index = yCell*16 + zCell*4 + xCell。
        for (int i = 0; i < 1024; i++) {
            int xCell = i & 3;
            int zCell = (i >> 2) & 3;
            int yCell = i >> 4;
            out.writeInt(biomes.biome(xCell * 4, yCell * 4, zCell * 4));
        }
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 16; s++) {
            if ((sectionMask & (1 << s)) == 0) {
                continue;
            }
            int yBase = s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir); // blockCount のみ（fluidCount は 1.16.1 には無い）
            writePalettedContainer757(sections, blockValues, true);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }


    /**
     * 735 形式の Chunk Data を作る。736 と完全同一（Stage A §11.1・Stage B §14.4 実測。
     * 736⇔735 はパケットID・レジストリとも差分 0 件）のため build736 をそのまま使う。
     * 根拠は実測：実バニラ 1.16 の生キャプチャ cap735-chunk-x--3-z--15.bin
     * （ignoreOldData=true、bitMap 0x000f、biomes 1024 件全て 24＝deep_ocean）、
     * ChunkTranslator.translateChunkData735To736（恒等写像）。
     */
    static byte[] build735(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build736(chunkX, chunkZ, blocks, biomes);
    }

    static byte[] build735(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int sectionMask) {
        return build736(chunkX, chunkZ, blocks, biomes, sectionMask);
    }


    /** 755 形式の Chunk Data を作る（オーバーワールド、minY 0、高さ 256）。全 16 セクションあり。 */
    static byte[] build755(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build755(chunkX, chunkZ, blocks, biomes, 0xFFFF);
    }

    /**
     * 755 形式の Chunk Data を作る。756 と完全同一（Stage B §14.4 実測。
     * packet_map_chunk は protocol.json で完全一致し、実バニラ 1.17／1.17.1 の
     * Chunk Data クラス qw はフィールド同一で javap -c の差分は難読化クラス名の
     * 付け替えのみ。レジストリ差分も 0 件）。そのため build756 と同じ書き方で、
     * bitMap＋名前付き heightmaps＋バイオーム int[1024]＋masked 節のみ。
     * BitStorage は 756 と同じ non-straddling（packPerLong）＋長さ接頭辞あり。
     */
    static byte[] build755(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int sectionMask) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // bitMap：long 1 個。
        out.writeVarInt(1);
        out.writeLong(sectionMask & 0xFFFFL);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 0＋0x00）。
        out.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        // biomes：varint[1024]。index = yCell*16 + zCell*4 + xCell。
        out.writeVarInt(1024);
        for (int i = 0; i < 1024; i++) {
            int xCell = i & 3;
            int zCell = (i >> 2) & 3;
            int yCell = i >> 4;
            out.writeVarInt(biomes.biome(xCell * 4, yCell * 4, zCell * 4));
        }
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 16; s++) {
            if ((sectionMask & (1 << s)) == 0) {
                continue;
            }
            int yBase = s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir); // blockCount のみ（fluidCount は 1.17 には無い）
            writePalettedContainer757(sections, blockValues, true);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    /** 757 形式のパレット付きコンテナ 1 個を書く（長さ接頭辞あり＋packPerLong）。 */
    private static void writePalettedContainer757(ByteArrayOutputStream out, int[] values,
                                                  boolean blocks) {
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        for (int value : values) {
            palette.putIfAbsent(value, palette.size());
        }
        int entryCount = values.length;
        if (palette.size() == 1) {
            out.write(0); // BPE 0（シングル）
            writeVarInt(out, palette.keySet().iterator().next());
            writeVarInt(out, 0); // data array 長 0（1.18 は長さ接頭辞がある）
            return;
        }
        int bits = 1;
        while ((1 << bits) < palette.size()) {
            bits++;
        }
        out.write(bits);
        writeVarInt(out, palette.size());
        List<Integer> paletteList = new ArrayList<>(palette.keySet());
        for (int value : paletteList) {
            writeVarInt(out, value);
        }
        int[] indices = new int[entryCount];
        for (int i = 0; i < entryCount; i++) {
            indices[i] = palette.get(values[i]);
        }
        long[] packed = packPerLong(indices, bits);
        writeVarInt(out, packed.length);
        for (long value : packed) {
            writeLong(out, value);
        }
    }

    /**
     * エントリ列を SimpleBitStorage と同じく long の境界をまたがない詰めで
     * long 列にする（1 long あたり floor(64 / bits) 個。実バニラ 1.18 の
     * SimpleBitStorage.get のバイトコードで確認。2026-11-15）。
     * packContinuous（straddling）とは long 数が違う（BPE 5：320 long ではなく
     * 342 long。バイオーム BPE 3：3 long ではなく 4 long）。
     */
    private static long[] packPerLong(int[] entries, int bits) {
        int valuesPerLong = 64 / bits;
        long[] longs = new long[(entries.length + valuesPerLong - 1) / valuesPerLong];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < entries.length; i++) {
            int cellIndex = i / valuesPerLong;
            int bitIndex = (i % valuesPerLong) * bits;
            longs[cellIndex] |= ((long) entries[i] & mask) << bitIndex;
        }
        return longs;
    }

    /** 763 形式の Chunk Data を作る（heightmaps が名前付き NBT）。 */
    static byte[] build763(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 2バイト 0＋0x00）。
        out.writeByte(0x0A);
        out.writeByte(0x00);
        out.writeByte(0x00);
        out.writeByte(0x00);
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 24; s++) {
            int yBase = -64 + s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir);
            writePalettedContainer(sections, blockValues, true, false);
            int[] biomeValues = new int[64];
            for (int j = 0; j < 64; j++) {
                int x = j & 3;
                int z = (j >> 2) & 3;
                int yLocal = (j >> 4) & 3;
                biomeValues[j] = biomes.biome(x * 4, yBase + yLocal * 4, z * 4);
            }
            writePalettedContainer(sections, biomeValues, false, false);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int remaining = value;
        while (true) {
            if ((remaining & ~0x7F) == 0) {
                out.write(remaining);
                return;
            }
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) (value >> (i * 8)) & 0xFF);
        }
    }

    /**
     * エントリ列をバニラの BitStorage と同じ連続詰めで long 列にする。
     * 旧式の「1 long あたり 64 / bits 個」は bits が 64 を割り切らないとき
     * （3・5・6・7・9…）に long 数を多めに数え、またぐエントリの上位ビットを
     * 落とす（2026-11-14、26.1のBedrockマトリクスで発覚）。テストビルダーも
     * 本物のバニラと同じ詰め方にする。
     */
    private static long[] packContinuous(int[] entries, int bits) {
        long[] longs = new long[(entries.length * bits + 63) / 64];
        long mask = (1L << bits) - 1;
        int bitPos = 0;
        for (int entry : entries) {
            int cellIndex = bitPos >> 6;
            int bitIndex = bitPos & 63;
            longs[cellIndex] |= ((long) entry & mask) << bitIndex;
            if (bitIndex + bits > 64) {
                int lowBits = 64 - bitIndex;
                longs[cellIndex + 1] |= ((long) entry & mask) >>> lowBits;
            }
            bitPos += bits;
        }
        return longs;
    }

    /** Java のパレット付きコンテナ 1 個を書く。 */
    private static void writePalettedContainer(ByteArrayOutputStream out, int[] values,
                                               boolean blocks, boolean direct) {
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        for (int value : values) {
            palette.putIfAbsent(value, palette.size());
        }
        int entryCount = values.length;
        if (palette.size() == 1) {
            out.write(0); // BPE 0（シングル）
            writeVarInt(out, palette.keySet().iterator().next());
            writeVarInt(out, 0); // data array 長 0
            return;
        }
        if (direct) {
            int bits = blocks ? 15 : 4;
            out.write(bits);
            long[] packed = packContinuous(values, bits);
            writeVarInt(out, packed.length);
            for (long value : packed) {
                writeLong(out, value);
            }
            return;
        }
        int bits = 1;
        while ((1 << bits) < palette.size()) {
            bits++;
        }
        out.write(bits);
        writeVarInt(out, palette.size());
        List<Integer> paletteList = new ArrayList<>(palette.keySet());
        for (int value : paletteList) {
            writeVarInt(out, value);
        }
        int[] indices = new int[entryCount];
        for (int i = 0; i < entryCount; i++) {
            indices[i] = palette.get(values[i]);
        }
        long[] packed = packContinuous(indices, bits);
        writeVarInt(out, packed.length);
        for (long value : packed) {
            writeLong(out, value);
        }
    }
}
