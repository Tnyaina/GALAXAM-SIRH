import { describe, it, expect } from 'vitest';
import { manualValuePayload } from '../utils/manualVariableCalculation';
describe('saisie de calcul manuel', () => {
    it('accepte la virgule et conserve les entrées décimales', () => {
        expect(manualValuePayload({ methodeCalcul: 'QUANTITE_TAUX' }, { quantiteSaisie: '1,5', tauxSaisi: '2 500,125' })).toEqual({ quantiteSaisie: '1.5', tauxSaisi: '2500.125' });
    });
    it('ne transmet pas un montant client pour un pourcentage', () => {
        expect(manualValuePayload({ methodeCalcul: 'POURCENTAGE_SALAIRE_BASE' }, { tauxSaisi: '10', montant: '999999' })).toEqual({ tauxSaisi: '10' });
    });
    it('refuse valeurs incomplètes et taux hors plage', () => {
        expect(() => manualValuePayload({ methodeCalcul: 'QUANTITE_TAUX' }, { tauxSaisi: '10' })).toThrow();
        expect(() => manualValuePayload({ methodeCalcul: 'POURCENTAGE_SALAIRE_BASE' }, { tauxSaisi: '101' })).toThrow();
        expect(() => manualValuePayload({}, { montant: '1,123' })).toThrow();
    });
});
