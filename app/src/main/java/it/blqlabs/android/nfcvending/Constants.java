package it.blqlabs.android.nfcvending;

/**
 * Created by davide on 05/08/14.
 */
public class Constants {

    public static final String USER_SHARED_PREF = "userSharedPref";
    public static final String USER_NAME = "userName";
    public static final String USER_SURNAME = "userSurname";
    public static final String USER_CREDIT = "userCredit";
    public static final String USER_ID = "userId";

    public static final String M_SHARED_PREF = "mSharedPref";
    public static final String IS_FIRST_RUN = "isFirstRun";
    public static final String OTP_KEY = "abcdefghilmnopqrstuvz";

    public static enum State {
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
    }

}
