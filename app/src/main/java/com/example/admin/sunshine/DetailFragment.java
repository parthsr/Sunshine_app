package com.example.admin.sunshine;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by Admin on 3/7/2016.
 */
public class DetailFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView=inflater.inflate(R.layout.fragment_detail,container,false);
        Intent intent= getActivity().getIntent();
        if(intent!=null && intent.hasExtra(Intent.EXTRA_TEXT))
        {
            String  forecastStr=intent.getStringExtra(Intent.EXTRA_TEXT);
            TextView text= (TextView) rootView.findViewById(R.id.detail_textView);
            text.setText(forecastStr);
        }

        return rootView;
    }
}
