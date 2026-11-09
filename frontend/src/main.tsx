import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { AuthProvider } from './auth/AuthContext'
import App from './App'
import { WakeBanner } from './components/WakeBanner'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // No query-level retries: apiFetch already retries GETs on network errors and 502/503/504
      // with 2s/5s/10s/20s backoff (DEPLOY.md). Retrying here too would multiply the attempts.
      retry: false,
    },
    mutations: {
      // Never auto-retry writes (POST/PUT/PATCH/DELETE).
      retry: false,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
          <WakeBanner />
          <Toaster
            position="bottom-center"
            containerClassName="!bottom-20 md:!bottom-6"
            toastOptions={{
              style: {
                background: '#1C1C1C',
                color: '#F5F5F5',
                border: '1px solid #2A2A2A',
                borderRadius: '9999px',
                fontSize: '14px',
              },
              success: { iconTheme: { primary: '#FFB000', secondary: '#0A0A0A' } },
              error: { iconTheme: { primary: '#EF4444', secondary: '#0A0A0A' } },
            }}
          />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
