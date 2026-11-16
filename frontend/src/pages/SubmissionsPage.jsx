import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import axiosClient from '../api/axiosClient';
import {
  FileText,
  Plus,
  Play,
  CheckCircle,
  AlertTriangle,
  XCircle,
  Clock,
  Sparkles,
  ShieldAlert,
  ArrowRight,
  MessageSquare,
  AlertOctagon,
  RefreshCw,
  Send,
  UserCheck,
  Ban,
  GitBranch,
  ShieldCheck,
  History
} from 'lucide-react';
import TraceabilityModal from '../components/TraceabilityModal';

const severityBadge = {
  CRITICAL: 'bg-rose-100 text-rose-800 border-rose-300',
  MAJOR: 'bg-amber-100 text-amber-800 border-amber-300',
  MINOR: 'bg-blue-100 text-blue-800 border-blue-300',
  ADVISORY: 'bg-slate-100 text-slate-700 border-slate-300',
};

const statusBadge = {
  DRAFT: 'bg-slate-100 text-slate-700 border-slate-300',
  PENDING_ANALYSIS: 'bg-slate-100 text-slate-700 border-slate-300',
  IN_REVIEW: 'bg-blue-100 text-blue-800 border-blue-300',
  CHANGES_REQUESTED: 'bg-amber-100 text-amber-800 border-amber-300',
  APPROVED: 'bg-emerald-100 text-emerald-800 border-emerald-300',
  REJECTED: 'bg-rose-100 text-rose-800 border-rose-300',
};

const normalizeAction = (action) => {
  if (!action) return null;
  const upper = String(action).trim().toUpperCase();
  if (upper === 'ACCEPTED' || upper === 'ACCEPT') return 'ACCEPT';
  if (upper === 'MODIFIED' || upper === 'MODIFY') return 'MODIFY';
  if (upper === 'REJECTED' || upper === 'REJECT') return 'REJECT';
  return null;
};

export default function SubmissionsPage() {
  const { user, activeWorkspace } = useAuth();
  const [submissions, setSubmissions] = useState([]);
  const [selectedSubmissionId, setSelectedSubmissionId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // New Submission Modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newCampaign, setNewCampaign] = useState('');
  const [newRawText, setNewRawText] = useState('');

  // Traceability & Revision states
  const [traceFindingId, setTraceFindingId] = useState(null);
  const [showRevisionModal, setShowRevisionModal] = useState(false);
  const [revisionRawText, setRevisionRawText] = useState('');
  const [submissionVersions, setSubmissionVersions] = useState([]);
  const [submissionDecisions, setSubmissionDecisions] = useState([]);

  // Finding response states: scoped by submissionId -> { [submissionId]: { [findingId]: { action, reason, proposedEdit } } }
  const [findingResponses, setFindingResponses] = useState({});

  // Review decision states
  const [reviewDecision, setReviewDecision] = useState('APPROVE');
  const [reviewComments, setReviewComments] = useState('');
  const [overrideReason, setOverrideReason] = useState('');

  const workspaceId = activeWorkspace?.id;
  const isReviewerOrAdmin = activeWorkspace?.role === 'REVIEWER' || activeWorkspace?.role === 'ADMIN';

  // Load submissions
  const fetchSubmissions = async (isManualRefresh = false) => {
    if (!workspaceId) return;
    if (isManualRefresh && isRefreshing) return;
    try {
      if (isManualRefresh) {
        setIsRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);
      const res = await axiosClient.get(`/workspaces/${workspaceId}/submissions`);
      if (res.data?.success) {
        const freshList = res.data.data || [];
        setSubmissions(freshList);
        if (freshList.length > 0) {
          setSelectedSubmissionId((prevId) => {
            if (prevId && freshList.some((s) => s.id === prevId)) {
              return prevId;
            }
            return freshList[0].id;
          });
        } else {
          setSelectedSubmissionId(null);
        }
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load submissions');
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchSubmissions();
  }, [workspaceId]);

  // Load selected submission detail
  const fetchDetail = async (id) => {
    if (!workspaceId || !id) return;
    try {
      setLoading(true);
      const res = await axiosClient.get(`/workspaces/${workspaceId}/submissions/${id}`);
      if (res.data?.success) {
        setDetail(res.data.data);
      }
      try {
        const vRes = await axiosClient.get(`/workspaces/${workspaceId}/submissions/${id}/versions`);
        if (vRes.data?.success) {
          setSubmissionVersions(vRes.data.data || []);
        }
      } catch (vErr) {
        console.warn('Could not load version history', vErr);
      }
      try {
        const dRes = await axiosClient.get(`/workspaces/${workspaceId}/submissions/${id}/decisions`);
        if (dRes.data?.success) {
          setSubmissionDecisions(dRes.data.data || []);
        }
      } catch (dErr) {
        console.warn('Could not load decisions', dErr);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load submission details');
    } finally {
      setLoading(false);
    }
  };

  // Create Revision (v2, v3, etc.)
  const handleCreateRevision = async (e) => {
    e.preventDefault();
    if (!revisionRawText.trim()) return;
    try {
      setActionLoading(true);
      const res = await axiosClient.post(
        `/workspaces/${workspaceId}/submissions/${selectedSubmissionId}/revisions`,
        { rawText: revisionRawText.trim() }
      );
      if (res.data?.success) {
        setShowRevisionModal(false);
        setSuccessMsg(`Revision created! Version v${res.data.data.currentVersion?.versionNumber || 2} is in DRAFT. Run analysis to verify compliance.`);
        await fetchDetail(selectedSubmissionId);
        await fetchSubmissions();
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create revision');
    } finally {
      setActionLoading(false);
    }
  };

  useEffect(() => {
    if (selectedSubmissionId) {
      fetchDetail(selectedSubmissionId);
    } else {
      setDetail(null);
    }
  }, [selectedSubmissionId]);

  // Sample copy loader
  const loadSampleNonCompliantCopy = () => {
    setNewTitle('VeriFlora Spring Radiance Botanical Elixir Ad');
    setNewCampaign('Spring Launch 2026');
    setNewRawText(
      'Experience nature with our 100% organic botanical elixir. Our patented herbal formula cures eczema within three days of continuous application.'
    );
  };

  // Create submission
  const handleCreateSubmission = async (e) => {
    e.preventDefault();
    if (!newTitle.trim() || !newRawText.trim()) return;
    try {
      setActionLoading(true);
      const res = await axiosClient.post(`/workspaces/${workspaceId}/submissions`, {
        title: newTitle.trim(),
        campaignName: newCampaign.trim() || 'General Campaign',
        rawText: newRawText.trim()
      });
      if (res.data?.success) {
        setShowCreateModal(false);
        setNewTitle('');
        setNewCampaign('');
        setNewRawText('');
        setSuccessMsg('Submission created! Ready to run automated governance analysis.');
        await fetchSubmissions();
        setSelectedSubmissionId(res.data.data.id);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create submission');
    } finally {
      setActionLoading(false);
    }
  };

  // Run analysis pipeline
  const handleRunAnalysis = async () => {
    if (!selectedSubmissionId) return;
    try {
      setActionLoading(true);
      setError(null);
      const res = await axiosClient.post(`/workspaces/${workspaceId}/submissions/${selectedSubmissionId}/analyze`);
      if (res.data?.success) {
        setSuccessMsg(
          `Governance analysis complete! Total verified findings: ${res.data.data.verifiedFindingsCount}, Rejected proposals: ${res.data.data.rejectedFindingsCount}`
        );
        await fetchDetail(selectedSubmissionId);
        await fetchSubmissions();
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Analysis pipeline failed');
    } finally {
      setActionLoading(false);
    }
  };

  // Submit creator response to a finding
  const handleFindingResponseSubmit = async (findingId) => {
    const existingResp = responses.find((r) => r.findingId === findingId);
    const subResponses = findingResponses[selectedSubmissionId] || {};
    const resp = subResponses[findingId] || {
      action: normalizeAction(existingResp?.action),
      reason: existingResp?.reason || '',
      proposedEdit: existingResp?.modifiedText || existingResp?.proposedEdit || ''
    };

    if (!resp || !resp.action) {
      setError('Please select an action: Accept, Modify, or Reject.');
      return;
    }
    if ((resp.action === 'MODIFY' || resp.action === 'REJECT') && (!resp.reason || !resp.reason.trim())) {
      setError('Mandatory response requirement: You must provide a specific rationale when modifying or rejecting a finding.');
      return;
    }

    try {
      setActionLoading(true);
      const res = await axiosClient.post(
        `/workspaces/${workspaceId}/submissions/${selectedSubmissionId}/findings/${findingId}/responses`,
        {
          action: resp.action,
          reason: resp.reason?.trim() || null,
          proposedEdit: resp.proposedEdit?.trim() || null
        }
      );
      if (res.data?.success) {
        setSuccessMsg('Finding response recorded in immutable audit log.');
        await fetchDetail(selectedSubmissionId);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to record finding response');
    } finally {
      setActionLoading(false);
    }
  };

  // Submit reviewer decision
  const handleRecordDecision = async (e) => {
    e.preventDefault();
    try {
      setActionLoading(true);
      const res = await axiosClient.post(
        `/workspaces/${workspaceId}/submissions/${selectedSubmissionId}/decisions`,
        {
          decision: reviewDecision,
          comments: reviewComments.trim() || null,
          overrideReason: overrideReason.trim() || null
        }
      );
      if (res.data?.success) {
        setSuccessMsg(`Review decision recorded: Submission marked as ${res.data.data.status}`);
        setReviewComments('');
        setOverrideReason('');
        await fetchDetail(selectedSubmissionId);
        await fetchSubmissions();
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to record review decision');
    } finally {
      setActionLoading(false);
    }
  };

  const subObj = detail?.submission || detail || {};
  const versionObj = detail?.currentVersion || detail?.activeVersion || {};
  const findings = detail?.findings || [];
  const responses = detail?.responses || [];
  const latestRun = detail?.latestAnalysisRun;
  const creatorId = subObj?.createdBy?.id || detail?.createdById;
  const isCreatorOfSubmission = creatorId === user?.id;

  // Check if there are unresolved critical findings
  const hasUnresolvedCriticalFindings = findings.some(f => {
    if (f.severity !== 'CRITICAL') return false;
    const resp = responses.find(r => r.findingId === f.id);
    if (!resp) return true;
    const normalized = normalizeAction(resp.action);
    return normalized !== 'ACCEPT';
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Title & Action */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center space-x-3">
          <div className="p-2 bg-teal-50 border border-teal-200 rounded-lg text-teal-700">
            <FileText className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">Submissions & Governance Workflow</h1>
            <p className="text-xs text-slate-500">
              Rule-based copy analysis, deterministic evidence verification, creator responses, and reviewer decisions
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowCreateModal(true)}
          className="inline-flex items-center space-x-2 bg-teal-600 hover:bg-teal-700 text-white px-3.5 py-2 rounded-lg text-sm font-medium shadow-sm transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>New Copy Submission</span>
        </button>
      </div>

      {/* Notifications */}
      {successMsg && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-lg text-sm flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <CheckCircle className="w-4 h-4 text-emerald-600 flex-shrink-0" />
            <span>{successMsg}</span>
          </div>
          <button onClick={() => setSuccessMsg(null)} className="text-emerald-600 hover:text-emerald-800 text-xs font-bold">Dismiss</button>
        </div>
      )}
      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 text-rose-800 rounded-lg text-sm flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <AlertTriangle className="w-4 h-4 text-rose-600 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-rose-600 hover:text-rose-800 text-xs font-bold">Dismiss</button>
        </div>
      )}

      {/* Main Grid: Submissions Queue (Left) + Detailed Interactive Findings Workspace (Right) */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Left: Queue */}
        <div className="lg:col-span-1 bg-white border border-slate-200 rounded-xl p-4 shadow-sm space-y-3">
          <div className="flex items-center justify-between border-b border-slate-100 pb-2">
            <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Submissions ({submissions.length})
            </h2>
            <button
              onClick={() => fetchSubmissions(true)}
              disabled={isRefreshing || loading}
              title="Refresh Submissions"
              className="text-slate-400 hover:text-slate-600 disabled:opacity-40 p-1 rounded hover:bg-slate-100 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-teal-600' : ''}`} />
            </button>
          </div>

          <div className="space-y-2">
            {submissions.map((sub) => {
              const isSelected = selectedSubmissionId === sub.id;
              return (
                <button
                  key={sub.id}
                  onClick={() => setSelectedSubmissionId(sub.id)}
                  className={`w-full text-left p-3 rounded-lg border transition-all ${
                    isSelected
                      ? 'bg-teal-50/70 border-teal-300 text-teal-900 shadow-xs'
                      : 'bg-white border-slate-200/80 text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1.5">
                    <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold border ${statusBadge[sub.status] || ''}`}>
                      {sub.status}
                    </span>
                    <span className="text-[10px] text-slate-400">#{sub.id}</span>
                  </div>
                  <div className="font-semibold text-sm line-clamp-1">{sub.title}</div>
                  <div className="text-xs text-slate-500 mt-1 flex items-center justify-between">
                    <span>{sub.campaignName || 'General Campaign'}</span>
                    <span>v{sub.currentVersion?.versionNumber || sub.currentVersionNumber || 1}</span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right: Selected Submission Detail */}
        <div className="lg:col-span-3 space-y-6">
          {detail ? (
            <>
              {/* Submission Header Card */}
              <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-100 pb-4">
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${statusBadge[subObj.status || 'DRAFT']}`}>
                        {subObj.status || 'DRAFT'}
                      </span>
                      <h2 className="text-lg font-bold text-slate-900">{subObj.title}</h2>
                    </div>
                    <div className="flex items-center space-x-3 text-xs text-slate-500 mt-1">
                      <span>Campaign: <strong>{subObj.campaignName || 'General Campaign'}</strong></span>
                      <span>•</span>
                      <span>Version: <strong>v{subObj.currentVersionNumber || versionObj.versionNumber || 1}</strong></span>
                      <span>•</span>
                      <span>Creator: <strong>{subObj.createdBy?.fullName || subObj.createdBy || 'Creator'}</strong> ({subObj.createdBy?.email || subObj.createdByEmail || ''})</span>
                    </div>
                    {submissionVersions.length > 1 && (
                      <div className="flex items-center space-x-1.5 mt-2 text-xs">
                        <History className="w-3.5 h-3.5 text-slate-400" />
                        <span className="text-slate-400">Revisions:</span>
                        {submissionVersions.map((v) => (
                          <span
                            key={v.id}
                            className={`px-2 py-0.5 rounded text-[11px] font-mono font-medium ${
                              v.versionNumber === (subObj.currentVersionNumber || versionObj.versionNumber)
                                ? 'bg-teal-100 text-teal-800 border border-teal-200'
                                : 'bg-slate-100 text-slate-600'
                            }`}
                          >
                            v{v.versionNumber}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="flex items-center space-x-2">
                    {subObj.status !== 'APPROVED' && (
                      <button
                        onClick={() => {
                          setRevisionRawText(versionObj.rawText || '');
                          setShowRevisionModal(true);
                        }}
                        disabled={actionLoading}
                        className="inline-flex items-center space-x-2 bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 px-3.5 py-2 rounded-lg text-sm font-semibold shadow-xs transition-colors"
                        title="Create a new revision (v2) of this submission"
                      >
                        <GitBranch className="w-4 h-4 text-slate-500" />
                        <span>Create Revision</span>
                      </button>
                    )}
                    <button
                      onClick={handleRunAnalysis}
                      disabled={actionLoading}
                      className="inline-flex items-center space-x-2 bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm font-semibold shadow-xs transition-colors"
                    >
                      <Play className="w-4 h-4 fill-white" />
                      <span>{actionLoading ? 'Analyzing...' : 'Run Governance Analysis'}</span>
                    </button>
                  </div>
                </div>

                {/* Analysis Summary Banner */}
                {latestRun && (
                  <div className="bg-slate-50 border border-slate-200 rounded-lg p-3.5 flex flex-wrap items-center justify-between gap-3 text-xs">
                    <div className="flex items-center space-x-4">
                      <span className="font-semibold text-slate-700">Latest Run #{latestRun.id}:</span>
                      <span>Passages: <strong>{latestRun.passagesCount}</strong></span>
                      <span className="text-emerald-700 font-semibold">Verified Findings: <strong>{latestRun.verifiedFindingsCount}</strong></span>
                      <span className="text-slate-500">Rejected by Validator: <strong>{latestRun.rejectedFindingsCount}</strong></span>
                    </div>
                    <div className="text-slate-400 font-mono">
                      Completed: {new Date(latestRun.completedAt).toLocaleTimeString()}
                    </div>
                  </div>
                )}
              </div>

              {/* Split Workspace: Raw Copy (Left) + Verified Grounded Findings (Right) */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Left Panel: Raw Copy with Segmented Offsets */}
                <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-3">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                    <h3 className="font-semibold text-slate-900 text-sm flex items-center space-x-2">
                      <FileText className="w-4 h-4 text-teal-600" />
                      <span>Submitted Copy Content</span>
                    </h3>
                    <span className="text-xs text-slate-400 font-mono">
                      {(versionObj.rawText || '').length} characters
                    </span>
                  </div>

                  <div className="p-4 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-800 font-mono whitespace-pre-wrap leading-relaxed">
                    {versionObj.rawText || 'No text content available'}
                  </div>

                  {findings.length > 0 && (
                    <div className="text-xs text-slate-500 space-y-1 pt-2">
                      <span className="font-semibold text-slate-700">Highlighted Passage Offsets:</span>
                      {findings.map((f, i) => (
                        <div key={f.id} className="p-2 bg-rose-50/60 border border-rose-200 rounded text-rose-900 font-mono text-xs">
                          [{f.passageStart}..{f.passageEnd}]: "{f.passageQuote || f.passageText}"
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Right Panel: Verified Grounded Findings */}
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-slate-900 text-sm flex items-center space-x-2">
                      <ShieldAlert className="w-4 h-4 text-rose-600" />
                      <span>Verified Grounded Findings ({findings.length})</span>
                    </h3>
                  </div>

                  {findings.length === 0 ? (
                    <div className="bg-white border border-slate-200 rounded-xl p-8 text-center text-slate-400 text-sm">
                      No findings detected for this submission. Click "Run Governance Analysis" to verify against active knowledge rules.
                    </div>
                  ) : (
                    findings.map((finding) => {
                      const existingResp = responses.find((r) => r.findingId === finding.id);
                      const subResponses = findingResponses[selectedSubmissionId] || {};
                      const findingDraft = subResponses[finding.id];
                      const currentFormState = {
                        action: findingDraft?.action !== undefined ? findingDraft.action : normalizeAction(existingResp?.action),
                        reason: findingDraft?.reason !== undefined ? findingDraft.reason : (existingResp?.reason || ''),
                        proposedEdit: findingDraft?.proposedEdit !== undefined ? findingDraft.proposedEdit : (existingResp?.modifiedText || existingResp?.proposedEdit || '')
                      };

                      return (
                        <div
                          key={finding.id}
                          className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-3.5 border-l-4 border-l-rose-500"
                        >
                          {/* Finding Header */}
                          <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-2">
                              <span className={`px-2 py-0.5 rounded text-xs font-bold border ${severityBadge[finding.severity]}`}>
                                {finding.severity}
                              </span>
                              <span className="font-mono text-xs font-bold text-slate-800">
                                {finding.ruleCode}
                              </span>
                              <span className="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded">
                                {finding.category}
                              </span>
                            </div>
                            <div className="flex items-center space-x-2">
                              <button
                                type="button"
                                onClick={() => setTraceFindingId(finding.id)}
                                className="inline-flex items-center space-x-1 px-2.5 py-1 rounded bg-teal-50 hover:bg-teal-100 text-teal-800 border border-teal-200 text-xs font-semibold transition-colors"
                                title="Open complete Guideline-to-Decision Traceability Provenance"
                              >
                                <ShieldCheck className="w-3.5 h-3.5 text-teal-600" />
                                <span>Inspect Audit Trace</span>
                              </button>
                              <span className="text-[10px] font-mono text-slate-400">
                                [{finding.passageStart}..{finding.passageEnd}]
                              </span>
                            </div>
                          </div>

                          {/* Grounded Evidence Box */}
                          <div className="bg-teal-50/70 border border-teal-200 rounded-lg p-3 text-xs space-y-1">
                            <div className="flex items-center space-x-1.5 text-teal-800 font-semibold">
                              <CheckCircle className="w-3.5 h-3.5 text-teal-600" />
                              <span>Verified Source Grounding (Version #{finding.sourceVersionId}):</span>
                            </div>
                            <p className="text-teal-900 font-mono italic">
                              "{finding.evidenceQuote}"
                            </p>
                          </div>

                          {/* Explanation & Suggestion */}
                          <div className="text-xs space-y-1 text-slate-700">
                            <p><strong>Governance Rationale:</strong> {finding.explanation}</p>
                            {finding.suggestedEdit && (
                              <div className="mt-2 p-2.5 bg-slate-50 border border-slate-200 rounded text-slate-800 font-mono text-xs">
                                <span className="text-slate-500 block mb-1">Suggested Replacement:</span>
                                <span className="text-emerald-700 font-semibold">{finding.suggestedEdit}</span>
                              </div>
                            )}
                          </div>

                          {/* Creator Response Section */}
                          <div className="pt-3 border-t border-slate-100 space-y-2">
                            <div className="flex items-center justify-between">
                              <span className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
                                Creator Response
                              </span>
                              {existingResp && normalizeAction(existingResp.action) && (
                                <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                                  Current: {normalizeAction(existingResp.action)}
                                </span>
                              )}
                            </div>

                            {/* If viewing as an independent Reviewer (who did not author the submission), show read-only creator response */}
                            {isReviewerOrAdmin && !isCreatorOfSubmission ? (
                              <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-1.5">
                                {existingResp ? (
                                  <>
                                    <div className="flex items-center justify-between">
                                      <span className="font-semibold text-slate-800">
                                        Action Taken: <strong className="text-teal-800">{normalizeAction(existingResp.action)}</strong>
                                      </span>
                                    </div>
                                    {existingResp.reason && (
                                      <p className="text-slate-600">
                                        <span className="font-medium text-slate-700">Creator Rationale:</span> {existingResp.reason}
                                      </p>
                                    )}
                                    {(existingResp.modifiedText || existingResp.proposedEdit) && (
                                      <p className="text-blue-900 font-mono text-[11px] bg-blue-50 p-2 rounded border border-blue-100">
                                        <span className="font-sans font-medium text-blue-700">Proposed Alternative:</span> "{existingResp.modifiedText || existingResp.proposedEdit}"
                                      </p>
                                    )}
                                  </>
                                ) : (
                                  <p className="text-slate-400 italic">
                                    Pending creator response. Author has not yet recorded an action for this finding.
                                  </p>
                                )}
                              </div>
                            ) : (
                              /* Interactive response controls for the content creator */
                              <>
                                <div className="flex items-center space-x-2">
                                  {['ACCEPT', 'MODIFY', 'REJECT'].map((act) => (
                                    <button
                                      key={act}
                                      type="button"
                                      onClick={() =>
                                        setFindingResponses((prev) => ({
                                          ...prev,
                                          [selectedSubmissionId]: {
                                            ...(prev[selectedSubmissionId] || {}),
                                            [finding.id]: { ...currentFormState, action: act }
                                          }
                                        }))
                                      }
                                      className={`px-3 py-1 rounded text-xs font-semibold border transition-all ${
                                        currentFormState.action === act
                                          ? act === 'ACCEPT'
                                            ? 'bg-emerald-600 text-white border-emerald-600'
                                            : act === 'MODIFY'
                                            ? 'bg-blue-600 text-white border-blue-600'
                                            : 'bg-rose-600 text-white border-rose-600'
                                          : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                                      }`}
                                    >
                                      {act === 'ACCEPT' ? 'Accept Fix' : act === 'MODIFY' ? 'Modify Copy' : 'Reject Finding'}
                                    </button>
                                  ))}
                                </div>

                                {/* Reason Input if MODIFY or REJECT */}
                                {(currentFormState.action === 'MODIFY' || currentFormState.action === 'REJECT') && (
                                  <div className="space-y-1.5 pt-1">
                                    <label className="block text-[11px] font-semibold text-rose-700">
                                      Mandatory Rationale *
                                    </label>
                                    <input
                                      type="text"
                                      required
                                      placeholder="Explain why this finding is rejected or modified..."
                                      value={currentFormState.reason || ''}
                                      onChange={(e) =>
                                        setFindingResponses((prev) => ({
                                          ...prev,
                                          [selectedSubmissionId]: {
                                            ...(prev[selectedSubmissionId] || {}),
                                            [finding.id]: { ...currentFormState, reason: e.target.value }
                                          }
                                        }))
                                      }
                                      className="w-full text-xs border border-rose-300 rounded px-2.5 py-1.5 focus:ring-1 focus:ring-rose-500 focus:outline-none"
                                    />
                                  </div>
                                )}

                                {currentFormState.action === 'MODIFY' && (
                                  <div className="space-y-1.5 pt-1">
                                    <label className="block text-[11px] font-semibold text-slate-600">
                                      Proposed Alternative Copy
                                    </label>
                                    <input
                                      type="text"
                                      placeholder="Enter proposed alternative copy..."
                                      value={currentFormState.proposedEdit || ''}
                                      onChange={(e) =>
                                        setFindingResponses((prev) => ({
                                          ...prev,
                                          [selectedSubmissionId]: {
                                            ...(prev[selectedSubmissionId] || {}),
                                            [finding.id]: { ...currentFormState, proposedEdit: e.target.value }
                                          }
                                        }))
                                      }
                                      className="w-full text-xs border border-slate-300 rounded px-2.5 py-1.5 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                                    />
                                  </div>
                                )}

                                <button
                                  type="button"
                                  onClick={() => handleFindingResponseSubmit(finding.id)}
                                  disabled={actionLoading}
                                  className="mt-2 inline-flex items-center space-x-1.5 bg-slate-800 hover:bg-slate-900 text-white px-3 py-1 rounded text-xs font-medium transition-colors"
                                >
                                  <Send className="w-3 h-3" />
                                  <span>Save Response</span>
                                </button>
                              </>
                            )}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>

              {/* Role-Specific Decision & Feedback Area */}
              {!isReviewerOrAdmin ? (
                /* CREATOR VIEW: Reviewer Feedback & Governance Status (NO approval/rejection controls!) */
                <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <div className="flex items-center space-x-2">
                      <UserCheck className="w-5 h-5 text-teal-600" />
                      <h3 className="font-bold text-slate-900 text-base">Compliance Review Status</h3>
                    </div>
                    <span className="text-xs text-slate-500">
                      Independent Governance Gate
                    </span>
                  </div>

                  {submissionDecisions.length > 0 ? (
                    <div className="space-y-3">
                      {submissionDecisions.slice(0, 1).map((dec) => (
                        <div
                          key={dec.id}
                          className={`p-4 rounded-xl border ${
                            dec.decision === 'APPROVED'
                              ? 'bg-emerald-50/70 border-emerald-200 text-emerald-900'
                              : dec.decision === 'CHANGES_REQUESTED'
                              ? 'bg-amber-50/70 border-amber-200 text-amber-900'
                              : 'bg-rose-50/70 border-rose-200 text-rose-900'
                          }`}
                        >
                          <div className="flex items-center justify-between mb-2">
                            <span className="font-bold text-xs uppercase tracking-wider px-2 py-0.5 rounded bg-white/80 shadow-2xs">
                              {dec.decision?.replace('_', ' ')}
                            </span>
                            <span className="text-xs opacity-75 font-mono">
                              {dec.createdAt ? new Date(dec.createdAt).toLocaleString() : ''}
                            </span>
                          </div>

                          {dec.comment && (
                            <p className="text-sm font-medium mt-1.5">
                              "{dec.comment}"
                            </p>
                          )}

                          {dec.overrideReason && (
                            <p className="text-xs mt-2 p-2 bg-white/60 rounded border border-amber-200/50">
                              <span className="font-bold uppercase tracking-wider text-[10px] block mb-0.5">Override Justification:</span>
                              {dec.overrideReason}
                            </p>
                          )}

                          <div className="mt-2.5 text-xs opacity-80 flex items-center justify-between">
                            <span>Reviewed by <strong>{dec.reviewerName || 'Independent Reviewer'}</strong></span>
                            {dec.decision === 'CHANGES_REQUESTED' && (
                              <button
                                type="button"
                                onClick={() => {
                                  setRevisionRawText(versionObj.rawText || '');
                                  setShowRevisionModal(true);
                                }}
                                className="inline-flex items-center space-x-1.5 bg-amber-700 hover:bg-amber-800 text-white px-3 py-1 rounded-lg text-xs font-semibold shadow-xs transition-colors"
                              >
                                <GitBranch className="w-3.5 h-3.5" />
                                <span>Create Revision (v{(subObj.currentVersionNumber || versionObj.versionNumber || 1) + 1})</span>
                              </button>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl text-slate-600 text-xs flex items-center space-x-3">
                      <Clock className="w-5 h-5 text-slate-400 flex-shrink-0" />
                      <div>
                        <span className="font-semibold block text-slate-800">Pending Independent Review</span>
                        <span>Your submission is queued for compliance verification. When the reviewer evaluates your copy, their decision and notes will appear here.</span>
                      </div>
                    </div>
                  )}
                </div>
              ) : isCreatorOfSubmission ? (
                /* REVIEWER/ADMIN VIEW WHO AUTHORED THE SUBMISSION (Separation of duties: Cannot review own copy) */
                <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <div className="flex items-center space-x-2">
                      <ShieldAlert className="w-5 h-5 text-amber-600" />
                      <h3 className="font-bold text-slate-900 text-base">Independent Review Required</h3>
                    </div>
                    <span className="text-xs text-slate-500 font-mono">
                      Separation of Duties
                    </span>
                  </div>

                  <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-start space-x-3">
                    <Ban className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="font-bold text-sm">Separation of Duties Policy Enforced</p>
                      <p className="mt-1 leading-relaxed text-amber-800">
                        You authored this submission ({user?.email}). Under VeriWeave compliance governance, content creators cannot approve, request changes, or reject their own submissions. Another reviewer or administrator must conduct the independent evaluation.
                      </p>
                    </div>
                  </div>

                  {submissionDecisions.length > 0 && (
                    <div className="pt-2">
                      <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block mb-2">Previous Decisions:</span>
                      <div className="space-y-2">
                        {submissionDecisions.map((dec) => (
                          <div key={dec.id} className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs flex items-center justify-between">
                            <div>
                              <span className="font-bold text-slate-800 font-mono">{dec.decision}</span>
                              <span className="text-slate-500 ml-2">by {dec.reviewerName}</span>
                              {dec.comment && <p className="text-slate-600 mt-1">"{dec.comment}"</p>}
                            </div>
                            <span className="text-[11px] text-slate-400 font-mono">
                              {new Date(dec.createdAt).toLocaleDateString()}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                /* REVIEWER/ADMIN VIEW (Independent Reviewer Decision Gate) */
                <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <div className="flex items-center space-x-2">
                      <UserCheck className="w-5 h-5 text-teal-600" />
                      <h3 className="font-bold text-slate-900 text-base">Reviewer Decision Gate</h3>
                    </div>
                    <span className="text-xs text-slate-500">
                      Authority: REVIEWER or ADMIN • Enforces Separation of Duties
                    </span>
                  </div>

                  {subObj.status === 'DRAFT' ? (
                    <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-start space-x-3">
                      <Clock className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
                      <div>
                        <p className="font-bold text-sm">Submission In Draft Status</p>
                        <p className="mt-1 leading-relaxed text-amber-800">
                          This submission is currently in DRAFT status. Automated compliance analysis must be run and verified before an independent review decision can be recorded.
                        </p>
                      </div>
                    </div>
                  ) : subObj.status === 'APPROVED' ? (
                    <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-900 flex items-start space-x-3">
                      <CheckCircle className="w-5 h-5 text-emerald-600 flex-shrink-0 mt-0.5" />
                      <div>
                        <p className="font-bold text-sm">Submission Finalized & Approved</p>
                        <p className="mt-1 leading-relaxed text-emerald-800">
                          This submission has been formally APPROVED for brand and regulatory compliance. The current version is locked; no further review decisions can be submitted on this version.
                        </p>
                      </div>
                    </div>
                  ) : subObj.status === 'REJECTED' ? (
                    <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-900 flex items-start space-x-3">
                      <XCircle className="w-5 h-5 text-rose-600 flex-shrink-0 mt-0.5" />
                      <div>
                        <p className="font-bold text-sm">Submission Rejected</p>
                        <p className="mt-1 leading-relaxed text-rose-800">
                          This submission was REJECTED during compliance review. Awaiting a revised copy submission from the content creator.
                        </p>
                      </div>
                    </div>
                  ) : subObj.status === 'CHANGES_REQUESTED' ? (
                    <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-start space-x-3">
                      <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
                      <div>
                        <p className="font-bold text-sm">Changes Requested</p>
                        <p className="mt-1 leading-relaxed text-amber-800">
                          Changes have been requested on this version. Awaiting a new revision from the content creator before further review.
                        </p>
                      </div>
                    </div>
                  ) : (
                    <form onSubmit={handleRecordDecision} className="space-y-4">
                      {/* Decision Selector */}
                      <div className="flex items-center space-x-3">
                        {[
                          { val: 'APPROVE', label: 'Approve Submission', color: 'emerald' },
                          { val: 'REQUEST_CHANGES', label: 'Request Changes', color: 'amber' },
                          { val: 'REJECT', label: 'Reject Submission', color: 'rose' }
                        ].map((d) => (
                          <button
                            key={d.val}
                            type="button"
                            onClick={() => setReviewDecision(d.val)}
                            className={`px-4 py-2 rounded-lg text-sm font-semibold border transition-all ${
                              reviewDecision === d.val
                                ? d.color === 'emerald'
                                  ? 'bg-emerald-600 text-white border-emerald-600 shadow-xs'
                                  : d.color === 'amber'
                                  ? 'bg-amber-600 text-white border-amber-600 shadow-xs'
                                  : 'bg-rose-600 text-white border-rose-600 shadow-xs'
                                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                            }`}
                          >
                            {d.label}
                          </button>
                        ))}
                      </div>

                      {/* Mandatory Override Justification if Approving with Unresolved Critical Findings */}
                      {reviewDecision === 'APPROVE' && hasUnresolvedCriticalFindings && (
                        <div className="p-4 bg-rose-50 border border-rose-200 rounded-lg space-y-2">
                          <div className="flex items-center space-x-2 text-rose-800 font-semibold text-xs">
                            <AlertOctagon className="w-4 h-4 text-rose-600" />
                            <span>MANDATORY OVERRIDE JUSTIFICATION REQUIRED</span>
                          </div>
                          <p className="text-xs text-rose-700">
                            This submission has unresolved CRITICAL findings. To approve, you must record a formal, audited business justification.
                          </p>
                          <textarea
                            required
                            rows={2}
                            placeholder="State legal / brand director justification for overriding critical compliance findings..."
                            value={overrideReason}
                            onChange={(e) => setOverrideReason(e.target.value)}
                            className="w-full text-xs border border-rose-300 rounded p-2 focus:ring-1 focus:ring-rose-500 focus:outline-none"
                          />
                        </div>
                      )}

                      {/* Review Comments */}
                      <div>
                        <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                          Review Comments {(reviewDecision === 'REJECT' || reviewDecision === 'REQUEST_CHANGES') && <span className="text-rose-600">*</span>}
                        </label>
                        <textarea
                          rows={2}
                          required={reviewDecision === 'REJECT' || reviewDecision === 'REQUEST_CHANGES'}
                          placeholder={reviewDecision === 'REQUEST_CHANGES' ? "Provide specific instructions for the creator to address before re-submitting..." : "Add compliance feedback or notes..."}
                          value={reviewComments}
                          onChange={(e) => setReviewComments(e.target.value)}
                          className="w-full text-sm border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                        />
                      </div>

                      <div className="flex items-center justify-between pt-1">
                        <span className="text-xs text-slate-400">
                          Recording as: <strong className="text-slate-600">{user?.fullName || user?.email}</strong>
                        </span>
                        <button
                          type="submit"
                          disabled={actionLoading}
                          className="inline-flex items-center space-x-2 bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white px-5 py-2.5 rounded-lg text-sm font-semibold transition-colors shadow-xs"
                        >
                          <UserCheck className="w-4 h-4" />
                          <span>{actionLoading ? 'Recording...' : 'Commit Review Decision'}</span>
                        </button>
                      </div>
                    </form>
                  )}

                  {/* Previous decisions history for reviewers */}
                  {submissionDecisions.length > 0 && (
                    <div className="pt-2 border-t border-slate-100">
                      <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block mb-2">Previous Decisions:</span>
                      <div className="space-y-2">
                        {submissionDecisions.map((dec) => (
                          <div key={dec.id} className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs flex items-center justify-between">
                            <div>
                              <span className="font-bold text-slate-800 font-mono">{dec.decision}</span>
                              <span className="text-slate-500 ml-2">by {dec.reviewerName}</span>
                              {dec.comment && <p className="text-slate-600 mt-1">"{dec.comment}"</p>}
                              {dec.overrideReason && <p className="text-rose-600 mt-0.5 font-medium">Override: "{dec.overrideReason}"</p>}
                            </div>
                            <span className="text-[11px] text-slate-400 font-mono">
                              {new Date(dec.createdAt).toLocaleDateString()}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </>
          ) : (
            <div className="bg-white border border-slate-200 rounded-xl p-12 text-center text-slate-400">
              Select a submission from the queue or click "New Copy Submission" to start.
            </div>
          )}
        </div>
      </div>

      {/* Modal: New Copy Submission */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl max-w-xl w-full p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-lg font-bold text-slate-900">New Marketing Copy Submission</h3>
              <button
                type="button"
                onClick={loadSampleNonCompliantCopy}
                className="inline-flex items-center space-x-1.5 text-xs text-teal-700 bg-teal-50 hover:bg-teal-100 px-2.5 py-1 rounded font-medium transition-colors"
              >
                <Sparkles className="w-3.5 h-3.5" />
                <span>Load Sample Non-Compliant Copy</span>
              </button>
            </div>

            <form onSubmit={handleCreateSubmission} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Submission Title
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. VeriFlora Spring Radiance Botanical Elixir Ad"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Campaign Name
                </label>
                <input
                  type="text"
                  placeholder="e.g. Spring Launch 2026"
                  value={newCampaign}
                  onChange={(e) => setNewCampaign(e.target.value)}
                  className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Raw Copy Content
                </label>
                <textarea
                  required
                  rows={5}
                  placeholder="Paste marketing copy here for passage segmentation and evidence-linked governance check..."
                  value={newRawText}
                  onChange={(e) => setNewRawText(e.target.value)}
                  className="w-full text-sm border border-slate-300 rounded-lg p-3 font-mono focus:ring-1 focus:ring-teal-500 focus:outline-none leading-relaxed"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white px-5 py-2 rounded-lg text-sm font-semibold transition-colors shadow-xs"
                >
                  {actionLoading ? 'Submitting...' : 'Submit Copy'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Create Revision */}
      {showRevisionModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl max-w-xl w-full p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center space-x-2">
                <GitBranch className="w-5 h-5 text-teal-600" />
                <h3 className="text-lg font-bold text-slate-900">Create Copy Revision</h3>
              </div>
              <span className="text-xs bg-teal-50 text-teal-700 font-bold px-2.5 py-0.5 rounded">
                Next Version: v{(subObj.currentVersionNumber || versionObj.versionNumber || 1) + 1}
              </span>
            </div>

            <p className="text-xs text-slate-500">
              Update the copy text to address reviewer feedback or replace prohibited phrasing with approved alternatives. A new immutable content version will be created in status DRAFT.
            </p>

            <form onSubmit={handleCreateRevision} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Revised Copy Content
                </label>
                <textarea
                  required
                  rows={6}
                  value={revisionRawText}
                  onChange={(e) => setRevisionRawText(e.target.value)}
                  placeholder="Enter revised marketing copy text..."
                  className="w-full text-sm border border-slate-300 rounded-lg p-3 font-mono focus:ring-1 focus:ring-teal-500 focus:outline-none leading-relaxed"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowRevisionModal(false)}
                  className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white px-5 py-2 rounded-lg text-sm font-semibold transition-colors shadow-xs"
                >
                  {actionLoading ? 'Creating Revision...' : 'Save & Publish Revision'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Guideline-to-Decision Traceability Explorer */}
      {traceFindingId && (
        <TraceabilityModal
          workspaceId={workspaceId}
          submissionId={selectedSubmissionId}
          findingId={traceFindingId}
          onClose={() => setTraceFindingId(null)}
        />
      )}
    </div>
  );
}
