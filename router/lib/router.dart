import 'dart:async';

import 'package:flutter/services.dart';

class Router {
  static const MethodChannel _channel =
      const MethodChannel('router');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
