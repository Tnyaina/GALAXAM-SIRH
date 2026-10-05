package mg.galaxam.sirh.contrat.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

public class EmployePhotoNamingTest {

    @Test
    public void testSanitizeNamePart() {
        assertEquals("DUPONT", EmployeController.sanitizeNamePart("DUPONT"));
        assertEquals("Dupont", EmployeController.sanitizeNamePart(" Dupont "));
        assertEquals("Elodie", EmployeController.sanitizeNamePart("Élodie"));
        assertEquals("Jean-Pierre", EmployeController.sanitizeNamePart("Jean-Pierre"));
        assertEquals("Andriamalala_Tojo", EmployeController.sanitizeNamePart("Andriamalala Tojo"));
        assertEquals("EMP-2026-0042", EmployeController.sanitizeNamePart("EMP-2026-0042"));
        assertEquals("", EmployeController.sanitizeNamePart(null));
        assertEquals("", EmployeController.sanitizeNamePart("   "));
        assertEquals("Test_Special", EmployeController.sanitizeNamePart("Test !@# Special"));
    }

    @Test
    public void testGeneratePhotoFilenameWithFullDetails() {
        String filename = EmployeController.generatePhotoFilename(
                "EMP001", "Dupont", "Jean-Pierre", ".jpg");

        assertTrue(filename.startsWith("PHOTO_EMP001_DUPONT_Jean-Pierre_"),
                "Filename should contain matricule, uppercase nom, and clean prenom. Got: " + filename);
        assertTrue(filename.endsWith(".jpg"), "Filename should have .jpg extension. Got: " + filename);

        // Pattern: PHOTO_EMP001_DUPONT_Jean-Pierre_yyyyMMdd_HHmmss_xxxxxx.jpg
        String[] parts = filename.split("_");
        assertTrue(parts.length >= 6, "Filename should have at least 6 parts separated by underscore.");
    }

    @Test
    public void testGeneratePhotoFilenameWithMatriculeOnly() {
        String filename = EmployeController.generatePhotoFilename(
                "EMP-2026-0099", "", null, ".png");

        assertTrue(filename.startsWith("PHOTO_EMP-2026-0099_"),
                "Filename should start with PHOTO_EMP-2026-0099_. Got: " + filename);
        assertTrue(filename.endsWith(".png"), "Filename should have .png extension. Got: " + filename);
    }

    @Test
    public void testGeneratePhotoFilenameWithNomOnly() {
        String filename = EmployeController.generatePhotoFilename(
                null, "Rakoto", "", ".webp");

        assertTrue(filename.startsWith("PHOTO_RAKOTO_"),
                "Filename should start with PHOTO_RAKOTO_. Got: " + filename);
        assertTrue(filename.endsWith(".webp"), "Filename should have .webp extension. Got: " + filename);
    }

    @Test
    public void testGeneratePhotoFilenameFallbackCollaborateur() {
        String filename = EmployeController.generatePhotoFilename(
                null, null, "", ".jpg");

        assertTrue(filename.startsWith("PHOTO_COLLABORATEUR_"),
                "Filename should fallback to PHOTO_COLLABORATEUR_. Got: " + filename);
        assertTrue(filename.endsWith(".jpg"), "Filename should end with .jpg. Got: " + filename);
    }

    @Test
    public void testGeneratePhotoFilenameUniqueness() {
        Set<String> filenames = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String name = EmployeController.generatePhotoFilename("EMP001", "Martin", "Paul", ".jpg");
            assertFalse(filenames.contains(name), "Generated duplicate filename: " + name);
            filenames.add(name);
        }
        assertEquals(50, filenames.size());
    }
}
