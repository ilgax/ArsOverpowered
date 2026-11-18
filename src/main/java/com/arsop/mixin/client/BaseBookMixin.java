package com.arsop.mixin.client;

import com.arsop.item.CodexOfRuin;
import com.hollingsworth.arsnouveau.client.gui.book.BaseBook;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Codex's spell screen is taller by one glyph row, to fit a second row of 10 glyph cells. */
@Mixin(value = BaseBook.class, remap = false)
public abstract class BaseBookMixin {
    @Shadow public int bookTop;
    @Shadow public int bookBottom;

    @Inject(method = "init", at = @At("RETURN"))
    private void arsop$tallCodex(CallbackInfo ci) {
        if ((Object) this instanceof GuiSpellBook gui && CodexOfRuin.isCodex(gui.bookStack)) {
            bookTop -= CodexOfRuin.BOOK_EXTRA_HEIGHT / 2;
            bookBottom += CodexOfRuin.BOOK_EXTRA_HEIGHT - CodexOfRuin.BOOK_EXTRA_HEIGHT / 2;
            // 10 extra glyph cells; GuiSpellBook.init builds its cells right after this.
            if (gui.bonusSlots < CodexOfRuin.BONUS_GLYPH_SLOTS) gui.bonusSlots = CodexOfRuin.BONUS_GLYPH_SLOTS;
        }
    }
}