package com.mbientlab.bletoolbox.dfu;

import com.mbientlab.bletoolbox.R;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class FirmwareVersionSelector extends DialogFragment {
    private FirmwareConfiguration config;
    
    public interface FirmwareConfiguration {
        void versionSelected(int index);
        String[] availableVersions();
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        
        if (!(activity instanceof FirmwareConfiguration)) {
            throw new IllegalStateException(
                    "Activity must implement AccelerometerFragment.Configuration interface.");
        }
        
        config= (FirmwareConfiguration) activity;
    }
    
    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_firmware_selector, container);
    }
    
    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        getDialog().setTitle("Firmware Versions");
        
        ListView versionList= (ListView) view.findViewById(R.id.firmware_version_list);
        versionList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                config.versionSelected(position);
                dismiss();
            }
        });
        versionList.setAdapter(new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_list_item_1, config.availableVersions()));
    }
}
