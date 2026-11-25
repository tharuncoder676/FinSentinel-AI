import React, { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bot, Send, Square, Copy, RotateCw, Trash2, Sparkles, ChevronDown, ChevronUp, Info, BookOpen, Plus } from 'lucide-react';
import { apiRequest, apiUrl } from '../lib/api';
import { useApp } from '../context/AppContext';
import { ModalDialog } from '../components/ModalDialog';

interface AiStatus {
  liveAiConfigured: boolean;
  activeProvider: string;
  activeModel: string;
  statusMessage: string;
}

interface RagStatus {
  enabled: boolean;
  totalDocuments: number;
  totalChunks: number;
  pgvectorAvailable: boolean;
  embeddingMode: string;
}

interface RagDocument {
  id: string;
  slug: string;
  title: string;
  category: string;
  scope: 'SYSTEM' | 'USER';
  sourceType: string;
  chunkCount: number;
  preview: string;
  updatedAt: string;
}

interface RagChunkSource {
  documentId: string;
  documentTitle: string;
  category: string;
  scope: string;
  chunkIndex: number;
  heading: string;
  content: string;
  score: number;
  retrievalMode: string;
  citation: string;
}

interface ConversationSummary {
  id: string;
  title: string;
  providerUsed: string;
  updatedAt: string;
}

interface MessageItem {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations?: string[];
  groundedCalculations?: Array<{ toolName: string; citation: string; data: unknown }>;
  knowledgeSources?: RagChunkSource[];
  liveAiUsed?: boolean;
  unavailableReason?: string;
}

export function AssistantPage() {
  const { notifyToast } = useApp();
  const qc = useQueryClient();

  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [activeRunId, setActiveRunId] = useState<string | null>(null);
  const [expandedCalc, setExpandedCalc] = useState<string | null>(null);
  const [skillsOpen, setSkillsOpen] = useState(false);
  const [skillTitle, setSkillTitle] = useState('');
  const [skillCategory, setSkillCategory] = useState('CUSTOM_SKILL');
  const [skillContent, setSkillContent] = useState('');
  const abortRef = useRef<AbortController | null>(null);

  const { data: status } = useQuery({
    queryKey: ['ai-status'],
    queryFn: () => apiRequest<AiStatus>('/api/v1/ai/status'),
  });

  const { data: ragStatus } = useQuery({
    queryKey: ['rag-status'],
    queryFn: () => apiRequest<RagStatus>('/api/v1/rag/status'),
  });

  const { data: ragDocs = [] } = useQuery({
    queryKey: ['rag-documents'],
    queryFn: () => apiRequest<RagDocument[]>('/api/v1/rag/documents'),
    enabled: skillsOpen,
  });

  const ingestSkillMut = useMutation({
    mutationFn: () =>
      apiRequest<RagDocument>('/api/v1/rag/documents', {
        method: 'POST',
        body: JSON.stringify({
          title: skillTitle.trim(),
          category: skillCategory,
          content: skillContent.trim(),
          scope: 'USER',
        }),
      }),
    onSuccess: (doc) => {
      qc.invalidateQueries({ queryKey: ['rag-documents'] });
      qc.invalidateQueries({ queryKey: ['rag-status'] });
      setSkillTitle('');
      setSkillContent('');
      notifyToast(`Indexed "${doc.title}" (${doc.chunkCount} RAG chunks).`);
    },
    onError: (err: Error) => {
      notifyToast(err.message || 'Unable to index skill document.');
    },
  });

  const deleteSkillMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/rag/documents/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rag-documents'] });
      qc.invalidateQueries({ queryKey: ['rag-status'] });
      notifyToast('Knowledge document deleted.');
    },
  });

  const { data: conversations = [] } = useQuery({
    queryKey: ['ai-conversations'],
    queryFn: () => apiRequest<ConversationSummary[]>('/api/v1/ai/conversations'),
  });

  const loadConversation = async (id: string) => {
    setActiveConvId(id);
    const msgs = await apiRequest<Array<{ id: string; role: 'USER' | 'ASSISTANT'; content: string; citationsJson?: string }>>(`/api/v1/ai/conversations/${id}/messages`);
    setMessages(
      msgs.map((m) => ({
        id: m.id,
        role: m.role,
        content: m.content,
        citations: m.citationsJson ? JSON.parse(m.citationsJson) : [],
      }))
    );
  };

  const deleteConvMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/ai/conversations/${id}`, { method: 'DELETE' }),
    onSuccess: (_d, id) => {
      qc.invalidateQueries({ queryKey: ['ai-conversations'] });
      if (activeConvId === id) {
        setActiveConvId(null);
        setMessages([]);
      }
      notifyToast('Conversation deleted.');
    },
  });

  const sendMessage = async (textOverride?: string) => {
    const prompt = (textOverride ?? input).trim();
    if (!prompt || streaming) return;
    setInput('');
    const userMsgId = 'u-' + Date.now();
    const assistantId = 'a-' + Date.now();
    setMessages((prev) => [...prev, { id: userMsgId, role: 'USER', content: prompt }, { id: assistantId, role: 'ASSISTANT', content: '' }]);
    setStreaming(true);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const csrfMatch = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrfMatch) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrfMatch[1]);

      const res = await fetch(apiUrl('/api/v1/ai/chat/stream'), {
        method: 'POST',
        credentials: 'include',
        headers,
        body: JSON.stringify({ conversationId: activeConvId, message: prompt }),
        signal: controller.signal,
      });

      if (!res.ok || !res.body) {
        const err = await res.json().catch(() => ({ message: 'AI request failed' }));
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, content: `Error: ${err.message}` } : m)));
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let eventName = 'message';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const rawLine of lines) {
          const line = rawLine.trimEnd();
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const payloadStr = line.slice(5).trim();
            if (!payloadStr) continue;
            const data = JSON.parse(payloadStr);
            if (eventName === 'meta') {
              setActiveRunId(data.runId);
              if (data.conversationId) setActiveConvId(data.conversationId);
              setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, citations: data.citations } : m)));
            } else if (eventName === 'delta') {
              setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + (data.text || '') } : m)));
            } else if (eventName === 'done') {
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId
                    ? {
                        ...m,
                        liveAiUsed: data.liveAiUsed,
                        unavailableReason: data.unavailableReason,
                        groundedCalculations: data.groundedCalculations,
                        knowledgeSources: data.knowledgeSources,
                      }
                    : m
                )
              );
              qc.invalidateQueries({ queryKey: ['ai-conversations'] });
            } else if (eventName === 'error') {
              setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, content: `Error: ${data.message}` } : m)));
            }
          }
        }
      }
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, content: 'Stream interrupted.' } : m)));
      }
    } finally {
      setStreaming(false);
      setActiveRunId(null);
    }
  };

  const stopStream = async () => {
    if (activeRunId) {
      await apiRequest(`/api/v1/ai/runs/${activeRunId}/cancel`, { method: 'POST' }).catch(() => {});
    }
    abortRef.current?.abort();
    setStreaming(false);
  };

  const regenerateLast = () => {
    const lastUser = [...messages].reverse().find((m) => m.role === 'USER');
    if (lastUser) sendMessage(lastUser.content);
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 min-h-[75vh]">
      {/* Conversation Sidebar */}
      <aside className="lg:col-span-3 surface-card p-4 flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold m-0">Conversations</h2>
          <button
            type="button"
            onClick={() => {
              setActiveConvId(null);
              setMessages([]);
            }}
            className="text-xs px-2.5 py-1 rounded border cursor-pointer"
            style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
          >
            New Chat
          </button>
        </div>

        <ul className="space-y-1.5 flex-1 overflow-y-auto list-none p-0 m-0">
          {conversations.map((c) => (
            <li key={c.id} className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => loadConversation(c.id)}
                className="flex-1 text-left px-2.5 py-2 rounded text-xs truncate cursor-pointer border"
                style={{
                  borderColor: activeConvId === c.id ? 'var(--accent)' : 'var(--border)',
                  background: activeConvId === c.id ? 'var(--elevated)' : 'transparent',
                }}
              >
                {c.title}
              </button>
              <button
                type="button"
                aria-label={`Delete conversation ${c.title}`}
                onClick={() => deleteConvMut.mutate(c.id)}
                className="p-1.5 rounded border cursor-pointer"
                style={{ borderColor: 'var(--border)', color: 'var(--critical)' }}
              >
                <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
              </button>
            </li>
          ))}
          {conversations.length === 0 && (
            <li className="text-xs py-4" style={{ color: 'var(--text-secondary)' }}>No conversation history yet.</li>
          )}
        </ul>
      </aside>

      {/* Main Conversational Workspace */}
      <section className="lg:col-span-9 surface-card flex flex-col overflow-hidden">
        <div className="p-4 border-b flex flex-wrap items-center justify-between gap-2" style={{ borderColor: 'var(--border)' }}>
          <div className="flex items-center gap-2">
            <Bot className="w-5 h-5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
            <div>
              <h1 className="text-base font-bold m-0">AI Financial Intelligence Assistant</h1>
              <p className="text-[11px] m-0" style={{ color: 'var(--text-secondary)' }}>
                {status?.statusMessage || 'Checking backend provider configuration...'}
                {ragStatus ? ` • RAG Skills: ${ragStatus.totalDocuments} docs / ${ragStatus.totalChunks} chunks (${ragStatus.embeddingMode})` : ''}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => setSkillsOpen(true)}
              className="inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded border cursor-pointer font-medium"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
            >
              <BookOpen className="w-3.5 h-3.5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              Knowledge Skills (RAG)
            </button>
            {!status?.liveAiConfigured && (
              <span className="inline-flex items-center gap-1 text-[11px] px-2.5 py-1 rounded border" style={{ borderColor: 'var(--warning)', color: 'var(--warning)' }}>
                <Info className="w-3.5 h-3.5" aria-hidden="true" />
                Deterministic + RAG Fallback
              </span>
            )}
          </div>
        </div>

        {/* Suggested Prompts */}
        <div className="px-4 py-2.5 border-b flex flex-wrap gap-2" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
          {[
            'Why did my risk score change and what drives it?',
            'What is the 50/30/20 budget rule and how many emergency months should I keep?',
            'Compare debt Avalanche vs Snowball for my upcoming obligations.',
          ].map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => sendMessage(q)}
              className="text-xs px-3 py-1 rounded-full border cursor-pointer hover:opacity-85"
              style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
            >
              <Sparkles className="w-3 h-3 inline mr-1" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              {q}
            </button>
          ))}
        </div>

        {/* Messages Stream */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4" aria-live="polite">
          {messages.length === 0 && (
            <div className="py-12 text-center max-w-lg mx-auto space-y-2">
              <p className="text-sm font-medium m-0">Ask grounded questions about your finances</p>
              <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>
                Responses cite backend tool calls (`getFinancialSummary`, `getRiskAssessment`, `getCashFlowForecast`, `comparePaymentScenarios`, `runFinancialStressTest`) and never fabricate uncomputed numbers.
              </p>
            </div>
          )}

          {messages.map((m) => (
            <div key={m.id} className={`flex flex-col ${m.role === 'USER' ? 'items-end' : 'items-start'}`}>
              <div
                className="max-w-3xl rounded-lg p-3.5 text-sm whitespace-pre-wrap border"
                style={{
                  background: m.role === 'USER' ? 'var(--accent)' : 'var(--elevated)',
                  color: m.role === 'USER' ? 'var(--accent-text)' : 'var(--text-primary)',
                  borderColor: 'var(--border)',
                }}
              >
                {m.content || (streaming ? 'Computing verified response...' : '')}
              </div>

              {m.role === 'ASSISTANT' && m.content && (
                <div className="max-w-3xl w-full mt-1.5 space-y-1.5">
                  <div className="flex flex-wrap items-center gap-2 text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard.writeText(m.content);
                        notifyToast('Copied response.');
                      }}
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded border cursor-pointer"
                      style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
                    >
                      <Copy className="w-3 h-3" aria-hidden="true" /> Copy
                    </button>
                    <button
                      type="button"
                      onClick={regenerateLast}
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded border cursor-pointer"
                      style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
                    >
                      <RotateCw className="w-3 h-3" aria-hidden="true" /> Regenerate
                    </button>
                    {m.citations && m.citations.length > 0 && (
                      <span>Sources: {m.citations.join(' • ')}</span>
                    )}
                  </div>

                  {m.knowledgeSources && m.knowledgeSources.length > 0 && (
                    <div className="border rounded-md p-2.5 space-y-1.5" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                      <p className="text-[11px] font-semibold m-0" style={{ color: 'var(--text-secondary)' }}>
                        RAG Knowledge Skills Cited ({m.knowledgeSources.length})
                      </p>
                      <ul className="space-y-1 list-none p-0 m-0 text-xs">
                        {m.knowledgeSources.map((ks) => (
                          <li key={`${ks.documentId}-${ks.chunkIndex}`} className="border-t pt-1" style={{ borderColor: 'var(--border)' }}>
                            <span className="font-semibold">{ks.documentTitle}</span> — <span>{ks.heading}</span>{' '}
                            <span className="text-[11px] font-mono-num" style={{ color: 'var(--text-secondary)' }}>
                              ({ks.scope} • score {ks.score})
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {m.groundedCalculations && m.groundedCalculations.length > 0 && (
                    <div className="border rounded-md" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                      <button
                        type="button"
                        onClick={() => setExpandedCalc(expandedCalc === m.id ? null : m.id)}
                        className="w-full px-3 py-1.5 text-left text-xs font-medium flex items-center justify-between cursor-pointer"
                      >
                        <span>Inspect Backend-Grounded Calculations ({m.groundedCalculations.length} tools)</span>
                        {expandedCalc === m.id ? <ChevronUp className="w-3.5 h-3.5" aria-hidden="true" /> : <ChevronDown className="w-3.5 h-3.5" aria-hidden="true" />}
                      </button>
                      {expandedCalc === m.id && (
                        <pre className="p-3 text-[11px] overflow-x-auto m-0 border-t font-mono-num" style={{ borderColor: 'var(--border)' }}>
                          <code>{JSON.stringify(m.groundedCalculations, null, 2)}</code>
                        </pre>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Input Form */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sendMessage();
          }}
          className="p-3 border-t flex gap-2"
          style={{ borderColor: 'var(--border)' }}
        >
          <label htmlFor="ai-chat-input" className="visually-hidden">Message AI Financial Assistant</label>
          <input
            id="ai-chat-input"
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
                // handled by form submit cleanly
              }
            }}
            placeholder="Ask about risk drivers, cash shortfalls, payment schedules, or stress scenarios..."
            className="flex-1 px-3 py-2.5 rounded-md border text-sm"
            style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
          />
          {streaming ? (
            <button
              type="button"
              onClick={stopStream}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-md text-xs font-semibold cursor-pointer"
              style={{ background: 'var(--critical)', color: '#fff' }}
            >
              <Square className="w-3.5 h-3.5" aria-hidden="true" />
              Stop
            </button>
          ) : (
            <button
              type="submit"
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-md text-xs font-semibold cursor-pointer"
              style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
            >
              <Send className="w-3.5 h-3.5" aria-hidden="true" />
              Send
            </button>
          )}
        </form>
      </section>

      <ModalDialog open={skillsOpen} onClose={() => setSkillsOpen(false)} title="Financial Intelligence Skills (RAG)">
        <div className="space-y-4">
          {ragStatus && (
            <div className="p-3 rounded-md border text-xs space-y-1" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <p className="m-0 font-semibold">
                Hybrid Engine: {ragStatus.embeddingMode} {ragStatus.pgvectorAvailable ? '• pgvector HNSW active' : '• PostgreSQL FTS + cosine vectors'}
              </p>
              <p className="m-0" style={{ color: 'var(--text-secondary)' }}>
                {ragStatus.totalDocuments} indexed documents across {ragStatus.totalChunks} searchable skill chunks.
              </p>
            </div>
          )}

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!skillTitle.trim() || !skillContent.trim() || ingestSkillMut.isPending) return;
              ingestSkillMut.mutate();
            }}
            className="space-y-2.5 border rounded-md p-3"
            style={{ borderColor: 'var(--border)' }}
          >
            <p className="text-xs font-semibold m-0 flex items-center gap-1.5">
              <Plus className="w-3.5 h-3.5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              Add Custom Skill / Domain Knowledge
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              <div className="sm:col-span-2">
                <label htmlFor="rag-skill-title" className="block text-[11px] font-medium mb-1">Skill Title</label>
                <input
                  id="rag-skill-title"
                  type="text"
                  required
                  value={skillTitle}
                  onChange={(e) => setSkillTitle(e.target.value)}
                  placeholder="e.g. Monthly Tax Reserve Rules"
                  className="w-full px-2.5 py-1.5 rounded border text-xs"
                  style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                />
              </div>
              <div>
                <label htmlFor="rag-skill-category" className="block text-[11px] font-medium mb-1">Category</label>
                <select
                  id="rag-skill-category"
                  value={skillCategory}
                  onChange={(e) => setSkillCategory(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded border text-xs"
                  style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                >
                  <option value="CUSTOM_SKILL">Custom Skill</option>
                  <option value="PERSONAL_FINANCE">Personal Finance</option>
                  <option value="DEBT_PLANNING">Debt Planning</option>
                  <option value="PLATFORM_GUIDE">Workspace Note</option>
                </select>
              </div>
            </div>
            <div>
              <label htmlFor="rag-skill-content" className="block text-[11px] font-medium mb-1">Markdown / Domain Playbook Content</label>
              <textarea
                id="rag-skill-content"
                rows={4}
                required
                value={skillContent}
                onChange={(e) => setSkillContent(e.target.value)}
                placeholder="Paste rules, definitions, financial playbooks, or Q&A guidance for the assistant to retrieve automatically..."
                className="w-full px-2.5 py-1.5 rounded border text-xs"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
              />
            </div>
            <button
              type="submit"
              disabled={ingestSkillMut.isPending}
              className="px-3 py-1.5 rounded text-xs font-semibold cursor-pointer"
              style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
            >
              {ingestSkillMut.isPending ? 'Indexing Chunks...' : 'Index Knowledge Skill'}
            </button>
          </form>

          <div className="space-y-2 max-h-64 overflow-y-auto">
            {ragDocs.map((doc) => (
              <div key={doc.id} className="p-2.5 rounded border text-xs flex items-start justify-between gap-2" style={{ borderColor: 'var(--border)' }}>
                <div className="min-w-0">
                  <p className="font-semibold m-0 truncate">{doc.title}</p>
                  <p className="text-[11px] m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
                    {doc.scope} • {doc.category} • {doc.chunkCount} chunks
                  </p>
                  <p className="text-[11px] m-0 mt-1" style={{ color: 'var(--text-secondary)' }}>{doc.preview}</p>
                </div>
                {doc.scope === 'USER' && (
                  <button
                    type="button"
                    aria-label={`Delete knowledge ${doc.title}`}
                    onClick={() => deleteSkillMut.mutate(doc.id)}
                    className="p-1 rounded border cursor-pointer shrink-0"
                    style={{ borderColor: 'var(--border)', color: 'var(--critical)' }}
                  >
                    <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      </ModalDialog>
    </div>
  );
}
