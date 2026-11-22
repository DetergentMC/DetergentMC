#!/usr/bin/env python3
"""ClientAdapter／ClientJoinFlow／ClientProxy に 477（1.14）を追加する。

477 は 480 とパケットID・形式が一致し、レジストリもワイヤ上一致
（パケットID・形式の差分0件、レジストリは変換表に現れない
memory_module_type の1件だけ。実 jar の data-generator ダンプと
minecraft-data の protocol.json（1.14 と 1.14.1 がバイト一致）で
証明済み）のため、480 の分岐へ 477 を足す形にする。最古の対応
クライアントは 477 になる。

使い方: python3 tools/patch-477-client.py
冪等：477 が既にあれば何もしない。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROTO = ROOT / "src/main/java/io/detergentmc/protocol"


def fail(msg):
    print(f"NG: {msg}", file=sys.stderr)
    sys.exit(1)


def rep(text, old, new, count=1):
    n = text.count(old)
    if n != count:
        fail(f"置換対象 {old[:60]!r} が {n} 件（期待 {count}）")
    return text.replace(old, new)


def patch_client_adapter():
    p = PROTO / "ClientAdapter.java"
    text = p.read_text(encoding="utf-8")
    if "ProtocolVersion.V1_14;" in text or "V1_14)" in text and "477" in text:
        print("skip（既存）: ClientAdapter は 477 済み")
        return
    # 1. 最古の対応クライアント。
    text = rep(text,
               "    /** 受け入れる一番古いクライアントのプロトコル（1.14.1）。 */\n"
               "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_14_1;",
               "    /** 受け入れる一番古いクライアントのプロトコル（1.14）。 */\n"
               "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_14;")
    # 2. decide() の受け入れ一覧の先頭に 477。
    text = rep(text,
               "        if (clientProtocol == ProtocolVersion.V1_14_1\n",
               "        if (clientProtocol == ProtocolVersion.V1_14\n"
               "                || clientProtocol == ProtocolVersion.V1_14_1\n")
    # 3. decide() の 477 専用メッセージ（480 分岐の前）。
    text = rep(text,
               "            if (clientProtocol == ProtocolVersion.V1_14_1) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル480（1.14.1）のクライアントを変換して受け入れます。\"",
               "            if (clientProtocol == ProtocolVersion.V1_14) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル477（1.14）のクライアントを変換して受け入れます。\"\n"
               "                                + \"設定フェーズの省略とブロックの代替が必要です。\"\n"
               "                                + \"パケットID・形式は480（1.14.1）と完全一致（差分0件、\"\n"
               "                                + \"minecraft-dataのprotocol.jsonがバイト単位で一致）で、\"\n"
               "                                + \"レジストリも変換表に現れない memory_module_type の1件を除き\"\n"
               "                                + \"実jarのデータ生成ダンプで一致を確認済みのため、変換は480の経路を共用します。\"\n"
               "                                + \"掘削の確認応答 acknowledge_player_digging は477にも480にも無いため落とします。\"\n"
               "                                + \"Trade List の各取引には demand が、末尾には canRestock が無いため付け外しします。\"\n"
               "                                + \"Join Game／Respawn に hashedSeed は無く、チャンクのバイオームは\"\n"
               "                                + \"バッファ末尾の256件、出現には末尾メタデータが付きます。\"\n"
               "                                + \"チャットは署名なしの旧式で、チャンクの光は別パケット、\"\n"
               "                                + \"出現は5種に分かれています。\");\n"
               "            }\n"
               "            if (clientProtocol == ProtocolVersion.V1_14_1) {\n"
               "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
               "                        \"プロトコル480（1.14.1）のクライアントを変換して受け入れます。\"")
    # 4. 古すぎる側の注記と文言。
    text = rep(text,
               "            // 480 未満、および 481〜484・486〜489・491〜497・499〜572・574〜757 の未対応帯（1.14、1.16〜1.17.x 等）は「古すぎる」。\n"
               "            // 480・485・490・498・573・575・578 自体は上の分岐で受け入れ済みのためここには来ない。",
               "            // 477 未満、および 478〜479・481〜484・486〜489・491〜497・499〜572・574〜757 の未対応帯（1.13.x以前、1.16〜1.17.x 等）は「古すぎる」。\n"
               "            // 477・480・485・490・498・573・575・578 自体は上の分岐で受け入れ済みのためここには来ない。")
    text = rep(text,
               "                            + \"1.14.1（480）以降のクライアントで参加してください。\");",
               "                            + \"1.14（477）以降のクライアントで参加してください。\");")
    # 5. supportedClientProtocols() の注記と本体（OLDEST が 477 になるため
    # 480 を明示する）。
    text = rep(text,
               "        // OLDEST（480）だけでは485・490・498・573・575・578・758・759・760が漏れるため、485・490・498・573・575・578・758・759・760は明示する。",
               "        // OLDEST（477）だけでは480・485・490・498・573・575・578・758・759・760が漏れるため、480・485・490・498・573・575・578・758・759・760は明示する。")
    text = rep(text,
               "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_14_2, ProtocolVersion.V1_14_3, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2, ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3,",
               "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_14_1, ProtocolVersion.V1_14_2, ProtocolVersion.V1_14_3, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2, ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3,")
    # 6. versionName()。
    text = rep(text,
               "        if (protocol == ProtocolVersion.V1_14_1) {\n"
               "            return \"1.14.1\";\n"
               "        }",
               "        if (protocol == ProtocolVersion.V1_14) {\n"
               "            return \"1.14\";\n"
               "        }\n"
               "        if (protocol == ProtocolVersion.V1_14_1) {\n"
               "            return \"1.14.1\";\n"
               "        }")
    p.write_text(text, encoding="utf-8")
    print("ClientAdapter.java を更新した")


def patch_client_join_flow():
    p = PROTO / "ClientJoinFlow.java"
    text = p.read_text(encoding="utf-8")
    if "ProtocolVersion.V1_14\n" in text:
        print("skip（既存）: ClientJoinFlow は 477 済み")
        return
    text = rep(text,
               "クライアントのプロトコル番号（480か485か490か498か573か",
               "クライアントのプロトコル番号（477か480か485か490か498か573か")
    text = rep(text,
               "                || clientProtocol == ProtocolVersion.V1_14_1\n"
               "                ? List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.PLAY, Stage.JOINED, Stage.IN_GAME)",
               "                || clientProtocol == ProtocolVersion.V1_14_1\n"
               "                || clientProtocol == ProtocolVersion.V1_14\n"
               "                ? List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.PLAY, Stage.JOINED, Stage.IN_GAME)")
    text = rep(text,
               "480と485と490と498と573と575と578と758と759と760と761と762と763はなし",
               "477と480と485と490と498と573と575と578と758と759と760と761と762と763はなし")
    p.write_text(text, encoding="utf-8")
    print("ClientJoinFlow.java を更新した")


def patch_client_proxy():
    p = PROTO / "ClientProxy.java"
    text = p.read_text(encoding="utf-8")
    if "pack477" in text:
        print("skip（既存）: ClientProxy は 477 済み")
        return
    # 1. pack477 フィールド（pack480 の前）。
    text = rep(text,
               "    /**\n"
               "     * 480クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 485 と同じ後付けの流儀（{@link #setPack480(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack480;",
               "    /**\n"
               "     * 477クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 480 と同じ後付けの流儀（{@link #setPack477(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack477;\n"
               "    /**\n"
               "     * 480クライアントに送るリソースパック（pack_format 4）。無ければ null。\n"
               "     * 485 と同じ後付けの流儀（{@link #setPack480(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack480;")
    # 2. コンストラクタ検証（3件とも）。
    text = rep(text,
               "        if (internalProtocol != ProtocolVersion.V1_14_1\n",
               "        if (internalProtocol != ProtocolVersion.V1_14\n"
               "                && internalProtocol != ProtocolVersion.V1_14_1\n", 3)
    text = rep(text,
               '"internalProtocol must be 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),',
               '"internalProtocol must be 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),', 3)
    # 3. pack477 アクセサ（setPack480 の前）。
    text = rep(text,
               "    /**\n"
               "     * 480クライアントに送るリソースパックを設定する\n",
               "    /**\n"
               "     * 477クライアントに送るリソースパックを設定する\n"
               "     * （{@code detergentmc.yml} の {@code resource-pack.url-477} 等）。\n"
               "     * 起動（{@link #start()}）前に呼ぶこと。\n"
               "     */\n"
               "    public void setPack477(ResourcePack pack477) {\n"
               "        this.pack477 = pack477;\n"
               "    }\n\n"
               "    /** 477クライアントに送るリソースパック（未設定なら null）。 */\n"
               "    public ResourcePack pack477() {\n"
               "        return pack477;\n"
               "    }\n\n"
               "    /**\n"
               "     * 480クライアントに送るリソースパックを設定する\n")
    # 4. セッションへの受け渡し。
    text = rep(text,
               "                session.setPack480(proxy.pack480);",
               "                session.setPack477(proxy.pack477);\n"
               "                session.setPack480(proxy.pack480);")
    p.write_text(text, encoding="utf-8")
    print("ClientProxy.java を更新した")


def main():
    patch_client_adapter()
    patch_client_join_flow()
    patch_client_proxy()
    return 0


if __name__ == "__main__":
    sys.exit(main())
