import React from 'react';
import { useAuth } from '../context/AuthContext';
import { 
  ShieldCheck, 
  Building2, 
  User, 
  LogOut, 
  LayoutDashboard, 
  BookOpen, 
  FileText, 
  Lock,
  ArrowRightLeft
} from 'lucide-react';

const roleBadgeColors = {
  ADMIN: 'bg-purple-100 text-purple-800 border-purple-200',
  MANAGER: 'bg-blue-100 text-blue-800 border-blue-200',
  CREATOR: 'bg-teal-100 text-teal-800 border-teal-200',
  REVIEWER: 'bg-amber-100 text-amber-800 border-amber-200',
};

export default function Header({ activeTab, onTabChange }) {
  const { user, activeWorkspace, memberships, switchWorkspace, logout } = useAuth();

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="h-16 flex items-center justify-between">
          {/* Brand Logo & Tagline */}
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-lg bg-teal-600 flex items-center justify-center text-white shadow-sm">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="font-bold text-lg text-slate-900 tracking-tight">VeriWeave</span>
                <span className="text-xs px-2 py-0.5 rounded font-mono font-medium bg-slate-100 text-slate-600">MVP</span>
              </div>
              <p className="text-xs text-slate-500 hidden sm:block">Evidence-linked brand governance</p>
            </div>
          </div>

          {/* Navigation Tabs (when logged in) */}
          {user && (
            <nav className="hidden md:flex items-center space-x-1">
              {[
                { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
                { id: 'knowledge', label: 'Knowledge Base', icon: BookOpen },
                { id: 'submissions', label: 'Submissions & Review', icon: FileText },
                { id: 'audit', label: 'Audit Trail', icon: Lock },
              ].map((tab) => {
                const Icon = tab.icon;
                const isActive = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    onClick={() => onTabChange(tab.id)}
                    className={`flex items-center space-x-2 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all ${
                      isActive
                        ? 'bg-teal-50 text-teal-800 border border-teal-200'
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-transparent'
                    }`}
                  >
                    <Icon className={`w-4 h-4 ${isActive ? 'text-teal-600' : 'text-slate-400'}`} />
                    <span>{tab.label}</span>
                  </button>
                );
              })}
            </nav>
          )}

          {/* User & Workspace Controls */}
          {user && (
            <div className="flex items-center space-x-3">
              {/* Workspace Badge */}
              {activeWorkspace && (
                <div className="flex items-center space-x-2 bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1 text-xs">
                  <Building2 className="w-3.5 h-3.5 text-slate-500" />
                  <span className="font-semibold text-slate-700 hidden sm:inline">{activeWorkspace.name}</span>
                  <span
                    className={`text-[10px] uppercase px-1.5 py-0.5 rounded-full border font-bold ${
                      roleBadgeColors[activeWorkspace.role] || 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {activeWorkspace.role}
                  </span>
                </div>
              )}

              {/* User Profile & Logout */}
              <div className="flex items-center space-x-2 border-l border-slate-200 pl-3">
                <div className="text-right hidden xl:block">
                  <div className="text-xs font-semibold text-slate-800">{user.fullName}</div>
                  <div className="text-[10px] text-slate-400 font-mono">{user.email}</div>
                </div>
                <button
                  onClick={logout}
                  title="Sign out"
                  className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Mobile Navigation Bar */}
        {user && (
          <div className="md:hidden flex items-center justify-around py-2 border-t border-slate-100">
            {[
              { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
              { id: 'knowledge', label: 'Knowledge Base', icon: BookOpen },
              { id: 'submissions', label: 'Submissions', icon: FileText },
              { id: 'audit', label: 'Audit', icon: Lock },
            ].map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => onTabChange(tab.id)}
                  className={`flex flex-col items-center py-1 px-2 text-[10px] font-medium ${
                    isActive ? 'text-teal-700' : 'text-slate-500'
                  }`}
                >
                  <Icon className="w-4 h-4 mb-0.5" />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </header>
  );
}
