package io.papermc.jkvttplugin.CustomNPCs;

import java.util.List;
import java.util.Map;

public class NpcData {
    private final String name;
    private final String type;
    private final int hitPoints;
    private final int armorClass;
    private final int speed;
    private final Map<String, Integer> abilities;
    private final List<NpcAttack> attacks;
    private final List<String> inventory;

    public NpcData(String name, String type, int hitPoints, int armorClass, int speed, Map<String, Integer> abilities, List<NpcAttack> attacks, List<String> inventory) {
        this.name = name;
        this.type = type;
        this.hitPoints = hitPoints;
        this.armorClass = armorClass;
        this.speed = speed;
        this.abilities = abilities;
        this.attacks = attacks;
        this.inventory = inventory;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public int getHitPoints() { return hitPoints; }
    public int getArmorClass() { return armorClass; }
    public int getSpeed() { return speed; }
    public Map<String, Integer> getAbilities() { return abilities; }
    public List<NpcAttack> getAttacks() { return attacks; }
    public List<String> getInventory() { return inventory; }

    @Override
    public String toString() {
        return name + " (HP: " + getHitPoints() + ", AC: " + getArmorClass() + ")";
    }

    public static class NpcAttack {
        private final String name;
        private final int toHit;
        private final String damage;

        public NpcAttack(String name, int toHit, String damage) {
            this.name = name;
            this.toHit = toHit;
            this.damage = damage;
        }

        public String getName() { return name; }
        public int getToHit() { return toHit; }
        public String getDamage() { return damage; }

        @Override
        public String toString() {
            return name + " (To Hit: " + getToHit() + ", Damage: " + getDamage() + ")";
        }
    }
}
