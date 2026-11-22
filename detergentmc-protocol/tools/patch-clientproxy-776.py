#!/usr/bin/env python3
"""ClientProxy.java の新ビッグコンストラクタ（pack759-first）に 776（26.2）を入れる。

775 の前例（patch-clientproxy-775.py）と同じ段取り：
- 新コンストラクタに pack776 引数・フィールド・アクセサ・検証を追加する
- 中間コンストラクタの委譲に pack776=null を1つ足す
  （他の短いコンストラクタは中間へ委譲するため触らない）
- 新しい中間コンストラクタは作らない（774／775 のときも作っていない）
- セッションへの pack776 の受け渡し（session.setPack776）を追加する。
  ProxySession 側の setPack776 は ProxySession 776 パッチで追加される。

触らないもの：
- 旧ビッグコンストラクタ（pack761-first）。呼び出し元が無く、
  776 スコープ外のため現状維持。親への報告事項とする。

テストの更新（別スクリプト update-776-clientproxy-tests.py で行う）：
- ClientProxy767/768/769Test の internalProtocol776IsStillRejected は
  新コンストラクタの検証を通るため、776 受け入れ後は失敗する。777 へ移す。
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
    src = rep(src, "    private final ResourcePack pack775;\n",
              "    private final ResourcePack pack775;\n"
              "    private final ResourcePack pack776;\n",
              "1 フィールド")

    # 2. 新コンストラクタの javadoc：@param pack776 を追加
    #    （後続の @param internalProtocol が 759 から始まるため新コンストラクタ側だけに一致）
    src = rep(src,
              "     * @param pack775         775クライアントに送るリソースパック\n"
              "     *                        （pack_format 84）。内部が775でない場合に使う。\n"
              "     *                        {@code null} の場合は775クライアントにも\n"
              "     *                        774用パックを出す（ProxySession の既定の扱い）\n"
              "     * @param internalProtocol 内部バニラのプロトコル（759＝1.19、760＝1.19.1／1.19.2、761＝1.19.3、762＝1.19.4、\n"
              "     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1）。呼び出し元は\n",
              "     * @param pack775         775クライアントに送るリソースパック\n"
              "     *                        （pack_format 84）。内部が775でない場合に使う。\n"
              "     *                        {@code null} の場合は775クライアントにも\n"
              "     *                        774用パックを出す（ProxySession の既定の扱い）\n"
              "     * @param pack776         776クライアントに送るリソースパック\n"
              "     *                        （pack_format 88）。内部が776でない場合に使う。\n"
              "     *                        {@code null} の場合は776クライアントにも\n"
              "     *                        775用パックを出す（ProxySession の既定の扱い）\n"
              "     * @param internalProtocol 内部バニラのプロトコル（759＝1.19、760＝1.19.1／1.19.2、761＝1.19.3、762＝1.19.4、\n"
              "     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1、776＝26.2）。呼び出し元は\n",
              "2 新コンストラクタ javadoc")

    # 3. 新コンストラクタの署名（pack759-first の署名全体で特定）
    src = rep(src,
              "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, ResourcePack pack775, int internalProtocol) {",
              "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, ResourcePack pack775, ResourcePack pack776, int internalProtocol) {",
              "3 新コンストラクタ署名")

    # 4. 検証：V26_2 を追加し、エラーメッセージを更新
    #    （エラーメッセージが 759 から始まるため新コンストラクタ側だけに一致）
    src = rep(src,
              "                && internalProtocol != ProtocolVersion.V1_21_9\n"
              "                && internalProtocol != ProtocolVersion.V1_21_11\n"
              "                && internalProtocol != ProtocolVersion.V26_1) {\n"
              "            throw new IllegalArgumentException(\n"
              "                    \"internalProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), \"\n"
              "                            + \"765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), \"\n"
              "                            + \"769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10), 774 (1.21.11) or 775 (26.1): \"\n"
              "                            + internalProtocol);",
              "                && internalProtocol != ProtocolVersion.V1_21_9\n"
              "                && internalProtocol != ProtocolVersion.V1_21_11\n"
              "                && internalProtocol != ProtocolVersion.V26_1\n"
              "                && internalProtocol != ProtocolVersion.V26_2) {\n"
              "            throw new IllegalArgumentException(\n"
              "                    \"internalProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), \"\n"
              "                            + \"765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), \"\n"
              "                            + \"769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10), 774 (1.21.11), 775 (26.1) or 776 (26.2): \"\n"
              "                            + internalProtocol);",
              "4 検証")

    # 5. 代入：新コンストラクタ側の this.pack775（ファイル内で最初の出現）が対象。
    #    旧コンストラクタ側（2番目の出現）は触らない。
    marker = "        this.pack775 = pack775;\n"
    idx = src.find(marker)
    if idx < 0:
        failures.append("5 代入: this.pack775 が見つからない")
    else:
        src = src[:idx] + marker + "        this.pack776 = pack776;\n" + src[idx + len(marker):]

    # 6. アクセサ pack776() を pack775() の直後に追加
    src = rep(src,
              "    public ResourcePack pack775() {\n"
              "        return pack775;\n"
              "    }\n",
              "    public ResourcePack pack775() {\n"
              "        return pack775;\n"
              "    }\n"
              "\n"
              "    /**\n"
              "     * 776クライアントに送るリソースパック（無ければ null）。内部が776で\n"
              "     * ない場合に使う差し替え用で、内部が776の場合は null のまま\n"
              "     * （バニラのパックをそのまま出す）。\n"
              "     */\n"
              "    public ResourcePack pack776() {\n"
              "        return pack776;\n"
              "    }\n",
              "6 アクセサ")

    # 7. 中間コンストラクタの委譲に pack776=null を1つ足す
    src = rep(src,
              "                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null, null,\n"
              "                internalProtocol);",
              "                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null, null, null,\n"
              "                internalProtocol);",
              "7 中間コンストラクタ委譲")

    # 8. 旧コンストラクタ側の代入：pack776 は final フィールドのため、
    #    旧コンストラクタ（pack776 引数なし）でも初期化が必要。null 固定とする。
    #    （旧コンストラクタに呼び出し元は無く、検証や署名には触らない）
    src = rep(src,
              "        this.pack775 = null; // 旧コンストラクタには pack775 引数が無い（呼び出し元なしの旧経路のため null 固定）\n",
              "        this.pack775 = null; // 旧コンストラクタには pack775 引数が無い（呼び出し元なしの旧経路のため null 固定）\n"
              "        this.pack776 = null; // 旧コンストラクタには pack776 引数が無い（呼び出し元なしの旧経路のため null 固定）\n",
              "8 旧コンストラクタ代入")

    # 9. セッションへの受け渡し：setPack775 の直後に setPack776 を追加。
    #    コメントの setter 一覧と pack775 の説明にも 776 を追加。
    src = rep(src,
              "                // セッションへ直接渡す（ProxySession#setPack759／setPack760／setPack761／setPack762／setPack767／setPack768／setPack769／setPack770／setPack772／setPack773／setPack774／setPack775）。",
              "                // セッションへ直接渡す（ProxySession#setPack759／setPack760／setPack761／setPack762／setPack767／setPack768／setPack769／setPack770／setPack772／setPack773／setPack774／setPack775／setPack776）。",
              "9 受け渡しコメント")
    src = rep(src,
              "                // 775クライアントには pack775（pack_format 84）を出し、\n"
              "                // 未設定の場合は ProxySession が pack774 に落とす。\n",
              "                // 775クライアントには pack775（pack_format 84）を出し、\n"
              "                // 未設定の場合は ProxySession が pack774 に落とす。\n"
              "                // 776クライアントには pack776（pack_format 88）を出し、\n"
              "                // 未設定の場合は ProxySession が pack775 に落とす。\n",
              "9 受け渡しコメント2")
    src = rep(src,
              "                session.setPack775(proxy.pack775);\n",
              "                session.setPack775(proxy.pack775);\n"
              "                session.setPack776(proxy.pack776);\n",
              "9 setPack776 呼び出し")

    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        print("何も書き込まない。")
        return 1
    PATH.write_text(src, encoding="utf-8")
    print("ClientProxy.java の新コンストラクタに 776 を適用した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
