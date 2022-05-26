package net.arathain.tot.common.world.structures.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Holder;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.gen.structure.StructureSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public class DungeonStructureConfig extends RSAdvancedConfig{

    public static final Codec<DungeonStructureConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StructurePool.CODEC.fieldOf("start_pool").forGetter(config -> config.startPool),
            Codec.intRange(0, 30).fieldOf("size").forGetter(config -> config.size),
            Codec.INT.fieldOf("max_y").orElse(Integer.MAX_VALUE).forGetter(config -> config.maxY),
            Codec.INT.fieldOf("min_y").orElse(Integer.MIN_VALUE).forGetter(config -> config.minY),
            Codec.BOOL.fieldOf("do_not_remove_out_of_bounds_pieces").orElse(false).forGetter(config -> config.clipOutOfBoundsPieces),
            Codec.INT.optionalFieldOf("vertical_distance_from_start_piece").forGetter(config -> config.verticalRange),
            Codec.intRange(0, 100).fieldOf("valid_biome_radius_check").orElse(0).forGetter(config -> config.biomeRadius),
            Codec.intRange(0, 100).fieldOf("structure_set_avoid_radius_check").orElse(0).forGetter(config -> config.structureAvoidRadius),
            RegistryKey.codec(Registry.STRUCTURE_SET_WORLDGEN).listOf().fieldOf("structure_set_to_avoid").orElse(new ArrayList<>()).forGetter(config -> config.structureSetToAvoid),
            Identifier.CODEC.listOf().fieldOf("pools_that_ignore_boundaries").orElse(new ArrayList<>()).xmap(HashSet::new, ArrayList::new).forGetter(config -> config.poolsThatIgnoreBoundaries)
    ).apply(instance, DungeonStructureConfig::new));

    public DungeonStructureConfig(StructurePool startPool, int size,
                                  int maxY, int minY, boolean clipOutOfBoundsPieces,
                                  Optional<Integer> verticalRange, int biomeRadius, int structureAvoidRadius,
                                  List<RegistryKey<StructureSet>> structureSetToAvoid,
                                  HashSet<Identifier> poolsThatIgnoreBoundaries) {
        super(startPool, size, maxY, minY, clipOutOfBoundsPieces, verticalRange, biomeRadius, structureAvoidRadius, structureSetToAvoid, poolsThatIgnoreBoundaries);
    }

}
