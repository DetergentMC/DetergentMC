package io.detergentmc.legacy;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

/**
 * The legacy generation's login cryptography: RSA-1024 key exchange
 * and AES/CFB8 stream encryption, used on BOTH faces of the frontend
 * (terminating a legacy client's login, and starting one towards a
 * legacy backend). Vanilla performs this encryption even in
 * offline-mode, so there is no way around implementing it.
 *
 * <p>CFB8 note (measured during the pre-Netty survey): the cipher
 * must be driven through {@link Cipher#update} directly. Routing the
 * bytes through CipherInputStream/CipherOutputStream buffers them
 * and desynchronises the stream.
 */
public final class LegacyCrypto {

    private LegacyCrypto() {
    }

    /** A stateful byte-stream cipher (AES/CFB8, no padding). */
    public static final class StreamCipher {
        private final Cipher cipher;

        private StreamCipher(Cipher cipher) {
            this.cipher = cipher;
        }

        /** Transforms bytes in place-ish (returns a new array). */
        public synchronized byte[] process(byte[] data, int offset, int length) {
            return cipher.update(data, offset, length);
        }

        public synchronized byte[] process(byte[] data) {
            return cipher.update(data);
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024, new SecureRandom());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    /** X.509-encoded public key bytes, as sent in the 0xFD packet. */
    public static byte[] publicKeyBytes(PublicKey key) {
        return key.getEncoded();
    }

    /** RSA/ECB/PKCS1Padding decryption of a shared secret / verify token. */
    public static byte[] rsaDecrypt(PrivateKey key, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    /** RSA/ECB/PKCS1Padding encryption (client face towards a backend). */
    public static byte[] rsaEncrypt(PublicKey key, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    /** Parses an X.509 RSA public key received in a 0xFD packet. */
    public static PublicKey parsePublicKey(byte[] encoded) throws GeneralSecurityException {
        return java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
    }

    public static StreamCipher encryptor(byte[] sharedSecret) {
        return streamCipher(Cipher.ENCRYPT_MODE, sharedSecret);
    }

    public static StreamCipher decryptor(byte[] sharedSecret) {
        return streamCipher(Cipher.DECRYPT_MODE, sharedSecret);
    }

    private static StreamCipher streamCipher(int mode, byte[] sharedSecret) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(mode, new SecretKeySpec(sharedSecret, "AES"),
                    new IvParameterSpec(sharedSecret));
            return new StreamCipher(cipher);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES/CFB8 setup failed", e);
        }
    }

    public static byte[] randomBytes(int n) {
        byte[] out = new byte[n];
        new SecureRandom().nextBytes(out);
        return out;
    }
}
