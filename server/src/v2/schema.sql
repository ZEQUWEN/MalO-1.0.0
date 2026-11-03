-- v2 PostgreSQL schema. Apply explicitly; legacy JSON data is not migrated.
CREATE TABLE IF NOT EXISTS v2_users (
  id uuid PRIMARY KEY,
  email text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS v2_kofi_intents (
  token_hash char(64) PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES v2_users(id),
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  currency char(3) NOT NULL,
  period_days integer NOT NULL CHECK (period_days BETWEEN 1 AND 366),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  CHECK (expires_at > created_at)
);

CREATE TABLE IF NOT EXISTS v2_kofi_events (
  transaction_id text PRIMARY KEY,
  message_id text NOT NULL UNIQUE,
  received_at timestamptz NOT NULL DEFAULT now(),
  token_hash char(64),
  amount_minor bigint,
  currency text,
  event_type text,
  result text NOT NULL,
  -- Retain the provider ID for reconciliation, not the secret, payer email,
  -- free-form message, or payment code.
  CHECK (result IN ('processing', 'settled', 'review'))
);

CREATE TABLE IF NOT EXISTS v2_subscriptions (
  user_id uuid PRIMARY KEY REFERENCES v2_users(id),
  current_period_end timestamptz NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Codes and sessions are stored hashed; neither is a client-selected user ID.
CREATE TABLE IF NOT EXISTS v2_email_challenges (
  email text PRIMARY KEY,
  code_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0)
);

CREATE TABLE IF NOT EXISTS v2_sessions (
  token_hash char(64) PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES v2_users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz
);

CREATE TABLE IF NOT EXISTS v2_kofi_conflicts (
  transaction_id text NOT NULL,
  message_id text NOT NULL,
  first_seen_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (transaction_id, message_id)
);

CREATE TABLE IF NOT EXISTS v2_rate_limits (
  bucket_key text PRIMARY KEY,
  request_count integer NOT NULL,
  expires_at timestamptz NOT NULL
);

ALTER TABLE v2_kofi_events ADD COLUMN IF NOT EXISTS is_subscription_payment boolean;
