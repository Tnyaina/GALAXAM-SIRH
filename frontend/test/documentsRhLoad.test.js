import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

let vite;
let DocumentsRhTab;
before(async () => {
  vite = await createServer({
    configFile: fileURLToPath(new URL('../vite.config.js', import.meta.url)),
    appType: 'custom', logLevel: 'error', server: { middlewareMode: true },
  });
  ({ default: DocumentsRhTab } = await vite.ssrLoadModule('/src/features/employees/components/tabs/DocumentsRhTab.jsx'));
});
after(async () => { await vite?.close(); });

const render = (status, documents = []) => renderToStaticMarkup(createElement(DocumentsRhTab, {
  documentsData: { documents, obligatoiresManquants: [] },
  documentsStatus: status,
  loadingDocs: status === 'loading',
  onRetry() {},
}));

test('une erreur affiche la nouvelle tentative sans faux état vide ni conformité', () => {
  const html = render('error');
  assert.match(html, /Impossible de charger les documents RH/);
  assert.match(html, /Réessayer/);
  assert.doesNotMatch(html, /Aucun document RH déposé|Dossier conforme/);
});

test('une liste vide réussie est annoncée comme telle', () => {
  assert.match(render('success'), /Aucun document RH déposé/);
});

test('les documents antérieurs restent visibles après un échec de rechargement', () => {
  const html = render('error', [{ idDocument: 7, nomAffichage: 'Pièce synthétique.pdf' }]);
  assert.match(html, /Pièce synthétique.pdf/);
  assert.match(html, /Impossible de charger les documents RH/);
});

test('le chargement n’annonce pas une liste vide', () => {
  assert.doesNotMatch(render('loading'), /Aucun document RH déposé|Dossier conforme/);
});
