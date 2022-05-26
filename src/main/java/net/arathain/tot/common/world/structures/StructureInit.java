package net.arathain.tot.common.world.structures;

import net.arathain.tot.TomeOfTiamatha;
import net.arathain.tot.common.world.structures.stc.DungeonStructure;
import net.arathain.tot.common.world.structures.util.DungeonStructureConfig;
import net.arathain.tot.mixin.StructureFeatureAccessor;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.StructureFeature;

public class StructureInit {

    public StructureInit() {}

    public static StructureFeature<?> DUNGEON_STRUCTURE = new DungeonStructure<>(DungeonStructureConfig.CODEC);

    public static void registerStructures() {
        Registry.register(Registry.STRUCTURE_FEATURE, new Identifier(TomeOfTiamatha.MODID, "boss_dungeon"), DUNGEON_STRUCTURE);

        StructureFeatureAccessor.getSTEP().put(DUNGEON_STRUCTURE, GenerationStep.Feature.SURFACE_STRUCTURES);
    }
}
