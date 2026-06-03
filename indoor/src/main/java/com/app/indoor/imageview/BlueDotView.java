package com.app.indoor.imageview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.app.indoor.R;
import com.app.indoor.imageview.SmoothEstimate;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;


public class BlueDotView extends SubsamplingScaleImageView {

    private float uncertaintyRadius = 1.0f;
    private float dotRadius = 1.0f;

    private PointF dotCenter = null;

    private double heading = -1.0;

    private SmoothEstimate smoothEstimate = new SmoothEstimate();

    private Paint paint = new Paint();

    // =========================
    // Route Variables
    // =========================
    private OnPoiClickListener poiClickListener;
    private List<PointF> routePoints = new ArrayList<>();

    private Paint routePaint = new Paint();

    // =========================
    // POI Variables
    // =========================

    private List<POIMarker> poiMarkers = new ArrayList<>();

    private Paint poiTextPaint = new Paint();

    private Bitmap markerBitmap;

    // =========================
    // POI Model
    // =========================

    public static class POIMarker {

        public String name;

        public PointF point;

        public LatLng latLng;

        public POIMarker(
                String name,
                PointF point,
                LatLng latLng
        ) {
            this.name = name;
            this.point = point;
            this.latLng = latLng;
        }
    }
    public void setOnPoiClickListener(
            OnPoiClickListener listener
    ) {
        this.poiClickListener = listener;
    }

    // =========================
    // Setters
    // =========================

    public void setUncertaintyRadius(float uncertaintyRadius) {
        this.uncertaintyRadius = uncertaintyRadius;
    }

    public void setDotRadius(float dotRadius) {
        this.dotRadius = dotRadius;
    }

    public void setDotCenter(PointF dotCenter) {
        this.dotCenter = dotCenter;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }

    public void setRoutePoints(List<PointF> points) {
        this.routePoints = points;
    }

    public void setPoiMarkers(List<POIMarker> markers) {
        this.poiMarkers = markers;
    }

    // =========================
    // Constructors
    // =========================

    public BlueDotView(Context context) {
        this(context, null);
    }

    public BlueDotView(Context context, AttributeSet attr) {
        super(context, attr);
        initialise();
    }

    // =========================
    // Initialize
    // =========================

    private void initialise() {

        Log.d("BluedotView", "Initialize");

        setWillNotDraw(false);

        setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);

        // =========================
        // Blue Dot Paint
        // =========================

        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(getResources().getColor(R.color.ia_blue));

        // =========================
        // Route Paint
        // =========================

        routePaint.setAntiAlias(true);
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(8f);
        routePaint.setColor(Color.BLUE);

        // =========================
        // POI Text Paint
        // =========================

        poiTextPaint.setAntiAlias(true);
        poiTextPaint.setColor(Color.BLACK);
        poiTextPaint.setTextSize(32f);

        // =========================
        // Marker Bitmap
        // =========================

        markerBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.ic_marker
        );
    }

    // =========================
    // Draw
    // =========================

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        if (!isReady()) {
            return;
        }

        // =========================
        // Draw Route
        // =========================

        if (routePoints != null && routePoints.size() > 1) {

            for (int i = 0; i < routePoints.size() - 1; i += 2) {

                if (i + 1 >= routePoints.size()) {
                    break;
                }

                PointF start = sourceToViewCoord(routePoints.get(i));

                PointF end = sourceToViewCoord(routePoints.get(i + 1));

                if (start == null || end == null) {
                    continue;
                }

                canvas.drawLine(
                        start.x,
                        start.y,
                        end.x,
                        end.y,
                        routePaint
                );
            }
        }

        // =========================
        // Draw POI Markers
        // =========================

        if (poiMarkers != null) {

            for (POIMarker marker : poiMarkers) {

                PointF vPoint = sourceToViewCoord(marker.point);

                if (vPoint == null) {
                    continue;
                }

                // Draw Bitmap Marker

                canvas.drawBitmap(
                        markerBitmap,
                        vPoint.x - (markerBitmap.getWidth() / 2f),
                        vPoint.y - markerBitmap.getHeight(),
                        null
                );

                // Draw Text

                canvas.drawText(
                        marker.name,
                        vPoint.x + 20,
                        vPoint.y,
                        poiTextPaint
                );
            }
        }

        // =========================
        // Draw Blue Dot
        // =========================

        if (dotCenter != null) {

            // Update smooth estimate

            smoothEstimate.update(
                    dotCenter.x,
                    dotCenter.y,
                    (float) ((heading) / 180.0 * Math.PI),
                    uncertaintyRadius,
                    System.currentTimeMillis()
            );

            PointF vPoint = sourceToViewCoord(
                    smoothEstimate.getX(),
                    smoothEstimate.getY()
            );

            if (vPoint != null) {

                // =========================
                // Uncertainty Circle
                // =========================

                float scaledUncertaintyRadius =
                        getScale() * smoothEstimate.getRadius();

                paint.setAlpha(30);

                canvas.drawCircle(
                        vPoint.x,
                        vPoint.y,
                        scaledUncertaintyRadius,
                        paint
                );

                // =========================
                // Dot Circle
                // =========================

                float scaledDotRadius =
                        getScale() * dotRadius;

                paint.setAlpha(90);

                canvas.drawCircle(
                        vPoint.x,
                        vPoint.y,
                        scaledDotRadius,
                        paint
                );

                // =========================
                // Heading Triangle
                // =========================

                if (heading != -1.0) {

                    paint.setAlpha(255);

                    Path triangle = headingTriangle(
                            vPoint.x,
                            vPoint.y,
                            smoothEstimate.getHeading() - (float) Math.PI / 2,
                            scaledDotRadius
                    );

                    canvas.drawPath(triangle, paint);
                }
            }
        }

        postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_UP) {

            if (poiMarkers != null) {

                for (POIMarker marker : poiMarkers) {

                    PointF viewPoint =
                            sourceToViewCoord(marker.point);

                    if (viewPoint == null) {
                        continue;
                    }

                    float dx =
                            event.getX() - viewPoint.x;

                    float dy =
                            event.getY() - viewPoint.y;

                    float distance =
                            (float) Math.sqrt(dx * dx + dy * dy);

                    // Click radius

                    if (distance < 80f) {

                        if (poiClickListener != null) {

                            poiClickListener.onPoiClick(marker);
                        }

                        return true;
                    }
                }
            }
        }

        return super.onTouchEvent(event);
    }

    // =========================
    // Heading Triangle
    // =========================

    private static Path headingTriangle(
            float x,
            float y,
            float a,
            float r
    ) {

        float x1 = (float) (x + 0.9 * r * Math.cos(a));

        float y1 = (float) (y + 0.9 * r * Math.sin(a));

        float x2 = (float) (x + 0.2 * r * Math.cos(a + 0.5 * Math.PI));

        float y2 = (float) (y + 0.2 * r * Math.sin(a + 0.5 * Math.PI));

        float x3 = (float) (x + 0.2 * r * Math.cos(a - 0.5 * Math.PI));

        float y3 = (float) (y + 0.2 * r * Math.sin(a - 0.5 * Math.PI));

        Path triangle = new Path();

        triangle.moveTo(x1, y1);

        triangle.lineTo(x2, y2);

        triangle.lineTo(x3, y3);

        triangle.lineTo(x1, y1);

        return triangle;
    }



    public interface OnPoiClickListener {
        void onPoiClick(POIMarker marker);
    }
}
