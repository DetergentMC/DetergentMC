#!/usr/bin/env python3
"""769受け入れに伴い「769は新しすぎる」前提のテストを770へ移す。

各置換はファイル中にちょうど指定回数だけ現れること。
ClientJoinFlow の javadoc も769対応にする（ロジックは汎用のため変更なし）。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"
MAIN = ROOT / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"

# (ファイル, 旧, 新, 期待回数)
EDITS = [
    (TEST / "ClientAdapterTest.java",
     "        Handshake handshake = ClientAdapter.decide(769);",
     "        Handshake handshake = ClientAdapter.decide(770);", 1),
    (TEST / "ClientAdapterTest.java",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768), ClientAdapter.supportedClientProtocols());",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769), ClientAdapter.supportedClientProtocols());", 1),
    (TEST / "ClientAdapter764Test.java",
     "        // 763より古い／768より新しいは従来通り断る。",
     "        // 763より古い／769より新しいは従来通り断る。", 1),
    (TEST / "ClientAdapter764Test.java",
     "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(769).decision());",
     "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(770).decision());", 1),
    (TEST / "ClientAdapter764Test.java",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768), ClientAdapter.supportedClientProtocols());",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769), ClientAdapter.supportedClientProtocols());", 1),
    (TEST / "ClientAdapter766Test.java",
     "assertFalse(ClientAdapter.decide(769, 766).accepted());",
     "assertFalse(ClientAdapter.decide(770, 766).accepted());", 1),
    (TEST / "ClientAdapter767Test.java",
     "assertFalse(ClientAdapter.decide(769, 767).accepted());",
     "assertFalse(ClientAdapter.decide(770, 767).accepted());", 1),
    (TEST / "ClientAdapter768Test.java",
     "        // 769より新しい／763より古いは断る。",
     "        // 770より新しい／763より古いは断る。", 1),
    (TEST / "ClientAdapter768Test.java",
     "assertFalse(ClientAdapter.decide(769, 768).accepted());",
     "assertFalse(ClientAdapter.decide(770, 768).accepted());", 1),
    (TEST / "ClientAdapterCrossVersionTest.java",
     "ClientAdapter.decide(769,",
     "ClientAdapter.decide(770,", 4),
    (TEST / "ClientJoinFlow764Test.java",
     "new ClientJoinFlow(769)",
     "new ClientJoinFlow(770)", 1),
    (TEST / "ClientJoinFlow766Test.java",
     "new ClientJoinFlow(769)",
     "new ClientJoinFlow(770)", 1),
    (TEST / "ClientJoinFlow767Test.java",
     "new ClientJoinFlow(769)",
     "new ClientJoinFlow(770)", 1),
    (TEST / "ClientJoinFlow768Test.java",
     "new ClientJoinFlow(769)",
     "new ClientJoinFlow(770)", 1),
    (TEST / "ClientProxyTest.java",
     "                // 768は対応済みのため、拒否されるのは769（1.21.4以降の未対応番号）。",
     "                // 769は対応済みのため、拒否されるのは770（1.21.5以降の未対応番号）。", 1),
    (TEST / "ClientProxyTest.java",
     "client.sendPacket(new MinecraftPacket(0x00, handshakePayload(769, 2)));",
     "client.sendPacket(new MinecraftPacket(0x00, handshakePayload(770, 2)));", 1),
    (TEST / "ClientProxyTest.java",
     'assertTrue(reason.contains("769"), reason);',
     'assertTrue(reason.contains("770"), reason);', 1),
    (MAIN / "ClientJoinFlow.java",
     """ *   <li>1.21.2／1.21.3（768）：767と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は767から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>""",
     """ *   <li>1.21.2／1.21.3（768）：767と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は767から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>
 *   <li>1.21.4（769）：768と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は768から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>""", 1),
    (MAIN / "ClientJoinFlow.java",
     "     * @param clientProtocol クライアントのプロトコル番号（763か764か765か766か767か768）。",
     "     * @param clientProtocol クライアントのプロトコル番号（763か764か765か766か767か768か769）。", 1),
    (MAIN / "ClientJoinFlow.java",
     "    /** 設定フェーズがあるか（764と765と766と767と768はあり、763はなし）。 */",
     "    /** 設定フェーズがあるか（764と765と766と767と768と769はあり、763はなし）。 */", 1),
    (MAIN / "ClientJoinFlow.java",
     "     * ログインに成功した：ログイン → 設定フェーズ（764・765・766・767・768）かプレイ（763）。",
     "     * ログインに成功した：ログイン → 設定フェーズ（764・765・766・767・768・769）かプレイ（763）。", 1),
]


def main():
    for path, old, new, expected in EDITS:
        src = path.read_text(encoding="utf-8")
        n = src.count(old)
        if n != expected:
            print(f"NG: {path.name}: {old[:60]!r} が {n} 回（{expected} 回のはず）",
                  file=sys.stderr)
            sys.exit(1)
        path.write_text(src.replace(old, new), encoding="utf-8")
        print(f"OK: {path.name}: {old[:50]!r}")
    print("テストと ClientJoinFlow の769対応を入れた")


if __name__ == "__main__":
    main()
