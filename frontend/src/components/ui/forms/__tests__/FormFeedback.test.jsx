// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import Alert from '../../feedback/Alert';
import FormError from '../../feedback/FormError';
import Input, { Select, Textarea } from '../Input';

describe('messages globaux et retours de formulaire', () => {
  let host;
  let root;

  beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
  });

  it('annonce les avertissements globaux poliment et les erreurs globales immédiatement', async () => {
    await act(async () => root.render(
      <div><Alert type="warning" message="Règle à vérifier" /><Alert variant="danger" message="Chargement impossible" /></div>
    ));

    expect(host.querySelector('[role="status"]').getAttribute('aria-live')).toBe('polite');
    expect(host.querySelector('[role="alert"]').getAttribute('aria-live')).toBe('assertive');
    expect(host.querySelectorAll('[role="alert"]')).toHaveLength(1);
  });

  it('présente une erreur de soumission compacte, distincte de la bannière globale', async () => {
    await act(async () => root.render(<FormError message="Le code est déjà utilisé." />));
    const error = host.querySelector('[role="alert"]');

    expect(error.textContent).toContain('Le code est déjà utilisé.');
    expect(error.className).toContain('border-s-2');
    expect(error.className).not.toContain('p-3');
  });

  it('lie le feedback de champ au contrôle et expose aria-invalid', async () => {
    await act(async () => root.render(
      <div>
        <Input id="code" label="Code" error="Le code est obligatoire." />
        <Textarea id="motif" label="Motif" error="Le motif est obligatoire." />
        <Select id="type" label="Type" error="Sélection obligatoire."><option value="">Choisir</option></Select>
      </div>
    ));

    for (const id of ['code', 'motif', 'type']) {
      const control = host.querySelector(`#${id}`);
      expect(control.getAttribute('aria-invalid')).toBe('true');
      expect(host.querySelector(`#${control.getAttribute('aria-describedby')}`).getAttribute('role')).toBe('alert');
      expect(host.querySelector(`label[for="${id}"]`)).not.toBeNull();
    }
  });
});
