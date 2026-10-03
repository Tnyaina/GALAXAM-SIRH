package mg.galaxam.sirh.paie.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class PaieRubriqueRouteTest {
    @Test
    void routesDoNotRepeatTheApiContextPath() {
        var routes = PaieRubriqueController.class.getAnnotation(RequestMapping.class).value();
        assertTrue(Arrays.asList(routes).contains("/paie/rubriques"));
        assertTrue(Arrays.asList(routes).contains("/v1/paie/rubriques"));
    }
}
