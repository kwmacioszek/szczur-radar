package pl.szczurradar.api.mutation;

import java.util.List;

/**
 * The surviving mutants of the core accepted with a reason (S-45). PIT mutates the core's classes (the list is in the
 * profile {@code mutation} of {@code pom.xml}) and {@code SurvivingMutantsTest} compares its report with this
 * registry after every {@code ./mvnw -Pmutation test}.
 * <p>
 * The rule: a mutant that survives ({@code SURVIVED} or {@code NO_COVERAGE}) is killed by a test of behavior, or it
 * gets an entry here with a concrete reason starting with its category:
 * <ul>
 * <li>{@code równoważny:} — why the behavior does not change;</li>
 * <li>{@code obronny:} — a branch the input contract makes unreachable, naming the place that rules it out;</li>
 * <li>{@code tekst:} — the mutant changes only the text of a message that is no contract.</li>
 * </ul>
 * An entry whose mutant no longer survives (killed, timed out, gone) fails the guard, so the registry does not rot.
 * <p>
 * An entry is the mutant's identity without its line ({@link MutantKey}); the guard prints a surviving mutant without
 * an entry as an entry ready to paste, its line in a comment. One entry answers for one mutant: the same key on two
 * lines needs two entries. A lambda's method is written without its number ({@code lambda$name}, not
 * {@code lambda$name$0}), also in the description.
 */
final class AcceptedMutants {

	/** The entries; an array, so each pasted entry may end with a comma. */
	private static final AcceptedMutant[] ENTRIES = {
		// agents.assessor (S-45 phase 3)
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.AssessmentResult", "callFailed",
				"()Z",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_IF",
				"removed conditional - replaced equality check with true",
				"równoważny: the status check dropped: the constructor gives an ok result no failure class (failureClass == null), so the next check is already false for it"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.AssessmentResult", "callFailed",
				"()Z",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_IF",
				"removed conditional - replaced equality check with true",
				"równoważny: the null check dropped: the constructor gives every failed result a failure class and status == FAILED is checked first, so failureClass is never null here"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.OutputParser", "readSchemaText",
				"()Ljava/lang/String;",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_ELSE",
				"removed conditional - replaced equality check with false",
				"obronny: the schema resource is packed in the jar from src/main/resources/assessor/ (ContractSchemaTest and every OutputParser test read it); without it readAllBytes on null would still fail the start, only with an NPE instead of the message"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.OutputParser", "readSchemaText",
				"()Ljava/lang/String;",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_IF",
				"removed conditional - replaced equality check with true",
				"równoważny: in PIT's run only: readSchemaText runs once per JVM, in the class initializer of OutputParser.SchemaText, which PIT's minion has run before it inserts this mutant, so no test executes it; outside PIT the always-thrown error would fail every OutputParser test"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.OutputParser", "readSchemaText",
				"()Ljava/lang/String;",
				"org.pitest.mutationtest.engine.gregor.mutators.returns.EmptyObjectReturnValsMutator",
				"replaced return value with \"\" for pl/szczurradar/api/agents/assessor/validation/OutputParser::readSchemaText",
				"równoważny: in PIT's run only: readSchemaText runs once per JVM, in the class initializer of OutputParser.SchemaText, which PIT's minion has run before it inserts this mutant, so no test executes it; outside PIT an empty schema text would fail every OutputParser test"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.OutputParser", "parse",
				"(Ljava/lang/String;)Lpl/szczurradar/api/agents/assessor/validation/OutputParser$Result;",
				"org.pitest.mutationtest.engine.gregor.mutators.returns.NullReturnValsMutator",
				"replaced return value with null for pl/szczurradar/api/agents/assessor/validation/OutputParser::parse",
				"obronny: the mapping catch after the schema check: JSON the V2 schema accepted always maps to the records, because ContractSchemaTest keeps the records and the schema the same shape (properties, order, enums, limits)"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.StanceTable", "assertiveness",
				"()Lpl/szczurradar/api/agents/assessor/validation/StanceTable$AssertivenessPresence;",
				"org.pitest.mutationtest.engine.gregor.mutators.returns.NullReturnValsMutator",
				"replaced return value with null for pl/szczurradar/api/agents/assessor/validation/StanceTable::assertiveness",
				"tekst: its only reader is the message of a D12 violation (StanceAssertivenessRule: \"null\" or \"hedged or unhedged\"); allowsAssertiveness reads the field itself"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.StanceTable", "allowsClaimIdCount",
				"(I)Z",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_ORDER_IF",
				"removed conditional - replaced comparison check with true",
				"równoważny: count >= 0 of the ALLOWED row dropped: its only caller passes stance.claimIds().size(), a list size that is never negative (the bound count <= 4 of the same row is killed in ContractV2RulesTest)"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.rules.IrrelevantHasNoContentRule", "check",
				"(Lpl/szczurradar/api/agents/assessor/contract/AssessmentInputV1;Lpl/szczurradar/api/agents/assessor/contract/AssessmentOutputV2;)Ljava/util/List;",
				"org.pitest.mutationtest.engine.gregor.mutators.returns.EmptyObjectReturnValsMutator",
				"replaced return value with Collections.emptyList for pl/szczurradar/api/agents/assessor/validation/rules/IrrelevantHasNoContentRule::check",
				"równoważny: the early return of a relevant output returns the list just created and still empty; RuleBasedValidator only reads it (addAll)"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.rules.QuotedText", "resolvesToText",
				"(Lpl/szczurradar/api/agents/assessor/contract/AssessmentInputV1;)Z",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_IF",
				"removed conditional - replaced equality check with true",
				"równoważny: the D7 check (normalized quote not empty) dropped from D6's filter: a quote empty after normalization is the empty string, which every normalized field contains, so D6 reports it neither way (D7 does); the other check of the line (resolves) is killed by an NPE on a missing field"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.rules.QuotedText", "location",
				"()Ljava/lang/String;",
				"org.pitest.mutationtest.engine.gregor.mutators.returns.EmptyObjectReturnValsMutator",
				"replaced return value with \"\" for pl/szczurradar/api/agents/assessor/validation/rules/QuotedText::location",
				"tekst: location() is read only for the messages of D5 and D6 violations (\"quoted field item.body does not exist\"); rule, path and class stay"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.rules.StanceAssertivenessRule", "check",
				"(Lpl/szczurradar/api/agents/assessor/contract/AssessmentInputV1;Lpl/szczurradar/api/agents/assessor/contract/AssessmentOutputV2;)Ljava/util/List;",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_ELSE",
				"removed conditional - replaced equality check with false",
				"tekst: the choice between \"null\" and \"hedged or unhedged\" in the D12 violation's message; whether D12 fires, its path and class do not change"),
		AcceptedMutant.of("pl.szczurradar.api.agents.assessor.validation.rules.StanceAssertivenessRule", "check",
				"(Lpl/szczurradar/api/agents/assessor/contract/AssessmentInputV1;Lpl/szczurradar/api/agents/assessor/contract/AssessmentOutputV2;)Ljava/util/List;",
				"org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_IF",
				"removed conditional - replaced equality check with true",
				"tekst: the choice between \"null\" and \"hedged or unhedged\" in the D12 violation's message; whether D12 fires, its path and class do not change"),
	};

	private AcceptedMutants() {
	}

	static List<AcceptedMutant> entries() {
		return List.of(ENTRIES);
	}

}
