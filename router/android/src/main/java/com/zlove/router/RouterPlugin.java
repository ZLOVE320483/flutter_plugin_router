package com.zlove.router;

import android.app.Activity;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * RouterPlugin
 */
public class RouterPlugin implements MethodCallHandler {

    public interface IRouteCallback {
        void onRouteChannelReady();
        void updateCurRouteName(String routeName);
    }

    private static INativeRouteHandler sNativeRouteHandler;

    public static final String ROUTE_PLUGIN_NAME = "com.zlove.router.RouterPlugin";
    public static final String METHOD_CHANNEL_NAME = "router";
    private boolean mIsRouteChannelReady;
    private Registrar mRegistrar;
    private MethodChannel mMethodChannel;

    private RouterPlugin(Registrar registrar, MethodChannel channel) {
        mRegistrar = registrar;
        mMethodChannel = channel;
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), METHOD_CHANNEL_NAME);
        RouterPlugin plugin = new RouterPlugin(registrar, channel);
        channel.setMethodCallHandler(plugin);
        registrar.publish(plugin);
    }

    public static void init(INativeRouteHandler routeHandler) {
        sNativeRouteHandler = routeHandler;
    }

    boolean isRouteChannelReady() {
        return mIsRouteChannelReady;
    }

    void resetRouteChannel() {
        mIsRouteChannelReady = false;
    }

    void navigateTo(String url, Object params) {
        Map<String, Object> args = new HashMap<>();
        args.put("url", url);
        args.put("params", params);
        mMethodChannel.invokeMethod("navigateTo", args);
    }

    void popFlutter(String uniqueRouteName) {
        Map<String, Object> args = new HashMap<>();
        args.put("route", uniqueRouteName);
        mMethodChannel.invokeMethod("popFlutter", args);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        Activity activity = mRegistrar.activity();
        if (TextUtils.equals(call.method, "getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else if (TextUtils.equals(call.method,"destroyFlutterView")) {
            // 与FlutterView的生命周期与Activity生命周期绑定了，所以这里暂时什么也不做
            result.success(null);
        } else if (TextUtils.equals(call.method, "onRouteChannelReady")) {
            mIsRouteChannelReady = true;
            if (activity instanceof IRouteCallback) {
                ((IRouteCallback) activity).onRouteChannelReady();
            }
            result.success(null);
        } else if (TextUtils.equals(call.method,"updateCurFlutterRoute")) {
            if (activity instanceof IRouteCallback) {
                ((IRouteCallback) activity).updateCurRouteName((String) call.arguments);
            }
            result.success(null);
        } else if (TextUtils.equals(call.method, "openNativeRoute")) {
            if (sNativeRouteHandler != null && activity != null) {
                String openUrl = call.argument("openUrl");
                Object extraArgs = call.argument("extras");
                sNativeRouteHandler.handleNativeRoute(activity, openUrl, extraArgs);
                result.success(null);
            } else {
                result.error("1", "没有注册NativeRouteHandler", null);
            }
        } else {
            result.notImplemented();
        }
    }
}
