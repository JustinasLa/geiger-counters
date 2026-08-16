package tfmc.justin.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import tfmc.justin.models.ItemReward;
import tfmc.justin.models.TierReward;
import tfmc.justin.utils.Utils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// ====================================
// Handles loading and storing all Geiger Counter configuration
// ====================================
public class GeigerConfiguration {
    
    private final JavaPlugin plugin;
    
    // World settings
    private World world;
    private double minX, maxX, minZ, maxZ;

    // Spawn location filters
    private SpawnFilterConfig spawnFilters = new SpawnFilterConfig();

    // Detection settings
    private double collectionDistance;
    private double maxDetectionDistance;
    private double closeRangeThreshold;
    private double threeRingsDistance;
    private double twoRingsDistance;
    
    // Messages
    private String messageFoundSource;
    private String messageDeadGeiger;
    
    // Colors
    private ColorConfig closeRangeStartColor;
    private ColorConfig closeRangeEndColor;
    private ColorConfig farRangeStartColor;
    private ColorConfig farRangeEndColor;
    
    // Rewards
    private final List<TierReward> tierRewards = new ArrayList<>();
    
    public GeigerConfiguration(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    // ====================================
    // Load all configuration from config.yml
    // ====================================
    public void load() {
        loadWorld();
        loadSearchArea();
        loadSpawnFilters();
        loadDetectionSettings();
        loadColorSettings();
        loadMessages();
        loadRewards();
    }
    
    private void loadWorld() {
        String worldName = plugin.getConfig().getString("source.world");
        world = Bukkit.getWorld(worldName);
    }
    
    private void loadSearchArea() {
        minX = plugin.getConfig().getDouble("source.top-left.x");
        maxX = plugin.getConfig().getDouble("source.bottom-right.x");
        minZ = plugin.getConfig().getDouble("source.top-left.z");
        maxZ = plugin.getConfig().getDouble("source.bottom-right.z");
        
        // Make sure that <min> is always less than <max>
        if (minX > maxX) {
            double temp = minX;
            minX = maxX;
            maxX = temp;
        }
        if (minZ > maxZ) {
            double temp = minZ;
            minZ = maxZ;
            maxZ = temp;
        }
    }
    
    // ====================================
    // Load the rules that decide where the source is allowed to spawn
    // ====================================
    private void loadSpawnFilters() {
        String base = "source.spawn-filters.";
        SpawnFilterConfig filters = new SpawnFilterConfig();

        filters.maxAttempts = Math.max(1, plugin.getConfig().getInt(base + "max-attempts", 50));
        filters.rejectLiquid = plugin.getConfig().getBoolean(base + "reject-liquid", true);
        filters.rejectVoid = plugin.getConfig().getBoolean(base + "reject-void", true);
        filters.minDistanceFromSpawn = plugin.getConfig().getDouble(base + "min-distance-from-spawn", 0.0);

        for (String name : plugin.getConfig().getStringList(base + "blocked-blocks")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().warning("Unknown material in spawn-filters.blocked-blocks: " + name);
                continue;
            }
            filters.blockedBlocks.add(material);
        }

        filters.worldGuardEnabled = plugin.getConfig().getBoolean(base + "worldguard.enabled", true);
        for (String region : plugin.getConfig().getStringList(base + "worldguard.blacklisted-regions")) {
            filters.blacklistedRegions.add(region.toLowerCase());
        }

        spawnFilters = filters;
    }

    private void loadDetectionSettings() {
        collectionDistance = plugin.getConfig().getDouble("detection.collection-distance", 20.0);
        maxDetectionDistance = plugin.getConfig().getDouble("detection.max-detection-distance", 2500.0);
        closeRangeThreshold = plugin.getConfig().getDouble("detection.close-range-threshold", 200.0);
        threeRingsDistance = plugin.getConfig().getDouble("detection.ring-thresholds.three-rings", 100.0);
        twoRingsDistance = plugin.getConfig().getDouble("detection.ring-thresholds.two-rings", 300.0);
    }
    
    private void loadMessages() {
        messageFoundSource = Utils.colorize(plugin.getConfig().getString("messages.found-source", 
            "&5You have found the source of Arcane Radiation! The source has moved."));
        messageDeadGeiger = Utils.colorize(plugin.getConfig().getString("messages.dead-geiger", 
            "&7Your Arcane Trace Detector has run out of fuel."));
    }
    
    // =========== Color Settings ======================
    private void loadColorSettings() {
        closeRangeStartColor = new ColorConfig(
            plugin.getConfig().getInt("colors.close-range.start.red", 255),
            plugin.getConfig().getInt("colors.close-range.start.green", 255),
            plugin.getConfig().getInt("colors.close-range.start.blue", 255)
        );
        closeRangeEndColor = new ColorConfig(
            plugin.getConfig().getInt("colors.close-range.end.red", 255),
            plugin.getConfig().getInt("colors.close-range.end.green", 0),
            plugin.getConfig().getInt("colors.close-range.end.blue", 255)
        );
        
        farRangeStartColor = new ColorConfig(
            plugin.getConfig().getInt("colors.far-range.start.red", 255),
            plugin.getConfig().getInt("colors.far-range.start.green", 0),
            plugin.getConfig().getInt("colors.far-range.start.blue", 255)
        );
        farRangeEndColor = new ColorConfig(
            plugin.getConfig().getInt("colors.far-range.end.red", 17),
            plugin.getConfig().getInt("colors.far-range.end.green", 0),
            plugin.getConfig().getInt("colors.far-range.end.blue", 17)
        );
    }
    
    // ====================================
    // Load tiered reward system from config
    // ====================================
    private void loadRewards() {
        tierRewards.clear();
        
        // Load tier weights
        ConfigurationSection weightsSection = plugin.getConfig().getConfigurationSection("drops.tier-weights");
        if (weightsSection == null) {
            plugin.getLogger().warning("No tier-weights section found in config!");
            return;
        }
        
        // Load tiers section
        ConfigurationSection tiersSection = plugin.getConfig().getConfigurationSection("drops.tiers");
        if (tiersSection == null) {
            plugin.getLogger().warning("No tiers section found in config!");
            return;
        }
        
        // Process each tier
        String[] tierNames = {"common", "uncommon", "rare", "epic", "legendary", "mythical"};
        for (String tierName : tierNames) {
            double weight = weightsSection.getDouble(tierName, 0.0);
            
            if (weight > 0) {
                TierReward tier = new TierReward(tierName, weight);
                
                // Load items for this tier
                List<String> tierItems = tiersSection.getStringList(tierName);
                for (String itemString : tierItems) {
                    ItemReward item = parseItemReward(itemString);
                    if (item != null) {
                        tier.addItem(item);
                    }
                }
                
                // Only add tier if it has items
                if (!tier.isEmpty()) {
                    tierRewards.add(tier);
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + tierRewards.size() + " reward tiers");
    }
    
    private ItemReward parseItemReward(String itemString) {
        // Split on the last colon to separate item path from amount
        int lastColonIndex = itemString.lastIndexOf(':');
        if (lastColonIndex == -1) {
            return null;
        }
        
        String itemPath = itemString.substring(0, lastColonIndex);
        String amountString = itemString.substring(lastColonIndex + 1);
        
        try {
            int amount = Integer.parseInt(amountString);
            return new ItemReward(itemPath, amount);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid amount in item reward: " + itemString);
            return null;
        }
    }
    
    // ===== GETTERS =====
    
    public World getWorld() { return world; }
    public double getMinX() { return minX; }
    public double getMaxX() { return maxX; }
    public double getMinZ() { return minZ; }
    public double getMaxZ() { return maxZ; }
    public SpawnFilterConfig getSpawnFilters() { return spawnFilters; }
    public double getCollectionDistance() { return collectionDistance; }
    public double getMaxDetectionDistance() { return maxDetectionDistance; }
    public double getCloseRangeThreshold() { return closeRangeThreshold; }
    public double getThreeRingsDistance() { return threeRingsDistance; }
    public double getTwoRingsDistance() { return twoRingsDistance; }
    public String getMessageFoundSource() { return messageFoundSource; }
    public String getMessageDeadGeiger() { return messageDeadGeiger; }
    public ColorConfig getCloseRangeStartColor() { return closeRangeStartColor; }
    public ColorConfig getCloseRangeEndColor() { return closeRangeEndColor; }
    public ColorConfig getFarRangeStartColor() { return farRangeStartColor; }
    public ColorConfig getFarRangeEndColor() { return farRangeEndColor; }
    public List<TierReward> getTierRewards() { return tierRewards; }


    // =========== Rules for valid source spawn locations ================
    public static class SpawnFilterConfig {
        private int maxAttempts = 50;
        private boolean rejectLiquid = true;
        private boolean rejectVoid = true;
        private double minDistanceFromSpawn = 0.0;
        private final Set<Material> blockedBlocks = EnumSet.noneOf(Material.class);
        private boolean worldGuardEnabled = true;
        private final Set<String> blacklistedRegions = new HashSet<>();

        public int getMaxAttempts() { return maxAttempts; }
        public boolean isRejectLiquid() { return rejectLiquid; }
        public boolean isRejectVoid() { return rejectVoid; }
        public double getMinDistanceFromSpawn() { return minDistanceFromSpawn; }
        public Set<Material> getBlockedBlocks() { return blockedBlocks; }
        public boolean isWorldGuardEnabled() { return worldGuardEnabled; }
        public Set<String> getBlacklistedRegions() { return blacklistedRegions; }
    }

    // =========== Store RGB color values ================
    public static class ColorConfig {
        private final int red;
        private final int green;
        private final int blue;
        
        public ColorConfig(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
        
        public int getRed() { return red; }
        public int getGreen() { return green; }
        public int getBlue() { return blue; }
    }
}

