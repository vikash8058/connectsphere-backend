package com.connectsphere.auth.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @InjectMocks
    private LoggingAspect loggingAspect;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @Mock
    private Signature signature;

    @Test
    void testLogMethodEntry() {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{ "arg1" });

        loggingAspect.logMethodEntry(joinPoint);
        
        verify(joinPoint, times(1)).getTarget();
    }

    @Test
    void testLogMethodExit() {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        loggingAspect.logMethodExit(joinPoint, "result");
        
        verify(joinPoint, times(1)).getTarget();
    }

    @Test
    void testLogException() {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        loggingAspect.logException(joinPoint, new RuntimeException("error"));
        
        verify(joinPoint, times(1)).getTarget();
    }

    @Test
    void testLogExecutionTime() throws Throwable {
        when(proceedingJoinPoint.getTarget()).thenReturn(new Object());
        when(proceedingJoinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(proceedingJoinPoint.proceed()).thenReturn("result");

        Object result = loggingAspect.logExecutionTime(proceedingJoinPoint);
        
        assertEquals("result", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }
}
