package multitallented.redcastlemedia.bukkit.townships;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Multitallented
 */
public class ConfigManager {
    
    private boolean explode;
    private final FileConfiguration config;
    private final Townships plugin;
    private final double maxTax;
    private final boolean destroyNoMoney;
    private final boolean destroyNoPower;
    private final boolean usePower;
    private final boolean useWar;
    private final double declareWarBase;
    private final double declareWarPer;
    private final double makePeaceBase;
    private final double makePeacePer;
    private final double renameCost;
    private final int powerPerKill;
    private final boolean playerInRegionChecks;
    private final boolean multipleTownMembership;
    private final ArrayList<String> whiteListTowns;
    private final String helpPage;
    private final double autoDeposit;
    private final double defaultSalvage;
    private final boolean explosionOverride;
    private final int levelDifference;
    private final long spawnKill;
    private final long gracePeriod;
    private final HashMap<String, String> itemGroups;
    
    public ConfigManager(FileConfiguration config, Townships plugin) {
        this.config = config;
        this.plugin = plugin;
        
        //Parse region config data
        explode = config.getBoolean("explode-on-destroy", false);
        maxTax = config.getDouble("max-tax", 0.0); 
        destroyNoMoney = config.getBoolean("destroy-if-no-money");
        destroyNoPower = config.getBoolean("destory-if-no-power");
        usePower = config.getBoolean("use-power", true);
        useWar = config.getBoolean("war.use-war", false);
        declareWarBase = config.getDouble("war.declare-war-base-cost", 2000.0); 
        declareWarPer = config.getDouble("war.declare-war-cost-per-member", 500.0);
        makePeaceBase = config.getDouble("war.make-peace-base-cost", 1000.0);
        makePeacePer = config.getDouble("war.make-peace-cost-per-member", 500.0);
        levelDifference = config.getInt("war.level-difference", 20);
        spawnKill = config.getLong("war.spawn-kill", 180) * 1000;
        renameCost = config.getDouble("rename-cost", 1000.0);
        powerPerKill = config.getInt("power-per-kill", 1);
        playerInRegionChecks = !config.getBoolean("disable-player-in-region-checks", false);
        multipleTownMembership = config.getBoolean("multiple-town-membership.allow", true);
        whiteListTowns = (ArrayList<String>) config.getStringList("multiple-town-membership.whitelist");
        helpPage = config.getString("help-page", "http://dev.bukkit.org/bukkit-plugins/project-34212/");
        autoDeposit = config.getDouble("auto-deposit", 0.0);
        defaultSalvage = config.getDouble("default-salvage", 0.0) / 100;
        explosionOverride = config.getBoolean("explosion-override", false);
        gracePeriod = config.getLong("grace-period-minutes", 0) * 600000;
        itemGroups = processGroups(config.getConfigurationSection("item-groups"));
        loadCharters();
    }

    private HashMap<String, String> processGroups(ConfigurationSection cs) {
        HashMap<String, String> returnMap = new HashMap<String, String>();
        if (cs == null) {
            return returnMap;
        }
        for (String key : cs.getKeys(false)) {
            returnMap.put(key, cs.getString(key, "1.1"));
        }
        return returnMap;
    }
    
    private void loadCharters() {
        Map<String, List<String>> charters = new HashMap<String, List<String>>();
        File charterFolder = new File(plugin.getDataFolder(), "charters");
        charterFolder.mkdirs();
        for (File charterFile : charterFolder.listFiles()) {
            FileConfiguration charterConfig = new YamlConfiguration();
            try {
                charterConfig.load(charterFile);
            } catch (Exception e) {
                plugin.warning("Failed to load charter " + charterFile.getName());
            }
            for (String key : charterConfig.getKeys(false)) {
                charters.put(key, charterConfig.getStringList(key));
                break;
            }
        }
        //send loaded charters for live use
        plugin.setCharters(charters);
    }
    
    public synchronized void writeToCharter(String name, List<String> data) {
        File charterFolder = new File(plugin.getDataFolder(), "charters");
        charterFolder.mkdirs();//Create the folder if it doesn't exist
        
        File charterData = new File(charterFolder, name + ".yml");
        if (!charterData.exists()) {
            try {
                charterData.createNewFile();
            } catch (Exception e) {
                plugin.warning("Could not create new charter file " + name + ".yml");
                return;
            }
        }
        
        //Create the FileConfiguration to handle the new Charter
        FileConfiguration charterConfig = new YamlConfiguration();
        try {
            charterConfig.load(charterData);
        } catch (Exception e) {
            plugin.warning("Could not load charter " + name + ".yml");
            return;
        }
        charterConfig.set(name, data);
        try {
            charterConfig.save(charterData);
        } catch (IOException ex) {
            plugin.warning("Could not save charter file " + name + ".yml");
        }
    }
    public HashMap<String, String> getItemGroups() {
        return itemGroups;
    }

    public long getGracePeriod() {
        return gracePeriod;
    }

    public long getSpawnKill() {
        return spawnKill;
    }
    
    public double getSalvage() {
        return defaultSalvage;
    }
    public boolean getMultipleTownMembership() {
        return multipleTownMembership;
    }

    public boolean containsWhiteListTownMembership(String name) {
        return whiteListTowns.contains(name);
    }
    
    public boolean getExplosionOverride() {
        return explosionOverride;
    }
    public int getLevelDifference() {
        return levelDifference;
    }

    public boolean getPlayerInRegionChecks() {
        return playerInRegionChecks;
    }
    
    public double getAutoDeposit() {
        return autoDeposit;
    }
    
    public int getPowerPerKill() {
        return powerPerKill;
    }
    
    public boolean getExplode() {
        return explode;
    }
    
    public double getMaxTax() {
        return maxTax;
    }
    
    public boolean getDestroyNoMoney() {
        return destroyNoMoney;
    }
    
    public boolean getDestroyNoPower() {
        return destroyNoPower;
    }
    
    public boolean getUsePower() {
        return usePower;
    }
    
    public boolean getUseWar() {
        return useWar;
    }

    public String getHelpPage() {
        return helpPage;
    }
    
    public double getDeclareWarBase() {
        return declareWarBase;
    }
    
    public double getDeclareWarPer() {
        return declareWarPer;
    }
    
    public double getMakePeaceBase() {
        return makePeaceBase;
    }
    
    public double getMakePeacePer() {
        return makePeacePer;
    }
    
    public double getRenameCost() {
        return renameCost;
    }
    
    public synchronized void removeCharter(String name) {
        File charter = new File(plugin.getDataFolder() + "/charters", name + ".yml");
        if (!charter.exists()) {
            plugin.warning("Unable to delete non-existent charter " + name + ".yml");
            return;
        }
        if (!charter.delete()) {
            plugin.warning("Unable to delete charter " + name + ".yml");
        }
    }
}
