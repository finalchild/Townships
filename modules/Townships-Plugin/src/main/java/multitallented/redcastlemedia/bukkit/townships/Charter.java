package multitallented.redcastlemedia.bukkit.townships;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import multitallented.redcastlemedia.bukkit.townships.region.SuperRegionType;

public class Charter {
	private SuperRegionType srt;
	private List<UUID> members;
	
	public Charter(SuperRegionType srt, UUID owner) {
		this.srt = srt;
		members = new ArrayList<UUID>();
		members.add(owner);
	}
	
	public Charter(SuperRegionType srt, List<UUID> members) {
		this.srt = srt;
		this.members = members;
	}
	
	public boolean addMember(UUID member) {
		return members.add(member);
	}
	
	public SuperRegionType getSuperRegionType() {
		return srt;
	}
	
	public List<UUID> getMembers() {
		return members;
	}
}
