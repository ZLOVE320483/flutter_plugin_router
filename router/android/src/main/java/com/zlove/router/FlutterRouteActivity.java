package com.zlove.router;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import io.flutter.app.FlutterActivityEvents;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.FlutterView;

public class FlutterRouteActivity extends Activity implements FlutterView.Provider, PluginRegistry, RouterPlugin.IRouteCallback {
    private final FlutterRouteDelegate delegate = new FlutterRouteDelegate(this);
    private final FlutterActivityEvents eventDelegate = delegate;
    private final FlutterView.Provider viewProvider = delegate;
    private final PluginRegistry pluginRegistry = delegate;
    private final RouterPlugin.IRouteCallback routeCallback = delegate;

    protected void onFrameShown() {

    }

    protected Drawable getTransitionScreenDrawable() {
        return delegate.getLaunchScreenDrawableFromActivityTheme();
    }

    /**
     * Returns the Flutter io.flutter.view used by this activity; will be null before
     * {@link #onCreate(Bundle)} is called.
     */
    @Override
    public FlutterView getFlutterView() {
        return viewProvider.getFlutterView();
    }

    @Override
    public final boolean hasPlugin(String key) {
        return pluginRegistry.hasPlugin(key);
    }

    @Override
    public final <T> T valuePublishedByPlugin(String pluginKey) {
        return pluginRegistry.valuePublishedByPlugin(pluginKey);
    }

    @Override
    public final Registrar registrarFor(String pluginKey) {
        return pluginRegistry.registrarFor(pluginKey);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        eventDelegate.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        eventDelegate.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        eventDelegate.onResume();
    }

    @Override
    protected void onDestroy() {
        eventDelegate.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!eventDelegate.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        eventDelegate.onStop();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        eventDelegate.onPause();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        eventDelegate.onPostResume();
    }

    // @Override - added in API level 23
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        eventDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!eventDelegate.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        eventDelegate.onNewIntent(intent);
    }

    @Override
    public void onUserLeaveHint() {
        eventDelegate.onUserLeaveHint();
    }

    @Override
    public void onTrimMemory(int level) {
        eventDelegate.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        eventDelegate.onLowMemory();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        eventDelegate.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRouteChannelReady() {
        routeCallback.onRouteChannelReady();
    }

    @Override
    public void updateCurRouteName(String routeName) {
        delegate.updateCurRouteName(routeName);
    }
}
