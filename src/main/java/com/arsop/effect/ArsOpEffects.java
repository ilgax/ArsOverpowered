package com.arsop.effect;

import com.arsop.ArsOp;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ArsOpEffects {
    private ArsOpEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ArsOp.MODID);

    /**
     * Fallen I (amplifier 0) = crushed: crawl, weaker hits, no healing.
     * Fallen II (amplifier 1) = pinned: no movement, no damage dealt, no healing.
     * Movement is enforced in {@link FallenMovement}, not with speed attributes.
     */
    public static final DeferredHolder<MobEffect, FallenEffect> FALLEN = EFFECTS.register("fallen", () ->
            (FallenEffect) new FallenEffect().addAttributeModifier(Attributes.ATTACK_SPEED, ArsOp.prefix("fallen_attack_speed"),
                    FallenEffect.CRUSHED_ATTACK_SPEED - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
