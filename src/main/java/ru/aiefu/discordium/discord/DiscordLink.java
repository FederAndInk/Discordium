package ru.aiefu.discordium.discord;

import java.awt.Color;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.security.auth.login.LoginException;

import org.apache.logging.log4j.LogManager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import kong.unirest.Unirest;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.Advancement;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.BaseComponent;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import ru.aiefu.discordium.ConsoleFilter;
import ru.aiefu.discordium.DiscordiumCommands;
import ru.aiefu.discordium.DiscordiumLogger;
import ru.aiefu.discordium.OnPlayerMessageEvent;
import ru.aiefu.discordium.ProfileLinkCommand;
import ru.aiefu.discordium.config.ConfigManager;
import ru.aiefu.discordium.config.LinkedProfile;
import ru.aiefu.discordium.language.ServerLanguage;

public class DiscordLink implements DedicatedServerModInitializer {
    public static final String MODID = "discordium";
    public static JDA jda;
    public static TextChannel chatChannel;
    public static TextChannel consoleChannel;
    public static TextChannel crashChannel;
    public static DiscordConfig config;
    public static DedicatedServer server;
    public static DiscordiumLogger logger = new DiscordiumLogger();

    public static HashMap<String, LinkedProfile> linkedPlayers = new HashMap<>();
    public static HashMap<String, String> linkedPlayersByDiscordId = new HashMap<>();
    public static HashMap<Integer, VerificationData> pendingPlayers = new HashMap<>();
    public static HashMap<String, Integer> pendingPlayersUUID = new HashMap<>();
    public static long currentTime = System.currentTimeMillis();
    public static String botName;
    public static Guild guild;

    public static final ServerLanguage language = new ServerLanguage();

    public static boolean stopped = false;

    @Override
    public void onInitializeServer() {
        ConfigManager manager = new ConfigManager();
        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addFilter(new ConsoleFilter());
        try {
            manager.craftPaths();
            manager.genDiscordLinkSettings();
            DiscordLink.config = manager.readDiscordLinkSettings();
            DiscordLink.config.setup();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            initialize(manager);
        } catch (LoginException | InterruptedException e) {
            e.printStackTrace();
        }
        language.loadAllLanguagesIncludingModded(config.targetLocalization, config.isBidirectional);
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            DiscordiumCommands.register(dispatcher);
            if(config.enableAccountLinking && !config.forceLinking){
                ProfileLinkCommand.register(dispatcher);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DiscordLink.server = (DedicatedServer) server;
            sendMessage(chatChannel, DiscordLink.config.startupMsg);
            OnPlayerMessageEvent.EVENT.register(DiscordLink::onPlayerMessage);
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> currentTime = System.currentTimeMillis());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tickCount = server.getTickCount();
            if(tickCount % 6000 == 0){
                DiscordLink.setTopic(server.getPlayerCount(), server.getMaxPlayers());
            }
            if (tickCount % 1200 == 0) {
                var it = pendingPlayers.entrySet().iterator();
                while(it.hasNext()){
                    var e = it.next();
                    VerificationData data = e.getValue();
                    if(currentTime > data.validUntil()){
                        pendingPlayersUUID.remove(data.uuid());
                        it.remove();
                    }
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DiscordLink.shutdown());
    }

    public static void initialize(ConfigManager manager) throws LoginException, InterruptedException {
        jda = JDABuilder.createDefault(config.token).setHttpClient((new OkHttpClient.Builder()).protocols(Collections.singletonList(Protocol.HTTP_1_1)).build()).setMemberCachePolicy(MemberCachePolicy.ALL).enableIntents(GatewayIntent.GUILD_MEMBERS).addEventListeners(new Object[]{new DiscordListener()}).build();
        jda.awaitReady();
        if(!config.serverId.isEmpty()){
            guild = jda.getGuildById(config.serverId);
            if(guild != null && config.preloadDiscordMembers)
                guild.loadMembers();
        }
        botName = jda.getSelfUser().getName();
        chatChannel = jda.getTextChannelById(config.chatChannelId);
        consoleChannel = jda.getTextChannelById(config.consoleChannelId);
        if (!config.crashChannelId.isEmpty()) {
            crashChannel = jda.getTextChannelById(config.crashChannelId);
        }
        String webhookUrl = config.webhookUrl;
        boolean bl = config.enableWebhook;
        if(bl && config.webhookUrl.length() > 0)
        for (Webhook w : chatChannel.retrieveWebhooks().complete()){
            if(w.getUrl().equals(webhookUrl)){
                bl = false;
                break;
            }
        }

        if(bl){
            config.webhookUrl = chatChannel.createWebhook("Minecraft Chat Message Forwarding").complete().getUrl();
            manager.writeCurrentConfigInstance();
        }
    }

    public static void postConsoleMessage(String msg){
        if(consoleChannel != null && !stopped && config.enableLogsForwarding){
            if(msg.length() > 1999)
                msg = msg.substring(0, 1999);
            sendMessage(consoleChannel, msg);
        }
    }

    public static void postChatMessage(BaseComponent component) {
        if (chatChannel != null && !stopped) {
            sendMessage(chatChannel, component.getString());
        }
    }

    private static String discordNameIfEnabled(String uuid) {
        if (config.enableAccountLinking && guild != null && config.useDiscordData) {
            LinkedProfile profile = linkedPlayers.get(uuid);
            if (profile != null) {
                Member m = guild.getMemberById(profile.discordId);
                if (m != null) {
                    return m.getEffectiveName();
                } else {
                    logger.warn("discord member {} not found", profile.discordId);
                }
            }
        }
        return "";
    }
    
    private static String playerAndDiscordNames(String mcname, String uuid) {
        String dname = discordNameIfEnabled(uuid);
        if (dname.isEmpty() || dname.equals(mcname)) {
            return mcname;
        } else {
            return mcname + " (" + dname + ")";
        }
    }

    public static void onPlayerMessage(ServerPlayer player, String msg, BaseComponent textComponent){
        if(config.enableMentions) {
            msg = parseDiscordMentions(msg);
        }
        if(config.enableWebhook){
            String uuid = player.getStringUUID();
            String name = player.getScoreboardName();
            postWebHookMsg(msg, playerAndDiscordNames(name, uuid), getPlayerIconUrl(name, uuid));
        } else {
            postChatMessage(textComponent);
        }
    }

    private static final Pattern pattern = Pattern.compile("(?<=@).+?(?=@|$|\\s)");

    public static String parseDiscordMentions(String msg){
        if(guild != null){
            List<String> mentions = pattern.matcher(msg).results().map(matchResult -> matchResult.group(0)).collect(Collectors.toList());
            for (String s : mentions){
                if(User.USER_TAG.matcher(s).matches()) {
                    Member m = guild.getMemberByTag(s);
                    if (m != null) {
                        msg = msg.replaceAll("@" + s, "<@!" + m.getId() + ">");
                    }
                }
            }
        }
        return msg;
    }

    public static void postWebHookMsg(String msg, String username, String avatarUrl){
        if(chatChannel != null && !stopped) {
            JsonObject object = new JsonObject();
            object.addProperty("username", username);
            object.addProperty("avatar_url", avatarUrl);
            object.addProperty("content", msg);
            try {
                sendWebhook(object);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void greetingMsg(String username, String uuid){
        EmbedBuilder e = new EmbedBuilder();
        e.setAuthor(config.joinMessage.replaceAll("\\{username}",
                playerAndDiscordNames(username, uuid)), null, getPlayerIconUrl(username, uuid));
        e.setColor(Color.GREEN);
        sendEmbed(chatChannel, e.build());
    }

    public static void logoutMsg(String username, String uuid){
        EmbedBuilder e = new EmbedBuilder();
        e.setAuthor(config.disconnectMessage.replaceAll("\\{username}", 
                playerAndDiscordNames(username, uuid)), null, getPlayerIconUrl(username, uuid));
        e.setColor(Color.RED);
        sendEmbed(chatChannel, e.build());
    }

    public static void sendAdvancement(String username, Advancement adv, String uuid){
        EmbedBuilder e = new EmbedBuilder();
        e.setAuthor(String.format(Language.getInstance().getOrDefault("chat.type.advancement." + adv.getDisplay().getFrame().getName()), 
                playerAndDiscordNames(username, uuid), adv.getDisplay().getTitle().getString()), null, getPlayerIconUrl(username, uuid));
        if(config.appendAdvancementDescription){
            e.setDescription(String.format("** %s **", adv.getDisplay().getDescription().getString()));
        }
        e.setColor(12524269);
        sendEmbed(chatChannel, e.build());
    }

    public static void sendDeathMsg(String username, String msg, String uuid){
        EmbedBuilder e = new EmbedBuilder();
        e.setAuthor(msg.replace(username, playerAndDiscordNames(username, uuid)), null, getPlayerIconUrl(username, uuid));
        e.setColor(Color.RED);
        sendEmbed(chatChannel, e.build());
    }

    public static String getPlayerIconUrl(String name, String uuid){
        return DiscordLink.config.playerHeadsUrl.replaceAll("\\{username}", name).replaceAll("\\{uuid}", uuid);
    }

    public static void sendEmbed(TextChannel ch, MessageEmbed e){
        if(ch!= null && !stopped){
            chatChannel.sendMessageEmbeds(e).queue();
        }
    }

    public static void sendMessage(MessageChannel ch, String msg){
        if(ch!= null && !stopped){
            ch.sendMessage(msg).queue();
        }
    }

    public static void sendWebhook(JsonObject json) throws IOException {
        if(!stopped && config.webhookUrl.length() > 0)
        Unirest.post(config.webhookUrl).header("Content-type", "application/json").body(new Gson().toJson(json)).asStringAsync();
    }

    public static void setTopic(int count, int maxCount){
        if(!stopped)
        chatChannel.getManager().setTopic(DiscordLink.config.channelTopicMsg + count + "/" + maxCount).queue();
    }

    public static void setTopic(String msg){
        if(!stopped)
            chatChannel.getManager().setTopic(msg).queue();
    }

    public static void shutdown(){
        setTopic(config.shutdownTopicMsg);
        sendMessage(chatChannel, config.serverStopMsg);
        Unirest.shutDown();
        stopped = true;
        try {
            Thread.sleep(350L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        jda.shutdown();
    }
}
