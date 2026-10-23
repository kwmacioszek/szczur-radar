---
change_id: deploy-verification-hardening
title: Wiarygodna weryfikacja wdrożeń i alarmowanie bez blokowania Wait for CI
status: archived
created: 2026-10-20
updated: 2026-10-23
archived_at: 2026-10-23T17:40:41Z
---

## Notes

Utwardzenie weryfikacji wdrożeń Szczur Radara. Obecny deploy-verify generuje skipped i potrafi zakończyć się zielono mimo wykrytej awarii. Chcę wiarygodnych statusów green/red po deployu, rzeczywistego sprawdzania połączenia API z PostgreSQL i alarmowania bez blokowania Railway Wait for CI ani ponownego deployu tego samego commita. Rozważ wydzielenie monitoringu do osobnego repozytorium, Railway webhooks i zewnętrzny status commita, ale zweryfikuj te założenia przed wyborem architektury. Zachowaj bezpieczeństwo produkcji, obsługę PR environments i ochronę przed duplikatami zdarzeń.
