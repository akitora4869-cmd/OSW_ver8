package jp.onesignalwolf;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Block;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;
import org.bukkit.util.EulerAngle;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import java.time.Duration;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.io.File;
import java.io.IOException;

public final class OneSignalWolfPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Map<UUID, Long> meleeCooldown = new HashMap<>();
    private final Map<UUID, Body> bodies = new HashMap<>();
    private final Map<UUID, PendingReport> reports = new HashMap<>();
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Set<UUID> voteSubmitted = new HashSet<>();
    private final List<EmpZone> empZones = new ArrayList<>();
    private final List<SmokeZone> smokeZones = new ArrayList<>();
    private final Map<UUID, TextDisplay> tags = new HashMap<>();
    private final Map<UUID, Integer> signalCounts = new HashMap<>();
    private final Map<UUID, Integer> healCounts = new HashMap<>();
    private final Map<UUID, Integer> bodyFindCounts = new HashMap<>();
    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private final Map<UUID, Integer> reportCounts = new HashMap<>();
    private final Map<UUID, Integer> specialCollectCounts = new HashMap<>();
    private final Map<UUID, Long> darknessSince = new HashMap<>();
    private final Map<UUID, Item> willDrops = new HashMap<>();
    private final Map<UUID, String> aidMode = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Deque<TrackPoint>> tracks = new HashMap<>();
    private final Map<UUID, DamageCauseInfo> lastCause = new HashMap<>();
    private final Set<UUID> wildlife = new HashSet<>();
    private long nextWildlifeAt = 0L;
    private boolean running=false, meeting=false, breakerOff=false, blizzard=false;
    private long nextBlizzardAt=0L, blizzardUntil=0L, breakerOffSince=0L;
    private final Map<UUID,Double> temperatures=new HashMap<>();
    private final Map<UUID,Location> indoorPosA=new HashMap<>(), indoorPosB=new HashMap<>();
    private boolean meetingChoicePhase=false;
    private final Set<UUID> meetingAttendees=new HashSet<>(), meetingRefused=new HashSet<>();
    private final Map<UUID,Location> meetingOrigins=new HashMap<>();
    private boolean testMode=false;
    private final Map<UUID, BotData> bots = new LinkedHashMap<>();
    private int remainingGameSeconds=900;
    private long timerMark=0L;
    private BossBar gameBar;
    private NamespacedKey itemKey, ammoKey, energyKey, usesKey;
    private final Map<UUID,Long> lastEnergyDrain=new HashMap<>();
    private final Map<UUID,String> visionHudState=new HashMap<>();
    private final Map<UUID,Set<String>> flashlightLightBlocks=new HashMap<>();
    private final Map<String,org.bukkit.block.data.BlockData> blackoutLights=new HashMap<>();
    private int meetingTask=-1;
    private File mapFile; private YamlConfiguration mapCfg;
    private UUID finalWinner=null; private boolean executionReady=false;
    private final String[] colors={"RED","BLUE","GREEN","YELLOW","PURPLE","ORANGE","WHITE","CYAN","PINK","LIME","GRAY","BROWN"};
    private final Color[] leatherColors={Color.RED,Color.BLUE,Color.GREEN,Color.YELLOW,Color.PURPLE,Color.ORANGE,Color.WHITE,Color.AQUA,Color.FUCHSIA,Color.LIME,Color.GRAY,Color.fromRGB(128,70,30)};
    private final String[] hos={"MEDIC","GUARD","SCOUT","ENGINEER","SIGNALER","TRACKER","FORENSIC","COURIER","SURVIVOR","HUNTER","SABOTEUR","OBSERVER","NEGOTIATOR","LOOTER","OPERATOR"};
    private final String[] missions={"SEND_3_SIGNALS","HEAL_PLAYER","USE_BREAKER","USE_EMP","FIND_BODY","SURVIVE_DARKNESS","KILL_PLAYER","REPORT_BODY","COLLECT_SPECIAL"};

    @Override public void onEnable(){
        saveDefaultConfig(); itemKey=new NamespacedKey(this,"special_item"); ammoKey=new NamespacedKey(this,"ammo"); energyKey=new NamespacedKey(this,"energy"); usesKey=new NamespacedKey(this,"uses");
        getServer().getPluginManager().registerEvents(this,this); PluginCommand osw=Objects.requireNonNull(getCommand("osw")); osw.setExecutor(this); osw.setTabCompleter(this);
        setupNoNameTags(); loadMapConfig();
        gameBar=Bukkit.createBossBar("OneSignalWolf", BarColor.RED, BarStyle.SOLID);
        Bukkit.getScheduler().runTaskTimer(this,this::tick,2L,4L); nextWildlifeAt=System.currentTimeMillis()+getConfig().getLong("wildlife-interval-seconds",180)*1000L; nextBlizzardAt=System.currentTimeMillis()+getConfig().getLong("blizzard.min-interval-seconds",180)*1000L;
        getLogger().info("OneSignalWolf v0.17.5-map-tools-indoor-blizzard enabled (Paper 1.20.1 / Java 17)");
    }
    @Override public void onDisable(){ restorePlayerVisibility(); clearAllFlashlightLights(); tags.values().forEach(Entity::remove); removeAllBots(); if(gameBar!=null)gameBar.removeAll(); saveMapConfig(); }


    private void loadMapConfig(){
        mapFile=new File(getDataFolder(),"map.yml"); mapCfg=YamlConfiguration.loadConfiguration(mapFile);
    }
    private void saveMapConfig(){ if(mapCfg==null||mapFile==null)return; try{mapCfg.save(mapFile);}catch(IOException e){getLogger().warning("Could not save map.yml: "+e.getMessage());} }
    private String blockKey(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private String locString(Location l){return l.getWorld().getName()+","+l.getX()+","+l.getY()+","+l.getZ()+","+l.getYaw()+","+l.getPitch();}
    private Location parseLoc(String raw){if(raw==null)return null;try{String[] a=raw.split(",");World w=Bukkit.getWorld(a[0]);if(w==null)return null;return new Location(w,Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]),Float.parseFloat(a[4]),Float.parseFloat(a[5]));}catch(Exception e){return null;}}
    private boolean configuredBlock(String path,Block b){String key=blockKey(b);return mapCfg.getStringList(path).contains(key)||key.equals(mapCfg.getString(path));}
    private void addConfiguredBlock(String path,Block b){List<String> list=new ArrayList<>(mapCfg.getStringList(path));String k=blockKey(b);if(!list.contains(k))list.add(k);mapCfg.set(path,list);saveMapConfig();}
    private Block targetBlock(Player p){return p.getTargetBlockExact(6);}
    private Location meetingLocation(){Location l=parseLoc(mapCfg.getString("meeting"));return l!=null?l:Bukkit.getWorlds().get(0).getSpawnLocation();}
    private Location lobbyLocation(){Location l=parseLoc(mapCfg.getString("lobby"));return l!=null?l:Bukkit.getWorlds().get(0).getSpawnLocation();}

    private void setRegisteredLights(boolean on){for(String raw:mapCfg.getStringList("lights")){String[] a=raw.split(":");if(a.length!=4)continue;World w=Bukkit.getWorld(a[0]);if(w==null)continue;try{Block b=w.getBlockAt(Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]));String k=blockKey(b);if(!on){blackoutLights.putIfAbsent(k,b.getBlockData().clone());if(b.getBlockData() instanceof org.bukkit.block.data.Lightable li){li.setLit(false);b.setBlockData(li,false);}else b.setType(Material.AIR,false);}else{org.bukkit.block.data.BlockData old=blackoutLights.remove(k);if(old!=null)b.setBlockData(old,false);}}catch(Exception ignored){}}}

    private void setupNoNameTags(){
        Scoreboard sb=Bukkit.getScoreboardManager().getMainScoreboard();
        Team t=sb.getTeam("osw_hidden_names"); if(t==null)t=sb.registerNewTeam("osw_hidden_names");
        t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }

    private void tick(){
        long now=System.currentTimeMillis(); empZones.removeIf(e->e.until<now); smokeZones.removeIf(s->s.until<now);
        tickGameTimer(now); tickBlizzard(now);
        for(Player p:Bukkit.getOnlinePlayers()){
            if(!running) continue;
            PlayerData d=data.get(p.getUniqueId()); if(d==null)continue; tickTemperature(p,now);
            if(breakerOff){ darknessSince.putIfAbsent(p.getUniqueId(),now); if(now-darknessSince.get(p.getUniqueId())>=60000)checkMission(p,"SURVIVE_DARKNESS"); } else darknessSince.remove(p.getUniqueId());
            boolean dark = breakerOff || p.getEyeLocation().getBlock().getLightLevel()<=getConfig().getInt("vision.dark-light-level",5);
            ItemStack flashlight=findHandItem(p,"FLASHLIGHT"), shield=findHotbarItem(p,"EMP_SHIELD");
            boolean flashOn=dark&&flashlight!=null&&energy(flashlight)>0;
            if(flashOn){
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                p.removePotionEffect(PotionEffectType.DARKNESS);
                updateFlashlightLights(p);
                drainEnergyOncePerSecond(p,flashlight,"flashlight-energy-seconds",180);
                setVisionHud(p,"FLASHLIGHT");
            } else if(dark){
                clearFlashlightLights(p);
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,10,1,false,false,false));
                setVisionHud(p,"NONE");
            } else {
                clearFlashlightLights(p);
                p.removePotionEffect(PotionEffectType.DARKNESS);
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                setVisionHud(p,"NONE");
            }
            updateTag(p,d); updateSidebar(p,d);
            if(hasHotbar(p,"TRACKER")) showTracks(p);
            if(shield!=null && inEmp(p.getLocation()) && energy(shield)>0){p.sendActionBar("§5⚠ EMP圏内 §7- 通信は遮断されています");drainEnergyOncePerSecond(p,shield,"emp-shield-energy-seconds",150);}
        }
        updateTagVisibility();
        processReports(now);
        processWillExpiry();
        recordTracks(now);
        tickWildlife(now);
    }


    private void setVisionHud(Player p,String state){
        // v0.17.3: Full-screen custom-font overlays were removed because they overlap
        // Scoreboard/BossBar/Hotbar. Keep only state bookkeeping; vision is rendered
        // by vanilla Night Vision (scope) or temporary LIGHT blocks (flashlight).
        visionHudState.put(p.getUniqueId(),state);
    }

    private void updateFlashlightLights(Player p){
        clearFlashlightLights(p);
        Set<String> placed=new HashSet<>();
        Location eye=p.getEyeLocation();
        org.bukkit.util.Vector dir=eye.getDirection().normalize();
        // A narrow beam: brighter near the player, extending roughly 10 blocks forward.
        for(double dist=1.5;dist<=10.0;dist+=1.25){
            Location center=eye.clone().add(dir.clone().multiply(dist));
            placeTemporaryLight(center,15,placed);
            if(dist>=4.0){
                org.bukkit.util.Vector side=new org.bukkit.util.Vector(-dir.getZ(),0,dir.getX());
                if(side.lengthSquared()>0.001) side.normalize();
                double spread=Math.min(1.15,dist*0.08);
                placeTemporaryLight(center.clone().add(side.clone().multiply(spread)),13,placed);
                placeTemporaryLight(center.clone().subtract(side.clone().multiply(spread)),13,placed);
            }
        }
        flashlightLightBlocks.put(p.getUniqueId(),placed);
    }

    private void placeTemporaryLight(Location loc,int level,Set<String> placed){
        Block b=loc.getBlock();
        if(!b.getType().isAir() && b.getType()!=Material.LIGHT)return;
        String key=blockKey(b);
        // Do not take ownership of a LIGHT block that was not created by this system.
        boolean ours=flashlightLightBlocks.values().stream().anyMatch(set->set.contains(key));
        if(b.getType()==Material.LIGHT && !ours)return;
        b.setType(Material.LIGHT,false);
        if(b.getBlockData() instanceof org.bukkit.block.data.type.Light light){
            light.setLevel(Math.max(0,Math.min(15,level)));
            b.setBlockData(light,false);
        }
        placed.add(key);
    }

    private void clearFlashlightLights(Player p){
        Set<String> old=flashlightLightBlocks.remove(p.getUniqueId());
        if(old==null)return;
        for(String key:old){
            boolean stillUsed=flashlightLightBlocks.values().stream().anyMatch(set->set.contains(key));
            if(!stillUsed) clearTemporaryLightKey(key);
        }
    }

    private void clearTemporaryLightKey(String key){
        String[] a=key.split(":");
        if(a.length!=4)return;
        World w=Bukkit.getWorld(a[0]); if(w==null)return;
        try{
            Block b=w.getBlockAt(Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]));
            if(b.getType()==Material.LIGHT)b.setType(Material.AIR,false);
        }catch(NumberFormatException ignored){}
    }

    private void clearAllFlashlightLights(){
        Set<String> all=new HashSet<>();
        for(Set<String> set:flashlightLightBlocks.values())all.addAll(set);
        flashlightLightBlocks.clear();
        for(String key:all)clearTemporaryLightKey(key);
    }

    private void updateTag(Player p, PlayerData d){
        TextDisplay td=tags.get(p.getUniqueId());
        if(td==null || !td.isValid()){
            td=p.getWorld().spawn(p.getLocation().add(0,2.25,0),TextDisplay.class,x->{x.setBillboard(Display.Billboard.CENTER);x.setSeeThrough(false);x.setShadowed(true);x.setPersistent(false);});
            tags.put(p.getUniqueId(),td);
        }
        td.setText("§f"+d.code+" §8| §f"+p.getName()); td.teleport(p.getLocation().add(0,2.25,0));
    }
    private void updateTagVisibility(){
        double normalRange=getConfig().getDouble("nameplate-range",15);
        for(Player viewer:Bukkit.getOnlinePlayers()) for(Player target:Bukkit.getOnlinePlayers()){
            if(viewer.equals(target))continue;
            TextDisplay td=tags.get(target.getUniqueId());
            boolean visible=canRecognize(viewer,target,normalRange);
            // Hide/show the actual player per viewer, not only the custom name tag.
            if(visible){
                viewer.showPlayer(this,target);
                if(td!=null)viewer.showEntity(this,td);
            }else{
                viewer.hidePlayer(this,target);
                if(td!=null)viewer.hideEntity(this,td);
            }
        }
    }
    private boolean canRecognize(Player viewer,Player target,double normalRange){
        if(!running || meeting || meetingChoicePhase)return true;
        if(viewer.getGameMode()==GameMode.SPECTATOR || target.getGameMode()==GameMode.SPECTATOR)return true;
        if(!data.containsKey(viewer.getUniqueId()) || !data.containsKey(target.getUniqueId()))return true;
        if(!viewer.getWorld().equals(target.getWorld()))return false;
        if(!viewer.hasLineOfSight(target) || smokeBlocks(viewer.getEyeLocation(),target.getEyeLocation()))return false;

        double distance=viewer.getEyeLocation().distance(target.getEyeLocation());
        boolean dark=breakerOff
                || viewer.hasPotionEffect(PotionEffectType.DARKNESS)
                || viewer.getEyeLocation().getBlock().getLightLevel()<=getConfig().getInt("vision.dark-light-level",5);
        boolean storm=blizzard && !isIndoor(viewer.getLocation());
        if(!dark && !storm)return distance<=normalRange;

        double close=dark?getConfig().getDouble("vision.dark-recognition-range",4.0):getConfig().getDouble("blizzard.recognition-range",6.0);
        if(dark&&storm)close=Math.min(close,getConfig().getDouble("blizzard.recognition-range",6.0));
        if(distance<=close)return true;

        ItemStack flashlight=findHandItem(viewer,"FLASHLIGHT");
        if(flashlight==null || energy(flashlight)<=0)return false;
        double range=dark?getConfig().getDouble("vision.flashlight-recognition-range",12.0):getConfig().getDouble("blizzard.flashlight-recognition-range",10.0);
        if(dark&&storm)range=Math.min(range,getConfig().getDouble("blizzard.flashlight-recognition-range",10.0));
        if(distance>range)return false;

        // Flashlight recognition is directional. Dot threshold is configurable; 0.82 is a fairly narrow cone.
        Vector toTarget=target.getEyeLocation().toVector().subtract(viewer.getEyeLocation().toVector());
        if(toTarget.lengthSquared()<0.0001)return true;
        double dot=viewer.getEyeLocation().getDirection().normalize().dot(toTarget.normalize());
        return dot>=getConfig().getDouble("vision.flashlight-cone-dot",0.82);
    }
    private void restorePlayerVisibility(){
        for(Player viewer:Bukkit.getOnlinePlayers())for(Player target:Bukkit.getOnlinePlayers())if(!viewer.equals(target))viewer.showPlayer(this,target);
    }
    private boolean smokeBlocks(Location a,Location b){
        Vector ab=b.toVector().subtract(a.toVector()); double len=ab.length(); if(len==0)return false; Vector dir=ab.normalize();
        for(SmokeZone s:smokeZones){ if(!s.world.equals(a.getWorld()))continue; for(double x=0;x<=len;x+=1){ Location q=a.clone().add(dir.clone().multiply(x)); if(q.distanceSquared(s.center)<=s.radius*s.radius)return true; } } return false;
    }
    private void showTracks(Player viewer){
        long cutoff=System.currentTimeMillis()-getConfig().getLong("tracker-history-seconds",30)*1000L;
        for(Map.Entry<UUID,Deque<TrackPoint>> en:tracks.entrySet()) if(!en.getKey().equals(viewer.getUniqueId())) for(TrackPoint tp:en.getValue()) if(tp.time>=cutoff&&tp.loc.getWorld().equals(viewer.getWorld())&&tp.loc.distanceSquared(viewer.getLocation())<=400) viewer.spawnParticle(Particle.ASH,tp.loc.clone().add(0,.1,0),1,0,0,0,0);
    }
    private void recordTracks(long now){
        if(!running)return;
        long cutoff=now-getConfig().getLong("tracker-history-seconds",30)*1000L;
        for(Player p:Bukkit.getOnlinePlayers()) if(data.containsKey(p.getUniqueId())&&p.getGameMode()!=GameMode.SPECTATOR){
            Location old=lastLocations.put(p.getUniqueId(),p.getLocation().clone());
            if(old==null||!old.getWorld().equals(p.getWorld())||old.distanceSquared(p.getLocation())>.5){Deque<TrackPoint> q=tracks.computeIfAbsent(p.getUniqueId(),k->new ArrayDeque<>());q.addLast(new TrackPoint(p.getLocation().clone(),now));while(!q.isEmpty()&&q.peekFirst().time<cutoff)q.removeFirst();}
        }
    }
    private void tickWildlife(long now){
        if(!running||meeting||!getConfig().getBoolean("wildlife-enabled",true)||now<nextWildlifeAt)return;
        nextWildlifeAt=now+getConfig().getLong("wildlife-interval-seconds",180)*1000L;
        if(mapCfg.getConfigurationSection("wildlife")==null)return;
        List<String> ids=new ArrayList<>(mapCfg.getConfigurationSection("wildlife").getKeys(false)); if(ids.isEmpty())return;
        String id=ids.get(ThreadLocalRandom.current().nextInt(ids.size())); Location l=parseLoc(mapCfg.getString("wildlife."+id)); if(l==null)return;
        Wolf w=l.getWorld().spawn(l,Wolf.class); w.setCustomName("§4Wild Beast"); w.setAngry(true); wildlife.add(w.getUniqueId());
        Player target=l.getWorld().getPlayers().stream().filter(x->data.containsKey(x.getUniqueId())&&x.getGameMode()!=GameMode.SPECTATOR).min(Comparator.comparingDouble(x->x.getLocation().distanceSquared(l))).orElse(null); if(target!=null)w.setTarget(target);
        Bukkit.getScheduler().runTaskLater(this,()->{wildlife.remove(w.getUniqueId());if(w.isValid())w.remove();},20L*getConfig().getLong("wildlife-duration-seconds",60));
    }

    @EventHandler public void onJoin(PlayerJoinEvent e){ Team t=Bukkit.getScoreboardManager().getMainScoreboard().getTeam("osw_hidden_names"); if(t!=null)t.addEntry(e.getPlayer().getName()); }
    @EventHandler public void onAdvancement(PlayerAdvancementDoneEvent e){ if(running)e.message(null); }
    @EventHandler public void onChat(AsyncPlayerChatEvent e){
        if(!running)return;e.setCancelled(true);Player from=e.getPlayer();PlayerData fd=data.get(from.getUniqueId());String name=fd==null?from.getName():fd.code+"|"+from.getName();String msg="§7<§f"+name+"§7> §f"+e.getMessage();
        Bukkit.getScheduler().runTask(this,()->{if(meeting){if(meetingAttendees.contains(from.getUniqueId()))for(Player to:Bukkit.getOnlinePlayers())if(meetingAttendees.contains(to.getUniqueId()))to.sendMessage(msg);return;}double r=getConfig().getDouble("chat-radius",8);for(Player to:Bukkit.getOnlinePlayers())if(to.getWorld().equals(from.getWorld())&&to.getLocation().distanceSquared(from.getLocation())<=r*r&&from.hasLineOfSight(to))to.sendMessage(msg);});
    }
    @EventHandler public void onDeath(PlayerDeathEvent e){
        if(!running)return; Player p=e.getEntity(); e.deathMessage(null); e.setKeepInventory(false); e.getDrops().removeIf(this::isBoundItem);
        PlayerData d=data.get(p.getUniqueId()); if(d!=null){
            for(int i=0;i<d.backpacks;i++)e.getDrops().add(item("BACKPACK"));
            ItemStack note=findItem(p,"NOTE"); if(note!=null){ Item wi=p.getWorld().dropItemNaturally(p.getLocation(),makeWill(note,d.code)); willDrops.put(wi.getUniqueId(),wi); Bukkit.getScheduler().runTaskLater(this,()->expireWill(wi.getUniqueId()),20L*180); }
            DamageCauseInfo dc=lastCause.get(p.getUniqueId());spawnBody(p,d.code,dc);if(dc!=null&&dc.actor!=null&&dc.type.startsWith("環境キル")){Player killer=Bukkit.getPlayer(dc.actor);if(killer!=null){killCounts.merge(killer.getUniqueId(),1,Integer::sum);checkMission(killer,"KILL_PLAYER");killer.sendMessage("§6環境キル成立 §7+撃破判定");}}
        }
        cancelReportsBy(p.getUniqueId()); Bukkit.getScheduler().runTask(this,this::checkLastSurvivor);
    }
    private void spawnBody(Player p,String code,DamageCauseInfo cause){
        Location l=p.getLocation().clone().add(0,-1.25,0); List<ArmorStand> parts=new ArrayList<>();
        // Invisible support entities: only equipped items are visible; no trapdoor/log is required.
        ArmorStand head=p.getWorld().spawn(l.clone().add(0,.22,0),ArmorStand.class,a->{a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setPersistent(false);a.setRotation(l.getYaw(),0);});
        ItemStack skull=new ItemStack(Material.PLAYER_HEAD); SkullMeta sm=(SkullMeta)skull.getItemMeta(); sm.setOwningPlayer(p); skull.setItemMeta(sm); head.getEquipment().setHelmet(skull); parts.add(head);
        ArmorStand torso=p.getWorld().spawn(l.clone().add(0,.08,0),ArmorStand.class,a->{a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setPersistent(false);a.setRotation(l.getYaw(),0);a.setBodyPose(new EulerAngle(Math.PI/2,0,0));});
        torso.getEquipment().setChestplate(coloredChest(data.get(p.getUniqueId()).colorIndex)); parts.add(torso);
        Interaction hit=p.getWorld().spawn(l.clone().add(0,.45,0),Interaction.class,i->{i.setInteractionWidth(1.35f);i.setInteractionHeight(.8f);i.setResponsive(true);i.setPersistent(false);});
        Body b=new Body(UUID.randomUUID(),p.getUniqueId(),code,p.getName(),parts,hit,l,System.currentTimeMillis(),cause==null?"不明":cause.type,false,false); bodies.put(b.id,b);
    }
    private void spawnBotBody(BotData bot,Location l){
        l=l.clone().add(0,-1.25,0);
        List<ArmorStand> parts=new ArrayList<>(); ArmorStand h=l.getWorld().spawn(l.clone().add(0,.22,0),ArmorStand.class,a->{a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setPersistent(false);});
        h.getEquipment().setHelmet(new ItemStack(woolFor(bot.colorIndex)));parts.add(h);
        ArmorStand t=l.getWorld().spawn(l.clone().add(0,.08,0),ArmorStand.class,a->{a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setPersistent(false);a.setBodyPose(new EulerAngle(Math.PI/2,0,0));});t.getEquipment().setChestplate(coloredChest(bot.colorIndex));parts.add(t);
        Interaction hit=l.getWorld().spawn(l.clone().add(0,.45,0),Interaction.class,i->{i.setInteractionWidth(1.35f);i.setInteractionHeight(.8f);i.setResponsive(true);i.setPersistent(false);});
        Body b=new Body(UUID.randomUUID(),bot.entity.getUniqueId(),bot.code,bot.id,parts,hit,l.clone(),System.currentTimeMillis(),"不明",false,true);bodies.put(b.id,b);
    }
    private ItemStack coloredChest(int i){ItemStack x=new ItemStack(Material.LEATHER_CHESTPLATE);LeatherArmorMeta m=(LeatherArmorMeta)x.getItemMeta();m.setColor(leatherColors[i%leatherColors.length]);m.setUnbreakable(true);x.setItemMeta(m);return x;}
    private Material woolFor(int i){Material[] w={Material.RED_WOOL,Material.BLUE_WOOL,Material.GREEN_WOOL,Material.YELLOW_WOOL,Material.PURPLE_WOOL,Material.ORANGE_WOOL,Material.WHITE_WOOL,Material.CYAN_WOOL,Material.PINK_WOOL,Material.LIME_WOOL,Material.GRAY_WOOL,Material.BROWN_WOOL};return w[i%w.length];}
    @EventHandler public void onInteractEntity(PlayerInteractAtEntityEvent e){
        if(!running||meeting)return; Body b=bodies.values().stream().filter(x->x.hit.getUniqueId().equals(e.getRightClicked().getUniqueId())).findFirst().orElse(null);if(b==null)return;e.setCancelled(true);if(nearestBody(e.getPlayer(),4)!=b){e.getPlayer().sendMessage("§7視界が悪く、死体を確認できません。");return;}bodyFindCounts.merge(e.getPlayer().getUniqueId(),1,Integer::sum);checkMission(e.getPlayer(),"FIND_BODY");e.getPlayer().sendMessage("§7死体を発見した：§f"+b.code+" §8| §f"+b.playerName+"§7。通報端末を使用すると通報できます。");
    }
    private Body nearestBody(Player p,double range){
        double actual=range;
        if(blizzard&&!isIndoor(p.getLocation())){
            ItemStack f=findHandItem(p,"FLASHLIGHT"); boolean on=f!=null&&energy(f)>0;
            actual=Math.min(actual,getConfig().getDouble(on?"blizzard.flashlight-body-range":"blizzard.body-range",on?6.0:3.0));
        }
        final double r=actual;
        return bodies.values().stream().filter(b->b.location.getWorld().equals(p.getWorld())&&b.location.distanceSquared(p.getLocation())<=r*r&&!smokeBlocks(p.getEyeLocation(),b.location.clone().add(0,.4,0))&&p.hasLineOfSight(b.hit)).min(Comparator.comparingDouble(b->b.location.distanceSquared(p.getLocation()))).orElse(null);
    }
    private void startReport(Player p,Body b){ if(reports.containsKey(p.getUniqueId()))return; PlayerData pd=data.get(p.getUniqueId()); if(pd!=null&&"FORENSIC".equals(pd.ho))p.sendMessage("§b鑑識 §7死亡推定: "+Math.max(0,(System.currentTimeMillis()-b.diedAt)/1000)+"秒前 / 死因: §f"+b.cause); long due=System.currentTimeMillis()+getConfig().getLong("report-delay-seconds",10)*1000; reports.put(p.getUniqueId(),new PendingReport(p.getUniqueId(),b.id,due)); p.sendMessage("§e通報中… §7あと10秒生存すると会議が開かれます。"); }
    private void processReports(long now){ for(PendingReport r:new ArrayList<>(reports.values())) if(now>=r.due){ Player p=Bukkit.getPlayer(r.reporter); if(p!=null&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR){ Body body=bodies.get(r.body);if(body!=null)body.discovered=true;reports.clear(); reportCounts.merge(p.getUniqueId(),1,Integer::sum); checkMission(p,"REPORT_BODY"); startMeeting(); break;} else reports.remove(r.reporter); } }
    private void cancelReportsBy(UUID id){reports.remove(id);}

    private void startMeeting(){
        if(meeting)return; meeting=true;meetingChoicePhase=true;timerMark=System.currentTimeMillis();votes.clear();voteSubmitted.clear();meetingAttendees.clear();meetingRefused.clear();meetingOrigins.clear();
        Bukkit.broadcastMessage("§c§l死体が通報された §7- 会議への参加を選択してください。");
        for(Player p:Bukkit.getOnlinePlayers()) if(data.containsKey(p.getUniqueId())&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR){meetingOrigins.put(p.getUniqueId(),p.getLocation().clone());p.setInvulnerable(true);p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,20*12,10,false,false,false));sendMeetingChoice(p);}
        Bukkit.getScheduler().runTaskLater(this,this::resolveMeetingChoices,20L*10);
    }
    private void sendMeetingChoice(Player p){TextComponent root=new TextComponent("§e会議が開かれた  ");TextComponent yes=new TextComponent("§a§l[参加する]");yes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/osw meetingjoin yes"));TextComponent no=new TextComponent(" §c§l[参加しない]");no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/osw meetingjoin no"));root.addExtra(yes);root.addExtra(no);p.spigot().sendMessage(root);}
    private void chooseMeeting(Player p,boolean join){if(!meeting||!meetingChoicePhase||!meetingOrigins.containsKey(p.getUniqueId()))return;meetingAttendees.remove(p.getUniqueId());meetingRefused.remove(p.getUniqueId());if(join)meetingAttendees.add(p.getUniqueId());else meetingRefused.add(p.getUniqueId());p.sendMessage(join?"§a会議に参加します。":"§7会議への参加を拒否しました。あなたは『行方不明』として扱われます。");}
    private void resolveMeetingChoices(){if(!meeting)return;meetingChoicePhase=false;for(UUID id:meetingOrigins.keySet())if(!meetingAttendees.contains(id)&&!meetingRefused.contains(id))meetingRefused.add(id);Location loc=meetingLocation();for(Player p:Bukkit.getOnlinePlayers()){if(!data.containsKey(p.getUniqueId()))continue;p.removePotionEffect(PotionEffectType.BLINDNESS);if(meetingAttendees.contains(p.getUniqueId())){p.teleport(loc);openVoteGui(p);}}castBotVotes();int sec=getConfig().getInt("meeting-seconds",90);meetingTask=Bukkit.getScheduler().scheduleSyncDelayedTask(this,this::finishVote,sec*20L);}
    private void openVoteGui(Player p){Inventory inv=Bukkit.createInventory(null,54,"追放投票");int i=0;for(Map.Entry<UUID,PlayerData> en:data.entrySet()){UUID id=en.getKey();if(id.equals(p.getUniqueId()))continue;Player q=Bukkit.getPlayer(id);Body body=bodies.values().stream().filter(b->b.owner.equals(id)).findFirst().orElse(null);boolean dead=q==null||q.isDead()||q.getGameMode()==GameMode.SPECTATOR;boolean knownDead=dead&&body!=null&&body.discovered;if(knownDead)continue;ItemStack x=new ItemStack(Material.PLAYER_HEAD);SkullMeta m=(SkullMeta)x.getItemMeta();if(q!=null)m.setOwningPlayer(q);String status=meetingAttendees.contains(id)?"§a会議参加中":"§e行方不明";m.setDisplayName("§f"+en.getValue().code+" §8| §f"+(q!=null?q.getName():(body!=null?body.playerName:"不明")));m.setLore(List.of(status));m.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"VOTE:"+id);x.setItemMeta(m);inv.setItem(i++,x);if(i>=45)break;}for(BotData b:bots.values())if(b.alive()&&i<45){ItemStack x=new ItemStack(woolFor(b.colorIndex));ItemMeta m=x.getItemMeta();m.setDisplayName("§f"+b.code+" §8| §f"+b.id);m.setLore(List.of("§e行方不明"));m.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"VOTE:"+b.entity.getUniqueId());x.setItemMeta(m);inv.setItem(i++,x);}ItemStack skip=guiItem(Material.GRAY_DYE,"§7棄権",List.of());ItemMeta sm=skip.getItemMeta();sm.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"VOTE:SKIP");skip.setItemMeta(sm);inv.setItem(53,skip);p.openInventory(inv);}
    @EventHandler public void onVoteGui(InventoryClickEvent e){if(!"追放投票".equals(e.getView().getTitle())||!(e.getWhoClicked() instanceof Player p))return;e.setCancelled(true);String v=id(e.getCurrentItem());if(v==null||!v.startsWith("VOTE:"))return;if(v.equals("VOTE:SKIP"))votes.remove(p.getUniqueId());else try{votes.put(p.getUniqueId(),UUID.fromString(v.substring(5)));}catch(Exception ignored){}voteSubmitted.add(p.getUniqueId());p.closeInventory();p.sendMessage("§a投票を受け付けました。");}
    @EventHandler public void onVoteClose(InventoryCloseEvent e){
        if(!"追放投票".equals(e.getView().getTitle())||!(e.getPlayer() instanceof Player p)||!meeting||meetingChoicePhase||!meetingAttendees.contains(p.getUniqueId()))return;
        Bukkit.getScheduler().runTaskLater(this,()->{if(!meeting||voteSubmitted.contains(p.getUniqueId()))return;TextComponent root=new TextComponent("§e投票画面を閉じました。 ");TextComponent reopen=new TextComponent("§a§l[投票画面を開く]");reopen.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/osw voteopen"));root.addExtra(reopen);p.spigot().sendMessage(root);},2L);
    }
    private void finishVote(){Map<UUID,Integer> c=new HashMap<>();votes.values().forEach(v->c.merge(v,1,Integer::sum));int max=c.values().stream().max(Integer::compare).orElse(0);List<UUID> top=new ArrayList<>();c.forEach((k,v)->{if(v==max)top.add(k);});if(max>0&&top.size()==1){UUID target=top.get(0);Body body=bodies.values().stream().filter(b->b.owner.equals(target)).findFirst().orElse(null);Player x=Bukkit.getPlayer(target);BotData bot=bots.get(target);boolean alreadyDead=(x!=null&&(x.isDead()||x.getGameMode()==GameMode.SPECTATOR))||(body!=null);if(alreadyDead){Bukkit.broadcastMessage("§7投票対象は§c既に死亡していた§7。追放者はいません。");if(body!=null)body.discovered=true;}else if(x!=null){Bukkit.broadcastMessage("§c"+code(x)+" §7| §f"+x.getName()+" §cが追放された。");x.setGameMode(GameMode.SPECTATOR);}else if(bot!=null&&bot.alive()){Bukkit.broadcastMessage("§c"+bot.code+" §7| §f"+bot.id+" §cが追放された。");bot.dead=true;if(bot.entity.isValid())bot.entity.remove();}}else Bukkit.broadcastMessage(max==0?"§7追放者なし":"§7同票のため追放者なし");
        for(Iterator<Map.Entry<UUID,Body>> it=bodies.entrySet().iterator();it.hasNext();){Map.Entry<UUID,Body> en=it.next();if(en.getValue().discovered){en.getValue().remove();it.remove();}}
        meeting=false;meetingChoicePhase=false;votes.clear();voteSubmitted.clear();for(Player p:Bukkit.getOnlinePlayers()){p.closeInventory();p.removePotionEffect(PotionEffectType.BLINDNESS);p.setInvulnerable(true);Location back=meetingOrigins.get(p.getUniqueId());if(back!=null&&meetingAttendees.contains(p.getUniqueId()))p.teleport(back);}meetingOrigins.clear();meetingAttendees.clear();meetingRefused.clear();Bukkit.broadcastMessage("§e3... 2... 1... §aゲーム再開");Bukkit.getScheduler().runTaskLater(this,()->{for(Player p:Bukkit.getOnlinePlayers())p.setInvulnerable(false);timerMark=System.currentTimeMillis();checkLastSurvivor();},60L);}
    @EventHandler(priority=EventPriority.HIGHEST) public void meetingSafety(EntityDamageEvent e){if(meeting&&e.getEntity() instanceof Player p&&data.containsKey(p.getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void meetingMove(PlayerMoveEvent e){
        if(!meeting||e.getTo()==null||!data.containsKey(e.getPlayer().getUniqueId()))return;
        UUID id=e.getPlayer().getUniqueId();
        // 選択中と不参加者は位置固定。視点変更だけは許可する。
        if(meetingChoicePhase||meetingRefused.contains(id)){
            Location from=e.getFrom().clone(); Location to=e.getTo();
            if(from.getWorld()!=to.getWorld()||from.distanceSquared(to)>0.0001){from.setYaw(to.getYaw());from.setPitch(to.getPitch());e.setTo(from);} return;
        }
        if(!meetingAttendees.contains(id))return;
        Location c=meetingLocation();double r=getConfig().getDouble("meeting-room-radius",10);
        if(!e.getTo().getWorld().equals(c.getWorld())||e.getTo().distanceSquared(c)>r*r){Location back=e.getFrom().clone();back.setYaw(e.getTo().getYaw());back.setPitch(e.getTo().getPitch());e.setTo(back);}
    }

    @EventHandler public void onDamage(EntityDamageByEntityEvent e){
        if(!running||meeting)return; if(!(e.getEntity() instanceof Player victim))return;
        Player attacker=null; if(e.getDamager() instanceof Player p)attacker=p; else if(e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p)attacker=p;
        if(attacker==null)return;
        lastCause.put(victim.getUniqueId(),new DamageCauseInfo(attacker.getUniqueId(),"PLAYER",System.currentTimeMillis()));
        String id=id(attacker.getInventory().getItemInMainHand());
        if("KNIFE".equals(id)){ lastCause.put(victim.getUniqueId(),new DamageCauseInfo(attacker.getUniqueId(),"BLADE",System.currentTimeMillis()));
            long until=meleeCooldown.getOrDefault(attacker.getUniqueId(),0L); if(System.currentTimeMillis()<until){e.setCancelled(true);attacker.sendActionBar("§c攻撃可能まで待ってください");return;}
            if(isBehind(attacker,victim)){
                e.setCancelled(true); damageUses(attacker.getInventory().getItemInMainHand(),2,10,true);
                ItemStack neck=findItem(victim,"NECK_WARMER");
                if(neck!=null){consume(neck);victim.getWorld().playSound(victim.getLocation(),Sound.BLOCK_ANVIL_LAND,1.2f,1.8f);victim.getWorld().spawnParticle(Particle.CRIT,victim.getEyeLocation().add(0,-.35,0),28,.35,.25,.35,.2);attacker.sendActionBar("§e背後攻撃を防がれた");victim.sendActionBar("§bネックウォーマーが背後攻撃を防いだ！");return;}
                attacker.sendActionBar("§4§l背後攻撃");
                meleeCooldown.put(attacker.getUniqueId(),System.currentTimeMillis()+getConfig().getLong("knife-kill-cooldown-seconds",30)*1000);
                killCounts.merge(attacker.getUniqueId(),1,Integer::sum); checkMission(attacker,"KILL_PLAYER");
                victim.setHealth(0.0); return;
            }
            damageUses(attacker.getInventory().getItemInMainHand(),1,10,true); e.setDamage(5.0);
        }
        if(hasPassive(victim,"BODY_ARMOR")){ e.setDamage(e.getDamage()*.7); damageArmor(victim); }
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void onDamageMonitor(EntityDamageByEntityEvent e){
        if(!running||meeting||!(e.getEntity() instanceof Player victim))return;
        Player attacker=null; if(e.getDamager() instanceof Player p)attacker=p; else if(e.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player p)attacker=p;
        if(attacker==null||attacker.equals(victim))return;
        if(victim.getHealth()-e.getFinalDamage()<=0.001){
            String weapon=id(attacker.getInventory().getItemInMainHand());
            if("KNIFE".equals(weapon)) meleeCooldown.put(attacker.getUniqueId(),System.currentTimeMillis()+getConfig().getLong("knife-kill-cooldown-seconds",30)*1000);
            killCounts.merge(attacker.getUniqueId(),1,Integer::sum); checkMission(attacker,"KILL_PLAYER");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void onAnyDamage(EntityDamageEvent e){
        if(!running||!(e.getEntity() instanceof Player p))return;
        if(SIGNAL_GUI_TITLE.equals(p.getOpenInventory().getTitle())){signalSessions.remove(p.getUniqueId());p.closeInventory();p.sendMessage("§c攻撃を受けたためSignal入力が中断されました。");}
        if(e instanceof EntityDamageByEntityEvent by){ if(by.getDamager() instanceof Wolf w&&wildlife.contains(w.getUniqueId()))lastCause.put(p.getUniqueId(),new DamageCauseInfo(null,"WILDLIFE",System.currentTimeMillis())); return; }
        String t=switch(e.getCause()){case FALL->"落下";case FIRE,FIRE_TICK,LAVA,HOT_FLOOR->"炎";case DROWNING->"溺死";case BLOCK_EXPLOSION,ENTITY_EXPLOSION->"爆発";case SUFFOCATION,CRAMMING->"圧死";default->"環境";};
        DamageCauseInfo prev=lastCause.get(p.getUniqueId());UUID actor=(prev!=null&&prev.actor!=null&&System.currentTimeMillis()-prev.time<=8000)?prev.actor:null;lastCause.put(p.getUniqueId(),new DamageCauseInfo(actor,actor==null?t:"環境キル/"+t,System.currentTimeMillis()));
    }
    private boolean isBehind(Player a,Player v){ Vector facing=v.getLocation().getDirection().setY(0).normalize(); Vector toA=a.getLocation().toVector().subtract(v.getLocation().toVector()).setY(0).normalize(); return facing.dot(toA)<-0.5 && a.getLocation().distanceSquared(v.getLocation())<=9; }
    private void damageArmor(Player p){ ItemStack armor=findItem(p,"BODY_ARMOR"); if(armor==null)return; ItemMeta m=armor.getItemMeta(); if(m instanceof org.bukkit.inventory.meta.Damageable d){d.setDamage(d.getDamage()+1);armor.setItemMeta(m);if(d.getDamage()>=8){p.getInventory().removeItem(armor);p.sendMessage("§cBody Armor broke.");}} }

    @EventHandler public void onInteract(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND)return; Action a=e.getAction(); Player p=e.getPlayer();
        String held=id(p.getInventory().getItemInMainHand());
        if((a==Action.LEFT_CLICK_AIR||a==Action.LEFT_CLICK_BLOCK)&&"FIRST_AID".equals(held)){String m=aidMode.getOrDefault(p.getUniqueId(),"SELF").equals("SELF")?"TARGET":"SELF";aidMode.put(p.getUniqueId(),m);p.sendActionBar("§cFIRST AID §7MODE: §f"+m);e.setCancelled(true);return;}
        if(a!=Action.RIGHT_CLICK_AIR&&a!=Action.RIGHT_CLICK_BLOCK)return;
        if(e.getClickedBlock()!=null){Block b=e.getClickedBlock();
            if(running&&!meeting&&configuredBlock("breakers",b)){breakerOff=!breakerOff;if(breakerOff)breakerOffSince=System.currentTimeMillis();else breakerOffSince=0L;setRegisteredLights(!breakerOff);checkMission(p,"USE_BREAKER");Bukkit.broadcastMessage(breakerOff?"§4停電が発生した。":"§a電力が復旧した。");if(!breakerOff)for(Player q:Bukkit.getOnlinePlayers()){q.removePotionEffect(PotionEffectType.DARKNESS);q.removePotionEffect(PotionEffectType.NIGHT_VISION);}return;}
            if(running&&!meeting&&configuredBlock("emps",b)){double radius=mapCfg.getDouble("emp-settings."+blockKey(b)+".radius",getConfig().getDouble("emp-radius",30));long secs=mapCfg.getLong("emp-settings."+blockKey(b)+".seconds",getConfig().getLong("emp-seconds",60));empZones.add(new EmpZone(b.getWorld(),b.getLocation().add(.5,.5,.5),radius,System.currentTimeMillis()+secs*1000));checkMission(p,"USE_EMP");p.sendMessage("§5EMP activated.");return;}
            if(executionReady&&configuredBlock("execution-switch",b)){executeFinal(p);return;}
            if(running&&!meeting&&configuredBlock("chargers",b)){e.setCancelled(true);rechargeAll(p);p.sendMessage("§a充電施設：電子機器をフル充電しました。");p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,1f,1.4f);return;}
        }
        if(!running||meeting)return; ItemStack it=p.getInventory().getItemInMainHand(); String id=id(it); if(id==null)return;
        if("REPORTER".equals(id)){e.setCancelled(true);Body b=nearestBody(p,4);if(b!=null)startReport(p,b);else p.sendMessage("§7近くに通報できる死体がありません。");return;}
        switch(id){
            case "RADIO" -> openSignalGui(p);
            case "BACKPACK" -> useBackpack(p,it);
            case "SMOKE" -> useSmoke(p,it);
            case "FIRST_AID" -> useAid(p,it);
            case "HANDGUN" -> shootGun(p,it);
            case "STUN" -> shootStun(p,it);
            case "EMERGENCY_BATTERY" -> { if(rechargeAll(p)){consume(it);p.sendMessage("§eEmergency Batteryで電子機器をフル充電しました。");}else p.sendMessage("§7充電が必要な電子機器がありません。"); }
        }
    }
    private final List<String> signalWords=List.of("ME","RED","BLUE","GREEN","YELLOW","PURPLE","ORANGE","WHITE","CYAN","DANGER","SAFE","HELP","TRUST","DOUBT","ATTACK","KILL","FOUND","BODY","BREAKER","EMP","NORTH","SOUTH","EAST","WEST","NOW","PAST","NOT","MAYBE","POINT","HIGH","LOW","WEAPON","FOLLOW","LAB","TOWER");
    private final Map<UUID,SignalSession> signalSessions=new HashMap<>();
    private final Map<UUID,GmPreset> gmPresets=new HashMap<>();
    private static final String GM_GUI_TITLE="§0OneSignalWolf GM設定";
    private static final String GM_PLAYER_TITLE="§0GM個別設定: ";
    private static final String SIGNAL_GUI_TITLE="§0OneSignal 通信機";
    private void openSignalGui(Player p){
        if(!running||meeting)return;
        SignalSession session=new SignalSession();
        signalSessions.put(p.getUniqueId(),session);
        renderSignalGui(p,session);
    }
    private void renderSignalGui(Player p,SignalSession session){
        Inventory inv=Bukkit.createInventory(null,54,SIGNAL_GUI_TITLE);
        inv.setItem(0,guiItem(Material.COMPASS,"§b送信先: §f"+session.target,List.of("§7クリックで送信先を変更","§7ALL または各カラーへ送信")));
        int max=signalMaxWords(p);
        for(int i=0;i<5;i++){
            String value=i<session.words.size()?session.words.get(i):"-";
            Material mat=i<max?Material.LIGHT_BLUE_STAINED_GLASS_PANE:Material.GRAY_STAINED_GLASS_PANE;
            inv.setItem(2+i,guiItem(mat,"§bWORD "+(i+1)+": §f"+value,List.of(i<max?"§7選択した単語が入ります":"§8このスロットは使用できません")));
        }
        inv.setItem(7,guiItem(Material.RED_DYE,"§cCLEAR",List.of("§7選択した単語をすべて消去")));
        inv.setItem(8,guiItem(Material.LIME_DYE,"§aSEND",List.of("§7Signalを送信","§7GUI操作中も攻撃を受けます")));
        int slot=9;
        for(String word:signalWords){
            inv.setItem(slot++,guiItem(Material.PAPER,"§f"+word,List.of("§7クリックして追加")));
        }
        while(slot<45)inv.setItem(slot++,guiItem(Material.BLACK_STAINED_GLASS_PANE," ",List.of()));
        inv.setItem(49,guiItem(inEmp(p.getLocation())?Material.REDSTONE_TORCH:Material.LIME_DYE,
                inEmp(p.getLocation())?"§c通信不能":"§a通信可能",
                List.of(inEmp(p.getLocation())?"§7EMPの影響下では送受信できません":"§7現在Signalを送信できます")));
        p.openInventory(inv);
    }
    private ItemStack guiItem(Material material,String name,List<String> lore){ItemStack x=new ItemStack(material);ItemMeta m=x.getItemMeta();m.setDisplayName(name);m.setLore(lore);x.setItemMeta(m);return x;}
    private int signalMaxWords(Player p){PlayerData d=data.get(p.getUniqueId());return 3+(hasHotbar(p,"SIGNAL_BOOSTER")?1:0)+(d!=null&&"SIGNALER".equals(d.ho)?1:0);}
    private List<String> signalTargets(Player p){
        List<String> out=new ArrayList<>();out.add("ALL");
        for(Player q:Bukkit.getOnlinePlayers())if(data.containsKey(q.getUniqueId())&&!q.equals(p))out.add(code(q));
        for(BotData b:bots.values())if(b.alive())out.add(b.code);
        return out;
    }
    private void cycleSignalTarget(Player p,SignalSession session){List<String> targets=signalTargets(p);int idx=targets.indexOf(session.target);session.target=targets.get((idx+1+targets.size())%targets.size());renderSignalGui(p,session);}
    @EventHandler public void onSignalGui(InventoryClickEvent e){
        if(!SIGNAL_GUI_TITLE.equals(e.getView().getTitle())||!(e.getWhoClicked() instanceof Player p))return;
        e.setCancelled(true); SignalSession session=signalSessions.computeIfAbsent(p.getUniqueId(),k->new SignalSession()); int slot=e.getRawSlot();
        if(slot==0){cycleSignalTarget(p,session);return;}
        if(slot==7){session.words.clear();renderSignalGui(p,session);return;}
        if(slot==8){
            if(session.words.isEmpty()){p.sendMessage("§c送信する単語を選択してください。");return;}
            if(inEmp(p.getLocation())){p.sendMessage("§cEMPの影響によりSignalを送信できません。");return;}
            sendSignal(p,session.target,session.words.toArray(String[]::new));signalSessions.remove(p.getUniqueId());p.closeInventory();return;
        }
        if(slot>=9&&slot<45){ItemStack x=e.getCurrentItem();if(x==null||x.getType()!=Material.PAPER||!x.hasItemMeta())return;String w=ChatColor.stripColor(x.getItemMeta().getDisplayName());int max=signalMaxWords(p);if(session.words.size()>=max){p.sendMessage("§cこれ以上単語を追加できません。");return;}session.words.add(w);renderSignalGui(p,session);}
    }
    @EventHandler public void onSignalClose(InventoryCloseEvent e){if(e.getPlayer() instanceof Player p&&SIGNAL_GUI_TITLE.equals(e.getView().getTitle()))Bukkit.getScheduler().runTask(this,()->{if(!SIGNAL_GUI_TITLE.equals(p.getOpenInventory().getTitle()))signalSessions.remove(p.getUniqueId());});}

    private void openGmGui(Player gm){
        Inventory inv=Bukkit.createInventory(null,54,GM_GUI_TITLE); int slot=0;
        for(Player target:Bukkit.getOnlinePlayers()){
            if(slot>=45)break; GmPreset pr=gmPresets.get(target.getUniqueId());
            ItemStack head=new ItemStack(Material.PLAYER_HEAD); SkullMeta sm=(SkullMeta)head.getItemMeta(); sm.setOwningPlayer(target);
            sm.setDisplayName("§f"+target.getName());
            sm.setLore(List.of("§7HO: "+(pr==null||pr.ho==null?"§eランダム":"§d"+pr.ho),"§7使命II: "+(pr==null||pr.mission==null?"§eランダム":"§6"+pr.mission),"§7カラー: "+(pr==null||pr.color==null?"§eランダム":"§f"+pr.color),"§aクリックで個別設定"));
            sm.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"GM_PLAYER:"+target.getUniqueId());head.setItemMeta(sm);inv.setItem(slot++,head);
        }
        inv.setItem(45,guiItem(Material.ENDER_EYE,"§e全員ランダム",List.of("§7GM指定をすべて解除")));
        inv.setItem(49,guiItem(Material.LIME_CONCRETE,"§aゲーム開始",List.of("§7現在の指定内容で開始","§7未指定項目はランダム抽選")));
        inv.setItem(53,guiItem(Material.BOOK,"§b設定について",List.of("§7使命Iは全員共通: ゲーム終了まで生存","§7HO/使命II/カラーを個別指定できます")));
        gm.openInventory(inv);
    }
    private void openGmPlayerGui(Player gm,Player target){
        GmPreset pr=gmPresets.computeIfAbsent(target.getUniqueId(),k->new GmPreset());
        Inventory inv=Bukkit.createInventory(null,27,GM_PLAYER_TITLE+target.getName());
        ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta sm=(SkullMeta)head.getItemMeta();sm.setOwningPlayer(target);sm.setDisplayName("§f"+target.getName());head.setItemMeta(sm);inv.setItem(4,head);
        inv.setItem(10,guiItem(Material.ENCHANTED_BOOK,"§dHO: §f"+(pr.ho==null?"ランダム":pr.ho),List.of("§7左クリック: 次へ","§7右クリック: ランダムへ")));
        inv.setItem(13,guiItem(Material.WRITABLE_BOOK,"§6使命II: §f"+(pr.mission==null?"ランダム":pr.mission),List.of("§7左クリック: 次へ","§7右クリック: ランダムへ","§8使命I: ゲーム終了まで生存（固定）")));
        inv.setItem(16,guiItem(Material.LEATHER_HELMET,"§bカラー: §f"+(pr.color==null?"ランダム":pr.color),List.of("§7左クリック: 次へ","§7右クリック: ランダムへ")));
        inv.setItem(18,guiItem(Material.ARROW,"§e一覧へ戻る",List.of()));
        inv.setItem(22,guiItem(Material.BARRIER,"§cこのプレイヤーをリセット",List.of("§7HO・使命II・カラーをランダムへ")));
        gm.openInventory(inv);
    }
    private String nextValue(String current,String[] values){if(current==null)return values[0];for(int i=0;i<values.length;i++)if(values[i].equals(current))return values[(i+1)%values.length];return values[0];}
    @EventHandler public void onGmGui(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player gm)||(!GM_GUI_TITLE.equals(e.getView().getTitle())&&!e.getView().getTitle().startsWith(GM_PLAYER_TITLE)))return;
        e.setCancelled(true);if(running||!gm.hasPermission("osw.admin"))return;
        if(GM_GUI_TITLE.equals(e.getView().getTitle())){
            if(e.getRawSlot()==45){gmPresets.clear();openGmGui(gm);return;}
            if(e.getRawSlot()==49){gm.closeInventory();startGame();return;}
            String sid=id(e.getCurrentItem());if(sid!=null&&sid.startsWith("GM_PLAYER:")){try{Player t=Bukkit.getPlayer(UUID.fromString(sid.substring(10)));if(t!=null)openGmPlayerGui(gm,t);}catch(Exception ignored){} }return;
        }
        String name=ChatColor.stripColor(e.getView().getTitle().substring(GM_PLAYER_TITLE.length()));Player target=Bukkit.getPlayerExact(name);if(target==null){gm.closeInventory();return;}GmPreset pr=gmPresets.computeIfAbsent(target.getUniqueId(),k->new GmPreset());
        boolean right=e.isRightClick();int slot=e.getRawSlot();
        if(slot==10)pr.ho=right?null:nextValue(pr.ho,hos);else if(slot==13)pr.mission=right?null:nextValue(pr.mission,missions);else if(slot==16)pr.color=right?null:nextValue(pr.color,colors);else if(slot==18){openGmGui(gm);return;}else if(slot==22){gmPresets.remove(target.getUniqueId());openGmPlayerGui(gm,target);return;}else return;
        openGmPlayerGui(gm,target);
    }

    private void useBackpack(Player p,ItemStack it){ PlayerData d=data.get(p.getUniqueId()); if(d.backpacks>=getConfig().getInt("backpack-max",3)){p.sendMessage("§cBACKPACK CAPACITY MAX");return;} d.backpacks++; consume(it); unlockSlots(p,d.backpacks*3); p.sendMessage("§aBACKPACK EQUIPPED §7Capacity +3"); }
    private void unlockSlots(Player p,int n){ for(int slot=9;slot<36;slot++){ if(slot-9<n && "LOCK".equals(id(p.getInventory().getItem(slot))))p.getInventory().setItem(slot,null); } }
    private void useSmoke(Player p,ItemStack it){ consume(it); double r=getConfig().getDouble("smoke-radius",5); long until=System.currentTimeMillis()+getConfig().getLong("smoke-seconds",12)*1000; Location c=p.getLocation().clone();smokeZones.add(new SmokeZone(p.getWorld(),c,r,until)); for(int i=0;i<900;i++){double ang=Math.random()*Math.PI*2,rad=Math.sqrt(Math.random())*r;double x=Math.cos(ang)*rad,z=Math.sin(ang)*rad,y=.15+Math.random()*2.8;Location q=c.clone().add(x,y,z);p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,q,1,.18,.12,.18,.005);if(i%4==0)p.getWorld().spawnParticle(Particle.SMOKE_LARGE,q,1,.12,.12,.12,.002);} }
    private void useAid(Player p,ItemStack it){ Player target=p; if("TARGET".equals(aidMode.getOrDefault(p.getUniqueId(),"SELF"))){target=null;for(Entity en:p.getNearbyEntities(3,3,3))if(en instanceof Player q&&p.hasLineOfSight(q)){target=q;break;}if(target==null){p.sendMessage("§cNo target in range.");return;}} double max=Objects.requireNonNull(target.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue(); target.setHealth(Math.min(max,target.getHealth()+6)); healCounts.merge(p.getUniqueId(),1,Integer::sum); checkMission(p,"HEAL_PLAYER"); p.sendMessage("§aFirst Aid: healed "+code(target)); consume(it); }
    private void shootGun(Player p,ItemStack gun){ ItemMeta m=gun.getItemMeta(); int max=getConfig().getInt("handgun-magazine",6);int ammo=m.getPersistentDataContainer().getOrDefault(ammoKey,PersistentDataType.INTEGER,max); if(ammo<=0){p.sendMessage("§cカチッ……弾切れです。");return;} ammo--;m.getPersistentDataContainer().set(ammoKey,PersistentDataType.INTEGER,ammo);m.setDisplayName("§6Handgun §7["+ammo+"/"+max+"]");gun.setItemMeta(m);setDurabilityBar(gun,ammo,max);p.getWorld().playSound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,4f,1.6f); Arrow ar=p.launchProjectile(Arrow.class);ar.setDamage(8);ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);if(ammo==0)Bukkit.getScheduler().runTask(this,()->consume(gun)); }
    private void shootStun(Player p,ItemStack it){ ItemMeta m=it.getItemMeta();int uses=m.getPersistentDataContainer().getOrDefault(usesKey,PersistentDataType.INTEGER,2);if(uses<=0)return;Snowball s=p.launchProjectile(Snowball.class);s.setVelocity(p.getEyeLocation().getDirection().multiply(1.35));s.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"STUN_PROJECTILE");uses--;m.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,uses);m.setDisplayName("§bStun Device §7["+uses+"/2]");it.setItemMeta(m);setDurabilityBar(it,uses,2);if(uses==0)Bukkit.getScheduler().runTask(this,()->consume(it)); }
    @EventHandler public void onProjectileHit(ProjectileHitEvent e){ if(e.getEntity() instanceof Snowball s && "STUN_PROJECTILE".equals(s.getPersistentDataContainer().get(itemKey,PersistentDataType.STRING)) && e.getHitEntity() instanceof Player p){int ticks=getConfig().getInt("stun-seconds",3)*20;p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,ticks,10));p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,ticks,10));p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP,ticks,200));} }

    @EventHandler public void onPickup(EntityPickupItemEvent e){
        if(!running||!(e.getEntity() instanceof Player p))return;
        String sid=id(e.getItem().getItemStack());
        if(sid!=null&&!isBoundItem(e.getItem().getItemStack())&&!"WILL".equals(sid)&&!"LOCK".equals(sid)){
            specialCollectCounts.merge(p.getUniqueId(),1,Integer::sum); checkMission(p,"COLLECT_SPECIAL");
        }
        if("WILL".equals(sid)) willDrops.remove(e.getItem().getUniqueId());
    }
    @EventHandler public void onInventory(InventoryClickEvent e){ if(!running||!(e.getWhoClicked() instanceof Player p))return; if(e.getClickedInventory()==p.getInventory()&&e.getSlot()>=9&&e.getSlot()<36&&"LOCK".equals(id(e.getCurrentItem())))e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e){ if(isBoundItem(e.getItemDrop().getItemStack()))e.setCancelled(true); }

    private void startGame(){
        running=true;meeting=false;breakerOff=false;data.clear();signalCounts.clear();healCounts.clear();bodyFindCounts.clear();killCounts.clear();reportCounts.clear();specialCollectCounts.clear();darknessSince.clear();bodies.values().forEach(Body::remove);bodies.clear();
        remainingGameSeconds=getConfig().getInt("game-duration-seconds",900); timerMark=System.currentTimeMillis();
        if(gameBar!=null){gameBar.removeAll(); for(Player p:Bukkit.getOnlinePlayers())gameBar.addPlayer(p); updateGameBar();}
        List<Player> ps=new ArrayList<>(Bukkit.getOnlinePlayers()); Collections.shuffle(ps); int idx=0; for(Player pl:ps)setupPlayer(pl,idx++);
        for(BotData bot:bots.values()){bot.code=colors[idx%colors.length];bot.colorIndex=idx%colors.length;applyBotLook(bot);idx++;}
        Bukkit.broadcastMessage("§4§lOneSignalWolf START §7- participants: §f"+participantCount());
        checkLastSurvivor();
    }
    private void setupPlayer(Player p,int i){
        GmPreset preset=gmPresets.get(p.getUniqueId());
        String ho=(preset!=null&&preset.ho!=null)?preset.ho:hos[ThreadLocalRandom.current().nextInt(hos.length)];
        String mission=(preset!=null&&preset.mission!=null)?preset.mission:missions[ThreadLocalRandom.current().nextInt(missions.length)];
        int ci=i%colors.length;
        if(preset!=null&&preset.color!=null){for(int n=0;n<colors.length;n++)if(colors[n].equalsIgnoreCase(preset.color)){ci=n;break;}}
        PlayerData d=new PlayerData(colors[ci],ci,ho,mission);data.put(p.getUniqueId(),d);p.getInventory().clear();p.setGameMode(GameMode.SURVIVAL);p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue());
        p.getInventory().setHelmet(coloredHelmet(d.colorIndex));p.getInventory().setItem(0,bound(Material.BOOK,"HO","§dHO: "+d.ho,List.of("§7Mission I: SURVIVE +5pt","§7Mission II: "+d.mission)));
        p.getInventory().setItem(1,bound(Material.COMPASS,"RADIO","§bOneSignal Radio",List.of("§7/osw signal WORD WORD WORD")));
        p.getInventory().setItem(2,bound(Material.WRITABLE_BOOK,"NOTE","§f個人ノート",List.of("§7死亡すると遺書になります")));
        p.getInventory().setItem(3,bound(Material.BELL,"REPORTER","§c通報端末",List.of("§7死体の近くで右クリックして通報")));
        for(int s=9;s<36;s++)p.getInventory().setItem(s,bound(Material.BLACK_STAINED_GLASS_PANE,"LOCK","§8Locked inventory slot",List.of("§7Use a Backpack to unlock")));
        if("GUARD".equals(d.ho))p.getInventory().setItem(4,item("BODY_ARMOR")); if("MEDIC".equals(d.ho))p.getInventory().setItem(4,item("FIRST_AID")); if("TRACKER".equals(d.ho))p.getInventory().setItem(4,item("TRACKER")); if("SIGNALER".equals(d.ho))p.getInventory().setItem(4,item("SIGNAL_BOOSTER")); if("ENGINEER".equals(d.ho))p.getInventory().setItem(4,item("EMERGENCY_BATTERY"));
        Team t=Bukkit.getScoreboardManager().getMainScoreboard().getTeam("osw_hidden_names");if(t!=null)t.addEntry(p.getName());
        p.sendMessage("§fYOU ARE §l"+d.code+" §7| HO: §d"+d.ho+" §7| Mission II: §e"+d.mission);
    }
    private ItemStack coloredHelmet(int i){ItemStack x=new ItemStack(Material.LEATHER_HELMET);LeatherArmorMeta m=(LeatherArmorMeta)x.getItemMeta();m.setColor(leatherColors[i%leatherColors.length]);m.setUnbreakable(true);m.setDisplayName("§fCode Helmet: "+colors[i%colors.length]);x.setItemMeta(m);return x;}

    private void sendSignal(Player p,String[] words){sendSignal(p,"ALL",words);}
    private void sendSignal(Player p,String target,String[] words){
        if(!running||meeting||inEmp(p.getLocation())){p.sendMessage("§cSignalを送信できません。");return;}
        int max=signalMaxWords(p);if(words.length>max){p.sendMessage("§c単語数が上限を超えています。最大: "+max);return;}
        signalCounts.merge(p.getUniqueId(),1,Integer::sum);checkMission(p,"SEND_3_SIGNALS");
        String msg="§b[SIGNAL] §f"+code(p)+" §7→ §f"+target+" §7: §f"+String.join(" / ",words).toUpperCase(Locale.ROOT);
        int delivered=0;
        for(Player q:Bukkit.getOnlinePlayers()){
            if(!data.containsKey(q.getUniqueId())||inEmp(q.getLocation()))continue;
            if("ALL".equalsIgnoreCase(target)||code(q).equalsIgnoreCase(target)){q.sendMessage(msg);delivered++;}
        }
        p.sendActionBar("§bSignal送信 §7→ §f"+target+" §7(受信: "+delivered+")");
    }
    private boolean inEmp(Location l){long n=System.currentTimeMillis();return empZones.stream().anyMatch(e->e.until>n&&e.world.equals(l.getWorld())&&e.center.distanceSquared(l)<=e.radius*e.radius);}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0){sendHelp(sender);return true;}
        String sub=args[0].toLowerCase(Locale.ROOT); Player p=sender instanceof Player x?x:null;
        if(sub.equals("help")){sendHelp(sender);return true;}
        if(sub.equals("meetingjoin")&&p!=null&&args.length>=2){chooseMeeting(p,args[1].equalsIgnoreCase("yes")||args[1].equalsIgnoreCase("join"));return true;}
        if(sub.equals("voteopen")&&p!=null&&meeting&&!meetingChoicePhase&&meetingAttendees.contains(p.getUniqueId())){openVoteGui(p);return true;}
        if(sub.equals("tool")&&admin(sender)){if(p==null)return true;if(args.length<2){p.sendMessage("§e/osw tool light | lightblock | indoor");return true;}String q=args[1].toLowerCase(Locale.ROOT);p.getInventory().addItem(q.equals("indoor")?item("INDOOR_TOOL"):q.equals("lightblock")?item("LIGHT_BLOCK"):item("LIGHT_TOOL"));return true;}
        if(sub.equals("indoor")&&admin(sender)){if(p==null)return true;return handleIndoor(p,args);}
        if(sub.equals("blizzard")&&admin(sender)){if(args.length<2){sender.sendMessage("§eBlizzard: "+(blizzard?"ON":"OFF"));return true;}if(args[1].equalsIgnoreCase("on")||args[1].equalsIgnoreCase("start"))startBlizzard();else stopBlizzard();return true;}
        if(sub.equals("setup")){if(!admin(sender))return true;if(p==null){sender.sendMessage("§cRun setup commands in-game.");return true;}return handleSetup(p,args);}
        if(sub.equals("gm")&&admin(sender)){if(p==null){sender.sendMessage("§cゲーム内で実行してください。");return true;}if(running){p.sendMessage("§cゲーム開始後はGM設定を変更できません。");return true;}if(args.length>=2&&args[1].equalsIgnoreCase("reset")){gmPresets.clear();p.sendMessage("§aGM指定をすべてランダムに戻しました。");return true;}openGmGui(p);return true;}
        if(sub.equals("start")&&admin(sender)){startGame();return true;} if(sub.equals("stop")&&admin(sender)){resetGame();sender.sendMessage("Stopped and reset.");return true;}
        if(sub.equals("testmode")&&admin(sender)){ if(args.length<2){sender.sendMessage("§eTEST MODE: "+(testMode?"ON":"OFF"));return true;} testMode=args[1].equalsIgnoreCase("on"); if(!testMode)removeAllBots(); sender.sendMessage("§eTEST MODE: "+(testMode?"ON":"OFF")); return true; }
        if(sub.equals("test")&&admin(sender)&&args.length>=3&&args[1].equalsIgnoreCase("start")){int target=Math.max(1,Integer.parseInt(args[2]));testMode=true;removeAllBots();int humans=Bukkit.getOnlinePlayers().size();spawnBots(Math.max(0,target-humans),p!=null?p.getLocation():Bukkit.getWorlds().get(0).getSpawnLocation());startGame();return true;}
        if(sub.equals("bot")&&admin(sender)){return handleBotCommand(sender,p,args);}
        if(p==null)return true;
        if(sub.equals("ho")){PlayerData d=data.get(p.getUniqueId());if(d!=null)p.sendMessage("§f"+d.code+" §7HO=§d"+d.ho+" §7Mission I=SURVIVE(+5) Mission II=§e"+d.mission+" §7Mission II Status="+(d.missionDone?"§aDONE":"§cPENDING")+" §7Points=§a"+d.points);return true;}
        if(sub.equals("point")){PlayerData d=data.get(p.getUniqueId());if(d!=null)p.sendMessage("§aPOINTS: "+d.points);return true;}
        if(sub.equals("signal")&&args.length>=2){sendSignal(p,Arrays.copyOfRange(args,1,args.length));return true;}
        if(sub.equals("breaker")&&admin(sender)){breakerOff=!breakerOff;if(breakerOff)breakerOffSince=System.currentTimeMillis();else breakerOffSince=0L;setRegisteredLights(!breakerOff);if(!breakerOff)for(Player q:Bukkit.getOnlinePlayers()){q.removePotionEffect(PotionEffectType.DARKNESS);q.removePotionEffect(PotionEffectType.NIGHT_VISION);}Bukkit.broadcastMessage(breakerOff?"§4POWER DOWN":"§aPOWER RESTORED");return true;}
        if(sub.equals("emp")&&admin(sender)){empZones.add(new EmpZone(p.getWorld(),p.getLocation(),getConfig().getDouble("emp-radius",30),System.currentTimeMillis()+getConfig().getLong("emp-seconds",60)*1000));p.sendMessage("§5EMP activated.");return true;}
        if(sub.equals("meeting")&&admin(sender)){startMeeting();return true;}
        if(sub.equals("vote")&&args.length>=2&&meeting){Player t=findByCode(args[1]);if(t!=null){votes.put(p.getUniqueId(),t.getUniqueId());voteSubmitted.add(p.getUniqueId());p.sendMessage("§a投票: "+code(t));}return true;}
        if(sub.equals("report")){Body b=nearestBody(p,4);if(b!=null)startReport(p,b);else p.sendMessage("§7近くに通報できる死体がありません。");return true;}
        if(sub.equals("item")&&admin(sender)&&args.length>=2){p.getInventory().addItem(item(args[1].toUpperCase(Locale.ROOT)));return true;}
        if(sub.equals("end")&&admin(sender)){endGame();return true;}
        return true;
    }
    private void checkMission(Player p,String event){
        PlayerData d=data.get(p.getUniqueId()); if(d==null||d.missionDone||!d.mission.equals(event))return;
        boolean ok=switch(event){
            case "SEND_3_SIGNALS" -> signalCounts.getOrDefault(p.getUniqueId(),0)>=3;
            case "HEAL_PLAYER" -> healCounts.getOrDefault(p.getUniqueId(),0)>=1;
            case "FIND_BODY" -> bodyFindCounts.getOrDefault(p.getUniqueId(),0)>=1;
            case "KILL_PLAYER" -> killCounts.getOrDefault(p.getUniqueId(),0)>=1;
            case "REPORT_BODY" -> reportCounts.getOrDefault(p.getUniqueId(),0)>=1;
            case "COLLECT_SPECIAL" -> specialCollectCounts.getOrDefault(p.getUniqueId(),0)>=1;
            case "USE_BREAKER","USE_EMP" -> true;
            case "SURVIVE_DARKNESS" -> darknessSince.containsKey(p.getUniqueId()) && System.currentTimeMillis()-darknessSince.get(p.getUniqueId())>=60000;
            default -> false;
        };
        if(ok){d.missionDone=true;d.points+=getConfig().getInt("mission-ii-points",4);p.sendMessage("§6MISSION II COMPLETE §a+"+getConfig().getInt("mission-ii-points",4)+"pt");}
    }
    private void expireWill(UUID entityId){ Item it=willDrops.remove(entityId); if(it!=null&&it.isValid())it.remove(); }
    private void processWillExpiry(){ willDrops.entrySet().removeIf(en->!en.getValue().isValid()); }
    private void resetGame(){restorePlayerVisibility();running=false;blizzard=false;temperatures.clear();meeting=false;meetingChoicePhase=false;meetingAttendees.clear();meetingRefused.clear();meetingOrigins.clear();if(breakerOff)setRegisteredLights(true);breakerOff=false;executionReady=false;finalWinner=null;remainingGameSeconds=getConfig().getInt("game-duration-seconds",900);timerMark=0L;if(gameBar!=null)gameBar.removeAll();empZones.clear();smokeZones.clear();reports.clear();votes.clear();for(Body b:bodies.values())b.remove();bodies.clear();for(UUID id:new HashSet<>(wildlife)){Entity en=Bukkit.getEntity(id);if(en!=null)en.remove();}wildlife.clear();for(Player p:Bukkit.getOnlinePlayers()){p.setInvulnerable(false);p.removePotionEffect(PotionEffectType.DARKNESS);p.removePotionEffect(PotionEffectType.NIGHT_VISION);p.getInventory().clear();p.getInventory().setHelmet(null);p.clearTitle();clearFlashlightLights(p);visionHudState.remove(p.getUniqueId());if(p.getGameMode()==GameMode.SPECTATOR)p.setGameMode(GameMode.SURVIVAL);}data.clear();tags.values().forEach(Entity::remove);tags.clear();removeAllBots();}
    private void endGame(){
        if(!running)return;
        int aliveHumans=aliveHumanCount(), aliveBots=aliveBotCount();
        boolean all=(aliveHumans+aliveBots)==participantCount();
        for(Player p:Bukkit.getOnlinePlayers())if(data.containsKey(p.getUniqueId())&&p.getGameMode()!=GameMode.SPECTATOR&&!p.isDead()){
            PlayerData d=data.get(p.getUniqueId());if(!d.survivalAwarded){d.points+=5;d.survivalAwarded=true;}
        }
        if(all){finishAllSurvived();return;}
        Player winner=data.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue().points,a.getValue().points)).map(e->Bukkit.getPlayer(e.getKey())).filter(Objects::nonNull).findFirst().orElse(null);
        if(winner!=null)Bukkit.broadcastMessage("§6§l勝者 — "+code(winner)+" §f| §f"+winner.getName()+" §7("+data.get(winner.getUniqueId()).points+"pt)");
        if(winner!=null){finalWinner=winner.getUniqueId();executionReady=true;prepareExecution(winner);Bukkit.broadcastMessage("§4最終処刑の準備が完了しました。 §7"+code(winner)+" が処刑スイッチを使用できます。");}
        running=false;if(gameBar!=null)gameBar.setVisible(false);
    }
    private void finishAllSurvived(){
        running=false;executionReady=false;finalWinner=null;if(gameBar!=null)gameBar.setVisible(false);
        int max=data.values().stream().mapToInt(d->d.points).max().orElse(0);
        Set<UUID> mvps=new HashSet<>();for(Map.Entry<UUID,PlayerData> en:data.entrySet())if(en.getValue().points==max)mvps.add(en.getKey());
        Bukkit.broadcastMessage("§a§l全員生存 §f- 全員が生還した");
        for(Player p:Bukkit.getOnlinePlayers()){
            p.setInvulnerable(true);
            p.playSound(p.getLocation(),Sound.ENTITY_ENDER_DRAGON_FLAP,1.8f,.65f);
            p.sendTitle("§a§l全員生存","§f全員が生還した",10,60,15);
        }
        Bukkit.getScheduler().runTaskLater(this,()->forEachParticipantSound(Sound.UI_TOAST_CHALLENGE_COMPLETE,1.2f,1.0f),35L);
        Bukkit.getScheduler().runTaskLater(this,()->{
            resetGame();Location lobby=lobbyLocation();
            for(Player p:Bukkit.getOnlinePlayers()){p.teleport(lobby);p.setInvulnerable(false);}
            for(UUID id:mvps){Player p=Bukkit.getPlayer(id);if(p!=null){p.getInventory().setHelmet(makeCrown());p.sendMessage("§6§l最高得点者 §f"+max+"pt §7- 次のゲーム開始まで王冠が与えられます。");}}
        },100L);
    }
    private void forEachParticipantSound(Sound sound,float volume,float pitch){for(Player p:Bukkit.getOnlinePlayers())p.playSound(p.getLocation(),sound,volume,pitch);}
    private ItemStack makeCrown(){ItemStack x=new ItemStack(Material.GOLDEN_HELMET);ItemMeta m=x.getItemMeta();m.setDisplayName("§6§l王冠");m.setLore(List.of("§7全員生存 - 最高得点者"));m.setUnbreakable(true);x.setItemMeta(m);return x;}
    private ItemStack makeLastSurvivorCrown(){ItemStack x=new ItemStack(Material.LEATHER_HELMET);LeatherArmorMeta m=(LeatherArmorMeta)x.getItemMeta();m.setColor(Color.fromRGB(190,20,20));m.setDisplayName("§4§l赤の王冠");m.setLore(List.of("§7最後の生存者"));m.setUnbreakable(true);x.setItemMeta(m);return x;}
    private void prepareExecution(Player winner){
        Location wl=parseLoc(mapCfg.getString("winner"));if(wl!=null)winner.teleport(wl);int i=1;
        for(Player p:Bukkit.getOnlinePlayers())if(data.containsKey(p.getUniqueId())&&!p.equals(winner)&&p.getGameMode()!=GameMode.SPECTATOR&&!p.isDead()){Location c=parseLoc(mapCfg.getString("cages."+i));if(c==null)c=parseLoc(mapCfg.getString("cages.1"));if(c!=null)p.teleport(c);p.setInvulnerable(true);i++;}
    }
    private void executeFinal(Player p){if(!executionReady||finalWinner==null||!p.getUniqueId().equals(finalWinner)){p.sendMessage("§cOnly the winner can use this switch.");return;}executionReady=false;Bukkit.broadcastMessage("§4§lEXECUTION");for(Player q:Bukkit.getOnlinePlayers())if(data.containsKey(q.getUniqueId())&&!q.getUniqueId().equals(finalWinner)&&q.getGameMode()!=GameMode.SPECTATOR){q.setInvulnerable(false);q.setHealth(0.0);}}


    private void tickGameTimer(long now){
        if(!running){timerMark=now;return;}
        if(meeting){timerMark=now;updateGameBar();return;}
        if(timerMark==0L){timerMark=now;return;}
        long elapsed=(now-timerMark)/1000L;
        if(elapsed<=0)return;
        remainingGameSeconds-= (int)elapsed; timerMark+=elapsed*1000L; updateGameBar();
        if(remainingGameSeconds<=0){remainingGameSeconds=0;endGame();}
    }
    private void updateGameBar(){
        if(gameBar==null)return; int total=Math.max(1,getConfig().getInt("game-duration-seconds",900));
        int m=Math.max(0,remainingGameSeconds)/60,s=Math.max(0,remainingGameSeconds)%60;
        gameBar.setTitle("§f終了まで §c"+String.format("%02d:%02d",m,s)+(meeting?" §e[会議中・停止]":""));
        gameBar.setProgress(Math.max(0.0,Math.min(1.0,remainingGameSeconds/(double)total)));
        gameBar.setVisible(running);
    }
    private int aliveHumanCount(){return (int)Bukkit.getOnlinePlayers().stream().filter(p->data.containsKey(p.getUniqueId())&&p.getGameMode()!=GameMode.SPECTATOR&&!p.isDead()).count();}
    private int aliveBotCount(){return (int)bots.values().stream().filter(BotData::alive).count();}
    private int participantCount(){return data.size()+bots.size();}
    private void checkLastSurvivor(){
        if(!running||meeting)return; int alive=aliveHumanCount()+aliveBotCount(); if(alive!=1)return;
        running=false;if(gameBar!=null)gameBar.setVisible(false);executionReady=false;
        Player hp=Bukkit.getOnlinePlayers().stream().filter(p->data.containsKey(p.getUniqueId())&&p.getGameMode()!=GameMode.SPECTATOR&&!p.isDead()).findFirst().orElse(null);
        BotData bp=bots.values().stream().filter(BotData::alive).findFirst().orElse(null);
        String who=hp!=null?(code(hp)+" | "+hp.getName()):(bp!=null?(bp.code+" | "+bp.id):"不明");
        finalWinner=hp==null?null:hp.getUniqueId();
        for(Player p:Bukkit.getOnlinePlayers()){
            p.setInvulnerable(true);p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,20*6,10,false,false,false));
            p.playSound(p.getLocation(),Sound.ENTITY_PILLAGER_CELEBRATE,1.5f,.7f);
        }
        Bukkit.getScheduler().runTaskLater(this,()->{for(Player p:Bukkit.getOnlinePlayers())p.sendTitle("§6§l"+who+"の勝利","§4最後の生存者",5,55,10);},20L);
        Bukkit.getScheduler().runTaskLater(this,()->{UUID winnerId=finalWinner;resetGame();Location lobby=lobbyLocation();for(Player p:Bukkit.getOnlinePlayers()){p.teleport(lobby);p.removePotionEffect(PotionEffectType.BLINDNESS);p.setInvulnerable(false);}if(winnerId!=null){Player winner=Bukkit.getPlayer(winnerId);if(winner!=null){winner.getInventory().setHelmet(makeLastSurvivorCrown());winner.sendMessage("§4§l最後の生存者 §7- 次のゲーム開始まで赤の王冠が与えられます。");}}},100L);
    }
    private void spawnBots(int count,Location base){
        for(int i=0;i<count;i++){
            Location at=base.clone().add((i%4)*1.5,0,(i/4)*1.5);
            Zombie z=at.getWorld().spawn(at,Zombie.class);z.setAI(false);z.setSilent(true);z.setCanPickupItems(false);z.setRemoveWhenFarAway(false);z.setAdult();z.setCustomNameVisible(false);
            BotData bd=new BotData(z,"BOT-"+(bots.size()+1));bots.put(z.getUniqueId(),bd);applyBotLook(bd);
        }
    }
    private void applyBotLook(BotData b){if(b.entity==null||!b.entity.isValid())return; if(b.code==null)b.code="BOT";b.entity.setCustomName(b.code);if(b.entity.getEquipment()!=null)b.entity.getEquipment().setHelmet(coloredHelmet(b.colorIndex));}
    private void removeAllBots(){for(BotData b:new ArrayList<>(bots.values()))if(b.entity!=null&&b.entity.isValid())b.entity.remove();bots.clear();}
    private BotData findBot(String key){for(BotData b:bots.values())if(b.id.equalsIgnoreCase(key)||b.code.equalsIgnoreCase(key)||b.entity.getUniqueId().toString().startsWith(key))return b;return null;}
    private boolean handleBotCommand(CommandSender sender,Player p,String[] args){
        if(args.length<2){sender.sendMessage("§e/osw bot add [n] | remove [n|all] | list | kill <id> | tp <id>");return true;}
        String a=args[1].toLowerCase(Locale.ROOT);
        if(a.equals("add")){int n=args.length>=3?Integer.parseInt(args[2]):1;Location l=p!=null?p.getLocation():Bukkit.getWorlds().get(0).getSpawnLocation();spawnBots(Math.max(1,n),l);sender.sendMessage("§aBots: "+bots.size());return true;}
        if(a.equals("remove")){if(args.length>=3&&args[2].equalsIgnoreCase("all")){removeAllBots();}else{int n=args.length>=3?Integer.parseInt(args[2]):1;for(BotData b:new ArrayList<>(bots.values()).subList(0,Math.min(n,bots.size()))){if(b.entity.isValid())b.entity.remove();bots.remove(b.entity.getUniqueId());}}sender.sendMessage("§aBots: "+bots.size());checkLastSurvivor();return true;}
        if(a.equals("list")){sender.sendMessage("§eBots (alive "+aliveBotCount()+"/"+bots.size()+")");for(BotData b:bots.values())sender.sendMessage("§7- "+b.id+" §f"+b.code+" §7"+(b.alive()?"ALIVE":"DEAD"));return true;}
        if((a.equals("kill")||a.equals("tp"))&&args.length>=3){BotData b=findBot(args[2]);if(b==null){sender.sendMessage("§cBot not found.");return true;}if(a.equals("kill")){if(b.entity.isValid())b.entity.setHealth(0);else b.dead=true;}else if(p!=null&&b.entity.isValid())b.entity.teleport(p.getLocation());return true;}
        return true;
    }
    private void castBotVotes(){
        List<UUID> humanTargets=Bukkit.getOnlinePlayers().stream().filter(p->data.containsKey(p.getUniqueId())&&p.getGameMode()!=GameMode.SPECTATOR&&!p.isDead()).map(Player::getUniqueId).toList();
        if(humanTargets.isEmpty())return; for(BotData b:bots.values())if(b.alive()&&ThreadLocalRandom.current().nextDouble()<0.8)votes.put(b.entity.getUniqueId(),humanTargets.get(ThreadLocalRandom.current().nextInt(humanTargets.size())));
    }
    @EventHandler public void onBotDeath(EntityDeathEvent e){
        BotData b=bots.get(e.getEntity().getUniqueId());if(b==null)return;Location dl=e.getEntity().getLocation().clone();b.dead=true;e.getDrops().clear();e.setDroppedExp(0);spawnBotBody(b,dl);Bukkit.getScheduler().runTask(this,this::checkLastSurvivor);
    }

    private boolean handleSetup(Player p,String[] args){
        if(args.length<2){p.sendMessage("§e/osw setup breaker | emp [radius] [seconds] | wildlife <id> | meeting | cage <n> | winner | execution-switch | list | remove <type> [id] | tp <type> [id] | test <type>");return true;}
        String t=args[1].toLowerCase(Locale.ROOT);
        if(t.equals("light-scan")){int r=args.length>=3?Math.max(1,Math.min(64,Integer.parseInt(args[2]))):20;int n=scanLights(p,r);p.sendMessage("§a光源を一括登録しました: "+n+"個");return true;}
        if(t.equals("light")){Block b=targetBlock(p);if(b==null){p.sendMessage("§c停電対象の光源ブロックを見てください。");return true;}addConfiguredBlock("lights",b);p.sendMessage("§a停電対象の光源を登録しました。");return true;}
        if(t.equals("charger")){Block b=targetBlock(p);if(b==null){p.sendMessage("§c充電施設にするブロックを見てください。");return true;}addConfiguredBlock("chargers",b);p.sendMessage("§a充電施設を登録しました。");return true;}
        if(t.equals("breaker")){Block b=targetBlock(p);if(b==null){p.sendMessage("§cLook at the breaker lever/button.");return true;}addConfiguredBlock("breakers",b);p.spawnParticle(Particle.VILLAGER_HAPPY,b.getLocation().add(.5,.5,.5),20,.3,.3,.3);p.sendMessage("§aBreaker registered: "+blockKey(b));return true;}
        if(t.equals("emp")){Block b=targetBlock(p);if(b==null){p.sendMessage("§cLook at the EMP block.");return true;}addConfiguredBlock("emps",b);double r=args.length>=3?Double.parseDouble(args[2]):getConfig().getDouble("emp-radius",30);long sec=args.length>=4?Long.parseLong(args[3]):getConfig().getLong("emp-seconds",60);mapCfg.set("emp-settings."+blockKey(b)+".radius",r);mapCfg.set("emp-settings."+blockKey(b)+".seconds",sec);saveMapConfig();p.sendMessage("§aEMP registered: radius="+r+" duration="+sec+"s");return true;}
        if(t.equals("wildlife")&&args.length>=3){mapCfg.set("wildlife."+args[2],locString(p.getLocation()));saveMapConfig();p.sendMessage("§aWildlife spawn registered: "+args[2]);return true;}
        if(t.equals("meeting")){mapCfg.set("meeting",locString(p.getLocation()));saveMapConfig();p.sendMessage("§aMeeting location registered.");return true;}
        if(t.equals("cage")&&args.length>=3){mapCfg.set("cages."+args[2],locString(p.getLocation()));saveMapConfig();p.sendMessage("§aCage position "+args[2]+" registered.");return true;}
        if(t.equals("winner")){mapCfg.set("winner",locString(p.getLocation()));saveMapConfig();p.sendMessage("§aWinner position registered.");return true;}
        if(t.equals("execution-switch")){Block b=targetBlock(p);if(b==null){p.sendMessage("§cLook at the execution switch.");return true;}mapCfg.set("execution-switch",blockKey(b));saveMapConfig();p.sendMessage("§aExecution switch registered: "+blockKey(b));return true;}
        if(t.equals("list")){p.sendMessage("§e--- OneSignalWolf map.yml ---");p.sendMessage("§7Breakers: §f"+mapCfg.getStringList("breakers").size()+" §7EMPs: §f"+mapCfg.getStringList("emps").size()+" §7Wildlife: §f"+(mapCfg.getConfigurationSection("wildlife")==null?0:mapCfg.getConfigurationSection("wildlife").getKeys(false).size()));p.sendMessage("§7Meeting: §f"+(mapCfg.contains("meeting")?"SET":"NOT SET")+" §7Winner: §f"+(mapCfg.contains("winner")?"SET":"NOT SET")+" §7Execution switch: §f"+(mapCfg.contains("execution-switch")?"SET":"NOT SET"));return true;}
        if(t.equals("tp")&&args.length>=3){Location l=null;String id=args.length>=4?args[3]:"1";if(args[2].equalsIgnoreCase("meeting"))l=parseLoc(mapCfg.getString("meeting"));else if(args[2].equalsIgnoreCase("winner"))l=parseLoc(mapCfg.getString("winner"));else if(args[2].equalsIgnoreCase("cage"))l=parseLoc(mapCfg.getString("cages."+id));else if(args[2].equalsIgnoreCase("wildlife"))l=parseLoc(mapCfg.getString("wildlife."+id));if(l!=null)p.teleport(l);else p.sendMessage("§cLocation not found.");return true;}
        if(t.equals("remove")&&args.length>=3){String type=args[2].toLowerCase(Locale.ROOT);String id=args.length>=4?args[3]:null;if(type.equals("meeting")||type.equals("winner")||type.equals("execution-switch"))mapCfg.set(type,null);else if(type.equals("cage")&&id!=null)mapCfg.set("cages."+id,null);else if(type.equals("wildlife")&&id!=null)mapCfg.set("wildlife."+id,null);else{p.sendMessage("§cFor breaker/emp, look at the registered block and use: /osw setup remove-look "+type);return true;}saveMapConfig();p.sendMessage("§aRemoved.");return true;}
        if(t.equals("remove-look")&&args.length>=3){Block b=targetBlock(p);if(b==null)return true;String path=args[2].equalsIgnoreCase("emp")?"emps":"breakers";List<String> list=new ArrayList<>(mapCfg.getStringList(path));list.remove(blockKey(b));mapCfg.set(path,list);saveMapConfig();p.sendMessage("§aRemoved "+blockKey(b));return true;}
        if(t.equals("test")&&args.length>=3){String type=args[2].toLowerCase(Locale.ROOT);if(type.equals("meeting")){p.teleport(meetingLocation());return true;}if(type.equals("emp")){empZones.add(new EmpZone(p.getWorld(),p.getLocation(),getConfig().getDouble("emp-radius",30),System.currentTimeMillis()+10000));p.sendMessage("§5Test EMP: 10s");return true;}if(type.equals("breaker")){breakerOff=!breakerOff;p.sendMessage("§eBreaker test: "+(breakerOff?"OFF":"ON"));return true;}if(type.equals("wildlife")){String id=args.length>=4?args[3]:null;Location l=id==null?null:parseLoc(mapCfg.getString("wildlife."+id));if(l!=null){Wolf w=l.getWorld().spawn(l,Wolf.class);w.setAngry(true);p.sendMessage("§aTest wildlife spawned at "+id);}else p.sendMessage("§cUse /osw setup test wildlife <id>");return true;}if(type.equals("execution")){Player oldWinner=finalWinner==null?null:Bukkit.getPlayer(finalWinner);finalWinner=p.getUniqueId();executionReady=true;prepareExecution(p);p.sendMessage("§4Execution test ready. Use the configured switch.");return true;}}
        p.sendMessage("§cUnknown setup command.");return true;
    }

    private boolean isIndoor(Location l){
        if(l==null||l.getWorld()==null||mapCfg.getConfigurationSection("indoors")==null)return false;
        for(String name:mapCfg.getConfigurationSection("indoors").getKeys(false)){
            Location a=parseLoc(mapCfg.getString("indoors."+name+".a")),b=parseLoc(mapCfg.getString("indoors."+name+".b"));
            if(a==null||b==null||!a.getWorld().equals(l.getWorld()))continue;
            if(l.getX()>=Math.min(a.getX(),b.getX())&&l.getX()<=Math.max(a.getX(),b.getX())+1&&l.getY()>=Math.min(a.getY(),b.getY())&&l.getY()<=Math.max(a.getY(),b.getY())+1&&l.getZ()>=Math.min(a.getZ(),b.getZ())&&l.getZ()<=Math.max(a.getZ(),b.getZ())+1)return true;
        }return false;
    }
    private void tickBlizzard(long now){
        if(!running)return;
        if(blizzard){if(now>=blizzardUntil)stopBlizzard();else for(Player p:Bukkit.getOnlinePlayers())if(data.containsKey(p.getUniqueId())&&!isIndoor(p.getLocation()))p.spawnParticle(Particle.SNOWFLAKE,p.getLocation().add(0,2,0),35,4,2,4,.03);return;}
        if(now>=nextBlizzardAt)startBlizzard();
    }
    private void startBlizzard(){blizzard=true;long now=System.currentTimeMillis();blizzardUntil=now+getConfig().getLong("blizzard.duration-seconds",90)*1000L;Bukkit.broadcastMessage("§b§l吹雪が発生した。 §7屋外の視界と体温に注意してください。");}
    private void stopBlizzard(){blizzard=false;long min=getConfig().getLong("blizzard.min-interval-seconds",180),max=getConfig().getLong("blizzard.max-interval-seconds",300);nextBlizzardAt=System.currentTimeMillis()+ThreadLocalRandom.current().nextLong(Math.max(1,min),Math.max(min+1,max+1))*1000L;Bukkit.broadcastMessage("§7吹雪が弱まった。");}
    private void tickTemperature(Player p,long now){
        double t=temperatures.getOrDefault(p.getUniqueId(),100.0), delta=0;
        boolean indoor=isIndoor(p.getLocation());
        if(indoor&&!breakerOff)delta=getConfig().getDouble("temperature.indoor-recovery-per-second",1.0)/5.0;
        else {
            double loss=(blizzard&&!indoor)?getConfig().getDouble("temperature.blizzard-loss-per-second",1.0):getConfig().getDouble("temperature.cold-loss-per-second",0.35);
            if(indoor&&breakerOff){double elapsed=Math.max(0,(now-breakerOffSince)/1000.0);double grace=getConfig().getDouble("temperature.poweroff-grace-seconds",30.0),cool=getConfig().getDouble("temperature.poweroff-cooldown-seconds",90.0);double factor=Math.max(0,Math.min(1,(elapsed-grace)/Math.max(1,cool)));loss*=factor;}
            if(hasPassive(p,"NECK_WARMER"))loss*=0.60;delta=-loss/5.0;
        }
        t=Math.max(0,Math.min(100,t+delta));temperatures.put(p.getUniqueId(),t);
        int freezeAt=getConfig().getInt("temperature.freezing-threshold",15);if(t<=freezeAt){p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(),p.getFreezeTicks()+8));if(now%2000<250)p.damage(getConfig().getDouble("temperature.freezing-damage",1.0));}else p.setFreezeTicks(Math.max(0,p.getFreezeTicks()-8));
        if(t<=60)p.sendActionBar("§b体温 §f"+(int)Math.round(t)+"%"+(t<=freezeAt?" §c凍え状態":""));
    }
    private boolean handleIndoor(Player p,String[] args){
        if(args.length<2){p.sendMessage("§e/osw indoor add <名前> | remove <名前> | list | show | info");return true;}String q=args[1].toLowerCase(Locale.ROOT);
        if(q.equals("add")&&args.length>=3){Location a=indoorPosA.get(p.getUniqueId()),b=indoorPosB.get(p.getUniqueId());if(a==null||b==null){p.sendMessage("§c屋内範囲設定ツールで地点A/Bを選択してください。");return true;}String n=args[2];mapCfg.set("indoors."+n+".a",locString(a));mapCfg.set("indoors."+n+".b",locString(b));saveMapConfig();p.sendMessage("§a屋内区画「"+n+"」を登録しました。");return true;}
        if(q.equals("remove")&&args.length>=3){mapCfg.set("indoors."+args[2],null);saveMapConfig();p.sendMessage("§a屋内区画を削除しました。");return true;}
        if(q.equals("list")){p.sendMessage("§e屋内区画: §f"+(mapCfg.getConfigurationSection("indoors")==null?"なし":String.join(", ",mapCfg.getConfigurationSection("indoors").getKeys(false))));return true;}
        if(q.equals("info")){p.sendMessage("§e現在地: "+(isIndoor(p.getLocation())?"§a屋内":"§b屋外"));return true;}
        if(q.equals("show")){if(mapCfg.getConfigurationSection("indoors")!=null)for(String n:mapCfg.getConfigurationSection("indoors").getKeys(false)){Location a=parseLoc(mapCfg.getString("indoors."+n+".a")),b=parseLoc(mapCfg.getString("indoors."+n+".b"));if(a!=null&&b!=null)showCuboid(p,a,b);}return true;}return true;
    }
    private void showCuboid(Player p,Location a,Location b){World w=a.getWorld();if(w==null||!w.equals(p.getWorld()))return;double minX=Math.min(a.getX(),b.getX()),maxX=Math.max(a.getX(),b.getX())+1,minY=Math.min(a.getY(),b.getY()),maxY=Math.max(a.getY(),b.getY())+1,minZ=Math.min(a.getZ(),b.getZ()),maxZ=Math.max(a.getZ(),b.getZ())+1;for(double x=minX;x<=maxX;x+=1)for(double y:minmax(minY,maxY))for(double z:minmax(minZ,maxZ))p.spawnParticle(Particle.VILLAGER_HAPPY,new Location(w,x,y,z),1,0,0,0,0);}
    private double[] minmax(double a,double b){return new double[]{a,b};}
    private int scanLights(Player p,int r){Set<Material> types=EnumSet.of(Material.TORCH,Material.WALL_TORCH,Material.LANTERN,Material.SOUL_LANTERN,Material.GLOWSTONE,Material.SEA_LANTERN,Material.SHROOMLIGHT,Material.REDSTONE_LAMP,Material.END_ROD,Material.OCHRE_FROGLIGHT,Material.VERDANT_FROGLIGHT,Material.PEARLESCENT_FROGLIGHT);int n=0;Location c=p.getLocation();for(int x=-r;x<=r;x++)for(int y=-Math.min(r,16);y<=Math.min(r,16);y++)for(int z=-r;z<=r;z++){Block b=c.getWorld().getBlockAt(c.getBlockX()+x,c.getBlockY()+y,c.getBlockZ()+z);if(types.contains(b.getType())){int before=mapCfg.getStringList("lights").size();addConfiguredBlock("lights",b);if(mapCfg.getStringList("lights").size()>before)n++;}}return n;}
    @EventHandler public void onToolInteract(PlayerInteractEvent e){
        if(!e.getPlayer().hasPermission("osw.admin"))return;String i=id(e.getPlayer().getInventory().getItemInMainHand());if(i==null)return;
        if("LIGHT_TOOL".equals(i)&&e.getClickedBlock()!=null&&(e.getAction()==Action.RIGHT_CLICK_BLOCK||e.getAction()==Action.LEFT_CLICK_BLOCK)){e.setCancelled(true);Block b=e.getClickedBlock();List<String> l=new ArrayList<>(mapCfg.getStringList("lights"));String k=blockKey(b);if(l.remove(k))e.getPlayer().sendMessage("§c光源登録を解除: "+k);else{l.add(k);e.getPlayer().sendMessage("§a光源登録: "+k);}mapCfg.set("lights",l);saveMapConfig();return;}
        if("INDOOR_TOOL".equals(i)&&e.getClickedBlock()!=null){e.setCancelled(true);if(e.getAction()==Action.LEFT_CLICK_BLOCK){indoorPosA.put(e.getPlayer().getUniqueId(),e.getClickedBlock().getLocation());e.getPlayer().sendMessage("§b屋内 地点Aを設定");}else if(e.getAction()==Action.RIGHT_CLICK_BLOCK){indoorPosB.put(e.getPlayer().getUniqueId(),e.getClickedBlock().getLocation());e.getPlayer().sendMessage("§b屋内 地点Bを設定");}}
    }
    @EventHandler public void onLightBlockPlace(org.bukkit.event.block.BlockPlaceEvent e){if("LIGHT_BLOCK".equals(id(e.getItemInHand()))){addConfiguredBlock("lights",e.getBlockPlaced());e.getPlayer().sendMessage("§a照明を設置・自動登録しました。");}}
    @EventHandler public void onLightBlockBreak(org.bukkit.event.block.BlockBreakEvent e){String k=blockKey(e.getBlock());List<String> l=new ArrayList<>(mapCfg.getStringList("lights"));if(l.remove(k)){mapCfg.set("lights",l);saveMapConfig();if(e.getPlayer().hasPermission("osw.admin"))e.getPlayer().sendMessage("§7照明登録を自動解除しました。");}}

    private void sendHelp(CommandSender s){
        s.sendMessage("§6§l===== OneSignalWolf コマンド =====");
        s.sendMessage("§eゲーム §7/osw ho §8| §7/osw point §8| §7/osw report §8| §7/osw vote <COLOR>");
        if(s.hasPermission("osw.admin")){
            s.sendMessage("§e管理 §7/osw start §8| §7stop §8| §7end §8| §7meeting §8| §7breaker §8| §7emp");
            s.sendMessage("§eGM設定 §7/osw gm §8| §7/osw gm reset §8(開始前のHO・使命・カラー指定)");
            s.sendMessage("§eテスト §7/osw testmode <on|off> §8| §7/osw test start <人数> §8| §7/osw bot ...");
            s.sendMessage("§eアイテム §7/osw item <ID>");
            s.sendMessage("§eマップ §7/osw setup <...> §8(Tab補完対応)");
        }
        s.sendMessage("§bSignal §7通信機を右クリックしてGUIから送信します。§8(/osw signal はデバッグ用)");
        s.sendMessage("§7各階層で §fTAB §7を押すと利用可能な候補を表示します。");
    }

    private List<String> complete(String token, Collection<String> values){
        String q=token==null?"":token.toLowerCase(Locale.ROOT);
        return values.stream().filter(Objects::nonNull).filter(v->v.toLowerCase(Locale.ROOT).startsWith(q)).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    private List<String> sectionKeys(String path){
        if(mapCfg==null||mapCfg.getConfigurationSection(path)==null)return List.of();
        return new ArrayList<>(mapCfg.getConfigurationSection(path).getKeys(false));
    }
    private List<String> botIds(){return bots.values().stream().map(b->b.id).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    private List<String> voteCodes(){
        List<String> out=new ArrayList<>();
        for(Player q:Bukkit.getOnlinePlayers())if(data.containsKey(q.getUniqueId())&&q.getGameMode()!=GameMode.SPECTATOR&&!q.isDead())out.add(code(q));
        for(BotData b:bots.values())if(b.alive())out.add(b.code);
        return out;
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        boolean adm=sender.hasPermission("osw.admin");
        if(args.length==1){
            List<String> root=new ArrayList<>(List.of("help","ho","point","report","vote"));
            if(adm)root.addAll(List.of("start","stop","end","meeting","breaker","emp","item","testmode","test","bot","setup","signal","gm","tool","indoor","blizzard"));
            return complete(args[0],root);
        }
        String a=args[0].toLowerCase(Locale.ROOT);
        if(a.equals("gm")&&args.length==2)return complete(args[1],List.of("open","reset"));
        if(a.equals("vote")&&args.length==2)return complete(args[1],voteCodes());
        if(a.equals("testmode")&&args.length==2)return complete(args[1],List.of("on","off"));
        if(a.equals("test")){
            if(args.length==2)return complete(args[1],List.of("start"));
            if(args.length==3&&args[1].equalsIgnoreCase("start"))return complete(args[2],List.of("4","6","8","10","12"));
        }
        if(a.equals("bot")){
            if(args.length==2)return complete(args[1],List.of("add","remove","list","kill","tp"));
            if(args.length==3&&args[1].equalsIgnoreCase("add"))return complete(args[2],List.of("1","2","4","7"));
            if(args.length==3&&args[1].equalsIgnoreCase("remove")){List<String> x=new ArrayList<>(List.of("1","2","4","all"));return complete(args[2],x);}
            if(args.length==3&&(args[1].equalsIgnoreCase("kill")||args[1].equalsIgnoreCase("tp")))return complete(args[2],botIds());
        }
        if(a.equals("tool")&&args.length==2)return complete(args[1],List.of("light","lightblock","indoor"));
        if(a.equals("indoor")&&args.length==2)return complete(args[1],List.of("add","remove","list","show","info"));
        if(a.equals("blizzard")&&args.length==2)return complete(args[1],List.of("on","off","start","stop"));
        if(a.equals("item")&&args.length==2)return complete(args[1],List.of("BACKPACK","SIGNAL_BOOSTER","EMERGENCY_BATTERY","FIRST_AID","BODY_ARMOR","TRACKER","FLASHLIGHT","KNIFE","SMOKE","HANDGUN","STUN","EMP_SHIELD","NECK_WARMER"));
        if(a.equals("setup")){
            if(args.length==2)return complete(args[1],List.of("breaker","light","light-scan","charger","emp","wildlife","meeting","cage","winner","execution-switch","list","tp","remove","remove-look","test"));
            String b=args.length>1?args[1].toLowerCase(Locale.ROOT):"";
            if(b.equals("emp")){if(args.length==3)return complete(args[2],List.of("20","30","40","50"));if(args.length==4)return complete(args[3],List.of("30","60","90","120"));}
            if(b.equals("cage")&&args.length==3)return complete(args[2],List.of("1","2","3","4","5","6","7","8"));
            if((b.equals("tp")||b.equals("remove"))&&args.length==3)return complete(args[2],List.of("meeting","winner","cage","wildlife","execution-switch"));
            if((b.equals("tp")||b.equals("remove"))&&args.length==4){
                if(args[2].equalsIgnoreCase("cage"))return complete(args[3],sectionKeys("cages"));
                if(args[2].equalsIgnoreCase("wildlife"))return complete(args[3],sectionKeys("wildlife"));
            }
            if(b.equals("remove-look")&&args.length==3)return complete(args[2],List.of("breaker","emp"));
            if(b.equals("test")&&args.length==3)return complete(args[2],List.of("meeting","emp","breaker","wildlife","execution"));
            if(b.equals("test")&&args.length==4&&args[2].equalsIgnoreCase("wildlife"))return complete(args[3],sectionKeys("wildlife"));
        }
        return Collections.emptyList();
    }

    private boolean admin(CommandSender s){if(!s.hasPermission("osw.admin")){s.sendMessage("§cNo permission.");return false;}return true;}
    private Player findByCode(String c){return Bukkit.getOnlinePlayers().stream().filter(p->code(p).equalsIgnoreCase(c)).findFirst().orElse(null);} private String code(Player p){PlayerData d=data.get(p.getUniqueId());return d==null?p.getName():d.code;}

    private ItemStack item(String id){
        return switch(id){
            case "BACKPACK"->special(Material.BUNDLE,id,"§6Backpack",List.of("§7RIGHT CLICK: unlock +3 slots"));
            case "SIGNAL_BOOSTER"->special(Material.AMETHYST_SHARD,id,"§bSignal Booster",List.of("§7HOTBAR: +1 Signal word"));
            case "EMERGENCY_BATTERY"->special(Material.REDSTONE,id,"§eEmergency Battery",List.of("§7PASSIVE/device: extra powered use"));
            case "FIRST_AID"->special(Material.PAPER,id,"§cFirst Aid Kit",List.of("§7HAND: heal self/near target"));
            case "BODY_ARMOR"->special(Material.IRON_CHESTPLATE,id,"§7Body Armor",List.of("§7PASSIVE: physical damage -30%"));
            case "TRACKER"->special(Material.RECOVERY_COMPASS,id,"§aTracker",List.of("§7HOTBAR: recent nearby traces"));
            case "FLASHLIGHT"->energyItem(Material.CARROT_ON_A_STICK,id,"§e懐中電灯",List.of("§7HAND: 暗所で正面視界を補助","§7電力式"));
            case "KNIFE"->{ItemStack x=special(Material.IRON_SWORD,id,"§fKnife",List.of("§7通常命中: 耐久 -1/10","§7背後攻撃: 耐久 -2/10"));setDurabilityBar(x,10,10);yield x;}
            case "SMOKE"->special(Material.FIREWORK_STAR,id,"§7Smoke Grenade",List.of("§7HAND: dense smoke screen"));
            case "HANDGUN"->{int max=getConfig().getInt("handgun-magazine",6);ItemStack x=special(Material.CROSSBOW,id,"§6Handgun §7["+max+"/"+max+"]",List.of("§7HAND: 1マガジン限定"));ItemMeta m=x.getItemMeta();m.getPersistentDataContainer().set(ammoKey,PersistentDataType.INTEGER,max);x.setItemMeta(m);setDurabilityBar(x,max,max);yield x;}
            case "STUN"->{ItemStack x=special(Material.WOODEN_HOE,id,"§bStun Device §7[2/2]",List.of("§7HAND: 右クリックで雪玉軌道のスタン弾","§7使用回数: 2"));ItemMeta m=x.getItemMeta();m.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,2);x.setItemMeta(m);setDurabilityBar(x,2,2);yield x;}
            case "EMP_SHIELD"->energyItem(Material.SHEARS,id,"§5EMP Shield",List.of("§7HOTBAR: EMP圏内を検知","§7電力式"));
            case "NECK_WARMER"->special(Material.CHAIN,id,"§bネックウォーマー",List.of("§7PASSIVE: Backstabを1回無効化","§7寒冷時の体温低下を40%軽減","§7発動すると破壊される"));
            case "LIGHT_TOOL"->special(Material.BLAZE_ROD,id,"§e照明設定ツール",List.of("§7右クリック: 光源登録/解除"));
            case "LIGHT_BLOCK"->special(Material.SEA_LANTERN,id,"§e自動登録照明ブロック",List.of("§7設置すると停電対象へ自動登録"));
            case "INDOOR_TOOL"->special(Material.WOODEN_AXE,id,"§b屋内範囲設定ツール",List.of("§7左クリック: 地点A","§7右クリック: 地点B","§7/osw indoor add <名前>"));
            default->special(Material.BARRIER,id,"§cUnknown: "+id,List.of());
        };
    }
    private int customModelData(String id){return switch(id){
        case "FLASHLIGHT" -> 1102; case "EMP_SHIELD" -> 1103;
        case "HANDGUN" -> 1104; case "STUN" -> 1105; case "KNIFE" -> 1106;
        case "EMERGENCY_BATTERY" -> 1107; case "SMOKE" -> 1108; case "NECK_WARMER" -> 1109;
        default -> 0;};}
    private ItemStack special(Material mat,String id,String name,List<String> lore){ItemStack x=new ItemStack(mat);ItemMeta m=x.getItemMeta();m.setDisplayName(name);m.setLore(lore);int cmd=customModelData(id);if(cmd>0)m.setCustomModelData(cmd);m.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,id);x.setItemMeta(m);return x;}
    private ItemStack bound(Material mat,String id,String name,List<String> lore){return special(mat,id,name,lore);}
    private String id(ItemStack x){if(x==null||x.getType()==Material.AIR||!x.hasItemMeta())return null;return x.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING);}
    private boolean isBoundItem(ItemStack x){String i=id(x);return "HO".equals(i)||"RADIO".equals(i)||"NOTE".equals(i)||"LOCK".equals(i);}
    private boolean hasHotbar(Player p,String wanted){for(int i=0;i<9;i++)if(wanted.equals(id(p.getInventory().getItem(i))))return true;return false;}
    private boolean hasHand(Player p,String wanted){return wanted.equals(id(p.getInventory().getItemInMainHand()))||wanted.equals(id(p.getInventory().getItemInOffHand()));}
    private boolean hasPassive(Player p,String wanted){return findItem(p,wanted)!=null;}
    private ItemStack findItem(Player p,String wanted){for(ItemStack x:p.getInventory().getContents())if(wanted.equals(id(x)))return x;return null;}
    private void consume(ItemStack x){if(x.getAmount()<=1)x.setAmount(0);else x.setAmount(x.getAmount()-1);}
    private ItemStack findHotbarItem(Player p,String wanted){for(int i=0;i<9;i++){ItemStack x=p.getInventory().getItem(i);if(wanted.equals(id(x)))return x;}return null;}
    private ItemStack findHandItem(Player p,String wanted){ItemStack a=p.getInventory().getItemInMainHand();if(wanted.equals(id(a)))return a;ItemStack b=p.getInventory().getItemInOffHand();return wanted.equals(id(b))?b:null;}
    private ItemStack energyItem(Material mat,String id,String name,List<String> lore){ItemStack x=special(mat,id,name,lore);ItemMeta m=x.getItemMeta();m.getPersistentDataContainer().set(energyKey,PersistentDataType.INTEGER,100);x.setItemMeta(m);setDurabilityBar(x,100,100);return x;}
    private int energy(ItemStack x){if(x==null||!x.hasItemMeta())return 0;return x.getItemMeta().getPersistentDataContainer().getOrDefault(energyKey,PersistentDataType.INTEGER,100);}
    private void setEnergy(ItemStack x,int value){if(x==null)return;value=Math.max(0,Math.min(100,value));ItemMeta m=x.getItemMeta();m.getPersistentDataContainer().set(energyKey,PersistentDataType.INTEGER,value);x.setItemMeta(m);setDurabilityBar(x,value,100);}
    private void drainEnergyOncePerSecond(Player p,ItemStack x,String cfg,int defaultSeconds){long now=System.currentTimeMillis();long key=p.getUniqueId().getMostSignificantBits()^Objects.hashCode(id(x));Long last=lastEnergyDrain.get(new UUID(key,key));if(last!=null&&now-last<1000)return;lastEnergyDrain.put(new UUID(key,key),now);int seconds=Math.max(10,getConfig().getInt(cfg,defaultSeconds));int amount=Math.max(1,(int)Math.ceil(100.0/seconds));setEnergy(x,energy(x)-amount);}
    private boolean rechargeAll(Player p){boolean changed=false;for(ItemStack x:p.getInventory().getContents()){String i=id(x);if(("FLASHLIGHT".equals(i)||"EMP_SHIELD".equals(i))&&energy(x)<100){setEnergy(x,100);changed=true;}}return changed;}
    private void setDurabilityBar(ItemStack x,int remaining,int max){if(x==null||max<=0)return;ItemMeta m=x.getItemMeta();if(m instanceof org.bukkit.inventory.meta.Damageable d&&x.getType().getMaxDurability()>0){int md=x.getType().getMaxDurability();int damage=(int)Math.round(md*(1.0-Math.max(0,remaining)/(double)max));d.setDamage(Math.min(md-1,Math.max(0,damage)));x.setItemMeta(m);}}
    private void damageUses(ItemStack x,int amount,int max,boolean breakAtZero){if(x==null)return;ItemMeta m=x.getItemMeta();int left=m.getPersistentDataContainer().getOrDefault(usesKey,PersistentDataType.INTEGER,max);left=Math.max(0,left-amount);m.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,left);x.setItemMeta(m);setDurabilityBar(x,left,max);if(left<=0&&breakAtZero)consume(x);}
    private void updateSidebar(Player p,PlayerData d){Scoreboard sb=Bukkit.getScoreboardManager().getNewScoreboard();Objective o=sb.registerNewObjective("osw","dummy","§4§lOneSignalWolf");o.setDisplaySlot(DisplaySlot.SIDEBAR);int known=participantCount();for(Body b:bodies.values())if(!b.discovered&&!b.owner.equals(p.getUniqueId()))known--;String[] lines=meeting?new String[]{"§f会議終了まで §e"+Math.max(0,meetingSecondsLeft())+"秒","§7 ","§f職業 §d"+d.ho,"§f使命 §e"+(d.missionDone?"達成":"未達成"),"§f残り人数 §c"+known,"§f功績点 §6"+d.points}:new String[]{"§f職業 §d"+d.ho,"§7 ","§f使命 §e"+(d.missionDone?"達成":"未達成"),"§7  ","§f残り人数 §c"+known,"§f功績点 §6"+d.points};int score=lines.length;for(String line:lines)o.getScore(line+ChatColor.values()[score%ChatColor.values().length]).setScore(score--);p.setScoreboard(sb);}
    private int meetingSecondsLeft(){return meeting?Math.max(0,getConfig().getInt("meeting-seconds",90)):0;}
    private ItemStack makeWill(ItemStack note,String code){ItemStack w=new ItemStack(Material.WRITTEN_BOOK);BookMeta wm=(BookMeta)w.getItemMeta();wm.setTitle(code+"'s Will");wm.setAuthor(code);if(note.getItemMeta() instanceof BookMeta bm)wm.setPages(bm.getPages());wm.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"WILL");w.setItemMeta(wm);return w;}

    private static final class SignalSession{String target="ALL";final List<String> words=new ArrayList<>();}
    private static final class GmPreset{String ho;String mission;String color;}
    private static final class BotData{final Zombie entity;final String id;String code="BOT";int colorIndex=0;boolean dead=false;BotData(Zombie e,String i){entity=e;id=i;}boolean alive(){return !dead&&entity!=null&&entity.isValid()&&!entity.isDead();}}
    private static final class PlayerData{final String code;final int colorIndex;final String ho,mission;int points=0,backpacks=0;boolean missionDone=false,survivalAwarded=false;PlayerData(String c,int i,String h,String m){code=c;colorIndex=i;ho=h;mission=m;}}
    private static final class Body{final UUID id,owner;final String code,playerName;final List<ArmorStand> parts;final Interaction hit;final Location location;final long diedAt;final String cause;boolean discovered;final boolean bot;Body(UUID i,UUID o,String c,String n,List<ArmorStand> p,Interaction h,Location l,long d,String ca,boolean di,boolean bo){id=i;owner=o;code=c;playerName=n;parts=p;hit=h;location=l;diedAt=d;cause=ca;discovered=di;bot=bo;}void remove(){for(Entity e:parts)if(e.isValid())e.remove();if(hit!=null&&hit.isValid())hit.remove();}}
    private record PendingReport(UUID reporter,UUID body,long due){}
    private record EmpZone(World world,Location center,double radius,long until){}
    private record SmokeZone(World world,Location center,double radius,long until){}
    private record TrackPoint(Location loc,long time){}
    private record DamageCauseInfo(UUID actor,String type,long time){}
}
