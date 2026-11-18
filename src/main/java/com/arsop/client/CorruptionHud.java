package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.corruption.Corruption;
import com.arsop.item.CodexOfRuin;
import com.hollingsworth.arsnouveau.ArsNouveau;
import com.hollingsworth.arsnouveau.setup.config.Config;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Corruption bar, drawn in Ars's mana bar frame just above the mana bar, while holding the Codex or dream-walking.
 */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public final class CorruptionHud {
    private CorruptionHud() {}

    private static final ResourceLocation BORDER = ArsNouveau.prefix("textures/gui/manabar_gui_border.png");
    /** Gap between Ars's mana bar and this one. */
    private static final int LIFT = 20;
    private static final int WIDTH = 96;

    public static int value;

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ArsOp.prefix("corruption_bar"), CorruptionHud::render);
    }

    private static boolean shouldShow(Minecraft mc) {
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return false;
        if (CodexOfRuin.isCreativeCodex(mc.player.getMainHandItem()) || CodexOfRuin.isCreativeCodex(mc.player.getOffhandItem())) return false;
        return CodexOfRuin.isHolding(mc.player) || PossessionClient.isActive();
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldShow(mc)) return;

        int x = 10 + Config.MANABAR_X_OFFSET.get();
        int y = mc.getWindow().getGuiScaledHeight() - 5 + Config.MANABAR_Y_OFFSET.get() - LIFT;
        float fraction = Mth.clamp(value / (float) Corruption.MAX, 0f, 1f);
        int filled = (int) (WIDTH * fraction);

        g.blit(BORDER, x, y - 18, 0, 0, 108, 18, 256, 256);
        g.fill(x + 8, y - 10, x + 106, y - 2, 0xFF000000); // black trough instead of Ars's purple

        int top = y - 9, bottom = y - 3;
        if (filled > 0) {
            // Blood red that darkens toward black as it fills; past 75 it throbs.
            float pulse = value >= Corruption.DARKNESS
                    ? 0.75f + 0.25f * Mth.sin((mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.25f)
                    : 1f;
            int bright = color(0.75f * pulse, 0.05f, 0.08f);
            int deep = color(0.30f * pulse, 0.0f, 0.04f);
            g.fillGradient(x + 9, top, x + 9 + filled, bottom, bright, deep);
            g.fill(x + 9, top, x + 9 + filled, top + 1, color(0.95f * pulse, 0.25f, 0.25f));
        }
        g.blit(BORDER, x, y - 17, 0, 18, 108, 20, 256, 256);

        String label = value + "%";
        g.drawString(mc.font, label, x + 9 + WIDTH / 2 - mc.font.width(label) / 2, y - 18 + 1, 0xFFE0B0B0, true);
    }

    private static int color(float r, float g, float b) {
        return 0xFF000000 | ((int) (Mth.clamp(r, 0, 1) * 255) << 16) | ((int) (Mth.clamp(g, 0, 1) * 255) << 8) | (int) (Mth.clamp(b, 0, 1) * 255);
    }
}