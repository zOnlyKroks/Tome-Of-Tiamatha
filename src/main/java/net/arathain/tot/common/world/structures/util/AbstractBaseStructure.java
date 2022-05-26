package net.arathain.tot.common.world.structures.util;

import com.mojang.serialization.Codec;
import net.minecraft.structure.PostPlacementProcessor;
import net.minecraft.structure.StructurePiecesGenerator;
import net.minecraft.structure.StructurePiecesGeneratorFactory;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public abstract class AbstractBaseStructure<C extends FeatureConfig> extends StructureFeature<C> {

    public AbstractBaseStructure(Codec<C> codec, Predicate<StructurePiecesGeneratorFactory.Context<C>> locationCheckPredicate, Function<StructurePiecesGeneratorFactory.Context<C>, Optional<StructurePiecesGenerator<C>>> pieceCreationPredicate) {
        this(codec, locationCheckPredicate, pieceCreationPredicate, PostPlacementProcessor.EMPTY);
    }

    public AbstractBaseStructure(Codec<C> codec, Predicate<StructurePiecesGeneratorFactory.Context<C>> locationCheckPredicate, Function<StructurePiecesGeneratorFactory.Context<C>, Optional<StructurePiecesGenerator<C>>> pieceCreationPredicate, PostPlacementProcessor postPlacementProcessor) {
        super(codec, (context) -> {
                    if (!locationCheckPredicate.test(context)) {
                        return Optional.empty();
                    }
                    else {
                        return pieceCreationPredicate.apply(context);
                    }
                },
                postPlacementProcessor);
    }
}
