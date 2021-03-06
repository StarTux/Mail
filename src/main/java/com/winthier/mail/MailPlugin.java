package com.winthier.mail;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.winthier.sql.SQLDatabase;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@Getter
public final class MailPlugin extends JavaPlugin implements Listener {
    static final UUID SERVER_UUID = new UUID(0, 0);
    SQLDatabase db;
    final Map<UUID, BukkitTask> tasks = new HashMap<>();
    MailToCommand mailToCommand = new MailToCommand(this);
    MailCommand mailCommand = new MailCommand(this);
    final Set<UUID> sidebarList = new HashSet<>();

    @Override
    public void onEnable() {
        db = new SQLDatabase(this);
        db.registerTables(SQLMail.class);
        db.createAllTables();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("mail").setExecutor(mailCommand);
        getCommand("mailto").setExecutor(mailToCommand);
        getServer().getScheduler().runTaskTimer(this, this::updateSidebarList, 100, 100);
    }

    void cancelTask(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onPlayerSidebar(PlayerSidebarEvent event) {
        if (!sidebarList.contains(event.getPlayer().getUniqueId())) return;
        event.addLines(this, Priority.HIGH, ChatColor.AQUA + "You have " + ChatColor.YELLOW + "/mail");
    }

    void updateSidebarList() {
        Set<UUID> onlineIds = getServer().getOnlinePlayers().stream()
            .map(Player::getUniqueId)
            .collect(Collectors.toCollection(HashSet::new));
        if (onlineIds.isEmpty()) {
            sidebarList.clear();
            return;
        }
        db.find(SQLMail.class)
            .in("owner", onlineIds)
            .eq("read", false)
            .findListAsync(mails -> {
                    sidebarList.clear();
                    for (SQLMail mail : mails) {
                        sidebarList.add(mail.getOwner());
                    }
                });
    }
}
