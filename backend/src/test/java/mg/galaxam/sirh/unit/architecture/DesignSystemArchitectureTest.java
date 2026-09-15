package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DesignSystemArchitectureTest {

    private static final Path FRONTEND = Path.of("../frontend/src");

    @Test
    void establishedCoreComponentsRemainTheSingleUiVocabulary() {
        List<String> components = List.of(
                "components/ui/forms/Button.jsx",
                "components/ui/data-display/Badge.jsx",
                "components/ui/feedback/Alert.jsx",
                "components/ui/data-display/Table.jsx",
                "components/ui/overlays/Modal.jsx",
                "components/ui/overlays/SlideOver.jsx",
                "components/ui/data-display/EmptyState.jsx",
                "components/ui/forms/Input.jsx",
                "components/ui/forms/Select.jsx",
                "components/ui/navigation/Tabs.jsx",
                "components/ui/navigation/Pagination.jsx");

        assertThat(components).allSatisfy(relative -> assertThat(Files.exists(FRONTEND.resolve(relative))).isTrue());
    }

    @Test
    void payrollStatusesUseSemanticComponentsAndTokens() throws Exception {
        String cockpit = Files.readString(FRONTEND.resolve("components/paie/CockpitPaieTab.jsx"));
        assertThat(cockpit).contains("<Badge", "<Alert", "<Pagination");
        assertThat(cockpit).doesNotContain(
                "bg-emerald-", "text-emerald-", "border-emerald-",
                "bg-amber-", "text-amber-", "border-amber-",
                "bg-rose-", "text-rose-", "border-rose-",
                "bg-red-", "text-red-", "border-red-");
    }

    @Test
    void nonExceptionalControlsKeepTheAngularGalaxamIdentity() throws Exception {
        try (Stream<Path> files = Files.walk(FRONTEND)) {
            String sources = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jsx") || path.toString().endsWith(".js"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).reduce("", String::concat);
            assertThat(sources).doesNotContain(
                    "rounded-sm", "rounded-md", "rounded-lg", "rounded-xl", "rounded-2xl", "rounded-3xl");
        }
    }
}
