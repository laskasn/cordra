package net.cnri.cordra;

public class RequestContextHolder {
    private static ThreadLocal<RequestContext> threadLocalRequestContext = new ThreadLocal<>();

    public static void clear() {
        threadLocalRequestContext.remove();
    }

    public static void set(RequestContext requestContext) {
        if (requestContext == null) {
            threadLocalRequestContext.remove();
        } else {
            threadLocalRequestContext.set(requestContext);
        }
    }

    public static RequestContext get() {
        return threadLocalRequestContext.get();
    }

    public static boolean beforeAuthCall() {
        RequestContext context = RequestContextHolder.get();
        if (context == null) {
            context = new RequestContext();
            context.setAuthCall(true);
            RequestContextHolder.set(context);
            return true;
        } else {
            context.setAuthCall(true);
            return false;
        }
    }

    public static void afterAuthCall(boolean wasNoContext) {
        if (wasNoContext) {
            RequestContextHolder.clear();
        } else {
            RequestContextHolder.get().setAuthCall(false);
        }
    }

}
