package net.arathain.tot.mixin;

import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.pack.ResourcePack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Mixin(NamespaceResourceManager.class)
public interface NamespaceResourceManagerAccessor {
    @Accessor("packList")
    List<ResourcePack> repurposedstructures_getFallbacks();

    @Invoker("open")
    InputStream repurposedstructures_callGetWrappedResource(Identifier id, ResourcePack pack) throws IOException;
}
