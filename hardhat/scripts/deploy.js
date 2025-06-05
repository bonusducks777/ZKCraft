const { ethers } = require("hardhat");

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying contracts with the account:", deployer.address);
  console.log("Account balance:", (await ethers.provider.getBalance(deployer.address)).toString());

  const ZKCAsset = await ethers.getContractFactory("ZKCAsset");
  const zkcAsset = await ZKCAsset.deploy(deployer.address);
  await zkcAsset.deployed();

  console.log("ZKCAsset deployed to:", zkcAsset.address);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});