package com.arsop.item;

import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Codex of Ruin: an archmage-tier spellbook that can cast forbidden glyphs. Its spells grow stronger with the
 * holder's corruption,.
 */
public class CodexOfRuin extends SpellBook {
    public static final int SPELL_SLOTS = 10;
    /** Extra glyph slots per spell on top of Ars's 10. */
    public static final int BONUS_GLYPH_SLOTS = 10;
    /** Extra height of the Codex spell screen: one more row of glyph cells. */
    public static final int BOOK_EXTRA_HEIGHT = 24;

    public CodexOfRuin() {
        this(SpellTier.THREE);
    }

    public CodexOfRuin(SpellTier tier) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                .component(DataComponents.BASE_COLOR, DyeColor.RED)
                .component(DataComponentRegistry.SPELL_CASTER, new SpellCaster(SPELL_SLOTS)), tier);
    }

    public static boolean isCodex(ItemStack stack) {
        return stack != null && stack.getItem() instanceof CodexOfRuin;
    }

    /** The creative Codex: no corruption, full power. */
    public static boolean isCreativeCodex(ItemStack stack) {
        return stack != null && stack.getItem() instanceof CodexOfRuin codex && codex.getTier() == SpellTier.CREATIVE;
    }

    @Override
    public void createGeoRenderer(java.util.function.Consumer<software.bernie.geckolib.animatable.client.GeoRenderProvider> consumer) {
        consumer.accept(new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new com.arsop.client.CodexRenderer();
                return renderer;
            }
        });
    }

    public static boolean isHolding(Player player) {
        return isCodex(player.getMainHandItem()) || isCodex(player.getOffhandItem());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        // Upgrading copies the old book's color; the Codex is always red (its renderer draws its own cover anyway).
        if (!level.isClientSide && stack.get(DataComponents.BASE_COLOR) != DyeColor.RED) {
            stack.set(DataComponents.BASE_COLOR, DyeColor.RED);
        }
        // Codex books made while it briefly had 20 spell slots go back to 10.
        SpellCaster caster = stack.get(DataComponentRegistry.SPELL_CASTER);
        if (!level.isClientSide && caster != null && caster.getMaxSlots() > SPELL_SLOTS) {
            stack.set(DataComponentRegistry.SPELL_CASTER, caster.setMaxSlots(SPELL_SLOTS));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("ars_op.codex.tooltip.forbidden").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("ars_op.codex.tooltip.power").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable(getTier() == SpellTier.CREATIVE ? "ars_op.codex.tooltip.creative" : "ars_op.codex.tooltip.possess").withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}