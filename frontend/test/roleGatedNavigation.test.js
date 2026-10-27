import assert from 'node:assert/strict';
import test from 'node:test';
import { canAccessRoleGatedRoute, filterMenuByUser } from '../src/utils/sidebarUtils.js';

test('la route Organisation exige ORGANISATION_WRITE ou ROLE_ADMIN', () => {
  const user = { role: 'ROLE_RH', permissions: ['ORGANISATION_READ'] };

  assert.equal(canAccessRoleGatedRoute('/admin/organisation', user), false);
  assert.equal(canAccessRoleGatedRoute('/parametres/organisation', user), false);
  assert.equal(canAccessRoleGatedRoute('/admin/organisation', { role: 'ROLE_RH', permissions: ['ORGANISATION_WRITE'] }), true);
  assert.equal(canAccessRoleGatedRoute('/employes', user), true);
  assert.equal(canAccessRoleGatedRoute('/admin/organisation', { role: 'ROLE_ADMIN' }), true);
});

test('le menu RH non administrateur conserve les autres entrées et retire les liens admin', () => {
  const menu = [
    { idMenu: 1, libelle: 'Personnel', sousMenus: [{ idMenu: 2, libelle: 'Employés', href: '/employes' }] },
    { idMenu: 3, libelle: 'Administration', sousMenus: [{ idMenu: 4, libelle: 'Organisation', href: '/admin/organisation' }] },
  ];

  const filtered = filterMenuByUser(menu, { role: 'ROLE_RH', permissions: ['ORGANISATION_READ', 'EMPLOYE_READ'] });
  assert.deepEqual(filtered.map(({ idMenu }) => idMenu), [1]);
  assert.deepEqual(filtered[0].sousMenus.map(({ href }) => href), ['/employes']);
});

test('ROLE_ADMIN garde les entrées d’administration dans son menu', () => {
  const menu = [{ idMenu: 3, libelle: 'Administration', sousMenus: [{ idMenu: 4, libelle: 'Organisation', href: '/admin/organisation' }] }];
  assert.deepEqual(filterMenuByUser(menu, { role: 'ROLE_ADMIN' }), menu);
});
