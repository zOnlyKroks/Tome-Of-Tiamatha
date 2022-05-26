package net.arathain.tot.common.world.structures.stc;

import com.mojang.serialization.Codec;
import net.arathain.tot.TomeOfTiamatha;
import net.arathain.tot.common.world.structures.PieceLimitedJigsawManager;
import net.arathain.tot.common.world.structures.StructUtils;
import net.arathain.tot.common.world.structures.util.AdvancedJigsawStructure;
import net.arathain.tot.common.world.structures.util.DungeonStructureConfig;
import net.minecraft.structure.StructurePiecesGenerator;
import net.minecraft.structure.StructurePiecesGeneratorFactory;
import net.minecraft.structure.piece.PoolStructurePiece;
import net.minecraft.util.Holder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.LegacySimpleRandom;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.StructurePoolFeatureConfig;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public class DungeonStructure <C extends DungeonStructureConfig> extends AdvancedJigsawStructure<C> {

    public DungeonStructure(Codec<C> codec) {
        super(codec, DungeonStructure::isMineshaftFeatureChunk, DungeonStructure::generateMineshaftPieces);
    }

    public DungeonStructure(Codec<C> codec, Predicate<StructurePiecesGeneratorFactory.Context<C>> locationCheckPredicate, Function<StructurePiecesGeneratorFactory.Context<C>, Optional<StructurePiecesGenerator<C>>> pieceCreationPredicate) {
        super(codec, locationCheckPredicate, pieceCreationPredicate);
    }

    protected static <CC extends DungeonStructureConfig> boolean isMineshaftFeatureChunk(StructurePiecesGeneratorFactory.Context<CC> context) {
        ChunkRandom worldgenRandom = new ChunkRandom(new LegacySimpleRandom(0L));
        worldgenRandom.setPopulationSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);

        Holder<Biome> biomeAtSpot = context.chunkGenerator().method_16359(BiomeCoords.fromBlock(context.chunkPos().getCenterX()), BiomeCoords.fromBlock(50), BiomeCoords.fromBlock(context.chunkPos().getCenterZ()));
        return context.validBiome().test(biomeAtSpot) && AdvancedJigsawStructure.isAdvancedFeatureChunk(context);
    }

    public static <CC extends DungeonStructureConfig> Optional<StructurePiecesGenerator<CC>> generateMineshaftPieces(StructurePiecesGeneratorFactory.Context<CC> context) {
        BlockPos.Mutable blockpos = new BlockPos.Mutable(context.chunkPos().x, 0, context.chunkPos().z);
        CC config = context.config();

        if(config.maxY - config.minY <= 0) {
            TomeOfTiamatha.LOGGER.error("MinY should always be less than MaxY or else a crash will occur or no pieces will spawn. Problematic structure is:" + config.startPool.getId());
        }
        ChunkRandom random = new ChunkRandom(new LegacySimpleRandom(0L));
        random.setPopulationSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);
        int structureStartHeight = random.nextInt(config.maxY - config.minY) + config.minY;
        blockpos.move(Direction.UP, structureStartHeight);

        int topClipOff;
        int bottomClipOff;
        if(config.verticalRange.isEmpty()) {
            // Help make sure the Jigsaw Blocks have room to spawn new pieces if structure is right on edge of maxY or topYLimit
            topClipOff = config.clipOutOfBoundsPieces ? config.maxY + 5 : Integer.MAX_VALUE;
            bottomClipOff = config.clipOutOfBoundsPieces ? config.minY - 5 : Integer.MIN_VALUE;
        }
        else {
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
                    int justBelowTerrain = getTerrainHeight(context.chunkPos().getCenterAtY(0), context.chunkGenerator(), context.heightLimitView()) - 15;
                    int finalJustBelowTerrain = Math.max(justBelowTerrain, bottomClipOff);
                    Optional<PoolStructurePiece> topPiece = pieces.stream().max(Comparator.comparingInt(piece -> piece.getBoundingBox().getMaxY()));
                    if(topPiece.isPresent() && finalJustBelowTerrain < topClipOff && finalJustBelowTerrain < topPiece.get().getBoundingBox().getMaxY()) {
                        int topPieceMaxY = topPiece.get().getBoundingBox().getMaxY();
                        pieces.forEach(piece -> piece.translate(0, finalJustBelowTerrain - topPieceMaxY, 0));
                    }
                });
    }

    private static int getTerrainHeight(BlockPos centerPos, ChunkGenerator chunkGenerator, HeightLimitView heightLimitView) {
        int height = chunkGenerator.getSpawnHeight(heightLimitView);

        BlockPos pos = new BlockPos(centerPos.getX(), StructUtils.getMaxTerrainLimit(chunkGenerator), centerPos.getZ());
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for(Direction direction : Direction.Type.HORIZONTAL) {
            mutable.set(pos).move(direction, 16);
            height = Math.min(height, chunkGenerator.getSpawnHeight(heightLimitView));
        }

        return height;
    }

}
