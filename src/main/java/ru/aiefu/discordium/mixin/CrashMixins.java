package ru.aiefu.discordium.mixin;

import java.io.File;
import java.io.IOException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.CrashReport;
import ru.aiefu.discordium.discord.DiscordLink;
import ru.aiefu.discordium.mclog.APIResponse;
import ru.aiefu.discordium.mclog.MclogUploader;

@Mixin(CrashReport.class)
public abstract class CrashMixins {
  @Inject(method = "saveToFile", at = @At("RETURN"))
  public void sendCrashReport(File file, CallbackInfoReturnable<Boolean> cir) {
    if (DiscordLink.config.crashChannelId.isEmpty() || file == null) {
      return;
    }
    DiscordLink.logger.info("sending crash report {}", file);

    String mclogUrl = "";
    try {
      APIResponse mclogResp = MclogUploader.share(file);
      mclogUrl = mclogResp.url;
      DiscordLink.logger.info("crash report uploaded to {}", mclogUrl);
    } catch (IOException e) {
      e.printStackTrace();
    }
    var msg = DiscordLink.crashChannel.sendFile(file);
    if (!mclogUrl.isEmpty()) {
      msg = msg.append(mclogUrl);
    }
    msg.queue();
  }
}
