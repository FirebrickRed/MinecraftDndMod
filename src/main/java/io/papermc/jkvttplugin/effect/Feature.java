package io.papermc.jkvttplugin.effect;

/**
 * An authored class/subclass/racial feature (the Effect Engine, #70). A thin container: how it's
 * activated, what it costs, and what it does. The "does" is one of two composable blocks:
 * an {@code apply} buff ({@link ActiveEffect} template placed on the target, e.g. Rage) and/or an
 * {@code action} ({@link FeatureAction}: an area/save/damage projection, e.g. a breath weapon).
 *
 * <p>A feature may also declare the pool it spends from: when its {@code cost} block carries a
 * {@code max}/{@code recovery}, the character materializes that resource (e.g. a breath weapon's
 * proficiency-bonus uses, recovered on a long rest). This lets a race grant its own limited-use
 * feature entirely in YAML.
 *
 * <p>Purely data — parsed from a {@code features:} YAML block by {@link FeatureParser}. The code
 * never hardcodes a specific feature.
 */
public class Feature {

    private final String id;
    private final String name;
    private final String activation;    // "action" | "bonus_action" | "reaction" | null (passive)
    private final String target;        // "self" (default) | "other_creature"
    private final String costResource;  // class/racial resource id spent to use it, or null
    private final int costAmount;
    private final ActiveEffect applyTemplate; // the buff to apply, or null
    private final FeatureAction action;       // the area/save/damage projection, or null

    // Optional resource pool this feature grants (materialized on the character). -1 max = none.
    private final int grantedResourceMax;         // fixed max, or -1 to use the prof-bonus flag
    private final boolean grantedResourceByProf;  // max = proficiency bonus
    private final String grantedResourceRecovery; // "long_rest" | "short_rest" | "dawn" | null

    /** Legacy 7-arg constructor (apply-only features such as Rage). */
    public Feature(String id, String name, String activation, String target,
                   String costResource, int costAmount, ActiveEffect applyTemplate) {
        this(id, name, activation, target, costResource, costAmount, applyTemplate,
                null, -1, false, null);
    }

    public Feature(String id, String name, String activation, String target,
                   String costResource, int costAmount, ActiveEffect applyTemplate,
                   FeatureAction action, int grantedResourceMax, boolean grantedResourceByProf,
                   String grantedResourceRecovery) {
        this.id = id;
        this.name = name;
        this.activation = activation;
        this.target = target == null ? "self" : target.toLowerCase();
        this.costResource = costResource;
        this.costAmount = Math.max(1, costAmount);
        this.applyTemplate = applyTemplate;
        this.action = action;
        this.grantedResourceMax = grantedResourceMax;
        this.grantedResourceByProf = grantedResourceByProf;
        this.grantedResourceRecovery = grantedResourceRecovery;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getActivation() { return activation; }
    public String getTarget() { return target; }
    public boolean targetsSelf() { return !"other_creature".equals(target); }
    public String getCostResource() { return costResource; }
    public int getCostAmount() { return costAmount; }
    public ActiveEffect getApplyTemplate() { return applyTemplate; }
    public boolean hasApply() { return applyTemplate != null; }
    public FeatureAction getAction() { return action; }
    public boolean hasAction() { return action != null; }

    /** True if this feature also defines the resource pool it draws from (to be materialized). */
    public boolean grantsResource() {
        return costResource != null && (grantedResourceByProf || grantedResourceMax > 0);
    }
    public int getGrantedResourceMax() { return grantedResourceMax; }
    public boolean isGrantedResourceByProf() { return grantedResourceByProf; }
    public String getGrantedResourceRecovery() { return grantedResourceRecovery; }
}
