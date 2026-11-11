import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import axiosClient from '../api/axiosClient';
import { 
  ShieldCheck, 
  Search, 
  RefreshCw, 
  FileText, 
  CheckCircle, 
  Clock, 
  Lock,
  Tag,
  Hash
} from 'lucide-react';

const actionBadges = {
  KNOWLEDGE_SOURCE_CREATED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  KNOWLEDGE_VERSION_DRAFT_CREATED: 'bg-amber-50 text-amber-700 border-amber-200',
  KNOWLEDGE_VERSION_APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  KNOWLEDGE_ENTRY_CREATED: 'bg-blue-50 text-blue-700 border-blue-200',
  KNOWLEDGE_ENTRY_DELETED: 'bg-rose-50 text-rose-700 border-rose-200',
  SUBMISSION_CREATED: 'bg-sky-50 text-sky-700 border-sky-200',
  SUBMISSION_ANALYSIS_COMPLETED: 'bg-purple-50 text-purple-700 border-purple-200',
  FINDING_RESPONDED: 'bg-teal-50 text-teal-700 border-teal-200',
  REVIEW_DECISION_RECORDED: 'bg-emerald-100 text-emerald-800 border-emerald-300',
};

export default function AuditPage() {
  const { activeWorkspace } = useAuth();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedEvent, setSelectedEvent] = useState(null);

  const workspaceId = activeWorkspace?.id;

  const fetchAuditEvents = async () => {
    if (!workspaceId) return;
    try {
      setLoading(true);
      const res = await axiosClient.get(`/workspaces/${workspaceId}/audit`);
      if (res.data?.success) {
        setEvents(res.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load audit events', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAuditEvents();
  }, [workspaceId]);

  const filteredEvents = events.filter((ev) => {
    const term = searchTerm.toLowerCase();
    return (
      ev.action?.toLowerCase().includes(term) ||
      ev.actor?.fullName?.toLowerCase().includes(term) ||
      ev.actor?.email?.toLowerCase().includes(term) ||
      ev.entityType?.toLowerCase().includes(term) ||
      ev.metadata?.toLowerCase().includes(term)
    );
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center space-x-3">
          <div className="p-2 bg-teal-50 border border-teal-200 rounded-lg text-teal-700">
            <Lock className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">Immutable Audit Trail</h1>
            <p className="text-xs text-slate-500">
              Tamper-evident, append-only log recording every governance approval, verification run, creator response and review decision
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-3">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              placeholder="Search actions, actors, entities..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="text-xs pl-9 pr-3 py-2 border border-slate-300 rounded-lg focus:ring-1 focus:ring-teal-500 focus:outline-none w-64 bg-white"
            />
          </div>
          <button
            onClick={fetchAuditEvents}
            title="Refresh Log"
            className="p-2 border border-slate-300 rounded-lg hover:bg-slate-50 text-slate-600 transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Events Table Card */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
        <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center space-x-2">
            <ShieldCheck className="w-4 h-4 text-teal-600" />
            <span className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
              Recorded Events ({filteredEvents.length})
            </span>
          </div>
          <span className="text-[11px] font-mono text-slate-400">
            Append-Only • Strict Immutability
          </span>
        </div>

        {filteredEvents.length === 0 ? (
          <div className="p-12 text-center text-slate-400 text-sm">
            {loading ? 'Loading audit events...' : 'No audit events recorded yet.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-100 text-left text-xs">
              <thead className="bg-slate-50 text-slate-500 uppercase tracking-wider font-semibold">
                <tr>
                  <th className="py-3 px-4">Event ID</th>
                  <th className="py-3 px-4">Timestamp (UTC)</th>
                  <th className="py-3 px-4">Action</th>
                  <th className="py-3 px-4">Actor</th>
                  <th className="py-3 px-4">Entity</th>
                  <th className="py-3 px-4">Metadata</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {filteredEvents.map((ev) => (
                  <tr key={ev.id} className="hover:bg-slate-50/50">
                    <td className="py-3 px-4 font-mono font-medium text-slate-400">
                      #{ev.id}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap font-mono text-slate-600">
                      {new Date(ev.timestamp).toLocaleString()}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap">
                      <span className={`px-2 py-0.5 rounded text-[11px] font-semibold border ${actionBadges[ev.action] || 'bg-slate-100 text-slate-700'}`}>
                        {ev.action}
                      </span>
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap">
                      <div className="font-semibold text-slate-900">{ev.actor?.fullName || 'System'}</div>
                      <div className="text-[10px] text-slate-400 font-mono">{ev.actor?.email}</div>
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap font-mono">
                      <span className="text-slate-800 font-medium">{ev.entityType}</span>
                      <span className="text-slate-400 ml-1">#{ev.entityId}</span>
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-600 max-w-xs truncate" title={ev.metadata}>
                      {ev.metadata || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
