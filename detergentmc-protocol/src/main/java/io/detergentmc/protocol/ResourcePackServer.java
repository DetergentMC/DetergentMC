package io.detergentmc.protocol;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Serves a generated resource-pack zip over plain HTTP on loopback, so
 * the vanilla server can point clients at it through
 * {@code server.properties} ({@code resource-pack} /
 * {@code resource-pack-sha1}).
 *
 * <p>The server binds explicitly to {@code 127.0.0.1}: IPv6 loopback is
 * unreliable in this environment, and the pack only ever needs to be
 * reachable from the same machine (the vanilla server and the players
 * connecting to it run here in the demo setup). Only the single path
 * {@code /detergentmc-pack.zip} is served; anything else is a 404.
 *
 * <p>Usage:
 * <pre>{@code
 * ResourcePackServer server = new ResourcePackServer(built.file(), built.sha1(), 25567, false);
 * ResourcePack pack = server.start();   // pack.url() is the download URL
 * ...
 * server.stop();
 * }</pre>
 *
 * <h2>1.20.1 variant</h2>
 *
 * <p>When a second pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_763} and
 * {@link #pack763()} returns the {@link ResourcePack} the proxy offers
 * to 763 clients (pack_format 15). The 765 pack stays the one returned
 * by {@link #start()} and written into {@code server.properties}.
 * Both packs share the same port and the same required/prompt settings.
 *
 * <h2>1.20.2 variant</h2>
 *
 * <p>When a third pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_764} and
 * {@link #pack764()} returns the {@link ResourcePack} the proxy offers
 * to 764 clients (pack_format 18). All three packs share the same port.
 *
 * <h2>1.20.5 / 1.20.6 variant</h2>
 *
 * <p>When a fourth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_766} and
 * {@link #pack766()} returns the {@link ResourcePack} the proxy offers
 * to 766 clients (pack_format 32). One pack serves both 1.20.5 and
 * 1.20.6: they share protocol 766 and the pack format. All four packs
 * share the same port.
 *
 * <h2>1.21 / 1.21.1 variant</h2>
 *
 * <p>When a fifth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_767} and
 * {@link #pack767()} returns the {@link ResourcePack} the proxy offers
 * to 767 clients (pack_format 34). One pack serves both 1.21 and
 * 1.21.1: they share protocol 767 and the pack format. All five packs
 * share the same port.
 *
 * <h2>1.21.2 / 1.21.3 variant</h2>
 *
 * <p>When a sixth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_768} and
 * {@link #pack768()} returns the {@link ResourcePack} the proxy offers
 * to 768 clients (pack_format 42). One pack serves both 1.21.2 and
 * 1.21.3: they share protocol 768 and the pack format. All six packs
 * share the same port.
 *
 * <h2>1.21.4 variant</h2>
 *
 * <p>When a seventh pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_769} and
 * {@link #pack769()} returns the {@link ResourcePack} the proxy offers
 * to 769 clients (pack_format 46). All seven packs share the same port.
 *
 * <h2>1.21.5 variant</h2>
 *
 * <p>When an eighth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_770} and
 * {@link #pack770()} returns the {@link ResourcePack} the proxy offers
 * to 770 clients (pack_format 55). All eight packs share the same port.
 *
 * <h2>1.21.6 variant</h2>
 *
 * <p>When a ninth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_771} and
 * {@link #pack771()} returns the {@link ResourcePack} the proxy offers
 * to 771 clients (pack_format 63). All nine packs share the same port.

 *
 * <h2>1.21.7／1.21.8 variant</h2>
 *
 * <p>When a tenth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_772} and
 * {@link #pack772()} returns the {@link ResourcePack} the proxy offers
 * to 772 clients (pack_format 64). All ten packs share the same port.

 *
 * <h2>1.21.9／1.21.10 variant</h2>
 *
 * <p>When an eleventh pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_773} and
 * {@link #pack773()} returns the {@link ResourcePack} the proxy offers
 * to 773 clients (pack_format 69). All eleven packs share the same port.
 *
 * <h2>1.21.11 variant</h2>
 *
 * <p>When a twelfth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_774} and
 * {@link #pack774()} returns the {@link ResourcePack} the proxy offers
 * to 774 clients (pack_format 75). All twelve packs share the same port.
 *
 * <h2>26.1 variant</h2>
 *
 * <p>When a thirteenth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_775} and
 * {@link #pack775()} returns the {@link ResourcePack} the proxy offers
 * to 775 clients (pack_format 84). All thirteen packs share the same port.
 *
 * <h2>26.2 variant</h2>
 *
 * <p>When a fourteenth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_776} and
 * {@link #pack776()} returns the {@link ResourcePack} the proxy offers
 * to 776 clients (pack_format 88). All fourteen packs share the same port.
 *
 * <h2>1.18.2 variant</h2>
 *
 * <p>When a 1.18.2 pack is given (see the 19-pack constructor),
 * the server also serves it at {@link #PACK_PATH_758} and
 * {@link #pack758()} returns the {@link ResourcePack} the proxy offers
 * to 758 clients (pack_format 8). All nineteen packs share the same port.
 */
public final class ResourcePackServer {

    private static final Logger LOG = Logger.getLogger(ResourcePackServer.class.getName());

    /** The only path this server answers. */
    public static final String PACK_PATH = "/detergentmc-pack.zip";

    /** Path of the 1.18.2 (pack_format 8) variant, when one is served. */
    /** Path of the 1.14.4 (pack_format 4) variant, when one is served. */
    /** Path of the 1.14.3 (pack_format 4) variant, when one is served. */
    /** Path of the 1.14.2 (pack_format 4) variant, when one is served. */
    /** Path of the 1.14.1 (pack_format 4) variant, when one is served. */
    /** Path of the 1.13.1 (pack_format 4) variant, when one is served. */
    public static final String PACK_PATH_401 = "/detergentmc-pack-1.13.1.zip";

    /** Path of the 1.13 (pack_format 4) variant, when one is served. */
    public static final String PACK_PATH_393 = "/detergentmc-pack-1.13.zip";

    /** Path of the 1.12.2 (pack_format 3) variant, when one is served. */
    public static final String PACK_PATH_340 = "/detergentmc-pack-1.12.2.zip";
    public static final String PACK_PATH_47 = "/detergentmc-pack-1.8.9.zip";
    public static final String PACK_PATH_5 = "/detergentmc-pack-1.7.10.zip";
    public static final String PACK_PATH_4 = "/detergentmc-pack-1.7.2.zip";
    public static final String PACK_PATH_107 = "/detergentmc-pack-1.9.zip";
    public static final String PACK_PATH_108 = "/detergentmc-pack-1.9.1.zip";
    public static final String PACK_PATH_109 = "/detergentmc-pack-1.9.2.zip";
    public static final String PACK_PATH_110 = "/detergentmc-pack-1.9.4.zip";
    public static final String PACK_PATH_210 = "/detergentmc-pack-1.10.2.zip";
    public static final String PACK_PATH_315 = "/detergentmc-pack-1.11.zip";
    public static final String PACK_PATH_316 = "/detergentmc-pack-1.11.2.zip";
    public static final String PACK_PATH_335 = "/detergentmc-pack-1.12.zip";
    public static final String PACK_PATH_338 = "/detergentmc-pack-1.12.1.zip";

    /** Path of the 1.13.2 (pack_format 4) variant, when one is served. */
    public static final String PACK_PATH_404 = "/detergentmc-pack-1.13.2.zip";

    /** Path of the 1.14 (pack_format 4) variant, when one is served. */
    public static final String PACK_PATH_477 = "/detergentmc-pack-1.14.zip";

    public static final String PACK_PATH_480 = "/detergentmc-pack-1.14.1.zip";

    public static final String PACK_PATH_485 = "/detergentmc-pack-1.14.2.zip";

    public static final String PACK_PATH_490 = "/detergentmc-pack-1.14.3.zip";

    public static final String PACK_PATH_498 = "/detergentmc-pack-1.14.4.zip";

    /** Path of the 1.15 (pack_format 5) variant, when one is served. */
    public static final String PACK_PATH_573 = "/detergentmc-pack-1.15.zip";

    /** Path of the 1.15.1 (pack_format 5) variant, when one is served. */
    public static final String PACK_PATH_575 = "/detergentmc-pack-1.15.1.zip";

    public static final String PACK_PATH_578 = "/detergentmc-pack-1.15.2.zip";
    public static final String PACK_PATH_758 = "/detergentmc-pack-1.18.2.zip";

    /** Path of the 1.19 (pack_format 9) variant, when one is served. */
    public static final String PACK_PATH_759 = "/detergentmc-pack-1.19.zip";

    /** Path of the 1.19.3 (pack_format 12) variant, when one is served. */
    /** Path of the 1.19.1 / 1.19.2 (pack_format 9) variant, when one is served. */
    public static final String PACK_PATH_760 = "/detergentmc-pack-1.19.1.zip";

    /** Path of the 1.19.3 (pack_format 12) variant, when one is served. */
    public static final String PACK_PATH_761 = "/detergentmc-pack-1.19.3.zip";

    /** Path of the 1.19.4 (pack_format 13) variant, when one is served. */
    public static final String PACK_PATH_762 = "/detergentmc-pack-1.19.4.zip";

    /** Path of the 1.20.1 (pack_format 15) variant, when one is served. */
    public static final String PACK_PATH_763 = "/detergentmc-pack-1.20.1.zip";

    /** Path of the 1.20.2 (pack_format 18) variant, when one is served. */
    public static final String PACK_PATH_764 = "/detergentmc-pack-1.20.2.zip";

    /**
     * Path of the 1.20.5 / 1.20.6 (pack_format 32) variant, when one is
     * served. One pack serves both versions: they share protocol 766.
     */
    public static final String PACK_PATH_766 = "/detergentmc-pack-1.20.5.zip";

    /**
     * Path of the 1.21 / 1.21.1 (pack_format 34) variant, when one is
     * served. One pack serves both versions: they share protocol 767.
     */
    public static final String PACK_PATH_767 = "/detergentmc-pack-1.21.zip";

    /**
     * Path of the 1.21.2 / 1.21.3 (pack_format 42) variant, when one is
     * served. One pack serves both versions: they share protocol 768.
     */
    public static final String PACK_PATH_768 = "/detergentmc-pack-1.21.2.zip";

    /** Path of the 1.21.4 (pack_format 46) variant, when one is served. */
    public static final String PACK_PATH_769 = "/detergentmc-pack-1.21.4.zip";

    /** Path of the 1.21.5 (pack_format 55) variant, when one is served. */
    public static final String PACK_PATH_770 = "/detergentmc-pack-1.21.5.zip";

    /** Path of the 1.21.6 (pack_format 63) variant, when one is served. */
    public static final String PACK_PATH_771 = "/detergentmc-pack-1.21.6.zip";

    /** Path of the 1.21.7 (pack_format 64) variant, when one is served. */
    public static final String PACK_PATH_772 = "/detergentmc-pack-1.21.7.zip";

    /** Path of the 1.21.9 (pack_format 69) variant, when one is served. */
    public static final String PACK_PATH_773 = "/detergentmc-pack-1.21.9.zip";

    /** Path of the 1.21.11 (pack_format 75) variant, when one is served. */
    public static final String PACK_PATH_774 = "/detergentmc-pack-1.21.11.zip";

    /** Path of the 26.1 (pack_format 84) variant, when one is served. */
    public static final String PACK_PATH_775 = "/detergentmc-pack-26.1.zip";

    /** Path of the 26.2 (pack_format 88) variant, when one is served. */
    public static final String PACK_PATH_776 = "/detergentmc-pack-26.2.zip";
    public static final String PACK_PATH_735 = "/detergentmc-pack-1.16.zip";
    public static final String PACK_PATH_736 = "/detergentmc-pack-1.16.1.zip";
    public static final String PACK_PATH_751 = "/detergentmc-pack-1.16.2.zip";
    public static final String PACK_PATH_753 = "/detergentmc-pack-1.16.3.zip";
    public static final String PACK_PATH_754 = "/detergentmc-pack-1.16.5.zip";
    public static final String PACK_PATH_755 = "/detergentmc-pack-1.17.zip";
    public static final String PACK_PATH_756 = "/detergentmc-pack-1.17.1.zip";
    public static final String PACK_PATH_757 = "/detergentmc-pack-1.18.zip";

    /** Translation key used for the client-side pack prompt. */
    public static final String DEFAULT_PROMPT_KEY = "resource-pack.prompt";

    private final Path packFile;
    private final String sha1;
    private final Path pack107File;
    private final String sha1107;
    private final Path pack47File;
    private final Path pack5File;
    private final Path pack4File;
    private final String sha147;
    private final String sha15;
    private final String sha14;
    private final Path pack108File;
    private final String sha1108;
    private final Path pack109File;
    private final String sha1109;
    private final Path pack110File;
    private final String sha1110;
    private final Path pack210File;
    private final String sha1210;
    private final Path pack315File;
    private final String sha1315;
    private final Path pack316File;
    private final String sha1316;
    private final Path pack335File;
    private final String sha1335;
    private final Path pack338File;
    private final String sha1338;
    private final Path pack340File;
    private final String sha1340;
    private final Path pack393File;
    private final String sha1393;
    private final Path pack401File;
    private final String sha1401;
    private final Path pack404File;
    private final String sha1404;
    private final Path pack477File;
    private final String sha1477;
    private final Path pack480File;
    private final String sha1480;
    private final Path pack485File;
    private final String sha1485;
    private final Path pack490File;
    private final String sha1490;
    private final Path pack498File;
    private final String sha1498;
    private final Path pack573File;
    private final String sha1573;
    private final Path pack575File;
    private final String sha1575;
    private final Path pack578File;
    private final String sha1578;
    private final Path pack758File;
    private final String sha1758;
    private final Path pack759File;
    private final String sha1759;
    private final Path pack760File;
    private final String sha1760;
    private final Path pack761File;
    private final String sha1761;
    private final Path pack762File;
    private final String sha1762;
    private final Path pack763File;
    private final String sha1763;
    private final Path pack764File;
    private final String sha1764;
    private final Path pack766File;
    private final String sha1766;
    private final Path pack767File;
    private final String sha1767;
    private final Path pack768File;
    private final String sha1768;
    private final Path pack769File;
    private final String sha1769;
    private final Path pack770File;
    private final String sha1770;
    private final Path pack771File;
    private final String sha1771;
    private final Path pack772File;
    private final String sha1772;
    private final Path pack773File;
    private final String sha1773;
    private final Path pack774File;
    private final String sha1774;
    private final Path pack775File;
    private final String sha1775;
    private final Path pack776File;
    private final String sha1776;
    private final int requestedPort;
    private final boolean required;
    private final String promptKey;

    private HttpServer httpServer;
    private ExecutorService executor;
    private byte[] packBytes;
    private byte[] pack107Bytes;
    private byte[] pack47Bytes;
    private byte[] pack5Bytes;
    private byte[] pack4Bytes;
    private byte[] pack108Bytes;
    private byte[] pack109Bytes;
    private byte[] pack110Bytes;
    private byte[] pack210Bytes;
    private byte[] pack315Bytes;
    private byte[] pack316Bytes;
    private byte[] pack335Bytes;
    private byte[] pack338Bytes;
    private byte[] pack340Bytes;
    private byte[] pack393Bytes;
    private byte[] pack401Bytes;
    private byte[] pack404Bytes;
    private byte[] pack477Bytes;
    private byte[] pack480Bytes;
    private byte[] pack485Bytes;
    private byte[] pack490Bytes;
    private byte[] pack498Bytes;
    private byte[] pack573Bytes;
    private byte[] pack575Bytes;
    private byte[] pack578Bytes;
    private byte[] pack758Bytes;
    private byte[] pack759Bytes;
    private byte[] pack760Bytes;
    private byte[] pack761Bytes;
    private byte[] pack762Bytes;
    private byte[] pack763Bytes;
    private byte[] pack764Bytes;
    private byte[] pack766Bytes;
    private byte[] pack767Bytes;
    private byte[] pack768Bytes;
    private byte[] pack769Bytes;
    private byte[] pack770Bytes;
    private byte[] pack771Bytes;
    private byte[] pack772Bytes;
    private byte[] pack773Bytes;
    private byte[] pack774Bytes;
    private byte[] pack775Bytes;
    private byte[] pack776Bytes;
    /** {@link #start()} が返した本体のパック（765用）。開始前は null。 */
    private ResourcePack mainPack;

    private final Path pack735File;

    private final String sha1735;

    private final Path pack736File;

    private final String sha1736;

    private final Path pack751File;

    private final String sha1751;

    private final Path pack753File;

    private final String sha1753;

    private final Path pack754File;

    private final String sha1754;

    private final Path pack755File;

    private final String sha1755;

    private final Path pack756File;

    private final String sha1756;

    private final Path pack757File;

    private final String sha1757;

    private byte[] pack735Bytes;

    private byte[] pack736Bytes;

    private byte[] pack751Bytes;

    private byte[] pack753Bytes;

    private byte[] pack754Bytes;

    private byte[] pack755Bytes;

    private byte[] pack756Bytes;

    private byte[] pack757Bytes;

    /**
     * @param packFile zip file to serve (must exist when {@link #start()}
     *                 is called)
     * @param sha1     lower-case hex SHA-1 of {@code packFile}
     * @param port     port to listen on, or {@code 0} for any free port
     * @param required whether clients must accept the pack to play
     */
    public ResourcePackServer(Path packFile, String sha1, int port, boolean required) {
        this(packFile, sha1, port, required, DEFAULT_PROMPT_KEY);
    }

    public ResourcePackServer(Path packFile, String sha1, int port,
            boolean required, String promptKey) {
        this(packFile, sha1, null, null, port, required, promptKey);
    }

    /**
     * @param packFile    zip file to serve (must exist when {@link #start()}
     *                    is called)
     * @param sha1        lower-case hex SHA-1 of {@code packFile}
     * @param pack763File 1.20.1 variant zip file, or {@code null} to serve
     *                    only the main pack
     * @param sha1763     lower-case hex SHA-1 of {@code pack763File}
     *                    (required when {@code pack763File} is given)
     * @param port        port to listen on, or {@code 0} for any free port
     * @param required    whether clients must accept the pack to play
     * @param promptKey   translation key for the client-side pack prompt
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, null, null, port, required, promptKey);
    }

    /**
     * @param pack764File 1.20.2 variant zip file, or {@code null} to serve
     *                    no 1.20.2 variant
     * @param sha1764     lower-case hex SHA-1 of {@code pack764File}
     *                    (required when {@code pack764File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                null, null, port, required, promptKey);
    }

    /**
     * @param pack766File 1.20.5 / 1.20.6 variant zip file, or {@code null}
     *                    to serve no 1.20.5 / 1.20.6 variant (one pack
     *                    serves both versions: protocol 766)
     * @param sha1766     lower-case hex SHA-1 of {@code pack766File}
     *                    (required when {@code pack766File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, null, null, port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.2 / 1.21.3 variant: no 768 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, null, null,
                port, required, promptKey);
    }

    /**
     * @param pack767File 1.21 / 1.21.1 variant zip file, or {@code null}
     *                    to serve no 1.21 / 1.21.1 variant (one pack
     *                    serves both versions: protocol 767)
     * @param sha1767     lower-case hex SHA-1 of {@code pack767File}
     *                    (required when {@code pack767File} is given)
     * @param pack768File 1.21.2 / 1.21.3 variant zip file, or {@code null}
     *                    to serve no 1.21.2 / 1.21.3 variant (one pack
     *                    serves both versions: protocol 768)
     * @param sha1768     lower-case hex SHA-1 of {@code pack768File}
     *                    (required when {@code pack768File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                null, null, port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.5 variant: no 770 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, null, null, null, null, null, null, null, null, port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.6 variant: no 771 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, null, null, null, null, null, null,
                port, required, promptKey);
    }
    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.7 variant: no 772 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.9 variant: no 773 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, null, null, null, null,
                port, required, promptKey);
    }


    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.11 variant: no 774 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, pack773File, sha1773, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 26.1 variant: no 775 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 26.2 variant: no 776 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774,
            Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, pack775File, sha1775, null, null,
                port, required, promptKey);
    }

    /**
     * @param pack769File 1.21.4 variant zip file, or {@code null}
     *                    to serve no 1.21.4 variant
     * @param sha1769     lower-case hex SHA-1 of {@code pack769File}
     *                    (required when {@code pack769File} is given)
     * @param pack770File 1.21.5 variant zip file, or {@code null}
     *                    to serve no 1.21.5 variant
     * @param sha1770     lower-case hex SHA-1 of {@code pack770File}
     *                    (required when {@code pack770File} is given)
     * @param pack771File 1.21.6 variant zip file, or {@code null}
     *                    to serve no 1.21.6 variant
     * @param sha1771     lower-case hex SHA-1 of {@code pack771File}
     *                    (required when {@code pack771File} is given)
     * @param pack772File 1.21.7 variant zip file, or {@code null}
     *                    to serve no 1.21.7 variant
     * @param sha1772     lower-case hex SHA-1 of {@code pack772File}
     *                    (required when {@code pack772File} is given)
     * @param pack773File 1.21.9 variant zip file, or {@code null}
     *                    to serve no 1.21.9 variant
     * @param sha1773     lower-case hex SHA-1 of {@code pack773File}
     *                    (required when {@code pack773File} is given)
     * @param pack774File 1.21.11 variant zip file, or {@code null}
     *                    to serve no 1.21.11 variant
     * @param sha1774     lower-case hex SHA-1 of {@code pack774File}
     *                    (required when {@code pack774File} is given)
     * @param pack775File 26.1 variant zip file, or {@code null}
     *                    to serve no 26.1 variant (one pack serves
     *                    26.1 / 26.1.1 / 26.1.2: protocol 775)
     * @param sha1775     lower-case hex SHA-1 of {@code pack775File}
     *                    (required when {@code pack775File} is given)
     * @param pack776File 26.2 variant zip file, or {@code null}
     *                    to serve no 26.2 variant (26.2 has no hotfix
     *                    releases: protocol 776)
     * @param sha1776     lower-case hex SHA-1 of {@code pack776File}
     *                    (required when {@code pack776File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = null;
        this.sha1760 = null;
        this.pack761File = null;
        this.sha1761 = null;
        this.pack762File = null;
        this.sha1762 = null;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.sha147 = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack735File = null;
        this.sha1735 = null;
        this.pack736File = null;
        this.sha1736 = null;
        this.pack751File = null;
        this.sha1751 = null;
        this.pack753File = null;
        this.sha1753 = null;
        this.pack754File = null;
        this.sha1754 = null;
        this.pack755File = null;
        this.sha1755 = null;
        this.pack756File = null;
        this.sha1756 = null;
        this.pack757File = null;
        this.sha1757 = null;
    }

    /**
     * Full constructor with a 1.19.4 variant (protocol 762,
     * pack_format 13), alongside the 1.20.1–26.2 variants.
     * The parameters follow the same order and rules as the
     * 12-pack constructor above; {@code pack762File} / {@code sha1762}
     * come first among the variants.
     *
     * @param pack762File 1.19.4 variant zip file, or {@code null}
     *                    to serve no 1.19.4 variant
     * @param sha1762     lower-case hex SHA-1 of {@code pack762File}
     *                    (required when {@code pack762File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = null;
        this.sha1760 = null;
        this.pack761File = null;
        this.sha1761 = null;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.sha147 = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack735File = null;
        this.sha1735 = null;
        this.pack736File = null;
        this.sha1736 = null;
        this.pack751File = null;
        this.sha1751 = null;
        this.pack753File = null;
        this.sha1753 = null;
        this.pack754File = null;
        this.sha1754 = null;
        this.pack755File = null;
        this.sha1755 = null;
        this.pack756File = null;
        this.sha1756 = null;
        this.pack757File = null;
        this.sha1757 = null;
    }

    /**
     * Full constructor with a 1.19 variant (protocol 759,
     * pack_format 9), alongside the 1.19.1–26.2 variants.
     * The parameters follow the same order and rules as the
     * 14-pack constructor above; {@code pack759File} / {@code sha1759}
     * come first among the variants.
     *
     * @param pack759File 1.19 variant zip file, or {@code null}
     *                    to serve no 1.19 variant
     * @param sha1759     lower-case hex SHA-1 of {@code pack759File}
     *                    (required when {@code pack759File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack759File, String sha1759,
            Path pack760File, String sha1760, Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack759File != null && (sha1759 == null || sha1759.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19 pack is required");
        }
        if (pack760File != null && (sha1760 == null || sha1760.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.1 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack759File = pack759File;
        this.sha1759 = sha1759;
        this.pack760File = pack760File;
        this.sha1760 = sha1760;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.sha147 = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack735File = null;
        this.sha1735 = null;
        this.pack736File = null;
        this.sha1736 = null;
        this.pack751File = null;
        this.sha1751 = null;
        this.pack753File = null;
        this.sha1753 = null;
        this.pack754File = null;
        this.sha1754 = null;
        this.pack755File = null;
        this.sha1755 = null;
        this.pack756File = null;
        this.sha1756 = null;
        this.pack757File = null;
        this.sha1757 = null;
    }

    /**
     * Full constructor with a 1.18.2 variant (protocol 758,
     * pack_format 8), alongside the 1.19–26.1 variants.
     * The parameters follow the same order and rules as the
     * 15-pack constructor above; {@code pack758File} / {@code sha1758}
     * come first among the variants.
     *
     * @param pack758File 1.18.2 variant zip file, or {@code null}
     *                    to serve no 1.18.2 variant
     * @param sha1758     lower-case hex SHA-1 of {@code pack758File}
     *                    (required when {@code pack758File} is given)
     */
    /**
     * Full constructor with a 1.15.2 variant (protocol 578,
     * pack_format 5), alongside the 1.18.2–26.2 variants.
     * The parameters follow the same order and rules as the
     * 19-pack constructor below; {@code pack578File} / {@code sha1578}
     * come first among the variants.
     *
     * @param pack578File 1.15.2 variant zip file, or {@code null}
     *                    to serve no 1.15.2 variant
     * @param sha1578     lower-case hex SHA-1 of {@code pack578File}
     *                    (required when {@code pack578File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack316File, String sha1316, Path pack210File, String sha1210, Path pack110File, String sha1110, Path pack109File, String sha1109, Path pack108File, String sha1108, Path pack107File, String sha1107, Path pack47File, String sha147, Path pack5File, String sha15, Path pack4File, String sha14, Path pack315File, String sha1315, Path pack335File, String sha1335, Path pack338File, String sha1338, Path pack340File, String sha1340, Path pack393File, String sha1393, Path pack401File, String sha1401, Path pack404File, String sha1404, Path pack477File, String sha1477, Path pack480File, String sha1480, Path pack485File, String sha1485, Path pack490File, String sha1490, Path pack498File, String sha1498, Path pack573File, String sha1573, Path pack575File, String sha1575, Path pack578File, String sha1578,
            Path pack758File, String sha1758,
            Path pack759File, String sha1759,
            Path pack760File, String sha1760, Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack316File != null && (sha1316 == null || sha1316.isBlank())) {
            throw new IllegalArgumentException("sha1316 is required when pack316File is given");
        }
        if (pack210File != null && (sha1210 == null || sha1210.isBlank())) {
            throw new IllegalArgumentException("sha1210 is required when pack210File is given");
        }
        if (pack110File != null && (sha1110 == null || sha1110.isBlank())) {
            throw new IllegalArgumentException("sha1110 is required when pack110File is given");
        }
        if (pack109File != null && (sha1109 == null || sha1109.isBlank())) {
            throw new IllegalArgumentException("sha1109 is required when pack109File is given");
        }
        if (pack108File != null && (sha1108 == null || sha1108.isBlank())) {
            throw new IllegalArgumentException("sha1108 is required when pack108File is given");
        }
        if (pack107File != null && (sha1107 == null || sha1107.isBlank())) {
            throw new IllegalArgumentException("sha1107 is required when pack107File is given");
        }
        if (pack47File != null && (sha147 == null || sha147.isBlank())) {
            throw new IllegalArgumentException("sha147 is required when pack47File is given");
        }
        if (pack5File != null && (sha15 == null || sha15.isBlank())) {
            throw new IllegalArgumentException("sha15 is required when pack5File is given");
        }
        if (pack4File != null && (sha14 == null || sha14.isBlank())) {
            throw new IllegalArgumentException("sha14 is required when pack4File is given");
        }
        if (pack315File != null && (sha1315 == null || sha1315.isBlank())) {
            throw new IllegalArgumentException("sha1315 is required when pack315File is given");
        }
        if (pack335File != null && (sha1335 == null || sha1335.isBlank())) {
            throw new IllegalArgumentException("sha1335 is required when pack335File is given");
        }
        if (pack338File != null && (sha1338 == null || sha1338.isBlank())) {
            throw new IllegalArgumentException("sha1338 is required when pack338File is given");
        }
        if (pack340File != null && (sha1340 == null || sha1340.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.12.2 pack is required");
        }
        if (pack393File != null && (sha1393 == null || sha1393.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13 pack is required");
        }
        if (pack401File != null && (sha1401 == null || sha1401.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13.1 pack is required");
        }
        if (pack404File != null && (sha1404 == null || sha1404.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13.2 pack is required");
        }
        if (pack477File != null && (sha1477 == null || sha1477.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14 pack is required");
        }
        if (pack480File != null && (sha1480 == null || sha1480.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.1 pack is required");
        }
        if (pack485File != null && (sha1485 == null || sha1485.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.2 pack is required");
        }
        if (pack490File != null && (sha1490 == null || sha1490.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.3 pack is required");
        }
        if (pack498File != null && (sha1498 == null || sha1498.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.4 pack is required");
        }
        if (pack573File != null && (sha1573 == null || sha1573.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.15 pack is required");
        }
        if (pack575File != null && (sha1575 == null || sha1575.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.15.1 pack is required");
        }
        if (pack578File != null && (sha1578 == null || sha1578.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.15.2 pack is required");
        }
        if (pack758File != null && (sha1758 == null || sha1758.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18.2 pack is required");
        }
        if (pack759File != null && (sha1759 == null || sha1759.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19 pack is required");
        }
        if (pack760File != null && (sha1760 == null || sha1760.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.1 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = pack758File;
        this.sha1758 = sha1758;
        this.pack759File = pack759File;
        this.sha1759 = sha1759;
        this.pack760File = pack760File;
        this.sha1760 = sha1760;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack316File = pack316File;
        this.sha1316 = sha1316;
        this.pack210File = pack210File;
        this.sha1210 = sha1210;
        this.pack110File = pack110File;
        this.sha1110 = sha1110;
        this.pack109File = pack109File;
        this.sha1109 = sha1109;
        this.pack108File = pack108File;
        this.sha1108 = sha1108;
        this.pack107File = pack107File;
        this.sha1107 = sha1107;
        this.pack47File = pack47File;
        this.sha147 = sha147;
        this.pack5File = pack5File;
        this.pack4File = pack4File;
        this.sha15 = sha15;
        this.sha14 = sha14;
        this.pack315File = pack315File;
        this.sha1315 = sha1315;
        this.pack335File = pack335File;
        this.sha1335 = sha1335;
        this.pack338File = pack338File;
        this.sha1338 = sha1338;
        this.pack340File = pack340File;
        this.sha1340 = sha1340;
        this.pack393File = pack393File;
        this.sha1393 = sha1393;
        this.pack401File = pack401File;
        this.sha1401 = sha1401;
        this.pack404File = pack404File;
        this.sha1404 = sha1404;
        this.pack477File = pack477File;
        this.sha1477 = sha1477;
        this.pack480File = pack480File;
        this.sha1480 = sha1480;
        this.pack485File = pack485File;
        this.sha1485 = sha1485;
        this.pack490File = pack490File;
        this.sha1490 = sha1490;
        this.pack498File = pack498File;
        this.sha1498 = sha1498;
        this.pack573File = pack573File;
        this.sha1573 = sha1573;
        this.pack575File = pack575File;
        this.sha1575 = sha1575;
        this.pack578File = pack578File;
        this.sha1578 = sha1578;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack735File = null;
        this.sha1735 = null;
        this.pack736File = null;
        this.sha1736 = null;
        this.pack751File = null;
        this.sha1751 = null;
        this.pack753File = null;
        this.sha1753 = null;
        this.pack754File = null;
        this.sha1754 = null;
        this.pack755File = null;
        this.sha1755 = null;
        this.pack756File = null;
        this.sha1756 = null;
        this.pack757File = null;
        this.sha1757 = null;
    }

    public ResourcePackServer(Path packFile, String sha1, Path pack758File, String sha1758,
            Path pack759File, String sha1759,
            Path pack760File, String sha1760, Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack758File != null && (sha1758 == null || sha1758.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18.2 pack is required");
        }
        if (pack759File != null && (sha1759 == null || sha1759.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19 pack is required");
        }
        if (pack760File != null && (sha1760 == null || sha1760.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.1 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = pack758File;
        this.sha1758 = sha1758;
        this.pack759File = pack759File;
        this.sha1759 = sha1759;
        this.pack760File = pack760File;
        this.sha1760 = sha1760;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.sha147 = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack735File = null;
        this.sha1735 = null;
        this.pack736File = null;
        this.sha1736 = null;
        this.pack751File = null;
        this.sha1751 = null;
        this.pack753File = null;
        this.sha1753 = null;
        this.pack754File = null;
        this.sha1754 = null;
        this.pack755File = null;
        this.sha1755 = null;
        this.pack756File = null;
        this.sha1756 = null;
        this.pack757File = null;
        this.sha1757 = null;
    }

    /**
     * Full constructor with a 1.19.1 / 1.19.2 variant (protocol 760,
     * pack_format 9), alongside the 1.19.3–26.2 variants.
     * The parameters follow the same order and rules as the
     * 14-pack constructor above; {@code pack760File} / {@code sha1760}
     * come first among the variants.
     *
     * @param pack760File 1.19.1 / 1.19.2 variant zip file, or {@code null}
     *                    to serve no 1.19.1 / 1.19.2 variant
     * @param sha1760     lower-case hex SHA-1 of {@code pack760File}
     *                    (required when {@code pack760File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack760File, String sha1760,
            Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack760File != null && (sha1760 == null || sha1760.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.1 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = pack760File;
        this.sha1760 = sha1760;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.sha147 = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack735File = null;
        this.sha1735 = null;
        this.pack736File = null;
        this.sha1736 = null;
        this.pack751File = null;
        this.sha1751 = null;
        this.pack753File = null;
        this.sha1753 = null;
        this.pack754File = null;
        this.sha1754 = null;
        this.pack755File = null;
        this.sha1755 = null;
        this.pack756File = null;
        this.sha1756 = null;
        this.pack757File = null;
        this.sha1757 = null;
    }

    /**
     * Full constructor with a 1.19.3 variant (protocol 761,
     * pack_format 12), alongside the 1.19.4–26.2 variants.
     * The parameters follow the same order and rules as the
     * 13-pack constructor above; {@code pack761File} / {@code sha1761}
     * come first among the variants.
     *
     * @param pack761File 1.19.3 variant zip file, or {@code null}
     *                    to serve no 1.19.3 variant
     * @param sha1761     lower-case hex SHA-1 of {@code pack761File}
     *                    (required when {@code pack761File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = null;
        this.sha1760 = null;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.sha147 = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack735File = null;
        this.sha1735 = null;
        this.pack736File = null;
        this.sha1736 = null;
        this.pack751File = null;
        this.sha1751 = null;
        this.pack753File = null;
        this.sha1753 = null;
        this.pack754File = null;
        this.sha1754 = null;
        this.pack755File = null;
        this.sha1755 = null;
        this.pack756File = null;
        this.sha1756 = null;
        this.pack757File = null;
        this.sha1757 = null;
    }

    /** Convenience: serve a pack built by {@link ResourcePackBuilder}. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built, int port, boolean required) {
        this(built.file(), built.sha1(), port, required);
    }

    /**
     * Convenience: serve both the 1.20.4 pack and its 1.20.1 variant,
     * built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, int port, boolean required) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve both packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, int port, boolean required,
            String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1 and 1.20.2
     * variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            int port, boolean required) {
        this(built, built763, built764, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all three packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2 and
     * 1.20.5 / 1.20.6 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, int port, boolean required) {
        this(built, built763, built764, built766, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all four packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, int port, boolean required,
            String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6 and 1.21 / 1.21.1 variants, built by
     * {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, port, required,
                DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all five packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1 and 1.21.2 / 1.21.3 variants, built by
     * {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, port, required,
                DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all six packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, int port, boolean required,
            String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3 and 1.21.4
     * variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769,
                port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all seven packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4 and
     * 1.21.5 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all eight packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                null, null, null, null, null, null,
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5
     * and 1.21.6 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                built771, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all nine packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), null, null, null, null,
                port, required, promptKey);
    }
    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6
     * and 1.21.7／1.21.8 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                built771, built772, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all ten packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                null, null,
                port, required, promptKey);
    }
    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8 and 1.21.9／1.21.10 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                built771, built772, built773, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all eleven packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), null, null,
                port, required, promptKey);
    }


    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                built771, built772, built773, built774, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all twelve packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.19.4, 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built762, ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required) {
        this(built, built762, built763, built764, built766, built767, built768, built769, built770,
                built771, built772, built773, built774, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all thirteen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built762, ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null, null, null,
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.19.3, 1.19.4, 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built761, ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required) {
        this(built, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all seventeen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                null, null,
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.19, 1.19.1 / 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11 and 26.1 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {
        this(built, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all eighteen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.19, 1.19.1 / 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11, 26.1 and 26.2 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required) {
        this(built, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, built776, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all nineteen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty-seven packs except the 1.13.2 one. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),         built761.file(), built761.sha1(), built762.file(), built762.sha1(),         built763.file(), built763.sha1(),         built764.file(), built764.sha1(), built766.file(), built766.sha1(),         built767.file(), built767.sha1(), built768.file(), built768.sha1(),         built769.file(), built769.sha1(), built770.file(), built770.sha1(),         built771.file(), built771.sha1(), built772.file(), built772.sha1(),         built773.file(), built773.sha1(), built774.file(), built774.sha1(),         built775.file(), built775.sha1(),         built776.file(), built776.sha1(),         port, required, promptKey);
    }

    /** Convenience: serve all twenty-seven packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(), built761.file(), built761.sha1(), built762.file(), built762.sha1(), built763.file(), built763.sha1(), built764.file(), built764.sha1(), built766.file(), built766.sha1(), built767.file(), built767.sha1(), built768.file(), built768.sha1(), built769.file(), built769.sha1(), built770.file(), built770.sha1(), built771.file(), built771.sha1(), built772.file(), built772.sha1(), built773.file(), built773.sha1(), built774.file(), built774.sha1(), built775.file(), built775.sha1(), built776.file(), built776.sha1(), port, required, promptKey);
    }


    /** Convenience: serve all thirty-five packs, with an explicit prompt key. */
    /** Convenience without a 1.11.2 pack (316 clients fall back to the 1.12 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(), built761.file(), built761.sha1(), built762.file(), built762.sha1(), built763.file(), built763.sha1(), built764.file(), built764.sha1(), built766.file(), built766.sha1(), built767.file(), built767.sha1(), built768.file(), built768.sha1(), built769.file(), built769.sha1(), built770.file(), built770.sha1(), built771.file(), built771.sha1(), built772.file(), built772.sha1(), built773.file(), built773.sha1(), built774.file(), built774.sha1(), built775.file(), built775.sha1(), built776.file(), built776.sha1(), port, required, promptKey);
    }


    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(), built761.file(), built761.sha1(), built762.file(), built762.sha1(), built763.file(), built763.sha1(), built764.file(), built764.sha1(), built766.file(), built766.sha1(), built767.file(), built767.sha1(), built768.file(), built768.sha1(), built769.file(), built769.sha1(), built770.file(), built770.sha1(), built771.file(), built771.sha1(), built772.file(), built772.sha1(), built773.file(), built773.sha1(), built774.file(), built774.sha1(), built775.file(), built775.sha1(), built776.file(), built776.sha1(), port, required, promptKey);
    }


    /** Convenience: serve all forty-one packs, with an explicit prompt key. */
    /** Convenience without a 1.8.9 pack (47 clients fall back to the 1.9 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built107,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), built109.file(), built109.sha1(), built108.file(), built108.sha1(), built107.file(), built107.sha1(), null, null, null, null, null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built107,
            ResourcePackBuilder.BuiltPack built47,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), built109.file(), built109.sha1(), built108.file(), built108.sha1(), built107.file(), built107.sha1(), built47.file(), built47.sha1(), null, null, null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all forty-two packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built107,
            ResourcePackBuilder.BuiltPack built47,
            ResourcePackBuilder.BuiltPack built5,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), built109.file(), built109.sha1(), built108.file(), built108.sha1(), built107.file(), built107.sha1(), built47.file(), built47.sha1(), built5.file(), built5.sha1(), null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built107,
            ResourcePackBuilder.BuiltPack built47,
            ResourcePackBuilder.BuiltPack built5,
            ResourcePackBuilder.BuiltPack built4,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), built109.file(), built109.sha1(), built108.file(), built108.sha1(), built107.file(), built107.sha1(), built47.file(), built47.sha1(), built5.file(), built5.sha1(), built4.file(), built4.sha1(), built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }


    /** Convenience without a 1.9 pack (107 clients fall back to the 1.9.1 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), built109.file(), built109.sha1(), built108.file(), built108.sha1(), null, null, null, null, null, null, null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }


    /** Convenience without a 1.9.1 pack (108 clients fall back to the 1.9.2 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), built109.file(), built109.sha1(), null, null, null, null, null, null, null, null, null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }


    /** Convenience without a 1.9.2 pack (109 clients fall back to the 1.9.4 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }


    /** Convenience without a 1.9.4 pack (110 clients fall back to the 1.10.2 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built210,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }


    /** Convenience without a 1.10.2 pack (210 clients fall back to the 1.11 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built315,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }


    /** Convenience without a 1.11 pack (315 clients fall back to the 1.11.2 pack). */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built316,
            ResourcePackBuilder.BuiltPack built335,
            ResourcePackBuilder.BuiltPack built338,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(), built761.file(), built761.sha1(), built762.file(), built762.sha1(), built763.file(), built763.sha1(), built764.file(), built764.sha1(), built766.file(), built766.sha1(), built767.file(), built767.sha1(), built768.file(), built768.sha1(), built769.file(), built769.sha1(), built770.file(), built770.sha1(), built771.file(), built771.sha1(), built772.file(), built772.sha1(), built773.file(), built773.sha1(), built774.file(), built774.sha1(), built775.file(), built775.sha1(), built776.file(), built776.sha1(), port, required, promptKey);
    }


    /** Convenience: serve all thirty-one packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built340,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(), built761.file(), built761.sha1(), built762.file(), built762.sha1(), built763.file(), built763.sha1(), built764.file(), built764.sha1(), built766.file(), built766.sha1(), built767.file(), built767.sha1(), built768.file(), built768.sha1(), built769.file(), built769.sha1(), built770.file(), built770.sha1(), built771.file(), built771.sha1(), built772.file(), built772.sha1(), built773.file(), built773.sha1(), built774.file(), built774.sha1(), built775.file(), built775.sha1(), built776.file(), built776.sha1(), port, required, promptKey);
    }

    /** Convenience: serve all thirty packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }
    /** Convenience: serve all twenty-six packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty-five packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty-four packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty-three packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty-two packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty-one packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                built776.file(), built776.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty packs. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required) {
        this(built, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, built776, port, required, DEFAULT_PROMPT_KEY);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.18.2, 1.19, 1.19.1 / 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11, 26.1 and 26.2 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            ResourcePackBuilder.BuiltPack built776,
            int port, boolean required) {
        this(built, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, built776, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all fifteen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null, null, null,
                port, required, promptKey);
    }

    /**
     * Convenience: serve the 1.20.4 pack and its 1.19.1 / 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required) {
        this(built, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all fourteen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built761, ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built761.file(), built761.sha1(),
                built762.file(), built762.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null, null, null,
                port, required, promptKey);
    }
    /**
     * @param pack755File 1.17 variant zip file, or {@code null}
     *                    to serve no 1.17 variant
     * @param sha1755     lower-case hex SHA-1 of {@code pack755File}
     *                    (required when {@code pack755File} is given)
     * @param pack756File 1.17.1 variant zip file, or {@code null}
     *                    to serve no 1.17.1 variant
     * @param sha1756     lower-case hex SHA-1 of {@code pack756File}
     *                    (required when {@code pack756File} is given)
     * @param pack757File 1.18 / 1.18.1 variant zip file, or {@code null}
     *                    to serve no 1.18 / 1.18.1 variant (one pack serves
     *                    1.18 / 1.18.1: protocol 757)
     * @param sha1757     lower-case hex SHA-1 of {@code pack757File}
     *                    (required when {@code pack757File} is given)
     * @param pack769File 1.21.4 variant zip file, or {@code null}
     *                    to serve no 1.21.4 variant
     * @param sha1769     lower-case hex SHA-1 of {@code pack769File}
     *                    (required when {@code pack769File} is given)
     * @param pack770File 1.21.5 variant zip file, or {@code null}
     *                    to serve no 1.21.5 variant
     * @param sha1770     lower-case hex SHA-1 of {@code pack770File}
     *                    (required when {@code pack770File} is given)
     * @param pack771File 1.21.6 variant zip file, or {@code null}
     *                    to serve no 1.21.6 variant
     * @param sha1771     lower-case hex SHA-1 of {@code pack771File}
     *                    (required when {@code pack771File} is given)
     * @param pack772File 1.21.7 variant zip file, or {@code null}
     *                    to serve no 1.21.7 variant
     * @param sha1772     lower-case hex SHA-1 of {@code pack772File}
     *                    (required when {@code pack772File} is given)
     * @param pack773File 1.21.9 variant zip file, or {@code null}
     *                    to serve no 1.21.9 variant
     * @param sha1773     lower-case hex SHA-1 of {@code pack773File}
     *                    (required when {@code pack773File} is given)
     * @param pack774File 1.21.11 variant zip file, or {@code null}
     *                    to serve no 1.21.11 variant
     * @param sha1774     lower-case hex SHA-1 of {@code pack774File}
     *                    (required when {@code pack774File} is given)
     * @param pack775File 26.1 variant zip file, or {@code null}
     *                    to serve no 26.1 variant (one pack serves
     *                    26.1 / 26.1.1 / 26.1.2: protocol 775)
     * @param sha1775     lower-case hex SHA-1 of {@code pack775File}
     *                    (required when {@code pack775File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack735File, String sha1735, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack735File != null && (sha1735 == null || sha1735.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16 pack is required");
        }
        if (pack736File != null && (sha1736 == null || sha1736.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.1 pack is required");
        }
        if (pack751File != null && (sha1751 == null || sha1751.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.2 pack is required");
        }
        if (pack753File != null && (sha1753 == null || sha1753.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.3 pack is required");
        }
        if (pack754File != null && (sha1754 == null || sha1754.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.5 pack is required");
        }
        if (pack755File != null && (sha1755 == null || sha1755.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17 pack is required");
        }
        if (pack756File != null && (sha1756 == null || sha1756.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17.1 pack is required");
        }
        if (pack757File != null && (sha1757 == null || sha1757.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack735File = pack735File;
        this.sha1735 = sha1735;
        this.pack736File = pack736File;
        this.sha1736 = sha1736;
        this.pack751File = pack751File;
        this.sha1751 = sha1751;
        this.pack753File = pack753File;
        this.sha1753 = sha1753;
        this.pack754File = pack754File;
        this.sha1754 = sha1754;
        this.pack755File = pack755File;
        this.sha1755 = sha1755;
        this.pack756File = pack756File;
        this.sha1756 = sha1756;
        this.pack757File = pack757File;
        this.sha1757 = sha1757;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = null;
        this.sha1760 = null;
        this.pack761File = null;
        this.sha1761 = null;
        this.pack762File = null;
        this.sha1762 = null;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha147 = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack776File = null;
        this.sha1776 = null;
    }

    /**
     * Full constructor with a 1.19.4 variant (protocol 762,
     * pack_format 13), alongside the 1.18 variant and the 1.20.1–26.1 variants.
     * The parameters follow the same order and rules as the
     * 13-pack constructor above; {@code pack762File} / {@code sha1762}
     * come first among the variants.
     *
     * @param pack755File 1.17 variant zip file, or {@code null}
     *                    to serve no 1.17 variant
     * @param sha1755     lower-case hex SHA-1 of {@code pack755File}
     *                    (required when {@code pack755File} is given)
     * @param pack756File 1.17.1 variant zip file, or {@code null}
     *                    to serve no 1.17.1 variant
     * @param sha1756     lower-case hex SHA-1 of {@code pack756File}
     *                    (required when {@code pack756File} is given)
     * @param pack757File 1.18 / 1.18.1 variant zip file, or {@code null}
     *                    to serve no 1.18 / 1.18.1 variant (one pack serves
     *                    1.18 / 1.18.1: protocol 757)
     * @param sha1757     lower-case hex SHA-1 of {@code pack757File}
     *                    (required when {@code pack757File} is given)
     * @param pack762File 1.19.4 variant zip file, or {@code null}
     *                    to serve no 1.19.4 variant
     * @param sha1762     lower-case hex SHA-1 of {@code pack762File}
     *                    (required when {@code pack762File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack735File, String sha1735, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack735File != null && (sha1735 == null || sha1735.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16 pack is required");
        }
        if (pack736File != null && (sha1736 == null || sha1736.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.1 pack is required");
        }
        if (pack751File != null && (sha1751 == null || sha1751.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.2 pack is required");
        }
        if (pack753File != null && (sha1753 == null || sha1753.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.3 pack is required");
        }
        if (pack754File != null && (sha1754 == null || sha1754.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.5 pack is required");
        }
        if (pack755File != null && (sha1755 == null || sha1755.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17 pack is required");
        }
        if (pack756File != null && (sha1756 == null || sha1756.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17.1 pack is required");
        }
        if (pack757File != null && (sha1757 == null || sha1757.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack735File = pack735File;
        this.sha1735 = sha1735;
        this.pack736File = pack736File;
        this.sha1736 = sha1736;
        this.pack751File = pack751File;
        this.sha1751 = sha1751;
        this.pack753File = pack753File;
        this.sha1753 = sha1753;
        this.pack754File = pack754File;
        this.sha1754 = sha1754;
        this.pack755File = pack755File;
        this.sha1755 = sha1755;
        this.pack756File = pack756File;
        this.sha1756 = sha1756;
        this.pack757File = pack757File;
        this.sha1757 = sha1757;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = null;
        this.sha1760 = null;
        this.pack761File = null;
        this.sha1761 = null;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha147 = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack776File = null;
        this.sha1776 = null;
    }

    /**
     * Full constructor with a 1.19 variant (protocol 759,
     * pack_format 9), alongside the 1.18 variant and the 1.19.1–26.1 variants.
     * The parameters follow the same order and rules as the
     * 15-pack constructor above; {@code pack759File} / {@code sha1759}
     * come first among the variants.
     *
     * @param pack755File 1.17 variant zip file, or {@code null}
     *                    to serve no 1.17 variant
     * @param sha1755     lower-case hex SHA-1 of {@code pack755File}
     *                    (required when {@code pack755File} is given)
     * @param pack756File 1.17.1 variant zip file, or {@code null}
     *                    to serve no 1.17.1 variant
     * @param sha1756     lower-case hex SHA-1 of {@code pack756File}
     *                    (required when {@code pack756File} is given)
     * @param pack757File 1.18 / 1.18.1 variant zip file, or {@code null}
     *                    to serve no 1.18 / 1.18.1 variant (one pack serves
     *                    1.18 / 1.18.1: protocol 757)
     * @param sha1757     lower-case hex SHA-1 of {@code pack757File}
     *                    (required when {@code pack757File} is given)
     * @param pack759File 1.19 variant zip file, or {@code null}
     *                    to serve no 1.19 variant
     * @param sha1759     lower-case hex SHA-1 of {@code pack759File}
     *                    (required when {@code pack759File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack735File, String sha1735, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack759File, String sha1759,
            Path pack760File, String sha1760, Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack735File != null && (sha1735 == null || sha1735.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16 pack is required");
        }
        if (pack736File != null && (sha1736 == null || sha1736.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.1 pack is required");
        }
        if (pack751File != null && (sha1751 == null || sha1751.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.2 pack is required");
        }
        if (pack753File != null && (sha1753 == null || sha1753.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.3 pack is required");
        }
        if (pack754File != null && (sha1754 == null || sha1754.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.5 pack is required");
        }
        if (pack755File != null && (sha1755 == null || sha1755.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17 pack is required");
        }
        if (pack756File != null && (sha1756 == null || sha1756.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17.1 pack is required");
        }
        if (pack757File != null && (sha1757 == null || sha1757.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18 pack is required");
        }
        if (pack759File != null && (sha1759 == null || sha1759.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19 pack is required");
        }
        if (pack760File != null && (sha1760 == null || sha1760.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.1 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack735File = pack735File;
        this.sha1735 = sha1735;
        this.pack736File = pack736File;
        this.sha1736 = sha1736;
        this.pack751File = pack751File;
        this.sha1751 = sha1751;
        this.pack753File = pack753File;
        this.sha1753 = sha1753;
        this.pack754File = pack754File;
        this.sha1754 = sha1754;
        this.pack755File = pack755File;
        this.sha1755 = sha1755;
        this.pack756File = pack756File;
        this.sha1756 = sha1756;
        this.pack757File = pack757File;
        this.sha1757 = sha1757;
        this.pack759File = pack759File;
        this.sha1759 = sha1759;
        this.pack760File = pack760File;
        this.sha1760 = sha1760;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha147 = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack776File = null;
        this.sha1776 = null;
    }

    /**
     * Full constructor with a 1.19.1 / 1.19.2 variant (protocol 760,
     * pack_format 9), alongside the 1.18 variant and the 1.19.3–26.1 variants.
     * The parameters follow the same order and rules as the
     * 15-pack constructor above; {@code pack760File} / {@code sha1760}
     * come first among the variants.
     *
     * @param pack755File 1.17 variant zip file, or {@code null}
     *                    to serve no 1.17 variant
     * @param sha1755     lower-case hex SHA-1 of {@code pack755File}
     *                    (required when {@code pack755File} is given)
     * @param pack756File 1.17.1 variant zip file, or {@code null}
     *                    to serve no 1.17.1 variant
     * @param sha1756     lower-case hex SHA-1 of {@code pack756File}
     *                    (required when {@code pack756File} is given)
     * @param pack757File 1.18 / 1.18.1 variant zip file, or {@code null}
     *                    to serve no 1.18 / 1.18.1 variant (one pack serves
     *                    1.18 / 1.18.1: protocol 757)
     * @param sha1757     lower-case hex SHA-1 of {@code pack757File}
     *                    (required when {@code pack757File} is given)
     * @param pack760File 1.19.1 / 1.19.2 variant zip file, or {@code null}
     *                    to serve no 1.19.1 / 1.19.2 variant
     * @param sha1760     lower-case hex SHA-1 of {@code pack760File}
     *                    (required when {@code pack760File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack735File, String sha1735, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack760File, String sha1760,
            Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack735File != null && (sha1735 == null || sha1735.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16 pack is required");
        }
        if (pack736File != null && (sha1736 == null || sha1736.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.1 pack is required");
        }
        if (pack751File != null && (sha1751 == null || sha1751.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.2 pack is required");
        }
        if (pack753File != null && (sha1753 == null || sha1753.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.3 pack is required");
        }
        if (pack754File != null && (sha1754 == null || sha1754.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.5 pack is required");
        }
        if (pack755File != null && (sha1755 == null || sha1755.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17 pack is required");
        }
        if (pack756File != null && (sha1756 == null || sha1756.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17.1 pack is required");
        }
        if (pack757File != null && (sha1757 == null || sha1757.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18 pack is required");
        }
        if (pack760File != null && (sha1760 == null || sha1760.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.1 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack735File = pack735File;
        this.sha1735 = sha1735;
        this.pack736File = pack736File;
        this.sha1736 = sha1736;
        this.pack751File = pack751File;
        this.sha1751 = sha1751;
        this.pack753File = pack753File;
        this.sha1753 = sha1753;
        this.pack754File = pack754File;
        this.sha1754 = sha1754;
        this.pack755File = pack755File;
        this.sha1755 = sha1755;
        this.pack756File = pack756File;
        this.sha1756 = sha1756;
        this.pack757File = pack757File;
        this.sha1757 = sha1757;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = pack760File;
        this.sha1760 = sha1760;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha147 = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack776File = null;
        this.sha1776 = null;
    }

    /**
     * Full constructor with a 1.19.3 variant (protocol 761,
     * pack_format 12), alongside the 1.18 variant and the 1.19.4–26.1 variants.
     * The parameters follow the same order and rules as the
     * 14-pack constructor above; {@code pack761File} / {@code sha1761}
     * come first among the variants.
     *
     * @param pack755File 1.17 variant zip file, or {@code null}
     *                    to serve no 1.17 variant
     * @param sha1755     lower-case hex SHA-1 of {@code pack755File}
     *                    (required when {@code pack755File} is given)
     * @param pack756File 1.17.1 variant zip file, or {@code null}
     *                    to serve no 1.17.1 variant
     * @param sha1756     lower-case hex SHA-1 of {@code pack756File}
     *                    (required when {@code pack756File} is given)
     * @param pack757File 1.18 / 1.18.1 variant zip file, or {@code null}
     *                    to serve no 1.18 / 1.18.1 variant (one pack serves
     *                    1.18 / 1.18.1: protocol 757)
     * @param sha1757     lower-case hex SHA-1 of {@code pack757File}
     *                    (required when {@code pack757File} is given)
     * @param pack761File 1.19.3 variant zip file, or {@code null}
     *                    to serve no 1.19.3 variant
     * @param sha1761     lower-case hex SHA-1 of {@code pack761File}
     *                    (required when {@code pack761File} is given)
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack735File, String sha1735, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack735File != null && (sha1735 == null || sha1735.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16 pack is required");
        }
        if (pack736File != null && (sha1736 == null || sha1736.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.1 pack is required");
        }
        if (pack751File != null && (sha1751 == null || sha1751.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.2 pack is required");
        }
        if (pack753File != null && (sha1753 == null || sha1753.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.3 pack is required");
        }
        if (pack754File != null && (sha1754 == null || sha1754.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.5 pack is required");
        }
        if (pack755File != null && (sha1755 == null || sha1755.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17 pack is required");
        }
        if (pack756File != null && (sha1756 == null || sha1756.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17.1 pack is required");
        }
        if (pack757File != null && (sha1757 == null || sha1757.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack735File = pack735File;
        this.sha1735 = sha1735;
        this.pack736File = pack736File;
        this.sha1736 = sha1736;
        this.pack751File = pack751File;
        this.sha1751 = sha1751;
        this.pack753File = pack753File;
        this.sha1753 = sha1753;
        this.pack754File = pack754File;
        this.sha1754 = sha1754;
        this.pack755File = pack755File;
        this.sha1755 = sha1755;
        this.pack756File = pack756File;
        this.sha1756 = sha1756;
        this.pack757File = pack757File;
        this.sha1757 = sha1757;
        this.pack759File = null;
        this.sha1759 = null;
        this.pack760File = null;
        this.sha1760 = null;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
        this.pack107File = null;
        this.sha1107 = null;
        this.pack47File = null;
        this.pack5File = null;
        this.pack4File = null;
        this.sha147 = null;
        this.sha15 = null;
        this.sha14 = null;
        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;
        this.pack210File = null;
        this.sha1210 = null;
        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;
        this.pack340File = null;
        this.sha1340 = null;
        this.pack393File = null;
        this.sha1393 = null;
        this.pack401File = null;
        this.sha1401 = null;
        this.pack404File = null;
        this.sha1404 = null;
        this.pack477File = null;
        this.sha1477 = null;
        this.pack480File = null;
        this.sha1480 = null;
        this.pack485File = null;
        this.sha1485 = null;
        this.pack490File = null;
        this.sha1490 = null;
        this.pack498File = null;
        this.sha1498 = null;
        this.pack573File = null;
        this.sha1573 = null;
        this.pack575File = null;
        this.sha1575 = null;
        this.pack578File = null;
        this.sha1578 = null;
        this.pack758File = null;
        this.sha1758 = null;
        this.pack776File = null;
        this.sha1776 = null;
    }

    /** Convenience: serve all twenty-one packs. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built754, ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {
        this(built, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all twenty-two packs, built by {@link ResourcePackBuilder}. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built753, ResourcePackBuilder.BuiltPack built754, ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {
        this(built, built753, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all twenty-three packs, built by {@link ResourcePackBuilder}. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built751, ResourcePackBuilder.BuiltPack built753, ResourcePackBuilder.BuiltPack built754, ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {
        this(built, built751, built753, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all twenty-four packs, built by {@link ResourcePackBuilder}. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built736, ResourcePackBuilder.BuiltPack built751, ResourcePackBuilder.BuiltPack built753, ResourcePackBuilder.BuiltPack built754, ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {
        this(built, built736, built751, built753, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all twenty-five packs, built by {@link ResourcePackBuilder}. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built735, ResourcePackBuilder.BuiltPack built736, ResourcePackBuilder.BuiltPack built751, ResourcePackBuilder.BuiltPack built753, ResourcePackBuilder.BuiltPack built754, ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
            ResourcePackBuilder.BuiltPack built762,
            ResourcePackBuilder.BuiltPack built763,
            ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            ResourcePackBuilder.BuiltPack built774,
            ResourcePackBuilder.BuiltPack built775,
            int port, boolean required) {
        this(built, built735, built736, built751, built753, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    public ResourcePackServer(Path packFile, String sha1, Path pack316File, String sha1316, Path pack210File, String sha1210, Path pack110File, String sha1110, Path pack109File, String sha1109, Path pack108File, String sha1108, Path pack107File, String sha1107, Path pack47File, String sha147, Path pack5File, String sha15, Path pack4File, String sha14, Path pack315File, String sha1315, Path pack335File, String sha1335, Path pack338File, String sha1338, Path pack340File, String sha1340, Path pack393File, String sha1393, Path pack401File, String sha1401, Path pack404File, String sha1404, Path pack477File, String sha1477, Path pack480File, String sha1480, Path pack485File, String sha1485, Path pack490File, String sha1490, Path pack498File, String sha1498, Path pack573File, String sha1573, Path pack575File, String sha1575, Path pack578File, String sha1578,
            Path pack758File, String sha1758,
            Path pack759File, String sha1759,
            Path pack760File, String sha1760, Path pack761File, String sha1761,
            Path pack762File, String sha1762,
            Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            Path pack773File, String sha1773, Path pack774File, String sha1774, Path pack775File, String sha1775, Path pack776File, String sha1776,
            Path pack735File, String sha1735, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757,
            int port, boolean required, String promptKey) {
        if (packFile == null) {
            throw new IllegalArgumentException("packFile is required");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 is required");
        }
        if (pack735File != null && (sha1735 == null || sha1735.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16 pack is required");
        }
        if (pack736File != null && (sha1736 == null || sha1736.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.1 pack is required");
        }
        if (pack751File != null && (sha1751 == null || sha1751.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.2 pack is required");
        }
        if (pack753File != null && (sha1753 == null || sha1753.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.3 pack is required");
        }
        if (pack754File != null && (sha1754 == null || sha1754.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.16.5 pack is required");
        }
        if (pack755File != null && (sha1755 == null || sha1755.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17 pack is required");
        }
        if (pack756File != null && (sha1756 == null || sha1756.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.17.1 pack is required");
        }
        if (pack757File != null && (sha1757 == null || sha1757.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18 pack is required");
        }
        if (pack316File != null && (sha1316 == null || sha1316.isBlank())) {
            throw new IllegalArgumentException("sha1316 is required when pack316File is given");
        }
        if (pack210File != null && (sha1210 == null || sha1210.isBlank())) {
            throw new IllegalArgumentException("sha1210 is required when pack210File is given");
        }
        if (pack110File != null && (sha1110 == null || sha1110.isBlank())) {
            throw new IllegalArgumentException("sha1110 is required when pack110File is given");
        }
        if (pack109File != null && (sha1109 == null || sha1109.isBlank())) {
            throw new IllegalArgumentException("sha1109 is required when pack109File is given");
        }
        if (pack108File != null && (sha1108 == null || sha1108.isBlank())) {
            throw new IllegalArgumentException("sha1108 is required when pack108File is given");
        }
        if (pack107File != null && (sha1107 == null || sha1107.isBlank())) {
            throw new IllegalArgumentException("sha1107 is required when pack107File is given");
        }
        if (pack47File != null && (sha147 == null || sha147.isBlank())) {
            throw new IllegalArgumentException("sha147 is required when pack47File is given");
        }
        if (pack5File != null && (sha15 == null || sha15.isBlank())) {
            throw new IllegalArgumentException("sha15 is required when pack5File is given");
        }
        if (pack4File != null && (sha14 == null || sha14.isBlank())) {
            throw new IllegalArgumentException("sha14 is required when pack4File is given");
        }
        if (pack315File != null && (sha1315 == null || sha1315.isBlank())) {
            throw new IllegalArgumentException("sha1315 is required when pack315File is given");
        }
        if (pack335File != null && (sha1335 == null || sha1335.isBlank())) {
            throw new IllegalArgumentException("sha1335 is required when pack335File is given");
        }
        if (pack338File != null && (sha1338 == null || sha1338.isBlank())) {
            throw new IllegalArgumentException("sha1338 is required when pack338File is given");
        }
        if (pack340File != null && (sha1340 == null || sha1340.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.12.2 pack is required");
        }
        if (pack393File != null && (sha1393 == null || sha1393.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13 pack is required");
        }
        if (pack401File != null && (sha1401 == null || sha1401.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13.1 pack is required");
        }
        if (pack404File != null && (sha1404 == null || sha1404.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.13.2 pack is required");
        }
        if (pack477File != null && (sha1477 == null || sha1477.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14 pack is required");
        }
        if (pack480File != null && (sha1480 == null || sha1480.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.1 pack is required");
        }
        if (pack485File != null && (sha1485 == null || sha1485.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.2 pack is required");
        }
        if (pack490File != null && (sha1490 == null || sha1490.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.3 pack is required");
        }
        if (pack498File != null && (sha1498 == null || sha1498.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.14.4 pack is required");
        }
        if (pack573File != null && (sha1573 == null || sha1573.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.15 pack is required");
        }
        if (pack575File != null && (sha1575 == null || sha1575.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.15.1 pack is required");
        }
        if (pack578File != null && (sha1578 == null || sha1578.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.15.2 pack is required");
        }
        if (pack758File != null && (sha1758 == null || sha1758.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.18.2 pack is required");
        }
        if (pack759File != null && (sha1759 == null || sha1759.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19 pack is required");
        }
        if (pack760File != null && (sha1760 == null || sha1760.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.1 pack is required");
        }
        if (pack761File != null && (sha1761 == null || sha1761.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.3 pack is required");
        }
        if (pack762File != null && (sha1762 == null || sha1762.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.19.4 pack is required");
        }
        if (pack763File != null && (sha1763 == null || sha1763.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.1 pack is required");
        }
        if (pack764File != null && (sha1764 == null || sha1764.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.2 pack is required");
        }
        if (pack766File != null && (sha1766 == null || sha1766.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.20.5 pack is required");
        }
        if (pack767File != null && (sha1767 == null || sha1767.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21 pack is required");
        }
        if (pack768File != null && (sha1768 == null || sha1768.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.2 pack is required");
        }
        if (pack769File != null && (sha1769 == null || sha1769.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.4 pack is required");
        }
        if (pack770File != null && (sha1770 == null || sha1770.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.5 pack is required");
        }
        if (pack771File != null && (sha1771 == null || sha1771.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.6 pack is required");
        }
        if (pack772File != null && (sha1772 == null || sha1772.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.7 pack is required");
        }
        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.9 pack is required");
        }
        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 1.21.11 pack is required");
        }
        if (pack775File != null && (sha1775 == null || sha1775.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.1 pack is required");
        }
        if (pack776File != null && (sha1776 == null || sha1776.isBlank())) {
            throw new IllegalArgumentException("sha1 for the 26.2 pack is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.packFile = packFile;
        this.sha1 = sha1;
        this.pack758File = pack758File;
        this.sha1758 = sha1758;
        this.pack759File = pack759File;
        this.sha1759 = sha1759;
        this.pack760File = pack760File;
        this.sha1760 = sha1760;
        this.pack761File = pack761File;
        this.sha1761 = sha1761;
        this.pack762File = pack762File;
        this.sha1762 = sha1762;
        this.pack763File = pack763File;
        this.sha1763 = sha1763;
        this.pack764File = pack764File;
        this.sha1764 = sha1764;
        this.pack766File = pack766File;
        this.sha1766 = sha1766;
        this.pack767File = pack767File;
        this.sha1767 = sha1767;
        this.pack768File = pack768File;
        this.sha1768 = sha1768;
        this.pack769File = pack769File;
        this.sha1769 = sha1769;
        this.pack770File = pack770File;
        this.sha1770 = sha1770;
        this.pack771File = pack771File;
        this.sha1771 = sha1771;
        this.pack772File = pack772File;
        this.sha1772 = sha1772;
        this.pack773File = pack773File;
        this.sha1773 = sha1773;
        this.pack774File = pack774File;
        this.sha1774 = sha1774;
        this.pack775File = pack775File;
        this.sha1775 = sha1775;
        this.pack316File = pack316File;
        this.sha1316 = sha1316;
        this.pack210File = pack210File;
        this.sha1210 = sha1210;
        this.pack110File = pack110File;
        this.sha1110 = sha1110;
        this.pack109File = pack109File;
        this.sha1109 = sha1109;
        this.pack108File = pack108File;
        this.sha1108 = sha1108;
        this.pack107File = pack107File;
        this.sha1107 = sha1107;
        this.pack47File = pack47File;
        this.sha147 = sha147;
        this.pack5File = pack5File;
        this.pack4File = pack4File;
        this.sha15 = sha15;
        this.sha14 = sha14;
        this.pack315File = pack315File;
        this.sha1315 = sha1315;
        this.pack335File = pack335File;
        this.sha1335 = sha1335;
        this.pack338File = pack338File;
        this.sha1338 = sha1338;
        this.pack340File = pack340File;
        this.sha1340 = sha1340;
        this.pack393File = pack393File;
        this.sha1393 = sha1393;
        this.pack401File = pack401File;
        this.sha1401 = sha1401;
        this.pack404File = pack404File;
        this.sha1404 = sha1404;
        this.pack477File = pack477File;
        this.sha1477 = sha1477;
        this.pack480File = pack480File;
        this.sha1480 = sha1480;
        this.pack485File = pack485File;
        this.sha1485 = sha1485;
        this.pack490File = pack490File;
        this.sha1490 = sha1490;
        this.pack498File = pack498File;
        this.sha1498 = sha1498;
        this.pack573File = pack573File;
        this.sha1573 = sha1573;
        this.pack575File = pack575File;
        this.sha1575 = sha1575;
        this.pack578File = pack578File;
        this.sha1578 = sha1578;
        this.pack776File = pack776File;
        this.sha1776 = sha1776;
        this.pack735File = pack735File;
        this.sha1735 = sha1735;
        this.pack736File = pack736File;
        this.sha1736 = sha1736;
        this.pack751File = pack751File;
        this.sha1751 = sha1751;
        this.pack753File = pack753File;
        this.sha1753 = sha1753;
        this.pack754File = pack754File;
        this.sha1754 = sha1754;
        this.pack755File = pack755File;
        this.sha1755 = sha1755;
        this.pack756File = pack756File;
        this.sha1756 = sha1756;
        this.pack757File = pack757File;
        this.sha1757 = sha1757;
        this.requestedPort = port;
        this.required = required;
        this.promptKey = promptKey == null ? "" : promptKey;
    }

    /** Convenience: serve all fifty-one packs (735-776 and the descent packs), with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built735, ResourcePackBuilder.BuiltPack built736, ResourcePackBuilder.BuiltPack built751, ResourcePackBuilder.BuiltPack built753, ResourcePackBuilder.BuiltPack built754, ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761, ResourcePackBuilder.BuiltPack built762, ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764, ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767, ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769, ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771, ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773, ResourcePackBuilder.BuiltPack built774, ResourcePackBuilder.BuiltPack built775, ResourcePackBuilder.BuiltPack built776, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built404, ResourcePackBuilder.BuiltPack built401, ResourcePackBuilder.BuiltPack built393, ResourcePackBuilder.BuiltPack built340, ResourcePackBuilder.BuiltPack built338, ResourcePackBuilder.BuiltPack built335, ResourcePackBuilder.BuiltPack built316, ResourcePackBuilder.BuiltPack built315, ResourcePackBuilder.BuiltPack built210, ResourcePackBuilder.BuiltPack built110, ResourcePackBuilder.BuiltPack built109, ResourcePackBuilder.BuiltPack built108, ResourcePackBuilder.BuiltPack built107, ResourcePackBuilder.BuiltPack built47, ResourcePackBuilder.BuiltPack built5, ResourcePackBuilder.BuiltPack built4,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built210.file(), built210.sha1(), built110.file(), built110.sha1(), built109.file(), built109.sha1(), built108.file(), built108.sha1(), built107.file(), built107.sha1(), built47.file(), built47.sha1(), built5.file(), built5.sha1(), built4.file(), built4.sha1(), built315.file(), built315.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(), built340.sha1(), built393.file(), built393.sha1(), built401.file(), built401.sha1(), built404.file(), built404.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(), built761.file(), built761.sha1(), built762.file(), built762.sha1(), built763.file(), built763.sha1(), built764.file(), built764.sha1(), built766.file(), built766.sha1(), built767.file(), built767.sha1(), built768.file(), built768.sha1(), built769.file(), built769.sha1(), built770.file(), built770.sha1(), built771.file(), built771.sha1(), built772.file(), built772.sha1(), built773.file(), built773.sha1(), built774.file(), built774.sha1(), built775.file(), built775.sha1(), built776.file(), built776.sha1(), built735.file(), built735.sha1(), built736.file(), built736.sha1(), built751.file(), built751.sha1(), built753.file(), built753.sha1(), built754.file(), built754.sha1(), built755.file(), built755.sha1(), built756.file(), built756.sha1(), built757.file(), built757.sha1(), port, required, promptKey);
    }



    /**
     * Starts the HTTP server and returns the {@link ResourcePack} clients
     * should be offered (URL + SHA-1 + required flag + prompt key).
     *
     * @throws IOException if the pack file cannot be read or the port
     *         cannot be bound
     */
    public synchronized ResourcePack start() throws IOException {
        if (httpServer != null) {
            throw new IllegalStateException("Resource pack server is already running");
        }
        packBytes = readAndVerify(packFile, sha1);
        if (pack735File != null) {
            pack735Bytes = readAndVerify(pack735File, sha1735);
        }
        if (pack736File != null) {
            pack736Bytes = readAndVerify(pack736File, sha1736);
        }
        if (pack751File != null) {
            pack751Bytes = readAndVerify(pack751File, sha1751);
        }
        if (pack753File != null) {
            pack753Bytes = readAndVerify(pack753File, sha1753);
        }
        if (pack754File != null) {
            pack754Bytes = readAndVerify(pack754File, sha1754);
        }
        if (pack755File != null) {
            pack755Bytes = readAndVerify(pack755File, sha1755);
        }
        if (pack756File != null) {
            pack756Bytes = readAndVerify(pack756File, sha1756);
        }
        if (pack757File != null) {
            pack757Bytes = readAndVerify(pack757File, sha1757);
        }
        if (pack316File != null) {
            pack316Bytes = readAndVerify(pack316File, sha1316);
        }
        if (pack210File != null) {
            pack210Bytes = readAndVerify(pack210File, sha1210);
        }
        if (pack110File != null) {
            pack110Bytes = readAndVerify(pack110File, sha1110);
        }
        if (pack109File != null) {
            pack109Bytes = readAndVerify(pack109File, sha1109);
        }
        if (pack108File != null) {
            pack108Bytes = readAndVerify(pack108File, sha1108);
        }
        if (pack107File != null) {
            pack107Bytes = readAndVerify(pack107File, sha1107);
        }
        if (pack47File != null) {
            pack47Bytes = readAndVerify(pack47File, sha147);
        }
        if (pack5File != null) {
            pack5Bytes = readAndVerify(pack5File, sha15);
            pack4Bytes = readAndVerify(pack4File, sha14);
        }
        if (pack315File != null) {
            pack315Bytes = readAndVerify(pack315File, sha1315);
        }
        if (pack335File != null) {
            pack335Bytes = readAndVerify(pack335File, sha1335);
        }
        if (pack338File != null) {
            pack338Bytes = readAndVerify(pack338File, sha1338);
        }
        if (pack340File != null) {
            pack340Bytes = readAndVerify(pack340File, sha1340);
        }
        if (pack393File != null) {
            pack393Bytes = readAndVerify(pack393File, sha1393);
        }
        if (pack401File != null) {
            pack401Bytes = readAndVerify(pack401File, sha1401);
        }
        if (pack404File != null) {
            pack404Bytes = readAndVerify(pack404File, sha1404);
        }
        if (pack477File != null) {
            pack477Bytes = readAndVerify(pack477File, sha1477);
        }
        if (pack480File != null) {
            pack480Bytes = readAndVerify(pack480File, sha1480);
        }
        if (pack485File != null) {
            pack485Bytes = readAndVerify(pack485File, sha1485);
        }
        if (pack490File != null) {
            pack490Bytes = readAndVerify(pack490File, sha1490);
        }
        if (pack498File != null) {
            pack498Bytes = readAndVerify(pack498File, sha1498);
        }
        if (pack573File != null) {
            pack573Bytes = readAndVerify(pack573File, sha1573);
        }
        if (pack575File != null) {
            pack575Bytes = readAndVerify(pack575File, sha1575);
        }
        if (pack578File != null) {
            pack578Bytes = readAndVerify(pack578File, sha1578);
        }
        if (pack758File != null) {
            pack758Bytes = readAndVerify(pack758File, sha1758);
        }
        if (pack759File != null) {
            pack759Bytes = readAndVerify(pack759File, sha1759);
        }
        if (pack760File != null) {
            pack760Bytes = readAndVerify(pack760File, sha1760);
        }
        if (pack761File != null) {
            pack761Bytes = readAndVerify(pack761File, sha1761);
        }
        if (pack762File != null) {
            pack762Bytes = readAndVerify(pack762File, sha1762);
        }
        if (pack763File != null) {
            pack763Bytes = readAndVerify(pack763File, sha1763);
        }
        if (pack764File != null) {
            pack764Bytes = readAndVerify(pack764File, sha1764);
        }
        if (pack766File != null) {
            pack766Bytes = readAndVerify(pack766File, sha1766);
        }
        if (pack767File != null) {
            pack767Bytes = readAndVerify(pack767File, sha1767);
        }
        if (pack768File != null) {
            pack768Bytes = readAndVerify(pack768File, sha1768);
        }
        if (pack769File != null) {
            pack769Bytes = readAndVerify(pack769File, sha1769);
        }
        if (pack770File != null) {
            pack770Bytes = readAndVerify(pack770File, sha1770);
        }
        if (pack771File != null) {
            pack771Bytes = readAndVerify(pack771File, sha1771);
        }
        if (pack772File != null) {
            pack772Bytes = readAndVerify(pack772File, sha1772);
        }
        if (pack773File != null) {
            pack773Bytes = readAndVerify(pack773File, sha1773);
        }
        if (pack774File != null) {
            pack774Bytes = readAndVerify(pack774File, sha1774);
        }
        if (pack775File != null) {
            pack775Bytes = readAndVerify(pack775File, sha1775);
        }
        if (pack776File != null) {
            pack776Bytes = readAndVerify(pack776File, sha1776);
        }
        // Bind explicitly to IPv4 loopback (see class javadoc).
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", requestedPort), 0);
        httpServer.createContext(PACK_PATH, exchange -> serveBytes(exchange, packBytes));
        if (pack735Bytes != null) {
            httpServer.createContext(PACK_PATH_735, exchange -> serveBytes(exchange, pack735Bytes));
        }
        if (pack736Bytes != null) {
            httpServer.createContext(PACK_PATH_736, exchange -> serveBytes(exchange, pack736Bytes));
        }
        if (pack751Bytes != null) {
            httpServer.createContext(PACK_PATH_751, exchange -> serveBytes(exchange, pack751Bytes));
        }
        if (pack753Bytes != null) {
            httpServer.createContext(PACK_PATH_753, exchange -> serveBytes(exchange, pack753Bytes));
        }
        if (pack754Bytes != null) {
            httpServer.createContext(PACK_PATH_754, exchange -> serveBytes(exchange, pack754Bytes));
        }
        if (pack755Bytes != null) {
            httpServer.createContext(PACK_PATH_755, exchange -> serveBytes(exchange, pack755Bytes));
        }
        if (pack756Bytes != null) {
            httpServer.createContext(PACK_PATH_756, exchange -> serveBytes(exchange, pack756Bytes));
        }
        if (pack757Bytes != null) {
            httpServer.createContext(PACK_PATH_757, exchange -> serveBytes(exchange, pack757Bytes));
        }
        if (pack316Bytes != null) {
            httpServer.createContext(PACK_PATH_316, exchange -> serveBytes(exchange, pack316Bytes));
        }
        if (pack210Bytes != null) {
            httpServer.createContext(PACK_PATH_210, exchange -> serveBytes(exchange, pack210Bytes));
        }
        if (pack110Bytes != null) {
            httpServer.createContext(PACK_PATH_110, exchange -> serveBytes(exchange, pack110Bytes));
        }
        if (pack109Bytes != null) {
            httpServer.createContext(PACK_PATH_109, exchange -> serveBytes(exchange, pack109Bytes));
        }
        if (pack108Bytes != null) {
            httpServer.createContext(PACK_PATH_108, exchange -> serveBytes(exchange, pack108Bytes));
        }
        if (pack107Bytes != null) {
            httpServer.createContext(PACK_PATH_107, exchange -> serveBytes(exchange, pack107Bytes));
        }
        if (pack47Bytes != null) {
            httpServer.createContext(PACK_PATH_47, exchange -> serveBytes(exchange, pack47Bytes));
        }
        if (pack5Bytes != null) {
            httpServer.createContext(PACK_PATH_5, exchange -> serveBytes(exchange, pack5Bytes));
        }
        if (pack4Bytes != null) {
            httpServer.createContext(PACK_PATH_4, exchange -> serveBytes(exchange, pack4Bytes));
        }
        if (pack315Bytes != null) {
            httpServer.createContext(PACK_PATH_315, exchange -> serveBytes(exchange, pack315Bytes));
        }
        if (pack335Bytes != null) {
            httpServer.createContext(PACK_PATH_335, exchange -> serveBytes(exchange, pack335Bytes));
        }
        if (pack338Bytes != null) {
            httpServer.createContext(PACK_PATH_338, exchange -> serveBytes(exchange, pack338Bytes));
        }
        if (pack340Bytes != null) {
            httpServer.createContext(PACK_PATH_340, exchange -> serveBytes(exchange, pack340Bytes));
        }
        if (pack393Bytes != null) {
            httpServer.createContext(PACK_PATH_393, exchange -> serveBytes(exchange, pack393Bytes));
        }
        if (pack401Bytes != null) {
            httpServer.createContext(PACK_PATH_401, exchange -> serveBytes(exchange, pack401Bytes));
        }
        if (pack404Bytes != null) {
            httpServer.createContext(PACK_PATH_404, exchange -> serveBytes(exchange, pack404Bytes));
        }
        if (pack477Bytes != null) {
            httpServer.createContext(PACK_PATH_477, exchange -> serveBytes(exchange, pack477Bytes));
        }
        if (pack480Bytes != null) {
            httpServer.createContext(PACK_PATH_480, exchange -> serveBytes(exchange, pack480Bytes));
        }
        if (pack485Bytes != null) {
            httpServer.createContext(PACK_PATH_485, exchange -> serveBytes(exchange, pack485Bytes));
        }
        if (pack490Bytes != null) {
            httpServer.createContext(PACK_PATH_490, exchange -> serveBytes(exchange, pack490Bytes));
        }
        if (pack498Bytes != null) {
            httpServer.createContext(PACK_PATH_498, exchange -> serveBytes(exchange, pack498Bytes));
        }
        if (pack573Bytes != null) {
            httpServer.createContext(PACK_PATH_573, exchange -> serveBytes(exchange, pack573Bytes));
        }
        if (pack575Bytes != null) {
            httpServer.createContext(PACK_PATH_575, exchange -> serveBytes(exchange, pack575Bytes));
        }
        if (pack578Bytes != null) {
            httpServer.createContext(PACK_PATH_578, exchange -> serveBytes(exchange, pack578Bytes));
        }
        if (pack758Bytes != null) {
            httpServer.createContext(PACK_PATH_758, exchange -> serveBytes(exchange, pack758Bytes));
        }
        if (pack759Bytes != null) {
            httpServer.createContext(PACK_PATH_759, exchange -> serveBytes(exchange, pack759Bytes));
        }
        if (pack760Bytes != null) {
            httpServer.createContext(PACK_PATH_760, exchange -> serveBytes(exchange, pack760Bytes));
        }
        if (pack761Bytes != null) {
            httpServer.createContext(PACK_PATH_761, exchange -> serveBytes(exchange, pack761Bytes));
        }
        if (pack762Bytes != null) {
            httpServer.createContext(PACK_PATH_762, exchange -> serveBytes(exchange, pack762Bytes));
        }
        if (pack763Bytes != null) {
            httpServer.createContext(PACK_PATH_763, exchange -> serveBytes(exchange, pack763Bytes));
        }
        if (pack764Bytes != null) {
            httpServer.createContext(PACK_PATH_764, exchange -> serveBytes(exchange, pack764Bytes));
        }
        if (pack766Bytes != null) {
            httpServer.createContext(PACK_PATH_766, exchange -> serveBytes(exchange, pack766Bytes));
        }
        if (pack767Bytes != null) {
            httpServer.createContext(PACK_PATH_767, exchange -> serveBytes(exchange, pack767Bytes));
        }
        if (pack768Bytes != null) {
            httpServer.createContext(PACK_PATH_768, exchange -> serveBytes(exchange, pack768Bytes));
        }
        if (pack769Bytes != null) {
            httpServer.createContext(PACK_PATH_769, exchange -> serveBytes(exchange, pack769Bytes));
        }
        if (pack770Bytes != null) {
            httpServer.createContext(PACK_PATH_770, exchange -> serveBytes(exchange, pack770Bytes));
        }
        if (pack771Bytes != null) {
            httpServer.createContext(PACK_PATH_771, exchange -> serveBytes(exchange, pack771Bytes));
        }
        if (pack772Bytes != null) {
            httpServer.createContext(PACK_PATH_772, exchange -> serveBytes(exchange, pack772Bytes));
        }
        if (pack773Bytes != null) {
            httpServer.createContext(PACK_PATH_773, exchange -> serveBytes(exchange, pack773Bytes));
        }
        if (pack774Bytes != null) {
            httpServer.createContext(PACK_PATH_774, exchange -> serveBytes(exchange, pack774Bytes));
        }
        if (pack775Bytes != null) {
            httpServer.createContext(PACK_PATH_775, exchange -> serveBytes(exchange, pack775Bytes));
        }
        if (pack776Bytes != null) {
            httpServer.createContext(PACK_PATH_776, exchange -> serveBytes(exchange, pack776Bytes));
        }
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "resource-pack-http");
            thread.setDaemon(true);
            return thread;
        });
        httpServer.setExecutor(executor);
        httpServer.start();
        LOG.info(() -> "Resource pack server started at " + url());
        mainPack = new ResourcePack(url(), sha1, required, promptKey);
        return mainPack;
    }

    private static byte[] readAndVerify(Path file, String expectedSha1) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (!ResourcePackBuilder.sha1Hex(bytes).equalsIgnoreCase(expectedSha1)) {
            throw new IOException("Resource pack SHA-1 mismatch for " + file);
        }
        return bytes;
    }

    private static void serveBytes(com.sun.net.httpserver.HttpExchange exchange, byte[] bytes)
            throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                    && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Length",
                    String.valueOf(bytes.length));
            exchange.sendResponseHeaders(200, bytes.length);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } else {
                exchange.getResponseBody().close();
            }
        } finally {
            exchange.close();
        }
    }

    /** Stops the HTTP server. No-op when it was never started. */
    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        packBytes = null;
        pack107Bytes = null;
        pack47Bytes = null;
        pack108Bytes = null;
        pack109Bytes = null;
        pack110Bytes = null;
        pack210Bytes = null;
        pack315Bytes = null;
        pack316Bytes = null;
        pack335Bytes = null;
        pack338Bytes = null;
        pack340Bytes = null;
        pack393Bytes = null;
        pack401Bytes = null;
        pack404Bytes = null;
        pack477Bytes = null;
        pack480Bytes = null;
        pack485Bytes = null;
        pack490Bytes = null;
        pack498Bytes = null;
        pack573Bytes = null;
        pack575Bytes = null;
        pack578Bytes = null;
        pack758Bytes = null;
        pack759Bytes = null;
        pack760Bytes = null;
        pack761Bytes = null;
        pack762Bytes = null;
        pack763Bytes = null;
        pack764Bytes = null;
        pack766Bytes = null;
        pack767Bytes = null;
        pack768Bytes = null;
        pack769Bytes = null;
        pack770Bytes = null;
        pack771Bytes = null;
        pack772Bytes = null;
        pack773Bytes = null;
        pack774Bytes = null;
        pack775Bytes = null;
        pack776Bytes = null;
        mainPack = null;
    }

    /** Whether the server is currently running. */
    public synchronized boolean isRunning() {
        return httpServer != null;
    }

    /** The download URL, e.g. {@code http://127.0.0.1:25566/detergentmc-pack.zip}. */
    public synchronized String url() {
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH;
    }

    /** The download URL of the 1.14.4 variant, or {@code null} when not served. */
    /** The download URL of the 1.14.3 variant, or {@code null} when not served. */
    /** The download URL of the 1.14.2 variant, or {@code null} when not served. */
    /** The download URL of the 1.14.1 variant, or {@code null} when not served. */
    /** The download URL of the 1.8.9 variant, or {@code null} when not served. */
    public synchronized String url47() {
        if (pack47File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_47;
    }

    /**
     * The 1.8.9 {@link ResourcePack} to offer to 47 clients, or
     * {@code null} when no 1.8.9 variant is served.
     */
    public synchronized ResourcePack pack47() {
        return pack47File == null ? null
                : new ResourcePack(url47(), sha147, required, promptKey);
    }

    /** The download URL of the 1.7.10 variant, or {@code null} when not served. */
    public synchronized String url5() {
        if (pack5File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_5;
    }

    /**
     * The 1.7.10 {@link ResourcePack} to offer to 5 clients, or
     * {@code null} when no 1.7.10 variant is served.
     */
    public synchronized ResourcePack pack5() {
        return pack5File == null ? null
                : new ResourcePack(url5(), sha15, required, promptKey);
    }

    /** The download URL of the 1.7.2 variant, or {@code null} when not served. */
    public synchronized String url4() {
        if (pack4File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_4;
    }

    /**
     * The 1.7.2 {@link ResourcePack} to offer to 4 clients, or
     * {@code null} when no 1.7.2 variant is served.
     */
    public synchronized ResourcePack pack4() {
        return pack4File == null ? null
                : new ResourcePack(url4(), sha14, required, promptKey);
    }

    /** The download URL of the 1.9 variant, or {@code null} when not served. */
    public synchronized String url107() {
        if (pack107File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_107;
    }

    /**
     * The 1.9 {@link ResourcePack} to offer to 107 clients, or
     * {@code null} when no 1.9 variant is served.
     */
    public synchronized ResourcePack pack107() {
        return pack107File == null ? null
                : new ResourcePack(url107(), sha1107, required, promptKey);
    }

    /** The download URL of the 1.9.1 variant, or {@code null} when not served. */
    public synchronized String url108() {
        if (pack108File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_108;
    }

    /**
     * The 1.9.1 {@link ResourcePack} to offer to 108 clients, or
     * {@code null} when no 1.9.1 variant is served.
     */
    public synchronized ResourcePack pack108() {
        return pack108File == null ? null
                : new ResourcePack(url108(), sha1108, required, promptKey);
    }

    /** The download URL of the 1.9.2 variant, or {@code null} when not served. */
    public synchronized String url109() {
        if (pack109File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_109;
    }

    /**
     * The 1.9.2 {@link ResourcePack} to offer to 109 clients, or
     * {@code null} when no 1.9.2 variant is served.
     */
    public synchronized ResourcePack pack109() {
        return pack109File == null ? null
                : new ResourcePack(url109(), sha1109, required, promptKey);
    }

    /** The download URL of the 1.9.4 variant, or {@code null} when not served. */
    public synchronized String url110() {
        if (pack110File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_110;
    }

    /**
     * The 1.9.4 {@link ResourcePack} to offer to 110 clients, or
     * {@code null} when no 1.9.4 variant is served.
     */
    public synchronized ResourcePack pack110() {
        return pack110File == null ? null
                : new ResourcePack(url110(), sha1110, required, promptKey);
    }

    /** The download URL of the 1.10.2 variant, or {@code null} when not served. */
    public synchronized String url210() {
        if (pack210File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_210;
    }

    /**
     * The 1.10.2 {@link ResourcePack} to offer to 210 clients, or
     * {@code null} when no 1.10.2 variant is served.
     */
    public synchronized ResourcePack pack210() {
        return pack210File == null ? null
                : new ResourcePack(url210(), sha1210, required, promptKey);
    }

    /** The download URL of the 1.11 variant, or {@code null} when not served. */
    public synchronized String url315() {
        if (pack315File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_315;
    }

    /**
     * The 1.11 {@link ResourcePack} to offer to 315 clients, or
     * {@code null} when no 1.11 variant is served.
     */
    public synchronized ResourcePack pack315() {
        return pack315File == null ? null
                : new ResourcePack(url315(), sha1315, required, promptKey);
    }

    /** The download URL of the 1.11.2 variant, or {@code null} when not served. */
    public synchronized String url316() {
        if (pack316File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_316;
    }

    /**
     * The 1.11.2 {@link ResourcePack} to offer to 335 clients, or
     * {@code null} when no 1.11.2 variant is served.
     */
    public synchronized ResourcePack pack316() {
        return pack316File == null ? null
                : new ResourcePack(url316(), sha1316, required, promptKey);
    }

    /** The download URL of the 1.12 variant, or {@code null} when not served. */
    public synchronized String url335() {
        if (pack335File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_335;
    }

    /**
     * The 1.12 {@link ResourcePack} to offer to 335 clients, or
     * {@code null} when no 1.12 variant is served.
     */
    public synchronized ResourcePack pack335() {
        return pack335File == null ? null
                : new ResourcePack(url335(), sha1335, required, promptKey);
    }

    /** The download URL of the 1.12.1 variant, or {@code null} when not served. */
    public synchronized String url338() {
        if (pack338File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_338;
    }

    /**
     * The 1.12.1 {@link ResourcePack} to offer to 338 clients, or
     * {@code null} when no 1.12.1 variant is served.
     */
    public synchronized ResourcePack pack338() {
        return pack338File == null ? null
                : new ResourcePack(url338(), sha1338, required, promptKey);
    }

    /** The download URL of the 1.12.2 variant, or {@code null} when not served. */
    public synchronized String url340() {
        if (pack340File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_340;
    }

    /**
     * The 1.12.2 {@link ResourcePack} to offer to 340 clients, or
     * {@code null} when no 1.12.2 variant is served.
     */
    public synchronized ResourcePack pack340() {
        return pack340File == null ? null
                : new ResourcePack(url340(), sha1340, required, promptKey);
    }

    /** The download URL of the 1.13 variant, or {@code null} when not served. */
    public synchronized String url393() {
        if (pack393File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_393;
    }

    /**
     * The 1.13 {@link ResourcePack} to offer to 393 clients, or
     * {@code null} when no 1.13 variant is served.
     */
    public synchronized ResourcePack pack393() {
        return pack393File == null ? null
                : new ResourcePack(url393(), sha1393, required, promptKey);
    }

    /** The download URL of the 1.13.1 variant, or {@code null} when not served. */
    public synchronized String url401() {
        if (pack401File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_401;
    }

    /**
     * The 1.13.1 {@link ResourcePack} to offer to 401 clients, or
     * {@code null} when no 1.13.1 variant is served.
     */
    public synchronized ResourcePack pack401() {
        return pack401File == null ? null
                : new ResourcePack(url401(), sha1401, required, promptKey);
    }

    /** The download URL of the 1.13.2 variant, or {@code null} when not served. */
    public synchronized String url404() {
        if (pack404File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_404;
    }

    /**
     * The 1.13.2 {@link ResourcePack} to offer to 404 clients, or
     * {@code null} when no 1.13.2 variant is served.
     */
    public synchronized ResourcePack pack404() {
        return pack404File == null ? null
                : new ResourcePack(url404(), sha1404, required, promptKey);
    }

    /** The download URL of the 1.14 variant, or {@code null} when not served. */
    public synchronized String url477() {
        if (pack477File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_477;
    }

    /**
     * The 1.14 {@link ResourcePack} to offer to 477 clients, or
     * {@code null} when no 1.14 variant is served.
     */
    public synchronized ResourcePack pack477() {
        return pack477File == null ? null
                : new ResourcePack(url477(), sha1477, required, promptKey);
    }

    public synchronized String url480() {
        if (pack480File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_480;
    }

    /**
     * The 1.14.1 {@link ResourcePack} to offer to 480 clients, or
     * {@code null} when no 1.14.1 variant is served.
     */
    public synchronized ResourcePack pack480() {
        return pack480File == null ? null
                : new ResourcePack(url480(), sha1480, required, promptKey);
    }

    public synchronized String url485() {
        if (pack485File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_485;
    }

    /**
     * The 1.14.2 {@link ResourcePack} to offer to 485 clients, or
     * {@code null} when no 1.14.2 variant is served.
     */
    public synchronized ResourcePack pack485() {
        return pack485File == null ? null
                : new ResourcePack(url485(), sha1485, required, promptKey);
    }

    public synchronized String url490() {
        if (pack490File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_490;
    }

    /**
     * The 1.14.3 {@link ResourcePack} to offer to 490 clients, or
     * {@code null} when no 1.14.3 variant is served.
     */
    public synchronized ResourcePack pack490() {
        return pack490File == null ? null
                : new ResourcePack(url490(), sha1490, required, promptKey);
    }

    public synchronized String url498() {
        if (pack498File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_498;
    }

    /**
     * The 1.14.4 {@link ResourcePack} to offer to 498 clients, or
     * {@code null} when no 1.14.4 variant is served.
     */
    public synchronized ResourcePack pack498() {
        return pack498File == null ? null
                : new ResourcePack(url498(), sha1498, required, promptKey);
    }

    /** The download URL of the 1.15 variant, or {@code null} when not served. */
    public synchronized String url573() {
        if (pack573File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_573;
    }

    /**
     * The 1.15 {@link ResourcePack} to offer to 573 clients, or
     * {@code null} when no 1.15 variant is served.
     */
    public synchronized ResourcePack pack573() {
        return pack573File == null ? null
                : new ResourcePack(url573(), sha1573, required, promptKey);
    }

    /** The download URL of the 1.15.1 variant, or {@code null} when not served. */
    public synchronized String url575() {
        if (pack575File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_575;
    }

    /**
     * The 1.15.1 {@link ResourcePack} to offer to 575 clients, or
     * {@code null} when no 1.15.1 variant is served.
     */
    public synchronized ResourcePack pack575() {
        return pack575File == null ? null
                : new ResourcePack(url575(), sha1575, required, promptKey);
    }

    /** The download URL of the 1.15.2 variant, or {@code null} when not served. */
    public synchronized String url578() {
        if (pack578File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_578;
    }

    /**
     * The 1.15.2 {@link ResourcePack} to offer to 578 clients, or
     * {@code null} when no 1.15.2 variant is served.
     */
    public synchronized ResourcePack pack578() {
        return pack578File == null ? null
                : new ResourcePack(url578(), sha1578, required, promptKey);
    }

    /** The download URL of the 1.18.2 variant, or {@code null} when not served. */
    public synchronized String url758() {
        if (pack758File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_758;
    }

    /**
     * The 1.18.2 {@link ResourcePack} to offer to 758 clients, or
     * {@code null} when no 1.18.2 variant is served.
     */
    public synchronized ResourcePack pack758() {
        return pack758File == null ? null
                : new ResourcePack(url758(), sha1758, required, promptKey);
    }

    /** The download URL of the 1.19 variant, or {@code null} when not served. */
    public synchronized String url759() {
        if (pack759File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_759;
    }

    /**
     * The 1.19 {@link ResourcePack} to offer to 759 clients, or
     * {@code null} when no 1.19 variant is served.
     */
    public synchronized ResourcePack pack759() {
        return pack759File == null ? null
                : new ResourcePack(url759(), sha1759, required, promptKey);
    }

    /** The download URL of the 1.19.1 / 1.19.2 variant, or {@code null} when not served. */
    public synchronized String url760() {
        if (pack760File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_760;
    }

    /**
     * The 1.19.1 / 1.19.2 {@link ResourcePack} to offer to 760 clients, or
     * {@code null} when no 1.19.1 / 1.19.2 variant is served.
     */
    public synchronized ResourcePack pack760() {
        return pack760File == null ? null
                : new ResourcePack(url760(), sha1760, required, promptKey);
    }

    /** The download URL of the 1.19.3 variant, or {@code null} when not served. */
    public synchronized String url761() {
        if (pack761File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_761;
    }

    /**
     * The 1.19.3 {@link ResourcePack} to offer to 761 clients, or
     * {@code null} when no 1.19.3 variant is served.
     */
    public synchronized ResourcePack pack761() {
        return pack761File == null ? null
                : new ResourcePack(url761(), sha1761, required, promptKey);
    }

    /** The download URL of the 1.19.4 variant, or {@code null} when not served. */
    public synchronized String url762() {
        if (pack762File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_762;
    }

    /**
     * The 1.19.4 {@link ResourcePack} to offer to 762 clients, or
     * {@code null} when no 1.19.4 variant is served.
     */
    public synchronized ResourcePack pack762() {
        return pack762File == null ? null
                : new ResourcePack(url762(), sha1762, required, promptKey);
    }

    /** The download URL of the 1.20.1 variant, or {@code null} when not served. */
    public synchronized String url763() {
        if (pack763File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_763;
    }

    /**
     * The 1.20.1 {@link ResourcePack} to offer to 763 clients, or
     * {@code null} when no 1.20.1 variant is served.
     */
    public synchronized ResourcePack pack763() {
        return pack763File == null ? null
                : new ResourcePack(url763(), sha1763, required, promptKey);
    }

    /** The download URL of the 1.20.2 variant, or {@code null} when not served. */
    public synchronized String url764() {
        if (pack764File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_764;
    }

    /**
     * The 1.20.2 {@link ResourcePack} to offer to 764 clients, or
     * {@code null} when no 1.20.2 variant is served.
     */
    public synchronized ResourcePack pack764() {
        return pack764File == null ? null
                : new ResourcePack(url764(), sha1764, required, promptKey);
    }

    /** The download URL of the 1.20.5 / 1.20.6 variant, or {@code null} when not served. */
    public synchronized String url766() {
        if (pack766File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_766;
    }

    /**
     * The 1.20.5 / 1.20.6 {@link ResourcePack} to offer to 766 clients,
     * or {@code null} when no 1.20.5 / 1.20.6 variant is served. One pack
     * serves both versions: they share protocol 766 and pack_format 32.
     */
    public synchronized ResourcePack pack766() {
        return pack766File == null ? null
                : new ResourcePack(url766(), sha1766, required, promptKey);
    }

    /** The download URL of the 1.21 / 1.21.1 variant, or {@code null} when not served. */
    public synchronized String url767() {
        if (pack767File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_767;
    }

    /**
     * The 1.21 / 1.21.1 {@link ResourcePack} to offer to 767 clients,
     * or {@code null} when no 1.21 / 1.21.1 variant is served. One pack
     * serves both versions: they share protocol 767 and pack_format 34.
     */
    public synchronized ResourcePack pack767() {
        return pack767File == null ? null
                : new ResourcePack(url767(), sha1767, required, promptKey);
    }

    /** The download URL of the 1.21.2 / 1.21.3 variant, or {@code null} when not served. */
    public synchronized String url768() {
        if (pack768File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_768;
    }

    /**
     * The 1.21.2 / 1.21.3 {@link ResourcePack} to offer to 768 clients,
     * or {@code null} when no 1.21.2 / 1.21.3 variant is served. One pack
     * serves both versions: they share protocol 768 and pack_format 42.
     */
    public synchronized ResourcePack pack768() {
        return pack768File == null ? null
                : new ResourcePack(url768(), sha1768, required, promptKey);
    }

    /** The download URL of the 1.21.4 variant, or {@code null} when not served. */
    public synchronized String url769() {
        if (pack769File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_769;
    }

    /**
     * The 1.21.4 {@link ResourcePack} to offer to 769 clients,
     * or {@code null} when no 1.21.4 variant is served.
     */
    public synchronized ResourcePack pack769() {
        return pack769File == null ? null
                : new ResourcePack(url769(), sha1769, required, promptKey);
    }

    /** The download URL of the 1.21.5 variant, or {@code null} when not served. */
    public synchronized String url770() {
        if (pack770File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_770;
    }

    /**
     * The 1.21.5 {@link ResourcePack} to offer to 770 clients,
     * or {@code null} when no 1.21.5 variant is served.
     */
    public synchronized ResourcePack pack770() {
        return pack770File == null ? null
                : new ResourcePack(url770(), sha1770, required, promptKey);
    }

    /** The download URL of the 1.21.6 variant, or {@code null} when not served. */
    public synchronized String url771() {
        if (pack771File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_771;
    }

    /**
     * The 1.21.6 {@link ResourcePack} to offer to 771 clients,
     * or {@code null} when no 1.21.6 variant is served.
     */
    public synchronized ResourcePack pack771() {
        return pack771File == null ? null
                : new ResourcePack(url771(), sha1771, required, promptKey);
    }

    /** The download URL of the 1.21.7 variant, or {@code null} when not served. */
    public synchronized String url772() {
        if (pack772File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_772;
    }

    /**
     * The 1.21.7 {@link ResourcePack} to offer to 772 clients,
     * or {@code null} when no 1.21.7 variant is served.
     */
    public synchronized ResourcePack pack772() {
        return pack772File == null ? null
                : new ResourcePack(url772(), sha1772, required, promptKey);
    }

    /** The download URL of the 1.21.9 variant, or {@code null} when not served. */
    public synchronized String url773() {
        if (pack773File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_773;
    }

    /**
     * The 1.21.9 {@link ResourcePack} to offer to 773 clients,
     * or {@code null} when no 1.21.9 variant is served.
     */
    public synchronized ResourcePack pack773() {
        return pack773File == null ? null
                : new ResourcePack(url773(), sha1773, required, promptKey);
    }

    /** The download URL of the 1.21.11 variant, or {@code null} when not served. */
    public synchronized String url774() {
        if (pack774File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_774;
    }

    /**
     * The 1.21.11 {@link ResourcePack} to offer to 774 clients,
     * or {@code null} when no 1.21.11 variant is served.
     */
    public synchronized ResourcePack pack774() {
        return pack774File == null ? null
                : new ResourcePack(url774(), sha1774, required, promptKey);
    }

    /** The download URL of the 26.1 variant, or {@code null} when not served. */
    public synchronized String url775() {
        if (pack775File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_775;
    }

    /**
     * The 26.1 {@link ResourcePack} to offer to 775 clients,
     * or {@code null} when no 26.1 variant is served.
     */
    public synchronized ResourcePack pack775() {
        return pack775File == null ? null
                : new ResourcePack(url775(), sha1775, required, promptKey);
    }

    /** The download URL of the 26.2 variant, or {@code null} when not served. */
    public synchronized String url776() {
        if (pack776File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_776;
    }

    /**
     * The 26.2 {@link ResourcePack} to offer to 776 clients,
     * or {@code null} when no 26.2 variant is served.
     */
    public synchronized ResourcePack pack776() {
        return pack776File == null ? null
                : new ResourcePack(url776(), sha1776, required, promptKey);
    }

    /** The download URL of the 1.16 variant, or {@code null} when not served. */
    public synchronized String url735() {
        if (pack735File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_735;
    }

    /**
     * The 1.16 {@link ResourcePack} to offer to 735 clients, or
     * {@code null} when no 1.16 variant is served.
     */
    public synchronized ResourcePack pack735() {
        return pack735File == null ? null
                : new ResourcePack(url735(), sha1735, required, promptKey);
    }

    /** The download URL of the 1.16.1 variant, or {@code null} when not served. */
    public synchronized String url736() {
        if (pack736File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_736;
    }

    /**
     * The 1.16.1 {@link ResourcePack} to offer to 736 clients, or
     * {@code null} when no 1.16.1 variant is served.
     */
    public synchronized ResourcePack pack736() {
        return pack736File == null ? null
                : new ResourcePack(url736(), sha1736, required, promptKey);
    }

    /** The download URL of the 1.16.2 variant, or {@code null} when not served. */
    public synchronized String url751() {
        if (pack751File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_751;
    }

    /**
     * The 1.16.2 {@link ResourcePack} to offer to 751 clients, or
     * {@code null} when no 1.16.2 variant is served.
     */
    public synchronized ResourcePack pack751() {
        return pack751File == null ? null
                : new ResourcePack(url751(), sha1751, required, promptKey);
    }

    /** The download URL of the 1.16.3 variant, or {@code null} when not served. */
    public synchronized String url753() {
        if (pack753File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_753;
    }

    /**
     * The 1.16.3 {@link ResourcePack} to offer to 753 clients, or
     * {@code null} when no 1.16.3 variant is served.
     */
    public synchronized ResourcePack pack753() {
        return pack753File == null ? null
                : new ResourcePack(url753(), sha1753, required, promptKey);
    }

    /** The download URL of the 1.16.4 / 1.16.5 variant, or {@code null} when not served.
     * One pack serves both versions: they share protocol 754. */
    public synchronized String url754() {
        if (pack754File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_754;
    }

    /**
     * The 1.16.4 / 1.16.5 {@link ResourcePack} to offer to 754 clients, or
     * {@code null} when no 1.16.4 / 1.16.5 variant is served. One pack
     * serves both versions: they share protocol 754 and pack_format 6.
     */
    public synchronized ResourcePack pack754() {
        return pack754File == null ? null
                : new ResourcePack(url754(), sha1754, required, promptKey);
    }

    /** The download URL of the 1.17 variant, or {@code null} when not served. */
    public synchronized String url755() {
        if (pack755File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_755;
    }

    /**
     * The 1.17 {@link ResourcePack} to offer to 755 clients, or
     * {@code null} when no 1.17 variant is served.
     */
    public synchronized ResourcePack pack755() {
        return pack755File == null ? null
                : new ResourcePack(url755(), sha1755, required, promptKey);
    }

    /** The download URL of the 1.17.1 variant, or {@code null} when not served. */
    public synchronized String url756() {
        if (pack756File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_756;
    }

    /**
     * The 1.17.1 {@link ResourcePack} to offer to 756 clients, or
     * {@code null} when no 1.17.1 variant is served.
     */
    public synchronized ResourcePack pack756() {
        return pack756File == null ? null
                : new ResourcePack(url756(), sha1756, required, promptKey);
    }

    /** The download URL of the 1.18 / 1.18.1 variant, or {@code null} when not served. */
    public synchronized String url757() {
        if (pack757File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_757;
    }

    /**
     * The 1.18 / 1.18.1 {@link ResourcePack} to offer to 757 clients, or
     * {@code null} when no 1.18 / 1.18.1 variant is served.
     */
    public synchronized ResourcePack pack757() {
        return pack757File == null ? null
                : new ResourcePack(url757(), sha1757, required, promptKey);
    }

    /**
     * クライアントのプロトコルに合ったリソースパックを返す。
     * 758→{@link #pack758()}（pack_format 8。無い場合は759パック、さらに無い場合は760パック、
     * さらに無い場合は761パック、さらに無い場合は762パック、さらに無い場合は763パックに代替）、
     * 759→{@link #pack759()}（pack_format 9。無い場合は760パック、さらに無い場合は761パック、
     * さらに無い場合は762パック、さらに無い場合は763パックに代替）、
     * 761→{@link #pack761()}（pack_format 12。無い場合は762パック、さらに無い場合は763パックに代替）、
     * 762→{@link #pack762()}（pack_format 13。無い場合は763パックに代替）、
     * 763→{@link #pack763()}（pack_format 15）、764→{@link #pack764()}
     * （pack_format 18）、766→{@link #pack766()}（pack_format 32）、
     * 767→{@link #pack767()}（pack_format 34）、
     * 768→{@link #pack768()}（pack_format 42）、
     * 769→{@link #pack769()}（pack_format 46）、
     * 770→{@link #pack770()}（pack_format 55）、
     * 771→{@link #pack771()}（pack_format 63）、
     * 772→{@link #pack772()}（pack_format 64）、
     * 773→{@link #pack773()}（pack_format 69）、
     * 774→{@link #pack774()}（pack_format 75）、
     * 775→{@link #pack775()}（pack_format 84）、
     * 776→{@link #pack776()}（pack_format 88）、
     * 765→{@link #start()} が返した本体（pack_format 22）。開始前は本体が
     * 無いため 765 には null を返す。
     *
     * <p>出し分けの基準はクライアントのプロトコルであって、内部サーバーの
     * バージョンではない。内部が764でも765クライアントには本体（22）を
     * 出す。内部サーバー自身（server.properties）に書くパックも、同じ
     * 基準で {@code packForProtocol(serverProtocol)} を使って選ぶこと。
     */
    public synchronized ResourcePack packForProtocol(int clientProtocol) {
        if (clientProtocol == ProtocolVersion.V1_16) {
            // 735パックが無い旧コンストラクタでは736パック、さらに無い場合は751パック、
            // さらに無い場合は753パック、さらに無い場合は754パック、さらに無い場合は755パック、
            // さらに無い場合は756パック、さらに無い場合は757パック、
            // さらに無い場合は759パック、さらに無い場合は760パック、さらに無い場合は761パック、
            // さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            // 735は今のツリー内最古のため、隣接する新しい pack736 に落とす
            // （ProxySession のフォールバックと同じ決まり。735も736も
            // pack_format 5のため、落としても形式は変わらない）。
            ResourcePack pack735 = pack735();
            if (pack735 != null) {
                return pack735;
            }
            ResourcePack pack736 = pack736();
            if (pack736 != null) {
                return pack736;
            }
            ResourcePack pack751 = pack751();
            if (pack751 != null) {
                return pack751;
            }
            ResourcePack pack753 = pack753();
            if (pack753 != null) {
                return pack753;
            }
            ResourcePack pack754 = pack754();
            if (pack754 != null) {
                return pack754;
            }
            ResourcePack pack755 = pack755();
            if (pack755 != null) {
                return pack755;
            }
            ResourcePack pack756 = pack756();
            if (pack756 != null) {
                return pack756;
            }
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_16_1) {
            // 736パックが無い旧コンストラクタでは751パック、さらに無い場合は753パック、
            // さらに無い場合は754パック、さらに無い場合は755パック、さらに無い場合は756パック、さらに無い場合は757パック、
            // さらに無い場合は759パック、さらに無い場合は760パック、さらに無い場合は761パック、
            // さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            // 隣接する新しい pack751 に落とす
            // （ProxySession のフォールバックと同じ決まり。736は pack_format 5、
            // 751は pack_format 6のため、落とすと形式が変わる点に注意。754のときの
            // pack755へのフォールバック（6→7）と同じく、境界を越える落とし方）。
            ResourcePack pack736 = pack736();
            if (pack736 != null) {
                return pack736;
            }
            ResourcePack pack751 = pack751();
            if (pack751 != null) {
                return pack751;
            }
            ResourcePack pack753 = pack753();
            if (pack753 != null) {
                return pack753;
            }
            ResourcePack pack754 = pack754();
            if (pack754 != null) {
                return pack754;
            }
            ResourcePack pack755 = pack755();
            if (pack755 != null) {
                return pack755;
            }
            ResourcePack pack756 = pack756();
            if (pack756 != null) {
                return pack756;
            }
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_16_2) {
            // 751パックが無い旧コンストラクタでは753パック、さらに無い場合は754パック、
            // さらに無い場合は755パック、さらに無い場合は756パック、さらに無い場合は757パック、
            // さらに無い場合は759パック、さらに無い場合は760パック、さらに無い場合は761パック、
            // さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            // 隣接する新しい pack753 に落とす
            // （ProxySession のフォールバックと同じ決まり。751と753はどちらも
            // pack_format 6のため、落としても形式は変わらない）。
            ResourcePack pack751 = pack751();
            if (pack751 != null) {
                return pack751;
            }
            ResourcePack pack753 = pack753();
            if (pack753 != null) {
                return pack753;
            }
            ResourcePack pack754 = pack754();
            if (pack754 != null) {
                return pack754;
            }
            ResourcePack pack755 = pack755();
            if (pack755 != null) {
                return pack755;
            }
            ResourcePack pack756 = pack756();
            if (pack756 != null) {
                return pack756;
            }
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_16_3) {
            // 753パックが無い旧コンストラクタでは754パック、さらに無い場合は755パック、
            // さらに無い場合は756パック、さらに無い場合は757パック、さらに無い場合は759パック、
            // さらに無い場合は760パック、さらに無い場合は761パック、さらに無い場合は762パック、
            // さらに無い場合は763パックに代替する。
            // 隣接する新しい pack754 に落とす
            // （ProxySession のフォールバックと同じ決まり。753と754はどちらも
            // pack_format 6のため、落としても形式は変わらない）。
            ResourcePack pack753 = pack753();
            if (pack753 != null) {
                return pack753;
            }
            ResourcePack pack754 = pack754();
            if (pack754 != null) {
                return pack754;
            }
            ResourcePack pack755 = pack755();
            if (pack755 != null) {
                return pack755;
            }
            ResourcePack pack756 = pack756();
            if (pack756 != null) {
                return pack756;
            }
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_16_5) {
            // 754パックが無い旧コンストラクタでは755パック、さらに無い場合は756パック、
            // さらに無い場合は757パック、さらに無い場合は759パック、さらに無い場合は760パック、
            // さらに無い場合は761パック、さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            // 754は今のツリー内最古のため、隣接する新しい pack755 に落とす
            // （ProxySession のフォールバックと同じ決まり）。
            ResourcePack pack754 = pack754();
            if (pack754 != null) {
                return pack754;
            }
            ResourcePack pack755 = pack755();
            if (pack755 != null) {
                return pack755;
            }
            ResourcePack pack756 = pack756();
            if (pack756 != null) {
                return pack756;
            }
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_17) {
            // 755パックが無い旧コンストラクタでは756パック、さらに無い場合は757パック、
            // さらに無い場合は759パック、さらに無い場合は760パック、さらに無い場合は761パック、
            // さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            // 隣接する新しい pack756 に落とす（ProxySession のフォールバックと
            // 同じ決まり。755と756はどちらも pack_format 7のため、落としても
            // 形式は変わらない）。
            ResourcePack pack755 = pack755();
            if (pack755 != null) {
                return pack755;
            }
            ResourcePack pack756 = pack756();
            if (pack756 != null) {
                return pack756;
            }
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_17_1) {
            // 756パックが無い旧コンストラクタでは757パック、さらに無い場合は759パック、
            // さらに無い場合は760パック、さらに無い場合は761パック、さらに無い場合は762パック、
            // さらに無い場合は763パックに代替する。
            // 隣接する新しい pack757 に落とす（ProxySession のフォールバックと
            // 同じ決まり。Stage B §13.3）。
            ResourcePack pack756 = pack756();
            if (pack756 != null) {
                return pack756;
            }
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_18) {
            // 757パックが無い旧コンストラクタでは759パック、さらに無い場合は760パック、
            // さらに無い場合は761パック、さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            // 757より古いパックはツリーに無いため、隣接する新しい pack759 に落とす
            // （ProxySession のフォールバックと同じ決まり。Stage B §13.3）。
            ResourcePack pack757 = pack757();
            if (pack757 != null) {
                return pack757;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_9) {
            // 107パックが無い場合は108パック（同じ pack_format 2・
            // 同じ旧レイアウト。説明文だけが 1.9.1 用になる）、
            // さらに無い場合は108分岐と同じ順に代替する。
            ResourcePack pack107 = pack107();
            if (pack107 != null) {
                return pack107;
            }
            return packForProtocol(ProtocolVersion.V1_9_1);
        }
        if (clientProtocol == ProtocolVersion.V1_9_1) {
            // 108パックが無い場合は109パック（同じ pack_format 2・
            // 同じ旧レイアウト。説明文だけが 1.9.2 用になる）、
            // さらに無い場合は109分岐と同じ順に代替する。
            ResourcePack pack108 = pack108();
            if (pack108 != null) {
                return pack108;
            }
            return packForProtocol(ProtocolVersion.V1_9_2);
        }
        if (clientProtocol == ProtocolVersion.V1_9_2) {
            // 109パックが無い場合は110パック（同じ pack_format 2・
            // 同じ旧レイアウト。説明文だけが 1.9.4 用になる）、
            // さらに無い場合は110分岐と同じ順に代替する。
            ResourcePack pack109 = pack109();
            if (pack109 != null) {
                return pack109;
            }
            return packForProtocol(ProtocolVersion.V1_9_4);
        }
        if (clientProtocol == ProtocolVersion.V1_9_4) {
            // 110パックが無い場合は210パック（同じ pack_format 2・
            // 同じ旧レイアウト。説明文だけが 1.10.2 用になる）、
            // さらに無い場合は210分岐と同じ順に代替する。
            ResourcePack pack110 = pack110();
            if (pack110 != null) {
                return pack110;
            }
            return packForProtocol(ProtocolVersion.V1_10_2);
        }
        if (clientProtocol == ProtocolVersion.V1_10_2) {
            // 210パックが無い場合は315パック（pack_format は 2 と 3 で
            // 異なるが、旧レイアウトは共通。315 分岐と同じ順に代替する。
            // 本来は形式が違うため 210 専用パックの用意が望ましい）、
            // さらに無い場合は315分岐と同じ順に代替する。
            ResourcePack pack210 = pack210();
            if (pack210 != null) {
                return pack210;
            }
            return packForProtocol(ProtocolVersion.V1_11);
        }
        if (clientProtocol == ProtocolVersion.V1_11) {
            // 315パックが無い場合は316パック（同じ pack_format 3・
            // 同じ旧レイアウト。説明文だけが 1.11.2 用になる）、
            // さらに無い場合は316分岐と同じ順に代替する。
            ResourcePack pack315 = pack315();
            if (pack315 != null) {
                return pack315;
            }
            return packForProtocol(ProtocolVersion.V1_11_2);
        }
        if (clientProtocol == ProtocolVersion.V1_11_2) {
            // 316パックが無い場合は335パック（同じ pack_format 3・
            // 同じ旧レイアウト。説明文だけが 1.12 用になる）、
            // さらに無い場合は335分岐と同じ順に代替する。
            ResourcePack pack316 = pack316();
            if (pack316 != null) {
                return pack316;
            }
            return packForProtocol(ProtocolVersion.V1_12);
        }
        if (clientProtocol == ProtocolVersion.V1_12) {
            // 335パックが無い場合は338パック（同じ pack_format 3・
            // 同じ旧レイアウト。説明文だけが 1.12.1 用になる）、
            // さらに無い場合は338分岐と同じ順に代替する。
            ResourcePack pack335 = pack335();
            if (pack335 != null) {
                return pack335;
            }
            return packForProtocol(ProtocolVersion.V1_12_1);
        }
        if (clientProtocol == ProtocolVersion.V1_12_1) {
            // 338パックが無い場合は340パック（同じ pack_format 3・
            // 同じ旧レイアウト。説明文だけが 1.12.2 用になる）、
            // さらに無い場合は340分岐と同じ順に代替する。
            ResourcePack pack338 = pack338();
            if (pack338 != null) {
                return pack338;
            }
            return packForProtocol(ProtocolVersion.V1_12_2);
        }
        if (clientProtocol == ProtocolVersion.V1_12_2) {
            // 340パックが無い場合は393パック（pack_format 4。1.12.2
            // クライアントは読み込めないが、代替が無い場合の最終手段）、
            // さらに無い場合は393分岐と同じ順に代替する。
            ResourcePack pack340 = pack340();
            if (pack340 != null) {
                return pack340;
            }
            return packForProtocol(ProtocolVersion.V1_13);
        }
        if (clientProtocol == ProtocolVersion.V1_13) {
            // 393パックが無い場合は401パック（同じ pack_format 4）、
            // さらに無い場合は401分岐と同じ順に代替する。
            ResourcePack pack393 = pack393();
            if (pack393 != null) {
                return pack393;
            }
            return packForProtocol(ProtocolVersion.V1_13_1);
        }
        if (clientProtocol == ProtocolVersion.V1_13_1) {
            // 401パックが無い場合は404パック（同じ pack_format 4）、
            // さらに無い場合は404分岐と同じ順に代替する。
            ResourcePack pack401 = pack401();
            if (pack401 != null) {
                return pack401;
            }
            return packForProtocol(ProtocolVersion.V1_13_2);
        }
        if (clientProtocol == ProtocolVersion.V1_13_2) {
            // 404パックが無い場合は477パック（同じ pack_format 4）、
            // さらに無い場合は477分岐と同じ順に代替する。
            ResourcePack pack404 = pack404();
            if (pack404 != null) {
                return pack404;
            }
            return packForProtocol(ProtocolVersion.V1_14);
        }
        if (clientProtocol == ProtocolVersion.V1_14) {
            // 477パックが無い場合は480パック（同じ pack_format 4）、
            // さらに無い場合は480分岐と同じ順に代替する。
            ResourcePack pack477 = pack477();
            if (pack477 != null) {
                return pack477;
            }
            return packForProtocol(ProtocolVersion.V1_14_1);
        }
        if (clientProtocol == ProtocolVersion.V1_14_1) {
            // 480パックが無い場合は485パック（同じ pack_format 4）、
            // さらに無い場合は485分岐と同じ順に代替する。
            ResourcePack pack480 = pack480();
            if (pack480 != null) {
                return pack480;
            }
            return packForProtocol(ProtocolVersion.V1_14_2);
        }
        if (clientProtocol == ProtocolVersion.V1_14_2) {
            // 485パックが無い場合は490パック（同じ pack_format 4）、
            // さらに無い場合は490分岐と同じ順に代替する。
            ResourcePack pack485 = pack485();
            if (pack485 != null) {
                return pack485;
            }
            return packForProtocol(ProtocolVersion.V1_14_3);
        }
        if (clientProtocol == ProtocolVersion.V1_14_3) {
            // 490パックが無い場合は498パック（同じ pack_format 4）、
            // さらに無い場合は498分岐と同じ順に代替する。
            ResourcePack pack490 = pack490();
            if (pack490 != null) {
                return pack490;
            }
            return packForProtocol(ProtocolVersion.V1_14_4);
        }
        if (clientProtocol == ProtocolVersion.V1_14_4) {
            // 498パックが無い旧コンストラクタでは573パック、さらに無い場合は
            // 575・578・758・759・760・761・762・763パックの順に代替する。
            ResourcePack pack498 = pack498();
            if (pack498 != null) {
                return pack498;
            }
            ResourcePack pack573 = pack573();
            if (pack573 != null) {
                return pack573;
            }
            ResourcePack pack575 = pack575();
            if (pack575 != null) {
                return pack575;
            }
            ResourcePack pack578 = pack578();
            if (pack578 != null) {
                return pack578;
            }
            ResourcePack pack758 = pack758();
            if (pack758 != null) {
                return pack758;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_15) {
            // 573パックが無い旧コンストラクタでは575パック、さらに無い場合は
            // 578・758・759・760・761・762・763パックの順に代替する。
            ResourcePack pack573 = pack573();
            if (pack573 != null) {
                return pack573;
            }
            ResourcePack pack575 = pack575();
            if (pack575 != null) {
                return pack575;
            }
            ResourcePack pack578 = pack578();
            if (pack578 != null) {
                return pack578;
            }
            ResourcePack pack758 = pack758();
            if (pack758 != null) {
                return pack758;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_15_1) {
            // 575パックが無い旧コンストラクタでは578パック、さらに無い場合は
            // 758・759・760・761・762・763パックの順に代替する。
            ResourcePack pack575 = pack575();
            if (pack575 != null) {
                return pack575;
            }
            ResourcePack pack578 = pack578();
            if (pack578 != null) {
                return pack578;
            }
            ResourcePack pack758 = pack758();
            if (pack758 != null) {
                return pack758;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_15_2) {
            // 578パックが無い旧コンストラクタでは758パック、さらに無い場合は
            // 759・760・761・762・763パックの順に代替する。
            ResourcePack pack578 = pack578();
            if (pack578 != null) {
                return pack578;
            }
            ResourcePack pack758 = pack758();
            if (pack758 != null) {
                return pack758;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_18_2) {
            // 758パックが無い旧コンストラクタでは759パック、さらに無い場合は760パック、
            // さらに無い場合は761パック、さらに無い場合は762パック、
            // さらに無い場合は763パックに代替する。
            ResourcePack pack758 = pack758();
            if (pack758 != null) {
                return pack758;
            }
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_19) {
            // 759パックが無い旧コンストラクタでは760パック、さらに無い場合は761パック、
            // さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            ResourcePack pack759 = pack759();
            if (pack759 != null) {
                return pack759;
            }
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_19_1) {
            // 760パックが無い旧コンストラクタでは761パック、さらに無い場合は762パック、
            // さらに無い場合は763パックに代替する。
            ResourcePack pack760 = pack760();
            if (pack760 != null) {
                return pack760;
            }
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_19_3) {
            // 761パックが無い旧コンストラクタでは762パック、さらに無い場合は763パックに代替する。
            ResourcePack pack761 = pack761();
            if (pack761 != null) {
                return pack761;
            }
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_19_4) {
            // 762パックが無い旧コンストラクタでは763パックに代替する。
            ResourcePack pack762 = pack762();
            return pack762 != null ? pack762 : pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_20_1) {
            return pack763();
        }
        if (clientProtocol == ProtocolVersion.V1_20_2) {
            return pack764();
        }
        if (clientProtocol == ProtocolVersion.V1_20_5) {
            return pack766();
        }
        if (clientProtocol == ProtocolVersion.V1_21) {
            return pack767();
        }
        if (clientProtocol == ProtocolVersion.V1_21_2) {
            return pack768();
        }
        if (clientProtocol == ProtocolVersion.V1_21_4) {
            return pack769();
        }
        if (clientProtocol == ProtocolVersion.V1_21_5) {
            return pack770();
        }
        if (clientProtocol == ProtocolVersion.V1_21_6) {
            return pack771();
        }
        if (clientProtocol == ProtocolVersion.V1_21_7) {
            return pack772();
        }
        if (clientProtocol == ProtocolVersion.V1_21_9) {
            return pack773();
        }
        if (clientProtocol == ProtocolVersion.V1_21_11) {
            return pack774();
        }
        if (clientProtocol == ProtocolVersion.V26_1) {
            return pack775();
        }
        if (clientProtocol == ProtocolVersion.V26_2) {
            return pack776();
        }
        return mainPack;
    }

    /** The port actually bound, or the requested port before {@link #start()}. */
    public synchronized int port() {
        return httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
    }

    public Path packFile() {
        return packFile;
    }

    public String sha1() {
        return sha1;
    }

    public boolean required() {
        return required;
    }
}
