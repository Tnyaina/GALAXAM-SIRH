import { test, expect } from '@playwright/test';

test.describe('E2E Scénario 1 — Authentification et Double Barrière RBAC', () => {

    test('1.1. Agent de Pointage : Login, Redirection ciblée et Menus restreints', async ({ page }) => {
        await page.goto('/');

        // Remplissage du formulaire de connexion
        await page.locator('input[name="identifiant"]').fill('pointage@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Pointage-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();

        // Vérification de la redirection automatique vers le module Pointage
        await expect(page).toHaveURL(/\/pointages/);

        // Menu Pointage présent dans la navigation
        await expect(page.getByRole('button', { name: /Pointage & Présences/i })).toBeVisible();

        // Les menus sensibles (Paie et Administration) doivent être strictement absents
        await expect(page.getByRole('button', { name: /Paie & Rémunération/i })).not.toBeVisible();
        await expect(page.getByRole('button', { name: /Administration/i })).not.toBeVisible();

        // Tentative de navigation directe vers une URL admin sensible (Double barrière UI)
        await page.goto('/admin/securite');
        // L'UI redirige l'agent vers son périmètre autorisé (/pointages) plutôt que d'autoriser l'accès
        await expect(page).toHaveURL(/\/pointages/);

        // Tentative de navigation directe vers le module Paie
        await page.goto('/paie');
        await expect(page).toHaveURL(/\/pointages/);

        // Double barrière Backend : appel API direct avec le token de l'agent
        const token = await page.evaluate(() => localStorage.getItem('token'));
        const response = await page.request.get('http://localhost:8085/api/paie/periodes', {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        // L'API backend Spring Boot doit rejeter avec 403 Forbidden
        expect(response.status()).toBe(403);
    });

    test('1.2. Direction Financière (DAF / Admin) : Plein accès aux modules', async ({ page }) => {
        await page.goto('/');

        await page.locator('input[name="identifiant"]').fill('daf@galaxam.com');
        await page.locator('input[name="password"]').fill('Test-Admin-Only-Password-2026!');
        await page.locator('button[type="submit"]').click();

        // Redirection vers le tableau de bord
        await expect(page).toHaveURL(/\/dashboard/);

        // Vérification de la présence de tous les menus dans la navigation
        await expect(page.getByRole('link', { name: /Tableau de bord/i })).toBeVisible();
        await expect(page.getByRole('button', { name: /Pointage & Présences/i })).toBeVisible();
        await expect(page.getByRole('button', { name: /Congés & Absences/i })).toBeVisible();
        await expect(page.getByRole('button', { name: /Paie & Rémunération/i })).toBeVisible();
        await expect(page.getByRole('button', { name: /Administration/i })).toBeVisible();

        // Navigation vers le panneau de sécurité admin
        await page.goto('/admin/securite');
        await expect(page).toHaveURL(/\/admin\/securite/);

        // Navigation vers la gestion de la paie
        await page.goto('/paie');
        await expect(page).toHaveURL(/\/paie/);
    });

    test('1.3. Rejet avec message explicite lors d identifiants invalides', async ({ page }) => {
        await page.goto('/');

        await page.locator('input[name="identifiant"]').fill('inconnu@galaxam.com');
        await page.locator('input[name="password"]').fill('MauvaisMotDePasse!');
        await page.locator('button[type="submit"]').click();

        // Doit afficher un message d'erreur et ne pas rediriger
        await expect(page.locator('[role="alert"], div:has-text("incorrect")').first()).toBeVisible();
        await expect(page).toHaveURL(/\//);
    });
});
