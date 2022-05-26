package net.arathain.tot.common.world.structures;

import net.arathain.tot.TomeOfTiamatha;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

import java.util.function.Supplier;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public class JSONConditionsRegistry {

    private JSONConditionsRegistry() {}

    public static final RegistryKey<Registry<Object>> RS_JSON_CONDITIONS_KEY = Registry.createRegistryKey("json_conditions");
    private static final Supplier<Boolean> TEMP_CLASS_TYPE = () -> true;
    public static final Registry<Supplier<Boolean>> RS_JSON_CONDITIONS_REGISTRY = (Registry<Supplier<Boolean>>) FabricRegistryBuilder.createSimple(TEMP_CLASS_TYPE.getClass(), RS_JSON_CONDITIONS_KEY.getRegistry()).buildAndRegister();

    public static void createJSONConditionsRegistry() {
        // Classloads the fields that creates the registries.
        // Registers a condition for testing purposes.
        Registry.REGISTRIES.getOrEmpty(new Identifier(TomeOfTiamatha.MODID, "json_conditions"))
                .ifPresent(registry -> Registry.register(
                        (Registry<Supplier<Boolean>>)registry,
                        new Identifier(TomeOfTiamatha.MODID, "test"),
                        () -> false));
    }
}
