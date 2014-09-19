package it.blqlabs.android.nfcvending;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class MainFragment extends Fragment {

    private TextView textView;

    public MainFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_main, container, false);
        if(v != null) {
            textView = (TextView) v.findViewById(R.id.textView);
            textView.setMovementMethod(new ScrollingMovementMethod());
            textView.setText("Waiting...");
        }
        return  v;
    }

    public void updateText(final String text) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.append("\n" + text);
            }
        });

    }

}
