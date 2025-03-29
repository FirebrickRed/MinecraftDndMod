package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class BloodHunter extends DndClass {

    public BloodHunter(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "BloodHunter";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "bloodhunter_icon", 0);
    }

//    (a) a martial weapon or (b) two simple weapons
//    (a) a light crossbow and 20 bolts or (b) hand crossbow and 20 bolts
//    (a) studded leather armor or (b) scale mail armor
//    an explorer's pack and alchemist's supplies
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                new ItemStack(Material.BUNDLE),
                new ItemStack(Material.BUNDLE),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW)
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("leather_or_scale_armor", Arrays.asList(
                new ItemStack(Material.LEATHER_CHESTPLATE),
                new ItemStack(Material.CHAINMAIL_CHESTPLATE)
        ));

        choices.put("light_or_hand_crossbow", Arrays.asList(
                DndWeapon.getWeapon("light_crossbow").toItemStack(),
                DndWeapon.getWeapon("hand_crossbow").toItemStack()
        ));

        return choices;
    }
}
