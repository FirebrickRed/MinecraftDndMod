package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Ranger extends DndClass {

    public Ranger(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Ranger";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "ranger_icon", 0);
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

//    (a) scale mail or (b) leather armor
//    (a) two shortswords or (b) two simple melee weapons
//    (a) a dungeoneer's pack or (b) an explorer's pack
//    A longbow and a quiver of 20 arrows
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                new ItemStack(Material.BOW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.STICK)
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("scale_mail_or_leather_armor", Arrays.asList(
                new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                new ItemStack(Material.LEATHER_CHESTPLATE)
        ));

        choices.put("1shortsword_or_simple_melee_weapon", Arrays.asList(
                DndWeapon.getWeapon("shortsword").toItemStack(),
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

        choices.put("2shortsword_or_simple_melee_weapon", Arrays.asList(
                DndWeapon.getWeapon("shortsword").toItemStack(),
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

        choices.put("dungeoneers_or_explorers_pack", Arrays.asList(
                DndWeapon.getWeapon("club").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack()
        ));

        return choices;
    }
}
