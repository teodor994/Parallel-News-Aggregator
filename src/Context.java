import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Context {
    // --- Input Configurations ---
    public final List<String> fileList;
    public final Set<String> targetLangs;
    public final Set<String> targetCats;
    public final Set<String> excludeWords;

    // --- Dynamic Load Balancing Pointers ---
    public final AtomicInteger ptrRead = new AtomicInteger(0);
    public final AtomicInteger ptrCount = new AtomicInteger(0);
    public final AtomicInteger ptrFilter = new AtomicInteger(0);
    public final AtomicInteger ptrProcess = new AtomicInteger(0);

    // --- Data Containers ---
    // Thread-safe list for initial ingestion
    public final List<NewsItem> rawList = Collections.synchronizedList(new ArrayList<>());

    // Frequency maps for deduplication logic
    public final ConcurrentHashMap<String, Integer> uuidFreq = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, Integer> titleFreq = new ConcurrentHashMap<>();

    // Final list of unique articles
    public final List<NewsItem> cleanList = Collections.synchronizedList(new ArrayList<>());

    // --- Aggregation Results ---
    public final ConcurrentHashMap<String, List<String>> resultByCat = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, List<String>> resultByLang = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, Integer> keywordFreq = new ConcurrentHashMap<>();

    public Context(List<String> files, Set<String> langs, Set<String> cats, Set<String> badWords) {
        this.fileList = files;
        this.targetLangs = langs;
        this.targetCats = cats;
        this.excludeWords = badWords;
    }

    public void registerCategory(String cat, String id) {
        resultByCat.computeIfAbsent(cat, k -> Collections.synchronizedList(new ArrayList<>())).add(id);
    }

    public void registerLanguage(String lang, String id) {
        resultByLang.computeIfAbsent(lang, k -> Collections.synchronizedList(new ArrayList<>())).add(id);
    }

    // Static nested class for parsing auxiliary file structure
    public static class MetaInfo {
        Set<String> targetLangs = new HashSet<>();
        Set<String> targetCats = new HashSet<>();
        Set<String> excludeWords = new HashSet<>();
    }
}