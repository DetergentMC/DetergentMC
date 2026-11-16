package io.detergentmc.protocol;

import java.util.List;

/**
 * 一人のクライアントの参加フロー（プロトコルレベルの状態遷移）。
 *
 * <p>本物のネットワーク層はまだ無いので、このクラスはパケットを送受信
 * しない。参加の「順番」と「段階」をモデル化したもので、クライアント
 * アダプターの判断が正しい順番になっているかを単体テストで確認するため
 * のもの。実機のクライアントが無くても、プロトコルレベルで参加・スポーン
 * までの流れを検証できる。
 *
 * <p>段階の違い：
 * <ul>
 *   <li>1.17.1（756）：759と同形。ハンドシェイク → ログイン → プレイ → 参加
 *       → ゲーム内。1.20.2で追加された「設定フェーズ」が無い。プレイ状態の
 *       新規パケットは0件（757との差分は simulation_distance 1件の削除による
 *       -1ずれだけ）で、削除は1件（simulation_distance）。
 *       Join Game は simulationDistance が無く、Respawn は757と同一。</li>
 *   <li>1.18／1.18.1（757）：759と同形。ハンドシェイク → ログイン → プレイ → 参加
 *       → ゲーム内。1.20.2で追加された「設定フェーズ」が無い。プレイ状態の
 *       新規パケットは5件（spawn_entity_living・spawn_entity_painting・
 *       sculk_vibration_signal・chat・chat_to_server）で、削除は8件
 *       （署名付きチャット関係）。Join Game・Respawn は dimensionType が
 *       値で lastDeathLocation が無い（759ではキーと Optional）。</li>
 *   <li>1.19（759）：ハンドシェイク → ログイン → プレイ → 参加
 *       → ゲーム内。1.20.2で追加された「設定フェーズ」が無い（760・761・762・763と同じ）。</li>
 *   <li>1.19.1／1.19.2（760）：ハンドシェイク → ログイン → プレイ → 参加
 *       → ゲーム内。1.20.2で追加された「設定フェーズ」が無い（761・762・763と同じ）。</li>
 *   <li>1.19.3（761）：ハンドシェイク → ログイン → プレイ → 参加
 *       → ゲーム内。1.20.2で追加された「設定フェーズ」が無い（762・763と同じ）。</li>
 *   <li>1.19.4（762）：ハンドシェイク → ログイン → プレイ → 参加
 *       → ゲーム内。1.20.2で追加された「設定フェーズ」が無い（761・763と同じ）。</li>
 *   <li>1.20.1（763）：ハンドシェイク → ログイン → プレイ → 参加
 *       → ゲーム内。1.20.2で追加された「設定フェーズ」が無い。</li>
 *   <li>1.20.2（764）：ハンドシェイク → ログイン → <b>設定フェーズ</b>
 *       → プレイ → 参加 → ゲーム内。設定フェーズはプロキシが消費せず、
 *       クライアントとバニラの間でそのまま中継する（763との最大の違い）。</li>
 *   <li>1.20.4（765）：ハンドシェイク → ログイン → <b>設定フェーズ</b>
 *       → プレイ → 参加 → ゲーム内。設定フェーズでレジストリ情報など
 *       を受け取る。</li>
 *   <li>1.20.5／1.20.6（766）：764・765と同じくハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号と Registry Data の形式は765と違うが、段階の順番は
 *       同じである（ {@link io.detergentmc.protocol.ProxySession} 参照）。</li>
 *   <li>1.21／1.21.1（767）：766と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は766から変わらず、新規パケット（Server Links・
 *       Custom Report Details）が増えただけである。</li>
 *   <li>1.21.2／1.21.3（768）：767と同形。ハンドシェイク → ログイン
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
 *       増えただけである。</li>
 *   <li>1.21.6（771）：770と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は770から変わらず、新規パケットはプレイ状態に
 *       増えただけである。</li>
 *   <li>1.21.7／1.21.8（772）：771と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は771から変わらず、新規パケットは無い。</li>
 *   <li>1.21.9／1.21.10（773）：772と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は772から変わらず、新規は Code of Conduct（0x13）／
 *       Accept Code of Conduct（0x09）。プレイ状態の新規パケットは
 *       デバッグ系が8件。</li>
 *   <li>1.21.11（774）：773と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は773から変わらず、新規パケットは無い。</li>
 *   <li>26.1（775）：774と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は774から変わらず、新規パケットは無い。プレイ状態の
 *       新規パケットは5件（Game Rule Values・Low Disk Space Warning・
 *       Attack・Set Game Rule・Spectate Entity）。</li>
 * </ul>
 *
 * <p>順番を間違えた遷移（例：765で設定フェーズを飛ばしてプレイに入る、
 * 763で設定フェーズに入ろうとする）は {@link IllegalStateException}
 * になる。それが「このクライアントにはこの順番」という約束である。
 */
public final class ClientJoinFlow {

    /** 参加の段階。 */
    public enum Stage {
        HANDSHAKE,
        LOGIN,
        CONFIGURATION,
        PLAY,
        JOINED,
        IN_GAME
    }

    private final int clientProtocol;
    private final List<Stage> stages;
    private int index;

    /**
     * @param clientProtocol クライアントのプロトコル番号（756か757か759か760か761か762か763か764か765か766か767か768か769か770か771か772か773か774か775）。
     * @throws IllegalArgumentException 対応していないプロトコルの場合
     */
    public ClientJoinFlow(int clientProtocol) {
        if (!ClientAdapter.supportedClientProtocols().contains(clientProtocol)) {
            throw new IllegalArgumentException(
                    "対応していないクライアントのプロトコルです: " + clientProtocol
                            + "（" + ClientAdapter.decide(clientProtocol).message() + "）");
        }
        this.clientProtocol = clientProtocol;
        this.stages = clientProtocol == ProtocolVersion.V1_20_1
                || clientProtocol == ProtocolVersion.V1_19_4
                || clientProtocol == ProtocolVersion.V1_19_3
                || clientProtocol == ProtocolVersion.V1_19_1
                || clientProtocol == ProtocolVersion.V1_19
                || clientProtocol == ProtocolVersion.V1_18
                || clientProtocol == ProtocolVersion.V1_17_1
                ? List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.PLAY, Stage.JOINED, Stage.IN_GAME)
                : List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.CONFIGURATION,
                        Stage.PLAY, Stage.JOINED, Stage.IN_GAME);
        this.index = 0;
    }

    /** クライアントのプロトコル番号。 */
    public int clientProtocol() {
        return clientProtocol;
    }

    /** このクライアントの参加の段階の一覧（順番通り）。 */
    public List<Stage> stages() {
        return stages;
    }

    /** 今いる段階。 */
    public Stage current() {
        return stages.get(index);
    }

    /** 設定フェーズがあるか（764と765と766と767と768と769と770と771と772と773と774と775はあり、756と757と759と760と761と762と763はなし）。 */
    public boolean hasConfigurationPhase() {
        return stages.contains(Stage.CONFIGURATION);
    }

    /** ゲーム内に入ったか（スポーン済み）。 */
    public boolean isInGame() {
        return current() == Stage.IN_GAME;
    }

    /** ゲーム内でのみできること：チャット。 */
    public boolean canChat() {
        return isInGame();
    }

    /** ゲーム内でのみできること：移動。 */
    public boolean canMove() {
        return isInGame();
    }

    /** ハンドシェイクを受けた：ハンドシェイク → ログイン。 */
    public void handshakeReceived() {
        advance(Stage.HANDSHAKE, Stage.LOGIN);
    }

    /**
     * ログインに成功した：ログイン → 設定フェーズ（764・765・766・767・768・769・770・771）かプレイ（756・757・759・760・761・762・763）。
     */
    public void loginSucceeded() {
        advance(Stage.LOGIN, hasConfigurationPhase() ? Stage.CONFIGURATION : Stage.PLAY);
    }

    /**
     * 設定フェーズが終わった：設定フェーズ → プレイ。
     * 756・757・759・760・761・762・763のクライアントには設定フェーズが無いので呼んではいけない。
     */
    public void configurationFinished() {
        advance(Stage.CONFIGURATION, Stage.PLAY);
    }

    /** プレイ開始（Join Game）を受けた：プレイ → 参加。 */
    public void joinGameReceived() {
        advance(Stage.PLAY, Stage.JOINED);
    }

    /** スポーンした：参加 → ゲーム内。 */
    public void spawned() {
        advance(Stage.JOINED, Stage.IN_GAME);
    }

    private void advance(Stage expected, Stage next) {
        if (current() != expected) {
            throw new IllegalStateException("今は " + current() + " なので "
                    + expected + " からの遷移はできません。"
                    + "プロトコル" + clientProtocol + "の参加フローでは "
                    + stages + " の順番で進みます。");
        }
        index = stages.indexOf(next);
    }
}
