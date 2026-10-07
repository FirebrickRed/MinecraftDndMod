package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.DndSubClass;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Things that used to be decided by a name in the code and are data now (#261): which tools pick
 * locks, and the item under a subclass's menu tile.
 */
class HomebrewFriendlyTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void thievesToolsPickLocksBecauseOfTheirTag() {
        assertEquals(List.of("thieves_tools"), LockTools.ids());
        assertTrue(LockTools.is("thieves_tools"));
        assertTrue(LockTools.is("Thieves_Tools"));
        assertFalse(LockTools.is("disguise_kit"), "a tool, but not a lock tool");
        assertFalse(LockTools.is(null));
    }

    /** With two lock tools in the content, the prompt offers the one this character can actually use. */
    @Test
    void theOfferedToolIsTheOneTheyCarryAndKnow() {
        CharacterSheet rogue = character("halfling", "lightfoot", "rogue", "criminal", scores(Ability.DEXTERITY, 16));
        assertTrue(rogue.isProficientWithTool("thieves_tools"));
        List<String> all = List.of("skeleton_keys", "thieves_tools");

        assertEquals("thieves_tools", LockTools.pick(all, List.of("skeleton_keys", "thieves_tools"), rogue), "carried and proficient");
        assertEquals("skeleton_keys", LockTools.pick(all, List.of("skeleton_keys"), rogue), "carried beats known");
        assertEquals("thieves_tools", LockTools.pick(all, List.of(), rogue), "carrying none: the one they know");

        CharacterSheet fighter = character("human", null, "fighter", "soldier", scores());
        assertEquals("skeleton_keys", LockTools.pick(all, List.of(), fighter), "nothing to go on: the first");
        assertNull(LockTools.pick(List.of(), List.of(), rogue), "content with no lock tools offers none");
    }

    /** A subclass tile's item comes from YAML: its own material, else its class's subclass_material, else paper. */
    @Test
    void subclassTilesTakeTheirItemFromYaml() {
        for (DndSubClass sub : ClassLoader.getClass("cleric").getSubclasses().values()) {
            assertEquals(Material.ENCHANTED_BOOK, sub.getIconMaterial(), sub.getName());
        }
        assertEquals(Material.BOOK, ClassLoader.getClass("warlock").getSubclasses().values().iterator().next().getIconMaterial());
        assertEquals(Material.BLAZE_POWDER, ClassLoader.getClass("sorcerer").getSubclasses().values().iterator().next().getIconMaterial());

        DndSubClass own = new DndSubClass();
        own.setParentClass("cleric"); // the class name decides nothing any more
        assertEquals(Material.PAPER, own.getIconMaterial(), "no material anywhere");
        own.setMaterial("nether_star");
        assertEquals(Material.NETHER_STAR, own.getIconMaterial());
    }
}
