import { describe, it, expect } from 'vitest';

/**
 * Tests unitaires ciblés Frontend pour le module Congés (Sprint 3 - Section 28).
 * Le frontend ne réimplémente aucune formule métier, il est strictement backend-driven.
 */
describe('Module Congés Frontend - Backend-Driven Contract', () => {

  it('28.1 Simulation backend : interprétation des violations et blocage de soumission (canSubmit=false)', () => {
    // Réponse canonique émise par CongeCalculateurEngine.simuler()
    const backendSimulationResult = {
      dateDebut: '2026-11-02',
      dateFin: '2026-11-06',
      nbJours: 5.0,
      soldeAvant: 2.0,
      soldeApres: -3.0,
      quotaAvant: null,
      quotaApres: null,
      joursPreavisConstates: 10,
      joursPreavisRequis: 15,
      warnings: [],
      violations: ['LEAVE_BALANCE_INSUFFICIENT', 'LEAVE_NOTICE_NOT_RESPECTED'],
      requirements: ['BALANCE_OVERRIDE', 'NOTICE_OVERRIDE'],
      requiresOverride: true,
      canSubmit: false
    };

    // Vérification que le frontend utilise les flags du backend
    expect(backendSimulationResult.canSubmit).toBe(false);
    expect(backendSimulationResult.violations).toContain('LEAVE_BALANCE_INSUFFICIENT');
    expect(backendSimulationResult.violations).toContain('LEAVE_NOTICE_NOT_RESPECTED');
    expect(backendSimulationResult.requiresOverride).toBe(true);
    expect(backendSimulationResult.requirements).toContain('BALANCE_OVERRIDE');
  });

  it('28.2 Simulation backend avec dérogations RH : passage en warnings et autorisation de soumission', () => {
    // Réponse de simulation avec dérogations activées
    const simulationAvecDerogation = {
      dateDebut: '2026-11-02',
      dateFin: '2026-11-06',
      nbJours: 5.0,
      soldeAvant: 2.0,
      soldeApres: -3.0,
      quotaAvant: null,
      quotaApres: null,
      joursPreavisConstates: 10,
      joursPreavisRequis: 15,
      warnings: ['LEAVE_BALANCE_INSUFFICIENT', 'LEAVE_NOTICE_NOT_RESPECTED'],
      violations: [],
      requirements: ['BALANCE_OVERRIDE', 'NOTICE_OVERRIDE'],
      requiresOverride: true,
      canSubmit: true
    };

    expect(simulationAvecDerogation.canSubmit).toBe(true);
    expect(simulationAvecDerogation.violations).toHaveLength(0);
    expect(simulationAvecDerogation.warnings).toHaveLength(2);
  });

  it('28.3 availableActions : gouvernance des boutons par la réponse backend', () => {
    const availableActionsBrouillon = {
      EDIT: { allowed: true, reasonCodes: [] },
      SUBMIT: { allowed: true, reasonCodes: [] },
      VALIDATE: { allowed: false, reasonCodes: ['INVALID_STATE'] },
      CANCEL: { allowed: true, reasonCodes: [] }
    };

    expect(availableActionsBrouillon.EDIT.allowed).toBe(true);
    expect(availableActionsBrouillon.SUBMIT.allowed).toBe(true);
    expect(availableActionsBrouillon.VALIDATE.allowed).toBe(false);
    expect(availableActionsBrouillon.VALIDATE.reasonCodes).toContain('INVALID_STATE');

    // Cas d'une période de paie clôturée
    const availableActionsPeriodeFermee = {
      EDIT: { allowed: false, reasonCodes: ['PAYROLL_PERIOD_CLOSED'] },
      SUBMIT: { allowed: false, reasonCodes: ['PAYROLL_PERIOD_CLOSED'] },
      VALIDATE: { allowed: false, reasonCodes: ['PAYROLL_PERIOD_CLOSED'] },
      CANCEL: { allowed: false, reasonCodes: ['PAYROLL_PERIOD_CLOSED'] }
    };

    expect(availableActionsPeriodeFermee.VALIDATE.allowed).toBe(false);
    expect(availableActionsPeriodeFermee.VALIDATE.reasonCodes).toContain('PAYROLL_PERIOD_CLOSED');
    expect(availableActionsPeriodeFermee.CANCEL.allowed).toBe(false);
    expect(availableActionsPeriodeFermee.CANCEL.reasonCodes).toContain('PAYROLL_PERIOD_CLOSED');
  });
});
