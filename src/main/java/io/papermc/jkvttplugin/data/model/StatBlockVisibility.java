package io.papermc.jkvttplugin.data.model;

/**
 * Controls what information players can see when viewing an entity's stat block.
 * DMs always see everything.
 */
public class StatBlockVisibility {

    /**
     * Whether players can see this entity's stat block at all.
     * If false, only DMs can view it.
     * Default: true
     */
    private boolean playersCanSee = true;

    /**
     * Whether to show current HP to players.
     * Even if true, max HP is always visible in stat blocks.
     * Default: false (hide current HP from players for mystery/challenge)
     */
    private boolean showHp = false;

    /**
     * Whether to show attack stats (to-hit, damage) to players.
     * Default: true
     */
    private boolean showAttacks = true;

    /**
     * Whether to show ability scores to players.
     * Default: true
     */
    private boolean showAbilities = true;

    public StatBlockVisibility() {
        // Use defaults
    }

    // ==================== GETTERS & SETTERS ====================

    public boolean isPlayersCanSee() {
        return playersCanSee;
    }

    public void setPlayersCanSee(boolean playersCanSee) {
        this.playersCanSee = playersCanSee;
    }

    public boolean isShowHp() {
        return showHp;
    }

    public void setShowHp(boolean showHp) {
        this.showHp = showHp;
    }

    public boolean isShowAttacks() {
        return showAttacks;
    }

    public void setShowAttacks(boolean showAttacks) {
        this.showAttacks = showAttacks;
    }

    public boolean isShowAbilities() {
        return showAbilities;
    }

    public void setShowAbilities(boolean showAbilities) {
        this.showAbilities = showAbilities;
    }
}