import { test, expect } from '@playwright/test';

test.describe('E2E Scénario 7 — Workflow Congés & Absences : Demande, Modal d\'Audit Comptable, Validation DAF et Imputation Ledger', () => {

    test('4.1. Vérification solde initial EMP003 (15.0 j)', async ({ page }) => {
        // Connexion DAF
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/dashboard/);

        // Consultation de la fiche employé EMP003 (Congés)
        await page.goto('/employes/3?tab=conges');
        await expect(page.getByText(/15(\.00)?\s*j/i).first()).toBeVisible({ timeout: 10000 });
    });

    test('4.2. Création d\'une demande de congé annuel (2 jours) pour EMP003', async ({ page }) => {
        // Connexion DAF
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/dashboard/);

        // Navigation vers le module Congés
        await page.goto('/conges?tab=demandes');
        await expect(page.getByRole('button', { name: /Nouvelle demande/i })).toBeVisible();
        await page.getByRole('button', { name: /Nouvelle demande/i }).click();

        // Modale ouverte
        await expect(page.getByText(/Nouvelle demande de congé ou d'absence/i)).toBeVisible();

        // Sélection de l'employé EMP003
        const empInput = page.locator('input[placeholder*="Rechercher par nom" i]');
        await expect(empInput).toBeVisible();
        await empInput.fill('EMP003');
        const empOption = page.locator('div[role="button"]:has-text("EMP003"), div:has-text("EMP003")').last();
        await empOption.click();

        // Type de congé : Congé annuel payé
        const typeSelectBtn = page.getByRole('button', { name: /Sélectionner un type de congé/i });
        await expect(typeSelectBtn).toBeVisible();
        await typeSelectBtn.click();
        await page.locator('div[role="dialog"]').getByText('Congé annuel payé').first().click();

        // Dates : 2 jours (2026-10-05 au 2026-10-06)
        const dateInputs = page.locator('div[role="dialog"] input[type="date"]');
        await dateInputs.nth(0).fill('2026-10-05');
        await dateInputs.nth(1).fill('2026-10-06');

        // Vérification du calcul projeté (2 jours demandés)
        await expect(page.locator('div[role="dialog"]').getByText(/2\s*jours/i).first()).toBeVisible({ timeout: 5000 });

        // Soumission
        const submitBtn = page.getByRole('button', { name: /Soumettre la demande/i });
        await expect(submitBtn).toBeVisible();
        await submitBtn.click();

        // Toast de confirmation
        await expect(page.getByText(/enregistrée avec succès|créée avec succès/i)).toBeVisible({ timeout: 10000 });
    });

    test('4.3. Contrôle comptable avant validation et Approbation DAF', async ({ page }) => {
        // Connexion DAF
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/dashboard/);

        // Navigation vers les demandes en attente
        await page.goto('/conges?tab=demandes');
        await page.getByRole('button', { name: /En attente/i }).click();

        // La ligne EMP003 doit être présente avec bouton Valider
        const empRow = page.locator('tr:has-text("EMP003")');
        await expect(empRow).toBeVisible({ timeout: 10000 });
        const validerBtn = empRow.getByRole('button', { name: /Valider/i });
        await expect(validerBtn).toBeVisible();
        await validerBtn.click();

        // Modale d'audit comptable de validation
        await expect(page.getByText(/Confirmer la validation/i)).toBeVisible();
        await expect(page.getByText(/Solde Avant/i)).toBeVisible();
        await expect(page.locator('div[role="dialog"]').getByText('15.00 j')).toBeVisible();
        await expect(page.getByText(/Demandés/i)).toBeVisible();
        await expect(page.locator('div[role="dialog"]').getByText('2.00 j', { exact: true })).toBeVisible();
        await expect(page.getByText(/Débités/i)).toBeVisible();
        await expect(page.locator('div[role="dialog"]').getByText('-2.00 j', { exact: true })).toBeVisible();
        await expect(page.getByText(/Solde Après/i)).toBeVisible();
        await expect(page.locator('div[role="dialog"]').getByText('13.00 j')).toBeVisible();

        // Confirmer la validation
        const confirmBtn = page.getByRole('button', { name: /Valider la demande/i });
        await confirmBtn.click();

        // Toast succès
        await expect(page.getByText(/Demande validée/i)).toBeVisible({ timeout: 10000 });
    });

    test('4.4. Vérification de l\'imputation comptable et mise à jour du solde restant (13.00 j)', async ({ page }) => {
        // Connexion DAF
        await page.goto('/');
        await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();
        await expect(page).toHaveURL(/\/dashboard/);

        // Consultation de la fiche employé EMP003
        await page.goto('/employes/3?tab=conges');

        // Le nouveau solde restant affiché doit être exactement 13.00 j (sans recalcul frontend)
        await expect(page.getByText(/13(\.00)?\s*j/i).first()).toBeVisible({ timeout: 10000 });
    });
});
