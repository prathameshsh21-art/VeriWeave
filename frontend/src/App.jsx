import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import Header from './components/Header';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import KnowledgePage from './pages/KnowledgePage';
import SubmissionsPage from './pages/SubmissionsPage';
import AuditPage from './pages/AuditPage';
import AcceptInvitePage from './pages/AcceptInvitePage';

function AppContent() {
  const { user, loading } = useAuth();
  const [activeTab, setActiveTab] = useState('dashboard');

  // Check if current route is an invitation link (/invite/:token)
  const pathname = window.location.pathname;
  if (pathname.startsWith('/invite/')) {
    const token = pathname.replace('/invite/', '').split('/')[0];
    if (token) {
      return <AcceptInvitePage token={token} />;
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="flex items-center space-x-3 text-slate-500">
          <div className="w-5 h-5 border-2 border-teal-600 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm font-medium">Loading VeriWeave session...</span>
        </div>
      </div>
    );
  }

  const renderCurrentPage = () => {
    switch (activeTab) {
      case 'knowledge':
        return <KnowledgePage />;
      case 'submissions':
        return <SubmissionsPage />;
      case 'audit':
        return <AuditPage />;
      case 'dashboard':
      default:
        return <DashboardPage onNavigate={setActiveTab} />;
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <Header activeTab={activeTab} onTabChange={setActiveTab} />
      <main className="flex-1">
        {user ? renderCurrentPage() : <LoginPage />}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}
