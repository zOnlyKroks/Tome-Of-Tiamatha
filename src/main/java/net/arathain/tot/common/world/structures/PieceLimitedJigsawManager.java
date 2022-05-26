package net.arathain.tot.common.world.structures;

import com.google.common.collect.Queues;
import com.mojang.datafixers.util.Pair;
import net.arathain.tot.TomeOfTiamatha;
import net.arathain.tot.mixin.SinglePoolElementAccessor;
import net.arathain.tot.mixin.StructurePoolAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.JigsawBlock;
import net.minecraft.block.enums.JigsawOrientation;
import net.minecraft.structure.*;
import net.minecraft.structure.piece.PoolStructurePiece;
import net.minecraft.structure.piece.StructurePiece;
import net.minecraft.structure.pool.*;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.LegacySimpleRandom;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.StructurePoolFeatureConfig;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.*;
import java.util.function.BiConsumer;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public class PieceLimitedJigsawManager {
    // Record for entries
    public record Entry(PoolStructurePiece piece, MutableObject<BoxOctree> boxOctreeMutableObject, int topYLimit, int depth) { }

    public static <C extends FeatureConfig> Optional<StructurePiecesGenerator<C>> assembleJigsawStructure(
            StructurePiecesGeneratorFactory.Context<C> context,
            StructurePoolFeatureConfig jigsawConfig,
            Identifier structureID,
            BlockPos startPos,
            boolean doBoundaryAdjustments,
            boolean useHeightmap,
            int maxY,
            int minY,
            BiConsumer<StructurePiecesGenerator, List<PoolStructurePiece>> structureBoundsAdjuster
    ) {
        return assembleJigsawStructure(context, jigsawConfig, structureID, startPos, doBoundaryAdjustments, useHeightmap, maxY, minY, new HashSet<>(), structureBoundsAdjuster);
    }

    public static <C extends FeatureConfig> Optional<StructurePiecesGenerator<C>> assembleJigsawStructure(
            StructurePiecesGeneratorFactory.Context<C> context,
            StructurePoolFeatureConfig jigsawConfig,
            Identifier structureID,
            BlockPos startPos,
            boolean doBoundaryAdjustments,
            boolean useHeightmap,
            int maxY,
            int minY,
            Set<Identifier> poolsThatIgnoreBounds,
            BiConsumer<StructurePiecesGenerator, List<PoolStructurePiece>> structureBoundsAdjuster
    ) {
        // Get jigsaw pool registry
        Registry<StructurePool> jigsawPoolRegistry = context.registryManager().get(Registry.STRUCTURE_POOL_KEY);

        // Get a random orientation for the starting piece
        ChunkRandom random = new ChunkRandom(new LegacySimpleRandom(0L));
        random.setPopulationSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);
        BlockRotation rotation = BlockRotation.random(random);

        // Get starting pool
        StructurePool startPool = jigsawConfig.startPool.value();
        if(startPool == null || startPool.getElementCount() == 0) {
            throw new RuntimeException("Repurposed Structures: Empty or nonexistent start pool in structure: " + structureID + " Crash is imminent");
        }

        // Grab a random starting piece from the start pool. This is just the piece design itself, without rotation or position information.
        // Think of it as a blueprint.
        StructurePoolElement startPieceBlueprint = startPool.getRandomElement(random);
        if (startPieceBlueprint == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }

        // Instantiate a piece using the "blueprint" we just got.
        PoolStructurePiece startPiece = new PoolStructurePiece(
                context.structureManager(),
                startPieceBlueprint,
                startPos,
                startPieceBlueprint.getGroundLevelDelta(),
                rotation,
                startPieceBlueprint.getBoundingBox(context.structureManager(), startPos, rotation)
        );

        // Store center position of starting piece's bounding box
        BlockBox pieceBoundingBox = startPiece.getBoundingBox();
        int pieceCenterX = (pieceBoundingBox.getMaxX() + pieceBoundingBox.getMinX()) / 2;
        int pieceCenterZ = (pieceBoundingBox.getMaxZ() + pieceBoundingBox.getMinZ()) / 2;
        int pieceCenterY = useHeightmap
                ? startPos.getY() + context.chunkGenerator().getHeightInGround(pieceCenterX, pieceCenterZ, Heightmap.Type.WORLD_SURFACE_WG, context.heightLimitView())
                : startPos.getY();

        int yAdjustment = pieceBoundingBox.getMinY() + startPiece.getGroundLevelDelta();
        startPiece.translate(0, pieceCenterY - yAdjustment, 0);
        if (!context.validBiome().test(context.chunkGenerator().method_16359(BiomeCoords.fromBlock(pieceCenterX), BiomeCoords.fromBlock(pieceCenterY), BiomeCoords.fromBlock(pieceCenterZ)))) {
            return Optional.empty();
        }

        return Optional.of((structurePiecesBuilder, contextx) -> {
            List<PoolStructurePiece> components = new ArrayList<>();
            components.add(startPiece);
            Map<Identifier, StructurePieceCountsManager.RequiredPieceNeeds> requiredPieces = TomeOfTiamatha.structurePieceCountsManager.getRequirePieces(structureID);
            boolean runOnce = requiredPieces == null || requiredPieces.isEmpty();
            Map<Identifier, Integer> currentPieceCounter = new HashMap<>();
            for (int attempts = 0; runOnce || doesNotHaveAllRequiredPieces(components, requiredPieces, currentPieceCounter); attempts++) {
                if (attempts == 100) {
                    break;
                }

                components.clear();
                components.add(startPiece); // Add start piece to list of pieces

                if (jigsawConfig.getSize() > 0) {
                    Box axisAlignedBB = new Box(pieceCenterX - 80, pieceCenterY - 120, pieceCenterZ - 80, pieceCenterX + 80 + 1, pieceCenterY + 180 + 1, pieceCenterZ + 80 + 1);
                    BoxOctree boxOctree = new BoxOctree(axisAlignedBB); // The maximum boundary of the entire structure
                    boxOctree.addBox(Box.from(pieceBoundingBox));
                    Entry startPieceEntry = new Entry(startPiece, new MutableObject<>(boxOctree), pieceCenterY + 80, 0);

                    Assembler assembler = new Assembler(structureID, jigsawPoolRegistry, jigsawConfig.getSize(), context, components, random, requiredPieces, maxY, minY, poolsThatIgnoreBounds);
                    assembler.availablePieces.addLast(startPieceEntry);

                    while (!assembler.availablePieces.isEmpty()) {
                        Entry entry = assembler.availablePieces.removeFirst();
                        assembler.generatePiece(entry.piece, entry.boxOctreeMutableObject, entry.topYLimit, entry.depth, doBoundaryAdjustments, context.heightLimitView());
                    }
                }

                if (runOnce) break;
            }

            components.forEach(structurePiecesBuilder::addPiece);

            // Do not generate if out of bounds
            if(structurePiecesBuilder.getBoundingBox().getMaxY() > context.heightLimitView().getTopY()) {
                structurePiecesBuilder.clear();
            }
        });
    }

    private static boolean doesNotHaveAllRequiredPieces(List<? extends StructurePiece> components, Map<Identifier, StructurePieceCountsManager.RequiredPieceNeeds> requiredPieces, Map<Identifier, Integer> counter) {
        counter.clear();
        requiredPieces.forEach((key, value) -> counter.put(key, value.getRequiredAmount()));
        for(Object piece : components) {
            if(piece instanceof PoolStructurePiece) {
                StructurePoolElement poolElement = ((PoolStructurePiece)piece).getPoolElement();
                if(poolElement instanceof SinglePoolElement) {
                    Identifier pieceID = ((SinglePoolElementAccessor) poolElement).repurposedstructures_getTemplate().left().orElse(null);
                    if(counter.containsKey(pieceID)) {
                        counter.put(pieceID, counter.get(pieceID) - 1);
                    }
                }
            }
        }

        return counter.values().stream().anyMatch(count -> count > 0);
    }


    public static final class Assembler {
        private final Registry<StructurePool> poolRegistry;
        private final int maxDepth;
        private final ChunkGenerator chunkGenerator;
        private final StructureManager structureManager;
        private final List<PoolStructurePiece> structurePieces;
        private final Random rand;
        public final Deque<Entry> availablePieces = Queues.newArrayDeque();
        private final Map<Identifier, Integer> currentPieceCounts;
        private final Map<Identifier, Integer> maximumPieceCounts;
        private final Map<Identifier, StructurePieceCountsManager.RequiredPieceNeeds> requiredPieces;
        private final int maxY;
        private final int minY;
        private final Set<Identifier> poolsThatIgnoreBounds;

        public <C extends FeatureConfig> Assembler(Identifier structureID, Registry<StructurePool> poolRegistry, int maxDepth, StructurePiecesGeneratorFactory.Context<C> context, List<PoolStructurePiece> structurePieces, Random rand, Map<Identifier, StructurePieceCountsManager.RequiredPieceNeeds> requiredPieces, int maxY, int minY, Set<Identifier> poolsThatIgnoreBounds) {
            this.poolRegistry = poolRegistry;
            this.maxDepth = maxDepth;
            this.chunkGenerator = context.chunkGenerator();
            this.structureManager = context.structureManager();
            this.structurePieces = structurePieces;
            this.rand = rand;
            this.maxY = maxY;
            this.minY = minY;

            // Create map clone so we do not modify the original map.
            this.requiredPieces = requiredPieces == null ? new HashMap<>() : new HashMap<>(requiredPieces);
            this.maximumPieceCounts = new HashMap<>(TomeOfTiamatha.structurePieceCountsManager.getMaximumCountForPieces(structureID));
            this.poolsThatIgnoreBounds = poolsThatIgnoreBounds;

            // pieceCounts will keep track of how many of the pieces we are checking were spawned
            this.currentPieceCounts = new HashMap<>();
            this.requiredPieces.forEach((key, value) -> this.currentPieceCounts.putIfAbsent(key, 0));
            this.maximumPieceCounts.forEach((key, value) -> this.currentPieceCounts.putIfAbsent(key, 0));
        }

        public void generatePiece(PoolStructurePiece piece, MutableObject<BoxOctree> boxOctree, int minY, int depth, boolean doBoundaryAdjustments, HeightLimitView heightLimitView) {
            // Collect data from params regarding piece to process
            StructurePoolElement pieceBlueprint = piece.getPoolElement();
            BlockPos piecePos = piece.getPos();
            BlockRotation pieceRotation = piece.getRotation();
            BlockBox pieceBoundingBox = piece.getBoundingBox();
            int pieceMinY = pieceBoundingBox.getMinY();
            MutableObject<BoxOctree> parentOctree = new MutableObject<>();

            // Get list of all jigsaw blocks in this piece
            List<Structure.StructureBlockInfo> pieceJigsawBlocks = pieceBlueprint.getStructureBlockInfos(this.structureManager, piecePos, pieceRotation, this.rand);

            for (Structure.StructureBlockInfo jigsawBlock : pieceJigsawBlocks) {
                // Gather jigsaw block information
                Direction direction = JigsawBlock.getFacing(jigsawBlock.state);
                BlockPos jigsawBlockPos = jigsawBlock.pos;
                BlockPos jigsawBlockTargetPos = jigsawBlockPos.offset(direction);

                // Get the jigsaw block's piece pool
                Identifier jigsawBlockPool = new Identifier(jigsawBlock.nbt.getString("pool"));
                Optional<StructurePool> poolOptional = this.poolRegistry.getOrEmpty(jigsawBlockPool);

                // Only continue if we are using the jigsaw pattern registry and if it is not empty
                if (!(poolOptional.isPresent() && (poolOptional.get().getElementCount() != 0 || Objects.equals(jigsawBlockPool, StructurePools.EMPTY.getValue())))) {
                    continue;
                }

                // Get the jigsaw block's fallback pool (which is a part of the pool's JSON)
                Identifier jigsawBlockFallback = poolOptional.get().getTerminatorsId();
                Optional<StructurePool> fallbackOptional = this.poolRegistry.getOrEmpty(jigsawBlockFallback);

                // Only continue if the fallback pool is present and valid
                if (!(fallbackOptional.isPresent() && (fallbackOptional.get().getElementCount() != 0 || Objects.equals(jigsawBlockFallback, StructurePools.EMPTY.getValue())))) {
                    continue;
                }

                // Adjustments for if the target block position is inside the current piece
                boolean isTargetInsideCurrentPiece = pieceBoundingBox.contains(jigsawBlockTargetPos);
                int targetPieceBoundsTop;
                MutableObject<BoxOctree> octreeToUse;
                if (isTargetInsideCurrentPiece) {
                    octreeToUse = parentOctree;
                    targetPieceBoundsTop = pieceMinY;
                    if (parentOctree.getValue() == null) {
                        parentOctree.setValue(new BoxOctree(Box.from(pieceBoundingBox)));
                    }
                }
                else {
                    octreeToUse = boxOctree;
                    targetPieceBoundsTop = minY;
                }

                // Process the pool pieces, randomly choosing different pieces from the pool to spawn
                if (depth != this.maxDepth) {
                    StructurePoolElement generatedPiece = this.processList(new ArrayList<>(((StructurePoolAccessor)poolOptional.get()).getElementCounts()), doBoundaryAdjustments, jigsawBlock, jigsawBlockTargetPos, pieceMinY, jigsawBlockPos, octreeToUse, piece, depth, targetPieceBoundsTop, heightLimitView, false);
                    if (generatedPiece != null) continue; // Stop here since we've already generated the piece
                }

                // Process the fallback pieces in the event none of the pool pieces work
                boolean ignoreBounds = false;
                if(poolsThatIgnoreBounds != null) {
                    ignoreBounds = poolsThatIgnoreBounds.contains(jigsawBlockFallback);
                }
                this.processList(new ArrayList<>(((StructurePoolAccessor)fallbackOptional.get()).getElementCounts()), doBoundaryAdjustments, jigsawBlock, jigsawBlockTargetPos, pieceMinY, jigsawBlockPos, octreeToUse, piece, depth, targetPieceBoundsTop, heightLimitView, ignoreBounds);
            }
        }

        /**
         * Helper function. Searches candidatePieces for a suitable piece to spawn.
         * All other params are intended to be passed directly from {@link Assembler#generatePiece}
         * @return The piece genereated, or null if no suitable pieces were found.
         */
        private StructurePoolElement processList(
                List<Pair<StructurePoolElement, Integer>> candidatePieces,
                boolean doBoundaryAdjustments,
                Structure.StructureBlockInfo jigsawBlock,
                BlockPos jigsawBlockTargetPos,
                int pieceMinY,
                BlockPos jigsawBlockPos,
                MutableObject<BoxOctree> boxOctreeMutableObject,
                PoolStructurePiece piece,
                int depth,
                int targetPieceBoundsTop,
                HeightLimitView heightLimitView,
                boolean ignoreBounds
        ) {
            StructurePool.Projection piecePlacementBehavior = piece.getPoolElement().getProjection();
            boolean isPieceRigid = piecePlacementBehavior == StructurePool.Projection.RIGID;
            int jigsawBlockRelativeY = jigsawBlockPos.getY() - pieceMinY;
            int surfaceHeight = -1; // The y-coordinate of the surface. Only used if isPieceRigid is false.

            int totalCount = candidatePieces.stream().mapToInt(Pair::getSecond).reduce(0, Integer::sum);

            while (candidatePieces.size() > 0) {
                // Prioritize required piece if the following conditions are met:
                // 1. It's a potential candidate for this pool
                // 2. It hasn't already been placed
                // 3. We are at least certain amount of pieces away from the starting piece.
                Pair<StructurePoolElement, Integer> chosenPiecePair = null;
                // Condition 2
                Optional<Identifier> pieceNeededToSpawn = this.requiredPieces.keySet().stream().filter(key -> {
                    int currentCount = this.currentPieceCounts.get(key);
                    StructurePieceCountsManager.RequiredPieceNeeds requiredPieceNeeds = this.requiredPieces.get(key);
                    int requireCount = requiredPieceNeeds == null ? 0 : requiredPieceNeeds.getRequiredAmount();
                    return currentCount < requireCount;
                }).findFirst();

                if (pieceNeededToSpawn.isPresent()) {
                    for (int i = 0; i < candidatePieces.size(); i++) {
                        Pair<StructurePoolElement, Integer> candidatePiecePair = candidatePieces.get(i);
                        StructurePoolElement candidatePiece = candidatePiecePair.getFirst();
                        if (candidatePiece instanceof SinglePoolElement && ((SinglePoolElementAccessor) candidatePiece).repurposedstructures_getTemplate().left().get().equals(pieceNeededToSpawn.get())) { // Condition 1
                            if (depth >= Math.min(maxDepth - 1, this.requiredPieces.get(pieceNeededToSpawn.get()).getMinDistanceFromCenter())) { // Condition 3
                                // All conditions are met. Use required piece  as chosen piece.
                                chosenPiecePair = candidatePiecePair;
                            }
                            else {
                                // If not far enough from starting room, remove the required piece from the list
                                totalCount -= candidatePiecePair.getSecond();
                                candidatePieces.remove(candidatePiecePair);
                            }
                            break;
                        }
                    }
                }

                // Choose piece if required piece wasn't selected
                if (chosenPiecePair == null) {
                    int chosenWeight = rand.nextInt(totalCount) + 1;

                    for (Pair<StructurePoolElement, Integer> candidate : candidatePieces) {
                        chosenWeight -= candidate.getSecond();
                        if (chosenWeight <= 0) {
                            chosenPiecePair = candidate;
                            break;
                        }
                    }
                }

                StructurePoolElement candidatePiece = chosenPiecePair.getFirst();

                // Vanilla check. Not sure on the implications of this.
                if (candidatePiece == EmptyPoolElement.INSTANCE) {
                    return null;
                }

                // Before performing any logic, check to ensure we haven't reached the max number of instances of this piece.
                // This logic is my own additional logic - vanilla does not offer this behavior.
                Identifier pieceName = null;
                if(candidatePiece instanceof SinglePoolElement) {
                    pieceName = ((SinglePoolElementAccessor) candidatePiece).repurposedstructures_getTemplate().left().get();
                    if (this.currentPieceCounts.containsKey(pieceName) && this.maximumPieceCounts.containsKey(pieceName)) {
                        if (this.currentPieceCounts.get(pieceName) >= this.maximumPieceCounts.get(pieceName)) {
                            // Remove this piece from the list of candidates and retry.
                            totalCount -= chosenPiecePair.getSecond();
                            candidatePieces.remove(chosenPiecePair);
                            continue;
                        }
                    }
                }

                // Try different rotations to see which sides of the piece are fit to be the receiving end
                for (BlockRotation rotation : BlockRotation.randomRotationOrder(this.rand)) {
                    List<Structure.StructureBlockInfo> candidateJigsawBlocks = candidatePiece.getStructureBlockInfos(this.structureManager, BlockPos.fromLong(0), rotation, this.rand);
                    BlockBox tempCandidateBoundingBox = candidatePiece.getBoundingBox(this.structureManager, BlockPos.fromLong(0), rotation);

                    // Some sort of logic for setting the candidateHeightAdjustments var if doBoundaryAdjustments.
                    // Not sure on this - personally, I never enable doBoundaryAdjustments.
                    int candidateHeightAdjustments;
                    if (doBoundaryAdjustments && tempCandidateBoundingBox.getMaxY() - tempCandidateBoundingBox.getMinY() <= 16) {
                        candidateHeightAdjustments = candidateJigsawBlocks.stream().mapToInt((pieceCandidateJigsawBlock) -> {
                            if (!tempCandidateBoundingBox.contains(pieceCandidateJigsawBlock.pos.offset(JigsawBlock.getFacing(pieceCandidateJigsawBlock.state)))) {
                                return 0;
                            }
                            else {
                                Identifier candidateTargetPool = new Identifier(pieceCandidateJigsawBlock.nbt.getString("pool"));
                                Optional<StructurePool> candidateTargetPoolOptional = this.poolRegistry.getOrEmpty(candidateTargetPool);
                                Optional<StructurePool> candidateTargetFallbackOptional = candidateTargetPoolOptional.flatMap((p_242843_1_) -> this.poolRegistry.getOrEmpty(p_242843_1_.getId()));
                                int tallestCandidateTargetPoolPieceHeight = candidateTargetPoolOptional.map((p_242842_1_) -> p_242842_1_.getHighestY(this.structureManager)).orElse(0);
                                int tallestCandidateTargetFallbackPieceHeight = candidateTargetFallbackOptional.map((p_242840_1_) -> p_242840_1_.getHighestY(this.structureManager)).orElse(0);
                                return Math.max(tallestCandidateTargetPoolPieceHeight, tallestCandidateTargetFallbackPieceHeight);
                            }
                        }).max().orElse(0);
                    }
                    else {
                        candidateHeightAdjustments = 0;
                    }

                    // Check for each of the candidate's jigsaw blocks for a match
                    for (Structure.StructureBlockInfo candidateJigsawBlock : candidateJigsawBlocks) {
                        if (canJigsawsAttach(jigsawBlock, candidateJigsawBlock)) {
                            BlockPos candidateJigsawBlockPos = candidateJigsawBlock.pos;
                            BlockPos candidateJigsawBlockRelativePos = new BlockPos(jigsawBlockTargetPos.getX() - candidateJigsawBlockPos.getX(), jigsawBlockTargetPos.getY() - candidateJigsawBlockPos.getY(), jigsawBlockTargetPos.getZ() - candidateJigsawBlockPos.getZ());

                            // Get the bounding box for the piece, offset by the relative position difference
                            BlockBox candidateBoundingBox = candidatePiece.getBoundingBox(this.structureManager, candidateJigsawBlockRelativePos, rotation);

                            // Determine if candidate is rigid
                            StructurePool.Projection candidatePlacementBehavior = candidatePiece.getProjection();
                            boolean isCandidateRigid = candidatePlacementBehavior == StructurePool.Projection.RIGID;

                            // Determine how much the candidate jigsaw block is off in the y direction.
                            // This will be needed to offset the candidate piece so that the jigsaw blocks line up properly.
                            int candidateJigsawBlockRelativeY = candidateJigsawBlockPos.getY();
                            int candidateJigsawYOffsetNeeded = jigsawBlockRelativeY - candidateJigsawBlockRelativeY + JigsawBlock.getFacing(jigsawBlock.state).getOffsetY();

                            // Determine how much we need to offset the candidate piece itself in order to have the jigsaw blocks aligned.
                            // Depends on if the placement of both pieces is rigid or not
                            int adjustedCandidatePieceMinY;
                            if (isPieceRigid && isCandidateRigid) {
                                adjustedCandidatePieceMinY = pieceMinY + candidateJigsawYOffsetNeeded;
                            }
                            else {
                                if (surfaceHeight == -1) {
                                    surfaceHeight = this.chunkGenerator.getHeight(jigsawBlockPos.getX(), jigsawBlockPos.getZ(), Heightmap.Type.WORLD_SURFACE_WG, heightLimitView);
                                }

                                adjustedCandidatePieceMinY = surfaceHeight - candidateJigsawBlockRelativeY;
                            }
                            int candidatePieceYOffsetNeeded = adjustedCandidatePieceMinY - candidateBoundingBox.getMinY();

                            // Offset the candidate's bounding box by the necessary amount
                            BlockBox adjustedCandidateBoundingBox = candidateBoundingBox.offset(0, candidatePieceYOffsetNeeded, 0);

                            // Add this offset to the relative jigsaw block position as well
                            BlockPos adjustedCandidateJigsawBlockRelativePos = candidateJigsawBlockRelativePos.add(0, candidatePieceYOffsetNeeded, 0);

                            // Final adjustments to the bounding box.
                            if (candidateHeightAdjustments > 0) {
                                int k2 = Math.max(candidateHeightAdjustments + 1, adjustedCandidateBoundingBox.getMaxY() - adjustedCandidateBoundingBox.getMinY());
                                adjustedCandidateBoundingBox.contains(new BlockPos(adjustedCandidateBoundingBox.getMinX(), adjustedCandidateBoundingBox.getMinY() + k2, adjustedCandidateBoundingBox.getMinZ()));
                            }

                            // Prevent pieces from spawning above max Y or below min Y
                            if (adjustedCandidateBoundingBox.getMaxY() > this.maxY || adjustedCandidateBoundingBox.getMinY() < this.minY) {
                                continue;
                            }

                            Box axisAlignedBB = Box.from(adjustedCandidateBoundingBox);
                            Box axisAlignedBBDeflated = axisAlignedBB.contract(0.25D);
                            boolean validBounds = false;

                            // Make sure new piece fits within the chosen octree without intersecting any other piece.
                            if (ignoreBounds || (boxOctreeMutableObject.getValue().boundaryContains(axisAlignedBBDeflated) && !boxOctreeMutableObject.getValue().intersectsAnyBox(axisAlignedBBDeflated))) {
                                boxOctreeMutableObject.getValue().addBox(axisAlignedBB);
                                validBounds = true;
                            }

                            if (validBounds) {

                                // Determine ground level delta for this new piece
                                int newPieceGroundLevelDelta = piece.getGroundLevelDelta();
                                int groundLevelDelta;
                                if (isCandidateRigid) {
                                    groundLevelDelta = newPieceGroundLevelDelta - candidateJigsawYOffsetNeeded;
                                }
                                else {
                                    groundLevelDelta = candidatePiece.getGroundLevelDelta();
                                }

                                // Create new piece
                                PoolStructurePiece newPiece = new PoolStructurePiece(
                                        this.structureManager,
                                        candidatePiece,
                                        adjustedCandidateJigsawBlockRelativePos,
                                        groundLevelDelta,
                                        rotation,
                                        adjustedCandidateBoundingBox
                                );

                                // Determine actual y-value for the new jigsaw block
                                int candidateJigsawBlockY;
                                if (isPieceRigid) {
                                    candidateJigsawBlockY = pieceMinY + jigsawBlockRelativeY;
                                }
                                else if (isCandidateRigid) {
                                    candidateJigsawBlockY = adjustedCandidatePieceMinY + candidateJigsawBlockRelativeY;
                                }
                                else {
                                    if (surfaceHeight == -1) {
                                        surfaceHeight = this.chunkGenerator.getHeight(jigsawBlockPos.getX(), jigsawBlockPos.getZ(), Heightmap.Type.WORLD_SURFACE_WG, heightLimitView);
                                    }

                                    candidateJigsawBlockY = surfaceHeight + candidateJigsawYOffsetNeeded / 2;
                                }

                                // Add the junction to the existing piece
                                piece.addJunction(
                                        new JigsawJunction(
                                                jigsawBlockTargetPos.getX(),
                                                candidateJigsawBlockY - jigsawBlockRelativeY + newPieceGroundLevelDelta,
                                                jigsawBlockTargetPos.getZ(),
                                                candidateJigsawYOffsetNeeded,
                                                candidatePlacementBehavior)
                                );

                                // Add the junction to the new piece
                                newPiece.addJunction(
                                        new JigsawJunction(
                                                jigsawBlockPos.getX(),
                                                candidateJigsawBlockY - candidateJigsawBlockRelativeY + groundLevelDelta,
                                                jigsawBlockPos.getZ(),
                                                -candidateJigsawYOffsetNeeded,
                                                piecePlacementBehavior)
                                );

                                // Add the piece
                                this.structurePieces.add(newPiece);
                                if (depth + 1 <= this.maxDepth) {
                                    this.availablePieces.addLast(new Entry(newPiece, boxOctreeMutableObject, targetPieceBoundsTop, depth + 1));
                                }
                                // Update piece count, if an entry exists for this piece
                                if (pieceName != null && this.currentPieceCounts.containsKey(pieceName)) {
                                    this.currentPieceCounts.put(pieceName, this.currentPieceCounts.get(pieceName) + 1);
                                }
                                return candidatePiece;
                            }
                        }
                    }
                }
                totalCount -= chosenPiecePair.getSecond();
                candidatePieces.remove(chosenPiecePair);
            }
            return null;
        }

        public static boolean canJigsawsAttach(Structure.StructureBlockInfo jigsaw1, Structure.StructureBlockInfo jigsaw2) {
            JigsawOrientation prop1 = jigsaw1.state.get(JigsawBlock.ORIENTATION);
            JigsawOrientation prop2 = jigsaw2.state.get(JigsawBlock.ORIENTATION);
            String joint = jigsaw1.nbt.getString("joint");
            if(joint.isEmpty()) {
                joint = prop1.getFacing().getAxis().isHorizontal() ? "aligned" : "rollable";
            }

            boolean isRollable = joint.equals("rollable");
            return prop1.getFacing() == prop2.getFacing().getOpposite() &&
                    (isRollable || prop1.getFacing() == prop2.getFacing()) &&
                    jigsaw1.nbt.getString("target").equals(jigsaw2.nbt.getString("name"));
        }
    }
}