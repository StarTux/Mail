package com.winthier.mail;

import com.cavetale.core.font.Emoji;
import com.cavetale.core.font.GlyphPolicy;
import com.winthier.chat.ChatPlugin;
import com.winthier.playercache.PlayerCache;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class MailToCommand implements TabExecutor {
    final MailPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length < 2) return false;
        PlayerCache recipient = PlayerCache.forName(args[0]);
        if (recipient == null) {
            Msg.warn(sender, "Player not found: %s", args[0]);
            return true;
        }
        UUID senderUuid = sender instanceof Player
            ? ((Player) sender).getUniqueId()
            : MailPlugin.SERVER_UUID;
        if (ChatPlugin.getInstance().doesIgnore(recipient.getUuid(), senderUuid)) return true;
        StringBuilder sb = new StringBuilder(args[1]);
        for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
        String message = sb.toString();
        SQLMail mail = new SQLMail();
        mail.setSender(senderUuid);
        mail.setRecipient(recipient.getUuid());
        if (sender.hasPermission("mail.emoji") && message.contains(":")) {
            Component component = Emoji.replaceText(message, GlyphPolicy.PUBLIC, false).asComponent();
            mail.setMessageComponent(component);
        } else {
            mail.setMessage(message);
        }
        mail.setCreated(new Date());
        mail.setOwner(recipient.getUuid());
        SQLMail mailCopy = new SQLMail(mail);
        mailCopy.setOwner(senderUuid);
        mailCopy.setRead(true);
        plugin.db.save(mail);
        Msg.info(sender, "Mail sent to %s", recipient.getName());
        if (!recipient.getUuid().equals(senderUuid)) {
            plugin.db.save(mailCopy);
            Player target = plugin.getServer().getPlayer(recipient.getUuid());
            if (target != null) {
                Msg.raw(target, Msg.button("&rYou have mail. &a[Click here]",
                                           null,
                                           "&a/mail\n&r&oView your mail.",
                                           "/mail",
                                           ChatColor.GREEN));
            }
        } else {
            mail.display(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) return null;
        String arg = args[args.length - 1];
        if (sender.hasPermission("mail.emoji")) {
            return Emoji.tabComplete(arg);
        }
        return Collections.emptyList();
    }
}
