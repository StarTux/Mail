package com.winthier.mail;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.font.Emoji;
import com.winthier.playercache.PlayerCache;

final class MailToCommand extends AbstractCommand<MailPlugin> {
    protected MailToCommand(final MailPlugin plugin) {
        super(plugin, "mailto");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("<player> <message>")
            .completers(PlayerCache.NAME_COMPLETER, Emoji.PUBLIC_COMPLETER, CommandArgCompleter.REPEAT)
            .description("Send mail")
            .senderCaller(plugin.mailCommand::send);
    }
}
