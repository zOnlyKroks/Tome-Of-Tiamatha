package net.arathain.tot.mixin;

import net.arathain.tot.common.world.structures.JSONConditionsRegistry;
import net.minecraft.util.registry.BuiltinRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltinRegistries.class)
public class BuiltinRegistiresMixin {

    /**
     * Creates and inits our custom registry at game startup
     * @author TelepathicGrunt
     */
    @Inject(method = "init",
            at = @At(value = "HEAD"))
    private static void repurposedstructures_initCustomRegistries(CallbackInfo ci) {
        JSONConditionsRegistry.createJSONConditionsRegistry();
    }

}
