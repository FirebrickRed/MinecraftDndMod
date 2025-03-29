package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Artificer extends DndClass {

    public Artificer(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Artificer";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "artificer_icon", 0);
    }

    @Override
    public int[] getAvailableSpellSlots(int level) {
        int[] availableSpellSlots = new int[10];
        switch (level) {
            case 1, 2:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 2;
                break;
            case 3, 4:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 3;
                break;
            case 5, 6:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 2;
                break;
            case 7, 8:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                break;
            case 9:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 2;
                break;
            case 10:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 2;
                break;
            case 11, 12:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                break;
            case 13:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 1;
                break;
            case 14:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 1;
                break;
            case 15:
            case 16:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 2;
                break;
            case 17, 18:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 1;
                break;
            case 19, 20:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                break;
        }
        return availableSpellSlots;
    }

//    any two simple weapons
//    a light crossbow and 20 bolts
//    (a) studded leather armor or (b) scale mail
//    thieves’ tools and a dungeoneer’s pack
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
