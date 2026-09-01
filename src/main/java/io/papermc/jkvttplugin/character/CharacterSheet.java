package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.util.DndRules;
import io.papermc.jkvttplugin.util.ItemUtil;
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
    private DndSubClass subclass;
    private DndBackground background;

    private EnumMap<Ability, Integer> abilityScores;
    private Set<Skill> skillProficiencies = new HashSet<>();

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
    // Live buffs/debuffs on this character (the Effect Engine, #70). Read by combat when resolving.
    private final List<io.papermc.jkvttplugin.effect.ActiveEffect> activeEffects = new ArrayList<>();

    // Racial traits (Issue #51)
    private Set<String> weaponProficiencies = new HashSet<>();
    private Set<String> armorProficiencies = new HashSet<>();
    private Set<String> toolProficiencies = new HashSet<>();
    private Set<String> languages = new HashSet<>();
    private Set<String> damageResistances = new HashSet<>();
    // CUSTOM player-choice selections kept by choice id (e.g. draconic_ancestry -> "Red (Fire, …)").
    // Feature actions read these to resolve their per-choice variant (breath weapon, #70).
    private final Map<String, String> customChoices = new HashMap<>();
    private List<InnateSpell> innateSpells = new ArrayList<>();
    private Integer darkvision;  // Vision range in feet (60, 120, etc.), null = no darkvision
    private int longRestHours = 8;      // Hours needed for a long rest (#160); elves trance in 4.
    private boolean sleepRequired = true; // Warforged don't sleep. Not yet enforced — see #45.

    // Movement speeds
    private int speed = 30;  // Walking speed (default 30)
    private int swimmingSpeed = 0;  // 0 = use default (half walking speed)
    private int flyingSpeed = 0;    // 0 = can't fly
    private int climbingSpeed = 0;  // 0 = use default (half walking speed)
    private int burrowingSpeed = 0; // 0 = can't burrow

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

        // Load subclass if selected (only for classes with subclass_level == 1)
        if (session.getSelectedSubclass() != null && sheet.dndClass.getSubclassLevel() == 1) {
            sheet.subclass = sheet.dndClass.getSubclasses().get(session.getSelectedSubclass());
            if (sheet.subclass == null) {
                throw new IllegalArgumentException("Subclass not found: " + session.getSelectedSubclass() + " for class " + sheet.dndClass.getName());
            }
        }

        sheet.background = BackgroundLoader.getBackground(session.getSelectedBackground());
        if (sheet.background == null) {
            throw new IllegalArgumentException("Background not found: " + session.getSelectedBackground());
        }

        sheet.abilityScores = new EnumMap<>(session.getAbilityScores());

        // Apply racial ability score bonuses (both fixed and player-chosen)
        sheet.applyRacialBonuses(session);

        // Apply racial traits (proficiencies, resistances, innate spells, movement speeds, darkvision)
        sheet.applyRacialTraits();

        // Apply class tool proficiencies
        sheet.applyClassTraits();

        // Apply background traits (tool proficiencies, languages, skill proficiencies)
        sheet.applyBackgroundTraits();

        // Apply subclass traits (bonus spells, proficiencies, languages, darkvision, swimming speed)
        sheet.applySubclassTraits();

        sheet.loadSpells(session.getSelectedSpells(), session.getSelectedCantrips());
        sheet.loadSkillProficiencies(session);
        sheet.loadToolAndLanguageProficiencies(session);
        sheet.applyLinkedResistances(); // now that CUSTOM choices (e.g. draconic ancestry) are known
        sheet.calculateHealth();

        sheet.grantStartingEquipment(session);

        sheet.calculateArmorClass();
        sheet.initializeSpellSlots();
        sheet.initializeClassResources();

        return sheet;
    }

    public static CharacterSheet loadFromData(UUID characterId, UUID playerId, String characterName, String raceName, String subraceName, String className, String subclassName, String backgroundName, EnumMap<Ability, Integer> abilityScores, Set<Skill> skillProficiencies, Set<String> spellNames, Set<String> cantripNames, int currentHealth, int maxHealth, int armorClass) {
        CharacterSheet sheet = new CharacterSheet(characterId, playerId, characterName);

        sheet.race = RaceLoader.getRace(raceName);
        if (subraceName != null && sheet.race != null && sheet.race.hasSubraces()) {
            sheet.subrace = sheet.race.getSubraces().get(subraceName);
        }
        sheet.dndClass = ClassLoader.getClass(className);

        // Load subclass if present
        if (subclassName != null && sheet.dndClass != null && sheet.dndClass.hasSubclasses()) {
            sheet.subclass = sheet.dndClass.getSubclasses().get(subclassName);
        }

        sheet.background = BackgroundLoader.getBackground(backgroundName);

        sheet.abilityScores = new EnumMap<>(abilityScores);

        // Load skill proficiencies from saved data
        if (skillProficiencies != null) {
            sheet.skillProficiencies = new HashSet<>(skillProficiencies);
        }

        // Apply racial traits (proficiencies, resistances, innate spells, movement speeds, darkvision)
        sheet.applyRacialTraits();

        // Apply class, background, and subclass traits to restore tool proficiencies and languages
        sheet.applyClassTraits();
        sheet.applyBackgroundTraits();
        if (sheet.subclass != null) {
            sheet.applySubclassTraits();
        }

        sheet.loadSpells(spellNames, cantripNames);

        sheet.currentHealth = currentHealth;
        sheet.totalHealth = maxHealth;

        // Spell slots and class resources initialize to full here; the persistence loader reapplies
        // the saved current/spent values afterward (Issue #31).
        sheet.initializeSpellSlots();
        sheet.initializeClassResources();

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
            Map<Ability, Integer> chosenBonuses = session.getRacialBonusAllocations();
            for (Map.Entry<Ability, Integer> entry : chosenBonuses.entrySet()) {
                Ability ability = entry.getKey();
                int bonus = entry.getValue();
                int currentScore = abilityScores.getOrDefault(ability, 10);
                abilityScores.put(ability, currentScore + bonus);
            }
        }

        // RAW cap: clamp every score to 20 if enabled (#112). Default off = house rule allows >20.
        if (io.papermc.jkvttplugin.config.PluginConfig.isAbilityScoreCap20()) {
            abilityScores.replaceAll((a, v) -> Math.min(20, v));
        }
    }

    /**
     * Apply racial traits to the character (Issue #51)
     * This includes proficiencies, damage resistances, innate spells, darkvision, and movement speeds.
     * Traits are applied from both race and subrace (subrace overrides race where applicable).
     */
    private void applyRacialTraits() {
        // Apply movement speeds (race base, subrace can override)
        if (race != null) {
            this.speed = race.getSpeed();
            this.swimmingSpeed = race.getSwimmingSpeed();
            this.flyingSpeed = race.getFlyingSpeed();
            this.climbingSpeed = race.getClimbingSpeed();
            this.burrowingSpeed = race.getBurrowingSpeed();
            this.darkvision = race.getDarkvision();
            this.longRestHours = race.getLongRestHours();   // #160 (not enforced yet — #45)
            this.sleepRequired = race.isSleepRequired();
        }

        // Subrace can override movement speeds
        if (subrace != null) {
            if (subrace.getSpeed() > 0) {
                this.speed = subrace.getSpeed();
            }
            if (subrace.getSwimmingSpeed() > 0) {
                this.swimmingSpeed = subrace.getSwimmingSpeed();
            }
            if (subrace.getFlyingSpeed() > 0) {
                this.flyingSpeed = subrace.getFlyingSpeed();
            }
            if (subrace.getClimbingSpeed() > 0) {
                this.climbingSpeed = subrace.getClimbingSpeed();
            }
            if (subrace.getBurrowingSpeed() > 0) {
                this.burrowingSpeed = subrace.getBurrowingSpeed();
            }
            if (subrace.getDarkvision() != null) {
                this.darkvision = subrace.getDarkvision();
            }
        }

        // Apply proficiencies from race
        if (race != null) {
            this.weaponProficiencies.addAll(race.getWeaponProficiencies());
            this.armorProficiencies.addAll(race.getArmorProficiencies());
            this.toolProficiencies.addAll(race.getToolProficiencies());
            this.languages.addAll(race.getLanguages());
            this.damageResistances.addAll(race.getDamageResistances());
            this.innateSpells.addAll(race.getInnateSpells());

            // Add skill proficiencies from race (convert String to Skill enum)
            for (String skillName : race.getSkillProficiencies()) {
                try {
                    Skill skill = Skill.valueOf(skillName.toUpperCase().replace(" ", "_"));
                    this.skillProficiencies.add(skill);
                } catch (IllegalArgumentException e) {
                    // Skip invalid skill names
                }
            }
        }

        // Apply proficiencies from subrace (additive, not override)
        if (subrace != null) {
            this.weaponProficiencies.addAll(subrace.getWeaponProficiencies());
            this.armorProficiencies.addAll(subrace.getArmorProficiencies());
            this.toolProficiencies.addAll(subrace.getToolProficiencies());
            this.languages.addAll(subrace.getLanguages());
            this.damageResistances.addAll(subrace.getDamageResistances());
            this.innateSpells.addAll(subrace.getInnateSpells());

            // Add skill proficiencies from subrace
            for (String skillName : subrace.getSkillProficiencies()) {
                try {
                    Skill skill = Skill.valueOf(skillName.toUpperCase().replace(" ", "_"));
                    this.skillProficiencies.add(skill);
                } catch (IllegalArgumentException e) {
                    // Skip invalid skill names
                }
            }
        }

        // Initialize innate spell uses (including proficiency-scaled abilities)
        int proficiencyBonus = getProficiencyBonus();
        for (InnateSpell innateSpell : innateSpells) {
            innateSpell.initializeUses(proficiencyBonus);
        }
    }

    /**
     * Apply class proficiencies to the character.
     * This includes armor, weapon, and tool proficiencies from the character's class.
     */
    private void applyClassTraits() {
        if (dndClass == null) return;

        // Apply armor proficiencies from class
        if (dndClass.getArmorProficiencies() != null) {
            this.armorProficiencies.addAll(dndClass.getArmorProficiencies());
        }

        // Apply weapon proficiencies from class
        if (dndClass.getWeaponProficiencies() != null) {
            this.weaponProficiencies.addAll(dndClass.getWeaponProficiencies());
        }

        // Apply tool proficiencies from class
        if (dndClass.getToolProficiencies() != null) {
            this.toolProficiencies.addAll(dndClass.getToolProficiencies());
        }
    }

    /**
     * Apply background traits to the character.
     * This includes automatic tool proficiencies and languages from the background.
     * Note: Skill proficiencies are handled separately in loadSkillProficiencies().
     */
    private void applyBackgroundTraits() {
        if (background == null) return;

        // Apply automatic tool proficiencies
        if (background.getTools() != null) {
            this.toolProficiencies.addAll(background.getTools());
        }

        // Apply automatic languages
        if (background.getLanguages() != null) {
            this.languages.addAll(background.getLanguages());
        }
    }

    /**
     * Apply subclass traits to the character (Issue #64)
     * This includes bonus spells, additional spells, proficiencies, languages, darkvision, and swimming speed.
     * Traits are applied from the subclass if one is selected.
     */
    private void applySubclassTraits() {
        if (subclass == null) return;

        // Apply swimming speed from subclass (additive/override)
        if (subclass.getSwimmingSpeed() > 0) {
            this.swimmingSpeed = subclass.getSwimmingSpeed();
        }

        // Apply darkvision from subclass (upgrade if better)
        if (subclass.getDarkvision() > 0) {
            // Upgrade darkvision if subclass grants better vision
            if (this.darkvision == null || subclass.getDarkvision() > this.darkvision) {
                this.darkvision = subclass.getDarkvision();
            }
        }

        // Apply proficiencies from subclass
        if (subclass.getArmorProficiencies() != null) {
            this.armorProficiencies.addAll(subclass.getArmorProficiencies());
        }
        if (subclass.getWeaponProficiencies() != null) {
            this.weaponProficiencies.addAll(subclass.getWeaponProficiencies());
        }
        if (subclass.getToolProficiencies() != null) {
            this.toolProficiencies.addAll(subclass.getToolProficiencies());
        }
        if (subclass.getLanguages() != null) {
            this.languages.addAll(subclass.getLanguages());
        }
        if (subclass.getSkillProficiencies() != null) {
            for (String skillName : subclass.getSkillProficiencies()) {
                try {
                    Skill skill = Skill.valueOf(skillName.toUpperCase().replace(" ", "_"));
                    this.skillProficiencies.add(skill);
                } catch (IllegalArgumentException e) {
                    // Skip invalid skill names
                }
            }
        }

        // Add bonus spells from subclass (always known/prepared)
        if (subclass.getBonusSpells() != null) {
            for (String spellId : subclass.getBonusSpells()) {
                DndSpell spell = SpellLoader.getSpell(spellId);
                if (spell != null) {
                    if (spell.getLevel() == 0) {
                        this.knownCantrips.add(spell);
                    } else {
                        this.knownSpells.add(spell);
                    }
                }
            }
        }

        // Add additional spells from subclass (cantrips always known)
        if (subclass.getAdditionalSpells() != null) {
            for (String spellId : subclass.getAdditionalSpells()) {
                DndSpell spell = SpellLoader.getSpell(spellId);
                if (spell != null) {
                    if (spell.getLevel() == 0) {
                        this.knownCantrips.add(spell);
                    } else {
                        this.knownSpells.add(spell);
                    }
                }
            }
        }

        // TODO: Apply conditional bonus spells based on player choices (e.g., Genie patron type)
        // This will require looking up choices from CharacterCreationSession
        // For now, conditional spells are not applied automatically

        // TODO: Apply conditional advantages (e.g., advantage on saves vs disease)
        // This will require a conditional_advantages field on CharacterSheet
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

    /**
     * Loads skill proficiencies from the character creation session.
     * Applies automatic background skills AND extracts skill choices from pending choices (both class and background sources).
     */
    private void loadSkillProficiencies(CharacterCreationSession session) {
        // Apply automatic background skill proficiencies
        if (background != null && background.getSkills() != null) {
            for (String skillName : background.getSkills()) {
                Skill skill = Skill.fromString(skillName);
                if (skill != null) {
                    skillProficiencies.add(skill);
                }
            }
        }

        // Apply player-chosen skills from pending choices
        if (session == null || session.getPendingChoices() == null) return;

        for (PendingChoice<?> pc : session.getPendingChoices()) {
            // Only process SKILL type choices
            if (pc.getPlayersChoice().getType() != PlayersChoice.ChoiceType.SKILL) continue;

            // Get chosen skills (they're stored as strings like "acrobatics", "stealth", etc.)
            Set<?> chosen = pc.getChosen();
            for (Object obj : chosen) {
                if (obj instanceof String skillName) {
                    Skill skill = Skill.fromString(skillName);
                    if (skill != null) {
                        skillProficiencies.add(skill);
                    }
                }
            }
        }
    }

    /**
     * Loads tool proficiencies and languages from player choices in the character creation session.
     * Note: Automatic tool/language proficiencies are already applied in applyBackgroundTraits() and applyRacialTraits().
     */
    private void loadToolAndLanguageProficiencies(CharacterCreationSession session) {
        if (session == null || session.getPendingChoices() == null) return;

        for (PendingChoice<?> pc : session.getPendingChoices()) {
            Set<?> chosen = pc.getChosen();

            // Handle TOOL type choices
            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.TOOL) {
                for (Object obj : chosen) {
                    if (obj instanceof String toolName) {
                        toolProficiencies.add(toolName);
                    }
                }
            }

            // Handle LANGUAGE type choices
            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.LANGUAGE) {
                for (Object obj : chosen) {
                    if (obj instanceof String languageName) {
                        languages.add(languageName);
                    }
                }
            }

            // Handle CUSTOM choices (e.g. draconic_ancestry): remember the picked option by choice id
            // so feature actions can resolve their per-choice variant later (breath weapon, #70).
            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.CUSTOM) {
                for (Object obj : chosen) {
                    if (obj instanceof String value) {
                        setCustomChoice(pc.getId(), value);
                        break; // single-pick custom choices
                    }
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
        // Create a function to get ability modifiers for resource calculations
        java.util.function.Function<String, Integer> abilityModifier = (abilityName) -> {
            try {
                Ability ability = Ability.valueOf(abilityName.toUpperCase());
                return getModifier(ability);
            } catch (IllegalArgumentException e) {
                return 0; // If ability name is invalid, return 0
            }
        };

        if (dndClass != null) {
            classResources = dndClass.createResourcesForCharacter(getTotalLevel(), abilityModifier);
        }
        initializeFeatureResources();
    }

    /**
     * Materializes the resource pools declared by authored features that grant their own (Effect
     * Engine, #70) — e.g. a dragonborn's breath weapon (proficiency-bonus uses, long-rest recovery).
     * Keeps a race's limited-use feature fully data-driven, no class needed.
     */
    private void initializeFeatureResources() {
        for (io.papermc.jkvttplugin.effect.Feature f : getAllFeatures()) {
            if (!f.grantsResource()) continue;
            if (getResource(f.getCostResource()) != null) continue; // already provided (e.g. by class)
            int max = f.isGrantedResourceByProf() ? getProficiencyBonus() : f.getGrantedResourceMax();
            if (max <= 0) continue;
            ClassResource.RecoveryType recovery = parseRecovery(f.getGrantedResourceRecovery());
            classResources.add(new ClassResource(f.getCostResource(), max, recovery));
        }
    }

    private static ClassResource.RecoveryType parseRecovery(String s) {
        if (s == null) return ClassResource.RecoveryType.LONG_REST;
        return switch (s.toLowerCase()) {
            case "short_rest", "short" -> ClassResource.RecoveryType.SHORT_REST;
            case "dawn" -> ClassResource.RecoveryType.DAWN;
            case "none" -> ClassResource.RecoveryType.NONE;
            default -> ClassResource.RecoveryType.LONG_REST;
        };
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

        // Fallback for unknown items - still tag with item_id for shop compatibility (Issue #75)
        ItemStack fallbackItem = Util.createItem(
                Component.text(Util.prettify(itemId), NamedTextColor.WHITE),
                List.of(Component.text("Unknown item", NamedTextColor.GRAY)),
                "unknown_item",
                1
        );
        ItemUtil.tagItemId(fallbackItem, itemId);
        return fallbackItem;
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
    public void equipShield(DndArmor shield) {
        this.equippedShield = shield;
        calculateArmorClass();
    }
    public void unequipShield() {
        this.equippedShield = null;
        calculateArmorClass();
    }

    /**
     * Regenerate an inventory item from its id (via the loaders) and add it to this character's
     * equipment at the given quantity. Used when restoring saved equipment (#31).
     */
    public void addEquipmentItem(String itemId, int quantity) {
        ItemStack item = createItemFromId(itemId);
        if (item != null) {
            item.setAmount(Math.max(1, quantity));
            equipment.add(item);
        }
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

    public DndSubClass getSubclass() {
        return subclass;
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

    public int getTempHealth() {
        return tempHealth;
    }

    // ==================== Combat HP (Issue #100) ====================

    /**
     * Heal this character, never exceeding max HP. Ignores non-positive amounts.
     */
    public void heal(int amount) {
        if (amount <= 0) return;
        currentHealth = Math.min(totalHealth, currentHealth + amount);
    }

    /**
     * Grant temporary HP. Per 5e, temp HP does not stack — keep the higher value.
     */
    public void setTemporaryHp(int amount) {
        if (amount < 0) return;
        tempHealth = Math.max(tempHealth, amount);
    }

    /**
     * A character at 0 HP is unconscious (and begins death saves — Issue #101).
     */
    public boolean isUnconscious() {
        return currentHealth <= 0;
    }

    /**
     * Apply incoming damage. Temporary HP absorbs damage first; any remainder
     * reduces current HP and never drops it below 0.
     */
    public void takeDamage(int damage) {
        if (damage <= 0) return;

        // Temporary HP absorbs damage first; leftover temp HP persists.
        if (tempHealth > 0) {
            int absorbed = Math.min(tempHealth, damage);
            tempHealth -= absorbed;
            damage -= absorbed;
        }

        // Any remaining damage reduces current HP, never below 0 (MVP: no negative HP).
        currentHealth = Math.max(0, currentHealth - damage);
    }

    /**
     * Damage types this character resists (takes half). Vulnerability/immunity
     * data is not modeled yet — see the Issue #100 follow-up.
     */
    public Set<String> getDamageResistances() {
        return new HashSet<>(damageResistances);
    }

    public int getProficiencyBonus() {
        return DndRules.getProficiencyBonus(getTotalLevel());
    }

    public int getSpeed() {
        return speed; // Uses character's own speed field (set from race/subrace in applyRacialTraits)
    }

    /** Hours needed for a long rest (#160); 8 by default, 4 for elven trance. Not enforced yet (#45). */
    public int getLongRestHours() {
        return longRestHours;
    }
    /** Whether this character needs to sleep for a long rest (false for Warforged). Not enforced yet (#45). */
    public boolean isSleepRequired() {
        return sleepRequired;
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

    /**
     * Checks if the character is proficient in a specific skill.
     * @param skill The skill to check
     * @return true if proficient, false otherwise
     */
    public boolean isProficientInSkill(Skill skill) {
        return skillProficiencies.contains(skill);
    }

    /**
     * Calculates the skill bonus for a given skill.
     * Formula: ability modifier + (proficiency bonus if proficient)
     * @param skill The skill to calculate bonus for
     * @return The total skill bonus
     */
    public int getSkillBonus(Skill skill) {
        int abilityModifier = getModifier(skill.getAbility());
        int profBonus = isProficientInSkill(skill) ? getProficiencyBonus() : 0;
        return abilityModifier + profBonus;
    }

    /**
     * Gets a formatted breakdown of a skill bonus for display in chat.
     * Examples: "+3[DEX] +2[Prof]" (proficient), "+2[DEX]" (not proficient), "-1[STR]" (negative modifier)
     *
     * @param skill The skill to get the breakdown for
     * @return Formatted string showing ability modifier and proficiency bonus if applicable
     */
    public String getSkillBonusBreakdown(Skill skill) {
        int abilityModifier = getModifier(skill.getAbility());
        int profBonus = isProficientInSkill(skill) ? getProficiencyBonus() : 0;

        StringBuilder breakdown = new StringBuilder();

        // Add ability modifier with 3-letter abbreviation: "+3[DEX]" or "-1[STR]"
        breakdown.append(abilityModifier >= 0 ? "+" : "")
                 .append(abilityModifier)
                 .append("[")
                 .append(skill.getAbility().getAbbreviation())
                 .append("]");

        // Add proficiency if applicable: " +2[Prof]"
        if (profBonus > 0) {
            breakdown.append(" +").append(profBonus).append("[Prof]");
        }

        return breakdown.toString();
    }

    /**
     * Gets all skill proficiencies for this character.
     * @return Set of skills the character is proficient in
     */
    public Set<Skill> getSkillProficiencies() {
        return new HashSet<>(skillProficiencies);
    }

    /**
     * Gets all weapon proficiencies for this character.
     * Includes proficiencies from race, subrace, class, and subclass.
     * @return Set of weapon proficiency names (e.g., "longsword", "simple_weapons")
     */
    public Set<String> getWeaponProficiencies() {
        return new HashSet<>(weaponProficiencies);
    }

    /**
     * Gets all tool proficiencies for this character.
     * Includes proficiencies from race, subrace, class, background, and subclass.
     * @return Set of tool proficiency names (e.g., "smiths_tools", "thieves_tools")
     */
    public Set<String> getToolProficiencies() {
        return new HashSet<>(toolProficiencies);
    }

    /**
     * Gets all languages known by this character.
     * Includes languages from race, subrace, background, and subclass.
     * @return Set of language names (e.g., "Common", "Elvish", "Draconic")
     */
    public Set<String> getLanguages() {
        return new HashSet<>(languages);
    }

    /**
     * Checks if the character is proficient in a specific saving throw.
     * Proficiency is determined by the character's class.
     * @param ability The ability to check
     * @return true if proficient in this save, false otherwise
     */
    public boolean isProficientInSave(Ability ability) {
        return dndClass != null
                && dndClass.getSavingThrows() != null
                && dndClass.getSavingThrows().contains(ability);
    }

    /**
     * Calculates the saving throw bonus for a given ability.
     * Formula: ability modifier + (proficiency bonus if proficient)
     * @param ability The ability to calculate save bonus for
     * @return The total saving throw bonus
     */
    public int getSavingThrowBonus(Ability ability) {
        int abilityModifier = getModifier(ability);
        int profBonus = isProficientInSave(ability) ? getProficiencyBonus() : 0;
        return abilityModifier + profBonus;
    }

    /**
     * Gets a formatted breakdown of an ability check bonus for display in chat.
     * Ability checks never include proficiency.
     * Examples: "+3[STR]", "-1[INT]"
     *
     * @param ability The ability to get the breakdown for
     * @return Formatted string showing ability modifier only
     */
    public String getAbilityCheckBreakdown(Ability ability) {
        int abilityModifier = getModifier(ability);

        // Ability checks are just the modifier: "+3[STR]" or "-1[INT]"
        return (abilityModifier >= 0 ? "+" : "")
                + abilityModifier
                + "["
                + ability.getAbbreviation()
                + "]";
    }

    /**
     * Gets a formatted breakdown of a saving throw bonus for display in chat.
     * Examples: "+3[STR] +2[Prof]" (proficient), "+1[WIS]" (not proficient)
     *
     * @param ability The ability to get the save breakdown for
     * @return Formatted string showing ability modifier and proficiency bonus if applicable
     */
    public String getSaveBreakdown(Ability ability) {
        int abilityModifier = getModifier(ability);
        int profBonus = isProficientInSave(ability) ? getProficiencyBonus() : 0;

        StringBuilder breakdown = new StringBuilder();

        // Add ability modifier: "+3[STR]" or "-1[INT]"
        breakdown.append(abilityModifier >= 0 ? "+" : "")
                .append(abilityModifier)
                .append("[")
                .append(ability.getAbbreviation())
                .append("]");

        // Add proficiency if applicable: " +2[Prof]"
        if (profBonus > 0) {
            breakdown.append(" +").append(profBonus).append("[Prof]");
        }

        return breakdown.toString();
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

    /**
     * Sets the remaining spell slots for a level, clamped to 0..max. Used when restoring saved
     * state (#31): initializeSpellSlots() fills to full on load, then the spent amount is reapplied.
     */
    public void setSpellSlotsRemaining(int level, int remaining) {
        if (level < 1 || level > 9) return;
        spellSlots[level - 1] = Math.max(0, Math.min(maxSpellSlots[level - 1], remaining));
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

    /**
     * Gets all innate spells granted by this character's race and subrace.
     * This includes spells that may not yet be available due to level requirements.
     *
     * @return An unmodifiable list of all innate racial spells
     * @see #getAvailableInnateSpells() for spells available at current level
     */
    public List<InnateSpell> getInnateSpells() {
        return new ArrayList<>(innateSpells);
    }

    /**
     * Gets innate spells available at the character's current level.
     * Filters out spells that require a higher character level.
     *
     * <p>For example, a Drow's Darkness spell requires level 5, so it won't
     * appear in this list for a level 3 character, but will for a level 5+ character.
     *
     * @return A list of innate spells the character can currently use
     * @see #getInnateSpells() for all racial spells regardless of level
     */
    public List<InnateSpell> getAvailableInnateSpells() {
        int characterLevel = getTotalLevel();
        return innateSpells.stream()
                .filter(spell -> spell.isAvailableAtLevel(characterLevel))
                .toList();
    }

    /**
     * Checks if the character has any innate spells available at the specified spell level.
     * This is used to determine whether spell level buttons should be shown in the UI
     * for non-spellcasters who have racial magic abilities.
     *
     * @param spellLevel The spell level to check (1-9, not character level)
     * @return true if the character has at least one innate spell at this level, false otherwise
     */
    public boolean hasInnateSpellsAtLevel(int spellLevel) {
        return innateSpells.stream()
                .anyMatch(innate -> innate.isAvailableAtLevel(getTotalLevel())
                        && !innate.isCantrip()
                        && innate.getSpellLevel() == spellLevel);
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

    // ==================== ACTIVE EFFECTS (Effect Engine, #70) ====================

    public List<io.papermc.jkvttplugin.effect.ActiveEffect> getActiveEffects() { return activeEffects; }

    public boolean hasEffect(String sourceId) {
        for (var e : activeEffects) if (e.getSourceId().equalsIgnoreCase(sourceId)) return true;
        return false;
    }

    /** Attach an effect. A non-stacking effect from the same source refreshes rather than duplicating. */
    public void addEffect(io.papermc.jkvttplugin.effect.ActiveEffect effect) {
        if (effect == null) return;
        if (!effect.stacks()) removeEffect(effect.getSourceId());
        activeEffects.add(effect);
    }

    public void removeEffect(String sourceId) {
        activeEffects.removeIf(e -> e.getSourceId().equalsIgnoreCase(sourceId));
    }

    /** Look up an authored feature by id across this character's class and race (Effect Engine, #70). */
    public io.papermc.jkvttplugin.effect.Feature getFeature(String id) {
        if (id == null) return null;
        if (dndClass != null) {
            for (var f : dndClass.getFeatures()) if (f.getId().equalsIgnoreCase(id)) return f;
        }
        if (race != null) {
            for (var f : race.getFeatures()) if (f.getId().equalsIgnoreCase(id)) return f;
        }
        return null;
    }

    /** Every authored feature this character has, class then race (for menus/tab-completion). */
    public List<io.papermc.jkvttplugin.effect.Feature> getAllFeatures() {
        List<io.papermc.jkvttplugin.effect.Feature> all = new ArrayList<>();
        if (dndClass != null) all.addAll(dndClass.getFeatures());
        if (race != null) all.addAll(race.getFeatures());
        return all;
    }

    /** The option this character picked for a CUSTOM choice (e.g. draconic_ancestry), or null. */
    public String getCustomChoice(String choiceId) {
        return choiceId == null ? null : customChoices.get(choiceId);
    }
    public void setCustomChoice(String choiceId, String value) {
        if (choiceId != null && value != null) customChoices.put(choiceId, value);
    }
    public Map<String, String> getCustomChoices() { return customChoices; }

    // ---- combat read sites ----
    public boolean resistsDamage(String damageType) {
        for (var e : activeEffects) if (e.resists(damageType)) return true;
        return hasStaticResistance(damageType); // permanent racial/subrace resistances too
    }
    /** Name of the source granting resistance to this type (e.g. "Rage" or "Dragonborn"), or null. */
    public String resistanceSourceFor(String damageType) {
        for (var e : activeEffects) if (e.resists(damageType)) return e.getSourceName();
        if (hasStaticResistance(damageType)) return race != null ? race.getName() : "resistance";
        return null;
    }
    /** A permanent resistance from race/subrace (matched case-insensitively). */
    private boolean hasStaticResistance(String damageType) {
        if (damageType == null) return false;
        for (String r : damageResistances) if (r.equalsIgnoreCase(damageType)) return true;
        return false;
    }

    /**
     * Resolves a resistance the race links to a CUSTOM choice (e.g. dragonborn draconic ancestry →
     * its element) now that the choice is known, and folds it into the permanent resistances. Safe to
     * call more than once. Must run after the character's CUSTOM choices are populated (#51/#70).
     */
    public void applyLinkedResistances() {
        if (race == null || race.getLinkedResistanceChoice() == null) return;
        String picked = getCustomChoice(race.getLinkedResistanceChoice());
        if (picked == null) return;
        String type = race.getLinkedResistanceMapping().get(picked.trim().toLowerCase());
        if (type != null && !type.isBlank()) damageResistances.add(type);
    }
    public boolean hasAdvantageOn(String rollTag) {
        for (var e : activeEffects) if (e.givesAdvantageOn(rollTag)) return true;
        return false;
    }
    public boolean hasDisadvantageOn(String rollTag) {
        for (var e : activeEffects) if (e.givesDisadvantageOn(rollTag)) return true;
        return false;
    }
    /**
     * Whether a natural 1 on a d20 should be rerolled once (Halfling Lucky). Read from passive
     * feature definitions directly (rest-safe, no applied effect needed) and any active effect.
     */
    public boolean rerollsNat1() {
        for (var e : activeEffects) if (e.rerollsNat1()) return true;
        for (var f : getAllFeatures()) {
            boolean passive = f.getActivation() == null || f.getActivation().equalsIgnoreCase("passive");
            if (passive && f.hasApply() && f.getApplyTemplate().rerollsNat1()) return true;
        }
        return false;
    }
    public int bonusDamageFor(String rollTag) {
        int sum = 0;
        for (var e : activeEffects) sum += e.bonusDamageFor(rollTag);
        return sum;
    }
    /** Labeled effect bonus damage for a swing, e.g. "+2[Rage]" (empty if none), for #168 breakdowns. */
    public String bonusDamageBreakdownFor(String rollTag) {
        StringBuilder sb = new StringBuilder();
        for (var e : activeEffects) {
            int b = e.bonusDamageFor(rollTag);
            if (b != 0) sb.append(sb.length() > 0 ? " " : "").append(b > 0 ? "+" : "").append(b)
                    .append("[").append(e.getSourceName()).append("]");
        }
        return sb.toString();
    }

    // ---- duration lifecycle ----
    /** Note a maintenance trigger (e.g. "attacked", "took_damage") on all effects that need it. */
    public void markEffectsMaintained(String trigger) {
        for (var e : activeEffects) e.markMaintained(trigger);
    }
    /** Advance effect durations at this character's turn start; returns the effects that just expired. */
    public List<io.papermc.jkvttplugin.effect.ActiveEffect> tickEffectsTurnStart() {
        List<io.papermc.jkvttplugin.effect.ActiveEffect> ended = new ArrayList<>();
        activeEffects.removeIf(e -> {
            if (e.tickTurnStartAndCheckExpiry()) { ended.add(e); return true; }
            return false;
        });
        return ended;
    }
    /** Remove effects that end on the given rest ("short"/"long"); returns the expired effects. */
    public List<io.papermc.jkvttplugin.effect.ActiveEffect> clearEffectsOnRest(String restType) {
        List<io.papermc.jkvttplugin.effect.ActiveEffect> ended = new ArrayList<>();
        activeEffects.removeIf(e -> {
            if (e.endsOnRest(restType)) { ended.add(e); return true; }
            return false;
        });
        return ended;
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

        // Restore innate spells that recover on long rest
        int proficiencyBonus = getProficiencyBonus();
        for (InnateSpell innateSpell : innateSpells) {
            if ("long_rest".equalsIgnoreCase(innateSpell.getRecovery())) {
                innateSpell.resetUses(proficiencyBonus);
            }
        }

        breakConcentration();
        activeEffects.clear(); // a long rest ends any lingering buffs/debuffs (Effect Engine, #70)

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

        // Restore innate spells that recover on short rest
        int proficiencyBonus = getProficiencyBonus();
        for (InnateSpell innateSpell : innateSpells) {
            if ("short_rest".equalsIgnoreCase(innateSpell.getRecovery())) {
                innateSpell.resetUses(proficiencyBonus);
            }
        }

        // Warlocks recover Pact Magic slots on short rest
        // ToDo: Implement when Warlock-specific slot recovery is added
    }
}
