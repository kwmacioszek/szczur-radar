# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## Nie myl wyboru startera z pełnym projektem stacku technologicznego

- **Context**: Praca greenfield z `/10x-tech-stack-selector`, gdy projekt ma więcej zależności i elementów niż sam framework startowy.
- **Problem**: `tech-stack.md` może wyglądać zbyt skromnie, jeśli oczekuje się od niego pełnej specyfikacji technologicznej projektu. Skill jest jednak zaprojektowany przede wszystkim jako kontrakt przekazania do `/10x-bootstrapper`: wybiera agent-friendly starter, zapisuje sygnały projektu i uzasadnienie wyboru. Nie musi zawierać kompletnej listy bibliotek i decyzji implementacyjnych.
- **Rule**: Najpierw sprawdź kontrakt artefaktu i rolę skilla w całym workflow. Nie rozszerzaj `tech-stack.md` na siłę o ORM, auth, biblioteki domenowe, test tooling i inne dependencies, jeśli handoff schema tego nie wymaga — szczegółowy stack materializuje się podczas bootstrapu i późniejszego planowania implementacji. Na ścieżce custom warto zakwestionować domyślną rekomendację startera, jeśli nie pasuje do charakteru aplikacji, ale po decyzji wróć do kontraktu skilla zamiast zamieniać selector w pełny proces projektowania architektury.
- **Applies to**: plan

## Oddziel RAW od danych operacyjnych i zachowaj możliwość kompresji

- **Context**: Pipeline ingestujący niezmienne payloady z zewnętrznych źródeł (Wykop, Reddit i przyszłe źródła), szczególnie przy bezterminowej retencji i storage rozliczanym według zajętej przestrzeni.
- **Problem**: Trzymanie pełnego RAW JSON inline w głównym rekordzie posta niepotrzebnie powiększa relacyjną bazę i indeksowany/queryable storage, wiąże archiwalny payload z modelem domenowym oraz utrudnia późniejszą kompresję lub migrację do tańszego storage. Historyczny Szczur Radar osiągnął ok. 46 MB przy 11 767 wpisach z 29 dni, więc wzrost jest mierzalny i kumuluje się latami.
- **Rule**: RAW JSON z zewnętrznych źródeł zawsze przechowuj oddzielnie od znormalizowanych rekordów aplikacyjnych. Zachowaj RAW bezstratnie i projektuj jego storage tak, aby można go było transparentnie kompresować oraz później przenieść do innego backendu bez zmiany modelu domenowego, API ani utraty widoku surowych danych.
- **Applies to**: plan, plan-review, implement, impl-review

## Railway trial: limity kosztów za paywallem, ale PITR działa

- **Context**: Wdrożenie MVP na Railway na planie **trial** (bez aktywnej subskrypcji).
- **Problem**: Na trialu `railway usage limit set` zwraca „Usage limits require an active subscription" — nie da się ustawić soft/hard limitu kosztów, więc egzekwowalnej bramki wydatków nie ma do czasu subskrypcji. PITR jest natomiast dostępne na trialu: `railway postgres pitr enable` tworzy base backup + ciągłe archiwizowanie WAL. Ale automatyczny harmonogram backupów (`railway postgres pitr schedule set`) jest gated („You do not have access to this resource"). Dodatkowo `pitr progress --watch` dotyczy tylko klastrów HA — na pojedynczej instancji zwraca mylący komunikat, który łatwo wziąć za brak PITR.
- **Rule**: Na trialu Railway planuj kontrolę kosztów wokół budżetu trialu (brak egzekwowalnego twardego limitu) i ustaw limity ($ alert + hard limit) natychmiast po wykupieniu subskrypcji. Do odtwarzalności na trialu polegaj na PITR (ciągłe archiwizowanie + base backup z `pitr enable`); cykliczny harmonogram backupów traktuj jako pozycję po-subskrypcyjną. Zdrowie backupów sprawdzaj przez `railway postgres pitr status --json` (`backupSetCount`, `archiverHealthy`, `maxRestoreTime`), a nie przez `pitr progress` na usłudze nie-HA.
- **Applies to**: infra, deploy, plan
