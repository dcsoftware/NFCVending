package it.blqlabs.android.nfcvending;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * Created by davide on 10/07/14.
 */
public class CardReader {

    /*public static enum State {
        DISCONNECTED("disconnected", 0), CONNECTED("connected", 1), APP_SELECTED("app_selected", 2), AUTHENTICATED("authenticated", 3),
        LOGGED_IN("logged_in", 4), READING_STATUS("reading_status", 5), DATA_UPDATED("data_updated", 6),
        RELEASED("released", 7);

        private String stringValue;
        private int intValue;

        private State(String s, int i) {
            stringValue = s;
            intValue = i;
        }

        @Override
        public String toString() {
            return stringValue;
        }
    }*/

    private static final String TAG = "CardReader";
    // AID for our loyalty card service.
    //private static final String SAMPLE_CARD_AID = "F01234";
    private static final String APP_AID = "FF000000001234";
    // ISO-DEP command HEADER for selecting an AID.
    // Format: [Class | Instruction | Parameter 1 | Parameter 2]
    private static final String APDU_LE = "00";
    private static final String APDU_SELECT = "00A4";
    private static final String APDU_AUTHENTICATE = "0020";
    private static final String APDU_LOG_IN = "0030";
    private static final String APDU_READ_STATUS = "0040";
    private static final String APDU_UPDATE_CREDIT = "0050";
    private static final String APDU_P1_SELECT_BY_NAME = "04";
    private static final String APDU_P1_SELECT_BY_ID = "00";
    private static final String APDU_P2_SELECT_BY_NAME = "00";
    private static final String APDU_P2_SELECT_BY_ID = "0C";
    private static final String APDU_P1_GENERAL = "00";
    private static final String APDU_P2_GENERAL = "00";
    private static final String APDU_READ_BINARY = "00B0";
    private static final String APDU_UPDATE_BINARY = "00D6";
    // "OK" status word sent in response to SELECT AID command (0x9000)
    private static final byte[] RESULT_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] RESULT_STATUS_WAITING = {(byte) 0x22, (byte) 0x33};
    private static final byte[] RESULT_STATUS_RECHARGED = {(byte) 0x33, (byte) 0x44};
    private static final byte[] RESULT_STATUS_PURCHASE = {(byte) 0x44, (byte) 0x55};
    private static final byte[] RESULT_DATA_UPDATED = {(byte) 0x55, (byte) 0x66};
    private SharedPreferences cardSetting;
    private SharedPreferences.Editor settingEditor;

    private Tag myTag;
    private IsoDep isoDep;

    private String userName = "";
    private String userSurname = "";
    private String userCredit = "";
    private String userId = "";
    private boolean recharged = false;

    //private State cardState;

    private Constants.State cardState;

    //private OneTimePasswordGenerator otpGenerator = new OneTimePasswordGenerator();

    private WeakReference<ActionCallback> mActionCallback;

    public interface ActionCallback {
        public void onActionReceived(String account);
    }

    public CardReader(ActionCallback actionCallback, Tag tag, Context context) {
        mActionCallback = new WeakReference<ActionCallback>(actionCallback);

        cardSetting = context.getSharedPreferences(Constants.USER_SHARED_PREF, Context.MODE_PRIVATE);
        userName = cardSetting.getString(Constants.USER_NAME, "");
        userSurname = cardSetting.getString(Constants.USER_SURNAME, "");
        userCredit = cardSetting.getString(Constants.USER_CREDIT, "");
        userId = cardSetting.getString(Constants.USER_ID, "");

        if(tag != null) {
            myTag = tag;
        }

        isoDep = IsoDep.get(myTag);
        cardState = Constants.State.DISCONNECTED;

    }

    public CardReader(ActionCallback actionCallback, Tag tag) {
        mActionCallback = new WeakReference<ActionCallback>(actionCallback);
        if(tag != null) {
            myTag = tag;
        }

        isoDep = IsoDep.get(myTag);
    }

    public CardReader(Tag tag) {
        if(tag != null) {
            myTag = tag;
        }

        isoDep = IsoDep.get(myTag);
    }

    public void StartTransaction() {

        byte[] command;
        byte[] result;
        byte[] data;
        byte[] statusWord;
        byte[] payload;
        int rLength;
        float newCredit = Float.valueOf(userCredit);

        if(isoDep != null) {
            try {
                isoDep.connect();
                cardState = Constants.State.CONNECTED;
                while(isoDep.isConnected()) {
                    switch(cardState) {
                        case CONNECTED:
                            mActionCallback.get().onActionReceived("Connected...\nSelecting Vending APP: AID=" + APP_AID);

                            command = BuildApduCommand(APDU_SELECT, APDU_P1_SELECT_BY_NAME, APDU_P2_SELECT_BY_NAME, APP_AID, APDU_LE);
                            result = isoDep.transceive(command);

                            statusWord = new byte[]{result[result.length - 2], result[result.length - 1]};

                            if (Arrays.equals(RESULT_OK, statusWord)) {
                                mActionCallback.get().onActionReceived("App selection OK!");
                                cardState = Constants.State.APP_SELECTED;
                            }

                            break;
                        /*case APP_SELECTED:
                            String pw = otpGenerator.calculate();

                            byte[] pwByte = pw.getBytes();

                            mActionCallback.get().onActionReceived("OTP Authorization, code: " + pw);
                            mActionCallback.get().onActionReceived("Hex Code: " + ByteArrayToHexString(pwByte));

                            command = BuildApduCommand(APDU_AUTHENTICATE, APDU_P1_GENERAL, APDU_P2_GENERAL, ByteArrayToHexString(pwByte), APDU_LE);
                            result = isoDep.transceive(command);
                            statusWord = new byte[]{result[result.length - 2], result[result.length - 1]};

                            if (Arrays.equals(RESULT_OK, statusWord)) {
                                mActionCallback.get().onActionReceived("Authenticated!");
                                cardState = Constants.State.AUTHENTICATED;
                            }

                            break;*/
                        case AUTHENTICATED:
                            mActionCallback.get().onActionReceived("Logging in...");

                            data = (userName + ";" + userSurname + ";" + userId + ";" + userCredit + ";").getBytes();

                            command = BuildApduCommand(APDU_LOG_IN, APDU_P1_GENERAL, APDU_P2_GENERAL, ByteArrayToHexString(data), APDU_LE);
                            result = isoDep.transceive(command);
                            statusWord = new byte[]{result[result.length - 2], result[result.length - 1]};

                            if (Arrays.equals(RESULT_OK, statusWord)) {
                                mActionCallback.get().onActionReceived("Logged in!");
                                cardState = Constants.State.READING_STATUS;
                            }

                            break;
                        case READING_STATUS:
                            mActionCallback.get().onActionReceived("Reading Status...");

                            data = new byte[]{(byte) 0x11, (byte) 0x22};

                            command = BuildApduCommand(APDU_READ_STATUS, APDU_P1_GENERAL, APDU_P2_GENERAL, ByteArrayToHexString(data), APDU_LE);
                            result = isoDep.transceive(command);
                            statusWord = new byte[]{result[result.length - 2], result[result.length - 1]};

                            if (Arrays.equals(RESULT_STATUS_WAITING, statusWord)) {
                                mActionCallback.get().onActionReceived("Status: WAITING!");
                                cardState = Constants.State.READING_STATUS;
                                //wait(500);
                            } else if (Arrays.equals(RESULT_STATUS_RECHARGED, statusWord)) {
                                mActionCallback.get().onActionReceived("Status: RECHARGED!");
                                rLength = result.length;
                                payload = Arrays.copyOf(result, rLength - 2);
                                int rechargeValue = Integer.valueOf(ByteArrayToHexString(payload));
                                newCredit += rechargeValue;
                                mActionCallback.get().onActionReceived("Recharging credit of: " + rechargeValue);
                                mActionCallback.get().onActionReceived("New credit: " + newCredit + " (Old credit: " + userCredit);
                                recharged = true;
                                cardState = Constants.State.DATA_UPDATED;
                            } else if (Arrays.equals(RESULT_STATUS_PURCHASE, statusWord)) {
                                mActionCallback.get().onActionReceived("Status: PURCHASE!");
                                rLength = result.length;
                                payload = Arrays.copyOf(result, rLength - 2);
                                int purchaseValue = Integer.valueOf(ByteArrayToHexString(payload));
                                newCredit -= purchaseValue;
                                mActionCallback.get().onActionReceived("Spent: " + purchaseValue);
                                mActionCallback.get().onActionReceived("New credit: " + newCredit + " (Old credit: " + userCredit);
                                cardState = Constants.State.DATA_UPDATED;
                            }

                            break;
                        case DATA_UPDATED:
                            mActionCallback.get().onActionReceived("Sending new credit: " + newCredit);
                            settingEditor.putString(Constants.USER_CREDIT, String.valueOf(newCredit));
                            settingEditor.commit();

                            data = cardSetting.getString(Constants.USER_CREDIT, "").getBytes();

                            command = BuildApduCommand(APDU_UPDATE_CREDIT, APDU_P1_GENERAL, APDU_P1_GENERAL, ByteArrayToHexString(data), APDU_LE);
                            result = isoDep.transceive(command);

                            statusWord = new byte[]{result[result.length - 2], result[result.length - 1]};

                            if (Arrays.equals(RESULT_DATA_UPDATED, statusWord)) {
                                mActionCallback.get().onActionReceived("Credit updated correctly!");
                                cardState = Constants.State.READING_STATUS;
                            }

                            break;

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void Authenticate() {

        if (isoDep != null) {
            try {
                // Connect to the remote NFC device
                isoDep.connect();

                mActionCallback.get().onActionReceived("Selecting App...");
                // Build SELECT AID command for our loyalty card service.
                // This command tells the remote device which service we wish to communicate with.
                Log.i(TAG, "Requesting remote AID: " + APP_AID);
                //byte[] command = BuildSelectApdu(SAMPLE_CARD_AID);
                byte[] command = BuildApduCommand(APDU_SELECT, APDU_P1_SELECT_BY_NAME, APDU_P2_SELECT_BY_NAME, APP_AID, APDU_LE);
                // Send command to remote device
                Log.i(TAG, "SELECT: Sending: " + ByteArrayToHexString(command));
                byte[] result = isoDep.transceive(command);
                // If AID is successfully selected, 0x9000 is returned as the status word (last 2
                // bytes of the result) by convention. Everything before the status word is
                // optional payload, which is used here to hold the account number.
                int resultLength = result.length;
                Log.i(TAG, "SELECT: Result length: " + resultLength);
                byte[] statusWord = {result[resultLength-2], result[resultLength-1]};
                byte[] payload = Arrays.copyOf(result, resultLength - 2);

                if (Arrays.equals(RESULT_OK, statusWord)) {
                    // The remote NFC device will immediately respond with its stored account number
                    //String accountNumber = new String(payload, "UTF-8");
                    String accountNumber = ByteArrayToHexString(payload);
                    Log.i(TAG, "SELECT: Received: " + accountNumber);

                    // Inform CardReaderFragment of received account number
                    mActionCallback.get().onActionReceived("\nApp selection OK!");
                }

                /*String pw = otpGenerator.calculate();

                byte[] pwByte = pw.getBytes();

                mActionCallback.get().onActionReceived("\nOTP Authorization, code: " + pw);
                mActionCallback.get().onActionReceived("\nHex Code: " + ByteArrayToHexString(pwByte));

                byte[] command2 = BuildApduCommand(APDU_AUTHENTICATE, APDU_P1_GENERAL, APDU_P2_GENERAL, ByteArrayToHexString(pwByte), APDU_LE);
                Log.i(TAG, "VERIFY: Sending Command: " + ByteArrayToHexString(command2));
                byte[] result2 = isoDep.transceive(command2);

                int resultLength2 = result2.length;

                Log.i(TAG, "VERIFY: Result length: " + resultLength2);
                byte[] statusWord2 = {result2[resultLength2 - 2], result2[resultLength2 - 1]};
                byte[] payload2 = Arrays.copyOf(result2, resultLength2 - 2);

                String message = ByteArrayToHexString(payload2);

                Log.i(TAG, "VERIFY: Result: " + message);*/
            } catch (IOException e) {
                Log.e(TAG, "Error communicating with card: " + e.toString());
            }
        }
    }

    public boolean Initialize() {

        return true;
    }

    /*public boolean Authenticate() {
        return true;
    }*/

    public boolean SetCredit() {
        return true;
    }

    public boolean UpdateCredit() {
        return true;
    }

    public static byte[] BuildApduCommand(String header, String p1, String p2, String data, String le) {
        return HexStringToByteArray(header + p1 + p2 + String.format("%02X", data.length() / 2) + data + le);
    }

    /**
     * Utility class to convert a byte array to a hexadecimal string.
     *
     * @param bytes Bytes to convert
     * @return String, containing hexadecimal representation.
     */
    public static String ByteArrayToHexString(byte[] bytes) {
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

    /**
     * Utility class to convert a hexadecimal string to a byte string.
     *
     * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
     *
     * @param s String containing hexadecimal characters to convert
     * @return Byte array generated from input
     */
    public static byte[] HexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
