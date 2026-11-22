package com.example.minimalnav;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.ViewHolder> {

    /** 按下时轻微下压，松手回弹 */
    private static final float PRESSED_SCALE = 0.975f;
    /** 聚焦态放大倍数 */
    private static final float FOCUS_SCALE = 1.02f;
    /** 非聚焦行淡出透明度 */
    private static final float DIMMED_ALPHA = 0.38f;

    /** 所有行共用一个插值器实例，避免每次触摸都 new 一个对象。 */
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator();

    public interface OnAddressClickListener {
        void onClick(Address address);
    }

    public interface OnAddressLongClickListener {
        void onLongClick(Address address);
    }

    private final List<Address> list;
    private final OnAddressClickListener clickListener;
    private final OnAddressLongClickListener longClickListener;

    /** 当前操作对象的位置，-1 表示无聚焦 */
    private int focusPosition = -1;

    /**
     * 卡片底图只解析一次并复用。两个 drawable 都是无状态静态资源，
     * 多行共享安全；原来每行每绑一次 setBackgroundResource 都会重新实例化 GradientDrawable。
     */
    private Drawable bgNormal;
    private Drawable bgFocused;

    public AddressAdapter(List<Address> list,
                          OnAddressClickListener clickListener,
                          OnAddressLongClickListener longClickListener) {
        this.list = list;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    /**
     * 设置聚焦项：浮层打开期间该行放大并高亮描边，其余行淡出。
     * 传 -1 清除聚焦。
     */
    public void setFocusPosition(int position) {
        if (focusPosition == position) {
            return;
        }
        int previous = focusPosition;
        focusPosition = position;
        if (previous >= 0 && previous < list.size()) {
            notifyItemChanged(previous);
        }
        if (position >= 0 && position < list.size()) {
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);
        ViewHolder holder = new ViewHolder(view);
        bindListeners(holder);
        return holder;
    }

    /**
     * 监听器在「创建 Holder」时绑一次，而不是每次 onBindViewHolder 都新建 3 个 lambda。
     * 内部用 getBindingAdapterPosition() 取当前位置，复用时也不会指向旧数据。
     */
    private void bindListeners(final ViewHolder holder) {
        final View item = holder.itemView;

        // 按下反馈：轻微下压，松手回弹到当前状态（聚焦 1.02，否则 1.0）
        item.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                            .setDuration(120)
                            .setInterpolator(EASE)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float target = holder.getBindingAdapterPosition() == focusPosition
                            ? FOCUS_SCALE : 1f;
                    v.animate().cancel();
                    v.animate().scaleX(target).scaleY(target)
                            .setDuration(220)
                            .setInterpolator(EASE)
                            .start();
                    break;
                default:
                    break;
            }
            return false;
        });

        item.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && position < list.size()) {
                clickListener.onClick(list.get(position));
            }
        });

        item.setOnLongClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || position >= list.size()) {
                return false;
            }
            longClickListener.onLongClick(list.get(position));
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Address address = list.get(position);
        final Context context = holder.itemView.getContext();

        // 用 setImageResource + setColorFilter 代替 getDrawable + wrap().mutate() + setTint：
        // 前者走 Resources 的 ConstantState 缓存，着色在绘制期完成，不产生新 Drawable 对象。
        holder.ivIcon.setImageResource(Palette.ICONS[address.getIconIndex()]);
        holder.ivIcon.setColorFilter(Palette.COLORS[address.getColorIndex()], PorterDuff.Mode.SRC_IN);

        holder.tvName.setText(address.getName());

        final View item = holder.itemView;
        final boolean focused = position == focusPosition;
        final boolean dimmed = focusPosition >= 0 && !focused;

        // 底图只在状态真的切换时才设置：setBackground 会触发 requestLayout，
        // 每次绑定都调用等于让列表在滚动/刷新时反复请求布局。
        if (holder.backgroundFocused != focused) {
            holder.backgroundFocused = focused;
            item.setBackground(focused ? focusedBackground(context) : normalBackground(context));
        }

        item.setAlpha(dimmed ? DIMMED_ALPHA : 1f);
        item.animate().cancel();
        item.setScaleX(1f);
        item.setScaleY(1f);
        if (focused) {
            item.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE)
                    .setDuration(220)
                    .setInterpolator(EASE)
                    .start();
        }
    }

    private Drawable normalBackground(Context context) {
        if (bgNormal == null) {
            bgNormal = ContextCompat.getDrawable(context, R.drawable.bg_glass_card);
        }
        return bgNormal;
    }

    private Drawable focusedBackground(Context context) {
        if (bgFocused == null) {
            bgFocused = ContextCompat.getDrawable(context, R.drawable.bg_glass_card_focused);
        }
        return bgFocused;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;
        /** 当前底图是否为聚焦态，避免重复 setBackground 触发无谓的 requestLayout。 */
        boolean backgroundFocused;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
        }
    }
}
