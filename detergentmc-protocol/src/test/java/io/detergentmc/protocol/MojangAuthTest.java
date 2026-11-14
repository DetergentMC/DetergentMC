package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MojangAuth.AuthException;
import io.detergentmc.protocol.MojangAuth.EncryptionRequest;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

/**
 * Mojang認証の暗号まわりの検証（担当1）。ネットワークは使わない。
 */
class MojangAuthTest {

    @Test
    void serverIdHashMatchesMojangFormula() throws Exception {
        byte[] secret = new byte[16];
        Arrays.fill(secret, (byte) 0x42);
        KeyPair keys = MojangAuth.generateKeyPair();
        String hash = MojangAuth.serverIdHash(
                "", secret, keys.getPublic().getEncoded());
        // Mojang の決め方をテスト側で素直に計算し直す。
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update("".getBytes(StandardCharsets.ISO_8859_1));
        sha1.update(secret);
        sha1.update(keys.getPublic().getEncoded());
        String expected = new BigInteger(sha1.digest()).toString(16);
        assertEquals(expected, hash);
    }

    @Test
    void serverIdHashIsDeterministicAndDependsOnInputs() {
        KeyPair keys = MojangAuth.generateKeyPair();
        byte[] secret = MojangAuth.generateSharedSecret();
        String first = MojangAuth.serverIdHash("", secret, keys.getPublic().getEncoded());
        String second = MojangAuth.serverIdHash("", secret, keys.getPublic().getEncoded());
        assertEquals(first, second);
        byte[] otherSecret = MojangAuth.generateSharedSecret();
        assertNotEquals(first,
                MojangAuth.serverIdHash("", otherSecret, keys.getPublic().getEncoded()));
    }

    @Test
    void encryptionRequestResponseRoundTrip() {
        KeyPair keys = MojangAuth.generateKeyPair();
        byte[] token = MojangAuth.generateVerifyToken();
        byte[] request = MojangAuth.encryptionRequestPayload("", keys.getPublic(), token);
        EncryptionRequest parsed = MojangAuth.parseEncryptionRequest(request);
        assertEquals("", parsed.serverId());
        assertArrayEquals(keys.getPublic().getEncoded(), parsed.publicKey());
        assertArrayEquals(token, parsed.verifyToken());

        byte[] secret = MojangAuth.generateSharedSecret();
        byte[] response = MojangAuth.buildEncryptionResponsePayload(
                secret, token, keys.getPublic());
        byte[][] parts = MojangAuth.parseEncryptionResponse(response);
        assertArrayEquals(secret, MojangAuth.rsaDecrypt(parts[0], keys.getPrivate()));
        assertArrayEquals(token, MojangAuth.rsaDecrypt(parts[1], keys.getPrivate()));
    }

    @Test
    void rsaDecryptWithWrongKeyFails() {
        KeyPair keys = MojangAuth.generateKeyPair();
        KeyPair other = MojangAuth.generateKeyPair();
        byte[] response = MojangAuth.buildEncryptionResponsePayload(
                MojangAuth.generateSharedSecret(), MojangAuth.generateVerifyToken(),
                keys.getPublic());
        byte[][] parts = MojangAuth.parseEncryptionResponse(response);
        assertThrows(AuthException.class,
                () -> MojangAuth.rsaDecrypt(parts[0], other.getPrivate()));
    }

    @Test
    void aesCfb8RoundTrip() throws Exception {
        byte[] secret = MojangAuth.generateSharedSecret();
        Cipher encrypt = MojangAuth.createEncryptCipher(secret);
        Cipher decrypt = MojangAuth.createDecryptCipher(secret);
        byte[] plain = "hello minecraft proxy".getBytes(StandardCharsets.UTF_8);
        // ストリーム暗号らしく、分割して暗号化・復号しても同じになる。
        byte[] firstHalf = encrypt.update(Arrays.copyOfRange(plain, 0, 5));
        byte[] secondHalf = encrypt.update(Arrays.copyOfRange(plain, 5, plain.length));
        byte[] cipherText = new byte[firstHalf.length + secondHalf.length];
        System.arraycopy(firstHalf, 0, cipherText, 0, firstHalf.length);
        System.arraycopy(secondHalf, 0, cipherText, firstHalf.length, secondHalf.length);
        assertFalse(Arrays.equals(plain, cipherText));
        byte[] backFirst = decrypt.update(Arrays.copyOfRange(cipherText, 0, 3));
        byte[] backSecond = decrypt.update(Arrays.copyOfRange(cipherText, 3, cipherText.length));
        byte[] back = new byte[backFirst.length + backSecond.length];
        System.arraycopy(backFirst, 0, back, 0, backFirst.length);
        System.arraycopy(backSecond, 0, back, backFirst.length, backSecond.length);
        assertArrayEquals(plain, back);
    }

    @Test
    void onlineLoginAuthAcceptsCorrectTokenAndRejectsWrongToken() {
        OnlineLoginAuth attempt = OnlineLoginAuth.begin("Steve");
        byte[] request = attempt.encryptionRequestPayload();
        EncryptionRequest parsed = MojangAuth.parseEncryptionRequest(request);
        byte[] secret = new byte[16];
        Arrays.fill(secret, (byte) 7);
        byte[] good = MojangAuth.buildEncryptionResponsePayload(
                secret, parsed.verifyToken(),
                MojangAuth.decodePublicKey(parsed.publicKey()));
        OnlineLoginAuth.Accepted accepted = attempt.acceptEncryptionResponse(good);
        assertArrayEquals(secret, accepted.sharedSecret());
        assertEquals(MojangAuth.serverIdHash("", secret, parsed.publicKey()),
                accepted.serverIdHash());

        // 違うトークンでは失敗する。
        OnlineLoginAuth other = OnlineLoginAuth.begin("Steve");
        EncryptionRequest otherParsed =
                MojangAuth.parseEncryptionRequest(other.encryptionRequestPayload());
        byte[] badToken = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        byte[] bad = MojangAuth.buildEncryptionResponsePayload(
                secret, badToken, MojangAuth.decodePublicKey(otherParsed.publicKey()));
        assertThrows(AuthException.class, () -> other.acceptEncryptionResponse(bad));
    }

    @Test
    void onlineLoginAuthRejectsSecondResponse() {
        OnlineLoginAuth attempt = OnlineLoginAuth.begin("Steve");
        EncryptionRequest parsed =
                MojangAuth.parseEncryptionRequest(attempt.encryptionRequestPayload());
        byte[] response = MojangAuth.buildEncryptionResponsePayload(
                MojangAuth.generateSharedSecret(), parsed.verifyToken(),
                MojangAuth.decodePublicKey(parsed.publicKey()));
        attempt.acceptEncryptionResponse(response);
        assertThrows(AuthException.class,
                () -> attempt.acceptEncryptionResponse(response));
    }
}
