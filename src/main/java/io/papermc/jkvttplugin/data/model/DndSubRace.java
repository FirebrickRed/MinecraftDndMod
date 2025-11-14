package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.ChoiceUtil;
import io.papermc.jkvttplugin.util.LoreBuilder;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DndSubRace {
    private String id;
    private String name;
    private String description;

    private Map<Ability, Integer> fixedAbilityScores;
    private AbilityScoreChoice abilityScoreChoice;

    private List<String> traits;
    private List<String> languages;
    private List<ChoiceEntry> playerChoices = List.of();
    private String icon;

    // Mechanical trait implementations (Issue #51)
    // Subraces can override or extend base race traits
    private int speed;           // Override base race speed (e.g., Wood Elf 35 vs Elf 30), 0 = use parent race speed
    private int swimmingSpeed;   // Override/add swimming speed, 0 = use parent race speed or default
    private int flyingSpeed;     // Override/add flying speed, 0 = use parent race speed or can't fly
    private int climbingSpeed;   // Override/add climbing speed, 0 = use parent race speed or default
    private int burrowingSpeed;  // Override/add burrowing speed, 0 = use parent race speed or can't burrow
    private Integer darkvision;  // Override base race darkvision (e.g., Drow 120 vs Elf 60)
    private List<String> damageResistances = List.of();
    private List<String> skillProficiencies = List.of();
    private List<String> weaponProficiencies = List.of();
    private List<String> armorProficiencies = List.of();
    private List<String> toolProficiencies = List.of();
    private List<InnateSpell> innateSpells = List.of();

    public DndSubRace() {}

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public Map<Ability, Integer> getFixedAbilityScores() {
        return fixedAbilityScores;
    }
    public void setFixedAbilityScores(Map<Ability, Integer> fixedAbilityScores) {
        this.fixedAbilityScores = fixedAbilityScores != null ? Map.copyOf(fixedAbilityScores) : Map.of();
    }

    public AbilityScoreChoice getAbilityScoreChoice() {
        return abilityScoreChoice;
    }
    public void setAbilityScoreChoice(AbilityScoreChoice abilityScoreChoice) {
        this.abilityScoreChoice = abilityScoreChoice;
    }

    public List<String> getTraits() {
        return traits;
    }
    public void setTraits(List<String> traits) {
        this.traits = traits != null ? List.copyOf(traits) : List.of();
    }

    public List<String> getLanguages() {
        return languages;
    }
    public void setLanguages(List<String> languages) {
        this.languages = languages != null ? List.copyOf(languages) : List.of();
    }

    public int getSpeed() {
        return speed;
    }
    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getSwimmingSpeed() {
        return swimmingSpeed;
    }
    public void setSwimmingSpeed(int swimmingSpeed) {
        this.swimmingSpeed = swimmingSpeed;
    }

    public int getFlyingSpeed() {
        return flyingSpeed;
    }
    public void setFlyingSpeed(int flyingSpeed) {
        this.flyingSpeed = flyingSpeed;
    }

    public int getClimbingSpeed() {
        return climbingSpeed;
    }
    public void setClimbingSpeed(int climbingSpeed) {
        this.climbingSpeed = climbingSpeed;
    }

    public int getBurrowingSpeed() {
        return burrowingSpeed;
    }
    public void setBurrowingSpeed(int burrowingSpeed) {
        this.burrowingSpeed = burrowingSpeed;
    }

    public Integer getDarkvision() {
        return darkvision;
    }
    public void setDarkvision(Integer darkvision) {
        this.darkvision = darkvision;
    }

    public List<String> getDamageResistances() {
        return damageResistances;
    }
    public void setDamageResistances(List<String> damageResistances) {
        this.damageResistances = damageResistances != null ? List.copyOf(damageResistances) : List.of();
    }

    public List<String> getSkillProficiencies() {
        return skillProficiencies;
    }
    public void setSkillProficiencies(List<String> skillProficiencies) {
        this.skillProficiencies = skillProficiencies != null ? List.copyOf(skillProficiencies) : List.of();
    }

    public List<String> getWeaponProficiencies() {
        return weaponProficiencies;
    }
    public void setWeaponProficiencies(List<String> weaponProficiencies) {
        this.weaponProficiencies = weaponProficiencies != null ? List.copyOf(weaponProficiencies) : List.of();
    }

    public List<String> getArmorProficiencies() {
        return armorProficiencies;
    }
    public void setArmorProficiencies(List<String> armorProficiencies) {
        this.armorProficiencies = armorProficiencies != null ? List.copyOf(armorProficiencies) : List.of();
    }

    public List<String> getToolProficiencies() {
        return toolProficiencies;
    }
    public void setToolProficiencies(List<String> toolProficiencies) {
        this.toolProficiencies = toolProficiencies != null ? List.copyOf(toolProficiencies) : List.of();
    }

    public List<InnateSpell> getInnateSpells() {
        return innateSpells;
    }
    public void setInnateSpells(List<InnateSpell> innateSpells) {
        this.innateSpells = innateSpells != null ? List.copyOf(innateSpells) : List.of();
    }

    public void setPlayerChoices(List<ChoiceEntry> playerChoices) {
        this.playerChoices = playerChoices == null ? List.of() : List.copyOf(playerChoices);
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Material getIconMaterial() {
        // ToDo: update to use custom icons
        return Material.PAPER;
    }

    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getName()), null, icon, 0);
    }

    public void contributeChoices(List<PendingChoice<?>> out) {
        for(ChoiceEntry e : playerChoices) {
            PlayersChoice<String> pc = (PlayersChoice<String>) e.pc();
            if (ChoiceUtil.usable(pc)) {
                out.add(PendingChoice.ofStrings(e.id(), e.title(), pc, "race"));
            }
        }
    }

    /**
     * Contributes automatic grants (proficiencies, darkvision, etc.) from this subrace.
     * These are traits the player receives automatically without needing to choose.
     */
    public void contributeAutomaticGrants(List<AutomaticGrant> out) {
        String source = this.name;

        // Speed (only if different from base race)
        if (speed > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.SPEED, "Walking Speed", source, speed + " ft"));
        }
        if (swimmingSpeed > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.SPEED, "Swimming Speed", source, swimmingSpeed + " ft"));
        }
        if (flyingSpeed > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.SPEED, "Flying Speed", source, flyingSpeed + " ft"));
        }
        if (climbingSpeed > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.SPEED, "Climbing Speed", source, climbingSpeed + " ft"));
        }
        if (burrowingSpeed > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.SPEED, "Burrowing Speed", source, burrowingSpeed + " ft"));
        }

        // Darkvision (only if different from base race)
        if (darkvision != null && darkvision > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.DARKVISION, "Darkvision", source, darkvision + " ft"));
        }

        // Languages
        if (languages != null) {
            for (String lang : languages) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.LANGUAGE, Util.prettify(lang), source));
            }
        }

        // Skill Proficiencies
        if (skillProficiencies != null) {
            for (String skill : skillProficiencies) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.SKILL_PROFICIENCY, Util.prettify(skill), source));
            }
        }

        // Weapon Proficiencies
        if (weaponProficiencies != null) {
            for (String weapon : weaponProficiencies) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.WEAPON_PROFICIENCY, Util.prettify(weapon), source));
            }
        }

        // Armor Proficiencies
        if (armorProficiencies != null) {
            for (String armor : armorProficiencies) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.ARMOR_PROFICIENCY, Util.prettify(armor), source));
            }
        }

        // Tool Proficiencies
        if (toolProficiencies != null) {
            for (String tool : toolProficiencies) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.TOOL_PROFICIENCY, Util.prettify(tool), source));
            }
        }

        // Damage Resistances
        if (damageResistances != null) {
            for (String resistance : damageResistances) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.DAMAGE_RESISTANCE, Util.prettify(resistance), source));
            }
        }

        // Ability Score Bonuses
        if (fixedAbilityScores != null) {
            for (var entry : fixedAbilityScores.entrySet()) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.ABILITY_SCORE, entry.getKey().toString(), source, "+" + entry.getValue()));
            }
        }

        // Innate Spells
        if (innateSpells != null) {
            for (InnateSpell spell : innateSpells) {
                String spellName = Util.prettify(spell.getSpellId());
                String details = spell.isCantrip() ? "Cantrip" : "Level " + spell.getSpellLevel();
                if (!spell.isCantrip() && spell.getUses() > 0) {
                    details += ", " + spell.getUses() + "/day";
                }
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.INNATE_SPELL, spellName, source, details));
            }
        }
    }

    public List<Component> getSelectionMenuLore() {
        LoreBuilder builder = LoreBuilder.create();

        // Ability Score Choices (player choices - not automatic grants)
        if (abilityScoreChoice != null) {
            builder.blankLine()
                   .addLine("Ability Score Choice:", NamedTextColor.GOLD);
            for (List<Integer> distribution : abilityScoreChoice.getDistributions()) {
                String distText = distribution.stream()
                        .map(n -> "+" + n)
                        .collect(Collectors.joining("/"));
                builder.addLine("  Choose: " + distText, NamedTextColor.YELLOW);
            }
        }

        // Traits (unique features of this subrace)
        if (traits != null && !traits.isEmpty()) {
            List<String> truncatedTraits = traits.stream()
                    .map(trait -> trait.length() > 40 ? trait.substring(0, 37) + "..." : trait)
                    .toList();
            builder.addListSection("Traits:", truncatedTraits, NamedTextColor.AQUA, NamedTextColor.WHITE);
        }

        // Automatic Grants (ability scores, languages, speed, proficiencies, darkvision, resistances, etc.)
        List<AutomaticGrant> grants = new ArrayList<>();
        contributeAutomaticGrants(grants);
        builder.addAutomaticGrants(grants);

        // Player Choices (languages, etc.)
        builder.addLanguageChoices(null, playerChoices);

        // Description (flavor text)
        builder.addDescription(description, 50);

        return builder.build();
    }

    // Builder
    public static class Builder {
        private final DndSubRace instance = new DndSubRace();

        public Builder id(String id) {
            instance.setId(id);
            return this;
        }

        public Builder name(String name) {
            instance.setName(name);
            return this;
        }

        public Builder description(String description) {
            instance.setDescription(description);
            return this;
        }

        public Builder fixedAbilityScores(Map<Ability, Integer> fixedAbilityScores) {
            instance.setFixedAbilityScores(fixedAbilityScores);
            return this;
        }

        public Builder abilityScoreChoice(AbilityScoreChoice abilityScoreChoice) {
            instance.setAbilityScoreChoice(abilityScoreChoice);
            return this;
        }

        public Builder traits(List<String> traits) {
            instance.setTraits(traits);
            return this;
        }

        public Builder languages(List<String> languages) {
            instance.setLanguages(languages);
            return this;
        }

        public Builder playerChoices(List<ChoiceEntry> playerChoices) {
            instance.setPlayerChoices(playerChoices);
            return this;
        }

        public Builder icon(String icon) {
            instance.setIcon(icon);
            return this;
        }

        public Builder speed(int speed) {
            instance.setSpeed(speed);
            return this;
        }

        public Builder swimmingSpeed(int swimmingSpeed) {
            instance.setSwimmingSpeed(swimmingSpeed);
            return this;
        }

        public Builder flyingSpeed(int flyingSpeed) {
            instance.setFlyingSpeed(flyingSpeed);
            return this;
        }

        public Builder climbingSpeed(int climbingSpeed) {
            instance.setClimbingSpeed(climbingSpeed);
            return this;
        }

        public Builder burrowingSpeed(int burrowingSpeed) {
            instance.setBurrowingSpeed(burrowingSpeed);
            return this;
        }

        public Builder darkvision(Integer darkvision) {
            instance.setDarkvision(darkvision);
            return this;
        }

        public Builder damageResistances(List<String> damageResistances) {
            instance.setDamageResistances(damageResistances);
            return this;
        }

        public Builder skillProficiencies(List<String> skillProficiencies) {
            instance.setSkillProficiencies(skillProficiencies);
            return this;
        }

        public Builder weaponProficiencies(List<String> weaponProficiencies) {
            instance.setWeaponProficiencies(weaponProficiencies);
            return this;
        }

        public Builder armorProficiencies(List<String> armorProficiencies) {
            instance.setArmorProficiencies(armorProficiencies);
            return this;
        }

        public Builder toolProficiencies(List<String> toolProficiencies) {
            instance.setToolProficiencies(toolProficiencies);
            return this;
        }

        public Builder innateSpells(List<InnateSpell> innateSpells) {
            instance.setInnateSpells(innateSpells);
            return this;
        }

        public DndSubRace build() {
            return instance;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
