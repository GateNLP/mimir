/*
 *  WebUtils.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Dominic Rout 5 Apr 2017
 *  Valentin Tablan, 29 Jan 2010
 *
 *  $Id: WebUtils.java 17423 2014-02-26 10:36:54Z valyt $
 */
package gate.mimir.tool;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.SerializableEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

/**
 * A collection of methods that provide various utility functions for web
 * applications.
 */
public class WebUtils {

    protected PoolingHttpClientConnectionManager connectionManager;
    protected CredentialsProvider credsProvider;
    protected CloseableHttpClient client;
    protected boolean hasContext;
    protected CookieStore cookieJar;
    protected UsernamePasswordCredentials creds;


    public WebUtils() {
        this(null, null, null, 10);
    }

    public WebUtils(CookieStore cookieJar) {
        this(null, null, null, 10);
    }

    public WebUtils(String userName, String password) {
        this(null, userName, password, 10);
    }

    public WebUtils(CookieStore cookieJar,
                    String userName, String password) {
        this(cookieJar, userName, password, 10);

    }

    public WebUtils(CookieStore cookieJar,
                    String userName, String password, int maxConnections) {
        connectionManager = new PoolingHttpClientConnectionManager(60, TimeUnit.SECONDS);
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnections);

        SocketConfig.Builder socketConfigBuilder = connectionManager.getDefaultSocketConfig().custom();

        SocketConfig config = socketConfigBuilder.
                setSoReuseAddress(true).
                setSoKeepAlive(true).
                setSoLinger(0).
                build();
        connectionManager.setDefaultSocketConfig(config);

        this.cookieJar = cookieJar;


        hasContext = cookieJar != null || userName != null || password != null;

        credsProvider = new BasicCredentialsProvider();
        if (userName != null && password != null) {
            creds = new UsernamePasswordCredentials(userName, password);
        } else {
            creds = null;
        }

        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultSocketConfig(config);

        if (cookieJar != null) {
            RequestConfig globalConfig = RequestConfig.custom()
                    .setCookieSpec(CookieSpecs.DEFAULT)
                    .build();

            builder
                .setDefaultCookieStore(cookieJar)
                .setDefaultRequestConfig(globalConfig);
        }

        client = builder.build();
    }

    /**
     * Constructs a URL from a base URL segment and a set of query parameters.
     * @param urlBase the string that will be the prefix of the returned.
     * This should include everything apart from the query part of the URL.
     * @param params an array of String values, which should contain alternating
     * parameter names and parameter values. It is obvious that the size of this
     * array must be an even number.
     * @return a URl built according to the provided parameters. If for example
     * the following parameter values are provided: <b>urlBase:</b>
     * <tt>http://host:8080/appName/service</tt>; <b>params:</b> <tt>foo1, bar1,
     * foo2, bar2, foo3, bar3</tt>, then the following URL would be returned:
     * <tt>http://host:8080/appName/service?foo1=bar1&amp;foo2=bar2&amp;foo3=bar3</tt>
     */
    public static String buildUrl(String urlBase, String... params){
        StringBuilder str = new StringBuilder(urlBase);
        if(params != null && params.length > 0){
            str.append('?');
            for(int i = 0 ; i < (params.length/2) - 1; i++){
                str.append(params[i * 2]);
                str.append('=');
                str.append(params[i * 2 + 1]);
                str.append('&');
            }
            //and now, the last parameter
            str.append(params[params.length - 2]);
            str.append('=');
            str.append(params[params.length - 1]);
        }
        return str.toString();
    }


    protected HttpContext getContext() {
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        if (this.cookieJar != null) {
            context.setCookieStore(this.cookieJar);
        }

        return context;
    }

    public CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
        // If we have a context, we have to generate a new one for each request,
        // because sharing them between threads seems to break after a few hundred thousan
        // requests.
        if (hasContext) {
            // Fetch a context to use.
            HttpContext context = getContext();

            if (creds != null) {
                // Attach any credentials provided to the given host.
                credsProvider.setCredentials(
                        new AuthScope(request.getURI().getHost(),
                                AuthScope.ANY_PORT),
                        creds);
            }

            // Run the request.
            return this.client.execute(request, context);
        } else {
            // No cookies or auth needed - just run the request as is.
            return this.client.execute(request);
        }
    }

    /**
     * Calls a web service action (i.e. it connects to a URL). If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will write all content available from the
     * input stream of the resulting connection to the provided Appendable.
     *
     * @param out     an {@link Appendable} to which the output is written.
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
    public void getText(final Appendable out, String baseUrl, String... params)
            throws IOException {
        HttpGet request = new HttpGet(buildUrl(baseUrl, params));

        new RequestExecutor<Void>(this)
                .runRequest(request, response -> {
                    InputStream contentInputStream = response.getEntity().getContent();
                    try {
                        Reader r = new InputStreamReader(contentInputStream, "UTF-8");
                        char[] bufArray = new char[4096];
                        CharBuffer buf = CharBuffer.wrap(bufArray);
                        int charsRead = -1;
                        while ((charsRead = r.read(bufArray)) >= 0) {
                            buf.position(0);
                            buf.limit(charsRead);
                            out.append(buf);

                        }
                    } finally {
                        contentInputStream.close();
                    }
                    return null;
                });
    }

    /**
     * Calls a web service action (i.e. it connects to a URL). If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will write all content available from the
     * input stream of the resulting connection to a String and return it.
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
    public String getString(String baseUrl, String... params) throws IOException {
        StringBuffer resultBuffer = new StringBuffer();
        getText(resultBuffer, baseUrl, params);
        return resultBuffer.toString();
    }


    /**
     * Calls a web service action (i.e. it connects to a URL), and reads a
     * serialised int value from the resulting connection. If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all additional content available from
     * either the input and error streams of the resulting connection (which
     * should permit connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
    public int getInt(String baseUrl, String... params)
            throws IOException {
        HttpGet request = new HttpGet(buildUrl(baseUrl, params));

        return new RequestExecutor<Integer>(this)
                .runObjectRequest(request, (ObjectInputStream o) -> o.readInt());
    }

    /**
     * Calls a web service action (i.e. it connects to a URL). If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all content available from either the
     * input and error streams of the resulting connection (which should permit
     * connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
     public void getVoid(String baseUrl, String... params) throws IOException {
        HttpGet request = new HttpGet(buildUrl(baseUrl, params));

        new RequestExecutor<Void>(this)
                .runRequest(request, response -> null);
    }

    /**
     * Calls a web service action (i.e. it connects to a URL), and reads a
     * serialised long value from the resulting connection. If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all additional content available from
     * either the input and error streams of the resulting connection (which
     * should permit connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
     public long getLong(String baseUrl, String... params)
            throws IOException {
        HttpGet request = new HttpGet(buildUrl(baseUrl, params));

        return new RequestExecutor<Long>(this)
                .runObjectRequest(request, (ObjectInputStream o) -> o.readLong());
    }

    /**
     * Calls a web service action (i.e. it connects to a URL), and reads a
     * serialised double value from the resulting connection. If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all additional content available from
     * either the input and error streams of the resulting connection (which
     * should permit connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
     public double getDouble(String baseUrl, String... params)
            throws IOException {
        HttpGet request = new HttpGet(buildUrl(baseUrl, params));

        return new RequestExecutor<Double>(this)
                .runObjectRequest(request, (ObjectInputStream o) -> o.readDouble());
    }

    /**
     * Calls a web service action (i.e. it connects to a URL), and reads a
     * serialised boolean value from the resulting connection. If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all additional content available from
     * either the input and error streams of the resulting connection (which
     * should permit connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
    public boolean getBoolean(String baseUrl, String... params)
            throws IOException {
        HttpGet request = new HttpGet(buildUrl(baseUrl, params));

        return new RequestExecutor<Boolean>(this)
                .runObjectRequest(request, ObjectInputStream::readBoolean);
    }

    /**
     * Calls a web service action (i.e. it connects to a URL), and reads a
     * serialised Object value from the resulting connection. If the connection
     * fails, for whatever reason, or the response code is different from
     * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all additional content available from
     * either the input and error streams of the resulting connection (which
     * should permit connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException            if the connection fails.
     * @throws ClassNotFoundException if the value read from the remote connection
     *                                is of a type unknown to the local JVM.
     */
    public Object getObject(String baseUrl, String... params)
            throws IOException, ClassNotFoundException {
        HttpGet request = new HttpGet(buildUrl(baseUrl, params));

        try {
            return new RequestExecutor<>(this)
                    .runObjectRequest(request, ObjectInputStream::readObject);

        } catch (RuntimeException e) {
            if (e.getCause() instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    /**
     * Calls a web service action (i.e. it connects to a URL) using the POST HTTP
     * method, sending the given object in Java serialized format as the request
     * body.  The request is sent using chunked transfer encoding, and the
     * request's Content-Type is set to application/octet-stream.  If the
     * connection fails, for whatever reason, or the response code is different
     * from {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all content available from either the
     * input and error streams of the resulting connection (which should permit
     * connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param object  the object to serialize and send in the POST body
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
    public void postObject(String baseUrl, Serializable object,
                           String... params) throws IOException {
        HttpPost request = new HttpPost(buildUrl(baseUrl, params));

        request.setHeader("Content-Type", "application/octet-stream");
        // Set up the entity to send to the server.
        SerializableEntity entity = new SerializableEntity(object);
        entity.setChunked(true);
        request.setEntity(entity);

        // Now run the request
        new RequestExecutor<Void>(this)
                .runRequest(request, a -> null);
    }

    /**
     * Calls a web service action (i.e. it connects to a URL) using the POST HTTP
     * method, sending the given bytes as the request
     * body.  The request is sent using chunked transfer encoding, and the
     * request's Content-Type is set to application/octet-stream.  If the
     * connection fails, for whatever reason, or the response code is different
     * from {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * This method will drain (and discard) all content available from either the
     * input and error streams of the resulting connection (which should permit
     * connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param data    a {@link ByteArrayOutputStream} containing the data to be
     *                written. Its {@link ByteArrayOutputStream#writeTo(OutputStream)} method
     *                will be called causing it to write its data to the output connection.
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @throws IOException if the connection fails.
     */
    public void postData(String baseUrl, ByteArrayOutputStream data,
                         String... params) throws IOException {
        HttpPost request = new HttpPost(buildUrl(baseUrl, params));

        request.setHeader("Content-Type", "application/octet-stream");

        ByteArrayEntity entity = new ByteArrayEntity(data.toByteArray());
        entity.setChunked(true);
        request.setEntity(entity);

        new RequestExecutor<Void>(this)
                .runRequest(request, a -> null);
    }

    /**
     * Calls a web service action (i.e. it connects to a URL) using the POST HTTP
     * method, sending the given object in Java serialized format as the request
     * body.  The request is sent using chunked transfer encoding, and the
     * request's Content-Type is set to application/octet-stream.  If the
     * connection fails, for whatever reason, or the response code is different
     * from {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
     * The response from the server is read and Java-deserialized, the resulting
     * Object being returned.
     * <p>
     * This method will then drain (and discard) all the remaining content
     * available from either the input and error streams of the resulting
     * connection (which should permit connection keepalives).
     *
     * @param baseUrl the constant part of the URL to be accessed.
     * @param object  the object to serialize and send in the POST body
     * @param params  an array of String values, that contain an alternation of
     *                parameter name, and parameter values.
     * @return the de-serialized value sent by the remote endpoint.
     * @throws IOException            if the connection fails.
     * @throws ClassNotFoundException if the data sent from the remote endpoint
     *                                cannot be deserialized to a class locally known.
     */
    public Object rpcCall(String baseUrl, Serializable object,
                          String... params) throws IOException, ClassNotFoundException {
        HttpPost request = new HttpPost(buildUrl(baseUrl, params));
        request.setHeader("Content-Type", "application/octet-stream");
        // Set up the entity to send to the server.
        SerializableEntity entity = new SerializableEntity(object);
        entity.setChunked(true);
        request.setEntity(entity);

        // Now run the request
        return new RequestExecutor<>(this)
                .runObjectRequest(request, ObjectInputStream::readObject);
    }

    protected static class RequestExecutor<T> {
        private WebUtils webUtils;

        RequestExecutor(WebUtils webUtils) {
            this.webUtils = webUtils;
        }

        public T runRequest(HttpUriRequest request, CheckedRequestConsumer<T> consumer) throws IOException {
            CloseableHttpResponse response = webUtils.execute(request);
            try {
                long code = response.getStatusLine().getStatusCode();

                if (code == HttpURLConnection.HTTP_OK) {
                    // try to get more details
                    return consumer.run(response);
                } else {
                    // some problem -> try to get more details
                    String message = response.getStatusLine().getReasonPhrase();
                    throw new IOException(code
                            + (message != null ? " (" + message + ")" : "")
                            + " Remote connection failed.");
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                // make sure the connection is drained, to allow connection keepalive
                response.close();
            }
        }

        public T runObjectRequest(HttpUriRequest request, final CheckedObjectInputStreamConsumer<T> consumer) throws IOException {
            return runRequest(request, (CloseableHttpResponse response) -> {
                InputStream contentInputStream = null;
                try {
                    contentInputStream = response.getEntity().getContent();
                    return consumer.run(new ObjectInputStream(contentInputStream));
                } finally {
                    contentInputStream.close();
                }
            });
        }


        public interface CheckedRequestConsumer<T> {
            T run(CloseableHttpResponse response) throws IOException, ClassNotFoundException;
        }

        public interface CheckedObjectInputStreamConsumer<T> {
            T run(ObjectInputStream response) throws IOException, ClassNotFoundException;
        }
    }


}
