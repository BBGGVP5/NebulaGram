package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.ArrayList;
import java.util.List;

import static app.nebulagram.ui.NebulaText.text;

/**
 * Лента: последние посты выбранных каналов, новые сверху, с вкладками
 * категорий над списком. Посты берутся обычным {@code messages.getHistory}
 * каждого канала — по несколько последних, — и сливаются по времени.
 */
public final class NebulaFeedFragment extends BaseFragment {
    private static final int PER_CHANNEL = 12, MAX_CHANNELS = 40, MAX_POSTS = 200, SETTINGS = 1;

    private static final class Post implements NebulaFeedModel.Post {
        /** Первое сообщение поста: его открываем и по нему сортируем. */
        MessageObject message;
        /** Сообщение с подписью; у альбома подпись бывает у любого элемента. */
        MessageObject captioned;
        /** Чьё превью показываем: первый элемент с картинкой. */
        MessageObject cover;
        int count = 1;
        final long channel;
        Post(MessageObject message, long channel) { this.message = message; this.channel = channel; }
        @Override public long channel() { return channel; }
        @Override public int id() { return message.getId(); }
        @Override public int date() { return message.messageOwner.date; }
    }

    /**
     * Альбом приходит отдельными сообщениями с общим {@code grouped_id}. Раньше
     * каждое становилось своим постом с голой подписью «Альбом»; теперь альбом —
     * один пост с превью первого снимка и подписью, где бы она ни стояла.
     */
    private static List<Post> albums(List<Post> collected) {
        ArrayList<Post> result = new ArrayList<>();
        java.util.HashMap<String, Post> groups = new java.util.HashMap<>();
        for (Post item : collected) {
            Post post = new Post(item.message, item.channel);
            long group = item.message.messageOwner.grouped_id;
            if (group == 0) {
                post.captioned = item.message;
                post.cover = hasPreview(item.message) ? item.message : null;
                result.add(post);
                continue;
            }
            String key = item.channel + ":" + group;
            Post head = groups.get(key);
            if (head == null) {
                head = post;
                head.count = 0;
                groups.put(key, head);
                result.add(head);
            }
            head.count++;
            if (item.message.getId() < head.message.getId()) head.message = item.message;
            if (!TextUtils.isEmpty(item.message.caption)
                    && (head.captioned == null || item.message.getId() < head.captioned.getId())) head.captioned = item.message;
            if (hasPreview(item.message) && (head.cover == null || item.message.getId() < head.cover.getId())) head.cover = item.message;
        }
        for (Post post : result) if (post.captioned == null) post.captioned = post.message;
        return result;
    }

    private static TLRPC.PhotoSize previewSize(MessageObject message) {
        if (message == null || message.photoThumbs == null || message.photoThumbs.isEmpty()) return null;
        return FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
    }

    private static boolean hasPreview(MessageObject message) {
        return message != null && (message.isPhoto() || message.isVideo() || message.isGif()) && previewSize(message) != null;
    }

    private NebulaFeedModel model;
    private long selectedCategory;
    private LinearLayout chips;
    private TextView empty;
    private RecyclerView list;
    private final ArrayList<Post> posts = new ArrayList<>();
    private final ArrayList<Integer> requests = new ArrayList<>();
    private int generation;
    private boolean destroyed;
    private NebulaTheme theme;

    public NebulaFeedFragment(int account) {
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        theme = NebulaTheme.of(context);
        NebulaFormUi.bar(this, actionBar, context, text("Лента", "Feed"));
        actionBar.createMenu().addItem(SETTINGS, R.drawable.msg_settings);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == SETTINGS) presentFragment(new NebulaFeedSettingsFragment(currentAccount));
            }
        });

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(theme.surface());

        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        chipsScroll.addView(chips);
        body.addView(chipsScroll, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout frame = new FrameLayout(context);
        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        list.setAdapter(new Adapter());
        frame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = NebulaFormUi.note(context, "");
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(32), AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        empty.setOnClickListener(v -> {
            if (model != null && model.channels.isEmpty()) presentFragment(new NebulaFeedSettingsFragment(currentAccount));
        });
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        body.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, body);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Настройки ленты могли поменяться, пока экран был закрыт.
        model = NebulaFeedStore.load(currentAccount);
        if (selectedCategory != 0) {
            NebulaFeedModel.Category category = model.find(selectedCategory);
            if (category == null || category.hidden) selectedCategory = 0;
        }
        actionBar.setSubtitle(NebulaFeedStore.channelsCount(model.channels.size()));
        // Подпись создаётся позже заголовка и остаётся с левым выравниванием:
        // «2 канала» стояло под «назад», а не под центрированной «Лентой».
        if (actionBar.getSubtitleTextView() != null) actionBar.getSubtitleTextView().setGravity(Gravity.CENTER);
        buildChips();
        reload();
    }

    private void buildChips() {
        Context context = chips.getContext();
        chips.removeAllViews();
        chips.addView(chip(context, text("Все", "All"), 0));
        for (NebulaFeedModel.Category category : model.tabs()) {
            String title = TextUtils.isEmpty(category.emoji) ? category.name : category.emoji + " " + category.name;
            chips.addView(chip(context, title, category.id));
        }
        ((View) chips.getParent()).setVisibility(model.tabs().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private TextView chip(Context context, String title, long id) {
        boolean selected = id == selectedCategory;
        TextView view = new TextView(context);
        view.setText(title);
        view.setSingleLine();
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTypeface(AndroidUtilities.bold());
        view.setGravity(Gravity.CENTER);
        view.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        view.setTextColor(selected ? theme.onPrimaryContainer() : theme.onSurfaceVariant());
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(AndroidUtilities.dp(16));
        if (selected) background.setColor(theme.primaryContainer());
        else background.setStroke(AndroidUtilities.dp(1), theme.outline());
        view.setBackground(background);
        view.setOnClickListener(v -> {
            if (selectedCategory == id) {
                list.smoothScrollToPosition(0);
                return;
            }
            selectedCategory = id;
            buildChips();
            reload();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, AndroidUtilities.dp(32));
        params.rightMargin = AndroidUtilities.dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private void cancelRequests() {
        for (int request : requests) getConnectionsManager().cancelRequest(request, true);
        requests.clear();
    }

    private void reload() {
        cancelRequests();
        final int current = ++generation;
        posts.clear();
        list.getAdapter().notifyDataSetChanged();
        NebulaFeedModel.Category category = selectedCategory == 0 ? null : model.find(selectedCategory);
        List<Long> channels = model.channelsOf(category);
        if (channels.isEmpty()) {
            showEmpty(model.channels.isEmpty()
                    ? text("Каналы для ленты ещё не выбраны.\nНажмите, чтобы выбрать.", "No channels in the feed yet.\nTap to choose some.")
                    : text("В этой категории пока нет каналов.", "This category has no channels yet."));
            return;
        }
        showEmpty(text("Загрузка…", "Loading…"));
        final ArrayList<Post> collected = new ArrayList<>();
        final int[] pending = {0};
        for (int i = 0; i < channels.size() && i < MAX_CHANNELS; i++) {
            final long channel = channels.get(i);
            TLRPC.InputPeer peer = getMessagesController().getInputPeer(-channel);
            if (peer == null || peer instanceof TLRPC.TL_inputPeerEmpty) continue;
            TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
            request.peer = peer;
            request.limit = PER_CHANNEL;
            pending[0]++;
            requests.add(getConnectionsManager().sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (current != generation || destroyed) return;
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
                    getMessagesController().putUsers(result.users, false);
                    getMessagesController().putChats(result.chats, false);
                    for (TLRPC.Message message : result.messages) {
                        if (message instanceof TLRPC.TL_messageService || message instanceof TLRPC.TL_messageEmpty) continue;
                        collected.add(new Post(new MessageObject(currentAccount, message, false, true), channel));
                    }
                }
                pending[0]--;
                publish(collected, pending[0] == 0);
            })));
        }
        if (pending[0] == 0) showEmpty(text("Не удалось открыть выбранные каналы.", "Could not open the chosen channels."));
    }

    private void publish(List<Post> collected, boolean done) {
        posts.clear();
        posts.addAll(NebulaFeedModel.newestFirst(albums(collected), MAX_POSTS));
        list.getAdapter().notifyDataSetChanged();
        if (!posts.isEmpty()) empty.setVisibility(View.GONE);
        else if (done) showEmpty(text("Постов пока нет.", "No posts yet."));
    }

    private void showEmpty(String value) {
        empty.setText(value);
        empty.setVisibility(View.VISIBLE);
    }

    private void open(Post post) {
        Bundle args = new Bundle();
        args.putLong("chat_id", post.channel);
        args.putInt("message_id", post.id());
        presentFragment(new ChatActivity(args));
    }

    private static CharSequence body(MessageObject message, boolean previewShown) {
        if (!TextUtils.isEmpty(message.caption)) return message.caption;
        // Превью уже говорит, что это фото или видео: подпись «Видео» под ним лишняя.
        if (previewShown) return null;
        TLRPC.MessageMedia media = message.messageOwner.media;
        boolean textOnly = media == null || media instanceof TLRPC.TL_messageMediaEmpty || media instanceof TLRPC.TL_messageMediaWebPage;
        if (!textOnly && !TextUtils.isEmpty(message.messageOwner.message)) return message.messageOwner.message;
        return message.messageText;
    }

    private final class Cell extends LinearLayout {
        final BackupImageView avatar = new BackupImageView(getContext());
        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        final TextView name = new TextView(getContext()), time = new TextView(getContext()), bodyView = new TextView(getContext());
        final BackupImageView photo = new BackupImageView(getContext());
        final FrameLayout media = new FrameLayout(getContext());
        final TextView badge = new TextView(getContext());

        Cell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            int pad = AndroidUtilities.dp(14);
            setPadding(pad, pad, pad, pad);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(NebulaShape.radius(20));
            background.setColor(theme.surfaceContainer());
            setBackground(background);
            LinearLayout header = new LinearLayout(context);
            header.setGravity(Gravity.CENTER_VERTICAL);
            avatar.setRoundRadius(AndroidUtilities.dp(18));
            header.addView(avatar, new LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(36)));
            LinearLayout titles = new LinearLayout(context);
            titles.setOrientation(VERTICAL);
            titles.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(theme.onSurface());
            name.setSingleLine();
            name.setEllipsize(TextUtils.TruncateAt.END);
            time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            time.setTextColor(theme.onSurfaceVariant());
            titles.addView(name);
            titles.addView(time);
            header.addView(titles, new LayoutParams(0, -2, 1f));
            addView(header);
            photo.setRoundRadius(AndroidUtilities.dp(14));
            media.addView(photo, new FrameLayout.LayoutParams(-1, -1));
            // Видео и альбом помечены поверх превью, а не отдельной строкой текста.
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(0xFFFFFFFF);
            badge.setSingleLine();
            badge.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));
            GradientDrawable badgeBackground = new GradientDrawable();
            badgeBackground.setCornerRadius(AndroidUtilities.dp(11));
            badgeBackground.setColor(0x99000000);
            badge.setBackground(badgeBackground);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
            badgeParams.setMargins(0, 0, AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            media.addView(badge, badgeParams);
            LayoutParams photoParams = new LayoutParams(-1, AndroidUtilities.dp(200));
            photoParams.topMargin = AndroidUtilities.dp(10);
            addView(media, photoParams);
            bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            bodyView.setTextColor(theme.onSurface());
            bodyView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            bodyView.setMaxLines(12);
            bodyView.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams textParams = new LayoutParams(-1, -2);
            textParams.topMargin = AndroidUtilities.dp(10);
            addView(bodyView, textParams);
        }

        void bind(Post post) {
            TLRPC.Chat chat = getMessagesController().getChat(post.channel);
            avatarDrawable.setInfo(currentAccount, chat);
            avatar.setForUserOrChat(chat, avatarDrawable);
            name.setText(chat == null ? "" : chat.title);
            MessageObject message = post.message;
            String date = LocaleController.stringForMessageListDate(message.messageOwner.date);
            if (message.messageOwner.views > 0) date += "  ·  " + LocaleController.formatShortNumber(message.messageOwner.views, null) + text(" просм.", " views");
            time.setText(date);
            MessageObject cover = post.cover;
            TLRPC.PhotoSize size = previewSize(cover);
            boolean preview = cover != null && size != null;
            CharSequence value = body(post.captioned, preview);
            bodyView.setText(value);
            bodyView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
            if (preview) {
                media.setVisibility(VISIBLE);
                photo.getImageReceiver().setCrossfadeWithOldImage(false);
                photo.setImage(ImageLocation.getForObject(size, cover.photoThumbsObject), "400_200", (android.graphics.drawable.Drawable) null, cover);
                String mark = null;
                if (post.count > 1) mark = text(post.count + " медиа", post.count + " items");
                else if (cover.isVideo()) mark = "▶  " + AndroidUtilities.formatShortDuration((int) cover.getDuration());
                else if (cover.isGif()) mark = "GIF";
                badge.setText(mark);
                badge.setVisibility(mark == null ? GONE : VISIBLE);
            } else {
                media.setVisibility(GONE);
            }
            setOnClickListener(v -> open(post));
        }
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Cell cell = new Cell(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.leftMargin = params.rightMargin = AndroidUtilities.dp(12);
            params.topMargin = AndroidUtilities.dp(8);
            cell.setLayoutParams(params);
            return new RecyclerView.ViewHolder(cell) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((Cell) holder.itemView).bind(posts.get(position));
        }

        @Override
        public int getItemCount() { return posts.size(); }
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        cancelRequests();
        super.onFragmentDestroy();
    }
}
