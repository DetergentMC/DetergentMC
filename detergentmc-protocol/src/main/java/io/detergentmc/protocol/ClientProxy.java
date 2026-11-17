package io.detergentmc.protocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * DetergentMC のプロキシ（Netty）。
 *
 * <p>公開ポート（{@code server.port}、既定25565）で待ち受け、1接続ごとに
 * バニラ（内部ポート、127.0.0.1のみ）へつなぐ。パケットの翻訳そのものは
 * {@link ProxySession} が行い、このクラスは Netty の配管だけを受け持つ。
 *
 * <p>モードは3つある：
 * <ul>
 *   <li>ステータス：フレーム単位で中継し、クライアントのプロトコルに
 *       合わせて version の JSON だけを書き換える（内部のプロトコルに
 *       よらず、クライアントの番号・表示バージョンにする）。</li>
 *   <li>763ログイン（offline）：フレーム単位で翻訳する。設定フェーズは
 *       プロキシが内部クライアント役として消費する（内部765の場合は
 *       765役、内部764の場合は764役）。</li>
 *   <li>764ログイン（offline／online）：フレーム単位で翻訳する。
 *       763と違い設定フェーズはそのまま中継する（レジストリ差分だけ変換）。
 *       online-mode の認証は763と同じくプロキシが完結させる。
 *       内部764の場合は764クライアントが素通しになる。</li>
 *   <li>765ログイン（offline）：内部765の場合はハンドシェイクの直後に
 *       素通し（リレー）に切り替わる。内部764の場合は下向き変換
 *       （765→764）で翻訳する。暗号化・圧縮があってもバイト列をそのまま
 *       通すのは素通しの場合だけである。</li>
 *   <li>ログイン（online-mode、763・764・765共通。担当1）：プロキシが
 *       クライアントとの間で Mojang 認証を完結させる。Encryption
 *       Request→Response→hasJoined 照会の後、認証済み UUID・名前で
 *       内部バニラ（offline-mode、信頼済み接続）へ繋ぐ。クライアント側
 *       の暗号化は {@link MinecraftCipher} がパイプラインで扱い、
 *       ネイティブのクライアントは Login Success の後にリレーへ切り替わる
 *       （内部765なら765、内部764なら764）。翻訳が必要なクライアントは
 *       そのまま翻訳モードで続ける。
 *       hasJoined 照会は別スレッドで行い、Netty のイベントループを
 *       止めない。</li>
 * </ul>
 *
 * <p>振り分けの向きはクライアントのプロトコルと内部プロトコル
 * （{@link #internalProtocol()}）の比較で決まる。内部764の場合：
 * 764クライアントは素通し、765クライアントは下向き変換（765→764）、
 * 763クライアントは763→764の直接変換である。内部765の場合は従来通り
 * （765素通し、764／763は上向き変換）。
 *
 * <p>内部バニラは loopback（127.0.0.1）の信頼済み接続である。認証を
 * 済ませていない接続が内部バニラに直接届くと認証を迂回できてしまうため、
 * バニラ側ホストは loopback 以外を受け付けない（コンストラクタで検査）。
 * バニラ自身も {@code server-ip=127.0.0.1} に束縛する
 * （{@code VanillaServerHost} 参照）。
 *
 * <p>フロント（クライアント側）とバック（バニラ側）の両チャネルは同じ
 * イベントループで動かす。そうすれば {@link ProxySession}（スレッド
 * セーフではない）を1スレッドからだけ触ることになる。
 * リレーへの切り替えはパケット境界で行い、デコーダに残った断片は
 * 生バイトとして先に相手へ送る（{@link MinecraftPacket.Decoder#takeBuffered()}）。
 */
public final class ClientProxy {

    private static final Logger LOG = Logger.getLogger(ClientProxy.class.getName());

    private final int publicPort;
    private final String vanillaHost;
    private final int vanillaPort;
    private final boolean onlineMode;
    /**
     * 578クライアントに送るリソースパック（pack_format 5）。無ければ null。
     * コンストラクタの引数には無いため、{@link #setPack578(ResourcePack)}
     * で設定し、セッションへは {@link ProxySession#setPack578(ResourcePack)}
     * で渡す。
     */
    private ResourcePack pack578;
    /**
     * 575クライアントに送るリソースパック（pack_format 5）。無ければ null。
     * 578 と同じ後付けの流儀（{@link #setPack575(ResourcePack)}）。
     */
    private ResourcePack pack575;
    /**
     * 573クライアントに送るリソースパック（pack_format 5）。無ければ null。
     * 575 と同じ後付けの流儀（{@link #setPack573(ResourcePack)}）。
     */
    private ResourcePack pack573;
    /**
     * 477クライアントに送るリソースパック（pack_format 4）。無ければ null。
     * 480 と同じ後付けの流儀（{@link #setPack477(ResourcePack)}）。
     */
    private ResourcePack pack477;
    /**
     * 480クライアントに送るリソースパック（pack_format 4）。無ければ null。
     * 485 と同じ後付けの流儀（{@link #setPack480(ResourcePack)}）。
     */
    private ResourcePack pack480;
    /**
     * 485クライアントに送るリソースパック（pack_format 4）。無ければ null。
     * 490 と同じ後付けの流儀（{@link #setPack485(ResourcePack)}）。
     */
    private ResourcePack pack485;
    /**
     * 490クライアントに送るリソースパック（pack_format 4）。無ければ null。
     * 498 と同じ後付けの流儀（{@link #setPack490(ResourcePack)}）。
     */
    private ResourcePack pack490;
    /**
     * 498クライアントに送るリソースパック（pack_format 4）。無ければ null。
     * 573 と同じ後付けの流儀（{@link #setPack498(ResourcePack)}）。
     */
    private ResourcePack pack498;
    /** 758クライアントに送るリソースパック（pack_format 8）。無ければ null。 */
    private final ResourcePack pack758;
    /** 759クライアントに送るリソースパック（pack_format 9）。無ければ null。 */
    private final ResourcePack pack759;
    /** 760クライアントに送るリソースパック（pack_format 9）。無ければ null。 */
    private final ResourcePack pack760;
    /** 761クライアントに送るリソースパック（pack_format 12）。無ければ null。 */
    private final ResourcePack pack761;
    /** 762クライアントに送るリソースパック（pack_format 13）。無ければ null。 */
    private final ResourcePack pack762;
    /** 763クライアントに送るリソースパック（pack_format 15）。無ければ null。 */
    private final ResourcePack pack763;
    /** 764クライアントに送るリソースパック（pack_format 18）。無ければ null。 */
    private final ResourcePack pack764;
    /**
     * 765クライアントに送るリソースパック（pack_format 22）。内部が764の
     * 場合にだけ使う。内部が765の場合はバニラが送ってきたパックをそのまま
     * 出すため null のままでよい。無ければ null。
     */
    private final ResourcePack pack765;
    /**
     * 766クライアントに送るリソースパック（pack_format 32）。内部が766で
     * ない場合にだけ使う。内部が766の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。
     */
    private final ResourcePack pack766;
    /**
     * 767クライアントに送るリソースパック（pack_format 34）。内部が767で
     * ない場合にだけ使う。内部が767の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack767(ResourcePack)} で渡す
     * （{@code resolveHandshake} には767用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack767;
    /**
     * 768クライアントに送るリソースパック（pack_format 42）。内部が768で
     * ない場合にだけ使う。内部が768の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack768(ResourcePack)} で渡す
     * （{@code resolveHandshake} には768用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack768;
    /**
     * 769クライアントに送るリソースパック（pack_format 46）。内部が769で
     * ない場合にだけ使う。内部が769の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack769(ResourcePack)} で渡す
     * （{@code resolveHandshake} には769用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack769;
    /**
     * 770クライアントに送るリソースパック（pack_format 55）。内部が770で
     * ない場合にだけ使う。内部が770の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack770(ResourcePack)} で渡す
     * （{@code resolveHandshake} には770用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack770;
    /**
     * 771クライアントに送るリソースパック（pack_format 63）。内部が771で
     * ない場合にだけ使う。内部が771の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack771(ResourcePack)} で渡す
     * （{@code resolveHandshake} には771用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack771;
    /**
     * 772クライアントに送るリソースパック（pack_format 64）。内部が772で
     * ない場合にだけ使う。内部が772の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack772(ResourcePack)} で渡す
     * （{@code resolveHandshake} には772用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack772;
    /**
     * 773クライアントに送るリソースパック（pack_format 69）。内部が773で
     * ない場合にだけ使う。内部が773の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack773(ResourcePack)} で渡す
     * （{@code resolveHandshake} には773用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack773;
    /**
     * 774クライアントに送るリソースパック（pack_format 75）。内部が774で
     * ない場合にだけ使う。内部が774の場合はバニラが送ってきたパックをその
     * まま出すため null のままでよい。無ければ null。セッションへは
     * {@link ProxySession#setPack774(ResourcePack)} で渡す
     * （{@code resolveHandshake} には774用パックの引数がまだ無いため）。
     */
    private final ResourcePack pack774;
    private final ResourcePack pack775;
    private final ResourcePack pack776;
    /**
     * 内部バニラのプロトコル（758＝1.18.2、763＝1.20／1.20.1、766＝1.20.5／1.20.6、
     * 765＝1.20.4、764＝1.20.2、767＝1.21／1.21.1、768＝1.21.2／1.21.3、
     * 769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11）。呼び出し元は
     * {@code ProtocolVersion.serverProtocolFor(minecraftVersion)} で
     * 求めた値を渡すこと。プロキシはクライアントの
     * プロトコルとこの内部プロトコルを比べ、同じなら素通し、違えば
     * 変換する方向に切り替える（{@link ProxySession} 参照）。
     * 内部763も受け付ける：764／765／766／767クライアントの設定フェーズは
     * プロキシが Join Game の codec から合成する（ProxySession の
     * synthesizingConfig 参照）。763を拒否していたのは古い制限で、
     * 1.20系の相互連携（766→763 など）の妨げになるため解消した。
     * 内部767も受け付けるが、767のプレイ状態のルーティング自体は
     * ProxySession 側の実装待ちである（コンストラクタの検証だけが
     * 767を通す）。
     */
    private final int internalProtocol;
    /** 【担当1】プロキシ認証の設定（セッションサーバー URL・照会器）。 */
    private final ProxyAuthConfig authConfig;
    /**
     * 【バッチ4】Surfactant転送の共有シークレット。空（既定）なら転送なし。
     * コンストラクタの引数がこれ以上増えないよう、起動前に
     * {@link #setForwardingSecret(String)} で設定する
     * （パック767以降をセッションへ後付けする流儀と同じ）。
     */
    private volatile String forwardingSecret = "";

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running;
    /** 【担当1】hasJoined 照会用のスレッドプール（Netty のイベントループを止めないため）。 */
    private ExecutorService authExecutor;

    /**
     * @param publicPort  クライアントが接続する公開ポート
     * @param vanillaHost バニラの待ち受けホスト（通常 127.0.0.1）
     * @param vanillaPort バニラの内部ポート
     * @param onlineMode  バニラの online-mode（763の受け入れ可否に使う）
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, null, null);
    }

    /**
     * {@link #ClientProxy(int, String, int, boolean)} の763用パック付き版。
     *
     * @param pack763 763クライアントに送るリソースパック（pack_format 15）。
     *                {@code null} の場合はバニラが送ってきた765用パックを
     *                763クライアントにもそのまま出す
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, null,
                ProxyAuthConfig.defaults());
    }

    /**
     * {@link #ClientProxy(int, String, int, boolean, ResourcePack, ResourcePack)}
     * の764用パック付き版。
     *
     * @param pack764 764クライアントに送るリソースパック（pack_format 18）。
     *                {@code null} の場合はバニラが送ってきた765用パックを
     *                764クライアントにもそのまま出す
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                ProxyAuthConfig.defaults());
    }

    /**
     * 【担当1】{@link #ClientProxy(int, String, int, boolean, ResourcePack, ResourcePack)}
     * の認証設定付き版。
     *
     * @param authConfig プロキシ認証の設定。{@code null} の場合は既定
     *                   （本物の Mojang セッションサーバー）
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ProxyAuthConfig authConfig) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, null, authConfig);
    }

    /**
     * {@link #ClientProxy(int, String, int, boolean, ResourcePack, ResourcePack, ResourcePack)}
     * の認証設定付き版。
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, null, null, ProtocolVersion.V1_20_4);
    }

    /**
     * 逆向き対応版：内部プロトコルと765クライアント用のパックを指定する。
     * 766用パックは {@code null}（766クライアントには765用パックを出す
     * 従来の扱い）。766用パックを渡す場合は
     * {@link #ClientProxy(int, String, int, boolean, ResourcePack, ResourcePack,
     * ResourcePack, ProxyAuthConfig, ResourcePack, ResourcePack, int)}
     * を使うこと。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, null, internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, pack766, null, internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, pack766, pack767, null, internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, null, internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768／769クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, pack769, null,
                internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, null,
                internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770／771クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, null,
                internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770／771／772クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param pack772         772クライアントに送るリソースパック
     *                        （pack_format 64）。内部が772でない場合に使う。
     *                        {@code null} の場合は772クライアントにも
     *                        771用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack762, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, null,
                internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770／771／772／773クライアント用のパックを指定する。
     *
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param pack772         772クライアントに送るリソースパック
     *                        （pack_format 64）。内部が772でない場合に使う。
     *                        {@code null} の場合は772クライアントにも
     *                        771用パックを出す（ProxySession の既定の扱い）
     * @param pack773         773クライアントに送るリソースパック
     *                        （pack_format 69）。内部が773でない場合に使う。
     *                        {@code null} の場合は773クライアントにも
     *                        772用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（763＝1.20／1.20.1、
     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, int internalProtocol) {
        this(publicPort, vanillaHost, vanillaPort, onlineMode, null, null, null, pack762, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null, null, null,
                internalProtocol);
    }

    /**
     * 逆向き対応版：内部プロトコルと758／759／760／762／765／766／767／768／769／770／771／772／773／774クライアント用のパックを指定する。
     * 旧最大コンストラクタ（759先頭）と同じだが、758用パックも渡せる。
     *
     * @param pack758         758クライアントに送るリソースパック
     *                        （pack_format 8）。内部が758でない場合に使う。
     *                        {@code null} の場合は758クライアントにも
     *                        759用パックを出す（ProxySession の既定の扱い）
     * @param pack759         759クライアントに送るリソースパック
     *                        （pack_format 9）。内部が759でない場合に使う。
     *                        {@code null} の場合は759クライアントにも
     *                        760用パックを出す（ProxySession の既定の扱い）
     * @param pack760         760クライアントに送るリソースパック
     *                        （pack_format 9）。内部が760でない場合に使う。
     *                        {@code null} の場合は760クライアントにも
     *                        761用パックを出す（ProxySession の既定の扱い）
     * @param pack761         761クライアントに送るリソースパック
     *                        （pack_format 12）。内部が761でない場合に使う。
     *                        {@code null} の場合は761クライアントにも
     *                        762用パックを出す（ProxySession の既定の扱い）
     * @param pack762         762クライアントに送るリソースパック
     *                        （pack_format 13）。内部が762でない場合に使う。
     *                        {@code null} の場合は762クライアントにも
     *                        763用パックを出す（ProxySession の既定の扱い）
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param pack772         772クライアントに送るリソースパック
     *                        （pack_format 64）。内部が772でない場合に使う。
     *                        {@code null} の場合は772クライアントにも
     *                        771用パックを出す（ProxySession の既定の扱い）
     * @param pack773         773クライアントに送るリソースパック
     *                        （pack_format 69）。内部が773でない場合に使う。
     *                        {@code null} の場合は773クライアントにも
     *                        772用パックを出す（ProxySession の既定の扱い）
     * @param pack774         774クライアントに送るリソースパック
     *                        （pack_format 75）。内部が774でない場合に使う。
     *                        {@code null} の場合は774クライアントにも
     *                        773用パックを出す（ProxySession の既定の扱い）
     * @param pack775         775クライアントに送るリソースパック
     *                        （pack_format 84）。内部が775でない場合に使う。
     *                        {@code null} の場合は775クライアントにも
     *                        774用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（758＝1.18.2、759＝1.19、760＝1.19.1／1.19.2、761＝1.19.3、762＝1.19.4、
     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack758, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, ResourcePack pack775, int internalProtocol) {
        if (publicPort < 1 || publicPort > 65535) {
            throw new IllegalArgumentException("publicPort out of range: " + publicPort);
        }
        if (vanillaPort < 1 || vanillaPort > 65535) {
            throw new IllegalArgumentException("vanillaPort out of range: " + vanillaPort);
        }
        if (vanillaHost == null || vanillaHost.isBlank()) {
            throw new IllegalArgumentException("vanillaHost is required");
        }
        if (!isLoopbackHost(vanillaHost)) {
            // 内部バニラは信頼済み接続（認証なし）なので、loopback 以外に
            // 置くと誰でも認証を迂回して入れてしまう。受け付けない。
            throw new IllegalArgumentException(
                    "vanillaHost must be loopback (127.0.0.1, ::1 or localhost): "
                            + vanillaHost);
        }
        if (internalProtocol != ProtocolVersion.V1_14
                && internalProtocol != ProtocolVersion.V1_14_1
                && internalProtocol != ProtocolVersion.V1_14_2
                && internalProtocol != ProtocolVersion.V1_14_3
                && internalProtocol != ProtocolVersion.V1_14_4
                && internalProtocol != ProtocolVersion.V1_15
                && internalProtocol != ProtocolVersion.V1_15_1
                && internalProtocol != ProtocolVersion.V1_15_2
                && internalProtocol != ProtocolVersion.V1_18_2
                && internalProtocol != ProtocolVersion.V1_19
                && internalProtocol != ProtocolVersion.V1_19_1
                && internalProtocol != ProtocolVersion.V1_19_3
                && internalProtocol != ProtocolVersion.V1_19_4
                && internalProtocol != ProtocolVersion.V1_20_1
                && internalProtocol != ProtocolVersion.V1_20_2
                && internalProtocol != ProtocolVersion.V1_20_4
                && internalProtocol != ProtocolVersion.V1_20_5
                && internalProtocol != ProtocolVersion.V1_21
                && internalProtocol != ProtocolVersion.V1_21_2
                && internalProtocol != ProtocolVersion.V1_21_4
                && internalProtocol != ProtocolVersion.V1_21_5
                && internalProtocol != ProtocolVersion.V1_21_6
                && internalProtocol != ProtocolVersion.V1_21_7
                && internalProtocol != ProtocolVersion.V1_21_9
                && internalProtocol != ProtocolVersion.V1_21_11
                && internalProtocol != ProtocolVersion.V26_1) {
            throw new IllegalArgumentException(
                    "internalProtocol must be 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), "
                            + "769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10), 774 (1.21.11) or 775 (26.1): "
                            + internalProtocol);
        }
        this.publicPort = publicPort;
        this.vanillaHost = vanillaHost;
        this.vanillaPort = vanillaPort;
        this.onlineMode = onlineMode;
        this.pack758 = pack758;
        this.pack759 = pack759;
        this.pack760 = pack760;
        this.pack761 = pack761;
        this.pack762 = pack762;
        this.pack763 = pack763;
        this.pack764 = pack764;
        this.pack765 = pack765;
        this.pack766 = pack766;
        this.pack767 = pack767;
        this.pack768 = pack768;
        this.pack769 = pack769;
        this.pack770 = pack770;
        this.pack771 = pack771;
        this.pack772 = pack772;
        this.pack773 = pack773;
        this.pack774 = pack774;
        this.pack775 = pack775;
        this.pack776 = null; // 旧コンストラクタには pack776 引数が無い（呼び出し元なしの旧経路のため null 固定）
        this.internalProtocol = internalProtocol;
        this.authConfig = authConfig == null ? ProxyAuthConfig.defaults() : authConfig;
    }

    /**
     * 逆向き対応版：内部プロトコルと759／760／762／765／766／767／768／769／770／771／772／773／774クライアント用のパックを指定する。
     *
     * @param pack759         759クライアントに送るリソースパック
     *                        （pack_format 9）。内部が759でない場合に使う。
     *                        {@code null} の場合は759クライアントにも
     *                        760用パックを出す（ProxySession の既定の扱い）
     * @param pack760         760クライアントに送るリソースパック
     *                        （pack_format 9）。内部が760でない場合に使う。
     *                        {@code null} の場合は760クライアントにも
     *                        761用パックを出す（ProxySession の既定の扱い）
     * @param pack761         761クライアントに送るリソースパック
     *                        （pack_format 12）。内部が761でない場合に使う。
     *                        {@code null} の場合は761クライアントにも
     *                        762用パックを出す（ProxySession の既定の扱い）
     * @param pack762         762クライアントに送るリソースパック
     *                        （pack_format 13）。内部が762でない場合に使う。
     *                        {@code null} の場合は762クライアントにも
     *                        763用パックを出す（ProxySession の既定の扱い）
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param pack772         772クライアントに送るリソースパック
     *                        （pack_format 64）。内部が772でない場合に使う。
     *                        {@code null} の場合は772クライアントにも
     *                        771用パックを出す（ProxySession の既定の扱い）
     * @param pack773         773クライアントに送るリソースパック
     *                        （pack_format 69）。内部が773でない場合に使う。
     *                        {@code null} の場合は773クライアントにも
     *                        772用パックを出す（ProxySession の既定の扱い）
     * @param pack774         774クライアントに送るリソースパック
     *                        （pack_format 75）。内部が774でない場合に使う。
     *                        {@code null} の場合は774クライアントにも
     *                        773用パックを出す（ProxySession の既定の扱い）
     * @param pack775         775クライアントに送るリソースパック
     *                        （pack_format 84）。内部が775でない場合に使う。
     *                        {@code null} の場合は775クライアントにも
     *                        774用パックを出す（ProxySession の既定の扱い）
     * @param pack776         776クライアントに送るリソースパック
     *                        （pack_format 88）。内部が776でない場合に使う。
     *                        {@code null} の場合は776クライアントにも
     *                        775用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（759＝1.19、760＝1.19.1／1.19.2、761＝1.19.3、762＝1.19.4、
     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1、776＝26.2）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, ResourcePack pack775, ResourcePack pack776, int internalProtocol) {
        if (publicPort < 1 || publicPort > 65535) {
            throw new IllegalArgumentException("publicPort out of range: " + publicPort);
        }
        if (vanillaPort < 1 || vanillaPort > 65535) {
            throw new IllegalArgumentException("vanillaPort out of range: " + vanillaPort);
        }
        if (vanillaHost == null || vanillaHost.isBlank()) {
            throw new IllegalArgumentException("vanillaHost is required");
        }
        if (!isLoopbackHost(vanillaHost)) {
            // 内部バニラは信頼済み接続（認証なし）なので、loopback 以外に
            // 置くと誰でも認証を迂回して入れてしまう。受け付けない。
            throw new IllegalArgumentException(
                    "vanillaHost must be loopback (127.0.0.1, ::1 or localhost): "
                            + vanillaHost);
        }
        if (internalProtocol != ProtocolVersion.V1_14
                && internalProtocol != ProtocolVersion.V1_14_1
                && internalProtocol != ProtocolVersion.V1_14_2
                && internalProtocol != ProtocolVersion.V1_14_3
                && internalProtocol != ProtocolVersion.V1_14_4
                && internalProtocol != ProtocolVersion.V1_15
                && internalProtocol != ProtocolVersion.V1_15_1
                && internalProtocol != ProtocolVersion.V1_15_2
                && internalProtocol != ProtocolVersion.V1_18_2
                && internalProtocol != ProtocolVersion.V1_19
                && internalProtocol != ProtocolVersion.V1_19_1
                && internalProtocol != ProtocolVersion.V1_19_3
                && internalProtocol != ProtocolVersion.V1_19_4
                && internalProtocol != ProtocolVersion.V1_20_1
                && internalProtocol != ProtocolVersion.V1_20_2
                && internalProtocol != ProtocolVersion.V1_20_4
                && internalProtocol != ProtocolVersion.V1_20_5
                && internalProtocol != ProtocolVersion.V1_21
                && internalProtocol != ProtocolVersion.V1_21_2
                && internalProtocol != ProtocolVersion.V1_21_4
                && internalProtocol != ProtocolVersion.V1_21_5
                && internalProtocol != ProtocolVersion.V1_21_6
                && internalProtocol != ProtocolVersion.V1_21_7
                && internalProtocol != ProtocolVersion.V1_21_9
                && internalProtocol != ProtocolVersion.V1_21_11
                && internalProtocol != ProtocolVersion.V26_1
                && internalProtocol != ProtocolVersion.V26_2) {
            throw new IllegalArgumentException(
                    "internalProtocol must be 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), "
                            + "769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10), 774 (1.21.11), 775 (26.1) or 776 (26.2): "
                            + internalProtocol);
        }
        this.publicPort = publicPort;
        this.vanillaHost = vanillaHost;
        this.vanillaPort = vanillaPort;
        this.onlineMode = onlineMode;
        this.pack758 = null;
        this.pack759 = pack759;
        this.pack760 = pack760;
        this.pack761 = pack761;
        this.pack762 = pack762;
        this.pack763 = pack763;
        this.pack764 = pack764;
        this.pack765 = pack765;
        this.pack766 = pack766;
        this.pack767 = pack767;
        this.pack768 = pack768;
        this.pack769 = pack769;
        this.pack770 = pack770;
        this.pack771 = pack771;
        this.pack772 = pack772;
        this.pack773 = pack773;
        this.pack774 = pack774;
        this.pack775 = pack775;
        this.pack776 = pack776;
        this.internalProtocol = internalProtocol;
        this.authConfig = authConfig == null ? ProxyAuthConfig.defaults() : authConfig;
    }
    /**
     * 逆向き対応版：内部プロトコルと758〜776クライアント用のパックを指定する。
     *
     * @param pack758         758クライアントに送るリソースパック
     *                        （pack_format 8）。内部が758でない場合に使う。
     *                        {@code null} の場合は758クライアントにも
     *                        759用パックを出す（ProxySession の既定の扱い）
     * @param pack759         759クライアントに送るリソースパック
     *                        （pack_format 9）。内部が759でない場合に使う。
     *                        {@code null} の場合は759クライアントにも
     *                        760用パックを出す（ProxySession の既定の扱い）
     * @param pack760         760クライアントに送るリソースパック
     *                        （pack_format 9）。内部が760でない場合に使う。
     *                        {@code null} の場合は760クライアントにも
     *                        761用パックを出す（ProxySession の既定の扱い）
     * @param pack761         761クライアントに送るリソースパック
     *                        （pack_format 12）。内部が761でない場合に使う。
     *                        {@code null} の場合は761クライアントにも
     *                        762用パックを出す（ProxySession の既定の扱い）
     * @param pack762         762クライアントに送るリソースパック
     *                        （pack_format 13）。内部が762でない場合に使う。
     *                        {@code null} の場合は762クライアントにも
     *                        763用パックを出す（ProxySession の既定の扱い）
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param pack772         772クライアントに送るリソースパック
     *                        （pack_format 64）。内部が772でない場合に使う。
     *                        {@code null} の場合は772クライアントにも
     *                        771用パックを出す（ProxySession の既定の扱い）
     * @param pack773         773クライアントに送るリソースパック
     *                        （pack_format 69）。内部が773でない場合に使う。
     *                        {@code null} の場合は773クライアントにも
     *                        772用パックを出す（ProxySession の既定の扱い）
     * @param pack774         774クライアントに送るリソースパック
     *                        （pack_format 75）。内部が774でない場合に使う。
     *                        {@code null} の場合は774クライアントにも
     *                        773用パックを出す（ProxySession の既定の扱い）
     * @param pack775         775クライアントに送るリソースパック
     *                        （pack_format 84）。内部が775でない場合に使う。
     *                        {@code null} の場合は775クライアントにも
     *                        774用パックを出す（ProxySession の既定の扱い）
     * @param pack776         776クライアントに送るリソースパック
     *                        （pack_format 88）。内部が776でない場合に使う。
     *                        {@code null} の場合は776クライアントにも
     *                        775用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（758＝1.18.2、759＝1.19、760＝1.19.1／1.19.2、761＝1.19.3、762＝1.19.4、
     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1、776＝26.2）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack758, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, ResourcePack pack775, ResourcePack pack776, int internalProtocol) {
        if (publicPort < 1 || publicPort > 65535) {
            throw new IllegalArgumentException("publicPort out of range: " + publicPort);
        }
        if (vanillaPort < 1 || vanillaPort > 65535) {
            throw new IllegalArgumentException("vanillaPort out of range: " + vanillaPort);
        }
        if (vanillaHost == null || vanillaHost.isBlank()) {
            throw new IllegalArgumentException("vanillaHost is required");
        }
        if (!isLoopbackHost(vanillaHost)) {
            // 内部バニラは信頼済み接続（認証なし）なので、loopback 以外に
            // 置くと誰でも認証を迂回して入れてしまう。受け付けない。
            throw new IllegalArgumentException(
                    "vanillaHost must be loopback (127.0.0.1, ::1 or localhost): "
                            + vanillaHost);
        }
        if (internalProtocol != ProtocolVersion.V1_14
                && internalProtocol != ProtocolVersion.V1_14_1
                && internalProtocol != ProtocolVersion.V1_14_2
                && internalProtocol != ProtocolVersion.V1_14_3
                && internalProtocol != ProtocolVersion.V1_14_4
                && internalProtocol != ProtocolVersion.V1_15
                && internalProtocol != ProtocolVersion.V1_15_1
                && internalProtocol != ProtocolVersion.V1_15_2
                && internalProtocol != ProtocolVersion.V1_18_2
                && internalProtocol != ProtocolVersion.V1_19
                && internalProtocol != ProtocolVersion.V1_19_1
                && internalProtocol != ProtocolVersion.V1_19_3
                && internalProtocol != ProtocolVersion.V1_19_4
                && internalProtocol != ProtocolVersion.V1_20_1
                && internalProtocol != ProtocolVersion.V1_20_2
                && internalProtocol != ProtocolVersion.V1_20_4
                && internalProtocol != ProtocolVersion.V1_20_5
                && internalProtocol != ProtocolVersion.V1_21
                && internalProtocol != ProtocolVersion.V1_21_2
                && internalProtocol != ProtocolVersion.V1_21_4
                && internalProtocol != ProtocolVersion.V1_21_5
                && internalProtocol != ProtocolVersion.V1_21_6
                && internalProtocol != ProtocolVersion.V1_21_7
                && internalProtocol != ProtocolVersion.V1_21_9
                && internalProtocol != ProtocolVersion.V1_21_11
                && internalProtocol != ProtocolVersion.V26_1
                && internalProtocol != ProtocolVersion.V26_2) {
            throw new IllegalArgumentException(
                    "internalProtocol must be 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), "
                            + "769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10), 774 (1.21.11), 775 (26.1) or 776 (26.2): "
                            + internalProtocol);
        }
        this.publicPort = publicPort;
        this.vanillaHost = vanillaHost;
        this.vanillaPort = vanillaPort;
        this.onlineMode = onlineMode;
        this.pack758 = pack758;
        this.pack759 = pack759;
        this.pack760 = pack760;
        this.pack761 = pack761;
        this.pack762 = pack762;
        this.pack763 = pack763;
        this.pack764 = pack764;
        this.pack765 = pack765;
        this.pack766 = pack766;
        this.pack767 = pack767;
        this.pack768 = pack768;
        this.pack769 = pack769;
        this.pack770 = pack770;
        this.pack771 = pack771;
        this.pack772 = pack772;
        this.pack773 = pack773;
        this.pack774 = pack774;
        this.pack775 = pack775;
        this.pack776 = pack776;
        this.internalProtocol = internalProtocol;
        this.authConfig = authConfig == null ? ProxyAuthConfig.defaults() : authConfig;
    }


    /**
     * 逆向き対応版：内部プロトコルと762／765／766／767／768／769／770／771／772／773／774クライアント用のパックを指定する。
     *
     * @param pack761         761クライアントに送るリソースパック
     *                        （pack_format 12）。内部が761でない場合に使う。
     *                        {@code null} の場合は761クライアントにも
     *                        762用パックを出す（ProxySession の既定の扱い）
     * @param pack762         762クライアントに送るリソースパック
     *                        （pack_format 13）。内部が762でない場合に使う。
     *                        {@code null} の場合は762クライアントにも
     *                        763用パックを出す（ProxySession の既定の扱い）
     * @param pack765         765クライアントに送るリソースパック
     *                        （pack_format 22）。内部が764の場合にだけ使う。
     *                        {@code null} の場合はバニラが送ってきたパックを
     *                        そのまま出す
     * @param pack766         766クライアントに送るリソースパック
     *                        （pack_format 32）。内部が766でない場合に使う。
     *                        {@code null} の場合は766クライアントにも
     *                        765用パックを出す（従来の扱い）
     * @param pack767         767クライアントに送るリソースパック
     *                        （pack_format 34）。内部が767でない場合に使う。
     *                        {@code null} の場合は767クライアントにも
     *                        766用パックを出す（ProxySession の既定の扱い）
     * @param pack768         768クライアントに送るリソースパック
     *                        （pack_format 42）。内部が768でない場合に使う。
     *                        {@code null} の場合は768クライアントにも
     *                        767用パックを出す（ProxySession の既定の扱い）
     * @param pack769         769クライアントに送るリソースパック
     *                        （pack_format 46）。内部が769でない場合に使う。
     *                        {@code null} の場合は769クライアントにも
     *                        768用パックを出す（ProxySession の既定の扱い）
     * @param pack770         770クライアントに送るリソースパック
     *                        （pack_format 55）。内部が770でない場合に使う。
     *                        {@code null} の場合は770クライアントにも
     *                        769用パックを出す（ProxySession の既定の扱い）
     * @param pack771         771クライアントに送るリソースパック
     *                        （pack_format 63）。内部が771でない場合に使う。
     *                        {@code null} の場合は771クライアントにも
     *                        770用パックを出す（ProxySession の既定の扱い）
     * @param pack772         772クライアントに送るリソースパック
     *                        （pack_format 64）。内部が772でない場合に使う。
     *                        {@code null} の場合は772クライアントにも
     *                        771用パックを出す（ProxySession の既定の扱い）
     * @param pack773         773クライアントに送るリソースパック
     *                        （pack_format 69）。内部が773でない場合に使う。
     *                        {@code null} の場合は773クライアントにも
     *                        772用パックを出す（ProxySession の既定の扱い）
     * @param pack774         774クライアントに送るリソースパック
     *                        （pack_format 75）。内部が774でない場合に使う。
     *                        {@code null} の場合は774クライアントにも
     *                        773用パックを出す（ProxySession の既定の扱い）
     * @param internalProtocol 内部バニラのプロトコル（761＝1.19.3、762＝1.19.4、
     *                        763＝1.20／1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11）。呼び出し元は
     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}
     *                        で求めた値を渡すこと
     */
    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,
            boolean onlineMode, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,
            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, int internalProtocol) {
        if (publicPort < 1 || publicPort > 65535) {
            throw new IllegalArgumentException("publicPort out of range: " + publicPort);
        }
        if (vanillaPort < 1 || vanillaPort > 65535) {
            throw new IllegalArgumentException("vanillaPort out of range: " + vanillaPort);
        }
        if (vanillaHost == null || vanillaHost.isBlank()) {
            throw new IllegalArgumentException("vanillaHost is required");
        }
        if (!isLoopbackHost(vanillaHost)) {
            // 内部バニラは信頼済み接続（認証なし）なので、loopback 以外に
            // 置くと誰でも認証を迂回して入れてしまう。受け付けない。
            throw new IllegalArgumentException(
                    "vanillaHost must be loopback (127.0.0.1, ::1 or localhost): "
                            + vanillaHost);
        }
        if (internalProtocol != ProtocolVersion.V1_19_3
                && internalProtocol != ProtocolVersion.V1_19_4
                && internalProtocol != ProtocolVersion.V1_20_1
                && internalProtocol != ProtocolVersion.V1_20_2
                && internalProtocol != ProtocolVersion.V1_20_4
                && internalProtocol != ProtocolVersion.V1_20_5
                && internalProtocol != ProtocolVersion.V1_21
                && internalProtocol != ProtocolVersion.V1_21_2
                && internalProtocol != ProtocolVersion.V1_21_4
                && internalProtocol != ProtocolVersion.V1_21_5
                && internalProtocol != ProtocolVersion.V1_21_6
                && internalProtocol != ProtocolVersion.V1_21_7
                && internalProtocol != ProtocolVersion.V1_21_9
                && internalProtocol != ProtocolVersion.V1_21_11) {
            throw new IllegalArgumentException(
                    "internalProtocol must be 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5), 767 (1.21), 768 (1.21.2), "
                            + "769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10) or 774 (1.21.11): "
                            + internalProtocol);
        }
        this.publicPort = publicPort;
        this.vanillaHost = vanillaHost;
        this.vanillaPort = vanillaPort;
        this.onlineMode = onlineMode;
        this.pack758 = null;
        this.pack759 = null;
        this.pack760 = null;
        this.pack761 = pack761;
        this.pack762 = pack762;
        this.pack763 = pack763;
        this.pack764 = pack764;
        this.pack765 = pack765;
        this.pack766 = pack766;
        this.pack767 = pack767;
        this.pack768 = pack768;
        this.pack769 = pack769;
        this.pack770 = pack770;
        this.pack771 = pack771;
        this.pack772 = pack772;
        this.pack773 = pack773;
        this.pack774 = pack774;
        this.pack775 = null; // 旧コンストラクタには pack775 引数が無い（呼び出し元なしの旧経路のため null 固定）
        this.pack776 = null; // 旧コンストラクタには pack776 引数が無い（呼び出し元なしの旧経路のため null 固定）
        this.internalProtocol = internalProtocol;
        this.authConfig = authConfig == null ? ProxyAuthConfig.defaults() : authConfig;
    }

    /** 内部バニラのホストが loopback か（信頼済み接続の条件）。 */
    static boolean isLoopbackHost(String host) {
        String normalized = host.strip().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("127.0.0.1")
                || normalized.equals("localhost")
                || normalized.equals("::1")
                || normalized.equals("[::1]");
    }

    public int publicPort() {
        return publicPort;
    }

    public String vanillaHost() {
        return vanillaHost;
    }

    public int vanillaPort() {
        return vanillaPort;
    }

    public boolean onlineMode() {
        return onlineMode;
    }

    /** 763クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack763() {
        return pack763;
    }

    /** 764クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack764() {
        return pack764;
    }

    /**
     * 765クライアントに送るリソースパック（無ければ null）。内部が764の
     * 場合にだけ使う差し替え用で、内部が765の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack765() {
        return pack765;
    }

    /**
     * 766クライアントに送るリソースパック（無ければ null）。内部が766で
     * ない場合に使う差し替え用で、内部が766の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack766() {
        return pack766;
    }

    /**
     * 767クライアントに送るリソースパック（無ければ null）。内部が767で
     * ない場合に使う差し替え用で、内部が767の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack767() {
        return pack767;
    }

    /**
     * 768クライアントに送るリソースパック（無ければ null）。内部が768で
     * ない場合に使う差し替え用で、内部が768の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack768() {
        return pack768;
    }

    /**
     * 769クライアントに送るリソースパック（無ければ null）。内部が769で
     * ない場合に使う差し替え用で、内部が769の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack769() {
        return pack769;
    }

    /**
     * 770クライアントに送るリソースパック（無ければ null）。内部が770で
     * ない場合に使う差し替え用で、内部が770の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack770() {
        return pack770;
    }

    /**
     * 771クライアントに送るリソースパック（無ければ null）。内部が771で
     * ない場合に使う差し替え用で、内部が771の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack771() {
        return pack771;
    }

    /**
     * 772クライアントに送るリソースパック（無ければ null）。内部が772で
     * ない場合に使う差し替え用で、内部が772の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack772() {
        return pack772;
    }
    /**
     * 773クライアントに送るリソースパック（無ければ null）。内部が773で
     * ない場合に使う差し替え用で、内部が773の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack773() {
        return pack773;
    }

    /**
     * 774クライアントに送るリソースパック（無ければ null）。内部が774で
     * ない場合に使う差し替え用で、内部が774の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack774() {
        return pack774;
    }

    /**
     * 775クライアントに送るリソースパック（無ければ null）。内部が775で
     * ない場合に使う差し替え用で、内部が775の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack775() {
        return pack775;
    }

    /**
     * 776クライアントに送るリソースパック（無ければ null）。内部が776で
     * ない場合に使う差し替え用で、内部が776の場合は null のまま
     * （バニラのパックをそのまま出す）。
     */
    public ResourcePack pack776() {
        return pack776;
    }

    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5、767＝1.21、768＝1.21.2、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11）。 */
    public int internalProtocol() {
        return internalProtocol;
    }

    /** 【担当1】プロキシ認証の設定。 */
    public ProxyAuthConfig authConfig() {
        return authConfig;
    }

    /**
     * 【バッチ4】Surfactant転送の共有シークレットを設定する
     * （{@code detergentmc.yml} の {@code forwarding.secret}）。
     * 起動（{@link #start()}）前に呼ぶこと。空・null なら転送なしで、
     * 従来の接続の動作は一切変わらない。設定すると、このプロキシへの
     * ログイン接続は全て転送の検証対象になり、検証が通らない接続は
     * 切断される（Surfactant経由でしか入れなくなる）。
     */
    public void setForwardingSecret(String forwardingSecret) {
        this.forwardingSecret = forwardingSecret == null ? "" : forwardingSecret;
    }

    /** 【バッチ4】Surfactant転送の共有シークレット（未設定なら空）。 */
    public String forwardingSecret() {
        return forwardingSecret;
    }

    /**
     * 578クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-578} 等）。
     * 起動（{@link #start()}）前に呼ぶこと。コンストラクタの引数が
     * これ以上増えないよう、転送シークレットと同じ後付けの流儀にする。
     */
    /**
     * 573クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-573} 等）。
     * 起動（{@link #start()}）前に呼ぶこと。
     */
    /**
     * 490クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-490} 等）。
     * 起動（{@link #start()}）前に呼ぶこと。
     */
    /**
     * 477クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-477} 等）。
     * 起動（{@link #start()}）前に呼ぶこと。
     */
    public void setPack477(ResourcePack pack477) {
        this.pack477 = pack477;
    }

    /** 477クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack477() {
        return pack477;
    }

    /**
     * 480クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-480} 等）。
     * 起動（{@link #start()}）前に呼ぶこと。
     */
    public void setPack480(ResourcePack pack480) {
        this.pack480 = pack480;
    }

    /** 480クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack480() {
        return pack480;
    }

    /**
     * 485クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-485} 等）。
     * 起動（{@link #start()}）前に呼ぶこと。
     */
    public void setPack485(ResourcePack pack485) {
        this.pack485 = pack485;
    }

    /** 485クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack485() {
        return pack485;
    }

    public void setPack490(ResourcePack pack490) {
        this.pack490 = pack490;
    }

    /** 490クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack490() {
        return pack490;
    }

    public void setPack498(ResourcePack pack498) {
        this.pack498 = pack498;
    }

    public ResourcePack pack498() {
        return pack498;
    }

    public void setPack573(ResourcePack pack573) {
        this.pack573 = pack573;
    }

    /** 573クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack573() {
        return pack573;
    }

    /**
     * 575クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-575} 等）。
     * 起動（{@link #start()}）前に呼ぶこと。
     */
    public void setPack575(ResourcePack pack575) {
        this.pack575 = pack575;
    }

    /** 575クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack575() {
        return pack575;
    }

    public void setPack578(ResourcePack pack578) {
        this.pack578 = pack578;
    }

    /** 578クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack578() {
        return pack578;
    }

    public boolean isRunning() {
        return running && serverChannel != null && serverChannel.isActive();
    }

    /**
     * 公開ポートで待ち受けを開始する。バインドに失敗したら
     * {@link IOException} を投げる（呼び出し元はバニラも止めること。
     * バニラだけが残る状態を作らない）。
     */
    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("ClientProxy is already running");
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        // 【担当1】hasJoined 照会用。デーモンスレッドで、プロキシ停止時に閉じる。
        AtomicInteger threadCount = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "detergentmc-auth-" + threadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        authExecutor = Executors.newCachedThreadPool(factory);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new ProxyLinkHandler(
                                Side.CLIENT, ClientProxy.this));
                    }
                });
        try {
            ChannelFuture bound = bootstrap.bind(publicPort).sync();
            serverChannel = bound.channel();
            running = true;
            LOG.info(() -> "DetergentMC proxy listening on port " + publicPort
                    + " (vanilla on " + vanillaHost + ":" + vanillaPort
                    + ", internal protocol=" + internalProtocol
                    + ", online-mode=" + onlineMode + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
            throw new IOException("Interrupted while binding the proxy port " + publicPort, e);
        } catch (RuntimeException e) {
            stop();
            throw new IOException("Failed to bind the proxy port " + publicPort
                    + ": " + e.getMessage(), e);
        }
    }

    /** 公開ポートの待ち受けを止め、イベントループを閉じる。 */
    public synchronized void stop() {
        running = false;
        if (serverChannel != null) {
            try {
                serverChannel.close().syncUninterruptibly();
            } catch (RuntimeException ignored) {
                // 停止時はログを出さない。
            }
            serverChannel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup = null;
        }
        if (authExecutor != null) {
            authExecutor.shutdownNow();
            authExecutor = null;
        }
        LOG.info("DetergentMC proxy stopped");
    }

    /** フロント／バックのどちら側か。 */
    enum Side {
        CLIENT, SERVER
    }

    /**
     * 1接続の片側。もう片側（peer）と {@link ProxySession} を共有する。
     * フレーム単位の翻訳モードと、生バイトの素通し（リレー）モードを
     * 切り替えられる。
     */
    static final class ProxyLinkHandler extends ChannelInboundHandlerAdapter {

        private final Side side;
        private final ClientProxy proxy;
        private final MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();

        private ProxySession session;
        private Channel peer;
        private ProxyLinkHandler peerHandler;
        /** バックへの接続が完了する前に届いたパケットの待ち行列。 */
        private final Queue<byte[]> pendingBytes = new ArrayDeque<>();
        private boolean backendConnecting;
        private volatile boolean closed;
        // 【担当1】online-mode認証まわり（フロント側だけが使う）。
        /** hasJoined 照会を投げたか（二重投げ防止）。 */
        private boolean authSubmitted;
        /** クライアントとの暗号化をパイプラインに入れたか。 */
        private boolean cipherEnabled;
        /** hasJoined 照会の待ち受け中に届いたクライアントのパケット（後で処理する）。 */
        private final Queue<MinecraftPacket> queuedDuringAuth = new ArrayDeque<>();

        ProxyLinkHandler(Side side, ClientProxy proxy) {
            this.side = side;
            this.proxy = proxy;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (side == Side.CLIENT) {
                connectBackend(ctx);
            }
            ctx.fireChannelActive();
        }

        /** フロントが開いたら、同じイベントループでバニラへつなぐ。 */
        private void connectBackend(ChannelHandlerContext ctx) {
            backendConnecting = true;
            ProxyLinkHandler backend = new ProxyLinkHandler(Side.SERVER, proxy);
            backend.peer = ctx.channel();
            backend.peerHandler = this;
            this.peerHandler = backend;
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(backend);
                        }
                    });
            bootstrap.connect(new InetSocketAddress(proxy.vanillaHost, proxy.vanillaPort))
                    .addListener(future -> {
                        backendConnecting = false;
                        if (future.isSuccess()) {
                            peer = ((ChannelFuture) future).channel();
                            backend.peer = ctx.channel();
                            flushPending(ctx);
                        } else {
                            LOG.warning("Could not connect to the vanilla server ("
                                    + proxy.vanillaHost + ":" + proxy.vanillaPort
                                    + "): " + future.cause());
                            closeBoth(ctx);
                        }
                    });
        }

        /** バック接続前に届いたバイト列を、接続後に処理する。 */
        private void flushPending(ChannelHandlerContext ctx) {
            byte[] queued;
            while ((queued = pendingBytes.poll()) != null) {
                processBytes(ctx, queued);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                ReferenceCountUtil.release(msg);
                return;
            }
            try {
                if (session != null && session.isRelayMode()) {
                    // 素通しモード：フレームを解釈せず生バイトのまま送る。
                    forwardRaw(buf);
                    return;
                }
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                if (side == Side.CLIENT && peer == null && backendConnecting) {
                    // バニラへまだつながっていない：後で処理する。
                    pendingBytes.add(bytes);
                    return;
                }
                if (side == Side.CLIENT && peer == null) {
                    // バニラへの接続に失敗した後。フロントは閉じられる。
                    closeBoth(ctx);
                    return;
                }
                processBytes(ctx, bytes);
            } finally {
                buf.release();
            }
        }

        /** バイト列をフレームに分け、セッションに通して相手へ送る。 */
        private void processBytes(ChannelHandlerContext ctx, byte[] bytes) {
            List<MinecraftPacket> packets;
            try {
                packets = decoder.feed(bytes);
            } catch (IllegalArgumentException e) {
                LOG.warning("不正なフレームを受け取ったため切断します: " + e.getMessage());
                closeBoth(ctx);
                return;
            }
            for (MinecraftPacket packet : packets) {
                if (!handlePacket(ctx, packet)) {
                    return;
                }
            }
        }

        /**
         * 1パケットをセッションに通す。ハンドシェイクだけは特別扱い
         * （プロトコルが決まるのはここなので、セッションを作り直す）。
         */
        private boolean handlePacket(ChannelHandlerContext ctx, MinecraftPacket packet) {
            if (side == Side.CLIENT && session == null) {
                if (packet.id() != 0x00) {
                    LOG.warning("最初のパケットがハンドシェイクではないため切断します。");
                    closeBoth(ctx);
                    return false;
                }
                ProxySession.HandshakeResult result = ProxySession.resolveHandshake(
                        packet.payload(), proxy.onlineMode, proxy.pack763, proxy.pack764,
                        proxy.authConfig, proxy.pack765, proxy.pack766,
                        proxy.internalProtocol, proxy.forwardingSecret);
                if (result.disconnect()) {
                    writeToClient(ctx, result.disconnectToClient());
                    closeBoth(ctx);
                    return false;
                }
                session = result.session();
                peerHandler.session = session;
                // 758／759／760／761／762／767／768／769／770／771／772／773／774用パックは resolveHandshake の引数には無いため、
                // セッションへ直接渡す（ProxySession#setPack758／setPack759／setPack760／setPack761／setPack762／setPack767／setPack768／setPack769／setPack770／setPack772／setPack773／setPack774／setPack775／setPack776）。
                // 758クライアントには pack758（pack_format 8）を出し、
                // 未設定の場合は ProxySession が pack759 に落とす。
                // 759クライアントには pack759（pack_format 9）を出し、
                // 未設定の場合は ProxySession が pack760 に落とす。
                // 760クライアントには pack760（pack_format 9）を出し、
                // 未設定の場合は ProxySession が pack761 に落とす。
                // 761クライアントには pack761（pack_format 12）を出し、
                // 未設定の場合は ProxySession が pack762 に落とす。
                // 762クライアントには pack762（pack_format 13）を出し、
                // 未設定の場合は ProxySession が pack763 に落とす。
                // 767クライアントには pack767（pack_format 34）を出し、
                // 未設定の場合は ProxySession が pack766 に落とす。
                // 768クライアントには pack768（pack_format 42）を出し、
                // 未設定の場合は ProxySession が pack767 に落とす。
                // 769クライアントには pack769（pack_format 46）を出し、
                // 未設定の場合は ProxySession が pack768 に落とす。
                // 770クライアントには pack770（pack_format 55）を出し、
                // 未設定の場合は ProxySession が pack769 に落とす。
                // 771クライアントには pack771（pack_format 63）を出し、
                // 未設定の場合は ProxySession が pack770 に落とす。
                // 773クライアントには pack773（pack_format 69）を出し、
                // 未設定の場合は ProxySession が pack772 に落とす。
                // 774クライアントには pack774（pack_format 75）を出し、
                // 未設定の場合は ProxySession が pack773 に落とす。
                // 775クライアントには pack775（pack_format 84）を出し、
                // 未設定の場合は ProxySession が pack774 に落とす。
                // 776クライアントには pack776（pack_format 88）を出し、
                // 未設定の場合は ProxySession が pack775 に落とす。
                // 772クライアントには pack772（pack_format 64）を出し、
                // 未設定の場合は ProxySession が pack771 に落とす。
                session.setPack477(proxy.pack477);
                session.setPack480(proxy.pack480);
                session.setPack485(proxy.pack485);
                session.setPack490(proxy.pack490);
                session.setPack498(proxy.pack498);
                session.setPack573(proxy.pack573);
                session.setPack575(proxy.pack575);
                session.setPack578(proxy.pack578);
                session.setPack758(proxy.pack758);
                session.setPack759(proxy.pack759);
                session.setPack760(proxy.pack760);
                session.setPack761(proxy.pack761);
                session.setPack762(proxy.pack762);
                session.setPack767(proxy.pack767);
                session.setPack768(proxy.pack768);
                session.setPack769(proxy.pack769);
                session.setPack770(proxy.pack770);
                session.setPack771(proxy.pack771);
                session.setPack773(proxy.pack773);
                session.setPack774(proxy.pack774);
                session.setPack775(proxy.pack775);
                session.setPack776(proxy.pack776);
                session.setPack772(proxy.pack772);
                if (result.toServer() != null) {
                    writeToServer(ctx, result.toServer());
                }
                if (result.relay()) {
                    switchToRelay(ctx);
                }
                return true;
            }
            if (session == null) {
                // バック側でセッションが無い（フロントがまだハンドシェイクを
                // 処理していない）のはあり得ない。念のため落とす。
                return true;
            }
            // 【担当1】hasJoined 照会の待ち受け中は、クライアントからの
            // パケットを溜めておく（認証が終わるまでバニラへは送らない）。
            // 通常クライアントは Login Success まで何も送ってこないが、
            // 念のための受け皿である。
            if (side == Side.CLIENT && session.isAuthPending()) {
                queuedDuringAuth.add(packet);
                return true;
            }
            ProxySession.Outcome outcome;
            if (side == Side.CLIENT) {
                outcome = session.handleClientPacket(packet);
            } else {
                outcome = session.handleServerPacket(packet);
            }
            boolean keepGoing = deliverOutcome(ctx, outcome);
            if (side == Side.CLIENT) {
                maybeStartAuthVerification(ctx);
            }
            return keepGoing;
        }

        /**
         * セッションの処理結果を相手へ送る。リレー切替・切断もここで行う。
         *
         * @return 接続を続ける場合は {@code true}、閉じた場合は {@code false}
         */
        private boolean deliverOutcome(ChannelHandlerContext ctx, ProxySession.Outcome outcome) {
            for (MinecraftPacket toServer : outcome.toServer()) {
                writeToServer(ctx, toServer);
            }
            for (MinecraftPacket toClient : outcome.toClient()) {
                writeToClient(ctx, toClient);
            }
            if (outcome.relay()) {
                switchToRelay(ctx);
            }
            if (outcome.closeAfterSend()) {
                // 書き込みを流してから両方閉じる。
                Channel front = side == Side.CLIENT ? ctx.channel() : peer;
                Channel back = side == Side.CLIENT ? peer : ctx.channel();
                if (front != null) {
                    front.flush();
                }
                if (back != null) {
                    back.flush();
                }
                closeBoth(ctx);
                return false;
            }
            if (session.isClosed()) {
                closeBoth(ctx);
                return false;
            }
            return true;
        }

        /**
         * 【担当1】Encryption Response を受け取った直後の処理。
         * クライアントとの暗号化をパイプラインに入れ、hasJoined 照会を
         * 別スレッドへ投げる。照会の結果はイベントループ上で
         * {@link #completeAuthVerification(ChannelHandlerContext, Optional, String)}
         * へ返す（ProxySession は1スレッドからだけ触る約束を守るため）。
         */
        private void maybeStartAuthVerification(ChannelHandlerContext ctx) {
            if (authSubmitted || session == null || closed) {
                return;
            }
            ProxySession.PendingAuthVerification pending = session.pendingAuthVerification();
            if (pending == null) {
                return;
            }
            authSubmitted = true;
            byte[] sharedSecret = session.clientSharedSecret();
            if (sharedSecret == null) {
                LOG.warning("共有秘密が無いのに認証照会が始まりました。");
                completeAuthVerification(ctx, Optional.empty(),
                        "ログイン処理で内部エラーが発生しました。");
                return;
            }
            enableClientEncryption(ctx, sharedSecret);
            ExecutorService executor = proxy.authExecutor;
            if (executor == null) {
                completeAuthVerification(ctx, Optional.empty(),
                        "ログイン処理で内部エラーが発生しました。");
                return;
            }
            executor.execute(() -> {
                Optional<AuthenticatedProfile> profile;
                String failureReason = null;
                try {
                    profile = proxy.authConfig.verifier()
                            .verify(pending.username(), pending.serverIdHash());
                    if (profile.isEmpty()) {
                        failureReason = "Mojang認証に失敗しました。"
                                + "このアカウントでは参加できません。";
                    }
                } catch (Exception e) {
                    LOG.warning("セッションサーバーへの照会に失敗しました: " + e.getMessage());
                    profile = Optional.empty();
                    failureReason = "認証サーバーに接続できませんでした。"
                            + "しばらくしてからもう一度お試しください。";
                }
                Optional<AuthenticatedProfile> result = profile;
                String reason = failureReason;
                ctx.channel().eventLoop().execute(
                        () -> completeAuthVerification(ctx, result, reason));
            });
        }

        /** 【担当1】クライアントとの暗号化をパイプラインの先頭に入れる。 */
        private void enableClientEncryption(ChannelHandlerContext ctx, byte[] sharedSecret) {
            if (cipherEnabled || sharedSecret == null) {
                return;
            }
            if (decoder.bufferedBytes() > 0) {
                // Encryption Response の直後にクライアントが送るものは
                // 無いはず。断片が残っている場合は暗号化の切り替えで
                // 読めなくなるため、警告を残す（通常は起きない）。
                LOG.warning("暗号化の有効化時に未処理の断片が残っています（"
                        + decoder.bufferedBytes() + "バイト）。");
            }
            cipherEnabled = true;
            MinecraftCipher.install(ctx.pipeline(), sharedSecret);
        }

        /**
         * 【担当1】hasJoined 照会の結果をセッションへ返す（イベントループ上）。
         * 認証成功時は預かったハンドシェイクと認証済み Login Start が
         * バニラへ送られ、失敗時は理由付き Login Disconnect が送られる。
         * 照会の待ち受け中に溜めたクライアントのパケットもここで流す。
         */
        private void completeAuthVerification(ChannelHandlerContext ctx,
                Optional<AuthenticatedProfile> profile, String failureReason) {
            if (closed || session == null) {
                return;
            }
            ProxySession.Outcome outcome =
                    session.completeAuthentication(profile, failureReason);
            boolean keepGoing = deliverOutcome(ctx, outcome);
            if (!keepGoing) {
                return;
            }
            MinecraftPacket queued;
            while ((queued = queuedDuringAuth.poll()) != null) {
                if (!handlePacket(ctx, queued)) {
                    return;
                }
            }
        }

        /** リレーに切り替える。デコーダの断片は生バイトで先に送る。 */
        private void switchToRelay(ChannelHandlerContext ctx) {
            byte[] buffered = decoder.takeBuffered();
            if (buffered.length > 0) {
                forwardRawOwned(Unpooled.wrappedBuffer(buffered));
            }
            if (peerHandler != null) {
                byte[] peerBuffered = peerHandler.decoder.takeBuffered();
                if (peerBuffered.length > 0) {
                    peerHandler.forwardRawOwned(Unpooled.wrappedBuffer(peerBuffered));
                }
            }
        }

        /** 参照カウントを増やさず、そのまま相手へ送る（所有権を渡す）。 */
        private void forwardRawOwned(ByteBuf buf) {
            Channel target = peer;
            if (target != null && target.isActive()) {
                target.writeAndFlush(buf);
            } else {
                buf.release();
            }
        }

        private void forwardRaw(ByteBuf buf) {
            Channel target = peer;
            if (target != null && target.isActive()) {
                target.writeAndFlush(buf.retain());
            }
        }

        private void writeToServer(ChannelHandlerContext ctx, MinecraftPacket packet) {
            Channel target = side == Side.CLIENT ? peer : ctx.channel();
            if (target != null && target.isActive()) {
                target.writeAndFlush(Unpooled.wrappedBuffer(packet.encode()));
            }
        }

        private void writeToClient(ChannelHandlerContext ctx, MinecraftPacket packet) {
            Channel target = side == Side.CLIENT ? ctx.channel() : peer;
            if (target != null && target.isActive()) {
                target.writeAndFlush(Unpooled.wrappedBuffer(packet.encode()));
            }
        }

        private void closeBoth(ChannelHandlerContext ctx) {
            if (closed) {
                return;
            }
            closed = true;
            if (peerHandler != null) {
                peerHandler.closed = true;
            }
            Channel front = side == Side.CLIENT ? ctx.channel() : peer;
            Channel back = side == Side.CLIENT ? peer : ctx.channel();
            if (front != null) {
                front.close();
            }
            if (back != null) {
                back.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closeBoth(ctx);
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.warning("プロキシの接続で例外が発生しました: " + cause);
            closeBoth(ctx);
        }
    }
}
