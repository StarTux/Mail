package com.winthier.mail;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.font.Emoji;
import com.cavetale.core.font.GlyphPolicy;
import com.cavetale.core.playercache.PlayerCache;
import com.winthier.chat.ChatPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
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
        rootNode.addChild("list").denyTabCompletion()
            .alias("all")
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
        rootNode.addChild("send").arguments("<player> <message>")
            .completers(PlayerCache.NAME_COMPLETER, Emoji.PUBLIC_COMPLETER, CommandArgCompleter.REPEAT)
            .description("Send mail")
            .senderCaller(this::send);
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
        List<ComponentLike> lines = new ArrayList<>();
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
        PluginPlayerEvent.Name.READ_MAIL.make(plugin, player)
            .detail(Detail.INDEX, mail.getId())
            .callEvent();
    }

    protected void deletedMail(Player player, int count) {
        if (count == 0) {
            player.sendMessage(Component.text("Mail not found!", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Mail deleted!", NamedTextColor.RED));
        }
    }

    protected boolean send(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        PlayerCache recipient = PlayerCache.forArg(args[0]);
        if (recipient == null) {
            throw new CommandWarn("Player not found: " + args[0]);
        }
        UUID senderUuid = sender instanceof Player
            ? ((Player) sender).getUniqueId()
            : MailPlugin.SERVER_UUID;
        if (ChatPlugin.getInstance().doesIgnore(recipient.getUuid(), senderUuid)) return true;
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
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
        plugin.db.saveAsync(mail, null);
        sender.sendMessage(Component.text("Mail sent to " + recipient.name, NamedTextColor.GREEN));
        if (!recipient.getUuid().equals(senderUuid)) {
            plugin.db.saveAsync(mailCopy, null);
            Player target = plugin.getServer().getPlayer(recipient.getUuid());
            if (target != null) {
                Component tooltip = Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                        Component.text("/mail", NamedTextColor.GREEN),
                        Component.text("View unread mail", NamedTextColor.GRAY),
                    });
                target.sendMessage(Component.text().color(NamedTextColor.WHITE)
                                   .content("You have mail. ")
                                   .append(Component.text("[Click Here]", NamedTextColor.GREEN))
                                   .hoverEvent(HoverEvent.showText(tooltip))
                                   .clickEvent(ClickEvent.runCommand("/mail")));
            }
        } else {
            sender.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), mail.makeDisplay()));
        }
        if (sender instanceof Player) {
            PluginPlayerEvent.Name.SEND_MAIL.make(plugin, (Player) sender)
                .detail(Detail.TARGET, recipient.getUuid())
                .callEvent();
        }
        return true;
    }
}
