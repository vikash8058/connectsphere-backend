package com.connectsphere.follow.aop;

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

    @InjectMocks private LoggingAspect loggingAspect;
    @Mock private JoinPoint joinPoint;
    @Mock private ProceedingJoinPoint proceedingJoinPoint;
    @Mock private Signature signature;

    @Test
    void logMethodEntry() {
        when(joinPoint.getTarget()).thenReturn(this);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg1"});

        assertDoesNotThrow(() -> loggingAspect.logMethodEntry(joinPoint));
    }

    @Test
    void logMethodExit() {
        when(joinPoint.getTarget()).thenReturn(this);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        assertDoesNotThrow(() -> loggingAspect.logMethodExit(joinPoint, "result"));
    }

    @Test
    void logMethodExit_nullResult() {
        when(joinPoint.getTarget()).thenReturn(this);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        assertDoesNotThrow(() -> loggingAspect.logMethodExit(joinPoint, null));
    }

    @Test
    void logException() {
        when(joinPoint.getTarget()).thenReturn(this);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        assertDoesNotThrow(() -> loggingAspect.logException(joinPoint, new RuntimeException("fail")));
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
