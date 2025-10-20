package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.DndRules;
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
    private int[] spellSlots = new int[9];
    private int[] maxSpellSlots = new int[9];
    private DndSpell concentratingOn = null;

    private int totalHealth;
    private int currentHealth;
    private int tempHealth;
    private int armorClass;

    private List<ItemStack> equipment = new ArrayList<>();
    private DndArmor equippedArmor;
    private DndArmor equippedShield;

    private List<ClassResource> classResources = new ArrayList<>();

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
            sheet.subrace = sheet.race.getSubraces().get(session.getSelectedSubRace());
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

        // Apply racial ability score bonuses (both fixed and player-chosen)
        sheet.applyRacialBonuses(session);

        sheet.loadSpells(session.getSelectedSpells(), session.getSelectedCantrips());
        sheet.calculateHealth();

        sheet.grantStartingEquipment(session);

        sheet.calculateArmorClass();
        sheet.initializeSpellSlots();
        sheet.initializeClassResources();

        return sheet;
    }

    public static CharacterSheet loadFromData(UUID characterId, UUID playerId, String characterName, String raceName, String subraceName, String className, String backgroundName, EnumMap<Ability, Integer> abilityScores, Set<String> spellNames, Set<String> cantripNames, int currentHealth, int maxHealth, int armorClass) {
        CharacterSheet sheet = new CharacterSheet(characterId, playerId, characterName);

        sheet.race = RaceLoader.getRace(raceName);
        if (subraceName != null && sheet.race != null && sheet.race.hasSubraces()) {
            sheet.subrace = sheet.race.getSubraces().get(subraceName);
        }
        sheet.dndClass = ClassLoader.getClass(className);
        sheet.background = BackgroundLoader.getBackground(backgroundName);

        sheet.abilityScores = new EnumMap<>(abilityScores);

        sheet.loadSpells(spellNames, cantripNames);

        sheet.currentHealth = currentHealth;
        sheet.totalHealth = maxHealth;

        // ToDo: Load spell slots from persistence instead of resetting to max (Issue #31)
        sheet.initializeSpellSlots();

        // ToDo: Load equipped armor from persistence (Issue #31)
        // Currently equippedArmor is null on load, so AC will be wrong if player was wearing armor
        // For now, recalculate AC from base stats (will be correct once armor is re-equipped)
        // When implementing Issue #31:
        // 1. Save equippedArmor.getId() and equippedShield.getId() to YAML
        // 2. Load armor/shield references from ArmorLoader in loadFromData()
        // 3. Remove armorClass parameter entirely and always calculate from equipped gear
        sheet.calculateArmorClass();

        return sheet;
    }

    /**
     * Apply racial ability score bonuses to the character's base ability scores.
     * This includes:
     * 1. Fixed bonuses from race (e.g., Elf +2 DEX)
     * 2. Fixed bonuses from subrace (e.g., High Elf +1 INT)
     * 3. Player-chosen bonuses from racial distributions (e.g., Giff choose +2/+1)
     */
    private void applyRacialBonuses(CharacterCreationSession session) {
        // Apply fixed race bonuses
        if (race != null && race.getFixedAbilityScores() != null) {
            for (Map.Entry<Ability, Integer> entry : race.getFixedAbilityScores().entrySet()) {
                Ability ability = entry.getKey();
                int bonus = entry.getValue();
                int currentScore = abilityScores.getOrDefault(ability, 10);
                abilityScores.put(ability, currentScore + bonus);
            }
        }

        // Apply fixed subrace bonuses
        if (subrace != null && subrace.getFixedAbilityScores() != null) {
            for (Map.Entry<Ability, Integer> entry : subrace.getFixedAbilityScores().entrySet()) {
                Ability ability = entry.getKey();
                int bonus = entry.getValue();
                int currentScore = abilityScores.getOrDefault(ability, 10);
                abilityScores.put(ability, currentScore + bonus);
            }
        }

        // Apply player-chosen racial bonuses from session
        if (session != null && session.getRacialBonusAllocations() != null) {
            EnumMap<Ability, Integer> chosenBonuses = session.getRacialBonusAllocations();
            for (Map.Entry<Ability, Integer> entry : chosenBonuses.entrySet()) {
                Ability ability = entry.getKey();
                int bonus = entry.getValue();
                int currentScore = abilityScores.getOrDefault(ability, 10);
                abilityScores.put(ability, currentScore + bonus);
            }
        }
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

    private void initializeSpellSlots() {
        if (dndClass == null || dndClass.getSpellcastingInfo() == null) return;

        SpellcastingInfo spellcasting = dndClass.getSpellcastingInfo();
        int characterLevel = getTotalLevel();

        Map<Integer, List<Integer>> slotsByLevel = spellcasting.getSpellSlotsByLevel();
        if (slotsByLevel == null) return;

        // Iterate through spell levels 1-9
        for (int spellLevel = 1; spellLevel <= 9; spellLevel++) {
            List<Integer> slotsProgression = slotsByLevel.get(spellLevel);
            if (slotsProgression == null || slotsProgression.isEmpty()) continue;

            // Get slots for this character level (YAML arrays are 0-indexed, so characterLevel-1)
            int slotIndex = characterLevel - 1;
            if (slotIndex >= 0 && slotIndex < slotsProgression.size()) {
                int slots = slotsProgression.get(slotIndex);
                maxSpellSlots[spellLevel - 1] = slots;
                spellSlots[spellLevel - 1] = slots;
            }
        }
    }

    private void initializeClassResources() {
        if (dndClass == null) {
            return;
        }

        // Create a function to get ability modifiers for resource calculations
        java.util.function.Function<String, Integer> abilityModifier = (abilityName) -> {
            try {
                Ability ability = Ability.valueOf(abilityName.toUpperCase());
                return getModifier(ability);
            } catch (IllegalArgumentException e) {
                return 0; // If ability name is invalid, return 0
            }
        };

        classResources = dndClass.createResourcesForCharacter(getTotalLevel(), abilityModifier);
    }

    private void grantStartingEquipment(CharacterCreationSession session) {
        List<ItemStack> startingItems = new ArrayList<>();

        if (dndClass != null && dndClass.getStartingEquipment() != null) {
            for (String itemId : dndClass.getStartingEquipment()) {
                ItemStack item = createItemFromId(itemId);
                if (item != null) {
                    startingItems.add(item);
                }
            }
        }

        if (dndClass != null && session != null) {
            List<ItemStack> choiceItems = resolveEquipmentFromChoices(session.getPendingChoices(), "class");
            startingItems.addAll(choiceItems);
        }

        if (background != null) {
            List<String> bgEquipment = background.getStartingEquipment();
            if (bgEquipment != null) {
                for (String itemId : bgEquipment) {
                    ItemStack item = createItemFromId(itemId);
                    if (item != null) {
                        startingItems.add(item);
                    }
                }
            }
        }

        if (background != null && session != null) {
            List<ItemStack> choiceItems = resolveEquipmentFromChoices(session.getPendingChoices(), "background");
            startingItems.addAll(choiceItems);
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
    public void equipArmor(DndArmor armor) {
        this.equippedArmor = armor;
        calculateArmorClass();
    }
    public void unequipArmor() {
        this.equippedArmor = null;
        calculateArmorClass();
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

    public DndRace getRace() {
        return race;
    }

    public boolean hasSubrace() {
        return race.hasSubraces();
    }

    public DndSubRace getSubrace() {
        return subrace;
    }

    public DndClass getMainClass() { return dndClass; }

    // ToDo: update this logic to not be hardcoded when level up gets implemented
    public int getTotalLevel() {
        return 1;
    }

    public DndBackground getBackground() {
        return background;
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

    public int getProficiencyBonus() {
        return DndRules.getProficiencyBonus(getTotalLevel());
    }

    public int getSpeed() {
        if (race != null) {
            return race.getSpeed();
        }
        return 30; // Default if race is null
    }

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
        return Ability.getModifier(abilityScores.get(ability));
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

    public Set<DndSpell> getKnownCantrips() {
        return knownCantrips;
    }

    public int getSpellSlotsRemaining(int level) {
        if (level < 1 || level > 9) return 0;
        return spellSlots[level - 1];
    }

    public int getMaxSpellSlots(int level) {
        if (level < 1 || level > 9) return 0;
        return maxSpellSlots[level - 1];
    }

    public DndSpell getConcentratingOn() {
        return concentratingOn;
    }

    public void setConcentratingOn(DndSpell spell) {
        this.concentratingOn = spell;
    }

    public boolean isConcentrating() {
        return concentratingOn != null;
    }

    public void breakConcentration() {
        concentratingOn = null;
    }

    public boolean hasSpellSlot(int level) {
        if (level < 1 || level > 9) return false;
        return spellSlots[level - 1] > 0;
    }

    public void consumeSpellSlot(int level) {
        if (level < 1 || level > 9) return;
        if (spellSlots[level - 1] > 0) {
            spellSlots[level - 1] -= 1;
        }
    }

    public void addSpell(DndSpell spell) {
        if (!knownSpells.contains(spell)) {
            knownSpells.add(spell);
        }
    }

    public Set<DndSpell> getKnownSpells() {
        return knownSpells;
    }

    public List<ClassResource> getClassResources() {
        return new ArrayList<>(classResources);
    }

    public ClassResource getResource(String resourceName) {
        for (ClassResource resource : classResources) {
            if (resource.getName().equalsIgnoreCase(resourceName)) {
                return resource;
            }
        }
        return null;
    }

    public boolean hasResource(String resourceName) {
        return getResource(resourceName) != null;
    }

    public void longRest() {
        // Restore spell slots
        for (int i = 0; i < 9; i++) {
            spellSlots[i] = maxSpellSlots[i];
        }

        // Restore class resources that recover on long rest
        for (ClassResource resource : classResources) {
            if (resource.getRecovery() == ClassResource.RecoveryType.LONG_REST) {
                resource.restore();
            }
        }

        breakConcentration();

        currentHealth = totalHealth;
        tempHealth = 0;
    }

    public void shortRest() {
        // Restore class resources that recover on short rest
        for (ClassResource resource : classResources) {
            if (resource.getRecovery() == ClassResource.RecoveryType.SHORT_REST) {
                resource.restore();
            }
        }

        // Warlocks recover Pact Magic slots on short rest
        // ToDo: Implement when Warlock-specific slot recovery is added
    }
}
