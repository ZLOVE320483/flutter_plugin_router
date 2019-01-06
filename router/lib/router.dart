import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';

import 'package:flutter/services.dart';

class Router {
  static const MethodChannel _channel = const MethodChannel('router');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static final GlobalKey<NavigatorState> _globalKeyForRouter = GlobalKey();
  static final GlobalKey<RouteAppState> _globalKeyForRouteApp = GlobalKey();
  static final List<Route<dynamic>> _history = [];

  static void _init() {
    _registerMethodHandler();
    _onRouteChannelReady();
  }

  static void _registerMethodHandler() {
    _channel.setMethodCallHandler((MethodCall methodCall) {
      switch (methodCall.method) {
        case "navigateTo":
          {
            Map args = methodCall.arguments;
            if (args != null) {
              Router._pushPageWithUrl(args["url"], params: args["params"]);
            }
          }
          break;
        case "popFlutter":
          {
            Map args = methodCall.arguments;
            if (args != null) {
              String uniquePageName = args["route"];
              int indexBegin = _history.indexWhere((route) {
                if (route is OrangePageRoute &&
                    route.uniquePageName == uniquePageName) {
                  return true;
                }
                return false;
              });
              int indexEnd = _history.indexWhere((route) {
                if (route is OrangePageRoute) {
                  return true;
                }
                return false;
              }, indexBegin + 1);
              if (indexBegin > -1 && indexEnd > -1) {
                List<Route<dynamic>> history = [];
                for (int i = indexBegin; i < indexEnd; ++i) {
                  history.add(_history[i]);
                }
                _history.removeRange(indexBegin, indexEnd);
                for (int i = 0; i < indexEnd - indexBegin; ++i) {
                  _globalKeyForRouter.currentState.removeRoute(history[i]);
                }
              } else {
                OrangePageRoute route = _history[indexBegin];
                _history.removeAt(indexBegin);
                _globalKeyForRouter.currentState.removeRoute(route);
              }
            }
          }
          break;
      }
    });
  }

  static jumpToNativeActivity(String openUrl, {dynamic extras}) {
    if (openUrl.startsWith("/")) {
      openUrl = "orangelocal://flutter?url=" + openUrl;
    }
    _channel.invokeMethod(
        "openNativeRoute", {"openUrl": openUrl, "extras": extras});
  }

  /// 这个方法处理返回键逻辑，需要从flutter页面直接退出时调用
  /// 由于iOS不相应SystemNavigator.pop()所以自己实现了popFlutterView
  static notifyPopFlutterView() {
    if (Platform.isIOS) {
      _channel.invokeMethod("popFlutterView");
    } else {
      SystemNavigator.pop();
    }
  }

  static _notifyDestroyFlutterView() {
    _channel.invokeMethod("destroyFlutterView");
  }

  static _onRouteChannelReady() async {
    _channel.invokeMethod("onRouteChannelReady");
  }

  static _pushPageWithUrl(String url, {dynamic params}) {
    NavigatorState navState = _globalKeyForRouter.currentState;
    String uniquePageName = Utils.generateUniquePageName(url);
    OrangePageRoute route = OrangePageRoute(
        _globalKeyForRouteApp.currentState.widget._routeMap[url],
        uniquePageName,
        OrangeRouteSettings(params: params, name: url));
    navState.push(route);
    _channel.invokeMethod("updateCurFlutterRoute", uniquePageName);
  }
}

class RouteApp extends StatefulWidget {

  /// {@macro flutter.widgets.widgetsApp.navigatorObservers}
  ///
  /// The [Navigator] is only built if routes are provided (either via [home],
  /// [routes], [onGenerateRoute], or [onUnknownRoute]); if they are not,
  /// [navigatorObservers] must be the empty list and [builder] must not be null.
  final List<NavigatorObserver> navigatorObservers;

  /// A one-line description used by the device to identify the app for the user.
  ///
  /// On Android the titles appear above the task manager's app snapshots which are
  /// displayed when the user presses the "recent apps" button. Similarly, on
  /// iOS the titles appear in the App Switcher when the user double presses the
  /// home button.
  final String title;

  /// The widget for the default route of the app ([Navigator.defaultRouteName],
  /// which is `/`).
  ///
  /// This is the route that is displayed first when the application is started
  /// normally, unless [initialRoute] is specified. It's also the route that's
  /// displayed if the [initialRoute] can't be displayed.
  ///
  /// 打底的Widget，默认为纯白屏，一般不会被显示，建议设置为贴近App主题的纯色
  final Widget home;

  /// The colors to use for the application's widgets.
  final ThemeData theme;

  /// Turns on a performance overlay.
  ///
  /// See also:
  ///
  ///  * <https://flutter.io/debugging/#performanceoverlay>
  final bool showPerformanceOverlay;

  /// Turns on checkerboarding of raster cache images.
  final bool checkerboardRasterCacheImages;

  /// Turns on checkerboarding of layers rendered to offscreen bitmaps.
  final bool checkerboardOffscreenLayers;

  /// Turns on an overlay that shows the accessibility information
  /// reported by the framework.
  final bool showSemanticsDebugger;

  /// {@macro flutter.widgets.widgetsApp.debugShowCheckedModeBanner}
  final bool debugShowCheckedModeBanner;

  /// Turns on a [GridPaper] overlay that paints a baseline grid
  /// Material apps.
  ///
  /// Only available in checked mode.
  ///
  /// See also:
  ///
  ///  * <https://material.google.com/layout/metrics-keylines.html>
  final bool debugShowMaterialGrid;

  final Map<String, OrangePageBuilder> _routeMap;

  RouteApp(
      this._routeMap, {
        this.navigatorObservers = const <NavigatorObserver>[],
        this.title,
        this.home,
        this.theme,
        this.debugShowMaterialGrid = false,
        this.showPerformanceOverlay = false,
        this.checkerboardRasterCacheImages = false,
        this.checkerboardOffscreenLayers = false,
        this.showSemanticsDebugger = false,
        this.debugShowCheckedModeBanner = true,
      }) : super(key: Router._globalKeyForRouteApp);

  @override
  State<StatefulWidget> createState() {
    return RouteAppState();
  }
}

class RouteAppState extends State<RouteApp> {
  List<NavigatorObserver> _navigatorObservers = [OrangeNavigatorObserver()];

  @override
  void initState() {
    super.initState();
    _navigatorObservers.addAll(widget.navigatorObservers);
    Router._init();
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: widget.title ?? '',
      navigatorKey: Router._globalKeyForRouter,
      navigatorObservers: _navigatorObservers,
      home: widget.home ?? new MyHomeWidget(),
      theme: widget.theme,
      showPerformanceOverlay: widget.showPerformanceOverlay,
      checkerboardRasterCacheImages: widget.checkerboardRasterCacheImages,
      checkerboardOffscreenLayers: widget.checkerboardOffscreenLayers,
      showSemanticsDebugger: widget.showSemanticsDebugger,
      debugShowCheckedModeBanner: widget.debugShowCheckedModeBanner,
      debugShowMaterialGrid: widget.debugShowMaterialGrid,
    );
  }
}

class MyHomeWidget extends StatefulWidget {

  MyHomeWidget({Key key}) : super(key: key);

  @override
  State<StatefulWidget> createState() {
    return MyHomeWidgetState();
  }
}

class MyHomeWidgetState extends State<MyHomeWidget> {

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.white,
    );
  }
}

class OrangeNavigatorObserver extends NavigatorObserver {

  OrangeNavigatorObserver();

  @override
  void didPush(Route route, Route previousRoute) {
    Router._history.add(route);
  }

  @override
  void didReplace({Route newRoute, Route oldRoute}) {
    int index = Router._history.indexOf(oldRoute);
    if (index >= 0) {
      Router._history.removeAt(index);
      Router._history.insert(index, newRoute);
    }
  }

  @override
  void didPop(Route route, Route previousRoute) {
    if (route is OrangePageRoute) {
      Router.notifyPopFlutterView();
    }
    Router._history.remove(route);
    // 这里是最后一个栈了，可以选择销毁native的FlutterView
    if (Router._history.length == 1) {
      Router._notifyDestroyFlutterView();
    }
  }

  @override
  void didRemove(Route route, Route previousRoute) {
    // 这里是最后一个栈了，可以选择销毁native的FlutterView
    if (Router._history.length == 1) {
      Router._notifyDestroyFlutterView();
    }
  }

}

class OrangeRouteSettings extends RouteSettings {
  const OrangeRouteSettings({
    this.params,
    String name,
  }) : super(name: name);

  final dynamic params;
}

class OrangePageRoute<T> extends PageRoute<T> {
  final String uniquePageName;
  final OrangePageBuilder pageBuilder;
  final OrangeRouteSettings routeSettings;
  final RouteTransitionsBuilder transitionsBuilder;

  @override
  final Duration transitionDuration;

  @override
  final bool opaque;

  @override
  final bool barrierDismissible;

  @override
  final Color barrierColor;

  @override
  final String barrierLabel;

  @override
  final bool maintainState;

  OrangePageRoute(
      this.pageBuilder,
      this.uniquePageName,
      this.routeSettings, {
        this.transitionsBuilder = _defaultTransitionsBuilder,
        this.transitionDuration = const Duration(milliseconds: 0),
        this.opaque = false,
        this.barrierDismissible = false,
        this.barrierColor,
        this.barrierLabel,
        this.maintainState = true,
      })  : assert(pageBuilder != null),
        assert(transitionsBuilder != null),
        assert(barrierDismissible != null),
        assert(maintainState != null),
        assert(opaque != null),
        super(settings: routeSettings);

  static Widget _defaultTransitionsBuilder(
      BuildContext context,
      Animation<double> animation,
      Animation<double> secondaryAnimation,
      Widget child) {
    return child;
  }

  @override
  Widget buildPage(BuildContext context, Animation<double> animation, Animation<double> secondaryAnimation) {
    return MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaleFactor: 1.0,
        ),
        child: pageBuilder(context, routeSettings?.params));
  }

  @override
  Widget buildTransitions(BuildContext context, Animation<double> animation, Animation<double> secondaryAnimation, Widget child) {
    return transitionsBuilder(context, animation, secondaryAnimation, child);
  }
}

typedef Widget OrangePageBuilder(BuildContext context, dynamic params);

class Utils extends Object {
  static int baseId = 0;
  static const String pageNameSeperatorToken = "_";

  static int generatePrimaryPageId() {
    return baseId++;
  }

  static String generateUniquePageName(String pageName) {
    return (pageName ?? "") +
        pageNameSeperatorToken +
        generatePrimaryPageId().toString();
  }
}