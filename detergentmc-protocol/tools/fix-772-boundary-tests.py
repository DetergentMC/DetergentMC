#!/usr/bin/env python3
"""772受け入れに伴う既存テストの境界更新（772→773）＋Bedrockの実バグ修正。

772が受け入れ対象になったため、「772は新しすぎる／未知」を主張する
既存テストは境界を773へ移す。対象：
- ClientAdapter*Test／ClientAdapterTest／ClientAdapterCrossVersionTest：
  decide(772)／decide(772, X)→773、supportedClientProtocols 一覧に772追加。
- ClientJoinFlow*Test：new ClientJoinFlow(772)→773。
- ClientProxy767/768/769Test：internalProtocol772IsStillRejected→773、
  tenArg／elevenArgコンストラクタの assertThrows(772)→773。
- ClientProxyTest：unsupportedProtocolLoginIsRejected の handshake(772)→773。
- ResourcePackBuilder767/768/769/770/771Test：packFormatForProtocol(772)→773。

別件の実バグ：BedrockEntityTranslator.parseUpdateAttributes の
属性名 dispatch から769分岐が落ち、769が attributeName766 に誤って
落ちていた（JavaBackendConnection769Test.updateAttributes769…が失敗）。
HEADの連鎖（772分岐を先頭に追加）に復旧する。

各置換はファイル中にちょうど指定回数だけ現れること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROTO_TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"
BEDROCK_MAIN = (ROOT.parent / "detergentmc-bedrock" / "src" / "main" / "java"
                / "io" / "detergentmc" / "bedrock" / "BedrockEntityTranslator.java")


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
sub(PROTO_TEST / "ClientAdapter764Test.java", "decide 772->773",
    "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(772).decision());",
    "assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(773).decision());")
sub(PROTO_TEST / "ClientAdapter764Test.java", "supported list +772",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771), ClientAdapter.supportedClientProtocols());",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771, 772), ClientAdapter.supportedClientProtocols());")
sub(PROTO_TEST / "ClientAdapterTest.java", "rejectsNewer 772->773",
    "        Handshake handshake = ClientAdapter.decide(772);",
    "        Handshake handshake = ClientAdapter.decide(773);")
sub(PROTO_TEST / "ClientAdapterTest.java", "supported list +772",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771), ClientAdapter.supportedClientProtocols());",
    "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771, 772), ClientAdapter.supportedClientProtocols());")
for name, server in [("ClientAdapter766Test", 766), ("ClientAdapter767Test", 767),
                     ("ClientAdapter768Test", 768), ("ClientAdapter769Test", 769),
                     ("ClientAdapter770Test", 770), ("ClientAdapter771Test", 771)]:
    sub(PROTO_TEST / f"{name}.java", "decide 772->773",
        f"assertFalse(ClientAdapter.decide(772, {server}).accepted());",
        f"assertFalse(ClientAdapter.decide(773, {server}).accepted());")
for name in ["ClientAdapter768Test", "ClientAdapter769Test",
             "ClientAdapter770Test", "ClientAdapter771Test"]:
    sub(PROTO_TEST / f"{name}.java", "comment 772->773",
        "        // 772より新しい／763より古いは断る。",
        "        // 773より新しい／763より古いは断る。")
all_sub(PROTO_TEST / "ClientAdapterCrossVersionTest.java", "decide 772->773",
        "ClientAdapter.decide(772,", "ClientAdapter.decide(773,")

# ------------------------------------------------------------------
# ClientJoinFlow
# ------------------------------------------------------------------
for name in ["ClientJoinFlow764Test", "ClientJoinFlow766Test", "ClientJoinFlow767Test",
             "ClientJoinFlow768Test", "ClientJoinFlow769Test", "ClientJoinFlow770Test",
             "ClientJoinFlow771Test"]:
    sub(PROTO_TEST / f"{name}.java", "JoinFlow 772->773",
        "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(772));",
        "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(773));")

# ------------------------------------------------------------------
# ClientProxy
# ------------------------------------------------------------------
for name in ["ClientProxy767Test", "ClientProxy768Test", "ClientProxy769Test"]:
    sub(PROTO_TEST / f"{name}.java", "internalProtocol773",
        "    void internalProtocol772IsStillRejected() throws Exception {",
        "    void internalProtocol773IsStillRejected() throws Exception {")
    sub(PROTO_TEST / f"{name}.java", "internalProtocol message 772->773",
        '            throw new AssertionError("expected IllegalArgumentException for 772");',
        '            throw new AssertionError("expected IllegalArgumentException for 773");')
sub(PROTO_TEST / "ClientProxy767Test.java", "internalProtocol arg 772->773",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, 772);",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, 773);")
sub(PROTO_TEST / "ClientProxy768Test.java", "internalProtocol arg 772->773",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, 772);",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, 773);")
sub(PROTO_TEST / "ClientProxy769Test.java", "internalProtocol arg 772->773",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 772);",
    "                    null, null, ProxyAuthConfig.defaults(), null, null, null, null, null, 773);")
sub(PROTO_TEST / "ClientProxy767Test.java", "tenArg assertThrows 772->773",
    "                null, null, ProxyAuthConfig.defaults(), null, null, 772));",
    "                null, null, ProxyAuthConfig.defaults(), null, null, 773));")
for name in ["ClientProxy768Test", "ClientProxy769Test"]:
    sub(PROTO_TEST / f"{name}.java", "elevenArg assertThrows 772->773",
        "                null, null, ProxyAuthConfig.defaults(), null, null, null, 772));",
        "                null, null, ProxyAuthConfig.defaults(), null, null, null, 773));")
sub(PROTO_TEST / "ClientProxyTest.java", "handshake 772->773",
    "                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(772, 2)));",
    "                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(773, 2)));")
sub(PROTO_TEST / "ClientProxyTest.java", "reason 772->773",
    '                assertTrue(reason.contains("772"), reason);',
    '                assertTrue(reason.contains("773"), reason);')

# ------------------------------------------------------------------
# ResourcePackBuilder
# ------------------------------------------------------------------
for name in ["ResourcePackBuilder767Test", "ResourcePackBuilder768Test",
             "ResourcePackBuilder769Test", "ResourcePackBuilder770Test",
             "ResourcePackBuilder771Test"]:
    sub(PROTO_TEST / f"{name}.java", "packFormat 772->773",
        "                () -> ResourcePackBuilder.packFormatForProtocol(772));",
        "                () -> ResourcePackBuilder.packFormatForProtocol(773));")

# ------------------------------------------------------------------
# Bedrock：parseUpdateAttributes の769分岐を復旧（実バグ修正）
# ------------------------------------------------------------------
sub(BEDROCK_MAIN, "parseUpdateAttributes 769 branch",
    """                String name = serverProtocol == RegistryRemapper.PROTOCOL_772
                        ? RegistryRemapper.attributeName772(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_771
                                ? RegistryRemapper.attributeName771(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_770
                                ? RegistryRemapper.attributeName769(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_768
                                        ? RegistryRemapper.attributeName768(keyId)
                                        : serverProtocol == RegistryRemapper.PROTOCOL_767
                                                ? RegistryRemapper.attributeName767(keyId)
                                                : RegistryRemapper.attributeName766(keyId);""",
    """                String name = serverProtocol == RegistryRemapper.PROTOCOL_772
                        ? RegistryRemapper.attributeName772(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_771
                                ? RegistryRemapper.attributeName771(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_770
                                ? RegistryRemapper.attributeName770(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_769
                                ? RegistryRemapper.attributeName769(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_768
                                        ? RegistryRemapper.attributeName768(keyId)
                                        : serverProtocol == RegistryRemapper.PROTOCOL_767
                                                ? RegistryRemapper.attributeName767(keyId)
                                                : RegistryRemapper.attributeName766(keyId);""")

print("772の境界更新と parseUpdateAttributes の修正を入れた")
