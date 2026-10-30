package pl.szczurradar.api.sources;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.ItemType;

/**
 * A {@link SourceFetcher} of a given {@link Source} whose answer a test scripts, without Spring and
 * without a network: posts, no post, a thrown exception, an answer held on a latch without a bound, or
 * one delayed by a {@link Duration} (only the watchdog's scenarios, which measure time). Every switch is
 * {@code volatile}: the run's queue thread reads what the test thread set.
 *
 * <p>Not configured until a test says so ({@link #configured(boolean)}), so a range run that names no
 * source fetches only the others, and {@link #reset} brings it back to that. A held or delayed fetch whose
 * thread is interrupted throws, keeping the interrupt flag, as a source's client does.
 *
 * <p>Only for the engine's unit tests ({@code pipeline/step/SourceIngestTest}), where the sources are
 * placeholders. The integration tests run real sources over local servers: Wykop on
 * {@code LocalWykopServer}, Reddit on {@code sources/reddit/LocalReddit}.
 */
public final class ScriptedSourceFetcher implements SourceFetcher {

	/** Unique source ids across every fetcher and scenario of the JVM. */
	private static final AtomicInteger IDS = new AtomicInteger();

	private final Source source;

	private volatile boolean configured;

	private volatile Supplier<Fetched> answer = ScriptedSourceFetcher::nothing;

	private volatile @Nullable CountDownLatch hold;

	private volatile @Nullable Duration delay;

	private final AtomicInteger calls = new AtomicInteger();

	private volatile @Nullable Range lastRange;

	public ScriptedSourceFetcher(Source source) {
		this.source = source;
	}

	@Override
	public Source source() {
		return source;
	}

	@Override
	public boolean configured() {
		return configured;
	}

	@Override
	public Fetched fetch(Instant from, Instant to) {
		calls.incrementAndGet();
		lastRange = new Range(from, to);
		try {
			CountDownLatch latch = hold;
			if (latch != null) {
				// Without a bound: only release() or an interrupt ends it.
				latch.await();
			}
			Duration pause = delay;
			if (pause != null) {
				Thread.sleep(pause);
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(source.value() + ": the fetch was interrupted", ex);
		}
		return answer.get();
	}

	/** Whether a range run can fetch it; {@code false} until a test sets it. */
	public ScriptedSourceFetcher configured(boolean configured) {
		this.configured = configured;
		return this;
	}

	/** Answers {@code fetched}; its posts must be of this fetcher's source. */
	public ScriptedSourceFetcher answer(Fetched fetched) {
		if (fetched.posts().stream().anyMatch(post -> post.source() != source)) {
			throw new IllegalArgumentException("a post of another source than " + source.value());
		}
		this.answer = () -> fetched;
		return this;
	}

	/** Answers {@code count} posts of {@link #posts(int)}, nothing skipped. */
	public ScriptedSourceFetcher answerPosts(int count) {
		List<SourcePost> posts = posts(count);
		return answer(new Fetched(posts, posts.size(), 0));
	}

	/** Answers no post: {@code fetched} elements, every one skipped. */
	public ScriptedSourceFetcher answerEmpty(int fetched) {
		return answer(new Fetched(List.of(), fetched, fetched));
	}

	/** Throws {@code failure} as the source's own failure. */
	public ScriptedSourceFetcher fail(RuntimeException failure) {
		this.answer = () -> {
			throw failure;
		};
		return this;
	}

	/** Holds each fetch until {@link #release()} (or an interrupt) before it answers. */
	public ScriptedSourceFetcher hold() {
		this.hold = new CountDownLatch(1);
		return this;
	}

	/** Lets a held fetch answer. */
	public void release() {
		CountDownLatch latch = hold;
		if (latch != null) {
			latch.countDown();
		}
	}

	/** Pauses each fetch for {@code delay} before it answers: only for the watchdog's scenarios. */
	public ScriptedSourceFetcher delay(Duration delay) {
		this.delay = delay;
		return this;
	}

	/** Back to the start: not configured, answering nothing, no hold (a held fetch released) or delay, no calls. */
	public void reset() {
		release();
		configured = false;
		answer = ScriptedSourceFetcher::nothing;
		hold = null;
		delay = null;
		calls.set(0);
		lastRange = null;
	}

	/** How many fetches started. */
	public int calls() {
		return calls.get();
	}

	/** The range of the last fetch, or {@code null} before the first. */
	public @Nullable Range lastRange() {
		return lastRange;
	}

	/** {@code count} posts of this source, each with a source id no other has. */
	public List<SourcePost> posts(int count) {
		return IntStream.range(0, count).mapToObj(i -> post(source.value() + "-" + IDS.incrementAndGet())).toList();
	}

	/** A post of this source with the source id {@code id}. */
	public SourcePost post(String id) {
		return new SourcePost(source, ItemType.POST, id, "kanal", Instant.parse("2026-10-01T08:00:00Z"), null,
				"treść " + id, null, null, null, List.of(), "{}".getBytes(StandardCharsets.UTF_8));
	}

	private static Fetched nothing() {
		return new Fetched(List.of(), 0, 0);
	}

	/** The range a fetch was asked for. */
	public record Range(Instant from, Instant to) {
	}

}
