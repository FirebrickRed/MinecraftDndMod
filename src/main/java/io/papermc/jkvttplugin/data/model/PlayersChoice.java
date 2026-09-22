package io.papermc.jkvttplugin.data.model;

import java.util.*;

public class PlayersChoice<T> {
    public enum ChoiceType {
        SKILL, TOOL, LANGUAGE, EQUIPMENT, FEAT, ABILITY_SCORE, SPELL, CUSTOM,
        /** Doubled proficiency in skills or tools you already have (rogue, bard). Options: skill ids, tool ids, or the tag `skill`. */
        EXPERTISE
    }

    private final int choose;
    private final List<T> options;
    private final ChoiceType type;

    public PlayersChoice(int choose, List<T> options, ChoiceType type) {
        if (choose < 0) {
            throw new IllegalArgumentException("Choose must be non-negative.");
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Options list must not be null or empty.");
        }
        if (choose > options.size()) {
            throw new IllegalArgumentException("Cannot choose more options than are available.");
        }

        this.choose = choose;
        this.options = Collections.unmodifiableList(options);
        this.type = type;
    }

    public int getChoose() {
        return choose;
    }

    public List<T> getOptions() {
        return options;
    }

    public ChoiceType getType() {
        return type;
    }

    /**
     * {@code also_give: true} on a {@code type: tool} choice: the picked tool is granted as an item
     * as well as a proficiency. A tool id is an item id, so one pick does both — the Guild
     * Artisan's "proficiency with one artisan's tool" and "a set of artisan's tools" are the same
     * tool, and two separate picks couldn't enforce that (#206).
     */
    private boolean alsoGive = false;

    public boolean isAlsoGive() {
        return alsoGive;
    }

    public PlayersChoice<T> alsoGive(boolean alsoGive) {
        this.alsoGive = alsoGive;
        return this;
    }

    /**
     * {@code casting_ability:} on a {@code type: spell} choice: the pick is the race's own magic,
     * cast with this ability whatever the class (a high elf's wizard cantrip is INT for a rogue
     * too). The pick becomes an innate spell instead of joining the class's cantrips. Null = a
     * class pick (a Nature cleric's druid cantrip), cast with the class's ability.
     */
    private io.papermc.jkvttplugin.data.model.enums.Ability castingAbility;

    public io.papermc.jkvttplugin.data.model.enums.Ability getCastingAbility() {
        return castingAbility;
    }

    public PlayersChoice<T> castingAbility(io.papermc.jkvttplugin.data.model.enums.Ability ability) {
        this.castingAbility = ability;
        return this;
    }

    // ToDo: update when I start using it based on use cases
    // it doesn't like that options may not be a string
//    public boolean containsOptions(String value) {
//        return options.contains(value);
//    }

    @Override
    public String toString() {
        return "PlayersChoice{choose%d, options=%s, type=%s}".formatted(choose, options, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayersChoice<?> that)) return false;
        return choose == that.choose &&
                options.equals(that.options) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(choose, options);
    }

    public static <T> PlayersChoice<T> fromMap(Map<?, ?> map, Class<T> clazz, ChoiceType choiceType) {
        int choose = 1;
        List<T> options = new ArrayList<>();

        Object chooseObj = map.get("choose");
        if (chooseObj instanceof Number) {
            choose = ((Number) chooseObj).intValue();
        } else if (chooseObj instanceof String s) {
            try { choose = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }

        Object optionsObj = map.get("options");
        if (optionsObj instanceof List<?> list) {
            for (Object item : list) {
                if (clazz == String.class && item instanceof String) {
                    options.add(clazz.cast(item));
                } else if (clazz.isEnum() && item instanceof String s) {
                    for (T constant : clazz.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equalsIgnoreCase(s.replace(' ', '_'))) {
                            options.add(constant);
                            break;
                        }
                    }
                }
            }
        }

        return new PlayersChoice<>(choose, options, choiceType);
    }

}
