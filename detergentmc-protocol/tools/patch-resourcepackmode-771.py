#!/usr/bin/env python3
"""ResourcePackMode.java へ Java 1.21.6（プロトコル771）の対応を入れる。

Decision レコードに pack771 を追加し、decide() の 771 オーバーロードを
増やす。770の流儀をなぞる：フル decide() を771まで伸ばし、770までの
decide() を「771無し」の委譲版にする。後方互換コンストラクタも同様。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ResourcePackMode.java"

EDITS = [
    ("Decision javadoc pack771", """\
     * @param pack770 the 1.21.5 pack to offer to 770 clients in
     *                external mode, or {@code null} when none is
     *                configured (770 clients then get {@code pack} as
     *                before)
     */
    public record Decision(Kind kind, ResourcePack pack, String problem,
            ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770) {""",
     """\
     * @param pack770 the 1.21.5 pack to offer to 770 clients in
     *                external mode, or {@code null} when none is
     *                configured (770 clients then get {@code pack} as
     *                before)
     * @param pack771 the 1.21.6 pack to offer to 771 clients in
     *                external mode, or {@code null} when none is
     *                configured (771 clients then get {@code pack} as
     *                before)
     */
    public record Decision(Kind kind, ResourcePack pack, String problem,
            ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771) {"""),
    ("without-771 Decision constructor", """\
        /** Backwards-compatible constructor without a 1.21.5 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,
                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769) {
            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, null);
        }""",
     """\
        /** Backwards-compatible constructor without a 1.21.5 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,
                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769) {
            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, null, null);
        }

        /** Backwards-compatible constructor without a 1.21.6 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,
                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
                ResourcePack pack770) {
            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, pack770, null);
        }"""),
    ("packForProtocol javadoc", """\
         * 769→{@code pack769}（46）、770→{@code pack770}（55）。
         * そのバージョン用のパックが未設定の場合は本体を返す""",
     """\
         * 769→{@code pack769}（46）、770→{@code pack770}（55）、
         * 771→{@code pack771}（63）。
         * そのバージョン用のパックが未設定の場合は本体を返す"""),
    ("packForProtocol branch", """\
            if (protocol == ProtocolVersion.V1_21_5) {
                return pack770 != null ? pack770 : pack;
            }
            return pack;""",
     """\
            if (protocol == ProtocolVersion.V1_21_5) {
                return pack770 != null ? pack770 : pack;
            }
            if (protocol == ProtocolVersion.V1_21_6) {
                return pack771 != null ? pack771 : pack;
            }
            return pack;"""),
    ("decide 770 overload becomes delegating", """\
    /**
     * {@link #decide(String, String, String, String, String, String, String, String, String, String, String, String, String, String, boolean, String)}
     * with the optional 1.20.1, 1.20.2, 1.20.5 / 1.20.6, 1.21 /
     * 1.21.1, 1.21.2 / 1.21.3, 1.21.4 and 1.21.5 packs
     * ({@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768},
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769} and
     * {@code resource-pack.url-770} / {@code resource-pack.sha1-770}).
     * The 770 pack is one pack for 1.21.5 (protocol 770,
     * pack_format 55) and follows the same rules as the 763 / 764 / 766 /
     * 767 / 768 / 769 packs: empty means no pack for that version, {@code https://}
     * with a valid SHA-1 yields {@code pack770}, anything else drops
     * the 770 pack with a warning and leaves the primary pack unchanged.
     *
     * @param configuredUrl770  {@code resource-pack.url-770} (may be null/blank)
     * @param configuredSha1770 {@code resource-pack.sha1-770} (may be null/blank)
     */
    public static Decision decide(String configuredUrl, String configuredSha1,
            String configuredUrl763, String configuredSha1763,
            String configuredUrl764, String configuredSha1764,
            String configuredUrl766, String configuredSha1766,
            String configuredUrl767, String configuredSha1767,
            String configuredUrl768, String configuredSha1768,
            String configuredUrl769, String configuredSha1769,
            String configuredUrl770, String configuredSha1770,
            boolean required, String promptKey) {""",
     """\
    /**
     * {@link #decide(String, String, String, String, String, String, String, String, String, String, String, String, String, String, boolean, String)}
     * with the optional 1.20.1, 1.20.2, 1.20.5 / 1.20.6, 1.21 /
     * 1.21.1, 1.21.2 / 1.21.3, 1.21.4 and 1.21.5 packs
     * ({@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768},
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769} and
     * {@code resource-pack.url-770} / {@code resource-pack.sha1-770}).
     * The 770 pack is one pack for 1.21.5 (protocol 770,
     * pack_format 55) and follows the same rules as the 763 / 764 / 766 /
     * 767 / 768 / 769 packs: empty means no pack for that version, {@code https://}
     * with a valid SHA-1 yields {@code pack770}, anything else drops
     * the 770 pack with a warning and leaves the primary pack unchanged.
     *
     * @param configuredUrl770  {@code resource-pack.url-770} (may be null/blank)
     * @param configuredSha1770 {@code resource-pack.sha1-770} (may be null/blank)
     */
    public static Decision decide(String configuredUrl, String configuredSha1,
            String configuredUrl763, String configuredSha1763,
            String configuredUrl764, String configuredSha1764,
            String configuredUrl766, String configuredSha1766,
            String configuredUrl767, String configuredSha1767,
            String configuredUrl768, String configuredSha1768,
            String configuredUrl769, String configuredSha1769,
            String configuredUrl770, String configuredSha1770,
            boolean required, String promptKey) {
        return decide(configuredUrl, configuredSha1,
                configuredUrl763, configuredSha1763,
                configuredUrl764, configuredSha1764,
                configuredUrl766, configuredSha1766,
                configuredUrl767, configuredSha1767,
                configuredUrl768, configuredSha1768,
                configuredUrl769, configuredSha1769,
                configuredUrl770, configuredSha1770,
                null, null, required, promptKey);
    }

    /**
     * {@link #decide(String, String, String, String, String, String, String, String, String, String, String, String, String, String, boolean, String)}
     * with the optional 1.20.1, 1.20.2, 1.20.5 / 1.20.6, 1.21 /
     * 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5 and 1.21.6 packs
     * ({@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768},
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769},
     * {@code resource-pack.url-770} / {@code resource-pack.sha1-770} and
     * {@code resource-pack.url-771} / {@code resource-pack.sha1-771}).
     * The 771 pack is one pack for 1.21.6 (protocol 771,
     * pack_format 63) and follows the same rules as the 763 / 764 / 766 /
     * 767 / 768 / 769 / 770 packs: empty means no pack for that version, {@code https://}
     * with a valid SHA-1 yields {@code pack771}, anything else drops
     * the 771 pack with a warning and leaves the primary pack unchanged.
     *
     * @param configuredUrl771  {@code resource-pack.url-771} (may be null/blank)
     * @param configuredSha1771 {@code resource-pack.sha1-771} (may be null/blank)
     */
    public static Decision decide(String configuredUrl, String configuredSha1,
            String configuredUrl763, String configuredSha1763,
            String configuredUrl764, String configuredSha1764,
            String configuredUrl766, String configuredSha1766,
            String configuredUrl767, String configuredSha1767,
            String configuredUrl768, String configuredSha1768,
            String configuredUrl769, String configuredSha1769,
            String configuredUrl770, String configuredSha1770,
            String configuredUrl771, String configuredSha1771,
            boolean required, String promptKey) {"""),
    ("decide body pack771 var", """\
            ResourcePack pack769 = null;
            ResourcePack pack770 = null;""",
     """\
            ResourcePack pack769 = null;
            ResourcePack pack770 = null;
            ResourcePack pack771 = null;"""),
    ("decide body url771 block", """\
            String url770 = configuredUrl770 == null ? "" : configuredUrl770.trim();
            if (!url770.isEmpty()) {
                if (!url770.startsWith("https://")) {
                    problems.add("resource-pack.url-770 must start with https:// (got '"
                            + url770 + "'); 770 clients will be offered the 1.20.4 pack instead");
                } else if (!isValidSha1(configuredSha1770)) {
                    problems.add("resource-pack.sha1-770 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1770
                            + "'; 770 clients will be offered the 1.20.4 pack instead");
                } else {
                    pack770 = new ResourcePack(url770,
                            configuredSha1770.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""",
     """\
            String url770 = configuredUrl770 == null ? "" : configuredUrl770.trim();
            if (!url770.isEmpty()) {
                if (!url770.startsWith("https://")) {
                    problems.add("resource-pack.url-770 must start with https:// (got '"
                            + url770 + "'); 770 clients will be offered the 1.20.4 pack instead");
                } else if (!isValidSha1(configuredSha1770)) {
                    problems.add("resource-pack.sha1-770 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1770
                            + "'; 770 clients will be offered the 1.20.4 pack instead");
                } else {
                    pack770 = new ResourcePack(url770,
                            configuredSha1770.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url771 = configuredUrl771 == null ? "" : configuredUrl771.trim();
            if (!url771.isEmpty()) {
                if (!url771.startsWith("https://")) {
                    problems.add("resource-pack.url-771 must start with https:// (got '"
                            + url771 + "'); 771 clients will be offered the 1.20.4 pack instead");
                } else if (!isValidSha1(configuredSha1771)) {
                    problems.add("resource-pack.sha1-771 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1771
                            + "'; 771 clients will be offered the 1.20.4 pack instead");
                } else {
                    pack771 = new ResourcePack(url771,
                            configuredSha1771.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }"""),
    ("decide return", """\
            String problem = problems.isEmpty() ? null : String.join("; ", problems);
            return new Decision(Kind.EXTERNAL, pack, problem,
                    pack763, pack764, pack766, pack767, pack768, pack769, pack770);""",
     """\
            String problem = problems.isEmpty() ? null : String.join("; ", problems);
            return new Decision(Kind.EXTERNAL, pack, problem,
                    pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771);"""),
]


def main():
    src = PATH.read_text(encoding="utf-8")
    for name, old, new in EDITS:
        n = src.count(old)
        if n != 1:
            print(f"NG: {name}: anchor が {n} 回見つかった（1回のはず）", file=sys.stderr)
            sys.exit(1)
        src = src.replace(old, new, 1)
        print(f"OK: {name}")
    PATH.write_text(src, encoding="utf-8")
    print("ResourcePackMode.java に 771 の対応を挿入した")


if __name__ == "__main__":
    main()
