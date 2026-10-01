package mg.galaxam.sirh.paie.application;

import java.sql.Connection;
import java.time.LocalDate;
import mg.galaxam.sirh.paie.persistence.PaieRubrique;
import mg.galaxam.sirh.paie.domain.engine.ManualRuleApplicability;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;

/** Résolution commune à la saisie et au calcul. */
public final class ManualRubriqueResolver {
    private ManualRubriqueResolver() {}
    public static PaieRubrique resolve(Connection c,int linked,LocalDate start,LocalDate end) throws Exception {
        var original=PaieRubrique.trouverParId(linked,c);
        if(original==null) throw missing();
        int root=original.getIdRubriqueOrigine()==null?linked:original.getIdRubriqueOrigine();
        PaieRubrique match=null;
        try(var ps=c.prepareStatement("SELECT id_rubrique,date_debut_effet,date_fin_effet FROM paie_rubrique WHERE COALESCE(id_rubrique_origine,id_rubrique)=?")) {
            ps.setInt(1,root); try(var rs=ps.executeQuery()) { while(rs.next()) {
                var from=rs.getDate(2);var to=rs.getDate(3);
                if(ManualRuleApplicability.covers(from==null?null:from.toLocalDate(),to==null?null:to.toLocalDate(),start,end)) {
                    if(match!=null) throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,"Plusieurs versions couvrent la période.");
                    match=PaieRubrique.trouverParId(rs.getInt(1),c);
                }
            }}
        }
        if(match==null) throw missing();
        return match;
    }
    private static SirhBusinessException missing() { return new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
            "Aucune version de rubrique ne couvre toute la période."); }
}
