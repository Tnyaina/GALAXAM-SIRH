import { memo } from 'react';
import StatCard from '@/components/ui/data-display/StatCard';

function SupervisionKpiCards({
    stats = {},
    selectedStatut,
    selectedEcheance,
    doublonsOnly,
    onFilterClick
}) {
    return (
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3.5">
            {/* 1. Total Contrats */}
            <StatCard
                title="Contrats au total"
                value={stats.totalContrats ?? 0}
                subtext="Tous statuts confondus"
                color="neutral"
                active={selectedStatut === 'ALL' && !doublonsOnly && !selectedEcheance}
                onClick={() => onFilterClick({ statut: 'ALL', doublons: false, echeance: '' })}
            />

            {/* 2. Contrats Actifs */}
            <StatCard
                title="Contrats actifs"
                value={stats.contratsActifs ?? 0}
                subtext="En cours d'exécution"
                color="success"
                active={selectedStatut === 'ACTIF' && !doublonsOnly && !selectedEcheance}
                onClick={() => onFilterClick({ statut: 'ACTIF', doublons: false, echeance: '' })}
            />

            {/* 3. Planifiés / À signer */}
            <StatCard
                title="Planifiés / À signer"
                value={stats.enAttenteSignature ?? 0}
                subtext="Effet futur ou brouillon"
                color="brand"
                active={selectedStatut === 'PENDING' && !doublonsOnly && !selectedEcheance}
                onClick={() => onFilterClick({ statut: 'PENDING', doublons: false, echeance: '' })}
            />

            {/* 4. Échéances < 60 jours */}
            <StatCard
                title="Échéances < 60 jours"
                value={stats.echeances60Jours ?? 0}
                subtext="Essai ou fin de contrat"
                color="warning"
                active={selectedEcheance === '60J' && !doublonsOnly}
                onClick={() => onFilterClick({ statut: 'ALL', doublons: false, echeance: '60J' })}
            />

            {/* 5. Doublons potentiels */}
            <StatCard
                title="Doublons potentiels"
                value={stats.doublonsPotentiels ?? 0}
                subtext="Dossiers à vérifier"
                color="danger"
                active={doublonsOnly}
                onClick={() => onFilterClick({ statut: 'ALL', doublons: true, echeance: '' })}
            />
        </div>
    );
}

export default memo(SupervisionKpiCards);
