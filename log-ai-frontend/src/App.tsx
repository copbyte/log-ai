import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Logs from './pages/Logs'
import AIAnalysis from './pages/AIAnalysis'
import AlertRules from './pages/AlertRules'
import AlertRecords from './pages/AlertRecords'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('token')
  if (!token) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <PrivateRoute>
            <AppLayout />
          </PrivateRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="logs" element={<Logs />} />
        <Route path="ai-analysis" element={<AIAnalysis />} />
        <Route path="alert-rules" element={<AlertRules />} />
        <Route path="alert-records" element={<AlertRecords />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App