/**
 * This file is part of Townships-Plugin.

 * Townships-Plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Townships-Plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Townships-Plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package multitallented.redcastlemedia.bukkit.townships.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import multitallented.redcastlemedia.bukkit.townships.Townships;
import multitallented.redcastlemedia.bukkit.townships.Util;
import multitallented.redcastlemedia.bukkit.townships.region.Region;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegion;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegionType;

public class CreateCommand implements TSCommand {

    @Override
    public boolean onCommand(CommandSender sender, String[] args, Townships instance) {
        Player player = (Player) sender;

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