package com.sismd.monitor.performance;

public interface PerformanceMonitor {
    void start();
    PerformanceSnapshot stop();
}
