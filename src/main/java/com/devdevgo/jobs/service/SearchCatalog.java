package com.devdevgo.jobs.service;

import com.devdevgo.jobs.model.SearchProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class SearchCatalog {

    private final List<SearchProfile> profiles = List.of(
            new SearchProfile("software-fresher", "software engineer fresher", "in", "fresher"),
            new SearchProfile("software-intern", "software engineer intern", "in", "intern"),
            new SearchProfile("backend-fresher", "backend developer fresher", "in", "backend"),
            new SearchProfile("backend-intern", "backend developer intern", "in", "backend"),
            new SearchProfile("frontend-fresher", "frontend developer fresher", "in", "frontend"),
            new SearchProfile("frontend-intern", "frontend developer intern", "in", "frontend"),
            new SearchProfile("web-fresher", "web developer fresher", "in", "web"),
            new SearchProfile("web-intern", "web developer intern", "in", "web"),
            new SearchProfile("fullstack-fresher", "full stack developer fresher", "in", "fullstack"),
            new SearchProfile("fullstack-intern", "full stack developer intern", "in", "fullstack"),
            new SearchProfile("java-fresher", "java developer fresher", "in", "java"),
            new SearchProfile("java-intern", "java developer intern", "in", "java"),
            new SearchProfile("python-fresher", "python developer fresher", "in", "python"),
            new SearchProfile("python-intern", "python developer intern", "in", "python"),
            new SearchProfile("data-analyst-intern", "data analyst intern", "in", "data-analyst"),
            new SearchProfile("data-analyst-fresher", "data analyst fresher", "in", "data-analyst"),
            new SearchProfile("data-science-intern", "data science intern", "in", "data-science"),
            new SearchProfile("data-science-fresher", "data science fresher", "in", "data-science"),
            new SearchProfile("ml-intern", "machine learning intern", "in", "machine-learning"),
            new SearchProfile("ml-fresher", "machine learning fresher", "in", "machine-learning"),
            new SearchProfile("ai-intern", "ai engineer intern", "in", "ai"),
            new SearchProfile("ai-fresher", "ai engineer fresher", "in", "ai"),
            new SearchProfile("qa-intern", "qa tester intern", "in", "qa"),
            new SearchProfile("qa-fresher", "qa tester fresher", "in", "qa"),
            new SearchProfile("automation-intern", "automation testing intern", "in", "automation"),
            new SearchProfile("devops-intern", "devops intern", "in", "devops"),
            new SearchProfile("cloud-intern", "cloud engineer intern", "in", "cloud"),
            new SearchProfile("security-intern", "cybersecurity intern", "in", "security"),
            new SearchProfile("android-intern", "android developer intern", "in", "android"),
            new SearchProfile("android-fresher", "android developer fresher", "in", "android"),
            new SearchProfile("ios-intern", "ios developer intern", "in", "ios"),
            new SearchProfile("flutter-intern", "flutter developer intern", "in", "flutter"),
            new SearchProfile("react-intern", "react developer intern", "in", "react"),
            new SearchProfile("uiux-intern", "ui ux designer intern", "in", "ui-ux"),
            new SearchProfile("database-intern", "database developer intern", "in", "database"),
            new SearchProfile("embedded-intern", "embedded engineer intern", "in", "embedded"),
            new SearchProfile("data-engineer-intern", "data engineer intern", "in", "data-engineering"),
            new SearchProfile("campus-hiring", "campus hiring software engineer", "in", "campus")
    );

    public List<SearchProfile> all() {
        return profiles;
    }

    public int size() {
        return profiles.size();
    }

    public List<SearchProfile> selectBatch(int startIndex, int batchSize) {
        if (profiles.isEmpty() || batchSize <= 0) {
            return List.of();
        }

        int size = profiles.size();
        int effectiveBatch = Math.min(batchSize, size);
        int start = Math.floorMod(startIndex, size);

        return IntStream.range(0, effectiveBatch)
                .mapToObj(i -> profiles.get((start + i) % size))
                .toList();
    }
}
