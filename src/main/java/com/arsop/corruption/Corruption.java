package com.arsop.corruption;

import com.arsop.ArsOp;
import com.arsop.item.CodexOfRuin;
import com.arsop.network.CorruptionPayload;
import com.arsop.possess.Possession;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Corruption meter (0-100), kept on the player across deaths. The Codex of Ruin feeds it; it drains slowly while
 * the Codex is put away.
 * <ul>
 *   <li>25+: whispers.</li>
 *   <li>50+: constant Hunger.</li>
 *   <li>75+: Weakness and pulses of Darkness.</li>
 *   <li>100: backlash - heavy damage and Wither, then the meter drops to 75.</li>
 * </ul>
 * Spells cast from the Codex deal 1.25x damage, rising to 1.75x at full corruption.
 */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class Corruption {
    private Corruption() {}

    public static final int MAX = 100;
    public static final int WHISPERS = 25;
    public static final int HUNGER = 50;
    public static final int DARKNESS = 75;
    public static final int AFTER_BACKLASH = 75;
    /** Ticks per point of decay while the Codex is not held. */
    public static final int DECAY_TICKS = 200;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ArsOp.MODID);

    public static final Supplier<AttachmentType<Integer>> CORRUPTION = ATTACHMENTS.register("corruption",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static int get(Player player) {
        return player.getData(CORRUPTION);
    }

    public static void add(Player player, int amount) {
        set(player, get(player) + amount);
    }

    public static void set(Player player, int value) {
        int old = get(player);
        int next = Mth.clamp(value, 0, MAX);
        if (next == old) return;
        player.setData(CORRUPTION, next);
        if (player instanceof ServerPlayer sp) sync(sp);
    }

    /** Damage multiplier for spells cast from the Codex. */
    public static final float MAX_POWER = 1.75f;

    public static float powerMultiplier(Player player) {
        return 1.25f + (MAX_POWER - 1.25f) * get(player) / MAX;
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CorruptionPayload(get(player)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int value = get(player);
        if (value <= 0) return;

        boolean engaged = (CodexOfRuin.isHolding(player) && !CodexOfRuin.isCreativeCodex(player.getMainHandItem())
                && !CodexOfRuin.isCreativeCodex(player.getOffhandItem())) || Possession.isCorrupting(player);
        if (!engaged && player.tickCount % DECAY_TICKS == 0) {
            set(player, value - 1);
            return;
        }
        if (player.isCreative() || player.isSpectator()) return;

        RandomSource random = player.getRandom();
        if (value >= WHISPERS && random.nextInt(600) == 0) {
            player.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8f, 0.5f + random.nextFloat() * 0.3f);
        }
        if (value >= HUNGER && player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 0, true, false, true));
        }
        if (value >= DARKNESS) {
            if (player.tickCount % 100 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, true, false, true));
            }
            if (random.nextInt(400) == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false, true));
            }
        }
        if (value >= MAX) backlash(player);
    }

    private static void backlash(ServerPlayer player) {
        player.hurt(player.damageSources().magic(), 8f);
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
        player.playNotifySound(SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(Component.translatable("ars_op.corruption.backlash").withStyle(ChatFormatting.DARK_RED), false);
        set(player, AFTER_BACKLASH);
    }
}