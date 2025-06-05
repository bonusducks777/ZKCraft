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
6. **Install LuckPerms** for rank management.
7. **Start the server.**
8. **Complete setup in-game:**
   - If config values are missing, a setup wizard will prompt admins in-game.
   - Use `/zkc config set blockchain.rpc-url <url>`, `/zkc config set blockchain.private-key <key>`, and `/zkc config set blockchain.zkcasset-contract-address <address>` to set required values.
   - Use `/zkc reload` to reload config and initialize blockchain connection.
   - If blockchain connection fails, all players will be notified.

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

## Testing
1. Start a Spigot server with the plugin and LuckPerms.
2. Link a wallet: `/zkc wallet link 0x123...`
3. Assign a rank: `/zkc rank assign <player> VIP`
4. Check rank: `/zkc rank check <player>`
5. Sync rank: `/zkc rank sync`
6. Store an item: `/zkc inventory set` (hold item in main hand)
7. Retrieve item: `/zkc inventory get`
8. Test cross-server sync by joining another server with the same plugin and using `/zkc rank sync` or `/zkc inventory get`.
9. Use `/zkc probe` commands to check on-chain status.

## Notes
- Ranks and items are ERC-721 NFTs in the `ZKCAsset` contract, with YAML fallback for testing.
- Each server queries the blockchain independently, ensuring decentralized synchronization.
- Inventory enforces single-slot rules: items are removed from the main hand when stored, added to the inventory when retrieved, and cannot be stored/retrieved if the slot is occupied/empty.
- Requires LuckPerms for rank application.
- Complies with Xsolla ZK bounty's no-gambling rule.

## Troubleshooting
- If blockchain connection fails, all players will be notified in-game.
- Use `/zkc config view` to check current config values.
- Use `/zkc reload` after changing config values.
- If setup wizard appears, follow its instructions to complete setup.

## Future Work
- Add QR code generation for wallet actions.
- Support item metadata (e.g., enchantments) in NFTs.
- Develop an optional React frontend for wallet management.