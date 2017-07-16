package com.winthier.mail;

import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLDatabase;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@Getter
public final class MailPlugin extends JavaPlugin implements Listener {
    static final UUID SERVER_UUID = new UUID(0, 0);
    private SQLDatabase db;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    @Override
    public void onEnable() {
        db = new SQLDatabase(this);
        db.registerTables(SQLMail.class);
        db.createAllTables();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("mailto").setExecutor((sender, command, alias, args) -> mailtoCommand(sender, args));
        getCommand("mail").setExecutor((sender, command, alias, args) -> mailCommand(sender, args));
        for (Player player: getServer().getOnlinePlayers()) {
            launchTask(player);
        }
    }

    void launchTask(Player player) {
        cancelTask(player);
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, () -> remindPlayer(player), 20 * 5, 20 * 60);
        tasks.put(player.getUniqueId(), task);
    }

    void cancelTask(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        launchTask(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelTask(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        cancelTask(event.getPlayer());
    }

    void remindPlayer(Player player) {
        if (!player.isOnline()) {
            cancelTask(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        int count = db.find(SQLMail.class).eq("owner", uuid).eq("recipient", uuid).eq("read", false).findRowCount();
        if (count > 0) {
            Msg.sendActionBar(player, "&aYou have mail");
            Msg.raw(player, Msg.button("&rYou have mail. &a[Click here]",
                                       null,
                                       "&a/mail\n&r&oView your mail.",
                                       "/mail",
                                       ChatColor.GREEN));
        }
    }

    boolean mailCommand(CommandSender sender, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        String cmd = args.length > 0 ? args[0].toLowerCase() : null;
        if (cmd == null) {
            List<SQLMail> mails = db.find(SQLMail.class).eq("owner", uuid).eq("recipient", uuid).eq("read", false).findList();
            Msg.info(player, "You have " + mails.size() + " unread mails.");
            for (SQLMail mail: mails) {
                Msg.raw(player, Msg.button("&a[" + mail.getId() + "] &b" + mail.getSenderName() + ": &r" + mail.getShortMessage(),
                                           null,
                                           "&a/mail read " + mail.getId() + "\n&r&oRead this mail.",
                                           "/mail read " + mail.getId(),
                                           ChatColor.GREEN));
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
            List<SQLMail> mails = db.find(SQLMail.class).eq("owner", uuid).eq("recipient", uuid).findList();
            Msg.info(player, "You have " + mails.size() + " mails.");
            for (SQLMail mail: mails) {
                Msg.raw(player, Msg.button("&a[" + mail.getId() + "] &b" + mail.getSenderName() + ": &r" + mail.getShortMessage(),
                                           null,
                                           "&a/mail read " + mail.getId() + "\n&r&oRead this mail.",
                                           "/mail read " + mail.getId(),
                                           ChatColor.GREEN));
            }
        } else if (cmd.equals("read") && args.length == 2) {
            int mailId;
            try {
                mailId = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                mailId = -1;
            }
            if (mailId < 0) return true;
            SQLMail mail = db.find(SQLMail.class).eq("id", mailId).eq("owner", uuid).findUnique();
            if (mail == null) return true;
            sender.sendMessage("");
            Msg.send(player, "&bFrom: &r%s", mail.getSenderName());
            Msg.send(player, "&bTo: &r%s", mail.getRecipientName());
            Msg.send(player, "&bMessage: &r%s", mail.getMessage());
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
            sender.sendMessage("");
            if (!mail.isRead()) {
                mail.setRead(true);
                db.save(mail);
            }
        } else {
            return false;
        }
        return true;
    }

    boolean mailtoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        PlayerCache recipient = PlayerCache.forName(args[0]);
        if (recipient == null) {
            Msg.warn(sender, "Player not found: %s", args[0]);
            return true;
        }
        UUID senderUuid = sender instanceof Player ? ((Player)sender).getUniqueId() : SERVER_UUID;
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
        db.save(mail);
        Msg.info(sender, "Mail sent to %s", recipient.getName());
        if (!recipient.getUuid().equals(senderUuid)) {
            db.save(mailCopy);
            Player target = getServer().getPlayer(recipient.getUuid());
            if (target != null) {
                Msg.sendActionBar(target, "&aYou have mail");
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
