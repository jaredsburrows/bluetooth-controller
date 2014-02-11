package burrows.apps.bluetooth;

import java.util.ArrayList;

import burrows.apps.bluetooth.utils.Utils;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity 
{
	// Message types sent from the BluetoothService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Layout Views
	private EditText mOutEditText;

	// Toolbar
	private Button mToolbarConnectButton;
	private Button mToolbarDisconnectButton;
	private Button mToolbarPauseButton = null, mToolbarPlayButton;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Array adapter for the conversation thread
	private ArrayAdapter<String> mConversationArrayAdapter;
	// String buffer for outgoing messages
	private StringBuffer mOutStringBuffer;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the Bluetooth services
	private BluetoothChatService mBluetoothService = null;

	// State variables
	private boolean paused = false;
	private boolean connected = false;

	/**
	 * @category Activity
	 */
	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.activity_main);

		//		mSendTextContainer = findViewById(R.id.send_text_container);

		mToolbarConnectButton = (Button) findViewById(R.id.toolbar_btn_connect);
		mToolbarConnectButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startDeviceListActivity();
			}
		});

		mToolbarDisconnectButton = (Button) findViewById(R.id.toolbar_btn_disconnect);
		mToolbarDisconnectButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				disconnectDevices();
			}
		});

		mToolbarPauseButton = (Button) findViewById(R.id.toolbar_btn_pause);
		mToolbarPauseButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				//				paused = true;
				//				onPausedStateChanged();
				sendMessage("0");
			}
		});

		mToolbarPlayButton = (Button) findViewById(R.id.toolbar_btn_play);
		mToolbarPlayButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				//				paused = false;
				//				onPausedStateChanged();
				sendMessage("1");
			}
		});

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		//    if (mBluetoothAdapter == null) {
		//        Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
		//        finish();
		//    }
	}

	private void startDeviceListActivity() {
		Intent serverIntent = new Intent(this, DeviceListActivity.class);
		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	}

	@Override
	public void onStart() {
		super.onStart();
		// If BT is not on, request that it be enabled.
		// setupUserInterface() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the Bluetooth session
		} else {
			if (mBluetoothService == null) setupUserInterface();
		}
	}

	private void setupUserInterface() 
	{
		// Initialize the array adapter for the conversation thread
		mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
		ListView mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);

		// Initialize the compose field with a listener for the return key
		mOutEditText = (EditText) findViewById(R.id.edit_text_out);
		mOutEditText.setOnEditorActionListener(mWriteListener);

		// Initialize the send button with a listener that for click events
		Button mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Send a message using content of the edit text widget
				TextView view = (TextView) findViewById(R.id.edit_text_out);
				String message = view.getText().toString();
				sendMessage(message);
			}
		});

		// Initialize the BluetoothService to perform Bluetooth connections
		mBluetoothService = new BluetoothChatService(mHandler);

		// Initialize the buffer for outgoing messages
		mOutStringBuffer = new StringBuffer("");
	}

	@Override
	public void onDestroy() 
	{
		super.onDestroy();
		
		if (mBluetoothService != null) 
		{
			mBluetoothService.stop();
		}
	}

	/**
	 * Sends a message.
	 *
	 * @param message A string of text to send.
	 */
	private void sendMessage(String message) {
		// Check that we're actually connected before trying anything
		if (mBluetoothService.getState() != BluetoothChatService.STATE_CONNECTED) {
			Toast.makeText(this, "BT NOT CON", Toast.LENGTH_SHORT).show();
			return;
		}

		// Check that there's actually something to send
		if (message.length() > 0) {
			message += "\n";
			// Get the message bytes and tell the BluetoothService to write
			byte[] send = message.getBytes();
			mBluetoothService.write(send);

			// Reset out string buffer to zero and clear the edit text field
			mOutStringBuffer.setLength(0);
			mOutEditText.setText(mOutStringBuffer);
		}
	}

	// The action listener for the EditText widget, to listen for the return key
	private TextView.OnEditorActionListener mWriteListener =
			new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
			// If the action is a key-up event on the return key, send the message
			if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
				String message = view.getText().toString();
				sendMessage(message);
			}
			return true;
		}
	};

	// The Handler that gets information back from the BluetoothService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					connected = true;
					//                        mTitle.setText(mConnectedDeviceName);
					break;
				case BluetoothChatService.STATE_CONNECTING:
					//                        mTitle.setText(R.string.title_connecting);
					break;
				case BluetoothChatService.STATE_NONE:
					connected = false;
					//                        mTitle.setText(R.string.title_not_connected);
					break;
				}
				//				onBluetoothStateChanged();
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				// construct a string from the buffer
				String writeMessage = new String(writeBuf);
				mConversationArrayAdapter.add(">>> " + writeMessage);
				break;
			case MESSAGE_READ:
				if (paused) break;
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				mConversationArrayAdapter.add(readMessage);
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(), "Connected to "
						+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
						Toast.LENGTH_SHORT).show();
				break;
			}
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				mBluetoothService.connect(device);
			}
			break;

		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode != Activity.RESULT_OK) {
				// User did not enable Bluetooth or an error occurred
				Toast.makeText(this, "BT NOT ON", Toast.LENGTH_SHORT).show();
			}
			setupUserInterface();
		}
	}

	private void disconnectDevices() 
	{
		if (mBluetoothService != null) 
		{
			mBluetoothService.stop();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		this.getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
}