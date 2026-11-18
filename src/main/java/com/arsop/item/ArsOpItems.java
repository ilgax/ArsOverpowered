package com.arsop.item;

import com.arsop.ArsOp;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ArsOpItems {
    private ArsOpItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArsOp.MODID);

    public static final DeferredItem<Item> CODEX_OF_RUIN = ITEMS.register("codex_of_ruin", () -> new CodexOfRuin());
    public static final DeferredItem<Item> CREATIVE_CODEX_OF_RUIN =
            ITEMS.register("creative_codex_of_ruin", () -> new CodexOfRuin(SpellTier.CREATIVE));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ArsOpItems::onCreativeTabs);
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(CODEX_OF_RUIN);
            event.accept(CREATIVE_CODEX_OF_RUIN);
        }
    }
}