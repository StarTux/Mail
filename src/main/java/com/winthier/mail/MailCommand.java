package com.winthier.mail;

import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.event.player.PluginPlayerEvent;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class MailCommand implements CommandExecutor {
    final MailPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        String cmd = args.length > 0 ? args[0].toLowerCase() : null;
        if (cmd == null) {
            List<SQLMail> mails = plugin.db.find(SQLMail.class)
                .eq("owner", uuid)
                .eq("recipient", uuid)
                .eq("read", false)
                .findList();
            Msg.info(player, "You have " + mails.size() + " unread mails.");
            for (SQLMail mail: mails) {
                Component tooltip = Component.text()
                    .append(Component.text("/mail read " + mail.getId(), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text("Read this mail.", NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false)
                    .build();
                sender.sendMessage(Component.text()
                                   .append(Component.text("[" + mail.getId() + "]", NamedTextColor.GRAY))
                                   .append(Component.space())
                                   .append(Component.text(mail.getSenderName(), NamedTextColor.AQUA))
                                   .append(Component.text(":", NamedTextColor.GRAY))
                                   .append(Component.space())
                                   .append(mail.getShortMessageComponent())
                                   .hoverEvent(tooltip)
                                   .clickEvent(ClickEvent.runCommand("/mail read " + mail.getId()))
                                   .build());
            }
            Msg.raw(player, " ",
                    Msg.button("&a[Mailto]",
                               null,
                               "&a/mailto&2 <player> <message>\n&r&oWrite someone a mail.",
                               "/mailto ",
                               ChatColor.GREEN),
                    "  ",
                    Msg.button("&9[All]",
                               null,
                               "&a/mail all\n&r&oView all mails.",
                               "/mail all",
                               ChatColor.BLUE));
        } else if (cmd.equals("all")) {
            List<SQLMail> mails = plugin.db.find(SQLMail.class).eq("owner", uuid).findList();
            Msg.info(player, "You have " + mails.size() + " mails.");
            for (SQLMail mail: mails) {
                String chat = "&a[" + mail.getId() + "] &b"
                    + mail.getSenderName() + ": &r" + mail.getShortMessage();
                String tooltip = "&a/mail read " + mail.getId() + "\n&r&oRead this mail.";
                String click = "/mail read " + mail.getId();
                Msg.raw(player, Msg.button(chat, null, tooltip, click, ChatColor.GREEN));
            }
        } else if (cmd.equals("read") && args.length == 2) {
            int mailId;
            try {
                mailId = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                mailId = -1;
            }
            if (mailId < 0) return true;
            SQLMail mail = plugin.db.find(SQLMail.class)
                .eq("id", mailId)
                .eq("owner", uuid)
                .findUnique();
            if (mail == null) return true;
            sender.sendMessage("");
            mail.display(sender);
            if (player != null) {
                Msg.raw(player, " ",
                        Msg.button("&a[Reply]",
                                   null,
                                   "&a/mailto " + mail.getSenderName() + "\n&r&oReply to this mail.",
                                   "/mailto " + mail.getSenderName() + " ",
                                   ChatColor.GREEN),
                        "  ",
                        Msg.button("&9[Mail]",
                                   null,
                                   "&a/mail\n&r&oCheck for more mail.",
                                   "/mail",
                                   ChatColor.BLUE));
            }
            sender.sendMessage("");
            if (!mail.isRead()) {
                mail.setRead(true);
                plugin.db.saveAsync(mail, unused -> plugin.updateSidebarList());
            }
            PluginPlayerEvent.Name.READ_MAIL.ultimate(plugin, player)
                .detail(Detail.INDEX, mailId)
                .call();
        } else {
            return false;
        }
        return true;
    }
}
