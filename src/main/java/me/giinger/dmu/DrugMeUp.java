package me.giinger.dmu;

import me.giinger.dmu.Updater.UpdateType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class DrugMeUp extends JavaPlugin {

    public FileConfiguration config = getConfig();
    public Logger log = getLogger();

    private ArrayList<String> onDrugs = new ArrayList<>();
    private ArrayList<String> noplace = new ArrayList<>();
    private ArrayList<String> isJump = new ArrayList<>();
    private ArrayList<String> drunk = new ArrayList<>();
    private ArrayList<String> heartattack = new ArrayList<>();
    private ArrayList<World> worlds = new ArrayList<>();
    private HashMap<ItemStack, Drug> drugs = new HashMap<>();
    private boolean isUpdate;
    private boolean isDownloaded;
    private File matList = new File("plugins/DrugMeUp/materialList.txt");
    private File oldDir = new File("plugins/DrugMeUp/Old_Configs/");
    private PlayerHandler playerHandler = new PlayerHandler(this);
    private Updater updater = new Updater(this, 35506, this.getFile(), Updater.UpdateType.NO_DOWNLOAD, true);

    public void onDisable() {
        log.info("Disabled!");
    }

    public void onEnable() {
        log.info("Enabled!");
        getServer().getPluginManager().registerEvents(new EventsHandler(this), this);

        saveDefaultConfig();
        createMaterialList();
        configUpdate();

        config = getConfig();

        gatherDrugs();

        if (config.getBoolean("Options.AutoUpdateChecker")
                && !config.getBoolean("Options.AutoUpdateDownload")) {
            isUpdate(UpdateType.NO_DOWNLOAD);
        } else if (config.getBoolean("Options.AutoUpdateDownload")) {
            isUpdate(UpdateType.DEFAULT);
        }

        if (isMultiworld()) {
            gatherWorlds();
        }
    }

    /* Gather the drugs into their own, separate objects */
    private void gatherDrugs() {
        for (String key : config.getConfigurationSection("DrugIds").getKeys(false)) {
            Material mat = Material.getMaterial(key.split(":")[0]);
            short dmg = (key.split(":").length == 1) ? 0 : Short.parseShort(key.split(":")[1]);
            ItemStack item = new ItemStack(mat, 1, dmg);
            String name = config.getString("DrugIds." + key + ".DrugName");
            String message = (config.getString("DrugIds." + key + ".Message") != null) ? config.getString("DrugIds" +
                    "." + key + ".Message") : "";
            String[] effects = config.getString("DrugIds." + key + ".Effect")
                    .replaceAll(" ", "").split(",");
            String[] negatives = config.getString("DrugIds." + key + ".Negatives")
                    .replaceAll(" ", "").split(",");
            int negChance = (config.getInt("DrugIds." + key + ".NegChance") != 0) ? config.getInt("DrugIds." + key
                    + ".NegChance") : 0;
            boolean type = config.getString("DrugIds." + key + ".Type").equalsIgnoreCase("All");
            boolean smoke = config.getBoolean("DrugIds." + key + ".Smoke");
            boolean negative = (negChance != 0);
            boolean sneak = config.getBoolean("DrugIds." + key + ".MustSneak");
            boolean edible = item.getType().isEdible() || item.getType().name().equalsIgnoreCase("POTION");
            drugs.put(item, new Drug(item, name, message, effects, negatives, negChance, type, smoke, negative,
                    sneak, edible));
        }
    }

    public PlayerHandler getPlayerHandler() {
        return playerHandler;
    }

    public Player getPlayer(String name) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    /* Create the list of materials */
    private void createMaterialList() {
        try {
            if (!matList.exists()) {
                matList.createNewFile();
                FileWriter fw = new FileWriter(matList);

                fw.write("---- All Materials ----" + System.lineSeparator());
                for (Material m : Material.values()) {
                    fw.write(m.name() + System.lineSeparator());
                }
                fw.close();

                Bukkit.getConsoleSender().sendMessage(
                        ChatColor.RED + "" + ChatColor.BOLD + "[DrugMeUp] Material File Generated " + ChatColor.RESET);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("drugmeup")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("drugs.reload")) {
                        reloadConfig();
                        if (sender instanceof Player) {
                            sender.sendMessage(ChatColor.GREEN
                                    + "DrugMeUp Reloaded!");
                            return true;
                        }
                        sender.sendMessage("[DrugMeUp] Reloaded!");
                        return true;
                    }
                }
            } else if (args.length == 2) {
                Player p = getPlayer(args[1]);
                if (args[0].equalsIgnoreCase("cleardrugs")) {
                    if (sender.hasPermission("drugs.cleardrugs")) {
                        if (p == null) {
                            if (sender instanceof Player) {
                                sender.sendMessage(ChatColor.RED + "[DrugMeUp] '" + args[1] + "' is not online.");
                            } else {
                                sender.sendMessage("[DrugMeUp] '" + args[1] + "' is not online.");
                            }
                            return true;
                        } else {
                            for (PotionEffect pe : p.getActivePotionEffects()) {
                                p.removePotionEffect(pe.getType());
                            }
                            p.sendMessage(ChatColor.GREEN
                                    + "[DrugMeUp] All of your drug effects have been cleared!");
                            if (sender instanceof Player) {
                                sender.sendMessage(ChatColor.GREEN + "[DrugMeUp] Cleared drug effects from '"
                                        + p.getName() + "'.");
                            } else {
                                sender.sendMessage("[DrugMeUp] Cleared drug effects from '" + p.getName() + "'.");
                            }
                            return true;
                        }
                    } else {
                        sender.sendMessage(colorize(config.getString("Chat.Errors.NoPerms")));
                    }
                }
            } else {
                return false;
            }
        } else {
            sender.sendMessage(colorize(config.getString("Chat.Errors.NoPerms")));
        }
        return true;
    }

    /* Colorize a String */
    public String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /* Update config */
    private void configUpdate() {
        try {
            File file = new File(getDataFolder(), "config.yml");
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            // Only change this if you need to regenerate the config.
            String check = "DO_NOT_TOUCH: 0.9.1";
            boolean needUpdate = false;
            boolean saveOld = true;

            while ((line = br.readLine()) != null) {
                if (line.equalsIgnoreCase(check)) {
                    needUpdate = true;
                }
                if (line.replaceAll(" ", "").equalsIgnoreCase(
                        "SaveOldConfigs:false")) {
                    saveOld = false;
                }
            }
            br.close();

            if (needUpdate) {
                String string;
                if (saveOld) {
                    string = ChatColor.RED + "" + ChatColor.BOLD
                            + "[DrugMeUp] Config Saved & Regenerated! Update new one to your liking."
                            + ChatColor.RESET;
                    oldDir.mkdir();
                    DateFormat dateFormat = new SimpleDateFormat(
                            "MM-dd-yyyy_HH-mm-ss");
                    Date date = new Date();
                    file.renameTo(new File(oldDir + "\\" + dateFormat.format(date)
                            + ".yml"));
                    saveDefaultConfig();
                } else {
                    string = ChatColor.RED + "" + ChatColor.BOLD
                            + "[DrugMeUp] Config Regenerated! Update new one to your liking."
                            + ChatColor.RESET;
                    config = getConfig();
                    config.set("Options.SaveOldConfigs", false);
                    saveDefaultConfig();
                }
                Bukkit.getConsoleSender().sendMessage(string);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /* Check for config update */
    public boolean isUpdate(UpdateType type) {
        PluginDescriptionFile pdfFile = getDescription();
        if (type == UpdateType.DEFAULT) {
            if (!updater.getLatestName().equalsIgnoreCase(
                    "drugmeup v" + pdfFile.getVersion())) {
                isDownloaded = true;
            }
        } else if (type == UpdateType.NO_DOWNLOAD) {
            Bukkit.getConsoleSender()
                    .sendMessage(
                            ChatColor.RED
                                    + ""
                                    + ChatColor.BOLD
                                    + System.lineSeparator()
                                    + System.lineSeparator()
                                    + "[DrugMeUp] Update Available! "
                                    + System.lineSeparator()
                                    + "Download it at: dev.bukkit.org/server-mods/drugmeup"
                                    + System.lineSeparator() + ChatColor.RESET);
            isUpdate = true;
        }

        return false;
    }

    /* Check if the plugin should be multi-world */
    public boolean isMultiworld() {
        return config.getString("Options.Worlds").split(",").length > 1;
    }

    /* Return the ArrayList<World> of worlds that the plugin is active in */
    public ArrayList<World> getWorlds() {
        return worlds;
    }

    /* Gather the worlds that support this plugin */
    public void gatherWorlds() {
        String[] inConfig = config.getString("Options.Worlds").split(",");
        for (String s : inConfig) {
            worlds.add(Bukkit.getWorld(s));
        }
    }

    /* Get everyone who can't place blocks */
    public List<String> getNoPlace() {
        return noplace;
    }

    /* Get everyone who has scrambled text */
    public List<String> getDrunk() {
        return this.drunk;
    }

    /* Get everyone who is on drugs */
    public List<String> getOnDrugs() {
        return this.onDrugs;
    }

    /* Get everyone who is having a heart attack */
    public List<String> getHeartAttack() {
        return this.heartattack;
    }

    /* Get everyone who has drug-induced jump boost */
    public List<String> getIsJump() {
        return this.isJump;
    }

    /* Check if there's an update */
    public boolean isUpdate() {
        return this.isUpdate;
    }

    /* Check if the update should be automatically downloaded */
    public boolean getIsDownloaded() {
        return this.isDownloaded;
    }

    /* Check if the itemstack is a type of drug */
    public boolean isDrug(ItemStack item) {
        for (Drug drug : drugs.values()) {
            if (drug.getItemStack().getType().name().equalsIgnoreCase(item.getType().name())) {
                return true;
            }
        }
        return false;
    }

    /* Get Drug Instance from ItemStack */
    public Drug getDrug(ItemStack item) {
        for (Drug drug : drugs.values()) {
            if (drug.getItemStack().getType().name().equalsIgnoreCase(item.getType().name())) {
                return drug;
            }
        }
        return null;
    }
}
