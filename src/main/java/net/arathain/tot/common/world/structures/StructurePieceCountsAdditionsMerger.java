package net.arathain.tot.common.world.structures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.bridge.game.PackType;
import net.arathain.tot.TomeOfTiamatha;
import net.arathain.tot.mixin.ReloadableResourceManagerImplAccessor;
import net.fabricmc.fabric.mixin.resource.loader.NamespaceResourceManagerAccessor;
import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.pack.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Credit to TelepathicGrunt (https://github.com/TelepathicGrunt)
public class StructurePieceCountsAdditionsMerger {

    private StructurePieceCountsAdditionsMerger() {}

    // Needed for detecting the correct files, ignoring file extension, and what JSON parser to use for parsing the files
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().setLenient().disableHtmlEscaping().create();
    private static final String DATA_TYPE = "rs_pieces_spawn_counts_additions";
    private static final int FILE_SUFFIX_LENGTH = ".json".length();

    /**
     * Call this at end of StructurePieceCountsManager's apply to make sure we merge in all found counts additions into the parsed based counts.
     */
    static void performCountsAdditionsDetectionAndMerger(ResourceManager resourceManager) {
        Map<Identifier, List<JsonElement>> countsAdditionJSON = getAllDatapacksJSONElement(resourceManager, GSON, DATA_TYPE, FILE_SUFFIX_LENGTH);
        parseCountsAndBeginMerger(countsAdditionJSON);
    }

    /**
     * Will iterate over all of our found counts additions and parse our JSON objects to shove the final product into StructurePiecesCountsManager
     */
    private static void parseCountsAndBeginMerger(Map<Identifier, List<JsonElement>> countsAdditionJSON) {
        for (Map.Entry<Identifier, List<JsonElement>> entry : countsAdditionJSON.entrySet()) {
            TomeOfTiamatha.structurePieceCountsManager.parseAndAddCountsJSONObj(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Obtains all of the file streams for all files found in all datapacks with the given id.
     *
     * @return - Filestream list of all files found with id
     */
    public static List<InputStream> getAllFileStreams(ResourceManager resourceManager, Identifier fileID) throws IOException {
        List<InputStream> fileStreams = new ArrayList<>();

        NamespaceResourceManager namespaceResourceManager = ((ReloadableResourceManagerImplAccessor) resourceManager).tome_of_timanthia_getNamespacedManagers().get(fileID.getNamespace());
        List<ResourcePack> allResourcePacks = ((net.arathain.tot.mixin.NamespaceResourceManagerAccessor) namespaceResourceManager).repurposedstructures_getFallbacks();

        // Find the file with the given id and add its filestream to the list
        for (ResourcePack resourcePack : allResourcePacks) {
            if (resourcePack.contains(ResourceType.SERVER_DATA, fileID)) {
                InputStream inputStream = ((net.arathain.tot.mixin.NamespaceResourceManagerAccessor) namespaceResourceManager).repurposedstructures_callGetWrappedResource(fileID, resourcePack);
                if (inputStream != null) fileStreams.add(inputStream);
            }
        }

        // Return filestream of all files matching id path
        return fileStreams;
    }

    /**
     * Will grab all JSON objects from all datapacks's folder that is specified by the dataType parameter.
     *
     * @return - A map of paths (identifiers) to a list of all JSON elements found under it from all datapacks.
     */
    public static Map<Identifier, List<JsonElement>> getAllDatapacksJSONElement(ResourceManager resourceManager, Gson gson, String dataType, int fileSuffixLength) {
        Map<Identifier, List<JsonElement>> map = new HashMap<>();
        int dataTypeLength = dataType.length() + 1;

        // Finds all JSON files paths within the pool_additions folder. NOTE: this is just the path rn. Not the actual files yet.
        for (Identifier fileIDWithExtension : resourceManager.findResources(dataType, (fileString) -> fileString.endsWith(".json"))) {
            String identifierPath = fileIDWithExtension.getPath();
            Identifier fileID = new Identifier(
                    fileIDWithExtension.getNamespace(),
                    identifierPath.substring(dataTypeLength, identifierPath.length() - fileSuffixLength));

            try {
                // getAllFileStreams will find files with the given ID. This part is what will loop over all matching files from all datapacks.
                for (InputStream fileStream : getAllFileStreams(resourceManager, fileIDWithExtension)) {
                    try (Reader bufferedReader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {

                        // Get the JSON from the file
                        JsonElement countsJSONElement = JsonHelper.deserialize(gson, bufferedReader, (Class<? extends JsonElement>) JsonElement.class);
                        if (countsJSONElement != null) {

                            // Create list in map for the ID if non exists yet for that ID
                            if (!map.containsKey(fileID)) {
                                map.put(fileID, new ArrayList<>());
                            }
                            // Add the parsed json to the list we will merge later on
                            map.get(fileID).add(countsJSONElement);
                        }
                        else {
                        }
                    }
                }
            }
            catch (IllegalArgumentException | IOException | JsonParseException exception) {
            }
        }

        return map;
    }

}
