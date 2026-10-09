package pl.szczurradar.api.raw;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Raw payloads; only {@link PostgresRawPayloadStore} uses it. */
interface RawPayloadRepository extends JpaRepository<RawPayload, UUID> {

}
