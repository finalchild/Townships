package multitallented.redcastlemedia.bukkit.townships.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import multitallented.redcastlemedia.bukkit.townships.Townships;
import multitallented.redcastlemedia.bukkit.townships.Util;
import multitallented.redcastlemedia.bukkit.townships.effect.Effect;
import multitallented.redcastlemedia.bukkit.townships.events.ToPreRegionCreatedEvent;
import multitallented.redcastlemedia.bukkit.townships.region.Region;
import multitallented.redcastlemedia.bukkit.townships.region.RegionManager;
import multitallented.redcastlemedia.bukkit.townships.region.RegionType;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegion;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegionType;

public class CreateCommand implements TSCommand {

    @Override
    public boolean onCommand(CommandSender sender, String[] args, Townships instance) {
        Player player = (Player) sender;

        if (args.length == 2) {
            String regionName = args[1];

            //Permission Check
            boolean nullPerms = Townships.perm == null;
            boolean createAll = nullPerms || Townships.perm.has(player, "townships.create.all");
            if (!(nullPerms || createAll || Townships.perm.has(player, "townships.create." + regionName))) {

                if (Townships.perm.has(player, "townships.rebuild." + regionName)) {
                    player.performCommand("to rebuild " + regionName);
                    return true;
                }

                player.sendMessage(ChatColor.GRAY + "[Townships] 권한이 부족하여 " + regionName + "을(를) 건설할 수 없습니다.");
                return true;
            }

            Location currentLocation = player.getLocation();
            currentLocation.setX(Math.floor(currentLocation.getX()) + 0.4);
            currentLocation.setY(Math.floor(currentLocation.getY()) + 0.4);
            currentLocation.setZ(Math.floor(currentLocation.getZ()) + 0.4);

            //Check if player is standing someplace where a chest can be placed.
            Block currentBlock = currentLocation.getBlock();
            if (!currentBlock.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "[Townships] 상자가 설치될 수 있는 위치에 서 주세요!");
                return true;
            }
            RegionType currentRegionType = instance.regionManager.getRegionType(regionName);
            if (currentRegionType == null) {
                player.sendMessage(ChatColor.GRAY + "[Townships] " + regionName + " 는 올바른 건물 종류가 아닙니다.");
                player.sendMessage(ChatColor.GRAY + "[Townships] 올바른 사용법: /to create " + regionName + " <이름>");
                return true;
            }

            //Check if player can afford to create this region
            double costCheck = 0;
            if (Townships.econ != null) {
                double cost = currentRegionType.getMoneyRequirement();
                if (Townships.econ.getBalance(player) < cost) {
                    player.sendMessage(ChatColor.GRAY + "[Townships] 이 건물을 지으려면 " + cost + " 이(가) 필요합니다.");
                    return true;
                } else {
                    costCheck = cost;
                }

            }

            //Check if over max number of regions of that type
            if (instance.regionManager.isAtMaxRegions(player, currentRegionType)) {
                player.sendMessage(ChatColor.GRAY + "[Townships] 권한이 부족하여" + currentRegionType.getName() + "을(를) 더 지을 수 없습니다.");
                return true;
            }

            //Check if above min y
            if (currentRegionType.getMinY() != -1 && Math.floor(currentRegionType.getMinY()) > Math.floor(currentLocation.getY())) {
                    player.sendMessage(ChatColor.GRAY + "[Townships] 현재 Y 좌표는 " + Math.floor(currentLocation.getY()) + "입니다. 건물을 짓기 위한 최소 Y 좌표는 " + currentRegionType.getMinY() + "입니다.");
                    return true;
            }

            //Check if above max y
            if (currentRegionType.getMaxY() != -1 && Math.floor(currentRegionType.getMaxY()) < Math.floor(currentLocation.getY())) {
                    player.sendMessage(ChatColor.GRAY + "[Townships] 현재 Y 좌표는 " + Math.floor(currentLocation.getY()) + "입니다. 건물을 지으려면 Y 좌표 " + currentRegionType.getMaxY() + " 아래로 내려가야 합니다.");
                    return true;
            }

            //Check biome
            if (!currentRegionType.getBiome().isEmpty()
                    && !currentRegionType.getBiome().contains(player.getLocation().getBlock().getBiome().name())) {
                String mes = "";
                for (String me : currentRegionType.getBiome()) {
                    if (mes.length() == 0) {
                        mes += me;
                    } else {
                        mes += ", " + me;
                    }
                }
                player.sendMessage(ChatColor.GRAY + "[Townships] 이 건축물은 " + mes + " 바이옴에서만 건설하실 수 있습니다.");
                player.sendMessage(ChatColor.GRAY + "[Townships] 현재 바이옴은 " + player.getLocation().getBlock().getBiome().name() + " 입니다.");
                return true;
            }

            //Check if too close to other region
            List<Region> containingRegions = instance.regionManager.getContainingBuildRegions(currentLocation, currentRegionType.getRawBuildRadius());
            if (!containingRegions.isEmpty()) {

                //If the player is an owner of the region, then try to rebuild instead
                if (!containingRegions.get(0).getOwners().isEmpty() &&
                        containingRegions.get(0).getOwners().contains(player.getUniqueId()) &&
                        Townships.perm.has(player, "townships.rebuild." + containingRegions.get(0).getType().toLowerCase())) {
                    player.performCommand("to rebuild " + currentRegionType.getName());
                    return true;
                }
                player.sendMessage (ChatColor.GRAY + "[Townships] 다른 건물과 너무 가깝습니다.");
                return true;
            }

            //Check if in a super region and if has permission to make that region
            List<String> reqSuperRegion = currentRegionType.getSuperRegions();

            boolean meetsReqs = false;
            String limitMessage = null;

            if (reqSuperRegion != null && !reqSuperRegion.isEmpty()) {
                for (SuperRegion sr : instance.regionManager.getContainingSuperRegions(currentLocation)) {
                    if (reqSuperRegion.contains(sr.getType())) {
                        meetsReqs = true;
                        if (!instance.regionManager.isInsideSuperRegion(sr, currentLocation, currentRegionType.getRawBuildRadius())) {
                            player.sendMessage(ChatColor.RED + "[Townships] " + regionName + "의 일부 건물은 " + sr.getType() +" 안에 있게 됩니다.");
                            return true;
                        }
                        SuperRegionType srt = instance.regionManager.getSuperRegionType(sr.getType());
                        HashMap<String, Integer> limits = srt.getRegionLimits();
                        boolean containsName = limits.containsKey(currentRegionType.getName());
                        boolean containsGroup = !containsName && limits.containsKey(currentRegionType.getGroup());

                        if (containsName || containsGroup) {
                            int limit = containsName ? limits.get(currentRegionType.getName()) : limits.get(currentRegionType.getGroup());

                            if (limit > 0) {
                                int regionCount = 0;
                                for (Region r : instance.regionManager.getContainedRegions(sr)) {
                                    if ((containsName && r.getType().equals(currentRegionType.getName())) || 
                                            (containsGroup && instance.regionManager.getRegionType(r.getType()).getGroup().equals(currentRegionType.getGroup()))) {
                                        regionCount++;
                                        if (limit <= regionCount) {
                                            limitMessage = ChatColor.RED + "[Townships] " + sr.getType() + " 에서는 건물을 " + limit + "까지만 지을 수 있습니다.";
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!sr.hasOwner(player)) {
                        if (!sr.hasMember(player) || !sr.getMember(player).contains(regionName)) {
                            player.sendMessage(ChatColor.GRAY + "[Townships] " + sr.getName() 
                                    + "의 소유자로부터 " + regionName + "을(를) 건설할 수 있는 권한을 부여받지 않았습니다.");
                            return true;
                        }
                    }
                }
            } else {
                for (SuperRegion sr : instance.regionManager.getContainingSuperRegions(currentLocation)) {
                    if (!sr.hasOwner(player)) {
                        if (!sr.hasMember(player) || !sr.getMember(player).contains(regionName)) {
                            player.sendMessage(ChatColor.GRAY + "[Townships] " + sr.getName() + "의 소유자로부터 " + regionName + "을(를) 건설할 수 있는 권한을 부여받지 않았습니다.");
                            return true;
                        }
                    }
                }
                meetsReqs = true;
            }

            if (!meetsReqs) {
                player.sendMessage(ChatColor.GRAY + "[Townships] " + regionName + "에 다음 건물을 건설해야 합니다.");
                String message = ChatColor.GOLD + "";
                int j=0;
                for (String s : reqSuperRegion) {
                    if (message.length() + s.length() + 2 > 55) {
                        player.sendMessage(message);
                        message = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                        break;
                    } else {
                        message += s + ", ";
                    }
                }
                if (!reqSuperRegion.isEmpty()) {
                    player.sendMessage(message.substring(0, message.length() - 2) + "에 지으셔야 합니다."); // Check
                }
                return true;
            }

            if (limitMessage != null) {
                player.sendMessage(limitMessage);
                return true;
            }
            
            if (Effect.regionHasEffect(currentRegionType.getEffects(), "nation") > 0) {
                if (!RegionManager.isInNation(player)) {
                    player.sendMessage(ChatColor.GRAY + "[Townships] 영토에서만 지을 수 있습니다.");
                    return true;
                }
            }

            //Check if it has required blocks
            if (!currentRegionType.getRequirements().isEmpty()) {
                List<String> message = Util.hasCreationRequirements(currentLocation, currentRegionType, instance.regionManager);
                if (!message.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "[Townships] 이 건물을 건설하려면 블록이 더 필요합니다.");
                    for (String s : message) {
                        player.sendMessage(ChatColor.GOLD + s);
                    }
                    return true;
                }
            }
            
            ToPreRegionCreatedEvent preEvent = new ToPreRegionCreatedEvent(currentLocation, currentRegionType, player);
            instance.getServer().getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) {
                return true;
            }

            //Create chest at players feet for tracking reagents and removing upkeep items
            currentBlock.setType(Material.CHEST);

            List<UUID> owners = new ArrayList<UUID>();
            owners.add(player.getUniqueId());
            if (Townships.econ != null && costCheck > 0) {
                Townships.econ.withdrawPlayer(player, costCheck);
            }

            instance.regionManager.addRegion(currentLocation, regionName, owners);
            player.sendMessage(ChatColor.GRAY + "[Townships] " + ChatColor.WHITE + "성공적으로 건물을 건설하셨습니다:  " + ChatColor.RED + regionName);

            return true;
        }
        
        //Check if valid name (further name checking later)
        if (args[2].length() > 16 || !Util.validateFileName(args[2])) {
            player.sendMessage(ChatColor.GRAY + "[Townships] 올바른 이름이 아닙니다.");
             return true;
        }
        if (instance.getServer().getPlayerExact(args[2]) != null) {
            player.sendMessage(ChatColor.GRAY + "[Townships] 마을 이름은 플레이어 이름과 같을 수 없습니다.");
            return true;
        }

        String regionTypeName = args[1];
        //Permission Check
        if (Townships.perm != null && !Townships.perm.has(player, "townships.create.all") &&
                !Townships.perm.has(player, "townships.create." + regionTypeName)) {
            player.sendMessage(ChatColor.GRAY + "[Townships] 권한이 부족하여 " + regionTypeName+ "을(를) 생성할 수 없습니다.");
            return true;
        }

        //Check if valid super region
        Location currentLocation = player.getLocation();
        
        currentLocation.setX(Math.floor(currentLocation.getX()) + 0.4);
        currentLocation.setY(Math.floor(currentLocation.getY()) + 0.4);
        currentLocation.setZ(Math.floor(currentLocation.getZ()) + 0.4);

        SuperRegionType currentRegionType = instance.regionManager.getSuperRegionType(regionTypeName);
        if (currentRegionType == null) {
            player.sendMessage(ChatColor.GRAY + "[Townships] " + regionTypeName + " 은(는) 올바른 건물 종류가 아닙니다.");
            int j=0;
            String message = ChatColor.GOLD + "";
            for (String s : instance.regionManager.getSuperRegionTypes()) {
                if (Townships.perm == null || (Townships.perm.has(player, "townships.create.all") ||
                        Townships.perm.has(player, "townships.create." + s))) {
                    if (message.length() + s.length() + 2 > 55) {
                        player.sendMessage(message);
                        message = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                        break;
                    } else {
                        message += s + ", ";
                    }
                }
            }
            if (!instance.regionManager.getSuperRegionTypes().isEmpty()) {
                player.sendMessage(message.substring(0, message.length() - 2));
            }
            return true;
        }

        //Check if player can afford to create this townships
        double costCheck = 0;
        if (Townships.econ != null) {
            double cost = currentRegionType.getMoneyRequirement();
            if (Townships.econ.getBalance(player) < cost) {
                player.sendMessage(ChatColor.GRAY + "[Townships] 이 건물을 지으려면 " + cost + " 이(가) 필요합니다.");
                return true;
            } else {
                costCheck = cost;
            }

        }

        Map<UUID, List<String>> members = new HashMap<UUID, List<String>>();
        int currentCharter = currentRegionType.getCharter();
        //Make sure the super-region has a valid charter
        if (!Townships.perm.has(player, "townships.admin")) {
            if (currentCharter > 0) {
                try {
                    if (!instance.pendingCharters.containsKey(args[2])) {
                        player.sendMessage(ChatColor.GRAY + "[Townships] 계약을 먼저 생성해야 합니다. 명령어: /to charter " + args[1] + " " + args[2]);
                        return true;
                    } else if (instance.pendingCharters.get(args[2]).getMembers().size() <= currentCharter) {
                        player.sendMessage(ChatColor.GRAY + "[Townships] " + currentCharter + " 명의 서명이 필요합니다. 서명 명령어: /to signcharter " + args[2]);
                        return true;
                    } else if (!instance.pendingCharters.get(args[2]).getSuperRegionType().equals(args[1]) ||
                            !instance.pendingCharters.get(args[2]).getMembers().get(0).equals(player.getUniqueId())) {
                        player.sendMessage(ChatColor.GRAY + "[Townships] 이 계약서는 다른 건물 또는 다른 유저의 것입니다.");
                        player.sendMessage(ChatColor.GRAY + "소유자: " + Bukkit.getOfflinePlayer(instance.pendingCharters.get(args[2]).getMembers().get(0)).getName() + ", 종류: " + instance.pendingCharters.get(args[2]).getSuperRegionType().getName());
                        return true;
                    } else {
                        int i =0;
                        for (UUID s : instance.pendingCharters.get(args[2]).getMembers()) {
                            List<String> tempArray = new ArrayList<String>();
                            tempArray.add("member");
                            if (i > 0) {
                                members.put(s, tempArray);
                            } else {
                                i++;
                            }
                        }
                    }
                } catch (Exception e) {
                    instance.warning("Possible failure to find correct charter for " + args[2]);
                }
            }
        } else if (instance.pendingCharters.containsKey(args[2])) {
            if (currentCharter > 0) {
                try {
                    if (instance.pendingCharters.get(args[2]).getMembers().size() <= currentCharter) {
                        player.sendMessage(ChatColor.GRAY + "[Townships] " + currentCharter + " 명의 서명이 필요합니다. 서명 명령어: /to signcharter " + args[2]);
                        return true;
                    } else if (!instance.pendingCharters.get(args[2]).getSuperRegionType().getName().equalsIgnoreCase(args[1]) ||
                            !instance.pendingCharters.get(args[2]).getMembers().get(0).equals(player.getUniqueId())) {
                        player.sendMessage(ChatColor.GRAY + "[Townships] 이 계약서는 다른 건물 또는 다른 유저의 것입니다.");
                        player.sendMessage(ChatColor.GRAY + "소유자: " + Bukkit.getOfflinePlayer(instance.pendingCharters.get(args[2]).getMembers().get(0)).getName() + ", 종류: " + instance.pendingCharters.get(args[2]).getSuperRegionType().getName());
                        return true;
                    } else {
                        int i =0;
                        for (UUID s : instance.pendingCharters.get(args[2]).getMembers()) {
                            List<String> tempArray = new ArrayList<String>();
                            tempArray.add("member");
                            if (i > 0) {
                                members.put(s, tempArray);
                            } else {
                                i++;
                            }
                        }
                    }
                } catch (Exception e) {
                    instance.warning("Possible failure to find correct charter for " + args[2]);
                }
            }
        }

        Map<String, Integer> requirements = currentRegionType.getRequirements();
        HashMap<String, Integer> req = new HashMap<String, Integer>();
        for (String s : currentRegionType.getRequirements().keySet()) {
            req.put(new String(s), new Integer(requirements.get(s)));
        }

        //Check for required regions
        List<String> children = currentRegionType.getChildren();
        if (children != null) {
            for (String s : children) {
                if (!req.containsKey(s)) {
                    req.put(new String(s), 1);
                }
            }
        }

        //Check if there already is a super-region by that name, but not if it's one of the child regions
        if (instance.regionManager.getSuperRegion(args[2]) != null && (children == null || !children.contains(instance.regionManager.getSuperRegion(args[2]).getType()))) {
            player.sendMessage(ChatColor.GRAY + "[Townships] 그 이름을 가진 마을이 이미 존재합니다.");
            return true;
        }



        List<String> quietDestroy = new ArrayList<String>();
        double radius = currentRegionType.getRawRadius();


        //Check if there is an overlapping super-region of the same type
        for (SuperRegion sr : instance.regionManager.getSortedSuperRegions()) {
            try {
                if (sr.getLocation().distance(currentLocation) < radius + instance.regionManager.getSuperRegionType(sr.getType()).getRawRadius() &&
                        (sr.getType().equalsIgnoreCase(regionTypeName) || !sr.hasOwner(player))) {
                    player.sendMessage(ChatColor.GRAY + "[Townships] " + sr.getName() + " 은(는) 이미 여기에 있습니다.");
                    return true;
                }
            } catch (IllegalArgumentException iae) {

            }
        }
        
        SuperRegion originalChild = null;
        if (!req.isEmpty()) {
            for (SuperRegion sr : instance.regionManager.getContainingSuperRegions(currentLocation)) {
                if (children != null && children.contains(sr.getType()) && sr.hasOwner(player)) {
                    if (children.get(0).equals(sr.getType())) {
                        originalChild = sr;
                    }
                    quietDestroy.add(sr.getName());
                }

                String rType = sr.getType();
                if (!sr.hasOwner(player) && (!sr.hasMember(player) || !sr.getMember(player).contains(regionTypeName))) {
                    player.sendMessage(ChatColor.GRAY + "[Townships] 권한이 부족하여 " + regionTypeName + "을(를) " + sr.getName() + " 안에 지을 수 없습니다.");
                    return true;
                } 
                if (req.containsKey(rType)) {
                    int amount = req.get(rType);
                    if (amount < 2) {
                        req.remove(rType);
                        if (req.isEmpty()) {
                            break;
                        }
                    } else {
                        req.put(rType, amount - 1);
                    }
                }
            }

            Location loc = player.getLocation();
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            double radius1 = currentRegionType.getRawRadius();
            for (Region r : instance.regionManager.getSortedRegions()) {
                Location l = r.getLocation();
                if (l.getX() + radius1 < x) {
                    break;
                }

                if (l.getX() - radius1 < x && l.getY() + radius1 > y && l.getY() - radius1 < y && 
                        l.getZ() + radius1 > z && l.getZ() - radius1 < z && l.getWorld().equals(loc.getWorld())) {

                    if (req.containsKey(r.getType())) {
                        if (req.get(r.getType()) < 2) {
                            req.remove(r.getType());
                        } else {
                            req.put(r.getType(), req.get(r.getType()) - 1);
                        }
                    } else if (req.containsKey(instance.regionManager.getRegionType(r.getType()).getGroup())) {
                        String group = instance.regionManager.getRegionType(r.getType()).getGroup();
                        if (req.get(group) < 2) {
                            req.remove(group);
                        } else {
                            req.put(group, req.get(group) - 1);
                        }
                    }
                }
            }
            //Check to see if the new region completely contains the original child region
            if (originalChild != null) {
                SuperRegionType srt = instance.regionManager.getSuperRegionType(originalChild.getType());
                if (srt != null && originalChild.getLocation().distance(currentLocation) > srt.getRadius()) {
                    player.sendMessage(ChatColor.RED + "[Townships] " + currentRegionType + " 은 " + originalChild.getType() + "을(를) 전부 다 덮을 정도의 크기여야 합니다.");
                    return true;
                }
            }

            if (!req.isEmpty()) {
                for (Region r : instance.regionManager.getContainingRegions(currentLocation)) {
                    String rType = instance.regionManager.getRegion(r.getLocation()).getType();
                    if (req.containsKey(rType)) {
                        int amount = req.get(rType);
                        if (amount <= 1) {
                            req.remove(rType);
                        } else {
                            req.put(rType, amount - 1);
                        }
                    }
                }

            }
        }
        if (!req.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "[Townships] 이 건물은 필요한 모든 건물을 포함하고 있지 않습니다.");
            int j=0;
            String message = ChatColor.GOLD + "";
            for (String s : req.keySet()) {
                if (message.length() + s.length() + 3 + req.get(s).toString().length() > 55) {
                    player.sendMessage(message);
                    message = ChatColor.GOLD + "";
                    j++;
                }
                if (j >14) {
                    break;
                } else {
                    message += req.get(s) + " " + s + ", ";
                }
            }
            if (!req.isEmpty()) {
                player.sendMessage(message.substring(0, message.length() - 2));
            }
            return true;
        }

        //Assimulate any child super regions
        List<UUID> owners = new ArrayList<UUID>();
        double balance = 0.0;
        int power = 0;
        for (String s : quietDestroy) {
            SuperRegion sr = instance.regionManager.getSuperRegion(s);
            for (UUID so : sr.getOwners()) {
                if (!owners.contains(so))
                    owners.add(so);
            }
            for (UUID sm : sr.getMembers().keySet()) {
                if (!members.containsKey(sm) && sr.getMember(sm).contains("member")) {
                    members.put(sm, sr.getMember(sm));
                }
            }
            balance += sr.getBalance();
            power += sr.getPower();
        }
        power += currentRegionType.getDailyPower();
        if (power > currentRegionType.getMaxPower()) {
            power = currentRegionType.getMaxPower();
        }

        //Check if more members needed to create the super-region
        if (owners.size() + members.size() < currentRegionType.getPopulation()) {
            player.sendMessage(ChatColor.GRAY + "[Townships] " + (currentRegionType.getPopulation() - owners.size() - members.size()) + " 명의 멤버가 더 필요합니다.");
            return true;
        }
        
        List<Location> childLocations = null;
        if (originalChild != null) {
            childLocations = originalChild.getChildLocations();
//            System.out.println("[Townships] " + originalChild.getLocation().getWorld().getName() + ":" +
//                    originalChild.getLocation().getX() + ":" + originalChild.getLocation().getY() + ":" + originalChild.getLocation().getZ());
            childLocations.add(originalChild.getLocation());
        }

        for (String s : quietDestroy) {
            instance.regionManager.destroySuperRegion(s, false, true);
        }
        if (currentCharter > 0 && instance.pendingCharters.containsKey(args[2])) {
            Townships.configManager.removeCharter(args[2]);
            instance.pendingCharters.remove(args[2]);
        }
        if (!owners.contains(player.getUniqueId())) {
            owners.add(player.getUniqueId());
        }
        if (costCheck > 0) {
            Townships.econ.withdrawPlayer(player, costCheck);
        }

        if (quietDestroy.isEmpty()) {
            balance += Townships.getConfigManager().getAutoDeposit();
        }
        
        List<String> superRegionMembers = new ArrayList<String>();
        instance.regionManager.addSuperRegion(args[2], currentLocation, regionTypeName, owners, members, superRegionMembers, power, balance, childLocations);
        player.sendMessage(ChatColor.GOLD + "[Townships] 새로운 건물 (" + args[1] + ") 을 건설하셨습니다. 이름: " + args[2]);
        return true;
    }

}
