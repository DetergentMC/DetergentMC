package io.detergentmc.protocol;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Builds the DetergentMC demo resource pack as a zip file.
 *
 * <p>Every asset is generated in code — no binary assets are committed to
 * the repository and no texture is copied from Mojang or anyone else.
 * The pack is a deliberately honest demo for Minecraft 1.20.4
 * ({@code pack_format} 22):
 *
 * <ul>
 *   <li>a 16x16 bubble texture, drawn with {@link BufferedImage}, at
 *       {@code assets/detergentmc/textures/item/bubble.png};</li>
 *   <li>an item model for it at
 *       {@code assets/detergentmc/models/item/bubble.json};</li>
 *   <li>an override of the vanilla slimeball model
 *       ({@code assets/minecraft/models/item/slimeball.json}) so a
 *       slimeball with {@code CustomModelData:1} renders as the bubble.
 *       That is all the demo claims: a slimeball that <em>looks</em> like
 *       a bubble, not a new item id.</li>
 * </ul>
 *
 * <p>The matching block-side demo lives in the content module
 * ({@code CustomBlockRegistry#demoBubbleBlock()}): a
 * {@code detergentmc:bubble_block} shown to clients as a vanilla slime
 * block. It is likewise only a look + a mapping table — vanilla has no
 * such block id, so no new block id is ever sent to a client.
 *
 * <h2>1.17.1 variant (protocol 756, {@code pack_format} 7)</h2>
 *
 * <p>The same demo assets are also built as a 1.17.1 pack
 * ({@link #build756(Path)}): the only difference is
 * {@code pack.mcmeta} ({@code pack_format} 7 instead of 22).
 * 1.17.1 uses the plain {@code pack_format} integer form: the 1.17.1
 * server jar's {@code PackMetadataSectionSerializer} (obfuscated
 * {@code adb}) reads only {@code description} and {@code pack_format},
 * and no class in the jar mentions {@code min_format} /
 * {@code max_format} (those arrived with the range form in a later
 * version), so the plain form is the correct one here — unlike 26.3,
 * which needs the range form. The jar's {@code SharedConstants}
 * (obfuscated {@code ab}) carries the format as 7, and its
 * {@code version.json} says {@code pack_version.resource: 7}.
 *
 * <h2>1.18 / 1.18.1 variant (protocol 757, {@code pack_format} 8)</h2>
 *
 * <p>The same demo assets are also built as a 1.18 / 1.18.1 pack
 * ({@link #build757(Path)}): the only difference is
 * {@code pack.mcmeta} ({@code pack_format} 8 instead of 22).
 * 1.18 uses the plain {@code pack_format} integer form: the 1.18
 * server jar's {@code PackMetadataSectionSerializer} reads only
 * {@code description} and {@code pack_format}, and no class in the jar
 * mentions {@code min_format} / {@code max_format} (those arrived with
 * the range form in a later version), so the plain form is the correct
 * one here — unlike 26.3, which needs the range form. One pack serves
 * both 1.18 and 1.18.1: they share protocol 757 and format 8.
 *
 * <h2>1.19 variant (protocol 759, {@code pack_format} 9)</h2>
 *
 * <p>The same demo assets are also built as a 1.19 pack
 * ({@link #build759(Path)}): the only difference is
 * {@code pack.mcmeta} ({@code pack_format} 9 instead of 22).
 * 1.19 and 1.19.1 / 1.19.2 share the pack format, so the two packs
 * have the same {@code pack.mcmeta}; they are separate files so each
 * client is offered the pack matching its protocol version
 * (see {@link PlayTranslator#buildPlayResourcePack763}).
 *
 * <h2>1.19.1 / 1.19.2 variant (protocol 760, {@code pack_format} 9)</h2>
 *
 * <p>The same demo assets are also built as a 1.19.1 / 1.19.2 pack
 * ({@link #build760(Path)}): the only difference is
 * {@code pack.mcmeta} ({@code pack_format} 9 instead of 22).
 * A 1.19.1 / 1.19.2 client offered the format-12 pack would show it as
 * incompatible, which is why the proxy offers each client the pack
 * matching its protocol version
 * (see {@link PlayTranslator#buildPlayResourcePack763}).
 *
 * <h2>1.19.3 variant (protocol 761, {@code pack_format} 12)</h2>
 *
 * <p>The same demo assets are also built as a 1.19.3 pack
 * ({@link #build761(Path)}): the only difference is
 * {@code pack.mcmeta} ({@code pack_format} 12 instead of 22).
 * A 1.19.3 client offered the format-13 pack would show it as
 * incompatible, which is why the proxy offers each client the pack
 * matching its protocol version
 * (see {@link PlayTranslator#buildPlayResourcePack763}).
 *
 * <h2>1.19.4 variant (protocol 762, {@code pack_format} 13)</h2>
 *
 * <p>The same demo assets are also built as a 1.19.4 pack
 * ({@link #build762(Path)}): the only difference is
 * {@code pack.mcmeta} ({@code pack_format} 13 instead of 22). The demo
 * assets themselves contain no 1.20-only constructs — the
 * {@code item/generated} parent and the {@code custom_model_data}
 * override predicate have existed since long before 1.19.4 — so nothing
 * has to be removed or replaced for 1.19.4; the format number is what
 * a 1.19.4 client checks. A 1.19.4 client offered the format-15 pack
 * would show it as incompatible, which is why the proxy offers each
 * client the pack matching its protocol version
 * (see {@link PlayTranslator#buildPlayResourcePack763}).
 *
 * <h2>1.20.1 variant (protocol 763, {@code pack_format} 15)</h2>
 *
 * <p>The same demo assets are also built as a 1.20.1 pack
 * ({@link #build763(Path)}): the only difference is
 * {@code pack.mcmeta} ({@code pack_format} 15 instead of 22). The demo
 * assets themselves contain no 1.20.3-only constructs — the
 * {@code item/generated} parent and the {@code custom_model_data}
 * override predicate have existed since long before 1.20.3 — so nothing
 * has to be removed or replaced for 1.20.1; the format number is what
 * a 1.20.1 client checks. A 1.20.1 client offered the format-22 pack
 * would show it as incompatible, which is why the proxy offers each
 * client the pack matching its protocol version
 * (see {@link PlayTranslator#buildPlayResourcePack763}).
 *
 * <h2>1.20.2 variant (protocol 764, {@code pack_format} 18)</h2>
 *
 * <p>The same demo assets are also built as a 1.20.2 pack
 * ({@link #build764(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 18). The 1.20.2 resource-pack format is 18, not
 * 15: Minecraft Wiki's pack_format table lists 18 for 1.20.2
 * (1.20.2-pre2–23w41a), while 15 covers 1.20–1.20.1 and 22 covers
 * 1.20.3–1.20.4. A 1.20.2 client offered the format-15 pack would show
 * it as incompatible, so 764 clients get their own pack. (The data-pack
 * format for 1.20.2 is also 18, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 * The demo assets need nothing else: 1.20.2's new
 * {@code supported_formats} range field is optional, and the assets
 * contain no 1.20.3-only constructs.
 *
 * <h2>1.20.5 / 1.20.6 variant (protocol 766, {@code pack_format} 32)</h2>
 *
 * <p>The same demo assets are also built as a 1.20.5 pack
 * ({@link #build766(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 32). The 1.20.5 / 1.20.6 resource-pack format is
 * 32, not 22: Minecraft Wiki's pack_format table lists 32 for
 * 1.20.5–1.20.6, while 22 covers 1.20.3–1.20.4 and 18 covers 1.20.2.
 * A 1.20.5 client offered the format-22 pack would show it as
 * incompatible, so 766 clients get their own pack. 1.20.5 and 1.20.6
 * share the format (and the protocol), so one pack serves both
 * ({@code detergentmc-pack-1.20.5.zip}). The demo assets need nothing
 * else: they contain no 1.20.5-only constructs — the new vault block
 * and wolf armour textures of format 32 are additions, not changes to
 * the item/generated model or the custom_model_data predicate this
 * demo uses. (The data-pack format for 1.20.5 / 1.20.6 is 41, but
 * that is a separate number — see {@code DatapackGenerator} — and
 * must not be confused with this one.)
 *
 * <h2>1.21 / 1.21.1 variant (protocol 767, {@code pack_format} 34)</h2>
 *
 * <p>The same demo assets are also built as a 1.21 pack
 * ({@link #build767(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 34). The 1.21 / 1.21.1 resource-pack format is
 * 34, not 32: Minecraft Wiki's pack_format table lists 34 for
 * 1.21–1.21.1, while 32 covers 1.20.5–1.20.6 and 22 covers 1.20.3–1.20.4.
 * A 1.21 client offered the format-32 pack would show it as
 * incompatible, so 767 clients get their own pack. 1.21 and 1.21.1
 * share the format (and the protocol), so one pack serves both
 * ({@code detergentmc-pack-1.21.zip}). The demo assets need nothing
 * else: they contain no 1.21-only constructs. (The data-pack format
 * for 1.21 / 1.21.1 is 48, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 *
 * <h2>1.21.2 / 1.21.3 variant (protocol 768, {@code pack_format} 42)</h2>
 *
 * <p>The same demo assets are also built as a 1.21.2 pack
 * ({@link #build768(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 42). The 1.21.2 / 1.21.3 resource-pack format is
 * 42, not 34: Minecraft Wiki's pack_format table lists 42 for
 * 1.21.2–1.21.3. A 1.21.2 client offered the format-34 pack would show
 * it as incompatible, so 768 clients get their own pack. 1.21.2 and
 * 1.21.3 share the format (and the protocol), so one pack serves both
 * ({@code detergentmc-pack-1.21.2.zip}). (The data-pack format
 * for 1.21.2 / 1.21.3 is 57, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 *
 * <h2>1.21.4 variant (protocol 769, {@code pack_format} 46)</h2>
 *
 * <p>The same demo assets are also built as a 1.21.4 pack
 * ({@link #build769(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 46). The 1.21.4 resource-pack format is
 * 46, not 42: Minecraft Wiki's pack_format table lists 46 for
 * 1.21.4. A 1.21.4 client offered the format-42 pack would show
 * it as incompatible, so 769 clients get their own pack. (The data-pack format
 * for 1.21.4 is 61, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 *
 * <h2>1.21.5 variant (protocol 770, {@code pack_format} 55)</h2>
 *
 * <p>The same demo assets are also built as a 1.21.5 pack
 * ({@link #build770(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 55). The 1.21.5 resource-pack format is
 * 55, not 46: Minecraft Wiki's pack_format table lists 55 for
 * 1.21.5. A 1.21.5 client offered the format-46 pack would show
 * it as incompatible, so 770 clients get their own pack. (The data-pack format
 * for 1.21.5 is 71, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 *
 * <h2>1.21.6 variant (protocol 771, {@code pack_format} 63)</h2>
 *
 * <p>The same demo assets are also built as a 1.21.6 pack
 * ({@link #build771(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 63). The 1.21.6 resource-pack format is
 * 63, not 55: Minecraft Wiki's pack_format table lists 63 for
 * 1.21.6. A 1.21.6 client offered the format-55 pack would show
 * it as incompatible, so 771 clients get their own pack. (The data-pack format
 * for 1.21.6 is 80, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 *
 * <h2>1.21.7／1.21.8 variant (protocol 772, {@code pack_format} 64)</h2>
 *
 * <p>The same demo assets are also built as a 1.21.7 pack
 * ({@link #build772(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 64). The 1.21.7 resource-pack format is
 * 64, not 63: Minecraft Wiki's pack_format table lists 64 for
 * 1.21.7. A 1.21.7／1.21.8 client offered the format-63 pack would show
 * it as incompatible, so 772 clients get their own pack. (The data-pack format
 * for 1.21.7／1.21.8 is 81, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 *
 * <h2>1.21.9／1.21.10 variant (protocol 773, {@code pack_format} 69)</h2>
 *
 * <p>The same demo assets are also built as a 1.21.9 pack
 * ({@link #build773(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 69). The 1.21.9 resource-pack format is
 * 69, not 64: Minecraft Wiki's pack_format table lists 69 for
 * 1.21.9. A 1.21.9／1.21.10 client offered the format-64 pack would show
 * it as incompatible, so 773 clients get their own pack. (The data-pack format
 * for 1.21.9／1.21.10 is 88, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 */
public final class ResourcePackBuilder {

    /**
     * Minecraft 1.17.1 resource-pack format. 1.17.1 server jar
     * (javap): {@code SharedConstants} (obfuscated {@code ab}) carries
     * the resource-pack format as 7, {@code version.json} says
     * {@code pack_version.resource: 7}, and
     * {@code PackMetadataSectionSerializer} (obfuscated {@code adb})
     * reads only {@code description} and {@code pack_format} — the
     * plain integer form.
     */
    public static final int PACK_FORMAT_1_17_1 = 7;

    /**
     * Minecraft 1.18 / 1.18.1 resource-pack format. 1.18 server jar
     * (javap): {@code SharedConstants.RESOURCE_PACK_FORMAT = 8}, and
     * {@code PackMetadataSectionSerializer.fromJson} reads only
     * {@code description} and {@code pack_format} — the plain integer
     * form. One pack serves both versions: they share protocol 757.
     */
    public static final int PACK_FORMAT_1_18 = 8;

    /**
     * Minecraft 1.19 resource-pack format (Minecraft Wiki: pack_format 9).
     * 1.19 and 1.19.1 / 1.19.2 share the format, so this is the same
     * value as {@link #PACK_FORMAT_1_19_1}; it exists so 759 call sites
     * can name the version they mean.
     */
    public static final int PACK_FORMAT_1_19 = 9;

    /** Minecraft 1.19.1 / 1.19.2 resource-pack format (Minecraft Wiki: pack_format 9). */
    public static final int PACK_FORMAT_1_19_1 = 9;

    /** Minecraft 1.19.3 resource-pack format (Minecraft Wiki: pack_format 12). */
    public static final int PACK_FORMAT_1_19_3 = 12;

    /** Minecraft 1.19.4 resource-pack format (Minecraft Wiki: pack_format 13). */
    public static final int PACK_FORMAT_1_19_4 = 13;

    /** Minecraft 1.20.4 resource-pack format. */
    public static final int PACK_FORMAT_1_20_4 = 22;

    /** Minecraft 1.20.1 resource-pack format. */
    public static final int PACK_FORMAT_1_20_1 = 15;

    /** Minecraft 1.20.2 resource-pack format (Minecraft Wiki: pack_format 18). */
    public static final int PACK_FORMAT_1_20_2 = 18;

    /** Minecraft 1.20.5 / 1.20.6 resource-pack format (Minecraft Wiki: pack_format 32). */
    public static final int PACK_FORMAT_1_20_5 = 32;

    /** Minecraft 1.21 / 1.21.1 resource-pack format (Minecraft Wiki: pack_format 34). */
    public static final int PACK_FORMAT_1_21 = 34;

    /** Minecraft 1.21.2 / 1.21.3 resource-pack format (Minecraft Wiki: pack_format 42). */
    public static final int PACK_FORMAT_1_21_2 = 42;

    /** Minecraft 1.21.4 resource-pack format (Minecraft Wiki: pack_format 46). */
    public static final int PACK_FORMAT_1_21_4 = 46;

    /** Minecraft 1.21.5 resource-pack format (Minecraft Wiki: pack_format 55). */
    public static final int PACK_FORMAT_1_21_5 = 55;

    /** Minecraft 1.21.6 resource-pack format (Minecraft Wiki: pack_format 63). */
    public static final int PACK_FORMAT_1_21_6 = 63;

    /** Minecraft 1.21.7 / 1.21.8 resource-pack format (Minecraft Wiki: pack_format 64). */
    public static final int PACK_FORMAT_1_21_7 = 64;

    /** Minecraft 1.21.9 / 1.21.10 resource-pack format (Minecraft Wiki: pack_format 69). */
    public static final int PACK_FORMAT_1_21_9 = 69;

    /** Minecraft 1.21.11 resource-pack format (Minecraft Wiki: pack_format 75). */
    public static final int PACK_FORMAT_1_21_11 = 75;

    /** Minecraft 26.1 resource-pack format (26.1 server jar version.json: pack resource 84.0). */
    public static final int PACK_FORMAT_26_1 = 84;

    /** File name of the generated pack inside the output directory. */
    public static final String PACK_FILE_NAME = "detergentmc-pack.zip";

    /** File name of the generated 1.17.1 pack inside the output directory. */
    public static final String PACK_FILE_NAME_756 = "detergentmc-pack-1.17.1.zip";

    /** File name of the generated 1.18 / 1.18.1 pack inside the output directory.
     * One pack serves both versions: they share protocol 757 and
     * resource-pack format 8.
     */
    public static final String PACK_FILE_NAME_757 = "detergentmc-pack-1.18.zip";

    /** File name of the generated 1.19 pack inside the output directory. */
    public static final String PACK_FILE_NAME_759 = "detergentmc-pack-1.19.zip";

    /** File name of the generated 1.19.1 / 1.19.2 pack inside the output directory. */
    public static final String PACK_FILE_NAME_760 = "detergentmc-pack-1.19.1.zip";

    /** File name of the generated 1.19.3 pack inside the output directory. */
    public static final String PACK_FILE_NAME_761 = "detergentmc-pack-1.19.3.zip";

    /** File name of the generated 1.19.4 pack inside the output directory. */
    public static final String PACK_FILE_NAME_762 = "detergentmc-pack-1.19.4.zip";

    /** File name of the generated 1.20.1 pack inside the output directory. */
    public static final String PACK_FILE_NAME_763 = "detergentmc-pack-1.20.1.zip";

    /** File name of the generated 1.20.2 pack inside the output directory. */
    public static final String PACK_FILE_NAME_764 = "detergentmc-pack-1.20.2.zip";

    /**
     * File name of the generated 1.20.5 / 1.20.6 pack inside the output
     * directory. One pack serves both versions: they share protocol 766
     * and resource-pack format 32.
     */
    public static final String PACK_FILE_NAME_766 = "detergentmc-pack-1.20.5.zip";

    /** File name of the generated 1.21 / 1.21.1 pack inside the output
     * directory. One pack serves both versions: they share protocol 767
     * and resource-pack format 34.
     */
    public static final String PACK_FILE_NAME_767 = "detergentmc-pack-1.21.zip";

    /**
     * File name of the generated 1.21.2 / 1.21.3 pack inside the output
     * directory. One pack serves both versions: they share protocol 768
     * and resource-pack format 42.
     */
    public static final String PACK_FILE_NAME_768 = "detergentmc-pack-1.21.2.zip";

    /** File name of the generated 1.21.4 pack inside the output directory
     * (protocol 769, resource-pack format 46).
     */
    public static final String PACK_FILE_NAME_769 = "detergentmc-pack-1.21.4.zip";

    /** File name of the generated 1.21.5 pack inside the output directory
     * (protocol 770, resource-pack format 55).
     */
    public static final String PACK_FILE_NAME_770 = "detergentmc-pack-1.21.5.zip";

    /** File name of the generated 1.21.6 pack inside the output directory
     * (protocol 771, resource-pack format 63).
     */
    public static final String PACK_FILE_NAME_771 = "detergentmc-pack-1.21.6.zip";

    /** File name of the generated 1.21.7 pack inside the output directory
     * (protocol 772, resource-pack format 64). One pack serves both 1.21.7
     * and 1.21.8: they share protocol 772 and resource-pack format 64.
     */
    public static final String PACK_FILE_NAME_772 = "detergentmc-pack-1.21.7.zip";

    /** File name of the generated 1.21.9 pack inside the output directory
     * (protocol 773, resource-pack format 69). One pack serves both 1.21.9
     * and 1.21.10: they share protocol 773 and resource-pack format 64.
     */
    public static final String PACK_FILE_NAME_773 = "detergentmc-pack-1.21.9.zip";

    /** File name of the generated 1.21.11 pack inside the output directory
     * (protocol 774, resource-pack format 75).
     */
    public static final String PACK_FILE_NAME_774 = "detergentmc-pack-1.21.11.zip";

    /** File name of the generated 26.1 pack inside the output directory
     * (protocol 775, resource-pack format 84). One pack serves 26.1,
     * 26.1.1 and 26.1.2: they share protocol 775 and resource-pack format 84.
     */
    public static final String PACK_FILE_NAME_775 = "detergentmc-pack-26.1.zip";

    /** Path of the bubble texture inside the pack. */
    public static final String BUBBLE_TEXTURE_PATH =
            "assets/detergentmc/textures/item/bubble.png";

    /** Path of the bubble item model inside the pack. */
    public static final String BUBBLE_MODEL_PATH =
            "assets/detergentmc/models/item/bubble.json";

    /** Path of the overridden vanilla slimeball model inside the pack. */
    public static final String SLIMEBALL_MODEL_PATH =
            "assets/minecraft/models/item/slimeball.json";

    /** {@code CustomModelData} value that turns a slimeball into a bubble. */
    public static final int BUBBLE_CUSTOM_MODEL_DATA = 1;

    private static final String PACK_MCMETA = "{\"pack\":{\"pack_format\":22,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_756 = "{\"pack\":{\"pack_format\":7,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_757 = "{\"pack\":{\"pack_format\":8,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_760 = "{\"pack\":{\"pack_format\":9,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_761 = "{\"pack\":{\"pack_format\":12,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_762 = "{\"pack\":{\"pack_format\":13,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_763 = "{\"pack\":{\"pack_format\":15,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_764 = "{\"pack\":{\"pack_format\":18,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_766 = "{\"pack\":{\"pack_format\":32,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_767 = "{\"pack\":{\"pack_format\":34,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_768 = "{\"pack\":{\"pack_format\":42,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_769 = "{\"pack\":{\"pack_format\":46,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_770 = "{\"pack\":{\"pack_format\":55,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_771 = "{\"pack\":{\"pack_format\":63,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_772 = "{\"pack\":{\"pack_format\":64,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_773 = "{\"pack\":{\"pack_format\":69,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_774 = "{\"pack\":{\"pack_format\":75,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String PACK_MCMETA_775 = "{\"pack\":{\"pack_format\":84,"
            + "\"description\":\"DetergentMC resource pack\"}}";

    private static final String BUBBLE_MODEL_JSON = "{\"parent\":\"item/generated\","
            + "\"textures\":{\"layer0\":\"detergentmc:item/bubble\"}}";

    private static final String SLIMEBALL_MODEL_JSON = "{\"parent\":\"item/generated\","
            + "\"textures\":{\"layer0\":\"minecraft:item/slimeball\"},"
            + "\"overrides\":[{\"predicate\":{\"custom_model_data\":1},"
            + "\"model\":\"detergentmc:item/bubble\"}]}";

    /** A built pack: the zip file on disk and its lower-case hex SHA-1. */
    public record BuiltPack(Path file, String sha1) {
        public BuiltPack {
            if (file == null) {
                throw new IllegalArgumentException("BuiltPack file is required");
            }
            if (sha1 == null || sha1.isBlank()) {
                throw new IllegalArgumentException("BuiltPack sha1 is required");
            }
        }
    }

    /**
     * The resource-pack format for a client protocol version:
     * 7 for 756 (1.17.1), 8 for 757 (1.18 / 1.18.1), 9 for 759 (1.19) and 760 (1.19.1 / 1.19.2), 12 for 761 (1.19.3), 13 for 762 (1.19.4), 15 for 763 (1.20.1), 18 for 764 (1.20.2), 22 for 765 (1.20.4),
     * 32 for 766 (1.20.5 / 1.20.6), 34 for 767 (1.21 / 1.21.1),
     * 42 for 768 (1.21.2 / 1.21.3), 46 for 769 (1.21.4),
     * 55 for 770 (1.21.5), 63 for 771 (1.21.6), 64 for 772 (1.21.7／1.21.8),
     * 69 for 773 (1.21.9／1.21.10), 75 for 774 (1.21.11), 84 for 775 (26.1).
     *
     * @throws IllegalArgumentException for any other protocol version
     */
    public static int packFormatForProtocol(int protocolVersion) {
        return switch (protocolVersion) {
            case ProtocolVersion.V1_17_1 -> PACK_FORMAT_1_17_1;
            case ProtocolVersion.V1_18 -> PACK_FORMAT_1_18;
            case ProtocolVersion.V1_19 -> PACK_FORMAT_1_19;
            case ProtocolVersion.V1_19_1 -> PACK_FORMAT_1_19_1;
            case ProtocolVersion.V1_19_3 -> PACK_FORMAT_1_19_3;
            case ProtocolVersion.V1_19_4 -> PACK_FORMAT_1_19_4;
            case ProtocolVersion.V1_20_1 -> PACK_FORMAT_1_20_1;
            case ProtocolVersion.V1_20_2 -> PACK_FORMAT_1_20_2;
            case ProtocolVersion.V1_20_4 -> PACK_FORMAT_1_20_4;
            case ProtocolVersion.V1_20_5 -> PACK_FORMAT_1_20_5;
            case ProtocolVersion.V1_21 -> PACK_FORMAT_1_21;
            case ProtocolVersion.V1_21_2 -> PACK_FORMAT_1_21_2;
            case ProtocolVersion.V1_21_4 -> PACK_FORMAT_1_21_4;
            case ProtocolVersion.V1_21_5 -> PACK_FORMAT_1_21_5;
            case ProtocolVersion.V1_21_6 -> PACK_FORMAT_1_21_6;
            case ProtocolVersion.V1_21_7 -> PACK_FORMAT_1_21_7;
            case ProtocolVersion.V1_21_9 -> PACK_FORMAT_1_21_9;
            case ProtocolVersion.V1_21_11 -> PACK_FORMAT_1_21_11;
            case ProtocolVersion.V26_1 -> PACK_FORMAT_26_1;
            default -> throw new IllegalArgumentException(
                    "No resource-pack format known for protocol " + protocolVersion);
        };
    }

    /**
     * Builds the pack into {@code outputDir/detergentmc-pack.zip}
     * (creating the directory if needed) and returns the file plus its
     * SHA-1, which is what vanilla writes into
     * {@code server.properties} as {@code resource-pack-sha1}.
     */
    public BuiltPack build(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_20_4, PACK_FILE_NAME);
    }

    /**
     * Builds the 1.17.1 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.17.1.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 7, plain integer
     * form — 1.17.1 has no {@code min_format} / {@code max_format}).
     */
    public BuiltPack build756(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_17_1, PACK_FILE_NAME_756);
    }

    /**
     * Builds the 1.18 / 1.18.1 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.18.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 8, plain integer
     * form — 1.18 has no {@code min_format} / {@code max_format}).
     * One pack serves both versions: they share protocol 757.
     */
    public BuiltPack build757(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_18, PACK_FILE_NAME_757);
    }

    /**
     * Builds the 1.19 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.19.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 9 — the same
     * format as 1.19.1 / 1.19.2).
     */
    public BuiltPack build759(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_19, PACK_FILE_NAME_759);
    }

    /**
     * Builds the 1.19.1 / 1.19.2 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.19.1.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 9).
     */
    public BuiltPack build760(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_19_1, PACK_FILE_NAME_760);
    }

    /**
     * Builds the 1.19.3 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.19.3.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 12).
     */
    public BuiltPack build761(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_19_3, PACK_FILE_NAME_761);
    }

    /**
     * Builds the 1.19.4 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.19.4.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 13).
     */
    public BuiltPack build762(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_19_4, PACK_FILE_NAME_762);
    }

    /**
     * Builds the 1.20.1 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.20.1.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 15).
     */
    public BuiltPack build763(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_20_1, PACK_FILE_NAME_763);
    }

    /**
     * Builds the 1.20.2 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.20.2.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 18).
     */
    public BuiltPack build764(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_20_2, PACK_FILE_NAME_764);
    }

    /**
     * Builds the 1.20.5 / 1.20.6 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.20.5.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 32). One pack
     * serves both versions: they share protocol 766 and the format.
     */
    public BuiltPack build766(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_20_5, PACK_FILE_NAME_766);
    }

    /**
     * Builds the 1.21 / 1.21.1 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 34). One pack
     * serves both versions: they share protocol 767 and the format.
     */
    public BuiltPack build767(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21, PACK_FILE_NAME_767);
    }

    /**
     * Builds the 1.21.2 / 1.21.3 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.2.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 42). One pack
     * serves both versions: they share protocol 768 and the format.
     */
    public BuiltPack build768(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_2, PACK_FILE_NAME_768);
    }

    /**
     * Builds the 1.21.4 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.4.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 46).
     */
    public BuiltPack build769(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_4, PACK_FILE_NAME_769);
    }

    /**
     * Builds the 1.21.5 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.5.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 55).
     */
    public BuiltPack build770(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_5, PACK_FILE_NAME_770);
    }

    /**
     * Builds the 1.21.6 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.6.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 63).
     */
    public BuiltPack build771(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_6, PACK_FILE_NAME_771);
    }

    /**
     * Builds the 1.21.7 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.7.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 64). One pack serves
     * both 1.21.7 and 1.21.8: they share protocol 772.
     */
    public BuiltPack build772(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_7, PACK_FILE_NAME_772);
    }

    /**
     * Builds the 1.21.9 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.9.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 69). One pack serves
     * both 1.21.9 and 1.21.10: they share protocol 773.
     */
    public BuiltPack build773(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_9, PACK_FILE_NAME_773);
    }

    /**
     * Builds the 1.21.11 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.11.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 75).
     */
    public BuiltPack build774(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_11, PACK_FILE_NAME_774);
    }

    /**
     * Builds the 26.1 variant of the pack into
     * {@code outputDir/detergentmc-pack-26.1.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 84). One pack serves
     * 26.1, 26.1.1 and 26.1.2: they share protocol 775.
     */
    public BuiltPack build775(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_26_1, PACK_FILE_NAME_775);
    }

    private BuiltPack build(Path outputDir, int packFormat, String fileName)
            throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is required");
        }
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(fileName);
        writePack(file, packFormat);
        return new BuiltPack(file, sha1Hex(file));
    }

    /** Writes the pack zip to {@code file}. */
    public void writePack(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_20_4);
    }

    /** Writes the 1.17.1 variant of the pack zip to {@code file}. */
    public void writePack756(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_17_1);
    }

    /** Writes the 1.18 / 1.18.1 variant of the pack zip to {@code file}. */
    public void writePack757(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_18);
    }

    /** Writes the 1.19 variant of the pack zip to {@code file}. */
    public void writePack759(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_19);
    }

    /** Writes the 1.19.3 variant of the pack zip to {@code file}. */
    public void writePack761(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_19_3);
    }

    /** Writes the 1.19.4 variant of the pack zip to {@code file}. */
    public void writePack762(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_19_4);
    }

    /** Writes the 1.20.1 variant of the pack zip to {@code file}. */
    public void writePack763(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_20_1);
    }

    /** Writes the 1.20.2 variant of the pack zip to {@code file}. */
    public void writePack764(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_20_2);
    }

    /** Writes the 1.20.5 / 1.20.6 variant of the pack zip to {@code file}. */
    public void writePack766(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_20_5);
    }

    /** Writes the 1.21 / 1.21.1 variant of the pack zip to {@code file}. */
    public void writePack767(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21);
    }

    /** Writes the 1.21.2 / 1.21.3 variant of the pack zip to {@code file}. */
    public void writePack768(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_2);
    }

    /** Writes the 1.21.4 variant of the pack zip to {@code file}. */
    public void writePack769(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_4);
    }

    /** Writes the 1.21.5 variant of the pack zip to {@code file}. */
    public void writePack770(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_5);
    }

    /** Writes the 1.21.6 variant of the pack zip to {@code file}. */
    public void writePack771(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_6);
    }

    /** Writes the 1.21.7 variant of the pack zip to {@code file}. */
    public void writePack772(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_7);
    }

    /** Writes the 1.21.9 variant of the pack zip to {@code file}. */
    public void writePack773(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_9);
    }

    /** Writes the 1.21.11 variant of the pack zip to {@code file}. */
    public void writePack774(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_11);
    }

    /** Writes the 26.1 variant of the pack zip to {@code file}. */
    public void writePack775(Path file) throws IOException {
        writePack(file, PACK_FORMAT_26_1);
    }

    /** Writes the pack zip with the given {@code pack_format} to {@code file}. */
    public void writePack(Path file, int packFormat) throws IOException {
        String mcmeta = switch (packFormat) {
            case PACK_FORMAT_1_17_1 -> PACK_MCMETA_756;
            case PACK_FORMAT_1_18 -> PACK_MCMETA_757;
            case PACK_FORMAT_1_19_1 -> PACK_MCMETA_760;
            case PACK_FORMAT_1_19_3 -> PACK_MCMETA_761;
            case PACK_FORMAT_1_19_4 -> PACK_MCMETA_762;
            case PACK_FORMAT_1_20_1 -> PACK_MCMETA_763;
            case PACK_FORMAT_1_20_2 -> PACK_MCMETA_764;
            case PACK_FORMAT_1_20_4 -> PACK_MCMETA;
            case PACK_FORMAT_1_20_5 -> PACK_MCMETA_766;
            case PACK_FORMAT_1_21 -> PACK_MCMETA_767;
            case PACK_FORMAT_1_21_2 -> PACK_MCMETA_768;
            case PACK_FORMAT_1_21_4 -> PACK_MCMETA_769;
            case PACK_FORMAT_1_21_5 -> PACK_MCMETA_770;
            case PACK_FORMAT_1_21_6 -> PACK_MCMETA_771;
            case PACK_FORMAT_1_21_7 -> PACK_MCMETA_772;
            case PACK_FORMAT_1_21_9 -> PACK_MCMETA_773;
            case PACK_FORMAT_1_21_11 -> PACK_MCMETA_774;
            case PACK_FORMAT_26_1 -> PACK_MCMETA_775;
            default -> throw new IllegalArgumentException(
                    "Unknown resource-pack format: " + packFormat);
        };
        byte[] png = bubblePngBytes();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            addTextEntry(zip, "pack.mcmeta", mcmeta);
            addBinaryEntry(zip, BUBBLE_TEXTURE_PATH, png);
            addTextEntry(zip, BUBBLE_MODEL_PATH, BUBBLE_MODEL_JSON);
            addTextEntry(zip, SLIMEBALL_MODEL_PATH, SLIMEBALL_MODEL_JSON);
        }
    }

    /**
     * Generates the 16x16 bubble texture as PNG bytes: a light-blue bubble
     * (a filled circle with a darker rim) and a white highlight, on a
     * transparent background. Drawn from scratch in code.
     */
    public static byte[] bubblePngBytes() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            // Transparent background; the bubble is a circle.
            g.setColor(new Color(0x7D, 0xD3, 0xFC)); // light sky blue
            g.fillOval(1, 1, 14, 14);
            // Slightly deeper blue lower half, for a bit of depth.
            g.setColor(new Color(0x38, 0xBD, 0xF8));
            g.fillOval(1, 7, 14, 8);
            // Darker rim.
            g.setColor(new Color(0x0E, 0xA5, 0xE9));
            g.drawOval(1, 1, 13, 13);
            // White highlight, top-left.
            g.setColor(Color.WHITE);
            g.fillOval(4, 3, 4, 4);
            g.fillOval(3, 6, 2, 2);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("No PNG writer available");
            }
            return out.toByteArray();
        }
    }

    /** Lower-case hex SHA-1 of a file. */
    public static String sha1Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is guaranteed to exist on every JDK.
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    /** Lower-case hex SHA-1 of bytes (handy for tests / HTTP checks). */
    public static String sha1Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    private static void addTextEntry(ZipOutputStream zip, String name, String text)
            throws IOException {
        addBinaryEntry(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void addBinaryEntry(ZipOutputStream zip, String name, byte[] bytes)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // Fixed timestamp so rebuilding the same pack yields the same zip
        // (and therefore the same SHA-1) instead of depending on the clock.
        entry.setLastModifiedTime(java.nio.file.attribute.FileTime.from(Instant.EPOCH));
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
