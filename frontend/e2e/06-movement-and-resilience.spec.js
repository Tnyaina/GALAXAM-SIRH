import { test, expect } from '@playwright/test';

async function loginAsDaf(page) {
    await page.goto('/');
    await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
    await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard/);
}

async function loginAsPointageAgent(page) {
    await page.goto('/');
    await page.locator('input[name="identifiant"]').fill('pointage@galaxam.com');
    await page.locator('input[name="password"]').fill('Test-Pointage-Only-Password-2026!');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/pointages/);
}

test.describe('E2E Scénario 6 — Mouvements Métier, RBAC Strict et Résilience Système', () => {

    test('6.1. Étanchéité RBAC : Refus d\'accès absolu de l\'Agent de Pointage au Module Paie', async ({ page }) => {
        await loginAsPointageAgent(page);

        // 1. Tentative d'accès direct à la racine du module Paie
        await page.goto('/paie');
        // Doit être redirigé vers pointages (car sans permission PAIE_READ)
        await expect(page).not.toHaveURL(/\/paie/);
        await expect(page).toHaveURL(/\/pointages/);

        // 2. Tentative d'accès direct à l'étape de calcul de la période 101
        await page.goto('/paie/periodes/101/calcul');
        await expect(page).not.toHaveURL(/\/paie/);
        await expect(page).toHaveURL(/\/pointages/);

        // 3. Tentative d'accès aux exports réglementaires et virements
        await page.goto('/paie/periodes/101/sorties');
        await expect(page).not.toHaveURL(/\/paie/);
        await expect(page).toHaveURL(/\/pointages/);

        // Vérification de l'absence du menu Paie dans la barre latérale pour l'Agent
        const paieMenu = page.getByRole('button', { name: /Paie & Rémunération/i });
        await expect(paieMenu).not.toBeVisible();
    });

    test('6.2. Résilience aux altérations d\'URL (ID période inexistant ou aberrant)', async ({ page }) => {
        await loginAsDaf(page);

        // 1. Navigation vers un identifiant de période inexistant (99999)
        await page.goto('/paie/periodes/99999/preparation');
        // L'application ne doit pas crasher (pas d'écran blanc)
        await expect(page.locator('body')).not.toBeEmpty();
        await expect(page.locator('header, nav, [role="banner"]').first()).toBeVisible();

        // 2. Navigation vers un ID négatif (-1)
        await page.goto('/paie/periodes/-1/calcul');
        await expect(page.locator('body')).not.toBeEmpty();
        await expect(page.locator('header, nav, [role="banner"]').first()).toBeVisible();

        // 3. Route inconnue 404
        await page.goto('/route-totalement-inexistante-pour-test-404');
        await expect(page.getByText(/404|Page non trouvée|introuvable/i).first()).toBeVisible({ timeout: 10000 });
    });

    test('6.3. Résilience au rechargement F5 (Idempotence et persistance du contexte)', async ({ page }) => {
        await loginAsDaf(page);

        // 1. Navigation vers l'étape de contrôle de la période 101
        await page.goto('/paie/periodes/101/controle');
        await expect(page.getByRole('heading', { name: /Étape 5 — Contrôle des bulletins/i })).toBeVisible({ timeout: 10000 });

        // Vérification des données initiales
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();

        // 2. Rechargement complet de la page (F5)
        await page.reload();

        // Vérification que le contexte est réhydraté sans perte d'état ni déconnexion
        await expect(page.getByRole('heading', { name: /Étape 5 — Contrôle des bulletins/i })).toBeVisible({ timeout: 15000 });
        await expect(page.locator('tr:has-text("EMP001")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP002")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP003")')).toBeVisible();
        await expect(page.locator('tr:has-text("EMP006")')).toBeVisible();

        // 3. Rechargement sur l'étape de préparation
        await page.goto('/paie/periodes/101/preparation');
        await expect(page.getByRole('heading', { name: /Étape 1 — Préparation de la campagne/i })).toBeVisible({ timeout: 10000 });
        await page.reload();
        await expect(page.getByRole('heading', { name: /Étape 1 — Préparation de la campagne/i })).toBeVisible({ timeout: 15000 });
        await expect(page.getByText(/Snapshot Pointage scellé & figé/i)).toBeVisible();
    });

    test('6.4. Parcours transversal complet multi-modules (Dashboard -> Congés -> Pointage -> Paie)', async ({ page }) => {
        await loginAsDaf(page);

        // 1. Dashboard
        await page.goto('/dashboard');
        await expect(page.getByText(/Tableau de bord|Vue d'ensemble/i).first()).toBeVisible({ timeout: 10000 });

        // 2. Navigation vers Congés
        await page.goto('/conges');
        await expect(page.getByText(/Congés & Absences|Soldes/i).first()).toBeVisible({ timeout: 10000 });

        // 3. Navigation vers Pointage
        await page.goto('/pointages');
        await expect(page.getByText(/Pointage & Présences|Saisie Quotidienne/i).first()).toBeVisible({ timeout: 10000 });

        // 4. Navigation vers Paie
        await page.goto('/paie');
        await expect(page.getByText(/Périodes de Paie|Campagnes/i).first()).toBeVisible({ timeout: 10000 });

        // Vérification que la session est restée intacte tout au long du parcours
        await expect(page.getByText('daf@galaxam.com').first()).toBeVisible();
    });
});
