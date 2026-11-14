package io.detergentmc.bedrock;

import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Bedrock Login パケット（0x01）の読み取りと、JWT チェーンからの
 * プレイヤー情報の抜き出し（Floodgate 風の ID の材料）。
 *
 * <p>形式（minecraft-data Bedrock 1.20.40「Login Sequence」）：
 * <pre>
 *   protocol_version: i32（ビッグエンディアン！）
 *   tokens: VarInt 長＋LoginTokens
 *     identity: LittleString（u32 LE 長＋JSON {"chain": ["<jwt>", ...]})
 *     client:   LittleString（JWT 文字列。スキン等のクライアント・データ）
 * </pre>
 * JWT は {@code header.payload.signature} の 3 部分で、payload は
 * Base64URL の JSON。チェーンの末尾の JWT の payload の
 * {@code extraData} に {@code displayName}（ゲーマータグ）、
 * {@code XUID}、{@code identity}（Xbox UUID）が入っている。
 *
 * <h2>検証の正直な区別（重要）</h2>
 * <p>このクラスは <b>JWT の署名を検証しない</b>。チェーンの形（3 個の
 * JWT があり、末尾の payload に extraData があること）だけを確認し、
 * 中身を読み取るだけである。したがって
 * {@link VerificationStatus#NOT_VERIFIED_TRUSTED} が返る。
 * これは Geyser のように Mojang の公開鍵でチェーンを検証する実装では
 * <b>ない</b>。online-mode のサーバーでこのまま使うと、XUID と
 * ゲーマータグを自称するだけの偽クライアントが、他人の XUID になりす
 * まして参加できてしまう（Floodgate 本来の前提である「Xbox Live の
 * 署名で裏付けられた XUID」がないため）。開発・身内サーバー以外での
 * 運用は、この検証が入るまで推奨しない。くわしくは docs/BEDROCK.md
 * の「セキュリティ（Xbox 署名検証なし）」を参照。
 */
public final class BedrockLogin {

    private BedrockLogin() {
    }

    /** JWT チェーンの検証状態。現状は NOT_VERIFIED_TRUSTED しか返らない。 */
    public enum VerificationStatus {
        /**
         * 署名は検証していない。チェーンの形だけを確認して中身を信じている
         * （開発用の信頼済み扱い）。本物の Xbox 検証は未実装。
         */
        NOT_VERIFIED_TRUSTED,
        /** チェーンの形すら整っていない（JWT が 0 個など）。 */
        CHAIN_MALFORMED
    }

    /** Login パケットから抜き出したプレイヤー情報。 */
    public record LoginInfo(int protocolVersion, String displayName, String xuid,
            String identityUuid, String clientDataJson,
            VerificationStatus verificationStatus) {
        /** XUID があるかどうか（無い場合は FloodgateId が名前から UUID を作る）。 */
        public boolean hasXuid() {
            return xuid != null && !xuid.isBlank();
        }
    }

    /**
     * Login パケット（ID バイトを除いたペイロード）を解析する。
     *
     * @param payload Login パケットのペイロード（ID の後ろから）
     * @return 抜き出した情報
     * @throws IllegalArgumentException 形式が不正な場合
     */
    public static LoginInfo parse(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        int protocolVersion = in.readBigInt();
        // tokens：VarInt 長＋中身。長さが残りと合わなくても、読める分だけ読む。
        long tokensLength = BedrockVarInt.readUnsignedVarInt(in);
        int tokensStart = in.position();
        if (tokensLength < 0 || tokensLength > in.remaining()) {
            throw new IllegalArgumentException(
                    "Login パケットの tokens 長が不正です: " + tokensLength);
        }
        ByteReader tokens = new ByteReader(payload, tokensStart);
        String identityJson = BedrockVarInt.readString(tokens);
        String clientJwt = "";
        if (tokens.hasRemaining()) {
            clientJwt = BedrockVarInt.readString(tokens);
        }
        return parseIdentity(protocolVersion, identityJson, clientJwt);
    }

    /** identity チェーン JSON と client JWT から LoginInfo を作る（テスト用にも使う）。 */
    static LoginInfo parseIdentity(int protocolVersion, String identityJson, String clientJwt) {
        List<String> chain = extractChain(identityJson);
        if (chain.isEmpty()) {
            return new LoginInfo(protocolVersion, "", null, null, clientJwt,
                    VerificationStatus.CHAIN_MALFORMED);
        }
        // 末尾の JWT の payload を読む（extraData は末尾にあるのが通例）。
        String displayName = "";
        String xuid = null;
        String identityUuid = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            String payloadJson = decodeJwtPayload(chain.get(i));
            if (payloadJson == null) {
                continue;
            }
            String extra = extractObject(payloadJson, "extraData");
            if (extra != null) {
                displayName = extractString(extra, "displayName");
                xuid = extractString(extra, "XUID");
                identityUuid = extractString(extra, "identity");
                break;
            }
        }
        if (displayName == null) {
            displayName = "";
        }
        return new LoginInfo(protocolVersion, displayName, xuid, identityUuid, clientJwt,
                VerificationStatus.NOT_VERIFIED_TRUSTED);
    }

    /** {@code {"chain": [...]}} から JWT 文字列の列を抜き出す。 */
    static List<String> extractChain(String identityJson) {
        List<String> chain = new ArrayList<>();
        if (identityJson == null) {
            return chain;
        }
        int chainIndex = identityJson.indexOf("\"chain\"");
        if (chainIndex < 0) {
            // chain キーが無い場合は、JSON 自体が JWT の配列という扱いはしない。
            return chain;
        }
        int bracket = identityJson.indexOf('[', chainIndex);
        int end = identityJson.indexOf(']', bracket);
        if (bracket < 0 || end < 0) {
            return chain;
        }
        String inside = identityJson.substring(bracket + 1, end);
        int cursor = 0;
        while (true) {
            int open = inside.indexOf('"', cursor);
            if (open < 0) {
                break;
            }
            int close = inside.indexOf('"', open + 1);
            if (close < 0) {
                break;
            }
            chain.add(inside.substring(open + 1, close));
            cursor = close + 1;
        }
        return chain;
    }

    /** JWT（header.payload.signature）の payload を Base64URL から JSON に戻す。 */
    static String decodeJwtPayload(String jwt) {
        if (jwt == null) {
            return null;
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Base64URL に足りない「=」を補う。 */
    private static String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    /** JSON オブジェクトからキーに対応する文字列値を抜き出す（小さな走査。JSON ライブラリは使わない）。 */
    static String extractString(String json, String key) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return null;
        }
        int cursor = colon + 1;
        while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= json.length()) {
            return null;
        }
        if (json.charAt(cursor) == '"') {
            StringBuilder out = new StringBuilder();
            cursor++;
            while (cursor < json.length()) {
                char c = json.charAt(cursor);
                if (c == '\\' && cursor + 1 < json.length()) {
                    char next = json.charAt(cursor + 1);
                    switch (next) {
                        case 'n' -> out.append('\n');
                        case 't' -> out.append('\t');
                        case 'r' -> out.append('\r');
                        case 'u' -> {
                            if (cursor + 5 < json.length()) {
                                try {
                                    out.append((char) Integer.parseInt(
                                            json.substring(cursor + 2, cursor + 6), 16));
                                } catch (NumberFormatException ignored) {
                                    // 壊れたエスケープはそのまま落とす。
                                }
                                cursor += 4;
                            }
                        }
                        default -> out.append(next);
                    }
                    cursor += 2;
                } else if (c == '"') {
                    return out.toString();
                } else {
                    out.append(c);
                    cursor++;
                }
            }
            return null;
        }
        // 文字列以外（数値など）は、その場のトークンをそのまま返す。
        int end = cursor;
        while (end < json.length()
                && ",}]".indexOf(json.charAt(end)) < 0
                && !Character.isWhitespace(json.charAt(end))) {
            end++;
        }
        return json.substring(cursor, end);
    }

    /** JSON オブジェクトからキーに対応する子オブジェクト（{...}）を抜き出す。 */
    static String extractObject(String json, String key) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return null;
        }
        int open = json.indexOf('{', colon);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(open, i + 1);
                }
            }
        }
        return null;
    }
}
