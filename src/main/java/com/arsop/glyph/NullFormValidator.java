package com.arsop.glyph;

import com.arsop.glyph.effect.EffectNull;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.validation.BaseSpellValidationError;
import com.hollingsworth.arsnouveau.common.spell.validation.ScanningSpellValidator;

import java.util.List;

/** Spell book validation: Null can't be used with the Self form. */
public class NullFormValidator extends ScanningSpellValidator<boolean[]> {
    public static final String CODE = "ars_op_null_self";

    @Override
    protected boolean[] initContext() {
        return new boolean[1]; // [0] = spell uses Self
    }

    @Override
    protected void digestSpellPart(boolean[] self, int position, AbstractSpellPart part, List<SpellValidationError> errors) {
        if (part instanceof MethodSelf) self[0] = true;
        if (self[0] && part == EffectNull.INSTANCE) {
            errors.add(new BaseSpellValidationError(position, part, CODE));
        }
    }

    public static boolean isSelfNull(List<AbstractSpellPart> parts) {
        return !parts.isEmpty() && parts.get(0) instanceof MethodSelf && parts.contains(EffectNull.INSTANCE);
    }
}
