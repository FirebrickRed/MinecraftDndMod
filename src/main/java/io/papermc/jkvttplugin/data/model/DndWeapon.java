package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
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
    private String customModel; // YAML custom_model: optional resource-pack model (opt-in, when pack art exists)
    private String material;    // YAML material: vanilla Minecraft item to render as (base + fallback)
    private String ammunition;  // YAML ammunition: the item id this weapon fires (#128)
    private int reach;          // YAML reach: melee reach in feet; 0 = unset, meaning the default 5
    private int recoveryChance = -1; // YAML recovery_chance: % odds of surviving recovery (#191); -1 = unset

    // ---- magic (#188) ----
    // A magic weapon names its mundane `base:` (a Longsword +2 is a longsword) and adds a `magic:`
    // block. The loader merges the base's stats in, so only the differences are authored.
    private String baseId;          // YAML base: the mundane weapon this one is (proficiency, tags)
    private String rarity;          // YAML rarity: common | uncommon | rare | very_rare | legendary | artifact
    private int attackBonus;        // magic.attack_bonus: added to attack rolls
    private int damageBonus;        // magic.damage_bonus: added to damage rolls
    private int critBonusDamage;    // magic.crit_bonus_damage: extra damage on a natural 20 (Vicious Weapon)

    public String getBaseId() { return baseId; }
    public void setBaseId(String baseId) { this.baseId = baseId; }
    private String baseName;        // the base weapon's display name, for proficiency matching
    public void setBaseName(String baseName) { this.baseName = baseName; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public int getAttackBonus() { return attackBonus; }
    public void setAttackBonus(int attackBonus) { this.attackBonus = attackBonus; }
    public int getDamageBonus() { return damageBonus; }
    public void setDamageBonus(int damageBonus) { this.damageBonus = damageBonus; }
    public int getCritBonusDamage() { return critBonusDamage; }
    public void setCritBonusDamage(int critBonusDamage) { this.critBonusDamage = critBonusDamage; }

    /** True for a magic weapon: one with a base weapon, a rarity, or any magic bonus. */
    public boolean isMagic() {
        return baseId != null || rarity != null || attackBonus != 0 || damageBonus != 0 || critBonusDamage != 0;
    }

    public DndWeapon() {}

    /**
     * The item id this weapon fires, from YAML {@code ammunition:} (#128) — e.g. {@code arrow} for
     * a shortbow. Null when the weapon needs none. Data-driven on purpose: a homebrew weapon can
     * fire a homebrew item without any Java change.
     */
    public String getAmmunition() { return ammunition; }
    public void setAmmunition(String ammunition) { this.ammunition = ammunition; }

    /** True if this weapon must consume ammunition to make a ranged attack. */
    public boolean usesAmmunition() { return hasProperty("ammunition"); }

    /**
     * How far this weapon can strike in melee, in feet. Defaults to 5 when the YAML says nothing,
     * so every weapon written before {@code reach:} existed keeps its normal reach.
     *
     * <p>Distinct from {@link #getNormalRange()}: reach is how far you can stab, range is how far a
     * ranged or thrown weapon travels. A thrown weapon has both.
     */
    public int getReachFeet() { return reach > 0 ? reach : 5; }
    public void setReach(int reach) { this.reach = reach; }

    /**
     * Percent chance this weapon survives being recovered after it was thrown (#191). Defaults to
     * 100: a thrown javelin is lying right there, not "half recovered" like a volley of arrows.
     * A fragile homebrew throwable can lower it.
     */
    public int getRecoveryChance() { return recoveryChance >= 0 ? recoveryChance : 100; }
    public void setRecoveryChance(int recoveryChance) { this.recoveryChance = recoveryChance; }

    /** True if this weapon strikes beyond a normal 5 ft melee (glaive, halberd, whip …). */
    public boolean hasReach() { return getReachFeet() > 5; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

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

        // Specific weapon proficiency — by this weapon's name or id, or its base's (a Longsword +2
        // is a longsword), in either the singular or plural spelling the YAML uses ("longsword",
        // "longswords").
        for (String key : new String[]{name, id, baseId, baseName}) {
            if (key == null || key.isBlank()) continue;
            String k = Util.normalize(key);
            if (weaponProficiencies.contains(k) || weaponProficiencies.contains(k + "s")
                    || (k.endsWith("s") && weaponProficiencies.contains(k.substring(0, k.length() - 1)))) {
                return true;
            }
        }

        // Check category proficiency (simple weapons, martial weapons)
        String categoryProf = Util.normalize(category + " weapons");
        if (weaponProficiencies.contains(categoryProf)) {
            return true;
        }

        return false;
    }

    private static String signed(int n) { return n >= 0 ? "+" + n : String.valueOf(n); }

    /** The DMG's rarity colours, as players know them from D&D Beyond / VTTs. */
    private static NamedTextColor rarityColor(String rarity) {
        return switch (Util.normalize(rarity)) {
            case "uncommon" -> NamedTextColor.GREEN;
            case "rare" -> NamedTextColor.BLUE;
            case "very_rare" -> NamedTextColor.DARK_PURPLE;
            case "legendary" -> NamedTextColor.GOLD;
            case "artifact" -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY; // common
        };
    }

    public ItemStack createItemStack() {
        List<Component> lore = new ArrayList<>();

        // Magic first (#188): rarity, and what it adds.
        if (rarity != null) {
            lore.add(Component.text(Util.prettify(rarity) + " magic weapon", rarityColor(rarity)));
        }
        if (attackBonus != 0 || damageBonus != 0) {
            String b = attackBonus == damageBonus
                    ? signed(attackBonus) + " to attack and damage rolls"
                    : signed(attackBonus) + " to attack, " + signed(damageBonus) + " to damage";
            lore.add(Component.text(b, NamedTextColor.LIGHT_PURPLE));
        }
        if (critBonusDamage != 0) {
            lore.add(Component.text("On a natural 20: " + signed(critBonusDamage) + " damage", NamedTextColor.LIGHT_PURPLE));
        }

        // Add damage info
        lore.add(Component.text("Damage: " + damage + " " + damageType, NamedTextColor.GRAY));

        // Which ability you add to the attack roll (#184). Deliberately static: the playtest
        // confusion was "what do I add?", not "what's my total?" — and a static line needs no
        // per-wielder rewriting, so it can't go stale, split stacks, or follow a dropped weapon
        // to the next player. `/combat attack … showModifiers` still gives the live number.
        Ability primary = getPrimaryAbility();
        String toHitAbility = primary != null ? primary.getAbbreviation() : "STR or DEX";
        lore.add(Component.text("To hit: " + toHitAbility + " + proficiency", NamedTextColor.GRAY));

        // Add properties
        if (properties != null && !properties.isEmpty()) {
            StringBuilder props = new StringBuilder("Properties: ");
            props.append(String.join(", ", properties.stream()
                    .map(Util::prettify).toArray(String[]::new)));
            lore.add(Component.text(props.toString(), NamedTextColor.GRAY));
        }

        // Reach, when it's beyond a normal melee swing — the whole point of a glaive.
        if (hasReach()) {
            lore.add(Component.text("Reach: " + getReachFeet() + " ft", NamedTextColor.GRAY));
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

        // Base: the vanilla Minecraft item, from YAML `material:`. Paper is the only
        // hardcoded fallback (used only if a weapon omits/misspells its material).
        Material base = Util.parseMaterial(material, Material.PAPER);
        ItemStack item = Util.createItem(
                Component.text(name, isMagic() && rarity != null ? rarityColor(rarity) : NamedTextColor.WHITE),
                lore,
                null,
                1,
                base
        );

        // Optional resource-pack overlay (YAML `custom_model:`); null/blank keeps the vanilla item.
        ItemUtil.applyModel(item, customModel);

        // A magic weapon shimmers like an enchanted one, so it reads as special in a chest or a hand.
        if (isMagic()) item.editMeta(m -> m.setEnchantmentGlintOverride(true));

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

    public String getCustomModel() { return customModel; }
    public void setCustomModel(String customModel) { this.customModel = customModel; }
}
