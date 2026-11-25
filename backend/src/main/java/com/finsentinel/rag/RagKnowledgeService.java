package com.finsentinel.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.ai.AiProviderFactory;
import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.config.AppProperties;
import com.finsentinel.common.security.UserSessionPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class RagKnowledgeService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeService.class);
    private static final int LOCAL_VECTOR_DIM = 256;
    private static final int CHUNK_TARGET_CHARS = 700;
    private static final int MAX_CHUNKS_PER_DOC = 60;

    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?previous\\s+instructions|system\\s+prompt|reveal\\s+.*api[_\\s]?key|dump\\s+all\\s+users|bypass\\s+authorization)"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "are", "was", "were",
            "what", "when", "where", "which", "how", "can", "you", "your", "into", "about",
            "have", "has", "will", "not", "but", "all", "any", "use", "does", "should"
    );

    private final KnowledgeDocumentRepository documentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AiProviderFactory aiProviderFactory;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private volatile Boolean cachedPgvectorAvailable;

    public RagKnowledgeService(KnowledgeDocumentRepository documentRepository,
                               JdbcTemplate jdbcTemplate,
                               AiProviderFactory aiProviderFactory,
                               AuditService auditService,
                               AppProperties appProperties,
                               ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.aiProviderFactory = aiProviderFactory;
        this.auditService = auditService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureSystemSkillsSeeded();
        } catch (Exception ex) {
            log.warn("RAG system skill seeding skipped or deferred: {}", ex.getMessage());
        }
    }

    @Transactional
    public void ensureSystemSkillsSeeded() {
        List<SystemSkillSeed> skills = builtInSkills();
        for (SystemSkillSeed seed : skills) {
            String hash = sha256Hex(seed.title() + "|" + seed.category() + "|" + seed.content());
            Optional<KnowledgeDocumentEntity> existing = documentRepository.findBySlugAndScope(seed.slug(), "SYSTEM");
            if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
                continue;
            }
            upsertDocumentInternal(
                    existing.map(KnowledgeDocumentEntity::getId).orElse(UUID.randomUUID()),
                    null,
                    seed.slug(),
                    seed.title(),
                    seed.category(),
                    "SYSTEM",
                    "BUILTIN_SKILL",
                    seed.content(),
                    hash
            );
        }
    }

    @Transactional(readOnly = true)
    public RagDtos.RagStatusDto getStatus() {
        long docs = documentRepository.count();
        Long chunks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_chunks", Long.class);
        boolean pgvec = isPgvectorAvailable();
        String embMode = hasText(appProperties.ai().nvidiaApiKey())
                ? "nvidia-nim+postgres-fts"
                : (hasText(appProperties.ai().openaiApiKey()) ? "openai+postgres-fts" : "local-hybrid+postgres-fts");
        return new RagDtos.RagStatusDto(appProperties.ai().ragEnabled(), docs, chunks != null ? chunks : 0L, pgvec, embMode);
    }

    @Transactional(readOnly = true)
    public List<RagDtos.KnowledgeDocumentDto> listDocuments(UUID userId) {
        return documentRepository.findAccessibleDocuments(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public RagDtos.KnowledgeDocumentDto ingestDocument(UserSessionPrincipal principal, RagDtos.IngestDocumentRequest req) {
        String title = req.title().trim();
        String category = hasText(req.category()) ? req.category().trim().toUpperCase() : "CUSTOM_SKILL";
        String content = req.content().trim();
        validateContentSafety(content);

        String requestedScope = hasText(req.scope()) ? req.scope().trim().toUpperCase() : "USER";
        if ("SYSTEM".equals(requestedScope) && !"ADMIN".equalsIgnoreCase(principal.role())) {
            throw ApiException.forbidden("Only administrators can publish SYSTEM-wide skill documents.");
        }
        String scope = "SYSTEM".equals(requestedScope) ? "SYSTEM" : "USER";
        UUID ownerId = "SYSTEM".equals(scope) ? null : principal.userId();

        String slug = toSlug(title) + "-" + Long.toHexString(System.currentTimeMillis() & 0xfffffL);
        String hash = sha256Hex(title + "|" + category + "|" + content);

        KnowledgeDocumentEntity saved = upsertDocumentInternal(
                UUID.randomUUID(),
                ownerId,
                slug,
                title,
                category,
                scope,
                "USER".equals(scope) ? "USER_NOTE" : "BUILTIN_SKILL",
                content,
                hash
        );

        auditService.record(principal.userId(), principal.role(), "RAG_DOCUMENT_INGESTED", "KNOWLEDGE_DOCUMENT",
                saved.getId().toString(), "SUCCESS", Map.of("scope", scope, "chunks", saved.getChunkCount()));

        return toDto(saved);
    }

    @Transactional
    public void deleteDocument(UserSessionPrincipal principal, UUID documentId) {
        KnowledgeDocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("Knowledge document not found"));

        if ("SYSTEM".equals(doc.getScope())) {
            if (!"ADMIN".equalsIgnoreCase(principal.role())) {
                throw ApiException.forbidden("Only administrators can remove SYSTEM skill documents.");
            }
        } else if (!principal.userId().equals(doc.getOwnerUserId())) {
            throw ApiException.notFound("Knowledge document not found");
        }

        documentRepository.delete(doc);
        auditService.record(principal.userId(), principal.role(), "RAG_DOCUMENT_DELETED", "KNOWLEDGE_DOCUMENT",
                documentId.toString(), "SUCCESS", Map.of("scope", doc.getScope()));
    }

    @Transactional(readOnly = true)
    public RagDtos.QueryResponse query(UUID userId, RagDtos.QueryRequest req) {
        int topK = req.topK() != null ? Math.min(10, Math.max(1, req.topK())) : 4;
        List<RagDtos.RetrievedChunkDto> results = retrieve(userId, req.query(), topK);
        String mode = results.isEmpty() ? "none" : results.get(0).retrievalMode();
        return new RagDtos.QueryResponse(req.query(), mode, results);
    }

    @Transactional(readOnly = true)
    public List<RagDtos.RetrievedChunkDto> retrieve(UUID userId, String queryText, int topK) {
        if (!appProperties.ai().ragEnabled() || queryText == null || queryText.isBlank()) {
            return List.of();
        }
        int k = Math.min(10, Math.max(1, topK));
        String cleanedQuery = queryText.trim();

        // Check if query has dense neural embedding available
        double[] neuralQueryVec = null;
        String retrievalMode = "hybrid-fts+local-vector";
        Optional<List<double[]>> maybeQueryEmb = aiProviderFactory.embedTexts(List.of(cleanedQuery), "query");
        if (maybeQueryEmb.isPresent() && !maybeQueryEmb.get().isEmpty()) {
            neuralQueryVec = maybeQueryEmb.get().get(0);
            retrievalMode = isPgvectorAvailable() ? "pgvector+postgres-fts" : "nvidia-vector+postgres-fts";
        }
        double[] localQueryVec = localSemanticEmbedding(cleanedQuery);
        Set<String> queryTerms = tokenizeKeywords(cleanedQuery);

        String sql = """
                SELECT c.id, c.document_id, d.title, d.category, c.scope, c.chunk_index, c.heading, c.content,
                       c.embedding_json, c.embedding_provider,
                       ts_rank_cd(c.search_tsv, websearch_to_tsquery('english', ?)) AS fts_rank
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                WHERE (c.scope = 'SYSTEM' OR c.owner_user_id = ?)
                """;

        List<ChunkCandidate> candidates = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ChunkCandidate(
                        UUID.fromString(rs.getString("document_id")),
                        rs.getString("title"),
                        rs.getString("category"),
                        rs.getString("scope"),
                        rs.getInt("chunk_index"),
                        rs.getString("heading"),
                        rs.getString("content"),
                        rs.getString("embedding_json"),
                        rs.getString("embedding_provider"),
                        rs.getDouble("fts_rank")
                ),
                cleanedQuery,
                userId
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        double maxFts = candidates.stream().mapToDouble(ChunkCandidate::ftsRank).max().orElse(0.0);
        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());

        for (ChunkCandidate c : candidates) {
            double normFts = maxFts > 0 ? Math.min(1.0, c.ftsRank() / maxFts) : 0.0;
            double[] storedVec = parseVector(c.embeddingJson());
            double vecScore;
            if (neuralQueryVec != null && !"local-hybrid".equals(c.embeddingProvider()) && storedVec.length == neuralQueryVec.length) {
                vecScore = Math.max(0.0, cosineSimilarity(neuralQueryVec, storedVec));
            } else {
                double[] localDocVec = storedVec.length == LOCAL_VECTOR_DIM ? storedVec : localSemanticEmbedding(c.heading() + " " + c.content());
                vecScore = Math.max(0.0, cosineSimilarity(localQueryVec, localDocVec));
            }
            double lexicalOverlap = keywordOverlapScore(queryTerms, c.heading(), c.category(), c.content());
            double userBoost = "USER".equals(c.scope()) && lexicalOverlap > 0.15 ? 0.08 : 0.0;

            double combined = (0.45 * normFts) + (0.40 * vecScore) + (0.15 * lexicalOverlap) + userBoost;
            if (combined >= 0.08) {
                scored.add(new ScoredCandidate(c, Math.round(combined * 1000.0) / 1000.0));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());
        List<RagDtos.RetrievedChunkDto> out = new ArrayList<>();
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            ScoredCandidate sc = scored.get(i);
            ChunkCandidate c = sc.candidate();
            String citation = "RAG:" + c.documentTitle() + " [" + c.heading() + "]";
            out.add(new RagDtos.RetrievedChunkDto(
                    c.documentId(),
                    c.documentTitle(),
                    c.category(),
                    c.scope(),
                    c.chunkIndex(),
                    c.heading(),
                    c.content(),
                    sc.score(),
                    retrievalMode,
                    citation
            ));
        }
        return out;
    }

    private KnowledgeDocumentEntity upsertDocumentInternal(UUID docId,
                                                         UUID ownerUserId,
                                                         String slug,
                                                         String title,
                                                         String category,
                                                         String scope,
                                                         String sourceType,
                                                         String content,
                                                         String contentHash) {
        List<ChunkSlice> slices = chunkMarkdownContent(title, content);
        Instant now = Instant.now();

        KnowledgeDocumentEntity doc = documentRepository.findById(docId).orElseGet(KnowledgeDocumentEntity::new);
        boolean isNew = doc.getId() == null;
        doc.setId(docId);
        doc.setOwnerUserId(ownerUserId);
        doc.setSlug(slug);
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setScope(scope);
        doc.setSourceType(sourceType);
        doc.setContent(content);
        doc.setContentHash(contentHash);
        doc.setChunkCount(slices.size());
        if (isNew) {
            doc.setCreatedAt(now);
        }
        doc.setUpdatedAt(now);
        KnowledgeDocumentEntity saved = documentRepository.saveAndFlush(doc);

        jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE document_id = ?", saved.getId());

        List<String> chunkInputs = slices.stream()
                .map(s -> title + " - " + s.heading() + "\n" + s.content())
                .toList();

        Optional<List<double[]>> maybeNeural = Optional.empty();
        String providerTag = "local-hybrid";
        if (!"BUILTIN_SKILL".equals(sourceType) || hasText(appProperties.ai().nvidiaApiKey()) || hasText(appProperties.ai().openaiApiKey())) {
            maybeNeural = aiProviderFactory.embedTexts(chunkInputs, "passage");
            if (maybeNeural.isPresent()) {
                providerTag = hasText(appProperties.ai().nvidiaApiKey()) ? "nvidia-nim" : "openai";
            }
        }

        boolean pgvecCol = isPgvectorAvailable();
        for (int i = 0; i < slices.size(); i++) {
            ChunkSlice slice = slices.get(i);
            double[] vec = maybeNeural.isPresent()
                    ? maybeNeural.get().get(i)
                    : localSemanticEmbedding(title + " " + slice.heading() + " " + slice.content());
            String vecJson = serializeVector(vec);
            UUID chunkId = UUID.randomUUID();

            if (pgvecCol && maybeNeural.isPresent() && vec.length == 1024) {
                jdbcTemplate.update(
                        "INSERT INTO knowledge_chunks (id, document_id, owner_user_id, scope, chunk_index, heading, content, embedding_json, embedding_provider, embedding, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)",
                        chunkId, saved.getId(), ownerUserId, scope, i, slice.heading(), slice.content(),
                        vecJson, providerTag, toPgvectorLiteral(vec), Timestamp.from(now)
                );
            } else {
                jdbcTemplate.update(
                        "INSERT INTO knowledge_chunks (id, document_id, owner_user_id, scope, chunk_index, heading, content, embedding_json, embedding_provider, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        chunkId, saved.getId(), ownerUserId, scope, i, slice.heading(), slice.content(),
                        vecJson, providerTag, Timestamp.from(now)
                );
            }
        }

        return saved;
    }

    private boolean isPgvectorAvailable() {
        Boolean cached = cachedPgvectorAvailable;
        if (cached != null) {
            return cached;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'knowledge_chunks' AND column_name = 'embedding'",
                    Integer.class
            );
            boolean present = count != null && count > 0;
            cachedPgvectorAvailable = present;
            return present;
        } catch (Exception ex) {
            cachedPgvectorAvailable = false;
            return false;
        }
    }

    private List<ChunkSlice> chunkMarkdownContent(String defaultHeading, String content) {
        List<ChunkSlice> chunks = new ArrayList<>();
        String currentHeading = defaultHeading;
        StringBuilder buffer = new StringBuilder();

        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("## ") || line.startsWith("### ") || line.startsWith("# ")) {
                if (buffer.length() > 40) {
                    emitSizedChunks(chunks, currentHeading, buffer.toString().trim());
                    buffer.setLength(0);
                }
                currentHeading = line.replaceFirst("^#+\\s*", "").trim();
                if (currentHeading.length() > 190) {
                    currentHeading = currentHeading.substring(0, 190);
                }
            } else if (line.isEmpty() && buffer.length() >= CHUNK_TARGET_CHARS) {
                emitSizedChunks(chunks, currentHeading, buffer.toString().trim());
                buffer.setLength(0);
            } else if (!line.isEmpty()) {
                if (!buffer.isEmpty()) buffer.append('\n');
                buffer.append(line);
            }
            if (chunks.size() >= MAX_CHUNKS_PER_DOC) break;
        }

        if (!buffer.isEmpty() && chunks.size() < MAX_CHUNKS_PER_DOC) {
            emitSizedChunks(chunks, currentHeading, buffer.toString().trim());
        }

        if (chunks.isEmpty()) {
            chunks.add(new ChunkSlice(defaultHeading, content.length() > 1200 ? content.substring(0, 1200) : content));
        }
        return chunks;
    }

    private void emitSizedChunks(List<ChunkSlice> target, String heading, String block) {
        if (block.length() <= CHUNK_TARGET_CHARS + 150) {
            target.add(new ChunkSlice(heading, block));
            return;
        }
        int start = 0;
        while (start < block.length() && target.size() < MAX_CHUNKS_PER_DOC) {
            int end = Math.min(block.length(), start + CHUNK_TARGET_CHARS);
            if (end < block.length()) {
                int split = block.lastIndexOf(' ', end);
                if (split > start + 250) {
                    end = split;
                }
            }
            target.add(new ChunkSlice(heading, block.substring(start, end).trim()));
            if (end >= block.length()) break;
            start = Math.max(end - 100, start + 200);
        }
    }

    private double[] localSemanticEmbedding(String text) {
        double[] vec = new double[LOCAL_VECTOR_DIM];
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (norm.isEmpty()) {
            return vec;
        }
        String[] words = norm.split(" ");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.length() >= 2 && !STOP_WORDS.contains(w)) {
                addHashedFeature(vec, "w:" + w, 1.5);
                if (w.length() >= 4) {
                    for (int c = 0; c <= w.length() - 3; c++) {
                        addHashedFeature(vec, "g:" + w.substring(c, c + 3), 0.5);
                    }
                }
            }
            if (i + 1 < words.length) {
                addHashedFeature(vec, "b:" + w + "_" + words[i + 1], 1.2);
            }
        }
        double mag = 0.0;
        for (double v : vec) mag += v * v;
        if (mag > 0) {
            double inv = 1.0 / Math.sqrt(mag);
            for (int i = 0; i < vec.length; i++) vec[i] *= inv;
        }
        return vec;
    }

    private static void addHashedFeature(double[] vec, String feature, double weight) {
        int h = feature.hashCode();
        int idx = Math.floorMod(h, LOCAL_VECTOR_DIM);
        double sign = ((h >>> 31) == 0) ? 1.0 : -1.0;
        vec[idx] += sign * weight;
    }

    private static double cosineSimilarity(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static Set<String> tokenizeKeywords(String text) {
        Set<String> terms = new HashSet<>();
        for (String tok : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (tok.length() >= 3 && !STOP_WORDS.contains(tok)) {
                terms.add(tok);
            }
        }
        return terms;
    }

    private static double keywordOverlapScore(Set<String> queryTerms, String heading, String category, String content) {
        if (queryTerms.isEmpty()) return 0.0;
        String haystack = (heading + " " + category + " " + content).toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : queryTerms) {
            if (haystack.contains(term)) matched++;
        }
        return (double) matched / queryTerms.size();
    }

    private String serializeVector(double[] vec) {
        try {
            return objectMapper.writeValueAsString(vec);
        } catch (Exception e) {
            return "[]";
        }
    }

    private double[] parseVector(String json) {
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception e) {
            return new double[0];
        }
    }

    private static String toPgvectorLiteral(double[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%.6f", vec[i]));
        }
        return sb.append(']').toString();
    }

    private void validateContentSafety(String content) {
        if (INJECTION_PATTERN.matcher(content).find()) {
            throw ApiException.badRequest("KNOWLEDGE_SAFETY_BLOCKED",
                    "Document rejected: embedded prompt-injection or credential-exfiltration pattern detected.");
        }
    }

    private RagDtos.KnowledgeDocumentDto toDto(KnowledgeDocumentEntity e) {
        String preview = e.getContent().replaceAll("\\s+", " ").trim();
        if (preview.length() > 160) preview = preview.substring(0, 157) + "...";
        return new RagDtos.KnowledgeDocumentDto(
                e.getId(), e.getSlug(), e.getTitle(), e.getCategory(), e.getScope(),
                e.getSourceType(), e.getChunkCount(), preview, e.getUpdatedAt()
        );
    }

    private static String toSlug(String s) {
        String clean = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return clean.isEmpty() ? "skill-doc" : (clean.length() > 80 ? clean.substring(0, 80) : clean);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private record ChunkSlice(String heading, String content) {}
    private record ChunkCandidate(UUID documentId, String documentTitle, String category, String scope,
                                  int chunkIndex, String heading, String content, String embeddingJson,
                                  String embeddingProvider, double ftsRank) {}
    private record ScoredCandidate(ChunkCandidate candidate, double score) {}
    private record SystemSkillSeed(String slug, String title, String category, String content) {}

    private List<SystemSkillSeed> builtInSkills() {
        return List.of(
                new SystemSkillSeed(
                        "skill-risk-scoring",
                        "FinSentinel Explainable Risk Score Skill (v1-deterministic)",
                        "RISK_POLICY",
                        """
                        ## Weighted Risk Pillars
                        FinSentinel computes an explainable 0-100 risk score using five deterministic financial health pillars under policy version v1-deterministic:
                        1. Liquidity & Emergency Buffer (30% weight): Compares total liquid account balances against essential monthly fixed expenses. Below 1.0 month of coverage elevates liquidity risk sharply; 3.0+ months produces low liquidity risk.
                        2. Debt & Obligation Load (25% weight): Evaluates total recurring debt EMIs and scheduled obligations relative to monthly recurring income. Ratios above 45% indicate elevated debt pressure; below 25% is healthy.
                        3. Cash-Flow Volatility (20% weight): Measures day-to-day income and spending instability using the coefficient of variation across recent cash flows.
                        4. Upcoming Payment Coverage (15% weight): Checks upcoming scheduled obligations over the next 30 days against projected available balance.
                        5. Discretionary Spend Ratio (10% weight): Tracks non-essential spending relative to total outflows or income.
                        ## Risk Bands & Data Completeness
                        Score bands: 0-24 LOW, 25-49 MODERATE, 50-74 ELEVATED, 75-100 CRITICAL.
                        When certain transaction categories or accounts are incomplete, FinSentinel explicitly computes a data completeness percentage and excludes missing metrics rather than fabricating values.
                        """
                ),
                new SystemSkillSeed(
                        "skill-cashflow-forecasting",
                        "Cash-Flow Forecasting & Liquidity Runway Skill",
                        "FORECASTING",
                        """
                        ## Event-Driven 30/60/90 Day Projection
                        FinSentinel projects daily running balances across 30, 60, and 90-day horizons using DeterministicEventForecastStrategy:
                        - Starting Cash Baseline: Sum of active liquid checking, savings, and wallet accounts.
                        - Scheduled Inflows: Recurring salaries, freelance retainers, or stipend dates mapped deterministically onto future dates.
                        - Scheduled Outflows: Pending rent, loan EMIs, utility bills, and subscriptions deducted on their due dates.
                        - Daily Variable Baseline: Estimated everyday spending derived from trailing transaction history.
                        ## Shortfall Detection & Recovery
                        If projected balance dips below zero on any day, the earliest date is flagged as shortageDate alongside the minimum projected balance (minProjectedBalance) and recovery trajectory. Users can mitigate projected shortfalls by deferring discretionary expenses, re-sequencing optional payments, or building an automatic buffer before high-outflow weeks.
                        """
                ),
                new SystemSkillSeed(
                        "skill-emi-debt-strategy",
                        "Debt Payoff, EMI & Obligation Strategy Skill",
                        "DEBT_PLANNING",
                        """
                        ## Comparing Payoff Strategies
                        FinSentinel evaluates debt repayment options across four structured methods:
                        - Avalanche Strategy: Allocates extra monthly repayment capacity to the obligation with the highest annual interest rate (APR) first, minimizing total interest paid mathematically.
                        - Snowball Strategy: Allocates extra funds to the smallest remaining balance first, creating fast psychological wins and freeing monthly minimum cash flow early.
                        - Baseline / Minimums Only: Pays scheduled minimums across all loans to preserve short-term liquidity when cash-flow risk is elevated.
                        - Proportional Split: Distributes surplus payment capacity across obligations proportionally to outstanding principal.
                        ## EMI Formula & DTI Benchmarks
                        Monthly EMI = P * r * (1 + r)^n / ((1 + r)^n - 1), where P is principal, r is monthly rate (annualRate / 1200), and n is tenure in months.
                        Educational benchmark: Keep total monthly EMIs under 35% of net monthly income; exceeding 45-50% reduces shock resilience during salary delays.
                        """
                ),
                new SystemSkillSeed(
                        "skill-emergency-fund-resilience",
                        "Emergency Buffer & Stress-Testing Playbook",
                        "STRESS_TESTING",
                        """
                        ## Resilience Against Income Delays & Shocks
                        FinSentinel's Digital Twin and Stress Test Matrix test household resilience without modifying live ledger balances:
                        - Salary / Income Delay Shock: Simulates 7, 15, or 30-day income delays to expose bills due before salary credits.
                        - Unexpected Expense Shock: Models sudden medical repairs, family emergencies, or vehicle breakdowns (e.g., INR 25,000-50,000).
                        - Expense Inflation & Rate Hike: Measures sensitivity to 10-20% rising living costs or floating loan rate increases.
                        ## Building an Emergency Runway
                        A foundational resilience rule targets 3 to 6 months of core living obligations (housing, groceries, utilities, EMIs, insurance) held in instant-access liquid savings before committing aggressively to locked or illiquid investments.
                        """
                ),
                new SystemSkillSeed(
                        "skill-budgeting-household",
                        "Household Budgeting, Savings Rate & Cash-Flow Hygiene",
                        "PERSONAL_FINANCE",
                        """
                        ## The 50/30/20 Cash-Flow Framework
                        A widely used educational budgeting baseline divides net monthly take-home income into:
                        - 50% Needs: Rent/housing, groceries, utilities, healthcare, minimum debt EMIs, essential transport.
                        - 30% Wants / Discretionary: Dining out, entertainment, shopping, discretionary travel upgrades.
                        - 20% Savings & Debt Prepayment: Emergency buffer top-ups, goal sinking funds, and extra high-APR debt payoff.
                        ## Practical Cash-Flow Habits
                        - Pay Yourself First: Schedule recurring transfers toward emergency or goal buckets right after salary credit.
                        - Sinking Funds for Irregular Bills: Divide annual insurance premiums, festival expenses, or tuition fees by 12 so irregular spikes do not trigger negative shortfalls.
                        - Category & Duplicate Audits: Use FinSentinel's CSV import preview and transaction tags to spot subscription creep early.
                        """
                ),
                new SystemSkillSeed(
                        "skill-investment-guardrails",
                        "General Financial Education & Neutrality Guardrails",
                        "FINANCIAL_LITERACY",
                        """
                        ## Compounding, Inflation & Diversification Basics
                        - Compound Growth: Time horizon and consistent contributions amplify returns exponentially, while high-interest consumer debt compounds negatively against net worth.
                        - Real Return vs Nominal Return: Purchasing power grows only by return minus inflation; emergency reserves prioritize safety and instant liquidity over yield.
                        - Diversification & Horizon Matching: Short-term goals (under 2 years) favor high-liquidity capital-preserving accounts; multi-year goals require evaluating risk tolerance and liquidity buffer first.
                        ## Educational Non-Advisory Boundary
                        FinSentinel AI provides quantitative cash-flow explanations and educational literacy only. It does not offer personalized regulated investment advice, securities recommendations, tax filings, or credit approval guarantees.
                        """
                ),
                new SystemSkillSeed(
                        "skill-platform-guide",
                        "FinSentinel AI Platform & Deployment Architecture Guide",
                        "PLATFORM_GUIDE",
                        """
                        ## Navigating FinSentinel Workspaces
                        - Dashboard & Onboarding: Overview of liquid balance, monthly income vs spending, risk score badge, and interactive charts in English or Tamil with light/dark theme support.
                        - Ledger & CSV Import: Multi-account transaction management, filtering, CSV preview with duplicate detection, and CSV export.
                        - Risk Anatomy & Forecast Timeline: Transparent breakdown of every risk pillar and 30/60/90-day daily balance trajectories.
                        - Digital Twin & Stress Matrix: What-if sandbox and preset shock matrix with PDF report export.
                        - Payment Planner & Goals: Due obligations, Avalanche vs Snowball comparison, EMI calculator, and goal contribution tracking.
                        - AI Assistant & Knowledge Skills (RAG): Hybrid Retrieval-Augmented Generation combining PostgreSQL full-text + vector similarity over built-in and custom financial skill docs with NVIDIA NIM / Mistral / OpenAI / Gemini / Anthropic or offline deterministic analysis.
                        """
                ),
                new SystemSkillSeed(
                        "skill-security-privacy",
                        "Security, Privacy & Grounded AI Safety Architecture",
                        "SECURITY_PRIVACY",
                        """
                        ## Zero-Leakage & Access Control Guarantees
                        - HttpOnly Session & CSRF Protection: Authentication uses HttpOnly cookies plus X-XSRF-TOKEN CSRF defense; LLM API keys remain strictly server-side environment variables and are never exposed to the React client.
                        - Strict Tenant Isolation: Every ledger query, simulation, conversation, and custom RAG knowledge document is scoped strictly by the authenticated principal's userId.
                        - Prompt Injection & Hallucination Defense: User prompts and ingested documents are screened for instruction-override or key-extraction attempts, wrapped in untrusted_data delimiters, and grounded against deterministic Java backend calculations plus cited RAG skill chunks.
                        """
                )
        );
    }
}
