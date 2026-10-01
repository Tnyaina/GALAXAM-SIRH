package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.application.PointageService;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** Reconstitution depuis les deux classeurs, uniquement dans une transaction annulée sur copie. */
@EnabledIfSystemProperty(named="payroll.copied-data-test",matches="true")
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:postgresql://localhost:5432/sirh_galaxam_recette_variables_20261001",
 "app.rattrapage.enabled=false","app.security.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
class PayrollAgentXCopiedDataTest {
 @MockitoBean(name="org.springframework.context.annotation.internalScheduledAnnotationProcessor")
 ScheduledAnnotationBeanPostProcessor scheduling;
 @Autowired DataSource dataSource;
 @Autowired PayrollService payroll;
 @Autowired PointageService pointage;
 @Autowired PointageSyntheseService synthese;

 @Test void reproduireAgentXDeLaSaisieAuRecalcul() throws Exception {
  try(Connection c=dataSource.getConnection()) {
   try(var ps=c.prepareStatement("SELECT current_database()");var rs=ps.executeQuery()) {
    assertTrue(rs.next());assertEquals("sirh_galaxam_recette_variables_20261001",rs.getString(1));
   }
   c.setAutoCommit(false);
   try {
    int employee=insert(c,"INSERT INTO employe (matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) VALUES ('200','AGENT','X TMM','M','1990-01-15','Recette','RECETTE-X-TMM','2010-01-15','TMM','Recette','0340000000') RETURNING id_employe");
    execute(c,"UPDATE employe SET est_actif=false WHERE id_employe<>?",employee);
    int contract=CongeTestFixtureHelper.createContratActif(c,employee,LocalDate.of(2025,2,26));
    int assignment=CongeTestFixtureHelper.createAffectation(c,employee,contract,LocalDate.of(2025,2,26),new BigDecimal("370000"));
    execute(c,"UPDATE employe_affectation_carriere SET id_site=2,id_fonction=16,id_categorie_professionnelle=2 WHERE id_affectation=?",assignment);
    execute(c,"UPDATE periode_paie SET id_statut_periode=(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code='EN_COURS') WHERE id_periode_paie=1");
    execute(c,"INSERT INTO conge_taux_applicable (id_employe,taux_journalier,date_effet,source_provenance,commentaire) VALUES (?,9663.09,'2026-07-26','REPRISE_CUTOVER','Recap salaire H5')",employee);
    int leave=insert(c,"INSERT INTO demande_conge (id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,nb_jours_decomptes,commentaire) VALUES (?,5,3,'2026-08-17','2026-08-25',9,'Recette Agent X : classeur') RETURNING id_demande_conge",employee);
    // État d'ouverture de recette : deux lots ayant les valeurs de la prise Excel, pas un historique inventé.
    execute(c,"INSERT INTO mouvement_solde_conge (id_employe,type_mouvement,nb_jours,date_mouvement,valeur_source,commentaire) VALUES (?,'AJUSTEMENT',2.91,'2026-07-24',26285.26,'Ouverture recette : lot Excel V19/W19'),(?,'AJUSTEMENT',6.09,'2026-07-25',60682.53,'Ouverture recette : lot Excel V20/W20')",employee,employee);
    execute(c,"INSERT INTO mouvement_solde_conge (id_employe,type_mouvement,nb_jours,date_mouvement,id_demande_conge,commentaire) VALUES (?,'PRISE',-9,'2026-08-17',?,'Prise Excel 17 au 25 août')",employee,leave);
    var days=new ArrayList<SaisiePointageJourDTO>();
    try(var r=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/paie/agent-x-tmm-aout-2026.csv")))) {
     r.readLine();for(String line;(line=r.readLine())!=null;) {
      String[] v=line.split(",",-1);var d=new SaisiePointageJourDTO();
      d.setIdEmploye(employee);d.setIdSite(2);d.setIdPeriodePaie(1);d.setDatePointage(v[0]);d.setCodeStatut(v[5]);
      if(!v[1].isEmpty()){d.setHeureEntreeMatin(v[1]);d.setHeureSortieMatin(v[2]);d.setHeureEntreeAm(v[3]);d.setHeureSortieAm(v[4]);}
      if("CONGE".equals(v[5]))d.setTypeCongeJour("FULL_DAY");
      boolean worked="PRESENT".equals(v[5]);d.setDynamicIndemnites(Map.of(
        "1",Map.of("matin",worked,"am",worked),"2",Map.of("accordee",Boolean.parseBoolean(v[6]))));days.add(d);
     }
    }
    var written=pointage.enregistrerPointagesBatch(days,c);assertEquals(true,written.get("success"));
    execute(c,"UPDATE pointage_jour SET id_demande_conge=? WHERE id_employe=? AND date_pointage BETWEEN '2026-08-17' AND '2026-08-25'",leave,employee);
    execute(c,"INSERT INTO pointage_ajustement_periode (id_employe,id_periode_paie,minutes_signees,motif) VALUES (?,1,-150,'Excel T45/Y51 : 10 minutes fois 15 déplacements')",employee);
    try(var ps=c.prepareStatement("SELECT i.montant_calcule FROM pointage_indemnite_jour i JOIN pointage_jour p USING(id_pointage) WHERE p.id_employe=? AND p.date_pointage='2026-08-10' AND i.id_indemnite=1")){
     ps.setInt(1,employee);try(var rs=ps.executeQuery()){assertTrue(rs.next());money("1418.18",rs.getBigDecimal(1));}
    }
    synthese.gelerPeriode(1,null,c);
    execute(c,"UPDATE periode_paie SET id_statut_periode=(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code='VALIDEE') WHERE id_periode_paie=1");
    execute(c,"INSERT INTO paie_avance_acompte (id_employe,id_periode_paie,type_mouvement,id_type_mouvement,sens,montant,montant_initial,solde_restant,motif,statut) SELECT ?,1,code,id_type_mouvement,sens,6080,6080,6080,'Classeur recap salaire X5','APPROUVE' FROM ref_type_mouvement_paie WHERE code='CALL_PERSO'",employee);
    try(var ps=c.prepareStatement("SELECT minutes_hs30_finales,minutes_absence_finale,total_heures_travaillees,nb_jours_deplacement FROM pointage_cumul_periode WHERE id_employe=? AND id_periode_paie=1 ORDER BY version_snapshot DESC LIMIT 1")) {
     ps.setInt(1,employee);try(var rs=ps.executeQuery()){assertTrue(rs.next());assertEquals(387,rs.getInt(1));assertEquals(0,rs.getInt(2));money("128.95",rs.getBigDecimal(3));money("15",rs.getBigDecimal(4));}
    }
    var simulated=payroll.calculerBulletinEmploye(employee,1,true,null,c);
    print(simulated);
    money("370000",simulated.getBulletin().getSalaireBase());
    money("111000",line(simulated,"DEDUCTION_CONGE_BASE"));
    money("86967.79",line(simulated,"INDEMNITE_CONGE_PAYE"));
    money("17899.09",line(simulated,"HEURES_SUP_30"));
    money("35272.70",line(simulated,"INDEMNITE_TRANSPORT"));
    money("399139.58",simulated.getBulletin().getTotalGainsBruts());
    money("9978.49",simulated.getBulletin().getTotalCotisationsSalariales());
    money("380081.09",simulated.getBulletin().getNetAvantArrondi());
    money("389100",simulated.getBulletin().getBaseIrsaArrondie());
    assertTrue(simulated.getLignes().stream().noneMatch(l -> "HS_EXONEREE_IRSA".equals(l.getCodeRubrique())));
    money("3000",simulated.getBulletin().getIrsaNet());
    money("380080",simulated.getBulletin().getNetAPayer());
    var actual=payroll.calculerBulletinEmploye(employee,1,false,null,c);
    var again=payroll.calculerBulletinEmploye(employee,1,false,null,c);
    assertEquals(actual.getBulletin().getIdBulletin(),again.getBulletin().getIdBulletin());
    assertEquals(simulated.getBulletin().getNetAPayer(),actual.getBulletin().getNetAPayer());
    assertEquals(actual.getBulletin().getNetAPayer(),again.getBulletin().getNetAPayer());
   } finally {c.rollback();}
  }
 }
 private static int insert(Connection c,String sql,Object...args)throws Exception{
  try(var p=c.prepareStatement(sql)){bind(p,args);try(var r=p.executeQuery()){assertTrue(r.next());return r.getInt(1);}}
 }
 private static void execute(Connection c,String sql,Object...args)throws Exception{try(var p=c.prepareStatement(sql)){bind(p,args);p.executeUpdate();}}
 private static void bind(java.sql.PreparedStatement p,Object[] args)throws Exception{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
 private static BigDecimal line(BulletinPaieComplet b,String code){return b.getLignes().stream().filter(l->code.equals(l.getCodeRubrique())).findFirst().orElseThrow().getMontantSalarial();}
 private static void money(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual),()->expected+" <> "+actual);}
 private static void print(BulletinPaieComplet b){System.out.println("AGENT_X_RESULT "+b.getBulletin().getTotalGainsBruts()+" cotis="+b.getBulletin().getTotalCotisationsSalariales()+" IRSA="+b.getBulletin().getIrsaNet()+" avant="+b.getBulletin().getNetAvantArrondi()+" net="+b.getBulletin().getNetAPayer());for(var l:b.getLignes())System.out.println("AGENT_X_LINE "+l.getCodeRubrique()+" base="+l.getBaseCalcul()+" taux="+l.getTauxSalarial()+" montant="+l.getMontantSalarial());}
}
