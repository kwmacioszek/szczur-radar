@REPOSITORY_GUIDELINES.md

<!-- BEGIN @przeprogramowani/10x-cli -->

## Zestaw narzędzi AI 10xDevs — moduł 5, lekcja 5 (agenci asynchroniczni i zdalni)

Deleguj ograniczoną, zaplanowaną zmianę i wróć, aby ją przejrzeć. Kontrola nie znika; przenosi się do kontraktu zadania, konfiguracji sandboxa i końcowego przeglądu.

```
/10x-plan -> /goal + /10x-goal-implement (interactive or claude -p) -> review the commits and the manual checklist
```

### Router zadań — od czego zacząć

| Umiejętność | Użyj jej, gdy |
| --- | --- |
| `/10x-goal-implement <change-id>` | Zatwierdzony `context/changes/<id>/plan.md` powinien zostać wykonany bez nadzoru. Połącz go z `/goal <condition>`; zapisuje commit po każdej zielonej fazie i kończy się oczekującymi ręcznymi kontrolami. |
| `/10x-implement` | Chcesz samodzielnie kierować każdą fazą. |

### Twarde zasady

- **Deleguj wyłącznie zaplanowaną pracę.** Brak zatwierdzonego `plan.md` oznacza brak działania bez nadzoru.
- **Ogranicz każde uruchomienie.** Warunek `/goal` określa stan końcowy i limit tur.
- **Zielone uruchomienie nie oznacza ukończonej zmiany.** Przejrzyj commity i wykonaj ręczną checklistę przed mergowaniem.
- **Przyznaj sandboxowi tylko te sekrety i dostęp do sieci, których zadanie potrzebuje.**
- **Monitoruj równoległe i zdalne uruchomienia w jednym miejscu.** Uruchamiaj każdą sesję bez nadzoru w osobnym obszarze roboczym herdr, aby widzieć, którzy agenci pracują, są zablokowani lub skończyli, oraz interweniować z przeglądarki (konfiguracja w M2L6 Deep Dive).

<!-- END @przeprogramowani/10x-cli -->
