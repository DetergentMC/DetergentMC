package io.detergentmc.protocol;

import java.util.List;

/**
 * クライアントアダプター：サーバー（1.20.4 / プロトコル765）に違う
 * バージョンのクライアントが参加するときの、プロトコル変換の判断と
 * バージョン差の一覧。
 *
 * <p>考え方（変換の方針）：
 * <ul>
 *   <li>サーバー内部は常に 1.20.4（プロトコル765）で動く。クライアントに
 *       合わせてサーバー側のバージョンを下げることはしない。</li>
 *   <li>クライアント向けのパケットは、クライアントのプロトコルに合わせて
 *       変換して送る。クライアントからのパケットは、765の形に読み替えて
 *       処理する。つまり変換はクライアントとの境界面だけで行う。</li>
 *   <li>変換できないものは、黙ってごまかさず {@link Handling} で明示する。
 *       送れないブロックは代替ブロックに置き換え、パケット単位で未検証の
 *       ものは {@code NEEDS_VERIFICATION} とする。</li>
 * </ul>
 *
 * <p>内部が 1.20.2（764）の場合も同じ考え方で、振り分けだけが変わる。
 * クライアントと内部のプロトコルを比べ（{@link #direction(int, int)}）、
 * 同じなら素通し、クライアントが古ければ上向き変換、新しければ下向き
 * 変換（765→764）にする。763と764の間の直接対応は
 * {@code map*763To764}／{@code map*764To763} が持つ（763→765→764 の
 * 二段変換はしない。既存の765→763表と765→764表の合成である）。
 *
 * <p>対応クライアントは 1.17（755）と 1.17.1（756）と 1.18／1.18.1（757）と 1.19（759）と 1.19.1／1.19.2（760）と 1.19.3（761）と 1.19.4（762）と 1.20.1（763）と 1.20.2（764）と 1.20.4（765）
 * と 1.20.5／1.20.6（766）と 1.21／1.21.1（767）と 1.21.2／1.21.3（768）
 * と 1.21.4（769）と 1.21.5（770）と 1.21.6（771）と 1.21.7／1.21.8（772）と 1.21.9／1.21.10（773）と 1.21.11（774）と 26.1（775）。それより古いクライアントは「古すぎる」、
 * それより新しいクライアントは「新しすぎる」として、ハンドシェイクで
 * 断る（ {@link #decide(int)}）。766と767のプレイ状態の変換（スロットの
 * データコンポーネント化など）は別担当の対応表・レジストリ表を待つため、
 * 766クライアントの参加フロー（ハンドシェイク→ログイン→設定フェーズ）
 * まではこのクラスと {@link io.detergentmc.protocol.ProxySession} で
 * 振り分ける。プレイ状態のパケット変換は未実装のものがある
 * （ {@link io.detergentmc.protocol.ProxySession} 参照）。768のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の768対応で実装する。769のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の769対応で実装する。770のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の770対応で実装する。771のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の771対応で実装する。772のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の772対応で実装する。773のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の773対応で実装する。
 *
 * <p>764の変換方針は763と少し違う。763には設定フェーズが無いので
 * プロキシが設定フェーズを消費して Join Game に codec を埋め込むが、
 * 764には設定フェーズがあるので中継だけする（レジストリ差分だけ変換）。
 * 詳しくは {@link io.detergentmc.protocol.ProxySession} を参照。
 *
 * <p>まだ本物のネットワーク層（Nettyパイプライン）は無いので、このクラスは
 * 判断と対応表だけを持つ純粋関数群である。バニラ1.20.4の子プロセス自体は
 * プロトコル763のクライアントをハンドシェイクで弾くため、実機の1.20.1
 * クライアントが今日そのまま参加できるわけではない。本物の参加には、
 * この判断と対応表を使うプロキシ／Netty層が次の段階で要る（未実装）。
 * プロトコルレベルの検証は {@link ClientJoinFlow} の単体テストで行う。
 */
public final class ClientAdapter {

    /** サーバーが動くプロトコル（1.20.4）。 */
    public static final int SERVER_PROTOCOL = ProtocolVersion.V1_20_4;

    /** 受け入れる一番古いクライアントのプロトコル（1.19）。 */
    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_19;

    private ClientAdapter() {
    }

    /** ハンドシェイクに対する答え。 */
    public enum Decision {
        /** サーバーと同じプロトコル。変換なしで受け入れる。 */
        ACCEPT_NATIVE,
        /** 古いプロトコルだが、アダプターで変換して受け入れる。 */
        ACCEPT_VIA_ADAPTER,
        /** 古すぎて対応できない。 */
        REJECT_OUTDATED,
        /** 新しすぎて対応できない。 */
        REJECT_TOO_NEW
    }

    /** ハンドシェイク判定の結果。 */
    public record Handshake(Decision decision, int clientProtocol, String message) {

        /** このクライアントとの通信にプロトコル変換が要るか。 */
        public boolean needsTranslation() {
            return decision == Decision.ACCEPT_VIA_ADAPTER;
        }

        /** 受け入れるか。 */
        public boolean accepted() {
            return decision == Decision.ACCEPT_NATIVE
                    || decision == Decision.ACCEPT_VIA_ADAPTER;
        }
    }

    /** バージョン差をどう扱うか。 */
    public enum Handling {
        /** 違いはなく、そのまま使える。 */
        UNAFFECTED,
        /** 変換して送る／読み替える。 */
        TRANSLATED,
        /** 代替のブロック・見た目に置き換える。 */
        FALLBACK,
        /** 変換が必要なことは分かっているが、パケット単位の照合が未検証。 */
        NEEDS_VERIFICATION,
        /** 対応しない。 */
        NOT_SUPPORTED
    }

    /** 一つのバージョン差。 */
    public record Difference(String area, String summary, Handling handling, String detail) {
    }

    /** クライアントと内部サーバーのプロトコルの関係（振り分けの向き）。 */
    public enum Direction {
        /** クライアントと内部が同じプロトコル。変換なしで素通しする。 */
        NATIVE,
        /** クライアントが内部より古い。上向き変換（763／764→765）。 */
        UP,
        /** クライアントが内部より新しい。下向き変換（765→764）。 */
        DOWN
    }

    /**
     * クライアントと内部サーバーのプロトコルを比べ、振り分けの向きを返す。
     * 同じなら素通し、違えば変換する方向に切り替えるための判断である。
     */
    public static Direction direction(int clientProtocol, int serverProtocol) {
        if (clientProtocol == serverProtocol) {
            return Direction.NATIVE;
        }
        return clientProtocol < serverProtocol ? Direction.UP : Direction.DOWN;
    }

    /**
     * クライアントのプロトコル番号に対するハンドシェイク判定。
     *
     * <ul>
     *   <li>765（1.20.4）：そのまま受け入れる。</li>
     *   <li>766（1.20.5／1.20.6）：変換して受け入れる。設定フェーズは中継する。</li>
     *   <li>767（1.21／1.21.1）：変換して受け入れる。設定フェーズは766と同形で中継する。</li>
     *   <li>768（1.21.2／1.21.3）：変換して受け入れる。設定フェーズは767と同形で中継する。</li>
     *   <li>769（1.21.4）：変換して受け入れる。設定フェーズは768と同形で中継する。</li>
     *   <li>770（1.21.5）：変換して受け入れる。設定フェーズは769と同形で中継する。</li>
     *   <li>771（1.21.6）：変換して受け入れる。設定フェーズは770と同形で中継する。</li>
     *   <li>772（1.21.7／1.21.8）：変換して受け入れる。設定フェーズは771と同形で中継する。</li>
     *   <li>773（1.21.9／1.21.10）：変換して受け入れる。設定フェーズは772と同形で中継する。</li>
     *   <li>774（1.21.11）：変換して受け入れる。設定フェーズは773と同形で中継する。</li>
     *   <li>775（26.1）：変換して受け入れる。設定フェーズは774と同形で中継する。</li>
     *   <li>764（1.20.2）：変換して受け入れる。設定フェーズは中継する。</li>
     *   <li>763（1.20.1）：変換して受け入れる。</li>
     *   <li>762（1.19.4）：変換して受け入れる。設定フェーズが無いのは763と同じ。</li>
     *   <li>761（1.19.3）：変換して受け入れる。設定フェーズが無いのは763と同じ。</li>
     *   <li>760（1.19.1／1.19.2）：変換して受け入れる。設定フェーズが無いのは763と同じ。</li>
     *   <li>759（1.19）：変換して受け入れる。設定フェーズが無いのは763と同じ。</li>
     *   <li>757（1.18／1.18.1）：変換して受け入れる。設定フェーズが無いのは763と同じ。</li>
     *   <li>755（1.17）：変換して受け入れる。設定フェーズが無いのは763と同じ。パケットIDは756と同一で、レジストリも756と同一。</li>
     *   <li>756（1.17.1）：変換して受け入れる。設定フェーズが無いのは763と同じ。</li>
     *   <li>755より古い：古すぎるとして断る。</li>
     *   <li>775より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol) {
        return decide(clientProtocol, SERVER_PROTOCOL);
    }

    /**
     * {@link #decide(int)} の内部プロトコル指定版（逆向き対応）。
     * 受け入れるクライアントの集合は内部によらず 755・756・757・759・760・761・762・763・764・765・766・767・768・769・770・771・772・773・774・775 で、
     * 向きだけが内部との比較で決まる：同じなら素通し、古ければ上向き、
     * 新しければ下向き変換で受け入れる。
     *
     * <ul>
     *   <li>内部764＋764クライアント：そのまま受け入れる。</li>
     *   <li>内部764＋765クライアント：下向き変換（765→764）で受け入れる。</li>
     *   <li>内部764＋763クライアント：763→764の直接変換で受け入れる。
     *       設定フェーズはプロキシが764クライアント役として消費する。</li>
     *   <li>内部763＋764／765／766／767クライアント：設定フェーズが無い内部に
     *       対し、プロキシが Join Game の codec から設定フェーズを合成する
     *       （ {@link io.detergentmc.protocol.ProxySession} 参照）。</li>
     *   <li>内部766＋763クライアント：設定フェーズはプロキシが766クライアント役
     *       として消費する。内部766＋764／765クライアント：設定フェーズは
     *       中継し、Registry Data の形式（766はエントリ配列）だけ変換する。
     *       内部766＋767クライアント：設定フェーズは中継し、767の新規パケット
     *       （Server Links・Custom Report Details）は落とす。</li>
     *   <li>内部767＋763クライアント：設定フェーズはプロキシが767クライアント役
     *       として消費する。内部767＋764／765／766クライアント：設定フェーズは
     *       中継し、Registry Data の形式だけ変換する。</li>
     *   <li>内部768＋763クライアント：設定フェーズはプロキシが768クライアント役
     *       として消費する。内部768＋764／765／766／767クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部769＋763クライアント：設定フェーズはプロキシが769クライアント役
     *       として消費する。内部769＋764／765／766／767／768クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部770＋763クライアント：設定フェーズはプロキシが770クライアント役
     *       として消費する。内部770＋764／765／766／767／768／769クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部771＋763クライアント：設定フェーズはプロキシが771クライアント役
     *       として消費する。内部771＋764／765／766／767／768／769／770クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部773＋763クライアント：設定フェーズはプロキシが773クライアント役
     *       として消費する。内部773＋764／765／766／767／768／769／770／771／772クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部774＋763クライアント：設定フェーズはプロキシが774クライアント役
     *       として消費する。内部774＋764／765／766／767／768／769／770／771／772／773クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部775＋763クライアント：設定フェーズはプロキシが775クライアント役
     *       として消費する。内部775＋764／765／766／767／768／769／770／771／772／773／774クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部772＋763クライアント：設定フェーズはプロキシが772クライアント役
     *       として消費する。内部772＋764／765／766／767／768／769／770／771クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部774＋762クライアント：設定フェーズはプロキシが774クライアント役
     *       として消費し、プレイ状態は774→763→762の二段変換で受け入れる。</li>
     *   <li>内部774＋761クライアント：設定フェーズはプロキシが774クライアント役
     *       として消費し、プレイ状態は774→763→762→761の三段変換で受け入れる。</li>
     *   <li>内部774＋760クライアント：設定フェーズはプロキシが774クライアント役
     *       として消費し、プレイ状態は774→763→762→761→760の四段変換で受け入れる。</li>
     *   <li>内部774＋759クライアント：設定フェーズはプロキシが774クライアント役
     *       として消費し、プレイ状態は774→763→762→761→760→759の五段変換で受け入れる。</li>
     *   <li>内部775＋762クライアント：設定フェーズはプロキシが775クライアント役
     *       として消費し、プレイ状態は775→774→763→762の三段変換で受け入れる。</li>
     *   <li>内部775＋761クライアント：設定フェーズはプロキシが775クライアント役
     *       として消費し、プレイ状態は775→774→763→762→761の四段変換で受け入れる。</li>
     *   <li>内部775＋760クライアント：設定フェーズはプロキシが775クライアント役
     *       として消費し、プレイ状態は775→774→763→762→761→760の五段変換で受け入れる。</li>
     *   <li>内部775＋759クライアント：設定フェーズはプロキシが775クライアント役
     *       として消費し、プレイ状態は775→774→763→762→761→760→759の六段変換で受け入れる。</li>
     *   <li>内部757＋759クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       757↔759の直接変換で受け入れる。</li>
     *   <li>内部757＋760クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       757→759→760の二段変換で受け入れる。</li>
     *   <li>内部757＋761クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       757→759→760→761の三段変換で受け入れる。</li>
     *   <li>内部757＋762クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       757→759→760→761→762の四段変換で受け入れる。</li>
     *   <li>内部757＋763クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       757→759→760→761→762→763の五段変換で受け入れる。</li>
     *   <li>内部757＋764／765／766／767／768／769／770／771／772／773／774／775クライアント：
     *       設定フェーズが無い内部に対し、プロキシが Join Game の codec から
     *       設定フェーズを合成する（ {@link io.detergentmc.protocol.ProxySession} 参照）。</li>
     *   <li>内部755＋756クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       755↔756の直接変換で受け入れる（パケットID・レジストリとも同一）。</li>
     *   <li>内部755＋757クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       755→756→757の二段変換で受け入れる（パケットID・レジストリとも755↔756は同一）。</li>
     *   <li>内部755＋759／760／761／762／763クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       755→756→757→759→760→761→762→763の多段変換で受け入れる。</li>
     *   <li>内部755＋764／765／766／767／768／769／770／771／772／773／774／775クライアント：
     *       設定フェーズが無い内部に対し、プロキシが Join Game の codec から
     *       設定フェーズを合成する（ {@link io.detergentmc.protocol.ProxySession} 参照）。</li>
     *   <li>内部756＋757クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       756↔757の直接変換で受け入れる。</li>
     *   <li>内部756＋759／760／761／762／763クライアント：設定フェーズはどちらにも無い。プレイ状態は
     *       756→757→759→760→761→762→763の多段変換で受け入れる。</li>
     *   <li>内部756＋764／765／766／767／768／769／770／771／772／773／774／775クライアント：
     *       設定フェーズが無い内部に対し、プロキシが Join Game の codec から
     *       設定フェーズを合成する（ {@link io.detergentmc.protocol.ProxySession} 参照）。</li>
     *   <li>755より古い：古すぎるとして断る。</li>
     *   <li>775より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol, int serverProtocol) {
        if (clientProtocol == serverProtocol) {
            return new Handshake(Decision.ACCEPT_NATIVE, clientProtocol,
                    "プロトコル" + clientProtocol + "のクライアントをそのまま受け入れます。");
        }
        if (clientProtocol == ProtocolVersion.V1_17
                || clientProtocol == ProtocolVersion.V1_17_1
                || clientProtocol == ProtocolVersion.V1_18
                || clientProtocol == ProtocolVersion.V1_19
                || clientProtocol == ProtocolVersion.V1_19_1
                || clientProtocol == ProtocolVersion.V1_19_3
                || clientProtocol == ProtocolVersion.V1_19_4
                || clientProtocol == ProtocolVersion.V1_20_2
                || clientProtocol == OLDEST_SUPPORTED_CLIENT_PROTOCOL
                || clientProtocol == ProtocolVersion.V1_20_1
                || clientProtocol == ProtocolVersion.V1_20_4
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5
                || clientProtocol == ProtocolVersion.V1_21_6
                || clientProtocol == ProtocolVersion.V1_21_7
                || clientProtocol == ProtocolVersion.V1_21_9
                || clientProtocol == ProtocolVersion.V1_21_11
                || clientProtocol == ProtocolVersion.V26_1) {
            Direction dir = direction(clientProtocol, serverProtocol);
            if (clientProtocol == ProtocolVersion.V1_20_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル764（1.20.2）のクライアントを変換して受け入れます。"
                                + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。");
            }
            if (clientProtocol == ProtocolVersion.V1_17) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル755（1.17）のクライアントを変換して受け入れます。"
                                + "設定フェーズは無い（1.20.2／764で追加）ため、"
                                + "設定フェーズの省略とブロックの代替が必要です。"
                                + "パケットIDは756と同一のため番号の付け替えは不要です。"
                                + "レジストリは756と同一です。");
            }
            if (clientProtocol == ProtocolVersion.V1_17_1) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル756（1.17.1）のクライアントを変換して受け入れます。"
                                + "設定フェーズは無い（1.20.2／764で追加）ため、"
                                + "設定フェーズの省略とブロックの代替が必要です。"
                                + "パケットIDは757から16件変わるため番号を付け替えます。"
                                + "simulation_distance（0x57）の削除による-1ずれです。");
            }
            if (clientProtocol == ProtocolVersion.V1_18) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル757（1.18／1.18.1）のクライアントを変換して受け入れます。"
                                + "設定フェーズは無い（1.20.2／764で追加）ため、"
                                + "設定フェーズの省略とブロックの代替が必要です。"
                                + "パケットIDは759から123件変わるため番号を付け替えます。");
            }
            if (clientProtocol == ProtocolVersion.V1_19) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル759（1.19）のクライアントを変換して受け入れます。"
                                + "設定フェーズの省略とブロックの代替が必要です。"
                                + "パケットIDは760から131件変わるため番号を付け替えます。");
            }
            if (clientProtocol == ProtocolVersion.V1_19_1) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル760（1.19.1／1.19.2）のクライアントを変換して受け入れます。"
                                + "設定フェーズの省略とブロックの代替が必要です。"
                                + "パケットIDは761から118件変わるため番号を付け替えます。");
            }
            if (clientProtocol == ProtocolVersion.V1_19_3) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル761（1.19.3）のクライアントを変換して受け入れます。"
                                + "設定フェーズの省略とブロックの代替が必要です。"
                                + "パケットIDは762から132件変わるため番号を付け替えます。");
            }
            if (clientProtocol == ProtocolVersion.V1_19_4) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル762（1.19.4）のクライアントを変換して受け入れます。"
                                + "設定フェーズの省略とブロックの代替が必要です。");
            }
            if (clientProtocol == ProtocolVersion.V1_20_1) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル763（1.20.1）のクライアントを変換して受け入れます。"
                                        + "設定フェーズの省略とブロックの代替が必要です。"
                                : "プロトコル763（1.20.1）のクライアントを763→764の直接変換で受け入れます。"
                                        + "設定フェーズはプロキシが764クライアント役として消費します。");
            }
            if (clientProtocol == ProtocolVersion.V1_20_5) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル766（1.20.5／1.20.6）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "プレイ状態の変換は対応表の766対応を待つものがあります。"
                                : "プロトコル766（1.20.5／1.20.6）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "プレイ状態の変換は対応表の766対応を待つものがあります。");
            }
            if (clientProtocol == ProtocolVersion.V1_21) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル767（1.21／1.21.1）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は766⇔767で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル767（1.21／1.21.1）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は766⇔767で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル768（1.21.2／1.21.3）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は767⇔768で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル768（1.21.2／1.21.3）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は767⇔768で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_4) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル769（1.21.4）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は768⇔769で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル769（1.21.4）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は768⇔769で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_5) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル770（1.21.5）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は769⇔770で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル770（1.21.5）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は769⇔770で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_6) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル771（1.21.6）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は770⇔771で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル771（1.21.6）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は770⇔771で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_9) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル773（1.21.9／1.21.10）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は772⇔773で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル773（1.21.9／1.21.10）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は772⇔773で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_11) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル774（1.21.11）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は773⇔774で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル774（1.21.11）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は773⇔774で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V26_1) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル775（26.1）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は774⇔775で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                        + "パケットIDは774から164件変わるため番号を付け替えます。"
                                : "プロトコル775（26.1）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は774⇔775で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                        + "パケットIDは774から164件変わるため番号を付け替えます。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_7) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル772（1.21.7／1.21.8）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は771⇔772で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル772（1.21.7／1.21.8）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は771⇔772で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                    "プロトコル765（1.20.4）のクライアントを下向き変換（765→764）で受け入れます。"
                            + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。");
        }
        if (clientProtocol < OLDEST_SUPPORTED_CLIENT_PROTOCOL) {
            return new Handshake(Decision.REJECT_OUTDATED, clientProtocol,
                    "プロトコル" + clientProtocol + "は古すぎます。"
                            + "1.17（755）以降のクライアントで参加してください。");
        }
        return new Handshake(Decision.REJECT_TOO_NEW, clientProtocol,
                "プロトコル" + clientProtocol + "は新しすぎます。"
                        + "このサーバーは26.1（775）まで対応しています。");
    }

    /** 受け入れるクライアントのプロトコル番号の一覧。 */
    public static List<Integer> supportedClientProtocols() {
        // OLDEST（759）だけでは760が漏れるため、760は明示する。
        // 760をここから外すと ClientJoinFlow が760クライアントを拒否する。
        return List.of(ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3,
                ProtocolVersion.V1_19_4,
                ProtocolVersion.V1_20_1,
                ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21, ProtocolVersion.V1_21_2,
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6,
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11,
                ProtocolVersion.V26_1);
    }

    /** プロトコル番号に対応する表示バージョン（"1.20.1" 等）。未知の番号は null。 */
    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_17) {
            return "1.17";
        }
        if (protocol == ProtocolVersion.V1_17_1) {
            return "1.17.1";
        }
        if (protocol == ProtocolVersion.V1_18) {
            return "1.18";
        }
        if (protocol == ProtocolVersion.V1_19) {
            return "1.19";
        }
        if (protocol == ProtocolVersion.V1_19_1) {
            return "1.19.1";
        }
        if (protocol == ProtocolVersion.V1_19_3) {
            return "1.19.3";
        }
        if (protocol == ProtocolVersion.V1_19_4) {
            return "1.19.4";
        }
        if (protocol == ProtocolVersion.V1_20_1) {
            return "1.20.1";
        }
        if (protocol == ProtocolVersion.V1_20_2) {
            return "1.20.2";
        }
        if (protocol == ProtocolVersion.V1_20_4) {
            return "1.20.4";
        }
        if (protocol == ProtocolVersion.V1_20_5) {
            return "1.20.5";
        }
        if (protocol == ProtocolVersion.V1_21) {
            return "1.21";
        }
        if (protocol == ProtocolVersion.V1_21_2) {
            return "1.21.2";
        }
        if (protocol == ProtocolVersion.V1_21_4) {
            return "1.21.4";
        }
        if (protocol == ProtocolVersion.V1_21_5) {
            return "1.21.5";
        }
        if (protocol == ProtocolVersion.V1_21_6) {
            return "1.21.6";
        }
        if (protocol == ProtocolVersion.V1_21_9) {
            return "1.21.9";
        }
        if (protocol == ProtocolVersion.V1_21_11) {
            return "1.21.11";
        }
        if (protocol == ProtocolVersion.V26_1) {
            return "26.1";
        }
        if (protocol == ProtocolVersion.V1_21_7) {
            return "1.21.7";
        }
        return null;
    }

    /**
     * ステータス応答 JSON の {@code version} を、内部サーバーの表示から
     * クライアント向けの表示に書き換える。
     *
     * <p>{@link PlayTranslator#rewriteStatusJsonFor763(String)}／
     * {@link PlayTranslator#rewriteStatusJsonFor764(String)} は内部765
     * 前提（{@code "protocol":765} と {@code "1.20.4"} を探す）だが、
     * 内部764の場合は応答が既に {@code "protocol":764}／
     * {@code "1.20.2"} になっているため、そちらには使えない。
     * このメソッドは内部・クライアントの両方のプロトコルを受け取り、
     * どちら向きでも書き換える（内部764＋765クライアントなら 764→765、
     * 内部764＋763クライアントなら 764→763）。同じプロトコル同士や、
     * どちらかの表示名が分からない場合はそのまま返す。
     * {@code version} オブジェクトの中だけを書き換える（プレイヤー一覧
     * の {@code name} には触れない）。
     */
    public static String rewriteStatusJson(String json, int serverProtocol,
            int clientProtocol) {
        if (json == null || serverProtocol == clientProtocol) {
            return json;
        }
        String fromName = versionName(serverProtocol);
        String toName = versionName(clientProtocol);
        if (fromName == null || toName == null) {
            return json;
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
        // protocol: 内部の番号 → クライアントの番号（空白の有無を問わない）。
        versionBody = versionBody.replaceAll(
                "\"protocol\"\\s*:\\s*" + serverProtocol,
                "\"protocol\":" + clientProtocol);
        // name: 内部の表示バージョン → クライアントの表示バージョン。
        versionBody = versionBody.replaceAll(
                "\"name\"\\s*:\\s*\"" + java.util.regex.Pattern.quote(fromName) + "\"",
                "\"name\":\"" + toName + "\"");
        return before + versionBody + after;
    }

    /** {@code open} の "{" に対応する "}" の位置。文字列の中の括弧は数えない。 */
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
    // 763（1.20.1）⇔764（1.20.2）の直接対応（内部764＋763クライアント用）。
    // 763→765→764 の二段変換はしない。既存の765→763表と765→764表を
    // 合成する。アイテムは 763→765 の逆変換表が既にあるため正確に合成
    // できる。ブロック状態・音・パーティクルは逆変換表が無いため、
    // 765→763表を逆引き（初出の765番号を採用）してから765→764表を引く。
    // 765にしかないものは、763向けと同一の代替方針（近いブロックへの代替）が
    // 両方の表に既に入っているため、合成結果も同じ代替になる。
    // 往復の一致はテストで確認する（ClientAdapterCrossVersionTest）。
    // 763と764で登録が同一のもの（エンティティ・パーティクル・ブロック・
    // メニュー）は恒等写像、エフェクトは763が1ベース・764が0ベースのため
    // ±1する。ブロックエンティティ種別も 0〜40 は同一の並びである。
    // ------------------------------------------------------------------

    /** 763のブロック状態ID → 765のブロック状態ID（逆引き。初出を採用）。 */
    private static final int[] BLOCK_STATE_763_TO_765 =
            buildInverse(RegistryRemapper.MAX_BLOCK_STATE_765, RegistryRemapper::mapBlockState);

    /** 764のブロック状態ID → 765のブロック状態ID（逆引き。初出を採用）。 */
    private static final int[] BLOCK_STATE_764_TO_765 =
            buildInverse(RegistryRemapper.MAX_BLOCK_STATE_765, RegistryRemapper::mapBlockState764);

    /** 763の音ID → 765の音ID（逆引き。初出を採用）。 */
    private static final int[] SOUND_763_TO_765 =
            buildInverse(RegistryRemapper.MAX_SOUND_765, RegistryRemapper::mapSound);

    /** 764の音ID → 765の音ID（逆引き。初出を採用）。 */
    private static final int[] SOUND_764_TO_765 =
            buildInverse(RegistryRemapper.MAX_SOUND_765, RegistryRemapper::mapSound764);

    /** 763のパーティクルID → 765のパーティクルID（逆引き。初出を採用）。 */
    private static final int[] PARTICLE_763_TO_765 =
            buildInverse(RegistryRemapper.MAX_PARTICLE_765, RegistryRemapper::mapParticle);

    /** 764のパーティクルID → 765のパーティクルID（逆引き。初出を採用）。 */
    private static final int[] PARTICLE_764_TO_765 =
            buildInverse(RegistryRemapper.MAX_PARTICLE_765, RegistryRemapper::mapParticle764);

    /**
     * 765→クライアント表の逆引き表を作る。{@code forward} が765番号を
     * クライアント番号に写す関数で、結果はクライアント番号→765番号の表
     * （対応する765番号が無い場合は -1）。複数の765番号が同じクライアント
     * 番号に写る場合（新しいブロックの代替など）は、初出（最小の765番号）を
     * 採用する。
     */
    private static int[] buildInverse(int max765, java.util.function.IntUnaryOperator forward) {
        int[] inverse = new int[max765 + 1];
        java.util.Arrays.fill(inverse, -1);
        for (int id765 = 0; id765 <= max765; id765++) {
            int idClient = forward.applyAsInt(id765);
            if (idClient >= 0 && idClient <= max765 && inverse[idClient] < 0) {
                inverse[idClient] = id765;
            }
        }
        return inverse;
    }

    /** 763のブロック状態ID → 764のブロック状態ID。範囲外は石（1）。 */
    public static int mapBlockState763To764(int id763) {
        if (id763 < 0 || id763 > RegistryRemapper.MAX_BLOCK_STATE_765) {
            return RegistryRemapper.STONE_BLOCK_STATE_764;
        }
        int id765 = BLOCK_STATE_763_TO_765[id763];
        if (id765 < 0) {
            return RegistryRemapper.STONE_BLOCK_STATE_764;
        }
        return RegistryRemapper.mapBlockState764(id765);
    }

    /** 764のブロック状態ID → 763のブロック状態ID。範囲外は石（1）。 */
    public static int mapBlockState764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.MAX_BLOCK_STATE_765) {
            return RegistryRemapper.STONE_BLOCK_STATE_763;
        }
        int id765 = BLOCK_STATE_764_TO_765[id764];
        if (id765 < 0) {
            return RegistryRemapper.STONE_BLOCK_STATE_763;
        }
        return RegistryRemapper.mapBlockState(id765);
    }

    /** 763のアイテムID → 764のアイテムID。範囲外は石（1）。 */
    public static int mapItem763To764(int id763) {
        int id765 = RegistryRemapper.mapItemReverse(id763);
        if (id765 < 0 || id765 > RegistryRemapper.MAX_ITEM_765) {
            return RegistryRemapper.STONE_ITEM_764;
        }
        return RegistryRemapper.mapItem764(id765);
    }

    /** 764のアイテムID → 763のアイテムID。範囲外は石（1）。 */
    public static int mapItem764To763(int id764) {
        int id765 = RegistryRemapper.mapItemReverse764(id764);
        if (id765 < 0 || id765 > RegistryRemapper.MAX_ITEM_765) {
            return RegistryRemapper.STONE_ITEM_763;
        }
        return RegistryRemapper.mapItem(id765);
    }

    /** 763の音ID → 764の音ID。範囲外は石の破壊音。 */
    public static int mapSound763To764(int id763) {
        if (id763 < 0 || id763 > RegistryRemapper.MAX_SOUND_765) {
            return RegistryRemapper.GENERIC_SOUND_764;
        }
        int id765 = SOUND_763_TO_765[id763];
        if (id765 < 0) {
            return RegistryRemapper.GENERIC_SOUND_764;
        }
        return RegistryRemapper.mapSound764(id765);
    }

    /** 764の音ID → 763の音ID。範囲外は石の破壊音。 */
    public static int mapSound764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.MAX_SOUND_765) {
            return RegistryRemapper.GENERIC_SOUND_763;
        }
        int id765 = SOUND_764_TO_765[id764];
        if (id765 < 0) {
            return RegistryRemapper.GENERIC_SOUND_763;
        }
        return RegistryRemapper.mapSound(id765);
    }

    /** 763のパーティクルID → 764のパーティクルID。範囲外は ambient_entity_effect（0）。 */
    public static int mapParticle763To764(int id763) {
        if (id763 < 0 || id763 > RegistryRemapper.MAX_PARTICLE_765) {
            return RegistryRemapper.AMBIENT_PARTICLE_764;
        }
        int id765 = PARTICLE_763_TO_765[id763];
        if (id765 < 0) {
            return RegistryRemapper.AMBIENT_PARTICLE_764;
        }
        return RegistryRemapper.mapParticle764(id765);
    }

    /** 764のパーティクルID → 763のパーティクルID。範囲外は ambient_entity_effect（0）。 */
    public static int mapParticle764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.MAX_PARTICLE_765) {
            return RegistryRemapper.AMBIENT_PARTICLE_763;
        }
        int id765 = PARTICLE_764_TO_765[id764];
        if (id765 < 0) {
            return RegistryRemapper.AMBIENT_PARTICLE_763;
        }
        return RegistryRemapper.mapParticle(id765);
    }

    /**
     * 763のエンティティ種別ID → 764の種別ID。763と764の登録は同一の並び
     * （breeze／wind_charge は両方とも無い）のため恒等写像。範囲外はブタ（72）。
     */
    public static int mapEntityType763To764(int id763) {
        if (id763 < 0 || id763 > RegistryRemapper.MAX_ENTITY_TYPE_764) {
            return RegistryRemapper.PIG_ENTITY_TYPE_764;
        }
        return id763;
    }

    /**
     * 764のエンティティ種別ID → 763の種別ID。登録は同一の並びのため恒等写像。
     * 範囲外はブタ（72）。
     */
    public static int mapEntityType764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.MAX_ENTITY_TYPE_764) {
            return RegistryRemapper.PIG_ENTITY_TYPE_763;
        }
        return id764;
    }

    /**
     * 763のブロックID（Block Action 用）→ 764のブロックID。登録は同一の
     * 並びのため恒等写像。範囲外は石（1）。
     */
    public static int mapBlock763To764(int id763) {
        if (id763 < 0 || id763 > RegistryRemapper.MAX_BLOCK_764) {
            return RegistryRemapper.STONE_BLOCK_764;
        }
        return id763;
    }

    /**
     * 764のブロックID（Block Action 用）→ 763のブロックID。登録は同一の
     * 並びのため恒等写像。範囲外は石（1）。
     */
    public static int mapBlock764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.MAX_BLOCK_764) {
            return RegistryRemapper.STONE_BLOCK_763;
        }
        return id764;
    }

    /**
     * 763のメニュー種別ID（Open Window 用）→ 764の種別ID。登録は同一の
     * 並び（crafter_3x3 は両方とも無い）のため恒等写像。範囲外は作業台（11）。
     */
    public static int mapMenu763To764(int id763) {
        if (id763 < 0 || id763 > RegistryRemapper.MAX_MENU_764) {
            return RegistryRemapper.CRAFTING_MENU_764;
        }
        return id763;
    }

    /**
     * 764のメニュー種別ID（Open Window 用）→ 763の種別ID。登録は同一の
     * 並びのため恒等写像。範囲外は作業台（11）。
     */
    public static int mapMenu764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.MAX_MENU_764) {
            return RegistryRemapper.CRAFTING_MENU_763;
        }
        return id764;
    }

    /**
     * 763のエフェクトID → 764のエフェクトID。763は1ベース（Speed=1）、
     * 764は0ベース（Speed=0）のため1引く。範囲外は Speed（0）。
     */
    public static int mapEffect763To764(int id763) {
        if (id763 < 1 || id763 > RegistryRemapper.MAX_EFFECT_764 + 1) {
            return RegistryRemapper.SPEED_EFFECT_764;
        }
        return id763 - 1;
    }

    /**
     * 764のエフェクトID → 763のエフェクトID。764は0ベース、763は1ベースの
     * ため1足す。範囲外は Speed（1）。
     */
    public static int mapEffect764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.MAX_EFFECT_764) {
            return RegistryRemapper.SPEED_EFFECT_763;
        }
        return id764 + 1;
    }

    /**
     * 763のブロックエンティティ種別ID → 764の種別ID。0〜40 は同一の並びの
     * ため恒等写像。crafter（41）／trial_spawner（42）は両方とも無いため
     * {@link RegistryRemapper#DROP}。範囲外も {@link RegistryRemapper#DROP}。
     */
    public static int mapBlockEntityType763To764(int id763) {
        if (id763 < 0 || id763 > RegistryRemapper.BLOCK_ENTITY_TRIAL_SPAWNER_765) {
            return RegistryRemapper.DROP;
        }
        if (id763 == RegistryRemapper.BLOCK_ENTITY_CRAFTER_765
                || id763 == RegistryRemapper.BLOCK_ENTITY_TRIAL_SPAWNER_765) {
            return RegistryRemapper.DROP;
        }
        return id763;
    }

    /**
     * 764のブロックエンティティ種別ID → 763の種別ID。0〜40 は同一の並びの
     * ため恒等写像。crafter（41）／trial_spawner（42）は両方とも無いため
     * {@link RegistryRemapper#DROP}。範囲外も {@link RegistryRemapper#DROP}。
     */
    public static int mapBlockEntityType764To763(int id764) {
        if (id764 < 0 || id764 > RegistryRemapper.BLOCK_ENTITY_TRIAL_SPAWNER_765) {
            return RegistryRemapper.DROP;
        }
        if (id764 == RegistryRemapper.BLOCK_ENTITY_CRAFTER_765
                || id764 == RegistryRemapper.BLOCK_ENTITY_TRIAL_SPAWNER_765) {
            return RegistryRemapper.DROP;
        }
        return id764;
    }

    /**
     * サーバー（765）とクライアントの間のバージョン差の一覧。
     * 765のクライアントには差がないので空の一覧を返す。
     * 対応していないプロトコルには、対応しない旨の差を一つだけ返す。
     */
    public static List<Difference> differences(int clientProtocol) {
        if (clientProtocol == SERVER_PROTOCOL) {
            return List.of();
        }
        if (clientProtocol == ProtocolVersion.V1_20_2) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が764と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.20.2と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は765と同じ形。",
                            Handling.UNAFFECTED,
                            "764の Login Start は名前＋UUID必須で、763の bool 付き任意UUID"
                                    + "ではない。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは764にもある。そのまま中継する。",
                            Handling.TRANSLATED,
                            "764はログイン成功のあと設定フェーズに入る（763と違い、"
                                    + "プロキシが設定フェーズを消費する必要は無い）。"
                                    + "Registry Data は765と同じく1個の名前なし NBT codec で、"
                                    + "そのまま中継する。リソースパックだけは形が違う"
                                    + "（765の Add／Remove に対し、764は UUID なしの単一パケット）"
                                    + "ため変換する。"),
                    new Difference("BLOCK",
                            "1.20.3で追加・改名されたブロックが764には無い。",
                            Handling.FALLBACK,
                            "crafter→crafting_table、trial_spawner→spawner、"
                                    + "short_grass→grass に置き換える（SimpleBlockTranslator）。"
                                    + "763と764のブロック状態IDは同じ並びのため、チャンクの"
                                    + "状態ID変換は763向けのものがそのまま使える。"),
                    new Difference("SPAWN",
                            "スポーン開始の合図が違う。",
                            Handling.TRANSLATED,
                            "765（1.20.3以降）は Game Event 13（チャンク待ち開始）が要るが、"
                                    + "764にはイベント13が無い。764向けにはそのイベントを落とし、"
                                    + "デフォルトスポーン位置パケットをスポーンの合図にする。"),
                    new Difference("CHAT",
                            "チャットはテキスト内容は同じだが、パケット番号が違う。",
                            Handling.NEEDS_VERIFICATION,
                            "パケットIDはバージョンごとに振り直されるため、番号の付け替えが必須。"
                                    + "テキストは764では JSON 文字列、765では NBT のため、"
                                    + "763向けと同じ NBT→JSON 文字列変換を使う。"
                                    + "署名フィールドの差分はパケット単位で未検証のため、実機での"
                                    + "チャット送受信の確認がまだ要る。"),
                    new Difference("MOVE",
                            "移動（位置・向き）は両方にあるが、パケット番号が違う。",
                            Handling.NEEDS_VERIFICATION,
                            "位置・向きパケット自体は両バージョンにある。番号の付け替えが必須で、"
                                    + "フィールド単位の照合は未検証のため、実機での移動確認がまだ要る。"),
                    new Difference("RESOURCE_PACK",
                            "リソースパックの送り方が違う。",
                            Handling.TRANSLATED,
                            "764は設定フェーズとプレイ状態の両方で、UUID なしの単一パケット"
                                    + "（URL＋SHA-1＋forced＋任意プロンプト）を使う。765の"
                                    + "Add Resource Pack（UUID付き）を764の形に変換し、"
                                    + "Remove Resource Pack は764に相当が無いので落とす。"
                                    + "なおDetergentMCのデモパックは pack_format 22"
                                    + "（1.20.3以降用）で作られているため、764クライアントには"
                                    + "「互換性なし」と表示される可能性があり、要確認。"));
        }
        if (clientProtocol == ProtocolVersion.V1_20_5) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が766と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.20.4と1.20.5で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化は765と同じ形。Login Success だけ末尾に "
                                    + "strictErrorHandling（bool）が増えた。",
                            Handling.TRANSLATED,
                            "765の Login Success に false を1バイト足して766形式にする。"
                                    + "online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは766にもある。そのまま中継するが、Registry Data の"
                                    + "形式が違う（765は1個の codec、766はレジストリごとの"
                                    + "エントリ配列）。",
                            Handling.TRANSLATED,
                            "Registry Data は765の codec を766のエントリ配列に組み替え、"
                                    + "Add／Remove Resource Pack は番号だけ付け替える（形式は同じ）。"
                                    + "Cookie Request／Select Known Packs（766のみ）はプロキシが"
                                    + "空の応答で返す。Tags はレジストリ番号がずれるため落とす。"),
                    new Difference("PLAY",
                            "1.20.5でスロットがデータコンポーネント化され、プレイ状態の"
                                    + "多くのパケットの形式が変わった。",
                            Handling.NEEDS_VERIFICATION,
                            "プレイ状態の766変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の766対応を待つ。設定フェーズまでの"
                                    + "参加フローは振り分け済みだが、プレイ中のパケット変換は"
                                    + "未実装のものがある。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が767と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.20.5と1.21で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化は766と同じ形。Login Success だけ末尾に "
                                    + "strictErrorHandling（bool）がある（766と同じ）。",
                            Handling.TRANSLATED,
                            "766の Login Success と767の Login Success は同じ形。"
                                    + "765の Login Success に false を1バイト足して767形式にする。"
                                    + "online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは767にもあり、766と同形。そのまま中継するが、"
                                    + "新規パケットが6件ある。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は766と同じ。Registry Data の形式も"
                                    + "766と同じエントリ配列だが、順序が変わるレジストリ"
                                    + "（エンチャント・絵画・旗・トリム・ダメージ種別）は名前で"
                                    + "並べ替える。767で追加された Server Links（クライアント行き・"
                                    + "サーバー行き）と Custom Report Details（クライアント行き・"
                                    + "サーバー行き）は、古いクライアントには落とし、767クライアントには通す。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が766から変わった（属性・レシピ種別など）。",
                            Handling.TRANSLATED,
                            "Update Attributes は属性の並びが変わり（新規9件が名前順に挿入されて"
                                    + "番号がずれる）、modifier の UUID が文字列になったため変換が要る"
                                    + "（RegistryRemapper の767対応表）。Join Game・Respawn・"
                                    + "Update Attributes 以外のプレイ状態のパケットは形式も766と同じ。"),
                    new Difference("PLAY",
                            "767で新規のパケットが6件ある（設定の Server Links・"
                                    + "Custom Report Details が各2件、プレイの同2件）。"
                                    + "既存パケットのIDは766から1件も変わらない。",
                            Handling.TRANSLATED,
                            "プレイ状態の767変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の767対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Damage Event は"
                                    + "766⇔767で変換する。残る制限：Window Click・"
                                    + "Set Creative Slot は763／764／765向けに落とす。"
                                    + "Declare Recipes・Tags・Statistics・World Event は全方向で"
                                    + "未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_2) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が768と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.1と1.21.2で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化は767と同じ形。Login Success だけ末尾の "
                                    + "strictErrorHandling（bool）が768で廃止された。",
                            Handling.TRANSLATED,
                            "767の Login Success から末尾の bool を1バイト落として768形式にする。"
                                    + "online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは768にもあり、767と同形。そのまま中継するが、"
                                    + "新規パケットは無い。Registry Data の順序が変わる"
                                    + "レジストリがある。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は767と同じ。Registry Data の形式も"
                                    + "767と同じエントリ配列だが、順序が変わるレジストリ"
                                    + "（ブロック状態・アイテム・エンティティ・音・属性・"
                                    + "ダメージ種別など）は名前で並べ替える。Custom Report Details・"
                                    + "Server Links は767にも768にもある。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が767から大きく変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・属性・データコンポーネント種別）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 26,684→27,318件（ペールオーク・クリエイキングハートの"
                                    + "追加と挿入によるずれ）、アイテムは 1,333→1,375件"
                                    + "（バンドル16色・ペールオーク類・クリエイキングのスポーンエッグなど）、"
                                    + "エンティティは 130→150件（ボートが木材ごとの種別に分裂、"
                                    + "クリエイキングの追加）、音は 1,611→1,636件、属性は 31→32件"
                                    + "（tempt_range の追加と接頭辞の除去）、データコンポーネント種別は"
                                    + " 57→67件（RegistryRemapper の768対応表）。"
                                    + "絵画・旗の模様・トリム・楽器・ジュークボックスの歌は変わらない。"
                                    + "ダメージ種別に ender_pearl・mace_smash が追加された。"),
                    new Difference("PLAY",
                            "768で新規のパケットが11件あり、既存パケットのIDも145件変わる。"
                                    + "移動パケット・Set Cooldown・Explosion・Update Time・"
                                    + "Synchronize Player Position の形式も変わった。",
                            Handling.TRANSLATED,
                            "プレイ状態の768変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の768対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは767⇔768で変換する。残る制限：Window Click・"
                                    + "Set Creative Slot は763／764／765向けに落とす。"
                                    + "Declare Recipes・Tags・Statistics・World Event は全方向で"
                                    + "未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_4) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が769と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.3と1.21.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は768と同じ形。",
                            Handling.UNAFFECTED,
                            "768と769の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは769にもあり、768と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（バイオーム pale_garden の追加）。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は768と同じ。Registry Data の形式も"
                                    + "768と同じエントリ配列だが、バイオームだけ順序が変わる"
                                    + "（pale_garden が 39 に入り、plains が 39→40 にずれる）。"
                                    + "Custom Report Details・Server Links は768にも769にもある。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が768から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・パーティクル・バイオーム）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,318→27,866件（樹脂・アイブロッサム・"
                                    + "クリエイキングハートの追加と挿入によるずれ）、アイテムは"
                                    + " 1,375→1,385件（樹脂のレンガなど）、エンティティは 150→149件"
                                    + "（creaking_transient の削除）、音は 1,636→1,651件、"
                                    + "パーティクルは 111→112件（pale_oak_leaves の追加）。"
                                    + "バイオームに pale_garden が追加された。"
                                    + "属性・エンチャント・データコンポーネント種別は変わらない"
                                    + "（RegistryRemapper の769対応表）。ダメージ種別・絵画・"
                                    + "旗の模様・トリム・楽器・ジュークボックスの歌は変わらない。"),
                    new Difference("PLAY",
                            "769で新規のパケットが3件あり、既存パケットのIDも24件変わる"
                                    + "（すべてサーバー行き）。Vehicle Move・World Particles・"
                                    + "Player Info の形式も変わった。",
                            Handling.TRANSLATED,
                            "新規は Pick Item From Block／Entity と Player Loaded、削除は"
                                    + " Pick Item。Vehicle Move の末尾に onGround、World Particles"
                                    + " に alwaysShow、Player Info に showHat が付いた。"
                                    + "プレイ状態の769変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の769対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは768⇔769で変換する。残る制限：Pick Item は"
                                    + "両方向とも落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_5) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が770と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.4と1.21.5で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は769と同じ形。",
                            Handling.UNAFFECTED,
                            "769と770の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは770にもあり、769と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（ブロック状態・アイテム・エンティティ・音・"
                                    + "パーティクル）。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は769と同じ。Registry Data の形式も"
                                    + "769と同じエントリ配列だが、ブロック状態・アイテム・"
                                    + "エンティティ・音・パーティクルの順序が変わる。"
                                    + "Custom Report Details・Server Links は769にも770にもある。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が769から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・パーティクル）。"
                                    + "データコンポーネント種別も 67→96件に増えた。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,866→27,914件（茂み・ワイルドフラワー・"
                                    + "リーフリッター・ホタルブッシュ・サボテンの花・枯れ草・"
                                    + "テストブロックの追加と挿入によるずれ）、アイテムは"
                                    + " 1,385→1,396件（青・茶の卵など）、エンティティは 149→150件"
                                    + "（potion の splash／lingering への分割）、音は 1,651→1,702件、"
                                    + "パーティクルは 112→114件（tinted_leaves・firefly の追加）。"
                                    + "バイオーム・属性・エンチャント・メニューは変わらない。"
                                    + "データコンポーネント種別は 67→96件（tooltip_display の"
                                    + "追加と hide_tooltip／hide_additional_tooltip の統合など）。"
                                    + "ダメージ種別・絵画・旗の模様・トリム・楽器・ジュークボックスの"
                                    + "歌は変わらない。"),
                    new Difference("PLAY",
                            "770で新規のパケットが3件あり、既存パケットのIDも122件変わる"
                                    + "（送信側117件、受信側5件）。Chunk Data の heightmap、"
                                    + "Player Chat、Chat Message／Chat Command Signed、"
                                    + "Window Click／Set Creative Slot、Entity Metadata の"
                                    + "形式も変わった。",
                            Handling.TRANSLATED,
                            "新規は Test Instance Block Status（送信側）と Set Test Block・"
                                    + "Test Instance Block Action（受信側）、削除は"
                                    + " Spawn Entity Experience Orb（経験値オーブは通常の"
                                    + " Spawn Entity に統合）。Chunk Data の heightmap は"
                                    + " NBT から直接の配列に、Player Chat は先頭に globalIndex、"
                                    + "Chat Message／Chat Command Signed は末尾に checksum が"
                                    + "付いた。Window Click は HashedSlot、Set Creative Slot は"
                                    + " UntrustedSlot になった。Entity Metadata は cow／wolf／"
                                    + "frog／pig／chicken／painting の variant の型・番号が"
                                    + "変わった。プレイ状態の770変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の770対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは769⇔770で変換する。残る制限：Test Instance"
                                    + " Block 系のパケットは落とす。未対応のデータコンポーネント"
                                    + "種別は落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_6) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が771と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.5と1.21.6で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は770と同じ形。",
                            Handling.UNAFFECTED,
                            "770と771の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは771にもあり、770と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（ブロック状態・アイテム・エンティティ・音・"
                                    + "ジュークボックスの歌）。minecraft:dialog レジストリが"
                                    + "新規で、Show／Clear Dialog パケットも新規。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は770と同じ。Registry Data の形式も"
                                    + "770と同じエントリ配列だが、ブロック状態・アイテム・"
                                    + "エンティティ・音・ジュークボックスの歌の順序が変わる。"
                                    + "minecraft:dialog レジストリ（3件）が新規。"
                                    + "Custom Report Details・Server Links（サーバー行き）は"
                                    + "771で削除され、代わりに Custom Click Action（0x08）が"
                                    + "新規。Show Dialog（0x12）／Clear Dialog（0x11）も新規。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が770から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・ジュークボックスの歌・属性）。"
                                    + "minecraft:dialog レジストリが新規。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,914→27,946件（乾燥ガストの追加のみ）、"
                                    + "アイテムは 1,396→1,415件（ハーネス・乾燥ガストの"
                                    + "スポーンエッグなど）、エンティティは 150→151件"
                                    + "（ハッピーガストの追加）、音は 1,702→1,727件"
                                    + "（+29／-4、ambient と blow_out の衝突あり）。"
                                    + "属性は 32→35件（camera_distance・"
                                    + "waypoint_transmit_range・waypoint_receive_range の追加）。"
                                    + "ジュークボックスの歌は 19→20件（tears を17番に挿入）。"
                                    + "バイオーム・エンチャント・メニュー・パーティクルは変わらない。"
                                    + "データコンポーネント種別は 96件のまま。"
                                    + "ダメージ種別・絵画・旗の模様・トリム・楽器は変わらない"
                                    + "（ダメージ種別は770と同じ49種・同じ並び。実バニラ1.21.6"
                                    + "のキャプチャで確認済み）。"),
                    new Difference("PLAY",
                            "771で新規のパケットが8件あり、削除が2件、既存パケットのIDも"
                                    + "58件変わる（プレイ送信側）。クライアント行きのIDは"
                                    + "770と変わらない。ロケーターバー（tracked_waypoint）と"
                                    + "ダイアログ（show_dialog／clear_dialog）が新規。",
                            Handling.TRANSLATED,
                            "新規は Change Gamemode（送信側 0x04）・Custom Click Action"
                                    + "（送信側 0x41、設定 0x08）・Tracked Waypoint（0x83）・"
                                    + "Show Dialog（0x85）・Clear Dialog（0x84）・設定の"
                                    + "Show Dialog（0x12）／Clear Dialog（0x11）。"
                                    + "削除は Custom Report Details／Server Links（送信側）。"
                                    + "Change Gamemode 0x04 の挿入で送信側 0x04 以降が+1ずれる"
                                    + "（Chat Message 0x07→0x08、Window Click 0x10→0x11、"
                                    + "Set Creative Slot 0x36→0x37、Keep Alive 応答 0x1A→0x1B、"
                                    + "Ping 応答 0x2B→0x2C）。Chat Message の checksum・"
                                    + "Vehicle Move の onGround・Window Click の HashedSlot・"
                                    + "Set Creative Slot の UntrustedSlot・Player Chat の"
                                    + "globalIndex は770・771とも同じため、番号だけ付け替える。"
                                    + "プレイ状態の771変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の771対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは770⇔771で変換する。残る制限：ロケーターバー・"
                                    + "ダイアログ・Change Gamemode・Custom Click Action は770に"
                                    + "相当が無いため旧クライアント向けに落とす。ハッピーガストは"
                                    + "770ではガストに代替する。乾燥ガスト・ハーネスはアイテム・"
                                    + "ブロック状態の付け替えで通す。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_7) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が772と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.6と1.21.7／1.21.8で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は771と同じ形。",
                            Handling.UNAFFECTED,
                            "771と772の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは772にもあり、771と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（アイテム・音・絵画・ジュークボックスの歌）。"
                                    + "minecraft:dialog レジストリは771にもあり、Show／Clear Dialog "
                                    + "パケットも771と同形・同ID。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は771と同じ。Registry Data の形式も"
                                    + "771と同じエントリ配列だが、アイテム・音・絵画・"
                                    + "ジュークボックスの歌の順序が変わる。"
                                    + "minecraft:dialog レジストリ（3件）は771と同じ。"
                                    + "Custom Click Action（設定 0x08）・Show Dialog（0x12）・"
                                    + "Clear Dialog（0x11）は771と同ID・同形のため素通し。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が771から変わった（アイテム・音・絵画・"
                                    + "ジュークボックスの歌）。エフェクトは Unluck→BadLuck の"
                                    + "名前変更のみ。",
                            Handling.TRANSLATED,
                            "アイテムは 1,415→1,416件（music_disc_lava_chicken を1256番に挿入）、"
                                    + "音は 1,727→1,728件（music_disc.lava_chicken を939番に挿入）、"
                                    + "絵画は 50→51件（dennis を14番に挿入）、ジュークボックスの歌は"
                                    + " 20→21件（lava_chicken を9番に挿入）。"
                                    + "エフェクトは 38件のまま（Unluck→BadLuck の名前変更のみで番号は同一）。"
                                    + "ブロック状態・エンティティ・バイオーム・パーティクル・属性・"
                                    + "エンチャント・メニュー・データコンポーネント種別・ダメージ種別・"
                                    + "旗の模様・トリム・楽器は変わらない。"
                                    + "（絵画 dennis・ジュークボックス lava_chicken の位置は"
                                    + "実バニラ1.21.7／1.21.8のキャプチャで確認する。）"),
                    new Difference("PLAY",
                            "772で新規・削除・ID変更のパケットは無い（247件すべて771と同一）。"
                                    + "ロケーターバー・ダイアログ・Change Gamemode・Custom Click Action は"
                                    + "771にもあり、772ではそのまま使える。",
                            Handling.TRANSLATED,
                            "新規・削除のパケットは無く、既存パケットのIDも771と変わらない。"
                                    + "Chat Message の checksum・Vehicle Move の onGround・"
                                    + "Window Click の HashedSlot・Set Creative Slot の UntrustedSlot・"
                                    + "Player Chat の globalIndex は771・772とも同じため、"
                                    + "パケットIDの付け替えは不要。"
                                    + "プレイ状態の772変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の772対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは771⇔772で変換する。残る制限：ロケーターバー・"
                                    + "ダイアログ・Change Gamemode・Custom Click Action は770以下に"
                                    + "相当が無いため旧クライアント向けに落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_9) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が773と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.7／1.21.8と1.21.9／1.21.10で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は772と同じ形。",
                            Handling.UNAFFECTED,
                            "772と773の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは773にもあり、772と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（ブロック状態・アイテム・エンティティ・音・パーティクル）。"
                                    + "Code of Conduct が新規。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は772と同じ。Registry Data の形式も"
                                    + "772と同じエントリ配列だが、ブロック状態・アイテム・"
                                    + "エンティティ・音・パーティクルの順序が変わる。"
                                    + "minecraft:dialog レジストリは772と同じ。"
                                    + "Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）が新規。Show Dialog（0x12）・"
                                    + "Clear Dialog（0x11）・Custom Click Action（設定 0x08）は"
                                    + "772と同ID・同形のため素通し。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が772から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・パーティクル・ブロックエンティティ）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,946→29,671件（銅系ブロック・棚の追加）、"
                                    + "アイテムは 1,416→1,488件（銅の道具・防具・銅ゴーレムの"
                                    + "スポーンエッグなど）、エンティティは 151→153件"
                                    + "（銅ゴーレム・マネキンの追加）、音は 1,728→1,771件"
                                    + "（銅系の音＋weather.end_flash）、パーティクルは 114→115件"
                                    + "（copper_fire_flame を5番に挿入）、ブロックエンティティは"
                                    + " 47→49件（shelf を40番に挿入、copper_golem_statue を48番に"
                                    + "追加）。ブロック chain は iron_chain に改名。"
                                    + "バイオーム・エンチャント・メニュー・属性・"
                                    + "データコンポーネント種別・ダメージ種別・絵画・"
                                    + "ジュークボックスの歌・旗の模様・トリム・楽器は変わらない。"
                                    + "（絵画・ジュークボックスの歌は772と同一。実バニラ1.21.9／"
                                    + "1.21.10のキャプチャで確認する。）"),
                    new Difference("PLAY",
                            "773で新規のパケットが8件あり、削除が1件、クライアント行きの"
                                    + "パケットIDも108件変わる。デバッグ系のパケット"
                                    + "（debug_block_value など）が新規。",
                            Handling.TRANSLATED,
                            "新規は Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）・Debug Block Value（0x1A）・"
                                    + "Debug Chunk Value（0x1B）・Debug Entity Value（0x1C）・"
                                    + "Debug Event（0x1D）・Game Test Highlight Pos（0x27）・"
                                    + "Debug Subscription Request（送信側 0x16）。"
                                    + "削除は Debug Sample Subscription（送信側 0x16）。"
                                    + "デバッグ用パケットの挿入でプレイ送信側 0x1A 以降が+4、"
                                    + "0x27 以降が+5ずれる（Join Game 0x2B→0x30、"
                                    + "Chunk Data 0x27→0x2C、Keep Alive 0x26→0x2B、"
                                    + "Ping 0x36→0x3B）。送信側のパケットIDは772と変わらない"
                                    + "（Debug Sample Subscription の削除を除く）。"
                                    + "Chat Message の checksum・Vehicle Move の onGround・"
                                    + "Window Click の HashedSlot・Set Creative Slot の UntrustedSlot・"
                                    + "Player Chat の globalIndex は772・773とも同じため、"
                                    + "番号だけ付け替える。"
                                    + "プレイ状態の773変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の773対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは772⇔773で変換する。残る制限：デバッグ用"
                                    + "パケットは772に相当が無いため旧クライアント向けに落とす。"
                                    + "銅ゴーレム・マネキンは772ではブタに代替する。銅系アイテムは"
                                    + "石に代替する。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_11) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が774と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.9／1.21.10と1.21.11で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は773と同じ形。",
                            Handling.UNAFFECTED,
                            "773と774の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは774にもあり、773と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（アイテム・エンティティ・音・データコンポーネント種別）。"
                                    + "Code of Conduct は773と同じ。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は773と同じ。Registry Data の形式も"
                                    + "773と同じエントリ配列だが、アイテム・エンティティ・"
                                    + "音・データコンポーネント種別の順序が変わる。"
                                    + "minecraft:dialog レジストリは773と同じ。"
                                    + "Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）は773と同ID・同形のため素通し。Show Dialog（0x12）・"
                                    + "Clear Dialog（0x11）・Custom Click Action（設定 0x08）は"
                                    + "773と同ID・同形のため素通し。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が773から変わった（アイテム・"
                                    + "エンティティ・音・データコンポーネント種別・エンチャント・効果）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 29,671件で773と同一（1.21.11はブロックを追加しない）、"
                                    + "アイテムは 1,488→1,505件（槍7種・ノーチラス防具5種・"
                                    + "ネザライト馬鎧・スポーンエッグ4種）、エンティティは 153→157件"
                                    + "（ノーチラス・ゾンビノーチラス・キャメルハスク・パーチドの追加）、"
                                    + "音は 1,771→1,838件（ノーチラス・キャメルハスク・パーチド・"
                                    + "ゾンビノーチラス・槍の音67件）、パーティクルは 115件で同一、"
                                    + "ブロックエンティティは 49件で同一、データコンポーネント種別は"
                                    + " 96→104件（use_effects・minimum_attack_charge・damage_type・"
                                    + "attack_range・piercing_weapon・kinetic_weapon・"
                                    + "swing_animation・zombie_nautilus/variant の8種を追加）、"
                                    + "エンチャントは 42→43件（lunge を22番に挿入）、効果は 39→40件"
                                    + "（BreathOfTheNautilus を末尾に追加）。バイオーム・メニュー・属性・"
                                    + "ダメージ種別・絵画・ジュークボックスの歌・旗の模様・トリム・楽器は変わらない。"),
                    new Difference("PLAY",
                            "774で新規のパケットは0件、削除も0件、クライアント行きの"
                                    + "パケットIDも773とまったく同じ。",
                            Handling.TRANSLATED,
                            "新規・削除のパケットは無い。プレイのクライアント行き・サーバー行きとも"
                                    + "773と同ID（Join Game 0x30、Chunk Data 0x2C、Keep Alive 0x2B、"
                                    + "Ping 0x3B、Respawn 0x50、Disconnect 0x20、Add Resource Pack 0x4F、"
                                    + "Spawn Position 0x5F、Player Info Update 0x44、Tracked Waypoint 0x88、"
                                    + "Show Dialog 0x8A、Clear Dialog 0x89）。"
                                    + "Enchant Item（送信側）の enchantment フィールドが varint から"
                                    + " i8 に変わったが、値は 0〜42 のためバイト列は同じ。"
                                    + "Chat Message の checksum・Vehicle Move の onGround・"
                                    + "Window Click の HashedSlot・Set Creative Slot の UntrustedSlot・"
                                    + "Player Chat の globalIndex は773・774とも同じため、"
                                    + "番号だけ付け替える必要も無い。"
                                    + "プレイ状態の774変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の774対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは773⇔774で変換する。残る制限：デバッグ用"
                                    + "パケットは773に相当が無いわけではなく同IDのため素通しするが、"
                                    + "ノーチラス・ゾンビノーチラス・キャメルハスク・パーチドは773では"
                                    + "ブタに代替する。槍・ノーチラス防具は石に代替する。Window Click・"
                                    + "Set Creative Slot は763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V26_1) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が775と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.11と26.1で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は774と同じ形。",
                            Handling.UNAFFECTED,
                            "774と775の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは775にもあり、774と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（アイテム・エンティティ・音・データコンポーネント種別・スロット表示）。"
                                    + "Code of Conduct は774と同じ。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は774と同じ。Registry Data の形式も"
                                    + "774と同じエントリ配列だが、アイテム・エンティティ・"
                                    + "音・データコンポーネント種別・スロット表示の順序が変わる。"
                                    + "minecraft:dialog レジストリは774と同じ。"
                                    + "Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）は774と同ID・同形のため素通し。Show Dialog（0x12）・"
                                    + "Clear Dialog（0x11）・Custom Click Action（設定 0x08）は"
                                    + "774と同ID・同形のため素通し。設定フェーズの新規パケットは無い。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が774から変わった（アイテム・"
                                    + "ブロック状態・音・パーティクル・データコンポーネント種別・"
                                    + "スロット表示・エンティティメタデータ種別）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 29,671→29,873件（金のタンポポ2種と"
                                    + "トランペットの音符ブロック200状態を追加）、"
                                    + "アイテムは 1,505→1,506件（金のタンポポを230番に挿入）、"
                                    + "エンティティは 157件で同一、"
                                    + "音は 1,838→1,902件（子猫・子鶏・子馬・子豚・子狼などの"
                                    + "子どもの音と金のタンポポ・トランペット64件）、"
                                    + "パーティクルは 115→117件（pause_mob_growth・"
                                    + "reset_mob_growth を50・51番に挿入）、"
                                    + "ブロックエンティティは 49件で同一（本物の26.1の codec で"
                                    + "Stage E に確認）、データコンポーネント種別は"
                                    + " 104→110件（additional_trade_cost・dye・"
                                    + "pig/sound_variant・cow/sound_variant・"
                                    + "chicken/sound_variant・cat/sound_variant の6種を追加）、"
                                    + "スロット表示は 8→11件（with_any_potion・"
                                    + "only_with_component・dyed の3種を追加）、"
                                    + "エンチャントは 43件で同一、効果は 40件で同一、"
                                    + "環境属性は 45→48件（visual系3種を追加）、"
                                    + "レシピシリアライザは 22→21件（crafting_special_* 3種を"
                                    + "削除し crafting_dye・crafting_imbue を追加）。"
                                    + "バイオーム・メニュー・属性・ダメージ種別・絵画・"
                                    + "ジュークボックスの歌・旗の模様・トリム・楽器は変わらない。"),
                    new Difference("PLAY",
                            "775で新規のパケットは5件、削除は0件、パケットIDは"
                                    + "774から164件変わる（クライアント行き100件・サーバー行き64件）。",
                            Handling.TRANSLATED,
                            "新規パケットは Game Rule Values（0x27）・"
                                    + "Low Disk Space Warning（0x32）・Attack（送信側 0x01）・"
                                    + "Set Game Rule（送信側 0x39）・Spectate Entity（送信側 0x3E）。"
                                    + "送信側の先頭への Attack 挿入で送信側 0x01 以降が+1、"
                                    + "0x39 以降がさらに+1、0x3E 以降がさらに+1ずれる。"
                                    + "クライアント行きは 0x27 と 0x32 の挿入で 0x27 以降が+1、"
                                    + "0x32 以降が+2ずれる（Join Game 0x31、Chunk Data 0x2D、"
                                    + "Keep Alive 0x2C、Ping 0x3D、Respawn 0x52、"
                                    + "Disconnect 0x20、Add Resource Pack 0x51、"
                                    + "Spawn Position 0x61、Player Info Update 0x46、"
                                    + "Tracked Waypoint 0x8A、Show Dialog 0x8C、Clear Dialog 0x8B）。"
                                    + "Update Time は time・tickDayTime から clockUpdates 配列へ"
                                    + "再構成されたため変換する。Use Entity は再構成され、攻撃は"
                                    + " Attack パケットへ分離されたため変換する。Enchant Item"
                                    + "（送信側）の enchantment フィールドが i8 から varint に"
                                    + "戻ったが、値は 0〜42 のためバイト列は同じ。Client Command に"
                                    + " request_gamerule_values（action 2）が追加された。"
                                    + "Advancements のアイコンが Slot から ItemStackTemplate に"
                                    + "変わった。Entity Metadata の種別は 39→43件（cat／cow／"
                                    + "pig／chicken の sound_variant 4種を挿入）。"
                                    + "Chat Message の checksum・Vehicle Move の onGround・"
                                    + "Window Click の HashedSlot・Set Creative Slot の UntrustedSlot・"
                                    + "Player Chat の globalIndex は774・775とも同じため、"
                                    + "番号だけ付け替える。"
                                    + "プレイ状態の775変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の775対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは774⇔775で変換する。残る制限：金のタンポポは"
                                    + "774では石に代替する。子どもの音バリアントは774では親の音に"
                                    + "代替する。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_17_1) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が756と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.17.1と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始は名前だけ（756）。playerUUID は無い。署名データも無い（署名付きチャットは759で導入）。",
                            Handling.TRANSLATED,
                            "756の Login Start は名前だけで、760の任意UUIDも759の署名データも無い。"
                                    + "プロキシは認証済みの名前・UUID で Login Start を作り直すため、"
                                    + "クライアントの署名データは内部へは届かない。"),
                    new Difference("CONFIGURATION",
                            "1.20.2で追加された「設定フェーズ」が756クライアントには無い（757と同じ）。",
                            Handling.TRANSLATED,
                            "756はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                    + "設定フェーズで送るレジストリ情報は、756向けには Join Game の"
                                    + "codec としてプレイ状態で送り直す。設定フェーズ用のパケットを"
                                    + "756に送ってはいけない。プロキシは765クライアント役として"
                                    + "設定フェーズを消費する。"),
                    new Difference("REGISTRY",
                            "757と756のレジストリは1.18の追加分だけ違う。ブロック状態 20,342→20,342、"
                                    + "ブロック 898→898、アイテム 1,100→1,101、エンティティ 113→113、"
                                    + "音 1,190→1,203、パーティクル 89→88、エンチャント 38→38、"
                                    + "バイオーム 81→61、ブロックエンティティ 34→34、メニュー 24→24。",
                            Handling.TRANSLATED,
                            "ブロック状態は 20,342→20,342（同一・同一順序）、"
                                    + "ブロックは 898→898（同一・同一順序）、アイテムは 1,100→1,101"
                                    + "（新規 1：music_disc_otherside を1027番に挿入）、"
                                    + "エンティティは 113→113（同一）、音は 1,190→1,203"
                                    + "（新規 13）、パーティクルは 89→88（1.17の barrier・light が"
                                    + "1.18の block_marker に統合）、エンチャントは 38→38（同一）、"
                                    + "バイオームは 81→61（改名 11・削除 26・新規 6。757の61件に対し"
                                    + "756は deep_dark・dripstone_caves・lush_caves など旧来の"
                                    + "バイオームを多く含む）、ブロックエンティティは 34→34（同一）、"
                                    + "メニュー 24→24（同一）。"
                                    + "改名・削除はバイオームとパーティクル（統合）のみ"
                                    + "（RegistryRemapper の756対応表）。"),
                    new Difference("PLAY",
                            "プレイ状態は757↔756の直接変換のあと、既存の757↔759↔760↔761↔762↔763↔764↔765の変換を重ねる。"
                                    + "新規パケットは0件、削除は1件、パケットIDは16件変わる。",
                            Handling.TRANSLATED,
                            "Join Game 0x26（757では0x26）、Chunk Data 0x22（0x22）。"
                                    + "Join Game は simulationDistance の有無だけが違う"
                                    + "（1.18は viewDistance の後に simulationDistance を追加し、"
                                    + "1.17.1には無い。dimension は両方とも NBT 値、"
                                    + "lastDeathLocation はどちらにも無い）。Respawn は同一。"
                                    + "Registry Data パケットはどちらにも無い。"
                                    + "削除は simulation_distance 1件（757で0x57。1.18で追加された"
                                    + "描画距離通知で、1.17.1には無い）。変更16件はすべて"
                                    + "0x57の削除による-1ずれ（0x58以降）。"
                                    + "756クライアントには pack_format 7 のパックを出す"
                                    + "（プレイ状態での旧式オファー）。"
                                    + "Window Click・Set Creative Slot は757↔756で番号だけ付け替える"
                                    + "（落とさない）。Declare Recipes・Tags・Statistics・"
                                    + "World Event は全方向で未対応。"));
        }
        if (clientProtocol == ProtocolVersion.V1_17) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が755と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.17と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始は名前だけ（755）。playerUUID は無い。署名データも無い（署名付きチャットは759で導入）。",
                            Handling.TRANSLATED,
                            "755の Login Start は名前だけで、760の任意UUIDも759の署名データも無い。"
                                    + "プロキシは認証済みの名前・UUID で Login Start を作り直すため、"
                                    + "クライアントの署名データは内部へは届かない。"),
                    new Difference("CONFIGURATION",
                            "1.20.2で追加された「設定フェーズ」が755クライアントには無い（756と同じ）。",
                            Handling.TRANSLATED,
                            "755はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                    + "設定フェーズで送るレジストリ情報は、755向けには Join Game の"
                                    + "codec としてプレイ状態で送り直す。設定フェーズ用のパケットを"
                                    + "755に送ってはいけない。プロキシは765クライアント役として"
                                    + "設定フェーズを消費する。"),
                    new Difference("REGISTRY",
                            "756と755のレジストリは同一。ブロック状態 20,342→20,342、"
                                    + "ブロック 898→898、アイテム 1,100→1,100、エンティティ 113→113、"
                                    + "音 1,190→1,190、パーティクル 89→89、エンチャント 38→38、"
                                    + "バイオーム 81→81、ブロックエンティティ 34→34、メニュー 24→24。",
                            Handling.TRANSLATED,
                            "ブロック状態は 20,342→20,342（同一・同一順序。実バニラjarの"
                                    + "data-generator報告は1.17と1.17.1でバイト同一）、"
                                    + "ブロックは 898→898（同一・同一順序）、アイテムは 1,100→1,100"
                                    + "（同一）、エンティティは 113→113（同一）、音は 1,190→1,190"
                                    + "（同一）、パーティクルは 89→89（同一）、エンチャントは 38→38"
                                    + "（同一）、バイオームは 81→81（同一。id 0〜175の非連番も両方同じ）、"
                                    + "ブロックエンティティは 34→34（同一）、メニュー 24→24（同一）。"
                                    + "改名・削除・新規は0件（RegistryRemapper の755対応表。補正も全種0件）。"
                                    + "verify-755-tables.py は43件中43件通過。"),
                    new Difference("PLAY",
                            "プレイ状態は756↔755の直接変換のあと、既存の756↔757↔759↔760↔761↔762↔763↔764↔765の変換を重ねる。"
                                    + "新規パケットは0件、削除は0件、パケットIDは0件変わる。",
                            Handling.TRANSLATED,
                            "Join Game 0x26（756では0x26）、Chunk Data 0x22（0x22）。"
                                    + "Join Game・Respawn は756と同一（実バニラ1.17と1.17.1の"
                                    + "Join Game は両方31,555バイトで、差分はentityId・hashedSeedの"
                                    + "9バイトのみ。dimension は両方とも NBT 値、"
                                    + "simulationDistance・lastDeathLocation はどちらにも無い）。"
                                    + "Registry Data パケットはどちらにも無い。"
                                    + "唯一の違いは内部名の改名1件（destroy_entity→entity_destroy）で、"
                                    + "ID は同じ0x3aのため番号の付け替えは不要。"
                                    + "755クライアントには pack_format 7 のパックを出す"
                                    + "（プレイ状態での旧式オファー）。"
                                    + "Window Click・Set Creative Slot は756↔755で番号も変わらない"
                                    + "（落とさない）。Declare Recipes・Tags・Statistics・"
                                    + "World Event は全方向で未対応。"));
        }
        if (clientProtocol == ProtocolVersion.V1_18) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が757と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.18と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始は名前だけ（757）。playerUUID は無い。署名データも無い（署名付きチャットは759で導入）。",
                            Handling.TRANSLATED,
                            "757の Login Start は名前だけで、760の任意UUIDも759の署名データも無い。"
                                    + "プロキシは認証済みの名前・UUID で Login Start を作り直すため、"
                                    + "クライアントの署名データは内部へは届かない。"),
                    new Difference("CONFIGURATION",
                            "1.20.2で追加された「設定フェーズ」が757クライアントには無い（759と同じ）。",
                            Handling.TRANSLATED,
                            "757はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                    + "設定フェーズで送るレジストリ情報は、757向けには Join Game の"
                                    + "codec としてプレイ状態で送り直す。設定フェーズ用のパケットを"
                                    + "757に送ってはいけない。プロキシは765クライアント役として"
                                    + "設定フェーズを消費する。"),
                    new Difference("REGISTRY",
                            "759と757のレジストリは1.19の追加分だけ違う。ブロック状態 20,342→21,448、"
                                    + "ブロック 898→933、アイテム 1,101→1,152、エンティティ 113→118、"
                                    + "音 1,203→1,321、パーティクル 88→93、エンチャント 38→39、"
                                    + "バイオーム 61→63、ブロックエンティティ 34→36。",
                            Handling.TRANSLATED,
                            "ブロック状態は 20,342→21,448（新規 994＋葉の waterlogged 追加 112）、"
                                    + "ブロックは 898→933（新規 35：マングローブ一式・泥・スカルク・"
                                    + "フロッグライトなど）、アイテムは 1,101→1,152（新規 51）、"
                                    + "エンティティは 113→118（新規 5：アレイ・チェスト付きボート・"
                                    + "カエル・オタマジャクシ・ウォーデン）、音は 1,203→1,321"
                                    + "（新規 118）、パーティクルは 88→93（新規 5）、エンチャントは"
                                    + " 38→39（swift_sneak を12番に挿入）、バイオームは 61→63"
                                    + "（mangrove_swamp を7番・deep_dark を52番に挿入）、"
                                    + "ブロックエンティティは 34→36（sculk_catalyst・sculk_shrieker）。"
                                    + "改名・削除は0件（RegistryRemapper の757対応表）。"),
                    new Difference("PLAY",
                            "プレイ状態は759↔757の直接変換のあと、既存の759↔760↔761↔762↔763↔764↔765の変換を重ねる。"
                                    + "新規パケットは5件、削除は8件、パケットIDは123件変わる。",
                            Handling.TRANSLATED,
                            "Join Game 0x23（759では0x26）、Chunk Data 0x1F（0x22）。"
                                    + "Join Game・Respawn は dimensionType が値（757）⇔キー（759）で、"
                                    + "lastDeathLocation は759で追加され757には無い。"
                                    + "チャットは757では署名なしの chat（クライアント行き0x0F・"
                                    + "サーバー行き0x03）で、759の System Chat／Player Chat／"
                                    + "Chat Message／Chat Command に分離された署名付き形式とは違うため変換する。"
                                    + "新規は spawn_entity_living（0x02）・spawn_entity_painting（0x03）・"
                                    + "sculk_vibration_signal（0x05）・chat（0x0F）・chat_to_server（0x03）。"
                                    + "削除は署名付きチャット関係8件（System Chat・Player Chat・"
                                    + "Should Display Chat Preview・Server Data・Chat Preview・"
                                    + "Chat Message・Chat Command・Chat Preview To Server）。"
                                    + "757クライアントには pack_format 8 のパックを出す"
                                    + "（プレイ状態での旧式オファー）。"
                                    + "Window Click・Set Creative Slot は759↔757で番号だけ付け替える"
                                    + "（落とさない）。Declare Recipes・Tags・Statistics・"
                                    + "World Event は全方向で未対応。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が759と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.19と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始は署名データ付き（759）。playerUUID は無い。署名付きチャットは759（1.19）で導入された初代の形式。",
                            Handling.TRANSLATED,
                            "759の Login Start は名前＋署名データ（option）。760は任意UUIDが"
                                    + "足され、761以降は署名データが無く、代わりにプレイ状態の "
                                    + "Chat Session Update で鍵を送る。プロキシは認証済みの名前・UUID で "
                                    + "Login Start を作り直すため、クライアントの署名データは内部へは届かない。"
                                    + "Chat Message／Chat Command／Player Chat は759と760で形式が違うため変換する。"),
                    new Difference("CONFIGURATION",
                            "1.20.2で追加された「設定フェーズ」が759クライアントには無い（760と同じ）。",
                            Handling.TRANSLATED,
                            "759はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                    + "設定フェーズで送るレジストリ情報は、759向けには Join Game の"
                                    + "codec としてプレイ状態で送り直す。設定フェーズ用のパケットを"
                                    + "759に送ってはいけない。プロキシは765クライアント役として"
                                    + "設定フェーズを消費する。"),
                    new Difference("REGISTRY",
                            "759と760のレジストリは全カテゴリ同一（恒等写像）。パケットIDは760→759で131件変更。",
                            Handling.TRANSLATED,
                            "ブロック状態 21,448→21,448（同一）、"
                                    + "アイテム 1,152→1,152（同一）、"
                                    + "音 1,321→1,321（同一）、パーティクル 93→93（同一）、"
                                    + "エンティティ 118→118（同一）、"
                                    + "バイオーム 63→63（同一。plains は759=1・760=1）、"
                                    + "ダメージ種別は759の codec に存在しない（1.19.4で追加。762の42件の恒等写像と仮定）、"
                                    + "メニュー 24→24（同一）、"
                                    + "ブロックエンティティ 36→36（同一）。"
                                    + "唯一の既知の違いは blocks.json の drops メタデータ 3 件で ID に影響なし。"),
                    new Difference("PLAY",
                            "プレイ状態は760↔759の直接変換のあと、既存の760↔761↔762↔763↔764↔765の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Player Chat／Chat Message／Chat Command の署名付きチャット形式、"
                                    + "System Chat の type varint、Server Data の enforcesSecureChat、"
                                    + "Trade List の件数の型（u8⇔varint）を変換する。Message Header／"
                                    + "Chat Suggestions／Hide Message は759に無いため落とす。Message "
                                    + "Acknowledgement は759に無いため落とす。Multi Block Change の "
                                    + "bool（notTrustEdges⇔suppressLightUpdates）は同じ位置のためそのまま写す。"
                                    + "Window Click・Set Creative Slot は760↔759で番号だけ付け替える"
                                    + "（落とさない）。Declare Recipes・Tags・Statistics・"
                                    + "World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "759はプレイ状態での旧式オファー（UUIDなし）。",
                            Handling.TRANSLATED,
                            "759クライアントには pack_format 9 のパックを出す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19_1) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が760と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.19.1と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始は署名データ付き（760）。署名付きチャットは759（1.19）で導入された。760は連鎖ハッシュ＋Message Header付きの別形式。",
                            Handling.TRANSLATED,
                            "760の Login Start は名前＋署名データ（option）＋任意UUID。"
                                    + "761以降は署名データが無く、代わりにプレイ状態の "
                                    + "Chat Session Update で鍵を送る。プロキシは認証済みの名前・UUID で "
                                    + "Login Start を作り直すため、クライアントの署名データは内部へは届かない。"
                                    + "Chat Message／Chat Command／Player Chat は760と761で形式が違うため変換する。"),
                    new Difference("CONFIGURATION",
                            "1.20.2で追加された「設定フェーズ」が760クライアントには無い（761と同じ）。",
                            Handling.TRANSLATED,
                            "760はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                    + "設定フェーズで送るレジストリ情報は、760向けには Join Game の"
                                    + "codec としてプレイ状態で送り直す。設定フェーズ用のパケットを"
                                    + "760に送ってはいけない。プロキシは765クライアント役として"
                                    + "設定フェーズを消費する。"),
                    new Difference("REGISTRY",
                            "760と761のレジストリ差分を761経由で変換する（パケットIDは761→760で118件変更）。",
                            Handling.TRANSLATED,
                            "ブロック状態 21,448→23,232（+1,784。bamboo・hanging sign 系など）、"
                                    + "アイテム 1,152→1,186（+34）、"
                                    + "音 1,321→1,392（+71）、パーティクル 93→93（同一）、"
                                    + "エンティティ 118→119（+1。camel）、"
                                    + "バイオーム 63→63（名前は同一だが順序が違う。plains は760=1・761=38）、"
                                    + "ダメージ種別は760の codec に存在しない（1.19.4で追加。762の42件の恒等写像と仮定）、"
                                    + "メニュー 24→24（同一）、"
                                    + "ブロックエンティティ 36→38（chiseled_bookshelf・hanging_sign）。"),
                    new Difference("PLAY",
                            "プレイ状態は761↔760の直接変換のあと、既存の761↔762↔763↔764↔765の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Player Chat／Chat Message／Chat Command／Message Acknowledgement の"
                                    + "署名付きチャット形式、Player Info の action 形式、Server Data の "
                                    + "previewsChat、Sound Effect の Holder 形式を変換する。Message Header は"
                                    + "落とし、Player Chat だけを送る。Feature Flags／Player Info Remove／"
                                    + "Add／Remove Resource Pack／Chunk Biomes／Bundle Delimiter／"
                                    + "Damage Event／Hurt Animation／Profileless Chat は760に無いため落とす。"
                                    + "Window Click・Set Creative Slot は761↔760で変換する"
                                    + "（落とさない）。Declare Recipes・Tags・Statistics・"
                                    + "World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "760はプレイ状態での旧式オファー（UUIDなし）。",
                            Handling.TRANSLATED,
                            "760クライアントには pack_format 9 のパックを出す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19_3) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が761と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.19.3と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始は762と同じく bool 付き任意UUID。署名付きチャットも761と762で同じ形。",
                            Handling.UNAFFECTED,
                            "761の Login Start は bool 付き任意UUID（762・763と同じ）。"
                                    + "Chat Message／Chat Command／Player Chat／System Chat は"
                                    + "761と762で同一。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "1.20.2で追加された「設定フェーズ」が761クライアントには無い（762と同じ）。",
                            Handling.TRANSLATED,
                            "761はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                    + "設定フェーズで送るレジストリ情報は、761向けには Join Game の"
                                    + "codec としてプレイ状態で送り直す。設定フェーズ用のパケットを"
                                    + "761に送ってはいけない。プロキシは765クライアント役として"
                                    + "設定フェーズを消費する。"),
                    new Difference("REGISTRY",
                            "761と762のレジストリ差分を762経由で変換する（パケットIDは762→761で132件変更）。",
                            Handling.TRANSLATED,
                            "ブロック状態 23,232→23,725（+493。cherry 系など）、"
                                    + "アイテム 1,186→1,228（+42）、"
                                    + "音 1,392→1,454（+62）、パーティクル 93→96"
                                    + "（cherry_leaves 系3種）、"
                                    + "エンティティ 119→124（+5。display 系・sniffer）、"
                                    + "バイオーム 63→63（同一）、"
                                    + "ダメージ種別は761の codec に存在しない（1.19.4で追加。実バニラで確認）、"
                                    + "メニュー 24→25（smithing の新旧分離）、"
                                    + "ブロックエンティティ 38→40（suspicious_sand・decorated_pot）。"),
                    new Difference("PLAY",
                            "プレイ状態は762↔761の直接変換のあと、既存の762↔763↔764↔765の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Synchronize Player Position の末尾の dismountVehicle、"
                                    + "Server Data の motd／icon の形、Spawn Entity（player）と"
                                    + "Named Entity Spawn の対応付けを変換する。Feature Flags は"
                                    + "番号だけ付け替える。Damage Event／Bundle Delimiter／"
                                    + "Chunk Biomes／Hurt Animation は761に無いため落とす。"
                                    + "Window Click・Set Creative Slot は762↔761で変換する"
                                    + "（落とさない）。Declare Recipes・Tags・Statistics・"
                                    + "World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "761はプレイ状態での旧式オファー（UUIDなし）。",
                            Handling.TRANSLATED,
                            "761クライアントには pack_format 12 のパックを出す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19_4) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が762と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.19.4と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始は763と同じく bool 付き任意UUID。署名付きチャットも762と763で同じ形。",
                            Handling.UNAFFECTED,
                            "762の Login Start は bool 付き任意UUID（763と同じ）。"
                                    + "Chat Message／Chat Command／Player Chat／System Chat は"
                                    + "762と763で同一。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "1.20.2で追加された「設定フェーズ」が762クライアントには無い（763と同じ）。",
                            Handling.TRANSLATED,
                            "762はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                    + "設定フェーズで送るレジストリ情報は、762向けには Join Game の"
                                    + "codec としてプレイ状態で送り直す。設定フェーズ用のパケットを"
                                    + "762に送ってはいけない。プロキシは765クライアント役として"
                                    + "設定フェーズを消費する。"),
                    new Difference("REGISTRY",
                            "762と763のレジストリ差分を763経由で変換する（パケットIDは762と763で0件変更）。",
                            Handling.TRANSLATED,
                            "ブロック状態 23,725→24,135（+410。calibrated_sculk_sensor 384件など）、"
                                    + "アイテム 1,228→1,255（+27。陶器の破片の改名4件を含む）、"
                                    + "音 1,454→1,474（+20）、パーティクル 96→95、"
                                    + "バイオーム 63→64（cherry_grove を追加）、"
                                    + "ダメージ種別 42→44（generic_kill・outside_border）、"
                                    + "メニュー 25→24（legacy_smithing→smithing）、"
                                    + "ブロックエンティティ 40→41（suspicious_sand→brushable_block）。"),
                    new Difference("PLAY",
                            "プレイ状態は762↔763の直接変換のあと、既存の763↔764↔765の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Join Game・Respawn の末尾の portalCooldown、看板の isFrontText、"
                                    + "戦闘イベントの entityId、チャンクの trustEdges、Advancements の"
                                    + "sendsTelemetryData を付け外しする。Window Click・"
                                    + "Set Creative Slot は762↔763で変換する（落とさない）。"
                                    + "Declare Recipes・Tags・Statistics・World Event は"
                                    + "全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "762はプレイ状態での旧式オファー（UUIDなし）。",
                            Handling.TRANSLATED,
                            "762クライアントには pack_format 13 のパックを出す。"));
        }
        // ここまでで返らなかったのは760・761・762・763・未対応のいずれか。760・761・762は上の分岐で
        // 返っているため、ここに来る760・761・762は無い。763は下の末尾リストへ。
        if (clientProtocol != OLDEST_SUPPORTED_CLIENT_PROTOCOL
                && clientProtocol != ProtocolVersion.V1_20_1) {
            return List.of(new Difference("HANDSHAKE",
                    "このプロトコルには対応していません。",
                    Handling.NOT_SUPPORTED,
                    decide(clientProtocol).message()));
        }
        return List.of(
                new Difference("HANDSHAKE",
                        "ハンドシェイク自体の形は同じ。プロトコル番号が763と名乗るだけ。",
                        Handling.UNAFFECTED,
                        "ハンドシェイクパケットの構造は1.20.1と1.20.4で変わっていない。"),
                new Difference("LOGIN",
                        "ログイン開始・暗号化・ログイン成功は同じ形。",
                        Handling.UNAFFECTED,
                        "online-modeの認証も含め、ログインの流れに差はない。"),
                new Difference("CONFIGURATION",
                        "1.20.2で追加された「設定フェーズ」が763クライアントには無い。",
                        Handling.TRANSLATED,
                        "763はログイン成功のあとすぐプレイ状態へ進む。765のサーバーが"
                                + "設定フェーズで送るレジストリ情報は、763向けにはプレイ状態で"
                                + "送り直す必要がある。設定フェーズ用のパケットを763に送っては"
                                + "いけない。"),
                new Difference("BLOCK",
                        "1.20.3で追加・改名されたブロックが763には無い。",
                        Handling.FALLBACK,
                        "crafter→crafting_table、trial_spawner→spawner、"
                                + "short_grass→grass に置き換える（SimpleBlockTranslator）。"
                                + "1.20.3で実験的機能として入った1.21向けブロック（銅の電球など）は"
                                + "通常の1.20.4ワールドには出ないため、変換表の対象外。"),
                new Difference("SPAWN",
                        "スポーン開始の合図が違う。",
                        Handling.TRANSLATED,
                        "763はデフォルトスポーン位置パケットでスポーンを開始する。"
                                + "765（1.20.3以降）は Game Event 13（チャンク待ち開始）が要る。"
                                + "763向けには763式のスポーン手順を送る。"),
                new Difference("CHAT",
                        "チャットはテキスト内容は同じだが、パケット番号が違う。",
                        Handling.NEEDS_VERIFICATION,
                        "パケットIDはバージョンごとに振り直されるため、番号の付け替えが必須。"
                                + "署名フィールドの差分はパケット単位で未検証のため、実機での"
                                + "チャット送受信の確認がまだ要る。"),
                new Difference("MOVE",
                        "移動（位置・向き）は両方にあるが、パケット番号が違う。",
                        Handling.NEEDS_VERIFICATION,
                        "位置・向きパケット自体は両バージョンにある。番号の付け替えが必須で、"
                                + "フィールド単位の照合は未検証のため、実機での移動確認がまだ要る。"),
                new Difference("RESOURCE_PACK",
                        "リソースパックの送り方が違う。",
                        Handling.TRANSLATED,
                        "763はプレイ状態での旧式オファー（UUIDなし）。765（1.20.3以降）は"
                                + "UUID付きで、設定フェーズでも扱う。なおDetergentMCのデモパックは"
                                + "pack_format 22（1.20.3以降用）で作られているため、763クライアント"
                                + "には「互換性なし」と表示される可能性があり、要確認。"));
    }

    /**
     * {@link #differences(int)} の内部プロトコル指定版（逆向き対応）。
     * 内部765の場合は {@link #differences(int)} と同じ一覧を返す。
     * 内部764の場合は、765クライアント向けの下向き変換と、763クライアント
     * 向けの763→764直接変換の差を返す。内部763・766・767の場合は
     * 設定フェーズの扱いだけを書いた一覧を返す。内部とクライアントが
     * 同じ場合は空の一覧を返す。
     */
    public static List<Difference> differences(int clientProtocol, int serverProtocol) {
        if (clientProtocol == serverProtocol) {
            return List.of();
        }
        if (serverProtocol == SERVER_PROTOCOL) {
            return differences(clientProtocol);
        }
        if (clientProtocol == ProtocolVersion.V1_17) {
            // クライアント755＋内部756／757／759〜775。755↔756はパケットID・
            // レジストリ・Join Game／Respawn codec とも同一のため、756／757
            // 向けは直接変換だけで済む。759〜763向けは設定フェーズが無く、
            // 764以降向けはプロキシが設定フェーズを合成する。
            if (serverProtocol == ProtocolVersion.V1_17_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "755も756も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は756↔755の直接変換だけで済む（パケットIDは0件変更・新規0件・削除0件）。",
                                Handling.TRANSLATED,
                                "Join Game・Respawn は同一（dimension は両方とも NBT 値、"
                                        + "simulationDistance・lastDeathLocation はどちらにも無い）。"
                                        + "レジストリも同一（ブロック状態 20,342件・アイテム 1,100件・"
                                        + "音 1,190件・バイオーム 81件とも同一）。"
                                        + "内部名の改名1件（destroy_entity→entity_destroy）は ID が"
                                        + "同じ0x3aのため番号の付け替えは不要。"
                                        + "Window Click・Set Creative Slot も番号は変わらない。"),
                        new Difference("RESOURCE_PACK",
                                "755クライアントには pack_format 7 のパックを出す。",
                                Handling.TRANSLATED,
                                "755はプレイ状態での旧式オファー（UUIDなし）で pack_format 7 のパックを出す。"
                                        + "内部756のパック（pack_format 7）と同じ形式だが、755用パックを別ファイルで用意する。"));
            }
            if (serverProtocol == ProtocolVersion.V1_18) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "755も757も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は755→756→757の二段変換で済む（パケットIDは755↔756で0件変更、756↔757で16件変更・1件削除）。",
                                Handling.TRANSLATED,
                                "755↔756はパケットID・レジストリ・Join Game／Respawn codec とも"
                                        + "同一のため番号の付け替えは不要。756↔757は simulationDistance"
                                        + "の付け外しと simulation_distance（757で0x57）の削除による"
                                        + "-1ずれ（0x58以降）を変換し、レジストリは756↔757対応表で"
                                        + "変換する。バイオームは81件⇔61件の dimensionCodec の付け替え。"
                                        + "Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "755クライアントには pack_format 7 のパックを出す。",
                                Handling.TRANSLATED,
                                "755はプレイ状態での旧式オファー（UUIDなし）で pack_format 7 のパックを出す。"
                                        + "内部757のパック（pack_format 8）とは別に755用パックを用意する。"));
            }
            if (serverProtocol == ProtocolVersion.V1_19
                    || serverProtocol == ProtocolVersion.V1_19_1
                    || serverProtocol == ProtocolVersion.V1_19_3
                    || serverProtocol == ProtocolVersion.V1_19_4
                    || serverProtocol == ProtocolVersion.V1_20_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "755も759／760／761／762／763も設定フェーズを持たないため、プロキシは "
                                        + "Login Acknowledged を送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は756↔755の直接変換のあと、756から内部までの既存の変換を重ねる。",
                                Handling.TRANSLATED,
                                "756↔755は同一のため変換は756↔757から始まる。Player Chat／"
                                        + "Chat Message／Chat Command の署名付きチャット形式、Player Info の"
                                        + "action 形式、Server Data の previewsChat、Sound Effect の Holder 形式を"
                                        + "変換し、レジストリは757↔759対応表で変換する。Message Header は落とす。"
                                        + "Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "755クライアントには pack_format 7 のパックを出す。",
                                Handling.TRANSLATED,
                                "755はプレイ状態での旧式オファー（UUIDなし）で pack_format 7 のパックを出す。"
                                        + "内部759／760／761／762／763のパック（pack_format 9／9／12／13／15）とは別に755用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            "内部" + serverProtocol + "の設定フェーズは、755クライアントにはプロキシが"
                                    + serverProtocol + "クライアント役として消費し、755クライアントには送らない。",
                            Handling.TRANSLATED,
                            "755には設定フェーズが無い。内部サーバーの Registry Data は Join Game の"
                                    + "codec として755向けに送り直す。"),
                    new Difference("PLAY",
                            "プレイ状態は756↔755の直接変換のあと、756から内部" + serverProtocol
                                    + "までの既存の変換を重ねる。Window Click・Set Creative Slot は756↔757では"
                                    + "変換するが、766以降のデータコンポーネント形式との間では落とす。",
                            Handling.TRANSLATED,
                            "Join Game は simulationDistance の付け外しと、バイオーム"
                                    + "81件⇔61件の dimensionCodec の付け替え、署名なし chat と"
                                    + "署名付きチャット形式の変換をし、レジストリは756↔757対応表で"
                                    + "変換する。Declare Recipes・Tags・Statistics・World Event は"
                                    + "全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "755クライアントには pack_format 7 のパックを出す。",
                            Handling.TRANSLATED,
                            "内部サーバーの設定フェーズのリソースパックはプロキシが消費し、755向けには"
                                    + "プレイ状態の旧式オファーで pack_format 7 のパックを出し直す。"));
        }
        if (serverProtocol == ProtocolVersion.V1_17) {
            // 内部755＋756／757／759〜775クライアント。内部755には設定フェーズが無い。
            // 756／757／759〜763クライアントにも設定フェーズは無いが、764以降の
            // クライアントにはプロキシが Join Game の codec から設定フェーズを
            // 合成する（内部756と同じ段取り）。
            if (clientProtocol != ProtocolVersion.V1_17_1
                    && clientProtocol != ProtocolVersion.V1_18
                    && clientProtocol != ProtocolVersion.V1_19
                    && clientProtocol != ProtocolVersion.V1_19_1
                    && clientProtocol != ProtocolVersion.V1_19_3
                    && clientProtocol != ProtocolVersion.V1_19_4
                    && clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5
                    && clientProtocol != ProtocolVersion.V1_21_6
                    && clientProtocol != ProtocolVersion.V1_21_7
                    && clientProtocol != ProtocolVersion.V1_21_9
                    && clientProtocol != ProtocolVersion.V1_21_11
                    && clientProtocol != ProtocolVersion.V26_1) {
                return List.of(new Difference("HANDSHAKE",
                        "このプロトコルには対応していません。",
                        Handling.NOT_SUPPORTED,
                        decide(clientProtocol, serverProtocol).message()));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            clientProtocol == ProtocolVersion.V1_20_2
                                    || clientProtocol == ProtocolVersion.V1_20_4
                                    || clientProtocol == ProtocolVersion.V1_20_5
                                    || clientProtocol == ProtocolVersion.V1_21
                                    || clientProtocol == ProtocolVersion.V1_21_2
                                    || clientProtocol == ProtocolVersion.V1_21_4
                                    || clientProtocol == ProtocolVersion.V1_21_5
                                    || clientProtocol == ProtocolVersion.V1_21_6
                                    || clientProtocol == ProtocolVersion.V1_21_7
                                    || clientProtocol == ProtocolVersion.V1_21_9
                                    || clientProtocol == ProtocolVersion.V1_21_11
                                    || clientProtocol == ProtocolVersion.V26_1
                                    ? "内部755には設定フェーズが無い。プロキシが Join Game の "
                                            + "codec からクライアント向けの設定フェーズを合成する。"
                                    : "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                            Handling.TRANSLATED,
                            "設定フェーズの振り分けは ProxySession が行う。"),
                    new Difference("PLAY",
                            "内部755とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、755↔756の直接変換のあと、756から"
                                    + "クライアントまでの既存の変換を重ねる。スロットは"
                                    + "755⇔756⇔757⇔759⇔760⇔761⇔762⇔763⇔764⇔765で変換し、766以降向けの Window Click・"
                                    + "Set Creative Slot は落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。",
                            Handling.TRANSLATED,
                            "755↔756はパケットID・レジストリ・Join Game／Respawn codec とも"
                                    + "同一のため、変換は756↔757から始まる。Join Game は"
                                    + "simulationDistance の付け外しと、バイオーム81件⇔61件の"
                                    + "dimensionCodec の付け替えを変換し、Respawn は756と同一。"
                                    + "レジストリは756↔757対応表で変換する。"
                                    + "チャットは755の署名なし chat とクライアントの署名付き形式の"
                                    + "間で変換する。設定フェーズまでの参加フローは振り分け済み。"));
        }
        if (serverProtocol == ProtocolVersion.V1_18) {
            // 内部757＋756／759〜775クライアント。内部757には設定フェーズが無い。
            // 756／759〜763クライアントにも設定フェーズは無いが、764以降の
            // クライアントにはプロキシが Join Game の codec から設定フェーズを
            // 合成する（内部763と同じ段取り）。
            if (clientProtocol != ProtocolVersion.V1_17_1
                    && clientProtocol != ProtocolVersion.V1_19
                    && clientProtocol != ProtocolVersion.V1_19_1
                    && clientProtocol != ProtocolVersion.V1_19_3
                    && clientProtocol != ProtocolVersion.V1_19_4
                    && clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5
                    && clientProtocol != ProtocolVersion.V1_21_6
                    && clientProtocol != ProtocolVersion.V1_21_7
                    && clientProtocol != ProtocolVersion.V1_21_9
                    && clientProtocol != ProtocolVersion.V1_21_11
                    && clientProtocol != ProtocolVersion.V26_1) {
                return List.of(new Difference("HANDSHAKE",
                        "このプロトコルには対応していません。",
                        Handling.NOT_SUPPORTED,
                        decide(clientProtocol, serverProtocol).message()));
            }
            if (clientProtocol == ProtocolVersion.V1_17_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "756も757も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は757↔756の直接変換だけで済む（パケットIDは16件変更・1件削除）。",
                                Handling.TRANSLATED,
                                "Join Game は simulationDistance の付け外しだけ"
                                        + "（dimension は両方とも NBT 値、lastDeathLocation は"
                                        + "どちらにも無い）。Respawn は同一。simulation_distance"
                                        + "（757で0x57）の削除による-1ずれ（0x58以降）を変換し、"
                                        + "レジストリは756↔757対応表で変換する。バイオームは"
                                        + "81件⇔61件の dimensionCodec の付け替え。"
                                        + "Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "756クライアントには pack_format 7 のパックを出す。",
                                Handling.TRANSLATED,
                                "756はプレイ状態での旧式オファー（UUIDなし）で pack_format 7 のパックを出す。"
                                        + "内部757のパック（pack_format 8）とは別に756用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            clientProtocol == ProtocolVersion.V1_20_2
                                    || clientProtocol == ProtocolVersion.V1_20_4
                                    || clientProtocol == ProtocolVersion.V1_20_5
                                    || clientProtocol == ProtocolVersion.V1_21
                                    || clientProtocol == ProtocolVersion.V1_21_2
                                    || clientProtocol == ProtocolVersion.V1_21_4
                                    || clientProtocol == ProtocolVersion.V1_21_5
                                    || clientProtocol == ProtocolVersion.V1_21_6
                                    || clientProtocol == ProtocolVersion.V1_21_7
                                    || clientProtocol == ProtocolVersion.V1_21_9
                                    || clientProtocol == ProtocolVersion.V1_21_11
                                    || clientProtocol == ProtocolVersion.V26_1
                                    ? "内部757には設定フェーズが無い。プロキシが Join Game の "
                                            + "codec からクライアント向けの設定フェーズを合成する。"
                                    : "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                            Handling.TRANSLATED,
                            "設定フェーズの振り分けは ProxySession が行う。"),
                    new Difference("PLAY",
                            "内部757とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、757↔759の直接変換のあと、759から"
                                    + "クライアントまでの既存の変換を重ねる。スロットは"
                                    + "757⇔759⇔760⇔761⇔762⇔763⇔764⇔765で変換し、766以降向けの Window Click・"
                                    + "Set Creative Slot は落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。",
                            Handling.TRANSLATED,
                            "Join Game・Respawn は dimensionType の値⇔キーと lastDeathLocation の"
                                    + "付け外しを変換し、レジストリは757↔759対応表で変換する。"
                                    + "チャットは757の署名なし chat とクライアントの署名付き形式の"
                                    + "間で変換する。設定フェーズまでの参加フローは振り分け済み。"));
        }
        if (serverProtocol == ProtocolVersion.V1_17_1) {
            // 内部756＋759〜775クライアント。内部756には設定フェーズが無い。
            // 759〜763クライアントにも設定フェーズは無いが、764以降の
            // クライアントにはプロキシが Join Game の codec から設定フェーズを
            // 合成する（内部757と同じ段取り）。
            if (clientProtocol != ProtocolVersion.V1_18
                    && clientProtocol != ProtocolVersion.V1_19
                    && clientProtocol != ProtocolVersion.V1_19_1
                    && clientProtocol != ProtocolVersion.V1_19_3
                    && clientProtocol != ProtocolVersion.V1_19_4
                    && clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5
                    && clientProtocol != ProtocolVersion.V1_21_6
                    && clientProtocol != ProtocolVersion.V1_21_7
                    && clientProtocol != ProtocolVersion.V1_21_9
                    && clientProtocol != ProtocolVersion.V1_21_11
                    && clientProtocol != ProtocolVersion.V26_1) {
                return List.of(new Difference("HANDSHAKE",
                        "このプロトコルには対応していません。",
                        Handling.NOT_SUPPORTED,
                        decide(clientProtocol, serverProtocol).message()));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            clientProtocol == ProtocolVersion.V1_20_2
                                    || clientProtocol == ProtocolVersion.V1_20_4
                                    || clientProtocol == ProtocolVersion.V1_20_5
                                    || clientProtocol == ProtocolVersion.V1_21
                                    || clientProtocol == ProtocolVersion.V1_21_2
                                    || clientProtocol == ProtocolVersion.V1_21_4
                                    || clientProtocol == ProtocolVersion.V1_21_5
                                    || clientProtocol == ProtocolVersion.V1_21_6
                                    || clientProtocol == ProtocolVersion.V1_21_7
                                    || clientProtocol == ProtocolVersion.V1_21_9
                                    || clientProtocol == ProtocolVersion.V1_21_11
                                    || clientProtocol == ProtocolVersion.V26_1
                                    ? "内部756には設定フェーズが無い。プロキシが Join Game の "
                                            + "codec からクライアント向けの設定フェーズを合成する。"
                                    : "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                            Handling.TRANSLATED,
                            "設定フェーズの振り分けは ProxySession が行う。"),
                    new Difference("PLAY",
                            "内部756とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、756↔757の直接変換のあと、757から"
                                    + "クライアントまでの既存の変換を重ねる。スロットは"
                                    + "756⇔757⇔759⇔760⇔761⇔762⇔763⇔764⇔765で変換し、766以降向けの Window Click・"
                                    + "Set Creative Slot は落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。",
                            Handling.TRANSLATED,
                            "Join Game は simulationDistance の付け外しと、バイオーム"
                                    + "81件⇔61件の dimensionCodec の付け替えを変換し、"
                                    + "Respawn は同一。レジストリは756↔757対応表で変換する。"
                                    + "チャットは756の署名なし chat とクライアントの署名付き形式の"
                                    + "間で変換する。設定フェーズまでの参加フローは振り分け済み。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19_4) {
            if (serverProtocol == ProtocolVersion.V1_20_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "762も763も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は762↔763の直接変換だけで済む（パケットIDは0件変更）。",
                                Handling.TRANSLATED,
                                "Join Game・Respawn の portalCooldown、看板の isFrontText、"
                                        + "戦闘イベントの entityId、チャンクの trustEdges、Advancements の"
                                        + "sendsTelemetryData を付け外しし、レジストリは762↔763対応表で"
                                        + "変換する。Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "762クライアントには pack_format 13 のパックを出す。",
                                Handling.TRANSLATED,
                                "762はプレイ状態での旧式オファー（UUIDなし）。内部763のパック"
                                        + "（pack_format 15）とは別に762用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            "内部" + serverProtocol + "の設定フェーズは、762クライアントにはプロキシが"
                                    + serverProtocol + "クライアント役として消費し、762クライアントには送らない。",
                            Handling.TRANSLATED,
                            "762には設定フェーズが無い。内部サーバーの Registry Data は Join Game の"
                                    + "codec として762向けに送り直す。"),
                    new Difference("PLAY",
                            "プレイ状態は762↔763の直接変換のあと、763から内部" + serverProtocol
                                    + "までの既存の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Window Click・Set Creative Slot は762↔763では変換するが、766以降の"
                                    + "データコンポーネント形式との間では落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "762クライアントには pack_format 13 のパックを出す。",
                            Handling.TRANSLATED,
                            "内部サーバーの設定フェーズのリソースパックはプロキシが消費し、762向けには"
                                    + "プレイ状態の旧式オファーで出し直す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19_1) {
            if (serverProtocol == ProtocolVersion.V1_19_3) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "760も761も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は761↔760の直接変換だけで済む（パケットIDは118件変更）。",
                                Handling.TRANSLATED,
                                "Player Chat／Chat Message／Chat Command／Message Acknowledgement の"
                                        + "署名付きチャット形式、Player Info の action 形式、Server Data の "
                                        + "previewsChat、Sound Effect の Holder 形式を変換し、レジストリは"
                                        + "761↔760対応表で変換する。Message Header は落とす。Feature Flags／"
                                        + "Player Info Remove／Profileless Chat は760に無いため落とす。"
                                        + "Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "760クライアントには pack_format 9 のパックを出す。",
                                Handling.TRANSLATED,
                                "760はプレイ状態での旧式オファー（UUIDなし）。内部761のパック"
                                        + "（pack_format 12）とは別に760用パックを用意する。"));
            }
            if (serverProtocol == ProtocolVersion.V1_19_4) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "760も762も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は761↔760の直接変換のあと、762↔761の直接変換を重ねる。",
                                Handling.TRANSLATED,
                                "761↔760の変換に加え、Synchronize Player Position の dismountVehicle、"
                                        + "Server Data の motd／icon の形、Spawn Entity（player）と Named Entity "
                                        + "Spawn の対応付けを変換し、レジストリは762↔761対応表で変換する。"
                                        + "Damage Event／Bundle Delimiter／Chunk Biomes／Hurt Animation は"
                                        + "760に無いため落とす。Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "760クライアントには pack_format 9 のパックを出す。",
                                Handling.TRANSLATED,
                                "760はプレイ状態での旧式オファー（UUIDなし）。内部762のパック"
                                        + "（pack_format 13）とは別に760用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            "内部" + serverProtocol + "の設定フェーズは、760クライアントにはプロキシが"
                                    + serverProtocol + "クライアント役として消費し、760クライアントには送らない。",
                            Handling.TRANSLATED,
                            "760には設定フェーズが無い。内部サーバーの Registry Data は Join Game の"
                                    + "codec として760向けに送り直す。"),
                    new Difference("PLAY",
                            "プレイ状態は761↔760の直接変換のあと、761から内部" + serverProtocol
                                    + "までの既存の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Window Click・Set Creative Slot は761↔760では変換するが、766以降の"
                                    + "データコンポーネント形式との間では落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "760クライアントには pack_format 9 のパックを出す。",
                            Handling.TRANSLATED,
                            "内部サーバーの設定フェーズのリソースパックはプロキシが消費し、760向けには"
                                    + "プレイ状態の旧式オファーで出し直す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19) {
            if (serverProtocol == ProtocolVersion.V1_19_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "759も760も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は760↔759の直接変換だけで済む（パケットIDは131件変更）。",
                                Handling.TRANSLATED,
                                "Player Chat／Chat Message／Chat Command の署名付きチャット形式、"
                                        + "System Chat の type varint、Server Data の enforcesSecureChat、"
                                        + "Trade List の件数の型（u8⇔varint）を変換する。Message Header／"
                                        + "Chat Suggestions／Hide Message は759に無いため落とす。Message "
                                        + "Acknowledgement は759に無いため落とす。レジストリは全カテゴリ同一。"
                                        + "Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "759クライアントには pack_format 9 のパックを出す。",
                                Handling.TRANSLATED,
                                "759はプレイ状態での旧式オファー（UUIDなし）。760のパックと同じ "
                                        + "pack_format 9 だが、759用パックを別ファイルで用意する。"));
            }
            if (serverProtocol == ProtocolVersion.V1_19_3
                    || serverProtocol == ProtocolVersion.V1_19_4
                    || serverProtocol == ProtocolVersion.V1_20_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "759も761／762／763も設定フェーズを持たないため、プロキシは "
                                        + "Login Acknowledged を送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は760↔759の直接変換のあと、761↔760から内部までの既存の変換を重ねる。",
                                Handling.TRANSLATED,
                                "760↔759の変換に加え、Player Chat／Chat Message／Chat Command の"
                                        + "署名付きチャット形式、Player Info の action 形式、Server Data の "
                                        + "previewsChat、Sound Effect の Holder 形式を変換し、レジストリは"
                                        + "761↔760対応表で変換する。Message Header は落とす。Window Click・"
                                        + "Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "759クライアントには pack_format 9 のパックを出す。",
                                Handling.TRANSLATED,
                                "759はプレイ状態での旧式オファー（UUIDなし）。内部761／762／763のパック"
                                        + "（pack_format 12／13／15）とは別に759用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            "内部" + serverProtocol + "の設定フェーズは、759クライアントにはプロキシが"
                                    + serverProtocol + "クライアント役として消費し、759クライアントには送らない。",
                            Handling.TRANSLATED,
                            "759には設定フェーズが無い。内部サーバーの Registry Data は Join Game の"
                                    + "codec として759向けに送り直す。"),
                    new Difference("PLAY",
                            "プレイ状態は760↔759の直接変換のあと、760から内部" + serverProtocol
                                    + "までの既存の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Window Click・Set Creative Slot は760↔759では変換するが、766以降の"
                                    + "データコンポーネント形式との間では落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "759クライアントには pack_format 9 のパックを出す。",
                            Handling.TRANSLATED,
                            "内部サーバーの設定フェーズのリソースパックはプロキシが消費し、759向けには"
                                    + "プレイ状態の旧式オファーで出し直す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_18) {
            if (serverProtocol == ProtocolVersion.V1_19) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "757も759も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は759↔757の直接変換だけで済む（パケットIDは123件変更）。",
                                Handling.TRANSLATED,
                                "Join Game・Respawn の dimensionType（値⇔キー）と lastDeathLocation の"
                                        + "付け外し、署名なし chat と署名付きチャット形式の変換、"
                                        + "spawn_entity_living／spawn_entity_painting と Spawn Entity の"
                                        + "対応付けを変換し、レジストリは757↔759対応表で変換する。"
                                        + "System Chat／Player Chat／Chat Message／Chat Command は"
                                        + "757に無いため落とす。Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "757クライアントには pack_format 8 のパックを出す。",
                                Handling.TRANSLATED,
                                "757はプレイ状態での旧式オファー（UUIDなし）で pack_format 8 のパックを出す。"
                                        + "内部759のパック（pack_format 9）とは別に757用パックを用意する。"));
            }
            if (serverProtocol == ProtocolVersion.V1_19_1
                    || serverProtocol == ProtocolVersion.V1_19_3
                    || serverProtocol == ProtocolVersion.V1_19_4
                    || serverProtocol == ProtocolVersion.V1_20_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "757も760／761／762／763も設定フェーズを持たないため、プロキシは "
                                        + "Login Acknowledged を送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は759↔757の直接変換のあと、759から内部までの既存の変換を重ねる。",
                                Handling.TRANSLATED,
                                "759↔757の変換に加え、Player Chat／Chat Message／Chat Command の"
                                        + "署名付きチャット形式、Player Info の action 形式、Server Data の "
                                        + "previewsChat、Sound Effect の Holder 形式を変換し、レジストリは"
                                        + "760↔759対応表で変換する。Message Header は落とす。Window Click・"
                                        + "Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "757クライアントには pack_format 8 のパックを出す。",
                                Handling.TRANSLATED,
                                "757はプレイ状態での旧式オファー（UUIDなし）で pack_format 8 のパックを出す。"
                                        + "内部760／761／762／763のパック（pack_format 9／12／13／15）とは別に757用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            "内部" + serverProtocol + "の設定フェーズは、757クライアントにはプロキシが"
                                    + serverProtocol + "クライアント役として消費し、757クライアントには送らない。",
                            Handling.TRANSLATED,
                            "757には設定フェーズが無い。内部サーバーの Registry Data は Join Game の"
                                    + "codec として757向けに送り直す。"),
                    new Difference("PLAY",
                            "プレイ状態は759↔757の直接変換のあと、759から内部" + serverProtocol
                                    + "までの既存の変換を重ねる。Window Click・Set Creative Slot は757↔759では"
                                    + "変換するが、766以降のデータコンポーネント形式との間では落とす。",
                            Handling.TRANSLATED,
                            "Join Game・Respawn の dimensionType（値⇔キー）と lastDeathLocation の"
                                    + "付け外し、署名なし chat と署名付きチャット形式の変換をし、"
                                    + "レジストリは757↔759対応表で変換する。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "757クライアントには pack_format 8 のパックを出す。",
                            Handling.TRANSLATED,
                            "内部サーバーの設定フェーズのリソースパックはプロキシが消費し、757向けには"
                                    + "プレイ状態の旧式オファーで pack_format 8 のパックを出し直す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_17_1) {
            if (serverProtocol == ProtocolVersion.V1_18) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "756も757も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は757↔756の直接変換だけで済む（パケットIDは16件変更・1件削除）。",
                                Handling.TRANSLATED,
                                "Join Game は simulationDistance の付け外しだけ"
                                        + "（dimension は両方とも NBT 値、lastDeathLocation は"
                                        + "どちらにも無い）。Respawn は同一。simulation_distance"
                                        + "（757で0x57）の削除による-1ずれ（0x58以降）を変換し、"
                                        + "レジストリは756↔757対応表で変換する。バイオームは"
                                        + "81件⇔61件の dimensionCodec の付け替え。"
                                        + "Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "756クライアントには pack_format 7 のパックを出す。",
                                Handling.TRANSLATED,
                                "756はプレイ状態での旧式オファー（UUIDなし）で pack_format 7 のパックを出す。"
                                        + "内部757のパック（pack_format 8）とは別に756用パックを用意する。"));
            }
            if (serverProtocol == ProtocolVersion.V1_19
                    || serverProtocol == ProtocolVersion.V1_19_1
                    || serverProtocol == ProtocolVersion.V1_19_3
                    || serverProtocol == ProtocolVersion.V1_19_4
                    || serverProtocol == ProtocolVersion.V1_20_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "756も759／760／761／762／763も設定フェーズを持たないため、プロキシは "
                                        + "Login Acknowledged を送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は757↔756の直接変換のあと、757から内部までの既存の変換を重ねる。",
                                Handling.TRANSLATED,
                                "757↔756の変換に加え、Player Chat／Chat Message／Chat Command の"
                                        + "署名付きチャット形式、Player Info の action 形式、Server Data の "
                                        + "previewsChat、Sound Effect の Holder 形式を変換し、レジストリは"
                                        + "759↔757対応表で変換する。Message Header は落とす。Window Click・"
                                        + "Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "756クライアントには pack_format 7 のパックを出す。",
                                Handling.TRANSLATED,
                                "756はプレイ状態での旧式オファー（UUIDなし）で pack_format 7 のパックを出す。"
                                        + "内部759／760／761／762／763のパック（pack_format 9／9／12／13／15）とは別に756用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            "内部" + serverProtocol + "の設定フェーズは、756クライアントにはプロキシが"
                                    + serverProtocol + "クライアント役として消費し、756クライアントには送らない。",
                            Handling.TRANSLATED,
                            "756には設定フェーズが無い。内部サーバーの Registry Data は Join Game の"
                                    + "codec として756向けに送り直す。"),
                    new Difference("PLAY",
                            "プレイ状態は757↔756の直接変換のあと、757から内部" + serverProtocol
                                    + "までの既存の変換を重ねる。Window Click・Set Creative Slot は757↔756では"
                                    + "変換するが、766以降のデータコンポーネント形式との間では落とす。",
                            Handling.TRANSLATED,
                            "Join Game は simulationDistance の付け外しと、バイオーム"
                                    + "81件⇔61件の dimensionCodec の付け替え、署名なし chat と"
                                    + "署名付きチャット形式の変換をし、レジストリは756↔757対応表で"
                                    + "変換する。Declare Recipes・Tags・Statistics・World Event は"
                                    + "全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "756クライアントには pack_format 7 のパックを出す。",
                            Handling.TRANSLATED,
                            "内部サーバーの設定フェーズのリソースパックはプロキシが消費し、756向けには"
                                    + "プレイ状態の旧式オファーで pack_format 7 のパックを出し直す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_19_3) {
            if (serverProtocol == ProtocolVersion.V1_19_4) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "761も762も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は762↔761の直接変換だけで済む（パケットIDは132件変更）。",
                                Handling.TRANSLATED,
                                "Synchronize Player Position の dismountVehicle、Server Data の"
                                        + "motd／icon の形、Spawn Entity（player）と Named Entity Spawn の"
                                        + "対応付けを変換し、レジストリは762↔761対応表で変換する。"
                                        + "Damage Event／Bundle Delimiter／Chunk Biomes／Hurt Animation は"
                                        + "761に無いため落とす。Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "761クライアントには pack_format 12 のパックを出す。",
                                Handling.TRANSLATED,
                                "761はプレイ状態での旧式オファー（UUIDなし）。内部762のパック"
                                        + "（pack_format 13）とは別に761用パックを用意する。"));
            }
            if (serverProtocol == ProtocolVersion.V1_20_1) {
                return List.of(
                        new Difference("CONFIGURATION",
                                "どちらにも設定フェーズが無い。ログイン成功のあとすぐプレイ状態へ進む。",
                                Handling.TRANSLATED,
                                "761も763も設定フェーズを持たないため、プロキシは Login Acknowledged を"
                                        + "送らず、両方をそのままプレイ状態へ進める。"),
                        new Difference("PLAY",
                                "プレイ状態は762↔761の直接変換のあと、762↔763の直接変換を重ねる。",
                                Handling.TRANSLATED,
                                "762↔761の変換に加え、Join Game・Respawn の portalCooldown、看板の"
                                        + "isFrontText、戦闘イベントの entityId、チャンクの trustEdges、"
                                        + "Advancements の sendsTelemetryData を付け外しし、レジストリは"
                                        + "762↔763対応表で変換する。Window Click・Set Creative Slot も変換する。"),
                        new Difference("RESOURCE_PACK",
                                "761クライアントには pack_format 12 のパックを出す。",
                                Handling.TRANSLATED,
                                "761はプレイ状態での旧式オファー（UUIDなし）。内部763のパック"
                                        + "（pack_format 15）とは別に761用パックを用意する。"));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            "内部" + serverProtocol + "の設定フェーズは、761クライアントにはプロキシが"
                                    + serverProtocol + "クライアント役として消費し、761クライアントには送らない。",
                            Handling.TRANSLATED,
                            "761には設定フェーズが無い。内部サーバーの Registry Data は Join Game の"
                                    + "codec として761向けに送り直す。"),
                    new Difference("PLAY",
                            "プレイ状態は762↔761の直接変換のあと、762から内部" + serverProtocol
                                    + "までの既存の変換を重ねる。",
                            Handling.TRANSLATED,
                            "Window Click・Set Creative Slot は762↔761では変換するが、766以降の"
                                    + "データコンポーネント形式との間では落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。"),
                    new Difference("RESOURCE_PACK",
                            "761クライアントには pack_format 12 のパックを出す。",
                            Handling.TRANSLATED,
                            "内部サーバーの設定フェーズのリソースパックはプロキシが消費し、761向けには"
                                    + "プレイ状態の旧式オファーで出し直す。"));
        }
        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2
                || serverProtocol == ProtocolVersion.V1_21_4
                || serverProtocol == ProtocolVersion.V1_21_5
                || serverProtocol == ProtocolVersion.V1_21_6
                || serverProtocol == ProtocolVersion.V1_21_7
                || serverProtocol == ProtocolVersion.V1_21_9
                || serverProtocol == ProtocolVersion.V1_21_11
                || serverProtocol == ProtocolVersion.V26_1) {
            // 内部763・766・767・768・769・770・771・772・773・774・775の差の一覧は、対応表・レジストリ表の766／767／768／769／770／771／772／773／774／775対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770・771・772・773・774・775は消費・中継）。
            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5
                    && clientProtocol != ProtocolVersion.V1_21_6
                    && clientProtocol != ProtocolVersion.V1_21_7
                    && clientProtocol != ProtocolVersion.V1_21_9
                    && clientProtocol != ProtocolVersion.V1_21_11
                    && clientProtocol != ProtocolVersion.V26_1) {
                return List.of(new Difference("HANDSHAKE",
                        "このプロトコルには対応していません。",
                        Handling.NOT_SUPPORTED,
                        decide(clientProtocol, serverProtocol).message()));
            }
            return List.of(
                    new Difference("CONFIGURATION",
                            serverProtocol == ProtocolVersion.V1_20_1
                                    ? "内部763には設定フェーズが無い。プロキシが Join Game の "
                                            + "codec からクライアント向けの設定フェーズを合成する。"
                                    : serverProtocol == ProtocolVersion.V1_21
                                            ? "内部767の設定フェーズは、763クライアントにはプロキシが"
                                                    + "767クライアント役として消費し、764／765／766クライアント"
                                                    + "には中継する（Registry Data の形式だけ変換）。"
                                                    + "767の新規パケット（Server Links・Custom Report Details）は"
                                                    + "古いクライアントには落とす。"
                                            : serverProtocol == ProtocolVersion.V26_1
                                                    ? "内部775の設定フェーズは、763クライアントにはプロキシが"
                                                            + "775クライアント役として消費し、764／765／766／767／768／769／770／771／772／773／774クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは無い。"
                                            : serverProtocol == ProtocolVersion.V1_21_11
                                                    ? "内部774の設定フェーズは、763クライアントにはプロキシが"
                                                            + "774クライアント役として消費し、764／765／766／767／768／769／770／771／772／773クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは Show／Clear Dialog。"
                                            : serverProtocol == ProtocolVersion.V1_21_9
                                                    ? "内部773の設定フェーズは、763クライアントにはプロキシが"
                                                            + "773クライアント役として消費し、764／765／766／767／768／769／770／771／772クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは Show／Clear Dialog。"
                                            : serverProtocol == ProtocolVersion.V1_21_7
                                                    ? "内部772の設定フェーズは、763クライアントにはプロキシが"
                                                            + "772クライアント役として消費し、764／765／766／767／768／769／770／771クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは Show／Clear Dialog。"
                                            : serverProtocol == ProtocolVersion.V1_21_6
                                                    ? "内部771の設定フェーズは、763クライアントにはプロキシが"
                                                            + "771クライアント役として消費し、764／765／766／767／768／769／770クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは Show／Clear Dialog。"
                                            : serverProtocol == ProtocolVersion.V1_21_5
                                                    ? "内部770の設定フェーズは、763クライアントにはプロキシが"
                                                            + "770クライアント役として消費し、764／765／766／767／768／769クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは無い。"
                                            : serverProtocol == ProtocolVersion.V1_21_4
                                                    ? "内部769の設定フェーズは、763クライアントにはプロキシが"
                                                            + "769クライアント役として消費し、764／765／766／767／768クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "769の新規パケット（Pick Item From Block／Entity・"
                                                            + "Player Loaded）は古いクライアントには落とす。"
                                            : serverProtocol == ProtocolVersion.V1_21_2
                                                    ? "内部768の設定フェーズは、763クライアントにはプロキシが"
                                                            + "768クライアント役として消費し、764／765／766／767クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "768の新規パケット（Recipe Book 系など）は"
                                                            + "古いクライアントには落とす。"
                                                    : "内部766の設定フェーズは、763クライアントにはプロキシが"
                                                    + "766クライアント役として消費し、764／765／767クライアント"
                                                    + "には中継する（Registry Data の形式と順序だけ変換）。",
                            Handling.TRANSLATED,
                            "設定フェーズの振り分けは ProxySession が行う。"),
                    new Difference("PLAY",
                            "内部" + serverProtocol + "とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、対応表（PacketIds・"
                                    + "RegistryRemapper）の767／768／769／770／771／772／773／774／775対応で実装済み。スロットは"
                                    + "766⇔767⇔768⇔769⇔770⇔771⇔772⇔773⇔774⇔775で変換し、763／764／765向けの Window Click・"
                                    + "Set Creative Slot は落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応。",
                            Handling.TRANSLATED,
                            "設定フェーズまでの参加フローは振り分け済みで、プレイ中の"
                                    + "パケット変換も実装済み。残る落としものは上の一覧の通り。"));
        }
        if (clientProtocol != ProtocolVersion.V1_20_1
                && clientProtocol != ProtocolVersion.V1_20_2
                && clientProtocol != ProtocolVersion.V1_20_4
                && clientProtocol != ProtocolVersion.V1_20_5
                && clientProtocol != ProtocolVersion.V1_21
                && clientProtocol != ProtocolVersion.V1_21_2
                && clientProtocol != ProtocolVersion.V1_21_4
                && clientProtocol != ProtocolVersion.V1_21_5
                && clientProtocol != ProtocolVersion.V1_21_6
                && clientProtocol != ProtocolVersion.V1_21_7
                && clientProtocol != ProtocolVersion.V1_21_9
                && clientProtocol != ProtocolVersion.V1_21_11
                && clientProtocol != ProtocolVersion.V26_1) {
            return List.of(new Difference("HANDSHAKE",
                    "このプロトコルには対応していません。",
                    Handling.NOT_SUPPORTED,
                    decide(clientProtocol, serverProtocol).message()));
        }
        // V1_17 は専用の755分岐が先に755を処理するため、この条件には実際には
        // 到達しない休眠エントリ（756の「7」と同じ扱い）。三項演算子側の
        // V1_17 → "7" と対になる。
        if (clientProtocol == ProtocolVersion.V1_17
                || clientProtocol == ProtocolVersion.V1_17_1
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5
                || clientProtocol == ProtocolVersion.V1_21_6
                || clientProtocol == ProtocolVersion.V1_21_7
                || clientProtocol == ProtocolVersion.V1_21_9
                || clientProtocol == ProtocolVersion.V1_21_11
                || clientProtocol == ProtocolVersion.V26_1) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が"
                                    + clientProtocol + "と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.20.2と1.20.5／1.21で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン成功は" + clientProtocol + "だけ末尾に strictErrorHandling "
                                    + "（bool）が付く。764には無い。",
                            Handling.TRANSLATED,
                            clientProtocol + "の Login Success から末尾の bool を1バイト"
                                    + "落として764形式にする。online-modeの認証も含め、"
                                    + "ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは" + clientProtocol + "にもある。そのまま中継するが、"
                                    + "Registry Data の形式と順序だけ変換する。",
                            Handling.TRANSLATED,
                            clientProtocol + "の Registry Data はエントリ配列形式で、"
                                    + "764の1個の名前なし NBT codec とは違うため、"
                                    + "プロキシが順序を並べ替えて送る。767の新規パケット"
                                    + "（Server Links・Custom Report Details）は764の"
                                    + "サーバーには落とす。"),
                    new Difference("PLAY",
                            "スロットはデータコンポーネント形式のため、764の NBT 形式には"
                                    + "変換できない。Window Click・Set Creative Slot は落とす。",
                            Handling.TRANSLATED,
                            "Join Game・Respawn・Keep Alive・Ping は番号と形式を変換して"
                                    + "通す。スロットを含むパケットは764向けに落とすため、"
                                    + clientProtocol + "クライアントが内部764のサーバーにいるときは"
                                    + "インベントリ操作ができない。"),
                    new Difference("RESOURCE_PACK",
                            "リソースパックの送り方が違う。",
                            Handling.TRANSLATED,
                            "764は UUID なしの単一パケットのため、UUID を合成して変換する。"
                                    + clientProtocol + "クライアントには pack_format "
                                    + (clientProtocol == ProtocolVersion.V1_17 ? "7"
                                            : clientProtocol == ProtocolVersion.V1_17_1 ? "7"
                                            : clientProtocol == ProtocolVersion.V1_18 ? "8"
                                            : clientProtocol == ProtocolVersion.V26_1 ? "84"
                                            : clientProtocol == ProtocolVersion.V1_21_11 ? "75"
                                            : clientProtocol == ProtocolVersion.V1_21_9 ? "69"
                                            : clientProtocol == ProtocolVersion.V1_21_7 ? "64"
                                            : clientProtocol == ProtocolVersion.V1_21_6 ? "63"
                                            : clientProtocol == ProtocolVersion.V1_21_5 ? "55"
                                            : clientProtocol == ProtocolVersion.V1_21_4 ? "46"
                                            : clientProtocol == ProtocolVersion.V1_21_2 ? "42"
                                            : clientProtocol == ProtocolVersion.V1_21 ? "34" : "32")
                                    + " のパックを出す。"));
        }
        if (clientProtocol == ProtocolVersion.V1_20_4) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が765と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.20.2と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は764と同じ形。",
                            Handling.UNAFFECTED,
                            "765の Login Start は名前＋UUID必須で、764と同じ。"
                                    + "online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは765にもある。そのまま中継する。",
                            Handling.TRANSLATED,
                            "765はログイン成功のあと設定フェーズに入る。"
                                    + "Registry Data は764と同じく1個の名前なし NBT codec で、"
                                    + "そのまま中継する。リソースパックだけは形が違う"
                                    + "（764の UUID なし単一パケットに対し、765は UUID 付きの"
                                    + " Add／Remove）ため変換する。"),
                    new Difference("BLOCK",
                            "1.20.3で追加・改名されたブロックが764のサーバーには無い。",
                            Handling.FALLBACK,
                            "765クライアントが送る新しいブロック（crafter／trial_spawner等）は"
                                    + "764のサーバーに存在しないため、近いブロックへの代替か、"
                                    + "サーバー側での拒否が必要になる。チャンクの状態IDは"
                                    + "765→764表で変換する。"),
                    new Difference("JOIN_GAME",
                            "Join Game は764に無い doLimitedCrafting だけが違う。",
                            Handling.TRANSLATED,
                            "765の Join Game は764のものに doLimitedCrafting（bool）が"
                                    + "1つ増えただけである。764の Join Game に false を補って"
                                    + "765形式にする。"),
                    new Difference("RESOURCE_PACK",
                            "リソースパックの送り方が違う。",
                            Handling.TRANSLATED,
                            "765は UUID 付きの Add／Remove（設定フェーズとプレイ状態の両方）。"
                                    + "764は UUID なしの単一パケットのため、UUID を合成して変換する。"
                                    + "765クライアントには pack_format 22 のパックを出す"
                                    + "（内部が764でも22のパックを出す）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_20_2) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が764と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.20.2と1.20.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は764と同じ形。",
                            Handling.UNAFFECTED,
                            "764の Login Start は名前＋UUID必須。online-modeの認証も含め、"
                                    + "ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは764にもある。そのまま中継する。",
                            Handling.UNAFFECTED,
                            "内部もクライアントも764のため、設定フェーズは変換なしで通る。"),
                    new Difference("RESOURCE_PACK",
                            "リソースパックは764の単一パケット（UUIDなし）のまま通る。",
                            Handling.UNAFFECTED,
                            "内部もクライアントも764のため、出し分けは要らない。"
                                    + "764クライアントには pack_format 18 のパックを出す。"));
        }
        return List.of(
                new Difference("HANDSHAKE",
                        "ハンドシェイク自体の形は同じ。プロトコル番号が763と名乗るだけ。",
                        Handling.UNAFFECTED,
                        "ハンドシェイクパケットの構造は1.20.1と1.20.2で変わっていない。"),
                new Difference("LOGIN",
                        "ログイン開始は763だけ形式が違う（bool 付き任意UUID）。",
                        Handling.TRANSLATED,
                        "763の Login Start は bool 付き任意UUID。764向けには"
                                + "名前＋UUID必須の形に変換する。online-modeの認証は"
                                + "プロキシが完結させる（内部764でも同じ）。"),
                new Difference("CONFIGURATION",
                        "1.20.2で追加された「設定フェーズ」が763クライアントには無い。",
                        Handling.TRANSLATED,
                        "763はログイン成功のあとすぐプレイ状態へ進む。764のサーバーが"
                                + "設定フェーズで送るレジストリ情報は、763向けにはプレイ状態で"
                                + "送り直す必要がある。設定フェーズ用のパケットを763に送っては"
                                + "いけない。プロキシは764クライアント役として設定フェーズを消費する。"),
                new Difference("BLOCK",
                        "763と764のブロック状態は直接対応する（763→765→764の二段変換はしない）。",
                        Handling.TRANSLATED,
                        "763→764の対応表は、既存の765→763表と765→764表の合成である。"
                                + "764にも存在するものはそのまま使い、765にしかないものは"
                                + "763向けと同一の代替方針（近いブロックへの代替）を764向けにも適用する。"
                                + "エンティティ・パーティクル・ブロック・メニューは763と764で"
                                + "登録が同一のため、そのまま通る。"),
                new Difference("JOIN_GAME",
                        "Join Game は764形式で来るので、763形式に変換する。",
                        Handling.TRANSLATED,
                        "764の Join Game は765のものから doLimitedCrafting を除いた形。"
                                + "763向けには codec（dimension_type・biome）を埋め込んだ"
                                + "763形式に変換する。"),
                new Difference("RESOURCE_PACK",
                        "リソースパックの送り方が違う。",
                        Handling.TRANSLATED,
                        "763はプレイ状態での旧式オファー（UUIDなし）。764は設定フェーズと"
                                + "プレイ状態の両方で UUID なしの単一パケットのため、"
                                + "763向けには ID だけ付け替えて送る。763クライアントには"
                                + "pack_format 15 のパックを出す。"));
    }
}
