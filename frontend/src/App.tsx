import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AppProvider, useApp } from './context/AppContext';
import { AppShell } from './components/AppShell';
import { LandingPage } from './pages/LandingPage';
import { AuthPage } from './pages/AuthPages';
import { OnboardingPage } from './pages/OnboardingPage';
import { DashboardPage } from './pages/DashboardPage';
import { TransactionsPage } from './pages/TransactionsPage';
import { IncomeExpensesPage } from './pages/IncomeExpensesPage';
import { RiskPage } from './pages/RiskPage';
import { ForecastPage } from './pages/ForecastPage';
import { PaymentPlannerPage } from './pages/PaymentPlannerPage';
import { DigitalTwinPage } from './pages/DigitalTwinPage';
import { StressTestPage } from './pages/StressTestPage';
import { AssistantPage } from './pages/AssistantPage';
import { TimelinePage } from './pages/TimelinePage';
import { GoalsPage } from './pages/GoalsPage';
import { ReportsPage } from './pages/ReportsPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { SettingsPage } from './pages/SettingsPage';
import { HelpPage } from './pages/HelpPage';
import { AdminPage } from './pages/AdminPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: 1,
    },
  },
});

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, authLoading } = useApp();
  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--bg)', color: 'var(--text-secondary)' }}>
        Verifying session...
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/auth/login" replace />;
  }
  return <>{children}</>;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/auth/login" element={<AuthPage mode="login" />} />
      <Route path="/auth/register" element={<AuthPage mode="register" />} />
      <Route path="/auth/forgot-password" element={<AuthPage mode="forgot" />} />
      <Route path="/auth/reset-password" element={<AuthPage mode="reset" />} />
      <Route path="/auth/verify-email" element={<AuthPage mode="verify" />} />

      <Route
        path="/onboarding"
        element={
          <ProtectedRoute>
            <OnboardingPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/app"
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/app/overview" replace />} />
        <Route path="overview" element={<DashboardPage />} />
        <Route path="transactions" element={<TransactionsPage />} />
        <Route path="income-expenses" element={<IncomeExpensesPage />} />
        <Route path="risk" element={<RiskPage />} />
        <Route path="forecast" element={<ForecastPage />} />
        <Route path="payments" element={<PaymentPlannerPage />} />
        <Route path="digital-twin" element={<DigitalTwinPage />} />
        <Route path="stress-test" element={<StressTestPage />} />
        <Route path="assistant" element={<AssistantPage />} />
        <Route path="timeline" element={<TimelinePage />} />
        <Route path="goals" element={<GoalsPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="help" element={<HelpPage />} />
        <Route path="admin" element={<AdminPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AppProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AppProvider>
    </QueryClientProvider>
  );
}
