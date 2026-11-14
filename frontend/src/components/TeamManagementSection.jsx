import React, { useState, useEffect } from 'react';
import { 
  Users, 
  UserPlus, 
  Mail, 
  Shield, 
  CheckCircle, 
  Clock, 
  Trash2, 
  Copy, 
  Check, 
  ExternalLink, 
  AlertCircle,
  RefreshCw,
  X
} from 'lucide-react';
import axiosClient from '../api/axiosClient';

const roleBadgeColors = {
  ADMIN: 'bg-purple-100 text-purple-800 border-purple-200',
  MANAGER: 'bg-blue-100 text-blue-800 border-blue-200',
  CREATOR: 'bg-teal-100 text-teal-800 border-teal-200',
  REVIEWER: 'bg-amber-100 text-amber-800 border-amber-200',
};

export default function TeamManagementSection({ workspaceId, currentRole }) {
  const isAdmin = currentRole === 'ADMIN';

  const [members, setMembers] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [apiUnavailable, setApiUnavailable] = useState(false);
  const [showInviteModal, setShowInviteModal] = useState(false);
  
  // Invite Form State
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('CREATOR');
  const [submittingInvite, setSubmittingInvite] = useState(false);
  const [inviteError, setInviteError] = useState('');
  const [generatedInvite, setGeneratedInvite] = useState(null);
  const [copiedToken, setCopiedToken] = useState(null);

  const loadData = async () => {
    if (!workspaceId || !isAdmin) return;
    setLoading(true);
    setError('');
    try {
      const [membersRes, invitesRes] = await Promise.all([
        axiosClient.get(`/workspaces/${workspaceId}/members`),
        axiosClient.get(`/workspaces/${workspaceId}/invitations`).catch(() => ({ data: { data: [] } }))
      ]);
      setApiUnavailable(false);
      setMembers(membersRes.data?.data || []);
      setInvitations(invitesRes.data?.data || []);
    } catch (err) {
      const status = err.response?.status;
      const msg = err.response?.data?.message || err.message || '';
      if (status === 404 || msg.includes('No static resource') || msg.includes('404')) {
        // Backend endpoint is not present on the connected server instance
        setApiUnavailable(true);
      } else {
        setError(err.response?.data?.message || 'Failed to load team data.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isAdmin) {
      loadData();
    }
  }, [workspaceId, isAdmin]);

  // Non-Admins or instances where backend member management API does not exist: do not render
  if (!isAdmin || apiUnavailable) {
    return null;
  }

  const handleSendInvite = async (e) => {
    e.preventDefault();
    setInviteError('');
    setSubmittingInvite(true);
    setGeneratedInvite(null);

    try {
      const res = await axiosClient.post(`/workspaces/${workspaceId}/invitations`, {
        email: inviteEmail.trim().toLowerCase(),
        role: inviteRole,
      });
      const inviteData = res.data?.data;
      setGeneratedInvite(inviteData);
      setInviteEmail('');
      loadData();
    } catch (err) {
      setInviteError(err.response?.data?.message || 'Failed to create invitation.');
    } finally {
      setSubmittingInvite(false);
    }
  };

  const handleRevokeInvite = async (invitationId) => {
    if (!window.confirm('Are you sure you want to revoke this invitation?')) return;
    try {
      await axiosClient.delete(`/workspaces/${workspaceId}/invitations/${invitationId}`);
      setInvitations(prev => prev.filter(inv => inv.id !== invitationId));
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to revoke invitation.');
    }
  };

  const copyToClipboard = (token, text) => {
    const inviteUrl = `${window.location.origin}/invite/${token}`;
    navigator.clipboard.writeText(inviteUrl);
    setCopiedToken(token);
    setTimeout(() => setCopiedToken(null), 2500);
  };

  return (
    <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
      {/* Header */}
      <div className="p-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <Users className="w-5 h-5 text-teal-600" />
            <h2 className="text-lg font-bold text-slate-900">Workspace Team & Roles</h2>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Separation of duties and role-based access for this workspace. Only Admins can issue invitations.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={loadData}
            title="Refresh team members"
            className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-50 border border-slate-200 rounded-lg transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-teal-600' : ''}`} />
          </button>
          {isAdmin && (
            <button
              onClick={() => {
                setGeneratedInvite(null);
                setInviteError('');
                setShowInviteModal(true);
              }}
              className="inline-flex items-center space-x-2 bg-teal-600 hover:bg-teal-500 text-white px-4 py-2 rounded-lg text-xs font-semibold shadow-sm transition-all"
            >
              <UserPlus className="w-4 h-4" />
              <span>Invite Member</span>
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 mx-6 mt-4 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-lg flex items-center space-x-2">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Active Members Table */}
      <div className="p-6">
        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">
          Active Members ({members.length})
        </h3>
        <div className="overflow-x-auto border border-slate-200 rounded-lg">
          <table className="w-full text-left text-xs text-slate-600">
            <thead className="bg-slate-50 text-slate-700 uppercase font-semibold border-b border-slate-200 text-[11px]">
              <tr>
                <th className="px-4 py-3">Member</th>
                <th className="px-4 py-3">Role & Authority</th>
                <th className="px-4 py-3">Email Status</th>
                <th className="px-4 py-3">Joined</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading && members.length === 0 ? (
                <tr>
                  <td colSpan="4" className="px-4 py-6 text-center text-slate-400 italic">
                    Loading workspace members...
                  </td>
                </tr>
              ) : members.length === 0 ? (
                <tr>
                  <td colSpan="4" className="px-4 py-6 text-center text-slate-400 italic">
                    No members found in this workspace.
                  </td>
                </tr>
              ) : (
                members.map((m) => (
                  <tr key={m.id} className="hover:bg-slate-50/60 transition-colors">
                    <td className="px-4 py-3 font-medium text-slate-900">
                      <div>{m.fullName}</div>
                      <div className="text-[11px] text-slate-400 font-mono">{m.email}</div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${roleBadgeColors[m.role] || 'bg-slate-100 text-slate-700'}`}>
                        {m.role}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {m.emailVerified ? (
                        <span className="inline-flex items-center space-x-1 text-emerald-600 font-medium text-[11px]">
                          <CheckCircle className="w-3.5 h-3.5" />
                          <span>Verified</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center space-x-1 text-amber-600 font-medium text-[11px]">
                          <Clock className="w-3.5 h-3.5" />
                          <span>Pending Verification</span>
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-400 font-mono text-[11px]">
                      {m.joinedAt ? new Date(m.joinedAt).toLocaleDateString() : 'Initial'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Pending Invitations Section (Admins Only) */}
      {isAdmin && (
        <div className="p-6 pt-0 border-t border-slate-100 mt-2">
          <div className="flex items-center justify-between mb-3 pt-6">
            <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Pending & Past Invitations ({invitations.length})
            </h3>
          </div>

          {invitations.length === 0 ? (
            <div className="text-center py-6 border border-dashed border-slate-200 rounded-lg text-slate-400 text-xs">
              No invitations pending. Click "Invite Member" above to onboard team members with CREATOR or REVIEWER roles.
            </div>
          ) : (
            <div className="overflow-x-auto border border-slate-200 rounded-lg">
              <table className="w-full text-left text-xs text-slate-600">
                <thead className="bg-slate-50 text-slate-700 uppercase font-semibold border-b border-slate-200 text-[11px]">
                  <tr>
                    <th className="px-4 py-3">Invited Email</th>
                    <th className="px-4 py-3">Assigned Role</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Expires</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {invitations.map((inv) => (
                    <tr key={inv.id} className="hover:bg-slate-50/60 transition-colors">
                      <td className="px-4 py-3 font-mono text-slate-800">{inv.email}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${roleBadgeColors[inv.role] || 'bg-slate-100 text-slate-700'}`}>
                          {inv.role}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold ${
                          inv.status === 'PENDING'
                            ? 'bg-amber-100 text-amber-800'
                            : inv.status === 'ACCEPTED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-slate-100 text-slate-600'
                        }`}>
                          {inv.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-400 font-mono text-[11px]">
                        {inv.expiresAt ? new Date(inv.expiresAt).toLocaleDateString() : 'N/A'}
                      </td>
                      <td className="px-4 py-3 text-right space-x-2">
                        {inv.status === 'PENDING' && (
                          <>
                            <button
                              onClick={() => copyToClipboard(inv.token)}
                              title="Copy invite URL"
                              className="inline-flex items-center space-x-1 px-2 py-1 text-slate-600 hover:text-teal-700 hover:bg-slate-100 rounded border border-slate-200 transition-colors"
                            >
                              {copiedToken === inv.token ? (
                                <>
                                  <Check className="w-3 h-3 text-emerald-600" />
                                  <span className="text-[10px] text-emerald-600 font-bold">Copied</span>
                                </>
                              ) : (
                                <>
                                  <Copy className="w-3 h-3" />
                                  <span className="text-[10px]">Copy Link</span>
                                </>
                              )}
                            </button>
                            <button
                              onClick={() => handleRevokeInvite(inv.id)}
                              title="Revoke invitation"
                              className="inline-flex items-center space-x-1 px-2 py-1 text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded border border-rose-200 transition-colors"
                            >
                              <Trash2 className="w-3 h-3" />
                              <span className="text-[10px]">Revoke</span>
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Invite Modal */}
      {showInviteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full border border-slate-200 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center space-x-2 text-slate-900 font-bold">
                <UserPlus className="w-5 h-5 text-teal-600" />
                <span>Invite Workspace Team Member</span>
              </div>
              <button
                onClick={() => setShowInviteModal(false)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              {inviteError && (
                <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-lg flex items-center space-x-2">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>{inviteError}</span>
                </div>
              )}

              {generatedInvite ? (
                <div className="space-y-4">
                  <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-xl space-y-2">
                    <div className="flex items-center space-x-2 text-emerald-800 font-bold text-xs">
                      <CheckCircle className="w-4 h-4 text-emerald-600" />
                      <span>Invitation Successfully Generated!</span>
                    </div>
                    <p className="text-xs text-emerald-700">
                      An invitation for <span className="font-semibold">{generatedInvite.email}</span> with role <span className="font-semibold">{generatedInvite.role}</span> has been created.
                    </p>
                  </div>

                  <div className="space-y-1.5">
                    <label className="block text-xs font-semibold text-slate-700">Invitation Link:</label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="text"
                        readOnly
                        value={`${window.location.origin}/invite/${generatedInvite.token}`}
                        className="w-full text-xs font-mono bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-slate-600 select-all"
                      />
                      <button
                        onClick={() => copyToClipboard(generatedInvite.token)}
                        className="inline-flex items-center space-x-1 bg-teal-600 hover:bg-teal-500 text-white px-3 py-2 rounded-lg text-xs font-semibold shadow-sm transition-all flex-shrink-0"
                      >
                        {copiedToken === generatedInvite.token ? (
                          <>
                            <Check className="w-3.5 h-3.5" />
                            <span>Copied</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3.5 h-3.5" />
                            <span>Copy</span>
                          </>
                        )}
                      </button>
                    </div>
                    <p className="text-[11px] text-slate-400">
                      Share this secure one-time link with the invited member. It will expire in 7 days.
                    </p>
                  </div>

                  <div className="pt-2 flex justify-end">
                    <button
                      onClick={() => {
                        setGeneratedInvite(null);
                        setShowInviteModal(false);
                      }}
                      className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-semibold"
                    >
                      Done
                    </button>
                  </div>
                </div>
              ) : (
                <form onSubmit={handleSendInvite} className="space-y-4">
                  <div className="space-y-1">
                    <label className="block text-xs font-semibold text-slate-700">
                      Colleague's Work Email <span className="text-rose-500">*</span>
                    </label>
                    <div className="relative">
                      <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                      <input
                        type="email"
                        required
                        value={inviteEmail}
                        onChange={(e) => setInviteEmail(e.target.value)}
                        placeholder="colleague@company.com"
                        className="w-full pl-9 pr-3 py-2 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                      />
                    </div>
                  </div>

                  <div className="space-y-1">
                    <label className="block text-xs font-semibold text-slate-700">
                      Assigned Role <span className="text-rose-500">*</span>
                    </label>
                    <div className="grid grid-cols-2 gap-3">
                      <label className={`border rounded-xl p-3 cursor-pointer transition-all flex flex-col justify-between ${
                        inviteRole === 'CREATOR' 
                          ? 'border-teal-500 bg-teal-50/50 ring-1 ring-teal-500' 
                          : 'border-slate-200 hover:border-slate-300'
                      }`}>
                        <div className="flex items-center space-x-2">
                          <input
                            type="radio"
                            name="role"
                            value="CREATOR"
                            checked={inviteRole === 'CREATOR'}
                            onChange={() => setInviteRole('CREATOR')}
                            className="text-teal-600 focus:ring-teal-500"
                          />
                          <span className="font-bold text-xs text-slate-800">CREATOR</span>
                        </div>
                        <p className="text-[10px] text-slate-500 mt-2 leading-relaxed">
                          Drafts content, triggers evidence analysis, and responds to findings.
                        </p>
                      </label>

                      <label className={`border rounded-xl p-3 cursor-pointer transition-all flex flex-col justify-between ${
                        inviteRole === 'REVIEWER' 
                          ? 'border-amber-500 bg-amber-50/50 ring-1 ring-amber-500' 
                          : 'border-slate-200 hover:border-slate-300'
                      }`}>
                        <div className="flex items-center space-x-2">
                          <input
                            type="radio"
                            name="role"
                            value="REVIEWER"
                            checked={inviteRole === 'REVIEWER'}
                            onChange={() => setInviteRole('REVIEWER')}
                            className="text-amber-600 focus:ring-amber-500"
                          />
                          <span className="font-bold text-xs text-slate-800">REVIEWER</span>
                        </div>
                        <p className="text-[10px] text-slate-500 mt-2 leading-relaxed">
                          Compliance & legal reviewer. Formally approves or rejects submissions.
                        </p>
                      </label>
                    </div>
                  </div>

                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-[11px] text-slate-500 space-y-1">
                    <div className="font-semibold text-slate-700">Governance & Separation of Duties:</div>
                    <div>A user with CREATOR authority cannot approve their own submissions. Workspace invitations ensure proper separation between authors and reviewers.</div>
                  </div>

                  <div className="flex items-center justify-end space-x-3 pt-2">
                    <button
                      type="button"
                      onClick={() => setShowInviteModal(false)}
                      className="px-4 py-2 border border-slate-200 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-50"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={submittingInvite}
                      className="inline-flex items-center space-x-2 bg-teal-600 hover:bg-teal-500 disabled:opacity-50 text-white px-5 py-2 rounded-lg text-xs font-bold shadow-sm transition-all"
                    >
                      {submittingInvite ? (
                        <>
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                          <span>Generating...</span>
                        </>
                      ) : (
                        <>
                          <Mail className="w-3.5 h-3.5" />
                          <span>Generate & Send Invite</span>
                        </>
                      )}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
