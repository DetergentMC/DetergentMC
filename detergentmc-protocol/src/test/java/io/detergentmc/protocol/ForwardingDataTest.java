package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * {@link ForwardingData} の検証テスト。ワイヤはテスト側で
 * {@link javax.crypto.Mac} を使って独立に組み立てる
 * （検証対象のコードで署名しない＝仕様どおりかを確かめる）。
 * 仕様は Surfactant 側 ForwardingSigner と同じ v1 形式：
 * バージョン1バイト＋HMAC-SHA256署名32バイト＋JSON。
 */
class ForwardingDataTest {

    private static final String SECRET = "test-secret";

    /** テスト側で独立に署名してワイヤを作る。 */
    private static byte[] wire(String secret, String json) throws Exception {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(jsonBytes);
        byte[] wire = new byte[1 + signature.length + jsonBytes.length];
        wire[0] = 1;
        System.arraycopy(signature, 0, wire, 1, signature.length);
        System.arraycopy(jsonBytes, 0, wire, 1 + signature.length, jsonBytes.length);
        return wire;
    }

    @Test
    void verifiesPayloadWithProperties() throws Exception {
        String json = "{\"address\":\"203.0.113.7:51234\","
                + "\"uuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\","
                + "\"name\":\"FwdSteve\",\"properties\":["
                + "{\"name\":\"textures\",\"value\":\"abc\",\"signature\":\"xyz\"},"
                + "{\"name\":\"cape\",\"value\":\"def\"}]}";
        Optional<ForwardingData> verified = ForwardingData.verify(SECRET, wire(SECRET, json));
        assertTrue(verified.isPresent());
        ForwardingData data = verified.get();
        assertEquals("203.0.113.7:51234", data.address());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", data.uuid());
        assertEquals("FwdSteve", data.name());
        assertEquals(2, data.properties().size());
        assertEquals("textures", data.properties().get(0).name());
        assertEquals("abc", data.properties().get(0).value());
        assertEquals("xyz", data.properties().get(0).signature());
        assertEquals("cape", data.properties().get(1).name());
        assertEquals(null, data.properties().get(1).signature());
    }

    @Test
    void verifiesPayloadWithoutProperties() throws Exception {
        String json = "{\"address\":\"127.0.0.1:1000\","
                + "\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Alex\"}";
        Optional<ForwardingData> verified = ForwardingData.verify(SECRET, wire(SECRET, json));
        assertTrue(verified.isPresent());
        assertTrue(verified.get().properties().isEmpty());
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        byte[] wire = wire(SECRET, "{\"address\":\"127.0.0.1:1\","
                + "\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Alex\"}");
        wire[wire.length - 2] ^= 0x01; // JSONの末尾近くを壊す
        assertTrue(ForwardingData.verify(SECRET, wire).isEmpty());
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        String json = "{\"address\":\"127.0.0.1:1\","
                + "\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Alex\"}";
        assertTrue(ForwardingData.verify(SECRET, wire("other-secret", json)).isEmpty());
    }

    @Test
    void rejectsWrongVersionAndShortInput() throws Exception {
        byte[] wire = wire(SECRET, "{\"address\":\"a\",\"uuid\":\"u\",\"name\":\"n\"}");
        wire[0] = 2;
        assertTrue(ForwardingData.verify(SECRET, wire).isEmpty());
        assertTrue(ForwardingData.verify(SECRET, new byte[10]).isEmpty());
        assertTrue(ForwardingData.verify(SECRET, null).isEmpty());
    }

    @Test
    void rejectsMissingFieldsAndBlankSecret() throws Exception {
        assertTrue(ForwardingData.verify(SECRET,
                wire(SECRET, "{\"address\":\"a\",\"name\":\"n\"}")).isEmpty());
        String json = "{\"address\":\"a\",\"uuid\":\"u\",\"name\":\"n\"}";
        assertTrue(ForwardingData.verify("", wire(SECRET, json)).isEmpty());
        assertTrue(ForwardingData.verify(null, wire(SECRET, json)).isEmpty());
    }
}
