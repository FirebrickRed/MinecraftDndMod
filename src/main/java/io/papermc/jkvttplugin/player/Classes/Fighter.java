package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Fighter extends DndClass {

    public Fighter(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Fighter";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "fighter_icon", 0);
    }

//    (a) chain mail or (b) leather, longbow, and 20 arrows
//    (a) a martial weapon and a shield or (b) two martial weapons
//    (a) a light crossbow and 20 bolts or (b) two handaxes
//    (a) a dungeoneer's pack or (b) an explorer's pack
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                new ItemStack(Material.LEATHER_CHESTPLATE),
                new ItemStack(Material.BUNDLE),
                new ItemStack(Material.STICK)
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("shield_or_simple_weapon", Arrays.asList(
                new ItemStack(Material.SHIELD),
                new ItemStack(Material.WOODEN_SWORD)
        ));

        choices.put("scimitar_or_simple_melee_weapon", Arrays.asList(
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.WOODEN_AXE)
        ));

        return choices;
    }
}
