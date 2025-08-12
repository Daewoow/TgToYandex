package org.shaurmeow.tgtoyandex.clients;

import org.drinkless.tdlib.TdApi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CustomClient {
    private final int nativeClientId;
    public static final String[] GIT_COMMIT_HASH = new String[]{"sha-256", "sha-384", "sha-512"};
    private static final ConcurrentHashMap<Integer, CustomClient.ExceptionHandler> defaultExceptionHandlers;
    private static final ConcurrentHashMap<Integer, CustomClient.Handler> updateHandlers;
    private static final ConcurrentHashMap<Long, CustomClient.Handler> handlers;
    private static final AtomicLong currentQueryId;
    private static final AtomicLong clientCount;
    private static final CustomClient.ResponseReceiver responseReceiver;

    public void send(TdApi.Function query, CustomClient.ResultHandler resultHandler, CustomClient.ExceptionHandler exceptionHandler) {
        long queryId = currentQueryId.incrementAndGet();
        if (resultHandler != null) {
            handlers.put(queryId, new CustomClient.Handler(resultHandler, exceptionHandler));
        }

        nativeClientSend(this.nativeClientId, queryId, query);
    }

    public void send(TdApi.Function query, CustomClient.ResultHandler resultHandler) {
        this.send(query, resultHandler, (CustomClient.ExceptionHandler)null);
    }

    public static TdApi.Object execute(TdApi.Function query) {
        return nativeClientExecute(query);
    }

    public static CustomClient create(CustomClient.ResultHandler updateHandler, CustomClient.ExceptionHandler updateExceptionHandler, CustomClient.ExceptionHandler defaultExceptionHandler) {
        CustomClient client = new CustomClient(updateHandler, updateExceptionHandler, defaultExceptionHandler);
        synchronized(responseReceiver) {
            if (!responseReceiver.isRun) {
                responseReceiver.isRun = true;
                Thread receiverThread = new Thread(responseReceiver, "TDLib thread");
                receiverThread.setDaemon(true);
                receiverThread.start();
            }

            return client;
        }
    }

    public static void setLogMessageHandler(int maxVerbosityLevel, CustomClient.LogMessageHandler logMessageHandler) {
        nativeClientSetLogMessageHandler(maxVerbosityLevel, logMessageHandler);
    }

    private CustomClient(CustomClient.ResultHandler updateHandler, CustomClient.ExceptionHandler updateExceptionHandler, CustomClient.ExceptionHandler defaultExceptionHandler) {
        clientCount.incrementAndGet();
        this.nativeClientId = createNativeClient();
        if (updateHandler != null) {
            updateHandlers.put(this.nativeClientId, new CustomClient.Handler(updateHandler, updateExceptionHandler));
        }

        if (defaultExceptionHandler != null) {
            defaultExceptionHandlers.put(this.nativeClientId, defaultExceptionHandler);
        }

        this.send(new TdApi.GetOption("version"), (CustomClient.ResultHandler)null, (CustomClient.ExceptionHandler)null);
    }
//
//    protected void finalize() throws Throwable {
//        this.send(new TdApi.Close(), (CustomClient.ResultHandler)null, (CustomClient.ExceptionHandler)null);
//    }

    private static native int createNativeClient();

    private static native void nativeClientSend(int var0, long var1, TdApi.Function var3);

    private static native int nativeClientReceive(int[] var0, long[] var1, TdApi.Object[] var2, double var3);

    private static native TdApi.Object nativeClientExecute(TdApi.Function var0);

    private static native void nativeClientSetLogMessageHandler(int var0, CustomClient.LogMessageHandler var1);

    static {
        try {
            System.loadLibrary("tdjni");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }

        defaultExceptionHandlers = new ConcurrentHashMap();
        updateHandlers = new ConcurrentHashMap();
        handlers = new ConcurrentHashMap();
        currentQueryId = new AtomicLong();
        clientCount = new AtomicLong();
        responseReceiver = new CustomClient.ResponseReceiver();
    }

    private static class ResponseReceiver implements Runnable {
        public boolean isRun;
        private static final int MAX_EVENTS = 1000;
        private final int[] clientIds;
        private final long[] eventIds;
        private final TdApi.Object[] events;

        private ResponseReceiver() {
            this.isRun = false;
            this.clientIds = new int[1000];
            this.eventIds = new long[1000];
            this.events = new TdApi.Object[1000];
        }

        public void run() {
            while(true) {
                int resultN = CustomClient.nativeClientReceive(this.clientIds, this.eventIds, this.events, (double)100000.0F);

                for(int i = 0; i < resultN; ++i) {
                    this.processResult(this.clientIds[i], this.eventIds[i], this.events[i]);
                    this.events[i] = null;
                }
            }
        }

        private void processResult(int clientId, long id, TdApi.Object object) {
            boolean isClosed = false;
            if (id == 0L && object instanceof TdApi.UpdateAuthorizationState) {
                TdApi.AuthorizationState authorizationState = ((TdApi.UpdateAuthorizationState)object).authorizationState;
                if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
                    isClosed = true;
                }
            }

            CustomClient.Handler handler = id == 0L ? (CustomClient.Handler) CustomClient.updateHandlers.get(clientId) : (CustomClient.Handler) CustomClient.handlers.remove(id);
            if (handler != null) {
                try {
                    handler.resultHandler.onResult(object);
                } catch (Throwable var11) {
                    Throwable cause = var11;
                    CustomClient.ExceptionHandler exceptionHandler = handler.exceptionHandler;
                    if (exceptionHandler == null) {
                        exceptionHandler = (CustomClient.ExceptionHandler) CustomClient.defaultExceptionHandlers.get(clientId);
                    }

                    if (exceptionHandler != null) {
                        try {
                            exceptionHandler.onException(cause);
                        } catch (Throwable var10) {
                        }
                    }
                }
            }

            if (isClosed) {
                CustomClient.updateHandlers.remove(clientId);
                CustomClient.defaultExceptionHandlers.remove(clientId);
                CustomClient.clientCount.decrementAndGet();
            }

        }
    }

    private static class Handler {
        final CustomClient.ResultHandler resultHandler;
        final CustomClient.ExceptionHandler exceptionHandler;

        Handler(CustomClient.ResultHandler resultHandler, CustomClient.ExceptionHandler exceptionHandler) {
            this.resultHandler = resultHandler;
            this.exceptionHandler = exceptionHandler;
        }
    }

    public interface ExceptionHandler {
        void onException(Throwable var1);
    }

    public interface LogMessageHandler {
        void onLogMessage(int var1, String var2);
    }

    public interface ResultHandler {
        void onResult(TdApi.Object var1);
    }
}
