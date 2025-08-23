package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.Util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class PendingChoice<T> {
    private final String id;
    private final String title;

    private final PlayersChoice<T> playersChoice;
    private final String source; // e.g. "race", "class", "background"

    private final LinkedHashSet<T> chosen = new LinkedHashSet<>();

    private final Function<T, String> toKey;
    private final Function<String, T> fromKey;
    private final Function<T, String> toLabel;

    public PendingChoice(String id, String title, PlayersChoice<T> playersChoice, String source, Function<T, String> toKey, Function<String, T> fromKey, Function<T, String> toLabel) {
        this.id = id;
        this.title = title;
        this.playersChoice = playersChoice;
        this.source = source;
        this.toKey = toKey;
        this.fromKey = fromKey;
        this.toLabel = toLabel;
    }

    public static PendingChoice<String> ofStrings(String id, String title, PlayersChoice<String> pc, String source) {
        return new PendingChoice<>(id, title, pc, source, s -> s, s -> s, Util::prettify);
    }

    public static <E extends Enum<E>> PendingChoice<E> ofEnum(String id, String title, PlayersChoice<E> pc, String source, Class<E> enumClass) {
        return new PendingChoice<>(id, title, pc, source,
                Enum::toString,
                key -> {
                    if (key == null) return null;
                    return Enum.valueOf(enumClass, key.toUpperCase());
                },
                e -> Util.prettify(e.name())
            );
    }

    public static <T> PendingChoice<T> ofGeneric(String id, String title, PlayersChoice<T> pc, String source) {
        Function<T, String> toKey = t -> String.valueOf(t);
        Function<String, T> fromKey = key -> pc.getOptions().stream()
                .filter(o -> String.valueOf(o).equalsIgnoreCase(key))
                .findFirst().orElse(null);
        return new PendingChoice<>(id, title, pc, source, toKey, fromKey, toKey);
    }

    public static <T> PendingChoice<T> ofGeneric(String id, String title, PlayersChoice<T> pc, String source, Function<T, String> toKey, Function<String, T> fromKey, Function<T, String> toLabel) {
        return new PendingChoice<>(id, title, pc, source, toKey, fromKey, toLabel);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public PlayersChoice<T> getPlayersChoice() { return playersChoice; }
    public String getSource() { return source; }
    public Set<T> getChosen() {
        return chosen;
    }

    public int getChoose() { return playersChoice.getChoose(); }

    public boolean isComplete() { return chosen.size() >= playersChoice.getChoose(); }

    public List<String> optionKeys() {
        return playersChoice.getOptions().stream().map(toKey).toList();
    }

    public String displayFor(String key) {
        T t = fromKey.apply(key);
        return (t == null) ? key : toLabel.apply(t);
    }

    public boolean isSelectedKey(String key) { return chosen.contains(fromKey.apply(key)); }
    public void selectKey(String key) { chosen.add(fromKey.apply(key)); }
    public void unselectKey(String key) { chosen.remove(fromKey.apply(key)); }
    public int selectedCount() { return chosen.size(); }

    public boolean toggleOption(T option, Set<T> alreadyOwned) {
        if (chosen.contains(option)) {
            chosen.remove(option);
            return false; // Option was removed
        } else {
            // Do not allow duplicates across all sources
            if (alreadyOwned.contains(option)) return false;
            if (chosen.size() >= playersChoice.getChoose()) {
                // Remove oldest to keep size correct (replace behavior)
                var it = chosen.iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            chosen.add(option);
            return true;
        }
    }

    public boolean toggleKey(String key, Set<T> alreadyOwned) {
        T option = fromKey.apply(key);
        if (option == null) return false;
        return toggleOption(option, alreadyOwned);
    }


    @Override
    public String toString() {
        return "PendingChoice{id='%s', title='%s', choose=%d, selected=%s".formatted(id, title, playersChoice.getChoose(), chosen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, playersChoice);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PendingChoice<?> other)) return false;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.title, other.title)
                && Objects.equals(this.playersChoice, other.playersChoice);
    }
}
