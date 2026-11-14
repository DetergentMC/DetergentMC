package io.detergentmc.protocol;

/**
 * プロキシ認証の設定（担当1：763 online-mode認証）。
 *
 * <p>{@code server.online-mode=true} のとき、プロキシはクライアントとの
 * 間で Mojang 認証を自分で完結させ、認証済みの接続だけを内部バニラ
 * （offline-mode、信頼済み接続）へ繋ぐ。{@code online-mode=false} の
 * とき、この設定は使われない。
 *
 * <p>セッションサーバーの URL は差し替え可能で、既定は本物の Mojang
 * セッションサーバー。テストではロカールの偽サーバー（http の
 * loopback）を指定する。本番で http を指定すると認証が平文になるため、
 * 運用では既定の https のままにすること。
 */
public final class ProxyAuthConfig {

    /** 本物の Mojang セッションサーバー。 */
    public static final String DEFAULT_SESSION_SERVER_URL =
            SessionServerClient.HttpSessionServerClient.DEFAULT_BASE_URL;

    private final String sessionServerBaseUrl;
    private final SessionServerClient verifier;

    private ProxyAuthConfig(String sessionServerBaseUrl, SessionServerClient verifier) {
        this.sessionServerBaseUrl = sessionServerBaseUrl;
        this.verifier = verifier == null
                ? new SessionServerClient.HttpSessionServerClient(sessionServerBaseUrl)
                : verifier;
    }

    /** 既定の設定（本物の Mojang セッションサーバー）。 */
    public static ProxyAuthConfig defaults() {
        return new ProxyAuthConfig(DEFAULT_SESSION_SERVER_URL, null);
    }

    /**
     * セッションサーバーの base URL を指定して作る。空文字・null は既定
     * （本物の Mojang）になる。テストでは偽サーバーの URL を渡す。
     */
    public static ProxyAuthConfig of(String sessionServerBaseUrl) {
        String url = sessionServerBaseUrl == null || sessionServerBaseUrl.isBlank()
                ? DEFAULT_SESSION_SERVER_URL
                : sessionServerBaseUrl.strip();
        return new ProxyAuthConfig(url, null);
    }

    /** 照会器を直接差し替えて作る（単体テスト用）。 */
    public static ProxyAuthConfig withVerifier(SessionServerClient verifier) {
        if (verifier == null) {
            throw new IllegalArgumentException("verifier is required");
        }
        return new ProxyAuthConfig(DEFAULT_SESSION_SERVER_URL, verifier);
    }

    /** セッションサーバーの base URL。 */
    public String sessionServerBaseUrl() {
        return sessionServerBaseUrl;
    }

    /** hasJoined 照会器。 */
    public SessionServerClient verifier() {
        return verifier;
    }
}
