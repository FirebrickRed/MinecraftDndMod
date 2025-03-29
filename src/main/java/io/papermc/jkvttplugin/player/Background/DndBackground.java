package io.papermc.jkvttplugin.player.Background;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DndBackground {
    public final String backgroundName;
    public abstract ItemStack getBackgroundIcon();

    public DndBackground(String backgroundName) {
        this.backgroundName = backgroundName;
    }

    public String getBackgroundName() {
        return backgroundName;
    }

    // equipment
    public abstract List<ItemStack> getStartingEquipment();
    public Map<String, List<ItemStack>> getGearChoices() {
        return new HashMap<>();
    }
    // tool proficiencies
    // skill proficiencies
    // languages

    public enum DndBackgroundType {
        NOBLE(new Noble()),
        SAILOR(new Sailor()),
        WILDSPACER(new Wildspacer());

        private final DndBackground dndBackground;

        DndBackgroundType(DndBackground dndBackground) {
            this.dndBackground = dndBackground;
        }

        public DndBackground getDndBackground() {
            return dndBackground;
        }

        public static DndBackgroundType fromString(String name) {
            for (DndBackgroundType backgroundType : values()) {
                if (backgroundType.dndBackground.getBackgroundName().equalsIgnoreCase(name)) {
                    return backgroundType;
                }
            }

            return null;
        }
    }
}
