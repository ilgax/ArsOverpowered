package com.arsop.glyph.effect;

import com.arsop.ArsOp;
import com.arsop.effect.ArsOpEffects;
import com.arsop.effect.FallenEffect;
import com.arsop.network.FallenCasterPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDurationDown;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fall: a word of power that slams creatures into the ground. Deals no damage.
 * <ul>
 *   <li><b>Entity hit</b> (including Self): the struck entity gets Fallen I (crawls, fights weakly),
 *       and every other creature in the radius gets Fallen II (pinned). Both last 8s, separately.</li>
 *   <li><b>Block hit</b>: every creature in the radius is pinned.</li>
 *   <li><b>Self</b>: you get Fallen I; <b>Self + AOE</b>: you get Fallen II. Others around you are pinned either way.</li>`n *   <li>The caster is never caught in the area. AOE widens the radius.</li>`n *   <li>Fallen players hear nothing except sounds from their caster.</li>
 * </ul>
 * Players caught in it also see the world go dark.
 */
public class EffectFall extends AbstractEffect {
    public static final EffectFall INSTANCE = new EffectFall();

    public ModConfigSpec.IntValue DURATION_TICKS;
    public ModConfigSpec.DoubleValue BASE_RADIUS;
    public ModConfigSpec.DoubleValue CRUSHED_DAMAGE_MULT;
    public ModConfigSpec.DoubleValue CRAWL_SPEED;

    private EffectFall() {
        super(ArsOp.prefix("glyph_fall"), "Fall");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level, @NotNull LivingEntity shooter, SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) {
            areaFall(serverLevel, hit.getEntity().position(), shooter, spellStats, null);
            return;
        }
        // Everything else in the radius is pinned with Fallen II (sound plays first, before the silence).
        areaFall(serverLevel, target.position(), shooter, spellStats, target);
        // The struck entity gets Fallen I (crawls, can still fight weakly).
        // Self cast: as if someone struck you (works in creative). Self + AOE: as if caught in the area (Fallen II).
        boolean selfPin = target == shooter && spellStats.getAoeMultiplier() > 0;
        if (target == shooter || canAffect(target, shooter)) {
            slam(serverLevel, target, shooter, spellStats, selfPin);
        }
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level, @NotNull LivingEntity shooter, SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        areaFall(serverLevel, Vec3.atCenterOf(hit.getBlockPos().relative(hit.getDirection())), shooter, spellStats, null);
    }

    /** Pins every creature in the radius except the caster and {@code exclude} (the struck entity, if any). */
    private void areaFall(ServerLevel level, Vec3 center, LivingEntity shooter, SpellStats stats, LivingEntity exclude) {
        int aoe = Math.min(stats.getBuffCount(AugmentAOE.INSTANCE), MAX_UPGRADES);
        double radius = BASE_RADIUS.get() + RADIUS_PER_AOE * aoe;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                e -> e != exclude && e.distanceToSqr(center) <= radius * radius && canAffect(e, shooter));
        playFall(level, center, radius); // before the effect, so victims hear the impact, then silence
        for (LivingEntity target : targets) {
            slam(level, target, shooter, stats, true);
        }
    }

    private boolean canAffect(LivingEntity target, LivingEntity shooter) {
        if (target == shooter || !target.isAlive()) return false;
        return !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void slam(ServerLevel level, LivingEntity target, LivingEntity shooter, SpellStats stats, boolean pin) {
        int extend = Math.min(stats.getBuffCount(AugmentExtendTime.INSTANCE), MAX_UPGRADES);
        int shorten = stats.getBuffCount(AugmentDurationDown.INSTANCE);
        int ticks = Math.max(20, DURATION_TICKS.get() + TICKS_PER_EXTEND * (extend - shorten));

        // Hurl them into the floor (no damage).
        target.setDeltaMovement(0, -4.0, 0);
        target.hurtMarked = true;
        target.resetFallDistance();
        if (target instanceof Player p && p.isFallFlying()) p.stopFallFlying();

        // Remember who did this: Fallen victims can still hear their caster (see FallenSilence).
        target.getPersistentData().putUUID(FallenEffect.CASTER_KEY, shooter.getUUID());
        if (target instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new FallenCasterPayload(shooter.getId()));
        }
        // Struck entity: Fallen I. Area: Fallen II. Separate, no fallback between them.
        target.addEffect(new MobEffectInstance(ArsOpEffects.FALLEN, ticks, pin ? FallenEffect.PINNED : FallenEffect.CRUSHED, false, false, true), shooter);
        // "The world goes black" for players.
        if (target instanceof Player) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0, false, false, false), shooter);
        }
    }

    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02f, 0.02f, 0.02f), 2.5f);

    private void playFall(ServerLevel level, Vec3 center, double radius) {
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.4f);
        int points = (int) Math.max(24, radius * 16);
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            level.sendParticles(BLACK, center.x + Math.cos(a) * radius, center.y + 0.2, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void buildConfig(ModConfigSpec.Builder builder) {
        super.buildConfig(builder);
        DURATION_TICKS = builder.comment("Ticks of Fallen I (struck entity) and Fallen II (area). 20 ticks = 1 second.")
                .defineInRange("duration_ticks", 160, 0, Integer.MAX_VALUE);
        BASE_RADIUS = builder.comment("Area radius in blocks before AOE augments (each AOE adds 4).")
                .defineInRange("base_radius", 8.0, 0.0, 64.0); // each AOE adds 4
        CRUSHED_DAMAGE_MULT = builder.comment("Damage multiplier for anything dealt by a creature under Fallen I (0.4 = 40% of normal).")
                .defineInRange("crushed_damage_multiplier", 0.4, 0.0, 1.0);
        CRAWL_SPEED = builder.comment("Max blocks per tick a Fallen I creature can move horizontally (walking is ~0.22).")
                .defineInRange("crawl_speed", 0.03, 0.0, 1.0);
    }

    /**
     * Upgrade budget: Extend Time and AOE share {@link #MAX_UPGRADES} slots (any mix).
     * Each upgrade adds 5% mana drain on top of the base 80%, so a full budget drains 100%.
     */
    public static final int MAX_UPGRADES = 4;
    public static final double BASE_MANA_FRACTION = 0.8;
    public static final double MANA_PER_UPGRADE = 0.05;
    /** Per Extend Time: +1 second of Fallen (Duration Down: -1 second). */
    public static final int TICKS_PER_EXTEND = 20;
    /** Per AOE: +4 blocks of radius. */
    public static final double RADIUS_PER_AOE = 4.0;
    /** Fall can only be cast with at least this fraction of max mana. */
    public static final double MIN_MANA_TO_CAST = 0.8;

    @Override
    protected void addDefaultAugmentLimits(Map<ResourceLocation, Integer> defaults) {
        // Per-type caps; the shared Extend Time + AOE budget is enforced at cast time.
        defaults.put(AugmentExtendTime.INSTANCE.getRegistryName(), MAX_UPGRADES);
        defaults.put(AugmentAOE.INSTANCE.getRegistryName(), MAX_UPGRADES);
    }

    /** Fraction of the caster's current mana this cast drains: 80% + 5% per upgrade, up to 100%. */
    public static double manaFraction(int upgrades) {
        int n = Math.max(0, Math.min(upgrades, MAX_UPGRADES));
        return Math.min(1.0, BASE_MANA_FRACTION + MANA_PER_UPGRADE * n);
    }

    @Override
    public int getDefaultManaCost() {
        return 300;
    }

    @Override
    public SpellTier defaultTier() {
        return SpellTier.THREE;
    }

    @NotNull
    @Override
    public Set<AbstractAugment> getCompatibleAugments() {
        return augmentSetOf(AugmentAOE.INSTANCE, AugmentExtendTime.INSTANCE, AugmentDurationDown.INSTANCE);
    }

    @Override
    public void addAugmentDescriptions(Map<AbstractAugment, String> map) {
        super.addAugmentDescriptions(map);
        map.put(AugmentAOE.INSTANCE, "+4 blocks of radius and +5% mana drain each. Shares 4 upgrade slots with Extend Time.");
        map.put(AugmentExtendTime.INSTANCE, "+1 second of Fallen and +5% mana drain each. Shares 4 upgrade slots with AOE.");
        map.put(AugmentDurationDown.INSTANCE, "-1 second of Fallen each (minimum 1 second).");
    }

    @Override
    public String getBookDescription() {
        return "A single word that commands everything to the ground. Deals no damage. The creature it strikes is left crawling, hitting weaker and unable to heal, while every other creature around it is slammed and pinned, unable to move or attack. On yourself, you feel it as if struck, or as if caught in the area with AOE. On a block, everything nearby is pinned. The fallen hear nothing but the one who cast it. Needs at least 80% of your max mana. Drains 80% of your current mana, +5% per Extend Time or AOE (4 upgrades max, 100%). Only castable from an Archmage or Creative spell book.";
    }
}
