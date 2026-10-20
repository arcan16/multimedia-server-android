package com.multimediaserver.android.ui.catalog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.multimediaserver.android.databinding.ItemTitleCardBinding;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.util.Format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Poster grid of the catalog (TitleCard.jsx). */
public class TitleCardAdapter extends RecyclerView.Adapter<TitleCardAdapter.Holder> {

    public interface OnTitleClick {
        void onClick(MovieSeries title);
    }

    private final List<MovieSeries> items = new ArrayList<>();
    private Map<Long, String> genresById = new HashMap<>();
    private final OnTitleClick listener;

    public TitleCardAdapter(OnTitleClick listener) {
        this.listener = listener;
    }

    public void setItems(List<MovieSeries> titles) {
        items.clear();
        items.addAll(titles);
        notifyDataSetChanged();
    }

    public void setGenres(Map<Long, String> byId) {
        genresById = byId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemTitleCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MovieSeries title = items.get(position);
        holder.b.poster.bind(title);
        holder.b.name.setText(title.name);
        String genres = Format.genreNames(title.genreIds, genresById);
        String year = title.releaseYear != null ? String.valueOf(title.releaseYear) : "";
        holder.b.meta.setText(genres.isEmpty() ? year : year + " · " + genres);
        holder.b.getRoot().setContentDescription(title.name + ", " + Format.typeLabel(title.type).toLowerCase() + ", " + year);
        holder.b.getRoot().setOnClickListener(v -> listener.onClick(title));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemTitleCardBinding b;

        Holder(ItemTitleCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
