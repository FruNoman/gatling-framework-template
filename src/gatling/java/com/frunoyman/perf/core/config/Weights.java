package com.frunoyman.perf.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A scenario's mix in percent — the scenario names its branches ({@code company}, {@code deal} …) and their default shares (the load
 * model's), {@code -Dweights=company:40,deal:20,…} replaces them for a run. The shares must add up to 100: a gap would be a call
 * never made, an excess a branch Gatling never reaches.
 */
public final class Weights {

    private final Map<String, Double> percent;

    private Weights(Map<String, Double> percent) {
        this.percent = percent;
    }

    public static Weights of(Map<String, Integer> defaults) {
        Map<String, Double> percent = new LinkedHashMap<>();
        String override = LoadProfile.property("weights", "");
        if (override.isBlank()) {
            defaults.forEach((branch, share) -> percent.put(branch, share.doubleValue()));
        } else {
            for (String entry : override.split(",")) {
                String[] branchAndShare = entry.trim().split(":");
                if (!defaults.containsKey(branchAndShare[0])) {
                    throw new IllegalArgumentException("Unknown branch '" + branchAndShare[0] + "' — known: " + defaults.keySet());
                }
                percent.put(branchAndShare[0], Double.parseDouble(branchAndShare[1]));
            }
            defaults.keySet().forEach(branch -> percent.putIfAbsent(branch, 0.0));
        }
        double total = percent.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 100) > 0.001) {
            throw new IllegalArgumentException("Weights add up to " + total + " %, not 100: " + percent);
        }
        return new Weights(percent);
    }

    /** A branch's share, in percent. */
    public double of(String branch) {
        Double share = percent.get(branch);
        if (share == null) {
            throw new IllegalArgumentException("No weight for '" + branch + "'");
        }
        return share;
    }

    @Override
    public String toString() {
        return percent.toString();
    }
}
