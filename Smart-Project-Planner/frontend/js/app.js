/**
 * Smart Project Planner - Main Workspace Application Logic
 * Manages Multi-Tenant state, RBAC UI rendering, Projects, Kanban Board, and Team Members.
 */

// ============================================================================
// State Management
// ============================================================================
let currentUser = null;
let projectsList = [];
let tasksList = [];
let usersList = [];
let currentDraggedTaskId = null;
let activeView = 'overview';

// ============================================================================
// Initialization
// ============================================================================
document.addEventListener('DOMContentLoaded', () => {
  currentUser = getStoredUser();
  if (!currentUser) {
    window.location.href = '/login';
    return;
  }

  // Setup sidebar tenant and profile info
  setupUserInterface();

  // Load initial workspace data
  loadAllData();
});

function setupUserInterface() {
  // Populate tenant company info
  const compNameEl = document.getElementById('sidebarCompanyName');
  const compCodeEl = document.getElementById('sidebarCompanyCode');
  const teamCodeEl = document.getElementById('teamViewCompanyCode');
  const usernameEl = document.getElementById('sidebarUsername');
  const roleBadgeEl = document.getElementById('sidebarRoleBadge');
  const avatarEl = document.getElementById('sidebarAvatar');

  if (compNameEl) compNameEl.textContent = currentUser.companyName || 'Workspace';
  if (compCodeEl) compCodeEl.textContent = currentUser.companyCode || 'TENANT';
  if (teamCodeEl) teamCodeEl.textContent = currentUser.companyCode || 'TENANT';
  if (usernameEl) usernameEl.textContent = currentUser.username;
  if (avatarEl) avatarEl.textContent = currentUser.username.charAt(0).toUpperCase();

  if (roleBadgeEl) {
    roleBadgeEl.textContent = currentUser.role || 'EMPLOYEE';
    roleBadgeEl.className = `badge role-${(currentUser.role || 'employee').toLowerCase()}`;
  }

  // Enforce Role-Based Access Control (RBAC) UI Rendering
  enforceRbacControls();
}

/**
 * Enforces RBAC permissions on UI controls.
 * EMPLOYEES cannot create/delete projects and cannot see Team Management.
 */
function enforceRbacControls() {
  const isEmployee = currentUser.role === 'EMPLOYEE';
  const isAdmin = currentUser.role === 'ADMIN';

  // 1. Hide Team Management menu for Employees
  const navTeam = document.getElementById('navTeam');
  if (navTeam && isEmployee) {
    navTeam.style.display = 'none';
  }

  // 2. Hide "New Project" buttons for Employees
  const btnNewProj = document.getElementById('btnNewProject');
  const btnNewProjView = document.getElementById('btnNewProjectView');
  if (isEmployee) {
    if (btnNewProj) btnNewProj.style.display = 'none';
    if (btnNewProjView) btnNewProjView.style.display = 'none';
  }
}

// ============================================================================
// Data Fetching & Sync (Multi-Tenant Secure API Calls)
// ============================================================================

async function apiFetch(endpoint, options = {}) {
  const token = getStoredToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (currentUser) {
    headers['X-User-Id'] = currentUser.userId;
    headers['X-Company-Id'] = currentUser.companyId;
    headers['X-User-Role'] = currentUser.role;
  }

  const response = await fetch(endpoint, {
    ...options,
    headers: headers
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.success === false) {
    if (response.status === 401) {
      handleLogout();
    }
    throw new Error(data.error || 'Server request failed');
  }
  return data;
}

async function loadAllData() {
  try {
    await Promise.all([
      loadProjects(),
      loadTasks(),
      loadTeamUsers()
    ]);
    renderOverviewMetrics();
    renderOverviewTasks();
  } catch (err) {
    console.error('Error loading workspace data:', err);
  }
}

// ============================================================================
// View Navigation
// ============================================================================

function switchDashboardView(view) {
  activeView = view;

  // View sections
  const views = {
    overview: document.getElementById('viewOverview'),
    projects: document.getElementById('viewProjects'),
    kanban: document.getElementById('viewKanban'),
    team: document.getElementById('viewTeam')
  };

  // Nav items
  const navs = {
    overview: document.getElementById('navOverview'),
    projects: document.getElementById('navProjects'),
    kanban: document.getElementById('navKanban'),
    team: document.getElementById('navTeam')
  };

  // Titles
  const titles = {
    overview: 'Workspace Overview',
    projects: 'Projects Directory',
    kanban: 'Kanban Sprint Board',
    team: 'Team & RBAC Permissions'
  };

  for (const key in views) {
    if (views[key]) views[key].style.display = (key === view) ? 'block' : 'none';
    if (navs[key]) {
      if (key === view) navs[key].classList.add('active');
      else navs[key].classList.remove('active');
    }
  }

  const pageTitle = document.getElementById('pageTitle');
  if (pageTitle) pageTitle.textContent = titles[view] || 'Workspace';

  // Show/Hide project filter on topbar only for Kanban
  const kanbanFilter = document.getElementById('kanbanProjectFilter');
  if (kanbanFilter) {
    kanbanFilter.style.display = (view === 'kanban') ? 'block' : 'none';
  }

  // Refresh view contents
  if (view === 'overview') {
    renderOverviewMetrics();
    renderOverviewTasks();
  } else if (view === 'projects') {
    renderProjects(projectsList);
  } else if (view === 'kanban') {
    renderKanban(tasksList);
  } else if (view === 'team') {
    renderTeamTable(usersList);
  }
}

// ============================================================================
// Projects Management
// ============================================================================

async function loadProjects() {
  try {
    const data = await apiFetch('/api/projects');
    projectsList = data.projects || [];
    renderProjects(projectsList);
    populateProjectDropdowns();
    return projectsList;
  } catch (err) {
    showToast('Failed to load projects: ' + err.message, 'error');
    return [];
  }
}

function renderProjects(projects) {
  const container = document.getElementById('projectsGrid');
  if (!container) return;

  if (!projects || projects.length === 0) {
    container.innerHTML = `
      <div style="grid-column: 1 / -1; padding: 3rem; text-align: center; color: var(--text-muted);" class="glass-card">
        <p style="font-size: 1.1rem; margin-bottom: 0.75rem;">No projects found</p>
        ${currentUser.role !== 'EMPLOYEE' ? '<button class="btn btn-primary btn-sm" onclick="openProjectModal()">Create First Project</button>' : ''}
      </div>
    `;
    return;
  }

  const canDelete = currentUser.role === 'ADMIN' || currentUser.role === 'MANAGER';

  container.innerHTML = projects.map(p => {
    const total = p.totalTasks || 0;
    const completed = p.completedTasks || 0;
    const percent = total > 0 ? Math.round((completed / total) * 100) : 0;

    return `
      <div class="glass-card project-card">
        <div>
          <div class="project-header">
            <h4 class="project-name">${escapeHtml(p.name)}</h4>
            ${canDelete ? `
              <button class="btn btn-danger btn-sm" onclick="handleDeleteProject(${p.projectId})" title="Delete Project">
                ✕
              </button>
            ` : ''}
          </div>
          <p class="project-desc">${escapeHtml(p.description || 'No description provided.')}</p>
        </div>

        <div>
          <div style="display: flex; justify-content: space-between; font-size: 0.75rem; margin-bottom: 0.35rem;">
            <span style="color: var(--text-secondary); font-weight: 500;">Progress</span>
            <span style="color: var(--accent-teal); font-weight: 600;">${percent}% (${completed}/${total} tasks)</span>
          </div>
          <div class="project-progress-bar">
            <div class="project-progress-fill" style="width: ${percent}%;"></div>
          </div>

          <div class="project-footer">
            <span>By <strong>${escapeHtml(p.creatorName || 'Admin')}</strong></span>
            <span>${p.startDate || ''} → ${p.endDate || ''}</span>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

function openProjectModal() {
  const overlay = document.getElementById('projectModalOverlay');
  if (overlay) {
    overlay.classList.add('active');
    document.getElementById('projName').focus();
    // Default dates
    const today = new Date().toISOString().split('T')[0];
    const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0];
    document.getElementById('projStartDate').value = today;
    document.getElementById('projEndDate').value = nextMonth;
  }
}

function closeProjectModal() {
  const overlay = document.getElementById('projectModalOverlay');
  if (overlay) overlay.classList.remove('active');
}

async function handleCreateProjectSubmit(event) {
  event.preventDefault();
  const name = document.getElementById('projName').value.trim();
  const description = document.getElementById('projDescription').value.trim();
  const startDate = document.getElementById('projStartDate').value;
  const endDate = document.getElementById('projEndDate').value;
  const btn = document.getElementById('btnSubmitProject');

  setButtonLoading(btn, true, 'Creating...');

  try {
    const data = await apiFetch('/api/projects', {
      method: 'POST',
      body: JSON.stringify({ name, description, startDate, endDate })
    });

    showToast('Project created successfully!', 'success');
    closeProjectModal();
    document.getElementById('createProjectForm').reset();
    await loadProjects();
    renderOverviewMetrics();
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false, 'Create Project');
  }
}

async function handleDeleteProject(projectId) {
  if (!confirm('Are you sure you want to delete this project? All associated tasks will be removed.')) {
    return;
  }

  try {
    await apiFetch(`/api/projects?id=${projectId}`, { method: 'DELETE' });
    showToast('Project deleted.', 'info');
    await loadProjects();
    await loadTasks();
    renderOverviewMetrics();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

function populateProjectDropdowns() {
  // Populate Task Modal Project select
  const taskProjectSelect = document.getElementById('taskProjectSelect');
  const kanbanFilter = document.getElementById('kanbanProjectFilter');

  if (taskProjectSelect) {
    taskProjectSelect.innerHTML = '<option value="">Select a project...</option>' +
      projectsList.map(p => `<option value="${p.projectId}">${escapeHtml(p.name)}</option>`).join('');
  }

  if (kanbanFilter) {
    const currentVal = kanbanFilter.value;
    kanbanFilter.innerHTML = '<option value="">All Projects</option>' +
      projectsList.map(p => `<option value="${p.projectId}" ${currentVal == p.projectId ? 'selected' : ''}>${escapeHtml(p.name)}</option>`).join('');
  }
}

// ============================================================================
// Tasks & Kanban Board Management
// ============================================================================

async function loadTasks() {
  const kanbanFilter = document.getElementById('kanbanProjectFilter');
  let url = '/api/tasks';
  if (kanbanFilter && kanbanFilter.value) {
    url += `?projectId=${kanbanFilter.value}`;
  }

  try {
    const data = await apiFetch(url);
    tasksList = data.tasks || [];
    renderKanban(tasksList);
    renderOverviewTasks();
    renderOverviewMetrics();
    return tasksList;
  } catch (err) {
    showToast('Failed to load tasks: ' + err.message, 'error');
    return [];
  }
}

function renderKanban(tasks) {
  const cardsTodo = document.getElementById('cardsTodo');
  const cardsInProgress = document.getElementById('cardsInProgress');
  const cardsDone = document.getElementById('cardsDone');

  const countTodo = document.getElementById('countTodo');
  const countInProgress = document.getElementById('countInProgress');
  const countDone = document.getElementById('countDone');

  if (!cardsTodo || !cardsInProgress || !cardsDone) return;

  const todoTasks = tasks.filter(t => t.status === 'TODO');
  const inProgressTasks = tasks.filter(t => t.status === 'IN_PROGRESS');
  const doneTasks = tasks.filter(t => t.status === 'DONE');

  if (countTodo) countTodo.textContent = todoTasks.length;
  if (countInProgress) countInProgress.textContent = inProgressTasks.length;
  if (countDone) countDone.textContent = doneTasks.length;

  cardsTodo.innerHTML = renderTaskCards(todoTasks);
  cardsInProgress.innerHTML = renderTaskCards(inProgressTasks);
  cardsDone.innerHTML = renderTaskCards(doneTasks);
}

function renderTaskCards(tasks) {
  if (!tasks || tasks.length === 0) {
    return `<div style="text-align: center; padding: 2rem 1rem; color: var(--text-muted); font-size: 0.8rem;">No tasks here</div>`;
  }

  const canDelete = currentUser.role === 'ADMIN' || currentUser.role === 'MANAGER';

  return tasks.map(t => {
    const priorityClass = `priority-${(t.priority || 'medium').toLowerCase()}`;
    const assigneeInitial = t.assignedToName ? t.assignedToName.charAt(0).toUpperCase() : '?';

    return `
      <div class="task-card" draggable="true" ondragstart="handleDragStart(event, ${t.taskId})" id="taskCard_${t.taskId}">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem;">
          <span class="badge ${priorityClass}">
            <span class="badge-dot"></span>${t.priority || 'MEDIUM'}
          </span>
          <span class="task-project-pill">${escapeHtml(t.projectName || 'Project')}</span>
        </div>

        <h5 class="task-title">${escapeHtml(t.title)}</h5>
        ${t.description ? `<p class="task-desc">${escapeHtml(t.description)}</p>` : ''}

        <div class="task-card-footer">
          <div class="task-assignee">
            <span class="assignee-avatar-sm">${assigneeInitial}</span>
            <span>${escapeHtml(t.assignedToName || 'Unassigned')}</span>
          </div>

          <div class="task-actions">
            <!-- Dropdown Status selector for mobile/quick change -->
            <select class="task-status-select" onchange="updateTaskStatus(${t.taskId}, this.value)">
              <option value="TODO" ${t.status === 'TODO' ? 'selected' : ''}>To Do</option>
              <option value="IN_PROGRESS" ${t.status === 'IN_PROGRESS' ? 'selected' : ''}>In Progress</option>
              <option value="DONE" ${t.status === 'DONE' ? 'selected' : ''}>Done</option>
            </select>

            ${canDelete ? `
              <button onclick="handleDeleteTask(${t.taskId})" style="background:none; border:none; color:var(--text-muted); cursor:pointer; font-size:0.75rem;" title="Delete Task" onmouseover="this.style.color='#ef4444'" onmouseout="this.style.color='var(--text-muted)'">✕</button>
            ` : ''}
          </div>
        </div>
      </div>
    `;
  }).join('');
}

// Drag & Drop HTML5 Handlers
function handleDragStart(event, taskId) {
  currentDraggedTaskId = taskId;
  event.dataTransfer.setData('text/plain', taskId);
  event.dataTransfer.effectAllowed = 'move';
  const card = document.getElementById(`taskCard_${taskId}`);
  if (card) card.classList.add('dragging');
}

function handleDragOver(event) {
  event.preventDefault();
  event.dataTransfer.dropEffect = 'move';
  const col = event.currentTarget.querySelector('.kanban-cards-wrapper');
  if (col) col.classList.add('drag-over');
}

function handleDragLeave(event) {
  const col = event.currentTarget.querySelector('.kanban-cards-wrapper');
  if (col) col.classList.remove('drag-over');
}

async function handleDrop(event, targetStatus) {
  event.preventDefault();
  const col = event.currentTarget.querySelector('.kanban-cards-wrapper');
  if (col) col.classList.remove('drag-over');

  const taskId = currentDraggedTaskId || parseInt(event.dataTransfer.getData('text/plain'));
  if (!taskId) return;

  const card = document.getElementById(`taskCard_${taskId}`);
  if (card) card.classList.remove('dragging');

  await updateTaskStatus(taskId, targetStatus);
}

async function updateTaskStatus(taskId, newStatus) {
  try {
    await apiFetch('/api/tasks/status', {
      method: 'POST',
      body: JSON.stringify({ taskId, status: newStatus })
    });

    // Update local task state
    const t = tasksList.find(item => item.taskId === taskId);
    if (t) t.status = newStatus;

    renderKanban(tasksList);
    renderOverviewMetrics();
    renderOverviewTasks();
    showToast(`Task moved to ${newStatus.replace('_', ' ')}`, 'success');
  } catch (err) {
    showToast('Failed to update status: ' + err.message, 'error');
    loadTasks();
  }
}

function openTaskModal() {
  const overlay = document.getElementById('taskModalOverlay');
  if (overlay) {
    overlay.classList.add('active');
    populateProjectDropdowns();
    populateAssigneeDropdown();
    // Default due date: 7 days from now
    const nextWeek = new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0];
    document.getElementById('taskDueDateInput').value = nextWeek;
    document.getElementById('taskTitleInput').focus();
  }
}

function closeTaskModal() {
  const overlay = document.getElementById('taskModalOverlay');
  if (overlay) overlay.classList.remove('active');
}

async function handleCreateTaskSubmit(event) {
  event.preventDefault();
  const projectId = parseInt(document.getElementById('taskProjectSelect').value);
  const title = document.getElementById('taskTitleInput').value.trim();
  const description = document.getElementById('taskDescInput').value.trim();
  const assignedTo = document.getElementById('taskAssigneeSelect').value;
  const priority = document.getElementById('taskPrioritySelect').value;
  const dueDate = document.getElementById('taskDueDateInput').value;
  const status = document.getElementById('taskStatusSelect').value;
  const btn = document.getElementById('btnSubmitTask');

  if (!projectId || !title || !dueDate) {
    showToast('Please fill all required fields.', 'error');
    return;
  }

  setButtonLoading(btn, true, 'Saving Task...');

  try {
    await apiFetch('/api/tasks', {
      method: 'POST',
      body: JSON.stringify({
        projectId,
        title,
        description,
        assignedTo: assignedTo ? parseInt(assignedTo) : null,
        priority,
        dueDate,
        status
      })
    });

    showToast('Task created successfully!', 'success');
    closeTaskModal();
    document.getElementById('createTaskForm').reset();
    await loadTasks();
    await loadProjects(); // Updates task counts
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false, 'Save Task');
  }
}

async function handleDeleteTask(taskId) {
  if (!confirm('Are you sure you want to delete this task?')) return;

  try {
    await apiFetch(`/api/tasks?id=${taskId}`, { method: 'DELETE' });
    showToast('Task deleted.', 'info');
    await loadTasks();
    await loadProjects();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// ============================================================================
// Team & RBAC Management
// ============================================================================

async function loadTeamUsers() {
  try {
    const data = await apiFetch('/api/users');
    usersList = data.users || [];
    renderTeamTable(usersList);
    populateAssigneeDropdown();
    return usersList;
  } catch (err) {
    return [];
  }
}

function populateAssigneeDropdown() {
  const select = document.getElementById('taskAssigneeSelect');
  if (!select) return;

  const currentVal = select.value;
  select.innerHTML = '<option value="">Unassigned</option>' +
    usersList.map(u => `<option value="${u.userId}" ${currentVal == u.userId ? 'selected' : ''}>${escapeHtml(u.username)} (${u.role})</option>`).join('');
}

function renderTeamTable(users) {
  const tbody = document.getElementById('teamTableBody');
  if (!tbody) return;

  if (!users || users.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 2rem;">No team members found</td></tr>`;
    return;
  }

  const isAdmin = currentUser.role === 'ADMIN';

  tbody.innerHTML = users.map(u => {
    const isSelf = u.userId === currentUser.userId;
    const roleBadge = `<span class="badge role-${(u.role || 'employee').toLowerCase()}">${u.role || 'EMPLOYEE'}</span>`;

    return `
      <tr>
        <td>
          <div style="display: flex; align-items: center; gap: 0.6rem;">
            <span class="assignee-avatar-sm">${escapeHtml(u.username.charAt(0).toUpperCase())}</span>
            <strong>${escapeHtml(u.username)}</strong> ${isSelf ? '<span style="font-size:0.7rem; color:var(--accent-teal);">(You)</span>' : ''}
          </div>
        </td>
        <td>${escapeHtml(u.email || '-')}</td>
        <td>${roleBadge}</td>
        <td>${u.createdAt ? u.createdAt.substring(0, 10) : 'Recent'}</td>
        <td>
          ${isAdmin && !isSelf ? `
            <select class="form-control" style="width: 140px; height: 30px; font-size: 0.75rem; padding: 0.2rem 0.5rem;" onchange="handleRoleUpdate(${u.userId}, this.value)">
              <option value="EMPLOYEE" ${u.role === 'EMPLOYEE' ? 'selected' : ''}>Employee</option>
              <option value="HR" ${u.role === 'HR' ? 'selected' : ''}>HR</option>
              <option value="MANAGER" ${u.role === 'MANAGER' ? 'selected' : ''}>Manager</option>
              <option value="ADMIN" ${u.role === 'ADMIN' ? 'selected' : ''}>Admin</option>
            </select>
          ` : '<span style="color: var(--text-muted); font-size: 0.8rem;">Protected</span>'}
        </td>
      </tr>
    `;
  }).join('');
}

async function handleRoleUpdate(userId, newRole) {
  try {
    await apiFetch('/api/users', {
      method: 'POST',
      body: JSON.stringify({ userId, role: newRole })
    });
    showToast(`Role updated to ${newRole}`, 'success');
    await loadTeamUsers();
  } catch (err) {
    showToast('Failed to update role: ' + err.message, 'error');
    loadTeamUsers();
  }
}

// ============================================================================
// Overview Metrics & Previews
// ============================================================================

function renderOverviewMetrics() {
  const mProjects = document.getElementById('metricTotalProjects');
  const mActive = document.getElementById('metricActiveTasks');
  const mCompleted = document.getElementById('metricCompletedTasks');

  if (mProjects) mProjects.textContent = projectsList.length;

  const activeCount = tasksList.filter(t => t.status !== 'DONE').length;
  const completedCount = tasksList.filter(t => t.status === 'DONE').length;

  if (mActive) mActive.textContent = activeCount;
  if (mCompleted) mCompleted.textContent = completedCount;
}

function renderOverviewTasks() {
  const container = document.getElementById('overviewTaskList');
  if (!container) return;

  const recent = tasksList.slice(0, 5);
  if (recent.length === 0) {
    container.innerHTML = `<div style="color: var(--text-muted); font-size: 0.85rem; padding: 1rem 0;">No tasks created yet. Click "+ New Task" above to add your first sprint task.</div>`;
    return;
  }

  container.innerHTML = recent.map(t => {
    const statusClass = `status-${(t.status || 'todo').toLowerCase()}`;
    const priorityClass = `priority-${(t.priority || 'medium').toLowerCase()}`;

    return `
      <div style="display: flex; justify-content: space-between; align-items: center; padding: 0.75rem 1rem; background: var(--bg-card); border: 1px solid var(--border-subtle); border-radius: var(--radius-md);">
        <div style="display: flex; align-items: center; gap: 0.75rem;">
          <span class="badge ${priorityClass}" style="font-size: 0.7rem;"><span class="badge-dot"></span>${t.priority}</span>
          <span style="font-weight: 500; font-size: 0.9rem;">${escapeHtml(t.title)}</span>
          <span style="font-size: 0.75rem; color: var(--text-muted);">(${escapeHtml(t.projectName)})</span>
        </div>
        <div style="display: flex; align-items: center; gap: 1rem;">
          <span style="font-size: 0.75rem; color: var(--text-secondary);">Due ${t.dueDate || 'Soon'}</span>
          <span class="badge ${statusClass}" style="font-size: 0.7rem;">${t.status.replace('_', ' ')}</span>
        </div>
      </div>
    `;
  }).join('');
}

// ============================================================================
// Real-Time Search Filter
// ============================================================================

function filterCurrentView(query) {
  const q = query.trim().toLowerCase();

  if (activeView === 'projects') {
    const filtered = projectsList.filter(p =>
      p.name.toLowerCase().includes(q) ||
      (p.description && p.description.toLowerCase().includes(q))
    );
    renderProjects(filtered);
  } else if (activeView === 'kanban') {
    const filtered = tasksList.filter(t =>
      t.title.toLowerCase().includes(q) ||
      (t.description && t.description.toLowerCase().includes(q)) ||
      (t.assignedToName && t.assignedToName.toLowerCase().includes(q))
    );
    renderKanban(filtered);
  } else if (activeView === 'team') {
    const filtered = usersList.filter(u =>
      u.username.toLowerCase().includes(q) ||
      (u.email && u.email.toLowerCase().includes(q))
    );
    renderTeamTable(filtered);
  }
}

// Utility: HTML escape
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
