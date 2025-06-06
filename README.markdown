# ZKCraftTrade Plugin

## Overview
ZKCraftTrade is a Minecraft Spigot plugin for managing player ranks and a single cross-server inventory slot as ERC-721 NFTs on the Xsolla ZK Sepolia Testnet. Each server independently queries the blockchain for rank and item NFTs, ensuring decentralized synchronization. Ranks and items are player-owned assets stored in wallets.

## Features
- **Wallet Management**: Link/unlink wallets to player UUIDs.
- **Rank Management**: Assign, remove, sync, check, and list ranks as NFTs.
- **Single Inventory Slot**: Store one item from the main hand as an NFT, retrieve it to the inventory, with strict single-slot enforcement.
- **Decentralized Sync**: Each server queries the `ZKCAsset` contract independently.
- **Blockchain Integration**: Web3j bundled in the `.jar` for direct contract calls.
- **Config Management**: In-game commands to set/view config values.
- **Setup Wizard**: Guides admins through initial setup if config is incomplete.
- **Blockchain Polling**: Probes the blockchain every 5 seconds for updates.
- **Manual Probing**: Commands to manually probe blockchain for rank/item status.
- **Tab Completion**: All commands support tab completion.
- **Pause/Resume Polling:** `/zkc pausepoll` and `/zkc resumepoll` allow admins to pause and resume blockchain polling at any time.

## Installation & Setup Guide
1. **Clone the repository:**
   ```sh
   git clone <repo-url>
   ```
2. **Deploy the `ZKCAsset.sol` contract:**
   - Install Hardhat and dependencies: `npm install`
   - Configure `hardhat.config.js` with your private key
   - Run: `npx hardhat run scripts/deploy.js --network xsollaZkSepolia`
   - Note the contract address
3. **Generate Web3j bindings:**
   ```sh
   web3j generate solidity --abiFile=ZKCAsset.abi --binFile=ZKCAsset.bin --outputDir=plugin/src/main/java --package=com.zkcraft.zkcasset
   ```
4. **Build the plugin:**
   ```sh
   cd plugin && mvn clean package
   ```
5. **Copy the plugin JAR:**
   - Copy `plugin/target/ZKCraftTrade-1.0-SNAPSHOT.jar` to your Spigot server's `plugins` folder.
6. **Install LuckPerms** for rank management in production (optional for testing).
7. **Start the server.**
8. **Complete setup in-game:**
   - If config values are missing, a setup wizard will prompt admins in-game.
   - Use `/zkc config set blockchain.rpc-url <url>`, `/zkc config set blockchain.private-key <key>`, and `/zkc config set blockchain.zkcasset-contract-address <address>` to set required values.
   - Use `/zkc reload` to reload config and initialize blockchain connection.
   - If blockchain connection fails, all players will be notified.

## Step-by-Step Plugin Compilation and Deployment

### 1. Deploy the Smart Contract

You need to deploy the ZKCAsset contract to the zkxsolla network:

```powershell
# Navigate to the smart contracts directory
cd xsolla-zk-smart-contracts-starter-kit

# Install dependencies (if not done already)
npm install

# Compile the contracts
npx hardhat compile

# Deploy to zkxsolla network
npx hardhat run scripts\deploy.js --network zkxsolla
```

The deployment script will output the contract address. Save this address - you'll need it for the plugin configuration.

### 2. Build the Plugin

```powershell
# Navigate to the plugin directory
cd ..\plugin

# Build with Maven
mvn clean package
```

This will create a JAR file in the `target` folder, typically named `ZKCraftTrade-1.0-SNAPSHOT.jar`.

### 3. Install and Configure

1. Copy the JAR file to your Spigot server's `plugins` folder
2. Start the server once to generate the config.yml file
3. Stop the server
4. Edit the `plugins/ZKCraftTrade/config.yml` file to set:
   ```yaml
   rpc_url: "https://zkrpc.xsollaz.com"
   contract_address: "0xYOUR_DEPLOYED_CONTRACT_ADDRESS"
   ```
5. Restart the server

### 4. Testing in-game

Log in and use the plugin commands to test functionality:
- Link a wallet with `/zkc wallet link <privateKey>`
- Assign a rank with `/zkc rank assign <player> <rankName>`
- Check ranks with `/zkc rank check <player>`

The plugin will work for testing without LuckPerms installed. For full functionality in a production environment, install the LuckPerms plugin.

## Building and Deploying

### 1. Deploy the Smart Contract

Before building the plugin JAR, you need to deploy the ZKCAsset smart contract:

```bash
cd xsolla-zk-smart-contracts-starter-kit
npm install
npx hardhat compile
npx hardhat run scripts/deploy-zkcasset.js --network zkxsolla
```

Take note of the deployed contract address that will be displayed in the console.

### 2. Build the Plugin

```bash
cd plugin
mvn clean package
```

This will create a JAR file in the `target` folder.

### 3. Installation

1. Copy the JAR file to your Spigot server's `plugins` folder
2. Start the server to generate the config.yml file
3. Update the config.yml with your smart contract address:
   ```yaml
   rpc_url: "https://zkrpc.xsollaz.com"
   contract_address: "0xYOUR_DEPLOYED_CONTRACT_ADDRESS"
   ```
4. Restart the server or use `/zkc reload` to apply changes

### Smart Contract Architecture

The ZKCAsset contract stores the following information:
- Player ranks (as NFTs with type "rank" and value corresponding to the rank name)
- Player inventory items (as NFTs with type "item" and value containing serialized item data)

Each player wallet can have multiple NFTs associated with it, each representing a different asset type.

## Usage
- **Wallet:** `/zkc wallet <link|unlink>`
- **Rank:** `/zkc rank <assign|remove|sync|check|list>`
- **Inventory:** `/zkc inventory <set|get|view>`
- **Admin:** `/zkc reload`, `/zkc info`, `/zkc config <set|view>`, `/zkc probe <self|player> [playerName] [rank|item]`, `/zkc pausepoll`, `/zkc resumepoll`

### Tab Completion
All commands and subcommands support tab completion for easier use, including pause/resume polling.

### Blockchain Polling
- The plugin automatically probes the blockchain every 5 seconds for all linked wallets.
- You can pause/resume polling with `/zkc pausepoll` and `/zkc resumepoll` (admin only).
- You can manually probe with `/zkc probe self` or `/zkc probe player <playerName> [rank|item]`.

## Wallet Linking

### New Private Key Workflow
ZKCraftTrade now uses a secure private key workflow for wallet linking:

1. Players provide their private key using: `/zkc wallet link <privateKey>`
2. The plugin derives the public wallet address from the private key
3. The derived address is displayed to the player and linked to their account
4. The private key is not stored, only the derived public address

### Security Notes
- Private keys should never be shared publicly
- The plugin only uses the private key at the moment of linking to derive the address
- The plugin does not store the private key, only the derived public address
- For maximum security, consider using a dedicated wallet for game interactions

### Commands
- Link wallet: `/zkc wallet link <privateKey>`
- Unlink wallet: `/zkc wallet unlink`
- Sync wallet assets: `/zkc wallet sync`
- List wallet assets: `/zkc wallet list`

## Testing
1. Start a Spigot server with the plugin (and LuckPerms if you want full rank functionality).
2. Link a wallet: `/zkc wallet link <privateKey>` (plugin will derive and display your wallet address)
3. Assign a rank: `/zkc rank assign <player> VIP`
4. Check rank: `/zkc rank check <player>`
5. Sync rank: `/zkc rank sync`
6. Store an item: `/zkc inventory set` (hold item in main hand)
7. Retrieve item: `/zkc inventory get`
8. Test cross-server sync by joining another server with the same plugin and using `/zkc rank sync` or `/zkc inventory get`.
9. Use `/zkc probe` commands to check on-chain status.

## Testing Without LuckPerms

The plugin can now run in a "testing mode" without LuckPerms installed:

1. In testing mode, rank management still works, but ranks are only simulated visually:
   - When a rank is applied, a message will be shown to the player
   - The rank will be stored in player metadata
   - The rank will be tracked on the blockchain as usual
   - LuckPerms commands will not be executed

2. The plugin automatically detects if LuckPerms is available:
   - If found, full rank functionality is enabled (recommended for production)
   - If not found, the plugin switches to testing mode automatically

This makes it easier to test the plugin's blockchain functionality without setting up LuckPerms first.

## Notes
- Ranks and items are ERC-721 NFTs in the `ZKCAsset` contract, with YAML fallback for testing.
- Each server queries the blockchain independently, ensuring decentralized synchronization.
- Inventory enforces single-slot rules: items are removed from the main hand when stored, added to the inventory when retrieved, and cannot be stored/retrieved if the slot is occupied/empty.
- LuckPerms is recommended for rank application in production, but the plugin has a testing mode that works without it.
- Complies with Xsolla ZK bounty's no-gambling rule.

## Troubleshooting
- If blockchain connection fails, all players will be notified in-game.
- Use `/zkc config view` to check current config values.
- Use `/zkc reload` after changing config values.
- If setup wizard appears, follow its instructions to complete setup.

## Troubleshooting Contract Deployment

If you encounter issues with contract deployment:

1. **RPC URL Issues**
   - Verify the RPC URL in `hardhat.config.ts` is correct
   - Check that you're connected to the internet and the RPC endpoint is accessible

2. **Private Key Setup**
   - Make sure your private key is set in the `.env` file (never commit this file to version control)
   - The account needs to have enough funds for deployment

3. **Manual Contract Verification**
   - If you need to manually deploy the contract, you can use these steps:
     ```powershell
     # Set environment variables (replace with your actual private key)
     $env:MAINNET_PRIVATE_KEY = "0xYourPrivateKeyHere"
     
     # Use the deployment script in zk-deploy
     cd xsolla-zk-smart-contracts-starter-kit
     npx hardhat deploy-zksync-zk --script deployZKCAsset.ts --network zkxsolla
     ```

4. **Testing Connection**
   - Use the debug mode in the plugin's config.yml to see detailed logs
   - Check your server logs for any connection errors

## Future Work
- Add QR code generation for wallet actions.
- Support item metadata (e.g., enchantments) in NFTs.
- Develop an optional React frontend for wallet management.