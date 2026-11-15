import React, { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, UserCheck, AlertCircle, ArrowRight, CheckCircle2 } from 'lucide-react';

export default function AcceptInvitePage({ token, onComplete }) {
  const { user, login } = useAuth();
  const [invitation, setInvitation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');

  useEffect(() => {
    const fetchInvitation = async () => {
      try {
        const res = await axiosClient.get(`/auth/invitations/${token}`);
        if (res.data?.success && res.data?.data) {
          setInvitation(res.data.data);
        } else {
          setError('Invitation not found or no longer active.');
        }
      } catch (err) {
        if (!err.response) {
          setError('Unable to contact the VeriWeave server. Please verify your internet connection or check backend API status.');
        } else {
          setError(err.response?.data?.message || 'Invalid or expired invitation token.');
        }
      } finally {
        setLoading(false);
      }
    };
    fetchInvitation();
  }, [token]);

  const handleAccept = async (e) => {
    if (e) e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      const payload = user ? {} : { fullName, password };
      const res = await axiosClient.post(`/auth/invitations/${token}/accept`, payload);
      if (res.data?.success && res.data?.data) {
        const data = res.data.data;
        if (data.accessToken) {
          localStorage.setItem('veriweave_access_token', data.accessToken);
          localStorage.setItem('veriweave_refresh_token', data.refreshToken);
          if (data.activeWorkspace) {
            localStorage.setItem('veriweave_active_workspace_id', String(data.activeWorkspace.id));
          }
        }
        setSuccess(true);
        setTimeout(() => {
          window.location.href = '/';
        }, 1200);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to accept invitation.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="flex items-center space-x-3 text-slate-500">
          <div className="w-5 h-5 border-2 border-teal-600 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm font-medium">Verifying invitation token...</span>
        </div>
      </div>
    );
  }

  if (error && !invitation) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
        <div className="max-w-md w-full bg-white p-8 rounded-xl border border-slate-200 text-center shadow-sm">
          <div className="w-12 h-12 bg-red-100 text-red-600 rounded-full flex items-center justify-center mx-auto mb-4">
            <AlertCircle className="w-6 h-6" />
          </div>
          <h2 className="text-xl font-bold text-slate-900 mb-2">Invitation Unavailable</h2>
          <p className="text-sm text-slate-600 mb-6">{error}</p>
          <a
            href="/"
            className="inline-flex items-center justify-center px-4 py-2 border border-slate-300 rounded-lg text-sm font-medium text-slate-700 bg-white hover:bg-slate-50"
          >
            Return to Sign In
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col justify-center py-12 px-4 sm:px-6 lg:px-8 bg-slate-50">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="w-12 h-12 rounded-xl bg-teal-600 flex items-center justify-center text-white shadow-md">
            <ShieldCheck className="w-7 h-7" />
          </div>
        </div>
        <h2 className="mt-4 text-center text-2xl font-bold tracking-tight text-slate-900">
          Join {invitation?.workspaceName}
        </h2>
        <p className="mt-1 text-center text-sm text-slate-500">
          You have been invited to collaborate as a{' '}
          <span className="font-semibold text-teal-700 uppercase">{invitation?.role}</span>
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-6 shadow-sm border border-slate-200 rounded-xl sm:px-10">
          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm flex items-start space-x-2">
              <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {success ? (
            <div className="p-6 text-center">
              <CheckCircle2 className="w-12 h-12 text-teal-600 mx-auto mb-3" />
              <h3 className="text-lg font-bold text-slate-900 mb-1">Welcome aboard!</h3>
              <p className="text-sm text-slate-600">You have joined {invitation?.workspaceName}. Redirecting to your dashboard...</p>
            </div>
          ) : user ? (
            <div className="space-y-4">
              <div className="p-3 bg-teal-50 border border-teal-200 rounded-lg text-xs text-teal-800">
                Logged in as <strong>{user.email}</strong>. Accepting will link this workspace to your existing account.
              </div>
              <button
                type="button"
                disabled={submitting}
                onClick={handleAccept}
                className="w-full flex items-center justify-center space-x-2 py-2.5 px-4 rounded-lg text-sm font-semibold text-white bg-teal-600 hover:bg-teal-700 transition-colors disabled:opacity-50"
              >
                <span>{submitting ? 'Joining...' : 'Accept & Open Workspace'}</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <form onSubmit={handleAccept} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Invited Email
                </label>
                <input
                  type="email"
                  disabled
                  value={invitation?.email || ''}
                  className="w-full px-3 py-2 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-600 cursor-not-allowed"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Your Full Name
                </label>
                <input
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="e.g. Marcus Chen"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Create Password
                </label>
                <input
                  type="password"
                  required
                  minLength={8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Min 8 characters"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
                />
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="w-full flex items-center justify-center space-x-2 py-2.5 px-4 rounded-lg text-sm font-semibold text-white bg-teal-600 hover:bg-teal-700 transition-colors disabled:opacity-50"
              >
                <span>{submitting ? 'Creating account & joining...' : 'Join Workspace'}</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
