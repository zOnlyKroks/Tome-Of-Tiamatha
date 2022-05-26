package net.arathain.tot.common.world.structures.util;

import com.mojang.serialization.Codec;
import net.arathain.tot.TomeOfTiamatha;
import net.arathain.tot.common.world.structures.PieceLimitedJigsawManager;
import net.arathain.tot.common.world.structures.StructUtils;
import net.minecraft.structure.StructurePiecesGenerator;
import net.minecraft.structure.StructurePiecesGeneratorFactory;
import net.minecraft.util.Holder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.source.CheckerboardBiomeSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.LegacySimpleRandom;
import net.minecraft.world.gen.SingleThreadedRandom;
import net.minecraft.world.gen.WorldGenRandom;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.StructurePoolFeatureConfig;
import net.minecraft.world.gen.structure.StructureSet;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public class AdvancedJigsawStructure <C extends RSAdvancedConfig> extends AbstractBaseStructure<C> {

    public AdvancedJigsawStructure(Codec<C> codec) {
        super(codec, AdvancedJigsawStructure::isAdvancedFeatureChunk, AdvancedJigsawStructure::generateAdvancedPieces);
    }

    public AdvancedJigsawStructure(Codec<C> codec, Predicate<StructurePiecesGeneratorFactory.Context<C>> locationCheckPredicate, Function<StructurePiecesGeneratorFactory.Context<C>, Optional<StructurePiecesGenerator<C>>> pieceCreationPredicate) {
        super(codec, locationCheckPredicate, pieceCreationPredicate);
    }

    protected static <CC extends RSAdvancedConfig> boolean isAdvancedFeatureChunk(StructurePiecesGeneratorFactory.Context<CC> context) {
        ChunkPos chunkPos = context.chunkPos();
        CC config = context.config();

        if (!(context.biomeSource() instanceof CheckerboardBiomeSource)) {
            for (int curChunkX = chunkPos.x - config.biomeRadius; curChunkX <= chunkPos.x + config.biomeRadius; curChunkX++) {
                for (int curChunkZ = chunkPos.z - config.biomeRadius; curChunkZ <= chunkPos.z + config.biomeRadius; curChunkZ++) {
                    ChunkRandom random = new ChunkRandom(new LegacySimpleRandom(0L));
                    random.setPopulationSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);
                    int structureStartHeight = random.nextInt(config.maxY - config.minY) + config.minY;
                    if (!context.validBiome().test(context.chunkGenerator().method_16359(curChunkX << 2, structureStartHeight >> 2, curChunkZ << 2))) {
                        return false;
                    }
                }
            }
        }

        //cannot be near other specified structure
        for (RegistryKey<StructureSet> structureSetToAvoid : config.structureSetToAvoid) {
            if (context.chunkGenerator().method_41053(structureSetToAvoid, context.seed(), chunkPos.x, chunkPos.z, config.structureAvoidRadius)) {
                return false;
            }
        }

        return true;
    }

    public static <CC extends RSAdvancedConfig> Optional<StructurePiecesGenerator<CC>> generateAdvancedPieces(StructurePiecesGeneratorFactory.Context<CC> context) {
        BlockPos.Mutable blockpos = new BlockPos.Mutable(context.chunkPos().x, 0, context.chunkPos().z);
        CC config = context.config();

        if (config.maxY - config.minY <= 0) {
            TomeOfTiamatha.LOGGER.error("MinY should always be less than MaxY or else a crash will occur or no pieces will spawn. Problematic structure is:" + config.startPool.getId());
        }
        ChunkRandom random = new ChunkRandom(new LegacySimpleRandom(0L));
        random.setPopulationSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);
        int structureStartHeight = random.nextInt(config.maxY - config.minY) + config.minY;
        blockpos.move(Direction.UP, structureStartHeight);

        int topClipOff;
        int bottomClipOff;
        if (config.verticalRange.isEmpty()) {
            // Help make sure the Jigsaw Blocks have room to spawn new pieces if structure is right on edge of maxY or topYLimit
            topClipOff = config.clipOutOfBoundsPieces ? config.maxY + 5 : Integer.MAX_VALUE;
            bottomClipOff = config.clipOutOfBoundsPieces ? config.minY - 5 : Integer.MIN_VALUE;
        } else {
            topClipOff = structureStartHeight + config.verticalRange.get();
            bottomClipOff = structureStartHeight - config.verticalRange.get();
        }

        return PieceLimitedJigsawManager.assembleJigsawStructure(
                context,
                new StructurePoolFeatureConfig(Holder.createDirect(config.startPool), config.size),
                StructUtils.getCsfNameForConfig(config, context.registryManager()),
                blockpos,
                false,
                false,
                topClipOff,
                bottomClipOff,
                config.poolsThatIgnoreBoundaries,
                (structurePiecesBuilder, pieces) -> {
                });
    }
}
