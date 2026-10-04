package com.example.payments

/**
 * Crypto assets accepted through CryptoBot (Telegram Crypto Pay) and the
 * blockchain networks each of them can be funded from.
 *
 * The list mirrors `server/src/networks.js`; the gateway remains the source of
 * truth and overrides it at runtime via `/api/catalog`, but these defaults keep
 * the picker usable offline.
 */

data class CryptoNetwork(
    val id: String,
    val title: String,
    val short: String,
    val colorHex: Long,
    val explorer: String,
    val minConfirmations: Int
)

data class CryptoAsset(
    val symbol: String,
    val name: String,
    val colorHex: Long,
    val decimals: Int,
    val networks: List<CryptoNetwork>,
    val defaultNetworkId: String
) {
    fun defaultNetwork(): CryptoNetwork =
        networks.firstOrNull { it.id == defaultNetworkId } ?: networks.first()
}

object CryptoCatalog {

    val TRON = CryptoNetwork("TRON", "Tron (TRC-20)", "TRC-20", 0xFFEF0027, "https://tronscan.org/#/transaction/", 19)
    val TON = CryptoNetwork("TON", "The Open Network", "TON", 0xFF0098EA, "https://tonviewer.com/transaction/", 1)
    val ETH = CryptoNetwork("ETH", "Ethereum (ERC-20)", "ERC-20", 0xFF627EEA, "https://etherscan.io/tx/", 12)
    val BSC = CryptoNetwork("BSC", "BNB Smart Chain", "BEP-20", 0xFFF3BA2F, "https://bscscan.com/tx/", 15)
    val SOLANA = CryptoNetwork("SOLANA", "Solana (SPL)", "SPL", 0xFF14F195, "https://solscan.io/tx/", 1)
    val BITCOIN = CryptoNetwork("BITCOIN", "Bitcoin", "BTC", 0xFFF7931A, "https://mempool.space/tx/", 2)
    val LITECOIN = CryptoNetwork("LITECOIN", "Litecoin", "LTC", 0xFF345D9D, "https://blockchair.com/litecoin/transaction/", 6)
    val POLYGON = CryptoNetwork("POLYGON", "Polygon PoS", "POLYGON", 0xFF8247E5, "https://polygonscan.com/tx/", 128)

    val networksById: Map<String, CryptoNetwork> =
        listOf(TRON, TON, ETH, BSC, SOLANA, BITCOIN, LITECOIN, POLYGON).associateBy { it.id }

    val assets: List<CryptoAsset> = listOf(
        CryptoAsset("USDT", "Tether USD", 0xFF26A17B, 2, listOf(TRON, TON, ETH, BSC, SOLANA, POLYGON), "TRON"),
        CryptoAsset("USDC", "USD Coin", 0xFF2775CA, 2, listOf(ETH, SOLANA, TRON, BSC, POLYGON), "ETH"),
        CryptoAsset("TON", "Toncoin", 0xFF0098EA, 4, listOf(TON), "TON"),
        CryptoAsset("TRX", "Tron", 0xFFEF0027, 2, listOf(TRON), "TRON"),
        CryptoAsset("BTC", "Bitcoin", 0xFFF7931A, 8, listOf(BITCOIN), "BITCOIN"),
        CryptoAsset("ETH", "Ethereum", 0xFF627EEA, 6, listOf(ETH), "ETH"),
        CryptoAsset("SOL", "Solana", 0xFF14F195, 4, listOf(SOLANA), "SOLANA"),
        CryptoAsset("BNB", "BNB", 0xFFF3BA2F, 5, listOf(BSC), "BSC"),
        CryptoAsset("LTC", "Litecoin", 0xFF345D9D, 5, listOf(LITECOIN), "LITECOIN")
    )

    fun asset(symbol: String): CryptoAsset? =
        assets.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }

    fun network(id: String?): CryptoNetwork? = id?.let { networksById[it.uppercase()] }

    fun isAllowed(symbol: String, networkId: String): Boolean =
        asset(symbol)?.networks?.any { it.id.equals(networkId, ignoreCase = true) } == true

    /** Explorer deep link for a confirmed transfer. */
    fun explorerUrl(networkId: String, txHash: String): String? =
        network(networkId)?.let { it.explorer + txHash }
}
