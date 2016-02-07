package multitallented.plugins.townships.effects;

import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import multitallented.redcastlemedia.bukkit.townships.Townships;
import multitallented.redcastlemedia.bukkit.townships.effect.Effect;
import multitallented.redcastlemedia.bukkit.townships.events.ToPreRegionCreatedEvent;
import multitallented.redcastlemedia.bukkit.townships.events.ToRenameEvent;
import multitallented.redcastlemedia.bukkit.townships.events.ToTwoSecondEffectEvent;
import multitallented.redcastlemedia.bukkit.townships.region.Region;
import multitallented.redcastlemedia.bukkit.townships.region.RegionManager;
import multitallented.redcastlemedia.bukkit.townships.region.RegionType;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 *
 * @author Multitallented
 */
public class EffectDrainPower extends Effect {
    private HashMap<Location, Long> lastUpkeep = new HashMap<Location, Long>();

    public EffectDrainPower(Townships plugin) {
        super(plugin);
        registerEvent(new UpkeepListener(plugin, this));
    }

    @Override
    public void init(Townships plugin) {
        super.init(plugin);
    }

    public class UpkeepListener implements Listener {
        private final EffectDrainPower effect;
        private final Townships plugin;
        public UpkeepListener(Townships plugin, EffectDrainPower effect) {
            this.effect = effect;
            this.plugin = plugin;
        }


        @EventHandler
        public void onCustomEvent(ToTwoSecondEffectEvent event) {
            if (event.getEffect().length < 1 || !event.getEffect()[0].equals("drain_power")) {
                return;
            }
            Region r = event.getRegion();
            Location l = r.getLocation();

            //Check if the region has the shoot arrow effect and return arrow velocity
            long period = Long.parseLong(event.getEffect()[1]) * 1000;
                if (period < 1) {
                return;
            }

            if (lastUpkeep.get(l) != null && period + lastUpkeep.get(l) > new Date().getTime()) {
                return;
            }

            //Check if valid siege machine position
            if (l.getBlock().getRelative(BlockFace.UP).getY() < l.getWorld().getHighestBlockAt(l).getY()) {
                return;
            }

            //Check to see if the Townships has enough reagents
            if (!effect.hasReagents(l)) {
                return;
            }

            Block b = l.getBlock().getRelative(BlockFace.UP);
            if (!(b.getState() instanceof Sign)) {
                return;
            }

            //Find target Super-region
            Sign sign = (Sign) b.getState();
            String srName = sign.getLine(0);
            SuperRegion sr = plugin.getRegionManager().getSuperRegion(srName);
            if (sr == null) {
                sign.setLine(2, "유효하지 않은 이름");
                sign.update();
                return;
            }

            //Check if too far away
            double rawRadius = plugin.getRegionManager().getSuperRegionType(sr.getType()).getRawRadius();
            try {
                if (sr.getLocation().distance(l) - rawRadius >  150) {
                    sign.setLine(2, "사정거리");
                    sign.setLine(3, "바깥");
                    sign.update();
                    return;
                }
            } catch (IllegalArgumentException iae) {
                sign.setLine(2, "사정거리");
                sign.setLine(3, "바깥");
                sign.update();
                return;
            }

            if (sr.getPower() < 1) {
                return;
            }

            //Run upkeep but don't need to know if upkeep occured
            //effect.forceUpkeep(l);
            Effect.forceUpkeep(l);
            lastUpkeep.put(l, new Date().getTime());

            Location spawnLoc = l.getBlock().getRelative(BlockFace.UP, 3).getLocation();
            //Location srLoc = sr.getLocation();
            Location loc = new Location(spawnLoc.getWorld(), spawnLoc.getX(), spawnLoc.getY() + 15, spawnLoc.getZ());
            final Location loc1 = new Location(spawnLoc.getWorld(), spawnLoc.getX(), spawnLoc.getY() + 20, spawnLoc.getZ());
            final Location loc2 = new Location(spawnLoc.getWorld(), spawnLoc.getX(), spawnLoc.getY() + 25, spawnLoc.getZ());
            final Location loc3 = new Location(spawnLoc.getWorld(), spawnLoc.getX(), spawnLoc.getY() + 30, spawnLoc.getZ());
            TNTPrimed tnt = l.getWorld().spawn(loc, TNTPrimed.class);
            tnt.setFuseTicks(1);

            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {
                    TNTPrimed tnt = loc1.getWorld().spawn(loc1, TNTPrimed.class);
                    tnt.setFuseTicks(1);
                }
            }, 5L);
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {
                    TNTPrimed tnt = loc2.getWorld().spawn(loc2, TNTPrimed.class);
                    tnt.setFuseTicks(1);
                }
            }, 10L);

            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {
                    TNTPrimed tnt = loc3.getWorld().spawn(loc3, TNTPrimed.class);
                    tnt.setFuseTicks(1);
                }
            }, 15L);

            //double randX = srLoc.getX() + Math.random()*rawRadius*(-1 * (int) (Math.random() + 0.5));
            //double randZ = srLoc.getZ() + Math.random()*rawRadius*(-1 * (int) (Math.random() + 0.5));
            //final Location endLoc = new Location(srLoc.getWorld(), randX, 240, randZ);

            plugin.getRegionManager().reduceRegion(sr);
            double amount = sr.getBalance() * 0.7 * Townships.getConfigManager().getPowerPerKill() / sr.getMaxPower();
            plugin.getRegionManager().addBalance(sr, -1 * amount);
            SuperRegion attackersr = RegionManager.getSR(r.getPrimaryOwner());
            attackersr.setBalance(attackersr.getBalance() + amount);
           
            for (UUID id : sr.getMembers().keySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(id);
                amount = Townships.econ.getBalance(Bukkit.getOfflinePlayer(id)) * 0.7 * Townships.getConfigManager().getPowerPerKill() / sr.getMaxPower();
                Townships.econ.withdrawPlayer(player, amount);
                Townships.econ.depositPlayer(Bukkit.getOfflinePlayer(r.getPrimaryOwner()), amount);
                if (player.isOnline()) {
                    player.getPlayer().sendMessage("[Townships] " + amount + "가 약탈당하였습니다. 방어하세요! 좌표: " + r.getLocation().getX() + " " + r.getLocation().getY() + " " + r.getLocation().getZ());
                }
            }
            for (UUID id : sr.getOwners()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(id);
                amount = Townships.econ.getBalance(player) * 0.7 * Townships.getConfigManager().getPowerPerKill() / sr.getMaxPower();
                Townships.econ.withdrawPlayer(player, amount);
                Townships.econ.depositPlayer(Bukkit.getOfflinePlayer(r.getPrimaryOwner()), amount);
                if (player.isOnline()) {
                    player.getPlayer().sendMessage("[Townships] " + amount + "가 약탈당하였습니다. 방어하세요! 좌표: " + r.getLocation().getX() + " " + r.getLocation().getY() + " " + r.getLocation().getZ());
                }
            }
            if (sr.getPower() < 1 && Townships.getConfigManager().getDestroyNoPower()) {
                plugin.getRegionManager().destroySuperRegion(sr.getName(), true);
            }
        }

        @EventHandler
        public void onPreRegionCreated(ToPreRegionCreatedEvent event) {
            Location l = event.getLocation();
            RegionType rt = event.getRegionType();
            Player player = event.getPlayer();

            for (String s : rt.getEffects()) {
                if (s.startsWith("drain_power") || s.startsWith("charging_drain_power")) {
                    Block b = l.getBlock().getRelative(BlockFace.UP);
                    if (!(b.getState() instanceof Sign)) {
                        player.sendMessage(ChatColor.RED + "[Townships] 대상 마을을 쓴 표지판을 중앙 상자 위에 두십시오.");
                        event.setCancelled(true);
                        return;
                    }

                    if (l.getBlock().getRelative(BlockFace.UP).getY() < l.getWorld().getHighestBlockAt(l).getY()) {
                        player.sendMessage(ChatColor.RED + "[Townships] 공성 대포 중심 위에 어떠한 블록도 있어서는 안 됩니다.");
                        event.setCancelled(true);
                        return;
                    }

                    //Find target Super-region
                    Sign sign = (Sign) b.getState();
                    String srName = sign.getLine(0);
                    SuperRegion sr = plugin.getRegionManager().getSuperRegion(srName);
                    if (sr == null) {
                        sign.setLine(0, "유효하지 않은 대상");
                        sign.update();
                        player.sendMessage(ChatColor.RED + "[Townships] 표지판 첫 줄에 대상 마을 이름을 쓰십시오.");
                        event.setCancelled(true);
                        return;
                    }
                    Bukkit.broadcastMessage(ChatColor.GRAY + "[Townships] " + ChatColor.RED +
                            player.getDisplayName() + ChatColor.WHITE + "이(가) " +
                            ChatColor.RED + rt.getName() + ChatColor.WHITE + "로 " + ChatColor.RED + sr.getName() + "를 공격합니다!");
                    return;
                }
            }
        }

        @EventHandler
        public void onRename(ToRenameEvent event) {
            RegionManager rm = getPlugin().getRegionManager();
            for (Region r : rm.getSortedRegions()) {
                RegionType rt = rm.getRegionType(r.getType());
                if (rt == null) {
                    continue;
                }

                boolean hasEffect = false;
                for (String s : rt.getEffects()) {
                    if (s.startsWith("drain_power")) {
                        hasEffect = true;
                        break;
                    }
                }
                if (!hasEffect) {
                    continue;
                }

                Sign sign;
                Block b = r.getLocation().getBlock().getRelative(BlockFace.UP);
                try {
                    if (!(b instanceof Sign)) {
                        continue;
                    }
                    sign = (Sign) b;
                } catch (Exception e) {
                    continue;
                }
                String srName = sign.getLine(0);
                SuperRegion sr = rm.getSuperRegion(srName);
                if (sr == null) {
                    continue;
                }
                if (sr.getName().equals(event.getOldName())) {
                    sign.setLine(0, event.getNewName());
                }
            }
        }
    }
}
