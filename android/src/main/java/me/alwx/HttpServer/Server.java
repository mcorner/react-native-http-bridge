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

import java.util.Collections;
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

class PendingResponse {
  public Response response;
}

public class Server extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private static final String SERVER_EVENT_ID = "httpServerResponseReceived";

    private ReactContext reactContext;
    private Map<String, PendingResponse> responses;

    public Server(ReactContext context, int port) {
        super(port);
        reactContext = context;
        responses = Collections.synchronizedMap(new HashMap<String, PendingResponse>());

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

        PendingResponse pending = new PendingResponse();
        responses.put(requestId, pending);

        this.sendEvent(reactContext, SERVER_EVENT_ID, request);

        synchronized(pending) {
          try{
            while (pending.response == null){
              pending.wait();
            }
            return pending.response;
          } catch(InterruptedException e){
            Log.e(TAG, "Waiting for response interrupted");
          }
          responses.remove(requestId);
        }

        return null;
    }

    public void respond(String requestId, Response resp, ReadableMap opts) {
      if (opts != null && opts.hasKey("headers")){
        ReadableMap headers = opts.getMap("headers");
        ReadableMapKeySetIterator iterator = headers.keySetIterator();

        while (iterator.hasNextKey()) {
          String key = iterator.nextKey();
          resp.addHeader(key, headers.getString(key));
        }
      }

      PendingResponse pending = responses.get(requestId);
      if (pending != null){
        synchronized(pending) {
          pending.response = resp;
          pending.notify();
        }
      }
    }

    public void respondFixed(String requestId, int code, String type, String body, ReadableMap opts){
      Response resp = newFixedLengthResponse(Status.lookup(code), type, body);
      respond(requestId, resp, opts);
    }

    public void respondFile(ReactApplicationContext reactContext, String requestId, int code, String filePath, ReadableMap opts) {
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
          getMimeTypeForFile(filePath),
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
