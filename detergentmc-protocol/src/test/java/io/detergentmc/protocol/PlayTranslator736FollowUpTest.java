// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayTranslator} の736（1.16.1）⇔751（1.16.2）向け
 * Unlock Recipes／Recipe Book 変換器のテスト（Stage B フォローアップ）。
 * Unlock Recipes：751は smelting pair の後に blast／smoker の4 bool を追加。
 * 736→751の補完値は false×4（ViaVersion の ItemPacketRewriter1_16_2 と同じ）。
 * Recipe Book：751の recipe_book／displayed_recipe を736の
 * crafting_book_data type=1／type=0 に写す（ViaVersion の
 * Protocol1_16_1To1_16_2 の写像そのまま）。
 * 736→751の recipe_book は非可逆（4冊分を優先順で1冊に絞る）。
 */
class PlayTranslator736FollowUpTest {

    /** 文字列リストを書く（[count varint][文字列列]）。 */
    private static void writeStrings(Writer writer, String... values) {
        writer.writeVarInt(values.length);
        for (String value : values) {
            writer.writeString(value);
        }
    }

    /** 文字列リストを読む。 */
    private static List<String> readStrings(Reader reader) {
        int count = reader.readVarInt();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(reader.readString());
        }
        return values;
    }

    // ------------------------------------------------------------------
    // Unlock Recipes
    // ------------------------------------------------------------------

    /** Unlock Recipes（736）：[action][4 bool][recipes1][recipes2（action==0 のときだけ）]。 */
    private static byte[] unlockRecipes736Payload(int action, String[] recipes1, String[] recipes2) {
        Writer writer = new Writer();
        writer.writeVarInt(action);
        writer.writeBoolean(true); // craftingBookOpen
        writer.writeBoolean(false); // filteringCraftable
        writer.writeBoolean(true); // smeltingBookOpen
        writer.writeBoolean(true); // filteringSmeltable
        writeStrings(writer, recipes1);
        if (action == 0) {
            writeStrings(writer, recipes2);
        }
        return writer.bytes();
    }

    /** Unlock Recipes（751）：736の4 bool の後に blast／smoker の4 bool を追加。 */
    private static byte[] unlockRecipes751Payload(int action, boolean[] extra,
            String[] recipes1, String[] recipes2) {
        Writer writer = new Writer();
        writer.writeVarInt(action);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(true);
        for (boolean value : extra) {
            writer.writeBoolean(value);
        }
        writeStrings(writer, recipes1);
        if (action == 0) {
            writeStrings(writer, recipes2);
        }
        return writer.bytes();
    }

    @Test
    void unlockRecipes736To751InitInsertsFourFalse() {
        byte[] out = PlayTranslator.translateUnlockRecipes736To751(
                unlockRecipes736Payload(0, new String[]{"a", "b"}, new String[]{"c"}));
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean()); // craftingBookOpen
        assertFalse(reader.readBoolean()); // filteringCraftable
        assertTrue(reader.readBoolean()); // smeltingBookOpen
        assertTrue(reader.readBoolean()); // filteringSmeltable
        assertFalse(reader.readBoolean()); // blastFurnaceOpen（補完）
        assertFalse(reader.readBoolean()); // filteringBlastFurnace（補完）
        assertFalse(reader.readBoolean()); // smokerBookOpen（補完）
        assertFalse(reader.readBoolean()); // filteringSmoker（補完）
        assertEquals(List.of("a", "b"), readStrings(reader));
        assertEquals(List.of("c"), readStrings(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void unlockRecipes736To751AddSkipsRecipes2() {
        // action=1（ADD）のとき recipes2 は無い。
        byte[] out = PlayTranslator.translateUnlockRecipes736To751(
                unlockRecipes736Payload(1, new String[]{"a"}, null));
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertEquals(List.of("a"), readStrings(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void unlockRecipes751To736StripsFourBools() {
        byte[] out = PlayTranslator.translateUnlockRecipes751To736(
                unlockRecipes751Payload(0, new boolean[]{true, true, false, true},
                        new String[]{"a", "b"}, new String[]{"c"}));
        assertArrayEquals(
                unlockRecipes736Payload(0, new String[]{"a", "b"}, new String[]{"c"}), out);
    }

    @Test
    void unlockRecipes751To736RemoveHasNoRecipes2() {
        byte[] out = PlayTranslator.translateUnlockRecipes751To736(
                unlockRecipes751Payload(2, new boolean[]{false, false, false, false},
                        new String[]{"a"}, null));
        assertArrayEquals(
                unlockRecipes736Payload(2, new String[]{"a"}, null), out);
    }

    @Test
    void unlockRecipesRoundTrips() {
        byte[] payload = unlockRecipes736Payload(0, new String[]{"a", "b"}, new String[]{"c"});
        byte[] as751 = PlayTranslator.translateUnlockRecipes736To751(payload);
        assertArrayEquals(payload, PlayTranslator.translateUnlockRecipes751To736(as751));
    }

    // ------------------------------------------------------------------
    // Recipe Book（751 → 736）
    // ------------------------------------------------------------------

    /** Recipe Book（751）：[bookId varint][bookOpen bool][filterActive bool]。 */
    private static byte[] recipeBook751Payload(int bookId, boolean open, boolean filter) {
        Writer writer = new Writer();
        writer.writeVarInt(bookId);
        writer.writeBoolean(open);
        writer.writeBoolean(filter);
        return writer.bytes();
    }

    /** crafting_book_data type=1 の8 bool を読む。 */
    private static boolean[] readRecipeBookSettings(Reader reader) {
        assertEquals(1, reader.readVarInt()); // type=SETTINGS
        boolean[] values = new boolean[8];
        for (int i = 0; i < 8; i++) {
            values[i] = reader.readBoolean();
        }
        return values;
    }

    @Test
    void recipeBook751To736MapsEachBookId() {
        // bookId 0：crafting だけ開く。
        boolean[] crafting = readRecipeBookSettings(new Reader(
                PlayTranslator.translateRecipeBook751To736(recipeBook751Payload(0, true, true))));
        assertArrayEquals(
                new boolean[]{true, true, false, true, false, true, false, true}, crafting);
        // bookId 1：smelting だけ開く。
        boolean[] smelting = readRecipeBookSettings(new Reader(
                PlayTranslator.translateRecipeBook751To736(recipeBook751Payload(1, true, false))));
        assertArrayEquals(
                new boolean[]{false, false, true, false, false, false, false, false}, smelting);
        // bookId 2：blasting だけ開く。
        boolean[] blasting = readRecipeBookSettings(new Reader(
                PlayTranslator.translateRecipeBook751To736(recipeBook751Payload(2, true, true))));
        assertArrayEquals(
                new boolean[]{false, true, false, true, true, true, false, true}, blasting);
        // bookId 3：smoking だけ開く。
        boolean[] smoking = readRecipeBookSettings(new Reader(
                PlayTranslator.translateRecipeBook751To736(recipeBook751Payload(3, true, false))));
        assertArrayEquals(
                new boolean[]{false, false, false, false, false, false, true, false}, smoking);
        // bookId 0・open=false：全部閉じる。
        boolean[] closed = readRecipeBookSettings(new Reader(
                PlayTranslator.translateRecipeBook751To736(recipeBook751Payload(0, false, true))));
        assertArrayEquals(
                new boolean[]{false, true, false, true, false, true, false, true}, closed);
        // bookId 範囲外：open は全部 false だが、フィルタは filterActive が
        // 4冊全てに入る（ViaVersion の写像通り。フィルタは bookId に依存しない）。
        boolean[] outOfRange = readRecipeBookSettings(new Reader(
                PlayTranslator.translateRecipeBook751To736(recipeBook751Payload(9, true, true))));
        assertArrayEquals(
                new boolean[]{false, true, false, true, false, true, false, true}, outOfRange);
    }

    @Test
    void recipeBookRoundTripsForOneBook() {
        // 751→736→751 は1冊分なら可逆。
        byte[] payload = recipeBook751Payload(1, true, true);
        byte[] as736 = PlayTranslator.translateRecipeBook751To736(payload);
        assertArrayEquals(payload, PlayTranslator.translateRecipeBook736To751(as736));
    }

    // ------------------------------------------------------------------
    // Recipe Book（736 → 751）
    // ------------------------------------------------------------------

    /** crafting_book_data type=1（736）：[type=1][8 bool]。 */
    private static byte[] craftingBookDataSettingsPayload(boolean[] values) {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        for (boolean value : values) {
            writer.writeBoolean(value);
        }
        return writer.bytes();
    }

    @Test
    void recipeBook736To751PicksFirstOpenBook() {
        // crafting 開：bookId 0。
        byte[] crafting = PlayTranslator.translateRecipeBook736To751(
                craftingBookDataSettingsPayload(
                        new boolean[]{true, true, false, false, false, false, false, false}));
        Reader craftingReader = new Reader(crafting);
        assertEquals(0, craftingReader.readVarInt());
        assertTrue(craftingReader.readBoolean());
        assertTrue(craftingReader.readBoolean());
        // smelting だけ開：bookId 1。
        byte[] smelting = PlayTranslator.translateRecipeBook736To751(
                craftingBookDataSettingsPayload(
                        new boolean[]{false, false, true, true, false, false, false, false}));
        Reader smeltingReader = new Reader(smelting);
        assertEquals(1, smeltingReader.readVarInt());
        assertTrue(smeltingReader.readBoolean());
        assertTrue(smeltingReader.readBoolean());
        // blasting だけ開：bookId 2。
        byte[] blasting = PlayTranslator.translateRecipeBook736To751(
                craftingBookDataSettingsPayload(
                        new boolean[]{false, false, false, false, true, false, false, false}));
        Reader blastingReader = new Reader(blasting);
        assertEquals(2, blastingReader.readVarInt());
        assertTrue(blastingReader.readBoolean());
        assertFalse(blastingReader.readBoolean());
        // smoking だけ開：bookId 3。
        byte[] smoking = PlayTranslator.translateRecipeBook736To751(
                craftingBookDataSettingsPayload(
                        new boolean[]{false, false, false, false, false, false, true, true}));
        Reader smokingReader = new Reader(smoking);
        assertEquals(3, smokingReader.readVarInt());
        assertTrue(smokingReader.readBoolean());
        assertTrue(smokingReader.readBoolean());
        // どれも開いていない：bookId 0・open=false・filter=craftingFilter。
        byte[] none = PlayTranslator.translateRecipeBook736To751(
                craftingBookDataSettingsPayload(
                        new boolean[]{false, true, false, false, false, false, false, false}));
        Reader noneReader = new Reader(none);
        assertEquals(0, noneReader.readVarInt());
        assertFalse(noneReader.readBoolean());
        assertTrue(noneReader.readBoolean());
        // 複数開いている：優先順で crafting（bookId 0）を選ぶ。
        byte[] multi = PlayTranslator.translateRecipeBook736To751(
                craftingBookDataSettingsPayload(
                        new boolean[]{true, false, true, true, true, true, true, true}));
        Reader multiReader = new Reader(multi);
        assertEquals(0, multiReader.readVarInt());
        assertTrue(multiReader.readBoolean());
        assertFalse(multiReader.readBoolean());
    }

    @Test
    void recipeBook736To751TypeZeroReturnsNull() {
        // type=0 は displayed_recipe 側の変換器を使うため null。
        Writer writer = new Writer();
        writer.writeVarInt(0);
        writer.writeString("minecraft:stone");
        assertNull(PlayTranslator.translateRecipeBook736To751(writer.bytes()));
    }

    // ------------------------------------------------------------------
    // Displayed Recipe
    // ------------------------------------------------------------------

    @Test
    void displayedRecipe751To736ConvertsNotDrops() {
        // 旧版は落としていたが、実測により type=0 の crafting_book_data に
        // 変換できることが判明した（Stage B フォローアップ）。
        Writer writer = new Writer();
        writer.writeString("minecraft:stone");
        byte[] out = PlayTranslator.translateDisplayedRecipe751To736(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt()); // type=SHOWN
        assertEquals("minecraft:stone", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void displayedRecipe736To751Converts() {
        Writer writer = new Writer();
        writer.writeVarInt(0);
        writer.writeString("minecraft:stone");
        byte[] out = PlayTranslator.translateDisplayedRecipe736To751(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("minecraft:stone", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void displayedRecipe736To751TypeOneReturnsNull() {
        // type=1 は recipe_book 側の変換器を使うため null。
        assertNull(PlayTranslator.translateDisplayedRecipe736To751(
                craftingBookDataSettingsPayload(
                        new boolean[]{true, false, false, false, false, false, false, false})));
    }

    @Test
    void displayedRecipeRoundTrips() {
        Writer writer = new Writer();
        writer.writeString("minecraft:stone");
        byte[] as736 = PlayTranslator.translateDisplayedRecipe751To736(writer.bytes());
        assertArrayEquals(writer.bytes(),
                PlayTranslator.translateDisplayedRecipe736To751(as736));
    }
}
