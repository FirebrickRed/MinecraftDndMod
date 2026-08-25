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
    private String description;
    private String material;     // YAML material: vanilla Minecraft item to render as
    private String customModel;  // YAML custom_model: optional resource-pack model (opt-in)
    private Cost cost;

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

    public boolean isSpellcastingFocus() {
        return "spellcasting_focus".equals(type);
    }

    public boolean canCastWith(String classSpellFocusType) {
        if (!isSpellcastingFocus()) return false;

        if ("component".equals(focusType)) return true;

        return focusType != null && focusType.equals(classSpellFocusType);
    }

    public ItemStack createItemStack() {
        List<Component> lore = new ArrayList<>();

        if (isSpellcastingFocus()) {
            lore.add(Component.text("Spellcasting Focus", NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.text("Right-click to cast spells", NamedTextColor.GRAY));
        }

        if (description != null) {
            lore.add(Component.text(""));
            lore.add(Component.text(description, NamedTextColor.YELLOW));
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
