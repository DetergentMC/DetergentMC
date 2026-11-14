package io.detergentmc.protocol;

import java.util.List;
import java.util.UUID;

/**
 * Mojangセッションサーバーの hasJoined が返した、認証済みプレイヤー。
 *
 * <p>{@code properties} はスキン等（主に {@code textures}）で、Login
 * Success に入れてクライアントへ渡す。内部バニラは offline-mode のため
 * プロパティを自分では取りに行かない。スキンの扱いの詳細は
 * docs/PROXY-1.20.1.md を参照（自分のスキンはクライアント自身の
 * Mojangセッションから表示される。他のプレイヤーから見たスキンは
 * Player Info Update への注入が未実装のため既定スキンになる）。
 */
public record AuthenticatedProfile(UUID uuid, String name, List<Property> properties) {

    /** プロパティ1個（名前・値・任意の署名）。 */
    public record Property(String name, String value, String signature) {
    }

    public AuthenticatedProfile {
        properties = properties == null ? List.of() : List.copyOf(properties);
    }

    /** プロパティがあるか。 */
    public boolean hasProperties() {
        return !properties.isEmpty();
    }
}
