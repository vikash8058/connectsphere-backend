package com.connectsphere.search.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @BeforeEach
    void setUp() {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(joinPoint.getTarget()).thenReturn(new Object());
        lenient().when(signature.getName()).thenReturn("testMethod");
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{});

        lenient().when(proceedingJoinPoint.getSignature()).thenReturn(signature);
        lenient().when(proceedingJoinPoint.getTarget()).thenReturn(new Object());
    }

    @Test
    void logMethodEntry() {
        loggingAspect.logMethodEntry(joinPoint);
        verify(joinPoint, atLeastOnce()).getSignature();
    }

    @Test
    void logMethodExit() {
        loggingAspect.logMethodExit(joinPoint, "result");
        verify(joinPoint, atLeastOnce()).getSignature();
    }

    @Test
    void logException() {
        loggingAspect.logException(joinPoint, new RuntimeException("error"));
        verify(joinPoint, atLeastOnce()).getSignature();
    }

    @Test
    void logExecutionTime() throws Throwable {
        when(proceedingJoinPoint.proceed()).thenReturn("done");
        Object result = loggingAspect.logExecutionTime(proceedingJoinPoint);
        assertNotNull(result);
        verify(proceedingJoinPoint).proceed();
    }

    @Test
    void logExecutionTime_slowMethod() throws Throwable {
        when(proceedingJoinPoint.proceed()).thenAnswer(invocation -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 1600) {
                // Busy wait to exceed 1500ms threshold without using Thread.sleep()
            }
            return "slow";
        });
        loggingAspect.logExecutionTime(proceedingJoinPoint);
        verify(proceedingJoinPoint).proceed();
    }
}
