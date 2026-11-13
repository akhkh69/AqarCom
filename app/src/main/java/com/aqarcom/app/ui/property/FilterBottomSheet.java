package com.aqarcom.app.ui.property;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aqarcom.app.R;
import com.aqarcom.app.databinding.SheetFiltersBinding;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

/** Advanced search filters (FR-08). The host implements {@link Host} to receive the result. */
public class FilterBottomSheet extends BottomSheetDialogFragment {

    public interface Host { void onFiltersApplied(PropertyFilters f); }

    private static final String ARG = "filters";
    private SheetFiltersBinding b;
    private PropertyFilters filters;

    public static FilterBottomSheet newInstance(PropertyFilters f) {
        FilterBottomSheet s = new FilterBottomSheet();
        Bundle a = new Bundle();
        a.putSerializable(ARG, f);
        s.setArguments(a);
        return s;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = SheetFiltersBinding.inflate(inflater, container, false);
        filters = (PropertyFilters) requireArguments().getSerializable(ARG);
        if (filters == null) filters = new PropertyFilters();

        Ui.dropdown(b.ddCity, Labels.CITIES);
        Ui.dropdown(b.ddType, Labels.PROPERTY_TYPES);
        Ui.dropdownSelect(b.ddCity, Labels.CITIES, filters.city);
        Ui.dropdownSelect(b.ddType, Labels.PROPERTY_TYPES, filters.propertyType);
        if (filters.minPrice != null) b.etMinPrice.setText(Fmt.number(filters.minPrice).replace(",", ""));
        if (filters.maxPrice != null) b.etMaxPrice.setText(Fmt.number(filters.maxPrice).replace(",", ""));
        if (filters.minArea != null) b.etMinArea.setText(Fmt.number(filters.minArea).replace(",", ""));
        if (filters.maxArea != null) b.etMaxArea.setText(Fmt.number(filters.maxArea).replace(",", ""));

        String[] rooms = {"1+", "2+", "3+", "4+", "5+"};
        for (int i = 0; i < rooms.length; i++) {
            Chip c = (Chip) inflater.inflate(R.layout.item_filter_chip, b.chipsRooms, false);
            c.setId(View.generateViewId());
            c.setText(rooms[i]);
            c.setTag(i + 1);
            c.setChecked(filters.minRooms != null && filters.minRooms == i + 1);
            b.chipsRooms.addView(c);
        }

        b.btnApply.setOnClickListener(v -> apply());
        b.btnReset.setOnClickListener(v -> {
            filters.clearAdvanced();
            deliver();
        });
        return b.getRoot();
    }

    private void apply() {
        filters.city = Ui.dropdownKey(b.ddCity, Labels.CITIES);
        filters.propertyType = Ui.dropdownKey(b.ddType, Labels.PROPERTY_TYPES);
        filters.minPrice = Ui.parseDouble(Ui.text(b.etMinPrice));
        filters.maxPrice = Ui.parseDouble(Ui.text(b.etMaxPrice));
        filters.minArea = Ui.parseDouble(Ui.text(b.etMinArea));
        filters.maxArea = Ui.parseDouble(Ui.text(b.etMaxArea));
        filters.minRooms = null;
        int checked = b.chipsRooms.getCheckedChipId();
        if (checked != View.NO_ID) {
            Chip c = b.chipsRooms.findViewById(checked);
            if (c != null) filters.minRooms = (Integer) c.getTag();
        }
        deliver();
    }

    private void deliver() {
        if (getParentFragment() instanceof Host) ((Host) getParentFragment()).onFiltersApplied(filters);
        else if (getActivity() instanceof Host) ((Host) getActivity()).onFiltersApplied(filters);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
