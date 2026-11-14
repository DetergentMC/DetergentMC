package io.detergentmc.protocol;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mojang認証（online-mode）の暗号まわり。純粋関数と小さな値型だけ。
 *
 * <p>流れ（Velocity／BungeeCord の信頼プロキシと同じ考え方）：
 * プロキシがクライアントとの間で認証を自分で完結させる。内部バニラは
 * offline-mode（信頼済み接続）で動かし、バニラ自身の暗号化は使わない。
 * 以前の「763＋online-mode は暗号化の MITM が素のバニラ相手には原理的に
 * 成立しない」という結論は、バニラに認証を任せたまま翻訳しようとした
 * 場合の話である。認証の主体をプロキシに移せば成立する。
 *
 * <ol>
 *   <li>プロキシが RSA 鍵ペアと verify token を作り、Encryption Request
 *       （serverId・公開鍵・verify token）をクライアントへ送る。</li>
 *   <li>クライアントは shared secret と verify token をプロキシの公開鍵で
 *       RSA 暗号化して Encryption Response で返す。</li>
 *   <li>プロキシは秘密鍵で復号し、verify token が一致すれば shared secret
 *       で AES/CFB8 暗号化を有効化する。</li>
 *   <li>serverId ハッシュ（SHA-1。Mojang の決め方）を作り、セッション
 *       サーバー（{@link SessionServerClient}）へ hasJoined を照会する。</li>
 *   <li>認証成功時のみ、認証済み UUID・名前で Login Start を書き換えて
 *       内部バニラへ繋ぐ。</li>
 * </ol>
 *
 * <p>serverId ハッシュは Mojang の決め方そのまま：SHA-1（serverId の
 * ISO-8859-1 バイト列＋shared secret＋公開鍵の DER バイト列）を
 * {@link BigInteger} の16進数にし、負の場合は先頭に {@code -} を付ける
 * （{@code new BigInteger(digest).toString(16)}）。
 */
public final class MojangAuth {

    /** Mojang認証で使う serverId。バニラと同じく空文字列。 */
    public static final String SERVER_ID = "";

    private static final SecureRandom RANDOM = new SecureRandom();

    private MojangAuth() {
    }

    /** 認証に失敗したときの例外。メッセージはそのまま切断理由に使える。 */
    public static final class AuthException extends IllegalArgumentException {
        AuthException(String message) {
            super(message);
        }

        AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Encryption Request の中身（クライアントが受け取るもの）。 */
    public record EncryptionRequest(String serverId, byte[] publicKey, byte[] verifyToken) {
    }

    /** RSA 鍵ペア（1024ビット。バニラと同じ長さ）を作る。 */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024, RANDOM);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSAが使えません", e);
        }
    }

    /** verify token（4バイトの乱数）を作る。 */
    public static byte[] generateVerifyToken() {
        byte[] token = new byte[4];
        RANDOM.nextBytes(token);
        return token;
    }

    /** shared secret（16バイトの乱数。AES-128）を作る。 */
    public static byte[] generateSharedSecret() {
        byte[] secret = new byte[16];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** Encryption Request（ログイン 0x01）のペイロードを作る。 */
    public static byte[] encryptionRequestPayload(String serverId, PublicKey publicKey,
            byte[] verifyToken) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(serverId);
        writer.writeByteArray(publicKey.getEncoded());
        writer.writeByteArray(verifyToken);
        return writer.bytes();
    }

    /** Encryption Request のペイロードを読む（テスト用フェイククライアント向け）。 */
    public static EncryptionRequest parseEncryptionRequest(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        String serverId = reader.readString();
        byte[] publicKey = reader.readByteArray();
        byte[] verifyToken = reader.readByteArray();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Encryption Request に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new EncryptionRequest(serverId, publicKey, verifyToken);
    }

    /** DER バイト列から RSA 公開鍵を復元する（テスト用フェイククライアント向け）。 */
    public static PublicKey decodePublicKey(byte[] encoded) {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalArgumentException("公開鍵を復元できませんでした。", e);
        }
    }

    /** shared secret と verify token を公開鍵で RSA 暗号化して Encryption Response を作る。 */
    public static byte[] buildEncryptionResponsePayload(byte[] sharedSecret, byte[] verifyToken,
            PublicKey publicKey) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeByteArray(rsaEncrypt(sharedSecret, publicKey));
        writer.writeByteArray(rsaEncrypt(verifyToken, publicKey));
        return writer.bytes();
    }

    /** Encryption Response（ログイン 0x01）のペイロードを読む。 */
    public static byte[][] parseEncryptionResponse(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        byte[] secret = reader.readByteArray();
        byte[] token = reader.readByteArray();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Encryption Response に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new byte[][]{secret, token};
    }

    /** RSA/ECB/PKCS1Padding で暗号化する。 */
    public static byte[] rsaEncrypt(byte[] plain, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, RANDOM);
            return cipher.doFinal(plain);
        } catch (Exception e) {
            throw new IllegalStateException("RSA暗号化に失敗しました。", e);
        }
    }

    /** RSA/ECB/PKCS1Padding で復号する。 */
    public static byte[] rsaDecrypt(byte[] encrypted, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new AuthException("暗号化応答を復号できませんでした。", e);
        }
    }

    /**
     * serverId ハッシュを作る。Mojang の hasJoined 照会に使う値。
     *
     * @param serverId     サーバーID（DetergentMC では常に {@link #SERVER_ID}＝空文字）
     * @param sharedSecret クライアントと共有した秘密（16バイト）
     * @param publicKey    プロキシの公開鍵の DER バイト列
     */
    public static String serverIdHash(String serverId, byte[] sharedSecret, byte[] publicKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(serverId.getBytes(StandardCharsets.ISO_8859_1));
            sha1.update(sharedSecret);
            sha1.update(publicKey);
            return new BigInteger(sha1.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1が使えません", e);
        }
    }

    /** shared secret から AES/CFB8 の暗号化用 Cipher を作る。 */
    public static Cipher createEncryptCipher(byte[] sharedSecret) {
        return createCipher(sharedSecret, Cipher.ENCRYPT_MODE);
    }

    /** shared secret から AES/CFB8 の復号用 Cipher を作る。 */
    public static Cipher createDecryptCipher(byte[] sharedSecret) {
        return createCipher(sharedSecret, Cipher.DECRYPT_MODE);
    }

    private static Cipher createCipher(byte[] sharedSecret, int mode) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            SecretKeySpec key = new SecretKeySpec(sharedSecret, "AES");
            cipher.init(mode, key, new IvParameterSpec(sharedSecret));
            return cipher;
        } catch (Exception e) {
            throw new IllegalStateException("AES/CFB8 の Cipher を作れませんでした。", e);
        }
    }

    /** バイト列を定数時間で比べる（verify token の照合用）。 */
    public static boolean tokensEqual(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
