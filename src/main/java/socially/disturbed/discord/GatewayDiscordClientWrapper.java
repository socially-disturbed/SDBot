package socially.disturbed.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import socially.disturbed.command.CommandDto;
import socially.disturbed.command.CommandResolver;
import socially.disturbed.function.SDFunctionsImpl;

public class GatewayDiscordClientWrapper {
    private final CommandResolver commandResolver;
    private static GatewayDiscordClient gateway;
    private static String discordToken;

    public GatewayDiscordClientWrapper() {
        discordToken = System.getenv("disctoken");
        if (discordToken == null) {
            throw new RuntimeException("No discord token");
        }

        if (gateway == null) {
            login();
        }
        commandResolver = new CommandResolver(new SDFunctionsImpl());
    }

    public void init() {
        startOnReadyEventListener();
        startMessageCreateEventListener();
        onDisconnect();
    }

    public GatewayDiscordClient getGateway() {
        return gateway;
    }

    private void login() {
        DiscordClient client = DiscordClient.create(discordToken);
        gateway = client.login().block();
    }

    private void startOnReadyEventListener() {
        gateway.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
                    User self = event.getSelf();
                    System.out.printf("Logged in as %s#%s%n", self.getUsername(), self.getDiscriminator());
                });
    }

    private void startMessageCreateEventListener() {
        gateway
                .on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .subscribe(this::handleMessage);
    }

    private void onDisconnect() {
        gateway.onDisconnect().block();
    }

    protected void handleMessage(Message message) {
        String messageString = message.getContent();
        if (message.getAuthor().get().isBot() && messageString.indexOf("!") != 0) return;
        if (messageString.indexOf("!") == 0) {
            CommandDto commandDto = new CommandDto(message);
            commandDto = commandResolver.invokeMethod(commandDto);
            if (commandDto.deleteCommandMsg()) {
                message.delete().subscribe();
            }
            if (commandDto.getReturnMsgChannelId() == null) {
                MessageChannel channel = message.getChannel().block();
                sendMessage(channel, commandDto.getReturningMsg(), commandDto.deleteLastChannelMsg());
            }
            else {
                String channelId = commandDto.getReturnMsgChannelId();
                sendMessage(channelId, commandDto.getReturningMsg(), commandDto.deleteLastChannelMsg());
            }
        }
    }

    public void sendMessage(MessageChannel channel, String message, boolean deleteLastMsg) {
        if (deleteLastMsg)
            deleteLastChannelMsg(channel);
        channel.createMessage(message).subscribe();
    }

    public void sendMessage(String channelId, String message, boolean deleteLastMsg) {
        MessageChannel channel = gateway.getChannelById(
                Snowflake.of(channelId)).cast(MessageChannel.class).block();
        if (deleteLastMsg)
            deleteLastChannelMsg(channel);
        channel.createMessage(message).subscribe();
    }

    public void deleteLastChannelMsg(MessageChannel channel) {
        try {
            channel.getLastMessage().block().delete().subscribe();
        } catch (Exception e) {
            System.out.println("Failed to delete last message, moving calmly forward");
        }
    }
}
