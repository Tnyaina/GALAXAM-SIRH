package mg.galaxam.sirh.pointage.application;
import mg.galaxam.sirh.pointage.api.dto.*;
import mg.galaxam.sirh.common.exception.*;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PointageDocumentServiceTest {
 @Test void exportUtiliseToujoursLaPeriodeDePaieMemeDepuisUneVueMois() throws Exception {
  var ds=mock(DataSource.class);var employee=mock(PointageEmployeService.class);
  var f=new FichePointageEmployeDTO();f.setIdPeriode(9);f.setIdEmploye(7);f.setStatutPeriode("OUVERTE");
  f.setDateDebut("2026-09-25");f.setDateFin("2026-10-24");
  when(employee.getFichePointageEmploye(7,"2026-09-28","periode",9)).thenReturn(f);
  var c=mock(Connection.class);var ps=mock(PreparedStatement.class);
  when(ds.getConnection()).thenReturn(c);when(c.prepareStatement(anyString())).thenReturn(ps);
  when(ps.executeQuery()).thenReturn(mock(ResultSet.class));
  var data=new PointageDocumentService(ds,employee).preparer(7,"2026-09-28","mois",9);
  assertEquals("2026-09-25",data.fiche().getDateDebut());assertEquals("2026-10-24",data.fiche().getDateFin());
  verify(employee).getFichePointageEmploye(7,"2026-09-28","periode",9);
 }
 @Test void refuseUnIdentifiantDePeriodeInexistantSansRepliMensuel() throws Exception {
  var ds=mock(DataSource.class);var c=mock(Connection.class);var ps=mock(PreparedStatement.class);
  when(ds.getConnection()).thenReturn(c);when(c.prepareStatement(anyString())).thenReturn(ps);when(ps.executeQuery()).thenReturn(mock(ResultSet.class));
  var service=new PointageEmployeService(ds,mock(PointageSyntheseService.class),null);
  var ex=assertThrows(SirhBusinessException.class,()->service.getFichePointageEmploye(7,"2026-09-28","periode",999));
  assertEquals(SirhErrorCode.POINTAGE_PERIOD_CONTEXT_REQUIRED,ex.getErrorCode());
 }
 @Test void refuseUnePeriodeValideeSansSnapshot() throws Exception {
  var ds=mock(DataSource.class);var employee=mock(PointageEmployeService.class);
  var f=new FichePointageEmployeDTO();f.setIdPeriode(9);f.setStatutPeriode("VALIDEE");
  when(employee.getFichePointageEmploye(7,null,"periode",9)).thenReturn(f);
  var c=mock(Connection.class);var ps=mock(PreparedStatement.class);var rs=mock(ResultSet.class);
  when(ds.getConnection()).thenReturn(c);when(c.prepareStatement(anyString())).thenReturn(ps);when(ps.executeQuery()).thenReturn(rs);
  var ex=assertThrows(SirhBusinessException.class,()->new PointageDocumentService(ds,employee).preparer(7,null,"periode",9));
  assertEquals(SirhErrorCode.POINTAGE_EXPORT_SNAPSHOT_MISSING,ex.getErrorCode());
 }
 @Test void refuseUnJournalModifieApresLeGel() throws Exception {
  var ds=mock(DataSource.class);var employee=mock(PointageEmployeService.class);
  var f=new FichePointageEmployeDTO();f.setIdPeriode(9);f.setStatutPeriode("VALIDEE");
  var day=new PointageJourDTO();day.setIdPointage(12);day.setVersion(2);f.setPointagesData(List.of(day));
  when(employee.getFichePointageEmploye(7,null,"periode",9)).thenReturn(f);
  var c=mock(Connection.class);var ps=mock(PreparedStatement.class);var rs=mock(ResultSet.class);
  var jp=mock(PreparedStatement.class);var jr=mock(ResultSet.class);
  when(ds.getConnection()).thenReturn(c);when(c.prepareStatement(contains("pointage_cumul_periode"))).thenReturn(ps);
  when(ps.executeQuery()).thenReturn(rs);when(rs.next()).thenReturn(true);when(rs.getInt("id_cumul")).thenReturn(3);
  when(c.prepareStatement(contains("pointage_snapshot_jour"))).thenReturn(jp);when(jp.executeQuery()).thenReturn(jr);
  when(jr.next()).thenReturn(true,false);when(jr.getInt(1)).thenReturn(12);when(jr.getInt(2)).thenReturn(1);
  var ex=assertThrows(SirhBusinessException.class,()->new PointageDocumentService(ds,employee).preparer(7,null,"periode",9));
  assertEquals(SirhErrorCode.POINTAGE_EXPORT_SNAPSHOT_MISMATCH,ex.getErrorCode());
 }
 @Test void prendLeBilanEtLesMontantsDuSnapshotPasDuCalculCourant() throws Exception {
  var ds=mock(DataSource.class);var employee=mock(PointageEmployeService.class);
  var f=new FichePointageEmployeDTO();f.setIdPeriode(9);f.setIdEmploye(7);f.setStatutPeriode("VALIDEE");
  f.setTotauxIndemnites(Map.of("REPAS",new java.math.BigDecimal("999")));
  when(employee.getFichePointageEmploye(7,null,"periode",9)).thenReturn(f);
  var c=mock(Connection.class);when(ds.getConnection()).thenReturn(c);
  var ps=mock(PreparedStatement.class);var rs=mock(ResultSet.class);
  when(c.prepareStatement(contains("pointage_cumul_periode"))).thenReturn(ps);when(ps.executeQuery()).thenReturn(rs);
  when(rs.next()).thenReturn(true);when(rs.getInt("id_cumul")).thenReturn(3);when(rs.getInt("version_snapshot")).thenReturn(2);
  when(rs.getObject(anyString())).thenReturn(120);when(rs.getBigDecimal(anyString())).thenReturn(java.math.BigDecimal.ONE);
  var jp=mock(PreparedStatement.class);var jr=mock(ResultSet.class);
  when(c.prepareStatement(contains("pointage_snapshot_jour"))).thenReturn(jp);when(jp.executeQuery()).thenReturn(jr);
  var ap=mock(PreparedStatement.class);var ar=mock(ResultSet.class);
  when(c.prepareStatement(contains("pointage_snapshot_indemnite"))).thenReturn(ap);when(ap.executeQuery()).thenReturn(ar);
  when(ar.next()).thenReturn(true,false);when(ar.getString("code")).thenReturn("REPAS");when(ar.getString("libelle")).thenReturn("Repas");
  when(ar.getBigDecimal("montant")).thenReturn(new java.math.BigDecimal("123"));
  var adj=mock(PreparedStatement.class);when(c.prepareStatement(contains("pointage_snapshot_ajustement"))).thenReturn(adj);
  when(adj.executeQuery()).thenReturn(mock(ResultSet.class));
  var data=new PointageDocumentService(ds,employee).preparer(7,null,"periode",9);
  assertEquals(2,data.versionSnapshot());assertEquals(0,new java.math.BigDecimal("2").compareTo(data.bilan().get(4)));
  assertEquals(new java.math.BigDecimal("123"),data.indemnites().get(0).montant());
  assertNull(data.indemnites().get(0).quantite());
 }

}
