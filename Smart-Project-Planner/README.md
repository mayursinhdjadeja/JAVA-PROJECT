# Smart Project Planner

**Enterprise Multi-Tenant SaaS Project & Task Management Platform**

Built with pure **Core Java** (native `com.sun.net.httpserver.HttpServer`), **MySQL** via JDBC (`PreparedStatement` only), **Vanilla HTML5/CSS3/ES6+**, and **Groq Cloud AI (LLaMA-3)**.

---

## 🚀 Key Highlights & Architectural Constraints
- **Zero Spring Boot / External Frameworks:** Operates directly on the built-in JDK `HttpServer`.
- **Zero JSON Third-Party Libraries:** Custom manual JSON tokenizer, parser, and serializer built directly into `ProjectController.java`.
- **Strict Multi-Tenancy Data Isolation:** Each tenant has an isolated workspace identified by unique `company_code` and `company_id`. Zero cross-tenant data leakage.
- **ACID Transactional Registration:** Atomic company and administrator onboarding using JDBC transactions (`connection.setAutoCommit(false)` and `commit()` / `rollback()`).
- **Role-Based Access Control (RBAC):** `ADMIN`, `MANAGER`, `HR`, and `EMPLOYEE` permissions strictly enforced on both Backend Service and Frontend UI.
- **Groq Cloud AI Integration:** Auto-generates structured technical specifications, acceptance criteria, and subtasks via `java.net.http.HttpClient`.
- **24/7 SLA Watchdog Daemon:** Multithreaded background worker continuously monitoring overdue tasks across all tenants.
- **Vercel / Linear Aesthetic:** Premium dark theme with frosted glassmorphism, fluid animations, and native HTML5 Kanban drag-and-drop.

---

## 📁 File Structure
```
Smart-Project-Planner/
├── database/
│   └── schema.sql                                     # Complete MySQL schema & seed data
├── backend/
│   ├── bin/                                           # Compiled class binaries
│   ├── lib/                                           # Place mysql-connector-j.jar here
│   └── src/
│       └── com/smartProjectPlanner/
│           ├── SmartProjectPlannerApp.java            # Main server & static file handler
│           ├── config/
│           │   └── DatabaseConnector.java             # JDBC connection lifecycle & configuration
│           ├── model/
│           │   ├── Role.java                          # ADMIN, MANAGER, HR, EMPLOYEE
│           │   ├── Priority.java                      # LOW, MEDIUM, HIGH, CRITICAL
│           │   ├── Status.java                        # TODO, IN_PROGRESS, DONE
│           │   ├── Company.java                       # Tenant Company model
│           │   ├── User.java                          # User model with RBAC
│           │   ├── Project.java                       # Project model
│           │   └── Task.java                          # Task model for Kanban board
│           ├── exception/
│           │   └── ProjectException.java              # Checked business validation exception
│           ├── dao/
│           │   └── ProjectDAO.java                    # JDBC operations, Transactions, JOINs
│           ├── service/
│           │   ├── ProjectService.java                # Service layer interface
│           │   └── ProjectServiceImplementation.java  # Business logic & RBAC validation
│           ├── controller/
│           │   └── ProjectController.java             # HTTP handler, Manual JSON parser, CORS
│           └── util/
│               ├── SecurityUtil.java                  # SHA-256 hashing & signed auth tokens
│               ├── AIService.java                     # Groq Cloud API integration
│               └── BackgroundTaskWorker.java          # Periodic SLA monitor (Multithreading)
├── frontend/
│   ├── index.html                                     # Animated Landing Page & CSS Mockup
│   ├── login.html                                     # Split-screen Auth with demo pills
│   ├── register.html                                  # Company & Employee Onboarding tabs
│   ├── dashboard.html                                 # SaaS Workspace, Kanban Board, Team view
│   ├── css/
│   │   └── style.css                                  # Linear/Vercel Dark Theme & Glassmorphism
│   └── js/
│       ├── auth.js                                    # Auth API calls & session guard
│       ├── app.js                                     # Kanban drag-and-drop, RBAC, Projects
│       └── ai.js                                      # Groq AI prompt & modal integration
├── build.bat                                          # One-click Windows compile script
├── run.bat                                            # One-click Windows launch script
└── README.md
```

---

## 🛠️ Getting Started

### 1. Database Setup (MySQL)
Make sure MySQL server is running. Execute `database/schema.sql`:
```bash
mysql -u root -p < database/schema.sql
```
*Default connection parameters configured in `DatabaseConnector.java`:*
- URL: `jdbc:mysql://localhost:3306/smart_project_planner`
- User: `root`
- Password: `root` (or set via `DB_PASSWORD` environment variable)

### 2. Add MySQL Connector JAR
Download `mysql-connector-j-8.x.jar` and place it in the `backend/lib/` folder:
```
backend/lib/mysql-connector-j-8.3.0.jar
```

### 3. (Optional) Configure Groq API Key
To enable live Groq LLaMA-3 task generation, set the environment variable:
```cmd
set GROQ_API_KEY=gsk_your_groq_api_key_here
```
*(If no API key is set, the application automatically falls back to an intelligent offline template generator without failing).*

### 4. Compile the Application
Run the build script:
```cmd
build.bat
```
Or manually:
```cmd
javac -d backend/bin backend/src/com/smartProjectPlanner/config/*.java backend/src/com/smartProjectPlanner/model/*.java backend/src/com/smartProjectPlanner/exception/*.java backend/src/com/smartProjectPlanner/util/*.java backend/src/com/smartProjectPlanner/dao/*.java backend/src/com/smartProjectPlanner/service/*.java backend/src/com/smartProjectPlanner/controller/*.java backend/src/com/smartProjectPlanner/*.java
```

### 5. Launch the Server
Run the launch script:
```cmd
run.bat
```
Or manually:
```cmd
java -cp "backend/bin;backend/lib/*" com.smartProjectPlanner.SmartProjectPlannerApp
```

Open your browser at: **`http://localhost:8080/`**

---

## 🔑 Demo Seed Accounts
All seed accounts use the default password: **`Admin@123`**

| Company Name | Company Code | Username | Role | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Acme Technologies** | `ACME-2026` | `alex_admin` | **ADMIN** | Full administrative rights, role updates |
| **Acme Technologies** | `ACME-2026` | `sarah_pm` | **MANAGER** | Project & Task creation, deletion |
| **Acme Technologies** | `ACME-2026` | `elena_hr` | **HR** | Team management |
| **Acme Technologies** | `ACME-2026` | `dev_david` | **EMPLOYEE** | Kanban status movement, restricted admin UI |
| **Nexus Cybernetics** | `NEXUS-99` | `nexus_admin` | **ADMIN** | **Tenant 2** (Verifies strict data isolation) |

---

## 🌐 API Reference

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/login` | Authenticate username & password | No |
| `POST` | `/api/register/company` | Atomic company & admin registration | No |
| `POST` | `/api/register/employee` | Register employee under company code | No |
| `GET` | `/api/projects` | List all projects for tenant company | Yes |
| `POST` | `/api/projects` | Create a new project (Admin/Manager) | Yes |
| `DELETE` | `/api/projects?id={id}` | Delete project & cascade tasks | Yes |
| `GET` | `/api/tasks` | List company tasks (optional `?projectId=`) | Yes |
| `POST` | `/api/tasks` | Create task with priority & due date | Yes |
| `POST` | `/api/tasks/status` | Update task status (TODO, IN_PROGRESS, DONE) | Yes |
| `DELETE` | `/api/tasks?id={id}` | Delete task | Yes |
| `GET` | `/api/users` | List company team members | Yes |
| `POST` | `/api/users` | Update employee role (Admin only) | Yes |
| `POST` | `/api/ai/generate` | Generate task specs via Groq Cloud API | Yes |
| `GET` | `/api/health` | Service uptime and heartbeat | No |
