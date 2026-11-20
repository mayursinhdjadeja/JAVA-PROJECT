package com.smartProjectPlanner.util;

import com.smartProjectPlanner.dao.ProjectDAO;
import com.smartProjectPlanner.model.Task;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background Daemon Worker that periodically monitors overdue tasks across tenants.
 * Demonstrates multithreading and SLA background watchdog monitoring.
 */
public class BackgroundTaskWorker implements Runnable {

    private final ProjectDAO projectDAO;
    private final long checkIntervalMs;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public BackgroundTaskWorker(ProjectDAO projectDAO) {
        this(projectDAO, 60000); // Default: every 60 seconds
    }

    public BackgroundTaskWorker(ProjectDAO projectDAO, long checkIntervalMs) {
        this.projectDAO = projectDAO;
        this.checkIntervalMs = checkIntervalMs;
    }

    @Override
    public void run() {
        System.out.println("[BackgroundTaskWorker] Background SLA Monitor started. Interval: " + (checkIntervalMs / 1000) + "s");

        while (running.get()) {
            try {
                checkOverdueTasks();
                Thread.sleep(checkIntervalMs);
            } catch (InterruptedException e) {
                System.out.println("[BackgroundTaskWorker] Worker interrupted. Shutting down.");
                break;
            } catch (Exception e) {
                System.err.println("[BackgroundTaskWorker] Error during scan: " + e.getMessage());
                try {
                    Thread.sleep(10000); // Back off on error
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        System.out.println("[BackgroundTaskWorker] Background SLA Monitor stopped.");
    }

    /**
     * Queries database for overdue tasks and triggers alerts.
     */
    private void checkOverdueTasks() {
        try {
            List<Task> overdueList = projectDAO.getOverdueTasks();
            if (overdueList != null && !overdueList.isEmpty()) {
                System.out.println("================================================================================");
                System.out.println("[BackgroundTaskWorker] WARNING: " + overdueList.size() + " OVERDUE TASK(S) DETECTED!");
                LocalDate today = LocalDate.now();

                for (Task task : overdueList) {
                    long daysOverdue = 0;
                    if (task.getDueDate() != null) {
                        LocalDate dueDate = task.getDueDate().toLocalDate();
                        daysOverdue = ChronoUnit.DAYS.between(dueDate, today);
                    }
                    String assignee = task.getAssignedToName() != null ? task.getAssignedToName() : "Unassigned";
                    String project = task.getProjectName() != null ? task.getProjectName() : "Project #" + task.getProjectId();

                    System.out.printf("  [!] Task #%d: '%s' | Project: %s | Assignee: %s | Priority: %s | Days Overdue: %d days%n",
                            task.getTaskId(), task.getTitle(), project, assignee, task.getPriority(), daysOverdue);
                }
                System.out.println("================================================================================");
            } else {
                System.out.println("[BackgroundTaskWorker] Health check passed: No overdue tasks currently pending.");
            }
        } catch (Exception e) {
            // If database is not yet up or connection temporarily drops, log quietly
            System.err.println("[BackgroundTaskWorker] Overdue check notice: " + e.getMessage());
        }
    }

    public void stop() {
        running.set(false);
    }
}
