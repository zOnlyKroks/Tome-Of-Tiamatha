package net.arathain.tot.mixin;

import net.minecraft.resource.MultiPackResourceManager;
import net.minecraft.resource.NamespaceResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MultiPackResourceManager.class)
public interface ReloadableResourceManagerImplAccessor {
    @Accessor("namespaceManagers")
    Map<String, NamespaceResourceManager> tome_of_timanthia_getNamespacedManagers();
}
