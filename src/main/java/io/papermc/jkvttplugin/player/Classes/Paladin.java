package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Paladin extends DndClass {

    public Paladin(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Paladin";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "paladin_icon", 0);
    }

    @Override
    public int[] getAvailableSpellSlots(int level) {
        int[] availableSpellSlots = new int[10];

        switch (level) {
            case 2:
                availableSpellSlots[1] = 2;
                break;
            case 3, 4:
                availableSpellSlots[1] = 3;
                break;
            case 5, 6:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 2;
                break;
            case 7, 8:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                break;
            case 9, 10:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 2;
                break;
            case 11, 12:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                break;
            case 13, 14:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 1;
                break;
            case 15, 16:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 2;
                break;
            case 17, 18:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 1;
                break;
            case 19, 20:
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                break;
        }

        return availableSpellSlots;
    }

//    (a) a martial weapon and a shield or (b) two martial weapons
//    (a) five javelins or (b) any simple melee weapon
//    (a) a priest's pack or (b) an explorer's pack
//    Chain mail and a holy symbol
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                new ItemStack(Material.BUNDLE)
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
