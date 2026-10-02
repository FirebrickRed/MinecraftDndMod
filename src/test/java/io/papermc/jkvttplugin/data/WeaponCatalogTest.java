package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every weapon on the PHB weapon table exists, in the right category (#playtest: a barbarian's
 * "any martial melee weapon" had no battleaxe, and nothing noticed). The content validator catches
 * an id that's referenced but missing; this catches one that's simply never been written.
 */
class WeaponCatalogTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    /** id → "category type", PHB p.149. */
    private static final Map<String, String> PHB = Map.ofEntries(
            // Simple melee
            Map.entry("club", "simple melee"), Map.entry("dagger", "simple melee"), Map.entry("greatclub", "simple melee"),
            Map.entry("handaxe", "simple melee"), Map.entry("javelin", "simple melee"), Map.entry("light_hammer", "simple melee"),
            Map.entry("mace", "simple melee"), Map.entry("quarterstaff", "simple melee"), Map.entry("sickle", "simple melee"),
            Map.entry("spear", "simple melee"),
            // Simple ranged
            Map.entry("light_crossbow", "simple ranged"), Map.entry("dart", "simple ranged"),
            Map.entry("shortbow", "simple ranged"), Map.entry("sling", "simple ranged"),
            // Martial melee
            Map.entry("battleaxe", "martial melee"), Map.entry("flail", "martial melee"), Map.entry("glaive", "martial melee"),
            Map.entry("greataxe", "martial melee"), Map.entry("greatsword", "martial melee"), Map.entry("halberd", "martial melee"),
            Map.entry("lance", "martial melee"), Map.entry("longsword", "martial melee"), Map.entry("maul", "martial melee"),
            Map.entry("morningstar", "martial melee"), Map.entry("pike", "martial melee"), Map.entry("rapier", "martial melee"),
            Map.entry("scimitar", "martial melee"), Map.entry("shortsword", "martial melee"), Map.entry("trident", "martial melee"),
            Map.entry("war_pick", "martial melee"), Map.entry("warhammer", "martial melee"), Map.entry("whip", "martial melee"),
            // Martial ranged
            Map.entry("blowgun", "martial ranged"), Map.entry("hand_crossbow", "martial ranged"),
            Map.entry("heavy_crossbow", "martial ranged"), Map.entry("longbow", "martial ranged"), Map.entry("net", "martial ranged"));

    @Test
    void everyPhbWeaponExistsInItsCategory() {
        assertEquals(37, PHB.size());
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : PHB.entrySet()) {
            DndWeapon w = WeaponLoader.getWeapon(e.getKey());
            if (w == null) { problems.add(e.getKey() + ": missing"); continue; }
            String actual = (w.getCategory() + " " + w.getType()).toLowerCase();
            if (!actual.equals(e.getValue())) problems.add(e.getKey() + ": " + actual + ", should be " + e.getValue());
        }
        assertTrue(problems.isEmpty(), "PHB weapons: " + problems);
    }

    /**
     * A blowgun's flat 1 plus the attacker's modifier is one number ("4"), not "1+3", which the dice
     * roller can't read: every blowgun hit would have failed to roll.
     */
    @Test
    void flatWeaponDamageWithAModifierRolls() {
        var sheet = TestContent.character("human", null, "rogue", "criminal",
                TestContent.scores(io.papermc.jkvttplugin.data.model.enums.Ability.DEXTERITY, 16));
        String dmg = io.papermc.jkvttplugin.combat.AttackHandler.buildPlayerDamageString(sheet, WeaponLoader.getWeapon("blowgun"));
        var r = io.papermc.jkvttplugin.util.DiceRoller.rollOrFlat(dmg);
        assertNotNull(r, "'" + dmg + "' should roll");
        assertEquals(1 + 3, r.total(), "1 + DEX 3");
        assertNotNull(io.papermc.jkvttplugin.util.DiceRoller.rollOrFlat(
                io.papermc.jkvttplugin.combat.AttackHandler.buildPlayerDamageString(sheet, WeaponLoader.getWeapon("net"))));
    }
}
