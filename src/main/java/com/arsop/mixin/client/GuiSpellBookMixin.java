package com.arsop.mixin.client;

import com.arsop.item.CodexOfRuin;
import com.hollingsworth.arsnouveau.client.gui.book.BaseBook;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.hollingsworth.arsnouveau.client.gui.buttons.CraftingButton;
import com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Codex of Ruin spell screen: 20 glyph cells as two rows of 10 (instead of Ars's scrolling single row). Row two sits
 * on an extra strip of page under row one; the spell reads row one then row two.
 */
@Mixin(value = GuiSpellBook.class, remap = false)
public abstract class GuiSpellBookMixin {
    private static final int EXTRA_HEIGHT = CodexOfRuin.BOOK_EXTRA_HEIGHT;
    private static final int PER_ROW = 10;
    /**
     * Ars's page texture: rows 107-130 are plain page (no corner curls), rows 172-187 the bottom binding. The spine
     * is dashed every 12 rows, so plain page is copied in 24-row steps that keep the dashes lined up.
     */
    private static final int PLAIN_V = 107;
    private static final int PLAIN_H = 24;
    private static final int CURL_TOP = 131;
    private static final int BINDING_V = 172;
    private static final int BINDING_H = 16;

    private GuiSpellBook arsop$self() {
        return (GuiSpellBook) (Object) this;
    }

    private boolean arsop$isCodex() {
        return CodexOfRuin.isCodex(arsop$self().bookStack);
    }

    /** Lay all cells out as rows of 10, row one where Ars puts its row, row two under it. */
    @Inject(method = "resetCraftingCells", at = @At("TAIL"))
    private void arsop$twoRows(CallbackInfo ci) {
        if (!arsop$isCodex()) return;
        GuiSpellBook gui = arsop$self();
        gui.spellWindowOffset = 0;
        int firstRowY = gui.bookBottom - 43 - EXTRA_HEIGHT;
        for (int i = 0; i < gui.craftingCells.size(); i++) {
            CraftingButton cell = gui.craftingCells.get(i);
            int col = i % PER_ROW;
            int row = i / PER_ROW;
            cell.setX(gui.bookLeft + 19 + 24 * col + (col >= 5 ? 14 : 0));
            cell.setY(firstRowY + EXTRA_HEIGHT * row);
            cell.visible = true;
        }
        arsop$hideArrows();
    }

    @Shadow
    private void validate() {
        throw new AssertionError();
    }

    /**
     * Ars only fills empty cells inside its 10-cell scroll window, which would leave row two unreachable.
     * For the Codex, a glyph click fills the first empty cell of all 20.
     */
    @Inject(method = "onGlyphClick", at = @At("HEAD"), cancellable = true)
    private void arsop$fillAnyCell(Button button, CallbackInfo ci) {
        if (!arsop$isCodex() || !(button instanceof GlyphButton glyph)) return;
        ci.cancel();
        if (!glyph.validationErrors.isEmpty()) return;
        GuiSpellBook gui = arsop$self();
        for (CraftingButton cell : gui.craftingCells) {
            if (cell.getAbstractSpellPart() != null) continue;
            cell.setAbstractSpellPart(glyph.abstractSpellPart);
            if (cell.slotNum >= gui.spell.size()) gui.spell.add(glyph.abstractSpellPart);
            else gui.spell.set(cell.slotNum, glyph.abstractSpellPart);
            break;
        }
        validate();
    }

    /** Nothing to scroll: every cell is on screen. */
    @ModifyVariable(method = "updateWindowOffset", at = @At("HEAD"), argsOnly = true)
    private int arsop$noScroll(int offset) {
        return arsop$isCodex() ? 0 : offset;
    }

    @Inject(method = "updateWindowOffset", at = @At("TAIL"))
    private void arsop$noScrollArrows(int offset, CallbackInfo ci) {
        if (arsop$isCodex()) arsop$hideArrows();
    }

    @Inject(method = "updateNextGlyphArrow", at = @At("TAIL"))
    private void arsop$noNextArrow(CallbackInfo ci) {
        if (arsop$isCodex()) arsop$hideArrows();
    }

    private void arsop$hideArrows() {
        GuiSpellBook gui = arsop$self();
        if (gui.nextGlyphButton != null) {
            gui.nextGlyphButton.visible = false;
            gui.nextGlyphButton.active = false;
        }
        if (gui.prevGlyphButton != null) {
            gui.prevGlyphButton.visible = false;
            gui.prevGlyphButton.active = false;
        }
    }

    /** Repaint the page bottom as plain page (dropping the corner curls), one row taller, then the binding below it. */
    @Inject(method = "drawBackgroundElements", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lcom/hollingsworth/arsnouveau/client/gui/book/SpellSlottedScreen;drawBackgroundElements(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void arsop$extendPage(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (!arsop$isCodex()) return;
        int pageEnd = BINDING_V + EXTRA_HEIGHT;
        for (int y = CURL_TOP; y < pageEnd; y += PLAIN_H) {
            g.blit(BaseBook.background, 0, y, 0, PLAIN_V, BaseBook.FULL_WIDTH, Math.min(PLAIN_H, pageEnd - y), BaseBook.FULL_WIDTH, BaseBook.FULL_HEIGHT);
        }
        g.blit(BaseBook.background, 0, BINDING_V + EXTRA_HEIGHT, 0, BINDING_V, BaseBook.FULL_WIDTH, BINDING_H, BaseBook.FULL_WIDTH, BaseBook.FULL_HEIGHT);
    }

    /** Ars draws the mana bar at a fixed spot under the book; push it below the taller book. */
    @Inject(method = "drawBackgroundElements", at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
    private void arsop$lowerManaBar(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (arsop$isCodex()) g.pose().translate(0, EXTRA_HEIGHT, 0);
    }
}