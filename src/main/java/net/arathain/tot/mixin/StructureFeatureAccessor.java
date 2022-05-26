package net.arathain.tot.mixin;

import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.StructureFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(StructureFeature.class)
public interface StructureFeatureAccessor {

    @Invoker
    static <F extends StructureFeature<?>> F callRegister(String name, F structureFeature, GenerationStep.Feature step) {
        throw new UnsupportedOperationException();
    }

    @Accessor("STRUCTURE_TO_GENERATION_STEP")
    static Map<StructureFeature<?>, GenerationStep.Feature> getSTEP() {
        throw new UnsupportedOperationException();
    }

}
