/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import androidx.annotation.Keep;

public class ShutterButton extends View {

    public enum State {
        DEFAULT,
        RECORDING
    }

    private final static int LONG_PRESS_TIME = 800;

    private Drawable shadowDrawable;

    public ChatAttachAlertPhotoLayout.Mode cameraMode = ChatAttachAlertPhotoLayout.Mode.PHOTO;

    private DecelerateInterpolator interpolator = new DecelerateInterpolator();
    private Paint whitePaint;
    private Paint redPaint;
    private ShutterButtonDelegate delegate;
    private State state;
    private boolean pressed;
    private float redProgress;
    private long lastUpdateTime;
    private long totalTime;
    private boolean processRelease;

    private Runnable longPressed = new Runnable() {
        public void run() {
            if (delegate != null) {
                if (!delegate.shutterLongPressed()) {
                    processRelease = false;
                }
            }
        }
    };

    public interface ShutterButtonDelegate {
        boolean shutterLongPressed();
        void shutterReleased();
        void shutterCancel();
        boolean onTranslationChanged(float x, float y);
    }

    public ShutterButton(Context context) {
        super(context);
        shadowDrawable = getResources().getDrawable(R.drawable.camera_btn2);
        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(0xffffffff);
        redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        redPaint.setStyle(Paint.Style.FILL);
        redPaint.setColor(0xffcd4747);
        state = State.DEFAULT;
    }

    public void setDelegate(ShutterButtonDelegate shutterButtonDelegate) {
        delegate = shutterButtonDelegate;
    }

    public ShutterButtonDelegate getDelegate() {
        return delegate;
    }

    private void setHighlighted(boolean value) {
        AnimatorSet animatorSet = new AnimatorSet();
        if (value) {
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.SCALE_X, 1.06f),
                    ObjectAnimator.ofFloat(this, View.SCALE_Y, 1.06f));
        } else {
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(this, View.SCALE_Y, 1.0f));
            animatorSet.setStartDelay(40);
        }
        animatorSet.setDuration(120);
        animatorSet.setInterpolator(interpolator);
        animatorSet.start();
    }

    @Keep
    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        invalidate();
    }

    public State getState() {
        return state;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cx = getMeasuredWidth() / 2;
        int cy = getMeasuredHeight() / 2;

        shadowDrawable.setBounds(cx - AndroidUtilities.dp(36), cy - AndroidUtilities.dp(36), cx + AndroidUtilities.dp(36), cy + AndroidUtilities.dp(36));
        shadowDrawable.draw(canvas);

        float scale = (getScaleX() - 1.0f) / 0.06f;

        System.out.println(pressed + " " + state + " " + cameraMode);
        if (pressed || getScaleX() != 1.0f || cameraMode == ChatAttachAlertPhotoLayout.Mode.VIDEO) {
            if (state == State.RECORDING) {
                shadowDrawable = getResources().getDrawable(R.drawable.camera_btn);
                float size = AndroidUtilities.dp(10) * scale;
                float left = cx - size;
                float top = cy - size;
                float right = cx + size;
                float bottom = cy + size;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    redPaint.setColor(Color.parseColor("#F73131"));
                    canvas.drawRoundRect(left, top, right, bottom, AndroidUtilities.dp(6), AndroidUtilities.dp(6), redPaint);
                }
            } else {
                if (cameraMode == ChatAttachAlertPhotoLayout.Mode.PHOTO) {
                    shadowDrawable = getResources().getDrawable(R.drawable.camera_btn2);
                } else {
                    shadowDrawable = getResources().getDrawable(R.drawable.camera_btn3);
                }
            }
        } else {
            if (cameraMode == ChatAttachAlertPhotoLayout.Mode.PHOTO) {
                shadowDrawable = getResources().getDrawable(R.drawable.camera_btn2);
            } else {
                shadowDrawable = getResources().getDrawable(R.drawable.camera_btn3);
            }
        }
        shadowDrawable.draw(canvas);
        invalidate();
    }



    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(84), AndroidUtilities.dp(84));
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        float x = motionEvent.getX();
        float y = motionEvent.getY();
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (cameraMode == ChatAttachAlertPhotoLayout.Mode.VIDEO) {
                    if (state == State.DEFAULT) {
                        pressed = true;
                        processRelease = true;
                        AndroidUtilities.runOnUIThread(longPressed, LONG_PRESS_TIME);
                        setHighlighted(true);
                        if (delegate != null) {
                            delegate.shutterLongPressed();
                        }
                        setState(State.RECORDING, true);
                    } else if (state == State.RECORDING) {
                        // Завершение записи
                        setHighlighted(false);
                        if (delegate != null) {
                            delegate.shutterReleased();
                        }
                        setState(State.DEFAULT, true);
                    }
                } else {
                    AndroidUtilities.runOnUIThread(longPressed, LONG_PRESS_TIME);
                    pressed = true;
                    processRelease = true;
                    setHighlighted(true);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (cameraMode == ChatAttachAlertPhotoLayout.Mode.PHOTO) {
                    setHighlighted(false);
                    AndroidUtilities.cancelRunOnUIThread(longPressed);
                    if (processRelease && delegate != null) {
                        delegate.shutterReleased();
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (cameraMode == ChatAttachAlertPhotoLayout.Mode.PHOTO) {
                    float dx = x >= 0 && x <= getMeasuredWidth() ? 0 : x;
                    float dy = y >= 0 && y <= getMeasuredHeight() ? 0 : y;
                    if (delegate != null && delegate.onTranslationChanged(dx, dy)) {
                        AndroidUtilities.cancelRunOnUIThread(longPressed);
                        if (state == State.RECORDING) {
                            processRelease = false;
                            setHighlighted(false);
                            delegate.shutterCancel();
                            setState(State.DEFAULT, true);
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                setHighlighted(false);
                pressed = false;
                if (cameraMode == ChatAttachAlertPhotoLayout.Mode.PHOTO) {
                    AndroidUtilities.cancelRunOnUIThread(longPressed);
                }
                break;
        }
        return true;
    }


    public void setState(State value, boolean animated) {
        if (state != value) {
            state = value;
            if (animated) {
                lastUpdateTime = System.currentTimeMillis();
                totalTime = 0;
                redProgress = 0.0f;
            }
            invalidate();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.setClickable(true);
        info.setLongClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId(), LocaleController.getString(R.string.AccActionTakePicture)));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK.getId(), LocaleController.getString(R.string.AccActionRecordVideo)));
        }
    }
}
