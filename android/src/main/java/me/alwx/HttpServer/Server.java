package me.alwx.HttpServer;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReactApplicationContext;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Random;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.InputStream;

import android.content.res.AssetFileDescriptor;
import android.support.annotation.Nullable;
import android.util.Log;

public class Server extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private static final String SERVER_EVENT_ID = "httpServerResponseReceived";

    private ReactContext reactContext;
    private Map<String, Response> responses;

    public Server(ReactContext context, int port) {
        super(port);
        reactContext = context;
        responses = new HashMap<>();

        Log.d(TAG, "Server started");
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d(TAG, "Request received!");

        Random rand = new Random();
        String requestId = String.format("%d:%d", System.currentTimeMillis(), rand.nextInt(1000000));

        WritableMap request;
        try {
            request = fillRequestMap(session, requestId);
        } catch (Exception e) {
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage()
            );
        }

        this.sendEvent(reactContext, SERVER_EVENT_ID, request);

        while (responses.get(requestId) == null) {
            try {
                Log.d(TAG, "sleep");
                Thread.sleep(10);
            } catch (Exception e) {
                Log.d(TAG, "Exception while waiting: " + e);
            }
        }
        Response response = responses.get(requestId);
        responses.remove(requestId);

        return response;
    }

    public void respond(String requestId, Response resp, ReadableMap opts) {
      if (opts != null && opts.hasKey("headers")){
        Log.d(TAG, "has headers");

        ReadableMap headers = opts.getMap("headers");
        ReadableMapKeySetIterator iterator = headers.keySetIterator();

        while (iterator.hasNextKey()) {
          String key = iterator.nextKey();
          Log.d(TAG, "has header: " + key);

          resp.addHeader(key, headers.getString(key));
        }
      }
      responses.put(requestId, resp);
    }

    public void respondFixed(String requestId, int code, String type, String body, ReadableMap opts){
      Log.d(TAG, "respondFixed");
      Response resp = newFixedLengthResponse(Status.lookup(code), type, body);
      respond(requestId, resp, opts);
    }

    public void respondFile(ReactApplicationContext reactContext, String requestId, int code, String type, String filePath, ReadableMap opts) {
      Log.d(TAG, "respondFile");
      Response resp;
      try {
        InputStream reader;
        Long size;

        Log.d(TAG, filePath);

        if (opts.hasKey("assets") && opts.getBoolean("assets")){
          Log.d(TAG, "asset");

          AssetFileDescriptor fd = reactContext.getAssets().openFd(filePath);
          reader = fd.createInputStream();
          size = fd.getLength();
        } else{
          Log.d(TAG, "not asset");

          final Path path = Paths.get(filePath);
          reader = Files.newInputStream(path);
          size = Files.size(path);
        }
        resp = newFixedLengthResponse(
          Status.lookup(code),
          type,
          reader,
          size
        );
        respond(requestId, resp, opts);
      } catch (final Exception ex) {
        // Shouldn't happen, make sure you pass valid paths
        ex.printStackTrace();
        resp = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "ERROR");
        respond(requestId, resp, null);
      }
    }

    private WritableMap fillRequestMap(IHTTPSession session, String requestId) throws Exception {
        Method method = session.getMethod();
        WritableMap request = Arguments.createMap();
        request.putString("url", session.getUri());
        request.putString("type", method.name());
        request.putString("requestId", requestId);

        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        if (files.size() > 0) {
          request.putString("postData", files.get("postData"));
        }

        return request;
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }
}
