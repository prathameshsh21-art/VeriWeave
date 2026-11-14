import React, { useEffect, useState } from 'react';
import {
  X,
  ShieldCheck,
  FileText,
  AlertTriangle,
  CheckCircle2,
  GitBranch,
  User,
  Clock,
  Hash,
  ExternalLink,
  BookOpen,
  ArrowRight,
  ShieldAlert,
  HelpCircle,
  FileCheck
} from 'lucide-react';
import axiosClient from '../api/axiosClient';

export default function TraceabilityModal({ workspaceId, submissionId, findingId, onClose }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let isMounted = true;
    const fetchTrace = async () => {
      try {
        setLoading(true);
        setError('');
        const res = await axiosClient.get(`/workspaces/${workspaceId}/submissions/${submissionId}/findings/${findingId}/trace`);
        if (isMounted) {
          setData(res.data.data);
        }
      } catch (err) {
        if (isMounted) {
          setError(err.response?.data?.message || err.message || 'Failed to load traceability details');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    if (workspaceId && submissionId && findingId) {
      fetchTrace();
    }
    return () => { isMounted = false; };
  }, [workspaceId, submissionId, findingId]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="relative w-full max-w-4xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden my-8 max-h-[90vh] flex flex-col">
        {/* Modal Header */}
        <div className="px-6 py-4 bg-gradient-to-r from-slate-900 via-slate-800 to-teal-900 text-white flex items-center justify-between flex-shrink-0">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-lg bg-teal-500/20 border border-teal-400/30 flex items-center justify-center text-teal-300">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="text-base font-bold text-white tracking-wide">
                  Guideline-to-Decision Traceability Explorer
                </h3>
                <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-teal-500/30 text-teal-200 border border-teal-400/40 rounded-full">
                  Verified Provenance
                </span>
              </div>
              <p className="text-xs text-slate-300">
                End-to-end audit chain from submitted sentence to approved organizational rule and reviewer decision
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1 text-slate-800 text-sm">
          {loading && (
            <div className="py-16 text-center">
              <div className="w-10 h-10 border-4 border-teal-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
              <p className="text-sm font-medium text-slate-600">Reconstructing cryptographic provenance chain...</p>
            </div>
          )}

          {error && (
            <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 flex items-start space-x-3">
              <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-sm">Failed to load provenance chain</p>
                <p className="text-xs mt-1 text-red-600">{error}</p>
              </div>
            </div>
          )}

          {data && (
            <div className="space-y-6">
              {/* Summary Bar */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-xs">
                <div>
                  <span className="text-slate-400 block font-medium">Submission</span>
                  <span className="font-semibold text-slate-900 truncate block">{data.submissionTitle}</span>
                  <span className="text-[11px] text-slate-500">v{data.contentVersionNumber} • by {data.submissionAuthor}</span>
                </div>
                <div>
                  <span className="text-slate-400 block font-medium">Rule Violated</span>
                  <span className="font-semibold text-teal-700 block font-mono">[{data.ruleCode}]</span>
                  <span className="text-[11px] text-slate-500">{data.category}</span>
                </div>
                <div>
                  <span className="text-slate-400 block font-medium">Classification</span>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold mt-0.5 ${
                    data.classification === 'VERIFIED'
                      ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                      : 'bg-amber-100 text-amber-800 border border-amber-200'
                  }`}>
                    {data.classification}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400 block font-medium">Severity</span>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold mt-0.5 ${
                    data.severity === 'HIGH'
                      ? 'bg-red-100 text-red-800 border border-red-200'
                      : data.severity === 'MEDIUM'
                      ? 'bg-amber-100 text-amber-800 border border-amber-200'
                      : 'bg-slate-100 text-slate-700 border border-slate-200'
                  }`}>
                    {data.severity}
                  </span>
                </div>
              </div>

              {/* Traceability Timeline */}
              <div className="relative pl-6 space-y-6 before:absolute before:left-[11px] before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-200">
                
                {/* Step 1: Submitted Sentence */}
                <div className="relative">
                  <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-blue-600 text-white flex items-center justify-center text-[11px] font-bold shadow-sm">
                    1
                  </div>
                  <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-blue-700 flex items-center space-x-1.5">
                        <FileText className="w-3.5 h-3.5" />
                        <span>Submitted Copy & Character Offset</span>
                      </span>
                      <span className="text-[11px] font-mono text-slate-500 bg-slate-100 px-2 py-0.5 rounded">
                        Chars [{data.passageStart}..{data.passageEnd}]
                      </span>
                    </div>
                    <div className="p-3 bg-amber-50/70 border border-amber-200/80 rounded-lg text-slate-900 font-mono text-xs leading-relaxed">
                      "{data.passageQuote}"
                    </div>
                    {data.rawText && (
                      <p className="mt-2 text-[11px] text-slate-500">
                        Context excerpt from <span className="font-medium text-slate-700">v{data.contentVersionNumber}</span> submitted on {new Date(data.submissionCreatedAt).toLocaleString()}
                      </p>
                    )}
                  </div>
                </div>

                {/* Step 2: Deterministic Finding Verification */}
                <div className="relative">
                  <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-teal-600 text-white flex items-center justify-center text-[11px] font-bold shadow-sm">
                    2
                  </div>
                  <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-teal-700 flex items-center space-x-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Deterministic Finding & Classification</span>
                      </span>
                      <span className="text-[11px] text-teal-700 bg-teal-50 border border-teal-200 px-2 py-0.5 rounded font-medium">
                        Confidence: {data.confidence}
                      </span>
                    </div>
                    <p className="text-xs text-slate-700 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-200">
                      {data.explanation}
                    </p>
                    <div className="mt-2.5 flex items-center space-x-2 text-[11px] text-slate-500">
                      <ShieldCheck className="w-3.5 h-3.5 text-teal-600" />
                      <span>The model proposed this candidate; deterministic verification confirmed rule <strong className="text-slate-800">{data.ruleCode}</strong> constraint match.</span>
                    </div>
                  </div>
                </div>

                {/* Step 3: Approved Knowledge Guideline & Evidence */}
                <div className="relative">
                  <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-indigo-600 text-white flex items-center justify-center text-[11px] font-bold shadow-sm">
                    3
                  </div>
                  <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-indigo-700 flex items-center space-x-1.5">
                        <BookOpen className="w-3.5 h-3.5" />
                        <span>Approved Organizational Guideline Provenance</span>
                      </span>
                      {data.sourceStatus && (
                        <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">
                          {data.sourceStatus}
                        </span>
                      )}
                    </div>

                    <div className="space-y-2 text-xs">
                      <div className="p-3 bg-indigo-50/50 border border-indigo-100 rounded-lg">
                        <div className="flex items-center justify-between text-[11px] text-slate-600 mb-1">
                          <span className="font-semibold text-indigo-900">{data.sourceTitle || 'Guideline Document'}</span>
                          <span className="font-mono text-slate-500">{data.pageOrSectionReference || 'General Section'}</span>
                        </div>
                        <p className="font-mono text-slate-800 text-xs">"{data.ruleText}"</p>
                      </div>

                      {data.evidenceQuote && (
                        <div className="p-2.5 bg-slate-50 border border-slate-200 rounded-lg">
                          <span className="text-[10px] uppercase font-bold tracking-wider text-slate-500 block mb-0.5">Supporting Source Evidence Snippet:</span>
                          <p className="italic text-slate-700 text-xs font-mono">"...{data.evidenceQuote}..."</p>
                        </div>
                      )}

                      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-400 pt-1 font-mono">
                        <span>Source Version: v{data.sourceVersionNumber || 1}</span>
                        {data.sourceVersionChecksum && (
                          <span title={data.sourceVersionChecksum}>
                            SHA-256: {data.sourceVersionChecksum.substring(0, 16)}...
                          </span>
                        )}
                        <span>Type: {data.sourceType || 'POLICY'}</span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Step 4: Suggested Actionable Edit */}
                <div className="relative">
                  <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-emerald-600 text-white flex items-center justify-center text-[11px] font-bold shadow-sm">
                    4
                  </div>
                  <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-emerald-700 flex items-center space-x-1.5">
                        <FileCheck className="w-3.5 h-3.5" />
                        <span>Actionable Guideline-Compliant Suggestion</span>
                      </span>
                    </div>
                    <div className="p-3 bg-emerald-50/70 border border-emerald-200/80 rounded-lg text-emerald-950 font-mono text-xs">
                      {data.suggestedEdit || 'No alternative suggested'}
                    </div>
                  </div>
                </div>

                {/* Step 5: Creator Response */}
                <div className="relative">
                  <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-violet-600 text-white flex items-center justify-center text-[11px] font-bold shadow-sm">
                    5
                  </div>
                  <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-violet-700 flex items-center space-x-1.5">
                        <User className="w-3.5 h-3.5" />
                        <span>Creator Response & Action</span>
                      </span>
                      {data.creatorAction ? (
                        <span className={`text-[11px] font-bold px-2 py-0.5 rounded uppercase tracking-wider ${
                          data.creatorAction === 'ACCEPTED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : data.creatorAction === 'MODIFIED'
                            ? 'bg-blue-100 text-blue-800'
                            : 'bg-amber-100 text-amber-800'
                        }`}>
                          {data.creatorAction}
                        </span>
                      ) : (
                        <span className="text-[11px] text-slate-400 italic">Pending creator response</span>
                      )}
                    </div>
                    {data.creatorAction ? (
                      <div className="space-y-1.5 text-xs">
                        {data.creatorReason && (
                          <p className="text-slate-700 bg-slate-50 p-2.5 rounded-lg border border-slate-200">
                            <span className="font-semibold text-slate-900">Reason:</span> {data.creatorReason}
                          </p>
                        )}
                        {data.creatorModifiedText && (
                          <div className="p-2.5 bg-blue-50 border border-blue-200 rounded-lg">
                            <span className="text-[10px] uppercase font-bold text-blue-700 block mb-0.5">Proposed Revision:</span>
                            <p className="font-mono text-blue-950 text-xs">"{data.creatorModifiedText}"</p>
                          </div>
                        )}
                        <p className="text-[11px] text-slate-400 pt-1">
                          Responded by <span className="font-medium text-slate-600">{data.creatorRespondedBy || 'Creator'}</span> on {data.creatorRespondedAt ? new Date(data.creatorRespondedAt).toLocaleString() : 'N/A'}
                        </p>
                      </div>
                    ) : (
                      <p className="text-xs text-slate-500 italic">
                        The content creator has not yet submitted an explicit response (Accept / Modify / Reject) for this finding.
                      </p>
                    )}
                  </div>
                </div>

                {/* Step 6: Reviewer Decision (Separation of Duties) */}
                <div className="relative">
                  <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-purple-600 text-white flex items-center justify-center text-[11px] font-bold shadow-sm">
                    6
                  </div>
                  <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-purple-700 flex items-center space-x-1.5">
                        <ShieldAlert className="w-3.5 h-3.5" />
                        <span>Independent Reviewer Decision (Separation of Duties)</span>
                      </span>
                      {data.reviewerDecision ? (
                        <span className={`text-[11px] font-bold px-2 py-0.5 rounded uppercase tracking-wider ${
                          data.reviewerDecision === 'APPROVED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : data.reviewerDecision === 'CHANGES_REQUESTED'
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-red-100 text-red-800'
                        }`}>
                          {data.reviewerDecision.replace('_', ' ')}
                        </span>
                      ) : (
                        <span className="text-[11px] text-slate-400 italic">Pending review</span>
                      )}
                    </div>
                    {data.reviewerDecision ? (
                      <div className="space-y-1.5 text-xs">
                        {data.reviewerComment && (
                          <p className="text-slate-700 bg-slate-50 p-2.5 rounded-lg border border-slate-200">
                            <span className="font-semibold text-slate-900">Reviewer Note:</span> {data.reviewerComment}
                          </p>
                        )}
                        {data.overrideReason && (
                          <div className="p-2.5 bg-amber-50 border border-amber-200 rounded-lg">
                            <span className="text-[10px] uppercase font-bold text-amber-800 block mb-0.5">Override Justification:</span>
                            <p className="text-amber-950 text-xs">{data.overrideReason}</p>
                          </div>
                        )}
                        <p className="text-[11px] text-slate-400 pt-1">
                          Decided by <span className="font-medium text-slate-600">{data.reviewerName || 'Independent Reviewer'}</span> on {data.decidedAt ? new Date(data.decidedAt).toLocaleString() : 'N/A'}
                        </p>
                      </div>
                    ) : (
                      <p className="text-xs text-slate-500 italic">
                        Independent compliance reviewer has not yet recorded a final approval or request for changes.
                      </p>
                    )}
                  </div>
                </div>

                {/* Step 7: Immutable Audit Trail */}
                <div className="relative">
                  <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-slate-700 text-white flex items-center justify-center text-[11px] font-bold shadow-sm">
                    7
                  </div>
                  <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center space-x-1.5">
                        <Clock className="w-3.5 h-3.5" />
                        <span>Immutable Audit Trail Records</span>
                      </span>
                      <span className="text-[11px] font-mono text-slate-400">
                        {data.auditTrail ? `${data.auditTrail.length} recorded events` : '0 events'}
                      </span>
                    </div>

                    {data.auditTrail && data.auditTrail.length > 0 ? (
                      <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                        {data.auditTrail.map((ev) => (
                          <div key={ev.id} className="p-2 bg-slate-50 border border-slate-200 rounded text-xs flex items-center justify-between">
                            <div>
                              <span className="font-semibold text-slate-800 font-mono text-[11px]">{ev.action}</span>
                              <span className="text-slate-500 text-[11px] block">
                                by {ev.actor?.fullName || ev.actor?.email || 'System'} • {ev.entityType} #{ev.entityId}
                              </span>
                            </div>
                            <span className="text-[10px] text-slate-400 font-mono">
                              {new Date(ev.createdAt || ev.timestamp).toLocaleTimeString()}
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-slate-500 italic">
                        All lifecycle state transitions for this finding are recorded directly in the append-only audit log.
                      </p>
                    )}
                  </div>
                </div>

              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-3.5 bg-slate-50 border-t border-slate-200 flex items-center justify-between flex-shrink-0">
          <div className="flex items-center space-x-2 text-xs text-slate-500">
            <CheckCircle2 className="w-4 h-4 text-teal-600 flex-shrink-0" />
            <span>Cryptographically anchored in MySQL 8 with Hibernate immutability triggers.</span>
          </div>
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-slate-700 bg-white hover:bg-slate-100 border border-slate-300 rounded-lg shadow-sm transition-colors"
          >
            Close Explorer
          </button>
        </div>
      </div>
    </div>
  );
}
