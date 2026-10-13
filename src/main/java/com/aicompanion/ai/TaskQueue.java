package com.aicompanion.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskQueue {
    private final List<CompanionTask> tasks = new ArrayList<>();
    private boolean paused = false;

    public void enqueue(CompanionTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    /**
     * Pushes a task onto the front of the queue (top of execution stack - LIFO).
     */
    public void push(CompanionTask task) {
        if (task != null) {
            tasks.add(0, task);
        }
    }

    /**
     * Pushes a sequential list of subtasks onto the front of the execution stack.
     * The subtasks are inserted such that subtasks.get(0) is executed first, followed by get(1), etc.
     */
    public void pushSubtasks(List<CompanionTask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) return;
        for (int i = subtasks.size() - 1; i >= 0; i--) {
            CompanionTask t = subtasks.get(i);
            if (t != null) {
                tasks.add(0, t);
            }
        }
    }

    public CompanionTask poll() {
        if (tasks.isEmpty() || paused) return null;
        return tasks.remove(0);
    }

    public CompanionTask peek() {
        if (tasks.isEmpty()) return null;
        return tasks.get(0);
    }

    public List<CompanionTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public void clear() {
        tasks.clear();
    }

    public void remove(int index) {
        if (index >= 0 && index < tasks.size()) {
            tasks.remove(index);
        }
    }

    public void moveUp(int index) {
        if (index > 0 && index < tasks.size()) {
            Collections.swap(tasks, index, index - 1);
        }
    }

    public void moveDown(int index) {
        if (index >= 0 && index < tasks.size() - 1) {
            Collections.swap(tasks, index, index + 1);
        }
    }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public int size() { return tasks.size(); }
    public boolean isEmpty() { return tasks.isEmpty(); }
}
