package com.winthier.mail;

import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.event.player.PluginPlayerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
            plugin.db.find(SQLMail.class)
                .eq("owner", uuid)
                .eq("recipient", uuid)
                .eq("read", false)
                .findListAsync(mails -> listMails(player, mails, false));
            return true;
        } else if (cmd.equals("all")) {
            plugin.db.find(SQLMail.class)
                .eq("owner", uuid)
                .eq("recipient", uuid)
                .findListAsync(mails -> listMails(player, mails, true));
            return true;
        } else if (cmd.equals("read") && args.length == 2) {
            int mailId;
            try {
                mailId = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                mailId = -1;
            }
            if (mailId < 0) return true;
            plugin.db.find(SQLMail.class)
                .eq("id", mailId)
                .eq("owner", uuid)
                .findUniqueAsync(mail -> readMail(player, mail));
            return true;
        } else {
            return false;
        }
    }

    protected void listMails(Player player, List<SQLMail> mails, boolean isAll) {
        if (mails.isEmpty()) {
            player.sendMessage(Component.text("You have no" + (isAll ? "" : " unread") + " mail",
                                              NamedTextColor.RED));
            return;
        }
        List<ComponentLike> lines = new ArrayList<>();
        lines.add(Component.text().color(NamedTextColor.WHITE)
                  .content("You have ")
                  .append(Component.text(mails.size(), NamedTextColor.GREEN))
                  .append(Component.text(isAll ? " mails" : " unread mails"))
                  .build());
        for (SQLMail mail: mails) {
            ComponentLike tooltip = Component.text()
                .append(Component.text("/mail read " + mail.getId(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("Read this mail.", NamedTextColor.GRAY));
            lines.add(Component.text()
                      .append(Component.text("[" + mail.getId() + "] ", NamedTextColor.GRAY))
                      .append(Component.text(mail.getSenderName(), NamedTextColor.AQUA))
                      .append(Component.text(": ", NamedTextColor.GRAY))
                      .append(mail.getShortMessageComponent())
                      .hoverEvent(HoverEvent.showText(tooltip))
                      .clickEvent(ClickEvent.runCommand("/mail read " + mail.getId())));
        }
        do {
            List<ComponentLike> buttons = new ArrayList<>();
            buttons.add(Component.text("[MailTo]", NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                                        Component.text("/mailto <player> <message>", NamedTextColor.GREEN),
                                        Component.text("Send mail", NamedTextColor.GRAY),
                                    })))
                        .clickEvent(ClickEvent.suggestCommand("/mailto ")));
            buttons.add(Component.text("[All]", NamedTextColor.BLUE)
                        .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                                        Component.text("/mail all", NamedTextColor.GREEN),
                                        Component.text("View read and unread messages", NamedTextColor.GRAY),
                                    })))
                        .clickEvent(ClickEvent.runCommand("/mail all")));
            lines.add(Component.join(JoinConfiguration.separator(Component.space()), buttons));
        } while (false);
        player.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), lines));
    }

    protected void readMail(Player player, SQLMail mail) {
        if (mail == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.addAll(mail.makeDisplay());
        do {
            List<Component> buttons = new ArrayList<>(2);
            if (!plugin.SERVER_UUID.equals(mail.getSender())) {
                Component replyTooltip = Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                        Component.text("/mailto " + mail.getSenderName(),
                                       NamedTextColor.GREEN),
                        Component.text("Reply to this mail", NamedTextColor.GRAY),
                    });
                buttons.add(Component.text("[Reply]", NamedTextColor.GREEN)
                            .hoverEvent(HoverEvent.showText(replyTooltip))
                            .clickEvent(ClickEvent.suggestCommand("/mailto " + mail.getSenderName() + " ")));
            }
            Component mailTooltip = Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                    Component.text("/mail", NamedTextColor.GREEN),
                    Component.text("Check for more mail", NamedTextColor.GRAY),
                });
            buttons.add(Component.text("[Mail]", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(mailTooltip))
                        .clickEvent(ClickEvent.runCommand("/mail")));
            lines.add(Component.join(JoinConfiguration.separator(Component.space()), buttons));
        } while (false);
        lines.add(Component.empty());
        player.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), lines));
        if (!mail.isRead()) {
            mail.setRead(true);
            plugin.db.updateAsync(mail, unused -> plugin.updateSidebarList(), "read");
        }
        PluginPlayerEvent.Name.READ_MAIL.ultimate(plugin, player)
            .detail(Detail.INDEX, mail.getId())
            .call();
    }
}
