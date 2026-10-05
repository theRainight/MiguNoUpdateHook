package com.codex.migunoupdatehook102;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

public final class MiguNoUpdateModule extends XposedModule {
    private static final String TAG = "MiguNoUpdateHook102";
    private static final Set<String> TARGET_PACKAGES = setOf("cn.emagsoftware.gamehall", "com.migu.miguplay");
    private static final Set<String> URL_HOOKED_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Set<String> OKHTTP_HOOKED_LOADERS = ConcurrentHashMap.newKeySet();

    private String currentPackage = "";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        note("module loaded api=" + getApiVersion() + " process=" + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!TARGET_PACKAGES.contains(param.getPackageName())) {
            return;
        }
        currentPackage = param.getPackageName();
        installHooks(param.getDefaultClassLoader());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PACKAGES.contains(param.getPackageName())) {
            return;
        }
        currentPackage = param.getPackageName();
        installHooks(param.getClassLoader());
    }

    private void installHooks(ClassLoader appLoader) {
        try {
            installUrlOpenConnectionHooks();
            installHttpURLConnectionHooks();
            installClassLoaderHook();
            tryInstallOkHttpHooks(appLoader);
        } catch (Throwable t) {
        }
    }

    private void installUrlOpenConnectionHooks() throws NoSuchMethodException {
        String key = URL.class.getName();
        if (!URL_HOOKED_CLASSES.add(key + "#openConnection")) {
            return;
        }
        hook(URL.class.getDeclaredMethod("openConnection"))
                .setId(TAG + ":URL.openConnection")
                .intercept(chain -> {
                    URL url = (URL) chain.getThisObject();
                    String fake = fakeBody(url.toString());
                    if (fake != null) {
                        return new FakeHttpsURLConnection(url, fake);
                    }
                    return chain.proceed();
                });
        hook(URL.class.getDeclaredMethod("openConnection", Proxy.class))
                .setId(TAG + ":URL.openConnectionProxy")
                .intercept(chain -> {
                    URL url = (URL) chain.getThisObject();
                    String fake = fakeBody(url.toString());
                    if (fake != null) {
                        return new FakeHttpsURLConnection(url, fake);
                    }
                    return chain.proceed();
                });
    }

    private void installHttpURLConnectionHooks() {
        hookHttpClass(HttpURLConnection.class);
        hookHttpClass(HttpsURLConnection.class);
        hookNamedHttpClass("com.android.okhttp.internal.huc.HttpURLConnectionImpl");
        hookNamedHttpClass("com.android.okhttp.internal.huc.HttpsURLConnectionImpl");
    }

    private void hookNamedHttpClass(String className) {
        try {
            hookHttpClass(Class.forName(className));
        } catch (Throwable ignored) {
        }
    }

    private void hookHttpClass(Class<?> clazz) {
        if (!URL_HOOKED_CLASSES.add(clazz.getName())) {
            return;
        }
        hookNoArg(clazz, "getResponseCode", chain -> {
            String fake = fakeBodyFromConnection(chain.getThisObject());
            if (fake != null) {
                return 200;
            }
            return chain.proceed();
        });
        hookNoArg(clazz, "getResponseMessage", chain -> {
            String fake = fakeBodyFromConnection(chain.getThisObject());
            if (fake != null) {
                return "OK";
            }
            return chain.proceed();
        });
        hookNoArg(clazz, "getInputStream", chain -> {
            String fake = fakeBodyFromConnection(chain.getThisObject());
            if (fake != null) {
                return stream(fake);
            }
            return chain.proceed();
        });
        hookNoArg(clazz, "getErrorStream", chain -> {
            String fake = fakeBodyFromConnection(chain.getThisObject());
            if (fake != null) {
                return null;
            }
            return chain.proceed();
        });
        hookNoArg(clazz, "getContentLength", chain -> {
            String fake = fakeBodyFromConnection(chain.getThisObject());
            if (fake != null) {
                return bytes(fake).length;
            }
            return chain.proceed();
        });
        hookNoArg(clazz, "getContentLengthLong", chain -> {
            String fake = fakeBodyFromConnection(chain.getThisObject());
            if (fake != null) {
                return (long) bytes(fake).length;
            }
            return chain.proceed();
        });
        try {
            Method method = clazz.getMethod("getHeaderField", String.class);
            hook(method).setId(TAG + ":" + clazz.getName() + ".getHeaderField").intercept(chain -> {
                String fake = fakeBodyFromConnection(chain.getThisObject());
                if (fake != null) {
                    String name = (String) chain.getArg(0);
                    return headerValue(name, fake);
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {
        }
    }

    private void hookNoArg(Class<?> clazz, String name, XposedInterface.Hooker hooker) {
        try {
            Method method = clazz.getMethod(name);
            hook(method).setId(TAG + ":" + clazz.getName() + "." + name).intercept(hooker);
        } catch (Throwable ignored) {
        }
    }

    private void installClassLoaderHook() throws NoSuchMethodException {
        String key = ClassLoader.class.getName();
        if (!URL_HOOKED_CLASSES.add(key)) {
            return;
        }
        hook(ClassLoader.class.getDeclaredMethod("loadClass", String.class))
                .setId(TAG + ":ClassLoader.loadClass")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    afterClassLoaded(chain.getThisObject(), (String) chain.getArg(0), result);
                    return result;
                });
        hook(ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class))
                .setId(TAG + ":ClassLoader.loadClassResolve")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    afterClassLoaded(chain.getThisObject(), (String) chain.getArg(0), result);
                    return result;
                });
    }

    private void afterClassLoaded(Object loaderObject, String name, Object loadedClass) {
        if (!"okhttp3.RealCall".equals(name) || !(loaderObject instanceof ClassLoader) || !(loadedClass instanceof Class)) {
            return;
        }
        tryInstallOkHttpHooks((ClassLoader) loaderObject);
    }

    private void tryInstallOkHttpHooks(ClassLoader loader) {
        if (loader == null) {
            return;
        }
        String loaderKey = String.valueOf(System.identityHashCode(loader));
        if (!OKHTTP_HOOKED_LOADERS.add(loaderKey)) {
            return;
        }
        try {
            Class<?> realCall = Class.forName("okhttp3.RealCall", false, loader);
            Method execute = realCall.getDeclaredMethod("execute");
            hook(execute).setId(TAG + ":okhttp3.RealCall.execute").intercept(chain -> {
                Object call = chain.getThisObject();
                Object request = callRequest(call);
                    String url = requestUrl(request);
                    String fake = fakeBody(url);
                    if (fake != null) {
                        return buildOkHttpResponse(loader, request, fake);
                    }
                return chain.proceed();
            });
            try {
                Class<?> callback = Class.forName("okhttp3.Callback", false, loader);
                Method enqueue = realCall.getDeclaredMethod("enqueue", callback);
                hook(enqueue).setId(TAG + ":okhttp3.RealCall.enqueue").intercept(chain -> {
                    Object call = chain.getThisObject();
                    Object cb = chain.getArg(0);
                    Object request = callRequest(call);
                    String url = requestUrl(request);
                    String fake = fakeBody(url);
                    if (fake != null) {
                        Object response = buildOkHttpResponse(loader, request, fake);
                        Method onResponse = cb.getClass().getMethod("onResponse", Class.forName("okhttp3.Call", false, loader), Class.forName("okhttp3.Response", false, loader));
                        onResponse.invoke(cb, call, response);
                        return null;
                    }
                    return chain.proceed();
                });
            } catch (Throwable t) {
            }
        } catch (ClassNotFoundException ignored) {
            OKHTTP_HOOKED_LOADERS.remove(loaderKey);
        } catch (Throwable t) {
        }
    }

    private Object callRequest(Object call) throws Exception {
        Method request = call.getClass().getMethod("request");
        return request.invoke(call);
    }

    private String requestUrl(Object request) throws Exception {
        if (request == null) {
            return "";
        }
        Method url = request.getClass().getMethod("url");
        Object httpUrl = url.invoke(request);
        return String.valueOf(httpUrl);
    }

    private Object buildOkHttpResponse(ClassLoader loader, Object request, String body) throws Exception {
        Class<?> responseBuilderClass = Class.forName("okhttp3.Response$Builder", false, loader);
        Object builder = responseBuilderClass.getConstructor().newInstance();
        callBuilder(builder, "request", request.getClass(), request);
        Class<?> protocolClass = Class.forName("okhttp3.Protocol", false, loader);
        Object http11 = Enum.valueOf((Class<Enum>) protocolClass.asSubclass(Enum.class), "HTTP_1_1");
        callBuilder(builder, "protocol", protocolClass, http11);
        callBuilder(builder, "code", int.class, 200);
        callBuilder(builder, "message", String.class, "OK");
        Object responseBody = buildOkHttpBody(loader, body);
        callBuilder(builder, "body", Class.forName("okhttp3.ResponseBody", false, loader), responseBody);
        Method build = responseBuilderClass.getMethod("build");
        return build.invoke(builder);
    }

    private Object buildOkHttpBody(ClassLoader loader, String body) throws Exception {
        Class<?> responseBodyClass = Class.forName("okhttp3.ResponseBody", false, loader);
        Class<?> mediaTypeClass = Class.forName("okhttp3.MediaType", false, loader);
        Object mediaType = parseMediaType(mediaTypeClass, "application/json; charset=utf-8");
        try {
            Method create = responseBodyClass.getMethod("create", mediaTypeClass, String.class);
            return create.invoke(null, mediaType, body);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Method create = responseBodyClass.getMethod("create", String.class, mediaTypeClass);
            return create.invoke(null, body, mediaType);
        } catch (NoSuchMethodException ignored) {
        }
        Class<?> byteStringClass = Class.forName("okio.ByteString", false, loader);
        Method encodeUtf8 = byteStringClass.getMethod("encodeUtf8", String.class);
        Object byteString = encodeUtf8.invoke(null, body);
        Method create = responseBodyClass.getMethod("create", mediaTypeClass, byteStringClass);
        return create.invoke(null, mediaType, byteString);
    }

    private Object parseMediaType(Class<?> mediaTypeClass, String value) throws Exception {
        try {
            Method get = mediaTypeClass.getMethod("get", String.class);
            return get.invoke(null, value);
        } catch (NoSuchMethodException ignored) {
        }
        Method parse = mediaTypeClass.getMethod("parse", String.class);
        return parse.invoke(null, value);
    }

    private void callBuilder(Object builder, String name, Class<?> argClass, Object value) throws Exception {
        Method method = builder.getClass().getMethod(name, argClass);
        method.invoke(builder, value);
    }

    private String fakeBodyFromConnection(Object connection) {
        return fakeBody(urlOf(connection));
    }

    private String urlOf(Object connection) {
        try {
            Method getURL = connection.getClass().getMethod("getURL");
            Object url = getURL.invoke(connection);
            return String.valueOf(url);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String fakeBody(String url) {
        if (url == null) {
            return null;
        }
        String normalized = url.toLowerCase(Locale.ROOT);
        if (normalized.contains("betagame.migufun.com") && normalized.contains("/game/version/queryguideupgradeversion")) {
            return "{\"ResultCode\":\"-1\",\"Msg\":\"no update\",\"entry\":[]}";
        }
        if (normalized.contains("appipay.migu.cn:8443") && normalized.contains("/migusdk/verification/checkunionsdkupdate")) {
            return "{\"ResultCode\":\"-1\",\"Msg\":\"未查询到自升级记录\",\"entry\":[]}";
        }
        if (normalized.contains("freeserver.migufun.com") && normalized.contains("/resource/beta/package/pluggable.json")) {
            return "{\"version\":\"\",\"url\":\"\",\"forceUpdate\":false,\"upgrade\":false}";
        }
        return null;
    }

    private static String headerValue(String name, String body) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if ("content-type".equals(lower)) {
            return "application/json; charset=utf-8";
        }
        if ("content-length".equals(lower)) {
            return String.valueOf(bytes(body).length);
        }
        return null;
    }

    private static InputStream stream(String body) {
        return new ByteArrayInputStream(bytes(body));
    }

    private static byte[] bytes(String body) {
        try {
            return body.getBytes("UTF-8");
        } catch (Exception ignored) {
            return body.getBytes();
        }
    }

    private void note(String message) {
    }

    private void note(String message, Throwable throwable) {
    }

    private static Set<String> setOf(String... values) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        Collections.addAll(set, values);
        return set;
    }

    private static final class FakeHttpsURLConnection extends HttpsURLConnection {
        private final String body;

        FakeHttpsURLConnection(URL url, String body) {
            super(url);
            this.body = body;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() throws IOException {
            connected = true;
        }

        @Override
        public InputStream getInputStream() {
            return stream(body);
        }

        @Override
        public int getResponseCode() {
            return 200;
        }

        @Override
        public String getResponseMessage() {
            return "OK";
        }

        @Override
        public String getContentType() {
            return "application/json; charset=utf-8";
        }

        @Override
        public int getContentLength() {
            return bytes(body).length;
        }

        @Override
        public long getContentLengthLong() {
            return bytes(body).length;
        }

        @Override
        public String getHeaderField(String name) {
            return headerValue(name, body);
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return Collections.emptyMap();
        }

        @Override
        public void setRequestMethod(String method) throws ProtocolException {
            this.method = method;
        }

        @Override
        public String getCipherSuite() {
            return "TLS_FAKE_WITH_NULL_NULL";
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return null;
        }

        @Override
        public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
            return new Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() {
            return null;
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }
    }
}
