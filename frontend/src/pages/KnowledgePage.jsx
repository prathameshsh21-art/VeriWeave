import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import axiosClient from '../api/axiosClient';
import { 
  BookOpen, 
  Plus, 
  FileUp, 
  CheckCircle, 
  AlertCircle, 
  ShieldCheck, 
  Clock, 
  Trash2, 
  Edit3, 
  Hash, 
  Lock, 
  FileText,
  Search,
  RefreshCw,
  ExternalLink,
  Archive,
  GitBranch
} from 'lucide-react';

const categoryColors = {
  TONE: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  TERMINOLOGY: 'bg-blue-50 text-blue-700 border-blue-200',
  MESSAGING: 'bg-sky-50 text-sky-700 border-sky-200',
  FACT: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  CLAIM: 'bg-amber-50 text-amber-700 border-amber-200',
  VISUAL: 'bg-fuchsia-50 text-fuchsia-700 border-fuchsia-200',
  ACCESSIBILITY: 'bg-teal-50 text-teal-700 border-teal-200',
};

const severityColors = {
  CRITICAL: 'bg-rose-50 text-rose-700 border-rose-200',
  MAJOR: 'bg-amber-50 text-amber-700 border-amber-200',
  MINOR: 'bg-blue-50 text-blue-700 border-blue-200',
  ADVISORY: 'bg-slate-50 text-slate-700 border-slate-200',
};

const statusColors = {
  CURRENT: 'bg-emerald-100 text-emerald-800 border-emerald-300',
  DRAFT: 'bg-amber-100 text-amber-800 border-amber-300',
  SUPERSEDED: 'bg-slate-100 text-slate-600 border-slate-300',
};

export default function KnowledgePage() {
  const { activeWorkspace } = useAuth();
  const [sources, setSources] = useState([]);
  const [selectedSource, setSelectedSource] = useState(null);
  const [versions, setVersions] = useState([]);
  const [selectedVersion, setSelectedVersion] = useState(null);
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Modals
  const [showCreateSourceModal, setShowCreateSourceModal] = useState(false);
  const [showAddEntryModal, setShowAddEntryModal] = useState(false);
  const [showPdfModal, setShowPdfModal] = useState(false);

  // Forms
  const [newSourceTitle, setNewSourceTitle] = useState('');
  const [newSourceType, setNewSourceType] = useState('GUIDELINE');
  const [newEntryCode, setNewEntryCode] = useState('');
  const [newEntryCategory, setNewEntryCategory] = useState('TERMINOLOGY');
  const [newEntrySeverity, setNewEntrySeverity] = useState('MAJOR');
  const [newEntryText, setNewEntryText] = useState('');
  const [newEntryRef, setNewEntryRef] = useState('');
  const [pdfFile, setPdfFile] = useState(null);

  const [isRefreshing, setIsRefreshing] = useState(false);

  const workspaceId = activeWorkspace?.id;
  const isAdmin = activeWorkspace?.role === 'ADMIN';

  // Load Sources
  const fetchSources = async (isManualRefresh = false) => {
    if (!workspaceId) return;
    if (isManualRefresh && isRefreshing) return;
    try {
      if (isManualRefresh) {
        setIsRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);
      const res = await axiosClient.get(`/workspaces/${workspaceId}/sources`);
      if (res.data?.success) {
        const freshSources = res.data.data || [];
        setSources(freshSources);
        if (freshSources.length > 0) {
          // If already selected, preserve selection if still exists, otherwise choose first
          setSelectedSource(prev => {
            if (prev && freshSources.some(s => s.id === prev.id)) {
              return freshSources.find(s => s.id === prev.id);
            }
            return freshSources[0];
          });
        } else {
          setSelectedSource(null);
        }
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load knowledge sources');
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchSources();
  }, [workspaceId]);

  // Load Versions when source changes
  const fetchVersions = async (sourceId) => {
    if (!workspaceId || !sourceId) return;
    try {
      const res = await axiosClient.get(`/workspaces/${workspaceId}/sources/${sourceId}/versions`);
      if (res.data?.success) {
        const vList = res.data.data || [];
        setVersions(vList);
        const curr = vList.find(v => v.status === 'CURRENT') || vList[0] || null;
        setSelectedVersion(curr);
      }
    } catch (err) {
      console.error('Failed to load versions', err);
    }
  };

  useEffect(() => {
    if (selectedSource) {
      fetchVersions(selectedSource.id);
    } else {
      setVersions([]);
      setSelectedVersion(null);
    }
  }, [selectedSource]);

  // Load Entries when version changes
  const fetchEntries = async (sourceId, versionId) => {
    if (!workspaceId || !sourceId || !versionId) return;
    try {
      setLoading(true);
      const res = await axiosClient.get(`/workspaces/${workspaceId}/sources/${sourceId}/versions/${versionId}/entries`);
      if (res.data?.success) {
        setEntries(res.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load entries', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (selectedSource && selectedVersion) {
      fetchEntries(selectedSource.id, selectedVersion.id);
    } else {
      setEntries([]);
    }
  }, [selectedVersion]);

  // Create Source
  const handleCreateSource = async (e) => {
    e.preventDefault();
    if (!newSourceTitle.trim()) return;
    try {
      const res = await axiosClient.post(`/workspaces/${workspaceId}/sources`, {
        title: newSourceTitle,
        type: newSourceType
      });
      if (res.data?.success) {
        setShowCreateSourceModal(false);
        setNewSourceTitle('');
        setSuccessMsg(`Source "${res.data.data.title}" created with initial DRAFT version`);
        await fetchSources();
        setSelectedSource(res.data.data);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create source');
    }
  };

  // Add Entry
  const handleAddEntry = async (e) => {
    e.preventDefault();
    if (!newEntryCode.trim() || !newEntryText.trim()) return;
    try {
      const res = await axiosClient.post(
        `/workspaces/${workspaceId}/sources/${selectedSource.id}/versions/${selectedVersion.id}/entries`,
        {
          code: newEntryCode.trim(),
          category: newEntryCategory,
          defaultSeverity: newEntrySeverity,
          text: newEntryText.trim(),
          pageOrSectionReference: newEntryRef.trim() || null
        }
      );
      if (res.data?.success) {
        setShowAddEntryModal(false);
        setNewEntryCode('');
        setNewEntryText('');
        setNewEntryRef('');
        setSuccessMsg('Candidate knowledge entry added to DRAFT version');
        await fetchEntries(selectedSource.id, selectedVersion.id);
        await fetchVersions(selectedSource.id);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to add entry');
    }
  };

  // Delete Entry
  const handleDeleteEntry = async (entryId) => {
    if (!window.confirm('Delete this candidate entry from draft?')) return;
    try {
      await axiosClient.delete(
        `/workspaces/${workspaceId}/sources/${selectedSource.id}/versions/${selectedVersion.id}/entries/${entryId}`
      );
      setSuccessMsg('Entry deleted from draft');
      await fetchEntries(selectedSource.id, selectedVersion.id);
      await fetchVersions(selectedSource.id);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete entry');
    }
  };

  // Approve Version as CURRENT
  const handleApproveVersion = async () => {
    if (!window.confirm(`Approve Version v${selectedVersion.versionNumber} as CURRENT? This will compute canonical SHA-256 and supersede any prior version.`)) return;
    try {
      const res = await axiosClient.post(
        `/workspaces/${workspaceId}/sources/${selectedSource.id}/versions/${selectedVersion.id}/approve`
      );
      if (res.data?.success) {
        setSuccessMsg(`Version v${res.data.data.versionNumber} is now approved as CURRENT! Canonical Checksum: ${res.data.data.checksum?.substring(0, 16)}...`);
        await fetchVersions(selectedSource.id);
        await fetchSources();
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Approval failed. Only ADMIN can approve source versions.');
    }
  };

  // Create New Draft Version (Evolve Guideline from Current)
  const handleCreateDraftVersion = async () => {
    if (!selectedSource || !selectedVersion) return;
    try {
      setLoading(true);
      const res = await axiosClient.post(
        `/workspaces/${workspaceId}/sources/${selectedSource.id}/versions?cloneFromVersionId=${selectedVersion.id}`
      );
      if (res.data?.success) {
        setSuccessMsg(`New DRAFT version v${res.data.data.versionNumber} created from v${selectedVersion.versionNumber}! You can now edit candidate rules.`);
        await fetchVersions(selectedSource.id);
        setSelectedVersion(res.data.data);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create new draft version');
    } finally {
      setLoading(false);
    }
  };

  // Archive Source
  const handleArchiveSource = async () => {
    if (!selectedSource) return;
    if (!window.confirm(`Are you sure you want to archive "${selectedSource.title}"? It will no longer be retrieved during copy analysis.`)) return;
    try {
      setLoading(true);
      await axiosClient.delete(`/workspaces/${workspaceId}/sources/${selectedSource.id}`);
      setSuccessMsg(`Knowledge source "${selectedSource.title}" archived successfully.`);
      setSelectedSource(null);
      await fetchSources();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to archive source');
    } finally {
      setLoading(false);
    }
  };

  // PDF Extraction
  const handlePdfUpload = async (e) => {
    e.preventDefault();
    if (!pdfFile) {
      setError('Please select a PDF file to import.');
      return;
    }
    if (!pdfFile.name.toLowerCase().endsWith('.pdf')) {
      setError('Unsupported file format: Only PDF documents (.pdf) are supported for knowledge extraction.');
      return;
    }
    const formData = new FormData();
    formData.append('file', pdfFile);

    try {
      setLoading(true);
      const res = await axiosClient.post(
        `/workspaces/${workspaceId}/sources/${selectedSource.id}/versions/${selectedVersion.id}/import-pdf`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      );
      if (res.data?.success) {
        setShowPdfModal(false);
        setPdfFile(null);
        setSuccessMsg(`PDF extracted! Added ${res.data.data?.length || 0} candidate entries to DRAFT v${selectedVersion.versionNumber}.`);
        await fetchEntries(selectedSource.id, selectedVersion.id);
        await fetchVersions(selectedSource.id);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'PDF extraction failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Page Title & Actions */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-3">
            <div className="p-2 bg-teal-50 border border-teal-200 rounded-lg text-teal-700">
              <BookOpen className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-slate-900">Knowledge Base Governance</h1>
              <p className="text-xs text-slate-500">
                Ground truth repository: guidelines, approved terminology, claims register, and asset rules
              </p>
            </div>
          </div>
        </div>

        {isAdmin && (
          <div className="flex items-center space-x-3">
            <button
              onClick={() => setShowCreateSourceModal(true)}
              className="inline-flex items-center space-x-2 bg-teal-600 hover:bg-teal-700 text-white px-3.5 py-2 rounded-lg text-sm font-medium shadow-sm transition-colors"
            >
              <Plus className="w-4 h-4" />
              <span>New Source</span>
            </button>
          </div>
        )}
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
            <AlertCircle className="w-4 h-4 text-rose-600 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-rose-600 hover:text-rose-800 text-xs font-bold">Dismiss</button>
        </div>
      )}

      {/* Main Grid: Sources List (Left) + Source Detail & Entries (Right) */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Left: Sources List */}
        <div className="lg:col-span-1 bg-white border border-slate-200 rounded-xl p-4 shadow-sm space-y-3">
          <div className="flex items-center justify-between border-b border-slate-100 pb-2">
            <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Sources ({sources.length})</h2>
            <button
              onClick={() => fetchSources(true)}
              disabled={isRefreshing || loading}
              title="Refresh Sources"
              className="text-slate-400 hover:text-slate-600 disabled:opacity-40 p-1 rounded hover:bg-slate-100 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-teal-600' : ''}`} />
            </button>
          </div>

          <div className="space-y-1.5">
            {sources.map((s) => {
              const isSelected = selectedSource?.id === s.id;
              return (
                <button
                  key={s.id}
                  onClick={() => setSelectedSource(s)}
                  className={`w-full text-left p-3 rounded-lg border transition-all ${
                    isSelected
                      ? 'bg-teal-50/70 border-teal-300 text-teal-900 shadow-xs'
                      : 'bg-white border-slate-200/80 text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-xs font-mono font-medium text-slate-500">{s.type}</span>
                    {s.currentVersionId ? (
                      <span className="text-[10px] bg-emerald-100 text-emerald-800 px-1.5 py-0.2 rounded font-medium">Approved</span>
                    ) : (
                      <span className="text-[10px] bg-amber-100 text-amber-800 px-1.5 py-0.2 rounded font-medium">Draft Only</span>
                    )}
                  </div>
                  <div className="font-semibold text-sm line-clamp-1">{s.title}</div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right: Selected Source, Version Selector & Entries */}
        <div className="lg:col-span-3 space-y-6">
          {selectedSource ? (
            <>
              {/* Source Header Card */}
              <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-slate-100 pb-4">
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="text-xs font-mono font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                        {selectedSource.type}
                      </span>
                      <h2 className="text-lg font-bold text-slate-900">{selectedSource.title}</h2>
                    </div>
                    <p className="text-xs text-slate-500 mt-1">Source ID: #{selectedSource.id} • Workspace: {activeWorkspace?.name}</p>
                  </div>

                  {/* Version Picker */}
                  <div className="flex items-center space-x-2">
                    <span className="text-xs text-slate-500 font-medium">Version:</span>
                    <select
                      className="text-sm font-semibold border border-slate-200 rounded-lg px-2.5 py-1.5 bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-teal-500"
                      value={selectedVersion?.id || ''}
                      onChange={(e) => {
                        const found = versions.find(v => String(v.id) === e.target.value);
                        if (found) setSelectedVersion(found);
                      }}
                    >
                      {versions.map(v => (
                        <option key={v.id} value={v.id}>
                          v{v.versionNumber} ({v.status})
                        </option>
                      ))}
                    </select>

                    {isAdmin && (
                      <button
                        type="button"
                        onClick={handleArchiveSource}
                        title="Archive Knowledge Source"
                        className="p-1.5 rounded-lg border border-slate-200 text-slate-400 hover:text-rose-600 hover:border-rose-200 hover:bg-rose-50 transition-colors ml-1"
                      >
                        <Archive className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>

                {/* Version Metadata Banner */}
                {selectedVersion && (
                  <div className="flex flex-wrap items-center justify-between gap-3 bg-slate-50 p-3.5 rounded-lg border border-slate-200 text-xs">
                    <div className="flex flex-wrap items-center gap-4">
                      <div className="flex items-center space-x-1.5">
                        <span className="font-medium text-slate-500">Status:</span>
                        <span className={`px-2 py-0.5 rounded-full font-bold border ${statusColors[selectedVersion.status] || ''}`}>
                          {selectedVersion.status}
                        </span>
                      </div>

                      {selectedVersion.checksum && (
                        <div className="flex items-center space-x-1.5 font-mono">
                          <Hash className="w-3.5 h-3.5 text-slate-400" />
                          <span className="text-slate-500 font-medium">SHA-256:</span>
                          <span className="text-slate-700 font-semibold" title={selectedVersion.checksum}>
                            {selectedVersion.checksum.substring(0, 16)}...
                          </span>
                        </div>
                      )}

                      {selectedVersion.fileReference && (
                        <div className="flex items-center space-x-1.5">
                          <FileText className="w-3.5 h-3.5 text-slate-400" />
                          <span className="text-slate-600 font-medium">{selectedVersion.fileReference}</span>
                        </div>
                      )}
                    </div>

                    {/* Version Actions */}
                    <div className="flex items-center space-x-2">
                      {selectedVersion.status === 'DRAFT' && isAdmin && (
                        <>
                          <button
                            onClick={() => setShowPdfModal(true)}
                            className="inline-flex items-center space-x-1.5 bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 px-3 py-1.5 rounded-md font-medium transition-colors"
                          >
                            <FileUp className="w-3.5 h-3.5 text-slate-500" />
                            <span>Import PDF</span>
                          </button>

                          <button
                            onClick={() => setShowAddEntryModal(true)}
                            className="inline-flex items-center space-x-1.5 bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 px-3 py-1.5 rounded-md font-medium transition-colors"
                          >
                            <Plus className="w-3.5 h-3.5 text-slate-500" />
                            <span>Add Rule</span>
                          </button>

                          <button
                            onClick={handleApproveVersion}
                            className="inline-flex items-center space-x-1.5 bg-emerald-600 hover:bg-emerald-700 text-white px-3 py-1.5 rounded-md font-medium transition-colors shadow-xs"
                          >
                            <CheckCircle className="w-3.5 h-3.5" />
                            <span>Approve as CURRENT</span>
                          </button>
                        </>
                      )}

                      {selectedVersion.status !== 'DRAFT' && (
                        <div className="flex items-center space-x-2">
                          <div className="flex items-center space-x-1.5 text-slate-500 bg-white border border-slate-200 px-2.5 py-1 rounded-md">
                            <Lock className="w-3.5 h-3.5 text-slate-400" />
                            <span>Approved version is immutable</span>
                          </div>
                          {isAdmin && (
                            <button
                              type="button"
                              onClick={handleCreateDraftVersion}
                              className="inline-flex items-center space-x-1.5 bg-teal-600 hover:bg-teal-700 text-white px-3 py-1.5 rounded-md font-medium transition-colors shadow-xs"
                              title="Create a new draft version cloned from this version to update rules"
                            >
                              <GitBranch className="w-3.5 h-3.5" />
                              <span>New Draft Version</span>
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>

              {/* Entries Table */}
              <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
                <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <h3 className="font-semibold text-slate-900 text-sm">Knowledge Entries ({entries.length})</h3>
                    {selectedVersion?.status === 'DRAFT' && (
                      <span className="text-xs bg-amber-50 text-amber-700 border border-amber-200 px-2 py-0.5 rounded">
                        Unapproved Candidates
                      </span>
                    )}
                  </div>
                </div>

                {entries.length === 0 ? (
                  <div className="p-8 text-center text-slate-400 text-sm">
                    No entries in this version. Use "Add Rule" or "Import PDF" to add candidate rules.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-slate-100 text-left text-sm">
                      <thead className="bg-slate-50/75 text-xs text-slate-500 uppercase tracking-wider font-semibold">
                        <tr>
                          <th className="py-3 px-4">Code</th>
                          <th className="py-3 px-4">Category</th>
                          <th className="py-3 px-4">Severity</th>
                          <th className="py-3 px-4">Rule / Guidance Text</th>
                          <th className="py-3 px-4">Reference</th>
                          {selectedVersion?.status === 'DRAFT' && isAdmin && (
                            <th className="py-3 px-4 text-right">Actions</th>
                          )}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 text-slate-700">
                        {entries.map((entry) => (
                          <tr key={entry.id} className="hover:bg-slate-50/50">
                            <td className="py-3 px-4 font-mono font-semibold text-slate-900 whitespace-nowrap">
                              {entry.code}
                            </td>
                            <td className="py-3 px-4 whitespace-nowrap">
                              <span className={`px-2 py-0.5 rounded-full text-xs font-medium border ${categoryColors[entry.category] || 'bg-slate-100'}`}>
                                {entry.category}
                              </span>
                            </td>
                            <td className="py-3 px-4 whitespace-nowrap">
                              <span className={`px-2 py-0.5 rounded text-xs font-semibold border ${severityColors[entry.defaultSeverity] || 'bg-slate-100'}`}>
                                {entry.defaultSeverity}
                              </span>
                            </td>
                            <td className="py-3 px-4 text-xs font-mono leading-relaxed max-w-md">
                              {entry.text}
                            </td>
                            <td className="py-3 px-4 text-xs text-slate-500 whitespace-nowrap">
                              {entry.pageOrSectionReference || '—'}
                            </td>
                            {selectedVersion?.status === 'DRAFT' && isAdmin && (
                              <td className="py-3 px-4 text-right whitespace-nowrap">
                                <button
                                  onClick={() => handleDeleteEntry(entry.id)}
                                  className="text-slate-400 hover:text-rose-600 transition-colors"
                                  title="Delete entry"
                                >
                                  <Trash2 className="w-4 h-4" />
                                </button>
                              </td>
                            )}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="bg-white border border-slate-200 rounded-xl p-12 text-center text-slate-400">
              Select or create a knowledge source from the left panel.
            </div>
          )}
        </div>
      </div>

      {/* Modal: Create Source */}
      {showCreateSourceModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-slate-900">Create Knowledge Source</h3>
            <form onSubmit={handleCreateSource} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Source Title
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Botanical Claims Verification Standard"
                  value={newSourceTitle}
                  onChange={(e) => setNewSourceTitle(e.target.value)}
                  className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Source Type
                </label>
                <select
                  value={newSourceType}
                  onChange={(e) => setNewSourceType(e.target.value)}
                  className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                >
                  <option value="GUIDELINE">GUIDELINE</option>
                  <option value="TERMINOLOGY">TERMINOLOGY</option>
                  <option value="CLAIMS_REGISTER">CLAIMS_REGISTER</option>
                  <option value="ASSET_RULES">ASSET_RULES</option>
                </select>
              </div>

              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowCreateSourceModal(false)}
                  className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="bg-teal-600 hover:bg-teal-700 text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors"
                >
                  Create Source
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Entry */}
      {showAddEntryModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl max-w-lg w-full p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-slate-900">Add Candidate Rule / Entry</h3>
            <p className="text-xs text-slate-500">
              Entry will be added as unapproved candidate to DRAFT version v{selectedVersion?.versionNumber}.
            </p>
            <form onSubmit={handleAddEntry} className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Rule Code
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. ORG-01"
                    value={newEntryCode}
                    onChange={(e) => setNewEntryCode(e.target.value)}
                    className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Category
                  </label>
                  <select
                    value={newEntryCategory}
                    onChange={(e) => setNewEntryCategory(e.target.value)}
                    className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                  >
                    <option value="TONE">TONE</option>
                    <option value="TERMINOLOGY">TERMINOLOGY</option>
                    <option value="MESSAGING">MESSAGING</option>
                    <option value="FACT">FACT</option>
                    <option value="CLAIM">CLAIM</option>
                    <option value="VISUAL">VISUAL</option>
                    <option value="ACCESSIBILITY">ACCESSIBILITY</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Default Severity
                  </label>
                  <select
                    value={newEntrySeverity}
                    onChange={(e) => setNewEntrySeverity(e.target.value)}
                    className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                  >
                    <option value="CRITICAL">CRITICAL</option>
                    <option value="MAJOR">MAJOR</option>
                    <option value="MINOR">MINOR</option>
                    <option value="ADVISORY">ADVISORY</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Reference
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. Page 2, Section 3.1"
                    value={newEntryRef}
                    onChange={(e) => setNewEntryRef(e.target.value)}
                    className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Rule Guidance / Approved Fact Text
                </label>
                <textarea
                  required
                  rows={3}
                  placeholder="e.g. Never use absolute organic claims without USDA or Ecocert certification..."
                  value={newEntryText}
                  onChange={(e) => setNewEntryText(e.target.value)}
                  className="w-full text-sm border border-slate-300 rounded-lg px-3 py-2 focus:ring-1 focus:ring-teal-500 focus:outline-none"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowAddEntryModal(false)}
                  className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="bg-teal-600 hover:bg-teal-700 text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors"
                >
                  Add Candidate Entry
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: PDF Upload */}
      {showPdfModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-slate-900">Import Guideline PDF</h3>
            <p className="text-xs text-slate-500">
              Apache PDFBox will extract structured text and create a new DRAFT version with candidate entries preserving page numbers. Extracted content is NOT automatically approved.
            </p>

            <form onSubmit={handlePdfUpload} className="space-y-4">
              <div className="border-2 border-dashed border-slate-300 rounded-lg p-6 text-center hover:border-teal-500 transition-colors">
                <input
                  type="file"
                  accept="application/pdf"
                  required
                  onChange={(e) => setPdfFile(e.target.files?.[0] || null)}
                  className="text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-teal-50 file:text-teal-700 hover:file:bg-teal-100"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowPdfModal(false)}
                  className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!pdfFile || loading}
                  className="bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors"
                >
                  {loading ? 'Extracting...' : 'Extract Candidates'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
