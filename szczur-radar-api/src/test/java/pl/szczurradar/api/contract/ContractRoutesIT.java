package pl.szczurradar.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.web.MappedRoutes;

/**
 * Every route under {@code /api/} the application maps is in {@link ContractRegistry}: in
 * {@link ContractRegistry#SHAPES} when the SPA reads its body, else in {@link ContractRegistry#NOT_READ_BY_SPA}
 * with the reason. Both ways: a registry entry without its route fails too. {@code /internal/**} (the cron's)
 * and {@code /error} are outside the SPA's contract. The context is {@code AuthorizationCompletenessIT}'s, so
 * the context cache shares it.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContractRoutesIT {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	void everyApiRouteIsInTheRegistry() {
		Set<String> mapped = MappedRoutes.routes(handlerMapping)
			.stream()
			.filter(route -> route.pattern().startsWith("/api/"))
			.map(MappedRoutes.Route::key)
			.collect(Collectors.toCollection(TreeSet::new));
		Set<String> both = new TreeSet<>(ContractRegistry.SHAPES.keySet());
		both.retainAll(ContractRegistry.NOT_READ_BY_SPA.keySet());
		Set<String> registered = new TreeSet<>(ContractRegistry.SHAPES.keySet());
		registered.addAll(ContractRegistry.NOT_READ_BY_SPA.keySet());

		assertThat(mapped).as("routes under /api/ found").contains("GET /api/auth/me", "GET /api/posts");
		assertThat(both).withFailMessage("%s", "Routes both in ContractRegistry.SHAPES and NOT_READ_BY_SPA: " + both)
			.isEmpty();
		Set<String> unregistered = difference(mapped, registered);
		assertThat(unregistered).withFailMessage("%s", "Routes under /api/ missing in ContractRegistry: " + unregistered
				+ " — a route the SPA reads gets its contract file in szczur-radar-web/src/contract/, its typed sample"
				+ " in samples.ts and an entry in SHAPES; any other one an entry in NOT_READ_BY_SPA with the reason")
			.isEmpty();
		Set<String> gone = difference(registered, mapped);
		assertThat(gone).withFailMessage("%s", "ContractRegistry names routes the application does not map: " + gone
				+ " — remove or correct them")
			.isEmpty();
	}

	private static Set<String> difference(Set<String> from, Set<String> without) {
		return from.stream().filter(value -> !without.contains(value)).collect(Collectors.toCollection(TreeSet::new));
	}

}
