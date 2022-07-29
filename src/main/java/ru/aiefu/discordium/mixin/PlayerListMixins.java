package ru.aiefu.discordium.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ru.aiefu.discordium.DisconnectMsgBuilder;
import ru.aiefu.discordium.config.ConfigManager;
import ru.aiefu.discordium.config.LinkedProfile;
import ru.aiefu.discordium.discord.DiscordConfig;
import ru.aiefu.discordium.discord.DiscordLink;
import ru.aiefu.discordium.discord.VerificationData;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Random;


@Mixin(PlayerList.class)
public class PlayerListMixins {

    private final Random r = new Random();

    @Inject(method = "placeNewPlayer", at =@At("HEAD"))
    private void sendDiscordWelcomeMsg(Connection connection, ServerPlayer serverPlayer, CallbackInfo ci){
        DiscordLink.greetingMsg(serverPlayer.getScoreboardName(), serverPlayer.getStringUUID());
    }

    @Inject(method = "remove", at =@At("HEAD"))
    private void sendLogoutMessage(ServerPlayer serverPlayer, CallbackInfo ci){
        DiscordLink.logoutMsg(serverPlayer.getScoreboardName(), serverPlayer.getStringUUID());
        LinkedProfile profile = DiscordLink.linkedPlayers.get(serverPlayer.getStringUUID());
        if(profile != null) {
            DiscordLink.linkedPlayersByDiscordId.remove(profile.discordId);
            DiscordLink.linkedPlayers.remove(serverPlayer.getGameProfile().getId().toString());
        }
    }

    @Inject(method = "canPlayerLogin", at =@At("HEAD"), cancellable = true)
    private void checkLink(SocketAddress socketAddress, GameProfile gameProfile, CallbackInfoReturnable<Component> cir) throws IOException {
        DiscordConfig cfg = DiscordLink.config;
        String uuid = gameProfile.getId().toString();
        LinkedProfile profile = null;
        if(cfg.enableAccountLinking) {
            profile = ConfigManager.getLinkedProfile(uuid);
            if (profile != null) {
                DiscordLink.linkedPlayers.put(uuid, profile);
                DiscordLink.linkedPlayersByDiscordId.put(profile.discordId, gameProfile.getName());
            }
        }
        if (cfg.enableAccountLinking && cfg.forceLinking && profile == null) {
            DiscordLink.logger.info(gameProfile.getName() + " needs to link");
            if(!DiscordLink.pendingPlayersUUID.containsKey(uuid)) {
                int authCode = r.nextInt(100_000, 1_000_000);
                while (DiscordLink.pendingPlayers.containsKey(authCode)) {
                    authCode = r.nextInt(100_000, 1_000_000);
                }
                String auth = String.valueOf(authCode);
                DiscordLink.pendingPlayers.put(authCode, new VerificationData(gameProfile.getName(), uuid, DiscordLink.currentTime + 600_000));
                DiscordLink.pendingPlayersUUID.put(uuid, authCode);
                DisconnectMsgBuilder.setVerificationDisconnectMsg(cir, auth);
            } else {
                DisconnectMsgBuilder.setVerificationDisconnectMsg(cir, DiscordLink.pendingPlayersUUID.get(uuid).toString());
            }
        }
    }
}
