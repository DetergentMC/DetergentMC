package io.detergentmc.bedrock;

import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** テスト用の JWT／Login パケット組み立てヘルパー（本物の署名は無い）。 */
final class TestJwt {

    private TestJwt() {
    }

    /** extraData 入りの JWT（header.payload.signature。署名はダミー）。 */
    static String jwtWithExtraData(String displayName, String xuid, String identityUuid) {
        String header = base64Url("{\"alg\":\"ES384\",\"x5u\":\"https://example.invalid/key\"}");
        String payload = base64Url("{\"extraData\":{\"displayName\":\"" + displayName
                + "\",\"XUID\":\"" + xuid + "\",\"identity\":\"" + identityUuid + "\"}}");
        return header + "." + payload + ".c2ln";
    }

    /** extraData の無い JWT。 */
    static String jwtPlain(String json) {
        return base64Url("{\"alg\":\"ES384\"}") + "." + base64Url(json) + ".c2ln";
    }

    static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** identity チェーン JSON（3 個の JWT。末尾に extraData あり）。 */
    static String chainJson(String displayName, String xuid, String identityUuid) {
        return "{\"chain\":[\"" + jwtPlain("{}") + "\",\""
                + jwtPlain("{}") + "\",\""
                + jwtWithExtraData(displayName, xuid, identityUuid) + "\"]}";
    }

    /** Login パケットのペイロード（ID の後ろ）を作る。 */
    static byte[] loginPayload(int protocol, String displayName, String xuid,
            String identityUuid) {
        ByteWriter out = new ByteWriter();
        out.writeBigInt(protocol);
        ByteWriter tokens = new ByteWriter();
        BedrockVarInt.writeString(tokens, chainJson(displayName, xuid, identityUuid));
        BedrockVarInt.writeString(tokens, jwtPlain(
                "{\"DeviceOS\":7,\"GameVersion\":\"1.20.50\",\"LanguageCode\":\"ja_JP\"}"));
        byte[] tokenBytes = tokens.bytes();
        BedrockVarInt.writeUnsignedVarInt(out, tokenBytes.length);
        out.writeBytes(tokenBytes);
        return out.bytes();
    }

    /** Login パケット 1 個（ID 0x01 を含むバッチ用ペイロード）。 */
    static byte[] loginPacket(int protocol, String displayName, String xuid,
            String identityUuid) {
        byte[] payload = loginPayload(protocol, displayName, xuid, identityUuid);
        byte[] out = new byte[1 + payload.length];
        out[0] = (byte) BedrockProtocol.PACKET_LOGIN;
        System.arraycopy(payload, 0, out, 1, payload.length);
        return out;
    }
}
