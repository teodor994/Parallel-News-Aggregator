import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class TaskRunner extends Thread {
    private final int workerId;
    private final int threadCount;
    private final Context ctx;
    private final CyclicBarrier barrier;
    private final ObjectMapper jsonParser;

    public TaskRunner(int id, int count, Context context, CyclicBarrier barrier) {
        this.workerId = id;
        this.threadCount = count;
        this.ctx = context;
        this.barrier = barrier;
        this.jsonParser = new ObjectMapper();
    }

    @Override
    public void run() {
        try {
            // Phase 1: Dynamic File Ingestion
            int currentFileIdx;
            while ((currentFileIdx = ctx.ptrRead.getAndIncrement()) < ctx.fileList.size()) {
                String path = ctx.fileList.get(currentFileIdx);
                try {
                    List<NewsItem> items = jsonParser.readValue(new File(path), new TypeReference<List<NewsItem>>(){});
                    ctx.rawList.addAll(items);
                } catch (IOException e) {
                    // Fail-safe: Skip corrupted or empty files
                }
            }
            barrier.await();

            // Phase 2: Deduplication Analysis (Mapping)
            int totalRaw = ctx.rawList.size();
            int currentItemIdx;
            while ((currentItemIdx = ctx.ptrCount.getAndIncrement()) < totalRaw) {
                NewsItem item = ctx.rawList.get(currentItemIdx);
                // Atomic merge for frequency counting
                ctx.uuidFreq.merge(item.articleId, 1, Integer::sum);
                ctx.titleFreq.merge(item.title, 1, Integer::sum);
            }
            barrier.await();

            // Phase 3: Filtering Valid Articles
            // Identify unique articles based on UUID and Title counts
            while ((currentItemIdx = ctx.ptrFilter.getAndIncrement()) < totalRaw) {
                NewsItem item = ctx.rawList.get(currentItemIdx);
                boolean uniqueId = ctx.uuidFreq.get(item.articleId) == 1;
                boolean uniqueTitle = ctx.titleFreq.get(item.title) == 1;

                if (uniqueId && uniqueTitle) {
                    ctx.cleanList.add(item);
                }
            }
            barrier.await();


            // Phase 4: Data Processing & Aggregation
            int totalValid = ctx.cleanList.size();
            while ((currentItemIdx = ctx.ptrProcess.getAndIncrement()) < totalValid) {
                NewsItem item = ctx.cleanList.get(currentItemIdx);

                // 4.1 Keyword Analysis (English only)
                if ("english".equals(item.lang)) {
                    extractAndCountKeywords(item);
                }

                // 4.2 Language Classification
                if (item.lang != null && ctx.targetLangs.contains(item.lang)) {
                    ctx.registerLanguage(item.lang, item.articleId);
                }

                // 4.3 Category Classification
                if (item.categories != null) {
                    // Use Set to ensure unique category processing per item
                    Set<String> distinctCats = new HashSet<>(item.categories);
                    for (String cat : distinctCats) {
                        if (ctx.targetCats.contains(cat)) {
                            ctx.registerCategory(cat, item.articleId);
                        }
                    }
                }
            }
            // Final barrier synchronization
            barrier.await();

        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }

    /**
     * Tokenizes text and updates the global keyword frequency map.
     */
    private void extractAndCountKeywords(NewsItem item) {
        String[] tokens = item.sanitizeText();
        Set<String> uniqueTokens = new HashSet<>();

        // Filter stop words and empty strings
        for (String token : tokens) {
            if (!token.isEmpty() && !ctx.excludeWords.contains(token)) {
                uniqueTokens.add(token);
            }
        }

        // Update shared map
        for (String token : uniqueTokens) {
            ctx.keywordFreq.merge(token, 1, Integer::sum);
        }
    }
}