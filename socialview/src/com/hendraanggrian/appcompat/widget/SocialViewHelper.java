package com.hendraanggrian.appcompat.widget;

import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.PatternsCompat;

import com.hendraanggrian.appcompat.socialview.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to help implement {@link SocialView} on any {@link TextView}-based class.
 */
public final class SocialViewHelper implements SocialView {
    private static final int FLAG_HASHTAG = 1;
    private static final int FLAG_MENTION = 2;
    private static final int FLAG_HYPERLINK = 4;

    private static final Pattern PATTERN_HASHTAG = Pattern.compile("#(\\w+)");
    private static final Pattern PATTERN_MENTION = Pattern.compile("@(\\w+)");
    private static final Pattern PATTERN_HYPERLINK = PatternsCompat.WEB_URL;

    private int flags;
    private Pattern hashtagPattern = PATTERN_HASHTAG;
    private Pattern mentionPattern = PATTERN_MENTION;
    private Pattern hyperlinkPattern = PATTERN_HYPERLINK;

    private final TextView view;
    private ColorStateList hashtagColors;
    private ColorStateList mentionColors;
    private ColorStateList hyperlinkColors;
    private OnClickListener hashtagClickListener;
    private OnClickListener mentionClickListener;
    private OnClickListener hyperlinkClickListener;
    private OnChangedListener hashtagChangedListener;
    private OnChangedListener mentionChangedListener;
    private boolean hashtagEditing;
    private boolean mentionEditing;

    @SuppressWarnings("FieldCanBeLocal")
    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (count > 0 && start > 0) {
                final char c = s.charAt(start - 1);
                switch (c) {
                    case '#':
                        hashtagEditing = true;
                        mentionEditing = false;
                        break;
                    case '@':
                        hashtagEditing = false;
                        mentionEditing = true;
                        break;
                    default:
                        if (!Character.isLetterOrDigit(c)) {
                            hashtagEditing = false;
                            mentionEditing = false;
                        } else if (hashtagChangedListener != null && hashtagEditing) {
                            hashtagChangedListener.onChanged(SocialViewHelper.this, s.subSequence(
                                indexOfPreviousNonLetterDigit(s, 0, start - 1) + 1, start
                            ));
                        } else if (mentionChangedListener != null && mentionEditing) {
                            mentionChangedListener.onChanged(SocialViewHelper.this, s.subSequence(
                                indexOfPreviousNonLetterDigit(s, 0, start - 1) + 1, start
                            ));
                        }
                        break;
                }
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // triggered when text is added
            if (s.length() == 0) {
                return;
            }
            colorize();
            if (start < s.length()) {
                final int index = start + count - 1;
                if (index < 0) {
                    return;
                }
                switch (s.charAt(index)) {
                    case '#':
                        hashtagEditing = true;
                        mentionEditing = false;
                        break;
                    case '@':
                        hashtagEditing = false;
                        mentionEditing = true;
                        break;
                    default:
                        if (!Character.isLetterOrDigit(s.charAt(start))) {
                            hashtagEditing = false;
                            mentionEditing = false;
                        } else if (hashtagChangedListener != null && hashtagEditing) {
                            hashtagChangedListener.onChanged(SocialViewHelper.this, s.subSequence(
                                indexOfPreviousNonLetterDigit(s, 0, start) + 1, start + count
                            ));
                        } else if (mentionChangedListener != null && mentionEditing) {
                            mentionChangedListener.onChanged(SocialViewHelper.this, s.subSequence(
                                indexOfPreviousNonLetterDigit(s, 0, start) + 1, start + count
                            ));
                        }
                        break;
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    public SocialViewHelper(@NonNull TextView textView, @Nullable AttributeSet attrs) {
        view = textView;
        view.addTextChangedListener(textWatcher);
        view.setText(view.getText(), TextView.BufferType.SPANNABLE);
        final TypedArray a = view.getContext().obtainStyledAttributes(
            attrs, R.styleable.SocialView, R.attr.socialViewStyle, R.style.Widget_SocialView
        );
        flags = a.getInteger(R.styleable.SocialView_socialFlags, FLAG_HASHTAG | FLAG_MENTION | FLAG_HYPERLINK);
        hashtagColors = a.getColorStateList(R.styleable.SocialView_hashtagColor);
        mentionColors = a.getColorStateList(R.styleable.SocialView_mentionColor);
        hyperlinkColors = a.getColorStateList(R.styleable.SocialView_hyperlinkColor);
        a.recycle();
        colorize();
    }

    @Override
    public void setHashtagPattern(@Nullable Pattern pattern) {
        hashtagPattern = pattern != null ? pattern : PATTERN_HASHTAG;
    }

    @Override
    public void setMentionPattern(@Nullable Pattern pattern) {
        mentionPattern = pattern != null ? pattern : PATTERN_MENTION;
    }

    @Override
    public void setHyperlinkPattern(@Nullable Pattern pattern) {
        hyperlinkPattern = pattern != null ? pattern : PATTERN_HYPERLINK;
    }

    @Override
    public boolean isHashtagEnabled() {
        return (flags | FLAG_HASHTAG) == flags;
    }

    @Override
    public void setHashtagEnabled(boolean enabled) {
        if (enabled != isHashtagEnabled()) {
            flags = enabled ? flags | FLAG_HASHTAG : flags & (~FLAG_HASHTAG);
            colorize();
        }
    }

    @Override
    public boolean isMentionEnabled() {
        return (flags | FLAG_MENTION) == flags;
    }

    @Override
    public void setMentionEnabled(boolean enabled) {
        if (enabled != isMentionEnabled()) {
            flags = enabled ? flags | FLAG_MENTION : flags & (~FLAG_MENTION);
            colorize();
        }
    }

    @Override
    public boolean isHyperlinkEnabled() {
        return (flags | FLAG_HYPERLINK) == flags;
    }

    @Override
    public void setHyperlinkEnabled(boolean enabled) {
        if (enabled != isHyperlinkEnabled()) {
            flags = enabled ? flags | FLAG_HYPERLINK : flags & (~FLAG_HYPERLINK);
            colorize();
        }
    }

    @NonNull
    @Override
    public ColorStateList getHashtagColors() {
        return hashtagColors;
    }

    @Override
    public void setHashtagColors(@NonNull ColorStateList colors) {
        hashtagColors = colors;
        colorize();
    }

    @NonNull
    @Override
    public ColorStateList getMentionColors() {
        return mentionColors;
    }

    @Override
    public void setMentionColors(@NonNull ColorStateList colors) {
        mentionColors = colors;
        colorize();
    }

    @NonNull
    @Override
    public ColorStateList getHyperlinkColors() {
        return hyperlinkColors;
    }

    @Override
    public void setHyperlinkColors(@NonNull ColorStateList colors) {
        hyperlinkColors = colors;
        colorize();
    }

    @Override
    public int getHashtagColor() {
        return getHashtagColors().getDefaultColor();
    }

    @Override
    public void setHashtagColor(int color) {
        setHashtagColors(ColorStateList.valueOf(color));
    }

    @Override
    public int getMentionColor() {
        return getMentionColors().getDefaultColor();
    }

    @Override
    public void setMentionColor(int color) {
        setMentionColors(ColorStateList.valueOf(color));
    }

    @Override
    public int getHyperlinkColor() {
        return getHyperlinkColors().getDefaultColor();
    }

    @Override
    public void setHyperlinkColor(int color) {
        setHyperlinkColors(ColorStateList.valueOf(color));
    }

    @Override
    public void setOnHashtagClickListener(@Nullable OnClickListener listener) {
        ensureMovementMethod();
        hashtagClickListener = listener;
        colorize();
    }

    @Override
    public void setOnMentionClickListener(@Nullable OnClickListener listener) {
        ensureMovementMethod();
        mentionClickListener = listener;
        colorize();
    }

    @Override
    public void setOnHyperlinkClickListener(@Nullable OnClickListener listener) {
        ensureMovementMethod();
        hyperlinkClickListener = listener;
        colorize();
    }

    @Override
    public void setHashtagTextChangedListener(@Nullable OnChangedListener listener) {
        hashtagChangedListener = listener;
    }

    @Override
    public void setMentionTextChangedListener(@Nullable OnChangedListener listener) {
        mentionChangedListener = listener;
    }

    @NonNull
    @Override
    public List<String> getHashtags() {
        return extract(hashtagPattern);
    }

    @NonNull
    @Override
    public List<String> getMentions() {
        return extract(mentionPattern);
    }

    @NonNull
    @Override
    public List<String> getHyperlinks() {
        return extract(hyperlinkPattern);
    }

    private void colorize() {
        final CharSequence text = view.getText();
        if (!(text instanceof Spannable)) {
            throw new IllegalStateException("Attached text is not a Spannable," +
                "add TextView.BufferType.SPANNABLE when setting text to this TextView.");
        }
        final Spannable spannable = (Spannable) text;
        for (final Object span : spannable.getSpans(0, text.length(), CharacterStyle.class)) {
            spannable.removeSpan(span);
        }
        if (isHashtagEnabled()) {
            span(hashtagPattern, spannable, new Callable<Object>() {
                    @Override
                    public Object call() {
                        return hashtagClickListener != null
                            ? new SocialSpan(hashtagClickListener, hashtagColors)
                            : new ForegroundColorSpan(hashtagColors.getDefaultColor());
                    }
                }
            );
        }
        if (isMentionEnabled()) {
            span(mentionPattern, spannable, new Callable<Object>() {
                    @Override
                    public Object call() {
                        return mentionClickListener != null
                            ? new SocialSpan(mentionClickListener, mentionColors)
                            : new ForegroundColorSpan(mentionColors.getDefaultColor());
                    }
                }
            );
        }
        if (isHyperlinkEnabled()) {
            span(hyperlinkPattern, spannable, new Callable<Object>() {
                    @Override
                    public Object call() {
                        return hyperlinkClickListener != null
                            ? new SocialSpan(hyperlinkClickListener, hyperlinkColors)
                            : new URLSpan(text.toString()) {
                            @Override
                            public void updateDrawState(@NonNull TextPaint ds) {
                                ds.setColor(hyperlinkColors.getDefaultColor());
                                ds.setUnderlineText(true);
                            }
                        };
                    }
                }
            );
        }
    }

    private void ensureMovementMethod() {
        final MovementMethod method = view.getMovementMethod();
        if (!(method instanceof LinkMovementMethod)) {
            view.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private List<String> extract(Pattern pattern) {
        final List<String> list = new ArrayList<>();
        final Matcher matcher = pattern.matcher(view.getText());
        while (matcher.find()) {
            list.add(matcher.group(pattern != hyperlinkPattern
                ? 1 // remove hashtag and mention symbol
                : 0));
        }
        return list;
    }

    private static int indexOfNextNonLetterDigit(CharSequence text, int start) {
        for (int i = start + 1; i < text.length(); i++) {
            if (!Character.isLetterOrDigit(text.charAt(i))) {
                return i;
            }
        }
        return text.length();
    }

    private static int indexOfPreviousNonLetterDigit(CharSequence text, int start, int end) {
        for (int i = end; i > start; i--) {
            if (!Character.isLetterOrDigit(text.charAt(i))) {
                return i;
            }
        }
        return start;
    }

    private static void span(Pattern pattern, Spannable spannable, Callable<Object> spanCallable) {
        final Matcher matcher = pattern.matcher(spannable);
        while (matcher.find()) {
            final int start = matcher.start();
            final int end = matcher.end();
            final Object span;
            try {
                span = spanCallable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (span instanceof SocialSpan) {
                ((SocialSpan) span).text = spannable.subSequence(start, end);
            }
        }
    }

    private class SocialSpan extends ClickableSpan {
        private final OnClickListener listener;
        private final ColorStateList colors;
        private CharSequence text;

        SocialSpan(OnClickListener listener, ColorStateList colors) {
            this.listener = listener;
            this.colors = colors;
        }

        @Override
        public void onClick(@NonNull View widget) {
            listener.onClick(SocialViewHelper.this, listener != hyperlinkClickListener
                ? text.subSequence(1, text.length())
                : text
            );
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setColor(colors.getDefaultColor());
            ds.setUnderlineText(listener == hyperlinkClickListener);
        }
    }
}