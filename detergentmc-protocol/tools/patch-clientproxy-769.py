#!/usr/bin/env python3
"""ClientProxy.java へ Java 1.21.4（プロトコル769）の対応を入れる。

pack769 フィールド・12引数コンストラクタ・getter・session への受け渡し。
11引数コンストラクタは pack769=null で12引数へ委譲する（10引数→11引数の
流儀と同じ）。リレー判定は ProxySession.resolveHandshake の汎用比較
（protocol == serverProtocol）のため変更不要。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ClientProxy.java"

EDITS = [
    ("pack769 field", """\
    /**
     * 768クライアントに送るリソースパック（pack_format 42）。内部が768で
     * ない場合にだけ使う。内部が768の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack768(ResourcePack)} で渡す
     * （{@code resolveHandshake} には768用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack768;""",
     """\
    /**
     * 768クライアントに送るリソースパック（pack_format 42）。内部が768で
     * ない場合にだけ使う。内部が768の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack768(ResourcePack)} で渡す
     * （{@code resolveHandshake} には768用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack768;
    /**
     * 769クライアントに送るリソースパック（pack_format 46）。内部が769で
     * ない場合にだけ使う。内部が769の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack769(ResourcePack)} で渡す
     * （{@code resolveHandshake} には769用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack769;"""),
    ("internalProtocol field javadoc", """\
    /**
     * 内部バニラのプロトコル（763＝1.20／1.20.1、766＝1.20.5／1.20.6、
     * 765＝1.20.4、764＝1.20.2、767＝1.21／1.21.1）。呼び出し元は""",
     """\
    /**
     * 内部バニラのプロトコル（763＝1.20／1.20.1、766＝1.20.5／1.20.6、
     * 765＝1.20.4、764＝1.20.2、767＝1.21／1.21.1、768＝1.21.2／1.21.3、
     * 769＝1.21.4）。呼び出し元は"""),
    ("11-arg constructor delegates to 12-arg", """\
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, int internalProtocol) {""",
     """\
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, null, internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768／769クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            int internalProtocol) {"""),
    ("11-arg javadoc internalProtocol", """\
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, null, internalProtocol);
    }""",
     """\
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, null, internalProtocol);
    }"""),
    ("internalProtocol validation", """\
        if (internalProtocol != ProtocolVersion.V1_20_1
                && internalProtocol != ProtocolVersion.V1_20_2
                && internalProtocol != ProtocolVersion.V1_20_4
                && internalProtocol != ProtocolVersion.V1_20_5
                && internalProtocol != ProtocolVersion.V1_21
                && internalProtocol != ProtocolVersion.V1_21_2) {
            throw new IllegalArgumentException(
                    "internalProtocol must be 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5), 767 (1.21) or 768 (1.21.2): "
                            + internalProtocol);
        }""",
     """\
        if (internalProtocol != ProtocolVersion.V1_20_1
                && internalProtocol != ProtocolVersion.V1_20_2
                && internalProtocol != ProtocolVersion.V1_20_4
                && internalProtocol != ProtocolVersion.V1_20_5
                && internalProtocol != ProtocolVersion.V1_21
                && internalProtocol != ProtocolVersion.V1_21_2
                && internalProtocol != ProtocolVersion.V1_21_4) {
            throw new IllegalArgumentException(
                    "internalProtocol must be 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2) "
                            + "or 769 (1.21.4): "
                            + internalProtocol);
        }"""),
    ("pack769 assignment", """\
        this.pack767 = pack767;
        this.pack768 = pack768;
        this.internalProtocol = internalProtocol;""",
     """\
        this.pack767 = pack767;
        this.pack768 = pack768;
        this.pack769 = pack769;
        this.internalProtocol = internalProtocol;"""),
    ("pack769 getter", """\
    /**
     * 768クライアントに送るリソースパック（無ければ null）。内部が768で
     * ない場合に使う差し替え用で、内部が768の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack768() {
        return pack768;
    }""",
     """\
    /**
     * 768クライアントに送るリソースパック（無ければ null）。内部が768で
     * ない場合に使う差し替え用で、内部が768の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack768() {
        return pack768;
    }

    /**
     * 769クライアントに送るリソースパック（無ければ null）。内部が769で
     * ない場合に使う差し替え用で、内部が769の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack769() {
        return pack769;
    }"""),
    ("internalProtocol() javadoc", """\
    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5、767＝1.21）。 */""",
     """\
    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5、767＝1.21、768＝1.21.2、769＝1.21.4）。 */"""),
    ("setPack769 in handshake", """\
                // 767／768用パックは resolveHandshake の引数には無いため、
                // セッションへ直接渡す（ProxySession#setPack767／setPack768）。
                // 767クライアントには pack767（pack_format 34）を出し、
                // 未設定の場合は ProxySession が pack766 に落とす。
                // 768クライアントには pack768（pack_format 42）を出し、
                // 未設定の場合は ProxySession が pack767 に落とす。
                session.setPack767(proxy.pack767);
                session.setPack768(proxy.pack768);""",
     """\
                // 767／768／769用パックは resolveHandshake の引数には無いため、
                // セッションへ直接渡す（ProxySession#setPack767／setPack768／setPack769）。
                // 767クライアントには pack767（pack_format 34）を出し、
                // 未設定の場合は ProxySession が pack766 に落とす。
                // 768クライアントには pack768（pack_format 42）を出し、
                // 未設定の場合は ProxySession が pack767 に落とす。
                // 769クライアントには pack769（pack_format 46）を出し、
                // 未設定の場合は ProxySession が pack768 に落とす。
                session.setPack767(proxy.pack767);
                session.setPack768(proxy.pack768);
                session.setPack769(proxy.pack769);"""),
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
    print("ClientProxy.java に 769 の対応を挿入した")


if __name__ == "__main__":
    main()
