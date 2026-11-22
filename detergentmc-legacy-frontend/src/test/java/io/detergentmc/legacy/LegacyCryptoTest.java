package io.detergentmc.legacy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyCryptoTest {

    @Test
    void rsaRoundTrip() throws Exception {
        KeyPair pair = LegacyCrypto.generateKeyPair();
        byte[] secret = LegacyCrypto.randomBytes(16);
        byte[] encrypted = LegacyCrypto.rsaEncrypt(pair.getPublic(), secret);
        assertArrayEquals(secret, LegacyCrypto.rsaDecrypt(pair.getPrivate(), encrypted));
    }

    @Test
    void publicKeyBytesParseBack() throws Exception {
        KeyPair pair = LegacyCrypto.generateKeyPair();
        byte[] encoded = LegacyCrypto.publicKeyBytes(pair.getPublic());
        // The vanilla 1.6.4 key encodes to 162 bytes.
        assertEquals(162, encoded.length);
        assertEquals(pair.getPublic(), LegacyCrypto.parsePublicKey(encoded));
    }

    @Test
    void cfb8StreamsStayInSyncByteByByte() {
        byte[] secret = LegacyCrypto.randomBytes(16);
        LegacyCrypto.StreamCipher enc = LegacyCrypto.encryptor(secret);
        LegacyCrypto.StreamCipher dec = LegacyCrypto.decryptor(secret);
        byte[] plain = new byte[1024];
        new Random(42).nextBytes(plain);
        ByteArrayOutputStream roundTripped = new ByteArrayOutputStream();
        // Feed the decryptor one byte at a time: CFB8 must not buffer.
        for (int i = 0; i < plain.length; i++) {
            byte[] cipherByte = enc.process(new byte[]{plain[i]});
            byte[] back = dec.process(cipherByte);
            roundTripped.write(back, 0, back.length);
        }
        assertArrayEquals(plain, roundTripped.toByteArray());
    }

    @Test
    void cfb8ChunkedMatchesWhole() {
        byte[] secret = LegacyCrypto.randomBytes(16);
        byte[] plain = new byte[500];
        new Random(7).nextBytes(plain);
        byte[] whole = LegacyCrypto.encryptor(secret).process(plain);
        LegacyCrypto.StreamCipher enc = LegacyCrypto.encryptor(secret);
        ByteArrayOutputStream chunked = new ByteArrayOutputStream();
        int[] sizes = {1, 7, 100, 3, 389};
        int pos = 0;
        for (int size : sizes) {
            byte[] part = enc.process(plain, pos, size);
            chunked.write(part, 0, part.length);
            pos += size;
        }
        assertEquals(500, pos);
        assertArrayEquals(whole, chunked.toByteArray());
        // And the ciphertext differs from plaintext somewhere.
        assertEquals(false, Arrays.equals(plain, whole));
    }
}
