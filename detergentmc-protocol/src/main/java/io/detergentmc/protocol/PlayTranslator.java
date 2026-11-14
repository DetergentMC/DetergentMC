package io.detergentmc.protocol;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
            out.set(targetId, new Tag.CompoundTag(entry));
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


}
