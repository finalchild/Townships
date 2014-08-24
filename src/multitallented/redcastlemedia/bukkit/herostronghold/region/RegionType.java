package multitallented.redcastlemedia.bukkit.herostronghold.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Multitallented
 */
public class RegionType {
    private String name;
    private final ArrayList<String> friendlyClasses;
    private final ArrayList<String> enemyClasses;
    private final ArrayList<String> effects;
    private final int radius;
    private final ArrayList<ArrayList<HSItem>> requirements;
    private final ArrayList<ArrayList<HSItem>> reagents;
    private final ArrayList<ArrayList<HSItem>> upkeep;
    private final ArrayList<ArrayList<HSItem>> output;
    private final double upkeepChance;
    private final double moneyRequirement;
    private final double moneyOutput;
    private final double exp;
    private final HashMap<String, Integer> superRegions;
    private final int buildRadius;
    private final int rawBuildRadius;
    private final int rawRadius;
    private final String description;
    private final String group;
    private final int powerDrain;
    private final int housing;
    private final List<String> biome;
    private final ItemStack icon;
    
    
    public RegionType(String name, String group, ArrayList<String> friendlyClasses,
            ArrayList<String> enemyClasses, ArrayList<String> effects,
            int radius, int buildRadius, ArrayList<ArrayList<HSItem>> requirements, HashMap<String, Integer> superRegions,
            ArrayList<ArrayList<HSItem>> reagents, ArrayList<ArrayList<HSItem>> upkeep,
            ArrayList<ArrayList<HSItem>> output, double upkeepChance,
            double moneyRequirement, double moneyOutput, double exp,
            String description, int powerDrain,
            int housing, List<String> biome, ItemStack icon) {
        this.name = name;
        this.group = group;
        this.friendlyClasses = friendlyClasses;
        this.enemyClasses = enemyClasses;
        this.effects = effects;
        this.radius = radius;
        this.rawRadius = (int) Math.sqrt(radius);
        this.rawBuildRadius = (int) Math.sqrt(buildRadius);
        this.buildRadius = buildRadius;
        this.requirements = requirements;
        this.superRegions = superRegions;
        this.reagents = reagents;
        this.upkeep = upkeep;
        this.output = output;
        this.upkeepChance = upkeepChance;
        this.moneyRequirement = moneyRequirement;
        this.moneyOutput = moneyOutput;
        this.exp = exp;
        this.description = description;
        this.powerDrain = powerDrain;
        this.housing = housing;
        this.biome = biome;
        this.icon = icon;
    }
    public ItemStack getIcon() {
        return icon;
    }
    public List<String> getBiome() {
        return biome;
    }
    public int getHousing() {
        return housing;
    }
    
    public int getPowerDrain() {
        return powerDrain;
    }
    
    public String getGroup() {
        return group;
    }
    
    public int getRawRadius() {
        return rawRadius;
    }
    
    public int getRawBuildRadius() {
        return rawBuildRadius;
    }
    
    public int getBuildRadius() {
        return buildRadius;
    }
    
    public HashMap<String, Integer> getSuperRegions() {
        return superRegions;
    }
    
    public double getExp() {
        return exp;
    }
    
    public String getName() {
        return name;
    }
    
    public ArrayList<ArrayList<HSItem>> getReagents() {
        return reagents;
    }
    
    public int getRadius() {
        return radius;
    }
    
    public ArrayList<ArrayList<HSItem>> getRequirements() {
        return requirements;
    }
    
    public double getMoneyRequirement() {
        return moneyRequirement;
    }
    
    public double getUpkeepChance() {
        return upkeepChance;
    }
    
    public ArrayList<String> getEffects() {
        return effects;
    }
    
    public ArrayList<ArrayList<HSItem>> getUpkeep() {
        return upkeep;
    }
    
    public double getMoneyOutput() {
        return moneyOutput;
    }
    
    public ArrayList<ArrayList<HSItem>> getOutput() {
        return output;
    }
    
    public boolean containsFriendlyClass(String name) {
        return this.friendlyClasses.contains(name);
    }
    
    public boolean containsEnemyClass(String name) {
        return this.enemyClasses.contains(name);
    }
    
    public String getDescription() {
        return this.description;
    }
}
