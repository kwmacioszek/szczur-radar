package pl.szczurradar.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import pl.szczurradar.api.user.SystemAccount;

/**
 * Integration test classes that share a Spring context key share one Postgres container, so data one class
 * commits is visible to the next (S-30: {@code MigrationsIT} left posts that {@code PostRepositoryIT}'s global
 * leftover query found). This global extension (registered through {@code META-INF/services}) makes the data
 * of a class its own: after every top-level {@code @SpringBootTest} class it truncates every table of schema
 * {@code public} but {@code flyway_schema_history}, and keeps {@link SystemAccount#SCHEDULER}, the one row a
 * migration inserts; before every such class that reuses a context an earlier class used it fails, naming the tables and row
 * counts, if anything else is left (the first class of a context may seed data while the context starts).
 *
 * <p>It only touches the Testcontainers database of {@link TestcontainersConfiguration} (a context with a
 * {@code PostgreSQLContainer} bean), never a Postgres a context reaches through the {@code PG*} variables.
 * It does nothing for a class without {@code @SpringBootTest}, for a {@code @Nested} class (its callbacks
 * run in the middle of the outer class), for a context without a {@link DataSource}, and when the database
 * cannot be reached or has no tables yet (a class that runs Flyway {@code clean} itself, or one whose
 * datasource points nowhere): there is nothing to empty. The tables are listed when the check or the clean
 * runs, so a table a new migration adds is covered without a change here.
 */
public class DbIsolationExtension implements BeforeAllCallback, AfterAllCallback {

	private static final String FLYWAY_HISTORY = "flyway_schema_history";

	private static final String USERS = "users";

	private static final String LOCK_TIMEOUT = "30s";

	/** The datasources (one per context) a class has already used; weakly held, so a closed context goes away. */
	private static final Set<DataSource> CONTEXTS_SEEN = Collections.newSetFromMap(new WeakHashMap<>());

	@Override
	public void beforeAll(ExtensionContext context) {
		JdbcTemplate jdbc = jdbcOf(context);
		if (jdbc == null) {
			return;
		}
		// The first class of a context may seed data while its context starts (PipelineStartupRecoveryIT does, to
		// have runs to recover): that is its own, so only a context an earlier class already used is checked.
		boolean usedBefore;
		synchronized (CONTEXTS_SEEN) {
			usedBefore = !CONTEXTS_SEEN.add(jdbc.getDataSource());
		}
		if (!usedBefore) {
			return;
		}
		try {
			requireClean(jdbc, context.getRequiredTestClass().getName());
		}
		catch (DataAccessException unreachable) {
			// no database to check
		}
	}

	@Override
	public void afterAll(ExtensionContext context) {
		JdbcTemplate jdbc = jdbcOf(context);
		if (jdbc == null) {
			return;
		}
		try {
			tables(jdbc);
		}
		catch (DataAccessException unreachable) {
			return; // no database to empty
		}
		// A clean that fails (a lock that is not released, a lost connection) is an error of this class, not
		// something to hide: the next class would fail on leftovers with the real cause gone.
		clean(jdbc);
	}

	/**
	 * Fails when a table other than {@code flyway_schema_history} has rows (in {@code users}: other than the
	 * scheduler's), naming each table with its row count and {@code testClass}. A missing table is clean.
	 */
	static void requireClean(JdbcTemplate jdbc, String testClass) {
		List<String> leftovers = leftovers(jdbc);
		if (!leftovers.isEmpty()) {
			throw new IllegalStateException("The database is not clean before " + testClass
					+ ", an earlier test left rows: " + String.join(", ", leftovers));
		}
	}

	/** Empties every table of schema {@code public} but {@code flyway_schema_history}; {@code users} keeps the scheduler. */
	static void clean(JdbcTemplate jdbc) {
		// One transaction with a lock timeout: TRUNCATE takes an exclusive lock, and a transaction a background
		// thread of the test still holds would otherwise make it wait without end.
		new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(status -> {
			jdbc.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
			List<String> tables = tables(jdbc);
			List<String> others = new ArrayList<>(tables);
			others.remove(USERS);
			if (!others.isEmpty()) {
				jdbc.execute("TRUNCATE TABLE " + others.stream().map(DbIsolationExtension::quote)
						.collect(Collectors.joining(", ")) + " CASCADE");
			}
			if (tables.contains(USERS)) {
				jdbc.update("DELETE FROM users WHERE id <> ?", SystemAccount.SCHEDULER.id());
			}
		});
	}

	private static List<String> leftovers(JdbcTemplate jdbc) {
		List<String> leftovers = new ArrayList<>();
		for (String table : tables(jdbc)) {
			Long rows = USERS.equals(table)
					? jdbc.queryForObject("SELECT count(*) FROM users WHERE id <> ?", Long.class,
							SystemAccount.SCHEDULER.id())
					: jdbc.queryForObject("SELECT count(*) FROM " + quote(table), Long.class);
			if (rows != null && rows > 0) {
				leftovers.add(table + " (" + rows + ")");
			}
		}
		return leftovers;
	}

	private static List<String> tables(JdbcTemplate jdbc) {
		return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> ?"
				+ " ORDER BY tablename", String.class, FLYWAY_HISTORY);
	}

	private static String quote(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	/** The test's {@link JdbcTemplate}, or {@code null} when this extension has nothing to do for the class. */
	private static JdbcTemplate jdbcOf(ExtensionContext context) {
		Class<?> testClass = context.getTestClass().orElse(null);
		if (testClass == null || testClass.getEnclosingClass() != null
				|| !AnnotationSupport.isAnnotated(testClass, SpringBootTest.class)
				|| AnnotationSupport.isAnnotated(testClass, DirtiesContext.class)) {
			return null;
		}
		try {
			ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
			// Only the Testcontainers database of TestcontainersConfiguration is emptied: a context that points at
			// another Postgres (PG* variables, a developer's local database) is never touched.
			if (applicationContext.getBeanProvider(PostgreSQLContainer.class).getIfAvailable() == null) {
				return null;
			}
			DataSource dataSource = applicationContext.getBeanProvider(DataSource.class).getIfUnique();
			return dataSource == null ? null : new JdbcTemplate(dataSource);
		}
		catch (RuntimeException contextFailure) {
			// The class's own tests report a context that does not start.
			return null;
		}
	}

}
