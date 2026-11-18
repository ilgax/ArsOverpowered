package com.arsop.glyph.augment;

import com.arsop.ArsOp;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A "Greater" augment: takes one slot, but when the spell is cast it becomes {@link #copies()} copies of
 * {@link #base} (see {@link GreaterAugments#expand}). Every glyph sees plain AOE/Split/... so all glyphs and forms
 * work with it, mana is charged for the copies, and each glyph's own augment limit still caps the result.
 */
public class AugmentGreater extends AbstractAugment {
    public static final int DEFAULT_COPIES = 2;

    public final AbstractAugment base;
    private final String baseName;
    public ModConfigSpec.IntValue COPIES;

    public AugmentGreater(String id, String name, AbstractAugment base, String baseName) {
        super(ArsOp.prefix(id), name);
        this.base = base;
        this.baseName = baseName;
    }

    public int copies() {
        return COPIES == null ? DEFAULT_COPIES : COPIES.get();
    }

    @Override
    public void buildConfig(ModConfigSpec.Builder builder) {
        super.buildConfig(builder);
        COPIES = builder.comment("How many " + baseName + " augments one " + getName() + " counts as when the spell is cast.")
                .defineInRange("copies", DEFAULT_COPIES, 1, 16);
    }

    /** Only shown in the book; the real cost is the copies' cost, charged on the expanded spell. */
    @Override
    public int getDefaultManaCost() {
        return 0;
    }

    @Override
    public SpellTier defaultTier() {
        return SpellTier.THREE;
    }

    @NotNull
    @Override
    public Set<AbstractAugment> getCompatibleAugments() {
        return augmentSetOf();
    }

    @Override
    public String getBookDescription() {
        return "Counts as " + DEFAULT_COPIES + " " + baseName + " augments (configurable) while taking a single slot. "
                + "Costs the same mana as those augments, and never goes past a glyph's own " + baseName + " limit.";
    }
}
