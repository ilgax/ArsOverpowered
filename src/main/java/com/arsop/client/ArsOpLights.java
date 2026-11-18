package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.entity.ArsOpEntities;
import com.hollingsworth.arsnouveau.common.light.LightManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Hooks NULLs into Ars Nouveau's built-in dynamic lights (client config: lights.lightsEnabled). */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public final class ArsOpLights {
    private ArsOpLights() {}

    public static final int NULL_LIGHT = 15;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> LightManager.register(ArsOpEntities.NULL.get(), e -> NULL_LIGHT));
    }
}
