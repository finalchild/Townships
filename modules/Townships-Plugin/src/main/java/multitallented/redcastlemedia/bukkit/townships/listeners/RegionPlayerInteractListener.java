package multitallented.redcastlemedia.bukkit.townships.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import multitallented.redcastlemedia.bukkit.townships.Townships;
import multitallented.redcastlemedia.bukkit.townships.events.ToPlayerExitRegionEvent;
import multitallented.redcastlemedia.bukkit.townships.events.ToPlayerExitSRegionEvent;
import multitallented.redcastlemedia.bukkit.townships.region.Region;
import multitallented.redcastlemedia.bukkit.townships.region.RegionCondition;
import multitallented.redcastlemedia.bukkit.townships.region.RegionManager;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Multitallented
 */
public class RegionPlayerInteractListener implements Listener {
    private final RegionManager rm;
    private final Map<Player, String> channels = new HashMap<Player, String>();
    private final Townships plugin;
    public RegionPlayerInteractListener(Townships plugin) {
        this.rm = plugin.getRegionManager();
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        ArrayList<Region> previousRegions = plugin.getCheckRegionTask().lastRegion.get(p);
        ArrayList<SuperRegion> previousSRegions = plugin.getCheckRegionTask().lastSRegion.get(p);
        if (previousRegions != null) {
            for (Region r : previousRegions) {
                ToPlayerExitRegionEvent exitEent = new ToPlayerExitRegionEvent(r.getLocation(), p, true);
                Bukkit.getPluginManager().callEvent(exitEent);
            }

            plugin.getCheckRegionTask().lastRegion.remove(p);
        }
        if (previousSRegions != null) {
            for (SuperRegion sr : previousSRegions) {
                ToPlayerExitSRegionEvent exitEent = new ToPlayerExitSRegionEvent(sr.getName(), p, true);
                Bukkit.getPluginManager().callEvent(exitEent);
            }

            plugin.getCheckRegionTask().lastRegion.remove(p);
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH)
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        String channel = channels.get(player);
        if (channel == null || channel.equals("")) {
            return;
        }
        event.setCancelled(true);
        String title = null;
        SuperRegion sr = rm.getSuperRegion(channel);
        if (sr == null) {
            return;
        }
        ArrayList<String> memberPerms = (ArrayList<String>) sr.getMember(player.getName());
        if (memberPerms == null || memberPerms.isEmpty() || !memberPerms.contains("member")) {
            return;
        }
        for (String s : memberPerms) {
            if (s.contains("title:")) {
                title = s.replace("title:", "");
            }
        }
        SendMessageThread smt = new SendMessageThread(plugin, channel, channels, title, player, event.getMessage());
        String message = "[" + channel + "] " + player.getDisplayName() + ": " + event.getMessage();
        Logger.getLogger("Minecraft").log(Level.INFO, message);
        try {
            smt.run();
        } catch (Exception e) {
            
        }
    }
    
    public void setPlayerChannel(Player p, String s) {
        if (s.equals("")) {
            String prevChannel = channels.get(p);
            String title = null;
            try {
                for (String sn : rm.getSuperRegion(prevChannel).getMember(p.getName())) {
                    if (sn.contains("title:")) {
                        title = sn.replace("title:", "");
                    }
                }
            } catch (NullPointerException npe) {

            }
            channels.remove(p);
            if (prevChannel != null && !prevChannel.endsWith(s)) {
                SendMessageThread smt = new SendMessageThread(plugin, prevChannel, channels, title, p, p.getDisplayName() + " has left channel " + s);
                try {
                    smt.run();
                } catch(Exception e) {

                }
            }
            return;
        }
        channels.put(p, s);
        String title = null;
        try {
            for (String sn : rm.getSuperRegion(s).getMember(p.getName())) {
                if (sn.contains("title:")) {
                    title = sn.replace("title:", "");
                }
            }
        } catch (NullPointerException npe) {

        }
        SendMessageThread smt = new SendMessageThread(plugin, s, channels, title, p, p.getDisplayName() + " has joined channel " + s);
        try {
            smt.run();
        } catch (Exception e) {
            
        }
    }
    
    public Map<Player, String> getChannels() {
        return channels;
    }

    @EventHandler
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || !(event.getRightClicked() instanceof ItemFrame)) {
                return;
        }
        ArrayList<RegionCondition> conditions = new ArrayList<RegionCondition>();
        conditions.add(new RegionCondition("deny_block_build", true, 0));
        conditions.add(new RegionCondition("deny_block_build_no_reagent", false, 0));
        if (rm.shouldTakeAction(event.getRightClicked().getLocation(), event.getPlayer(), conditions)) {
                event.getPlayer().sendMessage(ChatColor.GRAY + "[Townships] This region is protected");
                event.setCancelled(true);
                return;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getPlayer().getItemInHand() != null && event.getPlayer().getItemInHand().getType() == Material.COAL) {
            event.setCancelled(true);
            if (event.getClickedBlock() == null) {
                return;
            }
            if (plugin.who(event.getClickedBlock().getLocation(),event.getPlayer())) {
                return;
            } else {
                event.getPlayer().sendMessage(ChatColor.GRAY + "[Townships] No regions here");
            }
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            if ((event.getClickedBlock().getType() == Material.CROPS || event.getClickedBlock().getTypeId() == 60)) {
                ArrayList<RegionCondition> conditions = new ArrayList<RegionCondition>();
                conditions.add(new RegionCondition("deny_block_break", true, 0));
                conditions.add(new RegionCondition("deny_block_break_no_reagent", false, 0));
                if (rm.shouldTakeAction(event.getClickedBlock().getLocation(), event.getPlayer(), conditions)) {
                    event.setCancelled(true);
                    return;
                }
            } else {
                ArrayList<RegionCondition> conditions = new ArrayList<RegionCondition>();
                conditions.add(new RegionCondition("deny_player_interact", true, 0));
                conditions.add(new RegionCondition("deny_player_interact_no_reagent", false, 0));
                conditions.add(new RegionCondition("deny_use_circuit", true, 0));
                conditions.add(new RegionCondition("deny_use_circuit_no_reagent", false, 0));
                if (rm.shouldTakeAction(event.getClickedBlock().getLocation(), event.getPlayer(), conditions)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        ArrayList<RegionCondition> conditions = new ArrayList<RegionCondition>();
        conditions.add(new RegionCondition("deny_player_interact", true, 0));
        conditions.add(new RegionCondition("deny_player_interact_no_reagent", false, 0));
        if (event.getClickedBlock().getType() == Material.LEVER || event.getClickedBlock().getType() == Material.STONE_BUTTON) {
            conditions.add(new RegionCondition("deny_use_circuit", true, 0));
            conditions.add(new RegionCondition("deny_use_circuit_no_reagent", false, 0));
        } else if (event.getClickedBlock().getType() == Material.WOODEN_DOOR || event.getClickedBlock().getType() == Material.TRAP_DOOR ||
                event.getClickedBlock().getType() == Material.IRON_DOOR_BLOCK) {
            conditions.add(new RegionCondition("deny_use_door", true, 0));
            conditions.add(new RegionCondition("deny_use_door_no_reagent", false, 0));
        } else if (event.getClickedBlock().getType() == Material.CHEST || event.getClickedBlock().getType() == Material.FURNACE ||
                event.getClickedBlock().getType() == Material.DISPENSER) {
            conditions.add(new RegionCondition("deny_use_chest", true, 0));
            conditions.add(new RegionCondition("deny_use_chest_no_reagent", false, 0));
        }
        if (rm.shouldTakeAction(event.getClickedBlock().getLocation(), event.getPlayer(), conditions)) {
            event.getPlayer().sendMessage(ChatColor.GRAY + "[Townships] This region is protected");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != TeleportCause.ENDER_PEARL) {
            return;
        }

        ArrayList<RegionCondition> conditions = new ArrayList<RegionCondition>();
        conditions.add(new RegionCondition("deny_ender_pearl", true, 0));
        conditions.add(new RegionCondition("deny_ender_pearl_no_reagent", false, 0));

        if (rm.shouldTakeAction(event.getTo(), event.getPlayer(), conditions)) {
            //event.getPlayer().getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
            event.getPlayer().sendMessage(ChatColor.GRAY + "[Townships] This region is protected");
            event.setCancelled(true);
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled() || (!rm.shouldTakeAction(event.getBed().getLocation(), event.getPlayer(), 0, "deny_player_interact", true) &&
                !rm.shouldTakeAction(event.getBed().getLocation(), event.getPlayer(), 0, "deny_player_interact_no_reagent", false))) {
            return;
        }

        event.getPlayer().sendMessage(ChatColor.GRAY + "[Townships] This region is protected");
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (event.isCancelled() || (!rm.shouldTakeAction(event.getBlockClicked().getLocation(), event.getPlayer(), 0, "deny_bucket_use", true) &&
                !rm.shouldTakeAction(event.getBlockClicked().getLocation(), event.getPlayer(), 0, "deny_bucket_use_no_reagent", false))) {
            return;
        }

        event.getPlayer().sendMessage(ChatColor.GRAY + "[Townships] This region is protected");
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled() || (!rm.shouldTakeAction(event.getBlockClicked().getLocation(), event.getPlayer(), 0, "deny_bucket_use", true) &&
                !rm.shouldTakeAction(event.getBlockClicked().getLocation(), event.getPlayer(), 0, "deny_bucket_use_no_reagent", false))) {
            return;
        }
        
        event.getPlayer().sendMessage(ChatColor.GRAY + "[Townships] This region is protected");
        event.setCancelled(true);
    }
}
