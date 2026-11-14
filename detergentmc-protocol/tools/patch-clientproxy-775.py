#!/usr/bin/env python3
"""ClientProxy.java の新ビッグコンストラクタ（pack759-first）に 775（26.1）を入れる。

774 の前例（コミット 0e6dba5）と同じ段取り：
- 新コンストラクタに pack775 引数・フィールド・アクセサ・検証を追加する
- 596行目の中間コンストラクタの委譲に pack775=null を1つ足す
  （他の短いコンストラクタは中間へ委譲するため触らない）
- 新しい中間コンストラクタは作らない（774 のときも作っていない）

触らないもの：
- 旧ビッグコンストラクタ（pack761-first、793行目）。呼び出し元が無く、
  775 スコープ外のため現状維持。親への報告事項とする。
- session.setPack775(...) の配線は ProxySession 側に setPack775 が無いため
  今は入れない（コンパイルが通らなくなる）。ProxySession 775 パッチの適用時に
  入れること。pack775 フィールド・アクセサは先に用意しておく。

テストの更新（別スクリプト update-775-clientproxy-tests.py で行う）：
- ClientProxy767/768/769Test の internalProtocol775IsStillRejected は
  新コンストラクタの検証を通るため、775 受け入れ後は失敗する。776 へ移す。
"""
import sys
from pathlib import Path

PATH = Path("detergentmc-protocol/src/main/java/io/detergentmc/protocol/ClientProxy.java")
failures = []


def rep(src, old, new, note, count=1):
    n = src.count(old)
    if n != count:
        failures.append(f"{note}: 期待 {count} 回だが {n} 回（{old[:60]!r}）")
        return src
    return src.replace(old, new)


def main():
    src = PATH.read_text(encoding="utf-8")

    # 1. フィールド（クラスに1つだけ）
    src = rep(src, "    private final ResourcePack pack774;\n",
              "    private final ResourcePack pack774;\n"
              "    private final ResourcePack pack775;\n",
              "1 フィールド")

    # 2. 新コンストラクタの javadoc：@param pack775 を追加
    #    （後続の @param internalProtocol が 759 から始まるため新コンストラクタ側だけに一致）
    src = rep(src,
              "     * @param pack774         774クライアントに送るリソースパック\n"
              "     *                        （pack_format 75）。内部が774でない場合に使う。\n"
              "     *                        {@code null} の場合は774クライアントにも\n"
              "     *                        773用パックを出す（ProxySession の既定の扱い）\n"
              "     * @param internalProtocol 内部バニラのプロトコル（759＝1.19、760＝1.19.1／1.19.2、761＝1.19.3、762＝1.19.4、\n"
              "     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11）。呼び出し元は\n",
              "     * @param pack774         774クライアントに送るリソースパック\n"
              "     *                        （pack_format 75）。内部が774でない場合に使う。\n"
              "     *                        {@code null} の場合は774クライアントにも\n"
              "     *                        773用パックを出す（ProxySession の既定の扱い）\n"
              "     * @param pack775         775クライアントに送るリソースパック\n"
              "     *                        （pack_format 84）。内部が775でない場合に使う。\n"
              "     *                        {@code null} の場合は775クライアントにも\n"
              "     *                        774用パックを出す（ProxySession の既定の扱い）\n"
              "     * @param internalProtocol 内部バニラのプロトコル（759＝1.19、760＝1.19.1／1.19.2、761＝1.19.3、762＝1.19.4、\n"
              "     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1）。呼び出し元は\n",
              "2 新コンストラクタ javadoc")

    # 3. 新コンストラクタの署名（pack759-first の署名全体で特定）
    src = rep(src,
              "            boolean onlineMode, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,\n"
              "            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,\n"
              "            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
              "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, int internalProtocol) {",
              "            boolean onlineMode, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,\n"
              "            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,\n"
              "            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
              "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, ResourcePack pack775, int internalProtocol) {",
              "3 新コンストラクタ署名")

    # 4. 検証：V26_1 を追加し、エラーメッセージを更新
    #    （エラーメッセージが 759 から始まるため新コンストラクタ側だけに一致）
    src = rep(src,
              "                && internalProtocol != ProtocolVersion.V1_21_9\n"
              "                && internalProtocol != ProtocolVersion.V1_21_11) {\n"
              "            throw new IllegalArgumentException(\n"
              "                    \"internalProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), \"\n"
              "                            + \"765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), \"\n"
              "                            + \"769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10) or 774 (1.21.11): \"\n"
              "                            + internalProtocol);",
              "                && internalProtocol != ProtocolVersion.V1_21_9\n"
              "                && internalProtocol != ProtocolVersion.V1_21_11\n"
              "                && internalProtocol != ProtocolVersion.V26_1) {\n"
              "            throw new IllegalArgumentException(\n"
              "                    \"internalProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), \"\n"
              "                            + \"765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), \"\n"
              "                            + \"769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10), 774 (1.21.11) or 775 (26.1): \"\n"
              "                            + internalProtocol);",
              "4 検証")

    # 5. 代入：新コンストラクタ側の this.pack774（ファイル内で最初の出現）が対象。
    #    旧コンストラクタ側（2番目の出現）は触らない。
    marker = "        this.pack774 = pack774;\n"
    idx = src.find(marker)
    if idx < 0:
        failures.append("5 代入: this.pack774 が見つからない")
    else:
        src = src[:idx] + marker + "        this.pack775 = pack775;\n" + src[idx + len(marker):]

    # 6. アクセサ pack775() を pack774() の直後に追加
    src = rep(src,
              "    public ResourcePack pack774() {\n"
              "        return pack774;\n"
              "    }\n",
              "    public ResourcePack pack774() {\n"
              "        return pack774;\n"
              "    }\n"
              "\n"
              "    /**\n"
              "     * 775クライアントに送るリソースパック（無ければ null）。内部が775で\n"
              "     * ない場合に使う差し替え用で、内部が775の場合は null のまま\n"
              "     * （バニラのパックをそのまま出す）。\n"
              "     */\n"
              "    public ResourcePack pack775() {\n"
              "        return pack775;\n"
              "    }\n",
              "6 アクセサ")

    # 7. 596行目の中間コンストラクタの委譲に pack775=null を1つ足す
    src = rep(src,
              "                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null,\n"
              "                internalProtocol);",
              "                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null, null,\n"
              "                internalProtocol);",
              "7 中間コンストラクタ委譲")

    # 8. 旧コンストラクタ側の代入：pack775 は final フィールドのため、
    #    旧コンストラクタ（pack775 引数なし）でも初期化が必要。null 固定とする。
    #    （旧コンストラクタに呼び出し元は無く、検証や署名には触らない）
    marker2 = "        this.pack774 = pack774;\n"
    first = src.find(marker2)
    assert first >= 0, "代入マーカーが見つからない"
    second = src.find(marker2, first + len(marker2))
    assert second > first, "旧コンストラクタ側の代入が見つからない"
    assert "this.internalProtocol" in src[second + len(marker2):second + len(marker2) + 60]
    src = (src[:second] + marker2
           + "        this.pack775 = null; // 旧コンストラクタには pack775 引数が無い（呼び出し元なしの旧経路のため null 固定）\n"
           + src[second + len(marker2):])

    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        print("何も書き込まない。")
        return 1
    PATH.write_text(src, encoding="utf-8")
    print("ClientProxy.java の新コンストラクタに 775 を適用した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
