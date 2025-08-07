package io.papermc.jkvttplugin.data.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PendingChoice<T> {
    private final PlayersChoice<T> playersChoice;
    private final String source; // e.g. "race", "class", "background"
    private final LinkedHashSet<T> chosen = new LinkedHashSet<>();

    public PendingChoice(PlayersChoice<T> playersChoice, String source) {
        this.playersChoice = playersChoice;
        this.source = source;
    }

    public PlayersChoice<T> getPlayersChoice() {
        return playersChoice;
    }

    public String getSource() {
        return source;
    }

    public Set<T> getChosen() {
        return chosen;
    }

    public boolean isComplete() {
        return chosen.size() >= playersChoice.getChoose();
    }

    public boolean toggleOption(T option, Set<T> alreadyOwned) {
        if (chosen.contains(option)) {
            chosen.remove(option);
            return false; // Option was removed
        } else {
            // Do not allow duplicates across all sources
            if (alreadyOwned.contains(option)) return false;
            if (chosen.size() >= playersChoice.getChoose()) {
                // Remove oldest to keep size correct (replace behavior)
                chosen.iterator().next();
                chosen.remove(chosen.iterator().next());
            }
            chosen.add(option);
            return true;
        }
    }

    // For showing unchosen options
    public List<T> getAvailableOptions(Set<T> alreadyOwned) {
        return playersChoice.getOptions().stream()
                .filter(option -> !alreadyOwned.contains(option) || chosen.contains(option))
                .toList();
    }
}
