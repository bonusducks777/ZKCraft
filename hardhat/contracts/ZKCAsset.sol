// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract ZKCAsset is ERC721, Ownable {
    struct Asset {
        string assetType;
        string value;
    }

    uint256 private _nextTokenId;
    mapping(uint256 => Asset) public assets;
    mapping(address => uint256[]) private _walletAssets;

    constructor(address initialOwner) ERC721("ZKCraftAsset", "ZKC") Ownable(initialOwner) {
        _nextTokenId = 1;
    }

    function mint(address to, string memory assetType, string memory value) public onlyOwner {
        uint256 tokenId = _nextTokenId++;
        _safeMint(to, tokenId);
        assets[tokenId] = Asset(assetType, value);
        _walletAssets[to].push(tokenId);
    }

    function exists(uint256 tokenId) internal view returns (bool) {
        return _ownerOf(tokenId) != address(0);
    }

    function burn(uint256 tokenId) public onlyOwner {
        require(exists(tokenId), "Token does not exist");
        address owner = ownerOf(tokenId);
        _burn(tokenId);
        delete assets[tokenId];
        uint256[] storage tokens = _walletAssets[owner];
        for (uint256 i = 0; i < tokens.length; i++) {
            if (tokens[i] == tokenId) {
                tokens[i] = tokens[tokens.length - 1];
                tokens.pop();
                break;
            }
        }
    }

    function getWalletAssets(address wallet) public view returns (Asset[] memory) {
        uint256[] memory tokenIds = _walletAssets[wallet];
        Asset[] memory result = new Asset[](tokenIds.length);
        for (uint256 i = 0; i < tokenIds.length; i++) {
            result[i] = assets[tokenIds[i]];
        }
        return result;
    }

    function getTokenId(address wallet, string memory assetType) public view returns (uint256) {
        uint256[] memory tokenIds = _walletAssets[wallet];
        for (uint256 i = 0; i < tokenIds.length; i++) {
            if (keccak256(abi.encodePacked(assets[tokenIds[i]].assetType)) == keccak256(abi.encodePacked(assetType))) {
                return tokenIds[i];
            }
        }
        return 0;
    }
}