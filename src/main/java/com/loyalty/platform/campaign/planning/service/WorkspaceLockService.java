package com.loyalty.platform.campaign.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 工作区分布式锁（开发阶段：基于 ConcurrentHashMap + ReentrantLock，生产可升级为 Redis）。
 */
@Service
public class WorkspaceLockService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceLockService.class);

    private final Map<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    /**
     * 尝试获取工作区锁。
     */
    public boolean tryLock(String workspaceId) {
        ReentrantLock lock = lockMap.computeIfAbsent(workspaceId, k -> new ReentrantLock());
        boolean acquired = lock.tryLock();
        if (acquired) {
            log.debug("Lock acquired for workspace: {}", workspaceId);
        }
        return acquired;
    }

    /**
     * 释放工作区锁。
     */
    public void unlock(String workspaceId) {
        ReentrantLock lock = lockMap.get(workspaceId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released for workspace: {}", workspaceId);
        }
    }

    /**
     * 带自动释放的锁执行器。
     */
    public <T> T executeWithLock(String workspaceId, Supplier<T> action) {
        if (!tryLock(workspaceId)) {
            throw new ConcurrentModificationException("Workspace is locked by another user: " + workspaceId);
        }
        try {
            return action.get();
        } finally {
            unlock(workspaceId);
        }
    }

    /**
     * 无返回值的锁执行器。
     */
    public void executeWithLockVoid(String workspaceId, Runnable action) {
        if (!tryLock(workspaceId)) {
            throw new ConcurrentModificationException("Workspace is locked by another user: " + workspaceId);
        }
        try {
            action.run();
        } finally {
            unlock(workspaceId);
        }
    }
}
