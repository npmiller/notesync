package fr.insarouen.asi.notesync;

import fr.insarouen.asi.notesync.tasks.*;
import fr.insarouen.asi.notesync.sync.*;

import android.app.Activity;
import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.BroadcastReceiver;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;

import android.os.Bundle;

import android.util.Log;

import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;


import android.widget.Toast;

import android.content.Context;

import java.util.Calendar;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

//à fusionner avec WiFiDirectActivity

public class NoteSync extends Activity implements TaskAddFragment.Callbacks,
	   TaskEditFragment.Callbacks, 
	   TaskListFragment.Callbacks {
		   private TaskList tasks; 
		   private TaskListAdapter adapter;
		   private String filename = "tasks";
		   private boolean isWifiP2pEnabled;
		   private boolean isConnected = false;
		   private boolean isConnecting = false;
		   private WifiP2pManager manager;
		   private boolean retryChannel = false;
		   private Channel channel;
		   private BroadcastReceiver receiver = null;
		   private ProgressDialog progressDialog = null;
		   private PeerListDialog peerListDialog;
		   // Intent request codes
		   private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
		   private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
		   private static final int REQUEST_ENABLE_BT = 3;
		   // Name of the connected device
		   private String mConnectedDeviceName = null;
		   // Local Bluetooth adapter
		   private BluetoothAdapter mBluetoothAdapter = null;
		   // Member object for the chat services
		   private SyncBTService mChatService = null;

		   private final IntentFilter intentFilter = new IntentFilter();

		   public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
			   this.isWifiP2pEnabled = isWifiP2pEnabled;
		   }

		   public boolean isWifiP2pEnabled() {
			   return this.isWifiP2pEnabled;
		   }

		   /** Called when the activity is first created. */
		   @Override
		   public void onCreate(Bundle savedInstanceState) {
			   super.onCreate(savedInstanceState);
			   setContentView(R.layout.main);

			   intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
			   intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
			   intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

			   manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
			   channel = manager.initialize(this, getMainLooper(), null);

			   tasks = readTaskList();
			   adapter = new TaskListAdapter(this, tasks);

			   if(savedInstanceState == null) {
				   final ActionBar actionBar = getActionBar();
				   actionBar.setHomeButtonEnabled(false);
				   actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

				   FragmentTransaction ft = getFragmentManager().beginTransaction();
				   ft.replace(R.id.container, new TaskListFragment());
				   ft.commit();
			   }
			   // Get local Bluetooth adapter
			   mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			   // If the adapter is null, then Bluetooth is not supported
			   if (mBluetoothAdapter == null) {
				   Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
				   finish();
				   return;
			   }
		   }

		   @Override
		   public void onResume() {
			   super.onResume();
		   }

		   @Override
		   public void onPause() {
			   super.onPause();
			   saveTaskList(tasks);
			   if(receiver != null)
				   unregisterReceiver(receiver);
		   }

		   public void setTaskList(TaskList taskList) {
			   tasks = taskList;
			   adapter.setTasks(taskList);
			   adapter.notifyDataSetChanged();
			   if (progressDialog != null && progressDialog.isShowing()) {
				   progressDialog.dismiss();
			   }
		   }

		   /* * Fragments callbacks * */

		   /* TaskAddFragment */
		   @Override
		   public void addTask(Task t) {
			   tasks.add(t);
			   adapter.notifyDataSetChanged();

			   FragmentTransaction ft = getFragmentManager().beginTransaction();
			   ft.replace(R.id.container, new TaskListFragment());
			   ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
			   ft.commit();
		   }

		   /* TaskEditFragment */
		   @Override
		   public void replaceTask(Task t, boolean orderChanged) {
			   if(orderChanged) {
				   tasks.remove(t);
				   addTask(t);
			   }
			   adapter.notifyDataSetChanged();

			   FragmentTransaction ft = getFragmentManager().beginTransaction();
			   ft.replace(R.id.container, new TaskListFragment());
			   ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
			   ft.commit();
		   }

		   /* TaskListFragment */

		   @Override
		   public TaskListAdapter getTasksAdapter() {
			   return adapter;
		   }

		   @Override
		   public TaskList getTasks() {
			   return tasks;
		   }

		   @Override
		   public void showDetails(int position) {
			   FragmentTransaction ft = getFragmentManager().beginTransaction();
			   ft.replace(R.id.container, new TaskEditFragment((Task)adapter.getItem(position)));
			   ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
			   ft.addToBackStack(null);
			   ft.commit();
		   }

		   @Override
		   public void removeTask(int position) {
			   adapter.removeTask(position);
		   }

		   @Override
		   public void onAddClick() {
			   FragmentTransaction ft = getFragmentManager().beginTransaction();
			   ft.replace(R.id.container, new TaskAddFragment());
			   ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
			   ft.addToBackStack(null);
			   ft.commit();
		   }

		   @Override
		   public void onSyncWifiClick() {
			   if (!isConnected){
				   receiver = new NoteSyncBroadcastReceiver(manager, channel, this);
				   registerReceiver(receiver, intentFilter);
				   onInitiateDiscovery();
			   } else {
				   this.peerListDialog.reconnect(this);
			   }
		   }

		   @Override
		   public void onSyncBTClick() {
			   // If BT is not on, request that it be enabled.
			   // setupChat() will then be called during onActivityResult
			   if (!mBluetoothAdapter.isEnabled()) {
				   Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				   startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			   } else {
				   mChatService = new SyncBTService(this);
				   mChatService.start();
				   Intent serverIntent = null;
				   serverIntent = new Intent(this, fr.insarouen.asi.notesync.sync.DeviceListActivity.class);
				   startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
			   }
		   }

		   public void onActivityResult(int requestCode, int resultCode, Intent data) {
			   switch (requestCode) {
				   case REQUEST_CONNECT_DEVICE_SECURE:
					   // When DeviceListActivity returns with a device to connect
					   if (resultCode == Activity.RESULT_OK) {
						   connectDevice(data, true);
					   }
					   break;
				   case REQUEST_CONNECT_DEVICE_INSECURE:
					   // When DeviceListActivity returns with a device to connect
					   if (resultCode == Activity.RESULT_OK) {
						   connectDevice(data, false);
					   }
					   break;
				   case REQUEST_ENABLE_BT:
					   // When the request to enable Bluetooth returns
					   if (resultCode == Activity.RESULT_OK) {
						   // Initialize the BluetoothChatService to perform bluetooth connections
						   mChatService = new SyncBTService(this);
						   mChatService.start();
						   Intent serverIntent = null;
						   serverIntent = new Intent(this, fr.insarouen.asi.notesync.sync.DeviceListActivity.class);
						   startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
					   } else {
						   // User did not enable Bluetooth or an error occurred
					   }
			   }
		   }

		   private void connectDevice(Intent data, boolean secure) {
			   // Get the device MAC address
			   String address = data.getExtras()
				   .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
			   // Get the BluetoothDevice object
			   BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
			   // Attempt to connect to the device
			   mChatService.connect(device, secure);
		   }

		   private void ensureDiscoverable() {
			   if (mBluetoothAdapter.getScanMode() !=
					   BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
				   Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				   discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
				   startActivity(discoverableIntent);
					   }
		   }


		   public void onInitiateDiscovery() {
			   progressDialog = ProgressDialog.show(this, this.getString(R.string.backCancel), this.getString(R.string.findingPeers), true,
					   true, new DialogInterface.OnCancelListener() {
						   @Override
						   public void onCancel(DialogInterface dialog) {

						   }
					   });
			   manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

				   @Override
				   public void onSuccess() {
					   Toast.makeText(NoteSync.this, NoteSync.this.getString(R.string.discoveryInitiated),
						   Toast.LENGTH_SHORT).show();
				   }

			   @Override
			   public void onFailure(int reasonCode) {
				   Toast.makeText(NoteSync.this, NoteSync.this.getString(R.string.discoveryFailed),
					   Toast.LENGTH_SHORT).show();
				   Log.d("NoteSync","Discovery failed : "+reasonCode);
				   if (NoteSync.this.progressDialog != null && NoteSync.this.progressDialog.isShowing()) {
					   NoteSync.this.progressDialog.dismiss();
				   }
			   }
			   });
		   }

		   public ProgressDialog getProgressDialog() {
			   return this.progressDialog;
		   }

		   public void setProgressDialog(ProgressDialog progressDialog) {
			   this.progressDialog = progressDialog;
		   }

		   public void onPeerSelection(PeerListDialog peerListDialog) {
			   this.peerListDialog = peerListDialog;
			   if (!isConnected && !isConnecting && !peerListDialog.peerListEmpty())
				   peerListDialog.show(getFragmentManager(), "PeerListDialog");
		   }

		   public void setConnected(boolean isConnected) {
			   this.isConnected = isConnected;
			   if (isConnected){
				   if (peerListDialog != null) {
					   peerListDialog.getPeerSelection().setConnected();
					   peerListDialog.dismiss();
				   }
				   if (progressDialog != null && progressDialog.isShowing()) {
					   progressDialog.dismiss();
				   }
			   }
		   }

		   public boolean isConnected() {
			   return isConnected;
		   }

		   public void setConnecting(boolean isConnecting) {
			   this.isConnecting = isConnecting;
		   }

		   public boolean isConnecting() {
			   return this.isConnecting;
		   }

		   public void showToast(final String text) {
			   runOnUiThread(new Runnable() {
				   public void run() {
					   Toast.makeText(NoteSync.this, text, Toast.LENGTH_SHORT).show();
				   }
			   }
			   );
		   }

		   private void saveTaskList(TaskList tl) {
			   try {
				   FileOutputStream fos = this.openFileOutput(filename, Context.MODE_PRIVATE);
				   ObjectOutputStream os = new ObjectOutputStream(fos);
				   os.writeObject(tl);
			   } catch (IOException e) {
				   Toast toast = Toast.makeText(this,
						   this.getString(R.string.nosave),
						   Toast.LENGTH_LONG);
				   toast.show();
			   }
		   }

		   private TaskList readTaskList() {
			   try {
				   FileInputStream fis = this.openFileInput(filename);
				   ObjectInputStream is = new ObjectInputStream(fis);
				   return (TaskList)is.readObject();
			   } catch (Exception e) {
				   return new TaskList();
			   }
		   }
}
