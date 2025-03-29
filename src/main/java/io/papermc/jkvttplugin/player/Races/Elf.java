package io.papermc.jkvttplugin.player.Races;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Elf extends DndRace {
    private final List<DndRace> subRaces;

    public Elf(List<DndRace> subRaces) {
        super("Elf");
        this.subRaces = subRaces;
    }

//    @Override
//    public List<Component> getAttributeBonuses() {
//        // Ability Score Increase. Increase one ability score by 2, and increase a different one by 1, or increase three different scores by 1.
//        List<Component> attributeBonuses = new ArrayList<>();
//        attributeBonuses.add(Component.text("+2 Dexterity"));
//        return attributeBonuses;
//    }

    @Override
    public List<DndRace> getSubRaces() {
        return subRaces;
    }

    @Override
    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getRaceName()), null, "elf_icon", 0);
    }
}

/*

Ability Score Increase. Your Dexterity score increases by 2.
Age. Although elves reach physical maturity at about the same age as humans, the elven understanding of adulthood goes beyond physical growth to encompass worldly experience. An elf typically claims adulthood and an adult name around the age of 100 and can live to be 750 years old.
Alignment. Elves love freedom, variety, and self-expression, so they lean strongly towards the gentler aspects of chaos. They value and protect others' freedom as well as their own, and are good more often than not. Drow are an exception; their exile into the Underdark has made them vicious and dangerous. Drow are more often evil than not.
Size. Elves range from under 5 to over 6 feet tall and have slender builds. Your size is Medium.
Speed. Your base walking speed is 30 feet.
Darkvision. Accustomed to twilit forests and the night sky, you have superior vision in dark and dim conditions. You can see in dim light within 60 feet of you as if it were bright light, and in darkness as if it were dim light. You can't discern color in darkness, only shades of gray.
Fey Ancestry. You have advantage on saving throws against being charmed, and magic can't put you to sleep.
Trance. Elves do not sleep. Instead they meditate deeply, remaining semi-conscious, for 4 hours a day. The Common word for this meditation is "trance." While meditating, you dream after a fashion; such dreams are actually mental exercises that have become reflexive after years of practice. After resting in this way, you gain the same benefit a human would from 8 hours of sleep.
Keen Senses. You have proficiency in the Perception skill.
Languages. You can speak, read, and write Common and Elven.

 */
