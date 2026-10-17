@REPOSITORY_GUIDELINES.md

<!-- BEGIN @przeprogramowani/10x-cli -->

## Zestaw narzędzi AI 10xDevs — Moduł 4, Lekcja 2 (Mapa projektu)

Zapoznaj się z nieznanym lub starszym repozytorium na podstawie dowodów, a nie drzew katalogów: **najpierw szerokie skanowanie, później głębokie skupienie.**

```
git history + dependency graph + contributors -> context/map/repo-map.md -> focused /10x-research
```

### Router zadań — od czego zacząć

| Umiejętność / prompt | Użyj, gdy |
| --- | --- |
| `/10x-repo-map` | Mapa projektu w jednym przebiegu. Klasyfikuje możliwości biznesowe według krytyczności × rozgłosu, przechowuje dowody w `context/map/evidence/` i zapisuje `context/map/repo-map.md` ze strefami ryzyka, listą lektur na pierwszy dzień oraz wyraźnie wskazanymi niewiadomymi. Użyj `--replace`, aby odświeżyć; poprzedni przebieg zostanie przeniesiony do `context/archive/`. |
| `m4l2-1-territory-git-history` -> `m4l2-2-structure-dependency-cruiser` -> `m4l2-3-contributors-git` -> `m4l2-repo-map-synthesis` | Ta sama mapa budowana krok po kroku, po jednym artefakcie naraz, aby zobaczyć, co dodaje każde źródło dowodów. |
| `/10x-research` | Pytanie dotyczące jednej funkcji lub obszaru. Mapa obejmuje szerokość i nie przyjmuje celu; głębia należy do badań. |

### Twarde zasady

- **Pozwól narzędziom zbierać dowody poza oknem kontekstu.** Używaj narzędzi git, forge i grafowych do gromadzenia danych, a następnie interpretuj wyłącznie ich skondensowane wyniki. Nigdy nie czytaj repozytorium plik po pliku.
- **Wyklucz szum.** Podczas klasyfikowania aktywności pomijaj lockfile'y, wygenerowany kod, commity masowego formatowania, commity botów oraz commity utworzone przez agentów.
- **Niewiadome są częścią mapy.** Oznaczaj jako niewiadome to, czego dowody nie mogą pokazać, zamiast wypełniać lukę zgadywaniem.

<!-- END @przeprogramowani/10x-cli -->
