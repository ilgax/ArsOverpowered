package com.arsop.mixin;

import com.arsop.possess.PossessionClientState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mob you dream-walk in is simulated on your client like your own player (as vanilla does for a ridden horse):
 * instant movement, no rubber-banding. The server checks and accepts the positions you send.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isControlledByLocalInstance", at = @At("HEAD"), cancellable = true)
    private void arsop$possessed(CallbackInfoReturnable<Boolean> cir) {
        int id = PossessionClientState.mobId;
        if (id < 0) return;
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && self.getId() == id) cir.setReturnValue(true);
    }
}