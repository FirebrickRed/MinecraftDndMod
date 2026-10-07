package io.papermc.jkvttplugin.combat;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Tracks per-turn state for a combatant during their active turn.
 * Created fresh at the start of each turn, discarded when the turn ends.
 *
 * Issue #98 - Turn Management & Action Economy
 */
public class TurnState {

    private boolean actionUsed;
    private boolean bonusActionUsed;
    private boolean reactionUsed;
    private boolean attackActionTaken; // took the Attack action (not just any action): unlocks a bonus attack
    private int attacksLeftInAction;   // more attacks the Attack action still allows (Extra Attack, #153)

    // Set when an attack HITS; consumed by /combat damage so damage can only be applied
    // once per hit (no /combat damage spamming). Cleared at the start of each turn.
    private UUID pendingDamageTargetId;
    // The flat damage modifier for the pending hit (e.g. +3 STR). When the player supplies their
    // physically-rolled damage dice via manualRoll <n>, the game adds this — mirroring attack rolls.
    private int pendingDamageBonus;
    private String pendingDamageLabel = ""; // labeled breakdown of the bonus, e.g. "+5[STR] +2[Rage]" (#168)
    private String pendingDamageType = "";  // the hit's damage type, so /combat damage needs no 'type' (#183)
    private String pendingDamageDice = "";  // the hit's damage dice, so 'autoRoll' needs no dice typed (#183)
    // Whether the pending hit was a critical. Remembered from the attack so /combat damage applies
    // the "crit vs a downed creature = 2 death-save failures" rule without a user-facing flag.
    private boolean pendingDamageCrit;
    // The pending damage should be halved when applied (e.g. a successful save vs a save spell). (#123)
    private boolean pendingDamageHalf;
    // A reaction window opened on this hit (#195), so its damage goes to the DM for [Apply] (#175).
    private boolean pendingDamageAfterReaction;

    private double movementUsed;      // feet moved this turn
    private final int movementBudget; // max feet (from speed)
    private boolean dashed;           // Dash action taken → movement doubled (#143)
    private boolean movementWarned;   // whether we've already warned about exceeding movement

    private final Location turnStartLocation;  // for /combat movement undo

    // Gear the combatant started the turn with, and which reminders we've already given (#190).
    private String turnStartWeaponId;
    private String turnStartOffhandWeaponId;
    private String switchedToWeaponId; // a switch already settled this turn: confirmed, or the DM allowed it
    private boolean objectInteractionUsed;
    private boolean shieldChangeWarned;

    public TurnState(int speed, Location startLocation) {
        this.actionUsed = false;
        this.bonusActionUsed = false;
        this.reactionUsed = false;
        this.movementUsed = 0.0;
        this.movementBudget = speed;
        this.movementWarned = false;
        this.turnStartLocation = startLocation != null ? startLocation.clone() : null;
    }

    // ==================== ACTION ECONOMY ====================

    public void useAction() { actionUsed = true; }
    public void useBonusAction() { bonusActionUsed = true; }
    public void useReaction() { reactionUsed = true; }
    /**
     * The Action was spent on the Attack action, which gives {@code attacksPerAction} attacks (1 until
     * Extra Attack, #153); the first is being made now. Two-weapon fighting and Martial Arts build on it.
     */
    private String attackActionWith; // what the Attack action's first attack was made with: a weapon id, or "unarmed" (#259)

    public void markAttackAction(int attacksPerAction) { markAttackAction(attacksPerAction, "unarmed"); }

    /** @param with the weapon id the Attack action was made with, or "unarmed" */
    public void markAttackAction(int attacksPerAction, String with) {
        attackActionTaken = true;
        attackActionWith = with;
        attacksLeftInAction = Math.max(0, attacksPerAction - 1);
    }
    public int getAttacksLeftInAction() { return attacksLeftInAction; }

    // A creature's Multiattack this turn (#253): null until its first attack starts one.
    private io.papermc.jkvttplugin.data.model.Multiattack.Progress multiattack;
    public io.papermc.jkvttplugin.data.model.Multiattack.Progress getMultiattack() { return multiattack; }
    public void setMultiattack(io.papermc.jkvttplugin.data.model.Multiattack.Progress progress) { this.multiattack = progress; }
    /** One of the Attack action's further attacks is being made. */
    public void useExtraAttack() { if (attacksLeftInAction > 0) attacksLeftInAction--; }
    // The attack or spell that started this fight, owed on this (their first) turn (#152): its label and
    // the command that makes it. Until it's made or the DM releases it, the Action is spoken for.
    private String openingLabel, openingCommand;

    public void setOpening(String label, String command) { openingLabel = label; openingCommand = command; }
    public void clearOpening() { openingLabel = null; openingCommand = null; }
    public String getOpeningLabel() { return openingLabel; }
    public String getOpeningCommand() { return openingCommand; }

    public boolean isAttackActionTaken() { return attackActionTaken; }
    /** What the Attack action was made with (a weapon id or "unarmed"), or null before it's taken. */
    public String getAttackActionWith() { return attackActionTaken ? attackActionWith : null; }

    public boolean isActionUsed() { return actionUsed; }
    public boolean isBonusActionUsed() { return bonusActionUsed; }
    public boolean isReactionUsed() { return reactionUsed; }

    // ==================== GEAR CHANGES (#190) ====================

    /**
     * The weapons in hand when this turn began. Attacking with any other weapon is a switch, and
     * {@link WeaponSwitch} settles what it costs before the roll: drawing or stowing a weapon is your
     * one free object interaction per turn, a second one takes your Action. Strapping on a shield costs
     * an Action, and that one is only ever a reminder.
     */
    public String getTurnStartWeaponId() { return turnStartWeaponId; }
    public void setTurnStartWeaponId(String weaponId) { this.turnStartWeaponId = weaponId; }

    public boolean isObjectInteractionUsed() { return objectInteractionUsed; }
    public void markObjectInteractionUsed() { this.objectInteractionUsed = true; }

    public String getTurnStartOffhandWeaponId() { return turnStartOffhandWeaponId; }
    public void setTurnStartOffhandWeaponId(String weaponId) { this.turnStartOffhandWeaponId = weaponId; }

    public String getSwitchedToWeaponId() { return switchedToWeaponId; }
    public void setSwitchedToWeaponId(String weaponId) { this.switchedToWeaponId = weaponId; }

    public boolean isShieldChangeWarned() { return shieldChangeWarned; }
    public void markShieldChangeWarned() { this.shieldChangeWarned = true; }

    // ==================== PENDING DAMAGE (one damage application per hit) ====================

    /** Record that an attack hit {@code target}, opening a single /combat damage window. */
    public void markAttackHit(UUID targetId) { markAttackHit(targetId, 0, "", false); }
    /** As above, remembering the flat damage bonus, its labeled breakdown (#168), and crit. */
    public void markAttackHit(UUID targetId, int damageBonus, String damageBonusLabel, boolean crit) {
        this.pendingDamageTargetId = targetId;
        this.pendingDamageBonus = damageBonus;
        this.pendingDamageLabel = damageBonusLabel == null ? "" : damageBonusLabel;
        this.pendingDamageCrit = crit;
        this.pendingDamageAfterReaction = false;
        this.pendingSneak = null; // a new hit; its own Sneak Attack (if any) is set after this
    }

    // Sneak Attack that's in this hit's damage (#229): spent when the damage lands, not on the hit,
    // so a Shield that turns the hit into a miss doesn't use it up.
    private SneakAttack.Use pendingSneak;
    public SneakAttack.Use getPendingSneak() { return pendingSneak; }
    public void setPendingSneak(SneakAttack.Use use) { this.pendingSneak = use; }
    public void markReactionOnHit() { this.pendingDamageAfterReaction = true; }
    public boolean isPendingDamageAfterReaction() { return pendingDamageAfterReaction; }
    public boolean isDamagePending() { return pendingDamageTargetId != null; }
    public UUID getPendingDamageTargetId() { return pendingDamageTargetId; }
    public int getPendingDamageBonus() { return pendingDamageBonus; }
    public String getPendingDamageLabel() { return pendingDamageLabel; }
    public String getPendingDamageType() { return pendingDamageType; }
    public void setPendingDamageType(String type) { this.pendingDamageType = type == null ? "" : type; }
    public String getPendingDamageDice() { return pendingDamageDice; }
    public void setPendingDamageDice(String dice) { this.pendingDamageDice = dice == null ? "" : dice; }
    public boolean isPendingDamageCrit() { return pendingDamageCrit; }
    public boolean isPendingDamageHalf() { return pendingDamageHalf; }
    public void setPendingDamageHalf(boolean half) { this.pendingDamageHalf = half; }

    // Great Weapon Fighting (#229): the weapon attack being made says whether its hit rerolls 1s and
    // 2s; recording the hit takes that and resets it, so a spell's hit afterwards doesn't inherit it.
    private boolean rerollLowForNextHit;
    private boolean pendingDamageRerollLow;
    public void setRerollLowForNextHit(boolean reroll) { this.rerollLowForNextHit = reroll; }
    public boolean takeRerollLowForNextHit() { boolean r = rerollLowForNextHit; rerollLowForNextHit = false; return r; }
    public void setPendingDamageRerollLow(boolean reroll) { this.pendingDamageRerollLow = reroll; }
    public boolean isPendingDamageRerollLow() { return pendingDamageRerollLow; }

    public void clearDamagePending() {
        this.pendingDamageRerollLow = false;
        this.pendingSneak = null;
        this.pendingDamageTargetId = null;
        this.pendingDamageBonus = 0;
        this.pendingDamageLabel = "";
        this.pendingDamageType = "";
        this.pendingDamageDice = "";
        this.pendingDamageCrit = false;
        this.pendingDamageHalf = false;
        this.pendingDamageAfterReaction = false;
    }

    // ==================== MOVEMENT ====================

    public void addMovement(double feet) {
        movementUsed += feet;
    }

    public void setMovementUsed(double feet) {
        this.movementUsed = feet;
        // Clear warning if player walked back within budget
        if (!isOverMovementBudget()) {
            this.movementWarned = false;
        }
    }

    public double getMovementUsed() { return movementUsed; }

    /** Base speed for the turn. */
    public int getMovementBudget() { return movementBudget; }

    /** Speed available this turn, doubled while the Dash action is active (#143). */
    public int getEffectiveMovementBudget() { return dashed ? movementBudget * 2 : movementBudget; }

    /** The Dash action doubles this turn's movement. */
    public boolean isDashed() { return dashed; }
    public void setDashed(boolean dashed) { this.dashed = dashed; }

    public double getMovementRemaining() {
        return Math.max(0, getEffectiveMovementBudget() - movementUsed);
    }

    public boolean isOverMovementBudget() {
        return movementUsed > getEffectiveMovementBudget();
    }

    public boolean hasMovementWarned() { return movementWarned; }
    public void setMovementWarned(boolean warned) { this.movementWarned = warned; }

    /**
     * Reset movement used to 0 (for /combat movement undo).
     * The caller is responsible for teleporting the combatant back to turnStartLocation.
     */
    public void undoMovement() {
        movementUsed = 0.0;
        movementWarned = false;
    }

    public Location getTurnStartLocation() { return turnStartLocation; }
}
