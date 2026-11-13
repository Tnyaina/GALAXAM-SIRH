import { useCallback, useEffect, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

// Les valeurs métier restent internes aux composants ; seuls les chemins sont exposés.
const routeGroups = {
  organisation: {
    base: '/admin/organisation', keys: ['tab'], defaultValues: ['filiales'],
    entries: [
      ['filiales', 'filiales'], ['departements', 'departements'], ['fonctions', 'fonctions'],
    ],
  },
  securite: {
    base: '/admin/securite', keys: ['tab'], defaultValues: ['users'],
    entries: [['users', 'utilisateurs'], ['roles', 'roles'], ['menus', 'menus']],
  },
  referentiels: {
    base: '/admin/referentiels-rh', keys: ['tab'], defaultValues: ['carriere'],
    entries: [['carriere', 'categories'], ['modes_calcul', 'calcul-salaire'], ['motifs', 'motifs'], ['evenements', 'evenements'], ['docTypes', 'documents'], ['rh', 'contrats']],
  },
  parametres: {
    base: '/admin/parametres', keys: ['module', 'tab'], defaultValues: ['paie', 'reglementation_fiscale'],
    entries: [
      ['paie', 'reglementation_fiscale', 'paie/fiscalite'],
      ['paie', 'cotisations_irsa', 'paie/cotisations'],
      ['paie', 'parametres_globaux_paie', 'paie/regles'],
      ['paie', 'indemnites_sites', 'paie/indemnites'],
      ['paie', 'modes_paiement', 'paie/paiements'],
      ['general', 'parametres_globaux_systeme', 'general/constantes'],
      ['general', 'parametres_globaux_contrats', 'general/contrats'],
      ['conges', 'parametres_globaux_conges', 'conges/constantes'],
      ['conges', 'regles_acquisition', 'conges/acquisition'],
      ['pointage', 'parametres_globaux_pointage', 'pointage/constantes'],
      ['calendrier', 'calendrier', 'calendrier'],
      ['import', 'employes', 'import/employes'],
    ],
  },
  conges: {
    base: '/conges', keys: ['tab'], defaultValues: ['planning'],
    entries: [['planning', 'planning'], ['demandes', 'demandes'], ['periodes', 'droits'], ['mes-demandes', 'mes-demandes']],
  },
  pointages: {
    base: '/pointages', keys: ['tab'], defaultValues: ['saisie'],
    entries: [['saisie', 'saisie'], ['saisie-employe', 'saisie-employe'], ['suivi', 'suivi'], ['arbitrage', 'arbitrage']],
  },
  paie: {
    base: '/paie', keys: ['tab'], defaultValues: ['centre'],
    entries: [['centre', 'centre'], ['periodes', 'cycles'], ['variables', 'variables']],
  },
  paieConfiguration: {
    base: '/paie/configuration', keys: ['vue'], defaultValues: ['rubriques'],
    entries: [['rubriques', 'rubriques'], ['types', 'types']],
  },
  declarations: {
    base: '/paie/declarations', keys: ['vue'], defaultValues: ['mensuel'],
    entries: [['mensuel', 'mensuel'], ['trimestriel', 'trimestriel'], ['annuel', 'annuel']],
  },
  carrieres: {
    base: '/carrieres', keys: ['tab'], defaultValues: ['contrats'],
    entries: [['contrats', 'contrats'], ['mouvements', 'mouvements']],
  },
};

const valueKey = (values) => values.join('\u0000');

const cleanNavigationParams = (group, params) => {
  group.keys.forEach(key => params.delete(key));
  if (group.base === '/paie/configuration') params.delete('mode');
  if (group.base === '/admin/parametres') {
    for (const [key, value] of params) {
      if (key.endsWith('_statut') && value === 'EN_VIGUEUR') params.delete(key);
      if ((key.endsWith('_nature') || key.endsWith('_etat') || key.endsWith('_categorie')) && value === 'ALL') params.delete(key);
      if (key.endsWith('_q') && !value) params.delete(key);
      if (key.endsWith('_tab')) params.delete(key);
    }
  }
  return params;
};

export function readablePathFor(groupName, values) {
  const group = routeGroups[groupName];
  if (!group) return null;
  const entry = group.entries.find(item => valueKey(item.slice(0, group.keys.length)) === valueKey(values));
  if (!entry) return null;
  return `${group.base}/${entry[group.keys.length]}`;
}

export function useReadableTabs(groupName) {
  const group = routeGroups[groupName];
  const location = useLocation();
  const navigate = useNavigate();
  if (!group) throw new Error(`Groupe de routes inconnu : ${groupName}`);

  const activeValues = useMemo(() => {
    const pathEntry = group.entries.find(item => `${group.base}/${item[group.keys.length]}` === location.pathname);
    return pathEntry
      ? pathEntry.slice(0, group.keys.length)
      : group.defaultValues;
  }, [group, location.pathname]);
  const params = useMemo(() => {
    const next = new URLSearchParams(location.search);
    group.keys.forEach((key, index) => next.set(key, activeValues[index]));
    return next;
  }, [group, location.search, activeValues]);

  useEffect(() => {
    const target = readablePathFor(groupName, activeValues);
    if (!target) return;
    const next = cleanNavigationParams(group, new URLSearchParams(location.search));
    const destination = `${target}${next.size ? `?${next}` : ''}${location.hash}`;
    if (`${location.pathname}${location.search}${location.hash}` !== destination) navigate(destination, { replace: true });
  }, [group, groupName, location.pathname, location.search, location.hash, activeValues, navigate]);

  const setParams = useCallback((update, options = {}) => {
    const next = typeof update === 'function' ? update(new URLSearchParams(params)) : update;
    const resolved = new URLSearchParams(next);
    const values = group.keys.map((key, index) => resolved.get(key) || group.defaultValues[index]);
    const target = readablePathFor(groupName, values) || location.pathname;
    cleanNavigationParams(group, resolved);
    navigate(`${target}${resolved.size ? `?${resolved}` : ''}`, { replace: options.replace });
  }, [group, groupName, location.pathname, params, navigate]);
  return [params, setParams];
}
