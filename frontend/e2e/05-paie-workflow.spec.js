import { test, expect } from '@playwright/test';
import { execSync } from 'child_process';

/**
 * Helper de réinitialisation déterministe de la période 101.
 * Rétablit le statut VALIDEE (pointage scellé, prêt pour calcul paie) et les bulletins en CALCULE.
 */
function resetPeriod101() {
    try {
        execSync(
            `PGPASSWORD=galaxam_password /opt/homebrew/Cellar/libpq/18.1/bin/psql -h localhost -p 5432 -U galaxam_admin -d sirh_galaxam_test -c "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE') WHERE id_periode_paie = 101; UPDATE bulletin_paie SET statut = 'ROUVERT', motif_reouverture = 'Reouverture test reset' WHERE id_periode_paie = 101; UPDATE bulletin_paie SET statut = 'CALCULE' WHERE id_periode_paie = 101; INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, source_provenance, commentaire) SELECT 1, 13333.33, '2026-01-01', 'REPRISE_CUTOVER', 'TCONGE initial E2E EMP001' WHERE NOT EXISTS (SELECT 1 FROM conge_taux_applicable WHERE id_employe = 1); INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, source_provenance, commentaire) SELECT 2, 16666.67, '2026-01-01', 'REPRISE_CUTOVER', 'TCONGE initial E2E EMP002' WHERE NOT EXISTS (SELECT 1 FROM conge_taux_applicable WHERE id_employe = 2);"`,
            { stdio: 'ignore' }
        );
    } catch (e) {
        console.warn('Reset period 101 warning:', e.message);
    }
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

    test('5.1. Préparation de la campagne et vérification des 4 prérequis de calcul', async ({ page }) => {
        await loginAsDaf(page);

        // Navigation vers l'étape 1 du wizard
        await page.goto('/paie/periodes/101/preparation');
        await expect(page.getByRole('heading', { name: /Étape 1 — Préparation de la campagne/i })).toBeVisible({ timeout: 10000 });

        // Vérification des 4 prérequis métier
        await expect(page.getByText(/Snapshot Pointage scellé & figé \(DAF\)/i)).toBeVisible();
        await expect(page.getByText(/Exercice fiscal & Barème IRSA actif/i)).toBeVisible();
        await expect(page.getByText(/Moteur de calcul prêt/i)).toBeVisible();
        await expect(page.getByText(/Saisie des variables de paie/i)).toBeVisible();

        // Vérification du statut du pointage gelé
        await expect(page.getByText(/Le pointage de la période est validé et figé/i)).toBeVisible();

        // Vérification du bouton suivant vers la population
        const nextBtn = page.getByRole('button', { name: /Population éligible|Suivant/i });
        await expect(nextBtn).toBeVisible();
    });

    test('5.2. Population de paie — Résolution et conformité des 4 salariés', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/population');
        await expect(page.getByRole('heading', { name: /Étape 2 — Population de paie/i })).toBeVisible({ timeout: 10000 });

        // Vérification de la présence des 4 salariés de la campagne
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
        await expect(page.locator('tr:has-text("RAZAFY Jean")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP002")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP003")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();

        // Vérification de la recherche interactive
        const searchInput = page.locator('#population-search');
        await expect(searchInput).toBeVisible();
        await searchInput.fill('EMP006');
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP001")')).not.toBeVisible();

        // Réinitialisation de la recherche
        await searchInput.fill('');
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
    });

    test('5.3. Variables et Primes — Consultation et capacité de saisie', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/variables');
        await expect(page.getByText('Primes & Gains', { exact: true })).toBeVisible({ timeout: 10000 });

        // Le bouton "Nouvelle Variable" doit être disponible pour la DAF
        const addBtn = page.getByRole('button', { name: /Nouvelle Variable/i });
        await expect(addBtn).toBeVisible();

        // Vérification des StatCards des variables
        await expect(page.getByText('Acomptes & Retenues', { exact: true })).toBeVisible();
    });

    test('5.4. Simulation collective in-memory et Calcul officiel réel persistant', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/calcul');
        await expect(page.getByRole('heading', { name: /Étape 4 — Calcul de la paie/i })).toBeVisible({ timeout: 10000 });

        // 1. Simulation waterfall (sans persistance en base)
        const simBtn = page.locator('#btn-simuler-paie');
        await expect(simBtn).toBeEnabled();
        await simBtn.click();

        // Attente des résultats prévisionnels
        await expect(page.getByText(/Résultats de la simulation \(prévisionnels en mémoire\)/i)).toBeVisible({ timeout: 15000 });
        await expect(page.getByText(/Simulation collective exécutée avec succès/i)).toBeVisible();

        // Vérification des agrégats canoniques de simulation
        await expect(page.getByText(/1.*084.*598/i).first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText(/1.*068.*770/i).first()).toBeVisible({ timeout: 15000 });

        // 2. Calcul réel officiel (avec persistance)
        page.on('dialog', async dialog => {
            await dialog.accept();
        });

        const calcBtn = page.locator('#btn-calculer-paie');
        await expect(calcBtn).toBeEnabled();
        await calcBtn.click();

        // Notification de succès du calcul réel
        await expect(page.getByText(/Calcul de paie réel effectué et persisté avec succès/i)).toBeVisible({ timeout: 15000 });
        await expect(page.getByText(/Résultats du calcul réel persisté|Dernier état calculé en base/i)).toBeVisible();
    });

    test('5.5. Contrôle des bulletins — Table collective et Drawer d\'explicabilité', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/controle');
        await expect(page.getByRole('heading', { name: /Étape 5 — Contrôle des bulletins/i })).toBeVisible({ timeout: 10000 });

        // Vérification des 4 bulletins dans la table
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP002")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP003")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();

        // Vérification de l'export CSV du contrôle
        await expect(page.locator('#btn-export-controle-csv')).toBeEnabled();

        // Clic sur EMP001 pour ouvrir le Drawer latéral de détail
        const emp001Row = page.locator('tr:has-text("EMP001")').first();
        await emp001Row.click();

        // Le Drawer latéral doit s'ouvrir
        await expect(page.getByText(/RAZAFY Jean/i)).toBeVisible({ timeout: 10000 });
        await expect(page.getByText(/TOTAL BRUT/i)).toBeVisible();
        await expect(page.getByText(/NET IMPOSABLE/i)).toBeVisible();

        // Vérification du montant Net à Payer officiel pour EMP001 (231 000 Ar)
        await expect(page.getByText('231 000 Ar').first()).toBeVisible();

        // Fermeture du Drawer
        const closeBtn = page.getByRole('button', { name: /Fermer/i }).or(page.locator('button[aria-label="Fermer"]'));
        if (await closeBtn.isVisible()) {
            await closeBtn.click();
        } else {
            await page.keyboard.press('Escape');
        }
    });

    test('5.6. Approbation et validation DAF des bulletins (PAIE_VALIDEE)', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/validation');
        await expect(page.getByText(/Étape 3 du Cycle Paie — Approbation & Clôture DAF/i)).toBeVisible({ timeout: 10000 });

        // Valider les bulletins
        page.on('dialog', async dialog => {
            await dialog.accept();
        });

        const validerBtn = page.locator('#btn-valider-bulletins');
        if (await validerBtn.isEnabled()) {
            await validerBtn.click();
            await expect(page.getByText(/Bulletins validés avec succès/i)).toBeVisible({ timeout: 15000 });
        }

        // Vérification de l'état validé
        await expect(page.locator('span:has-text("✓ Paie validée"), span:has-text("Paie Validée & Approuvée"), button:has-text("Bulletins approuvés")').first()).toBeVisible({ timeout: 10000 });
    });

    test('5.7. Clôture définitive et irréversible de la campagne (CLOTUREE)', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/validation');
        await expect(page.getByText(/Actions du workflow/i)).toBeVisible({ timeout: 10000 });

        page.on('dialog', async dialog => {
            await dialog.accept();
        });

        const cloturerBtn = page.locator('#btn-cloturer-periode');
        await expect(cloturerBtn).toBeEnabled();
        await cloturerBtn.click();

        // Notification et écran de clôture
        await expect(page.getByText(/Période clôturée définitivement|Période clôturée\. Les bulletins sont immuables/i).first()).toBeVisible({ timeout: 15000 });
        await expect(page.locator('#btn-goto-sorties')).toBeVisible();
    });

    test('5.8. Immutabilité post-clôture — Verrouillage du moteur de calcul', async ({ page }) => {
        await loginAsDaf(page);

        // Tentative d'accès à la page de calcul sur période clôturée
        await page.goto('/paie/periodes/101/calcul');
        await expect(page.getByRole('heading', { name: /Étape 4 — Calcul de la paie/i })).toBeVisible({ timeout: 10000 });

        // Le bouton de calcul réel doit être désactivé
        const calcBtn = page.locator('#btn-calculer-paie');
        await expect(calcBtn).toBeDisabled();

        // Le statut d'éligibilité doit indiquer le verrouillage
        await expect(page.locator('span:has-text("Prérequis manquants"), span:has-text("Période Clôturée")').first()).toBeVisible();
    });

    test('5.9. Restitutions légales, déclarations SAFI, CNaPS DNTS et virements bancaires', async ({ page }) => {
        await loginAsDaf(page);

        await page.goto('/paie/periodes/101/sorties');
        await expect(page.getByRole('heading', { name: /Étape 7 — Bulletins & Exports réglementaires/i })).toBeVisible({ timeout: 10000 });
        await expect(page.getByText(/Restitutions Légales & Fichiers Bancaires/i)).toBeVisible();

        // Vérification des 4 types d'exports réglementaires disponibles
        await expect(page.getByText(/SAFI IRSA — État 301/i)).toBeVisible();
        await expect(page.getByText(/EDI ISI — Télé-Déclaration XML/i)).toBeVisible();
        await expect(page.getByText(/DNTS — Cotisations Sociales CNaPS & FMFP/i)).toBeVisible();
        await expect(page.getByText(/Ordre de Virement Bancaire Collectif/i)).toBeVisible();

        // Vérification que les boutons de téléchargement sont actifs
        await expect(page.getByRole('button', { name: /Format ODS Officiel/i })).toBeEnabled();
        await expect(page.getByRole('button', { name: /Format CSV/i }).first()).toBeEnabled();
        await expect(page.getByRole('button', { name: /Télécharger DNTS CSV/i })).toBeEnabled();
        await expect(page.getByRole('button', { name: /Télécharger XML EDI ISI/i })).toBeEnabled();
    });

    test.afterAll(async () => {
        // Rétablir la période dans son état nominal pour les tests ultérieurs
        resetPeriod101();
    });
});
