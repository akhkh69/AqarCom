package com.aqarcom.app.ui.common;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Calls loadMore() when the list is scrolled near its end (server-side pagination). */
public abstract class EndlessScroll extends RecyclerView.OnScrollListener {
    private boolean loading;
    private boolean hasMore = true;

    public abstract void loadMore();

    public void setLoading(boolean l) { loading = l; }

    public void setHasMore(boolean h) { hasMore = h; }

    public void reset() { loading = false; hasMore = true; }

    @Override
    public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
        if (dy <= 0 || loading || !hasMore) return;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        if (llm.findLastVisibleItemPosition() >= llm.getItemCount() - 4) {
            loading = true;
            loadMore();
        }
    }
}
