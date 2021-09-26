package com.winthier.mail;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.winthier.sql.SQLDatabase;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@Getter
public final class MailPlugin extends JavaPlugin implements Listener {
    @Getter private static MailPlugin instance;
    public static final UUID SERVER_UUID = new UUID(0, 0);
    SQLDatabase db;
    final Map<UUID, BukkitTask> tasks = new HashMap<>();
    MailToCommand mailToCommand = new MailToCommand(this);
    MailCommand mailCommand = new MailCommand(this);
    final Set<UUID> sidebarList = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        db = new SQLDatabase(this);
        db.registerTables(SQLMail.class);
        if (!db.createAllTables()) {
            throw new IllegalStateException("Failed to setup database");
        }
        long deletionTimespan = 1000L * 60L * 60L * 24L * 30L * 3L; // 3 months
        long now = System.currentTimeMillis();
        Date then = new Date(now - deletionTimespan);
        db.find(SQLMail.class).lt("created", then).deleteAsync(cnt -> {
                long delay = System.currentTimeMillis() - now;
                long s = delay / 1000L;
                long ms = delay % 1000L;
                getLogger().info("Deleted " + cnt + " mails older than " + then + " in " + s + "." + ms + "s");
            });
        getServer().getPluginManager().registerEvents(this, this);
        mailCommand.enable();
        mailToCommand.enable();
        getServer().getScheduler().runTaskTimer(this, this::updateSidebarList, 100, 100);
    }

    void cancelTask(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onPlayerSidebar(PlayerSidebarEvent event) {
        if (!sidebarList.contains(event.getPlayer().getUniqueId())) return;
        event.add(this, Priority.HIGH,
                  Component.text("You have ", NamedTextColor.AQUA)
                  .append(Component.text("/mail", NamedTextColor.YELLOW)));
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
