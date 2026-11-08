import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { ApiError } from './api/client'
import { AuthProvider } from './auth/AuthContext'
import App from './App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // Don't hammer the server on client errors (4xx); retry transient failures once.
      retry: (count, err) => !(err instanceof ApiError && err.status >= 400 && err.status < 500) && count < 1,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
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
