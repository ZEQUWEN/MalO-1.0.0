import pg from 'pg';
import fs from 'node:fs/promises';

export function createPool(databaseUrl) {
  if (!databaseUrl) return null;
  return new pg.Pool({ connectionString: databaseUrl, max: 10, connectionTimeoutMillis: 5000, idleTimeoutMillis: 30000 });
}

// Explicit migration command; never silently initialize or reset state at startup.
export async function migrate(pool) {
  const sql = await fs.readFile(new URL('./schema.sql', import.meta.url), 'utf8');
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query("SELECT pg_advisory_xact_lock(hashtext('malo-v2-schema'))");
    await client.query(sql);
    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}
