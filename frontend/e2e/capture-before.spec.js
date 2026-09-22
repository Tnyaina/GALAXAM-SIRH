import { test } from '@playwright/test';

// Utilise la même baseURL que playwright.config.js (5174)
const CERT_DIR = '/Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/docs/payroll-v1-full-v3-visual-certification';
const PERIOD_ID = 101;

const PAGES = [
  { id: '01-centre-paie',  path: '/paie' },
  { id: '02-preparation',  path: `/paie/periodes/${PERIOD_ID}/preparation` },
  { id: '03-population',   path: `/paie/periodes/${PERIOD_ID}/population` },
  { id: '04-variables',    path: `/paie/periodes/${PERIOD_ID}/variables` },
  { id: '05-calcul',       path: `/paie/periodes/${PERIOD_ID}/calcul` },
  { id: '06-controle',     path: `/paie/periodes/${PERIOD_ID}/controle` },
  { id: '07-validation',   path: `/paie/periodes/${PERIOD_ID}/validation` },
  { id: '08-sorties',      path: `/paie/periodes/${PERIOD_ID}/sorties` },
  { id: '09-acomptes',     path: '/paie/acomptes-avances' },
  { id: '10-historique',   path: '/paie/historique' },
  { id: '11-declarations', path: '/paie/declarations' },
];

test.use({ viewport: { width: 1440, height: 900 } });

async function loginAsDaf(page) {
  await page.goto('/');
  await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
  await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
  await page.locator('button[type="submit"]').click();
  await page.waitForURL('**/dashboard**', { timeout: 10000 });
}

test('Capture BEFORE screenshots — V1 Full V3 Conformance', async ({ page }) => {
  await loginAsDaf(page);

  for (const p of PAGES) {
    await page.goto(p.path, { timeout: 15000 }).catch(() => {});
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1200);
    await page.screenshot({ path: `${CERT_DIR}/${p.id}-before.png`, fullPage: false });
    console.log('CAPTURED:', p.id);
  }
});
