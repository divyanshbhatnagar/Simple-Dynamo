package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SimpleDynamoProvider extends ContentProvider {

	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value" ;
	public static String successor;
	public static String predecessor;
	public static ArrayList<String> dhtArr = new ArrayList<String>();
	public ArrayList<String> listOfNodes = listCreate();
	public static String myPortstr;
	public static String origin;
	public static String msgType;
	private static Boolean lockSingleQuery = true;
	private static Boolean lockAllQuery = true;
	private static  Boolean lockInsert = true;
	private static HashMap<String, String> returnHaspMap = new HashMap<String, String>();
	public static final String port0 = "11108";
	public Map<String, String> portMap = mapCreate();
	public static String portStr;
	public static  Map<String, String> queryResult = new HashMap<String, String>();

	private ArrayList<String> listCreate(){
		ArrayList<String> listNodes = new ArrayList<String>();

		try {
			listNodes.add(0, genHash("5562"));
			listNodes.add(1, genHash("5556"));
			listNodes.add(2, genHash("5554"));
			listNodes.add(3, genHash("5558"));
			listNodes.add(4, genHash("5560"));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		Collections.sort(listNodes);
		return listNodes;
	}

	private Map<String, String> mapCreate() {
		Map<String, String> mapFinal = new HashMap<String, String>();
		try {
			mapFinal.put(genHash("5554"), "11108");
			mapFinal.put(genHash("5556"), "11112");
			mapFinal.put(genHash("5558"), "11116");
			mapFinal.put(genHash("5560"), "11120");
			mapFinal.put(genHash("5562"), "11124");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return mapFinal;
	}

	public static String portId[] = {"5554", "5556", "5558", "5560", "5562"};


	static final String TAG = SimpleDynamoProvider.class.getSimpleName();
	static final String REMOTE_PORT[] = {"11108", "11112", "11116", "11120", "11124"};
	static final int SERVER_PORT = 10000;


	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub


		String[] check = checkPosition(selection);
		String dest = check[0];
		String destSuc = check[1];
		String secondSuc = check[2];

		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "","","delete",myPortstr,portMap.get(dest),selection,"");

		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "","","delete",myPortstr,portMap.get(destSuc),selection,"");

		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "","","delete",myPortstr,portMap.get(secondSuc),selection,"");

		try {
			TimeUnit.MILLISECONDS.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		String key = values.get(KEY_FIELD).toString();
		String value = values.get(VALUE_FIELD).toString();
		Log.v("Insert Request:  ", "value: " + value + " key:  " + key + " in port " + portMap.get(myPortstr));
		//Log.v("TRIO", predecessor + ":" + myPortstr + ":" + successor);
		String[] nodes = checkPosition(key);
		String condition = nodes[0];
		String secondSucc = nodes[2];
		String succ = nodes[1];



		msgType = "insert";
		Log.v("Insert Forwarded:  ", "value: " + value + " key:  " + key + "to port " + portMap.get(condition));
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, successor, predecessor, msgType, origin, portMap.get(condition), key, value);




		msgType = "replication";

		Log.v(" Also inserting in :  ",      "value: " + value + " key:  " + key + " in port " + portMap.get(succ));
		MessageAction(successor, predecessor, msgType, origin, portMap.get(succ), key, value);

		Log.v("And inserting in :  ","value: " + value + " key:  " + key + " in port " + portMap.get(secondSucc));
		MessageAction(successor, predecessor, msgType, origin, portMap.get(secondSucc), key, value);



		return uri;
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub

		// Networking hack from PA1
		TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
		portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		try {
			myPortstr = genHash(portStr);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		try {

			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		} catch (IOException e) {

			Log.e(TAG, "Can't create a ServerSocket");

		}



			failureRecovery(myPortstr);


		origin = myPortstr;
		if(portStr.equals("5560"))
		{
			successor = listOfNodes.get(0);
		}
		else {
			successor = listOfNodes.get(listOfNodes.indexOf(myPortstr) + 1);
		}
		if(portStr.equals("5562"))
		{
			predecessor = listOfNodes.get(4);
		}
		else {
			predecessor = listOfNodes.get(listOfNodes.indexOf(myPortstr) - 1);
		}


		return false;
	}


	private void failureRecovery(String port){

		Log.v("In failure recovery: ", " failed node received " + portMap.get(port));
		int ind = listOfNodes.indexOf(port);
		//Log.v("Calling recovery for: ", " counter " +  + " sending to " + portMap.get(listOfNodes.get(i)) + " sending from " + portMap.get(myPortstr));

			for (int i = 0; i<listOfNodes.size();i++) {
				if (i != ind) {
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "", "", "recovery", myPortstr, portMap.get(listOfNodes.get(i)), "", "");
				}
			}



	}


	private String queryAllMessage(String succ, String pred, String type, String SrcOrigin, String dest, String key, String value, HashMap<String, String> hMap) {
		try {
			Log.v("In query all: ", " from " + portMap.get(SrcOrigin) + "   sending to  " + dest + "  msg type " + type);
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(dest));

			Message mg = new Message(succ, pred, type, SrcOrigin, dest, key, value, hMap);
			ObjectOutputStream obj = new ObjectOutputStream(socket.getOutputStream());
			obj.writeObject(mg);
			ObjectInputStream objRead = new ObjectInputStream(socket.getInputStream());
			Message message = (Message) objRead.readObject();
		} catch (UnknownHostException e) {
			Log.e(TAG, "UnknownHost  " + e);
		} catch (SocketTimeoutException e){
			Log.e(TAG, "SocketTimedOut  " + e);
		}
		catch (IOException e) {
			Log.e(TAG, "IOException  " + e);
		} catch (ClassNotFoundException e) {
			Log.e(TAG, "ClassNotFound  " + e);
		}

		return "Successfully forwarded";
	}

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {


		@Override
		protected Void doInBackground(ServerSocket... sockets) {

			String authority = "edu.buffalo.cse.cse486586.simpledynamo.provider";
			String scheme = "content";

			Uri.Builder uriBuilder = new Uri.Builder();
			uriBuilder.authority(authority);
			uriBuilder.scheme(scheme);
			Uri providerUri = uriBuilder.build();
			try {
				ServerSocket serverSocket = sockets[0];


            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
				//int keyToPut = 0;
				while (true) {

					Socket socket = serverSocket.accept();
					ObjectInputStream objRead = new ObjectInputStream(socket.getInputStream());
					try {
						Message msg = (Message) objRead.readObject();
						switch (msg.mType) {
							case "insert":
								Log.v("IRequest from:  ", portMap.get(msg.origin) + " key:  " + msg.key + " value " + msg.value);
								String[] nodes = checkPosition(msg.key);
								String condition = nodes[0];
								String secondSucc = nodes[2];
								String succ = nodes[1];
								if (condition.equals(myPortstr)) {

									Log.v("Inserted Remotely:  ", "value: " + msg.value + " key:  " + msg.key + " in port " + portMap.get(myPortstr) + " fromn port " + portMap.get(msg.origin));
									SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
									SharedPreferences.Editor editor = sharedPref.edit();
									editor.putString(msg.key, msg.value);
									editor.commit();


								} else {
									msgType = "insert";
									Log.v("Forwarded Remotely:  ", "value: " + msg.value + " key:  " + msg.key + " to port " + portMap.get(condition) + " fromn port " + portMap.get(msg.origin));
									MessageAction(successor, predecessor, msgType, origin, portMap.get(condition), msg.key, msg.value);



									msgType = "replication";

									Log.v(" Also inserting in :  ",      "value: " + msg.value + " key:  " + msg.key + " in port " + portMap.get(succ));
									MessageAction(successor, predecessor, msgType, origin, portMap.get(succ), msg.key, msg.value);

									Log.v("And inserting in :  ","value: " + msg.value + " key:  " + msg.key + " in port " + portMap.get(secondSucc));
									MessageAction(successor, predecessor, msgType, origin, portMap.get(secondSucc), msg.key, msg.value);









								}

								Message message = new Message(successor, predecessor, msg.origin);
								ObjectOutputStream obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;
							case "replication":
								Log.v("Inserting in 2: ", "value: " + msg.value + " key:  " + msg.key + " in port " + portMap.get(myPortstr) + " fromn port " + portMap.get(msg.origin));
								if(msg.mType.equals("replication")) {
									SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
									SharedPreferences.Editor editor = sharedPref.edit();
									editor.putString(msg.key, msg.value);
									editor.commit();

								}

								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;
							case "singlequery":
								Log.v("Single Q Server:  ", " value: " + msg.value + " key:  " + msg.key + " to port " + portMap.get(msg.origin) + " from port " + portMap.get(myPortstr) + " Origin" + portMap.get(msg.origin));
								if(msg.mType.equals("singlequery"))
								{
									Log.v("Searched Remotely:  ", " value: " + msg.value + " key:  " + msg.key + " to port " + portMap.get(msg.origin) + " from port " + portMap.get(myPortstr) + " Origin" + portMap.get(msg.origin));
									SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
									String ans = sharedPref.getString(msg.key, null);
									Log.v("Single query result:  ", " key :   " + msg.key + "  value :   "+ ans + " sending result to:  " + portMap.get(msg.origin));
									MessageAction(successor, predecessor, "singleresult", msg.origin, portMap.get(msg.origin), msg.key, ans);

								}
								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;
							case "singleresult":
								Log.v("Single QR Server:  ", " value: " + msg.value + " key:  " + msg.key + " to port " + portMap.get(msg.origin) + " from port " + portMap.get(myPortstr) + " Origin" + portMap.get(msg.origin));
								queryResult.put(msg.key,msg.value);
								//lockSingleQuery = false;

								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;
							case "allquery":
								if (myPortstr.equals(msg.origin))
								{
									lockAllQuery = false;
								}
								else {
									SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
									HashMap<String, String> tempMap = new HashMap<String, String>();
									for (String key : sharedPref.getAll().keySet()) {
										tempMap.put(key, sharedPref.getString(key, null));
									}
									Log.v("Searched ALL Rem:  ", " key:  " + msg.key + " to port " + portMap.get(successor) + " from port " + portMap.get(portMap.get(myPortstr)));
									queryAllMessage(successor, predecessor, "allresult", msg.origin, portMap.get(msg.origin), msg.key, msg.value, tempMap);
									//queryAllMessage(successor, predecessor, "allquery", msg.origin, portMap.get(successor), msg.key, msg.value, null);
								}
								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;
							case "allresult":
								Log.v("In all result:  ","  from " + msg.origin);
								returnHaspMap.putAll(msg.allQResult);
								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;


							case "recovery":

								Log.v("In recovery:  ", " msg type  " + msg.mType + " origin  " + portMap.get(msg.origin) + " received on  " + msg.dest );
								if(msg.mType.equals("recovery")) {
									SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
									HashMap<String, String> tempMap = new HashMap<String, String>();
									for (String key : sharedPref.getAll().keySet()) {
										tempMap.put(key, sharedPref.getString(key, null));
									}
									Log.v("Before recovresult:  ", " to port " + portMap.get(msg.origin) + " from port " + portMap.get(msg.dest));
									queryAllMessage("", "", "recovResult", msg.origin, portMap.get(msg.origin), "", "", tempMap);
								}
								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;

							case "recovResult":

								Log.v("In recovresult: ", " from " + portMap.get(msg.origin) + "  size of hashmap  " + msg.allQResult.size());

								Map<String,String> recovMap = new HashMap<String, String >();
								recovMap.putAll(msg.allQResult);
								Log.v("In recovresult check1: ", " from " + portMap.get(msg.origin) + "  size of hashmap  " + recovMap.size());

								//Log.v("In recovresult check2: "," origin " + portMap.get(msg.origin) + "  successor 1 " + portMap.get(succ) + "  successor 2 " + portMap.get(secondSuc) +"  predecessor 1 " + portMap.get(predec) +"  predecessor 2 " + portMap.get(secondPredec) );

								for(String k : recovMap.keySet())
								{
									String[] con = checkPosition(k);
									//String check = checkPosition(k);
									if(con[0].equals(msg.origin)||con[1].equals(msg.origin)||con[2].equals(msg.origin))
									{
										SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
										SharedPreferences.Editor editor = sharedPref.edit();
										editor.putString(k, recovMap.get(k));
										editor.commit();

									}

								}
								Log.v("In recovresult final: ", " finally recovery finish ");
								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;

							case "delete":
								if(msg.mType.equals("delete"))
								{
									SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
									SharedPreferences.Editor editor = sharedPref.edit();
									//editor.remove(msg.key);
									editor.clear();
									editor.commit();
								}
								message = new Message(successor, predecessor, msg.origin);
								obj = new ObjectOutputStream(socket.getOutputStream());
								obj.writeObject(message);
								break;
							default:
								Log.e(TAG, "Unknown message type: " + msg.mType);


						}

					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					} catch (IndexOutOfBoundsException e) {
						Log.e(TAG, "Index not found  ", e);
					}

				}
			} catch (IOException e) {
				Log.e(TAG, "Error in server socket");
			}
			return null;
		}

		protected void onProgressUpdate(String... strings) {


			return;
		}
	}

	/***
	 * ClientTask is an AsyncTask that should send a string over the network.
	 * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
	 * an enter key press event.
	 *
	 * @author stevko
	 */
	private class ClientTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			try {

				String succ = msgs[0];
				String pred = msgs[1];
				String msgTypeMo = msgs[2];
				String sendOrigin = msgs[3];
				String dest = msgs[4];
				String sendkey = msgs[5];
				String sendValue = msgs[6];

				Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
						Integer.parseInt(dest));

                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
				Log.v("In Client:   ",   "Request from port:   " + portMap.get(sendOrigin) + "   Request to port :  " + dest + " key :   " + sendkey + "   value : " + sendValue + "  message type  " + msgTypeMo);
				Message mg = new Message(succ, pred, msgTypeMo, sendOrigin, dest, sendkey, sendValue);
				ObjectOutputStream obj = new ObjectOutputStream(socket.getOutputStream());
				Log.v("In Client:   ", " After msg object " + "   Request from port:   " + portMap.get(mg.origin) + "   Request to port :  " + mg.dest + " key :   " + mg.key + "   value : " + mg.value + "  message type  " + mg.mType);
				obj.writeObject(mg);

				ObjectInputStream objRead = new ObjectInputStream(socket.getInputStream());
				Message message = (Message) objRead.readObject();


			} catch (ClassNotFoundException e) {
				Log.e(TAG, "" + e);
			} catch (UnknownHostException e) {
				Log.e(TAG, "ClientTask UnknownHostException" + e);
			}catch (SocketTimeoutException e){
				Log.e(TAG, "ClientTask SocketTimeOut" + e);
			}
			catch (IOException e) {

				Log.e(TAG, "ClientTask socket IOException" + e );

			}



			return null;
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
						String sortOrder) {
		// TODO Auto-generated method stub
		String query = selection;

		String[] params = {"key", "value"};
		MatrixCursor mtxCur = new MatrixCursor(params);
		if (query.equals("@")) {
			SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
			Map<String, ?> temp = sharedPref.getAll();
			for (String key : temp.keySet()) {
				Log.v("Stored keys @  ", key);
				String ans = temp.get(key).toString();
				Log.v("Stored values @   ", ans);
				mtxCur.addRow(new String[]{key, ans});
			}
		} else if (query.equals("*")) {
			SharedPreferences sharedPref = getContext().getSharedPreferences("DBFILE", 0);
			Map<String, ?> temp = sharedPref.getAll();

			for (String key : temp.keySet()) {
				Log.v("Stored keys *  ", key);
				String ans = temp.get(key).toString();
				Log.v("Stored values *  ", ans);
				mtxCur.addRow(new String[]{key, ans});
			}
			Log.v("Search ALL SentL:  ", " key:  " + selection + " to port " + portMap.get(successor) + " from port " + portMap.get(portMap.get(myPortstr)));
			//new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, successor, predecessor, "allquery", origin, portMap.get(successor), selection, "");


			int ind = listOfNodes.indexOf(myPortstr);
			//Log.v("Calling recovery for: ", " counter " +  + " sending to " + portMap.get(listOfNodes.get(i)) + " sending from " + portMap.get(myPortstr));

			for (int i = 0; i<listOfNodes.size();i++) {
				if (i != ind) {
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "", "", "allquery", myPortstr, portMap.get(listOfNodes.get(i)), "", "");
				}
			}



				try {
					TimeUnit.MILLISECONDS.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			lockAllQuery = true;
			for (String hkey : returnHaspMap.keySet()){
				mtxCur.addRow(new String[]{hkey, returnHaspMap.get(hkey)});
			}
		} else {

				String[] node = checkPosition(selection);
				String desti = node[0];
				String succe = node[1];
				String secondSucce = node[2];
				Log.v("Search Sent Local:  ", " key:  " + selection + " to port " + desti + " from port " + portMap.get(myPortstr));
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,successor, predecessor, "singlequery", origin, portMap.get(desti), selection, "");
			Log.v("Search Sent succe:  ", " key:  " + selection + " to port " + succe + " from port " + portMap.get(myPortstr));
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,successor, predecessor, "singlequery", origin, portMap.get(succe), selection, "");
			Log.v("Search Sent secondSuc: ", " key:  " + selection + " to port " + secondSucce + " from port " + portMap.get(myPortstr));
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,successor, predecessor, "singlequery", origin, portMap.get(secondSucce), selection, "");
				//MessageAction(successor, predecessor, "singlequery", origin, portMap.get(checkPosition(selection)), selection, "");
			try {
				TimeUnit.MILLISECONDS.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}


			while (queryResult.get(selection) == null){
					Log.v("In while :", " waiting for result ");

				}
				Log.v("Search Returned:  ", " key:  " + selection +  "   value:   "  + queryResult.get(selection) + " from port " + portMap.get(myPortstr) );
				mtxCur.addRow(new String[]{selection, queryResult.get(selection) });
				lockSingleQuery = true;

		}
		return mtxCur;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	public void MessageAction(String succ, String pred, String type, String origin, String dest, String key, String value) {
		//Log.v("In Message  Action:  ", " key:  " + key + " to port " + dest + " from port " + origin);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, succ, pred, type, origin, dest, key, value);
		//Log.v("In MessageAction:  ", "  After client call     " + " key:  " + key + " to port " + dest + " from port " + origin);
	}

	public String[] checkPosition(String keyed) {
		//Log.v("The entered key ", keyed);
		String hashOfKey = null;
		try {
			hashOfKey = genHash(keyed);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (Exception E) {
			E.printStackTrace();
		}
		String[] nodes = new String[3];
		//Log.v("Condition :   ", "Port:   " + myPortstr + "Pred:     " + predecessor + "HashedKey:    " + hashOfKey);
		if(hashOfKey.compareTo(listOfNodes.get(0)) <=0)
		{
			nodes[0] = listOfNodes.get(0);
			nodes[1] = listOfNodes.get(1);
			nodes[2] = listOfNodes.get(2);

			return nodes;
		}
		else if(hashOfKey.compareTo(listOfNodes.get(1)) <=0) {
			nodes[0] = listOfNodes.get(1);
			nodes[1] = listOfNodes.get(2);
			nodes[2] = listOfNodes.get(3);



			return nodes;
		}
		else if(hashOfKey.compareTo(listOfNodes.get(2)) <=0){
			nodes[0] = listOfNodes.get(2);
			nodes[1] = listOfNodes.get(3);
			nodes[2] = listOfNodes.get(4);



			return nodes;
		}
		else if(hashOfKey.compareTo(listOfNodes.get(3)) <=0){
			nodes[0] = listOfNodes.get(3);
			nodes[1] = listOfNodes.get(4);
			nodes[2] = listOfNodes.get(0);





			return nodes;
		}
		else if(hashOfKey.compareTo(listOfNodes.get(4)) <=0)
		{
			nodes[0] = listOfNodes.get(4);
			nodes[1] = listOfNodes.get(0);
			nodes[2] = listOfNodes.get(1);

			return nodes;
		}
		else
		{
			nodes[0] = listOfNodes.get(0);
			nodes[1] = listOfNodes.get(1);
			nodes[2] = listOfNodes.get(2);

			return nodes;
		}

	}






}
