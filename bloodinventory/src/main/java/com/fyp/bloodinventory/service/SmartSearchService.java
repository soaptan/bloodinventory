package com.fyp.bloodinventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fyp.bloodinventory.dto.SmartSearchResultDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SmartSearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_RESULTS = 8;
    private static final int RERANK_CANDIDATE_LIMIT = 10;
    private static final Set<String> ADMIN = Set.of("ROLE_BLOOD_ADMINISTRATOR");
    private static final Set<String> MEDICAL = Set.of("ROLE_MEDICAL_STAFF");
    private static final Set<String> LAB = Set.of("ROLE_LAB_TECHNICIAN");
    private static final Set<String> ALL_ROLES = Set.of();

    private static final List<SearchDocument> DOCUMENTS = List.of(
            document("Administrator Dashboard", "Review blood bank activity, key metrics, inventory highlights, near expiry alerts, staff totals, donor totals, and quick administration modules.", "/admin/dashboard", "Dashboard", ADMIN, "admin home overview system summary operational highlights staff donors available stock near expiry dashboard"),
            document("Staff Management", "View, register, update, and deactivate staff accounts. Use this page for staff profiles, role assignment, authority access, and personnel records.", "/admin/staff/management", "Administration", ADMIN, "register staff add staff create account update staff edit account delete staff profile employee role blood administrator medical staff lab technician"),
            document("Add Staff", "Open the staff registration form and create a new staff account with role and profile details.", "/admin/staff/management#add", "Administration", ADMIN, "register staff add new staff create staff account staff registration user account role assignment"),
            document("Storage Configuration", "Manage storage locations, capacity, active storage areas, and placement rules for blood components.", "/admin/storage", "Administration", ADMIN, "storage configuration locations capacity add location update location archive storage freezer refrigerator shelf placement"),
            document("Deferral Rules", "Create and maintain donor deferral reasons and eligibility rules used during donor screening.", "/admin/deferral-rules", "Administration", ADMIN, "deferral rules eligibility controls donor deferral add rule update rule archive reason screening"),
            document("Inventory Monitoring", "Monitor blood component status, availability, expiry risk, and stock movement across the inventory.", "/admin/inventory", "Inventory", ADMIN, "inventory monitoring blood component status available stock near expiry expired quarantined discarded reserved used stock level"),
            document("Reports and Alerts", "View reports, warnings, summary exports, near expiry lists, and operational alerts.", "/admin/reports", "Reports", ADMIN, "reports alerts download summary warning near expiry inventory report donor report component report"),
            document("System Settings", "Customize interface preferences, language, backup policy, security settings, and session controls.", "/admin/settings", "Settings", ADMIN, "settings system configuration ui language backup security session control theme accent font password"),
            document("My Profile", "Update your own profile, profile photo, contact details, and password.", "/profile", "Account", ALL_ROLES, "my profile account photo upload avatar contact details change password personal information"),
            document("Medical Dashboard", "Review clinical workflow activity for donor eligibility, donations, transfusions, and component matching.", "/medical/dashboard", "Dashboard", MEDICAL, "medical dashboard clinical overview donor eligibility donation transfusion safe blood match"),
            document("Donor Eligibility", "Create donor records, update donor details, assess eligibility, and record deferrals.", "/medical/donor-eligibility", "Medical", MEDICAL, "donor eligibility add donor update donor delete donor assessment screening deferral clear donor record"),
            document("Blood Collection", "Record donation sessions, collection details, donor donation history, and component preparation workflow.", "/medical/donations", "Medical", MEDICAL, "blood collection donation sessions add donation update donation delete donation component preparation donor collection"),
            document("Transfusion Request", "Record transfusion events, patient information, component use, and clinical request updates.", "/medical/transfusion", "Medical", MEDICAL, "transfusion request patient clinical request add event update event record use reverse record blood use"),
            document("Safe Blood Match", "Find compatible blood components, filter available stock, reserve units, and release units.", "/medical/components", "Medical", MEDICAL, "safe blood match compatibility review find match reserve unit release unit compatible component stock"),
            document("Lab Dashboard", "Review lab workflow status, pending tests, completed screening, safe components, and traceability highlights.", "/lab/dashboard", "Dashboard", LAB, "lab dashboard technician overview pending tests tti screening component status traceability"),
            document("Pending Test Queue", "Review samples awaiting lab screening and move pending donations into TTI screening workflow.", "/lab/tti-screening#queue", "Laboratory", LAB, "pending test queue samples awaiting review tti screening queue approve safe record result"),
            document("TTI Screening", "Record, update, and remove transfusion-transmitted infection screening results.", "/lab/tti-screening", "Laboratory", LAB, "tti screening infection screening add result update result delete result reactive negative lab test"),
            document("Component Status", "View component validation status and release or discard components after lab review.", "/lab/component-status", "Laboratory", LAB, "component status validation progress view components update status release discard safe quarantined"),
            document("Traceability", "Track blood unit movement, audit changes, and review component history across the system.", "/lab/traceability", "Laboratory", LAB, "traceability audit trail tracked units unit movement history component records trace changes")
    );

    private final RestClient embeddingClient;
    private final RestClient rerankClient;
    private final String apiKey;
    private final String embeddingModel;
    private final String rerankUrl;
    private final String rerankModel;
    private volatile List<double[]> documentEmbeddings;

    public SmartSearchService(@Value("${nvidia.nim.base-url:https://integrate.api.nvidia.com}") String baseUrl,
                              @Value("${nvidia.nim.api-key:}") String apiKey,
                              @Value("${nvidia.nim.embedding-model:nvidia/nv-embedqa-e5-v5}") String embeddingModel,
                              @Value("${nvidia.nim.rerank-url:https://ai.api.nvidia.com/v1/retrieval/nvidia/reranking}") String rerankUrl,
                              @Value("${nvidia.nim.rerank-model:nvidia/nv-rerankqa-mistral-4b-v3}") String rerankModel) {
        this.embeddingClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
        this.rerankClient = RestClient.builder().build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.embeddingModel = defaultIfBlank(embeddingModel, "nvidia/nv-embedqa-e5-v5");
        this.rerankUrl = defaultIfBlank(rerankUrl, "https://ai.api.nvidia.com/v1/retrieval/nvidia/reranking");
        this.rerankModel = defaultIfBlank(rerankModel, "nvidia/nv-rerankqa-mistral-4b-v3");
    }

    public List<SmartSearchResultDto> search(String query, Collection<String> authorities) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        List<IndexedDocument> accessibleDocuments = accessibleDocuments(authorities);
        if (accessibleDocuments.isEmpty()) {
            return List.of();
        }

        if (apiKey.isBlank()) {
            return keywordSearch(normalizedQuery, accessibleDocuments);
        }

        try {
            return semanticSearch(normalizedQuery, accessibleDocuments);
        } catch (RestClientException | IllegalStateException ex) {
            return keywordSearch(normalizedQuery, accessibleDocuments);
        }
    }

    private List<SmartSearchResultDto> semanticSearch(String query, List<IndexedDocument> accessibleDocuments) {
        List<double[]> embeddings = documentEmbeddings();
        List<double[]> queryEmbeddings = embed(List.of(query), "query");
        if (queryEmbeddings.isEmpty()) {
            return keywordSearch(query, accessibleDocuments);
        }

        double[] queryEmbedding = queryEmbeddings.get(0);
        List<ScoredDocument> candidates = accessibleDocuments.stream()
                .map(indexed -> new ScoredDocument(
                        indexed.document(),
                        cosineSimilarity(queryEmbedding, embeddings.get(indexed.index())),
                        "Semantic match"
                ))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(RERANK_CANDIDATE_LIMIT)
                .toList();

        if (candidates.isEmpty()) {
            return keywordSearch(query, accessibleDocuments);
        }

        return rerank(query, candidates).stream()
                .limit(MAX_RESULTS)
                .map(this::toDto)
                .toList();
    }

    private List<SmartSearchResultDto> keywordSearch(String query, List<IndexedDocument> accessibleDocuments) {
        return accessibleDocuments.stream()
                .map(indexed -> new ScoredDocument(indexed.document(), keywordScore(query, indexed.document()), "Keyword match"))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(MAX_RESULTS)
                .map(this::toDto)
                .toList();
    }

    private List<ScoredDocument> rerank(String query, List<ScoredDocument> candidates) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", rerankModel);
        payload.put("query", Map.of("text", query));
        payload.put("passages", candidates.stream()
                .map(candidate -> Map.of("text", candidate.document().passage()))
                .toList());
        payload.put("truncate", "END");

        JsonNode response = rerankClient.post()
                .uri(rerankUrl)
                .contentType(jsonMediaType())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        JsonNode rankedItems = firstArray(Objects.requireNonNull(response, "Rerank response must not be null."),
                "rankings", "results", "data");
        if (rankedItems == null) {
            return candidates;
        }

        List<ScoredDocument> ranked = new ArrayList<>();
        Set<Integer> usedIndexes = new HashSet<>();

        for (JsonNode item : rankedItems) {
            int index = firstNonNegativeInt(item, "index", "passage_index", "document_index");
            if (index < 0 || index >= candidates.size() || !usedIndexes.add(index)) {
                continue;
            }

            double score = firstFiniteDouble(item, candidates.get(index).score(),
                    "score", "logit", "relevance_score");
            ranked.add(new ScoredDocument(candidates.get(index).document(), score, "AI ranked"));
        }

        if (ranked.isEmpty()) {
            return candidates;
        }

        ranked.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
        return ranked;
    }

    private List<double[]> documentEmbeddings() {
        List<double[]> cached = documentEmbeddings;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            cached = documentEmbeddings;
            if (cached != null) {
                return cached;
            }

            List<String> passages = DOCUMENTS.stream()
                    .map(SearchDocument::passage)
                    .toList();
            List<double[]> embeddings = embed(passages, "passage");
            if (embeddings.size() != DOCUMENTS.size()) {
                throw new IllegalStateException("Embedding response did not match the search index.");
            }

            documentEmbeddings = embeddings;
            return embeddings;
        }
    }

    private List<double[]> embed(List<String> input, String inputType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", embeddingModel);
        payload.put("input", input);
        payload.put("input_type", inputType);
        payload.put("encoding_format", "float");
        payload.put("truncate", "END");

        JsonNode response = embeddingClient.post()
                .uri("/v1/embeddings")
                .contentType(jsonMediaType())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = Objects.requireNonNull(response, "Embedding response must not be null.").path("data");
        if (!data.isArray()) {
            throw new IllegalStateException("Embedding response did not include data.");
        }

        List<double[]> embeddings = new ArrayList<>();
        for (JsonNode item : data) {
            double[] vector = vectorFrom(item.path("embedding"));
            if (vector.length > 0) {
                embeddings.add(vector);
            }
        }
        return embeddings;
    }

    private List<IndexedDocument> accessibleDocuments(Collection<String> authorities) {
        Set<String> safeAuthorities = authorities == null ? Set.of() : new HashSet<>(authorities);
        List<IndexedDocument> documents = new ArrayList<>();

        for (int index = 0; index < DOCUMENTS.size(); index++) {
            SearchDocument document = DOCUMENTS.get(index);
            if (document.roles().isEmpty() || document.roles().stream().anyMatch(safeAuthorities::contains)) {
                documents.add(new IndexedDocument(index, document));
            }
        }

        return documents;
    }

    private SmartSearchResultDto toDto(ScoredDocument result) {
        SearchDocument document = result.document();
        return new SmartSearchResultDto(
                document.title(),
                document.description(),
                document.url(),
                document.category(),
                Math.max(0, result.score()),
                result.matchType()
        );
    }

    private double keywordScore(String query, SearchDocument document) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<String> terms = tokenize(normalizedQuery);
        double score = 0;

        score += phraseScore(normalizedQuery, document.title(), 12);
        score += phraseScore(normalizedQuery, document.keywords(), 8);
        score += phraseScore(normalizedQuery, document.description(), 5);
        score += phraseScore(normalizedQuery, document.category(), 3);

        for (String term : terms) {
            score += containsScore(term, document.title(), 4);
            score += containsScore(term, document.keywords(), 3);
            score += containsScore(term, document.category(), 2);
            score += containsScore(term, document.description(), 1);
            score += containsScore(term, document.url(), 1);
        }

        return score;
    }

    private double phraseScore(String query, String value, double weight) {
        return value.toLowerCase(Locale.ROOT).contains(query) ? weight : 0;
    }

    private double containsScore(String term, String value, double weight) {
        return value.toLowerCase(Locale.ROOT).contains(term) ? weight : 0;
    }

    private List<String> tokenize(String value) {
        return List.of(value.split("[^a-z0-9]+")).stream()
                .filter(term -> term.length() > 1)
                .toList();
    }

    private double cosineSimilarity(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) {
            return 0;
        }

        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double[] vectorFrom(JsonNode embedding) {
        if (!embedding.isArray()) {
            return new double[0];
        }

        double[] vector = new double[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            vector[index] = embedding.get(index).asDouble();
        }
        return vector;
    }

    private JsonNode firstArray(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private int firstNonNegativeInt(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.canConvertToInt() && value.asInt() >= 0) {
                return value.asInt();
            }
        }
        return -1;
    }

    private double firstFiniteDouble(JsonNode node, double fallback, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber() && Double.isFinite(value.asDouble())) {
                return value.asDouble();
            }
        }
        return fallback;
    }

    private static SearchDocument document(String title,
                                           String description,
                                           String url,
                                           String category,
                                           Set<String> roles,
                                           String keywords) {
        return new SearchDocument(title, description, url, category, roles, keywords);
    }

    private static String normalizeQuery(String query) {
        return query == null ? "" : query.replaceAll("\\s+", " ").trim();
    }

    private static MediaType jsonMediaType() {
        return MediaType.APPLICATION_JSON;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String normalized = defaultIfBlank(value, "https://integrate.api.nvidia.com");

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private record IndexedDocument(int index, SearchDocument document) {
    }

    private record ScoredDocument(SearchDocument document, double score, String matchType) {
    }

    private record SearchDocument(String title,
                                  String description,
                                  String url,
                                  String category,
                                  Set<String> roles,
                                  String keywords) {

        private String passage() {
            return String.join(" ", title, description, category, keywords, url);
        }
    }
}
