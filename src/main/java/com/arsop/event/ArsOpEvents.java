package com.arsop.event;

import com.arsop.effect.FallenEffect;
import com.arsop.glyph.effect.EffectFall;
import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;

public final class ArsOpEvents {
    private ArsOpEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ArsOpEvents::onHeal);
        NeoForge.EVENT_BUS.addListener(ArsOpEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ArsOpEvents::onSpellCost);
        NeoForge.EVENT_BUS.addListener(ArsOpEvents::onSpellCast);
    }

    /** Fallen: nothing can heal you. */
    private static void onHeal(LivingHealEvent event) {
        if (FallenEffect.isFallen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Fallen II attackers deal no damage at all (melee, arrows, explosions: anything whose
     * source entity is them). Fallen I attackers deal reduced damage.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (FallenEffect.isPinned(attacker)) {
            event.setCanceled(true);
        } else if (FallenEffect.isFallen(attacker)) {
            var mult = EffectFall.INSTANCE.CRUSHED_DAMAGE_MULT;
            event.setAmount(event.getAmount() * (float) (mult == null ? 0.4 : mult.get()));
        }
    }

    /**
     * Fall drains a share of the caster's current mana: 80%, +5% per Extend Time or AOE on Fall (max 4 = 100%).
     * Never cheaper than the spell's normal cost. Ars uses this value both to check and to spend mana.
     */
    private static void onSpellCost(SpellCostCalcEvent.Post event) {
        int fallIndex = fallIndex(event.context.getSpell());
        if (fallIndex < 0) return;
        LivingEntity caster = event.context.getUnwrappedCaster();
        IManaCap mana = CapabilityRegistry.getMana(caster);
        if (mana == null) return;
        double fraction = EffectFall.manaFraction(countUpgrades(event.context.getSpell(), fallIndex));
        int drain = (int) Math.ceil(mana.getCurrentMana() * fraction);
        event.currentCost = Math.max(event.currentCost, drain);
    }

    /**
     * Fall casting rules: highest-tier spell books only (Archmage, Creative); at most 4 Extend Time + AOE
     * combined; and the caster must hold at least 80% of their max mana (creative players exempt, as in Ars).
     */
    private static void onSpellCast(SpellCastEvent event) {
        int fallIndex = fallIndex(event.spell);
        if (fallIndex < 0) return;
        ItemStack tool = event.context.getCasterTool();
        boolean topTierBook = tool.getItem() instanceof SpellBook book && book.getTier().value >= SpellTier.THREE.value;
        if (!topTierBook) {
            deny(event, Component.translatable("ars_op.fall.needs_archmage"));
            return;
        }
        if (countUpgrades(event.spell, fallIndex) > EffectFall.MAX_UPGRADES) {
            deny(event, Component.translatable("ars_op.fall.too_many_upgrades", EffectFall.MAX_UPGRADES));
            return;
        }
        LivingEntity caster = event.context.getUnwrappedCaster();
        if (caster instanceof Player p && p.isCreative()) return;
        IManaCap mana = CapabilityRegistry.getMana(caster);
        if (mana != null && mana.getCurrentMana() < mana.getMaxMana() * EffectFall.MIN_MANA_TO_CAST) {
            deny(event, Component.translatable("ars_op.fall.needs_mana", (int) Math.round(EffectFall.MIN_MANA_TO_CAST * 100)));
        }
    }

    private static void deny(SpellCastEvent event, Component message) {
        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    private static int fallIndex(Spell spell) {
        return spell == null ? -1 : spell.unsafeList().indexOf(EffectFall.INSTANCE);
    }

    /** Extend Time + AOE augments directly following Fall (augments apply to the effect before them). */
    private static int countUpgrades(Spell spell, int fallIndex) {
        List<AbstractSpellPart> parts = spell.unsafeList();
        int count = 0;
        for (int i = fallIndex + 1; i < parts.size() && parts.get(i) instanceof AbstractAugment; i++) {
            if (parts.get(i) == AugmentExtendTime.INSTANCE || parts.get(i) == AugmentAOE.INSTANCE) count++;
        }
        return count;
    }
}
