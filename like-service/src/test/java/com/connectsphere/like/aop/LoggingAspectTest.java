package com.connectsphere.like.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @InjectMocks
    private LoggingAspect loggingAspect;

    @Mock private JoinPoint joinPoint;
    @Mock private ProceedingJoinPoint proceedingJoinPoint;
    @Mock private Signature signature;

    private void setupBase(JoinPoint jp) {
        when(jp.getTarget()).thenReturn(this);
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
    }

    @Test
    void logMethodEntry() {
        setupBase(joinPoint);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg1"});
        loggingAspect.logMethodEntry(joinPoint);
        verify(joinPoint).getArgs();
    }

    @Test
    void logMethodExit() {
        setupBase(joinPoint);
        loggingAspect.logMethodExit(joinPoint, "result");
        verify(joinPoint).getSignature();
    }

    @Test
    void logMethodExit_nullResult() {
        setupBase(joinPoint);
        loggingAspect.logMethodExit(joinPoint, null);
        verify(joinPoint).getSignature();
    }

    @Test
    void logException() {
        setupBase(joinPoint);
        RuntimeException ex = new RuntimeException("Test error");
        loggingAspect.logException(joinPoint, ex);
        verify(joinPoint).getSignature();
    }

    @Test
    void logExecutionTime() throws Throwable {
        when(proceedingJoinPoint.getTarget()).thenReturn(this);
        when(proceedingJoinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(proceedingJoinPoint.proceed()).thenReturn("done");

        Object result = loggingAspect.logExecutionTime(proceedingJoinPoint);
        assertEquals("done", result);
    }
}
