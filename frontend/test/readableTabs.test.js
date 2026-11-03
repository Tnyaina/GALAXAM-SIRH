import test from 'node:test';
import assert from 'node:assert/strict';
import { readablePathFor } from '../src/hooks/useReadableTabs.js';

test('les onglets des principaux modules ont un chemin stable', () => {
  assert.equal(readablePathFor('securite', ['users']), '/admin/securite/utilisateurs');
  assert.equal(readablePathFor('conges', ['demandes']), '/conges/demandes');
  assert.equal(readablePathFor('pointages', ['arbitrage']), '/pointages/arbitrage');
  assert.equal(readablePathFor('paie', ['periodes']), '/paie/cycles');
  assert.equal(readablePathFor('paieConfiguration', ['types']), '/paie/configuration/types');
  assert.equal(readablePathFor('carrieres', ['mouvements']), '/carrieres/mouvements');
  assert.equal(readablePathFor('parametres', ['general', 'parametres_globaux_systeme']), '/admin/parametres/general/constantes');
  assert.equal(readablePathFor('parametres', ['paie', 'majorations']), null);
});
