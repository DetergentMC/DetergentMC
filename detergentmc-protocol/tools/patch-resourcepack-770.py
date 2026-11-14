#!/usr/bin/env python3
"""リソースパックの770対応を入れる（DetergentServer／yml）。

pack_format 55（リソースパック）／71（データパック）は Minecraft Wiki の
pack_format 表で確認した値。769の流儀をなぞる：769用が7番目に増えるのと
同じ形で、770用を増やす。
ResourcePackBuilder／ResourcePackServer／ResourcePackMode の770対応は
手書きで先に入れてある（このスクリプトは触らない）。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent  # detergentmc-protocol/
PROTOCOL = ROOT / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol"
CORE = ROOT.parent / "detergentmc-core" / "src" / "main" / "java" / "io" / "detergentmc" / "core"

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


# ---------------------------------------------------------------- DetergentServer
edit(DET, [
    ("resourcePack770 field", """\
    /** 769クライアントに送るリソースパック（pack_format 46）。無ければ null。 */
    private ResourcePack resourcePack769;""",
     """\
    /** 769クライアントに送るリソースパック（pack_format 46）。無ければ null。 */
    private ResourcePack resourcePack769;
    /** 770クライアントに送るリソースパック（pack_format 55）。無ければ null。 */
    private ResourcePack resourcePack770;"""),
    ("hostResourcePack javadoc", """\
     * 1.21 or 1.21.1, the 768 pack (pack_format 42) when it is
     * 1.21.2 or 1.21.3, the 769 pack (pack_format 46) when it is
     * 1.21.4, the 765 pack (pack_format 22) otherwise.
     * Native clients reach the vanilla pack untouched (relay mode), so it
     * must match the internal version; translated clients get the proxy's
     * per-client substitution instead. Falls back to the 765 pack when no
     * 763 / 764 / 766 / 767 / 768 / 769 pack was prepared.""",
     """\
     * 1.21 or 1.21.1, the 768 pack (pack_format 42) when it is
     * 1.21.2 or 1.21.3, the 769 pack (pack_format 46) when it is
     * 1.21.4, the 770 pack (pack_format 55) when it is
     * 1.21.5, the 765 pack (pack_format 22) otherwise.
     * Native clients reach the vanilla pack untouched (relay mode), so it
     * must match the internal version; translated clients get the proxy's
     * per-client substitution instead. Falls back to the 765 pack when no
     * 763 / 764 / 766 / 767 / 768 / 769 / 770 pack was prepared."""),
    ("hostResourcePack branch", """\
        if (serverProtocol == ProtocolVersion.V1_21_4 && resourcePack769 != null) {
            return resourcePack769;
        }
        return resourcePack;""",
     """\
        if (serverProtocol == ProtocolVersion.V1_21_4 && resourcePack769 != null) {
            return resourcePack769;
        }
        if (serverProtocol == ProtocolVersion.V1_21_5 && resourcePack770 != null) {
            return resourcePack770;
        }
        return resourcePack;"""),
    ("createClientProxy javadoc", """\
     * 766 for 1.20.5/1.20.6, 767 for 1.21/1.21.1, 768 for 1.21.2/1.21.3,
     *     769 for 1.21.4)
     * and the per-client packs are passed through, so the proxy""",
     """\
     * 766 for 1.20.5/1.20.6, 767 for 1.21/1.21.1, 768 for 1.21.2/1.21.3,
     *     769 for 1.21.4, 770 for 1.21.5)
     * and the per-client packs are passed through, so the proxy"""),
    ("createClientProxy pack769 tail", """\
     * (native 768 clients relay the vanilla pack untouched). The 769 pack is passed
     * unless the internal server is 1.21.4, for the same reason
     * (native 769 clients relay the vanilla pack untouched).""",
     """\
     * (native 768 clients relay the vanilla pack untouched). The 769 pack is passed
     * unless the internal server is 1.21.4, for the same reason
     * (native 769 clients relay the vanilla pack untouched). The 770 pack is passed
     * unless the internal server is 1.21.5, for the same reason
     * (native 770 clients relay the vanilla pack untouched)."""),
    ("createClientProxy pack770", """\
        ResourcePack packFor769Clients =
                serverProtocol == ProtocolVersion.V1_21_4 ? null : resourcePack769;
        return new ClientProxy(port, "127.0.0.1", internalPort, onlineMode,
                resourcePack763, resourcePack764, authConfig,
                packFor765Clients, packFor766Clients, packFor767Clients,
                packFor768Clients, packFor769Clients, serverProtocol);""",
     """\
        ResourcePack packFor769Clients =
                serverProtocol == ProtocolVersion.V1_21_4 ? null : resourcePack769;
        ResourcePack packFor770Clients =
                serverProtocol == ProtocolVersion.V1_21_5 ? null : resourcePack770;
        return new ClientProxy(port, "127.0.0.1", internalPort, onlineMode,
                resourcePack763, resourcePack764, authConfig,
                packFor765Clients, packFor766Clients, packFor767Clients,
                packFor768Clients, packFor769Clients, packFor770Clients, serverProtocol);"""),
    ("startResourcePack javadoc", """\
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
     * 1.21.2 or 1.21.3, the 769 pack when it is 1.21.4, the 765 pack otherwise.""",
     """\
     * 1.21.2 / 1.21.3 variant ({@code pack_format} 42, one pack for
     * both versions), a 1.21.4 variant ({@code pack_format} 46) and a
     * 1.21.5 variant ({@code pack_format} 55):
     * in loopback mode they are generated and served
     * alongside the 1.20.4 pack by the same HTTP server (see
     * {@link #resourcePack763()}, {@link #resourcePack764()},
     * {@link #resourcePack766()}, {@link #resourcePack767()},
     * {@link #resourcePack768()}, {@link #resourcePack769()} and
     * {@link #resourcePack770()}); in external mode they come from
     * {@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768},
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769} and
     * {@code resource-pack.url-770} / {@code resource-pack.sha1-770}
     * when both are valid. The proxy offers the 1.20.1 pack to 763
     * clients, the 1.20.2 pack to 764 clients, the 1.20.5 / 1.20.6 pack
     * to 766 clients, the 1.21 / 1.21.1 pack to 767 clients, the
     * 1.21.2 / 1.21.3 pack to 768 clients, the 1.21.4 pack to 769
     * clients, the 1.21.5 pack to 770 clients, and the 1.20.4 pack to 765
     * clients. The pack written into {@code server.properties} is chosen by
     * {@link #hostResourcePack()}: the 764 pack when the vanilla server
     * itself is 1.20.2, the 766 pack when it is 1.20.5 or 1.20.6, the
     * 767 pack when it is 1.21 or 1.21.1, the 768 pack when it is
     * 1.21.2 or 1.21.3, the 769 pack when it is 1.21.4, the 770 pack
     * when it is 1.21.5, the 765 pack otherwise."""),
    ("decide call url-770", """\
                        config.getString("resource-pack.url-769", ""),
                        config.getString("resource-pack.sha1-769", ""),
                        required, promptKey);""",
     """\
                        config.getString("resource-pack.url-769", ""),
                        config.getString("resource-pack.sha1-769", ""),
                        config.getString("resource-pack.url-770", ""),
                        config.getString("resource-pack.sha1-770", ""),
                        required, promptKey);"""),
    ("EXTERNAL resourcePack769 null", """\
                    resourcePack768 = null;
                    resourcePack769 = null;
                    return null;""",
     """\
                    resourcePack768 = null;
                    resourcePack769 = null;
                    resourcePack770 = null;
                    return null;"""),
    ("EXTERNAL resourcePack770 assignment", """\
                resourcePack769 = decision.pack769();
                if (resourcePack769 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack769.url()));
                }
                return resourcePack;""",
     """\
                resourcePack769 = decision.pack769();
                if (resourcePack769 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack769.url()));
                }
                resourcePack770 = decision.pack770();
                if (resourcePack770 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack770.url()));
                }
                return resourcePack;"""),
    ("LOOPBACK build770", """\
            // 1.20.4用（pack_format 22）と1.20.1用（pack_format 15）、
            // 1.20.2用（pack_format 18）、1.20.5／1.20.6用（pack_format 32、
            // 1パックで両バージョン共用）、1.21／1.21.1用（pack_format 34、
            // 1パックで両バージョン共用）、1.21.2／1.21.3用（pack_format 42、
            // 1パックで両バージョン共用）、1.21.4用（pack_format 46）の
            // 7つを生成し、同じHTTPサーバー（同じ25567番ポート）で配信する。
            // 765クライアントには前者、763クライアントには2番目、
            // 764クライアントには3番目、766クライアントには4番目、
            // 767クライアントには5番目、768クライアントには6番目、
            // 769クライアントには7番目をプロキシが出し分ける。""",
     """\
            // 1.20.4用（pack_format 22）と1.20.1用（pack_format 15）、
            // 1.20.2用（pack_format 18）、1.20.5／1.20.6用（pack_format 32、
            // 1パックで両バージョン共用）、1.21／1.21.1用（pack_format 34、
            // 1パックで両バージョン共用）、1.21.2／1.21.3用（pack_format 42、
            // 1パックで両バージョン共用）、1.21.4用（pack_format 46）、
            // 1.21.5用（pack_format 55）の8つを生成し、同じHTTPサーバー
            // （同じ25567番ポート）で配信する。765クライアントには前者、
            // 763クライアントには2番目、764クライアントには3番目、
            // 766クライアントには4番目、767クライアントには5番目、
            // 768クライアントには6番目、769クライアントには7番目、
            // 770クライアントには8番目をプロキシが出し分ける。"""),
    ("LOOPBACK built770", """\
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
            resourcePack769 = resourcePackServer.pack769();""",
     """\
            ResourcePackBuilder.BuiltPack built769 = new ResourcePackBuilder()
                    .build769(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built770 = new ResourcePackBuilder()
                    .build770(workingDir.resolve("data").resolve("resource-pack"));
            resourcePackServer = new ResourcePackServer(
                    built, built763, built764, built766, built767, built768, built769, built770,
                    port, required,
                    promptKey.isBlank()
                            ? ResourcePackServer.DEFAULT_PROMPT_KEY : promptKey.trim());
            resourcePack = resourcePackServer.start();
            resourcePack763 = resourcePackServer.pack763();
            resourcePack764 = resourcePackServer.pack764();
            resourcePack766 = resourcePackServer.pack766();
            resourcePack767 = resourcePackServer.pack767();
            resourcePack768 = resourcePackServer.pack768();
            resourcePack769 = resourcePackServer.pack769();
            resourcePack770 = resourcePackServer.pack770();"""),
    ("generateDatapack comment", """\
            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4.""",
     """\
            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5."""),
    ("stopResourcePack", """\
        resourcePack768 = null;
        resourcePack769 = null;
    }""",
     """\
        resourcePack768 = null;
        resourcePack769 = null;
        resourcePack770 = null;
    }"""),
    ("resourcePack770 getter", """\
    /**
     * The 1.21.4 resource pack offered to 769 clients, or
     * {@code null} (protocol 769, pack_format 46).
     */
    public ResourcePack resourcePack769() {
        return resourcePack769;
    }""",
     """\
    /**
     * The 1.21.4 resource pack offered to 769 clients, or
     * {@code null} (protocol 769, pack_format 46).
     */
    public ResourcePack resourcePack769() {
        return resourcePack769;
    }

    /**
     * The 1.21.5 resource pack offered to 770 clients, or
     * {@code null} (protocol 770, pack_format 55).
     */
    public ResourcePack resourcePack770() {
        return resourcePack770;
    }"""),
])

# ---------------------------------------------------------------- detergentmc.yml
edit(YML, [
    ("yml 768 count", """\
  # ループバック配信では無視されます（6つとも自動生成・配信します）。""",
     """\
  # ループバック配信では無視されます（8つとも自動生成・配信します）。"""),
    ("url-770/sha1-770", """\
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
  sha1-769: """"",
     """\
  # 1.21.4 pack for 769 clients (optional; external mode only).
  # Same rules as url-768 / sha1-768, but the hosted pack must be
  # pack_format 46 (the 1.21.2 pack, format 42, is NOT shared with
  # 769 clients: a 1.21.4 client shows it as incompatible).
  # Leave both empty to offer the 1.20.4 pack to 769 clients.
  # In loopback mode these two are ignored: all eight variants are
  # generated and served locally.
  # 769クライアント向けの1.21.4用パック（任意・外部配信時のみ）。
  # url-768 / sha1-768 と同じ決まりですが、ホストするパックは
  # pack_format 46 のものが必要です。1.21.2用パック（format 42）は
  # 769クライアントには流用しません（「互換性なし」と表示されます）。
  # 空のままなら769クライアントにも1.20.4用パックを出します。
  # ループバック配信では無視されます（8つとも自動生成・配信します）。
  url-769: ""
  sha1-769: ""
  # 1.21.5 pack for 770 clients (optional; external mode only).
  # Same rules as url-769 / sha1-769, but the hosted pack must be
  # pack_format 55 (the 1.21.4 pack, format 46, is NOT shared with
  # 770 clients: a 1.21.5 client shows it as incompatible).
  # Leave both empty to offer the 1.20.4 pack to 770 clients.
  # In loopback mode these two are ignored: all eight variants are
  # generated and served locally.
  # 770クライアント向けの1.21.5用パック（任意・外部配信時のみ）。
  # url-769 / sha1-769 と同じ決まりですが、ホストするパックは
  # pack_format 55 のものが必要です。1.21.4用パック（format 46）は
  # 770クライアントには流用しません（「互換性なし」と表示されます）。
  # 空のままなら770クライアントにも1.20.4用パックを出します。
  # ループバック配信では無視されます（8つとも自動生成・配信します）。
  url-770: ""
  sha1-770: """""),
])

print("DetergentServer／detergentmc.yml の770対応を入れた")
