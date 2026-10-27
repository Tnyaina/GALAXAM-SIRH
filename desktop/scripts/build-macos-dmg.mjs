import { lstat, readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const desktopDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const bundleRoot = path.join(desktopDir, 'src-tauri/target/release/bundle');
const macosDir = path.join(bundleRoot, 'macos');
const dmgDir = path.join(bundleRoot, 'dmg');
const knownAppNames = new Set(['GRH.app', 'GALAXAM SIRH.app']);

async function removeLooseApps() {
  for (const name of knownAppNames) {
    const appPath = path.join(macosDir, name);
    try {
      const info = await lstat(appPath);
      if (!info.isDirectory() || info.isSymbolicLink()) {
        throw new Error(`Refusing to remove unexpected build output: ${appPath}`);
      }
      await rm(appPath, { recursive: true });
      console.log(`Removed intermediate app bundle: ${appPath}`);
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  }
}

async function clearOldInstallers() {
  for (const name of await readdir(dmgDir).catch((error) => error.code === 'ENOENT' ? [] : Promise.reject(error))) {
    if (name.endsWith('.dmg')) await rm(path.join(dmgDir, name));
  }
}

function run(command, args) {
  const result = spawnSync(command, args, { cwd: desktopDir, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} ${args.join(' ')} failed with status ${result.status}`);
}

try {
  // Only generated files inside Tauri's release bundle directories are touched.
  await removeLooseApps();
  await clearOldInstallers();

  run('npm', ['run', 'backend:prepare']);
  run('npm', ['run', 'postgresql:prepare']);
  run('npm', ['run', 'frontend:build']);
  run(path.join(desktopDir, 'node_modules/.bin/tauri'), ['build', '--bundles', 'dmg']);

  const installers = (await readdir(dmgDir)).filter((name) => name.startsWith('GRH_') && name.endsWith('.dmg'));
  if (installers.length === 0) throw new Error(`The GRH DMG was not produced in ${dmgDir}`);
  console.log(`DMG ready: ${installers.map((name) => path.join(dmgDir, name)).join(', ')}`);
} finally {
  await removeLooseApps();
}
