package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Warlock extends DndClass {

    public Warlock(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Warlock";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "warlock_icon", 0);
    }

    @Override
    public int[] getAvailableSpellSlots(int level) {
        int[] availableSpellSlots = new int[10];

        switch (level) {
            case 1:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 1;
                break;
            case 2:
                availableSpellSlots[0] = 2;
                availableSpellSlots[1] = 2;
                break;
            case 3:
                availableSpellSlots[0] = 2;
                availableSpellSlots[2] = 2;
                break;
            case 4:
                availableSpellSlots[0] = 3;
                availableSpellSlots[2] = 2;
                break;
            case 5, 6:
                availableSpellSlots[0] = 3;
                availableSpellSlots[3] = 2;
                break;
            case 7, 8:
                availableSpellSlots[0] = 3;
                availableSpellSlots[4] = 2;
                break;
            case 9:
                availableSpellSlots[0] = 3;
                availableSpellSlots[5] = 2;
                break;
            case 10:
                availableSpellSlots[0] = 4;
                availableSpellSlots[5] = 2;
                break;
            case 11, 12, 13, 14, 15, 16:
                availableSpellSlots[0] = 4;
                availableSpellSlots[5] = 3;
                break;
            case 17, 18, 19, 20:
                availableSpellSlots[0] = 4;
                availableSpellSlots[5] = 4;
                break;
        }

        return availableSpellSlots;
    }

//    public List<ItemStack> getSpellList() {
//        return Arrays.asList(
//                new ItemStack(Material.PAPER),
//                new ItemStack(Material.PAPER)
//        );
//    }

//    (a) a light crossbow and 20 bolts or (b) any simple weapon
//    (a) a component pouch or (b) an arcane focus
//    (a) a scholar's pack or (b) a dungeoneer's pack
//    Leather armor, any simple weapon, and two daggers
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                new ItemStack(Material.LEATHER_CHESTPLATE),
                DndWeapon.getWeapon("dagger").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack()
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("any_simple_weapon", Arrays.asList(
                DndWeapon.getWeapon("club").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack(),
                DndWeapon.getWeapon("greatclub").toItemStack(),
                DndWeapon.getWeapon("handaxe").toItemStack(),
                DndWeapon.getWeapon("javelin").toItemStack(),
                DndWeapon.getWeapon("light_hammer").toItemStack(),
                DndWeapon.getWeapon("mace").toItemStack(),
                DndWeapon.getWeapon("quarterstaff").toItemStack(),
                DndWeapon.getWeapon("sickle").toItemStack(),
                DndWeapon.getWeapon("spear").toItemStack()
        ));

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

        choices.put("scholars_or_dungeoneers_pack", Arrays.asList(
                DndWeapon.getWeapon("club").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack()
        ));

        return choices;
    }
}
