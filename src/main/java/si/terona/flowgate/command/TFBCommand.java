package si.terona.flowgate.command;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import si.terona.flowgate.FlowGate;

public final class TFBCommand extends Command {

    public TFBCommand() {
        super("flowgate", "flowgate.command.reload");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        FlowGate plugin = FlowGate.get();

        if (args.length == 0) {
            send(sender, plugin, "commands.usage");
            return;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {

            boolean success = plugin.reloadAll();

            if (!success) {
                sender.sendMessage(new TextComponent(
                        "§c[FlowGate] Reload failed. Check proxy console for details."
                ));
                return;
            }

            send(sender, plugin, "commands.reload");
            return;
        }

        send(sender, plugin, "commands.unknown");
    }

    private void send(CommandSender sender, FlowGate plugin, String path) {

        String prefix = plugin.getMessages().getString("prefix", "");
        String msg = plugin.getMessages().getString(path, "");

        if (msg == null || msg.isEmpty()) return;

        sender.sendMessage(
                new TextComponent((prefix + " " + msg).replace("&", "§"))
        );
    }
}
