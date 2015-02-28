package com.github.thorqin.toolkit.web.utility;

import com.github.thorqin.toolkit.utility.MimeUtils;
import com.github.thorqin.toolkit.utility.Serializer;
import com.github.thorqin.toolkit.utility.UserAgentUtils;
import com.github.thorqin.toolkit.web.HttpException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Created by nuo.qin on 12/25/2014.
 */
public final class ServletUtils {
    private static Logger logger = Logger.getLogger(ServletUtils.class.getName());

    public static void setCrossSiteHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("P3P", "CP=CAO PSA OUR");
        response.setHeader("Access-Control-Allow-Methods",
                "GET,POST,PUT,DELETE,HEAD,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers",
                "Content-Type,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control");
    }

    public static String getURL(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder(request.getRequestURL());
        String queryString = request.getQueryString();
        if (queryString != null)
            sb.append("?").append(queryString);
        return sb.toString();
    }

    public static boolean supportGZipCompression(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("Accept-Encoding");
        while (headers.hasMoreElements()) {
            String[] value = headers.nextElement().split(",");
            for (String v : value) {
                if (v.trim().toLowerCase().equals("gzip"))
                    return true;
            }
        }
        return false;
    }

    public static String readHttpBody(HttpServletRequest request) {
        try {
            InputStream is = request.getInputStream();
            if (is != null) {
                Writer writer = new StringWriter();
                char[] buffer = new char[1024];
                try {
                    String encoding = request.getCharacterEncoding();
                    if (encoding == null) {
                        encoding = "UTF-8";
                    }
                    Reader reader = new BufferedReader(
                            new InputStreamReader(is, encoding));
                    int n;
                    while ((n = reader.read(buffer)) != -1) {
                        writer.write(buffer, 0, n);
                    }
                } catch (IOException ex) {
                    logger.log(Level.WARNING,
                            "Read http body failed: ", ex);
                }
                return writer.toString();
            } else {
                return "";
            }
        } catch (IOException e) {
            return "";
        }
    }

    public static void sendText(HttpServletResponse response, Integer status, String message, boolean compress) {
        response.setStatus(status);
        sendText(response, message, compress);
    }

    public static void sendText(HttpServletResponse response, Integer status, String message) {
        response.setStatus(status);
        sendText(response, message, false);
    }

    public static void sendText(HttpServletResponse response, Integer status, String message, String contentType, boolean compress) {
        response.setStatus(status);
        sendText(response, message, contentType, compress);
    }

    public static void sendText(HttpServletResponse response, Integer status, String message, String contentType) {
        response.setStatus(status);
        sendText(response, message, contentType, false);
    }

    public static void sendText(HttpServletResponse response, String message, String contentType) {
        sendText(response, message, contentType, false);
    }

    public static void sendText(HttpServletResponse response, String message, String contentType, boolean compress) {
        response.setContentType(contentType);
        response.setCharacterEncoding("utf-8");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-store");
        response.setDateHeader("Expires", 0);
        if (message == null)
            return;
        if (compress) {
            response.addHeader("Content-Encoding", "gzip");
            response.addHeader("Transfer-Encoding", "chunked");
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(response.getOutputStream())) {
                try (OutputStreamWriter writer = new OutputStreamWriter(gzipStream, "utf-8")) {
                    writer.write(message);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Send message to client failed!", ex);
            }
        } else {
            try (Writer w = response.getWriter()) {
                w.write(message);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Send message to client failed!", ex);
            }
        }
    }

    public static void sendText(HttpServletResponse response, String message, boolean compress) {
        sendText(response, message, "text/plain", compress);
    }

    public static void sendText(HttpServletResponse response, String message) {
        sendText(response, message, "text/plain", false);
    }

    public static void sendHtml(HttpServletResponse response, String html, boolean compress) {
        sendText(response, html, "text/html", compress);
    }

    public static void sendHtml(HttpServletResponse response, String html) {
        sendText(response, html, "text/html", false);
    }

    public static void sendHtml(HttpServletResponse response, Integer status, String html, boolean compress) {
        sendText(response, status, html, "text/html", compress);
    }

    public static void sendHtml(HttpServletResponse response, Integer status, String html) {
        sendText(response, status, html, "text/html", false);
    }

    public static void sendJsonString(HttpServletResponse response, Integer status, String jsonString, boolean compress) {
        response.setStatus(status);
        sendJsonString(response, jsonString, compress);
    }

    public static void sendJsonString(HttpServletResponse response, Integer status, String jsonString) {
        response.setStatus(status);
        sendJsonString(response, jsonString, false);
    }

    public static void sendJsonString(HttpServletResponse response, String jsonString, boolean compress) {
        sendText(response, jsonString, "application/json", compress);
    }

    public static void sendJsonString(HttpServletResponse response, String jsonString) {
        sendText(response, jsonString, "application/json", false);
    }

    public static void send(HttpServletResponse response, Integer status) {
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-store");
        response.setDateHeader("Expires", 0);
        response.setStatus(status);
    }

    public static void sendJsonObject(HttpServletResponse response, Integer status, Object obj, boolean compress) {
        response.setStatus(status);
        sendJsonObject(response, obj, compress);
    }

    public static void sendJsonObject(HttpServletResponse response, Integer status, Object obj) {
        response.setStatus(status);
        sendJsonObject(response, obj, false);
    }

    public static void sendJsonObject(HttpServletResponse response, Object obj) {
        sendJsonObject(response, obj, false);
    }

    public static void sendJsonObject(HttpServletResponse response, Object obj, boolean compress) {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-store");
        response.setDateHeader("Expires", 0);
        if (compress) {
            response.addHeader("Content-Encoding", "gzip");
            response.addHeader("Transfer-Encoding", "chunked");
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(response.getOutputStream())) {
                try (OutputStreamWriter writer = new OutputStreamWriter(gzipStream, "utf-8")) {
                    Serializer.toJson(obj, writer);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Send message to client failed!", ex);
            }
        } else {
            try (PrintWriter writer = response.getWriter()) {
                Serializer.toJson(obj, writer);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Send message to client failed!", ex);
            }
        }
    }

    public static void download(HttpServletRequest req,
                                HttpServletResponse resp,
                                File file,
                                String fileName) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            download(req, resp, inputStream, fileName, null);
        }
    }

    public static void download(HttpServletRequest req,
                                HttpServletResponse resp,
                                File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            download(req, resp, inputStream, file.getName(), null);
        }
    }

    public static void download(HttpServletRequest req,
                                HttpServletResponse resp,
                                File file,
                                String fileName, String mimeType) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            download(req, resp, inputStream, fileName, mimeType);
        }
    }

    public static void download(HttpServletRequest req,
                                HttpServletResponse resp,
                                InputStream inputStream,
                                String fileName, String mimeType) throws IOException {
        try {
            if (fileName != null) {
                fileName = URLEncoder.encode(fileName, "utf-8").replace("+", "%20");
            } else {
                fileName = "download.dat";
            }
        } catch (UnsupportedEncodingException e1) {
            fileName = "download.dat";
            e1.printStackTrace();
        }
        if (mimeType == null) {
            String extName = "";
            if (fileName.lastIndexOf(".") > 0)
                extName = fileName.substring(fileName.lastIndexOf(".") + 1);
            mimeType = MimeUtils.getFileMime(extName);
        }
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        resp.setContentType(mimeType);

        String userAgent = req.getHeader("User-Agent").toLowerCase();
        UserAgentUtils.UserAgentInfo uaInfo = UserAgentUtils.parse(userAgent);
        if (uaInfo.browser == UserAgentUtils.BrowserType.FIREFOX) {
            resp.addHeader("Content-Disposition", "attachment; filename*=\"utf-8''"
                    + fileName + "\"");
        } else {
            resp.addHeader("Content-Disposition", "attachment; filename=\""
                    + fileName + "\"");
        }
        try (OutputStream outputStream = resp.getOutputStream()) {
            int length;
            byte[] buffer = new byte[4096];
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
    }

    public static void error(Exception ex, Integer status) throws HttpException {
        Throwable e = ex;
        Throwable cause;
        while ((cause = e.getCause()) != null)
            e = cause;
        throw new HttpException(status, e.getMessage(), ex);
    }

    public static void error(Exception ex) throws HttpException {
        error(ex, 400);
    }
}
