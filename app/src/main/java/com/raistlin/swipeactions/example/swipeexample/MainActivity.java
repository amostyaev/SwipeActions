package com.raistlin.swipeactions.example.swipeexample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.raistlin.swipeactions.SwipeActionsLayout;
import com.raistlin.swipeactions.SwipeDirection;

public class MainActivity extends Activity {

    private int mValue = 10;

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.activity_actions_view);
        SwipeActionsLayout layout = (SwipeActionsLayout) findViewById(R.id.activity_actions_layout);
        layout.setActionsListener(new SwipeActionsLayout.ActionsListener() {
            @Override
            public void onActionSelected(SwipeDirection direction) {
                switch (direction) {
                    case NONE:
                        break;
                    case LEFT:
                        mValue--;
                        updateView();
                        break;
                    case RIGHT:
                        mValue++;
                        updateView();
                        break;
                }
            }
        });
        updateView();
    }

    private void updateView() {
        mTextView.setText(String.valueOf(mValue));
    }
}
