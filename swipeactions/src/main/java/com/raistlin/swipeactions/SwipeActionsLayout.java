package com.raistlin.swipeactions;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;

public class SwipeActionsLayout extends ViewGroup {

    private static final String LOG_TAG = SwipeActionsLayout.class.getSimpleName();

    private static final int MAX_ALPHA = 255;
    private static final int STARTING_PROGRESS_ALPHA = (int) (.3f * MAX_ALPHA);

    private static final int CIRCLE_DIAMETER = 40;

    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final int INVALID_POINTER = -1;
    private static final float DRAG_RATE = .5f;

    private static final int ALPHA_ANIMATION_DURATION = 300;
    private static final int ANIMATE_TO_TRIGGER_DURATION = 200;
    private static final int ANIMATE_TO_START_DURATION = 200;

    // Default background for the progress spinner
    private static final int CIRCLE_BG_LIGHT = 0xFFFAFAFA;
    // Default offset in dips from the top of the view to where the progress spinner should stop
    private static final int DEFAULT_CIRCLE_TARGET = 64;

    private SwipeDirection mSwipeDirection = SwipeDirection.NONE;

    private View mTarget; // the target of the gesture
    private ActionsListener mListener;
    private int mTouchSlop;
    private float mTotalDragDistance = -1;

    // Whether or not the starting offset has been determined.
    private boolean mOriginalOffsetCalculated = false;

    private float mInitialMotionX;
    private float mInitialDownX;
    private float mInitialDownY;
    private boolean mIsBeingDragged;
    private int mActivePointerId = INVALID_POINTER;

    private final DecelerateInterpolator mDecelerateInterpolator;
    private static final int[] LAYOUT_ATTRS = new int[]{
            android.R.attr.enabled
    };

    private CircleImageView mLeftImage;
    private CircleImageView mRightImage;

    protected CircleImageView mAnimationImage;
    protected int mFrom;

    private Animation mAlphaStartAnimation;
    private Animation mAlphaMaxAnimation;

    private float mSpinnerFinalOffset;

    private int mCircleWidth;
    private int mCircleHeight;

    private Animation.AnimationListener mRefreshListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            mAnimationImage.setVisibility(View.GONE);
            setColorViewAlpha(MAX_ALPHA);
            if (mListener != null) {
                if (mAnimationImage == mLeftImage) {
                    mListener.onActionSelected(SwipeDirection.LEFT);
                } else if (mAnimationImage == mRightImage) {
                    mListener.onActionSelected(SwipeDirection.RIGHT);
                }
            }
            // Return the circle to its start position
            setTargetOffsetLeftAndRight(mAnimationImage, mAnimationImage.getOriginalOffset() - mAnimationImage.getCurrentTargetOffset(), true /* requires update */);
            mAnimationImage.setCurrentTargetOffset(mAnimationImage.getOriginalOffset());
        }
    };

    private void setColorViewAlpha(int targetAlpha) {
        getCurrentSwipeView().setAllAlpha(targetAlpha);
    }

    private CircleImageView getCurrentSwipeView() {
        if (mSwipeDirection == SwipeDirection.RIGHT) {
            return mRightImage;
        } else if (mSwipeDirection == SwipeDirection.LEFT) {
            return mLeftImage;
        } else {
            return mLeftImage;
        }
    }

    /**
     * Simple constructor to use when creating a SwipeRefreshLayout from code.
     */
    public SwipeActionsLayout(Context context) {
        this(context, null);
    }

    /**
     * Constructor that is called when inflating SwipeRefreshLayout from XML.
     */
    public SwipeActionsLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setWillNotDraw(false);
        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

        final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
        setEnabled(a.getBoolean(0, true));
        a.recycle();

        TypedArray attr = context.obtainStyledAttributes(attrs, R.styleable.SwipeActionsLayout);
        int leftImage = attr.getResourceId(R.styleable.SwipeActionsLayout_left_image, -1);
        int rightImage = attr.getResourceId(R.styleable.SwipeActionsLayout_right_image, -1);
        attr.recycle();

        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        mCircleWidth = (int) (CIRCLE_DIAMETER * metrics.density);
        mCircleHeight = (int) (CIRCLE_DIAMETER * metrics.density);

        createProgressView(leftImage, rightImage);
        // the absolute offset has to take into account that the circle starts at an offset
        mSpinnerFinalOffset = DEFAULT_CIRCLE_TARGET * metrics.density;
        mTotalDragDistance = mSpinnerFinalOffset;
    }

    private void createProgressView(int leftImage, int rightImage) {
        mLeftImage = new CircleImageView(getContext(), CIRCLE_BG_LIGHT, CIRCLE_DIAMETER / 2, leftImage);
        mLeftImage.setVisibility(View.GONE);
        addView(mLeftImage);

        mRightImage = new CircleImageView(getContext(), CIRCLE_BG_LIGHT, CIRCLE_DIAMETER / 2, rightImage);
        mRightImage.setVisibility(View.GONE);
        addView(mRightImage);
    }

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe
     * gesture.
     */
    public void setActionsListener(ActionsListener listener) {
        mListener = listener;
    }

    private void completeAction() {
        ensureTarget();
        animateOffsetToCorrectPosition(getCurrentSwipeView(), getCurrentSwipeView().getCurrentTargetOffset(), mRefreshListener);
    }

    private void startProgressAlphaStartAnimation() {
        mAlphaStartAnimation = startAlphaAnimation(getCurrentSwipeView().getDrawableAlpha(), STARTING_PROGRESS_ALPHA);
    }

    private void startProgressAlphaMaxAnimation() {
        mAlphaMaxAnimation = startAlphaAnimation(getCurrentSwipeView().getDrawableAlpha(), MAX_ALPHA);
    }

    private Animation startAlphaAnimation(final int startingAlpha, final int endingAlpha) {
        Animation alpha = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                getCurrentSwipeView().setDrawableAlpha((int) (startingAlpha + ((endingAlpha - startingAlpha) * interpolatedTime)));
            }
        };
        alpha.setDuration(ALPHA_ANIMATION_DURATION);
        // Clear out the previous animation listeners.
        getCurrentSwipeView().setAnimationListener(null);
        getCurrentSwipeView().clearAnimation();
        getCurrentSwipeView().startAnimation(alpha);
        return alpha;
    }

    private void ensureTarget() {
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mLeftImage) && !child.equals(mRightImage)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        final View child = mTarget;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        int circleWidth = mLeftImage.getMeasuredWidth();
        int circleHeight = mLeftImage.getMeasuredHeight();
        if (mSwipeDirection == SwipeDirection.LEFT) {
            mLeftImage.layout(mLeftImage.getCurrentTargetOffset(), (height / 2 - circleHeight / 2),
                    mLeftImage.getCurrentTargetOffset() + circleWidth, (height / 2 + circleHeight / 2));
        } else if (mSwipeDirection == SwipeDirection.RIGHT) {
            mRightImage.layout(mTarget.getMeasuredWidth() + mRightImage.getCurrentTargetOffset(), (height / 2 - circleHeight / 2),
                    mTarget.getMeasuredWidth() + mRightImage.getCurrentTargetOffset() + circleWidth, (height / 2 + circleHeight / 2));
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        mTarget.measure(MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
        mLeftImage.measure(MeasureSpec.makeMeasureSpec(mCircleWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mCircleHeight, MeasureSpec.EXACTLY));
        mRightImage.measure(MeasureSpec.makeMeasureSpec(mCircleWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mCircleHeight, MeasureSpec.EXACTLY));
        if (!mOriginalOffsetCalculated) {
            mOriginalOffsetCalculated = true;
            mLeftImage.setOriginalOffset(-mLeftImage.getMeasuredWidth());
            mLeftImage.setCurrentTargetOffset(-mLeftImage.getMeasuredWidth());
            mRightImage.setOriginalOffset(0);
            mRightImage.setCurrentTargetOffset(0);
        }
    }

    public boolean canChildScrollHorizontal() {
        return ViewCompat.canScrollHorizontally(mTarget, -1) || ViewCompat.canScrollHorizontally(mTarget, 1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();

        final int action = MotionEventCompat.getActionMasked(ev);

        if (!isEnabled() || canChildScrollHorizontal()) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                setTargetOffsetLeftAndRight(mLeftImage, mLeftImage.getOriginalOffset() - mLeftImage.getLeft(), true);
                setTargetOffsetLeftAndRight(mRightImage, mTarget.getMeasuredWidth() + mRightImage.getOriginalOffset(), true);
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                final float initialDownX = getMotionEventX(ev, mActivePointerId);
                if (initialDownX == -1) {
                    return false;
                }
                mInitialDownX = initialDownX;
                mInitialDownY = getMotionEventY(ev, mActivePointerId);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                final float x = getMotionEventX(ev, mActivePointerId);
                if (x == -1) {
                    return false;
                }
                final float y = getMotionEventY(ev, mActivePointerId);
                final float xDiff = x - mInitialDownX;
                final float yDiff = y - mInitialDownY;
                if (!mIsBeingDragged) {
                    if (Math.abs(xDiff) > 3 * Math.abs(yDiff)) {
                        if (xDiff > mTouchSlop) {
                            mSwipeDirection = SwipeDirection.LEFT;
                            mInitialMotionX = mInitialDownX + mTouchSlop;
                            mIsBeingDragged = true;
                            getCurrentSwipeView().setDrawableAlpha(STARTING_PROGRESS_ALPHA);
                        } else if (xDiff < -mTouchSlop) {
                            mSwipeDirection = SwipeDirection.RIGHT;
                            mInitialMotionX = mInitialDownX - mTouchSlop;
                            mIsBeingDragged = true;
                            getCurrentSwipeView().setDrawableAlpha(STARTING_PROGRESS_ALPHA);
                        }
                    } else {
                        // replace the initial point because movement was too horizontal
                        mInitialDownX = x;
                        mInitialDownY = y;
                    }
                }
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mSwipeDirection = SwipeDirection.NONE;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged;
    }

    private float getMotionEventX(MotionEvent ev, int activePointerId) {
        final int index = ev.findPointerIndex(activePointerId);
        if (index < 0) {
            return -1;
        }
        return ev.getX(index);
    }

    private float getMotionEventY(MotionEvent ev, int activePointerId) {
        final int index = ev.findPointerIndex(activePointerId);
        if (index < 0) {
            return -1;
        }
        return ev.getY(index);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // Nope.
    }

    private boolean isAnimationRunning(Animation animation) {
        return animation != null && animation.hasStarted() && !animation.hasEnded();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        if (!isEnabled() || canChildScrollHorizontal()) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float x = getMotionEventX(ev, pointerIndex);
                if (x == -1) {
                    return false;
                }
                final float overscroll = mSwipeDirection.getMultiplier() * (x - mInitialMotionX) * DRAG_RATE;
                if (mIsBeingDragged) {
                    float originalDragPercent = overscroll / mTotalDragDistance;
                    if (originalDragPercent < 0) {
                        return false;
                    }
                    float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
                    float extraOS = Math.abs(overscroll) - mTotalDragDistance;
                    float slingshotDist = mSpinnerFinalOffset;
                    float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2) / slingshotDist);
                    float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow((tensionSlingshotPercent / 4), 2)) * 2f;
                    float extraMove = (slingshotDist) * tensionPercent * 2;

                    int targetX = getCurrentSwipeView().getOriginalOffset() + mSwipeDirection.getMultiplier() * (int) ((slingshotDist * dragPercent) + extraMove);
                    // where 1.0f is a full circle
                    if (getCurrentSwipeView().getVisibility() != View.VISIBLE) {
                        getCurrentSwipeView().setVisibility(View.VISIBLE);
                    }
                    getCurrentSwipeView().setScaledProgress(1f);
                    if (overscroll < mTotalDragDistance) {
                        if (getCurrentSwipeView().getDrawableAlpha() > STARTING_PROGRESS_ALPHA && !isAnimationRunning(mAlphaStartAnimation)) {
                            // Animate the alpha
                            startProgressAlphaStartAnimation();
                        }
                    } else {
                        if (getCurrentSwipeView().getDrawableAlpha() < MAX_ALPHA && !isAnimationRunning(mAlphaMaxAnimation)) {
                            // Animate the alpha
                            startProgressAlphaMaxAnimation();
                        }
                    }
                    if (mSwipeDirection == SwipeDirection.LEFT) {
                        setTargetOffsetLeftAndRight(mLeftImage, targetX - mLeftImage.getCurrentTargetOffset(), true /* requires update */);
                    } else {
                        setTargetOffsetLeftAndRight(mRightImage, targetX - mRightImage.getCurrentTargetOffset(), true /* requires update */);
                    }
                }
                break;
            }
            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);
                mActivePointerId = ev.getPointerId(index);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mActivePointerId == INVALID_POINTER) {
                    if (action == MotionEvent.ACTION_UP) {
                        Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                    }
                    return false;
                }
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = getMotionEventX(ev, pointerIndex);
                if (x == -1) {
                    return false;
                }
                final float overscrollTop = mSwipeDirection.getMultiplier() * (x - mInitialMotionX) * DRAG_RATE;
                mIsBeingDragged = false;
                if (overscrollTop > mTotalDragDistance) {
                    completeAction();
                } else {
                    animateOffsetToStartPosition(getCurrentSwipeView(), getCurrentSwipeView().getCurrentTargetOffset());
                }
                mActivePointerId = INVALID_POINTER;
                mSwipeDirection = SwipeDirection.NONE;
                return false;
            }
        }
        return true;
    }

    private void animateOffsetToCorrectPosition(CircleImageView image, int from, AnimationListener listener) {
        mAnimationImage = image;
        mFrom = from;
        mAnimateToCorrectPosition.reset();
        mAnimateToCorrectPosition.setDuration(ANIMATE_TO_TRIGGER_DURATION);
        mAnimateToCorrectPosition.setInterpolator(mDecelerateInterpolator);
        getCurrentSwipeView().setAnimationListener(listener);
        getCurrentSwipeView().clearAnimation();
        getCurrentSwipeView().startAnimation(mAnimateToCorrectPosition);
    }

    private void animateOffsetToStartPosition(CircleImageView image, int from) {
        mAnimationImage = image;
        mFrom = from;
        mAnimateToStartPosition.reset();
        mAnimateToStartPosition.setDuration(ANIMATE_TO_START_DURATION);
        mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
        getCurrentSwipeView().setAnimationListener(null);
        getCurrentSwipeView().clearAnimation();
        getCurrentSwipeView().startAnimation(mAnimateToStartPosition);
    }

    private final Animation mAnimateToCorrectPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int targetLeft;
            if (mAnimationImage == mLeftImage) {
                int endTarget = (int) (mSpinnerFinalOffset - Math.abs(mAnimationImage.getOriginalOffset()));
                targetLeft = (mFrom + (int) ((endTarget - mFrom) * interpolatedTime));
            } else {
                int endTarget = (int) (Math.abs(mAnimationImage.getOriginalOffset()) - mSpinnerFinalOffset);
                targetLeft = (mTarget.getMeasuredWidth() + mFrom + (int) ((endTarget - mFrom) * interpolatedTime));
            }
            int offset = targetLeft - mAnimationImage.getLeft();
            setTargetOffsetLeftAndRight(mAnimationImage, offset, false /* requires update */);
        }
    };

    private void moveToStart(float interpolatedTime) {
        int targetLeft;
        if (mAnimationImage == mLeftImage) {
            targetLeft = (mFrom + (int) ((mAnimationImage.getOriginalOffset() - mFrom) * interpolatedTime));
        } else {
            targetLeft = (mTarget.getMeasuredWidth() + mFrom + (int) ((mAnimationImage.getOriginalOffset() - mFrom) * interpolatedTime));
        }
        int offset = targetLeft - mAnimationImage.getLeft();
        setTargetOffsetLeftAndRight(mAnimationImage, offset, false /* requires update */);
    }

    private final Animation mAnimateToStartPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            moveToStart(interpolatedTime);
        }
    };

    private void setTargetOffsetLeftAndRight(CircleImageView view, int offset, boolean requiresUpdate) {
        view.bringToFront();
        view.offsetLeftAndRight(offset);
        if (view == mLeftImage) {
            view.setCurrentTargetOffset(view.getLeft());
        } else if (view == mRightImage) {
            view.setCurrentTargetOffset(view.getLeft() - mTarget.getMeasuredWidth());
        }
        if (requiresUpdate && android.os.Build.VERSION.SDK_INT < 11) {
            invalidate();
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a refresh should implement this interface.
     */
    public interface ActionsListener {
        void onActionSelected(SwipeDirection direction);
    }
}