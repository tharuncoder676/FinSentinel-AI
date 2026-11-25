import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AppProvider } from '../context/AppContext';
import { LandingPage } from '../pages/LandingPage';

describe('FinSentinel AI Frontend Core Components', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: false, status: 401, headers: new Headers() })) as unknown as typeof fetch);
  });

  it('renders exact hero heading and interactive read-only preview on LandingPage', () => {
    render(
      <AppProvider>
        <MemoryRouter>
          <LandingPage />
        </MemoryRouter>
      </AppProvider>
    );

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Understand your money before uncertainty becomes a problem.'
    );
    expect(screen.getByText(/Interactive Preview/i)).toBeInTheDocument();

    const delaySlider = screen.getByLabelText(/Simulate Salary Delay/i);
    fireEvent.change(delaySlider, { target: { value: '15' } });
    expect(screen.getByText('15 days')).toBeInTheDocument();
  });

  it('switches language between English and Tamil on landing page', () => {
    render(
      <AppProvider>
        <MemoryRouter>
          <LandingPage />
        </MemoryRouter>
      </AppProvider>
    );

    const langBtn = screen.getByRole('button', { name: /தமிழ்/i });
    fireEvent.click(langBtn);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('நிச்சயமற்ற நிலை');
  });
});
