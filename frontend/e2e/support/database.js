import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const database = process.env.E2E_DATABASE || 'sirh_galaxam_test';
const user = process.env.E2E_DATABASE_USER || 'galaxam_admin';
const psqlBin = process.env.PSQL_BIN || '/Applications/pgAdmin 4.app/Contents/SharedSupport/psql';

function runPsql(args, input) {
    // 1. Essayer psql natif (local PostgreSQL)
    const env = { ...process.env, PGPASSWORD: process.env.PGPASSWORD || 'galaxam_password' };
    const nativeResult = spawnSync(psqlBin, ['-h', 'localhost', '-p', '5432', '-U', user, '-d', database, '-v', 'ON_ERROR_STOP=1', ...args], {
        input,
        env,
        encoding: 'utf8',
    });
    if (nativeResult.status === 0) {
        return;
    }

    // 2. Fallback docker si environnement conteneurisé
    const container = process.env.E2E_POSTGRES_CONTAINER || 'galaxam-postgres';
    const dockerResult = spawnSync('docker', ['exec', '-i', container, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database, ...args], {
        input,
        encoding: 'utf8',
    });
    if (dockerResult.status !== 0) {
        throw new Error(nativeResult.stderr || dockerResult.stderr || 'Échec de préparation de la base E2E');
    }
}

export function seedE2eDatabase() {
    const seedPath = resolve(process.cwd(), '../scripts/seed_e2e_data.sql');
    runPsql([], readFileSync(seedPath, 'utf8'));
}

export function executeE2eSql(sql) {
    runPsql(['-c', sql]);
}

