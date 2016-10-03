package fi.thl.pivot.aspect;

import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import fi.thl.pivot.annotation.AuditedMethod;

/**
 * Captures all method calls in this project annotated with the {@link AuditedMethod}
 * annotation. All calls are then logged as either completed or failed
 * 
 * @author aleksiyrttiaho
 *
 */
@Component
@Aspect
public class AuditedAspect {

    private static final Logger LOG = Logger.getLogger(AuditedAspect.class);

    @Around("execution(* fi.thl.pivot..*(..)) && @annotation(fi.thl.pivot.annotation.AuditedMethod)")
    public Object invoke(ProceedingJoinPoint invocation) throws Throwable {
        try {
            Object o = invocation.proceed();
            LOG.debug("Call to " + invocation + " completed");
            return o;
        } catch (Throwable t) {
            LOG.debug("Call to " + invocation + " failed", t);
            throw t;
        }
    }
}
