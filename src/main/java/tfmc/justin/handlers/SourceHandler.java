package tfmc.justin.handlers;

import me.Plugins.TLibs.Objects.API.ItemAPI;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import tfmc.justin.config.GeigerConfiguration;
import tfmc.justin.metrics.UsageStats;
import tfmc.justin.models.ItemReward;
import tfmc.justin.models.TierReward;

import java.util.List;
import java.util.Random;

// ====================================
// Handles radioactive source location and collection
// ====================================
public class SourceHandler {
    
    private static final String DEAD_GEIGER_PATH = "m.TOOLS.DEAD_GEIGER_COUNTER";
    
    private final JavaPlugin plugin;
    private final GeigerConfiguration config;
    private final ItemAPI api;
    private final Random random = new Random();
    
    private Location sourceLocation;
    
    public SourceHandler(JavaPlugin plugin, GeigerConfiguration config, ItemAPI api) {
        this.plugin = plugin;
        this.config = config;
        this.api = api;
    }
    
    // ====================================
    // Get current source location
    // ====================================
    public Location getSourceLocation() {
        return sourceLocation;
    }
    
    // ====================================
    // Move source to a random location within configured bounds
    // ====================================
    public void moveSourceToRandomLocation() {
        double randomX = config.getMinX() + (config.getMaxX() - config.getMinX()) * random.nextDouble();
        double randomZ = config.getMinZ() + (config.getMaxZ() - config.getMinZ()) * random.nextDouble();
        moveSourceToLocation(randomX, randomZ);
    }

    // ====================================
    // Move source to specific X/Z coordinates (Y snaps to surface)
    // ====================================
    public void moveSourceToLocation(double x, double z) {
        double surfaceY = config.getWorld().getHighestBlockYAt((int)x, (int)z) + 1.0;

        sourceLocation = new Location(config.getWorld(), x, surfaceY, z);

        plugin.getLogger().info(String.format("Radioactive source moved to X = %.1f Z = %.1f",
            x, z));
    }

    // ====================================
    // Check if player is close enough to collect the source
    // ====================================
    public void tryCollectSource(Player player, double distance, EquipmentSlot geigerSlot) {
        if (distance > config.getCollectionDistance()) {
            return;
        }

        collectSource(player, geigerSlot);
    }

    private void collectSource(Player player, EquipmentSlot geigerSlot) {
        UsageStats.getInstance().recordSourceCollected();
        moveSourceToRandomLocation();
        notifyPlayerOfCollection(player);
        replaceGeigerWithDeadVersion(player, geigerSlot);
        giveReward(player);
    }

    private void notifyPlayerOfCollection(Player player) {
        player.sendMessage(config.getMessageFoundSource());
    }

    private void replaceGeigerWithDeadVersion(Player player, EquipmentSlot geigerSlot) {
        // Remove active Geiger Counter from whichever hand held it
        if (geigerSlot == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Give dead Geiger Counter
        try {
            ItemStack deadGeiger = api.getCreator().getItemFromPath(DEAD_GEIGER_PATH).clone();
            player.getInventory().addItem(deadGeiger);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            player.sendMessage(config.getMessageDeadGeiger());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give dead Geiger Counter to " + player.getName() + ": " + e.getMessage());
        }
    }
    
    // ====================================
    // Give a random reward to the player based on tier weights
    // ====================================
    private void giveReward(Player player) {
        List<TierReward> tiers = config.getTierRewards();
        if (tiers.isEmpty()) {
            return;
        }
        
        // Select a tier based on weights
        TierReward selectedTier = selectRandomTier(tiers);
        if (selectedTier == null || selectedTier.isEmpty()) {
            return;
        }
        
        // Select a random item from the tier
        List<ItemReward> tierItems = selectedTier.getItems();
        ItemReward randomItem = tierItems.get(random.nextInt(tierItems.size()));
        
        // Give the item to the player
        giveRewardItem(player, randomItem, selectedTier.getTierName());
    }
    
    // ====================================
    // Select a random tier based on weights
    // Uses cumulative weight distribution
    // ====================================
    private TierReward selectRandomTier(List<TierReward> tiers) {
        // Calculate total weight
        double totalWeight = 0.0;
        for (TierReward tier : tiers) {
            totalWeight += tier.getWeight();
        }
        
        // Generate random value between 0 and total weight
        double randomValue = random.nextDouble() * totalWeight;
        
        // Find which tier this value falls into
        double cumulativeWeight = 0.0;
        for (TierReward tier : tiers) {
            cumulativeWeight += tier.getWeight();
            if (randomValue <= cumulativeWeight) {
                return tier;
            }
        }
        
        // Fallback to last tier (shouldnt happen but why not)
        return tiers.get(tiers.size() - 1);
    }
    
    // ====================================
    // Give a specific reward item to the player
    // ====================================
    private void giveRewardItem(Player player, ItemReward reward, String tierName) {
        try {
            ItemStack rewardItem = createRewardItem(reward);
            player.getInventory().addItem(rewardItem);
            plugin.getLogger().info(player.getName() + " received " + tierName + " reward: " + reward.getOutputItem());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give reward to " + player.getName() + ": " + e.getMessage());
        }
    }
    
    // ====================================
    // Create an ItemStack for the reward
    // ====================================
    private ItemStack createRewardItem(ItemReward reward) {
        String itemPath = parseItemPath(reward.getOutputItem());
        int amount = reward.getOutputAmount();
        
        ItemStack item = api.getCreator().getItemFromPath(itemPath.toLowerCase());
        if (item == null) {
            throw new IllegalArgumentException("Invalid item path: " + itemPath);
        }
        
        item = item.clone();
        item.setAmount(amount);
        return item;
    }
    
    private String parseItemPath(String rawPath) {
        if (rawPath.contains(":")) {
            return rawPath.split(":")[0];
        }
        return rawPath;
    }
}
