# react-native-http-bridge

Simple HTTP server for [React Native](https://github.com/facebook/react-native).
Created for [Status.im](https://github.com/status-im/status-react).

Since 0.5.0 supports and handles GET, POST, PUT and DELETE requests.
The library can be useful for handling requests with `application/json` content type
(and this is the only content type we support at the current stage) and returning different responses.

Since 0.6.0 can handle millions of requests at the same time and also includes some very basic support for [React Native QT](https://github.com/status-im/react-native-desktop).

NOTES for this fork:

Forked the original to add some file functionality

You can now do a few things demonstrated here:

```
HttpBridge.start(1234, 'cdn', {assetMode:'serve', headers:{"Cache-Control": 'public, max-age=3600'}}, async (request) => {
  HttpBridge.respond(request.requestId, 307, "text/html", "", {
    headers: {
      Location: Config.assetsUrl+request.url
    }
  });
});
```

The assetMode "serve" allows the embedded server to send back files from the MainBundle on ios or assets on Android.  If it doesn't find the file, the callback gets fired.  The headers for the server will be applied when serving assets like this (but not elsewhere).

Assets served directly must be in these places:
* android/app/src/main/assets/
* ios/assets


The callback can also set headers and send back codes that forward etc.

You can also choose to serve files through the callback.  If you set assets: true, it will look in assets (Android only).  Requires RNFS to look for files etc.

```
let inBundle, path;
if (Platform.OS === 'android'){
  path = request.url.replace(/^\/+/g, '')
  inBundle = await RNFS.existsAssets(path)
} else {
  path = `${RNFS.MainBundlePath}/assets/${request.url}`;
  inBundle = await RNFS.exists(path)
}

if (inBundle){
  HttpBridge.respondFile(request.requestId, 200, path, {
    assets: true,
    headers: {
      'Cache-Control': 'public, max-age=3600'
    }
  });
}
```

## Install

```shell
npm install --save react-native-http-bridge
```

## Automatically link

#### With React Native 0.27+

```shell
react-native link react-native-http-bridge
```

## Example

First import/require react-native-http-server:

```js

    var httpBridge = require('react-native-http-bridge');

```


Initalize the server in the `componentWillMount` lifecycle method. You need to provide a `port` and a callback.

```js

    componentWillMount() {
      // initalize the server (now accessible via localhost:1234)
      httpBridge.start(5561, function(request) {

          // you can use request.url, request.type and request.postData here
          if (request.type === "GET" && request.url.split("/")[1] === "users") {
            httpBridge.respond(200, "application/json", "{\"message\": \"OK\"}");
          } else {
            httpBridge.respond(400, "application/json", "{\"message\": \"Bad Request\"}");
          }

      });
    }

```

Finally, ensure that you disable the server when your component is being unmounted.

```js

  componentWillUnmount() {
    httpBridge.stop();
  }

```
