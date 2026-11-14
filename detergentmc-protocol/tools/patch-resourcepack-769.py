#!/usr/bin/env python3
"""リソースパックの769対応を入れる（Builder／Mode／Server／DetergentServer／yml）。

pack_format 46（リソースパック）／61（データパック）は Minecraft Wiki の
pack_format 表で確認した値。768の流儀をなぞる：768用が7番目に増えるのと
同じ形で、769用を増やす。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent  # detergentmc-protocol/
PROTOCOL = ROOT / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol"
CORE = ROOT.parent / "detergentmc-core" / "src" / "main" / "java" / "io" / "detergentmc" / "core"

BUILDER = PROTOCOL / "ResourcePackBuilder.java"
MODE = PROTOCOL / "ResourcePackMode.java"
SERVER = PROTOCOL / "ResourcePackServer.java"
DET = CORE / "DetergentServer.java"
YML = ROOT.parent / "detergentmc-core" / "src" / "main" / "resources" / "detergentmc.yml"


def edit(path, pairs):
    src = path.read_text(encoding="utf-8")
    for name, old, new in pairs:
        n = src.count(old)
        if n != 1:
            print(f"NG: {path.name} {name}: anchor が {n} 回見つかった（1回のはず）",
                  file=sys.stderr)
            sys.exit(1)
        src = src.replace(old, new, 1)
        print(f"OK: {path.name} {name}")
    path.write_text(src, encoding="utf-8")


# ---------------------------------------------------------------- ResourcePackBuilder
# ---------------------------------------------------------------- ResourcePackServer
# ---------------------------------------------------------------- DetergentServer
edit(DET, [
    ("resourcePack769 field", """\
    /** 768クライアントに送るリソースパック（pack_format 42）。無ければ null。1.21.2と1.21.3で共用。 */
    private ResourcePack resourcePack768;""",
     """\
    /** 768クライアントに送るリソースパック（pack_format 42）。無ければ null。1.21.2と1.21.3で共用。 */
    private ResourcePack resourcePack768;
    /** 769クライアントに送るリソースパック（pack_format 46）。無ければ null。 */
    private ResourcePack resourcePack769;"""),
    ("hostResourcePack javadoc", """\
     * {@code server.properties}: the 763 pack (pack_format 15) when the
     * internal server is 1.20/1.20.1, the 764 pack (pack_format 18) when
     * the internal server is 1.20.2, the 766 pack (pack_format 32) when it
     * is 1.20.5 or 1.20.6, the 767 pack (pack_format 34) when it is
     * 1.21 or 1.21.1, the 765 pack (pack_format 22) otherwise.
     * Native clients reach the vanilla pack untouched (relay mode), so it
     * must match the internal version; translated clients get the proxy's
     * per-client substitution instead. Falls back to the 765 pack when no
     * 763 / 764 / 766 / 767 pack was prepared.""",
     """\
     * {@code server.properties}: the 763 pack (pack_format 15) when the
     * internal server is 1.20/1.20.1, the 764 pack (pack_format 18) when
     * the internal server is 1.20.2, the 766 pack (pack_format 32) when it
     * is 1.20.5 or 1.20.6, the 767 pack (pack_format 34) when it is
     * 1.21 or 1.21.1, the 768 pack (pack_format 42) when it is
     * 1.21.2 or 1.21.3, the 769 pack (pack_format 46) when it is
     * 1.21.4, the 765 pack (pack_format 22) otherwise.
     * Native clients reach the vanilla pack untouched (relay mode), so it
     * must match the internal version; translated clients get the proxy's
     * per-client substitution instead. Falls back to the 765 pack when no
     * 763 / 764 / 766 / 767 / 768 / 769 pack was prepared."""),
    ("hostResourcePack branch", """\
        if (serverProtocol == ProtocolVersion.V1_21_2 && resourcePack768 != null) {
            return resourcePack768;
        }
        return resourcePack;""",
     """\
        if (serverProtocol == ProtocolVersion.V1_21_2 && resourcePack768 != null) {
            return resourcePack768;
        }
        if (serverProtocol == ProtocolVersion.V1_21_4 && resourcePack769 != null) {
            return resourcePack769;
        }
        return resourcePack;"""),
    ("createClientProxy javadoc", """\
     * 766 for 1.20.5/1.20.6, 767 for 1.21/1.21.1, 768 for 1.21.2/1.21.3)
     * and the per-client packs are passed through, so the proxy
     * translates toward the running vanilla version instead of
     * assuming 765. The 765 pack is only passed when the internal
     * server is 1.20/1.20.1 or 1.20.2 — with an internal 765 server the vanilla pack
     * goes through untouched, so {@code null} keeps that path exactly
     * as before. The 766 pack is passed unless the internal server is
     * 1.20.5/1.20.6, for the same reason (native 766 clients relay
     * the vanilla pack untouched). The 767 pack is passed unless the
     * internal server is 1.21/1.21.1, for the same reason (native 767
     * clients relay the vanilla pack untouched). The 768 pack is passed
     * unless the internal server is 1.21.2/1.21.3, for the same reason
     * (native 768 clients relay the vanilla pack untouched).""",
     """\
     * 766 for 1.20.5/1.20.6, 767 for 1.21/1.21.1, 768 for 1.21.2/1.21.3,
     *     769 for 1.21.4)
     * and the per-client packs are passed through, so the proxy
     * translates toward the running vanilla version instead of
     * assuming 765. The 765 pack is only passed when the internal
     * server is 1.20/1.20.1 or 1.20.2 — with an internal 765 server the vanilla pack
     * goes through untouched, so {@code null} keeps that path exactly
     * as before. The 766 pack is passed unless the internal server is
     * 1.20.5/1.20.6, for the same reason (native 766 clients relay
     * the vanilla pack untouched). The 767 pack is passed unless the
     * internal server is 1.21/1.21.1, for the same reason (native 767
     * clients relay the vanilla pack untouched). The 768 pack is passed
     * unless the internal server is 1.21.2/1.21.3, for the same reason
     * (native 768 clients relay the vanilla pack untouched). The 769 pack is passed
     * unless the internal server is 1.21.4, for the same reason
     * (native 769 clients relay the vanilla pack untouched)."""),
    ("createClientProxy pack769", """\
        ResourcePack packFor768Clients =
                serverProtocol == ProtocolVersion.V1_21_2 ? null : resourcePack768;
        return new ClientProxy(port, "127.0.0.1", internalPort, onlineMode,
                resourcePack763, resourcePack764, authConfig,
                packFor765Clients, packFor766Clients, packFor767Clients,
                packFor768Clients, serverProtocol);""",
     """\
        ResourcePack packFor768Clients =
                serverProtocol == ProtocolVersion.V1_21_2 ? null : resourcePack768;
        ResourcePack packFor769Clients =
                serverProtocol == ProtocolVersion.V1_21_4 ? null : resourcePack769;
        return new ClientProxy(port, "127.0.0.1", internalPort, onlineMode,
                resourcePack763, resourcePack764, authConfig,
                packFor765Clients, packFor766Clients, packFor767Clients,
                packFor768Clients, packFor769Clients, serverProtocol);"""),
    ("startResourcePack javadoc", """\
     * <p>Both modes also prepare a 1.20.1 variant of the pack
     * ({@code pack_format} 15), a 1.20.2 variant ({@code pack_format}
     * 18), a 1.20.5 / 1.20.6 variant ({@code pack_format} 32, one
     * pack for both versions), a 1.21 / 1.21.1 variant
     * ({@code pack_format} 34, one pack for both versions) and a
     * 1.21.2 / 1.21.3 variant ({@code pack_format} 42, one pack for
     * both versions): in loopback mode they are generated and served
     * alongside the 1.20.4 pack by the same HTTP server (see
     * {@link #resourcePack763()}, {@link #resourcePack764()},
     * {@link #resourcePack766()}, {@link #resourcePack767()} and
     * {@link #resourcePack768()}); in external mode they come from
     * {@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767} and
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768}
     * when both are valid. The proxy offers the 1.20.1 pack to 763
     * clients, the 1.20.2 pack to 764 clients, the 1.20.5 / 1.20.6 pack
     * to 766 clients, the 1.21 / 1.21.1 pack to 767 clients, the
     * 1.21.2 / 1.21.3 pack to 768 clients, and the 1.20.4 pack to 765
     * clients. The pack written into {@code server.properties} is chosen by
     * {@link #hostResourcePack()}: the 764 pack when the vanilla server
     * itself is 1.20.2, the 766 pack when it is 1.20.5 or 1.20.6, the
     * 767 pack when it is 1.21 or 1.21.1, the 768 pack when it is
     * 1.21.2 or 1.21.3, the 765 pack otherwise.""",
     """\
     * <p>Both modes also prepare a 1.20.1 variant of the pack
     * ({@code pack_format} 15), a 1.20.2 variant ({@code pack_format}
     * 18), a 1.20.5 / 1.20.6 variant ({@code pack_format} 32, one
     * pack for both versions), a 1.21 / 1.21.1 variant
     * ({@code pack_format} 34, one pack for both versions), a
     * 1.21.2 / 1.21.3 variant ({@code pack_format} 42, one pack for
     * both versions) and a 1.21.4 variant ({@code pack_format} 46):
     * in loopback mode they are generated and served
     * alongside the 1.20.4 pack by the same HTTP server (see
     * {@link #resourcePack763()}, {@link #resourcePack764()},
     * {@link #resourcePack766()}, {@link #resourcePack767()},
     * {@link #resourcePack768()} and {@link #resourcePack769()}); in external mode they come from
     * {@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768} and
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769}
     * when both are valid. The proxy offers the 1.20.1 pack to 763
     * clients, the 1.20.2 pack to 764 clients, the 1.20.5 / 1.20.6 pack
     * to 766 clients, the 1.21 / 1.21.1 pack to 767 clients, the
     * 1.21.2 / 1.21.3 pack to 768 clients, the 1.21.4 pack to 769
     * clients, and the 1.20.4 pack to 765
     * clients. The pack written into {@code server.properties} is chosen by
     * {@link #hostResourcePack()}: the 764 pack when the vanilla server
     * itself is 1.20.2, the 766 pack when it is 1.20.5 or 1.20.6, the
     * 767 pack when it is 1.21 or 1.21.1, the 768 pack when it is
     * 1.21.2 or 1.21.3, the 769 pack when it is 1.21.4, the 765 pack otherwise."""),
    ("decide call url-769", """\
                        config.getString("resource-pack.url-768", ""),
                        config.getString("resource-pack.sha1-768", ""),
                        required, promptKey);""",
     """\
                        config.getString("resource-pack.url-768", ""),
                        config.getString("resource-pack.sha1-768", ""),
                        config.getString("resource-pack.url-769", ""),
                        config.getString("resource-pack.sha1-769", ""),
                        required, promptKey);"""),
    ("EXTERNAL resourcePack768 null", """\
                    resourcePack = null;
                    resourcePack763 = null;
                    resourcePack764 = null;
                    resourcePack766 = null;
                    resourcePack767 = null;
                    resourcePack768 = null;
                    return null;
                }""",
     """\
                    resourcePack = null;
                    resourcePack763 = null;
                    resourcePack764 = null;
                    resourcePack766 = null;
                    resourcePack767 = null;
                    resourcePack768 = null;
                    resourcePack769 = null;
                    return null;
                }"""),
    ("EXTERNAL resourcePack769 assignment", """\
                resourcePack768 = decision.pack768();
                if (resourcePack768 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack768.url()));
                }
                return resourcePack;""",
     """\
                resourcePack768 = decision.pack768();
                if (resourcePack768 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack768.url()));
                }
                resourcePack769 = decision.pack769();
                if (resourcePack769 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack769.url()));
                }
                return resourcePack;"""),
    ("LOOPBACK build769", """\
            // 1.20.4用（pack_format 22）と1.20.1用（pack_format 15）、
            // 1.20.2用（pack_format 18）、1.20.5／1.20.6用（pack_format 32、
            // 1パックで両バージョン共用）、1.21／1.21.1用（pack_format 34、
            // 1パックで両バージョン共用）、1.21.2／1.21.3用（pack_format 42、
            // 1パックで両バージョン共用）の6つを生成し、同じHTTPサーバー
            // （同じ25567番ポート）で配信する。765クライアントには前者、
            // 763クライアントには2番目、764クライアントには3番目、
            // 766クライアントには4番目、767クライアントには5番目、
            // 768クライアントには6番目をプロキシが出し分ける。""",
     """\
            // 1.20.4用（pack_format 22）と1.20.1用（pack_format 15）、
            // 1.20.2用（pack_format 18）、1.20.5／1.20.6用（pack_format 32、
            // 1パックで両バージョン共用）、1.21／1.21.1用（pack_format 34、
            // 1パックで両バージョン共用）、1.21.2／1.21.3用（pack_format 42、
            // 1パックで両バージョン共用）、1.21.4用（pack_format 46）の
            // 7つを生成し、同じHTTPサーバー（同じ25567番ポート）で配信する。
            // 765クライアントには前者、763クライアントには2番目、
            // 764クライアントには3番目、766クライアントには4番目、
            // 767クライアントには5番目、768クライアントには6番目、
            // 769クライアントには7番目をプロキシが出し分ける。"""),
    ("LOOPBACK built769", """\
            ResourcePackBuilder.BuiltPack built768 = new ResourcePackBuilder()
                    .build768(workingDir.resolve("data").resolve("resource-pack"));
            resourcePackServer = new ResourcePackServer(
                    built, built763, built764, built766, built767, built768, port, required,
                    promptKey.isBlank()
                            ? ResourcePackServer.DEFAULT_PROMPT_KEY : promptKey.trim());
            resourcePack = resourcePackServer.start();
            resourcePack763 = resourcePackServer.pack763();
            resourcePack764 = resourcePackServer.pack764();
            resourcePack766 = resourcePackServer.pack766();
            resourcePack767 = resourcePackServer.pack767();
            resourcePack768 = resourcePackServer.pack768();""",
     """\
            ResourcePackBuilder.BuiltPack built768 = new ResourcePackBuilder()
                    .build768(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built769 = new ResourcePackBuilder()
                    .build769(workingDir.resolve("data").resolve("resource-pack"));
            resourcePackServer = new ResourcePackServer(
                    built, built763, built764, built766, built767, built768, built769,
                    port, required,
                    promptKey.isBlank()
                            ? ResourcePackServer.DEFAULT_PROMPT_KEY : promptKey.trim());
            resourcePack = resourcePackServer.start();
            resourcePack763 = resourcePackServer.pack763();
            resourcePack764 = resourcePackServer.pack764();
            resourcePack766 = resourcePackServer.pack766();
            resourcePack767 = resourcePackServer.pack767();
            resourcePack768 = resourcePackServer.pack768();
            resourcePack769 = resourcePackServer.pack769();"""),
    ("generateDatapack comment", """\
            // The datapack format follows the running vanilla version:
            // 15 for 1.20.1, 18 for 1.20.2, 26 for 1.20.4,
            // 41 for 1.20.5 / 1.20.6, 48 for 1.21 / 1.21.1,
            // 57 for 1.21.2 / 1.21.3.""",
     """\
            // The datapack format follows the running vanilla version:
            // 15 for 1.20.1, 18 for 1.20.2, 26 for 1.20.4,
            // 41 for 1.20.5 / 1.20.6, 48 for 1.21 / 1.21.1,
            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4."""),
    ("stopResourcePack", """\
        resourcePack = null;
        resourcePack763 = null;
        resourcePack764 = null;
        resourcePack766 = null;
        resourcePack767 = null;
        resourcePack768 = null;
    }""",
     """\
        resourcePack = null;
        resourcePack763 = null;
        resourcePack764 = null;
        resourcePack766 = null;
        resourcePack767 = null;
        resourcePack768 = null;
        resourcePack769 = null;
    }"""),
    ("resourcePack769 getter", """\
    /**
     * The 1.21.2 / 1.21.3 resource pack offered to 768 clients, or
     * {@code null}. One pack serves both versions (protocol 768,
     * pack_format 42).
     */
    public ResourcePack resourcePack768() {
        return resourcePack768;
    }""",
     """\
    /**
     * The 1.21.2 / 1.21.3 resource pack offered to 768 clients, or
     * {@code null}. One pack serves both versions (protocol 768,
     * pack_format 42).
     */
    public ResourcePack resourcePack768() {
        return resourcePack768;
    }

    /**
     * The 1.21.4 resource pack offered to 769 clients, or
     * {@code null} (protocol 769, pack_format 46).
     */
    public ResourcePack resourcePack769() {
        return resourcePack769;
    }"""),
])

# ---------------------------------------------------------------- detergentmc.yml
edit(YML, [
    ("url-769/sha1-769", """\
  url-768: ""
  sha1-768: """"",
     """\
  url-768: ""
  sha1-768: ""
  # 1.21.4 pack for 769 clients (optional; external mode only).
  # Same rules as url-768 / sha1-768, but the hosted pack must be
  # pack_format 46 (the 1.21.2 pack, format 42, is NOT shared with
  # 769 clients: a 1.21.4 client shows it as incompatible).
  # Leave both empty to offer the 1.20.4 pack to 769 clients.
  # In loopback mode these two are ignored: all seven variants are
  # generated and served locally.
  # 769クライアント向けの1.21.4用パック（任意・外部配信時のみ）。
  # url-768 / sha1-768 と同じ決まりですが、ホストするパックは
  # pack_format 46 のものが必要です。1.21.2用パック（format 42）は
  # 769クライアントには流用しません（「互換性なし」と表示されます）。
  # 空のままなら769クライアントにも1.20.4用パックを出します。
  # ループバック配信では無視されます（7つとも自動生成・配信します）。
  url-769: ""
  sha1-769: """""),
])

print("ResourcePackBuilder／ResourcePackMode／ResourcePackServer／DetergentServer／detergentmc.yml の769対応を入れた")
