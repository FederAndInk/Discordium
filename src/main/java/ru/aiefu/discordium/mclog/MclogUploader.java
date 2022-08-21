package ru.aiefu.discordium.mclog;

/**
 * MIT Licensed
 * https://github.com/aternosorg/mclogs-java
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import ru.aiefu.discordium.discord.DiscordLink;

public class MclogUploader {
  public static APIResponse share(File file) throws IOException {
    // connect to api
    URL url = new URL("https://api.mclo.gs/1/log");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);

    // convert log to application/x-www-form-urlencoded
    String content = "content=" + URLEncoder.encode(readLines(file), StandardCharsets.UTF_8.toString());
    byte[] out = content.getBytes(StandardCharsets.UTF_8);
    int length = out.length;

    Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(DiscordLink.MODID);
    String version = mod.isPresent() ? mod.get().getMetadata().getVersion().getFriendlyString() : "unknown";
    String mcversion = DiscordLink.server.getServerVersion();

    // send log to api
    connection.setFixedLengthStreamingMode(length);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    connection.setRequestProperty("User-Agent", DiscordLink.MODID + "/" + version + "/" + mcversion);
    connection.connect();
    try (OutputStream os = connection.getOutputStream()) {
      os.write(out);
    }

    // handle response
    return APIResponse.parse(inputStreamToString(connection.getInputStream()));
  }

  private static String readLines(File file) throws IOException {
    try (InputStream in = Files.newInputStream(file.toPath())) {
      return inputStreamToString(in);
    }
  }

  private static String inputStreamToString(InputStream is) {
    return new BufferedReader(new InputStreamReader(is))
        .lines()
        .collect(Collectors.joining("\n"));
  }
}
