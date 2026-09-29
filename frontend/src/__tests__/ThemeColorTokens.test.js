import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const sourceRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const themeCss = readFileSync(join(sourceRoot, 'index.css'), 'utf8');
const declaredColors = new Set([...themeCss.matchAll(/--color-([a-z0-9-]+)\s*:/g)].map((match) => match[1]));

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return /\.(jsx?|tsx?|css)$/.test(entry.name) ? [path] : [];
  });
}

describe('tokens couleurs du frontend', () => {
  const files = sourceFiles(sourceRoot);
  const source = files.map((path) => readFileSync(path, 'utf8')).join('\n');

  it('charge le thème global depuis le point d’entrée React', () => {
    const main = readFileSync(join(sourceRoot, 'main.jsx'), 'utf8');
    expect(main).toMatch(/import\s+["']\.\/index\.css["']/);
  });

  it('déclare toutes les couleurs CSS utilisées par les composants', () => {
    const references = [...source.matchAll(/var\(--color-([a-z0-9-]+)/g)].map((match) => match[1]);
    const missing = [...new Set(references.filter((name) => !declaredColors.has(name)))];
    expect(missing).toEqual([]);
  });

  it('ne laisse pas de couleurs littérales dans le code des vues', () => {
    const viewSource = files
      .filter((path) => !path.endsWith('index.css'))
      .map((path) => readFileSync(path, 'utf8'))
      .join('\n');
    expect(viewSource).not.toMatch(/#[\da-fA-F]{3,8}\b/);
    expect(viewSource).not.toMatch(/\b(?:bg|text|border|ring|outline|fill|stroke|divide|placeholder)-(?:red|green|blue|amber|yellow|lime|emerald|teal|cyan|indigo|violet|rose|pink|slate|gray|zinc|neutral|stone)-\d{2,3}\b/);
  });
});
