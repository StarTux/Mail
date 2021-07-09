package com.winthier.mail;

import com.winthier.playercache.PlayerCache;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.command.CommandSender;

@Data
@Table(name = "mails")
public final class SQLMail {
    @Id private Integer id;

    @Column(nullable = false)
    private UUID owner;

    @Column(nullable = false)
    private UUID sender;

    @Column(nullable = false)
    private UUID recipient;

    @Column(nullable = false, length = 4096)
    private String message;

    @Column(nullable = false)
    private boolean read;

    @Column(nullable = false)
    private Date created;

    public SQLMail() { }

    public SQLMail(final SQLMail o) {
        this.id = o.id;
        this.owner = o.owner;
        this.sender = o.sender;
        this.recipient = o.recipient;
        this.message = o.message;
        this.read = o.read;
        this.created = o.created;
    }

    String getShortMessage() {
        if (message == null) return "";
        if (message.length() < 16) return message;
        return message.substring(0, 16);
    }

    String getSenderName() {
        if (MailPlugin.SERVER_UUID.equals(sender)) return "The Server";
        return PlayerCache.nameForUuid(sender);
    }

    String getRecipientName() {
        return PlayerCache.nameForUuid(recipient);
    }

    public void setMessageComponent(Component component) {
        message = GsonComponentSerializer.gson().serialize(component);
    }

    public Component getMessageComponent() {
        return message.startsWith("{")
            ? GsonComponentSerializer.gson().deserialize(message)
            : Component.text(message, NamedTextColor.WHITE);
    }

    public Component getShortMessageComponent() {
        if (!message.startsWith("{")) return Component.text(getShortMessage());
        Component messageComponent = getMessageComponent();
        int length = 0;
        TextComponent.Builder result = Component.text();
        int total = 0;
        for (Component child : messageComponent.children()) {
            if (!(child instanceof TextComponent)) {
                result.append(child);
                continue;
            }
            TextComponent textChild = (TextComponent) child;
            int len = textChild.content().length();
            if (total > 0 && total + len >= 16) break;
            result.append(textChild);
            total += len;
        }
        return result.build();
    }

    public void display(CommandSender viewer) {
        Msg.send(viewer, "&bFrom: &r%s", getSenderName());
        Msg.send(viewer, "&bTo: &r%s", getRecipientName());
        viewer.sendMessage(Component.text()
                           .append(Component.text("Message:", NamedTextColor.AQUA))
                           .append(Component.space())
                           .append(getMessageComponent()));
    }
}
