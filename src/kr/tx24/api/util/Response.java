package kr.tx24.api.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import kr.tx24.api.conf.ApiConfigLoader;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.lang.URIUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.mapper.JacksonUtils;
import mik.proc.http.bean.RequestObject;

public class Response {
	private static Logger logger = LoggerFactory.getLogger(Response.class);
	public static final String TEXT_PLAIN = "text/plain; charset=UTF-8";
	public static final String TEXT_HTML = "text/html; charset=UTF-8";
	public static final String APPLICATION_JSON = "application/json; charset=UTF-8";
	public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
	public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
	public static final int HTTP_BROWSER_CACHE_SECONDS = 2 * 60;
	private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
	//private static final List<String> STATIC_EXT		= Arrays.asList("css","html","js","");
	private static final int HTTP_CACHE_SECONDS = 2 * 60;
	private static final String CHARSET = "charset";
	private static final String RESPONSE = "response";
	private static HttpResponseStatus STATUS_OK = new HttpResponseStatus(200, "OK. ");


	public static void redirect(ChannelHandlerContext ctx, String newUri, boolean permanent) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, (permanent == true) ? HttpResponseStatus.PERMANENT_REDIRECT : HttpResponseStatus.TEMPORARY_REDIRECT);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN);
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "0");
		response.headers().set(HttpHeaderNames.LOCATION, newUri);

		ctx.writeAndFlush(response);
		logger.info("{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), response.status().toString());

	}

	public static void error(ChannelHandlerContext ctx, HttpResponseStatus status) {

		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN);
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "0");

		ctx.writeAndFlush(response);
		logger.info("{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), status.toString());

	}

	public static void error(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {

		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN);
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, message.getBytes().length);

		ctx.writeAndFlush(response);
		logger.info("{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), status.toString());

	}

	public static void notModified(ChannelHandlerContext ctx, FullHttpRequest request) {
		HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "0");

		if (HttpUtil.isKeepAlive(request)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(response);
		} else {
			ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		}

		logger.info("{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), HttpResponseStatus.NOT_MODIFIED.toString());

	}

	public static void response(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status) {
		FullHttpResponse response = null;
		if (status.equals(HttpResponseStatus.OK)) {
			response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer("OK.", CharsetUtil.UTF_8));
		} else {
			response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		}


		response.headers().add(HttpHeaderNames.CONTENT_TYPE, TEXT_HTML);
		response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

		if (HttpUtil.isKeepAlive(request)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(response);

		} else {
			ChannelFuture cf = ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
			cf.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
					}
					future.channel().close();
				}
			});
		}
		logger.info("{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), response.status().toString());
	}


	public static void successResource(ChannelHandlerContext ctx, FullHttpRequest request, byte[] data) {

		HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(data));
		response.headers().add(HttpHeaderNames.CONTENT_LENGTH, data.length);


		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
		//Date header
		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
		time.add(Calendar.SECOND, HTTP_BROWSER_CACHE_SECONDS);
		response.headers().add(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
		response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_BROWSER_CACHE_SECONDS);
		response.headers().add(HttpHeaderNames.CONTENT_TYPE, ApiUtils.getMimeType(request.uri()));
		response.headers().add(HttpHeaderNames.ACCEPT_RANGES, HttpHeaderValues.BYTES);
		response.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		response.headers().set(HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(System.currentTimeMillis())));    //이 부분은 하지 말자


		if (HttpUtil.isKeepAlive(request)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.write(response);
		} else {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

			ChannelFuture cf = ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
			cf.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
					}
					future.channel().close();
				}
			});
		}

		ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

		if (!HttpUtil.isKeepAlive(request)) {
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
		logger.info("{},{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), response.status().code(), data.length);
		return;
	}


	public static void success(RequestObject Request, Object response) {
		String res = new JacksonUtils().toJson(response);
		if (SystemUtils.deepview()) {
			logger.info("response={}", res);
		}
		response(Request, APPLICATION_JSON, Unpooled.copiedBuffer(res, CharsetUtil.UTF_8), STATUS_OK);
	}

	public static void success(RequestObject Request, String contentType, String message) {
		if (SystemUtils.deepview()) {
			logger.info("response={}", message);
		}
		response(Request, contentType, Unpooled.copiedBuffer(message, CharsetUtil.UTF_8), STATUS_OK);
	}

	public static void success(RequestObject Request, String contentType, ByteBuf byteBuf) {
		response(Request, contentType, byteBuf, STATUS_OK);

	}


	public static void response(RequestObject Request, INet inet) {
		if (inet.data().isEmpty(CHARSET)) {
			inet.data().put(CHARSET, CharsetUtil.UTF_8.toString());
		}

		String contentType = MsgUtils.format("{}; charset={}", inet.data().getString("contentType"), inet.data().getString(CHARSET));

		if (SystemUtils.deepview()) {
			logger.info("contentType : [{}]", contentType);
			logger.info("response");
			logger.info("head : \n{}", new JacksonUtils().pretty().toJson(inet.head()));
			logger.info("data : \n{}", new JacksonUtils().pretty().toJson(inet.data()));
		}

		if(inet.head().isEquals("responseType", "redirect/http")) {
			redirect(Request.ctx, inet.data().getString(RESPONSE), false);

		}else if (inet.head().isEquals("responseType", "text/html")) {
			LinkedMap<String, Object> headMap = new LinkedMap<>(inet.head());
			LinkedMap<String, Object> dataMap = new LinkedMap<>(inet.data());
			
			response(Request, TEXT_HTML, Unpooled.copiedBuffer(dataMap.getString("html").getBytes()), HttpResponseStatus.valueOf(headMap.getInt("httpStatus")));
			
			logger.info("httpStatus : {}", headMap.getInt("httpStatus"));
			
			return;
			
		}else if (inet.head().isEquals("responseType", "redirect/html")) {
			LinkedMap<String, Object> headMap = new LinkedMap<>(inet.head());
			LinkedMap<String, Object> dataMap = new LinkedMap<>(inet.data());

			String form = "";
			if (CommonUtils.isContains(headMap.getString("tmpl"),"auto_submit","galaxia_auto_submit")) {
				form = TmplUtils.autoSubmit(
						headMap.getString("tmpl"),
						headMap.getString("action"),
						headMap.getString("method"),
						dataMap
				);
			} else if (headMap.isEquals("tmpl", "postmessage_submit")) {
				form = TmplUtils.autoPostMessage(
						headMap.getString("tmpl"),
						dataMap
				);
			}else {
				form = TmplUtils.failure(headMap.getString("tmpl", "failure"), dataMap);
				response(Request, TEXT_HTML, Unpooled.copiedBuffer(form.getBytes()), new HttpResponseStatus(
						headMap.getInt("httpStatus"), headMap.getString("httpMessage")));
				return;
			}

			response(Request, TEXT_HTML, Unpooled.copiedBuffer(form.getBytes()), STATUS_OK);
		} else if (inet.head().startsWith("responseType", "error")) {
			// String[] error = CommonUtils.split(inet.data().getString("contentType"), "/", true,2);
			// error(Request.ctx, new HttpResponseStatus(CommonUtils.parseInt(error[1]), inet.data().getString(RESPONSE)));
		} else {
			try {
				byte[] buf = null;
				if (inet.data().get(RESPONSE) instanceof byte[]) {
					buf = (byte[]) inet.data().get(RESPONSE);
				} else {
					buf = inet.data().getString(RESPONSE).getBytes(inet.data().getString(CHARSET));
				}

				LinkedMap<String, Object> headMap = new LinkedMap<>(inet.head());
				if(CommonUtils.isNotEmpty(headMap)){
					if (inet.head().isEquals("responseType", "redirect/text")) {
						response(Request, TEXT_PLAIN, Unpooled.copiedBuffer(buf), new HttpResponseStatus(
								headMap.getInt("httpStatus", 200), headMap.getString("httpMessage", "OK. ")));
					}else{
						response(Request, contentType, Unpooled.copiedBuffer(buf), new HttpResponseStatus(
								headMap.getInt("httpStatus", 200), headMap.getString("httpMessage", "OK. ")));
					}
				}else{
					response(Request, contentType, Unpooled.copiedBuffer(buf), STATUS_OK);
				}

			} catch (Exception e) {
				logger.info("error : {}", e.getMessage());
				response(Request, contentType, Unpooled.copiedBuffer(inet.data().getString(RESPONSE), Charset.forName(inet.data().getString(CHARSET))), STATUS_OK);
			}
		}
	}

	public static void response(RequestObject Request, String contentType, ByteBuf byteBuf, HttpResponseStatus status) {
		ChannelHandlerContext ctx = Request.ctx;

		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, byteBuf);

		response.headers().add("Access-Control-Allow-Origin", "*");
		response.headers().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT");

		if (Request.httpMethod.equals(HttpMethod.OPTIONS.name())) {
			response.headers().add("Access-Control-Allow-Headers", "Origin, Authorization, Content-Type, Accept");
		}

		response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
		response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
	

		try {
			if (Request.keepAlive) {
				response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
				ctx.writeAndFlush(response);
			} else {
				response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
				ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
					@Override
					public void operationComplete(ChannelFuture future) throws Exception {
						if (future.isSuccess()) {
						} else {
							logger.info("response error : ");
						}
						future.channel().close();
					}
				});
			}

		} catch (Exception e) {
		}

		if (byteBuf == null) {
			logger.info("{},{},{},null", ctx.channel().attr(AttributeKey.valueOf("log")).get(), response.status().code(), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
		} else {
			logger.info("{},{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), response.status().code(), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
			//logger.info("{},{},{}",ctx.channel().attr(AttributeKey.valueOf("log")).get(),status.code(),response.content().readableBytes());
		}


		logger.info(String.format("elapsed Time in %.3fms%n", (System.nanoTime() - Request.startTime) / 1e6d));

	}

	public static void error(RequestObject Request, String contentType, ByteBuf byteBuf, HttpResponseStatus status) {
		ChannelHandlerContext ctx = Request.ctx;

		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, byteBuf);

		response.headers().add("Access-Control-Allow-Origin", "*");
		response.headers().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT");

		if (Request.httpMethod.equals(HttpMethod.OPTIONS.name())) {
			response.headers().add("Access-Control-Allow-Headers", "Origin, Authorization, Content-Type, Accept");
		}
		response.headers().add(HttpHeaderNames.CACHE_CONTROL, "no-cache");
		response.headers().add(HttpHeaderNames.EXPIRES, "-1");
		response.headers().add(HttpHeaderNames.PRAGMA, "no-cache");
		response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
		response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
		response.headers().add("x-powered-by", "E2U");

		try {
			if (Request.keepAlive) {
				response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
				ctx.writeAndFlush(response);
			} else {
				response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
				ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
					@Override
					public void operationComplete(ChannelFuture future) throws Exception {
						if (future.isSuccess()) {
						} else {
							logger.info("response error : ");
						}
						future.channel().close();
					}
				});
			}

		} catch (Exception e) {
		}

		if (byteBuf == null) {
			logger.info("{},{},{},null", ctx.channel().attr(AttributeKey.valueOf("log")).get(), response.status().code(), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
		} else {
			logger.info("{},{},{}", ctx.channel().attr(AttributeKey.valueOf("log")).get(), response.status().code(), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
			//logger.info("{},{},{}",ctx.channel().attr(AttributeKey.valueOf("log")).get(),status.code(),response.content().readableBytes());
		}


		logger.info(String.format("elapsed Time in %.3fms%n", (System.nanoTime() - Request.startTime) / 1e6d));

	}


	public static void resource(ChannelHandlerContext ctx, FullHttpRequest request) {
		if (ApiConstants.STATIC_CACHE) {
			staticCache(ctx, request);
		} else {
			staticNoCache(ctx, request);
		}
	}


	private static void staticCache(ChannelHandlerContext ctx, FullHttpRequest request) {

		String ifModifiedSince = CommonUtils.nToB(request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE));

		if (!CommonUtils.isBlank(ifModifiedSince)) {
			try {
				SimpleDateFormat dateFormatter = new SimpleDateFormat(Response.HTTP_DATE_FORMAT, Locale.US);
				Date since = dateFormatter.parse(ifModifiedSince);
				long elapsed = (System.currentTimeMillis() - since.getTime()) / 1000;
				if (elapsed < HTTP_CACHE_SECONDS) {//사용자 브러우저에 캐쉬가 있는 경우 2분안에는 NOT_MODIFIED
					Response.notModified(ctx, request);
					return;
				}
			} catch (Exception e) {
			}
		}


		if (ApiConstants.staticMap.containsKey(request.uri())) {
			Response.successResource(ctx, request, ApiConstants.staticMap.put(request.uri(), ApiConstants.staticMap.getUnchecked(request.uri())));
			return;
		}

		String path = sanitizeUri(request.uri());
		logger.info("uri : {} , path : {}", request.uri(), path);
		File f = new File(path);
		byte[] data = null;
		if (f.exists()) {
			try {
				data = Files.readAllBytes(new File(path).toPath());
			} catch (Exception ex) {
			}
		}

		if (data == null) {
			Response.error(ctx, HttpResponseStatus.NOT_FOUND);
		} else {
			Response.successResource(ctx, request, ApiConstants.staticMap.put(request.uri(), data));
		}
		return;


	}


	private static void staticNoCache(ChannelHandlerContext ctx, FullHttpRequest request) {

		String path = sanitizeUri(request.uri());
		logger.info("uri : {} , path : {}", request.uri(), path);
		File f = new File(path);
		byte[] data = null;
		if (f.exists()) {
			try {
				data = Files.readAllBytes(new File(path).toPath());
			} catch (Exception ex) {
			}
		}

		if (data == null) {
			Response.error(ctx, HttpResponseStatus.NOT_FOUND);
		} else {
			Response.successResource(ctx, request, data);
		}
		return;


	}

	public static String sanitizeUri(String uri) {
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error(e);
		}
		if ((uri.isEmpty()) || (uri.charAt(0) != '/')) {
			return "";
		}
		uri = uri.replace('/', File.separatorChar);
		if ((uri.contains(File.separator + '.')) || (uri.contains('.' + File.separator)) ||
				(uri.charAt(0) == '.') || (uri.charAt(uri.length() - 1) == '.') || (INSECURE_URI.matcher(uri).matches())) {
			return "";
		}

		return ApiConfigLoader.env.webroot + uri;
	}

	public static void main(String[] args) {
		String s = "SERVICE_ID=glx_api&ORDER_ID=TRX22000435330D67F44%3A1&ORDER_DATE=20240725162448&SERVICE_CODE=0900&PAY_MESSAGE=";
		System.out.println(URIUtils.decode("MDg0NjAxMDAgICAgICBnbHhfYXBpICAgICAgICAgICAgIDA5MDB0ZUorTGxUa1FMM0Q3UllIWjBDYldPMjFMWFk2TzJBSmN4ZEF1SERGUnZmeFRNeU9RUlh1NURDM0FiWHVnWkVuTE9ERkRWS3Z2a1JyR1hSajNqWDBtT2tkTXZKaEI5TkhYcm83Q2J3b0lPSG5ZMFlZa3EyYksrS3BEeE05Qy9nNitIODlUeFNWd1AwVWtuZ3VUazZRV3Z5OWRITHRUS0RaeFJIL2pzYjZTK3dwQjN3ZTFSSEV1bU5JSlRlWDF6SDR5Vm14MWR3ekI2UlJiVnlXZFlIZ2t0ZWtoeHJiYnNqTDBoNkExaTBwMUk0R0l4eUl4UlNBR2N3c3o5KzlweEtzZm1meVp4NjFpSEQvZWZFQm1yZjFzcFA1OVFKNmtpcFk0WDJyZlVHK1luTUJadjlSM3pPdWlPTFZSTXJmanpibFd1MjY1UlQxSjhHV2RsMDN3cThhQVVFWUNTQ3lKWTkwS01qcUp1WUdNamlNNFFGSTRaeGxBMElBU3JMTkJvOHlIaG43WkRJZHJUaFc3K0NGS0s4QzI5QkJlOVZDN3BYdHJuMSt3OHZoM2s1d3ZlM1JqSHB2RVN4WVpZSlFtdlJqTXZZMTF6V1RDQitDUlNZMHRoMWJNRWFxQlpwbTBkcDFkeEFuenN1QmdjOFFlYm1KcVRlc1lVNkIzdHRaVjg4Ujg0STNzZFdVRDBkcmxpb21hS3Uwdis1TllEYm5QN0FseHNLbGFxbWNTc3UwOTlzYUkzNys1STkvenVVSHFSUGE3c3kzdXczUVAyZUkrTktub1pwbjFOTVBIQWNNbEczbVFYTjRBYXU2d2ZubmlJT21NblJGUFplT3pKMUI5VTgyWDA2ekFqS3Ywd2hldExpWFEwWG1xQVZ3UWZXNERQMzhXTmZSc1VWRDE5amtPcC9NT1YwNElTRnF0UUx6Tzd5RHVOSTJPQUhLQkRSTGNneWxudStHQlE5NkVPZGg0OU1nR2N4eEZBVTNDQTdlQWg0Z1NkanNjcFpXMW1PSU5jUHpocUwyeFZHUkllektLSVA4U2pxWXp1ZmY2U2RmWFJTc1h3a3ROWlpsQ2JNPQ%3D%3D"));
	}
}

