package com.cliqz.browser.main;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.cliqz.browser.R;
import com.cliqz.browser.app.BrowserApp;
import com.cliqz.browser.main.search.SearchView;
import com.cliqz.browser.telemetry.Telemetry;
import com.cliqz.browser.telemetry.TelemetryKeys;
import com.cliqz.nove.Bus;

import java.util.Objects;
import java.util.regex.Pattern;

import javax.inject.Inject;

import acr.browser.lightning.utils.Utils;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * This handles the quick access bar and the query suggestions (as presenter)
 *
 * @author Stefano Pacifici
 */
public class QuickAccessBar extends FrameLayout {

    /*
        A little bit of information over this delay, to be acceptable the UI must be refreshed
        at least 15 times per second, so basically by saying that POSITION_UPDATER_DELAY is 1000/15
        milliseconds we basically skip a frame.
     */
    private static final long POSITION_UPDATER_DELAY = 1000L / 15L;

    private static final Pattern FILTER =
            Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);
    private static final int SUGGESTIONS_LIMIT = 3;
    private static final int SUGGESTIONS_TV_PADDING = 20;
    private static final int FONT_SIZE = 18;
    private static final int APPEAR_ANIMATION_START_DELAY = 200;
    private static final int APPEAR_ANIMATION_DURATION = 200;
    private static final int DISAPPEAR_ANIMATION_DURATION = 10;
    private static final int MAX_Y = Integer.MAX_VALUE >> 2;

    private ObjectAnimator mCurrentAnimator;
    private boolean mShown;
    private final PositionUpdater positionUpdater = new PositionUpdater();
    // Quick access bar measured position by orientation
    private SparseIntArray mY = new SparseIntArray();

    @BindView(R.id.query_suggestion_container)
    LinearLayout querySuggestionContainer;

    @BindView(R.id.access_bar_container)
    ViewGroup accessBarContainer;

    @BindView(R.id.tabs_overview_btn)
    Button tabsOverviewButton;

    @BindView(R.id.history_btn)
    Button historyButton;

    @BindView(R.id.offrz_btn)
    Button offrzButton;

    @BindView(R.id.favorites_btn)
    Button favoritesButton;

    @Inject
    Telemetry telemetry;

    @Inject
    Bus bus;

    @Inject
    SearchView searchView;

    public QuickAccessBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public QuickAccessBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
        setButtonsTopDrawable(context);
        setSaveEnabled(false);
    }

    private void init(Context context) {
        Objects.requireNonNull(BrowserApp.getActivityComponent(context)).inject(this);

        final LayoutInflater inflater = LayoutInflater.from(context);
        final View quickBar = inflater.inflate(R.layout.quick_acces_bar_layout, this);

        ButterKnife.bind(this, quickBar);
        // if Android less than 23 we have to tint the icons and possibly clone them
        final int count = accessBarContainer.getChildCount();
        final @ColorInt int tintColor = ContextCompat.getColor(context, R.color.primary_color_dark);
        for (int i = 0; i < count && Build.VERSION.SDK_INT < Build.VERSION_CODES.M; i++) {
            final View view = accessBarContainer.getChildAt(i);
            if (view instanceof Button) {
                final Button button = (Button) view;
                final Drawable topDrawable = button.getCompoundDrawables()[1];
                if (topDrawable == null) {
                    continue;
                }
                final Drawable tintedDrawable = topDrawable.mutate();
                DrawableCompat.setTint(tintedDrawable, tintColor);
                button.setCompoundDrawables(null, tintedDrawable, null, null);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void setButtonsTopDrawable(Context context){
        final Drawable tabOverviewDrawable =
                AppCompatResources.getDrawable(context, R.drawable.ic_tabs_black).mutate();
        final Drawable historyDrawable =
                AppCompatResources.getDrawable(context, R.drawable.ic_history_black).mutate();
        final Drawable favoriteDrawable =
                AppCompatResources.getDrawable(context, R.drawable.ic_star).mutate();
        final @ColorInt int color = ContextCompat.getColor(context, R.color.primary_color_dark);

        DrawableCompat.setTint(tabOverviewDrawable, color);
        DrawableCompat.setTint(historyDrawable, color);
        DrawableCompat.setTint(favoriteDrawable, color);

        tabsOverviewButton.setCompoundDrawablesWithIntrinsicBounds(null,tabOverviewDrawable,null,null);
        historyButton.setCompoundDrawablesWithIntrinsicBounds(null,historyDrawable,null,null);
        favoritesButton.setCompoundDrawablesWithIntrinsicBounds(null,favoriteDrawable,null,null);
        final Drawable offrzDrawable =
                AppCompatResources.getDrawable(context, R.drawable.ic_offrz_black).mutate();
        DrawableCompat.setTint(offrzDrawable, color);
        offrzButton.setCompoundDrawablesWithIntrinsicBounds(null, offrzDrawable, null, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int nhms;
        if (isKeyboardVisible()) {
            nhms = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED);
            postDelayed(positionUpdater, POSITION_UPDATER_DELAY);
        } else {
            nhms = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, nhms);
    }

    public void showAccessBar() {
        querySuggestionContainer.setVisibility(INVISIBLE);
        accessBarContainer.setVisibility(VISIBLE);
    }

    public void showSuggestions(String[] suggestions, String originalQuery) {
        int occupiedSpace = 0;
        int shownSuggestions = 0;
        querySuggestionContainer.removeAllViews();

        if ((originalQuery != null && URLUtil.isValidUrl(originalQuery)) ||
                suggestions == null || suggestions.length == 0) {
            accessBarContainer.setVisibility(VISIBLE);
            querySuggestionContainer.setVisibility(INVISIBLE);
            return;
        }

        accessBarContainer.setVisibility(INVISIBLE);
        querySuggestionContainer.setVisibility(VISIBLE);

        for (final String suggestion : suggestions) {
            if (shownSuggestions >= SUGGESTIONS_LIMIT) {
                break;
            }
            if (FILTER.matcher(suggestion).matches() ||
                    (originalQuery != null && originalQuery.trim().equals(suggestion))) {
                continue;
            }
            final TextView tv = new TextView(getContext());
            final ViewGroup.LayoutParams params =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            tv.setSelected(true);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE);
            tv.setPadding(SUGGESTIONS_TV_PADDING, SUGGESTIONS_TV_PADDING,
                    SUGGESTIONS_TV_PADDING, SUGGESTIONS_TV_PADDING);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tv.setLayoutParams(params);
            tv.setOnClickListener(v -> {
                bus.post(new Messages.UpdateQuery(suggestion));
                //Dividing by 2 to get the 0 based index of the TextViews excluding the Separator
                telemetry.sendQuerySuggestionClickSignal(querySuggestionContainer.indexOfChild(tv) / 2);
            });
            final int beginIndex;
            if (originalQuery != null && suggestion.startsWith(originalQuery)) {
                beginIndex = originalQuery.length();
            } else {
                beginIndex = 0;
            }
            final SpannableStringBuilder stringBuilder = new SpannableStringBuilder(suggestion);
            stringBuilder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    beginIndex, suggestion.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tv.setText(stringBuilder);
            final TextPaint paint = tv.getPaint();
            final Rect rect = new Rect();
            paint.getTextBounds(tv.getText().toString(), 0, tv.getText().length(), rect);
            if (rect.width() < querySuggestionContainer.getWidth() - occupiedSpace - 2 * SUGGESTIONS_TV_PADDING) {
                querySuggestionContainer.addView(tv);
                occupiedSpace = occupiedSpace + rect.width() + 2 * SUGGESTIONS_TV_PADDING;
                shownSuggestions++;
                final View divider = new View(getContext());
                final LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        Utils.dpToPx(1), ViewGroup.LayoutParams.MATCH_PARENT);
                dividerParams.setMargins(0, SUGGESTIONS_TV_PADDING, 0, SUGGESTIONS_TV_PADDING);
                divider.setLayoutParams(dividerParams);
                divider.setBackgroundColor(Color.BLACK);
                querySuggestionContainer.addView(divider);
            }
        }

        telemetry.sendQuerySuggestionShowSignal(Math.min(suggestions.length, SUGGESTIONS_LIMIT), shownSuggestions);
    }

    private int getPositionForOrientation() {
        final int orientation = getResources().getConfiguration().orientation;
        return mY.get(orientation, Integer.MAX_VALUE);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isKeyboardVisible() &&
                getPositionForOrientation() != Integer.MAX_VALUE) {
            startAppearAnimation(true);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus && MAX_Y < getPositionForOrientation()) {
            setY(MAX_Y);
            setPositionForOrientation(MAX_Y);
        }
    }

    private void setPositionForOrientation(int y) {
        final int orientation = getResources().getConfiguration().orientation;
        mY.append(orientation, y);
    }

    private int getParentHeight() {
        return ((ViewGroup) getParent()).getHeight();
    }
    
    @OnClick(R.id.tabs_overview_btn)
    void onTabsOverviewButtonClicked() {
        safeBusPost(new Messages.GoToOverview());
        safeTelemetry(TelemetryKeys.OPEN_TABS);
    }

    @OnClick(R.id.history_btn)
    void onHistoryButtonClicked() {
        safeBusPost(new Messages.GoToHistory());
        safeTelemetry(TelemetryKeys.HISTORY);
    }

    @OnClick(R.id.favorites_btn)
    void onFavoritesButtonClicked() {
        safeBusPost(new Messages.GoToFavorites());
        safeTelemetry(TelemetryKeys.FAVORITES);
    }

    @OnClick(R.id.offrz_btn)
    void onOffrzButtonClicked() {
        safeBusPost(new Messages.GoToOffrz());
        safeTelemetry(TelemetryKeys.OFFRZ);
    }

    private void safeBusPost(Object msg) {
        if (bus != null) {
            bus.post(msg);
        }
    }

    private void safeTelemetry(@Nullable String target) {
        if (telemetry != null) {
            final String view = searchView != null && searchView.isFreshTabVisible() ?
                    TelemetryKeys.HOME : TelemetryKeys.CARDS;
            telemetry.sendQuickAccessBarSignal(TelemetryKeys.CLICK, target, view);
        }
    }

    public void show() {
        if (mShown) {
            return;
        }
        mShown = true;
        bringToFront();
        cancelCurrentAnimation();
        startAppearAnimation(false);
    }

    public void hide() {
        if (!mShown) {
            return;
        }
        mShown = false;
        cancelCurrentAnimation();
        startDisappearAnimation();
    }

    private void cancelCurrentAnimation() {
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
            mCurrentAnimator = null;
        }
    }

    private void startAppearAnimation(boolean now) {
        final int toY = getPositionForOrientation();
        if (toY == Integer.MAX_VALUE) {
            return;
        }
        final float fromY = getParentHeight();
        setY(fromY);
        mCurrentAnimator = ObjectAnimator.ofFloat(this, "y", fromY, toY);
        if (!now) {
            mCurrentAnimator.setStartDelay(APPEAR_ANIMATION_START_DELAY);
        }
        mCurrentAnimator.setDuration(APPEAR_ANIMATION_DURATION);
        mCurrentAnimator.start();
    }

    private void startDisappearAnimation() {
        final int fromY = getPositionForOrientation();
        if (fromY == Integer.MAX_VALUE) {
            return;
        }
        final float toY = getParentHeight();
        mCurrentAnimator = ObjectAnimator.ofFloat(this, "y", fromY, toY);
        mCurrentAnimator.setDuration(DISAPPEAR_ANIMATION_DURATION);
        mCurrentAnimator.start();
    }

    private boolean isKeyboardVisible() {
        final int parentHeight = getParentHeight();
        if (parentHeight == 0) {
            return false;
        }
        final Rect visibleFrame = new Rect();
        getWindowVisibleDisplayFrame(visibleFrame);
        final float visibilityRatio = ((float) parentHeight) /
                ((float) visibleFrame.bottom - visibleFrame.top);
        return Math.abs(1.0f - visibilityRatio) > 0.2f;
    }

    private class PositionUpdater implements Runnable {
        @Override
        public void run() {
            if (isKeyboardVisible()) {
                final Rect visibleFrame = new Rect();
                getWindowVisibleDisplayFrame(visibleFrame);
                final int y = visibleFrame.bottom - accessBarContainer.getHeight();
                final int currentY = getPositionForOrientation();
                if (y < currentY) {
                    setPositionForOrientation(y);
                    if (mShown) {
                        cancelCurrentAnimation();
                        startAppearAnimation(false);
                    }
                }
            } else {
                setY(MAX_Y);
            }
        }
    }
}
