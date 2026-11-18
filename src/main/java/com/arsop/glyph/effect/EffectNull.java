package com.arsop.glyph.effect;

import com.arsop.ArsOp;
import com.arsop.entity.NullEntity;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAccelerate;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDecelerate;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDurationDown;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentPierce;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectGravity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Null: creates a NULL singularity (see {@link NullEntity}).
 * <ul>
 *   <li>Projectile: the spell projectile is replaced by a NULL thrown from the hand. It stops once its center
 *       hits a block, unless Pierce is added.</li>
 *   <li>Orbit -> Null: each orbiting projectile becomes a mini NULL circling the caster (no block breaking).</li>
 *   <li>Touch / Self / other forms: the NULL hangs in place at the target.</li>
 *   <li>Always damages creatures it touches. Breaks blocks only if Break comes after it in the spell.</li>
 *   <li>Size: 1x1 at base, radius 2^AOE (2, 4, 8, 16), max 4 AOE.</li>
 *   <li>Gravity after it: pulls creatures in (stronger with Amplify). Amplify: +2 damage each; 1+ ignores armor/Resistance.
 *       Discarding: Amplify 1 at radius 16, 2 at 8, 3 at 4, 4 at 2; 1x1 never (max Amplify 4) (also erases unbreakable blocks with Break).</li>
 * </ul>
 */
public class EffectNull extends AbstractEffect {
    public static final EffectNull INSTANCE = new EffectNull();

    public static final int MAX_AOE = 4;
    public static final int MAX_AMP = 4;
    public static final int TICKS_PER_EXTEND = 20;
    /** Bigger NULLs last longer: +1 second per size step. */
    public static final int TICKS_PER_AOE = 20;
    public static final double SPEED_PER_ACCEL = 0.432;
    /** Each Accelerate on the Null charges 15% faster. */
    public static final double CHARGE_ACCEL_BONUS = 0.15;
    public static final float BASE_DAMAGE = 8.0f;
    public static final float DAMAGE_PER_AOE = 2.0f;
    public static final float DAMAGE_PER_AMP = 4.0f;
    public static final double PULL_BASE = 0.08;
    public static final double PULL_PER_AMP = 0.03;
    public static final int ORBIT_LIFE_TICKS = 400;

    public ModConfigSpec.IntValue LIFE_TICKS;
    public ModConfigSpec.DoubleValue SPEED;
    public ModConfigSpec.IntValue GROW_FIRST_TICKS;
    public ModConfigSpec.DoubleValue GROW_FACTOR;
    public ModConfigSpec.IntValue MANA_PER_STEP;

    private EffectNull() {
        super(ArsOp.prefix("glyph_null"), "Null");
    }

    /** Everything that shapes a NULL. */
    public record Params(int aoe, int amp, int extend, int shorten, int accel, int decel, int pierce, boolean breaks, boolean gravity,
                         int chargeAccel) {
        public Params {
            aoe = Math.max(0, Math.min(MAX_AOE, aoe));
            amp = Math.max(0, Math.min(MAX_AMP, amp));
        }

        public static Params of(SpellStats s, List<AbstractSpellPart> parts) {
            return new Params(s.getBuffCount(AugmentAOE.INSTANCE), s.getBuffCount(AugmentAmplify.INSTANCE),
                    s.getBuffCount(AugmentExtendTime.INSTANCE), s.getBuffCount(AugmentDurationDown.INSTANCE),
                    s.getBuffCount(AugmentAccelerate.INSTANCE), s.getBuffCount(AugmentDecelerate.INSTANCE),
                    s.getBuffCount(AugmentPierce.INSTANCE), hasAfter(parts, parts.indexOf(INSTANCE), EffectBreak.class),
                    hasAfter(parts, parts.indexOf(INSTANCE), EffectGravity.class), 0);
        }

        /**
         * Counts augments directly after the Null at {@code nullIndex}. Accelerate/Decelerate on the cast form set
         * throw speed; Accelerate on the Null speeds up charging. Pierce counts in either place.
         */
        public static Params of(List<AbstractSpellPart> parts, int nullIndex) {
            int aoe = 0, amp = 0, ext = 0, down = 0, acc = 0, dec = 0, pierce = 0, chargeAcc = 0;
            for (int i = 0; i < parts.size(); i++) {
                AbstractSpellPart p = parts.get(i);
                if (i > nullIndex && p instanceof AbstractEffect) break;
                if (p == AugmentPierce.INSTANCE) pierce++;
                if (i < nullIndex) {
                    if (p == AugmentAccelerate.INSTANCE) acc++;
                    else if (p == AugmentDecelerate.INSTANCE) dec++;
                    continue;
                }
                if (i == nullIndex) continue;
                if (p == AugmentAccelerate.INSTANCE) chargeAcc++;
                else if (p == AugmentDecelerate.INSTANCE) dec++;
                else if (p == AugmentAOE.INSTANCE) aoe++;
                else if (p == AugmentAmplify.INSTANCE) amp++;
                else if (p == AugmentExtendTime.INSTANCE) ext++;
                else if (p == AugmentDurationDown.INSTANCE) down++;
            }
            return new Params(aoe, amp, ext, down, acc, dec, pierce,
                    hasAfter(parts, nullIndex, EffectBreak.class), hasAfter(parts, nullIndex, EffectGravity.class), chargeAcc);
        }

        /** True if a glyph of {@code type} appears anywhere after the Null. */
        private static boolean hasAfter(List<AbstractSpellPart> parts, int nullIndex, Class<? extends AbstractEffect> type) {
            if (nullIndex < 0) return false;
            for (int i = nullIndex + 1; i < parts.size(); i++) {
                if (type.isInstance(parts.get(i))) return true;
            }
            return false;
        }

        /** 1x1 (radius 0.5) with no AOE, then 2^AOE blocks. */
        public float radius() {
            return radiusFor(aoe);
        }

        public static float radiusFor(int aoe) {
            aoe = Math.max(0, Math.min(MAX_AOE, aoe));
            return aoe == 0 ? 0.5f : (float) (1 << aoe);
        }

        /** Same NULL at another size step (used while a NULL is growing). */
        public Params withAoe(int newAoe) {
            return new Params(newAoe, amp, extend, shorten, accel, decel, pierce, breaks, gravity, chargeAccel);
        }

        /** Orbiting mini NULLs: 0.5, growing 0.25 per AOE. */
        public float miniRadius() {
            return 0.5f + 0.25f * aoe;
        }

        public int life() {
            int base = INSTANCE.LIFE_TICKS == null ? 80 : INSTANCE.LIFE_TICKS.get();
            return Math.max(20, base + TICKS_PER_AOE * aoe + TICKS_PER_EXTEND * (extend - shorten));
        }

        public int orbitLife() {
            return Math.max(20, ORBIT_LIFE_TICKS + TICKS_PER_EXTEND * (extend - shorten));
        }

        public double speed() {
            double base = INSTANCE.SPEED == null ? 0.864 : INSTANCE.SPEED.get();
            return Math.max(0.08, base + SPEED_PER_ACCEL * (accel - decel));
        }

        /** Orbit spin in radians per tick: one turn per 2 s, faster with Accelerate (form or Null). */
        public double orbitSpin() {
            return Math.max(0.03, (Math.PI / 20.0) * (1.0 + 0.5 * (accel + chargeAccel - decel)));
        }

        /**
         * Ticks to grow from step {@code step} to the next while charging. Starts fast and slows toward max:
         * first step * factor^step, divided by (1 + 15% per Accelerate on the Null).
         */
        public float ticksForStep(int step) {
            double first = INSTANCE.GROW_FIRST_TICKS == null ? 6 : INSTANCE.GROW_FIRST_TICKS.get();
            double factor = INSTANCE.GROW_FACTOR == null ? 1.6 : INSTANCE.GROW_FACTOR.get();
            double ticks = first * Math.pow(factor, step) / (1.0 + CHARGE_ACCEL_BONUS * chargeAccel);
            return (float) Math.max(1.0, ticks);
        }

        public float damage() {
            return BASE_DAMAGE + DAMAGE_PER_AOE * aoe + DAMAGE_PER_AMP * amp;
        }

        public double pull() {
            return gravity ? PULL_BASE + PULL_PER_AMP * amp : 0;
        }

        /** Amp 1+: damage ignores armor, Resistance and protection enchantments. */
        public boolean piercing() {
            return amp >= 1;
        }

        /** Minimum size that discards: Amp 1 -> radius 16, 2 -> 8, 3 -> 4, 4 (max) -> 2. A 1x1 NULL never discards. */
        public boolean discards() {
            return amp >= 1 && aoe >= 1 && aoe >= MAX_AOE + 1 - amp;
        }
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level, @NotNull LivingEntity shooter, SpellStats stats, SpellContext ctx, SpellResolver resolver) {
        create(level, shooter, hit.getEntity().getBoundingBox().getCenter(), stats, ctx);
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level, @NotNull LivingEntity shooter, SpellStats stats, SpellContext ctx, SpellResolver resolver) {
        create(level, shooter, Vec3.atCenterOf(hit.getBlockPos()), stats, ctx);
    }

    private void create(Level level, LivingEntity shooter, Vec3 center, SpellStats stats, SpellContext ctx) {
        if (!(level instanceof ServerLevel)) return;
        List<AbstractSpellPart> parts = ctx.getSpell() == null ? Collections.emptyList() : ctx.getSpell().unsafeList();
        NullEntity.spawn(level, shooter, center, Vec3.ZERO, Params.of(stats, parts));
    }

    @Override
    public void buildConfig(ModConfigSpec.Builder builder) {
        super.buildConfig(builder);
        LIFE_TICKS = builder.comment("Ticks a NULL exists before collapsing. 20 ticks = 1 second.")
                .defineInRange("life_ticks", 80, 1, 72000);
        SPEED = builder.comment("Blocks per tick a thrown NULL travels before Accelerate/Decelerate.")
                .defineInRange("speed", 0.864, 0.0, 10.0);
        GROW_FIRST_TICKS = builder.comment("Ticks of holding right click to grow a charging NULL from 1x1 to radius 2.")
                .defineInRange("grow_first_step_ticks", 6, 1, 400);
        GROW_FACTOR = builder.comment("Each later size step takes this many times longer than the one before (starts fast, slows toward max).")
                .defineInRange("grow_step_factor", 1.6, 1.0, 10.0);
        MANA_PER_STEP = builder.comment("Mana spent each time a charging NULL grows into the next size step.")
                .defineInRange("mana_per_step", 100, 0, 100000);
    }

    @Override
    protected void addDefaultAugmentLimits(Map<ResourceLocation, Integer> defaults) {
        defaults.put(AugmentAOE.INSTANCE.getRegistryName(), MAX_AOE);
        defaults.put(AugmentAmplify.INSTANCE.getRegistryName(), MAX_AMP);
    }

    @Override
    public int getDefaultManaCost() {
        return 400;
    }

    @Override
    public SpellTier defaultTier() {
        return SpellTier.THREE;
    }

    @NotNull
    @Override
    public Set<AbstractAugment> getCompatibleAugments() {
        return augmentSetOf(AugmentAOE.INSTANCE, AugmentAmplify.INSTANCE, AugmentExtendTime.INSTANCE,
                AugmentDurationDown.INSTANCE, AugmentAccelerate.INSTANCE, AugmentDecelerate.INSTANCE, AugmentPierce.INSTANCE);
    }

    @Override
    public void addAugmentDescriptions(Map<AbstractAugment, String> map) {
        super.addAugmentDescriptions(map);
        map.put(AugmentAOE.INSTANCE, "Doubles the NULL's radius: 2, 4, 8, 16 blocks (max 4). +2 damage and +1 second each. Thrown NULLs start at 1x1 and grow up to this size while you hold right click.");
        map.put(AugmentAmplify.INSTANCE, "+4 damage each, and a stronger pull with Gravity. Any Amplify ignores armor and Resistance. Erases creatures (and unbreakable blocks with Break): 1 at radius 16, 2 at radius 8, 3 at radius 4, 4 at radius 2 (max 4). 1x1 NULLs never erase.");
        map.put(AugmentExtendTime.INSTANCE, "+1 second before the NULL collapses.");
        map.put(AugmentDurationDown.INSTANCE, "-1 second before the NULL collapses (minimum 1 second).");
        map.put(AugmentAccelerate.INSTANCE, "A held NULL charges 15% faster each (on Projectile instead, it flies faster). Orbiting NULLs spin faster.");
        map.put(AugmentDecelerate.INSTANCE, "A thrown NULL flies slower; orbiting NULLs spin slower.");
        map.put(AugmentPierce.INSTANCE, "A thrown NULL keeps flying through blocks instead of stopping.");
    }

    @Override
    public String getBookDescription() {
        return "Creates a NULL, a singularity that damages everything it touches (8 damage, +2 per AOE, +4 per Amplify). Add Break after it to make it break blocks without drops, and Gravity to make it pull creatures in. With Projectile a click throws it from your hand; holding right click gathers it above your head, growing (mana per size step), then flies where you look on release and stops in the first block it hits unless Pierced; with Projectile and Wall, a wall of small NULLs flies out; with Orbit, small NULLs circle you. Amplify ignores armor, and with enough Amplify and size it erases creatures outright. It never touches its caster. Base size 1x1, radius doubles with each AOE up to 16.";
    }
}
