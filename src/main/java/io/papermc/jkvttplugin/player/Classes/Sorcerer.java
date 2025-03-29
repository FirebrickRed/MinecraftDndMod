package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Sorcerer extends DndClass {

    public Sorcerer(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Sorcerer";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "sorcerer_icon", 0);
    }

    @Override
    public int[] getAvailableSpellSlots(int level) {
        int[] availableSpellSlots = new int[10];
        switch (level) {
            case 1:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 2;
                break;
            case 2:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 3;
                break;
            case 3:
                availableSpellSlots[0] = 4;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 2;
                break;
            case 4:
                availableSpellSlots[0] = 5;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                break;
            case 5:
                availableSpellSlots[0] = 5;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 2;
                break;
            case 6:
                availableSpellSlots[0] = 5;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                break;
            case 7:
                availableSpellSlots[0] = 5;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 1;
                break;
            case 8:
                availableSpellSlots[0] = 5;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 2;
                break;
            case 9:
                availableSpellSlots[0] = 5;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 1;
                break;
            case 10:
                availableSpellSlots[0] = 6;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                break;
            case 11:
                availableSpellSlots[0] = 6;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                availableSpellSlots[6] = 1;
                break;
            case 12:
                availableSpellSlots[0] = 6;
                availableSpellSlots[1] = 4;
                availableSpellSlots[2] = 3;
                availableSpellSlots[3] = 3;
                availableSpellSlots[4] = 3;
                availableSpellSlots[5] = 2;
                availableSpellSlots[6] = 1;
                break;
            case 13:
            case 14:
                availableSpellSlots[0] = 6;
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
                availableSpellSlots[0] = 6;
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
                availableSpellSlots[0] = 6;
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
                availableSpellSlots[0] = 6;
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
                availableSpellSlots[0] = 6;
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
                availableSpellSlots[0] = 6;
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

//    (a) a light crossbow and 20 bolts or (b) any simple weapon
//    (a) a component pouch or (b) an arcane focus
//    (a) a dungeoneer's pack or (b) an explorer's pack
//    Two daggers
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                DndWeapon.getWeapon("dagger").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack()
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("light_crossbow_or_any_simple_weapon", Arrays.asList(
                DndWeapon.getWeapon("club").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack(),
                DndWeapon.getWeapon("greatclub").toItemStack(),
                DndWeapon.getWeapon("handaxe").toItemStack(),
                DndWeapon.getWeapon("javelin").toItemStack(),
                DndWeapon.getWeapon("light_hammer").toItemStack(),
                DndWeapon.getWeapon("mace").toItemStack(),
                DndWeapon.getWeapon("quarterstaff").toItemStack(),
                DndWeapon.getWeapon("sickle").toItemStack(),
                DndWeapon.getWeapon("spear").toItemStack(),
                DndWeapon.getWeapon("light_crossbow").toItemStack()
        ));

        choices.put("component_pouch_or_arcane_focus", Arrays.asList(
                DndWeapon.getWeapon("club").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack()
        ));

        choices.put("dungeoneers_or_explorers_pack", Arrays.asList(
                DndWeapon.getWeapon("club").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack()
        ));

        return choices;
    }
}
