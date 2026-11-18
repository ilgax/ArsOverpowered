package com.arsop.entity;

import com.arsop.ArsOp;
import com.arsop.glyph.effect.EffectNull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.minecraft.world.Clearable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A NULL: a singularity that breaks every block it touches (no drops).
 * <ul>
 *   <li>Damage &gt; 0 (Amplify 1+): pulls creatures to its center and hurts them every tick (i-frames apply).</li>
 *   <li>Piercing (Amplify 2+): that damage ignores armor, Resistance and protection.</li>
 *   <li>Discarding (enough Amplify for its size): deletes entities outright, kills players, erases unbreakable blocks.</li>
 *   <li>Host rule: never touches its caster, the caster's mount/passengers, the caster's projectiles, or other NULLs.</li>
 * </ul>
 * The position is the disc's center. Large NULLs break blocks over several ticks (scan budget).
 */
public class NullEntity extends Entity {
    public static final ResourceKey<DamageType> NULL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ArsOp.prefix("null"));
    public static final ResourceKey<DamageType> NULL_PIERCING = ResourceKey.create(Registries.DAMAGE_TYPE, ArsOp.prefix("null_piercing"));

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(NullEntity.class, EntityDataSerializers.FLOAT);
    /** Entity id the NULL circles around, or -1. Synced so the client moves it smoothly too. */
    private static final EntityDataAccessor<Integer> ORBIT_OWNER = SynchedEntityData.defineId(NullEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ORBIT_ANGLE = SynchedEntityData.defineId(NullEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ORBIT_DIST = SynchedEntityData.defineId(NullEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ORBIT_SPIN = SynchedEntityData.defineId(NullEntity.class, EntityDataSerializers.FLOAT);
    /** Entity id of the caster this NULL is charging above, or -1. Synced so the client follows the caster smoothly. */
    private static final EntityDataAccessor<Integer> CHARGE_OWNER = SynchedEntityData.defineId(NullEntity.class, EntityDataSerializers.INT);
    /** Charging NULL can't grow any more (reached its AOE cap). Drives the white-hot look. */
    private static final EntityDataAccessor<Boolean> CHARGE_MAX = SynchedEntityData.defineId(NullEntity.class, EntityDataSerializers.BOOLEAN);
    /** Gap between the caster's head and the bottom of a charging NULL. */
    private static final double CHARGE_GAP = 1.0;
    /** Ticks after the cast during which a missing "held" signal doesn't release yet (packet ordering). */
    private static final int CHARGE_GRACE = 5;
    /** Block positions checked per tick. A radius-16 NULL (~36k cube positions) clears in one tick. */
    private static final int SCAN_BUDGET = 65536;
    /** Pull reaches this many radii out (at least 4 blocks). */
    private static final double PULL_RANGE_MULT = 3.0;

    @Nullable private UUID ownerId;
    private int life = 100;
    private int age;
    private float damage;
    private boolean piercing;
    private boolean discards;
    private double pull;
    /** Keeps flying through blocks instead of stopping in the first one. */
    private boolean pierce;
    /** Breaks blocks (Break glyph after Null). */
    private boolean breaks;

    // Charging (server only, not saved: a reload just releases it).
    @Nullable private EffectNull.Params chargeParams;
    /** Charging where it was placed (Touch) instead of above the head. */
    private boolean inPlace;
    /** Size progress in AOE steps; the integer part is the last checkpoint reached. */
    private float chargeProgress;
    /** Highest step already paid for. */
    private int chargePaid;
    private int chargeTicks;
    private boolean warnedNoMana;

    private int scanCursor;
    @Nullable private BlockPos scanCenter;

    public NullEntity(EntityType<? extends NullEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static NullEntity spawn(Level level, LivingEntity owner, Vec3 center, Vec3 velocity, EffectNull.Params params) {
        return spawn(level, owner, center, velocity, params, params.radius(), true);
    }

    public static NullEntity spawn(Level level, LivingEntity owner, Vec3 center, Vec3 velocity, EffectNull.Params params,
                                   float radius, boolean sound) {
        NullEntity n = new NullEntity(ArsOpEntities.NULL.get(), level);
        n.ownerId = owner.getUUID();
        n.setPos(center);
        n.setDeltaMovement(velocity);
        n.setRadius(radius);
        n.life = params.life();
        n.damage = params.damage();
        n.piercing = params.piercing();
        n.discards = params.discards();
        n.pull = params.pull();
        n.pierce = params.pierce() > 0;
        n.breaks = params.breaks();
        level.addFreshEntity(n);
        if (sound) level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 0.5f);
        return n;
    }

    /** A mini NULL circling {@code owner}: damages (and pulls, with Amplify) but never breaks blocks or discards. */
    public static NullEntity spawnOrbiting(Level level, LivingEntity owner, EffectNull.Params params, float angle) {
        NullEntity n = new NullEntity(ArsOpEntities.NULL.get(), level);
        n.ownerId = owner.getUUID();
        float r = params.miniRadius();
        n.setRadius(r);
        n.entityData.set(ORBIT_OWNER, owner.getId());
        n.entityData.set(ORBIT_ANGLE, angle);
        n.entityData.set(ORBIT_DIST, 1.6f + r * 2f);
        n.entityData.set(ORBIT_SPIN, (float) params.orbitSpin());
        n.life = params.orbitLife();
        n.damage = params.damage();
        n.piercing = params.piercing();
        n.discards = false;
        n.breaks = false;
        n.pull = params.pull();
        n.setPos(n.orbitPosition(owner));
        level.addFreshEntity(n);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6f, 1.2f);
        return n;
    }

    /**
     * A thrown NULL that first grows above the caster's head while they hold right click (up to the AOE cap,
     * paying mana per step), then flies where they look on release.
     */
    public static NullEntity spawnCharging(Level level, LivingEntity owner, EffectNull.Params params) {
        NullEntity n = new NullEntity(ArsOpEntities.NULL.get(), level);
        n.ownerId = owner.getUUID();
        n.chargeParams = params;
        n.applyStep(params.withAoe(0));
        n.pierce = params.pierce() > 0;
        n.breaks = params.breaks();
        n.pull = params.pull();
        n.life = params.life();
        n.setRadius(EffectNull.Params.radiusFor(0));
        n.entityData.set(CHARGE_OWNER, owner.getId());
        n.setPos(n.chargePosition(owner));
        level.addFreshEntity(n);
        NullCharge.start(owner.getUUID(), n);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 0.7f);
        return n;
    }

    /** Growing above the caster's head (thrown NULL). Synced, so it works on both sides. */
    public boolean isAnchoredCharge() {
        return entityData.get(CHARGE_OWNER) >= 0;
    }

    /** Still growing, above the head or in place (server side). */
    public boolean isCharging() {
        return isAnchoredCharge() || chargeParams != null;
    }

    /**
     * Touch -> Null: appears at the target at 1x1 and grows in place while the caster holds right click
     * (same curve, mana per step and cues as a thrown NULL). On release it stays and its lifetime starts.
     */
    public static NullEntity spawnChargingInPlace(Level level, LivingEntity owner, Vec3 center, EffectNull.Params params) {
        NullEntity n = new NullEntity(ArsOpEntities.NULL.get(), level);
        n.ownerId = owner.getUUID();
        n.chargeParams = params;
        n.inPlace = true;
        n.applyStep(params.withAoe(0));
        n.pierce = params.pierce() > 0;
        n.breaks = params.breaks();
        n.pull = params.pull();
        n.life = params.withAoe(0).life();
        n.setRadius(EffectNull.Params.radiusFor(0));
        n.setPos(center);
        level.addFreshEntity(n);
        NullCharge.start(owner.getUUID(), n);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 0.5f);
        return n;
    }

    /** True once a charging NULL has reached its full size. */
    public boolean isChargeMax() {
        return entityData.get(CHARGE_MAX);
    }

    private void announce(LivingEntity owner, Component msg) {
        if (owner instanceof Player p) p.displayClientMessage(msg, true);
    }

    private static String sizeLabel(int step) {
        return step == 0 ? "1x1" : String.valueOf(1 << step);
    }

    /** Bottom edge stays one block above the head, so it grows upward and never touches the caster. */
    private Vec3 chargePosition(Entity owner) {
        return owner.position().add(0, owner.getBbHeight() + CHARGE_GAP + getRadius(), 0);
    }

    private void applyStep(EffectNull.Params p) {
        this.damage = p.damage();
        this.piercing = p.piercing();
        this.discards = p.discards();
    }

    private void tickCharge(ServerLevel server, LivingEntity owner) {
        EffectNull.Params params = chargeParams;
        if (params == null) {
            finishCharge(owner);
            return;
        }
        chargeTicks++;
        boolean held = NullCharge.isHeld(owner.getUUID()) || chargeTicks <= CHARGE_GRACE;
        if (!held) {
            finishCharge(owner);
            return;
        }
        // Touch: wait like the projectile does, so a click doesn't start growing.
        if (inPlace && chargeTicks <= NullCharge.HOLD_THRESHOLD) return;
        int cap = params.aoe();
        if (chargeProgress >= cap) {
            if (!isChargeMax()) {
                entityData.set(CHARGE_MAX, true);
                announce(owner, Component.translatable("ars_op.null.max_size", sizeLabel(cap)));
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.5f);
                server.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.6f);
            }
            return;
        }

        int next = (int) Math.floor(chargeProgress) + 1;
        if (chargePaid < next) {
            if (!payStep(owner)) {
                // Out of mana: stays at this checkpoint.
                if (!warnedNoMana) {
                    warnedNoMana = true;
                    announce(owner, Component.translatable("ars_op.null.no_mana", sizeLabel(next - 1), sizeLabel(cap)));
                    server.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.6f);
                }
                return;
            }
            warnedNoMana = false;
            chargePaid = next;
        }
        float before = chargeProgress;
        chargeProgress = Math.min(cap, chargeProgress + 1f / params.ticksForStep((int) Math.floor(before)));
        int step = (int) Math.floor(chargeProgress);
        if (step > (int) Math.floor(before)) {
            applyStep(params.withAoe(step));
            if (step < cap) announce(owner, Component.translatable("ars_op.null.size", sizeLabel(step), sizeLabel(cap)));
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f + 0.1f * step);
        }
        float lo = EffectNull.Params.radiusFor(step);
        float hi = EffectNull.Params.radiusFor(Math.min(cap, step + 1));
        setRadius(lo + (hi - lo) * (chargeProgress - step));
    }

    private boolean payStep(LivingEntity owner) {
        if (owner instanceof Player p && p.isCreative()) return true;
        IManaCap mana = CapabilityRegistry.getMana(owner);
        if (mana == null) return false;
        double cost = Math.ceil(mana.getMaxMana() * EffectNull.stepPct());
        if (cost <= 0) return true;
        if (mana.getCurrentMana() < cost) return false;
        mana.removeMana(cost);
        return true;
    }

    private void finishCharge(LivingEntity owner) {
        if (inPlace) settle(owner);
        else release(owner);
    }

    /** Touch NULL let go: keeps its last checkpoint size where it is, and starts its lifetime. */
    private void settle(LivingEntity owner) {
        int step = (int) Math.floor(chargeProgress);
        setRadius(EffectNull.Params.radiusFor(step));
        if (chargeParams != null) life = chargeParams.withAoe(step).life();
        chargeParams = null;
        inPlace = false;
        entityData.set(CHARGE_MAX, false);
        if (owner != null) NullCharge.stop(owner.getUUID(), this);
        else if (ownerId != null) NullCharge.stop(ownerId, this);
        age = 0;
    }

    private void release(LivingEntity owner) {
        // Snap to the last checkpoint so size, tier and lifetime match.
        int step = (int) Math.floor(chargeProgress);
        setRadius(EffectNull.Params.radiusFor(step));
        if (chargeParams != null) life = chargeParams.withAoe(step).life();
        setPos(chargePosition(owner));
        entityData.set(CHARGE_OWNER, -1);
        entityData.set(CHARGE_MAX, false);
        NullCharge.stop(owner.getUUID(), this);
        double speed = chargeParams != null ? chargeParams.speed() : 0.864;
        setDeltaMovement(owner.getLookAngle().scale(speed));
        chargeParams = null;
        age = 0;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    private Vec3 orbitPosition(Entity owner) {
        double a = entityData.get(ORBIT_ANGLE) + tickCount * entityData.get(ORBIT_SPIN);
        double d = entityData.get(ORBIT_DIST);
        return owner.position().add(Math.cos(a) * d, owner.getBbHeight() * 0.55, Math.sin(a) * d);
    }

    private boolean isOrbiting() {
        return entityData.get(ORBIT_OWNER) >= 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 0.5f);
        builder.define(ORBIT_OWNER, -1);
        builder.define(ORBIT_ANGLE, 0f);
        builder.define(ORBIT_DIST, 0f);
        builder.define(ORBIT_SPIN, 0f);
        builder.define(CHARGE_OWNER, -1);
        builder.define(CHARGE_MAX, false);
    }

    public float getRadius() {
        return this.entityData.get(RADIUS);
    }

    public void setRadius(float r) {
        this.entityData.set(RADIUS, r);
    }

    @Override
    public void tick() {
        super.tick();
        if (isAnchoredCharge()) {
            Entity anchor = level().getEntity(entityData.get(CHARGE_OWNER));
            if (anchor == null || !anchor.isAlive()) {
                if (!level().isClientSide) collapse();
                return;
            }
            if (!level().isClientSide && anchor instanceof LivingEntity living) {
                tickCharge((ServerLevel) level(), living);
                if (!isAnchoredCharge()) return; // just released: starts flying next tick
            }
            setPos(chargePosition(anchor));
            if (level().isClientSide) return;
            ServerLevel server = (ServerLevel) level();
            float r = getRadius();
            if (pull > 0) pullEntities(anchor, r);
            hitEntities(server, anchor, r);
            return;
        } else if (isOrbiting()) {
            Entity anchor = level().getEntity(entityData.get(ORBIT_OWNER));
            if (anchor == null || !anchor.isAlive()) {
                if (!level().isClientSide) collapse();
                return;
            }
            setPos(orbitPosition(anchor));
        } else {
            move();
        }
        if (level().isClientSide) return;

        if (!level().isClientSide && inPlace && chargeParams != null) {
            Entity o = ownerId == null ? null : ((ServerLevel) level()).getEntity(ownerId);
            if (o instanceof LivingEntity living && living.isAlive()) tickCharge((ServerLevel) level(), living);
            else settle(null);
        }
        if (!inPlace) age++;
        if (age > life || getY() < level().getMinBuildHeight() - 64 || getY() > level().getMaxBuildHeight() + 256) {
            collapse();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        Entity owner = ownerId == null ? null : server.getEntity(ownerId);
        float r = getRadius();
        if (pull > 0) pullEntities(owner, r);
        hitEntities(server, owner, r);
        if (canBreakNow()) eraseBlocks(server, r);
        if (age % 20 == 0 && !isOrbiting()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.3f);
        }
    }

    /**
     * Flies straight. Without Pierce it stops once its probe point reaches a solid block. The probe sits
     * halfway between the center and the leading edge, and up to 1 block lower (as far as it stays inside
     * the NULL), so it lands on the ground it skims instead of floating over it.
     */
    private void move() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() == 0) return;
        Vec3 to = position().add(v);
        if (!pierce) {
            float r = getRadius();
            double forward = r * 0.5;
            double down = Math.min(1.0, Math.sqrt(Math.max(0, r * r - forward * forward)) * 0.95);
            Vec3 lead = v.normalize().scale(forward).add(0, -down, 0);
            Vec3 probeFrom = position().add(lead);
            BlockHitResult hit = level().clip(new ClipContext(probeFrom, to.add(lead), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                setPos(hit.getLocation().subtract(lead));
                setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
        setPos(to);
    }

    /** A thrown NULL without Pierce only eats blocks once it has landed, so Break can't tunnel it forward. */
    private boolean canBreakNow() {
        return breaks && (pierce || getDeltaMovement().lengthSqr() == 0);
    }

    /**
     * The client simulates movement itself (straight line or orbit), so small server corrections are
     * ignored to avoid snapping back and forth. Large drift still snaps.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (isOrbiting() || isAnchoredCharge()) return;
        if (position().distanceToSqr(x, y, z) > 2.25) super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    // ---- entities ------------------------------------------------------------------------------

    private boolean isHost(@Nullable Entity owner, Entity e) {
        if (e instanceof NullEntity) return true;
        if (owner == null) return false;
        if (e == owner) return true;
        if (e.hasIndirectPassenger(owner) || owner.hasIndirectPassenger(e)) return true;
        return e instanceof Projectile p && p.getOwner() == owner;
    }

    /** True if the entity's bounding box touches the sphere. */
    private boolean touches(Entity e, double r) {
        AABB b = e.getBoundingBox();
        Vec3 c = position();
        double dx = Math.max(b.minX - c.x, Math.max(0, c.x - b.maxX));
        double dy = Math.max(b.minY - c.y, Math.max(0, c.y - b.maxY));
        double dz = Math.max(b.minZ - c.z, Math.max(0, c.z - b.maxZ));
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private void hitEntities(ServerLevel server, @Nullable Entity owner, double r) {
        AABB box = new AABB(position(), position()).inflate(r);
        for (Entity e : server.getEntities(this, box, Entity::isAlive)) {
            Entity root = e instanceof PartEntity<?> part ? part.getParent() : e;
            if (!root.isAlive() || isHost(owner, root) || !touches(e, r)) continue;
            if (discards) {
                erase(server, root, owner);
            } else if (root instanceof LivingEntity living) {
                living.hurt(source(server, piercing ? NULL_PIERCING : NULL_DAMAGE, owner), damage);
            }
        }
    }

    private DamageSource source(ServerLevel server, ResourceKey<DamageType> key, @Nullable Entity owner) {
        return new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), this, owner);
    }

    private void erase(ServerLevel server, Entity e, @Nullable Entity owner) {
        NullErasure.erase(server, e, this, owner);
    }

    /** True if this NULL deletes what it touches (checked by {@link NullErasure} at the start of each tick). */
    boolean discardsNow() {
        return discards;
    }

    /** The erase pass, run by {@link NullErasure} before any entity ticks. */
    void eraseTouching(ServerLevel server) {
        Entity owner;
        if (isAnchoredCharge()) owner = server.getEntity(entityData.get(CHARGE_OWNER));
        else owner = ownerId == null ? null : server.getEntity(ownerId);
        hitEntities(server, owner, getRadius());
    }

    /** Living things within range are dragged to the center; inside the NULL they're held there. */
    private void pullEntities(@Nullable Entity owner, double r) {
        double range = Math.max(4.0, r * PULL_RANGE_MULT);
        AABB box = new AABB(position(), position()).inflate(range);
        for (Entity e : level().getEntities(this, box, Entity::isAlive)) {
            if (!(e instanceof LivingEntity) || isHost(owner, e)) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 to = position().subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double dist = to.length();
            if (dist > range) continue;
            if (dist <= Math.max(r, 1.0)) {
                // Held in the middle.
                e.setDeltaMovement(to.scale(0.35));
            } else {
                double strength = pull * (1.0 + (range - dist) / range);
                e.setDeltaMovement(e.getDeltaMovement().add(to.scale(strength / dist)));
            }
            e.hurtMarked = true;
            e.resetFallDistance();
        }
    }

    // ---- blocks --------------------------------------------------------------------------------

    private void eraseBlocks(ServerLevel server, float r) {
        BlockPos center = BlockPos.containing(position());
        int ir = (int) Math.ceil(r);
        int side = ir * 2 + 1;
        int total = side * side * side;
        // A new sweep starts from wherever the NULL is now.
        if (scanCenter == null || scanCursor >= total) {
            scanCenter = center;
            scanCursor = 0;
        }
        double r2 = r < 1 ? 0.25 : (double) r * r;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int end = Math.min(total, scanCursor + SCAN_BUDGET);
        for (; scanCursor < end; scanCursor++) {
            int i = scanCursor;
            int dx = i % side - ir;
            int dy = (i / side) % side - ir;
            int dz = i / (side * side) - ir;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            p.set(scanCenter.getX() + dx, scanCenter.getY() + dy, scanCenter.getZ() + dz);
            if (server.isOutsideBuildHeight(p) || !server.hasChunkAt(p)) continue;
            BlockState state = server.getBlockState(p);
            if (state.isAir()) continue;
            if (!discards && state.getDestroySpeed(server, p) < 0) continue; // unbreakable needs a discarding NULL
            BlockEntity be = server.getBlockEntity(p);
            if (be != null) Clearable.tryClear(be);
            // No neighbor updates: sand stays floating, fluids don't rush in. The hole is just gone.
            server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (ownerId != null) NullCharge.stop(ownerId, this);
        super.remove(reason);
    }

    private void collapse() {
        if (level() instanceof ServerLevel server) {
            float r = getRadius();
            server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 40 + (int) (r * 10), r * 0.6, r * 0.6, r * 0.6, 0.15);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 0.4f);
        }
        discard();
    }

    // ---- entity plumbing -----------------------------------------------------------------------

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(getRadius() * 1.5 + 1);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        double d = 128 + getRadius() * 2;
        return distSq < d * d;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        life = tag.getInt("Life");
        age = tag.getInt("Age");
        damage = tag.getFloat("Damage");
        piercing = tag.getBoolean("Piercing");
        discards = tag.getBoolean("Discards");
        pull = tag.getDouble("Pull");
        pierce = tag.getBoolean("Pierce");
        breaks = tag.getBoolean("Breaks");
        setRadius(tag.getFloat("Radius"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putInt("Life", life);
        tag.putInt("Age", age);
        tag.putFloat("Damage", damage);
        tag.putBoolean("Piercing", piercing);
        tag.putBoolean("Discards", discards);
        tag.putDouble("Pull", pull);
        tag.putBoolean("Pierce", pierce);
        tag.putBoolean("Breaks", breaks);
        tag.putFloat("Radius", getRadius());
    }
}
