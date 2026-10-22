import test from 'node:test';
import assert from 'node:assert/strict';
import { retirerElement, annulerSuppression, createSatelliteClientKey } from '../src/features/employees/components/modals/satelliteFormChanges.js';

test('les clés locales des nouveaux éléments sont distinctes et stables sur l’objet', () => {
    const first = { clientKey: createSatelliteClientKey(), nom: 'A' };
    const second = { clientKey: createSatelliteClientKey(), nom: 'B' };
    assert.notEqual(first.clientKey, second.clientKey);
    const updated = { ...first, nom: 'Modifié' };
    assert.equal(updated.clientKey, first.clientKey);
});

test('un enfant existant est supprimé seulement à la sauvegarde et peut être restauré', () => {
    const enfant = { idEnfant: 12, nom: 'Test' };
    const retire = retirerElement([enfant], [], 0, 'idEnfant');
    assert.deepEqual(retire.elements, []);
    assert.deepEqual(retire.suppressions.map(e => e.idEnfant), [12]);
    const restaure = annulerSuppression(retire.elements, retire.suppressions, 0);
    assert.deepEqual(restaure.elements, [enfant]);
    assert.deepEqual(restaure.suppressions, []);
});

test('un nouveau contact retiré ne crée aucune demande de suppression', () => {
    const retire = retirerElement([{ nom: 'Test' }], [], 0, 'idContact');
    assert.deepEqual(retire.elements, []);
    assert.deepEqual(retire.suppressions, []);
});

test('une suppression de contact ne retire pas les autres contacts', () => {
    const contacts = [{ idContact: 1 }, { idContact: 2 }];
    const retire = retirerElement(contacts, [], 0, 'idContact');
    assert.deepEqual(retire.elements, [contacts[1]]);
    assert.deepEqual(retire.suppressions, [contacts[0]]);
});
