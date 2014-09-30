package it.blqlabs.android.nfcvending;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.tech.IsoDep;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

//import org.jboss.aerogear.security.otp.Totp;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import it.blqlabs.android.nfcvending.OTPGenerator.OTPGenerator;


public class ComService extends Service {
    private static final String APP_AID = "FF000000001234";
    private static final String APDU_LE = "00";
    private static final String APDU_SELECT = "00A4";
    private static final String APDU_AUTHENTICATE = "0020";
    private static final String APDU_LOG_IN = "0030";
    private static final String APDU_READ_STATUS = "0040";
    private static final String APDU_UPDATE_CREDIT = "0050";
    private static final String APDU_P1_SELECT_BY_NAME = "04";
    private static final String APDU_P2_SELECT_BY_NAME = "00";
    private static final String APDU_P1_GENERAL = "00";
    private static final String APDU_P2_GENERAL = "00";
    private static final byte[] RESULT_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] RESULT_STATUS_WAITING = {(byte) 0x22, (byte) 0x33};
    private static final byte[] RESULT_STATUS_RECHARGED = {(byte) 0x33, (byte) 0x44};
    private static final byte[] RESULT_STATUS_PURCHASE = {(byte) 0x44, (byte) 0x55};
    private static final byte[] RESULT_DATA_UPDATED = {(byte) 0x55, (byte) 0x66};
    private static final byte[] RESULT_AUTH_ERROR = {(byte) 0xB1, (byte) 0xB2};


    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;

    private IsoDep isoDep;

    private String userName = "";
    private String userSurname = "";
    private String userCredit = "";
    private String userId = "";
    private boolean recharged = false;
    private Constants.State cardState;
    private SharedPreferences cardSetting;
    private SharedPreferences.Editor settingEditor;
    private byte[] command;
    private byte[] result;
    private byte[] data;
    private byte[] statusWord;
    private byte[] payload;
    private int rLength;
    private float newCredit = 0;

    private String secret = "ABCDEFGHIJ";

    private OTPGenerator mOtpGenerator;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            Context context = MainActivity.getContext();
            mOtpGenerator = new OTPGenerator(secret);
            cardSetting = context.getSharedPreferences(Constants.USER_SHARED_PREF, Context.MODE_PRIVATE);
            settingEditor = cardSetting.edit();
            userName = cardSetting.getString(Constants.USER_NAME, "");
            userSurname = cardSetting.getString(Constants.USER_SURNAME, "");


            isoDep = IsoDep.get(MainActivity.getTag());
            cardState = Constants.State.DISCONNECTED;

            Messenger messenger= MainActivity.getMainActivity().mMessenger;
            if(isoDep != null) {
                try {
                    isoDep.connect();
                    cardState = Constants.State.CONNECTED;
                    messenger.send(Message.obtain(null, cardState.ordinal(), cardState));
                    while(isoDep.isConnected()) {
                        switch(cardState) {
                            case CONNECTED:
                                command = BuildApduCommand(APDU_SELECT, APDU_P1_SELECT_BY_NAME, APDU_P2_SELECT_BY_NAME, APP_AID, APDU_LE);
                                result = isoDep.transceive(command);

                                statusWord = new byte[]{result[0], result[1]};

                                if (Arrays.equals(RESULT_OK, statusWord)) {
                                    cardState = Constants.State.APP_SELECTED;
                                    messenger.send(Message.obtain(null, cardState.ordinal(), "App selection OK!!"));
                                }

                                break;
                            case APP_SELECTED:

                                String pw = null;
                                try {
                                    pw = String.valueOf(mOtpGenerator.getCode1());
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                } catch (InvalidKeyException e) {
                                    e.printStackTrace();
                                }

                                if (pw.length() == 5) {
                                    String s = "0" + pw;
                                    pw = s;
                                }
                                if (pw.length() == 4) {
                                    String s = "00" + pw;
                                    pw = s;
                                }
                                if (pw.length() == 3) {
                                    String s = "000" + pw;
                                    pw = s;
                                }

                                byte[] pwByte = pw.getBytes();

                                messenger.send(Message.obtain(null, cardState.ordinal(), "OTP Authorization, code: " + pw));
                                messenger.send(Message.obtain(null, cardState.ordinal(), "Hex Code: " + ByteArrayToHexString(pwByte)));

                                command = BuildApduCommand(APDU_AUTHENTICATE, APDU_P1_GENERAL, APDU_P2_GENERAL, ByteArrayToHexString(pwByte), APDU_LE);
                                result = isoDep.transceive(command);
                                statusWord = new byte[]{result[0], result[1]};

                                if(Arrays.equals(RESULT_AUTH_ERROR, statusWord)) {
                                    messenger.send(Message.obtain(null, cardState.ordinal(), "Authentication error!!"));
                                    Thread.sleep(1000);
                                } else if (Arrays.equals(RESULT_OK, statusWord)) {
                                    cardState = Constants.State.AUTHENTICATED;
                                    messenger.send(Message.obtain(null, cardState.ordinal(), "Authenticated!!"));
                                }

                                break;
                            case AUTHENTICATED:
                                userCredit = cardSetting.getString(Constants.USER_CREDIT, "");
                                userId = cardSetting.getString(Constants.USER_ID, "");
                                newCredit = Float.valueOf(userCredit);
                                messenger.send(Message.obtain(null, cardState.ordinal(), "Logging in... " + newCredit));

                                data = (userId + "," + userCredit + ";").getBytes();

                                command = BuildApduCommand(APDU_LOG_IN, APDU_P1_GENERAL, APDU_P2_GENERAL, ByteArrayToHexString(data), APDU_LE);
                                result = isoDep.transceive(command);
                                statusWord = new byte[]{result[0], result[1]};

                                if (Arrays.equals(RESULT_OK, statusWord)) {
                                    cardState = Constants.State.READING_STATUS;
                                    messenger.send(Message.obtain(null, cardState.ordinal(), "Logged In..."));
                                }

                                break;
                            case READING_STATUS:
                                messenger.send(Message.obtain(null, cardState.ordinal(), cardState));
                                data = new byte[]{(byte) 0x11, (byte) 0x22};

                                command = BuildApduCommand(APDU_READ_STATUS, APDU_P1_GENERAL, APDU_P2_GENERAL, ByteArrayToHexString(data), APDU_LE);
                                Thread.sleep(3000);
                                result = isoDep.transceive(command);
                                statusWord = new byte[]{result[0], result[1]};
                                Log.d("TAG", "Result lenght = " + result.length);
                                Log.d("TAG", "Status word = " + new String(result));
                                if (Arrays.equals(RESULT_STATUS_WAITING, statusWord)) {
                                    cardState = Constants.State.READING_STATUS;
                                    messenger.send(Message.obtain(null, cardState.ordinal(), "Status: WAITING!"));
                                } else if (Arrays.equals(RESULT_STATUS_RECHARGED, statusWord)) {
                                    payload = Arrays.copyOfRange(result, 2, result.length);
                                    float rechargeValue = Float.valueOf(new String(payload));
                                    messenger.send(Message.obtain(null, cardState.ordinal(), "RECHARGED!! " + rechargeValue));
                                    //rLength = result.length;
                                    //payload = Arrays.copyOf(result, rLength - 2);
                                    newCredit += rechargeValue;
                                    //recharged = true;
                                    cardState = Constants.State.DATA_UPDATED;
                                } else if (Arrays.equals(RESULT_STATUS_PURCHASE, statusWord)) {
                                    payload = Arrays.copyOfRange(result, 2, result.length);
                                    float purchaseValue = Float.valueOf(new String(payload));
                                    messenger.send(Message.obtain(null, cardState.ordinal(), "PURCHASE! " + purchaseValue));
                                    //payload = Arrays.copyOf(result, rLength - 2);
                                    newCredit -= purchaseValue;
                                    cardState = Constants.State.DATA_UPDATED;
                                }

                                break;
                            case DATA_UPDATED:
                                messenger.send(Message.obtain(null, cardState.ordinal(), "Credit update: " + newCredit));
                                newCredit = (float)Math.round(newCredit * 100) / 100;
                                settingEditor.putString(Constants.USER_CREDIT, String.valueOf(newCredit));
                                settingEditor.commit();


                                cardState = Constants.State.AUTHENTICATED;
                                /*data = cardSetting.getString(Constants.USER_CREDIT, "").getBytes();

                                command = BuildApduCommand(APDU_UPDATE_CREDIT, APDU_P1_GENERAL, APDU_P1_GENERAL, ByteArrayToHexString(data), APDU_LE);
                                result = isoDep.transceive(command);

                                statusWord = new byte[]{result[0], result[1]};

                                if (Arrays.equals(RESULT_DATA_UPDATED, statusWord)) {
                                    cardState = Constants.State.READING_STATUS;
                                }*/

                                break;

                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }

        public String ByteArrayToHexString(byte[] bytes) {
            final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
            char[] hexChars = new char[bytes.length * 2];
            int v;
            for ( int j = 0; j < bytes.length; j++ ) {
                v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
        public byte[] BuildApduCommand(String header, String p1, String p2, String data, String le) {
            return HexStringToByteArray(header + p1 + p2 + String.format("%02X", data.length() / 2) + data + le);
        }

        /**
         * Utility class to convert a hexadecimal string to a byte string.
         *
         * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
         *
         * @param s String containing hexadecimal characters to convert
         * @return Byte array generated from input
         */
        public byte[] HexStringToByteArray(String s) {
            int len = s.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i+1), 16));
            }
            return data;
        }
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Thread.NORM_PRIORITY);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "download service starting", Toast.LENGTH_SHORT).show();

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }
}
