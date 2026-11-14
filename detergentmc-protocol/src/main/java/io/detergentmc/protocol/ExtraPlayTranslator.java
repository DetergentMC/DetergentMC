package io.detergentmc.protocol;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * これまで763向けに落としていたプレイ状態のパケットのペイロード変換
 * （純粋関数。Netty を使わない）。担当3（Respawn／落としパケット）。
 *
 * <p>情報源は PrismarineJS minecraft-data の protocol.json（1.20＝763、
 * 1.20.2／1.20.3＝765。765は1.20.4と共通）と wiki.vg の Protocol。
 * フィールドの順番と型はすべてそこから取った。1.20.2 の定義も確認し、
 * 1.20.2で設定フェーズが追加されたときの Respawn の変化（後述）を
 * 1.20／1.20.2／1.20.3 の3点で突き合わせた。
 *
 * <p>このクラスが扱う変換：
 * <ul>
 *   <li>Respawn（765 0x45 → 763 0x41）：フィールドは両バージョンで
 *       同じものだけ。違いは {@code copyMetadata} の位置だけ。
 *       763では {@code isFlat} の直後（death の前）、765では末尾
 *       （portalCooldown の後）。dimension は両バージョンとも文字列の
 *       識別子で、1.20.2の設定フェーズ追加で VarInt には変わっていない
 *       （変わったのは Join Game の dimension まわりで、Respawn は
 *       対象外。1.20／1.20.2／1.20.3 の protocol.json で確認済み）。
 *       そのため Respawn の変換にレジストリは要らない。</li>
 *   <li>次元移動と設定フェーズ：バニラは次元移動のときに Start
 *       Configuration を送らない。Respawn（プレイ状態）＋チャンク＋
 *       位置同期だけで次元が変わる。したがって通常の次元移動では
 *       設定フェーズの往復は発生せず、この Respawn 変換だけで足りる。
 *       設定フェーズの往復が必要になるのは、プレイ中にサーバーが
 *       Start Configuration（765 0x67）を送ってきたときだけ
 *       （データパックの再読み込みなど）で、その肩代わりは
 *       {@link ProxySession} が行う（763クライアントはプレイ状態の
 *       まま、プロキシがサーバー側の設定フェーズを消費する）。
 *       Start Configuration 自体に763の対応パケットは無い。</li>
 *   <li>Boss Bar（765 0x0A → 763 0x0B）：title だけ NBT→JSON 文字列。
 *       それ以外（UUID、action、health、color、dividers、flags）は同じ。
 *       action ごとの有無（title は 0／3、health は 0／2、color・
 *       dividers は 0／4、flags は 0／5）は両バージョンで同じ。</li>
 *   <li>Teams（765 0x5E → 763 0x5A）：表示名（name）・接頭辞
 *       （prefix）・接尾辞（suffix）だけ NBT→JSON 文字列。それ以外
 *       （friendlyFire は i8、nameTagVisibility・collisionRule は文字列、
 *       formatting は VarInt、players は文字列配列）は同じ。
 *       mode ごとの有無（表示まわりは 0／2、players は 0／3／4）は
 *       両バージョンで同じ。</li>
 *   <li>Tab Complete（765 0x10 → 763 0x0F）：補完候補の tooltip だけ
 *       NBT→JSON 文字列（option 型のまま）。transactionId・start・
 *       length・match は同じ。</li>
 *   <li>Explosion（765 0x1E → 763 0x1D）：共通部分（座標・半径・
 *       影響ブロックのオフセット・プレイヤーの速度）はそのまま通す。
 *       765だけの末尾（block_interaction_type、粒子×2、音）は
 *       763に相当するフィールドが無い。ただし無言で落とすのではなく、
 *       末尾を読み飛ばして（読まないとパケットの終わりが分からない）
 *       共通部分だけを送る。763クライアントは Explosion パケットを
 *       受け取れば自分の既定の爆発粒子・爆発音で演出するので、爆発
 *       そのもの（位置・半径・ノックバック・ブロック破壊）は伝わる。
 *       失われるのはサーバー指定の粒子と音だけである。</li>
 *   <li>Advancements（765 0x70 → 763 0x69）：表示の title・description
 *       だけ NBT→JSON 文字列。アイコン（slot）はバイト列をそのまま
 *       写す（アイテムIDはレジストリ番号のため、1.20.3追加分は
 *       ずれる可能性がある。Window Items などと同じ既知の制限）。
 *       765には無い {@code criteria} は763では必須のため、空配列を
 *       書く。進捗そのものは progressMapping で別に送られるので、
 *       達成状態・トーストは保たれる。criteria が空でもクライアントは
 *       クラッシュしない（表示上の条件一覧が出ないだけ）。
 *       764向け（{@link #translateAdvancements765To764(byte[])}）は
 *       criteria が無い点が765と同じなので、空配列は書かない。
 *       それ以外は763向けと同じ変換である。</li>
 * </ul>
 *
 * <p>逆向き（764サーバー→765クライアント）の変換は {@code *764To765}
 * のメソッドで行う。764と765は両方とも設定フェーズがあるため、
 * 763のときのような設定フェーズの消費は不要で、Respawn・
 * Start Configuration は形式が同じ（minecraft-data の 1.20.2 と
 * 1.20.3 の protocol.json で確認済み）ためそのまま通す。
 * テキストは764の JSON 文字列から765の NBT へ {@link JsonToNbt} で
 * 変換する。Explosion は764に無い末尾（block_interaction_type・
 * 粒子×2・音）を合成する（代替・無害化の方針は
 * {@link #translateExplosion764To765(byte[])} を参照）。
 *
 * <p>無言で落とす実装はここには無い。変換できないフィールドは上の通り
 * 代替・無害化の方針を取る。パケット自体が壊れていて読めない場合
 * （途中で切れている、末尾に余分なバイトがある）は
 * {@link IllegalArgumentException} を投げ、呼び出し側
 * （{@link ProxySession}）がそのパケットだけを落としてログに残す。
 * 壊れたパケットを無理に通してクライアントをクラッシュさせるより、
 * 1個だけ落とす方が安全である。
 */
public final class ExtraPlayTranslator {

    private ExtraPlayTranslator() {
    }

    // ------------------------------------------------------------------
    // Respawn（765 → 763）
    // ------------------------------------------------------------------

    /**
     * 765の Respawn（0x45）を763形式（0x41）に変換する。
     *
     * <p>765の順番：dimension, worldName, hashedSeed, gamemode(i8),
     * previousGamemode(u8), isDebug, isFlat, death(option), portalCooldown,
     * copyMetadata。763の順番：同じだが copyMetadata が isFlat の直後
     * （death の前）。それ以外のフィールドは型も順番も同じ。
     */
    public static byte[] translateRespawn765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        String dimension = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gamemode = reader.readByte();
        int previousGamemode = reader.readUnsignedByte();
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
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Respawn に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(dimension);
        writer.writeString(worldName);
        writer.writeLong(hashedSeed);
        writer.writeByte(gamemode);
        writer.writeByte(previousGamemode);
        writer.writeBoolean(isDebug);
        writer.writeBoolean(isFlat);
        // 763では copyMetadata が death の前（765では末尾）。
        writer.writeBoolean(copyMetadata);
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(deathDimension);
            writer.writePositionRaw(deathLocation);
        }
        writer.writeVarInt(portalCooldown);
        return writer.bytes();
    }

    /**
     * 764の Respawn（0x44）を763形式（0x41）に変換する。
     * 764と765で Respawn の形式は同じ（copyMetadata は両方とも末尾）ため、
     * {@link #translateRespawn765To763(byte[])} をそのまま使える。
     */
    public static byte[] translateRespawn764To763(byte[] payload) {
        return translateRespawn765To763(payload);
    }

    /**
     * 763の Respawn（0x41）を764形式（0x44）に変換する。
     * 763では copyMetadata が isFlat の直後（death の前）、764では末尾
     * （portalCooldown の後）。それ以外のフィールドは型も順番も同じ。
     */
    public static byte[] translateRespawn763To764(byte[] payload) {
        return translateRespawn763(payload);
    }

    /** Respawn（763 → 765）。763→764と同じ形式。 */
    public static byte[] translateRespawn763To765(byte[] payload) {
        return translateRespawn763(payload);
    }

    /** 763の Respawn を読んで764／765形式（copyMetadata は末尾）で書く。 */
    private static byte[] translateRespawn763(byte[] payload) {
        Reader reader = new Reader(payload);
        String dimension = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gamemode = reader.readByte();
        int previousGamemode = reader.readUnsignedByte();
        boolean isDebug = reader.readBoolean();
        boolean isFlat = reader.readBoolean();
        // 763では copyMetadata が death の前（764／765では末尾）。
        boolean copyMetadata = reader.readBoolean();
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
                    "Respawn に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(dimension);
        writer.writeString(worldName);
        writer.writeLong(hashedSeed);
        writer.writeByte(gamemode);
        writer.writeByte(previousGamemode);
        writer.writeBoolean(isDebug);
        writer.writeBoolean(isFlat);
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(deathDimension);
            writer.writePositionRaw(deathLocation);
        }
        writer.writeVarInt(portalCooldown);
        writer.writeBoolean(copyMetadata);
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Boss Bar（765 → 763）
    // ------------------------------------------------------------------

    /**
     * 765の Boss Bar（0x0A）を763形式（0x0B）に変換する。
     * title（action 0＝追加、3＝タイトル更新）だけ NBT→JSON 文字列。
     */
    public static byte[] translateBossBar765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        UUID uuid = reader.readUuid();
        int action = reader.readVarInt();
        String titleJson = null;
        if (action == 0 || action == 3) {
            titleJson = Nbt.toJsonString(reader.readAnonymousNbt().tag());
        }
        Float health = null;
        if (action == 0 || action == 2) {
            health = reader.readFloat();
        }
        Integer color = null;
        if (action == 0 || action == 4) {
            color = reader.readVarInt();
        }
        Integer dividers = null;
        if (action == 0 || action == 4) {
            dividers = reader.readVarInt();
        }
        Integer flags = null;
        if (action == 0 || action == 5) {
            flags = reader.readUnsignedByte();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Boss Bar に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeUuid(uuid);
        writer.writeVarInt(action);
        if (titleJson != null) {
            writer.writeString(titleJson);
        }
        if (health != null) {
            writer.writeFloat(health);
        }
        if (color != null) {
            writer.writeVarInt(color);
        }
        if (dividers != null) {
            writer.writeVarInt(dividers);
        }
        if (flags != null) {
            writer.writeByte(flags);
        }
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Teams（765 → 763）
    // ------------------------------------------------------------------

    /**
     * 765の Teams（0x5E）を763形式（0x5A）に変換する。
     * name・prefix・suffix（mode 0＝作成、2＝更新）だけ NBT→JSON 文字列。
     */
    public static byte[] translateTeams765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        String team = reader.readString();
        int mode = reader.readByte();
        String nameJson = null;
        Integer friendlyFire = null;
        String nameTagVisibility = null;
        String collisionRule = null;
        Integer formatting = null;
        String prefixJson = null;
        String suffixJson = null;
        boolean hasInfo = mode == 0 || mode == 2;
        if (hasInfo) {
            nameJson = Nbt.toJsonString(reader.readAnonymousNbt().tag());
            friendlyFire = (int) reader.readByte(); // i8。両バージョンとも同じ。
            nameTagVisibility = reader.readString();
            collisionRule = reader.readString();
            formatting = reader.readVarInt();
            prefixJson = Nbt.toJsonString(reader.readAnonymousNbt().tag());
            suffixJson = Nbt.toJsonString(reader.readAnonymousNbt().tag());
        }
        List<String> players = null;
        if (mode == 0 || mode == 3 || mode == 4) {
            players = PacketIO.readStringList(reader);
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Teams に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(team);
        writer.writeByte(mode);
        if (hasInfo) {
            writer.writeString(nameJson);
            writer.writeByte(friendlyFire);
            writer.writeString(nameTagVisibility);
            writer.writeString(collisionRule);
            writer.writeVarInt(formatting);
            writer.writeString(prefixJson);
            writer.writeString(suffixJson);
        }
        if (players != null) {
            PacketIO.writeStringList(writer, players);
        }
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Tab Complete（765 → 763）
    // ------------------------------------------------------------------

    /**
     * 765の Tab Complete（0x10：補完候補）を763形式（0x0F）に変換する。
     * 各候補の tooltip（option 型）だけ NBT→JSON 文字列。
     */
    public static byte[] translateTabComplete765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        int transactionId = reader.readVarInt();
        int start = reader.readVarInt();
        int length = reader.readVarInt();
        int count = reader.readVarInt();
        List<String> matches = new ArrayList<>(count);
        List<String> tooltips = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            matches.add(reader.readString());
            if (reader.readBoolean()) {
                tooltips.add(Nbt.toJsonString(reader.readAnonymousNbt().tag()));
            } else {
                tooltips.add(null);
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Tab Complete に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(transactionId);
        writer.writeVarInt(start);
        writer.writeVarInt(length);
        writer.writeVarInt(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            writer.writeString(matches.get(i));
            writer.writeBoolean(tooltips.get(i) != null);
            if (tooltips.get(i) != null) {
                writer.writeString(tooltips.get(i));
            }
        }
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Explosion（765 → 763）
    // ------------------------------------------------------------------

    /**
     * 765の Explosion（0x1E）を763形式（0x1D）に変換する。
     *
     * <p>共通部分（座標 f64×3、半径 f32、影響ブロックのオフセット配列、
     * プレイヤーの速度 f32×3）はそのまま通す。765だけの末尾は
     * 763に無いので落とすが、そのまま捨てるのではなく
     * {@link #skipParticle765(Reader)} と
     * {@link #skipSoundHolder(Reader)} で正しく読み飛ばす
     * （読まずに捨てると、どこまでがこのパケットか分からない）。
     * 763クライアントは Explosion パケット自体に既定の爆発粒子・爆発音が
     * 紐づいているので、爆発の演出は失われない。失われるのはサーバーが
     * 指定した粒子と音だけである（代替・無害化の方針）。
     */
    public static byte[] translateExplosion765To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeDouble(reader.readDouble()); // x
        writer.writeDouble(reader.readDouble()); // y
        writer.writeDouble(reader.readDouble()); // z
        writer.writeFloat(reader.readFloat()); // radius
        int blockCount = reader.readVarInt();
        writer.writeVarInt(blockCount);
        for (int i = 0; i < blockCount; i++) {
            writer.writeByte(reader.readByte());
            writer.writeByte(reader.readByte());
            writer.writeByte(reader.readByte());
        }
        writer.writeFloat(reader.readFloat()); // playerMotionX
        writer.writeFloat(reader.readFloat()); // playerMotionY
        writer.writeFloat(reader.readFloat()); // playerMotionZ
        // ---- 765だけの末尾。763には無いので読んで捨てる。 ----
        reader.readVarInt(); // block_interaction_type
        skipParticle765(reader); // small_explosion_particle
        skipParticle765(reader); // large_explosion_particle
        skipSoundHolder(reader); // sound
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Explosion に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return writer.bytes();
    }

    /**
     * 765の Particle 1個を読み飛ばす。Particle は VarInt の粒子ID＋
     * 粒子の種類ごとのデータ（wiki.vg の Particle 型）。
     * データの有無と大きさは minecraft-data 1.20.3 の
     * {@code particleData} から取った。1.20.3で追加された粒子
     * （gust 系など）の分だけIDがずれているので、763の表ではなく
     * 765の表で引くこと。載っていないIDはデータ無しとして扱う。
     * バニラの Explosion で実際に送られる粒子（explosion／
     * explosion_emitter）はデータ無しなので、通常はここを通らない。
     */
    static void skipParticle765(Reader reader) {
        int id = reader.readVarInt();
        switch (id) {
            case 2, 3, 27 -> reader.readVarInt(); // block／block_marker／falling_dust：blockState
            case 14 -> { // dust：色（r, g, b）＋大きさ
                reader.readFloat();
                reader.readFloat();
                reader.readFloat();
                reader.readFloat();
            }
            case 15 -> { // dust_color_transition：開始色＋大きさ＋終了色
                for (int i = 0; i < 7; i++) {
                    reader.readFloat();
                }
            }
            case 33 -> reader.readFloat(); // cherry_leaves：回転
            case 42 -> skipSlot(reader); // item：スロット
            case 43 -> skipVibration(reader); // vibration
            case 96 -> reader.readVarInt(); // shriek：表示までの遅延
            default -> {
                // データ無しの粒子（explosion、explosion_emitter など）。
            }
        }
    }

    /** 765の vibration 粒子のデータを読み飛ばす。 */
    private static void skipVibration(Reader reader) {
        String positionType = reader.readString();
        boolean isEntity = "minecraft:entity".equals(positionType);
        if (isEntity) {
            reader.readVarInt(); // entityId
            reader.readVarInt(); // entityEyeHeight
        }
        if ("minecraft:block".equals(positionType)) {
            reader.readPositionRaw(); // destination（ブロック位置）
        } else if (isEntity) {
            reader.readVarInt(); // destination（エンティティID）
        }
        reader.readVarInt(); // ticks
    }

    /**
     * 765の ItemSoundHolder（registryEntryHolder）を読み飛ばす。
     * VarInt が 0 ならインライン定義（音名＋任意の固定範囲）が続き、
     * 0 以外ならレジストリID（＋1 された値）だけで終わる。
     */
    static void skipSoundHolder(Reader reader) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            reader.readString(); // soundName
            if (reader.readBoolean()) {
                reader.readFloat(); // fixedRange
            }
        }
    }

    // ------------------------------------------------------------------
    // Advancements（765 → 763）
    // ------------------------------------------------------------------

    /**
     * 765の Advancements（0x70）を763形式（0x69）に変換する。
     *
     * <p>差分は2か所。表示の title・description は NBT→JSON 文字列。
     * {@code criteria} は765には無く763では必須なので、空配列を書く
     * （進捗は progressMapping で別に送られるため、達成状態は保たれる。
     * 空でもクライアントはクラッシュしない）。
     * アイコン（slot）はバイト列をそのまま写す。
     */
    public static byte[] translateAdvancements765To763(byte[] payload) {
        return translateAdvancements765(payload, true);
    }

    /**
     * 765の Advancements（0x70）を764形式（0x6C）に変換する。
     *
     * <p>764の Advancements は765と同じく {@code criteria} が無い。
     * そのため差分は表示の title・description の NBT→JSON 文字列だけで、
     * 空の criteria は書かない。それ以外は763向けと同じ変換である。
     */
    public static byte[] translateAdvancements765To764(byte[] payload) {
        return translateAdvancements765(payload, false);
    }

    /** {@link #translateAdvancements765To763(byte[])} と {@link #translateAdvancements765To764(byte[])} の共通処理。 */
    private static byte[] translateAdvancements765(byte[] payload, boolean writeCriteria) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeBoolean(reader.readBoolean()); // reset
        int mappingCount = reader.readVarInt();
        writer.writeVarInt(mappingCount);
        for (int i = 0; i < mappingCount; i++) {
            writer.writeString(reader.readString()); // key
            // parentId（option）
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            // displayData（option）
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeString(Nbt.toJsonString(reader.readAnonymousNbt().tag())); // title
                writer.writeString(Nbt.toJsonString(reader.readAnonymousNbt().tag())); // description
                writer.writeBytes(copySlotRaw(reader, payload)); // icon
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt(); // 表示フラグ（i32）
                writer.writeInt(flags);
                // flags の最下位ビット（0x01）が背景テクスチャの有無。
                if ((flags & 0x01) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // xCord
                writer.writeFloat(reader.readFloat()); // yCord
            }
            // 765には criteria が無い。763では空配列を書く（無害化）。
            // 764にも criteria は無いので書かない。
            if (writeCriteria) {
                writer.writeVarInt(0);
            }
            // requirements：文字列配列の配列
            int requirementCount = reader.readVarInt();
            writer.writeVarInt(requirementCount);
            for (int r = 0; r < requirementCount; r++) {
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
            }
            writer.writeBoolean(reader.readBoolean()); // sendsTelemetryData
        }
        // identifiers（削除する進捗のID一覧）
        PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
        // progressMapping
        int progressCount = reader.readVarInt();
        writer.writeVarInt(progressCount);
        for (int i = 0; i < progressCount; i++) {
            writer.writeString(reader.readString()); // key
            int criterionCount = reader.readVarInt();
            writer.writeVarInt(criterionCount);
            for (int c = 0; c < criterionCount; c++) {
                writer.writeString(reader.readString()); // criterionIdentifier
                boolean hasProgress = reader.readBoolean();
                writer.writeBoolean(hasProgress);
                if (hasProgress) {
                    writer.writeLong(reader.readLong()); // criterionProgress
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Advancements に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Advancements（764 → 763）
    // ------------------------------------------------------------------

    /**
     * 764の Advancements（0x6C）を763形式（0x69）に変換する。
     *
     * <p>表示の title・description は763も764も JSON 文字列のため
     * そのまま通す。アイコン（slot）だけ
     * {@link PlayTranslator#translateSlot764To763(Reader, Writer)}
     * で変換する（アイテムIDの付け替えと NBT の名前付け）。
     * {@code criteria} は764には無く763では必須なので、空配列を書く
     * （進捗は progressMapping で別に送られるため、達成状態は保たれる。
     * 空でもクライアントはクラッシュしない）。
     */
    public static byte[] translateAdvancements764To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeBoolean(reader.readBoolean()); // reset
        int mappingCount = reader.readVarInt();
        writer.writeVarInt(mappingCount);
        for (int i = 0; i < mappingCount; i++) {
            writer.writeString(reader.readString()); // key
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeString(reader.readString()); // title（JSON 文字列のまま）
                writer.writeString(reader.readString()); // description（JSON 文字列のまま）
                PlayTranslator.translateSlot764To763(reader, writer); // icon
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt(); // 表示フラグ（i32）
                writer.writeInt(flags);
                if ((flags & 0x01) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // xCord
                writer.writeFloat(reader.readFloat()); // yCord
            }
            // 764には criteria が無い。763では空配列を書く（無害化）。
            writer.writeVarInt(0);
            int requirementCount = reader.readVarInt();
            writer.writeVarInt(requirementCount);
            for (int r = 0; r < requirementCount; r++) {
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
            }
            writer.writeBoolean(reader.readBoolean()); // sendsTelemetryData
        }
        PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
        int progressCount = reader.readVarInt();
        writer.writeVarInt(progressCount);
        for (int i = 0; i < progressCount; i++) {
            writer.writeString(reader.readString()); // key
            int criterionCount = reader.readVarInt();
            writer.writeVarInt(criterionCount);
            for (int c = 0; c < criterionCount; c++) {
                writer.writeString(reader.readString()); // criterionIdentifier
                boolean hasProgress = reader.readBoolean();
                writer.writeBoolean(hasProgress);
                if (hasProgress) {
                    writer.writeLong(reader.readLong()); // criterionProgress
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Advancements に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // Advancements（763 → 764／765）
    // ------------------------------------------------------------------

    /**
     * 763の Advancements（0x69）を764形式（0x6C）に変換する。
     * 表示の title・description を JSON 文字列から NBT へ変換する
     * （{@link JsonToNbt}）。{@code criteria} は764には無いため落とす
     * （達成条件の内訳はクライアントに送らない。進捗の達成状態そのものは
     * progressMapping で送られる）。アイコン（slot）はアイテムIDを
     * 763→764へ変換する。
     */
    public static byte[] translateAdvancements763To764(byte[] payload) {
        return translateAdvancements763(payload, false);
    }

    /** Advancements（763 → 765）。763→764と同じ形式で、スロットだけ765向け。 */
    public static byte[] translateAdvancements763To765(byte[] payload) {
        return translateAdvancements763(payload, true);
    }

    /** {@link #translateAdvancements763To764(byte[])} と {@link #translateAdvancements763To765(byte[])} の共通処理。 */
    private static byte[] translateAdvancements763(byte[] payload, boolean to765) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeBoolean(reader.readBoolean()); // reset
        int mappingCount = reader.readVarInt();
        writer.writeVarInt(mappingCount);
        for (int i = 0; i < mappingCount; i++) {
            writer.writeString(reader.readString()); // key
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeAnonymousNbt(JsonToNbt.fromJson(reader.readString())); // title
                writer.writeAnonymousNbt(JsonToNbt.fromJson(reader.readString())); // description
                if (to765) {
                    PlayTranslator.translateSlot763To765(reader, writer); // icon
                } else {
                    PlayTranslator.translateSlot763To764(reader, writer); // icon
                }
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt(); // 表示フラグ（i32）
                writer.writeInt(flags);
                if ((flags & 0x01) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // xCord
                writer.writeFloat(reader.readFloat()); // yCord
            }
            // 763の criteria は764／765には無いため読んで捨てる。
            PacketIO.readStringList(reader);
            int requirementCount = reader.readVarInt();
            writer.writeVarInt(requirementCount);
            for (int r = 0; r < requirementCount; r++) {
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
            }
            writer.writeBoolean(reader.readBoolean()); // sendsTelemetryData
        }
        PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
        int progressCount = reader.readVarInt();
        writer.writeVarInt(progressCount);
        for (int i = 0; i < progressCount; i++) {
            writer.writeString(reader.readString()); // key
            int criterionCount = reader.readVarInt();
            writer.writeVarInt(criterionCount);
            for (int c = 0; c < criterionCount; c++) {
                writer.writeString(reader.readString()); // criterionIdentifier
                boolean hasProgress = reader.readBoolean();
                writer.writeBoolean(hasProgress);
                if (hasProgress) {
                    writer.writeLong(reader.readLong()); // criterionProgress
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Advancements に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return writer.bytes();
    }

    // ------------------------------------------------------------------
    // 逆向き（764→765）。サーバーが764、クライアントが765の場合。
    // ------------------------------------------------------------------

    /**
     * 764の Respawn を765形式に変換する。
     * 764と765で形式は同じ（copyMetadata は両方とも末尾。
     * minecraft-data の 1.20.2 と 1.20.3 の packet_respawn は同一）のため、
     * そのまま返す。IDの付け替えは {@link PacketTranslator} が行う。
     * 次元移動でも設定フェーズの往復は発生しない（Respawn＋チャンク＋
     * 位置同期だけで次元が変わる点は下向きと同じ）。
     */
    public static byte[] translateRespawn764To765(byte[] payload) {
        return payload.clone();
    }

    /**
     * 764の Start Configuration（プレイ中に設定フェーズへ戻る要求）を
     * 765形式に変換する。ペイロードは無く、両バージョンとも設定フェーズが
     * あるため、そのまま返す。IDの付け替えは {@link PacketTranslator}
     * が行う。763のときのようなプロキシによる設定フェーズの肩代わりは
     * 不要である。
     */
    public static byte[] translateStartConfiguration764To765(byte[] payload) {
        return payload.clone();
    }

    /**
     * 764の Boss Bar を765形式に変換する。
     * title（action 0＝追加、3＝タイトル更新）だけ JSON 文字列→NBT。
     */
    public static byte[] translateBossBar764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        UUID uuid = reader.readUuid();
        int action = reader.readVarInt();
        String titleJson = null;
        if (action == 0 || action == 3) {
            titleJson = reader.readString();
        }
        Float health = null;
        if (action == 0 || action == 2) {
            health = reader.readFloat();
        }
        Integer color = null;
        if (action == 0 || action == 4) {
            color = reader.readVarInt();
        }
        Integer dividers = null;
        if (action == 0 || action == 4) {
            dividers = reader.readVarInt();
        }
        Integer flags = null;
        if (action == 0 || action == 5) {
            flags = reader.readUnsignedByte();
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Boss Bar に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeUuid(uuid);
        writer.writeVarInt(action);
        if (titleJson != null) {
            writer.writeAnonymousNbt(JsonToNbt.fromJson(titleJson));
        }
        if (health != null) {
            writer.writeFloat(health);
        }
        if (color != null) {
            writer.writeVarInt(color);
        }
        if (dividers != null) {
            writer.writeVarInt(dividers);
        }
        if (flags != null) {
            writer.writeByte(flags);
        }
        return writer.bytes();
    }

    /**
     * 764の Teams を765形式に変換する。
     * name・prefix・suffix（mode 0＝作成、2＝更新）だけ JSON 文字列→NBT。
     */
    public static byte[] translateTeams764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        String team = reader.readString();
        int mode = reader.readByte();
        String nameJson = null;
        Integer friendlyFire = null;
        String nameTagVisibility = null;
        String collisionRule = null;
        Integer formatting = null;
        String prefixJson = null;
        String suffixJson = null;
        boolean hasInfo = mode == 0 || mode == 2;
        if (hasInfo) {
            nameJson = reader.readString();
            friendlyFire = (int) reader.readByte();
            nameTagVisibility = reader.readString();
            collisionRule = reader.readString();
            formatting = reader.readVarInt();
            prefixJson = reader.readString();
            suffixJson = reader.readString();
        }
        List<String> players = null;
        if (mode == 0 || mode == 3 || mode == 4) {
            players = PacketIO.readStringList(reader);
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Teams に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(team);
        writer.writeByte(mode);
        if (hasInfo) {
            writer.writeAnonymousNbt(JsonToNbt.fromJson(nameJson));
            writer.writeByte(friendlyFire);
            writer.writeString(nameTagVisibility);
            writer.writeString(collisionRule);
            writer.writeVarInt(formatting);
            writer.writeAnonymousNbt(JsonToNbt.fromJson(prefixJson));
            writer.writeAnonymousNbt(JsonToNbt.fromJson(suffixJson));
        }
        if (players != null) {
            PacketIO.writeStringList(writer, players);
        }
        return writer.bytes();
    }

    /**
     * 764の Tab Complete（補完候補）を765形式に変換する。
     * 各候補の tooltip（option 型）だけ JSON 文字列→NBT。
     */
    public static byte[] translateTabComplete764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        int transactionId = reader.readVarInt();
        int start = reader.readVarInt();
        int length = reader.readVarInt();
        int count = reader.readVarInt();
        List<String> matches = new ArrayList<>(count);
        List<String> tooltips = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            matches.add(reader.readString());
            if (reader.readBoolean()) {
                tooltips.add(reader.readString());
            } else {
                tooltips.add(null);
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Tab Complete に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(transactionId);
        writer.writeVarInt(start);
        writer.writeVarInt(length);
        writer.writeVarInt(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            writer.writeString(matches.get(i));
            writer.writeBoolean(tooltips.get(i) != null);
            if (tooltips.get(i) != null) {
                writer.writeAnonymousNbt(JsonToNbt.fromJson(tooltips.get(i)));
            }
        }
        return writer.bytes();
    }

    /**
     * 764の Explosion を765形式に変換する。
     *
     * <p>共通部分（座標・半径・影響ブロックのオフセット・プレイヤーの速度）は
     * そのまま通す。764に無い末尾は合成する（代替・無害化の方針。
     * 落とすのではなく、765クライアントが期待する形で補う）：
     * <ul>
     *   <li>block_interaction_type は 2（破壊＋減衰あり）。764の爆発は
     *       影響ブロックを破壊するため、バニラの実体爆発の既定値である
     *       2を選ぶ（0＝維持・1＝破壊では、764サーバーが送った
     *       影響ブロック一覧と食い違う）。</li>
     *   <li>small_explosion_particle は explosion（765 id 23）、
     *       large_explosion_particle は explosion_emitter（765 id 22）。
     *       どちらも追加データは無い。764クライアントが既定で出す
     *       爆発演出と同じものである。</li>
     *   <li>sound は直書きの {@code minecraft:entity.generic.explode}
     *       （固定範囲なし）。764の Explosion には音の指定が無く、
     *       764クライアントは既定の爆発音を出すため、それと同じ音を
     *       指定する。</li>
     * </ul>
     * 764サーバーが送った爆発の位置・半径・ノックバック・ブロック破壊は
     * すべて保たれる。失われる情報は無い（764側に元々無い指定を、
     * 764クライアントの既定の演出で補っているだけである）。
     */
    public static byte[] translateExplosion764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeDouble(reader.readDouble()); // x
        writer.writeDouble(reader.readDouble()); // y
        writer.writeDouble(reader.readDouble()); // z
        writer.writeFloat(reader.readFloat()); // radius
        int blockCount = reader.readVarInt();
        writer.writeVarInt(blockCount);
        for (int i = 0; i < blockCount; i++) {
            writer.writeByte(reader.readByte());
            writer.writeByte(reader.readByte());
            writer.writeByte(reader.readByte());
        }
        writer.writeFloat(reader.readFloat()); // playerMotionX
        writer.writeFloat(reader.readFloat()); // playerMotionY
        writer.writeFloat(reader.readFloat()); // playerMotionZ
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Explosion に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        // ---- 764に無い末尾。765クライアントの既定の演出で合成する。 ----
        writer.writeVarInt(2); // block_interaction_type：破壊＋減衰あり
        writer.writeVarInt(23); // small_explosion_particle：explosion（データ無し）
        writer.writeVarInt(22); // large_explosion_particle：explosion_emitter（データ無し）
        writer.writeVarInt(0); // sound：直書き
        writer.writeString("minecraft:entity.generic.explode");
        writer.writeBoolean(false); // 固定範囲なし
        return writer.bytes();
    }

    /**
     * 764の Advancements を765形式に変換する。
     *
     * <p>差分は2か所。表示の title・description は JSON 文字列→NBT。
     * アイコン（slot）は764のアイテムIDを765に変換する（下向きでは
     * バイト列をそのまま写していたが、上向きでは反転表があるため
     * 正しく変換できる）。764にも765にも {@code criteria} は無いので
     * 何も書かない。
     */
    public static byte[] translateAdvancements764To765(byte[] payload) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeBoolean(reader.readBoolean()); // reset
        int mappingCount = reader.readVarInt();
        writer.writeVarInt(mappingCount);
        for (int i = 0; i < mappingCount; i++) {
            writer.writeString(reader.readString()); // key
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeAnonymousNbt(JsonToNbt.fromJson(reader.readString())); // title
                writer.writeAnonymousNbt(JsonToNbt.fromJson(reader.readString())); // description
                translateSlot764To765(reader, writer); // icon
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt(); // 表示フラグ（i32）
                writer.writeInt(flags);
                if ((flags & 0x01) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // xCord
                writer.writeFloat(reader.readFloat()); // yCord
            }
            int requirementCount = reader.readVarInt();
            writer.writeVarInt(requirementCount);
            for (int r = 0; r < requirementCount; r++) {
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
            }
            writer.writeBoolean(reader.readBoolean()); // sendsTelemetryData
        }
        PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
        int progressCount = reader.readVarInt();
        writer.writeVarInt(progressCount);
        for (int i = 0; i < progressCount; i++) {
            writer.writeString(reader.readString()); // key
            int criterionCount = reader.readVarInt();
            writer.writeVarInt(criterionCount);
            for (int c = 0; c < criterionCount; c++) {
                writer.writeString(reader.readString()); // criterionIdentifier
                boolean hasProgress = reader.readBoolean();
                writer.writeBoolean(hasProgress);
                if (hasProgress) {
                    writer.writeLong(reader.readLong()); // criterionProgress
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Advancements に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return writer.bytes();
    }

    /**
     * 764のスロット1個を765向けに読む（Advancements のアイコン用）。
     * スロットの NBT は任意の名前なし NBT（先頭 0x00＝無し。bool の
     * 前置きは無い）なので、{@link ChunkTranslator} のスロットと同じ
     * 読み方をする。
     */
    private static void translateSlot764To765(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (!present) {
            return;
        }
        writer.writeVarInt(RegistryRemapper.mapItemUp764(reader.readVarInt()));
        writer.writeByte(reader.readByte());
        Nbt.Tag nbt = reader.readAnonymousNbt().tag();
        if (nbt == null) {
            writer.writeByte(0);
        } else {
            writer.writeAnonymousNbt(nbt);
        }
    }

    // ------------------------------------------------------------------
    // スロット（両バージョン共通の読み飛ばし・写し）
    // ------------------------------------------------------------------

    /**
     * スロット1個を読み飛ばす（bool＋アイテムID・個数・NBT）。
     *
     * <p>NBT の前には bool が無い。スロットの NBT は名前なし NBT そのもの
     * で、先頭1バイトが 0x00（TAG_End）なら「無し」、それ以外ならそのバイト
     * から NBT blob が始まる（wiki.vg の Slot Data、2024-02版の旧形式）。
     * 以前は bool＋NBT として読んでいたため、NBT 付きスロットでは型バイト
     * （0x0A など）を bool と誤読して1バイトずれ、後続が誤読されていた。
     * NBT 無し（0x00）の場合だけ偶然同じ結果になっていた。
     * 同じ読み方は {@link ChunkTranslator} のスロット処理とも一致する。
     */
    static void skipSlot(Reader reader) {
        if (!reader.readBoolean()) {
            return; // 空スロット
        }
        reader.readVarInt(); // itemId
        reader.readByte(); // itemCount
        reader.readAnonymousNbt(); // NBT（0x00 なら無し）
    }

    /** スロット1個のバイト列をそのまま抜き出す（再エンコードしない）。 */
    static byte[] copySlotRaw(Reader reader, byte[] payload) {
        int start = reader.position();
        skipSlot(reader);
        return Arrays.copyOfRange(payload, start, reader.position());
    }

    // ------------------------------------------------------------------
    // Respawn／戦闘イベント／Advancements（763 ⇔ 762）
    // Join Game と同じく、762 は 763 から末尾の portalCooldown だけが無く、
    // 戦闘イベントだけ entityId の有無が違う。
    // ------------------------------------------------------------------

    /**
     * 763の Respawn（0x41）を762形式（0x41）に変換する。
     * 763の順番：dimension, worldName, hashedSeed, gamemode, previousGamemode,
     * isDebug, isFlat, copyMetadata, death, portalCooldown。
     * 762は同じだが末尾の portalCooldown が無い。
     */
    public static byte[] translateRespawn763To762(byte[] payload) {
        Reader reader = new Reader(payload);
        String dimension = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gamemode = reader.readByte();
        int previousGamemode = reader.readUnsignedByte();
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
        reader.readVarInt(); // portalCooldown（763のみ）
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Respawn に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(dimension);
        writer.writeString(worldName);
        writer.writeLong(hashedSeed);
        writer.writeByte(gamemode);
        writer.writeByte(previousGamemode);
        writer.writeBoolean(isDebug);
        writer.writeBoolean(isFlat);
        writer.writeBoolean(copyMetadata);
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(deathDimension);
            writer.writePositionRaw(deathLocation);
        }
        return writer.bytes();
    }

    /** 762の Respawn（0x41）を763形式（0x41）に変換する。portalCooldown=0 を足す。 */
    public static byte[] translateRespawn762To763(byte[] payload) {
        Reader reader = new Reader(payload);
        String dimension = reader.readString();
        String worldName = reader.readString();
        long hashedSeed = reader.readLong();
        int gamemode = reader.readByte();
        int previousGamemode = reader.readUnsignedByte();
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
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Respawn に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeString(dimension);
        writer.writeString(worldName);
        writer.writeLong(hashedSeed);
        writer.writeByte(gamemode);
        writer.writeByte(previousGamemode);
        writer.writeBoolean(isDebug);
        writer.writeBoolean(isFlat);
        writer.writeBoolean(copyMetadata);
        writer.writeBoolean(hasDeath);
        if (hasDeath) {
            writer.writeString(deathDimension);
            writer.writePositionRaw(deathLocation);
        }
        writer.writeVarInt(0); // portalCooldown（763のみ）
        return writer.bytes();
    }

    /**
     * Death Combat Event（763 0x38 → 762 0x38）。
     * 762 は playerId と message の間に entityId（i32）がある。
     * 763 には無いため、死んだプレイヤー自身の ID を入れる（無害化）。
     */
    public static byte[] translateDeathCombatEvent763To762(byte[] payload) {
        Reader reader = new Reader(payload);
        int playerId = reader.readVarInt();
        String message = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Death Combat Event に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(playerId);
        writer.writeInt(playerId); // entityId（763に無いため playerId で代用）
        writer.writeString(message);
        return writer.bytes();
    }

    /** Death Combat Event（762 0x38 → 763 0x38）。entityId を読んで落とす。 */
    public static byte[] translateDeathCombatEvent762To763(byte[] payload) {
        Reader reader = new Reader(payload);
        int playerId = reader.readVarInt();
        reader.readInt(); // entityId（762のみ）
        String message = reader.readString();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Death Combat Event に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(playerId);
        writer.writeString(message);
        return writer.bytes();
    }

    /**
     * End Combat Event（763 0x36 → 762 0x36）。
     * 762 は duration の直後に entityId（i32）がある。
     * 763 には無いため -1（エンティティ無し）を入れる（無害化）。
     */
    public static byte[] translateEndCombatEvent763To762(byte[] payload) {
        Reader reader = new Reader(payload);
        int duration = reader.readVarInt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "End Combat Event に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(duration);
        writer.writeInt(-1); // entityId（763に無いため -1 で代用）
        return writer.bytes();
    }

    /** End Combat Event（762 0x36 → 763 0x36）。entityId を読んで落とす。 */
    public static byte[] translateEndCombatEvent762To763(byte[] payload) {
        Reader reader = new Reader(payload);
        int duration = reader.readVarInt();
        reader.readInt(); // entityId（762のみ）
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "End Combat Event に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        Writer writer = new Writer();
        writer.writeVarInt(duration);
        return writer.bytes();
    }

    /**
     * Advancements（763 0x69 → 762 0x69）。
     * 差分は1か所だけ：763 は各進捗の値の末尾に sendsTelemetryData（bool）が
     * あるため、それを読んで落とす。criteria は 762・763 ともに
     * 文字列のキー一覧のためそのまま通す。表示の title・description は
     * 両方とも JSON 文字列、アイコンはスロット変換でアイテムIDを付け替える。
     */
    public static byte[] translateAdvancements763To762(byte[] payload) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeBoolean(reader.readBoolean()); // reset
        int mappingCount = reader.readVarInt();
        writer.writeVarInt(mappingCount);
        for (int i = 0; i < mappingCount; i++) {
            writer.writeString(reader.readString()); // key
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeString(reader.readString()); // title（JSON 文字列のまま）
                writer.writeString(reader.readString()); // description（JSON 文字列のまま）
                PlayTranslator.translateSlot763To762(reader, writer); // icon
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt(); // 表示フラグ（i32）
                writer.writeInt(flags);
                if ((flags & 0x01) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // xCord
                writer.writeFloat(reader.readFloat()); // yCord
            }
            // criteria：文字列のキー一覧（値の中身は無い）
            int criteriaCount = reader.readVarInt();
            writer.writeVarInt(criteriaCount);
            for (int c = 0; c < criteriaCount; c++) {
                writer.writeString(reader.readString());
            }
            int requirementCount = reader.readVarInt();
            writer.writeVarInt(requirementCount);
            for (int r = 0; r < requirementCount; r++) {
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
            }
            reader.readBoolean(); // sendsTelemetryData（763のみ）
        }
        PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
        int progressCount = reader.readVarInt();
        writer.writeVarInt(progressCount);
        for (int i = 0; i < progressCount; i++) {
            writer.writeString(reader.readString()); // key
            int criterionCount = reader.readVarInt();
            writer.writeVarInt(criterionCount);
            for (int c = 0; c < criterionCount; c++) {
                writer.writeString(reader.readString()); // criterionIdentifier
                boolean hasProgress = reader.readBoolean();
                writer.writeBoolean(hasProgress);
                if (hasProgress) {
                    writer.writeLong(reader.readLong()); // criterionProgress
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Advancements に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return writer.bytes();
    }

    /**
     * Advancements（762 0x69 → 763 0x69）。
     * 各進捗の値の末尾に sendsTelemetryData=false を足す。
     * 762 の進捗にテレメトリ送信の概念は無いため false が無害化になる。
     */
    public static byte[] translateAdvancements762To763(byte[] payload) {
        Reader reader = new Reader(payload);
        Writer writer = new Writer();
        writer.writeBoolean(reader.readBoolean()); // reset
        int mappingCount = reader.readVarInt();
        writer.writeVarInt(mappingCount);
        for (int i = 0; i < mappingCount; i++) {
            writer.writeString(reader.readString()); // key
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeString(reader.readString()); // title
                writer.writeString(reader.readString()); // description
                PlayTranslator.translateSlot762To763(reader, writer); // icon
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt(); // 表示フラグ（i32）
                writer.writeInt(flags);
                if ((flags & 0x01) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // xCord
                writer.writeFloat(reader.readFloat()); // yCord
            }
            int criteriaCount = reader.readVarInt();
            writer.writeVarInt(criteriaCount);
            for (int c = 0; c < criteriaCount; c++) {
                writer.writeString(reader.readString());
            }
            int requirementCount = reader.readVarInt();
            writer.writeVarInt(requirementCount);
            for (int r = 0; r < requirementCount; r++) {
                PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
            }
            writer.writeBoolean(false); // sendsTelemetryData（763のみ）
        }
        PacketIO.writeStringList(writer, PacketIO.readStringList(reader));
        int progressCount = reader.readVarInt();
        writer.writeVarInt(progressCount);
        for (int i = 0; i < progressCount; i++) {
            writer.writeString(reader.readString()); // key
            int criterionCount = reader.readVarInt();
            writer.writeVarInt(criterionCount);
            for (int c = 0; c < criterionCount; c++) {
                writer.writeString(reader.readString()); // criterionIdentifier
                boolean hasProgress = reader.readBoolean();
                writer.writeBoolean(hasProgress);
                if (hasProgress) {
                    writer.writeLong(reader.readLong()); // criterionProgress
                }
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(
                    "Advancements に余分なバイトがあります（残り=" + reader.remaining() + "）。");
        }
        return writer.bytes();
    }

}
