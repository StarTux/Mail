package com.winthier.mail;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.event.player.PluginPlayerEvent;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

final class MailCommand extends AbstractCommand<MailPlugin> {
    protected MailCommand(final MailPlugin plugin) {
        super(plugin, "mail");
    }

    @Override
    protected void onEnable() {
        rootNode.description("View unread mail")
            .playerCaller((player, args) -> {
                    if (args.length != 0) return false;
                    plugin.db.find(SQLMail.class)
                        .eq("owner", player.getUniqueId())
                        .eq("recipient", player.getUniqueId())
                        .eq("read", false)
                        .findListAsync(mails -> listMails(player, mails, false));
                    return true;
                });
        rootNode.addChild("all").denyTabCompletion()
            .denyTabCompletion()
            .description("View all mail")
            .playerCaller((player, args) -> {
                    if (args.length != 0) return false;
                    plugin.db.find(SQLMail.class)
                        .eq("owner", player.getUniqueId())
                        .findListAsync(mails -> listMails(player, mails, true));
                    return true;
                });
        rootNode.addChild("read").arguments("<id>")
            .denyTabCompletion()
            .description("Read a mail")
            .playerCaller((player, args) -> {
                    if (args.length != 1) return false;
                    plugin.db.find(SQLMail.class)
                        .eq("id", requireMailId(args[0]))
                        .eq("owner", player.getUniqueId())
                        .findUniqueAsync(mail -> readMail(player, mail));
                    return true;
                });
        rootNode.addChild("delete").arguments("<id>")
            .denyTabCompletion()
            .description("Delete a mail")
            .playerCaller((player, args) -> {
                    if (args.length != 1) return false;
                    plugin.db.find(SQLMail.class)
                        .eq("id", requireMailId(args[0]))
                        .eq("owner", player.getUniqueId())
                        .deleteAsync(count -> deletedMail(player, count));
                    return true;
                });
    }

    protected int requireMailId(String arg) {
        int mailId;
        try {
            mailId = Integer.parseInt(arg);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Number expected: " + arg);
        }
        if (mailId < 1) throw new CommandWarn("Invalid id: " + mailId);
        return mailId;
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
                      .append(Component.text(mail.getSenderName() + ": ", NamedTextColor.GRAY))
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
            if (!isAll) {
                buttons.add(Component.text("[All]", NamedTextColor.BLUE)
                            .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                                            Component.text("/mail all", NamedTextColor.GREEN),
                                            Component.text("View read and unread messages", NamedTextColor.GRAY),
                                        })))
                            .clickEvent(ClickEvent.runCommand("/mail all")));
            }
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
            Component deleteTooltip = Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                    Component.text("/mail delete " + mail.getId(), NamedTextColor.RED),
                    Component.text("Delete this mail", NamedTextColor.GRAY),
                });
            buttons.add(Component.text("[Delete]", NamedTextColor.RED)
                        .hoverEvent(HoverEvent.showText(deleteTooltip))
                        .clickEvent(ClickEvent.runCommand("/mail delete " + mail.getId())));
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

    protected void deletedMail(Player player, int count) {
        if (count == 0) {
            player.sendMessage(Component.text("Mail not found!", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Mail deleted!", NamedTextColor.RED));
        }
    }
}
