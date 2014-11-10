package io.relayr.commons.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

import io.relayr.commons.util.Util;

public class CustomTextView extends TextView {

    public CustomTextView(Context context) {
        this(context, null);
    }

    public CustomTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public CustomTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (isInEditMode()) return;
        Typeface tf = Util.getFont(context, attrs);
        if (tf != null) setTypeface(tf);
    }

}
