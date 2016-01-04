package multitallented.redcastlemedia.bukkit.townships.region;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

/**
 *
 * @author Multitallented
 * @author Phoenix_Frenzy
 */
public class Region {
    private int id;
    private Location loc;
    private String type;
    private List<UUID> owners;
    private List<UUID> members;
    
    public Region(int id, Location loc, String type, List<UUID> owners, List<UUID> members) {
        this.id = id;
        this.loc = loc;
        this.type = type;
        this.owners = owners;
        this.members = members;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public int getID() {
        return id;
    }
    
    public Location getLocation() {
        return loc;
    }
    
    public String getType() {
        return type;
    }
    
    public UUID getPrimaryOwner() {
        return owners.get(0);
    }
    
    public List<UUID> getOwners() {
        return owners;
    }
    
    public List<UUID> getMembers() {
        return members;
    }
    
    public void addOwner(OfflinePlayer name) {
        addOwner(name.getUniqueId());
    }
    
    public void addOwner(UUID name) {
        owners.add(name);
    }
    
    public void addMember(OfflinePlayer name) {
        addMember(name.getUniqueId());
    }
    
    public void addMember(UUID name) {
        members.add(name);
    }
    
    public boolean remove(OfflinePlayer name) {
        return remove(name.getUniqueId());
    }
    
    public boolean remove(UUID name) {
        if (owners.contains(name)) {
            owners.remove(name);
            return true;
        } else if (members.contains(name)) {
            members.remove(name);
            return true;
        } else {
            return false;
        }
    }
    
    public boolean isPrimaryOwner(OfflinePlayer name) {
        return isPrimaryOwner(name.getUniqueId());
    }
    
    public boolean isPrimaryOwner(UUID name) {
        return owners.get(0).equals(name);
    }
    
    
    public boolean isOwner(OfflinePlayer name) {
        return isMember(name.getUniqueId());
    }
    
    public boolean isOwner(UUID name) {
        return owners.contains(name);
    }
    
    public boolean isMember(OfflinePlayer name) {
        return isMember(name.getUniqueId());
    }
    
    public boolean isMember(UUID name) {
        return members.contains(name);
    }
}

