package com.winthier.mail;

import com.winthier.playercache.PlayerCache;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

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

    @Column(nullable = false, length = 1023)
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
}
