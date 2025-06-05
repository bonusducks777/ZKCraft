package com.zkcraft;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.zkcraft.zkcasset.ZKCAsset;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.crypto.Credentials;
import org.web3j.tx.RawTransactionManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.Collections;

public class ZKCraftTrade extends JavaPlugin {
    private FileConfiguration config;
    private File walletFile, inventoryFile, rankFile;
    private FileConfiguration walletData, inventoryData, rankData;
    private Map<UUID, String> playerWallets = new HashMap<>(); // UUID -> Wallet Address
    private Map<UUID, ItemStack> crossServerInventory = new HashMap<>(); // UUID -> Single Item
    private Map<String, String> walletRanks = new HashMap<>(); // Wallet Address -> Rank (YAML fallback)
    private Web3j web3j;
    private ZKCAsset zkcAsset;
    private boolean blockchainEnabled;
    private BukkitRunnable blockchainPoller;
    private boolean setupComplete = false;
    private boolean luckPermsAvailable = false;

    private static final String PLACEHOLDER_RANK = "[BLOCKCHAIN OFFLINE]";
    private static final String PLACEHOLDER_ITEM = "[BLOCKCHAIN OFFLINE]";
    private static final String PLACEHOLDER_WALLET = "[NO WALLET LINKED]";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        // Ensure config values exist
        boolean changed = false;
        if (!config.contains("blockchain.rpc-url")) {
            config.set("blockchain.rpc-url", "https://sepolia.api.zk.netlify.app");
            changed = true;
        }
        if (!config.contains("blockchain.private-key")) {
            config.set("blockchain.private-key", "your-private-key");
            changed = true;
        }
        if (!config.contains("blockchain.zkcasset-contract-address")) {
            config.set("blockchain.zkcasset-contract-address", "0x123...");
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().warning("config.yml was missing required values. Default placeholders have been set. Please update config.yml and reload the plugin.");
        }
        initializeDataFiles();
        initializeBlockchain();
        getCommand("zkc").setExecutor(new ZKCCommandExecutor());
        getCommand("zkc").setTabCompleter(new ZKCTabCompleter());
        startBlockchainPoller();
        if (!setupComplete) {
            runSetupWizard();
        }
        checkForLuckPerms();
        getLogger().info("ZKCraftTrade plugin enabled!");
    }

    @Override
    public void onDisable() {
        saveDataFiles();
        if (web3j != null) {
            web3j.shutdown();
        }
        if (blockchainPoller != null) {
            blockchainPoller.cancel();
        }
        getLogger().info("ZKCraftTrade plugin disabled!");
    }

    private void startBlockchainPoller() {
        if (blockchainPoller != null) blockchainPoller.cancel();
        blockchainPoller = new BukkitRunnable() {
            @Override
            public void run() {
                // Example: poll all player wallets for rank/item updates
                for (Map.Entry<UUID, String> entry : playerWallets.entrySet()) {
                    String wallet = entry.getValue();
                    if (wallet != null) {
                        getAssetFromBlockchain(wallet, "rank");
                        getAssetFromBlockchain(wallet, "item");
                    }
                }
            }
        };
        blockchainPoller.runTaskTimerAsynchronously(this, 0L, 100L); // 100 ticks = 5 seconds
    }

    private void runSetupWizard() {
        displayDeploymentInstructions();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("zkcraft.admin")) {
                    p.sendMessage(ChatColor.AQUA + "[ZKCraftTrade] Setup Wizard: Please check and set your blockchain config values using /zkc config set <key> <value>.");
                    p.sendMessage(ChatColor.AQUA + "Required: rpc_url, contract_address");
                    p.sendMessage(ChatColor.AQUA + "RPC URL should be set to: https://zkrpc.xsollazk.com");
                    p.sendMessage(ChatColor.AQUA + "Contract address should be set to the deployed ZKCAsset address");
                }
            }
        }, 40L);
    }

    private void broadcastBlockchainError(String message) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.RED + "[ZKCraftTrade] Blockchain Error: " + message);
            }
        });
    }

    private void reportBlockchainUnavailable(Player player) {
        player.sendMessage(ChatColor.RED + "[ZKCraftTrade] Blockchain is currently unavailable. Showing placeholder data.");
    }

    private void initializeBlockchain() {
        try {
            String rpcUrl = config.getString("blockchain.rpc-url", "https://sepolia.api.zk.netlify.app");
            String privateKey = config.getString("blockchain.private-key");
            String contractAddress = config.getString("blockchain.zkcasset-contract-address");

            if (privateKey == null || contractAddress == null) {
                broadcastBlockchainError("Missing private-key or zkcasset-contract-address in config.yml");
                blockchainEnabled = false;
                return;
            }

            web3j = Web3j.build(new HttpService(rpcUrl));
            Credentials credentials = Credentials.create(privateKey);
            RawTransactionManager txManager = new RawTransactionManager(web3j, credentials, 1377); // Chain ID for Xsolla ZK
            zkcAsset = ZKCAsset.load(contractAddress, web3j, txManager, new DefaultGasProvider());
            blockchainEnabled = true;
            getLogger().info("Blockchain initialized: Connected to Xsolla ZK Sepolia Testnet");
        } catch (Exception e) {
            broadcastBlockchainError("Failed to initialize blockchain: " + e.getMessage());
            blockchainEnabled = false;
        }
    }

    private void loadPlayerWallets() {
        for (String key : walletData.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            String address = walletData.getString(key + ".address");
            playerWallets.put(uuid, address);
        }
    }

    private void loadCrossServerInventory() {
        crossServerInventory.clear();
        for (String key : inventoryData.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            Object itemObj = inventoryData.get(key + ".item");
            if (itemObj instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) itemObj;
                    ItemStack item = ItemStack.deserialize(itemMap);
                    crossServerInventory.put(uuid, item);
                } catch (Exception e) {
                    getLogger().warning("Failed to deserialize ItemStack for UUID " + key + ": " + e.getMessage());
                }
            }
        }
    }

    private void loadWalletRanks() {
        for (String wallet : rankData.getKeys(false)) {
            String rank = rankData.getString(wallet + ".rank");
            if (rank != null) {
                walletRanks.put(wallet, rank);
            }
        }
    }

    private void applyRank(Player player, String rank) {
        if (rank != null && !rank.isEmpty()) {
            try {
                // Try to use LuckPerms if available
                getServer().dispatchCommand(getServer().getConsoleSender(), "lp user " + player.getName() + " group add " + rank);
                getLogger().info("Applied rank " + rank + " to player " + player.getName() + " via LuckPerms");
            } catch (Exception e) {
                // Fallback for testing without LuckPerms
                getLogger().info("LuckPerms not found or command failed. For testing only: Applied virtual rank " + rank + " to player " + player.getName());
                player.sendMessage("§a[ZKCraftTrade] §7Virtual rank applied: §e" + rank + " §7(LuckPerms not detected)");
                // Store the rank in metadata for display purposes only
                player.setMetadata("zkc_rank", new org.bukkit.metadata.FixedMetadataValue(this, rank));
            }
        }
    }

    private void removeRank(Player player, String rank) {
        if (rank != null && !rank.isEmpty()) {
            try {
                // Try to use LuckPerms if available
                getServer().dispatchCommand(getServer().getConsoleSender(), "lp user " + player.getName() + " group remove " + rank);
                getLogger().info("Removed rank " + rank + " from player " + player.getName() + " via LuckPerms");
            } catch (Exception e) {
                // Fallback for testing without LuckPerms
                getLogger().info("LuckPerms not found or command failed. For testing only: Removed virtual rank " + rank + " from player " + player.getName());
                player.sendMessage("§a[ZKCraftTrade] §7Virtual rank removed: §e" + rank + " §7(LuckPerms not detected)");
                // Remove the metadata
                player.removeMetadata("zkc_rank", this);
            }
        }
    }

    private void mintAsset(String wallet, String assetType, String value) {
        if (!blockchainEnabled) {
            if (assetType.equals("rank")) {
                walletRanks.put(wallet, value);
                rankData.set(wallet + ".rank", value);
            } else if (assetType.equals("item")) {
                // Handled in inventory logic
            }
            saveDataFiles();
            return;
        }
        try {
            zkcAsset.mint(wallet, assetType, value).send();
        } catch (Exception e) {
            getLogger().warning("Failed to mint " + assetType + " on blockchain: " + e.getMessage());
            if (assetType.equals("rank")) {
                walletRanks.put(wallet, value);
                rankData.set(wallet + ".rank", value);
            }
            saveDataFiles();
        }
    }

    private void burnAsset(String wallet, String assetType) {
        if (!blockchainEnabled) {
            if (assetType.equals("rank")) {
                walletRanks.remove(wallet);
                rankData.set(wallet, null);
            } else if (assetType.equals("item")) {
                // Handled in inventory logic
            }
            saveDataFiles();
            return;
        }
        try {
            java.math.BigInteger tokenId = zkcAsset.getTokenId(wallet, assetType).send();
            if (tokenId.intValue() != 0) {
                zkcAsset.burn(tokenId).send();
            }
        } catch (Exception e) {
            getLogger().warning("Failed to burn " + assetType + " from blockchain: " + e.getMessage());
            if (assetType.equals("rank")) {
                walletRanks.remove(wallet);
                rankData.set(wallet, null);
            }
            saveDataFiles();
        }
    }

    private ZKCAsset.Asset getAssetFromBlockchain(String wallet, String assetType) {
        if (!blockchainEnabled) {
            if (assetType.equals("rank")) {
                String rank = walletRanks.get(wallet);
                if (rank != null) {
                    return new ZKCAsset.Asset(assetType, rank);
                } else {
                    return new ZKCAsset.Asset(assetType, PLACEHOLDER_RANK);
                }
            } else if (assetType.equals("item")) {
                return new ZKCAsset.Asset(assetType, PLACEHOLDER_ITEM);
            }
            return null;
        }
        try {
            List<ZKCAsset.Asset> assets = zkcAsset.getWalletAssets(wallet).send();
            for (ZKCAsset.Asset asset : assets) {
                if (asset.assetType.equals(assetType)) {
                    return asset;
                }
            }
            return null;
        } catch (Exception e) {
            getLogger().warning("Failed to fetch " + assetType + " from blockchain: " + e.getMessage());
            if (assetType.equals("rank")) {
                String rank = walletRanks.get(wallet);
                if (rank != null) {
                    return new ZKCAsset.Asset(assetType, rank);
                } else {
                    return new ZKCAsset.Asset(assetType, PLACEHOLDER_RANK);
                }
            } else if (assetType.equals("item")) {
                return new ZKCAsset.Asset(assetType, PLACEHOLDER_ITEM);
            }
            return null;
        }
    }

    // Derive wallet address from private key
    private String deriveAddressFromPrivateKey(String privateKey) {
        try {
            // If the private key starts with "0x", remove it
            if (privateKey.startsWith("0x")) {
                privateKey = privateKey.substring(2);
            }
            
            // Create credentials from the private key
            Credentials credentials = Credentials.create(privateKey);
            
            // Get the address
            String address = credentials.getAddress();
            
            return address;
        } catch (Exception e) {
            getLogger().severe("Error deriving address from private key: " + e.getMessage());
            return null;
        }
    }

    private void checkForLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPermsAvailable = true;
            getLogger().info("LuckPerms detected. Rank management will use LuckPerms.");
        } else {
            luckPermsAvailable = false;
            getLogger().warning("LuckPerms not detected. Rank management will be in 'testing mode' only.");
            getLogger().warning("In testing mode, ranks are only simulated and not actually applied to players.");
            getLogger().warning("Install LuckPerms for full rank functionality in production.");
        }
    }

    // Initialize YAML data files for wallets, inventory, and ranks
    private void initializeDataFiles() {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            walletFile = new File(dataFolder, "wallets.yml");
            if (!walletFile.exists()) walletFile.createNewFile();
            walletData = YamlConfiguration.loadConfiguration(walletFile);

            inventoryFile = new File(dataFolder, "inventory.yml");
            if (!inventoryFile.exists()) inventoryFile.createNewFile();
            inventoryData = YamlConfiguration.loadConfiguration(inventoryFile);

            rankFile = new File(dataFolder, "ranks.yml");
            if (!rankFile.exists()) rankFile.createNewFile();
            rankData = YamlConfiguration.loadConfiguration(rankFile);

            loadPlayerWallets();
            loadCrossServerInventory();
            loadWalletRanks();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize data files: " + e.getMessage());
        }
    }

    // Save all YAML data files
    private void saveDataFiles() {
        try {
            if (walletData != null && walletFile != null) walletData.save(walletFile);
            if (inventoryData != null && inventoryFile != null) inventoryData.save(inventoryFile);
            if (rankData != null && rankFile != null) rankData.save(rankFile);
        } catch (Exception e) {
            getLogger().severe("Failed to save data files: " + e.getMessage());
        }
    }

    // Reload all YAML data files
    private void reloadDataFiles() {
        try {
            if (walletFile != null) walletData = YamlConfiguration.loadConfiguration(walletFile);
            if (inventoryFile != null) inventoryData = YamlConfiguration.loadConfiguration(inventoryFile);
            if (rankFile != null) rankData = YamlConfiguration.loadConfiguration(rankFile);
            loadPlayerWallets();
            loadCrossServerInventory();
            loadWalletRanks();
        } catch (Exception e) {
            getLogger().severe("Failed to reload data files: " + e.getMessage());
        }
    }

    // Display deployment instructions to admins
    private void displayDeploymentInstructions() {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("zkcraft.admin")) {
                    p.sendMessage(ChatColor.GOLD + "[ZKCraftTrade] Blockchain Setup Instructions:");
                    p.sendMessage(ChatColor.YELLOW + "1. Deploy the ZKCAsset smart contract to your zkSync-compatible network.");
                    p.sendMessage(ChatColor.YELLOW + "2. Set the contract address in config.yml under blockchain.zkcasset-contract-address.");
                    p.sendMessage(ChatColor.YELLOW + "3. Set your RPC URL and private key in config.yml.");
                    p.sendMessage(ChatColor.YELLOW + "4. Reload the plugin with /zkc reload or restart the server.");
                }
            }
        });
    }

    class ZKCCommandExecutor implements TabExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players!");
                return true;
            }
            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage("Usage: /zkc <wallet|rank|inventory|reload|info>");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "wallet":
                    handleWalletCommands(player, args);
                    break;
                case "rank":
                    handleRankCommands(player, args);
                    break;
                case "inventory":
                    handleInventoryCommands(player, args);
                    break;
                case "reload":
                    if (player.hasPermission("zkcraft.admin")) {
                        reloadConfig();
                        reloadDataFiles();
                        initializeBlockchain();
                        player.sendMessage("Plugin configuration and data reloaded!");
                    } else {
                        player.sendMessage("You don't have permission!");
                    }
                    break;
                case "info":
                    if (player.hasPermission("zkcraft.admin")) {
                        player.sendMessage("Wallets: " + playerWallets);
                        player.sendMessage("Inventory: " + crossServerInventory);
                        player.sendMessage("Ranks: " + walletRanks);
                        player.sendMessage("Blockchain Enabled: " + blockchainEnabled);
                    } else {
                        player.sendMessage("You don't have permission!");
                    }
                    break;
                case "config":
                    handleConfigCommands(player, args);
                    break;
                case "probe":
                    handleProbeCommand(player, args);
                    break;
                case "pausepoll":
                    handlePausePollCommand(player);
                    break;
                case "resumepoll":
                    handleResumePollCommand(player);
                    break;
                default:
                    player.sendMessage("Unknown subcommand! Use: /zkc <wallet|rank|inventory|reload|info>");
            }
            return true;
        }

        private void handleWalletCommands(Player player, String[] args) {
            if (args.length < 2) {
                player.sendMessage("Usage: /zkc wallet <link|unlink>");
                return;
            }
            UUID uuid = player.getUniqueId();
            switch (args[1].toLowerCase()) {
                case "link":
                    if (args.length < 3) {
                        player.sendMessage("Usage: /zkc wallet link <privateKey>");
                        return;
                    }
                    String privateKey = args[2];
                    String address = deriveAddressFromPrivateKey(privateKey);
                    if (address == null) {
                        player.sendMessage("Invalid private key format. Please check your private key and try again.");
                        return;
                    }
                    playerWallets.put(uuid, address);
                    walletData.set(uuid.toString() + ".address", address);
                    saveDataFiles();
                    player.sendMessage("Wallet linked successfully!");
                    player.sendMessage("Derived address: " + address);
                    if (!blockchainEnabled) reportBlockchainUnavailable(player);
                    break;
                case "unlink":
                    String wallet = playerWallets.remove(uuid);
                    if (wallet != null) {
                        ZKCAsset.Asset rank = getAssetFromBlockchain(wallet, "rank");
                        if (rank != null) {
                            burnAsset(wallet, "rank");
                            removeRank(player, rank.value);
                        }
                        ZKCAsset.Asset item = getAssetFromBlockchain(wallet, "item");
                        if (item != null) {
                            burnAsset(wallet, "item");
                        }
                        walletRanks.remove(wallet);
                        rankData.set(wallet, null);
                        crossServerInventory.remove(uuid);
                        inventoryData.set(uuid.toString(), null);
                    }
                    walletData.set(uuid.toString(), null);
                    saveDataFiles();
                    player.sendMessage("Wallet unlinked!");
                    break;
                default:
                    player.sendMessage("Usage: /zkc wallet <link|unlink>");
            }
        }

        private void handleRankCommands(Player player, String[] args) {
            if (args.length < 2) {
                player.sendMessage("Usage: /zkc rank <assign|remove|sync|check|list>");
                return;
            }
            UUID uuid = player.getUniqueId();
            String wallet = playerWallets.get(uuid);
            switch (args[1].toLowerCase()) {
                case "assign":
                    if (!player.hasPermission("zkcraft.admin")) {
                        player.sendMessage("You don't have permission!");
                        return;
                    }
                    if (args.length < 4) {
                        player.sendMessage("Usage: /zkc rank assign <player> <rank>");
                        return;
                    }
                    Player target = getServer().getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage("Player not found!");
                        return;
                    }
                    String targetWallet = playerWallets.get(target.getUniqueId());
                    if (targetWallet == null) {
                        player.sendMessage("Target player has no linked wallet!");
                        return;
                    }
                    String rank = args[3];
                    ZKCAsset.Asset existingRank = getAssetFromBlockchain(targetWallet, "rank");
                    if (existingRank != null) {
                        player.sendMessage("Player already has a rank: " + existingRank.value);
                        return;
                    }
                    mintAsset(targetWallet, "rank", rank);
                    applyRank(target, rank);
                    player.sendMessage("Assigned rank " + rank + " to " + args[2] + "'s wallet (" + targetWallet + ")");
                    break;
                case "remove":
                    if (!player.hasPermission("zkcraft.admin")) {
                        player.sendMessage("You don't have permission!");
                        return;
                    }
                    if (args.length < 3) {
                        player.sendMessage("Usage: /zkc rank remove <player>");
                        return;
                    }
                    Player removeTarget = getServer().getPlayer(args[2]);
                    if (removeTarget == null) {
                        player.sendMessage("Player not found!");
                        return;
                    }
                    String removeWallet = playerWallets.get(removeTarget.getUniqueId());
                    if (removeWallet == null) {
                        player.sendMessage("Target player has no linked wallet!");
                        return;
                    }
                    ZKCAsset.Asset rankAsset = getAssetFromBlockchain(removeWallet, "rank");
                    if (rankAsset == null) {
                        player.sendMessage("Player has no rank!");
                        return;
                    }
                    burnAsset(removeWallet, "rank");
                    removeRank(removeTarget, rankAsset.value);
                    player.sendMessage("Removed rank " + rankAsset.value + " from " + args[2] + "'s wallet (" + removeWallet + ")");
                    break;
                case "sync":
                    if (wallet == null) {
                        player.sendMessage("You must link a wallet first! Use /zkc wallet link <privateKey>");
                        return;
                    }
                    ZKCAsset.Asset rank = getAssetFromBlockchain(wallet, "rank");
                    if (rank != null) {
                        applyRank(player, rank.value);
                        player.sendMessage("Rank synced: " + rank.value);
                    } else {
                        player.sendMessage("No rank found for your wallet!");
                    }
                    break;
                case "check":
                    if (args.length < 3) {
                        player.sendMessage("Usage: /zkc rank check <player>");
                        return;
                    }
                    Player checkTarget = getServer().getPlayer(args[2]);
                    if (checkTarget == null) {
                        player.sendMessage("Player not found!");
                        return;
                    }
                    String checkWallet = playerWallets.get(checkTarget.getUniqueId());
                    if (checkWallet == null) {
                        player.sendMessage("Player has no linked wallet!");
                        return;
                    }
                    ZKCAsset.Asset checkRank = getAssetFromBlockchain(checkWallet, "rank");
                    if (!blockchainEnabled) reportBlockchainUnavailable(player);
                    if (checkRank != null) {
                        player.sendMessage(args[2] + "'s rank (" + checkWallet + "): " + checkRank.value);
                    } else {
                        player.sendMessage(args[2] + " has no rank!");
                    }
                    break;
                case "list":
                    if (wallet == null) {
                        player.sendMessage("You must link a wallet first! Use /zkc wallet link <privateKey>");
                        return;
                    }
                    ZKCAsset.Asset myRank = getAssetFromBlockchain(wallet, "rank");
                    if (!blockchainEnabled) reportBlockchainUnavailable(player);
                    if (myRank != null) {
                        player.sendMessage("Your rank (" + wallet + "): " + myRank.value);
                    } else {
                        player.sendMessage("You have no rank!");
                    }
                    break;
                default:
                    player.sendMessage("Usage: /zkc rank <assign|remove|sync|check|list>");
            }
        }

        private void handleInventoryCommands(Player player, String[] args) {
            if (args.length < 2) {
                player.sendMessage("Usage: /zkc inventory <set|get|view>");
                return;
            }
            UUID uuid = player.getUniqueId();
            String wallet = playerWallets.get(uuid);
            if (wallet == null) {
                player.sendMessage("You must link a wallet first! Use /zkc wallet link <privateKey>");
                return;
            }
            PlayerInventory inventory = player.getInventory();
            switch (args[1].toLowerCase()) {
                case "set":
                    Bukkit.getScheduler().runTaskAsynchronously(ZKCraftTrade.this, () -> {
                        ZKCAsset.Asset existingItem = getAssetFromBlockchain(wallet, "item");
                        if (existingItem != null) {
                            player.sendMessage("You already have an item stored: " + existingItem.value);
                            return;
                        }
                        ItemStack item = inventory.getItemInMainHand();
                        if (item == null || item.getType().isAir()) {
                            player.sendMessage("You must hold an item in your main hand!");
                            return;
                        }
                        String itemValue = item.getType().toString();
                        mintAsset(wallet, "item", itemValue);
                        crossServerInventory.put(uuid, item.clone());
                        inventoryData.set(uuid.toString() + ".item", item.serialize());
                        Bukkit.getScheduler().runTask(ZKCraftTrade.this, () -> {
                            inventory.setItemInMainHand(null);
                            saveDataFiles();
                            player.sendMessage("Item stored as NFT: " + itemValue);
                        });
                    });
                    break;
                case "get":
                    Bukkit.getScheduler().runTaskAsynchronously(ZKCraftTrade.this, () -> {
                        ZKCAsset.Asset itemAsset = getAssetFromBlockchain(wallet, "item");
                        if (itemAsset == null) {
                            player.sendMessage("No item stored in your inventory slot!");
                            return;
                        }
                        ItemStack storedItem = crossServerInventory.get(uuid);
                        if (storedItem != null) {
                            Bukkit.getScheduler().runTask(ZKCraftTrade.this, () -> {
                                inventory.addItem(storedItem.clone());
                                burnAsset(wallet, "item");
                                crossServerInventory.remove(uuid);
                                inventoryData.set(uuid.toString(), null);
                                saveDataFiles();
                                player.sendMessage("Retrieved item: " + itemAsset.value);
                            });
                        } else {
                            player.sendMessage("Item not found in local storage! Try syncing on another server.");
                        }
                    });
                    break;
                case "view":
                    ZKCAsset.Asset viewItem = getAssetFromBlockchain(wallet, "item");
                    if (!blockchainEnabled) reportBlockchainUnavailable(player);
                    if (viewItem != null) {
                        player.sendMessage("Stored item: " + viewItem.value);
                    } else {
                        player.sendMessage("No item stored in your inventory slot!");
                    }
                    break;
                default:
                    player.sendMessage("Usage: /zkc inventory <set|get|view>");
            }
        }

        private void handleConfigCommands(Player player, String[] args) {
            if (!player.hasPermission("zkcraft.admin")) {
                player.sendMessage("You don't have permission!");
                return;
            }
            if (args.length < 2) {
                player.sendMessage("Usage: /zkc config <set|view> [key] [value]");
                return;
            }
            switch (args[1].toLowerCase()) {
                case "set":
                    if (args.length < 4) {
                        player.sendMessage("Usage: /zkc config set <key> <value>");
                        return;
                    }
                    String key = args[2];
                    String value = args[3];
                    // Type checking for known keys
                    if (key.equals("blockchain.rpc-url") && !value.startsWith("http")) {
                        player.sendMessage(ChatColor.RED + "rpc-url must be a valid URL");
                        return;
                    }
                    if (key.equals("blockchain.zkcasset-contract-address") && !value.startsWith("0x")) {
                        player.sendMessage(ChatColor.RED + "Contract address must start with 0x");
                        return;
                    }
                    config.set(key, value);
                    saveConfig();
                    player.sendMessage("Config value set: " + key + " = " + value);
                    break;
                case "view":
                    player.sendMessage("Current config values:");
                    for (String k : config.getKeys(true)) {
                        Object v = config.get(k);
                        player.sendMessage(k + ": " + v);
                    }
                    break;
                default:
                    player.sendMessage("Usage: /zkc config <set|view> [key] [value]");
            }
        }

        private void handleProbeCommand(Player player, String[] args) {
            if (args.length < 2) {
                player.sendMessage("Usage: /zkc probe <self|player> [playerName] [rank|item]");
                return;
            }
            String targetType = args[1].toLowerCase();
            if (targetType.equals("self")) {
                String wallet = playerWallets.get(player.getUniqueId());
                if (wallet == null) {
                    player.sendMessage("You must link a wallet first!");
                    return;
                }
                ZKCAsset.Asset rank = getAssetFromBlockchain(wallet, "rank");
                ZKCAsset.Asset item = getAssetFromBlockchain(wallet, "item");
                if (!blockchainEnabled) reportBlockchainUnavailable(player);
                player.sendMessage("On-chain rank: " + (rank != null ? rank.value : PLACEHOLDER_RANK));
                player.sendMessage("On-chain item: " + (item != null ? item.value : PLACEHOLDER_ITEM));
            } else if (targetType.equals("player") && args.length >= 3) {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("Player not found!");
                    return;
                }
                String wallet = playerWallets.get(target.getUniqueId());
                if (wallet == null) {
                    player.sendMessage("Target player has no linked wallet!");
                    return;
                }
                String assetType = (args.length >= 4) ? args[3].toLowerCase() : "all";
                if (!blockchainEnabled) reportBlockchainUnavailable(player);
                if (assetType.equals("rank") || assetType.equals("all")) {
                    ZKCAsset.Asset rank = getAssetFromBlockchain(wallet, "rank");
                    player.sendMessage(target.getName() + " on-chain rank: " + (rank != null ? rank.value : PLACEHOLDER_RANK));
                }
                if (assetType.equals("item") || assetType.equals("all")) {
                    ZKCAsset.Asset item = getAssetFromBlockchain(wallet, "item");
                    player.sendMessage(target.getName() + " on-chain item: " + (item != null ? item.value : PLACEHOLDER_ITEM));
                }
            } else {
                player.sendMessage("Usage: /zkc probe <self|player> [playerName] [rank|item]");
            }
        }

        private void handlePausePollCommand(Player player) {
            if (!player.hasPermission("zkcraft.admin")) {
                player.sendMessage("You don't have permission!");
                return;
            }
            if (blockchainPoller != null) {
                blockchainPoller.cancel();
                blockchainPoller = null;
                player.sendMessage(ChatColor.YELLOW + "Blockchain polling paused.");
            } else {
                player.sendMessage(ChatColor.RED + "Blockchain polling is already paused.");
            }
        }
        private void handleResumePollCommand(Player player) {
            if (!player.hasPermission("zkcraft.admin")) {
                player.sendMessage("You don't have permission!");
                return;
            }
            if (blockchainPoller == null) {
                startBlockchainPoller();
                player.sendMessage(ChatColor.GREEN + "Blockchain polling resumed.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Blockchain polling is already running.");
            }
        }
    }

    class ZKCTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                List<String> subs = new ArrayList<>();
                Collections.addAll(subs, "wallet", "rank", "inventory", "reload", "info", "config", "probe", "pausepoll", "resumepoll");
                return filterPrefix(subs, args[0]);
            }
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("wallet")) {
                    return filterPrefix(new ArrayList<>(List.of("link", "unlink", "sync", "list")), args[1]);
                }
                if (args[0].equalsIgnoreCase("config")) {
                    return filterPrefix(new ArrayList<>(List.of("set", "view")), args[1]);
                }
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("probe")) {
                return filterPrefix(new ArrayList<>(List.of("self", "player")), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("probe") && args[1].equalsIgnoreCase("player")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filterPrefix(names, args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("probe") && args[1].equalsIgnoreCase("player")) {
                return filterPrefix(new ArrayList<>(List.of("rank", "item", "all")), args[3]);
            }
            return Collections.emptyList();
        }
        private List<String> filterPrefix(List<String> options, String prefix) {
            if (prefix == null || prefix.isEmpty()) return options;
            List<String> out = new ArrayList<>();
            for (String s : options) if (s.toLowerCase().startsWith(prefix.toLowerCase())) out.add(s);
            return out;
        }
    }
}