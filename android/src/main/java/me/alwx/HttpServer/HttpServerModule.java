package me.alwx.HttpServer;

import android.content.Context;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import java.io.IOException;

import android.util.Log;

public class HttpServerModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    ReactApplicationContext reactContext;

    private static final String MODULE_NAME = "HttpServer";

    private static int port;
    private static Server server = null;

    public HttpServerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void start(int port, String serviceName) {
        Log.d(MODULE_NAME, "Initializing server...");
        this.port = port;

        startServer();
    }

    @ReactMethod
    public void stop() {
        Log.d(MODULE_NAME, "Stopping server...");

        stopServer();
    }

    @ReactMethod
    public void respond(String requestId, int code, String type, String body, ReadableMap opts) {
        if (server != null) {
            server.respondFixed(requestId, code, type, body, opts);
        }
    }

    @ReactMethod
    public void respondFile(String requestId, int code, String type, String fileName, ReadableMap opts) {
        if (server != null) {
            server.respondFile(reactContext, requestId, code, type, fileName, opts);
        }
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        stopServer();
    }

    private void startServer() {
        if (this.port == 0) {
            return;
        }

        if (server == null) {
            server = new Server(reactContext, port);
        }
        try {
            server.start();
        } catch (IOException e) {
            Log.e(MODULE_NAME, e.getMessage());
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
            port = 0;
        }
    }
}
