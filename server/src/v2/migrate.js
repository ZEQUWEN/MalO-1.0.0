import { createPool, migrate } from './database.js';
const pool = createPool(process.env.DATABASE_URL);
if (!pool) {
  console.error('DATABASE_URL must be configured before migrating.');
  process.exitCode = 1;
} else {
  try {
    await migrate(pool);
    console.log('v2 schema applied. Legacy JSON data was not migrated.');
  } catch {
    console.error('Database migration failed. Check database connectivity and permissions.');
    process.exitCode = 1;
  } finally {
    await pool.end();
  }
}
