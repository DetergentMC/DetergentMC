package io.detergentmc.bedrock;

import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Bedrock ゲームパケットのバッチ（0xfe）。
 *
 * <p>形式：先頭 1 バイト {@code 0xfe}＋zlib 圧縮されたペイロード。
 * 圧縮を解いた中身はパケットの列で、1 個ずつ「符号なし VarInt の長さ＋
 * パケット本体（先頭がパケット ID の符号なし VarInt）」が並ぶ。
 * zlib は Java 標準ライブラリ（java.util.zip）で扱う。
 *
 * <p>注意：Bedrock の圧縮は「zlib 形式」（Deflater の既定）で、gzip ではない。
 */
public final class BedrockBatch {

    private BedrockBatch() {
    }

    /** パケット 1 個（ID＋ID 以後のペイロード）。 */
    public record GamePacket(int id, byte[] payload) {
    }

    /** 複数のゲームパケットを 1 個のバッチ（0xfe＋zlib）にまとめる。 */
    public static byte[] encode(List<GamePacket> packets) {
        ByteWriter inner = new ByteWriter();
        for (GamePacket packet : packets) {
            ByteWriter one = new ByteWriter();
            BedrockVarInt.writeUnsignedVarInt(one, packet.id());
            one.writeBytes(packet.payload());
            byte[] bytes = one.bytes();
            BedrockVarInt.writeUnsignedVarInt(inner, bytes.length);
            inner.writeBytes(bytes);
        }
        byte[] compressed = compress(inner.bytes());
        byte[] out = new byte[1 + compressed.length];
        out[0] = (byte) BedrockProtocol.ID_BATCH;
        System.arraycopy(compressed, 0, out, 1, compressed.length);
        return out;
    }

    /** 1 個のゲームパケットだけのバッチを作る。 */
    public static byte[] encodeOne(int id, byte[] payload) {
        return encode(List.of(new GamePacket(id, payload)));
    }

    /** バッチを解いてゲームパケットの列にする。0xfe で始まらない場合は例外。 */
    public static List<GamePacket> decode(byte[] batch) throws IOException {
        if (batch == null || batch.length < 1
                || batch[0] != (byte) BedrockProtocol.ID_BATCH) {
            throw new IllegalArgumentException("Bedrock バッチ（0xfe）ではありません。");
        }
        byte[] plain = decompress(batch, 1, batch.length - 1);
        List<GamePacket> packets = new ArrayList<>();
        ByteReader in = new ByteReader(plain);
        while (in.hasRemaining()) {
            long length = BedrockVarInt.readUnsignedVarInt(in);
            if (length <= 0 || length > in.remaining()) {
                throw new IllegalArgumentException(
                        "バッチ内のパケット長が不正です: " + length);
            }
            byte[] one = in.readBytes((int) length);
            ByteReader packetReader = new ByteReader(one);
            int id = (int) BedrockVarInt.readUnsignedVarInt(packetReader);
            packets.add(new GamePacket(id, packetReader.rest()));
        }
        return packets;
    }

    /** zlib 圧縮（Bedrock のバッチ形式）。 */
    static byte[] compress(byte[] plain) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(plain);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** zlib 展開。展開後のサイズが 16MB を超えたら例外（異常なバッチへの防御）。 */
    static byte[] decompress(byte[] bytes, int offset, int length) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes, offset, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count;
                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException e) {
                    throw new IOException("Bedrock バッチの zlib 展開に失敗しました。", e);
                }
                if (count == 0) {
                    if (inflater.needsInput()) {
                        break;
                    }
                    throw new IOException("Bedrock バッチの zlib 展開が進みません。");
                }
                out.write(buffer, 0, count);
                if (out.size() > 16 * 1024 * 1024) {
                    throw new IOException("Bedrock バッチが大きすぎます（16MB 超）。");
                }
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    /** 生の zlib 圧縮データを展開する（テスト用）。 */
    public static byte[] decompressRaw(byte[] compressed) throws IOException {
        return decompress(compressed, 0, compressed.length);
    }
}
