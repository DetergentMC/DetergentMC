#!/usr/bin/env python3
"""リソースパックの771対応を入れる（DetergentServer／yml）。

pack_format 63（リソースパック）／80（データパック）は Minecraft Wiki の
pack_format 表で確認した値。770の流儀をなぞる：770用が8番目に増えるのと
同じ形で、771用を増やす。
ResourcePackBuilder／ResourcePackServer／ResourcePackMode の771対応は
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
    ("resourcePack771 field", """\
    /** 770クライアントに送るリソースパック（pack_format 55）。無ければ null。 */
    private ResourcePack resourcePack770;""",
     """\
    /** 770クライアントに送るリソースパック（pack_format 55）。無ければ null。 */
    private ResourcePack resourcePack770;
    /** 771クライアントに送るリソースパック（pack_format 63）。無ければ null。 */
    private ResourcePack resourcePack771;"""),
    ("hostResourcePack javadoc", """\
     * 1.21 or 1.21.1, the 768 pack (pack_format 42) when it is
     * 1.21.2 or 1.21.3, the 769 pack (pack_format 46) when it is
     * 1.21.4, the 770 pack (pack_format 55) when it is
     * 1.21.5, the 765 pack (pack_format 22) otherwise.
     * Native clients reach the vanilla pack untouched (relay mode), so it
     * must match the internal version; translated clients get the proxy's
     * per-client substitution instead. Falls back to the 765 pack when no
     * 763 / 764 / 766 / 767 / 768 / 769 / 770 pack was prepared.""",
     """\
     * 1.21 or 1.21.1, the 768 pack (pack_format 42) when it is
     * 1.21.2 or 1.21.3, the 769 pack (pack_format 46) when it is
     * 1.21.4, the 770 pack (pack_format 55) when it is
     * 1.21.5, the 771 pack (pack_format 63) when it is
     * 1.21.6, the 765 pack (pack_format 22) otherwise.
     * Native clients reach the vanilla pack untouched (relay mode), so it
     * must match the internal version; translated clients get the proxy's
     * per-client substitution instead. Falls back to the 765 pack when no
     * 763 / 764 / 766 / 767 / 768 / 769 / 770 / 771 pack was prepared."""),
    ("hostResourcePack branch", """\
        if (serverProtocol == ProtocolVersion.V1_21_5 && resourcePack770 != null) {
            return resourcePack770;
        }
        return resourcePack;""",
     """\
        if (serverProtocol == ProtocolVersion.V1_21_5 && resourcePack770 != null) {
            return resourcePack770;
        }
        if (serverProtocol == ProtocolVersion.V1_21_6 && resourcePack771 != null) {
            return resourcePack771;
        }
        return resourcePack;"""),
    ("createClientProxy javadoc", """\
     * 766 for 1.20.5/1.20.6, 767 for 1.21/1.21.1, 768 for 1.21.2/1.21.3,
     *     769 for 1.21.4, 770 for 1.21.5)
     * and the per-client packs are passed through, so the proxy""",
     """\
     * 766 for 1.20.5/1.20.6, 767 for 1.21/1.21.1, 768 for 1.21.2/1.21.3,
     *     769 for 1.21.4, 770 for 1.21.5, 771 for 1.21.6)
     * and the per-client packs are passed through, so the proxy"""),
    ("createClientProxy pack770 tail", """\
     * (native 769 clients relay the vanilla pack untouched). The 770 pack is passed
     * unless the internal server is 1.21.5, for the same reason
     * (native 770 clients relay the vanilla pack untouched).""",
     """\
     * (native 769 clients relay the vanilla pack untouched). The 770 pack is passed
     * unless the internal server is 1.21.5, for the same reason
     * (native 770 clients relay the vanilla pack untouched). The 771 pack is passed
     * unless the internal server is 1.21.6, for the same reason
     * (native 771 clients relay the vanilla pack untouched)."""),
    ("createClientProxy pack771", """\
        ResourcePack packFor770Clients =
                serverProtocol == ProtocolVersion.V1_21_5 ? null : resourcePack770;
        return new ClientProxy(port, "127.0.0.1", internalPort, onlineMode,
                resourcePack763, resourcePack764, authConfig,
                packFor765Clients, packFor766Clients, packFor767Clients,
                packFor768Clients, packFor769Clients, packFor770Clients, serverProtocol);""",
     """\
        ResourcePack packFor770Clients =
                serverProtocol == ProtocolVersion.V1_21_5 ? null : resourcePack770;
        ResourcePack packFor771Clients =
                serverProtocol == ProtocolVersion.V1_21_6 ? null : resourcePack771;
        return new ClientProxy(port, "127.0.0.1", internalPort, onlineMode,
                resourcePack763, resourcePack764, authConfig,
                packFor765Clients, packFor766Clients, packFor767Clients,
                packFor768Clients, packFor769Clients, packFor770Clients, packFor771Clients,
                serverProtocol);"""),
    ("startResourcePack javadoc", """\
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
     * when it is 1.21.5, the 765 pack otherwise.""",
     """\
     * 1.21.2 / 1.21.3 variant ({@code pack_format} 42, one pack for
     * both versions), a 1.21.4 variant ({@code pack_format} 46), a
     * 1.21.5 variant ({@code pack_format} 55) and a 1.21.6 variant
     * ({@code pack_format} 63):
     * in loopback mode they are generated and served
     * alongside the 1.20.4 pack by the same HTTP server (see
     * {@link #resourcePack763()}, {@link #resourcePack764()},
     * {@link #resourcePack766()}, {@link #resourcePack767()},
     * {@link #resourcePack768()}, {@link #resourcePack769()},
     * {@link #resourcePack770()} and {@link #resourcePack771()});
     * in external mode they come from
     * {@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768},
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769},
     * {@code resource-pack.url-770} / {@code resource-pack.sha1-770} and
     * {@code resource-pack.url-771} / {@code resource-pack.sha1-771}
     * when both are valid. The proxy offers the 1.20.1 pack to 763
     * clients, the 1.20.2 pack to 764 clients, the 1.20.5 / 1.20.6 pack
     * to 766 clients, the 1.21 / 1.21.1 pack to 767 clients, the
     * 1.21.2 / 1.21.3 pack to 768 clients, the 1.21.4 pack to 769
     * clients, the 1.21.5 pack to 770 clients, the 1.21.6 pack to 771
     * clients, and the 1.20.4 pack to 765
     * clients. The pack written into {@code server.properties} is chosen by
     * {@link #hostResourcePack()}: the 764 pack when the vanilla server
     * itself is 1.20.2, the 766 pack when it is 1.20.5 or 1.20.6, the
     * 767 pack when it is 1.21 or 1.21.1, the 768 pack when it is
     * 1.21.2 or 1.21.3, the 769 pack when it is 1.21.4, the 770 pack
     * when it is 1.21.5, the 771 pack when it is 1.21.6, the 765 pack
     * otherwise."""),
    ("decide call url-771", """\
                        config.getString("resource-pack.url-770", ""),
                        config.getString("resource-pack.sha1-770", ""),
                        required, promptKey);""",
     """\
                        config.getString("resource-pack.url-770", ""),
                        config.getString("resource-pack.sha1-770", ""),
                        config.getString("resource-pack.url-771", ""),
                        config.getString("resource-pack.sha1-771", ""),
                        required, promptKey);"""),
    ("EXTERNAL resourcePack770 null", """\
                    resourcePack769 = null;
                    resourcePack770 = null;
                    return null;""",
     """\
                    resourcePack769 = null;
                    resourcePack770 = null;
                    resourcePack771 = null;
                    return null;"""),
    ("EXTERNAL resourcePack771 assignment", """\
                resourcePack770 = decision.pack770();
                if (resourcePack770 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack770.url()));
                }
                return resourcePack;""",
     """\
                resourcePack770 = decision.pack770();
                if (resourcePack770 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack770.url()));
                }
                resourcePack771 = decision.pack771();
                if (resourcePack771 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack771.url()));
                }
                return resourcePack;"""),
    ("LOOPBACK build771", """\
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
            // 770クライアントには8番目をプロキシが出し分ける。""",
     """\
            // 1.20.4用（pack_format 22）と1.20.1用（pack_format 15）、
            // 1.20.2用（pack_format 18）、1.20.5／1.20.6用（pack_format 32、
            // 1パックで両バージョン共用）、1.21／1.21.1用（pack_format 34、
            // 1パックで両バージョン共用）、1.21.2／1.21.3用（pack_format 42、
            // 1パックで両バージョン共用）、1.21.4用（pack_format 46）、
            // 1.21.5用（pack_format 55）、1.21.6用（pack_format 63）の
            // 9つを生成し、同じHTTPサーバー（同じ25567番ポート）で配信する。
            // 765クライアントには前者、763クライアントには2番目、
            // 764クライアントには3番目、766クライアントには4番目、
            // 767クライアントには5番目、768クライアントには6番目、
            // 769クライアントには7番目、770クライアントには8番目、
            // 771クライアントには9番目をプロキシが出し分ける。"""),
    ("LOOPBACK built771", """\
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
            resourcePack770 = resourcePackServer.pack770();""",
     """\
            ResourcePackBuilder.BuiltPack built770 = new ResourcePackBuilder()
                    .build770(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built771 = new ResourcePackBuilder()
                    .build771(workingDir.resolve("data").resolve("resource-pack"));
            resourcePackServer = new ResourcePackServer(
                    built, built763, built764, built766, built767, built768, built769, built770,
                    built771, port, required,
                    promptKey.isBlank()
                            ? ResourcePackServer.DEFAULT_PROMPT_KEY : promptKey.trim());
            resourcePack = resourcePackServer.start();
            resourcePack763 = resourcePackServer.pack763();
            resourcePack764 = resourcePackServer.pack764();
            resourcePack766 = resourcePackServer.pack766();
            resourcePack767 = resourcePackServer.pack767();
            resourcePack768 = resourcePackServer.pack768();
            resourcePack769 = resourcePackServer.pack769();
            resourcePack770 = resourcePackServer.pack770();
            resourcePack771 = resourcePackServer.pack771();"""),
    ("generateDatapack comment", """\
            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5.""",
     """\
            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5, 80 for 1.21.6."""),
    ("stopResourcePack", """\
        resourcePack769 = null;
        resourcePack770 = null;
    }""",
     """\
        resourcePack769 = null;
        resourcePack770 = null;
        resourcePack771 = null;
    }"""),
    ("resourcePack771 getter", """\
    /**
     * The 1.21.5 resource pack offered to 770 clients, or
     * {@code null} (protocol 770, pack_format 55).
     */
    public ResourcePack resourcePack770() {
        return resourcePack770;
    }""",
     """\
    /**
     * The 1.21.5 resource pack offered to 770 clients, or
     * {@code null} (protocol 770, pack_format 55).
     */
    public ResourcePack resourcePack770() {
        return resourcePack770;
    }

    /**
     * The 1.21.6 resource pack offered to 771 clients, or
     * {@code null} (protocol 771, pack_format 63).
     */
    public ResourcePack resourcePack771() {
        return resourcePack771;
    }"""),
])

# ---------------------------------------------------------------- detergentmc.yml
# 「8つとも」の行は url-768／url-769／url-770 の3か所にあるため全置換する。
YML_TEXT = YML.read_text(encoding="utf-8")
n8 = YML_TEXT.count("  # ループバック配信では無視されます（8つとも自動生成・配信します）。")
if n8 != 3:
    print(f"NG: detergentmc.yml yml 770 count: 8つともの行が {n8} 回見つかった（3回のはず）",
          file=sys.stderr)
    sys.exit(1)
YML.write_text(YML_TEXT.replace(
    "  # ループバック配信では無視されます（8つとも自動生成・配信します）。",
    "  # ループバック配信では無視されます（9つとも自動生成・配信します）。"),
    encoding="utf-8")
print("OK: detergentmc.yml yml 770 count (3か所)")
edit(YML, [
    ("url-771/sha1-771", """\
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
  # ループバック配信では無視されます（9つとも自動生成・配信します）。
  url-770: ""
  sha1-770: """"",
     """\
  # 1.21.5 pack for 770 clients (optional; external mode only).
  # Same rules as url-769 / sha1-769, but the hosted pack must be
  # pack_format 55 (the 1.21.4 pack, format 46, is NOT shared with
  # 770 clients: a 1.21.5 client shows it as incompatible).
  # Leave both empty to offer the 1.20.4 pack to 770 clients.
  # In loopback mode these two are ignored: all nine variants are
  # generated and served locally.
  # 770クライアント向けの1.21.5用パック（任意・外部配信時のみ）。
  # url-769 / sha1-769 と同じ決まりですが、ホストするパックは
  # pack_format 55 のものが必要です。1.21.4用パック（format 46）は
  # 770クライアントには流用しません（「互換性なし」と表示されます）。
  # 空のままなら770クライアントにも1.20.4用パックを出します。
  # ループバック配信では無視されます（9つとも自動生成・配信します）。
  url-770: ""
  sha1-770: ""
  # 1.21.6 pack for 771 clients (optional; external mode only).
  # Same rules as url-770 / sha1-770, but the hosted pack must be
  # pack_format 63 (the 1.21.5 pack, format 55, is NOT shared with
  # 771 clients: a 1.21.6 client shows it as incompatible).
  # Leave both empty to offer the 1.20.4 pack to 771 clients.
  # In loopback mode these two are ignored: all nine variants are
  # generated and served locally.
  # 771クライアント向けの1.21.6用パック（任意・外部配信時のみ）。
  # url-770 / sha1-770 と同じ決まりですが、ホストするパックは
  # pack_format 63 のものが必要です。1.21.5用パック（format 55）は
  # 771クライアントには流用しません（「互換性なし」と表示されます）。
  # 空のままなら771クライアントにも1.20.4用パックを出します。
  # ループバック配信では無視されます（9つとも自動生成・配信します）。
  url-771: ""
  sha1-771: """""),
])

print("DetergentServer／detergentmc.yml の771対応を入れた")
