// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import PayrollConfigurationTab from '../components/tabs/PayrollConfigurationTab';
import paieService from '../api/paieService';
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ hasPermission: () => true }) }));
vi.mock('@/components/ui/feedback/Toast', () => ({ useToast: () => ({ success: vi.fn() }) }));
vi.mock('../api/paieService', () => ({ default: {
    getConfigurationCatalogue: vi.fn(), creerRubriqueVariable: vi.fn(), modifierRubriqueVariable: vi.fn(), creerVersionRubrique: vi.fn(), creerTypeVariable: vi.fn(), modifierTypeVariable: vi.fn(),
} }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let root, host;
const choices = (value,label) => [{value,label}];
const rubrique = {id:1,code:'PRIME_TEST',libelle:'Prime de recette',sens:'GAIN',categorie:'PRIME',etapeCalcul:'BRUT',niveauConfiguration:'CONFIGURABLE',typeValeur:'MONTANT',niveauLibelle:'Configurable',peutModifier:true,peutVersionner:true,estActif:true,soumisIrsa:true,soumisCnaps:true,soumisSante:true};
const catalogue = {
    items:[rubrique], selection:null, totalElements:21,page:1,taille:10,famille:[],rubriquesCompatibles:[rubrique],
    validation:{codePattern:'[A-Z][A-Z0-9_]{2,39}',codeMessage:'Vérifiez le format du code.'},
    defautsRubrique:{categorie:'PRIME',sens:'GAIN',etapeCalcul:'BRUT',methodeCalcul:'MONTANT',politiqueArrondi:'CENTIME',conditionApplication:'TOUS',estActif:true},
    defautsType:{sens:'GAIN',categorie:'PRIME',estActif:true},
    options:{assiettes:[{key:'soumisIrsa',label:'IRSA'},{key:'soumisCnaps',label:'CNaPS'}],categories:[{value:'PRIME',label:'Prime',defauts:{sens:'GAIN',etapeCalcul:'BRUT'}}],etapes:[{value:'BRUT',label:'Dans le brut',sens:'GAIN',assiettesModifiables:true}],methodes:choices('MONTANT','Montant saisi'),arrondis:choices('CENTIME','Au centime'),conditions:choices('TOUS','Tous'),sens:choices('GAIN','Gain'),niveaux:choices('CONFIGURABLE','Configurable'),statuts:choices('actif','Actifs'),tris:choices('ordre','Ordre du catalogue')},
};
function LocationView(){return <output data-testid="location">{useLocation().search}</output>;}
async function render(url='/admin/parametres?module=paie&tab=rubriques_variables', domaine='rubriques') {
    await act(async()=>root.render(<MemoryRouter initialEntries={[url]}><PayrollConfigurationTab domaine={domaine} /><LocationView /></MemoryRouter>));
}
async function click(text){await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.trim()===text).click());}
async function fill(label,value){const input=[...document.querySelectorAll('input')].find(i=>document.querySelector(`label[for="${i.id}"]`)?.textContent.startsWith(label));await act(async()=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));});}
beforeEach(()=>{vi.clearAllMocks();host=document.createElement('div');document.body.append(host);root=createRoot(host);paieService.getConfigurationCatalogue.mockResolvedValue(catalogue);});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();});
describe('Paramétrage paie',()=>{
    it('consulte d’abord le catalogue et envoie la pagination au backend en conservant le contexte',async()=>{
        await render();expect(host.querySelector('[role="dialog"]')).toBeNull();
        expect(host.textContent).toContain('Cas exceptionnel :');
        expect(host.textContent).toContain('Cas courant');
        expect(host.querySelector('section[aria-label="Fiche de configuration"]').textContent).toContain('Prime de recette');
        const next=host.querySelector('button[aria-label="Page suivante"]');
        await act(async()=>next.click());
        expect(paieService.getConfigurationCatalogue.mock.calls.at(-1)[0].page).toBe(2);
        expect(host.querySelector('output').textContent).toContain('module=paie');
        expect(host.querySelector('output').textContent).toContain('rb_page=2');
    });
    it('affiche les erreurs sous les champs sans bandeau global et conserve les valeurs',async()=>{
        await render();await click('Nouvelle règle manuelle');await fill('Libellé','Ma prime');
        await act(async()=>document.querySelector('[role="dialog"] form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
        expect(document.querySelector('[role="dialog"]').textContent).toContain('Renseignez ce champ.');
        expect(document.querySelector('[role="dialog"]').textContent).not.toContain('L’enregistrement');
        expect(paieService.creerRubriqueVariable).not.toHaveBeenCalled();
        expect([...document.querySelectorAll('input')].some(i=>i.value==='Ma prime')).toBe(true);
    });
    it('bloque la double soumission et préserve la saisie après une erreur réseau',async()=>{
        let reject;
        paieService.creerRubriqueVariable.mockImplementation(()=>new Promise((_,r)=>{reject=r;}));
        await render();await click('Nouvelle règle manuelle');await fill('Code','PRIME_TEST');await fill('Libellé','Ma prime');await fill('Référence','Accord interne');await fill('Début','2027-01-01');
        const form=document.querySelector('[role="dialog"] form');
        await act(async()=>{form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));});
        expect(paieService.creerRubriqueVariable).toHaveBeenCalledTimes(1);
        await act(async()=>reject(new Error('Connexion interrompue. Réessayez.')));
        expect(document.querySelector('[role="dialog"]')).not.toBeNull();
        expect([...document.querySelectorAll('input')].some(i=>i.value==='Ma prime')).toBe(true);
        expect(document.querySelector('[role="dialog"]').textContent).toContain('Connexion interrompue');
    });
    it('verrouille l’identité et la liaison d’un type déjà utilisé',async()=>{
        const type={id:2,code:'PRIME_UTILISEE',libelle:'Prime utilisée',sens:'GAIN',idRubrique:1,estActif:true,peutModifier:true,identiteVerrouillee:true,liaisonVerrouillee:true,rubrique};
        paieService.getConfigurationCatalogue.mockResolvedValue({...catalogue,items:[type],selection:type});
        await render('/admin/parametres?module=paie&tab=types_variables&vt_selection=2','types');
        await click('Modifier');
        const dialog=document.querySelector('[role="dialog"]');
        expect(dialog.querySelector('input').disabled).toBe(true);
        expect([...dialog.querySelectorAll('select')].every(select=>select.disabled)).toBe(true);
        expect([...dialog.querySelectorAll('input')].some(input=>input.value==='Prime utilisée'&&!input.disabled)).toBe(true);
    });
    it('guide la création d’un type vers une rubrique manuelle recherchable',async()=>{
        paieService.getConfigurationCatalogue.mockResolvedValue({...catalogue,items:[],rubriquesCompatibles:[rubrique]});
        await render('/paie/configuration?vue=types','types');
        await act(async()=>await new Promise(resolve=>setTimeout(resolve,0)));
        await click('Nouveau type');
        const dialog=document.querySelector('[role="dialog"]');
        expect(dialog.textContent).toContain('Le montant saisi suivra la rubrique associée');
        await act(async()=>{[...dialog.querySelectorAll('button')].find(button=>button.textContent.includes('Rechercher une rubrique manuelle')).click(); await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));});
        expect(document.body.textContent).toContain('Prime de recette');
    });
    it('annule la requête précédente lorsque la sélection change',async()=>{
        await render();const firstSignal=paieService.getConfigurationCatalogue.mock.calls.at(-1)[1];
        paieService.getConfigurationCatalogue.mockResolvedValue({...catalogue,selection:rubrique});
        await act(async()=>host.querySelector('ul[aria-label="Rubriques de paie"] button').click());
        expect(firstSignal.aborted).toBe(true);
        expect(paieService.getConfigurationCatalogue.mock.calls.at(-1)[0].selection).toBe(1);
    });
});
