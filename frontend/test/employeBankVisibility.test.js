import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

let vite;
let FichePersonnelleTab;
before(async () => {
  vite = await createServer({
    configFile: fileURLToPath(new URL('../vite.config.js', import.meta.url)),
    appType: 'custom', logLevel: 'error', server: { middlewareMode: true },
  });
  ({ default: FichePersonnelleTab } = await vite.ssrLoadModule(
    '/src/features/employees/components/tabs/FichePersonnelleTab.jsx'));
});
after(async () => { await vite?.close(); });

const employee = { nom: 'Synthétique', adresse: 'Adresse de test' };
const accounts = [{ idCompteBanque: 1, nomBanque: 'Banque de test', numCompteRib: 'SYNTHETIC-RIB-001' }];
const render = (props) => renderToStaticMarkup(createElement(FichePersonnelleTab, {
  emp: employee, comptes: accounts, onAddCompte() {}, ...props,
}));

test('les droits employés seuls ne montrent aucun renseignement bancaire', () => {
  const html = render({ canReadBank: false, canWriteBank: false });
  assert.match(html, /Adresse de test/);
  assert.doesNotMatch(html, /SYNTHETIC-RIB|Compte bancaire pour virement|Ajouter un compte/);
});

test('la lecture paie révèle le compte sans action de modification', () => {
  const html = render({ canReadBank: true, canWriteBank: false });
  assert.match(html, /SYNTHETIC-RIB-001/);
  assert.doesNotMatch(html, /Modifier le compte/);
});

test('l’écriture paie sans lecture montre l’action sans révéler le compte existant', () => {
  const html = render({ canReadBank: false, canWriteBank: true });
  assert.match(html, /Renseigner ou remplacer le compte/);
  assert.doesNotMatch(html, /SYNTHETIC-RIB/);
});
