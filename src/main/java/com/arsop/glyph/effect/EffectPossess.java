package com.arsop.glyph.effect;

import com.arsop.ArsOp;
import com.arsop.possess.Possession;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDurationDown;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Possess (forbidden): the caster dream-walks into the target mob and drives it. Castable only from the Codex of Ruin.
 */
public class EffectPossess extends AbstractEffect {
    public static final EffectPossess INSTANCE = new EffectPossess();

    public static final int BASE_TICKS = 400;
    public static final int TICKS_PER_EXTEND = 100;
    public static final int MIN_TICKS = 100;

    private EffectPossess() {
        super(ArsOp.prefix("glyph_possess"), "Possess");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level, @NotNull LivingEntity shooter, SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        if (level.isClientSide || !(shooter instanceof ServerPlayer player)) return;
        if (!(hit.getEntity() instanceof Mob mob) || !mob.isAlive()) return;
        int extend = spellStats.getBuffCount(AugmentExtendTime.INSTANCE);
        int shorten = spellStats.getBuffCount(AugmentDurationDown.INSTANCE);
        int ticks = Math.max(MIN_TICKS, BASE_TICKS + TICKS_PER_EXTEND * (extend - shorten));
        Possession.start(player, mob, ticks, !com.arsop.item.CodexOfRuin.isCreativeCodex(spellContext.getCasterTool()));
    }

    @Override
    public int getDefaultManaCost() {
        return 250;
    }

    @NotNull
    @Override
    public SpellTier defaultTier() {
        return SpellTier.THREE;
    }

    @NotNull
    @Override
    public Set<AbstractAugment> getCompatibleAugments() {
        return augmentSetOf(AugmentExtendTime.INSTANCE, AugmentDurationDown.INSTANCE);
    }

    @Override
    public void addAugmentDescriptions(Map<AbstractAugment, String> map) {
        super.addAugmentDescriptions(map);
        map.put(AugmentExtendTime.INSTANCE, "+5 seconds of dream-walking.");
        map.put(AugmentDurationDown.INSTANCE, "-5 seconds of dream-walking (minimum 5).");
    }

    @Override
    public String getBookDescription() {
        return "Forbidden: castable only from the Codex of Ruin. Your mind leaves your body and enters the target creature for 20 seconds: you see through its eyes, walk and jump with it, and left click makes it attack (a Creeper ignites). Your body stands frozen and you snap back the moment it is hurt, or when you sneak, right click, wander too far or the creature dies. Bosses resist. Adds corruption while it lasts.";
    }
}