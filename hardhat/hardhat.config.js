require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

module.exports = {
  networks: {
    xsollaZkSepoliaTestnet: {
      url: "https://zkrpc.xsollazk.com",
      accounts: [process.env.PRIVATE_KEY],
      chainId: 555272
    },
  },
  solidity: "0.8.28",
};