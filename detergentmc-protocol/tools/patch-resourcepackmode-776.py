#!/usr/bin/env python3
"""ResourcePackMode.java に 776（26.2）パックを追加する。

775 の追加と同じパターン。位置引数オーバーロードの罠（罠7）に注意：
decide() のオーバーロードは引数の個数（arity）で解決されるため、新しい
url776／sha1776 ペアはフル decide の url775／sha1775 の直後に挿入し、
委譲側も同じ位置に null ペアを挿入すること。末尾に足すと古い呼び出しが
別オーバーロードへ無言で束縛され直す。

変更点：
* Decision レコード：pack776 フィールド＋@param。後方互換コンストラクタの
  末尾 null を1つずつ増やす（without 1.21.9: 3→4、without 1.21.11: 2→3、
  without 26.1: 1→2）。新規に "without a 26.2 pack" コンストラクタを追加。
* packForProtocol()：V26_2 分岐を追加（pack776、無ければ本体）。
* decide()：新規オーバーロード "through 775 + 776"（34引数）を追加し、
  through-775+760 オーバーロードに 776 ペアを挿入（シグネチャ・委譲とも）。
  フル decide に 776 ペアを挿入し、url-776／sha1-776 の検証と pack776 の
  生成・Decision への追加を行う。
* through-774 までのオーバーロードは委譲先の arity が変わらないため変更不要。
"""
import sys
from pathlib import Path

PATH = Path("detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackMode.java")
failures = []


def rep(src, old, new, note, count=1):
    n = src.count(old)
    if n != count:
        failures.append(f"{note}: 期待 {count} 回だが {n} 回（{old[:70]!r}）")
        return src
    return src.replace(old, new)


def main():
    src = PATH.read_text(encoding="utf-8")
    orig = src

    # --- 1. Decision レコード：@param pack776 とフィールド ---
    src = rep(src,
              "     * @param pack775 the 26.1 pack to offer to 775 clients in\n"
              "     *                external mode, or {@code null} when none is\n"
              "     *                configured (775 clients then get {@code pack} as\n"
              "     *                before)\n"
              "     */",
              "     * @param pack775 the 26.1 pack to offer to 775 clients in\n"
              "     *                external mode, or {@code null} when none is\n"
              "     *                configured (775 clients then get {@code pack} as\n"
              "     *                before)\n"
              "     * @param pack776 the 26.2 pack to offer to 776 clients in\n"
              "     *                external mode, or {@code null} when none is\n"
              "     *                configured (776 clients then get {@code pack} as\n"
              "     *                before)\n"
              "     */",
              "1 Decision javadoc")
    src = rep(src,
              "            ResourcePack pack772, ResourcePack pack773, ResourcePack pack774,\n"
              "            ResourcePack pack775) {",
              "            ResourcePack pack772, ResourcePack pack773, ResourcePack pack774,\n"
              "            ResourcePack pack775, ResourcePack pack776) {",
              "1 Decision フィールド")

    # --- 2. 後方互換コンストラクタ：末尾 null を1つずつ増やす ---
    # without 1.21.9（末尾 null 3個→4個）
    src = rep(src,
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, null, null, null);",
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, null, null, null, null);",
              "2 without 1.21.9")
    # without 1.21.11（末尾 null 2個→3個）
    src = rep(src,
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null, null);",
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null, null, null);",
              "2 without 1.21.11")
    # without 26.1（末尾 null 1個→2個）
    src = rep(src,
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, null);",
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, null, null);",
              "2 without 26.1")

    # --- 3. 新規 "without a 26.2 pack" コンストラクタ ---
    src = rep(src,
              "        /** Backwards-compatible constructor without a 26.1 pack. */\n"
              "        public Decision(Kind kind, ResourcePack pack, String problem,\n"
              "                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,\n"
              "                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
              "                ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774) {\n"
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, null, null);\n"
              "        }\n",
              "        /** Backwards-compatible constructor without a 26.1 pack. */\n"
              "        public Decision(Kind kind, ResourcePack pack, String problem,\n"
              "                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,\n"
              "                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
              "                ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774) {\n"
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, null, null);\n"
              "        }\n"
              "\n"
              "        /** Backwards-compatible constructor without a 26.2 pack. */\n"
              "        public Decision(Kind kind, ResourcePack pack, String problem,\n"
              "                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,\n"
              "                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
              "                ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, ResourcePack pack775) {\n"
              "            this(kind, pack, problem, null, null, null, null, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, null);\n"
              "        }\n",
              "3 without 26.2 新設")

    # --- 4. packForProtocol()：V26_2 分岐 ---
    src = rep(src,
              "         * 774→{@code pack774}（75）、775→{@code pack775}（84）。",
              "         * 774→{@code pack774}（75）、775→{@code pack775}（84）、\n"
              "         * 776→{@code pack776}（88）。",
              "4 packForProtocol javadoc")
    src = rep(src,
              "            if (protocol == ProtocolVersion.V26_1) {\n"
              "                return pack775 != null ? pack775 : pack;\n"
              "            }\n"
              "            return pack;",
              "            if (protocol == ProtocolVersion.V26_1) {\n"
              "                return pack775 != null ? pack775 : pack;\n"
              "            }\n"
              "            if (protocol == ProtocolVersion.V26_2) {\n"
              "                return pack776 != null ? pack776 : pack;\n"
              "            }\n"
              "            return pack;",
              "4 packForProtocol 分岐")

    # --- 5. 新規オーバーロード "through 775 + 776"（34引数） ---
    # through-775 オーバーロードの直後（through-775+760 オーバーロードの直前）に挿入。
    src = rep(src,
              "    /**\n"
              "     * {@link #decide(String, String, boolean, String)} with the optional\n"
              "     * per-version packs through 26.1 plus the 1.19.1 / 1.19.2 pack.\n",
              "    /**\n"
              "     * {@link #decide(String, String, boolean, String)} with the optional\n"
              "     * per-version packs through 26.1 plus the 26.2 pack.\n"
              "     * The 776 pack is one pack for 26.2 (protocol 776,\n"
              "     * pack_format 88) and follows the same rules as the 775 pack.\n"
              "     *\n"
              "     * @param configuredUrl776  {@code resource-pack.url-776} (may be null/blank)\n"
              "     * @param configuredSha1776 {@code resource-pack.sha1-776} (may be null/blank)\n"
              "     */\n"
              "    public static Decision decide(String configuredUrl, String configuredSha1,\n"
              "            String configuredUrl761, String configuredSha1761,\n"
              "            String configuredUrl762, String configuredSha1762,\n"
              "            String configuredUrl763, String configuredSha1763,\n"
              "            String configuredUrl764, String configuredSha1764,\n"
              "            String configuredUrl766, String configuredSha1766,\n"
              "            String configuredUrl767, String configuredSha1767,\n"
              "            String configuredUrl768, String configuredSha1768,\n"
              "            String configuredUrl769, String configuredSha1769,\n"
              "            String configuredUrl770, String configuredSha1770,\n"
              "            String configuredUrl771, String configuredSha1771,\n"
              "            String configuredUrl772, String configuredSha1772,\n"
              "            String configuredUrl773, String configuredSha1773,\n"
              "            String configuredUrl774, String configuredSha1774,\n"
              "            String configuredUrl775, String configuredSha1775,\n"
              "            String configuredUrl776, String configuredSha1776,\n"
              "            boolean required, String promptKey) {\n"
              "        return decide(configuredUrl, configuredSha1, configuredUrl761, configuredSha1761, configuredUrl762, configuredSha1762, configuredUrl763, configuredSha1763,\n"
              "                configuredUrl764, configuredSha1764, configuredUrl766, configuredSha1766, configuredUrl767, configuredSha1767, configuredUrl768, configuredSha1768, configuredUrl769, configuredSha1769,\n"
              "                configuredUrl770, configuredSha1770, configuredUrl771, configuredSha1771, configuredUrl772, configuredSha1772, configuredUrl773, configuredSha1773, configuredUrl774, configuredSha1774,\n"
              "                configuredUrl775, configuredSha1775, configuredUrl776, configuredSha1776, null, null, required, promptKey);\n"
              "    }\n"
              "\n"
              "    /**\n"
              "     * {@link #decide(String, String, boolean, String)} with the optional\n"
              "     * per-version packs through 26.1 plus the 1.19.1 / 1.19.2 pack.\n",
              "5 新規オーバーロード through 775+776")

    # --- 6. through-775+760 オーバーロード：シグネチャと委譲に 776 ペアを挿入 ---
    src = rep(src,
              "     * {@link #decide(String, String, boolean, String)} with the optional\n"
              "     * per-version packs through 26.1 plus the 1.19.1 / 1.19.2 pack.\n"
              "     * The 1.19 pack ({@code resource-pack.url-759} /\n"
              "     * {@code resource-pack.sha1-759}) is not passed here; 759 clients\n"
              "     * are offered the 1.19.1 / 1.19.2 pack (when configured) as before.\n"
              "     * The 775 pack is one pack for 26.1 / 26.1.1 / 26.1.2 (protocol 775,\n"
              "     * pack_format 84) and follows the same rules as the 774 pack.\n"
              "     */",
              "     * {@link #decide(String, String, boolean, String)} with the optional\n"
              "     * per-version packs through 26.2 plus the 1.19.1 / 1.19.2 pack.\n"
              "     * The 1.19 pack ({@code resource-pack.url-759} /\n"
              "     * {@code resource-pack.sha1-759}) is not passed here; 759 clients\n"
              "     * are offered the 1.19.1 / 1.19.2 pack (when configured) as before.\n"
              "     * The 775 pack is one pack for 26.1 / 26.1.1 / 26.1.2 (protocol 775,\n"
              "     * pack_format 84) and follows the same rules as the 774 pack.\n"
              "     * The 776 pack is one pack for 26.2 (protocol 776,\n"
              "     * pack_format 88) and follows the same rules as the 775 pack.\n"
              "     */",
              "6 through-775+760 javadoc")
    src = rep(src,
              "            String configuredUrl774, String configuredSha1774,\n"
              "            String configuredUrl775, String configuredSha1775,\n"
              "            String configuredUrl760, String configuredSha1760,\n"
              "            boolean required, String promptKey) {\n"
              "        return decide(configuredUrl, configuredSha1, configuredUrl761, configuredSha1761, configuredUrl762, configuredSha1762, configuredUrl763, configuredSha1763,\n"
              "                configuredUrl764, configuredSha1764, configuredUrl766, configuredSha1766, configuredUrl767, configuredSha1767, configuredUrl768, configuredSha1768, configuredUrl769, configuredSha1769,\n"
              "                configuredUrl770, configuredSha1770, configuredUrl771, configuredSha1771, configuredUrl772, configuredSha1772, configuredUrl773, configuredSha1773, configuredUrl774, configuredSha1774,\n"
              "                configuredUrl775, configuredSha1775, configuredUrl760, configuredSha1760, null, null, required, promptKey);",
              "            String configuredUrl774, String configuredSha1774,\n"
              "            String configuredUrl775, String configuredSha1775,\n"
              "            String configuredUrl776, String configuredSha1776,\n"
              "            String configuredUrl760, String configuredSha1760,\n"
              "            boolean required, String promptKey) {\n"
              "        return decide(configuredUrl, configuredSha1, configuredUrl761, configuredSha1761, configuredUrl762, configuredSha1762, configuredUrl763, configuredSha1763,\n"
              "                configuredUrl764, configuredSha1764, configuredUrl766, configuredSha1766, configuredUrl767, configuredSha1767, configuredUrl768, configuredSha1768, configuredUrl769, configuredSha1769,\n"
              "                configuredUrl770, configuredSha1770, configuredUrl771, configuredSha1771, configuredUrl772, configuredSha1772, configuredUrl773, configuredSha1773, configuredUrl774, configuredSha1774,\n"
              "                configuredUrl775, configuredSha1775, configuredUrl776, configuredSha1776, configuredUrl760, configuredSha1760, null, null, required, promptKey);",
              "6 through-775+760 シグネチャ・委譲")

    # --- 7. フル decide：javadoc とシグネチャに 776 を追加 ---
    src = rep(src,
              "     * {@link #decide(String, String, boolean, String)} with the optional\n"
              "     * per-version packs through 26.1 plus the 1.19 pack\n"
              "     * ({@code resource-pack.url-759} / {@code resource-pack.sha1-759})\n"
              "     * and the 1.19.1 / 1.19.2 pack ({@code resource-pack.url-760} /\n"
              "     * {@code resource-pack.sha1-760}).\n"
              "     * The 775 pack is one pack for 26.1 / 26.1.1 / 26.1.2 (protocol 775,\n"
              "     * pack_format 84) and follows the same rules as the 774 pack.\n"
              "     *\n"
              "     * @param configuredUrl775  {@code resource-pack.url-775} (may be null/blank)\n"
              "     * @param configuredSha1775 {@code resource-pack.sha1-775} (may be null/blank)\n"
              "     */",
              "     * {@link #decide(String, String, boolean, String)} with the optional\n"
              "     * per-version packs through 26.2 plus the 1.19 pack\n"
              "     * ({@code resource-pack.url-759} / {@code resource-pack.sha1-759})\n"
              "     * and the 1.19.1 / 1.19.2 pack ({@code resource-pack.url-760} /\n"
              "     * {@code resource-pack.sha1-760}).\n"
              "     * The 775 pack is one pack for 26.1 / 26.1.1 / 26.1.2 (protocol 775,\n"
              "     * pack_format 84) and follows the same rules as the 774 pack.\n"
              "     * The 776 pack is one pack for 26.2 (protocol 776,\n"
              "     * pack_format 88) and follows the same rules as the 775 pack.\n"
              "     *\n"
              "     * @param configuredUrl775  {@code resource-pack.url-775} (may be null/blank)\n"
              "     * @param configuredSha1775 {@code resource-pack.sha1-775} (may be null/blank)\n"
              "     * @param configuredUrl776  {@code resource-pack.url-776} (may be null/blank)\n"
              "     * @param configuredSha1776 {@code resource-pack.sha1-776} (may be null/blank)\n"
              "     */",
              "7 フル decide javadoc")
    src = rep(src,
              "            String configuredUrl774, String configuredSha1774,\n"
              "            String configuredUrl775, String configuredSha1775,\n"
              "            String configuredUrl760, String configuredSha1760,\n"
              "            String configuredUrl759, String configuredSha1759,\n"
              "            boolean required, String promptKey) {",
              "            String configuredUrl774, String configuredSha1774,\n"
              "            String configuredUrl775, String configuredSha1775,\n"
              "            String configuredUrl776, String configuredSha1776,\n"
              "            String configuredUrl760, String configuredSha1760,\n"
              "            String configuredUrl759, String configuredSha1759,\n"
              "            boolean required, String promptKey) {",
              "7 フル decide シグネチャ")

    # --- 8. フル decide 本体：pack776 変数・検証・Decision への追加 ---
    src = rep(src,
              "            ResourcePack pack774 = null;\n"
              "            ResourcePack pack775 = null;",
              "            ResourcePack pack774 = null;\n"
              "            ResourcePack pack775 = null;\n"
              "            ResourcePack pack776 = null;",
              "8 pack776 変数")
    src = rep(src,
              "                    pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775);",
              "                    pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, pack776);",
              "8 Decision への pack776 追加")

    # --- 8b. フル decide 本体：url-776／sha1-776 の検証ブロック ---
    src = rep(src,
              "                } else {\n"
              "                    pack775 = new ResourcePack(url775,\n"
              "                            configuredSha1775.trim().toLowerCase(Locale.ROOT),\n"
              "                            required, prompt);\n"
              "                }\n"
              "            }\n"
              "            String problem = problems.isEmpty() ? null : String.join(\"; \", problems);",
              "                } else {\n"
              "                    pack775 = new ResourcePack(url775,\n"
              "                            configuredSha1775.trim().toLowerCase(Locale.ROOT),\n"
              "                            required, prompt);\n"
              "                }\n"
              "            }\n"
              "            String url776 = configuredUrl776 == null ? \"\" : configuredUrl776.trim();\n"
              "            if (!url776.isEmpty()) {\n"
              "                if (!url776.startsWith(\"https://\")) {\n"
              "                    problems.add(\"resource-pack.url-776 must start with https:// (got '\"\n"
              "                            + url776 + \"'); 776 clients will be offered the 1.20.4 pack instead\");\n"
              "                } else if (!isValidSha1(configuredSha1776)) {\n"
              "                    problems.add(\"resource-pack.sha1-776 is missing or not a 40-digit hex \"\n"
              "                            + \"SHA-1: '\" + configuredSha1776\n"
              "                            + \"'; 776 clients will be offered the 1.20.4 pack instead\");\n"
              "                } else {\n"
              "                    pack776 = new ResourcePack(url776,\n"
              "                            configuredSha1776.trim().toLowerCase(Locale.ROOT),\n"
              "                            required, prompt);\n"
              "                }\n"
              "            }\n"
              "            String problem = problems.isEmpty() ? null : String.join(\"; \", problems);",
              "8b url-776 検証ブロック")

    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        print("何も書き込まない。")
        return 1
    if src == orig:
        print("変更なし（既に適用済みか）。")
        return 0
    PATH.write_text(src, encoding="utf-8")
    print("ResourcePackMode.java に 776 を適用した。")
    print("注意：url-776／sha1-776 の検証ブロックは次の手順で追加すること。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
