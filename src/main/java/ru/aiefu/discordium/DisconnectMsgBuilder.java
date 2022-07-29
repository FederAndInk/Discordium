package ru.aiefu.discordium;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextComponent;
import ru.aiefu.discordium.discord.DiscordConfig;
import ru.aiefu.discordium.discord.DiscordLink;

public class DisconnectMsgBuilder {
  static public void setVerificationDisconnectMsg(CallbackInfoReturnable<Component> cir, String code) {
    DiscordConfig cfg = DiscordLink.config;
    final String CODE_TOKEN = "{code}";
    final String BOTNAME_TOKEN = "{botname}";

    String msgRest = cfg.verificationDisconnect;
    int idx = msgRest.indexOf('{');
    var msgComp = new TextComponent(msgRest.substring(0, idx == -1 ? msgRest.length() : idx));

    while (idx != -1) {
      msgRest = msgRest.substring(idx);

      if (msgRest.startsWith(CODE_TOKEN)) {
        msgComp.append(new TextComponent(code).withStyle(ChatFormatting.GREEN));
        msgRest = msgRest.substring(CODE_TOKEN.length());

      } else if (msgRest.startsWith(BOTNAME_TOKEN)) {
        msgComp.append(new TextComponent(DiscordLink.botName)
            .withStyle(ChatFormatting.DARK_PURPLE));
        msgRest = msgRest.substring(BOTNAME_TOKEN.length());

        // Handle links
      } else if (msgRest.startsWith("{{")) {
        var endLinkIdx = msgRest.indexOf("}}");
        if (endLinkIdx != -1) {
          String link = msgRest.substring(2, endLinkIdx);
          msgComp.append(new TextComponent(link)
              .withStyle(style -> style
                  .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link))
                  .withColor(ChatFormatting.BLUE)
                  .withUnderlined(true)
                  .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent("Link")))));
          msgRest = msgRest.substring(endLinkIdx + 2);
        } else {
          msgComp.append(new TextComponent("{"));
          msgRest = msgRest.substring(1);
        }
      } else {
        msgComp.append(new TextComponent("{"));

        msgRest = msgRest.substring(1);
      }
      idx = msgRest.indexOf('{');
      msgComp.append(msgRest.substring(0, idx == -1 ? msgRest.length() : idx));
    }
    cir.setReturnValue(msgComp.withStyle(ChatFormatting.WHITE));
  }
}
