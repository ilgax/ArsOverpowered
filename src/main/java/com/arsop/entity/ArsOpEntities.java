package com.arsop.entity;

import com.arsop.ArsOp;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ArsOpEntities {
    private ArsOpEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ArsOp.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<NullEntity>> NULL = ENTITIES.register("null_singularity", () ->
            EntityType.Builder.<NullEntity>of(NullEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSummon()
                    .build("null_singularity"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
