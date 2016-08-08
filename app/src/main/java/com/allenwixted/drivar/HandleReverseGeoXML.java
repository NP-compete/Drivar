package com.allenwixted.drivar;

import android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by allenwixted on 01/08/16.
 */
public class HandleReverseGeoXML {

    private String address = "Label";
    private String urlString = null;
    private XmlPullParserFactory XMLPPObj;
    public  volatile boolean parsingComplete = true;

    public HandleReverseGeoXML(String Url){
        this.urlString = Url;
    }

    public String getAddress(){
        return address;
    }

    public void parseAndStoreXML(XmlPullParser myParser){
        int event;
        String text = null;
        try{
            event = myParser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT){
                String name = myParser.getName();
                switch (event){
                    case XmlPullParser.START_TAG:
                        break;
                    case  XmlPullParser.TEXT:
                        text = myParser.getText();
                        break;
                    case  XmlPullParser.END_TAG:

                        if (name.equals("Label")){
                            address = text;
                            Log.i("XMLREADER", "addr " + address);
                        }
                        else {

                        }
                        break;
                }
                event = myParser.next();
            }
            parsingComplete = false;
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void fetchXML(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection connect = (HttpURLConnection) url.openConnection();
                    connect.setReadTimeout(10000);
                    connect.setConnectTimeout(15000);
                    connect.setRequestMethod("GET");
                    connect.setDoInput(true);
                    connect.connect();

                    InputStream inputStream = connect.getInputStream();

                    XMLPPObj = XmlPullParserFactory.newInstance();
                    XmlPullParser myparser = XMLPPObj.newPullParser();
                    myparser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    myparser.setInput(inputStream, null);
                    parseAndStoreXML(myparser);
                    inputStream.close();
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
}
