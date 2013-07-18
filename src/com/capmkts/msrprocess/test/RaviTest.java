package com.capmkts.msrprocess.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RaviTest {
	
	public static void main(String[] args) throws Exception{
//		System.out.println(org.hibernate.Version.getVersionString());
//		String URL = "jdbc:sqlserver://localhost:1433;databaseName=master;"; 
//
//		String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver"; 
//
//		String ID = "tester"; 
//
//		String PW = "Passw0rd"; 
//
//		// This is where to load the driver 
//		Class.forName (DRIVER); 
//		// This is where to connect 
//		Connection link = DriverManager.getConnection(URL, ID, PW); 
//		// In my case, I'm connecting to the URL, ID and PW where you are connecting the driver.
//		
//		System.out.println(" Got the connection "+link);
		
//		Session session = HibernateUtil.getSession();
//		Query query = session.createQuery("select count(*) from Holiday where holidayDate = :compareDate");
//		List list = query.list();
//		int count = Integer.parseInt(list.get(0).toString());
//		System.out.println("COUNT: " + count);
		
		BigDecimal bd = null;
		String x = null;
		Double test = 3.4543542E7;
		try{
			bd = new BigDecimal(test);
		}catch(Exception e){
			System.out.println(e);
			bd = new BigDecimal("0");
		}
		x = bd.toPlainString();
		System.out.println(test);
		System.out.println(bd);
		System.out.println(x);

		String _value = "5.3232E6";
		for (int k=1; k<10; k++){
			if (_value.startsWith(k+".") && _value.contains("E")){
    			BigDecimal bdv = new BigDecimal(_value);
    			_value = bdv.toPlainString();
    			break;
    		}
		}
		System.out.println(_value);
		
		String xml = postRequest("","http://localhost:1433/cmc-data-mapping-ws/api/v1?accessToken=2z8mjkxl3hid94re");
		System.out.println("XML: " +xml);
	}
	
	// POST the XML string as text/xml  via HTTPS
	public static String postRequest(String strRequest, String strURL ) throws Exception {
	    String responseXML = null;

	    try{
	        URL url = new URL(strURL);
	        URLConnection connection = url.openConnection();
	        HttpURLConnection httpConn = (HttpURLConnection) connection;

	        byte[] requestXML = strRequest.getBytes();

	        // Set the appropriate HTTP parameters.
	    httpConn.setRequestProperty( "Content-Length", String.valueOf( requestXML.length ) );
	        httpConn.setRequestProperty("Content-Type","text/xml; charset=utf-8");
	        httpConn.setRequestMethod( "POST" );
	    httpConn.setDoOutput(true);
	      httpConn.setDoInput(true);

	      // Send the String that was read into postByte.
	      OutputStream out = httpConn.getOutputStream();
	      out.write(requestXML);
	      out.close();

	      // Read the response and write it to standard out.
	      InputStreamReader isr = new InputStreamReader(httpConn.getInputStream());
	      BufferedReader br = new BufferedReader(isr);
	      String temp;
	      String tempResponse = "";

	      //Create a string using response from web services
	        while ((temp = br.readLine()) != null) {
				tempResponse = tempResponse + temp;
			}
	        responseXML = tempResponse;
	        br.close();
	        isr.close();
	    } catch (java.net.MalformedURLException e) {
	    System.out.println("Error in postRequest(): Secure Service Required");
	   } catch (Exception e) {
	    System.out.println("Error in postRequest(): " + e.getMessage());
	   }
	   return responseXML;
	}
	
	public static int parseInteger(String value){
		try {
			if (!value.isEmpty()){
				return Integer.parseInt(value);
			}
			else{
				return new Integer(0);
			}
		}catch(Exception e){
			return new Integer(0);
		}
	}
	
private static String[] checkMailingAddress(String[] recordArray) {
		
		System.out.println("**** Checking Mailing Address ****");
		String[] dataArray;
		String[] arrayBuilder = new String[recordArray.length];
		
		String propStreetAddress="", propCity="", propState="", propZip = "";
		
		//Copy header
		arrayBuilder[0] = recordArray[0];
		//Remove leading null value
		if (arrayBuilder[1] == null){
			arrayBuilder[1] = "";
		}

		for (int i = 1; i < recordArray.length; i++) {
			dataArray = recordArray[i].split("\\,");
			
			for (int j=0; j<dataArray.length; j++){
				switch (j){
					case 81:
						propStreetAddress = dataArray[j];
						System.out.println("Prop Street: " + propStreetAddress);
						break;
					case 82:
						propCity = dataArray[j];
						break;
					case 83:
						propState = dataArray[j];
						break;
					case 84:
						propZip = dataArray[j];
						break;
					case 86:
						if (dataArray[86].isEmpty()){
							dataArray[86] = propStreetAddress;
							dataArray[87] = propCity;
							dataArray[88] = propState;
							dataArray[89] = propZip;
						}
						break;		
				}
				
				arrayBuilder[i] += dataArray[j]+",";
				System.out.println(arrayBuilder[i]);
			}
			arrayBuilder[i] += "\n";
		}
		
		String[]  temp = arrayBuilder[1].split("\\,");
		System.out.println("F86:  " + temp[89]);
		System.out.println("**** Checking Mailing Address Complete! ****");
		return arrayBuilder;
	}

	public static Date parseDate(String dateToParse){
		Date date = null;
		try {
			date = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.ENGLISH).parse(dateToParse);
		} catch (ParseException e) {
			System.out.println("Invalid Date: " +dateToParse);
		}
		
		return date;
	}
	
	public static String arrayToString(String[] content){
		String tmpStr = "";
		for (int i=0; i<content.length; i++){
			tmpStr += content[i] + " ";
		}
		return tmpStr;
	}

}
