package io.detergentmc.bedrock;

/**
 * Bedrock（Geyser 風＋Floodgate 風）の設定値。
 *
 * <p>{@code detergentmc.yml} のキーと既定値：
 * <pre>
 * bedrock:
 *   enabled: true        # Bedrock リスナーを起動するかどうか
 *   port: 19132          # Bedrock（RakNet UDP）の待ち受けポート
 *   motd: "DetergentMC 1.20.4"   # サーバーリストの MOTD 行1
 *   chunk-radius: 6      # Bedrock へ送るチャンクの半径（チャンク単位）
 * floodgate:
 *   prefix: "."          # Bedrock プレイヤーの Java 名の先頭に付ける文字
 * </pre>
 * {@code max-players} と {@code server-name} は Java 側の設定を流用する
 * （MOTD の人数・ワールド名に出す）。
 */
public record BedrockConfig(boolean enabled, int port, String motd, String floodgatePrefix,
        int maxPlayers, String serverName, String internalHost, int internalPort,
        int serverProtocol, int chunkRadius) {

    /** Bedrock リスナーの既定ポート。 */
    public static final int DEFAULT_PORT = BedrockProtocol.DEFAULT_PORT;

    /** Floodgate 風プレフィックスの既定値。 */
    public static final String DEFAULT_FLOODGATE_PREFIX = FloodgateId.DEFAULT_PREFIX;

    /** チャンク半径の既定値（チャンク単位）。Java の視野距離は 8。 */
    public static final int DEFAULT_CHUNK_RADIUS = 6;

    /** チャンク半径の上限（チャンク単位）。 */
    public static final int MAX_CHUNK_RADIUS = 12;

    /** 既定値だけの設定（テスト用）。 */
    public static BedrockConfig defaults(int serverProtocol, int internalPort) {
        return new BedrockConfig(true, DEFAULT_PORT, "DetergentMC", DEFAULT_FLOODGATE_PREFIX,
                20, "DetergentMC", "127.0.0.1", internalPort, serverProtocol,
                DEFAULT_CHUNK_RADIUS);
    }

    public BedrockConfig {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("bedrock.port が範囲外です: " + port);
        }
        if (chunkRadius < 1 || chunkRadius > MAX_CHUNK_RADIUS) {
            throw new IllegalArgumentException(
                    "bedrock.chunk-radius が範囲外です: " + chunkRadius + "（1〜" + MAX_CHUNK_RADIUS + "）。");
        }
        if (floodgatePrefix == null) {
            floodgatePrefix = DEFAULT_FLOODGATE_PREFIX;
        }
        if (motd == null) {
            motd = "DetergentMC";
        }
        if (serverName == null) {
            serverName = "DetergentMC";
        }
    }
}
