/*
 * Copyright (c) 2021, the hapjs-platform Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hapjs.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatDelegate;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import org.hapjs.bridge.annotation.WidgetAnnotation;
import org.hapjs.common.utils.DisplayUtil;
import org.hapjs.common.utils.FloatUtil;
import org.hapjs.component.Component;
import org.hapjs.component.Container;
import org.hapjs.component.animation.Transform;
import org.hapjs.component.bridge.RenderEventCallback;
import org.hapjs.component.constants.Attributes;
import org.hapjs.component.constants.Corner;
import org.hapjs.render.Autoplay;
import org.hapjs.runtime.ConfigurationManager;
import org.hapjs.runtime.DarkThemeUtil;
import org.hapjs.runtime.HapConfiguration;
import org.hapjs.runtime.HapEngine;
import org.hapjs.runtime.ProviderManager;
import org.hapjs.system.SysOpProvider;
import org.hapjs.widgets.view.image.FlexImageView;
import org.json.JSONObject;

@WidgetAnnotation(
        name = Image.WIDGET_NAME,
        methods = {
                Component.METHOD_ANIMATE,
                Component.METHOD_GET_BOUNDING_CLIENT_RECT,
                Component.METHOD_TO_TEMP_FILE_PATH,
                Component.METHOD_FOCUS,
                Image.METHOD_START_ANIMATION,
                Image.METHOD_STOP_ANIMAION
        })
public class Image extends Component<FlexImageView> implements Autoplay {

    protected static final String WIDGET_NAME = "image";
    protected static final String RESULT_WIDTH = "width";
    protected static final String RESULT_HEIGHT = "height";

    protected static final String EVENT_COMPLETE = "complete";
    protected static final String EVENT_ERROR = "error";

    protected static final String BLUR = "blur";

    protected static final String BLANK = "blank";

    // default true
    // true：#000，alpha 50%
    protected static final String ENABLE_NIGHT_MODE = "enablenightmode";

    protected static final String METHOD_START_ANIMATION = "startAnimation";
    protected static final String METHOD_STOP_ANIMAION = "stopAnimation";
    protected static final String AUTOPLAY = "autoplay";

    private boolean mHasCompleteListener = false;
    private boolean mHasErrorListener = false;
    private boolean mIsSrcInit = false;
    private boolean mEnableNightMode = true;
    private boolean mHasSetForceDark = false;
    private OnConfigurationListener mConfigurationListener;

    public Image(
            HapEngine hapEngine,
            Context context,
            Container parent,
            int ref,
            RenderEventCallback callback,
            Map<String, Object> savedState) {
        super(hapEngine, context, parent, ref, callback, savedState);
        mConfigurationListener = new OnConfigurationListener(this);
        ConfigurationManager.getInstance().addListener(mConfigurationListener);
    }

    public OnConfigurationListener getConfigurationListener() {
        return mConfigurationListener;
    }

    @Override
    protected FlexImageView createViewImpl() {
        FlexImageView imageView = new FlexImageView(mContext);
        imageView.setComponent(this);

        imageView.setOnLoadStatusListener(
                new FlexImageView.OnLoadStatusListener() {
                    @Override
                    public void onComplete(int width, int height) {
                        if (mHasCompleteListener) {
                            Map<String, Object> params = new HashMap<>();
                            params.put(
                                    RESULT_WIDTH, DisplayUtil.getDesignPxByWidth(width,
                                            mHapEngine.getDesignWidth()));
                            params.put(
                                    RESULT_HEIGHT,
                                    DisplayUtil.getDesignPxByWidth(height,
                                            mHapEngine.getDesignWidth()));
                            mCallback.onJsEventCallback(
                                    getPageId(), mRef, EVENT_COMPLETE, Image.this, params, null);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (mHasErrorListener) {
                            mCallback.onJsEventCallback(getPageId(), mRef, EVENT_ERROR, Image.this,
                                    null, null);
                        }
                    }
                });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageView.setForceDarkAllowed(false);
        }
        setNightMode(imageView, DarkThemeUtil.isDarkMode(mContext));
        return imageView;
    }

    @Override
    protected boolean setAttribute(String key, Object attribute) {
        switch (key) {
            case Attributes.Style.SRC:
                mIsSrcInit = true;
                String src = Attributes.getString(attribute);
                setSrc(src);
                return true;
            case Attributes.Style.RESIZE_MODE:
            case Attributes.Style.OBJECT_FIT:
                String resizeMode = Attributes.getString(attribute, Attributes.ObjectFit.COVER);
                setObjectFit(resizeMode);
                return true;
            case Attributes.Style.ALT_OBJECT_FIT:
                String altObjectFit = Attributes.getString(attribute, Attributes.ObjectFit.COVER);
                setAltObjectFit(altObjectFit);
                return true;
            case Attributes.Style.FILTER:
                setFilter(attribute);
                return true;
            case Attributes.Style.ALT:
                String alt = Attributes.getString(attribute);
                setAlt(alt);
                return true;
            case Attributes.Style.WIDTH:
                String width = Attributes.getString(attribute, "");
                int lastWidth = getWidth();
                setWidth(width);
                if (lastWidth != getWidth()) {
                    retrySrc();
                }
                return true;
            case Attributes.Style.HEIGHT:
                String height = Attributes.getString(attribute, "");
                int lastHeight = getHeight();
                setHeight(height);
                if (lastHeight != getHeight()) {
                    retrySrc();
                }
                return true;
            case ENABLE_NIGHT_MODE:
                if (!mHasSetForceDark) {
                    mEnableNightMode = Attributes.getBoolean(attribute, true);
                    setNightMode(mHost, DarkThemeUtil.isDarkMode(mContext));
                }
                return true;
            case Attributes.Style.FORCE_DARK:
                mHasSetForceDark = true;
                mEnableNightMode = Attributes.getBoolean(attribute, true);
                setNightMode(mHost, DarkThemeUtil.isDarkMode(mContext));
                return true;
            case AUTOPLAY:
                boolean autoplay = Attributes.getBoolean(attribute, true);
                setAutoplay(autoplay);
                return true;
            default:
                break;
        }
        return super.setAttribute(key, attribute);
    }

    /**
     * @param imageView host
     * @param nightMode current mode
     */
    public void setNightMode(ImageView imageView, boolean nightMode) {
        SysOpProvider sysOpProvider = ProviderManager.getDefault().getProvider(SysOpProvider.NAME);
        if (sysOpProvider != null && sysOpProvider.handleImageForceDark(imageView, mEnableNightMode)) {
            return;
        }

        if (imageView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }

        // clear color filter
        if (!mEnableNightMode || !nightMode || !mParent.getHostView().isForceDarkAllowed()) {
            imageView.clearColorFilter();
            return;
        }
        //close the default global color filter
        if (sysOpProvider != null && sysOpProvider.isCloseGlobalDefaultNightMode()) {
            imageView.clearColorFilter();
            return;
        }
        imageView.setColorFilter(Color.parseColor("#80000000"), PorterDuff.Mode.SRC_ATOP);
    }

    private void retrySrc() {
        if (isHeightDefined() && isWidthDefined() && mIsSrcInit) {
            mIsSrcInit = false;
            mHost.retrySource();
        }
    }

    @Override
    protected boolean addEvent(String event) {
        if (TextUtils.isEmpty(event) || mHost == null) {
            return true;
        }

        if (EVENT_COMPLETE.equals(event)) {
            mHasCompleteListener = true;
            return true;
        } else if (EVENT_ERROR.equals(event)) {
            mHasErrorListener = true;
            return true;
        }

        return super.addEvent(event);
    }

    @Override
    protected boolean removeEvent(String event) {
        if (TextUtils.isEmpty(event) || mHost == null) {
            return true;
        }

        switch (event) {
            case EVENT_COMPLETE:
                mHasCompleteListener = false;
                return true;
            case EVENT_ERROR:
                mHasErrorListener = false;
                return true;
            default:
                break;
        }

        return super.removeEvent(event);
    }

    @Override
    public void setBorderRadiusPercent(String position, float borderRadiusPercent) {
        if (FloatUtil.isUndefined(borderRadiusPercent) || borderRadiusPercent < 0
                || mHost == null) {
            return;
        }

        switch (position) {
            case Attributes.Style.BORDER_RADIUS:
                mHost.setBorderRadiusPercent(borderRadiusPercent);
                break;
            case Attributes.Style.BORDER_TOP_LEFT_RADIUS:
                mHost.setBorderRadiusPercent(Corner.TOP_LEFT, borderRadiusPercent);
                break;
            case Attributes.Style.BORDER_TOP_RIGHT_RADIUS:
                mHost.setBorderRadiusPercent(Corner.TOP_RIGHT, borderRadiusPercent);
                break;
            case Attributes.Style.BORDER_BOTTOM_LEFT_RADIUS:
                mHost.setBorderRadiusPercent(Corner.BOTTOM_LEFT, borderRadiusPercent);
                break;
            case Attributes.Style.BORDER_BOTTOM_RIGHT_RADIUS:
                mHost.setBorderRadiusPercent(Corner.BOTTOM_RIGHT, borderRadiusPercent);
                break;
            default:
                break;
        }

        super.setBorderRadiusPercent(position, borderRadiusPercent);
    }

    public void invokeMethod(String methodName, Map<String, Object> args) {
        super.invokeMethod(methodName, args);
        if (METHOD_START_ANIMATION.equals(methodName)) {
            startAnimation();
        } else if (METHOD_STOP_ANIMAION.equals(methodName)) {
            stopAnimation();
        }
    }

    @Override
    public void setBorderRadius(String position, float borderRadius) {
        if (FloatUtil.isUndefined(borderRadius) || borderRadius < 0 || mHost == null) {
            return;
        }

        switch (position) {
            case Attributes.Style.BORDER_RADIUS:
                mHost.setBorderRadius(borderRadius);
                break;
            case Attributes.Style.BORDER_TOP_LEFT_RADIUS:
                mHost.setBorderRadius(Corner.TOP_LEFT, borderRadius);
                break;
            case Attributes.Style.BORDER_TOP_RIGHT_RADIUS:
                mHost.setBorderRadius(Corner.TOP_RIGHT, borderRadius);
                break;
            case Attributes.Style.BORDER_BOTTOM_LEFT_RADIUS:
                mHost.setBorderRadius(Corner.BOTTOM_LEFT, borderRadius);
                break;
            case Attributes.Style.BORDER_BOTTOM_RIGHT_RADIUS:
                mHost.setBorderRadius(Corner.BOTTOM_RIGHT, borderRadius);
                break;
            default:
                break;
        }

        super.setBorderRadius(position, borderRadius);
    }

    @Override
    public void destroy() {
        super.destroy();
        ConfigurationManager.getInstance().removeListener(mConfigurationListener);
    }

    public void setSrc(String src) {
        if (mHost == null) {
            return;
        }

        if (TextUtils.isEmpty(src)) {
            mHost.setSource(null);
            return;
        }
        Uri uri = tryParseUri(src);
        mHost.setSource(uri);

        if (uri == null && mHasErrorListener) {
            mCallback.onJsEventCallback(getPageId(), mRef, EVENT_ERROR, Image.this, null, null);
        }
    }

    // 源图片的缩放模式
    public void setObjectFit(String objectFit) {
        if (mHost == null) {
            return;
        }

        mHost.setObjectFit(objectFit);
    }

    // 占位图的缩放模式
    public void setAltObjectFit(String altObjectFit) {
        if (mHost == null) {
            return;
        }

        mHost.setAltObjectFit(altObjectFit);
    }

    // 设置CSS滤镜，示例：filter:blur(5px)，支持多个滤镜并排
    // 暂时只支持blur滤镜
    public void setFilter(Object filterObject) {
        if (mHost == null || filterObject == null) {
            return;
        }
        JSONObject jsonObj = Transform.toJsonObject(filterObject);
        if (jsonObj == null) {
            return;
        }
        // 处理blur逻辑
        String blurString = jsonObj.optString(BLUR);
        // blur的长度只支持px、dp，不支持百分比，为WEB CSS定义，将通过toolkit提醒开发者
        // blurRadius < 0 时，效果与blurRadius = 0 一样
        if (!TextUtils.isEmpty(blurString)) {
            int blurRadius = Attributes.getInt(mHapEngine, blurString, 0);
            mHost.setBlurRadius(Math.max(blurRadius, 0));
        }
    }

    public void setAlt(String alt) {
        if (mHost == null) {
            return;
        }
        if (TextUtils.isEmpty(alt) || BLANK.equals(alt)) {
            mHost.setPlaceholderDrawable(null);
            return;
        }

        Uri uri = mCallback.getCache(alt);
        if (uri != null) {
            mHost.setPlaceholderDrawable(uri);
        }
    }

    public void setAutoplay(boolean autoplay) {
        if (mHost == null) {
            return;
        }
        stopAnimation();
        mHost.setAutoplay(autoplay);
    }

    public void startAnimation() {
        if (mHost == null) {
            return;
        }
        mHost.startAnimation();
    }

    public void stopAnimation() {
        if (mHost == null) {
            return;
        }
        mHost.stopAnimation();
    }

    @Override
    public void start() {
        startAnimation();
    }

    @Override
    public void stop() {
        stopAnimation();
    }

    @Override
    public boolean isRunning() {
        if (mHost != null) {
            return mHost.isAnimationRunning();
        }
        return false;
    }

    private static class OnConfigurationListener
            implements ConfigurationManager.ConfigurationListener {
        private WeakReference<Image> imageWeakReference;

        public OnConfigurationListener(Image image) {
            imageWeakReference = new WeakReference<>(image);
        }

        @Override
        public void onConfigurationChanged(HapConfiguration newConfig) {
            Image image = imageWeakReference.get();
            if (null != image) {
                if (newConfig.getUiMode() != newConfig.getLastUiMode()) {
                    if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO
                            && AppCompatDelegate.getDefaultNightMode()
                            != AppCompatDelegate.MODE_NIGHT_YES) {
                        boolean darkMode = newConfig.getUiMode() == Configuration.UI_MODE_NIGHT_YES;
                        image.setNightMode(image.mHost, darkMode);
                    }
                }
            }
        }
    }
}