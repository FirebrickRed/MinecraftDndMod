package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A study check on an annotated block (#231): a library shelf that gives lore, an enchanting table
 * that gives the important bits. The block's description is the base line everyone gets; each DC tier
 * a character clears adds its own line on top.
 *
 * <p>The result is remembered <b>per character</b> (a new character after a death gets their own try)
 * and repeats on every later click: no reroll when nothing has changed (the DMG's "multiple ability checks" rule). A passive check
 * is re-read live instead, so a better bonus later shows more. Only the character who studied sees
 * the text; sharing it with the table is up to them.
 */
public final class Study {

    public enum Mode { OFF, PASSIVE, ROLLED }

    /** How the number came about, for the record: the game rolled it, they typed the d20, or the total. */
    public enum How {
        GAME("game rolled"), TYPED("typed d20"), TOTAL("typed total"), PASSIVE("passive");
        public final String label;
        How(String label) { this.label = label; }
    }

    public record Tier(int dc, String text) {}

    /**
     * One character's result. {@code shown} is the tier they've been told, or null while it waits
     * for the DM ({@link #dmFirst}); {@code tier} is what the roll earned.
     */
    public record Result(String characterName, String check, int total, How how, int tier, Integer shown) {
        public Result withShown(Integer s) { return new Result(characterName, check, total, how, tier, s); }
    }

    public static final int MAX_TIERS = 3;
    public static final int MAX_CHECKS = 3;

    public Mode mode = Mode.OFF;
    /** Skills or abilities that count, as lower-case enum names: "history", "intelligence". */
    public final List<String> checks = new ArrayList<>();
    /** Sorted by DC, lowest first; never more than {@link #MAX_TIERS}, never a blank text. */
    private final List<Tier> tiers = new ArrayList<>();
    /** The roll is made and saved, but the text waits for the DM's [Tell them]. */
    public boolean dmFirst;
    /** Character id → their result. */
    public final Map<UUID, Result> results = new LinkedHashMap<>();

    public boolean active() { return mode != Mode.OFF && !checks.isEmpty(); }

    public List<Tier> tiers() { return List.copyOf(tiers); }

    /** Keep the tiers with text, lowest DC first, at most {@link #MAX_TIERS}. */
    public void setTiers(List<Tier> in) {
        tiers.clear();
        for (Tier t : in) {
            if (t == null || t.text() == null || t.text().isBlank()) continue;
            tiers.add(new Tier(t.dc(), t.text().trim()));
        }
        tiers.sort(Comparator.comparingInt(Tier::dc));
        while (tiers.size() > MAX_TIERS) tiers.remove(tiers.size() - 1);
    }

    /** How many tiers this total clears: 0 = just the description. */
    public int tierFor(int total) {
        int n = 0;
        for (Tier t : tiers) if (total >= t.dc()) n++;
        return n;
    }

    /** The extra lines for tiers 1..tier. */
    public List<String> linesUpTo(int tier) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(tier, tiers.size()); i++) out.add(tiers.get(i).text());
        return out;
    }

    // ==================== CHECK IDS ====================

    /** "History", "sleight of hand", "INT" → "history", "sleight_of_hand", "intelligence"; null if neither. */
    public static String normalizeCheck(String raw) {
        if (raw == null) return null;
        String k = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (k.isEmpty()) return null;
        for (Skill s : Skill.values()) if (s.name().equals(k)) return k.toLowerCase(Locale.ROOT);
        for (Ability a : Ability.values()) {
            if (a.name().equals(k) || a.getAbbreviation().equals(k)) return a.name().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** The skill this check id names, or null when it's a plain ability check. */
    public static Skill skillOf(String check) {
        try { return Skill.valueOf(check.toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; }
    }

    /** The ability this check id names, or null when it's a skill. */
    public static Ability abilityOf(String check) {
        return check == null ? null : Ability.fromString(check);
    }

    /** "History", "Intelligence check". */
    public static String displayName(String check) {
        Skill s = skillOf(check);
        if (s != null) return s.getDisplayName();
        Ability a = abilityOf(check);
        if (a == null) return check;
        String n = a.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1) + " check";
    }

    /**
     * The check a {@code /character check} roll is, as a check id: ("SKILL", "HISTORY") → "history",
     * ("CHECK", "INTELLIGENCE") → "intelligence". Saves and tools aren't study checks: null.
     */
    public static String checkOfRoll(String type, String value) {
        if (value == null) return null;
        if ("SKILL".equals(type) && skillOf(value) != null) return value.toLowerCase(Locale.ROOT);
        if ("CHECK".equals(type) && abilityOf(value) != null) return value.toLowerCase(Locale.ROOT);
        return null;
    }

    /** "History, Arcana or Intelligence check" — the summary's and the dialog's wording. */
    public String checksText() {
        List<String> names = new ArrayList<>();
        for (String c : checks) names.add(displayName(c));
        if (names.size() <= 1) return String.join("", names);
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
    }

    /** "rolled History or Arcana, DC 10/15/20, you first" for the annotation summary. */
    public String summary() {
        if (!active()) return "";
        List<String> dcs = new ArrayList<>();
        for (Tier t : tiers) dcs.add(String.valueOf(t.dc()));
        return "study: " + mode.name().toLowerCase(Locale.ROOT) + " " + checksText()
                + (dcs.isEmpty() ? "" : ", DC " + String.join("/", dcs))
                + (dmFirst ? ", you first" : "")
                + (results.isEmpty() ? "" : ", " + results.size() + " studied");
    }

    // ==================== PERSISTENCE ====================

    public void save(ConfigurationSection s) {
        s.set("mode", mode.name());
        s.set("checks", new ArrayList<>(checks));
        s.set("dmFirst", dmFirst);
        List<Map<String, Object>> ts = new ArrayList<>();
        for (Tier t : tiers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dc", t.dc());
            m.put("text", t.text());
            ts.add(m);
        }
        s.set("tiers", ts);
        for (Map.Entry<UUID, Result> e : results.entrySet()) {
            ConfigurationSection r = s.createSection("results." + e.getKey());
            Result v = e.getValue();
            r.set("name", v.characterName());
            r.set("check", v.check());
            r.set("total", v.total());
            r.set("how", v.how().name());
            r.set("tier", v.tier());
            r.set("shown", v.shown() == null ? -1 : v.shown());
        }
    }

    public static Study load(ConfigurationSection s) {
        Study st = new Study();
        if (s == null) return st;
        try { st.mode = Mode.valueOf(s.getString("mode", "OFF")); } catch (IllegalArgumentException ignored) {}
        for (String c : s.getStringList("checks")) {
            String n = normalizeCheck(c);
            if (n != null && !st.checks.contains(n)) st.checks.add(n);
        }
        st.dmFirst = s.getBoolean("dmFirst", false);
        List<Tier> ts = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("tiers")) {
            Object dc = m.get("dc"), text = m.get("text");
            if (dc instanceof Number n && text != null) ts.add(new Tier(n.intValue(), text.toString()));
        }
        st.setTiers(ts);
        ConfigurationSection rs = s.getConfigurationSection("results");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(id);
                if (r == null) continue;
                UUID uuid;
                How how;
                try { uuid = UUID.fromString(id); how = How.valueOf(r.getString("how", "GAME")); }
                catch (IllegalArgumentException ex) { continue; }
                int shown = r.getInt("shown", -1);
                st.results.put(uuid, new Result(r.getString("name", "?"), r.getString("check", ""),
                        r.getInt("total"), how, r.getInt("tier"), shown < 0 ? null : shown));
            }
        }
        return st;
    }
}
