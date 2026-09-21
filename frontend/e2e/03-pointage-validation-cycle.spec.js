import { test, expect } from '@playwright/test';

test.describe('E2E Scénarios 4, 5 & 6 — Cycle de Validation du Pointage : Transmission, Rejet DAF et Gel Snapshot', () => {

    test.beforeAll(async ({ browser }) => {
        const page = await browser.newPage();
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('pointage@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Pointage-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/pointages/);
        await page.goto('/pointages?tab=suivi&periode=101');
        await expect(page.getByText(/Salariés suivis/i)).toBeVisible();
        const blockingMessage = page.getByText(/anomalie\(s\) bloquante\(s\) non régularisée\(s\)/i);
        const hasBlockingAnomaly = await blockingMessage.waitFor({ state: 'visible', timeout: 3000 })
            .then(() => true)
            .catch(() => false);
        if (!hasBlockingAnomaly) {
            await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-08&site=1');
            await expect(page.locator('table')).toBeVisible();
            await page.getByRole('button', { name: /Horaire standard/i }).click();
            await page.locator('button:has-text("Enregistrer")').click();
            await expect(page.getByText(/Récapitulatif & Validation des Pointages du Jour/i)).toBeVisible();
            await page.getByRole('button', { name: /Confirmer & Enregistrer/i }).click();
            await expect(page.getByText(/Pointages enregistrés avec succès/i)).toBeVisible({ timeout: 10000 });
            await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-09&site=1');
            await expect(page.locator('table')).toBeVisible();
            const firstRow = page.locator('table tbody tr').first();
            const entreeInput = firstRow.locator('input[placeholder="--:--"]').first();
            await entreeInput.fill('08:00');
            await page.locator('button:has-text("Enregistrer")').click();
            await expect(page.getByText(/Récapitulatif & Validation des Pointages du Jour/i)).toBeVisible();
            await page.getByRole('button', { name: /Confirmer & Enregistrer/i }).click();
            await expect(page.getByText(/Pointages enregistrés avec succès/i)).toBeVisible({ timeout: 10000 });
            await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-09&site=1');
            await page.getByRole('button', { name: /Horaire standard/i }).click();
            await page.locator('button:has-text("Enregistrer")').click();
            await page.getByRole('button', { name: /Confirmer & Enregistrer/i }).click();
            await expect(page.getByText(/Pointages enregistrés avec succès/i)).toBeVisible({ timeout: 10000 });
        }
        await page.close();
    });

    test('3.1. Blocage anomalie, Régularisation, Transmission par l\'Agent (SUBMIT) et verrouillage de la saisie', async ({ page }) => {
        // Connexion Agent
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('pointage@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Pointage-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/pointages/);

        // Navigation vers l'étape Suivi & Anomalies
        await page.goto('/pointages?tab=suivi&periode=101');
        await expect(page.getByText(/Salariés suivis/i)).toBeVisible();

        // Vérification de la détection de l'anomalie bloquante créée au scénario 2
        await expect(page.getByText(/anomalie\(s\) bloquante\(s\) non régularisée\(s\)/i)).toBeVisible();

        // Le bouton "Transmettre" ne doit pas être cliquable tant qu'il y a un point bloquant
        const transmitBtn = page.getByRole('button', { name: /^Transmettre$/i });
        await expect(transmitBtn).not.toBeVisible();

        // Régularisation de l'anomalie de saisie pour EMP002
        const regulariserBtn = page.locator('tr:has-text("EMP002")').getByRole('button', { name: /Régulariser/i }).first();
        await expect(regulariserBtn).toBeVisible();
        await regulariserBtn.click();

        // Modale de régularisation ouverte
        await expect(page.getByText(/Régularisation du pointage/i)).toBeVisible();
        const motifTextarea = page.locator('textarea[placeholder*="motif" i]');
        await expect(motifTextarea).toBeVisible();
        await motifTextarea.fill('Oubli de badgeage régularisé - horaires standard confirmés');

        // Enregistrer la régularisation
        const saveModalBtn = page.getByRole('button', { name: /Enregistrer la régularisation/i });
        await saveModalBtn.click();
        await expect(page.getByText(/Pointage régularisé avec succès/i)).toBeVisible({ timeout: 10000 });

        // Une fois régularisée, le bouton Transmettre devient disponible
        await expect(transmitBtn).toBeVisible();
        await transmitBtn.click();

        // Confirmer la transmission dans la modale
        const confirmDialogBtn = page.getByRole('button', { name: /Confirmer et transmettre/i });
        await expect(confirmDialogBtn).toBeVisible();
        await confirmDialogBtn.click();

        // Notification de succès
        await expect(page.getByText(/Période transmise|avec succès/i)).toBeVisible({ timeout: 10000 });

        // Vérification du verrouillage agent de la saisie quotidienne
        await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-08&site=1');
        const autoFillBtn = page.getByRole('button', { name: /Horaire standard/i });
        await expect(autoFillBtn).toBeDisabled();
    });

    test('3.2. Examen DAF, Rejet avec motif explicite (REJECT) et réouverture pour correction', async ({ page }) => {
        // Connexion DAF
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/dashboard/);

        // Navigation vers le cockpit d'arbitrage pointage
        await page.goto('/pointages?tab=arbitrage&periode=101');
        await expect(page.locator('table')).toBeVisible();

        // Le bouton "Renvoyer pour correction" doit être accessible pour la DAF
        const rejectBtn = page.getByRole('button', { name: /Renvoyer pour correction/i });
        await expect(rejectBtn).toBeVisible();
        await rejectBtn.click();

        // Saisie du motif de rejet
        const textarea = page.locator('textarea');
        await expect(textarea).toBeVisible();
        await textarea.fill('Anomalies d\'heures supplémentaires à vérifier sur semaine 2');

        // Confirmation du renvoi
        const modalRejectBtn = page.locator('div[role="dialog"] button:has-text("Renvoyer pour correction")');
        await modalRejectBtn.click();

        // Notification de renvoi
        await expect(page.getByText(/Période renvoyée/i)).toBeVisible({ timeout: 10000 });

        // Déconnexion / Reconnexion de l'Agent pour vérifier que la période est réouverte
        await page.evaluate(() => localStorage.clear());
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('pointage@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Pointage-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/pointages/);

        // La saisie est de nouveau possible (période réouverte EN_COURS)
        await page.goto('/pointages?tab=saisie&periode=101&date=2026-09-08&site=1');
        const autoFillBtn = page.getByRole('button', { name: /Horaire standard/i });
        await expect(autoFillBtn).toBeEnabled();
    });

    test('3.3. Re-transmission et Approbation finale DAF (APPROVE) avec gel du snapshot', async ({ page }) => {
        // 1. L'Agent re-transmet après correction
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('pointage@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Pointage-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/pointages/);

        await page.goto('/pointages?tab=suivi&periode=101');
        const transmitBtn = page.getByRole('button', { name: /^Transmettre$/i });
        await expect(transmitBtn).toBeVisible();
        await transmitBtn.click();
        await page.getByRole('button', { name: /Confirmer et transmettre/i }).click();
        await expect(page.getByText(/Période transmise|avec succès/i)).toBeVisible({ timeout: 10000 });

        // 2. La DAF se connecte et approuve officiellement la période
        await page.evaluate(() => localStorage.clear());
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/dashboard/);

        await page.goto('/pointages?tab=arbitrage&periode=101');
        await expect(page.locator('table')).toBeVisible();

        // Clic sur "Valider l'arbitrage & Sceller les heures"
        const approveBtn = page.getByRole('button', { name: /Valider l'arbitrage/i });
        await expect(approveBtn).toBeVisible();
        await approveBtn.click();

        // Confirmer dans la modale d'arbitrage & gel
        const confirmFreezeBtn = page.getByRole('button', { name: /Valider l'arbitrage & Sceller le pointage/i });
        await expect(confirmFreezeBtn).toBeVisible();
        await confirmFreezeBtn.click();

        // Notification de validation
        await expect(page.getByText(/Période validée avec succès/i)).toBeVisible({ timeout: 10000 });

        // La période est désormais verrouillée (bouton "Rafraîchir l'affichage" au lieu de "Actualiser les calculs")
        await expect(page.getByRole('button', { name: /Rafraîchir l'affichage/i })).toBeVisible();
    });
});
