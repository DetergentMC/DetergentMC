package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Login パケット（JWT チェーン）のパースのテスト。 */
class BedrockLoginTest {

    @Test
    void parsesDisplayNameXuidAndIdentity() {
        byte[] payload = TestJwt.loginPayload(630, "Steve", "2535461234567890",
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        BedrockLogin.LoginInfo login = BedrockLogin.parse(payload);
        assertEquals(630, login.protocolVersion());
        assertEquals("Steve", login.displayName());
        assertEquals("2535461234567890", login.xuid());
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", login.identityUuid());
        assertTrue(login.hasXuid());
        // 署名は検証しない（開発用の信頼済み扱い）ことが明示されていること。
        assertEquals(BedrockLogin.VerificationStatus.NOT_VERIFIED_TRUSTED,
                login.verificationStatus());
    }

    @Test
    void accepts12040Protocol() {
        byte[] payload = TestJwt.loginPayload(622, "Alex", "111", "11111111-2222-3333-4444-555555555555");
        BedrockLogin.LoginInfo login = BedrockLogin.parse(payload);
        assertEquals(622, login.protocolVersion());
        assertTrue(BedrockProtocol.isSupportedProtocol(622));
        assertTrue(BedrockProtocol.isSupportedProtocol(630));
        // 1.20.70（662）も 1.20.0〜26.51 の対応範囲に入る。
        assertTrue(BedrockProtocol.isSupportedProtocol(662));
        assertFalse(BedrockProtocol.isSupportedProtocol(588));
        assertTrue(BedrockProtocol.isSupportedProtocol(2225),
                "26.60（2225）はパケット形式として対応する（トランスポートは NetherNet）");
        assertTrue(BedrockProtocol.isNetherNetProtocol(2225),
                "26.60（2225）は NetherNet 世代");
    }

    @Test
    void missingXuidIsReported() {
        // extraData に XUID が無いチェーン。
        String chain = "{\"chain\":[\"" + TestJwt.jwtPlain("{}") + "\",\""
                + TestJwt.jwtWithExtraData("NoXuid", "", "00000000-0000-0000-0000-000000000000")
                + "\"]}";
        // XUID が空文字の場合は hasXuid() が false になること。
        BedrockLogin.LoginInfo login = BedrockLogin.parseIdentity(630, chain, "");
        assertEquals("NoXuid", login.displayName());
        assertFalse(login.hasXuid());
    }

    @Test
    void emptyChainIsMalformed() {
        BedrockLogin.LoginInfo login = BedrockLogin.parseIdentity(630, "{\"chain\":[]}", "");
        assertEquals(BedrockLogin.VerificationStatus.CHAIN_MALFORMED,
                login.verificationStatus());
        assertFalse(login.hasXuid());
    }

    @Test
    void jwtPayloadDecoding() {
        String jwt = TestJwt.jwtWithExtraData("Steve", "2535", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String payload = BedrockLogin.decodeJwtPayload(jwt);
        assertTrue(payload.contains("\"displayName\":\"Steve\""), payload);
        assertTrue(payload.contains("\"XUID\":\"2535\""), payload);
        assertNull(BedrockLogin.decodeJwtPayload("not-a-jwt"));
        assertNull(BedrockLogin.decodeJwtPayload(null));
    }

    @Test
    void jsonStringExtraction() {
        assertEquals("Steve", BedrockLogin.extractString(
                "{\"displayName\":\"Steve\",\"XUID\":\"123\"}", "displayName"));
        assertEquals("123", BedrockLogin.extractString(
                "{\"displayName\":\"Steve\",\"XUID\":\"123\"}", "XUID"));
        assertNull(BedrockLogin.extractString("{}", "displayName"));
        // エスケープされた文字列も読めること。
        assertEquals("a\"b", BedrockLogin.extractString(
                "{\"displayName\":\"a\\\"b\"}", "displayName"));
    }
}
