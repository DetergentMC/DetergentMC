package io.detergentmc.protocol;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイ／ログインのパケットのペイロード変換（純粋関数。Netty を使わない）。
 *
 * <p>情報源は PrismarineJS minecraft-data の protocol.json（1.20＝763、
 * 1.20.3＝765。765は1.20.4と共通）。フィールドの順番と型はすべて
 * そこから取った。IDの付け替えだけでは済まないものだけをここに置き、
 * IDだけが違うものは {@link PacketTranslator}／{@link PacketIds} の
 * 表に任せる。
 *
 * <p>このクラスが扱う変換と、その根拠：
 * <ul>
 *   <li>ハンドシェイク：763→765 はプロトコル番号の書き換えだけ。
 *       形式は両バージョンで同じ（VarInt＋文字列＋u16＋VarInt）。</li>
 *   <li>ステータス JSON：763向けに version.protocol 765→763、
 *       version.name "1.20.4"→"1.20.1" に書き換える。version
 *       オブジェクトの中だけを書き換える（説明文に同じ文字列が
 *       あっても触らない）。</li>
 *   <li>Login Start：763は {@code 名前＋bool＋任意UUID}、765は
 *       {@code 名前＋UUID（必須）}。bool を外し、UUID が無ければ
 *       オフライン UUID（v3、{@code "OfflinePlayer:"＋名前} の MD5）
 *       を合成する。バニラ（765）は UUID を16バイト必ず読むので、
 *       そのまま通すと次のパケットを食い潰してしまう。
 *       764の Login Start は765と同じ {@code 名前＋UUID（必須）} のため
 *       変換は要らない（そのまま通す）。</li>
 *   <li>Join Game：765は codec を持たず（codec は設定フェーズの
 *       Registry Data に移った）、フィールドの順番も763と違う。
 *       763形式に並べ替え、保持したレジストリから dimension codec
 *       を組み立てる。{@code doLimitedCrafting}（765のみ）は落とす。</li>
 *   <li>チャット（サーバー行き）：本物の1.20.1クライアントは
 *       763でも署名付きの完全な形式（文字列＋タイムスタンプ＋salt＋
 *       署名＋offset＋acknowledged）を送る。minecraft-data の
 *       1.20 と 1.20.3 の packet_chat_message は同じ構造である。
 *       そのため完全な形式はそのまま通す。ただし文字列だけの
 *       簡略形が来た場合（旧式クライアントやテスト用）には、
 *       765形式を合成する（タイムスタンプ＝現在時刻、salt＝0、
 *       署名なし、offset＝0、acknowledged＝0,0,0）。
 *       <b>署名は検証しない。</b>offline 想定のプロキシであり、
 *       署名の検証はサーバー（バニラ）の仕事でもないため、ここでは
 *       送られてきた署名をそのまま通すか、無いものとして扱うだけ
 *       にする。</li>
 *   <li>チャット（クライアント行き）：System Chat は765が NBT、
 *       763が JSON 文字列。Player Chat は765が networkName などを
 *       NBT で送り、763は JSON 文字列で送る。NBT→JSON 文字列に
 *       落とす（ {@link Nbt#toJsonString(Tag)}）。</li>
 *   <li>Registry Data（設定フェーズ、765）：1.20.3では1パケットに
 *       全レジストリ入りの NBT codec が入る（minecraft-data の
 *       packet_registry_data は {@code codec: anonymousNbt} 1個）。
 *       その NBT から dimension_type と worldgen/biome を抜く。
 *       旧式（1.20.2以前）の {@code レジストリID＋エントリ配列}
 *       形式が来た場合にも対応する（フォールバック）。</li>
 *   <li>プレイ状態の Registry Data は763に存在しない
 *       （minecraft-data 1.20 の play toClient に registry_data は
 *       無い）。そのため設定フェーズの Registry Data をプレイ状態の
 *       IDに付け替えて送ることはしない。レジストリは Join Game の
 *       codec に入れるのが763の正しい受け渡し方である。</li>
 * </ul>
 *
 * <p>逆向き（764サーバー→765クライアント）の変換は {@code *764To765}
 * のメソッドで行う。764と765は両方とも設定フェーズがあり、Join Game・
 * Respawn・Login Start の形式が同じ（minecraft-data の 1.20.2 と
 * 1.20.3 の protocol.json で確認済み）ため、それらは変換不要で
 * そのまま通す（{@link #translateJoinGame764To765(byte[])} 参照）。
 * 違うのはテキストコンポーネントだけで、764の JSON 文字列を765の
 * 名前なし NBT に {@link JsonToNbt} で変換する。サーバー行きの
 * Chat Message・Chat Command は両バージョンとも署名付きの同じ形式の
 * ため、そのまま通す。Scoreboard Score は765で形式が変わったため、
 * 削除（action 1）は Reset Score に組み替える
 * （{@link #translateScoreboardScore764To765(byte[])} 参照）。
 */
public final class PlayTranslator {

    private PlayTranslator() {
    }

    // ------------------------------------------------------------------
    // ハンドシェイク
    // ------------------------------------------------------------------

    /** ハンドシェイクの中身。 */
    public record Handshake(int protocol, String address, int port, int nextState) {
    }

    /** ハンドシェイクのペイロードを読む。 */
    public static Handshake parseHandshake(byte[] payload) {
        Reader reader = new Reader(payload);
        int protocol = reader.readVarInt();
        String address = reader.readString();
        int port = reader.readUnsignedShort();
        int nextState = reader.readVarInt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "ハンドシェイクに余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new Handshake(protocol, address, port, nextState);
    }

    /** ハンドシェイクのプロトコル番号だけを書き換える（763→765 など）。 */
    public static byte[] rewriteHandshakeProtocol(byte[] payload, int newProtocol) {
        Handshake handshake = parseHandshake(payload);
        Writer writer = new Writer();
        writer.writeVarInt(newProtocol);
        writer.writeString(handshake.address());
        writer.writeUnsignedShort(handshake.port());
        writer.writeVarInt(handshake.nextState());
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // ステータス JSON
    // ------------------------------------------------------------------

    /**
     * バニラのステータス応答 JSON を763クライアント向けに書き換える。
     * {@code version} オブジェクトの中の protocol と name だけを
     * 書き換える（765→763、"1.20.4"→"1.20.1"）。version が無ければ
     * そのまま返す。
     */
    public static String rewriteStatusJsonFor763(String json) {
        return rewriteStatusJson(json, 765, "1.20.4", 763, "1.20.1");
    }

    /**
     * バニラのステータス応答 JSON を764クライアント向けに書き換える。
     * {@code version} オブジェクトの中の protocol と name だけを
     * 書き換える（765→764、"1.20.4"→"1.20.2"）。version が無ければ
     * そのまま返す。
     */
    public static String rewriteStatusJsonFor764(String json) {
        return rewriteStatusJson(json, 765, "1.20.4", 764, "1.20.2");
    }

    /**
     * 764サーバーのステータス応答 JSON を765クライアント向けに書き換える。
     * 逆向き（サーバーが764・クライアントが765）の場合に使う。
     * {@code version} オブジェクトの中の protocol と name だけを
     * 書き換える（764→765、"1.20.2"→"1.20.4"）。version が無ければ
     * そのまま返す。
     */
    public static String rewriteStatusJsonFor765(String json) {
        return rewriteStatusJson(json, 764, "1.20.2", 765, "1.20.4");
    }

    /** {@link #rewriteStatusJsonFor763(String)} と {@link #rewriteStatusJsonFor764(String)} の共通処理。 */
    private static String rewriteStatusJson(String json, int fromProtocol, String fromName,
            int toProtocol, String toName) {
        if (json == null) {
            return null;
        }
        int versionAt = json.indexOf("\"version\"");
        if (versionAt < 0) {
            return json;
        }
        int open = json.indexOf('{', versionAt);
        if (open < 0) {
            return json;
        }
        int close = findMatchingBrace(json, open);
        if (close < 0) {
            return json;
        }
        String before = json.substring(0, open + 1);
        String versionBody = json.substring(open + 1, close);
        String after = json.substring(close);
        // protocol: 765 → 763／764（空白の有無を問わない）
        versionBody = versionBody.replaceAll(
                "\"protocol\"\\s*:\\s*" + fromProtocol, "\"protocol\":" + toProtocol);
        // name: "1.20.4" → "1.20.1"／"1.20.2"
        versionBody = versionBody.replaceAll(
                "\"name\"\\s*:\\s*\"" + fromName.replace(".", "\\.") + "\"",
                "\"name\":\"" + toName + "\"");
        return before + versionBody + after;
    }

    private static int findMatchingBrace(String text, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
                default -> {
                }
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // ログイン
    // ------------------------------------------------------------------

    /**
     * オフライン UUID（バニラと同じ決め方）：{@code "OfflinePlayer:"＋名前}
     * の MD5 を v3 UUID にする。バニラの offline-mode でも同じ UUID が
     * 付くので、UUID を送らないクライアントの代わりに使える。
     */
    public static UUID offlineUuid(String username) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hash = md5.digest(
                    ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte) ((hash[6] & 0x0F) | 0x30); // version 3
            hash[8] = (byte) ((hash[8] & 0x3F) | 0x80); // variant
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xFFL);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xFFL);
            }
            return new UUID(msb, lsb);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5が使えません", e);
        }
    }

    /**
     * 763の Login Start（名前＋bool＋任意UUID）を765形式
     * （名前＋UUID）に変換する。UUID が無い場合はオフライン UUID を合成する。
     */
    public static byte[] translateLoginStart763To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String username = reader.readString();
        UUID uuid;
        if (reader.remaining() == 0) {
            // bool すら無い、壊れた／古い形式。名前だけならオフライン UUID。
            uuid = offlineUuid(username);
        } else {
            boolean hasUuid = reader.readBoolean();
            if (hasUuid) {
                uuid = reader.readUuid();
            } else {
                uuid = offlineUuid(username);
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(username);
        writer.writeUuid(uuid);
        return writer.bytes();
    }

    /**
     * 764／765／766の Login Start（名前＋UUID必須）を763形式
     * （名前＋bool＋UUID）に変換する。764以降のクライアントが内部763の
     * サーバーに入るときに使う。763のサーバーは bool 無しのペイロードを
     * 誤読する（UUID の先頭バイトを bool と読む）ため、変換なしでは
     * 参加できない。
     */
    public static byte[] translateLoginStart765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        String username = reader.readString();
        UUID uuid = reader.readUuid();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(username);
        writer.writeBoolean(true);
        writer.writeUuid(uuid);
        return writer.bytes();
    }

    /** ログイン切断（Login Disconnect 0x00）のペイロードを作る。 */
    public static byte[] loginDisconnectPayload(String message) {
        Writer writer = new Writer();
        writer.writeString(Nbt.jsonString(message));
        return writer.bytes();
    }

    // 【担当1：763 online-mode認証】認証済み Login Start / Login Success。
    /**
     * 認証済みの Login Start（765形式：名前＋UUID）を作る。プロキシ認証で
     * 確定した名前・UUID を内部バニラへ渡すときに使う。クライアントが
     * 送ってきた UUID（763の任意UUID・偽装の可能性）は使わない。
     */
    public static byte[] buildLoginStart765(String username, UUID uuid) {
        Writer writer = new Writer();
        writer.writeString(username);
        writer.writeUuid(uuid);
        return writer.bytes();
    }

    /**
     * Login Success（UUID＋名前＋プロパティ）のペイロードに、認証済み
     * プロパティ（主に textures）を入れる。内部バニラは offline-mode の
     * ためプロパティ無しで送ってくるので、プロキシが hasJoined で得た
     * ものをここで付け足す。プロパティが無ければそのまま返す。
     */
    public static byte[] rewriteLoginSuccessWithProperties(byte[] payload,
            AuthenticatedProfile profile) {
        if (profile == null || !profile.hasProperties()) {
            return payload.clone();
        }
        Reader reader = new Reader(payload);
        UUID uuid = reader.readUuid();
        String username = reader.readString();
        int count = reader.readVarInt();
        for (int i = 0; i < count; i++) {
            reader.readString();
            reader.readString();
            if (reader.readBoolean()) {
                reader.readString();
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Login Success に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeUuid(uuid);
        writer.writeString(username);
        writer.writeVarInt(profile.properties().size());
        for (AuthenticatedProfile.Property property : profile.properties()) {
            writer.writeString(property.name());
            writer.writeString(property.value());
            writer.writeBoolean(property.signature() != null);
            if (property.signature() != null) {
                writer.writeString(property.signature());
            }
        }
        return writer.bytes();
    }

    /** 763＋online-mode のときの切断メッセージ。 */
    public static String onlineModeRejectedMessage() {
        return "1.20.1クライアントは online-mode では参加できません。"
                + "online-mode を false にしてください。";
    }

    // ------------------------------------------------------------------
    // Registry Data（設定フェーズ）
    // ------------------------------------------------------------------

    /**
     * 765の設定フェーズの Registry Data（0x05）のペイロードから、
     * レジストリを取り出す。戻り値はレジストリ名→レジストリ codec
     * （{@code {"type": 名前, "value": [...]}} のコンパウンド）。
     *
     * <p>新形式（1.20.3）：ペイロード全体が1個の名前なし NBT で、
     * ルートのコンパウンドのキーがレジストリ名。
     * 旧形式（1.20.2以前）：レジストリID（文字列）＋エントリ数＋
     * エントリ（名前＋bool＋任意NBT）の並び。その場合は codec 形式に
     * 組み立て直す。
     */
    public static Map<String, Tag.CompoundTag> parseRegistryData(byte[] payload) {
        // 新形式をまず試す：ペイロード全体が1個の NBT として読めるか。
        try {
            Nbt.ReadResult read = Nbt.readAnonymous(payload, 0);
            if (read.bytesRead() == payload.length
                    && read.tag() instanceof Tag.CompoundTag root) {
                Map<String, Tag.CompoundTag> registries = new LinkedHashMap<>();
                for (Map.Entry<String, Tag> entry : root.value().entrySet()) {
                    if (entry.getValue() instanceof Tag.CompoundTag codec) {
                        registries.put(entry.getKey(), codec);
                    }
                }
                if (!registries.isEmpty()) {
                    return registries;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // 旧形式として読み直す。
        }
        // 旧形式：レジストリID＋エントリ配列。
        Reader reader = new Reader(payload);
        String registryId = reader.readString();
        int count = reader.readVarInt();
        List<Tag> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = reader.readString();
            boolean hasData = reader.readBoolean();
            Tag element;
            if (hasData) {
                Nbt.ReadResult data = reader.readAnonymousNbt();
                element = data.tag();
            } else {
                element = new Tag.CompoundTag(Map.of());
            }
            Map<String, Tag> entry = new LinkedHashMap<>();
            entry.put("name", new Tag.StringTag(name));
            entry.put("id", new Tag.IntTag(i));
            entry.put("element", element);
            entries.add(new Tag.CompoundTag(entry));
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Registry Data に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Map<String, Tag> codec = new LinkedHashMap<>();
        codec.put("type", new Tag.StringTag(registryId));
        codec.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
        return Map.of(registryId, new Tag.CompoundTag(codec));
    }

    /**
     * 保持したレジストリから763向け Join Game の dimension codec を
     * 組み立てる。763の codec は dimension_type と worldgen/biome の
     * 2つのレジストリだけを含む。どちらも無ければ空のコンパウンドを返す
     * （クライアントは参加できないが、壊れたパケットは送らない）。
     */
    public static Tag.CompoundTag buildJoinGameCodec(
            Map<String, Tag.CompoundTag> registries) {
        Map<String, Tag> codec = new LinkedHashMap<>();
        for (String name : List.of("minecraft:dimension_type", "minecraft:worldgen/biome")) {
            Tag.CompoundTag registry = registries.get(name);
            if (registry != null) {
                codec.put(name, registry);
            }
        }
        return new Tag.CompoundTag(codec);
    }

    // ------------------------------------------------------------------
    // Join Game
    // ------------------------------------------------------------------

    /**
     * 765の Join Game（0x29）を763形式（0x28）に変換する。
     *
     * <p>765の順番：entityId, hardcore, worldNames, maxPlayers,
     * viewDistance, simulationDistance, reducedDebug, enableRespawnScreen,
     * doLimitedCrafting（落とす）, worldType, worldName, hashedSeed,
     * gameMode, previousGameMode, isDebug, isFlat, death, portalCooldown。
     * 763の順番：entityId, hardcore, gameMode, previousGameMode,
     * worldNames, dimensionCodec, worldType, worldName, hashedSeed,
     * maxPlayers, viewDistance, simulationDistance, reducedDebug,
     * enableRespawnScreen, isDebug, isFlat, death, portalCooldown。
     */
    public static byte[] translateJoinGame765To763(byte[] payload765,
            Map<String, Tag.CompoundTag> registries) {
        Reader reader = new Reader(payload765);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        List<String> worldNames = PacketIO.readStringList(reader);
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        int simulationDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        reader.readBoolean(); // doLimitedCrafting（763には無い）
        String worldType = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        int portalCooldown = reader.readVarInt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Join Game に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeInt(entityId);
        writer.writeBoolean(hardcore);
        writer.writeByte(gameMode);
        writer.writeByte(previousGameMode);
        PacketIO.writeStringList(writer, worldNames);
        // 763の codec は名前付き NBT（ルート名は空文字）。
        writer.writeNamedNbt("", buildJoinGameCodec(registries));
        writer.writeString(worldType);
        writer.writeString(worldName);
        writer.writeLong(hashedSeed);
        writer.writeVarInt(maxPlayers);
        writer.writeVarInt(viewDistance);
        writer.writeVarInt(simulationDistance);
        writer.writeBoolean(reducedDebug);
        writer.writeBoolean(enableRespawnScreen);
        writer.writeBoolean(isDebug);
        writer.writeBoolean(isFlat);
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(deathDimension);
            writer.writePositionRaw(deathLocation);
        }
        writer.writeVarInt(portalCooldown);
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // チャット（サーバー行き）
    // ------------------------------------------------------------------

    /**
     * 763の Chat Message（0x05）を765形式に変換する。
     *
     * <p>完全な形式（文字列＋タイムスタンプ＋salt＋署名＋offset＋
     * acknowledged）が来た場合はそのまま返す（本物の1.20.1クライアントは
     * この形式で送る。minecraft-data の 1.20 と 1.20.3 の
     * packet_chat_message は同じ構造）。文字列だけの簡略形が来た場合
     * は、765形式を合成する：タイムスタンプ＝現在時刻（ミリ秒）、
     * salt＝0、署名なし、offset＝0、acknowledged＝0,0,0。
     * 署名は検証しない（offline 想定。署名が付いていればそのまま通す）。
     */
    public static byte[] translateChatMessage763To765(byte[] payload) {
        return translateChatMessage763To765(payload, System.currentTimeMillis());
    }

    /** テスト用に時刻を注入できる版。 */
    static byte[] translateChatMessage763To765(byte[] payload, long timestampMillis) {
        Reader reader = new Reader(payload);
        String message = reader.readString();
        if (reader.remaining() == 0) {
            // 文字列だけの簡略形：765形式を合成する。
            Writer writer = new Writer();
            writer.writeString(message);
            writer.writeLong(timestampMillis);
            writer.writeLong(0L); // salt
            writer.writeBoolean(false); // 署名なし
            writer.writeVarInt(0); // offset
            writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
            return writer.bytes();
        }
        // 完全な形式：そのまま通す（署名も含めて中身は触らない）。
        return payload.clone();
    }

    /** Chat Command は両バージョンとも同じ形式なのでそのまま通す。 */
    public static byte[] translateChatCommand763To765(byte[] payload) {
        return payload.clone();
    }

    /**
     * 766の Chat Command（符号なし。コマンド文字列だけ）を764／765／763の
     * Chat Command（署名付きの完全な形式）に変換する。署名は無いため
     * 空の署名一覧で送る（サーバーは署名なしコマンドとして扱う）。
     * 時刻は現在時刻、salt は 0、acknowledged は空で合成する。
     * （Chat Message の簡略形と同じ方針。）
     */
    public static byte[] translateChatCommand766To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String command = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Chat Command に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(command);
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeVarInt(0); // argumentSignatures（空）
        writer.writeVarInt(0); // messageCount
        writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
        return writer.bytes();
    }


    /**
     * 767の Chat Command（符号なし、コマンド文字列のみ）を765形式
     * （符号付き）に変換する。766と同じ形のため
     * {@link #translateChatCommand766To765} と同じ。
     */
    public static byte[] translateChatCommand767To765(byte[] payload) {
        return translateChatCommand766To765(payload);
    }

    // ------------------------------------------------------------------
    // チャット（クライアント行き）
    // ------------------------------------------------------------------

    /**
     * 765の System Chat（0x69：NBT＋bool）を763形式（0x64：
     * JSON 文字列＋bool）に変換する。
     */
    public static byte[] translateSystemChat765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Nbt.ReadResult content = reader.readAnonymousNbt();
        boolean actionBar = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "System Chat に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(Nbt.toJsonString(content.tag()));
        writer.writeBoolean(actionBar);
        return writer.bytes();
    }

    /**
     * 765の Player Chat（0x37）を763形式（0x35）に変換する。
     * 差分は3か所：unsignedChatContent（NBT→JSON文字列）、
     * networkName（NBT→JSON文字列）、networkTargetName
     * （NBT→JSON文字列）。それ以外（送信者UUID、署名、平文、タイム
     * スタンプ、salt、previousMessages、filter、chat type）は同じ。
     * 署名は検証しない（そのまま通す）。
     */
    public static byte[] translatePlayerChat765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        UUID sender = reader.readUuid();
        int index = reader.readVarInt();
        boolean hasSignature = reader.readBoolean();
        byte[] signature = hasSignature ? reader.readBytes(256) : null;
        String plainMessage = reader.readString();
        long timestamp = reader.readLong();
        long salt = reader.readLong();
        // previousMessages：個数＋（id＋id==0なら署名256バイト）
        int previousCount = reader.readVarInt();
        List<PreviousMessage> previous = new ArrayList<>(previousCount);
        for (int i = 0; i < previousCount; i++) {
            int id = reader.readVarInt();
            byte[] sig = id == 0 ? reader.readBytes(256) : null;
            previous.add(new PreviousMessage(id, sig));
        }
        boolean hasUnsigned = reader.readBoolean();
        String unsignedJson = null;
        if (hasUnsigned) {
            Nbt.ReadResult unsigned = reader.readAnonymousNbt();
            unsignedJson = Nbt.toJsonString(unsigned.tag());
        }
        int filterType = reader.readVarInt();
        List<Long> filterMask = null;
        if (filterType == 2) {
            int maskCount = reader.readVarInt();
            filterMask = new ArrayList<>(maskCount);
            for (int i = 0; i < maskCount; i++) {
                filterMask.add(reader.readLong());
            }
        }
        int chatType = reader.readVarInt();
        Nbt.ReadResult networkName = reader.readAnonymousNbt();
        boolean hasTarget = reader.readBoolean();
        String targetJson = null;
        if (hasTarget) {
            Nbt.ReadResult target = reader.readAnonymousNbt();
            targetJson = Nbt.toJsonString(target.tag());
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Player Chat に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeUuid(sender);
        writer.writeVarInt(index);
        writer.writeBoolean(hasSignature);
        if (hasSignature) {
            writer.writeBytes(signature);
        }
        writer.writeString(plainMessage);
        writer.writeLong(timestamp);
        writer.writeLong(salt);
        writer.writeVarInt(previous.size());
        for (PreviousMessage entry : previous) {
            writer.writeVarInt(entry.id());
            if (entry.id() == 0) {
                writer.writeBytes(entry.signature());
            }
        }
        writer.writeBoolean(hasUnsigned);
        if (hasUnsigned) {
            writer.writeString(unsignedJson);
        }
        writer.writeVarInt(filterType);
        if (filterType == 2) {
            writer.writeVarInt(filterMask.size());
            for (long value : filterMask) {
                writer.writeLong(value);
            }
        }
        writer.writeVarInt(chatType);
        writer.writeString(Nbt.toJsonString(networkName.tag()));
        writer.writeBoolean(hasTarget);
        if (hasTarget) {
            writer.writeString(targetJson);
        }
        return writer.bytes();
    }

    private record PreviousMessage(int id, byte[] signature) {
    }

    /**
     * 765の Profileless Chat（0x1C）を763形式（0x1B）に変換する。
     * message／name／target の NBT を JSON 文字列に落とすだけ。
     * type（VarInt）は同じ。
     */
    public static byte[] translateProfilelessChat765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Nbt.ReadResult message = reader.readAnonymousNbt();
        int type = reader.readVarInt();
        Nbt.ReadResult name = reader.readAnonymousNbt();
        boolean hasTarget = reader.readBoolean();
        String targetJson = null;
        if (hasTarget) {
            Nbt.ReadResult target = reader.readAnonymousNbt();
            targetJson = Nbt.toJsonString(target.tag());
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Profileless Chat に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(Nbt.toJsonString(message.tag()));
        writer.writeVarInt(type);
        writer.writeString(Nbt.toJsonString(name.tag()));
        writer.writeBoolean(hasTarget);
        if (hasTarget) {
            writer.writeString(targetJson);
        }
        return writer.bytes();
    }

    /**
     * 765の Death Combat Event（0x3A）を763形式（0x38）に変換する。
     * playerId（VarInt）は同じ、message の NBT を JSON 文字列に落とす。
     */
    public static byte[] translateDeathCombatEvent765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        int playerId = reader.readVarInt();
        Nbt.ReadResult message = reader.readAnonymousNbt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Death Combat Event に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(playerId);
        writer.writeString(Nbt.toJsonString(message.tag()));
        return writer.bytes();
    }

    /**
     * 765の Scoreboard Objective（0x5C）を763形式（0x58）に変換する。
     * displayText の NBT を JSON 文字列に落とす。numberFormat と
     * styling（765のみ。action 0／2のとき後置）は763に無いので落とす。
     * action 1（削除）のときは名前と action だけ。
     */
    public static byte[] translateScoreboardObjective765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        String name = reader.readString();
        int action = reader.readByte();
        String displayJson = null;
        int type = 0;
        if (action == 0 || action == 2) {
            Nbt.ReadResult display = reader.readAnonymousNbt();
            displayJson = Nbt.toJsonString(display.tag());
            type = reader.readVarInt();
            // numberFormat：option<VarInt>。ある場合は styling（NBT）も後置する。
            boolean hasNumberFormat = reader.readBoolean();
            int numberFormat = -1;
            if (hasNumberFormat) {
                numberFormat = reader.readVarInt();
            }
            if (numberFormat == 1 || numberFormat == 2) {
                reader.readAnonymousNbt(); // styling。763には無いので読んで捨てる。
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Scoreboard Objective に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeByte(action);
        if (action == 0 || action == 2) {
            writer.writeString(displayJson);
            writer.writeVarInt(type);
        }
        return writer.bytes();
    }

    /**
     * 765の Scoreboard Score（0x5F）を763形式（0x5B）に変換する。
     * 765には action が無く、常に「設定」である。763には displayName が
     * 無いので、displayName／numberFormat／styling（765のみ）は落とす。
     */
    public static byte[] translateScoreboardScore765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        String itemName = reader.readString();
        String scoreName = reader.readString();
        int value = reader.readVarInt();
        boolean hasDisplayName = reader.readBoolean();
        if (hasDisplayName) {
            reader.readAnonymousNbt(); // displayName。763には無いので読んで捨てる。
        }
        boolean hasNumberFormat = reader.readBoolean();
        int numberFormat = -1;
        if (hasNumberFormat) {
            numberFormat = reader.readVarInt();
        }
        if (numberFormat == 1 || numberFormat == 2) {
            reader.readAnonymousNbt(); // styling。763には無いので読んで捨てる。
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Scoreboard Score に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(itemName);
        writer.writeVarInt(0); // action 0＝設定
        writer.writeString(scoreName);
        writer.writeVarInt(value);
        return writer.bytes();
    }

    /**
     * 765の Reset Score（0x42）を763の Scoreboard Score（削除）に変換する。
     * 765では削除が別パケットになっている。objective が指定されていない
     * （全 objective のリセット）は763では表せないので空を返す（呼び出し
     * 側が落とす）。
     */
    public static java.util.Optional<byte[]> translateResetScore765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        String entityName = reader.readString();
        boolean hasObjective = reader.readBoolean();
        String objectiveName = hasObjective ? reader.readString() : null;
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Reset Score に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        if (objectiveName == null) {
            return java.util.Optional.empty();
        }
        Writer writer = new Writer();
        writer.writeString(entityName);
        writer.writeVarInt(1); // action 1＝削除
        writer.writeString(objectiveName);
        return java.util.Optional.of(writer.bytes());
    }

    // ------------------------------------------------------------------
    // その他のクライアント行き（NBT→JSON 文字列）
    // ------------------------------------------------------------------

    /** 765の Disconnect（play：NBT）を763形式（JSON 文字列）に変換する。 */
    public static byte[] translateDisconnect765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Nbt.ReadResult reason = reader.readAnonymousNbt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Disconnect に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(Nbt.toJsonString(reason.tag()));
        return writer.bytes();
    }

    /** 765の Server Data（motd が NBT）を763形式（motd が JSON 文字列）に変換する。 */
    public static byte[] translateServerData765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Nbt.ReadResult motd = reader.readAnonymousNbt();
        boolean hasIcon = reader.readBoolean();
        byte[] icon = hasIcon ? reader.readByteArray() : null;
        boolean enforcesSecureChat = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Server Data に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(Nbt.toJsonString(motd.tag()));
        writer.writeBoolean(hasIcon);
        if (hasIcon) {
            writer.writeByteArray(icon);
        }
        writer.writeBoolean(enforcesSecureChat);
        return writer.bytes();
    }

    /** 765の Action Bar（NBT）を763形式（JSON 文字列）に変換する。 */
    public static byte[] translateActionBar765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Nbt.ReadResult content = reader.readAnonymousNbt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Action Bar に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(Nbt.toJsonString(content.tag()));
        return writer.bytes();
    }

    /** 765の Playerlist Header（NBT×2）を763形式（JSON 文字列×2）に変換する。 */
    public static byte[] translatePlayerlistHeader765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Nbt.ReadResult header = reader.readAnonymousNbt();
        Nbt.ReadResult footer = reader.readAnonymousNbt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Playerlist Header に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(Nbt.toJsonString(header.tag()));
        writer.writeString(Nbt.toJsonString(footer.tag()));
        return writer.bytes();
    }

    /** 765の Set Title Text / Subtitle（NBT）を763形式（JSON 文字列）に変換する。 */
    public static byte[] translateTitleText765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Nbt.ReadResult content = reader.readAnonymousNbt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Set Title Text に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(Nbt.toJsonString(content.tag()));
        return writer.bytes();
    }

    /**
     * 765の Player Info Update（0x3C）を763形式（0x3A）に変換する。
     * 差分は displayName だけ（765は任意NBT、763は任意JSON文字列）。
     * どのエントリにも displayName が無ければペイロードはそのまま返す
     * （再エンコードによる差分を出さないため）。displayName がある
     * 場合は、その NBT 部分だけを JSON 文字列に置き換える（それ以外の
     * バイトは一切触らない）。
     */
    public static byte[] translatePlayerInfo765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        int action = reader.readUnsignedByte();
        int count = reader.readVarInt();
        // displayName の置き換え範囲（開始・終了オフセットと置き換え後のバイト列）。
        List<int[]> spans = new ArrayList<>();
        List<byte[]> replacements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reader.readUuid();
            if ((action & 0x01) != 0) {
                reader.readString(); // 名前
                int properties = reader.readVarInt();
                for (int p = 0; p < properties; p++) {
                    reader.readString();
                    reader.readString();
                    if (reader.readBoolean()) {
                        reader.readString();
                    }
                }
            }
            if ((action & 0x02) != 0) {
                // chat_session は option 型：先頭に有無の bool がある。
                // （読み忘れると後続が1バイトずれて壊れる。）
                if (reader.readBoolean()) {
                    reader.readUuid(); // chat session UUID
                    reader.readLong(); // 有効期限
                    reader.readByteArray(); // 公開鍵
                    reader.readByteArray(); // 署名
                }
            }
            if ((action & 0x04) != 0) {
                reader.readVarInt(); // ゲームモード
            }
            if ((action & 0x08) != 0) {
                reader.readVarInt(); // listed
            }
            if ((action & 0x10) != 0) {
                reader.readVarInt(); // 遅延
            }
            if ((action & 0x20) != 0) {
                if (reader.readBoolean()) {
                    int nbtStart = reader.position();
                    Nbt.ReadResult displayName = reader.readAnonymousNbt();
                    int nbtEnd = reader.position();
                    Writer replacement = new Writer();
                    replacement.writeString(Nbt.toJsonString(displayName.tag()));
                    spans.add(new int[]{nbtStart, nbtEnd});
                    replacements.add(replacement.bytes());
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Player Info Update に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        if (spans.isEmpty()) {
            return payload.clone();
        }
        ByteArrayOutputStreamSplicer splicer = new ByteArrayOutputStreamSplicer(payload);
        for (int i = 0; i < spans.size(); i++) {
            splicer.replace(spans.get(i)[0], spans.get(i)[1], replacements.get(i));
        }
        return splicer.bytes();
    }

    /** バイト列の一部分を置き換えるための小さなヘルパー。 */
    private static final class ByteArrayOutputStreamSplicer {
        private final byte[] original;
        private final List<int[]> spans = new ArrayList<>();
        private final List<byte[]> replacements = new ArrayList<>();

        ByteArrayOutputStreamSplicer(byte[] original) {
            this.original = original;
        }

        void replace(int start, int end, byte[] replacement) {
            spans.add(new int[]{start, end});
            replacements.add(replacement);
        }

        byte[] bytes() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int cursor = 0;
            for (int i = 0; i < spans.size(); i++) {
                int start = spans.get(i)[0];
                int end = spans.get(i)[1];
                out.write(original, cursor, start - cursor);
                out.write(replacements.get(i), 0, replacements.get(i).length);
                cursor = end;
            }
            out.write(original, cursor, original.length - cursor);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // 逆向き（764→765）。サーバーが764、クライアントが765の場合。
    // テキストは764の JSON 文字列から765の名前なし NBT へ変換する。
    // Join Game・Respawn・Login Start は両バージョンで形式が同じため
    // 変換不要（IDの付け替えは PacketTranslator が行う）。
    // ------------------------------------------------------------------

    /**
     * 764の Join Game（0x29）を765形式（0x29）に変換する。
     * 764と765で形式は同じ（codec は両方とも設定フェーズに移っている。
     * minecraft-data の 1.20.2 と 1.20.3 の packet_login は同一）のため、
     * そのまま返す。IDの付け替えは {@link PacketTranslator} が行う。
     */
    public static byte[] translateJoinGame764To765(byte[] payload) {
        return payload.clone();
    }

    /**
     * 765の Chat Message を764形式に変換する（サーバー行き）。
     * 両バージョンとも署名付きの完全な形式（文字列＋タイムスタンプ＋
     * salt＋署名＋offset＋acknowledged）で同じため、そのまま返す。
     * minecraft-data の 1.20.2 と 1.20.3 の packet_chat_message は同一。
     */
    public static byte[] translateChatMessage765To764(byte[] payload) {
        return payload.clone();
    }

    /** Chat Command は両バージョンとも同じ形式なのでそのまま通す。 */
    public static byte[] translateChatCommand765To764(byte[] payload) {
        return payload.clone();
    }

    /**
     * 764の System Chat（JSON 文字列＋bool）を765形式（NBT＋bool）に変換する。
     */
    public static byte[] translateSystemChat764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String contentJson = reader.readString();
        boolean actionBar = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "System Chat に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeAnonymousNbt(JsonToNbt.fromJson(contentJson));
        writer.writeBoolean(actionBar);
        return writer.bytes();
    }

    /**
     * 764の Player Chat を765形式に変換する。
     * 差分は3か所：unsignedChatContent・networkName・networkTargetName
     * （JSON 文字列→NBT）。それ以外は同じ。
     */
    public static byte[] translatePlayerChat764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        UUID sender = reader.readUuid();
        int index = reader.readVarInt();
        boolean hasSignature = reader.readBoolean();
        byte[] signature = hasSignature ? reader.readBytes(256) : null;
        String plainMessage = reader.readString();
        long timestamp = reader.readLong();
        long salt = reader.readLong();
        int previousCount = reader.readVarInt();
        List<PreviousMessage> previous = new ArrayList<>(previousCount);
        for (int i = 0; i < previousCount; i++) {
            int id = reader.readVarInt();
            byte[] sig = id == 0 ? reader.readBytes(256) : null;
            previous.add(new PreviousMessage(id, sig));
        }
        boolean hasUnsigned = reader.readBoolean();
        String unsignedJson = null;
        if (hasUnsigned) {
            unsignedJson = reader.readString();
        }
        int filterType = reader.readVarInt();
        List<Long> filterMask = null;
        if (filterType == 2) {
            int maskCount = reader.readVarInt();
            filterMask = new ArrayList<>(maskCount);
            for (int i = 0; i < maskCount; i++) {
                filterMask.add(reader.readLong());
            }
        }
        int chatType = reader.readVarInt();
        String networkNameJson = reader.readString();
        boolean hasTarget = reader.readBoolean();
        String targetJson = null;
        if (hasTarget) {
            targetJson = reader.readString();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Player Chat に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeUuid(sender);
        writer.writeVarInt(index);
        writer.writeBoolean(hasSignature);
        if (hasSignature) {
            writer.writeBytes(signature);
        }
        writer.writeString(plainMessage);
        writer.writeLong(timestamp);
        writer.writeLong(salt);
        writer.writeVarInt(previous.size());
        for (PreviousMessage entry : previous) {
            writer.writeVarInt(entry.id());
            if (entry.id() == 0) {
                writer.writeBytes(entry.signature());
            }
        }
        writer.writeBoolean(hasUnsigned);
        if (hasUnsigned) {
            writer.writeAnonymousNbt(JsonToNbt.fromJson(unsignedJson));
        }
        writer.writeVarInt(filterType);
        if (filterType == 2) {
            writer.writeVarInt(filterMask.size());
            for (long value : filterMask) {
                writer.writeLong(value);
            }
        }
        writer.writeVarInt(chatType);
        writer.writeAnonymousNbt(JsonToNbt.fromJson(networkNameJson));
        writer.writeBoolean(hasTarget);
        if (hasTarget) {
            writer.writeAnonymousNbt(JsonToNbt.fromJson(targetJson));
        }
        return writer.bytes();
    }

    /**
     * 764の Profileless Chat を765形式に変換する。
     * message／name／target の JSON 文字列を NBT にするだけ。
     */
    public static byte[] translateProfilelessChat764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String messageJson = reader.readString();
        int type = reader.readVarInt();
        String nameJson = reader.readString();
        boolean hasTarget = reader.readBoolean();
        String targetJson = null;
        if (hasTarget) {
            targetJson = reader.readString();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Profileless Chat に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeAnonymousNbt(JsonToNbt.fromJson(messageJson));
        writer.writeVarInt(type);
        writer.writeAnonymousNbt(JsonToNbt.fromJson(nameJson));
        writer.writeBoolean(hasTarget);
        if (hasTarget) {
            writer.writeAnonymousNbt(JsonToNbt.fromJson(targetJson));
        }
        return writer.bytes();
    }

    /**
     * 764の Death Combat Event を765形式に変換する。
     * playerId（VarInt）は同じ、message の JSON 文字列を NBT にする。
     */
    public static byte[] translateDeathCombatEvent764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        int playerId = reader.readVarInt();
        String messageJson = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Death Combat Event に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(playerId);
        writer.writeAnonymousNbt(JsonToNbt.fromJson(messageJson));
        return writer.bytes();
    }

    /**
     * 764の Scoreboard Objective を765形式に変換する。
     * displayText の JSON 文字列を NBT にする。numberFormat と styling
     * は765のみのフィールドのため、764側には無いので「無し」
     * （hasNumberFormat=false）を書く（無害化：765クライアントは
     * numberFormat 無しを既定の表示として扱う）。
     * action 1（削除）のときは名前と action だけ。
     */
    public static byte[] translateScoreboardObjective764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String name = reader.readString();
        int action = reader.readByte();
        String displayJson = null;
        int type = 0;
        if (action == 0 || action == 2) {
            displayJson = reader.readString();
            type = reader.readVarInt();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Scoreboard Objective に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeByte(action);
        if (action == 0 || action == 2) {
            writer.writeAnonymousNbt(JsonToNbt.fromJson(displayJson));
            writer.writeVarInt(type);
            writer.writeBoolean(false); // numberFormat 無し
        }
        return writer.bytes();
    }

    /**
     * 764の Scoreboard Score を765形式に変換する。
     * 765には action が無く、常に「設定」である。action 0（設定）の
     * ときは displayName／numberFormat を「無し」として765形式にする。
     * action 1（削除）のときは765の Scoreboard Score では表せないため
     * 空を返す。呼び出し側は代わりに
     * {@link #buildResetScore765ForRemoval(byte[])} で作った
     * Reset Score（765 0x42）を送ること。
     */
    public static java.util.Optional<byte[]> translateScoreboardScore764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String itemName = reader.readString();
        int action = reader.readVarInt();
        String scoreName = reader.readString();
        int value = 0;
        if (action != 1) {
            value = reader.readVarInt();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Scoreboard Score に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        if (action == 1) {
            return java.util.Optional.empty();
        }
        Writer writer = new Writer();
        writer.writeString(itemName);
        writer.writeString(scoreName);
        writer.writeVarInt(value);
        writer.writeBoolean(false); // displayName 無し
        writer.writeBoolean(false); // numberFormat 無し
        return java.util.Optional.of(writer.bytes());
    }

    /**
     * 764の Scoreboard Score（削除）のペイロードから、765の Reset Score
     * のペイロードを作る。764では削除が Scoreboard Score の action 1
     * だったが、765では Reset Score という別パケットになっている。
     * Reset Score の形式：entityName、objectiveName の有無（bool）、
     * objectiveName。
     */
    public static byte[] buildResetScore765ForRemoval(byte[] payload764) {
        Reader reader = new Reader(payload764);
        String itemName = reader.readString();
        int action = reader.readVarInt();
        String scoreName = reader.readString();
        if (action != 1) {
            throw new IllegalArgumentException(
                    "削除ではない Scoreboard Score です（action=" + action + "）。");
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Scoreboard Score に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(itemName);
        writer.writeBoolean(true);
        writer.writeString(scoreName);
        return writer.bytes();
    }

    /** 764の Disconnect（play：JSON 文字列）を765形式（NBT）に変換する。 */
    public static byte[] translateDisconnect764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String reasonJson = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Disconnect に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeAnonymousNbt(JsonToNbt.fromJson(reasonJson));
        return writer.bytes();
    }

    /** 764の Server Data（motd が JSON 文字列）を765形式（motd が NBT）に変換する。 */
    public static byte[] translateServerData764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String motdJson = reader.readString();
        boolean hasIcon = reader.readBoolean();
        byte[] icon = hasIcon ? reader.readByteArray() : null;
        boolean enforcesSecureChat = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Server Data に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeAnonymousNbt(JsonToNbt.fromJson(motdJson));
        writer.writeBoolean(hasIcon);
        if (hasIcon) {
            writer.writeByteArray(icon);
        }
        writer.writeBoolean(enforcesSecureChat);
        return writer.bytes();
    }

    /** 764の Action Bar（JSON 文字列）を765形式（NBT）に変換する。 */
    public static byte[] translateActionBar764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String contentJson = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Action Bar に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeAnonymousNbt(JsonToNbt.fromJson(contentJson));
        return writer.bytes();
    }

    /** 764の Playerlist Header（JSON 文字列×2）を765形式（NBT×2）に変換する。 */
    public static byte[] translatePlayerlistHeader764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String headerJson = reader.readString();
        String footerJson = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Playerlist Header に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeAnonymousNbt(JsonToNbt.fromJson(headerJson));
        writer.writeAnonymousNbt(JsonToNbt.fromJson(footerJson));
        return writer.bytes();
    }

    /**
     * 764の Set Title Text／Set Title Subtitle（JSON 文字列）を765形式
     * （NBT）に変換する。両パケットとも中身はコンポーネント1個だけで
     * 同じ形のため、このメソッドを両方に使う。
     */
    public static byte[] translateTitleText764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String contentJson = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Set Title Text に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeAnonymousNbt(JsonToNbt.fromJson(contentJson));
        return writer.bytes();
    }

    /**
     * 764の Player Info Update を765形式に変換する。
     * 差分は displayName だけ（764は任意JSON文字列、765は任意NBT）。
     * どのエントリにも displayName が無ければペイロードはそのまま返す
     * （再エンコードによる差分を出さないため）。displayName がある
     * 場合は、その JSON 文字列部分だけを NBT に置き換える（それ以外の
     * バイトは一切触らない）。
     */
    public static byte[] translatePlayerInfo764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        int action = reader.readUnsignedByte();
        int count = reader.readVarInt();
        List<int[]> spans = new ArrayList<>();
        List<byte[]> replacements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reader.readUuid();
            if ((action & 0x01) != 0) {
                reader.readString(); // 名前
                int properties = reader.readVarInt();
                for (int p = 0; p < properties; p++) {
                    reader.readString();
                    reader.readString();
                    if (reader.readBoolean()) {
                        reader.readString();
                    }
                }
            }
            if ((action & 0x02) != 0) {
                if (reader.readBoolean()) {
                    reader.readUuid(); // chat session UUID
                    reader.readLong(); // 有効期限
                    reader.readByteArray(); // 公開鍵
                    reader.readByteArray(); // 署名
                }
            }
            if ((action & 0x04) != 0) {
                reader.readVarInt(); // ゲームモード
            }
            if ((action & 0x08) != 0) {
                reader.readVarInt(); // listed
            }
            if ((action & 0x10) != 0) {
                reader.readVarInt(); // 遅延
            }
            if ((action & 0x20) != 0) {
                if (reader.readBoolean()) {
                    int stringStart = reader.position();
                    String displayJson = reader.readString();
                    int stringEnd = reader.position();
                    Writer replacement = new Writer();
                    replacement.writeAnonymousNbt(JsonToNbt.fromJson(displayJson));
                    spans.add(new int[]{stringStart, stringEnd});
                    replacements.add(replacement.bytes());
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Player Info Update に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        if (spans.isEmpty()) {
            return payload.clone();
        }
        ByteArrayOutputStreamSplicer splicer = new ByteArrayOutputStreamSplicer(payload);
        for (int i = 0; i < spans.size(); i++) {
            splicer.replace(spans.get(i)[0], spans.get(i)[1], replacements.get(i));
        }
        return splicer.bytes();
    }

    // ------------------------------------------------------------------
    // リソースパック
    // ------------------------------------------------------------------

    /** 設定フェーズの Add Resource Pack（765）の内容。 */
    public record PendingPack(UUID uuid, String url, String hash, boolean forced,
            String promptJson) {
    }

    /** 設定フェーズの Add Resource Pack（765）のペイロードを読む。 */
    public static PendingPack parseConfigAddResourcePack(byte[] payload) {
        Reader reader = new Reader(payload);
        UUID uuid = reader.readUuid();
        String url = reader.readString();
        String hash = reader.readString();
        boolean forced = reader.readBoolean();
        String promptJson = null;
        if (reader.readBoolean()) {
            Nbt.ReadResult prompt = reader.readAnonymousNbt();
            promptJson = Nbt.toJsonString(prompt.tag());
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Add Resource Pack に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new PendingPack(uuid, url, hash, forced, promptJson);
    }

    /**
     * 保持したリソースパックを763のプレイ状態の Resource Pack Send
     * （0x40：URL＋SHA-1＋forced＋任意プロンプト）に変換する。
     * 差し替え先の763用パック（{@code override}）がある場合は、その
     * URLとSHA-1を使う。763クライアントに format 22 のパックを出すと
     * 「互換性なし」と表示されるため、763には pack_format 15 のパック
     * を出すのが既定の動きである（{@code override} が null の場合だけ、
     * バニラが送ってきた765用パックをそのまま出す）。UUID・forced・
     * プロンプトはバニラのパックのものを使い続ける：クライアントの応答
     * はその UUID でバニラへ返すので、URL／SHA-1 だけを置き換える。
     */
    public static byte[] buildPlayResourcePack763(PendingPack pack) {
        return buildPlayResourcePack763(pack, null);
    }

    /** {@link #buildPlayResourcePack763(PendingPack)} の差し替え付き版。 */
    public static byte[] buildPlayResourcePack763(PendingPack pack, ResourcePack override) {
        Writer writer = new Writer();
        writer.writeString(override != null ? override.url() : pack.url());
        writer.writeString(override != null ? override.sha1() : pack.hash());
        writer.writeBoolean(pack.forced());
        writer.writeBoolean(pack.promptJson() != null);
        if (pack.promptJson() != null) {
            writer.writeString(pack.promptJson());
        }
        return writer.bytes();
    }

    /** プレイ状態の Add Resource Pack（765）を763の Resource Pack Send に変換する。 */
    public static byte[] translatePlayAddResourcePack765To763(byte[] payload) {
        PendingPack pack = parseConfigAddResourcePack(payload);
        return buildPlayResourcePack763(pack);
    }

    /**
     * 保持したリソースパックを764の設定フェーズの Resource Pack Send
     * （0x06：URL＋SHA-1＋forced＋任意プロンプト）に変換する。
     * 764の resource_pack_send は763のプレイ状態の旧式オファーと
     * 同じ形（UUIDなし、プロンプトは JSON 文字列）なので、
     * {@link #buildPlayResourcePack763(PendingPack, ResourcePack)} と
     * 同じバイト列になる。差し替え先の764用パック（{@code override}、
     * pack_format 18）がある場合は、その URL と SHA-1 を使う。
     * {@code override} が null の場合はバニラの765用パック（format 22）
     * をそのまま出す（764クライアントには「互換性なし」と表示される）。
     */
    public static byte[] buildConfigResourcePack764(PendingPack pack, ResourcePack override) {
        return buildPlayResourcePack763(pack, override);
    }

    /**
     * 764の Resource Pack Response（設定 0x05・プレイ 0x27：結果 VarInt
     * のみ）を765形式（UUID＋結果）に変換する。764の応答は763と同じく
     * UUID を含まないため、{@link #translateResourcePackResponse763To765(byte[], PendingPack)}
     * と同じ変換になる。どのパックへの応答かは {@code pack} で指定する
     * （プロキシが保持したもの）。
     */
    public static byte[] translateResourcePackResponse764To765(byte[] payload,
            PendingPack pack) {
        return translateResourcePackResponse763To765(payload, pack);
    }

    /**
     * 763の Resource Pack Response（play 0x24：結果 VarInt のみ）を
     * 765形式（UUID＋結果）に変換する。どのパックへの応答かは
     * {@code pack} で指定する（プロキシが保持したもの）。
     */
    public static byte[] translateResourcePackResponse763To765(byte[] payload,
            PendingPack pack) {
        Reader reader = new Reader(payload);
        int result = reader.readVarInt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Resource Pack Response に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeUuid(pack.uuid());
        writer.writeVarInt(result);
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // 設定フェーズのクライアント役としての合成パケット
    // ------------------------------------------------------------------

    /** Login Acknowledged（765、サーバー行き 0x03）。中身は空。 */
    public static byte[] loginAcknowledgedPayload() {
        return new byte[0];
    }

    /**
     * Client Information（設定フェーズ、765 サーバー行き 0x00）を合成する。
     * プロキシが765クライアント役として送るもの。値は控えめな既定値：
     * ロケール ja_JP、描画距離 8、チャットは全表示、チャット色あり、
     * スキンパーツ全表示、利き手は右、テキストフィルタなし、
     * サーバーリスト掲載を許可。
     */
    public static byte[] clientInformationPayload() {
        Writer writer = new Writer();
        writer.writeString("ja_JP");
        writer.writeByte(8); // 描画距離
        writer.writeVarInt(0); // チャットモード：全表示
        writer.writeBoolean(true); // チャット色
        writer.writeByte(0x7F); // スキンパーツ
        writer.writeVarInt(1); // 利き手：右
        writer.writeBoolean(false); // テキストフィルタなし
        writer.writeBoolean(true); // サーバーリスト掲載を許可
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // 763（1.20.1）⇔764（1.20.2）のペイロード変換。
    // ChunkTranslator（担当外）ではなくここに置く。違いは3点だけ：
    // NBT は763が名前付き・764が名前なし、テキストは両方とも JSON 文字列、
    // エフェクトは763が1ベース・764が0ベース。レジストリ番号は
    // ClientAdapter の直接の763↔764表（763→765→764 の二段変換はしない）
    // を使う。読めないパケットは元のペイロードをそのまま返す
    // （ChunkTranslator の *765To763 と同じ方針。壊れたものを無理に
    // 変換してクライアントを切断させるより、素通しの方が安全）。
    // ------------------------------------------------------------------

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(PlayTranslator.class.getName());

    /** 764の落下ブロックのエンティティ種別ID（wiki.vg 1.20.2 の表による）。 */
    static final int FALLING_BLOCK_764 = 36;

    /** 任意の名前なし NBT（764形式）を読む。先頭が 0x00（END）なら無し。 */
    private static Tag readOptionalAnonymousNbt(Reader reader) {
        return reader.readAnonymousNbt().tag();
    }

    /** 任意の名前付き NBT（763形式）を読む。先頭が 0x00（END）なら無し。 */
    private static Tag readOptionalNamedNbt(Reader reader) {
        return reader.readNamedNbt().tag();
    }

    /** 任意の NBT を763形式（名前付き、ルート名は空文字）で書く。無ければ 0x00 を1バイト書く。 */
    private static void writeOptionalNamedNbt(Writer writer, Tag tag) {
        if (tag == null) {
            writer.writeByte(0);
        } else {
            writer.writeNamedNbt("", tag);
        }
    }

    /** 任意の NBT を764形式（名前なし）で書く。無ければ 0x00 を1バイト書く。 */
    private static void writeOptionalAnonymousNbt(Writer writer, Tag tag) {
        if (tag == null) {
            writer.writeByte(0);
        } else {
            writer.writeAnonymousNbt(tag);
        }
    }

    /** ペイロードを読み切ったことを確認する。余りがあれば不正とする。 */
    private static void requireEnd(Reader reader) {
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "ペイロードの末尾に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
    }

    /**
     * 764のスロット1個を763向けに読む（クライアント行き）。
     * アイテムIDを764→763へ、NBT を名前なし→名前付きに変換する。
     */
    public static void translateSlot764To763(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (!present) {
            return;
        }
        writer.writeVarInt(ClientAdapter.mapItem764To763(reader.readVarInt()));
        writer.writeByte(reader.readByte());
        writeOptionalNamedNbt(writer, readOptionalAnonymousNbt(reader));
    }

    /**
     * 763のスロット1個を764向けに読む（サーバー行き）。
     * アイテムIDを763→764へ、NBT を名前付き→名前なしに変換する。
     */
    public static void translateSlot763To764(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (!present) {
            return;
        }
        writer.writeVarInt(ClientAdapter.mapItem763To764(reader.readVarInt()));
        writer.writeByte(reader.readByte());
        writeOptionalAnonymousNbt(writer, readOptionalNamedNbt(reader));
    }

    /**
     * 765のスロット1個を763向けに読む（サーバー行き・内部763用）。
     * アイテムIDを765→763へ、NBT を名前なし→名前付きに変換する。
     */
    public static void translateSlot765To763(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (!present) {
            return;
        }
        writer.writeVarInt(RegistryRemapper.mapItem(reader.readVarInt()));
        writer.writeByte(reader.readByte());
        writeOptionalNamedNbt(writer, readOptionalAnonymousNbt(reader));
    }

    /**
     * 763のスロット1個を765向けに読む（サーバー行き・内部763用）。
     * アイテムIDを763→765へ、NBT を名前付き→名前なしに変換する。
     */
    public static void translateSlot763To765(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (!present) {
            return;
        }
        writer.writeVarInt(RegistryRemapper.mapItemReverse(reader.readVarInt()));
        writer.writeByte(reader.readByte());
        writeOptionalAnonymousNbt(writer, readOptionalNamedNbt(reader));
    }


    /**
     * 763のスロット1個を762向けに読む。
     * アイテムIDを763→762へ付け替える。NBT は両方とも名前付きのためそのまま写す。
     */
    public static void translateSlot763To762(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (!present) {
            return;
        }
        writer.writeVarInt(RegistryRemapper.mapItem763To762(reader.readVarInt()));
        writer.writeByte(reader.readByte());
        writeOptionalNamedNbt(writer, readOptionalNamedNbt(reader));
    }

    /**
     * 762のスロット1個を763向けに読む。
     * アイテムIDを762→763へ付け替える。NBT は両方とも名前付きのためそのまま写す。
     */
    public static void translateSlot762To763(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (!present) {
            return;
        }
        writer.writeVarInt(RegistryRemapper.mapItem762To763(reader.readVarInt()));
        writer.writeByte(reader.readByte());
        writeOptionalNamedNbt(writer, readOptionalNamedNbt(reader));
    }

    /**
     * Block Entity Data（764 → 763）。
     * 位置・種別・NBT。種別は763↔764表で変換し、両方に無い種別
     * （crafter／trial_spawner は764にも763にも無い）の場合は
     * {@code null} を返し、呼び出し側はパケットごと落とすこと
     * （代替のブロックエンティティが無いため。ブロック自体は
     * チャンク側で近いブロックへ代替される）。
     */
    public static byte[] translateBlockEntityData764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long position = reader.readPositionRaw();
            int type = reader.readVarInt();
            Tag nbt = readOptionalAnonymousNbt(reader);
            requireEnd(reader);
            int mapped = ClientAdapter.mapBlockEntityType764To763(type);
            if (mapped == RegistryRemapper.DROP) {
                LOG.warning("763に無いブロックエンティティ種別（764 type=" + type
                        + "）の Block Entity Data を落とします。");
                return null;
            }
            Writer writer = new Writer();
            writer.writePositionRaw(position);
            writer.writeVarInt(mapped);
            writeOptionalNamedNbt(writer, nbt);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Block Entity Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Set Slot（764 → 763）。窓・状態・スロット番号は同じ。スロットだけ変換する。 */
    public static byte[] translateSetSlot764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeByte(reader.readByte());
            writer.writeVarInt(reader.readVarInt());
            writer.writeShort(reader.readShort());
            translateSlot764To763(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Window Items（764 → 763）。スロット列と手持ちスロットを変換する。 */
    public static byte[] translateWindowItems764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeUnsignedByte(reader.readUnsignedByte());
            writer.writeVarInt(reader.readVarInt());
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Window Items の個数が負です: " + count);
            }
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                translateSlot764To763(reader, writer);
            }
            translateSlot764To763(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Window Items を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Entity Equipment（764 → 763）。
     * 装備は「スロット種別バイト（上位ビット＝続きあり）＋スロット」の繰り返し。
     */
    public static byte[] translateEntityEquipment764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt());
            while (true) {
                int slotByte = reader.readUnsignedByte();
                writer.writeUnsignedByte(slotByte);
                translateSlot764To763(reader, writer);
                if ((slotByte & 0x80) == 0) {
                    break;
                }
            }
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Entity Equipment を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Trade List（764 → 763）。取引の3スロットずつ変換する（それ以外は同じ形式）。 */
    public static byte[] translateTradeList764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt());
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Trade List の件数が負です: " + count);
            }
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                translateSlot764To763(reader, writer);
                translateSlot764To763(reader, writer);
                translateSlot764To763(reader, writer);
                writer.writeBoolean(reader.readBoolean());
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
                writer.writeFloat(reader.readFloat());
                writer.writeInt(reader.readInt());
            }
            writer.writeVarInt(reader.readVarInt());
            writer.writeVarInt(reader.readVarInt());
            writer.writeBoolean(reader.readBoolean());
            writer.writeBoolean(reader.readBoolean());
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Trade List を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Entity Metadata（764 → 763）。スロット・ブロック状態・粒子・NBT を変換する。
     *
     * <p>variant 系の扱い：猫（22）・カエル（24）は763と764で登録が同一
     * （11種・3種とも変わっていない）ためそのまま通す。狼（23）・絵画（26）は
     * 1.20.2で追加されたレジストリ（wolf_variant／painting_variant）で
     * 763には型そのものが無いため、エントリごと落とす（パケット自体は残す。
     * 落とした分は既定の見た目になるだけ）。765→763で variant 系を
     * すべて落としていたのと違い、764→763では落とすのは23・26だけである。
     * 直書き（holder 0）の NBT が来るのは23・26だけで、落とすため
     * NBT の名前付け替えは要らない。
     */
    public static byte[] translateEntityMetadata764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeVarInt(reader.readVarInt());
            while (true) {
                int index = reader.readUnsignedByte();
                if (index == 0xFF) {
                    writer.writeUnsignedByte(0xFF);
                    break;
                }
                int type = reader.readVarInt();
                if (type == 22 || type == 24) {
                    // 猫・カエルの variant：登録は同一のためそのまま通す。
                    writer.writeUnsignedByte(index);
                    writer.writeVarInt(type);
                    writer.writeVarInt(reader.readVarInt());
                    continue;
                }
                if (type == 23 || type == 26) {
                    // 狼・絵画の variant：763には無い型のためエントリごと落とす。
                    int holder = reader.readVarInt();
                    if (holder == 0) {
                        reader.readAnonymousNbt();
                    }
                    continue;
                }
                writer.writeUnsignedByte(index);
                writer.writeVarInt(type);
                translateMetadataValue764To763(reader, writer, type);
            }
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Entity Metadata を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * メタデータの値1個を764→763向けに変換する。型番号は wiki.vg の
     * Entity Metadata（1.20.2）による。variant 系（22／23／24／26）は
     * 呼び出し側で扱うため、ここには来ない。テキスト（5／6）は763も764も
     * JSON 文字列のためそのまま通す。
     */
    private static void translateMetadataValue764To763(Reader reader, Writer writer, int type) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeVarInt(reader.readVarInt());
            case 2 -> writer.writeVarLong(reader.readVarLong());
            case 3 -> writer.writeFloat(reader.readFloat());
            case 4 -> writer.writeString(reader.readString());
            case 5 -> writer.writeString(reader.readString());
            case 6 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                }
            }
            case 7 -> translateSlot764To763(reader, writer);
            case 8 -> writer.writeBoolean(reader.readBoolean());
            case 9 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 10 -> writer.writePositionRaw(reader.readPositionRaw());
            case 11 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 12 -> writer.writeVarInt(reader.readVarInt());
            case 13 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeBytes(reader.readBytes(16));
                }
            }
            case 14 -> writer.writeVarInt(ClientAdapter.mapBlockState764To763(reader.readVarInt()));
            case 15 -> {
                // 0 は「無し」。それ以外はブロック状態ID。
                int state = reader.readVarInt();
                writer.writeVarInt(state == 0 ? 0 : ClientAdapter.mapBlockState764To763(state));
            }
            case 16 -> writeOptionalNamedNbt(writer, readOptionalAnonymousNbt(reader));
            case 17 -> translateParticle764To763(reader, writer);
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle764To763(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20 -> writer.writeVarInt(reader.readVarInt());
            case 21 -> writer.writeVarInt(reader.readVarInt());
            case 27 -> writer.writeVarInt(reader.readVarInt());
            case 25 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 28 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 29 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }

    /**
     * パーティクル1個（ID VarInt＋データ）を764→763向けに変換する。
     * データの形式は minecraft-data の protocol.json の particleData
     * （764側のキー）による。764のIDで読むため、765向けの読み方とは
     * 番号が違う（例：falling_dust は764=25・765=27、item は764=40・
     * 765=42、vibration は764=41・765=43、shriek は764=93・765=96）。
     */
    private static void translateParticle764To763(Reader reader, Writer writer) {
        int particleId = reader.readVarInt();
        writer.writeVarInt(ClientAdapter.mapParticle764To763(particleId));
        switch (particleId) {
            case 2, 3, 25 -> // block, block_marker, falling_dust：ブロック状態ID
                    writer.writeVarInt(ClientAdapter.mapBlockState764To763(reader.readVarInt()));
            case 14 -> { // dust：赤・緑・青・大きさ
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 15 -> { // dust_color_transition：7個の float
                for (int i = 0; i < 7; i++) {
                    writer.writeFloat(reader.readFloat());
                }
            }
            case 31 -> writer.writeFloat(reader.readFloat()); // 回転（1個の float）
            case 40 -> translateSlot764To763(reader, writer); // item
            case 41 -> { // vibration：位置種別＋行き先＋tick 数
                String positionType = reader.readString();
                writer.writeString(positionType);
                if ("minecraft:entity".equals(positionType)) {
                    writer.writeVarInt(reader.readVarInt());
                    writer.writeVarInt(reader.readVarInt());
                }
                if ("minecraft:block".equals(positionType)) {
                    writer.writePositionRaw(reader.readPositionRaw());
                } else if ("minecraft:entity".equals(positionType)) {
                    writer.writeVarInt(reader.readVarInt());
                }
                writer.writeVarInt(reader.readVarInt());
            }
            case 93 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            default -> {
                // その他の粒子にデータは無い。
            }
        }
    }

    /** Set Cooldown（764 → 763）：アイテムIDだけ変換する。 */
    public static byte[] translateSetCooldown764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int item = reader.readVarInt();
            int ticks = reader.readVarInt();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(ClientAdapter.mapItem764To763(item));
            writer.writeVarInt(ticks);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Cooldown を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Block Action（764 → 763）：ブロックIDは763と764で登録が同一のため、
     * 形式は変わらない（範囲外だけ石に寄せる）。再エンコードして返す。
     */
    public static byte[] translateBlockAction764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long position = reader.readPositionRaw();
            int byte1 = reader.readUnsignedByte();
            int byte2 = reader.readUnsignedByte();
            int blockId = reader.readVarInt();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writePositionRaw(position);
            writer.writeUnsignedByte(byte1);
            writer.writeUnsignedByte(byte2);
            writer.writeVarInt(ClientAdapter.mapBlock764To763(blockId));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Block Action を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Open Window（764 → 763）：メニュー種別IDは763と764で登録が同一で、
     * タイトルは両方とも JSON 文字列のため、形式は変わらない。
     * 再エンコードして返す（範囲外のメニューだけ作業台に寄せる）。
     */
    public static byte[] translateOpenWindow764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readVarInt();
            int menuType = reader.readVarInt();
            String titleJson = reader.readString();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(windowId);
            writer.writeVarInt(ClientAdapter.mapMenu764To763(menuType));
            writer.writeString(titleJson);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Open Window を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Spawn Entity（764 → 763）。種別IDは763と764で登録が同一。
     * falling_block の objectData はブロック状態IDなので一緒に変換する。
     * 764の falling_block は36（wiki.vg 1.20.2 の表。765は breeze が
     * 36に入ったため37にずれている）。
     */
    public static byte[] translateSpawnEntity764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            byte[] uuid = reader.readBytes(16);
            int type = reader.readVarInt();
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            int pitch = reader.readByte();
            int yaw = reader.readByte();
            int headPitch = reader.readByte();
            int objectData = reader.readVarInt();
            int velX = reader.readShort();
            int velY = reader.readShort();
            int velZ = reader.readShort();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeBytes(uuid);
            writer.writeVarInt(ClientAdapter.mapEntityType764To763(type));
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(pitch);
            writer.writeByte(yaw);
            writer.writeByte(headPitch);
            writer.writeVarInt(type == FALLING_BLOCK_764
                    ? ClientAdapter.mapBlockState764To763(objectData) : objectData);
            writer.writeShort(velX);
            writer.writeShort(velY);
            writer.writeShort(velZ);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** 音の Holder を764→763向けに書き直す。直書きの名前は763向けの名前に寄せる。 */
    private static void translateSoundHolder764To763(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            writer.writeVarInt(0);
            writer.writeString(RegistryRemapper.mapSoundName(reader.readString()));
            boolean hasRange = reader.readBoolean();
            writer.writeBoolean(hasRange);
            if (hasRange) {
                writer.writeFloat(reader.readFloat());
            }
            return;
        }
        writer.writeVarInt(ClientAdapter.mapSound764To763(holder - 1) + 1);
    }

    /** Sound Effect（764 → 763）。音の Holder だけ変換する。 */
    public static byte[] translateSoundEffect764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            translateSoundHolder764To763(reader, writer);
            writer.writeVarInt(reader.readVarInt());
            writer.writeInt(reader.readInt());
            writer.writeInt(reader.readInt());
            writer.writeInt(reader.readInt());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeLong(reader.readLong());
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Sound Effect を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Entity Sound Effect（764 → 763）。音の Holder だけ変換する。 */
    public static byte[] translateEntitySoundEffect764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            translateSoundHolder764To763(reader, writer);
            writer.writeVarInt(reader.readVarInt());
            writer.writeVarInt(reader.readVarInt());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeLong(reader.readLong());
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Entity Sound Effect を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Stop Sound（764 → 763）。
     * flags の bit 0 は音源（VarInt）、bit 1 は音の名前（文字列）。
     * 名前は763向けの名前に寄せる。
     */
    public static byte[] translateStopSound764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int flags = reader.readByte();
            Writer writer = new Writer();
            writer.writeByte(flags);
            if ((flags & 0x01) != 0) {
                writer.writeVarInt(reader.readVarInt());
            }
            if ((flags & 0x02) != 0) {
                writer.writeString(RegistryRemapper.mapSoundName(reader.readString()));
            }
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Stop Sound を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** World Particles（764 → 763）。パーティクルIDとそのデータを変換する。 */
    public static byte[] translateWorldParticles764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            translateParticle764To763(reader, writer);
            writer.writeBoolean(reader.readBoolean());
            writer.writeDouble(reader.readDouble());
            writer.writeDouble(reader.readDouble());
            writer.writeDouble(reader.readDouble());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeFloat(reader.readFloat());
            writer.writeInt(reader.readInt());
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("World Particles を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Entity Effect（764 → 763）。エフェクトIDは763が1ベース・764が0ベースの
     * ため1足す。factorCodec は764の名前なし NBT から763の名前付き NBT へ。
     */
    public static byte[] translateEntityEffect764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            int effectId = reader.readVarInt();
            int amplifier = reader.readByte();
            int duration = reader.readVarInt();
            int hideParticles = reader.readByte();
            boolean hasFactor = reader.readBoolean();
            Tag factorCodec = hasFactor ? reader.readAnonymousNbt().tag() : null;
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeVarInt(ClientAdapter.mapEffect764To763(effectId));
            writer.writeByte(amplifier);
            writer.writeVarInt(duration);
            writer.writeByte(hideParticles);
            writer.writeBoolean(factorCodec != null);
            if (factorCodec != null) {
                writer.writeNamedNbt("", factorCodec);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Entity Effect を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Remove Entity Effect（764 → 763）。エフェクトIDだけ1足す。 */
    public static byte[] translateRemoveEntityEffect764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            int effectId = reader.readVarInt();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeVarInt(ClientAdapter.mapEffect764To763(effectId));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Remove Entity Effect を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // サーバー行き（763→764／764→763／765→763：スロットだけ）。
    // Window Click・Set Creative Slot はスロットの変換（NBT の名前付けと
    // アイテムIDの付け替え）が要る。形式は3バージョンで同じ
    // （窓・状態・スロット・ボタン・モード・変更スロット列・カーソル）。
    // ------------------------------------------------------------------

    /** Window Click（763 → 764）。変更スロットとカーソルを764向けにする。 */
    public static byte[] translateWindowClick763To764(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeUnsignedByte(reader.readUnsignedByte());
            writer.writeVarInt(reader.readVarInt());
            writer.writeShort(reader.readShort());
            writer.writeByte(reader.readByte());
            writer.writeVarInt(reader.readVarInt());
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Window Click の件数が負です: " + count);
            }
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeShort(reader.readShort());
                translateSlot763To764(reader, writer);
            }
            translateSlot763To764(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Window Click を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Set Creative Slot（763 → 764）。 */
    public static byte[] translateSetCreativeSlot763To764(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeShort(reader.readShort());
            translateSlot763To764(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Creative Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Window Click（764 → 763：内部763用）。変更スロットとカーソルを763向けにする。 */
    public static byte[] translateWindowClick764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeUnsignedByte(reader.readUnsignedByte());
            writer.writeVarInt(reader.readVarInt());
            writer.writeShort(reader.readShort());
            writer.writeByte(reader.readByte());
            writer.writeVarInt(reader.readVarInt());
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Window Click の件数が負です: " + count);
            }
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeShort(reader.readShort());
                translateSlot764To763(reader, writer);
            }
            translateSlot764To763(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Window Click を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Set Creative Slot（764 → 763：内部763用）。 */
    public static byte[] translateSetCreativeSlot764To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeShort(reader.readShort());
            translateSlot764To763(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Creative Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Window Click（765 → 763：内部763用）。変更スロットとカーソルを763向けにする。 */
    public static byte[] translateWindowClick765To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeUnsignedByte(reader.readUnsignedByte());
            writer.writeVarInt(reader.readVarInt());
            writer.writeShort(reader.readShort());
            writer.writeByte(reader.readByte());
            writer.writeVarInt(reader.readVarInt());
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Window Click の件数が負です: " + count);
            }
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeShort(reader.readShort());
                translateSlot765To763(reader, writer);
            }
            translateSlot765To763(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Window Click を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Set Creative Slot（765 → 763：内部763用）。 */
    public static byte[] translateSetCreativeSlot765To763(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeShort(reader.readShort());
            translateSlot765To763(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Creative Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // 764→765のリソースパック（設定フェーズ／プレイ状態）。
    // ProxySession.buildConfigAddResourcePack765 と同じバイト列を作る。
    // ------------------------------------------------------------------

    /**
     * 764の Resource Pack（UUIDなし）から765の設定フェーズの
     * Add Resource Pack（UUID付き）を作る。UUID は保持したパックの
     * もの（764形式のパース時に振ったランダムな UUID）を使う。
     * プロンプトは JSON 文字列→NBT に変換する（{@link JsonToNbt}）。
     * 差し替え先の765用パック（{@code override}、pack_format 22）が
     * ある場合は、その URL と SHA-1 を使う。
     */
    public static byte[] translateConfigAddResourcePack764To765(PendingPack pack,
            ResourcePack override) {
        Writer writer = new Writer();
        writer.writeUuid(pack.uuid());
        writer.writeString(override != null ? override.url() : pack.url());
        writer.writeString(override != null ? override.sha1() : pack.hash());
        writer.writeBoolean(pack.forced());
        if (pack.promptJson() == null) {
            writer.writeBoolean(false);
        } else {
            writer.writeBoolean(true);
            writer.writeAnonymousNbt(JsonToNbt.fromJson(pack.promptJson()));
        }
        return writer.bytes();
    }

    /**
     * 764のプレイ状態の Resource Pack（UUIDなし）から765のプレイ状態の
     * Add Resource Pack（UUID付き）を作る。設定フェーズのものと
     * バイト列は同じである。
     */
    public static byte[] translatePlayResourcePack764To765(PendingPack pack,
            ResourcePack override) {
        return translateConfigAddResourcePack764To765(pack, override);
    }

    // ------------------------------------------------------------------
    // 766（1.20.5／1.20.6）向けの変換。
    // 情報源は PrismarineJS minecraft-data の protocol.json（1.20.5）。
    // 766の Registry Data は765と形式が違う（1個の codec ではなく、
    // レジストリごとの {id, entries: [{key, value}]}）。Login Success は
    // 末尾に strictErrorHandling（bool）が増えた。Join Game は
    // dimension が文字列ではなくレジストリ番号（VarInt）になった。
    // プレイ状態の766変換（スロットのデータコンポーネント化など）は
    // 別担当の対応表・レジストリ表を待つため、ここでは設定フェーズまで
    // の参加フローに要るものだけを扱う。
    // ------------------------------------------------------------------

    /**
     * 765の Login Success を766形式にする。末尾に strictErrorHandling
     * （bool）を1つ足すだけ（false＝厳密なエラー処理をしない）。
     * 765のペイロードは UUID・名前・プロパティで完結しているため、
     * 末尾への追加で正しい766形式になる。
     */
    public static byte[] translateLoginSuccess765To766(byte[] payload) {
        byte[] out = java.util.Arrays.copyOf(payload, payload.length + 1);
        out[payload.length] = 0;
        return out;
    }

    /**
     * 766の Login Success を765形式（763・764も同じ形式）にする。
     * 末尾の strictErrorHandling（bool）1バイトを落とす。
     * 末尾に bool が無い（壊れた）ペイロードは例外にする。
     */
    public static byte[] translateLoginSuccess766To765(byte[] payload) {
        Reader reader = new Reader(payload);
        UUID uuid = reader.readUuid();
        String name = reader.readString();
        int properties = reader.readVarInt();
        List<String[]> parsed = new ArrayList<>(properties);
        for (int i = 0; i < properties; i++) {
            String key = reader.readString();
            String value = reader.readString();
            boolean hasSignature = reader.readBoolean();
            String signature = hasSignature ? reader.readString() : null;
            parsed.add(new String[]{key, value, signature});
        }
        if (reader.remaining() != 1) {
            throw new IllegalArgumentException(
                    "Login Success に strictErrorHandling がありません（残り=" + reader.remaining() + "）。");
        }
        reader.readBoolean(); // strictErrorHandling。落とす。
        Writer writer = new Writer();
        writer.writeUuid(uuid);
        writer.writeString(name);
        writer.writeVarInt(parsed.size());
        for (String[] property : parsed) {
            writer.writeString(property[0]);
            writer.writeString(property[1]);
            writer.writeBoolean(property[2] != null);
            if (property[2] != null) {
                writer.writeString(property[2]);
            }
        }
        return writer.bytes();
    }


    /**
     * 765の Login Success を767形式にする。767も766と同じく末尾に
     * strictErrorHandling（bool）が付くため {@link #translateLoginSuccess765To766}
     * と同じ（末尾に {@code 0} を1バイト足す）。
     */
    public static byte[] translateLoginSuccess765To767(byte[] payload) {
        return translateLoginSuccess765To766(payload);
    }

    /**
     * 767の Login Success を765形式にする。末尾の strictErrorHandling を落とす。
     * {@link #translateLoginSuccess766To765} と同じ。
     */
    public static byte[] translateLoginSuccess767To765(byte[] payload) {
        return translateLoginSuccess766To765(payload);
    }

    /**
     * 766の Login Success を767形式にする。両方とも末尾に strictErrorHandling
     * が付くため形式は同一で、そのまま返す。
     */
    public static byte[] translateLoginSuccess766To767(byte[] payload) {
        return payload;
    }

    /**
     * 767の Login Success を766形式にする。{@link #translateLoginSuccess766To767}
     * と同じく形式は同一で、そのまま返す。
     */
    public static byte[] translateLoginSuccess767To766(byte[] payload) {
        return payload;
    }

    /**
     * 765／764の Registry Data（1個の codec NBT）を766形式に変換する。
     * 766はレジストリごとに1パケット（{id, entries: [{key, value}]}）
     * になるため、レジストリごとのペイロードのリストを返す。
     * codec のエントリは {name, id, element} で、766のエントリは
     * {key: name, value: element} になる（数値 id は766では順序で
     * 決まるため落とす）。要素が無いエントリは value なし（bool false）
     * で送る。
     */
    public static List<byte[]> translateRegistryData765To766(byte[] payload) {
        return buildRegistryData766(parseRegistryData(payload));
    }

    /**
     * 保持したレジストリ（codec 形式）から766の Registry Data の
     * ペイロード列を作る。レジストリごとに1パケット。
     * 内部763の Join Game の codec から766クライアント向けの
     * 設定フェーズを合成するときにも使う。
     */
    public static List<byte[]> buildRegistryData766(
            Map<String, Tag.CompoundTag> registries) {
        List<byte[]> out = new ArrayList<>();
        for (Map.Entry<String, Tag.CompoundTag> registry : registries.entrySet()) {
            Writer writer = new Writer();
            writer.writeString(registry.getKey());
            List<Tag> entries = registry.getValue().getList("value")
                    .map(Tag.ListTag::value)
                    .orElse(List.of());
            writer.writeVarInt(entries.size());
            for (Tag entry : entries) {
                if (!(entry instanceof Tag.CompoundTag compound)) {
                    throw new IllegalArgumentException(
                            "Registry Data のエントリがコンパウンドではありません: " + entry);
                }
                String name = compound.getString("name")
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Registry Data のエントリに name がありません。"));
                writer.writeString(name);
                java.util.Optional<Tag> element = compound.get("element");
                writer.writeBoolean(element.isPresent());
                if (element.isPresent()) {
                    writer.writeAnonymousNbt(element.get());
                }
            }
            out.add(writer.bytes());
        }
        return out;
    }

    /**
     * 766の Registry Data（レジストリごとのエントリ配列）を765／764形式
     * （1個の codec NBT）に変換する。
     * {@link #parseRegistryData(byte[])} の旧形式フォールバックが766の
     * 並び（id＋件数＋{key, 有無, NBT}）をそのまま読めるため、
     * それを使って codec を組み立て直す。数値 id はエントリの順序で
     * 合成する（766では順序で決まるため）。
     */
    public static byte[] translateRegistryData766To765(byte[] payload) {
        Map<String, Tag.CompoundTag> registries = parseRegistryData(payload);
        Map<String, Tag> root = new LinkedHashMap<>();
        for (Map.Entry<String, Tag.CompoundTag> registry : registries.entrySet()) {
            root.put(registry.getKey(), registry.getValue());
        }
        return Nbt.writeAnonymous(new Tag.CompoundTag(root));
    }


    private static final String[] ENCHANTMENT_765_NAMES = {
            "minecraft:protection", "minecraft:fire_protection", "minecraft:feather_falling", "minecraft:blast_protection", "minecraft:projectile_protection", "minecraft:respiration",
            "minecraft:aqua_affinity", "minecraft:thorns", "minecraft:depth_strider", "minecraft:frost_walker", "minecraft:binding_curse", "minecraft:soul_speed",
            "minecraft:swift_sneak", "minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods", "minecraft:knockback", "minecraft:fire_aspect",
            "minecraft:looting", "minecraft:sweeping", "minecraft:efficiency", "minecraft:silk_touch", "minecraft:unbreaking", "minecraft:fortune",
            "minecraft:power", "minecraft:punch", "minecraft:flame", "minecraft:infinity", "minecraft:luck_of_the_sea", "minecraft:lure",
            "minecraft:loyalty", "minecraft:impaling", "minecraft:riptide", "minecraft:channeling", "minecraft:multishot", "minecraft:quick_charge",
            "minecraft:piercing", "minecraft:mending", "minecraft:vanishing_curse",
    };

    private static final String[] ENCHANTMENT_766_NAMES = {
            "minecraft:protection", "minecraft:fire_protection", "minecraft:feather_falling", "minecraft:blast_protection", "minecraft:projectile_protection", "minecraft:respiration",
            "minecraft:aqua_affinity", "minecraft:thorns", "minecraft:depth_strider", "minecraft:frost_walker", "minecraft:binding_curse", "minecraft:soul_speed",
            "minecraft:swift_sneak", "minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods", "minecraft:knockback", "minecraft:fire_aspect",
            "minecraft:looting", "minecraft:sweeping_edge", "minecraft:efficiency", "minecraft:silk_touch", "minecraft:unbreaking", "minecraft:fortune",
            "minecraft:power", "minecraft:punch", "minecraft:flame", "minecraft:infinity", "minecraft:luck_of_the_sea", "minecraft:lure",
            "minecraft:loyalty", "minecraft:impaling", "minecraft:riptide", "minecraft:channeling", "minecraft:multishot", "minecraft:quick_charge",
            "minecraft:piercing", "minecraft:density", "minecraft:breach", "minecraft:wind_burst", "minecraft:mending", "minecraft:vanishing_curse",
    };

    private static final String[] ENCHANTMENT_767_NAMES = {
            "minecraft:aqua_affinity", "minecraft:bane_of_arthropods", "minecraft:binding_curse", "minecraft:blast_protection", "minecraft:breach", "minecraft:channeling",
            "minecraft:density", "minecraft:depth_strider", "minecraft:efficiency", "minecraft:feather_falling", "minecraft:fire_aspect", "minecraft:fire_protection",
            "minecraft:flame", "minecraft:fortune", "minecraft:frost_walker", "minecraft:impaling", "minecraft:infinity", "minecraft:knockback",
            "minecraft:looting", "minecraft:loyalty", "minecraft:luck_of_the_sea", "minecraft:lure", "minecraft:mending", "minecraft:multishot",
            "minecraft:piercing", "minecraft:power", "minecraft:projectile_protection", "minecraft:protection", "minecraft:punch", "minecraft:quick_charge",
            "minecraft:respiration", "minecraft:riptide", "minecraft:sharpness", "minecraft:silk_touch", "minecraft:smite", "minecraft:soul_speed",
            "minecraft:sweeping_edge", "minecraft:swift_sneak", "minecraft:thorns", "minecraft:unbreaking", "minecraft:vanishing_curse", "minecraft:wind_burst",
    };

    /** 768（1.21.2／1.21.3）のエンチャント名。767と同一の42件・同じ並び。 */
    private static final String[] ENCHANTMENT_768_NAMES = {
            "minecraft:aqua_affinity", "minecraft:bane_of_arthropods", "minecraft:binding_curse", "minecraft:blast_protection", "minecraft:breach", "minecraft:channeling",
            "minecraft:density", "minecraft:depth_strider", "minecraft:efficiency", "minecraft:feather_falling", "minecraft:fire_aspect", "minecraft:fire_protection",
            "minecraft:flame", "minecraft:fortune", "minecraft:frost_walker", "minecraft:impaling", "minecraft:infinity", "minecraft:knockback",
            "minecraft:looting", "minecraft:loyalty", "minecraft:luck_of_the_sea", "minecraft:lure", "minecraft:mending", "minecraft:multishot",
            "minecraft:piercing", "minecraft:power", "minecraft:projectile_protection", "minecraft:protection", "minecraft:punch", "minecraft:quick_charge",
            "minecraft:respiration", "minecraft:riptide", "minecraft:sharpness", "minecraft:silk_touch", "minecraft:smite", "minecraft:soul_speed",
            "minecraft:sweeping_edge", "minecraft:swift_sneak", "minecraft:thorns", "minecraft:unbreaking", "minecraft:vanishing_curse", "minecraft:wind_burst",
    };

    /** 774（1.21.11）のエンチャント名。768の42件に lunge（21）を加えた43件。 */
    private static final String[] ENCHANTMENT_774_NAMES = {
            "minecraft:aqua_affinity", "minecraft:bane_of_arthropods", "minecraft:binding_curse", "minecraft:blast_protection", "minecraft:breach", "minecraft:channeling",
            "minecraft:density", "minecraft:depth_strider", "minecraft:efficiency", "minecraft:feather_falling", "minecraft:fire_aspect", "minecraft:fire_protection",
            "minecraft:flame", "minecraft:fortune", "minecraft:frost_walker", "minecraft:impaling", "minecraft:infinity", "minecraft:knockback",
            "minecraft:looting", "minecraft:loyalty", "minecraft:luck_of_the_sea", "minecraft:lunge", "minecraft:lure", "minecraft:mending",
            "minecraft:multishot", "minecraft:piercing", "minecraft:power", "minecraft:projectile_protection", "minecraft:protection", "minecraft:punch",
            "minecraft:quick_charge", "minecraft:respiration", "minecraft:riptide", "minecraft:sharpness", "minecraft:silk_touch", "minecraft:smite",
            "minecraft:soul_speed", "minecraft:sweeping_edge", "minecraft:swift_sneak", "minecraft:thorns", "minecraft:unbreaking", "minecraft:vanishing_curse",
            "minecraft:wind_burst",
    };

    // 766（1.20.5）の絵画は30件のコード登録順。配置可能な26件に加え、
    // コマンド限定の4件（earth・wind・water・fire、1.19から存在）を含む
    // （ViaVersion mapping-1.20.5.json の paintings で確認）。1.20.5は
    // painting_variant を Registry Data で送らないため、実サーバーの
    // Registry Data では確認していない。
    private static final String[] PAINTING_766_NAMES = {
            "minecraft:kebab", "minecraft:aztec", "minecraft:alban", "minecraft:aztec2", "minecraft:bomb", "minecraft:plant",
            "minecraft:wasteland", "minecraft:pool", "minecraft:courbet", "minecraft:sea", "minecraft:sunset", "minecraft:creebet",
            "minecraft:wanderer", "minecraft:graham", "minecraft:match", "minecraft:bust", "minecraft:stage", "minecraft:void",
            "minecraft:skull_and_roses", "minecraft:wither", "minecraft:fighters", "minecraft:pointer", "minecraft:pigscene", "minecraft:burning_skull",
            "minecraft:skeleton", "minecraft:earth", "minecraft:wind", "minecraft:water", "minecraft:fire", "minecraft:donkey_kong",
    };

    private static final String[] PAINTING_767_NAMES = {
            "minecraft:alban", "minecraft:aztec", "minecraft:aztec2", "minecraft:backyard", "minecraft:baroque", "minecraft:bomb",
            "minecraft:bouquet", "minecraft:burning_skull", "minecraft:bust", "minecraft:cavebird", "minecraft:changing", "minecraft:cotan",
            "minecraft:courbet", "minecraft:creebet", "minecraft:donkey_kong", "minecraft:earth", "minecraft:endboss", "minecraft:fern",
            "minecraft:fighters", "minecraft:finding", "minecraft:fire", "minecraft:graham", "minecraft:humble", "minecraft:kebab",
            "minecraft:lowmist", "minecraft:match", "minecraft:meditative", "minecraft:orb", "minecraft:owlemons", "minecraft:passage",
            "minecraft:pigscene", "minecraft:plant", "minecraft:pointer", "minecraft:pond", "minecraft:pool", "minecraft:prairie_ride",
            "minecraft:sea", "minecraft:skeleton", "minecraft:skull_and_roses", "minecraft:stage", "minecraft:sunflowers", "minecraft:sunset",
            "minecraft:tides", "minecraft:unpacked", "minecraft:void", "minecraft:wanderer", "minecraft:wasteland", "minecraft:water",
            "minecraft:wind", "minecraft:wither",
    };

    private static final String[] BANNER_766_NAMES = {
            "minecraft:base", "minecraft:border", "minecraft:bricks", "minecraft:circle", "minecraft:creeper", "minecraft:cross",
            "minecraft:curly_border", "minecraft:diagonal_left", "minecraft:diagonal_right", "minecraft:diagonal_up_left", "minecraft:diagonal_up_right", "minecraft:flower",
            "minecraft:globe", "minecraft:gradient", "minecraft:gradient_up", "minecraft:half_horizontal", "minecraft:half_horizontal_bottom", "minecraft:half_vertical",
            "minecraft:half_vertical_right", "minecraft:mojang", "minecraft:piglin", "minecraft:rhombus", "minecraft:skull", "minecraft:small_stripes",
            "minecraft:square_bottom_left", "minecraft:square_bottom_right", "minecraft:square_top_left", "minecraft:square_top_right", "minecraft:straight_cross", "minecraft:stripe_bottom",
            "minecraft:stripe_center", "minecraft:stripe_downleft", "minecraft:stripe_downright", "minecraft:stripe_left", "minecraft:stripe_middle", "minecraft:stripe_right",
            "minecraft:stripe_top", "minecraft:triangle_bottom", "minecraft:triangle_top", "minecraft:triangles_bottom", "minecraft:triangles_top",
    };

    private static final String[] BANNER_767_NAMES = {
            "minecraft:base", "minecraft:border", "minecraft:bricks", "minecraft:circle", "minecraft:creeper", "minecraft:cross",
            "minecraft:curly_border", "minecraft:diagonal_left", "minecraft:diagonal_right", "minecraft:diagonal_up_left", "minecraft:diagonal_up_right", "minecraft:flow",
            "minecraft:flower", "minecraft:globe", "minecraft:gradient", "minecraft:gradient_up", "minecraft:guster", "minecraft:half_horizontal",
            "minecraft:half_horizontal_bottom", "minecraft:half_vertical", "minecraft:half_vertical_right", "minecraft:mojang", "minecraft:piglin", "minecraft:rhombus",
            "minecraft:skull", "minecraft:small_stripes", "minecraft:square_bottom_left", "minecraft:square_bottom_right", "minecraft:square_top_left", "minecraft:square_top_right",
            "minecraft:straight_cross", "minecraft:stripe_bottom", "minecraft:stripe_center", "minecraft:stripe_downleft", "minecraft:stripe_downright", "minecraft:stripe_left",
            "minecraft:stripe_middle", "minecraft:stripe_right", "minecraft:stripe_top", "minecraft:triangle_bottom", "minecraft:triangle_top", "minecraft:triangles_bottom",
            "minecraft:triangles_top",
    };

    private static final String[] TRIM_766_NAMES = {
            "minecraft:coast", "minecraft:dune", "minecraft:eye", "minecraft:host", "minecraft:raiser", "minecraft:rib",
            "minecraft:sentry", "minecraft:shaper", "minecraft:silence", "minecraft:snout", "minecraft:spire", "minecraft:tide",
            "minecraft:vex", "minecraft:ward", "minecraft:wayfinder", "minecraft:wild",
    };

    private static final String[] TRIM_767_NAMES = {
            "minecraft:bolt", "minecraft:coast", "minecraft:dune", "minecraft:eye", "minecraft:flow", "minecraft:host",
            "minecraft:raiser", "minecraft:rib", "minecraft:sentry", "minecraft:shaper", "minecraft:silence", "minecraft:snout",
            "minecraft:spire", "minecraft:tide", "minecraft:vex", "minecraft:ward", "minecraft:wayfinder", "minecraft:wild",
    };

    private static final String[] DAMAGE_766_NAMES = {
            "minecraft:arrow", "minecraft:bad_respawn_point", "minecraft:cactus", "minecraft:cramming", "minecraft:dragon_breath", "minecraft:drown",
            "minecraft:dry_out", "minecraft:explosion", "minecraft:fall", "minecraft:falling_anvil", "minecraft:falling_block", "minecraft:falling_stalactite",
            "minecraft:fireball", "minecraft:fireworks", "minecraft:fly_into_wall", "minecraft:freeze", "minecraft:generic", "minecraft:generic_kill",
            "minecraft:hot_floor", "minecraft:in_fire", "minecraft:in_wall", "minecraft:indirect_magic", "minecraft:lava", "minecraft:lightning_bolt",
            "minecraft:magic", "minecraft:mob_attack", "minecraft:mob_attack_no_aggro", "minecraft:mob_projectile", "minecraft:on_fire", "minecraft:out_of_world",
            "minecraft:outside_border", "minecraft:player_attack", "minecraft:player_explosion", "minecraft:sonic_boom", "minecraft:spit", "minecraft:stalagmite",
            "minecraft:starve", "minecraft:sting", "minecraft:sweet_berry_bush", "minecraft:thorns", "minecraft:thrown", "minecraft:trident",
            "minecraft:unattributed_fireball", "minecraft:wither", "minecraft:wither_skull",
    };

    private static final String[] DAMAGE_767_NAMES = {
            "minecraft:arrow", "minecraft:bad_respawn_point", "minecraft:cactus", "minecraft:campfire", "minecraft:cramming", "minecraft:dragon_breath",
            "minecraft:drown", "minecraft:dry_out", "minecraft:explosion", "minecraft:fall", "minecraft:falling_anvil", "minecraft:falling_block",
            "minecraft:falling_stalactite", "minecraft:fireball", "minecraft:fireworks", "minecraft:fly_into_wall", "minecraft:freeze", "minecraft:generic",
            "minecraft:generic_kill", "minecraft:hot_floor", "minecraft:in_fire", "minecraft:in_wall", "minecraft:indirect_magic", "minecraft:lava",
            "minecraft:lightning_bolt", "minecraft:magic", "minecraft:mob_attack", "minecraft:mob_attack_no_aggro", "minecraft:mob_projectile", "minecraft:on_fire",
            "minecraft:out_of_world", "minecraft:outside_border", "minecraft:player_attack", "minecraft:player_explosion", "minecraft:sonic_boom", "minecraft:spit",
            "minecraft:stalagmite", "minecraft:starve", "minecraft:sting", "minecraft:sweet_berry_bush", "minecraft:thorns", "minecraft:thrown",
            "minecraft:trident", "minecraft:unattributed_fireball", "minecraft:wind_charge", "minecraft:wither", "minecraft:wither_skull",
    };

    /** 768（1.21.2／1.21.3）のダメージ種別名。767の47件に ender_pearl（8）と mace_smash（26）を加えた49件。mace_smash は magic の前に来る（アルファベット順）。 */
    private static final String[] DAMAGE_768_NAMES = {
            "minecraft:arrow", "minecraft:bad_respawn_point", "minecraft:cactus", "minecraft:campfire", "minecraft:cramming", "minecraft:dragon_breath",
            "minecraft:drown", "minecraft:dry_out", "minecraft:ender_pearl", "minecraft:explosion", "minecraft:fall", "minecraft:falling_anvil",
            "minecraft:falling_block", "minecraft:falling_stalactite", "minecraft:fireball", "minecraft:fireworks", "minecraft:fly_into_wall", "minecraft:freeze",
            "minecraft:generic", "minecraft:generic_kill", "minecraft:hot_floor", "minecraft:in_fire", "minecraft:in_wall", "minecraft:indirect_magic",
            "minecraft:lava", "minecraft:lightning_bolt", "minecraft:mace_smash", "minecraft:magic", "minecraft:mob_attack", "minecraft:mob_attack_no_aggro",
            "minecraft:mob_projectile", "minecraft:on_fire", "minecraft:out_of_world", "minecraft:outside_border", "minecraft:player_attack", "minecraft:player_explosion",
            "minecraft:sonic_boom", "minecraft:spit", "minecraft:stalagmite", "minecraft:starve", "minecraft:sting", "minecraft:sweet_berry_bush",
            "minecraft:thorns", "minecraft:thrown", "minecraft:trident", "minecraft:unattributed_fireball", "minecraft:wind_charge", "minecraft:wither",
            "minecraft:wither_skull",
    };

    /** 774（1.21.11）のダメージ種別名。768の49件に spear（37）を加えた50件。 */
    private static final String[] DAMAGE_774_NAMES = {
            "minecraft:arrow", "minecraft:bad_respawn_point", "minecraft:cactus", "minecraft:campfire", "minecraft:cramming", "minecraft:dragon_breath",
            "minecraft:drown", "minecraft:dry_out", "minecraft:ender_pearl", "minecraft:explosion", "minecraft:fall", "minecraft:falling_anvil",
            "minecraft:falling_block", "minecraft:falling_stalactite", "minecraft:fireball", "minecraft:fireworks", "minecraft:fly_into_wall", "minecraft:freeze",
            "minecraft:generic", "minecraft:generic_kill", "minecraft:hot_floor", "minecraft:in_fire", "minecraft:in_wall", "minecraft:indirect_magic",
            "minecraft:lava", "minecraft:lightning_bolt", "minecraft:mace_smash", "minecraft:magic", "minecraft:mob_attack", "minecraft:mob_attack_no_aggro",
            "minecraft:mob_projectile", "minecraft:on_fire", "minecraft:out_of_world", "minecraft:outside_border", "minecraft:player_attack", "minecraft:player_explosion",
            "minecraft:sonic_boom", "minecraft:spear", "minecraft:spit", "minecraft:stalagmite", "minecraft:starve", "minecraft:sting",
            "minecraft:sweet_berry_bush", "minecraft:thorns", "minecraft:thrown", "minecraft:trident", "minecraft:unattributed_fireball", "minecraft:wind_charge",
            "minecraft:wither", "minecraft:wither_skull",
    };

    /**
     * レジストリの順序が変わった組み合わせで、送信側の数値 id から
     * 受信側の数値 id への写像を返す。順序が変わらない組み合わせは
     * {@code null}。763／764は765と同じ順序のため、ここでは765として扱う。
     * enchantment は765↔766でも順序が変わる（density・breach・wind_burst
     * の挿入で mending・vanishing_curse が後ろへずれる）。
     */
    private static java.util.function.IntUnaryOperator registryIdMapper(
            String registryId, int fromProtocol, int toProtocol) {
        int from = (fromProtocol == 763 || fromProtocol == 764) ? 765 : fromProtocol;
        int to = (toProtocol == 763 || toProtocol == 764) ? 765 : toProtocol;
        if (from == to) {
            return null;
        }
        return switch (registryId) {
            case "minecraft:enchantment" -> switch (from * 1000 + to) {
                case 765766 -> RegistryRemapper::mapEnchantment765To766;
                case 766765 -> RegistryRemapper::mapEnchantment766To765;
                case 766767 -> RegistryRemapper::mapEnchantment766To767;
                case 767766 -> RegistryRemapper::mapEnchantment767To766;
                case 765767 -> RegistryRemapper::mapEnchantment765To767;
                case 767765 -> RegistryRemapper::mapEnchantment767To765;
                case 767768 -> RegistryRemapper::mapEnchantment767To768;
                case 768767 -> RegistryRemapper::mapEnchantment768To767;
                case 765768 -> RegistryRemapper::mapEnchantment765To768;
                case 768765 -> RegistryRemapper::mapEnchantment768To765;
                case 766768 -> RegistryRemapper::mapEnchantment766To768;
                case 768766 -> RegistryRemapper::mapEnchantment768To766;
                case 773774 -> RegistryRemapper::mapEnchantment773To774;
                case 774773 -> RegistryRemapper::mapEnchantment774To773;
                case 774775 -> RegistryRemapper::mapEnchantment774To775;
                case 775774 -> RegistryRemapper::mapEnchantment775To774;
                default -> null;
            };
            case "minecraft:painting_variant" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapPaintingVariant766To767;
                case 767766 -> RegistryRemapper::mapPaintingVariant767To766;
                case 765767 -> RegistryRemapper::mapPaintingVariant765To767;
                case 767765 -> RegistryRemapper::mapPaintingVariant767To765;
                case 767768 -> RegistryRemapper::mapPaintingVariant767To768;
                case 768767 -> RegistryRemapper::mapPaintingVariant768To767;
                case 765768 -> RegistryRemapper::mapPaintingVariant765To768;
                case 768765 -> RegistryRemapper::mapPaintingVariant768To765;
                case 766768 -> RegistryRemapper::mapPaintingVariant766To768;
                case 768766 -> RegistryRemapper::mapPaintingVariant768To766;
                default -> null;
            };
            case "minecraft:banner_pattern" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapBannerPattern766To767;
                case 767766 -> RegistryRemapper::mapBannerPattern767To766;
                case 765767 -> RegistryRemapper::mapBannerPattern765To767;
                case 767765 -> RegistryRemapper::mapBannerPattern767To765;
                case 767768 -> RegistryRemapper::mapBannerPattern767To768;
                case 768767 -> RegistryRemapper::mapBannerPattern768To767;
                case 765768 -> RegistryRemapper::mapBannerPattern765To768;
                case 768765 -> RegistryRemapper::mapBannerPattern768To765;
                case 766768 -> RegistryRemapper::mapBannerPattern766To768;
                case 768766 -> RegistryRemapper::mapBannerPattern768To766;
                default -> null;
            };
            case "minecraft:trim_pattern" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapTrimPattern766To767;
                case 767766 -> RegistryRemapper::mapTrimPattern767To766;
                case 765767 -> RegistryRemapper::mapTrimPattern765To767;
                case 767765 -> RegistryRemapper::mapTrimPattern767To765;
                case 767768 -> RegistryRemapper::mapTrimPattern767To768;
                case 768767 -> RegistryRemapper::mapTrimPattern768To767;
                case 765768 -> RegistryRemapper::mapTrimPattern765To768;
                case 768765 -> RegistryRemapper::mapTrimPattern768To765;
                case 766768 -> RegistryRemapper::mapTrimPattern766To768;
                case 768766 -> RegistryRemapper::mapTrimPattern768To766;
                default -> null;
            };
            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                case 771772 -> RegistryRemapper::mapJukeboxSong771To772;
                case 772771 -> RegistryRemapper::mapJukeboxSong772To771;
                case 772773 -> RegistryRemapper::mapJukeboxSong772To773;
                case 773772 -> RegistryRemapper::mapJukeboxSong773To772;
                case 773774 -> RegistryRemapper::mapJukeboxSong773To774;
                case 774773 -> RegistryRemapper::mapJukeboxSong774To773;
                case 774775 -> RegistryRemapper::mapJukeboxSong774To775;
                case 775774 -> RegistryRemapper::mapJukeboxSong775To774;
                default -> null;
            };
            case "minecraft:damage_type" -> switch (from * 1000 + to) {
                case 766767 -> RegistryRemapper::mapDamageType766To767;
                case 767766 -> RegistryRemapper::mapDamageType767To766;
                case 765767 -> RegistryRemapper::mapDamageType765To767;
                case 767765 -> RegistryRemapper::mapDamageType767To765;
                case 767768 -> RegistryRemapper::mapDamageType767To768;
                case 768767 -> RegistryRemapper::mapDamageType768To767;
                case 765768 -> RegistryRemapper::mapDamageType765To768;
                case 768765 -> RegistryRemapper::mapDamageType768To765;
                case 766768 -> RegistryRemapper::mapDamageType766To768;
                case 768766 -> RegistryRemapper::mapDamageType768To766;
                case 773774 -> RegistryRemapper::mapDamageType773To774;
                case 774773 -> RegistryRemapper::mapDamageType774To773;
                case 774775 -> RegistryRemapper::mapDamageType774To775;
                case 775774 -> RegistryRemapper::mapDamageType775To774;
                default -> null;
            };
            case "minecraft:worldgen/biome" -> switch (from * 1000 + to) {
                case 757759 -> RegistryRemapper::mapBiome757To759;
                case 759757 -> RegistryRemapper::mapBiome759To757;
                default -> null;
            };
            case "minecraft:dimension_type" -> switch (from * 1000 + to) {
                case 757759 -> PlayTranslator::mapDimensionType757To759;
                case 759757 -> PlayTranslator::mapDimensionType759To757;
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * 受信側プロトコルでのレジストリの全エントリ名。順序が変わった
     * レジストリで、送信側に無いエントリ（767だけにある絵画など）を
     * hasData=false で埋めるときに使う。765は766と同じ表を使う
     * （enchantment だけが765固有の表）。
     */
    private static String[] registryTargetNames(String registryId, int toProtocol) {
        int to = (toProtocol == 763 || toProtocol == 764) ? 765 : toProtocol;
        return switch (registryId) {
            case "minecraft:enchantment" -> switch (to) {
                case 765 -> ENCHANTMENT_765_NAMES;
                case 766 -> ENCHANTMENT_766_NAMES;
                case 767 -> ENCHANTMENT_767_NAMES;
                case 768, 769, 770, 771, 772, 773 -> ENCHANTMENT_768_NAMES;
                case 774 -> ENCHANTMENT_774_NAMES;
                case 775 -> ENCHANTMENT_774_NAMES; // 774と同一の43件
                default -> null;
            };
            case "minecraft:painting_variant" -> to == 767 || to == 768 || to == 769 ? PAINTING_767_NAMES : PAINTING_766_NAMES;
            case "minecraft:banner_pattern" -> to == 767 || to == 768 || to == 769 ? BANNER_767_NAMES : BANNER_766_NAMES;
            case "minecraft:trim_pattern" -> to == 767 || to == 768 || to == 769 ? TRIM_767_NAMES : TRIM_766_NAMES;
            case "minecraft:jukebox_song" -> switch (to) {
                case 775 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 774と同一の21曲
                case 774 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 773と同一の21曲
                case 773 -> RegistryTables773.JUKEBOX_SONG_773_NAMES;
                case 772 -> RegistryTables772.JUKEBOX_SONG_772_NAMES;
                case 771 -> RegistryTables771.JUKEBOX_SONG_771_NAMES;
                case 770 -> RegistryTables771.JUKEBOX_SONG_770_NAMES;
                case 767, 768, 769 -> RegistryTables771.JUKEBOX_SONG_770_NAMES;
                default -> null;
            };
            case "minecraft:damage_type" -> switch (to) {
                case 767 -> DAMAGE_767_NAMES;
                case 768, 769, 770, 771, 772, 773 -> DAMAGE_768_NAMES;
                case 774 -> DAMAGE_774_NAMES;
                case 775 -> DAMAGE_774_NAMES; // 774と同一の50件
                default -> DAMAGE_766_NAMES;
            };
            case "minecraft:worldgen/biome" -> switch (to) {
                case 757 -> BIOME_757_NAMES;
                case 759 -> BIOME_759_NAMES;
                default -> null;
            };
            case "minecraft:dimension_type" -> switch (to) {
                case 757 -> DIMENSION_TYPE_757_NAMES;
                case 759 -> DIMENSION_TYPE_759_NAMES;
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * 1つのレジストリのエントリ列を、送信側の順序から受信側の順序へ
     * 並べ替える。受信側に無いエントリは落とし、送信側に無いエントリ
     * （受信側だけにあるもの）は名前だけのエントリ（hasData=false）
     * で埋める。埋めたエントリは数値 id の位置合わせのためで、実機
     * クライアントでの扱いは未確認。エントリの element（NBT）は
     * そのまま引き継ぐ。767→766/765 では sweeping→sweeping_edge の
     * 改名もここで吸収する（名前は受信側の表で置き換える）。
     */
    private static List<Tag> reorderRegistryEntries(String registryId,
            List<Tag> entries, int fromProtocol, int toProtocol) {
        java.util.function.IntUnaryOperator map =
                registryIdMapper(registryId, fromProtocol, toProtocol);
        if (map == null) {
            return entries;
        }
        String[] targetNames = registryTargetNames(registryId, toProtocol);
        List<Tag> out = new ArrayList<>(java.util.Collections.nCopies(targetNames.length, null));
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Tag.CompoundTag compound)) {
                continue;
            }
            int srcId = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value())
                    .orElse(i);
            int targetId = map.applyAsInt(srcId);
            if (targetId < 0 || targetId >= targetNames.length) {
                continue;
            }
            Map<String, Tag> entry = new LinkedHashMap<>();
            entry.put("name", new Tag.StringTag(targetNames[targetId]));
            entry.put("id", new Tag.IntTag(targetId));
            compound.get("element").ifPresent(t -> entry.put("element", t));
            // 受信側に無いエントリへのフォールバックで複数の送信側エントリが同じ
            // 受信側 id になる場合（759→757のバイオームで mangrove_swamp→swamp 等）、
            // 先に来たもの（真の対応）を残す。他の変換は全て単射のため挙動は変わらない。
            if (out.get(targetId) == null) {
                out.set(targetId, new Tag.CompoundTag(entry));
            }
        }
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) {
                Map<String, Tag> entry = new LinkedHashMap<>();
                entry.put("name", new Tag.StringTag(targetNames[i]));
                entry.put("id", new Tag.IntTag(i));
                out.set(i, new Tag.CompoundTag(entry));
            }
        }
        return out;
    }

    /**
     * 保持したレジストリ（codec 形式）のうち、順序が変わったレジストリを
     * 受信側の順序に並べ替える。763／764は765として扱う。
     * {@code minecraft:jukebox_song} は766／765には存在しないため、
     * 受信側が766／765の場合はレジストリごと落とす。
     * {@code minecraft:chat_type} は1.19で追加されたため、
     * 受信側が757の場合はレジストリごと落とす。
     * 入力のマップは変更しない（新しいマップを返す）。
     */
    public static Map<String, Tag.CompoundTag> reorderRegistries(
            Map<String, Tag.CompoundTag> registries, int fromProtocol, int toProtocol) {
        Map<String, Tag.CompoundTag> out = new LinkedHashMap<>();
        for (Map.Entry<String, Tag.CompoundTag> registry : registries.entrySet()) {
            String registryId = registry.getKey();
            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11
                    && toProtocol != ProtocolVersion.V26_1) {
                continue;
            }
            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11
                    && toProtocol != ProtocolVersion.V26_1) {
                continue;
            }
            if (registryId.equals("minecraft:chat_type") && toProtocol == ProtocolVersion.V1_18) {
                continue;
            }
            List<Tag> entries = registry.getValue().getList("value")
                    .map(Tag.ListTag::value)
                    .orElse(List.of());
            List<Tag> reordered = reorderRegistryEntries(
                    registryId, entries, fromProtocol, toProtocol);
            if (reordered == entries) {
                out.put(registryId, registry.getValue());
                continue;
            }
            Map<String, Tag> codec = new LinkedHashMap<>();
            codec.put("type", new Tag.StringTag(registryId));
            codec.put("value", new Tag.ListTag(Nbt.COMPOUND, reordered));
            out.put(registryId, new Tag.CompoundTag(codec));
        }
        return out;
    }

    /**
     * 766の Registry Data（レジストリごとのエントリ配列）を767形式にする。
     * 順序が変わったレジストリを並べ替え、767だけにあるエントリは
     * hasData=false で埋める。1レジストリ分のペイロードを受け取り、
     * 1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData766To767(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_20_5, ProtocolVersion.V1_21);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 767の Registry Data を766形式にする。{@code minecraft:jukebox_song}
     * は766に存在しないため {@code null} を返す（呼び出し側で落とす）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData767To766(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21, ProtocolVersion.V1_20_5);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * 765／764の Registry Data（1個の codec NBT）を767形式にする。
     * 766のエントリ配列は id を持たないためバイト列経由の合成はできない
     * （enchantment でずれる）ので、codec 段階で765→767へ直接並べ替えて
     * レジストリごとのペイロードのリストを返す。
     */
    public static List<byte[]> translateRegistryData765To767(byte[] payload) {
        return buildRegistryData766(reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_20_4, ProtocolVersion.V1_21));
    }

    /**
     * 767の Registry Data を765／764形式（1個の codec NBT）にする。
     * {@code minecraft:jukebox_song} の場合は {@code null} を返す
     * （呼び出し側で落とす）。
     */
    public static byte[] translateRegistryData767To765(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21, ProtocolVersion.V1_20_4);
        if (reordered.isEmpty()) {
            return null;
        }
        Map<String, Tag> root = new LinkedHashMap<>();
        for (Map.Entry<String, Tag.CompoundTag> registry : reordered.entrySet()) {
            root.put(registry.getKey(), registry.getValue());
        }
        return Nbt.writeAnonymous(new Tag.CompoundTag(root));
    }

    /** 763の Join Game の中身。codec は名前付き NBT（ルート名は空文字）。 */
    public record JoinGame763(int entityId, boolean hardcore, int gameMode,
            int previousGameMode, List<String> worldNames, Tag.CompoundTag codec,
            String worldType, String worldName, long hashedSeed, int maxPlayers,
            int viewDistance, int simulationDistance, boolean reducedDebug,
            boolean enableRespawnScreen, boolean isDebug, boolean isFlat,
            boolean hasDeath, String deathDimension, long deathLocation,
            int portalCooldown) {
    }

    /**
     * 763の Join Game（0x28）を読む。763の順番は
     * entityId, hardcore, gameMode, previousGameMode, worldNames, codec,
     * worldType, worldName, hashedSeed, maxPlayers, viewDistance,
     * simulationDistance, reducedDebug, enableRespawnScreen, isDebug, isFlat,
     * death, portalCooldown である。
     */
    public static JoinGame763 parseJoinGame763(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        int gameMode = reader.readByte();
        int previousGameMode = reader.readByte();
        List<String> worldNames = PacketIO.readStringList(reader);
        Tag.CompoundTag codec = (Tag.CompoundTag) reader.readNamedNbt().tag();
        String worldType = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        int simulationDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        int portalCooldown = reader.readVarInt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Join Game に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new JoinGame763(entityId, hardcore, gameMode, previousGameMode,
                worldNames, codec, worldType, worldName, hashedSeed, maxPlayers,
                viewDistance, simulationDistance, reducedDebug, enableRespawnScreen,
                isDebug, isFlat, hasDeath, deathDimension, deathLocation, portalCooldown);
    }

    /**
     * 763の Join Game の codec からレジストリの対応（名前→codec）を抜く。
     * dimension_type と worldgen/biome だけを使う（763の codec に入るのは
     * その2つである）。766向け Registry Data の合成にも使う。
     */
    public static Map<String, Tag.CompoundTag> registriesFromJoinGameCodec(
            Tag.CompoundTag codec) {
        Map<String, Tag.CompoundTag> registries = new LinkedHashMap<>();
        if (codec == null) {
            return registries;
        }
        for (String name : List.of("minecraft:dimension_type", "minecraft:worldgen/biome")) {
            codec.getCompound(name).ifPresent(registry -> registries.put(name, registry));
        }
        return registries;
    }

    /**
     * 763の Join Game を766形式（0x2B）に変換する。
     * 763には無い doLimitedCrafting は false、enforcesSecureChat は false
     * で補う（内部763はチャット署名の強制をしない）。dimension は文字列
     * （worldType）ではなくレジストリ番号（VarInt）になるため、
     * {@code registries} の dimension_type から worldType の番号を引く。
     * 見つからなければ 0（overworld）にする。
     */
    public static byte[] translateJoinGame763To766(JoinGame763 join,
            Map<String, Tag.CompoundTag> registries) {
        int dimensionId = dimensionIdFor(join.worldType(), registries);
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(false); // doLimitedCrafting（763には無い）
        // SpawnInfo：dimension（VarInt）＋worldName＋hashedSeed＋gameMode＋
        // previousGameMode＋isDebug＋isFlat＋death＋portalCooldown。
        writer.writeVarInt(dimensionId);
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        writer.writeVarInt(join.portalCooldown());
        writer.writeBoolean(false); // enforcesSecureChat
        return writer.bytes();
    }

    /** dimension_type のレジストリから、名前に対応する番号を引く。無ければ 0。 */
    private static int dimensionIdFor(String worldType,
            Map<String, Tag.CompoundTag> registries) {
        Tag.CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
        if (dimensionRegistry == null) {
            return 0;
        }
        List<Tag> entries = dimensionRegistry.getList("value")
                .map(Tag.ListTag::value)
                .orElse(List.of());
        for (Tag entry : entries) {
            if (entry instanceof Tag.CompoundTag compound
                    && compound.getString("name").orElse("").equals(worldType)) {
                return compound.get("id")
                        .filter(t -> t instanceof Tag.IntTag)
                        .map(t -> ((Tag.IntTag) t).value())
                        .orElse(0);
            }
        }
        return 0;
    }

    /** dimension_type のレジストリから、番号に対応する名前を引く。無ければ overworld。 */
    private static String dimensionNameFor(int dimensionId,
            Map<String, Tag.CompoundTag> registries) {
        Tag.CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
        if (dimensionRegistry == null) {
            return "minecraft:overworld";
        }
        List<Tag> entries = dimensionRegistry.getList("value")
                .map(Tag.ListTag::value)
                .orElse(List.of());
        for (Tag entry : entries) {
            if (entry instanceof Tag.CompoundTag compound
                    && compound.get("id")
                            .filter(t -> t instanceof Tag.IntTag)
                            .map(t -> ((Tag.IntTag) t).value())
                            .orElse(-1) == dimensionId) {
                return compound.getString("name").orElse("minecraft:overworld");
            }
        }
        return "minecraft:overworld";
    }

    /** 764／765の Join Game の中身（両バージョンで形式は同じ）。 */
    public record JoinGame765(int entityId, boolean hardcore, List<String> worldNames,
            int maxPlayers, int viewDistance, int simulationDistance, boolean reducedDebug,
            boolean enableRespawnScreen, boolean doLimitedCrafting, String worldType,
            String worldName, long hashedSeed, int gameMode, int previousGameMode,
            boolean isDebug, boolean isFlat, boolean hasDeath, String deathDimension,
            long deathLocation, int portalCooldown) {
    }

    /**
     * 764／765の Join Game（0x29）を読む。順番は entityId, hardcore,
     * worldNames, maxPlayers, viewDistance, simulationDistance,
     * reducedDebug, enableRespawnScreen, doLimitedCrafting, worldType,
     * worldName, hashedSeed, gameMode, previousGameMode, isDebug, isFlat,
     * death, portalCooldown である。
     */
    public static JoinGame765 parseJoinGame765(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        List<String> worldNames = PacketIO.readStringList(reader);
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        int simulationDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean doLimitedCrafting = reader.readBoolean();
        String worldType = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        int portalCooldown = reader.readVarInt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Join Game に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new JoinGame765(entityId, hardcore, worldNames, maxPlayers, viewDistance,
                simulationDistance, reducedDebug, enableRespawnScreen, doLimitedCrafting,
                worldType, worldName, hashedSeed, gameMode, previousGameMode, isDebug,
                isFlat, hasDeath, deathDimension, deathLocation, portalCooldown);
    }

    /**
     * 764／765の Join Game を766形式（0x2B）に変換する。
     * dimension は文字列（worldType）からレジストリ番号（VarInt）になる
     * ため、{@code registries} の dimension_type から番号を引く。
     * 見つからなければ 0（overworld）にする。enforcesSecureChat は
     * false で補う（内部764／765はチャット署名の強制をしない）。
     */
    public static byte[] translateJoinGame765To766(JoinGame765 join,
            Map<String, Tag.CompoundTag> registries) {
        int dimensionId = dimensionIdFor(join.worldType(), registries);
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.doLimitedCrafting());
        writer.writeVarInt(dimensionId);
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        writer.writeVarInt(join.portalCooldown());
        writer.writeBoolean(false); // enforcesSecureChat
        return writer.bytes();
    }

    /** 764／765の Join Game を766形式にする（{@link #translateJoinGame765To766} と同じ）。 */
    public static byte[] translateJoinGame764To766(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateJoinGame765To766(parseJoinGame765(payload), registries);
    }


    /**
     * 764／765の Join Game を767形式にする。767の Join Game は766と
     * 同一形式のため {@link #translateJoinGame765To766} と同じ。
     */
    public static byte[] translateJoinGame765To767(JoinGame765 join,
            Map<String, Tag.CompoundTag> registries) {
        return translateJoinGame765To766(join, registries);
    }

    /** 764／765の Join Game を767形式にする（{@link #translateJoinGame765To767} と同じ）。 */
    public static byte[] translateJoinGame764To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateJoinGame765To766(parseJoinGame765(payload), registries);
    }

    /**
     * 766の Join Game を767形式にする。Join Game（dimension の番号を
     * 含む SpawnInfo を持つ）は766と767で同一形式のため、そのまま返す
     * （dimension_type・biome のレジストリも766と767で同一）。
     */
    public static byte[] translateJoinGame766To767(byte[] payload) {
        return payload;
    }

    /**
     * 767の Join Game を766形式にする。{@link #translateJoinGame766To767}
     * と同じく同一形式のため、そのまま返す。
     */
    public static byte[] translateJoinGame767To766(byte[] payload) {
        return payload;
    }

    /**
     * 763の Join Game を764／765形式（0x29）に変換する。
     * doLimitedCrafting は763には無いため false で補う。
     * codec は要らない（764／765の Join Game は codec を持たない）。
     */
    public static byte[] translateJoinGame763To764(JoinGame763 join) {
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(false); // doLimitedCrafting（763には無い）
        writer.writeString(join.worldType());
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        writer.writeVarInt(join.portalCooldown());
        return writer.bytes();
    }

    /** 763の Join Game を765形式にする（{@link #translateJoinGame763To764} と同じ）。 */
    public static byte[] translateJoinGame763To765(JoinGame763 join) {
        return translateJoinGame763To764(join);
    }


    /**
     * 762の Join Game（0x28）を読む。762の順番は763と同じだが、末尾の
     * portalCooldown が無い。portalCooldown は 0 として {@link JoinGame763} に入れる。
     */
    public static JoinGame763 parseJoinGame762(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        int gameMode = reader.readByte();
        int previousGameMode = reader.readByte();
        List<String> worldNames = PacketIO.readStringList(reader);
        Tag.CompoundTag codec = (Tag.CompoundTag) reader.readNamedNbt().tag();
        String worldType = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        int simulationDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Join Game に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new JoinGame763(entityId, hardcore, gameMode, previousGameMode,
                worldNames, codec, worldType, worldName, hashedSeed, maxPlayers,
                viewDistance, simulationDistance, reducedDebug, enableRespawnScreen,
                isDebug, isFlat, hasDeath, deathDimension, deathLocation, 0);
    }

    /**
     * 763の Join Game を762形式にする。フィールドの順番は763と同じで、
     * 末尾の portalCooldown だけ書かない。codec（レジストリ）は
     * 762・763 とも Join Game で送るため、そのまま写す。
     * 762クライアントに 763以上のサーバーの codec（trim・damage_type の
     * 追加フィールド、cherry_grove 入りのバイオーム一覧など）が届く点は、
     * 763クライアントへの素通しと同じ扱いで、本物のクライアントでの
     * 確認は未実施である。
     */
    public static byte[] translateJoinGame763To762(JoinGame763 join) {
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeNamedNbt("", join.codec());
        writer.writeString(join.worldType());
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        return writer.bytes();
    }

    /**
     * 762の Join Game を763形式にする。762には無い末尾の portalCooldown
     * に 0 を補う。codec（レジストリ）はそのまま写す。
     */
    public static byte[] translateJoinGame762To763(byte[] payload) {
        JoinGame763 join = parseJoinGame762(payload);
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeNamedNbt("", join.codec());
        writer.writeString(join.worldType());
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        writer.writeVarInt(join.portalCooldown());
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // 762 ⇔ 761（1.19.4 ⇔ 1.19.3）
    // Join Game は 761・762 ともに同じ構造（末尾の portalCooldown が無い）
    // のため、762形式のまま通す（parseJoinGame762 は 761 の Join Game も
    // 読める）。Respawn／Advancements／Death／End Combat も構造が同一のため
    // 変換しない。チャットは両方とも署名付きで同一形式のため変換しない。
    // 構造が違うのは Synchronize Player Position（761 にだけ末尾の
    // dismountVehicle がある）と Server Data（761 は motd／icon が
    // [option, string]、762 は motd が string・icon が [option, buffer]）
    // の2つだけ。パケットIDは呼び出し側（ProxySession）が付け替える。
    // ------------------------------------------------------------------

    /**
     * Synchronize Player Position（762 0x3C → 761 0x38）。
     * 761 にだけ末尾の dismountVehicle（bool）があるため false を足す。
     */
    public static byte[] translatePosition762To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            byte flags = reader.readByte();
            int teleportId = reader.readVarInt();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeByte(flags);
            writer.writeVarInt(teleportId);
            writer.writeBoolean(false); // dismountVehicle（761のみ）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Synchronize Player Position を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /**
     * Synchronize Player Position（761 0x38 → 762 0x3C）。
     * 末尾の dismountVehicle を読んで落とす。
     */
    public static byte[] translatePosition761To762(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            byte flags = reader.readByte();
            int teleportId = reader.readVarInt();
            reader.readBoolean(); // dismountVehicle（761のみ）
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeByte(flags);
            writer.writeVarInt(teleportId);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Synchronize Player Position を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /**
     * Server Data（762 0x45 → 761 0x41）。
     * 762 は motd が string、icon が [option, buffer]（生の PNG バイト）だが、
     * 761 は motd・icon ともに [option, string]（icon は data URL 文字列）のため
     * 書き直す。motd は常に有るとして true を書く。
     */
    public static byte[] translateServerData762To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String motd = reader.readString();
            boolean hasIcon = reader.readBoolean();
            byte[] icon = hasIcon ? reader.readByteArray() : null;
            boolean enforcesSecureChat = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(true); // motd あり
            writer.writeString(motd);
            writer.writeBoolean(hasIcon);
            if (hasIcon) {
                writer.writeString("data:image/png;base64,"
                        + java.util.Base64.getEncoder().encodeToString(icon));
            }
            writer.writeBoolean(enforcesSecureChat);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Server Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Server Data（761 0x41 → 762 0x45）。
     * 761 の motd／icon（[option, string]）を 762 の motd（string）／
     * iconBytes（[option, buffer]）に書き直す。icon 文字列は
     * "data:image/png;base64,..." の data URL として Base64 デコードする。
     * デコードできない場合は icon 無しとして扱う。motd が無い場合は空文字にする。
     */
    public static byte[] translateServerData761To762(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean hasMotd = reader.readBoolean();
            String motd = hasMotd ? reader.readString() : "";
            boolean hasIcon = reader.readBoolean();
            String iconString = hasIcon ? reader.readString() : null;
            boolean enforcesSecureChat = reader.readBoolean();
            requireEnd(reader);
            byte[] icon = null;
            if (hasIcon && iconString != null) {
                String base64 = iconString.startsWith("data:image/png;base64,")
                        ? iconString.substring("data:image/png;base64,".length())
                        : iconString;
                try {
                    icon = java.util.Base64.getDecoder().decode(base64);
                } catch (IllegalArgumentException e) {
                    LOG.warning("Server Data の icon を Base64 デコードできなかったため icon 無しにします。");
                    icon = null;
                }
            }
            Writer writer = new Writer();
            writer.writeString(motd);
            writer.writeBoolean(icon != null);
            if (icon != null) {
                writer.writeByteArray(icon);
            }
            writer.writeBoolean(enforcesSecureChat);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Server Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // 760 ⇔ 759（1.19.1／1.19.2 ⇔ 1.19）
    // 署名付きチャットは 759（1.19）で導入された。759 の形式は初代の
    // インライン署名形式（Player Chat は signedChatContent＋署名バッファ、
    // Chat Message／Chat Command は signature＋signedPreview）で、760 は
    // 連鎖ハッシュ＋Message Header 付きの別形式（previousSignature、
    // formattedMessage、UUID 付き previousMessages、lastRejectedMessage）。
    // 依頼文の「署名付きチャットは760で導入された」は誤り。759が初出。
    // System Chat は 759 が type varint（0=chat、1=system、2=game_info）、
    // 760 が isActionBar bool。Server Data は 760 にだけ
    // enforcesSecureChat がある。Login Start は 760 にだけ playerUUID
    // option がある。Player Info は 759・760 ともに旧式の action VarInt
    // のため変換は要らない。Trade List の件数は 759 が u8、760 が varint
    // （ChunkTranslator 側で変換する）。Multi Block Change の bool は
    // 759 が notTrustEdges、760 が suppressLightUpdates で同じ位置の
    // 1バイトのためそのまま写す。Respawn の gamemode は u8（759）と
    // i8（760）でワイヤ上は同一のため変換は要らない。
    // 759→760 では previousMessages は空で送る（760 の形式は送信者UUIDを
    // 要するが、759 には previousMessages が無いため）。Message Header は
    // 760→759 では落とす（759 には無い）。署名の検証はしない（そのまま通す）。
    // パケットIDは呼び出し側（ProxySession）が付け替える。
    // ------------------------------------------------------------------

    /** Chat Message（759 0x05 → 760 0x05）。previousMessages・lastRejectedMessage を空で足す。 */
    public static byte[] translateChatMessage759To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            byte[] signature = reader.readByteArray();
            boolean signedPreview = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(message);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeByteArray(signature);
            writer.writeBoolean(signedPreview);
            writer.writeVarInt(0); // previousMessages（空。759 には無い）
            writer.writeBoolean(false); // lastRejectedMessage 無し
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Message を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Message（760 0x05 → 759 0x05）。previousMessages・lastRejectedMessage を落とす。 */
    public static byte[] translateChatMessage760To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            byte[] signature = reader.readByteArray();
            boolean signedPreview = reader.readBoolean();
            readPreviousMessages760(reader);
            readLastRejected760(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(message);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeByteArray(signature);
            writer.writeBoolean(signedPreview);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Message を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Command（759 0x04 → 760 0x04）。引数署名はそのまま通す。previousMessages・lastRejectedMessage を空で足す。 */
    public static byte[] translateChatCommand759To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            int argCount = reader.readVarInt();
            if (argCount < 0) {
                throw new IllegalArgumentException("Chat Command の引数の件数が負です: " + argCount);
            }
            List<String> argNames = new ArrayList<>(argCount);
            List<byte[]> argSigs = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                argNames.add(reader.readString());
                argSigs.add(reader.readByteArray());
            }
            boolean signedPreview = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(command);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeVarInt(argNames.size());
            for (int i = 0; i < argNames.size(); i++) {
                writer.writeString(argNames.get(i));
                writer.writeByteArray(argSigs.get(i));
            }
            writer.writeBoolean(signedPreview);
            writer.writeVarInt(0); // previousMessages（空。759 には無い）
            writer.writeBoolean(false); // lastRejectedMessage 無し
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Command を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Command（760 0x04 → 759 0x04）。previousMessages・lastRejectedMessage を落とす。 */
    public static byte[] translateChatCommand760To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            int argCount = reader.readVarInt();
            if (argCount < 0) {
                throw new IllegalArgumentException("Chat Command の引数の件数が負です: " + argCount);
            }
            List<String> argNames = new ArrayList<>(argCount);
            List<byte[]> argSigs = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                argNames.add(reader.readString());
                argSigs.add(reader.readByteArray());
            }
            boolean signedPreview = reader.readBoolean();
            readPreviousMessages760(reader);
            readLastRejected760(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(command);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeVarInt(argNames.size());
            for (int i = 0; i < argNames.size(); i++) {
                writer.writeString(argNames.get(i));
                writer.writeByteArray(argSigs.get(i));
            }
            writer.writeBoolean(signedPreview);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Command を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Player Chat（759 → 760）。
     * previousSignature は無し、formattedMessage は無し、previousMessages は
     * 空で送る。filterType は 0（無し）。senderName→networkName、
     * senderTeam→networkTargetName。Message Header は合成しない。
     */
    public static byte[] translatePlayerChat759To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String signedChatContent = reader.readString();
            boolean hasUnsigned = reader.readBoolean();
            String unsigned = hasUnsigned ? reader.readString() : null;
            int type = reader.readVarInt();
            UUID sender = reader.readUuid();
            String senderName = reader.readString();
            boolean hasTeam = reader.readBoolean();
            String senderTeam = hasTeam ? reader.readString() : null;
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            byte[] signature = reader.readByteArray();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(false); // previousSignature 無し
            writer.writeUuid(sender);
            writer.writeByteArray(signature);
            writer.writeString(signedChatContent);
            writer.writeBoolean(false); // formattedMessage 無し
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeVarInt(0); // previousMessages（空。759 には無い）
            writer.writeBoolean(hasUnsigned);
            if (hasUnsigned) {
                writer.writeString(unsigned);
            }
            writer.writeVarInt(0); // filterType（無し）
            writer.writeVarInt(type);
            writer.writeString(senderName);
            writer.writeBoolean(hasTeam);
            if (hasTeam) {
                writer.writeString(senderTeam);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Player Chat（760 → 759）。
     * previousSignature／formattedMessage／previousMessages／filter は落とす。
     * networkName→senderName、networkTargetName→senderTeam。
     */
    public static byte[] translatePlayerChat760To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            if (reader.readBoolean()) {
                reader.readByteArray(); // previousSignature（759 には無い）
            }
            UUID sender = reader.readUuid();
            byte[] signature = reader.readByteArray();
            String plainMessage = reader.readString();
            if (reader.readBoolean()) {
                reader.readString(); // formattedMessage（759 には無い）
            }
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            readPreviousMessages760(reader);
            boolean hasUnsigned = reader.readBoolean();
            String unsigned = hasUnsigned ? reader.readString() : null;
            int filterType = reader.readVarInt();
            if (filterType == 2) {
                int maskCount = reader.readVarInt();
                for (int i = 0; i < maskCount; i++) {
                    reader.readLong();
                }
            }
            int type = reader.readVarInt();
            String networkName = reader.readString();
            boolean hasTarget = reader.readBoolean();
            String target = hasTarget ? reader.readString() : null;
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(plainMessage);
            writer.writeBoolean(hasUnsigned);
            if (hasUnsigned) {
                writer.writeString(unsigned);
            }
            writer.writeVarInt(type);
            writer.writeUuid(sender);
            writer.writeString(networkName);
            writer.writeBoolean(hasTarget);
            if (hasTarget) {
                writer.writeString(target);
            }
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeByteArray(signature);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * System Chat（759 → 760）。type varint（0=chat、1=system、2=game_info）を
     * isActionBar bool に直す（2 だけ true）。
     */
    public static byte[] translateSystemChat759To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String content = reader.readString();
            int type = reader.readVarInt();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(content);
            writer.writeBoolean(type == 2);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("System Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * System Chat（760 → 759）。isActionBar bool を type varint に直す
     * （true→2 game_info、false→1 system）。
     */
    public static byte[] translateSystemChat760To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String content = reader.readString();
            boolean isActionBar = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(content);
            writer.writeVarInt(isActionBar ? 2 : 1);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("System Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Server Data（759 → 760）。enforcesSecureChat を false で足す。 */
    public static byte[] translateServerData759To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean hasMotd = reader.readBoolean();
            String motd = hasMotd ? reader.readString() : null;
            boolean hasIcon = reader.readBoolean();
            String icon = hasIcon ? reader.readString() : null;
            boolean previewsChat = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(hasMotd);
            if (hasMotd) {
                writer.writeString(motd);
            }
            writer.writeBoolean(hasIcon);
            if (hasIcon) {
                writer.writeString(icon);
            }
            writer.writeBoolean(previewsChat);
            writer.writeBoolean(false); // enforcesSecureChat（759 には無い）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Server Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Server Data（760 → 759）。enforcesSecureChat を落とす。 */
    public static byte[] translateServerData760To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean hasMotd = reader.readBoolean();
            String motd = hasMotd ? reader.readString() : null;
            boolean hasIcon = reader.readBoolean();
            String icon = hasIcon ? reader.readString() : null;
            boolean previewsChat = reader.readBoolean();
            reader.readBoolean(); // enforcesSecureChat（759 には無い）
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(hasMotd);
            if (hasMotd) {
                writer.writeString(motd);
            }
            writer.writeBoolean(hasIcon);
            if (hasIcon) {
                writer.writeString(icon);
            }
            writer.writeBoolean(previewsChat);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Server Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * 759の Login Start（名前＋署名データ option）を760形式
     * （名前＋署名データ option＋playerUUID 無し）に変換する。
     * 署名データはそのまま通す（760→765では落とす）。
     */
    public static byte[] translateLoginStart759To760(byte[] payload) {
        Reader reader = new Reader(payload);
        String username = reader.readString();
        boolean hasSignature = reader.readBoolean();
        long timestamp = 0;
        byte[] publicKey = null;
        byte[] signature = null;
        if (hasSignature) {
            timestamp = reader.readLong();
            publicKey = reader.readByteArray();
            signature = reader.readByteArray();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(username);
        writer.writeBoolean(hasSignature);
        if (hasSignature) {
            writer.writeLong(timestamp);
            writer.writeByteArray(publicKey);
            writer.writeByteArray(signature);
        }
        writer.writeBoolean(false); // playerUUID 無し
        return writer.bytes();
    }

    /**
     * 760の Login Start（名前＋署名データ option＋playerUUID option）を759形式
     * （名前＋署名データ option）に変換する。playerUUID は落とす。
     */
    public static byte[] translateLoginStart760To759(byte[] payload) {
        Reader reader = new Reader(payload);
        String username = reader.readString();
        boolean hasSignature = reader.readBoolean();
        long timestamp = 0;
        byte[] publicKey = null;
        byte[] signature = null;
        if (hasSignature) {
            timestamp = reader.readLong();
            publicKey = reader.readByteArray();
            signature = reader.readByteArray();
        }
        if (reader.readBoolean()) { // playerUUID option（759 には無い）
            reader.readUuid();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(username);
        writer.writeBoolean(hasSignature);
        if (hasSignature) {
            writer.writeLong(timestamp);
            writer.writeByteArray(publicKey);
            writer.writeByteArray(signature);
        }
        return writer.bytes();
    }

    /**
     * 759の Login Start を765形式（名前＋UUID）に変換する。
     * 759→760へ直してから760→765へ直す。
     */
    public static byte[] translateLoginStart759To765(byte[] payload) {
        return translateLoginStart760To765(translateLoginStart759To760(payload));
    }

    /**
     * 759の Login Start を763形式（名前＋bool＋UUID）に変換する。
     * 759→765へ直してから765→763へ直す。
     */
    public static byte[] translateLoginStart759To763(byte[] payload) {
        return translateLoginStart765To763(translateLoginStart759To765(payload));
    }

    /**
     * 765形式の Login Start（名前＋UUID）を759形式
     * （名前＋署名データ無し）に変換する。online-mode の認証済み
     * Login Start を内部759サーバーへ渡すときに使う。
     */
    public static byte[] translateLoginStart765To759(byte[] payload) {
        return translateLoginStart760To759(translateLoginStart765To760(payload));
    }

    // ------------------------------------------------------------------
    // 761 ⇔ 760（1.19.3 ⇔ 1.19.1／1.19.2）
    // チャットは署名付きだが形式が違う。760 は 1.19.2 までの旧形式
    // （Message Header＋Player Chat の分割、previousSignature、
    // formattedMessage、UUID 付き previousMessages、Chat Command／
    // Chat Message の signedPreview＋lastRejectedMessage）で、761 は
    // 1.19.3 の新形式（index＋option 256バイト署名、id 付き
    // previousMessages、messageCount＋acknowledged、Chat Session
    // Update）。Player Info も 760 は旧式の action VarInt、761 は
    // bitflags。Server Data は 760 にだけ previewsChat がある。
    // Sound Effect は 761 が Holder、760 が音IDの VarInt のため
    // ChunkTranslator 側で変換する（translateSoundEffect761To760 等）。
    // 署名の検証はしない（そのまま通す）。previousMessages の
    // 760→761 変換では署名が 256バイトのものだけ id=0 として残し、
    // それ以外は落とす。761→760 では previousMessages は空で送る
    // （760 の形式は送信者UUIDを要するが、761 の id 付き形式からは
    // 復元できないため）。Message Header は 761 には無いため落とす
    // （761→760 では合成しない。760 クライアントには Player Chat
    // だけを送る）。パケットIDは呼び出し側（ProxySession）が付け替える。
    // ------------------------------------------------------------------

    /** 760 の Chat Message／Chat Command の previousMessages 1件（送信者UUID＋署名）。 */
    private record PreviousMessage760(UUID sender, byte[] signature) {
    }

    /** 760 の Chat Message／Chat Command の lastRejectedMessage（送信者UUID＋署名）。 */
    private record LastRejected760(UUID sender, byte[] signature) {
    }

    /** Chat Message（760 0x05 → 761 0x05）。 */
    public static byte[] translateChatMessage760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            byte[] signature = reader.readByteArray();
            boolean signedPreview = reader.readBoolean();
            List<PreviousMessage760> previous = readPreviousMessages760(reader);
            LastRejected760 lastRejected = readLastRejected760(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(message);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeBoolean(signature.length == 256);
            if (signature.length == 256) {
                writer.writeBytes(signature);
            }
            writer.writeVarInt(previous.size()); // offset
            writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged（合成。0 は「未確認」）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Message を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Message（761 0x05 → 760 0x05）。 */
    public static byte[] translateChatMessage761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            boolean hasSignature = reader.readBoolean();
            byte[] signature = hasSignature ? reader.readBytes(256) : null;
            reader.readVarInt(); // offset（760 には無い）
            reader.readBytes(3); // acknowledged（760 には無い）
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(message);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeByteArray(signature == null ? new byte[0] : signature);
            writer.writeBoolean(false); // signedPreview（760 では使わない）
            writer.writeVarInt(0); // previousMessages（空。761 の id 付き形式からは復元できない）
            writer.writeBoolean(false); // lastRejectedMessage 無し
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Message を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Command（760 0x04 → 761 0x04）。引数署名は 256バイトのものだけ残す。 */
    public static byte[] translateChatCommand760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            int argCount = reader.readVarInt();
            List<String> argNames = new ArrayList<>(argCount);
            List<byte[]> argSigs = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                String name = reader.readString();
                byte[] sig = reader.readByteArray();
                if (sig.length == 256) {
                    argNames.add(name);
                    argSigs.add(sig);
                }
            }
            boolean signedPreview = reader.readBoolean();
            List<PreviousMessage760> previous = readPreviousMessages760(reader);
            LastRejected760 lastRejected = readLastRejected760(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(command);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeVarInt(argNames.size());
            for (int i = 0; i < argNames.size(); i++) {
                writer.writeString(argNames.get(i));
                writer.writeBytes(argSigs.get(i));
            }
            writer.writeVarInt(previous.size()); // messageCount
            writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged（合成。0 は「未確認」）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Command を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Command（761 0x04 → 760 0x04）。 */
    public static byte[] translateChatCommand761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            int argCount = reader.readVarInt();
            List<String> argNames = new ArrayList<>(argCount);
            List<byte[]> argSigs = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                argNames.add(reader.readString());
                argSigs.add(reader.readBytes(256));
            }
            reader.readVarInt(); // messageCount（760 には無い）
            reader.readBytes(3); // acknowledged（760 には無い）
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(command);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeVarInt(argNames.size());
            for (int i = 0; i < argNames.size(); i++) {
                writer.writeString(argNames.get(i));
                writer.writeByteArray(argSigs.get(i));
            }
            writer.writeBoolean(false); // signedPreview（760 では使わない）
            writer.writeVarInt(0); // previousMessages（空。761 の id 付き形式からは復元できない）
            writer.writeBoolean(false); // lastRejectedMessage 無し
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Command を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** 760 の previousMessages（送信者UUID＋署名の配列）を読む。 */
    private static List<PreviousMessage760> readPreviousMessages760(Reader reader) {
        int count = reader.readVarInt();
        if (count < 0) {
            throw new IllegalArgumentException("previousMessages の件数が負です: " + count);
        }
        List<PreviousMessage760> previous = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID sender = reader.readUuid();
            byte[] signature = reader.readByteArray();
            previous.add(new PreviousMessage760(sender, signature));
        }
        return previous;
    }

    /** 760 の lastRejectedMessage（option）を読む。無ければ null。 */
    private static LastRejected760 readLastRejected760(Reader reader) {
        if (!reader.readBoolean()) {
            return null;
        }
        UUID sender = reader.readUuid();
        byte[] signature = reader.readByteArray();
        return new LastRejected760(sender, signature);
    }

    /** Message Acknowledgement（760 0x03 → 761 0x03）。件数だけ残す。 */
    public static byte[] translateMessageAcknowledgement760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            List<PreviousMessage760> previous = readPreviousMessages760(reader);
            readLastRejected760(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(previous.size());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Message Acknowledgement を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /** Message Acknowledgement（761 0x03 → 760 0x03）。previousMessages は空で送る。 */
    public static byte[] translateMessageAcknowledgement761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            reader.readVarInt(); // count（760 では previousMessages の中身は復元できない）
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(0); // previousMessages（空）
            writer.writeBoolean(false); // lastRejectedMessage 無し
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Message Acknowledgement を変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /**
     * Player Chat（761 0x31 → 760 0x33）。
     * previousSignature は無し、formattedMessage は無し、previousMessages は
     * 空で送る。署名が無い場合は空のバッファを書く（760 の署名は必須項目）。
     * Message Header は合成しない。
     */
    public static byte[] translatePlayerChat761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            UUID sender = reader.readUuid();
            reader.readVarInt(); // index（760 には無い）
            boolean hasSignature = reader.readBoolean();
            byte[] signature = hasSignature ? reader.readBytes(256) : null;
            String plainMessage = reader.readString();
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            int previousCount = reader.readVarInt();
            for (int i = 0; i < previousCount; i++) {
                int id = reader.readVarInt();
                if (id == 0) {
                    reader.readBytes(256);
                }
            }
            boolean hasUnsigned = reader.readBoolean();
            String unsigned = hasUnsigned ? reader.readString() : null;
            int filterType = reader.readVarInt();
            List<Long> filterMask = null;
            if (filterType == 2) {
                int maskCount = reader.readVarInt();
                filterMask = new ArrayList<>(maskCount);
                for (int i = 0; i < maskCount; i++) {
                    filterMask.add(reader.readLong());
                }
            }
            int chatType = reader.readVarInt();
            String networkName = reader.readString();
            boolean hasTarget = reader.readBoolean();
            String target = hasTarget ? reader.readString() : null;
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(false); // previousSignature 無し
            writer.writeUuid(sender);
            writer.writeByteArray(signature == null ? new byte[0] : signature);
            writer.writeString(plainMessage);
            writer.writeBoolean(false); // formattedMessage 無し
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            writer.writeVarInt(0); // previousMessages（空。761 の id 付き形式からは復元できない）
            writer.writeBoolean(hasUnsigned);
            if (hasUnsigned) {
                writer.writeString(unsigned);
            }
            writer.writeVarInt(filterType);
            if (filterType == 2) {
                writer.writeVarInt(filterMask.size());
                for (long value : filterMask) {
                    writer.writeLong(value);
                }
            }
            writer.writeVarInt(chatType);
            writer.writeString(networkName);
            writer.writeBoolean(hasTarget);
            if (hasTarget) {
                writer.writeString(target);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** 760の Player Chat から送信者UUIDを読む（読めなければ nil UUID）。 */
    public static UUID extractChatSender760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            if (reader.readBoolean()) {
                reader.readByteArray(); // previousSignature
            }
            return reader.readUuid();
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    /**
     * Player Chat（760 0x33 → 761 0x31）。
     * index は呼び出し側が送信者ごとに数えたものを渡す（760 には無いため）。
     * previousSignature／formattedMessage は落とす。previousMessages は
     * 署名が 256バイトのものだけ id=0 として残す。
     */
    public static byte[] translatePlayerChat760To761(byte[] payload, int index) {
        try {
            Reader reader = new Reader(payload);
            if (reader.readBoolean()) {
                reader.readByteArray(); // previousSignature（761 には無い）
            }
            UUID sender = reader.readUuid();
            byte[] signature = reader.readByteArray();
            String plainMessage = reader.readString();
            if (reader.readBoolean()) {
                reader.readString(); // formattedMessage（761 には無い）
            }
            long timestamp = reader.readLong();
            long salt = reader.readLong();
            List<PreviousMessage760> previous = readPreviousMessages760(reader);
            boolean hasUnsigned = reader.readBoolean();
            String unsigned = hasUnsigned ? reader.readString() : null;
            int filterType = reader.readVarInt();
            List<Long> filterMask = null;
            if (filterType == 2) {
                int maskCount = reader.readVarInt();
                filterMask = new ArrayList<>(maskCount);
                for (int i = 0; i < maskCount; i++) {
                    filterMask.add(reader.readLong());
                }
            }
            int chatType = reader.readVarInt();
            String networkName = reader.readString();
            boolean hasTarget = reader.readBoolean();
            String target = hasTarget ? reader.readString() : null;
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeUuid(sender);
            writer.writeVarInt(index);
            writer.writeBoolean(signature.length == 256);
            if (signature.length == 256) {
                writer.writeBytes(signature);
            }
            writer.writeString(plainMessage);
            writer.writeLong(timestamp);
            writer.writeLong(salt);
            int kept = 0;
            for (PreviousMessage760 entry : previous) {
                if (entry.signature().length == 256) {
                    kept++;
                }
            }
            writer.writeVarInt(kept);
            for (PreviousMessage760 entry : previous) {
                if (entry.signature().length == 256) {
                    writer.writeVarInt(0);
                    writer.writeBytes(entry.signature());
                }
            }
            writer.writeBoolean(hasUnsigned);
            if (hasUnsigned) {
                writer.writeString(unsigned);
            }
            writer.writeVarInt(filterType);
            if (filterType == 2) {
                writer.writeVarInt(filterMask.size());
                for (long value : filterMask) {
                    writer.writeLong(value);
                }
            }
            writer.writeVarInt(chatType);
            writer.writeString(networkName);
            writer.writeBoolean(hasTarget);
            if (hasTarget) {
                writer.writeString(target);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** 761 の Player Info のプロパティ1件（名前・値・署名）。署名は無ければ null。 */
    private record PlayerInfoProperty(String name, String value, String signature) {
    }

    /** 761 の Player Info の chatSession（UUID・有効期限・公開鍵・署名）。無ければ null。 */
    private record PlayerInfoChatSession(UUID uuid, long expireTime, byte[] keyBytes,
            byte[] keySignature) {
    }

    /** 761 の Player Info のエントリ1件（読み取った全フィールド）。 */
    private record PlayerInfoEntry761(UUID uuid, String name,
            List<PlayerInfoProperty> properties, Integer gamemode, Integer listed,
            Integer latency, String displayName, boolean hasDisplayName,
            PlayerInfoChatSession chatSession) {
    }

    /**
     * Player Info Update（761 0x36 → 760 0x37）。
     * 761 は1パケットに複数の action（bitflags）を持てるが、760 は1パケットに
     * 1つの action（VarInt）しか持てないため、action ごとに760のパケットへ
     * 分割して返す。update_listed は760に無いため落とす。initialize_chat
     * だけの更新（add_player を伴わないもの）は760では表せないため落とす。
     * add_player と initialize_chat が同時の場合は crypto として入れる。
     */
    public static List<byte[]> translatePlayerInfo761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readUnsignedByte();
            int count = reader.readVarInt();
            List<PlayerInfoEntry761> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID uuid = reader.readUuid();
                String name = null;
                List<PlayerInfoProperty> properties = List.of();
                if ((action & 0x01) != 0) {
                    name = reader.readString();
                    int propertyCount = reader.readVarInt();
                    List<PlayerInfoProperty> props = new ArrayList<>(propertyCount);
                    for (int p = 0; p < propertyCount; p++) {
                        String propertyName = reader.readString();
                        String propertyValue = reader.readString();
                        String propertySignature = reader.readBoolean()
                                ? reader.readString() : null;
                        props.add(new PlayerInfoProperty(propertyName, propertyValue,
                                propertySignature));
                    }
                    properties = props;
                }
                PlayerInfoChatSession chatSession = null;
                if ((action & 0x02) != 0) {
                    if (reader.readBoolean()) {
                        UUID sessionUuid = reader.readUuid();
                        long expireTime = reader.readLong();
                        byte[] keyBytes = reader.readByteArray();
                        byte[] keySignature = reader.readByteArray();
                        chatSession = new PlayerInfoChatSession(sessionUuid, expireTime,
                                keyBytes, keySignature);
                    }
                }
                Integer gamemode = (action & 0x04) != 0 ? reader.readVarInt() : null;
                Integer listed = (action & 0x08) != 0 ? reader.readVarInt() : null;
                Integer latency = (action & 0x10) != 0 ? reader.readVarInt() : null;
                String displayName = null;
                boolean hasDisplayName = false;
                if ((action & 0x20) != 0) {
                    hasDisplayName = reader.readBoolean();
                    displayName = hasDisplayName ? reader.readString() : null;
                }
                entries.add(new PlayerInfoEntry761(uuid, name, properties, gamemode, listed,
                        latency, displayName, hasDisplayName, chatSession));
            }
            requireEnd(reader);
            List<byte[]> out = new ArrayList<>();
            if ((action & 0x01) != 0) {
                Writer writer = new Writer();
                writer.writeVarInt(0); // add_player
                writer.writeVarInt(entries.size());
                for (PlayerInfoEntry761 entry : entries) {
                    writer.writeUuid(entry.uuid());
                    writer.writeString(entry.name());
                    writer.writeVarInt(entry.properties().size());
                    for (PlayerInfoProperty property : entry.properties()) {
                        writer.writeString(property.name());
                        writer.writeString(property.value());
                        writer.writeBoolean(property.signature() != null);
                        if (property.signature() != null) {
                            writer.writeString(property.signature());
                        }
                    }
                    writer.writeVarInt(entry.gamemode() == null ? 0 : entry.gamemode());
                    writer.writeVarInt(entry.latency() == null ? 0 : entry.latency());
                    writer.writeBoolean(entry.hasDisplayName());
                    if (entry.hasDisplayName()) {
                        writer.writeString(entry.displayName());
                    }
                    writer.writeBoolean(entry.chatSession() != null);
                    if (entry.chatSession() != null) {
                        writer.writeLong(entry.chatSession().expireTime());
                        writer.writeByteArray(entry.chatSession().keyBytes());
                        writer.writeByteArray(entry.chatSession().keySignature());
                    }
                }
                out.add(writer.bytes());
            }
            if ((action & 0x04) != 0) {
                Writer writer = new Writer();
                writer.writeVarInt(1); // update_game_mode
                writer.writeVarInt(entries.size());
                for (PlayerInfoEntry761 entry : entries) {
                    writer.writeUuid(entry.uuid());
                    writer.writeVarInt(entry.gamemode() == null ? 0 : entry.gamemode());
                }
                out.add(writer.bytes());
            }
            if ((action & 0x10) != 0) {
                Writer writer = new Writer();
                writer.writeVarInt(2); // update_latency
                writer.writeVarInt(entries.size());
                for (PlayerInfoEntry761 entry : entries) {
                    writer.writeUuid(entry.uuid());
                    writer.writeVarInt(entry.latency() == null ? 0 : entry.latency());
                }
                out.add(writer.bytes());
            }
            if ((action & 0x20) != 0) {
                Writer writer = new Writer();
                writer.writeVarInt(3); // update_display_name
                writer.writeVarInt(entries.size());
                for (PlayerInfoEntry761 entry : entries) {
                    writer.writeUuid(entry.uuid());
                    writer.writeBoolean(entry.hasDisplayName());
                    if (entry.hasDisplayName()) {
                        writer.writeString(entry.displayName());
                    }
                }
                out.add(writer.bytes());
            }
            return out;
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Info を変換できなかったため素通しします: " + e.getMessage());
            return List.of(payload);
        }
    }

    /**
     * Player Info（760 0x37 → 761 0x36）。action が 0〜3 の場合だけ使う。
     * 760 は1パケットに1つの action しか持てないが、761 の add_player は
     * gamemode／ping／displayName を含まないため、action 0 は2パケットに
     * 分ける（add_player＋initialize_chat と、update_game_mode＋
     * update_latency＋update_display_name）。add_player の crypto があれば
     * initialize_chat も付け、chatSession の UUID はプレイヤーの UUID で
     * 代用する（760 の crypto には UUID が無い）。
     * action 4（remove_player）は別パケット（Player Info Remove）のため、
     * {@link #translatePlayerInfoRemove760To761(byte[])} を使うこと。
     */
    public static List<byte[]> translatePlayerInfo760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readVarInt();
            if (action < 0 || action > 3) {
                throw new IllegalArgumentException("Player Info の action が範囲外です: " + action);
            }
            int count = reader.readVarInt();
            List<byte[]> out = new ArrayList<>();
            if (action == 0) {
                // 1パケット目：add_player（＋crypto があれば initialize_chat）。
                // crypto の有無は全エントリを読んでから決まるため、一旦保持する。
                List<UUID> uuids = new ArrayList<>(count);
                List<String> names = new ArrayList<>(count);
                List<List<PlayerInfoProperty>> allProperties = new ArrayList<>(count);
                List<Integer> gamemodes = new ArrayList<>(count);
                List<Integer> pings = new ArrayList<>(count);
                List<String> displayNames = new ArrayList<>(count);
                List<Boolean> hasDisplayNames = new ArrayList<>(count);
                List<PlayerInfoChatSession> sessions = new ArrayList<>(count);
                boolean anyCrypto = false;
                for (int i = 0; i < count; i++) {
                    uuids.add(reader.readUuid());
                    names.add(reader.readString());
                    int propertyCount = reader.readVarInt();
                    List<PlayerInfoProperty> properties = new ArrayList<>(propertyCount);
                    for (int p = 0; p < propertyCount; p++) {
                        String propertyName = reader.readString();
                        String propertyValue = reader.readString();
                        String propertySignature = reader.readBoolean()
                                ? reader.readString() : null;
                        properties.add(new PlayerInfoProperty(propertyName, propertyValue,
                                propertySignature));
                    }
                    allProperties.add(properties);
                    gamemodes.add(reader.readVarInt());
                    pings.add(reader.readVarInt());
                    boolean hasDisplayName = reader.readBoolean();
                    hasDisplayNames.add(hasDisplayName);
                    displayNames.add(hasDisplayName ? reader.readString() : null);
                    if (reader.readBoolean()) {
                        long expireTime = reader.readLong();
                        byte[] keyBytes = reader.readByteArray();
                        byte[] keySignature = reader.readByteArray();
                        sessions.add(new PlayerInfoChatSession(uuids.get(i), expireTime,
                                keyBytes, keySignature));
                        anyCrypto = true;
                    } else {
                        sessions.add(null);
                    }
                }
                requireEnd(reader);
                Writer addPlayer = new Writer();
                addPlayer.writeUnsignedByte(anyCrypto ? 0x03 : 0x01);
                addPlayer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    addPlayer.writeUuid(uuids.get(i));
                    addPlayer.writeString(names.get(i));
                    addPlayer.writeVarInt(allProperties.get(i).size());
                    for (PlayerInfoProperty property : allProperties.get(i)) {
                        addPlayer.writeString(property.name());
                        addPlayer.writeString(property.value());
                        addPlayer.writeBoolean(property.signature() != null);
                        if (property.signature() != null) {
                            addPlayer.writeString(property.signature());
                        }
                    }
                    if (anyCrypto) {
                        PlayerInfoChatSession session = sessions.get(i);
                        addPlayer.writeBoolean(session != null);
                        if (session != null) {
                            addPlayer.writeUuid(session.uuid());
                            addPlayer.writeLong(session.expireTime());
                            addPlayer.writeByteArray(session.keyBytes());
                            addPlayer.writeByteArray(session.keySignature());
                        }
                    }
                }
                out.add(addPlayer.bytes());
                // 2パケット目：update_game_mode＋update_latency＋update_display_name。
                Writer updates = new Writer();
                updates.writeUnsignedByte(0x34);
                updates.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    updates.writeUuid(uuids.get(i));
                    updates.writeVarInt(gamemodes.get(i));
                    updates.writeVarInt(pings.get(i));
                    updates.writeBoolean(hasDisplayNames.get(i));
                    if (hasDisplayNames.get(i)) {
                        updates.writeString(displayNames.get(i));
                    }
                }
                out.add(updates.bytes());
                return out;
            }
            int flags = switch (action) {
                case 1 -> 0x04;
                case 2 -> 0x10;
                case 3 -> 0x20;
                default -> 0;
            };
            Writer writer = new Writer();
            writer.writeUnsignedByte(flags);
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeUuid(reader.readUuid());
                if (action == 1) {
                    writer.writeVarInt(reader.readVarInt()); // gamemode
                } else if (action == 2) {
                    writer.writeVarInt(reader.readVarInt()); // latency
                } else {
                    boolean hasDisplayName = reader.readBoolean();
                    writer.writeBoolean(hasDisplayName);
                    if (hasDisplayName) {
                        writer.writeString(reader.readString());
                    }
                }
            }
            requireEnd(reader);
            out.add(writer.bytes());
            return out;
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Info を変換できなかったため素通しします: " + e.getMessage());
            return List.of(payload);
        }
    }

    /**
     * Player Info（760 0x37、action 4 remove_player）→ Player Info Remove
     * （761 0x35）。UUID の配列だけを抜き出す。
     */
    public static byte[] translatePlayerInfoRemove760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readVarInt();
            if (action != 4) {
                throw new IllegalArgumentException(
                        "Player Info Remove の変換に action 4 以外が来ました: " + action);
            }
            int count = reader.readVarInt();
            List<UUID> uuids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                uuids.add(reader.readUuid());
            }
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(uuids.size());
            for (UUID uuid : uuids) {
                writer.writeUuid(uuid);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Info Remove を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Player Info Remove（761 0x35 → 760 0x37、action 4 remove_player）。
     * UUID の配列を 760 の Player Info（action 4）に包み直す。
     */
    public static byte[] translatePlayerInfoRemove761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int count = reader.readVarInt();
            List<UUID> uuids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                uuids.add(reader.readUuid());
            }
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(4); // remove_player
            writer.writeVarInt(uuids.size());
            for (UUID uuid : uuids) {
                writer.writeUuid(uuid);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Info Remove を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Server Data（761 0x41 → 760 0x42）。previewsChat は false を書く。 */
    public static byte[] translateServerData761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean hasMotd = reader.readBoolean();
            String motd = hasMotd ? reader.readString() : null;
            boolean hasIcon = reader.readBoolean();
            String icon = hasIcon ? reader.readString() : null;
            boolean enforcesSecureChat = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(hasMotd);
            if (hasMotd) {
                writer.writeString(motd);
            }
            writer.writeBoolean(hasIcon);
            if (hasIcon) {
                writer.writeString(icon);
            }
            writer.writeBoolean(false); // previewsChat（761 には無い）
            writer.writeBoolean(enforcesSecureChat);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Server Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Server Data（760 0x42 → 761 0x41）。previewsChat を読んで落とす。 */
    public static byte[] translateServerData760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean hasMotd = reader.readBoolean();
            String motd = hasMotd ? reader.readString() : null;
            boolean hasIcon = reader.readBoolean();
            String icon = hasIcon ? reader.readString() : null;
            reader.readBoolean(); // previewsChat（761 には無い）
            boolean enforcesSecureChat = reader.readBoolean();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(hasMotd);
            if (hasMotd) {
                writer.writeString(motd);
            }
            writer.writeBoolean(hasIcon);
            if (hasIcon) {
                writer.writeString(icon);
            }
            writer.writeBoolean(enforcesSecureChat);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Server Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }


    /**
     * 760の Login Start（名前＋署名データ option＋任意UUID）を765形式
     * （名前＋UUID）に変換する。署名データは落とす（プロキシは認証済みの
     * 名前・UUID で Login Start を作り直すため、内部へは届かない）。
     * UUID が無い場合はオフライン UUID を合成する。
     */
    public static byte[] translateLoginStart760To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String username = reader.readString();
        if (reader.readBoolean()) {
            reader.readLong(); // 署名データの timestamp
            reader.readByteArray(); // 公開鍵
            reader.readByteArray(); // 署名
        }
        UUID uuid;
        if (reader.readBoolean()) {
            uuid = reader.readUuid();
        } else {
            uuid = offlineUuid(username);
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(username);
        writer.writeUuid(uuid);
        return writer.bytes();
    }

    /**
     * 760の Login Start を763形式（名前＋bool＋UUID）に変換する。
     * 760→765へ直してから765→763へ直す。
     */
    public static byte[] translateLoginStart760To763(byte[] payload) {
        return translateLoginStart765To763(translateLoginStart760To765(payload));
    }

    /**
     * 765形式の Login Start（名前＋UUID）を760形式
     * （名前＋署名データ無し＋任意UUID）に変換する。online-mode の認証済み
     * Login Start を内部760サーバーへ渡すときに使う。
     */
    public static byte[] translateLoginStart765To760(byte[] payload) {
        Reader reader = new Reader(payload);
        String username = reader.readString();
        UUID uuid = reader.readUuid();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(username);
        writer.writeBoolean(false); // 署名データ無し
        writer.writeBoolean(true);
        writer.writeUuid(uuid);
        return writer.bytes();
    }

    /**
     * Explosion（761 → 760）。x／y／z が f64 から f32 になる（精度は落ちるが
     * 爆発の座標の値域では問題ない）。それ以外は同一。
     */
    public static byte[] translateExplosion761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float radius = reader.readFloat();
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Explosion の件数が負です: " + count);
            }
            List<int[]> offsets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                offsets.add(new int[]{reader.readByte(), reader.readByte(), reader.readByte()});
            }
            float motionX = reader.readFloat();
            float motionY = reader.readFloat();
            float motionZ = reader.readFloat();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeFloat((float) x);
            writer.writeFloat((float) y);
            writer.writeFloat((float) z);
            writer.writeFloat(radius);
            writer.writeVarInt(offsets.size());
            for (int[] offset : offsets) {
                writer.writeByte(offset[0]);
                writer.writeByte(offset[1]);
                writer.writeByte(offset[2]);
            }
            writer.writeFloat(motionX);
            writer.writeFloat(motionY);
            writer.writeFloat(motionZ);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Explosion（760 → 761）。x／y／z が f32 から f64 になる。 */
    public static byte[] translateExplosion760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            float x = reader.readFloat();
            float y = reader.readFloat();
            float z = reader.readFloat();
            float radius = reader.readFloat();
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Explosion の件数が負です: " + count);
            }
            List<int[]> offsets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                offsets.add(new int[]{reader.readByte(), reader.readByte(), reader.readByte()});
            }
            float motionX = reader.readFloat();
            float motionY = reader.readFloat();
            float motionZ = reader.readFloat();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(radius);
            writer.writeVarInt(offsets.size());
            for (int[] offset : offsets) {
                writer.writeByte(offset[0]);
                writer.writeByte(offset[1]);
                writer.writeByte(offset[2]);
            }
            writer.writeFloat(motionX);
            writer.writeFloat(motionY);
            writer.writeFloat(motionZ);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Hide Message（761 → 760）。id が 0（署名付き）の場合だけ変換できる。
     * id が 0 以外（過去のメッセージへの参照）は760では表せないため
     * {@code null} を返す（呼び出し側は落とす）。
     */
    public static byte[] translateHideMessage761To760(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int id = reader.readVarInt();
            if (id != 0) {
                return null;
            }
            byte[] signature = reader.readBytes(256);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeByteArray(signature);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Hide Message を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Hide Message（760 → 761）。署名が 256バイトの場合だけ変換できる。
     * それ以外は {@code null} を返す（呼び出し側は落とす）。
     */
    public static byte[] translateHideMessage760To761(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            byte[] signature = reader.readByteArray();
            requireEnd(reader);
            if (signature.length != 256) {
                return null;
            }
            Writer writer = new Writer();
            writer.writeVarInt(0);
            writer.writeBytes(signature);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Hide Message を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** 766の Join Game の中身。dimension はレジストリ番号（VarInt）である。 */
    public record JoinGame766(int entityId, boolean hardcore, List<String> worldNames,
            int maxPlayers, int viewDistance, int simulationDistance, boolean reducedDebug,
            boolean enableRespawnScreen, boolean doLimitedCrafting, int dimensionId,
            String worldName, long hashedSeed, int gameMode, int previousGameMode,
            boolean isDebug, boolean isFlat, boolean hasDeath, String deathDimension,
            long deathLocation, int portalCooldown, boolean enforcesSecureChat) {
    }

    /**
     * 766の Join Game（0x2B）を読む。順番は entityId, hardcore,
     * worldNames, maxPlayers, viewDistance, simulationDistance,
     * reducedDebug, enableRespawnScreen, doLimitedCrafting, SpawnInfo
     * （dimension, worldName, hashedSeed, gameMode, previousGameMode,
     * isDebug, isFlat, death, portalCooldown）, enforcesSecureChat である。
     */
    public static JoinGame766 parseJoinGame766(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        List<String> worldNames = PacketIO.readStringList(reader);
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        int simulationDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean doLimitedCrafting = reader.readBoolean();
        int dimensionId = reader.readVarInt();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        int portalCooldown = reader.readVarInt();
        boolean enforcesSecureChat = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Join Game に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return new JoinGame766(entityId, hardcore, worldNames, maxPlayers, viewDistance,
                simulationDistance, reducedDebug, enableRespawnScreen, doLimitedCrafting,
                dimensionId, worldName, hashedSeed, gameMode, previousGameMode, isDebug,
                isFlat, hasDeath, deathDimension, deathLocation, portalCooldown,
                enforcesSecureChat);
    }

    /**
     * 766の Join Game を764／765形式（0x29）に変換する。
     * dimension はレジストリ番号から文字列（worldType）に戻す。
     * enforcesSecureChat は764／765には無いため落とす
     * （内部764／765はチャット署名の強制をしない）。
     */
    public static byte[] translateJoinGame766To764(JoinGame766 join,
            Map<String, Tag.CompoundTag> registries) {
        String worldType = dimensionNameFor(join.dimensionId(), registries);
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.doLimitedCrafting());
        writer.writeString(worldType);
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        writer.writeVarInt(join.portalCooldown());
        return writer.bytes();
    }

    /** 766の Join Game を765形式にする（{@link #translateJoinGame766To764} と同じ）。 */
    public static byte[] translateJoinGame766To765(JoinGame766 join,
            Map<String, Tag.CompoundTag> registries) {
        return translateJoinGame766To764(join, registries);
    }

    /**
     * 766の Join Game を763形式（0x28）に変換する。
     * codec（dimension_type・biome）を名前付き NBT で埋め込む。
     * 763には無い doLimitedCrafting・enforcesSecureChat は落とす。
     */
    public static byte[] translateJoinGame766To763(JoinGame766 join,
            Map<String, Tag.CompoundTag> registries) {
        String worldType = dimensionNameFor(join.dimensionId(), registries);
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeNamedNbt("", buildJoinGameCodec(registries));
        writer.writeString(worldType);
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        writer.writeVarInt(join.portalCooldown());
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Respawn の766変換。766の Respawn は {SpawnInfo, copyMetadata(u8)}
    // で、SpawnInfo の dimension はレジストリ番号（VarInt）である。
    // 764／765の Respawn は dimension が文字列で copyMetadata が末尾。
    // ------------------------------------------------------------------

    /** 766の SpawnInfo の中身（Join Game と Respawn で共通）。 */
    public record SpawnInfo766(int dimensionId, String worldName, long hashedSeed,
            int gameMode, int previousGameMode, boolean isDebug, boolean isFlat,
            boolean hasDeath, String deathDimension, long deathLocation,
            int portalCooldown) {
    }

    /** 766の SpawnInfo を読む。 */
    public static SpawnInfo766 readSpawnInfo766(Reader reader) {
        int dimensionId = reader.readVarInt();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        int portalCooldown = reader.readVarInt();
        return new SpawnInfo766(dimensionId, worldName, hashedSeed, gameMode,
                previousGameMode, isDebug, isFlat, hasDeath, deathDimension,
                deathLocation, portalCooldown);
    }

    /** 766の SpawnInfo を書く。 */
    public static void writeSpawnInfo766(Writer writer, SpawnInfo766 info) {
        writer.writeVarInt(info.dimensionId());
        writer.writeString(info.worldName());
        writer.writeLong(info.hashedSeed());
        writer.writeByte(info.gameMode());
        writer.writeByte(info.previousGameMode());
        writer.writeBoolean(info.isDebug());
        writer.writeBoolean(info.isFlat());
        writer.writeBoolean(info.hasDeath());
        if (info.hasDeath()) {
            writer.writeString(info.deathDimension());
            writer.writePositionRaw(info.deathLocation());
        }
        writer.writeVarInt(info.portalCooldown());
    }

    /**
     * Respawn（766 → 764／765）。dimension を番号から文字列に戻し、
     * copyMetadata を末尾へ移す（766では SpawnInfo の後ろの u8、
     * 764／765では末尾の bool。どちらも1バイト）。
     */
    public static byte[] translateRespawn766To765(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        try {
            Reader reader = new Reader(payload);
            SpawnInfo766 info = readSpawnInfo766(reader);
            boolean copyMetadata = reader.readByte() != 0;
            requireEnd(reader);
            String worldType = dimensionNameFor(info.dimensionId(), registries);
            Writer writer = new Writer();
            writer.writeString(worldType);
            writer.writeString(info.worldName());
            writer.writeLong(info.hashedSeed());
            writer.writeByte(info.gameMode());
            writer.writeByte(info.previousGameMode());
            writer.writeBoolean(info.isDebug());
            writer.writeBoolean(info.isFlat());
            writer.writeBoolean(info.hasDeath());
            if (info.hasDeath()) {
                writer.writeString(info.deathDimension());
                writer.writePositionRaw(info.deathLocation());
            }
            writer.writeVarInt(info.portalCooldown());
            writer.writeBoolean(copyMetadata);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Respawn（766 → 764）。766→765と同じ形式。 */
    public static byte[] translateRespawn766To764(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn766To765(payload, registries);
    }

    /**
     * Respawn（766 → 763）。dimension を番号から文字列に戻し、
     * copyMetadata を SpawnInfo の後ろから isFlat の後ろへ移す。
     */
    public static byte[] translateRespawn766To763(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        try {
            Reader reader = new Reader(payload);
            SpawnInfo766 info = readSpawnInfo766(reader);
            boolean copyMetadata = reader.readByte() != 0;
            requireEnd(reader);
            String worldType = dimensionNameFor(info.dimensionId(), registries);
            Writer writer = new Writer();
            writer.writeString(worldType);
            writer.writeString(info.worldName());
            writer.writeLong(info.hashedSeed());
            writer.writeByte(info.gameMode());
            writer.writeByte(info.previousGameMode());
            writer.writeBoolean(info.isDebug());
            writer.writeBoolean(info.isFlat());
            writer.writeBoolean(copyMetadata);
            writer.writeBoolean(info.hasDeath());
            if (info.hasDeath()) {
                writer.writeString(info.deathDimension());
                writer.writePositionRaw(info.deathLocation());
            }
            writer.writeVarInt(info.portalCooldown());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Respawn（764／765 → 766）。dimension を文字列から番号にし、
     * copyMetadata を末尾から SpawnInfo の後ろへ移す。
     */
    public static byte[] translateRespawn765To766(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        try {
            Reader reader = new Reader(payload);
            String worldType = reader.readString();
            String worldName = reader.readString();
            long hashedSeed = reader.readLong();
            int gameMode = reader.readUnsignedByte();
            int previousGameMode = reader.readByte();
            boolean isDebug = reader.readBoolean();
            boolean isFlat = reader.readBoolean();
            boolean hasDeath = reader.readBoolean();
            String deathDimension = null;
            long deathLocation = 0;
            if (hasDeath) {
                deathDimension = reader.readString();
                deathLocation = reader.readPositionRaw();
            }
            int portalCooldown = reader.readVarInt();
            boolean copyMetadata = reader.readBoolean();
            requireEnd(reader);
            int dimensionId = dimensionIdFor(worldType, registries);
            Writer writer = new Writer();
            writeSpawnInfo766(writer, new SpawnInfo766(dimensionId, worldName, hashedSeed,
                    gameMode, previousGameMode, isDebug, isFlat, hasDeath, deathDimension,
                    deathLocation, portalCooldown));
            writer.writeByte(copyMetadata ? 1 : 0);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }


    /**
     * Respawn（764／765 → 767）。767の Respawn は766と同一形式のため
     * {@link #translateRespawn765To766} と同じ。
     */
    public static byte[] translateRespawn765To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn765To766(payload, registries);
    }

    /** Respawn（764 → 767）。765→767と同じ形式。 */
    public static byte[] translateRespawn764To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn765To767(payload, registries);
    }

    /**
     * Respawn（767 → 764／765）。766→765と同じ形式。
     */
    public static byte[] translateRespawn767To765(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn766To765(payload, registries);
    }

    /** Respawn（767 → 764）。767→765と同じ形式。 */
    public static byte[] translateRespawn767To764(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn767To765(payload, registries);
    }

    /**
     * Respawn（766 → 767）。766と767で同一形式のため、そのまま返す
     * （変換に失敗したとき translateRespawn766To765 は素通しを返すが、
     * ここでは元をそのまま返す点も同じ）。
     */
    public static byte[] translateRespawn766To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return payload;
    }

    /**
     * Respawn（767 → 766）。{@link #translateRespawn766To767} と同じく
     * 同一形式のため、そのまま返す。
     */
    public static byte[] translateRespawn767To766(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return payload;
    }

    /** Respawn（764 → 766）。765→766と同じ形式。 */
    public static byte[] translateRespawn764To766(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateRespawn765To766(payload, registries);
    }


    // ------------------------------------------------------------------
    // 767（1.21／1.21.1）⇔ 768（1.21.2／1.21.3）の変換
    // 破壊的変更：
    // ・Login Success の strictErrorHandling 廃止（767→768で末尾の bool を落とす）
    // ・SpawnInfo に seaLevel 追加（Join Game／Respawn の末尾近く）
    // ・Synchronize Player Position：teleportId が先頭へ、dx／dy／dz 追加、flags が u8→u32
    // ・移動パケット（サーバー行き）：onGround bool → MovementFlags u8
    // ・Update Time に tickDayTime 追加
    // ・Player Info に update_list_order（listPriority）追加
    // ・Set Slot State の window_id が varint → ContainerID（u8）
    // ------------------------------------------------------------------

    /**
     * 767の Login Success を768形式にする。末尾の strictErrorHandling を落とす。
     */
    public static byte[] translateLoginSuccess767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            UUID uuid = reader.readUuid();
            String username = reader.readString();
            int properties = reader.readVarInt();
            if (properties < 0) {
                throw new IllegalArgumentException("Login Success のプロパティ数が負です: " + properties);
            }
            List<String[]> props = new ArrayList<>();
            for (int i = 0; i < properties; i++) {
                String name = reader.readString();
                String value = reader.readString();
                String signature = reader.readBoolean() ? reader.readString() : null;
                props.add(new String[]{name, value, signature});
            }
            reader.readBoolean(); // strictErrorHandling（768には無い）
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Login Success に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeUuid(uuid);
            writer.writeString(username);
            writer.writeVarInt(props.size());
            for (String[] prop : props) {
                writer.writeString(prop[0]);
                writer.writeString(prop[1]);
                writer.writeBoolean(prop[2] != null);
                if (prop[2] != null) {
                    writer.writeString(prop[2]);
                }
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            // 壊れたペイロードは素通しする（ログインパケットの損失は致命的なため）。
            return payload;
        }
    }

    /**
     * 768の Login Success を767形式にする。末尾に strictErrorHandling=false を補う。
     */
    public static byte[] translateLoginSuccess768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            UUID uuid = reader.readUuid();
            String username = reader.readString();
            int properties = reader.readVarInt();
            if (properties < 0) {
                throw new IllegalArgumentException("Login Success のプロパティ数が負です: " + properties);
            }
            List<String[]> props = new ArrayList<>();
            for (int i = 0; i < properties; i++) {
                String name = reader.readString();
                String value = reader.readString();
                String signature = reader.readBoolean() ? reader.readString() : null;
                props.add(new String[]{name, value, signature});
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Login Success に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeUuid(uuid);
            writer.writeString(username);
            writer.writeVarInt(props.size());
            for (String[] prop : props) {
                writer.writeString(prop[0]);
                writer.writeString(prop[1]);
                writer.writeBoolean(prop[2] != null);
                if (prop[2] != null) {
                    writer.writeString(prop[2]);
                }
            }
            writer.writeBoolean(false); // strictErrorHandling（768には無い）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * 767の Registry Data を768形式にする。順序が変わったレジストリを並べ替え、
     * 768だけにあるエントリは hasData=false で埋める。1レジストリ分のペイロードを受け取り、
     * 1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData767To768(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21, ProtocolVersion.V1_21_2);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 768の Registry Data を767形式にする。{@code minecraft:jukebox_song}
     * は767にも存在するため落とさない。それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData768To767(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * 767の Join Game を768形式にする。SpawnInfo の末尾に seaLevel（63＝既定の海面高度）を補う。
     */
    public static byte[] translateJoinGame767To768(byte[] payload) {
        return translateSpawnInfo767To768(payload, true);
    }

    /**
     * 768の Join Game を767形式にする。SpawnInfo の末尾の seaLevel を落とす。
     */
    public static byte[] translateJoinGame768To767(byte[] payload) {
        return translateSpawnInfo768To767(payload, true);
    }

    /**
     * 767の Respawn を768形式にする。SpawnInfo の末尾に seaLevel（63）を補う。
     * {@code registries} は使わない（767⇔768では codec の差し替えは不要）。
     */
    public static byte[] translateRespawn767To768(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateSpawnInfo767To768(payload, false);
    }

    /**
     * 768の Respawn を767形式にする。SpawnInfo の末尾の seaLevel を落とす。
     */
    public static byte[] translateRespawn768To767(byte[] payload,
            Map<String, Tag.CompoundTag> registries) {
        return translateSpawnInfo768To767(payload, false);
    }

    /**
     * Join Game／Respawn の SpawnInfo 部分に seaLevel を補う（767→768）。
     * {@code joinGame} が真のときは末尾の enforcesSecureChat、偽のときは
     * copyMetadata（u8）も写す。
     */
    private static byte[] translateSpawnInfo767To768(byte[] payload, boolean joinGame) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            if (joinGame) {
                writer.writeInt(reader.readInt()); // entityId
                writer.writeBoolean(reader.readBoolean()); // isHardcore
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader)); // worldNames
                writer.writeVarInt(reader.readVarInt()); // maxPlayers
                writer.writeVarInt(reader.readVarInt()); // viewDistance
                writer.writeVarInt(reader.readVarInt()); // simulationDistance
                writer.writeBoolean(reader.readBoolean()); // reducedDebugInfo
                writer.writeBoolean(reader.readBoolean()); // enableRespawnScreen
                writer.writeBoolean(reader.readBoolean()); // doLimitedCrafting
            }
            copySpawnInfo767To768(reader, writer);
            if (joinGame) {
                writer.writeBoolean(reader.readBoolean()); // enforcesSecureChat
            } else {
                writer.writeByte(reader.readUnsignedByte()); // copyMetadata
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Join Game／Respawn に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Join Game／Respawn の SpawnInfo 部分から seaLevel を落とす（768→767）。
     * {@code joinGame} が真のときは末尾の enforcesSecureChat、偽のときは
     * copyMetadata（u8）も写す。
     */
    private static byte[] translateSpawnInfo768To767(byte[] payload, boolean joinGame) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            if (joinGame) {
                writer.writeInt(reader.readInt()); // entityId
                writer.writeBoolean(reader.readBoolean()); // isHardcore
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader)); // worldNames
                writer.writeVarInt(reader.readVarInt()); // maxPlayers
                writer.writeVarInt(reader.readVarInt()); // viewDistance
                writer.writeVarInt(reader.readVarInt()); // simulationDistance
                writer.writeBoolean(reader.readBoolean()); // reducedDebugInfo
                writer.writeBoolean(reader.readBoolean()); // enableRespawnScreen
                writer.writeBoolean(reader.readBoolean()); // doLimitedCrafting
            }
            copySpawnInfo768To767(reader, writer);
            if (joinGame) {
                writer.writeBoolean(reader.readBoolean()); // enforcesSecureChat
            } else {
                writer.writeByte(reader.readUnsignedByte()); // copyMetadata
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Join Game／Respawn に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** SpawnInfo 1個を767→768向けに写し、末尾に seaLevel（63）を補う。 */
    private static void copySpawnInfo767To768(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt()); // dimension
        writer.writeString(reader.readString()); // name
        writer.writeLong(reader.readLong()); // hashedSeed
        writer.writeByte(reader.readByte()); // gamemode
        writer.writeByte(reader.readByte()); // previousGamemode
        writer.writeBoolean(reader.readBoolean()); // isDebug
        writer.writeBoolean(reader.readBoolean()); // isFlat
        boolean hasDeath = reader.readBoolean();
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(reader.readString()); // death dimensionName
            writer.writePositionRaw(reader.readPositionRaw()); // death location
        }
        writer.writeVarInt(reader.readVarInt()); // portalCooldown
        writer.writeVarInt(63); // seaLevel（既定の海面高度）
    }

    /** SpawnInfo 1個を768→767向けに写し、末尾の seaLevel を落とす。 */
    private static void copySpawnInfo768To767(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt()); // dimension
        writer.writeString(reader.readString()); // name
        writer.writeLong(reader.readLong()); // hashedSeed
        writer.writeByte(reader.readByte()); // gamemode
        writer.writeByte(reader.readByte()); // previousGamemode
        writer.writeBoolean(reader.readBoolean()); // isDebug
        writer.writeBoolean(reader.readBoolean()); // isFlat
        boolean hasDeath = reader.readBoolean();
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(reader.readString()); // death dimensionName
            writer.writePositionRaw(reader.readPositionRaw()); // death location
        }
        writer.writeVarInt(reader.readVarInt()); // portalCooldown
        reader.readVarInt(); // seaLevel（767には無い）
    }

    /**
     * Synchronize Player Position（767 → 768）。
     * teleportId を先頭へ移し、dx／dy／dz（0.0）を補い、flags を u8→u32 にする。
     * u8 の下位5ビット（x／y／z／yaw／pitch）は u32 でも同じ位置のためそのまま写す。
     */
    public static byte[] translatePosition767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            int flags = reader.readUnsignedByte();
            int teleportId = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Synchronize Player Position に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(teleportId);
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeDouble(0.0); // dx
            writer.writeDouble(0.0); // dy
            writer.writeDouble(0.0); // dz
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeInt(flags & 0x1F); // flags（u32。下位5ビットのみ）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Synchronize Player Position（768 → 767）。
     * teleportId を末尾へ移し、dx／dy／dz を落とし、flags を u32→u8 にする。
     * dx／dy／dz の相対フラグが立っていた場合、767のクライアントはその
     * 速度差分を受け取れない（位置そのものは正しく送られる）。
     */
    public static byte[] translatePosition768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int teleportId = reader.readVarInt();
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            reader.readDouble(); // dx（767には無い）
            reader.readDouble(); // dy
            reader.readDouble(); // dz
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            int flags = reader.readInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Synchronize Player Position に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeByte(flags & 0x1F); // flags（u8。下位5ビットのみ）
            writer.writeVarInt(teleportId);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）の onGround bool → MovementFlags u8（bit0＝onGround）。 */
    private static int movementFlagsFromOnGround(boolean onGround) {
        return onGround ? 0x01 : 0x00;
    }

    /** 移動パケット（サーバー行き）Position（767 → 768）。 */
    public static byte[] translatePlayerPosition767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            int flags = movementFlagsFromOnGround(reader.readBoolean());
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Position に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(flags);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）Position（768 → 767）。hasHorizontalCollision は落とす。 */
    public static byte[] translatePlayerPosition768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            boolean onGround = (reader.readUnsignedByte() & 0x01) != 0;
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Position に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeBoolean(onGround);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）Position+Look（767 → 768）。 */
    public static byte[] translatePlayerPositionLook767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            int flags = movementFlagsFromOnGround(reader.readBoolean());
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Position+Look に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeByte(flags);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）Position+Look（768 → 767）。 */
    public static byte[] translatePlayerPositionLook768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            boolean onGround = (reader.readUnsignedByte() & 0x01) != 0;
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Position+Look に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeBoolean(onGround);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）Look（767 → 768）。 */
    public static byte[] translatePlayerLook767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            int flags = movementFlagsFromOnGround(reader.readBoolean());
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Look に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeByte(flags);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）Look（768 → 767）。 */
    public static byte[] translatePlayerLook768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            boolean onGround = (reader.readUnsignedByte() & 0x01) != 0;
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Look に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeBoolean(onGround);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）Flying（767 → 768）。 */
    public static byte[] translatePlayerFlying767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int flags = movementFlagsFromOnGround(reader.readBoolean());
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Flying に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeByte(flags);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** 移動パケット（サーバー行き）Flying（768 → 767）。 */
    public static byte[] translatePlayerFlying768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean onGround = (reader.readUnsignedByte() & 0x01) != 0;
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Flying に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeBoolean(onGround);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Update Time（767 → 768）。末尾に tickDayTime=false を補う。 */
    public static byte[] translateUpdateTime767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long age = reader.readLong();
            long time = reader.readLong();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Time に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeLong(age);
            writer.writeLong(time);
            writer.writeBoolean(false); // tickDayTime（767には無い）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Update Time（768 → 767）。末尾の tickDayTime を落とす。 */
    public static byte[] translateUpdateTime768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long age = reader.readLong();
            long time = reader.readLong();
            reader.readBoolean(); // tickDayTime（767には無い）
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Time に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeLong(age);
            writer.writeLong(time);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Player Info（767 → 768）。768の update_list_order は存在しないため
     * そのまま返す（768のクライアントは list_order 無しのエントリを受け入れる）。
     */
    public static byte[] translatePlayerInfo767To768(byte[] payload) {
        return payload;
    }

    /**
     * Player Info（768 → 767）。update_list_order のビット（0x40）を落とし、
     * 各プレイヤーの末尾の listPriority（VarInt）も落とす。
     * それ以外のアクションのデータ形式は767と768で同一（listed は VarInt）。
     */
    public static byte[] translatePlayerInfo768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readUnsignedByte();
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Player Info の件数が負です: " + count);
            }
            Writer writer = new Writer();
            writer.writeByte(action & ~0x40); // update_list_order のビットを落とす
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeUuid(reader.readUuid());
                if ((action & 0x01) != 0) {
                    writer.writeString(reader.readString()); // 名前
                    int properties = reader.readVarInt();
                    writer.writeVarInt(properties);
                    for (int p = 0; p < properties; p++) {
                        writer.writeString(reader.readString());
                        writer.writeString(reader.readString());
                        boolean hasSignature = reader.readBoolean();
                        writer.writeBoolean(hasSignature);
                        if (hasSignature) {
                            writer.writeString(reader.readString());
                        }
                    }
                }
                if ((action & 0x02) != 0) {
                    boolean hasSession = reader.readBoolean();
                    writer.writeBoolean(hasSession);
                    if (hasSession) {
                        writer.writeUuid(reader.readUuid()); // chat session UUID
                        writer.writeLong(reader.readLong()); // 有効期限
                        writer.writeByteArray(reader.readByteArray()); // 公開鍵
                        writer.writeByteArray(reader.readByteArray()); // 署名
                    }
                }
                if ((action & 0x04) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // ゲームモード
                }
                if ((action & 0x08) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // listed
                }
                if ((action & 0x10) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // 遅延
                }
                if ((action & 0x20) != 0) {
                    boolean hasDisplayName = reader.readBoolean();
                    writer.writeBoolean(hasDisplayName);
                    if (hasDisplayName) {
                        writer.writeAnonymousNbt(reader.readAnonymousNbt().tag());
                    }
                }
                if ((action & 0x40) != 0) {
                    reader.readVarInt(); // listPriority（767には無い）
                }
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Info に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Set Slot State（767 → 768）。window_id は両バージョンとも varint
     * （767は明示の varint、768は ContainerID＝varint）のため同一形式で、
     * 変換は不要である。
     */
    public static byte[] translateSetSlotState767To768(byte[] payload) {
        return payload;
    }

    /** Set Slot State（768 → 767）。window_id は両バージョンとも varint のため同一形式である。 */
    public static byte[] translateSetSlotState768To767(byte[] payload) {
        return payload;
    }

    /**
     * Enchant Item（767 → 768）。windowId は767が u8、768が varint
     *（ContainerID の型が1.21.2で変わった）のため変換する。
     * enchantment（varint）はそのまま。
     */
    public static byte[] translateEnchantItem767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readByte();
            int enchantment = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Enchant Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(windowId);
            writer.writeVarInt(enchantment);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Enchant Item（768 → 767）。windowId を varint（768）から u8（767）にする。 */
    public static byte[] translateEnchantItem768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readVarInt();
            int enchantment = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Enchant Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeByte(windowId);
            writer.writeVarInt(enchantment);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Close Window（767 → 768）。windowId は767が u8、768が varint
     *（ContainerID の型が1.21.2で変わった）のため変換する。
     */
    public static byte[] translateCloseWindow767To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Close Window に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(windowId);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Close Window（768 → 767）。windowId を varint（768）から u8（767）にする。 */
    public static byte[] translateCloseWindow768To767(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Close Window に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeByte(windowId);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Player Input（768 → 767／766／765／764／763の Steer Vehicle）。
     * 768の player_input は u8 のビットフラグ1バイト
     * （bit0 forward／bit1 backward／bit2 left／bit3 right／bit4 jump／
     * bit5 shift／bit6 sprint）だけである。Steer Vehicle は
     * sideways（f32）／forward（f32）／flags（u8：bit0 jump／bit1 sneak）。
     * sprint には送り先が無いため落とす。
     */
    public static byte[] translatePlayerInput768ToSteerVehicle(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int inputs = reader.readUnsignedByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Input に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            float forward = ((inputs & 0x01) != 0 ? 1.0f : 0.0f)
                    - ((inputs & 0x02) != 0 ? 1.0f : 0.0f);
            float sideways = ((inputs & 0x08) != 0 ? 1.0f : 0.0f)
                    - ((inputs & 0x04) != 0 ? 1.0f : 0.0f);
            int flags = 0;
            if ((inputs & 0x10) != 0) {
                flags |= 0x01; // jump
            }
            if ((inputs & 0x20) != 0) {
                flags |= 0x02; // sneak（shift）
            }
            Writer writer = new Writer();
            writer.writeFloat(sideways);
            writer.writeFloat(forward);
            writer.writeByte(flags);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Steer Vehicle（767／766／765／764／763 → 768の Player Input）。
     * sideways／forward の符号から4方向のビットを作り、jump／sneak を
     * jump／shift に写す。sprint ビットは常に false（送り元に無い）。
     */
    public static byte[] translateSteerVehicleToPlayerInput768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            float sideways = reader.readFloat();
            float forward = reader.readFloat();
            int flags = reader.readUnsignedByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Steer Vehicle に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            int inputs = 0;
            if (forward > 0.0f) {
                inputs |= 0x01; // forward
            } else if (forward < 0.0f) {
                inputs |= 0x02; // backward
            }
            if (sideways < 0.0f) {
                inputs |= 0x04; // left
            } else if (sideways > 0.0f) {
                inputs |= 0x08; // right
            }
            if ((flags & 0x01) != 0) {
                inputs |= 0x10; // jump
            }
            if ((flags & 0x02) != 0) {
                inputs |= 0x20; // shift（sneak）
            }
            Writer writer = new Writer();
            writer.writeByte(inputs);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    // ---- Client Information（Settings）の 767<->768 ----
    // 767（と 766 以前）の形式：locale 文字列、viewDistance i8、chatFlags varint、
    // chatColors bool、skinParts u8、mainHand varint、enableTextFiltering bool、
    // enableServerListing bool（8 フィールド）。
    // 768 は末尾に particleStatus varint（0=all / 1=decreased / 2=minimal）が
    // 追加された（proto.yml 1.21.3 の packet_common_settings）。
    // 767 形式のまま 768 サーバーへ送るとバニラの読み取りが EOF になり切断される
    // ため、プロキシが付け足す。逆向きは末尾を落として送り直す。

    /** Client Information の 767 形式→768 形式。末尾に particleStatus=0（all）を付ける。 */
    public static byte[] translateClientInformation767To768(byte[] payload) {
        if (payload == null) {
            return payload;
        }
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBytes(payload);
        writer.writeVarInt(0); // particleStatus: all
        return writer.bytes();
    }

    /**
     * Client Information の 768 形式→767 形式。末尾の particleStatus を落として
     * 8 フィールドだけ送り直す。読めないペイロードはそのまま返す（古いサーバーは
     * 余った末尾バイトを無視するため、壊れるよりはまし）。
     */
    public static byte[] translateClientInformation768To767(byte[] payload) {
        if (payload == null) {
            return payload;
        }
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            String locale = reader.readString();
            int viewDistance = reader.readByte();
            int chatFlags = reader.readVarInt();
            boolean chatColors = reader.readBoolean();
            int skinParts = reader.readUnsignedByte();
            int mainHand = reader.readVarInt();
            boolean enableTextFiltering = reader.readBoolean();
            boolean enableServerListing = reader.readBoolean();
            reader.readVarInt(); // particleStatus（落とす）
            if (reader.remaining() != 0) {
                return payload;
            }
            PacketIO.Writer writer = new PacketIO.Writer();
            writer.writeString(locale);
            writer.writeByte(viewDistance);
            writer.writeVarInt(chatFlags);
            writer.writeBoolean(chatColors);
            writer.writeUnsignedByte(skinParts);
            writer.writeVarInt(mainHand);
            writer.writeBoolean(enableTextFiltering);
            writer.writeBoolean(enableServerListing);
            return writer.bytes();
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return payload;
        }
    }
    // ------------------------------------------------------------------
    // 768 ⇔ 769（1.21.4）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information は768と769で形式が同一のため変換器は要らない。
    // 変わるのは Player Info（update_hat 追加・update_list_order の
    // ビット移動）、Set Held Item（i8→varint）、Vehicle Move
    // （末尾に onGround 追加）、Explosion（knockback f32→f64）、
    // Registry Data（バイオーム pale_garden の挿入）だけである。
    // Explosion は粒子・音の変換器が ChunkTranslator にあるため
    // あちらに置く（ChunkTranslator.translateExplosion768To769 参照）。
    // ------------------------------------------------------------------

    /**
     * Player Info（768 → 769）。768の update_list_order（bit 6）は
     * 769では bit 7 に移る。769の update_hat（bit 6）は768には無いため
     * 送らない（クライアントは既定の表示のまま）。
     * 各プレイヤーの末尾は listPriority（bit 7 のとき）だけ写す。
     * 768→769では listPriority の位置は displayName の直後で変わらない。
     */
    public static byte[] translatePlayerInfo768To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readUnsignedByte();
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Player Info の件数が負です: " + count);
            }
            Writer writer = new Writer();
            writer.writeByte((action & ~0x40) | ((action & 0x40) != 0 ? 0x80 : 0));
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeUuid(reader.readUuid());
                if ((action & 0x01) != 0) {
                    writer.writeString(reader.readString()); // 名前
                    int properties = reader.readVarInt();
                    writer.writeVarInt(properties);
                    for (int p = 0; p < properties; p++) {
                        writer.writeString(reader.readString());
                        writer.writeString(reader.readString());
                        boolean hasSignature = reader.readBoolean();
                        writer.writeBoolean(hasSignature);
                        if (hasSignature) {
                            writer.writeString(reader.readString());
                        }
                    }
                }
                if ((action & 0x02) != 0) {
                    boolean hasSession = reader.readBoolean();
                    writer.writeBoolean(hasSession);
                    if (hasSession) {
                        writer.writeUuid(reader.readUuid()); // chat session UUID
                        writer.writeLong(reader.readLong()); // 有効期限
                        writer.writeByteArray(reader.readByteArray()); // 公開鍵
                        writer.writeByteArray(reader.readByteArray()); // 署名
                    }
                }
                if ((action & 0x04) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // ゲームモード
                }
                if ((action & 0x08) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // listed
                }
                if ((action & 0x10) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // 遅延
                }
                if ((action & 0x20) != 0) {
                    boolean hasDisplayName = reader.readBoolean();
                    writer.writeBoolean(hasDisplayName);
                    if (hasDisplayName) {
                        writer.writeAnonymousNbt(reader.readAnonymousNbt().tag());
                    }
                }
                if ((action & 0x40) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // listPriority
                }
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Info に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Player Info（769 → 768）。769の update_list_order（bit 7）は
     * 768では bit 6 に移る。769の update_hat（bit 6）は768に無いため
     * 各プレイヤーの showHat bool を落とす。
     * 各プレイヤーの末尾の順序は minecraft-data 1.21.4 の通り
     * listPriority（bit 7）→ showHat（bit 6）である
     * （wiki.vg の2024-11-30時点の表は hat 追加前のため参照できない。
     * 実バニラ1.21.4での両ビット同時の確認は未実施）。
     */
    public static byte[] translatePlayerInfo769To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readUnsignedByte();
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Player Info の件数が負です: " + count);
            }
            Writer writer = new Writer();
            writer.writeByte((action & ~0xC0) | ((action & 0x80) != 0 ? 0x40 : 0));
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeUuid(reader.readUuid());
                if ((action & 0x01) != 0) {
                    writer.writeString(reader.readString()); // 名前
                    int properties = reader.readVarInt();
                    writer.writeVarInt(properties);
                    for (int p = 0; p < properties; p++) {
                        writer.writeString(reader.readString());
                        writer.writeString(reader.readString());
                        boolean hasSignature = reader.readBoolean();
                        writer.writeBoolean(hasSignature);
                        if (hasSignature) {
                            writer.writeString(reader.readString());
                        }
                    }
                }
                if ((action & 0x02) != 0) {
                    boolean hasSession = reader.readBoolean();
                    writer.writeBoolean(hasSession);
                    if (hasSession) {
                        writer.writeUuid(reader.readUuid()); // chat session UUID
                        writer.writeLong(reader.readLong()); // 有効期限
                        writer.writeByteArray(reader.readByteArray()); // 公開鍵
                        writer.writeByteArray(reader.readByteArray()); // 署名
                    }
                }
                if ((action & 0x04) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // ゲームモード
                }
                if ((action & 0x08) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // listed
                }
                if ((action & 0x10) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // 遅延
                }
                if ((action & 0x20) != 0) {
                    boolean hasDisplayName = reader.readBoolean();
                    writer.writeBoolean(hasDisplayName);
                    if (hasDisplayName) {
                        writer.writeAnonymousNbt(reader.readAnonymousNbt().tag());
                    }
                }
                if ((action & 0x80) != 0) {
                    writer.writeVarInt(reader.readVarInt()); // listPriority
                }
                if ((action & 0x40) != 0) {
                    reader.readBoolean(); // showHat（768には無い）
                }
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Player Info に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Set Held Item（クライアント行き。768は i8、769は varint）。 */
    public static byte[] translateSetHeldItem768To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int slot = reader.readByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Set Held Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(slot);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Set Held Item（クライアント行き。769は varint、768は i8）。 */
    public static byte[] translateSetHeldItem769To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int slot = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Set Held Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeByte(slot);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Vehicle Move（サーバー行き。769は末尾に onGround bool が増えた）。
     * 768→769は onGround=false を補う（768のクライアントは接地状態を
     * 送らないため近似。実機未確認）。
     */
    public static byte[] translateVehicleMove768To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Vehicle Move に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeBoolean(false); // onGround（768には無い）
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Vehicle Move（サーバー行き。769→768）。末尾の onGround を落とす。 */
    public static byte[] translateVehicleMove769To768(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float yaw = reader.readFloat();
            float pitch = reader.readFloat();
            reader.readBoolean(); // onGround（768には無い）
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Vehicle Move に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * 768の Registry Data を769形式にする。順序が変わったレジストリは
     * 並べ替え（768⇔769では enchantment 等は同一のため素通し）、
     * バイオームには pale_garden を39番に挿入する。
     * 1レジストリ分のペイロードを受け取り、1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData768To769(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4);
        List<byte[]> built = buildRegistryData766(insertPaleGardenBiome(reordered));
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 769の Registry Data を768形式にする。バイオームの pale_garden を
     * 落とし、40番以降のバイオームIDを1つ戻す。
     * {@code minecraft:jukebox_song} は768にも存在するため落とさない。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData769To768(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_2);
        List<byte[]> built = buildRegistryData766(removePaleGardenBiome(reordered));
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * バイオームレジストリに pale_garden を39番に挿入する。
     * 39番以降のエントリの id を+1 し、pale_garden は名前だけの
     * エントリ（hasData=false）で埋める。バイオームレジストリが無い
     * 場合や pale_garden が既にある場合（winter_drop 有効時の768）は
     * そのまま返す。入力のマップは変更しない（新しいマップを返す）。
     */
    public static Map<String, Tag.CompoundTag> insertPaleGardenBiome(
            Map<String, Tag.CompoundTag> registries) {
        Tag.CompoundTag biome = registries.get("minecraft:worldgen/biome");
        if (biome == null) {
            return registries;
        }
        List<Tag> entries = biome.getList("value")
                .map(Tag.ListTag::value)
                .orElse(List.of());
        for (Tag entry : entries) {
            if (entry instanceof Tag.CompoundTag compound
                    && compound.getString("name")
                            .map("minecraft:pale_garden"::equals).orElse(false)) {
                return registries;
            }
        }
        List<Tag> out = new ArrayList<>();
        boolean inserted = false;
        for (Tag entry : entries) {
            if (!(entry instanceof Tag.CompoundTag compound)) {
                out.add(entry);
                continue;
            }
            int id = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value())
                    .orElse(out.size());
            if (!inserted && id >= 39) {
                out.add(paleGardenFillerEntry());
                inserted = true;
            }
            out.add(shiftedBiomeEntry(compound, id >= 39 ? id + 1 : id));
        }
        if (!inserted) {
            out.add(paleGardenFillerEntry());
        }
        Map<String, Tag.CompoundTag> copy = new LinkedHashMap<>(registries);
        Map<String, Tag> codec = new LinkedHashMap<>();
        codec.put("type", new Tag.StringTag("minecraft:worldgen/biome"));
        codec.put("value", new Tag.ListTag(Nbt.COMPOUND, out));
        copy.put("minecraft:worldgen/biome", new Tag.CompoundTag(codec));
        return copy;
    }

    /**
     * バイオームレジストリから pale_garden を落とす。
     * 40番以降のエントリの id を-1 する。pale_garden が無い場合は
     * そのまま返す。入力のマップは変更しない（新しいマップを返す）。
     */
    public static Map<String, Tag.CompoundTag> removePaleGardenBiome(
            Map<String, Tag.CompoundTag> registries) {
        Tag.CompoundTag biome = registries.get("minecraft:worldgen/biome");
        if (biome == null) {
            return registries;
        }
        List<Tag> entries = biome.getList("value")
                .map(Tag.ListTag::value)
                .orElse(List.of());
        boolean present = false;
        for (Tag entry : entries) {
            if (entry instanceof Tag.CompoundTag compound
                    && compound.getString("name")
                            .map("minecraft:pale_garden"::equals).orElse(false)) {
                present = true;
                break;
            }
        }
        if (!present) {
            return registries;
        }
        List<Tag> out = new ArrayList<>();
        for (Tag entry : entries) {
            if (!(entry instanceof Tag.CompoundTag compound)) {
                out.add(entry);
                continue;
            }
            if (compound.getString("name")
                    .map("minecraft:pale_garden"::equals).orElse(false)) {
                continue;
            }
            int id = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value())
                    .orElse(out.size());
            out.add(shiftedBiomeEntry(compound, id > 39 ? id - 1 : id));
        }
        Map<String, Tag.CompoundTag> copy = new LinkedHashMap<>(registries);
        Map<String, Tag> codec = new LinkedHashMap<>();
        codec.put("type", new Tag.StringTag("minecraft:worldgen/biome"));
        codec.put("value", new Tag.ListTag(Nbt.COMPOUND, out));
        copy.put("minecraft:worldgen/biome", new Tag.CompoundTag(codec));
        return copy;
    }

    /** pale_garden の名前だけのエントリ（id=39。element 無し＝hasData=false）。 */
    private static Tag.CompoundTag paleGardenFillerEntry() {
        Map<String, Tag> entry = new LinkedHashMap<>();
        entry.put("name", new Tag.StringTag("minecraft:pale_garden"));
        entry.put("id", new Tag.IntTag(39));
        return new Tag.CompoundTag(entry);
    }

    /** バイオームのエントリ1個を id だけ付け替えて作り直す（name／element はそのまま）。 */
    private static Tag.CompoundTag shiftedBiomeEntry(Tag.CompoundTag compound, int newId) {
        Map<String, Tag> entry = new LinkedHashMap<>();
        entry.put("name", new Tag.StringTag(
                compound.getString("name").orElseThrow(() -> new IllegalArgumentException(
                        "バイオームのエントリに name がありません。"))));
        entry.put("id", new Tag.IntTag(newId));
        compound.get("element").ifPresent(t -> entry.put("element", t));
        return new Tag.CompoundTag(entry);
    }

    // ------------------------------------------------------------------
    // 769 ⇔ 770（1.21.5）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move は
    // 769と770で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Player Chat（先頭に globalIndex varint 追加）、
    // Chat Message／Chat Command Signed（末尾に checksum u8 追加）、
    // Registry Data（レジストリの順序。1.21.5で追加されたレジストリは無い）
    // だけである。Explosion は粒子・音の変換器が ChunkTranslator にあるため
    // あちらに置く（ChunkTranslator.translateExplosion769To770 参照）。
    // ------------------------------------------------------------------

    /**
     * Player Info（769 → 770）。形式は同一のため素通しする。
     * （update_hat・update_list_order とも769・770で同じ。）
     */
    public static byte[] translatePlayerInfo769To770(byte[] payload) {
        return payload;
    }

    /** Player Info（770 → 769）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo770To769(byte[] payload) {
        return payload;
    }

    /** Set Held Item（769 → 770）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem769To770(byte[] payload) {
        return payload;
    }

    /** Set Held Item（770 → 769）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem770To769(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（769 → 770）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove769To770(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（770 → 769）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove770To769(byte[] payload) {
        return payload;
    }

    /**
     * Player Chat（769 → 770）。先頭に globalIndex（varint）を追加する。
     * globalIndex はセッションごとの通し番号（ProxySession が 0 から数える）。
     */
    public static byte[] translatePlayerChat769To770(byte[] payload, int globalIndex) {
        Writer writer = new Writer();
        writer.writeVarInt(globalIndex);
        writer.writeBytes(payload);
        return writer.bytes();
    }

    /**
     * Player Chat（770 → 769）。先頭の globalIndex（varint）を取り除く。
     */
    public static byte[] translatePlayerChat770To769(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            reader.readVarInt(); // globalIndex
            return reader.rest();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Chat Message／Chat Command Signed（770 → 769）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum770(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（769 → 770）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実バニラ 1.21.5 での確認は未実施。サーバーが拒否する可能性がある）。
     */
    public static byte[] appendChatChecksum770(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 769の Registry Data を770形式にする。1.21.5で追加されたレジストリは
     * 無いため順序の付け替え（reorderRegistries）だけでよい。
     * {@code minecraft:jukebox_song} は769にも770にも存在するため落とさない。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData769To770(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 770の Registry Data を769形式にする。1.21.5で追加されたレジストリは
     * 無いため順序の付け替え（reorderRegistries）だけでよい。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData770To769(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_4);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }


    // 770 ⇔ 771（1.21.6）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は770と771で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771ともあるが、番号が 770→771 で+1ずれる。checksum の中身は
    // 770と同じ扱い）、Registry Data（レジストリの順序。1.21.6で追加
    // されたのは minecraft:dialog。jukebox_song は tears の挿入で
    // 17番以降が+1ずれる）だけである。
    // ------------------------------------------------------------------

    /** Player Info（770 → 771）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo770To771(byte[] payload) {
        return payload;
    }

    /** Player Info（771 → 770）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo771To770(byte[] payload) {
        return payload;
    }

    /** Set Held Item（770 → 771）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem770To771(byte[] payload) {
        return payload;
    }

    /** Set Held Item（771 → 770）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem771To770(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（770 → 771）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove770To771(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（771 → 770）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove771To770(byte[] payload) {
        return payload;
    }

    /** Player Chat（770 → 771）。先頭の globalIndex は770・771ともあるため素通しする。 */
    public static byte[] translatePlayerChat770To771(byte[] payload) {
        return payload;
    }

    /** Player Chat（771 → 770）。先頭の globalIndex は770・771ともあるため素通しする。 */
    public static byte[] translatePlayerChat771To770(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（771 → 770）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum771(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（770 → 771）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実バニラ 1.21.6 で checksum 0x00 を送って Player Chat のエコーを
     * 受け取れたことは確認済み。計算式そのものの確認は未実施）。
     */
    public static byte[] appendChatChecksum771(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 770の Registry Data を771形式にする。1.21.6で追加された
     * minecraft:dialog は770には無いため、770サーバーからの Registry Data
     * には含まれない（771クライアントへは欠けたまま送る。実バニラ
     * クライアントでの扱いは未確認）。
     * {@code minecraft:jukebox_song} は tears の挿入で17番以降が+1ずれる
     * ため並べ替える（reorderRegistries）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData770To771(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 771の Registry Data を770形式にする。minecraft:dialog は770には
     * 無いため落とす（reorderRegistries）。jukebox_song は tears を落として
     * 18番以降を-1する。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData771To770(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_5);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }


    // 771 ⇔ 772（1.21.7／1.21.8）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は771と772で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771・772ともある。番号は 771→772 で変わらない。checksum の中身は
    // 771と同じ扱い）、Registry Data（レジストリの順序。1.21.7での変更は
    // jukebox_song の lava_chicken 挿入（9）と絵画 dennis 挿入（14）など。
    // minecraft:dialog は771からあるため通す）だけである。
    // ------------------------------------------------------------------

    /** Player Info（771 → 772）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo771To772(byte[] payload) {
        return payload;
    }

    /** Player Info（772 → 771）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo772To771(byte[] payload) {
        return payload;
    }

    /** Set Held Item（771 → 772）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem771To772(byte[] payload) {
        return payload;
    }

    /** Set Held Item（772 → 771）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem772To771(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（771 → 772）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove771To772(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（772 → 771）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove772To771(byte[] payload) {
        return payload;
    }

    /** Player Chat（771 → 772）。先頭の globalIndex は770・771・772ともあるため素通しする。 */
    public static byte[] translatePlayerChat771To772(byte[] payload) {
        return payload;
    }

    /** Player Chat（772 → 771）。先頭の globalIndex は770・771・772ともあるため素通しする。 */
    public static byte[] translatePlayerChat772To771(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（772 → 771）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum772(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（771 → 772）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     */
    public static byte[] appendChatChecksum772(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 771の Registry Data を772形式にする。minecraft:dialog は771から
     * あるためそのまま通す。{@code minecraft:jukebox_song} は lava_chicken
     * の挿入（9）で9番以降が+1ずれるため並べ替える（reorderRegistries）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData771To772(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_7);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 772の Registry Data を771形式にする。minecraft:dialog は771にも
     * あるため通す（reorderRegistries）。jukebox_song は lava_chicken を落として
     * 10番以降を-1する。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData772To771(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_6);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }


    // 772 ⇔ 773（1.21.9／1.21.10）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は772と773で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771・772・773ともある。番号は 772→773 で変わらない。checksum の中身は
    // 772と同じ扱い）、Registry Data（レジストリの順序。1.21.9での変更は
    // ブロック状態・アイテム・音・パーティクル・エンティティの追加など。
    // jukebox_song・絵画・バイオーム・エンチャント・属性は同一。
    // minecraft:dialog は771からあるため通す）だけである。
    // ------------------------------------------------------------------

    /** Player Info（772 → 773）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo772To773(byte[] payload) {
        return payload;
    }

    /** Player Info（773 → 772）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo773To772(byte[] payload) {
        return payload;
    }

    /** Set Held Item（772 → 773）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem772To773(byte[] payload) {
        return payload;
    }

    /** Set Held Item（773 → 772）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem773To772(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（772 → 773）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove772To773(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（773 → 772）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove773To772(byte[] payload) {
        return payload;
    }

    /** Player Chat（772 → 773）。先頭の globalIndex は770・771・772・773ともあるため素通しする。 */
    public static byte[] translatePlayerChat772To773(byte[] payload) {
        return payload;
    }

    /** Player Chat（773 → 772）。先頭の globalIndex は770・771・772・773ともあるため素通しする。 */
    public static byte[] translatePlayerChat773To772(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（773 → 772）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum773(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（772 → 773）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     */
    public static byte[] appendChatChecksum773(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 772の Registry Data を773形式にする。minecraft:dialog は772から
     * あるためそのまま通す。{@code minecraft:jukebox_song} は lava_chicken
     * の挿入（9）で9番以降が+1ずれるため並べ替える（reorderRegistries）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData772To773(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 773の Registry Data を772形式にする。minecraft:dialog は772にも
     * あるため通す（reorderRegistries）。jukebox_song は lava_chicken を落として
     * 10番以降を-1する。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData773To772(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_7);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }


    // 773 ⇔ 774（1.21.11）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は773と774で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771・772・773・774ともある。番号は 773→774 で変わらない。checksum の中身は
    // 773と同じ扱い）、Registry Data（レジストリの順序。1.21.11での変更は
    // アイテム・音・エンティティの追加と、エンチャント lunge（21）の挿入・
    // ダメージ種別 spear（37）の挿入。ブロック状態・バイオーム・パーティクル・
    // 絵画・ジュークボックス・属性は同一。minecraft:dialog は771からあるため通す）
    // だけである。なお Registry Data の entity_type・sound・item・particle は
    // 773節と同じく素通し（順序は付け替えない。Spawn Entity の種別 ID だけ
    // 翻訳するため 773 と同じずれが残る。制限事項として記録する）。
    // ------------------------------------------------------------------

    /** Player Info（773 → 774）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo773To774(byte[] payload) {
        return payload;
    }

    /** Player Info（774 → 773）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo774To773(byte[] payload) {
        return payload;
    }

    /** Set Held Item（773 → 774）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem773To774(byte[] payload) {
        return payload;
    }

    /** Set Held Item（774 → 773）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem774To773(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（773 → 774）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove773To774(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（774 → 773）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove774To773(byte[] payload) {
        return payload;
    }

    /** Player Chat（773 → 774）。先頭の globalIndex は770・771・772・773・774ともあるため素通しする。 */
    public static byte[] translatePlayerChat773To774(byte[] payload) {
        return payload;
    }

    /** Player Chat（774 → 773）。先頭の globalIndex は770・771・772・773・774ともあるため素通しする。 */
    public static byte[] translatePlayerChat774To773(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（774 → 773）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。773節の同名メソッドと同じ扱い。）
     */
    public static byte[] stripChatChecksum774(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（773 → 774）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。773節の同名メソッドと同じ扱い。）
     */
    public static byte[] appendChatChecksum774(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 773の Registry Data を774形式にする。minecraft:dialog は773から
     * あるためそのまま通す。並べ替えるのは enchantment（lunge を21に挿入）
     * と damage_type（spear を37に挿入）のみ（reorderRegistries）。
     * jukebox_song・絵画は773・774で同一。entity_type・sound・item・
     * particle は素通し（773節と同じ）。それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData773To774(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 774の Registry Data を773形式にする。minecraft:dialog は773にも
     * あるため通す（reorderRegistries）。enchantment は lunge（774=21）を落とし、
     * damage_type は spear（774=37）を落として後ろを-1する。jukebox_song・
     * 絵画は773・774で同一。entity_type・sound・item・particle は素通し
     * （773節と同じ）。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData774To773(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_9);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }


    // 774 ⇔ 775（26.1）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は774と775で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Registry Data（レジストリの順序。26.1での変更はアイテム・音・
    // ブロック状態の追加。エンチャント・ダメージ種別・エンティティ・絵画・
    // ジュークボックス・属性・エフェクト・バイオームは同一。minecraft:dialog は
    // 771からあるため通す）、Update Time（time・tickDayTime から clockUpdates
    // 配列へ再構成。age は両版にある）、Enchant Item（enchantment が i8 から
    // varint へ戻る）、Use Entity（再構成。attack は独立パケットへ分離）だけである。
    // なお Registry Data の entity_type・sound・item・particle は774節と同じく
    // 素通し（順序は付け替えない。Spawn Entity の種別 ID だけ翻訳するため 774 と
    // 同じずれが残る。制限事項として記録する）。
    // ------------------------------------------------------------------

    /** Player Info（774 → 775）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo774To775(byte[] payload) {
        return payload;
    }

    /** Player Info（775 → 774）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo775To774(byte[] payload) {
        return payload;
    }

    /** Set Held Item（774 → 775）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem774To775(byte[] payload) {
        return payload;
    }

    /** Set Held Item（775 → 774）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem775To774(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（774 → 775）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove774To775(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（775 → 774）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove775To774(byte[] payload) {
        return payload;
    }

    /** Player Chat（774 → 775）。先頭の globalIndex は770・771・772・773・774・775ともあるため素通しする。 */
    public static byte[] translatePlayerChat774To775(byte[] payload) {
        return payload;
    }

    /** Player Chat（775 → 774）。先頭の globalIndex は770・771・772・773・774・775ともあるため素通しする。 */
    public static byte[] translatePlayerChat775To774(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（775 → 774）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。774節の同名メソッドと同じ扱い。）
     */
    public static byte[] stripChatChecksum775(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（774 → 775）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。774節の同名メソッドと同じ扱い。）
     */
    public static byte[] appendChatChecksum775(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 774の Registry Data を775形式にする。minecraft:dialog は771から
     * あるためそのまま通す。エンチャント・ダメージ種別・ジュークボックス・
     * 絵画は774・775で同一のため並べ替えは実質そのまま（reorderRegistries は
     * 774→775 の対応表を通す）。entity_type・sound・item・particle は素通し
     * （774節と同じ）。それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData774To775(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_11, ProtocolVersion.V26_1);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 775の Registry Data を774形式にする。minecraft:dialog は774にも
     * あるため通す（reorderRegistries）。エンチャント・ダメージ種別・
     * ジュークボックス・絵画は774・775で同一のため並べ替えは実質そのまま。
     * entity_type・sound・item・particle は素通し（774節と同じ）。
     * それ以外は1レジストリ分のペイロードを返す。空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData775To774(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V26_1, ProtocolVersion.V1_21_11);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * Update Time（774 → 775）。774は {age i64, time i64, tickDayTime bool}、
     * 775は {age i64, clockUpdates[{id varint, totalTicks varlong, partialTick f32, rate f32}]}。
     * age はそのまま写す。time は id 0 の時計の totalTicks にし、tickDayTime は
     * rate にする（true＝1.0f、false＝0.0f）。partialTick は 0.0f にする。
     * （注：775 の clock id の意味と rate の単位は未確認。775 の時計が複数ある
     * 場合、id 0 以外の時計は作らない。実機での確認は未実施。）
     */
    public static byte[] translateUpdateTime774To775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long age = reader.readLong();
            long time = reader.readLong();
            boolean tickDayTime = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Time に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeLong(age);
            writer.writeVarInt(1); // clockUpdates の件数
            writer.writeVarInt(0); // id（既定の時計）
            writer.writeVarLong(time); // totalTicks
            writer.writeFloat(0.0f); // partialTick
            writer.writeFloat(tickDayTime ? 1.0f : 0.0f); // rate
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Update Time（775 → 774）。775は {age i64, clockUpdates[...]}、
     * 774は {age i64, time i64, tickDayTime bool}。age はそのまま写す。
     * time は id 0 の時計の totalTicks（無ければ最初の時計、無ければ 0）、
     * tickDayTime は rate != 0 とする。id 0 以外の時計は落とす。
     * （注：clock id の意味と rate の単位は未確認。実機での確認は未実施。）
     */
    public static byte[] translateUpdateTime775To774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long age = reader.readLong();
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Update Time の時計の件数が負です: " + count);
            }
            long time = 0;
            boolean tickDayTime = false;
            boolean haveFirst = false;
            long firstTime = 0;
            boolean firstTickDayTime = false;
            boolean foundId0 = false;
            for (int i = 0; i < count; i++) {
                int id = reader.readVarInt();
                long totalTicks = reader.readVarLong();
                reader.readFloat(); // partialTick
                float rate = reader.readFloat();
                if (!haveFirst) {
                    firstTime = totalTicks;
                    firstTickDayTime = rate != 0.0f;
                    haveFirst = true;
                }
                if (id == 0) {
                    time = totalTicks;
                    tickDayTime = rate != 0.0f;
                    foundId0 = true;
                }
            }
            if (!foundId0) {
                time = firstTime;
                tickDayTime = firstTickDayTime;
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Time に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeLong(age);
            writer.writeLong(time);
            writer.writeBoolean(tickDayTime);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Enchant Item（774 → 775）。windowId は両版とも varint（ContainerID）。
     * enchantment は774が i8、775が varint のため変換する。
     */
    public static byte[] translateEnchantItem774To775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readVarInt();
            int enchantment = reader.readByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Enchant Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(windowId);
            writer.writeVarInt(enchantment);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Enchant Item（775 → 774）。enchantment は775が varint、774が i8。
     * varint が i8 の範囲（-128〜127）に収まらない場合は素通しする
     * （実在のエンチャント ID は 0〜42 のため起きない）。
     */
    public static byte[] translateEnchantItem775To774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readVarInt();
            int enchantment = reader.readVarInt();
            if (enchantment < -128 || enchantment > 127) {
                throw new IllegalArgumentException(
                        "Enchant Item のエンチャント ID が i8 の範囲外です: " + enchantment);
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Enchant Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(windowId);
            writer.writeByte(enchantment);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Entity（774 → 775）。774は {target varint, mouse varint,
     * x／y／z f32（mouse==2 のときだけ）, hand varint（mouse==0／2 のときだけ）,
     * sneaking bool}、775は {target varint, hand varint（0＝main_hand／1＝off_hand）,
     * location lpVec3（f32×3）, sneaking bool}。
     * mouse==2（interact_at）はそのまま変換する。mouse==0（interact）は
     * location が無いため (0, 0, 0) を補う（775 側は常に location を持つ）。
     * mouse==1（attack）は 775 では独立パケット（attack 0x01）のため
     * ここでは表せず、null を返す（呼び出し側が attack パケットへ振り分けるか落とす）。
     * （注：mouse==0 の location 補完は仮定。実機での確認は未実施。）
     */
    public static byte[] translateUseEntity774To775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            int mouse = reader.readVarInt();
            float x = 0.0f;
            float y = 0.0f;
            float z = 0.0f;
            int hand = 0;
            if (mouse == 2) {
                x = reader.readFloat();
                y = reader.readFloat();
                z = reader.readFloat();
                hand = reader.readVarInt();
            } else if (mouse == 0) {
                hand = reader.readVarInt();
            } else if (mouse == 1) {
                // attack：775 の use_entity では表せない。
                return null;
            } else {
                throw new IllegalArgumentException("Use Entity の mouse が未知です: " + mouse);
            }
            boolean sneaking = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Use Entity に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            writer.writeVarInt(hand);
            writer.writeFloat(x);
            writer.writeFloat(y);
            writer.writeFloat(z);
            writer.writeBoolean(sneaking);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Entity（775 → 774）。775は {target varint, hand varint, location lpVec3,
     * sneaking bool}、774は {target varint, mouse varint, x／y／z f32, hand varint,
     * sneaking bool}。775 には素の interact（mouse==0）の形が無いため、
     * 常に mouse==2（interact_at）として変換する（location は常に付ける）。
     */
    public static byte[] translateUseEntity775To774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            int hand = reader.readVarInt();
            float x = reader.readFloat();
            float y = reader.readFloat();
            float z = reader.readFloat();
            boolean sneaking = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Use Entity に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            writer.writeVarInt(2); // mouse：interact_at
            writer.writeFloat(x);
            writer.writeFloat(y);
            writer.writeFloat(z);
            writer.writeVarInt(hand);
            writer.writeBoolean(sneaking);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Entity（774）の attack（mouse==1）を775の attack パケットのペイロードに直す。
     * 774は {target varint, mouse varint, sneaking bool}、775の attack は {entityId varint}。
     * attack でなければ null を返す（呼び出し側はその場合 translateUseEntity774To775 を使う）。
     * 生成器ではなく手書き（attack 分離は ProxySession 側の配線と対になるため）。
     */
    public static byte[] useEntityAttackTo775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            int mouse = reader.readVarInt();
            if (mouse != 1) {
                return null;
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Attack（775 → 774 の Use Entity）。775は {entityId varint}、774は
     * {target varint, mouse varint, sneaking bool}。775の attack パケットには
     * sneaking が無いため false にする（774サーバー側では「しゃがんでいない攻撃」として扱われる）。
     * 生成器ではなく手書き（attack 分離は ProxySession 側の配線と対になるため）。
     */
    public static byte[] translateAttack775ToUseEntity774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Attack に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            writer.writeVarInt(1); // mouse：attack
            writer.writeBoolean(false); // sneaking：775の attack には無いため false
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Client Command（775）が request_gamerule_values（action==2）かどうか。
     * 774にはこの action が無いため、呼び出し側は true の場合にパケットを落とす。
     * ペイロードが読めない場合は false（通常経路に任せる）。
     */
    public static boolean isClientCommandRequestGameRuleValues775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            return reader.readVarInt() == 2;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }



    // ================================================================
    // 757 <=> 759 (1.18 / 1.18.1 <=> 1.19)
    // ================================================================

    /**
     * 757<=>759の変換で使うレジストリの名前列・element の16進（実バニラの Join Game codec から取ったもの。
     * ライブキャプチャ（/tmp/cap-757-joingame.bin、/tmp/cap-759-joingame.bin）で確認）。
     */
    private static final String[] BIOME_757_NAMES = {
            "minecraft:the_void", "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:desert",
            "minecraft:swamp", "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:old_growth_birch_forest",
            "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:savanna", "minecraft:savanna_plateau",
            "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest", "minecraft:windswept_savanna", "minecraft:jungle", "minecraft:sparse_jungle",
            "minecraft:bamboo_jungle", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:meadow", "minecraft:grove",
            "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:river", "minecraft:frozen_river",
            "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore", "minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
            "minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
            "minecraft:mushroom_fields", "minecraft:dripstone_caves", "minecraft:lush_caves", "minecraft:nether_wastes", "minecraft:warped_forest", "minecraft:crimson_forest",
            "minecraft:soul_sand_valley", "minecraft:basalt_deltas", "minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:small_end_islands",
            "minecraft:end_barrens",
    };

    private static final String[] BIOME_759_NAMES = {
            "minecraft:the_void", "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:desert",
            "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:dark_forest",
            "minecraft:old_growth_birch_forest", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:savanna",
            "minecraft:savanna_plateau", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest", "minecraft:windswept_savanna", "minecraft:jungle",
            "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:meadow",
            "minecraft:grove", "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:river",
            "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
            "minecraft:deep_lukewarm_ocean", "minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean",
            "minecraft:deep_frozen_ocean", "minecraft:mushroom_fields", "minecraft:dripstone_caves", "minecraft:lush_caves", "minecraft:deep_dark", "minecraft:nether_wastes",
            "minecraft:warped_forest", "minecraft:crimson_forest", "minecraft:soul_sand_valley", "minecraft:basalt_deltas", "minecraft:the_end", "minecraft:end_highlands",
            "minecraft:end_midlands", "minecraft:small_end_islands", "minecraft:end_barrens",
    };

    private static final String[] BIOME_757_CATEGORIES = {
            "none", "plains", "plains", "icy", "icy", "desert",
            "swamp", "forest", "forest", "forest", "forest", "forest",
            "taiga", "taiga", "taiga", "taiga", "savanna", "savanna",
            "extreme_hills", "extreme_hills", "extreme_hills", "savanna", "jungle", "jungle",
            "jungle", "mesa", "mesa", "mesa", "mountain", "forest",
            "mountain", "mountain", "mountain", "mountain", "river", "river",
            "beach", "beach", "beach", "ocean", "ocean", "ocean",
            "ocean", "ocean", "ocean", "ocean", "ocean", "ocean",
            "mushroom", "underground", "underground", "nether", "nether", "nether",
            "nether", "nether", "the_end", "the_end", "the_end", "the_end",
            "the_end",
    };

    private static final String[] DIMENSION_TYPE_757_NAMES = {
            "minecraft:overworld", "minecraft:overworld_caves", "minecraft:the_nether", "minecraft:the_end",
    };

    private static final String[] DIMENSION_TYPE_759_NAMES = {
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "minecraft:overworld_caves",
    };

    private static final String[] CHAT_TYPE_759_NAMES = {
            "minecraft:chat", "minecraft:system", "minecraft:game_info", "minecraft:say_command",
            "minecraft:msg_command", "minecraft:team_msg_command", "minecraft:emote_command", "minecraft:tellraw_command",
    };

    /** DIMENSION_TYPE_757_ELEMENT_HEX：実バニラの Join Game codec から取った element（anonymous NBT の16進）。 */
    private static final String[] DIMENSION_TYPE_757_ELEMENT_HEX = {
            // minecraft:overworld（291バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0105000d616d6269656e745f6c696768740000000008000a" +
            "696e66696e696275726e001e6d696e6563726166743a696e66696e696275726e5f6f766572776f726c640100147265737061" +
            "776e5f616e63686f725f776f726b730001000c6861735f736b796c69676874010100096265645f776f726b73010800076566" +
            "666563747300136d696e6563726166743a6f766572776f726c640100096861735f72616964730103000e6c6f676963616c5f" +
            "68656967687400000180060010636f6f7264696e6174655f7363616c653ff00000000000000300056d696e5f79ffffffc001" +
            "000b6861735f6365696c696e6700010009756c7472617761726d000300066865696768740000018000",
            // minecraft:overworld_caves（291バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0105000d616d6269656e745f6c696768740000000008000a" +
            "696e66696e696275726e001e6d696e6563726166743a696e66696e696275726e5f6f766572776f726c640100147265737061" +
            "776e5f616e63686f725f776f726b730001000c6861735f736b796c69676874010100096265645f776f726b73010800076566" +
            "666563747300136d696e6563726166743a6f766572776f726c640100096861735f72616964730103000e6c6f676963616c5f" +
            "68656967687400000180060010636f6f7264696e6174655f7363616c653ff00000000000000300056d696e5f79ffffffc001" +
            "000b6861735f6365696c696e6701010009756c7472617761726d000300066865696768740000018000",
            // minecraft:the_nether（310バイト）。
            "0a01000b7069676c696e5f73616665010100076e61747572616c0005000d616d6269656e745f6c696768743dcccccd08000a" +
            "696e66696e696275726e001b6d696e6563726166743a696e66696e696275726e5f6e65746865720100147265737061776e5f" +
            "616e63686f725f776f726b730101000c6861735f736b796c69676874000100096265645f776f726b73000800076566666563" +
            "747300146d696e6563726166743a7468655f6e657468657204000a66697865645f74696d6500000000000046500100096861" +
            "735f72616964730003000e6c6f676963616c5f68656967687400000080060010636f6f7264696e6174655f7363616c654020" +
            "0000000000000300056d696e5f790000000001000b6861735f6365696c696e6701010009756c7472617761726d0103000668" +
            "65696768740000010000",
            // minecraft:the_end（304バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0005000d616d6269656e745f6c696768740000000008000a" +
            "696e66696e696275726e00186d696e6563726166743a696e66696e696275726e5f656e640100147265737061776e5f616e63" +
            "686f725f776f726b730001000c6861735f736b796c69676874000100096265645f776f726b73000800076566666563747300" +
            "116d696e6563726166743a7468655f656e6404000a66697865645f74696d6500000000000017700100096861735f72616964" +
            "730103000e6c6f676963616c5f68656967687400000100060010636f6f7264696e6174655f7363616c653ff0000000000000" +
            "0300056d696e5f790000000001000b6861735f6365696c696e6700010009756c7472617761726d0003000668656967687400" +
            "00010000",
    };

    /** DIMENSION_TYPE_759_ELEMENT_HEX：実バニラの Join Game codec から取った element（anonymous NBT の16進）。 */
    private static final String[] DIMENSION_TYPE_759_ELEMENT_HEX = {
            // minecraft:overworld（434バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0105000d616d6269656e745f6c696768740000000003001f" +
            "6d6f6e737465725f737061776e5f626c6f636b5f6c696768745f6c696d69740000000008000a696e66696e696275726e001f" +
            "236d696e6563726166743a696e66696e696275726e5f6f766572776f726c640100147265737061776e5f616e63686f725f77" +
            "6f726b730001000c6861735f736b796c69676874010100096265645f776f726b73010800076566666563747300136d696e65" +
            "63726166743a6f766572776f726c640100096861735f72616964730103000e6c6f676963616c5f6865696768740000018006" +
            "0010636f6f7264696e6174655f7363616c653ff00000000000000a00196d6f6e737465725f737061776e5f6c696768745f6c" +
            "6576656c0800047479706500116d696e6563726166743a756e69666f726d0a000576616c756503000d6d696e5f696e636c75" +
            "736976650000000003000d6d61785f696e636c75736976650000000700000300056d696e5f79ffffffc0010009756c747261" +
            "7761726d0001000b6861735f6365696c696e67000300066865696768740000018000",
            // minecraft:the_nether（381バイト）。
            "0a01000b7069676c696e5f73616665010100076e61747572616c0005000d616d6269656e745f6c696768743dcccccd03001f" +
            "6d6f6e737465725f737061776e5f626c6f636b5f6c696768745f6c696d69740000000f08000a696e66696e696275726e001c" +
            "236d696e6563726166743a696e66696e696275726e5f6e65746865720100147265737061776e5f616e63686f725f776f726b" +
            "730101000c6861735f736b796c69676874000100096265645f776f726b73000800076566666563747300146d696e65637261" +
            "66743a7468655f6e657468657204000a66697865645f74696d6500000000000046500100096861735f72616964730003000e" +
            "6c6f676963616c5f68656967687400000080060010636f6f7264696e6174655f7363616c6540200000000000000300196d6f" +
            "6e737465725f737061776e5f6c696768745f6c6576656c0000000b0300056d696e5f7900000000010009756c747261776172" +
            "6d0101000b6861735f6365696c696e67010300066865696768740000010000",
            // minecraft:the_end（447バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0005000d616d6269656e745f6c696768740000000003001f" +
            "6d6f6e737465725f737061776e5f626c6f636b5f6c696768745f6c696d69740000000008000a696e66696e696275726e0019" +
            "236d696e6563726166743a696e66696e696275726e5f656e640100147265737061776e5f616e63686f725f776f726b730001" +
            "000c6861735f736b796c69676874000100096265645f776f726b73000800076566666563747300116d696e6563726166743a" +
            "7468655f656e6404000a66697865645f74696d6500000000000017700100096861735f72616964730103000e6c6f67696361" +
            "6c5f68656967687400000100060010636f6f7264696e6174655f7363616c653ff00000000000000a00196d6f6e737465725f" +
            "737061776e5f6c696768745f6c6576656c0800047479706500116d696e6563726166743a756e69666f726d0a000576616c75" +
            "6503000d6d696e5f696e636c75736976650000000003000d6d61785f696e636c75736976650000000700000300056d696e5f" +
            "7900000000010009756c7472617761726d0001000b6861735f6365696c696e67000300066865696768740000010000",
            // minecraft:overworld_caves（434バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0105000d616d6269656e745f6c696768740000000003001f" +
            "6d6f6e737465725f737061776e5f626c6f636b5f6c696768745f6c696d69740000000008000a696e66696e696275726e001f" +
            "236d696e6563726166743a696e66696e696275726e5f6f766572776f726c640100147265737061776e5f616e63686f725f77" +
            "6f726b730001000c6861735f736b796c69676874010100096265645f776f726b73010800076566666563747300136d696e65" +
            "63726166743a6f766572776f726c640100096861735f72616964730103000e6c6f676963616c5f6865696768740000018006" +
            "0010636f6f7264696e6174655f7363616c653ff00000000000000a00196d6f6e737465725f737061776e5f6c696768745f6c" +
            "6576656c0800047479706500116d696e6563726166743a756e69666f726d0a000576616c756503000d6d696e5f696e636c75" +
            "736976650000000003000d6d61785f696e636c75736976650000000700000300056d696e5f79ffffffc0010009756c747261" +
            "7761726d0001000b6861735f6365696c696e67010300066865696768740000018000",
    };

    /** CHAT_TYPE_759_ELEMENT_HEX：実バニラの Join Game codec から取った element（anonymous NBT の16進）。 */
    private static final String[] CHAT_TYPE_759_ELEMENT_HEX = {
            // minecraft:chat（232バイト）。
            "0a0a0004636861740a000a6465636f726174696f6e08000f7472616e736c6174696f6e5f6b6579000e636861742e74797065" +
            "2e746578740a00057374796c650009000a706172616d65746572730800000002000673656e6465720007636f6e74656e7400" +
            "000a00096e6172726174696f6e0800087072696f726974790004636861740a000a6465636f726174696f6e08000f7472616e" +
            "736c6174696f6e5f6b65790016636861742e747970652e746578742e6e6172726174650a00057374796c650009000a706172" +
            "616d65746572730800000002000673656e6465720007636f6e74656e74000000",
            // minecraft:system（42バイト）。
            "0a0a000463686174000a00096e6172726174696f6e0800087072696f72697479000673797374656d0000",
            // minecraft:game_info（13バイト）。
            "0a0a00076f7665726c61790000",
            // minecraft:say_command（240バイト）。
            "0a0a0004636861740a000a6465636f726174696f6e08000f7472616e736c6174696f6e5f6b65790016636861742e74797065" +
            "2e616e6e6f756e63656d656e740a00057374796c650009000a706172616d65746572730800000002000673656e6465720007" +
            "636f6e74656e7400000a00096e6172726174696f6e0800087072696f726974790004636861740a000a6465636f726174696f" +
            "6e08000f7472616e736c6174696f6e5f6b65790016636861742e747970652e746578742e6e6172726174650a00057374796c" +
            "650009000a706172616d65746572730800000002000673656e6465720007636f6e74656e74000000",
            // minecraft:msg_command（275バイト）。
            "0a0a0004636861740a000a6465636f726174696f6e08000f7472616e736c6174696f6e5f6b65790021636f6d6d616e64732e" +
            "6d6573736167652e646973706c61792e696e636f6d696e670a00057374796c65080005636f6c6f7200046772617901000669" +
            "74616c6963010009000a706172616d65746572730800000002000673656e6465720007636f6e74656e7400000a00096e6172" +
            "726174696f6e0800087072696f726974790004636861740a000a6465636f726174696f6e08000f7472616e736c6174696f6e" +
            "5f6b65790016636861742e747970652e746578742e6e6172726174650a00057374796c650009000a706172616d6574657273" +
            "0800000002000673656e6465720007636f6e74656e74000000",
            // minecraft:team_msg_command（248バイト）。
            "0a0a0004636861740a000a6465636f726174696f6e08000f7472616e736c6174696f6e5f6b65790013636861742e74797065" +
            "2e7465616d2e746578740a00057374796c650009000a706172616d6574657273080000000300097465616d5f6e616d650006" +
            "73656e6465720007636f6e74656e7400000a00096e6172726174696f6e0800087072696f726974790004636861740a000a64" +
            "65636f726174696f6e08000f7472616e736c6174696f6e5f6b65790016636861742e747970652e746578742e6e6172726174" +
            "650a00057374796c650009000a706172616d65746572730800000002000673656e6465720007636f6e74656e74000000",
            // minecraft:emote_command（226バイト）。
            "0a0a0004636861740a000a6465636f726174696f6e08000f7472616e736c6174696f6e5f6b6579000f636861742e74797065" +
            "2e656d6f74650a00057374796c650009000a706172616d65746572730800000002000673656e6465720007636f6e74656e74" +
            "00000a00096e6172726174696f6e0800087072696f726974790004636861740a000a6465636f726174696f6e08000f747261" +
            "6e736c6174696f6e5f6b6579000f636861742e747970652e656d6f74650a00057374796c650009000a706172616d65746572" +
            "730800000002000673656e6465720007636f6e74656e74000000",
            // minecraft:tellraw_command（40バイト）。
            "0a0a000463686174000a00096e6172726174696f6e0800087072696f726974790004636861740000",
    };

    /** dimension_type の id を757順→759順に直す（overworld_caves 1→3）。 */
    private static int mapDimensionType757To759(int id) {
        return switch (id) {
            case 0 -> 0;
            case 1 -> 3;
            case 2 -> 1;
            case 3 -> 2;
            default -> 0; // 範囲外は overworld
        };
    }

    /** dimension_type の id を759順→757順に直す（overworld_caves 3→1）。 */
    private static int mapDimensionType759To757(int id) {
        return switch (id) {
            case 0 -> 0;
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 1;
            default -> 0; // 範囲外は overworld
        };
    }

    /** 16進の anonymous NBT（1要素分）を CompoundTag に直す。 */
    private static Tag.CompoundTag parseElementHex(String hex) {
        Tag tag = Nbt.readAnonymous(HexFormat.of().parseHex(hex), 0).tag();
        if (!(tag instanceof Tag.CompoundTag compound)) {
            throw new IllegalArgumentException("element の NBT がコンパウンドではありません。");
        }
        return compound;
    }

    /** 名前列と element の16進列から、名前→element のマップを作る。 */
    private static Map<String, Tag.CompoundTag> elementMap(String[] names, String[] hexes) {
        Map<String, Tag.CompoundTag> out = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            out.put(names[i], parseElementHex(hexes[i]));
        }
        return out;
    }

    /** 実バニラ1.18の dimension_type element 4件（名前→element）。 */
    private static final Map<String, Tag.CompoundTag> DIMENSION_TYPE_757_ELEMENTS =
            elementMap(DIMENSION_TYPE_757_NAMES, DIMENSION_TYPE_757_ELEMENT_HEX);

    /** 実バニラ1.19の dimension_type element 4件（名前→element）。 */
    private static final Map<String, Tag.CompoundTag> DIMENSION_TYPE_759_ELEMENTS =
            elementMap(DIMENSION_TYPE_759_NAMES, DIMENSION_TYPE_759_ELEMENT_HEX);

    /** 実バニラ1.19の chat_type element 8件（名前→element）。 */
    private static final Map<String, Tag.CompoundTag> CHAT_TYPE_759_ELEMENTS =
            elementMap(CHAT_TYPE_759_NAMES, CHAT_TYPE_759_ELEMENT_HEX);

    /** 757に無く759にあるバイオーム2件の element（名前→element。実バニラ1.19の Join Game codec から取ったもの）。 */
    private static final Map<String, Tag.CompoundTag> BIOME_759_NEW_ELEMENTS = Map.of(
            "minecraft:mangrove_swamp", parseElementHex("0a08000d70726563697069746174696f6e00047261696e0a00076566666563747308001467726173735f636f6c6f725f6d6f64696669657200057377616d700a00056d757369630100157265706c6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e64001f6d696e6563726166743a6d757369632e6f766572776f726c642e7377616d700300096d696e5f64656c617900002ee000030009736b795f636f6c6f720078a7ff03000d666f6c696167655f636f6c6f72008db12703000f77617465725f666f675f636f6c6f72004d7a60030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003a7a6a0a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f4ccccd050008646f776e66616c6c3f66666600"),
            "minecraft:deep_dark", parseElementHex("0a08000d70726563697069746174696f6e00047261696e0a0007656666656374730a00056d757369630100157265706c6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400236d696e6563726166743a6d757369632e6f766572776f726c642e646565705f6461726b0300096d696e5f64656c617900002ee000030009736b795f636f6c6f720078a7ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f4ccccd050008646f776e66616c6c3ecccccd00"));

    /** 送信者の無いチャットに使う nil UUID（1.18のバニラもシステムメッセージには Util.NIL_UUID を使う）。 */
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    /** ブロック座標 (x, y, z) を position（i64 packed）に直す。 */
    private static long packBlockPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** codec の dimension_type から、element が dimension と一致するエントリの名前を返す。無ければ null。 */
    private static String findDimensionName(Tag.CompoundTag codec, Tag dimension) {
        return codec.getCompound("minecraft:dimension_type")
                .flatMap(r -> r.getList("value"))
                .map(Tag.ListTag::value)
                .orElse(List.of()).stream()
                .filter(e -> e instanceof Tag.CompoundTag)
                .map(e -> (Tag.CompoundTag) e)
                .filter(e -> e.get("element").map(el -> el.equals(dimension)).orElse(false))
                .findFirst()
                .flatMap(e -> e.getString("name"))
                .orElse(null);
    }

    /** codec の dimension_type から、名前のエントリの element を返す。無ければ null。 */
    private static Tag findDimensionElement(Tag.CompoundTag codec, String name) {
        return codec.getCompound("minecraft:dimension_type")
                .flatMap(r -> r.getList("value"))
                .map(Tag.ListTag::value)
                .orElse(List.of()).stream()
                .filter(e -> e instanceof Tag.CompoundTag)
                .map(e -> (Tag.CompoundTag) e)
                .filter(e -> e.getString("name").map(name::equals).orElse(false))
                .findFirst()
                .flatMap(e -> e.get("element"))
                .orElse(null);
    }

    /**
     * 757の dimension（NBTの値）に対応する dimension_type の名前を返す。
     * codec 内の element との完全一致で探し、無ければ既定の757 element との一致、
     * (effects, has_ceiling) での推定の順に試す。どれも当たらなければ overworld。
     * codec が null の場合（Respawn）は codec での検索を飛ばす。
     */
    private static String dimensionName757(Tag dimension, Tag.CompoundTag codec) {
        if (codec != null) {
            String fromCodec = findDimensionName(codec, dimension);
            if (fromCodec != null) {
                return fromCodec;
            }
        }
        for (Map.Entry<String, Tag.CompoundTag> e : DIMENSION_TYPE_757_ELEMENTS.entrySet()) {
            if (e.getValue().equals(dimension)) {
                return e.getKey();
            }
        }
        // 推定：effects と has_ceiling の組み合わせで4種を区別する
        // （overworld と overworld_caves は has_ceiling だけが違う。ライブキャプチャで確認）。
        if (dimension instanceof Tag.CompoundTag compound) {
            String effects = compound.getString("effects").orElse("");
            boolean hasCeiling = compound.get("has_ceiling")
                    .filter(t -> t instanceof Tag.ByteTag)
                    .map(t -> ((Tag.ByteTag) t).value() != 0)
                    .orElse(false);
            if (effects.equals("minecraft:the_nether")) {
                return "minecraft:the_nether";
            }
            if (effects.equals("minecraft:the_end")) {
                return "minecraft:the_end";
            }
            if (effects.equals("minecraft:overworld")) {
                return hasCeiling ? "minecraft:overworld_caves" : "minecraft:overworld";
            }
        }
        LOG.warning("dimension の名前が分からなかったため minecraft:overworld にします。");
        return "minecraft:overworld";
    }

    /**
     * 759の dimension（名前）に対応する757の dimension（NBTの値）を返す。
     * 既定の757 element を使う。名前が既定に無く、codec に element があればそれを使い、
     * それも無ければ overworld の element にする。
     */
    private static Tag dimensionValue759(String name, Tag.CompoundTag codec) {
        Tag.CompoundTag element = DIMENSION_TYPE_757_ELEMENTS.get(name);
        if (element != null) {
            return element;
        }
        if (codec != null) {
            Tag fromCodec = findDimensionElement(codec, name);
            if (fromCodec != null) {
                return fromCodec;
            }
        }
        LOG.warning("dimension " + name + " の757 element が無いため minecraft:overworld にします。");
        return DIMENSION_TYPE_757_ELEMENTS.get("minecraft:overworld");
    }

    /** 759の dimension（名前）に対応する757の dimension（NBTの値）を返す（codec が無い場合。Respawn 用）。 */
    private static Tag dimensionValue759(String name) {
        return dimensionValue759(name, null);
    }

    /** codec（名前付き NBT の中身）からレジストリのマップを抜く。 */
    private static Map<String, Tag.CompoundTag> registriesFromCodec(Tag.CompoundTag codec) {
        Map<String, Tag.CompoundTag> out = new LinkedHashMap<>();
        for (Map.Entry<String, Tag> e : codec.value().entrySet()) {
            if (e.getValue() instanceof Tag.CompoundTag reg) {
                out.put(e.getKey(), reg);
            }
        }
        return out;
    }

    /**
     * dimension_type のエントリ列の element を、受信側バージョンの既定値で置き換える。
     * 名前が既定に無いもの（カスタムの dimension_type）はそのまま。
     * 757の element には monster_spawn_block_light_limit／monster_spawn_light_level が無く、
     * infiniburn も "#" 無しのため、そのまま759クライアントに送ると読めない。
     * 逆に759の element には757に無いフィールドがあるため、そのまま757に送らない。
     * 既定値は実バニラの Join Game codec から取ったもの（ライブキャプチャで確認）。
     */
    private static List<Tag> substituteDimensionTypeElements(List<Tag> entries, int toProtocol) {
        Map<String, Tag.CompoundTag> defaults = toProtocol == ProtocolVersion.V1_19
                ? DIMENSION_TYPE_759_ELEMENTS
                : toProtocol == ProtocolVersion.V1_17_1
                        ? DIMENSION_TYPE_756_ELEMENTS : DIMENSION_TYPE_757_ELEMENTS;
        List<Tag> out = new ArrayList<>(entries.size());
        for (Tag entry : entries) {
            if (!(entry instanceof Tag.CompoundTag compound)) {
                out.add(entry);
                continue;
            }
            String name = compound.getString("name").orElse(null);
            Tag.CompoundTag replacement = name == null ? null : defaults.get(name);
            if (replacement == null) {
                out.add(entry);
                continue;
            }
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            compound.value().forEach((k, v) -> {
                if (!k.equals("element")) {
                    rebuilt.put(k, v);
                }
            });
            rebuilt.put("element", replacement);
            out.add(new Tag.CompoundTag(rebuilt));
        }
        return out;
    }

    /** biome のエントリ列の element に category が無ければ757の表から補う（1.18の Biome codec は category を要する。759の element には category が無い）。 */
    private static List<Tag> addBiomeCategories(List<Tag> entries) {
        List<Tag> out = new ArrayList<>(entries.size());
        for (Tag entry : entries) {
            if (!(entry instanceof Tag.CompoundTag compound)) {
                out.add(entry);
                continue;
            }
            int id = compound.get("id")
                    .filter(t -> t instanceof Tag.IntTag)
                    .map(t -> ((Tag.IntTag) t).value())
                    .orElse(-1);
            if (id < 0 || id >= BIOME_757_CATEGORIES.length) {
                out.add(entry);
                continue;
            }
            Tag element = compound.get("element").orElse(null);
            if (!(element instanceof Tag.CompoundTag elementCompound)
                    || elementCompound.get("category").isPresent()) {
                out.add(entry);
                continue;
            }
            Map<String, Tag> rebuiltElement = new LinkedHashMap<>(elementCompound.value());
            rebuiltElement.put("category", new Tag.StringTag(BIOME_757_CATEGORIES[id]));
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            compound.value().forEach((k, v) -> {
                rebuilt.put(k, k.equals("element") ? new Tag.CompoundTag(rebuiltElement) : v);
            });
            out.add(new Tag.CompoundTag(rebuilt));
        }
        return out;
    }

    /**
     * biome のエントリ列のうち element が無いものに、759の既定の element を補う。
     * 対象は757に無い mangrove_swamp と deep_dark の2件（reorderRegistries が
     * 名前だけのエントリで埋めたもの）。759のクライアントは biome の element
     * （色・音など）を要するため、名前だけでは不十分。element は実バニラ1.19の
     * Join Game codec から取ったもの。
     */
    private static List<Tag> fillNewBiomeElements(List<Tag> entries) {
        List<Tag> out = new ArrayList<>(entries.size());
        for (Tag entry : entries) {
            if (!(entry instanceof Tag.CompoundTag compound)) {
                out.add(entry);
                continue;
            }
            if (compound.get("element").isPresent()) {
                out.add(entry);
                continue;
            }
            String name = compound.getString("name").orElse(null);
            Tag.CompoundTag element = name == null ? null : BIOME_759_NEW_ELEMENTS.get(name);
            if (element == null) {
                out.add(entry);
                continue;
            }
            Map<String, Tag> rebuilt = new LinkedHashMap<>(compound.value());
            rebuilt.put("element", element);
            out.add(new Tag.CompoundTag(rebuilt));
        }
        return out;
    }

    /**
     * 757の dimensionCodec を759形式にする。dimension_type・biome を並べ替え、
     * dimension_type の element を759既定値に置き換え、chat_type 8件を既定値で補う。
     * biome の element はそのまま引き継ぐ（757の element にある category は
     * 759の codec では未知のフィールドとして無視される）。757に無い
     * mangrove_swamp・deep_dark の2件には759の既定の element を補う。
     * codec のキーの並びは759の実バニラ通り（chat_type、dimension_type、worldgen/biome）。
     */
    public static Tag.CompoundTag translateDimensionCodec757To759(Tag.CompoundTag codec757) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                registriesFromCodec(codec757), ProtocolVersion.V1_18, ProtocolVersion.V1_19);
        Map<String, Tag> codec = new LinkedHashMap<>();
        List<Tag> chatEntries = new ArrayList<>();
        for (int i = 0; i < CHAT_TYPE_759_NAMES.length; i++) {
            chatEntries.add(new Tag.CompoundTag(Map.of(
                    "name", new Tag.StringTag(CHAT_TYPE_759_NAMES[i]),
                    "id", new Tag.IntTag(i),
                    "element", CHAT_TYPE_759_ELEMENTS.get(CHAT_TYPE_759_NAMES[i]))));
        }
        Map<String, Tag> chatCodec = new LinkedHashMap<>();
        chatCodec.put("type", new Tag.StringTag("minecraft:chat_type"));
        chatCodec.put("value", new Tag.ListTag(Nbt.COMPOUND, chatEntries));
        codec.put("minecraft:chat_type", new Tag.CompoundTag(chatCodec));
        Tag.CompoundTag dimensionRegistry = reordered.get("minecraft:dimension_type");
        if (dimensionRegistry != null) {
            List<Tag> entries = substituteDimensionTypeElements(
                    dimensionRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()),
                    ProtocolVersion.V1_19);
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:dimension_type"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:dimension_type", new Tag.CompoundTag(rebuilt));
        }
        Tag.CompoundTag biomeRegistry = reordered.get("minecraft:worldgen/biome");
        if (biomeRegistry != null) {
            List<Tag> entries = fillNewBiomeElements(
                    biomeRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()));
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:worldgen/biome"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:worldgen/biome", new Tag.CompoundTag(rebuilt));
        }
        return new Tag.CompoundTag(codec);
    }

    /**
     * 759の dimensionCodec を757形式にする。chat_type を落とし、dimension_type・biome を757順に並べ替え、
     * dimension_type の element を757既定値に置き換える。biome の element に category が無ければ757の表から補う。
     * codec のキーの並びは757の実バニラ通り（dimension_type、worldgen/biome）。
     */
    public static Tag.CompoundTag translateDimensionCodec759To757(Tag.CompoundTag codec759) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                registriesFromCodec(codec759), ProtocolVersion.V1_19, ProtocolVersion.V1_18);
        Map<String, Tag> codec = new LinkedHashMap<>();
        Tag.CompoundTag dimensionRegistry = reordered.get("minecraft:dimension_type");
        if (dimensionRegistry != null) {
            List<Tag> entries = substituteDimensionTypeElements(
                    dimensionRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()),
                    ProtocolVersion.V1_18);
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:dimension_type"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:dimension_type", new Tag.CompoundTag(rebuilt));
        }
        Tag.CompoundTag biomeRegistry = reordered.get("minecraft:worldgen/biome");
        if (biomeRegistry != null) {
            List<Tag> entries = addBiomeCategories(
                    biomeRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()));
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:worldgen/biome"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:worldgen/biome", new Tag.CompoundTag(rebuilt));
        }
        return new Tag.CompoundTag(codec);
    }

    // ------------------------------------------------------------------
    // Join Game
    // ------------------------------------------------------------------

    /** 757の Join Game の中身。dimension は DimensionType の値そのもの（NBT）。previousGameMode は i8（-1＝なし）。 */
    public record JoinGame757(int entityId, boolean hardcore, int gameMode, int previousGameMode,
            List<String> worldNames, Tag.CompoundTag dimensionCodec, Tag dimension,
            String worldName, long hashedSeed, int maxPlayers, int viewDistance,
            int simulationDistance, boolean reducedDebug, boolean enableRespawnScreen,
            boolean isDebug, boolean isFlat) {
    }

    /** 759の Join Game の中身。dimension は ResourceKey（文字列）。death は lastDeathLocation。 */
    public record JoinGame759(int entityId, boolean hardcore, int gameMode, int previousGameMode,
            List<String> worldNames, Tag.CompoundTag dimensionCodec, String dimension,
            String worldName, long hashedSeed, int maxPlayers, int viewDistance,
            int simulationDistance, boolean reducedDebug, boolean enableRespawnScreen,
            boolean isDebug, boolean isFlat, boolean hasDeath, String deathDimension,
            long deathLocation) {
    }

    /** GameType の最大 id（spectator＝3）。previousGameMode の範囲検査に使う。 */
    private static final int MAX_GAME_TYPE_ID_757 = 3;

    /** 757／759の previousGameMode（i8）が GameType の範囲内か。-1＝なし、0〜3＝id。 */
    private static void checkPreviousGameMode757(int value) {
        if (value != -1 && (value < 0 || value > MAX_GAME_TYPE_ID_757)) {
            throw new IllegalArgumentException(
                    "previousGameMode が GameType の範囲外です: " + value);
        }
    }

    /**
     * 757の Join Game を読む。順番は entityId, hardcore, gameMode, previousGameMode(i8),
     * worldNames, dimensionCodec（名前付き NBT）, dimension（名前付き NBT）,
     * worldName, hashedSeed, maxPlayers, viewDistance, simulationDistance,
     * reducedDebug, enableRespawnScreen, isDebug, isFlat である
     * （minecraft-data mc-1.18-protocol.json の packet_login とライブキャプチャで確認）。
     */
    public static JoinGame757 parseJoinGame757(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        checkPreviousGameMode757(previousGameMode);
        List<String> worldNames = PacketIO.readStringList(reader);
        Tag dimensionCodec = reader.readNamedNbt().tag();
        if (!(dimensionCodec instanceof Tag.CompoundTag codec)) {
            throw new IllegalArgumentException("Join Game の dimensionCodec がコンパウンドではありません。");
        }
        Tag dimension = reader.readNamedNbt().tag();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        int simulationDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        requireEnd(reader);
        return new JoinGame757(entityId, hardcore, gameMode, previousGameMode, worldNames,
                codec, dimension, worldName, hashedSeed, maxPlayers, viewDistance,
                simulationDistance, reducedDebug, enableRespawnScreen, isDebug, isFlat);
    }

    /**
     * 759の Join Game を読む。757と同じ順番だが、dimension は ResourceKey（文字列）で、
     * 末尾に death（lastDeathLocation の option）が付く
     * （minecraft-data mc-1.19-protocol.json の packet_login とライブキャプチャで確認）。
     */
    public static JoinGame759 parseJoinGame759(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        checkPreviousGameMode757(previousGameMode);
        List<String> worldNames = PacketIO.readStringList(reader);
        Tag dimensionCodec = reader.readNamedNbt().tag();
        if (!(dimensionCodec instanceof Tag.CompoundTag codec)) {
            throw new IllegalArgumentException("Join Game の dimensionCodec がコンパウンドではありません。");
        }
        String dimension = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        int simulationDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        requireEnd(reader);
        return new JoinGame759(entityId, hardcore, gameMode, previousGameMode, worldNames,
                codec, dimension, worldName, hashedSeed, maxPlayers, viewDistance,
                simulationDistance, reducedDebug, enableRespawnScreen, isDebug, isFlat,
                hasDeath, deathDimension, deathLocation);
    }

    /** 757の Join Game を書く。 */
    public static byte[] writeJoinGame757(JoinGame757 join) {
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeNamedNbt("", join.dimensionCodec());
        writer.writeNamedNbt("", join.dimension());
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        return writer.bytes();
    }

    /** 759の Join Game を書く。 */
    public static byte[] writeJoinGame759(JoinGame759 join) {
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeNamedNbt("", join.dimensionCodec());
        writer.writeString(join.dimension());
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeVarInt(join.simulationDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        writer.writeBoolean(join.hasDeath());
        if (join.hasDeath()) {
            writer.writeString(join.deathDimension());
            writer.writePositionRaw(join.deathLocation());
        }
        return writer.bytes();
    }

    /**
     * Join Game（757 → 759）。dimension は NBT の値から codec 内の名前を引き、
     * dimensionCodec は759形式に直す（chat_type 8件を補う）。lastDeathLocation は757に無いため absent。
     * 読めないペイロードは素通しする（このファイルの他の変換器と同じ方針）。
     */
    public static byte[] translateJoinGame757To759(byte[] payload) {
        try {
            JoinGame757 join = parseJoinGame757(payload);
            String dimensionName = dimensionName757(join.dimension(), join.dimensionCodec());
            return writeJoinGame759(new JoinGame759(join.entityId(), join.hardcore(),
                    join.gameMode(), join.previousGameMode(), join.worldNames(),
                    translateDimensionCodec757To759(join.dimensionCodec()), dimensionName,
                    join.worldName(), join.hashedSeed(), join.maxPlayers(), join.viewDistance(),
                    join.simulationDistance(), join.reducedDebug(), join.enableRespawnScreen(),
                    join.isDebug(), join.isFlat(), false, null, 0));
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Join Game（759 → 757）。dimension は名前から757の element を引き、
     * dimensionCodec は757形式に直す（chat_type を落とす）。lastDeathLocation は落とす。
     * 読めないペイロードは素通しする。
     */
    public static byte[] translateJoinGame759To757(byte[] payload) {
        try {
            JoinGame759 join = parseJoinGame759(payload);
            Tag dimension = dimensionValue759(join.dimension(), join.dimensionCodec());
            return writeJoinGame757(new JoinGame757(join.entityId(), join.hardcore(),
                    join.gameMode(), join.previousGameMode(), join.worldNames(),
                    translateDimensionCodec759To757(join.dimensionCodec()), dimension,
                    join.worldName(), join.hashedSeed(), join.maxPlayers(), join.viewDistance(),
                    join.simulationDistance(), join.reducedDebug(), join.enableRespawnScreen(),
                    join.isDebug(), join.isFlat()));
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // Respawn
    // ------------------------------------------------------------------

    /** 757の Respawn（SpawnInfo）の中身。dimension は DimensionType の値そのもの（NBT）。previousGameMode は u8（Join Game の i8 とは違う。protocol.json で確認）。 */
    public record SpawnInfo757(Tag dimension, String worldName, long hashedSeed, int gameMode,
            int previousGameMode, boolean isDebug, boolean isFlat, boolean copyMetadata) {
    }

    /** 759の Respawn（SpawnInfo）の中身。dimension は ResourceKey（文字列）。death は lastDeathLocation。 */
    public record SpawnInfo759(String dimension, String worldName, long hashedSeed, int gameMode,
            int previousGameMode, boolean isDebug, boolean isFlat, boolean copyMetadata,
            boolean hasDeath, String deathDimension, long deathLocation) {
    }

    /** 757の SpawnInfo を読む。 */
    public static SpawnInfo757 readSpawnInfo757(Reader reader) {
        Tag dimension = reader.readNamedNbt().tag();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readUnsignedByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean copyMetadata = reader.readBoolean();
        return new SpawnInfo757(dimension, worldName, hashedSeed, gameMode,
                previousGameMode, isDebug, isFlat, copyMetadata);
    }

    /** 759の SpawnInfo を読む。 */
    public static SpawnInfo759 readSpawnInfo759(Reader reader) {
        String dimension = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readUnsignedByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean copyMetadata = reader.readBoolean();
        boolean hasDeath = reader.readBoolean();
        String deathDimension = null;
        long deathLocation = 0;
        if (hasDeath) {
            deathDimension = reader.readString();
            deathLocation = reader.readPositionRaw();
        }
        return new SpawnInfo759(dimension, worldName, hashedSeed, gameMode,
                previousGameMode, isDebug, isFlat, copyMetadata, hasDeath, deathDimension,
                deathLocation);
    }

    /** 757の SpawnInfo を書く。 */
    public static void writeSpawnInfo757(Writer writer, SpawnInfo757 info) {
        writer.writeNamedNbt("", info.dimension());
        writer.writeString(info.worldName());
        writer.writeLong(info.hashedSeed());
        writer.writeByte(info.gameMode());
        writer.writeByte(info.previousGameMode());
        writer.writeBoolean(info.isDebug());
        writer.writeBoolean(info.isFlat());
        writer.writeBoolean(info.copyMetadata());
    }

    /** 759の SpawnInfo を書く。 */
    public static void writeSpawnInfo759(Writer writer, SpawnInfo759 info) {
        writer.writeString(info.dimension());
        writer.writeString(info.worldName());
        writer.writeLong(info.hashedSeed());
        writer.writeByte(info.gameMode());
        writer.writeByte(info.previousGameMode());
        writer.writeBoolean(info.isDebug());
        writer.writeBoolean(info.isFlat());
        writer.writeBoolean(info.copyMetadata());
        writer.writeBoolean(info.hasDeath());
        if (info.hasDeath()) {
            writer.writeString(info.deathDimension());
            writer.writePositionRaw(info.deathLocation());
        }
    }

    /**
     * Respawn（757 → 759）。dimension は NBT の値から名前を引く（codec が無いため既定の757 element との一致で判定）。
     * lastDeathLocation は757に無いため absent。copyMetadata はそのまま写す。
     * previousGameMode は u8 のまま写す（範囲検査はしない。Join Game の i8 と違い、"無し" の符号が protocol.json には無いため）。
     * 読めないペイロードは素通しする。
     */
    public static byte[] translateRespawn757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            SpawnInfo757 info = readSpawnInfo757(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writeSpawnInfo759(writer, new SpawnInfo759(
                    dimensionName757(info.dimension(), null),
                    info.worldName(), info.hashedSeed(), info.gameMode(),
                    info.previousGameMode(), info.isDebug(), info.isFlat(),
                    info.copyMetadata(), false, null, 0));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Respawn（759 → 757）。dimension は名前から757の element を引く。lastDeathLocation は落とす。
     * copyMetadata はそのまま写す。読めないペイロードは素通しする。
     */
    public static byte[] translateRespawn759To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            SpawnInfo759 info = readSpawnInfo759(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writeSpawnInfo757(writer, new SpawnInfo757(
                    dimensionValue759(info.dimension()),
                    info.worldName(), info.hashedSeed(), info.gameMode(),
                    info.previousGameMode(), info.isDebug(), info.isFlat(),
                    info.copyMetadata()));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // チャット
    // ------------------------------------------------------------------

    /** 1.18の Chat の position（i8）を1.19の System Chat の type（varint）に直す。0=chat、1=system、2=game_info。範囲外は chat（0）。 */
    private static int chatPositionToChatType(int position) {
        if (position < 0 || position > 2) {
            LOG.warning("Chat の position が範囲外のため chat（0）にします: " + position);
            return 0;
        }
        return position;
    }

    /** 1.19の System Chat の type（varint）を1.18の Chat の position（i8）に直す。0=chat、1=system、2=game_info。3〜7（say_command 等）は1.18に無いため chat（0）扱いにする。範囲外も chat（0）。 */
    private static int chatTypeToChatPosition(int type) {
        if (type < 0 || type > 7) {
            LOG.warning("System Chat の type が範囲外のため chat（0）にします: " + type);
            return 0;
        }
        return type <= 2 ? type : 0;
    }

    /**
     * Chat（757 クライアント行き 0x0F → 759 System Chat 0x5F）。
     * 757は {message string, position i8, sender UUID}、759は {content string, type varint}。
     * position は chat_type の id と同じ並び（0=chat、1=system、2=game_info）。
     * sender は System Chat に無いため落とす。
     * （注：759の System Chat の type は varint であり、overlay bool ではない。
     * overlay bool は1.19.1／760で追加されたもので、759には無い。
     * minecraft-data mc-1.19-protocol.json の packet_system_chat で確認。）
     */
    public static byte[] translateChat757ToSystemChat(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            int position = reader.readByte();
            reader.readUuid(); // sender（759 の System Chat には無い）
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(message);
            writer.writeVarInt(chatPositionToChatType(position));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * System Chat（759 0x5F → 757 Chat 0x0F）。sender は無いため nil UUID を補う。
     * type 3〜7（say_command 等）は1.18に無いため chat（position 0）扱いにする。
     */
    public static byte[] translateSystemChat759ToChat(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String content = reader.readString();
            int type = reader.readVarInt();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(content);
            writer.writeByte(chatTypeToChatPosition(type));
            writer.writeUuid(NIL_UUID);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("System Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Chat（757 サーバー行き 0x03 → 759 Chat Message 0x04）。"/" で始まるものはコマンドのため null を返す
     * （translateChat757ToChatCommand を使う。ProxySession は null の場合にそちらを試す）。
     * 署名は無いため timestamp＝現在時刻、salt＝0、署名は空、signedPreview＝false で埋める
     * （763の簡略形と同じ方針。署名の検証はしない）。
     */
    public static byte[] translateChat757ToChatMessage(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            requireEnd(reader);
            if (message.startsWith("/")) {
                return null;
            }
            Writer writer = new Writer();
            writer.writeString(message);
            writer.writeLong(System.currentTimeMillis());
            writer.writeLong(0L);
            writer.writeByteArray(new byte[0]);
            writer.writeBoolean(false);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Chat（757 サーバー行き 0x03 → 759 Chat Command 0x03）。"/" で始まらないものはコマンドではないため null を返す
     * （translateChat757ToChatMessage を使う）。コマンド文字列は "/" を除いたもの。
     * 引数署名は空、timestamp＝現在時刻、salt＝0、signedPreview＝false で埋める。
     */
    public static byte[] translateChat757ToChatCommand(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            requireEnd(reader);
            if (!message.startsWith("/")) {
                return null;
            }
            Writer writer = new Writer();
            writer.writeString(message.substring(1));
            writer.writeLong(System.currentTimeMillis());
            writer.writeLong(0L);
            writer.writeVarInt(0); // argumentSignatures（空）
            writer.writeBoolean(false);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Message（759 サーバー行き 0x04 → 757 Chat 0x03）。署名・タイムスタンプ・salt・signedPreview を落として本文だけ通す。 */
    public static byte[] translateChatMessage759ToChat(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            reader.readLong(); // timestamp
            reader.readLong(); // salt
            reader.readByteArray(); // signature
            reader.readBoolean(); // signedPreview
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString(message);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Message を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Chat Command（759 サーバー行き 0x03 → 757 Chat 0x03）。"/" を付けて戻す。引数署名は落とす。 */
    public static byte[] translateChatCommand759ToChat(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            reader.readLong(); // timestamp
            reader.readLong(); // salt
            int argCount = reader.readVarInt();
            if (argCount < 0) {
                throw new IllegalArgumentException("Chat Command の引数の件数が負です: " + argCount);
            }
            for (int i = 0; i < argCount; i++) {
                reader.readString();
                reader.readByteArray();
            }
            reader.readBoolean(); // signedPreview
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeString("/" + command);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chat Command を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Player Chat（759 クライアント行き 0x30 → 757）。署名付きのため757へは変換せず null を返す（落とす）。
     * 759の Player Chat は署名付きの発言で、757の Chat には署名の送り先が無い。
     * 本文だけを Chat に直すことも考えられるが、chat_type の装飾（"&lt;名前&gt; 本文" 等）を
     * プロキシが合成することになり、サーバーの書式とずれるため行わない。
     * 呼び出し側（ProxySession）は null を受けたらパケットを落とす。
     */
    public static byte[] translatePlayerChat759ToChat(byte[] payload) {
        return null;
    }

    // ------------------------------------------------------------------
    // Spawn Entity
    // ------------------------------------------------------------------

    /** 759の painting のエンティティ種別 id。registries-1.19.json で確認（757は60）。 */
    private static final int PAINTING_ENTITY_759 = 63;

    /** 759の painting のエンティティ種別 id（757側は60）。registries-1.18.json で確認。 */
    private static final int PAINTING_ENTITY_757 = 60;

    /**
     * 759のエンティティ種別 id のうち、living（LivingEntity）ではないもの39件。
     * 実バニラ1.19の server.jar のクラス階層で確認（EntityType の静的初期化子から名前→クラスを引き、
     * LivingEntity（bbf）の子孫かどうかを javap で判定）。painting（63）を含む。
     */
    private static final int[] NON_LIVING_ENTITY_759_IDS = {
            1, 3, 8, 9, 18, 21, 26, 27, 28, 29, 30, 35, 44, 45, 46, 47, 48, 50, 52, 53, 54, 55,
            56, 57, 58, 59, 63, 72, 79, 84, 86, 87, 93, 94, 95, 96, 97, 109, 117,
    };

    /** 759のエンティティ種別 id が living（LivingEntity）かどうか。範囲外の id は living 扱いしない（汎用パケットへ回す）。 */
    public static boolean isLivingEntityType759(int type759) {
        if (type759 < 0 || type759 > 117) {
            return false;
        }
        return java.util.Arrays.binarySearch(NON_LIVING_ENTITY_759_IDS, type759) < 0;
    }

    /**
     * Spawn Entity Living（757 0x02 → 759 Spawn Entity 0x00）。
     * 757は {entityId, uuid, type, x, y, z, yaw, pitch, headPitch, velocity}、
     * 759は {entityId, uuid, type, x, y, z, pitch, yaw, headPitch, objectData, velocity}。
     * pitch／yaw の順序を入れ替え、type はエンティティ種別表で付け替え、
     * objectData は 0 を補う（living には不要。実バニラ1.19の ClientboundAddEntityPacket(Entity) は
     * this(entity, 0) で、javap で確認）。
     */
    public static byte[] translateSpawnEntityLiving757ToSpawnEntity759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            UUID uuid = reader.readUuid();
            int type = reader.readVarInt();
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            int yaw = reader.readByte();
            int pitch = reader.readByte();
            int headPitch = reader.readByte();
            int velX = reader.readShort();
            int velY = reader.readShort();
            int velZ = reader.readShort();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeUuid(uuid);
            writer.writeVarInt(RegistryRemapper.mapEntityType757To759(type));
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(pitch);
            writer.writeByte(yaw);
            writer.writeByte(headPitch);
            writer.writeVarInt(0); // objectData（living は 0）
            writer.writeShort(velX);
            writer.writeShort(velY);
            writer.writeShort(velZ);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity Living を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Spawn Entity（757 0x00 → 759 Spawn Entity 0x00）。
     * 757は {entityId, uuid, type, x, y, z, pitch, yaw, objectData i32, velocity}、
     * 759は {entityId, uuid, type, x, y, z, pitch, yaw, headPitch, objectData varint, velocity}。
     * headPitch は 0 を補う（実バニラ1.19の (Entity, int) コンストラクタは yHeadRot=0.0 で、javap で確認）。
     * objectData は i32→varint に直す。type はエンティティ種別表で付け替える。
     */
    public static byte[] translateSpawnEntity757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            UUID uuid = reader.readUuid();
            int type = reader.readVarInt();
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            int pitch = reader.readByte();
            int yaw = reader.readByte();
            int objectData = reader.readInt();
            int velX = reader.readShort();
            int velY = reader.readShort();
            int velZ = reader.readShort();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeUuid(uuid);
            writer.writeVarInt(RegistryRemapper.mapEntityType757To759(type));
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(pitch);
            writer.writeByte(yaw);
            writer.writeByte(0); // headPitch（汎用は 0）
            writer.writeVarInt(objectData);
            writer.writeShort(velX);
            writer.writeShort(velY);
            writer.writeShort(velZ);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** 方向の3D data value（DOWN=0、UP=1、NORTH=2、SOUTH=3、WEST=4、EAST=5）を絵画の yaw（i8）に直す。SOUTH=0、WEST=64、NORTH=-128、EAST=-64。上下は絵画に無いため SOUTH（0）扱い。 */
    private static int paintingDirectionToYaw(int direction) {
        return switch (direction) {
            case 4 -> 64; // WEST：90度
            case 2 -> -128; // NORTH：180度
            case 5 -> -64; // EAST：270度
            default -> 0; // SOUTH（3）・上下（0／1）・範囲外
        };
    }

    /**
     * Spawn Entity Painting（757 0x03 → 759 Spawn Entity 0x00）。
     * 757は {entityId, uuid, motive varint, pos, direction u8}、
     * 759は {entityId, uuid, type, x, y, z, pitch, yaw, headPitch, objectData, velocity}。
     * type は painting（757:60→759:63）。x／y／z はブロック座標を double に直す。
     * pitch=0、yaw は direction から（実バニラ1.19の AbstractPainting.setDirection は
     * setYRot(get2DDataValue()*90) で、javap で確認）、headPitch=0、
     * objectData は direction の3D data value（実バニラ1.19の Painting.getAddEntityPacket は
     * new ClientboundAddEntityPacket(this, direction.get3DDataValue(), blockPos) で、javap で確認）、
     * velocity は (0, 0, 0)。motive（絵柄）は759の Spawn Entity には無いため落とす
     * （絵柄は後の Set Entity Data で送られる）。
     */
    public static byte[] translateSpawnEntityPainting757ToSpawnEntity759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            UUID uuid = reader.readUuid();
            reader.readVarInt(); // motive（759 には無い）
            long pos = reader.readPositionRaw();
            int direction = reader.readUnsignedByte();
            requireEnd(reader);
            int x = (int) (pos >> 38);
            int y = (int) (pos & 0xFFF);
            int z = (int) ((pos << 26) >> 38); // 26ビットの符号付き（負の座標も正しく復元する）
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeUuid(uuid);
            writer.writeVarInt(RegistryRemapper.mapEntityType757To759(PAINTING_ENTITY_757));
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(0); // pitch（絵画は0）
            writer.writeByte(paintingDirectionToYaw(direction));
            writer.writeByte(0); // headPitch
            writer.writeVarInt(direction); // objectData（3D data value）
            writer.writeShort(0);
            writer.writeShort(0);
            writer.writeShort(0);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity Painting を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Spawn Entity（759 0x00 → 757 Spawn Entity Living 0x02）。type が living の場合のみ変換し、それ以外は null を返す。
     * pitch／yaw の順序を入れ替え、objectData を落とす。type はエンティティ種別表で付け替える。
     */
    public static byte[] translateSpawnEntity759ToLiving(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            UUID uuid = reader.readUuid();
            int type = reader.readVarInt();
            if (!isLivingEntityType759(type)) {
                return null;
            }
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            int pitch = reader.readByte();
            int yaw = reader.readByte();
            int headPitch = reader.readByte();
            reader.readVarInt(); // objectData（757 の living には無い）
            int velX = reader.readShort();
            int velY = reader.readShort();
            int velZ = reader.readShort();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeUuid(uuid);
            writer.writeVarInt(RegistryRemapper.mapEntityType759To757(type));
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(yaw);
            writer.writeByte(pitch);
            writer.writeByte(headPitch);
            writer.writeShort(velX);
            writer.writeShort(velY);
            writer.writeShort(velZ);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Spawn Entity（759 0x00 → 757 Spawn Entity 0x00）。type が living でも painting でもない場合のみ変換し、
     * それ以外は null を返す（translateSpawnEntity759ToLiving／translateSpawnEntity759ToPainting を使う）。
     * headPitch を落とし、objectData は varint→i32 に直す。type はエンティティ種別表で付け替える。
     */
    public static byte[] translateSpawnEntity759To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            UUID uuid = reader.readUuid();
            int type = reader.readVarInt();
            if (type == PAINTING_ENTITY_759 || isLivingEntityType759(type)) {
                return null;
            }
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            int pitch = reader.readByte();
            int yaw = reader.readByte();
            reader.readByte(); // headPitch（757 の汎用には無い）
            int objectData = reader.readVarInt();
            int velX = reader.readShort();
            int velY = reader.readShort();
            int velZ = reader.readShort();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeUuid(uuid);
            writer.writeVarInt(RegistryRemapper.mapEntityType759To757(type));
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeByte(pitch);
            writer.writeByte(yaw);
            writer.writeInt(objectData);
            writer.writeShort(velX);
            writer.writeShort(velY);
            writer.writeShort(velZ);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Spawn Entity（759 0x00 → 757 Spawn Entity Painting 0x03）。type が painting（63）の場合のみ変換し、それ以外は null を返す。
     * x／y／z は double からブロック座標に直す。direction は objectData（3D data value）。
     * motive（絵柄）は Spawn Entity からは分からないため 0（kebab。1.18の既定）にする。
     * 正しい絵柄は後の Set Entity Data で送られる（別途の変換対象）。
     */
    public static byte[] translateSpawnEntity759ToPainting(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int entityId = reader.readVarInt();
            UUID uuid = reader.readUuid();
            int type = reader.readVarInt();
            if (type != PAINTING_ENTITY_759) {
                return null;
            }
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            reader.readByte(); // pitch（絵画は0。使わない）
            reader.readByte(); // yaw（direction から決まるため使わない）
            reader.readByte(); // headPitch（使わない）
            int objectData = reader.readVarInt();
            reader.readShort(); // velocity（使わない）
            reader.readShort();
            reader.readShort();
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(entityId);
            writer.writeUuid(uuid);
            writer.writeVarInt(0); // motive：不明のため kebab（0）
            writer.writePositionRaw(packBlockPosition(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
            writer.writeByte(objectData);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Spawn Entity を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // Player Info／Set Held Item／Vehicle Move
    // ------------------------------------------------------------------

    /** Player Info のプロパティ1件（signature は option）。 */
    private record PlayerInfo757Property(String name, String value, boolean hasSignature, String signature) {
    }

    /** Player Info 1エントリ分（crypto は759の add_player にだけある）。 */
    private record PlayerInfo757Entry(UUID uuid, String name, List<PlayerInfo757Property> properties,
            int gameMode, int ping, boolean hasDisplayName, String displayName,
            boolean hasCrypto, long cryptoTimestamp, byte[] cryptoPublicKey,
            byte[] cryptoSignature) {
    }

    /** Player Info の action が範囲内か（0=add_player、1=update_game_mode、2=update_latency、3=update_display_name、4=remove_player）。 */
    private static void checkPlayerInfoAction(int action) {
        if (action < 0 || action > 4) {
            throw new IllegalArgumentException("Player Info の action が範囲外です: " + action);
        }
    }

    /** Player Info 1エントリ分を読む。hasCryptoField が true の場合（759）は add_player の crypto も読む。 */
    private static PlayerInfo757Entry readPlayerInfo757Entry(Reader reader, int action, boolean hasCryptoField) {
        UUID uuid = reader.readUuid();
        String name = null;
        List<PlayerInfo757Property> properties = List.of();
        int gameMode = 0;
        int ping = 0;
        boolean hasDisplayName = false;
        String displayName = null;
        boolean hasCrypto = false;
        long cryptoTimestamp = 0;
        byte[] cryptoPublicKey = null;
        byte[] cryptoSignature = null;
        switch (action) {
            case 0 -> {
                name = reader.readString();
                int propertyCount = reader.readVarInt();
                if (propertyCount < 0) {
                    throw new IllegalArgumentException(
                            "Player Info のプロパティ件数が負です: " + propertyCount);
                }
                List<PlayerInfo757Property> props = new ArrayList<>(propertyCount);
                for (int i = 0; i < propertyCount; i++) {
                    String propertyName = reader.readString();
                    String propertyValue = reader.readString();
                    boolean hasSignature = reader.readBoolean();
                    String signature = hasSignature ? reader.readString() : null;
                    props.add(new PlayerInfo757Property(propertyName, propertyValue, hasSignature, signature));
                }
                properties = props;
                gameMode = reader.readVarInt();
                ping = reader.readVarInt();
                hasDisplayName = reader.readBoolean();
                displayName = hasDisplayName ? reader.readString() : null;
                if (hasCryptoField) {
                    hasCrypto = reader.readBoolean();
                    if (hasCrypto) {
                        cryptoTimestamp = reader.readLong();
                        cryptoPublicKey = reader.readByteArray();
                        cryptoSignature = reader.readByteArray();
                    }
                }
            }
            case 1 -> gameMode = reader.readVarInt();
            case 2 -> ping = reader.readVarInt();
            case 3 -> {
                hasDisplayName = reader.readBoolean();
                displayName = hasDisplayName ? reader.readString() : null;
            }
            case 4 -> {
                // remove_player：UUID だけ
            }
            default -> throw new IllegalArgumentException("Player Info の action が範囲外です: " + action);
        }
        return new PlayerInfo757Entry(uuid, name, properties, gameMode, ping,
                hasDisplayName, displayName, hasCrypto, cryptoTimestamp, cryptoPublicKey, cryptoSignature);
    }

    /** Player Info 1エントリ分を書く。writeCrypto が true の場合（759）は add_player の crypto も書く（無ければ absent）。 */
    private static void writePlayerInfo757Entry(Writer writer, PlayerInfo757Entry entry, int action, boolean writeCrypto) {
        writer.writeUuid(entry.uuid());
        switch (action) {
            case 0 -> {
                writer.writeString(entry.name());
                writer.writeVarInt(entry.properties().size());
                for (PlayerInfo757Property property : entry.properties()) {
                    writer.writeString(property.name());
                    writer.writeString(property.value());
                    writer.writeBoolean(property.hasSignature());
                    if (property.hasSignature()) {
                        writer.writeString(property.signature());
                    }
                }
                writer.writeVarInt(entry.gameMode());
                writer.writeVarInt(entry.ping());
                writer.writeBoolean(entry.hasDisplayName());
                if (entry.hasDisplayName()) {
                    writer.writeString(entry.displayName());
                }
                if (writeCrypto) {
                    writer.writeBoolean(entry.hasCrypto());
                    if (entry.hasCrypto()) {
                        writer.writeLong(entry.cryptoTimestamp());
                        writer.writeByteArray(entry.cryptoPublicKey());
                        writer.writeByteArray(entry.cryptoSignature());
                    }
                }
            }
            case 1 -> writer.writeVarInt(entry.gameMode());
            case 2 -> writer.writeVarInt(entry.ping());
            case 3 -> {
                writer.writeBoolean(entry.hasDisplayName());
                if (entry.hasDisplayName()) {
                    writer.writeString(entry.displayName());
                }
            }
            case 4 -> {
                // remove_player：UUID だけ
            }
            default -> throw new IllegalArgumentException("Player Info の action が範囲外です: " + action);
        }
    }

    /**
     * Player Info（757 0x36 → 759 0x34）。759は add_player に crypto（公開鍵＋署名の option）が追加されたため、
     * 757→759では absent を補う。action 1〜4（update_game_mode／update_latency／update_display_name／remove_player）は同一。
     * （minecraft-data mc-1.18／mc-1.19-protocol.json の packet_player_info で確認。）
     */
    public static byte[] translatePlayerInfo757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readVarInt();
            checkPlayerInfoAction(action);
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Player Info の件数が負です: " + count);
            }
            List<PlayerInfo757Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(readPlayerInfo757Entry(reader, action, false));
            }
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(action);
            writer.writeVarInt(count);
            for (PlayerInfo757Entry entry : entries) {
                writePlayerInfo757Entry(writer, entry, action, true);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Info を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Player Info（759 0x34 → 757 0x36）。add_player の crypto を落とす。
     * それ以外は同一。
     */
    public static byte[] translatePlayerInfo759To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int action = reader.readVarInt();
            checkPlayerInfoAction(action);
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Player Info の件数が負です: " + count);
            }
            List<PlayerInfo757Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(readPlayerInfo757Entry(reader, action, true));
            }
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeVarInt(action);
            writer.writeVarInt(count);
            for (PlayerInfo757Entry entry : entries) {
                writePlayerInfo757Entry(writer, entry, action, false);
            }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Player Info を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /** Set Held Item（757 0x48 → 759 0x47）。{slot i8} は同一のため素通しする（IDの付け替えは呼び出し側）。 */
    public static byte[] translateSetHeldItem757To759(byte[] payload) {
        return payload;
    }

    /** Set Held Item（759 0x47 → 757 0x48）。{slot i8} は同一のため素通しする。 */
    public static byte[] translateSetHeldItem759To757(byte[] payload) {
        return payload;
    }

    /** Held Item Change（サーバー行き。757 0x25 → 759 0x27）。{slotId i16} は同一のため素通しする。 */
    public static byte[] translateHeldItemChange757To759(byte[] payload) {
        return payload;
    }

    /** Held Item Change（サーバー行き。759 0x27 → 757 0x25）。{slotId i16} は同一のため素通しする。 */
    public static byte[] translateHeldItemChange759To757(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（757 0x2C → 759 0x29）。{x／y／z f64, yaw／pitch f32} は同一のため素通しする。 */
    public static byte[] translateVehicleMove757To759(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（759 0x29 → 757 0x2C）。同一のため素通しする。 */
    public static byte[] translateVehicleMove759To757(byte[] payload) {
        return payload;
    }

    // ---------------------------------------------------------------
    // 757 <=> 759：クライアント→サーバー側の小さい変換器（統合ステップで追加）
    // フィールド構成は minecraft-data protocol.json（mc-1.18／mc-1.19／
    // mc-1.20／mc-26.1-protocol.json）で実測。Wiki値は使っていない。
    // 読めないペイロードは素通しする（このファイルの他の変換器と同じ方針）。
    // ---------------------------------------------------------------

    /**
     * Block Dig（757 → 759）。757は {status varint, location position, face i8}、
     * 759は末尾に sequence（varint）が付く。sequence はサーバー側の照合用で、
     * 757クライアントは送ってこないため 0 を補う。
     */
    public static byte[] translateBlockDig757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int status = reader.readVarInt();
            long location = reader.readPositionRaw();
            int face = reader.readByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Block Dig に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(status);
            writer.writePositionRaw(location);
            writer.writeByte(face);
            writer.writeVarInt(0); // sequence：757には無いため 0 を補う
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Block Dig（763 → 757）。763は759と同じく末尾に sequence（varint）が付くため剥がす。
     * 763／764／765は同形式のため共用する。
     */
    public static byte[] translateBlockDig763To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int status = reader.readVarInt();
            long location = reader.readPositionRaw();
            int face = reader.readByte();
            reader.readVarInt(); // sequence：757には無いため落とす
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Block Dig に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(status);
            writer.writePositionRaw(location);
            writer.writeByte(face);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Block Place（757 → 759）。757は {hand varint, location position, direction varint,
     * cursorX／Y／Z f32, insideBlock bool}、759は末尾に sequence（varint）が付く。
     * sequence は 0 を補う。
     */
    public static byte[] translateBlockPlace757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int hand = reader.readVarInt();
            long location = reader.readPositionRaw();
            int direction = reader.readVarInt();
            float cursorX = reader.readFloat();
            float cursorY = reader.readFloat();
            float cursorZ = reader.readFloat();
            boolean insideBlock = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Block Place に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(hand);
            writer.writePositionRaw(location);
            writer.writeVarInt(direction);
            writer.writeFloat(cursorX);
            writer.writeFloat(cursorY);
            writer.writeFloat(cursorZ);
            writer.writeBoolean(insideBlock);
            writer.writeVarInt(0); // sequence：757には無いため 0 を補う
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Block Place（763 → 757）。763は759と同じく末尾に sequence（varint）が付くため剥がす。
     * 763／764／765は同形式のため共用する。
     */
    public static byte[] translateBlockPlace763To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int hand = reader.readVarInt();
            long location = reader.readPositionRaw();
            int direction = reader.readVarInt();
            float cursorX = reader.readFloat();
            float cursorY = reader.readFloat();
            float cursorZ = reader.readFloat();
            boolean insideBlock = reader.readBoolean();
            reader.readVarInt(); // sequence：757には無いため落とす
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Block Place に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(hand);
            writer.writePositionRaw(location);
            writer.writeVarInt(direction);
            writer.writeFloat(cursorX);
            writer.writeFloat(cursorY);
            writer.writeFloat(cursorZ);
            writer.writeBoolean(insideBlock);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Item（757 → 759）。757は {hand varint}、759は末尾に sequence（varint）が付く。
     * sequence は 0 を補う。
     */
    public static byte[] translateUseItem757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int hand = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Use Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(hand);
            writer.writeVarInt(0); // sequence：757には無いため 0 を補う
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Item（763 → 757）。763は759と同じく末尾に sequence（varint）が付くため剥がす。
     * 763／764／765は同形式のため共用する。
     */
    public static byte[] translateUseItem763To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int hand = reader.readVarInt();
            reader.readVarInt(); // sequence：757には無いため落とす
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Use Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(hand);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Set Beacon Effect（757 → 759）。757は {primary varint, secondary varint}、
     * 759は {primary option varint, secondary option varint}。757の -1（無し）は
     * absent（0x00）に直す。-1 が「無し」の慣習であることは実バニラの
     * ClientboundSetBeaconEffect の仕様からの推論で、protocol.json では決まらない。
     * 実機での確認は未実施。
     */
    public static byte[] translateSetBeaconEffect757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int primary = reader.readVarInt();
            int secondary = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Set Beacon Effect に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writeOptionalEffect(writer, primary);
            writeOptionalEffect(writer, secondary);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Set Beacon Effect の option varint 1件分を書く（-1 は absent）。 */
    private static void writeOptionalEffect(Writer writer, int effect) {
        if (effect < 0) {
            writer.writeBoolean(false);
        } else {
            writer.writeBoolean(true);
            writer.writeVarInt(effect);
        }
    }

    /**
     * Set Beacon Effect（763 → 757）。763は759と同じく option varint のため、
     * absent は -1 に戻す。763／764／765は同形式のため共用する。
     */
    public static byte[] translateSetBeaconEffect763To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int primary = readOptionalEffect(reader);
            int secondary = readOptionalEffect(reader);
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Set Beacon Effect に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(primary);
            writer.writeVarInt(secondary);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /** Set Beacon Effect の option varint 1件分を読む（absent は -1）。 */
    private static int readOptionalEffect(Reader reader) {
        if (!reader.readBoolean()) {
            return -1;
        }
        return reader.readVarInt();
    }

    /**
     * Update Structure Block（757 → 759）。seed だけが varlong（757）→ varint（759）で、
     * それ以外は同一（location position, action varint, mode varint, name string,
     * offset x／y／z i8, size x／y／z i8, mirror varint, rotation varint, metadata string,
     * integrity f32, flags u8）。seed は64ビット→32ビットへの切り詰め（実機での確認は未実施）。
     */
    public static byte[] translateUpdateStructureBlock757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long location = reader.readPositionRaw();
            int action = reader.readVarInt();
            int mode = reader.readVarInt();
            String name = reader.readString();
            int offsetX = reader.readByte();
            int offsetY = reader.readByte();
            int offsetZ = reader.readByte();
            int sizeX = reader.readByte();
            int sizeY = reader.readByte();
            int sizeZ = reader.readByte();
            int mirror = reader.readVarInt();
            int rotation = reader.readVarInt();
            String metadata = reader.readString();
            float integrity = reader.readFloat();
            long seed = reader.readVarLong();
            int flags = reader.readUnsignedByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Structure Block に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writePositionRaw(location);
            writer.writeVarInt(action);
            writer.writeVarInt(mode);
            writer.writeString(name);
            writer.writeByte(offsetX);
            writer.writeByte(offsetY);
            writer.writeByte(offsetZ);
            writer.writeByte(sizeX);
            writer.writeByte(sizeY);
            writer.writeByte(sizeZ);
            writer.writeVarInt(mirror);
            writer.writeVarInt(rotation);
            writer.writeString(metadata);
            writer.writeFloat(integrity);
            writer.writeVarInt((int) seed);
            writer.writeUnsignedByte(flags);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Update Structure Block（763 → 757）。763は759と同じく seed が varint のため varlong に戻す。
     * 763／764／765は同形式のため共用する。
     */
    public static byte[] translateUpdateStructureBlock763To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long location = reader.readPositionRaw();
            int action = reader.readVarInt();
            int mode = reader.readVarInt();
            String name = reader.readString();
            int offsetX = reader.readByte();
            int offsetY = reader.readByte();
            int offsetZ = reader.readByte();
            int sizeX = reader.readByte();
            int sizeY = reader.readByte();
            int sizeZ = reader.readByte();
            int mirror = reader.readVarInt();
            int rotation = reader.readVarInt();
            String metadata = reader.readString();
            float integrity = reader.readFloat();
            int seed = reader.readVarInt();
            int flags = reader.readUnsignedByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Structure Block に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writePositionRaw(location);
            writer.writeVarInt(action);
            writer.writeVarInt(mode);
            writer.writeString(name);
            writer.writeByte(offsetX);
            writer.writeByte(offsetY);
            writer.writeByte(offsetZ);
            writer.writeByte(sizeX);
            writer.writeByte(sizeY);
            writer.writeByte(sizeZ);
            writer.writeVarInt(mirror);
            writer.writeVarInt(rotation);
            writer.writeString(metadata);
            writer.writeFloat(integrity);
            writer.writeVarLong(seed);
            writer.writeUnsignedByte(flags);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Login Start（757 → 759）。757は {username string} のみ、759は末尾に
     * signature の option（{timestamp i64, publicKey buffer, signature buffer}）が付く。
     * 757クライアントは署名を持たないため absent（0x00）を補う。
     */
    public static byte[] translateLoginStart757To759(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String username = reader.readString();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString(username);
            writer.writeBoolean(false); // signature 無し
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Login Start（759 → 757）。759の signature option を落として {username string} だけにする。
     * 署名付きの場合は署名データを読み飛ばす。
     */
    public static byte[] translateLoginStart759To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String username = reader.readString();
            if (reader.readBoolean()) { // signature option
                reader.readLong(); // timestamp
                reader.readByteArray(); // publicKey
                reader.readByteArray(); // signature
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Login Start に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString(username);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Chat Message（763 → 757）。763は {message string, timestamp i64, salt i64,
     * signature option（bool＋256バイト）, offset varint, acknowledged 3バイト}。
     * 署名・タイムスタンプ等を剥がして757の chat {message string} に直す。
     * 763／764／765は同形式のため共用する（775は末尾に checksum u8 が付くため別メソッド）。
     */
    public static byte[] translateChatMessage763To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            reader.readLong(); // timestamp
            reader.readLong(); // salt
            if (reader.readBoolean()) { // signature option
                reader.readBytes(256);
            }
            reader.readVarInt(); // offset
            reader.readBytes(3); // acknowledged
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Chat Message に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString(message);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Chat Command（763 → 757）。763は {command string, timestamp i64, salt i64,
     * argumentSignatures（count varint＋{name string, signature 256バイト}）,
     * messageCount varint, acknowledged 3バイト}。署名等を剥がして757の chat
     * {"/"＋command} に直す。763／764／765は同形式のため共用する。
     */
    public static byte[] translateChatCommand763To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            reader.readLong(); // timestamp
            reader.readLong(); // salt
            int count = reader.readVarInt();
            for (int i = 0; i < count; i++) {
                reader.readString(); // argumentName
                reader.readBytes(256); // signature
            }
            reader.readVarInt(); // messageCount
            reader.readBytes(3); // acknowledged
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Chat Command に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString("/" + command);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Chat Message（775 → 757）。775は763形式の末尾に checksum（u8）が付く。
     * 署名・タイムスタンプ等を剥がして757の chat {message string} に直す。
     */
    public static byte[] translateChatMessage775To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String message = reader.readString();
            reader.readLong(); // timestamp
            reader.readLong(); // salt
            if (reader.readBoolean()) { // signature option
                reader.readBytes(256);
            }
            reader.readVarInt(); // offset
            reader.readBytes(3); // acknowledged
            reader.readByte(); // checksum
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Chat Message に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString(message);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Chat Command（775 → 757）。775の符号なし Chat Command は {command string} のみ。
     * 757の chat {"/"＋command} に直す。
     */
    public static byte[] translateChatCommand775To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Chat Command に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString("/" + command);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Chat Command Signed（775 → 757）。775は {command string, timestamp i64, salt i64,
     * argumentSignatures（count varint＋{name string, signature 256バイト}）,
     * messageCount varint, acknowledged 3バイト, checksum u8}。署名等を剥がして
     * 757の chat {"/"＋command} に直す。
     */
    public static byte[] translateChatCommandSigned775To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            String command = reader.readString();
            reader.readLong(); // timestamp
            reader.readLong(); // salt
            int count = reader.readVarInt();
            for (int i = 0; i < count; i++) {
                reader.readString(); // argumentName
                reader.readBytes(256); // signature
            }
            reader.readVarInt(); // messageCount
            reader.readBytes(3); // acknowledged
            reader.readByte(); // checksum
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Chat Command Signed に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString("/" + command);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Attack（775 → 757 の Use Entity）。775は {entityId varint}、757は
     * {target varint, mouse varint, sneaking bool}（mouse==1 が attack）。
     * 775の attack パケットには sneaking が無いため false にする
     * （translateAttack775ToUseEntity774 と同じ方針）。
     */
    public static byte[] translateAttack775ToUseEntity757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Attack に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            writer.writeVarInt(1); // mouse：attack
            writer.writeBoolean(false); // sneaking：775の attack には無いため false
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Entity（775 → 757）。775は {target varint, hand varint, location（f32×3）,
     * sneaking bool}、757は {target varint, mouse varint, x／y／z f32, hand varint,
     * sneaking bool}。775 には素の interact（mouse==0）の形が無いため、
     * 常に mouse==2（interact_at）として変換する
     * （translateUseEntity775To774 と同じ方針）。
     */
    public static byte[] translateUseEntity775To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            int hand = reader.readVarInt();
            float x = reader.readFloat();
            float y = reader.readFloat();
            float z = reader.readFloat();
            boolean sneaking = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Use Entity に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            writer.writeVarInt(2); // mouse：interact_at
            writer.writeFloat(x);
            writer.writeFloat(y);
            writer.writeFloat(z);
            writer.writeVarInt(hand);
            writer.writeBoolean(sneaking);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }


    // ================================================================
    // 756 <=> 757 (1.17.1 <=> 1.18 / 1.18.1)
    // ================================================================
    //
    // フィールド差分は minecraft-data protocol.json（mc-1.17.1／mc-1.18）で実測。
    // play toClient の差分は次の5件だけ（それ以外は全て同一）。
    //   - packet_login（Join Game）：757は viewDistance の後に simulationDistance（varint）が付く。
    //   - packet_simulation_distance：757だけにある（0x57。1.18で追加。756には無い）。
    //   - packet_map_chunk（Chunk Data）：756は {bitMap, heightmaps, biomes, chunkData, blockEntities}、
    //     757は {heightmaps, chunkData, blockEntities, trustEdges, light masks, skyLight, blockLight}。
    //     ChunkTranslator の担当のためここでは扱わない。
    //   - packet_multi_block_change：chunkCoordinates の y が756は unsigned、757は signed。
    //     ChunkTranslator の担当のためここでは扱わない。
    //   - packet_tile_entity_data（Block Entity Data）：action が756は u8、757は varint。
    //     ChunkTranslator の担当のためここでは扱わない。
    // play toServer の差分は packet_settings（Client Information）1件だけ。
    // login／handshaking／status は全パケット同一（差分0件）。
    //
    // 変換器を作らなかったもの（フィールド同一のため。protocol.json で確認。ID の付け替えだけは
    // PacketIds の表に任せる）：Chat（両方向）、Player Info、Set Held Item、Vehicle Move、
    // Block Dig／Block Place／Use Item（sequence は757・756とも無い。759で追加されたもの）、
    // Set Beacon Effect（両方 {primary varint, secondary varint}。option化は759から）、
    // Update Structure Block（seed は両方 varlong。varint化は759から）、
    // Initialize World Border／World Border Lerp Size（speed は両方 varlong。varint化は759から）、
    // Login Start（両方 {username} のみ。signature は759から）、
    // Enchant Item（両方 {windowId i8, enchantment i8}）。
    // Update Time は ID だけが違う（756=0x58、757=0x59。ペイロード同一）。
    //
    // dimensionCodec の違い（実バニラの Join Game ライブキャプチャで確認。
    // 756：31,555バイト、757：23,986バイト。どちらも Python ソケットの偽クライアントで取得。
    // JVM の TCP ループバックは遮断されるため Java クライアントは使えない）：
    //   - dimension_type は4件・同一順序（overworld 0、overworld_caves 1、the_nether 2、the_end 3）で
    //     id の写像は恒等。ただし overworld／overworld_caves の element は違う
    //     （1.18のワールド高さ変更：logical_height 256→384、min_y 0→-64、height 256→384）ため
    //     element は受信側の既定値で置き換える（757<=>759と同じ方針）。the_nether／the_end は同一。
    //   - worldgen/biome は756が81件（id 0〜175の非連番）、757が61件（id 0〜60の連番）。
    //     共有44件＋改名11件。757にしか無い6件は meadow→plains、grove→snowy_taiga、
    //     snowy_slopes→snowy_tundra、frozen_peaks→snowy_mountains、jagged_peaks→mountains、
    //     stony_peaks→mountains（RegistryTables756 の BIOME_756 表通り）。
    //     756→757では57件を持ち越し、残る4件（meadow／snowy_slopes／jagged_peaks／
    //     stony_peaks。grove／frozen_peaks は通常持ち越しで埋まる）を実バニラ1.18の element で
    //     埋めて61件にする。757→756では56件を持ち越し、残る25件を実バニラ1.17.1の element で
    //     埋めて81件にする。埋め表は両方向とも全件分（61件／81件）持つため、入力の codec が
    //     欠けていても出力は完全な codec になる。
    //   - 写像の衝突（複数の元が同じ先になる）は逆写像が指す正規の元を優先する。
    //     756→757の衝突は20組あり、19組は先勝ちで正規の元が残るが、windswept_forest（757 id 20）
    //     だけは例外で、wooded_hills（756 id 18）と wooded_mountains（756 id 34）が両方写り、
    //     逆写像は34を指す。先勝ちだと18の element が残り、往復で wooded_mountains が18の
    //     element に化けてしまうため、正規の元（34）が来たら上書きする。757→756の衝突4組は
    //     先勝ちで正規の元が残るが、念のため同じ規則にしている。
    //   - バイオーム element は756だけに depth・scale がある（1.18で削除）。
    //     756→757では落とし、757→756では実バニラ1.17.1の値を足す
    //     （depth は effects の直後、scale は temperature の直後に挿入し、並びも再現する）。
    //     それ以外の値の違い（savanna の temperature 1.2→2.0、dripstone_caves／lush_caves への
    //     music 追加等）は element をそのまま引き継ぐ（757<=>759と同じ方針で訂正しない）。
    //   - Join Game の dimension は両方 NBT の値。Respawn は両方とも
    //     {dimension NBT, worldName, seed, gameMode u8, previousGameMode u8, isDebug, isFlat,
    //     copyMetadata bool} で同一。dataToKeep はどちらにも無い。
    //
    // 設計上の決定：
    //   - 756→757の simulationDistance は viewDistance と同じ値にする
    //     （1.17.1に simulationDistance は無く、実質のシミュレーション距離は描画距離だったため。
    //     実バニラ1.18サーバーも simulation-distance の既定値は view-distance と同じ10を送る）。
    //   - 756→757の Client Information の enableServerListing は true で補完する
    //     （1.18以前のクライアントに一覧表示の拒否手段は無かったため。
    //     実バニラ1.18クライアントの送信値は未確認）。
    //   - reorderRegistries には756<=>757の対応を入れていない。756のバイオームは id が非連番で
    //     176枠の表にすると隙間まで埋めてしまうため、codec の変換はこの節の専用メソッドで行う。

    private static final String[] DIMENSION_TYPE_756_NAMES = {
            "minecraft:overworld", "minecraft:overworld_caves", "minecraft:the_nether", "minecraft:the_end",
    };

    /** DIMENSION_TYPE_756_ELEMENT_HEX：実バニラ1.17.1の Join Game codec から取った element（anonymous NBT の16進）。 */
    private static final String[] DIMENSION_TYPE_756_ELEMENT_HEX = {
            // minecraft:overworld（291バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0105000d616d6269656e745f6c696768740000000008" +
            "000a696e66696e696275726e001e6d696e6563726166743a696e66696e696275726e5f6f766572776f726c6401001472" +
            "65737061776e5f616e63686f725f776f726b730001000c6861735f736b796c69676874010100096265645f776f726b73" +
            "010800076566666563747300136d696e6563726166743a6f766572776f726c640100096861735f72616964730103000e" +
            "6c6f676963616c5f68656967687400000100060010636f6f7264696e6174655f7363616c653ff0000000000000030005" +
            "6d696e5f790000000001000b6861735f6365696c696e6700010009756c7472617761726d000300066865696768740000" +
            "010000",
            // minecraft:overworld_caves（291バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0105000d616d6269656e745f6c696768740000000008" +
            "000a696e66696e696275726e001e6d696e6563726166743a696e66696e696275726e5f6f766572776f726c6401001472" +
            "65737061776e5f616e63686f725f776f726b730001000c6861735f736b796c69676874010100096265645f776f726b73" +
            "010800076566666563747300136d696e6563726166743a6f766572776f726c640100096861735f72616964730103000e" +
            "6c6f676963616c5f68656967687400000100060010636f6f7264696e6174655f7363616c653ff0000000000000030005" +
            "6d696e5f790000000001000b6861735f6365696c696e6701010009756c7472617761726d000300066865696768740000" +
            "010000",
            // minecraft:the_nether（310バイト）。
            "0a01000b7069676c696e5f73616665010100076e61747572616c0005000d616d6269656e745f6c696768743dcccccd08" +
            "000a696e66696e696275726e001b6d696e6563726166743a696e66696e696275726e5f6e657468657201001472657370" +
            "61776e5f616e63686f725f776f726b730101000c6861735f736b796c69676874000100096265645f776f726b73000800" +
            "076566666563747300146d696e6563726166743a7468655f6e657468657204000a66697865645f74696d650000000000" +
            "0046500100096861735f72616964730003000e6c6f676963616c5f68656967687400000080060010636f6f7264696e61" +
            "74655f7363616c6540200000000000000300056d696e5f790000000001000b6861735f6365696c696e6701010009756c" +
            "7472617761726d010300066865696768740000010000",
            // minecraft:the_end（304バイト）。
            "0a01000b7069676c696e5f73616665000100076e61747572616c0005000d616d6269656e745f6c696768740000000008" +
            "000a696e66696e696275726e00186d696e6563726166743a696e66696e696275726e5f656e640100147265737061776e" +
            "5f616e63686f725f776f726b730001000c6861735f736b796c69676874000100096265645f776f726b73000800076566" +
            "666563747300116d696e6563726166743a7468655f656e6404000a66697865645f74696d650000000000001770010009" +
            "6861735f72616964730103000e6c6f676963616c5f68656967687400000100060010636f6f7264696e6174655f736361" +
            "6c653ff00000000000000300056d696e5f790000000001000b6861735f6365696c696e6700010009756c747261776172" +
            "6d000300066865696768740000010000",
    };

    /** 実バニラ1.17.1の dimension_type element 4件（名前→element）。 */
    private static final Map<String, Tag.CompoundTag> DIMENSION_TYPE_756_ELEMENTS =
            elementMap(DIMENSION_TYPE_756_NAMES, DIMENSION_TYPE_756_ELEMENT_HEX);

    /**
     * 756のバイオーム名（index = 756の id。id 0〜175の非連番のため隙間は null）。
     * 実バニラ1.17.1の Join Game codec（81件）と biomes-1.17.json の（id, name）から取ったもの。
     */
    private static final String[] BIOME_756_NAMES = {
            "minecraft:ocean", "minecraft:plains", "minecraft:desert", "minecraft:mountains", "minecraft:forest", "minecraft:taiga", "minecraft:swamp", "minecraft:river",
            "minecraft:nether_wastes", "minecraft:the_end", "minecraft:frozen_ocean", "minecraft:frozen_river", "minecraft:snowy_tundra", "minecraft:snowy_mountains", "minecraft:mushroom_fields", "minecraft:mushroom_field_shore",
            "minecraft:beach", "minecraft:desert_hills", "minecraft:wooded_hills", "minecraft:taiga_hills", "minecraft:mountain_edge", "minecraft:jungle", "minecraft:jungle_hills", "minecraft:jungle_edge",
            "minecraft:deep_ocean", "minecraft:stone_shore", "minecraft:snowy_beach", "minecraft:birch_forest", "minecraft:birch_forest_hills", "minecraft:dark_forest", "minecraft:snowy_taiga", "minecraft:snowy_taiga_hills",
            "minecraft:giant_tree_taiga", "minecraft:giant_tree_taiga_hills", "minecraft:wooded_mountains", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:badlands", "minecraft:wooded_badlands_plateau", "minecraft:badlands_plateau",
            "minecraft:small_end_islands", "minecraft:end_midlands", "minecraft:end_highlands", "minecraft:end_barrens", "minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:cold_ocean", "minecraft:deep_warm_ocean",
            "minecraft:deep_lukewarm_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean", null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, "minecraft:the_void",
            null, "minecraft:sunflower_plains", "minecraft:desert_lakes", "minecraft:gravelly_mountains", "minecraft:flower_forest", "minecraft:taiga_mountains", "minecraft:swamp_hills", null,
            null, null, null, null, "minecraft:ice_spikes", null, null, null,
            null, null, null, null, null, "minecraft:modified_jungle", null, "minecraft:modified_jungle_edge",
            null, null, null, "minecraft:tall_birch_forest", "minecraft:tall_birch_hills", "minecraft:dark_forest_hills", "minecraft:snowy_taiga_mountains", null,
            "minecraft:giant_spruce_taiga", "minecraft:giant_spruce_taiga_hills", "minecraft:modified_gravelly_mountains", "minecraft:shattered_savanna", "minecraft:shattered_savanna_plateau", "minecraft:eroded_badlands", "minecraft:modified_wooded_badlands_plateau", "minecraft:modified_badlands_plateau",
            "minecraft:bamboo_jungle", "minecraft:bamboo_jungle_hills", "minecraft:soul_sand_valley", "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:basalt_deltas", "minecraft:dripstone_caves", "minecraft:lush_caves"
    };

    /**
     * 757のバイオーム id → その写像先の756バイオームの (depth, scale)。
     * 実バニラ1.17.1の Join Game codec から取った値。757にしか無い6件も写像先の値が入っている
     * （meadow→plains 等。先勝ちで落とされるものは使われないが、写像先が空の場合はそのまま使える）。
     */
    private static final float[][] BIOME_757_DEPTH_SCALE = {
            {0.1f, 0.2f}, // 0 minecraft:the_void -> 756 id 127 minecraft:the_void
            {0.125f, 0.05f}, // 1 minecraft:plains -> 756 id 1 minecraft:plains
            {0.125f, 0.05f}, // 2 minecraft:sunflower_plains -> 756 id 129 minecraft:sunflower_plains
            {0.125f, 0.05f}, // 3 minecraft:snowy_plains -> 756 id 12 minecraft:snowy_tundra
            {0.425f, 0.45000002f}, // 4 minecraft:ice_spikes -> 756 id 140 minecraft:ice_spikes
            {0.125f, 0.05f}, // 5 minecraft:desert -> 756 id 2 minecraft:desert
            {-0.2f, 0.1f}, // 6 minecraft:swamp -> 756 id 6 minecraft:swamp
            {0.1f, 0.2f}, // 7 minecraft:forest -> 756 id 4 minecraft:forest
            {0.1f, 0.4f}, // 8 minecraft:flower_forest -> 756 id 132 minecraft:flower_forest
            {0.1f, 0.2f}, // 9 minecraft:birch_forest -> 756 id 27 minecraft:birch_forest
            {0.1f, 0.2f}, // 10 minecraft:dark_forest -> 756 id 29 minecraft:dark_forest
            {0.2f, 0.4f}, // 11 minecraft:old_growth_birch_forest -> 756 id 155 minecraft:tall_birch_forest
            {0.2f, 0.2f}, // 12 minecraft:old_growth_pine_taiga -> 756 id 32 minecraft:giant_tree_taiga
            {0.2f, 0.2f}, // 13 minecraft:old_growth_spruce_taiga -> 756 id 160 minecraft:giant_spruce_taiga
            {0.2f, 0.2f}, // 14 minecraft:taiga -> 756 id 5 minecraft:taiga
            {0.2f, 0.2f}, // 15 minecraft:snowy_taiga -> 756 id 30 minecraft:snowy_taiga
            {0.125f, 0.05f}, // 16 minecraft:savanna -> 756 id 35 minecraft:savanna
            {1.5f, 0.025f}, // 17 minecraft:savanna_plateau -> 756 id 36 minecraft:savanna_plateau
            {1f, 0.5f}, // 18 minecraft:windswept_hills -> 756 id 3 minecraft:mountains
            {1f, 0.5f}, // 19 minecraft:windswept_gravelly_hills -> 756 id 131 minecraft:gravelly_mountains
            {1f, 0.5f}, // 20 minecraft:windswept_forest -> 756 id 34 minecraft:wooded_mountains
            {0.3625f, 1.225f}, // 21 minecraft:windswept_savanna -> 756 id 163 minecraft:shattered_savanna
            {0.1f, 0.2f}, // 22 minecraft:jungle -> 756 id 21 minecraft:jungle
            {0.1f, 0.2f}, // 23 minecraft:sparse_jungle -> 756 id 23 minecraft:jungle_edge
            {0.1f, 0.2f}, // 24 minecraft:bamboo_jungle -> 756 id 168 minecraft:bamboo_jungle
            {0.1f, 0.2f}, // 25 minecraft:badlands -> 756 id 37 minecraft:badlands
            {0.1f, 0.2f}, // 26 minecraft:eroded_badlands -> 756 id 165 minecraft:eroded_badlands
            {1.5f, 0.025f}, // 27 minecraft:wooded_badlands -> 756 id 38 minecraft:wooded_badlands_plateau
            {0.125f, 0.05f}, // 28 minecraft:meadow -> 756 id 1 minecraft:plains
            {0.2f, 0.2f}, // 29 minecraft:grove -> 756 id 30 minecraft:snowy_taiga
            {0.125f, 0.05f}, // 30 minecraft:snowy_slopes -> 756 id 12 minecraft:snowy_tundra
            {0.45f, 0.3f}, // 31 minecraft:frozen_peaks -> 756 id 13 minecraft:snowy_mountains
            {1f, 0.5f}, // 32 minecraft:jagged_peaks -> 756 id 3 minecraft:mountains
            {1f, 0.5f}, // 33 minecraft:stony_peaks -> 756 id 3 minecraft:mountains
            {-0.5f, 0f}, // 34 minecraft:river -> 756 id 7 minecraft:river
            {-0.5f, 0f}, // 35 minecraft:frozen_river -> 756 id 11 minecraft:frozen_river
            {0f, 0.025f}, // 36 minecraft:beach -> 756 id 16 minecraft:beach
            {0f, 0.025f}, // 37 minecraft:snowy_beach -> 756 id 26 minecraft:snowy_beach
            {0.1f, 0.8f}, // 38 minecraft:stony_shore -> 756 id 25 minecraft:stone_shore
            {-1f, 0.1f}, // 39 minecraft:warm_ocean -> 756 id 44 minecraft:warm_ocean
            {-1f, 0.1f}, // 40 minecraft:lukewarm_ocean -> 756 id 45 minecraft:lukewarm_ocean
            {-1.8f, 0.1f}, // 41 minecraft:deep_lukewarm_ocean -> 756 id 48 minecraft:deep_lukewarm_ocean
            {-1f, 0.1f}, // 42 minecraft:ocean -> 756 id 0 minecraft:ocean
            {-1.8f, 0.1f}, // 43 minecraft:deep_ocean -> 756 id 24 minecraft:deep_ocean
            {-1f, 0.1f}, // 44 minecraft:cold_ocean -> 756 id 46 minecraft:cold_ocean
            {-1.8f, 0.1f}, // 45 minecraft:deep_cold_ocean -> 756 id 49 minecraft:deep_cold_ocean
            {-1f, 0.1f}, // 46 minecraft:frozen_ocean -> 756 id 10 minecraft:frozen_ocean
            {-1.8f, 0.1f}, // 47 minecraft:deep_frozen_ocean -> 756 id 50 minecraft:deep_frozen_ocean
            {0.2f, 0.3f}, // 48 minecraft:mushroom_fields -> 756 id 14 minecraft:mushroom_fields
            {0.125f, 0.05f}, // 49 minecraft:dripstone_caves -> 756 id 174 minecraft:dripstone_caves
            {0.1f, 0.2f}, // 50 minecraft:lush_caves -> 756 id 175 minecraft:lush_caves
            {0.1f, 0.2f}, // 51 minecraft:nether_wastes -> 756 id 8 minecraft:nether_wastes
            {0.1f, 0.2f}, // 52 minecraft:warped_forest -> 756 id 172 minecraft:warped_forest
            {0.1f, 0.2f}, // 53 minecraft:crimson_forest -> 756 id 171 minecraft:crimson_forest
            {0.1f, 0.2f}, // 54 minecraft:soul_sand_valley -> 756 id 170 minecraft:soul_sand_valley
            {0.1f, 0.2f}, // 55 minecraft:basalt_deltas -> 756 id 173 minecraft:basalt_deltas
            {0.1f, 0.2f}, // 56 minecraft:the_end -> 756 id 9 minecraft:the_end
            {0.1f, 0.2f}, // 57 minecraft:end_highlands -> 756 id 42 minecraft:end_highlands
            {0.1f, 0.2f}, // 58 minecraft:end_midlands -> 756 id 41 minecraft:end_midlands
            {0.1f, 0.2f}, // 59 minecraft:small_end_islands -> 756 id 40 minecraft:small_end_islands
            {0.1f, 0.2f}, // 60 minecraft:end_barrens -> 756 id 43 minecraft:end_barrens
    };

    /**
     * 実バニラ1.18のバイオーム element 全61件（名前→element。Join Game codec から取ったもの）。
     * 756→757で写像で埋まらない757のバイオームを埋めるために使う。通常は4件だけが埋められる
     * （meadow／snowy_slopes／jagged_peaks／stony_peaks。grove／frozen_peaks は通常
     * snowy_taiga_mountains／snowy_mountains の持ち越しで埋まる）。入力の756 codec が欠けて
     * いる場合も、ここから埋めるため出力は常に61件の完全な codec になる。
     * Map.of は10件までしか受け付けないため Map.ofEntries を使う。
     */
    private static final Map<String, Tag.CompoundTag> BIOME_757_FILL_ELEMENTS = Map.ofEntries(
            Map.entry("minecraft:the_void", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900046e6f6e6500")),
            Map.entry("minecraft:plains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720078a7" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f4ccccd050008646f776e66616c" +
                    "6c3ecccccd08000863617465676f72790006706c61696e7300")),
            Map.entry("minecraft:sunflower_plains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720078a7" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f4ccccd050008646f776e66616c" +
                    "6c3ecccccd08000863617465676f72790006706c61696e7300")),
            Map.entry("minecraft:snowy_plains", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726500000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f7279000369637900")),
            Map.entry("minecraft:ice_spikes", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726500000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f7279000369637900")),
            Map.entry("minecraft:desert", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726540000000050008646f776e66616c" +
                    "6c0000000008000863617465676f7279000664657365727400")),
            Map.entry("minecraft:swamp", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a00076566666563747308001467726173735f636f6c6f725f" +
                    "6d6f64696669657200057377616d70030009736b795f636f6c6f720078a7ff03000d666f6c696167655f636f6c6f7200" +
                    "6a703903000f77617465725f666f675f636f6c6f7200232317030009666f675f636f6c6f7200c0d8ff03000b77617465" +
                    "725f636f6c6f7200617b640a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673" +
                    "65744000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f" +
                    "636b5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f4ccccd050008646f776e66" +
                    "616c6c3f66666608000863617465676f727900057377616d7000")),
            Map.entry("minecraft:forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720079a6" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f333333050008646f776e66616c" +
                    "6c3f4ccccd08000863617465676f72790006666f7265737400")),
            Map.entry("minecraft:flower_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720079a6" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f333333050008646f776e66616c" +
                    "6c3f4ccccd08000863617465676f72790006666f7265737400")),
            Map.entry("minecraft:birch_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007aa5" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f19999a050008646f776e66616c" +
                    "6c3f19999a08000863617465676f72790006666f7265737400")),
            Map.entry("minecraft:dark_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a00076566666563747308001467726173735f636f6c6f725f" +
                    "6d6f646966696572000b6461726b5f666f72657374030009736b795f636f6c6f720079a6ff03000f77617465725f666f" +
                    "675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a" +
                    "6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000000008000573" +
                    "6f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f65787465" +
                    "6e7400000008000005000b74656d70657261747572653f333333050008646f776e66616c6c3f4ccccd08000863617465" +
                    "676f72790006666f7265737400")),
            Map.entry("minecraft:old_growth_birch_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007aa5" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f19999a050008646f776e66616c" +
                    "6c3f19999a08000863617465676f72790006666f7265737400")),
            Map.entry("minecraft:old_growth_pine_taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ca3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653e99999a050008646f776e66616c" +
                    "6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:old_growth_spruce_taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653e800000050008646f776e66616c" +
                    "6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653e800000050008646f776e66616c" +
                    "6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:snowy_taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f7200839e" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d7065726174757265bf000000050008646f776e66616c" +
                    "6c3ecccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:savanna", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726540000000050008646f776e66616c" +
                    "6c0000000008000863617465676f72790007736176616e6e6100")),
            Map.entry("minecraft:savanna_plateau", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726540000000050008646f776e66616c" +
                    "6c0000000008000863617465676f72790007736176616e6e6100")),
            Map.entry("minecraft:windswept_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653e4ccccd050008646f776e66616c" +
                    "6c3e99999a08000863617465676f7279000d65787472656d655f68696c6c7300")),
            Map.entry("minecraft:windswept_gravelly_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653e4ccccd050008646f776e66616c" +
                    "6c3e99999a08000863617465676f7279000d65787472656d655f68696c6c7300")),
            Map.entry("minecraft:windswept_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653e4ccccd050008646f776e66616c" +
                    "6c3e99999a08000863617465676f7279000d65787472656d655f68696c6c7300")),
            Map.entry("minecraft:windswept_savanna", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726540000000050008646f776e66616c" +
                    "6c0000000008000863617465676f72790007736176616e6e6100")),
            Map.entry("minecraft:jungle", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f733333050008646f776e66616c" +
                    "6c3f66666608000863617465676f727900066a756e676c6500")),
            Map.entry("minecraft:sparse_jungle", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f733333050008646f776e66616c" +
                    "6c3f4ccccd08000863617465676f727900066a756e676c6500")),
            Map.entry("minecraft:bamboo_jungle", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f733333050008646f776e66616c" +
                    "6c3f66666608000863617465676f727900066a756e676c6500")),
            Map.entry("minecraft:badlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000b74656d706572617475726540000000050008646f776e66616c6c000000000800086361" +
                    "7465676f727900046d65736100")),
            Map.entry("minecraft:eroded_badlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000b74656d706572617475726540000000050008646f776e66616c6c000000000800086361" +
                    "7465676f727900046d65736100")),
            Map.entry("minecraft:wooded_badlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000b74656d706572617475726540000000050008646f776e66616c6c000000000800086361" +
                    "7465676f727900046d65736100")),
            Map.entry("minecraft:meadow", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400206d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e6d6561646f770300096d696e5f64656c617900002ee000030009" +
                    "736b795f636f6c6f72007ba4ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200" +
                    "c0d8ff03000b77617465725f636f6c6f72000e4ecf0a000a6d6f6f645f736f756e6403000a7469636b5f64656c617900" +
                    "0017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e" +
                    "63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f00" +
                    "0000050008646f776e66616c6c3f4ccccd08000863617465676f727900086d6f756e7461696e00")),
            Map.entry("minecraft:grove", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e64001f6d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e67726f76650300096d696e5f64656c617900002ee00003000973" +
                    "6b795f636f6c6f720081a0ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0" +
                    "d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c61790000" +
                    "17700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63" +
                    "617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d7065726174757265be4ccc" +
                    "cd050008646f776e66616c6c3f4ccccd08000863617465676f72790006666f7265737400")),
            Map.entry("minecraft:snowy_slopes", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400266d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e736e6f77795f736c6f7065730300096d696e5f64656c61790000" +
                    "2ee000030009736b795f636f6c6f7200829fff03000f77617465725f666f675f636f6c6f7200050533030009666f675f" +
                    "636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f" +
                    "64656c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d" +
                    "6269656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d70657261" +
                    "74757265be99999a050008646f776e66616c6c3f66666608000863617465676f727900086d6f756e7461696e00")),
            Map.entry("minecraft:frozen_peaks", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400266d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e66726f7a656e5f7065616b730300096d696e5f64656c61790000" +
                    "2ee000030009736b795f636f6c6f7200859dff03000f77617465725f666f675f636f6c6f7200050533030009666f675f" +
                    "636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f" +
                    "64656c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d" +
                    "6269656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d70657261" +
                    "74757265bf333333050008646f776e66616c6c3f66666608000863617465676f727900086d6f756e7461696e00")),
            Map.entry("minecraft:jagged_peaks", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400266d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e6a61676765645f7065616b730300096d696e5f64656c61790000" +
                    "2ee000030009736b795f636f6c6f7200859dff03000f77617465725f666f675f636f6c6f7200050533030009666f675f" +
                    "636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f" +
                    "64656c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d" +
                    "6269656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d70657261" +
                    "74757265bf333333050008646f776e66616c6c3f66666608000863617465676f727900086d6f756e7461696e00")),
            Map.entry("minecraft:stony_peaks", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400256d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e73746f6e795f7065616b730300096d696e5f64656c617900002e" +
                    "e000030009736b795f636f6c6f720076a8ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f63" +
                    "6f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64" +
                    "656c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d62" +
                    "69656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d7065726174" +
                    "7572653f800000050008646f776e66616c6c3e99999a08000863617465676f727900086d6f756e7461696e00")),
            Map.entry("minecraft:river", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f72790005726976657200")),
            Map.entry("minecraft:frozen_river", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003938c90a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726500000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f72790005726976657200")),
            Map.entry("minecraft:beach", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720078a7" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f4ccccd050008646f776e66616c" +
                    "6c3ecccccd08000863617465676f72790005626561636800")),
            Map.entry("minecraft:snowy_beach", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653d4ccccd050008646f776e66616c" +
                    "6c3e99999a08000863617465676f72790005626561636800")),
            Map.entry("minecraft:stony_shore", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653e4ccccd050008646f776e66616c" +
                    "6c3e99999a08000863617465676f72790005626561636800")),
            Map.entry("minecraft:warm_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200041f33030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f720043d5ee0a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:lukewarm_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200041633030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f720045adf20a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:deep_lukewarm_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200041633030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f720045adf20a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:deep_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:cold_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:deep_cold_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:frozen_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003938c90a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d706572617475726500000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e08001474656d70657261747572655f6d6f6469666965720006" +
                    "66726f7a656e00")),
            Map.entry("minecraft:deep_frozen_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003938c90a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900056f6365616e08001474656d70657261747572655f6d6f6469666965720006" +
                    "66726f7a656e00")),
            Map.entry("minecraft:mushroom_fields", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f666666050008646f776e66616c" +
                    "6c3f80000008000863617465676f727900086d757368726f6f6d00")),
            Map.entry("minecraft:dripstone_caves", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400296d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e6472697073746f6e655f63617665730300096d696e5f64656c61" +
                    "7900002ee000030009736b795f636f6c6f720078a7ff03000f77617465725f666f675f636f6c6f720005053303000966" +
                    "6f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469" +
                    "636b5f64656c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e656372616674" +
                    "3a616d6269656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d70" +
                    "657261747572653f4ccccd050008646f776e66616c6c3ecccccd08000863617465676f7279000b756e64657267726f75" +
                    "6e6400")),
            Map.entry("minecraft:lush_caves", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400246d696e65" +
                    "63726166743a6d757369632e6f766572776f726c642e6c7573685f63617665730300096d696e5f64656c617900002ee0" +
                    "00030009736b795f636f6c6f72007ba4ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f" +
                    "6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f6465" +
                    "6c6179000017700600066f66667365744000000000000000080005736f756e6400166d696e6563726166743a616d6269" +
                    "656e742e63617665030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d706572617475" +
                    "72653f000000050008646f776e66616c6c3f00000008000863617465676f7279000b756e64657267726f756e6400")),
            Map.entry("minecraft:nether_wastes", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400246d696e65" +
                    "63726166743a6d757369632e6e65746865722e6e65746865725f7761737465730300096d696e5f64656c617900002ee0" +
                    "00030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400246d696e6563726166743a616d62" +
                    "69656e742e6e65746865725f7761737465732e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f756e" +
                    "6400296d696e6563726166743a616d6269656e742e6e65746865725f7761737465732e6164646974696f6e7306000b74" +
                    "69636b5f6368616e63653f86bb98c7e282410003000f77617465725f666f675f636f6c6f7200050533030009666f675f" +
                    "636f6c6f720033080803000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f" +
                    "64656c6179000017700600066f66667365744000000000000000080005736f756e6400246d696e6563726166743a616d" +
                    "6269656e742e6e65746865725f7761737465732e6d6f6f64030013626c6f636b5f7365617263685f657874656e740000" +
                    "0008000005000b74656d706572617475726540000000050008646f776e66616c6c0000000008000863617465676f7279" +
                    "00066e657468657200")),
            Map.entry("minecraft:warped_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400246d696e65" +
                    "63726166743a6d757369632e6e65746865722e7761727065645f666f726573740300096d696e5f64656c617900002ee0" +
                    "00030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400246d696e6563726166743a616d62" +
                    "69656e742e7761727065645f666f726573742e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f756e" +
                    "6400296d696e6563726166743a616d6269656e742e7761727065645f666f726573742e6164646974696f6e7306000b74" +
                    "69636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162696c6974793c69f6a9" +
                    "0a00076f7074696f6e730800047479706500166d696e6563726166743a7761727065645f73706f7265000003000f7761" +
                    "7465725f666f675f636f6c6f7200050533030009666f675f636f6c6f72001a051a03000b77617465725f636f6c6f7200" +
                    "3f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000" +
                    "0000080005736f756e6400246d696e6563726166743a616d6269656e742e7761727065645f666f726573742e6d6f6f64" +
                    "030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d7065726174757265400000000500" +
                    "08646f776e66616c6c0000000008000863617465676f727900066e657468657200")),
            Map.entry("minecraft:crimson_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400256d696e65" +
                    "63726166743a6d757369632e6e65746865722e6372696d736f6e5f666f726573740300096d696e5f64656c617900002e" +
                    "e000030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400256d696e6563726166743a616d" +
                    "6269656e742e6372696d736f6e5f666f726573742e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f" +
                    "756e64002a6d696e6563726166743a616d6269656e742e6372696d736f6e5f666f726573742e6164646974696f6e7306" +
                    "000b7469636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162696c6974793c" +
                    "cccccd0a00076f7074696f6e730800047479706500176d696e6563726166743a6372696d736f6e5f73706f7265000003" +
                    "000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f720033030303000b77617465725f636f" +
                    "6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f66667365744000" +
                    "000000000000080005736f756e6400256d696e6563726166743a616d6269656e742e6372696d736f6e5f666f72657374" +
                    "2e6d6f6f64030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d706572617475726540" +
                    "000000050008646f776e66616c6c0000000008000863617465676f727900066e657468657200")),
            Map.entry("minecraft:soul_sand_valley", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400276d696e65" +
                    "63726166743a6d757369632e6e65746865722e736f756c5f73616e645f76616c6c65790300096d696e5f64656c617900" +
                    "002ee000030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400276d696e6563726166743a" +
                    "616d6269656e742e736f756c5f73616e645f76616c6c65792e6c6f6f700a000f6164646974696f6e735f736f756e6408" +
                    "0005736f756e64002c6d696e6563726166743a616d6269656e742e736f756c5f73616e645f76616c6c65792e61646469" +
                    "74696f6e7306000b7469636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162" +
                    "696c6974793bcccccd0a00076f7074696f6e7308000474797065000d6d696e6563726166743a617368000003000f7761" +
                    "7465725f666f675f636f6c6f7200050533030009666f675f636f6c6f72001b474503000b77617465725f636f6c6f7200" +
                    "3f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000" +
                    "0000080005736f756e6400276d696e6563726166743a616d6269656e742e736f756c5f73616e645f76616c6c65792e6d" +
                    "6f6f64030013626c6f636b5f7365617263685f657874656e7400000008000005000b74656d7065726174757265400000" +
                    "00050008646f776e66616c6c0000000008000863617465676f727900066e657468657200")),
            Map.entry("minecraft:basalt_deltas", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400246d696e65" +
                    "63726166743a6d757369632e6e65746865722e626173616c745f64656c7461730300096d696e5f64656c617900002ee0" +
                    "00030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400246d696e6563726166743a616d62" +
                    "69656e742e626173616c745f64656c7461732e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f756e" +
                    "6400296d696e6563726166743a616d6269656e742e626173616c745f64656c7461732e6164646974696f6e7306000b74" +
                    "69636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162696c6974793df1daeb" +
                    "0a00076f7074696f6e730800047479706500136d696e6563726166743a77686974655f617368000003000f7761746572" +
                    "5f666f675f636f6c6f7200050533030009666f675f636f6c6f7200685f7003000b77617465725f636f6c6f72003f76e4" +
                    "0a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000000008" +
                    "0005736f756e6400246d696e6563726166743a616d6269656e742e626173616c745f64656c7461732e6d6f6f64030013" +
                    "626c6f636b5f7365617263685f657874656e7400000008000005000b74656d706572617475726540000000050008646f" +
                    "776e66616c6c0000000008000863617465676f727900066e657468657200")),
            Map.entry("minecraft:the_end", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900077468655f656e6400")),
            Map.entry("minecraft:end_highlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900077468655f656e6400")),
            Map.entry("minecraft:end_midlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900077468655f656e6400")),
            Map.entry("minecraft:small_end_islands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900077468655f656e6400")),
            Map.entry("minecraft:end_barrens", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000b74656d70657261747572653f000000050008646f776e66616c" +
                    "6c3f00000008000863617465676f727900077468655f656e6400"))
    );

    /**
     * 実バニラ1.17.1のバイオーム element 全81件（名前→element。Join Game codec から取ったもの）。
     * 757→756で写像で埋まらない756のバイオームを埋めるために使う。通常は次の25件が埋められる
     * （757側に写像先が無いもの。snowy_mountains は frozen_peaks の写像先のため含まない）：
     * 756 id 15 minecraft:mushroom_field_shore
     * 756 id 17 minecraft:desert_hills
     * 756 id 18 minecraft:wooded_hills
     * 756 id 19 minecraft:taiga_hills
     * 756 id 20 minecraft:mountain_edge
     * 756 id 22 minecraft:jungle_hills
     * 756 id 28 minecraft:birch_forest_hills
     * 756 id 31 minecraft:snowy_taiga_hills
     * 756 id 33 minecraft:giant_tree_taiga_hills
     * 756 id 39 minecraft:badlands_plateau
     * 756 id 47 minecraft:deep_warm_ocean
     * 756 id 130 minecraft:desert_lakes
     * 756 id 133 minecraft:taiga_mountains
     * 756 id 134 minecraft:swamp_hills
     * 756 id 149 minecraft:modified_jungle
     * 756 id 151 minecraft:modified_jungle_edge
     * 756 id 156 minecraft:tall_birch_hills
     * 756 id 157 minecraft:dark_forest_hills
     * 756 id 158 minecraft:snowy_taiga_mountains
     * 756 id 161 minecraft:giant_spruce_taiga_hills
     * 756 id 162 minecraft:modified_gravelly_mountains
     * 756 id 164 minecraft:shattered_savanna_plateau
     * 756 id 166 minecraft:modified_wooded_badlands_plateau
     * 756 id 167 minecraft:modified_badlands_plateau
     * 756 id 169 minecraft:bamboo_jungle_hills
     * 入力の757 codec が欠けている場合も、ここから埋めるため出力は常に81件の完全な codec になる。
     * Map.of は10件までしか受け付けないため Map.ofEntries を使う。
     */
    private static final Map<String, Tag.CompoundTag> BIOME_756_FILL_ELEMENTS = Map.ofEntries(
            Map.entry("minecraft:ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bf80000005000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:plains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720078a7" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e00000005000b74656d70657261747572653f4c" +
                    "cccd0500057363616c653d4ccccd050008646f776e66616c6c3ecccccd08000863617465676f72790006706c61696e73" +
                    "00")),
            Map.entry("minecraft:desert", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e00000005000b74656d70657261747572654000" +
                    "00000500057363616c653d4ccccd050008646f776e66616c6c0000000008000863617465676f72790006646573657274" +
                    "00")),
            Map.entry("minecraft:mountains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683f80000005000b74656d70657261747572653e4c" +
                    "cccd0500057363616c653f000000050008646f776e66616c6c3e99999a08000863617465676f7279000d65787472656d" +
                    "655f68696c6c7300")),
            Map.entry("minecraft:forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720079a6" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f33" +
                    "33330500057363616c653e4ccccd050008646f776e66616c6c3f4ccccd08000863617465676f72790006666f72657374" +
                    "00")),
            Map.entry("minecraft:taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653e80" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:swamp", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a00076566666563747308001467726173735f636f6c6f725f" +
                    "6d6f64696669657200057377616d70030009736b795f636f6c6f720078a7ff03000d666f6c696167655f636f6c6f7200" +
                    "6a703903000f77617465725f666f675f636f6c6f7200232317030009666f675f636f6c6f7200c0d8ff03000b77617465" +
                    "725f636f6c6f7200617b640a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673" +
                    "65744000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f" +
                    "636b5f7365617263685f657874656e740000000800000500056465707468be4ccccd05000b74656d7065726174757265" +
                    "3f4ccccd0500057363616c653dcccccd050008646f776e66616c6c3f66666608000863617465676f727900057377616d" +
                    "7000")),
            Map.entry("minecraft:river", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bf00000005000b74656d70657261747572653f00" +
                    "00000500057363616c6500000000050008646f776e66616c6c3f00000008000863617465676f72790005726976657200")),
            Map.entry("minecraft:nether_wastes", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400246d696e65" +
                    "63726166743a6d757369632e6e65746865722e6e65746865725f7761737465730300096d696e5f64656c617900002ee0" +
                    "00030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400246d696e6563726166743a616d62" +
                    "69656e742e6e65746865725f7761737465732e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f756e" +
                    "6400296d696e6563726166743a616d6269656e742e6e65746865725f7761737465732e6164646974696f6e7306000b74" +
                    "69636b5f6368616e63653f86bb98c7e282410003000f77617465725f666f675f636f6c6f7200050533030009666f675f" +
                    "636f6c6f720033080803000b77617465725f636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f" +
                    "64656c6179000017700600066f66667365744000000000000000080005736f756e6400246d696e6563726166743a616d" +
                    "6269656e742e6e65746865725f7761737465732e6d6f6f64030013626c6f636b5f7365617263685f657874656e740000" +
                    "0008000005000564657074683dcccccd05000b74656d7065726174757265400000000500057363616c653e4ccccd0500" +
                    "08646f776e66616c6c0000000008000863617465676f727900066e657468657200")),
            Map.entry("minecraft:the_end", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f00" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f00000008000863617465676f727900077468655f656e" +
                    "6400")),
            Map.entry("minecraft:frozen_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003938c90a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bf80000005000b74656d70657261747572650000" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e08" +
                    "001474656d70657261747572655f6d6f646966696572000666726f7a656e00")),
            Map.entry("minecraft:frozen_river", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003938c90a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bf00000005000b74656d70657261747572650000" +
                    "00000500057363616c6500000000050008646f776e66616c6c3f00000008000863617465676f72790005726976657200")),
            Map.entry("minecraft:snowy_tundra", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e00000005000b74656d70657261747572650000" +
                    "00000500057363616c653d4ccccd050008646f776e66616c6c3f00000008000863617465676f7279000369637900")),
            Map.entry("minecraft:snowy_mountains", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572650000" +
                    "00000500057363616c653e99999a050008646f776e66616c6c3f00000008000863617465676f7279000369637900")),
            Map.entry("minecraft:mushroom_fields", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653f66" +
                    "66660500057363616c653e99999a050008646f776e66616c6c3f80000008000863617465676f727900086d757368726f" +
                    "6f6d00")),
            Map.entry("minecraft:mushroom_field_shore", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074680000000005000b74656d70657261747572653f66" +
                    "66660500057363616c653ccccccd050008646f776e66616c6c3f80000008000863617465676f727900086d757368726f" +
                    "6f6d00")),
            Map.entry("minecraft:beach", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720078a7" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074680000000005000b74656d70657261747572653f4c" +
                    "cccd0500057363616c653ccccccd050008646f776e66616c6c3ecccccd08000863617465676f72790005626561636800")),
            Map.entry("minecraft:desert_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572654000" +
                    "00000500057363616c653e99999a050008646f776e66616c6c0000000008000863617465676f72790006646573657274" +
                    "00")),
            Map.entry("minecraft:wooded_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720079a6" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572653f33" +
                    "33330500057363616c653e99999a050008646f776e66616c6c3f4ccccd08000863617465676f72790006666f72657374" +
                    "00")),
            Map.entry("minecraft:taiga_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572653e80" +
                    "00000500057363616c653e99999a050008646f776e66616c6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:mountain_edge", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683f4ccccd05000b74656d70657261747572653e4c" +
                    "cccd0500057363616c653e99999a050008646f776e66616c6c3e99999a08000863617465676f7279000d65787472656d" +
                    "655f68696c6c7300")),
            Map.entry("minecraft:jungle", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f73" +
                    "33330500057363616c653e4ccccd050008646f776e66616c6c3f66666608000863617465676f727900066a756e676c65" +
                    "00")),
            Map.entry("minecraft:jungle_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572653f73" +
                    "33330500057363616c653e99999a050008646f776e66616c6c3f66666608000863617465676f727900066a756e676c65" +
                    "00")),
            Map.entry("minecraft:jungle_edge", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f73" +
                    "33330500057363616c653e4ccccd050008646f776e66616c6c3f4ccccd08000863617465676f727900066a756e676c65" +
                    "00")),
            Map.entry("minecraft:deep_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bfe6666605000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:stone_shore", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653e4c" +
                    "cccd0500057363616c653f4ccccd050008646f776e66616c6c3e99999a08000863617465676f727900046e6f6e6500")),
            Map.entry("minecraft:snowy_beach", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074680000000005000b74656d70657261747572653d4c" +
                    "cccd0500057363616c653ccccccd050008646f776e66616c6c3e99999a08000863617465676f72790005626561636800")),
            Map.entry("minecraft:birch_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007aa5" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f19" +
                    "999a0500057363616c653e4ccccd050008646f776e66616c6c3f19999a08000863617465676f72790006666f72657374" +
                    "00")),
            Map.entry("minecraft:birch_forest_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007aa5" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572653f19" +
                    "999a0500057363616c653e99999a050008646f776e66616c6c3f19999a08000863617465676f72790006666f72657374" +
                    "00")),
            Map.entry("minecraft:dark_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a00076566666563747308001467726173735f636f6c6f725f" +
                    "6d6f646966696572000b6461726b5f666f72657374030009736b795f636f6c6f720079a6ff03000f77617465725f666f" +
                    "675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a" +
                    "6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000000008000573" +
                    "6f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f65787465" +
                    "6e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f3333330500057363616c653e4c" +
                    "cccd050008646f776e66616c6c3f4ccccd08000863617465676f72790006666f7265737400")),
            Map.entry("minecraft:snowy_taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f7200839e" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d7065726174757265bf00" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3ecccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:snowy_taiga_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f7200839e" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d7065726174757265bf00" +
                    "00000500057363616c653e99999a050008646f776e66616c6c3ecccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:giant_tree_taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ca3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653e99" +
                    "999a0500057363616c653e4ccccd050008646f776e66616c6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:giant_tree_taiga_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ca3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572653e99" +
                    "999a0500057363616c653e99999a050008646f776e66616c6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:wooded_mountains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683f80000005000b74656d70657261747572653e4c" +
                    "cccd0500057363616c653f000000050008646f776e66616c6c3e99999a08000863617465676f7279000d65787472656d" +
                    "655f68696c6c7300")),
            Map.entry("minecraft:savanna", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f720075aa" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e00000005000b74656d70657261747572653f99" +
                    "999a0500057363616c653d4ccccd050008646f776e66616c6c0000000008000863617465676f72790007736176616e6e" +
                    "6100")),
            Map.entry("minecraft:savanna_plateau", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f720076a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683fc0000005000b74656d70657261747572653f80" +
                    "00000500057363616c653ccccccd050008646f776e66616c6c0000000008000863617465676f72790007736176616e6e" +
                    "6100")),
            Map.entry("minecraft:badlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000564657074683dcccccd05000b74656d7065726174757265400000000500057363616c65" +
                    "3e4ccccd050008646f776e66616c6c0000000008000863617465676f727900046d65736100")),
            Map.entry("minecraft:wooded_badlands_plateau", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000564657074683fc0000005000b74656d7065726174757265400000000500057363616c65" +
                    "3ccccccd050008646f776e66616c6c0000000008000863617465676f727900046d65736100")),
            Map.entry("minecraft:badlands_plateau", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000564657074683fc0000005000b74656d7065726174757265400000000500057363616c65" +
                    "3ccccccd050008646f776e66616c6c0000000008000863617465676f727900046d65736100")),
            Map.entry("minecraft:small_end_islands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f00" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f00000008000863617465676f727900077468655f656e" +
                    "6400")),
            Map.entry("minecraft:end_midlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f00" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f00000008000863617465676f727900077468655f656e" +
                    "6400")),
            Map.entry("minecraft:end_highlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f00" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f00000008000863617465676f727900077468655f656e" +
                    "6400")),
            Map.entry("minecraft:end_barrens", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72000000" +
                    "0003000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200a080a003000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f00" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f00000008000863617465676f727900077468655f656e" +
                    "6400")),
            Map.entry("minecraft:warm_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200041f33030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f720043d5ee0a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bf80000005000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:lukewarm_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200041633030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f720045adf20a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bf80000005000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:cold_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bf80000005000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:deep_warm_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200041f33030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f720043d5ee0a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bfe6666605000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:deep_lukewarm_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200041633030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f720045adf20a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bfe6666605000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:deep_cold_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bfe6666605000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e00")),
            Map.entry("minecraft:deep_frozen_ocean", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003938c90a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e740000000800000500056465707468bfe6666605000b74656d70657261747572653f00" +
                    "00000500057363616c653dcccccd050008646f776e66616c6c3f00000008000863617465676f727900056f6365616e08" +
                    "001474656d70657261747572655f6d6f646966696572000666726f7a656e00")),
            Map.entry("minecraft:the_void", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f00" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f00000008000863617465676f727900046e6f6e6500")),
            Map.entry("minecraft:sunflower_plains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720078a7" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e00000005000b74656d70657261747572653f4c" +
                    "cccd0500057363616c653d4ccccd050008646f776e66616c6c3ecccccd08000863617465676f72790006706c61696e73" +
                    "00")),
            Map.entry("minecraft:desert_lakes", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e66666605000b74656d70657261747572654000" +
                    "00000500057363616c653e800000050008646f776e66616c6c0000000008000863617465676f72790006646573657274" +
                    "00")),
            Map.entry("minecraft:gravelly_mountains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683f80000005000b74656d70657261747572653e4c" +
                    "cccd0500057363616c653f000000050008646f776e66616c6c3e99999a08000863617465676f7279000d65787472656d" +
                    "655f68696c6c7300")),
            Map.entry("minecraft:flower_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720079a6" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f33" +
                    "33330500057363616c653ecccccd050008646f776e66616c6c3f4ccccd08000863617465676f72790006666f72657374" +
                    "00")),
            Map.entry("minecraft:taiga_mountains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e99999a05000b74656d70657261747572653e80" +
                    "00000500057363616c653ecccccd050008646f776e66616c6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:swamp_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a00076566666563747308001467726173735f636f6c6f725f" +
                    "6d6f64696669657200057377616d70030009736b795f636f6c6f720078a7ff03000d666f6c696167655f636f6c6f7200" +
                    "6a703903000f77617465725f666f675f636f6c6f7200232317030009666f675f636f6c6f7200c0d8ff03000b77617465" +
                    "725f636f6c6f7200617b640a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673" +
                    "65744000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f" +
                    "636b5f7365617263685f657874656e740000000800000500056465707468bdcccccd05000b74656d7065726174757265" +
                    "3f4ccccd0500057363616c653e99999a050008646f776e66616c6c3f66666608000863617465676f727900057377616d" +
                    "7000")),
            Map.entry("minecraft:ice_spikes", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f72007fa1" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ed9999a05000b74656d70657261747572650000" +
                    "00000500057363616c653ee66667050008646f776e66616c6c3f00000008000863617465676f7279000369637900")),
            Map.entry("minecraft:modified_jungle", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653f73" +
                    "33330500057363616c653ecccccd050008646f776e66616c6c3f66666608000863617465676f727900066a756e676c65" +
                    "00")),
            Map.entry("minecraft:modified_jungle_edge", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653f73" +
                    "33330500057363616c653ecccccd050008646f776e66616c6c3f4ccccd08000863617465676f727900066a756e676c65" +
                    "00")),
            Map.entry("minecraft:tall_birch_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007aa5" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653f19" +
                    "999a0500057363616c653ecccccd050008646f776e66616c6c3f19999a08000863617465676f72790006666f72657374" +
                    "00")),
            Map.entry("minecraft:tall_birch_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007aa5" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683f0ccccd05000b74656d70657261747572653f19" +
                    "999a0500057363616c653f000000050008646f776e66616c6c3f19999a08000863617465676f72790006666f72657374" +
                    "00")),
            Map.entry("minecraft:dark_forest_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a00076566666563747308001467726173735f636f6c6f725f" +
                    "6d6f646966696572000b6461726b5f666f72657374030009736b795f636f6c6f720079a6ff03000f77617465725f666f" +
                    "675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a000a" +
                    "6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000000008000573" +
                    "6f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f65787465" +
                    "6e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653f3333330500057363616c653ecc" +
                    "cccd050008646f776e66616c6c3f4ccccd08000863617465676f72790006666f7265737400")),
            Map.entry("minecraft:snowy_taiga_mountains", parseElementHex(
                    "0a08000d70726563697069746174696f6e0004736e6f770a000765666665637473030009736b795f636f6c6f7200839e" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003d57d60a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e99999a05000b74656d7065726174757265bf00" +
                    "00000500057363616c653ecccccd050008646f776e66616c6c3ecccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:giant_spruce_taiga", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653e80" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:giant_spruce_taiga_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da3" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e4ccccd05000b74656d70657261747572653e80" +
                    "00000500057363616c653e4ccccd050008646f776e66616c6c3f4ccccd08000863617465676f72790005746169676100")),
            Map.entry("minecraft:modified_gravelly_mountains", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007da2" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683f80000005000b74656d70657261747572653e4c" +
                    "cccd0500057363616c653f000000050008646f776e66616c6c3e99999a08000863617465676f7279000d65787472656d" +
                    "655f68696c6c7300")),
            Map.entry("minecraft:shattered_savanna", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f720076a9" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683eb9999a05000b74656d70657261747572653f8c" +
                    "cccd0500057363616c653f9ccccd050008646f776e66616c6c0000000008000863617465676f72790007736176616e6e" +
                    "6100")),
            Map.entry("minecraft:shattered_savanna_plateau", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f720076a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683f86666605000b74656d70657261747572653f80" +
                    "00000500057363616c653f9b3334050008646f776e66616c6c0000000008000863617465676f72790007736176616e6e" +
                    "6100")),
            Map.entry("minecraft:eroded_badlands", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000564657074683dcccccd05000b74656d7065726174757265400000000500057363616c65" +
                    "3e4ccccd050008646f776e66616c6c0000000008000863617465676f727900046d65736100")),
            Map.entry("minecraft:modified_wooded_badlands_plateau", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000564657074683ee6666605000b74656d7065726174757265400000000500057363616c65" +
                    "3e99999a050008646f776e66616c6c0000000008000863617465676f727900046d65736100")),
            Map.entry("minecraft:modified_badlands_plateau", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a000765666665637473030009736b795f636f6c6f72006eb1" +
                    "ff03000b67726173735f636f6c6f720090814d03000d666f6c696167655f636f6c6f72009e814d03000f77617465725f" +
                    "666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f636f6c6f72003f76e40a" +
                    "000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f666673657440000000000000000800" +
                    "05736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b5f7365617263685f6578" +
                    "74656e7400000008000005000564657074683ee6666605000b74656d7065726174757265400000000500057363616c65" +
                    "3e99999a050008646f776e66616c6c0000000008000863617465676f727900046d65736100")),
            Map.entry("minecraft:bamboo_jungle", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d70657261747572653f73" +
                    "33330500057363616c653e4ccccd050008646f776e66616c6c3f66666608000863617465676f727900066a756e676c65" +
                    "00")),
            Map.entry("minecraft:bamboo_jungle_hills", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720077a8" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683ee6666605000b74656d70657261747572653f73" +
                    "33330500057363616c653e99999a050008646f776e66616c6c3f66666608000863617465676f727900066a756e676c65" +
                    "00")),
            Map.entry("minecraft:soul_sand_valley", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400276d696e65" +
                    "63726166743a6d757369632e6e65746865722e736f756c5f73616e645f76616c6c65790300096d696e5f64656c617900" +
                    "002ee000030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400276d696e6563726166743a" +
                    "616d6269656e742e736f756c5f73616e645f76616c6c65792e6c6f6f700a000f6164646974696f6e735f736f756e6408" +
                    "0005736f756e64002c6d696e6563726166743a616d6269656e742e736f756c5f73616e645f76616c6c65792e61646469" +
                    "74696f6e7306000b7469636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162" +
                    "696c6974793bcccccd0a00076f7074696f6e7308000474797065000d6d696e6563726166743a617368000003000f7761" +
                    "7465725f666f675f636f6c6f7200050533030009666f675f636f6c6f72001b474503000b77617465725f636f6c6f7200" +
                    "3f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000" +
                    "0000080005736f756e6400276d696e6563726166743a616d6269656e742e736f756c5f73616e645f76616c6c65792e6d" +
                    "6f6f64030013626c6f636b5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b7465" +
                    "6d7065726174757265400000000500057363616c653e4ccccd050008646f776e66616c6c000000000800086361746567" +
                    "6f727900066e657468657200")),
            Map.entry("minecraft:crimson_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400256d696e65" +
                    "63726166743a6d757369632e6e65746865722e6372696d736f6e5f666f726573740300096d696e5f64656c617900002e" +
                    "e000030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400256d696e6563726166743a616d" +
                    "6269656e742e6372696d736f6e5f666f726573742e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f" +
                    "756e64002a6d696e6563726166743a616d6269656e742e6372696d736f6e5f666f726573742e6164646974696f6e7306" +
                    "000b7469636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162696c6974793c" +
                    "cccccd0a00076f7074696f6e730800047479706500176d696e6563726166743a6372696d736f6e5f73706f7265000003" +
                    "000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f720033030303000b77617465725f636f" +
                    "6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f66667365744000" +
                    "000000000000080005736f756e6400256d696e6563726166743a616d6269656e742e6372696d736f6e5f666f72657374" +
                    "2e6d6f6f64030013626c6f636b5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b" +
                    "74656d7065726174757265400000000500057363616c653e4ccccd050008646f776e66616c6c00000000080008636174" +
                    "65676f727900066e657468657200")),
            Map.entry("minecraft:warped_forest", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400246d696e65" +
                    "63726166743a6d757369632e6e65746865722e7761727065645f666f726573740300096d696e5f64656c617900002ee0" +
                    "00030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400246d696e6563726166743a616d62" +
                    "69656e742e7761727065645f666f726573742e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f756e" +
                    "6400296d696e6563726166743a616d6269656e742e7761727065645f666f726573742e6164646974696f6e7306000b74" +
                    "69636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162696c6974793c69f6a9" +
                    "0a00076f7074696f6e730800047479706500166d696e6563726166743a7761727065645f73706f7265000003000f7761" +
                    "7465725f666f675f636f6c6f7200050533030009666f675f636f6c6f72001a051a03000b77617465725f636f6c6f7200" +
                    "3f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000" +
                    "0000080005736f756e6400246d696e6563726166743a616d6269656e742e7761727065645f666f726573742e6d6f6f64" +
                    "030013626c6f636b5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d7065" +
                    "726174757265400000000500057363616c653e4ccccd050008646f776e66616c6c0000000008000863617465676f7279" +
                    "00066e657468657200")),
            Map.entry("minecraft:basalt_deltas", parseElementHex(
                    "0a08000d70726563697069746174696f6e00046e6f6e650a0007656666656374730a00056d757369630100157265706c" +
                    "6163655f63757272656e745f6d75736963000300096d61785f64656c617900005dc0080005736f756e6400246d696e65" +
                    "63726166743a6d757369632e6e65746865722e626173616c745f64656c7461730300096d696e5f64656c617900002ee0" +
                    "00030009736b795f636f6c6f72006eb1ff08000d616d6269656e745f736f756e6400246d696e6563726166743a616d62" +
                    "69656e742e626173616c745f64656c7461732e6c6f6f700a000f6164646974696f6e735f736f756e64080005736f756e" +
                    "6400296d696e6563726166743a616d6269656e742e626173616c745f64656c7461732e6164646974696f6e7306000b74" +
                    "69636b5f6368616e63653f86bb98c7e28241000a00087061727469636c6505000b70726f626162696c6974793df1daeb" +
                    "0a00076f7074696f6e730800047479706500136d696e6563726166743a77686974655f617368000003000f7761746572" +
                    "5f666f675f636f6c6f7200423e42030009666f675f636f6c6f7200685f7003000b77617465725f636f6c6f72003f76e4" +
                    "0a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574400000000000000008" +
                    "0005736f756e6400246d696e6563726166743a616d6269656e742e626173616c745f64656c7461732e6d6f6f64030013" +
                    "626c6f636b5f7365617263685f657874656e7400000008000005000564657074683dcccccd05000b74656d7065726174" +
                    "757265400000000500057363616c653e4ccccd050008646f776e66616c6c0000000008000863617465676f727900066e" +
                    "657468657200")),
            Map.entry("minecraft:dripstone_caves", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f720078a7" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40a000a6d6f6f645f736f756e6403000a7469636b5f64656c6179000017700600066f6666736574" +
                    "4000000000000000080005736f756e6400166d696e6563726166743a616d6269656e742e63617665030013626c6f636b" +
                    "5f7365617263685f657874656e7400000008000005000564657074683e00000005000b74656d70657261747572653f4c" +
                    "cccd0500057363616c653d4ccccd050008646f776e66616c6c3ecccccd08000863617465676f7279000b756e64657267" +
                    "726f756e6400")),
            Map.entry("minecraft:lush_caves", parseElementHex(
                    "0a08000d70726563697069746174696f6e00047261696e0a000765666665637473030009736b795f636f6c6f72007ba4" +
                    "ff03000f77617465725f666f675f636f6c6f7200050533030009666f675f636f6c6f7200c0d8ff03000b77617465725f" +
                    "636f6c6f72003f76e40005000564657074683dcccccd05000b74656d70657261747572653f0000000500057363616c65" +
                    "3e4ccccd050008646f776e66616c6c3f00000008000863617465676f7279000b756e64657267726f756e6400"))
    );

    /**
     * 756の dimension（NBTの値）に対応する dimension_type の名前を返す。
     * dimensionName757 と同じ手順で、既定の element は756のものを使う。
     * （756と757の overworld／overworld_caves の element は logical_height／min_y／height が違うため、
     * 757の既定値では一致しない。）codec が null の場合（Respawn）は codec での検索を飛ばす。
     */
    private static String dimensionName756(Tag dimension, Tag.CompoundTag codec) {
        if (codec != null) {
            String fromCodec = findDimensionName(codec, dimension);
            if (fromCodec != null) {
                return fromCodec;
            }
        }
        for (Map.Entry<String, Tag.CompoundTag> e : DIMENSION_TYPE_756_ELEMENTS.entrySet()) {
            if (e.getValue().equals(dimension)) {
                return e.getKey();
            }
        }
        // 推定：effects と has_ceiling の組み合わせで4種を区別する（dimensionName757 と同じ）。
        if (dimension instanceof Tag.CompoundTag compound) {
            String effects = compound.getString("effects").orElse("");
            boolean hasCeiling = compound.get("has_ceiling")
                    .filter(t -> t instanceof Tag.ByteTag)
                    .map(t -> ((Tag.ByteTag) t).value() != 0)
                    .orElse(false);
            if (effects.equals("minecraft:the_nether")) {
                return "minecraft:the_nether";
            }
            if (effects.equals("minecraft:the_end")) {
                return "minecraft:the_end";
            }
            if (effects.equals("minecraft:overworld")) {
                return hasCeiling ? "minecraft:overworld_caves" : "minecraft:overworld";
            }
        }
        LOG.warning("dimension の名前が分からなかったため minecraft:overworld にします。");
        return "minecraft:overworld";
    }

    /**
     * 756の dimension_type の名前に対応する756の element を返す。
     * 既定の756 element を使う。名前が既定に無く、codec に element があればそれを使い、
     * それも無ければ overworld の element にする。
     */
    private static Tag dimensionValue756(String name, Tag.CompoundTag codec) {
        Tag.CompoundTag element = DIMENSION_TYPE_756_ELEMENTS.get(name);
        if (element != null) {
            return element;
        }
        if (codec != null) {
            Tag fromCodec = findDimensionElement(codec, name);
            if (fromCodec != null) {
                return fromCodec;
            }
        }
        LOG.warning("dimension " + name + " の756 element が無いため minecraft:overworld にします。");
        return DIMENSION_TYPE_756_ELEMENTS.get("minecraft:overworld");
    }

    /** 756の dimension_type の名前に対応する756の element を返す（codec が無い場合。Respawn 用）。 */
    private static Tag dimensionValue756(String name) {
        return dimensionValue756(name, null);
    }

    /** レジストリのエントリ列の1件から id を抜く。無ければ -1。 */
    private static int registryEntryId(Tag.CompoundTag compound) {
        return compound.get("id")
                .filter(t -> t instanceof Tag.IntTag)
                .map(t -> ((Tag.IntTag) t).value())
                .orElse(-1);
    }

    /** バイオームのレジストリエントリ1件を作る。element が null の場合は element 無し。 */
    private static Tag.CompoundTag biomeEntry(String name, int id, Tag element) {
        Map<String, Tag> entry = new LinkedHashMap<>();
        entry.put("name", new Tag.StringTag(name));
        entry.put("id", new Tag.IntTag(id));
        if (element != null) {
            entry.put("element", element);
        }
        return new Tag.CompoundTag(entry);
    }

    /** バイオーム element から depth・scale を落とす（756→757。1.18の element には無い）。他のフィールドは順番も含めてそのまま。 */
    private static Tag.CompoundTag stripDepthScale(Tag.CompoundTag element) {
        Map<String, Tag> rebuilt = new LinkedHashMap<>();
        element.value().forEach((k, v) -> {
            if (!k.equals("depth") && !k.equals("scale")) {
                rebuilt.put(k, v);
            }
        });
        return new Tag.CompoundTag(rebuilt);
    }

    /**
     * バイオーム element に depth・scale を足す（757→756。1.17.1の element は depth・scale を要する）。
     * 実バニラ1.17.1の並び（precipitation, effects, depth, temperature, scale, downfall, category）に合わせ、
     * depth は effects の直後、scale は temperature の直後に入れる。anchor が無い場合は末尾に足す。
     */
    private static Tag.CompoundTag addDepthScale(Tag.CompoundTag element, float depth, float scale) {
        Map<String, Tag> rebuilt = new LinkedHashMap<>();
        boolean depthAdded = false;
        boolean scaleAdded = false;
        for (Map.Entry<String, Tag> e : element.value().entrySet()) {
            rebuilt.put(e.getKey(), e.getValue());
            if (e.getKey().equals("effects")) {
                rebuilt.put("depth", new Tag.FloatTag(depth));
                depthAdded = true;
            } else if (e.getKey().equals("temperature")) {
                rebuilt.put("scale", new Tag.FloatTag(scale));
                scaleAdded = true;
            }
        }
        if (!depthAdded) {
            rebuilt.put("depth", new Tag.FloatTag(depth));
        }
        if (!scaleAdded) {
            rebuilt.put("scale", new Tag.FloatTag(scale));
        }
        return new Tag.CompoundTag(rebuilt);
    }

    /**
     * 756のバイオームのエントリ列を757順に並べ替える。
     * RegistryRemapper.mapBiome756To757 で写す。element は depth・scale を落として引き継ぐ。
     * 同じ757 id になるものが複数ある場合、逆写像（mapBiome757To756）が指す正規の元を優先する
     * （正規の元が後から来たら上書きする。それ以外は先勝ち）。これが無いと windswept_forest
     * （757 id 20）で wooded_hills（756 id 18）の element が残り、往復で wooded_mountains
     * （756 id 34）が wooded_hills の element に化けてしまう（逆写像は34を指す）。
     * 写像で埋まらない757のバイオームは実バニラ1.18の element（BIOME_757_FILL_ELEMENTS）で
     * 埋め、61件にする。
     */
    private static List<Tag> reorderBiomeEntries756To757(List<Tag> entries) {
        Tag[] out = new Tag[BIOME_757_NAMES.length];
        for (Tag entry : entries) {
            if (!(entry instanceof Tag.CompoundTag compound)) {
                continue;
            }
            int srcId = registryEntryId(compound);
            if (srcId < 0 || srcId >= BIOME_756_NAMES.length) {
                continue;
            }
            int dstId = RegistryRemapper.mapBiome756To757(srcId);
            if (dstId < 0 || dstId >= out.length) {
                continue;
            }
            boolean canonical = RegistryRemapper.mapBiome757To756(dstId) == srcId;
            if (out[dstId] != null && !canonical) {
                continue;
            }
            Tag element = compound.get("element").orElse(null);
            Tag stripped = element instanceof Tag.CompoundTag elementCompound
                    ? stripDepthScale(elementCompound) : element;
            out[dstId] = biomeEntry(BIOME_757_NAMES[dstId], dstId, stripped);
        }
        for (int i = 0; i < out.length; i++) {
            if (out[i] == null) {
                Tag.CompoundTag element = BIOME_757_FILL_ELEMENTS.get(BIOME_757_NAMES[i]);
                if (element == null) {
                    continue; // fill 表に無い（ありえないはず）
                }
                out[i] = biomeEntry(BIOME_757_NAMES[i], i, element);
            }
        }
        return List.of(out);
    }

    /**
     * 757のバイオームのエントリ列を756順に並べ替える。
     * RegistryRemapper.mapBiome757To756 で写す。element は写像先の756バイオームの depth・scale を
     * 足して引き継ぐ。同じ756 id になるものが複数ある場合、逆写像（mapBiome756To757）が指す
     * 正規の元を優先する（756→757側と同じ規則。こちらの衝突4組は先勝ちでも正規の元が残るが、
     * 入力の並びに依存しないよう同じ規則にしている）。
     * 写像で埋まらない756のバイオームは実バニラ1.17.1の element（BIOME_756_FILL_ELEMENTS）で
     * 埋め、81件にする。返り値は756 id の昇順（実バニラ1.17.1の codec と同じ並び）。
     */
    private static List<Tag> reorderBiomeEntries757To756(List<Tag> entries) {
        Tag[] out = new Tag[BIOME_756_NAMES.length];
        for (Tag entry : entries) {
            if (!(entry instanceof Tag.CompoundTag compound)) {
                continue;
            }
            int srcId = registryEntryId(compound);
            if (srcId < 0 || srcId >= BIOME_757_NAMES.length) {
                continue;
            }
            int dstId = RegistryRemapper.mapBiome757To756(srcId);
            if (dstId < 0 || dstId >= out.length) {
                continue;
            }
            boolean canonical = RegistryRemapper.mapBiome756To757(dstId) == srcId;
            if (out[dstId] != null && !canonical) {
                continue;
            }
            String name = BIOME_756_NAMES[dstId];
            if (name == null) {
                continue; // 756の隙間 id（表の上ではありえないはずだが安全のため）
            }
            float[] depthScale = BIOME_757_DEPTH_SCALE[srcId];
            Tag element = compound.get("element").orElse(null);
            Tag withDepthScale = element instanceof Tag.CompoundTag elementCompound
                    ? addDepthScale(elementCompound, depthScale[0], depthScale[1]) : element;
            out[dstId] = biomeEntry(name, dstId, withDepthScale);
        }
        for (int i = 0; i < out.length; i++) {
            if (out[i] == null && BIOME_756_NAMES[i] != null) {
                Tag.CompoundTag element = BIOME_756_FILL_ELEMENTS.get(BIOME_756_NAMES[i]);
                if (element == null) {
                    continue; // fill 表に無い（ありえないはず）
                }
                out[i] = biomeEntry(BIOME_756_NAMES[i], i, element);
            }
        }
        List<Tag> result = new ArrayList<>();
        for (Tag tag : out) {
            if (tag != null) {
                result.add(tag);
            }
        }
        return result;
    }

    /**
     * 756の dimensionCodec を757形式にする。dimension_type の element を757既定値に置き換え、
     * biome を757順に並べ替える（depth・scale を落とす。757にしか無い6件は実バニラ1.18の element で補う）。
     * codec のキーの並びは実バニラ通り（dimension_type、worldgen/biome）。
     */
    public static Tag.CompoundTag translateDimensionCodec756To757(Tag.CompoundTag codec756) {
        Map<String, Tag.CompoundTag> registries = registriesFromCodec(codec756);
        Map<String, Tag> codec = new LinkedHashMap<>();
        Tag.CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
        if (dimensionRegistry != null) {
            List<Tag> entries = substituteDimensionTypeElements(
                    dimensionRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()),
                    ProtocolVersion.V1_18);
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:dimension_type"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:dimension_type", new Tag.CompoundTag(rebuilt));
        }
        Tag.CompoundTag biomeRegistry = registries.get("minecraft:worldgen/biome");
        if (biomeRegistry != null) {
            List<Tag> entries = reorderBiomeEntries756To757(
                    biomeRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()));
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:worldgen/biome"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:worldgen/biome", new Tag.CompoundTag(rebuilt));
        }
        return new Tag.CompoundTag(codec);
    }

    /**
     * 757の dimensionCodec を756形式にする。dimension_type の element を756既定値に置き換え、
     * biome を756順に並べ替える（depth・scale を足す。写像で埋まらない25件は実バニラ1.17.1の element で補う）。
     * codec のキーの並びは実バニラ通り（dimension_type、worldgen/biome）。
     */
    public static Tag.CompoundTag translateDimensionCodec757To756(Tag.CompoundTag codec757) {
        Map<String, Tag.CompoundTag> registries = registriesFromCodec(codec757);
        Map<String, Tag> codec = new LinkedHashMap<>();
        Tag.CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
        if (dimensionRegistry != null) {
            List<Tag> entries = substituteDimensionTypeElements(
                    dimensionRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()),
                    ProtocolVersion.V1_17_1);
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:dimension_type"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:dimension_type", new Tag.CompoundTag(rebuilt));
        }
        Tag.CompoundTag biomeRegistry = registries.get("minecraft:worldgen/biome");
        if (biomeRegistry != null) {
            List<Tag> entries = reorderBiomeEntries757To756(
                    biomeRegistry.getList("value").map(Tag.ListTag::value).orElse(List.of()));
            Map<String, Tag> rebuilt = new LinkedHashMap<>();
            rebuilt.put("type", new Tag.StringTag("minecraft:worldgen/biome"));
            rebuilt.put("value", new Tag.ListTag(Nbt.COMPOUND, entries));
            codec.put("minecraft:worldgen/biome", new Tag.CompoundTag(rebuilt));
        }
        return new Tag.CompoundTag(codec);
    }

    // ------------------------------------------------------------------
    // Join Game
    // ------------------------------------------------------------------

    /** 756の Join Game の中身。757と違い simulationDistance が無い。dimension は DimensionType の値そのもの（NBT）。previousGameMode は i8（-1＝なし）。 */
    public record JoinGame756(int entityId, boolean hardcore, int gameMode, int previousGameMode,
            List<String> worldNames, Tag.CompoundTag dimensionCodec, Tag dimension,
            String worldName, long hashedSeed, int maxPlayers, int viewDistance,
            boolean reducedDebug, boolean enableRespawnScreen,
            boolean isDebug, boolean isFlat) {
    }

    /**
     * 756の Join Game を読む。順番は entityId, hardcore, gameMode, previousGameMode(i8),
     * worldNames, dimensionCodec（名前付き NBT）, dimension（名前付き NBT）,
     * worldName, hashedSeed, maxPlayers, viewDistance,
     * reducedDebug, enableRespawnScreen, isDebug, isFlat である
     * （minecraft-data mc-1.17.1-protocol.json の packet_login とライブキャプチャで確認）。
     */
    public static JoinGame756 parseJoinGame756(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        checkPreviousGameMode757(previousGameMode);
        List<String> worldNames = PacketIO.readStringList(reader);
        Tag dimensionCodec = reader.readNamedNbt().tag();
        if (!(dimensionCodec instanceof Tag.CompoundTag codec)) {
            throw new IllegalArgumentException("Join Game の dimensionCodec がコンパウンドではありません。");
        }
        Tag dimension = reader.readNamedNbt().tag();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        requireEnd(reader);
        return new JoinGame756(entityId, hardcore, gameMode, previousGameMode, worldNames,
                codec, dimension, worldName, hashedSeed, maxPlayers, viewDistance,
                reducedDebug, enableRespawnScreen, isDebug, isFlat);
    }

    /** 756の Join Game を書く。 */
    public static byte[] writeJoinGame756(JoinGame756 join) {
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeNamedNbt("", join.dimensionCodec());
        writer.writeNamedNbt("", join.dimension());
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        return writer.bytes();
    }

    /**
     * Join Game（756 → 757）。dimension は NBT の値から codec 内の名前を引き、757の element に直す。
     * dimensionCodec は757形式に直す。simulationDistance は viewDistance と同じ値にする
     * （1.17.1に simulationDistance は無く、実質のシミュレーション距離は描画距離だったため）。
     * 読めないペイロードは素通しする（このファイルの他の変換器と同じ方針）。
     */
    public static byte[] translateJoinGame756To757(byte[] payload) {
        try {
            JoinGame756 join = parseJoinGame756(payload);
            String dimensionName = dimensionName756(join.dimension(), join.dimensionCodec());
            return writeJoinGame757(new JoinGame757(join.entityId(), join.hardcore(),
                    join.gameMode(), join.previousGameMode(), join.worldNames(),
                    translateDimensionCodec756To757(join.dimensionCodec()),
                    dimensionValue759(dimensionName),
                    join.worldName(), join.hashedSeed(), join.maxPlayers(), join.viewDistance(),
                    join.viewDistance(), join.reducedDebug(), join.enableRespawnScreen(),
                    join.isDebug(), join.isFlat()));
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Join Game（757 → 756）。dimension は名前から756の element を引き、
     * dimensionCodec は756形式に直す。simulationDistance は落とす。
     * 読めないペイロードは素通しする。
     */
    public static byte[] translateJoinGame757To756(byte[] payload) {
        try {
            JoinGame757 join = parseJoinGame757(payload);
            String dimensionName = dimensionName757(join.dimension(), join.dimensionCodec());
            return writeJoinGame756(new JoinGame756(join.entityId(), join.hardcore(),
                    join.gameMode(), join.previousGameMode(), join.worldNames(),
                    translateDimensionCodec757To756(join.dimensionCodec()),
                    dimensionValue756(dimensionName),
                    join.worldName(), join.hashedSeed(), join.maxPlayers(), join.viewDistance(),
                    join.reducedDebug(), join.enableRespawnScreen(),
                    join.isDebug(), join.isFlat()));
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // Respawn
    // ------------------------------------------------------------------

    /** 756の Respawn（SpawnInfo）の中身。757と同一の8フィールド。dimension は DimensionType の値そのもの（NBT）。previousGameMode は u8（Join Game の i8 とは違う。protocol.json で確認）。copyMetadata は末尾。dataToKeep は無い。 */
    public record SpawnInfo756(Tag dimension, String worldName, long hashedSeed, int gameMode,
            int previousGameMode, boolean isDebug, boolean isFlat, boolean copyMetadata) {
    }

    /** 756の SpawnInfo を読む。 */
    public static SpawnInfo756 readSpawnInfo756(Reader reader) {
        Tag dimension = reader.readNamedNbt().tag();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readUnsignedByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean copyMetadata = reader.readBoolean();
        return new SpawnInfo756(dimension, worldName, hashedSeed, gameMode,
                previousGameMode, isDebug, isFlat, copyMetadata);
    }

    /** 756の SpawnInfo を書く。 */
    public static void writeSpawnInfo756(Writer writer, SpawnInfo756 info) {
        writer.writeNamedNbt("", info.dimension());
        writer.writeString(info.worldName());
        writer.writeLong(info.hashedSeed());
        writer.writeByte(info.gameMode());
        writer.writeByte(info.previousGameMode());
        writer.writeBoolean(info.isDebug());
        writer.writeBoolean(info.isFlat());
        writer.writeBoolean(info.copyMetadata());
    }

    /**
     * Respawn（756 → 757）。フィールドは同一のため、dimension の element だけ757既定値に直す
     * （overworld／overworld_caves の height が違う）。copyMetadata はそのまま写す。
     * previousGameMode は u8 のまま写す（範囲検査はしない。Join Game の i8 と違い、"無し" の符号が protocol.json には無いため）。
     * 読めないペイロードは素通しする。
     */
    public static byte[] translateRespawn756To757(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            SpawnInfo756 info = readSpawnInfo756(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writeSpawnInfo757(writer, new SpawnInfo757(
                    dimensionValue759(dimensionName756(info.dimension(), null)),
                    info.worldName(), info.hashedSeed(), info.gameMode(),
                    info.previousGameMode(), info.isDebug(), info.isFlat(),
                    info.copyMetadata()));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Respawn（757 → 756）。フィールドは同一のため、dimension の element だけ756既定値に直す。
     * copyMetadata はそのまま写す。読めないペイロードは素通しする。
     */
    public static byte[] translateRespawn757To756(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            SpawnInfo757 info = readSpawnInfo757(reader);
            requireEnd(reader);
            Writer writer = new Writer();
            writeSpawnInfo756(writer, new SpawnInfo756(
                    dimensionValue756(dimensionName757(info.dimension(), null)),
                    info.worldName(), info.hashedSeed(), info.gameMode(),
                    info.previousGameMode(), info.isDebug(), info.isFlat(),
                    info.copyMetadata()));
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // Client Information（Settings）
    // ------------------------------------------------------------------

    /**
     * Client Information（756 → 757）。756は {locale, viewDistance i8, chatFlags varint,
     * chatColors bool, skinParts u8, mainHand varint, disableTextFiltering bool} の7フィールド、
     * 757は末尾に enableServerListing bool が付いた8フィールド。
     * disableTextFiltering は反転して enableTextFiltering にする。
     * enableServerListing は true で補完する（1.18以前のクライアントに一覧表示の拒否手段は無かったため。
     * 実バニラ1.18クライアントの送信値は未確認）。読めないペイロードは素通しする。
     */
    public static byte[] translateClientInformation756To757(byte[] payload) {
        if (payload == null) {
            return payload;
        }
        try {
            Reader reader = new Reader(payload);
            String locale = reader.readString();
            int viewDistance = reader.readByte();
            int chatFlags = reader.readVarInt();
            boolean chatColors = reader.readBoolean();
            int skinParts = reader.readUnsignedByte();
            int mainHand = reader.readVarInt();
            boolean disableTextFiltering = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Client Information に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString(locale);
            writer.writeByte(viewDistance);
            writer.writeVarInt(chatFlags);
            writer.writeBoolean(chatColors);
            writer.writeUnsignedByte(skinParts);
            writer.writeVarInt(mainHand);
            writer.writeBoolean(!disableTextFiltering); // enableTextFiltering
            writer.writeBoolean(true); // enableServerListing：756には無いため true を補う
            return writer.bytes();
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return payload;
        }
    }

    /**
     * Client Information（757 → 756）。末尾の enableServerListing を落とし、
     * enableTextFiltering を反転して disableTextFiltering にする。読めないペイロードは素通しする。
     */
    public static byte[] translateClientInformation757To756(byte[] payload) {
        if (payload == null) {
            return payload;
        }
        try {
            Reader reader = new Reader(payload);
            String locale = reader.readString();
            int viewDistance = reader.readByte();
            int chatFlags = reader.readVarInt();
            boolean chatColors = reader.readBoolean();
            int skinParts = reader.readUnsignedByte();
            int mainHand = reader.readVarInt();
            boolean enableTextFiltering = reader.readBoolean();
            boolean enableServerListing = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Client Information に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeString(locale);
            writer.writeByte(viewDistance);
            writer.writeVarInt(chatFlags);
            writer.writeBoolean(chatColors);
            writer.writeUnsignedByte(skinParts);
            writer.writeVarInt(mainHand);
            writer.writeBoolean(!enableTextFiltering); // disableTextFiltering
            return writer.bytes();
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // 755（1.17）⇔ 756（1.17.1）
    // ------------------------------------------------------------------
    //
    // 素通し節。根拠は実測のみ（推測なし）：
    // - mc-1.17-protocol.json と mc-1.17.1-protocol.json の全173パケットの突き合わせ
    //   （.tmp-755/stage-b-play/diff-protocol-755-756.py）。packet_login／packet_respawn／
    //   packet_settings はフィールド構成まで完全同一。IDも同一（Join Game 0x26、Respawn 0x3d、
    //   Client Information 0x05、Update Time 0x58）。
    // - STAGE-A-755-NOTES.md §11.6：実バニラ1.17と1.17.1の Join Game は両方31,555バイトで、
    //   差分は entityId 1バイト＋hashedSeed 8バイトのみ。dimensionCodec の dimension_type 4件・
    //   バイオーム81件とも同一順序。レジストリ差分も0件（§11.1）。
    // - 違いが実測で見つかったのは play の4パケットだけ（Set Slot／Window Items／Window Click／
    //   Edit Book。いずれも stateId の有無などの窓系フィールド差分で、IDは同一）。これらは
    //   PlayTranslator の担当外（Set Slot／Window Items／Window Click は ChunkTranslator、
    //   Edit Book は未対応）で、本節では扱わない。
    // したがって本節の変換器はすべてペイロード unchanged（素通し）である。読めないペイロードも
    // 素通しする（このファイルの他の変換器と同じ方針）。

    /**
     * 755の dimensionCodec を756形式にする。レジストリ差分は0件のため素通し（同一インスタンスを返す）。
     * 根拠：STAGE-A-755-NOTES.md §11.1（差分0件。dimension_type 4件・バイオーム81件とも同一順序・同一内容）
     * と protocol.json の packet_login 突き合わせ（dimensionCodec は両方 nbt）。
     */
    public static Tag.CompoundTag translateDimensionCodec755To756(Tag.CompoundTag codec755) {
        return codec755;
    }

    /**
     * 756の dimensionCodec を755形式にする。レジストリ差分は0件のため素通し（同一インスタンスを返す）。
     * 根拠は {@link #translateDimensionCodec755To756(Tag.CompoundTag)} と同じ。
     */
    public static Tag.CompoundTag translateDimensionCodec756To755(Tag.CompoundTag codec756) {
        return codec756;
    }

    // ------------------------------------------------------------------
    // Join Game（755）
    // ------------------------------------------------------------------

    /** 755の Join Game の中身。756と同一の15フィールド。dimension は DimensionType の値そのもの（NBT）。previousGameMode は i8（-1＝なし）。simulationDistance は無い。 */
    public record JoinGame755(int entityId, boolean hardcore, int gameMode, int previousGameMode,
            List<String> worldNames, Tag.CompoundTag dimensionCodec, Tag dimension,
            String worldName, long hashedSeed, int maxPlayers, int viewDistance,
            boolean reducedDebug, boolean enableRespawnScreen,
            boolean isDebug, boolean isFlat) {
    }

    /**
     * 755の Join Game を読む。順番は entityId, hardcore, gameMode, previousGameMode(i8),
     * worldNames, dimensionCodec（名前付き NBT）, dimension（名前付き NBT）,
     * worldName, hashedSeed, maxPlayers, viewDistance,
     * reducedDebug, enableRespawnScreen, isDebug, isFlat である
     * （minecraft-data mc-1.17-protocol.json の packet_login と mc-1.17.1-protocol.json の
     * packet_login が完全同一であること、および STAGE-A-755-NOTES.md §11.6 の実バニラ
     * 1.17キャプチャ（31,555バイト）で確認）。
     */
    public static JoinGame755 parseJoinGame755(byte[] payload) {
        Reader reader = new Reader(payload);
        int entityId = reader.readInt();
        boolean hardcore = reader.readBoolean();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readByte();
        checkPreviousGameMode757(previousGameMode);
        List<String> worldNames = PacketIO.readStringList(reader);
        Tag dimensionCodec = reader.readNamedNbt().tag();
        if (!(dimensionCodec instanceof Tag.CompoundTag codec)) {
            throw new IllegalArgumentException("Join Game の dimensionCodec がコンパウンドではありません。");
        }
        Tag dimension = reader.readNamedNbt().tag();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int maxPlayers = reader.readVarInt();
        int viewDistance = reader.readVarInt();
        boolean reducedDebug = reader.readBoolean();
        boolean enableRespawnScreen = reader.readBoolean();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        requireEnd(reader);
        return new JoinGame755(entityId, hardcore, gameMode, previousGameMode, worldNames,
                codec, dimension, worldName, hashedSeed, maxPlayers, viewDistance,
                reducedDebug, enableRespawnScreen, isDebug, isFlat);
    }

    /** 755の Join Game を書く。756と同一のフィールド順。 */
    public static byte[] writeJoinGame755(JoinGame755 join) {
        Writer writer = new Writer();
        writer.writeInt(join.entityId());
        writer.writeBoolean(join.hardcore());
        writer.writeByte(join.gameMode());
        writer.writeByte(join.previousGameMode());
        PacketIO.writeStringList(writer, join.worldNames());
        writer.writeNamedNbt("", join.dimensionCodec());
        writer.writeNamedNbt("", join.dimension());
        writer.writeString(join.worldName());
        writer.writeLong(join.hashedSeed());
        writer.writeVarInt(join.maxPlayers());
        writer.writeVarInt(join.viewDistance());
        writer.writeBoolean(join.reducedDebug());
        writer.writeBoolean(join.enableRespawnScreen());
        writer.writeBoolean(join.isDebug());
        writer.writeBoolean(join.isFlat());
        return writer.bytes();
    }

    /**
     * Join Game（755 → 756）。パケットID（0x26）も codec も同一のため素通し（ペイロード unchanged）。
     * 根拠：protocol.json の packet_login 完全同一と STAGE-A-755-NOTES.md §11.6 の実測
     * （両 Join Game は31,555バイトで、差分は entityId 1バイト＋hashedSeed 8バイトのみ）。
     * 検証のためパースだけ行い、読めないペイロードも素通しする。
     */
    public static byte[] translateJoinGame755To756(byte[] payload) {
        try {
            parseJoinGame755(payload);
            return payload;
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Join Game（756 → 755）。パケットID（0x26）も codec も同一のため素通し（ペイロード unchanged）。
     * 根拠は {@link #translateJoinGame755To756(byte[])} と同じ。読めないペイロードも素通しする。
     */
    public static byte[] translateJoinGame756To755(byte[] payload) {
        try {
            parseJoinGame756(payload);
            return payload;
        } catch (IllegalArgumentException e) {
            LOG.warning("Join Game を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // Respawn（755）
    // ------------------------------------------------------------------

    /** 755の Respawn（SpawnInfo）の中身。756と同一の8フィールド。dimension は DimensionType の値そのもの（NBT）。previousGameMode は u8（Join Game の i8 とは違う。protocol.json で確認）。copyMetadata は末尾。dataToKeep は無い。 */
    public record SpawnInfo755(Tag dimension, String worldName, long hashedSeed, int gameMode,
            int previousGameMode, boolean isDebug, boolean isFlat, boolean copyMetadata) {
    }

    /** 755の SpawnInfo を読む。756と同一のフィールド順。 */
    public static SpawnInfo755 readSpawnInfo755(Reader reader) {
        Tag dimension = reader.readNamedNbt().tag();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gameMode = reader.readUnsignedByte();
        int previousGameMode = reader.readUnsignedByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        boolean copyMetadata = reader.readBoolean();
        return new SpawnInfo755(dimension, worldName, hashedSeed, gameMode,
                previousGameMode, isDebug, isFlat, copyMetadata);
    }

    /** 755の SpawnInfo を書く。756と同一のフィールド順。 */
    public static void writeSpawnInfo755(Writer writer, SpawnInfo755 info) {
        writer.writeNamedNbt("", info.dimension());
        writer.writeString(info.worldName());
        writer.writeLong(info.hashedSeed());
        writer.writeByte(info.gameMode());
        writer.writeByte(info.previousGameMode());
        writer.writeBoolean(info.isDebug());
        writer.writeBoolean(info.isFlat());
        writer.writeBoolean(info.copyMetadata());
    }

    /**
     * Respawn（755 → 756）。パケットID（0x3d）もフィールドも同一のため素通し（ペイロード unchanged）。
     * 根拠：protocol.json の packet_respawn 完全同一（dimension nbt、worldName、hashedSeed i64、
     * gamemode u8、previousGamemode u8、isDebug、isFlat、copyMetadata）と STAGE-A-755-NOTES.md
     * §11.6（Respawn も同一）。検証のためパースだけ行い、読めないペイロードも素通しする。
     */
    public static byte[] translateRespawn755To756(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            readSpawnInfo755(reader);
            requireEnd(reader);
            return payload;
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Respawn（756 → 755）。パケットID（0x3d）もフィールドも同一のため素通し（ペイロード unchanged）。
     * 根拠は {@link #translateRespawn755To756(byte[])} と同じ。読めないペイロードも素通しする。
     */
    public static byte[] translateRespawn756To755(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            readSpawnInfo756(reader);
            requireEnd(reader);
            return payload;
        } catch (IllegalArgumentException e) {
            LOG.warning("Respawn を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }

    // ------------------------------------------------------------------
    // Client Information（Settings、755）
    // ------------------------------------------------------------------

    /**
     * Client Information（755 → 756）。フィールド同一のため素通し（ペイロード unchanged）。
     * 根拠：protocol.json の packet_settings が両バージョンで完全同一（locale string、
     * viewDistance i8、chatFlags varint、chatColors bool、skinParts u8、mainHand varint、
     * disableTextFiltering bool の7フィールド。756→757であった enableTextFiltering への
     * 反転や enableServerListing の補完は、755⇔756間では不要）。
     * IDも両方0x05。読めないペイロードも素通しする。
     */
    public static byte[] translateClientInformation755To756(byte[] payload) {
        return payload;
    }

    /**
     * Client Information（756 → 755）。フィールド同一のため素通し（ペイロード unchanged）。
     * 根拠は {@link #translateClientInformation755To756(byte[])} と同じ。IDも両方0x05。
     */
    public static byte[] translateClientInformation756To755(byte[] payload) {
        return payload;
    }

}
