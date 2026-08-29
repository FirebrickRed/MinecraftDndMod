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

    // Set when an attack HITS; consumed by /combat damage so damage can only be applied
    // once per hit (no /combat damage spamming). Cleared at the start of each turn.
    private UUID pendingDamageTargetId;
    // The flat damage modifier for the pending hit (e.g. +3 STR). When the player supplies their
    // physically-rolled damage dice via --roll <n>, the game adds this — mirroring attack rolls.
    private int pendingDamageBonus;

    private double movementUsed;      // feet moved this turn
    private final int movementBudget; // max feet (from speed)
    private boolean movementWarned;   // whether we've already warned about exceeding movement

    private final Location turnStartLocation;  // for /combat movement undo

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

    public boolean isActionUsed() { return actionUsed; }
    public boolean isBonusActionUsed() { return bonusActionUsed; }
    public boolean isReactionUsed() { return reactionUsed; }

    // ==================== PENDING DAMAGE (one damage application per hit) ====================

    /** Record that an attack hit {@code target}, opening a single /combat damage window. */
    public void markAttackHit(UUID targetId) { markAttackHit(targetId, 0); }
    /** As above, remembering the flat damage bonus the game should add to a rolled damage die. */
    public void markAttackHit(UUID targetId, int damageBonus) {
        this.pendingDamageTargetId = targetId;
        this.pendingDamageBonus = damageBonus;
    }
    public boolean isDamagePending() { return pendingDamageTargetId != null; }
    public UUID getPendingDamageTargetId() { return pendingDamageTargetId; }
    public int getPendingDamageBonus() { return pendingDamageBonus; }
    public void clearDamagePending() { this.pendingDamageTargetId = null; this.pendingDamageBonus = 0; }

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
    public int getMovementBudget() { return movementBudget; }

    public double getMovementRemaining() {
        return Math.max(0, movementBudget - movementUsed);
    }

    public boolean isOverMovementBudget() {
        return movementUsed > movementBudget;
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
