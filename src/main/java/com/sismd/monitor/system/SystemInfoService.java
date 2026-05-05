package com.sismd.monitor.system;

@FunctionalInterface
public interface SystemInfoService {
    SystemInfoSnapshot read();
}
