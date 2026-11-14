package io.detergentmc.bedrock;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Floodgate 風の ID：Bedrock プレイヤーを Java のプレイヤーとして
 * 識別するための決定的な UUID と表示名。
 *
 * <p>Floodgate（本家）がやることと同じ考え方：Bedrock プレイヤーには
 * Java アカウントが無いので、Xbox の XUID から安定した UUID を作り、
 * ゲーマータグの先頭にプレフィックス（既定 {@code "."}）を付けた
 * 名前にして Java 側に参加させる。同じ XUID は常に同じ UUID・同じ名前に
 * なるため、プレイヤーデータや権限がセッションをまたいで保たれる。
 *
 * <ul>
 *   <li>UUID は <b>UUID v3</b>（名前ベースの MD5）で、
 *       {@code "detergentmc-bedrock:" + XUID} から作る。決定的で、
 *       Java の offline UUID（{@code "OfflinePlayer:" + 名前}）とも
 *       Mojang の UUID ともかぶらない名前空間になっている。
 *       Floodgate 本家の UUID 生成式とは別物であるため、Floodgate と
 *       同じ XUID でも DetergentMC の UUID は一致しない（別ソフトの
 *       ため、これは意図した違いである）。</li>
 *   <li>表示名は {@code prefix + ゲーマータグ} を Java の 16 文字制限に
 *       合わせて整形する。Java のプレイヤー名に使えない文字
 *       （{@code [A-Za-z0-9_]} 以外）は落とし、16 文字を超えたら切り捨てる。
 *       ゲーマータグが空の場合は {@code prefix + "Player"} にする。</li>
 *   <li>XUID が無い場合（Xbox Live にログインしていない Bedrock
 *       クライアント）は、ゲーマータグから UUID v3 を作る
 *       （{@code "detergentmc-bedrock:name:" + ゲーマータグ}）。XUID
 *       ありの場合と名前空間を分けてあるため、XUID なしのプレイヤーが
 *       XUID ありのプレイヤーと UUID で衝突しない。</li>
 * </ul>
 */
public final class FloodgateId {

    /** Floodgate 風の既定プレフィックス。 */
    public static final String DEFAULT_PREFIX = ".";

    private FloodgateId() {
    }

    /** XUID から決定的な UUID v3 を作る。 */
    public static UUID uuidForXuid(String xuid) {
        return UUID.nameUUIDFromBytes(
                ("detergentmc-bedrock:" + xuid).getBytes(StandardCharsets.UTF_8));
    }

    /** XUID が無い場合にゲーマータグから決定的な UUID v3 を作る。 */
    public static UUID uuidForName(String gamertag) {
        return UUID.nameUUIDFromBytes(
                ("detergentmc-bedrock:name:" + gamertag).getBytes(StandardCharsets.UTF_8));
    }

    /** Login 情報から Java 側で使う UUID を決める。 */
    public static UUID uuidFor(BedrockLogin.LoginInfo login) {
        if (login.hasXuid()) {
            return uuidForXuid(login.xuid());
        }
        return uuidForName(login.displayName());
    }

    /**
     * ゲーマータグを Java のプレイヤー名に整形する。
     *
     * @param prefix   先頭に付けるプレフィックス（既定 "."）
     * @param gamertag Bedrock のゲーマータグ
     * @return Java の 16 文字制限に収まる名前
     */
    public static String formatJavaName(String prefix, String gamertag) {
        // プレフィックスは Floodgate 風の "." を残すため、[A-Za-z0-9_.] だけにする
        // （ゲーマータグ側は Java 名に使えない文字を落とす）。
        String cleanPrefix = prefix == null ? "" : prefix.replaceAll("[^A-Za-z0-9_.]", "");
        String tag = gamertag == null ? "" : gamertag.replaceAll("[^A-Za-z0-9_]", "");
        if (tag.isEmpty()) {
            tag = "Player";
        }
        String combined = cleanPrefix + tag;
        if (combined.length() > 16) {
            combined = combined.substring(0, 16);
        }
        if (combined.isEmpty()) {
            combined = "Player";
        }
        return combined;
    }

    /** ログイン情報とプレフィックスから Java 側の表示名を作る。 */
    public static String javaNameFor(BedrockLogin.LoginInfo login, String prefix) {
        return formatJavaName(prefix, login.displayName());
    }
}
