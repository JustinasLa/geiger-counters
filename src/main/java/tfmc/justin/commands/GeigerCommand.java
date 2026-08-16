package tfmc.justin.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import tfmc.justin.managers.GeigerManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ====================================
// Admin command handler: /geiger <locate|move [x z]|reload>
// ====================================
public class GeigerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("locate", "move", "reload");

    private final GeigerManager manager;

    public GeigerCommand(GeigerManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "locate":
                handleLocate(sender);
                return true;
            case "move":
                handleMove(sender, args);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void handleLocate(CommandSender sender) {
        Location source = manager.getSourceHandler().getSourceLocation();
        if (source == null) {
            sender.sendMessage("§cNo active radioactive source - it is being relocated right now.");
            return;
        }

        sender.sendMessage(String.format("§aRadioactive source is at X = %.1f Z = %.1f (world: %s)",
            source.getX(), source.getZ(), source.getWorld().getName()));
    }

    // /geiger move       -> random location
    // /geiger move <x> <z> -> specific coordinates
    private void handleMove(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage("§7Searching for a new source location...");
            manager.getSourceHandler().moveSourceToRandomLocation()
                .thenAccept(location -> sendNewLocation(sender, location));
            return;
        }

        if (args.length != 3) {
            sender.sendMessage("§cUsage: /geiger move [x z]");
            return;
        }

        double x;
        double z;
        try {
            x = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cCoordinates must be numbers. Usage: /geiger move [x z]");
            return;
        }

        manager.getSourceHandler().moveSourceToLocation(x, z)
            .thenAccept(location -> sendNewLocation(sender, location));
    }

    // Called once the chunk load behind the move has resolved
    private void sendNewLocation(CommandSender sender, Location source) {
        if (source == null) {
            sender.sendMessage("§cThe source could not be moved.");
            return;
        }

        sender.sendMessage(String.format("§aRadioactive source moved to X = %.1f Z = %.1f",
            source.getX(), source.getZ()));
    }

    private void handleReload(CommandSender sender) {
        manager.reload();
        sender.sendMessage("§aGeiger Counter configuration reloaded.");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§eUsage: /geiger <locate|move [x z]|reload>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        return new ArrayList<>();
    }
}
