package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Rogue extends DndClass {

    public Rogue(int hitDie) {
        super(hitDie);
    }

    @Override
    public String getClassName() {
        return "Rogue";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "rogue_icon", 0);
    }

//    (a) a rapier or (b) a shortsword
//    (a) a shortbow and quiver of 20 arrows or (b) a shortsword
//    (a) a burglar's pack, (b) dungeoneer's pack, or (c) an explorer's pack
//    Leather armor, two daggers, and thieves' tools
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                new ItemStack(Material.LEATHER_CHESTPLATE),
                DndWeapon.getWeapon("dagger").toItemStack(),
                DndWeapon.getWeapon("dagger").toItemStack(),
                new ItemStack(Material.STICK)
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("burglar_or_dungeoneer_or_explorer_pack", Arrays.asList(
                new ItemStack(Material.BUNDLE),
                new ItemStack(Material.BUNDLE),
                new ItemStack(Material.BUNDLE)
        ));

        choices.put("shortbow_or_shortsword", Arrays.asList(
                DndWeapon.getWeapon("shortbow").toItemStack(),
                DndWeapon.getWeapon("shortsword").toItemStack())
        );

        choices.put("rapier_or_shortsword", Arrays.asList(
                DndWeapon.getWeapon("rapier").toItemStack(),
                DndWeapon.getWeapon("shortsword").toItemStack())
        );

        return choices;
    }
}
