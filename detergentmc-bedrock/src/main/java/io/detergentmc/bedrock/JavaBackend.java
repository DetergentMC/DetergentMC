package io.detergentmc.bedrock;

import java.io.IOException;

/**
 * Java バックエンド（内部バニラ）への接続の窓口。
 *
 * <p>{@link BedrockSession} はこのインターフェースだけを使い、実装
 * （{@link JavaBackendConnection}）を知らない。テストでは偽の
 * 実装に差し替えられる。
 */
public interface JavaBackend {

    /** 内部バニラに接続する（ログイン〜プレイ開始は非同期で進む）。 */
    void connect() throws IOException;

    /** Join Game を受け取り、プレイに入ったかどうか。 */
    boolean isJoined();

    /** 閉じているかどうか。 */
    boolean isClosed();

    /** チャット文を Java 側へ送る。 */
    void sendChat(String message);

    /** コマンド（{@code /} を除いた文字列）を Java 側へ送る。 */
    void sendCommand(String command);

    /** 移動を Java 側へ送る。 */
    void sendPosition(double x, double y, double z, float yaw, float pitch, boolean onGround);

    /**
     * Use Entity（攻撃／インタラクト）を Java 側へ送る。
     *
     * @param entityId 対象の Java エンティティ ID
     * @param action   0＝インタラクト、1＝攻撃
     * @param hand     インタラクトの手（0＝メインハンド）
     */
    void sendUseEntity(int entityId, int action, int hand);

    /**
     * Entity Action（プレイヤー自身の姿勢）を Java 側へ送る。
     *
     * @param action Java の Entity Action 番号
     *               （0＝しゃがみ開始、1＝しゃがみ終了、3＝ダッシュ開始、4＝ダッシュ終了）
     */
    void sendEntityAction(int action);

    /** 接続を閉じる。 */
    void close();
}
