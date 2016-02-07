package multitallented.redcastlemedia.bukkit.townships.region;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import multitallented.redcastlemedia.bukkit.townships.Townships;
import multitallented.redcastlemedia.bukkit.townships.effect.Effect;

/**
 *
 * @author Multitallented
 */
public class SuperRegion {
    private String name;
    private Location l;
    private String type;
    private List<UUID> owners;
    private final Map<UUID, List<String>> members;
    private final List<String> superRegionMembers;
    private int power;
    private double taxes = 0;
    private double balance = 0;
    private LinkedList<Double> taxRevenue;
    private int maxPower;
    private List<Location> childLocations;
    private long lastDisable;
    private int affection;
    
    public SuperRegion(String name, Location l, String type, List<UUID> owner, Map<UUID, List<String>> members, List<String> superRegionMembers,
            int power, double taxes, double balance, LinkedList<Double> taxRevenue, int maxPower, List<Location> childLocations,
            long lastDisable) {
        this.name = name;
        this.l = l;
        this.type=type;
        this.owners = owner;
        this.superRegionMembers = superRegionMembers;
        this.members = members;
        this.power = power;
        this.taxes = taxes;
        this.balance = balance;
        this.taxRevenue = taxRevenue;
        this.maxPower = maxPower;
        this.childLocations=childLocations;
        this.lastDisable=lastDisable;
        this.reloadAffection();
    }

    protected long getLastDisable() {
        return lastDisable;
    }

    protected void setLastDisable(long lastDisable) {
        this.lastDisable = lastDisable;
    }
    
    protected void setLocation(Location l) {
        this.l = l;
    }
    
    public List<Location> getChildLocations() {
        return this.childLocations;
    }
    
    protected void setType(String type) {
        this.type = type;
    }
    
    protected void setMaxPower(int maxPower) {
        this.maxPower = maxPower;
    }
    
    public int getMaxPower() {
        return maxPower;
    }
    
    public LinkedList<Double> getTaxRevenue() {
        return taxRevenue;
    }
    
    public void addTaxRevenue(double input) {
        taxRevenue.addFirst(input);
        if (taxRevenue.size() > 5) {
            taxRevenue.removeLast();
        }
    }
    
    public double getBalance() {
        return balance;
    }
    
    public void setBalance(double balance) {
        this.balance = balance;
    }
    
    public double getTaxes() {
        return taxes;
    }
    
    public void setTaxes(double taxes) {
        this.taxes = taxes;
    }
    
    public String getName() {
        return this.name;
    }
    
    public Location getLocation() {
        return l;
    }
    
    public String getType() {
        return type;
    }
    
    public boolean hasMember(OfflinePlayer name) {
        if (name == null) {
            return false;
        }
        return hasMember(name.getUniqueId());
    }
    
    public boolean hasMember(UUID name) {
        return members.keySet().contains(name);
    }

    public boolean hasSuperRegionMember(String name) {
        return superRegionMembers.contains(name);
    }
    
    public boolean addMember(OfflinePlayer name, List<String> perms) {
        return addMember(name.getUniqueId(), perms);
    }
    
    public boolean addMember(UUID name, List<String> perms) {
        return members.put(name, perms) != null;
    }
    
    public boolean addSuperRegionMember(String name) {
        return superRegionMembers.add(name);
    }
    
    public List<String> getMember(OfflinePlayer name) {
        return getMember(name.getUniqueId());
    }
    
    public List<String> getMember(UUID name) {
        return members.get(name);
    }
    
    public Map<UUID, List<String>> getMembers() {
        return members;
    }
    
    public List<String> getSuperRegionMembers() {
        return superRegionMembers;
    }
    
    public boolean togglePerm(OfflinePlayer name, String perm) {
        return togglePerm(name.getUniqueId(), perm);
    }
    
    public boolean togglePerm(UUID name, String perm) {
        boolean removed = false;
        try {
            if (!members.get(name).remove(perm))
                members.get(name).add(perm);
            else
                removed = true;
        } catch (NullPointerException npe) {
            
        }
        return removed;
    }
    
    public boolean hasOwner(OfflinePlayer name) {
        return hasOwner(name.getUniqueId());    
    }
    
    public boolean hasOwner(UUID name) {
        return owners.contains(name);
    }
    
    public boolean addOwner(OfflinePlayer name) {
        return addOwner(name.getUniqueId());
    }
    
    public boolean addOwner(UUID name) {
        return owners.add(name);
    }
    
    public List<OfflinePlayer> getOwnersD() {
        return owners.stream().map(Bukkit::getOfflinePlayer).collect(Collectors.toList());
    }
    public List<UUID> getOwners() {
        return owners;
    }
    
    public boolean remove(OfflinePlayer name) {
        return remove(name.getUniqueId());
    }
    
    public boolean remove(UUID name) {
        if (!owners.remove(name))
            return members.remove(name) != null;
        else
            return true;
    }
    
    public int getPower() {
        return power;
    }
    
    public void setPower(int i) {
        power = i;
    }
    
    public int getPopulation() {
        int membersSize = 0;
        for (UUID s : members.keySet()) {
            if (members.get(s).contains("member")) {
                membersSize += 1;
            }
        }
        return owners.size() + membersSize;
    }
    
    public int getAffection() { 
        return affection;
    }
    
    public void reloadAffection() {
        affection = 0;
        Townships plugin = (Townships) (Bukkit.getPluginManager().getPlugin("Townships"));
        for (Region r : plugin.getRegionManager().getContainedRegions(this)) {
            int number;
            if ((number = Effect.regionHasEffect(r, "affection")) != 0)
            affection += number;
        }
        
        
        if (this.getType() == "보호지역") {
            plugin.getRegionManager();
            this.setBalance(balance);
            this.owners = RegionManager.getBiggestAffection(this.getLocation()).getKey().getOwners();
        }
    }
    
    public SuperRegion getNation() {
        return RegionManager.getBiggestAffection(this).getKey();
    }
    public boolean isIndependent() {
        return this == getNation();
    }
    
    public List<SuperRegion> getColonies() {
        return RegionManager.getSortedSuperRegions().stream().filter(sr -> sr.getNation() == this).collect(Collectors.toList());
    }
}
