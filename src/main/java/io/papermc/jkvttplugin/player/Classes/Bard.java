package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Bard extends DndClass {

    public Bard(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Bard";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "bard_icon", 0);
    }

    @Override
    public int[] getAvailableSpellSlots(int level) {
        int[] availableSpellSlots = new int[10];
        switch (level) {
            case 1:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 2;
                break;
            case 2:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 3;
                break;
            case 3:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 2;
                break;
            case 4:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                break;
            case 5:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 2;
                break;
            case 6:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                break;
            case 7:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 1;
                break;
            case 8:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 2;
                break;
            case 9:
                availableSpellSlots[0] = 3;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 1;
                break;
            case 10:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                break;
            case 11, 12:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                availableSpellSlots[6] = 1;
                break;
            case 13:
            case 14:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                availableSpellSlots[6] = 1;
                availableSpellSlots[7] = 1;
                break;
            case 15:
            case 16:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                availableSpellSlots[6] = 1;
                availableSpellSlots[7] = 1;
                availableSpellSlots[8] = 1;
                break;
            case 17:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                availableSpellSlots[6] = 1;
                availableSpellSlots[7] = 1;
                availableSpellSlots[8] = 1;
                availableSpellSlots[9] = 1;
                break;
            case 18:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 3;
                availableSpellSlots[6] = 1;
                availableSpellSlots[7] = 1;
                availableSpellSlots[8] = 1;
                availableSpellSlots[9] = 1;
                break;
            case 19:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 3;
                availableSpellSlots[6] = 2;
                availableSpellSlots[7] = 1;
                availableSpellSlots[8] = 1;
                availableSpellSlots[9] = 1;
                break;
            case 20:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 3;
                availableSpellSlots[6] = 2;
                availableSpellSlots[7] = 2;
                availableSpellSlots[8] = 1;
                availableSpellSlots[9] = 1;
                break;
        }
        return availableSpellSlots;
    }

//    (a) a rapier, (b) a longsword, or (c) any simple weapon
//    (a) a diplomat's pack or (b) an entertainer's pack
//    (a) a lute or (b) any other musical instrument
//    Leather armor and a dagger
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
