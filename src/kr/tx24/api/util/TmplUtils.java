package kr.tx24.api.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TmplUtils {
    private static Logger logger = LoggerFactory.getLogger(TmplUtils.class);
    private static String inputTag = "<input type='hidden' name='{}' value ='{}'/>";
/*
    public static String failure(String name, LinkedMap<String, Object> dataMap){
        String tmpl = getTemplate(name, "tmpl");

        LinkedMap<String, Object> responseMap = new JacksonUtils().fromJsonLinkedMap(dataMap.getString("response"));
        SharedMap<String, String> replaceMap = new SharedMap<>();
        replaceMap.put("${trxId}"       , responseMap.getString("trxId"));
        replaceMap.put("${resultCd}"    , responseMap.getString("resultCd", "X914"));
        replaceMap.put("${message}"     , responseMap.getString("message" ,"내부시스템오류"));
        replaceMap.put("${description}" , responseMap.getString("description", "내부 시스템 및 매입사 시스템 일시 장애입니다 관리자에게 문의해 주시기 바랍니다.."));
        replaceMap.put("${create}"      , DateUtils.getCurrentDate());
        return replaceTemplate(tmpl, replaceMap);
    }


    public static String autoSubmit(String name, String action, String method, LinkedMap<String, Object> dataMap){
        String tmpl = getTemplate(name, "tmpl");

        SharedMap<String, String> replaceMap = new SharedMap<>();
        replaceMap.put("${method}", method);
        replaceMap.put("${action}", action);

        LinkedMap<String, Object> responseMap = new JacksonUtils().fromJsonLinkedMap(dataMap.getString("response"));
        StringJoiner sj = new StringJoiner("\n");
        for(String key : responseMap.keySet()) {
            sj.add(MsgUtils.format(inputTag, key, responseMap.getString(key)));
        }

        replaceMap.put("${params}", sj.toString());
        return replaceTemplate(tmpl, replaceMap);
    }

    public static String autoPostMessage(String name, LinkedMap<String, Object> dataMap) {
        String tmpl = getTemplate(name, "tmpl");

        String postMessage = "";
        if(JacksonUtils.isValid(dataMap.getString("response"))){
            LinkedMap<String, Object> responseMap = new JacksonUtils().fromJsonLinkedMap(dataMap.getString("response"));
            dataMap.put("resultCd", responseMap.getString("resultCd"));
            postMessage = "window.parent.postMessage({['%s']: %s}, '*');";
        }else{
            postMessage = "window.parent.postMessage({['%s']: '%s'}, '*');";
        }

        String evtName  = "EVT_CHECKOUT_ERROR";
        evtName = switch (dataMap.getString("resultCd")){
            case "X006" -> "EVT_CHECKOUT_CLOSE";
            default -> "EVT_CHECKOUT_SUBMITTED";
        };

        postMessage = String.format(postMessage, evtName, dataMap.getString("response"));

        SharedMap<String, String> replaceMap = new SharedMap<>();
        replaceMap.put("${postMessage}", postMessage);

        return replaceTemplate(tmpl, replaceMap);
    }


    private static String getTemplate(String name, String tmplPath) {
        String ext = "tmpl";

        if (ApiConstants.staticMap.containsKey(name)) {
            return new String(ApiConstants.staticMap.getUnchecked(name));
        } else {
            Path path = Paths.get(tmplPath, MsgUtils.format("{}.{}", name, ext));
            byte[] data = null;
            if (Files.exists(path)) {
                try {
                    data = Files.readAllBytes(path);
                    ApiConstants.staticMap.put(name, data);
                } catch (Exception e) {
                    logger.warn("Exception :{}", CommonUtils.getExceptionMessage(e));
                }
            } else {
                logger.warn("Resource not found : {}, {}", path, MsgUtils.format("{}.{}", name));
                return "";
            }

            if (CommonUtils.isEmpty(data)) {
                return "";
            }

            return new String(data);
        }
    }

    public static String replaceTemplate(String template, Map<String, String> replacements) {
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String variable = matcher.group(1);
            String replacement = replacements.getOrDefault("${" + variable + "}", matcher.group());
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }*/
}
