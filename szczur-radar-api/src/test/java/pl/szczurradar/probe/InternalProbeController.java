package pl.szczurradar.probe;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A test-only cron route ({@code InternalPipelineRunIT}, {@code InternalPipelineRunWithoutTokenIT}).
 * Deliberately outside {@code pl.szczurradar.api} so component scanning never adds it to other test
 * contexts; tests opt in with {@code @Import}.
 */
@RestController
public class InternalProbeController {

	@GetMapping("/internal/probe")
	Map<String, String> probe() {
		return Map.of("status", "ok");
	}

}
