package com.arsop.glyph.augment;

import com.arsop.ArsOp;
import com.arsop.glyph.NullFormValidator;
import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.common.spell.validation.CombinedSpellValidator;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAccelerate;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSplit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The Greater augments, their expansion into plain augments, and wiring them into every compatible glyph. */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class GreaterAugments {
    private GreaterAugments() {}

    public static final AugmentGreater GREATER_AOE = new AugmentGreater("glyph_greater_aoe", "Greater AOE", AugmentAOE.INSTANCE, "AOE");
    public static final AugmentGreater GREATER_SPLIT = new AugmentGreater("glyph_greater_split", "Greater Split", AugmentSplit.INSTANCE, "Split");
    public static final AugmentGreater GREATER_AMPLIFY = new AugmentGreater("glyph_greater_amplify", "Greater Amplify", AugmentAmplify.INSTANCE, "Amplify");
    public static final AugmentGreater GREATER_EXTEND_TIME = new AugmentGreater("glyph_greater_extend_time", "Greater Extend Time", AugmentExtendTime.INSTANCE, "Extend Time");
    public static final AugmentGreater GREATER_ACCELERATE = new AugmentGreater("glyph_greater_accelerate", "Greater Accelerate", AugmentAccelerate.INSTANCE, "Accelerate");

    public static final List<AugmentGreater> ALL = List.of(GREATER_AOE, GREATER_SPLIT, GREATER_AMPLIFY, GREATER_EXTEND_TIME, GREATER_ACCELERATE);

    public static void register() {
        for (AugmentGreater g : ALL) GlyphRegistry.registerSpell(g);
    }

    /** Every glyph/form that accepts the base augment also accepts its Greater version. */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (AbstractSpellPart part : GlyphRegistry.getSpellpartMap().values()) {
                if (part instanceof AbstractAugment || part.compatibleAugments == null) continue;
                Set<AbstractAugment> added = null;
                for (AugmentGreater g : ALL) {
                    if (part.compatibleAugments.contains(g.base) && !part.compatibleAugments.contains(g)) {
                        if (added == null) added = new HashSet<>(part.compatibleAugments);
                        added.add(g);
                    }
                }
                if (added != null) part.compatibleAugments = added;
            }
            addCraftingValidator();
        });
    }

    /** Hooks Ars OP's checks (Greater limits, no Self + Null) into the spell book's crafting validation. */
    private static void addCraftingValidator() {
        ArsNouveauAPI api = ArsNouveauAPI.getInstance();
        ISpellValidator ours = new CombinedSpellValidator(new GreaterLimitValidator(), new NullFormValidator());
        ISpellValidator current = api.getSpellCraftingSpellValidator();
        if (current instanceof CombinedSpellValidator combined) {
            try {
                combined.validators.add(ours);
                return;
            } catch (UnsupportedOperationException ignored) {
                // immutable list: wrap instead
            }
        }
        try {
            var field = ArsNouveauAPI.class.getDeclaredField("craftingSpellValidator");
            field.setAccessible(true);
            field.set(api, new CombinedSpellValidator(current, ours));
        } catch (ReflectiveOperationException e) {
            ArsOp.LOGGER.error("Could not add the Greater augment limit check to spell book validation", e);
        }
    }

    /**
     * Replaces each Greater augment with copies of its base augment, never letting a glyph exceed its own limit
     * for that augment. Returns the same spell when there is nothing to expand.
     */
    public static Spell expand(Spell spell) {
        if (spell == null) return null;
        List<AbstractSpellPart> in = spell.unsafeList();
        boolean any = false;
        for (AbstractSpellPart p : in) {
            if (p instanceof AugmentGreater) {
                any = true;
                break;
            }
        }
        if (!any) return spell;

        List<AbstractSpellPart> out = new ArrayList<>(in.size() + 8);
        AbstractSpellPart owner = null;
        Map<AbstractAugment, Integer> counts = new HashMap<>();
        for (AbstractSpellPart p : in) {
            if (p instanceof AugmentGreater g) {
                int limit = owner == null ? Integer.MAX_VALUE : owner.getAugmentLimit(g.base.getRegistryName());
                for (int i = 0; i < g.copies(); i++) {
                    int have = counts.getOrDefault(g.base, 0);
                    if (have >= limit) break;
                    out.add(g.base);
                    counts.put(g.base, have + 1);
                }
            } else if (p instanceof AbstractAugment a) {
                counts.merge(a, 1, Integer::sum);
                out.add(a);
            } else {
                owner = p;
                counts.clear();
                out.add(p);
            }
        }
        return spell.setRecipe(out);
    }
}
