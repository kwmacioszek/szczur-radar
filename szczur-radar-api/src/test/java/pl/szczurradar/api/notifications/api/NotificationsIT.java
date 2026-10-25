package pl.szczurradar.api.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.notifications.RunNotifications;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;

/**
 * The admins' notifications about runs that need attention: who gets one from
 * {@link RunNotifications#runEnded}, that a second report adds none, and the logged-in admin's list and
 * read marks ({@code /api/admin/notifications}), and the operator alert of an admin's run that needs
 * attention, raised once to the server's queue (here a mock). Runs are written as the pipeline leaves
 * them. Every test has its own admins, so the notifications about other tests' runs (which every admin
 * of this database gets) never reach its assertions.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationsIT {

	private static final String NOTIFICATIONS = "/api/admin/notifications";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RunNotifications runNotifications;

	@MockitoBean
	OperatorAlertQueue alerts;

	private UUID admin;

	private UUID otherAdmin;

	@BeforeEach
	void admins() {
		admin = account(Role.ADMIN, true);
		otherAdmin = account(Role.ADMIN, true);
	}

	@Test
	void aFailedRunNotifiesEveryAdminWhoCanLogInOnceEvenWhenReportedTwice() {
		UUID plainUser = account(Role.USER, true);
		UUID adminWithoutLogin = account(Role.ADMIN, false);
		UUID run = run("failed", 0, 0, 0);

		runNotifications.runEnded(run);
		runNotifications.runEnded(run);

		assertThat(notifications(admin, run)).isOne();
		assertThat(notifications(otherAdmin, run)).isOne();
		assertThat(notifications(plainUser, run)).isZero();
		assertThat(notifications(adminWithoutLogin, run)).isZero();
		assertThat(notifications(SystemAccount.SCHEDULER.id(), run)).isZero();
	}

	@Test
	void anAdminsRunThatNeedsAttentionIsRaisedToTheOperatorOnce() {
		UUID run = run("partial", 1, 1, 0, admin);
		UUID cronRun = run("failed", 0, 0, 0, SystemAccount.SCHEDULER.id());

		runNotifications.runEnded(run);
		runNotifications.runEnded(run);
		runNotifications.runEnded(cronRun);

		assertThat(notifications(admin, run)).isOne();
		verify(alerts, times(1)).raise(eq(AlertEvent.RUN_NEEDS_ATTENTION), eq(run),
				argThat(detail -> detail.startsWith("Pipeline run " + run + " (retry, admin) ended partial: dead 1")
						&& detail.endsWith("/admin/runs/" + run)));
		// The cron's failed run is the cron's to report.
		assertThat(notifications(admin, cronRun)).isOne();
		verify(alerts, never()).raise(any(), eq(cronRun), any());
	}

	@ParameterizedTest
	@CsvSource({
			// An interruption (deploy, restart, shutdown).
			"interrupted, 0, 0, 0",
			// A post used its third attempt.
			"partial, 1, 1, 0",
			// A post with a technical error: no assessment stored.
			"partial, 0, 0, 1" })
	void aRunThatNeedsAttentionNotifiesTheAdmins(String status, int failed, int dead, int errors) {
		UUID run = run(status, failed, dead, errors);

		runNotifications.runEnded(run);

		assertThat(notifications(admin, run)).isOne();
		assertThat(notifications(otherAdmin, run)).isOne();
	}

	@ParameterizedTest
	@CsvSource({
			"succeeded, 0, 0, 0",
			// Only failed posts with attempts left: the next run retries them.
			"partial, 2, 0, 0" })
	void aRunThatNeedsNoAttentionNotifiesNobody(String status, int failed, int dead, int errors) {
		UUID run = run(status, failed, dead, errors);

		runNotifications.runEnded(run);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE run_id = ?", Integer.class, run))
			.isZero();
	}

	@Test
	void anUnknownRunNotifiesNobodyAndThrowsNothing() {
		runNotifications.runEnded(UUID.randomUUID());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ?", Integer.class,
				admin)).isZero();
	}

	@Test
	void theListShowsTheTenNewestWithTheirRunsAndCountsEveryUnread() {
		List<UUID> runs = notifiedRuns(12);
		UUID newest = runs.getLast();
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', interruption_cause = 'deploy',"
				+ " finished_at = '2026-10-16T00:41:00Z', assessed_failed = 2, dead = 1, processing_errors = 3,"
				+ " skipped = 4, error = 'interrupted: a new deployment took over' WHERE id = ?", newest);

		MvcTestResult list = list(admin);

		assertThat(list).hasStatusOk();
		assertThat(list).bodyJson().extractingPath("$.unread").isEqualTo(12);
		assertThat(list).bodyJson()
			.extractingPath("$.items[*].run.id")
			.asArray()
			.containsExactlyElementsOf(runs.reversed().subList(0, 10).stream().map(UUID::toString).toList());
		Map<String, Object> item = JsonPath.read(content(list), "$.items[0]");
		assertThat(item).containsOnlyKeys("id", "kind", "createdAt", "readAt", "run")
			.containsEntry("id", notificationId(admin, newest).toString())
			.containsEntry("kind", "run_attention")
			.containsEntry("createdAt", "2026-10-16T00:11:00Z")
			.containsEntry("readAt", null);
		assertThat(JsonPath.<Map<String, Object>>read(content(list), "$.items[0].run"))
			.containsOnlyKeys("id", "kind", "status", "interruptionCause", "finishedAt", "assessedFailed", "dead",
					"processingErrors", "skipped", "error")
			.containsEntry("id", newest.toString())
			.containsEntry("kind", "retry")
			.containsEntry("status", "interrupted")
			.containsEntry("interruptionCause", "deploy")
			.containsEntry("finishedAt", "2026-10-16T00:41:00Z")
			.containsEntry("assessedFailed", 2)
			.containsEntry("dead", 1)
			.containsEntry("processingErrors", 3)
			.containsEntry("skipped", 4)
			.containsEntry("error", "interrupted: a new deployment took over");
	}

	@Test
	void anAdminWithoutNotificationsGetsAnEmptyList() {
		assertThat(list(admin)).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"unread\":0,\"items\":[]}");
	}

	@Test
	void anAdminMarksTheirOwnNotificationReadButNotAnotherAdminsOne() {
		UUID run = notifiedRuns(1).getFirst();
		UUID own = notificationId(admin, run);
		UUID others = notificationId(otherAdmin, run);

		assertThat(read(admin, own)).hasStatus(HttpStatus.NO_CONTENT);

		Timestamp readAt = readAt(own);
		assertThat(readAt).isNotNull();
		assertThat(list(admin)).bodyJson().extractingPath("$.unread").isEqualTo(0);
		assertThat(list(admin)).bodyJson().extractingPath("$.items[0].readAt").isNotNull();
		// Read again: still 204, the first read time stays.
		assertThat(read(admin, own)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(readAt(own)).isEqualTo(readAt);

		assertThat(read(admin, others)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_found\"}");
		assertThat(readAt(others)).isNull();
		assertThat(list(otherAdmin)).bodyJson().extractingPath("$.unread").isEqualTo(1);
		assertThat(read(admin, UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_found\"}");
		assertThat(mvc.post().uri(NOTIFICATIONS + "/not-a-uuid/read").with(as(admin)))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
	}

	@Test
	void markingAllReadClearsTheCountBeyondTheListAndLeavesAnotherAdminsAlone() {
		notifiedRuns(12);
		assertThat(list(admin)).bodyJson().extractingPath("$.unread").isEqualTo(12);

		assertThat(mvc.post().uri(NOTIFICATIONS + "/read").with(as(admin))).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult list = list(admin);
		assertThat(list).bodyJson().extractingPath("$.unread").isEqualTo(0);
		assertThat(JsonPath.<List<Object>>read(content(list), "$.items[*].readAt")).hasSize(10).doesNotContainNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND read_at IS NULL",
				Integer.class, admin)).isZero();
		// Again: nothing left to mark, still 204.
		assertThat(mvc.post().uri(NOTIFICATIONS + "/read").with(as(admin))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(list(otherAdmin)).bodyJson().extractingPath("$.unread").isEqualTo(12);
	}

	/**
	 * {@code count} failed runs, each reported to {@link RunNotifications#runEnded}, oldest first; their
	 * notifications were created a minute apart in this order, from 2026-10-16 00:00 UTC.
	 */
	private List<UUID> notifiedRuns(int count) {
		List<UUID> runs = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			UUID run = run("failed", 0, 0, 0);
			runNotifications.runEnded(run);
			jdbc.update("UPDATE notifications SET created_at = TIMESTAMPTZ '2026-10-16T00:00:00Z'"
					+ " + CAST(? AS INTEGER) * INTERVAL '1 minute' WHERE run_id = ?", i, run);
			runs.add(run);
		}
		return runs;
	}

	/** A finished retry run with these counters, requested by the scheduler. */
	private UUID run(String status, int assessedFailed, int dead, int processingErrors) {
		return run(status, assessedFailed, dead, processingErrors, SystemAccount.SCHEDULER.id());
	}

	/** A finished retry run with these counters, requested by {@code requestedBy}. */
	private UUID run(String status, int assessedFailed, int dead, int processingErrors, UUID requestedBy) {
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at, finished_at,"
				+ " assessed_failed, dead, processing_errors) VALUES ('retry', ?, ?, now(), now(), ?, ?, ?)"
				+ " RETURNING id", UUID.class, status, requestedBy, assessedFailed, dead, processingErrors);
	}

	/** An account without a password (as before its activation) with {@code role}. */
	private UUID account(Role role, boolean loginEnabled) {
		return jdbc.queryForObject("INSERT INTO users (email, role, login_enabled) VALUES (?, ?, ?) RETURNING id",
				UUID.class, "notified-" + UUID.randomUUID() + "@example.com", role.value(), loginEnabled);
	}

	private int notifications(UUID recipient, UUID run) {
		return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND run_id = ?"
				+ " AND kind = 'run_attention' AND read_at IS NULL", Integer.class, recipient, run);
	}

	private UUID notificationId(UUID recipient, UUID run) {
		return jdbc.queryForObject("SELECT id FROM notifications WHERE recipient_id = ? AND run_id = ?", UUID.class,
				recipient, run);
	}

	private Timestamp readAt(UUID notification) {
		return jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, notification);
	}

	private MvcTestResult list(UUID recipient) {
		return mvc.get().uri(NOTIFICATIONS).with(as(recipient)).exchange();
	}

	private MvcTestResult read(UUID recipient, UUID notification) {
		return mvc.post().uri(NOTIFICATIONS + "/" + notification + "/read").with(as(recipient)).exchange();
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static RequestPostProcessor as(UUID account) {
		return user(account.toString()).roles(Role.ADMIN.name());
	}

}
