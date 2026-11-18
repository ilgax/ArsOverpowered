package com.arsop.glyph.effect;

import com.arsop.ArsOp;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import com.hollingsworth.arsnouveau.api.util.SpellUtil;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentPierce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Annihilate: deletes whatever it touches.
 * <ul>
 *   <li>Entities take void damage (bypasses armor, resistance and invulnerability frames).</li>
 *   <li>Blocks are destroyed regardless of hardness or tool. Unbreakable blocks (bedrock, portals...)
 *       only if enabled in the server config.</li>
 * </ul>
 */
public class EffectAnnihilate extends AbstractEffect {
    public static final EffectAnnihilate INSTANCE = new EffectAnnihilate();

    public ModConfigSpec.BooleanValue ALLOW_UNBREAKABLE;

    private EffectAnnihilate() {
        super(ArsOp.prefix("glyph_annihilate"), "Annihilate");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level, @NotNull LivingEntity shooter, SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Entity target = hit.getEntity();
        // Void damage ignores creative mode, so spare creative/spectator players explicitly.
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return;

        double damage = DAMAGE.get() + AMP_VALUE.get() * spellStats.getAmpMultiplier();
        if (damage <= 0) return;

        DamageSource source = new DamageSource(
                serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FELL_OUT_OF_WORLD),
                shooter);
        target.hurt(source, (float) damage);
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level, @NotNull LivingEntity shooter, SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int pierce = spellStats.getBuffCount(AugmentPierce.INSTANCE);
        List<BlockPos> positions = SpellUtil.calcAOEBlocks(shooter, hit.getBlockPos(), hit, spellStats.getAoeMultiplier(), pierce);
        boolean allowUnbreakable = ALLOW_UNBREAKABLE != null && ALLOW_UNBREAKABLE.get();

        for (BlockPos pos : positions) {
            if (level.isOutsideBuildHeight(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(level, pos) < 0 && !allowUnbreakable) continue;
            // Respect land claims / spawn protection.
            if (!BlockUtil.destroyRespectsClaim(getPlayer(shooter, serverLevel), level, pos)) continue;
            level.destroyBlock(pos, true, shooter);
        }
    }

    @Override
    public void buildConfig(ModConfigSpec.Builder builder) {
        super.buildConfig(builder);
        addDamageConfig(builder, 1000.0);
        addAmpConfig(builder, 500.0);
        ALLOW_UNBREAKABLE = builder
                .comment("Allow Annihilate to destroy unbreakable blocks such as bedrock and end portal frames.")
                .define("allow_unbreakable", false);
    }

    @Override
    public int getDefaultManaCost() {
        return 250;
    }

    @Override
    public SpellTier defaultTier() {
        return SpellTier.THREE;
    }

    @NotNull
    @Override
    public Set<AbstractAugment> getCompatibleAugments() {
        return augmentSetOf(AugmentAmplify.INSTANCE, AugmentDampen.INSTANCE, AugmentAOE.INSTANCE, AugmentPierce.INSTANCE);
    }

    @Override
    public void addAugmentDescriptions(Map<AbstractAugment, String> map) {
        super.addAugmentDescriptions(map);
        map.put(AugmentAmplify.INSTANCE, "Even more damage. As if it mattered.");
        map.put(AugmentDampen.INSTANCE, "Reduces damage dealt.");
        map.put(AugmentAOE.INSTANCE, "Annihilates a larger area of blocks.");
        map.put(AugmentPierce.INSTANCE, "Annihilates blocks deeper into the surface.");
    }

    @Override
    public String getBookDescription() {
        return "Erases the target from existence. Deals void damage that ignores armor and resistance, and destroys any block regardless of hardness.";
    }
}
