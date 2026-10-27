import test from 'node:test';
import assert from 'node:assert/strict';
import { hasHorizontalOverflow } from '../src/components/ui/navigation/tabsOverflow.js';

test('signale un débordement horizontal réel', () => {
  assert.equal(hasHorizontalOverflow({ clientWidth: 366, scrollWidth: 419 }), true);
});

test('ne signale pas les onglets contenus dans la largeur disponible', () => {
  assert.equal(hasHorizontalOverflow({ clientWidth: 470, scrollWidth: 470 }), false);
});

test('ignore les éléments absents et les différences de sous-pixel', () => {
  assert.equal(hasHorizontalOverflow(null), false);
  assert.equal(hasHorizontalOverflow({ clientWidth: 400, scrollWidth: 401 }), false);
});
