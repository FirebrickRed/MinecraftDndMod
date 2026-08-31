package io.papermc.jkvttplugin.effect;

/**
 * An authored class/subclass/racial feature (the Effect Engine, #70). A thin container: how it's
 * activated, what it costs, and what it does. For now the "does" is an {@code apply} block — a buff
 * ({@link ActiveEffect} template) placed on the target. The {@code action} path (AoE/save/damage,
 * e.g. a breath weapon) is a later slice.
 *
 * <p>Purely data — parsed from a class's {@code features:} YAML by {@link FeatureParser}. The code
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

    public Feature(String id, String name, String activation, String target,
                   String costResource, int costAmount, ActiveEffect applyTemplate) {
        this.id = id;
        this.name = name;
        this.activation = activation;
        this.target = target == null ? "self" : target.toLowerCase();
        this.costResource = costResource;
        this.costAmount = Math.max(1, costAmount);
        this.applyTemplate = applyTemplate;
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
}
