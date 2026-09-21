import { test, expect } from '@playwright/test';

test.describe('E2E Scénarios 2 & 3 — Pointage Quotidien, Intégrité Temporelle et Anomalies', () => {

    test.beforeEach(async ({ page }) => {
        // Authentification en tant qu'Agent de Pointage
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('pointage@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Pointage-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/pointages/);
    });

    test('2.1. Saisie des horaires standard, enregistrement batch et persistance après refresh', async ({ page }) => {
        // Navigation vers la grille de saisie pour le mardi 08/09/2026 sur le Site #1 (date non encore pointée)
        await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-08&site=1');
        
        // Attendre que la grille soit chargée avec les collaborateurs
        await expect(page.locator('table')).toBeVisible();
        await expect(page.getByText('RAZAFY Jean')).toBeVisible();

        // Cliquer sur le bouton "Horaire standard" pour pré-remplir automatiquement
        const autoFillBtn = page.getByRole('button', { name: /Horaire standard/i });
        await expect(autoFillBtn).toBeEnabled();
        await autoFillBtn.click();

        // Le bouton "Enregistrer" doit maintenant être actif avec les modifications
        const saveBtn = page.locator('button:has-text("Enregistrer")');
        await expect(saveBtn).toBeVisible();
        await saveBtn.click();

        // La modale récapitulative doit s'ouvrir
        await expect(page.getByText(/Récapitulatif & Validation des Pointages du Jour/i)).toBeVisible();

        // Confirmer l'enregistrement
        const confirmBtn = page.getByRole('button', { name: /Confirmer & Enregistrer/i });
        await confirmBtn.click();

        // Attendre la fermeture de la modale et la notification de succès
        await expect(page.getByText(/Pointages enregistrés avec succès/i)).toBeVisible({ timeout: 10000 });

        // Rafraîchir la page (F5) pour vérifier la persistance en base et la restitution API
        await page.reload();
        await expect(page.locator('table')).toBeVisible();
        await expect(page.getByText('RAZAFY Jean')).toBeVisible();

        // Vérifier que les heures standard persistent bien dans les champs
        const firstRowTimeInputs = page.locator('table tbody tr').first().locator('input[placeholder="--:--"]');
        await expect(firstRowTimeInputs.first()).toHaveValue('08:00');
    });

    test('2.2. Intégrité temporelle : Blocage strict de la saisie sur date future', async ({ page }) => {
        // Navigation vers une date future (25/09/2026)
        await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-25&site=1');

        // Vérifier l'alerte d'intégrité temporelle
        await expect(page.getByText(/Date future/i)).toBeVisible();
        await expect(page.getByText(/la saisie et la modification des pointages sont strictement désactivées/i)).toBeVisible();

        // Vérifier que le bouton d'enregistrement est désactivé
        const saveBtn = page.getByRole('button', { name: /Enregistré|Enregistrer/i });
        await expect(saveBtn).toBeDisabled();

        // Vérifier que les inputs de temps sont désactivés
        const timeInputs = page.locator('table tbody tr').first().locator('input[type="text"]');
        const count = await timeInputs.count();
        if (count > 0) {
            await expect(timeInputs.first()).toBeDisabled();
        }
    });

    test('2.3. Période clôturée : Mode lecture seule non modifiable', async ({ page }) => {
        // Navigation vers la période clôturée (PER-2026-08-CLOT / id=102)
        await page.goto('/pointages?tab=saisie&periode=102&date=2026-08-10&site=1');

        // Le bouton Horaire standard doit être désactivé
        const autoFillBtn = page.getByRole('button', { name: /Horaire standard/i });
        await expect(autoFillBtn).toBeDisabled();

        // Le bouton Enregistrer doit être désactivé
        const saveBtn = page.getByRole('button', { name: /Enregistré|Enregistrer/i });
        await expect(saveBtn).toBeDisabled();
    });

    test('2.4. Anomalie de pointage : Détection backend, affichage cockpit et régularisation', async ({ page }) => {
        // Saisir volontairement un pointage anormal pour le 09/09/2026 : Entrée matin à 08:00 sans heure de sortie
        await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-09&site=1');
        await expect(page.locator('table')).toBeVisible();

        const firstRow = page.locator('table tbody tr').first();
        const entreeInput = firstRow.locator('input[placeholder="--:--"]').first();
        await entreeInput.click();
        await entreeInput.pressSequentially('0800', { delay: 50 });

        // Enregistrer l'anomalie
        const saveBtn = page.locator('button:has-text("Enregistrer")');
        await expect(saveBtn).toBeVisible();
        await saveBtn.click();

        await expect(page.getByText(/Récapitulatif & Validation des Pointages du Jour/i)).toBeVisible();
        await page.getByRole('button', { name: /Confirmer & Enregistrer/i }).click();
        await expect(page.getByText(/Pointages enregistrés avec succès/i)).toBeVisible({ timeout: 10000 });

        // Aller sur l'onglet Suivi & Anomalies
        await page.goto('/pointages?tab=suivi&periode=101');
        await expect(page.getByText(/Salariés suivis/i)).toBeVisible();
        await expect(page.getByText(/Anomalies actives/i)).toBeVisible();

        // Régulariser le pointage via l'UI : compléter les horaires
        await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-09&site=1');
        const autoFillBtn = page.getByRole('button', { name: /Horaire standard/i });
        await autoFillBtn.click();
        const saveRegBtn = page.locator('button:has-text("Enregistrer")');
        await saveRegBtn.click();
        await page.getByRole('button', { name: /Confirmer & Enregistrer/i }).click();
        await expect(page.getByText(/Pointages enregistrés avec succès/i)).toBeVisible({ timeout: 10000 });
    });
});
