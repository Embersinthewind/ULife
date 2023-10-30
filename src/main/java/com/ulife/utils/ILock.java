package com.ulife.utils;

public interface ILock {


    boolean tryLock(long timeoutSec);

    public void unLock();

}
