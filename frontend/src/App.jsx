import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/Layout'
import Login from './pages/Login'
import Connection from './pages/Connection'
import Topology from './pages/Topology'
import VertexType from './pages/VertexType'
import EdgeType from './pages/EdgeType'
import PropertyManagement from './pages/PropertyManagement'
import VertexData from './pages/VertexData'
import EdgeData from './pages/EdgeData'
import GraphExplore from './pages/GraphExplore'
import GremlinConsole from './pages/GremlinConsole'
import ImportExport from './pages/ImportExport'
import OperationLog from './pages/OperationLog'
import UserManagement from './pages/UserManagement'
import RoleManagement from './pages/RoleManagement'
import PermissionOverview from './pages/PermissionOverview'
import LoginLog from './pages/LoginLog'

const pages = [
  { path: 'connection', element: <Connection /> },
  { path: 'topology', element: <Topology /> },
  { path: 'vertex-type', element: <VertexType /> },
  { path: 'edge-type', element: <EdgeType /> },
  { path: 'property-management', element: <PropertyManagement /> },
  { path: 'vertex-data', element: <VertexData /> },
  { path: 'edge-data', element: <EdgeData /> },
  { path: 'graph-explore', element: <GraphExplore /> },
  { path: 'gremlin', element: <GremlinConsole /> },
  { path: 'import-export', element: <ImportExport /> },
  { path: 'operation-log', element: <OperationLog /> },
  { path: 'user-management', element: <UserManagement /> },
  { path: 'role-management', element: <RoleManagement /> },
  { path: 'permission-overview', element: <PermissionOverview /> },
  { path: 'login-log', element: <LoginLog /> },
]

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Layout>
                  <Navigate to="/connection" replace />
                </Layout>
              </ProtectedRoute>
            }
          />
          {pages.map((p) => (
            <Route
              key={p.path}
              path={`/${p.path}`}
              element={
                <ProtectedRoute>
                  <Layout>{p.element}</Layout>
                </ProtectedRoute>
              }
            />
          ))}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
