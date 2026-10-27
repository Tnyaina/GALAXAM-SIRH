import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const [attendanceView, payrollVariablesView, periodAttendancePage, workflowStepper, globalStyles] = await Promise.all([
  readFile(new URL('../src/features/attendance/components/views/PeriodeEmployeeTable.jsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/features/payroll/components/tabs/VariablesPaieTab.jsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/features/attendance/pages/SaisiePeriodeEmployePage.jsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/ui/navigation/WorkflowStepper.jsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/index.css', import.meta.url), 'utf8'),
]);

test('le pointage expose les libellés des cellules et une disposition mobile sans défilement horizontal', () => {
  for (const label of ['Date', 'Entrée matin', 'Sortie matin', 'Retour après-midi', 'Sortie fin', 'Retard', 'Heures réalisées', 'Statut', 'Actions']) {
    assert.ok(attendanceView.includes(`data-label="${label}"`), `Libellé manquant : ${label}`);
  }
  assert.match(attendanceView, /pointage-table-wrap/);
  assert.match(globalStyles, /\.pointage-table-wrap\s*\{\s*overflow-x:\s*hidden\s*!important/);
  assert.match(globalStyles, /\.pointage-table tbody tr\[role="row"\][\s\S]*?display:\s*grid/);
  assert.match(globalStyles, /\.pointage-table td\[role="cell"\]::before[\s\S]*?content:\s*attr\(data-label\)/);
});

test('les variables de paie présentent les informations et actions dans une grille mobile étiquetée', () => {
  for (const label of ['Salarié', 'Variable et régime', 'Montant', 'Justification', 'Statut', 'Actions']) {
    assert.ok(payrollVariablesView.includes(`data-label="${label}"`), `Libellé manquant : ${label}`);
  }
  assert.match(payrollVariablesView, /payroll-variable-table-wrap/);
  assert.match(globalStyles, /\.payroll-variable-table tbody tr\[role="row"\][\s\S]*?display:\s*grid/);
  assert.match(globalStyles, /\.payroll-variable-table td\[data-label="Actions"\] > div[\s\S]*?flex-wrap:\s*wrap/);
});

test('les commandes Pointage et les étapes restent lisibles sur mobile et tablette', () => {
  assert.match(periodAttendancePage, /attendance-mode-controls/);
  assert.match(periodAttendancePage, /attendance-mode-switch/);
  assert.match(workflowStepper, /workflow-stepper-steps/);
  assert.match(workflowStepper, /workflow-step-button/);
  assert.match(globalStyles, /@media\s*\(max-width:\s*1023px\)[\s\S]*?\.pointage-table\s*\{[\s\S]*?display:\s*block/);
  assert.match(globalStyles, /\.attendance-mode-switch\s*\{[\s\S]*?display:\s*grid/);
  assert.match(globalStyles, /\.workflow-stepper-steps\s*\{[\s\S]*?flex-wrap:\s*wrap[\s\S]*?overflow:\s*visible/);
});
