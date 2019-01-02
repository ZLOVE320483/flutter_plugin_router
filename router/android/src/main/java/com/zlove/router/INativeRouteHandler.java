package com.zlove.router;

import android.content.Context;

public interface INativeRouteHandler {
    void handleNativeRoute(Context context, String openUrl, Object extraArgs);
}
