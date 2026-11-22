package io.zincc.research;

import java.io.IOException;
import java.text.BreakIterator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebResearch {

	interface Source {

		List<String> search(String query) throws IOException, InterruptedException;

		Page fetch(String title) throws IOException, InterruptedException;

	}

	record Page(String title, String requestedUrl, String finalUrl, String extract) {
	}

	record Stored(Page page, Instant observed) {
	}

	public record Evidence(String title, String requestedUrl, String finalUrl, String quote, Instant observedAt,
			boolean cached) {
	}

	public record Answer(int discovered, int attempted, int fetched, List<Evidence> citations) {
	}

	private final Source source;

	private final Clock clock;

	private final boolean enabled;

	private final Map<String, Stored> cache = new LinkedHashMap<>(128, 0.75f, true);

	private final Semaphore slots = new Semaphore(4);

	private static final Set<String> STOP = Set.of("what", "who", "why", "which", "when", "where", "does", "the",
			"that", "and", "with", "from", "are", "was", "how", "about", "for", "can", "did", "has");

	@org.springframework.beans.factory.annotation.Autowired
	WebResearch(@Value("${zincc.local-only:false}") boolean localOnly,
			@Value("${zincc.research.enabled:true}") boolean enabled) {
		this(new WikipediaSource(WikipediaSource.API), Clock.systemUTC(), !localOnly && enabled);
	}

	WebResearch(Source source, Clock clock, boolean enabled) {
		this.source = source;
		this.clock = clock;
		this.enabled = enabled;
	}

	public boolean enabled() {
		return enabled;
	}

	public Answer answer(String question, boolean fresh) {
		if (!enabled)
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		if (question == null || question.isBlank() || question.length() > 300
				|| question.chars().anyMatch(Character::isISOControl))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		if (!slots.tryAcquire())
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
		try {
			var titles = source.search(question.strip());
			var terms = terms(question);
			var citations = new ArrayList<Evidence>();
			var seen = new HashSet<String>();
			int attempted = 0, fetched = 0;
			for (String title : titles.stream().limit(3).toList()) {
				attempted++;
				try {
					Stored stored;
					synchronized (cache) {
						stored = fresh ? null : cache.get(title);
					}
					boolean hit = stored != null
							&& stored.observed().plus(Duration.ofMinutes(5)).isAfter(clock.instant());
					if (!hit) {
						stored = new Stored(source.fetch(title), clock.instant());
						synchronized (cache) {
							cache.put(title, stored);
							if (cache.size() > 128)
								cache.remove(cache.keySet().iterator().next());
						}
					}
					fetched++;
					var page = stored.page();
					String quote = passage(page.extract(), terms);
					if (!quote.isEmpty() && seen.add(page.finalUrl()))
						citations.add(new Evidence(page.title(), page.requestedUrl(), page.finalUrl(), quote,
								stored.observed(), hit));
				}
				catch (IOException ex) {
					/* Other discovered pages can still provide evidence. */ }
			}
			return new Answer(titles.size(), attempted, fetched, List.copyOf(citations));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
		}
		catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
		}
		finally {
			slots.release();
		}
	}

	private static Set<String> terms(String text) {
		var terms = new HashSet<String>();
		for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
			if (word.length() > 1 && !STOP.contains(word))
				terms.add(word);
		return terms;
	}

	static String passage(String extract, Set<String> query) {
		var sentences = BreakIterator.getSentenceInstance(Locale.ENGLISH);
		sentences.setText(extract);
		String best = "";
		int score = 0;
		int start = sentences.first();
		for (int end = sentences.next(); end != BreakIterator.DONE; start = end, end = sentences.next()) {
			String sentence = extract.substring(start, end).strip();
			if (sentence.length() > 650)
				continue;
			var words = terms(sentence);
			words.retainAll(query);
			if (words.size() > score) {
				best = sentence;
				score = words.size();
			}
		}
		return best;
	}

}
