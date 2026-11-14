package io.detergentmc.bedrock;

import java.nio.charset.StandardCharsets;

/**
 * Bedrock のサーバーリスト用 MOTD（Unconnected Pong に入る文字列）。
 *
 * <p>形式は RakNet の Unconnected Pong（wiki.bedrock.dev「RakNet Protocol」）：
 * <pre>
 * MCPE;MOTD行1;プロトコル番号;バージョン名;現在人数;最大人数;サーバー固有ID;
 * MOTD行2;ゲームモード;ゲームモード（数値）;IPv4ポート;IPv6ポート;
 * </pre>
 * 例：{@code MCPE;Dedicated Server;527;1.19.1;0;10;13253860892328930865;Bedrock level;Survival;1;19132;19133;}
 * 「;」は MOTD の中に使えない（区切りと区別がつかなくなる）ため、生成時に
 * 「;」は全角の「；」に置き換える。
 */
public final class BedrockMotd {

    private BedrockMotd() {
    }

    /**
     * MOTD 文字列を作る。
     *
     * @param motdLine1  サーバーリストの1行目（例："DetergentMC 1.20.4"）
     * @param protocol   Bedrock プロトコル番号（1.20.50 なら 630）
     * @param version    Bedrock バージョン名（例："1.20.50"）
     * @param players    現在の Bedrock プレイヤー数
     * @param maxPlayers 最大プレイヤー数
     * @param serverId   サーバー固有ID（GUID。起動ごとに決まる数値）
     * @param motdLine2  サーバーリストの2行目（例："DetergentMC"）
     * @param ipv4Port   IPv4 ポート（既定 19132）
     * @param ipv6Port   IPv6 ポート（既定 19133。IPv6 を出さない実装では 19132 と同じ値を入れる）
     */
    public static String build(String motdLine1, int protocol, String version,
            int players, int maxPlayers, long serverId, String motdLine2,
            int ipv4Port, int ipv6Port) {
        return String.join(";",
                "MCPE",
                sanitize(motdLine1),
                String.valueOf(protocol),
                version,
                String.valueOf(players),
                String.valueOf(maxPlayers),
                String.valueOf(serverId),
                sanitize(motdLine2),
                "Survival",
                "1",
                String.valueOf(ipv4Port),
                String.valueOf(ipv6Port),
                "");
    }

    /** 「;」を全角に置き換える（区切りと区別するため）。 */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(';', '；').replace('\n', ' ');
    }

    /** MOTD 文字列を分解する（パースできない場合は null）。 */
    public static Parsed parse(String motd) {
        if (motd == null) {
            return null;
        }
        // 末尾の空要素も含めて分けるため limit に -1 を使う。
        String[] parts = motd.split(";", -1);
        if (parts.length < 12 || !"MCPE".equals(parts[0])) {
            return null;
        }
        try {
            return new Parsed(
                    parts[1],
                    Integer.parseInt(parts[2]),
                    parts[3],
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5]),
                    Long.parseLong(parts[6]),
                    parts[7],
                    parts[8],
                    Integer.parseInt(parts[9]),
                    Integer.parseInt(parts[10]),
                    Integer.parseInt(parts[11]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@link #parse(String)} の結果。 */
    public record Parsed(String motdLine1, int protocol, String version,
            int players, int maxPlayers, long serverId, String motdLine2,
            String gameMode, int gameModeNumeric, int ipv4Port, int ipv6Port) {
    }

    /** Unconnected Pong の MOTD 部分（文字列長 u16 BE＋UTF-8）をバイト列にする。 */
    public static byte[] encodeMotdField(String motd) {
        byte[] utf8 = motd.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[2 + utf8.length];
        out[0] = (byte) ((utf8.length >> 8) & 0xFF);
        out[1] = (byte) (utf8.length & 0xFF);
        System.arraycopy(utf8, 0, out, 2, utf8.length);
        return out;
    }
}
