package com.sabeeldev.alarmclock.service;

import java.lang.System;

@kotlin.Metadata(mv = {1, 5, 1}, k = 1, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nJ\u0010\u0010\u000b\u001a\u00020\u00062\u0006\u0010\f\u001a\u00020\nH\u0014R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/sabeeldev/alarmclock/service/BootService;", "Landroidx/core/app/JobIntentService;", "()V", "JOB_ID", "", "enqueueWork", "", "context", "Landroid/content/Context;", "work", "Landroid/content/Intent;", "onHandleWork", "intent", "app_debug"})
public final class BootService extends androidx.core.app.JobIntentService {
    private final int JOB_ID = 100;
    
    public BootService() {
        super();
    }
    
    @java.lang.Override()
    protected void onHandleWork(@org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
    
    public final void enqueueWork(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.content.Intent work) {
    }
}