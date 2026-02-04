import java.io.*;
import java.util.*;
import java.util.concurrent.CyclicBarrier;

public class Tema1 {
    public static void main(String[] args) throws IOException, InterruptedException {
        // Verify command line arguments availability
        if (args.length < 3) {
            System.err.println("Usage: java Tema1 <threads> <articles_file> <aux_file>");
            return;
        }

        // Parse input arguments
        int threadCount = Integer.parseInt(args[0]);
        String articlesPath = args[1];
        String auxPath = args[2];

        // 1. Ingest metadata and file paths
        Context.MetaInfo metaData = parseMetadata(auxPath);
        List<String> filePaths = retrieveFilePaths(articlesPath);

        // 2. Initialize shared context for parallel processing
        Context sharedCtx = new Context(
                filePaths,
                metaData.targetLangs,
                metaData.targetCats,
                metaData.excludeWords
        );

        // 3. Setup synchronization barrier and workers
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        TaskRunner[] workerPool = new TaskRunner[threadCount];

        for (int i = 0; i < threadCount; i++) {
            workerPool[i] = new TaskRunner(i, threadCount, sharedCtx, barrier);
            workerPool[i].start();
        }

        // 4. Await completion of all worker threads
        for (TaskRunner worker : workerPool) {
            worker.join();
        }

        // 5. Aggregate and export results (Sequential execution on Main Thread)
        // This ensures determinism in file writing order.

        // 5.1 Export Language files
        for (String lang : sharedCtx.targetLangs) {
            if (sharedCtx.resultByLang.containsKey(lang)) {
                List<String> ids = sharedCtx.resultByLang.get(lang);
                Collections.sort(ids); // Lexicographical sort
                exportToFile(lang + ".txt", ids);
            }
        }

        // 5.2 Export Category files
        for (String cat : sharedCtx.targetCats) {
            if (sharedCtx.resultByCat.containsKey(cat)) {
                List<String> ids = sharedCtx.resultByCat.get(cat);
                Collections.sort(ids);

                // Normalize filename: remove commas, replace spaces with underscores [cite: 61]
                String normalizedName = cat.replace(",", "").replace(" ", "_") + ".txt";
                exportToFile(normalizedName, ids);
            }
        }

        // 5.3 Global Article List (all_articles.txt)
        // Sort criteria: Published Date (DESC), UUID (ASC) [cite: 73]
        List<NewsItem> finalSortedList = new ArrayList<>(sharedCtx.cleanList);
        finalSortedList.sort((item1, item2) -> {
            int dateCompare = item2.publishDate.compareTo(item1.publishDate);
            if (dateCompare != 0) return dateCompare;
            return item1.articleId.compareTo(item2.articleId);
        });

        try (PrintWriter writer = new PrintWriter("all_articles.txt")) {
            for (NewsItem item : finalSortedList) {
                writer.println(item.articleId + " " + item.publishDate);
            }
        }

        // 5.4 Keyword Frequency List
        // Sort criteria: Count (DESC), Keyword (ASC) [cite: 93]
        List<Map.Entry<String, Integer>> keywordList = new ArrayList<>(sharedCtx.keywordFreq.entrySet());
        keywordList.sort((e1, e2) -> {
            int valCompare = e2.getValue().compareTo(e1.getValue());
            if (valCompare != 0) return valCompare;
            return e1.getKey().compareTo(e2.getKey());
        });

        try (PrintWriter writer = new PrintWriter("keywords_count.txt")) {
            for (Map.Entry<String, Integer> entry : keywordList) {
                writer.println(entry.getKey() + " " + entry.getValue());
            }
        }

        // 5.5 Final Statistics Report
        compileReport(sharedCtx, finalSortedList, keywordList);
    }

    /**
     * Helper to read the main articles input file containing paths to JSONs.
     */
    private static List<String> retrieveFilePaths(String filePath) throws IOException {
        List<String> paths = new ArrayList<>();
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine(); // Skip count header
            while ((line = br.readLine()) != null) {
                if(!line.trim().isEmpty()) {
                    // Resolve relative path against input file location
                    File absoluteFile = new File(parentDir, line.trim());
                    paths.add(absoluteFile.getPath());
                }
            }
        }
        return paths;
    }

    /**
     * Helper to parse auxiliary data (languages, categories, stop words).
     */
    private static Context.MetaInfo parseMetadata(String filePath) throws IOException {
        Context.MetaInfo info = new Context.MetaInfo();
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // Skip count

            String fLang = br.readLine();
            String fCat = br.readLine();
            String fLink = br.readLine();

            info.targetLangs = loadSet(new File(parentDir, fLang.trim()).getPath());
            info.targetCats = loadSet(new File(parentDir, fCat.trim()).getPath());
            info.excludeWords = loadSet(new File(parentDir, fLink.trim()).getPath());
        }
        return info;
    }

    private static Set<String> loadSet(String path) throws IOException {
        Set<String> set = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // Skip count header
            String line;
            while ((line = br.readLine()) != null) {
                set.add(line.trim());
            }
        }
        return set;
    }

    private static void exportToFile(String filename, List<String> lines) throws FileNotFoundException {
        try (PrintWriter pw = new PrintWriter(filename)) {
            for (String line : lines) pw.println(line);
        }
    }

    /**
     * Generates the 'reports.txt' file with aggregate statistics.
     */
    private static void compileReport(Context ctx, List<NewsItem> items, List<Map.Entry<String, Integer>> kws) throws FileNotFoundException {
        long duplicateCount = ctx.rawList.size() - ctx.cleanList.size();
        long uniqueCount = ctx.cleanList.size();

        // Determine most prolific author
        Map<String, Integer> authFreq = new HashMap<>();
        for(NewsItem item : ctx.cleanList) authFreq.merge(item.authorName, 1, Integer::sum);
        String topAuthor = findDominantKey(authFreq);

        // Determine dominant language
        Map<String, Integer> langFreq = new HashMap<>();
        for(NewsItem item : ctx.cleanList) langFreq.merge(item.lang, 1, Integer::sum);
        String topLang = findDominantKey(langFreq);

        // Determine top category
        Map<String, Integer> catFreq = new HashMap<>();
        for(NewsItem item : ctx.cleanList) {
            if (item.categories != null) {
                // Use Set to deduplicate categories within the same article scope
                Set<String> distinctCats = new HashSet<>(item.categories);
                for (String c : distinctCats) {
                    if (ctx.targetCats.contains(c)) {
                        catFreq.merge(c, 1, Integer::sum);
                    }
                }
            }
        }
        String topCat = findDominantKey(catFreq);
        String normTopCat = topCat.isEmpty() ? "" : topCat.replace(",", "").replace(" ", "_");

        // Identify most recent article
        NewsItem recentItem = items.isEmpty() ? null : items.get(0);

        // Identify top keyword
        String topKw = kws.isEmpty() ? "" : kws.get(0).getKey();
        int topKwCount = kws.isEmpty() ? 0 : kws.get(0).getValue();

        try (PrintWriter pw = new PrintWriter("reports.txt")) {
            pw.println("duplicates_found - " + duplicateCount);
            pw.println("unique_articles - " + uniqueCount);
            pw.println("best_author - " + (topAuthor.isEmpty() ? "" : topAuthor + " " + authFreq.get(topAuthor)));
            pw.println("top_language - " + (topLang.isEmpty() ? "" : topLang + " " + langFreq.get(topLang)));
            pw.println("top_category - " + (normTopCat.isEmpty() ? "" : normTopCat + " " + catFreq.get(topCat)));
            pw.println("most_recent_article - " + (recentItem == null ? "" : recentItem.publishDate + " " + recentItem.url));
            pw.println("top_keyword_en - " + (topKw.isEmpty() ? "" : topKw + " " + topKwCount));
        }
    }

    /**
     * Utility to find map key with Max Value (Tie-break: Alphabetical Key)
     */
    private static String findDominantKey(Map<String, Integer> map) {
        return map.entrySet().stream()
                .sorted((e1, e2) -> {
                    int valCmp = e2.getValue().compareTo(e1.getValue());
                    if (valCmp == 0) return e1.getKey().compareTo(e2.getKey());
                    return valCmp;
                })
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }
}