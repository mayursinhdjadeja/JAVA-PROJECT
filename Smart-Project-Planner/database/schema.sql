-- ============================================================================
-- Smart Project Planner - Database Schema
-- Multi-Tenant SaaS Database Schema for MySQL
-- ============================================================================

CREATE DATABASE IF NOT EXISTS smart_project_planner
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smart_project_planner;

-- ----------------------------------------------------------------------------
-- 1. Companies Table (Tenants)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companies (
    company_id INT AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    company_code VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. Users Table (Tenant Members with RBAC)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    company_id INT NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL, -- SHA-256 hashed
    email VARCHAR(150) NOT NULL,
    role ENUM('ADMIN', 'MANAGER', 'HR', 'EMPLOYEE') NOT NULL DEFAULT 'EMPLOYEE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_company
        FOREIGN KEY (company_id) REFERENCES companies(company_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 3. Projects Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS projects (
    project_id INT AUTO_INCREMENT PRIMARY KEY,
    company_id INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_company
        FOREIGN KEY (company_id) REFERENCES companies(company_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_projects_creator
        FOREIGN KEY (created_by) REFERENCES users(user_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 4. Tasks Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tasks (
    task_id INT AUTO_INCREMENT PRIMARY KEY,
    project_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    assigned_to INT NULL,
    priority ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    due_date DATE NOT NULL,
    status ENUM('TODO', 'IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'TODO',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_tasks_assignee
        FOREIGN KEY (assigned_to) REFERENCES users(user_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Indexes for Performance & Multi-Tenancy Lookups
-- ----------------------------------------------------------------------------
CREATE INDEX idx_users_company ON users(company_id);
CREATE INDEX idx_projects_company ON projects(company_id);
CREATE INDEX idx_tasks_project ON tasks(project_id);
CREATE INDEX idx_tasks_assigned ON tasks(assigned_to);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);

-- ----------------------------------------------------------------------------
-- Seed Data for Immediate Testing
-- Default Password for all seed users is 'Admin@123'
-- SHA-256 of 'Admin@123': 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
-- ----------------------------------------------------------------------------

-- Seed Companies
INSERT INTO companies (company_id, company_name, company_code) VALUES
(1, 'Acme Technologies', 'ACME-2026'),
(2, 'Nexus Cybernetics', 'NEXUS-99')
ON DUPLICATE KEY UPDATE company_name=VALUES(company_name);

-- Seed Users for Acme Technologies (Company 1)
INSERT INTO users (user_id, company_id, username, password, email, role) VALUES
(1, 1, 'alex_admin', 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7', 'alex@acme.io', 'ADMIN'),
(2, 1, 'sarah_pm', 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7', 'sarah@acme.io', 'MANAGER'),
(3, 1, 'elena_hr', 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7', 'elena@acme.io', 'HR'),
(4, 1, 'dev_david', 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7', 'david@acme.io', 'EMPLOYEE'),
(5, 1, 'dev_maya', 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7', 'maya@acme.io', 'EMPLOYEE')
ON DUPLICATE KEY UPDATE username=VALUES(username);

-- Seed Users for Nexus Cybernetics (Company 2 - Tenant Isolation Test)
INSERT INTO users (user_id, company_id, username, password, email, role) VALUES
(6, 2, 'nexus_admin', 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7', 'admin@nexus.io', 'ADMIN'),
(7, 2, 'nexus_dev', 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7', 'dev@nexus.io', 'EMPLOYEE')
ON DUPLICATE KEY UPDATE username=VALUES(username);

-- Seed Projects for Acme Technologies
INSERT INTO projects (project_id, company_id, name, description, start_date, end_date, created_by) VALUES
(1, 1, 'Cloud Migration 2026', 'Migrate legacy microservices to high-availability multi-region Kubernetes cluster.', '2026-01-10', '2026-06-30', 1),
(2, 1, 'Customer AI Portal', 'AI-assisted customer portal integrating conversational agents and ticket automation.', '2026-02-01', '2026-08-15', 2),
(3, 2, 'Quantum Sensor Core', 'Sensor fusion algorithm library for high-speed edge devices.', '2026-03-01', '2026-11-30', 6)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Seed Tasks for Acme Technologies Projects
INSERT INTO tasks (task_id, project_id, title, description, assigned_to, priority, due_date, status) VALUES
(1, 1, 'Audit legacy database schemas', 'Analyze normalization, query bottlenecks, and migration compatibility.', 4, 'HIGH', '2026-03-25', 'DONE'),
(2, 1, 'Configure Terraform VPC & Subnets', 'Provision production and staging VPCs across 3 availability zones.', 4, 'CRITICAL', '2026-04-10', 'IN_PROGRESS'),
(3, 1, 'Implement zero-downtime replication', 'Set up Kafka CDC connector for real-time change data capture.', 5, 'HIGH', '2026-04-20', 'TODO'),
(4, 2, 'Design Groq LLM prompt pipelines', 'Establish few-shot templates and evaluation harness for user intent parsing.', 5, 'MEDIUM', '2026-04-05', 'IN_PROGRESS'),
(5, 2, 'Build Glassmorphism dashboard UI', 'Implement responsive dark theme matching Linear and Vercel design standards.', 4, 'LOW', '2026-04-15', 'DONE'),
(6, 2, 'Set up overdue task background monitor', 'Background worker daemon checking for SLA breaches and task deadlines.', 2, 'HIGH', '2026-03-01', 'TODO'),
(7, 3, 'Calibrate IMU noise filtering', 'Noise reduction filter using extended Kalman filters.', 7, 'CRITICAL', '2026-05-15', 'IN_PROGRESS')
ON DUPLICATE KEY UPDATE title=VALUES(title);
