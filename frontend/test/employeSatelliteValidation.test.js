import test from 'node:test';
import assert from 'node:assert/strict';
import { validerSatellitesEmploye } from '../src/features/employees/utils/employeSatelliteValidation.js';

const referenceDate = new Date(2026, 5, 15, 12);

test('valide les champs obligatoires d’un enfant et tolère les champs facultatifs absents', () => {
    assert.deepEqual(validerSatellitesEmploye('famille', {
        enfants: [{ nom: 'Rakoto', prenom: 'Miora', dateNaissance: '2018-03-02' }],
    }, referenceDate), {});

    const errors = validerSatellitesEmploye('famille', {
        enfants: [{ nom: ' ', prenom: '', dateNaissance: '' }],
    }, referenceDate);
    assert.match(errors.enfant_0_nom, /nom.*obligatoire/);
    assert.match(errors.enfant_0_prenom, /prénom.*obligatoire/);
    assert.match(errors.enfant_0_dateNaissance, /date de naissance.*obligatoire/);
});

test('refuse les éléments enfant et contact null sans exception technique', () => {
    assert.match(validerSatellitesEmploye('famille', { enfants: [null] }, referenceDate).enfant_0_objet, /n'est pas renseigné/);
    assert.match(validerSatellitesEmploye('urgence', { contactsUrgence: [null] }, referenceDate).contact_0_objet, /n'est pas renseigné/);
});

test('valide plusieurs contacts et exige nom, prénom et téléphone', () => {
    assert.deepEqual(validerSatellitesEmploye('urgence', {
        contactsUrgence: [
            { nom: 'Rabe', prenom: 'Soa', telephone: '+261340000001' },
            { nom: 'Rasoa', prenom: 'Tiana', telephone: '+261340000002', adresse: 'Centre-ville' },
        ],
    }, referenceDate), {});

    const errors = validerSatellitesEmploye('urgence', {
        contactsUrgence: [{ nom: '', prenom: ' ', telephone: '', adresse: 'Adresse partielle' }],
    }, referenceDate);
    assert.match(errors.contact_0_nom, /nom.*obligatoire/);
    assert.match(errors.contact_0_prenom, /prénom.*obligatoire/);
    assert.match(errors.contact_0_telephone, /téléphone.*obligatoire/);
});

test('une ligne de contact entièrement vide reste facultative', () => {
    assert.deepEqual(validerSatellitesEmploye('urgence', {
        contactsUrgence: [{ nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }],
    }, referenceDate), {});
});

test('conserve le contrôle de dates déjà appliqué aux enfants', () => {
    const errors = validerSatellitesEmploye('famille', {
        enfants: [{ nom: 'Rabe', prenom: 'Soa', dateNaissance: '2027-01-01', dateFinScolarite: '2010-01-01' }],
    }, referenceDate);
    assert.match(errors.enfant_0_dateNaissance, /futur/);
    assert.match(errors.enfant_0_dateFinScolarite, /précéder la naissance/);
});
