package io.papermc.jkvttplugin.effect;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * When an effect primitive holds, from its {@code requires:} list: {@code no_armor}, {@code no_shield}.
 * Shared by AC formulas (#220) and Martial Arts (#221) so "unarmored" means one thing everywhere.
 */
public record Requirements(Set<String> required) {

    public static final Set<String> KNOWN = Set.of("no_armor", "no_shield");
    public static final Requirements NONE = new Requirements(Set.of());

    /** Reads {@code requires:}; anything unknown is added to {@code problems} and ignored. */
    public static Requirements parse(Object node, List<String> problems) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : ParseUtil.normalizeStringList(node)) {
            String r = s.trim().toLowerCase();
            if (KNOWN.contains(r)) out.add(r);
            else problems.add("requires '" + s + "' isn't one of " + String.join(", ", new TreeSet<>(KNOWN)));
        }
        return new Requirements(Set.copyOf(out));
    }

    public boolean met(boolean wearingArmor, boolean holdingShield) {
        if (wearingArmor && required.contains("no_armor")) return false;
        return !(holdingShield && required.contains("no_shield"));
    }

    public boolean contains(String requirement) { return required.contains(requirement); }
}
