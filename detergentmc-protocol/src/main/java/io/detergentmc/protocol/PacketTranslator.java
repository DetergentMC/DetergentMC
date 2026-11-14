package io.detergentmc.protocol;

import io.detergentmc.protocol.PacketIds.Direction;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PacketIds.State;
import java.util.OptionalInt;

/**
 * パケットIDの変換：763（1.20.1）／764（1.20.2）と765（1.20.4）の間の付け替え。
 *
 * <p>サーバー内部は常に765で動く（ {@link ClientAdapter}）。プロキシは
 * クライアントから来たパケットのIDを765のIDに付け替えてサーバーへ渡し、
 * サーバーから来たパケットのIDをクライアントのプロトコルのIDに付け替えて
 * クライアントへ送る。このクラスはその「IDの付け替え」だけを行う。
 *
 * <p>使い方の例（プロキシ担当者向け）：
 * <pre>
 * // 763クライアントから Keep Alive（サーバー行き）が届いた。
 * // サーバー（765）へ渡すときのIDに付け替える。
 * OptionalInt serverId = PacketTranslator.toServerId(
 *         PacketName.PLAY_KEEP_ALIVE_TO_SERVER, 763); // 0x12 → 0x15
 *
 * // サーバーから Join Game（クライアント行き）が来た。
 * // 763クライアントへ送るときのIDに付け替える。
 * OptionalInt clientId = PacketTranslator.toClientId(
 *         PacketName.PLAY_JOIN_GAME, 763); // 0x29 → 0x28
 *
 * // 変換が要るクライアントかどうか。
 * if (PacketTranslator.needsTranslation(clientProtocol)) { ... }
 * </pre>
 *
 * <p>逆向き（サーバーが764・クライアントが765）の場合も、このクラスを
 * そのまま使う。{@link #translateId(PacketName, int, int)} は任意の
 * 2プロトコル間の付け替えなので、765→764も764→765も扱える。
 * 内部プロトコルが765で固定の {@link #toServerId(PacketName, int)}・
 * {@link #toClientId(PacketName, int)} の代わりに、サーバーの
 * プロトコルを引数で渡す {@link #toServerId(PacketName, int, int)}・
 * {@link #toClientId(PacketName, int, int)} を使うこと。サーバーの
 * プロトコルは {@link ProtocolVersion#serverProtocolFor(String)}
 * （設定の {@code minecraft-version} から決める）で取得する。
 * 764と765は両方とも設定フェーズがあるため、763のときのような
 * 設定フェーズの消費は要らない。ペイロードの変換は
 * {@link PlayTranslator}・{@link ChunkTranslator}・
 * {@link ExtraPlayTranslator} の {@code *764To765}／{@code *765To764}
 * を使うこと。
 *
 * <p>大事な注意：<b>IDの付け替えだけでは遊べない。</b>同じ名前でも
 * ペイロードの中身が違うパケットがある。
 * <ul>
 *   <li>設定フェーズ：763には存在しない。765のサーバーが設定フェーズで
 *       送るレジストリ情報などを、763向けにはプレイ状態で送り直す必要が
 *       ある。設定フェーズ用のパケットを763に送ってはいけない
 *       （ {@link ClientJoinFlow} 参照）。</li>
 *   <li>チャット：サーバー行きの Chat Message は両バージョンとも 0x05
 *       だが、署名まわりのペイロードが違う。IDが同じだからといって
 *       素通ししてよいわけではない。</li>
 *   <li>リソースパック：763はプレイ状態の旧式オファー（UUIDなし、
 *       {@link PacketName#PLAY_RESOURCE_PACK} 0x40）。765はUUID付きで、
 *       設定フェーズとプレイの両方にある。UUIDの付け外しが要る。</li>
 *   <li>スポーン：763はデフォルトスポーン位置パケットが合図。765は
 *       Game Event 13（チャンク待ち開始）が要る。IDの付け替えとは別に
 *       順番の組み替えが要る。</li>
 * </ul>
 * 対応するIDが無い場合は空（ {@link OptionalInt#empty()} ）を返す。
 * そのパケットは「そのバージョンには無い」ので、黙って送らず、
 * プロキシ側で明示的に扱うこと（落とす・別パケットに置き換える等）。
 */
public final class PacketTranslator {

    /** サーバー内部のプロトコル（1.20.4）。 */
    public static final int SERVER_PROTOCOL = ProtocolVersion.V1_20_4;

    private PacketTranslator() {
    }

    /** この変換器が知っているクライアントのプロトコルか（763か764か765か766）。 */
    public static boolean isSupported(int protocol) {
        return PacketIds.isSupported(protocol);
    }

    /**
     * このクライアントとの通信にIDの付け替えが要るか。
     * 765（サーバーと同じ）は要らない。763と764と766は要る。
     * 未対応のプロトコルは {@code false} を返す（変換ではなく拒否が正しい
     * 扱いなので、 {@link ClientAdapter#decide(int)} で断ること）。
     * 766のID表は別担当が追加中のため、表が無い間は
     * {@link #translateId(PacketName, int, int)} が空を返す。
     */
    public static boolean needsTranslation(int clientProtocol) {
        return clientProtocol == ProtocolVersion.V1_20_1
                || clientProtocol == ProtocolVersion.V1_20_2
                || clientProtocol == ProtocolVersion.V1_20_5;
    }

    /**
     * パケットIDをあるプロトコルから別のプロトコルへ付け替える。
     *
     * @return 変換先のID。次の場合は空：どちらかのプロトコルにその
     *         パケットが存在しない場合、未対応のプロトコルの場合。
     *         {@code fromProtocol == toProtocol} の場合は元のIDを返す。
     */
    public static OptionalInt translateId(PacketName name, int fromProtocol, int toProtocol) {
        if (name == null) {
            return OptionalInt.empty();
        }
        OptionalInt from = PacketIds.id(name, fromProtocol);
        if (from.isEmpty()) {
            return OptionalInt.empty();
        }
        if (fromProtocol == toProtocol) {
            return from;
        }
        return PacketIds.id(name, toProtocol);
    }

    /**
     * ワイヤ上のIDをあるプロトコルから別のプロトコルへ付け替える。
     * 名前が分からない場合は {@link PacketIds#nameFor(int, State, Direction, int)}
     * で逆引きしてから {@link #translateId(PacketName, int, int)} を呼ぶ。
     *
     * @return 変換先のID。そのIDの名前が分からない場合や、どちらかの
     *         プロトコルに存在しない場合は空
     */
    public static OptionalInt translateId(int id, State state, Direction direction,
            int fromProtocol, int toProtocol) {
        return PacketIds.nameFor(id, state, direction, fromProtocol)
                .map(name -> translateId(name, fromProtocol, toProtocol))
                .orElse(OptionalInt.empty());
    }

    /**
     * クライアントから届いたパケットをサーバー（765）へ渡すときのID。
     * サーバー行きパケット用。例：763の Keep Alive（サーバー行き）
     * 0x12 → 765の 0x15。
     */
    public static OptionalInt toServerId(PacketName name, int clientProtocol) {
        return translateId(name, clientProtocol, SERVER_PROTOCOL);
    }

    /**
     * クライアントから届いたパケットをサーバーへ渡すときのID。
     * サーバー行きパケット用。サーバーのプロトコルを引数で渡す版。
     * 逆向き（サーバーが764・クライアントが765）の場合は
     * {@code serverProtocol} に764を渡す（
     * {@link ProtocolVersion#serverProtocolFor(String)} で取得する）。
     * 例：765の Keep Alive（サーバー行き）0x15 → 764の 0x14。
     */
    public static OptionalInt toServerId(PacketName name, int clientProtocol,
            int serverProtocol) {
        return translateId(name, clientProtocol, serverProtocol);
    }

    /**
     * サーバーから来たパケットをクライアントへ送るときのID。
     * クライアント行きパケット用。例：765の Join Game 0x29 →
     * 763クライアント向けの 0x28。
     */
    public static OptionalInt toClientId(PacketName name, int clientProtocol) {
        return translateId(name, SERVER_PROTOCOL, clientProtocol);
    }

    /**
     * サーバーから来たパケットをクライアントへ送るときのID。
     * クライアント行きパケット用。サーバーのプロトコルを引数で渡す版。
     * 逆向き（サーバーが764・クライアントが765）の場合は
     * {@code serverProtocol} に764を渡す。
     * 例：764の Respawn 0x43 → 765クライアント向けの 0x45。
     */
    public static OptionalInt toClientId(PacketName name, int serverProtocol,
            int clientProtocol) {
        return translateId(name, serverProtocol, clientProtocol);
    }

    /**
     * このクライアントとサーバーの通信にIDの付け替えが要るか。
     * プロトコルが違う場合に要る（同じ場合は要らない）。
     */
    public static boolean needsTranslation(int clientProtocol, int serverProtocol) {
        return clientProtocol != serverProtocol
                && PacketIds.isSupported(clientProtocol)
                && PacketIds.isSupported(serverProtocol);
    }

    /** そのパケットが指定プロトコルに存在するか。 */
    public static boolean existsIn(PacketName name, int protocol) {
        return PacketIds.id(name, protocol).isPresent();
    }
}
