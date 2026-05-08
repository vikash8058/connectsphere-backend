package com.connectsphere.post.aop;

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

    @Mock private JoinPoint joinPoint;
    @Mock private ProceedingJoinPoint proceedingJoinPoint;
    @Mock private Signature signature;

    @InjectMocks
    private LoggingAspect loggingAspect;

    private void setupMock(JoinPoint jp) {
        when(jp.getSignature()).thenReturn(signature);
        when(jp.getTarget()).thenReturn("TestTarget");
        when(signature.getName()).thenReturn("testMethod");
    }

    @Test
    void testLogMethodEntry() {
        setupMock(joinPoint);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg1"});
        loggingAspect.logMethodEntry(joinPoint);
        verify(joinPoint, atLeastOnce()).getSignature();
    }

    @Test
    void testLogMethodExit() {
        setupMock(joinPoint);
        loggingAspect.logMethodExit(joinPoint, "result");
        verify(joinPoint, atLeastOnce()).getSignature();
    }

    @Test
    void testLogException() {
        setupMock(joinPoint);
        loggingAspect.logException(joinPoint, new RuntimeException("Error"));
        verify(joinPoint, atLeastOnce()).getSignature();
    }

    @Test
    void testLogExecutionTime() throws Throwable {
        setupMock(proceedingJoinPoint);
        when(proceedingJoinPoint.proceed()).thenReturn("result");
        
        Object res = loggingAspect.logExecutionTime(proceedingJoinPoint);
        
        assertEquals("result", res);
        verify(proceedingJoinPoint).proceed();
    }
}
