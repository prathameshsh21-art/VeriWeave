import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { 
  ShieldCheck, 
  Database, 
  FileText, 
  CheckCircle, 
  Clock, 
  Lock, 
  GitCommit,
  ArrowRight,
  BookOpen,
  Sparkles,
  AlertTriangle,
  Play
} from 'lucide-react';
import axiosClient from '../api/axiosClient';
import TeamManagementSection from '../components/TeamManagementSection';

export default function DashboardPage({ onNavigate }) {
  const { user, activeWorkspace } = useAuth();
  const [stats, setStats] = useState({
    sourcesCount: 0,
    submissionsCount: 0,
    auditCount: 0,
  });

  const workspaceId = activeWorkspace?.id || localStorage.getItem('veriweave_active_workspace_id');

  useEffect(() => {
    if (workspaceId) {
      Promise.all([
        axiosClient.get(`/workspaces/${workspaceId}/sources`).catch(() => ({ data: { data: [] } })),
        axiosClient.get(`/workspaces/${workspaceId}/submissions`).catch(() => ({ data: { data: [] } })),
        axiosClient.get(`/workspaces/${workspaceId}/audit`).catch(() => ({ data: { data: [] } }))
      ]).then(([sourcesRes, subsRes, auditRes]) => {
        setStats({
          sourcesCount: sourcesRes.data?.data?.length || 0,
          submissionsCount: subsRes.data?.data?.length || 0,
          auditCount: auditRes.data?.data?.length || 0,
        });
      });
    }
  }, [workspaceId]);

  const rolePermissions = {
    ADMIN: ['Manage workspace & members', 'Edit knowledge entries', 'Approve source versions as CURRENT', 'View all submissions', 'View immutable audit log'],
    MANAGER: ['Submit marketing content & run pipeline', 'Add comments', 'View submissions & status'],
    CREATOR: ['Submit own copy content & run pipeline', 'Accept / modify / reject findings (with mandatory rationale)', 'View own submissions'],
    REVIEWER: ['Approve / reject / request changes (with mandatory rationale)', 'Override critical findings (with legal justification)', 'View prioritized submissions queue'],
  };

  const currentRole = activeWorkspace?.role || 'CREATOR';
  const permissions = rolePermissions[currentRole] || [];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Welcome Banner */}
      <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center space-x-3">
            <h1 className="text-2xl font-bold text-slate-900">Welcome, {user?.fullName}</h1>
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-teal-50 text-teal-700 border border-teal-200">
              Active Session
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            Workspace: <span className="font-semibold text-slate-700">{activeWorkspace?.name}</span> ({activeWorkspace?.slug})
          </p>
        </div>

        <div className="flex items-center space-x-2 text-xs font-semibold">
          <span className="text-slate-500">Role:</span>
          <span className="px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 border border-slate-200 font-mono">
            {currentRole}
          </span>
        </div>
      </div>

      {/* Core Axiom Banner */}
      <div className="bg-gradient-to-r from-teal-900 via-slate-900 to-indigo-950 text-white rounded-xl p-6 shadow-md flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center space-x-2 text-teal-400 text-xs font-mono uppercase tracking-wider font-semibold">
            <Sparkles className="w-4 h-4" />
            <span>VeriWeave Core Architectural Axiom</span>
          </div>
          <p className="text-lg font-semibold tracking-tight text-white">
            "The model proposes. Deterministic code verifies. A human decides."
          </p>
          <p className="text-xs text-slate-300 max-w-2xl leading-relaxed">
            AI is strictly a candidate generator. Every finding is verified in code against active, SHA-256-hashed CURRENT knowledge entries before human review.
          </p>
        </div>

        <div className="flex items-center space-x-3 self-end md:self-auto">
          <button
            onClick={() => onNavigate('submissions')}
            className="inline-flex items-center space-x-2 bg-teal-500 hover:bg-teal-400 text-slate-950 px-4 py-2 rounded-lg text-xs font-bold transition-all shadow-sm"
          >
            <Play className="w-3.5 h-3.5 fill-slate-950" />
            <span>Try Live Workflow</span>
          </button>
        </div>
      </div>

      {/* Quick Launch Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <button
          onClick={() => onNavigate('knowledge')}
          className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm text-left hover:border-teal-400 hover:shadow-md transition-all group"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="p-2.5 bg-indigo-50 border border-indigo-200 rounded-lg text-indigo-700 group-hover:scale-105 transition-transform">
              <BookOpen className="w-5 h-5" />
            </div>
            <span className="text-xs font-mono font-bold text-slate-400">{stats.sourcesCount} Sources</span>
          </div>
          <h3 className="font-bold text-slate-900 text-sm mb-1 group-hover:text-teal-700 transition-colors">
            Knowledge Base Governance
          </h3>
          <p className="text-xs text-slate-500 leading-relaxed">
            Manage source guidelines, DRAFT candidate versions, PDF Box text extraction, and transactional CURRENT version approvals.
          </p>
          <div className="mt-4 flex items-center text-xs font-semibold text-teal-600 group-hover:translate-x-1 transition-transform">
            <span>Open Knowledge Base</span>
            <ArrowRight className="w-3.5 h-3.5 ml-1" />
          </div>
        </button>

        <button
          onClick={() => onNavigate('submissions')}
          className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm text-left hover:border-teal-400 hover:shadow-md transition-all group"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="p-2.5 bg-teal-50 border border-teal-200 rounded-lg text-teal-700 group-hover:scale-105 transition-transform">
              <FileText className="w-5 h-5" />
            </div>
            <span className="text-xs font-mono font-bold text-slate-400">{stats.submissionsCount} In Queue</span>
          </div>
          <h3 className="font-bold text-slate-900 text-sm mb-1 group-hover:text-teal-700 transition-colors">
            Submissions & Review Workflow
          </h3>
          <p className="text-xs text-slate-500 leading-relaxed">
            Execute rule-based copy analysis, inspect verified passage findings, record creator responses, and commit review decisions.
          </p>
          <div className="mt-4 flex items-center text-xs font-semibold text-teal-600 group-hover:translate-x-1 transition-transform">
            <span>View Submissions</span>
            <ArrowRight className="w-3.5 h-3.5 ml-1" />
          </div>
        </button>

        <button
          onClick={() => onNavigate('audit')}
          className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm text-left hover:border-teal-400 hover:shadow-md transition-all group"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="p-2.5 bg-slate-100 border border-slate-200 rounded-lg text-slate-700 group-hover:scale-105 transition-transform">
              <Lock className="w-5 h-5" />
            </div>
            <span className="text-xs font-mono font-bold text-slate-400">{stats.auditCount} Events</span>
          </div>
          <h3 className="font-bold text-slate-900 text-sm mb-1 group-hover:text-teal-700 transition-colors">
            Immutable Audit Trail
          </h3>
          <p className="text-xs text-slate-500 leading-relaxed">
            Tamper-evident chronological log of every knowledge approval, submission check, creator response, and reviewer override.
          </p>
          <div className="mt-4 flex items-center text-xs font-semibold text-teal-600 group-hover:translate-x-1 transition-transform">
            <span>Inspect Audit Log</span>
            <ArrowRight className="w-3.5 h-3.5 ml-1" />
          </div>
        </button>
      </div>

      {/* Role & Separation of Duties Card */}
      <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-4 mb-4">
          <div className="flex items-center space-x-3">
            <div className="p-2 bg-teal-50 border border-teal-200 rounded-lg text-teal-700">
              <Lock className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900">Your Role & Authority</h2>
              <p className="text-xs text-slate-500">Server-enforced access privileges and governance policies for this workspace.</p>
            </div>
          </div>
          <div>
            <span className="text-xs font-mono font-bold text-teal-700 bg-teal-50 border border-teal-200 px-3 py-1.5 rounded-lg">
              {currentRole}
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              Server-Enforced Privileges:
            </p>
            <ul className="space-y-2 text-xs text-slate-600">
              {permissions.map((perm, idx) => (
                <li key={idx} className="flex items-start space-x-2">
                  <CheckCircle className="w-3.5 h-3.5 text-emerald-500 flex-shrink-0 mt-0.5" />
                  <span>{perm}</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="bg-amber-50/70 border border-amber-200/70 rounded-xl p-4 text-xs text-slate-600 flex flex-col justify-between">
            <div>
              <div className="flex items-center space-x-2 text-amber-900 font-bold mb-1">
                <ShieldCheck className="w-4 h-4 text-amber-600" />
                <span>Separation of Duties Rule</span>
              </div>
              <p className="text-amber-800 leading-relaxed text-[11px] mt-1">
                A user can never approve their own submission, even if holding both Creator and Reviewer roles. Submissions authored by your account must be reviewed and approved by an independent reviewer.
              </p>
            </div>
            <div className="mt-3 text-[10px] text-amber-700/80 font-mono">
              Enforced at backend controller & service layer with HTTP 403 Forbidden on self-review.
            </div>
          </div>
        </div>
      </div>

      {/* Team & Member Management Section - Strictly for ADMIN */}
      {currentRole === 'ADMIN' && (
        <TeamManagementSection workspaceId={workspaceId} currentRole={currentRole} />
      )}
    </div>
  );
}
