package it.blqlabs.android.nfcvending;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.app.FragmentTransaction;
import android.view.WindowManager;
import android.widget.Toast;


public class MainActivity extends FragmentActivity implements CardReader.ActionCallback{

    public static final String TAG = "MainActivity";
    public CardReader reader;
    public MainFragment fragment;

    private static MainActivity mMainActivity;
    private static Tag mTag;
    private static Context mContext;

    public Messenger mMessenger = new Messenger(new MessageHandler(this));
    private class MessageHandler extends Handler {
        private Context c;

        MessageHandler(Context c){
            this.c = c;
        }
        @Override
        public void handleMessage(Message msg) {
            onActionReceived(msg.obj.toString());
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mMainActivity = this;
        mContext = this.getApplicationContext();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        fragment = new MainFragment();
        transaction.replace(R.id.container, fragment);
        transaction.commit();

        isFirstRun();
    }

    public static MainActivity getMainActivity() {
        return mMainActivity;
    }

    public void isFirstRun() {
        SharedPreferences mSharedPref = getSharedPreferences(Constants.M_SHARED_PREF, MODE_PRIVATE);
        if (mSharedPref.getBoolean(Constants.IS_FIRST_RUN, true)) {

            SharedPreferences userSharedPref = getSharedPreferences(Constants.USER_SHARED_PREF, MODE_PRIVATE);
            SharedPreferences.Editor userPrefEditor = userSharedPref.edit();
            userPrefEditor.putString(Constants.USER_NAME, "Davide");
            userPrefEditor.putString(Constants.USER_SURNAME, "Corradini");
            userPrefEditor.putString(Constants.USER_CREDIT, "0");
            userPrefEditor.putString(Constants.USER_ID, "123456789");
            userPrefEditor.commit();

            SharedPreferences.Editor mPrefEditor = mSharedPref.edit();
            mPrefEditor.putBoolean(Constants.IS_FIRST_RUN, false);
            mPrefEditor.commit();
        }
    }


    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    public void onResume() {
        super.onResume();
        if(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    private void processIntent(Intent intent) {
        if(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Log.i(TAG, "tech discovered");

            mTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if(mTag != null) {
                Log.i(TAG, "Tag found");
                /*reader = new CardReader(this, mTag, this);

                reader.StartTransaction();*/
                //reader.Authenticate();
                Intent serv = new Intent(MainActivity.this, ComService.class);
                startService(serv);
            }

        }
    }

    public static Tag getTag() {
        return mTag;
    }

    public static Context getContext() {
        return mContext;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActionReceived(String text) {
        fragment.updateText(text);
    }
}
