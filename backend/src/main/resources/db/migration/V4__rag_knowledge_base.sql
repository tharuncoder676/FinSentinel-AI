-- RAG Knowledge & Financial Skills Schema (Supabase pgvector compatible + PostgreSQL FTS fallback)

CREATE TABLE knowledge_documents (
    id UUID PRIMARY KEY,
    owner_user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(64) NOT NULL,
    scope VARCHAR(24) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_knowledge_doc_scope CHECK (scope IN ('SYSTEM', 'USER'))
);

CREATE UNIQUE INDEX uk_knowledge_docs_system_slug
    ON knowledge_documents (slug)
    WHERE scope = 'SYSTEM';

CREATE INDEX idx_knowledge_docs_scope_owner
    ON knowledge_documents (scope, owner_user_id, updated_at DESC);

CREATE TABLE knowledge_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    owner_user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    scope VARCHAR(24) NOT NULL,
    chunk_index INT NOT NULL,
    heading VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    embedding_json TEXT NOT NULL,
    embedding_provider VARCHAR(32) NOT NULL DEFAULT 'local-hybrid',
    search_tsv tsvector GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(heading, '') || ' ' || content)
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_knowledge_chunk_scope CHECK (scope IN ('SYSTEM', 'USER'))
);

CREATE INDEX idx_knowledge_chunks_doc_idx
    ON knowledge_chunks (document_id, chunk_index);

CREATE INDEX idx_knowledge_chunks_scope_owner
    ON knowledge_chunks (scope, owner_user_id);

CREATE INDEX idx_knowledge_chunks_tsv
    ON knowledge_chunks USING GIN (search_tsv);

-- Enable pgvector HNSW index automatically on Supabase / pgvector-enabled PostgreSQL hosts
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS vector;
            ALTER TABLE knowledge_chunks ADD COLUMN IF NOT EXISTS embedding vector(1024);
            CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_vec_hnsw
                ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)
                WHERE embedding IS NOT NULL;
        EXCEPTION WHEN OTHERS THEN
            -- Gracefully ignore if pgvector extension lives in restricted schema; FTS + JSON cosine remains active
            NULL;
        END;
    END IF;
END $$;

-- Supabase Row Level Security compliance for public tables
ALTER TABLE knowledge_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_chunks ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE POLICY "rag_docs_select_system_or_own" ON knowledge_documents
            FOR SELECT TO authenticated
            USING (scope = 'SYSTEM' OR (select auth.uid()) = owner_user_id);

        CREATE POLICY "rag_docs_insert_own" ON knowledge_documents
            FOR INSERT TO authenticated
            WITH CHECK (scope = 'USER' AND (select auth.uid()) = owner_user_id);

        CREATE POLICY "rag_docs_update_own" ON knowledge_documents
            FOR UPDATE TO authenticated
            USING (scope = 'USER' AND (select auth.uid()) = owner_user_id)
            WITH CHECK (scope = 'USER' AND (select auth.uid()) = owner_user_id);

        CREATE POLICY "rag_docs_delete_own" ON knowledge_documents
            FOR DELETE TO authenticated
            USING (scope = 'USER' AND (select auth.uid()) = owner_user_id);

        CREATE POLICY "rag_chunks_select_system_or_own" ON knowledge_chunks
            FOR SELECT TO authenticated
            USING (scope = 'SYSTEM' OR (select auth.uid()) = owner_user_id);

        CREATE POLICY "rag_chunks_insert_own" ON knowledge_chunks
            FOR INSERT TO authenticated
            WITH CHECK (scope = 'USER' AND (select auth.uid()) = owner_user_id);

        CREATE POLICY "rag_chunks_delete_own" ON knowledge_chunks
            FOR DELETE TO authenticated
            USING (scope = 'USER' AND (select auth.uid()) = owner_user_id);
    END IF;
END $$;
