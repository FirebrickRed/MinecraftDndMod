package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** One wrap width everywhere, paragraphs kept; one spell description everywhere. */
class WrapAndSpellTextTest {

    @Test
    void wrapKeepsEveryLineWithinTheStandardWidth() {
        String text = "You hurl a mote of fire at a creature or object within range. Make a ranged spell attack against the target.";
        for (String line : Util.wrapText(text)) assertTrue(line.length() <= Util.WRAP_WIDTH, line);
    }

    @Test
    void wrapKeepsParagraphBreaks() {
        List<String> lines = Util.wrapText("First paragraph.\n\nSecond paragraph.");
        assertEquals(List.of("First paragraph.", "", "Second paragraph."), lines);
    }

    @Test
    void aSpellDescribesItselfWithTheRulesThatMatterForPicking() {
        TestContent.load();
        DndSpell fireBolt = SpellLoader.getSpell("fire_bolt");
        assertNotNull(fireBolt);
        String text = String.join("\n", fireBolt.detailLore().stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList());
        assertTrue(text.contains("Cantrip"), text);
        assertTrue(text.contains("Range:"), text);
        assertTrue(text.contains("Casting Time:"), text);
        for (Component line : fireBolt.detailLore()) {
            assertTrue(PlainTextComponentSerializer.plainText().serialize(line).length() <= Util.WRAP_WIDTH + 20,
                    "a tooltip line ran long: " + line);
        }
    }

    /** Friends' long material component made its card huge: no spell's tooltip line runs past the width. */
    @Test
    void everySpellCardFitsTheStandardWidth() {
        TestContent.load();
        java.util.List<String> tooLong = new java.util.ArrayList<>();
        for (DndSpell spell : SpellLoader.getAllSpells()) {
            for (Component line : spell.detailLore()) {
                String text = PlainTextComponentSerializer.plainText().serialize(line);
                if (text.length() > Util.WRAP_WIDTH) tooLong.add(spell.getId() + ": " + text);
            }
        }
        assertTrue(tooLong.isEmpty(), "Lines past " + Util.WRAP_WIDTH + " chars:\n" + String.join("\n", tooLong));
    }
}
