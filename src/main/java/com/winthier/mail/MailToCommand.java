package com.winthier.mail;

import com.winthier.playercache.PlayerCache;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class MailToCommand implements CommandExecutor {
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
        StringBuilder sb = new StringBuilder(args[1]);
        for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
        String message = sb.toString();
        SQLMail mail = new SQLMail();
        mail.setSender(senderUuid);
        mail.setRecipient(recipient.getUuid());
        mail.setMessage(message);
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
        }
        return true;
    }
}
