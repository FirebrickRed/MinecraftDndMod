package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndWeapon;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Barbarian extends DndClass {
    private int rageCharges;

    public Barbarian(int hitDie) {
        super(hitDie);
    }

    public boolean canRage() {
        return rageCharges > 0;
    }

    public void useRage() {
        if (rageCharges > 0) {
            rageCharges--;
        }
    }

    @Override
    public String getClassName() {
        return "Barbarian";
    }

    @Override
    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getClassName()), null, "barbarian_icon", 0);
    }

//    (a) a greataxe or (b) any martial melee weapon
//    (a) two handaxes or (b) any simple weapon
//    An explorer's pack and four javelins
    @Override
    public List<ItemStack> getBaseEquipment() {
        return Arrays.asList(
                new ItemStack(Material.BUNDLE),
                DndWeapon.getWeapon("javelin").toItemStack(),
                DndWeapon.getWeapon("javelin").toItemStack(),
                DndWeapon.getWeapon("javelin").toItemStack(),
                DndWeapon.getWeapon("javelin").toItemStack()
        );
    }

    @Override
    public Map<String, List<ItemStack>> getGearChoices() {
        Map<String, List<ItemStack>> choices = new HashMap<>();

        choices.put("greataxe_or_martial_melee_weapon", Arrays.asList(
                DndWeapon.getWeapon("battleaxe").toItemStack(),
                DndWeapon.getWeapon("flail").toItemStack(),
                DndWeapon.getWeapon("glaive").toItemStack(),
                DndWeapon.getWeapon("greataxe").toItemStack(),
                DndWeapon.getWeapon("greatsword").toItemStack(),
                DndWeapon.getWeapon("halberd").toItemStack(),
                DndWeapon.getWeapon("lance").toItemStack(),
                DndWeapon.getWeapon("longsword").toItemStack(),
                DndWeapon.getWeapon("maul").toItemStack(),
                DndWeapon.getWeapon("morningstar").toItemStack(),
                DndWeapon.getWeapon("pike").toItemStack(),
                DndWeapon.getWeapon("rapier").toItemStack(),
                DndWeapon.getWeapon("scimitar").toItemStack(),
                DndWeapon.getWeapon("shortsword").toItemStack(),
                DndWeapon.getWeapon("trident").toItemStack(),
                DndWeapon.getWeapon("warpick").toItemStack(),
                DndWeapon.getWeapon("warhammer").toItemStack(),
                DndWeapon.getWeapon("whip").toItemStack()
        ));

        // If Lizz complains tell her to stfu and stay in her lane
        choices.put("two_handaxes_or_any_simple_weapon", Arrays.asList(
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

        return choices;
    }
}
