package com.arsop.corruption;

import com.arsop.ArsOp;
import com.arsop.glyph.effect.EffectAnnihilate;
import com.arsop.glyph.effect.EffectPossess;
import com.arsop.item.CodexOfRuin;
import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Forbidden glyphs need the Codex; Codex casts feed corruption and hit harder. */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class CodexEvents {
    private CodexEvents() {}

    public static final int CAST_CORRUPTION = 2;
    public static final int FORBIDDEN_CORRUPTION = 8;

    public static boolean hasForbidden(Spell spell) {
        if (spell == null) return false;
        var parts = spell.unsafeList();
        return parts.contains(EffectAnnihilate.INSTANCE) || parts.contains(EffectPossess.INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCastGate(SpellCastEvent event) {
        if (!hasForbidden(event.spell)) return;
        if (event.context != null && CodexOfRuin.isCodex(event.context.getCasterTool())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            player.displayClientMessage(Component.translatable("ars_op.codex.forbidden").withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /** Lowest priority and not receiving canceled events: only casts that really happen add corruption. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCastCorrupt(SpellCastEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.context == null || !CodexOfRuin.isCodex(event.context.getCasterTool())) return;
        if (CodexOfRuin.isCreativeCodex(event.context.getCasterTool())) return;
        Corruption.add(player, CAST_CORRUPTION + (hasForbidden(event.spell) ? FORBIDDEN_CORRUPTION : 0));
    }

    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent.Pre event) {
        if (event.context == null || !CodexOfRuin.isCodex(event.context.getCasterTool())) return;
        if (!(event.caster instanceof Player player)) return;
        event.damage *= CodexOfRuin.isCreativeCodex(event.context.getCasterTool())
                ? Corruption.MAX_POWER : Corruption.powerMultiplier(player);
    }
}