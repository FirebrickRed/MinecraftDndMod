package io.papermc.jkvttplugin.player.Races;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class Plasmoid extends DndRace {
    public Plasmoid() {
        super("Plasmoid");
    }

//    @Override
//    public List<String> getAttributeBonuses() {
//        // Ability Score Increase. Increase one ability score by 2, and increase a different one by 1, or increase three different scores by 1.
//        return Arrays.asList("+2", "+1");
//    }

    @Override
    public String getCreatureType() {
        return "Ooze";
    }

    @Override
    public int getBreathDuration() {
        return 1000; // 50 seconds IRL = 1 in-game hour
    }

    @Override
    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getRaceName()), null, "plasmoid_icon", 0);
    }
}

/*

Creature Type. You are an Ooze.
Size. You are Medium or Small. You choose the size when you gain this race.
Speed. Your walking speed is 30 feet.
Amorphous. You can squeeze through a space as narrow as 1 inch wide, provided you are wearing and carrying nothing. You also have advantage on ability checks you make to initiate or escape a grapple.
Darkvision. You can see in dim light within 60 feet of yourself as if it were bright light, and in darkness as if it were dim light. You discern colors in that darkness only as shades of gray.
Hold Breath. You can hold your breath for 1 hour.
Natural Resilience. You have resistance to acid and poison damage, and you have advantage on saving throws against being poisoned.
Shape Self. As an action, you can reshape your body to give yourself a head, one or two arms, one or two legs, and makeshift hands and feet, or you can revert to a limbless blob. While you have a humanlike shape, you can wear clothing and armor made for a Humanoid of your size. As a bonus action, you can extrude a pseudopod that is up to 6 inches wide and 10 feet long or reabsorb it into your body. As part of the same bonus action, you can use this pseudopod to manipulate an object, open or close a door or container, or pick up or set down a Tiny object. The pseudopod contains no sensory organs and can’t attack, activate magic items, or lift more than 10 pounds.
Languages. You can speak, read, and write Common and one other language that you and your DM agree is appropriate.

 */
