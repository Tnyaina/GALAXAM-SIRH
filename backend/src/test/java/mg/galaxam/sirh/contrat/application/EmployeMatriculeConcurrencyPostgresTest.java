package mg.galaxam.sirh.contrat.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.domain.EmployeValidationEngine;
import mg.galaxam.sirh.contrat.domain.MatriculeGeneratorEngine;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class EmployeMatriculeConcurrencyPostgresTest {
    @Test
    void creationsAutomatiquesEtManuellesRestentUniquesEtContigues() throws Exception {
        String url = System.getenv("GALAXAM_MATRICULE_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL temporaire dédiée requise");
        String user = System.getenv("GALAXAM_MATRICULE_TEST_DB_USER");
        String password = System.getenv("GALAXAM_MATRICULE_TEST_DB_PASSWORD");
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(url);
        source.setUser(user);
        source.setPassword(password);
        EmployeService service = new EmployeService(source, new EmployeValidationEngine(), new MatriculeGeneratorEngine());

        String suggestion = service.generateNextMatricule();
        assertEquals(suggestion, service.generateNextMatricule(), "L'aperçu ne réserve pas de numéro.");
        String cinAuto1 = cinUnique();
        String cinAuto2 = cinUnique();
        String cinManuel = cinUnique();
        String cinCollision = cinUnique();
        String cinAutoApres = cinUnique();
        List<String> cins = List.of(cinAuto1, cinAuto2, cinManuel, cinCollision, cinAutoApres);
        var pool = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch depart = new CountDownLatch(1);
            Future<String> auto1 = pool.submit(() -> creerEmploye(service, depart, cinAuto1, "AUTO"));
            Future<String> auto2 = pool.submit(() -> creerEmploye(service, depart, cinAuto2, "AUTO"));
            Future<String> manuel = pool.submit(() -> creerEmploye(service, depart, cinManuel, "MANUEL-TEST"));
            depart.countDown();

            String matriculeAuto1 = auto1.get();
            String matriculeAuto2 = auto2.get();
            assertNotEquals(matriculeAuto1, matriculeAuto2);
            assertTrue(matriculeAuto1.matches("\\d{4,}"));
            assertTrue(matriculeAuto2.matches("\\d{4,}"));
            assertEquals(List.of(nombre(suggestion), nombre(suggestion) + 1),
                    List.of(nombre(matriculeAuto1), nombre(matriculeAuto2)).stream().sorted().toList(),
                    "Les créations automatiques restent contiguës malgré une création manuelle simultanée.");
            assertEquals("MANUEL-TEST", manuel.get());
            assertEqualsPersisted(url, user, password, cinAuto1, matriculeAuto1);
            assertEqualsPersisted(url, user, password, cinAuto2, matriculeAuto2);
            assertEqualsPersisted(url, user, password, cinManuel, "MANUEL-TEST");

            String suggestionConflit = service.generateNextMatricule();
            assertEquals(suggestionConflit,
                    creerEmploye(service, new CountDownLatch(0), cinCollision, suggestionConflit));
            String suggestionSuivante = service.generateNextMatricule();
            assertEquals(nombre(suggestionConflit) + 1, nombre(suggestionSuivante));
            assertEquals(suggestionSuivante,
                    creerEmploye(service, new CountDownLatch(0), cinAutoApres, "AUTO"));
            for (String cin : cins) assertTrue(employePersisted(url, user, password, cin));
        } finally {
            pool.shutdownNow();
            try (Connection c = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = c.prepareStatement("DELETE FROM employe WHERE cin_numero IN (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < cins.size(); i++) ps.setString(i + 1, cins.get(i));
                ps.executeUpdate();
            }
        }
    }

    private static String creerEmploye(EmployeService service, CountDownLatch depart, String cin, String matricule)
            throws Exception {
        depart.await();
        EmployeRequest request = new EmployeRequest();
        request.setMatricule(matricule);
        request.setNom("Employe");
        request.setPrenom("Test" + cin.substring(cin.length() - 4));
        request.setSexe("M");
        request.setDateNaissance(Date.valueOf("1990-01-01"));
        request.setLieuNaissance("Site de test");
        request.setCinNumero(cin);
        request.setCinDateDelivrance(Date.valueOf("2010-01-01"));
        request.setCinLieuDelivrance("Site de test");
        request.setAdresse("Adresse synthétique");
        request.setTelephone("0000000000");
        return service.creerEmploye(request).getMatricule();
    }

    private static void assertEqualsPersisted(String url, String user, String password, String cin, String expected)
            throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = c.prepareStatement("SELECT matricule FROM employe WHERE cin_numero = ?")) {
            ps.setString(1, cin);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expected, rs.getString(1));
            }
        }
    }

    private static boolean employePersisted(String url, String user, String password, String cin) throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM employe WHERE cin_numero = ?")) {
            ps.setString(1, cin);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String cinUnique() {
        return String.format("%012d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000_000L));
    }

    private static long nombre(String matricule) {
        return Long.parseLong(matricule);
    }
}
