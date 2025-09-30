package kr.tx24.lib.lang;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PatternUtils {

    /**
     * 
     */
    public PatternUtils() {
        // TODO Auto-generated constructor stub
    }

    /**
     * 주민등록번호/외국인등록번호 유효성 체크
     *
     * @param residentRegistrationNo 주민등록번호/외국인등록번호
     * @return 유효한 주민등록번호/외국인등록번호 형식 여부
     */
    public static boolean isResidentRegistrationNo(String residentRegistrationNo) {
        String juminNo = residentRegistrationNo.replaceAll("[^0-9]", "");
        if (juminNo.length() != 13) {
            return false;
        }
        int yy = to_int(juminNo.substring(0, 2));
        int mm = to_int(juminNo.substring(2, 4));
        int dd = to_int(juminNo.substring(4, 6));
        if (yy < 1 || yy > 99 || mm > 12 || mm < 1 || dd < 1 || dd > 31) {
            return false;
        }
        int sum = 0;
        int juminNo_6 = to_int(juminNo.charAt(6));
        if (juminNo_6 == 1 || juminNo_6 == 2 || juminNo_6 == 3 || juminNo_6 == 4) {
            // 내국인
            for (int i = 0; i < 12; i++) {
                sum += to_int(juminNo.charAt(i)) * ((i % 8) + 2);
            }
            if (to_int(juminNo.charAt(12)) != (11 - (sum % 11)) % 10) {
                return false;
            }
            return true;
        } else if (juminNo_6 == 5 || juminNo_6 == 6 || juminNo_6 == 7 || juminNo_6 == 8) {
            // 외국인
            if (to_int(juminNo.substring(7, 9)) % 2 != 0) {
                return false;
            }
            for (int i = 0; i < 12; i++) {
                sum += to_int(juminNo.charAt(i)) * ((i % 8) + 2);
            }
            if (to_int(juminNo.charAt(12)) != ((11 - (sum % 11)) % 10 + 2) % 10) {
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 주민등록번호/외국인등록번호 유효성 체크
     *
     * @param juminNo 주민등록번호/외국인등록번호
     * @return 유효한 주민등록번호/외국인등록번호 형식 여부
     */
    public static boolean isJuminNo(String juminNo) {
        return isResidentRegistrationNo(juminNo);
    }

    /**
     * 법인번호 유효성 체크
     *
     * @param corporationRegistrationNo 법인번호
     * @return 유효한 법인번호 형식 여부
     */
    public static boolean isCorporationRegistrationNo(String corporationRegistrationNo) {
        String corpRegNo = corporationRegistrationNo.replaceAll("[^0-9]", "");
        if (corpRegNo.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += ((i % 2) + 1) * to_int(corpRegNo.charAt(i));
        }
        if (to_int(corpRegNo.charAt(12)) != (10 - (sum % 10)) % 10) {
            return false;
        }
        return true;

    }

    /**
     * 사업자등록번호 유효성 체크
     *
     * @param businessRegistrationNo 사업자등록번호
     * @return 유효한 사업자등록번호 형식 여부
     */
    public static boolean isBusinessRegistrationNo(String businessRegistrationNo) {
        String bizRegNo = businessRegistrationNo.replaceAll("[^0-9]", "");
        if (bizRegNo.length() != 10) {
            return false;
        }
        int share = (int) (Math.floor(to_int(bizRegNo.charAt(8)) * 5) / 10);
        int rest = (to_int(bizRegNo.charAt(8)) * 5) % 10;
        int sum = (to_int(bizRegNo.charAt(0))) + ((to_int(bizRegNo.charAt(1)) * 3) % 10)
                + ((to_int(bizRegNo.charAt(2)) * 7) % 10) + ((to_int(bizRegNo.charAt(3)) * 1) % 10)
                + ((to_int(bizRegNo.charAt(4)) * 3) % 10) + ((to_int(bizRegNo.charAt(5)) * 7) % 10)
                + ((to_int(bizRegNo.charAt(6)) * 1) % 10) + ((to_int(bizRegNo.charAt(7)) * 3) % 10) + share + rest
                + (to_int(bizRegNo.charAt(9)));
        if (sum % 10 != 0) {
            return false;
        }
        return true;
    }

    /**
     * 신용카드번호 유효성 체크
     *
     * @param creditCardNo 신용카드번호
     * @return 유효한 신용카드번호 형식 여부
     */
    public static boolean isCreditCardNo(String creditCardNo) {
        return PatternUtils.matchCreditCardNo(creditCardNo).find();
    }

    /**
     * 여권번호 유효성 체크
     *
     * @param passportNo 여권번호
     * @return 유효한 여권번호 형식 여부
     */
    public static boolean isPassportNo(String passportNo) {
        return PatternUtils.matchPassportNo(passportNo).find();
    }

    /**
     * 운전면허번호 유효성 체크
     *
     * @param driversLicenseNo 운전면허번호
     * @return 유효한 운전면허번호 형식 여부
     */
    public static boolean isDriversLicenseNo(String driversLicenseNo) {
        return PatternUtils.matchDriversLicenseNo(driversLicenseNo).find();
    }

    /**
     * 휴대폰번호 유효성 체크
     *
     * @param cellphoneNo 휴대폰번호
     * @return 유효한 휴대폰번호 형식 여부
     */
    public static boolean isCellphoneNo(String cellphoneNo) {
        return PatternUtils.matchCellphoneNo(cellphoneNo).find();
    }

    /**
     * 일반전화번호 유효성 체크
     *
     * @param telephoneNo 전화번호
     * @return 유효한 전화번호 형식 여부
     */
    public static boolean isTelephoneNo(String telephoneNo) {
        return PatternUtils.matchTelephoneNo(telephoneNo).find();
    }

    /**
     * 이메일주소 유효성 체크
     *
     * @param emailAddress 이메일주소
     * @return 유효한 이메일주소 형식 여부
     */
    public static boolean isEmailAddress(String emailAddress) {
        return PatternUtils.matchEmailAddress(emailAddress).find();
    }

    /**
     * 아이피주소 유효성 체크
     *
     * @param ipAddress 아이피주소
     * @return 유효한 아이피주소 형식 여부
     */
    public static boolean isIPAddress(String ipAddress) {
        return PatternUtils.matchIPAddress(ipAddress).find();
    }

    /**
     * 주민등록번호 패턴
     */
    private static final Pattern RESIDENT_REGISTRATION_NO = Pattern.compile(
            "\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])(?:\\s|&nbsp;)*-?(?:\\s|&nbsp;)*[1-8]\\d{6}",
            Pattern.MULTILINE);

    private static Matcher matchResidentRegistrationNo(String residentRegistrationNo) {
        return BUSINESS_REGISTRATION_NO.matcher(residentRegistrationNo);
    }

    /**
     * 주민등록번호 패턴
     */
    private static final Pattern JUMIN_NO = RESIDENT_REGISTRATION_NO;

    private static Matcher matchJuminNo(String juminNo) {
        return JUMIN_NO.matcher(juminNo);
    }

    /**
     * 법인번호 패턴
     */
    private static final Pattern CORPORATION_REGISTRATION_NO = Pattern
            .compile("\\d{6}(?:\\s|&nbsp;)*-?(?:\\s|&nbsp;)*\\d{7}");

    private static Matcher matchCorporationRegistrationNo(String corporationRegistrationNo) {
        return CORPORATION_REGISTRATION_NO.matcher(corporationRegistrationNo);
    }

    /**
     * 사업자등록번호 패턴
     */
    private static final Pattern BUSINESS_REGISTRATION_NO = Pattern.compile(
            "[0-9]{3}(?:\\s|&nbsp;)*-(?:\\s|&nbsp;)*[0-9]{2}(?:\\s|&nbsp;)*-(?:\\s|&nbsp;)*[0-9]{5}",
            Pattern.MULTILINE);

    private static Matcher matchBusinessRegistrationNo(String businessRegistrationNo) {
        return BUSINESS_REGISTRATION_NO.matcher(businessRegistrationNo);
    }

    /**
     * 신용카드번호 패턴
     */
    private static final Pattern CREDIT_CARD_NO = Pattern.compile(
            "(?:5[1-5]\\d{14})|(?:4\\d{12}(\\d{3})?)|(?:3[47]\\d{13})|(?:6011\\d{12})|(?:(?:30[0-5]|36\\d|38\\d)\\d{11})",
            Pattern.MULTILINE);

    private static Matcher matchCreditCardNo(String creditCardNo) {
        return CREDIT_CARD_NO.matcher(creditCardNo);
    }

    /**
     * 여권번호 패턴
     */
    private static final Pattern PASSPORT_NO = Pattern.compile("");

    private static Matcher matchPassportNo(String passportNo) {
        return PASSPORT_NO.matcher(passportNo);
    }

    /**
     * 운전면허번호 패턴
     */
    private static final Pattern DRIVERS_LICENSE_NO = Pattern.compile("");

    private static Matcher matchDriversLicenseNo(String driversLicenseNo) {
        return DRIVERS_LICENSE_NO.matcher(driversLicenseNo);
    }

    /**
     * 휴대폰번호 패턴
     */
    private static final Pattern CELLPHONE_NO = Pattern.compile(
            "01(?:0|1|6|7|8|9)(?:\\s|&nbsp;)*-?(?:\\s|&nbsp;)*(?:\\d{4}|\\d{3})(?:\\s|&nbsp;)*-?(?:\\s|&nbsp;)*\\d{4}",
            Pattern.MULTILINE);

    private static Matcher matchCellphoneNo(String cellphoneNo) {
        return CELLPHONE_NO.matcher(cellphoneNo);
    }

    /**
     * 일반전화번호 패턴
     */
    private static final Pattern TELEPHONE_NO = Pattern.compile(
            "(?:02|0[3-9]{1}[0-9]{1})(?:\\s|&nbsp;)*(?:\\)|-)?(?:\\s|&nbsp;)*(?:\\d{4}|\\d{3})(?:\\s|&nbsp;)*-?(?:\\s|&nbsp;)*\\d{4}",
            Pattern.MULTILINE);

    private static Matcher matchTelephoneNo(String telephoneNo) {
        return TELEPHONE_NO.matcher(telephoneNo);
    }

    /**
     * 이메일주소 패턴
     */
    private static final Pattern EMAIL_ADDRESS = Pattern.compile("(?:\\w+\\.)*\\w+@(?:\\w+\\.)+[A-Za-z]+",
            Pattern.MULTILINE);

    private static Matcher matchEmailAddress(String emailAddress) {
        return EMAIL_ADDRESS.matcher(emailAddress);
    }

    /**
     * 아이피주소 패턴
     */
    private static final Pattern IP_ADDRESS = Pattern.compile(
            "(?:(?:(?:\\d{1,2})|(?:1\\d{2})|(?:2[0-4]\\d)|(?:25[0-5]))\\.){3}(?:(?:\\d{1,2})|(?:1\\d{2})|(?:2[0-4]\\d)|(?:25[0-5]))",
            Pattern.MULTILINE);

    private static Matcher matchIPAddress(String ipAddress) {
        return IP_ADDRESS.matcher(ipAddress);
    }

    private static int to_int(char c) {
        return Integer.parseInt(String.valueOf(c));
    }

    /**
     * String으로 표현된 숫자를 타입을 int로 변경
     */
    private static int to_int(String s) {
        return Integer.parseInt(s);
    }

    public static String prettyAccnt(String bankCd, String accnt) {
        accnt = accnt.replaceAll("[-]", "").trim();
        int len = accnt.length();

        if (bankCd.equals("002")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 14) {
                if (accnt.startsWith("013")) {
                    return accnt.replaceAll("(\\d{3})(\\d{7})(\\d{1})(\\d{3})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{3})(\\d{8})(\\d{3})", "$1-$2-$3");
                }
            }
        } else if (bankCd.equals("003")) {
            if (len == 10) {
                return accnt.replaceAll("(\\d{8})(\\d{2})", "$1-$2");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{8})", "$1-$2");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{6})(\\d{2})(\\d{2})(\\d{1})", "$1-$2-$3-$4-$5");
            }
        } else if (bankCd.equals("004")) {
            if (len == 11) {
                return accnt;
            } else if (len == 12) {
                if (accnt.substring(3, 5).equals("01")) {
                    return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{4})(\\d{3})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{6})(\\d{2})(\\d{4})", "$1-$2-$3");
                }
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{4})(\\d{2})(\\d{7})(\\d{1})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("005")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else {
                return accnt.replaceAll("(\\d{3})(\\d{6})(\\d{3})", "$1-$2-$3");
            }
        } else if (bankCd.equals("007")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 12) {
                if (accnt.substring(0, 3).equals("101") || accnt.substring(0, 3).equals("201")) {
                    return accnt.replaceAll("(\\d{3})(\\d{8})(\\d{1})", "$1-$2-$3");
                } else {
                    return accnt.replaceAll("(\\d{1})(\\d{11})", "$1-$2");
                }
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{8})(\\d{1})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("011")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{4})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 13) {
                return accnt.replaceAll("(\\d{3})(\\d{4})(\\d{4})(\\d{1})(\\d{1})", "$1-$2-$3-$4-$5");
            } else if (len == 14) {
                if (accnt.substring(6, 8).equals("64") || accnt.substring(6, 8).equals("65")) {
                    return accnt.replaceAll("(\\d{6})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{3})(\\d{4})(\\d{4})(\\d{2})(\\d{1})", "$1-$2-$3-$4-$5");
                }
            }
        } else if (bankCd.equals("012")) {
            if (len == 13) {
                return accnt.replaceAll("(\\d{3})(\\d{4})(\\d{4})(\\d{1})(\\d{1})", "$1-$2-$3-$4-$5");
            } else if (len == 14) {
                if (accnt.substring(6, 8).equals("51")) {
                    return accnt.replaceAll("(\\d{6})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
                } else if (accnt.substring(6, 8).equals("66") || accnt.substring(6, 8).equals("67")) {
                    return accnt.replaceAll("(\\d{6})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{3})(\\d{4})(\\d{4})(\\d{2})(\\d{1})", "$1-$2-$3-$4-$5");
                }
            }
        } else if (bankCd.equals("020")) {
            if (len == 13) {
                return accnt.replaceAll("(\\d{4})(\\d{3})(\\d{6})", "$1-$2-$3");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{6})(\\d{2})(\\d{2})(\\d{1})", "$1-$2-$3-$4-$5");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})(\\d{1})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("023")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{9})", "$1-$2-$3");
            }
        } else if (bankCd.equals("027")) {
            if (len == 13) {
                return accnt.replaceAll("(\\d{3})(\\d{5})(\\d{2})(\\d{1})(\\d{2})", "$1-$2-$3-$4-$5");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{1})(\\d{6})(\\d{1})(\\d{2})(\\d{2})", "$1-$2-$3-$4-$5");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{5})(\\d{2})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 10) {
                if (accnt.substring(0, 1).equals("5")) {
                    return accnt.replaceAll("(\\d{1})(\\d{6})(\\d{2})(\\d{1})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{2})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
                }
            }
        } else if (bankCd.equals("031")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})(\\d{3})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("032")) {
            if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 13) {
                return accnt.replaceAll("(\\d{3})(\\d{4})(\\d{4})(\\d{2})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("034")) {
            if (len == 12) {
                if (accnt.substring(3, 6).equals("107") || accnt.substring(3, 6).equals("108")) {
                    return accnt.replaceAll("(\\d{3})(\\d{3})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{1})(\\d{3})(\\d{9})", "$1-$2-$3");
                }
            } else if (len == 13) {
                return accnt.replaceAll("(\\d{3})(\\d{3})(\\d{5})", "$1-$2-$3");
            }
        } else if (bankCd.equals("035")) {
            if (len == 10) {
                return accnt.replaceAll("(\\d{2})(\\d{2})(\\d{6})", "$1-$2-$3");
            }
        } else if (bankCd.equals("037")) {
            if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{7})", "$1-$2-$3");
            } else if (len == 13) {
                return accnt.replaceAll("(\\d{1})(\\d{3})(\\d{2})(\\d{7})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("039")) {
            if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{7})", "$1-$2-$3");
            } else if (len == 13) {
                return accnt.replaceAll("(\\d{3})(\\d{9})(\\d{1})", "$1-$2-$3");
            }
        } else if (bankCd.equals("045")) {
            if (len == 13) {
                if (accnt.substring(4, 6).equals("09") || accnt.substring(4, 6).equals("10")
                        || accnt.substring(4, 6).equals("13") || accnt.substring(4, 6).equals("37")) {
                    return accnt.replaceAll("(\\d{4})(\\d{2})(\\d{6})(\\d{1})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{4})(\\d{8})(\\d{1})", "$1-$2-$3");
                }
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{4})(\\d{3})(\\d{6})(\\d{1})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("048")) {
            if (len == 13) {
                return accnt.replaceAll("(\\d{5})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{5})(\\d{2})(\\d{6})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 10) {
                return accnt.replaceAll("(\\d{3})(\\d{3})(\\d{4})", "$1-$2-$3");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{4})(\\d{4})", "$1-$2-$3");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{3})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("050")) {
            if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{2})(\\d{6})(\\d{1})", "$1-$2-$3-$4-$5");
            }
        } else if (bankCd.equals("054")) {
            if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{5})(\\d{1})(\\d{3})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("055")) {
            return accnt;
        } else if (bankCd.equals("057")) {
            return accnt;
        } else if (bankCd.equals("060")) {
            if (len == 12) {
                return accnt.replaceAll("(\\d{4})(\\d{5})(\\d{2})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{4})(\\d{10})", "$1-$2");
            }
        } else if (bankCd.equals("062")) {
            return accnt.replaceAll("(\\d{3})(\\d{9})(\\d{2})", "$1-$2-$3");
        } else if (bankCd.equals("064")) {
            if (len == 13) {
                return accnt.replaceAll("(\\d{5})(\\d{2})(\\d{6})", "$1-$2-$3");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{7})", "$1-$2-$3");
            }
        } else if (bankCd.equals("071")) {
            return accnt.replaceAll("(\\d{6})(\\d{2})(\\d{6})", "$1-$2-$3");
        } else if (bankCd.equals("081")) {
            return accnt.replaceAll("(\\d{3})(\\d{9})(\\d{2})", "$1-$2-$3");
        } else if (bankCd.equals("088")) {
            if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{8})(\\d{1})", "$1-$2-$3");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{3})(\\d{7})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{5})(\\d{1})", "$1-$2-$3-$4");
            } else if (len == 13) {
                if (accnt.substring(3, 5).equals("81")) {
                    return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{7})(\\d{1})", "$1-$2-$3-$4");
                } else {
                    return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{8})", "$1-$2-$3");
                }
            }
        } else if (bankCd.equals("089")) {
            if (len == 10) {
                return accnt.replaceAll("(\\d{1})(\\d{9})", "$1-$2");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{3})(\\d{3})(\\d{6})", "$1-$2-$3");
            } else if (len == 13) {
                return accnt.replaceAll("(\\d{2})(\\d{3})(\\d{4})(\\d{4})", "$1-$2-$3-$4");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{4})(\\d{3})(\\d{4})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("090")) {
            return accnt.replaceAll("(\\d{4})(\\d{2})(\\d{7})", "$1-$2-$3");
        } else if (bankCd.equals("209")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{4})(\\d{4})(\\d{4})", "$1-$2-$3");
            }
        } else if (bankCd.equals("218")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
            } else if (len == 9) {
                return accnt.replaceAll("(\\d{3})(\\d{3})(\\d{3})", "$1-$2-$3");
            }
        } else if (bankCd.equals("230")) {
            return accnt;
        } else if (bankCd.equals("238")) {
            return accnt;
        } else if (bankCd.equals("240")) {
            if (len == 8 || len == 10 || len == 12) {
                return accnt;
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{1})(\\d{5})(\\d{8})", "$1-$2-$3");
            }
        } else if (bankCd.equals("243")) {
            if (len == 10) {
                return accnt.replaceAll("(\\d{8})(\\d{2})", "$1-$2");
            } else if (len == 12) {
                return accnt.replaceAll("(\\d{8})(\\d{4})", "$1-$2");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{8})(\\d{2})(\\d{4})", "$1-$2-$3");
            }
        } else if (bankCd.equals("247")) {
            return accnt;
        } else if (bankCd.equals("261")) {
            return accnt.replaceAll("(\\d{4})(\\d{4})(\\d{1})(\\d{1})", "$1-$2-$3-$4");
        } else if (bankCd.equals("262")) {
            return accnt.replaceAll("(\\d{4})(\\d{4})(\\d{2})", "$1-$2-$3");
        } else if (bankCd.equals("263")) {
            return accnt;
        } else if (bankCd.equals("264")) {
            if (len == 10) {
                return accnt.replaceAll("(\\d{4})(\\d{4})(\\d{2})", "$1-$2-$3");
            } else {
                return accnt.replaceAll("(\\d{4})(\\d{4})", "$1-$2");
            }
        } else if (bankCd.equals("265")) {
            return accnt;
        } else if (bankCd.equals("266")) {
            return accnt;
        } else if (bankCd.equals("267")) {
            if (len == 9) {
                return accnt.replaceAll("(\\d{3})(\\d{6})", "$1-$2");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{6})(\\d{2})", "$1-$2-$3");
            }
        } else if (bankCd.equals("269")) {
            if (len == 10) {
                return accnt;
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{8})", "$1-$2");
            } else if (len == 13) {
                return accnt;
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{11})", "$1-$2");
            }
        } else if (bankCd.equals("270")) {
            if (len == 8) {
                return accnt.replaceAll("(\\d{7})(\\d{1})", "$1-$2");
            } else if (len == 10) {
                return accnt.replaceAll("(\\d{7})(\\d{1})(\\d{2})", "$1-$2-$3");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{8})(\\d{3})", "$1-$2");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{8})(\\d{3})(\\d{3})", "$1-$2-$3");
            }
        } else if (bankCd.equals("278")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
            }
        } else if (bankCd.equals("279")) {
            if (len == 9) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{4})", "$1-$2-$3");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{4})(\\d{2})", "$1-$2-$3-$4");
            }
        } else if (bankCd.equals("280")) {
            return accnt;
        } else if (bankCd.equals("287")) {
            if (len == 10) {
                return accnt.replaceAll("(\\d{4})(\\d{4})(\\d{2})", "$1-$2-$3");
            } else if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
            }
        } else if (bankCd.equals("290")) {
            return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
        } else if (bankCd.equals("291")) {
            return accnt;
        } else if (bankCd.equals("292")) {
            if (len == 11) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})", "$1-$2-$3");
            } else if (len == 14) {
                return accnt.replaceAll("(\\d{3})(\\d{2})(\\d{6})(\\d{3})", "$1-$2-$3-$4");
            }
        }
        return accnt;
    }

    public static int getAge(int birthYear, int birthMonth, int birthDay) {
        Calendar current = Calendar.getInstance();
        int currentYear = current.get(Calendar.YEAR);
        int currentMonth = current.get(Calendar.MONTH) + 1;
        int currentDay = current.get(Calendar.DAY_OF_MONTH);

        int age = currentYear - birthYear;

        if (birthMonth * 100 + birthDay > currentMonth * 100 + currentDay) {
            age--;
        }
        return age;
    }

    public static int calculateManAge(String dob) {

        String today = ""; // 오늘 날짜
        int manAge = 0; // 만 나이

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");

        today = formatter.format(new Date()); // 시스템 날짜를 가져와서 yyyyMMdd 형태로 변환

        // today yyyyMMdd
        int todayYear = Integer.parseInt(today.substring(0, 4));
        int todayMonth = Integer.parseInt(today.substring(4, 6));
        int todayDay = Integer.parseInt(today.substring(6, 8));

        int ssnYear = Integer.parseInt(dob.substring(0, 2));
        int ssnMonth = Integer.parseInt(dob.substring(2, 4));
        int ssnDay = Integer.parseInt(dob.substring(4, 6));

        if (dob.charAt(6) == '0' || dob.charAt(6) == '9') {
            ssnYear += 1800;
        } else if (dob.charAt(6) == '1' || dob.charAt(6) == '2' || dob.charAt(6) == '5' || dob.charAt(6) == '6') {
            ssnYear += 1900;
        } else { // 3, 4, 7, 8
            ssnYear += 2000;
        }

        manAge = todayYear - ssnYear;

        if (todayMonth < ssnMonth) { // 생년월일 "월"이 지났는지 체크
            manAge--;
        } else if (todayMonth == ssnMonth) { // 생년월일 "일"이 지났는지 체크
            if (todayDay < ssnDay) {
                manAge--; // 생일 안지났으면 (만나이 - 1)
            }
        }

        return manAge;
    }
    
    /**
     * 문자열에서 6자리 인증번호 찾기 
     * 먼저 [] 사이의 6자리 숫자를 찾고 없다면 연속된 6자리 숫자를 찾는다. 
     * @param message
     * @return
     */
    public static String getAuthNumber(String message) {
    	
    	String number = "";
    	
    	Pattern p = Pattern.compile("\\[(\\d{6})\\]");
    	Matcher matcher = p.matcher(message);
    	while (matcher.find()){
    		int g = matcher.groupCount();
    		if(g > 0) {
    			number = matcher.group(0);
    			number = number.replaceAll("\\[", "").replaceAll("\\]", "").trim();
    			break;
    		}
    	}
    	
    	if(CommonUtils.hasValue(number)){
    		return number;
    	}
    	
    	p = Pattern.compile("(\\d{6})");
    	matcher = p.matcher(message);
    	while (matcher.find()){
    		int g = matcher.groupCount();
    		//마지막 값을 가져온다.
    		for(int i=0;i<g ; i++) {
    			number = matcher.group(i).trim();
    		}
    	}
    	return number;
    }
    
    
    

}