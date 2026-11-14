package pl.szczurradar.api.mutation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The guard of the registry {@link AcceptedMutants} on PIT's real report: red for every surviving mutant without an
 * entry and for every entry without a surviving mutant. It runs only in the profile {@code mutation}, right after
 * PIT in the same command ({@code ./mvnw -B -Pmutation test}); {@link MutantRegistryTest} proves the comparison on
 * fixtures in the plain build.
 */
@Tag("mutation")
class SurvivingMutantsTest {

	/** Relative to the module directory, where Maven runs the tests; the profile writes it without a timestamp. */
	private static final Path REPORT = Path.of("target/pit-reports/mutations.xml");

	@Test
	void everySurvivingMutantHasItsEntryAndEveryEntryItsMutant() {
		assertThat(Files.isRegularFile(REPORT))
			.withFailMessage("No PIT report at %s: run ./mvnw -Pmutation test from szczur-radar-api/ (PIT, then this"
					+ " guard)", REPORT.toAbsolutePath())
			.isTrue();
		List<Mutant> mutants = MutationReport.read(REPORT);
		assertThat(mutants).as("the mutants of %s", REPORT).isNotEmpty();
		List<String> invalid = MutantRegistry.invalidReasons(AcceptedMutants.entries());
		assertThat(invalid).withFailMessage("%s", "Entries of AcceptedMutants with an invalid reason:\n"
				+ String.join("\n", invalid))
			.isEmpty();

		MutantRegistry.Comparison comparison = MutantRegistry.compare(mutants, AcceptedMutants.entries());

		assertThat(comparison.clean()).withFailMessage("%s", comparison.message()).isTrue();
	}

}
