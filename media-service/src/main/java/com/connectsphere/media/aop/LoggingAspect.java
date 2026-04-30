package com.connectsphere.media.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;


@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Pointcut("execution(* com.connectsphere.media.controller..*(..))")
    public void controllerLayer() {}

    @Pointcut("execution(* com.connectsphere.media.service..*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(* com.connectsphere.media.repository..*(..))")
    public void repositoryLayer() {}

    @Pointcut("execution(* com.connectsphere.media.scheduler..*(..))")
    public void schedulerLayer() {}

    @Pointcut("controllerLayer() || serviceLayer() || repositoryLayer() || schedulerLayer()")
    public void applicationLayer() {}

    @Before("applicationLayer()")
    public void logMethodEntry(JoinPoint jp) {
        log.debug(">>> ENTERING [{}.{}] args: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                Arrays.toString(jp.getArgs()));
    }

    @AfterReturning(pointcut = "applicationLayer()", returning = "result")
    public void logMethodExit(JoinPoint jp, Object result) {
        log.debug("<<< EXITING [{}.{}] returned: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                result != null ? result.getClass().getSimpleName() : "void");
    }

    @AfterThrowing(pointcut = "applicationLayer()", throwing = "ex")
    public void logException(JoinPoint jp, Throwable ex) {
        log.error("!!! EXCEPTION in [{}.{}] | {}: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                ex.getClass().getSimpleName(),
                ex.getMessage());
    }

    @Around("serviceLayer() || schedulerLayer()")
    public Object logExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();
        long elapsed = System.currentTimeMillis() - start;

        log.info("⏱ [{}.{}] completed in {} ms",
                pjp.getTarget().getClass().getSimpleName(),
                pjp.getSignature().getName(), elapsed);

        // NFR: media feed and story feed must respond within 1.5 seconds
        if (elapsed > 1500) {
            log.warn("⚠ SLOW METHOD: [{}.{}] took {} ms (threshold: 1500ms)",
                    pjp.getTarget().getClass().getSimpleName(),
                    pjp.getSignature().getName(), elapsed);
        }
        return result;
    }
}
