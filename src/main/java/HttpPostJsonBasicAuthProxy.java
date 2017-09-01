
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;


public final class HttpPostJsonBasicAuthProxy {

  private final String webServerName;
  private final int webServerPort;

  private final String webServerUser;
  private final String webServerPass;

  private final HttpHost httpProxy;


  private HttpPostJsonBasicAuthProxy(
                            final String webSrvrName, final int webSrvrPort,
                            final String webSrvrUser, final String webSrvrPass,
                            final HttpHost aHttpProxy
  ) {
    this.webServerName = webSrvrName;
    this.webServerPort = webSrvrPort;
    this.webServerUser = webSrvrUser;
    this.webServerPass = webSrvrPass;
    this.httpProxy = aHttpProxy;   // aHttpProxy may be null
  }


  private CloseableHttpClient createHttpClient() {
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(
            // NOTE:
            // the authentication realm should be a more specific string for the
            // web-server -but the authentc realm is dependent on the web-server
            new AuthScope(webServerName, webServerPort, AuthScope.ANY_REALM),
            new UsernamePasswordCredentials(webServerUser, webServerPass)
    );

    CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credsProvider)
            .build();

    return httpClient;
  }


  private HttpPost createHttpPostMethod(
                                      final String url, final String jsonReq,
                                      final boolean dumpRequestHeaders
  ) throws UnsupportedEncodingException {

    HttpPost httpPost = new HttpPost(url);

    RequestConfig config = RequestConfig.custom()
            .setAuthenticationEnabled(true)
            .setProxy(httpProxy)
            .build();
    httpPost.setConfig(config);

    httpPost.addHeader("content-type", "application/json");

    Base64 encoder = new Base64();
    String basicAuthCred = encoder.encodeAsString(
                              String.format("%s:%s", webServerUser,
                                                     webServerPass)
                                    .getBytes()
                           );
    httpPost.addHeader("Authorization", "Basic " + basicAuthCred);
    // System.err.println("Basic " + basicAuthCred);

    StringEntity jsonParams = new StringEntity(jsonReq);

    httpPost.setEntity(jsonParams);

    if (dumpRequestHeaders) {
      for (Header hdr: httpPost.getAllHeaders()) {
        String name = hdr.getName();
        String value = hdr.getValue();
        if (value != null) {
          System.out.println(String.format("%s:%s", name, value));
        } else {
          System.out.println(String.format("%s:", name));
        }
      }
      System.out.println();
    }

    return httpPost;
  }


  private void dumpHttpResponse(final CloseableHttpResponse httpResponse) {

    if (httpResponse == null) {
      return;
    }

    System.out.println(httpResponse.getStatusLine());

    for (Header hdr: httpResponse.getAllHeaders()) {
      String name = hdr.getName();
      String value = hdr.getValue();
      if (value != null) {
        System.out.println(String.format("%s:%s", name, value));
      } else {
        System.out.println(String.format("%s:", name));
      }
    }

    HttpEntity resEntity = httpResponse.getEntity();
    if (resEntity != null) {
      try {
        System.out.println(EntityUtils.toString(resEntity));
        EntityUtils.consume(resEntity);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    System.out.println();
  }


  public CloseableHttpResponse postJsonRequest(
                                    final String url, final String jsonRequest,
                                    final boolean dumpRequestHeaders
  ) {

    CloseableHttpClient httpClient = createHttpClient();

    HttpPost httpPost = null;
    try {
      httpPost = createHttpPostMethod(url, jsonRequest, dumpRequestHeaders);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      return null;
    }

    try {

      CloseableHttpResponse webServerResponse = httpClient.execute(httpPost);
      if (dumpRequestHeaders) {
        dumpHttpResponse(webServerResponse);
      }

      return webServerResponse;

    } catch (ClientProtocolException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }


  public static void main(final String[] args) throws Exception {

    // This is an example of using the client wrapper class, querying in this
    // case an HTTP NodeJS fake json-server
    // (https://github.com/typicode/json-server) through a TinyProxy HTTP
    // connect proxy, both in "localhost"
    String webProxyName = "localhost";
    int webProxyPort = 8888;  // default for TinyProxy
    String webProxyScheme = "http";

    HttpHost httpProxy = new HttpHost(webProxyName, webProxyPort,
                                      webProxyScheme);

    // httpProxy may be null, i.e., no HA-Proxy style http CONNECT proxy
    // httpProxy = null;

    // create an Apache wrapper object to query the NodeJS fake json-server
    // listening at port 3000
    HttpPostJsonBasicAuthProxy req = new HttpPostJsonBasicAuthProxy(
                                                        "localhost", 3000,
                                                        "username", "password",
                                                        httpProxy
                                         );

    long incrementalId = System.currentTimeMillis() / 1000 - 1;
    String jsonRequest;
    jsonRequest = String.format("{ \"id\": %d, \"title\": \"Post title at %d\", \"name\": \"your name\", \"comment\": \"a comment\" }",
                                incrementalId, incrementalId);
    System.out.println(jsonRequest);

    // true means: dump http headers nor response body
    req.postJsonRequest("http://127.0.0.1:3000/posts", jsonRequest, true);

    incrementalId = System.currentTimeMillis() / 1000;
    jsonRequest = String.format("{ \"id\": %d, \"title\": \"Second Post title at %d\", \"name\": \"another name\", \"comment\": \"a second comment\" }",
                                incrementalId, incrementalId);
    System.out.println(jsonRequest);

    // false means: don't dump http headers nor response body
    req.postJsonRequest("http://127.0.0.1:3000/posts", jsonRequest, false);

  }
}

