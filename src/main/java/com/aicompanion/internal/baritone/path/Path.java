package com.aicompanion.internal.baritone.path;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered sequence of PathNodes representing a planned route.
 * Adapted from Baritone (LGPL-3.0) Path.
 */
public class Path {
    private final List<PathNode> nodes;
    private int currentIndex = 0;
    private final double totalCost;

    public Path(List<PathNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.totalCost = this.nodes.isEmpty() ? 0.0 : this.nodes.get(this.nodes.size() - 1).gCost;
    }

    public static Path fromEndNode(PathNode endNode) {
        List<PathNode> list = new ArrayList<>();
        PathNode curr = endNode;
        while (curr != null) {
            list.add(curr);
            curr = curr.parent;
        }
        Collections.reverse(list);
        return new Path(list);
    }

    public int length() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public boolean isFinished() {
        return currentIndex >= nodes.size();
    }

    public PathNode getCurrentNode() {
        if (currentIndex < nodes.size()) {
            return nodes.get(currentIndex);
        }
        return null;
    }

    public PathNode getNextNode() {
        if (currentIndex + 1 < nodes.size()) {
            return nodes.get(currentIndex + 1);
        }
        return null;
    }

    public PathNode getNode(int index) {
        if (index >= 0 && index < nodes.size()) {
            return nodes.get(index);
        }
        return null;
    }

    public void advance() {
        currentIndex++;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = Math.max(0, Math.min(nodes.size(), index));
    }

    public List<PathNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<BlockPos> toBlockPosList() {
        List<BlockPos> list = new ArrayList<>(nodes.size());
        for (PathNode node : nodes) {
            list.add(node.getPos());
        }
        return list;
    }

    public double getTotalCost() {
        return totalCost;
    }
}
