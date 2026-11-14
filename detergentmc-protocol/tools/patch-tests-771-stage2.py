#!/usr/bin/env python3
"""771受け入れ（ステージ2）に伴い「771は新しすぎる」前提のテストを772へ移す。

各置換はファイル中にちょうど指定回数だけ現れること。
ClientJoinFlow の javadoc は771対応済み（patch-clientjoinflow-771.py）
のため、残った loginSucceeded の1行だけ直す。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"
MAIN = ROOT / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"

# (ファイル, 旧, 新, 期待回数)
EDITS = [
    (TEST / "ClientAdapterTest.java",
     "        Handshake handshake = ClientAdapter.decide(771);",
     "        Handshake handshake = ClientAdapter.decide(772);", 1),
    (TEST / "ClientAdapterTest.java",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770), ClientAdapter.supportedClientProtocols());",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771), ClientAdapter.supportedClientProtocols());", 1),
    (TEST / "ClientAdapter764Test.java",
     "        // 763より古い／770より新しいは従来通り断る。",
     "        // 763より古い／771より新しいは従来通り断る。", 1),
    (TEST / "ClientAdapter764Test.java",
     "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(771).decision());",
     "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(772).decision());", 1),
    (TEST / "ClientAdapter764Test.java",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770), ClientAdapter.supportedClientProtocols());",
     "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771), ClientAdapter.supportedClientProtocols());", 1),
    (TEST / "ClientAdapter766Test.java",
     "assertFalse(ClientAdapter.decide(771, 766).accepted());",
     "assertFalse(ClientAdapter.decide(772, 766).accepted());", 1),
    (TEST / "ClientAdapter767Test.java",
     "assertFalse(ClientAdapter.decide(771, 767).accepted());",
     "assertFalse(ClientAdapter.decide(772, 767).accepted());", 1),
    (TEST / "ClientAdapter768Test.java",
     "        // 771より新しい／763より古いは断る。",
     "        // 772より新しい／763より古いは断る。", 1),
    (TEST / "ClientAdapter768Test.java",
     "assertFalse(ClientAdapter.decide(771, 768).accepted());",
     "assertFalse(ClientAdapter.decide(772, 768).accepted());", 1),
    (TEST / "ClientAdapter769Test.java",
     "        // 771より新しい／763より古いは断る。",
     "        // 772より新しい／763より古いは断る。", 1),
    (TEST / "ClientAdapter769Test.java",
     "assertFalse(ClientAdapter.decide(771, 769).accepted());",
     "assertFalse(ClientAdapter.decide(772, 769).accepted());", 1),
    (TEST / "ClientAdapter770Test.java",
     "        // 771より新しい／763より古いは断る。",
     "        // 772より新しい／763より古いは断る。", 1),
    (TEST / "ClientAdapter770Test.java",
     "assertFalse(ClientAdapter.decide(771, 770).accepted());",
     "assertFalse(ClientAdapter.decide(772, 770).accepted());", 1),
    (TEST / "ClientAdapterCrossVersionTest.java",
     "ClientAdapter.decide(771,",
     "ClientAdapter.decide(772,", 4),
    (TEST / "ClientJoinFlow764Test.java",
     "new ClientJoinFlow(771)",
     "new ClientJoinFlow(772)", 1),
    (TEST / "ClientJoinFlow766Test.java",
     "new ClientJoinFlow(771)",
     "new ClientJoinFlow(772)", 1),
    (TEST / "ClientJoinFlow767Test.java",
     "new ClientJoinFlow(771)",
     "new ClientJoinFlow(772)", 1),
    (TEST / "ClientJoinFlow768Test.java",
     "new ClientJoinFlow(771)",
     "new ClientJoinFlow(772)", 1),
    (TEST / "ClientJoinFlow769Test.java",
     "new ClientJoinFlow(771)",
     "new ClientJoinFlow(772)", 1),
    (TEST / "ClientJoinFlow770Test.java",
     "new ClientJoinFlow(771)",
     "new ClientJoinFlow(772)", 1),
    (TEST / "ClientProxyTest.java",
     "                // 770は対応済みのため、拒否されるのは771（1.21.6以降の未対応番号）。",
     "                // 771は対応済みのため、拒否されるのは772（1.21.7以降の未対応番号）。", 1),
    (TEST / "ClientProxyTest.java",
     "client.sendPacket(new MinecraftPacket(0x00, handshakePayload(771, 2)));",
     "client.sendPacket(new MinecraftPacket(0x00, handshakePayload(772, 2)));", 1),
    (TEST / "ClientProxyTest.java",
     'assertTrue(reason.contains("771"), reason);',
     'assertTrue(reason.contains("772"), reason);', 1),
    (TEST / "ClientProxy767Test.java",
     "    void internalProtocol771IsStillRejected() throws Exception {",
     "    void internalProtocol772IsStillRejected() throws Exception {", 1),
    (TEST / "ClientProxy767Test.java",
     '            throw new AssertionError("expected IllegalArgumentException for 771");',
     '            throw new AssertionError("expected IllegalArgumentException for 772");', 1),
    (TEST / "ClientProxy767Test.java",
     "                    null, null, ProxyAuthConfig.defaults(), null, null, null, 771);",
     "                    null, null, ProxyAuthConfig.defaults(), null, null, null, 772);", 1),
    (TEST / "ClientProxy767Test.java",
     "                null, null, ProxyAuthConfig.defaults(), null, null, 771));",
     "                null, null, ProxyAuthConfig.defaults(), null, null, 772));", 1),
    (TEST / "ClientProxy768Test.java",
     "    void internalProtocol771IsStillRejected() throws Exception {",
     "    void internalProtocol772IsStillRejected() throws Exception {", 1),
    (TEST / "ClientProxy768Test.java",
     '            throw new AssertionError("expected IllegalArgumentException for 771");',
     '            throw new AssertionError("expected IllegalArgumentException for 772");', 1),
    (TEST / "ClientProxy768Test.java",
     "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, 771);",
     "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, 772);", 1),
    (TEST / "ClientProxy768Test.java",
     "                null, null, ProxyAuthConfig.defaults(), null, null, null, 771));",
     "                null, null, ProxyAuthConfig.defaults(), null, null, null, 772));", 1),
    (TEST / "ClientProxy769Test.java",
     "    void internalProtocol771IsStillRejected() throws Exception {",
     "    void internalProtocol772IsStillRejected() throws Exception {", 1),
    (TEST / "ClientProxy769Test.java",
     '            throw new AssertionError("expected IllegalArgumentException for 771");',
     '            throw new AssertionError("expected IllegalArgumentException for 772");', 1),
    (TEST / "ClientProxy769Test.java",
     "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 771);",
     "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 772);", 1),
    (TEST / "ClientProxy769Test.java",
     "                null, null, ProxyAuthConfig.defaults(), null, null, null, 771));",
     "                null, null, ProxyAuthConfig.defaults(), null, null, null, 772));", 1),
    (TEST / "ResourcePackBuilder767Test.java",
     "                () -> ResourcePackBuilder.packFormatForProtocol(771));",
     "                () -> ResourcePackBuilder.packFormatForProtocol(772));", 1),
    (TEST / "ResourcePackBuilder768Test.java",
     "                () -> ResourcePackBuilder.packFormatForProtocol(771));",
     "                () -> ResourcePackBuilder.packFormatForProtocol(772));", 1),
    (TEST / "ResourcePackBuilder769Test.java",
     "                () -> ResourcePackBuilder.packFormatForProtocol(771));",
     "                () -> ResourcePackBuilder.packFormatForProtocol(772));", 1),
    (TEST / "ResourcePackBuilder770Test.java",
     "                () -> ResourcePackBuilder.packFormatForProtocol(771));",
     "                () -> ResourcePackBuilder.packFormatForProtocol(772));", 1),
    (MAIN / "ClientJoinFlow.java",
     "     * ログインに成功した：ログイン → 設定フェーズ（764・765・766・767・768・769・770）かプレイ（763）。",
     "     * ログインに成功した：ログイン → 設定フェーズ（764・765・766・767・768・769・770・771）かプレイ（763）。", 1),
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
    print("テストと ClientJoinFlow の771対応を入れた")


if __name__ == "__main__":
    main()
