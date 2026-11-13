package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

/** Built-in PDF reader: pages render lazily off the UI thread, pinch or double-tap to zoom. */
public final class NebulaPdfViewerFragment extends BaseFragment {
    private static final int SHARE = 1, EXTERNAL = 2;
    private final File file;
    private final String title;
    private final boolean restricted;
    // PdfRenderer allows one open page at a time, so every render runs on this thread.
    private final ExecutorService renderer = Executors.newSingleThreadExecutor();
    private ParcelFileDescriptor descriptor;
    private PdfRenderer pdf;
    private int[] widths = new int[0], heights = new int[0];
    private LruCache<String, Bitmap> cache;
    private RecyclerView list;
    private LinearLayoutManager layout;
    private TextView counter;
    private ZoomFrame zoom;
    private int quality = 1, generation;
    private volatile boolean closed;

    public NebulaPdfViewerFragment(File file, String title, boolean restricted) {
        this.file = file; this.title = title; this.restricted = restricted;
    }

    private static String t(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(title);
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setSubtitleColor(theme.onSurfaceVariant());
        actionBar.setItemsColor(theme.onSurface(), false);
        if (!restricted) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(SHARE, R.drawable.msg_share).setContentDescription(t("Поделиться", "Share"));
            menu.addItem(EXTERNAL, R.drawable.msg_openin).setContentDescription(t("Открыть в другом приложении", "Open in another app"));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == SHARE) NebulaPdfViewer.share(getParentActivity(), file, title);
                else if (id == EXTERNAL) NebulaPdfViewer.openExternally(getParentActivity(), file);
            }
        });

        int memory = (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 6);
        cache = new LruCache<String, Bitmap>(memory) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
        };

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(theme.surfaceContainer());
        list = new RecyclerView(context);
        list.setLayoutManager(layout = new LinearLayoutManager(context));
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(72));
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) { updateCounter(); }
        });
        zoom = new ZoomFrame(context);
        zoom.addView(list, new FrameLayout.LayoutParams(-1, -1));
        root.addView(zoom, new FrameLayout.LayoutParams(-1, -1));

        counter = new TextView(context);
        counter.setTextColor(theme.onSurface());
        counter.setTextSize(14);
        counter.setTypeface(AndroidUtilities.bold());
        counter.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(theme.surface());
        pill.setCornerRadius(AndroidUtilities.dp(18));
        counter.setBackground(pill);
        counter.setElevation(AndroidUtilities.dp(3));
        counter.setVisibility(View.INVISIBLE);
        root.addView(counter, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));

        if (restricted && getParentActivity() != null) getParentActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        load();
        return fragmentView = root;
    }

    private void load() {
        renderer.execute(() -> {
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                pdf = new PdfRenderer(descriptor);
                int count = pdf.getPageCount();
                int[] w = new int[count], h = new int[count];
                for (int i = 0; i < count && !closed; i++) {
                    try (PdfRenderer.Page page = pdf.openPage(i)) { w[i] = page.getWidth(); h[i] = page.getHeight(); }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (closed) return;
                    widths = w; heights = h;
                    actionBar.setSubtitle(pages(count));
                    list.setAdapter(new Pages());
                    counter.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
                    updateCounter();
                });
            } catch (Exception error) {
                // Password-protected or damaged files go to the other app instead.
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> {
                    if (closed) return;
                    Toast.makeText(getParentActivity(), t("Не удалось открыть PDF здесь", "This PDF cannot be opened here"), Toast.LENGTH_SHORT).show();
                    if (!restricted) NebulaPdfViewer.openExternally(getParentActivity(), file);
                    finishFragment();
                });
            }
        });
    }

    private static String pages(int count) {
        if (NebulaText.text("ru", "en").equals("en")) return count + (count == 1 ? " page" : " pages");
        int mod10 = count % 10, mod100 = count % 100;
        String word = mod10 == 1 && mod100 != 11 ? "страница" : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "страницы" : "страниц";
        return count + " " + word;
    }

    private void updateCounter() {
        if (layout == null || widths.length == 0) return;
        int first = layout.findFirstVisibleItemPosition(), last = layout.findLastVisibleItemPosition();
        if (first < 0) return;
        // The page under the middle of the screen is the one being read.
        int page = first;
        int middle = list.getHeight() / 2;
        for (int i = first; i <= last; i++) {
            View view = layout.findViewByPosition(i);
            if (view != null && view.getTop() <= middle && view.getBottom() >= middle) { page = i; break; }
        }
        counter.setText((page + 1) + " / " + widths.length);
    }

    private void render(int index, int width, ImageView target) {
        String key = index + ":" + width;
        Bitmap cached = cache.get(key);
        if (cached != null) { target.setImageBitmap(cached); return; }
        target.setImageBitmap(null);
        int stamp = generation;
        renderer.execute(() -> {
            if (closed || pdf == null || stamp != generation) return;
            Bitmap bitmap = null;
            try (PdfRenderer.Page page = pdf.openPage(index)) {
                int height = Math.max(1, (int) ((long) width * page.getHeight() / Math.max(1, page.getWidth())));
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            } catch (Throwable error) {
                FileLog.e(error);
            }
            Bitmap result = bitmap;
            AndroidUtilities.runOnUIThread(() -> {
                if (result == null || closed) return;
                cache.put(key, result);
                if (key.equals(target.getTag())) target.setImageBitmap(result);
            });
        });
    }

    private void setQuality(int value) {
        if (value == quality || list == null || list.getAdapter() == null) return;
        quality = value; generation++;
        list.getAdapter().notifyDataSetChanged();
    }

    private final class Pages extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            ImageView page = new ImageView(parent.getContext());
            page.setScaleType(ImageView.ScaleType.FIT_XY);
            page.setBackgroundColor(Color.WHITE);
            page.setElevation(AndroidUtilities.dp(1));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
            page.setLayoutParams(params);
            return new RecyclerView.ViewHolder(page) { };
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ImageView page = (ImageView) holder.itemView;
            int width = Math.max(1, list.getWidth() - AndroidUtilities.dp(16));
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) page.getLayoutParams();
            params.height = (int) ((long) width * heights[position] / Math.max(1, widths[position]));
            page.setLayoutParams(params);
            page.setContentDescription(t("Страница ", "Page ") + (position + 1));
            int pixels = Math.min(width * quality, 4096);
            page.setTag(position + ":" + pixels);
            render(position, pixels, page);
        }
        @Override public int getItemCount() { return widths.length; }
    }

    /** Scales the page list around the gesture; the list keeps scrolling vertically while zoomed. */
    private final class ZoomFrame extends FrameLayout {
        private final ScaleGestureDetector scale;
        private final GestureDetector gestures;
        private float factor = 1f;
        private boolean scaling;

        ZoomFrame(Context context) {
            super(context);
            scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScaleBegin(ScaleGestureDetector detector) { scaling = true; return true; }
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    float next = Math.max(1f, Math.min(4f, factor * detector.getScaleFactor()));
                    // Keep the point under the fingers in place.
                    float ratio = next / factor;
                    float fx = detector.getFocusX() - getWidth() / 2f, fy = detector.getFocusY() - getHeight() / 2f;
                    list.setTranslationX((list.getTranslationX() - fx) * ratio + fx);
                    list.setTranslationY((list.getTranslationY() - fy) * ratio + fy);
                    apply(next);
                    return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector detector) { scaling = false; setQuality(factor >= 1.5f ? 2 : 1); }
            });
            gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDoubleTap(MotionEvent e) {
                    float target = factor > 1.05f ? 1f : 2.5f;
                    float fx = e.getX() - getWidth() / 2f, fy = e.getY() - getHeight() / 2f;
                    float ratio = target / factor;
                    float tx = target == 1f ? 0 : (list.getTranslationX() - fx) * ratio + fx;
                    float ty = target == 1f ? 0 : (list.getTranslationY() - fy) * ratio + fy;
                    android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0, 1);
                    float from = factor, fromX = list.getTranslationX(), fromY = list.getTranslationY();
                    animator.addUpdateListener(a -> {
                        float p = (float) a.getAnimatedValue();
                        list.setTranslationX(fromX + (tx - fromX) * p);
                        list.setTranslationY(fromY + (ty - fromY) * p);
                        apply(from + (target - from) * p);
                    });
                    animator.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(android.animation.Animator animation) { setQuality(factor >= 1.5f ? 2 : 1); }
                    });
                    animator.setDuration(220).start();
                    return true;
                }
                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    if (factor <= 1f || scaling) return false;
                    list.setTranslationX(list.getTranslationX() - dx);
                    apply(factor);
                    return false;
                }
            });
        }
        private void apply(float value) {
            factor = value;
            list.setScaleX(value); list.setScaleY(value);
            float maxX = (value - 1) * getWidth() / 2f, maxY = (value - 1) * getHeight() / 2f;
            list.setTranslationX(Math.max(-maxX, Math.min(maxX, list.getTranslationX())));
            list.setTranslationY(Math.max(-maxY, Math.min(maxY, list.getTranslationY())));
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            scale.onTouchEvent(event); gestures.onTouchEvent(event);
            return scaling || event.getPointerCount() > 1;
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            scale.onTouchEvent(event); gestures.onTouchEvent(event);
            return true;
        }
    }

    @Override public void onFragmentDestroy() {
        super.onFragmentDestroy();
        closed = true; generation++;
        if (restricted && getParentActivity() != null) getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        renderer.execute(() -> {
            try { if (pdf != null) pdf.close(); } catch (Exception ignore) { }
            try { if (descriptor != null) descriptor.close(); } catch (Exception ignore) { }
        });
        renderer.shutdown();
        if (cache != null) cache.evictAll();
    }
}
