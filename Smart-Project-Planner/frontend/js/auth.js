/**
 * Smart Project Planner - Authentication & Session Management
 * Handles API calls to /api/login, /api/register/company, and /api/register/employee.
 */

const API_BASE = '/api';

// ============================================================================
// Session Storage Utilities
// ============================================================================

function getStoredUser() {
  try {
    const raw = localStorage.getItem('spp_user');
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

function getStoredToken() {
  return localStorage.getItem('spp_token') || '';
}

function saveAuthSession(user, token) {
  if (user) {
    localStorage.setItem('spp_user', JSON.stringify(user));
  }
  if (token) {
    localStorage.setItem('spp_token', token);
  }
}

function clearAuthSession() {
  localStorage.removeItem('spp_user');
  localStorage.removeItem('spp_token');
}

function handleLogout() {
  clearAuthSession();
  showToast('Logged out of workspace.', 'info');
  setTimeout(() => {
    window.location.href = '/login';
  }, 400);
}

// ============================================================================
// Automatic Route Guard
// ============================================================================
(function checkRouteGuard() {
  const path = window.location.pathname;
  const user = getStoredUser();

  if (path.includes('dashboard')) {
    if (!user) {
      window.location.href = '/login';
    }
  } else if (path.includes('login') || path.includes('register')) {
    if (user) {
      window.location.href = '/dashboard';
    }
  }
})();

// ============================================================================
// Auth Form Handlers
// ============================================================================

/**
 * Handles Login API submission
 */
async function handleLoginSubmit(event) {
  event.preventDefault();
  const usernameInput = document.getElementById('loginUsername');
  const passwordInput = document.getElementById('loginPassword');
  const btn = document.getElementById('loginBtn');

  const username = usernameInput.value.trim();
  const password = passwordInput.value;

  if (!username || !password) {
    showToast('Please enter both username and password.', 'error');
    return;
  }

  setButtonLoading(btn, true, 'Signing in...');

  try {
    const res = await fetch(`${API_BASE}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      throw new Error(data.error || 'Invalid credentials.');
    }

    saveAuthSession(data.user, data.token);
    showToast('Login successful! Redirecting to workspace...', 'success');
    setTimeout(() => {
      window.location.href = '/dashboard';
    }, 600);

  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false, 'Sign In to Workspace');
  }
}

/**
 * Handles Company Registration (Admin) API submission
 */
async function handleCompanyRegisterSubmit(event) {
  event.preventDefault();
  const companyName = document.getElementById('regCompanyName').value.trim();
  const companyCode = document.getElementById('regCompanyCode').value.trim().toUpperCase();
  const username = document.getElementById('regAdminUsername').value.trim();
  const email = document.getElementById('regAdminEmail').value.trim();
  const password = document.getElementById('regAdminPassword').value;
  const btn = document.getElementById('regCompanyBtn');

  if (!companyName || !companyCode || !username || !email || !password) {
    showToast('All fields are required.', 'error');
    return;
  }

  setButtonLoading(btn, true, 'Provisioning Organization...');

  try {
    const res = await fetch(`${API_BASE}/register/company`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        companyName,
        companyCode,
        username,
        email,
        password
      })
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      throw new Error(data.error || 'Failed to register company.');
    }

    saveAuthSession(data.user, data.token);
    showToast('Company registered successfully! Redirecting...', 'success');
    setTimeout(() => {
      window.location.href = '/dashboard';
    }, 700);

  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false, 'Register Company & Enter Workspace');
  }
}

/**
 * Handles Employee Registration (joining existing company code)
 */
async function handleEmployeeRegisterSubmit(event) {
  event.preventDefault();
  const companyCode = document.getElementById('empCompanyCode').value.trim().toUpperCase();
  const username = document.getElementById('empUsername').value.trim();
  const email = document.getElementById('empEmail').value.trim();
  const password = document.getElementById('empPassword').value;
  const role = document.getElementById('empRole').value;
  const btn = document.getElementById('regEmpBtn');

  if (!companyCode || !username || !email || !password) {
    showToast('All fields are required.', 'error');
    return;
  }

  setButtonLoading(btn, true, 'Joining Workspace...');

  try {
    const res = await fetch(`${API_BASE}/register/employee`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        companyCode,
        username,
        email,
        password,
        role
      })
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      throw new Error(data.error || 'Failed to join company.');
    }

    saveAuthSession(data.user, data.token);
    showToast('Welcome to the workspace! Redirecting...', 'success');
    setTimeout(() => {
      window.location.href = '/dashboard';
    }, 700);

  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false, 'Join Workspace');
  }
}

// ============================================================================
// UI Helper Utilities
// ============================================================================

function switchRegisterTab(tab) {
  const companySection = document.getElementById('companyFormSection');
  const employeeSection = document.getElementById('employeeFormSection');
  const tabComp = document.getElementById('tabCompanyBtn');
  const tabEmp = document.getElementById('tabEmployeeBtn');

  if (tab === 'company') {
    companySection.style.display = 'block';
    employeeSection.style.display = 'none';
    tabComp.classList.add('active');
    tabEmp.classList.remove('active');
  } else {
    companySection.style.display = 'none';
    employeeSection.style.display = 'block';
    tabComp.classList.remove('active');
    tabEmp.classList.add('active');
  }
}

function fillCredentials(username, password) {
  const uInput = document.getElementById('loginUsername');
  const pInput = document.getElementById('loginPassword');
  if (uInput && pInput) {
    uInput.value = username;
    pInput.value = password;
    showToast(`Quick-filled credentials for ${username}`, 'info');
  }
}

function togglePasswordVisibility(inputId, btn) {
  const input = document.getElementById(inputId);
  if (input) {
    if (input.type === 'password') {
      input.type = 'text';
      btn.textContent = 'Hide';
    } else {
      input.type = 'password';
      btn.textContent = 'Show';
    }
  }
}

function setButtonLoading(btn, loading, text) {
  if (!btn) return;
  if (loading) {
    btn.disabled = true;
    btn.dataset.originalText = btn.innerHTML;
    btn.innerHTML = `<span>⏳ ${text}</span>`;
  } else {
    btn.disabled = false;
    btn.innerHTML = `<span>${text}</span>`;
  }
}

/**
 * Toast Notification System
 */
function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  
  let icon = 'ℹ️';
  if (type === 'success') icon = '✓';
  if (type === 'error') icon = '✕';

  toast.innerHTML = `<span style="font-weight: bold;">${icon}</span> <span>${message}</span>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}
