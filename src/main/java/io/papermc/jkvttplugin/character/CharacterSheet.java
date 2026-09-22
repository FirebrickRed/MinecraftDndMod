package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
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
    private UUID playerId; // the owning player; changes only through a DM transfer
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

    // Event-driven persistence (#31): once a sheet is live (created or loaded), any state-changing
    // mutator flushes it straight to disk — no timed autosave. Stays false while the sheet is being
    // built/deserialized so partial state isn't written mid-construction.
    private transient boolean savable = false;

    private List<ItemStack> equipment = new ArrayList<>();
    private DndArmor equippedArmor;
    private DndArmor equippedShield;

    private List<ClassResource> classResources = new ArrayList<>();
    // Live buffs/debuffs on this character (the Effect Engine, #70). Read by combat when resolving.
    private final List<io.papermc.jkvttplugin.effect.ActiveEffect> activeEffects = new ArrayList<>();

    // Racial traits (Issue #51)
    private Set<String> weaponProficiencies = new HashSet<>();
    private Set<String> armorProficiencies = new HashSet<>();
    // Tool / language sets hold canonical ids (ToolRegistry / LanguageRegistry). The full sets are
    // re-derived from race/class/background on load; the chosen* subsets are the player's picks
    // from creation, which only exist here and so are what persistence saves (#17).
    private Set<String> toolProficiencies = new LinkedHashSet<>();
    private Set<String> languages = new LinkedHashSet<>();
    private final Set<String> chosenToolProficiencies = new LinkedHashSet<>();
    private final Set<String> chosenLanguages = new LinkedHashSet<>();
    // Expertise (doubled proficiency): skill ids and tool ids from creation picks, e.g. a rogue's
    // stealth + thieves_tools. Saved as-is; only counts where the character is also proficient.
    private final Set<String> expertise = new LinkedHashSet<>();
    private Set<String> damageResistances = new HashSet<>();
    // CUSTOM player-choice selections kept by choice id (e.g. draconic_ancestry -> "Red (Fire, …)").
    // Feature actions read these to resolve their per-choice variant (breath weapon, #70).
    private final Map<String, String> customChoices = new HashMap<>();
    // Half-Orc Relentless Endurance: once between long rests, dropping to 0 HP leaves you at 1 (#70).
    private boolean relentlessEnduranceUsed = false;
    // Dying and death (#101). The death-save tally and death itself belong to the character, not to
    // the combatant: a fight ending, a restart, or a new fight must not quietly bring anyone back.
    // A dead character ignores hit points and rests until a DM revives them (revive()).
    private int deathSaveSuccesses;
    private int deathSaveFailures;
    private boolean dead;
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

        // Base AC from stats. The persistence loader re-equips the saved armor/shield immediately
        // after this, which recalculates AC properly (#31).
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

        // Class languages (Druidic, Thieves' Cant) and fixed `skills:` — both parsed and shown in
        // creation, but never applied to the sheet before.
        if (dndClass.getLanguages() != null) {
            this.languages.addAll(dndClass.getLanguages());
        }
        if (dndClass.getSkills() != null) {
            for (String skillName : dndClass.getSkills()) {
                Skill skill = Skill.fromString(skillName);
                if (skill != null) this.skillProficiencies.add(skill);
            }
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

            // TOOL / LANGUAGE choices. Recorded separately as well: grants re-derive from content on
            // load, but a pick only exists on the sheet, so it's what gets persisted.
            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.TOOL) {
                for (Object obj : chosen) {
                    if (obj instanceof String tool) chosenToolProficiencies.add(ToolRegistry.idOf(tool));
                }
            }
            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.LANGUAGE) {
                for (Object obj : chosen) {
                    if (obj instanceof String language) chosenLanguages.add(LanguageRegistry.idOf(language));
                }
            }
            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.EXPERTISE) {
                for (Object obj : chosen) {
                    if (obj instanceof String key) expertise.add(key.toLowerCase());
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
        toolProficiencies.addAll(chosenToolProficiencies);
        languages.addAll(chosenLanguages);
    }

    /**
     * Restores the player's creation-time tool/language picks on load (#17). Grants are already
     * back from {@code loadFromData} re-applying race/class/background; these are the rest.
     */
    public void restoreChosenProficiencies(Collection<String> tools, Collection<String> langs) {
        if (tools != null) for (String t : tools) chosenToolProficiencies.add(ToolRegistry.idOf(t));
        if (langs != null) for (String l : langs) chosenLanguages.add(LanguageRegistry.idOf(l));
        toolProficiencies.addAll(chosenToolProficiencies);
        languages.addAll(chosenLanguages);
    }

    public Set<String> getChosenToolProficiencies() { return Collections.unmodifiableSet(chosenToolProficiencies); }
    public Set<String> getChosenLanguages() { return Collections.unmodifiableSet(chosenLanguages); }

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
            for (String entry : dndClass.getStartingEquipment()) {
                startingItems.addAll(createItemsFromStartingEntry(entry));
            }
        }

        if (background != null) {
            List<String> bgEquipment = background.getStartingEquipment();
            if (bgEquipment != null) {
                for (String entry : bgEquipment) {
                    startingItems.addAll(createItemsFromStartingEntry(entry));
                }
            }
        }

        // Every source's picks at once. This used to run only for "class" and "background", so an
        // equipment pick on a race or subclass was silently never granted.
        if (session != null) {
            startingItems.addAll(resolveEquipmentFromChoices(session.getPendingChoices()));
        }

//        if (dndClass != null && dndClass.getSpellcastingAbility() != null) {
//            String focusType = dndClass.getSpellFocus();
//        }

        equipment.addAll(startingItems);
    }

    /**
     * Items from the player's picks: every equipment choice, plus the tool itself for a
     * {@code type: tool} choice marked {@code also_give} (Guild Artisan, Folk Hero, Entertainer).
     */
    private List<ItemStack> resolveEquipmentFromChoices(List<PendingChoice<?>> pendingChoices) {
        List<ItemStack> items = new ArrayList<>();
        if (pendingChoices == null) return items;

        for (PendingChoice<?> pc : pendingChoices) {
            PlayersChoice<?> choice = pc.getPlayersChoice();
            if (choice.getType() == PlayersChoice.ChoiceType.EQUIPMENT) {
                for (Object obj : pc.getChosen()) {
                    if (obj instanceof EquipmentOption equipmentOption) {
                        items.addAll(createItemsFromEquipmentOption(equipmentOption));
                    }
                }
            } else if (choice.getType() == PlayersChoice.ChoiceType.TOOL && choice.isAlsoGive()) {
                for (Object obj : pc.getChosen()) {
                    // A vehicle has no item; the content check warns about also_give offering one.
                    if (obj instanceof String toolId && ItemUtil.displayNameOf(toolId) != null) {
                        items.addAll(createItemsFromEquipmentOption(EquipmentOption.item(toolId, 1)));
                    }
                }
            }
        }
        return items;
    }

    /** A fixed starting-equipment entry: a plain id, or "id xN" for a quantity (e.g. "gold_piece x15"). */
    private List<ItemStack> createItemsFromStartingEntry(String entry) {
        EquipmentOption option = io.papermc.jkvttplugin.data.loader.parser.EquipmentParser.parseStartingEntry(entry);
        return option == null ? List.of() : createItemsFromEquipmentOption(option);
    }

    private List<ItemStack> createItemsFromEquipmentOption(EquipmentOption option) {
        List<ItemStack> items = new ArrayList<>();

        switch (option.getKind()) {
            case ITEM -> {
                String itemId = option.getIdOrTag();
                int remaining = option.getQuantity();

                // Split quantities past the stack size (e.g. 20 torches) into several stacks.
                while (remaining > 0) {
                    ItemStack item = createItemFromId(itemId);
                    if (item == null) break;
                    int amount = Math.min(remaining, item.getMaxStackSize());
                    item.setAmount(amount);
                    items.add(item);
                    remaining -= amount;
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

    /** All armor proficiencies — race, subrace, class and subclass. */
    public Set<String> getArmorProficiencies() {
        return new HashSet<>(armorProficiencies);
    }

    public List<ItemStack> getEquipment() {
        return new ArrayList<>(equipment);
    }

    // ==================== ARMOR PROFICIENCY (#209) ====================

    /**
     * Worn armor and shield this character lacks proficiency with, by name; empty if none.
     * PHB p.144: wearing armor you're not proficient with gives disadvantage on any ability check,
     * saving throw or attack roll that involves Strength or Dexterity, and you can't cast spells.
     */
    public List<String> unproficientArmorWorn() {
        List<String> out = new ArrayList<>();
        if (equippedArmor != null && !isProficientWithArmor(equippedArmor)) out.add(equippedArmor.getName());
        if (equippedShield != null && !isProficientWithArmor(equippedShield)) out.add(equippedShield.getName());
        return out;
    }

    /** True if the armor penalty applies to a roll using this ability (STR or DEX, while wearing it). */
    public boolean armorPenaltyApplies(Ability ability) {
        return (ability == Ability.STRENGTH || ability == Ability.DEXTERITY) && !unproficientArmorWorn().isEmpty();
    }

    /** "not proficient with Plate Armor" — for roll reminders and refusals; null if no penalty. */
    public String armorPenaltyReason() {
        List<String> worn = unproficientArmorWorn();
        return worn.isEmpty() ? null : "not proficient with " + String.join(" + ", worn);
    }

    /**
     * Proficient by category (light_armor / medium_armor / heavy_armor / shields, as the class and
     * race YAML spell them) or by the specific armor's id.
     */
    public boolean isProficientWithArmor(DndArmor armor) {
        if (armor == null) return true;
        String category = armor.isShield() ? "shields" : Util.normalize(armor.getCategory()) + "_armor";
        for (String p : armorProficiencies) {
            String key = Util.normalize(p);
            if (key.equals(category) || key.equals(Util.normalize(armor.getId()))) return true;
            if (armor.isShield() && key.equals("shield")) return true;
        }
        return false;
    }

    public DndArmor getEquippedArmor() {
        return equippedArmor;
    }
    public void equipArmor(DndArmor armor) {
        this.equippedArmor = armor;
        calculateArmorClass();
        persist();
    }
    public void unequipArmor() {
        this.equippedArmor = null;
        calculateArmorClass();
        persist();
    }

    public DndArmor getEquippedShield() {
        return equippedShield;
    }
    public void equipShield(DndArmor shield) {
        this.equippedShield = shield;
        calculateArmorClass();
        persist();
    }
    public void unequipShield() {
        this.equippedShield = null;
        calculateArmorClass();
        persist();
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

    /**
     * Take {@code quantity} of an item off this character's recorded gear, so switching or deleting
     * the character doesn't try to strip items that are already gone (a broken set of thieves' tools,
     * #210). The live inventory is the caller's job; this keeps the sheet's list in step with it.
     */
    public void removeEquipmentItem(String itemId, int quantity) {
        int left = quantity;
        for (java.util.Iterator<ItemStack> it = equipment.iterator(); it.hasNext() && left > 0; ) {
            ItemStack stack = it.next();
            if (!itemId.equalsIgnoreCase(ItemUtil.getItemId(stack))) continue;
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) it.remove();
            else stack.setAmount(stack.getAmount() - take);
        }
        if (left < quantity) persist();
    }


    // ========== GETTERS ==========

    public UUID getPlayerId() {
        return playerId;
    }

    /** Hand this character to another player. Use CharacterSheetManager.transferCharacter, which re-files it. */
    public void setOwner(UUID newOwner) {
        this.playerId = newOwner;
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
     * Mark this sheet as live so future mutations auto-save. Called by the persistence layer once
     * the sheet has been created or loaded into memory; before that, mutators don't touch disk.
     */
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /**
     * Flush this sheet to disk immediately (#31). No-op until the sheet is live (see {@link #savable}),
     * so construction/deserialization don't write partial state. This replaces the old 5-minute timer:
     * every state-changing mutator below calls it, so a crash loses nothing but the in-progress turn.
     */
    private void persist() {
        if (savable) CharacterPersistenceLoader.saveCharacter(this);
    }

    /**
     * Heal this character, never exceeding max HP. Ignores non-positive amounts. Regaining any hit
     * points resets the death-save tally (PHB p.197). The dead can't be healed: that takes a spell
     * like Revivify, which is {@link #revive}.
     */
    public void heal(int amount) {
        if (amount <= 0 || dead) return;
        if (currentHealth <= 0) resetDeathSaveTally();
        currentHealth = Math.min(totalHealth, currentHealth + amount);
        persist();
    }

    /**
     * Grant temporary HP. Per 5e, temp HP does not stack — keep the higher value.
     */
    public void setTemporaryHp(int amount) {
        if (amount < 0) return;
        tempHealth = Math.max(tempHealth, amount);
        persist();
    }

    /**
     * A character at 0 HP is unconscious (and begins death saves — Issue #101).
     */
    public boolean isUnconscious() {
        return currentHealth <= 0;
    }

    /** Apply incoming damage that isn't a critical hit. See {@link #takeDamage(int, boolean)}. */
    public void takeDamage(int damage) {
        takeDamage(damage, false);
    }

    /**
     * Apply incoming damage, with the 0-HP rules from PHB p.197. Temporary HP absorbs damage first;
     * the remainder reduces current HP, never below 0.
     *
     * <ul>
     *   <li>Dropping to 0 starts a fresh death-save tally, unless the damage left over after reaching
     *       0 is at least the hit point maximum: that's instant death (massive damage).</li>
     *   <li>Damage while already at 0 is a failed death save, two on a critical hit, or instant death
     *       if it's at least the hit point maximum.</li>
     * </ul>
     * The dead take no further damage.
     */
    public void takeDamage(int damage, boolean critical) {
        if (damage <= 0 || dead) return;

        // Temporary HP absorbs damage first; leftover temp HP persists.
        if (tempHealth > 0) {
            int absorbed = Math.min(tempHealth, damage);
            tempHealth -= absorbed;
            damage -= absorbed;
        }

        if (damage > 0) {
            if (currentHealth > 0) {
                int leftOver = damage - currentHealth;
                currentHealth = Math.max(0, currentHealth - damage);
                if (currentHealth == 0) {
                    resetDeathSaveTally(); // a fresh fall starts a fresh tally
                    if (leftOver >= totalHealth) markDead();
                }
            } else if (damage >= totalHealth) {
                markDead();
            } else {
                recordDeathSaveFailures(critical ? 2 : 1);
            }
        }
        persist();
    }

    // ==================== Dying and death (Issue #101) ====================

    public boolean isDead() { return dead; }
    public int getDeathSaveSuccesses() { return deathSaveSuccesses; }
    public int getDeathSaveFailures() { return deathSaveFailures; }

    /** At 0 HP with three successes: unconscious, but no longer rolling death saves. */
    public boolean isStable() {
        return !dead && currentHealth <= 0 && deathSaveSuccesses >= 3;
    }

    /** One successful death save; the third makes the character stable. */
    public void addDeathSaveSuccess() {
        if (dead) return;
        deathSaveSuccesses = Math.min(3, deathSaveSuccesses + 1);
        persist();
    }

    /** Failed death saves (two for a natural 1); the third kills. */
    public void addDeathSaveFailures(int count) {
        if (dead || count <= 0) return;
        recordDeathSaveFailures(count);
        persist();
    }

    /** Clear the tally: the character regained hit points, or has just dropped and starts over. */
    public void resetDeathSaves() {
        resetDeathSaveTally();
        persist();
    }

    /**
     * Bring a dead character back (Revivify, Raise Dead, or DM fiat) at {@code hp} hit points,
     * clamped to 1..max. Returns false, changing nothing, if the character isn't dead.
     */
    public boolean revive(int hp) {
        if (!dead) return false;
        dead = false;
        resetDeathSaveTally();
        breakConcentration(); // nothing carries over from before the death
        activeEffects.clear();
        currentHealth = Math.max(1, Math.min(totalHealth, hp));
        persist();
        return true;
    }

    /** Restore saved temp HP and per-rest trait uses on load, without persisting mid-deserialization. */
    public void restoreRestState(int tempHp, boolean relentlessUsed) {
        this.tempHealth = Math.max(0, tempHp);
        this.relentlessEnduranceUsed = relentlessUsed;
    }

    /** Restore saved death state on load, without persisting mid-deserialization. */
    public void restoreDeathState(int successes, int failures, boolean isDead) {
        this.deathSaveSuccesses = Math.max(0, Math.min(3, successes));
        this.deathSaveFailures = Math.max(0, Math.min(3, failures));
        this.dead = isDead;
        if (isDead) this.currentHealth = 0;
    }

    private void recordDeathSaveFailures(int count) {
        deathSaveFailures = Math.min(3, deathSaveFailures + count);
        if (deathSaveFailures >= 3) markDead();
    }

    private void resetDeathSaveTally() {
        deathSaveSuccesses = 0;
        deathSaveFailures = 0;
    }

    /**
     * Concentration and active effects are deliberately left alone here: the combat layer ends them
     * when the character drops (ConcentrationManager, endCombat) and announces it, and it can't if
     * they've already vanished. {@link #revive} clears whatever is left.
     */
    private void markDead() {
        dead = true;
        currentHealth = 0;
        tempHealth = 0;
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
        return getModifier(skill.getAbility()) + skillProficiencyBonus(skill);
    }

    /**
     * Gets a formatted breakdown of a skill bonus for display in chat.
     * Examples: "+3[DEX] +2[Prof]" (proficient), "+3[DEX] +4[Expertise]", "+2[DEX]" (not proficient)
     *
     * @param skill The skill to get the breakdown for
     * @return Formatted string showing ability modifier and proficiency bonus if applicable
     */
    public String getSkillBonusBreakdown(Skill skill) {
        int abilityModifier = getModifier(skill.getAbility());
        int profBonus = skillProficiencyBonus(skill);

        StringBuilder breakdown = new StringBuilder();

        // Add ability modifier with 3-letter abbreviation: "+3[DEX]" or "-1[STR]"
        breakdown.append(abilityModifier >= 0 ? "+" : "")
                 .append(abilityModifier)
                 .append("[")
                 .append(skill.getAbility().getAbbreviation())
                 .append("]");

        // Add proficiency if applicable: " +2[Prof]", or doubled " +4[Expertise]"
        if (profBonus > 0) {
            breakdown.append(" +").append(profBonus).append(hasExpertise(skill.name()) ? "[Expertise]" : "[Prof]");
        }

        return breakdown.toString();
    }

    /** Proficiency added to a skill check: 0, the bonus, or double it with expertise. */
    private int skillProficiencyBonus(Skill skill) {
        if (!isProficientInSkill(skill)) return 0;
        return getProficiencyBonus() * (hasExpertise(skill.name()) ? 2 : 1);
    }

    // ==================== TOOL CHECKS (#207) ====================

    public boolean isProficientWithTool(String tool) {
        return toolProficiencies.contains(ToolRegistry.idOf(tool));
    }

    /**
     * An ability check using a tool (PHB p.154): the ability modifier, plus the proficiency bonus if
     * proficient with the tool, doubled with expertise (a rogue's thieves' tools).
     */
    public int getToolCheckBonus(Ability ability, String tool) {
        return getModifier(ability) + toolProficiencyBonus(tool);
    }

    /** "+3[DEX] +2[Thieves' Tools]", "+3[DEX] +4[Thieves' Tools ×2]", or "+3[DEX]" when not proficient. */
    public String getToolCheckBreakdown(Ability ability, String tool) {
        int mod = getModifier(ability);
        StringBuilder b = new StringBuilder().append(mod >= 0 ? "+" : "").append(mod)
                .append("[").append(ability.getAbbreviation()).append("]");
        int prof = toolProficiencyBonus(tool);
        if (prof > 0) {
            b.append(" +").append(prof).append("[").append(ToolRegistry.displayName(tool))
                    .append(hasExpertise(tool) ? " ×2" : "").append("]");
        }
        return b.toString();
    }

    private int toolProficiencyBonus(String tool) {
        if (!isProficientWithTool(tool)) return 0;
        return getProficiencyBonus() * (hasExpertise(tool) ? 2 : 1);
    }

    // ==================== EXPERTISE ====================

    /**
     * True if this skill or tool has expertise (doubled proficiency). Keys are skill ids
     * ({@code stealth}) and tool ids ({@code thieves_tools}); expertise only counts where the
     * character is also proficient, which creation enforces.
     */
    public boolean hasExpertise(String skillOrTool) {
        if (skillOrTool == null) return false;
        return expertise.contains(skillOrTool.toLowerCase()) || expertise.contains(ToolRegistry.idOf(skillOrTool));
    }

    public Set<String> getExpertise() { return Collections.unmodifiableSet(expertise); }

    /** Restores saved expertise on load. */
    public void restoreExpertise(Collection<String> keys) {
        if (keys != null) for (String k : keys) if (k != null && !k.isBlank()) expertise.add(k.toLowerCase());
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
        persist();
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
        // (persist() below is a no-op during load, when this is called to reapply spent slots.)
        if (level < 1 || level > 9) return;
        spellSlots[level - 1] = Math.max(0, Math.min(maxSpellSlots[level - 1], remaining));
        persist();
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
        clearSpellMark(); // Hex/Hunter's Mark end when concentration does
    }

    // ---- Spell mark (Hex / Hunter's Mark, #178) ----
    // Entities can't hold effects, so a mark lives on the CASTER: who they've marked, the rider
    // damage on their hits, and (Hex) the ability the target has disadvantage on checks with.
    private java.util.UUID markTargetId;
    private String markDamage;              // rider dice, e.g. "1d6"
    private String markDamageType;          // e.g. "necrotic"
    private String markCheckDisadvantageAbility; // ability name (Hex's chosen ability), or null

    public void setSpellMark(java.util.UUID targetId, String damage, String damageType, String disadvantageAbility) {
        this.markTargetId = targetId;
        this.markDamage = damage;
        this.markDamageType = damageType;
        this.markCheckDisadvantageAbility = disadvantageAbility;
    }
    public void clearSpellMark() {
        markTargetId = null; markDamage = null; markDamageType = null; markCheckDisadvantageAbility = null;
    }
    public java.util.UUID getMarkTargetId() { return markTargetId; }
    public String getMarkDamageType() { return markDamageType; }
    public String getMarkCheckDisadvantageAbility() { return markCheckDisadvantageAbility; }
    /** The rider damage dice the caster adds when hitting {@code target}, or null if it isn't marked. */
    public String markRiderAgainst(java.util.UUID target) {
        return target != null && target.equals(markTargetId) ? markDamage : null;
    }

    public boolean hasSpellSlot(int level) {
        if (level < 1 || level > 9) return false;
        return spellSlots[level - 1] > 0;
    }

    public void consumeSpellSlot(int level) {
        if (level < 1 || level > 9) return;
        if (spellSlots[level - 1] > 0) {
            spellSlots[level - 1] -= 1;
            persist();
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

    /** The racial innate spell for this spell, if the character has it at their level; else null. */
    public InnateSpell findAvailableInnateSpell(DndSpell spell) {
        if (spell == null) return null;
        for (InnateSpell i : getAvailableInnateSpells()) {
            if (i.getSpellId() != null && i.getSpellId().equalsIgnoreCase(spell.getId())) return i;
        }
        return null;
    }

    /**
     * Whether the character can cast this spell at all: a class cantrip or spell, or a racial innate
     * spell they're high enough level for. Every cast path asks this one question; they used to
     * check only the class lists, so a tiefling rogue "didn't know" their own Thaumaturgy.
     */
    public boolean knowsSpell(DndSpell spell) {
        if (spell == null) return false;
        // By id, not object identity: /dm reload builds new DndSpell objects.
        for (DndSpell s : knownCantrips) if (s.getId().equalsIgnoreCase(spell.getId())) return true;
        for (DndSpell s : knownSpells) if (s.getId().equalsIgnoreCase(spell.getId())) return true;
        return findAvailableInnateSpell(spell) != null;
    }

    /**
     * The ability a spell is cast with: a racial innate spell's own {@code casting_ability} (a
     * tiefling's CHA, whatever their class), else the class's spellcasting ability. Null when
     * neither applies, e.g. a non-caster class with no innate spell.
     */
    public Ability castingAbilityFor(DndSpell spell) {
        InnateSpell innate = findAvailableInnateSpell(spell);
        if (innate != null && innate.getCastingAbility() != null) return innate.getCastingAbility();
        if (getMainClass() == null || getMainClass().getSpellcastingInfo() == null) return null;
        String name = getMainClass().getSpellcastingInfo().getCastingAbility();
        if (name == null) return null;
        try { return Ability.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
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
        persist(); // effects save like HP and slots do (#212) — a crash right after raging keeps the Rage
    }

    public void removeEffect(String sourceId) {
        if (activeEffects.removeIf(e -> e.getSourceId().equalsIgnoreCase(sourceId))) persist();
    }

    /**
     * Re-attach a saved effect on load (#212). Only the live state (rounds left, maintained this
     * round) is saved; the effect itself is rebuilt from its feature's YAML — this character's own
     * features first, then any class or race (an effect can come from someone else's feature).
     * Returns false if that feature no longer exists (renamed or removed), so the caller can report it.
     */
    public boolean restoreActiveEffect(String sourceId, int roundsRemaining, boolean maintainedThisRound) {
        io.papermc.jkvttplugin.effect.Feature feature = getFeature(sourceId);
        if (feature == null) feature = findFeatureAnywhere(sourceId);
        if (feature == null || !feature.hasApply()) return false;
        io.papermc.jkvttplugin.effect.ActiveEffect effect = feature.getApplyTemplate().copy();
        effect.restoreState(roundsRemaining, maintainedThisRound);
        activeEffects.add(effect);
        return true;
    }

    private static io.papermc.jkvttplugin.effect.Feature findFeatureAnywhere(String id) {
        for (DndClass c : ClassLoader.getAllClasses()) {
            for (var f : c.getFeatures()) if (f.getId().equalsIgnoreCase(id)) return f;
        }
        for (DndRace r : RaceLoader.getAllRaces()) {
            for (var f : r.getFeatures()) if (f.getId().equalsIgnoreCase(id)) return f;
        }
        return null;
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

    /** Conditional advantages from race + subclass, e.g. [{type: saving_throw, condition: poison}] (#103/#174). */
    public List<Map<String, String>> getAllConditionalAdvantages() {
        List<Map<String, String>> all = new ArrayList<>();
        if (race != null && race.getConditionalAdvantages() != null) all.addAll(race.getConditionalAdvantages());
        if (subclass != null && subclass.getConditionalAdvantages() != null) all.addAll(subclass.getConditionalAdvantages());
        return all;
    }

    /** True if a conditional advantage grants advantage on saving throws against any of {@code tags}. */
    public boolean hasSaveAdvantageVs(Set<String> tags) {
        if (tags == null || tags.isEmpty()) return false;
        for (Map<String, String> ca : getAllConditionalAdvantages()) {
            if (!"saving_throw".equalsIgnoreCase(ca.getOrDefault("type", ""))) continue;
            String cond = ca.getOrDefault("condition", "").toLowerCase();
            if (!cond.isEmpty() && tags.contains(cond)) return true;
        }
        return false;
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
     * Whether an always-on boolean primitive (e.g. "reroll_natural_1") is granted by a passive
     * feature (read from the definition directly, so it's rest-safe) or a live active effect.
     */
    public boolean hasPassiveFlag(String flag) {
        for (var e : activeEffects) if (e.hasFlag(flag)) return true;
        for (var f : getAllFeatures()) {
            boolean passive = f.getActivation() == null || f.getActivation().equalsIgnoreCase("passive");
            if (passive && f.hasApply() && f.getApplyTemplate().hasFlag(flag)) return true;
        }
        return false;
    }
    /** Reroll a natural 1 on a d20 once (Halfling Lucky). */
    public boolean rerollsNat1() { return hasPassiveFlag("reroll_natural_1"); }
    /** Roll one extra weapon die on a melee crit (Half-Orc Savage Attacks). */
    public boolean hasExtraCritDie() { return hasPassiveFlag("extra_crit_die"); }
    /** Can drop to 1 HP instead of 0 (Half-Orc Relentless Endurance) — subject to its per-rest use. */
    public boolean hasRelentlessEndurance() { return hasPassiveFlag("endure_below_1"); }
    /** True if Relentless Endurance is available right now (has the trait and hasn't used it since a long rest). */
    public boolean canEndureLethalHit() { return hasRelentlessEndurance() && !relentlessEnduranceUsed; }
    /** Spend Relentless Endurance for this long-rest window. Saved, so a restart doesn't hand it back. */
    public void markRelentlessEnduranceUsed() { relentlessEnduranceUsed = true; persist(); }
    public boolean isRelentlessEnduranceUsed() { return relentlessEnduranceUsed; }
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
        if (activeEffects.isEmpty()) return ended;
        activeEffects.removeIf(e -> {
            if (e.tickTurnStartAndCheckExpiry()) { ended.add(e); return true; }
            return false;
        });
        persist(); // rounds left changed (#212)
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

    /**
     * A long rest. A character needs at least 1 HP at the start to benefit (PHB p.186), so this does
     * nothing at 0 HP: not for the dying, the stable, or the dead.
     */
    public void longRest() {
        if (dead || currentHealth <= 0) return;
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
        relentlessEnduranceUsed = false; // Half-Orc Relentless Endurance recharges on a long rest

        currentHealth = totalHealth;
        tempHealth = 0;
        persist();
    }

    /** A short rest. Does nothing for a dead character. */
    public void shortRest() {
        if (dead) return;
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
        persist();
    }
}
