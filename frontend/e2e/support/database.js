import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const container = process.env.E2E_POSTGRES_CONTAINER || 'galaxam-postgres';
const database = process.env.E2E_DATABASE || 'sirh_galaxam_test';
const user = process.env.E2E_DATABASE_USER || 'galaxam_admin';

function runPsql(args, input) {
    const result = spawnSync('docker', ['exec', '-i', container, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database, ...args], {
        input,
        encoding: 'utf8',
    });
    if (result.status !== 0) {
        throw new Error(result.stderr || result.stdout || 'Échec de préparation de la base E2E');
    }
}

export function seedE2eDatabase() {
    const seedPath = resolve(process.cwd(), '../scripts/seed_e2e_data.sql');
    runPsql([], readFileSync(seedPath, 'utf8'));
}

export function executeE2eSql(sql) {
    runPsql(['-c', sql]);
}
