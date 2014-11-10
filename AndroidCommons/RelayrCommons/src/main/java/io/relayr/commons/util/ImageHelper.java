package io.relayr.commons.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

public abstract class ImageHelper {

    private static final RectF rectF = new RectF(0, 0 , 0, 0);
    private static final int color = 0xff424242;
    private static final PorterDuffXfermode porter = new PorterDuffXfermode(Mode.DST_IN);

    public static Bitmap roundCorners(Bitmap source, Context context) {
        if (source == null) return source;

        int width = source.getWidth();
        int height = source.getHeight();

        if (width <= 0 || height <= 0)
            return null;

        float density = context.getResources().getDisplayMetrics().density;
        float cornerRadius = width / 2 * density;

        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(color);

        Bitmap clipped = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(clipped);
        rectF.right = width;
        rectF.bottom = height;
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
        paint.setXfermode(porter);

        Bitmap rounded = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        canvas = new Canvas(rounded);
        canvas.drawBitmap(source, 0, 0, null);
        canvas.drawBitmap(clipped, 0, 0, paint);

        source.recycle();
        clipped.recycle();
        System.gc();

        return rounded;
    }
}

