package org.mla.finance.payment.payout;

/** Affichage masqué d'un numéro camerounais : 237697272882 -> « 6** ** 28 82 ». */
public final class MsisdnMask {

    private MsisdnMask() {
    }

    public static String mask(String msisdn) {
        if (msisdn == null) {
            return null;
        }
        String national = msisdn.startsWith("237") && msisdn.length() == 12 ? msisdn.substring(3) : msisdn;
        if (national.length() < 5) {
            return "****";
        }
        String last4 = national.substring(national.length() - 4);
        return national.charAt(0) + "** ** " + last4.substring(0, 2) + " " + last4.substring(2);
    }
}
