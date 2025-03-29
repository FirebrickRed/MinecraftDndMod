package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Monk extends DndClass {

    public Monk(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Monk";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "monk_icon", 0);
    }

//    (a) a shortsword or (b) any simple weapon
//    (a) a dungeoneer's pack or (b) an explorer's pack
//    10 darts
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack(),
                DndWeapon.getWeapon("dart").toItemStack()
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("shortsword_or_simple_melee_weapon", Arrays.asList(
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
