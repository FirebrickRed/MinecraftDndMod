package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DndItem {
    private String id;
    private String name;
    private String type;
    private String focusType;
    // The ability a check with this tool normally uses (thieves' tools → DEX). Null = the DM names it.
    private io.papermc.jkvttplugin.data.model.enums.Ability checkAbility;
    public io.papermc.jkvttplugin.data.model.enums.Ability getCheckAbility() { return checkAbility; }
    public void setCheckAbility(io.papermc.jkvttplugin.data.model.enums.Ability a) { this.checkAbility = a; }
    private String description;
    private String material;     // YAML material: vanilla Minecraft item to render as
    private String customModel;  // YAML custom_model: optional resource-pack model (opt-in)
    private Cost cost;
    private List<String> tags = new ArrayList<>(); // YAML tags: item-groupings (e.g. gaming_set) (#54)
    private int recoveryChance = -1; // YAML recovery_chance: % odds of surviving being picked up (#191); -1 = unset
    private String healing;      // YAML healing: dice restored when drunk, e.g. "2d4+2" (potions)

    public String getId() {
        return this.id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return this.type;
    }
    public void setType(String type) {
        this.type = type;
    }

    /** Healing dice for a drinkable item (potion), e.g. "2d4+2"; null for everything else. */
    public String getHealing() {
        return healing;
    }
    public void setHealing(String healing) {
        this.healing = healing;
    }

    /** True if this item can be drunk to restore HP (`/character drink`). */
    public boolean isDrinkable() {
        return healing != null && !healing.isBlank();
    }

    public String getFocusType() {
        return this.focusType;
    }
    public void setFocusType(String focusType) {
        this.focusType = focusType;
    }

    public String getDescription() {
        return this.description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getMaterial() {
        return this.material;
    }
    public void setMaterial(String material) {
        this.material = material;
    }
    public String getCustomModel() {
        return this.customModel;
    }
    public void setCustomModel(String customModel) {
        this.customModel = customModel;
    }

    public Cost getCost() {
        return this.cost;
    }
    public void setCost(Cost cost) {
        this.cost = cost;
    }

    /**
     * Percent chance this item survives when recovered off the battlefield (#191).
     * Unset defaults to 50% for ammunition (RAW: you get about half your arrows back) and 100%
     * for anything else, since only ammunition is expected to break.
     */
    public int getRecoveryChance() {
        if (recoveryChance >= 0) return recoveryChance;
        return (tags != null && tags.contains("ammunition")) ? 50 : 100;
    }
    public void setRecoveryChance(int recoveryChance) { this.recoveryChance = recoveryChance; }

    public List<String> getTags() {
        return this.tags;
    }
    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public boolean isSpellcastingFocus() {
        return "spellcasting_focus".equals(type);
    }

    public boolean canCastWith(String classSpellFocusType) {
        if (!isSpellcastingFocus()) return false;

        if ("component".equals(focusType)) return true;

        return focusType != null && focusType.equals(classSpellFocusType);
    }

    /** Names of the classes whose spellcasting focus type this item is. Empty for a component pouch (anyone). */
    private List<String> focusClasses() {
        List<String> out = new ArrayList<>();
        if (focusType == null || "component".equals(focusType)) return out;
        for (DndClass c : io.papermc.jkvttplugin.data.loader.ClassLoader.getAllClasses()) {
            SpellcastingInfo info = c.getSpellcastingInfo();
            if (info != null && focusType.equalsIgnoreCase(info.getSpellcastingFocusType())) out.add(c.getName());
        }
        out.sort(String::compareTo);
        return out;
    }

    public ItemStack createItemStack() {
        List<Component> lore = new ArrayList<>();

        if (isSpellcastingFocus()) {
            // Name who can cast with it: thieves' tools are the artificer's focus, and a bare
            // "Spellcasting Focus" on them read as if any rogue could cast through their lockpicks.
            List<String> casters = focusClasses();
            lore.add(Component.text(casters.isEmpty() ? "Spellcasting Focus"
                    : "Spellcasting focus for: " + String.join(", ", casters), NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.text(casters.isEmpty() ? "Right-click to cast spells"
                    : "Right-click to cast spells (" + String.join(", ", casters) + " only)", NamedTextColor.GRAY));
        }

        if (description != null) {
            lore.add(Component.text(""));
            // Wrapped: one long line runs off the edge of the screen.
            for (String line : Util.wrapText(description, 50)) lore.add(Component.text(line, NamedTextColor.YELLOW));
        }

        // Base: the vanilla Minecraft item (YAML material:), defaulting to paper.
        Material base = Util.parseMaterial(this.material, Material.PAPER);

        ItemStack item = Util.createItem(
                Component.text(name, NamedTextColor.WHITE),
                lore,
                null,
                1,
                base
        );

        // Optional resource-pack overlay (YAML custom_model:); null/blank keeps the vanilla item.
        ItemUtil.applyModel(item, customModel);

        // Add NBT tags (item_id and optionally spell_focus) in one operation
        ItemMeta meta = item.getItemMeta();

        // Tag with standardized item_id for reliable identification (Issue #75)
        meta.getPersistentDataContainer().set(
                ItemUtil.getItemIdKey(),
                PersistentDataType.STRING,
                id
        );

        if (isSpellcastingFocus()) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("jkvtt", "spell_focus"),
                    PersistentDataType.STRING,
                    focusType
            );
        }

        item.setItemMeta(meta);

        return item;
    }
}
