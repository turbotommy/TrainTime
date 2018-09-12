package se.tomlab;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.file.FileSystems;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

import static java.util.Locale.ENGLISH;

/**
 * @author Crunchify.com
 *
 */
public class Main {
    private HttpClient client;
    private HttpClientContext context = HttpClientContext.create();
    private boolean bProxyNeeded=true;
    private Properties props=new Properties();
    private ZonedDateTime zdtSuggestedNextRun;
    private JSONArray jsonDelayed;
    private File fDelayedFile;
    private List<String> lTrain;
    //private JSONMap<String, JSONObject> jsonmDelayed;
    private JSONObject jsonoDelayed;
    private long lMinutesToWait;

    public Main() {
        fDelayedFile=new File("Delayed-"+ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)+".json");
        jsonDelayed=new JSONArray();
        //jsonmDelayed=new JSONMap<String, JSONObject>();
        jsonoDelayed=new JSONObject();

        if(fDelayedFile.exists()) {
            //Read latefile

            try {
                jsonoDelayed=getJSON(fDelayedFile);
            } catch (IOException e) {
                System.out.println("IOFel");
                e.printStackTrace();
            }
        }
    }

    private JSONObject getJSON(File f) throws IOException {
        InputStreamReader isr=new InputStreamReader(new FileInputStream(f));

        return getJSON(isr);
    }

    private JSONObject getJSON(InputStreamReader isr) throws IOException {
        BufferedReader br=new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        JSONObject jsonObject=new JSONObject(sb.toString());
        return jsonObject;
    }

    public JSONObject getJSON(String uri) throws Exception {

        HttpUriRequest request = new HttpGet(uri);
        HttpResponse httpResponse = client.execute(request, context);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        System.out.println("######################### " + statusCode);
        HttpEntity respEntity=httpResponse.getEntity();
        System.out.println("Stream:" + respEntity.isStreaming());

        return getJSON(new InputStreamReader(respEntity.getContent()));
    }

    public void createHttpClient(String secretKey) throws Exception {
        Mangle mangle;

        //Read properties
        File f;
        f = new File(String.valueOf(FileSystems.getDefault().getPath("params.props")));
        props.load(new FileInputStream(f));

        mangle=new Mangle();

        props=mangle.decryptMap(secretKey, props);

        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setUserAgent("TrainTime 1.1");
        CredentialsProvider provider = new BasicCredentialsProvider();

        if(bProxyNeeded) {
            String pHostmane=props.getProperty("phostname");
            int pPort= Integer.valueOf(props.getProperty("pport"));
            String pUser=props.getProperty("puser");
            String pPasswd=props.getProperty("ppasswd");

            HttpHost proxyHost=new HttpHost(pHostmane, pPort);
            httpClientBuilder.setProxy(proxyHost);
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(pUser, pPasswd);
            AuthScope scope = new AuthScope(pHostmane, pPort);

            provider.setCredentials(scope, credentials);

        }
        String hostName=props.getProperty("hostname");
        int port= Integer.valueOf(props.getProperty("port"));
        String user=props.getProperty("user");
        String passwd=props.getProperty("passwd");

        provider.setCredentials(
                new AuthScope(hostName, port),
                new UsernamePasswordCredentials(user, passwd));

        context.setCredentialsProvider(provider);

        client = httpClientBuilder.build();
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
        return getZonedDateTime(jsonObject,key,false);
    }

    private ZonedDateTime getZonedDateTime(JSONObject jsonObject, String key, boolean bCheckNextRun) {
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
        String[] whitelist= new String[]{"Västerås", "Stockholm", "Skövde,Örebro","Köping"};

        JSONArray jaTransfers= (JSONArray) obj;

        jaTransfers.forEach(transferObj -> {
            JSONObject joTransfer=(JSONObject)transferObj;
            boolean bInclude=false;

            long lDepartureMinutesLate=0;
            long lArrivalMinutesLate=0;

            String sTrainId=joTransfer.getString("train");
            String sOrigin=joTransfer.getString("origin");
            String sDestination=joTransfer.getString("destination");

            String sOtherTowns=sOrigin;
            if(sOtherTowns.length()==0)
                sOtherTowns=sDestination;

            //Filter
            for (String town:whitelist) {
                if(sOtherTowns.contains(town)) {
                    bInclude=true;
                    break;
                }
            }
            if(bInclude) {
                ZonedDateTime zdtNewArrival=getZonedDateTime(joTransfer, "newArrival",true);
                ZonedDateTime zdtArrival=getZonedDateTime(joTransfer, "arrival",true);

                CalculateNextRun(zdtArrival, zdtNewArrival, joTransfer);

                ZonedDateTime zdtNewDeparture=getZonedDateTime(joTransfer, "newDeparture");
                ZonedDateTime zdtDeparture=getZonedDateTime(joTransfer, "departure");

                StringBuilder sbOut=new StringBuilder(sStation);
                sbOut.append(", Tåg ");
                sbOut.append(sTrainId);

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

                    jsonDelayed.put(joTransfer);
                    //jsonmDelayed.putIfEmpty(sTrainId,joTransfer);
                    if(!jsonoDelayed.isNull(sTrainId)) {
                        jsonoDelayed.remove(sTrainId);
                    }
                    jsonoDelayed.put(sTrainId,joTransfer);
                    System.out.println(sbOut);
                }
                jaOut.put(joTransfer);
            }
            else {
                //System.out.println("Bort:"+joTransfer);
            }
        });
        return jaOut;
    }

    private void CalculateNextRun(ZonedDateTime zdtArrival, ZonedDateTime zdtNewArrival, JSONObject joTransfer) {
        //Check the earliest time.
        //Compare late time with ordinary

        ZonedDateTime zdt;
        if(zdtNewArrival!=null) {
            zdt=zdtNewArrival;
        } else {
            zdt=zdtArrival;
        }
        if(zdt==null) {
            return;
        }
        if(zdtSuggestedNextRun.isAfter(zdt)&&zdt.isAfter(ZonedDateTime.now())) {
            zdtSuggestedNextRun=zdt;
            System.out.println("New time "+joTransfer);
        }
    }

    public void AdjustNextRun() {
        ZonedDateTime zdtCurrentTime=ZonedDateTime.now();
        long lMinutesDiff=Duration.between(zdtCurrentTime,zdtSuggestedNextRun ).toMinutes();

        if(lMinutesDiff<=0) {
            zdtSuggestedNextRun=zdtCurrentTime.plusMinutes(2);
        } else if(lMinutesDiff<2) {
            zdtSuggestedNextRun=zdtSuggestedNextRun.plusMinutes(2);
        } else {
            zdtSuggestedNextRun=zdtSuggestedNextRun.minusMinutes(1);
        }
        lMinutesToWait=Duration.between(zdtCurrentTime,zdtSuggestedNextRun ).toMinutes();
    }

    private class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }

    private void ExecuteAtCmd() throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");

        //# Using the dedicated -f flag
        //$ at now + 1 minute -f script.sh

        ProcessBuilder builder = new ProcessBuilder();
        if (isWindows) {
            builder.command("cmd.exe", "/c", "dir");
        } else {
            builder.command("sh",
                    "-c", "ls");
        }

        builder.directory(new File(System.getProperty("user.home")));
        builder.redirectOutput();
        Process process = builder.start();

        BufferedReader br=new BufferedReader(new InputStreamReader(process.getInputStream()));
        //StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
        //Executors.newSingleThreadExecutor().submit(streamGobbler);
        StringBuffer sb=new StringBuffer();
        CharBuffer cb=CharBuffer.allocate(100);
        while(br.read()>0) {
            System.out.println(br.readLine());
        }
        int exitCode = process.waitFor();
        assert exitCode == 0;
    }

    private void SaveJSON(String sTown, JSONObject jsonObject, JSONArray jsonArray) {
        String sNow=ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":",".");
        try {
            FileWriter fw=new FileWriter(sTown+"-"+sNow+".json");
            jsonObject.write(fw);
            fw.close();
            fw=new FileWriter("Filter-"+sTown+"-"+sNow+".json");
            jsonArray.write(fw);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
        String string = "";
        try {
            if (args.length>1&&args[1].toLowerCase().contains("noproxy")) {
                main.bProxyNeeded=false;
            }

            //TODO: Argument for writing encrypted parameters
            //Mangle mangle=new Mangle();
            //String secret=mangle.encryptMap(new Properties());
            //mangle.MangleTest();

            /*
            System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
            System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "ERROR");
*/
            while (true) {
                main.zdtSuggestedNextRun = ZonedDateTime.of(2222,9,9,9,9,9,9,ZoneId.systemDefault());

                main.createHttpClient(args[0]);

                //Västerås
                System.out.println("Västerås");
                JSONObject joVas = main.getJSON("http://api.tagtider.net/v1/stations/314.json");

                //Remove all entries without delays
                JSONArray jaVasFiltered = main.getDelays("Västerås", joVas);
                main.SaveJSON("Västerås", joVas, jaVasFiltered);

                //Sthlm
                System.out.println("Stockholm");
                JSONObject joSthlm = main.getJSON("http://api.tagtider.net/v1/stations/243.json");

                //Remove all entries without delays
                JSONArray jaSthlmFiltered = main.getDelays("Stockholm", joSthlm);
                main.SaveJSON("Stockholm", joSthlm,jaSthlmFiltered);

                main.SaveDelayed();

                main.AdjustNextRun();

                System.out.println("Next run: " + main.zdtSuggestedNextRun + ", in " + main.lMinutesToWait+" minutes");
                //main.ExecuteAtCmd();

//                System.out.println(jaVasDelayed);
//                System.out.println(jaSthlmDelayed);
                Thread.sleep(main.lMinutesToWait * 60000);
            }
            } catch (Exception e) {
                System.out.println("\nNu blev det fel!");
                System.out.println(e);
                e.printStackTrace();
            }
    }

    private void SaveDelayed() {
        try {
            //Do not save if empty
            if(jsonoDelayed.length()>0) {
                FileWriter fw = new FileWriter(fDelayedFile);
                jsonoDelayed.write(fw);
                fw.close();
            }
        } catch (IOException e) {
            System.out.println("Fel vid filskrivning.");
            e.printStackTrace();
        }
    }
}