package io.detergentmc.legacy;

import java.util.List;
import java.util.Map;

/**
 * The measured packet table for legacy protocol 78 (Minecraft 1.6.4).
 *
 * <p>How this table was established (Stage A, docs/PROXY-1.6.4.md):
 * the packet registrations and every packet's read method were
 * extracted mechanically from the real vanilla 1.6.4 server jar
 * (SHA-1 {@code 050f93c1f3fe9e2052398f7bd6aca10c63d64a87}), and the
 * result was validated by parsing three independent live captures
 * (45, 1,331 and 2,199 packets) exactly to end-of-stream with zero
 * desynchronisation.
 *
 * <p>Framing of this generation: there is NO length prefix and NO
 * VarInt anywhere. A packet is its 1-byte ID followed by its fields
 * in the order listed here. All multi-byte numbers are big-endian.
 * Strings are an i16 character count followed by UTF-16BE code units.
 * The ID space is a single table shared by both directions (the
 * {@code toClient}/{@code toServer} flags come from the jar's own
 * registration calls); a packet class has ONE read layout used for
 * both directions.
 *
 * <p>Packets whose layout is not a flat field list (counted arrays,
 * conditional tails, bulk payloads) are marked with a {@code custom}
 * kind and have dedicated reader/writer code in {@link LegacyPacketIO}.
 */
public final class LegacyPacketTable {

    /** One wire field of a legacy packet. */
    public enum Field {
        U8, I8, BOOL, I16, U16, I32, I64, F32, F64,
        /** i16 char count + UTF-16BE code units. */
        STRING,
        /** i16 byte length + raw bytes (-1 length is an error). */
        BYTES16,
        /** i16 item id (-1 = empty); if present: u8 count, i16 damage, gzip NBT. */
        SLOT,
        /** DataWatcher entries, bitfield headers, 0x7F terminator. */
        METADATA,
        /** i16 byte length + raw NBT bytes (-1 = none). NOT compressed. */
        NBTBYTES
    }

    /**
     * One packet definition.
     *
     * @param fields flat field layout, or {@code null} when
     *               {@code custom} names a dedicated codec
     * @param custom dedicated-codec kind, or {@code null} for flat packets
     */
    public record PacketDef(int id, String name, boolean toClient, boolean toServer,
                            List<Field> fields, String custom) {
        public boolean isCustom() {
            return custom != null;
        }
    }

    private static PacketDef def(int id, String name, boolean cb, boolean sb, Field... fields) {
        return new PacketDef(id, name, cb, sb, List.of(fields), null);
    }

    private static PacketDef custom(int id, String name, boolean cb, boolean sb, String kind) {
        return new PacketDef(id, name, cb, sb, null, kind);
    }

    private static final Field U8 = Field.U8;
    private static final Field I8 = Field.I8;
    private static final Field BOOL = Field.BOOL;
    private static final Field I16 = Field.I16;
    private static final Field U16 = Field.U16;
    private static final Field I32 = Field.I32;
    private static final Field I64 = Field.I64;
    private static final Field F32 = Field.F32;
    private static final Field F64 = Field.F64;
    private static final Field STR = Field.STRING;
    private static final Field BYTES = Field.BYTES16;
    private static final Field SLOT = Field.SLOT;
    private static final Field META = Field.METADATA;
    private static final Field NBT = Field.NBTBYTES;

    /** All 82 packet IDs registered by the vanilla 1.6.4 server. */
    public static final Map<Integer, PacketDef> V1_6_4 = Map.ofEntries(
            Map.entry(0x00, def(0x00, "keep_alive", true, true, I32)),
            Map.entry(0x01, def(0x01, "login", true, true, I32, STR, I8, I8, I8, I8, I8)),
            Map.entry(0x02, def(0x02, "handshake", false, true, U8, STR, STR, I32)),
            Map.entry(0x03, def(0x03, "chat", true, true, STR)),
            Map.entry(0x04, def(0x04, "time_update", true, false, I64, I64)),
            Map.entry(0x05, def(0x05, "entity_equipment", true, false, I32, I16, SLOT)),
            Map.entry(0x06, def(0x06, "spawn_position", true, false, I32, I32, I32)),
            Map.entry(0x07, def(0x07, "use_entity", false, true, I32, I32, I8)),
            Map.entry(0x08, def(0x08, "update_health", true, false, F32, I16, F32)),
            Map.entry(0x09, def(0x09, "respawn", true, true, I32, I8, I8, I16, STR)),
            Map.entry(0x0A, def(0x0A, "player_flying", true, true, BOOL)),
            Map.entry(0x0B, def(0x0B, "player_position", true, true, F64, F64, F64, F64, BOOL)),
            Map.entry(0x0C, def(0x0C, "player_look", true, true, F32, F32, BOOL)),
            Map.entry(0x0D, def(0x0D, "player_position_and_look", true, true, F64, F64, F64, F64, F32, F32, BOOL)),
            Map.entry(0x0E, def(0x0E, "player_digging", false, true, U8, I32, U8, I32, U8)),
            Map.entry(0x0F, def(0x0F, "block_place", false, true, I32, U8, I32, U8, SLOT, U8, U8, U8)),
            Map.entry(0x10, def(0x10, "held_item_change", true, true, I16)),
            Map.entry(0x11, def(0x11, "use_bed", true, false, I32, I8, I32, I8, I32)),
            Map.entry(0x12, def(0x12, "animation", true, true, I32, I8)),
            Map.entry(0x13, def(0x13, "entity_action", false, true, I32, I8, I32)),
            Map.entry(0x14, def(0x14, "spawn_player", true, false, I32, STR, I32, I32, I32, I8, I8, I16, META)),
            Map.entry(0x16, def(0x16, "collect_item", true, false, I32, I32)),
            Map.entry(0x17, custom(0x17, "spawn_object", true, false, "spawn_object")),
            Map.entry(0x18, def(0x18, "spawn_mob", true, false, I32, U8, I32, I32, I32, I8, I8, I8, I16, I16, I16, META)),
            Map.entry(0x19, def(0x19, "spawn_painting", true, false, I32, STR, I32, I32, I32, I32)),
            Map.entry(0x1A, def(0x1A, "spawn_experience_orb", true, false, I32, I32, I32, I32, I16)),
            Map.entry(0x1B, def(0x1B, "steer_vehicle", false, true, F32, F32, BOOL, BOOL)),
            Map.entry(0x1C, def(0x1C, "entity_velocity", true, false, I32, I16, I16, I16)),
            Map.entry(0x1D, custom(0x1D, "destroy_entities", true, false, "destroy_entities")),
            Map.entry(0x1E, def(0x1E, "entity", true, false, I32)),
            Map.entry(0x1F, def(0x1F, "entity_relative_move", true, false, I32, I8, I8, I8)),
            Map.entry(0x20, def(0x20, "entity_look", true, false, I32, I8, I8)),
            Map.entry(0x21, def(0x21, "entity_look_and_relative_move", true, false, I32, I8, I8, I8, I8, I8)),
            Map.entry(0x22, def(0x22, "entity_teleport", true, false, I32, I32, I32, I32, I8, I8)),
            Map.entry(0x23, def(0x23, "entity_head_look", true, false, I32, I8)),
            Map.entry(0x26, def(0x26, "entity_status", true, false, I32, I8)),
            Map.entry(0x27, def(0x27, "attach_entity", true, false, I32, I32, U8)),
            Map.entry(0x28, def(0x28, "entity_metadata", true, false, I32, META)),
            Map.entry(0x29, def(0x29, "entity_effect", true, false, I32, I8, I8, I16)),
            Map.entry(0x2A, def(0x2A, "remove_entity_effect", true, false, I32, I8)),
            Map.entry(0x2B, def(0x2B, "set_experience", true, false, F32, I16, I16)),
            Map.entry(0x2C, custom(0x2C, "entity_properties", true, false, "entity_properties")),
            Map.entry(0x33, custom(0x33, "chunk_data", true, false, "chunk_data")),
            Map.entry(0x34, custom(0x34, "multi_block_change", true, false, "multi_block_change")),
            Map.entry(0x35, def(0x35, "block_change", true, false, I32, U8, I32, I16, U8)),
            Map.entry(0x36, def(0x36, "block_action", true, false, I32, I16, I32, U8, U8, I16)),
            Map.entry(0x37, def(0x37, "block_break_animation", true, false, I32, I32, I32, I32, U8)),
            Map.entry(0x38, custom(0x38, "chunk_bulk", true, false, "chunk_bulk")),
            Map.entry(0x3C, custom(0x3C, "explosion", true, false, "explosion")),
            Map.entry(0x3D, def(0x3D, "effect", true, false, I32, I32, I8, I32, I32, BOOL)),
            Map.entry(0x3E, def(0x3E, "named_sound", true, false, STR, I32, I32, I32, F32, U8)),
            Map.entry(0x3F, def(0x3F, "particle", true, false, STR, F32, F32, F32, F32, F32, F32, F32, I32)),
            Map.entry(0x46, def(0x46, "change_game_state", true, false, I8, I8)),
            Map.entry(0x47, def(0x47, "spawn_global_entity", true, false, I32, I8, I32, I32, I32)),
            Map.entry(0x64, custom(0x64, "open_window", true, false, "open_window")),
            Map.entry(0x65, def(0x65, "close_window", true, true, I8)),
            Map.entry(0x66, def(0x66, "window_click", false, true, I8, I16, I8, I16, I8, SLOT)),
            Map.entry(0x67, def(0x67, "set_slot", true, false, I8, I16, SLOT)),
            Map.entry(0x68, custom(0x68, "window_items", true, false, "window_items")),
            Map.entry(0x69, def(0x69, "window_property", true, false, I8, I16, I16)),
            Map.entry(0x6A, def(0x6A, "confirm_transaction", true, true, I8, I16, BOOL)),
            Map.entry(0x6B, def(0x6B, "creative_inventory_action", true, true, I16, SLOT)),
            Map.entry(0x6C, def(0x6C, "enchant_item", false, true, I8, I8)),
            Map.entry(0x82, def(0x82, "update_sign", true, true, I32, I16, I32, STR, STR, STR, STR)),
            Map.entry(0x83, custom(0x83, "item_data", true, false, "item_data")),
            Map.entry(0x84, def(0x84, "tile_entity_data", true, false, I32, I16, I32, I8, NBT)),
            Map.entry(0x85, def(0x85, "open_sign_editor", true, false, U8, I32, I32, I32)),
            Map.entry(0xC8, def(0xC8, "statistics", true, false, I32, I32)),
            Map.entry(0xC9, def(0xC9, "player_list_item", true, false, STR, BOOL, I16)),
            Map.entry(0xCA, def(0xCA, "player_abilities", true, true, U8, F32, F32)),
            Map.entry(0xCB, def(0xCB, "tab_complete", true, true, STR)),
            Map.entry(0xCC, def(0xCC, "client_settings", false, true, STR, I8, I8, I8, BOOL)),
            Map.entry(0xCD, def(0xCD, "client_status", false, true, I8)),
            Map.entry(0xCE, def(0xCE, "scoreboard_objective", true, false, STR, STR, I8)),
            Map.entry(0xCF, custom(0xCF, "update_score", true, false, "update_score")),
            Map.entry(0xD0, def(0xD0, "display_scoreboard", true, false, I8, STR)),
            Map.entry(0xD1, custom(0xD1, "teams", true, false, "teams")),
            Map.entry(0xFA, def(0xFA, "plugin_message", true, true, STR, BYTES)),
            Map.entry(0xFC, def(0xFC, "encryption_response", true, true, BYTES, BYTES)),
            Map.entry(0xFD, def(0xFD, "encryption_request", true, false, STR, BYTES, BYTES)),
            Map.entry(0xFE, custom(0xFE, "server_list_ping", false, true, "server_list_ping")),
            Map.entry(0xFF, def(0xFF, "disconnect", true, true, STR))
    );

    /**
     * Protocol 74 (1.6.2). Measured identical to protocol 78 in
     * registrations, directions and every packet's read/write layout
     * (mechanical extraction from the real 1.6.2 jar plus a live
     * capture parsed exactly to end-of-stream; docs/PROXY-1.6.2.md),
     * so the table delegates to the 78 layouts outright — the same
     * full-delegation pattern the modern tree uses for wire-identical
     * neighbours.
     */
    public static final Map<Integer, PacketDef> V1_6_2 = V1_6_4;

    /**
     * Protocol 73 (1.6.1). Measured against the real 1.6.1 jar
     * (docs/PROXY-1.6.1.md): identical to the 78 table except that
     * 0x85 open_sign_editor is not registered (it arrived in 1.6.2)
     * and 0x2C entity_properties uses the
     * {@code entity_properties_nomod} codec, because attribute
     * modifiers are not on the 1.6.1 wire.
     */
    public static final Map<Integer, PacketDef> V1_6_1 = buildV1_6_1();

    private static Map<Integer, PacketDef> buildV1_6_1() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_6_4);
        table.remove(0x85);
        PacketDef props = table.get(0x2C);
        table.put(0x2C, new PacketDef(props.id(), props.name(), props.toClient(),
                props.toServer(), null, "entity_properties_nomod"));
        return Map.copyOf(table);
    }

    /**
     * Protocol 61 (1.5.2). Measured against the real 1.5.2 jar
     * (docs/PROXY-1.5.2.md): 79 registrations. Against the 78 table,
     * 0x85 open_sign_editor (1.6.2), 0x1B steer_vehicle and 0x2C
     * entity_properties (both 1.6) are not registered, and six
     * packets change shape: 0x08 health is an I16, 0x13 has no
     * trailing I32, 0x27 has no leash flag, 0x64 uses the
     * {@code open_window_nohorse} codec (no horse-window entity id),
     * 0xC8's amount is an I8, and 0xCA's speeds are bytes
     * (value / 255). Everything else is byte-identical to 78,
     * including the DataWatcher (7 types), the slot format and the
     * chunk bulk skyLight flag.
     */
    public static final Map<Integer, PacketDef> V1_5_2 = buildV1_5_2();

    private static Map<Integer, PacketDef> buildV1_5_2() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_6_4);
        table.remove(0x85);
        table.remove(0x1B);
        table.remove(0x2C);
        table.put(0x08, def(0x08, "update_health", true, false, I16, I16, F32));
        table.put(0x13, def(0x13, "entity_action", false, true, I32, I8));
        table.put(0x27, def(0x27, "attach_entity", true, false, I32, I32));
        table.put(0x64, custom(0x64, "open_window", true, false, "open_window_nohorse"));
        table.put(0xC8, def(0xC8, "statistics", true, false, I32, I8));
        table.put(0xCA, def(0xCA, "player_abilities", true, true, U8, I8, I8));
        return Map.copyOf(table);
    }

    /**
     * Protocol 60 (1.5.1 and 1.5). Measured identical to protocol 61
     * in registrations, directions and every packet's read/write
     * layout (mechanical extraction from the real 1.5.1 and 1.5 jars
     * plus live captures parsed exactly to end-of-stream;
     * docs/PROXY-1.5.1.md), so the table delegates to the 61 layouts
     * outright — the same full-delegation pattern as 74 to 78.
     */
    public static final Map<Integer, PacketDef> V1_5_1 = V1_5_2;

    /**
     * Protocol 51 (1.4.7 and 1.4.6). Measured against the real 1.4.7
     * jar (docs/PROXY-1.4.7.md): 74 registrations. Against the 61
     * table, the five 1.5 additions are not registered — 0x3F
     * particle and the scoreboard set 0xCE / 0xCF / 0xD0 / 0xD1 —
     * and 0x64 open_window uses the {@code open_window_notitle}
     * codec (no trailing useTitle flag; 1.4.7 window titles are
     * always literal). Everything else is byte-identical to 61,
     * including the DataWatcher (7 types), the slot format, the
     * chunk bulk skyLight flag and the I16 health / byte-speed
     * traits of the 1.5 generation. The 1.4.6 jar was extracted the
     * same way and is identical in registrations, layouts and
     * registries, so one table covers both releases.
     */
    public static final Map<Integer, PacketDef> V1_4_7 = buildV1_4_7();

    private static Map<Integer, PacketDef> buildV1_4_7() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_5_2);
        table.remove(0x3F);
        table.remove(0xCE);
        table.remove(0xCF);
        table.remove(0xD0);
        table.remove(0xD1);
        table.put(0x64, custom(0x64, "open_window", true, false, "open_window_notitle"));
        return Map.copyOf(table);
    }

    /**
     * Protocol 49 (1.4.5 and 1.4.4). Measured against the real 1.4.5
     * jar (docs/PROXY-1.4.5.md): 75 registrations. Against the 51
     * table, 0x15 pickup_spawn still exists (dropped item entities
     * spawn through it; 51 merged them into spawn_object type 2),
     * 0x17 spawn_object uses the {@code spawn_object_norot} codec
     * (the pitch/yaw bytes arrived in 1.4.6), 0x38 chunk_bulk uses
     * the {@code chunk_bulk_nosky} codec (the skyLight flag also
     * arrived in 1.4.6 — the flag's range is 51 and up), and 0x10
     * held_item_change is serverbound only. Everything else is
     * byte-identical to 51. The 1.4.4 jar was extracted the same
     * way and is identical in registrations, layouts and
     * registries, so one table covers both releases.
     */
    public static final Map<Integer, PacketDef> V1_4_5 = buildV1_4_5();

    private static Map<Integer, PacketDef> buildV1_4_5() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_4_7);
        table.put(0x15, custom(0x15, "pickup_spawn", true, false, "pickup_spawn"));
        PacketDef spawn = table.get(0x17);
        table.put(0x17, new PacketDef(spawn.id(), spawn.name(), spawn.toClient(),
                spawn.toServer(), null, "spawn_object_norot"));
        PacketDef bulk = table.get(0x38);
        table.put(0x38, new PacketDef(bulk.id(), bulk.name(), bulk.toClient(),
                bulk.toServer(), null, "chunk_bulk_nosky"));
        table.put(0x10, def(0x10, "held_item_change", false, true, I16));
        return Map.copyOf(table);
    }

    /**
     * Legacy protocol 47 (Minecraft 1.4.2 — NOT the Netty protocol
     * 47 of Minecraft 1.8; see the collision discipline in
     * {@link LegacyProtocol}). Measured against the real 1.4.2 jar
     * (docs/PROXY-1.4.2.md): the same 75 registrations as protocol
     * 49, with identical direction flags and identical read/write
     * layouts for every packet — including the pickup_spawn
     * packet, the no-rotation spawn_object, the no-flag
     * chunk_bulk and the serverbound-only held_item_change — and
     * the same slot/DataWatcher helper shapes, so the table
     * delegates to the 49 layouts outright (the same
     * full-delegation pattern as 74 to 78 and 60 to 61). The only
     * measured difference from 49 is registry-level: item 2267
     * (the "wait" record) does not exist yet; that lives in
     * {@link LegacyChunkRemap}, not in this table.
     */
    public static final Map<Integer, PacketDef> V1_4_2 = V1_4_5;

    /**
     * Protocol 39 (1.3.2 and 1.3.1). Measured against the real
     * 1.3.2 jar (docs/PROXY-1.3.2.md): the same 75 registrations
     * as legacy protocol 47 (1.4.2), with identical direction
     * flags, and identical read/write layouts for every packet
     * except five:
     * <ul>
     *   <li>0x04 time_update carries ONE i64 — the world's single
     *       "Time" value. The second long (a separate day time)
     *       arrived in 1.4.</li>
     *   <li>0x15 pickup_spawn uses the
     *       {@code pickup_spawn_expanded} codec: the item stack is
     *       inlined as (itemId i16, count u8, damage i16) with no
     *       NBT, where 1.4.2 sends a full slot.</li>
     *   <li>0x3D effect has no trailing "global" bool (it arrived
     *       in 1.4).</li>
     *   <li>0xCC client_settings has no trailing showCape bool
     *       (it arrived in 1.4).</li>
     *   <li>0xFE server_list_ping's packet class has no fields at
     *       all (1.4.2 reads one byte). A real 1.3.2 server answers
     *       a bare FE 01 with the pre-§1 response form and resets
     *       the connection on the full PingHost form; the frontend
     *       answers pings itself and never parses this packet.</li>
     * </ul>
     * Two further 1.3 traits live outside this table: the chunk
     * bulk keeps the {@code chunk_bulk_nosky} codec (the skyLight
     * flag arrived in 1.4.6), and the DataWatcher slot entries
     * carry no NBT (see {@link LegacyProtocol#watcherSlotHasNoNbt}).
     * The 0x66 window_click mode field is a boolean on the 1.3.2
     * wire where 1.4.2 reads a byte; the width is identical, so
     * the flat layout is shared and only the meaning differs
     * (0/1 = click / shift-click, which the modern mode byte
     * carries unchanged). The 1.3.1 jar was extracted the same way
     * and is identical in registrations, layouts and registries,
     * so one table covers both releases.
     */
    public static final Map<Integer, PacketDef> V1_3_2 = buildV1_3_2();

    private static Map<Integer, PacketDef> buildV1_3_2() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_4_2);
        table.put(0x04, def(0x04, "time_update", true, false, I64));
        table.put(0x15, custom(0x15, "pickup_spawn", true, false,
                "pickup_spawn_expanded"));
        table.put(0x3D, def(0x3D, "effect", true, false, I32, I32, I8, I32, I32));
        table.put(0xCC, def(0xCC, "client_settings", false, true, STR, I8, I8, I8));
        table.put(0xFE, def(0xFE, "server_list_ping", false, true));
        return Map.copyOf(table);
    }

    /**
     * Protocol 29 (1.2.5). Measured against the real 1.2.5 jar
     * (docs/PROXY-1.2.5.md): 68 registrations. Against the 39
     * table, the 1.3 additions are not registered — 0x37
     * block_break_animation, 0x38 chunk_bulk, 0x3E named_sound,
     * 0xCB tab_complete, 0xCC client_settings, 0xCD
     * client_status — and neither are the encryption pair
     * 0xFC / 0xFD: 29 is below the encryption boundary and its
     * login is plaintext. 0x32 pre_chunk (clientbound only)
     * exists only here. The changed layouts:
     * <ul>
     *   <li>0x01 login has EIGHT fields (an empty username string
     *       follows the entity id; gamemode and dimension are
     *       i32, not i8).</li>
     *   <li>0x02 handshake is a single string in both directions
     *       (the server answers with the connection-hash string);
     *       it carries no protocol number — the protocol is
     *       checked on the 0x01.</li>
     *   <li>0x05 entity_equipment uses the
     *       {@code equipment_inline} codec: (itemId i16, damage
     *       i16) inlined, no slot structure, no count, no NBT.</li>
     *   <li>0x0F block_place has no trailing cursor bytes.</li>
     *   <li>0x14 spawn_player has no trailing metadata.</li>
     *   <li>0x18 spawn_mob has no velocity shorts.</li>
     *   <li>0x1D destroy_entities uses the
     *       {@code destroy_entities_single} codec: one i32 entity
     *       id per packet (the field list keeps the counted
     *       shape with a one-element list).</li>
     *   <li>0x33 chunk_data uses the {@code chunk_data_125}
     *       codec: TWO length ints precede the data — the
     *       compressed length and an unused int the vanilla
     *       constructor never sets (always 0 on the wire).</li>
     *   <li>0x35 block_change's block type is a u8 (an i16 in
     *       39).</li>
     *   <li>0x36 block_action has no trailing block-type i16.</li>
     *   <li>0x3C explosion uses the {@code explosion_novel}
     *       codec: no trailing player-motion floats.</li>
     *   <li>0x84 tile_entity_data is the int-parameter form
     *       (action u8 + three i32s); the NBT form arrived in
     *       1.3.</li>
     *   <li>0xCA player_abilities is four bools (invulnerable,
     *       flying, canFly, creative).</li>
     * </ul>
     * Two further 29 traits live outside this table: every SLOT
     * field uses the conditional-NBT form (NBT only for
     * damageable items, see {@link LegacyWire#slotCarriesNbt29}),
     * and the DataWatcher slot entries carry no NBT (see
     * {@link LegacyProtocol#watcherSlotHasNoNbt}). The 0x66
     * window_click mode field is a boolean on the 1.2.5 wire
     * where the shared layout reads a byte; the width is
     * identical, so the flat layout is shared and only the
     * meaning differs (0/1 = click / shift-click) — the same
     * treatment protocol 39 uses. Identical to 39, verified
     * packet by packet: 0x09 respawn (bidirectional), the
     * expanded 0x15 pickup spawn, the no-rotation 0x17 spawn
     * object, the single-i64 0x04 and the global-less 0x3D.
     */
    public static final Map<Integer, PacketDef> V1_2_5 = buildV1_2_5();

    private static Map<Integer, PacketDef> buildV1_2_5() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_3_2);
        table.remove(0x37);
        table.remove(0x38);
        table.remove(0x3E);
        table.remove(0xCB);
        table.remove(0xCC);
        table.remove(0xCD);
        table.remove(0xFC);
        table.remove(0xFD);
        table.put(0x01, def(0x01, "login", true, true,
                I32, STR, STR, I32, I32, U8, U8, U8));
        table.put(0x02, def(0x02, "handshake", true, true, STR));
        table.put(0x05, custom(0x05, "entity_equipment", true, false,
                "equipment_inline"));
        table.put(0x0F, def(0x0F, "block_place", false, true,
                I32, U8, I32, U8, SLOT));
        table.put(0x14, def(0x14, "spawn_player", true, false,
                I32, STR, I32, I32, I32, I8, I8, I16));
        table.put(0x18, def(0x18, "spawn_mob", true, false,
                I32, U8, I32, I32, I32, I8, I8, I8, META));
        table.put(0x1D, custom(0x1D, "destroy_entities", true, false,
                "destroy_entities_single"));
        table.put(0x32, def(0x32, "pre_chunk", true, false, I32, I32, U8));
        table.put(0x33, custom(0x33, "chunk_data", true, false, "chunk_data_125"));
        table.put(0x35, def(0x35, "block_change", true, false, I32, U8, I32, U8, U8));
        table.put(0x36, def(0x36, "block_action", true, false, I32, I16, I32, U8, U8));
        table.put(0x3C, custom(0x3C, "explosion", true, false, "explosion_novel"));
        table.put(0x84, def(0x84, "tile_entity_data", true, false,
                I32, I16, I32, I8, I32, I32, I32));
        table.put(0xCA, def(0xCA, "player_abilities", true, true,
                BOOL, BOOL, BOOL, BOOL));
        return Map.copyOf(table);
    }

    /**
     * Protocol 28 (1.2.3 / 1.2.2 / 1.2.1). Measured against the
     * real jars (docs/PROXY-1.2.3.md): 67 registrations. Against
     * the 29 table the ONLY difference is that 0xCA
     * player_abilities is not registered — it arrived in 1.2.4.
     * Every other packet, direction flag, layout and registry is
     * identical to 29 (the 1.2.4 jar is likewise identical to
     * 1.2.5's, so 29 covers 1.2.4 and this table covers the three
     * older 1.2 releases).
     */
    public static final Map<Integer, PacketDef> V1_2_3 = buildV1_2_3();

    private static Map<Integer, PacketDef> buildV1_2_3() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_2_5);
        table.remove(0xCA);
        return Map.copyOf(table);
    }

    /**
     * Protocol 23 (1.1). Measured against the real 1.1 jar
     * (docs/PROXY-1.1.md): 65 registrations. Against the 28
     * table, 0x23 entity_head_look and 0x84 tile_entity_data are
     * not registered (both arrived in 1.2), and six layouts
     * differ:
     * <ul>
     *   <li>0x01 login is the transitional NINE-field form: an
     *       i64 world seed follows the username and the level-type
     *       string follows the seed; the tail is gamemode i32,
     *       then dimension / difficulty / world height / max
     *       players as four i8s (measured field semantics from
     *       the login class and the server's construction site).</li>
     *   <li>0x09 respawn carries the seed too: dimension i8,
     *       difficulty i8, gamemode i8, world height i16, seed
     *       i64, level type string.</li>
     *   <li>0x18 spawn_mob has only TWO rotation bytes (yaw,
     *       pitch); the head-yaw byte arrived in 1.2.</li>
     *   <li>0x33 chunk_data uses the {@code chunk_data_cuboid}
     *       codec: (x i32, y i16, z i32, sizeX / sizeY / sizeZ as
     *       u8 counts-minus-one, compressed length i32, data).
     *       The inflated data is the cuboid's block ids, then
     *       metadata / block-light / sky-light nibble arrays,
     *       indexed y-fastest.</li>
     *   <li>0x34 multi_block_change uses the
     *       {@code multi_block_change_early} codec: chunk x/z,
     *       a u16 count, then three parallel arrays (packed i16
     *       coordinates, u8 types, u8 metas).</li>
     *   <li>0xFA plugin_message differs only in the extractor's
     *       tokenisation; the wire form is identical.</li>
     * </ul>
     * The 1.1 registries lose the redstone lamp blocks (123 /
     * 124), the bottle o' enchanting (384) and fire charge (385)
     * items, the thrown-potion / exp-bottle objects (16 / 17)
     * and the ocelot / iron-golem mobs (98 / 99) — see
     * {@link LegacyChunkRemap} and the translator. Slots keep the
     * conditional-NBT form with the same 49-item damageable set,
     * and DataWatcher slots carry no NBT, exactly as in 28 / 29.
     */
    public static final Map<Integer, PacketDef> V1_1 = buildV1_1();

    private static Map<Integer, PacketDef> buildV1_1() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_2_3);
        table.remove(0x23);
        table.remove(0x84);
        table.put(0x01, def(0x01, "login", true, true,
                I32, STR, I64, STR, I32, I8, I8, I8, I8));
        table.put(0x09, def(0x09, "respawn", true, true,
                I8, I8, I8, I16, I64, STR));
        table.put(0x18, def(0x18, "spawn_mob", true, false,
                I32, U8, I32, I32, I32, I8, I8, META));
        table.put(0x33, custom(0x33, "chunk_data", true, false,
                "chunk_data_cuboid"));
        table.put(0x34, custom(0x34, "multi_block_change", true, false,
                "multi_block_change_early"));
        return Map.copyOf(table);
    }

    /**
     * Protocol 22 (1.0.0). Measured against the real 1.0.0 jar
     * (docs/PROXY-1.0.md): 65 registrations. Against the 23
     * table, the login and respawn lose the level-type string
     * (the seed stays), 0xFA plugin_message is not registered
     * (plugin messages arrived in 1.1), and one packet exists
     * only here: 0x1B player_input (strafe / forward / pitch /
     * yaw floats, jumping and sneaking bools, serverbound only)
     * — the client's raw movement input, removed again in 1.1.
     * Registries are 23's minus the spawn egg item (383);
     * entities are identical to 23.
     */
    public static final Map<Integer, PacketDef> V1_0 = buildV1_0();

    private static Map<Integer, PacketDef> buildV1_0() {
        Map<Integer, PacketDef> table = new java.util.HashMap<>(V1_1);
        table.remove(0xFA);
        table.put(0x01, def(0x01, "login", true, true,
                I32, STR, I64, I32, I8, I8, I8, I8));
        table.put(0x09, def(0x09, "respawn", true, true,
                I8, I8, I8, I16, I64));
        table.put(0x1B, def(0x1B, "player_input", false, true,
                F32, F32, F32, F32, BOOL, BOOL));
        return Map.copyOf(table);
    }

    private LegacyPacketTable() {
    }

    /** The packet table for a legacy protocol. */
    public static Map<Integer, PacketDef> tableFor(int legacyProtocol) {
        if (legacyProtocol == LegacyProtocol.V1_6_4) {
            return V1_6_4;
        }
        if (legacyProtocol == LegacyProtocol.V1_6_2) {
            return V1_6_2;
        }
        if (legacyProtocol == LegacyProtocol.V1_6_1) {
            return V1_6_1;
        }
        if (legacyProtocol == LegacyProtocol.V1_5_2) {
            return V1_5_2;
        }
        if (legacyProtocol == LegacyProtocol.V1_5_1) {
            return V1_5_1;
        }
        if (legacyProtocol == LegacyProtocol.V1_4_7) {
            return V1_4_7;
        }
        if (legacyProtocol == LegacyProtocol.V1_4_5) {
            return V1_4_5;
        }
        if (legacyProtocol == LegacyProtocol.V1_4_2) {
            return V1_4_2;
        }
        if (legacyProtocol == LegacyProtocol.V1_3_2) {
            return V1_3_2;
        }
        if (legacyProtocol == LegacyProtocol.V1_2_5) {
            return V1_2_5;
        }
        if (legacyProtocol == LegacyProtocol.V1_2_3) {
            return V1_2_3;
        }
        if (legacyProtocol == LegacyProtocol.V1_1) {
            return V1_1;
        }
        if (legacyProtocol == LegacyProtocol.V1_0) {
            return V1_0;
        }
        throw new IllegalArgumentException("unsupported legacy protocol: " + legacyProtocol);
    }

    /** Definition of one packet, or null when the ID is not registered. */
    public static PacketDef packet(int legacyProtocol, int id) {
        return tableFor(legacyProtocol).get(id);
    }
}
