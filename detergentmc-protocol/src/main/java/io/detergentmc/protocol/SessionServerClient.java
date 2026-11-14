package io.detergentmc.protocol;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mojangセッションサーバーへの hasJoined 照会。
 *
 * <p>照会先は {@code {baseUrl}/session/minecraft/hasJoined} で、既定の
 * baseUrl は本物の Mojang セッションサーバー
 * （{@code https://sessionserver.mojang.com}）。テストでは baseUrl を
 * ローカルの偽サーバーに差し替えられる（ {@link ProxyAuthConfig} 参照）。
 * 本物の Mojang への到達はサンドボックスでは遮断される場合があるため、
 * 単体・結合テストは偽サーバーで行う。
 *
 * <p>応答の意味：
 * <ul>
 *   <li>200：認証済み。JSON（{@code id}・{@code name}・
 *       {@code properties}）から {@link AuthenticatedProfile} を作る。</li>
 *   <li>204／403／404：そのプレイヤーはその serverId で参加していない
 *       （認証失敗）。空を返す。</li>
 *   <li>429／5xx などそれ以外：セッションサーバー側の問題として
 *       {@link IOException} を投げる（呼び出し元は「認証サーバーに
 *       接続できませんでした」として切断する）。</li>
 * </ul>
 *
 * <p>JSON の読み取りは小さな専用パーサーで行う（protocol モジュールに
 * JSON ライブラリは無い）。Mojang の応答は {@code id}（32桁16進数・
 * ハイフン無し）、{@code name}、{@code properties}（name／value／
 * signature の配列）だけを使う。
 */
public interface SessionServerClient {

    /**
     * hasJoined を照会する。
     *
     * @return 認証済みプロフィール。参加していない場合は空
     * @throws IOException 通信できない・応答が読めない場合
     * @throws InterruptedException 待ち受け中に割り込まれた場合
     */
    Optional<AuthenticatedProfile> verify(String username, String serverIdHash)
            throws IOException, InterruptedException;

    /** 本物の Mojang セッションサーバーへの HTTP 実装。 */
    final class HttpSessionServerClient implements SessionServerClient {

        /** 本物の Mojang セッションサーバー。 */
        public static final String DEFAULT_BASE_URL = "https://sessionserver.mojang.com";

        private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
        private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

        private final String baseUrl;
        private final HttpClient httpClient;

        public HttpSessionServerClient(String baseUrl) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("session server base URL is required");
            }
            this.baseUrl = baseUrl.stripTrailing().replaceAll("/+$", "");
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        }

        /** 既定（本物の Mojang セッションサーバー）で作る。 */
        public HttpSessionServerClient() {
            this(DEFAULT_BASE_URL);
        }

        public String baseUrl() {
            return baseUrl;
        }

        @Override
        public Optional<AuthenticatedProfile> verify(String username, String serverIdHash)
                throws IOException, InterruptedException {
            String url = baseUrl + "/session/minecraft/hasJoined?username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&serverId=" + URLEncoder.encode(serverIdHash, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new IOException("セッションサーバーに接続できませんでした (" + baseUrl + ")", e);
            }
            int status = response.statusCode();
            if (status == 200) {
                return Optional.of(parseProfile(response.body()));
            }
            if (status == 204 || status == 403 || status == 404) {
                return Optional.empty();
            }
            throw new IOException("セッションサーバーが応答しませんでした (HTTP " + status + ")");
        }

        /** hasJoined の JSON 応答をプロフィールに変換する（テスト用に公開）。 */
        static AuthenticatedProfile parseProfile(String json) throws IOException {
            String id = extractString(json, "id");
            String name = extractString(json, "name");
            if (id == null || name == null) {
                throw new IOException("セッションサーバーの応答が読めませんでした: " + json);
            }
            List<AuthenticatedProfile.Property> properties = extractProperties(json);
            return new AuthenticatedProfile(parseMojangId(id), name, properties);
        }

        /** 32桁16進数の Mojang ID を UUID にする。 */
        static UUID parseMojangId(String id) throws IOException {
            String hex = id.replace("-", "");
            if (hex.length() != 32) {
                throw new IOException("セッションサーバーの UUID が不正です: " + id);
            }
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-"
                    + hex.substring(20);
            try {
                return UUID.fromString(dashed);
            } catch (IllegalArgumentException e) {
                throw new IOException("セッションサーバーの UUID が不正です: " + id, e);
            }
        }

        /** JSON から {@code "key":"value"} の文字列値を抜く。無ければ null。 */
        static String extractString(String json, String key) {
            String needle = "\"" + key + "\"";
            int at = json.indexOf(needle);
            while (at >= 0) {
                int colon = json.indexOf(':', at + needle.length());
                if (colon < 0) {
                    return null;
                }
                int i = colon + 1;
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                if (i >= json.length()) {
                    return null;
                }
                if (json.charAt(i) == '"') {
                    return readJsonString(json, i);
                }
                // 値が文字列でない（次のキー等）場合は次の同名キーを探す。
                at = json.indexOf(needle, at + needle.length());
            }
            return null;
        }

        /** {@code "properties":[{...},{...}]} を抜く。無ければ空の一覧。 */
        static List<AuthenticatedProfile.Property> extractProperties(String json) {
            List<AuthenticatedProfile.Property> result = new ArrayList<>();
            int at = json.indexOf("\"properties\"");
            if (at < 0) {
                return result;
            }
            int open = json.indexOf('[', at);
            if (open < 0) {
                return result;
            }
            int close = findMatchingBracket(json, open);
            if (close < 0) {
                return result;
            }
            String body = json.substring(open + 1, close);
            int cursor = 0;
            while (cursor < body.length()) {
                int objOpen = body.indexOf('{', cursor);
                if (objOpen < 0) {
                    break;
                }
                int objClose = findMatchingBrace(body, objOpen);
                if (objClose < 0) {
                    break;
                }
                String obj = body.substring(objOpen, objClose + 1);
                String name = extractString(obj, "name");
                String value = extractString(obj, "value");
                String signature = extractString(obj, "signature");
                if (name != null && value != null) {
                    result.add(new AuthenticatedProfile.Property(name, value, signature));
                }
                cursor = objClose + 1;
            }
            return List.copyOf(result);
        }

        /** {@code "...."} の中身を読み、JSON エスケープを戻す。 */
        static String readJsonString(String json, int openQuote) {
            StringBuilder out = new StringBuilder();
            for (int i = openQuote + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            if (i + 5 < json.length()) {
                                try {
                                    out.append((char) Integer.parseInt(
                                            json.substring(i + 2, i + 6), 16));
                                } catch (NumberFormatException ignored) {
                                    // 壊れたエスケープはそのまま残す。
                                }
                                i += 4;
                            }
                        }
                        default -> out.append(next);
                    }
                    i++;
                    continue;
                }
                out.append(c);
            }
            return out.toString();
        }

        private static int findMatchingBrace(String text, int open) {
            return findMatching(text, open, '{', '}');
        }

        private static int findMatchingBracket(String text, int open) {
            return findMatching(text, open, '[', ']');
        }

        private static int findMatching(String text, int open, char openChar, char closeChar) {
            int depth = 0;
            boolean inString = false;
            for (int i = open; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == openChar) {
                    depth++;
                } else if (c == closeChar) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }
}
