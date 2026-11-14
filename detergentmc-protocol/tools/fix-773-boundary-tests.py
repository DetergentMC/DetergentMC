#!/usr/bin/env python3
"""773受け入れに伴う既存テストの境界更新（773→774）。

773が受け入れ対象になったため、「773は新しすぎる／未知」を主張する
既存テストは境界を774へ移す。fix-772-boundary-tests.py の流儀。
対象：
- ClientAdapter*Test／ClientAdapterTest／ClientAdapterCrossVersionTest：
  decide(773)／decide(773, X)→774、supportedClientProtocols 一覧に773追加。
- ClientJoinFlow*Test：new ClientJoinFlow(773)→774。
- ClientProxy767/768/769Test：internalProtocol773IsStillRejected→774、
  tenArg／elevenArgコンストラクタの assertThrows(773)→774。
- ClientProxyTest：unsupportedProtocolLoginIsRejected の handshake(773)→774。
- ResourcePackBuilder767/768/769/770/771/772Test：packFormatForProtocol(773)→774。

各置換はファイル中にちょうど指定回数だけ現れること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROTO_TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"


def sub(path, label, old, new, count=1):
    src = path.read_text(encoding="utf-8")
    n = src.count(old)
    if n != count:
        print(f"NG: {path.name} {label}: {old[:70]!r} が {n} 回（{count} 回のはず）",
              file=sys.stderr)
        sys.exit(1)
    path.write_text(src.replace(old, new, count), encoding="utf-8")
    print(f"OK: {path.name} {label}")


def all_sub(path, label, old, new):
    src = path.read_text(encoding="utf-8")
    n = src.count(old)
    if n < 1:
        print(f"NG: {path.name} {label}: {old[:70]!r} が 0 回", file=sys.stderr)
        sys.exit(1)
    path.write_text(src.replace(old, new), encoding="utf-8")
    print(f"OK: {path.name} {label} ({n} 回)")


# ------------------------------------------------------------------
# ClientAdapter
# ------------------------------------------------------------------
sub(PROTO_TEST / "ClientAdapter764Test.java", "decide 773->774",
    "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(773).decision());",
    "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(774).decision());")
sub(PROTO_TEST / "ClientAdapter764Test.java", "supported list +773",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771, 772), ClientAdapter.supportedClientProtocols());",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773), ClientAdapter.supportedClientProtocols());")
sub(PROTO_TEST / "ClientAdapterTest.java", "rejectsNewer 773->774",
    "        Handshake handshake = ClientAdapter.decide(773);",
    "        Handshake handshake = ClientAdapter.decide(774);")
sub(PROTO_TEST / "ClientAdapterTest.java", "supported list +773",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771, 772), ClientAdapter.supportedClientProtocols());",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773), ClientAdapter.supportedClientProtocols());")
for name, server in [("ClientAdapter766Test", 766), ("ClientAdapter767Test", 767),
                     ("ClientAdapter768Test", 768), ("ClientAdapter769Test", 769),
                     ("ClientAdapter770Test", 770), ("ClientAdapter771Test", 771),
                     ("ClientAdapter772Test", 772)]:
    sub(PROTO_TEST / f"{name}.java", "decide 773->774",
        f"assertFalse(ClientAdapter.decide(773, {server}).accepted());",
        f"assertFalse(ClientAdapter.decide(774, {server}).accepted());")
for name in ["ClientAdapter768Test", "ClientAdapter769Test",
             "ClientAdapter770Test", "ClientAdapter771Test", "ClientAdapter772Test"]:
    sub(PROTO_TEST / f"{name}.java", "comment 773->774",
        "        // 773より新しい／763より古いは断る。",
        "        // 774より新しい／763より古いは断る。")
all_sub(PROTO_TEST / "ClientAdapterCrossVersionTest.java", "decide 773->774",
        "ClientAdapter.decide(773,", "ClientAdapter.decide(774,")

# ------------------------------------------------------------------
# ClientJoinFlow
# ------------------------------------------------------------------
for name in ["ClientJoinFlow764Test", "ClientJoinFlow766Test", "ClientJoinFlow767Test",
             "ClientJoinFlow768Test", "ClientJoinFlow769Test", "ClientJoinFlow770Test",
             "ClientJoinFlow771Test", "ClientJoinFlow772Test"]:
    sub(PROTO_TEST / f"{name}.java", "JoinFlow 773->774",
        "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(773));",
        "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(774));")

# ------------------------------------------------------------------
# ClientProxy
# ------------------------------------------------------------------
for name in ["ClientProxy767Test", "ClientProxy768Test", "ClientProxy769Test"]:
    sub(PROTO_TEST / f"{name}.java", "internalProtocol774",
        "    void internalProtocol773IsStillRejected() throws Exception {",
        "    void internalProtocol774IsStillRejected() throws Exception {")
    sub(PROTO_TEST / f"{name}.java", "internalProtocol message 773->774",
        '            throw new AssertionError("expected IllegalArgumentException for 773");',
        '            throw new AssertionError("expected IllegalArgumentException for 774");')
sub(PROTO_TEST / "ClientProxy767Test.java", "internalProtocol arg 773->774",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, 773);",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, 774);")
sub(PROTO_TEST / "ClientProxy768Test.java", "internalProtocol arg 773->774",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, 773);",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, 774);")
sub(PROTO_TEST / "ClientProxy769Test.java", "internalProtocol arg 773->774",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 773);",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 774);")
sub(PROTO_TEST / "ClientProxy767Test.java", "tenArg assertThrows 773->774",
    "                null, null, ProxyAuthConfig.defaults(), null, null, 773));",
    "                null, null, ProxyAuthConfig.defaults(), null, null, 774));")
for name in ["ClientProxy768Test", "ClientProxy769Test"]:
    sub(PROTO_TEST / f"{name}.java", "elevenArg assertThrows 773->774",
        "                null, null, ProxyAuthConfig.defaults(), null, null, null, 773));",
        "                null, null, ProxyAuthConfig.defaults(), null, null, null, 774));")
sub(PROTO_TEST / "ClientProxyTest.java", "handshake 773->774",
    "                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(773, 2)));",
    "                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(774, 2)));")
sub(PROTO_TEST / "ClientProxyTest.java", "reason 773->774",
    '                assertTrue(reason.contains("773"), reason);',
    '                assertTrue(reason.contains("774"), reason);')

# ------------------------------------------------------------------
# ResourcePackBuilder
# ------------------------------------------------------------------
for name in ["ResourcePackBuilder767Test", "ResourcePackBuilder768Test",
             "ResourcePackBuilder769Test", "ResourcePackBuilder770Test",
             "ResourcePackBuilder771Test", "ResourcePackBuilder772Test"]:
    sub(PROTO_TEST / f"{name}.java", "packFormat 773->774",
        "                () -> ResourcePackBuilder.packFormatForProtocol(773));",
        "                () -> ResourcePackBuilder.packFormatForProtocol(774));")

print("773の境界更新を入れた")
