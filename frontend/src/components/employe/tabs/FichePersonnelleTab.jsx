import { User, Calendar, MapPin, Heart, Phone, Landmark } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

const InfoBlock = ({ label, value, mono = false, icon }) => (
    <div className="py-2">
        <p className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-fg-muted)] mb-0.5">{label}</p>
        <div className="flex items-center gap-1.5">
            {icon && <span className="text-[var(--color-fg-subtle)]">{icon}</span>}
            <p className={`text-sm font-semibold text-[var(--color-fg)] ${mono ? 'font-mono' : ''}`}>{value || '-'}</p>
        </div>
    </div>
);

const fmtDate = (d) => {
    if (!d) return '-';
    try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
};

export default function FichePersonnelleTab({ emp, comptes = [] }) {
    if (!emp) return null;

    return (
        <div className="space-y-8">
            {/* Section État Civil & Identité */}
            <div>
                <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] pb-2 border-b border-[var(--color-border-subtle)] mb-4 flex items-center gap-2">
                    <User size={14} /> État Civil & Identité
                </h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6">
                    <InfoBlock label="Sexe" value={emp.sexe === 'M' ? 'Masculin' : 'Féminin'} />
                    <InfoBlock label="Date de Naissance" value={fmtDate(emp.dateNaissance)} icon={<Calendar size={14} />} />
                    <InfoBlock label="Lieu de Naissance" value={emp.lieuNaissance} icon={<MapPin size={14} />} />
                    <InfoBlock label="Situation Familiale" value={emp.situationFamiliale} icon={<Heart size={14} />} />
                    <InfoBlock label="Nom du Père" value={emp.pereNomPrenom} />
                    <InfoBlock label="Nom de la Mère" value={emp.mereNomPrenom} />
                    <InfoBlock label="N° CIN" value={emp.cinNumero} mono />
                    <InfoBlock label="Date Délivrance CIN" value={fmtDate(emp.cinDateDelivrance)} />
                    <InfoBlock label="Lieu Délivrance CIN" value={emp.cinLieuDelivrance} />
                    <InfoBlock label="N° Permis de Conduire" value={emp.permisConduireNumero} mono />
                    {emp.cinDuplicataDate && <InfoBlock label="Duplicata CIN Date" value={fmtDate(emp.cinDuplicataDate)} />}
                    {emp.cinDuplicataLieu && <InfoBlock label="Duplicata CIN Lieu" value={emp.cinDuplicataLieu} />}
                </div>
            </div>

            {/* Section Coordonnées */}
            <div>
                <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] pb-2 border-b border-[var(--color-border-subtle)] mb-4 flex items-center gap-2">
                    <Phone size={14} /> Domicile & Coordonnées
                </h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6">
                    <div className="sm:col-span-2">
                        <InfoBlock label="Adresse Domicile" value={emp.adresse} icon={<MapPin size={14} />} />
                    </div>
                    <InfoBlock label="Ville" value={emp.ville} />
                    <InfoBlock label="Téléphone" value={emp.telephone} mono icon={<Phone size={14} />} />
                    <div className="sm:col-span-2">
                        <InfoBlock label="Adresse Email" value={emp.email} />
                    </div>
                </div>
            </div>

            {/* Section CNaPS & Banque */}
            <div>
                <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] pb-2 border-b border-[var(--color-border-subtle)] mb-4 flex items-center gap-2">
                    <Landmark size={14} /> Sécurité Sociale & Compte Bancaire
                </h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6 mb-4">
                    <InfoBlock label="N° Affiliation CNaPS" value={emp.numAffiliationCnaps} mono />
                    <InfoBlock label="Date Déclaration CNaPS" value={fmtDate(emp.dateDeclaration)} />
                </div>

                {comptes.length > 0 ? (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Banque</Th>
                                <Th>Agence</Th>
                                <Th>N° Compte / RIB</Th>
                                <Th>Titulaire</Th>
                                <Th>Statut</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {comptes.map((cb, i) => (
                                <Tr key={i}>
                                    <Td className="font-semibold">{cb.nomBanque || cb.libelleBanque || cb.codeBanque || (cb.idBanque ? `Banque N°${cb.idBanque}` : '-')}</Td>
                                    <Td>{cb.agence || '-'}</Td>
                                    <Td isData className="font-mono text-xs text-[var(--color-brand-500)] font-bold">{cb.numCompteRib}</Td>
                                    <Td>{cb.titulaireCompte || '-'}</Td>
                                    <Td>
                                        {cb.estPrincipal ? (
                                            <Badge variant="brand" size="sm">Principal</Badge>
                                        ) : (
                                            <Badge variant="default" size="sm">Secondaire</Badge>
                                        )}
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                ) : (
                    <p className="text-xs text-[var(--color-fg-muted)]">Aucun compte bancaire enregistré.</p>
                )}
            </div>
        </div>
    );
}
