package net.arathain.tot.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.processor.StructureProcessorList;
import net.minecraft.util.Holder;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {
    @Accessor("location")
    Either<Identifier, Structure> repurposedstructures_getTemplate();

    @Accessor("processors")
    Holder<StructureProcessorList> repurposedstructures_getProcessors();

    @Invoker("method_27233")
    Structure callGetTemplate(StructureManager structureManager);
}
