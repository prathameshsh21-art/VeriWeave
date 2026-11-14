import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import axiosClient from '../api/axiosClient';
import { ShieldCheck, ArrowRight, AlertCircle, CheckCircle2, Eye, EyeOff, KeyRound, Mail, RefreshCw, X } from 'lucide-react';

export default function LoginPage() {
  const { login, register } = useAuth();
  const [isRegister, setIsRegister] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [workspaceName, setWorkspaceName] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [coldStartNotice, setColdStartNotice] = useState(false);

  // Email Verification modal state
  const [showVerifyModal, setShowVerifyModal] = useState(false);
  const [verifyEmail, setVerifyEmail] = useState('');
  const [verifyCode, setVerifyCode] = useState('');
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [verifyError, setVerifyError] = useState('');

  // Change Email state
  const [showChangeEmail, setShowChangeEmail] = useState(false);
  const [newEmail, setNewEmail] = useState('');
  const [changePassword, setChangePassword] = useState('');

  // Forgot Password modal state
  const [showForgotModal, setShowForgotModal] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotStep, setForgotStep] = useState(1); // 1: send email, 2: enter code & new pw
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotMsg, setForgotMsg] = useState('');
  const [forgotError, setForgotError] = useState('');

  // Cold start timer effect
  useEffect(() => {
    let timer;
    if (loading) {
      timer = setTimeout(() => {
        setColdStartNotice(true);
      }, 3500);
    } else {
      setColdStartNotice(false);
    }
    return () => clearTimeout(timer);
  }, [loading]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);

    try {
      if (isRegister) {
        const res = await register(email, password, fullName, workspaceName);
        if (!res.accessToken) {
          // Requires email verification
          setVerifyEmail(email);
          setShowVerifyModal(true);
          setSuccessMsg('Account registered. Please enter the 6-digit verification code sent to your email.');
        }
      } else {
        await login(email, password);
      }
    } catch (err) {
      const errMsg = err.response?.data?.message || err.message || 'Authentication failed';
      if (err.response?.data?.error === 'EMAIL_NOT_VERIFIED') {
        setVerifyEmail(email);
        setShowVerifyModal(true);
        setVerifyError('Your email address has not been verified yet. Enter your verification code below:');
      } else {
        setError(errMsg);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleVerifySubmit = async (e) => {
    e.preventDefault();
    setVerifyError('');
    setVerifyLoading(true);

    try {
      const res = await axiosClient.post('/auth/verify-email', {
        email: verifyEmail,
        code: verifyCode.trim(),
      });
      if (res.data?.success && res.data?.data) {
        const data = res.data.data;
        if (data.accessToken) {
          localStorage.setItem('veriweave_access_token', data.accessToken);
          localStorage.setItem('veriweave_refresh_token', data.refreshToken);
          if (data.activeWorkspace) {
            localStorage.setItem('veriweave_active_workspace_id', String(data.activeWorkspace.id));
          }
          window.location.reload();
        }
      }
    } catch (err) {
      setVerifyError(err.response?.data?.message || 'Invalid or expired verification code');
    } finally {
      setVerifyLoading(false);
    }
  };

  const handleResendCode = async () => {
    setVerifyError('');
    try {
      await axiosClient.post('/auth/resend-verification', { email: verifyEmail });
      setSuccessMsg('A fresh verification code has been dispatched to ' + verifyEmail);
    } catch (err) {
      setVerifyError(err.response?.data?.message || 'Failed to resend verification code');
    }
  };

  const handleChangeEmailSubmit = async (e) => {
    e.preventDefault();
    setVerifyError('');
    setVerifyLoading(true);

    try {
      await axiosClient.post('/auth/change-email', {
        currentEmail: verifyEmail,
        newEmail: newEmail.trim().toLowerCase(),
        password: changePassword,
      });
      setVerifyEmail(newEmail.trim().toLowerCase());
      setShowChangeEmail(false);
      setSuccessMsg('Email updated to ' + newEmail + '. A new verification code has been sent.');
    } catch (err) {
      setVerifyError(err.response?.data?.message || 'Failed to update email address');
    } finally {
      setVerifyLoading(false);
    }
  };

  const handleForgotSubmit = async (e) => {
    e.preventDefault();
    setForgotError('');
    setForgotMsg('');
    setForgotLoading(true);

    try {
      if (forgotStep === 1) {
        await axiosClient.post('/auth/forgot-password', { email: forgotEmail });
        setForgotMsg('If registered, a 6-digit password reset code has been sent.');
        setForgotStep(2);
      } else {
        await axiosClient.post('/auth/reset-password', {
          email: forgotEmail,
          code: resetCode.trim(),
          newPassword: newPassword,
        });
        setForgotMsg('Password successfully reset! Please sign in with your new password.');
        setTimeout(() => {
          setShowForgotModal(false);
          setForgotStep(1);
          setEmail(forgotEmail);
        }, 1500);
      }
    } catch (err) {
      setForgotError(err.response?.data?.message || 'Operation failed. Please try again.');
    } finally {
      setForgotLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-4rem)] flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="w-12 h-12 rounded-xl bg-teal-600 flex items-center justify-center text-white shadow-md">
            <ShieldCheck className="w-7 h-7" />
          </div>
        </div>
        <h2 className="mt-4 text-center text-2xl font-bold tracking-tight text-slate-900">
          {isRegister ? 'Create your VeriWeave account' : 'Sign in to VeriWeave'}
        </h2>
        <p className="mt-1 text-center text-sm text-slate-500 italic">
          "The model proposes. Deterministic code verifies. A human decides."
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-6 shadow-sm border border-slate-200 rounded-xl sm:px-10">
          {/* Mode Switcher Tabs */}
          <div className="flex border-b border-slate-200 mb-6">
            <button
              type="button"
              onClick={() => { setIsRegister(false); setError(''); setSuccessMsg(''); }}
              className={`pb-3 flex-1 text-sm font-medium text-center border-b-2 transition-colors ${
                !isRegister
                  ? 'border-teal-600 text-teal-600 font-semibold'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => { setIsRegister(true); setError(''); setSuccessMsg(''); }}
              className={`pb-3 flex-1 text-sm font-medium text-center border-b-2 transition-colors ${
                isRegister
                  ? 'border-teal-600 text-teal-600 font-semibold'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              Create Account
            </button>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm flex items-start space-x-2">
              <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {successMsg && (
            <div className="mb-4 p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-lg text-sm flex items-start space-x-2">
              <CheckCircle2 className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <span>{successMsg}</span>
            </div>
          )}

          {coldStartNotice && (
            <div className="mb-4 p-3 bg-amber-50 border border-amber-200 text-amber-800 rounded-lg text-xs flex items-start space-x-2 animate-pulse">
              <RefreshCw className="w-4 h-4 mt-0.5 flex-shrink-0 animate-spin" />
              <span>The demo server is starting. The first request on cloud hosting may take 20–40 seconds. Please hold on...</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {isRegister && (
              <>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Full Name
                  </label>
                  <input
                    type="text"
                    required
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    placeholder="e.g. Jane Doe"
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Brand / Workspace Name
                  </label>
                  <input
                    type="text"
                    value={workspaceName}
                    onChange={(e) => setWorkspaceName(e.target.value)}
                    placeholder="e.g. Acme Corporation"
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                  />
                  <p className="mt-1 text-xs text-slate-400">
                    You will be assigned the <span className="font-semibold text-slate-600">ADMIN</span> role for this workspace.
                  </p>
                </div>
              </>
            )}

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                Email Address
              </label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@company.com"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Password
                </label>
                {!isRegister && (
                  <button
                    type="button"
                    onClick={() => { setForgotEmail(email); setShowForgotModal(true); }}
                    className="text-xs text-teal-600 hover:text-teal-700 font-medium"
                  >
                    Forgot Password?
                  </button>
                )}
              </div>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  minLength={8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500 pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-slate-600"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full mt-2 flex items-center justify-center space-x-2 py-2.5 px-4 border border-transparent rounded-lg text-sm font-semibold text-white bg-teal-600 hover:bg-teal-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-teal-500 transition-colors disabled:opacity-50"
            >
              <span>{loading ? 'Authenticating...' : isRegister ? 'Create Workspace' : 'Sign In'}</span>
              {!loading && <ArrowRight className="w-4 h-4" />}
            </button>
          </form>

          {/* Quick Demo Role Selector for CodeTitan Judges */}
          {!isRegister && (
            <div className="mt-6 pt-5 border-t border-slate-100">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  CodeTitan Demo Accounts
                </span>
                <span className="text-[10px] bg-teal-50 text-teal-700 font-medium px-2 py-0.5 rounded">
                  1-Click Fill
                </span>
              </div>
              <div className="space-y-1.5">
                <button
                  type="button"
                  onClick={() => {
                    setEmail('admin@veriflora.com');
                    setPassword('Secret123!');
                  }}
                  className="w-full text-left px-3 py-2 text-xs bg-slate-50 hover:bg-teal-50 hover:border-teal-300 border border-slate-200 rounded-lg transition-colors flex items-center justify-between group"
                >
                  <div>
                    <span className="font-semibold text-slate-800 group-hover:text-teal-800">Elena Vance (Admin)</span>
                    <span className="block text-[11px] text-slate-500">Brand Director • Full KB governance & audit</span>
                  </div>
                  <span className="text-[11px] text-teal-600 font-medium opacity-0 group-hover:opacity-100 transition-opacity">Select →</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEmail('creator@veriflora.com');
                    setPassword('Secret123!');
                  }}
                  className="w-full text-left px-3 py-2 text-xs bg-slate-50 hover:bg-teal-50 hover:border-teal-300 border border-slate-200 rounded-lg transition-colors flex items-center justify-between group"
                >
                  <div>
                    <span className="font-semibold text-slate-800 group-hover:text-teal-800">Marcus Chen (Creator)</span>
                    <span className="block text-[11px] text-slate-500">Content Creator • Author copy & respond to findings</span>
                  </div>
                  <span className="text-[11px] text-teal-600 font-medium opacity-0 group-hover:opacity-100 transition-opacity">Select →</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEmail('reviewer@veriflora.com');
                    setPassword('Secret123!');
                  }}
                  className="w-full text-left px-3 py-2 text-xs bg-slate-50 hover:bg-teal-50 hover:border-teal-300 border border-slate-200 rounded-lg transition-colors flex items-center justify-between group"
                >
                  <div>
                    <span className="font-semibold text-slate-800 group-hover:text-teal-800">Sarah Jenkins (Reviewer)</span>
                    <span className="block text-[11px] text-slate-500">Compliance Reviewer • Independent approval/override</span>
                  </div>
                  <span className="text-[11px] text-teal-600 font-medium opacity-0 group-hover:opacity-100 transition-opacity">Select →</span>
                </button>
              </div>
            </div>
          )}

          {/* Proposal reference note */}
          <div className="mt-6 pt-6 border-t border-slate-100">
            <div className="text-xs text-slate-500 flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-teal-600 flex-shrink-0" />
              <span>Stateless JWT session with server-enforced role permissions.</span>
            </div>
          </div>
        </div>
      </div>

      {/* Verify Email Modal */}
      {showVerifyModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center space-x-2 text-teal-700">
                <Mail className="w-5 h-5" />
                <h3 className="text-base font-bold text-slate-900">Email Verification</h3>
              </div>
              <button onClick={() => setShowVerifyModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            {verifyError && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-xs flex items-start space-x-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                <span>{verifyError}</span>
              </div>
            )}

            {!showChangeEmail ? (
              <form onSubmit={handleVerifySubmit} className="space-y-4">
                <p className="text-xs text-slate-600">
                  Enter the 6-digit verification code sent to <strong>{verifyEmail}</strong>:
                </p>
                <div>
                  <input
                    type="text"
                    required
                    maxLength={6}
                    value={verifyCode}
                    onChange={(e) => setVerifyCode(e.target.value)}
                    placeholder="123456"
                    className="w-full text-center tracking-widest text-xl font-bold py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-teal-500"
                  />
                </div>
                <button
                  type="submit"
                  disabled={verifyLoading}
                  className="w-full py-2 bg-teal-600 text-white rounded-lg text-sm font-semibold hover:bg-teal-700 disabled:opacity-50"
                >
                  {verifyLoading ? 'Verifying...' : 'Verify Code & Activate Session'}
                </button>
                <div className="flex items-center justify-between text-xs pt-2">
                  <button type="button" onClick={handleResendCode} className="text-teal-600 hover:underline">
                    Resend Code
                  </button>
                  <button type="button" onClick={() => setShowChangeEmail(true)} className="text-slate-500 hover:underline">
                    Change Email
                  </button>
                </div>
              </form>
            ) : (
              <form onSubmit={handleChangeEmailSubmit} className="space-y-3">
                <p className="text-xs text-slate-600">Enter your corrected email address and current password:</p>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">New Email</label>
                  <input
                    type="email"
                    required
                    value={newEmail}
                    onChange={(e) => setNewEmail(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">Account Password</label>
                  <input
                    type="password"
                    required
                    value={changePassword}
                    onChange={(e) => setChangePassword(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
                  />
                </div>
                <div className="flex space-x-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowChangeEmail(false)}
                    className="flex-1 py-2 border border-slate-300 text-slate-700 rounded-lg text-xs font-medium"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={verifyLoading}
                    className="flex-1 py-2 bg-teal-600 text-white rounded-lg text-xs font-semibold hover:bg-teal-700"
                  >
                    Update & Send Code
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center space-x-2 text-teal-700">
                <KeyRound className="w-5 h-5" />
                <h3 className="text-base font-bold text-slate-900">Reset Password</h3>
              </div>
              <button onClick={() => setShowForgotModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            {forgotError && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-xs flex items-start space-x-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                <span>{forgotError}</span>
              </div>
            )}

            {forgotMsg && (
              <div className="mb-4 p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-lg text-xs flex items-start space-x-2">
                <CheckCircle2 className="w-4 h-4 flex-shrink-0 mt-0.5" />
                <span>{forgotMsg}</span>
              </div>
            )}

            <form onSubmit={handleForgotSubmit} className="space-y-4">
              {forgotStep === 1 ? (
                <>
                  <p className="text-xs text-slate-600">
                    Enter your registered email address and we will dispatch a 6-digit password reset verification code:
                  </p>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Account Email
                    </label>
                    <input
                      type="email"
                      required
                      value={forgotEmail}
                      onChange={(e) => setForgotEmail(e.target.value)}
                      placeholder="name@company.com"
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={forgotLoading}
                    className="w-full py-2 bg-teal-600 text-white rounded-lg text-sm font-semibold hover:bg-teal-700 disabled:opacity-50"
                  >
                    {forgotLoading ? 'Dispatching code...' : 'Send Verification Code'}
                  </button>
                </>
              ) : (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      6-Digit Reset Code
                    </label>
                    <input
                      type="text"
                      required
                      maxLength={6}
                      value={resetCode}
                      onChange={(e) => setResetCode(e.target.value)}
                      placeholder="123456"
                      className="w-full text-center tracking-widest text-lg font-bold py-2 border border-slate-300 rounded-lg"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      New Password (Min 8 chars, mixed case + digit)
                    </label>
                    <input
                      type="password"
                      required
                      minLength={8}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={forgotLoading}
                    className="w-full py-2 bg-teal-600 text-white rounded-lg text-sm font-semibold hover:bg-teal-700 disabled:opacity-50"
                  >
                    {forgotLoading ? 'Updating password...' : 'Confirm New Password'}
                  </button>
                </>
              )}
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
