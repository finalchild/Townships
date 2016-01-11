package multitallented.redcastlemedia.bukkit.townships;
/**
 *
 * @author Multitallented
 */
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import multitallented.redcastlemedia.bukkit.townships.checkregiontask.CheckRegionTask;
import multitallented.redcastlemedia.bukkit.townships.effect.EffectManager;
import multitallented.redcastlemedia.bukkit.townships.events.ToCommandEffectEvent;
import multitallented.redcastlemedia.bukkit.townships.events.ToPreRegionCreatedEvent;
import multitallented.redcastlemedia.bukkit.townships.events.ToRenameEvent;
import multitallented.redcastlemedia.bukkit.townships.listeners.CustomListener;
import multitallented.redcastlemedia.bukkit.townships.listeners.RegionBlockListener;
import multitallented.redcastlemedia.bukkit.townships.listeners.RegionEntityListener;
import multitallented.redcastlemedia.bukkit.townships.listeners.RegionPlayerInteractListener;
import multitallented.redcastlemedia.bukkit.townships.listeners.guis.GUIListener;
import multitallented.redcastlemedia.bukkit.townships.listeners.guis.GUIManager;
import multitallented.redcastlemedia.bukkit.townships.listeners.guis.InfoGUIListener;
import multitallented.redcastlemedia.bukkit.townships.listeners.guis.RequirementsGUIListener;
import multitallented.redcastlemedia.bukkit.townships.listeners.guis.ShopGUIListener;
import multitallented.redcastlemedia.bukkit.townships.region.Region;
import multitallented.redcastlemedia.bukkit.townships.region.RegionManager;
import multitallented.redcastlemedia.bukkit.townships.region.RegionType;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegion;
import multitallented.redcastlemedia.bukkit.townships.region.SuperRegionType;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public class Townships extends JavaPlugin {
    protected FileConfiguration config;
    private RegionManager regionManager;
    private RegionBlockListener blockListener;
    public static Economy econ;
    public static Permission perm;
    public static Chat chat;
    private RegionEntityListener regionEntityListener;
    private RegionPlayerInteractListener dpeListener;
    private Map<UUID, String> pendingInvites = new HashMap<UUID, String>();
    private static ConfigManager configManager;
    private Map<String, Charter> pendingCharters = new HashMap<String, Charter>();
    private HashSet<String> effectCommands = new HashSet<String>();
    private GUIManager guiManager;
    private static EffectManager effectManager;
    private CheckRegionTask theSender;
    
    @Override
    public void onDisable() {
        GUIManager.closeAllMenus();
        
        getLogger().info("is now disabled!");
    }

    @Override
    public void onEnable() {
      
        //setup configs
        config = getConfig();
        config.options().copyDefaults(true);
        saveConfig();
        configManager = new ConfigManager(config, this);
        
        //Setup RegionManager
        regionManager = new RegionManager(this, config);
        
        setupPermissions();
        setupEconomy();
        setupChat();
        
        //Register Listeners Here
        blockListener = new RegionBlockListener(this);
        dpeListener = new RegionPlayerInteractListener(this);
        regionEntityListener = new RegionEntityListener(this);
        guiManager = new GUIManager(this);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(blockListener, this);
        
        pm.registerEvents(regionEntityListener, this);
        
        pm.registerEvents(dpeListener, this);
        
        pm.registerEvents(new CustomListener(this), this);
        
        pm.registerEvents(guiManager, this);
        
        pm.registerEvents(new GUIListener(regionManager), this);
        pm.registerEvents(new InfoGUIListener(regionManager), this);
        pm.registerEvents(new RequirementsGUIListener(this), this);
        pm.registerEvents(new ShopGUIListener(this), this);
        
        effectManager = new EffectManager(this);
        
        //Setup repeating sync task for checking regions
        getLogger().info("[REST] starting synchronous effect task");
        theSender = new CheckRegionTask(getServer(), this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, theSender, 10L, 10L);
        //theSender.run();
        
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        long timeUntilDay = (86400000 + cal.getTimeInMillis() - System.currentTimeMillis()) / 50;
        System.out.println("[REST] " + timeUntilDay + " ticks until 00:00");
        DailyTimerTask dtt = new DailyTimerTask(this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, dtt, timeUntilDay, 1728000);

        Permissions.assignPermissions(this);
        getLogger().info("[REST] is now enabled!");
    }
    
    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static EffectManager getEffectManager() {
        return effectManager;
    }
    
    public Map<Player, String> getChannels() {
        return dpeListener.getChannels();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = null;
        try {
            player = (Player) sender;
        } catch (Exception e) {
            warning("Only players can use Township commands");
            return true;
        }

        //Are they in a blacklisted world
        if ((Townships.perm == null || !Townships.perm.has(sender, "townships.admin")) && getConfigManager().getBlackListWorlds().contains(player.getWorld().getName())) {
            sender.sendMessage(ChatColor.RED + "[REST] 야생 월드에서 실행해주세요!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (player != null && !(Townships.perm == null || Townships.perm.has(player, "townships.admin"))) {
                return true;
            }
            config = getConfig();
            regionManager.reload();
            configManager = new ConfigManager(config, this);
            sender.sendMessage("[REST] 리로드됨");
            return true;
        }
        if (player == null) {
            sender.sendMessage("[REST] 플레이어가 아니신 듯?");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("shop")) {
            if (!perm.has(player, "townships.unlock")) {
                player.sendMessage(ChatColor.RED + "[REST] 권한이 부족합니다.");
                return true;
            }
            String category = "";

            if (args.length == 1 && regionManager.getRegionCategories().size() > 1) {
                ShopGUIListener.openCategoryShop(player);
                return true;
            }
            if (args.length != 1) {
                category = args[1].toLowerCase();
                if (category.equals("기타")) {
                    category = "";
                }
            }
            if (!regionManager.getRegionCategories().containsKey(category) && (category.equals("") && 
                    !regionManager.getRegionCategories().containsKey("기타"))
                    && !category.equals("towns")) {
                player.sendMessage(ChatColor.GRAY + "[REST] 잘못된 카테고리입니다");
                return true;
            }

            boolean permNull = perm == null;
            List<RegionType> regions = new ArrayList<RegionType>();

            List<SuperRegionType> superRegions = new ArrayList<SuperRegionType>();

            boolean createAll = permNull || perm.has(player, "townships.create.all");
            if (createAll) {
                player.sendMessage(ChatColor.GOLD + "[REST] 모든 건물을 언락하셨습니다.");
                return true;
            }
            if (!category.equals("마을")) {
                for (String s : regionManager.getRegionCategories().get(category)) {
                    RegionType rt = regionManager.getRegionType(s);
                    if (rt.getUnlockCost() > 0 && !perm.has(player, "townships.create." + s)) {
                        
                        regions.add(regionManager.getRegionType(s));
                    }
                }
            }
            if (category.equals("") && regionManager.getRegionCategories().containsKey("기타")) {
                for (String s : regionManager.getRegionCategories().get("기타")) {
                    RegionType rt = regionManager.getRegionType(s);
                    if (rt.getUnlockCost() > 0 && !perm.has(player, "townships.create." + s)) {
                        
                        regions.add(regionManager.getRegionType(s));
                    }
                }
            }
            if (regions.size() > 1) {
                Collections.sort(regions, new Comparator<RegionType>() {

                    @Override
                    public int compare(RegionType o1, RegionType o2) {
                        return GUIManager.compareRegions(o1, o2);
                    }
                });
            }
            if (category.equals("마을")) {
                for (String s : regionManager.getSuperRegionTypes()) {
                    SuperRegionType srt = regionManager.getSuperRegionType(s);
                    if (srt.getUnlockCost() > 0 && !perm.has(player, "townships.create." + s)) {
                        superRegions.add(regionManager.getSuperRegionType(s));
                    }
                }
            }
            if (superRegions.size() > 1) {
                Collections.sort(superRegions, new Comparator<SuperRegionType>() {

                    @Override
                    public int compare(SuperRegionType o1, SuperRegionType o2) {
                        return GUIManager.compareSRegions(o1, o2);
                    }
                });
            }
            ShopGUIListener.openListShop(regions, superRegions, player, category);
            
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("confirm")) {

            ShopGUIListener.openConfirmation(player, args[1]);
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("unlock")) {
            if (!perm.has(player, "townships.unlock")) {
                player.sendMessage(ChatColor.RED + "[REST] 권한이 부족합니다.");
                return true;
            }
            RegionType rt = regionManager.getRegionType(args[1]);
            NumberFormat formatter = NumberFormat.getCurrencyInstance();
            if (rt != null) {
                if (!econ.has(player, rt.getUnlockCost())) {
                   player.sendMessage(ChatColor.RED + "[REST] "+rt.getName()+"을 사시려면 " + formatter.format(rt.getUnlockCost()) + " 이 필요합니다. "); 
                   return true;
                }
                econ.withdrawPlayer(player, rt.getUnlockCost());
                perm.playerAdd(player, "townships.create." + rt.getName());
                player.sendMessage(ChatColor.GREEN + "[REST] " + rt.getName()+"을(를) 언락하셨습니다");
                
                return true;
            }
            SuperRegionType srt = regionManager.getSuperRegionType(args[1]);
            if (srt != null) {
                if (!econ.has(player, srt.getUnlockCost())) {
                   player.sendMessage(ChatColor.RED + "[REST] "+srt.getName()+"을 사시려면 " + formatter.format(srt.getUnlockCost()) + " 이 필요합니다. "); 
                   return true;
                }
                econ.withdrawPlayer(player, srt.getUnlockCost());
                perm.playerAdd(player, "townships.create." + srt.getName());
                player.sendMessage(ChatColor.GREEN + "[REST] " + srt.getName()+"을(를) 언락하셨습니다");
                
                return true;
            }
            
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("war")) {
            //hs war mySR urSR

            //Check for valid super-regions
            SuperRegion myTown = regionManager.getSuperRegion(args[2]);
            SuperRegion enemyTown = regionManager.getSuperRegion(args[1]);
            if (myTown == null || enemyTown == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 마을이 아닙니다.");
                return true;
            }

            //Check if already at war
            if (regionManager.hasWar(myTown, enemyTown)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + myTown.getName() + " 는 이미 전쟁 중 입니다!");
                return true;
            }

            //Check owner
            if (!myTown.hasOwner(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 타운의 주인이 아닙니다:" + myTown.getName());
                return true;
            }

            //Calculate Cost
            ConfigManager cm = getConfigManager();
            if (!cm.getUseWar()) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 명령어는 비활성화되어있습니다.");
                return true;
            }
            double cost = cm.getDeclareWarBase() + cm.getDeclareWarPer() * (myTown.getOwners().size() + myTown.getMembers().size() +
                    enemyTown.getOwners().size() + enemyTown.getMembers().size());

            //Check money
            if (Townships.econ != null) {
                if (myTown.getBalance() < cost) {
                    player.sendMessage(ChatColor.GRAY + "[REST] " + myTown.getName() + " 는 " + enemyTown.getName() + " 와 전쟁할 자금이 부족합니다. ");
                    return true;
                } else {
                    regionManager.addBalance(myTown, -1 * cost);
                }
            }

            regionManager.setWar(myTown, enemyTown);
            final SuperRegion sr1a = myTown;
            final SuperRegion sr2a = enemyTown;
            new Runnable() {
                  @Override
                  public void run()
                  {
                    getServer().broadcastMessage(ChatColor.RED + "[REST] " + sr1a.getName() + " 이(가) " + sr2a.getName() + " 에 전쟁 선포를 하였습니다!");
                  }
            }.run();
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("peace")) {
            //hs peace mySR urSR

            //Check for valid super-regions
            SuperRegion myTown = regionManager.getSuperRegion(args[2]);
            SuperRegion enemyTown = regionManager.getSuperRegion(args[1]);
            if (myTown == null || enemyTown == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 마을이 아닙니다.");
                return true;
            }

            //Check if already at war
            if (!regionManager.hasWar(myTown, enemyTown)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + myTown.getName() + " 는 현재 전쟁을 하고 있지 않습니다.");
                return true;
            }

            //Check owner
            if (!myTown.hasOwner(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + myTown.getName() + "의 주인이 아닙니다.");
                return true;
            }

            //Calculate Cost
            ConfigManager cm = getConfigManager();
            if (!cm.getUseWar()) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 명령어는 비활성화되어있습니다");
                return true;
            }
            double cost = cm.getMakePeaceBase() + cm.getMakePeacePer() * (myTown.getOwners().size() + myTown.getMembers().size() +
                    enemyTown.getOwners().size() + enemyTown.getMembers().size());

            //Check money
            if (Townships.econ != null) {
                if (myTown.getBalance() < cost) {
                    player.sendMessage(ChatColor.GRAY + "[REST] " + myTown.getName() + " 은(는) " + enemyTown.getName()+" 와 평화 협정을 맺기에 돈이 부족합니다.");
                    return true;
                } else {
                    regionManager.addBalance(myTown, -1 * cost);
                }
            }

            regionManager.setWar(myTown, enemyTown);
            final SuperRegion sr1a = myTown;
            final SuperRegion sr2a = enemyTown;
            new Runnable() {
                  @Override
                  public void run()
                  {
                    getServer().broadcastMessage(ChatColor.RED + "[REST] " + sr1a.getName() + " 와 " + sr2a.getName() + " 는 평화 협정을 맺었습니다!");
                  }
            }.run();
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("shop")) {
            //TODO find what to show them and open the GUI
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("charter")) {

            //Check if valid super region
            SuperRegionType currentRegionType = regionManager.getSuperRegionType(args[1]);
            if (currentRegionType == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 는 옳바른 건물 종류가 아닙니다.");
                int j=0;
                String message = ChatColor.GOLD + "";
                for (String s : regionManager.getSuperRegionTypes()) {
                    if (perm == null || (perm.has(player, "townships.create.all") ||
                            perm.has(player, "townships.create." + s))) {
                        if (message.length() + s.length() + 2 > 55) {
                            player.sendMessage(message + ", ");
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j > 14) {
                            break;
                        } else {
                            message += ", " + s;
                        }
                    }
                }
                if (!message.equals(ChatColor.GOLD + "")) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                }
                return true;
            }

            String regionTypeName = args[1].toLowerCase();
            //Permission Check
            if (perm != null && !perm.has(player, "townships.create.all") &&
                    !perm.has(player, "townships.create." + regionTypeName)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 권한이 부족합니다. 건물 종류: " + regionTypeName);
                return true;
            }

            //Make sure the super-region requires a Charter
            if (currentRegionType.getCharter() <= 0) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 는 계약이 필요하지 않습니다. 생성 명령어: /to create " + args[1]);
                return true;
            }

            //Make sure the name isn't too long
            if (args[2].length() > 15) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름은 너무 깁니다. (최대 16자)");
                return true;
            }
            //Check if valid filename
            if (!Util.validateFileName(args[2])) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 파일 이름이 아닙니다.");
                return true;
            }

            //Check if valid name
            if (pendingCharters.containsKey(args[2].toLowerCase())) {
                player.sendMessage(ChatColor.GRAY + "[REST] 이미 해당 이름은 사용되고 있습니다.");
                return true;
            }
            if (getServer().getPlayerExact(args[2]) != null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 마을 이름은 유저이름과 같을 수 없습니다.");
                return true;
            }

            //Check if allowed super-region
            if (regionManager.getSuperRegion(args[2]) != null && (!regionManager.getSuperRegion(args[2]).hasOwner(player)
                    || regionManager.getSuperRegion(args[2]).getType().equalsIgnoreCase(args[1]))) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 마을은 이미 존재합니다.");
                return true;
            }

            //Add the charter
            Charter tempList = new Charter(regionManager.getSuperRegionType(args[1]), player.getUniqueId());
            pendingCharters.put(args[2].toLowerCase(), tempList);
            configManager.writeToCharter(args[2].toLowerCase(), tempList);
            player.sendMessage(ChatColor.GOLD + "[REST] 성공적으로 계약을 생성하셨습니다. 해당 계약: " + args[2]);
            player.sendMessage(ChatColor.GOLD + "[REST] 다른 유저가 /to signcharter " + args[2] + "을(를) 치면 시작하실 수 있습니다.");
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("charterstats")) {
            //Check if valid charter
            if (!pendingCharters.containsKey(args[1].toLowerCase())) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 는 옳바른 계약 종류가 아닙니다.");
                return true;
            }

            player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 서명인: ");
            int j=0;
            String message = ChatColor.GOLD + "";
            Charter charter = pendingCharters.get(args[1]);
            if (charter != null) {
            	message += charter.getSuperRegionType().getName() + ", ";
                for (UUID s : charter.getMembers()) {
                    if (message.length() + Bukkit.getOfflinePlayer(s).getName().length() + 2 > 55) {
                        player.sendMessage(message);
                        message = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                        break;
                    } else {
                        message += Bukkit.getOfflinePlayer(s).getName() + ", ";
                    }
                }
                if (!charter.getMembers().isEmpty()) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                }
            } else {
                player.sendMessage(ChatColor.RED + "[REST] 계약을 불러오는데 오류가 있었습니다.");
                warning("Failed to load charter " + args[1] + ".yml");
            }

            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("signcharter")) {
            //Check if valid name
            if (!pendingCharters.containsKey(args[1].toLowerCase())) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + "의 계약은 존재하지 않습니다.");
                return true;
            }

            //Check permission
            if (perm != null && !perm.has(player, "townships.join")) {
                player.sendMessage(ChatColor.GRAY + "[REST] 계약을 서명하기엔 권한이 부족합니다.");
                return true;
            }

            //Sign Charter
            Charter charter = pendingCharters.get(args[1].toLowerCase());

            //Check if the player has already signed the charter once
            if (charter.getMembers().contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.GRAY + "[REST] 이미 계약에 서명하셨습니다.");
                return true;
            }

            charter.addMember(player.getUniqueId());
            configManager.writeToCharter(args[1], charter);
            pendingCharters.put(args[1], charter);
            player.sendMessage(ChatColor.GOLD + "[REST] " + args[1] + " 에 서명하셨습니다.");
            int remaining = 0;
            SuperRegionType srt = charter.getSuperRegionType();
            if (srt != null) {
                remaining = srt.getCharter() - charter.getMembers().size();
            }
            if (remaining > 0) {
                player.sendMessage(ChatColor.GOLD + "" + remaining + " 명의 서명이 남았습니다!");
            }
            OfflinePlayer owner = Bukkit.getOfflinePlayer(charter.getMembers().get(0));
            if (owner != null && owner.isOnline()) {
                owner.getPlayer().sendMessage(ChatColor.GOLD + "[REST] " + player.getDisplayName() + " 님이 " + args[1] +" 에 방금 서명하셨습니다.");
                if (remaining > 0) {
                    owner.getPlayer().sendMessage(ChatColor.GOLD + "" + remaining + " 명의 서명이 남았습니다!");
                }
            }
            return true;
        } else if (args.length > 1 && args[0].equals("cancelcharter")) {
            if (!pendingCharters.containsKey(args[1].toLowerCase())) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 에 해당하는 계약이 없습니다.");
                return true;
            }

            if (pendingCharters.get(args[1]).getMembers().size() < 1 || !pendingCharters.get(args[1]).getMembers().get(0).equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 계약의 주인이 아닙니다.");
                return true;
            }

            configManager.removeCharter(args[1]);
            pendingCharters.remove(args[1]);
            player.sendMessage(ChatColor.GOLD + "[REST] " + args[1] + " 에 해당하는 계약을 취소하셨습니다.");
            return true;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            String regionName = args[1];

            //Permission Check
            boolean nullPerms = perm == null;
            boolean createAll = nullPerms || perm.has(player, "townships.create.all");
            if (!(nullPerms || createAll || perm.has(player, "townships.create." + regionName))) {

                if (perm.has(player, "townships.rebuild." + regionName)) {
                    player.performCommand("to rebuild " + regionName);
                    return true;
                }

                player.sendMessage(ChatColor.GRAY + "[REST] " + regionName + "을(를) 건설하기 위한 권한이 부족합니다.");
                return true;
            }

            Location currentLocation = player.getLocation();
            currentLocation.setX(Math.floor(currentLocation.getX()) + 0.4);
            currentLocation.setY(Math.floor(currentLocation.getY()) + 0.4);
            currentLocation.setZ(Math.floor(currentLocation.getZ()) + 0.4);

            //Check if player is standing someplace where a chest can be placed.
            Block currentBlock = currentLocation.getBlock();
            if (!currentBlock.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "[REST] 창고가 설치될 수 있는 위치에 서주세요!");
                return true;
            }
            RegionType currentRegionType = regionManager.getRegionType(regionName);
            if (currentRegionType == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + regionName + " 는 옳바른 건물 종류가 아닙니다.");
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 사용법: /to create " + regionName + " <이름>");
                return true;
            }

            //Check if player can afford to create this region
            double costCheck = 0;
            if (econ != null) {
                double cost = currentRegionType.getMoneyRequirement();
                if (econ.getBalance(player) < cost) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 해당 건물을 짓기 위해선 ¢" + cost + " 이 필요합니다.");
                    return true;
                } else {
                    costCheck = cost;
                }

            }

            //Check if over max number of regions of that type
            if (regionManager.isAtMaxRegions(player, currentRegionType)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + currentRegionType.getName() + "을(를) 더 짓으실 권한이 없습니다.");
                return true;
            }

            //Check if above min y
            if (currentRegionType.getMinY() != -1 && Math.floor(currentRegionType.getMinY()) > Math.floor(currentLocation.getY())) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 현재 건물은 " + Math.floor(currentLocation.getY()) + "y 에 있습니다. 최소한 " + currentRegionType.getMinY() + "y 위에는 있어야 합니다.");
                    return true;
            }

            //Check if above max y
            if (currentRegionType.getMaxY() != -1 && Math.floor(currentRegionType.getMaxY()) < Math.floor(currentLocation.getY())) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 현재 건물은 " + Math.floor(currentLocation.getY()) + "y 에 있습니다. 최소한 " + currentRegionType.getMaxY() + "y 아래에 있어야 합니다.");
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
                player.sendMessage(ChatColor.GRAY + "[REST] 이 건축물은 " + mes + " 바이옴에서만 건설하실 수 있습니다.");
                player.sendMessage(ChatColor.GRAY + "[REST] 현재 바이옴은 " + player.getLocation().getBlock().getBiome().name() + " 입니다.");
                return true;
            }

            //Check if too close to other region
            List<Region> containingRegions = regionManager.getContainingBuildRegions(currentLocation, currentRegionType.getRawBuildRadius());
            if (!containingRegions.isEmpty()) {

                //If the player is an owner of the region, then try to rebuild instead
                if (!containingRegions.get(0).getOwners().isEmpty() &&
                        containingRegions.get(0).getOwners().contains(player.getUniqueId()) &&
                        perm.has(player, "townships.rebuild." + containingRegions.get(0).getType().toLowerCase())) {
                    player.performCommand("to rebuild " + currentRegionType.getName());
                    return true;
                }
                player.sendMessage (ChatColor.GRAY + "[REST] 다른 건물에 너무 가깝게 있습니다.");
                return true;
            }

            //Check if in a super region and if has permission to make that region
            String playername = player.getName();
            List<String> reqSuperRegion = currentRegionType.getSuperRegions();

            boolean meetsReqs = false;
            String limitMessage = null;

            if (reqSuperRegion != null && !reqSuperRegion.isEmpty()) {
                for (SuperRegion sr : regionManager.getContainingSuperRegions(currentLocation)) {
                    if (reqSuperRegion.contains(sr.getType())) {
                        meetsReqs = true;
                        if (!regionManager.isInsideSuperRegion(sr, currentLocation, currentRegionType.getRawBuildRadius())) {
                            player.sendMessage(ChatColor.RED + "[REST] 일부 건물( " + regionName + ") 은 " + sr.getType() +" 안에 있게 됩니다.");
                            return true;
                        }
                        SuperRegionType srt = regionManager.getSuperRegionType(sr.getType());
                        HashMap<String, Integer> limits = srt.getRegionLimits();
                        boolean containsName = limits.containsKey(currentRegionType.getName());
                        boolean containsGroup = !containsName && limits.containsKey(currentRegionType.getGroup());

                        if (containsName || containsGroup) {
                            int limit = containsName ? limits.get(currentRegionType.getName()) : limits.get(currentRegionType.getGroup());

                            if (limit > 0) {
                                int regionCount = 0;
                                for (Region r : regionManager.getContainedRegions(sr)) {
                                    if ((containsName && r.getType().equals(currentRegionType.getName())) || 
                                            (containsGroup && regionManager.getRegionType(r.getType()).getGroup().equals(currentRegionType.getGroup()))) {
                                        regionCount++;
                                        if (limit <= regionCount) {
                                            limitMessage = ChatColor.RED + "[REST] "+ sr.getType()+" 에서는 " + limit + " 개 보다 더 많이 지으실 수 없습니다.";
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!sr.hasOwner(player)) {
                        if (!sr.hasMember(player) || !sr.getMember(player).contains(regionName)) {
                            player.sendMessage(ChatColor.GRAY + "[REST] " + sr.getName() 
                                    + "의 주인으로 부터 " + regionName + "을(를) 건설하실 권한을 부여받지 않으셨습니다.");
                            return true;
                        }
                    }
                }
            } else {
                for (SuperRegion sr : regionManager.getContainingSuperRegions(currentLocation)) {
                    if (!sr.hasOwner(player)) {
                        if (!sr.hasMember(player) || !sr.getMember(player).contains(regionName)) {
                            player.sendMessage(ChatColor.GRAY + "[REST] " + sr.getName() + "의 주인으로 부터 " + regionName + "을(를) 건설하실 권한을 부여받지 않으셨습니다.");
                            return true;
                        }
                    }
                }
                meetsReqs = true;
            }

            if (!meetsReqs) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 건물 ( " + regionName + " )은 ");
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
                    player.sendMessage(message.substring(0, message.length() - 2) + " 에 지으셔야합니다.");
                }
                return true;
            }

            if (limitMessage != null) {
                player.sendMessage(limitMessage);
                return true;
            }

            //Check if it has required blocks
            if (!currentRegionType.getRequirements().isEmpty()) {
                List<String> message = Util.hasCreationRequirements(currentLocation, currentRegionType, regionManager);
                if (!message.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 해당 건축물은 필요 블럭 조건을 충족시키지 않습니다.");
                    for (String s : message) {
                        player.sendMessage(ChatColor.GOLD + s);
                    }
                    return true;
                }
            }
            
            ToPreRegionCreatedEvent preEvent = new ToPreRegionCreatedEvent(currentLocation, currentRegionType, player);
            getServer().getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) {
                return true;
            }

            //Create chest at players feet for tracking reagents and removing upkeep items
            currentBlock.setType(Material.CHEST);

            List<UUID> owners = new ArrayList<UUID>();
            owners.add(player.getUniqueId());
            if (econ != null && costCheck > 0) {
                econ.withdrawPlayer(player, costCheck);
            }

            regionManager.addRegion(currentLocation, regionName, owners);
            player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "성공적으로 건물을 생성하셨습니다:  " + ChatColor.RED + regionName);

            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("create")) {
            //Check if valid name (further name checking later)
            if (args[2].length() > 16 || !Util.validateFileName(args[2])) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 이름이 아닙니다.");
                return true;
            }
            if (getServer().getPlayerExact(args[2]) != null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 마을 이름은 플레이어 이름과 같을 수 없습니다.");
                return true;
            }

            String regionTypeName = args[1];
            //Permission Check
            if (perm != null && !perm.has(player, "townships.create.all") &&
                    !perm.has(player, "townships.create." + regionTypeName)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + regionTypeName+ "을(를) 생성하실 권한이 없습니다");
                return true;
            }

            //Check if valid super region
            Location currentLocation = player.getLocation();
            
            currentLocation.setX(Math.floor(currentLocation.getX()) + 0.4);
            currentLocation.setY(Math.floor(currentLocation.getY()) + 0.4);
            currentLocation.setZ(Math.floor(currentLocation.getZ()) + 0.4);

            SuperRegionType currentRegionType = regionManager.getSuperRegionType(regionTypeName);
            if (currentRegionType == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + regionTypeName + " 는 옳바른 건물 종류가 아닙니다.");
                int j=0;
                String message = ChatColor.GOLD + "";
                for (String s : regionManager.getSuperRegionTypes()) {
                    if (perm == null || (perm.has(player, "townships.create.all") ||
                            perm.has(player, "townships.create." + s))) {
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
                if (!regionManager.getSuperRegionTypes().isEmpty()) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                }
                return true;
            }

            //Check if player can afford to create this townships
            double costCheck = 0;
            if (econ != null) {
                double cost = currentRegionType.getMoneyRequirement();
                if (econ.getBalance(player) < cost) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 해당 건물을 생성하기 위해선 ¢" + cost + " 이 필요합니다..");
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
                        if (!pendingCharters.containsKey(args[2])) {
                            player.sendMessage(ChatColor.GRAY + "[REST] 계약을 먼저 생성하셔야 합니다. 명령어: /to charter " + args[1] + " " + args[2]);
                            return true;
                        } else if (pendingCharters.get(args[2]).getMembers().size() <= currentCharter) {
                            player.sendMessage(ChatColor.GRAY + "[REST] " + currentCharter + " 명의 서명이 필요합니다. 서명 명령어: /to signcharter " + args[2]);
                            return true;
                        } else if (!pendingCharters.get(args[2]).getSuperRegionType().equals(args[1]) ||
                                !pendingCharters.get(args[2]).getMembers().get(0).equals(player.getUniqueId())) {
                            player.sendMessage(ChatColor.GRAY + "[REST] 이 이름을 갖은 계약서는 다른 건물 또는 다른 유저의 것 입니다.");
                            player.sendMessage(ChatColor.GRAY + "주인: " + Bukkit.getOfflinePlayer(pendingCharters.get(args[2]).getMembers().get(0)).getName() + ", 종류: " + pendingCharters.get(args[2]).getSuperRegionType().getName());
                            return true;
                        } else {
                            int i =0;
                            for (UUID s : pendingCharters.get(args[2]).getMembers()) {
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
                        warning("Possible failure to find correct charter for " + args[2]);
                    }
                }
            } else if (pendingCharters.containsKey(args[2])) {
                if (currentCharter > 0) {
                    try {
                        if (pendingCharters.get(args[2]).getMembers().size() <= currentCharter) {
                            player.sendMessage(ChatColor.GRAY + "[REST] " + currentCharter + " 명의 서명이 필요합니다. 명령어: /to signcharter " + args[2]);
                            return true;
                        } else if (!pendingCharters.get(args[2]).getSuperRegionType().getName().equalsIgnoreCase(args[1]) ||
                                !pendingCharters.get(args[2]).getMembers().get(0).equals(player.getUniqueId())) {
                            player.sendMessage(ChatColor.GRAY + "[REST] 이 이름을 갖은 계약서는 다른 건물 또는 다른 유저의 것 입니다.");
                            player.sendMessage(ChatColor.GRAY + "주인: " + Bukkit.getOfflinePlayer(pendingCharters.get(args[2]).getMembers().get(0)).getName() + ", 종류: " + pendingCharters.get(args[2]).getSuperRegionType().getName());
                            return true;
                        } else {
                            int i =0;
                            for (UUID s : pendingCharters.get(args[2]).getMembers()) {
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
                        warning("Possible failure to find correct charter for " + args[2]);
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
            if (regionManager.getSuperRegion(args[2]) != null && (children == null || !children.contains(regionManager.getSuperRegion(args[2]).getType()))) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖은 마을이 이미 존재합니다.");
                return true;
            }



            List<String> quietDestroy = new ArrayList<String>();
            double radius = currentRegionType.getRawRadius();


            //Check if there is an overlapping super-region of the same type
            for (SuperRegion sr : regionManager.getSortedSuperRegions()) {
                try {
                    if (sr.getLocation().distance(currentLocation) < radius + regionManager.getSuperRegionType(sr.getType()).getRawRadius() &&
                            (sr.getType().equalsIgnoreCase(regionTypeName) || !sr.hasOwner(player))) {
                        player.sendMessage(ChatColor.GRAY + "[REST] " + sr.getName() + " 은 이미 이 곳에 있습니다.");
                        return true;
                    }
                } catch (IllegalArgumentException iae) {

                }
            }
            
            SuperRegion originalChild = null;
            if (!req.isEmpty()) {
                for (SuperRegion sr : regionManager.getContainingSuperRegions(currentLocation)) {
                    if (children != null && children.contains(sr.getType()) && sr.hasOwner(player)) {
                        if (children.get(0).equals(sr.getType())) {
                            originalChild = sr;
                        }
                        quietDestroy.add(sr.getName());
                    }

                    String rType = sr.getType();
                    if (!sr.hasOwner(player) && (!sr.hasMember(player) || !sr.getMember(player).contains(regionTypeName))) {
                        player.sendMessage(ChatColor.GRAY + "[REST] " + regionTypeName + "을(를) " + sr.getName() + " 안에 지으실 권한이 없습니다.");
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
                for (Region r : regionManager.getSortedRegions()) {
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
                        } else if (req.containsKey(regionManager.getRegionType(r.getType()).getGroup())) {
                            String group = regionManager.getRegionType(r.getType()).getGroup();
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
                    SuperRegionType srt = regionManager.getSuperRegionType(originalChild.getType());
                    if (srt != null && originalChild.getLocation().distance(currentLocation) > srt.getRadius()) {
                        player.sendMessage(ChatColor.RED + "[REST] " + currentRegionType + " 은 " + originalChild.getType() + "을(를) 전부다 덮을 정도의 크기여야 합니다.");
                        return true;
                    }
                }

                if (!req.isEmpty()) {
                    for (Region r : regionManager.getContainingRegions(currentLocation)) {
                        String rType = regionManager.getRegion(r.getLocation()).getType();
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
                player.sendMessage(ChatColor.GRAY + "[REST] 이 건물은 필요한 모든 건물을 포함하고 있지 않습니다.");
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
                SuperRegion sr = regionManager.getSuperRegion(s);
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
                player.sendMessage(ChatColor.GRAY + "[REST] " + (currentRegionType.getPopulation() - owners.size() - members.size()) + " 명의 맴버가 더 필요합니다.");
                return true;
            }
            
            List<Location> childLocations = null;
            if (originalChild != null) {
                childLocations = originalChild.getChildLocations();
//                System.out.println("[REST] " + originalChild.getLocation().getWorld().getName() + ":" +
//                        originalChild.getLocation().getX() + ":" + originalChild.getLocation().getY() + ":" + originalChild.getLocation().getZ());
                childLocations.add(originalChild.getLocation());
            }

            for (String s : quietDestroy) {
                regionManager.destroySuperRegion(s, false, true);
            }
            if (currentCharter > 0 && pendingCharters.containsKey(args[2])) {
                configManager.removeCharter(args[2]);
                pendingCharters.remove(args[2]);
            }
            String playername = player.getName();
            if (!owners.contains(player.getUniqueId())) {
                owners.add(player.getUniqueId());
            }
            if (costCheck > 0) {
                econ.withdrawPlayer(player, costCheck);
            }

            if (quietDestroy.isEmpty()) {
                balance += getConfigManager().getAutoDeposit();
            }
            
            regionManager.addSuperRegion(args[2], currentLocation, regionTypeName, owners, members, power, balance, childLocations);
            player.sendMessage(ChatColor.GOLD + "[REST] 새로운 건물 (" + args[1] + ") 을 생성하셨습니다. 이름: " + args[2]);
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("disable")) {
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            if (sr == null) {
                player.sendMessage("[REST] 해당 마을을 찾지 못 했습니다:" + args[1]);
                return true;
            }
            if (perm != null && !perm.has(player, "townships.admin")) {
                player.sendMessage("[REST] 권한 부족!");
                return true;
            }
            
            regionManager.setPower(sr, 1);
            
            regionManager.reduceRegion(sr);
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("listall")) {
            if (args.length > 1) {
                SuperRegionType srt = regionManager.getSuperRegionType(args[1]);
                if (srt == null) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 해당 마을을 찾지 못 했습니다: " + args[1]);
                    return true;
                }
                String message = ChatColor.GOLD + "";
                int j =0;
                for (SuperRegion sr : regionManager.getSortedSuperRegions()) {
                    if (message.length() + sr.getName().length() + 2 > 55) {
                        player.sendMessage(message);
                        message = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                        break;
                    } else {
                        message += sr.getName() + ", ";
                    }
                }
                if (!message.equals(ChatColor.GOLD + "")) {
                    player.sendMessage(message);
                }
            } else {
                String message = ChatColor.GOLD + "";
                int j =0;
                for (SuperRegion sr : regionManager.getSortedSuperRegions()) {
                    if (message.length() + sr.getName().length() + 2 > 55) {
                        player.sendMessage(message);
                        message = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                        break;
                    } else {
                        message += sr.getName() + ", ";
                    }
                }
                if (!message.equals(ChatColor.GOLD + "")) {
                    player.sendMessage(message);
                }
            }
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("withdraw")) {
            if (econ == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 경제 플러그인을 못 찾았습니다.");
                return true;
            }
            double amount = 0;
            try {
                amount = Double.parseDouble(args[1]);
                if (amount < 0) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 숫자를 입력해주세요.");
                    return true;
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 양이 아닙니다. 사용법: /to withdraw <금액> <마을 이름>");
                return true;
            }

            //Check if valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[2] + " 은 마을이 아닙니다.");
                return true;
            }

            //Check if owner or permitted member
            if ((!sr.hasMember(player) || !sr.getMember(player).contains("withdraw")) && !sr.hasOwner(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 당신은 해당 건물의 맴버가 아니거나 권한이 없습니다.");
                return true;
            }

            //Check if bank has that money
            double output = regionManager.getSuperRegionType(sr.getType()).getOutput();
            if (output < 0 && sr.getBalance() - amount < -output) {
                player.sendMessage(ChatColor.GRAY + "[REST] 최소 금액을 넘어선 금액을 인출하실 수 없습니다.");
                return true;
            } else if (output >= 0 && sr.getBalance() - amount < 0) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + sr.getName() + " 돈이 부족합니다..");
                return true;
            }

            //Withdraw the money
            econ.depositPlayer(player, amount);
            regionManager.addBalance(sr, -amount);
            player.sendMessage(ChatColor.GOLD + "[REST] " + amount + " 페니를 " + args[2] + " 에서 인출하셨습니다.");
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("deposit")) {
            if (econ == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 경제 플러그인 MIA!");
                return true;
            }
            double amount = 0;
            try {
                amount = Double.parseDouble(args[1]);
                if (amount < 0) {
                    player.sendMessage(ChatColor.GRAY + "[REST] ㅇㅅㅇ? 옳바른 숫자를 입력해주세요.");
                    return true;
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 금액이 아닙니다. 명령어: /to deposit <금액> <마을 이름>");
                return true;
            }

            //Check if player has that money
            if (!econ.has(player, amount)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 돈이 부족합니다.");
                return true;
            }

            //Check if valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[2] + " 는 마을이 아닙니다.");
                return true;
            }

            //Check if owner or member
            if (!sr.hasMember(player) && !sr.hasOwner(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 유저분은 " + args[2] + "의 맴버가 아닙니다.");
                return true;
            }

            //Deposit the money
            econ.withdrawPlayer(player, amount);
            regionManager.addBalance(sr, amount);
            player.sendMessage(ChatColor.GOLD + "[REST] " + amount + "페니를 " + args[2] + " 에 입금하셨습니다.");
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("settaxes")) {
            String playername = player.getName();
            //Check if the player is a owner or member of the super region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖은 건물이 없습니다: " + args[2]);
                return true;
            }
            if (!sr.hasOwner(player) && !sr.hasMember(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[2] + "의 세금을 설정할 권한이 없습니다.");
                return true;
            }

            //Check if member has permission
            if (sr.hasMember(player) && !sr.getMember(player).contains("settaxes")) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[2] + "의 세금을 설정할 권한이 없습니다.");
                return true;
            }

            //Check if valid amount
            double taxes = 0;
            try {
                taxes = Double.parseDouble(args[1]);
                double maxTax = configManager.getMaxTax();
                if (taxes < 0 || taxes > maxTax) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 너무 세금이 높거나 낮습니다.");
                    return true;
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GRAY + "[REST] 명령어: /to settaxes <금액> <상위건물이름>.");
                return true;
            }



            //Set the taxes
            regionManager.setTaxes(sr, taxes);
            player.sendMessage(ChatColor.GOLD + "[REST]  " + args[2] + "의 세금을 " + args[1] + " 로 설정하셨습니다");
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("listperms")) {
            //Get target player
            UUID playername;
            OfflinePlayer currentPlayer = getServer().getOfflinePlayer(args[1]);
            if (currentPlayer == null) {
                player.sendMessage(ChatColor.GOLD + "[REST] " + args[1] + "을 찾을 수 없습니다.");
                              return true;
            } else {
                playername = currentPlayer.getUniqueId();
            }

            String message = ChatColor.GRAY + "[REST] " + playername + " perm for " + args[2] + ":";
            String message2 = ChatColor.GOLD + "";
            //Check if the player is a owner or member of the super region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 건물이 존재하지 않습니다: " + args[2]);
                return true;
            }
            if (sr.hasOwner(player)) {
                player.sendMessage(message);
                player.sendMessage(message2 + "All Permissions");
                return true;
            } else if (sr.hasMember(player)) {
                player.sendMessage(message);
                int j=0;
                for (String s : sr.getMember(Bukkit.getOfflinePlayer(label))) {
                    if (message2.length() + s.length() + 2 > 57) {
                        player.sendMessage(message2);
                        message2 = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                        break;
                    } else {
                        message2 += s + ", ";
                    }
                }
                if (!sr.getMember(Bukkit.getOfflinePlayer(label)).isEmpty()) {
                    player.sendMessage(message2.substring(0, message2.length() - 2));
                }
                return true;
            }
            player.sendMessage(ChatColor.GRAY + "[REST] " + playername + " 님은 해당 건물에 속해있지 않습니다.");
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("listallperms")) {
            player.sendMessage(ChatColor.GRAY + "[REST] 모든 맴버의 권한:");
            player.sendMessage(ChatColor.GRAY + "member = 는 마을의 맴버");
            player.sendMessage(ChatColor.GRAY + "title:<title> = 채널에서 유저의 타이틀");
            player.sendMessage(ChatColor.GRAY + "addmember = 맴버 추가 권한");
            player.sendMessage(ChatColor.GRAY + "<regiontype> = 해당 건물 종류 생성 권한");
            player.sendMessage(ChatColor.GRAY + "withdraw = 은행 출금 권한");
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("ch")) {
            //Check if wanting to be set to any other channel
            if (args.length == 1 || args[1].equalsIgnoreCase("o") || args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("none")) {
                dpeListener.setPlayerChannel(player, "");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(ChatColor.GRAY + "[REST] /to ch 채널이름.  /to ch (<-전체 채팅으로 돌아가기)");
                return true;
            }

            //Check if valid super region
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 마을은 존재하지 않습니다. (" + args[1] + ").");
                player.sendMessage(ChatColor.GRAY + "명령어: /to ch  를 치시면 전체 채팅으로 돌아갈 수 있습니다.");
                return true;
            }

            //Check if player is a member or owner of that super-region
            String playername = player.getName();
            if (!sr.hasMember(player) && !sr.hasOwner(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + "의 맴버이실 경우에만 해당 채팅 채널에 참여하실 수 있습니다.");
                return true;
            }

            //Set the player as being in that channel
            dpeListener.setPlayerChannel(player, args[1]);
            return true;
        } else if (args.length > 2 && (args[0].equalsIgnoreCase("addmember") || args[0].equalsIgnoreCase("add"))) {
            //Check if valid super region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 마을은 존재하지 않습니다. (" + args[2] + ").");
                return true;
            }

            //Check if player is a member or owner of that super-region
            String playername = player.getName();
            boolean isOwner = sr.hasOwner(player.getUniqueId());
            boolean isMember = sr.hasMember(player.getUniqueId());
            boolean isAdmin = Townships.perm.has(player, "townships.admin");
            boolean isOp = player.isOp();
            if (!isMember && !isOwner && !isAdmin && !isOp) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[2] + "의 맴버가 아닙니다.");
                return true;
            }

            //Check if player has permission to invite players
            if (!isAdmin && isMember && !sr.getMember(player.getUniqueId()).contains("addmember")) {
                player.sendMessage(ChatColor.GRAY + "[REST] 권한이 부족합니다. " + args[2] + " 님에게 문의하세요");
                return true;
            }

            //Check if valid player
            Player invitee = getServer().getPlayer(args[1]);
            SuperRegion town = regionManager.getSuperRegion(args[1]);
            if (invitee == null /* && town == null */) { // TODO: revive this code
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + "는 온라인이 아닙니다.");
                return true;
            }

            //Check permission townships.join
            if (invitee != null && !perm.has(invitee, "townships.join") && !perm.has(invitee, "townships.join." + sr.getName())) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 는 마을에 가입할 수 있는 권한이 없습니다.");
                return true;
            }

            //Check if already a town member of a blacklisted town
            if (invitee != null && !configManager.getMultipleTownMembership()) {
                for (SuperRegion sr1 : regionManager.getSortedSuperRegions()) {
                    if ((sr1.hasOwner(invitee) || sr1.hasMember(invitee)) &&
                            !configManager.containsWhiteListTownMembership(sr1.getType())) {
                        player.sendMessage(ChatColor.GRAY + "[REST] 다른 마을의 맴버입니다.");
                        return true;
                    }
                }
            }

            //Check if has housing effect and if has enough housing
            if (!(Townships.perm != null && Townships.perm.has(player, "townships.admin")) && (regionManager.getSuperRegionType(sr.getType()).hasEffect("housing") && !regionManager.hasAvailableHousing(sr))) {
                player.sendMessage(ChatColor.GRAY + "[REST] 다른 유저를 " + sr.getName() + " 에 영입하시려면 집을 더 지으셔야 합니다.");
                return true;
            }

            //Send an invite
            if (invitee != null) {
                pendingInvites.put(invitee.getUniqueId(), args[2].toLowerCase());
                player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.GOLD + invitee.getDisplayName() + ChatColor.GRAY + " 님을 " + ChatColor.GOLD + args[2] + " 에 초대하셨습니다.");
                invitee.sendMessage(ChatColor.GOLD + "[REST] " + args[2] + " 에 초대받으셨습니다. 수락하시려면 /to accept " + args[2] +"을(를) 쳐주세요");
            } else {
            	// TODO: revive this code
            	/* //Add the town to the super region
                List<String> perm = new ArrayList<String>();
                perm.add("member");
                regionManager.setMember(sr, "sr:" + town.getName(), perm);
                for (OfflinePlayer s : sr.getMembers().keySet()) {
                    Player p = getServer().getPlayer(s);
                    if (p != null) {
                        p.sendMessage(ChatColor.GOLD + town.getName() + " has joined " + args[1]);
                    }
                }
                for (OfflinePlayer s : sr.getOwners()) {
                    Player p = getServer().getPlayer(s);
                    if (p != null) {
                        p.sendMessage(ChatColor.GOLD + town.getName() + " has joined " + args[1]);
                    }
                } */
            }
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("accept")) {
            //Check if player has a pending invite to that super-region
            if (!pendingInvites.containsKey(player.getUniqueId()) || !pendingInvites.get(player.getUniqueId()).equals(args[1].toLowerCase())) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 에 초대받지 않으셨습니다.");
                return true;
            }

            //Check if valid super region
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 마을은 없습니다. (" + args[1] + ").");
                return true;
            }

            //Check if player is a member or owner of that super-region
            String playername = player.getName();
            if (sr.hasMember(player) || sr.hasOwner(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 이미 해당 건물의 맴버입니다. " + args[1]);
                return true;
            }

            //Add the player to the super region
            List<String> perm = new ArrayList<String>();
            perm.add("member");
            regionManager.setMember(sr, player, perm);
            pendingInvites.remove(player.getName());
            player.sendMessage(ChatColor.GOLD + "[REST] " + args[1] + " 에 오신 걸 환영합니다.");
            for (UUID s : sr.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(s);
                if (p != null) {
                    p.sendMessage(ChatColor.GOLD + playername + " 님이 " + args[1] + " 에 가입하셨습니다.");
                }
            }
            for (UUID s : sr.getOwners()) {
                Player p = Bukkit.getPlayer(s);
                if (p != null) {
                    p.sendMessage(ChatColor.GOLD + playername + " 님이 " + args[1]+ " 에 가입하셨습니다.");
                }
            }
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("addowner")) {
            Player p = getServer().getPlayer(args[1]);
            String playername = args[1];

            //Check valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 마을은 없습니다." + args[2]);
                return true;
            }

            //Check valid player
            if (p == null || !sr.hasMember(p)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 닉네임을 갖고 있는 온라인인 멤버가 없습니다: " + args[1]);
                return true;
            } else {
                playername = p.getName();
            }


            //Check if player is an owner of that region
            if (!sr.hasOwner(player) && !Townships.perm.has(player, "townships.admin")) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[2] + "의 소유권을 갖고 계시지 않습니다.");
                return true;
            }

            //Check if playername is already an owner
            if (sr.hasOwner(p)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + "는 이미 해당 유저가 소유 중입니다. " + args[2]);
                return true;
            }

            //Check if player is member of super-region
            if (!sr.hasMember(p)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + "는 해당 마을의 멤버가 아닙니다 " + args[2]);
                return true;
            }

            regionManager.removeMember(sr, p);
            if (p != null)
                p.sendMessage(ChatColor.GOLD + "[REST] 이제 " + args[2] + "의 소유권을 갖고 계십니다.");
            for (UUID s : sr.getMembers().keySet()) {
                Player pl = Bukkit.getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + "님은 " + args[2] + "의 주인이 되셨습니다.");
                }
            }
            for (UUID s : sr.getOwners()) {
                Player pl = Bukkit.getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + "님은 " + args[2] + "의 주인이 되셨습니다.");
                }
            }
            regionManager.setOwner(sr, p);
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("leave")) {
            player.performCommand("to remove " + player.getName() + " " + args[1]);
            return true;
        } else if (args.length > 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("kick"))) {
            OfflinePlayer p = getServer().getOfflinePlayer(args[1]);
            String playername = args[1];


            //Check valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 마을은 없습니다. " + args[2]);
                return true;
            }

            //Check valid player
            if (p == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 사용자는 없습니다. " + args[2]);
                return true;
            }

            boolean isMember = sr.hasMember(p); 
            boolean isOwner = sr.hasOwner(p); 
            boolean isAdmin = Townships.perm.has(player, "townships.admin");

            //Check if player is member or owner of super-region
            if (!isMember && !isOwner) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 는 " + args[2] + "의 맴버가 아닙니다.");
                return true;
            }
            //Check if player is removing self
            if (p.equals(player)) {
                if (isMember) {
                    regionManager.removeMember(sr, p);
                } else if (isOwner) {
                    regionManager.setOwner(sr, p);
                }
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[2] + "을(를) 떠나셨습니다.");
                for (UUID s : sr.getMembers().keySet()) {
                    Player pl = Bukkit.getPlayer(s);
                    if (pl != null) {
                        pl.sendMessage(ChatColor.GOLD + playername + " 님이 " + args[2] + "을(를) 떠나셨습니다.");
                    }
                }
                for (UUID s : sr.getOwners()) {
                    Player pl = Bukkit.getPlayer(s);
                    if (pl != null) {
                        pl.sendMessage(ChatColor.GOLD + playername + " 님이 " + args[2] + "을(를) 떠나셨습니다.");
                    }
                }
                return true;
            }

            //Check if player has remove permission
            if (!sr.hasOwner(player) &&  !(!sr.hasMember(player) || !sr.getMember(player).contains("remove"))
                    && !isAdmin) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 맴버를 강퇴할 권한을 갖고 계시지 않습니다.");
                return true;
            }


            if (isMember) {
                regionManager.removeMember(sr, p);
            } else if (isOwner) {
                regionManager.setOwner(sr, p);
            } else {
                return true;
            }
            for (Region r : regionManager.getContainedRegions(sr)) {
                if (r.isOwner(p) || r.isMember(p)) {
                    r.remove(p);
                }
                if (r.getOwners().isEmpty()) {
                    r.addOwner(sr.getOwners().get(0));
                }
            }
            if (p.isOnline())
                p.getPlayer().sendMessage(ChatColor.GRAY + "[REST] " + args[2] + "에서 강퇴당하셨습니다.");

            for (UUID s : sr.getMembers().keySet()) {
                Player pl = Bukkit.getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + "님은 " + args[2] + "에서 강퇴당하셨습니다.");
                }
            }
            for (UUID s : sr.getOwners()) {
                Player pl = Bukkit.getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + "님이 " + args[2]+ "에서 강퇴당하셨습니다.");
                }
            }
            return true;
        } else if (args.length > 3 && (args[0].equalsIgnoreCase("toggleperm") || args[0].equalsIgnoreCase("perm"))) {
            Player p = getServer().getPlayer(args[1]);
            String playername = args[1];

            //Check valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[3]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 이름이 아닙니다" + args[3]);
                return true;
            }

            //Check if player is an owner of the super region
            if (!sr.hasOwner(player)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 마을의 주인이 아닙니다" + args[3]);
                return true;
            }

            //Check valid player
            if (p == null && !sr.hasMember(Bukkit.getOfflinePlayer(args[1]).getUniqueId())) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖고 있는 유저가 없습니다. " + args[1]);
                return true;
            } else if (p != null) {
                playername = p.getName();
            }

            //Check if player is member and not owner of super-region
            if (!sr.hasMember(p)) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + "님은 " + args[3] + "을(를) 소유하고 있거나 해당 맴버가 아닙니다.");
                return true;
            }

            if (args[2].equalsIgnoreCase("member")) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + "님에게 너무 잔인한 처사입니다.");
                return true;
            }
            
            List<String> perm = sr.getMember(p);
            if (perm.contains(args[2])) {
                perm.remove(args[2]);
                regionManager.setMember(sr, p, perm);
                player.sendMessage(ChatColor.GRAY + "[REST] 권한: " + args[2] + " 제거 " + args[1] + " 건물: " + args[3]);
                if (p != null)
                    p.sendMessage(ChatColor.GRAY + "[REST] 권한: " + args[2] + " 제거됨 | 건물:" + args[3]);
                return true;
            } else {
                perm.add(args[2]);
                regionManager.setMember(sr, p, perm);
                player.sendMessage(ChatColor.GRAY + "[REST] 권한 " + args[2] + " 추가. 해당 유저:" + args[1] + " 건물: " + args[3]);
                if (p != null)
                    p.sendMessage(ChatColor.GRAY + "[REST] 해당 권한을 받으셨습니다. " + args[2] + " 건물:" + args[3]);
                return true;
            }
        } else if (args.length > 0 && args[0].equalsIgnoreCase("whatshere")) {
            Location loc = player.getLocation();
            boolean foundRegion = false;
            for (Region r : regionManager.getContainingRegions(loc)) {
                foundRegion = true;
                player.sendMessage(ChatColor.GRAY + "[REST] 건물 ID: " + ChatColor.GOLD + r.getID());
                String message = ChatColor.GRAY + "종류: " + r.getType();
                if (!r.getOwners().isEmpty()) {
                    message += ", 소유자: " + Bukkit.getOfflinePlayer(r.getPrimaryOwner()).getName();
                }
                player.sendMessage(message);
            }

            for (SuperRegion sr : regionManager.getContainingSuperRegions(loc)) {
                player.sendMessage(ChatColor.GRAY + "[REST] 마을 이름: " + ChatColor.GOLD + sr.getName());
                String message = ChatColor.GRAY + "종류: " + sr.getType();
                if (!sr.getOwners().isEmpty()) {
                    message += ", 소유자: " + Bukkit.getOfflinePlayer(sr.getOwners().get(0)).getName();
                }
                player.sendMessage(message);
            }
            if (!foundRegion) {
                player.sendMessage(ChatColor.GRAY + "[REST] 이 위치에는 건물이 존재하지 않습니다.");
            }
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("info")) {
            //Check if valid regiontype or super-regiontype
            RegionType rt = regionManager.getRegionType(args[1]);
            SuperRegionType srt = regionManager.getSuperRegionType(args[1]);
            if (rt == null && srt == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 건물 종류가 아닙니다: " + args[1]);
                return true;
            }
            if (rt != null) {
                InfoGUIListener.openInfoInventory(rt, player, null);
            } else if (srt != null) {
                InfoGUIListener.openInfoInventory(srt, player, null);
            }

                /*player.sendMessage(ChatColor.GRAY + "[REST] Info for region type " + ChatColor.GOLD + args[1] + ":");

                String message = "";
                if (rt.getMoneyRequirement() != 0) {
                    message += ChatColor.GRAY + "Cost: " + ChatColor.GOLD + rt.getMoneyRequirement();
                }
                if (rt.getMoneyOutput() != 0) {
                    message += ChatColor.GRAY + ", Payout: " + ChatColor.GOLD + rt.getMoneyOutput();
                }
                message += ChatColor.GRAY + ", Radius: " + ChatColor.GOLD + (int) Math.sqrt(rt.getRadius());
                player.sendMessage(message);

                String description = rt.getDescription();
                int j=0;
                if (description != null) {
                    message = ChatColor.GRAY + "Description: " + ChatColor.GOLD;
                    if (description.length() + message.length() <= 55) {
                        player.sendMessage(message + description);
                        description = null;
                    }
                    while (description != null && j<12) {
                        if (description.length() > 53) {
                            message += description.substring(0, 53);
                            player.sendMessage(message);
                            description = description.substring(53);
                            message = ChatColor.GOLD + "";
                            j++;
                        } else {
                            player.sendMessage(message + description);
                            description = null;
                            j++;
                        }
                    }
                }

                message = ChatColor.GRAY + "Effects: " + ChatColor.GOLD;
                if (rt.getEffects() != null) {
                    for (String is : rt.getEffects()) {
                        String addLine = is.split("\\.")[0] + ", ";
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 12) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                }
                if (rt.getEffects() == null || rt.getEffects().isEmpty()) {
                    message += "None";
                    player.sendMessage(message);
                } else {
                    player.sendMessage(message.substring(0, message.length()-2));
                }
                message = ChatColor.GRAY + "Required Blocks: " + ChatColor.GOLD;
                if (rt.getRequirements() != null) {
                    for (List<HSItem> is : rt.getRequirements()) {
                        String addLine = "";
                        for (HSItem iss : is) {
                            String itemName = "";
                            if (iss.isWildDamage()) {
                                itemName = iss.getMat().name();
                            } else {
                                ItemStack ist = new ItemStack(iss.getMat(), 1, (short) iss.getDamage());
                                itemName = Items.itemByStack(ist).getName();
                            }

                            if (addLine.equals("")) {
                                addLine = iss.getQty() + ":" + itemName + ", ";
                            } else {
                                addLine = " or " + iss.getQty() + ":" + itemName + ", ";
                            }
                        }
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 12) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                }
                if (rt.getRequirements() == null || rt.getRequirements().isEmpty()) {
                    message += "None";
                    player.sendMessage(message);
                } else {
                    player.sendMessage(message.substring(0, message.length()-2));
                }
                message = ChatColor.GRAY + "Required Items: " + ChatColor.GOLD;
                if (rt.getReagents() != null) {
                    for (List<HSItem> is : rt.getReagents()) {
                        String addLine = "";
                        for (HSItem iss : is) {

                            String itemName = "";
                            if (iss.isWildDamage()) {
                                itemName = iss.getMat().name();
                            } else {
                                ItemStack ist = new ItemStack(iss.getMat(), 1, (short) iss.getDamage());
                                itemName = Items.itemByStack(ist).getName();
                            }
                            if (addLine.equals("")) {
                                addLine = iss.getQty() + ":" + itemName + ", ";
                            } else {
                                addLine = " or " + iss.getQty() + ":" + itemName + ", ";
                            }
                        }
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 12) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                }
                if (rt.getReagents() == null || rt.getReagents().isEmpty()) {
                    message += "None";
                    player.sendMessage(message);
                } else {
                    player.sendMessage(message.substring(0, message.length()-2));
                }
                if (rt.getUpkeep() != null && !rt.getUpkeep().isEmpty()) {
                    message = ChatColor.GRAY + "Upkeep Cost: " + ChatColor.GOLD;
                    for (List<HSItem> is : rt.getUpkeep()) {
                        String addLine = "";
                        for (HSItem iss : is) {
                            String itemName = "";
                            if (iss.isWildDamage()) {
                                itemName = iss.getMat().name();
                            } else {
                                ItemStack ist = new ItemStack(iss.getMat(), 1, (short) iss.getDamage());
                                itemName = Items.itemByStack(ist).getName();
                            }

                            if (addLine.equals("")) {
                                addLine = iss.getQty() + ":" + itemName + ", ";
                            } else {
                                addLine = " or " + iss.getQty() + ":" + itemName + ", ";
                            }
                        }
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 12) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                    player.sendMessage(message.substring(0, message.length()-2));
                }

                if (rt.getOutput() != null && !rt.getOutput().isEmpty()) {
                    message = ChatColor.GRAY + "Output: " + ChatColor.GOLD;
                    for (List<HSItem> is : rt.getOutput()) {
                        String addLine = "";
                        for (HSItem iss : is) {
                            String itemName = "";
                            if (iss.isWildDamage()) {
                                itemName = iss.getMat().name();
                            } else {
                                ItemStack ist = new ItemStack(iss.getMat(), 1, (short) iss.getDamage());
                                itemName = Items.itemByStack(ist).getName();
                            }

                            if (addLine.equals("")) {
                                addLine = iss.getQty() + ":" + itemName + ", ";
                            } else {
                                addLine = " or " + iss.getQty() + ":" + itemName + ", ";
                            }
                        }
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 12) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                    player.sendMessage(message.substring(0, message.length()-2));
                }
            } else if (srt != null) {
                player.sendMessage(ChatColor.GRAY + "[REST] Info for super-region type " + ChatColor.GOLD + args[1] + ":");

                String message = "";
                if (srt.getMoneyRequirement() != 0) {
                    message += ChatColor.GRAY + "Cost: " + ChatColor.GOLD + srt.getMoneyRequirement();
                }
                if (srt.getOutput() != 0) {
                    message += ChatColor.GRAY + ", Payout: " + ChatColor.GOLD + srt.getOutput();
                }

                if (!message.equals("")) {
                    player.sendMessage(message);
                }

                message = ChatColor.GRAY + "Power: " + ChatColor.GOLD + srt.getMaxPower() + " (+" + srt.getDailyPower() + "), ";
                if (srt.getCharter() != 0) {
                    message += ChatColor.GRAY + "Charter: " + ChatColor.GOLD + srt.getCharter() + ChatColor.GRAY + ", ";
                }
                message += "Radius: " + ChatColor.GOLD + (int) Math.sqrt(srt.getRadius());

                player.sendMessage(message);

                int j=0;
                if (srt.getDescription() != null) {
                    message = ChatColor.GRAY + "Description: " + ChatColor.GOLD;
                    String tempMess = srt.getDescription();
                    if (tempMess.length() + message.length() <= 55) {
                        player.sendMessage(message + tempMess);
                        tempMess = null;
                    }
                    while (tempMess != null && j<12) {
                        if (tempMess.length() > 53) {
                            message += tempMess.substring(0, 53);
                            player.sendMessage(message);
                            tempMess = tempMess.substring(53);
                            message = ChatColor.GOLD + "";
                            j++;
                        } else {
                            player.sendMessage(message + tempMess);
                            tempMess = null;
                            j++;
                        }
                    }
                }
                message = ChatColor.GRAY + "Effects: " + ChatColor.GOLD;
                List<String> effects = srt.getEffects();
                if (effects != null) {
                    for (String is : effects) {
                        String addLine = is + ", ";
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 11) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                }
                if (effects != null && !effects.isEmpty()) {
                    player.sendMessage(message.substring(0, message.length()-2));
                } else {
                    message += "None";
                    player.sendMessage(message);
                }
                message = ChatColor.GRAY + "Required Regions: " + ChatColor.GOLD;
                if (srt.getRequirements() != null) {
                    for (String is : srt.getRequirements().keySet()) {
                        String addLine = is + ":" + srt.getRequirement(is) + ", ";
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 12) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                }
                if (srt.getRequirements() == null || srt.getRequirements().isEmpty()) {
                    message += "None";
                    player.sendMessage(message);
                } else {
                    player.sendMessage(message.substring(0, message.length()-2));
                }
                if (srt.getChildren() != null) {
                    message = ChatColor.GRAY + "Evolves from: " + ChatColor.GOLD;
                    for (String is : srt.getChildren()) {
                        String addLine = is + ", ";
                        if (message.length() + addLine.length() > 55) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j < 12) {
                            message += addLine;
                        } else {
                            break;
                        }
                    }
                    player.sendMessage(message.substring(0, message.length()-2));
                }
            }*/
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("addowner")) {
            String playername = args[1];
            Player aPlayer = getServer().getPlayer(playername);
            if (aPlayer != null) {
                playername = aPlayer.getName();
            }

            Location loc = player.getLocation();
            for (Region r : regionManager.getContainingBuildRegions(loc)) {
                if (r.isOwner(player) || (perm != null && perm.has(player, "townships.admin"))) {
                    if (r.isOwner(aPlayer)) {
                        player.sendMessage(ChatColor.GRAY + "[REST] " + playername + " 님은 이미 이 건물을 소유하고 있습니다.");
                        return true;
                    }
                    if (r.isMember(aPlayer)) {
                        regionManager.setMember(r, player);
                    }
                    regionManager.setOwner(r, aPlayer);
                    player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "" + playername + " 님을 소유자로 추가함");
                    if (aPlayer != null) {
                        aPlayer.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "이제 " + player.getDisplayName() + "님의 " + r.getType() + "의 공동 소유자입니다.");
                    }
                    return true;
                } else {
                    boolean takeover = false;
                    for (SuperRegion sr : regionManager.getContainingSuperRegions(loc)) {
                        if (!sr.hasOwner(player)) {
                            takeover = false;
                            break;
                        }
                        if (regionManager.getSuperRegionType(sr.getType()).hasEffect("control")) {
                            takeover = true;
                        }
                    }
                    if (takeover) {
                        if (r.isOwner(aPlayer)) {
                            player.sendMessage(ChatColor.GRAY + "[REST] " + playername + "님은 이미 이 건물의 소유자입니다.");
                            return true;
                        }
                        if (r.isMember(aPlayer)) {
                            regionManager.setMember(r, aPlayer);
                        }
                        regionManager.setOwner(r, aPlayer);
                        player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "" + playername + "님을 소유자로 추가함.");
                        if (aPlayer != null) {
                            aPlayer.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "이제 " + player.getDisplayName() + "님의 " + r.getType()+"의 공동 소유자입니다.");
                        }
                        return true;
                    } else {
                        player.sendMessage(ChatColor.GRAY + "[REST]건물을 소유하고 있지 않습니다.");
                        return true;
                    }
                }
            }


            player.sendMessage(ChatColor.GRAY + "[REST]완료.");
            return true;
        } else if (args.length > 1 && (args[0].equalsIgnoreCase("addmember") || args[0].equalsIgnoreCase("add"))) {
            String playername = args[1];
            Player aPlayer = getServer().getPlayer(playername);
            if (aPlayer == null) {
                SuperRegion sr = regionManager.getSuperRegion(args[1]);
                return false;
                // if (sr == null) {
                //    playername = args[1];
                //} else {
                //	// TODO: revive this code
                //    // playername = "sr:" + sr.getName();
                //	return false;
                // }
            } else {
                playername = aPlayer.getName();
            }
            Location loc = player.getLocation();
            for (Region r : regionManager.getContainingBuildRegions(loc)) {
                if (r.isOwner(player) || (perm != null && perm.has(player, "townships.admin"))) {
                    if (r.isMember(aPlayer)) {
                        player.sendMessage(ChatColor.GRAY + "[REST] " + playername + "님은 이미 해당 건물의 맴버입니다.");
                        return true;
                    }
                    // if (r.isOwner(player) && !(aPlayer.equals(player) && r.getOwners().get(0).equals(player))) {
                    //    regionManager.setOwner(r, aPlayer);
                    // }
                    regionManager.setMember(r, aPlayer);
                    player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "" + playername + "님을 건물에 추가함.");
                    return true;
                } else {
                    player.sendMessage(ChatColor.GRAY + "[REST] 건물을 소유하고 있지 않습니다.");
                    return true;
                }
            }


            player.sendMessage(ChatColor.GRAY + "[REST]건물에 서계시지 않습니다.");
            return true;
        } else if (args.length > 2 && args[0].equals("addmemberid")) {
            String playername = args[1];
            OfflinePlayer aPlayer = getServer().getOfflinePlayer(playername);
             if (aPlayer == null) {
            	 return false;
            	 // TODO: revive this code
            //    SuperRegion sr = regionManager.getSuperRegion(args[1]);
            //    if (sr == null) {
            //        playername = args[1];
            //    } else {
            //        playername = "sr:" + sr.getName();
            //    }
            } else {
                playername = aPlayer.getName();
            }
            Region r = null;
            try {
                r = regionManager.getRegionByID(Integer.parseInt(args[2]));
                r.getType();
            } catch (Exception e) {
                player.sendMessage(ChatColor.GRAY + "[REST] " + args[1] + " 옳바른 id가 아님");
                return true;
            }
            if (r.isOwner(player) || (perm != null && perm.has(player, "townships.admin"))) {
                if (r.isMember(aPlayer)) {
                    player.sendMessage(ChatColor.GRAY + "[REST] " + playername + "님은 이미 이 건물의 맴버입니다..");
                    return true;
                }
                if (r.isOwner(aPlayer) && !(aPlayer.equals(player) && r.getOwners().get(0).equals(player.getUniqueId()))) {
                    regionManager.setOwner(r, aPlayer);
                }
                regionManager.setMember(r, aPlayer);
                player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "" + playername + " 님을 해당 건물에 추가함.");
                return true;
            } else {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 건물을 소유하고 있지 않습니다..");
                return true;
            }
        } else if (args.length > 1 && args[0].equalsIgnoreCase("whereis")) {
            RegionType rt = regionManager.getRegionType(args[1]);
            if (rt == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 건물 종류가 아닙니다: " + args[1]);
                return true;
            }
            boolean found = false;
            for (Region r : regionManager.getSortedRegions()) {
                if (r.isOwner(player) && r.getType().equals(args[1])) {
                    player.sendMessage(ChatColor.GOLD + "[REST] " + args[1] + " 좌표: " + ((int) r.getLocation().getX())
                            + ", " + ((int) r.getLocation().getY()) + ", " + ((int) r.getLocation().getZ()));
                    found = true;
                }
            }
            if (!found) {
                player.sendMessage(ChatColor.GOLD + "[REST] " + args[1] + " 발견 못 함.");
            }
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("setowner")) {
            String playername = args[1];
            Player aPlayer = getServer().getPlayer(playername);
            if (aPlayer != null) {
                playername = aPlayer.getName();
            } else {
                player.sendMessage(ChatColor.GRAY + "[REST] " + playername + " 님이 온라인이셔야 합니다.");
                return true;
            }


            Location loc = player.getLocation();
            List<Region> containedRegions = regionManager.getContainingBuildRegions(loc);
            for (Region r : regionManager.getContainingBuildRegions(aPlayer.getLocation())) {
                if (regionManager.isAtMaxRegions(aPlayer, regionManager.getRegionType(r.getType()))) {
                    player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.RED + playername + "" + r.getType()  + "을(를) 더 소유할 수 없습니다");
                    return true;
                }
                if (r.isOwner(player) || (perm != null && perm.has(player, "townships.admin"))) {
                    //Check if too far away
                    if (!containedRegions.contains(r)) {
                        continue;
                    }

                    if (r.isMember(aPlayer)) {
                        regionManager.setMember(r, aPlayer);
                    }
                    regionManager.setMember(r, player);
                    regionManager.setOwner(r, player);
                    regionManager.setPrimaryOwner(r, aPlayer);
                    player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + " " + playername + " 님을 소유자로 설정.");

                    aPlayer.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "이 건물을 소유하고 계시지 않습니다:" + player.getDisplayName() + "님의 " + r.getType());
                    return true;
                } else {
                    player.sendMessage(ChatColor.GRAY + "[REST] 이 건물을 소유하고 계시지 않습니다..");
                    return true;
                }
            }

            if (containedRegions.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "[REST] 건물에 서계시지 않습니다..");
                return true;
            }

            player.sendMessage(ChatColor.GRAY + "[REST] " + playername + " must be close by also.");
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("setmember")) {
            OfflinePlayer edPlayer = getServer().getOfflinePlayer(args[1]);
            if (edPlayer == null) {
                return true;
            }
            for (Region r : regionManager.getContainingRegions(player.getLocation())) {
                if (r.isOwner(player) || perm != null && perm.has(player, "townships.admin")) {
                    return true;
                }
            }
            
            
        } else if (args.length > 1 && args[0].equalsIgnoreCase("remove")) {
            String playername = args[1];
            OfflinePlayer aPlayer = getServer().getOfflinePlayer(playername);
            if (aPlayer != null) {
                playername = aPlayer.getName();
            }
            Location loc = player.getLocation();
            for (Region r : regionManager.getContainingBuildRegions(loc)) {
                if (r.isOwner(player) || (perm != null && perm.has(player, "townships.admin"))) {
                    if (r.isPrimaryOwner(aPlayer)) {
                        player.sendMessage(ChatColor.GRAY + "[REST] /to setowner 을 치셔서 소유자 변경가능.");
                        return true;
                    }
                    if (!r.isMember(aPlayer) && !r.isOwner(aPlayer)) {
                        player.sendMessage(ChatColor.GRAY + "[REST] " + playername + " 님은 이 건물에 속해있지 않습니다.");
                        return true;
                    }
                    if (r.isMember(aPlayer)) {
                        regionManager.setMember(r, aPlayer);
                    } else if (r.isOwner(aPlayer)) {
                        regionManager.setOwner(r, aPlayer);
                    }
                    player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.WHITE + "Removed " + playername + " from the region.");
                    return true;
                } else {
                    player.sendMessage(ChatColor.GRAY + "[REST] 건물을 소유하고 계시지 않습니다.");
                    return true;
                }
            }

            player.sendMessage(ChatColor.GRAY + "[REST] 건물에 서계시지 않습니다.");
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("destroy")) {
            //Check if valid region
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            Region r = null;
            if (sr == null) {
                try {
                    r = regionManager.getRegionByID(Integer.parseInt(args[1]));
                } catch (Exception e) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 가진 건물은 없습니다: " + args[1]);
                    return true;
                }
                if (r == null) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 건물 ID 인식 실패: " + args[1]);
                    return true;
                }
                if ((perm == null || !perm.has(player, "townships.admin")) && (r.getOwners().isEmpty() || !r.getOwners().contains(player.getUniqueId()))) {
                    player.sendMessage(ChatColor.GRAY + "[REST] 해당 건물의 소유자가 아닙니다.");
                    return true;
                }
                RegionType rt = regionManager.getRegionType(r.getType());
                if (rt != null && (getConfigManager().getSalvage() > 0 || rt.getSalvage() != 0) && r.isPrimaryOwner(player)) {
                    NumberFormat formatter = NumberFormat.getCurrencyInstance();
                    double salvageValue = getConfigManager().getSalvage() * rt.getMoneyRequirement();
                    salvageValue = rt.getSalvage() != 0 ? rt.getSalvage() : salvageValue;
                    player.sendMessage(ChatColor.GREEN + "[REST] 건물 " + r.getID() + "을(를) 구조하셨습니다: 값: " + formatter.format(salvageValue));
                    econ.depositPlayer(player, salvageValue);
                }
                regionManager.destroyRegion(r.getLocation());
                regionManager.removeRegion(r.getLocation());
                return true;
            }

            //Check if owner or admin of that region
            if ((perm == null || !perm.has(player, "townships.admin")) && (sr.getOwners().isEmpty() || !sr.getOwners().contains(player.getUniqueId()))) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 건물의 소유자가 아닙니다");
                return true;
            }

            regionManager.destroySuperRegion(args[1], true);
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("destroy")) {
            Location loc = player.getLocation();
            List<Location> locationsToDestroy = new ArrayList<Location>();
            for (Region r : regionManager.getContainingBuildRegions(loc)) {
                if (r.isOwner(player) || (perm != null && perm.has(player, "townships.admin"))) {
                    regionManager.destroyRegion(r.getLocation());
                    locationsToDestroy.add(r.getLocation());
                    break;
                } else {
                    player.sendMessage(ChatColor.GRAY + "[REST] 건물을 소유하고 계시지 않습니다.");
                    return true;
                }
            }

            if (locationsToDestroy.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "[REST] 건물에 서계시지 않습니다.");
            }
            
            for (Location l : locationsToDestroy) {
                regionManager.removeRegion(l);
                player.sendMessage(ChatColor.GRAY + "[REST] 건물 삭제됨.");
            }
            return true;
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("목록") || args[0].equalsIgnoreCase("list"))) {
            String category = "";

            if (args.length == 1 && regionManager.getRegionCategories().size() > 1) {
                GUIListener.openCategoryInventory(player);
                return true;
            }
            if (args.length != 1) {
                category = args[1].toLowerCase();
                if (category.equals("기타")) {
                    category = "";
                }
            }
            
            if (!regionManager.getRegionCategories().containsKey(category) && (category.equals("") && 
                    !regionManager.getRegionCategories().containsKey("기타"))
                    && !category.equals("마을")) {
                player.sendMessage(ChatColor.GRAY + "[REST] 옳바른 카테고리가 아닙니다");
                return true;
            }

            /*player.sendMessage(ChatColor.GRAY + "[REST] list of Region Types");
            String message = ChatColor.GOLD + "";*/
            boolean permNull = perm == null;
            List<RegionType> regions = new ArrayList<RegionType>();

            List<SuperRegionType> superRegions = new ArrayList<SuperRegionType>();

            boolean createAll = permNull || perm.has(player, "townships.create.all");
            if (!category.equals("마을") && category.contains(category)) {
                for (String s : regionManager.getRegionCategories().get(category)) {
                    if (createAll || permNull || perm.has(player, "townships.create." + s)) {
                        /*if (message.length() + s.length() + 2 > 55) {
                            player.sendMessage(message);
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j > 14) {
                            break;
                        } else {
                            message += s + ", ";
                        }*/
                        regions.add(regionManager.getRegionType(s));
                    }
                }
            }
            if (category.equals("") && regionManager.getRegionCategories().containsKey("기타")) {
                for (String s : regionManager.getRegionCategories().get("기타")) {
                    if (createAll || permNull || perm.has(player, "townships.create." + s)) {
                        /*if (message.length() + s.length() + 2 > 55) {
                            player.sendMessage(message);
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j > 14) {
                            break;
                        } else {
                            message += s + ", ";
                        }*/
                        regions.add(regionManager.getRegionType(s));
                    }
                }
            }
            if (regions.size() > 1) {
                Collections.sort(regions, new Comparator<RegionType>() {

                    @Override
                    public int compare(RegionType o1, RegionType o2) {
                        return GUIManager.compareRegions(o1, o2);
                    }
                });
            }
            if (category.equals("마을")) {
                for (String s : regionManager.getSuperRegionTypes()) {
                    if (createAll || permNull || perm.has(player, "townships.create." + s)) {
                        /*if (message.length() + s.length() + 2 > 55) {
                            player.sendMessage(message);
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j > 14) {
                            break;
                        } else {
                            message += s + ", ";
                        }*/
                        superRegions.add(regionManager.getSuperRegionType(s));
                    }
                }
            }
            if (superRegions.size() > 1) {
                Collections.sort(superRegions, new Comparator<SuperRegionType>() {

                    @Override
                    public int compare(SuperRegionType o1, SuperRegionType o2) {
                        return GUIManager.compareSRegions(o1,o2);
                    }
                });
            }
            GUIListener.openListInventory(regions, superRegions, player, category);
            /*if (!message.equals(ChatColor.GOLD + "")) {
                player.sendMessage(message.substring(0, message.length() - 2));
            }*/
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("rename")) {
            //Check if valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 이름을 갖은 상위건물이 없습니다");
                return true;
            }

            //Check if valid name
            if (args[2].length() > 16 && Util.validateFileName(args[2])) {
                player.sendMessage(ChatColor.GRAY + "[REST] 이름이 너무 깁니다. 최대 16자");
                return true;
            }

            //Check if player can rename the super-region
            if (!sr.hasOwner(player) && !Townships.perm.has(player, "townships.admin")) {
                player.sendMessage(ChatColor.GRAY + "[REST] 해당 마을 이름을 변경하는데 필요한 권한이 부족합니다.");
                return true;
            }

            double cost = configManager.getRenameCost();
            if (Townships.econ != null && cost > 0) {
                if (!Townships.econ.has(player, cost)) {
                    player.sendMessage(ChatColor.GRAY + "[REST] " + ChatColor.RED + cost + "을(를) 지불하시면 변경 가능합니다");
                    return true;
                } else {
                    Townships.econ.withdrawPlayer(player, cost);
                }
            }
            ToRenameEvent toRenameEvent = new ToRenameEvent(sr, args[1], args[2]);
            Bukkit.getPluginManager().callEvent(toRenameEvent);
            List<Location> childLocations = sr.getChildLocations();
            regionManager.destroySuperRegion(args[1], false, true);
            regionManager.addSuperRegion(args[2], sr.getLocation(), sr.getType(), sr.getOwners(), sr.getMembers(), sr.getPower(), sr.getBalance(), childLocations);
            player.sendMessage(ChatColor.GOLD + "[REST] " + args[1] + " 는 이제 " + args[2] + " 입니다");
            return true;
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("show"))) {

            return true;
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("who"))) {
            if (args.length == 1) {
                Location loc = player.getLocation();

                if (who(loc, player)) {
                    return true;
                }

                //player.sendMessage(ChatColor.GRAY + "[REST] There are no regions here.");
                player.performCommand("to whatshere");
                return true;
            }

            SuperRegion sr = regionManager.getSuperRegion(args[1]);

            if (sr != null) {

                NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.US);

                SuperRegionType srt = regionManager.getSuperRegionType(sr.getType());
                int population = sr.getOwners().size() + sr.getMembers().size();
                double revenue = sr.getTaxes() * sr.getMembers().size() + srt.getOutput();
                boolean reqs = regionManager.hasAllRequiredRegions(sr);
                boolean hasMoney = sr.getBalance() > 0;
                boolean notDisabled = reqs && hasMoney && sr.getPower() > 0;
                boolean hasGrace = regionManager.refreshGracePeriod(sr, hasMoney && reqs);
                regionManager.refreshGracePeriod(sr, hasMoney && reqs);
                long gracePeriod = regionManager.getRemainingGracePeriod(sr);
                String housing = "NA";
                if (srt.hasEffect("housing")) {
                    int housin = 0;
                    for (Region r : regionManager.getContainedRegions(sr)) {
                        housin += regionManager.getRegionType(r.getType()).getHousing();
                    }
                    housing = housin + "";
                }

                player.sendMessage(ChatColor.GRAY + "[REST] ==:|" + ChatColor.GOLD + sr.getName() + " (" + sr.getType() + ") " + ChatColor.GRAY + "|:==");
                player.sendMessage(ChatColor.GRAY + "인구: " + ChatColor.GOLD + population + "/" + housing + ChatColor.GRAY +
                        " 은행: " + (sr.getBalance() < srt.getOutput() ? ChatColor.RED : ChatColor.GOLD) + formatter.format(sr.getBalance()) + ChatColor.GRAY +
                        " 파워: " + (sr.getPower() < srt.getDailyPower() ? ChatColor.RED : ChatColor.GOLD) + sr.getPower() + 
                        " (+" + srt.getDailyPower() + ") / " + sr.getMaxPower());
                player.sendMessage(ChatColor.GRAY + "세금: " + ChatColor.GOLD + formatter.format(sr.getTaxes())
                        + ChatColor.GRAY + " 전체 매출: " + (revenue < 0 ? ChatColor.RED : ChatColor.GOLD) + formatter.format(revenue) +
                        ChatColor.GRAY + " 비활성화: " + (notDisabled && !hasGrace ? (ChatColor.GOLD + "아님") : (ChatColor.RED + "맞음")));
                
                if (!notDisabled && hasGrace) {
                    long hours = (gracePeriod / (1000 * 60 * 60)) % 24;
                    long minutes = (gracePeriod / (1000 * 60)) % 60;
                    long seconds = (gracePeriod / 1000) % 60;
                    player.sendMessage(ChatColor.GOLD + "평화 기간: " + ChatColor.RED + hours + "h " + minutes + "m " + seconds + "s");
                }
                
                if (sr.hasMember(player) || sr.hasOwner(player)) {
                    player.sendMessage(ChatColor.GRAY + "위치: " + ChatColor.GOLD + (int) sr.getLocation().getX() + ", " + (int) sr.getLocation().getY() + ", " + (int) sr.getLocation().getZ());
                }
                if (sr.getTaxes() != 0) {
                    String message = ChatColor.GRAY + "세금 기록: " + ChatColor.GOLD;
                    for (double d : sr.getTaxRevenue()) {
                        message += formatter.format(d) + ", ";
                    }
                    if (!sr.getTaxRevenue().isEmpty()) {
                        player.sendMessage(message.substring(0, message.length() - 2));
                    } else {
                        player.sendMessage(message);
                    }
                }
                String missingRegions = regionManager.hasAllRequiredRegions(sr, null);
                if (missingRegions != null) {
                    player.sendMessage(missingRegions);
                }
                
                String message = ChatColor.GRAY + "소유자들: " + ChatColor.GOLD;
                int j = 0;
                for (UUID s : sr.getOwners()) {
                    if (message.length() + Bukkit.getOfflinePlayer(s).getName().length() + 2 > 55) {
                        player.sendMessage(message);
                        message = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                      break;  
                    } else {
                        message += Bukkit.getOfflinePlayer(s).getName() + ", ";
                    }
                }
                if (!sr.getOwners().isEmpty()) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                } else {
                    player.sendMessage(message);
                }
                message = ChatColor.GRAY + "맴버들: " + ChatColor.GOLD;
                for (UUID s : sr.getMembers().keySet()) {
                    if (message.length() + 2 + Bukkit.getOfflinePlayer(s).getName().length() > 55) {
                        player.sendMessage(message);
                        message = ChatColor.GOLD + "";
                        j++;
                    }
                    if (j > 14) {
                        break;
                    } else {
                        message += Bukkit.getOfflinePlayer(s).getName() + ", ";
                    }
                }
                if (!sr.getMembers().isEmpty()) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                } else {
                    player.sendMessage(message);
                }
                message = ChatColor.GRAY + "전쟁들: " + ChatColor.GOLD;
                for (SuperRegion srr : regionManager.getWars(sr)) {
                    String s = srr.getName();
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
                if (!sr.getOwners().isEmpty()) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                } else {
                    player.sendMessage(message);
                }
                return true;
            }

            Player p = getServer().getPlayer(args[1]);
            if (p != null) {
                String playername = p.getName();
                player.sendMessage(ChatColor.GRAY + "[REST] " + p.getDisplayName() + " 는 해당 건물의 맴버:");
                String message = ChatColor.GOLD + "";
                int j = 0;
                for (SuperRegion sr1 : regionManager.getSortedSuperRegions()) {
                    if (sr1.hasOwner(p) || sr1.hasMember(p)) {
                        if (message.length() + sr1.getName().length() + 2 > 55) {
                            player.sendMessage(message);
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j > 14) {
                            break;
                        } else {
                            message += sr1.getName() + ", ";
                        }
                    }
                }
                if (!regionManager.getSortedRegions().isEmpty()) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                }
                return true;
            }

            player.sendMessage(ChatColor.GRAY + "[REST] 유저 또는 마을을 찾지 못 했습니다");
            player.performCommand("/to info " + args[1]);
            return true;
        } else if (args.length > 0 && effectCommands.contains(args[0])) {
            Bukkit.getServer().getPluginManager().callEvent(new ToCommandEffectEvent(args, player));
            return true;
        } else {
            //TODO add a page 3 to help for more instruction?
            if (args.length > 0 && args[args.length - 1].equals("2")) {
                sender.sendMessage(ChatColor.GRAY + "[REST] by " + ChatColor.GOLD + "Alice와 노예들 " + ChatColor.GRAY + ": <> = 필수, () = 옵션" +
                        ChatColor.GOLD + " Page 2");
                sender.sendMessage(ChatColor.GRAY + "/to charter <건물> <마을이름>");
                sender.sendMessage(ChatColor.GRAY + "/to charterstats <건물>");
                sender.sendMessage(ChatColor.GRAY + "/to signcharter <건물>");
                sender.sendMessage(ChatColor.GRAY + "/to cancelcharter <마을이름>");
                sender.sendMessage(ChatColor.GRAY + "/to rename <이름> <새이름>");
                sender.sendMessage(ChatColor.GRAY + "/to settaxes <금액> <마을이름>");
                sender.sendMessage(ChatColor.GRAY + "/to withdraw|deposit <금액> <마을이름>");
                sender.sendMessage(ChatColor.GRAY + "/to listperms <유저> <마을이름>");
                sender.sendMessage(ChatColor.GRAY + "/to listallperms");
                sender.sendMessage(ChatColor.GRAY + "/to perm <유저> <권한> <마을이름>");
                sender.sendMessage(ChatColor.GRAY + "/to ch (채널)");
                sender.sendMessage(ChatColor.GRAY + "/to ch - 채널 나가기");
                sender.sendMessage(ChatColor.GRAY + "카페 주소: " + getConfigManager().getHelpPage() + " | " + ChatColor.GOLD + "Page 2/3");
            } else if (args.length > 0 && args[args.length - 1].equals("3")) {
                sender.sendMessage(ChatColor.GRAY + "[REST] by " + ChatColor.GOLD + "Alice와 노예들 " + ChatColor.GRAY + ": <> = 필수, () = 옵션" +
                        ChatColor.GOLD + " Page 3");
                sender.sendMessage(ChatColor.GRAY + "/to war <적마을> <내마을>");
                sender.sendMessage(ChatColor.GRAY + "/to peace <적마을> <내마을>");
                sender.sendMessage(ChatColor.GRAY + "카페 주소: " + getConfigManager().getHelpPage() + " | " + ChatColor.GOLD + "Page 3/3");
            } else if (args.length > 0 && args[args.length - 1].equals("help")) {
            	sender.sendMessage(ChatColor.GRAY + "본 서버는 건물을 바탕으로 한 마을 시스템을 운영하고 있습니다.");
            	sender.sendMessage(ChatColor.GRAY + "/to list 로 건물/마을의 종류를 보신 후 건물을 지어주세요.");
            	sender.sendMessage(ChatColor.GRAY + "건물은 필요한 블록들이 주위에 있을 때 /to create <건물종류> 로 생성하실 수 있습니다.");
            	sender.sendMessage(ChatColor.GRAY + "마을은 필요한 건물들이 주위에 있을 때 /to create <마을종류> <마을이름(정하세요)> 로 생성하실 수 있습니다.");
            	sender.sendMessage(ChatColor.GRAY + "마을에 사람은 /to add <사람> <마을이름> 으로 추가하실 수 있습니다.");
            	sender.sendMessage(ChatColor.GRAY + "건물은 개인소유이며 마을과 다르게 소유됩니다. 건물에 사람은 건물 안에서 /to add <사람> 으로 추가하실 수 있습니다.");
            	sender.sendMessage(ChatColor.GRAY + "건물과 마을에는 각각 고유효과들이 있습니다.이를 바탕으로 삶을 발전시키며 여러분들만의 야생을 창조하십시오.");
            } else {
                sender.sendMessage(ChatColor.GRAY + "[REST] by " + ChatColor.GOLD + "Alice와 노예들 " + ChatColor.GRAY + ": () = optional" +
                        ChatColor.GOLD + " Page 1");
                sender.sendMessage(ChatColor.GRAY + "/to list");
                sender.sendMessage(ChatColor.GRAY + "/to info <건물/마을종류>");
                sender.sendMessage(ChatColor.GRAY + "/to create <건물/마을종류> (마을이름(마을생성시만))");
                sender.sendMessage(ChatColor.GRAY + "/to destroy (마을이름(마을파괴시만))");
                sender.sendMessage(ChatColor.GRAY + "/to add|addowner|remove <playername> (마을이름)");
                sender.sendMessage(ChatColor.GRAY + "/to leave <마을이름>");
                sender.sendMessage(ChatColor.GRAY + "/to whatshere");
                sender.sendMessage(ChatColor.GRAY + "/to who (유저이름|마을이름)");
                sender.sendMessage(ChatColor.GRAY + "카페 주소: " + getConfigManager().getHelpPage() + " |" + ChatColor.GOLD + " Page 1/3");
            }

            return true;
        }
        return false;
    }

    public boolean who(Location loc, Player player) {
        for (Region r : regionManager.getContainingBuildRegions(loc)) {
            player.sendMessage(ChatColor.GRAY + "[REST] ==:|" + ChatColor.GOLD + r.getID() + " (" + r.getType() + ") " + ChatColor.GRAY + "|:==");
            String message = ChatColor.GRAY + "소유자들: " + ChatColor.GOLD;
            int j = 0;
            for (UUID s : r.getOwners()) {
                if (message.length() + Bukkit.getOfflinePlayer(s).getName().length() + 2 > 55) {
                    player.sendMessage(message);
                    message = ChatColor.GOLD + "";
                    j++;
                }
                if (j > 14) {
                    break;
                } else {
                    message += Bukkit.getOfflinePlayer(s).getName() + ", ";
                }
            }
            if (!r.getOwners().isEmpty()) {
                player.sendMessage(message.substring(0, message.length() - 2));
            } else {
                player.sendMessage(message);
            }
            message = ChatColor.GRAY + "맴버들: " + ChatColor.GOLD;
            for (UUID s : r.getMembers()) {
                if (message.length() + 2 + Bukkit.getOfflinePlayer(s).getName().length() > 55) {
                    player.sendMessage(message);
                    message = ChatColor.GOLD + "";
                    j++;
                }
                if (j > 14) {
                    break;
                } else {
                    message += Bukkit.getOfflinePlayer(s).getName() + ", ";
                }
            }
            if (!r.getMembers().isEmpty()) {
                player.sendMessage(message.substring(0, message.length() - 2));
            } else {
                player.sendMessage(message);
            }
            return true;
        }
        return false;
    }

    public void addCommand(String command) {
        effectCommands.add(command);
    }
    
    public boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            econ = rsp.getProvider();
            if (econ != null)
                System.out.println("[REST] Hooked into " + econ.getName());
        }
        return econ != null;
    }
    private boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            perm = permissionProvider.getProvider();
            if (perm != null)
                System.out.println("[REST] Hooked into " + perm.getName());
        }
        return (perm != null);
    }
    private boolean setupChat()
    {
        RegisteredServiceProvider<Chat> chatProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
        if (chatProvider != null) {
            chat = chatProvider.getProvider();
        }
        return (chat != null);
    }
    public RegionManager getRegionManager() {
        return regionManager;
    }

    public CheckRegionTask getCheckRegionTask() {
        return theSender;
    }
    
    public void warning(String s) {
        String warning = "[REST] " + s;
        getLogger().warning(warning);
    }
    
    public void setConfigManager(ConfigManager cm) {
        configManager = cm;
    }
    
    public void setCharters(Map<String, Charter> input) {
        this.pendingCharters = input;
    }
    
}
