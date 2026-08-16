package tfmc.justin.managers;

import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.TLibs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import tfmc.justin.config.GeigerConfiguration;
import tfmc.justin.handlers.ParticleRenderer;
import tfmc.justin.handlers.SourceHandler;
import tfmc.justin.validators.GeigerValidator;
import tfmc.justin.validators.SpawnLocationFilter;

// ====================================
// Main manager for the Geiger Counter system
// Coordinates between configuration, validation, particles, and source handling
// ====================================
public class GeigerManager {
    
    private static final long CHECK_INTERVAL_TICKS = 5L;
    
    private static GeigerManager instance;
    private final JavaPlugin plugin;
    
    // ===== COMPONENTS =====
    private GeigerConfiguration configuration;
    private GeigerValidator validator;
    private SpawnLocationFilter spawnFilter;
    private ParticleRenderer particleRenderer;
    private SourceHandler sourceHandler;
    private ItemAPI api;
    
    // ===== INITIALIZATION =====
    
    private GeigerManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    public static GeigerManager getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new GeigerManager(plugin);
        }
        return instance;
    }
    
    public static GeigerManager getInstance() {
        return instance;
    }
    
    // ====================================
    // Initialize the Geiger Counter system
    // ====================================
    public void initialize() {
        api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
        
        configuration = new GeigerConfiguration(plugin);
        configuration.load();
        
        validator = new GeigerValidator(plugin, api);
        
        particleRenderer = new ParticleRenderer(configuration);
        
        spawnFilter = new SpawnLocationFilter(plugin, configuration);

        sourceHandler = new SourceHandler(plugin, configuration, api, spawnFilter);
        
        // Spawn initial source
        sourceHandler.moveSourceToRandomLocation();
        
        // Start player checking task
        startPlayerCheckTask();
        
        plugin.getLogger().info("Geiger Counter plugin has been enabled.");
    }
    
    // ====================================
    // Reload configuration from disk
    // ====================================
    public void reload() {
        plugin.reloadConfig();
        configuration.load();
    }

    public SourceHandler getSourceHandler() {
        return sourceHandler;
    }

    public GeigerConfiguration getConfiguration() {
        return configuration;
    }

    private void startPlayerCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkAllPlayers, 0L, CHECK_INTERVAL_TICKS);
    }
    
    // ===== PLAYER CHECKING =====
    
    // ====================================
    // Check all online players to see if they're holding a Geiger Counter
    // in either hand
    // ====================================
    private void checkAllPlayers() {
        Location source = sourceHandler.getSourceLocation();
        if (source == null) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            EquipmentSlot geigerSlot = findGeigerSlot(player);

            if (geigerSlot != null) {
                handlePlayerWithGeiger(player, source, geigerSlot);
            }
        }
    }

    // Returns the hand holding a Geiger Counter, or null if neither
    private EquipmentSlot findGeigerSlot(Player player) {
        if (validator.isGeigerCounter(player.getInventory().getItemInMainHand())) {
            return EquipmentSlot.HAND;
        }
        if (validator.isGeigerCounter(player.getInventory().getItemInOffHand())) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private void handlePlayerWithGeiger(Player player, Location source, EquipmentSlot geigerSlot) {
        double distance = calculateHorizontalDistance(player.getLocation(), source);
        particleRenderer.showParticleEffect(player, distance);
        sourceHandler.tryCollectSource(player, distance, geigerSlot);
    }
    
    // ===== DISTANCE CALCULATION =====

    // Calculate horizontal distance between two locations (ignores Y axis)
    private double calculateHorizontalDistance(Location from, Location to) {
        double deltaX = from.getX() - to.getX();
        double deltaZ = from.getZ() - to.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }
}
