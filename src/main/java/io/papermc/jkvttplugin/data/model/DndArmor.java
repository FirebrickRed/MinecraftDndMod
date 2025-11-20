package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DndArmor {
    private String id;
    private String name;
    private String category;
    private int baseAC;
    private boolean addsDexModifier = false;
    private int maxDexModifier = -1;
    private int strengthRequirement = 0;
    private boolean stealthDisadvantage = false;
    private String weight;
    private Cost cost;
    private String description;
    private String icon;
    private Material material;

    public DndArmor() {}

    public int calculateAC(int dexModifier, int strengthScore) {
        if (strengthScore < strengthRequirement) return -1;

        int ac = baseAC;

        if (addsDexModifier) {
            int dexToAdd = dexModifier;
            if (maxDexModifier >= 0) {
                dexToAdd = Math.min(dexModifier, maxDexModifier);
            }
            ac += dexToAdd;
        }

        return ac;
    }

    public boolean canWear(int strengthScore, Set<String> armorProficiencies) {
        if (strengthScore < strengthRequirement) return false;

        return isProficient(armorProficiencies);
    }

    public boolean isProficient(Set<String> armorProficiencies) {
        if (armorProficiencies == null) return false;

        if (armorProficiencies.contains(Util.normalize(name))) return true;

        String categoryProf = Util.normalize(category + " armor");
        if (armorProficiencies.contains(categoryProf)) return true;

        if ("shield".equalsIgnoreCase(category) && armorProficiencies.contains("shields")) return true;

        return false;
    }

    public boolean isLight() {
        return "light".equalsIgnoreCase(category);
    }

    public boolean isMedium() {
        return "medium".equalsIgnoreCase(category);
    }

    public boolean isHeavy() {
        return "heavy".equalsIgnoreCase(category);
    }

    public boolean isShield() {
        return "shield".equalsIgnoreCase(category);
    }

    public ItemStack createItemStack() {
        List<Component> lore = new ArrayList<>();

        String acText = "AC: " + baseAC;
        if (addsDexModifier) {
            if (maxDexModifier >= 0) {
                acText += " + Dex modifier (max " + maxDexModifier + ")";
            } else {
                acText += " + Dex modifier";
            }
        }
        lore.add(Component.text(acText, NamedTextColor.BLUE));

        if (strengthRequirement > 0) {
            lore.add(Component.text("Strength Requirement: " + strengthRequirement, NamedTextColor.RED));
        }

        if (stealthDisadvantage) {
            lore.add(Component.text("Stealth Disadvantage", NamedTextColor.RED));
        }

        lore.add(Component.text("Type: " + Util.prettify(category) + " Armor", NamedTextColor.GRAY));

        if (weight != null && !weight.isEmpty()) {
            lore.add(Component.text("Weight: " + weight, NamedTextColor.DARK_GRAY));
        }
        if (cost != null) {
            lore.add(Component.text("Cost: " + cost.toDisplayString(), NamedTextColor.GOLD));
        }

        if (description != null && !description.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text(description, NamedTextColor.YELLOW));
        }

        ItemStack item = Util.createItem(
                Component.text(name, NamedTextColor.WHITE),
                lore,
                icon != null ? icon : "armor_" + Util.normalize(name),
                1,
                getMaterial()
        );

        // Tag with standardized item_id for reliable identification (Issue #75)
        ItemUtil.tagItemId(item, id);

        return item;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }

    public int getBaseAC() { return baseAC; }
    public void setBaseAC(int baseAC) { this.baseAC = baseAC; }

    public boolean isAddsDexModifier() { return addsDexModifier; }
    public void setAddsDexModifier(boolean addsDexModifier) { this.addsDexModifier = addsDexModifier; }

    public int getMaxDexModifier() { return maxDexModifier; }
    public void setMaxDexModifier(int maxDexModifier) { this.maxDexModifier = maxDexModifier; }

    public int getStrengthRequirement() { return strengthRequirement; }
    public void setStrengthRequirement(int strengthRequirement) { this.strengthRequirement = strengthRequirement; }

    public boolean isStealthDisadvantage() { return stealthDisadvantage; }
    public void setStealthDisadvantage(boolean stealthDisadvantage) { this.stealthDisadvantage = stealthDisadvantage; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Material getMaterial() {
        return material != null ? material : isShield() ? Material.SHIELD : Material.LEATHER_CHESTPLATE;
    }
    public void setMaterial(Material material) {
        this.material = material;
    }
}
