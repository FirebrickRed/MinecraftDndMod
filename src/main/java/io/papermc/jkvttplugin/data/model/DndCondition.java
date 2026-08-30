package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A D&D condition (Prone, Poisoned, Dodging, …) loaded from YAML (Issue #103). MVP is display-only:
 * we track which conditions a combatant has and show their rules; the DM interprets the effects.
 */
public class DndCondition {
    private String id;
    private String name;
    private List<String> rules = new ArrayList<>();
    /** If true, the condition is cleared automatically at the start of the creature's next turn
     *  (e.g. Dodging, Disengaged — they last "until your next turn"). */
    private boolean untilNextTurn;

    /** Optional Minecraft PotionEffectType name (e.g. BLINDNESS) applied to a player while they have
     *  this condition, for real in-game feedback. Removed when the condition ends. */
    private String minecraftEffect;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getRules() { return rules; }
    public void setRules(List<String> rules) { this.rules = rules != null ? rules : new ArrayList<>(); }

    public boolean isUntilNextTurn() { return untilNextTurn; }
    public void setUntilNextTurn(boolean untilNextTurn) { this.untilNextTurn = untilNextTurn; }

    public String getMinecraftEffect() { return minecraftEffect; }
    public void setMinecraftEffect(String minecraftEffect) { this.minecraftEffect = minecraftEffect; }
}
