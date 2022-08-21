package ru.aiefu.discordium.language;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import ru.aiefu.discordium.DiscordiumLogger;
import ru.aiefu.discordium.discord.DiscordLink;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ServerLanguage extends Language {
    private Map<String, String> storage;
    private boolean isBidirectional = false;
    private static final DiscordiumLogger logger = DiscordLink.logger;
    private static final HashSet<String> excludeModIDs = new HashSet<>();

    public ServerLanguage(){
        excludeModIDs.add("discordium");
        excludeModIDs.add("fabric-api-lookup-api-v1");
        excludeModIDs.add("fabric-events-interaction-v0");
        excludeModIDs.add("confabricate");
        excludeModIDs.add("fabric-registry-sync-v0");
        excludeModIDs.add("fabric-structure-api-v1");
        excludeModIDs.add("fabric-events-lifecycle-v0");
        excludeModIDs.add("fabric-lifecycle-events-v1");
        excludeModIDs.add("fabric-gametest-api-v1");
        excludeModIDs.add("fabric-mining-level-api-v1");
        excludeModIDs.add("fabric-tool-attribute-api-v1");
        excludeModIDs.add("fabric-networking-v0");
    }

    public void loadAllLanguagesIncludingModded(String languageKey, boolean bl) {
        this.isBidirectional = bl;
        HashMap<String, String> languageKeys = new HashMap<>();
        try {
            loadMinecraftLanguage(languageKey, languageKeys);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collection<ModContainer> mods = FabricLoader.getInstance().getAllMods();
        if (!mods.isEmpty()) {
            for (ModContainer c : mods) {
                ModMetadata meta = c.getMetadata();
                if (meta instanceof LoaderModMetadata loaderModMetadata && !excludeModIDs.contains(meta.getId())) {
                    EntrypointMetadata data = loaderModMetadata.getEntrypoints("main").stream().findFirst()
                            .or(() -> loaderModMetadata.getEntrypoints("server").stream().findFirst())
                            .orElse(null);
                    if (data != null) {
                        try {
                            Class<?> modClass = FabricLauncherBase.getClass(data.getValue());
                            if (!tryLoadModLang(meta.getId(), languageKey, languageKeys, meta, modClass)) {
                                Set<String> assetsSubDirs = getAssetsSubDirs(c);
                                // I hate java, no clean way of checking for immutable
                                if (!assetsSubDirs.getClass().isInstance(Set.of())) {
                                    assetsSubDirs.remove(meta.getId());
                                }
                                var it = assetsSubDirs.iterator();
                                while (it.hasNext()
                                        && !tryLoadModLang(it.next(), languageKey, languageKeys, meta, modClass)) {
                                }
                            }
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                }
            }
        }
        this.storage = languageKeys;
        inject(this);
    }

    private Set<String> getAssetsSubDirs(ModContainer c) {
        var assetsOpt = c.findPath("/assets");
        if (assetsOpt.isPresent()) {
            logger.info("/assets present in {}", c.getMetadata().getName());
            try (var stream = Files.list(assetsOpt.get())) {
                return stream
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            logger.info("No /assets directory in mod {}", c.getMetadata().getName());
        }
        return Set.of();
    }

    private boolean tryLoadModLang(String dir, String languageKey, HashMap<String, String> languageKeys,
            ModMetadata meta, Class<?> modClass) {
        try {
            String locale = languageKey;
            InputStream inputStream = modClass
                    .getResourceAsStream(String.format("/assets/%s/lang/%s.json", dir, languageKey));
            if (inputStream == null && !languageKey.equals("en_us")) {
                inputStream = modClass.getResourceAsStream(String.format("/assets/%s/lang/en_us.json", dir));
                logger.info(
                        "Failed to load language {} for mod {} (id: {}) from dir {}, trying to load default en_us locale",
                        languageKey, meta.getName(), meta.getId(), dir);
                locale = "en_us(fallback)";
            }
            if (inputStream != null) {
                loadFromJson(inputStream, languageKeys::put);
                logger.info("Loaded language {} for mod {} from dir {}", locale, meta.getName(), dir);
                inputStream.close();
                return true;
            } else {
                logger.info("Failed to load default en_us locale for mod {}(id: {}) from dir {}", meta.getName(),
                        meta.getId(), dir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void loadMinecraftLanguage(String languageKey, HashMap<String, String> languageKeys) throws IOException {
        String path = String.format("./config/discord-chat/languages/%s.json", languageKey);
        InputStream stream = null;
        String locale = languageKey;
        if(Files.exists(Paths.get(path))){
            stream = new FileInputStream(path);
        } else {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://launchermeta.mojang.com/v1/packages/7fcda714d4a391a711ce434fa1dbbebe73ecf179/1.18.json").openConnection();
            Gson gson = new Gson();
            JsonObject indexes = gson.fromJson(new InputStreamReader(connection.getInputStream()), JsonObject.class);
            HashMap<String, AssetsData> data = gson.fromJson(indexes.get("objects"), new TypeToken<HashMap<String, AssetsData>>(){}.getType());
            AssetsData lang = data.get(String.format("minecraft/lang/%s.json", languageKey));
            if(lang != null){
                String hash = lang.hash;
                connection = (HttpURLConnection) new URL(String.format("https://resources.download.minecraft.net/%s/%s", hash.substring(0, 2), hash)).openConnection();
                stream = connection.getInputStream();
                if(stream != null){
                    byte[] inputArray = IOUtils.toByteArray(stream);
                    stream = new ByteArrayInputStream(inputArray);
                    FileOutputStream file = new FileOutputStream(path);
                    file.write(inputArray);
                    file.close();
                }
            }
        }
        if(stream == null) {
            stream = MinecraftServer.class.getResourceAsStream("/assets/minecraft/lang/en_us.json");
            logger.info(String.format("Failed to load minecraft locale %s, trying to load default en_us locale", locale));
            locale = "en_us(fallback)";
        }
        if(stream != null){
            loadFromJson(stream, languageKeys::put);
            logger.info("Loaded minecraft language " + locale);
            stream.close();
        }
    }



    @Override
    public String getOrDefault(@NotNull String string) {
        return storage.getOrDefault(string, string);
    }

    @Override
    public boolean has(@NotNull String string) {
        return storage.containsKey(string);
    }

    @Override
    public boolean isDefaultRightToLeft() {
        return isBidirectional;
    }

    @Override
    public FormattedCharSequence getVisualOrder(@NotNull FormattedText formattedText) {
        return FormattedBidiReorder.reorder(formattedText, this.isBidirectional);
    }

    public static class AssetsData {
        public String hash;
        public double size;
    }
}
