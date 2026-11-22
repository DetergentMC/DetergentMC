#!/usr/bin/env python3
"""ProxySession に 498 対応を追加する。

1. 578 用メソッド群を抽出・変換して 498 用メソッド群を生成し、
   readChunkCoords578 の直後に挿入する（形式差の調整込み）。
2. フィールド・検証・分岐条件などの配線を exact-match で書き換える
   （件数が合わなければ失敗する）。
"""
import json

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"
text = open(PATH).read()
lines = text.split("\n")


def extract(signature):
    for i, line in enumerate(lines):
        if signature in line and ("static" in line or "private" in line
                                  or "public" in line):
            start = i
            j = i - 1
            if j >= 0 and lines[j].strip() == "*/":
                k = j
                while k >= 0 and not lines[k].strip().startswith("/**"):
                    k -= 1
                if k >= 0:
                    start = k
            depth = 0
            end = None
            for m in range(i, len(lines)):
                depth += lines[m].count("{") - lines[m].count("}")
                if m > i and depth <= 0:
                    end = m
                    break
            if end is None:
                raise SystemExit("終端なし: " + signature)
            return "\n".join(lines[start:end + 1]), end
    raise SystemExit("見つかりません: " + signature)


# 498⇔758 のパケットID差分数（packet-ids-498.json から実数える）。
ids = json.load(open("/tmp/mcdata/packet-ids-498.json"))
diff_cb = sum(1 for v in ids.values()
              if v["state"] == "PLAY" and v["direction"] == "CLIENTBOUND"
              and v["id498"] >= 0 and v["id758"] >= 0 and v["id498"] != v["id758"])
diff_sb = sum(1 for v in ids.values()
              if v["state"] == "PLAY" and v["direction"] == "SERVERBOUND"
              and v["id498"] >= 0 and v["id758"] >= 0 and v["id498"] != v["id758"])
print("498⇔758 パケットID差分: clientbound", diff_cb, "serverbound", diff_sb)


def transform(block):
    block = block.replace("1.15.2", "1.14.4").replace("578", "498")
    return block


parts = []
insert_after = None

# convertServerPlayOutcomeTo578
block, _ = extract("convertServerPlayOutcomeTo578(Outcome outcome)")
parts.append(transform(block))

# translateServerPlayPacket758To578（フォールバックIDと Game State Change を調整）
block, _ = extract("translateServerPlayPacket758To578(MinecraftPacket packet)")
block = transform(block)
block = block.replace("orElse(0x50)", "orElse(0x4F)")
block = block.replace("orElse(0x33)", "orElse(0x32)")
block = block.replace("orElse(0x3e)", "orElse(0x3D)")
block = block.replace("orElse(0x25)", "orElse(0x24)")
anchor = """            case PLAY_WORLD_PARTICLES -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateWorldParticles758To498(packet.payload())));"""
assert anchor in block
block = block.replace(anchor, anchor + """
            case PLAY_GAME_STATE_CHANGE -> {
                // 498 には reason 11（immediate_respawn、573 で追加）が
                // 無いため、その理由だけ落とす。それ以外はそのまま通す。
                if (packet.payload().length > 0 && packet.payload()[0] == 11) {
                    yield List.of();
                }
                yield List.of(new MinecraftPacket(targetId, packet.payload()));
            }""")
parts.append(block)

# dimension 読み取り・heightOffset・ack
for sig in ("readDimension578FromJoinGame(byte[] payload578)",
            "readDimension578FromRespawn(byte[] payload578)",
            "heightOffset578(int dimensionId578)",
            "translateAckDigging758To578(byte[] payload)",
            "translateAckDigging578To758(byte[] payload)"):
    block, _ = extract(sig)
    parts.append(transform(block))

# to758ClientPacket578 + Client Settings 調整
for sig in ("to758ClientPacket578(MinecraftPacket packet)",
            "appendServerListings578(byte[] payload)",
            "stripServerListings578(byte[] payload)",
            "to578ClientPacket758(MinecraftPacket packet)"):
    block, _ = extract(sig)
    parts.append(transform(block))

# routeServerPlay578 + Update Light 貯蔵
for sig in ("routeServerPlay578(MinecraftPacket packet)",
            "bufferUpdateLight578(byte[] payload)",
            "takeUpdateLight578(int x, int z)"):
    block, _ = extract(sig)
    parts.append(transform(block))

# to758ServerPackets578（Respawn の dimension 読み取り元と Spawn Living を調整）
block, _ = extract("to758ServerPackets578(MinecraftPacket packet)")
block = transform(block)
old_respawn = "dimension498Server = readDimension498FromRespawn(translated);"
assert old_respawn in block
block = block.replace(old_respawn,
                      "dimension498Server = readDimension498FromRespawn(packet.payload());")
old_living = """            case PLAY_SPAWN_ENTITY_LIVING -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateSpawnLiving498To758(packet.payload())));"""
assert old_living in block
block = block.replace(old_living, """            case PLAY_SPAWN_ENTITY_LIVING -> {
                byte[][] parts = ChunkTranslator.spawnLiving498To758(packet.payload());
                List<MinecraftPacket> out = new ArrayList<>(2);
                out.add(new MinecraftPacket(targetId, parts[0]));
                if (parts[1] != null) {
                    int metaId = PacketIds.id(PacketName.PLAY_ENTITY_METADATA,
                            State.PLAY, Direction.CLIENTBOUND, 758).orElseThrow();
                    out.add(new MinecraftPacket(metaId, parts[1]));
                }
                yield out;
            }""")
parts.append(block)

# readChunkCoords578（挿入位置の目印も兼ねる）
block, insert_after = extract("readChunkCoords578(byte[] payload)")
parts.append(transform(block))

header = """
    // ------------------------------------------------------------------
    // 498（1.14.4）対応。578 との形式差（Join Game／Respawn の
    // hashedSeed 無し、チャンクのバイオーム配置、Spawn 系の末尾
    // メタデータ、World Particles の f32 座標、Game State Change の
    // reason 11 無し）は 498 専用の変換で吸収する。
    // ------------------------------------------------------------------
"""
generated = header + "\n" + "\n\n".join(parts) + "\n"
lines.insert(insert_after + 1, generated)
text = "\n".join(lines)


def replace(old, new, count):
    global text
    found = text.count(old)
    if found != count:
        raise SystemExit(f"置換対象の件数が合いません（{found} != {count}）: {old[:80]}...")
    text = text.replace(old, new)


# --- フィールド ---
replace("""    private ResourcePack pack573;

    /**
     * 578サーバーから届いた Update Light をチャンク座標ごとに貯める。""",
        """    private ResourcePack pack573;

    /**
     * 498クライアントに送るリソースパック（pack_format 4）。
     * {@code null} の場合は573用パックにフォールバックする
     * （pack_format が違うため形式上の問題が出る可能性がある制限）。
     */
    private ResourcePack pack498;

    /**
     * 578サーバーから届いた Update Light をチャンク座標ごとに貯める。""", 1)

replace("""    /** 578サーバー向け変換で使う現在のディメンションID（578形式。0=オーバーワールド）。 */
    private int dimension578Server;
""",
        """    /** 578サーバー向け変換で使う現在のディメンションID（578形式。0=オーバーワールド）。 */
    private int dimension578Server;

    /**
     * 498サーバーから届いた Update Light をチャンク座標ごとに貯める。
     * 498 では光がチャンクと別パケットのため、578 と同じ仕組みを使う。
     */
    private final Map<Long, byte[]> pendingLight498 = new LinkedHashMap<>();

    /** 498クライアント向け変換で使う現在のディメンションID（498形式。0=オーバーワールド）。 */
    private int dimension498Client;

    /** 498サーバー向け変換で使う現在のディメンションID（498形式。0=オーバーワールド）。 */
    private int dimension498Server;
""", 1)

# --- pack アクセサ ---
replace("""    /** 578クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack578() {
        return pack578;
    }
""",
        """    /** 578クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack578() {
        return pack578;
    }

    /** 498クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack498(ResourcePack pack498) {
        this.pack498 = pack498;
    }

    /** 498クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack498() {
        return pack498;
    }
""", 1)

# --- コンストラクタ検証 ---
replace("""        if (serverProtocol != ProtocolVersion.V1_15
""",
        """        if (serverProtocol != ProtocolVersion.V1_14_4
                && serverProtocol != ProtocolVersion.V1_15
""", 1)
replace('"serverProtocol must be 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "',
        '"serverProtocol must be 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "', 1)

# --- packFor763LikeClient ---
replace("""    public ResourcePack packFor763LikeClient() {
        if (is578() || is575() || is573()) {
            if (is573() && pack573 != null) {""",
        """    public ResourcePack packFor763LikeClient() {
        if (is498() || is578() || is575() || is573()) {
            if (is498() && pack498 != null) {
                return pack498;
            }
            if (is573() && pack573 != null) {""", 1)

# --- ヘルパー ---
replace("return (is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;",
        "return (is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;", 1)
replace("""    /** クライアントが 1.18.2（758）か。設定フェーズが無い側。 */
    private boolean is758() {""",
        """    /**
     * クライアントが 1.14.4（498）か。設定フェーズが無い側。
     * 形式は 573／578 と近いが、Join Game／Respawn・チャンク・
     * Spawn 系・World Particles に差分があるため分岐は分ける。
     */
    private boolean is498() {
        return clientProtocol == ProtocolVersion.V1_14_4;
    }

    /** クライアントが 1.18.2（758）か。設定フェーズが無い側。 */
    private boolean is758() {""", 1)
replace("return is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
        "return is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();", 1)
replace("""    /** 内部が 1.18.2（758）か。設定フェーズが無い側。 */
    private boolean isServer758() {""",
        """    /** 内部が 1.14.4（498）か。設定フェーズが無い側。 */
    private boolean isServer498() {
        return serverProtocol == ProtocolVersion.V1_14_4;
    }

    /** 内部が 1.18.2（758）か。設定フェーズが無い側。 */
    private boolean isServer758() {""", 1)

# --- ログイン系：旧形式サーバーのグループ（6件すべて同じ意味） ---
replace("(isServer758() || isServer578() || isServer575() || isServer573())",
        "(isServer758() || isServer498() || isServer578() || isServer575() || isServer573())", 6)
# Login Start のクライアント側グループ（handleClientLogin 側）
replace("""                if (is758() || is578() || is575() || is573()) {
                    payload = (isServer758() || isServer498() || isServer578() || isServer575() || isServer573())""",
        """                if (is758() || is498() || is578() || is575() || is573()) {
                    payload = (isServer758() || isServer498() || isServer578() || isServer575() || isServer573())""", 1)
# Login Success（サーバー側）
replace(": isServer578() || isServer575() || isServer573()\n                            ? PlayTranslator.translateLoginSuccess578To765(packet.payload())",
        ": isServer498() || isServer578() || isServer575() || isServer573()\n                            ? PlayTranslator.translateLoginSuccess578To765(packet.payload())", 1)
# 設定フェーズ無しのグループ
replace("isServer758() || isServer578() || isServer575() || isServer573())) {",
        "isServer758() || isServer498() || isServer578() || isServer575() || isServer573())) {", 1)
# adjustLoginSuccessPayload（クライアント向け Login Success）
replace("""        if (is578() || is575() || is573()) {
            // 578 の Login Success は UUID（文字列）＋名前だけ。
            return PlayTranslator.translateLoginSuccess765To578(payload);
        }""",
        """        if (is498() || is578() || is575() || is573()) {
            // 498／578 の Login Success は UUID（文字列）＋名前だけ。
            return PlayTranslator.translateLoginSuccess765To578(payload);
        }""", 1)

# --- handleClientPlay：クライアント→サーバーの前変換 ---
replace("""        if (is758() || is578() || is575() || is573()) {
            if (is578() || is575() || is573()) {""",
        """        if (is758() || is498() || is578() || is575() || is573()) {
            if (is498()) {
                // 498クライアント：パケットIDが758と違うものがあるため、
                // 先に758形式へ直してから、758クライアントと同じ経路に乗せる。
                // 758形式に変換できないものは落とす。
                packet = to758ClientPacket498(packet);
                if (packet == null) {
                    return Outcome.drop();
                }
            } else if (is578() || is575() || is573()) {""", 1)

# --- handleClientPlay：内部498サーバーへの経路（内部578ブロックの前に挿入） ---
replace("""        // 内部578：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から578形式へ変換して送る。
        if (isServer578() || isServer575() || isServer573()) {""",
        """        // 内部498：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から498形式へ変換して送る。
        if (isServer498()) {
            Outcome to763 = is763Like()
                    ? Outcome.toServer(packet)
                    : handleClientPlayTo763(packet);
            List<MinecraftPacket> toServer = new ArrayList<>();
            for (MinecraftPacket p : to763.toServer()) {
                MinecraftPacket p762 = to762ClientPacket(p);
                if (p762 == null) {
                    continue;
                }
                MinecraftPacket p761 = to761ClientPacket762(p762);
                if (p761 == null) {
                    continue;
                }
                MinecraftPacket p760 = to760ClientPacket761(p761);
                if (p760 == null) {
                    continue;
                }
                MinecraftPacket p759 = to759ClientPacket760(p760);
                if (p759 == null) {
                    continue;
                }
                MinecraftPacket p758 = to758ClientPacket759(p759);
                if (p758 == null) {
                    continue;
                }
                MinecraftPacket p498 = to498ClientPacket758(p758);
                if (p498 == null) {
                    continue;
                }
                toServer.add(p498);
            }
            return new Outcome(toServer, to763.toClient(), to763.relay(),
                    to763.closeAfterSend());
        }
        // 内部578：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から578形式へ変換して送る。
        if (isServer578() || isServer575() || isServer573()) {""", 1)

# --- 掘削 ack の記録条件 ---
replace("!(is758() || is578() || is575() || is573())",
        "!(is758() || is498() || is578() || is575() || is573())", 1)

# --- handleServerPlay：498クライアント向けの最終変換 ---
replace("""        Outcome outcome = handleServerPlayRouted(packet);
        if (is578() || is575() || is573()) {""",
        """        Outcome outcome = handleServerPlayRouted(packet);
        if (is498()) {
            // 498クライアント：763→762→761→760→759→758→498へ六段で変換する。
            Outcome to762 = convertServerPlayOutcomeTo762(outcome);
            Outcome to761 = convertServerPlayOutcomeTo761(to762);
            Outcome to760 = convertServerPlayOutcomeTo760(to761);
            Outcome to759 = convertServerPlayOutcomeTo759(to760);
            Outcome to758 = convertServerPlayOutcomeTo758(to759);
            return convertServerPlayOutcomeTo498(to758);
        }
        if (is578() || is575() || is573()) {""", 1)

# --- handleServerPlayRouted：内部498サーバーの経路 ---
replace("""        // 内部578：先に758形式へ直してから、758サーバーと同じ経路に乗せる。
        // Update Light はチャンクに同梱するため貯めておき、チャンクが
        // 来たときに一緒に変換する。Player Info は578・758で同一形式。
        if (isServer578() || isServer575() || isServer573()) {
            return routeServerPlay578(packet);
        }""",
        """        // 内部498：先に758形式へ直してから、758サーバーと同じ経路に乗せる。
        // Update Light はチャンクに同梱するため貯めておき、チャンクが
        // 来たときに一緒に変換する。Player Info は498・758で同一形式。
        if (isServer498()) {
            return routeServerPlay498(packet);
        }
        // 内部578：先に758形式へ直してから、758サーバーと同じ経路に乗せる。
        // Update Light はチャンクに同梱するため貯めておき、チャンクが
        // 来たときに一緒に変換する。Player Info は578・758で同一形式。
        if (isServer578() || isServer575() || isServer573()) {
            return routeServerPlay578(packet);
        }""", 1)

open(PATH, "w").write(text)
print("ProxySession へ 498 対応を入れました")
