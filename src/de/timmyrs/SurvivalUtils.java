package de.timmyrs;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class SurvivalUtils extends JavaPlugin implements Listener, CommandExecutor
{
	private File playerDataDir;
	private static final HashMap<Integer, ItemStack> startItems = new HashMap<>();
	static final ArrayList<TeleportationRequest> teleportationRequests = new ArrayList<>();
	private final HashMap<Player, Long> playersLastActivity = new HashMap<>();
	private final ArrayList<Player> antiAfkFarmingPlayers = new ArrayList<>();
	private final ArrayList<Player> cleverAntiAfkPlayers = new ArrayList<>();
	private final ArrayList<Player> sleepingPlayers = new ArrayList<>();
	private final HashMap<World, Integer> sleepMessageTasks = new HashMap<>();
	private final ArrayList<Player> colorSignInformed = new ArrayList<>();

	@Override
	public void onEnable()
	{
		playerDataDir = new File(getDataFolder(), "playerdata");
		getConfig().addDefault("homeLimits.default", 10);
		getConfig().addDefault("homeLimits.op", 100);
		getConfig().addDefault("startItems.enabled", false);
		final ArrayList<HashMap<String, Object>> defaultStartItems = new ArrayList<>();
		final HashMap<String, Object> apples = new HashMap<>();
		apples.put("slot", 1);
		apples.put("type", "APPLE");
		apples.put("amount", 8);
		apples.put("durability", 0);
		defaultStartItems.add(apples);
		final HashMap<String, Object> bed = new HashMap<>();
		bed.put("slot", 2);
		bed.put("type", "BED");
		bed.put("amount", 1);
		bed.put("durability", 0);
		defaultStartItems.add(bed);
		getConfig().addDefault("startItems.items", defaultStartItems);
		getConfig().addDefault("antiAfkFarming.enabled", false);
		getConfig().addDefault("antiAfkFarming.seconds", 60);
		getConfig().addDefault("cleverAfkKick.enabled", false);
		getConfig().addDefault("cleverAfkKick.seconds", 300);
		getConfig().addDefault("afkKick.enabled", false);
		getConfig().addDefault("afkKick.seconds", 1200);
		getConfig().addDefault("afkKickMessage", "You have been kicked for being AFK. Feel free to reconnect now that you're no longer AFK.");
		getConfig().addDefault("sleepCoordination.enabled", false);
		getConfig().addDefault("sleepCoordination.message", "&e%sleeping%/%total% players are sleeping. Won't you join them?");
		getConfig().addDefault("sleepCoordination.intervalTicks", 50);
		getConfig().addDefault("sleepCoordination.skipPercent", 100D);
		getConfig().addDefault("createWarpCommands", false);
		getConfig().addDefault("warpSigns.line", "[Warp]");
		getConfig().addDefault("warpSigns.color", "5");
		getConfig().addDefault("warps", new HashMap<String, Object>());
		getConfig().options().copyDefaults(true);
		saveConfig();
		reloadSurvivalUtilsConfig();
		getCommand("survivalutils").setExecutor(this);
		getCommand("tpa").setExecutor(this);
		getCommand("tpahere").setExecutor(this);
		getCommand("tpaccept").setExecutor(this);
		getCommand("tpcancel").setExecutor(this);
		getCommand("tptoggle").setExecutor(this);
		getCommand("home").setExecutor(this);
		getCommand("homes").setExecutor(this);
		getCommand("sethome").setExecutor(this);
		getCommand("delhome").setExecutor(this);
		getCommand("warp").setExecutor(this);
		getCommand("warps").setExecutor(this);
		getCommand("setwarp").setExecutor(this);
		getCommand("delwarp").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, ()->
		{
			synchronized(teleportationRequests)
			{
				final ArrayList<TeleportationRequest> _teleportationRequests = new ArrayList<>(teleportationRequests);
				final long time = getTime();
				for(TeleportationRequest tr : _teleportationRequests)
				{
					if(tr.expires < time)
					{
						teleportationRequests.remove(tr);
						tr.from.sendMessage("§eYour teleportation request to " + tr.to.getName() + " has expired.");
					}
				}
			}
			if(getConfig().getBoolean("antiAfkFarming.enabled") || getConfig().getBoolean("cleverAfkKick.enabled") || getConfig().getBoolean("afkKick.enabled"))
			{
				final Map<Player, Long> _playersLastActivity;
				synchronized(playersLastActivity)
				{
					_playersLastActivity = new HashMap<>(playersLastActivity);
				}
				final long kickTime = getTime() - getConfig().getInt("afkKick.seconds");
				final long cleverTime = getTime() - getConfig().getInt("cleverAfkKick.seconds");
				final long afkTime = getTime() - getConfig().getInt("antiAfkFarming.seconds");
				for(Map.Entry<Player, Long> entry : _playersLastActivity.entrySet())
				{
					if(getConfig().getBoolean("afkKick.enabled") && entry.getValue() < kickTime)
					{
						entry.getKey().kickPlayer(applyColor(getConfig().getString("afkKickMessage")));
					}
					else
					{
						if(getConfig().getBoolean("cleverAfkKick.enabled") && entry.getValue() < cleverTime)
						{
							synchronized(cleverAntiAfkPlayers)
							{
								if(!cleverAntiAfkPlayers.contains(entry.getKey()))
								{
									cleverAntiAfkPlayers.add(entry.getKey());
								}
							}
						}
						if(getConfig().getBoolean("antiAfkFarming.enabled") && entry.getValue() < afkTime)
						{
							synchronized(antiAfkFarmingPlayers)
							{
								if(!antiAfkFarmingPlayers.contains(entry.getKey()))
								{
									antiAfkFarmingPlayers.add(entry.getKey());
									entry.getKey().setMetadata("afk", new FixedMetadataValue(this, true));
								}
							}
						}
					}
				}
			}
		}, 100, 100);
	}

	private String applyColor(String string)
	{
		return string.replace("&", "§").replace("§§", "&");
	}

	private Location stringToLocation(String string)
	{
		final String[] arr = string.split(",");
		if(arr.length != 6)
		{
			return null;
		}
		return new Location(getServer().getWorld(arr[0]), Double.valueOf(arr[1]), Double.valueOf(arr[2]), Double.valueOf(arr[3]), Float.valueOf(arr[4]), Float.valueOf(arr[5]));
	}

	private String locationToString(Location location)
	{
		return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ() + "," + location.getYaw() + "," + location.getPitch();
	}

	private boolean isVanished(Player player)
	{
		for(MetadataValue meta : player.getMetadata("vanished"))
		{
			if(meta.asBoolean())
			{
				return true;
			}
		}
		return false;
	}

	private boolean canTeleport(Player p)
	{
		//noinspection deprecation
		return p.isOnGround() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
	}

	static long getTime()
	{
		return System.currentTimeMillis() / 1000L;
	}

	private File getConfigFile(Player p) throws IOException
	{
		if(!playerDataDir.exists() && !playerDataDir.mkdir())
		{
			throw new IOException("Failed to create " + playerDataDir.getPath());
		}
		return new File(playerDataDir, p.getUniqueId().toString().replace("-", "") + ".yml");
	}

	private int getHomeLimit(Player p)
	{
		if(p.isOp())
		{
			return getConfig().getInt("homeLimits.op");
		}
		final Map<String, Object> limits = getConfig().getConfigurationSection("homeLimits").getValues(false);
		for(String limit : limits.keySet())
		{
			if(!limit.equals("default") && !limit.equals("op") && p.hasPermission("survivalutils.homelimit." + limit))
			{
				return getConfig().getInt("homeLimits." + limit);
			}
		}
		return getConfig().getInt("homeLimits.default");
	}

	private void reloadSurvivalUtilsConfig()
	{
		reloadConfig();
		synchronized(SurvivalUtils.startItems)
		{
			SurvivalUtils.startItems.clear();
			if(getConfig().getBoolean("startItems.enabled"))
			{
				//noinspection unchecked
				final ArrayList<HashMap<String, Object>> startItems = (ArrayList<HashMap<String, Object>>) getConfig().getList("startItems.items");
				if(startItems != null)
				{
					for(HashMap<String, Object> i : startItems)
					{
						final ItemStack item = new ItemStack(Material.valueOf(((String) i.get("type")).toUpperCase()), (Integer) i.get("amount"));
						if(i.containsKey("durability"))
						{
							item.setDurability(((Integer) i.get("durability")).shortValue());
						}
						SurvivalUtils.startItems.put((Integer) i.get("slot"), item);
					}
				}
			}
		}
		synchronized(sleepingPlayers)
		{
			sleepingPlayers.clear();
			if(getConfig().getBoolean("sleepCoordination.enabled"))
			{
				for(Player p : getServer().getOnlinePlayers())
				{
					if(p.isSleeping())
					{
						sleepingPlayers.add(p);
					}
				}
				handleSleep(0);
			}
		}
		synchronized(playersLastActivity)
		{
			synchronized(antiAfkFarmingPlayers)
			{
				synchronized(cleverAntiAfkPlayers)
				{
					if(getConfig().getBoolean("antiAfkFarming.enabled") || getConfig().getBoolean("cleverAfkKick.enabled") || getConfig().getBoolean("afkKick.enabled"))
					{
						if(playersLastActivity.size() == 0)
						{
							final long time = getTime();
							for(Player p : getServer().getOnlinePlayers())
							{
								if(!p.hasPermission("survivalutils.allowafk"))
								{
									playersLastActivity.put(p, time);
								}
							}
							clearAntiAfkFarmingPlayers();
							cleverAntiAfkPlayers.clear();
						}
					}
					else
					{
						playersLastActivity.clear();
						clearAntiAfkFarmingPlayers();
						cleverAntiAfkPlayers.clear();
					}
				}
			}
		}
		saveConfig();
	}

	private void clearAntiAfkFarmingPlayers()
	{
		synchronized(antiAfkFarmingPlayers)
		{
			for(Player p : antiAfkFarmingPlayers)
			{
				p.removeMetadata("afk", this);
			}
			antiAfkFarmingPlayers.clear();
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e)
	{
		if(!e.getPlayer().hasPermission("survivalutils.allowafk") && (getConfig().getBoolean("antiAfkFarming.enabled") || getConfig().getBoolean("cleverAfkKick.enabled") || getConfig().getBoolean("afkKick.enabled")))
		{
			synchronized(playersLastActivity)
			{
				playersLastActivity.put(e.getPlayer(), getTime());
			}
		}
		if(!e.getPlayer().hasPlayedBefore())
		{
			synchronized(startItems)
			{
				for(Map.Entry<Integer, ItemStack> i : startItems.entrySet())
				{
					e.getPlayer().getInventory().setItem(i.getKey(), i.getValue().clone());
				}
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e)
	{
		if(getConfig().getBoolean("antiAfkFarming.enabled") || getConfig().getBoolean("cleverAfkKick.enabled") || getConfig().getBoolean("afkKick.enabled"))
		{
			synchronized(playersLastActivity)
			{
				playersLastActivity.remove(e.getPlayer());
			}
			synchronized(antiAfkFarmingPlayers)
			{
				antiAfkFarmingPlayers.remove(e.getPlayer());
				e.getPlayer().removeMetadata("afk", this);
			}
			synchronized(cleverAntiAfkPlayers)
			{
				cleverAntiAfkPlayers.remove(e.getPlayer());
			}
			synchronized(colorSignInformed)
			{
				colorSignInformed.remove(e.getPlayer());
			}
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent e)
	{
		if(!e.getPlayer().hasPermission("survivalutils.allowafk") && (getConfig().getBoolean("antiAfkFarming.enabled") || getConfig().getBoolean("cleverAfkKick.enabled") || getConfig().getBoolean("afkKick.enabled")))
		{
			synchronized(playersLastActivity)
			{
				playersLastActivity.put(e.getPlayer(), getTime());
			}
			synchronized(antiAfkFarmingPlayers)
			{
				antiAfkFarmingPlayers.remove(e.getPlayer());
				e.getPlayer().removeMetadata("afk", this);
			}
			synchronized(cleverAntiAfkPlayers)
			{
				cleverAntiAfkPlayers.remove(e.getPlayer());
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent e)
	{
		if(e.isCancelled())
		{
			return;
		}
		if(e.getEntity() instanceof Player && getConfig().getBoolean("cleverAfkKick.enabled"))
		{
			final Player p = (Player) e.getEntity();
			synchronized(cleverAntiAfkPlayers)
			{
				if(cleverAntiAfkPlayers.contains(p))
				{
					e.setCancelled(true);
					p.kickPlayer(applyColor(getConfig().getString("afkKickMessage")));
					return;
				}
			}
		}
		if(e.getDamager() instanceof Player && getConfig().getBoolean("antiAfkFarming.enabled"))
		{
			final Player p = (Player) e.getDamager();
			synchronized(antiAfkFarmingPlayers)
			{
				if(antiAfkFarmingPlayers.contains(p))
				{
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(PlayerInteractEvent e)
	{
		if(e.isCancelled())
		{
			return;
		}
		if(getConfig().getBoolean("antiAfkFarming.enabled"))
		{
			synchronized(antiAfkFarmingPlayers)
			{
				if(antiAfkFarmingPlayers.contains(e.getPlayer()))
				{
					e.setCancelled(true);
					return;
				}
			}
		}
		if(e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getState() instanceof Sign)
		{
			final Sign s = (Sign) e.getClickedBlock().getState();
			if(s.getLine(0).equals("§" + getConfig().getString("warpSigns.color") + getConfig().getString("warpSigns.line")))
			{
				final String w = s.getLine(1).toLowerCase();
				if((e.getPlayer().hasPermission("survivalutils.warps") || e.getPlayer().hasPermission("survivalutils.warps." + w)) && getConfig().contains("warps." + w))
				{
					e.setCancelled(true);
					e.getPlayer().teleport(stringToLocation(getConfig().getString("warps." + w)));
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onBlockPlace(BlockPlaceEvent e)
	{
		if(!e.isCancelled() && e.getPlayer().hasPermission("survivalutils.coloredsigns") && e.getBlockPlaced().getState() instanceof Sign)
		{
			synchronized(colorSignInformed)
			{
				if(colorSignInformed.contains(e.getPlayer()))
				{
					return;
				}
				colorSignInformed.add(e.getPlayer());
			}
			e.getPlayer().sendMessage("§aYou can use color codes on your sign with '&'!");
			e.getPlayer().sendMessage("For a list of color codes visit https://wiki.vg/Chat#Colors");
			e.getPlayer().sendMessage("If you want to use '&', use '&&'");
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onSignChange(SignChangeEvent e)
	{
		if(e.isCancelled())
		{
			return;
		}
		if(e.getPlayer().hasPermission("survivalutils.coloredsigns"))
		{
			for(int i = 0; i < 4; i++)
			{
				e.setLine(i, applyColor(e.getLine(i)));
			}
		}
		if(e.getPlayer().hasPermission("survivalutils.warpsigns") && e.getLine(0).trim().equalsIgnoreCase(getConfig().getString("warpSigns.line")))
		{
			e.setLine(0, "§" + getConfig().getString("warpSigns.color") + getConfig().getString("warpSigns.line"));
			e.setLine(1, e.getLine(1).trim());
		}
	}

	private void handleSleep(long subTicks)
	{
		if(getConfig().getBoolean("sleepCoordination.enabled"))
		{
			synchronized(sleepMessageTasks)
			{
				for(Integer i : sleepMessageTasks.values())
				{
					getServer().getScheduler().cancelTask(i);
				}
				sleepMessageTasks.clear();
			}
			final HashMap<World, ArrayList<Player>> worlds = new HashMap<>();
			synchronized(sleepingPlayers)
			{
				final ArrayList<Player> _sleepingPlayers = new ArrayList<>(sleepingPlayers);
				for(Player p : _sleepingPlayers)
				{
					if(p.isOnline() && p.isSleeping())
					{
						if(isVanished(p))
						{
							continue;
						}
						final ArrayList<Player> worldSleepingPlayers;
						if(worlds.containsKey(p.getWorld()))
						{
							worldSleepingPlayers = worlds.get(p.getWorld());
						}
						else
						{
							worldSleepingPlayers = new ArrayList<>();
						}
						worldSleepingPlayers.add(p);
						worlds.put(p.getWorld(), worldSleepingPlayers);
					}
					else
					{
						sleepingPlayers.remove(p);
					}
				}
			}
			final long intervalTicks;
			if((getConfig().getLong("sleepCoordination.intervalTicks") - subTicks) < 1)
			{
				intervalTicks = 1;
			}
			else
			{
				intervalTicks = getConfig().getLong("sleepCoordination.intervalTicks") - subTicks;
			}
			for(final Map.Entry<World, ArrayList<Player>> entry : worlds.entrySet())
			{
				final int worldSleepingPlayers = entry.getValue().size();
				if(worldSleepingPlayers > 0)
				{
					long neededForSleep = 0;
					for(Player p : entry.getKey().getPlayers())
					{
						if(isVanished(p))
						{
							continue;
						}
						synchronized(cleverAntiAfkPlayers)
						{
							if(cleverAntiAfkPlayers.contains(p))
							{
								p.kickPlayer(applyColor(getConfig().getString("afkKickMessage")));
							}
							else
							{
								neededForSleep++;
							}
						}
					}
					neededForSleep = Math.round((double) neededForSleep * getConfig().getDouble("sleepCoordination.skipPercent") * 0.01D);
					if(worldSleepingPlayers >= neededForSleep)
					{
						if(worldSleepingPlayers < entry.getKey().getPlayers().size())
						{
							synchronized(sleepingPlayers)
							{
								for(Player p : entry.getKey().getPlayers())
								{
									sleepingPlayers.remove(p);
								}
							}
							entry.getKey().setTime(0);
							entry.getKey().setThundering(false);
						}
					}
					else
					{
						final String neededForSleepString = String.valueOf(neededForSleep);
						synchronized(sleepMessageTasks)
						{
							sleepMessageTasks.put(entry.getKey(), getServer().getScheduler().scheduleSyncDelayedTask(this, ()->
							{
								synchronized(sleepMessageTasks)
								{
									sleepMessageTasks.remove(entry.getKey());
								}
								final String message = applyColor(getConfig().getString("sleepCoordination.message")).replace("%sleeping%", String.valueOf(entry.getValue().size())).replace("%total%", neededForSleepString);
								synchronized(cleverAntiAfkPlayers)
								{
									for(Player p : entry.getKey().getPlayers())
									{
										p.sendMessage(message);
									}
								}
							}, intervalTicks));
						}
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerBedEnter(final PlayerBedEnterEvent e)
	{
		if(!e.isCancelled() && getConfig().getBoolean("sleepCoordination.enabled"))
		{
			getServer().getScheduler().scheduleSyncDelayedTask(this, ()->
			{
				if(e.getPlayer().isSleeping())
				{
					synchronized(sleepingPlayers)
					{
						if(sleepingPlayers.contains(e.getPlayer()))
						{
							return;
						}
						sleepingPlayers.add(e.getPlayer());
					}
					handleSleep(5);
				}
			}, 5);
		}
	}

	@EventHandler
	public void onPlayerBedLeave(PlayerBedLeaveEvent e)
	{
		if(getConfig().getBoolean("sleepCoordination.enabled"))
		{
			synchronized(sleepingPlayers)
			{
				sleepingPlayers.remove(e.getPlayer());
			}
			handleSleep(0);
		}
	}

	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e)
	{
		if(!e.isCancelled())
		{
			final Player p = e.getPlayer();
			if(p.hasPermission("survivalutils.warp") && getConfig().getBoolean("createWarpCommands"))
			{
				final String command = e.getMessage().substring(1).split(" ")[0].toLowerCase();
				if((p.hasPermission("survivalutils.warps") || p.hasPermission("survivalutils.warps." + command)) && getServer().getPluginCommand(command) == null && getConfig().contains("warps." + command))
				{
					e.setCancelled(true);
					if(canTeleport(p))
					{
						p.teleport(stringToLocation(getConfig().getString("warps." + command)));
					}
					else
					{
						p.sendMessage("§cYou may not teleport right now.");
					}
				}
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender s, Command c, String l, String[] a)
	{
		switch(c.getName())
		{
			case "survivalutils":
				if(a.length > 0 && a[0].equalsIgnoreCase("reload") && s.hasPermission("survivalutils.reload"))
				{
					reloadSurvivalUtilsConfig();
					s.sendMessage("§aReloaded the configuration.");
				}
				else
				{
					s.sendMessage("https://www.spigotmc.org/resources/survivalutils.62574/");
				}
				break;
			case "tpa":
			case "tpahere":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player t = getServer().getPlayer(a[0]);
						if(t != null && t.isOnline())
						{
							if(t.hasPermission("survivalutils.tpa.accept"))
							{
								final Player p = (Player) s;
								if(!t.equals(p))
								{
									try
									{
										final File playerConfigFile = getConfigFile(t);
										final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
										if(playerConfig.contains("tpa"))
										{
											p.sendMessage("§c" + t.getName() + " doesn't want to receive teleportation requests.");
										}
										else
										{
											TeleportationRequest tr = TeleportationRequest.getFrom(p);
											if(tr != null && !tr.to.equals(t))
											{
												synchronized(teleportationRequests)
												{
													teleportationRequests.remove(tr);
												}
												s.sendMessage("§eYour teleportation request to " + tr.to.getName() + " has been cancelled.");
												tr = null;
											}
											if(tr == null)
											{
												final boolean here = c.getName().equals("tpahere");
												synchronized(teleportationRequests)
												{
													teleportationRequests.add(new TeleportationRequest(p, t, here));
												}
												if(here)
												{
													t.sendMessage("§e" + p.getName() + " has requested you to teleport to them.");
												}
												else
												{
													t.sendMessage("§e" + p.getName() + " has requested to teleport to you.");
												}
												t.sendMessage("You can accept it using /tpaccept " + p.getName());
												p.sendMessage("§aYou've sent a teleportation request to " + t.getName() + ".");
												if(p.hasPermission("survivalutils.tpa.cancel"))
												{
													p.sendMessage("You can cancel it using /tpcancel.");
												}
											}
											else
											{
												p.sendMessage("§cI already got it the first time.");
											}
										}
									}
									catch(IOException e)
									{
										e.printStackTrace();
										s.sendMessage("§cAn I/O error has occured. Please contact an administrator.");
									}
								}
								else
								{
									s.sendMessage("Yes?");
								}

							}
							else
							{
								s.sendMessage("§c" + t.getName() + " is missing the survivalutils.tpa.accept permission, so they can't /tpaccept.");
							}
						}
						else
						{
							s.sendMessage("§c'" + a[0] + "' isn't online.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /tpa <player>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "tpaccept":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player t = getServer().getPlayer(a[0]);
						if(t != null && t.isOnline())
						{
							final Player p = (Player) s;
							final TeleportationRequest tr = TeleportationRequest.get(t, p);
							if(tr != null)
							{
								synchronized(teleportationRequests)
								{
									teleportationRequests.remove(tr);
								}
								if(tr.here)
								{
									p.teleport(t);
								}
								else
								{
									t.teleport(p);
								}
							}
							else
							{
								s.sendMessage("§c" + t.getName() + " hasn't sent you a teleportation request recently.");
							}
						}
						else
						{
							s.sendMessage("§c'" + a[0] + "' isn't online.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /tpaccept <player>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "tpcancel":
				if(s instanceof Player)
				{
					final TeleportationRequest tr = TeleportationRequest.getFrom((Player) s);
					if(tr != null)
					{
						synchronized(teleportationRequests)
						{
							teleportationRequests.remove(tr);
						}
						s.sendMessage("§eYour teleportation request to " + tr.to.getName() + " has been cancelled.");
					}
					else
					{
						s.sendMessage("§eYou haven't sent a teleportation request recently.");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "tptoggle":
				if(s instanceof Player)
				{
					if(s.hasPermission("survivalutils.tpa.accept"))
					{
						try
						{
							final File playerConfigFile = getConfigFile((Player) s);
							final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
							if(playerConfig.contains("tpa"))
							{
								playerConfig.set("tpa", null);
								s.sendMessage("§aPlayers are now able to send you teleportation requests.");
							}
							else
							{
								playerConfig.set("tpa", 1);
								s.sendMessage("§aPlayers are now unable to send you teleportation requests.");

							}
							playerConfig.save(playerConfigFile);
						}
						catch(IOException e)
						{
							e.printStackTrace();
							s.sendMessage("§cAn I/O error has occured. Please contact an administrator.");
						}
					}
					else
					{
						s.sendMessage("§cYou're missing the survivalutils.tpa.accept permission, so you can't get teleportation requests.");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "warp":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player p = (Player) s;
						if(canTeleport(p))
						{
							final String w = a[0].toLowerCase();
							if((p.hasPermission("survivalutils.warps") || p.hasPermission("survivalutils.warps." + w)) && getConfig().contains("warps." + w))
							{
								p.teleport(stringToLocation(getConfig().getString("warps." + w)));
							}
							else
							{
								p.sendMessage("§c'" + w + "' is not a warp point.");
							}
						}
						else
						{
							p.sendMessage("§cYou may not teleport right now.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /warp <name>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "warps":
			{
				final Map<String, Object> warps = getConfig().getConfigurationSection("warps").getValues(false);
				int i = 0;
				final StringBuilder message = new StringBuilder();
				for(String name : warps.keySet())
				{
					if(s.hasPermission("survivalutils.warps") || s.hasPermission("survivalutils.warps." + name))
					{
						message.append(" ").append(name);
						i++;
					}
				}
				if(i == 0)
				{
					s.sendMessage("There are no warps.");
				}
				else
				{
					s.sendMessage((i == 1 ? "There is 1 warp:" : "There are " + i + " warps:") + message.toString());
				}
				break;
			}
			case "setwarp":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final String w = a[0].toLowerCase();
						if(getConfig().contains("warps." + w))
						{
							s.sendMessage("§cWarp point '" + w + "' already exists. Run /delwarp " + w + " first.");
						}
						else
						{
							getConfig().set("warps." + w, locationToString(((Player) s).getLocation()));
							s.sendMessage("§aSuccessfully created warp point '" + w + "'.");
							saveConfig();
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /setwarp <name>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "delwarp":
			{
				if(a.length == 1)
				{
					final Map<String, Object> warps = getConfig().getConfigurationSection("warps").getValues(false);
					final String w = a[0].toLowerCase();
					if(warps.containsKey(w))
					{
						warps.remove(w);
						getConfig().set("warps", warps);
						saveConfig();
						reloadConfig();
						s.sendMessage("§aSuccessfully deleted warp point '" + w + "'.");
					}
					else
					{
						s.sendMessage("§c'" + w + "' is not a warp point.");
					}
				}
				else
				{
					s.sendMessage("§cSyntax: /delwarp <name>");
				}
				break;
			}
			case "home":
				if(s instanceof Player)
				{
					if(a.length > 1)
					{
						s.sendMessage("§cSyntax: /home <name>");
					}
					else
					{
						final String homename;
						if(a.length == 1)
						{
							homename = a[0].toLowerCase();
						}
						else
						{
							homename = "home";
						}
						final Player p = (Player) s;
						if(canTeleport(p))
						{
							try
							{
								final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(getConfigFile(p));
								if(playerConfig.contains("homes"))
								{
									final Map<String, Object> homes = playerConfig.getConfigurationSection("homes").getValues(false);
									if(homes.containsKey(homename))
									{
										p.teleport(stringToLocation((String) homes.get(homename)));
									}
									else if(homes.size() == 1)
									{
										p.teleport(stringToLocation((String) homes.values().iterator().next()));
									}
									else
									{
										p.sendMessage("§cYou don't have a home named '" + homename + "'.");
									}
								}
								else
								{
									p.sendMessage("You're homeless. :^)");
								}
							}
							catch(IOException e)
							{
								e.printStackTrace();
								p.sendMessage("§cAn I/O error has occured. Please contact an administrator.");
							}
						}
						else
						{
							p.sendMessage("§cYou may not teleport right now.");
						}
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "homes":
				if(s instanceof Player)
				{
					final Player p = (Player) s;
					try
					{
						final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(getConfigFile(p));
						if(playerConfig.contains("homes"))
						{
							final Map<String, Object> homes = playerConfig.getConfigurationSection("homes").getValues(false);
							if(homes.size() > 0)
							{
								final StringBuilder message = new StringBuilder("You have ").append(homes.size()).append("/").append(getHomeLimit(p)).append(" homes:");
								for(String name : homes.keySet())
								{
									message.append(" ").append(name);
								}
								p.sendMessage(message.toString());
							}
							else
							{
								p.sendMessage("You're homeless. :^)");
							}
						}
						else
						{
							p.sendMessage("You're homeless. :^)");
						}
					}
					catch(IOException e)
					{
						e.printStackTrace();
						p.sendMessage("§cAn I/O error has occured. Please contact an administrator.");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "sethome":
				if(s instanceof Player)
				{
					if(a.length > 1)
					{
						s.sendMessage("§cSyntax: /sethome <name>");
					}
					else
					{
						final String homename;
						if(a.length == 1)
						{
							homename = a[0].toLowerCase();
						}
						else
						{
							homename = "home";
						}
						final Player p = (Player) s;
						try
						{
							final File playerConfigFile = getConfigFile(p);
							final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
							if(getHomeLimit(p) == 0 || (playerConfig.contains("homes") && playerConfig.getConfigurationSection("homes").getValues(false).size() >= getHomeLimit(p)))
							{
								p.sendMessage("§cYou can't create more than " + getHomeLimit(p) + " homes.");
							}
							else if(playerConfig.contains("homes." + homename))
							{
								p.sendMessage("§cYou already have a home named '" + homename + "'. Run /delhome " + homename + " first.");
							}
							else
							{
								playerConfig.set("homes." + homename, locationToString(p.getLocation()));
								try
								{
									playerConfig.save(playerConfigFile);
								}
								catch(IOException e)
								{
									throw new RuntimeException(e.getMessage());
								}
								p.sendMessage("§aSuccessfully created home '" + homename + "'.");
							}
						}
						catch(IOException e)
						{
							e.printStackTrace();
							p.sendMessage("§cAn I/O error has occured. Please contact an administrator.");
						}
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "delhome":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player p = (Player) s;
						try
						{
							final File playerConfigFile = getConfigFile(p);
							final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
							if(playerConfig.contains("homes." + a[0].toLowerCase()))
							{
								final Map<String, Object> homes = playerConfig.getConfigurationSection("homes").getValues(false);
								homes.remove(a[0].toLowerCase());
								playerConfig.set("homes", homes);
								try
								{
									playerConfig.save(playerConfigFile);
								}
								catch(IOException e)
								{
									throw new RuntimeException(e.getMessage());
								}
								p.sendMessage("§aSuccessfully deleted home '" + a[0].toLowerCase() + "'.");
							}
							else
							{
								p.sendMessage("§cYou don't have a home named '" + a[0].toLowerCase() + "'.");
							}
						}
						catch(IOException e)
						{
							e.printStackTrace();
							p.sendMessage("§cAn I/O error has occured. Please contact an administrator.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /delhome <name>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
		}
		return true;
	}
}

class TeleportationRequest
{
	final Player from;
	final Player to;
	final boolean here;
	final long expires;

	TeleportationRequest(Player from, Player to, boolean here)
	{
		this.from = from;
		this.to = to;
		this.here = here;
		this.expires = SurvivalUtils.getTime() + 60;
	}

	static TeleportationRequest get(Player from, Player to)
	{
		synchronized(SurvivalUtils.teleportationRequests)
		{
			for(TeleportationRequest tr : SurvivalUtils.teleportationRequests)
			{
				if(tr.from.equals(from) && tr.to.equals(to))
				{
					return tr;
				}
			}
		}
		return null;
	}

	static TeleportationRequest getFrom(Player from)
	{
		synchronized(SurvivalUtils.teleportationRequests)
		{
			for(TeleportationRequest tr : SurvivalUtils.teleportationRequests)
			{
				if(tr.from.equals(from))
				{
					return tr;
				}
			}
		}
		return null;
	}
}
