package io.papermc.jkvttplugin.player.Races;

import java.awt.*;
import java.util.List;

import org.bukkit.inventory.ItemStack;

public abstract class DndRace {
    public final String raceName;
    public abstract ItemStack getRaceIcon();

    public DndRace(String raceName) {
        this.raceName = raceName;
    }

    public String getRaceName() {
        return raceName;
    }

    // Method to return movement speed (default to 30, override for specific races)
    public int getMovementSpeed() {
        return 30;
    }

    public String getCreatureType() {
        return "Huminoid";
    }

    public int getBreathDuration() {
        return 20 * 20;
    }

    // Method to return any bonus attributes (e.g., +2 Dexterity for Elves)
//    public abstract List<Component> getAttributeBonuses();

    // Method to return any sub-race (optional)
    public List<DndRace> getSubRaces() {
        return List.of(); // Override for sub-races
    }

    public enum DndRaceType {
        ELF(new Elf(List.of(new AstralElf()))),
        GIFF(new Giff()),
        PLASMOID(new Plasmoid());

        private final DndRace dndRace;

        DndRaceType(DndRace dndRace) {
            this.dndRace = dndRace;
        }

        public DndRace getDndRace() {
            return dndRace;
        }

        public static DndRaceType fromString(String name) {
            for (DndRaceType raceType : values()) {
                if (raceType.dndRace.getRaceName().equalsIgnoreCase(name)) {
                    return raceType;
                }
            }

            return null;
        }
    }
}
