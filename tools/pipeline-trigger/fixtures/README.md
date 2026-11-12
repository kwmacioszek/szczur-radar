# Bodies of the API the trigger reads (S-43)

`api/*.json` are the answers of `/internal/pipeline-runs/**` that `trigger.sh` reads, one file each:
`daily-started.json` (202 of `POST /daily`), `run-state-failed.json`, `run-state-running.json`,
`run-state-interrupted.json` and `run-state-queued.json` (`GET /{id}`; a `succeeded` or `partial` run has
the shape of the failed one) and `nightly-summary.json` (200 of `POST /nightly-summary`).

Read by:

- `InternalPipelineRunIT` (the API): compares the real answer, masked (every UUID is `<uuid>`, every instant
  `<instant>`, see `ContractFiles.assertMatches(json, Path)`), with the file. A changed shape of
  `RunState`, `StartedRun` or the summary turns it red until the file is changed.
- `test.sh` (this tool): builds its stub API from the files. `<uuid>` is the run's id, `<instant>` a fixed
  time of the stub; a case may override only a key the file has (an unknown key is an error of the stub, so
  a field cannot be added to the stub alone).

The error bodies (`{"detail":code}`) are not files: their codes are checked against
`web/src/contract/rules.json` `errorCodes` by `test.sh`, and the statuses of `classify` against
`values.runStatus`.
