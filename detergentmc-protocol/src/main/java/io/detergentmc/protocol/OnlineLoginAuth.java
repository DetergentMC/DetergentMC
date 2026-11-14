package io.detergentmc.protocol;

import io.detergentmc.protocol.MojangAuth.AuthException;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Optional;

/**
 * 1回のログイン認証の試行（担当1：763 online-mode認証）。
 *
 * <p>プロキシがクライアントとの間で行う認証の、暗号まわりの状態を持つ。
 * hasJoined 照会そのものは {@link SessionServerClient} に任せ、ここは
 * 鍵・トークン・shared secret・serverId ハッシュだけを扱う。Netty は
 * 使わないので単体テストしやすい。
 *
 * <pre>
 * OnlineLoginAuth attempt = OnlineLoginAuth.begin("Steve");
 * // attempt.encryptionRequestPayload() をクライアントへ送る。
 * OnlineLoginAuth.Accepted accepted =
 *         attempt.acceptEncryptionResponse(responsePayload);
 * // accepted.sharedSecret() で AES/CFB8 を有効化し、
 * // verifier.verify("Steve", accepted.serverIdHash()) で認証する。
 * </pre>
 */
public final class OnlineLoginAuth {

    private final String username;
    private final KeyPair keyPair;
    private final byte[] verifyToken;
    private byte[] sharedSecret;
    private String serverIdHash;

    private OnlineLoginAuth(String username, KeyPair keyPair, byte[] verifyToken) {
        this.username = username;
        this.keyPair = keyPair;
        this.verifyToken = verifyToken;
    }

    /** 新しい認証試行を始める。鍵ペアと verify token を作る。 */
    public static OnlineLoginAuth begin(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        return new OnlineLoginAuth(username,
                MojangAuth.generateKeyPair(), MojangAuth.generateVerifyToken());
    }

    public String username() {
        return username;
    }

    /** クライアントへ送る Encryption Request（ログイン 0x01）のペイロード。 */
    public byte[] encryptionRequestPayload() {
        return MojangAuth.encryptionRequestPayload(
                MojangAuth.SERVER_ID, keyPair.getPublic(), verifyToken);
    }

    /** Encryption Response を受け取った後、認証が済んだか。 */
    public boolean hasSharedSecret() {
        return sharedSecret != null;
    }

    /** クライアントと共有した秘密（Encryption Response の受信後に有効）。 */
    public byte[] sharedSecret() {
        return sharedSecret == null ? null : sharedSecret.clone();
    }

    /** hasJoined 照会に使う serverId ハッシュ（受信後に有効）。 */
    public String serverIdHash() {
        return serverIdHash;
    }

    /** Encryption Response を受け取った結果。 */
    public record Accepted(byte[] sharedSecret, String serverIdHash) {
    }

    /**
     * クライアントの Encryption Response を処理する。
     *
     * @return 復号できた shared secret と serverId ハッシュ
     * @throws AuthException verify token が合わない・復号できない場合
     */
    public Accepted acceptEncryptionResponse(byte[] payload) {
        if (sharedSecret != null) {
            throw new AuthException("暗号化応答を二重に受け取りました。");
        }
        byte[][] parts;
        try {
            parts = MojangAuth.parseEncryptionResponse(payload);
        } catch (IllegalArgumentException e) {
            throw new AuthException("暗号化応答の形式が不正です。", e);
        }
        byte[] secret = MojangAuth.rsaDecrypt(parts[0], keyPair.getPrivate());
        byte[] token = MojangAuth.rsaDecrypt(parts[1], keyPair.getPrivate());
        if (!MojangAuth.tokensEqual(token, verifyToken)) {
            throw new AuthException("認証に失敗しました（verify token が一致しません）。");
        }
        if (secret.length != 16) {
            throw new AuthException("認証に失敗しました（shared secret の長さが不正です）。");
        }
        this.sharedSecret = secret;
        this.serverIdHash = MojangAuth.serverIdHash(
                MojangAuth.SERVER_ID, secret, keyPair.getPublic().getEncoded());
        return new Accepted(secret.clone(), serverIdHash);
    }

    /**
     * セッションサーバーへ hasJoined を照会する。
     *
     * @return 認証済みプロフィール。参加していない場合は空
     */
    public Optional<AuthenticatedProfile> verifyProfile(SessionServerClient verifier)
            throws IOException, InterruptedException {
        if (sharedSecret == null) {
            throw new IllegalStateException("Encryption Response をまだ受け取っていません。");
        }
        return verifier.verify(username, serverIdHash);
    }
}
