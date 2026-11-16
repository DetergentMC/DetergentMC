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
 * <h2>1.16.1 variant</h2>
 *
 * <p>When a twenty-fourth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_736} and
 * {@link #pack736()} returns the {@link ResourcePack} the proxy offers
 * to 736 clients (pack_format 5).
 * All twenty-four packs share the same port.
 *
 * <h2>1.16.2 variant</h2>
 *
 * <p>When a twenty-third pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_751} and
 * {@link #pack751()} returns the {@link ResourcePack} the proxy offers
 * to 751 clients (pack_format 6).
 * All twenty-three packs share the same port.
 *
 * <h2>1.16.3 variant</h2>
 *
 * <p>When a twenty-second pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_753} and
 * {@link #pack753()} returns the {@link ResourcePack} the proxy offers
 * to 753 clients (pack_format 6).
 * All twenty-two packs share the same port.
 *
 * <h2>1.16.4 / 1.16.5 variant</h2>
 *
 * <p>When a twenty-first pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_754} and
 * {@link #pack754()} returns the {@link ResourcePack} the proxy offers
 * to 754 clients (pack_format 6). One pack serves both 1.16.4 and
 * 1.16.5: they share protocol 754 and pack_format 6.
 * All twenty-one packs share the same port.
 *
 * <h2>1.17 variant</h2>
 *
 * <p>When a twentieth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_755} and
 * {@link #pack755()} returns the {@link ResourcePack} the proxy offers
 * to 755 clients (pack_format 7). All twenty packs share the same port.
 *
 * <h2>1.17.1 variant</h2>
 *
 * <p>When a nineteenth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_756} and
 * {@link #pack756()} returns the {@link ResourcePack} the proxy offers
 * to 756 clients (pack_format 7). All nineteen packs share the same port.
 *
 * <h2>1.18 / 1.18.1 variant</h2>
 *
 * <p>When an eighteenth pack is given (see
 * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),
 * the server also serves it at {@link #PACK_PATH_757} and
 * {@link #pack757()} returns the {@link ResourcePack} the proxy offers
 * to 757 clients (pack_format 8). All eighteen packs share the same port.
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
 */
public final class ResourcePackServer {

    private static final Logger LOG = Logger.getLogger(ResourcePackServer.class.getName());

    /** The only path this server answers. */
    public static final String PACK_PATH = "/detergentmc-pack.zip";

    /** Path of the 1.16.4 / 1.16.5 (pack_format 6) variant, when one is served.
     * One pack serves both versions: they share protocol 754. */
    /** Path of the 1.16.1 (pack_format 5) variant, when one is served. */
    public static final String PACK_PATH_736 = "/detergentmc-pack-1.16.1.zip";

    /** Path of the 1.16.2 (pack_format 6) variant, when one is served. */
    public static final String PACK_PATH_751 = "/detergentmc-pack-1.16.2.zip";

    /** Path of the 1.16.3 (pack_format 6) variant, when one is served. */
    public static final String PACK_PATH_753 = "/detergentmc-pack-1.16.3.zip";

    public static final String PACK_PATH_754 = "/detergentmc-pack-1.16.5.zip";

    /** Path of the 1.17 (pack_format 7) variant, when one is served. */
    public static final String PACK_PATH_755 = "/detergentmc-pack-1.17.zip";

    /** Path of the 1.18 / 1.18.1 (pack_format 8) variant, when one is served. */
    /** Path of the 1.17.1 (pack_format 7) variant, when one is served. */
    public static final String PACK_PATH_756 = "/detergentmc-pack-1.17.1.zip";

    /** Path of the 1.18 / 1.18.1 (pack_format 8) variant, when one is served. */
    public static final String PACK_PATH_757 = "/detergentmc-pack-1.18.zip";

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

    /** Translation key used for the client-side pack prompt. */
    public static final String DEFAULT_PROMPT_KEY = "resource-pack.prompt";

    private final Path packFile;
    private final String sha1;
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
    private final int requestedPort;
    private final boolean required;
    private final String promptKey;

    private HttpServer httpServer;
    private ExecutorService executor;
    private byte[] packBytes;
    private byte[] pack736Bytes;
    private byte[] pack751Bytes;
    private byte[] pack753Bytes;
    private byte[] pack754Bytes;
    private byte[] pack755Bytes;
    private byte[] pack756Bytes;
    private byte[] pack757Bytes;
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
    /** {@link #start()} が返した本体のパック（765用）。開始前は null。 */
    private ResourcePack mainPack;

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
        this(packFile, sha1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, pack763File, sha1763, pack764File, sha1764,
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
        this(packFile, sha1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, pack773File, sha1773, pack774File, sha1774, null, null,
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
    public ResourcePackServer(Path packFile, String sha1, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack763File, String sha1763,
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
    public ResourcePackServer(Path packFile, String sha1, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack762File, String sha1762,
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
    public ResourcePackServer(Path packFile, String sha1, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack759File, String sha1759,
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
    public ResourcePackServer(Path packFile, String sha1, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack760File, String sha1760,
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
    public ResourcePackServer(Path packFile, String sha1, Path pack736File, String sha1736, Path pack751File, String sha1751, Path pack753File, String sha1753, Path pack754File, String sha1754, Path pack755File, String sha1755, Path pack756File, String sha1756, Path pack757File, String sha1757, Path pack761File, String sha1761,
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
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null,
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
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
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

    /**
     * Convenience: serve the 1.20.4 pack and its 1.18 / 1.18.1, 1.19, 1.19.1 / 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8, 1.21.9／1.21.10, 1.21.11 and 26.1 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
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
        this(built, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all eighteen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
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
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, built757.file(), built757.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all nineteen packs. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
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
        this(built, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all nineteen packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
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
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, built756.file(), built756.sha1(), built757.file(), built757.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);
    }

    /** Convenience: serve all twenty packs. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
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
        this(built, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all twenty packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built755, ResourcePackBuilder.BuiltPack built756, ResourcePackBuilder.BuiltPack built757, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,
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
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, built755.file(), built755.sha1(), built756.file(), built756.sha1(), built757.file(), built757.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);
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

    /** Convenience: serve all twenty-one packs, with an explicit prompt key. */
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
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, null, null, built754.file(), built754.sha1(), built755.file(), built755.sha1(), built756.file(), built756.sha1(), built757.file(), built757.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);
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

    /** Convenience: serve all twenty-two packs, with an explicit prompt key. */
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
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, null, null, built753.file(), built753.sha1(), built754.file(), built754.sha1(), built755.file(), built755.sha1(), built756.file(), built756.sha1(), built757.file(), built757.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);
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

    /** Convenience: serve all twenty-three packs, with an explicit prompt key. */
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
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), null, null, built751.file(), built751.sha1(), built753.file(), built753.sha1(), built754.file(), built754.sha1(), built755.file(), built755.sha1(), built756.file(), built756.sha1(), built757.file(), built757.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);
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

    /** Convenience: serve all twenty-four packs, with an explicit prompt key. */
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
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built736.file(), built736.sha1(), built751.file(), built751.sha1(), built753.file(), built753.sha1(), built754.file(), built754.sha1(), built755.file(), built755.sha1(), built756.file(), built756.sha1(), built757.file(), built757.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                built775.file(), built775.sha1(),
                port, required, promptKey);
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
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, built760.file(), built760.sha1(),
                built761.file(), built761.sha1(), built762.file(), built762.sha1(),
                built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null,
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
        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, built761.file(), built761.sha1(),
                built762.file(), built762.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(), built774.file(), built774.sha1(),
                null, null,
                port, required, promptKey);
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
        // Bind explicitly to IPv4 loopback (see class javadoc).
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", requestedPort), 0);
        httpServer.createContext(PACK_PATH, exchange -> serveBytes(exchange, packBytes));
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
        pack736Bytes = null;
        pack751Bytes = null;
        pack753Bytes = null;
        pack754Bytes = null;
        pack755Bytes = null;
        pack756Bytes = null;
        pack757Bytes = null;
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

    /**
     * クライアントのプロトコルに合ったリソースパックを返す。
     * 751→{@link #pack751()}（pack_format 6。無い場合は753パック、さらに無い場合は754パック、
     * さらに無い場合は755パック、さらに無い場合は756パック、さらに無い場合は757パック、
     * さらに無い場合は759パック、さらに無い場合は760パック、さらに無い場合は761パック、
     * さらに無い場合は762パック、さらに無い場合は763パックに代替）、
     * 753→{@link #pack753()}（pack_format 6。無い場合は754パック、さらに無い場合は755パック、
     * さらに無い場合は756パック、さらに無い場合は757パック、さらに無い場合は759パック、
     * さらに無い場合は760パック、さらに無い場合は761パック、さらに無い場合は762パック、
     * さらに無い場合は763パックに代替）、
     * 754→{@link #pack754()}（pack_format 6。無い場合は755パック、さらに無い場合は756パック、
     * さらに無い場合は757パック、さらに無い場合は759パック、さらに無い場合は760パック、
     * さらに無い場合は761パック、さらに無い場合は762パック、さらに無い場合は763パックに代替）、
     * 755→{@link #pack755()}（pack_format 7。無い場合は756パック、さらに無い場合は757パック、
     * さらに無い場合は759パック、さらに無い場合は760パック、さらに無い場合は761パック、
     * さらに無い場合は762パック、さらに無い場合は763パックに代替）、
     * 756→{@link #pack756()}（pack_format 7。無い場合は757パック、さらに無い場合は759パック、
     * さらに無い場合は760パック、さらに無い場合は761パック、さらに無い場合は762パック、
     * さらに無い場合は763パックに代替）、
     * 757→{@link #pack757()}（pack_format 8。無い場合は759パック、さらに無い場合は760パック、
     * さらに無い場合は761パック、さらに無い場合は762パック、さらに無い場合は763パックに代替）、
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
     * 765→{@link #start()} が返した本体（pack_format 22）。開始前は本体が
     * 無いため 765 には null を返す。
     *
     * <p>出し分けの基準はクライアントのプロトコルであって、内部サーバーの
     * バージョンではない。内部が764でも765クライアントには本体（22）を
     * 出す。内部サーバー自身（server.properties）に書くパックも、同じ
     * 基準で {@code packForProtocol(serverProtocol)} を使って選ぶこと。
     */
    public synchronized ResourcePack packForProtocol(int clientProtocol) {
        if (clientProtocol == ProtocolVersion.V1_16_1) {
            // 736パックが無い旧コンストラクタでは751パック、さらに無い場合は753パック、
            // さらに無い場合は754パック、さらに無い場合は755パック、さらに無い場合は756パック、さらに無い場合は757パック、
            // さらに無い場合は759パック、さらに無い場合は760パック、さらに無い場合は761パック、
            // さらに無い場合は762パック、さらに無い場合は763パックに代替する。
            // 736は今のツリー内最古のため、隣接する新しい pack751 に落とす
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
