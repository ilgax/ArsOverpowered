package com.arsop.client;

import com.arsop.ArsOp;
import com.hollingsworth.arsnouveau.client.renderer.item.SpellBookRenderer;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import net.minecraft.resources.ResourceLocation;

/** Ars's spellbook model with the Codex's own dark red cover. */
public class CodexRenderer extends SpellBookRenderer {
    public static final ResourceLocation TEXTURE = ArsOp.prefix("textures/item/codex_of_ruin.png");

    @Override
    public ResourceLocation getTextureLocation(SpellBook book) {
        return TEXTURE;
    }
}