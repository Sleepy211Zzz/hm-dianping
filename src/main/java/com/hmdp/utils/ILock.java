package com.hmdp.utils;

/**
 * ClassName: ILock
 * Description:
 * Author
 * Create 2025/1/29 10:19
 * VERSION 1.0
 */
public interface ILock {

    boolean tryLock(long timeoutSec);

    void unLock();
}
