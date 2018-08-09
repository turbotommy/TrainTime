package se.tomlab;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystems;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONString;

import static java.util.Locale.*;

/**
 * @author Crunchify.com
 *
 */

public class Main {
    private HttpClient client;
    private HttpClientContext context = HttpClientContext.create();
    private boolean bProxyNeeded=true;
    private Properties props=new Properties();

    public JSONObject getJSON(String uri) throws Exception {

        HttpUriRequest request = new HttpGet(uri);
        HttpResponse httpResponse = client.execute(request, context);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        System.out.println("######################### " + statusCode);
        HttpEntity respEntity=httpResponse.getEntity();
        System.out.println("Stream:" + respEntity.isStreaming());
        BufferedReader br=new BufferedReader(new InputStreamReader(respEntity.getContent()));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        JSONObject jsonObject=new JSONObject(sb.toString());
        return jsonObject;
    }

    public void createHttpClient() throws Exception {
        String proxyHostName="";
        int proxyPort=0;
        String user="";
        String passwd="";

        //Read properties
        File f;
        f = new File(String.valueOf(FileSystems.getDefault().getPath("test.properties")));
        props.load(new FileInputStream(f));

        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setUserAgent("TrainTime 1.1");
        if(bProxyNeeded) {
            HttpHost proxyHost=new HttpHost(proxyHostName, proxyPort);
            httpClientBuilder.setProxy(proxyHost);
            proxyAuthenticate(user,passwd);
        }
        client = httpClientBuilder.build();
    }

    private void proxyAuthenticate(String username, String password) {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
        AuthScope scope = new AuthScope("proxy.lfnet.se", 8080);

        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(scope, credentials);

        provider.setCredentials(
                new AuthScope("api.tagtider.net", 80),
                new UsernamePasswordCredentials("tagtider", "codemocracy"));

        context.setCredentialsProvider(provider);
    }

    private Date getDate(JSONObject jsonObject, String key) {
        Date dt=null;
        try {
            String s=jsonObject.optString(key, null);
            if(s!=null) {
                DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", ENGLISH);
                dt = format.parse(s);
            }
        } catch (Exception e){
            System.out.println(e.getLocalizedMessage());
            int i=e.getCause().hashCode();
        }
        return dt;
    }

    private ZonedDateTime getZonedDateTime(JSONObject jsonObject, String key) {
        ZonedDateTime zdt=null;
        try {
            String s=jsonObject.optString(key, null);
            if(!(s==null||s.startsWith("0000-"))) {
                DateTimeFormatter dtf=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
                LocalDateTime ldt=LocalDateTime.parse(s,dtf);
                zdt=ldt.atZone(ZoneId.systemDefault());
            }
        } catch (Exception e){
            System.out.println(e.getLocalizedMessage());
            int i=e.getCause().hashCode();
        }
        return zdt;
    }

    private JSONArray getDelays(String sStation, JSONObject joIn) {
        JSONArray jaOut=new JSONArray();
        JSONPointer jp=new JSONPointer("/station/transfers/transfer");
        Object obj= jp.queryFrom(joIn);

        JSONArray jaTransfers= (JSONArray) obj;

        jaTransfers.forEach(transferObj -> {
            JSONObject joTransfer=(JSONObject)transferObj;

            long lDepartureMinutesLate=0;
            long lArrivalMinutesLate=0;

            ZonedDateTime zdtNewArrival=getZonedDateTime(joTransfer, "newArrival");
            ZonedDateTime zdtArrival=getZonedDateTime(joTransfer, "arrival");
            ZonedDateTime zdtNewDeparture=getZonedDateTime(joTransfer, "newDeparture");
            ZonedDateTime zdtDeparture=getZonedDateTime(joTransfer, "departure");

            StringBuilder sbOut=new StringBuilder(sStation);
            sbOut.append(", Tåg");

            String sOrigin=joTransfer.getString("origin");
            String sDestination=joTransfer.getString("destination");

            if(sOrigin.length()>0) {
                sbOut.append(" från ");
                sbOut.append(sOrigin);
            }

            if(sDestination.length()>0) {
                sbOut.append(" till ");
                sbOut.append(sDestination);
            }

            if(zdtNewArrival != null) {
                //How late is it?
                lArrivalMinutesLate= Duration.between(zdtArrival, zdtNewArrival).toMinutes();
                joTransfer.put("arrivalminuteslate", lArrivalMinutesLate);

                sbOut.append(", ankommer ");
                sbOut.append(zdtNewArrival.toLocalTime());
                sbOut.append(", ");
                sbOut.append(lArrivalMinutesLate);
                sbOut.append(" min sent");
            }

            if(zdtNewDeparture != null) {
                //How late is it?
                lDepartureMinutesLate= Duration.between(zdtDeparture, zdtNewDeparture).toMinutes();
                joTransfer.put("departureminuteslate", lDepartureMinutesLate);

                sbOut.append(", avgår ");
                sbOut.append(zdtNewDeparture.toLocalTime());
                sbOut.append(", ");
                sbOut.append(lDepartureMinutesLate);
                sbOut.append(" min sent");
            }

            if(zdtNewDeparture != null | zdtNewArrival != null) {
                Object oComment=joTransfer.optJSONObject("comment");
                if(oComment != null) {
                    sbOut.append(", ");
                    sbOut.append(oComment);
                }
                jaOut.put(joTransfer);
                System.out.println(sbOut);
            }
        });

        return jaOut;

    }

    public static void main(String[] args) {
        Main main = new Main();
        String string = "";
        try {
            /*
            System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
            System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "ERROR");
*/
            main.createHttpClient();

            //Västerås
            System.out.println("Västerås");
            JSONObject joVas = main.getJSON("http://api.tagtider.net/v1/stations/314.json");

            //Remove all entries without delays
            JSONArray jaVasDelayed=main.getDelays("Västerås", joVas);

            //Sthlm
            System.out.println("Stockholm");
            JSONObject joSthlm= main.getJSON("http://api.tagtider.net/v1/stations/243.json");

            //Remove all entries without delays
            JSONArray jaSthlmDelayed=main.getDelays("Stockholm", joSthlm);

            System.out.println(joSthlm);
            } catch (Exception e) {
                System.out.println("\nNu blev det fel!");
                System.out.println(e);
            }

    }
}