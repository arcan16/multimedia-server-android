package com.multimediaserver.android.ui.common;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;

import androidx.annotation.NonNull;

import com.multimediaserver.android.R;

import java.util.List;

/** Exposed-dropdown helpers (the web's &lt;select&gt;). The adapter never filters its options. */
public final class Dropdown {
    private Dropdown() {
    }

    public static void setOptions(AutoCompleteTextView view, List<String> labels) {
        view.setAdapter(new NoFilterAdapter(view.getContext(), labels));
    }

    private static class NoFilterAdapter extends ArrayAdapter<String> {
        private final List<String> items;

        NoFilterAdapter(Context context, List<String> items) {
            super(context, R.layout.item_dropdown, items);
            this.items = items;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = items;
                    results.count = items.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }
}
