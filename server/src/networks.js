/**
 * Catalogue of crypto assets and the blockchain networks they can be paid on
 * through CryptoBot (Crypto Pay API).
 *
 * Crypto Pay settles invoices inside the @CryptoBot wallet, so the *network* is
 * the rail the user tops up / withdraws with. We expose it explicitly because
 * users pay from external wallets and must not send, say, TRC-20 USDT to a TON
 * address. The chosen network travels with the invoice `payload` and is echoed
 * back by the webhook, so the receipt always states the exact rail used.
 */

/** @typedef {{ id: string, title: string, short: string, color: string, explorer: string, minConfirmations: number, txRegex: string }} Network */

export const NETWORKS = {
  TRON: {
    id: 'TRON',
    title: 'Tron (TRC-20)',
    short: 'TRC-20',
    color: '#EF0027',
    explorer: 'https://tronscan.org/#/transaction/',
    minConfirmations: 19,
    txRegex: '^[0-9a-fA-F]{64}$',
  },
  TON: {
    id: 'TON',
    title: 'The Open Network',
    short: 'TON',
    color: '#0098EA',
    explorer: 'https://tonviewer.com/transaction/',
    minConfirmations: 1,
    txRegex: '^[A-Za-z0-9_\\-+/=]{44,64}$',
  },
  ETH: {
    id: 'ETH',
    title: 'Ethereum (ERC-20)',
    short: 'ERC-20',
    color: '#627EEA',
    explorer: 'https://etherscan.io/tx/',
    minConfirmations: 12,
    txRegex: '^0x[0-9a-fA-F]{64}$',
  },
  BSC: {
    id: 'BSC',
    title: 'BNB Smart Chain (BEP-20)',
    short: 'BEP-20',
    color: '#F3BA2F',
    explorer: 'https://bscscan.com/tx/',
    minConfirmations: 15,
    txRegex: '^0x[0-9a-fA-F]{64}$',
  },
  SOLANA: {
    id: 'SOLANA',
    title: 'Solana (SPL)',
    short: 'SPL',
    color: '#14F195',
    explorer: 'https://solscan.io/tx/',
    minConfirmations: 1,
    txRegex: '^[1-9A-HJ-NP-Za-km-z]{64,90}$',
  },
  BITCOIN: {
    id: 'BITCOIN',
    title: 'Bitcoin',
    short: 'BTC',
    color: '#F7931A',
    explorer: 'https://mempool.space/tx/',
    minConfirmations: 2,
    txRegex: '^[0-9a-fA-F]{64}$',
  },
  LITECOIN: {
    id: 'LITECOIN',
    title: 'Litecoin',
    short: 'LTC',
    color: '#345D9D',
    explorer: 'https://blockchair.com/litecoin/transaction/',
    minConfirmations: 6,
    txRegex: '^[0-9a-fA-F]{64}$',
  },
  POLYGON: {
    id: 'POLYGON',
    title: 'Polygon PoS',
    short: 'POLYGON',
    color: '#8247E5',
    explorer: 'https://polygonscan.com/tx/',
    minConfirmations: 128,
    txRegex: '^0x[0-9a-fA-F]{64}$',
  },
};

/**
 * Assets supported by Crypto Pay, each with the networks we accept for it.
 * `asset` is the exact ticker Crypto Pay expects in `createInvoice`.
 */
export const ASSETS = [
  {
    asset: 'USDT',
    name: 'Tether USD',
    color: '#26A17B',
    decimals: 2,
    networks: ['TRON', 'TON', 'ETH', 'BSC', 'SOLANA', 'POLYGON'],
    defaultNetwork: 'TRON',
  },
  {
    asset: 'USDC',
    name: 'USD Coin',
    color: '#2775CA',
    decimals: 2,
    networks: ['ETH', 'SOLANA', 'TRON', 'BSC', 'POLYGON'],
    defaultNetwork: 'ETH',
  },
  {
    asset: 'TON',
    name: 'Toncoin',
    color: '#0098EA',
    decimals: 4,
    networks: ['TON'],
    defaultNetwork: 'TON',
  },
  {
    asset: 'TRX',
    name: 'Tron',
    color: '#EF0027',
    decimals: 2,
    networks: ['TRON'],
    defaultNetwork: 'TRON',
  },
  {
    asset: 'BTC',
    name: 'Bitcoin',
    color: '#F7931A',
    decimals: 8,
    networks: ['BITCOIN'],
    defaultNetwork: 'BITCOIN',
  },
  {
    asset: 'ETH',
    name: 'Ethereum',
    color: '#627EEA',
    decimals: 6,
    networks: ['ETH'],
    defaultNetwork: 'ETH',
  },
  {
    asset: 'SOL',
    name: 'Solana',
    color: '#14F195',
    decimals: 4,
    networks: ['SOLANA'],
    defaultNetwork: 'SOLANA',
  },
  {
    asset: 'BNB',
    name: 'BNB',
    color: '#F3BA2F',
    decimals: 5,
    networks: ['BSC'],
    defaultNetwork: 'BSC',
  },
  {
    asset: 'LTC',
    name: 'Litecoin',
    color: '#345D9D',
    decimals: 5,
    networks: ['LITECOIN'],
    defaultNetwork: 'LITECOIN',
  },
];

export const findAsset = (asset) =>
  ASSETS.find((item) => item.asset.toUpperCase() === String(asset || '').toUpperCase()) || null;

export const isNetworkAllowed = (asset, network) => {
  const entry = findAsset(asset);
  if (!entry) return false;
  return entry.networks.includes(String(network || '').toUpperCase());
};

/** Catalogue payload consumed by the Android client to render the selector. */
export const buildCatalog = () =>
  ASSETS.map((entry) => ({
    asset: entry.asset,
    name: entry.name,
    color: entry.color,
    decimals: entry.decimals,
    defaultNetwork: entry.defaultNetwork,
    networks: entry.networks.map((id) => NETWORKS[id]).filter(Boolean),
  }));
