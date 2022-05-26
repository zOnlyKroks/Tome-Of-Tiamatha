package net.arathain.tot.common.world.structures;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.FeatureConfig;

import java.util.concurrent.ConcurrentHashMap;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public class StructUtils {

    private static ConcurrentHashMap<FeatureConfig, Identifier> CACHED_CONFIG_TO_CSF_RL = new ConcurrentHashMap<>();

    public static Identifier getCsfNameForConfig(FeatureConfig config, DynamicRegistryManager registries) {
        return CACHED_CONFIG_TO_CSF_RL.computeIfAbsent(config, c -> registries.get(Registry.CONFIGURED_STRUCTURE_FEATURE_KEY)
                .getEntries().stream().filter(entry -> entry.getValue().config == config).findFirst().get().getKey().getRegistry());
    }

    public static int getMaxTerrainLimit(ChunkGenerator chunkGenerator) {
        return chunkGenerator.getMinimumY() + chunkGenerator.getWorldHeight();
    }

}
