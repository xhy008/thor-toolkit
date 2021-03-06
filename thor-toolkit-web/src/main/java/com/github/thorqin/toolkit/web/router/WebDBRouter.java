package com.github.thorqin.toolkit.web.router;

import com.github.thorqin.toolkit.db.DBService;
import com.github.thorqin.toolkit.trace.Tracer;
import com.github.thorqin.toolkit.utility.ConfigManager;
import com.github.thorqin.toolkit.utility.Localization;
import com.github.thorqin.toolkit.utility.Serializer;
import com.github.thorqin.toolkit.utility.StringUtils;
import com.github.thorqin.toolkit.validation.ValidateException;
import com.github.thorqin.toolkit.web.WebApplication;
import com.github.thorqin.toolkit.web.annotation.DBRouter;
import com.github.thorqin.toolkit.web.session.SessionFactory;
import com.github.thorqin.toolkit.web.session.WebSession;
import com.github.thorqin.toolkit.web.utility.ServletUtils;
import com.google.common.base.Strings;
import com.google.gson.reflect.TypeToken;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Type;
import java.security.InvalidParameterException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by nuo.qin on 2/4/2015.
 */
public abstract class WebDBRouter extends WebRouterBase {
    private final Logger logger;

    public static class MappingInfo {
        public String procedure;
        public String[] parameters;
    }

    private SessionFactory sessionFactory = new SessionFactory();
    private final String refreshEntry;
    protected DBService db;
    protected final String indexProcedure;
    protected final String localeBundle;
    protected final String configName;
    protected Map<String, MappingInfo> mapping = new HashMap<>();
    private boolean enableGzip = true;
    protected String dbServiceName = null;
    private ConfigManager.ChangeListener changeListener = new ConfigManager.ChangeListener() {
        @Override
        public void onConfigChanged(ConfigManager configManager) {
            db = application.getService(dbServiceName);
            enableGzip = configManager.getBoolean("enableGZip", enableGzip);
            makeMapping();
        }
    };

    public WebDBRouter(final WebApplication application) throws ValidateException {
        super(application);
        if (application != null)
            logger = application.getLogger();
        else
            logger = Logger.getLogger(WebDBRouter.class.getName());

        try {
            final DBRouter dbRouter = this.getClass().getAnnotation(DBRouter.class);
            if (application == null)
                throw new InstantiationError("Parameter 'application' is null. " +
                        "Must use this router under web application environment.");
            enableGzip = application.getConfigManager().getBoolean("enableGZip", enableGzip);
            if (dbRouter == null)
                throw new InstantiationError("Must either provide @DBRouter annotation or use 5 parameters constructor.");
            dbServiceName = dbRouter.value();
            db = application.getService(dbRouter.value());
            indexProcedure = dbRouter.index();
            localeBundle = dbRouter.localeMessage();
            configName = dbRouter.configName();
            if (dbRouter.refreshEntry().isEmpty())
                refreshEntry = null;
            else
                refreshEntry = dbRouter.refreshEntry();
            application.getConfigManager().addChangeListener(changeListener);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Create WebDBRouter instance failed!", ex);
            throw new RuntimeException(ex);
        }
    }

    public WebDBRouter(DBService dbService, String indexProcedure, String localeBundle, String refreshEntry, String configName) throws ValidateException {
        super(null);
        logger = Logger.getLogger(WebDBRouter.class.getName());
        if (dbService == null)
            throw new InstantiationError("Parameter 'dbService' is null. ");
        db = dbService;
        this.indexProcedure = indexProcedure;
        this.configName = configName;
        this.localeBundle = localeBundle;
        if (refreshEntry == null || refreshEntry.isEmpty())
            this.refreshEntry = null;
        else
            this.refreshEntry = refreshEntry;
    }

    protected Map<String, List<String>> getRouterDefinition() throws Exception {
        if (!Strings.isNullOrEmpty(configName)) {
            Type valueType = new TypeToken<List<String>>() {}.getType();
            return application.getConfigManager().getMap(configName, valueType);
        }
        String json = db.invoke(indexProcedure, String.class);
        Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
        return Serializer.fromJson(json, type);
    }

    public void makeMapping() {
        try {
            mapping.clear();
            Map<String, List<String>> procedures = getRouterDefinition();
            ANALYSE_PROCEDURE:
            for (String key : procedures.keySet()) {
                if (key == null)
                    continue;
                String[] pair = key.split(":");
                String entryName;
                String spName;
                String method;
                if (pair.length == 2) {
                    method = pair[0];
                    spName = pair[1];
                    entryName = pair[1];
                    if (entryName.toLowerCase().startsWith(indexProcedure.toLowerCase()))
                        entryName = entryName.substring(indexProcedure.length());
                    entryName = StringUtils.underlineToCamel(entryName);
                } else if (pair.length == 3) {
                    method = pair[0];
                    spName = pair[1];
                    entryName = pair[2];
                } else {
                    logger.log(Level.WARNING, "Invalid procedure web entry define: " + key);
                    continue;
                }
                if (method == null) {
                    logger.log(Level.WARNING, "Invalid procedure web entry define: "
                            + key + ": invalid method.");
                    continue;
                }
                if (spName == null || spName.isEmpty()) {
                    logger.log(Level.WARNING, "Invalid procedure web entry define: "
                            + key + ": invalid procedure name.");
                    continue;
                }
                MappingInfo info = new MappingInfo();
                info.procedure = spName;
                List<String> parameters = procedures.get(key);
                Set<String> paramSet = new HashSet<>();
                for (String parameter: parameters) {
                    if (!parameter.matches("^(?i)query_string|request_body|request_header|" +
                            "session|status|response_header|response_content_type|response_body$")) {
                        logger.log(Level.WARNING, "Invalid procedure web entry define: "
                                + key + ": invalid parameter: " + parameter);
                        continue ANALYSE_PROCEDURE;
                    }
                    if (paramSet.contains(parameter.toLowerCase())) {
                        logger.log(Level.WARNING, "Invalid procedure web entry define: "
                                + key + ": duplicated parameter: " + parameter);
                        continue ANALYSE_PROCEDURE;
                    }
                    paramSet.add(parameter.toLowerCase());
                }
                if (parameters == null)
                    info.parameters = new String[]{};
                else {
                    info.parameters = parameters.toArray(new String[parameters.size()]);
                }
                String[] methods = method.split("\\|");
                if (methods.length < 1) {
                    logger.log(Level.WARNING, "Invalid procedure web entry define: "
                            + key + ": invalid method.");
                    continue;
                }
                for (String m: methods) {
                    if (m.matches("^(?i)POST|GET|PUT|DELETE$")) {
                        String k = m.toUpperCase() + ":" + entryName;
                        mapping.put(k, info);
                        System.out.println("Add DB Mapping: " + k);
                    } else {
                        logger.log(Level.WARNING, "Invalid procedure web entry define: " +
                                key + ": invalid method: " + m + ", ignored.");
                        continue;
                    }
                }

            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Generate database mapping failed.", ex);
        }
    }

    @Override
    public final void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            String sessionTypeName = config.getInitParameter("sessionClass");
            sessionFactory.setSessionType(sessionTypeName);
            makeMapping();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Initialize WebDBRouter failed!", ex);
            throw new ServletException(ex);
        }
    }

    @Override
    public final void destroy() {
        if (application != null)
            application.getConfigManager().removeChangeListener(changeListener);
        // Servlet destroy
        super.destroy();
    }

    private static enum RequestPostType {
        JSON,
        HTTP_FORM,
        UNKNOWN
    }

    private final static Pattern errorPattern = Pattern.compile("<http:(\\d{3})(?::(.+))?>");

    private boolean dispatch(HttpServletRequest request, HttpServletResponse response) {
        long beginTime = System.currentTimeMillis();

        String httpMethod = request.getMethod().toUpperCase();
        String requestPath = request.getServletPath();
        if (request.getPathInfo() != null) {
            requestPath += request.getPathInfo();
        }
        requestPath = requestPath.substring(requestPath.lastIndexOf("/") + 1);

        if (refreshEntry != null && requestPath.equals(refreshEntry)) {
            System.out.println("NOTE: Refresh db route table!!");
            makeMapping();
            ServletUtils.sendText(response, "Route table has been refreshed!");
            return true;
        }

        String key = httpMethod + ":" + requestPath;
        MappingInfo mappingInfo = mapping.get(key);
        if (mappingInfo == null)
            return false;

        WebSession session = null;
        try {
            RequestPostType postType;
            String httpBody = null;
            boolean postJson = (request.getContentType() != null &&
                    request.getContentType().split(";")[0].equalsIgnoreCase("application/json") ||
                    request.getContentType() == null &&
                            (request.getMethod().equalsIgnoreCase("POST") || request.getMethod().equalsIgnoreCase("PUT")));
            boolean postForm = (request.getContentType() != null
                    && request.getContentType().split(";")[0].equalsIgnoreCase("application/x-www-form-urlencoded"));
            if (postJson)
                postType = RequestPostType.JSON;
            else if (postForm)
                postType = RequestPostType.HTTP_FORM;
            else
                postType = RequestPostType.UNKNOWN;
            if (postType != RequestPostType.UNKNOWN)
                httpBody = ServletUtils.readHttpBody(request);

            Map<String, String> headers = null;
            session = sessionFactory.getSession(application, request, response);
            if (session != null && !session.isNew()) {
                session.touch();
            }

            DBService.DBRefString refSession = null;
            DBService.DBOutString outResponseHeader = null;
            DBService.DBOutString outResponse = null;
            DBService.DBOutString outResponseContentType = null;
            DBService.DBOutInteger outStatus = null;
            List<Object> parameters = new ArrayList<>(mappingInfo.parameters.length);
            for (String paramName : mappingInfo.parameters) {
                if (paramName.equalsIgnoreCase("request_body")) {
                    if (postType == RequestPostType.JSON) {
                        parameters.add(httpBody);
                    } else if (postType == RequestPostType.HTTP_FORM) {
                        parameters.add(Serializer.toJsonString(Serializer.fromUrlEncoding(httpBody)));
                    } else
                        parameters.add(null);
                } else if (paramName.equalsIgnoreCase("query_string")) {
                    String queryString = request.getQueryString();
                    if (queryString == null || queryString.isEmpty())
                        parameters.add(null);
                    else {
                        parameters.add(Serializer.toJsonString(Serializer.fromUrlEncoding(queryString)));
                    }
                } else if (paramName.equalsIgnoreCase("request_header")) {
                    if (headers == null) {
                        headers = new HashMap<>();
                        List<String> names = Collections.list(request.getHeaderNames());
                        for (String name : names) {
                            headers.put(name, request.getHeader(name));
                        }
                    }
                    parameters.add(Serializer.toJsonString(headers));
                } else if (paramName.equalsIgnoreCase("response_header")) {
                    outResponseHeader = new DBService.DBOutString();
                    parameters.add(outResponseHeader);
                } else if (paramName.equalsIgnoreCase("response_body")) {
                    outResponse = new DBService.DBOutString();
                    parameters.add(outResponse);
                } else if (paramName.equalsIgnoreCase("status")) {
                    outStatus = new DBService.DBOutInteger();
                    parameters.add(outStatus);
                } else if (paramName.equalsIgnoreCase("response_content_type")) {
                    outResponseContentType = new DBService.DBOutString();
                    parameters.add(outResponseContentType);
                } else if (paramName.equalsIgnoreCase("session")) {
                    refSession = new DBService.DBRefString(Serializer.toJsonString(session.getMap()));
                    parameters.add(refSession);
                } else {
                    throw new InvalidParameterException("Invalid DB entry parameter: " + paramName);
                }
            }
            // Call database stored procedure
            db.invoke(mappingInfo.procedure, parameters.toArray());

            int status = HttpServletResponse.SC_OK;
            if (outStatus != null && outStatus.getValue() != null) {
                status = outStatus.getValue();
            }

            if (refSession != null && refSession.getValue() != null && session != null) {
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> map = Serializer.fromJson(refSession.getValue(), type);
                session.clear();
                for (String sessionKey : map.keySet()) {
                    session.set(sessionKey, map.get(sessionKey));
                }
            }
            if (outResponseHeader != null && outResponseHeader.getValue() != null) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> map = Serializer.fromJson(outResponseHeader.getValue(), type);
                for (String headerKey : map.keySet()) {
                    response.setHeader(headerKey, map.get(headerKey));
                }
            }
            if (session != null && !session.isSaved() && !session.isNew())
                session.save();

            String contentType;
            if (outResponseContentType != null && outResponseContentType.getValue() != null) {
                contentType = outResponseContentType.getValue();
            } else {
                contentType = "application/json";
            }
            String responseString;
            if (outResponse != null && outResponse.getValue() != null) {
                responseString = outResponse.getValue();
                boolean supportGzip = ServletUtils.supportGZipCompression(request);
                supportGzip = (enableGzip && supportGzip);
                ServletUtils.sendText(response, status, responseString, contentType, supportGzip);
            } else {
                ServletUtils.send(response, status);
            }
        } catch (SQLException ex) {
            if (session != null && !session.isSaved() && !session.isNew())
                session.save();
            if (ex.getMessage() != null) {
                Matcher matcher = errorPattern.matcher(ex.getMessage());
                if (matcher.find()) {
                    int status = Integer.valueOf(matcher.group(1));
                    String msg = matcher.group(2);
                    if (msg == null)
                        ServletUtils.send(response, status);
                    else {
                        Object lang = session.get("lang");
                        String language;
                        if (lang != null && lang.getClass().equals(String.class)) {
                            language = (String)lang;
                        } else
                            language = request.getHeader("Accept-Language");
                        ServletUtils.sendText(response, status,
                                Localization.get(localeBundle, language, msg));
                    }
                    logger.log(Level.WARNING, "Return HTTP error: " + ex.getMessage());
                } else {
                    ServletUtils.sendText(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error!");
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            } else {
                ServletUtils.sendText(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error!");
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        } catch (Exception ex) {
            if (session != null && !session.isSaved() && !session.isNew())
                session.save();
            ServletUtils.sendText(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error!");
            logger.log(Level.SEVERE, "Error processing!!", ex);
        } finally {
            if (application != null && application.getSetting().traceRouter) {
                Tracer.Info traceInfo = new Tracer.Info();
                traceInfo.catalog = "db_router";
                traceInfo.name = "access";
                traceInfo.put("url", ServletUtils.getURL(request));
                traceInfo.put("method", httpMethod);
                traceInfo.put("clientIP", request.getRemoteAddr());
                traceInfo.put("startTime", beginTime);
                traceInfo.put("runningTime", System.currentTimeMillis() - beginTime);
                application.trace(traceInfo);
            }
        }
        return true;
    }

    @Override
    protected final void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!dispatch(req, resp)) {
            super.service(req, resp);
        }
    }
}
