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
    /** Potion amplifier (0 = level I). Optional; lets e.g. Paralyzed use a stronger Slowness. */
    private int minecraftEffectAmplifier;

    // Mechanical enforcement (Issue #150):
    /** Speed 0 — the creature can't move (Restrained, Grappled, Paralyzed, Stunned, …). */
    private boolean noMovement;
    /** Can't take actions or reactions (Incapacitated, Stunned, Paralyzed, Unconscious, …). */
    private boolean noActions;

    // Advantage/disadvantage this condition imposes (Issue #103). Values: "advantage" | "disadvantage".
    /** The bearer's own attack rolls (e.g. Poisoned, Prone, Restrained, Blinded, Frightened → disadvantage). */
    private String selfAttack;
    /** The bearer's own ability checks (e.g. Poisoned, Frightened → disadvantage). */
    private String selfCheck;
    /** Attack rolls made AGAINST the bearer (e.g. Blinded, Restrained, Stunned → advantage; Invisible → disadvantage). */
    private String incomingAttack;
    /** Abilities on which the bearer has disadvantage on saving throws (e.g. Restrained → dexterity). */
    private List<String> saveDisadvantage = new ArrayList<>();
    /** Situational notes the game can't cleanly auto-apply — shown to remind the DM/roller. */
    private List<String> reminders = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getRules() { return rules; }

    /** The rules as bulleted lines wrapped to {@code width}, for a hover or a tooltip. */
    public String rulesText(int width) {
        StringBuilder sb = new StringBuilder();
        for (String r : rules) {
            boolean first = true;
            for (String part : io.papermc.jkvttplugin.util.Util.wrapText(r, width)) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(first ? "• " : "  ").append(part);
                first = false;
            }
        }
        return sb.toString();
    }
    public void setRules(List<String> rules) { this.rules = rules != null ? rules : new ArrayList<>(); }

    public boolean isUntilNextTurn() { return untilNextTurn; }
    public void setUntilNextTurn(boolean untilNextTurn) { this.untilNextTurn = untilNextTurn; }

    public String getMinecraftEffect() { return minecraftEffect; }
    public void setMinecraftEffect(String minecraftEffect) { this.minecraftEffect = minecraftEffect; }

    public int getMinecraftEffectAmplifier() { return minecraftEffectAmplifier; }
    public void setMinecraftEffectAmplifier(int amplifier) { this.minecraftEffectAmplifier = amplifier; }

    public boolean isNoMovement() { return noMovement; }
    public void setNoMovement(boolean noMovement) { this.noMovement = noMovement; }

    public boolean isNoActions() { return noActions; }
    public void setNoActions(boolean noActions) { this.noActions = noActions; }

    public String getSelfAttack() { return selfAttack; }
    public void setSelfAttack(String selfAttack) { this.selfAttack = selfAttack; }

    public String getSelfCheck() { return selfCheck; }
    public void setSelfCheck(String selfCheck) { this.selfCheck = selfCheck; }

    public String getIncomingAttack() { return incomingAttack; }
    public void setIncomingAttack(String incomingAttack) { this.incomingAttack = incomingAttack; }

    public List<String> getSaveDisadvantage() { return saveDisadvantage; }
    public void setSaveDisadvantage(List<String> saveDisadvantage) { this.saveDisadvantage = saveDisadvantage != null ? saveDisadvantage : new ArrayList<>(); }

    public List<String> getReminders() { return reminders; }
    public void setReminders(List<String> reminders) { this.reminders = reminders != null ? reminders : new ArrayList<>(); }
}
