package io.github.Leonardo0013YT.UltraCTW.game;

import com.nametagedit.plugin.NametagEdit;
import io.github.Leonardo0013YT.UltraCTW.UltraCTW;
import io.github.Leonardo0013YT.UltraCTW.enums.NPCType;
import io.github.Leonardo0013YT.UltraCTW.enums.State;
import io.github.Leonardo0013YT.UltraCTW.interfaces.*;
import io.github.Leonardo0013YT.UltraCTW.objects.Squared;
import io.github.Leonardo0013YT.UltraCTW.team.Team;
import io.github.Leonardo0013YT.UltraCTW.utils.Utils;
import io.github.Leonardo0013YT.UltraCTW.xseries.XSound;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class GameNoState implements Game {

    private final UltraCTW plugin;
    private final int id;
    private final String name, schematic;
    private final HashSet<Player> cached = new HashSet<>(), players = new HashSet<>(), spectators = new HashSet<>(), inLobby = new HashSet<>(), inGame = new HashSet<>();
    private final HashMap<ChatColor, Team> teams = new HashMap<>();
    private final HashMap<Integer, ChatColor> teamsID = new HashMap<>();
    private final HashMap<Player, GamePlayer> gamePlayer = new HashMap<>();
    private final ArrayList<Squared> protection = new ArrayList<>();
    private final ArrayList<WinEffect> winEffects = new ArrayList<>();
    private final ArrayList<WinDance> winDances = new ArrayList<>();
    private final ArrayList<KillEffect> killEffects = new ArrayList<>();
    private final HashMap<Location, ItemStack> wools = new HashMap<>();
    private final ArrayList<Location> npcShop = new ArrayList<>(), npcKits = new ArrayList<>();
    private final ArrayList<Location> placed = new ArrayList<>();
    private final Location lobby, spectator;
    private HashMap<UUID, ChatColor> playerTeam = new HashMap<>();
    private Squared lobbyProtection;
    private int teamSize, woolSize, min, starting, defKit, time = 0, max, worldTime;
    private State state;

    public GameNoState(UltraCTW plugin, String path, int id) {
        this.name = plugin.getArenas().get(path + ".name");
        plugin.getWc().deleteWorld(name);
        this.plugin = plugin;
        this.id = id;
        plugin.getWc().createEmptyWorld(name);
        this.schematic = plugin.getArenas().get(path + ".schematic");
        this.lobby = Utils.getStringLocation(plugin.getArenas().get(path + ".lobby"));
        if (plugin.getArenas().isSet(path + ".lobbyProtection.min")) {
            this.lobbyProtection = new Squared(Utils.getStringLocation(plugin.getArenas().get(path + ".lobbyProtection.max")), Utils.getStringLocation(plugin.getArenas().get(path + ".lobbyProtection.min")), false, true);
        }
        plugin.getWc().resetMap(new Location(lobby.getWorld(), 0, 75, 0), schematic);
        this.spectator = Utils.getStringLocation(plugin.getArenas().get(path + ".spectator"));
        for (String s : plugin.getArenas().getListOrDefault(path + ".npcShop", new ArrayList<>())) {
            npcShop.add(Utils.getStringLocation(s));
        }
        for (String s : plugin.getArenas().getListOrDefault(path + ".npcKits", new ArrayList<>())) {
            npcKits.add(Utils.getStringLocation(s));
        }
        this.teamSize = plugin.getArenas().getInt(path + ".teamSize");
        this.woolSize = plugin.getArenas().getInt(path + ".woolSize");
        this.defKit = plugin.getArenas().getIntOrDefault(path + ".defKit", 0);
        this.worldTime = plugin.getArenas().getIntOrDefault(path + ".worldTime", 500);
        lobby.getWorld().setTime(worldTime);
        plugin.getKm().getDefaultKits().add(defKit);
        this.starting = plugin.getCm().getStarting();
        this.min = plugin.getArenas().getInt(path + ".min");
        for (String c : plugin.getArenas().getConfig().getConfigurationSection(path + ".teams").getKeys(false)) {
            int tid = teams.size();
            ChatColor color = ChatColor.valueOf(c);
            teams.put(color, new Team(plugin, this, path + ".teams." + c, tid));
            teamsID.put(tid, color);
        }
        this.max = teamSize * teams.size();
        setState(State.WAITING);
        lobby.getWorld().getEntities().stream().filter(e -> !e.getType().equals(EntityType.PLAYER)).filter(e -> !e.getType().equals(EntityType.ARMOR_STAND)).forEach(Entity::remove);
        if (!plugin.getArenas().isSet(path + ".squareds")) return;
        for (String c : plugin.getArenas().getConfig().getConfigurationSection(path + ".squareds").getKeys(false)) {
            String nowPath = path + ".squareds." + c;
            protection.add(new Squared(Utils.getStringLocation(plugin.getArenas().get(nowPath + ".min")), Utils.getStringLocation(plugin.getArenas().get(nowPath + ".max")), true, false));
        }
    }

    @Override
    public void addPlayer(Player p) {
        cached.forEach(o -> o.hidePlayer(p));
        gamePlayer.put(p, new GamePlayer(p));
        p.teleport(lobby);
        Utils.setCleanPlayer(p);
        inLobby.add(p);
        cached.add(p);
        players.add(p);
        Utils.updateSB(p);
        checkStart();
        p.setGameMode(GameMode.SPECTATOR);
        NametagEdit.getApi().clearNametag(p);
        Game selected3 = plugin.getGm().getSelectedGame();
        plugin.getGem().createTeamsMenu(p, selected3);
        if (isState(State.WAITING) || isState(State.STARTING)) {
            sendGameMessage("§f¡§a" + p.getName() + " §ese ha unido §7(§b" + selected3.getPlayers().size() + "§7/§b" + selected3.getMax() + "§7)§f!");
        }
        p.sendMessage("§7[§eCTW§7] §f¡Usa §e§l/jugar §fpara ingresar a un equipo!");
    }

    @Override
    public void removePlayer(Player p) {
        plugin.getNpc().removePlayer(p);
        Utils.setCleanPlayer(p);
        removePlayerAllTeam(p);
        cached.remove(p);
        players.remove(p);
        spectators.remove(p);
        inLobby.remove(p);
        inGame.remove(p);
        if (gamePlayer.containsKey(p)) {
            GamePlayer gp = gamePlayer.get(p);
            gp.reset();
            gamePlayer.remove(p);
        }
        plugin.getTgm().removeTag(p);
        checkCancel();
        checkWin();
    }

    @Override
    public void checkStart() {
        if (isState(State.WAITING)) {
            if (cached.size() >= min) {
                setState(State.STARTING);
            }
        }
    }

    @Override
    public void reset() {
        plugin.getWc().deleteWorld(name);
        winDances.forEach(WinDance::stop);
        winEffects.forEach(WinEffect::stop);
        killEffects.forEach(KillEffect::stop);
        wools.clear();
        placed.clear();
        inGame.clear();
        inLobby.clear();
        gamePlayer.clear();
        spectators.clear();
        cached.clear();
        players.clear();
        World w = plugin.getWc().createEmptyWorld(name);
        updateWorld(w);
        teams.values().forEach(Team::reset);
        plugin.getWc().resetMap(new Location(lobby.getWorld(), 0, 75, 0), schematic);
        lobby.getWorld().setTime(worldTime);
        lobby.getWorld().getEntities().stream().filter(e -> !e.getType().equals(EntityType.PLAYER)).forEach(Entity::remove);
        starting = plugin.getCm().getStarting();
        time = 0;
        setState(State.WAITING);
    }

    public void updateWorld(World w) {
        lobbyProtection.getMax().setWorld(w);
        lobbyProtection.getMin().setWorld(w);
        teams.values().forEach(t -> t.updateWorld(w));
        lobby.setWorld(w);
        spectator.setWorld(w);
        for (Location l : npcShop) {
            l.setWorld(w);
        }
        for (Location l : npcKits) {
            l.setWorld(w);
        }
        wools.keySet().forEach(l -> l.setWorld(w));
        for (Squared s : protection) {
            s.getMin().setWorld(w);
            s.getMax().setWorld(w);
        }
    }

    @Override
    public void setSpect(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().setArmorContents(null);
        p.getInventory().clear();
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setHealth(p.getMaxHealth());
        p.setNoDamageTicks(Integer.MAX_VALUE);
        players.remove(p);
        spectators.add(p);
    }

    @Override
    public void update() {
        Utils.updateSB(this);
        if (isState(State.STARTING)) {
            if (starting == 30 || starting == 15 || starting == 10 || starting == 5 || starting == 4 || starting == 3 || starting == 2 || starting == 1) {
                sendGameTitle(plugin.getLang().get(null, "titles.starting.title").replaceAll("<time>", String.valueOf(starting)), plugin.getLang().get(null, "titles.starting.subtitle").replaceAll("<time>", String.valueOf(starting)), 0, 40, 0);
                sendGameMessage(plugin.getLang().get(null, "messages.starting").replaceAll("<starting>", String.valueOf(starting)).replaceAll("<s>", (starting > 1) ? "s" : ""));
                sendGameSound(XSound.BLOCK_NOTE_BLOCK_PLING.parseSound());
                for(Player on : cached){
                    sendGameActionBar(on, (plugin.getLang().get(null, "messages.starting").replaceAll("<starting>", String.valueOf(starting)).replaceAll("<s>", (starting > 1) ? "s" : "")));
                }
            }
            if (starting == 29 || starting == 14 || starting == 9 || starting == 0) {
                sendGameTitle("", "", 0, 1, 0);
            }
            if (starting == 0) {
                sendGameTitle("§e§l¡CAPTURA LA LANA!", "§a§l¡DIVIÉRTETE!", 20, 60, 20);
                setState(State.GAME);
                sendGameSound(XSound.ENTITY_PLAYER_LEVELUP.parseSound());
                checkTeamBalance();
                for (String s : plugin.getLang().getList("messages.start")) {
                    sendGameMessage(s);

                }
                for (Player on : cached) {
                    on.setFlying(false);
                    on.setAllowFlight(false);
                    on.setGameMode(GameMode.SURVIVAL);
                    CTWPlayer ctw = plugin.getDb().getCTWPlayer(on);
                    ctw.setPlayed(ctw.getPlayed() + 1);
                    Team t = getTeamPlayer(on);
                    if (t == null) {
                        addPlayerRandomTeam(on);
                        on.teleport(getTeamPlayer(on).getSpawn());
                    } else {
                        on.teleport(t.getSpawn());
                        plugin.getKm().giveDefaultKit(on, this, t);
                        Utils.updateSB(on);
                        inGame.add(on);
                        inLobby.remove(on);
                        for (Location k : npcKits) {
                            plugin.getSkm().spawnShopKeeper(on, k, ctw.getShopKeeper(), NPCType.KITS);
                        }
                        for (Location s : npcShop) {
                            plugin.getSkm().spawnShopKeeper(on, s, ctw.getShopKeeper(), NPCType.SHOP);
                        }
                        NametagEdit.getApi().setNametag(on, t.getName() + t.getColor() + "", "");
                        for (Player c : cached) {
                            c.showPlayer(on);
                            on.showPlayer(c);
                        }
                    }
                }
            }
            starting--;
        }
        if (isState(State.GAME)) {
            time++;
            teams.values().forEach(Team::updateSpawner);
            for(Player on : cached){
                Team t = getTeamPlayer(on);
                if (t == null) {
                    sendGameActionBar(on, "§7[§eCTW§7] §f¡Usa §e§l/jugar §fpara ingresar a un equipo!");
                } else {
                    sendGameActionBar(on, t.getColor() + "§lESTÀS EN EL EQUIPO §l" + t.getName());
                }
            }
        }
    }

    @Override
    public void checkWin() {
        if (isState(State.GAME)) {
            int al = getTeamAlive();
            if (al == 1) {
                Team t = getLastTeam();
                win(t);
            } else if (al == 0) {
                reset();
            }
        }
    }

    @Override
    public void checkCancel() {
        if (isState(State.STARTING)) {
            if (min > players.size()) {
                cancel();
            }
        }
    }

    @Override
    public void cancel() {
        this.starting = plugin.getCm().getStarting();
        setState(State.WAITING);
        sendGameMessage(plugin.getLang().get(null, "messages.cancelStart"));
        sendGameTitle(plugin.getLang().get(null, "titles.cancel.title"), plugin.getLang().get(null, "titles.cancel.subtitle"), 0, 40, 0);
        sendGameSound(plugin.getCm().getCancelStartSound());
    }

    @Override
    public void win(Team team) {
        if (plugin.isStop()) return;
        plugin.getGm().reset(this);
        setState(State.FINISH);
        GameWin gw = new GameWin(this);
        gw.setTeamWin(team);
        List<String> top = gw.getTop();
        String[] s1 = top.get(0).split(":");
        String[] s2 = top.get(1).split(":");
        String[] s3 = top.get(2).split(":");
        for (Player on : cached) {
            setSpect(on);
            for (String s : plugin.getLang().getList("messages.win")) {
                on.sendMessage(s.replaceAll("&", "§").replaceAll("<winner>", gw.getWinner()).replaceAll("<number1>", s1[1]).replaceAll("<top1>", s1[0]).replaceAll("<color1>", "" + ChatColor.valueOf(s1[2])).replaceAll("<number2>", s2[1]).replaceAll("<top2>", s2[0]).replaceAll("<color2>", "" + ChatColor.valueOf(s2[2])).replaceAll("<number3>", s3[1]).replaceAll("<top3>", s3[0]).replaceAll("<color3>", "" + ChatColor.valueOf(s3[2])));
            }
        }
        //plugin.getVc().getReflection().sendTitle(plugin.getLang().get("titles.lose.title"), plugin.getLang().get("titles.lose.subtitle"), 0, 60, 0, cached.stream().filter(on -> !team.getMembers().contains(on)).collect(Collectors.toList()));
        plugin.getVc().getReflection().sendTitle(team.getColor() + "§lDERROTA", team.getColor() + "¡El equipo " + gw.getWinner() + " ha ganado!", 20, 60, 20, cached.stream().filter(on -> !team.getMembers().contains(on)).collect(Collectors.toList()));
        for (Player w : team.getMembers()) {
            CTWPlayer ctw = plugin.getDb().getCTWPlayer(w);
            if (ctw == null) continue;
            if (plugin.getCm().isWCMDEnabled()) {
                plugin.getCm().getWinCommands().forEach(c -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replaceAll("<player>", w.getName())));
            }
            ctw.addCoins(plugin.getCm().getGCoinsWins());
            ctw.setXp(ctw.getXp() + plugin.getCm().getXpWin());
            //Todo lang
            w.sendMessage("§a§m§l---------------------------------------------");
            w.sendMessage("§7");
            w.sendMessage("§7            §a§lResumen de recompensas          §7");
            w.sendMessage("§7");
            w.sendMessage("§7Has obtenido:");
            w.sendMessage("§7• §6Monedas: +" + plugin.getCm().getGCoinsWins());
            w.sendMessage("§7• §bExp: +" + plugin.getCm().getXpWin());
            w.sendMessage("§7");
            w.sendMessage("§a§m§l---------------------------------------------");
            ctw.setWins(ctw.getWins() + 1);
            plugin.getLvl().checkUpgrade(w);
            plugin.getWem().execute(this, w, ctw.getWinEffect());
            plugin.getWdm().execute(this, w, ctw.getWinDance());
        }
        //plugin.getVc().getReflection().sendTitle(plugin.getLang().get("titles.win.title").replaceAll("<color>", team.getColor() + ""), plugin.getLang().get("titles.win.subtitle"), 0, 60, 0, team.getMembers());
        plugin.getVc().getReflection().sendTitle(team.getColor() + "§l¡VICTORIA!", team.getColor() + "¡El equipo " + gw.getWinner() + " ha ganado!", 20, 60, 20, team.getMembers());
        ArrayList<Player> back = new ArrayList<>(cached);

        //Todo enviar mensaje de lanas capturas

        new BukkitRunnable() {
            int count = 9;
            Game g = plugin.getGm().getSelectedGame();
            @Override
            public void run() {
                if(count==0) {
                    cancel();
                    return;
                }
                for(Player on : cached){
                    on.sendMessage("§7[§eCTW§7] §eUna nueva partida comienza en §a"+ count + "§e...");
                }
                if (count == 5 || count == 4 || count == 3 || count == 2 || count == 1 ) {
                    for(Player on : cached){
                        plugin.getVc().getReflection().sendTitle( "§fPròximo mapa: §a§l" + g.getName() ,"§fNueva partida en:§c§l " + count, 0, 40, 0, on);
                    }
                }
                count--;
            }
        }.runTaskTimer(plugin, 20L, 20L);

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::reset, 20 * 13);

        if (plugin.getCm().isAutoJoinFinish()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player on : back) {
                        if (on == null || !on.isOnline()) continue;
                        Game g = plugin.getGm().getSelectedGame();
                        plugin.getGm().addPlayerGame(on, g.getId());
                        on.sendMessage("§7[§eCTW§7] §e¡Fuiste enviado a la partida §a" + g.getName() + "§e!");
                    }
                }
            }.runTaskLater(plugin, 20 * 10);
        }
    }

    @Override
    public void addKill(Player p, boolean bowKill) {
        if (gamePlayer.containsKey(p)) {
            GamePlayer gp = gamePlayer.get(p);
            gp.setKills(gp.getKills() + 1);
            gp.addCoins(plugin.getCm().getCoinsKill());
            CTWPlayer ctw = plugin.getDb().getCTWPlayer(p);
            ctw.addCoins(plugin.getCm().getGCoinsKills());
            ctw.setXp(ctw.getXp() + plugin.getCm().getXpKill());
            ItemStack i = new ItemStack(322, 1);
            if (p.getInventory().firstEmpty() == -1) {
                p.sendMessage("Tu inventario estaba lleno, no recibiste la recompensa de Asesinato");
            } else {
                p.getInventory().addItem(i);
            }
            if (bowKill) {
                ctw.setBowKills(ctw.getBowKills() + 1);
            } else {
                ctw.setKills(ctw.getKills() + 1);
            }
            plugin.getLvl().checkUpgrade(p);
        }
    }

    @Override
    public void addDeath(Player p) {
        if (gamePlayer.containsKey(p)) {
            GamePlayer gp = gamePlayer.get(p);
            gp.setDeaths(gp.getDeaths() + 1);
            CTWPlayer ctw = plugin.getDb().getCTWPlayer(p);
            ctw.setDeaths(ctw.getDeaths() + 1);
        }
    }

    @Override
    public void sendGameMessage(String msg) {
        for (Player p : cached) {
            p.sendMessage(msg);
        }
    }

    @Override
    public void sendGameTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        plugin.getVc().getReflection().sendTitle(title, subtitle, fadeIn, stay, fadeOut, cached);
    }

    @Override
    public void sendGameActionBar(Player p, String msg) {
        plugin.getVc().getNMS().sendActionBar(p, msg);
    }

    @Override
    public void sendGameSound(Sound sound) {
        for (Player p : cached) {
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    @Override
    public int getTime() {
        return time;
    }

    @Override
    public boolean isState(State state) {
        return this.state.equals(state);
    }

    @Override
    public boolean isNearby(Location loc) {
        for (Team team : teams.values()) {
            if (team.isNearby(loc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addPlayerRandomTeam(Player p) {
        Team t = Utils.getMinorPlayersTeam(this);
        addPlayerTeam(p, t);
        p.sendMessage(plugin.getLang().get("messages.randomTeam").replaceAll("<team>", t.getName()));
        UltraCTW.get().getVc().getNMS().sendActionBar(p, "§e§lFuiste enviado al equipo "+ t.getName());

    }

    @Override
    public void addPlayerTeam(Player p, Team team) {
        removePlayerAllTeam(p);
        p.getInventory().clear();
        team.addMember(p);
        if (isState(State.GAME)) {
            CTWPlayer ctw = plugin.getDb().getCTWPlayer(p);
            ctw.setPlayed(ctw.getPlayed() + 1);
            p.teleport(team.getSpawn());
            plugin.getKm().giveDefaultKit(p, this, team);
            Utils.updateSB(p);
            inGame.add(p);
            inLobby.remove(p);
            p.setGameMode(GameMode.SURVIVAL);
            for (Location k : npcKits) {
                plugin.getSkm().spawnShopKeeper(p, k, ctw.getShopKeeper(), NPCType.KITS);
            }
            for (Location s : npcShop) {
                plugin.getSkm().spawnShopKeeper(p, s, ctw.getShopKeeper(), NPCType.SHOP);
            }
            NametagEdit.getApi().setNametag(p, team.getColor() + "", "");
            cached.forEach(o -> o.showPlayer(p));
        }
    }

    @Override
    public ArrayList<Location> getPlaced() {
        return placed;
    }

    @Override
    public void addWinEffects(WinEffect e) {
        winEffects.add(e);
    }

    @Override
    public void addWinDance(WinDance e) {
        winDances.add(e);
    }

    @Override
    public void addKillEffects(KillEffect e) {
        killEffects.add(e);
    }

    @Override
    public void removePlayerTeam(Player p, Team team) {
        team.removeMember(p);
        for (ChatColor c : team.getColors()) {
            if (team.getInProgress().get(c).isEmpty()) continue;
            team.getInProgress().get(c).remove(p.getUniqueId());
        }
    }

    @Override
    public GamePlayer getGamePlayer(Player p) {
        return gamePlayer.getOrDefault(p, new GamePlayer(p));
    }

    @Override
    public Team getTeamByID(int id) {
        return teams.get(teamsID.get(id));
    }

    @Override
    public Team getTeamByColor(ChatColor color) {
        return teams.get(color);
    }

    @Override
    public Team getTeamByWool(ChatColor color) {
        for (Team tt : teams.values()) {
            if (tt.getColors().contains(color)) {
                return tt;
            }
        }
        return null;
    }

    @Override
    public void checkTeamBalance() {
        Team minor = Utils.getMinorPlayersTeam(this);
        Team major = Utils.getMajorPlayersTeam(this);
        if (minor.getId() == major.getId()) return;
        if (minor.getTeamSize() < major.getTeamSize()) {
            Player on = major.getMembers().stream().findAny().orElse(null);
            if (on == null) return;
            removePlayerTeam(on, minor);
            addPlayerTeam(on, minor);
            on.sendMessage(plugin.getLang().get("messages.balancedTeam").replaceAll("<team>", minor.getName()));
            on.playSound(on.getLocation(), XSound.ENTITY_PLAYER_LEVELUP.parseSound(), 1.0f, 1.0f);
        }
    }

    @Override
    public void joinRandomTeam(Player p) {
        for (Team team : teams.values()) {
            if (team.getTeamSize() < teamSize) {
                addPlayerTeam(p, team);
                break;
            }
        }
    }

    @Override
    public void removePlayerAllTeam(Player p) {
        for (Team team : teams.values()) {
            if (team.getMembers().contains(p)) {
                removePlayerTeam(p, team);
            }
        }
    }

    @Override
    public Team getLastTeam() {
        for (Team team : teams.values()) {
            if (team.getTeamSize() > 0) {
                return team;
            }
        }
        return null;
    }

    @Override
    public int getTeamAlive() {
        int c = 0;
        for (Team team : teams.values()) {
            if (team.getTeamSize() > 0) {
                c++;
            }
        }
        return c;
    }

    @Override
    public Team getTeamPlayer(Player p) {
        for (Team team : teams.values()) {
            if (team.getMembers().contains(p)) {
                return team;
            }
        }
        return null;
    }

    @Override
    public void givePlayerItems(Player p) {
        p.getInventory().setItem(4, plugin.getIm().getTeams());
        p.getInventory().setItem(8, plugin.getIm().getLeave());
    }

    @Override
    public Squared getPlayerSquared(Player p) {
        for (Squared s : protection) {
            if (s.isInCuboid(p)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public Squared getPlayerSquared(Location loc) {
        for (Squared s : protection) {
            if (s.isInCuboid(loc)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public int getMax() {
        return max;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSchematic() {
        return schematic;
    }

    @Override
    public HashSet<Player> getCached() {
        return cached;
    }

    @Override
    public HashSet<Player> getPlayers() {
        return players;
    }

    @Override
    public HashSet<Player> getSpectators() {
        return spectators;
    }

    @Override
    public HashMap<ChatColor, Team> getTeams() {
        return teams;
    }

    @Override
    public HashMap<Integer, ChatColor> getTeamsID() {
        return teamsID;
    }

    @Override
    public HashMap<Player, GamePlayer> getGamePlayer() {
        return gamePlayer;
    }

    @Override
    public ArrayList<Squared> getProtection() {
        return protection;
    }

    @Override
    public ArrayList<WinEffect> getWinEffects() {
        return winEffects;
    }

    @Override
    public ArrayList<WinDance> getWinDances() {
        return winDances;
    }

    @Override
    public ArrayList<KillEffect> getKillEffects() {
        return killEffects;
    }

    @Override
    public Location getLobby() {
        return lobby;
    }

    @Override
    public Location getSpectator() {
        return spectator;
    }

    @Override
    public int getTeamSize() {
        return teamSize;
    }

    @Override
    public int getWoolSize() {
        return woolSize;
    }

    @Override
    public int getMin() {
        return min;
    }

    @Override
    public int getStarting() {
        return starting;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }

    @Override
    public int getDefKit() {
        return defKit;
    }

    @Override
    public HashMap<Location, ItemStack> getWools() {
        return wools;
    }

    @Override
    public HashSet<Player> getInLobby() {
        return inLobby;
    }

    @Override
    public HashSet<Player> getInGame() {
        return inGame;
    }

    @Override
    public Squared getLobbyProtection() {
        return lobbyProtection;
    }

}