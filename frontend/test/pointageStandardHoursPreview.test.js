import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { getConfiguredHoursPreview } from '../src/features/attendance/utils/pointageStandardHours.js';

const tableSource = await readFile(new URL('../src/features/attendance/components/views/PeriodeEmployeeTable.jsx', import.meta.url), 'utf8');

test('le remplissage standard suit les quatre plages du site, même si l’option continue est activée', () => {
  assert.deepEqual(getConfiguredHoursPreview({
    horaireContinu: true,
    heureDebutMatin: '08:00',
    heureFinMatin: '12:30',
    heureDebutAm: '13:30',
    heureFinAm: '17:00',
  }), {
    periods: [['08:00', '12:30'], ['13:30', '17:00']],
    continuous: false,
    totalMinutes: 480,
    totalHoursLabel: '8 h00',
  });

  assert.deepEqual(getConfiguredHoursPreview({
    horaireContinu: false,
    heureDebutMatin: '08:00',
    heureFinMatin: '12:00',
    heureDebutAm: '14:00',
    heureFinAm: '18:00',
  }), {
    periods: [['08:00', '12:00'], ['14:00', '18:00']],
    continuous: false,
    totalMinutes: 480,
    totalHoursLabel: '8 h00',
  });

  assert.equal(getConfiguredHoursPreview({
    horaireContinu: true,
    heureDebutMatin: '08:00',
    heureFinMatin: '12:00',
    heureDebutAm: null,
    heureFinAm: '18:00',
  }), null);
});

test('le remplissage hebdomadaire demande confirmation avec horaires, durée, dates et remplacement visibles', () => {
  assert.match(tableSource, /title="Vérifier les horaires de la semaine"/);
  assert.match(tableSource, /Durée calculée/);
  assert.match(tableSource, /Journées concernées/);
  assert.match(tableSource, /horaires déjà saisis seront remplacés/);
  assert.match(tableSource, /Le serveur ne sera modifié qu’après l’enregistrement/);
});
