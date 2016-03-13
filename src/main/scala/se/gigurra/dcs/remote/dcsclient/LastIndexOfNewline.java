package se.gigurra.dcs.remote.dcsclient;

public class LastIndexOfNewline {
    public static int find(final byte[] data, final int n) {
        for (int i = n-1; i >= 0; i--) {
            if (data[i] == 10) {
                return i;
            }
        }
        return -1;
    }
}
