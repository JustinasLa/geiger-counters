package tfmc.justin.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import tfmc.justin.managers.GeigerManager;
import tfmc.justin.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ====================================
// Admin command handler: /geiger <locate|move [x z]|limits [player]|resetlimits <player>|reload>
// ====================================
public class GeigerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("locate", "move", "limits", "resetlimits", "reload");

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
            // Singular spellings are aliases - both read naturally
            case "limit":
            case "limits":
                handleLimits(sender, args);
                return true;
            case "resetlimit":
            case "resetlimits":
                handleResetLimits(sender, args);
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

    // /geiger limits <player> -> how many collections that player has left
    private void handleLimits(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /geiger limits <player>");
            return;
        }

        if (!manager.getConfiguration().isLimitEnabled()) {
            sender.sendMessage("§7Drop limits are disabled in the config.");
            return;
        }

        OfflinePlayer target = resolvePlayer(sender, args[1]);
        if (target == null) {
            return;
        }

        int remaining = manager.getDropLimitManager().getRemaining(target.getUniqueId());
        int max = manager.getConfiguration().getLimitDrops();
        String window = Utils.formatDuration(manager.getConfiguration().getLimitWindowMillis());

        sender.sendMessage(String.format("§a%s has %d/%d drops left per %s.",
            args[1], remaining, max, window));

        if (remaining == 0) {
            long wait = manager.getDropLimitManager().getMillisUntilNextDrop(target.getUniqueId());
            sender.sendMessage("§7Next drop available in " + Utils.formatDuration(wait) + ".");
        }
    }

    // /geiger resetlimits <player> -> clear that player's collection history
    private void handleResetLimits(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /geiger resetlimits <player>");
            return;
        }

        OfflinePlayer target = resolvePlayer(sender, args[1]);
        if (target == null) {
            return;
        }

        manager.getDropLimitManager().reset(target.getUniqueId());
        sender.sendMessage("§aDrop limit reset for " + args[1] + ".");
    }

    // Offline lookup so limits can be inspected while the player is away
    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§cUnknown player: " + name);
            return null;
        }
        return target;
    }

    private void handleReload(CommandSender sender) {
        manager.reload();
        sender.sendMessage("§aGeiger Counter configuration reloaded.");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§eUsage: /geiger <locate|move [x z]|limits <player>|resetlimits <player>|reload>");
    }

    private static boolean isLimitSubcommand(String argument) {
        return argument.equalsIgnoreCase("limit") || argument.equalsIgnoreCase("limits")
            || argument.equalsIgnoreCase("resetlimit") || argument.equalsIgnoreCase("resetlimits");
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

        // Both limit subcommands (either spelling) take a player name
        if (args.length == 2 && isLimitSubcommand(args[0])) {
            List<String> matches = new ArrayList<>();
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    matches.add(player.getName());
                }
            }
            return matches;
        }

        return new ArrayList<>();
    }
}
