import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

let vite;
let EmployeTable;
let DepartementsList;
let ServicesList;

before(async () => {
  vite = await createServer({
    configFile: fileURLToPath(new URL('../vite.config.js', import.meta.url)),
    appType: 'custom',
    logLevel: 'error',
    server: { middlewareMode: true },
  });

  ({ default: EmployeTable } = await vite.ssrLoadModule('/src/features/employees/components/EmployeTable.jsx'));
  ({ default: DepartementsList } = await vite.ssrLoadModule('/src/features/organization/components/views/DepartementsList.jsx'));
  ({ default: ServicesList } = await vite.ssrLoadModule('/src/features/organization/components/views/ServicesList.jsx'));
});

after(async () => {
  await vite?.close();
});

test('le dossier employé est accessible par un lien clavier dans le tableau desktop', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, null,
    createElement(EmployeTable, {
      employes: [{ idEmploye: 42, nom: 'Rakoto', prenom: 'Miora', matricule: 'EMP-42', estActif: true }],
    }),
  ));

  assert.match(html, /<a[^>]*title="Ouvrir le dossier de Miora Rakoto"[^>]*href="\/employes\/42"[^>]*>Miora Rakoto<\/a>/);
  assert.doesNotMatch(html, /<tr[^>]*onclick=/i);
});

test('le département sélectionné est un contrôle natif avec son état annoncé', () => {
  const html = renderToStaticMarkup(createElement(DepartementsList, {
    departements: [{ idDepartement: 3, libelle: 'Ressources humaines', codeDepartement: 'RH', estActif: true }],
    selectedDepartement: { idDepartement: 3 },
    onSelectDepartement() {},
  }));

  assert.match(html, /<button[^>]*aria-pressed="true"/);
  assert.match(html, /Ressources humaines/);
});

test('les actions d’organisation suivent ORGANISATION_WRITE dans les vues département et service', () => {
  const department = { idDepartement: 3, libelle: 'Ressources humaines', codeDepartement: 'RH', estActif: true };
  const service = { idService: 4, libelle: 'Administration du personnel', codeService: 'ADP', estActif: true };
  const readOnlyHtml = renderToStaticMarkup(createElement('div', null,
    createElement(DepartementsList, {
      departements: [department], selectedDepartement: department, canWrite: false,
      onSelectDepartement() {}, onOpenEdit() {}, onToggleStatus() {}, onOpenCreate() {},
    }),
    createElement(ServicesList, {
      selectedDepartement: department, services: [service], canWrite: false,
      onOpenCreateService() {}, onOpenEditService() {}, onToggleServiceStatus() {},
    }),
  ));
  const writableHtml = renderToStaticMarkup(createElement(ServicesList, {
    selectedDepartement: department, services: [service], canWrite: true,
    onOpenCreateService() {}, onOpenEditService() {}, onToggleServiceStatus() {},
  }));

  assert.doesNotMatch(readOnlyHtml, /Nouveau Service|aria-label="Modifier Ressources humaines"|aria-label="Modifier le service Administration du personnel"/);
  assert.match(writableHtml, /Nouveau Service/);
  assert.match(writableHtml, /aria-label="Modifier le service Administration du personnel"/);
});
