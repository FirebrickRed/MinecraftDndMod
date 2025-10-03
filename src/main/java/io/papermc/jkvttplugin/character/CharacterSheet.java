package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CharacterSheet {
    private final UUID characterId;
    private final UUID playerId;
    private String characterName;

    private DndRace race;
    private DndSubRace subrace;
    private DndClass dndClass;
    private DndBackground background;

    private EnumMap<Ability, Integer> abilityScores;

    private Set<DndSpell> knownSpells = new HashSet<>();
    private Set<DndSpell> knownCantrips = new HashSet<>();

    private int totalHealth;
    private int currentHealth;
    private int tempHealth;
    private int armorClass;

    private List<ItemStack> equipment = new ArrayList<>();
    private DndArmor equippedArmor;
    private DndArmor equippedShield;

    private CharacterSheet(UUID characterId, UUID playerId, String characterName) {
        this.characterId = characterId;
        this.playerId = playerId;
        this.characterName = characterName;
    }

    public static CharacterSheet createFromSession(UUID characterId, UUID playerId, CharacterCreationSession session) {
        CharacterSheet sheet = new CharacterSheet(characterId, playerId, session.getCharacterName());

        sheet.race = RaceLoader.getRace(session.getSelectedRace());
        if (sheet.race == null) {
            throw new IllegalArgumentException("Race not found: " + session.getSelectedRace());
        }

        if (session.getSelectedSubRace() != null && sheet.race.hasSubraces()) {
            sheet.subrace = sheet.race.getSubRaceByName(session.getSelectedSubRace());
        }

        sheet.dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (sheet.dndClass == null) {
            throw new IllegalArgumentException("Class not found: " + session.getSelectedClass());
        }

        sheet.background = BackgroundLoader.getBackground(session.getSelectedBackground());
        if (sheet.background == null) {
            throw new IllegalArgumentException("Background not found: " + session.getSelectedBackground());
        }

        sheet.abilityScores = new EnumMap<>(session.getAbilityScores());
        // Claude TODO: MISSING FEATURE - Apply racial ability score bonuses
        // DndRace and DndSubRace have abilityScoreIncreases maps that should be applied here
        // Need to handle:
        // 1. Fixed bonuses (e.g., Dwarf +2 CON)
        // 2. Choice bonuses (e.g., Half-Elf +1 to two abilities of choice)
        // 3. Subrace bonuses (e.g., Mountain Dwarf +2 STR)
        // See: DndRace.getAbilityScoreIncreases() and DndSubRace.getAbilityScoreIncreases()
        // ToDo: figure out racial bonuses

        sheet.loadSpells(session.getSelectedSpells(), session.getSelectedCantrips());
        sheet.calculateHealth();

        sheet.grantStartingEquipment(session);

        sheet.calculateArmorClass();
        return sheet;
    }

    public static CharacterSheet loadFromData(UUID characterId, UUID playerId, String characterName, String raceName, String subraceName, String className, String backgroundName, EnumMap<Ability, Integer> abilityScores, Set<String> spellNames, Set<String> cantripNames, int currentHealth, int maxHealth, int armorClass) {
        CharacterSheet sheet = new CharacterSheet(characterId, playerId, characterName);

        sheet.race = RaceLoader.getRace(raceName);
        if (subraceName != null && sheet.race != null && sheet.race.hasSubraces()) {
            sheet.subrace = sheet.race.getSubRaceByName(subraceName);
        }
        sheet.dndClass = ClassLoader.getClass(className);
        sheet.background = BackgroundLoader.getBackground(backgroundName);

        sheet.abilityScores = new EnumMap<>(abilityScores);

        sheet.loadSpells(spellNames, cantripNames);

        sheet.currentHealth = currentHealth;
        sheet.totalHealth = maxHealth;
        sheet.armorClass = armorClass;

        return sheet;
    }

    private void loadSpells(Set<String> spellNames, Set<String> cantripNames) {
        if (spellNames != null) {
            for (String spellName : spellNames) {
                DndSpell spell = SpellLoader.getSpell(spellName);
                if (spell != null) {
                    knownSpells.add(spell);
                }
            }
        }

        if (cantripNames != null) {
            for (String cantripName : cantripNames) {
                DndSpell cantrip = SpellLoader.getSpell(cantripName);
                if (cantrip != null) {
                    knownCantrips.add(cantrip);
                }
            }
        }
    }

    private void calculateHealth() {
        if (dndClass != null) {
            int conModifier = getModifier(Ability.CONSTITUTION);
            totalHealth = dndClass.getHitDie() + conModifier;
            currentHealth = totalHealth;
        }
    }

    private void calculateArmorClass() {
        int baseAC = 10 + getModifier(Ability.DEXTERITY);

        if (equippedArmor != null) {
            int armorAC = equippedArmor.calculateAC(getModifier(Ability.DEXTERITY), getAbility(Ability.STRENGTH));

            if (armorAC > 0) {
                baseAC = armorAC;
            }
        }

        if (equippedShield != null && equippedShield.isShield()) {
            baseAC += equippedShield.getBaseAC();
        }

        armorClass = baseAC;
    }

    private void grantStartingEquipment(CharacterCreationSession session) {
        List<ItemStack> startingItems = new ArrayList<>();

        if (dndClass != null && dndClass.getStartingEquipment() != null) {
            for (String itemId : dndClass.getStartingEquipment()) {
                ItemStack item = createItemFromId(itemId);
                if (item != null) {
                    startingItems.add(item);
                    tryAutoEquip(item, itemId);
                }
            }
        }

        if (dndClass != null && session != null) {
            List<ItemStack> choiceItems = resolveEquipmentFromChoices(session.getPendingChoices(), "class");
            startingItems.addAll(choiceItems);
            for (ItemStack item : choiceItems) {
                tryAutoEquipFromItem(item);
            }
        }

        if (background != null) {
            List<String> bgEquipment = background.getStartingEquipment();
            if (bgEquipment != null) {
                for (String itemId : bgEquipment) {
                    ItemStack item = createItemFromId(itemId);
                    if (item != null) {
                        startingItems.add(item);
                        tryAutoEquip(item, itemId);
                    }
                }
            }
        }

        if (background != null && session != null) {
            List<ItemStack> choiceItems = resolveEquipmentFromChoices(session.getPendingChoices(), "background");
            startingItems.addAll(choiceItems);
            for (ItemStack item : choiceItems) {
                tryAutoEquipFromItem(item);
            }
        }

//        if (dndClass != null && dndClass.getSpellcastingAbility() != null) {
//            String focusType = dndClass.getSpellFocus();
//        }

        equipment.addAll(startingItems);
    }

    private List<ItemStack> resolveEquipmentFromChoices(List<PendingChoice<?>> pendingChoices, String source) {
        List<ItemStack> items = new ArrayList<>();

        if (pendingChoices == null) return items;

        for (PendingChoice<?> pc : pendingChoices) {
            if (!source.equals(pc.getSource())) continue;

            if (pc.getPlayersChoice().getType() != PlayersChoice.ChoiceType.EQUIPMENT) continue;

            Set<?> chosen = pc.getChosen();

            for (Object obj : chosen) {
                if (obj instanceof EquipmentOption equipmentOption) {
                    List<ItemStack> optionItems = createItemsFromEquipmentOption(equipmentOption);
                    items.addAll(optionItems);
                }
            }
        }

        return items;
    }

    private List<ItemStack> createItemsFromEquipmentOption(EquipmentOption option) {
        List<ItemStack> items = new ArrayList<>();

        switch (option.getKind()) {
            case ITEM -> {
                String itemId = option.getIdOrTag();
                int quantity = option.getQuantity();

                ItemStack item = createItemFromId(itemId);
                if (item != null) {
                    item.setAmount(quantity);
                    items.add(item);
                }
            }
            case TAG -> {
                String itemId = option.getIdOrTag();
                ItemStack item = createItemFromId(itemId);
                if (item != null) {
                    items.add(item);
                }
            }
            case BUNDLE -> {
                for (EquipmentOption part : option.getParts()) {
                    items.addAll(createItemsFromEquipmentOption(part));
                }
            }
        }

        return items;
    }

    private ItemStack createItemFromId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;

        DndWeapon weapon = WeaponLoader.getWeapon(itemId);
        if (weapon != null) {
            return weapon.createItemStack();
        }

        DndArmor armor = ArmorLoader.getArmor(itemId);
        if (armor != null) {
            return armor.createItemStack();
        }

        DndItem item = ItemLoader.getItem(itemId);
        if (item != null) {
            return item.createItemStack();
        }

        return Util.createItem(
                Component.text(Util.prettify(itemId), NamedTextColor.WHITE),
                List.of(Component.text("Unknown item", NamedTextColor.GRAY)),
                "unknown_item",
                1
        );
    }

    private void tryAutoEquip(ItemStack item, String itemId) {
        DndArmor armor = ArmorLoader.getArmor(itemId);
        if (armor != null) {
            Set<String> armorProfs = getArmorProficiencies();
            if (armor.canWear(getAbility(Ability.STRENGTH), armorProfs)) {
                if (armor.isShield() && equippedShield == null) {
                    equippedShield = armor;
                } else if (!armor.isShield() && equippedArmor == null) {
                    equippedArmor = armor;
                }
            }
        }
    }

    private void tryAutoEquipFromItem(ItemStack item) {
        String itemName = getItemName(item);
        if (itemName == null) return;

        DndArmor armor = findArmorByName(itemName);
        if (armor != null) {
            Set<String> armorProfs = getArmorProficiencies();
            if (armor.canWear(getAbility(Ability.STRENGTH), armorProfs)) {
                if (armor.isShield() && equippedShield == null) {
                    equippedShield = armor;
                } else if (!armor.isShield() && equippedArmor == null) {
                    equippedArmor = armor;
                }
            }
        }
    }

    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return null;
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private DndArmor findArmorByName(String displayName) {
        for (DndArmor armor : ArmorLoader.getAllArmors()) {
            if (armor.getName().equals(displayName)) {
                return armor;
            }
        }
        return null;
    }

    private Set<String> getArmorProficiencies() {
        Set<String> proficiencies = new HashSet<>();

        if (dndClass != null && dndClass.getArmorProficiencies() != null) {
            proficiencies.addAll(dndClass.getArmorProficiencies());
        }

        return proficiencies;
    }

    public List<ItemStack> getEquipment() {
        return new ArrayList<>(equipment);
    }

    public DndArmor getEquippedArmor() {
        return equippedArmor;
    }

    public DndArmor getEquippedShield() {
        return equippedShield;
    }


    // ========== GETTERS ==========

    public UUID getPlayerId() {
        return playerId;
    }
    public UUID getCharacterId() {
        return characterId;
    }
    public String getCharacterName() {
        return characterName;
    }

    public String getRaceName() {
        return race != null ? race.getName() : "Unknown";
    }

    public boolean hasSubrace() {
        return race.hasSubraces();
    }

    public String getSubraceName() {
        return subrace != null ? subrace.getName() : null;
    }

    public String getMainClassName() {
        return dndClass != null ? dndClass.getName() : "Unknown";
    }

    // ToDo: update this logic to not be hardcoded when level up gets implemented
    public int getTotalLevel() {
        return 1;
    }

    public String getBackgroundName() {
        return background != null ? background.getName() : "Unknown";
    }

    public boolean hasSpells() {
        return knownCantrips != null && knownSpells != null;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return totalHealth;
    }

//    public int getProficiencyBonus() {
//        return Math.floorDiv(getTotalLevel() - 1, 4) + 2;
//    }

    public int getAbility(Ability ability) {
        return abilityScores.getOrDefault(ability, 10);
    }

    public void setAbility(Ability ability, int value) {
        abilityScores.put(ability, value);
    }

    public int getArmorClass() {
        return armorClass;
    }

    public int getInitiative() {
        // ToDo: update to account for other potential areas of initiative increases
        return getModifier(Ability.DEXTERITY);
    }

    public int getModifier(Ability ability) {
        int value = abilityScores.get(ability);
        return (value - 10) / 2;
    }

    public void gainTempHealth(int tempHP) {
        tempHealth = Math.max(tempHealth, tempHP);
    }

//    public int getTotalLevel() {
//        return classLevels.values().stream().mapToInt(Integer::intValue).sum();
//    }
//
//    public DndClass getMainDndClass() {
//        // gets the highest level class
//        return classLevels.entrySet().stream()
//                .max(Map.Entry.comparingByValue()) // Get class with the highest level
//                .map(Map.Entry::getKey)
//                .orElse(null);
//    }

    public void addSpell(DndSpell spell) {
        if (!knownSpells.contains(spell)) {
            knownSpells.add(spell);
        }
    }

    public Set<DndSpell> getKnownSpells() {
        return knownSpells;
    }

    public Map<String, List<ItemStack>> getEquipmentChoicesList() {
        Map<String, List<ItemStack>> choices = new HashMap<>();
//        DndClass mainClass = getMainDndClass();

//        if (mainClass != null) {
//            choices.putAll(mainClass.getGearChoices());
//        }

//        if (background != null) {
////            choices.putAll(background.getGearChoices());
//        }

        return choices;
    }
}
