#!/usr/bin/env python3
"""patch-clientproxy-772.py から patch-clientproxy-773.py を生成する。

772の edit の new（＝773の old）をそのまま流用し、772→773の差分だけを
shift() で写す。ただし shift() は版の鎖・一覧を崩すため、以下は手書き。
- pack773 field／pack773 getter／setPack773 in handshake：
  挿入行の「pack_format 64」を「pack_format 69」に直す（773の値）。
- internalProtocol field javadoc／13-arg javadoc internalProtocol（2件）／
  internalProtocol() javadoc：末尾に「、773＝1.21.9／1.21.10」を足すだけ。
- internalProtocol validation：エラーメッセージに772の項を残したまま
  773の項を足す（shift は772の項を773に書き換えてしまう）。
- 15-arg constructor delegates to 16-arg：16引数コンストラクタが17引数へ
  委譲し、17引数の javadoc・署名を追加する（772の @param 一覧を shift
  すると全部ずれてしまうため手書き）。
"""
import ast, difflib, importlib.util
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gen773ps", TOOLS / "gen-773-proxysession.py")
g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
shift = g.shift


def load_edits(fn):
    src = open(TOOLS / fn, encoding="utf-8").read()
    tree = ast.parse(src)
    assigns = {n.targets[0].id: n.value for n in tree.body
               if isinstance(n, ast.Assign) and len(n.targets)==1 and isinstance(n.targets[0], ast.Name)}
    return ast.literal_eval(assigns["EDITS"])


e772 = {name: (old, new) for name, old, new in load_edits("patch-clientproxy-772.py")}
live = open(TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "ClientProxy.java",
            encoding="utf-8").read()


def apply_delta(old773, old772, new772):
    a = old772.split("\n")
    b = new772.split("\n")
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    out = old773.split("\n")
    for tag, i1, i2, j1, j2 in reversed(sm.get_opcodes()):
        if tag == "equal" or tag == "delete":
            continue
        new_lines = [shift(x) for x in b[j1:j2]]
        # 773の新規エントリの pack_format は 69（shift は 64 のままにする）。
        new_lines = [x.replace("pack_format 64", "pack_format 69")
                     if "773" in x else x for x in new_lines]
        if tag == "insert":
            out[j1:j1] = new_lines
        elif tag == "replace":
            out[j1:j2] = new_lines
    return "\n".join(out)


def append_773_proto(old773):
    """「772＝1.21.7／1.21.8）。」の直後に「、773＝1.21.9／1.21.10」を足す。"""
    old = "772＝1.21.7／1.21.8）。"
    new = "772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。"
    assert old773.count(old) == 1, old773.count(old)
    return old773.replace(old, new, 1)


# 17引数コンストラクタの javadoc・署名（16引数の javadoc を土台にする）
CTOR773_JAVADOC = '''    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770／771／772／773クライアント用のパックを指定する。
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
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param pack772         772クライアントに送るリソースパック
     *                        （pack_format 64）。内部が772でない場合に使う。
     *                        {@code null} の場合は772クライアントにも
     *                        771用パックを出す（ProxySession の既定の扱い）
     * @param pack773         773クライアントに送るリソースパック
     *                        （pack_format 69）。内部が773でない場合に使う。
     *                        {@code null} の場合は773クライアントにも
     *                        772用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, int internalProtocol) {'''


def ctor773(old773):
    """16引数コンストラクタの署名のあとに委譲本体と17引数を追加する。"""
    sig = '''    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, int internalProtocol) {'''
    assert old773.count(sig) == 1, old773.count(sig)
    body = '''    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, null,
                internalProtocol);
    }

''' + CTOR773_JAVADOC
    return old773.replace(sig, body, 1)


new_edits = []
NAME773 = {
    "14-arg constructor delegates to 15-arg": "16-arg constructor delegates to 17-arg",
}
for name772, (old772, new772) in e772.items():
    name773 = NAME773.get(name772, name772.replace("772", "773"))
    old773 = new772
    assert live.count(old773) == 1, f"{name773}: old_773 が live に {live.count(old773)} 回"
    if name773 in ("internalProtocol field javadoc",
                   "13-arg javadoc internalProtocol",
                   "13-arg javadoc internalProtocol (max pack769)",
                   "internalProtocol() javadoc"):
        new773 = append_773_proto(old773)
    elif name773 == "internalProtocol validation":
        # 鎖は手書き（apply_delta の replace は V1_21_6 行を落とす）。
        assert "                && internalProtocol != ProtocolVersion.V1_21_7) {" in old773
        new773 = old773.replace(
            "                && internalProtocol != ProtocolVersion.V1_21_7) {",
            "                && internalProtocol != ProtocolVersion.V1_21_7\n"
            "                && internalProtocol != ProtocolVersion.V1_21_9) {", 1)
        assert "771 (1.21.6) or 772 (1.21.7／1.21.8): " in new773
        new773 = new773.replace("771 (1.21.6) or 772 (1.21.7／1.21.8): ",
                                "771 (1.21.6), 772 (1.21.7／1.21.8) or 773 (1.21.9／1.21.10): ")
    elif name773 == "16-arg constructor delegates to 17-arg":
        new773 = ctor773(old773)
    elif name773 == "pack773 field":
        # difflib が pack771 のフィールドを pack772 と誤認して間に挿すため、
        # pack772 フィールドのあとへ追加する。
        new773 = old773 + """
    /**
     * 773クライアントに送るリソースパック（pack_format 69）。内部が773で
     * ない場合にだけ使う。内部が773の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack773(ResourcePack)} で渡す
     * （{@code resolveHandshake} には773用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack773;"""
    elif name773 == "pack773 getter":
        # pack772 getter のあとへ追加する。
        new773 = old773 + """
    /**
     * 773クライアントに送るリソースパック（無ければ null）。内部が773で
     * ない場合に使う差し替え用で、内部が773の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack773() {
        return pack773;
    }"""
    elif name773 == "pack773 assignment":
        # pack772 の代入のあとへ追加する。
        assert "        this.pack772 = pack772;\n" in old773
        new773 = old773.replace("        this.pack772 = pack772;\n",
                                "        this.pack772 = pack772;\n        this.pack773 = pack773;\n", 1)
    else:
        new773 = apply_delta(old773, old772, new772)
    new_edits.append((name773, old773, new773))

doc = '"""ClientProxy.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。\n\npack773 フィールド・17引数コンストラクタ・getter・session への受け渡し。\n16引数コンストラクタは pack773=null で17引数へ委譲する（14引数→15引数・\n15引数→16引数の流儀と同じ）。リレー判定は ProxySession.resolveHandshake の汎用比較\n（protocol == serverProtocol）のため変更不要。\n各アンカーはファイル中にちょうど1回だけ現れること。\n"""'
out_lines = [doc, "import sys", "from pathlib import Path", ""]
out_lines.append("PATH = Path(__file__).resolve().parent.parent / \"src\" / \"main\" / \"java\" \\")
out_lines.append("    / \"io\" / \"detergentmc\" / \"protocol\" / \"ClientProxy.java\"")
out_lines.append("")
out_lines.append("EDITS = [")
for name, old, new in new_edits:
    out_lines.append(f"    ({name!r}, {old!r},")
    out_lines.append(f"     {new!r}),")
out_lines.append("]")
out_lines.append("")
out_lines.append("")
out_lines.append("def main():")
out_lines.append("    src = PATH.read_text(encoding=\"utf-8\")")
out_lines.append("    for name, old, new in EDITS:")
out_lines.append("        n = src.count(old)")
out_lines.append("        if n != 1:")
out_lines.append("            print(f\"NG: {name}: anchor が {n} 回見つかった（1回のはず）\", file=sys.stderr)")
out_lines.append("            sys.exit(1)")
out_lines.append("        src = src.replace(old, new, 1)")
out_lines.append("        print(f\"OK: {name}\")")
out_lines.append("    PATH.write_text(src, encoding=\"utf-8\")")
out_lines.append("    print(\"ClientProxy.java に 773 の対応を挿入した\")")
out_lines.append("")
out_lines.append("")
out_lines.append("if __name__ == \"__main__\":")
out_lines.append("    main()")
open(TOOLS / "patch-clientproxy-773.py", "w", encoding="utf-8").write("\n".join(out_lines) + "\n")
print(f"patch-clientproxy-773.py を生成した（{len(new_edits)} edits）")
