package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.effect.FallenEffect;
import com.arsop.effect.FallenSilence;
import com.arsop.network.FallenClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Client half of Fallen's silence: sounds generated locally (music, ambience, weather, other
 * entities' client-side sounds) are cancelled, except the player's own and the caster's.
 */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public final class FallenSoundMute {
    private FallenSoundMute() {}

    private static boolean wasFallen = false;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        SoundInstance sound = event.getSound();
        if (sound == null || mc.player == null || mc.level == null || !FallenEffect.isFallen(mc.player)) return;
        if (!allowed(mc, sound)) event.setSound(null);
    }

    private static boolean allowed(Minecraft mc, SoundInstance sound) {
        if (sound.isRelative()) return false; // music, UI, biome ambience loops
        Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        if (FallenSilence.isNear(mc.player, pos, FallenSilence.OWN_RADIUS)) return true;
        Entity caster = FallenClientState.casterId >= 0 ? mc.level.getEntity(FallenClientState.casterId) : null;
        return caster != null && FallenSilence.isNear(caster, pos, FallenSilence.CASTER_RADIUS);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean fallen = mc.player != null && FallenEffect.isFallen(mc.player);
        if (fallen && !wasFallen) {
            // Cut what's already playing. PLAYERS is kept so the Fall impact and own sounds finish.
            mc.getMusicManager().stopPlaying();
            for (SoundSource source : SoundSource.values()) {
                if (source != SoundSource.PLAYERS && source != SoundSource.MASTER) {
                    mc.getSoundManager().stop(null, source);
                }
            }
        } else if (!fallen && wasFallen) {
            FallenClientState.casterId = -1;
        }
        wasFallen = fallen;
    }
}
