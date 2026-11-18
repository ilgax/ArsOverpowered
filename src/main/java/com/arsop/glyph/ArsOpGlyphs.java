package com.arsop.glyph;

import com.arsop.glyph.augment.GreaterAugments;
import com.arsop.glyph.effect.EffectAnnihilate;
import com.arsop.glyph.effect.EffectFall;
import com.arsop.glyph.effect.EffectNull;
import com.arsop.glyph.effect.EffectPossess;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;

public final class ArsOpGlyphs {
    private ArsOpGlyphs() {}

    public static void register() {
        GlyphRegistry.registerSpell(EffectAnnihilate.INSTANCE);
        GlyphRegistry.registerSpell(EffectFall.INSTANCE);
        GlyphRegistry.registerSpell(EffectNull.INSTANCE);
        GlyphRegistry.registerSpell(EffectPossess.INSTANCE);
        GreaterAugments.register();
    }
}
