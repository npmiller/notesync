package fr.insarouen.asi.notesync.sync;

import android.app.IntentService;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;

import fr.insarouen.asi.notesync.*;
import fr.insarouen.asi.notesync.tasks.*;
import fr.insarouen.asi.notesync.sync.PeerList.ServiceStatic;

public class TaskListTransferService extends IntentService {
	private static final int SOCKET_TIMEOUT = 5000;
	private Intent intent;
	private NoteSync noteSync;
	private String host;
	private boolean isGroupOwner;

	public TaskListTransferService() {
		super("TaskListTransferService");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		this.intent = ServiceStatic.getIntent();
		this.noteSync = ServiceStatic.getNoteSync();
		this.host = ServiceStatic.getHost();
		this.isGroupOwner = ServiceStatic.getIsGroupOwner();

		Socket socket = new Socket();
		int port = 8988;
		ServerSocket serverSocket = null;
		Socket client = null;
		TaskListAsync taskListAsync = null;

		if(isGroupOwner) {
			new TaskListAsync(noteSync).execute();
		} else {
			try {
				TaskList originalTL = noteSync.getTasks();
				socket.bind(null);
				socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

				ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
				outputStream.writeObject(originalTL);

				ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
				TaskList receivedTL = (TaskList) inputStream.readObject();

				TaskList mergedTL = TaskList.merge(noteSync.getTasks(), receivedTL);

				noteSync.runOnUiThread(new SetTaskListRun(noteSync, mergedTL));

				noteSync.showToast(noteSync.getString(R.string.successSync));
			} catch (IOException e) {
				noteSync.showToast(noteSync.getString(R.string.IOException));
				Log.d("NoteSync","IOException : "+e.getStackTrace().toString());
			} catch (ClassNotFoundException e) {
				noteSync.showToast(noteSync.getString(R.string.ClassNotFoundException));
				Log.d("NoteSync","ClassNotFoundException : "+e.getStackTrace().toString());
			} finally {
				socketClose(socket);
				socketClose(client);
			}
		}

	}

	private void socketClose(Socket s) {
		if(s != null && s.isConnected()) {
			try {
				s.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static class TaskListAsync extends AsyncTask<Void, Void, String> {
		private NoteSync noteSync;

		/**
		 * @param context
		 * @param statusText
		 */
		public TaskListAsync(NoteSync noteSync) {
			this.noteSync = noteSync;
		}

		@Override
		protected String doInBackground(Void... params) {
			try {
				TaskList originalTL = noteSync.getTasks();

				ServerSocket serverSocket = new ServerSocket(8988);

				Socket client = serverSocket.accept();
				ObjectInputStream inputStream = new ObjectInputStream(client.getInputStream());
				TaskList receivedTL = (TaskList) inputStream.readObject();


				TaskList mergedTL = TaskList.merge(noteSync.getTasks(), receivedTL);

				noteSync.runOnUiThread(new SetTaskListRun(noteSync, mergedTL));

				ObjectOutputStream outputStream = new ObjectOutputStream(client.getOutputStream());
				outputStream.writeObject(originalTL);

				serverSocket.close();
				return "succes";
			}
			catch (IOException e) {
				noteSync.showToast(noteSync.getString(R.string.IOException));
				Log.d("NoteSync","IOException : "+e.getStackTrace().toString());
				return null;
			}
			catch (ClassNotFoundException e) {
				noteSync.showToast(noteSync.getString(R.string.ClassNotFoundException));
				Log.d("NoteSync","ClassNotFoundException : "+e.getStackTrace().toString());
				return null;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				noteSync.showToast(noteSync.getString(R.string.successSync));
			}
		}

		@Override
		protected void onPreExecute() {
			noteSync.showToast(noteSync.getString(R.string.openingSocket));
		}

	}
}

class SetTaskListRun implements Runnable {
	private NoteSync n;
	private TaskList tl;
	public SetTaskListRun(NoteSync n, TaskList tl) {
		this.n = n;
		this.tl = tl;
	}

	@Override
	public void run() {
		n.setTaskList(tl);
	}
}
