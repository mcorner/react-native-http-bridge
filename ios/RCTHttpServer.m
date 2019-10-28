#import "RCTHttpServer.h"
#import "React/RCTBridge.h"
#import "React/RCTLog.h"
#import "React/RCTEventDispatcher.h"

#import "WGCDWebServer.h"
#import "WGCDWebServerDataResponse.h"
#import "WGCDWebServerFileResponse.h"
#import "WGCDWebServerDataRequest.h"
#import "WGCDWebServerPrivate.h"
#include <stdlib.h>

@interface RCTHttpServer : NSObject <RCTBridgeModule> {
    WGCDWebServer* _webServer;
    NSMutableDictionary* _requestResponses;
}
@end

static RCTBridge *bridge;

@implementation RCTHttpServer

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE();


- (void)initResponseReceivedFor:(WGCDWebServer *)server forType:(NSString*)type {
    [server addDefaultHandlerForMethod:type requestClass:[WGCDWebServerDataRequest class] processBlock:^WGCDWebServerResponse *(WGCDWebServerRequest* request) {

        long long milliseconds = (long long)([[NSDate date] timeIntervalSince1970] * 1000.0);
        int r = arc4random_uniform(1000000);
        NSString *requestId = [NSString stringWithFormat:@"%lld:%d", milliseconds, r];

        // it's a weird way of doing it, fix it
        @try {
            if ([WGCDWebServerTruncateHeaderValue(request.contentType) isEqualToString:@"application/json"]) {
                WGCDWebServerDataRequest* dataRequest = (WGCDWebServerDataRequest*)request;
                [self.bridge.eventDispatcher sendAppEventWithName:@"httpServerResponseReceived"
                                                             body:@{@"requestId": requestId,
                                                                    @"postData": dataRequest.jsonObject,
                                                                    @"type": type,
                                                                    @"url": request.URL.relativeString}];
            } else {
                [self.bridge.eventDispatcher sendAppEventWithName:@"httpServerResponseReceived"
                                                             body:@{@"requestId": requestId,
                                                                    @"type": type,
                                                                    @"url": request.URL.relativeString}];
            }
        } @catch (NSException *exception) {
            [self.bridge.eventDispatcher sendAppEventWithName:@"httpServerResponseReceived"
                                                         body:@{@"requestId": requestId,
                                                                @"type": type,
                                                                @"url": request.URL.relativeString}];
        }

        while ([_requestResponses objectForKey:requestId] == NULL) {
            [NSThread sleepForTimeInterval:0.01f];
        }

        WGCDWebServerResponse* response = [_requestResponses objectForKey:requestId];
        [_requestResponses removeObjectForKey:requestId];
        return response;
    }];
}

RCT_EXPORT_METHOD(start:(NSInteger) port
                  serviceName:(NSString *) serviceName)
{
    RCTLogInfo(@"Running HTTP bridge server: %ld", port);
    _requestResponses = [[NSMutableDictionary alloc] init];

    dispatch_sync(dispatch_get_main_queue(), ^{
        _webServer = [[WGCDWebServer alloc] init];

        [self initResponseReceivedFor:_webServer forType:@"POST"];
        [self initResponseReceivedFor:_webServer forType:@"PUT"];
        [self initResponseReceivedFor:_webServer forType:@"GET"];
        [self initResponseReceivedFor:_webServer forType:@"DELETE"];

        [_webServer startWithPort:port bonjourName:serviceName];
    });
}

RCT_EXPORT_METHOD(stop)
{
    RCTLogInfo(@"Stopping HTTP bridge server");

    if (_webServer != nil) {
        [_webServer stop];
        [_webServer removeAllHandlers];
        _webServer = nil;
    }
}

- (void)respondInternalWithRequestId:(NSString *) requestId response:(WGCDWebServerResponse*)response opts:(NSDictionary*) opts {

  NSDictionary *headers = [opts objectForKey:@"headers"];
  if (headers){
    for (id key in headers){
      [response setValue:[headers objectForKey:key] forAdditionalHeader:key];
    }
  }

  [_requestResponses setObject:response forKey:requestId];
}

RCT_EXPORT_METHOD(respond: (NSString *) requestId
                  code: (NSInteger) code
                  type: (NSString *) type
                  body: (NSString *) body
                  opts: (NSDictionary *) opts)
{
    NSData* data = [body dataUsingEncoding:NSUTF8StringEncoding];
    WGCDWebServerResponse* requestResponse = [[WGCDWebServerDataResponse alloc] initWithData:data contentType:type];
    requestResponse.statusCode = code;

    [self respondInternalWithRequestId:requestId response:requestResponse opts:opts];
}

RCT_EXPORT_METHOD(respondFile: (NSString *) requestId
                  code: (NSInteger) code
                  type: (NSString *) type
                  filePath: (NSString *) filePath
                  opts: (NSDictionary *) opts)
{
  WGCDWebServerResponse* requestResponse = [WGCDWebServerFileResponse responseWithFile:filePath];

  if (requestResponse){
    requestResponse.statusCode = code;
  } else{
    // didn't find file
    NSData* data = [@"ERROR" dataUsingEncoding:NSUTF8StringEncoding];
    requestResponse = [[WGCDWebServerDataResponse alloc] initWithData:data contentType:@"text/plain"];
  }
  [self respondInternalWithRequestId:requestId response:requestResponse opts:opts];
}

@end
