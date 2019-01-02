package com.zlove.router;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;

import io.flutter.app.FlutterActivityEvents;
import io.flutter.app.FlutterApplication;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformPlugin;
import io.flutter.util.Preconditions;
import io.flutter.view.FlutterMain;
import io.flutter.view.FlutterRunArguments;
import io.flutter.view.FlutterView;

public final class FlutterRouteDelegate implements FlutterActivityEvents,
        FlutterView.Provider,
        PluginRegistry,
        RouterPlugin.IRouteCallback {

    private static final String TAG = FlutterRouteDelegate.class.getSimpleName();
    private static final WindowManager.LayoutParams matchParent = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

    // 当前栈中活着的FlutterRouteActivity的个数
    private static int sActivitiesCount;
    static FlutterView sFlutterView;
    private static boolean sIsFlutterViewCreated;

    private String mUniqueRouteName;
    private boolean mIsRoutePopped = false;

    private FrameLayout mRootView;
    // 用于滑动返回或转场动画时显示
    private ImageView mCoverView;
    private Bitmap mCoverBitmap;

    private final FlutterRouteActivity activity;
    private FlutterView flutterView;
    private View launchView;

    FlutterRouteDelegate(FlutterRouteActivity activity) {
        this.activity = Preconditions.checkNotNull(activity);
    }

    @Override
    public FlutterView getFlutterView() {
        return flutterView;
    }

    @Override
    public boolean hasPlugin(String key) {
        return flutterView.getPluginRegistry().hasPlugin(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T valuePublishedByPlugin(String pluginKey) {
        return (T) flutterView.getPluginRegistry().valuePublishedByPlugin(pluginKey);
    }

    @Override
    public Registrar registrarFor(String pluginKey) {
        return flutterView.getPluginRegistry().registrarFor(pluginKey);
    }

    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        return flutterView.getPluginRegistry().onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return flutterView.getPluginRegistry().onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ++sActivitiesCount;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = activity.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(0x40000000);
            window.getDecorView().setSystemUiVisibility(PlatformPlugin.DEFAULT_SYSTEM_UI);
        }

        String[] args = getArgsFromIntent(activity.getIntent());
        FlutterMain.ensureInitializationComplete(activity.getApplicationContext(), args);

        mRootView = new FrameLayout(activity);
        activity.setContentView(mRootView, matchParent);
        checkIfAddFlutterView();

        if (loadIntent(activity.getIntent())) {
            return;
        }
        if (!flutterView.getFlutterNativeView().isApplicationRunning()) {
            String appBundlePath = FlutterMain.findAppBundlePath(activity.getApplicationContext());
            if (appBundlePath != null) {
                FlutterRunArguments arguments = new FlutterRunArguments();
                arguments.bundlePath = appBundlePath;
                arguments.entrypoint = "main";
                flutterView.runFromBundle(arguments);
            }
        }
        tryNavigateToRoute();
    }

    // 首次启动Flutter页面时会在RouteChannelReady的回调中被调用，其他情况下在onCreate调用
    private void tryNavigateToRoute() {
        final String route = activity.getIntent().getStringExtra("route");
        if (!TextUtils.isEmpty(route)) {
            Object params = null;
            Bundle bundle = activity.getIntent().getExtras();
            if (bundle != null) {
                params = bundle.get("params");
            }
            RouterPlugin plugin = getFlutterView().getPluginRegistry().valuePublishedByPlugin(RouterPlugin.ROUTE_PLUGIN_NAME);
            if (plugin != null && plugin.isRouteChannelReady()) {
                plugin.navigateTo(route, params);
            }
        }
    }

    private void checkIfAddFlutterView() {
        if (sFlutterView == null) {
            sFlutterView = new FlutterView(activity);
            sFlutterView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    sIsFlutterViewCreated = true;
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    sIsFlutterViewCreated = false;
                }
            });
            flutterView = sFlutterView;
            mRootView.addView(flutterView, 0, matchParent);
            return;
        }
        flutterView = sFlutterView;
        if (flutterView.getParent() != mRootView) {
            if (flutterView.getParent() != null) {
                ((ViewGroup) flutterView.getParent()).removeView(flutterView);
            }
            flutterView.attachActivity(activity);
            mRootView.addView(flutterView, 0, matchParent);
        }
        if (mCoverView == null) {
            mCoverView = new ImageView(activity);
            mCoverView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            mRootView.addView(mCoverView);
            mCoverView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // Only attempt to reload the Flutter Dart code during development. Use
        // the debuggable flag as an indicator that we are in development mode.
        if (!isDebuggable() || !loadIntent(intent)) {
            flutterView.getPluginRegistry().onNewIntent(intent);
        }
    }

    private boolean isDebuggable() {
        return (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    @Override
    public void onPause() {
        if (flutterView.getFlutterNativeView() != null && flutterView.getFlutterNativeView().isAttached()) {
            mCoverBitmap = flutterView.getBitmap();
            if (mCoverView != null && mCoverBitmap != null) {
                mCoverView.setImageBitmap(mCoverBitmap);
                mCoverView.setVisibility(View.VISIBLE);
            }
        }

        tryPopFlutterRoute();

        Application app = (Application) activity.getApplicationContext();
        if (app instanceof FlutterApplication) {
            FlutterApplication flutterApp = (FlutterApplication) app;
            if (activity.equals(flutterApp.getCurrentActivity())) {
                flutterApp.setCurrentActivity(null);
            }
        }
        if (flutterView != null) {
            flutterView.onPause();
        }
    }

    private void tryPopFlutterRoute() {
        if (!mIsRoutePopped && activity.isFinishing() && !TextUtils.isEmpty(mUniqueRouteName) && getFlutterView().getFlutterNativeView() != null) {
            RouterPlugin plugin = getFlutterView().getPluginRegistry().valuePublishedByPlugin(RouterPlugin.ROUTE_PLUGIN_NAME);
            if (plugin != null) {
                plugin.popFlutter(mUniqueRouteName);
            }
            mIsRoutePopped = true;
        }
    }

    @Override
    public void onStart() {
        if (flutterView != null) {
            flutterView.onStart();
        }
    }

    @Override
    public void onResume() {
        Application app = (Application) activity.getApplicationContext();
        if (app instanceof FlutterApplication) {
            FlutterApplication flutterApp = (FlutterApplication) app;
            flutterApp.setCurrentActivity(activity);
        }
        checkIfAddFlutterView();

        // 有截图是没必要添加launchView
        if (mCoverView.getVisibility() != View.VISIBLE) {
            // 防止闪黑屏
            if (launchView == null) {
                launchView = createLaunchView();
            }

            if (launchView != null) {
                addLaunchView();
            }
        }
        addFirstFrameListener();
    }

    @Override
    public void onStop() {
        if (isViewActive()) {
            flutterView.onStop();
        }
    }

    @Override
    public void onPostResume() {
        if (flutterView != null) {
            flutterView.onPostResume();
        }
    }

    @Override
    public void onDestroy() {
        tryPopFlutterRoute();
        Application app = (Application) activity.getApplicationContext();
        if (app instanceof FlutterApplication) {
            FlutterApplication flutterApp = (FlutterApplication) app;
            if (activity.equals(flutterApp.getCurrentActivity())) {
                flutterApp.setCurrentActivity(null);
            }
        }

        if (flutterView != null && flutterView.getParent() == mRootView) {
            mRootView.removeView(flutterView);
            flutterView.detachActivity();
        }

        --sActivitiesCount;

        if (sActivitiesCount == 0) {
            RouterPlugin plugin = getFlutterView().getPluginRegistry().valuePublishedByPlugin(RouterPlugin.ROUTE_PLUGIN_NAME);
            if (plugin != null) {
                // 由于后面FlutterView会被销毁，所以重置RouteChannel的状态
                plugin.resetRouteChannel();
            }
            destroyFlutterView();
        }

        // 下面是flutter源码
        // if (flutterView != null) {
        //     final boolean detach =
        //             flutterView.getPluginRegistry().onViewDestroy(flutterView.getFlutterNativeView());
        //     if (detach || viewFactory.retainFlutterNativeView()) {
        //         // Detach, but do not destroy the FlutterView if a plugin
        //         // expressed interest in its FlutterNativeView.
        //         flutterView.detach();
        //     } else {
        //         flutterView.destroy();
        //     }
        // }
    }

    private static void destroyFlutterView() {
        if (sFlutterView != null) {
            sFlutterView.getPluginRegistry().onViewDestroy(sFlutterView.getFlutterNativeView());
            sFlutterView.destroy();
            sFlutterView = null;
        }
    }

    @Override
    public boolean onBackPressed() {
        if (flutterView != null) {
            flutterView.popRoute();
            return true;
        }
        return false;
    }

    @Override
    public void onUserLeaveHint() {
        flutterView.getPluginRegistry().onUserLeaveHint();
    }

    @Override
    public void onTrimMemory(int level) {
        // Use a trim level delivered while the application is running so the
        // framework has a chance to react to the notification.
        if (level == TRIM_MEMORY_RUNNING_LOW) {
            flutterView.onMemoryPressure();
        }
    }

    @Override
    public void onLowMemory() {
        flutterView.onMemoryPressure();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    private boolean isViewActive() {
        return mRootView == getFlutterView().getParent();
    }

    private static String[] getArgsFromIntent(Intent intent) {
        // Before adding more entries to this list, consider that arbitrary
        // Android applications can generate intents with extra data and that
        // there are many security-sensitive args in the binary.
        ArrayList<String> args = new ArrayList<String>();
        if (intent.getBooleanExtra("trace-startup", false)) {
            args.add("--trace-startup");
        }
        if (intent.getBooleanExtra("start-paused", false)) {
            args.add("--start-paused");
        }
        if (intent.getBooleanExtra("use-test-fonts", false)) {
            args.add("--use-test-fonts");
        }
        if (intent.getBooleanExtra("enable-dart-profiling", false)) {
            args.add("--enable-dart-profiling");
        }
        if (intent.getBooleanExtra("enable-software-rendering", false)) {
            args.add("--enable-software-rendering");
        }
        if (intent.getBooleanExtra("skia-deterministic-rendering", false)) {
            args.add("--skia-deterministic-rendering");
        }
        if (intent.getBooleanExtra("trace-skia", false)) {
            args.add("--trace-skia");
        }
        if (intent.getBooleanExtra("verbose-logging", false)) {
            args.add("--verbose-logging");
        }
        if (!args.isEmpty()) {
            String[] argsArray = new String[args.size()];
            return args.toArray(argsArray);
        }
        return null;
    }

    private boolean loadIntent(Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_RUN.equals(action)) {
            String route = intent.getStringExtra("route");
            String appBundlePath = intent.getDataString();
            if (appBundlePath == null) {
                // Fall back to the installation path if no bundle path
                // was specified.
                appBundlePath = FlutterMain.findAppBundlePath(activity.getApplicationContext());
            }
            if (route != null) {
                flutterView.setInitialRoute(route);
            }
            if (!flutterView.getFlutterNativeView().isApplicationRunning()) {
                FlutterRunArguments args = new FlutterRunArguments();
                args.bundlePath = appBundlePath;
                args.entrypoint = "main";
                flutterView.runFromBundle(args);
            }
            return true;
        }

        return false;
    }

    /**
     * Creates a {@link View} containing the same {@link Drawable} as the one set as the
     * {@code windowBackground} of the parent activity for use as a launch splash io.flutter.view.
     *
     * Returns null if no {@code windowBackground} is set for the activity.
     */
    private View createLaunchView() {
        final Drawable launchScreenDrawable = activity.getTransitionScreenDrawable();
        if (launchScreenDrawable == null) {
            return null;
        }
        final View view = new View(activity);
        view.setLayoutParams(matchParent);
        view.setBackground(launchScreenDrawable);
        return view;
    }

    /**
     * Extracts a {@link Drawable} from the parent activity's {@code windowBackground}.
     *
     * {@code android:windowBackground} is specifically reused instead of a other attributes
     * because the Android framework can display it fast enough when launching the app as opposed
     * to anything defined in the Activity subclass.
     *
     * Returns null if no {@code windowBackground} is set for the activity.
     */
    @SuppressWarnings("deprecation")
    Drawable getLaunchScreenDrawableFromActivityTheme() {
        TypedValue typedValue = new TypedValue();
        if (!activity.getTheme().resolveAttribute(
                android.R.attr.windowBackground,
                typedValue,
                true)) {;
            return null;
        }
        if (typedValue.resourceId == 0) {
            return null;
        }
        try {
            return activity.getResources().getDrawable(typedValue.resourceId);
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Referenced launch screen windowBackground resource does not exist");
            return null;
        }
    }

    /**
     * Show and then automatically animate out the launch io.flutter.view.
     *
     * If a launch screen is defined in the user application's AndroidManifest.xml as the
     * activity's {@code windowBackground}, display it on top of the {@link FlutterView} and
     * remove the activity's {@code windowBackground}.
     *
     * Fade it out and remove it when the {@link FlutterView} renders its first frame.
     */
    private void addLaunchView() {
        if (launchView == null || launchView.getParent() != null) {
            return;
        }

        mRootView.addView(launchView, mRootView.indexOfChild(flutterView) + 1, matchParent);
        launchView.setAlpha(1);

        // Resets the activity theme from the one containing the launch screen in the window
        // background to a blank one since the launch screen is now in a io.flutter.view in front of the
        // FlutterView.
        //
        // We can make this configurable if users want it.

        // activity.setTheme(android.R.style.Theme_Black_NoTitleBar);
    }

    private void addFirstFrameListener() {
        if (sIsFlutterViewCreated) {
            onFrameShownInternal();
        } else {
            flutterView.addFirstFrameListener(new FlutterView.FirstFrameListener() {
                @Override
                public void onFirstFrame() {
                    onFrameShownInternal();
                    flutterView.removeFirstFrameListener(this);
                }
            });
        }
    }

    private void onFrameShownInternal() {
        if (launchView != null && launchView.getParent() != null) {
            launchView.animate().alpha(0f).setDuration(300).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Views added to an Activity's addContentView is always added to its
                    // root FrameLayout.
                    // 偶现空指针，未知原因动画快速进行了两次？
                    if (launchView.getParent() != null) {
                        ((ViewGroup) launchView.getParent()).removeView(launchView);
                    }
                    invokeOnFrameShown();
                }
            });
        } else {
            invokeOnFrameShown();
        }
    }

    private void invokeOnFrameShown() {
        if (mCoverView != null) {
            mCoverView.setImageBitmap(null);
            mCoverView.setVisibility(View.INVISIBLE);
            mCoverBitmap = null;
        }
        activity.onFrameShown();
    }

    @Override
    public void onRouteChannelReady() {
        tryNavigateToRoute();
    }

    @Override
    public void updateCurRouteName(String routeName) {
        mUniqueRouteName = routeName;
    }
}
