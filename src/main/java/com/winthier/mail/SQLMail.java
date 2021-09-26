package com.winthier.mail;

import com.winthier.playercache.PlayerCache;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

@Data
@Table(name = "mails")
public final class SQLMail {
    public static final int SHORT_MESSAGE_LENGTH = 12;

    @Id private Integer id;

    @Column(nullable = false)
    private UUID owner;

    @Column(nullable = false)
    private UUID sender;

    @Column(nullable = false)
    private UUID recipient;

    @Column(nullable = false, columnDefinition = "INT(1) NOT NULL DEFAULT 0")
    private int contentType = 0; // 0: text, 1: json

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
        this.contentType = o.contentType;
        this.message = o.message;
        this.read = o.read;
        this.created = o.created;
    }

    String getShortMessage() {
        if (message == null) return "";
        if (message.length() < SHORT_MESSAGE_LENGTH) return message;
        return message.substring(0, SHORT_MESSAGE_LENGTH);
    }

    String getSenderName() {
        if (MailPlugin.SERVER_UUID.equals(sender)) return "The Server";
        return PlayerCache.nameForUuid(sender);
    }

    String getRecipientName() {
        return PlayerCache.nameForUuid(recipient);
    }

    public void setMessageComponent(Component component) {
        contentType = 1;
        message = GsonComponentSerializer.gson().serialize(component);
    }

    public Component getMessageComponent() {
        if (contentType == 0) {
            return Component.text(message, NamedTextColor.WHITE);
        } else {
            try {
                return GsonComponentSerializer.gson().deserialize(message);
            } catch (Exception e) {
                MailPlugin.getInstance().getLogger().warning("Error deserializing " + toString());
                e.printStackTrace();
                return Component.text(message, NamedTextColor.WHITE);
            }
        }
    }

    public Component getShortMessageComponent() {
        if (contentType == 0) return Component.text(getShortMessage());
        final Component component;
        try {
            component = GsonComponentSerializer.gson().deserialize(message);
        } catch (Exception e) {
            MailPlugin.getInstance().getLogger().warning("Error deserializing " + toString());
            e.printStackTrace();
            return Component.text(getShortMessage());
        }
        int length = 0;
        TextComponent.Builder result = Component.text();
        int total = 0;
        for (Component child : component.children()) {
            if (!(child instanceof TextComponent)) {
                result.append(child);
                continue;
            }
            TextComponent textChild = (TextComponent) child;
            int len = textChild.content().length();
            if (total > 0 && total + len >= SHORT_MESSAGE_LENGTH) break;
            result.append(textChild);
            total += len;
        }
        return result.build();
    }

    public List<Component> makeDisplay() {
        return List.of(Component.text("From ", NamedTextColor.GREEN)
                       .append(Component.text(getSenderName(), NamedTextColor.WHITE)),
                       Component.text("To ", NamedTextColor.GREEN)
                       .append(Component.text(getRecipientName(), NamedTextColor.WHITE)),
                       Component.text("Message ", NamedTextColor.GREEN)
                       .append(getMessageComponent()));
    }
}
