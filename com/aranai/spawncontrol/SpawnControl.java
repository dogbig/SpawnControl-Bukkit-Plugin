package com.aranai.spawncontrol;

import java.io.*;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.*;
import java.sql.*;

// Import bukkit packages

import net.minecraft.server.WorldServer;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

// Import permissions package
import com.nijikokun.bukkit.Permissions.Permissions;

/**
 * SpawnControl for Bukkit
 *
 * @author Timberjaw
 */
public class SpawnControl extends JavaPlugin {
    private final SCPlayerListener playerListener = new SCPlayerListener(this);
    private Connection conn;
    public static Logger log;
    public final static String directory = "plugins/SpawnControl";
    public final static String db = "jdbc:sqlite:" + SpawnControl.directory + File.separator + "spawncontrol.db";
    
    // Permissions
    public static Permissions Permissions = null;
    public boolean usePermissions = false;
    
    // Cache variables
    private Hashtable<String,Integer> activePlayerIds;
    private Hashtable<Integer,Location> homes;
    private Hashtable<String,Integer> activeGroupIds;
    private Hashtable<Integer,Location> groupSpawns;
    private Hashtable<String,Boolean> respawning;
    private String lastSetting;
    private int lastSettingValue;
    
    // Settings
    public static final class Settings {
    	public static final int NO = 0;
    	public static final int YES = 1;
    	public static final int DEATH_NONE = 0;
    	public static final int DEATH_HOME = 1;
    	public static final int DEATH_GROUPSPAWN = 2;
    	public static final int DEATH_GLOBALSPAWN = 3;
    	public static final int JOIN_NONE = 0;
    	public static final int JOIN_HOME = 1;
    	public static final int JOIN_GROUPSPAWN = 2;
    	public static final int JOIN_GLOBALSPAWN = 3;
    	public static final int GLOBALSPAWN_DEFAULT = 0;
    	public static final int GLOBALSPAWN_OVERRIDE = 1;
    }
    
    public static final List<String> validSettings = Arrays.asList(
    		"enable_home", "enable_groupspawn", "enable_globalspawn",
    		"behavior_join", "behavior_death", "behavior_globalspawn");

    public SpawnControl(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
        super(pluginLoader, instance, desc, folder, plugin, cLoader);
        // TODO: Place any custom initialisation code here
    }
    
    // Initialize database
    private void initDB()
    {
    	ResultSet rs = null;
    	Statement st = null;
    	
    	try
        {
    		Class.forName("org.sqlite.JDBC");
        	conn = DriverManager.getConnection(db);
        	
        	DatabaseMetaData dbm = conn.getMetaData();
        	
        	// Check players table
            rs = dbm.getTables(null, null, "players", null);
            if (!rs.next())
            {
            	// Create table
            	log.info("[SpawnControl]: Table 'players' not found, creating.");
            	
            	conn.setAutoCommit(false);
                st = conn.createStatement();
                st.execute("CREATE TABLE `players` (`id` INTEGER PRIMARY KEY, `name` varchar(32) NOT NULL, "
                		+"`x` REAL, `y` REAL, `z` REAL, `r` REAL, `p` REAL, "
                		+"`updated` INTEGER, `updated_by` varchar(32));");
                st.execute("CREATE UNIQUE INDEX playerIndex on `players` (`name`);");
                conn.commit();
                
                log.info("[SpawnControl]: Table 'players' created.");
            }
            
            // Check groups table
            rs = dbm.getTables(null, null, "groups", null);
            if (!rs.next())
            {
            	// Create table
            	log.info("[SpawnControl]: Table 'groups' not found, creating.");
            	
            	conn.setAutoCommit(false);
                st = conn.createStatement();
                st.execute("CREATE TABLE `groups` (`id` INTEGER PRIMARY KEY, `name` varchar(32) NOT NULL, "
                		+"`x` REAL, `y` REAL, `z` REAL, `r` REAL, `p` REAL, "
                		+"`updated` INTEGER, `updated_by` varchar(32));");
                st.execute("CREATE UNIQUE INDEX groupIndex on `groups` (`name`);");
                conn.commit();
                
                log.info("[SpawnControl]: Table 'groups' created.");
            }
            
            // Check settings table
            boolean needSettings = false;
            rs = dbm.getTables(null, null, "settings", null);
            if (!rs.next())
            {
            	// Create table
            	needSettings = true;
            	System.out.println("[SpawnControl]: Table 'settings' not found, creating.");
            	
            	conn.setAutoCommit(false);
                st = conn.createStatement();
                st.execute("CREATE TABLE `settings` (`setting` varchar(32) PRIMARY KEY, `value` INT, "
                		+"`updated` INTEGER, `updated_by` varchar(32));");
                conn.commit();
                
                log.info("[SpawnControl]: Table 'settings' created.");
            }
        	
	        rs.close();
	        conn.close();
	        
	        if(needSettings)
	        {
	            // Insert default settings
		        this.setSetting("enable_home", Settings.YES, "initDB");
		        this.setSetting("enable_groupspawn", Settings.YES, "initDB");
		        this.setSetting("enable_globalspawn", Settings.YES, "initDB");
		        this.setSetting("behavior_death", Settings.DEATH_GLOBALSPAWN, "initDB");
		        this.setSetting("behavior_join", Settings.JOIN_NONE, "initDB");
		        this.setSetting("behavior_globalspawn", Settings.GLOBALSPAWN_DEFAULT, "initDB");
	        }
	        
	        // Check global spawn
	    	if(!this.activeGroupIds.contains("scglobal"))
	    	{
	    		if(!this.getGroupData("scglobal"))
	    		{
	    			// No group spawn available, use global
	    			log.info("[SpawnControl]: No global spawn found, setting global spawn to world spawn.");
	    			this.setGroupSpawn("scglobal", this.getServer().getWorlds().get(0).getSpawnLocation(), "initDB");
	    		}
	    		
	    		int db = this.getSetting("behavior_globalspawn");
	        	if(db != SpawnControl.Settings.GLOBALSPAWN_DEFAULT)
	        	{
		    		// Get global spawn location
		    		Location lg = this.getGroupSpawn("scglobal");
		    		
		    		// Set regular spawn location
		    		WorldServer ws = ((CraftWorld)this.getServer().getWorlds().get(0)).getHandle();
	                ws.spawnX = lg.getBlockX();
	                ws.spawnY = lg.getBlockY();
	                ws.spawnZ = lg.getBlockZ();
	        	}
	    	}
        }
        catch(SQLException e)
        {
        	// ERROR
        	System.out.println("[initDB] DB ERROR - " + e.getMessage() + " | SQLState: " + e.getSQLState() + " | Error Code: " + e.getErrorCode());
        }
        catch(Exception e)
        {
        	// Error
        	System.out.println("Error: " + e.getMessage());
        	e.printStackTrace();
        }
    }

    public void onEnable() {
    	log = Logger.getLogger("Minecraft");
    	
    	// Initialize active player ids and homes
        this.activePlayerIds = new Hashtable<String,Integer>();
        this.homes = new Hashtable<Integer,Location>();
        
        // Initialize active group ids and group spawns
        this.activeGroupIds = new Hashtable<String,Integer>();
        this.groupSpawns = new Hashtable<Integer,Location>();
        
        // Intialize respawn list
        this.respawning = new Hashtable<String,Boolean>();
        
        // Initialize last setting info
        this.lastSetting = "";
        this.lastSettingValue = -1;
    	
    	// Make sure we have a local folder for our database and such
        if (!new File(directory).exists()) {
            try {
                (new File(directory)).mkdir();
            } catch (Exception e) {
                SpawnControl.log.log(Level.SEVERE, "[SpawnControl]: Unable to create spawncontrol/ directory.");
            }
        }
        
        // Initialize the database
        this.initDB();
        
        // Initialize permissions system
    	Plugin test = this.getServer().getPluginManager().getPlugin("Permissions");

    	if(SpawnControl.Permissions == null) {
    	    if(test != null) {
    	    	SpawnControl.Permissions = (Permissions)test;
    	    	this.usePermissions = true;
    	    } else {
    	    	log.info("[SpawnControl] Warning: Permissions system not enabled.");
    	    }
    	}
        
        // Register our events
        PluginManager pm = getServer().getPluginManager();
        
        // Get player join
        pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Normal, this);
        
        // Get player respawn
        pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener, Priority.Highest, this);
        
        // Enable message
        PluginDescriptionFile pdfFile = this.getDescription();
        log.info( "[SpawnControl] version [" + pdfFile.getVersion() + "] loaded" );
    }
    
    public void onDisable() {
        // Disable message
    	PluginDescriptionFile pdfFile = this.getDescription();
    	log.info( "[SpawnControl] version [" + pdfFile.getVersion() + "] unloaded" );
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
    	return this.playerListener.onCommand(sender, command, commandLabel, args);
    }
    
    // Get timestamp
    public int getTimeStamp()
    {
    	return (int) (System.currentTimeMillis() / 1000L);
    }
    
    // Mark as respawning
    public void markPlayerRespawning(String name) { this.markPlayerDoneRespawning(name); this.respawning.put(name, true); }
    // Mark as done respawning
    public void markPlayerDoneRespawning(String name) { this.respawning.remove(name); }
    // Check to see if the player is respawning
    public boolean isPlayerRespawning(String name) { return this.respawning.containsKey(name); }
    
    
    // Get setting
    public int getSetting(String name)
    {
    	Connection conn = null;
    	PreparedStatement ps = null;
        ResultSet rs = null;
        int value = -1;
        
        if(this.lastSetting.equals(name))
        {
        	return this.lastSettingValue;
        }
		
		// Get from database
		try
        {
    		Class.forName("org.sqlite.JDBC");
        	conn = DriverManager.getConnection(db);
        	ps = conn.prepareStatement("SELECT * FROM `settings` WHERE `setting` = ?");
            ps.setString(1, name);
            rs = ps.executeQuery();
             
            while (rs.next()) { value = rs.getInt("value"); this.lastSetting = name; this.lastSettingValue = value; }
        	conn.close();
        }
        catch(Exception e)
        {
        	// Error
        	SpawnControl.log.warning("[SpawnControl] DB Error: " + e.getMessage());
        	e.printStackTrace();
        }
        
        return value;
    }
    
    // Set setting
    public boolean setSetting(String name, int value, String setter)
    {
        boolean success = true;
        
        try
        {
	    	Class.forName("org.sqlite.JDBC");
	    	Connection conn = DriverManager.getConnection(db);
	    	conn.setAutoCommit(false);
	        PreparedStatement ps = conn.prepareStatement("REPLACE INTO `settings` (`setting`,`value`,`updated`,`updated_by`) VALUES (?, ?, ?, ?);");
	        ps.setString(1, name);
	        ps.setInt(2, value);
	        ps.setInt(3, this.getTimeStamp());
	        ps.setString(4, setter);
	        ps.execute();
	        conn.commit();
	        conn.close();
	        
	        if(this.lastSetting.equals(name))
	        {
	        	this.lastSetting = "";
	        	this.lastSettingValue = -1;
	        }
        }
        catch(Exception e)
        {
        	SpawnControl.log.severe("[SpawnControl] Failed to save setting '"+name+"' with value '"+value+"'");
        	success = false;
        }
        
    	return success;
    }
    
    // Spawn
    public void sendToSpawn(Player p)
    {
    	this.sendToGroupSpawn("scglobal", p);
    }
    
    // Set spawn
    public boolean setSpawn(Location l, String setter)
    {
    	return this.setGroupSpawn("scglobal", l, setter);
    }
    
    // Get spawn
    public Location getSpawn()
    {
    	return this.getGroupSpawn("scglobal");
    }
    
    // Home
    public void sendHome(Player p)
    {
    	// Check for home
    	if(!this.activePlayerIds.contains(p.getName()))
    	{
    		if(!this.getPlayerData(p.getName()))
    		{
    			// No home available, use global
    			this.sendToSpawn(p);
    			return;
    		}
    	}
    	
    	// Teleport to home
    	p.teleportTo(this.homes.get(this.activePlayerIds.get(p.getName())));
    }
    
    // Get home
    public Location getHome(String name)
    {
    	// Check for home
    	if(!this.activePlayerIds.contains(name))
    	{
    		if(this.getPlayerData(name))
    		{
    			// Found home!
    			return this.homes.get(this.activePlayerIds.get(name));
    		}
    	}
    	
    	return null;
    }
    
    // Sethome
    public boolean setHome(String name, Location l, String updatedBy)
    {
    	Connection conn = null;
    	PreparedStatement ps = null;
        Boolean success = false;
		
		// Save to database
		try
        {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(db);
			conn.setAutoCommit(false);
			ps = conn.prepareStatement("REPLACE INTO `players` (id, name, x, y, z, r, p, updated, updated_by) VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?);");
			ps.setString(1, name);
			ps.setDouble(2, l.getX());
			ps.setDouble(3, l.getY());
			ps.setDouble(4, l.getZ());
			ps.setFloat(5, l.getYaw());
			ps.setFloat(6, l.getPitch());
			ps.setInt(7, this.getTimeStamp());
			ps.setString(8, updatedBy);
			ps.execute();
			conn.commit();
        	conn.close();
        	
        	success = true;
        }
        catch(SQLException e)
        {
        	// ERROR
        	System.out.println("[setHome] DB ERROR - " + e.getMessage() + " | SQLState: " + e.getSQLState() + " | Error Code: " + e.getErrorCode());
        }
        catch(Exception e)
        {
        	// Error
        	System.out.println("Error: " + e.getMessage());
        	e.printStackTrace();
        }
        
        if(success)
        {
        	// Update local cache
        	this.getPlayerData(name);
        }
        
        return success;
    }
    
    // Group spawn
    public void sendToGroupSpawn(String group, Player p)
    {
    	// Check for spawn
    	if(!this.activeGroupIds.contains(group))
    	{
    		if(!this.getGroupData(group))
    		{
    			// No group spawn available, use global
    			this.sendToSpawn(p);
    			return;
    		}
    	}
    	
    	// Teleport to home
    	p.teleportTo(this.groupSpawns.get(this.activeGroupIds.get(group)));
    }
    
    // Set group spawn
    public boolean setGroupSpawn(String group, Location l, String updatedBy)
    {
    	Connection conn = null;
    	PreparedStatement ps = null;
        Boolean success = false;
		
		// Save to database
		try
        {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(db);
			conn.setAutoCommit(false);
			ps = conn.prepareStatement("REPLACE INTO `groups` (id, name, x, y, z, r, p, updated, updated_by) VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?);");
			ps.setString(1, group);
			ps.setDouble(2, l.getX());
			ps.setDouble(3, l.getY());
			ps.setDouble(4, l.getZ());
			ps.setFloat(5, l.getYaw());
			ps.setFloat(6, l.getPitch());
			ps.setInt(7, this.getTimeStamp());
			ps.setString(8, updatedBy);
			ps.execute();
			conn.commit();
        	conn.close();
        	
        	success = true;
        }
        catch(SQLException e)
        {
        	// ERROR
        	System.out.println("[setGroupSpawn] DB ERROR - " + e.getMessage() + " | SQLState: " + e.getSQLState() + " | Error Code: " + e.getErrorCode());
        }
        catch(Exception e)
        {
        	// Error
        	System.out.println("Error: " + e.getMessage());
        	e.printStackTrace();
        }
        
        if(success)
        {
        	// Update local cache
        	this.getGroupData(group);
        }
        
        return success;
    }
    
    // Get group spawn
    public Location getGroupSpawn(String group)
    {
    	// Check for spawn
    	if(this.activeGroupIds.contains(group) || this.getGroupData(group))
    	{
    		return this.groupSpawns.get(this.activeGroupIds.get(group));
    	}
    	
    	SpawnControl.log.warning("[SpawnControl] Could not find or load group spawn for '"+group+"'!");
    	
    	return null;
    }
    
    // Utility
    private boolean getPlayerData(String name)
    {
    	Connection conn = null;
    	PreparedStatement ps = null;
        ResultSet rs = null;
        Boolean success = false;
        Integer id = 0;
		
		// Get from database
		try
        {
    		Class.forName("org.sqlite.JDBC");
        	conn = DriverManager.getConnection(db);
        	//conn.setAutoCommit(false);
        	ps = conn.prepareStatement("SELECT * FROM `players` WHERE `name` = ?");
            ps.setString(1, name);
            rs = ps.executeQuery();
            //conn.commit();
             
             while (rs.next()) {
                 success = true;
                 this.activePlayerIds.put(name, id);
                 Location l = new Location(this.getServer().getWorlds().get(0), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("r"), rs.getFloat("p"));
                 this.homes.put(id, l);
             }
        	conn.close();
        }
        catch(SQLException e)
        {
        	// ERROR
        	System.out.println("[getPlayerData] DB ERROR - " + e.getMessage() + " | SQLState: " + e.getSQLState() + " | Error Code: " + e.getErrorCode());
        }
        catch(Exception e)
        {
        	// Error
        	System.out.println("Error: " + e.getMessage());
        	e.printStackTrace();
        }
        
        return success;
    }
    
    private boolean getGroupData(String name)
    {
    	Connection conn = null;
    	PreparedStatement ps = null;
        ResultSet rs = null;
        Boolean success = false;
        Integer id = 0;
		
		// Get from database
		try
        {
    		Class.forName("org.sqlite.JDBC");
        	conn = DriverManager.getConnection(db);
        	//conn.setAutoCommit(false);
        	ps = conn.prepareStatement("SELECT * FROM `groups` WHERE `name` = ?");
            ps.setString(1, name);
            rs = ps.executeQuery();
            //conn.commit();
             
             while (rs.next()) {
                 success = true;
                 this.activeGroupIds.put(name, id);
                 Location l = new Location(this.getServer().getWorlds().get(0), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("r"), rs.getFloat("p"));
                 this.groupSpawns.put(id, l);
             }
        	conn.close();
        }
        catch(SQLException e)
        {
        	// ERROR
        	System.out.println("[getGroupData] DB ERROR - " + e.getMessage() + " | SQLState: " + e.getSQLState() + " | Error Code: " + e.getErrorCode());
        }
        catch(Exception e)
        {
        	// Error
        	System.out.println("Error: " + e.getMessage());
        	e.printStackTrace();
        }
        
        return success;
    }
    
    public void importConfig()
    {
    	File cf = new File(directory+"/spawncontrol-players.properties");
    	
    	if(cf.exists())
    	{
    		// Attempt import
            BufferedReader reader = null;

            try
            {
                reader = new BufferedReader(new FileReader(cf));
                String text = null;

                // Read a line
                while ((text = reader.readLine()) != null)
                {
                	// Skip if comment
                	if(!text.startsWith("#"))
                	{
                		// Format: Timberjaw=-86.14281646837361\:75.0\:233.43342838872454\:168.00002\:17.40001
                		text = text.replaceAll("\\\\", "");
                		String[] parts = text.split("=");
                		String name = parts[0];
                		String[] coords = parts[1].split(":");
                		Location l = new Location(null,
                				Double.parseDouble(coords[0]),
                				Double.parseDouble(coords[1]),
                				Double.parseDouble(coords[2]),
                				Float.parseFloat(coords[3]),
                				Float.parseFloat(coords[4]));
                		
                		// Set home
                		this.setHome(name, l, "ConfigImport");
                		
                		log.info("[SpawnControl] Found home for '"+name+"' at: "+l.getX()+","+l.getY()+","+l.getZ()+","+l.getYaw()+","+l.getPitch());
                	}
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    if (reader != null)
                    {
                        reader.close();
                    }
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
    	}
    }
    
    public void importGroupConfig()
    {
    	File cf = new File(directory+"/spawncontrol-groups.properties");
    	
    	if(cf.exists())
    	{
    		// Attempt import
            BufferedReader reader = null;

            try
            {
                reader = new BufferedReader(new FileReader(cf));
                String text = null;

                // Read a line
                while ((text = reader.readLine()) != null)
                {
                	// Skip if comment
                	if(!text.startsWith("#"))
                	{
                		// Format: admins=-56.50158762045817:12.0:265.4291449731157
                		text = text.replaceAll("\\\\", "");
                		String[] parts = text.split("=");
                		String name = parts[0];
                		String[] coords = parts[1].split(":");
                		Location l = new Location(null,
                				Double.parseDouble(coords[0]),
                				Double.parseDouble(coords[1]),
                				Double.parseDouble(coords[2]),
                				0.0f,
                				0.0f);
                		
                		// Set home
                		this.setGroupSpawn(name, l, "ConfigImport");
                		
                		log.info("[SpawnControl] Found group spawn for '"+name+"' at: "+l.getX()+","+l.getY()+","+l.getZ()+","+l.getYaw()+","+l.getPitch());
                	}
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    if (reader != null)
                    {
                        reader.close();
                    }
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
    	}
    }
}