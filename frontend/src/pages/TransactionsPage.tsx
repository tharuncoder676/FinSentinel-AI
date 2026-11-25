import React, { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Search, Upload, Download, Trash2, Edit3, AlertTriangle } from 'lucide-react';
import { apiRequest, apiUrl, HttpApiError } from '../lib/api';
import { useApp } from '../context/AppContext';
import { ModalDialog } from '../components/ModalDialog';

interface Account {
  id: string;
  name: string;
  accountType: string;
  currency: string;
  openingBalance: number;
  openingBalanceDate: string;
  currentBalance: number;
}

interface Category {
  id: string;
  name: string;
  kind: string;
  essential: boolean;
}

interface Transaction {
  id: string;
  accountId: string;
  accountName: string;
  transferAccountId: string | null;
  categoryId: string | null;
  categoryName: string;
  txType: string;
  amount: number;
  currency: string;
  occurredOn: string;
  merchant: string | null;
  description: string | null;
  essential: boolean;
  recurring: boolean;
  recurrenceRule: string;
}

interface PagedTx {
  items: Transaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface CsvPreview {
  headers: string[];
  totalRows: number;
  validRows: number;
  duplicateRows: number;
  errorRows: number;
  rows: Array<{
    rowNumber: number;
    date: string;
    txType: string;
    amount: number;
    currency: string;
    merchant: string;
    description: string;
    duplicate: boolean;
    error: string | null;
  }>;
}

export function TransactionsPage() {
  const { formatMoney, notifyToast } = useApp();
  const qc = useQueryClient();
  const [searchParams] = useSearchParams();

  const [search, setSearch] = useState('');
  const [txType, setTxType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [sortBy, setSortBy] = useState('occurredOn');
  const [sortDir, setSortDir] = useState('desc');
  const [page, setPage] = useState(0);

  const [accModalOpen, setAccModalOpen] = useState(false);
  const [txModalOpen, setTxModalOpen] = useState(searchParams.get('new') === '1');
  const [editingTx, setEditingTx] = useState<Transaction | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Transaction | null>(null);
  const [csvModalOpen, setCsvModalOpen] = useState(false);

  // Account form state
  const [accName, setAccName] = useState('');
  const [accType, setAccType] = useState('CHECKING');
  const [accOpenBal, setAccOpenBal] = useState('25000');
  const [accOpenDate, setAccOpenDate] = useState(new Date().toISOString().slice(0, 10));

  // Transaction form state
  const [formAccountId, setFormAccountId] = useState('');
  const [formTransferAccountId, setFormTransferAccountId] = useState('');
  const [formCategoryId, setFormCategoryId] = useState('');
  const [formTxType, setFormTxType] = useState('EXPENSE');
  const [formAmount, setFormAmount] = useState('');
  const [formDate, setFormDate] = useState(new Date().toISOString().slice(0, 10));
  const [formMerchant, setFormMerchant] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formEssential, setFormEssential] = useState(false);
  const [formRecurring, setFormRecurring] = useState(false);
  const [formRule, setFormRule] = useState('NONE');
  const [duplicateConflict, setDuplicateConflict] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // CSV state
  const [csvAccountId, setCsvAccountId] = useState('');
  const [csvPreview, setCsvPreview] = useState<CsvPreview | null>(null);
  const [csvError, setCsvError] = useState<string | null>(null);

  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => apiRequest<Account[]>('/api/v1/accounts'),
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiRequest<Category[]>('/api/v1/categories'),
  });

  const txQueryKey = ['transactions', search, txType, from, to, sortBy, sortDir, page];
  const { data: txPage, isLoading } = useQuery({
    queryKey: txQueryKey,
    queryFn: () => {
      const q = new URLSearchParams();
      if (search) q.set('search', search);
      if (txType) q.set('txType', txType);
      if (from) q.set('from', from);
      if (to) q.set('to', to);
      q.set('sortBy', sortBy);
      q.set('sortDir', sortDir);
      q.set('page', String(page));
      q.set('size', '15');
      return apiRequest<PagedTx>(`/api/v1/transactions?${q.toString()}`);
    },
  });

  const invalidateLedger = () => {
    qc.invalidateQueries({ queryKey: ['accounts'] });
    qc.invalidateQueries({ queryKey: ['transactions'] });
    qc.invalidateQueries({ queryKey: ['dashboard'] });
    qc.invalidateQueries({ queryKey: ['risk'] });
  };

  const createAccountMut = useMutation({
    mutationFn: () =>
      apiRequest('/api/v1/accounts', {
        method: 'POST',
        body: JSON.stringify({
          name: accName,
          accountType: accType,
          currency: 'INR',
          openingBalance: Number(accOpenBal),
          openingBalanceDate: accOpenDate,
        }),
      }),
    onSuccess: () => {
      invalidateLedger();
      setAccModalOpen(false);
      setAccName('');
      notifyToast('Account created.');
    },
  });

  const saveTx = async (allowDuplicate = false) => {
    setFormError(null);
    const targetAcc = formAccountId || accounts[0]?.id;
    if (!targetAcc) {
      setFormError('Please create an account first.');
      return;
    }
    try {
      const payload = {
        accountId: targetAcc,
        transferAccountId: formTxType === 'TRANSFER' ? formTransferAccountId : null,
        categoryId: formCategoryId || null,
        txType: formTxType,
        amount: Number(formAmount),
        occurredOn: formDate,
        merchant: formMerchant,
        description: formDesc,
        essential: formEssential,
        recurring: formRecurring,
        recurrenceRule: formRecurring ? formRule : 'NONE',
        allowDuplicate,
      };
      if (editingTx) {
        await apiRequest(`/api/v1/transactions/${editingTx.id}`, {
          method: 'PUT',
          body: JSON.stringify(payload),
        });
        notifyToast('Transaction updated.');
      } else {
        await apiRequest('/api/v1/transactions', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
        notifyToast('Transaction recorded.');
      }
      invalidateLedger();
      setTxModalOpen(false);
      setEditingTx(null);
      setDuplicateConflict(false);
      setFormAmount('');
      setFormMerchant('');
      setFormDesc('');
    } catch (err) {
      if (err instanceof HttpApiError && err.code === 'POTENTIAL_DUPLICATE') {
        setDuplicateConflict(true);
        setFormError(err.message);
      } else if (err instanceof HttpApiError) {
        setFormError(err.message);
      } else {
        setFormError('Failed to save transaction.');
      }
    }
  };

  const deleteTxMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/transactions/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      invalidateLedger();
      setDeleteTarget(null);
      notifyToast('Transaction deleted.');
    },
  });

  const handleCsvUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setCsvError(null);
    const targetAcc = csvAccountId || accounts[0]?.id;
    if (!targetAcc) {
      setCsvError('Create an account before importing a CSV statement.');
      return;
    }
    const fd = new FormData();
    fd.append('file', file);
    try {
      const preview = await apiRequest<CsvPreview>(`/api/v1/transactions/import/preview?accountId=${targetAcc}`, {
        method: 'POST',
        body: fd,
      });
      setCsvPreview(preview);
    } catch (err) {
      setCsvError(err instanceof Error ? err.message : 'Failed to parse CSV');
    }
  };

  const confirmCsvImport = async () => {
    if (!csvPreview) return;
    const targetAcc = csvAccountId || accounts[0]?.id;
    try {
      const res = await apiRequest<{ importedCount: number; skippedDuplicates: number }>('/api/v1/transactions/import', {
        method: 'POST',
        body: JSON.stringify({ accountId: targetAcc, rows: csvPreview.rows }),
      });
      invalidateLedger();
      setCsvModalOpen(false);
      setCsvPreview(null);
      notifyToast(`Imported ${res.importedCount} rows (${res.skippedDuplicates} duplicates skipped).`);
    } catch (err) {
      setCsvError(err instanceof Error ? err.message : 'Import failed');
    }
  };

  const openEditModal = (tx: Transaction) => {
    setEditingTx(tx);
    setFormAccountId(tx.accountId);
    setFormTransferAccountId(tx.transferAccountId || '');
    setFormCategoryId(tx.categoryId || '');
    setFormTxType(tx.txType);
    setFormAmount(String(tx.amount));
    setFormDate(tx.occurredOn);
    setFormMerchant(tx.merchant || '');
    setFormDesc(tx.description || '');
    setFormEssential(tx.essential);
    setFormRecurring(tx.recurring);
    setFormRule(tx.recurrenceRule || 'NONE');
    setDuplicateConflict(false);
    setFormError(null);
    setTxModalOpen(true);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Accounts & Transactions</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Single-source-of-truth ledger. Transfers between tracked accounts never inflate income or expenses.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setAccModalOpen(true)}
            className="px-3 py-2 rounded-md border text-xs font-medium cursor-pointer"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
          >
            + Add Account
          </button>
          <button
            type="button"
            onClick={() => setCsvModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md border text-xs font-medium cursor-pointer"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
          >
            <Upload className="w-3.5 h-3.5" aria-hidden="true" />
            Import CSV
          </button>
          <a
            href={apiUrl('/api/v1/transactions/export')}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md border text-xs font-medium no-underline"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--text-primary)' }}
          >
            <Download className="w-3.5 h-3.5" aria-hidden="true" />
            Export CSV
          </a>
          <button
            type="button"
            onClick={() => {
              setEditingTx(null);
              setFormAccountId(accounts[0]?.id || '');
              setFormError(null);
              setDuplicateConflict(false);
              setTxModalOpen(true);
            }}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md text-xs font-semibold cursor-pointer"
            style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
          >
            <Plus className="w-3.5 h-3.5" aria-hidden="true" />
            Add Transaction
          </button>
        </div>
      </div>

      {/* Tracked Accounts Strip */}
      <section aria-label="Tracked Accounts" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {accounts.map((acc) => (
          <div key={acc.id} className="surface-card p-4">
            <div className="flex items-center justify-between text-xs" style={{ color: 'var(--text-secondary)' }}>
              <span>{acc.accountType}</span>
              <span>{acc.currency}</span>
            </div>
            <div className="text-sm font-semibold mt-1 truncate">{acc.name}</div>
            <div className="text-lg font-bold font-mono-num mt-1" style={{ color: acc.currentBalance < 0 ? 'var(--critical)' : 'var(--accent)' }}>
              {formatMoney(acc.currentBalance)}
            </div>
            <div className="text-[11px] mt-1" style={{ color: 'var(--text-secondary)' }}>
              Opening {formatMoney(acc.openingBalance)} ({acc.openingBalanceDate})
            </div>
          </div>
        ))}
        {accounts.length === 0 && (
          <div className="surface-card p-4 sm:col-span-2 text-sm" style={{ color: 'var(--text-secondary)' }}>
            No tracked accounts yet. Add an account to begin recording transactions.
          </div>
        )}
      </section>

      {/* Search & Filters */}
      <search>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
          }}
          className="surface-card p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 items-end"
        >
          <div className="lg:col-span-2">
            <label htmlFor="tx-search" className="block text-xs font-semibold mb-1">Search merchant / notes</label>
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--text-secondary)' }} aria-hidden="true" />
              <input
                id="tx-search"
                type="search"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
                placeholder="Search merchant or description..."
                className="w-full pl-9 pr-3 py-2 rounded-md border text-sm"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
              />
            </div>
          </div>
          <div>
            <label htmlFor="tx-type-filter" className="block text-xs font-semibold mb-1">Type</label>
            <select
              id="tx-type-filter"
              value={txType}
              onChange={(e) => {
                setTxType(e.target.value);
                setPage(0);
              }}
              className="w-full px-3 py-2 rounded-md border text-sm"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            >
              <option value="">All types</option>
              <option value="INCOME">Income</option>
              <option value="EXPENSE">Expense</option>
              <option value="TRANSFER">Transfer</option>
              <option value="REFUND">Refund</option>
              <option value="SAVINGS">Savings</option>
            </select>
          </div>
          <div>
            <label htmlFor="tx-from" className="block text-xs font-semibold mb-1">From Date</label>
            <input
              id="tx-from"
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="w-full px-3 py-2 rounded-md border text-sm"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            />
          </div>
          <div>
            <label htmlFor="tx-to" className="block text-xs font-semibold mb-1">To Date</label>
            <input
              id="tx-to"
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              className="w-full px-3 py-2 rounded-md border text-sm"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            />
          </div>
          <div>
            <label htmlFor="tx-sort" className="block text-xs font-semibold mb-1">Sort</label>
            <select
              id="tx-sort"
              value={`${sortBy}:${sortDir}`}
              onChange={(e) => {
                const [b, d] = e.target.value.split(':');
                setSortBy(b);
                setSortDir(d);
              }}
              className="w-full px-3 py-2 rounded-md border text-sm"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            >
              <option value="occurredOn:desc">Newest first</option>
              <option value="occurredOn:asc">Oldest first</option>
              <option value="amount:desc">Highest amount</option>
              <option value="amount:asc">Lowest amount</option>
            </select>
          </div>
        </form>
      </search>

      {/* Transactions Table */}
      <div className="surface-card overflow-hidden">
        <div className="table-wrapper" tabIndex={0} role="region" aria-label="Transactions ledger table">
          <table className="w-full text-sm border-collapse">
            <caption className="visually-hidden">Recorded financial transactions</caption>
            <thead>
              <tr style={{ background: 'var(--elevated)' }}>
                <th scope="col" className="text-left p-3 text-xs font-semibold">Date</th>
                <th scope="col" className="text-left p-3 text-xs font-semibold">Merchant & Description</th>
                <th scope="col" className="text-left p-3 text-xs font-semibold">Account</th>
                <th scope="col" className="text-left p-3 text-xs font-semibold">Category</th>
                <th scope="col" className="text-left p-3 text-xs font-semibold">Type</th>
                <th scope="col" className="text-right p-3 text-xs font-semibold">Amount</th>
                <th scope="col" className="text-right p-3 text-xs font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr>
                  <td colSpan={7} className="p-6 text-center text-sm" style={{ color: 'var(--text-secondary)' }}>Loading transactions...</td>
                </tr>
              )}
              {!isLoading && (txPage?.items.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={7} className="p-8 text-center text-sm" style={{ color: 'var(--text-secondary)' }}>
                    No transactions match the current filter.
                  </td>
                </tr>
              )}
              {txPage?.items.map((tx) => {
                const isCredit = tx.txType === 'INCOME' || tx.txType === 'REFUND';
                return (
                  <tr key={tx.id} className="border-t hover:opacity-95" style={{ borderColor: 'var(--border)' }}>
                    <td className="p-3 font-mono-num text-xs whitespace-nowrap">{tx.occurredOn}</td>
                    <td className="p-3">
                      <div className="font-medium text-sm">{tx.merchant || tx.txType}</div>
                      {tx.description && <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>{tx.description}</div>}
                    </td>
                    <td className="p-3 text-xs">{tx.accountName}</td>
                    <td className="p-3 text-xs">{tx.categoryName}</td>
                    <td className="p-3 text-xs">
                      <span className="px-2 py-0.5 rounded border text-[11px] font-medium" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                        {tx.txType}
                      </span>
                    </td>
                    <td className="p-3 text-right font-mono-num font-semibold whitespace-nowrap" style={{ color: isCredit ? 'var(--accent)' : 'var(--text-primary)' }}>
                      {isCredit ? '+' : tx.txType === 'TRANSFER' ? '⇄ ' : '-'}{formatMoney(tx.amount)}
                    </td>
                    <td className="p-3 text-right whitespace-nowrap">
                      <button
                        type="button"
                        onClick={() => openEditModal(tx)}
                        aria-label={`Edit transaction ${tx.merchant || tx.occurredOn}`}
                        className="p-1.5 rounded border mr-1.5 cursor-pointer"
                        style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                      >
                        <Edit3 className="w-3.5 h-3.5" aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeleteTarget(tx)}
                        aria-label={`Delete transaction ${tx.merchant || tx.occurredOn}`}
                        className="p-1.5 rounded border cursor-pointer"
                        style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--critical)' }}
                      >
                        <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {txPage && txPage.totalPages > 1 && (
          <div className="p-3 border-t flex items-center justify-between text-xs" style={{ borderColor: 'var(--border)' }}>
            <span>Page {txPage.page + 1} of {txPage.totalPages} ({txPage.totalElements} records)</span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1 rounded border cursor-pointer disabled:opacity-40"
                style={{ borderColor: 'var(--border)' }}
              >
                Previous
              </button>
              <button
                type="button"
                disabled={page + 1 >= txPage.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1 rounded border cursor-pointer disabled:opacity-40"
                style={{ borderColor: 'var(--border)' }}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Add Account Modal */}
      <ModalDialog open={accModalOpen} onClose={() => setAccModalOpen(false)} title="Create Tracked Account">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createAccountMut.mutate();
          }}
          className="space-y-3"
        >
          <div>
            <label htmlFor="acc-name" className="block text-xs font-semibold mb-1">Account Name</label>
            <input id="acc-name" required value={accName} onChange={(e) => setAccName(e.target.value)} placeholder="e.g. HDFC Primary Salary" className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="acc-type" className="block text-xs font-semibold mb-1">Account Type</label>
              <select id="acc-type" value={accType} onChange={(e) => setAccType(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="CHECKING">Checking</option>
                <option value="SAVINGS">Savings</option>
                <option value="CASH">Cash</option>
                <option value="CREDIT_CARD">Credit Card</option>
              </select>
            </div>
            <div>
              <label htmlFor="acc-bal" className="block text-xs font-semibold mb-1">Opening Balance</label>
              <input id="acc-bal" type="number" step="0.01" required value={accOpenBal} onChange={(e) => setAccOpenBal(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>
          <div>
            <label htmlFor="acc-date" className="block text-xs font-semibold mb-1">Opening Balance Date</label>
            <input id="acc-date" type="date" required value={accOpenDate} onChange={(e) => setAccOpenDate(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setAccModalOpen(false)} className="px-3 py-2 rounded-md border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Cancel</button>
            <button type="submit" className="px-4 py-2 rounded-md text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>Save Account</button>
          </div>
        </form>
      </ModalDialog>

      {/* Add/Edit Transaction Modal */}
      <ModalDialog open={txModalOpen} onClose={() => setTxModalOpen(false)} title={editingTx ? 'Edit Transaction' : 'Add Transaction'}>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            saveTx(false);
          }}
          className="space-y-3"
        >
          {formError && (
            <div role="alert" className="p-2.5 rounded border text-xs flex items-start gap-2" style={{ borderColor: 'var(--warning)', color: 'var(--warning)' }}>
              <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
              <div>
                <p className="m-0">{formError}</p>
                {duplicateConflict && (
                  <button
                    type="button"
                    onClick={() => saveTx(true)}
                    className="mt-1.5 underline font-semibold cursor-pointer"
                  >
                    Record anyway (not a duplicate)
                  </button>
                )}
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="tx-form-type" className="block text-xs font-semibold mb-1">Transaction Type</label>
              <select id="tx-form-type" value={formTxType} onChange={(e) => setFormTxType(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="EXPENSE">Expense (Debit)</option>
                <option value="INCOME">Income (Credit)</option>
                <option value="TRANSFER">Account Transfer</option>
                <option value="REFUND">Refund (Credit)</option>
                <option value="SAVINGS">Savings Deposit</option>
              </select>
            </div>
            <div>
              <label htmlFor="tx-form-acc" className="block text-xs font-semibold mb-1">Account</label>
              <select id="tx-form-acc" value={formAccountId || accounts[0]?.id || ''} onChange={(e) => setFormAccountId(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </div>
          </div>

          {formTxType === 'TRANSFER' && (
            <div>
              <label htmlFor="tx-form-dest" className="block text-xs font-semibold mb-1">Destination Account</label>
              <select id="tx-form-dest" required value={formTransferAccountId} onChange={(e) => setFormTransferAccountId(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="">Select destination</option>
                {accounts.filter((a) => a.id !== (formAccountId || accounts[0]?.id)).map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="tx-form-amt" className="block text-xs font-semibold mb-1">Amount</label>
              <input id="tx-form-amt" type="number" step="0.01" min="0.01" required value={formAmount} onChange={(e) => setFormAmount(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="tx-form-date" className="block text-xs font-semibold mb-1">Date</label>
              <input id="tx-form-date" type="date" required value={formDate} onChange={(e) => setFormDate(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="tx-form-cat" className="block text-xs font-semibold mb-1">Category</label>
              <select id="tx-form-cat" value={formCategoryId} onChange={(e) => setFormCategoryId(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="">Auto / Uncategorized</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="tx-form-merch" className="block text-xs font-semibold mb-1">Merchant / Payee</label>
              <input id="tx-form-merch" value={formMerchant} onChange={(e) => setFormMerchant(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>

          <div>
            <label htmlFor="tx-form-desc" className="block text-xs font-semibold mb-1">Description</label>
            <input id="tx-form-desc" value={formDesc} onChange={(e) => setFormDesc(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>

          <div className="flex flex-wrap gap-4 text-xs pt-1">
            <label className="inline-flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={formEssential} onChange={(e) => setFormEssential(e.target.checked)} />
              Essential expenditure
            </label>
            <label className="inline-flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={formRecurring} onChange={(e) => setFormRecurring(e.target.checked)} />
              Recurring template
            </label>
            {formRecurring && (
              <select aria-label="Recurrence rule" value={formRule} onChange={(e) => setFormRule(e.target.value)} className="px-2 py-1 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="MONTHLY">Monthly</option>
                <option value="WEEKLY">Weekly</option>
                <option value="BIWEEKLY">Bi-weekly</option>
                <option value="QUARTERLY">Quarterly</option>
              </select>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setTxModalOpen(false)} className="px-3 py-2 rounded-md border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Cancel</button>
            <button type="submit" className="px-4 py-2 rounded-md text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
              {editingTx ? 'Update Transaction' : 'Save Transaction'}
            </button>
          </div>
        </form>
      </ModalDialog>

      {/* Delete Confirmation Modal */}
      <ModalDialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} title="Confirm Transaction Deletion">
        <div className="space-y-4">
          <p className="text-sm m-0">
            Delete transaction <strong>{deleteTarget?.merchant || deleteTarget?.txType}</strong> for <strong className="font-mono-num">{formatMoney(deleteTarget?.amount)}</strong>? Account balances and risk metrics will recalculate immediately.
          </p>
          <div className="flex justify-end gap-2">
            <button type="button" autoFocus onClick={() => setDeleteTarget(null)} className="px-3 py-2 rounded-md border text-xs font-medium cursor-pointer" style={{ borderColor: 'var(--border)' }}>
              Cancel
            </button>
            <button
              type="button"
              onClick={() => deleteTarget && deleteTxMut.mutate(deleteTarget.id)}
              className="px-4 py-2 rounded-md text-xs font-semibold cursor-pointer"
              style={{ background: 'var(--critical)', color: '#fff' }}
            >
              Delete Permanently
            </button>
          </div>
        </div>
      </ModalDialog>

      {/* CSV Import Modal */}
      <ModalDialog open={csvModalOpen} onClose={() => setCsvModalOpen(false)} title="Import CSV Statement">
        <div className="space-y-4">
          <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>
            Expected columns: <code>date (YYYY-MM-DD), type, amount, currency, merchant, description</code>. Duplicate rows and invalid formats are flagged before import.
          </p>
          <div>
            <label htmlFor="csv-acc" className="block text-xs font-semibold mb-1">Target Account</label>
            <select id="csv-acc" value={csvAccountId || accounts[0]?.id || ''} onChange={(e) => setCsvAccountId(e.target.value)} className="w-full px-3 py-2 rounded-md border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="csv-file" className="block text-xs font-semibold mb-1">Select CSV File (Max 2 MB)</label>
            <input id="csv-file" type="file" accept=".csv,text/csv" onChange={handleCsvUpload} className="text-xs" />
          </div>
          {csvError && <div role="alert" className="text-xs" style={{ color: 'var(--critical)' }}>{csvError}</div>}
          {csvPreview && (
            <div className="space-y-2">
              <div className="text-xs flex gap-4 font-mono-num">
                <span>Total: {csvPreview.totalRows}</span>
                <span style={{ color: 'var(--accent)' }}>Valid: {csvPreview.validRows}</span>
                <span style={{ color: 'var(--warning)' }}>Duplicates: {csvPreview.duplicateRows}</span>
                <span style={{ color: 'var(--critical)' }}>Errors: {csvPreview.errorRows}</span>
              </div>
              <div className="max-h-48 overflow-y-auto border rounded text-xs" style={{ borderColor: 'var(--border)' }}>
                <table className="w-full border-collapse">
                  <thead>
                    <tr style={{ background: 'var(--elevated)' }}>
                      <th className="p-1.5 text-left">Row</th>
                      <th className="p-1.5 text-left">Date</th>
                      <th className="p-1.5 text-left">Merchant</th>
                      <th className="p-1.5 text-right">Amount</th>
                      <th className="p-1.5 text-left">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {csvPreview.rows.slice(0, 30).map((r) => (
                      <tr key={r.rowNumber} className="border-t" style={{ borderColor: 'var(--border)' }}>
                        <td className="p-1.5 font-mono-num">{r.rowNumber}</td>
                        <td className="p-1.5 font-mono-num">{r.date || '—'}</td>
                        <td className="p-1.5">{r.merchant}</td>
                        <td className="p-1.5 text-right font-mono-num">{r.amount ?? '—'}</td>
                        <td className="p-1.5">
                          {r.error ? <span style={{ color: 'var(--critical)' }}>{r.error}</span> : r.duplicate ? 'Duplicate' : 'Ready'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setCsvPreview(null)} className="px-3 py-1.5 rounded border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Clear</button>
                <button
                  type="button"
                  disabled={csvPreview.validRows === 0}
                  onClick={confirmCsvImport}
                  className="px-4 py-1.5 rounded text-xs font-semibold cursor-pointer disabled:opacity-40"
                  style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
                >
                  Confirm Import ({csvPreview.validRows} rows)
                </button>
              </div>
            </div>
          )}
        </div>
      </ModalDialog>
    </div>
  );
}
