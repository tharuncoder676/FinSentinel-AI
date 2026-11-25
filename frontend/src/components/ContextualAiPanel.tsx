import { useState } from 'react';
import { Send, Bot, X, AlertCircle, Sparkles } from 'lucide-react';
import { apiRequest } from '../lib/api';

interface ChatRes {
  conversationId: string;
  liveAiUsed: boolean;
  providerUsed: string;
  unavailableReason?: string;
  content: string;
  toolsInvoked: string[];
  citations: string[];
}

export function ContextualAiPanel({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [prompt, setPrompt] = useState('');
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState<Array<{ role: 'user' | 'assistant'; text: string; meta?: string }>>([]);
  const [error, setError] = useState<string | null>(null);

  if (!open) return null;

  const send = async (textToSend: string) => {
    const clean = textToSend.trim();
    if (!clean || loading) return;
    setError(null);
    setMessages((prev) => [...prev, { role: 'user', text: clean }]);
    setPrompt('');
    setLoading(true);
    try {
      const res = await apiRequest<ChatRes>('/api/v1/ai/chat', {
        method: 'POST',
        body: JSON.stringify({ message: clean }),
      });
      const meta = `${res.liveAiUsed ? 'Live LLM' : 'Deterministic Backend'} • Tools: ${res.toolsInvoked.join(', ')}`;
      setMessages((prev) => [...prev, { role: 'assistant', text: res.content, meta }]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to reach assistant');
    } finally {
      setLoading(false);
    }
  };

  return (
    <aside
      aria-label="Contextual AI Financial Assistant"
      className="fixed right-0 top-0 bottom-0 z-40 w-full sm:w-96 border-l flex flex-col"
      style={{ background: 'var(--surface)', borderColor: 'var(--border)', boxShadow: 'var(--shadow-lg)' }}
    >
      <div className="flex items-center justify-between px-4 py-3 border-b" style={{ borderColor: 'var(--border)' }}>
        <div className="flex items-center gap-2">
          <Bot className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
          <h2 className="text-sm font-semibold m-0">Contextual AI Assistant</h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close contextual AI panel"
          className="p-1.5 rounded-md border cursor-pointer"
          style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
        >
          <X className="w-4 h-4" aria-hidden="true" />
        </button>
      </div>

      <div className="p-3 border-b flex flex-wrap gap-1.5" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
        {[
          'Why did my risk score change?',
          'When could a shortfall occur?',
          'Compare payment schedules',
        ].map((q) => (
          <button
            key={q}
            type="button"
            onClick={() => send(q)}
            className="text-xs px-2.5 py-1 rounded-full border cursor-pointer hover:opacity-85"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--text-primary)' }}
          >
            <Sparkles className="w-3 h-3 inline mr-1" style={{ color: 'var(--accent)' }} aria-hidden="true" />
            {q}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-3" aria-live="polite">
        {messages.length === 0 && (
          <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
            Ask about your risk score, projected cash-flow shortfalls, or upcoming obligations. All figures are grounded in deterministic Java services.
          </p>
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            className={`p-3 rounded-lg text-xs whitespace-pre-wrap ${m.role === 'user' ? 'ml-6' : 'mr-2 border'}`}
            style={{
              background: m.role === 'user' ? 'var(--accent)' : 'var(--elevated)',
              color: m.role === 'user' ? 'var(--accent-text)' : 'var(--text-primary)',
              borderColor: 'var(--border)',
            }}
          >
            {m.text}
            {m.meta && (
              <div className="mt-2 pt-1.5 border-t text-[11px]" style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}>
                {m.meta}
              </div>
            )}
          </div>
        ))}
        {error && (
          <div role="alert" className="p-2.5 rounded-md border text-xs flex items-center gap-2" style={{ borderColor: 'var(--critical)', color: 'var(--critical)' }}>
            <AlertCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(prompt);
        }}
        className="p-3 border-t flex gap-2"
        style={{ borderColor: 'var(--border)' }}
      >
        <label htmlFor="ctx-ai-input" className="visually-hidden">Ask FinSentinel AI</label>
        <input
          id="ctx-ai-input"
          type="text"
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="Ask about risk, cash flow, bills..."
          className="flex-1 px-3 py-2 text-sm rounded-md border"
          style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
        />
        <button
          type="submit"
          disabled={loading}
          aria-label="Send message"
          className="px-3 py-2 rounded-md text-sm font-medium cursor-pointer disabled:opacity-50"
          style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
        >
          <Send className="w-4 h-4" aria-hidden="true" />
        </button>
      </form>
    </aside>
  );
}
