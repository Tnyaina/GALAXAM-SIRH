import { User, Calendar, MapPin, Heart, Phone, Mail, Landmark, Shield, CreditCard, Baby, ShieldAlert } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

const InfoBlock = ({ label, value, mono = false, icon, href }) => (
    <div className="space-y-1">
        <p className="text-[11px] font-medium text-[var(--color-fg-muted)]">{label}</p>
        <div className="flex items-center gap-1.5">
            {icon && <span className="text-[var(--color-fg-subtle)] shrink-0">{icon}</span>}
            {href ? (
                <a
                    href={href}
                    className={`text-xs sm:text-[13px] font-bold text-brand-600 hover:text-brand-700 hover:underline truncate ${mono ? 'font-mono' : ''}`}
                >
                    {value || '—'}
                </a>
            ) : (
                <p className={`text-xs sm:text-[13px] font-bold text-[var(--color-fg)] truncate ${mono ? 'font-mono' : ''}`}>
                    {value || '—'}
                </p>
            )}
        </div>
    </div>
);

const fmtDate = (d) => {
    if (!d) return '—';
    try {
        const date = new Date(d);
        if (isNaN(date.getTime())) return '—';
        return date.toLocaleDateString('fr-FR');
    } catch {
        return '—';
    }
};

export default function FichePersonnelleTab({ 
    emp, 
    comptes = [], 
    conjoint = null, 
    enfants = [], 
    contactsUrgence = [] 
}) {
    if (!emp) return null;

    const hasConjoint = Boolean(conjoint && (conjoint.nom || conjoint.prenom));

    return (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-start">
            {/* Colonne 1 (Gauche - 6 cols) : Identité, Domicile & Urgence */}
            <div className="lg:col-span-6 space-y-4">
                {/* Carte 1 : État Civil & Identité */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 space-y-4">
                    <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <User size={14} className="text-brand-500" />
                            État Civil & Identité Juridique
                        </h3>
                        <div className="flex items-center gap-2">
                            {emp.situationFamiliale && (
                                <Badge variant="default" size="xs">
                                    {emp.situationFamiliale}
                                </Badge>
                            )}
                            <Badge variant="default" size="xs">
                                {emp.sexe === 'M' ? 'Masculin' : 'Féminin'}
                            </Badge>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-x-4 gap-y-3.5 pt-1">
                        <InfoBlock label="Date de naissance" value={fmtDate(emp.dateNaissance)} icon={<Calendar size={13} />} />
                        <InfoBlock label="Lieu de naissance" value={emp.lieuNaissance} icon={<MapPin size={13} />} />
                        <InfoBlock label="Nationalité" value={emp.nationalite || 'Malgache'} />
                        <InfoBlock label="Situation familiale" value={emp.situationFamiliale} icon={<Heart size={13} />} />

                        <InfoBlock label="Filiation (Père)" value={emp.pereNomPrenom} />
                        <InfoBlock label="Filiation (Mère)" value={emp.mereNomPrenom} />
                        <InfoBlock label="N° CIN" value={emp.cinNumero} mono icon={<CreditCard size={13} />} />
                        <InfoBlock label="Délivrance CIN" value={emp.cinDateDelivrance ? `${fmtDate(emp.cinDateDelivrance)} à ${emp.cinLieuDelivrance || '—'}` : '—'} />

                        {emp.cinDuplicataDate && (
                            <div className="col-span-2">
                                <InfoBlock label="Duplicata CIN" value={`${fmtDate(emp.cinDuplicataDate)} à ${emp.cinDuplicataLieu || '—'}`} />
                            </div>
                        )}
                        <div className="col-span-2">
                            <InfoBlock label="N° Permis de conduire" value={emp.permisConduireNumero} mono />
                        </div>
                    </div>
                </div>

                {/* Carte 2 : Domicile & Coordonnées */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 space-y-4">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2 pb-3 border-b border-[var(--color-border-subtle)]">
                        <Phone size={14} className="text-brand-500" />
                        Domicile & Coordonnées
                    </h3>

                    <div className="grid grid-cols-2 gap-x-4 gap-y-3.5 pt-1">
                        <div className="col-span-2">
                            <InfoBlock label="Adresse du domicile" value={emp.adresse} icon={<MapPin size={13} />} />
                        </div>
                        <InfoBlock label="Ville de résidence" value={emp.ville} />
                        <InfoBlock
                            label="Téléphone principal"
                            value={emp.telephone}
                            mono
                            icon={<Phone size={13} />}
                            href={emp.telephone ? `tel:${emp.telephone}` : null}
                        />
                        <div className="col-span-2">
                            <InfoBlock
                                label="Adresse e-mail"
                                value={emp.email}
                                icon={<Mail size={13} />}
                                href={emp.email ? `mailto:${emp.email}` : null}
                            />
                        </div>
                    </div>
                </div>

                {/* Carte 3 : Contacts d'Urgence */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 space-y-4">
                    <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <ShieldAlert size={14} className="text-brand-500" />
                            Contacts d'Urgence ({contactsUrgence.length})
                        </h3>
                        <span className="text-[10px] text-[var(--color-fg-subtle)]">
                            Accident / Incident site
                        </span>
                    </div>

                    {contactsUrgence.length > 0 ? (
                        <div className="space-y-2.5 pt-1">
                            {contactsUrgence.map((ct, i) => (
                                <div key={i} className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between">
                                    <div>
                                        <p className="text-xs font-bold text-[var(--color-fg)]">
                                            {ct.nom} {ct.prenom}
                                        </p>
                                        <p className="text-[11px] text-[var(--color-fg-muted)]">
                                            {ct.lienParente || 'Contact'} {ct.adresse ? `• ${ct.adresse}` : ''}
                                        </p>
                                    </div>
                                    {ct.telephone ? (
                                        <a
                                            href={`tel:${ct.telephone}`}
                                            className="font-mono text-xs font-bold text-brand-600 hover:text-brand-700 hover:underline flex items-center gap-1 shrink-0 ml-2"
                                        >
                                            <Phone size={11} />
                                            {ct.telephone}
                                        </a>
                                    ) : (
                                        <span className="text-xs text-[var(--color-fg-subtle)]">—</span>
                                    )}
                                </div>
                            ))}
                        </div>
                    ) : (
                        <p className="text-xs text-[var(--color-fg-muted)] p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center">
                            Aucun contact d'urgence renseigné.
                        </p>
                    )}
                </div>
            </div>

            {/* Colonne 2 (Droite - 6 cols) : Banque, Sécurité Sociale, Foyer */}
            <div className="lg:col-span-6 space-y-4">
                {/* Carte 4 : Sécurité Sociale & Affiliation */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 space-y-4">
                    <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <Landmark size={14} className="text-brand-500" />
                            Sécurité Sociale & Affiliation
                        </h3>
                        {emp.numAffiliationCnaps && (
                            <Badge variant="default" size="xs" className="font-mono">
                                CNaPS : {emp.numAffiliationCnaps}
                            </Badge>
                        )}
                    </div>

                    <div className="grid grid-cols-2 gap-x-4 gap-y-3 pt-1">
                        <InfoBlock label="N° Affiliation CNaPS" value={emp.numAffiliationCnaps} mono icon={<Shield size={13} />} />
                        <InfoBlock label="Date déclaration CNaPS" value={fmtDate(emp.dateDeclaration)} />
                    </div>

                    <div className="pt-2">
                        <h4 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)] mb-2">
                            Comptes Bancaires ({comptes.length})
                        </h4>
                        {comptes.length > 0 ? (
                            <Table>
                                <Thead>
                                    <Tr>
                                        <Th>Banque</Th>
                                        <Th>Agence</Th>
                                        <Th>N° Compte / RIB</Th>
                                        <Th align="center">Usage</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {comptes.map((cb, i) => (
                                        <Tr key={i}>
                                            <Td className="font-bold text-xs text-[var(--color-fg)]">
                                                {cb.nomBanque || cb.libelleBanque || cb.codeBanque || (cb.idBanque ? `Banque #${cb.idBanque}` : '—')}
                                            </Td>
                                            <Td className="text-xs text-[var(--color-fg-muted)]">{cb.agence || '—'}</Td>
                                            <Td isData className="font-mono text-xs font-bold text-[var(--color-fg)]">
                                                {cb.numCompteRib || '—'}
                                            </Td>
                                            <Td align="center">
                                                {cb.estPrincipal ? (
                                                    <Badge variant="success" size="xs" className="font-bold">Principal</Badge>
                                                ) : (
                                                    <Badge variant="default" size="xs">Secondaire</Badge>
                                                )}
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        ) : (
                            <p className="text-xs text-[var(--color-fg-muted)] p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center">
                                Aucun compte bancaire enregistré.
                            </p>
                        )}
                    </div>
                </div>

                {/* Carte 5 : Foyer & Famille Déclarée (Conjoint + Enfants) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 space-y-4">
                    <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <Baby size={14} className="text-brand-500" />
                            Foyer Déclaré & Situation Fiscale
                        </h3>
                        {enfants.length > 0 && (
                            <span className="text-[11px] font-semibold text-emerald-600 dark:text-emerald-400">
                                {enfants.filter(e => e.aCharge).length} à charge ({enfants.filter(e => {
                                    if (!e.aCharge) return false;
                                    if (e.estHandicape) return true;
                                    if (!e.dateNaissance) return false;
                                    const dob = new Date(e.dateNaissance);
                                    if (isNaN(dob.getTime())) return false;
                                    const now = new Date();
                                    let age = now.getFullYear() - dob.getFullYear();
                                    const m = now.getMonth() - dob.getMonth();
                                    if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) age--;
                                    return age < 21 || (age < 25 && e.estEtudiant && e.certificatFourni);
                                }).length * 2000} Ar déductibles IRSA)
                            </span>
                        )}
                    </div>

                    {/* Bloc Conjoint */}
                    <div className="space-y-2">
                        <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)] block">
                            Conjoint Déclaré
                        </span>
                        {hasConjoint ? (
                            <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] grid grid-cols-2 gap-3 text-xs">
                                <div>
                                    <span className="text-[10px] text-[var(--color-fg-muted)] block">Nom & Prénom</span>
                                    <span className="font-bold text-[var(--color-fg)]">{conjoint.nom} {conjoint.prenom}</span>
                                </div>
                                <div>
                                    <span className="text-[10px] text-[var(--color-fg-muted)] block">Naissance</span>
                                    <span>{fmtDate(conjoint.dateNaissance)}</span>
                                </div>
                                <div className="col-span-2">
                                    <span className="text-[10px] text-[var(--color-fg-muted)] block">Profession & Société</span>
                                    <span>{conjoint.fonction || 'Non renseigné'} {conjoint.societe ? `chez ${conjoint.societe}` : ''}</span>
                                </div>
                            </div>
                        ) : (
                            <p className="text-xs text-[var(--color-fg-muted)] p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                Aucun conjoint déclaré (Célibataire ou non déclaré).
                            </p>
                        )}
                    </div>

                    {/* Bloc Enfants */}
                    <div className="space-y-2 pt-2">
                        <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)] block">
                            Enfants ({enfants.length})
                        </span>
                        {enfants.length > 0 ? (
                            <div className="space-y-2">
                                {enfants.map((enf, i) => {
                                    let age = null;
                                    if (enf.dateNaissance) {
                                        const dob = new Date(enf.dateNaissance);
                                        if (!isNaN(dob.getTime())) {
                                            const now = new Date();
                                            age = now.getFullYear() - dob.getFullYear();
                                            const m = now.getMonth() - dob.getMonth();
                                            if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) age--;
                                        }
                                    }
                                    return (
                                        <div key={i} className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between text-xs">
                                            <div>
                                                <span className="font-bold text-[var(--color-fg)] block">
                                                    {enf.nom} {enf.prenom}
                                                </span>
                                                <span className="text-[11px] text-[var(--color-fg-muted)]">
                                                    {enf.sexe === 'M' ? 'Garçon' : enf.sexe === 'F' ? 'Fille' : ''} {age !== null ? `• ${age} ans` : ''}
                                                    {enf.estHandicape ? ' • Handicap' : enf.estEtudiant ? ' • Étudiant' : ''}
                                                </span>
                                            </div>
                                            <div className="flex items-center gap-1.5 shrink-0">
                                                {enf.aCharge && (
                                                    <Badge variant="success" size="xs">À charge</Badge>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <p className="text-xs text-[var(--color-fg-muted)] p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                Aucun enfant déclaré.
                            </p>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
