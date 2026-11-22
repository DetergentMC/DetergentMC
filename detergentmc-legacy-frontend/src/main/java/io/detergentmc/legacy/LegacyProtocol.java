package io.detergentmc.legacy;

import java.util.List;

/**
 * Protocol numbers of the pre-Netty generations (Minecraft 1.6.4 and
 * older), and the naming discipline that keeps them apart from the
 * Netty-generation numbers used by the rest of DetergentMC.
 *
 * <p><b>Collision discipline.</b> The legacy generations reuse small
 * numbers that mean something completely different in the modern
 * (Netty, 1.7.2+) protocol: legacy protocol 47 is Minecraft 1.4.2,
 * while modern protocol 47 is Minecraft 1.8. To keep the two spaces
 * from ever mixing:
 *
 * <ul>
 *   <li>Legacy protocol numbers are defined ONLY in this module, in
 *       this class and in {@link LegacyPacketTable}. They are never
 *       added to {@code io.detergentmc.protocol.ProtocolVersion} or
 *       {@code io.detergentmc.protocol.PacketIds}.</li>
 *   <li>Constants and methods here carry the {@code LEGACY} /
 *       "legacy" name marker. Code outside this module must refer to
 *       a legacy protocol as "legacy protocol N (Minecraft X)" and
 *       must never pass a legacy number into a modern-protocol
 *       API.</li>
 *   <li>{@code ClientAdapter.supportedClientProtocols()} stays a
 *       Netty-generation list. Legacy coverage is reported separately
 *       through {@link #supportedLegacyProtocols()} and has its own
 *       pinning test, so the "oldest protocol silently drops out"
 *       failure mode of the modern list cannot hide here either.</li>
 * </ul>
 *
 * <p>Measured facts behind these numbers: docs/PROXY-1.6.4.md and
 * the pre-Netty survey (legacy ping section-1 responses from the real
 * vanilla jars).
 */
public final class LegacyProtocol {

    /** Legacy protocol 78 = Minecraft 1.6.4 (measured via legacy ping). */
    public static final int V1_6_4 = 78;

    /** Legacy protocol 74 = Minecraft 1.6.2 (measured via legacy ping). */
    public static final int V1_6_2 = 74;

    /** Legacy protocol 73 = Minecraft 1.6.1 (measured via legacy ping). */
    public static final int V1_6_1 = 73;

    /** Legacy protocol 61 = Minecraft 1.5.2 (measured via legacy ping). */
    public static final int V1_5_2 = 61;

    /** Legacy protocol 60 = Minecraft 1.5.1 and 1.5 (measured via legacy ping). */
    public static final int V1_5_1 = 60;

    /**
     * Legacy protocol 51 = Minecraft 1.4.7 and 1.4.6 (measured via
     * legacy ping). Note the widespread tables that swap 49 and 51
     * are wrong: 51 is 1.4.6/1.4.7, 49 is 1.4.4/1.4.5.
     */
    public static final int V1_4_7 = 51;

    /**
     * Legacy protocol 49 = Minecraft 1.4.5 and 1.4.4 (measured via
     * legacy ping). See the note on {@link #V1_4_7}: the widespread
     * tables swapping 49 and 51 are wrong.
     */
    public static final int V1_4_5 = 49;

    /**
     * Legacy protocol 47 = Minecraft 1.4.2 (measured via legacy
     * ping plus handshake probing). <b>This is NOT the Netty
     * protocol 47 (Minecraft 1.8)</b>: the number lives only in
     * this legacy numbering space, exactly as the class-level
     * collision discipline describes. The constant is named after
     * the version (V1_4_2), never as a bare "47", so the two 47s
     * cannot be confused at a call site.
     */
    public static final int V1_4_2 = 47;

    /**
     * Legacy protocol 39 = Minecraft 1.3.2 and 1.3.1 (measured via
     * legacy ping plus handshake probing: protocol 39 is answered
     * with an encryption request, 38 is kicked "Outdated client!").
     * The widespread tables that put 1.3.1 on protocol 38 are wrong:
     * both 1.3 releases speak 39. Protocol 39 is the encryption
     * boundary generation — 39 and up encrypt the login (RSA-1024 +
     * AES/CFB8) even offline; 29 and below are plaintext. The
     * constant is named after the version (V1_3_2), never as a bare
     * "39", per the collision discipline above.
     */
    public static final int V1_3_2 = 39;

    /**
     * Legacy protocol 29 = Minecraft 1.2.5 and 1.2.4 (measured via
     * login probing: 28 is kicked, 29 succeeds, 30 is kicked; and
     * 1.2.4's jar — obtained via the Omniarchive route and
     * byte-verified against the official .exe distribution, since
     * piston-meta distributes no server jars for 1.2.4 and below —
     * is wire-identical to 1.2.5's: same 68 registrations, same
     * layouts, same registries). 29 is below the encryption
     * boundary: its login is entirely plaintext, the handshake
     * carries a single string (no protocol number — the protocol
     * is checked on the login packet), and its slot format appends
     * NBT only for damageable items (all measured in the real
     * 1.2.5 jar, docs/PROXY-1.2.5.md). The constant is named after
     * the version (V1_2_5), never as a bare "29", per the
     * collision discipline above.
     */
    public static final int V1_2_5 = 29;

    /**
     * Legacy protocol 28 = Minecraft 1.2.3, 1.2.2 and 1.2.1 (all
     * three measured wire-identical in their real jars). The
     * widespread tables that assign 28 to 1.2.4 are wrong: 1.2.4
     * speaks 29. 28 is protocol 29 minus exactly one packet —
     * 0xCA player_abilities arrived in 1.2.4 — and is otherwise
     * byte-identical to 29, registries included (measured,
     * docs/PROXY-1.2.3.md). The constant is named after the newest
     * version (V1_2_3), never as a bare "28", per the collision
     * discipline above.
     */
    public static final int V1_2_3 = 28;

    /**
     * Legacy protocol 23 = Minecraft 1.1 (measured via login
     * probing and the jar's protocol constant). A transitional
     * generation: its login and respawn carry an i64 world seed
     * and a level-type string, its chunks are cuboids rather than
     * sections, its spawn_mob has only two rotation bytes, and it
     * lacks the 1.2 additions (head look, tile entity data, the
     * redstone lamp, bottle o' enchanting, fire charge, thrown
     * potions, ocelots, iron golems) — all measured in the real
     * 1.1 jar, docs/PROXY-1.1.md. The constant is named after the
     * version (V1_1), never as a bare "23", per the collision
     * discipline above.
     */
    public static final int V1_1 = 23;

    /**
     * Legacy protocol 22 = Minecraft 1.0.0 (measured via login
     * probing and the jar's protocol constant) — the oldest
     * release DetergentMC targets. 22 is protocol 23 minus the
     * level-type strings (login and respawn) and minus plugin
     * messages, plus one packet 23 does not have: 0x1B
     * player_input, the client's movement input, removed again
     * in 1.1 (measured in the real 1.0.0 jar, docs/PROXY-1.0.md).
     * The constant is named after the version (V1_0), never as a
     * bare "22", per the collision discipline above.
     */
    public static final int V1_0 = 22;

    /**
     * The modern-side protocol the bridge mode speaks to the core
     * proxy: protocol 5 (Minecraft 1.7.10). Protocol 5's play packet
     * layouts ARE the internal hybrid format for every packet the two
     * generations share, and its chunk section content is byte-for-byte
     * the layout the legacy generations use, so translating legacy
     * wire &lt;-&gt; protocol-5 wire reuses all of the core's verified
     * conversion machinery instead of duplicating it.
     */
    public static final int BRIDGE_CLIENT_PROTOCOL = 5;

    private LegacyProtocol() {
    }

    /** Legacy protocols this frontend can terminate, newest first. */
    public static List<Integer> supportedLegacyProtocols() {
        return List.of(V1_6_4, V1_6_2, V1_6_1, V1_5_2, V1_5_1, V1_4_7, V1_4_5, V1_4_2,
                V1_3_2, V1_2_5, V1_2_3, V1_1, V1_0);
    }

    /** True when the given legacy protocol number is supported. */
    public static boolean isSupported(int legacyProtocol) {
        return supportedLegacyProtocols().contains(legacyProtocol);
    }

    /** The Minecraft version name of a legacy protocol, or null. */
    public static String versionName(int legacyProtocol) {
        return switch (legacyProtocol) {
            case V1_6_4 -> "1.6.4";
            case V1_6_2 -> "1.6.2";
            case V1_6_1 -> "1.6.1";
            case V1_5_2 -> "1.5.2";
            case V1_5_1 -> "1.5.1";
            case V1_4_7 -> "1.4.7";
            case V1_4_5 -> "1.4.5";
            case V1_4_2 -> "1.4.2";
            case V1_3_2 -> "1.3.2";
            case V1_2_5 -> "1.2.5";
            case V1_2_3 -> "1.2.3";
            case V1_1 -> "1.1";
            case V1_0 -> "1.0.0";
            default -> null;
        };
    }

    /** The legacy protocol of a Minecraft version name, or -1. */
    public static int protocolForVersionName(String versionName) {
        if (versionName == null) {
            return -1;
        }
        return switch (versionName) {
            case "1.6.4" -> V1_6_4;
            case "1.6.2" -> V1_6_2;
            case "1.6.1" -> V1_6_1;
            case "1.5.2" -> V1_5_2;
            case "1.5.1", "1.5" -> V1_5_1;
            case "1.4.7", "1.4.6" -> V1_4_7;
            case "1.4.5", "1.4.4" -> V1_4_5;
            case "1.4.2" -> V1_4_2;
            case "1.3.2", "1.3.1" -> V1_3_2;
            case "1.2.5", "1.2.4" -> V1_2_5;
            case "1.2.3", "1.2.2", "1.2.1" -> V1_2_3;
            case "1.1" -> V1_1;
            case "1.0.0" -> V1_0;
            default -> -1;
        };
    }

    /**
     * True for the 1.5-generation protocols (61 and 60), whose wire
     * layouts are identical (measured, docs/PROXY-1.5.1.md): every
     * "toward 1.5" conversion applies to both.
     */
    public static boolean is1_5Generation(int legacyProtocol) {
        return legacyProtocol == V1_5_2 || legacyProtocol == V1_5_1;
    }

    /**
     * True for the 1.4-generation protocols (51 = 1.4.7 / 1.4.6,
     * 49 = 1.4.5 / 1.4.4 and legacy 47 = 1.4.2), whose wire layouts
     * are the 61 layouts minus the scoreboard packets and the
     * named-particle packet, with an open_window that has no
     * useTitle flag (measured, docs/PROXY-1.4.7.md,
     * docs/PROXY-1.4.5.md and docs/PROXY-1.4.2.md). The 49-specific
     * wire traits (pickup spawn packet, no spawn rotation bytes,
     * no chunk-bulk skyLight flag) live in the 49 packet table's
     * codec kinds, not in generation branches — and legacy 47
     * (1.4.2) shares every one of them, being wire-identical to
     * 49 (measured: same 75 registrations, same direction flags,
     * same read/write layouts, same registries except one item).
     */
    public static boolean is1_4Generation(int legacyProtocol) {
        return legacyProtocol == V1_4_7 || legacyProtocol == V1_4_5
                || legacyProtocol == V1_4_2;
    }

    /**
     * True for the protocols with no firework-rocket object type
     * (it arrived in 1.4.6): legacy protocol 49 (1.4.5 / 1.4.4),
     * legacy protocol 47 (1.4.2), legacy protocol 39
     * (1.3.2 / 1.3.1), legacy protocol 29 (1.2.5 / 1.2.4),
     * legacy protocol 28 (1.2.3 / 1.2.2 / 1.2.1), legacy
     * protocol 23 (1.1) and legacy protocol 22 (1.0.0). Spawns of
     * object type 22 are dropped toward these clients (measured
     * entity registries, docs/PROXY-1.4.5.md, docs/PROXY-1.4.2.md,
     * docs/PROXY-1.3.2.md and docs/PROXY-1.2.5.md).
     */
    public static boolean hasNoFireworkRocket(int legacyProtocol) {
        return legacyProtocol == V1_4_5 || legacyProtocol == V1_4_2
                || legacyProtocol == V1_3_2 || legacyProtocol == V1_2_5
                || legacyProtocol == V1_2_3 || legacyProtocol == V1_1
                || legacyProtocol == V1_0;
    }

    /**
     * True for the 1.3-generation protocol (39 = 1.3.2 / 1.3.1),
     * whose wire layouts are the legacy-47 layouts with five
     * measured differences (single-long time packet, expanded
     * pickup spawn, no effect global flag, no client-settings
     * cape flag, fieldless server-list ping) — see
     * {@link LegacyPacketTable#V1_3_2} and docs/PROXY-1.3.2.md.
     */
    public static boolean is1_3Generation(int legacyProtocol) {
        return legacyProtocol == V1_3_2;
    }

    /**
     * True for the 1.2-generation protocols (29 = 1.2.5 / 1.2.4
     * and 28 = 1.2.3 / 1.2.2 / 1.2.1), whose wire layouts are the
     * protocol-39 layouts minus the 1.3 additions, with a
     * plaintext login, a pre-chunk packet, an 8-field login,
     * inlined equipment stacks, single-entity destroys, a
     * two-length chunk form, an int-parameter tile entity packet
     * and conditional-NBT slots. The two differ in exactly one
     * packet: BOOLx4 abilities (0xCA) exists only in 29 — see
     * {@link LegacyPacketTable#V1_2_5}, docs/PROXY-1.2.5.md and
     * docs/PROXY-1.2.3.md.
     */
    public static boolean is1_2Generation(int legacyProtocol) {
        return legacyProtocol == V1_2_5 || legacyProtocol == V1_2_3;
    }

    /**
     * True for the 1.2 generation and everything older (29, 28,
     * 23, 22): the protocols below the 1.3 additions, whose login
     * is plaintext and whose translator branches (pre-chunk chunk
     * fan-out, BOOL-form or absent abilities, int-parameter or
     * absent tile entity data, serverbound respawn requests on
     * 0x09) are shared. Protocol 39 (1.3) is NOT included: it has
     * the encryption pair and the NBT tile entity form.
     */
    public static boolean is1_2OrOlderGeneration(int legacyProtocol) {
        return is1_2Generation(legacyProtocol)
                || legacyProtocol == V1_1 || legacyProtocol == V1_0;
    }

    /**
     * True for the 1.1-and-older protocols (23 = 1.1 and
     * 22 = 1.0.0): the transitional login/respawn forms with an
     * i64 world seed, cuboid chunks, the three-array multi block
     * change and two-byte spawn_mob rotations (measured,
     * docs/PROXY-1.1.md and docs/PROXY-1.0.md).
     */
    public static boolean is1_1OrOlderGeneration(int legacyProtocol) {
        return legacyProtocol == V1_1 || legacyProtocol == V1_0;
    }

    /**
     * True for the protocols below the encryption boundary
     * (39): 29, 28, 23 and 22 log in entirely in plaintext — the
     * handshake carries a single string with no protocol number
     * and the protocol is checked on the login packet (measured
     * in all four jars).
     */
    public static boolean isPlaintextGeneration(int legacyProtocol) {
        return is1_2OrOlderGeneration(legacyProtocol);
    }

    /**
     * True for the protocols whose flat slot form appends the
     * gzip NBT only for damageable items (29, 28, 23, 22): the
     * slot readers in all four jars gate the NBT read on the
     * item predicate {@code maxDamage > 0 && !hasSubtypes}
     * (runtime-measured set of 49 items, identical in every jar
     * from 1.0.0 to 1.2.5 — see
     * {@link LegacyWire#DAMAGEABLE_ITEMS_1_2_5}).
     */
    public static boolean slotNbtIsConditional(int legacyProtocol) {
        return is1_2OrOlderGeneration(legacyProtocol);
    }

    /**
     * True for the protocols whose DataWatcher slot entries
     * (metadata type 5) carry no NBT: legacy protocol 47 (1.4.2),
     * legacy protocol 39 (1.3.2 / 1.3.1), legacy protocol 29
     * (1.2.5 / 1.2.4), legacy protocol 28 (1.2.3 / 1.2.2 / 1.2.1),
     * legacy protocol 23 (1.1) and legacy protocol 22 (1.0.0).
     * Measured in the real jars: their DataWatcher
     * readers build the slot from item id, count and damage only,
     * while protocol 49 (1.4.5) and up read a full slot (id,
     * count, damage, NBT) — the full-slot watcher form arrived
     * in 1.4.4. See docs/PROXY-1.3.2.md and docs/PROXY-1.2.5.md.
     */
    public static boolean watcherSlotHasNoNbt(int legacyProtocol) {
        return legacyProtocol == V1_4_2 || legacyProtocol == V1_3_2
                || is1_2OrOlderGeneration(legacyProtocol);
    }

    /**
     * True for the protocols that share the 1.5 generation's wire
     * traits against the 78 baseline: I16 health, no attach leash
     * flag, byte-quantised ability speeds and no horses (all of
     * these already hold in 1.4.7, measured in the 51 jar, in
     * 1.3.2, measured in the 39 jar, and in 1.2.5, measured in
     * the 29 jar — their layouts for those four packets are
     * identical to 51's, except that protocol 29's abilities
     * packet is BOOLx4 rather than byte speeds, which the
     * translator branches on before consulting this helper).
     */
    public static boolean is1_5OrOlderGeneration(int legacyProtocol) {
        return is1_5Generation(legacyProtocol) || is1_4Generation(legacyProtocol)
                || is1_3Generation(legacyProtocol) || is1_2OrOlderGeneration(legacyProtocol);
    }

    /**
     * Whether the given {@code minecraft-version} config value names a
     * backend this frontend serves directly (the core's Netty proxy
     * cannot speak to it).
     */
    public static boolean isLegacyBackendVersion(String minecraftVersion) {
        return protocolForVersionName(minecraftVersion) > 0;
    }
}
