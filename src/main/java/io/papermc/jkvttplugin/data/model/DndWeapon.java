package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DndWeapon {
    private String id;
    private String name;
    private String category; // Simple, Martial
    private String type; // Melee, Ranged
    private String damage; // "1d6", "1d8", etc.
    private String damageType; // Slashing, Piercing, Bludgeoning, etc.
    private Set<String> properties; // Finesse, Light, Heavy, etc.
    private int normalRange; // For ranged weapons
    private int longRange; // For ranged weapons
    private String weight; // "2 lb", "6 lb", etc.
    private Cost cost; // Structured cost with amount and currency
    private String description;
    private String icon; // For Minecraft item model

    public DndWeapon() {}

    // Core combat methods
    public boolean isFinesse() {
        return properties != null && properties.contains("finesse");
    }

    public boolean isRanged() {
        return "ranged".equalsIgnoreCase(type);
    }

    public boolean isMelee() {
        return "melee".equalsIgnoreCase(type);
    }

    public boolean hasProperty(String property) {
        return properties != null && properties.contains(property.toLowerCase());
    }

    public Ability getPrimaryAbility() {
        if (isFinesse()) {
            return null; // Player chooses between STR/DEX
        }
        return isRanged() ? Ability.DEXTERITY : Ability.STRENGTH;
    }

    public boolean isProficient(Set<String> weaponProficiencies) {
        if (weaponProficiencies == null) return false;

        // Check specific weapon proficiency
        if (weaponProficiencies.contains(Util.normalize(name))) {
            return true;
        }

        // Check category proficiency (simple weapons, martial weapons)
        String categoryProf = Util.normalize(category + " weapons");
        if (weaponProficiencies.contains(categoryProf)) {
            return true;
        }

        return false;
    }

    public ItemStack createItemStack() {
        List<Component> lore = new ArrayList<>();

        // Add damage info
        lore.add(Component.text("Damage: " + damage + " " + damageType, NamedTextColor.GRAY));

        // Add properties
        if (properties != null && !properties.isEmpty()) {
            StringBuilder props = new StringBuilder("Properties: ");
            props.append(String.join(", ", properties.stream()
                    .map(Util::prettify).toArray(String[]::new)));
            lore.add(Component.text(props.toString(), NamedTextColor.GRAY));
        }

        // Add range for ranged weapons
        if (isRanged() && normalRange > 0) {
            String rangeText = longRange > 0 ?
                    "Range: " + normalRange + "/" + longRange + " ft" :
                    "Range: " + normalRange + " ft";
            lore.add(Component.text(rangeText, NamedTextColor.GRAY));
        }

        // Add weight and cost
        if (weight != null && !weight.isEmpty()) {
            lore.add(Component.text("Weight: " + weight, NamedTextColor.DARK_GRAY));
        }
        // ToDo: can cost be null?
        if (cost != null) {
            lore.add(Component.text("Cost: " + cost.toDisplayString(), NamedTextColor.GOLD));
        }

        // Add description
        if (description != null && !description.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text(description, NamedTextColor.YELLOW));
        }

        ItemStack item = Util.createItem(
                Component.text(name, NamedTextColor.WHITE),
                lore,
                icon != null ? icon : "weapon_" + Util.normalize(name),
                1
        );

        // Tag with item_id for reliable identification (Issue #75)
        ItemUtil.tagItemId(item, id);

        return item;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDamage() { return damage; }
    public void setDamage(String damage) { this.damage = damage; }

    public String getDamageType() { return damageType; }
    public void setDamageType(String damageType) { this.damageType = damageType; }

    public Set<String> getProperties() { return properties; }
    public void setProperties(Set<String> properties) { this.properties = properties; }

    public int getNormalRange() { return normalRange; }
    public void setNormalRange(int normalRange) { this.normalRange = normalRange; }

    public int getLongRange() { return longRange; }
    public void setLongRange(int longRange) { this.longRange = longRange; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
