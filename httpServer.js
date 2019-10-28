/**
 * @providesModule react-native-http-server
 */
'use strict';

import {DeviceEventEmitter} from 'react-native';
import {NativeModules} from 'react-native';
var Server = NativeModules.HttpServer;

module.exports = {
    start: function (port, serviceName, callback) {
        if (port == 80) {
            throw "Invalid server port specified. Port 80 is reserved.";
        }

        Server.start(port, serviceName);
        DeviceEventEmitter.addListener('httpServerResponseReceived', callback);
    },

    stop: function () {
        Server.stop();
        DeviceEventEmitter.removeListener('httpServerResponseReceived');
    },

    respond: function (requestId, code, type, body, opts={}) {
        // opts.headers hash
        Server.respond(requestId, code, type, body, opts);
    },

    respondFile: function (requestId, code, filePath, opts={}) {
        // filePath is relative to the documents path
        // opt.headers hash
        Server.respondFile(requestId, code, filePath, opts);
    }

}
