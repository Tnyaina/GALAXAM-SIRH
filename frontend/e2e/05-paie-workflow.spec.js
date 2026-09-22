import { test, expect } from '@playwright/test';
import { executeE2eSql } from './support/database.js';

/**
 * Helper de réinitialisation déterministe de la période 101.
 * Rétablit le statut VALIDEE (pointage scellé, prêt pour calcul paie) et les bulletins en CALCULE.
 */
function resetPeriod101() {
    executeE2eSql(`
        UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE') WHERE id_periode_paie = 101;
        DELETE FROM bulletin_paie_ligne WHERE id_bulletin IN (SELECT id_bulletin FROM bulletin_paie WHERE id_periode_paie = 101);
        DELETE FROM bulletin_paie WHERE id_periode_paie = 101;
        DELETE FROM pointage_snapshot_bucket WHERE id_cumul IN (SELECT id_cumul FROM pointage_cumul_periode WHERE id_periode_paie = 101);
        DELETE FROM pointage_snapshot_semaine WHERE id_cumul IN (SELECT id_cumul FROM pointage_cumul_periode WHERE id_periode_paie = 101);
        DELETE FROM pointage_snapshot_jour WHERE id_cumul IN (SELECT id_cumul FROM pointage_cumul_periode WHERE id_periode_paie = 101);
        DELETE FROM pointage_cumul_periode WHERE id_periode_paie = 101;
        INSERT INTO pointage_cumul_periode (
            id_periode_paie, id_employe, version_snapshot, statut_snapshot, nb_jours_ouvrables, nb_jours_presents,
            total_heures_travaillees, heures_absence_retenues, total_minutes_retard,
            heures_hs_30, heures_hs_50, heures_nuit_total, heures_dimanche_total, heures_ferie_total,
            nb_jours_conge, montant_deplacement, gele_at
        ) VALUES 
        (101, 1, 1, 'GELE', 22, 11, 85.33, 88.00, 0, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP),
        (101, 2, 1, 'GELE', 22, 17, 133.33, 40.00, 0, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP),
        (101, 3, 1, 'GELE', 22, 20, 157.33, 0.00, 0, 0, 0, 0, 0, 0, 2.0, 0, CURRENT_TIMESTAMP),
        (101, 6, 1, 'GELE', 22, 22, 173.33, 0.00, 0, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP);
        INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, source_provenance, commentaire) SELECT 1, 13333.33, '2026-01-01', 'REPRISE_CUTOVER', 'TCONGE initial E2E EMP001' WHERE NOT EXISTS (SELECT 1 FROM conge_taux_applicable WHERE id_employe = 1);
        INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, source_provenance, commentaire) SELECT 2, 16666.67, '2026-01-01', 'REPRISE_CUTOVER', 'TCONGE initial E2E EMP002' WHERE NOT EXISTS (SELECT 1 FROM conge_taux_applicable WHERE id_employe = 2);
    `);
}

async function loginAsDaf(page) {
    await page.goto('/');
    await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
    await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard/);
}

test.describe.serial('E2E Scénario 5 — Workflow Complet de Paie (Étapes 5.1 à 5.9)', () => {

    test.beforeAll(async () => {
        resetPeriod101();
    });

    test('5.1. Préparation de la campagne et vérification de la readiness', async ({ page }) => {
        await loginAsDaf(page);

        // Navigation vers l'étape 1 du wizard
        await page.goto('/paie/periodes/101/preparation');
        await expect(page.getByRole('heading', { name: /Préparation de la campagne/i })).toBeVisible({ timeout: 10000 });

        // Vérification de la checklist de préparation (8 points canoniques V1 Full V3)
        await expect(page.getByText(/Contrats & affectations/i)).toBeVisible();
        await expect(page.getByText(/Calendriers/i).first()).toBeVisible();
        await expect(page.getByText(/Pointage/i).first()).toBeVisible();
        await expect(page.getByText(/Congés & TCONGE/i)).toBeVisible();

        // Bouton de continuation vers la population
        const nextBtn = page.getByRole('button', { name: /Continuer vers Population|Continuer/i });
        await expect(nextBtn).toBeVisible();
    });

    test('5.2. Population de paie — Résolution et conformité des salariés', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/population');
        await expect(page.getByRole('heading', { name: /Population de la campagne/i })).toBeVisible({ timeout: 10000 });

        // Vérification de la présence des salariés de la campagne dans la table compacte
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP002")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP003")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();

        // Vérification de la recherche
        const searchInput = page.locator('input[placeholder*="Recherche"]');
        await expect(searchInput).toBeVisible();
        await searchInput.fill('EMP006');
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP001")')).not.toBeVisible();

        // Réinitialisation de la recherche
        await searchInput.fill('');
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
    });

    test('5.3. Variables exceptionnelles — Consultation et capacité de saisie', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/variables');
        await expect(page.getByRole('heading', { name: /Variables exceptionnelles/i })).toBeVisible({ timeout: 10000 });

        // Vérification de la présence du bouton d'ajout
        const addBtn = page.getByRole('button', { name: /Ajouter une variable/i });
        await expect(addBtn).toBeVisible();
    });

    test('5.4. Simulation collective in-memory et Calcul officiel réel persistant', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/calcul');
        await expect(page.getByRole('heading', { name: /Calcul de la paie/i })).toBeVisible({ timeout: 10000 });

        // 1. Simulation en mémoire
        const simBtn = page.getByRole('button', { name: /Simuler en mémoire/i });
        if (await simBtn.isVisible()) {
            await simBtn.click();
            await expect(page.getByText(/Simulation collective exécutée/i)).toBeVisible({ timeout: 15000 });
        }

        // 2. Calcul réel officiel
        page.on('dialog', async dialog => {
            await dialog.accept();
        });

        const calcBtn = page.getByRole('button', { name: /Calculer la paie|Recalculer la paie/i }).first();
        await expect(calcBtn).toBeEnabled({ timeout: 10000 });
        await calcBtn.click();

        // Notification de succès du calcul réel
        await expect(page.getByText(/Calcul de paie réel effectué et persisté avec succès/i)).toBeVisible({ timeout: 15000 });
    });

    test('5.5. Contrôle des bulletins — Table collective et Drawer d\'explicabilité', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/controle');
        await expect(page.getByRole('heading', { name: /État nominatif & Contrôle/i })).toBeVisible({ timeout: 10000 });

        // Vérification des 4 bulletins dans la table
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP002")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP003")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();

        // Clic sur la ligne EMP001 pour ouvrir le Drawer latéral de détail
        page.on('console', msg => console.log('BROWSER CONSOLE:', msg.type(), msg.text()));
        page.on('pageerror', err => console.log('BROWSER ERROR:', err.message));

        const emp001Row = page.locator('tr:has-text("EMP001")').first();
        await emp001Row.click();

        // Le Drawer latéral doit s'ouvrir avec les 3 niveaux
        await expect(page.getByRole('dialog')).toBeVisible({ timeout: 10000 });
        await expect(page.getByRole('dialog')).toContainText('RAZAFY Jean');
        await expect(page.getByText(/TOTAL BRUT/i)).toBeVisible();
        await expect(page.getByText(/NET À PAYER/i).first()).toBeVisible();

        // Fermeture du Drawer
        const closeBtn = page.getByRole('button', { name: /Fermer/i }).first();
        if (await closeBtn.isVisible()) {
            await closeBtn.click();
        } else {
            await page.keyboard.press('Escape');
        }
    });

    test('5.6. Approbation et validation DAF des bulletins', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/validation');
        await expect(page.getByRole('heading', { name: /Validation de la paie/i })).toBeVisible({ timeout: 10000 });

        // Valider la paie via modale
        const validerBtn = page.getByRole('button', { name: /Valider la paie/i }).first();
        if (await validerBtn.isVisible()) {
            await validerBtn.click();
            const confirmBtn = page.getByRole('button', { name: /Confirmer la validation/i }).first();
            if (await confirmBtn.isVisible()) {
                await confirmBtn.click();
                await expect(page.getByText(/Campagne de paie validée avec succès/i)).toBeVisible({ timeout: 15000 });
            }
        }

        // Vérification de l'état validé
        await expect(page.getByText(/Campagne de paie validée/i).first()).toBeVisible({ timeout: 10000 });
    });

    test('5.7. Sorties de campagne et Clôture définitive', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/sorties');
        await expect(page.getByRole('heading', { name: /Sorties de/i })).toBeVisible({ timeout: 10000 });

        // Vérification des cartes par domaine
        await expect(page.getByText(/Salariés/i).first()).toBeVisible();
        await expect(page.getByText(/Paiement/i).first()).toBeVisible();
        await expect(page.getByText(/Comptabilité/i).first()).toBeVisible();
    });

    test.afterAll(async () => {
        // Rétablir la période dans son état nominal pour les tests ultérieurs
        resetPeriod101();
    });
});
