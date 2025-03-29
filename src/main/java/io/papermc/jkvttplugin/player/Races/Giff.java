package io.papermc.jkvttplugin.player.Races;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class Giff extends DndRace {
    public Giff() {
        super("Giff");
    }

//    @Override
//    public List<Component> getAttributeBonuses() {
//        // Ability Score Increase. Increase one ability score by 2, and increase a different one by 1, or increase three different scores by 1.
//        return Arrays.asList("+2", "+1");
//    }

    @Override
    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getRaceName()), null, "giff_icon", 0);
    }
}

/*

Size. You are Medium.
Speed. Your walking speed is 30 feet, and you have a swimming speed equal to your walking speed.
Astral Spark. Your psychic connection to the Astral Plane enables you to mystically access a spark of divine power, which you can channel through your weapons. When you hit a target with a simple or martial weapon, you can cause the target to take extra force damage equal to your proficiency bonus. You can use this trait a number of times equal to your proficiency bonus, but you can use it no more than once per turn. You regain all expended uses when you finish a long rest.
Firearms Mastery. You have a mystical connection to firearms that traces back to the gods of the giff, who delighted in such weapons. You have proficiency with all firearms and ignore the loading property of any firearm. In addition, attacking at long range with a firearm doesn't impose disadvantage on your attack roll.
Hippo Build. You have advantage on Strength-based ability checks and Strength saving throws. In addition, you count as one size larger when determining your carrying capacity and the weight you can push, drag, or lift.
Languages. You can speak, read, and write Common and one other language that you and your DM agree is appropriate.

 */
