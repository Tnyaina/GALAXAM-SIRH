import { useState } from 'react';
import { Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import SEO from '@/components/system/SEO';
import DossierSortieModal from '@/features/payroll/components/modals/stc/DossierSortieModal';
import StcRecuModal from '@/features/payroll/components/modals/stc/StcRecuModal';

export default function DossierSortieDetailPage() {
    const { id } = useParams();
    const [params] = useSearchParams();
    const navigate = useNavigate();
    const [openedStc, setOpenedStc] = useState(null);
    const nouveau = id === 'nouveau';
    const employeId = Number(params.get('employeId'));
    const dossierId = Number(id);

    if ((nouveau && (!Number.isInteger(employeId) || employeId <= 0))
        || (!nouveau && (!Number.isInteger(dossierId) || dossierId <= 0))) {
        return <Navigate to="/paie/dossiers-sortie" replace />;
    }

    return <>
        <SEO title="Dossier de sortie — GALAXAM SIRH" />
        <DossierSortieModal
            pageMode
            isOpen
            idDossier={nouveau ? null : dossierId}
            idEmploye={nouveau ? employeId : null}
            dateDepartInitiale={params.get('dateDepart') || null}
            motifInitial={params.get('motif') || 'DEMISSION'}
            onClose={() => navigate('/paie/dossiers-sortie')}
            onSaved={data => {
                if (nouveau && data?.idDossierSortie) {
                    navigate(`/paie/dossiers-sortie/${data.idDossierSortie}`, { replace: true });
                }
            }}
            onOpenStcModal={data => setOpenedStc(data.idStc)}
        />
        {openedStc && <StcRecuModal isOpen idStc={openedStc} onClose={() => setOpenedStc(null)} />}
    </>;
}
