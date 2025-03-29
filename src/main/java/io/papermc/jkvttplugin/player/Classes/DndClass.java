package io.papermc.jkvttplugin.player.Classes;

import io.papermc.jkvttplugin.util.DndSpell;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DndClass {
    public abstract String getClassName();
    public abstract ItemStack getClassIcon();
    protected int hitDie;

    public DndClass(int hitDie) {
        this.hitDie = hitDie;
    }

    public int getHitDie() {
        return hitDie;
    }

    public int rollHitPoints(int constitutionModifier) {
//        int roll = DiceRoller.rollDice(1, hitDie);
//        while (roll == 1) {
//            roll = DiceRoller.rollDice(1, hitDie);
//        }
        int roll = 0;
        return roll + constitutionModifier;
    }

    public abstract List<ItemStack> getBaseEquipment();

    public abstract Map<String, List<ItemStack>> getGearChoices();

    public String getGoldDiceRoll() {
        return "5d4 * 10";
    }

    public void giveStartingEquipment(Player player, Map<String, String> selectedChoices) {
        for (ItemStack item : getBaseEquipment()) {
            player.getInventory().addItem(item);
        }

        for (Map.Entry<String, String> choice : selectedChoices.entrySet()) {
            List<ItemStack> chosenItems = getGearChoices().get(choice.getValue());
            if (chosenItems != null) {
                for (ItemStack item : chosenItems) {
                    player.getInventory().addItem(item);
                }
            }
        }

        player.sendMessage("You received your starting equipment!");
    }

    public int[] getAvailableSpellSlots(int level) {
        return new int[10];
    }

    public List<DndSpell> getSpellList() {
        return DndSpell.getSpellsByClass(getClassName());
    }

    public enum DndClassType {
        ARTIFICER(new Artificer(8)),
        BARBARIAN(new Barbarian(12)),
        BARD(new Bard(8)),
        BLOODHUNTER(new BloodHunter(10)),
        CLERIC(new Cleric(8)),
        DRUID(new Druid(8)),
        FIGHTER(new Fighter(10)),
        MONK(new Monk(8)),
        PALADIN(new Paladin(10)),
        RANGER(new Ranger(10)),
        ROGUE(new Rogue(8)),
        SORCERER(new Sorcerer(6)),
        WARLOCK(new Warlock(8)),
        WIZARD(new Wizard(6));

        private final String displayName;
        private final DndClass dndClass;

        DndClassType(DndClass dndClass) {
            this.dndClass = dndClass;
            this.displayName = dndClass.getClassName();
        }

        public String getDisplayName() {
            return displayName;
        }

        public DndClass getDndClass() {
            return dndClass;
        }

        public static DndClassType fromString(String name) {
            for (DndClassType type : DndClassType.values()) {
                if (type.displayName.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null; // Return null if no match is found
        }
    }
}

/*
 * Multiclassing Proficiencies:
 * Artificer: Light Armor, Medium Armor, shields, thieves' tools, tinker's tools
 * Barbarian: Shields, Simple weapons, martial weapons
 * Bard: Light armor, one skill of your choice, one musical instrument of your choice
 * Cleric: Light armor, medium armor, shields
 * Druid: Light armor, medium armor, shields (druids will not wear armor or use shields made of metal)
 * Fighter: Light armor, medium armor, shields, simple weapons, martial weapons
 * Monk: Simple weapons, shortswords
 * Paladin: Light armor, medium armor, shields, simple weapons, martial weapons
 * Ranger: Light armor, medium armor, shields, simple weapons, martial weapons, one skill from the class's skill list
 * Rogue: Light armor, one skill from the class's skill list, thieves' tools
 * Sorcerer: -
 * Warlock: Light armor, simple weapons
 * Wizard: -
 */

