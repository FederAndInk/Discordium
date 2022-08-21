package ru.aiefu.discordium.discord;

public class DiscordConfig {
    public String token = "";
    public String chatChannelId = "";
    public String consoleChannelId = "";
    public String crashChannelId = "";
    public boolean enableLogsForwarding = true;
    public String serverId = "";
    public boolean preloadDiscordMembers = false;

    public String playerHeadsUrl = "http://cravatar.eu/avatar/{uuid}/400.png";
    public boolean enableWebhook = false;
    public String webhookUrl = "";
    public boolean enableAccountLinking = false;
    public boolean forceLinking = false;
    public boolean requireMemberOnDiscordServer = false;
    public boolean useDiscordData = false;
    public boolean enableMentions = false;

    public String targetLocalization = "en_us";
    public boolean isBidirectional = false;

    public String startupMsg = ":white_check_mark: **Server started!**";
    public String serverStopMsg = ":octagonal_sign: **Server stopped!**";

    public String joinMessage = "{username} joined the server!";
    public String disconnectMessage = "{username} left the server!";
    public boolean appendAdvancementDescription = true;

    public String onlinePlayersMsg = "Players online: ";
    public String noPlayersMsg = "Currently there are no players on server";
    public String channelTopicMsg = "Players online: ";
    public String shutdownTopicMsg = "Server offline";
    public String verificationDisconnect = "You need to verify your account via discord. Your code is {code}. Join {{https://discord.gg/YOURLINK}} and send this code in PM to {botname}.";
    public String notOnDiscordServer = "You need to be on the discord server. Please join https://discord.gg/YOURLINK";
    public String removedFromDiscordServer = "You are not on the discord server anymore";
    public String successfulVerificationMsg = "Successfully linked discord account to your game account {username}({uuid})";
    public String commandLinkMsg = "Your code is {code}. Send this code to {botname} PM.";
    public String alreadyLinked = "Game account {username} is already linked to {discordname}";
    public String successLinkDiscordMsg = "Linked game profile {username} to discord profile {discordname}";
    public String codeUnlinkMsg = "Unlinked discord profile successfully";
    public String codeUnlinkFail = "Failed to unlink discord profile, profile not found!";
    public String mentionState = "Mention sound notifications is now set to {state}";

    public int config_version = 1;

    public transient String cLinkMsg1;
    public transient String cLinkMsg2 = "";

    public void setup(){
        String msg = commandLinkMsg;
        int i = msg.indexOf("{code}");
        if(i != -1){
            cLinkMsg1 = msg.substring(0, i);
            cLinkMsg2 = msg.substring(i +6);
        } else cLinkMsg1 = msg;
    }
}
