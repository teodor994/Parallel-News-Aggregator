# Parallel News Aggregator
## Project Overview
This project implements a parallel news aggregator in Java. The application processes a large collection of news articles stored in JSON files, organizes them by categories and languages, removes duplicates, and generates multiple aggregated reports and statistics. The solution is designed to efficiently exploit multithreading using a fixed number of Java threads.

## Implemented Features
### A) Parallel processing
 - Uses a fixed pool of threads created at program start.

 - Work is distributed among threads to process input files concurrently.

 - Thread-safe data structures and synchronization mechanisms ensure correctness.

### B) JSON article parsing
 - Extracts relevant fields from each article: uuid, title, author, url, text, published, language, and categories.

 - Efficiently handles large input datasets.

### C) Duplicate elimination
 - Articles are considered duplicates if they share the same uuid or title.

 - All duplicate articles are removed from further processing.

 - The number of removed duplicates is reported.

### D) Category-based organization
 - Articles are grouped according to a predefined list of valid categories.

 - One output file is generated per category, containing sorted article UUIDs.

 - Category names are normalized to generate valid file names.

### E) Language-based organization
 - Articles are grouped by language using a predefined list of valid languages.

 - One output file is generated per language, containing sorted article UUIDs.

### F) Global article list
 - Generates all_articles.txt containing all unique articles.

 - Articles are sorted by publication date (descending), with UUID as a tie-breaker.

### G) Keyword analysis (English articles)
 - Processes only English-language articles.

 - Removes linking words defined in an external file.

 - Counts how many distinct articles contain each keyword.

 - Outputs results sorted by frequency and lexicographically.

### H) Statistical reports
-> Generates a reports.txt file containing:

1) Number of duplicates found
2) Number of unique articles
3) Most prolific author
4) Most common language
5) Most frequent category
6) Most recent article
7) Most frequent keyword in English articles
