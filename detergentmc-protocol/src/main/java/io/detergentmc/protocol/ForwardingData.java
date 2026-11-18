package io.detergentmc.protocol;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Surfactantからの転送（Forwarding）データの検証と読み取り。
 *
 * <p>Surfactant（プロキシ）が、ログイン段階のプラグイン message
 * {@code surfactant:forwarding} への応答として送ってくる、署名付きの
 * プレイヤー情報。DetergentMCは共有シークレット
 * （{@code detergentmc.yml} の {@code forwarding.secret}）で署名を検証し、
 * 合ったものだけを「Surfactantが保証した素性」として採用する
 * （Velocityのモダン転送と同じ考え方。ワイヤ形式はSurfactant独自の
 * v1形式で、Velocityとはバイト互換ではない）。
 *
 * <p>ワイヤ形式（v1）：「バージョン1バイト（0x01）＋HMAC-SHA256署名
 * 32バイト＋JSON（UTF-8）」。JSONは
 * <pre>{"address":"127.0.0.1:54321","uuid":"...","name":"Steve","properties":[{"name":"textures","value":"...","signature":"..."}]}</pre>
 * で、{@code properties} は認証済みの場合だけ付く（{@code signature} は
 * 無い場合に省略される）。署名の対象はJSONそのもの。
 *
 * <p>このクラスは形式の検証だけを担う。検証を通った素性をセッションへ
 * 採用する配線は {@link ProxySession} 側にある。Surfactant側の組み立て
 * （署名）とは独立に、形式の仕様として実装している。
 *
 * @param address 実クライアントの接続元（「IP:ポート」）
 * @param uuid プレイヤーのUUID（文字列形式）
 * @param name プレイヤー名
 * @param properties プロファイルプロパティ（認証済みの場合。無ければ空）
 */
public record ForwardingData(String address, String uuid, String name,
        List<AuthenticatedProfile.Property> properties) {

    /** 転送確認のチャンネル名（Surfactant側と揃える）。 */
    public static final String CHANNEL = "surfactant:forwarding";

    /** ワイヤ形式のバージョン。 */
    public static final int VERSION = 1;

    /** 署名の長さ（HMAC-SHA256）。 */
    private static final int SIGNATURE_LENGTH = 32;

    public ForwardingData {
        properties = properties == null ? List.of() : List.copyOf(properties);
    }

    /**
     * ワイヤ形式を検証し、署名が合えば中身を返す。バージョン違い・
     * 署名不一致・形式不正・必須項目の欠けはすべて空を返す
     * （呼び出し側は理由を区別しない。安全側の扱いは呼び出し側の役目）。
     */
    public static Optional<ForwardingData> verify(String secret, byte[] wire) {
        if (secret == null || secret.isBlank()
                || wire == null || wire.length < 1 + SIGNATURE_LENGTH
                || wire[0] != (byte) VERSION) {
            return Optional.empty();
        }
        byte[] signature = new byte[SIGNATURE_LENGTH];
        System.arraycopy(wire, 1, signature, 0, SIGNATURE_LENGTH);
        byte[] data = new byte[wire.length - 1 - SIGNATURE_LENGTH];
        System.arraycopy(wire, 1 + SIGNATURE_LENGTH, data, 0, data.length);
        if (!MessageDigest.isEqual(sign(secret, data), signature)) {
            return Optional.empty();
        }
        Nbt.Tag root;
        try {
            root = JsonToNbt.fromJson(new String(data, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (!(root instanceof Nbt.Tag.CompoundTag compound)) {
            return Optional.empty();
        }
        Optional<String> address = compound.getString("address");
        Optional<String> uuid = compound.getString("uuid");
        Optional<String> name = compound.getString("name");
        if (address.isEmpty() || uuid.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }
        List<AuthenticatedProfile.Property> properties = new ArrayList<>();
        Optional<Nbt.Tag.ListTag> list = compound.getList("properties");
        if (list.isPresent()) {
            for (Nbt.Tag element : list.get().value()) {
                if (!(element instanceof Nbt.Tag.CompoundTag property)) {
                    continue;
                }
                Optional<String> propertyName = property.getString("name");
                Optional<String> propertyValue = property.getString("value");
                if (propertyName.isEmpty() || propertyValue.isEmpty()) {
                    continue;
                }
                properties.add(new AuthenticatedProfile.Property(
                        propertyName.get(), propertyValue.get(),
                        property.getString("signature").orElse(null)));
            }
        }
        return Optional.of(new ForwardingData(
                address.get(), uuid.get(), name.get(), properties));
    }

    /** HMAC-SHA256で署名する（秘密はUTF-8）。 */
    private static byte[] sign(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256が使えません。", e);
        }
    }
}
