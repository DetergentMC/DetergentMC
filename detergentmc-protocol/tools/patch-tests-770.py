#!/usr/bin/env python3
"""770受け入れに伴い「770は新しすぎる」前提のテストを771へ移す。

各置換はファイル中にちょうど指定回数だけ現れること。
ClientJoinFlow の javadoc も770対応にする（ロジックは汎用のため変更なし）。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"
MAIN = ROOT / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"

# (ファイル, 旧, 新, 期待回数)
EDITS = [
    (TEST / "ClientAdapterTest.java",
     "        Handshake handshake = ClientAdapter.decide(770);",
     "        Handshake handshake = ClientAdapter.decide(771);", 1),
    (TEST / "ClientAdapterTest.java",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769), ClientAdapter.supportedClientProtocols());",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770), ClientAdapter.supportedClientProtocols());", 1),
    (TEST / "ClientAdapter764Test.java",
     "        // 763より古い／769より新しいは従来通り断る。",
     "        // 763より古い／770より新しいは従来通り断る。", 1),
    (TEST / "ClientAdapter764Test.java",
     "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(770).decision());",
     "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(771).decision());", 1),
    (TEST / "ClientAdapter764Test.java",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769), ClientAdapter.supportedClientProtocols());",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770), ClientAdapter.supportedClientProtocols());", 1),
    (TEST / "ClientAdapter766Test.java",
     "assertFalse(ClientAdapter.decide(770, 766).accepted());",
     "assertFalse(ClientAdapter.decide(771, 766).accepted());", 1),
    (TEST / "ClientAdapter767Test.java",
     "assertFalse(ClientAdapter.decide(770, 767).accepted());",
     "assertFalse(ClientAdapter.decide(771, 767).accepted());", 1),
    (TEST / "ClientAdapter768Test.java",
     "        // 770より新しい／763より古いは断る。",
     "        // 771より新しい／763より古いは断る。", 1),
    (TEST / "ClientAdapter768Test.java",
     "assertFalse(ClientAdapter.decide(770, 768).accepted());",
     "assertFalse(ClientAdapter.decide(771, 768).accepted());", 1),
    (TEST / "ClientAdapterCrossVersionTest.java",
     "ClientAdapter.decide(770,",
     "ClientAdapter.decide(771,", 4),
    (TEST / "ClientJoinFlow764Test.java",
     "new ClientJoinFlow(770)",
     "new ClientJoinFlow(771)", 1),
    (TEST / "ClientJoinFlow766Test.java",
     "new ClientJoinFlow(770)",
     "new ClientJoinFlow(771)", 1),
    (TEST / "ClientJoinFlow767Test.java",
     "new ClientJoinFlow(770)",
     "new ClientJoinFlow(771)", 1),
    (TEST / "ClientJoinFlow768Test.java",
     "new ClientJoinFlow(770)",
     "new ClientJoinFlow(771)", 1),
    (TEST / "ClientProxyTest.java",
     "                // 769は対応済みのため、拒否されるのは770（1.21.5以降の未対応番号）。",
     "                // 770は対応済みのため、拒否されるのは771（1.21.6以降の未対応番号）。", 1),
    (TEST / "ClientProxyTest.java",
     "client.sendPacket(new MinecraftPacket(0x00, handshakePayload(770, 2)));",
     "client.sendPacket(new MinecraftPacket(0x00, handshakePayload(771, 2)));", 1),
    (TEST / "ClientProxyTest.java",
     'assertTrue(reason.contains("770"), reason);',
     'assertTrue(reason.contains("771"), reason);', 1),
    (MAIN / "ClientJoinFlow.java",
     """ *   <li>1.21.2／1.21.3（768）：767と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は767から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>
 *   <li>1.21.4（769）：768と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は768から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>""",
     """ *   <li>1.21.2／1.21.3（768）：767と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は767から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>
 *   <li>1.21.4（769）：768と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は768から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>
 *   <li>1.21.5（770）：769と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は769から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>""", 1),
    (MAIN / "ClientJoinFlow.java",
     "     * @param clientProtocol クライアントのプロトコル番号（763か764か765か766か767か768か769）。",
     "     * @param clientProtocol クライアントのプロトコル番号（763か764か765か766か767か768か769か770）。", 1),
    (MAIN / "ClientJoinFlow.java",
     "    /** 設定フェーズがあるか（764と765と766と767と768と769はあり、763はなし）。 */",
     "    /** 設定フェーズがあるか（764と765と766と767と768と769と770はあり、763はなし）。 */", 1),
    (MAIN / "ClientJoinFlow.java",
     "     * ログインに成功した：ログイン → 設定フェーズ（764・765・766・767・768・769）かプレイ（763）。",
     "     * ログインに成功した：ログイン → 設定フェーズ（764・765・766・767・768・769・770）かプレイ（763）。", 1),
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
    print("テストと ClientJoinFlow の770対応を入れた")


if __name__ == "__main__":
    main()
