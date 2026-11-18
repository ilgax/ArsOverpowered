package com.arsop.mixin;

import com.arsop.glyph.augment.GreaterAugments;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every spell that gets cast goes through a SpellContext. Expanding Greater augments here means validation,
 * mana cost, forms and effects all see plain augments.
 */
@Mixin(value = SpellContext.class, remap = false)
public abstract class SpellContextMixin {
    @Shadow
    private Spell spell;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ars_op$expandGreater(CallbackInfo ci) {
        this.spell = GreaterAugments.expand(this.spell);
    }

    @ModifyVariable(method = "withSpell", at = @At("HEAD"), argsOnly = true)
    private Spell ars_op$expandGreaterWith(Spell spell) {
        return GreaterAugments.expand(spell);
    }
}
