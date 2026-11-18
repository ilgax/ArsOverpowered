package com.arsop;

import com.arsop.corruption.Corruption;
import com.arsop.effect.ArsOpEffects;
import com.arsop.item.ArsOpItems;
import com.arsop.entity.ArsOpEntities;
import com.arsop.event.ArsOpEvents;
import com.arsop.glyph.ArsOpGlyphs;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ArsOp.MODID)
public class ArsOp {
    public static final String MODID = "ars_op";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArsOp(IEventBus modEventBus, ModContainer modContainer) {
        ArsOpEffects.register(modEventBus);
        ArsOpEntities.register(modEventBus);
        ArsOpItems.register(modEventBus);
        Corruption.register(modEventBus);
        ArsOpEvents.register();
        // Glyphs must be registered in the constructor: Ars Nouveau creates the glyph
        // items from GlyphRegistry during its item RegisterEvent.
        ArsOpGlyphs.register();
    }

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
