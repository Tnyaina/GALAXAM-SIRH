package mg.galaxam.sirh;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SirhApplicationTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
		assertThat(dataSource).isNotNull();
		assertThat(mockMvc).isNotNull();

		String adminToken = userFixtures.getAdminToken();
		assertThat(adminToken).isNotBlank();

		String pointageToken = userFixtures.getPointageToken();
		assertThat(pointageToken).isNotBlank();
	}
}
