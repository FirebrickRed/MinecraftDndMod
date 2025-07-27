package io.papermc.jkvttplugin.data.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PlayersChoice<T> {
    private final int choose;
    private final List<T> options;

    public PlayersChoice(int choose, List<T> options) {
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
    }

    public int getChoose() {
        return choose;
    }

    public List<T> getOptions() {
        return options;
    }

    public boolean containsOptions(String value) {
        return options.contains(value);
    }

    @Override
    public String toString() {
        return "PlayersChoice{choose%d, options=%s}".formatted(choose, options);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayersChoice)) return false;
        PlayersChoice that = (PlayersChoice) o;
        return choose == that.choose && options.equals(that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(choose, options);
    }
}
