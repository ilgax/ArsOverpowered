package com.arsop.glyph.augment;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.spell.validation.BaseSpellValidationError;
import com.hollingsworth.arsnouveau.common.spell.validation.ScanningSpellValidator;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spell book validation: a Greater augment counts as its copies. Adding one (or a plain augment after one) is
 * blocked once the glyph already has its maximum of that augment.
 */
public class GreaterLimitValidator extends ScanningSpellValidator<GreaterLimitValidator.Ctx> {
    public static final String CODE = "ars_op_greater_limit";

    public static final class Ctx {
        AbstractSpellPart owner;
        final Map<AbstractAugment, Integer> counts = new HashMap<>();
    }

    @Override
    protected Ctx initContext() {
        return new Ctx();
    }

    @Override
    protected void digestSpellPart(Ctx ctx, int position, AbstractSpellPart part, List<SpellValidationError> errors) {
        if (!(part instanceof AbstractAugment augment)) {
            ctx.owner = part;
            ctx.counts.clear();
            return;
        }
        AbstractAugment base = augment instanceof AugmentGreater g ? g.base : augment;
        int adds = augment instanceof AugmentGreater g ? g.copies() : 1;
        int have = ctx.counts.getOrDefault(base, 0);
        if (ctx.owner != null) {
            int limit = ctx.owner.getAugmentLimit(base.getRegistryName());
            boolean involvesGreater = augment instanceof AugmentGreater || ctx.counts.containsKey(GREATER_MARK);
            if (involvesGreater && have >= limit) {
                errors.add(new BaseSpellValidationError(position, part, CODE,
                        Component.translatable(base.getLocaleName()), Component.literal(String.valueOf(limit))));
            }
        }
        ctx.counts.put(base, have + adds);
        if (augment instanceof AugmentGreater) ctx.counts.put(GREATER_MARK, 1);
    }

    /** Marks that a Greater augment was used on the current glyph (so plain ones after it are checked too). */
    private static final AbstractAugment GREATER_MARK = GreaterAugments.GREATER_AOE;
}
