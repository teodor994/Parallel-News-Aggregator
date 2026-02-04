import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Data model representing a single news article.
 * Uses Jackson annotations for JSON mapping.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsItem {
    @JsonProperty("uuid")
    public String articleId;

    @JsonProperty("title")
    public String title;

    @JsonProperty("author")
    public String authorName;

    @JsonProperty("url")
    public String url;

    @JsonProperty("text")
    public String bodyContent;

    @JsonProperty("published")
    public String publishDate;

    @JsonProperty("language")
    public String lang;

    @JsonProperty("categories")
    public List<String> categories;

    /**
     * Processes the body content to extract normalized keywords.
     * Logic: Lowercase -> Split by whitespace -> Keep only alphabetic chars.
     */
    public String[] sanitizeText() {
        if (bodyContent == null || bodyContent.isEmpty()) return new String[0];

        // 1. Convert to lowercase and split by whitespace
        String[] rawTokens = bodyContent.toLowerCase().split("\\s+");
        String[] processedTokens = new String[rawTokens.length];

        for(int i = 0; i < rawTokens.length; i++) {
            // 2. Strip non-alphabetic characters [cite: 98]
            processedTokens[i] = rawTokens[i].replaceAll("[^a-z]", "");
        }
        return processedTokens;
    }
}