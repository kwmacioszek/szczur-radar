package pl.szczurradar.api.agents.assessor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.claim;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.investment;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.target;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.validInput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.Direction;

/**
 * Every combination of {@code direction × assertiveness × claim_ids} (none, one author claim, one
 * reported claim) against table §7.2 of contract-v2.md, written out here independently of
 * {@link StanceTable}: allowed combinations validate, every forbidden one is a domain invariant
 * violation of D12, D13 or D14.
 */
class StanceTableTest {

	enum Claims {
		NONE, AUTHOR, REPORTED
	}

	/** The permitted rows of table §7.2: direction, assertiveness (null = JSON null), claims. */
	private static final Set<List<Object>> ALLOWED = allowed();

	private final RuleBasedValidator validator = RuleBasedValidator.contractV2();

	static Stream<Arguments> combinations() {
		List<Arguments> combinations = new ArrayList<>();
		List<Assertiveness> assertiveness = new ArrayList<>(Arrays.asList(Assertiveness.values()));
		assertiveness.add(null);
		for (Direction direction : Direction.values()) {
			for (Assertiveness value : assertiveness) {
				for (Claims claims : Claims.values()) {
					combinations.add(Arguments.of(direction, value, claims));
				}
			}
		}
		return combinations.stream();
	}

	@ParameterizedTest(name = "{0} / {1} / {2}")
	@MethodSource("combinations")
	void tableSection72(Direction direction, Assertiveness assertiveness, Claims claims) {
		AssessmentOutputV2 output = switch (claims) {
			case NONE -> investment(List.of(target("t1", "XTB", direction, assertiveness)), List.of());
			case AUTHOR, REPORTED -> investment(List.of(target("t1", "XTB", direction, assertiveness, "c1")),
					List.of(claim("c1", claims == Claims.AUTHOR ? Attribution.AUTHOR : Attribution.REPORTED, "XTB", "t1")));
		};
		ValidationResult result = validator.validate(validInput(), output);
		if (ALLOWED.contains(Arrays.asList(direction, assertiveness, claims))) {
			assertThat(result.violations()).isEmpty();
		}
		else {
			assertThat(result.violations()).isNotEmpty()
				.allSatisfy(violation -> assertThat(violation.rule()).isIn("D12", "D13", "D14"));
			assertThat(result.failureClass()).contains(FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}
	}

	private static Set<List<Object>> allowed() {
		List<List<Object>> rows = new ArrayList<>();
		for (Direction directional : List.of(Direction.BULLISH, Direction.BEARISH, Direction.NEUTRAL)) {
			rows.add(Arrays.asList(directional, Assertiveness.HEDGED, Claims.AUTHOR));
			rows.add(Arrays.asList(directional, Assertiveness.UNHEDGED, Claims.AUTHOR));
		}
		rows.add(Arrays.asList(Direction.MIXED, null, Claims.AUTHOR));
		rows.add(Arrays.asList(Direction.UNCLEAR, null, Claims.NONE));
		rows.add(Arrays.asList(Direction.UNCLEAR, null, Claims.AUTHOR));
		rows.add(Arrays.asList(Direction.UNCLEAR, null, Claims.REPORTED));
		rows.add(Arrays.asList(Direction.NOT_EXPRESSED, null, Claims.NONE));
		return Set.copyOf(rows);
	}

}
