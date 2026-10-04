package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.measurement.WykopGoldSampler.Anonymizer;
import pl.szczurradar.api.sources.wykop.WykopItem;

/** The gold sample's anonymization ({@link WykopGoldSampler.Anonymizer}), offline. */
class WykopAnonymizerTest {

	@Test
	void mentionsBecomeNumberedAliasesConsistentAcrossTheSample() {
		Anonymizer anonymizer = new Anonymizer(List.of());

		assertThat(anonymizer.text("@Jan_Kowalski nie stresuj @inny-nick, a @jan_kowalski wie"))
			.isEqualTo("@uzytkownik1 nie stresuj @uzytkownik2, a @uzytkownik1 wie");
		assertThat(anonymizer.text("cc @inny-nick")).isEqualTo("cc @uzytkownik2");
	}

	@Test
	void urlsBecomeMarkersAndWhiteSpaceIsKept() {
		Anonymizer anonymizer = new Anonymizer(List.of());

		assertThat(anonymizer.text("Patrz https://wykop.pl/link/1?a=b#x i www.bankier.pl/x\nkoniec"))
			.isEqualTo("Patrz <url> i <url>\nkoniec");
	}

	@Test
	void authorsNicksAreReplacedAsWholeWordsOnly() {
		Anonymizer anonymizer = new Anonymizer(List.of("Szczurek99", "ab"));

		assertThat(anonymizer.text("jak pisał Szczurek99, kupuję; Szczurek999 i ab zostają"))
			.isEqualTo("jak pisał @uzytkownik1, kupuję; Szczurek999 i ab zostają");
		assertThat(anonymizer.text("@Szczurek99 tak")).isEqualTo("@uzytkownik1 tak");
	}

	@Test
	void anElementLosesItsAuthorAndKeepsEverythingElse() {
		WykopItem raw = new WykopItem("link", 7L, "2026-10-01 10:00:00", null, "Tytuł @nick", "Opis https://x.pl",
				List.of("gielda"), new WykopItem.Author("nick"));

		WykopItem anonymized = new Anonymizer(List.of("nick")).apply(raw);

		assertThat(anonymized.author()).isNull();
		assertThat(anonymized.title()).isEqualTo("Tytuł @uzytkownik1");
		assertThat(anonymized.description()).isEqualTo("Opis <url>");
		assertThat(anonymized.createdAt()).isEqualTo(raw.createdAt());
	}

}
