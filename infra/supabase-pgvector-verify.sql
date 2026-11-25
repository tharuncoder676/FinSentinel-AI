-- Run in Supabase SQL Editor after deploying FinSentinel backend (optional verification)

-- 1. Ensure pgvector extension is enabled on Supabase
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Verify pgvector column + HNSW index exist on knowledge_chunks
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'knowledge_chunks'
  AND column_name IN ('embedding', 'search_tsv');

-- 3. Verify Row Level Security (RLS) is enabled on RAG tables
SELECT tablename, rowsecurity
FROM pg_tables
WHERE schemaname = 'public'
  AND tablename IN ('knowledge_documents', 'knowledge_chunks');

-- 4. Inspect seeded Financial Intelligence Skill Packs
SELECT slug, title, category, scope, chunk_count
FROM knowledge_documents
ORDER BY scope, slug;
